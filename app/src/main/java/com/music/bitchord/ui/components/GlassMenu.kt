package com.music.bitchord.ui.components

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import expo.modules.glassmenuandroid.GlassMenuAndroidView
import expo.modules.glassmenuandroid.GlassPanelView
import expo.modules.glassmenuandroid.records.GlassMenuItemRecord

/** True when the native expo-glass-menu engine can run (AGSL needs Android 13+). */
val supportsNativeGlassMenu: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

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
