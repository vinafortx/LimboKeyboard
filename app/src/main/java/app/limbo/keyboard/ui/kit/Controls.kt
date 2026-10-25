package app.limbo.keyboard.ui.kit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import app.limbo.keyboard.core.Colors
import app.limbo.keyboard.core.Dp
import app.limbo.keyboard.core.I18n
import app.limbo.keyboard.core.Maths.clamp
import app.limbo.keyboard.core.Spring
import app.limbo.keyboard.core.Type
import app.limbo.keyboard.core.Tx
import app.limbo.keyboard.layout.Glyph
import app.limbo.keyboard.theme.Theme
import kotlin.math.abs
import kotlin.math.roundToInt

/** Light haptics with a few discrete strengths, mapped to the platform feedback constants. */
object Haptics {

    fun tap(view: View, strength: Int = 1) {
        val constant = when (strength) {
            0 -> HapticFeedbackConstants.CLOCK_TICK
            2 -> HapticFeedbackConstants.LONG_PRESS
            else -> HapticFeedbackConstants.KEYBOARD_TAP
        }
        view.performHapticFeedback(constant)
    }

    fun native(context: Context, strength: Int = 1) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return
        val duration = when (strength) { 0 -> 8L; 2 -> 22L; else -> 14L }
        val amplitude = when (strength) { 0 -> 60; 2 -> 190; else -> 120 }
        runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        }
    }
}

