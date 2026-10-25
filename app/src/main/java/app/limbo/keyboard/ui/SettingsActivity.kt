package app.limbo.keyboard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import app.limbo.keyboard.core.Colors
import app.limbo.keyboard.core.Dp
import app.limbo.keyboard.core.I18n
import app.limbo.keyboard.core.MotionStyle
import app.limbo.keyboard.core.Type
import app.limbo.keyboard.layout.Glyph
import app.limbo.keyboard.layout.Language
import app.limbo.keyboard.theme.Theme
import app.limbo.keyboard.ui.kit.ChipsRow
import app.limbo.keyboard.ui.kit.Haptics
import app.limbo.keyboard.ui.kit.Segmented
import app.limbo.keyboard.ui.kit.Slider
import app.limbo.keyboard.ui.kit.SoftButton
import app.limbo.keyboard.ui.kit.Toggle
import app.limbo.keyboard.ui.kit.Ui
import app.limbo.keyboard.ui.kit.listRow

/** Everything that is not a colour lives here: behaviour, layout, languages, privacy. */
class SettingsActivity : BaseActivity() {

    override fun onBuild() {
        buildHeader()
        buildStatusSection()
        buildAppearanceSection()
        buildTypingSection()
        buildLayoutSection()
        buildLanguagesSection()
        buildClipboardSection()
        buildDataSection()
        buildAbout()
        Ui.space(this, 24f).also { content.addView(it) }
    }

    override fun onResume() {
        super.onResume()
    }

    // ------------------------------------------------------------------ scaffolding

