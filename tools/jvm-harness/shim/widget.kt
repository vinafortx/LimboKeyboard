package android.widget

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup

open class TextView(context: Context) : View(context) {
    var text: CharSequence = ""
    var textSize: Float = 14f
    var typefaceValue: Typeface? = null
    var letterSpacing: Float = 0f
    var gravityValue: Int = 0
    var includeFontPadding: Boolean = true
    var currentTextColor: Int = 0
    fun setTextSize(unit: Int, size: Float) { textSize = size }
    fun setTextColor(color: Int) { currentTextColor = color }
    fun setLineSpacing(add: Float, mult: Float) {}
    fun setTypeface(tf: Typeface?) { typefaceValue = tf }
    fun setTypeface(tf: Typeface?, style: Int) { typefaceValue = tf }
    fun getTypeface(): Typeface? = typefaceValue
    fun setSingleLine(single: Boolean) {}
    fun isSingleLine(): Boolean = false
    fun setGravity(g: Int) { gravityValue = g }
    fun getGravity(): Int = gravityValue
    fun setMaxLines(n: Int) {}
    fun setEllipsize(e: Any?) {}
    fun setHint(h: CharSequence?) {}
}

open class ViewGroup2(context: Context) : android.view.ViewGroup(context)

open class LinearLayout(context: Context) : android.view.ViewGroup(context) {
    companion object { const val HORIZONTAL = 0; const val VERTICAL = 1 }
    var orientation: Int = HORIZONTAL
    var gravity: Int = 0
    var weightSum: Float = 0f
    var showDividers: Int = 0

    class LayoutParams(width: Int, height: Int) : android.view.ViewGroup.MarginLayoutParams(width, height) {
        @JvmField var weight: Float = 0f
        @JvmField var gravity: Int = -1
        constructor(width: Int, height: Int, weight: Float) : this(width, height) { this.weight = weight }
    }
}

open class FrameLayout(context: Context) : android.view.ViewGroup(context) {
    class LayoutParams(width: Int, height: Int) : android.view.ViewGroup.MarginLayoutParams(width, height) {
        @JvmField var gravity: Int = -1
    }
}

open class ScrollView(context: Context) : FrameLayout(context) {
    var fillViewport: Boolean = false
    fun smoothScrollTo(x: Int, y: Int) {}
    fun scrollTo(x: Int, y: Int) {}
}

open class HorizontalScrollView(context: Context) : FrameLayout(context) {
    var fillViewport: Boolean = false
    fun smoothScrollTo(x: Int, y: Int) {}
}
