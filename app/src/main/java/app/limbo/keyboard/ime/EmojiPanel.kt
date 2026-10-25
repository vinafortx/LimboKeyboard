package app.limbo.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.limbo.keyboard.core.Colors
import app.limbo.keyboard.core.Dp
import app.limbo.keyboard.core.I18n
import app.limbo.keyboard.core.Type
import app.limbo.keyboard.core.Tx
import app.limbo.keyboard.theme.Theme
import app.limbo.keyboard.ui.kit.Glass
import app.limbo.keyboard.ui.kit.Haptics
import app.limbo.keyboard.ui.kit.SpringyView

/** Emoji, grouped so the right face is never more than one swipe away. */
object Emoji {

    val categories: List<Pair<String, List<String>>> = listOf(
        I18n.t("Смайлы", "Smileys") to listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
            "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
            "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥸",
            "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️",
            "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡",
            "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓",
            "🤗", "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄",
            "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵",
            "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕"
        ),
        I18n.t("Жесты", "Gestures") to listOf(
            "👍", "👎", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙",
            "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐", "🖖", "👋",
            "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🫶", "👏", "🙌",
            "👐", "🤲", "🫰", "🫵", "🫱", "🫲", "🫳", "🫴"
        ),
        I18n.t("Сердца", "Hearts") to listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
            "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "♥️",
            "💌", "🫶", "💋", "🫂", "💐", "🌹", "🌷", "🌸"
        ),
        I18n.t("Природа", "Nature") to listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
            "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🐤", "🦆",
            "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🪲", "🦋", "🐌",
            "🐢", "🐍", "🐙", "🦑", "🐬", "🐳", "🦭", "🐟", "🐠", "🦈",
            "🌵", "🎄", "🌲", "🌳", "🌴", "🌱", "🌿", "☘️", "🍀", "🍁",
            "🍂", "🍃", "🌾", "🌺", "🌻", "🌼", "🌷", "🌙", "⭐", "🌟",
            "✨", "⚡", "🔥", "🌈", "☀️", "⛅", "🌧", "❄️"
        ),
        I18n.t("Еда", "Food") to listOf(
            "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐",
            "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑",
            "🥦", "🥬", "🥒", "🌶", "🌽", "🥕", "🧄", "🧅", "🥔", "🍠",
            "🥐", "🥯", "🍞", "🥖", "🥨", "🧀", "🥚", "🍳", "🧈", "🥞",
            "🍔", "🍟", "🍕", "🌭", "🥪", "🌮", "🌯", "🥗", "🍝", "🍜",
            "🍲", "🍣", "🍱", "🥟", "🍤", "🍙", "🍚", "🍘", "🍥", "🥠",
            "🍦", "🍧", "🍨", "🍩", "🍪", "🎂", "🍰", "🧁", "🥧", "🍫",
            "🍬", "🍭", "🍮", "☕", "🍵", "🧋", "🥤", "🍺", "🍷", "🥂"
        ),
        I18n.t("Активность", "Activity") to listOf(
            "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱",
            "🪀", "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🥅", "⛳", "🪁",
            "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸",
            "🥌", "🎿", "⛷", "🏂", "🏋️", "🤼", "🤸", "⛹️", "🤺", "🤾",
            "🏌️", "🏇", "🧘", "🏄", "🏊", "🤽", "🚴", "🚵", "🎮", "🕹",
            "🎲", "🧩", "🎯", "🎳", "🎪", "🎭", "🎨", "🎬", "🎤", "🎧",
            "🎼", "🎹", "🥁", "🎷", "🎺", "🎸", "🪕", "🎻"
        ),
        I18n.t("Вещи", "Objects") to listOf(
            "⌚", "📱", "💻", "⌨️", "🖥", "🖨", "🖱", "💾", "💿", "📀",
            "🎥", "📷", "📸", "📹", "📼", "🔍", "💡", "🔦", "🕯", "🪔",
            "📔", "📕", "📗", "📘", "📙", "📚", "📖", "🔖", "🧷", "📎",
            "✂️", "📐", "📏", "📌", "📍", "🗂", "📁", "📂", "🗒", "🗓",
            "📇", "📈", "📉", "📊", "📋", "🗃", "🗄", "📦", "📫", "📮",
            "✉️", "📧", "💌", "📨", "📩", "📤", "📥", "🏷", "🔑", "🗝",
            "🔒", "🔓", "🔨", "🪛", "🔧", "🪚", "🧲", "💊", "💉", "🩸",
            "🎁", "🎈", "🎀", "🪄", "💰", "💳", "💎", "⚖️", "🧸", "🪆"
        ),
        I18n.t("Символы", "Symbols") to listOf(
            "✅", "❌", "⭕", "🚫", "💯", "❗", "❓", "‼️", "⁉️", "⚠️",
            "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "⚫", "⚪", "🟤", "🔶",
            "🔷", "🔸", "🔹", "🔺", "🔻", "➡️", "⬅️", "⬆️", "⬇️", "↩️",
            "🔄", "🔁", "🔀", "▶️", "⏸", "⏹", "⏺", "⏭", "⏮", "⏱",
            "⏰", "🕐", "📅", "📆", "♻️", "🆗", "🆕", "🆒", "🆓", "🔝",
            "©️", "®️", "™️", "💠", "🔱", "📶", "🔋", "🪫", "🔌", "💤"
        )
    )

    val recent = ArrayDeque<String>()
}

