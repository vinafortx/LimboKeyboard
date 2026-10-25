package app.limbo.keyboard.ui.kit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import app.limbo.keyboard.core.Clock
import app.limbo.keyboard.core.Colors
import app.limbo.keyboard.core.Dp
import app.limbo.keyboard.core.MotionStyle
import app.limbo.keyboard.core.Spring
import app.limbo.keyboard.core.Type
import app.limbo.keyboard.theme.Theme
import app.limbo.keyboard.theme.Themes

/**
 * A sheet that rises from the bottom over any screen. Used for theme grids, language lists and
 * anything else that deserves a moment of focus.
 */
class OverlaySheet(context: Context, var theme: Theme, title: String, subtitle: String? = null) :
    FrameLayout(context), app.limbo.keyboard.ui.ThemeAware {

    var onDismiss: (() -> Unit)? = null

    val card: LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val body: LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val scrimAlpha = Spring(300f, 30f)
    private val lift = Spring(320f, 26f)
    private var ticking = false
    private val tick: (Float) -> Unit = { dt ->
        scrimAlpha.advance(dt)
        lift.advance(dt)
        card.translationY = lift.value
        setBackgroundColor(Colors.alpha(Color.BLACK, 0.42f * scrimAlpha.value.coerceIn(0f, 1f)))
        if (!scrimAlpha.isActive && !lift.isActive) {
            ticking = false
            Clock.unsubscribe(tick)
        }
    }

    init {
        isClickable = true
        card.background = GlassDrawable(context, theme, 28f)
        card.setPadding(Dp.dpi(context, 18f), Dp.dpi(context, 16f), Dp.dpi(context, 18f), Dp.dpi(context, 20f))

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titles = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titles.addView(Ui.textView(context, title, Type.HEADLINE, 700, theme.text, tracking = -0.022f))
        if (subtitle != null) {
            titles.addView(Ui.textView(context, subtitle, Type.CAPTION, 450, Colors.alpha(theme.textSecondary, 0.9f)).apply {
                setPadding(0, Dp.dpi(context, 4f), 0, 0)
            })
        }
        header.addView(titles)
        val close = CloseDot(context, theme)
        close.setOnClickListener { dismiss() }
        header.addView(close, LinearLayout.LayoutParams(Dp.dpi(context, 32f), Dp.dpi(context, 32f)))
        card.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Dp.dpi(context, 12f)
        })
        card.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
            leftMargin = Dp.dpi(context, 10f)
            rightMargin = Dp.dpi(context, 10f)
            bottomMargin = Dp.dpi(context, 10f)
        })
    }

    fun present(parent: ViewGroup) {
        parent.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setBackgroundColor(Color.TRANSPARENT)
        scrimAlpha.jumpTo(0f, 1f)
        lift.jumpTo(Dp.dp(context, 36f), 0f)
        card.translationY = Dp.dp(context, 36f)
        ticking = true
        Clock.subscribe(tick)
    }

    fun dismiss() {
        onDismiss?.invoke()
        (parent as? ViewGroup)?.removeView(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            if (event.y < card.top) dismiss()
        }
        return true
    }

    override fun applyTheme(theme: Theme) {
        this.theme = theme
        card.background = GlassDrawable(context, theme, 28f)
    }
}

private class CloseDot(context: Context, private val theme: Theme) : SpringyView(context) {
    private val press = spring(520f, 26f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Dp.dp(context, 1.6f)
    }

    init { isClickable = true }

    override fun drawContent(canvas: Canvas) {
        paint.color = Colors.alpha(theme.text, 0.09f + 0.08f * press.value)
        canvas.drawCircle(width / 2f, height / 2f, width / 2f - Dp.dp(context, 1f), paint)
        stroke.color = Colors.alpha(theme.text, 0.65f)
        val r = Dp.dp(context, 7f)
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawLine(cx - r, cy - r, cx + r, cy + r, stroke)
        canvas.drawLine(cx + r, cy - r, cx - r, cy + r, stroke)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { press.setTarget(1f); wake(); return true }
            MotionEvent.ACTION_UP -> { press.setTarget(0f); wake(); Haptics.tap(this, 1); performClick(); return true }
            MotionEvent.ACTION_CANCEL -> { press.setTarget(0f); wake(); return true }
        }
        return true
    }
}

/** Grid of theme previews used by the home screen, settings and onboarding. */
fun themeGrid(
    context: Context,
    theme: Theme,
    items: List<Theme>,
    selectedId: String,
    columns: Int = 3,
    onPick: (Theme) -> Unit
): LinearLayout {
    val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    var row: LinearLayout? = null
    items.forEachIndexed { index, item ->
        if (index % columns == 0) {
            row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setPadding(0, 0, 0, Dp.dpi(context, 10f))
            }
            container.addView(row)
        }
        val swatch = ThemeSwatch(context, item, item.id == selectedId, onPick = { onPick(item) })
        val params = LinearLayout.LayoutParams(0, Dp.dpi(context, 118f), 1f)
        params.rightMargin = if ((index + 1) % columns == 0) 0 else Dp.dpi(context, 10f)
        row?.addView(swatch, params)
    }
    // Fill the trailing gap of the last row so swatches keep the same width.
    val remainder = items.size % columns
    if (remainder != 0) {
        repeat(columns - remainder) {
            row?.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        }
    }
    return container
}
