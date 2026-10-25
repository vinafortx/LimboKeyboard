package app.limbo.keyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.widget.FrameLayout
import app.limbo.keyboard.core.Clock
import app.limbo.keyboard.core.Colors
import app.limbo.keyboard.core.Dp
import app.limbo.keyboard.core.I18n
import app.limbo.keyboard.core.Settings
import app.limbo.keyboard.core.Type
import app.limbo.keyboard.core.Tx
import app.limbo.keyboard.keyboard.KeyboardEvent
import app.limbo.keyboard.keyboard.KeyboardView
import app.limbo.keyboard.layout.Action
import app.limbo.keyboard.layout.Language
import app.limbo.keyboard.theme.Theme
import app.limbo.keyboard.ui.kit.Glass
import app.limbo.keyboard.ui.kit.SpringyView

/** A quiet input field that shows what the demo keyboard types. */
class DemoField(context: Context, var theme: Theme) : SpringyView(context), ThemeAware {

    private val caret = spring(200f, 22f)
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var text: String = ""
        set(value) {
            field = value.takeLast(240)
            invalidate()
        }

    private var caretVisible = true
    private var blinkScheduled = false

    var placeholder: String = I18n.t("Попробуйте напечатать…", "Try typing here…")

    init {
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(android.view.View.MeasureSpec.getSize(widthMeasureSpec), Dp.dpi(context, 54f))
    }

    override fun drawContent(canvas: Canvas) {
        rect.set(Dp.dp(context, 1f), Dp.dp(context, 2f), width - Dp.dp(context, 1f), height - Dp.dp(context, 4f))
        val radius = Dp.dp(context, 16f)
        Glass.panel(
            canvas, rect, radius, theme, context,
            Colors.alpha(theme.keyTop, if (theme.dark) 0.5f else 0.9f),
            Colors.alpha(theme.keyBottom, if (theme.dark) 0.42f else 0.8f),
            false, 0.04f
        )

        val left = rect.left + Dp.dp(context, 14f)
        val available = rect.width() - Dp.dp(context, 30f)
        textPaint.color = theme.text
        textPaint.typeface = Type.face(480)
        textPaint.textSize = Dp.sp(context, 16f)

        if (text.isEmpty()) {
            placeholderPaint.color = Colors.alpha(theme.textSecondary, 0.55f)
            placeholderPaint.typeface = Type.face(450)
            placeholderPaint.textSize = Dp.sp(context, 15f)
            Tx.draw(canvas, placeholder, left, rect.centerY() + placeholderPaint.textSize * 0.35f, placeholderPaint)
            return
        }

        var visible = text
        while (Tx.measure(textPaint, visible) > available && visible.isNotEmpty()) {
            visible = visible.drop(1)
        }
        val baseline = rect.centerY() + textPaint.textSize * 0.35f
        Tx.draw(canvas, visible, left, baseline, textPaint)
        if (caretVisible && !Clock.reducedMotion) {
            val caretX = left + Tx.measure(textPaint, visible) + Dp.dp(context, 1.5f)
            paint.color = theme.accent
            canvas.drawRoundRect(caretX, baseline - textPaint.textSize * 0.78f, caretX + Dp.dp(context, 2f), baseline + textPaint.textSize * 0.18f, 1.5f, 1.5f, paint)
        }
    }

    fun scheduleBlink() {
        if (blinkScheduled) return
        blinkScheduled = true
        postDelayed({
            blinkScheduled = false
            caretVisible = !caretVisible
            invalidate()
            scheduleBlink()
        }, 520)
    }

    override fun applyTheme(theme: Theme) {
        this.theme = theme
        invalidate()
    }
}

/**
 * The interactive preview used on the home screen and in onboarding. It is the real keyboard
 * view, so what you touch is exactly what you will get.
 */
class DemoKeyboard(context: Context, theme: Theme, settings: Settings, language: Language = Language.EN) : FrameLayout(context) {

    val field = DemoField(context, theme)
    val keyboard = KeyboardView(context)
    var onTextChanged: ((String) -> Unit)? = null

    init {
        keyboard.theme = theme
        keyboard.settings = settings
        keyboard.setLanguage(language)
        keyboard.onEvent = { event ->
            when (event) {
                is KeyboardEvent.Commit -> {
                    field.text += event.text
                    field.scheduleBlink()
                    onTextChanged?.invoke(field.text)
                }
                is KeyboardEvent.Delete -> {
                    field.text = field.text.dropLast(1)
                    onTextChanged?.invoke(field.text)
                }
                is KeyboardEvent.Cursor -> Unit
                is KeyboardEvent.ActionRequested -> if (event.action == Action.ENTER) {
                    field.text += "\n"
                }
                else -> Unit
            }
        }

        addView(field, LayoutParams(LayoutParams.MATCH_PARENT, Dp.dpi(context, 54f)).apply {
            leftMargin = Dp.dpi(context, 10f)
            rightMargin = Dp.dpi(context, 10f)
            topMargin = Dp.dpi(context, 10f)
        })
        val boardParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = Dp.dpi(context, 64f)
        }
        addView(keyboard, boardParams)
        field.scheduleBlink()
    }

    fun applyTheme(theme: Theme) {
        keyboard.theme = theme
        field.applyTheme(theme)
    }

    fun resetText() {
        field.text = ""
        onTextChanged?.invoke("")
    }
}

/** Small wordmark used across screens: the name plus a quiet descriptor. */
class Wordmark(context: Context, var theme: Theme, compact: Boolean = false) : SpringyView(context), ThemeAware {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG)
    private val compactSize = compact

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(android.view.View.MeasureSpec.getSize(widthMeasureSpec), Dp.dpi(context, if (compactSize) 40f else 62f))
    }

    override fun drawContent(canvas: Canvas) {
        paint.color = theme.text
        paint.typeface = Type.face(700)
        paint.textSize = Dp.sp(context, if (compactSize) 20f else 30f)
        Tx.draw(canvas, "Limbo", Dp.dp(context, 2f), Dp.sp(context, if (compactSize) 26f else 40f), paint, -Dp.dp(context, 0.6f))
        sub.color = Colors.alpha(theme.textSecondary, 0.85f)
        sub.typeface = Type.face(550)
        sub.textSize = Dp.sp(context, 11.5f)
        sub.letterSpacing = 0.18f
        Tx.draw(
            canvas,
            I18n.t("КЛАВИАТУРА", "KEYBOARD"),
            Dp.dp(context, 3f) + Tx.measure(paint, "Limbo", -Dp.dp(context, 0.6f)) + Dp.dp(context, 9f),
            Dp.sp(context, if (compactSize) 20f else 32f),
            sub
        )
    }

    override fun applyTheme(theme: Theme) {
        this.theme = theme
        invalidate()
    }
}
