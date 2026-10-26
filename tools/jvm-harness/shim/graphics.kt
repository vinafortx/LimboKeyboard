package android.graphics

class RectF(@JvmField var left: Float = 0f, @JvmField var top: Float = 0f, @JvmField var right: Float = 0f, @JvmField var bottom: Float = 0f) {
    constructor(r: RectF) : this(r.left, r.top, r.right, r.bottom)
    fun width(): Float = right - left
    fun height(): Float = bottom - top
    fun centerX(): Float = (left + right) / 2f
    fun centerY(): Float = (top + bottom) / 2f
    fun set(l: Float, t: Float, r: Float, b: Float) { left = l; top = t; right = r; bottom = b }
    fun set(r: RectF) { set(r.left, r.top, r.right, r.bottom) }
    fun setEmpty() { left = 0f; top = 0f; right = 0f; bottom = 0f }
    fun isEmpty(): Boolean = left >= right || top >= bottom
    fun inset(dx: Float, dy: Float) { left += dx; top += dy; right -= dx; bottom -= dy }
    fun offset(dx: Float, dy: Float) { left += dx; top += dy; right += dx; bottom += dy }
    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
    fun round(r: Rect) { r.left = Math.round(left); r.top = Math.round(top); r.right = Math.round(right); r.bottom = Math.round(bottom) }
    fun intersect(l: Float, t: Float, r: Float, b: Float): Boolean {
        if (left < r && l < right && top < b && t < bottom) {
            left = maxOf(left, l); top = maxOf(top, t); right = minOf(right, r); bottom = minOf(bottom, b)
            return true
        }
        return false
    }
    override fun toString(): String = "RectF($left, $top, $right, $bottom)"
}

class Rect(@JvmField var left: Int = 0, @JvmField var top: Int = 0, @JvmField var right: Int = 0, @JvmField var bottom: Int = 0) {
    fun width(): Int = right - left
    fun height(): Int = bottom - top
    fun set(l: Int, t: Int, r: Int, b: Int) { left = l; top = t; right = r; bottom = b }
    fun setEmpty() { left = 0; top = 0; right = 0; bottom = 0 }
}

object Color {
    const val BLACK = -0x1000000
    const val WHITE = -0x1
    const val DKGRAY = -0x444444
    const val GRAY = -0x777778
    const val LTGRAY = -0x333334
    const val RED = -0x10000
    const val GREEN = -0xff0100
    const val BLUE = -0xffff01
    const val TRANSPARENT = 0

