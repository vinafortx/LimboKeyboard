package app.limbo.keyboard.ime

import android.content.Context
import org.json.JSONObject

/**
 * Personal dictionary. The keyboard learns the words you actually use and offers completions for
 * the word being typed; nothing leaves the device.
 */
class Dictionary(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("limbo_dictionary", Context.MODE_PRIVATE)
    private var words: MutableMap<String, Int>? = null
    private var recent: ArrayDeque<String> = ArrayDeque()

    private fun load(): MutableMap<String, Int> {
        words?.let { return it }
        val map = HashMap<String, Int>()
        val raw = sp.getString("words", null)
        if (raw != null) {
            runCatching {
                val json = JSONObject(raw)
                json.keys().forEach { key -> map[key] = json.optInt(key, 1) }
            }
        }
        words = map
        return map
    }

    private fun persist() {
        val map = load()
        if (map.size > 3000) {
            val trimmed = map.entries.sortedByDescending { it.value }.take(2000)
            map.clear()
            trimmed.forEach { map[it.key] = it.value }
        }
        val json = JSONObject()
        map.forEach { (word, count) -> json.put(word, count) }
        sp.edit().putString("words", json.toString()).apply()
    }

    fun learn(word: String) {
        val clean = word.trim().lowercase()
        if (clean.length < 2 || clean.length > 32) return
        if (!clean.all { it.isLetter() || it == '\'' || it == '-' }) return
        val map = load()
        map[clean] = (map[clean] ?: 0) + 1
        recent.remove(clean)
        recent.addFirst(clean)
        if (recent.size > 200) recent.removeLast()
        persist()
    }

    fun forget(word: String) {
        load().remove(word.lowercase())
        persist()
    }

    fun clear() {
        load().clear()
        recent.clear()
        persist()
    }

    /** Completions for the current word, ordered by personal usage and a small starter set. */
    fun suggestions(prefix: String, limit: Int = 3): List<String> {
        val query = prefix.trim().lowercase()
        if (query.isEmpty()) return emptyList()
        val map = load()
        val scored = ArrayList<Pair<String, Int>>()
        map.forEach { (word, count) ->
            if (word.startsWith(query) && word != query) scored.add(word to count)
        }
        if (scored.size < limit) {
            val extra = startersFor(query).filter { it != query && !scored.any { pair -> pair.first == it } }
            extra.forEachIndexed { index, word -> scored.add(word to (30 - index)) }
        }
        return scored.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .map { it.first }
            .take(limit)
    }

    /**
     * A compact starter vocabulary so the strip is useful before the keyboard has learned anything.
     * Kept tiny on purpose: personal words always outrank it.
     */
    private fun startersFor(prefix: String): List<String> {
        val dictionary = if (app.limbo.keyboard.core.I18n.isRussian) russian else english
        return dictionary.filter { it.startsWith(prefix) }.take(6)
    }

    private val english = listOf(
        "the", "and", "you", "that", "have", "for", "not", "with", "this", "but", "his", "from",
        "they", "say", "her", "she", "will", "one", "all", "would", "there", "their", "what",
        "out", "about", "who", "get", "which", "when", "make", "can", "like", "time", "just",
        "him", "know", "take", "people", "into", "year", "your", "good", "some", "could", "them",
        "see", "other", "than", "then", "now", "look", "only", "come", "its", "over", "think",
        "also", "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
        "even", "new", "want", "because", "any", "these", "give", "day", "most", "us", "thanks",
        "please", "tomorrow", "today", "message", "meeting", "hello", "number", "address", "email"
    )

    private val russian = listOf(
        "и", "в", "не", "на", "что", "я", "с", "он", "как", "а", "то", "все", "она", "так",
        "его", "но", "да", "ты", "к", "у", "же", "вы", "за", "бы", "по", "только", "ее",
        "мне", "было", "вот", "от", "меня", "еще", "нет", "о", "из", "ему", "теперь", "когда",
        "даже", "ну", "вдруг", "ли", "если", "уже", "или", "ни", "быть", "был", "него", "до",
        "вас", "нибудь", "опять", "уж", "вам", "ведь", "там", "потом", "себя", "ничего", "ей",
        "может", "они", "тут", "где", "есть", "надо", "ней", "для", "мы", "тебя", "их", "чем",
        "была", "сам", "чтоб", "без", "будто", "чего", "раз", "тоже", "себе", "под", "будет",
        "ж", "тогда", "кто", "этот", "того", "потому", "этого", "какой", "совсем", "ним",
        "здесь", "этом", "один", "почти", "мой", "тем", "чтобы", "нее", "сейчас", "были",
        "куда", "зачем", "сказать", "всех", "никогда", "сегодня", "можно", "при", "наконец",
        "два", "об", "другой", "хоть", "после", "над", "больше", "тот", "через", "эти",
        "нас", "про", "всего", "них", "какая", "много", "разве", "три", "эту", "моя", "впрочем",
        "хорошо", "свою", "этой", "перед", "иногда", "лучше", "чуть", "том", "нельзя", "такой",
        "им", "более", "всегда", "конечно", "всю", "между", "спасибо", "пожалуйста", "привет",
        "завтра", "сообщение", "встреча", "номер", "адрес", "работа", "время", "почта"
    )
}
