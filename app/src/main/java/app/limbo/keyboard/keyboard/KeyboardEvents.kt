package app.limbo.keyboard.keyboard

import app.limbo.keyboard.layout.Action
import app.limbo.keyboard.layout.Key

/** Everything the keyboard can ask its host to do. Kept as data so logging and testing stay easy. */
sealed class KeyboardEvent {
    data class Commit(val text: String, val key: Key? = null) : KeyboardEvent()
    data class Delete(val wholeWord: Boolean = false) : KeyboardEvent()
    data class Cursor(val delta: Int) : KeyboardEvent()
    data class ActionRequested(val action: Action) : KeyboardEvent()
    data class SwitchLanguage(val forward: Boolean = true) : KeyboardEvent()
    object LanguagePicker : KeyboardEvent()
    object ClipboardPanel : KeyboardEvent()
    object EmojiPanel : KeyboardEvent()
    object SettingsPanel : KeyboardEvent()
    data class StateChanged(val shifted: Boolean, val caps: Boolean, val layer: String) : KeyboardEvent()
    data class HeightChanged(val heightPx: Int) : KeyboardEvent()
    data class Feedback(val key: Key, val longPress: Boolean) : KeyboardEvent()
    object Dismiss : KeyboardEvent()
}
