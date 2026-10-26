package app.limbo.keyboard.ime

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import app.limbo.keyboard.core.Colors
import app.limbo.keyboard.core.Dp
import app.limbo.keyboard.core.I18n
import app.limbo.keyboard.theme.Theme

/**
 * A plain board used only when the drawn one cannot be built.
 *
 * It is deliberately boring: system type sizes, one background colour, no shaders, no animation,
 * one view per key. That is the point — there is almost nothing here that can fail, so a problem
 * in the full keyboard costs the user their looks for a moment, never the ability to type.
 */
class FallbackBoard(
    context: Context,
    theme: Theme,
    private val onText: (String) -> Unit,
    private val onDelete: () -> Unit,
    private val onEnter: () -> Unit
) : LinearLayout(context) {

    private val rowHeight = Dp.dpi(context, 48f)
    private val labelColor = theme.text
    private val keyColor = Colors.alpha(theme.keyTop, if (theme.dark) 0.55f else 0.38f)

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.panelBottom)
        setPadding(0, Dp.dpi(context, 4f), 0, Dp.dpi(context, 6f))

        rows().forEach { row ->
            addView(letterRow(row))
        }
        addView(bottomRow())
    }

    private fun rows(): List<String> = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

    private fun letterRow(keys: String): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(Dp.dpi(context, 2f), Dp.dpi(context, 2f), Dp.dpi(context, 2f), 0)
        keys.forEach { char -> addView(key(char.toString(), 1f) { onText(char.toString()) }) }
    }

    private fun bottomRow(): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(Dp.dpi(context, 2f), Dp.dpi(context, 2f), Dp.dpi(context, 2f), 0)
        addView(key(I18n.t("Пробел", "Space"), 4.6f) { onText(" ") })
        addView(key("⌫", 1.2f) { onDelete() })
        addView(key("⏎", 1.2f) { onEnter() })
    }

    private fun key(label: String, weight: Float, action: () -> Unit): TextView = TextView(context).apply {
        text = label
        setTextColor(labelColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        gravity = Gravity.CENTER
        setBackgroundColor(keyColor)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, rowHeight, weight).apply {
            marginStart = Dp.dpi(context, 2f)
            marginEnd = Dp.dpi(context, 2f)
        }
        isClickable = true
        contentDescription = label
    }
}