    private fun buildHeader() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Dp.dpi(this@SettingsActivity, 8f), 0, 0)
        }
        val back = SoftButton(this, theme, "", SoftButton.Kind.GLASS, Glyph.ARROW_LEFT).apply {
            compact = true
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(Dp.dpi(this, 46f), Dp.dpi(this, 42f)))
        row.addView(Ui.textView(this, I18n.t("Настройки", "Settings"), Type.TITLE, 700, theme.text, tracking = -0.03f).apply {
            setPadding(Dp.dpi(this@SettingsActivity, 12f), 0, 0, 0)
        })
        content.addView(row)
    }

    private fun section(title: String): LinearLayout {
        Ui.space(this, 16f).also { content.addView(it) }
        val card = Ui.card(this, theme, 16f, 24f)
        card.addView(Ui.section(this, title, theme))
        content.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return card
    }

    private fun divider(parent: LinearLayout) {
        val line = View(this).apply {
            setBackgroundColor(Colors.alpha(theme.text, 0.08f))
        }
        parent.addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
            topMargin = Dp.dpi(this@SettingsActivity, 8f)
            bottomMargin = Dp.dpi(this@SettingsActivity, 8f)
        })
    }

    private fun toggleRow(parent: LinearLayout, title: String, subtitle: String?, initial: Boolean, onChange: (Boolean) -> Unit) {
        val toggle = Toggle(this, theme, initial) { value ->
            Haptics.tap(this@SettingsActivity.window.decorView, 1)
            onChange(value)
        }
        parent.addView(listRow(this, theme, title, subtitle, toggle))
    }

    private fun sliderRow(
        parent: LinearLayout,
        title: String,
        min: Float,
        max: Float,
        value: Float,
        step: Float,
        format: (Float) -> String,
        onChange: (Float) -> Unit
    ) {
        parent.addView(Slider(this, theme, title, min, max, value, step, format) { onChange(it) }.apply {
            contentDescription = title
        })
    }

    // ------------------------------------------------------------------ sections

    private fun buildStatusSection() {
        val card = section(I18n.t("Клавиатура", "Keyboard"))
        val enabled = runCatching {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.any { it.packageName == packageName }
        }.getOrDefault(false)
        val selected = (Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "").startsWith(packageName)

        card.addView(Ui.textView(
            this,
            when {
                enabled && selected -> I18n.t("Включена и выбрана — всё готово", "Enabled and selected — all set")
                enabled -> I18n.t("Включена, ждёт выбора в списке клавиатур", "Enabled, waiting to be picked")
                else -> I18n.t("Выключена — включите, чтобы печатать", "Off — turn it on to start typing")
            },
            Type.CALLOUT, 500,
            if (enabled && selected) Colors.parse("#48D597", theme.text) else theme.textSecondary,
            lineSpacing = 1.3f
        ))
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, Dp.dpi(this@SettingsActivity, 12f), 0, 0)
        }
        val open = SoftButton(this, theme, I18n.t("Настройки клавиатур", "Keyboard settings"), SoftButton.Kind.GLASS).apply {
            compact = true
            setOnClickListener { runCatching { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) } }
        }
        val pick = SoftButton(this, theme, I18n.t("Выбрать", "Select"), SoftButton.Kind.PRIMARY).apply {
            compact = true
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
            }
        }
        actions.addView(open, LinearLayout.LayoutParams(0, Dp.dpi(this, 42f), 1.25f).apply { rightMargin = Dp.dpi(this@SettingsActivity, 8f) })
        actions.addView(pick, LinearLayout.LayoutParams(0, Dp.dpi(this, 42f), 1f))
        card.addView(actions)
    }

    private fun buildAppearanceSection() {
        val card = section(I18n.t("Оформление", "Appearance"))

        val themeRow = listRow(
            this, theme,
            I18n.t("Тема", "Theme"),
            theme.name,
            TrailingChevron()
        )
        themeRow.setOnClickListener {
            startActivity(Intent(this, ThemeStudioActivity::class.java))
        }
        card.addView(themeRow)
        divider(card)

        card.addView(Ui.textView(this, I18n.t("Анимации", "Animations"), Type.BODY - 1f, 550, theme.text).apply {
            setPadding(0, Dp.dpi(this@SettingsActivity, 4f), 0, Dp.dpi(this@SettingsActivity, 8f))
        })
        val motionLabels = MotionStyle.entries.map { if (I18n.isRussian) it.labelRu else it.labelEn }
        card.addView(Segmented(this, theme, motionLabels, settings.motion.ordinal) { index ->
            settings.motion = MotionStyle.entries[index]
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this, 42f)))

        toggleRow(card, I18n.t("Вибрация", "Vibration"), I18n.t("Короткий отклик на каждое нажатие", "A short pulse on every press"),
            settings.haptics) { settings.haptics = it }
        toggleRow(card, I18n.t("Звук", "Sound"), I18n.t("Тихий щелчок клавиши", "A quiet key click"),
            settings.sound) { settings.sound = it }

        val strengths = listOf(I18n.t("Мягкая", "Soft"), I18n.t("Обычная", "Normal"), I18n.t("Сильная", "Strong"))
        card.addView(Segmented(this, theme, strengths, settings.hapticStrength.coerceIn(0, 2)) { settings.hapticStrength = it },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this, 42f)).apply {
                topMargin = Dp.dpi(this@SettingsActivity, 8f)
            })
    }

    private fun buildTypingSection() {
        val card = section(I18n.t("Набор текста", "Typing"))
        toggleRow(card, I18n.t("Заглавные после точки", "Capitalise after a full stop"), null, settings.autoCaps) { settings.autoCaps = it }
        toggleRow(card, I18n.t("Умный пробел", "Smart space"), I18n.t("Пробел сам ставится после слов и символов", "Adds spaces after words and symbols"), settings.smartSpace) { settings.smartSpace = it }
        toggleRow(card, I18n.t("Свайп по пробелу", "Swipe on space"), I18n.t("Двигает курсор без касания текста", "Moves the caret through the text"), settings.swipeToCursor) { settings.swipeToCursor = it }
        toggleRow(card, I18n.t("Подсказки слов", "Word suggestions"), I18n.t("Учит ваши слова и предлагает окончания", "Learns your words and offers endings"), settings.suggestions) { settings.suggestions = it }
        toggleRow(card, I18n.t("Двойной пробел — точка", "Double space for a full stop"), null, settings.doubleSpacePeriod) { settings.doubleSpacePeriod = it }
    }

    private fun buildLayoutSection() {
        val card = section(I18n.t("Раскладка", "Layout"))

        card.addView(Ui.textView(this, I18n.t("Высота клавиш", "Key height"), Type.BODY - 1f, 550, theme.text).apply {
            setPadding(0, 0, 0, Dp.dpi(this@SettingsActivity, 8f))
        })
        val heights = listOf(I18n.t("Компактно", "Compact"), I18n.t("Обычно", "Balanced"), I18n.t("Просторно", "Roomy"))
        card.addView(Segmented(this, theme, heights, settings.heightScale) { settings.heightScale = it },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this, 42f)))

        toggleRow(card, I18n.t("Ряд цифр", "Number row"), I18n.t("Цифры над буквами", "Digits above the letters"), settings.numberRow) { settings.numberRow = it }
        toggleRow(card, I18n.t("Стрелки и выделение", "Arrows and selection"), I18n.t("Дополнительный ряд над буквами", "An extra row above the letters"), settings.cursorRow) { settings.cursorRow = it }
        toggleRow(card, I18n.t("Всплывающие символы", "Character pop-ups"), I18n.t("Дополнительные буквы по удержанию", "Extra letters on a long press"), settings.popups) { settings.popups = it }

        card.addView(Ui.textView(this, I18n.t("Прощающая зона", "Forgiving touch area"), Type.BODY - 1f, 550, theme.text).apply {
            setPadding(0, Dp.dpi(this@SettingsActivity, 12f), 0, 0)
        })
        card.addView(Ui.textView(this, I18n.t("Насколько близко к соседней клавише можно попасть, и всё ещё сработает ваша.",
            "How close to a neighbouring key a press can land and still count as yours."),
            Type.CAPTION, 450, Colors.alpha(theme.textSecondary, 0.85f), lineSpacing = 1.3f))
        card.addView(Slider(this, theme, I18n.t("Чуткость", "Tolerance"), 0f, 1f, settings.touchSlop, 0.05f,
            { "${(it * 100).toInt()}%" }) { settings.touchSlop = it })

        card.addView(Slider(this, theme, I18n.t("Удержание", "Long press"), 180f, 700f, settings.longPressDelay.toFloat(), 10f,
            { "${it.toInt()} мс" }) { settings.longPressDelay = it.toInt() })
    }

    private fun buildLanguagesSection() {
        val card = section(I18n.t("Языки", "Languages"))
        val chips = ChipsRow(
            this, theme,
            Language.entries.map { it.code to it.label },
            settings.languageCodes.toSet(),
            multi = true
        ) { selected ->
            if (selected.isEmpty()) return@ChipsRow
            settings.languageCodes = selected.toList()
            if (!settings.languageCodes.contains(settings.activeLanguage)) {
                settings.activeLanguage = settings.languageCodes.first()
            }
        }
        card.addView(chips, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this, 40f)))
        card.addView(Ui.textView(
            this,
            I18n.t("Язык переключается кнопкой глобуса, а раскладка остаётся привычной для каждого языка.",
                "Switch with the globe key; each language keeps its familiar layout."),
            Type.CAPTION, 450, Colors.alpha(theme.textSecondary, 0.82f), lineSpacing = 1.3f
        ).apply { setPadding(0, Dp.dpi(this@SettingsActivity, 10f), 0, 0) })
    }

    private fun buildClipboardSection() {
        val card = section(I18n.t("Буфер обмена", "Clipboard"))
        toggleRow(card, I18n.t("Запоминать копии", "Remember copies"),
            I18n.t("История хранится только на устройстве", "The history never leaves the device"),
            settings.clipboardEnabled) { settings.clipboardEnabled = it }
        toggleRow(card, I18n.t("Показывать в полосе", "Show above the keys"),
            I18n.t("Последние копии рядом с подсказками", "Recent copies next to word suggestions"),
            settings.clipboardSuggestionsInBar) { settings.clipboardSuggestionsInBar = it }

        card.addView(Slider(this, theme, I18n.t("Сколько хранить", "How many to keep"), 10f, 200f,
            settings.clipboardHistorySize.toFloat(), 5f, { "${it.toInt()}" }) {
            settings.clipboardHistorySize = it.toInt()
        })
        val clear = SoftButton(this, theme, I18n.t("Очистить историю", "Clear history"), SoftButton.Kind.GLASS).apply {
            compact = true
            setOnClickListener {
                limbo.clipboard.clear(keepPinned = true)
                Haptics.native(this@SettingsActivity, 1)
            }
        }
        card.addView(clear, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this, 42f)).apply {
            topMargin = Dp.dpi(this@SettingsActivity, 8f)
        })
        card.addView(Ui.textView(this, I18n.t("${limbo.clipboard.entries().size} фрагментов сейчас",
            "${limbo.clipboard.entries().size} items right now"),
            Type.CAPTION, 450, Colors.alpha(theme.textSecondary, 0.8f)).apply {
            setPadding(0, Dp.dpi(this@SettingsActivity, 8f), 0, 0)
        })
    }

    private fun buildDataSection() {
        val card = section(I18n.t("Данные", "Data"))

        val copy = SoftButton(this, theme, I18n.t("Скопировать настройки", "Copy settings"), SoftButton.Kind.GLASS).apply {
            compact = true
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Limbo settings", settings.exportJson()))
                Haptics.native(this@SettingsActivity, 1)
            }
        }
        card.addView(copy, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this, 42f)))

        val clearDictionary = SoftButton(this, theme, I18n.t("Забыть выученные слова", "Forget learned words"), SoftButton.Kind.GLASS).apply {
            compact = true
            setOnClickListener { limbo.dictionary.clear() }
        }
        card.addView(clearDictionary, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this, 42f)).apply {
            topMargin = Dp.dpi(this@SettingsActivity, 8f)
        })

        val reset = SoftButton(this, theme, I18n.t("Сбросить всё", "Reset everything"), SoftButton.Kind.DANGER).apply {
            compact = true
            setOnClickListener { confirmReset() }
        }
        card.addView(reset, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this, 42f)).apply {
            topMargin = Dp.dpi(this@SettingsActivity, 8f)
        })
    }

    private fun confirmReset() {
        val sheet = app.limbo.keyboard.ui.kit.OverlaySheet(
            this, theme,
            I18n.t("Сбросить настройки?", "Reset everything?"),
            I18n.t("Темы, языки и история буфера вернутся к исходным.", "Themes, languages and clipboard history go back to defaults.")
        )
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancel = SoftButton(this, theme, I18n.t("Отмена", "Cancel"), SoftButton.Kind.GLASS).apply {
            compact = true
            setOnClickListener { sheet.dismiss() }
        }
        val confirm = SoftButton(this, theme, I18n.t("Сбросить", "Reset"), SoftButton.Kind.DANGER).apply {
            compact = true
            setOnClickListener {
                settings.resetEverything()
                limbo.clipboard.clear(keepPinned = false)
                sheet.dismiss()
                recreate()
            }
        }
        row.addView(cancel, LinearLayout.LayoutParams(0, Dp.dpi(this, 44f), 1f).apply { rightMargin = Dp.dpi(this@SettingsActivity, 8f) })
        row.addView(confirm, LinearLayout.LayoutParams(0, Dp.dpi(this, 44f), 1f))
        sheet.body.addView(row)
        sheet.present(root)
    }

    private fun buildAbout() {
        Ui.space(this, 20f).also { content.addView(it) }
        content.addView(Ui.textView(
            this,
            I18n.t("Limbo 1.0 · работает без сети, без аналитики и без рекламы.",
                "Limbo 1.0 · offline, no analytics, no ads."),
            Type.CAPTION, 500, Colors.alpha(theme.textSecondary, 0.7f)
        ))
    }

    /** Quiet chevron used on rows that lead somewhere. */
    private inner class TrailingChevron : android.view.View(this@SettingsActivity) {
        init {
            layoutParams = LinearLayout.LayoutParams(Dp.dpi(this@SettingsActivity, 18f), Dp.dpi(this@SettingsActivity, 18f))
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = Colors.alpha(theme.textSecondary, 0.7f)
                strokeWidth = Dp.dp(this@SettingsActivity, 1.6f)
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
            app.limbo.keyboard.ui.kit.IconPainter.draw(
                canvas, Glyph.CHEVRON, width / 2f, height / 2f, width * 0.7f, stroke.color, stroke, stroke
            )
        }
    }
}
