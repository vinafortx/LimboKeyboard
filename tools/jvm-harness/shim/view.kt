package android.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable

interface ViewParent

object Gravity {
    const val CENTER_VERTICAL = 0x10
    const val CENTER_HORIZONTAL = 0x01
    const val CENTER = 0x11
    const val TOP = 0x30
    const val BOTTOM = 0x50
    const val START = 0x00800003.toInt()
    const val END = 0x00800005.toInt()
    const val LEFT = 0x03
    const val RIGHT = 0x05
}

object HapticFeedbackConstants {
    const val KEYBOARD_TAP = 3
    const val LONG_PRESS = 0
    const val CLOCK_TICK = 26
    const val VIRTUAL_KEY = 1
}

class WindowInsets {
    fun getSystemWindowInsetTop(): Int = 0
    fun getSystemWindowInsetBottom(): Int = 0
    fun getSystemWindowInsetLeft(): Int = 0
    fun getSystemWindowInsetRight(): Int = 0
    fun getStableInsetTop(): Int = 0
    fun getStableInsetBottom(): Int = 0
    fun getInsets(type: Int): Any = Any()
    companion object { const val TypeSystemBars = 2 }
}

interface WindowInsetsController {
    fun setSystemBarsAppearance(mask: Int, value: Int)
    fun hide(t: Int)
    fun show(t: Int)
    companion object {
        const val APPEARANCE_LIGHT_STATUS_BARS = 8
        const val APPEARANCE_LIGHT_NAVIGATION_BARS = 16
    }
}

class WindowInsetsControllerImpl : WindowInsetsController {
    override fun setSystemBarsAppearance(mask: Int, value: Int) {}
    override fun hide(t: Int) {}
    override fun show(t: Int) {}
}

class KeyEvent

class MotionEvent {
    companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2
        const val ACTION_CANCEL = 3
    }
    val action: Int = 0
    val x: Float = 0f
    val y: Float = 0f
    val downTime: Long = 0L
    val eventTime: Long = 0L
    fun getX(index: Int): Float = 0f
    fun getY(index: Int): Float = 0f
    fun getPointerCount(): Int = 1
    fun getPointerId(index: Int): Int = 0
    fun getHistoricalX(index: Int, i: Int): Float = 0f
}

open class View(val context: Context) {
    interface OnApplyWindowInsetsListener { fun onApplyWindowInsets(v: View, insets: WindowInsets): WindowInsets }
    interface OnClickListener { fun onClick(v: View) }
    interface OnLongClickListener { fun onLongClick(v: View): Boolean }
    interface OnTouchListener { fun onTouch(v: View, event: MotionEvent): Boolean }

    class MeasureSpec {
        companion object {
            const val UNSPECIFIED = 0
            const val EXACTLY = 1073741824
            const val AT_MOST = -2147483648
            @JvmStatic fun makeMeasureSpec(size: Int, mode: Int): Int = (size and 0x3fffffff) or (mode and 0xc0000000.toInt())
            @JvmStatic fun getMode(spec: Int): Int = spec and 0xc0000000.toInt()
            @JvmStatic fun getSize(spec: Int): Int = spec and 0x3fffffff
        }
    }

    companion object {
        const val MATCH_PARENT = -1
        const val FILL_PARENT = -1
        const val WRAP_CONTENT = -2
        const val VISIBLE = 0
        const val INVISIBLE = 4
        const val GONE = 8
        const val LAYER_TYPE_NONE = 0
        const val LAYER_TYPE_SOFTWARE = 1
        const val LAYER_TYPE_HARDWARE = 2
        const val OVER_SCROLL_NEVER = 2
        const val OVER_SCROLL_ALWAYS = 0
    }

    var layoutParams: android.view.ViewGroup.LayoutParams? = null
    var background: Drawable? = null
    var isClickable: Boolean = false
    var isFocusable: Boolean = false
    var isEnabled: Boolean = true
    var isSelected: Boolean = false
    var id: Int = 0
    var tag: Any? = null
    var contentDescription: CharSequence? = null
    var visibility: Int = VISIBLE
    var alpha: Float = 1f
    var translationX: Float = 0f
    var translationY: Float = 0f
    var scaleX: Float = 1f
    var scaleY: Float = 1f
    var rotation: Float = 0f
    var pivotX: Float = 0f
    var pivotY: Float = 0f
    var paddingLeft: Int = 0
    var paddingTop: Int = 0
    var paddingRight: Int = 0
    var paddingBottom: Int = 0
    var minimumHeight: Int = 0
    var minimumWidth: Int = 0
    var clipToOutline: Boolean = false
    var isVerticalScrollBarEnabled: Boolean = true
    var isHorizontalScrollBarEnabled: Boolean = true
    var overScrollMode: Int = OVER_SCROLL_ALWAYS
    var clipToPadding: Boolean = true
    var preservesEGLContextOnPause: Boolean = false
    var elevation: Float = 0f
    var importantForAccessibility: Int = 0

    private var widthV = 0
    private var heightV = 0
    private var listener: OnApplyWindowInsetsListener? = null
    private var clickListener: OnClickListener? = null
    private var longClickListener: OnLongClickListener? = null
    private var touchListener: OnTouchListener? = null

    open val width: Int get() = widthV
    open val height: Int get() = heightV
    val resources get() = context.resources
    val parent: ViewParent? = null

