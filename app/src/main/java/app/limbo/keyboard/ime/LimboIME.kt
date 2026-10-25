package app.limbo.keyboard.ime

import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import app.limbo.keyboard.LimboApp
import app.limbo.keyboard.core.Clock
import app.limbo.keyboard.core.Colors
import app.limbo.keyboard.core.Dp
import app.limbo.keyboard.core.I18n
import app.limbo.keyboard.core.Settings
import app.limbo.keyboard.core.Spring
import app.limbo.keyboard.keyboard.KeyboardEvent
import app.limbo.keyboard.keyboard.KeyboardView
import app.limbo.keyboard.layout.Action
import app.limbo.keyboard.layout.Family
import app.limbo.keyboard.layout.Glyph
import app.limbo.keyboard.layout.Language
import app.limbo.keyboard.layout.Layer
import app.limbo.keyboard.theme.KeyStyle
import app.limbo.keyboard.theme.Themes
import app.limbo.keyboard.ui.kit.KeyboardSurfaceDrawable

/**
 * The input method. It owns the keyboard view, the strip above it, the panels that replace it and
 * the small glass sheets used for quick choices while typing.
 */
class LimboIME : InputMethodService() {

    private val limbo: LimboApp get() = application as LimboApp
    private val settings: Settings get() = limbo.settings

    private var root: ImeFrame? = null
    private var keyboard: KeyboardView? = null
    private var strip: SuggestionStrip? = null
    private var panelHost: FrameLayout? = null
    private var clipboardPanel: ClipboardPanel? = null
    private var emojiPanel: EmojiPanel? = null
    private var sheet: QuickSheet? = null

    private var languageIndex = 0
    private var stripEnabled = true
    private var openPanel: View? = null
    private var currentPrefix: String = ""
    private val panelMotion = Spring(360f, 27f)
    private var panelTicking = false
    private val panelTick: (Float) -> Unit = { dt ->
        val host = panelHost
        if (host != null) {
            panelMotion.advance(dt)
            host.translationY = panelMotion.value
            if (!panelMotion.isActive) {
                panelTicking = false
                Clock.unsubscribe(panelTick)
                if (panelMotion.value >= host.height * 0.9f) {
                    host.removeAllViews()
                    host.visibility = View.GONE
                    openPanel = null
                }
            }
        } else {
            panelTicking = false
            Clock.unsubscribe(panelTick)
        }
    }

    override fun onCreate() {
        super.onCreate()
        languageIndex = settings.languageCodes.indexOf(settings.activeLanguage).coerceAtLeast(0)
    }

    override fun onCreateInputView(): View {
        val theme = settings.theme
        val context = this

        val frame = ImeFrame(context)
        frame.clipChildren = false
        frame.background = KeyboardSurfaceDrawable(context, theme)
        root = frame

        val suggestionStrip = SuggestionStrip(context)
        suggestionStrip.theme = theme
        strip = suggestionStrip
        frame.addView(suggestionStrip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Dp.dpi(context, 46f)))

