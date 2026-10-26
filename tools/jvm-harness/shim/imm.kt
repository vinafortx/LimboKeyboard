package android.view.inputmethod

import android.content.Context

class InputMethodInfo(val packageName: String = "app.limbo.keyboard") {
    val id: String get() = packageName
}

class InputMethodManager {
    val enabledInputMethodList: List<InputMethodInfo> get() = emptyList()
    fun showInputMethodPicker() {}
    fun showSoftInput(view: android.view.View, flags: Int): Boolean = true
    fun hideSoftInputFromWindow(token: Any?, flags: Int): Boolean = true
    companion object {
        const val SHOW_IMPLICIT = 1
        const val HIDE_IMPLICIT_ONLY = 1
    }
}

class EditorInfo {
    @JvmField var inputType: Int = 0
    @JvmField var imeOptions: Int = 0
    @JvmField var packageName: String? = null
    @JvmField var fieldId: Int = 0
    @JvmField var hintText: CharSequence? = null
    @JvmField var initialSelStart: Int = 0
    @JvmField var initialSelEnd: Int = 0
    fun setInputType(t: Int) { inputType = t }
    fun setImeOptions(o: Int) { imeOptions = o }
    companion object {
        const val IME_MASK_ACTION = 0xff
        const val IME_ACTION_NONE = 1
        const val IME_ACTION_UNSPECIFIED = 0
        const val IME_ACTION_GO = 2
        const val IME_ACTION_SEARCH = 3
        const val IME_ACTION_SEND = 4
        const val IME_ACTION_NEXT = 5
        const val IME_ACTION_DONE = 6
    }
}

open class InputConnection {
    open fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? = null
    open fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = null
    open fun getSelectedText(flags: Int): CharSequence? = null
    open fun getCursorCapsMode(reqModes: Int): Int = 0
    open fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean = true
    open fun deleteSurroundingText(before: Int, after: Int): Boolean = true
    open fun sendKeyEvent(event: android.view.KeyEvent?): Boolean = true
    open fun performEditorAction(id: Int): Boolean = true
    open fun performContextMenuAction(id: Int): Boolean = true
    open fun setSelection(start: Int, end: Int): Boolean = true
    open fun getExtractedText(request: Any?, flags: Int): Any? = null
    open fun finishComposingText(): Boolean = true
    open fun setComposingText(text: CharSequence?, pos: Int): Boolean = true
}
