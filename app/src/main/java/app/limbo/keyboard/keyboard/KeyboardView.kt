package app.limbo.keyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import app.limbo.keyboard.core.Clock
import app.limbo.keyboard.core.Colors
import app.limbo.keyboard.core.Dp
import app.limbo.keyboard.core.I18n
import app.limbo.keyboard.core.Maths.clamp
import app.limbo.keyboard.core.MotionStyle
import app.limbo.keyboard.core.Settings
import app.limbo.keyboard.core.Spring
import app.limbo.keyboard.core.Type
import app.limbo.keyboard.core.Tx
import app.limbo.keyboard.layout.Action
import app.limbo.keyboard.layout.Board
import app.limbo.keyboard.layout.BoardBuilder
import app.limbo.keyboard.layout.Family
import app.limbo.keyboard.layout.Glyph
import app.limbo.keyboard.layout.Key
import app.limbo.keyboard.layout.Language
import app.limbo.keyboard.layout.Layer
import app.limbo.keyboard.theme.Theme
import app.limbo.keyboard.ui.kit.Glass
import app.limbo.keyboard.ui.kit.Haptics
import app.limbo.keyboard.ui.kit.IconPainter
import app.limbo.keyboard.ui.kit.SpringyView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The keyboard itself: a canvas that lays out keys, springs them under the finger and resolves
 * touches generously, so a press that lands next to a key still counts for the closest one.
 */
class KeyboardView(context: Context) : SpringyView(context) {

    var onEvent: ((KeyboardEvent) -> Unit)? = null

    var theme: Theme = ThemeFactoryDefaults.theme
        set(value) {
            val geometryChanged = field.gap != value.gap ||
                field.rowGap != value.rowGap ||
                field.keyHeight != value.keyHeight ||
                field.keyCorner != value.keyCorner ||
                field.fontScale != value.fontScale
            field = value
            invalidateBoard()
            if (geometryChanged) rebuild(keepHeight = false)
        }

    var settings: Settings? = null

    var interactive: Boolean = true

    /** Height of the transparent strip above the keys that the magnified preview needs. */
    var topBandPx: Int = Dp.dpi(context, 46f)
        set(value) {
            if (field == value) return
            field = value
            layOut()
            requestLayout()
            invalidate()
        }

    val bubbleSpace: Int get() = topBandPx

    var language: Language = Language.EN
        private set
    var layer: Layer = Layer.LETTERS
        private set
    var shifted: Boolean = false
        private set
    var capsLock: Boolean = false
        private set
    var family: Family = Family.QWERTY
        private set

    private var board: Board? = null
    private var boxes: List<KeyBox> = emptyList()
    private var topOverlap = 0
    private var boardHeight = 0
    private var boardWidth = 0

    private var staticLayer: Bitmap? = null
    private var staticDirty = true

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val glyphStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val path = Path()

    private val handler = Handler(Looper.getMainLooper())
    private var activeIndex = -1
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var movedFar = false
    private var spaceSwipeAnchor = 0f
    private var swipeCursorActive = false
    private var popup: Popup? = null
    private var repeating = false
    private var repeatCount = 0
    private var lastSpaceUp = 0L
    private var lastShiftTap = 0L

