package android.graphics.drawable

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Rect

open class Drawable {
    private val boundsRef: Rect = Rect()
    var alphaValue: Int = 255
    open fun draw(canvas: android.graphics.Canvas) {}
    open fun setAlpha(alpha: Int) { alphaValue = alpha }
    open fun setColorFilter(colorFilter: ColorFilter?) {}
    open fun getOpacity(): Int = 0
    open fun invalidateSelf() {}
    fun setBounds(l: Int, t: Int, r: Int, b: Int) { boundsRef.set(l, t, r, b) }
    fun setBounds(r: Rect) { boundsRef.set(r.left, r.top, r.right, r.bottom) }
    fun getBounds(): Rect = boundsRef
    fun getIntrinsicWidth(): Int = -1
    fun getIntrinsicHeight(): Int = -1
}

class GradientDrawable : Drawable() {
    enum class Orientation { TOP_BOTTOM, LEFT_RIGHT, TR_BL, TL_BR }
    var shape: Int = 0
    @JvmField var cornerRadius: Float = 0f
    companion object { const val OVAL = 1; const val RECTANGLE = 0 }
    fun setColor(color: Int) {}
    fun setSize(w: Int, h: Int) {}
    fun setStroke(width: Int, color: Int) {}
    fun setOrientation(o: Orientation) {}
    fun setGradientType(t: Int) {}
    fun setColors(colors: IntArray) {}
}

class ColorDrawable(color: Int = 0) : Drawable()
