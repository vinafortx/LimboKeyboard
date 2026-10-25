package app.limbo.keyboard.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.limbo.keyboard.core.Colors
import app.limbo.keyboard.core.Dp
import app.limbo.keyboard.core.I18n
import app.limbo.keyboard.core.Type
import app.limbo.keyboard.keyboard.KeyboardView
import app.limbo.keyboard.layout.Glyph
import app.limbo.keyboard.theme.BarStyle
import app.limbo.keyboard.theme.KeyStyle
import app.limbo.keyboard.theme.Theme
import app.limbo.keyboard.theme.Themes
import app.limbo.keyboard.ui.kit.ChipsRow
import app.limbo.keyboard.ui.kit.ColorRow
import app.limbo.keyboard.ui.kit.GlassDrawable
import app.limbo.keyboard.ui.kit.Haptics
import app.limbo.keyboard.ui.kit.Segmented
import app.limbo.keyboard.ui.kit.Slider
import app.limbo.keyboard.ui.kit.SoftButton
import app.limbo.keyboard.ui.kit.Ui
import app.limbo.keyboard.ui.kit.themeGrid

/**
 * The studio: every number and every colour of the keyboard is editable, presets are only a
 * starting point. Changes are visible instantly on the live keyboard at the top.
 */
class ThemeStudioActivity : BaseActivity() {

    private lateinit var preview: KeyboardView
    private lateinit var previewFrame: FrameLayout
    private lateinit var presetGridHost: LinearLayout
    private var working: Theme = Themes.preset(Theme.DEFAULT_PRESET)
    private val handler = Handler(Looper.getMainLooper())
    private var applying = false

    override fun onBuild() {
        working = theme
        buildHeader()
        buildPreview()
        buildPresetCard()
        buildStyleCard()
        buildGeometryCard()
        buildColorCards()
        buildActionCard()
        Ui.space(this, 24f).also { content.addView(it) }
    }

