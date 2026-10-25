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
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
import kotlin.math.abs
import kotlin.math.min

/** Header shared by every panel: a title, a subtitle and a couple of quiet actions. */
private class PanelHeader(context: Context, theme: Theme, title: String, subtitle: String) : FrameLayout(context) {

    val titleView: TextView = app.limbo.keyboard.ui.kit.Ui.textView(context, title, Type.HEADLINE - 1f, 700, theme.text, tracking = -0.02f)
    val subtitleView: TextView = app.limbo.keyboard.ui.kit.Ui.textView(context, subtitle, Type.CAPTION, 500, Colors.alpha(theme.textSecondary, 0.85f), tracking = 0f)
    val actionButton: TextView = app.limbo.keyboard.ui.kit.Ui.textView(context, "", Type.CALLOUT, 600, theme.accent, tracking = 0f)
    val closeButton: View

    init {
        addView(titleView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.START })
        addView(subtitleView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.START })
        actionButton.gravity = Gravity.CENTER
        addView(actionButton, LayoutParams(LayoutParams.WRAP_CONTENT, Dp.dpi(context, 34f)).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            rightMargin = Dp.dpi(context, 40f)
        })
        closeButton = CloseButton(context, theme)
        addView(closeButton, LayoutParams(Dp.dpi(context, 34f), Dp.dpi(context, 34f)).apply { gravity = Gravity.END })

        (titleView.layoutParams as LayoutParams).topMargin = 0
        (subtitleView.layoutParams as LayoutParams).topMargin = Dp.dpi(context, 26f)
    }
}

private class CloseButton(context: Context, private val theme: Theme) : SpringyView(context) {

    private val press = spring(520f, 26f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Dp.dp(context, 1.6f)
    }

    init {
        isClickable = true
    }

    override fun drawContent(canvas: Canvas) {
        val r = Dp.dp(context, 8f)
        paint.color = Colors.alpha(theme.text, 0.1f + 0.08f * press.value)
        canvas.drawCircle(width / 2f, height / 2f, width / 2f - Dp.dp(context, 1f), paint)
        stroke.color = Colors.alpha(theme.text, 0.7f)
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawLine(cx - r * 0.55f, cy - r * 0.55f, cx + r * 0.55f, cy + r * 0.55f, stroke)
        canvas.drawLine(cx + r * 0.55f, cy - r * 0.55f, cx - r * 0.55f, cy + r * 0.55f, stroke)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                press.setTarget(1f)
                wake()
                return true
            }
            MotionEvent.ACTION_UP -> {
                press.setTarget(0f)
                wake()
                Haptics.tap(this, 1)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                press.setTarget(0f)
                wake()
                return true
            }
        }
        return true
    }
}

