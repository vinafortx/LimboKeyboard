package app.limbo.keyboard.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import app.limbo.keyboard.core.Clock
import app.limbo.keyboard.core.Crash
import app.limbo.keyboard.core.Colors
import app.limbo.keyboard.core.Dp
import app.limbo.keyboard.core.I18n
import app.limbo.keyboard.core.MotionStyle
import app.limbo.keyboard.core.Spring
import app.limbo.keyboard.core.Type
import app.limbo.keyboard.keyboard.KeyboardView
import app.limbo.keyboard.layout.Glyph
import app.limbo.keyboard.layout.Language
import app.limbo.keyboard.theme.Theme
import app.limbo.keyboard.theme.Themes
import app.limbo.keyboard.ui.kit.ChipsRow
import app.limbo.keyboard.ui.kit.Fx
import app.limbo.keyboard.ui.kit.Glass
import app.limbo.keyboard.ui.kit.GlassDrawable
import app.limbo.keyboard.ui.kit.Haptics
import app.limbo.keyboard.ui.kit.IconPainter
import app.limbo.keyboard.ui.kit.Segmented
import app.limbo.keyboard.ui.kit.Slider
import app.limbo.keyboard.ui.kit.SoftButton
import app.limbo.keyboard.ui.kit.SpringyView
import app.limbo.keyboard.ui.kit.Toggle
import app.limbo.keyboard.ui.kit.Ui
import app.limbo.keyboard.ui.kit.WallpaperDrawable
import app.limbo.keyboard.ui.kit.themeGrid
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * A short, honest introduction: why the keyboard feels different, the two system steps needed to
 * switch it on, then the choices that actually shape it — language, layout, look and clipboard.
 * The final page is a typing test, so the promise is proven before you leave.
 */
class OnboardingActivity : android.app.Activity(), ThemeAware {

    private val limbo get() = application as app.limbo.keyboard.LimboApp
    private val settings get() = limbo.settings
    private val theme: Theme get() = settings.theme

    private lateinit var wallpaper: WallpaperDrawable
    private lateinit var root: FrameLayout
    private lateinit var contentHost: LinearLayout
    private lateinit var dots: StepDots
    private lateinit var footer: LinearLayout
    private lateinit var primary: SoftButton
    private lateinit var secondary: SoftButton

    private var step = 0
    private var testDemo: DemoKeyboard? = null
    private var targetPhrase = ""

    private val totalSteps = 8

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { setupLimboWindow(theme) }

        val shell = Crash.guard(this) {
            buildShell()
            true
        } == true
        if (!shell) {
            // Nothing can be drawn; the home screen is a better place to be than a dead window.
            finish()
            return
        }