/** Round pressable button with three visual levels. */
class SoftButton(
    context: Context,
    var theme: Theme,
    text: String,
    var kind: Kind = Kind.PRIMARY,
    var glyph: Glyph = Glyph.NONE
) : SpringyView(context) {

    enum class Kind { PRIMARY, GLASS, QUIET, DANGER }

    var label: String = text
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    private val press = spring(560f, 27f)
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    var heightDp: Float = 54f
    var compact: Boolean = false

    init {
        isClickable = true
        isFocusable = true
        contentDescription = text
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = Dp.dp(context, if (compact) 42f else heightDp)
        val width = when (View.MeasureSpec.getMode(widthMeasureSpec)) {
            View.MeasureSpec.EXACTLY -> View.MeasureSpec.getSize(widthMeasureSpec)
            View.MeasureSpec.AT_MOST -> {
                textPaint.textSize = Dp.sp(context, if (compact) 14f else 15.5f)
                textPaint.typeface = Type.face(600)
                val textWidth = Tx.measure(textPaint, label)
                minOf(View.MeasureSpec.getSize(widthMeasureSpec), (textWidth + Dp.dp(context, 46f)).toInt())
            }
            else -> (Dp.dp(context, 160f)).toInt()
        }
        setMeasuredDimension(width, height.roundToInt())
    }

    override fun drawContent(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val p = press.value
        val inset = Dp.dp(context, 1.5f) + p * Dp.dp(context, 0.8f)
        rect.set(inset, inset, w - inset, h - inset)
        val radius = rect.height() / 2f

        val baseColor = when (kind) {
            Kind.PRIMARY -> theme.accent
            Kind.DANGER -> Color.parseColor("#FF5A5F")
            Kind.GLASS -> Colors.alpha(theme.keyTop, 0.85f)
            Kind.QUIET -> Colors.alpha(theme.keyTop, 0.35f)
        }
        val textColor = when (kind) {
            Kind.PRIMARY, Kind.DANGER -> Colors.on(baseColor)
            else -> theme.text
        }

        if (kind == Kind.PRIMARY || kind == Kind.DANGER) {
            Glass.drawShadow(canvas, rect, radius, 2.2f * (1f - p * 0.4f), true)
            fill.color = Colors.mix(baseColor, Colors.darken(baseColor, 0.16f), p)
            canvas.drawRoundRect(rect, radius, radius, fill)
            fill.color = Colors.alpha(Color.WHITE, 0.16f * (1f - p))
            val highlight = RectF(rect.left + radius * 0.5f, rect.top + 1.5f, rect.right - radius * 0.5f, rect.top + 2.6f)
            canvas.drawRoundRect(highlight, 2f, 2f, fill)
        } else {
            Glass.panel(
                canvas, rect, radius, theme, context,
                Colors.alpha(baseColor, 0.94f), Colors.alpha(theme.keyBottom, 0.8f), kind != Kind.QUIET, 0.05f
            )
            if (p > 0.01f) {
                Glass.wash(canvas, rect, radius, theme.accent, 0.12f * p)
            }
        }

        textPaint.color = textColor
        textPaint.typeface = Type.face(600)
        textPaint.textSize = Dp.sp(context, if (compact) 14f else 15.5f)

        val hasGlyph = glyph != Glyph.NONE
        val glyphSize = (textPaint.textSize * 1.15f)
        val textWidth = if (label.isEmpty()) 0f else Tx.measure(textPaint, label)
        val gap = if (hasGlyph && label.isNotEmpty()) Dp.dp(context, 8f) else 0f
        val total = textWidth + (if (hasGlyph) glyphSize + gap else 0f)
        var cursor = w / 2f - total / 2f

        if (hasGlyph) {
            val size = glyphSize
            IconPainter.draw(
                canvas, glyph, cursor + size / 2f, h / 2f, size * 0.82f, textColor,
                glyphStroke, fill
            )
            cursor += size + gap
        }
        if (label.isNotEmpty()) {
            val metrics = textPaint.fontMetrics
            val baseline = h / 2f - (metrics.ascent + metrics.descent) / 2f
            Tx.draw(canvas, label, cursor, baseline, textPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                press.setTarget(1f)
                wake()
                isPressed = true
                return true
            }
            MotionEvent.ACTION_UP -> {
                press.setTarget(0f)
                wake()
                isPressed = false
                if (event.x >= 0 && event.x <= width && event.y >= 0 && event.y <= height) {
                    Haptics.tap(this, 1)
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                press.setTarget(0f)
                wake()
                isPressed = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

/** iOS-style switch with a spring-loaded knob. */
class Toggle(
    context: Context,
    var theme: Theme,
    initial: Boolean,
    private val onChange: ((Boolean) -> Unit)? = null
) : SpringyView(context) {

    var checked: Boolean = initial
        set(value) {
            if (field == value) return
            field = value
            knob.to(if (value) 1f else 0f)
            wake()
            onChange?.invoke(value)
        }

    private val knob = spring(560f, 26f, if (initial) 1f else 0f)
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        isClickable = true
        contentDescription = I18n.t("Переключатель", "Toggle")
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(Dp.dpi(context, 52f), Dp.dpi(context, 32f))
    }

    override fun drawContent(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = knob.value
        rect.set(0f, 0f, w, h)
        val radius = h / 2f

        fill.color = Colors.mix(Colors.alpha(theme.text, 0.16f), theme.accent, t)
        canvas.drawRoundRect(rect, radius, radius, fill)
        stroke.color = Colors.alpha(Color.WHITE, 0.08f)
        stroke.strokeWidth = 1f
        canvas.drawRoundRect(rect, radius, radius, stroke)

        val pad = Dp.dp(context, 3f)
        val knobSize = h - pad * 2f
        val left = pad + t * (w - knobSize - pad * 2f)
        fill.color = Colors.alpha(Color.BLACK, 0.18f)
        canvas.drawRoundRect(left, pad + 1.5f, left + knobSize, pad + knobSize + 4f, knobSize / 2f, knobSize / 2f, fill)
        fill.color = if (theme.dark) Color.parseColor("#F7F8FA") else Color.WHITE
        canvas.drawRoundRect(left, pad, left + knobSize, pad + knobSize, knobSize / 2f, knobSize / 2f, fill)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            checked = !checked
            Haptics.tap(this, 1)
            performClick()
        }
        return true
    }
}

/** Horizontal slider with a label, a value and a soft track. */
class Slider(
    context: Context,
    var theme: Theme,
    private val label: String,
    private val min: Float,
    private val max: Float,
    private var current: Float,
    private val step: Float = 0f,
    private val formatter: (Float) -> String = { it.roundToInt().toString() },
    private val onChange: ((Float) -> Unit)? = null
) : SpringyView(context) {

    var value: Float
        get() = current
        set(v) {
            current = v.coerceIn(min, max)
            invalidate()
        }

    private val knob = spring(600f, 30f)
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var dragging = false

    init {
        isClickable = true
        knob.jumpTo(normalised(current))
        contentDescription = label
    }

    private fun normalised(v: Float): Float = if (max - min <= 0f) 0f else ((v - min) / (max - min)).coerceIn(0f, 1f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = when (View.MeasureSpec.getMode(widthMeasureSpec)) {
            View.MeasureSpec.EXACTLY -> View.MeasureSpec.getSize(widthMeasureSpec)
            else -> Dp.dpi(context, 240f)
        }
        setMeasuredDimension(width, Dp.dpi(context, 74f))
    }

    private fun trackRect(): RectF {
        val margin = Dp.dp(context, 2f)
        val y = Dp.dp(context, 55f)
        return RectF(margin, y - Dp.dp(context, 3f), width - margin, y + Dp.dp(context, 3f))
    }

    override fun drawContent(canvas: Canvas) {
        val w = width.toFloat()
        textPaint.color = theme.text
        textPaint.typeface = Type.face(550)
        textPaint.textSize = Dp.sp(context, 15f)
        Tx.draw(canvas, label, Dp.dp(context, 2f), Dp.sp(context, 20f), textPaint)

        valuePaint.color = Colors.alpha(theme.textSecondary, 0.95f)
        valuePaint.typeface = Type.face(500)
        valuePaint.textSize = Dp.sp(context, 14f)
        valuePaint.fontFeatureSettings = "tnum"
        Tx.draw(canvas, formatter(current), w - Dp.dp(context, 2f), Dp.sp(context, 20f), valuePaint, align = Paint.Align.RIGHT)

        val track = trackRect()
        val radius = track.height() / 2f
        fill.color = Colors.alpha(theme.text, 0.14f)
        canvas.drawRoundRect(track, radius, radius, fill)

        val t = if (dragging) normalised(current) else knob.value
        val filled = RectF(track.left, track.top, track.left + track.width() * t, track.bottom)
        fill.color = theme.accent
        canvas.drawRoundRect(filled, radius, radius, fill)

        val knobX = track.left + track.width() * t
        val knobRadius = Dp.dp(context, 11f)
        fill.color = Colors.alpha(Color.BLACK, 0.22f)
        canvas.drawCircle(knobX, track.centerY() + Dp.dp(context, 2f), knobRadius, fill)
        fill.color = Color.WHITE
        canvas.drawCircle(knobX, track.centerY(), knobRadius, fill)
        fill.color = Colors.alpha(theme.accent, 0.9f)
        canvas.drawCircle(knobX, track.centerY(), knobRadius * 0.42f, fill)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val track = trackRect()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                dragging = true
                val t = ((event.x - track.left) / track.width()).coerceIn(0f, 1f)
                var newValue = min + t * (max - min)
                if (step > 0f) newValue = (newValue / step).roundToInt() * step
                newValue = newValue.coerceIn(min, max)
                if (abs(newValue - current) > 0.0001f) {
                    current = newValue
                    onChange?.invoke(newValue)
                }
                if (event.actionMasked == MotionEvent.ACTION_DOWN) Haptics.tap(this, 0)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                knob.jumpTo(normalised(current))
                performClick()
                return true
            }
        }
        return true
    }
}

/** Two or three options in one pill; the selection slides with a spring. */
class Segmented(
    context: Context,
    var theme: Theme,
    private val items: List<String>,
    selectedIndex: Int = 0,
    private val onChange: ((Int) -> Unit)? = null
) : SpringyView(context) {

    var selected: Int = selectedIndex
        set(value) {
            if (field == value) return
            field = value
            indicator.to(value.toFloat())
            wake()
            onChange?.invoke(value)
        }

    private val indicator = spring(520f, 30f, selectedIndex.toFloat())
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = when (View.MeasureSpec.getMode(widthMeasureSpec)) {
            View.MeasureSpec.EXACTLY -> View.MeasureSpec.getSize(widthMeasureSpec)
            else -> Dp.dpi(context, 280f)
        }
        setMeasuredDimension(width, Dp.dpi(context, 44f))
    }

    override fun drawContent(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        val radius = h / 2f

        fill.color = Colors.alpha(theme.text, 0.1f)
        canvas.drawRoundRect(rect, radius, radius, fill)
        stroke.color = Colors.alpha(Color.WHITE, if (theme.dark) 0.08f else 0.6f)
        stroke.strokeWidth = 1f
        canvas.drawRoundRect(rect, radius, radius, stroke)

        val segmentWidth = w / items.size
        val pad = Dp.dp(context, 3f)
        val left = pad + indicator.value * segmentWidth
        val pill = RectF(left, pad, left + segmentWidth - pad * 2f, h - pad)
        Glass.drawShadow(canvas, pill, (pill.height()) / 2f, 1.4f, theme.dark)
        fill.color = if (theme.dark) Colors.alpha(Color.WHITE, 0.16f) else Color.WHITE
        canvas.drawRoundRect(pill, pill.height() / 2f, pill.height() / 2f, fill)

        items.forEachIndexed { index, item ->
            val active = index == selected
            textPaint.color = if (active) theme.text else Colors.alpha(theme.textSecondary, 0.85f)
            textPaint.typeface = Type.face(if (active) 600 else 500)
            textPaint.textSize = Dp.sp(context, 14f)
            val cx = segmentWidth * index + segmentWidth / 2f
            Tx.drawCenter(canvas, item, cx, h / 2f, textPaint, Dp.dp(context, 0.2f))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val index = (event.x / width * items.size).toInt().coerceIn(0, items.lastIndex)
            if (index != selected) {
                selected = index
                Haptics.tap(this, 1)
                performClick()
            }
        }
        return true
    }
}

