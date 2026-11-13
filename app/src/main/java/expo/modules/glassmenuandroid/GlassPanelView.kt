package expo.modules.glassmenuandroid

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderNode
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import expo.modules.glassmenuandroid.overlay.GlassMenuOverlay
import expo.modules.glassmenuandroid.records.GlassMenuConfigRecord
import expo.modules.glassmenuandroid.records.GlassTriggerConfigRecord
import expo.modules.glassmenuandroid.render.GlassRenderer
import expo.modules.glassmenuandroid.render.GlassSurfaceState
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min

internal fun Context.findHostActivity(): Activity? {
  var current: Context? = this
  while (current is ContextWrapper) {
    if (current is Activity) return current
    current = current.baseContext
  }
  return null
}

/**
 * Native liquid glass surface powered by the exact same [GlassRenderer] (`GlassMaterialShader` +
 * `GlassShadowShader` + `GlassRecipe.MenuDark` / `GlassRecipe.MenuLight`) as [GlassMenuOverlay].
 *
 * Captures the host Activity's `android.R.id.content` hierarchy into a hardware [RenderNode] and
 * renders real SDF squircle lens refraction, 26dp Gaussian blur, 1.3x saturation, and CASDF
 * specular rim highlights—even when hosted inside a separate Dialog or Popup window.
 */
open class GlassPanelView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), ViewTreeObserver.OnPreDrawListener, Choreographer.FrameCallback {

  private val density = resources.displayMetrics.density
  private val supportsGlass = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
  private var glassRenderer: Any? = null
  private val surfaceState = GlassSurfaceState()
  private val limits = Rect()
  private val viewLoc = IntArray(2)
  private val rootLoc = IntArray(2)
  private var lastScreenX = Int.MIN_VALUE
  private var lastScreenY = Int.MIN_VALUE
  private var lastW = 0
  private var lastH = 0
  private var selfCapturing = false
  private var observedActivityDecor: View? = null
  private var frameCallbackScheduled = false
  private var warmupFrames = 0
  private var movingFrames = 0

  private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val fallbackRect = RectF()

  var config = GlassMenuConfigRecord()
    set(value) {
      field = value
      invalidate()
    }

  var triggerConfig = GlassTriggerConfigRecord()
    set(value) {
      field = value
      invalidate()
    }

  var cornerRadiusDp: Float = 24f
    set(value) {
      if (field != value) {
        field = value
        invalidate()
      }
    }

