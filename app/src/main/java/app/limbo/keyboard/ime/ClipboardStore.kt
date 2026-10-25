package app.limbo.keyboard.ime

import android.content.ClipboardManager
import android.content.Context
import app.limbo.keyboard.core.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A clipboard that remembers. Unlike the system tray it keeps every text you copied, lets you
 * pin the ones you reuse, and survives a restart.
 */
class ClipboardStore(private val context: Context, private val settings: Settings) {

    data class Entry(
        val id: Long,
        val text: String,
        val createdAt: Long,
        val pinned: Boolean = false
    ) {
        val preview: String
            get() {
                val flat = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
                return if (flat.length <= 180) flat else flat.take(177) + "…"
            }

        val kind: Kind
            get() = when {
                text.matches(Regex("^https?://\\S+$")) -> Kind.LINK
                text.matches(Regex("^[\\w.+-]+@[\\w-]+\\.[\\w.]+$")) -> Kind.EMAIL
                text.matches(Regex("^\\+?[\\d\\s()-]{7,}$")) -> Kind.PHONE
                text.contains('\n') -> Kind.MULTILINE
                else -> Kind.TEXT
            }
    }

    enum class Kind { TEXT, LINK, EMAIL, PHONE, MULTILINE }

    private val sp = context.applicationContext.getSharedPreferences("limbo_clipboard", Context.MODE_PRIVATE)
    private val listeners = CopyOnWriteArrayList<(List<Entry>) -> Unit>()
    private var loaded: MutableList<Entry>? = null
    private var watcher: ClipboardManager? = null
    private var lastForeignText: String? = null

    val system: ClipboardManager?
        get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    fun entries(): List<Entry> = load().sortedWith(compareByDescending<Entry> { it.pinned }.thenByDescending { it.createdAt })

    fun pinned(): List<Entry> = entries().filter { it.pinned }

    fun addListener(listener: (List<Entry>) -> Unit) {
        listeners.add(listener)
        listener(entries())
    }

    fun removeListener(listener: (List<Entry>) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        val snapshot = entries()
        listeners.forEach { it(snapshot) }
    }

    private fun load(): MutableList<Entry> {
        loaded?.let { return it }
        val raw = sp.getString("entries", null)
        val list = ArrayList<Entry>()
        if (raw != null) {
            runCatching {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    list.add(
                        Entry(
                            id = o.optLong("id", i.toLong()),
                            text = o.optString("text", ""),
                            createdAt = o.optLong("at", 0L),
                            pinned = o.optBoolean("pinned", false)
                        )
                    )
                }
            }
        }
        loaded = list
        return list
    }

    private fun persist() {
        val array = JSONArray()
        load().forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("text", entry.text)
                    put("at", entry.createdAt)
                    put("pinned", entry.pinned)
                }
            )
        }
        sp.edit().putString("entries", array.toString()).apply()
    }

    /** Called by the service when the keyboard becomes visible so we never miss a copy. */
    fun startWatching() {
        if (!settings.clipboardEnabled) return
        if (watcher != null) return
        val manager = system ?: return
        watcher = manager
        manager.addPrimaryClipChangedListener(listener)
        captureCurrent()
    }

    fun stopWatching() {
        watcher?.removePrimaryClipChangedListener(listener)
        watcher = null
    }

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        captureCurrent()
    }

    private fun captureCurrent() {
        val manager = system ?: return
        val clip = runCatching { manager.primaryClip }.getOrNull() ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0)?.coerceToText(context)?.toString() ?: return
        if (text.isBlank()) return
        if (text == lastForeignText) return
        lastForeignText = text
        capture(text, fromSystem = true)
    }

    fun capture(text: String, fromSystem: Boolean = false) {
        if (text.isBlank()) return
        if (!settings.clipboardEnabled && fromSystem) return
        val list = load()
        val trimmed = if (text.length > 6000) text.take(6000) else text
        val existing = list.firstOrNull { it.text == trimmed }
        if (existing != null) {
            list.remove(existing)
            list.add(0, existing.copy(createdAt = System.currentTimeMillis()))
        } else {
            list.add(
                0,
                Entry(
                    id = System.currentTimeMillis() * 1000 + (list.size % 1000),
                    text = trimmed,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        val limit = settings.clipboardHistorySize.coerceIn(5, 200)
        val pinned = list.filter { it.pinned }
        val rest = list.filterNot { it.pinned }.sortedByDescending { it.createdAt }.take((limit - pinned.size).coerceAtLeast(1))
        val merged = (pinned + rest).sortedByDescending { it.createdAt }
        loaded = merged.toMutableList()
        persist()
        notifyChanged()
    }

    fun togglePin(id: Long) {
        val list = load()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        val entry = list[index]
        list[index] = entry.copy(pinned = !entry.pinned)
        persist()
        notifyChanged()
    }

    fun remove(id: Long) {
        val list = load()
        list.removeAll { it.id == id }
        persist()
        notifyChanged()
    }

    fun clear(keepPinned: Boolean = true) {
        val list = load()
        if (keepPinned) list.retainAll { it.pinned } else list.clear()
        persist()
        notifyChanged()
    }

    /** Puts a stored entry back into the system clipboard, e.g. to paste into another app. */
    fun copyToSystem(entry: Entry) {
        val manager = system ?: return
        runCatching { manager.setPrimaryClip(android.content.ClipData.newPlainText("Limbo", entry.text)) }
    }

    fun search(query: String): List<Entry> {
        if (query.isBlank()) return entries()
        val q = query.trim().lowercase()
        return entries().filter { it.text.lowercase().contains(q) }
    }
}
