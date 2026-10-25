package app.limbo.keyboard.ime

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
import android.widget.LinearLayout
import app.limbo.keyboard.core.Clock
import app.limbo.keyboard.core.Colors
import app.limbo.keyboard.core.Dp
import app.limbo.keyboard.core.I18n
import app.limbo.keyboard.core.MotionStyle
import app.limbo.keyboard.core.Spring
import app.limbo.keyboard.core.Type
import app.limbo.keyboard.core.Tx
import app.limbo.keyboard.layout.Glyph
import app.limbo.keyboard.theme.Theme
import app.limbo.keyboard.ui.kit.Glass
import app.limbo.keyboard.ui.kit.Haptics
import app.limbo.keyboard.ui.kit.IconPainter
import app.limbo.keyboard.ui.kit.SpringyView
import app.limbo.keyboard.ui.kit.Ui

/**
 * A glass sheet that rises over the keyboard for short choices: switching language, picking a
 * theme, or flipping a couple of quick settings without leaving the app you are typing in.
 */
class QuickSheet(context: Context, private val theme: Theme) : FrameLayout(context) {

    data class Item(
        val title: String,
        val subtitle: String? = null,
        val checked: Boolean = false,
        val glyph: Glyph = Glyph.NONE,
        val onTap: (() -> Unit)? = null
    )

    var onDismiss: (() -> Unit)? = null

    private val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val opacity = Spring(360f, 28f)
    private val lift = Spring(320f, 26f)
    private var ticking = false
    private val tick: (Float) -> Unit = { dt ->
        opacity.advance(dt)
        lift.advance(dt)
        card.alpha = opacity.value
        card.translationY = lift.value
        if (!opacity.isActive && !lift.isActive) {
            ticking = false
            Clock.unsubscribe(tick)
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        card.isClickable = true
        card.background = app.limbo.keyboard.ui.kit.GlassDrawable(context, theme, 26f)
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.BOTTOM
        lp.leftMargin = Dp.dpi(context, 12f)
        lp.rightMargin = Dp.dpi(context, 12f)
        lp.bottomMargin = Dp.dpi(context, 12f)
        addView(card, lp)
        card.setPadding(Dp.dpi(context, 8f), Dp.dpi(context, 10f), Dp.dpi(context, 8f), Dp.dpi(context, 12f))
    }

    fun setTitle(title: String, subtitle: String? = null) {
        card.addView(Ui.textView(context, title, Type.HEADLINE - 1f, 700, theme.text, tracking = -0.02f).apply {
            setPadding(Dp.dpi(context, 10f), Dp.dpi(context, 2f), Dp.dpi(context, 10f), if (subtitle == null) Dp.dpi(context, 10f) else Dp.dpi(context, 2f))
        })
        if (subtitle != null) {
            card.addView(Ui.textView(context, subtitle, Type.CAPTION, 500, Colors.alpha(theme.textSecondary, 0.9f)).apply {
                setPadding(Dp.dpi(context, 10f), 0, Dp.dpi(context, 10f), Dp.dpi(context, 10f))
            })
        }
    }

    fun addItems(items: List<Item>) {
        items.forEach { item ->
            val row = SheetRow(context, theme, item)
            row.setOnClickListener {
                Haptics.tap(row, 1)
                item.onTap?.invoke()
            }
            card.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Dp.dpi(context, if (item.subtitle != null) 58f else 48f)))
        }
        if (items.isEmpty()) {
            card.addView(Ui.textView(context, I18n.t("Пока нечего показать", "Nothing here yet"), Type.CALLOUT, 500, Colors.alpha(theme.textSecondary, 0.8f)).apply {
                gravity = Gravity.CENTER
                setPadding(0, Dp.dpi(context, 18f), 0, Dp.dpi(context, 18f))
            })
        }
    }

    fun addCustom(view: View) {
        card.addView(view)
    }

    fun present(parent: ViewGroup) {
        parent.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        opacity.jumpTo(0f, 1f)
        lift.jumpTo(Dp.dp(context, 26f), 0f)
        card.alpha = 0f
        card.translationY = Dp.dp(context, 26f)
        ticking = true
        Clock.subscribe(tick)
    }

    fun dismiss() {
        onDismiss?.invoke()
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            dismiss()
        }
        return true
    }
}

/** A single row inside the quick sheet. */
private class SheetRow(context: Context, private val theme: Theme, private val item: QuickSheet.Item) : SpringyView(context) {

    private val press = spring(520f, 27f)
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        isClickable = true
    }

    override fun drawContent(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (press.value > 0.01f) {
            rect.set(Dp.dp(context, 2f), Dp.dp(context, 2f), w - Dp.dp(context, 2f), h - Dp.dp(context, 2f))
            fill.color = Colors.alpha(theme.accent, 0.12f * press.value)
            canvas.drawRoundRect(rect, Dp.dp(context, 16f), Dp.dp(context, 16f), fill)
        }

        var textLeft = Dp.dp(context, 14f)
        if (item.glyph != Glyph.NONE) {
            IconPainter.draw(
                canvas, item.glyph, textLeft + Dp.dp(context, 10f), h / 2f, Dp.dp(context, 16f),
                theme.text, glyphStroke, fill.apply { this.color = theme.text }
            )
            textLeft += Dp.dp(context, 30f)
        }

        textPaint.color = theme.text
        textPaint.typeface = Type.face(550)
        textPaint.textSize = Dp.sp(context, 15f)
        val baseline = if (item.subtitle == null) h / 2f - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f else h / 2f - Dp.dp(context, 4f)
        Tx.draw(canvas, item.title, textLeft, baseline, textPaint)

        if (item.subtitle != null) {
            subPaint.color = Colors.alpha(theme.textSecondary, 0.85f)
            subPaint.typeface = Type.face(450)
            subPaint.textSize = Dp.sp(context, 12f)
            Tx.draw(canvas, item.subtitle, textLeft, baseline + Dp.sp(context, 16f), subPaint)
        }

        if (item.checked) {
            IconPainter.draw(
                canvas, Glyph.CHECK, w - Dp.dp(context, 24f), h / 2f, Dp.dp(context, 15f),
                theme.accent, glyphStroke, fill
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { press.setTarget(1f); wake(); return true }
            MotionEvent.ACTION_UP -> { press.setTarget(0f); wake(); performClick(); return true }
            MotionEvent.ACTION_CANCEL -> { press.setTarget(0f); wake(); return true }
        }
        return true
    }
}