        val board = KeyboardView(context)
        board.theme = theme
        board.settings = settings
        board.onEvent = ::handleEvent
        keyboard = board
        frame.addView(board, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val host = FrameLayout(context)
        host.visibility = View.GONE
        host.clipChildren = false
        panelHost = host
        frame.addView(host, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        frame.onLayoutReady = {
            board.setLanguage(currentLanguage(), currentFamily())
            frame.requestLayout()
        }

        suggestionStrip.onPick = { word -> commitSuggestion(word) }
        suggestionStrip.onClipboardPick = { text -> pasteText(text) }
        suggestionStrip.onOpenClipboard = { showClipboard() }

        window?.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        limbo.clipboard.addListener(clipListener)
        return frame
    }

    private val clipListener: (List<ClipboardStore.Entry>) -> Unit = { entries ->
        clipboardPanel?.bind(entries)
        refreshSuggestions()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val inputType = info?.inputType ?: InputType.TYPE_CLASS_TEXT
        stripEnabled = settings.suggestions && settings.theme.barStyle != app.limbo.keyboard.theme.BarStyle.HIDDEN
        root?.let { frame ->
            strip?.visibility = if (stripEnabled) View.VISIBLE else View.GONE
            keyboard?.topBandPx = if (stripEnabled) Dp.dpi(this, 46f) else 0
            frame.requestLayout()
        }
        keyboard?.let { board ->
            board.theme = settings.theme
            board.settings = settings
            board.setLanguage(currentLanguage(), currentFamily())
            val layer = when {
                inputType and 0x0f == InputType.TYPE_CLASS_NUMBER -> Layer.NUMBERS
                inputType and 0x0f == InputType.TYPE_CLASS_PHONE -> Layer.NUMBERS
                else -> Layer.LETTERS
            }
            board.setLayer(layer)
        }
        limbo.clipboard.startWatching()
        refreshShift()
        refreshSuggestions()
        applyTheme()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        closeSheet()
        hidePanel(immediate = true)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        refreshShift()
        refreshSuggestions()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        keyboard?.invalidateBoardSafely()
        applyTheme()
        root?.requestLayout()
    }

    private fun applyTheme() {
        val theme = settings.theme
        keyboard?.theme = theme
        strip?.theme = theme
        clipboardPanel?.let { it.theme = theme; it.background = KeyboardSurfaceDrawable(this, theme) }
        emojiPanel?.let { it.theme = theme; it.background = KeyboardSurfaceDrawable(this, theme) }
        (root?.background as? KeyboardSurfaceDrawable)?.theme = theme
        root?.invalidate()
    }

    private fun currentLanguage(): Language {
        val languages = settings.languages.ifEmpty { listOf(Language.EN) }
        languageIndex = languageIndex.coerceIn(0, languages.lastIndex)
        return languages[languageIndex]
    }

    private fun currentFamily(): Family {
        val language = currentLanguage()
        return if (language.supportsFamilies) language.defaultFamily else language.defaultFamily
    }

    private fun nextLanguage(forward: Boolean) {
        val languages = settings.languages.ifEmpty { listOf(Language.EN) }
        if (languages.size <= 1) {
            showLanguagePicker()
            return
        }
        languageIndex = if (forward) (languageIndex + 1) % languages.size else (languageIndex - 1 + languages.size) % languages.size
        val language = languages[languageIndex]
        settings.activeLanguage = language.code
        keyboard?.setLanguage(language, currentFamily())
        keyboard?.resetShift()
        refreshShift()
        refreshSuggestions()
    }

    // ------------------------------------------------------------------ events

    private fun handleEvent(event: KeyboardEvent) {
        when (event) {
            is KeyboardEvent.Commit -> commitText(event.text, event.key != null)
            is KeyboardEvent.Delete -> delete(event.wholeWord)
            is KeyboardEvent.Cursor -> moveCursor(event.delta)
            is KeyboardEvent.ActionRequested -> handleAction(event.action)
            is KeyboardEvent.SwitchLanguage -> nextLanguage(event.forward)
            KeyboardEvent.LanguagePicker -> showLanguagePicker()
            KeyboardEvent.ClipboardPanel -> showClipboard()
            KeyboardEvent.EmojiPanel -> showEmoji()
            KeyboardEvent.SettingsPanel -> showQuickSettings()
            is KeyboardEvent.StateChanged -> refreshSuggestions()
            is KeyboardEvent.HeightChanged -> root?.requestLayout()
            is KeyboardEvent.Feedback -> {
                if (settings.sound) playClick()
                if (event.key.action == Action.SPACE) learnLastWord()
                refreshSuggestions()
            }
            KeyboardEvent.Dismiss -> requestHideSelf(0)
        }
    }

    private fun handleAction(action: Action) {
        val ic = currentInputConnection ?: return
        when (action) {
            Action.ENTER -> {
                val info = currentInputEditorInfo
                val actionId = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                if (actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    ic.performEditorAction(actionId)
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
            }
            Action.SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)
            else -> Unit
        }
    }

    private fun commitText(text: String, fromKeyboard: Boolean) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        settings.typedCharacters = settings.typedCharacters + text.length
        if (fromKeyboard) settings.keystrokes = settings.keystrokes + 1
        if (text.firstOrNull()?.isLetter() == true) {
            if (text.length == 1) currentPrefix += text.lowercase() else currentPrefix = text.lowercase()
        } else if (text != "'") {
            if (text == " ") learnLastWord()
            currentPrefix = ""
        }
        refreshSuggestions()
        refreshShift()
    }

