package app.limbo.keyboard.ui.kit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import app.limbo.keyboard.core.Colors
import app.limbo.keyboard.core.Dp
import app.limbo.keyboard.core.I18n
import app.limbo.keyboard.core.Type
import app.limbo.keyboard.core.Tx
import app.limbo.keyboard.theme.Theme
import app.limbo.keyboard.ui.ThemeAware

/**
 * A colour control that feels like a real picker: a hue rail, then a saturation/brightness pad.
 * Values are plain ARGB ints so they serialise straight into a theme.
 */
class ColorRow(
    context: Context,
    var theme: Theme,
    private val label: String,
    initial: Int,
    private val onChange: (Int) -> Unit
) : LinearLayout(context), ThemeAware {

    private var color = initial
    private val hueRail: HueRail
    private val pad: ColorPad
    private val valueText: android.widget.TextView

    init {
        orientation = VERTICAL
        setPadding(0, Dp.dpi(context, 10f), 0, Dp.dpi(context, 6f))

        val head = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        head.addView(Ui.textView(context, label, Type.BODY - 0.5f, 600, theme.text, tracking = -0.012f),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        valueText = Ui.textView(context, Colors.toHex(color), Type.CAPTION, 550, Colors.alpha(theme.textSecondary, 0.9f)).apply {
            typeface = Type.mono(Colors.alpha(theme.textSecondary, 0.9f), Dp.sp(context, 11.5f)).typeface
        }
        head.addView(valueText)
        addView(head, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(context, 30f)))

        hueRail = HueRail(context, theme, color) { hue -> setColor(Colors.hsv(hue, Colors.saturation(color).coerceAtLeast(0.25f), Colors.brightness(color).coerceAtLeast(0.3f))) }
        val railScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(hueRail, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
        addView(railScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(context, 34f)).apply {
            topMargin = Dp.dpi(context, 4f)
        })

        pad = ColorPad(context, theme, color) { picked -> setColor(picked) }
        addView(pad, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(context, 64f)).apply {
            topMargin = Dp.dpi(context, 8f)
        })
    }

    private fun setColor(value: Int) {
        color = value
        pad.setColor(value)
        hueRail.setColor(value)
        valueText.text = Colors.toHex(value)
        onChange(value)
    }

    override fun applyTheme(theme: Theme) {
        this.theme = theme
        pad.applyTheme(theme)
        hueRail.applyTheme(theme)
    }
}

private class HueRail(
    context: Context,
    var theme: Theme,
    initial: Int,
    private val onPick: (Float) -> Unit
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val hues = 14
    private var currentHue = Colors.hue(initial)

    init {
        isClickable = true
    }

    fun setColor(color: Int) {
        currentHue = Colors.hue(color)
        invalidate()
    }

    fun applyTheme(theme: Theme) {
        this.theme = theme
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(Dp.dpi(context, hues * 30f + 8f), Dp.dpi(context, 30f))
    }

    override fun onDraw(canvas: Canvas) {
        val size = Dp.dp(context, 26f)
        val gap = Dp.dp(context, 4f)
        for (i in 0 until hues) {
            val hue = i * (360f / hues)
            val left = Dp.dp(context, 4f) + i * (size + gap)
            rect.set(left, Dp.dp(context, 2f), left + size, Dp.dp(context, 2f) + size)
            paint.color = Colors.hsv(hue, 0.72f, 0.98f)
            canvas.drawRoundRect(rect, size / 2f, size / 2f, paint)
            val isCurrent = kotlin.math.abs(hue - currentHue) < 360f / hues / 2f || kotlin.math.abs(hue - currentHue) > 360f - 360f / hues / 2f
            if (isCurrent) {
                stroke.color = Color.WHITE
                stroke.strokeWidth = Dp.dp(context, 2.4f)
                canvas.drawRoundRect(rect, size / 2f, size / 2f, stroke)
                stroke.color = Colors.alpha(Color.BLACK, 0.25f)
                stroke.strokeWidth = Dp.dp(context, 1f)
                rect.inset(Dp.dp(context, 3f), Dp.dp(context, 3f))
                canvas.drawRoundRect(rect, size / 2f, size / 2f, stroke)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val size = Dp.dp(context, 26f)
            val gap = Dp.dp(context, 4f)
            val index = ((event.x - Dp.dp(context, 4f)) / (size + gap)).toInt().coerceIn(0, hues - 1)
            Haptics.tap(this, 0)
            performClick()
            onPick(index * (360f / hues))
        }
        return true
    }
}

private class ColorPad(
    context: Context,
    var theme: Theme,
    initial: Int,
    private val onPick: (Int) -> Unit
) : View(context) {

    private var hue = Colors.hue(initial)
    private var saturation = Colors.saturation(initial).coerceAtLeast(0.05f)
    private var brightness = Colors.brightness(initial).coerceAtLeast(0.05f)
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        isClickable = true
    }

    fun setColor(color: Int) {
        hue = Colors.hue(color)
        saturation = Colors.saturation(color)
        brightness = Colors.brightness(color)
        invalidate()
    }

    fun applyTheme(theme: Theme) {
        this.theme = theme
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        rect.set(0f, 0f, w, h)
        val radius = Dp.dp(context, 12f)

        paint.shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(Color.WHITE, Colors.hsv(hue, 1f, 1f)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.TRANSPARENT, Color.BLACK),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null

        knob.color = Color.WHITE
        knob.strokeWidth = Dp.dp(context, 2f)
        val kx = saturation * w
        val ky = (1f - brightness) * h
        canvas.drawCircle(kx.coerceIn(Dp.dp(context, 6f), w - Dp.dp(context, 6f)), ky.coerceIn(Dp.dp(context, 6f), h - Dp.dp(context, 6f)), Dp.dp(context, 7f), knob)
        knob.color = Colors.alpha(Color.BLACK, 0.25f)
        knob.strokeWidth = Dp.dp(context, 1f)
        canvas.drawCircle(kx.coerceIn(Dp.dp(context, 6f), w - Dp.dp(context, 6f)), ky.coerceIn(Dp.dp(context, 6f), h - Dp.dp(context, 6f)), Dp.dp(context, 8.5f), knob)

        // Current colour chip in the corner
        paint.color = Colors.hsv(hue, saturation, brightness)
        canvas.drawCircle(w - Dp.dp(context, 16f), h - Dp.dp(context, 16f), Dp.dp(context, 10f), paint)
        knob.color = Colors.alpha(Color.WHITE, 0.85f)
        canvas.drawCircle(w - Dp.dp(context, 16f), h - Dp.dp(context, 16f), Dp.dp(context, 10f), knob)
    }

    private fun update(x: Float, y: Float) {
        saturation = (x / width).coerceIn(0f, 1f)
        brightness = (1f - (y / height)).coerceIn(0f, 1f)
        invalidate()
        onPick(Colors.hsv(hue, saturation.coerceAtLeast(0.02f), brightness.coerceAtLeast(0.02f)))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                Haptics.tap(this, 0)
                update(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                update(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }
}