    @JvmStatic fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b
    @JvmStatic fun rgb(r: Int, g: Int, b: Int): Int = argb(255, r, g, b)
    @JvmStatic fun alpha(c: Int): Int = (c ushr 24) and 0xFF
    @JvmStatic fun red(c: Int): Int = (c ushr 16) and 0xFF
    @JvmStatic fun green(c: Int): Int = (c ushr 8) and 0xFF
    @JvmStatic fun blue(c: Int): Int = c and 0xFF
    @JvmStatic fun parseColor(s: String): Int {
        val c = s.trim().removePrefix("#")
        return when (c.length) {
            6 -> (0xFF000000.toInt()) or c.toLong(16).toInt()
            8 -> c.toLong(16).toInt()
            3 -> {
                val r = c.substring(0, 1).repeat(2).toInt(16)
                val g = c.substring(1, 2).repeat(2).toInt(16)
                val b = c.substring(2, 3).repeat(2).toInt(16)
                argb(255, r, g, b)
            }
            else -> throw IllegalArgumentException("Unknown color $s")
        }
    }
    @JvmStatic fun HSVToColor(alpha: Int, hsv: FloatArray): Int {
        if (hsv[1] == 0f) { val v = (hsv[2] * 255).toInt(); return argb(alpha, v, v, v) }
        val h = hsv[0] / 60f
        val i = Math.floor(h.toDouble()).toInt()
        val f = h - i
        val p = hsv[2] * (1 - hsv[1])
        val q = hsv[2] * (1 - hsv[1] * f)
        val t = hsv[2] * (1 - hsv[1] * (1 - f))
        val v = hsv[2]
        val (r, g, b) = when (i % 6) {
            0 -> Triple(v, t, p); 1 -> Triple(q, v, p); 2 -> Triple(p, v, t)
            3 -> Triple(p, q, v); 4 -> Triple(t, p, v); else -> Triple(v, p, q)
        }
        return argb(alpha, (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }
    @JvmStatic fun colorToHSV(color: Int, hsv: FloatArray) {
        val r = red(color) / 255f; val g = green(color) / 255f; val b = blue(color) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        val d = max - min
        hsv[2] = max
        hsv[1] = if (max == 0f) 0f else d / max
        hsv[0] = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        if (hsv[0] < 0) hsv[0] += 360f
    }
}

class Typeface private constructor() {
    companion object {
        const val NORMAL = 0
        const val BOLD = 1
        const val ITALIC = 2
        val DEFAULT = Typeface()
        @JvmStatic fun create(family: String, style: Int): Typeface = DEFAULT
        @JvmStatic fun create(family: String, weight: Int, italic: Boolean): Typeface = DEFAULT
        @JvmStatic fun create(tf: Typeface?, style: Int): Typeface = DEFAULT
    }
}

class Paint(flags: Int = 0) {
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }
    enum class Align { LEFT, CENTER, RIGHT }

    companion object {
        const val ANTI_ALIAS_FLAG = 1
        const val FILTER_BITMAP_FLAG = 2
        const val SUBPIXEL_TEXT_FLAG = 128
    }

    var color: Int = Color.BLACK
    var alpha: Int = 255
    var style: Style = Style.FILL
    var strokeWidth: Float = 1f
    var strokeCap: Cap = Cap.BUTT
    var strokeJoin: Join = Join.MITER
    var textSize: Float = 12f
    private var tf: Typeface? = null
    var letterSpacing: Float = 0f
    private var shaderRef: Shader? = null
    fun setShader(s: Shader?): Shader? { shaderRef = s; return s }
    fun getShader(): Shader? = shaderRef
    var fontFeatureSettings: String? = null
    var isAntiAlias: Boolean = true
    var textAlign: Align = Align.LEFT
    var strokeMiter: Float = 0f
    fun setTypeface(t: Typeface?): Typeface? { tf = t; return t }
    fun getTypeface(): Typeface? = tf
    fun setShadowLayer(radius: Float, dx: Float, dy: Float, color: Int) {}
    fun setARGB(a: Int, r: Int, g: Int, b: Int) { color = Color.argb(a, r, g, b) }
    fun measureText(text: String): Float = text.length * textSize * 0.55f
    fun measureText(text: String, start: Int, end: Int): Float = (end - start) * textSize * 0.55f
    fun measureText(text: CharArray, start: Int, end: Int): Float = (end - start) * textSize * 0.55f
    val fontMetrics: FontMetrics get() = FontMetrics(-textSize * 0.8f, textSize * 0.2f)
    class FontMetrics(@JvmField val ascent: Float, @JvmField val descent: Float) {
        @JvmField var top: Float = ascent
        @JvmField var bottom: Float = descent
        @JvmField var leading: Float = 0f
    }
}

class FontMetrics(@JvmField val ascent: Float, @JvmField val descent: Float)

abstract class Shader {
    enum class TileMode { CLAMP, REPEAT, MIRROR }
}

class LinearGradient : Shader {
    constructor(x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int, mode: TileMode) : super()
    constructor(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, positions: FloatArray?, mode: TileMode) : super()
}

class RadialGradient : Shader {
    constructor(cx: Float, cy: Float, radius: Float, c0: Int, c1: Int, mode: TileMode) : super()
    constructor(cx: Float, cy: Float, radius: Float, colors: IntArray, stops: FloatArray?, mode: TileMode) : super()
}
class SweepGradient(cx: Float, cy: Float, c0: Int, c1: Int) : Shader()

class BitmapShader(val bitmap: Bitmap, val tileX: Shader.TileMode, val tileY: Shader.TileMode) : Shader()

class Bitmap private constructor(var width: Int, var height: Int) {
    enum class Config { ARGB_8888, RGB_565, ALPHA_8 }
    var isRecycled: Boolean = false
    fun recycle() { isRecycled = true }
    fun setPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {}
    fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {}
    fun eraseColor(color: Int) {}
    companion object {
        @JvmStatic fun createBitmap(w: Int, h: Int, config: Config): Bitmap {
            require(w > 0 && h > 0) { "width and height must be > 0" }
            return Bitmap(w, h)
        }
        @JvmStatic fun createBitmap(src: Bitmap, x: Int, y: Int, w: Int, h: Int): Bitmap = Bitmap(w, h)
        @JvmStatic fun createScaledBitmap(src: Bitmap, w: Int, h: Int, filter: Boolean): Bitmap = Bitmap(w, h)
    }
}

class Path {
    enum class Direction { CW, CCW }
    enum class FillType { WINDING, EVEN_ODD, INVERSE_WINDING }
    fun reset() {}
    fun rewind() {}
    fun moveTo(x: Float, y: Float) {}
    fun lineTo(x: Float, y: Float) {}
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) {}
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {}
    fun close() {}
    fun addRoundRect(r: RectF, rx: Float, ry: Float, dir: Direction) {}
    fun addRoundRect(l: Float, t: Float, rr: Float, b: Float, rx: Float, ry: Float, dir: Direction) {}
    fun addRect(l: Float, t: Float, r: Float, b: Float, dir: Direction) {}
    fun addCircle(x: Float, y: Float, r: Float, dir: Direction) {}
    fun addOval(r: RectF, dir: Direction) {}
    fun arcTo(r: RectF, start: Float, sweep: Float) {}
    fun arcTo(r: RectF, start: Float, sweep: Float, forceMoveTo: Boolean) {}
    fun setFillType(t: FillType) {}
    fun isEmpty(): Boolean = true
}

