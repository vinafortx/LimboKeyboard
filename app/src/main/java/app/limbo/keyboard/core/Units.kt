package app.limbo.keyboard.core

import android.content.Context
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** Screen density helpers. */
object Dp {
    fun density(context: Context): Float = context.resources.displayMetrics.density

    fun dp(context: Context, value: Float): Float = value * context.resources.displayMetrics.density

    /** Same as [dp] but rounded to whole pixels for layout and painting APIs that take ints. */
    fun dpi(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    fun px(context: Context, value: Float): Float = value * context.resources.displayMetrics.density

    fun sp(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.scaledDensity
}

/** Colour maths used across the interface. All values are ARGB ints. */
object Colors {

    fun alpha(color: Int, alpha: Float): Int =
        Color.argb((alpha.coerceIn(0f, 1f) * 255f).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))

    fun alphaOf(color: Int): Float = Color.alpha(color) / 255f

    fun mix(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f).roundToInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).roundToInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).roundToInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).roundToInt()
        )
    }

    fun lighten(color: Int, amount: Float): Int = mix(color, Color.WHITE, amount)

    fun darken(color: Int, amount: Float): Int = mix(color, Color.BLACK, amount)

    /** Perceptual luminance in 0..1. */
    fun luminance(color: Int): Float {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        fun lin(c: Float) = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * lin(r) + 0.7152f * lin(g) + 0.0722f * lin(b)
    }

    /** Black or white, whichever reads better on the given colour. */
    fun on(color: Int): Int = if (luminance(color) > 0.42f) Color.BLACK else Color.WHITE

    fun readable(color: Int, light: Int = Color.WHITE, dark: Int = Color.BLACK): Int =
        if (luminance(color) > 0.42f) dark else light

    fun toHex(color: Int, withAlpha: Boolean = false): String =
        if (withAlpha) String.format("#%08X", color) else String.format("#%06X", 0xFFFFFF and color)

    fun parse(text: String?, fallback: Int): Int {
        if (text == null) return fallback
        val clean = text.trim().removePrefix("#")
        return try {
            when (clean.length) {
                3 -> Color.rgb(
                    Integer.parseInt(clean.substring(0, 1).repeat(2), 16),
                    Integer.parseInt(clean.substring(1, 2).repeat(2), 16),
                    Integer.parseInt(clean.substring(2, 3).repeat(2), 16)
                )
                6 -> (0xFF000000 or java.lang.Long.parseLong(clean, 16)).toInt()
                8 -> java.lang.Long.parseLong(clean, 16).toInt()
                else -> fallback
            }
        } catch (_: Throwable) {
            fallback
        }
    }

    fun hsv(hue: Float, saturation: Float, value: Float, alpha: Float = 1f): Int =
        Color.HSVToColor((alpha * 255).roundToInt(), floatArrayOf(hue % 360f, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f)))

    fun hue(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[0]
    }

    fun saturation(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[1]
    }

    fun brightness(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[2]
    }

    fun shiftHue(color: Int, deltaDegrees: Float, saturationScale: Float = 1f, valueScale: Float = 1f): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return Color.HSVToColor(
            Color.alpha(color),
            floatArrayOf((hsv[0] + deltaDegrees + 360f) % 360f, (hsv[1] * saturationScale).coerceIn(0f, 1f), (hsv[2] * valueScale).coerceIn(0f, 1f))
        )
    }

    fun distance(a: Int, b: Int): Float {
        val dr = (Color.red(a) - Color.red(b)) / 255f
        val dg = (Color.green(a) - Color.green(b)) / 255f
        val db = (Color.blue(a) - Color.blue(b)) / 255f
        return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
    }

    /** A soft, low-contrast border that sits well on either light or dark surfaces. */
    fun hairline(surface: Int): Int =
        if (luminance(surface) > 0.5f) alpha(Color.BLACK, 0.08f) else alpha(Color.WHITE, 0.12f)
}

/** Small numeric helpers shared by the animation code. */
object Maths {
    fun Float.clamp(min: Float, max: Float): Float = if (this < min) min else if (this > max) max else this

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun easeOutCubic(t: Float): Float = 1f - (1f - t).pow(3f)

    fun easeOutQuint(t: Float): Float = 1f - (1f - t).pow(5f)

    fun easeInOutCubic(t: Float): Float = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3f) / 2f

    fun easeOutBack(t: Float, overshoot: Float = 1.70158f): Float {
        val c3 = overshoot + 1f
        return 1f + c3 * (t - 1f).pow(3f) + overshoot * (t - 1f).pow(2f)
    }

    fun approximately(a: Float, b: Float, tolerance: Float = 0.001f): Boolean = abs(a - b) <= tolerance
}
