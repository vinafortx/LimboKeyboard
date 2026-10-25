package app.limbo.keyboard.ui.kit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.limbo.keyboard.core.Colors
import app.limbo.keyboard.core.Dp
import app.limbo.keyboard.core.Type
import app.limbo.keyboard.theme.Theme
import kotlin.math.max
import kotlin.math.min

/** A soft, layered wallpaper that the glass surfaces sit on. */
class WallpaperDrawable(private val ctx: Context, var theme: Theme) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grainPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.width() == 0 || bounds.height() == 0) return
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()

        paint.shader = android.graphics.LinearGradient(
            0f, 0f, w * 0.35f, h,
            theme.wallTop, theme.wallBottom, Shader.TileMode.CLAMP
        )
        paint.alpha = 255
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.shader = RadialGradient(
            w * 0.16f, h * 0.08f, w * 0.95f,
            intArrayOf(Colors.alpha(theme.accent, if (theme.dark) 0.34f else 0.3f), Colors.alpha(theme.accent, 0f)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.shader = RadialGradient(
            w * 1.05f, h * 0.55f, w * 0.9f,
            intArrayOf(Colors.alpha(Colors.shiftHue(theme.accent, 62f), 0.24f), Colors.alpha(theme.accent, 0f)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)

        // Vignette keeps the eye in the middle of the screen.
        paint.shader = RadialGradient(
            w * 0.5f, h * 0.42f, max(w, h) * 0.95f,
            intArrayOf(Color.TRANSPARENT, Colors.alpha(Color.BLACK, if (theme.dark) 0.35f else 0.12f)),
            floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        drawGrain(canvas, w, h)
    }

    private fun drawGrain(canvas: Canvas, w: Float, h: Float) {
        val source = Glass.grain(ctx)
        grainPaint.shader = android.graphics.BitmapShader(source, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        grainPaint.alpha = 7
        canvas.drawRect(0f, 0f, w, h, grainPaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
}

/** Frosted background for cards, sheets and bars. */
class GlassDrawable(
    private val ctx: Context,
    var theme: Theme,
    var radiusDp: Float = 26f,
    var strength: Float = 1f,
    var strongEdge: Boolean = true
) : Drawable() {

    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        val radius = Dp.dp(ctx, radiusDp)
        val top = Colors.alpha(theme.panelTop, 0.92f * strength)
        val bottom = Colors.alpha(theme.panelBottom, 0.86f * strength)
        Glass.drawShadow(canvas, rect, radius, theme.shadow * 2f * strength, theme.dark)
        Glass.panel(canvas, rect, radius, theme, ctx, top, bottom, strongEdge, 0.05f)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

/** Helpers that keep activities short and typography consistent. */
object Ui {

    fun px(context: Context, dp: Float): Float = Dp.dp(context, dp)

    fun textView(
        context: Context,
        text: CharSequence,
        sizeSp: Float,
        weight: Int = 500,
        color: Int = Color.WHITE,
        tracking: Float = -0.01f,
        lineSpacing: Float = 1.22f
    ): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        typeface = Type.face(weight)
        setTextColor(color)
        letterSpacing = tracking
        setLineSpacing(0f, lineSpacing)
        includeFontPadding = false
        isSingleLine = false
        background = null
    }

    fun title(context: Context, text: CharSequence, theme: Theme, size: Float = Type.TITLE): TextView =
        textView(context, text, size, 700, theme.text, tracking = -0.025f)

    fun body(context: Context, text: CharSequence, theme: Theme, size: Float = Type.BODY): TextView =
        textView(context, text, size, 450, theme.textSecondary, tracking = -0.005f, lineSpacing = 1.32f)

    fun caption(context: Context, text: CharSequence, theme: Theme): TextView =
        textView(context, text, Type.CAPTION, 600, Colors.alpha(theme.textSecondary, 0.9f), tracking = 0.02f)

    fun section(context: Context, text: CharSequence, theme: Theme): TextView =
        textView(context, text.toString().uppercase(), Type.MICRO + 0.5f, 700, Colors.alpha(theme.textSecondary, 0.75f), tracking = 0.14f)

    fun row(context: Context, wrap: Boolean = false): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            if (wrap) LinearLayout.LayoutParams.WRAP_CONTENT else LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    fun column(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    fun space(context: Context, dp: Float): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, Dp.dpi(context, dp))
    }

    fun gap(context: Context, dp: Float): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(Dp.dpi(context, dp), 1)
    }

    fun margin(view: View, left: Float = 0f, top: Float = 0f, right: Float = 0f, bottom: Float = 0f, context: Context) {
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        lp.setMargins(Dp.dpi(context, left), Dp.dpi(context, top), Dp.dpi(context, right), Dp.dpi(context, bottom))
        view.layoutParams = lp
    }

    /** A frosted card that stacks children vertically. */
    fun card(context: Context, theme: Theme, paddingDp: Float = 18f, radiusDp: Float = 24f): LinearLayout =
        column(context).apply {
            background = GlassDrawable(context, theme, radiusDp)
            setPadding(Dp.dpi(context, paddingDp), Dp.dpi(context, paddingDp), Dp.dpi(context, paddingDp), Dp.dpi(context, paddingDp))
            clipToOutline = false
        }

    /** A pill with a label and a chevron, used as a list row inside cards. */
    fun chipLabel(context: Context, text: CharSequence, theme: Theme, active: Boolean): TextView =
        textView(
            context,
            text,
            Type.CALLOUT,
            600,
            if (active) theme.accentText else theme.text,
            tracking = 0f
        ).apply {
            gravity = Gravity.CENTER
            val ctx = context
            background = object : Drawable() {
                private val rect = RectF()
                override fun draw(canvas: Canvas) {
                    rect.set(bounds)
                    val radius = rect.height() / 2f
                    if (active) {
                        Glass.wash(canvas, rect, radius, theme.accent, 0.95f)
                    } else {
                        Glass.panel(canvas, rect, radius, theme, ctx, Colors.alpha(theme.keyTop, 0.7f), Colors.alpha(theme.keyBottom, 0.65f), false, 0.04f)
                    }
                }
                override fun setAlpha(a: Int) {}
                override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
                @Deprecated("Deprecated in Drawable")
                override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
            }
            setPadding(Dp.dpi(context, 16f), Dp.dpi(context, 9f), Dp.dpi(context, 16f), Dp.dpi(context, 9f))
        }

    /** Rounded icon plate used in onboarding and settings. */
    fun iconPlate(context: Context, theme: Theme, sizeDp: Float, glyph: app.limbo.keyboard.layout.Glyph): FrameLayout {
        val size = Dp.dpi(context, sizeDp)
        val frame = FrameLayout(context)
        frame.layoutParams = LinearLayout.LayoutParams(size, size)
        val icon = GlyphView(context, theme, glyph)
        frame.addView(icon, FrameLayout.LayoutParams(size, size))
        return frame
    }

    fun scrim(): Int = Color.argb(140, 0, 0, 0)

    fun dp(context: Context, value: Float): Int = Dp.dpi(context, value)
}

/**
 * Small line icons painted on a canvas. A single set keeps every glyph optically balanced
 * instead of mixing icon fonts and platform drawables.
 */
class GlyphView(
    context: Context,
    var theme: Theme,
    var glyph: app.limbo.keyboard.layout.Glyph,
    var color: Int = theme.text
) : View(context) {

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        IconPainter.draw(canvas, glyph, cx, cy, size * 0.62f, color, stroke, fillPaint)
    }
}

/** All glyph geometry in one place, drawn in a normalised square. */
object IconPainter {

    fun draw(
        canvas: Canvas,
        glyph: app.limbo.keyboard.layout.Glyph,
        cx: Float,
        cy: Float,
        size: Float,
        color: Int,
        stroke: Paint,
        fill: Paint
    ) {
        when (glyph) {
            app.limbo.keyboard.layout.Glyph.NONE -> return
            else -> {}
        }
        val s = size / 2f
        stroke.color = color
        stroke.strokeWidth = size * 0.085f
        stroke.style = Paint.Style.STROKE
        fill.color = color
        fill.style = Paint.Style.FILL

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            canvas.drawLine(cx + x1 * s, cy + y1 * s, cx + x2 * s, cy + y2 * s, stroke)
        }

        fun path(block: Path.() -> Unit) {
            val p = Path()
            p.block()
            canvas.drawPath(p, stroke)
        }

        when (glyph) {
            app.limbo.keyboard.layout.Glyph.SHIFT, app.limbo.keyboard.layout.Glyph.SHIFT_ACTIVE -> {
                path {
                    moveTo(cx, cy - s * 0.85f)
                    lineTo(cx + s * 0.9f, cy + s * 0.05f)
                    lineTo(cx + s * 0.35f, cy + s * 0.05f)
                    lineTo(cx + s * 0.35f, cy + s * 0.8f)
                    lineTo(cx - s * 0.35f, cy + s * 0.8f)
                    lineTo(cx - s * 0.35f, cy + s * 0.05f)
                    lineTo(cx - s * 0.9f, cy + s * 0.05f)
                    close()
                }
                if (glyph == app.limbo.keyboard.layout.Glyph.SHIFT_ACTIVE) canvas.drawPath(Path().apply {
                    moveTo(cx, cy - s * 0.85f)
                    lineTo(cx + s * 0.9f, cy + s * 0.05f)
                    lineTo(cx + s * 0.35f, cy + s * 0.05f)
                    lineTo(cx + s * 0.35f, cy + s * 0.8f)
                    lineTo(cx - s * 0.35f, cy + s * 0.8f)
                    lineTo(cx - s * 0.35f, cy + s * 0.05f)
                    lineTo(cx - s * 0.9f, cy + s * 0.05f)
                    close()
                }, fill)
            }

            app.limbo.keyboard.layout.Glyph.CAPS -> {
                path {
                    moveTo(cx, cy - s * 0.85f)
                    lineTo(cx + s * 0.9f, cy + s * 0.05f)
                    lineTo(cx + s * 0.35f, cy + s * 0.05f)
                    lineTo(cx + s * 0.35f, cy + s * 0.8f)
                    lineTo(cx - s * 0.35f, cy + s * 0.8f)
                    lineTo(cx - s * 0.35f, cy + s * 0.05f)
                    lineTo(cx - s * 0.9f, cy + s * 0.05f)
                    close()
                }
                line(-0.75f, 0.95f, 0.75f, 0.95f)
            }

            app.limbo.keyboard.layout.Glyph.BACKSPACE -> {
                path {
                    moveTo(cx - s * 0.95f, cy)
                    lineTo(cx - s * 0.35f, cy - s * 0.65f)
                    lineTo(cx + s * 0.9f, cy - s * 0.65f)
                    lineTo(cx + s * 0.9f, cy + s * 0.65f)
                    lineTo(cx - s * 0.35f, cy + s * 0.65f)
                    close()
                }
                line(0.15f, -0.22f, 0.6f, 0.22f)
                line(0.6f, -0.22f, 0.15f, 0.22f)
            }

            app.limbo.keyboard.layout.Glyph.ENTER -> {
                path {
                    moveTo(cx + s * 0.85f, cy - s * 0.7f)
                    lineTo(cx + s * 0.85f, cy + s * 0.15f)
                    lineTo(cx - s * 0.75f, cy + s * 0.15f)
                }
                path {
                    moveTo(cx - s * 0.25f, cy - s * 0.2f)
                    lineTo(cx - s * 0.75f, cy + s * 0.15f)
                    lineTo(cx - s * 0.25f, cy + s * 0.5f)
                }
            }

            app.limbo.keyboard.layout.Glyph.GLOBE -> {
                canvas.drawCircle(cx, cy, s * 0.85f, stroke)
                canvas.drawOval(cx - s * 0.38f, cy - s * 0.85f, cx + s * 0.38f, cy + s * 0.85f, stroke)
                line(-0.85f, 0f, 0.85f, 0f)
                canvas.drawArc(cx - s * 0.85f, cy - s * 0.55f, cx + s * 0.85f, cy + s * 0.55f, 200f, 140f, false, stroke)
            }

            app.limbo.keyboard.layout.Glyph.CLIPBOARD -> {
                path {
                    moveTo(cx - s * 0.62f, cy - s * 0.72f)
                    lineTo(cx + s * 0.62f, cy - s * 0.72f)
                    lineTo(cx + s * 0.62f, cy + s * 0.85f)
                    lineTo(cx - s * 0.62f, cy + s * 0.85f)
                    close()
                }
                canvas.drawRoundRect(
                    cx - s * 0.28f, cy - s * 0.95f, cx + s * 0.28f, cy - s * 0.55f,
                    s * 0.12f, s * 0.12f, stroke
                )
                line(-0.3f, 0.02f, 0.3f, 0.02f)
                line(-0.3f, 0.38f, 0.3f, 0.38f)
            }

            app.limbo.keyboard.layout.Glyph.EMOJI -> {
                canvas.drawCircle(cx, cy, s * 0.85f, stroke)
                canvas.drawCircle(cx - s * 0.3f, cy - s * 0.2f, s * 0.1f, fill)
                canvas.drawCircle(cx + s * 0.3f, cy - s * 0.2f, s * 0.1f, fill)
                canvas.drawArc(cx - s * 0.45f, cy - s * 0.25f, cx + s * 0.45f, cy + s * 0.55f, 20f, 140f, false, stroke)
            }

            app.limbo.keyboard.layout.Glyph.GEAR -> {
                val teeth = 8
                val outer = s * 0.82f
                val inner = s * 0.52f
                val p = Path()
                for (i in 0 until teeth * 2) {
                    val angle = Math.PI * i / teeth
                    val radius = if (i % 2 == 0) outer else inner
                    val x = cx + (kotlin.math.cos(angle) * radius).toFloat()
                    val y = cy + (kotlin.math.sin(angle) * radius).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
                stroke.strokeWidth = size * 0.07f
                canvas.drawPath(p, stroke)
                canvas.drawCircle(cx, cy, s * 0.26f, stroke)
            }

            app.limbo.keyboard.layout.Glyph.ARROW_LEFT -> {
                line(-0.7f, 0f, 0.7f, 0f)
                path {
                    moveTo(cx - s * 0.25f, cy - s * 0.42f)
                    lineTo(cx - s * 0.7f, cy)
                    lineTo(cx - s * 0.25f, cy + s * 0.42f)
                }
            }

            app.limbo.keyboard.layout.Glyph.ARROW_RIGHT -> {
                line(-0.7f, 0f, 0.7f, 0f)
                path {
                    moveTo(cx + s * 0.25f, cy - s * 0.42f)
                    lineTo(cx + s * 0.7f, cy)
                    lineTo(cx + s * 0.25f, cy + s * 0.42f)
                }
            }

            app.limbo.keyboard.layout.Glyph.TAB -> {
                line(-0.75f, -0.2f, 0.75f, -0.2f)
                line(-0.75f, 0.45f, 0.75f, 0.45f)
                line(-0.75f, -0.2f, -0.75f, 0.45f)
            }

            app.limbo.keyboard.layout.Glyph.HIDE -> {
                line(-0.6f, 0.45f, 0f, -0.15f)
                line(0f, -0.15f, 0.6f, 0.45f)
            }

            app.limbo.keyboard.layout.Glyph.SPACE -> Unit

            app.limbo.keyboard.layout.Glyph.MIC -> {
                canvas.drawRoundRect(cx - s * 0.28f, cy - s * 0.85f, cx + s * 0.28f, cy + s * 0.15f, s * 0.3f, s * 0.3f, stroke)
                canvas.drawArc(cx - s * 0.5f, cy - s * 0.35f, cx + s * 0.5f, cy + s * 0.55f, 0f, 180f, false, stroke)
                line(0f, 0.55f, 0f, 0.85f)
            }

            app.limbo.keyboard.layout.Glyph.SEARCH -> {
                canvas.drawCircle(cx - s * 0.15f, cy - s * 0.15f, s * 0.55f, stroke)
                line(0.3f, 0.3f, 0.75f, 0.75f)
            }

            app.limbo.keyboard.layout.Glyph.PLUS -> {
                line(-0.6f, 0f, 0.6f, 0f)
                line(0f, -0.6f, 0f, 0.6f)
            }

            app.limbo.keyboard.layout.Glyph.CHECK -> {
                path {
                    moveTo(cx - s * 0.6f, cy + s * 0.02f)
                    lineTo(cx - s * 0.15f, cy + s * 0.48f)
                    lineTo(cx + s * 0.65f, cy - s * 0.45f)
                }
            }

            app.limbo.keyboard.layout.Glyph.CHEVRON -> {
                path {
                    moveTo(cx - s * 0.25f, cy - s * 0.45f)
                    lineTo(cx + s * 0.25f, cy)
                    lineTo(cx - s * 0.25f, cy + s * 0.45f)
                }
            }

            app.limbo.keyboard.layout.Glyph.DOT -> canvas.drawCircle(cx, cy, s * 0.16f, fill)

            app.limbo.keyboard.layout.Glyph.STAR -> {
                val p = Path()
                val outer = s * 0.85f
                val inner = s * 0.36f
                for (i in 0 until 10) {
                    val angle = -Math.PI / 2 + Math.PI * i / 5
                    val radius = if (i % 2 == 0) outer else inner
                    val x = cx + (kotlin.math.cos(angle) * radius).toFloat()
                    val y = cy + (kotlin.math.sin(angle) * radius).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
                canvas.drawPath(p, if (glyph == app.limbo.keyboard.layout.Glyph.STAR) fill else stroke)
            }

            app.limbo.keyboard.layout.Glyph.NONE -> Unit
        }
    }
}
