package app.limbo.keyboard.ui.kit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import app.limbo.keyboard.core.Colors
import app.limbo.keyboard.theme.Theme
import kotlin.math.min
import kotlin.random.Random

/**
 * The visual language of the app: frosted panels, softly lit keys and a whisper of grain.
 *
 * Real backdrop blur is only available from Android 12 and only for privileged surfaces, so the
 * glass here is built the way a designer would on paper: a tinted gradient, a bright inner edge,
 * a diagonal specular sweep and a fine noise layer that stops large areas from banding.
 */
object Glass {

    private var grain: Bitmap? = null
    private val grainPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val rect = RectF()
    private val shaderCache = HashMap<Long, LinearGradient>()

    private fun gradient(key: Long, top: Float, bottom: Float, c1: Int, c2: Int, width: Float): LinearGradient {
        val cached = shaderCache[key]
        if (cached != null) return cached
        if (shaderCache.size > 380) shaderCache.clear()
        val g = LinearGradient(0f, top, 0f, bottom, c1, c2, Shader.TileMode.CLAMP)
        shaderCache[key] = g
        return g
    }

    private fun keyOf(a: Int, b: Int, w: Int, h: Int): Long =
        (a.toLong() shl 40) xor (b.toLong() shl 16) xor (w.toLong() shl 8) xor h.toLong()

