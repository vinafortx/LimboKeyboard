package app.limbo.keyboard.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.limbo.keyboard.core.Colors
import app.limbo.keyboard.core.Dp
import app.limbo.keyboard.core.I18n
import app.limbo.keyboard.core.Type
import app.limbo.keyboard.layout.Glyph
import app.limbo.keyboard.theme.Theme
import app.limbo.keyboard.theme.Themes
import app.limbo.keyboard.ui.kit.GlassDrawable
import app.limbo.keyboard.ui.kit.GlyphView
import app.limbo.keyboard.ui.kit.Haptics
import app.limbo.keyboard.ui.kit.SoftButton
import app.limbo.keyboard.ui.kit.Ui
import app.limbo.keyboard.ui.kit.themeGrid

/** Home: try the keyboard, see whether it is switched on, jump into what matters. */
class HomeActivity : BaseActivity() {

    private var onboardingLaunched = false
    private lateinit var demo: DemoKeyboard
    private lateinit var statusCard: LinearLayout
    private lateinit var themeTile: LinearLayout
    private lateinit var languageTile: LinearLayout
    private lateinit var clipboardTile: LinearLayout

    override fun onBuild() {
        buildHeader()
        buildHero()
        statusCard = buildStatusCard()
        buildTiles()
        buildFooter()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshTiles()
        if (!settings.onboardingDone && !onboardingLaunched) {
            onboardingLaunched = true
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    // ------------------------------------------------------------------ header

    private fun buildHeader() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Dp.dpi(this@HomeActivity, 8f), 0, 0)
        }
        val mark = Wordmark(this, theme)
        row.addView(mark, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val gear = SoftButton(this, theme, "", SoftButton.Kind.GLASS, Glyph.GEAR).apply {
            compact = true
            contentDescription = I18n.t("Настройки", "Settings")
            setOnClickListener { openSettings() }
        }
        row.addView(gear, LinearLayout.LayoutParams(Dp.dpi(this, 46f), Dp.dpi(this, 42f)))
        content.addView(row)
    }

    // ------------------------------------------------------------------ hero

    private fun buildHero() {
        Ui.space(this, 16f).also { content.addView(it) }

        val headline = Ui.textView(
            this,
            I18n.t("Печатайте свободно", "Type freely"),
            Type.DISPLAY, 700, theme.text, tracking = -0.035f, lineSpacing = 1.06f
        )
        content.addView(headline)

        content.addView(Ui.textView(
            this,
            I18n.t(
                "Клавиши не нужно ловить точно — соседняя клавиша рядом тоже сработает. Попробуйте прямо здесь.",
                "You never have to hit a key exactly — the closest neighbour counts too. Try it right here."
            ),
            Type.CALLOUT, 450, Colors.alpha(theme.textSecondary, 0.95f), tracking = -0.005f, lineSpacing = 1.35f
        ).apply { setPadding(0, Dp.dpi(this@HomeActivity, 8f), 0, 0) })

        Ui.space(this, 16f).also { content.addView(it) }

        val frame = FrameLayout(this).apply {
            background = GlassDrawable(this@HomeActivity, theme, 28f)
            setPadding(Dp.dpi(this@HomeActivity, 4f), Dp.dpi(this@HomeActivity, 4f), Dp.dpi(this@HomeActivity, 4f), Dp.dpi(this@HomeActivity, 8f))
        }
        demo = DemoKeyboard(this, theme, settings).apply {
            keyboard.setLanguage(settings.languages.firstOrNull() ?: app.limbo.keyboard.layout.Language.EN)
        }
        frame.addView(demo, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        content.addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    // ------------------------------------------------------------------ status

    private fun buildStatusCard(): LinearLayout {
        Ui.space(this, 14f).also { content.addView(it) }
        val card = Ui.card(this, theme, 18f, 24f)
        content.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return card
    }

    private fun refreshStatus() {
        statusCard.removeAllViews()
        val enabled = isKeyboardEnabled()
        val selected = isKeyboardSelected()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dot = View(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(if (enabled && selected) Color.parseColor("#48D597") else Color.parseColor("#F0B65A"))
                setSize(Dp.dpi(this@HomeActivity, 8f), Dp.dpi(this@HomeActivity, 8f))
            }
        }
        row.addView(dot, LinearLayout.LayoutParams(Dp.dpi(this, 8f), Dp.dpi(this, 8f)).apply { rightMargin = Dp.dpi(this@HomeActivity, 10f) })
        row.addView(Ui.textView(
            this,
            when {
                enabled && selected -> I18n.t("Клавиатура подключена", "Keyboard is ready")
                enabled -> I18n.t("Клавиатура включена, но не выбрана", "Enabled, but not selected yet")
                else -> I18n.t("Клавиатура пока выключена", "Keyboard is off for now")
            },
            Type.BODY, 600, theme.text, tracking = -0.01f
        ), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        statusCard.addView(row)

        if (!(enabled && selected)) {
            statusCard.addView(Ui.textView(
                this,
                I18n.t(
                    "Два коротких шага — и она появится при вводе текста в любом приложении.",
                    "Two short steps and it appears whenever you type, in any app."
                ),
                Type.CALLOUT, 450, Colors.alpha(theme.textSecondary, 0.9f), lineSpacing = 1.3f
            ).apply { setPadding(0, Dp.dpi(this@HomeActivity, 8f), 0, Dp.dpi(this@HomeActivity, 4f)) })

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, Dp.dpi(this@HomeActivity, 10f), 0, 0)
            }
            val enable = SoftButton(
                this, theme,
                if (enabled) I18n.t("Выбрать", "Select") else I18n.t("Включить", "Enable"),
                SoftButton.Kind.PRIMARY
            ).apply {
                compact = true
                setOnClickListener {
                    if (!enabled) {
                        runCatching { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                    } else {
                        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
                    }
                }
            }
            actions.addView(enable, LinearLayout.LayoutParams(0, Dp.dpi(this, 42f), 1f))
            statusCard.addView(actions)
        }
    }

    private fun isKeyboardEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
        return runCatching { imm.enabledInputMethodList.any { it.packageName == packageName } }.getOrDefault(false)
    }

