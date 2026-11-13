package com.kyant.backdrop.catalog.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import tachiyomi.presentation.core.components.material.LocalLayerBackdrop
import tachiyomi.presentation.core.components.material.LocalLiquidGlassConfig
import tachiyomi.presentation.core.components.material.rememberCupertinoHaptics
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun LiquidIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalLayerBackdrop.current ?: rememberLayerBackdrop(),
    enabled: Boolean = true,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    shape: Shape = CircleShape,
    size: Dp = 42.dp,
    content: @Composable () -> Unit,
) {
    val animationScope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
        )
    }

    val defaultSurface = if (isDark) Color(0xFF2C2C2E).copy(alpha = 0.55f) else Color(0xFFE5E5EA).copy(alpha = 0.65f)
    val effectiveSurfaceColor = if (surfaceColor.isSpecified) surfaceColor else defaultSurface

    val tintLum = if (tint.isSpecified && tint != Color.Transparent) {
        0.299f * tint.red + 0.587f * tint.green + 0.114f * tint.blue
    } else -1f

    val surfaceLum = if (surfaceColor.isSpecified && surfaceColor != Color.Transparent) {
        0.299f * surfaceColor.red + 0.587f * surfaceColor.green + 0.114f * surfaceColor.blue
    } else -1f

    val effectiveBgLum = when {
        tintLum >= 0f -> tintLum
        surfaceLum >= 0f -> surfaceLum
        else -> if (isDark) 0.15f else 0.85f
    }

    val dynamicContrastColor = if (effectiveBgLum > 0.45f) Color(0xFF111113) else Color.White

    val effectiveContentColor = when {
        contentColor.isSpecified && contentColor == Color.White && effectiveBgLum > 0.45f -> Color(0xFF111113)
        contentColor.isSpecified && contentColor == Color(0xFF111113) && effectiveBgLum <= 0.45f -> Color.White
        contentColor.isSpecified -> contentColor
        else -> dynamicContrastColor
    }

    val isGlass = LocalLiquidGlassConfig.current.enabled
    val haptics = rememberCupertinoHaptics()

    val glassOrSolidModifier = if (isGlass) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(3f.dp.toPx())
                lens(14f.dp.toPx(), 24f.dp.toPx(), chromaticAberration = true)
            },
            layerBlock = if (isInteractive && enabled) {
                {
                    val progress = interactiveHighlight.pressProgress
                    val scale = lerp(1f, 1f + 4f.dp.toPx() / this.size.height, progress)

                    val maxOffset = this.size.minDimension
                    val initialDerivative = 0.05f
                    val offset = interactiveHighlight.offset
                    translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                    translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                    val maxDragScale = 4f.dp.toPx() / this.size.height
                    val offsetAngle = atan2(offset.y, offset.x)
                    scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / this.size.maxDimension)
                    scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / this.size.maxDimension)
                }
            } else {
                null
            },
            onDrawSurface = {
                if (tint.isSpecified) {
                    drawRect(tint, blendMode = BlendMode.Hue)
                    drawRect(tint.copy(alpha = 0.78f))
                }
                if (effectiveSurfaceColor.isSpecified) {
                    drawRect(effectiveSurfaceColor)
                }
            },
        )
    } else {
        val solidColor = when {
            tint.isSpecified && tint != Color.Transparent -> tint
            effectiveSurfaceColor.isSpecified -> effectiveSurfaceColor
            else -> defaultSurface
        }
        Modifier
            .clip(shape)
            .background(solidColor)
    }

    Box(
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.45f)
            .then(glassOrSolidModifier)
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = {
                    haptics.click()
                    onClick()
                },
            )
            .then(
                if (isInteractive && enabled) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides effectiveContentColor) {
            content()
        }
    }
}

@Composable
fun LiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalLayerBackdrop.current ?: rememberLayerBackdrop(),
    enabled: Boolean = true,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    shape: Shape = Capsule(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val animationScope = rememberCoroutineScope()

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
        )
    }

    val isDark = isSystemInDarkTheme()
    val tintLum = if (tint.isSpecified && tint != Color.Transparent) {
        0.299f * tint.red + 0.587f * tint.green + 0.114f * tint.blue
    } else -1f

    val surfaceLum = if (surfaceColor.isSpecified && surfaceColor != Color.Transparent) {
        0.299f * surfaceColor.red + 0.587f * surfaceColor.green + 0.114f * surfaceColor.blue
    } else -1f

    val effectiveBgLum = when {
        tintLum >= 0f -> tintLum
        surfaceLum >= 0f -> surfaceLum
        else -> if (isDark) 0.15f else 0.85f
    }

    val dynamicContrastColor = if (effectiveBgLum > 0.45f) Color(0xFF111113) else Color.White

    val effectiveContentColor = when {
        contentColor.isSpecified && contentColor == Color.White && effectiveBgLum > 0.45f -> Color(0xFF111113)
        contentColor.isSpecified && contentColor == Color(0xFF111113) && effectiveBgLum <= 0.45f -> Color.White
        contentColor.isSpecified -> contentColor
        else -> dynamicContrastColor
    }
    val isGlass = LocalLiquidGlassConfig.current.enabled
    val haptics = rememberCupertinoHaptics()
    val defaultSurface = if (isDark) Color(0xFF2C2C2E).copy(alpha = 0.75f) else Color(0xFFE5E5EA).copy(alpha = 0.85f)
    val effectiveSurfaceColor = if (surfaceColor.isSpecified) surfaceColor else defaultSurface

    val glassOrSolidModifier = if (isGlass) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(3f.dp.toPx())
                lens(14f.dp.toPx(), 24f.dp.toPx(), chromaticAberration = true)
            },
            layerBlock = if (isInteractive && enabled) {
                {
                    val width = size.width
                    val height = size.height

                    val progress = interactiveHighlight.pressProgress
                    val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

                    val maxOffset = size.minDimension
                    val initialDerivative = 0.05f
                    val offset = interactiveHighlight.offset
                    translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                    translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                    val maxDragScale = 4f.dp.toPx() / size.height
                    val offsetAngle = atan2(offset.y, offset.x)
                    scaleX =
                        scale +
                        maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                        (width / height).fastCoerceAtMost(1f)
                    scaleY =
                        scale +
                        maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                        (height / width).fastCoerceAtMost(1f)
                }
            } else {
                null
            },
            onDrawSurface = {
                if (tint.isSpecified) {
                    drawRect(tint, blendMode = BlendMode.Hue)
                    drawRect(tint.copy(alpha = 0.78f))
                }
                if (surfaceColor.isSpecified) {
                    drawRect(surfaceColor)
                }
            },
        )
    } else {
        val solidColor = when {
            tint.isSpecified && tint != Color.Transparent -> tint
            effectiveSurfaceColor.isSpecified -> effectiveSurfaceColor
            else -> defaultSurface
        }
        Modifier
            .clip(shape)
            .background(solidColor)
    }

    Row(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .then(glassOrSolidModifier)
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = {
                    haptics.click()
                    onClick()
                },
            )
            .then(
                if (isInteractive && enabled) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8f.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides effectiveContentColor) {
            content()
        }
    }
}