/** Emoji keyboard: category rail on top, a wrapped grid below. */
class EmojiPanel(context: Context) : FrameLayout(context) {

    var theme: Theme = app.limbo.keyboard.theme.Themes.presets.first()
        set(value) {
            field = value
            applyTheme()
        }

    var onEmoji: ((String) -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onBackspace: (() -> Unit)? = null

    private val title: TextView
    private val rail: LinearLayout
    private val scroll: ScrollView
    private val grid: LinearLayout
    private var selected = 0

    init {
        title = app.limbo.keyboard.ui.kit.Ui.textView(
            context, I18n.t("Эмодзи", "Emoji"), Type.HEADLINE - 1f, 700, theme.text, tracking = -0.02f
        )
        addView(title, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            leftMargin = Dp.dpi(context, 18f)
            topMargin = Dp.dpi(context, 14f)
        })

        val close = CloseButtonSmall(context, theme)
        close.setOnClickListener { onClose?.invoke() }
        addView(close, LayoutParams(Dp.dpi(context, 34f), Dp.dpi(context, 34f)).apply {
            gravity = Gravity.END
            rightMargin = Dp.dpi(context, 16f)
            topMargin = Dp.dpi(context, 10f)
        })

        val backspace = BackspaceButton(context, theme)
        backspace.setOnClickListener {
            Haptics.tap(this, 1)
            onBackspace?.invoke()
        }
        addView(backspace, LayoutParams(Dp.dpi(context, 44f), Dp.dpi(context, 34f)).apply {
            gravity = Gravity.END
            rightMargin = Dp.dpi(context, 58f)
            topMargin = Dp.dpi(context, 10f)
        })

        rail = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val railScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(rail, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
        addView(railScroll, LayoutParams(LayoutParams.MATCH_PARENT, Dp.dpi(context, 40f)).apply {
            topMargin = Dp.dpi(context, 48f)
            leftMargin = Dp.dpi(context, 12f)
            rightMargin = Dp.dpi(context, 12f)
        })

        grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            topMargin = Dp.dpi(context, 92f)
        })