    private fun isKeyboardSelected(): Boolean {
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
        return current.startsWith(packageName)
    }

    // ------------------------------------------------------------------ tiles

    private fun buildTiles() {
        Ui.space(this, 14f).also { content.addView(it) }

        val firstRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        themeTile = makeTile(I18n.t("Оформление", "Appearance"), Glyph.STAR) { openThemeStudio() }
        languageTile = makeTile(I18n.t("Языки", "Languages"), Glyph.GLOBE) { showLanguageSheet() }
        firstRow.addView(themeTile, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Dp.dpi(this@HomeActivity, 10f) })
        firstRow.addView(languageTile, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(firstRow)

        Ui.space(this, 10f).also { content.addView(it) }

        val secondRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        clipboardTile = makeTile(I18n.t("Буфер обмена", "Clipboard"), Glyph.CLIPBOARD) { showClipboardSheet() }
        val settingsTile = makeTile(I18n.t("Настройки", "Settings"), Glyph.GEAR) { openSettings() }
        secondRow.addView(clipboardTile, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Dp.dpi(this@HomeActivity, 10f) })
        secondRow.addView(settingsTile, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(secondRow)
    }

    private fun makeTile(title: String, glyph: Glyph, action: () -> Unit): LinearLayout {
        val card = Ui.card(this, theme, 16f, 22f).apply {
            isClickable = true
            setOnClickListener {
                Haptics.tap(this, 1)
                action()
            }
        }
        val icon = FrameLayout(this)
        val iconView = GlyphView(this, theme, glyph, theme.accent)
        icon.addView(iconView, FrameLayout.LayoutParams(Dp.dpi(this, 22f), Dp.dpi(this, 22f)))
        card.addView(icon, LinearLayout.LayoutParams(Dp.dpi(this, 22f), Dp.dpi(this, 22f)))
        card.addView(Ui.textView(this, title, Type.BODY - 0.5f, 600, theme.text, tracking = -0.012f).apply {
            setPadding(0, Dp.dpi(this@HomeActivity, 12f), 0, 0)
        })
        val subtitle = Ui.textView(this, "", Type.CAPTION, 450, Colors.alpha(theme.textSecondary, 0.9f)).apply {
            setPadding(0, Dp.dpi(this@HomeActivity, 3f), 0, 0)
        }
        subtitle.tag = "subtitle"
        card.addView(subtitle)
        return card
    }

    private fun refreshTiles() {
        (themeTile.findViewWithTag<View>("subtitle") as? android.widget.TextView)?.text = theme.name
        (languageTile.findViewWithTag<View>("subtitle") as? android.widget.TextView)?.text =
            settings.languages.joinToString(" · ") { it.label }
        (clipboardTile.findViewWithTag<View>("subtitle") as? android.widget.TextView)?.text =
            I18n.t("${limbo.clipboard.entries().size} фрагментов", "${limbo.clipboard.entries().size} items")
    }