    fun grain(context: Context): Bitmap {
        grain?.let { return it }
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val random = Random(7)
        val pixels = IntArray(size * size)
        for (i in pixels.indices) {
            val v = random.nextInt(0, 255)
            pixels[i] = Color.argb(255, v, v, v)
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        grain = bitmap
        return bitmap
    }

    /** Fine grain overlay, tiled. Kept below 6% alpha so it reads as material, not noise. */
    fun drawGrain(canvas: Canvas, area: RectF, radius: Float, alpha: Float, context: Context) {
        if (alpha <= 0.001f) return
        val bitmap = grain(context)
        grainPaint.shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        grainPaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        grainPaint.setShader(grainPaint.shader)
        path.reset()
        path.addRoundRect(area, radius, radius, Path.Direction.CW)
        canvas.drawPath(path, grainPaint)
    }

    /** Soft ambient lift under a surface. Layered rectangles are much cheaper than a blur. */
    fun drawShadow(canvas: Canvas, area: RectF, radius: Float, depth: Float, dark: Boolean) {
        if (depth <= 0.01f) return
        val base = if (dark) Color.BLACK else Color.rgb(74, 86, 108)
        val layers = 4
        for (i in layers downTo 1) {
            val spread = depth * 2.2f * i
            val alpha = (depth * 0.026f) / i
            fill.shader = null
            fill.color = Colors.alpha(base, alpha)
            rect.set(area.left - spread * 0.35f, area.top + spread * 0.55f, area.right + spread * 0.35f, area.bottom + spread)
            canvas.drawRoundRect(rect, radius + spread * 0.6f, radius + spread * 0.6f, fill)
        }
    }

    /**
     * A frosted sheet.
     *
     * There is no way to sample what sits behind an input-method window, so the frost is built
     * the way a designer would draw it: a milky, almost flat body whose two stops differ by a
     * couple of percent, one hairline of light along the top edge, a fine grain that scatters
     * light, and a border you have to look for.
     */
    fun panel(
        canvas: Canvas,
        area: RectF,
        radius: Float,
        theme: Theme,
        context: Context,
        top: Int = theme.panelTop,
        bottom: Int = theme.panelBottom,
        strongTopEdge: Boolean = true,
        grainAlpha: Float = 0.028f,
        frost: Float = theme.frost
    ) {
        val flat = Colors.distance(top, bottom) < 0.05f
        if (flat) {
            fill.shader = null
            fill.color = top
            canvas.drawRoundRect(area, radius, radius, fill)
        } else {
            fill.shader = gradient(
                keyOf(top, bottom, area.width().toInt(), area.height().toInt()),
                0f,
                area.bottom,
                top,
                bottom,
                area.width()
            )
            canvas.drawRoundRect(area, radius, radius, fill)
            fill.shader = null
        }

        // Milk: the veil that makes the sheet read as frosted rather than merely transparent.
        val veil = if (theme.dark) 0.010f + 0.030f * frost else 0.026f + 0.060f * frost
        fill.color = Colors.alpha(Color.WHITE, veil)
        canvas.drawRoundRect(area, radius, radius, fill)

        if (strongTopEdge) {
            // The single glass cue: one pixel of light where a real sheet catches the window.
            fill.color = Colors.alpha(
                Color.WHITE,
                if (theme.dark) 0.055f + 0.055f * theme.sheen * 2f else 0.30f + 0.1f * theme.sheen
            )
            rect.set(area.left + radius * 0.6f, area.top + 0.5f, area.right - radius * 0.6f, area.top + 1.5f)
            canvas.drawRoundRect(rect, 1f, 1f, fill)
        }

        drawGrain(canvas, area, radius, grainAlpha * (if (theme.dark) 1f else 0.6f), context)

        stroke.color = if (theme.dark) {
            Colors.alpha(Color.WHITE, 0.05f + 0.06f * theme.keyStroke)
        } else {
            Colors.alpha(Color.BLACK, 0.05f)
        }
        stroke.strokeWidth = 1f
        rect.set(area.left + 0.5f, area.top + 0.5f, area.right - 0.5f, area.bottom - 0.5f)
        canvas.drawRoundRect(rect, radius - 0.5f, radius - 0.5f, stroke)
    }

    /**
     * A single key. [press] is 0..1 and comes from the key's own spring, so a tap keeps the
     * physical feel even when the finger slides to a neighbour.
     */
    fun key(
        canvas: Canvas,
        area: RectF,
        radius: Float,
        theme: Theme,
        context: Context,
        press: Float,
        isModifier: Boolean,
        isAccent: Boolean
    ) {
        val style = theme.keyStyle

        if (isAccent) {
            drawAccentKey(canvas, area, radius, theme, press)
            return
        }

        val faceColor = when {
            isModifier -> Colors.mix(theme.keyTop, theme.panelTop, 0.35f)
            else -> theme.keyTop
        }
        val faceBottom = when {
            isModifier -> Colors.mix(theme.keyBottom, theme.panelBottom, 0.35f)
            else -> theme.keyBottom
        }
        val pressedTop = if (theme.dark) Colors.mix(faceColor, Color.WHITE, 0.085f) else Colors.mix(faceColor, Color.BLACK, 0.055f)
        val pressedBottom = if (theme.dark) Colors.mix(faceBottom, Color.WHITE, 0.065f) else Colors.mix(faceBottom, Color.BLACK, 0.075f)

        val c1 = Colors.mix(faceColor, pressedTop, press)
        val c2 = Colors.mix(faceBottom, pressedBottom, press)

        when (style) {
            KeyStyleLike.CRYSTAL -> drawCrystalKey(canvas, area, radius, theme, press, c1, c2, isModifier)
            KeyStyleLike.OUTLINE -> drawOutlineKey(canvas, area, radius, theme, press, c1)
            KeyStyleLike.MINIMAL -> {
                fill.shader = null
                fill.color = Colors.alpha(c1, 0.34f + 0.3f * (1f - press))
                canvas.drawRoundRect(area, radius, radius, fill)
            }
            KeyStyleLike.FILLED -> {
                fill.shader = null
                fill.color = c1
                canvas.drawRoundRect(area, radius, radius, fill)
                fill.color = Colors.alpha(Color.WHITE, 0.03f + 0.05f * theme.sheen)
                rect.set(area.left + radius * 0.5f, area.top + 0.5f, area.right - radius * 0.5f, area.top + 1.5f)
                canvas.drawRoundRect(rect, 1f, 1f, fill)
            }
            KeyStyleLike.GLASS, KeyStyleLike.SOFT -> {
                // Frosted cap: flat colour, a hint of vertical falloff, one hairline of light.
                val alpha = when {
                    style == KeyStyleLike.SOFT -> 0.62f + 0.22f * theme.frost
                    isModifier -> 0.70f + 0.18f * theme.frost
                    else -> 0.82f + 0.14f * theme.frost
                }
                fill.shader = null
                fill.color = Colors.alpha(c1, alpha)
                canvas.drawRoundRect(area, radius, radius, fill)

                if (Colors.distance(c1, c2) > 0.015f) {
                    fill.shader = gradient(
                        keyOf(c1, c2, area.width().toInt(), area.height().toInt()),
                        area.top,
                        area.bottom,
                        Colors.alpha(c1, alpha * 0.35f),
                        Colors.alpha(c2, alpha * 0.55f),
                        area.width()
                    )
                    canvas.drawRoundRect(area, radius, radius, fill)
                    fill.shader = null
                }

                // Pressed caps get a faint accent wash instead of a heavy tint.
                if (press > 0.01f) {
                    fill.color = Colors.alpha(theme.accent, 0.07f * press)
                    canvas.drawRoundRect(area, radius, radius, fill)
                }

                fill.color = Colors.alpha(
                    Color.WHITE,
                    (if (theme.dark) 0.06f else 0.42f) * (1f - press * 0.45f)
                )
                rect.set(area.left + radius * 0.55f, area.top + 0.5f, area.right - radius * 0.55f, area.top + 1.5f)
                canvas.drawRoundRect(rect, 1f, 1f, fill)

                stroke.color = if (theme.dark) Colors.alpha(Color.WHITE, 0.055f) else Colors.alpha(Color.BLACK, 0.045f)
                stroke.strokeWidth = 1f
                rect.set(area.left + 0.5f, area.top + 0.5f, area.right - 0.5f, area.bottom - 0.5f)
                canvas.drawRoundRect(rect, radius - 0.5f, radius - 0.5f, stroke)
            }
        }
    }

    /** The primary action: a solid accent cap with one crisp edge of light. */
    private fun drawAccentKey(canvas: Canvas, area: RectF, radius: Float, theme: Theme, press: Float) {
        val base = if (press > 0.01f) Colors.mix(theme.accent, Color.BLACK, 0.07f * press) else theme.accent
        fill.shader = null
        fill.color = base
        canvas.drawRoundRect(area, radius, radius, fill)
        fill.color = Colors.alpha(Color.WHITE, 0.16f * (1f - press * 0.6f))
        rect.set(area.left + radius * 0.55f, area.top + 0.5f, area.right - radius * 0.55f, area.top + 1.5f)
        canvas.drawRoundRect(rect, 1f, 1f, fill)
    }

    /** The optional glossy cap: specular sweep, inner light, a soft lift. Off by default. */
    private fun drawCrystalKey(
        canvas: Canvas,
        area: RectF,
        radius: Float,
        theme: Theme,
        press: Float,
        c1: Int,
        c2: Int,
        isModifier: Boolean
    ) {
        val lift = (1f - press) * theme.shadow * 2.2f
        fill.shader = null
        fill.color = Colors.alpha(Color.BLACK, (0.10f + theme.shadow * 0.16f) * (1f - press * 0.7f) * if (theme.dark) 1f else 0.55f)
        rect.set(area.left, area.top + lift, area.right, area.bottom + lift)
        canvas.drawRoundRect(rect, radius, radius, fill)

        val face = RectF(area.left, area.top, area.right, area.bottom - lift * 0.35f)
        fill.shader = gradient(
            keyOf(c1, c2, face.width().toInt(), face.height().toInt()),
            face.top,
            face.bottom,
            c1,
            c2,
            face.width()
        )
        canvas.drawRoundRect(face, radius, radius, fill)
        fill.shader = null

        fill.color = Colors.alpha(Color.WHITE, (if (theme.dark) 0.14f else 0.7f) * (1f - press * 0.6f))
        rect.set(face.left + radius * 0.4f, face.top + 1f, face.right - radius * 0.4f, face.top + 2.4f)
        canvas.drawRoundRect(rect, 2f, 2f, fill)

        if (theme.sheen > 0.01f) {
            val sweep = Path()
            val w = face.width()
            val h = face.height()
            sweep.reset()
            sweep.moveTo(face.left, face.top + h * 0.62f)
            sweep.lineTo(face.left + w * 0.46f, face.top)
            sweep.lineTo(face.left + w * 0.72f, face.top)
            sweep.lineTo(face.left, face.top + h)
            sweep.close()
            fill.color = Colors.alpha(Color.WHITE, 0.06f * theme.sheen * 3f * (1f - press))
            canvas.drawPath(sweep, fill)
        }

        stroke.color = Colors.alpha(Color.WHITE, if (theme.dark) 0.10f else 0.75f)
        stroke.strokeWidth = 1f
        rect.set(face.left + 0.5f, face.top + 0.5f, face.right - 0.5f, face.bottom - 0.5f)
        canvas.drawRoundRect(rect, radius - 0.5f, radius - 0.5f, stroke)
        if (isModifier) {
            fill.color = Colors.alpha(theme.panelBottom, 0.18f)
            canvas.drawRoundRect(face, radius, radius, fill)
        }
    }

    private fun drawOutlineKey(canvas: Canvas, area: RectF, radius: Float, theme: Theme, press: Float, c1: Int) {
        fill.shader = null
        fill.color = Colors.alpha(c1, 0.12f + 0.06f * press)
        canvas.drawRoundRect(area, radius, radius, fill)
        stroke.color = Colors.alpha(theme.text, 0.18f + 0.18f * press)
        stroke.strokeWidth = 1.2f
        rect.set(area.left + 0.6f, area.top + 0.6f, area.right - 0.6f, area.bottom - 0.6f)
        canvas.drawRoundRect(rect, radius - 0.6f, radius - 0.6f, stroke)
    }

    /** Accent wash used for selected chips, active segments and the caret. */
    fun wash(canvas: Canvas, area: RectF, radius: Float, color: Int, strength: Float) {
        fill.shader = null
        fill.color = Colors.alpha(color, strength)
        canvas.drawRoundRect(area, radius, radius, fill)
    }

    /** A radial bloom placed behind the panel to fake light bouncing off the wallpaper. */
    fun glow(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        if (radius <= 0f) return
        val paint = fill
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(Colors.alpha(color, Colors.alphaOf(color)), Colors.alpha(color, 0f)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
    }

    fun hairline(canvas: Canvas, area: RectF, radius: Float, color: Int, width: Float = 1f) {
        stroke.color = color
        stroke.strokeWidth = width
        rect.set(area.left + width / 2f, area.top + width / 2f, area.right - width / 2f, area.bottom - width / 2f)
        canvas.drawRoundRect(rect, radius, radius, stroke)
    }

    /** Dotted focus ring, used sparingly around the primary action of a screen. */
    fun dottedRing(canvas: Canvas, area: RectF, radius: Float, color: Int, dash: Float, gap: Float) {
        val paint = stroke
        paint.color = color
        paint.strokeWidth = 1.4f
        paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(dash, gap), 0f)
        path.reset()
        path.addRoundRect(area, radius, radius, Path.Direction.CW)
        canvas.drawPath(path, paint)
        paint.pathEffect = null
    }

    fun clipRadius(value: Float, area: RectF): Float = min(value, min(area.width(), area.height()) / 2f)
}

/** Local alias so Glass can compare styles without importing the theme package widely. */
private typealias KeyStyleLike = app.limbo.keyboard.theme.KeyStyle
