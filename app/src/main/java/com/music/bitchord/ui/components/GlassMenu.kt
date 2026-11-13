package com.music.bitchord.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import expo.modules.glassmenuandroid.GlassMenuAndroidView
import expo.modules.glassmenuandroid.GlassPanelView
import expo.modules.glassmenuandroid.records.GlassMenuItemRecord
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** True when the native expo-glass-menu engine can run (AGSL needs Android 13+). */
val supportsNativeGlassMenu: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

val UnfoldWidthSpring = spring<Float>(dampingRatio = 0.82f, stiffness = 520f)
val UnfoldHeightSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 300f)
val FoldSpring = spring<Float>(dampingRatio = 1f, stiffness = 900f)

class UnfoldGeometry {
    var source: Rect = Rect.Zero

    fun frame(progressX: Float, progressY: Float, size: Size): Rect {
        val w = size.width
        val h = size.height
        val left = source.left.coerceIn(0f, w)
        val right = source.right.coerceIn(left, w)
        val top = source.top.coerceIn(0f, h)
        val bottom = source.bottom.coerceIn(top, h)
        val x = progressX.coerceIn(0f, 1f)
        val y = progressY.coerceIn(0f, 1f)
        return Rect(
            left = lerp(left, 0f, x),
            top = lerp(top, 0f, y),
            right = lerp(right, w, x),
            bottom = lerp(bottom, h, y),
        )
    }

    fun radius(progress: Float, frame: Rect, endRadius: Float): Float {
        val capsule = min(frame.width, frame.height) / 2f
        return min(lerp(capsule, endRadius, progress.coerceIn(0f, 1f)), capsule)
    }
}

class UnfoldShape(private val frame: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(frame, CornerRadius(radius)))
}

class GlassPopupPositionProvider(
    private val marginPx: Int,
    private val geometry: UnfoldGeometry,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val width = popupContentSize.width
        val height = popupContentSize.height
        val opensFromEnd = anchorBounds.center.x > windowSize.width / 2
        val maxX = max(marginPx, windowSize.width - width - marginPx)
        val x = (if (opensFromEnd) anchorBounds.right - width else anchorBounds.left).coerceIn(marginPx, maxX)
        val roomBelow = windowSize.height - anchorBounds.top
        val roomAbove = anchorBounds.bottom
        val opensAbove = roomBelow < height && roomAbove > roomBelow
        val maxY = max(marginPx, windowSize.height - height - marginPx)
        val y = (if (opensAbove) anchorBounds.bottom - height else anchorBounds.top).coerceIn(marginPx, maxY)
        geometry.source = Rect(
            left = (anchorBounds.left - x).toFloat(),
            top = (anchorBounds.top - y).toFloat(),
            right = (anchorBounds.right - x).toFloat(),
            bottom = (anchorBounds.bottom - y).toFloat(),
        )
        return IntOffset(x, y)
    }
}

/**
 * Native liquid glass background surface powered by expo-glass-menu [GlassRenderer]
 * (`GlassMaterialShader` + `GlassShadowShader` + `GlassRecipe.MenuDark`/`MenuLight`).
 */
@Composable
fun NativeGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    topCornersOnly: Boolean = false,
    materialProgress: Float = 1f,
    surfaceOpacity: Float = 1f,
    customFrame: Rect? = null,
    customCornerRadiusPx: Float? = null,
) {
    val isDark = isSystemInDarkTheme()
    val menuConfig = remember {
        expo.modules.glassmenuandroid.records.GlassMenuConfigRecord()
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            GlassPanelView(context).apply {
                this.config = menuConfig
                this.cornerRadiusDp = cornerRadius.value
                this.topCornersOnly = topCornersOnly
                this.colorScheme = if (isDark) "dark" else "light"
                this.materialProgress = materialProgress
                this.surfaceOpacity = surfaceOpacity
                if (customFrame != null) {
                    this.setCustomFrame(
                        customFrame.left,
                        customFrame.top,
                        customFrame.right,
                        customFrame.bottom,
                    )
                } else {
                    this.clearCustomFrame()
                }
                if (customCornerRadiusPx != null) {
                    this.customCornerRadiusPx = customCornerRadiusPx
                }
            }
        },
        update = { view ->
            if (view.config != menuConfig) view.config = menuConfig
            view.cornerRadiusDp = cornerRadius.value
            view.topCornersOnly = topCornersOnly
            view.colorScheme = if (isDark) "dark" else "light"
            view.materialProgress = materialProgress
            view.surfaceOpacity = surfaceOpacity
            if (customFrame != null) {
                view.setCustomFrame(
                    customFrame.left,
                    customFrame.top,
                    customFrame.right,
                    customFrame.bottom,
                )
            } else {
                view.clearCustomFrame()
            }
            if (customCornerRadiusPx != null) {
                view.customCornerRadiusPx = customCornerRadiusPx
            }
        },
    )
}

/**
 * Frosted liquid glass popup menu using Expo unfolding physics.
 */
