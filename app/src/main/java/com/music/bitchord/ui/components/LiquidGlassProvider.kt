package com.music.bitchord.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.components.backdrop.Backdrop
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.music.bitchord.ui.components.backdrop.drawBackdrop
import com.music.bitchord.ui.components.backdrop.effects.blur
import com.music.bitchord.ui.components.backdrop.effects.lens
import com.music.bitchord.ui.components.backdrop.effects.vibrancy
import com.music.bitchord.ui.components.backdrop.highlight.Highlight
import com.music.bitchord.ui.components.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

@Immutable
data class LiquidGlassConfig(
    val enabled: Boolean = true,
    val chromaticAberration: Boolean = true,
    val refractionMultiplier: Float = 1.0f,
    val blurMultiplier: Float = 1.0f,
    val lensHeightMultiplier: Float = 1.0f,
    val tintAlphaMultiplier: Float = 1.0f,
    val springPhysicsEnabled: Boolean = true,
    val depthEffect: Boolean = true,
    val customTintColor: Color? = null,
)

val LocalLiquidGlassConfig = compositionLocalOf { LiquidGlassConfig() }

val LocalLayerBackdrop = staticCompositionLocalOf<Backdrop?> { null }

fun sanitizeLensShape(shape: Shape): Shape {
    return if (shape is CornerBasedShape) {
        shape
    } else {
        Capsule
    }
}

/**
 * Universal Modifier that transforms a Composable into a refractive,
 * spring-animated Kyant Liquid Glass lens with AGSL shaders.
 */
fun Modifier.liquidGlassSurface(
    shape: Shape = Capsule(),
    interactionSource: InteractionSource? = null,
    blurRadius: Dp = 18.dp,
    refractionHeight: Dp = 15.dp,
    refractionAmount: Dp = 24.dp,
    tintColor: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    backdrop: Backdrop? = null,
    exportedBackdrop: LayerBackdrop? = null,
    isInteractive: Boolean = false,
    depthEffect: Boolean? = null,
    hasShadow: Boolean = true,
): Modifier = composed {
    val config = LocalLiquidGlassConfig.current
    if (!config.enabled) return@composed this

    val isDark = isSystemInDarkTheme()
    val isPressed by (interactionSource?.collectIsPressedAsState() ?: remember { mutableStateOf<Boolean>(false) })
    val animationScope = rememberCoroutineScope()

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
        )
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed && config.springPhysicsEnabled) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "LiquidGlassScale",
    )

    val dynamicRefractionAmount by animateFloatAsState(
        targetValue = if (isPressed) refractionAmount.value * 1.4f else refractionAmount.value,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "LiquidGlassRefraction",
    )

    val safeShape = remember(shape) { sanitizeLensShape(shape) }

    val candidateBackdrop = backdrop ?: LocalLayerBackdrop.current
    val activeBackdrop = if (candidateBackdrop != null && candidateBackdrop !== exportedBackdrop) {
        candidateBackdrop
    } else {
        rememberCanvasBackdrop {
            val baseColor = if (isDark) Color(0x3016161A) else Color(0x45F2F2F6)
            drawRect(baseColor)
        }
    }

    val baseTint = if (tintColor.isSpecified && tintColor != Color.Transparent) {
        tintColor.copy(alpha = (tintColor.alpha * config.tintAlphaMultiplier).coerceIn(0f, 1f))
    } else if (config.customTintColor != null && config.customTintColor.isSpecified) {
        val alpha = if (isDark) 0.18f else 0.24f
        config.customTintColor.copy(alpha = (alpha * config.tintAlphaMultiplier).coerceIn(0f, 1f))
    } else {
        if (isDark) {
            Color.White.copy(alpha = (0.13f * config.tintAlphaMultiplier).coerceIn(0f, 1f))
        } else {
            Color.White.copy(alpha = (0.26f * config.tintAlphaMultiplier).coerceIn(0f, 1f))
        }
    }

    val effectiveDepthEffect = depthEffect ?: config.depthEffect

    val backdropModifier = Modifier.drawBackdrop(
        backdrop = activeBackdrop,
        shape = { safeShape },
        exportedBackdrop = exportedBackdrop,
        effects = {
            vibrancy()
            val effectiveBlur = (blurRadius.toPx() * config.blurMultiplier).coerceAtLeast(0f)
            if (effectiveBlur > 0.5f) {
                blur(effectiveBlur)
            }
            try {
                lens(
                    refractionHeight = (refractionHeight.toPx() * config.refractionMultiplier * config.lensHeightMultiplier).coerceAtLeast(0.1f),
                    refractionAmount = (dynamicRefractionAmount.dp.toPx() * config.refractionMultiplier).coerceAtLeast(0.1f),
                    depthEffect = effectiveDepthEffect,
                    chromaticAberration = config.chromaticAberration,
                )
            } catch (_: Throwable) {
            }
        },
        highlight = { Highlight.Default },
        shadow = if (hasShadow) ({ Shadow.Default }) else null,
        onDrawSurface = {
            if (surfaceColor.isSpecified) {
                drawRect(surfaceColor)
            } else {
                drawRect(baseTint)
            }
        },
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(backdropModifier)
        .then(
            if (isInteractive) {
                interactiveHighlight.modifier
            } else {
                Modifier
            },
        )
}