        step = runCatching { settings.onboardingStep }.getOrDefault(0).coerceIn(0, totalSteps - 1)
        if (Crash.guard(this) { renderStep() } == null && step == 0) finish()
    }

    override fun onResume() {
        super.onResume()
        if (step == 1 || step == 2) renderStep(animate = false)
    }

    // ------------------------------------------------------------------ shell

    private fun buildShell() {
        wallpaper = WallpaperDrawable(this, theme)
        root = FrameLayout(this).apply { background = wallpaper }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Dp.dpi(this@OnboardingActivity, 20f), 0, Dp.dpi(this@OnboardingActivity, 20f), 0)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(Wordmark(this, theme, compact = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val skip = SoftButton(this, theme, I18n.t("Пропустить", "Skip"), SoftButton.Kind.GLASS).apply {
            compact = true
            setOnClickListener { finishOnboarding() }
        }
        top.addView(skip, LinearLayout.LayoutParams(Dp.dpi(this, 108f), Dp.dpi(this, 38f)))
        column.addView(top, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Dp.dpi(this@OnboardingActivity, 12f)
        })

        dots = StepDots(this, theme, totalSteps).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Dp.dpi(this@OnboardingActivity, 22f)).apply {
                topMargin = Dp.dpi(this@OnboardingActivity, 14f)
            }
        }
        column.addView(dots)

        contentHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            addView(contentHost, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        column.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Dp.dpi(this@OnboardingActivity, 6f), 0, Dp.dpi(this@OnboardingActivity, 16f))
        }
        primary = SoftButton(this, theme, "", SoftButton.Kind.PRIMARY)
        secondary = SoftButton(this, theme, "", SoftButton.Kind.QUIET)
        footer.addView(primary, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this, 54f)))
        footer.addView(secondary, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this, 42f)).apply {
            topMargin = Dp.dpi(this@OnboardingActivity, 4f)
        })
        column.addView(footer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
        root.setOnApplyWindowInsetsListener { _, insets ->
            column.setPadding(
                Dp.dpi(this, 20f),
                insets.systemWindowInsetTop + Dp.dpi(this, 4f),
                Dp.dpi(this, 20f),
                insets.systemWindowInsetBottom + Dp.dpi(this, 6f)
            )
            insets
        }
    }

    // ------------------------------------------------------------------ steps

    private fun renderStep(animate: Boolean = true) {
        dots.selected = step
        contentHost.removeAllViews()
        testDemo = null
        val stepContent = when (step) {
            0 -> welcomeStep()
            1 -> enableStep()
            2 -> selectStep()
            3 -> languagesStep()
            4 -> layoutStep()
            5 -> themeStep()
            6 -> clipboardStep()
            else -> testStep()
        }
        contentHost.addView(stepContent)
        if (animate) Fx.enter(stepContent, 22f)

        primary.label = when (step) {
            0 -> I18n.t("Начать", "Start")
            1 -> I18n.t("Открыть настройки", "Open settings")
            2 -> I18n.t("Выбрать клавиатуру", "Choose keyboard")
            3 -> I18n.t("Дальше", "Continue")
            4 -> I18n.t("Дальше", "Continue")
            5 -> I18n.t("Дальше", "Continue")
            6 -> I18n.t("Дальше", "Continue")
            else -> I18n.t("Готово", "Done")
        }
        primary.setOnClickListener { onPrimary() }
        secondary.label = if (step == 0) I18n.t("Посмотреть, что внутри", "Take a quick look") else I18n.t("Назад", "Back")
        secondary.setOnClickListener {
            if (step == 0) {
                jumpTo(3)
            } else {
                jumpTo(step - 1)
            }
        }
        Haptics.tap(dots, 0)
    }

    private fun onPrimary() {
        when (step) {
            1 -> openImeSettings()
            2 -> (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
            else -> Unit
        }
        if (step < totalSteps - 1) {
            jumpTo(step + 1)
        } else {
            finishOnboarding()
        }
    }

    private fun jumpTo(next: Int) {
        if (next == step) return
        step = next.coerceIn(0, totalSteps - 1)
        settings.onboardingStep = step
        renderStep()
    }

    private fun finishOnboarding() {
        settings.onboardingDone = true
        settings.onboardingStep = 0
        Fx.exit(contentHost, 30f) { finish() }
    }

    private fun openImeSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
    }

    private fun isEnabled(): Boolean = runCatching {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.any { it.packageName == packageName }
    }.getOrDefault(false)

    private fun isSelected(): Boolean =
        (Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "").startsWith(packageName)

    // ------------------------------------------------------------------ step views

    private fun welcomeStep(): View {
        val column = Ui.column(this)
        val art = HeroArt(this, theme, HeroArt.Kind.KEYS).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this@OnboardingActivity, 210f))
        }
        column.addView(art)
        column.addView(Ui.textView(
            this,
            I18n.t("Клавиатура, которая подстраивается под вас", "A keyboard that adapts to you"),
            Type.DISPLAY - 2f, 700, theme.text, tracking = -0.035f, lineSpacing = 1.06f
        ).apply { setPadding(0, Dp.dpi(this@OnboardingActivity, 8f), 0, 0) })
        column.addView(Ui.textView(
            this,
            I18n.t(
                "Привычная раскладка, клавиши, которые не требуют снайперской точности, и буфер обмена, который помнит всё скопированное.",
                "A familiar layout, keys that do not demand perfect aim, and a clipboard that remembers everything you copy."
            ),
            Type.CALLOUT, 450, Colors.alpha(theme.textSecondary, 0.95f), lineSpacing = 1.36f
        ).apply { setPadding(0, Dp.dpi(this@OnboardingActivity, 10f), 0, 0) })
        column.addView(Ui.textView(
            this,
            I18n.t("Займёт около минуты. Всё можно поменять позже.", "Takes about a minute. Everything can change later."),
            Type.CAPTION, 500, Colors.alpha(theme.textSecondary, 0.7f)
        ).apply { setPadding(0, Dp.dpi(this@OnboardingActivity, 14f), 0, 0) })
        return column
    }

    private fun enableStep(): View {
        val column = Ui.column(this)
        column.addView(HeroArt(this, theme, HeroArt.Kind.SWITCH).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this@OnboardingActivity, 168f))
        })
        column.addView(Ui.textView(this, I18n.t("Сначала включим", "First, switch it on"), Type.DISPLAY - 4f, 700, theme.text, tracking = -0.032f).apply {
            setPadding(0, Dp.dpi(this@OnboardingActivity, 8f), 0, 0)
        })
        column.addView(Ui.textView(
            this,
            I18n.t(
                "Android показывает клавиатуры из системного списка. Откройте настройки, найдите Limbo и включите его — вернитесь сюда, и мы продолжим.",
                "Android only shows keyboards from a system list. Open the settings, turn Limbo on, then come back here."
            ),
            Type.CALLOUT, 450, Colors.alpha(theme.textSecondary, 0.95f), lineSpacing = 1.36f
        ).apply { setPadding(0, Dp.dpi(this@OnboardingActivity, 10f), 0, 0) })
        column.addView(statusPill(isEnabled(), I18n.t("Клавиатура включена", "Keyboard is enabled"), I18n.t("Ждём включения", "Waiting for you")))
        return column
    }

    private fun selectStep(): View {
        val column = Ui.column(this)
        column.addView(HeroArt(this, theme, HeroArt.Kind.GLOBE).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this@OnboardingActivity, 168f))
        })
        column.addView(Ui.textView(this, I18n.t("Теперь выберем", "Now pick it"), Type.DISPLAY - 4f, 700, theme.text, tracking = -0.032f).apply {
            setPadding(0, Dp.dpi(this@OnboardingActivity, 8f), 0, 0)
        })
        column.addView(Ui.textView(
            this,
            I18n.t(
                "В списке клавиатур выберите Limbo. Это делается один раз — дальше он появляется сам.",
                "Pick Limbo in the keyboard list. It only takes once — after that it appears on its own."
            ),
            Type.CALLOUT, 450, Colors.alpha(theme.textSecondary, 0.95f), lineSpacing = 1.36f
        ).apply { setPadding(0, Dp.dpi(this@OnboardingActivity, 10f), 0, 0) })
        column.addView(statusPill(isSelected(), I18n.t("Limbo выбрана", "Limbo is selected"), I18n.t("Пока выбрана другая", "Another one is selected")))
        return column
    }

    private fun languagesStep(): View {
        val column = Ui.column(this)
        column.addView(Ui.textView(this, I18n.t("На чём вы печатаете?", "Which languages do you type in?"), Type.DISPLAY - 6f, 700, theme.text, tracking = -0.032f, lineSpacing = 1.1f))
        column.addView(Ui.textView(
            this,
            I18n.t("Можно выбрать несколько — переключение кнопкой глобуса.", "Choose as many as you need; switch with the globe key."),
            Type.CALLOUT, 450, Colors.alpha(theme.textSecondary, 0.9f), lineSpacing = 1.3f
        ).apply { setPadding(0, Dp.dpi(this@OnboardingActivity, 8f), 0, Dp.dpi(this@OnboardingActivity, 14f)) })

        val chips = ChipsRow(
            this, theme,
            Language.entries.map { it.code to it.label },
            settings.languageCodes.toSet(),
            multi = true
        ) { selected ->
            if (selected.isEmpty()) return@ChipsRow
            settings.languageCodes = selected.toList()
            if (!settings.languageCodes.contains(settings.activeLanguage)) settings.activeLanguage = settings.languageCodes.first()
        }
        column.addView(chips, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this, 40f)))
        column.addView(Ui.textView(
            this,
            I18n.t("Раскладка каждого языка остаётся привычной: QWERTY, ЙЦУКЕН, QWERTZ, AZERTY и другие.",
                "Every language keeps its familiar layout: QWERTY, QWERTZ, AZERTY and more."),
            Type.CAPTION, 450, Colors.alpha(theme.textSecondary, 0.8f), lineSpacing = 1.3f
        ).apply { setPadding(0, Dp.dpi(this@OnboardingActivity, 12f), 0, 0) })
        return column
    }

    private fun layoutStep(): View {
        val column = Ui.column(this)
        column.addView(Ui.textView(this, I18n.t("Как вам удобнее?", "What feels right?"), Type.DISPLAY - 6f, 700, theme.text, tracking = -0.032f))
        column.addView(Ui.textView(
            this,
            I18n.t("Размер клавиш и то, насколько прощает промахи. Всё меняется сразу — потрогайте клавиатуру ниже.",
                "Key size and how generously a near miss counts. Try the keyboard below — it is live."),
            Type.CALLOUT, 450, Colors.alpha(theme.textSecondary, 0.9f), lineSpacing = 1.32f
        ).apply { setPadding(0, Dp.dpi(this@OnboardingActivity, 8f), 0, Dp.dpi(this@OnboardingActivity, 14f)) })

        val frame = FrameLayout(this).apply {
            background = GlassDrawable(this@OnboardingActivity, theme, 24f)
            setPadding(Dp.dpi(this@OnboardingActivity, 3f), Dp.dpi(this@OnboardingActivity, 10f), Dp.dpi(this@OnboardingActivity, 3f), Dp.dpi(this@OnboardingActivity, 6f))
        }
        val board = KeyboardView(this).apply {
            this.theme = theme
            settings = this@OnboardingActivity.settings
            onEvent = { event ->
                if (event is app.limbo.keyboard.keyboard.KeyboardEvent.HeightChanged) frame.requestLayout()
            }
        }
        frame.addView(board, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        fun refreshBoard() {
            board.invalidateBoardSafely()
            frame.requestLayout()
        }

        val heights = listOf(I18n.t("Компактно", "Compact"), I18n.t("Обычно", "Balanced"), I18n.t("Просторно", "Roomy"))
        column.addView(Segmented(this, theme, heights, settings.heightScale) {
            settings.heightScale = it
            refreshBoard()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this, 42f)))

        column.addView(Slider(this, theme, I18n.t("Прощающая зона", "Forgiving touch area"), 0f, 1f, settings.touchSlop, 0.05f,
            { "${(it * 100).toInt()}%" }) { settings.touchSlop = it })

        val numbersRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        numbersRow.addView(Ui.textView(this, I18n.t("Ряд цифр сверху", "Number row on top"), Type.BODY - 1f, 550, theme.text),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val toggle = Toggle(this, theme, settings.numberRow) {
            settings.numberRow = it
            refreshBoard()
        }
        numbersRow.addView(toggle)
        column.addView(numbersRow)
        column.addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Dp.dpi(this@OnboardingActivity, 12f)
        })
        return column
    }

    private fun themeStep(): View {
        val column = Ui.column(this)
        column.addView(Ui.textView(this, I18n.t("Выберите настроение", "Pick a mood"), Type.DISPLAY - 6f, 700, theme.text, tracking = -0.032f))
        column.addView(Ui.textView(
            this,
            I18n.t("Начните с готового набора — потом можно изменить каждый цвет и радиус.",
                "Start from a preset — every colour and radius is editable later."),
            Type.CALLOUT, 450, Colors.alpha(theme.textSecondary, 0.9f), lineSpacing = 1.32f
        ).apply { setPadding(0, Dp.dpi(this@OnboardingActivity, 8f), 0, Dp.dpi(this@OnboardingActivity, 12f)) })

        val picks = listOf("frost", "midnight", "sunset", "mint", "neon-dusk", "porcelain").mapNotNull { id ->
            Themes.presets.firstOrNull { it.id == id }
        }
        column.addView(themeGrid(this, theme, picks, theme.id, columns = 3) { picked ->
            settings.theme = picked
        })

        val fine = SoftButton(this, theme, I18n.t("Настроить вручную", "Fine-tune it"), SoftButton.Kind.GLASS, Glyph.STAR).apply {
            compact = true
            setOnClickListener { startActivity(Intent(this@OnboardingActivity, ThemeStudioActivity::class.java)) }
        }
        column.addView(fine, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this, 42f)).apply {
            topMargin = Dp.dpi(this@OnboardingActivity, 6f)
        })
        return column
    }

    private fun clipboardStep(): View {
        val column = Ui.column(this)
        column.addView(HeroArt(this, theme, HeroArt.Kind.CARDS).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this@OnboardingActivity, 168f))
        })
        column.addView(Ui.textView(this, I18n.t("Буфер, который помнит", "A clipboard that remembers"), Type.DISPLAY - 6f, 700, theme.text, tracking = -0.032f).apply {
            setPadding(0, Dp.dpi(this@OnboardingActivity, 8f), 0, 0)
        })
        column.addView(Ui.textView(
            this,
            I18n.t(
                "Скопировали адрес, ссылку или номер — они остаются в истории клавиатуры. Закрепите нужное, и оно всегда под рукой.",
                "Copied an address, a link or a number? They stay in the keyboard's history. Pin the ones you reuse."
            ),
            Type.CALLOUT, 450, Colors.alpha(theme.textSecondary, 0.9f), lineSpacing = 1.34f
        ).apply { setPadding(0, Dp.dpi(this@OnboardingActivity, 8f), 0, Dp.dpi(this@OnboardingActivity, 12f)) })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(Ui.textView(this, I18n.t("Запоминать копии", "Remember copies"), Type.BODY - 1f, 550, theme.text),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Toggle(this, theme, settings.clipboardEnabled) { settings.clipboardEnabled = it })
        column.addView(row)

        column.addView(Slider(this, theme, I18n.t("Сколько хранить", "How many to keep"), 10f, 200f,
            settings.clipboardHistorySize.toFloat(), 5f, { "${it.toInt()}" }) {
            settings.clipboardHistorySize = it.toInt()
        })
        column.addView(Ui.textView(
            this,
            I18n.t("История хранится только на этом устройстве.", "History never leaves this device."),
            Type.CAPTION, 450, Colors.alpha(theme.textSecondary, 0.7f)
        ))
        return column
    }

    private fun testStep(): View {
        val column = Ui.column(this)
        targetPhrase = I18n.t("привет, limbo", "hello, limbo")
        column.addView(Ui.textView(this, I18n.t("Проверим на деле", "Let us try it"), Type.DISPLAY - 6f, 700, theme.text, tracking = -0.032f))
        column.addView(Ui.textView(
            this,
            I18n.t("Напечатайте «$targetPhrase» — и обратите внимание, что попадать точно в клавишу не обязательно.",
                "Type \u201C$targetPhrase\u201D — and notice you never have to hit a key exactly."),
            Type.CALLOUT, 450, Colors.alpha(theme.textSecondary, 0.9f), lineSpacing = 1.32f
        ).apply { setPadding(0, Dp.dpi(this@OnboardingActivity, 8f), 0, Dp.dpi(this@OnboardingActivity, 10f)) })

        val demo = DemoKeyboard(this, theme, settings)
        testDemo = demo
        val frame = FrameLayout(this).apply {
            background = GlassDrawable(this@OnboardingActivity, theme, 24f)
            setPadding(Dp.dpi(this@OnboardingActivity, 3f), Dp.dpi(this@OnboardingActivity, 3f), Dp.dpi(this@OnboardingActivity, 3f), Dp.dpi(this@OnboardingActivity, 6f))
        }
        val feedback = Ui.textView(this, "", Type.CALLOUT, 600, theme.textSecondary)
        feedback.setPadding(Dp.dpi(this@OnboardingActivity, 6f), Dp.dpi(this@OnboardingActivity, 8f), 0, 0)
        demo.onTextChanged = { text ->
            val typed = text.trim()
            val target = targetPhrase
            val matched = typed.take(min(typed.length, target.length))
            var correct = 0
            for (i in matched.indices) if (matched[i].equals(target[i], ignoreCase = true)) correct++
            feedback.text = when {
                typed.isEmpty() -> I18n.t("Поле выше — тоже живое", "The field above is live too")
                typed.length < target.length -> I18n.t("Совпадение: $correct из ${typed.length}", "Matching: $correct of ${typed.length}")
                else -> I18n.t("Отлично. Можно печатать", "Well done. You are ready")
            }
            feedback.setTextColor(
                if (typed.length >= target.length) Colors.parse("#48D597", theme.text) else theme.textSecondary
            )
        }
        frame.addView(demo, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        column.addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        column.addView(feedback)
        return column
    }

    private fun statusPill(done: Boolean, doneText: String, pendingText: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GlassDrawable(this@OnboardingActivity, theme, 16f)
            setPadding(Dp.dpi(this@OnboardingActivity, 14f), Dp.dpi(this@OnboardingActivity, 12f), Dp.dpi(this@OnboardingActivity, 14f), Dp.dpi(this@OnboardingActivity, 12f))
        }
        val icon = GlyphViewSmall(this, theme, if (done) Glyph.CHECK else Glyph.DOT, if (done) Colors.parse("#48D597", theme.text) else Colors.parse("#F0B65A", theme.text))
        card.addView(icon, LinearLayout.LayoutParams(Dp.dpi(this, 18f), Dp.dpi(this, 18f)).apply {
            rightMargin = Dp.dpi(this@OnboardingActivity, 10f)
        })
        card.addView(Ui.textView(this, if (done) doneText else pendingText, Type.CALLOUT, 600, theme.text),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrapper.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Dp.dpi(this@OnboardingActivity, 14f)
        })
        return wrapper
    }

    override fun applyTheme(theme: Theme) {
        wallpaper.theme = theme
    }
}