@Composable
fun CupertinoFrostedPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val unfoldX = remember { Animatable(0f) }
    val unfoldY = remember { Animatable(0f) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded) {
            visible = true
            coroutineScope {
                launch { unfoldX.animateTo(1f, UnfoldWidthSpring) }
                launch { unfoldY.animateTo(1f, UnfoldHeightSpring) }
            }
        } else if (visible) {
            coroutineScope {
                launch { unfoldY.animateTo(0f, FoldSpring) }
                launch { unfoldX.animateTo(0f, FoldSpring) }
            }
            visible = false
        }
    }

    if (!expanded && !visible) return

    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val cornerPx = with(density) { 20.dp.toPx() }
    val geometry = remember { UnfoldGeometry() }
    val positionProvider = remember(density) {
        GlassPopupPositionProvider(with(density) { 16.dp.roundToPx() }, geometry)
    }
    var popupSize by remember { mutableStateOf(IntSize.Zero) }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        val currentFrame = if (popupSize.width > 0 && popupSize.height > 0) {
            geometry.frame(unfoldX.value, unfoldY.value, Size(popupSize.width.toFloat(), popupSize.height.toFloat()))
        } else {
            null
        }
        val currentRadius = if (currentFrame != null) {
            geometry.radius(unfoldY.value, currentFrame, cornerPx)
        } else {
            cornerPx
        }
        Box(
            modifier = modifier
                .widthIn(min = 210.dp, max = 290.dp)
                .width(IntrinsicSize.Max)
                .onSizeChanged { popupSize = it },
        ) {
            if (supportsNativeGlassMenu) {
                NativeGlassSurface(
                    modifier = Modifier.matchParentSize(),
                    cornerRadius = 20.dp,
                    topCornersOnly = false,
                    materialProgress = unfoldY.value,
                    customFrame = currentFrame,
                    customCornerRadiusPx = currentRadius,
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (currentFrame != null) {
                            shape = UnfoldShape(currentFrame, currentRadius)
                            clip = true
                        }
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp)
                    .graphicsLayer {
                        alpha = ((unfoldY.value - 0.30f) / 0.70f).coerceIn(0f, 1f)
                    },
            ) {
                content()
            }
        }
    }
}

/**
 * Liquid glass menu item row matching iOS Cupertino styling.
 */
@Composable
fun CupertinoGlassMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val isDark = isSystemInDarkTheme()
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptics = rememberHaptics()

    val pressedBgColor = if (isPressed) {
        if (isDark) Color(0x28FFFFFF) else Color(0x14000000)
    } else if (selected) {
        if (isDark) Color(0x18FFFFFF) else Color(0x0C000000)
    } else {
        Color.Transparent
    }

    val defaultTextColor = if (destructive) {
        if (isDark) Color(0xFFFF453A) else Color(0xFFFF3B30)
    } else if (selected) {
        MaterialTheme.colorScheme.primary
    } else if (isDark) {
        Color(0xFFF5F5F7)
    } else {
        Color(0xFF1C1C1E)
    }

    val effectiveTextColor = if (enabled) defaultTextColor else defaultTextColor.copy(alpha = 0.38f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(pressedBgColor)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
            ) {
                haptics.play(Haptic.Select)
                onClick()
            }
            .padding(contentPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (leadingIcon != null) {
                    Box(
                        modifier = Modifier.size(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides effectiveTextColor,
                        ) {
                            leadingIcon()
                        }
                    }
                }
                CompositionLocalProvider(
                    LocalContentColor provides effectiveTextColor,
                ) {
                    ProvideTextStyle(
                        value = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = effectiveTextColor,
                            letterSpacing = (-0.2).sp,
                        ),
                    ) {
                        text()
                    }
                }
            }
            if (trailingIcon != null) {
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides if (destructive) effectiveTextColor else Color(0xFF8E8E93),
                    ) {
                        trailingIcon()
                    }
                }
            }
        }
    }
}

/**
 * Native liquid glass popup menu anchor with physics morph, blur and bloom.
 */
@Composable
fun GlassMenuAnchor(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<GlassMenuItemRecord>,
    modifier: Modifier = Modifier,
    alignment: String = "auto",
) {
    if (!supportsNativeGlassMenu) return

    val isDark = isSystemInDarkTheme()
    val menuConfig = remember {
        expo.modules.glassmenuandroid.records.GlassMenuConfigRecord()
    }
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    var anchorView by remember { mutableStateOf<GlassMenuAndroidView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            GlassMenuAndroidView(context).apply {
                glass = false
                this.config = menuConfig
                this.alignment = alignment
                colorScheme = if (isDark) "dark" else "light"
                this.items = items
                onMenuOpenChange = { event ->
                    val isOpen = event["isOpen"] as? Boolean ?: false
                    if (!isOpen) {
                        currentOnDismissRequest()
                    }
                }
                anchorView = this
            }
        },
        update = { view ->
            view.glass = false
            if (view.config != menuConfig) view.config = menuConfig
            view.alignment = alignment
            view.colorScheme = if (isDark) "dark" else "light"
            if (view.items != items) view.items = items
            anchorView = view
        },
    )

    LaunchedEffect(expanded) {
        val view = anchorView ?: return@LaunchedEffect
        if (expanded) {
            view.open()
        } else {
            view.close()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            anchorView?.close()
        }
    }
}