/** A wrapping row of selectable chips, single or multi choice. */
class ChipsRow(
    context: Context,
    var theme: Theme,
    private val items: List<Pair<String, String>>,
    preselected: Set<String> = emptySet(),
    private val multi: Boolean = false,
    private val onChange: ((Set<String>) -> Unit)? = null
) : FrameLayout(context) {

    private val selected = LinkedHashSet(preselected)
    private val chips = ArrayList<Chip>()
    private val scroll: HorizontalScrollView
    private val container: LinearLayout
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private var heightPx = 0

    init {
        scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            overScrollMode = OVER_SCROLL_NEVER
        }
        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scroll.addView(container, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        addView(scroll, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1))
        build()
    }

    private fun build() {
        container.removeAllViews()
        items.forEach { (id, label) ->
            val chip = Chip(id, label)
            chips.add(chip)
            container.addView(chip)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val chipHeight = Dp.dpi(context, 40f)
        textPaint.typeface = Type.face(600)
        textPaint.textSize = Dp.sp(context, 14f)
        chips.forEach { chip ->
            val textWidth = Tx.measure(textPaint, chip.label)
            chip.measure(
                View.MeasureSpec.makeMeasureSpec((textWidth + Dp.dp(context, 34f)).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(chipHeight, View.MeasureSpec.EXACTLY)
            )
        }
        heightPx = chipHeight
        (scroll.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.height = chipHeight
            scroll.layoutParams = it
        }
        val measuredWidth = if (View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.UNSPECIFIED)
            Dp.dpi(context, 320f) else View.MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(measuredWidth, chipHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        scroll.layout(0, 0, r - l, b - t)
        var x = 0
        chips.forEachIndexed { index, chip ->
            val cw = chip.measuredWidth
            val gap = Dp.dpi(context, 8f)
            chip.layout(x, 0, x + cw, heightPx)
            x += cw + gap
            if (index == chips.lastIndex) {
                container.layout(0, 0, x, heightPx)
            }
        }
    }

    fun select(id: String) {
        if (!multi) selected.clear()
        if (!selected.add(id) && multi) selected.remove(id)
        chips.forEach { it.invalidate() }
        onChange?.invoke(selected.toSet())
    }

    fun selectedIds(): Set<String> = selected.toSet()

    private inner class Chip(val id: String, val label: String) : View(context) {
        private val pill = RectF()
        private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val chipStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        init {
            isClickable = true
        }

        override fun onDraw(canvas: Canvas) {
            val active = id in selected
            pill.set(0f, Dp.dp(context, 1f), width.toFloat(), height - Dp.dp(context, 1f))
            val radius = pill.height() / 2f
            if (active) {
                chipPaint.color = theme.accent
                canvas.drawRoundRect(pill, radius, radius, chipPaint)
            } else {
                chipPaint.color = Colors.alpha(theme.text, if (theme.dark) 0.1f else 0.07f)
                canvas.drawRoundRect(pill, radius, radius, chipPaint)
                chipStroke.color = Colors.alpha(if (theme.dark) Color.WHITE else Color.BLACK, 0.1f)
                chipStroke.strokeWidth = 1f
                canvas.drawRoundRect(pill, radius, radius, chipStroke)
            }
            textPaint.color = if (active) theme.accentText else theme.text
            textPaint.typeface = Type.face(600)
            textPaint.textSize = Dp.sp(context, 14f)
            Tx.drawCenter(canvas, label, width / 2f, height / 2f, textPaint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                Haptics.tap(this, 1)
                select(id)
                performClick()
            }
            return true
        }
    }
}

/** Small preview of a theme: wallpaper, a couple of keys and the accent. */
class ThemeSwatch(
    context: Context,
    var theme: Theme,
    private val selected: Boolean,
    private val onPick: (() -> Unit)? = null,
    private val showName: Boolean = true
) : SpringyView(context) {

    private val press = spring(420f, 24f)
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    var widthDp: Float = 104f
    var heightDp: Float = 132f

    init {
        isClickable = true
        contentDescription = theme.name
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(Dp.dpi(context, widthDp), Dp.dpi(context, heightDp))
    }

    override fun drawContent(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val inset = Dp.dp(context, 3f) + press.value * Dp.dp(context, 1.2f)
        rect.set(inset, inset, w - inset, h - inset)
        val radius = Dp.dp(context, 20f)

        // Wallpaper
        fill.shader = android.graphics.LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            theme.wallTop, theme.wallBottom, android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, fill)
        fill.shader = null
        Glass.glow(canvas, rect.left + rect.width() * 0.25f, rect.top + rect.height() * 0.15f, rect.width() * 0.8f, theme.wallGlow)

        // Mini keyboard
        val pad = Dp.dp(context, 9f)
        val boardTop = rect.top + rect.height() * 0.44f
        val board = RectF(rect.left + pad, boardTop, rect.right - pad, rect.bottom - pad * 0.7f)
        Glass.panel(canvas, board, Dp.dp(context, 10f), theme, context, Colors.alpha(theme.panelTop, 0.85f), Colors.alpha(theme.panelBottom, 0.85f), false, 0.03f)

        val keyGap = Dp.dp(context, 2.5f)
        val rowHeight = (board.height() - keyGap * 4f) / 3.4f
        for (row in 0 until 3) {
            val keysInRow = if (row == 2) 7 else 8
            val rowWidth = (board.width() - pad * 1.4f)
            val keyWidth = (rowWidth - keyGap * (keysInRow - 1)) / keysInRow
            val top = board.top + pad * 0.5f + row * (rowHeight + keyGap)
            for (i in 0 until keysInRow) {
                val left = board.left + pad * 0.7f + i * (keyWidth + keyGap) + (if (row == 2) keyWidth * 0.5f else 0f)
                val key = RectF(left, top, left + keyWidth, top + rowHeight)
                fill.color = if (i == 0 && row == 2) Colors.alpha(theme.accent, 0.85f) else theme.keyTop
                canvas.drawRoundRect(key, Dp.dp(context, 3.5f), Dp.dp(context, 3.5f), fill)
            }
        }
        // Space bar
        val spaceTop = board.bottom - pad * 0.6f - rowHeight * 0.9f
        val space = RectF(board.left + pad * 0.7f + (board.width() - pad * 1.4f) * 0.42f, spaceTop, board.right - pad * 0.7f - (board.width() - pad * 1.4f) * 0.12f, spaceTop + rowHeight * 0.85f)
        if (space.top > board.top) {
            fill.color = Colors.alpha(theme.accent, 0.9f)
            canvas.drawRoundRect(space, Dp.dp(context, 3.5f), Dp.dp(context, 3.5f), fill)
        }

        // Ring
        stroke.strokeWidth = if (selected) Dp.dp(context, 2f) else Dp.dp(context, 1f)
        stroke.color = if (selected) theme.accent else Colors.alpha(if (theme.dark) Color.WHITE else Color.BLACK, 0.14f)
        val ring = RectF(rect.left + 1f, rect.top + 1f, rect.right - 1f, rect.bottom - 1f)
        canvas.drawRoundRect(ring, radius, radius, stroke)

        if (showName) {
            textPaint.color = if (selected) theme.text else Colors.alpha(theme.text, 0.85f)
            textPaint.typeface = Type.face(if (selected) 600 else 500)
            textPaint.textSize = Dp.sp(context, 12.5f)
            Tx.drawCenter(canvas, theme.name, w / 2f, h - Dp.dp(context, 34f), textPaint)
        }
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
                onPick?.invoke()
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

/** A quiet list row: title, optional subtitle and a trailing control. */
fun listRow(
    context: Context,
    theme: Theme,
    title: String,
    subtitle: String? = null,
    trailing: View? = null
): LinearLayout {
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Dp.dpi(context, 2f), Dp.dpi(context, 10f), Dp.dpi(context, 2f), Dp.dpi(context, 10f))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }
    column.addView(Ui.textView(context, title, Type.BODY - 0.5f, 550, theme.text, tracking = -0.01f))
    if (subtitle != null) {
        column.addView(Ui.textView(context, subtitle, Type.CAPTION, 450, Colors.alpha(theme.textSecondary, 0.9f), tracking = 0f, lineSpacing = 1.25f).apply {
            setPadding(0, Dp.dpi(context, 3f), 0, 0)
        })
    }
    row.addView(column)
    if (trailing != null) {
        row.addView(trailing)
    }
    return row
}