/** One stored clip. Tap pastes, long press copies back, the corners pin and delete. */
private class ClipCard(
    context: Context,
    private val theme: Theme,
    private val entry: ClipboardStore.Entry,
    private val onPaste: (ClipboardStore.Entry) -> Unit,
    private val onPin: (ClipboardStore.Entry) -> Unit,
    private val onDelete: (ClipboardStore.Entry) -> Unit,
    private val onCopy: (ClipboardStore.Entry) -> Unit
) : SpringyView(context) {

    private val press = spring(480f, 25f)
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var downTime = 0L

    private val cardHeight: Int get() = Dp.dpi(context, if (entry.text.length > 90) 92f else 74f)

    init {
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(android.view.View.MeasureSpec.getSize(widthMeasureSpec), cardHeight)
    }

    private fun pinRect(): RectF {
        val size = Dp.dp(context, 34f)
        return RectF(width - size - Dp.dp(context, 6f), Dp.dp(context, 8f), width - Dp.dp(context, 6f), Dp.dp(context, 8f) + size)
    }

    private fun deleteRect(): RectF {
        val size = Dp.dp(context, 34f)
        return RectF(width - size - Dp.dp(context, 6f), height - size - Dp.dp(context, 6f), width - Dp.dp(context, 6f), height - Dp.dp(context, 6f))
    }

    override fun drawContent(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(Dp.dp(context, 2f), Dp.dp(context, 3f), w - Dp.dp(context, 2f), h - Dp.dp(context, 4f))
        val radius = Dp.dp(context, 16f)

        paint.color = Colors.alpha(Color.BLACK, 0.16f * (if (theme.dark) 1f else 0.4f))
        canvas.drawRoundRect(RectF(rect.left, rect.top + Dp.dp(context, 2.5f), rect.right, rect.bottom + Dp.dp(context, 2.5f)), radius, radius, paint)
        Glass.panel(
            canvas, rect, radius, theme, context,
            Colors.alpha(theme.keyTop, 0.9f + 0.08f * press.value),
            Colors.alpha(theme.keyBottom, 0.85f + 0.1f * press.value),
            false, 0.04f
        )
        if (entry.pinned) {
            paint.color = Colors.alpha(theme.accent, 0.1f)
            canvas.drawRoundRect(rect, radius, radius, paint)
            strokePaint.color = Colors.alpha(theme.accent, 0.45f)
            strokePaint.strokeWidth = Dp.dp(context, 1.2f)
            canvas.drawRoundRect(rect, radius, radius, strokePaint)
        }

        val textLeft = rect.left + Dp.dp(context, 14f)
        val textRight = width - Dp.dp(context, 48f)
        val meta = when (entry.kind) {
            ClipboardStore.Kind.LINK -> I18n.t("Ссылка", "Link")
            ClipboardStore.Kind.EMAIL -> I18n.t("Почта", "Email")
            ClipboardStore.Kind.PHONE -> I18n.t("Номер", "Phone")
            ClipboardStore.Kind.MULTILINE -> I18n.t("Несколько строк", "Several lines")
            ClipboardStore.Kind.TEXT -> I18n.t("Текст", "Text")
        }

        titlePaint.color = theme.text
        titlePaint.typeface = Type.face(480)
        titlePaint.textSize = Dp.sp(context, 14.5f)
        val lines = if (entry.text.length > 90) 2 else 1
        val available = textRight - textLeft
        var y = rect.top + Dp.dp(context, 26f)
        val preview = entry.preview
        val wrapped = wrap(titlePaint, preview, available, lines)
        wrapped.forEach { line ->
            Tx.draw(canvas, line, textLeft, y, titlePaint)
            y += Dp.sp(context, 18f)
        }

        metaPaint.color = Colors.alpha(theme.textSecondary, 0.8f)
        metaPaint.typeface = Type.face(500)
        metaPaint.textSize = Dp.sp(context, 11f)
        val time = android.text.format.DateUtils.getRelativeTimeSpanString(
            entry.createdAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
        ).toString()
        Tx.draw(canvas, "$meta · $time", textLeft, rect.bottom - Dp.dp(context, 10f), metaPaint)

        val pin = pinRect()
        if (entry.pinned) {
            paint.color = Colors.alpha(theme.accent, 0.2f)
            canvas.drawCircle(pin.centerX(), pin.centerY(), pin.width() / 2f - Dp.dp(context, 2f), paint)
        }
        IconPainter.draw(
            canvas, Glyph.STAR, pin.centerX(), pin.centerY(), Dp.dp(context, 15f),
            if (entry.pinned) theme.accent else Colors.alpha(theme.textSecondary, 0.55f),
            glyphStroke, paint.apply { this.color = if (entry.pinned) theme.accent else theme.textSecondary }
        )

        val del = deleteRect()
        IconPainter.draw(
            canvas, Glyph.BACKSPACE, del.centerX(), del.centerY(), Dp.dp(context, 14f),
            Colors.alpha(theme.textSecondary, 0.42f), glyphStroke, paint.apply { this.color = theme.textSecondary }
        )
    }

    private fun wrap(paint: Paint, text: String, maxWidth: Float, maxLines: Int): List<String> {
        val result = ArrayList<String>()
        val words = text.split(' ')
        var line = ""
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (Tx.measure(paint, candidate) > maxWidth && line.isNotEmpty()) {
                result.add(line)
                line = word
                if (result.size == maxLines - 1) break
            } else {
                line = candidate
            }
        }
        if (result.size < maxLines && line.isNotEmpty()) {
            var final = line
            if (Tx.measure(paint, final) > maxWidth) {
                while (final.length > 1 && Tx.measure(paint, final + "…") > maxWidth) final = final.dropLast(1)
                final += "…"
            }
            result.add(final)
        }
        return result.ifEmpty { listOf("") }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = System.currentTimeMillis()
                press.setTarget(1f)
                wake()
                return true
            }
            MotionEvent.ACTION_UP -> {
                press.setTarget(0f)
                wake()
                val pin = pinRect()
                val del = deleteRect()
                val inPin = pin.contains(event.x, event.y)
                val inDelete = del.contains(event.x, event.y)
                when {
                    inPin -> {
                        Haptics.tap(this, 1)
                        onPin(entry)
                    }
                    inDelete -> {
                        Haptics.tap(this, 2)
                        onDelete(entry)
                    }
                    System.currentTimeMillis() - downTime > 420 -> {
                        Haptics.tap(this, 2)
                        onCopy(entry)
                    }
                    else -> {
                        Haptics.tap(this, 0)
                        onPaste(entry)
                    }
                }
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                press.setTarget(0f)
                wake()
                return true
            }
        }
        return true
    }
}