        buildRail()
        showCategory(0)
    }

    private fun applyTheme() {
        title.setTextColor(theme.text)
    }

    private fun buildRail() {
        rail.removeAllViews()
        val labels = listOf(I18n.t("Недавние", "Recent")) + Emoji.categories.map { it.first }
        labels.forEachIndexed { index, label ->
            val chip = RailChip(context, theme, label, index == selected)
            chip.setOnClickListener {
                Haptics.tap(this, 1)
                showCategory(index)
            }
            rail.addView(chip, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, Dp.dpi(context, 34f)).apply {
                rightMargin = Dp.dpi(context, 8f)
            })
        }
    }

    private fun showCategory(index: Int) {
        selected = index
        for (i in 0 until rail.childCount) {
            (rail.getChildAt(i) as? RailChip)?.setActive(i == index)
        }
        grid.removeAllViews()
        val items = when (index) {
            0 -> Emoji.recent.toList().ifEmpty { Emoji.categories[0].second.take(24) }
            else -> Emoji.categories.getOrNull(index - 1)?.second ?: emptyList()
        }
        val perRow = 8
        var row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        grid.addView(row)
        items.forEachIndexed { i, emoji ->
            if (i > 0 && i % perRow == 0) {
                row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                }
                grid.addView(row)
            }
            val cell = EmojiCell(context, emoji)
            cell.setOnClickListener {
                Haptics.tap(this, 1)
                Emoji.recent.remove(emoji)
                Emoji.recent.addFirst(emoji)
                if (Emoji.recent.size > 40) Emoji.recent.removeLast()
                onEmoji?.invoke(emoji)
            }
            row.addView(cell, LinearLayout.LayoutParams(0, Dp.dpi(context, 46f), 1f))
        }
    }
}

private class EmojiCell(context: Context, emoji: String) : TextView(context) {
    init {
        text = emoji
        textSize = 24f
        gravity = Gravity.CENTER
        isClickable = true
        setPadding(0, Dp.dpi(context, 4f), 0, Dp.dpi(context, 4f))
    }
}

private class RailChip(context: Context, private val theme: Theme, label: String, active: Boolean) : TextView(context) {

    private var isActive = active

    init {
        text = label
        textSize = 12.5f
        typeface = Type.face(600)
        gravity = Gravity.CENTER
        setPadding(Dp.dpi(context, 14f), 0, Dp.dpi(context, 14f), 0)
        isClickable = true
        refresh()
    }

    fun setActive(active: Boolean) {
        if (isActive == active) return
        isActive = active
        refresh()
    }

    private fun refresh() {
        setTextColor(if (isActive) theme.accentText else Colors.alpha(theme.text, 0.72f))
        background = object : android.graphics.drawable.Drawable() {
            private val rect = RectF()
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun draw(canvas: Canvas) {
                rect.set(bounds)
                if (isActive) {
                    paint.color = theme.accent
                    canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint)
                } else {
                    paint.color = Colors.alpha(theme.text, 0.08f)
                    canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint)
                }
            }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
            @Deprecated("Deprecated in Drawable")
            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        }
    }
}

private class CloseButtonSmall(context: Context, private val theme: Theme) : SpringyView(context) {
    private val press = spring(520f, 26f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Dp.dp(context, 1.6f)
    }

    init { isClickable = true }

    override fun drawContent(canvas: Canvas) {
        paint.color = Colors.alpha(theme.text, 0.1f + 0.08f * press.value)
        canvas.drawCircle(width / 2f, height / 2f, width / 2f - Dp.dp(context, 1f), paint)
        stroke.color = Colors.alpha(theme.text, 0.7f)
        val r = Dp.dp(context, 8f)
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawLine(cx - r * 0.55f, cy - r * 0.55f, cx + r * 0.55f, cy + r * 0.55f, stroke)
        canvas.drawLine(cx + r * 0.55f, cy - r * 0.55f, cx - r * 0.55f, cy + r * 0.55f, stroke)
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

private class BackspaceButton(context: Context, private val theme: Theme) : SpringyView(context) {
    private val press = spring(520f, 26f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init { isClickable = true }

    override fun drawContent(canvas: Canvas) {
        paint.color = Colors.alpha(theme.text, 0.1f + 0.08f * press.value)
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), height / 2f, height / 2f, paint)
        app.limbo.keyboard.ui.kit.IconPainter.draw(
            canvas, app.limbo.keyboard.layout.Glyph.BACKSPACE,
            width / 2f, height / 2f, Dp.dp(context, 17f),
            Colors.alpha(theme.text, 0.8f), stroke, paint
        )
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