  var topCornersOnly: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        invalidate()
      }
    }

  var colorScheme: String = "auto"
    set(value) {
      if (field != value) {
        field = value
        invalidate()
      }
    }

  var surfaceOpacity: Float = 1f
    set(value) {
      if (field != value) {
        field = value
        invalidate()
      }
    }

  var materialProgress: Float = 1f
    set(value) {
      if (field != value) {
        field = value
        invalidate()
      }
    }

  private val customFrameRect = RectF()
  private var hasCustomFrame = false

  var customCornerRadiusPx: Float? = null
    set(value) {
      if (field != value) {
        field = value
        invalidate()
      }
    }

  fun setCustomFrame(left: Float, top: Float, right: Float, bottom: Float) {
    if (!hasCustomFrame ||
      customFrameRect.left != left ||
      customFrameRect.top != top ||
      customFrameRect.right != right ||
      customFrameRect.bottom != bottom
    ) {
      customFrameRect.set(left, top, right, bottom)
      hasCustomFrame = true
      movingFrames = max(movingFrames, MovingHoldFrames)
      scheduleFrameTracking()
      invalidate()
    }
  }

  fun clearCustomFrame() {
    if (hasCustomFrame) {
      hasCustomFrame = false
      invalidate()
    }
  }

  private val activityPreDrawListener = ViewTreeObserver.OnPreDrawListener {
    val actRoot = resolveActivityRoot()
    if (actRoot != null && isHierarchyDirty(actRoot, 0)) {
      invalidate()
    }
    true
  }

  init {
    setWillNotDraw(false)
    clipChildren = false
    clipToPadding = false
  }

  private fun scheduleFrameTracking() {
    if (!frameCallbackScheduled && isAttachedToWindow) {
      frameCallbackScheduled = true
      Choreographer.getInstance().postFrameCallback(this)
    }
  }

  override fun doFrame(frameTimeNanos: Long) {
    frameCallbackScheduled = false
    if (!isAttachedToWindow) return

    var keepRunning = false
    if (warmupFrames > 0) {
      warmupFrames--
      keepRunning = true
    }

    if (isShown && width > 0 && height > 0) {
      getLocationOnScreen(viewLoc)
      if (viewLoc[0] != lastScreenX || viewLoc[1] != lastScreenY || width != lastW || height != lastH) {
        lastScreenX = viewLoc[0]
        lastScreenY = viewLoc[1]
        lastW = width
        lastH = height
        movingFrames = MovingHoldFrames
      }
    }

    if (movingFrames > 0) {
      movingFrames--
      keepRunning = true
    }

    if (keepRunning) {
      invalidate()
      scheduleFrameTracking()
    }
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    if (changed) {
      movingFrames = max(movingFrames, MovingHoldFrames)
      scheduleFrameTracking()
      invalidate()
    }
  }

  override fun offsetTopAndBottom(offset: Int) {
    super.offsetTopAndBottom(offset)
    if (offset != 0) {
      movingFrames = max(movingFrames, MovingHoldFrames)
      scheduleFrameTracking()
      invalidate()
    }
  }

  override fun offsetLeftAndRight(offset: Int) {
    super.offsetLeftAndRight(offset)
    if (offset != 0) {
      movingFrames = max(movingFrames, MovingHoldFrames)
      scheduleFrameTracking()
      invalidate()
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    disableParentClipping()
    configureHostWindow()
    viewTreeObserver.addOnPreDrawListener(this)
    val actDecor = context.findHostActivity()?.window?.decorView
    if (actDecor != null && actDecor !== rootView) {
      observedActivityDecor = actDecor
      actDecor.viewTreeObserver.addOnPreDrawListener(activityPreDrawListener)
    }
    warmupFrames = WarmupFrames
    movingFrames = MovingHoldFrames
    scheduleFrameTracking()
    invalidate()
  }

  override fun onDetachedFromWindow() {
    viewTreeObserver.removeOnPreDrawListener(this)
    Choreographer.getInstance().removeFrameCallback(this)
    frameCallbackScheduled = false
    observedActivityDecor?.let { decor ->
      if (decor.viewTreeObserver.isAlive) {
        decor.viewTreeObserver.removeOnPreDrawListener(activityPreDrawListener)
      }
    }
    observedActivityDecor = null
    if (supportsGlass) releaseGraphics()
    super.onDetachedFromWindow()
  }

  private fun disableParentClipping() {
    var current = parent
    var depth = 0
    while (current is ViewGroup && depth < 10) {
      if (current.id == android.R.id.content) break
      current.clipChildren = false
      current.clipToPadding = false
      current = current.parent
      depth++
    }
  }

  private fun configureHostWindow() {
    val actDecor = context.findHostActivity()?.window?.decorView ?: return
    val hostRoot = rootView ?: return
    if (hostRoot === actDecor) return
    val params = hostRoot.layoutParams as? WindowManager.LayoutParams ?: return
    var changed = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if ((params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0) {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        params.blurBehindRadius = 0
        changed = true
      }
    }
    if ((params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0 && params.dimAmount > 0f) {
      params.dimAmount = 0f
      changed = true
    }
    if (changed) {
      val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
      try {
        wm?.updateViewLayout(hostRoot, params)
      } catch (_: Throwable) {
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private fun releaseGraphics() {
    (glassRenderer as? GlassRenderer)?.release()
    glassRenderer = null
  }

  override fun onPreDraw(): Boolean {
    if (!isShown || width <= 0 || height <= 0) return true
    getLocationOnScreen(viewLoc)
    if (viewLoc[0] != lastScreenX || viewLoc[1] != lastScreenY || width != lastW || height != lastH) {
      lastScreenX = viewLoc[0]
      lastScreenY = viewLoc[1]
      lastW = width
      lastH = height
      movingFrames = max(movingFrames, MovingHoldFrames)
      scheduleFrameTracking()
      invalidate()
    } else {
      val actRoot = resolveActivityRoot()
      if (actRoot != null && isHierarchyDirty(actRoot, 0)) {
        invalidate()
      }
    }
    return true
  }

  private fun isHierarchyDirty(view: View, depth: Int): Boolean {
    if (view === this || view is GlassMenuOverlay || view is GlassPanelView) return false
    if (view.isDirty) return true
    if (view is ViewGroup && depth < 6) {
      for (i in 0 until view.childCount) {
        val child = view.getChildAt(i)
        if (child.visibility == VISIBLE && isHierarchyDirty(child, depth + 1)) return true
      }
    }
    return false
  }

  internal fun resolveActivityRoot(): ViewGroup? {
    val activity = context.findHostActivity()
    val actContent = activity?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
    if (actContent != null) return actContent
    return (rootView.findViewById<ViewGroup>(android.R.id.content)) ?: (rootView as? ViewGroup)
  }

  internal fun isDark(): Boolean = when (colorScheme) {
    "dark" -> true
    "light" -> false
    else -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
  }

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private fun renderer(pixelDensity: Float): GlassRenderer =
    glassRenderer as? GlassRenderer ?: GlassRenderer(pixelDensity, pixelDensity * FieldStep).also { glassRenderer = it }

  override fun draw(canvas: Canvas) {
    if (selfCapturing || capturingPanelDepth > 0) return
    super.draw(canvas)
  }

  override fun onDraw(canvas: Canvas) {
    if (selfCapturing || capturingPanelDepth > 0 || width <= 0 || height <= 0) return
    if (!supportsGlass || !canvas.isHardwareAccelerated) {
      drawFallback(canvas)
      return
    }
    drawGlass(canvas)
  }

  private fun drawFallback(canvas: Canvas) {
    val left = if (hasCustomFrame) customFrameRect.left else 0f
    val top = if (hasCustomFrame) customFrameRect.top else 0f
    val right = if (hasCustomFrame) customFrameRect.right else width.toFloat()
    val bottom = if (hasCustomFrame) customFrameRect.bottom else height.toFloat()
    val radius = customCornerRadiusPx ?: (cornerRadiusDp * density)
    fallbackRect.set(left, top, right, bottom)
    fallbackPaint.color = if (isDark()) 0xB824242C.toInt() else 0xCCF4F5F8.toInt()
    canvas.drawRoundRect(fallbackRect, radius, radius, fallbackPaint)
  }

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private fun drawGlass(canvas: Canvas) {
    val actRoot = resolveActivityRoot() ?: return
    val rootW = actRoot.width
    val rootH = actRoot.height
    if (rootW <= 0 || rootH <= 0) return

    getLocationOnScreen(viewLoc)
    actRoot.getLocationOnScreen(rootLoc)
    val offsetX = (viewLoc[0] - rootLoc[0]).toFloat()
    val offsetY = (viewLoc[1] - rootLoc[1]).toFloat()

    if (capturingMenuDepth > 0) {
      // If this GlassPanelView is inside the same window as actRoot, never draw glassNode into
      // GlassMenuOverlay.backdrop because glassNode references sharedBackdrop which could create
      // a cyclic RenderNode graph.
      if (rootView === actRoot.rootView) {
        drawFallback(canvas)
        return
      }
      // If this GlassPanelView is inside a separate Dialog/Popup window (e.g. AdaptiveSheet),
      // cachedRenderer's glassNode only references sharedBackdrop (which recorded actRoot, NOT the
      // Dialog's GlassMenuOverlay.backdrop), so drawing cachedGlass is acyclic!
      val cachedRenderer = glassRenderer as? GlassRenderer
      if (cachedRenderer != null) {
        val save = canvas.save()
        canvas.translate(-offsetX, -offsetY)
        cachedRenderer.drawCachedGlass(canvas)
        canvas.restoreToCount(save)
      } else {
        drawFallback(canvas)
      }
      return
    }

    val dark = isDark()
    val node = captureActivityBackdrop(actRoot, rootW, rootH, dark)
    limits.set(0, 0, rootW, rootH)

    val rawLeft = offsetX + (if (hasCustomFrame) customFrameRect.left else 0f)
    val rawTop = offsetY + (if (hasCustomFrame) customFrameRect.top else 0f)
    val rawRight = offsetX + (if (hasCustomFrame) customFrameRect.right else width.toFloat())
    val rawBottom = offsetY + (if (hasCustomFrame) customFrameRect.bottom else height.toFloat())
    if (rawRight <= rawLeft || rawBottom <= rawTop) return

    // Inset incoming SDF bounds by 1dp so the 0.533dp outer specular rim stroke
    // (drawn at 0 <= field.x <= 0.533 * density) stays completely inside view & screen bounds.
    val rimInset = min(1f * density, min(rawRight - rawLeft, rawBottom - rawTop) * 0.1f)
    val left = rawLeft + rimInset
    val top = rawTop + rimInset
    val right = rawRight - rimInset
    val bottom = if (topCornersOnly) rawBottom else (rawBottom - rimInset)

    val boxW = right - left
    val boxH = bottom - top
    val maxRadius = min(boxW, boxH) / 2f
    val radiusPx = min(customCornerRadiusPx ?: (cornerRadiusDp * density), maxRadius)
    val bottomExtension = if (topCornersOnly) max(radiusPx * 2f, 40f * density) else 0f

    val state = surfaceState
    state.incoming.set(left, top, right, bottom + bottomExtension)
    state.captureBounds.set(rawLeft, rawTop, rawRight, rawBottom)
    state.captureBounds.inset(-CaptureMargin * density, -CaptureMargin * density)
    state.lockedFieldStep = 0f
    state.radiusX = radiusPx
    state.radiusY = radiusPx
    state.hasSource = false
    state.smoothing = 0f
    state.lensHeight = FullLensHeight * density
    state.lensAmount = FullLensAmount * density
    state.materialProgress = materialProgress.coerceIn(0f, 1f)
    state.surfaceOpacity = surfaceOpacity.coerceIn(0f, 1f)
    state.dark = dark
    state.config = config
    state.trigger = triggerConfig
    state.pressX = 0f
    state.pressY = 0f
    state.pressRadius = LightRadius * density
    state.poolGain = 0f
    state.pressLift = 0f
    state.rimLift = 0f

    val renderer = renderer(density)
    renderer.prepare(state)
    val save = canvas.save()
    if (topCornersOnly) {
      val shadowGuard = 64f * density
      canvas.clipRect(-shadowGuard, -shadowGuard, width.toFloat() + shadowGuard, height.toFloat() + bottomExtension + shadowGuard)
    }
    canvas.translate(-offsetX, -offsetY)
    renderer.drawGlass(canvas, node, state, limits)
    if (renderer.warmPipelines(canvas)) post { invalidate() }
    canvas.restoreToCount(save)
  }

  private fun containsGlassView(view: View): Boolean {
    if (view === this || view is GlassPanelView || view is GlassMenuOverlay) return true
    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        val child = view.getChildAt(i)
        if (child.visibility == VISIBLE && containsGlassView(child)) return true
      }
    }
    return false
  }

  private fun drawBackdropSubtree(view: View, recording: Canvas) {
    if (view === this || view is GlassPanelView || view is GlassMenuOverlay || view.visibility != VISIBLE) return
    if (view.id == android.R.id.statusBarBackground || view.id == android.R.id.navigationBarBackground) return
    if (!containsGlassView(view)) {
      view.draw(recording)
      return
    }
    // View is an ancestor of a GlassPanelView/GlassMenuOverlay (e.g. binding.root or AndroidComposeView
    // when GlassPanelView is used in-window). Never call view.draw(recording) on it, because that
    // would record its RenderNode into sharedBackdrop while it is currently mid-draw or create a
    // cyclic RenderNode reference (Ancestor -> GlassPanelView -> glassNode -> sharedBackdrop -> Ancestor)!
    // Instead, if it is a regular ViewGroup (not a ComposeView), traverse its non-glass sibling children.
    if (view is ViewGroup && !view.javaClass.name.contains("ComposeView")) {
      val save = recording.save()
      recording.translate(view.left.toFloat() + view.translationX, view.top.toFloat() + view.translationY)
      recording.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
      view.background?.draw(recording)
      for (i in 0 until view.childCount) {
        drawBackdropSubtree(view.getChildAt(i), recording)
      }
      recording.restoreToCount(save)
    }
  }

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private fun captureActivityBackdrop(actRoot: ViewGroup, rootW: Int, rootH: Int, dark: Boolean): RenderNode {
    val inActivityWindow = rootView === actRoot.rootView
    val shared = sharedBackdrops.getOrPut(actRoot) { SharedBackdrop() }
    val node = shared.node as? RenderNode ?: RenderNode("GlassPanel.sharedBackdrop").also { shared.node = it }
    val now = SystemClock.elapsedRealtimeNanos()
    if (shared.width == rootW &&
      shared.height == rootH &&
      shared.dark == dark &&
      shared.inActivityWindow == inActivityWindow &&
      now - shared.lastRecordNanos < SharedCaptureReuseNanos &&
      node.hasDisplayList()
    ) {
      return node
    }

    node.setPosition(0, 0, rootW, rootH)
    selfCapturing = true
    capturingPanelDepth++
    try {
      val recording = node.beginRecording(rootW, rootH)
      recording.drawColor(if (dark) 0xFF18181E.toInt() else Color.WHITE)
      actRoot.rootView?.background?.draw(recording)
      actRoot.background?.draw(recording)
      for (i in 0 until actRoot.childCount) {
        val child = actRoot.getChildAt(i)
        if (inActivityWindow) {
          drawBackdropSubtree(child, recording)
        } else if (
          child !is GlassMenuOverlay &&
          child !is GlassPanelView &&
          child.visibility == VISIBLE &&
          child.id != android.R.id.statusBarBackground &&
          child.id != android.R.id.navigationBarBackground
        ) {
          drawBackdropSubtree(child, recording)
        }
      }
      node.endRecording()
      shared.width = rootW
      shared.height = rootH
      shared.dark = dark
      shared.inActivityWindow = inActivityWindow
      shared.lastRecordNanos = now
    } finally {
      capturingPanelDepth--
      selfCapturing = false
    }
    return node
  }

  private class SharedBackdrop {
    var node: Any? = null
    var lastRecordNanos: Long = 0L
    var width: Int = 0
    var height: Int = 0
    var dark: Boolean = true
    var inActivityWindow: Boolean = false
  }

  companion object {
    internal var capturingPanelDepth = 0
    internal var capturingMenuDepth = 0
    internal var capturingMenuBackdrop: Any? = null
    private val sharedBackdrops = WeakHashMap<ViewGroup, SharedBackdrop>()
    private const val SharedCaptureReuseNanos = 4_000_000L
    private const val WarmupFrames = 45
    private const val MovingHoldFrames = 15
    private const val FieldStep = 4f
    private const val CaptureMargin = 20f
    private const val FullLensHeight = 16f
    private const val FullLensAmount = -27f
    private const val LightRadius = 38f
  }
}