    private fun commitSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        if (currentPrefix.isNotEmpty()) {
            ic.deleteSurroundingText(currentPrefix.length, 0)
        }
        ic.commitText("$word ", 1)
        currentPrefix = ""
        limbo.dictionary.learn(word)
        refreshSuggestions()
        refreshShift()
    }

    private fun pasteText(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        currentPrefix = ""
        refreshSuggestions()
    }

    private fun delete(wholeWord: Boolean) {
        val ic = currentInputConnection ?: return
        if (wholeWord) {
            val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
            val trimmed = before.trimEnd()
            val word = trimmed.takeLastWhile { it.isLetterOrDigit() || it == '\'' || it == '-' }
            val count = before.length - (trimmed.length - word.length)
            if (count > 0) ic.deleteSurroundingText(count, 0)
        } else {
            val selected = ic.getSelectedText(0)
            if (!selected.isNullOrEmpty()) {
                ic.commitText("", 1)
            } else {
                ic.deleteSurroundingText(1, 0)
            }
        }
        currentPrefix = currentPrefix.dropLast(1)
        refreshSuggestions()
        refreshShift()
    }

    private fun moveCursor(delta: Int) {
        val ic = currentInputConnection ?: return
        val keyCode = if (delta > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(delta)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    private fun learnLastWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        val word = before.trimEnd(' ').takeLastWhile { it.isLetter() || it == '\'' || it == '-' }
        if (word.length >= 2) limbo.dictionary.learn(word)
        currentPrefix = ""
    }

    private fun playClick() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
        audio.playSoundEffect(android.media.AudioManager.FX_KEY_CLICK, 0.35f)
    }

    // ------------------------------------------------------------------ suggestions

    private fun refreshSuggestions() {
        val stripView = strip ?: return
        if (!stripEnabled) return
        val ic = currentInputConnection
        val before = ic?.getTextBeforeCursor(48, 0)?.toString().orEmpty()
        val prefix = before.takeLastWhile { it.isLetter() || it == '\'' || it == '-' }
        currentPrefix = prefix
        if (settings.suggestions && prefix.length >= 1) {
            val words = limbo.dictionary.suggestions(prefix, 3)
            if (words.isNotEmpty()) {
                stripView.showSuggestions(words, prefix)
                return
            }
        } else if (prefix.isEmpty()) {
            stripView.clearChips()
        }
        if (settings.clipboardSuggestionsInBar) {
            val clips = limbo.clipboard.entries().filter { it.text.length > 2 }.take(2)
            if (clips.isNotEmpty()) stripView.showClipboard(clips) else stripView.clearChips()
        } else {
            stripView.clearChips()
        }
    }

    private fun refreshShift() {
        val board = keyboard ?: return
        val info = currentInputEditorInfo ?: return
        if (board.capsLock) return
        if (info.inputType and 0x0f != InputType.TYPE_CLASS_TEXT) {
            board.setShifted(false)
            return
        }
        val capsMode = currentInputConnection?.getCursorCapsMode(
            TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES
        ) ?: 0
        val shouldShift = capsMode != 0 && settings.autoCaps
        board.setShifted(shouldShift)
    }

    // ------------------------------------------------------------------ panels

    private fun showPanel(content: View) {
        val host = panelHost ?: return
        if (openPanel != null) {
            host.removeAllViews()
        }
        // Panels sit on top of the keys, so they carry the same surface one level up.
        content.background = KeyboardSurfaceDrawable(this, settings.theme)
        openPanel = content
        host.removeAllViews()
        host.visibility = View.VISIBLE
        host.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        host.post {
            panelMotion.jumpTo(host.height.toFloat(), 0f)
            host.translationY = host.height.toFloat()
            if (!panelTicking) {
                panelTicking = true
                Clock.subscribe(panelTick)
            }
        }
    }

    private fun hidePanel(immediate: Boolean = false) {
        val host = panelHost ?: return
        if (host.visibility != View.VISIBLE) return
        if (immediate) {
            host.visibility = View.GONE
            host.removeAllViews()
            openPanel = null
            return
        }
        panelMotion.setTarget(host.height.toFloat())
        if (!panelTicking) {
            panelTicking = true
            Clock.subscribe(panelTick)
        }
    }

    private fun showClipboard() {
        val panel = clipboardPanel ?: ClipboardPanel(this).also { created ->
            created.theme = settings.theme
            created.onPaste = { text ->
                pasteText(text)
                hidePanel()
            }
            created.onClose = { hidePanel() }
            created.onCopyToSystem = { entry ->
                limbo.clipboard.copyToSystem(entry)
            }
            created.onPin = { entry -> limbo.clipboard.togglePin(entry.id) }
            created.onDelete = { entry -> limbo.clipboard.remove(entry.id) }
            created.onClear = { limbo.clipboard.clear(keepPinned = true) }
            clipboardPanel = created
        }
        panel.bind(limbo.clipboard.entries())
        showPanel(panel)
    }

    private fun showEmoji() {
        val panel = emojiPanel ?: EmojiPanel(this).also { created ->
            created.theme = settings.theme
            created.onEmoji = { emoji -> commitText(emoji, true) }
            created.onBackspace = { delete(false) }
            created.onClose = { hidePanel() }
            emojiPanel = created
        }
        showPanel(panel)
    }

    private fun closeSheet() {
        sheet?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
        }
        sheet = null
    }

    private fun showLanguagePicker() {
        val languages = settings.languages
        closeSheet()
        val quick = QuickSheet(this, settings.theme)
        quick.setTitle(
            I18n.t("Язык ввода", "Input language"),
            I18n.t("Свайп по пробелу не нужен — просто выберите", "Pick one, no long press needed")
        )
        quick.addItems(languages.map { language ->
            QuickSheet.Item(
                title = language.label,
                subtitle = language.rows.first().take(8).toCharArray().joinToString(" ") { it.toString() },
                checked = language.code == currentLanguage().code,
                glyph = Glyph.GLOBE,
                onTap = {
                    languageIndex = settings.languages.indexOfFirst { it.code == language.code }.coerceAtLeast(0)
                    settings.activeLanguage = language.code
                    keyboard?.setLanguage(language, currentFamily())
                    quick.dismiss()
                    sheet = null
                }
            )
        })
        quick.addCustom(quickAddLanguagesRow(quick))
        quick.onDismiss = { sheet = null }
        root?.let { quick.present(it) }
        sheet = quick
    }

    private fun quickAddLanguagesRow(quick: QuickSheet): View {
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Dp.dpi(this@LimboIME, 12f), Dp.dpi(this@LimboIME, 10f), Dp.dpi(this@LimboIME, 12f), Dp.dpi(this@LimboIME, 4f))
        }
        Language.entries.forEach { language ->
            val selected = settings.languageCodes.contains(language.code)
            val chip = app.limbo.keyboard.ui.kit.Ui.chipLabel(this, language.code.uppercase(), settings.theme, selected)
            chip.setOnClickListener {
                val codes = settings.languageCodes.toMutableList()
                if (selected) {
                    if (codes.size > 1) codes.remove(language.code) else return@setOnClickListener
                } else {
                    codes.add(language.code)
                }
                settings.languageCodes = codes
                quick.dismiss()
                sheet = null
                keyboard?.setLanguage(currentLanguage(), currentFamily())
            }
            row.addView(chip, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                Dp.dpi(this@LimboIME, 34f)
            ).apply { rightMargin = Dp.dpi(this@LimboIME, 6f) })
        }
        return row
    }

    private fun showQuickSettings() {
        closeSheet()
        val theme = settings.theme
        val quick = QuickSheet(this, theme)
        quick.setTitle(I18n.t("Быстрые настройки", "Quick settings"), I18n.t("Не закрывая приложение", "Without leaving the app"))
        val items = ArrayList<QuickSheet.Item>()
        items.add(
            QuickSheet.Item(
                title = I18n.t("Тема", "Theme"),
                subtitle = theme.name,
                glyph = Glyph.STAR,
                onTap = {
                    val all = Themes.presets
                    val index = all.indexOfFirst { it.id == theme.id }
                    val next = all[(index + 1).coerceAtLeast(0) % all.size]
                    settings.theme = next
                    applyTheme()
                    keyboard?.invalidateBoardSafely()
                    quick.dismiss()
                    sheet = null
                    showQuickSettings()
                }
            )
        )
        items.add(
            QuickSheet.Item(
                title = I18n.t("Высота клавиш", "Key height"),
                subtitle = when (settings.heightScale) { 0 -> I18n.t("Компактно", "Compact"); 2 -> I18n.t("Просторно", "Roomy"); else -> I18n.t("Обычно", "Balanced") },
                glyph = Glyph.CHEVRON,
                onTap = {
                    settings.heightScale = (settings.heightScale + 1) % 3
                    keyboard?.invalidateBoardSafely()
                    root?.requestLayout()
                    quick.dismiss()
                    sheet = null
                    showQuickSettings()
                }
            )
        )
        items.add(
            QuickSheet.Item(
                title = I18n.t("Вибрация", "Haptics"),
                checked = settings.haptics,
                glyph = Glyph.DOT,
                onTap = {
                    settings.haptics = !settings.haptics
                    quick.dismiss()
                    sheet = null
                    showQuickSettings()
                }
            )
        )
        items.add(
            QuickSheet.Item(
                title = I18n.t("Буфер обмена", "Clipboard"),
                subtitle = I18n.t("${limbo.clipboard.entries().size} сохранённых фрагментов", "${limbo.clipboard.entries().size} saved items"),
                glyph = Glyph.CLIPBOARD,
                onTap = {
                    quick.dismiss()
                    sheet = null
                    showClipboard()
                }
            )
        )
        items.add(
            QuickSheet.Item(
                title = I18n.t("Все настройки", "All settings"),
                subtitle = I18n.t("Откроется приложение", "Opens the app"),
                glyph = Glyph.GEAR,
                onTap = {
                    val intent = android.content.Intent(this@LimboIME, app.limbo.keyboard.ui.SettingsActivity::class.java)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    try {
                        startActivity(intent)
                    } catch (_: Throwable) {
                    }
                    quick.dismiss()
                    sheet = null
                }
            )
        )
        quick.addItems(items)
        quick.onDismiss = { sheet = null }
        root?.let { quick.present(it) }
        sheet = quick
    }

    override fun onDestroy() {
        limbo.clipboard.removeListener(clipListener)
        limbo.clipboard.stopWatching()
        super.onDestroy()
    }
}

