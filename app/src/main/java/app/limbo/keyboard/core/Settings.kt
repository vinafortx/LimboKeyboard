package app.limbo.keyboard.core

import android.content.Context
import android.content.SharedPreferences
import app.limbo.keyboard.layout.Language
import app.limbo.keyboard.theme.Theme
import app.limbo.keyboard.theme.ThemeCodec
import app.limbo.keyboard.theme.ThemeStore

/**
 * One source of truth for user preferences. Every setter persists immediately and notifies
 * listeners, so a change made in Settings is already visible in the keyboard behind it.
 */
class Settings(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("limbo_settings", Context.MODE_PRIVATE)

    private val listeners = LinkedHashSet<(String?) -> Unit>()
    val themes = ThemeStore(this, context)

    fun onChange(block: (String?) -> Unit) {
        listeners.add(block)
    }

    /** Listener that only fires for one preference key. */
    fun onChange(key: String, block: (String?) -> Unit) {
        listeners.add { changedKey -> if (changedKey == key || changedKey == null) block(changedKey) }
    }

    fun offChange(block: (String?) -> Unit) {
        listeners.remove(block)
    }

    private fun changed(key: String?) {
        listeners.toList().forEach { it(key) }
    }

    private fun put(key: String, value: Boolean) {
        sp.edit().putBoolean(key, value).apply()
        changed(key)
    }

    private fun put(key: String, value: Int) {
        sp.edit().putInt(key, value).apply()
        changed(key)
    }

    private fun put(key: String, value: Float) {
        sp.edit().putFloat(key, value).apply()
        changed(key)
    }

    private fun put(key: String, value: String) {
        sp.edit().putString(key, value).apply()
        changed(key)
    }

    val raw: SharedPreferences get() = sp

    // ---------------------------------------------------------------- appearance

    var theme: Theme
        get() = themes.current
        set(value) {
            themes.current = value
            changed(KEY_THEME)
        }

    var motion: MotionStyle
        get() = MotionStyle.entries.getOrElse(sp.getInt(KEY_MOTION, 1)) { MotionStyle.BALANCED }
        set(value) {
            put(KEY_MOTION, value.ordinal)
            Clock.reducedMotion = false
        }

    var haptics: Boolean
        get() = sp.getBoolean(KEY_HAPTICS, true)
        set(value) = put(KEY_HAPTICS, value)

    var hapticStrength: Int
        get() = sp.getInt(KEY_HAPTIC_STRENGTH, 1)
        set(value) = put(KEY_HAPTIC_STRENGTH, value)

    var sound: Boolean
        get() = sp.getBoolean(KEY_SOUND, false)
        set(value) = put(KEY_SOUND, value)

    // ---------------------------------------------------------------- keyboard geometry

    /** 0 = compact, 1 = balanced, 2 = roomy. Multiplies the row height. */
    var heightScale: Int
        get() = sp.getInt(KEY_HEIGHT_SCALE, 1)
        set(value) = put(KEY_HEIGHT_SCALE, value)

    /** Extra room around a key that still counts as a press, 0..1. */
    var touchSlop: Float
        get() = sp.getFloat(KEY_TOUCH_SLOP, 0.62f)
        set(value) = put(KEY_TOUCH_SLOP, value.coerceIn(0f, 1f))

    var numberRow: Boolean
        get() = sp.getBoolean(KEY_NUMBER_ROW, false)
        set(value) = put(KEY_NUMBER_ROW, value)

    var cursorRow: Boolean
        get() = sp.getBoolean(KEY_CURSOR_ROW, false)
        set(value) = put(KEY_CURSOR_ROW, value)

    var popups: Boolean
        get() = sp.getBoolean(KEY_POPUPS, true)
        set(value) = put(KEY_POPUPS, value)

    var longPressDelay: Int
        get() = sp.getInt(KEY_LONG_PRESS, 320)
        set(value) = put(KEY_LONG_PRESS, value)

    // ---------------------------------------------------------------- typing behaviour

    var autoCaps: Boolean
        get() = sp.getBoolean(KEY_AUTO_CAPS, true)
        set(value) = put(KEY_AUTO_CAPS, value)

    var smartSpace: Boolean
        get() = sp.getBoolean(KEY_SMART_SPACE, true)
        set(value) = put(KEY_SMART_SPACE, value)

    var swipeToCursor: Boolean
        get() = sp.getBoolean(KEY_SWIPE_CURSOR, true)
        set(value) = put(KEY_SWIPE_CURSOR, value)

    var suggestions: Boolean
        get() = sp.getBoolean(KEY_SUGGESTIONS, true)
        set(value) = put(KEY_SUGGESTIONS, value)

    var autoCorrect: Boolean
        get() = sp.getBoolean(KEY_AUTO_CORRECT, false)
        set(value) = put(KEY_AUTO_CORRECT, value)

    var doubleSpacePeriod: Boolean
        get() = sp.getBoolean(KEY_DOUBLE_SPACE, true)
        set(value) = put(KEY_DOUBLE_SPACE, value)

    // ---------------------------------------------------------------- languages

    var languageCodes: List<String>
        get() {
            val raw = sp.getString(KEY_LANGUAGES, null)
            val parsed = raw?.split(',')?.map { it.trim() }?.filter { Language.byCode(it) != null }
            return if (parsed.isNullOrEmpty()) listOf("en", "ru") else parsed
        }
        set(value) {
            val codes = value.distinct().filter { Language.byCode(it) != null }
            put(KEY_LANGUAGES, codes.joinToString(","))
        }

    val languages: List<Language>
        get() = languageCodes.mapNotNull { Language.byCode(it) }

    var activeLanguage: String
        get() = sp.getString(KEY_ACTIVE_LANGUAGE, "en") ?: "en"
        set(value) = put(KEY_ACTIVE_LANGUAGE, value)

    // ---------------------------------------------------------------- clipboard

    var clipboardHistorySize: Int
        get() = sp.getInt(KEY_CLIP_SIZE, 40)
        set(value) = put(KEY_CLIP_SIZE, value)

    var clipboardEnabled: Boolean
        get() = sp.getBoolean(KEY_CLIP_ENABLED, true)
        set(value) = put(KEY_CLIP_ENABLED, value)

    var clipboardSuggestionsInBar: Boolean
        get() = sp.getBoolean(KEY_CLIP_BAR, true)
        set(value) = put(KEY_CLIP_BAR, value)

    // ---------------------------------------------------------------- one-time state

    var onboardingDone: Boolean
        get() = sp.getBoolean(KEY_ONBOARDING, false)
        set(value) = put(KEY_ONBOARDING, value)

    var onboardingStep: Int
        get() = sp.getInt(KEY_ONBOARDING_STEP, 0)
        set(value) = put(KEY_ONBOARDING_STEP, value)

    var typedCharacters: Long
        get() = sp.getLong(KEY_TYPED, 0L)
        set(value) {
            sp.edit().putLong(KEY_TYPED, value).apply()
        }

    var keystrokes: Long
        get() = sp.getLong(KEY_KEYSTROKES, 0L)
        set(value) {
            sp.edit().putLong(KEY_KEYSTROKES, value).apply()
        }

    fun resetEverything() {
        sp.edit().clear().apply()
        themes.reset()
        changed(null)
    }

    fun exportJson(): String = buildString {
        append('{')
        append("\"version\":1,")
        append("\"theme\":").append(ThemeCodec.encode(theme)).append(',')
        append("\"motion\":").append(motion.ordinal).append(',')
        append("\"heightScale\":").append(heightScale).append(',')
        append("\"touchSlop\":").append(touchSlop).append(',')
        append("\"numberRow\":").append(numberRow).append(',')
        append("\"cursorRow\":").append(cursorRow).append(',')
        append("\"popups\":").append(popups).append(',')
        append("\"haptics\":").append(haptics).append(',')
        append("\"sound\":").append(sound).append(',')
        append("\"autoCaps\":").append(autoCaps).append(',')
        append("\"smartSpace\":").append(smartSpace).append(',')
        append("\"languages\":\"").append(languageCodes.joinToString(",")).append("\"")
        append('}')
    }

    companion object {
        const val KEY_THEME = "theme"
        const val KEY_MOTION = "motion"
        const val KEY_HAPTICS = "haptics"
        const val KEY_HAPTIC_STRENGTH = "haptic_strength"
        const val KEY_SOUND = "sound"
        const val KEY_HEIGHT_SCALE = "height_scale"
        const val KEY_TOUCH_SLOP = "touch_slop"
        const val KEY_NUMBER_ROW = "number_row"
        const val KEY_CURSOR_ROW = "cursor_row"
        const val KEY_POPUPS = "popups"
        const val KEY_LONG_PRESS = "long_press"
        const val KEY_AUTO_CAPS = "auto_caps"
        const val KEY_SMART_SPACE = "smart_space"
        const val KEY_SWIPE_CURSOR = "swipe_cursor"
        const val KEY_SUGGESTIONS = "suggestions"
        const val KEY_AUTO_CORRECT = "auto_correct"
        const val KEY_DOUBLE_SPACE = "double_space"
        const val KEY_LANGUAGES = "languages"
        const val KEY_ACTIVE_LANGUAGE = "active_language"
        const val KEY_CLIP_SIZE = "clip_size"
        const val KEY_CLIP_ENABLED = "clip_enabled"
        const val KEY_CLIP_BAR = "clip_bar"
        const val KEY_ONBOARDING = "onboarding_done"
        const val KEY_ONBOARDING_STEP = "onboarding_step"
        const val KEY_TYPED = "typed_chars"
        const val KEY_KEYSTROKES = "keystrokes"
    }
}
