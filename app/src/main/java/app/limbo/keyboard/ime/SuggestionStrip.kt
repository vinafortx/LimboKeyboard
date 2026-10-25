package app.limbo.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import app.limbo.keyboard.core.Colors
import app.limbo.keyboard.core.Dp
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

/**
 * The strip above the keys. It shows completions while you type and quietly offers the last
 * things you copied when there is nothing to complete.
 */
class SuggestionStrip(context: Context) : SpringyView(context) {

    var theme: Theme = app.limbo.keyboard.theme.Themes.presets.first()
        set(value) {
            field = value
            invalidate()
        }

    var onPick: ((String) -> Unit)? = null
    var onClipboardPick: ((String) -> Unit)? = null
    var onOpenClipboard: (() -> Unit)? = null

    private data class Chip(val text: String, val clipboard: Boolean)

    private val chips = ArrayList<Chip>()
    private val presses = ArrayList<Spring>()
    private var activeIndex = -1
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glyphStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    var style: MotionStyle = MotionStyle.BALANCED

    init {
        isClickable = true
    }

    fun showSuggestions(words: List<String>, prefix: String) {
        val next = ArrayList<Chip>()
        words.take(3).forEach { next.add(Chip(it, false)) }
        if (next.isEmpty()) {
            activeIndex = -1
        }
        apply(next, prefix)
    }

    fun showClipboard(items: List<ClipboardStore.Entry>) {
        val next = ArrayList<Chip>()
        items.take(2).forEach { next.add(Chip(it.preview, true)) }
        apply(next, "")
    }

    fun clearChips() = apply(emptyList(), "")

    private fun apply(next: List<Chip>, prefix: String) {
        chips.clear()
        chips.addAll(next)
        while (presses.size < chips.size) presses.add(spring(style.stiffness, style.damping))
        invalidate()
    }

    private var prefixText: String = ""

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(android.view.View.MeasureSpec.getSize(widthMeasureSpec), Dp.dpi(context, 46f))
    }

    override fun drawContent(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return

        if (chips.isEmpty()) {
            textPaint.color = Colors.alpha(theme.textSecondary, 0.55f)
            textPaint.typeface = Type.face(500)
            textPaint.textSize = Dp.sp(context, 12.5f)
            val hint = app.limbo.keyboard.core.I18n.t(
                "Limbo — печатайте свободно",
                "Limbo — type freely"
            )
            Tx.drawCenter(canvas, hint, w / 2f, h / 2f, textPaint, Dp.dp(context, 0.5f))
            return
        }

        val padding = Dp.dp(context, 6f)
        val gap = Dp.dp(context, 5f)
        val chipWidth = (w - padding * 2f - gap * (chips.size - 1)) / chips.size
        val chipHeight = h - Dp.dp(context, 8f)
        chips.forEachIndexed { index, chip ->
            val left = padding + index * (chipWidth + gap)
            rect.set(left, Dp.dp(context, 4f), left + chipWidth, Dp.dp(context, 4f) + chipHeight)
            val radius = Dp.dp(context, 13f)
            val press = presses.getOrNull(index)?.value ?: 0f

            if (press > 0.01f) {
                paint.color = Colors.alpha(theme.accent, 0.14f * press)
                canvas.drawRoundRect(rect, radius, radius, paint)
            }

            val glyphSpace = if (chip.clipboard) Dp.dp(context, 20f) else 0f
            if (chip.clipboard) {
                IconPainter.draw(
                    canvas, Glyph.CLIPBOARD,
                    rect.left + Dp.dp(context, 13f), rect.centerY(),
                    Dp.dp(context, 13f),
                    Colors.alpha(theme.textSecondary, 0.7f), glyphStroke, paint.apply { this.color = theme.textSecondary }
                )
            }

            textPaint.color = if (chip.clipboard) Colors.alpha(theme.textSecondary, 0.95f) else theme.text
            textPaint.typeface = Type.face(if (chip.clipboard) 450 else 550)
            textPaint.textSize = Dp.sp(context, 14.5f)
            val maxTextWidth = chipWidth - Dp.dp(context, 16f) - glyphSpace
            val text = fit(textPaint, chip.text, maxTextWidth)
            Tx.draw(
                canvas, text,
                rect.left + Dp.dp(context, 10f) + glyphSpace,
                rect.centerY() + textPaint.textSize * 0.36f,
                textPaint
            )

            if (index != chips.lastIndex) {
                strokePaint.color = Colors.alpha(theme.text, 0.08f)
                strokePaint.strokeWidth = 1f
                val x = rect.right + gap / 2f
                canvas.drawLine(x, rect.top + Dp.dp(context, 8f), x, rect.bottom - Dp.dp(context, 8f), strokePaint)
            }
        }
    }

    private fun fit(paint: Paint, text: String, maxWidth: Float): String {
        if (Tx.measure(paint, text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && Tx.measure(paint, text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    private fun indexAt(x: Float): Int {
        if (chips.isEmpty()) return -1
        val w = width.toFloat()
        val padding = Dp.dp(context, 6f)
        val gap = Dp.dp(context, 5f)
        val chipWidth = (w - padding * 2f - gap * (chips.size - 1)) / chips.size
        for (i in chips.indices) {
            val left = padding + i * (chipWidth + gap)
            if (x >= left && x <= left + chipWidth) return i
        }
        return -1
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val index = indexAt(event.x)
                if (index < 0) return false
                activeIndex = index
                presses.getOrNull(index)?.setTarget(1f)
                wake()
                return true
            }
            MotionEvent.ACTION_UP -> {
                presses.forEach { it.setTarget(0f) }
                wake()
                val index = indexAt(event.x)
                if (index >= 0 && index == activeIndex) {
                    val chip = chips[index]
                    Haptics.tap(this, 1)
                    if (chip.clipboard) onClipboardPick?.invoke(chip.text) else onPick?.invoke(chip.text)
                    performClick()
                }
                activeIndex = -1
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                presses.forEach { it.setTarget(0f) }
                activeIndex = -1
                return true
            }
        }
        return true
    }
}
