package android.provider

import android.content.ContentResolver

class Settings {
    class Secure {
        companion object {
            const val DEFAULT_INPUT_METHOD = "default_input_method"
            const val ENABLED_INPUT_METHODS = "enabled_input_methods"
            @JvmStatic fun getString(cr: ContentResolver?, name: String): String? = null
            @JvmStatic fun putString(cr: ContentResolver?, name: String, value: String?): Boolean = true
        }
    }
    class System {
        companion object {
            @JvmStatic fun getInt(cr: ContentResolver, name: String, def: Int): Int = def
        }
    }
    companion object {
        const val ACTION_INPUT_METHOD_SETTINGS = "android.settings.INPUT_METHOD_SETTINGS"
        const val ACTION_SETTINGS = "android.settings.SETTINGS"
        const val ACTION_MAIN = "android.intent.action.MAIN"
    }
}