    private val longPressRunnable = Runnable { beginLongPress() }
    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (!repeating || activeIndex < 0) return
            val box = boxes.getOrNull(activeIndex) ?: return
            repeatCount++
            emit(KeyboardEvent.Delete(wholeWord = false))
            hapticTap(box.key)
            val interval = when {
                repeatCount > 22 -> 26L
                repeatCount > 12 -> 38L
                else -> 56L
            }
            handler.postDelayed(this, interval)
        }
    }

    private val tick: (Float) -> Unit = { dt -> step(dt) }
    private var ticking = false

    private class KeyBox(val key: Key, val rect: RectF, val index: Int) {
        var press: Spring = Spring.snappy()
        var ripple: Float = 0f
    }

    private class Popup(val origin: KeyBox, val options: List<String>, var selected: Int, val rect: RectF)

    init {
        isClickable = true
        // the host frame disables clipping so the magnified preview can float above the keys
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    // ------------------------------------------------------------------ public API

    fun setLanguage(language: Language, family: Family = language.defaultFamily, rebuild: Boolean = true) {
        this.language = language
        this.family = family
        if (rebuild) {
            resetShift()
            rebuild(keepHeight = true)
        }
    }

    fun setLayer(newLayer: Layer) {
        layer = newLayer
        shifted = false
        capsLock = false
        invalidateBoard()
        rebuild(keepHeight = false)
    }

    fun setShifted(shifted: Boolean, caps: Boolean = false) {
        if (this.shifted == shifted && this.capsLock == caps) return
        this.shifted = shifted
        this.capsLock = caps
        invalidateBoard()
        rebuild(keepHeight = true)
    }

    fun toggleShift() {
        if (capsLock) {
            shifted = false
            capsLock = false
        } else if (shifted) {
            // Second tap inside the window turns Shift into Caps Lock.
            capsLock = true
            shifted = true
        } else {
            shifted = true
        }
        invalidateBoard()
        rebuild(keepHeight = true)
        emit(KeyboardEvent.StateChanged(shifted, capsLock, layer.name))
    }

    fun resetShift() {
        shifted = false
        capsLock = false
    }

    fun boardHeightPx(): Int = boardHeight

    /** Total height the input method should reserve, without the preview strip. */
    fun imeHeightPx(): Int = boardHeight

    fun invalidateTheme(newTheme: Theme) {
        theme = newTheme
    }

    /** Rebuilds rows after a preference change without moving the cursor or the language. */
    fun invalidateBoardSafely() {
        rebuild(keepHeight = false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        rebuild(keepHeight = false)
    }

    private fun invalidateBoard() {
        staticDirty = true
        invalidate()
    }

    // ------------------------------------------------------------------ building

    private fun rebuild(keepHeight: Boolean) {
        val prefs = settings ?: return
        val next = BoardBuilder.build(
            settings = prefs,
            language = language,
            family = family,
            layer = layer,
            shifted = shifted,
            capsLock = capsLock,
            showLanguageKey = true
        )
        board = next
        val previousHeight = boardHeight
        layOut()
        if (!keepHeight || previousHeight != boardHeight) {
            onEvent?.invoke(KeyboardEvent.HeightChanged(boardHeight))
        }
        staticDirty = true
        requestLayout()
        invalidate()
    }

    private fun layOut() {
        val current = board ?: return
        val prefs = settings
        clearSprings()
        val w = if (width > 0) width else resources.displayMetrics.widthPixels

        val padding = Dp.dp(context, 5f)
        val gap = Dp.dp(context, theme.gap.coerceIn(3f, 14f))
        val rowGap = Dp.dp(context, theme.rowGap.coerceIn(3f, 14f))
        val baseKeyHeight = Dp.dp(
            context,
            theme.keyHeight.coerceIn(38f, 72f) * when (prefs?.heightScale ?: 1) {
                0 -> 0.89f
                2 -> 1.12f
                else -> 1f
            }
        )

        topOverlap = topBandPx

        var y = topOverlap.toFloat()
        val newBoxes = ArrayList<KeyBox>()
        current.rows.forEach { row ->
            val rowHeight = baseKeyHeight * row.heightFactor
            val inset = padding + row.inset * ((w - padding * 2f) / 10f)
            val available = w - inset * 2f
            val gaps = gap * (row.keys.size - 1)
            val unit = (available - gaps) / row.weightSum
            var x = inset
            row.keys.forEachIndexed { index, key ->
                val width = unit * key.weight
                val box = RectF(x, y, x + width, y + rowHeight)
                val created = KeyBox(key, box, newBoxes.size)
                created.press = spring(stiffness = prefs?.let { motionStyle(it).stiffness } ?: 380f, damping = prefs?.let { motionStyle(it).damping } ?: 28f)
                newBoxes.add(created)
                x += width + gap
                if (index == row.keys.lastIndex) {
                    // keep x exact for the last key so the row stays symmetrical
                }
            }
            y += rowHeight + rowGap
        }

        boxes = newBoxes
        boardHeight = (y - rowGap + Dp.dp(context, 6f)).roundToInt()
        boardWidth = w
        staticDirty = true
    }

    private fun motionStyle(prefs: Settings): MotionStyle = prefs.motion

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = View.MeasureSpec.getSize(widthMeasureSpec)
        if (w != boardWidth || boxes.isEmpty()) {
            layOut()
        }
        val height = boardHeight
        setMeasuredDimension(w, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != boardWidth) {
            layOut()
            requestLayout()
        }
        staticDirty = true
    }

    // ------------------------------------------------------------------ rendering

    override fun drawContent(canvas: Canvas) {
        if (boxes.isEmpty()) return

        val anyPressed = boxes.any { it.press.value > 0.001f }
        if (anyPressed) {
            // While a finger is down the board is painted live so a smooshed key never leaves
            // its resting copy peeking out from underneath.
            for (box in boxes) {
                drawKey(canvas, box, box.press.value, active = box.index == activeIndex)
            }
        } else {
            val cached = staticLayer()
            if (cached != null) {
                canvas.drawBitmap(cached, 0f, 0f, null)
            } else {
                for (box in boxes) drawKey(canvas, box, 0f, active = false)
            }
        }

        popup?.let { drawPopup(canvas, it) }
        drawPreviewBubble(canvas)
    }

    private fun staticLayer(): Bitmap? {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return null
        val cached = staticLayer
        if (!staticDirty && cached != null && cached.width == w && cached.height == h) return cached
        if (cached != null) cached.recycle()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        for (box in boxes) drawKey(canvas, box, 0f, active = false)
        staticLayer = bitmap
        staticDirty = false
        return bitmap
    }

    private fun drawKey(canvas: Canvas, box: KeyBox, press: Float, active: Boolean) {
        val key = box.key
        val base = box.rect
        val scale = 1f - 0.028f * press
        val cx = base.centerX()
        val cy = base.centerY()

        val area = RectF(
            cx - base.width() / 2f * scale,
            cy - base.height() / 2f * scale - press * Dp.dp(context, 0.8f),
            cx + base.width() / 2f * scale,
            cy + base.height() / 2f * scale - press * Dp.dp(context, 0.8f)
        )
        val radius = Dp.dp(context, theme.keyCorner)

        val modifier = key.action != Action.INPUT && key.action != Action.SPACE
        val accent = key.action == Action.ENTER || (key.action == Action.SHIFT && (shifted || capsLock))

        Glass.key(canvas, area, radius, theme, context, press, modifier, accent)

        if (active && press > 0.05f) {
            strokePaint.color = Colors.alpha(theme.accent, 0.55f * press)
            strokePaint.strokeWidth = Dp.dp(context, 1.4f)
            canvas.drawRoundRect(area, radius, radius, strokePaint)
        }

        val textColor = when {
            accent -> theme.accentText
            modifier -> Colors.alpha(theme.text, 0.9f)
            else -> theme.text
        }
        drawKeyContent(canvas, box, area, textColor, press)
    }

    private fun drawKeyContent(canvas: Canvas, box: KeyBox, area: RectF, color: Int, press: Float) {
        val key = box.key

        if (key.glyph != Glyph.NONE && key.action != Action.SPACE) {
            val size = min(area.height() * 0.44f, Dp.dp(context, 26f))
            IconPainter.draw(canvas, key.glyph, area.centerX(), area.centerY(), size, color, glyphStroke, paint.apply { this.color = color })
            if (key.label.isNotEmpty()) {
                // Word labels next to a glyph (for example the current layout name).
                labelPaint.color = color
                labelPaint.typeface = Type.face(500)
                labelPaint.textSize = Dp.sp(context, 11.5f)
                Tx.drawCenter(canvas, key.label, area.centerX(), area.centerY() + size * 0.85f, labelPaint)
            }
            return
        }

        if (key.action == Action.SPACE) {
            labelPaint.color = Colors.alpha(color, 0.72f)
            labelPaint.typeface = Type.face(500)
            labelPaint.textSize = Dp.sp(context, 12.5f * theme.fontScale)
            Tx.drawCenter(canvas, I18n.t("пробел", "space"), area.centerX(), area.centerY(), labelPaint, Dp.dp(context, 0.7f))
            return
        }

        val text = key.label
        if (text.isEmpty()) return

        val isLetter = key.action == Action.INPUT && text.length == 1 && text[0].isLetter()
        val size = when {
            isLetter -> 21.5f
            key.action == Action.INPUT -> 18.5f
            text.length > 2 -> 12.5f
            else -> 15.5f
        } * theme.fontScale

        labelPaint.color = color
        labelPaint.typeface = Type.face(if (isLetter) 500 else 550)
        labelPaint.textSize = Dp.sp(context, size)
        val tracking = Dp.dp(context, theme.tracking * size)
        Tx.drawCenter(canvas, text, area.centerX(), area.centerY(), labelPaint, tracking)

        // Hint of the character hidden behind a long press, the way a good keyboard hints at it.
        if (key.action == Action.INPUT && key.alt.isNotEmpty() && (settings?.popups ?: true) && layer != Layer.LETTERS) {
            hintPaint.color = Colors.alpha(color, 0.4f)
            hintPaint.typeface = Type.face(500)
            hintPaint.textSize = Dp.sp(context, 10.5f)
            Tx.draw(canvas, key.alt.first(), area.right - Dp.dp(context, 5f), area.top + Dp.dp(context, 13f), hintPaint, 0f, Paint.Align.RIGHT)
        }

        if (key.action == Action.INPUT && key.alt.isNotEmpty() && layer == Layer.LETTERS && (settings?.popups ?: true)) {
            paint.color = Colors.alpha(theme.accent, 0.55f)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(area.right - Dp.dp(context, 5.5f), area.bottom - Dp.dp(context, 5.5f), Dp.dp(context, 1.3f), paint)
        }
    }

    /** The classic magnifier above the finger, drawn only while a letter is held. */
    private fun drawPreviewBubble(canvas: Canvas) {
        val box = boxes.getOrNull(activeIndex) ?: return
        val key = box.key
        if (popup != null) return
        if (key.action != Action.INPUT) return
        val press = box.press.value
        if (press < 0.12f) return
        val text = key.label
        if (text.isEmpty()) return

        val bubbleWidth = max(Dp.dp(context, 46f), box.rect.width() * 1.12f)
        val bubbleHeight = Dp.dp(context, 54f)
        val cx = box.rect.centerX().coerceIn(bubbleWidth / 2f + Dp.dp(context, 4f), width - bubbleWidth / 2f - Dp.dp(context, 4f))
        val top = max(Dp.dp(context, 2f), box.rect.top - bubbleHeight - Dp.dp(context, 7f))
        val bubble = RectF(cx - bubbleWidth / 2f, top, cx + bubbleWidth / 2f, top + bubbleHeight)
        val radius = Dp.dp(context, 15f)
        val alpha = ((press - 0.12f) / 0.5f).coerceIn(0f, 1f)

        paint.color = Colors.alpha(Color.BLACK, 0.22f * alpha)
        canvas.drawRoundRect(RectF(bubble.left, bubble.top + Dp.dp(context, 3f), bubble.right, bubble.bottom + Dp.dp(context, 3f)), radius, radius, paint)

        val bubbleTop = Color.argb(255, Color.red(theme.keyTop), Color.green(theme.keyTop), Color.blue(theme.keyTop))
        val bubbleBottom = Color.argb(255, Color.red(theme.keyBottom), Color.green(theme.keyBottom), Color.blue(theme.keyBottom))
        paint.color = Colors.alpha(Colors.mix(bubbleTop, bubbleBottom, 0.35f), alpha)
        canvas.drawRoundRect(bubble, radius, radius, paint)
        strokePaint.color = Colors.alpha(if (theme.dark) Color.WHITE else Color.BLACK, 0.12f * alpha)
        strokePaint.strokeWidth = Dp.dp(context, 1f)
        canvas.drawRoundRect(bubble, radius, radius, strokePaint)

        // Tail
        val tail = Path()
        val tailX = box.rect.centerX().coerceIn(bubble.left + radius, bubble.right - radius)
        tail.reset()
        tail.moveTo(tailX - Dp.dp(context, 6f), bubble.bottom - Dp.dp(context, 1f))
        tail.lineTo(tailX, bubble.bottom + Dp.dp(context, 7f))
        tail.lineTo(tailX + Dp.dp(context, 6f), bubble.bottom - Dp.dp(context, 1f))
        tail.close()
        paint.color = Colors.alpha(Colors.mix(bubbleTop, bubbleBottom, 0.9f), alpha)
        canvas.drawPath(tail, paint)

        labelPaint.color = Colors.alpha(theme.text, alpha)
        labelPaint.typeface = Type.face(500)
        labelPaint.textSize = Dp.sp(context, 27f * theme.fontScale)
        Tx.drawCenter(canvas, text, bubble.centerX(), bubble.centerY(), labelPaint)
    }

    private fun drawPopup(canvas: Canvas, popup: Popup) {
        val area = popup.rect
        val radius = area.height() / 2f
        paint.color = Colors.alpha(Color.BLACK, 0.26f)
        canvas.drawRoundRect(RectF(area.left, area.top + Dp.dp(context, 3f), area.right, area.bottom + Dp.dp(context, 3f)), radius, radius, paint)
        paint.color = Colors.alpha(theme.panelTop, 0.98f)
        canvas.drawRoundRect(area, radius, radius, paint)
        strokePaint.color = Colors.alpha(if (theme.dark) Color.WHITE else Color.BLACK, 0.14f)
        strokePaint.strokeWidth = Dp.dp(context, 1f)
        canvas.drawRoundRect(area, radius, radius, strokePaint)

        val count = popup.options.size
        val cell = area.width() / count
        popup.options.forEachIndexed { index, option ->
            val left = area.left + index * cell
            val cellRect = RectF(left + Dp.dp(context, 2f), area.top + Dp.dp(context, 2f), left + cell - Dp.dp(context, 2f), area.bottom - Dp.dp(context, 2f))
            if (index == popup.selected) {
                paint.color = Colors.alpha(theme.accent, 0.9f)
                canvas.drawRoundRect(cellRect, cellRect.height() / 2f, cellRect.height() / 2f, paint)
            }
            labelPaint.color = if (index == popup.selected) theme.accentText else theme.text
            labelPaint.typeface = Type.face(500)
            labelPaint.textSize = Dp.sp(context, 21f * theme.fontScale)
            Tx.drawCenter(canvas, option, cellRect.centerX(), cellRect.centerY(), labelPaint)
        }
    }

    // ------------------------------------------------------------------ touch

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return false
        val y = event.y
        if (event.actionMasked == MotionEvent.ACTION_DOWN && y < topOverlap - Dp.dp(context, 2f) && activeIndex < 0) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                movedFar = false
                swipeCursorActive = false
                spaceSwipeAnchor = event.x
                repeatCount = 0
                val index = hitTest(event.x, event.y)
                if (index < 0) return true
                activate(index)
                handler.postDelayed(longPressRunnable, (settings?.longPressDelay ?: 320).toLong())
                hapticTap(boxes[index].key)
                if (boxes[index].key.action == Action.BACKSPACE) {
                    emit(KeyboardEvent.Delete())
                    startRepeat()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                lastX = event.x
                lastY = event.y
                if (!movedFar && (abs(event.x - downX) > Dp.dp(context, 8f) || abs(event.y - downY) > Dp.dp(context, 8f))) {
                    movedFar = true
                    handler.removeCallbacks(longPressRunnable)
                }
                val currentPopup = popup
                if (currentPopup != null) {
                    updatePopupSelection(currentPopup, event.x)
                    return true
                }
                val activeBox = boxes.getOrNull(activeIndex)
                if (activeBox != null && activeBox.key.action == Action.SPACE && (settings?.swipeToCursor ?: true)) {
                    val dx = event.x - spaceSwipeAnchor
                    val step = Dp.dp(context, 11f)
                    if (abs(dx) > step) {
                        val steps = (dx / step).roundToInt()
                        if (steps != 0) {
                            emit(KeyboardEvent.Cursor(steps))
                            spaceSwipeAnchor = event.x
                            swipeCursorActive = true
                            hapticTap(activeBox.key)
                        }
                    }
                    return true
                }
                if (movedFar && !repeating) {
                    val index = hitTest(event.x, event.y)
                    if (index >= 0 && index != activeIndex) {
                        activate(index)
                        if (boxes[index].key.action == Action.BACKSPACE) {
                            emit(KeyboardEvent.Delete())
                            startRepeat()
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                handler.removeCallbacks(longPressRunnable)
                stopRepeat()
                val currentPopup = popup
                if (currentPopup != null) {
                    commitPopup(currentPopup)
                    return true
                }
                val box = boxes.getOrNull(activeIndex)
                if (box != null) {
                    releaseBox(box)
                    if (!swipeCursorActive) {
                        commit(box.key, longPress = false)
                    }
                }
                resetTouch()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                handler.removeCallbacks(longPressRunnable)
                stopRepeat()
                popup = null
                boxes.getOrNull(activeIndex)?.let { releaseBox(it) }
                resetTouch()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun resetTouch() {
        activeIndex = -1
        popup = null
        repeating = false
        invalidate()
    }

    private fun startRepeat() {
        repeating = true
        repeatCount = 0
        handler.postDelayed(repeatRunnable, 340)
    }

    private fun stopRepeat() {
        repeating = false
        handler.removeCallbacks(repeatRunnable)
    }

    private fun activate(index: Int) {
        if (index == activeIndex) return
        boxes.getOrNull(activeIndex)?.let { releaseBox(it) }
        activeIndex = index
        val box = boxes[index]
        box.press.setTarget(1f)
        box.press.nudge(6f)
        startTicking()
        invalidate()
    }

    private fun releaseBox(box: KeyBox) {
        box.press.setTarget(0f)
        startTicking()
    }

    private fun startTicking() {
        if (!ticking) {
            ticking = true
            Clock.subscribe(tick)
        }
    }

    private fun step(dt: Float) {
        var moving = false
        for (box in boxes) {
            if (box.press.isActive) {
                box.press.advance(dt)
                moving = true
            }
        }
        if (!moving) {
            ticking = false
            Clock.unsubscribe(tick)
        }
        invalidate()
    }

    /**
     * Nearest-key resolution with a generous, direction-aware tolerance: sideways misses are
     * forgiven more than vertical ones, which matches how thumbs actually land.
     */
    private fun hitTest(x: Float, y: Float): Int {
        var exact = -1
        for (box in boxes) {
            if (x >= box.rect.left && x <= box.rect.right && y >= box.rect.top && y <= box.rect.bottom) {
                exact = box.index
                break
            }
        }
        if (exact >= 0) return exact

        val slop = settings?.touchSlop ?: 0.62f
        val horizontal = Dp.dp(context, 10f + 16f * slop)
        val vertical = Dp.dp(context, 5f + 9f * slop)
        var best = -1
        var bestScore = Float.MAX_VALUE
        for (box in boxes) {
            val dx = when {
                x < box.rect.left -> box.rect.left - x
                x > box.rect.right -> x - box.rect.right
                else -> 0f
            }
            val dy = when {
                y < box.rect.top -> box.rect.top - y
                y > box.rect.bottom -> y - box.rect.bottom
                else -> 0f
            }
            if (dx > horizontal || dy > vertical) continue
            val score = dx / horizontal + dy / vertical * 1.35f + box.index * 0.0001f
            if (score < bestScore) {
                bestScore = score
                best = box.index
            }
        }
        return best
    }

    // ------------------------------------------------------------------ actions

    private fun commit(key: Key, longPress: Boolean) {
        when (key.action) {
            Action.INPUT -> {
                val text = when {
                    longPress -> key.alt.firstOrNull() ?: key.text
                    shifted && key.shiftedText != null -> key.shiftedText
                    else -> key.text
                }
                if (text.isEmpty()) return
                if (shifted && !capsLock) {
                    shifted = false
                    invalidateBoard()
                    rebuild(keepHeight = true)
                }
                emit(KeyboardEvent.Commit(text, key))
                emit(KeyboardEvent.Feedback(key, longPress))
            }

            Action.SPACE -> {
                val now = SystemClock.uptimeMillis()
                if ((settings?.doubleSpacePeriod ?: true) && now - lastSpaceUp < 380) {
                    emit(KeyboardEvent.Delete())
                    emit(KeyboardEvent.Commit(". "))
                } else {
                    emit(KeyboardEvent.Commit(" "))
                }
                lastSpaceUp = now
                emit(KeyboardEvent.Feedback(key, false))
            }

            Action.BACKSPACE -> {
                // Fires on touch down, including the first repeat, so nothing is lost here.
            }

            Action.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                val doubleTap = now - lastShiftTap < 320
                lastShiftTap = now
                if (doubleTap && !capsLock) {
                    capsLock = true
                    shifted = true
                    invalidateBoard()
                    rebuild(keepHeight = true)
                    emit(KeyboardEvent.StateChanged(shifted, capsLock, layer.name))
                } else {
                    toggleShift()
                }
            }

            Action.ENTER -> emit(KeyboardEvent.ActionRequested(Action.ENTER))
            Action.LAYER_LETTERS -> setLayer(Layer.LETTERS)
            Action.LAYER_NUMBERS -> setLayer(Layer.NUMBERS)
            Action.LAYER_SYMBOLS -> setLayer(Layer.SYMBOLS)
            Action.LANGUAGE -> emit(KeyboardEvent.SwitchLanguage(true))
            Action.CLIPBOARD -> emit(KeyboardEvent.ClipboardPanel)
            Action.EMOJI -> emit(KeyboardEvent.EmojiPanel)
            Action.SETTINGS -> emit(KeyboardEvent.SettingsPanel)
            Action.HIDE -> emit(KeyboardEvent.Dismiss)
            Action.CURSOR_LEFT -> emit(KeyboardEvent.Cursor(-1))
            Action.CURSOR_RIGHT -> emit(KeyboardEvent.Cursor(1))
            Action.TAB -> emit(KeyboardEvent.Commit("\t"))
            Action.SELECT_ALL -> emit(KeyboardEvent.ActionRequested(Action.SELECT_ALL))
        }
    }

    private fun emit(event: KeyboardEvent) {
        onEvent?.invoke(event)
    }

    private fun hapticTap(key: Key) {
        val prefs = settings ?: return
        if (!prefs.haptics) return
        if (key.action == Action.BACKSPACE && repeating) {
            Haptics.tap(this, 0)
        } else {
            Haptics.tap(this, prefs.hapticStrength)
        }
    }

    // ------------------------------------------------------------------ long press

    private fun beginLongPress() {
        val box = boxes.getOrNull(activeIndex) ?: return
        val key = box.key
        when {
            key.action == Action.INPUT && key.alt.isNotEmpty() -> {
                val options = ArrayList<String>()
                options.add(if (shifted && key.shiftedText != null) key.shiftedText else key.text)
                key.alt.forEach { options.add(it) }
                showPopup(box, options)
            }
            key.action == Action.INPUT && key.text.isNotEmpty() -> {
                showPopup(box, listOf(if (shifted && key.shiftedText != null) key.shiftedText else key.text))
            }
            key.action == Action.LANGUAGE -> {
                emit(KeyboardEvent.LanguagePicker)
                releaseBox(box)
                activeIndex = -1
                invalidate()
            }
            key.action == Action.SPACE -> {
                emit(KeyboardEvent.SettingsPanel)
                releaseBox(box)
                activeIndex = -1
                invalidate()
            }
            key.action == Action.CLIPBOARD -> {
                emit(KeyboardEvent.ClipboardPanel)
                releaseBox(box)
                activeIndex = -1
                invalidate()
            }
            key.action == Action.SHIFT -> {
                capsLock = !capsLock
                shifted = capsLock
                invalidateBoard()
                rebuild(keepHeight = true)
                emit(KeyboardEvent.StateChanged(shifted, capsLock, layer.name))
                Haptics.tap(this, 2)
            }
            key.action == Action.LAYER_NUMBERS || key.action == Action.LAYER_LETTERS -> {
                setLayer(if (layer == Layer.SYMBOLS) Layer.NUMBERS else Layer.SYMBOLS)
                Haptics.tap(this, 1)
            }
        }
    }

    private fun showPopup(box: KeyBox, options: List<String>) {
        if (options.isEmpty()) return
        val cell = Dp.dp(context, 46f)
        val padding = Dp.dp(context, 6f)
        val width = cell * options.size + padding * 2f
        val height = Dp.dp(context, 48f)
        val cx = box.rect.centerX().coerceIn(width / 2f + Dp.dp(context, 2f), this.width - width / 2f - Dp.dp(context, 2f))
        val top = (box.rect.top - height - Dp.dp(context, 8f)).coerceAtLeast(Dp.dp(context, 2f))
        val area = RectF(cx - width / 2f, top, cx + width / 2f, top + height)
        popup = Popup(box, options, 0, area)
        Haptics.tap(this, 2)
        invalidate()
    }

    private fun updatePopupSelection(current: Popup, x: Float) {
        val cell = current.rect.width() / current.options.size
        val index = ((x - current.rect.left) / cell).toInt().coerceIn(0, current.options.lastIndex)
        if (index != current.selected) {
            current.selected = index
            Haptics.tap(this, 0)
            invalidate()
        }
    }

    private fun commitPopup(current: Popup) {
        val option = current.options.getOrNull(current.selected) ?: return
        emit(KeyboardEvent.Commit(option, current.origin.key))
        emit(KeyboardEvent.Feedback(current.origin.key, true))
        releaseBox(current.origin)
        popup = null
        activeIndex = -1
        invalidate()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPressRunnable)
        stopRepeat()
        if (ticking) {
            Clock.unsubscribe(tick)
            ticking = false
        }
        boxes.forEach { it.press.jumpTo(0f) }
        super.onDetachedFromWindow()
    }
}

/** Fallback theme used before preferences are wired in. */
private object ThemeFactoryDefaults {
    val theme: Theme = app.limbo.keyboard.theme.Themes.presets.first()
}