    private fun buildHeader() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Dp.dpi(this@ThemeStudioActivity, 8f), 0, 0)
        }
        val back = SoftButton(this, theme, "", SoftButton.Kind.GLASS, Glyph.ARROW_LEFT).apply {
            compact = true
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(Dp.dpi(this, 46f), Dp.dpi(this, 42f)))
        row.addView(Ui.textView(
            this, I18n.t("Оформление", "Appearance"), Type.TITLE, 700, theme.text, tracking = -0.03f
        ).apply { setPadding(Dp.dpi(this@ThemeStudioActivity, 12f), 0, 0, 0) })
        content.addView(row)
        content.addView(Ui.textView(
            this,
            I18n.t("Любой оттенок, радиус и плотность. Начните с готового или соберите своё.",
                "Any hue, radius and density. Start from a preset or build your own."),
            Type.CALLOUT, 450, Colors.alpha(theme.textSecondary, 0.92f), lineSpacing = 1.32f
        ).apply { setPadding(0, Dp.dpi(this@ThemeStudioActivity, 6f), 0, 0) })
    }

    private fun buildPreview() {
        Ui.space(this, 14f).also { content.addView(it) }
        previewFrame = FrameLayout(this).apply {
            background = GlassDrawable(this@ThemeStudioActivity, working, 26f)
            setPadding(Dp.dpi(this@ThemeStudioActivity, 4f), Dp.dpi(this@ThemeStudioActivity, 12f), Dp.dpi(this@ThemeStudioActivity, 4f), Dp.dpi(this@ThemeStudioActivity, 8f))
        }
        preview = KeyboardView(this).apply {
            theme = working
            settings = this@ThemeStudioActivity.settings
            onEvent = { }
        }
        previewFrame.addView(preview, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        content.addView(previewFrame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun buildPresetCard() {
        Ui.space(this, 14f).also { content.addView(it) }
        val card = Ui.card(this, theme, 16f, 24f)
        card.addView(Ui.section(this, I18n.t("Готовые наборы", "Presets"), theme))
        presetGridHost = Ui.column(this).apply { setPadding(0, Dp.dpi(this@ThemeStudioActivity, 10f), 0, 0) }
        card.addView(presetGridHost)
        rebuildPresets()
        content.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun rebuildPresets() {
        presetGridHost.removeAllViews()
        val items = Themes.presets + limbo.settings.themes.customThemes()
        presetGridHost.addView(themeGrid(this, theme, items, working.id, columns = 3) { picked ->
            Haptics.tap(presetGridHost, 1)
            working = picked
            persistNow()
            preview.theme = picked
            previewFrame.background = GlassDrawable(this, picked, 26f)
            rebuildPresets()
        })
    }

    private fun buildStyleCard() {
        Ui.space(this, 14f).also { content.addView(it) }
        val card = Ui.card(this, theme, 16f, 24f)
        card.addView(Ui.section(this, I18n.t("Характер", "Character"), theme))

        // Six styles no longer fit a segmented control without squeezing the labels, so the
        // picker scrolls instead.
        val styleChips = ChipsRow(
            this, theme,
            KeyStyle.entries.map { it.name to if (I18n.isRussian) it.labelRu else it.labelEn },
            setOf(working.keyStyle.name),
            multi = false
        ) { selected ->
            val style = KeyStyle.entries.firstOrNull { it.name == selected.firstOrNull() } ?: return@ChipsRow
            edit { it.copy(keyStyle = style) }
        }
        card.addView(styleChips, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this, 40f)).apply {
            topMargin = Dp.dpi(this@ThemeStudioActivity, 12f)
        })

        val barLabels = listOf(I18n.t("Парящая", "Floating"), I18n.t("Встроенная", "Attached"), I18n.t("Скрыть", "Hidden"))
        val bar = Segmented(this, theme, barLabels, working.barStyle.ordinal) { index ->
            edit { it.copy(barStyle = BarStyle.entries[index]) }
        }
        card.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this, 44f)).apply {
            topMargin = Dp.dpi(this@ThemeStudioActivity, 10f)
        })

        card.addView(sliderRow(I18n.t("Мутность", "Frost"), 0f, 1f, working.frost, 0.05f, { "${(it * 100).toInt()}%" }) {
            edit { t -> t.copy(frost = it) }
        })
        card.addView(sliderRow(I18n.t("Свет по кромке", "Edge light"), 0f, 1f, working.sheen, 0.05f, { "${(it * 100).toInt()}%" }) {
            edit { t -> t.copy(sheen = it) }
        })
        card.addView(sliderRow(I18n.t("Тени", "Shadow"), 0f, 1f, working.shadow, 0.05f, { "${(it * 100).toInt()}%" }) {
            edit { t -> t.copy(shadow = it) }
        })
        content.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun buildGeometryCard() {
        Ui.space(this, 14f).also { content.addView(it) }
        val card = Ui.card(this, theme, 16f, 24f)
        card.addView(Ui.section(this, I18n.t("Геометрия", "Geometry"), theme))

        card.addView(sliderRow(I18n.t("Скругление панели", "Panel radius"), 8f, 44f, working.corner, 1f, { "${it.toInt()}" }) {
            edit { t -> t.copy(corner = it) }
        })
        card.addView(sliderRow(I18n.t("Скругление клавиш", "Key radius"), 2f, 26f, working.keyCorner, 1f, { "${it.toInt()}" }) {
            edit { t -> t.copy(keyCorner = it) }
        })
        card.addView(sliderRow(I18n.t("Зазор между клавишами", "Key gap"), 2f, 14f, working.gap, 0.5f, { "${it.toInt()}" }) {
            edit { t -> t.copy(gap = it, rowGap = it) }
        })
        card.addView(sliderRow(I18n.t("Высота клавиш", "Key height"), 38f, 72f, working.keyHeight, 1f, { "${it.toInt()}" }) {
            edit { t -> t.copy(keyHeight = it) }
        })
        card.addView(sliderRow(I18n.t("Размер шрифта", "Type size"), 0.8f, 1.25f, working.fontScale, 0.01f, { "${(it * 100).toInt()}%" }) {
            edit { t -> t.copy(fontScale = it) }
        })
        card.addView(sliderRow(I18n.t("Разрядка", "Tracking"), -0.02f, 0.08f, working.tracking, 0.005f, { String.format("%.3f", it) }) {
            edit { t -> t.copy(tracking = it) }
        })
        card.addView(sliderRow(I18n.t("Обводка", "Outline"), 0f, 1f, working.keyStroke, 0.05f, { "${(it * 100).toInt()}%" }) {
            edit { t -> t.copy(keyStroke = it) }
        })
        content.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun buildColorCards() {
        Ui.space(this, 14f).also { content.addView(it) }
        val card = Ui.card(this, theme, 16f, 24f)
        card.addView(Ui.section(this, I18n.t("Цвет", "Colour"), theme))
        card.addView(ColorRow(this, theme, I18n.t("Верх фона", "Background top"), working.wallTop) { color ->
            edit { it.copy(wallTop = color, panelTop = Colors.mix(color, android.graphics.Color.BLACK, if (it.dark) 0.5f else -0.5f)) }
            refreshPreviewChrome()
        })
        card.addView(ColorRow(this, theme, I18n.t("Низ фона", "Background bottom"), working.wallBottom) { color ->
            edit { it.copy(wallBottom = color, panelBottom = Colors.mix(color, android.graphics.Color.BLACK, if (it.dark) 0.5f else -0.5f)) }
            refreshPreviewChrome()
        })
        card.addView(ColorRow(this, theme, I18n.t("Акцент", "Accent"), working.accent) { color ->
            edit { it.copy(accent = color, accentText = Colors.on(color), wallGlow = Colors.alpha(color, 0.45f)) }
        })

        val dayNight = Segmented(this, theme, listOf(I18n.t("Тёмная", "Dark"), I18n.t("Светлая", "Light")), if (working.dark) 0 else 1) { index ->
            working = app.limbo.keyboard.theme.ThemeFactory.recolor(working, index == 0)
            persistNow()
            preview.theme = working
            previewFrame.background = GlassDrawable(this, working, 26f)
        }
        card.addView(dayNight, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Dp.dpi(this, 44f)).apply {
            topMargin = Dp.dpi(this@ThemeStudioActivity, 12f)
        })
        content.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun buildActionCard() {
        Ui.space(this, 14f).also { content.addView(it) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val save = SoftButton(this, theme, I18n.t("Сохранить", "Save"), SoftButton.Kind.PRIMARY, Glyph.CHECK).apply {
            setOnClickListener { saveTheme() }
        }
        val reset = SoftButton(this, theme, I18n.t("Сброс", "Reset"), SoftButton.Kind.GLASS).apply {
            setOnClickListener {
                working = Themes.preset(Theme.DEFAULT_PRESET)
                persistNow()
                preview.theme = working
                previewFrame.background = GlassDrawable(this@ThemeStudioActivity, working, 26f)
                rebuildPresets()
            }
        }
        row.addView(save, LinearLayout.LayoutParams(0, Dp.dpi(this, 52f), 1.4f).apply { rightMargin = Dp.dpi(this@ThemeStudioActivity, 8f) })
        row.addView(reset, LinearLayout.LayoutParams(0, Dp.dpi(this, 52f), 1f))
        content.addView(row)

        content.addView(Ui.textView(
            this,
            I18n.t("Сохранённые темы остаются в разделе «Готовые наборы» и работают так же, как встроенные.",
                "Saved themes live next to the built-in presets and behave the same way."),
            Type.CAPTION, 450, Colors.alpha(theme.textSecondary, 0.8f), lineSpacing = 1.3f
        ).apply { setPadding(0, Dp.dpi(this@ThemeStudioActivity, 10f), 0, 0) })
    }

    private fun saveTheme() {
        val count = limbo.settings.themes.customThemes().size + 1
        val name = I18n.t("Моя тема $count", "My theme $count")
        val saved = working.copy(id = "custom-${System.currentTimeMillis()}", name = name)
        working = saved
        limbo.settings.themes.save(saved)
        settings.theme = saved
        Haptics.native(this, 2)
        rebuildPresets()
    }

    private fun sliderRow(
        label: String,
        min: Float,
        max: Float,
        value: Float,
        step: Float,
        format: (Float) -> String,
        onChange: (Float) -> Unit
    ): Slider = Slider(this, theme, label, min, max, value, step, format) { onChange(it) }

    private fun edit(block: (Theme) -> Theme) {
        working = block(working)
        preview.theme = working
        schedulePersist()
    }

    private fun refreshPreviewChrome() {
        previewFrame.background = GlassDrawable(this, working, 26f)
        schedulePersist()
    }

    private val persistRunnable = Runnable { persistNow() }

    private fun schedulePersist() {
        handler.removeCallbacks(persistRunnable)
        handler.postDelayed(persistRunnable, 220)
    }

    private fun persistNow() {
        handler.removeCallbacks(persistRunnable)
        if (applying) return
        applying = true
        settings.theme = working
        applying = false
    }

    override fun applyTheme(theme: Theme) {
        super.applyTheme(theme)
        if (!this::preview.isInitialized) return
        preview.theme = theme
    }

    override fun onPause() {
        super.onPause()
        persistNow()
    }

    override fun onDestroy() {
        handler.removeCallbacks(persistRunnable)
        super.onDestroy()
    }
}