object LiquidGlassDefaults {
    fun popupSurfaceColor(isDark: Boolean, customTintColor: Color? = null): Color {
        if (customTintColor != null && customTintColor.isSpecified) {
            return if (isDark) {
                customTintColor.copy(alpha = 0.28f)
            } else {
                customTintColor.copy(alpha = 0.35f)
            }
        }
        return if (isDark) Color(0x4C181820) else Color(0x66FAFBFD)
    }

    fun popupBorderColor(isDark: Boolean): Color =
        if (isDark) Color(0x35FFFFFF) else Color(0x1F000000)

    val PopupBorderWidth: Dp = 0.6.dp

    val PopupBlurRadius: Dp = 24.dp
    val PopupRefractionHeight: Dp = 16.dp
    val PopupRefractionAmount: Dp = 26.dp

    fun popupTextColor(isDark: Boolean): Color =
        if (isDark) Color(0xF5FFFFFF) else Color(0xEB000000)

    fun popupSecondaryTextColor(isDark: Boolean): Color =
        if (isDark) Color(0x99EBEBF5) else Color(0x993C3C43)
}

/**
 * Universal Modifier that applies liquid glass background
 * for popup menus, bottom cards/sheets, dialogs, and popup selectors.
 */
fun Modifier.liquidGlassPopupSurface(
    shape: Shape = RoundedCornerShape(16.dp),
    backdrop: Backdrop? = null,
    exportedBackdrop: LayerBackdrop? = null,
    surfaceColor: Color = Color.Unspecified,
    tintColor: Color = Color.Unspecified,
    borderWidth: Dp = LiquidGlassDefaults.PopupBorderWidth,
    hasBorder: Boolean = true,
): Modifier = composed {
    val config = LocalLiquidGlassConfig.current
    if (!config.enabled) return@composed this

    val isDark = isSystemInDarkTheme()
    val effectiveSurfaceColor = if (surfaceColor.isSpecified) {
        surfaceColor
    } else {
        LiquidGlassDefaults.popupSurfaceColor(isDark, config.customTintColor)
    }
    val effectiveBorderColor = LiquidGlassDefaults.popupBorderColor(isDark)

    this
        .liquidGlassSurface(
            shape = shape,
            blurRadius = LiquidGlassDefaults.PopupBlurRadius,
            refractionHeight = LiquidGlassDefaults.PopupRefractionHeight,
            refractionAmount = LiquidGlassDefaults.PopupRefractionAmount,
            tintColor = tintColor,
            surfaceColor = effectiveSurfaceColor,
            backdrop = backdrop,
            exportedBackdrop = exportedBackdrop,
            depthEffect = true,
        )
        .then(
            if (hasBorder) {
                Modifier.border(
                    width = borderWidth,
                    color = effectiveBorderColor,
                    shape = shape,
                )
            } else {
                Modifier
            },
        )
}
