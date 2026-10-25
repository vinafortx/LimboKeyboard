package app.limbo.keyboard.layout

import app.limbo.keyboard.core.I18n

/** Physical arrangement of the letters. */
enum class Family(val labelRu: String, val labelEn: String, val sequence: String) {
    QWERTY("QWERTY", "QWERTY", "qwertyuiopasdfghjklzxcvbnm"),
    QWERTZ("QWERTZ", "QWERTZ", "qwertzuiopasdfghjkläöüyxcvbnm"),
    AZERTY("AZERTY", "AZERTY", "azertyuiopqsdfghjklmwxcvbn"),
    COLEMAK("Colemak", "Colemak", "qwfpgjluyarstdhneiozxcvbkm"),
    DVORAK("Dvorak", "Dvorak", "pyfgcrlaoeuidhtnsqjkxbmwvz")
}

/**
 * A supported language: its letters laid out in three rows (10 / 9 / 7 keys) plus the
 * extra characters that appear on long press.
 */
enum class Language(
    val code: String,
    val labelRu: String,
    val labelEn: String,
    val defaultFamily: Family,
    val rows: List<String>,
    val accents: Map<Char, String>,
    val autoCapitalize: Boolean = true
) {
    EN(
        code = "en",
        labelRu = "Английский",
        labelEn = "English",
        defaultFamily = Family.QWERTY,
        rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        accents = mapOf(
            'a' to "àáâäãåāæ",
            'c' to "çćč",
            'e' to "èéêëēę",
            'i' to "ìíîïī",
            'n' to "ñń",
            'o' to "òóôöõøōœ",
            's' to "śšş",
            'u' to "ùúûüū",
            'y' to "ýÿ",
            'z' to "žźż",
            'l' to "ł",
            'd' to "đ",
            'g' to "ğ"
        )
    ),
    RU(
        code = "ru",
        labelRu = "Русский",
        labelEn = "Russian",
        defaultFamily = Family.QWERTY,
        rows = listOf("йцукенгшщзх", "фывапролджэ", "ячсмитьбю"),
        accents = mapOf(
            'е' to "ё",
            'ь' to "ъ",
            'н' to "№",
            'х' to "«»"
        )
    ),
    DE(
        code = "de",
        labelRu = "Немецкий",
        labelEn = "German",
        defaultFamily = Family.QWERTZ,
        rows = listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnmß"),
        accents = mapOf(
            'a' to "äàâá",
            'o' to "öòôó",
            'u' to "üùûú",
            's' to "ßśš",
            'e' to "èéêë"
        )
    ),
    FR(
        code = "fr",
        labelRu = "Французский",
        labelEn = "French",
        defaultFamily = Family.AZERTY,
        rows = listOf("azertyuiop", "qsdfghjklm", "wxcvbn"),
        accents = mapOf(
            'e' to "éèêë",
            'a' to "àâä",
            'c' to "ç",
            'i' to "îï",
            'o' to "ôö",
            'u' to "ùûü",
            'y' to "ÿ",
            'n' to "ñ"
        )
    ),
    ES(
        code = "es",
        labelRu = "Испанский",
        labelEn = "Spanish",
        defaultFamily = Family.QWERTY,
        rows = listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm"),
        accents = mapOf(
            'a' to "áàä",
            'e' to "éèë",
            'i' to "íìï",
            'o' to "óòö",
            'u' to "úùü",
            'n' to "ñ",
            'c' to "ç"
        )
    ),
    IT(
        code = "it",
        labelRu = "Итальянский",
        labelEn = "Italian",
        defaultFamily = Family.QWERTY,
        rows = listOf("qwertyuiop", "asdfghjklò", "zxcvbnm"),
        accents = mapOf('a' to "àá", 'e' to "èé", 'i' to "ìí", 'o' to "òó", 'u' to "ùú")
    ),
    TR(
        code = "tr",
        labelRu = "Турецкий",
        labelEn = "Turkish",
        defaultFamily = Family.QWERTY,
        rows = listOf("qwertyuıopğ", "asdfghjklşi", "zxcvbnmöç"),
        accents = mapOf('a' to "â", 'i' to "î", 'u' to "û", 'o' to "ô")
    ),
    PL(
        code = "pl",
        labelRu = "Польский",
        labelEn = "Polish",
        defaultFamily = Family.QWERTY,
        rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        accents = mapOf(
            'a' to "ąá", 'c' to "ć", 'e' to "ęé", 'l' to "ł", 'n' to "ń", 'o' to "ó", 's' to "ś", 'z' to "żź"
        )
    );

    val label: String get() = if (I18n.isRussian) labelRu else labelEn

    /** Latin-only languages can be re-laid-out into any of the five families. */
    val supportsFamilies: Boolean
        get() = code in setOf("en", "it", "pl")

    /** Rows produced for a chosen family, falling back to the natural layout. */
    fun rowsFor(family: Family): List<String> {
        if (!supportsFamilies || family == defaultFamily) return rows
        val letters = family.sequence.filter { it.isLetter() }
        if (letters.length < 26) return rows
        return listOf(letters.substring(0, 10), letters.substring(10, 19), letters.substring(19, 26))
    }

    companion object {
        fun byCode(code: String?): Language? = entries.firstOrNull { it.code == code }

        fun forLocale(tag: String?): Language? {
            val lang = tag?.substringBefore('-')?.substringBefore('_')?.lowercase()
            return byCode(lang)
        }
    }
}

/** Shared symbol pages. */
object Symbols {

    val numbers = listOf("1234567890", "@#$%&*()-", "+=/:;!?,.")
    val symbols = listOf("~`|•√π÷×¶∆", "£€¥¢^°={}<>", "\\©®™✓[]…")

    val numberRow = "1234567890"

    /** Characters offered on long press for a symbol key. */
    fun alternativesFor(char: Char): String = when (char) {
        '.' -> ",!?…"
        ',' -> ";:'\""
        '?' -> "!¿"
        '!' -> "?¡"
        '-' -> "–—•"
        '/' -> "\\"
        '(' -> "[{<"
        ')' -> "]}>"
        else -> ""
    }
}