/**
 * Minimal container that gives the magnified key preview room to float above the keys while the
 * keyboard itself stays exactly as tall as its rows.
 */
private class ImeFrame(context: Context) : FrameLayout(context) {

    var onLayoutReady: (() -> Unit)? = null
    var totalHeight: Int = 0
    private var ready = false

    init {
        clipChildren = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = android.view.View.MeasureSpec.getSize(widthMeasureSpec)
        var stripHeight = 0
        val stripChild = getChildAt(0)
        if (stripChild != null) {
            stripChild.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(Dp.dpi(context, 46f), android.view.View.MeasureSpec.EXACTLY)
            )
            if (stripChild.visibility == VISIBLE) stripHeight = stripChild.measuredHeight
        }

        var boardHeight = 0
        var bubbleSpace = 0
        val boardChild = getChildAt(1)
        if (boardChild != null) {
            boardChild.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            )
            if (boardChild is KeyboardView) {
                bubbleSpace = boardChild.bubbleSpace
                boardHeight = boardChild.imeHeightPx()
            } else {
                boardHeight = boardChild.measuredHeight
            }
        }

        // The strip sits in the same band the magnified preview needs, so the reserved area is
        // the taller of the two and the keys always begin right below it.
        val topBand = maxOf(stripHeight, bubbleSpace)
        val total = topBand + (boardHeight - bubbleSpace).coerceAtLeast(0)
        totalHeight = total

        for (i in 2 until childCount) {
            val child = getChildAt(i)
            child.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(total.coerceAtLeast(1), android.view.View.MeasureSpec.EXACTLY)
            )
        }
        setMeasuredDimension(width, total)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        val stripChild = getChildAt(0)
        stripChild?.layout(0, 0, width, stripChild.measuredHeight)
        val boardChild = getChildAt(1)
        boardChild?.layout(0, 0, width, boardChild.measuredHeight)
        for (i in 2 until childCount) {
            getChildAt(i).layout(0, 0, width, height)
        }
        if (!ready) {
            ready = true
            post { onLayoutReady?.invoke() }
        }
    }
}