    open fun onDraw(canvas: Canvas) {}
    open fun drawSelf(canvas: Canvas) {
        if (visibility == VISIBLE) onDraw(canvas)
    }
    open fun onMeasure(widthSpec: Int, heightSpec: Int) { setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec)) }
    open fun draw(canvas: Canvas) { onDraw(canvas) }
    fun invalidate() {}
    fun postInvalidate() {}
    fun requestLayout() {}
    fun measure(widthSpec: Int, heightSpec: Int) {
        measuredWidth = 0
        onMeasure(widthSpec, heightSpec)
    }
    var measuredWidth: Int = 0
        private set
    var measuredHeight: Int = 0
        private set
    fun setMeasuredDimension(w: Int, h: Int) { widthV = w; heightV = h; measuredWidth = w; measuredHeight = h }
    open fun layout(l: Int, t: Int, r: Int, b: Int) {
        widthV = r - l
        heightV = b - t
        onLayout(r - l, b - t)
    }
    open fun onLayout(w: Int, h: Int) {}
    fun setPadding(l: Int, t: Int, r: Int, b: Int) { paddingLeft = l; paddingTop = t; paddingRight = r; paddingBottom = b }
    fun setPaddingRelative(l: Int, t: Int, r: Int, b: Int) = setPadding(l, t, r, b)
    fun setLayerType(layerType: Int, paint: Paint?) {}
    fun setOnClickListener(l: OnClickListener?) { clickListener = l; isClickable = l != null }
    fun setOnLongClickListener(l: OnLongClickListener?) { longClickListener = l }
    fun setOnTouchListener(l: OnTouchListener?) { touchListener = l }
    fun performClick(): Boolean { clickListener?.onClick(this); return true }
    fun callOnClick(): Boolean = performClick()
    fun performHapticFeedback(constant: Int): Boolean = true
    fun performHapticFeedback(constant: Int, flags: Int): Boolean = true
    fun setTag(key: Int, value: Any?) { tag = value }
    fun getTag(key: Int): Any? = tag
    open fun findViewWithTag(t: Any?): View? = if (tag == t) this else null
    fun <T : View> findViewById(id: Int): T? = null
    fun post(action: Runnable): Boolean = true
    fun postDelayed(action: Runnable, delay: Long): Boolean = true
    fun removeCallbacks(action: Runnable) {}
    fun setOnApplyWindowInsetsListener(l: OnApplyWindowInsetsListener?) { listener = l }
    fun requestApplyInsets() {}
    fun onApplyWindowInsets(insets: WindowInsets): WindowInsets { listener?.let { return it.onApplyWindowInsets(this, insets) }; return insets }
    fun getLocationOnScreen(out: IntArray) { out[0] = 0; out[1] = 0 }
    fun getLocationInWindow(out: IntArray) { out[0] = 0; out[1] = 0 }
    fun setBackgroundColor(color: Int) {}
    fun setBackgroundDrawable(d: Drawable?) {}
    fun setWillNotDraw(b: Boolean) {}
    fun animate(): Any = Any()
    open fun onAttachedToWindow() {}
    open fun onDetachedFromWindow() {}
    fun bringToFront() {}
    fun invalidateOutline() {}
    fun setOutlineProvider(p: Any?) {}
}

open class ViewGroup(context: Context) : View(context), ViewParent {
    open class LayoutParams(@JvmField var width: Int, @JvmField var height: Int) {
        companion object {
            const val MATCH_PARENT = -1
            const val FILL_PARENT = -1
            const val WRAP_CONTENT = -2
        }
    }

    open class MarginLayoutParams(width: Int, height: Int) : LayoutParams(width, height) {
        @JvmField var leftMargin = 0
        @JvmField var topMargin = 0
        @JvmField var rightMargin = 0
        @JvmField var bottomMargin = 0
        @JvmField var marginStart = 0
        @JvmField var marginEnd = 0
        fun setMargins(l: Int, t: Int, r: Int, b: Int) { leftMargin = l; topMargin = t; rightMargin = r; bottomMargin = b }
        fun setMarginStart(size: Int) { marginStart = size }
        fun setMarginEnd(size: Int) { marginEnd = size }
        fun getMarginStart(): Int = marginStart
        fun getMarginEnd(): Int = marginEnd
    }

    private val children = ArrayList<View>()

    val childCount: Int get() = children.size
    fun getChildAt(i: Int): View = children[i]
    fun addView(child: View) { children.add(child) }
    open fun addView(child: View, params: LayoutParams) { child.layoutParams = params; children.add(child) }
    fun addView(child: View, index: Int, params: LayoutParams) { child.layoutParams = params; children.add(index, child) }
    fun removeAllViews() { children.clear() }
    fun removeView(child: View) { children.remove(child) }
    fun removeViewAt(i: Int) { children.removeAt(i) }
    fun indexOfChild(child: View): Int = children.indexOf(child)
    override fun findViewWithTag(t: Any?): View? {
        if (tag == t) return this
        for (c in children) { c.findViewWithTag(t)?.let { return it } }
        return null
    }
    fun setClipChildren(b: Boolean) {}

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec).let { if (it <= 0) 1080 else it }
        var height = 0
        for (c in children) {
            c.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            height += c.measuredHeight
        }
        setMeasuredDimension(w, if (MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightSpec) else height)
    }

    override fun onLayout(w: Int, h: Int) {
        var y = 0
        for (c in children) {
            c.layout(0, y, w, y + c.measuredHeight)
            y += c.measuredHeight
        }
    }

    override fun drawSelf(canvas: Canvas) {
        super.drawSelf(canvas)
        if (visibility != VISIBLE) return
        for (c in children) {
            val save = canvas.save()
            canvas.translate(0f, 0f)
            c.drawSelf(canvas)
            canvas.restoreToCount(save)
        }
    }

    fun top(): Float = 0f

    override fun onAttachedToWindow() {}
}