/**
 * Full clipboard history: pinned items first, search, and a one-tap paste.
 */
class ClipboardPanel(context: Context) : FrameLayout(context) {

    var theme: Theme = app.limbo.keyboard.theme.Themes.presets.first()
        set(value) {
            field = value
            rebuild(entries)
        }

    var onPaste: ((String) -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onCopyToSystem: ((ClipboardStore.Entry) -> Unit)? = null

    private val header: PanelHeader
    private val list: LinearLayout
    private val scroll: ScrollView
    private val empty: TextView
    private var entries: List<ClipboardStore.Entry> = emptyList()

    init {
        header = PanelHeader(
            context, theme,
            I18n.t("Буфер обмена", "Clipboard"),
            I18n.t("Нажмите, чтобы вставить", "Tap to paste")
        )
        header.actionButton.setOnClickListener { clearRequested() }
        header.closeButton.setOnClickListener { onClose?.invoke() }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, Dp.dpi(context, 52f)).apply {
            leftMargin = Dp.dpi(context, 18f)
            rightMargin = Dp.dpi(context, 14f)
            topMargin = Dp.dpi(context, 8f)
        })

        list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            clipToPadding = false
            addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            topMargin = Dp.dpi(context, 66f)
            bottomMargin = Dp.dpi(context, 6f)
        })

        empty = app.limbo.keyboard.ui.kit.Ui.textView(
            context,
            I18n.t("Здесь появятся всё, что вы скопируете", "Everything you copy will appear here"),
            Type.CALLOUT, 500, Colors.alpha(theme.textSecondary, 0.7f)
        ).apply { gravity = Gravity.CENTER }
        addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = Dp.dpi(context, 90f)
        })
    }

    private fun clearRequested() {
        Haptics.tap(this, 1)
        entries = entries.filter { it.pinned }
        rebuild(entries)
        onClear?.invoke()
    }

    var onClear: (() -> Unit)? = null

    fun bind(entries: List<ClipboardStore.Entry>) {
        this.entries = entries
        rebuild(entries)
    }

    private fun rebuild(items: List<ClipboardStore.Entry>) {
        header.actionButton.text = I18n.t("Очистить", "Clear")
        header.actionButton.alpha = if (items.any { !it.pinned }) 1f else 0.4f
        scroll.visibility = if (items.isEmpty()) GONE else VISIBLE
        empty.visibility = if (items.isEmpty()) VISIBLE else GONE

        list.removeAllViews()
        items.forEach { entry ->
            val card = ClipCard(
                context, theme, entry,
                onPaste = { item -> onPaste?.invoke(item.text) },
                onPin = { item ->
                    val updated = items.map { if (it.id == item.id) it.copy(pinned = !it.pinned) else it }
                    rebuild(updated)
                    onPin?.invoke(item)
                },
                onDelete = { item ->
                    val updated = items.filterNot { it.id == item.id }
                    rebuild(updated)
                    onDelete?.invoke(item)
                },
                onCopy = { item -> onCopyToSystem?.invoke(item) }
            )
            list.addView(card, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Dp.dpi(context, if (entry.text.length > 90) 92f else 74f)).apply {
                bottomMargin = Dp.dpi(context, 7f)
                leftMargin = Dp.dpi(context, 14f)
                rightMargin = Dp.dpi(context, 14f)
            })
        }
    }

    var onPin: ((ClipboardStore.Entry) -> Unit)? = null
    var onDelete: ((ClipboardStore.Entry) -> Unit)? = null
}
