package com.music.bitchord.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * AGSL smooth-minimum (`smin`) metaball glass bridge shader.
 *
 * Evaluates a signed distance field union of [anchor] and [popup] rounded
 * rectangles with smoothing radius [k], rendering a translucent frosted glass
 * hull and specular rim highlight across the liquid neck bridging them.
 */
private const val METABALL_GLASS_AGSL = """
    uniform float4 uAnchor;   // left, top, right, bottom in px
    uniform float  uAnchorR;  // corner radius in px
    uniform float4 uPopup;    // left, top, right, bottom in px
    uniform float  uPopupR;   // corner radius in px
    uniform float  uK;        // smin smoothing radius in px
    uniform float4 uFill;     // rgba tint
    uniform float4 uRim;      // rgba rim highlight
    uniform float  uAlpha;    // overall opacity

    float sdRoundBox(float2 p, float2 center, float2 halfSize, float r) {
        float2 q = abs(p - center) - (halfSize - float2(r));
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    }

    float smin(float a, float b, float k) {
        if (k <= 0.001) return min(a, b);
        float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
        return mix(b, a, h) - k * h * (1.0 - h);
    }

    half4 main(float2 fragCoord) {
        float2 aCenter = 0.5 * (uAnchor.xy + uAnchor.zw);
        float2 aHalf   = max(0.5 * (uAnchor.zw - uAnchor.xy), float2(1.0));
        float2 pCenter = 0.5 * (uPopup.xy + uPopup.zw);
        float2 pHalf   = max(0.5 * (uPopup.zw - uPopup.xy), float2(1.0));

        float dA = sdRoundBox(fragCoord, aCenter, aHalf, min(uAnchorR, min(aHalf.x, aHalf.y)));
        float dP = sdRoundBox(fragCoord, pCenter, pHalf, min(uPopupR, min(pHalf.x, pHalf.y)));
        float d  = smin(dA, dP, uK);

        // Anti-aliased hull coverage
        float coverage = clamp(0.5 - d * 0.75, 0.0, 1.0) * uAlpha;
        if (coverage <= 0.001) return half4(0.0);

        // Specular rim along the metaball boundary
        float rimBand = smoothstep(2.5, 0.0, abs(d)) * clamp(0.5 - d, 0.0, 1.0);
        half4 col = half4(uFill) * coverage;
        col += half4(uRim) * (rimBand * uAlpha);
        return col;
    }
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun LiquidMetaballBridgeOverlay(
    anchorBounds: Rect?,
    popupBounds: Rect?,
    progress: Float,
    cornerRadius: Dp = 24.dp,
) {
    if (anchorBounds == null || popupBounds == null || progress <= 0.005f) return
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val popupRadiusPx = with(density) { cornerRadius.toPx() }
    val shader = remember { RuntimeShader(METABALL_GLASS_AGSL) }
    val brush = remember(shader) { ShaderBrush(shader) }

    val fillColor = if (isDark) Color(0xFF2C2C2E).copy(alpha = 0.42f) else Color(0xFFFAFAFA).copy(alpha = 0.52f)
    val rimColor = if (isDark) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.55f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val p = progress.coerceIn(0f, 1f)
                // Peak gooey bridge around mid-transition, snapping clean at 0 and 1.
                val bridgeWeight = kotlin.math.sin(p * kotlin.math.PI).toFloat().coerceAtLeast(0f)
                val kPx = lerp(4f, 52f, bridgeWeight)

                // Anchor shrinks slightly as droplet pulls out.
                val aScale = lerp(1f, 0.78f, p)
                val aHalfW = anchorBounds.width * 0.5f * aScale
                val aHalfH = anchorBounds.height * 0.5f * aScale
                val aCx = anchorBounds.center.x
                val aCy = anchorBounds.center.y

                // Popup expands from anchor center to final popup bounds.
                val pCx = lerp(aCx, popupBounds.center.x, p)
                val pCy = lerp(aCy, popupBounds.center.y, p)
                val pHalfW = lerp(anchorBounds.width * 0.5f, popupBounds.width * 0.5f, p)
                val pHalfH = lerp(anchorBounds.height * 0.5f, popupBounds.height * 0.5f, p)
                val pR = lerp(minOf(aHalfW, aHalfH), popupRadiusPx, p)

                shader.setFloatUniform(
                    "uAnchor",
                    aCx - aHalfW, aCy - aHalfH, aCx + aHalfW, aCy + aHalfH,
                )
                shader.setFloatUniform("uAnchorR", minOf(aHalfW, aHalfH))
                shader.setFloatUniform(
                    "uPopup",
                    pCx - pHalfW, pCy - pHalfH, pCx + pHalfW, pCy + pHalfH,
                )
                shader.setFloatUniform("uPopupR", pR)
                shader.setFloatUniform("uK", kPx)
                shader.setFloatUniform("uFill", fillColor.red, fillColor.green, fillColor.blue, fillColor.alpha)
                shader.setFloatUniform("uRim", rimColor.red, rimColor.green, rimColor.blue, rimColor.alpha)
                shader.setFloatUniform("uAlpha", (1f - (p - 0.85f).coerceAtLeast(0f) / 0.15f).coerceIn(0f, 1f))

                drawRect(brush = brush)
            },
    )
}

/**
 * Modifier that animates a Compose popup card with a bouncy metaball scale +
 * crisp content fade-in from [anchorBounds] (when present).
 */
@Composable
fun Modifier.liquidMetaballPopupTransform(
    visible: Boolean,
    anchorBounds: Rect? = null,
    onPopupBounds: ((Rect) -> Unit)? = null,
): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "metaballPopupProgress",
    )
    var ownBounds by remember { mutableStateOf<Rect?>(null) }
    return this
        .onGloballyPositioned { coords ->
            val b = coords.boundsInWindow()
            ownBounds = b
            onPopupBounds?.invoke(b)
        }
        .graphicsLayer {
            val p = progress.coerceIn(0f, 1f)
            val s = lerp(0.84f, 1f, p)
            scaleX = s
            scaleY = s
            alpha = ((p - 0.15f) / 0.85f).coerceIn(0f, 1f)
            val anchor = anchorBounds
            val own = ownBounds
            if (anchor != null && own != null) {
                translationX = lerp(anchor.center.x - own.center.x, 0f, p) * 0.35f
                translationY = lerp(anchor.center.y - own.center.y, 0f, p) * 0.35f
            }
        }
}