/** Progress indicator: a slim capsule that glides between steps. */
private class StepDots(context: Context, var theme: Theme, private val total: Int) : SpringyView(context) {

    private val motion = spring(420f, 28f, 0f)
    var selected: Int = 0
        set(value) {
            field = value
            motion.setTarget(value.toFloat())
            wake()
        }

    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(Dp.dpi(context, total * 14f + 10f), Dp.dpi(context, 22f))
    }

    override fun drawContent(canvas: Canvas) {
        val dot = Dp.dp(context, 6f)
        val gap = Dp.dp(context, 8f)
        val startX = Dp.dp(context, 4f)
        val cy = height / 2f
        for (i in 0 until total) {
            val cx = startX + i * (dot + gap) + dot / 2f
            val distance = abs(i - motion.value)
            val alpha = (1f - distance.coerceAtMost(1f) * 0.7f).coerceIn(0.25f, 1f)
            paint.color = if (i == selected) theme.accent else Colors.alpha(theme.text, 0.24f * alpha)
            canvas.drawCircle(cx, cy, dot / 2f, paint)
        }
        val activeX = startX + motion.value * (dot + gap)
        rect.set(activeX - Dp.dp(context, 1f), cy - dot / 2f, activeX + dot * 2.2f, cy + dot / 2f)
        paint.color = Colors.alpha(theme.accent, 0.28f)
        canvas.drawRoundRect(rect, dot / 2f, dot / 2f, paint)
    }
}