class Canvas(val bitmap: Bitmap? = null) {
    var density: Int = 0
    fun drawRect(l: Float, t: Float, r: Float, b: Float, paint: Paint) {}
    fun drawRect(rect: RectF, paint: Paint) {}
    fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) {}
    fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, paint: Paint) {}
    fun drawCircle(cx: Float, cy: Float, r: Float, paint: Paint) {}
    fun drawOval(rect: RectF, paint: Paint) {}
    fun drawOval(l: Float, t: Float, r: Float, b: Float, paint: Paint) {}
    fun drawArc(l: Float, t: Float, r: Float, b: Float, start: Float, sweep: Float, useCenter: Boolean, paint: Paint) {}
    fun drawArc(rect: RectF, start: Float, sweep: Float, useCenter: Boolean, paint: Paint) {}
    fun drawTextOnPath(text: String, path: Path, hOffset: Float, vOffset: Float, paint: Paint) {}
    fun drawLines(pts: FloatArray, paint: Paint) {}
    fun drawPoints(pts: FloatArray, paint: Paint) {}
    fun drawColor(color: Int) {}
    fun drawBitmap(bitmap: Bitmap, matrix: Any?, paint: Paint?) {}
    fun clipRect(rect: Rect): Boolean = true
    fun clipRect(l: Int, t: Int, r: Int, b: Int): Boolean = true
    fun saveLayer(l: Float, t: Float, r: Float, b: Float, paint: Paint?): Int = 1
    fun saveLayer(rect: RectF, paint: Paint?): Int = 1
    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {}
    fun drawPath(path: Path, paint: Paint) {}
    fun drawText(text: String, x: Float, y: Float, paint: Paint) {}
    fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) {}
    fun drawTextRun(text: CharSequence, start: Int, end: Int, ctxStart: Int, ctxEnd: Int, x: Float, y: Float, isRtl: Boolean, paint: Paint) {}
    fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {}
    fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint?) {}
    fun save(): Int = 1
    fun restore() {}
    fun restoreToCount(count: Int) {}
    fun translate(dx: Float, dy: Float) {}
    fun scale(sx: Float, sy: Float) {}
    fun rotate(degrees: Float) {}
    fun clipRect(l: Float, t: Float, r: Float, b: Float): Boolean = true
    fun clipRect(rect: RectF): Boolean = true
    fun clipPath(path: Path): Boolean = true
    fun clipPath(path: Path, op: Any?): Boolean = true
    fun concat(matrix: Any?) {}
    val width: Int get() = bitmap?.width ?: 1080
    val height: Int get() = bitmap?.height ?: 2340
    fun isHardwareAccelerated(): Boolean = false
}

class ColorFilter
class Region
