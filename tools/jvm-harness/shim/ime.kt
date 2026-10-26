package android.inputmethodservice

import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo

open class InputMethodService : android.app.Service() {
    private val dialog: android.app.Dialog by lazy { android.app.Dialog() }
    fun getWindow(): android.app.Dialog? = dialog
    fun getCurrentInputConnection(): android.view.inputmethod.InputConnection? = null
    fun getCurrentInputEditorInfo(): android.view.inputmethod.EditorInfo? = info
    fun getCurrentInputBinding(): Any? = null
    fun isInputViewShown(): Boolean = true
    fun isFullscreenMode(): Boolean = false
    fun getMaxWidth(): Int = 1080
    fun getMaxHeight(): Int = 780
    fun setCandidatesViewShown(show: Boolean) {}
    fun updateInputViewShown() {}
    fun setExtractViewShown(show: Boolean) {}
    var info: android.view.inputmethod.EditorInfo? = null
    open fun onCreateInputView(): View? = null
    open fun onCreateCandidatesView(): View? = null
    open fun onStartInputView(info: EditorInfo?, restarting: Boolean) { this.info = info }
    open fun onFinishInputView(finishingInput: Boolean) {}
    open fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {}
    open fun onFinishInput() {}
    fun setInputView(view: View?) {}
    fun requestHideSelf(flags: Int) {}
    fun switchInputMethod(id: String) {}
    fun sendDownUpKeyEvents(keyCode: Int) {}
}