    // ------------------------------------------------------------------ footer

    private fun buildFooter() {
        Ui.space(this, 18f).also { content.addView(it) }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val replay = SoftButton(this, theme, I18n.t("Пройти знакомство", "Replay the intro"), SoftButton.Kind.GLASS).apply {
            compact = true
            setOnClickListener {
                startActivity(Intent(this@HomeActivity, OnboardingActivity::class.java))
            }
        }
        row.addView(replay, LinearLayout.LayoutParams(0, Dp.dpi(this, 42f), 1f))
        content.addView(row)

        content.addView(Ui.textView(
            this,
            I18n.t("Всё работает без интернета. Слова и буфер остаются на устройстве.",
                "Runs entirely offline. Words and clipboard stay on your device."),
            Type.CAPTION, 450, Colors.alpha(theme.textSecondary, 0.7f), lineSpacing = 1.3f
        ).apply { setPadding(0, Dp.dpi(this@HomeActivity, 12f), 0, 0) })
    }

    // ------------------------------------------------------------------ navigation

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun openThemeStudio() {
        startActivity(Intent(this, ThemeStudioActivity::class.java))
    }

    private fun showLanguageSheet() {
        val sheet = app.limbo.keyboard.ui.kit.OverlaySheet(
            this, theme,
            I18n.t("Языки ввода", "Input languages"),
            I18n.t("Переключение — кнопкой глобуса на клавиатуре", "Switch with the globe key on the keyboard")
        )
        val chips = app.limbo.keyboard.ui.kit.ChipsRow(
            this, theme,
            app.limbo.keyboard.layout.Language.entries.map { it.code to it.label },
            settings.languageCodes.toSet(),
            multi = true
        ) { selected ->
            if (selected.isEmpty()) return@ChipsRow
            settings.languageCodes = selected.toList()
            demo.keyboard.setLanguage(settings.languages.first())
            refreshTiles()
        }
        sheet.body.addView(chips, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this, 40f)))
        sheet.body.addView(Ui.textView(
            this,
            I18n.t("Раскладка подстраивается под каждый язык автоматически.", "Each language keeps its own natural layout."),
            Type.CAPTION, 450, Colors.alpha(theme.textSecondary, 0.85f)
        ).apply { setPadding(0, Dp.dpi(this@HomeActivity, 10f), 0, 0) })
        sheet.present(root)
    }

    private fun showClipboardSheet() {
        val entries = limbo.clipboard.entries()
        val sheet = app.limbo.keyboard.ui.kit.OverlaySheet(
            this, theme,
            I18n.t("Буфер обмена", "Clipboard"),
            I18n.t("${entries.size} сохранённых фрагментов", "${entries.size} saved items")
        )
        if (entries.isEmpty()) {
            sheet.body.addView(Ui.textView(
                this,
                I18n.t("Скопируйте что-нибудь — и это появится здесь, даже после перезапуска.",
                    "Copy something and it lands here, even after a restart."),
                Type.CALLOUT, 450, Colors.alpha(theme.textSecondary, 0.9f), lineSpacing = 1.3f
            ))
        } else {
            entries.take(4).forEach { entry ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                row.addView(Ui.textView(this, entry.preview, Type.CALLOUT, 500, theme.text, lineSpacing = 1.25f))
                row.addView(Ui.textView(
                    this,
                    if (entry.pinned) I18n.t("Закреплено", "Pinned") else I18n.t("Недавнее", "Recent"),
                    Type.MICRO, 600, Colors.alpha(theme.textSecondary, 0.7f)
                ))
                row.setPadding(0, Dp.dpi(this, 8f), 0, Dp.dpi(this, 8f))
                sheet.body.addView(row)
            }
        }
        val open = SoftButton(this, theme, I18n.t("Открыть приложение клавиатуры", "Open from the keyboard"), SoftButton.Kind.GLASS).apply {
            compact = true
            setOnClickListener { sheet.dismiss() }
        }
        sheet.body.addView(open, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this, 42f)).apply {
            topMargin = Dp.dpi(this@HomeActivity, 12f)
        })
        sheet.present(root)
    }

    override fun applyTheme(theme: Theme) {
        super.applyTheme(theme)
        demo.applyTheme(theme)
    }
}
