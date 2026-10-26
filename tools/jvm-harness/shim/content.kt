package android.content

import android.content.res.Resources

interface SharedPreferences {
    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putLong(key: String, value: Long): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }
    fun getString(key: String, def: String?): String?
    fun getInt(key: String, def: Int): Int
    fun getFloat(key: String, def: Float): Float
    fun getLong(key: String, def: Long): Long
    fun getBoolean(key: String, def: Boolean): Boolean
    fun contains(key: String): Boolean
    fun edit(): Editor
    fun getAll(): Map<String, *>
}

class MemoryPrefs : SharedPreferences {
    private val map = HashMap<String, Any?>()

    private inner class Ed : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private val removals = HashSet<String>()
        private var cleared = false
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removals.add(key) }
        override fun clear() = apply { cleared = true }
        override fun apply() {
            if (cleared) map.clear()
            removals.forEach { map.remove(it) }
            map.putAll(pending)
        }
        override fun commit(): Boolean { apply(); return true }
    }

    override fun getString(key: String, def: String?): String? = map[key] as? String ?: def
    override fun getInt(key: String, def: Int): Int = map[key] as? Int ?: def
    override fun getFloat(key: String, def: Float): Float = map[key] as? Float ?: def
    override fun getLong(key: String, def: Long): Long = map[key] as? Long ?: def
    override fun getBoolean(key: String, def: Boolean): Boolean = map[key] as? Boolean ?: def
    override fun contains(key: String): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Ed()
    override fun getAll(): Map<String, *> = map
}

class Intent(val action: String? = null) {
    constructor(context: Context, cls: Class<*>) : this(null as String?)
}

open class Context {
    private val prefs = HashMap<String, SharedPreferences>()
    val resources: Resources = Resources()
    open val applicationContext: Context get() = this
    open val packageName: String get() = "app.limbo.keyboard"
    val contentResolver: ContentResolver = ContentResolver()

    open fun getExternalFilesDir(type: String?): java.io.File? = filesDir

    open val filesDir: java.io.File by lazy {
        java.nio.file.Files.createTempDirectory("limbo-shim").toFile()
    }

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        prefs.getOrPut(name) { MemoryPrefs() }

    open fun getSystemService(name: String): Any? = null
    fun getString(resId: Int): String = ""
    fun getString(resId: Int, vararg args: Any?): String = ""
    fun startActivity(intent: Intent) {}
    fun getDrawable(resId: Int): Any? = null

    companion object {
        const val MODE_PRIVATE = 0
        const val CLIPBOARD_SERVICE = "clipboard"
        const val INPUT_METHOD_SERVICE = "input_method"
        const val VIBRATOR_SERVICE = "vibrator"
        const val VIBRATOR_MANAGER_SERVICE = "vibrator_manager"
        const val WINDOW_SERVICE = "window"
    }
}

class ContentResolver

interface ClipData { val itemCount: Int }

open class ClipboardManager {
    fun interface OnPrimaryClipChangedListener {
        fun onPrimaryClipChanged()
    }
    fun addPrimaryClipChangedListener(l: OnPrimaryClipChangedListener) {}
    fun removePrimaryClipChangedListener(l: OnPrimaryClipChangedListener) {}
    var primaryClip: Any? = null
    fun hasPrimaryClip(): Boolean = false
}
