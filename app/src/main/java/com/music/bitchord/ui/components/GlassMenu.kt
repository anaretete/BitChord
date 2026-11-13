package com.music.bitchord.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.doOnLayout
import expo.modules.glassmenuandroid.GlassMenuAndroidView
import expo.modules.glassmenuandroid.GlassPanelView
import expo.modules.glassmenuandroid.records.GlassMenuConfigRecord
import expo.modules.glassmenuandroid.records.GlassMenuItemRecord
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

val supportsNativeGlassMenu: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@Composable
fun NativeGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 24.dp,
    topCornersOnly: Boolean = false,
    materialProgress: Float = 1f,
    surfaceOpacity: Float = 1f,
    customCornerRadiusPx: Float = -1f,
    customFrame: Rect? = null,
) {
    if (!supportsNativeGlassMenu) return
    val isDark = isSystemInDarkTheme()
    val menuConfig = remember { GlassMenuConfigRecord() }
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
                this.customCornerRadiusPx = customCornerRadiusPx
                if (customFrame != null) {
                    setCustomFrame(customFrame.left, customFrame.top, customFrame.right, customFrame.bottom)
                } else {
                    clearCustomFrame()
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
            view.customCornerRadiusPx = customCornerRadiusPx
            if (customFrame != null) {
                view.setCustomFrame(customFrame.left, customFrame.top, customFrame.right, customFrame.bottom)
            } else {
                view.clearCustomFrame()
            }
        },
    )
}

@Composable
fun GlassMenuAnchor(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<GlassMenuItemRecord>,
    modifier: Modifier = Modifier,
    alignment: String = "auto",
    glass: Boolean = false,
    cornerRadius: Float? = null,
) {
    val isDark = isSystemInDarkTheme()
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)

    if (!supportsNativeGlassMenu) {
        if (expanded && items.isNotEmpty()) {
            FallbackMenuDialog(
                items = items,
                onDismissRequest = currentOnDismissRequest,
            )
        }
        return
    }

    val menuConfig = remember { GlassMenuConfigRecord() }
    var anchorView by remember { mutableStateOf<GlassMenuAndroidView?>(null) }

    AndroidView(
        modifier = modifier.defaultMinSize(minWidth = 24.dp, minHeight = 24.dp),
        factory = { context ->
            GlassMenuAndroidView(context).apply {
                this.glass = glass
                this.cornerRadius = cornerRadius
                this.config = menuConfig
                this.alignment = alignment
                colorScheme = if (isDark) "dark" else "light"
                this.items = items
                onMenuOpenChange = { map ->
                    val isOpen = (map["open"] as? Boolean) ?: (map["isOpen"] as? Boolean) ?: false
                    if (!isOpen) currentOnDismissRequest()
                }
                anchorView = this
            }
        },
        update = { view ->
            view.glass = glass
            view.cornerRadius = cornerRadius
            if (view.config != menuConfig) view.config = menuConfig
            view.alignment = alignment
            view.colorScheme = if (isDark) "dark" else "light"
            if (view.items != items) view.items = items
            anchorView = view
        },
    )

    LaunchedEffect(expanded, anchorView) {
        val anchor = anchorView ?: return@LaunchedEffect
        if (expanded) {
            if (anchor.isAttachedToWindow && anchor.isLaidOut && anchor.width > 0 && anchor.height > 0) {
                anchor.post {
                    if (expanded && !anchor.open()) currentOnDismissRequest()
                }
            } else {
                anchor.doOnLayout {
                    anchor.post {
                        if (expanded && !anchor.open()) currentOnDismissRequest()
                    }
                }
            }
        } else {
            anchor.close()
        }
    }

    DisposableEffect(Unit) {
        onDispose { anchorView?.close() }
    }
}

@Composable
fun <T : Any> ExpoGlassMenuHost(
    item: T?,
    anchor: Rect?,
    onDismiss: () -> Unit,
    items: @Composable (T) -> List<GlassMenuItemRecord>,
) {
    var retainedItem by remember { mutableStateOf<T?>(null) }
    var retainedAnchor by remember { mutableStateOf<Rect?>(null) }
    var retainedMenuItems by remember { mutableStateOf<List<GlassMenuItemRecord>>(emptyList()) }

    if (item != null) {
        retainedItem = item
        retainedAnchor = anchor
        retainedMenuItems = items(item)
    }

    LaunchedEffect(item) {
        if (item == null && retainedItem != null) {
            delay(240L)
            retainedItem = null
            retainedAnchor = null
            retainedMenuItems = emptyList()
        }
    }

    val activeItem = item ?: retainedItem ?: return
    val activeAnchor = if (item != null) anchor else retainedAnchor
    val menuItems = if (item != null) items(activeItem) else retainedMenuItems
    if (menuItems.isEmpty()) return

    val isExpanded = item != null
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    var rootMeasured by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                rootOffset = it.positionInWindow()
                rootMeasured = true
            },
    ) {
        if (activeAnchor != null) {
            val widthDp = with(density) { activeAnchor.width.coerceAtLeast(1f).toDp() }.coerceAtLeast(24.dp)
            val heightDp = with(density) { activeAnchor.height.coerceAtLeast(1f).toDp() }.coerceAtLeast(24.dp)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (activeAnchor.left - rootOffset.x).roundToInt(),
                            (activeAnchor.top - rootOffset.y).roundToInt(),
                        )
                    }
                    .size(width = widthDp, height = heightDp),
            ) {
                GlassMenuAnchor(
                    expanded = isExpanded && rootMeasured,
                    onDismissRequest = onDismiss,
                    items = menuItems,
                    modifier = Modifier.matchParentSize(),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp),
            ) {
                GlassMenuAnchor(
                    expanded = isExpanded,
                    onDismissRequest = onDismiss,
                    items = menuItems,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

@Composable
private fun FallbackMenuDialog(
    items: List<GlassMenuItemRecord>,
    onDismissRequest: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(268.dp)
                .heightIn(max = 520.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                items.forEach { entry ->
                    when {
                        entry.separator -> HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                        entry.heading -> Text(
                            text = entry.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        else -> {
                            val tint = when {
                                entry.destructive -> MaterialTheme.colorScheme.error
                                entry.disabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !entry.disabled) {
                                        entry.onClick?.invoke()
                                        if (!entry.keepOpen) onDismissRequest()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (entry.checked == true) FontWeight.SemiBold else FontWeight.Normal,
                                        ),
                                        color = tint,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    entry.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (entry.checked == true) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