/**
 * A single piece of illustration per step, drawn rather than shipped as an asset so it always
 * matches the current theme.
 */
private class HeroArt(context: Context, var theme: Theme, private val kind: Kind) : SpringyView(context) {

    enum class Kind { KEYS, SWITCH, GLOBE, CARDS }

    private val entrance = spring(260f, 20f, 0f)
    private var phase = 0f
    private var ticking = false
    private val tick: (Float) -> Unit = { dt ->
        phase += dt
        invalidate()
    }

    init {
        entrance.setTarget(1f)
        wake()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!ticking) {
            ticking = true
            Clock.subscribe(tick)
        }
    }

    override fun onDetachedFromWindow() {
        if (ticking) {
            ticking = false
            Clock.unsubscribe(tick)
        }
        super.onDetachedFromWindow()
    }

    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    override fun drawContent(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val progress = entrance.value.coerceIn(0f, 1f)
        val bob = sin(phase * 1.4f) * Dp.dp(context, 3.5f)

        val centerX = w / 2f
        val centerY = h / 2f + (1f - progress) * Dp.dp(context, 18f)

        when (kind) {
            Kind.KEYS -> {
                val keySize = min(w * 0.19f, Dp.dp(context, 58f))
                val gap = keySize * 0.16f
                val rows = listOf(3, 4, 3)
                for (row in rows.indices) {
                    val count = rows[row]
                    val rowWidth = count * keySize + (count - 1) * gap
                    for (i in 0 until count) {
                        val offset = (row - 1) * (keySize + gap)
                        val left = centerX - rowWidth / 2f + i * (keySize + gap)
                        val top = centerY - keySize * 1.5f + offset + bob * (if (row == 1) 1f else 0.5f)
                        rect.set(left, top, left + keySize, top + keySize)
                        val press = if (row == 1 && i == 1) (sin(phase * 2.2f) * 0.5f + 0.5f) * 0.12f else 0f
                        Glass.key(canvas, rect, keySize * 0.28f, theme, context, press, isModifier = row == 2, isAccent = row == 1 && i == 1)
                        rect.inset(keySize * 0.3f, keySize * 0.3f)
                        paint.color = Colors.alpha(if (row == 1 && i == 1) theme.accentText else theme.text, 0.35f * progress)
                        canvas.drawRoundRect(rect, rect.width() / 2f, rect.height() / 2f, paint)
                    }
                }
            }

            Kind.SWITCH -> {
                val w2 = min(w * 0.6f, Dp.dp(context, 200f))
                val h2 = w2 * 0.28f
                rect.set(centerX - w2 / 2f, centerY - h2 / 2f + bob, centerX + w2 / 2f, centerY + h2 / 2f + bob)
                paint.color = Colors.alpha(theme.accent, 0.95f * progress)
                canvas.drawRoundRect(rect, h2 / 2f, h2 / 2f, paint)
                val knob = h2 - Dp.dp(context, 8f)
                paint.color = Colors.alpha(Color.WHITE, progress)
                canvas.drawCircle(rect.right - h2 / 2f, rect.centerY(), knob / 2f, paint)
                stroke.color = Colors.alpha(theme.text, 0.2f * progress)
                stroke.strokeWidth = Dp.dp(context, 1.4f)
                rect.set(centerX - w2 / 2f - Dp.dp(context, 18f), centerY + h2 * 0.9f + bob, centerX + w2 / 2f + Dp.dp(context, 18f), centerY + h2 * 1.35f + bob)
                canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, stroke)
            }

            Kind.GLOBE -> {
                val radius = min(w * 0.2f, Dp.dp(context, 62f))
                val cx = centerX
                val cy = centerY + bob
                paint.color = Colors.alpha(theme.accent, 0.14f * progress)
                canvas.drawCircle(cx, cy, radius * 1.55f, paint)
                stroke.color = Colors.alpha(theme.text, 0.85f * progress)
                stroke.strokeWidth = Dp.dp(context, 1.8f)
                canvas.drawCircle(cx, cy, radius, stroke)
                canvas.drawOval(cx - radius * 0.45f, cy - radius, cx + radius * 0.45f, cy + radius, stroke)
                canvas.drawLine(cx - radius, cy, cx + radius, cy, stroke)
                canvas.drawArc(cx - radius, cy - radius * 0.62f, cx + radius, cy + radius * 0.62f, 200f, 140f, false, stroke)
                path.reset()
                path.moveTo(cx + radius * 1.05f, cy + radius * 0.55f)
                path.lineTo(cx + radius * 1.55f, cy + radius * 0.05f)
                path.lineTo(cx + radius * 1.05f, cy - radius * 0.45f)
                stroke.color = Colors.alpha(theme.accent, progress)
                canvas.drawPath(path, stroke)
            }

            Kind.CARDS -> {
                val cardWidth = min(w * 0.62f, Dp.dp(context, 230f))
                val cardHeight = Dp.dp(context, 58f)
                for (i in 0..2) {
                    val shift = (i - 1) * cardHeight * 0.42f
                    val alpha = 1f - abs(i - 1) * 0.35f
                    rect.set(
                        centerX - cardWidth / 2f + (i - 1) * Dp.dp(context, 8f),
                        centerY - cardHeight / 2f + shift + bob,
                        centerX + cardWidth / 2f + (i - 1) * Dp.dp(context, 8f),
                        centerY + cardHeight / 2f + shift + bob
                    )
                    Glass.panel(canvas, rect, Dp.dp(context, 18f), theme, context,
                        Colors.alpha(theme.keyTop, 0.9f * alpha * progress), Colors.alpha(theme.keyBottom, 0.8f * alpha * progress), false, 0.03f)
                    paint.color = Colors.alpha(theme.text, 0.28f * alpha * progress)
                    canvas.drawRoundRect(
                        rect.left + Dp.dp(context, 16f), rect.centerY() - Dp.dp(context, 3f),
                        rect.left + cardWidth * (0.35f + i * 0.16f), rect.centerY() + Dp.dp(context, 3f),
                        Dp.dp(context, 3f), Dp.dp(context, 3f), paint
                    )
                    if (i == 1) {
                        paint.color = Colors.alpha(theme.accent, progress)
                        canvas.drawCircle(rect.right - Dp.dp(context, 22f), rect.centerY(), Dp.dp(context, 7f), paint)
                    }
                }
            }
        }
    }
}

private class GlyphViewSmall(context: Context, theme: Theme, glyph: Glyph, color: Int) : SpringyView(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        this.tag = glyph
        this.contentDescription = glyph.name
        this.setBackgroundColor(Color.TRANSPARENT)
        this.paint.color = color
    }

    override fun drawContent(canvas: Canvas) {
        val strokeWidth = Dp.dp(context, 1.8f)
        paint.strokeWidth = strokeWidth
        IconPainter.draw(canvas, tag as Glyph, width / 2f, height / 2f, min(width, height) * 0.8f, paint.color, paint, fill)
    }
}
