package app.limbo.keyboard.layout

import app.limbo.keyboard.core.Settings

/** What pressing a key does. Input keys carry their own text. */
enum class Action {
    INPUT,
    SHIFT,
    BACKSPACE,
    ENTER,
    SPACE,
    LAYER_LETTERS,
    LAYER_NUMBERS,
    LAYER_SYMBOLS,
    LANGUAGE,
    CLIPBOARD,
    EMOJI,
    SETTINGS,
    HIDE,
    CURSOR_LEFT,
    CURSOR_RIGHT,
    TAB,
    SELECT_ALL
}

/** Line icons drawn by the keyboard renderer. */
enum class Glyph {
    NONE,
    SHIFT,
    SHIFT_ACTIVE,
    CAPS,
    BACKSPACE,
    ENTER,
    GLOBE,
    CLIPBOARD,
    EMOJI,
    GEAR,
    ARROW_LEFT,
    ARROW_RIGHT,
    TAB,
    HIDE,
    SPACE,
    MIC,
    SEARCH,
    PLUS,
    CHECK,
    CHEVRON,
    DOT,
    STAR
}

enum class Layer { LETTERS, NUMBERS, SYMBOLS }

/** One key. Immutable: a new board is built whenever state changes. */
data class Key(
    val label: String,
    val action: Action = Action.INPUT,
    val text: String = "",
    val shiftedText: String? = null,
    val shiftedLabel: String? = null,
    val weight: Float = 1f,
    val alt: List<String> = emptyList(),
    val glyph: Glyph = Glyph.NONE,
    val repeatable: Boolean = false,
    val accent: Boolean = false
) {
    val outputsText: Boolean get() = action == Action.INPUT && text.isNotEmpty()
}

data class Row(val keys: List<Key>, val heightFactor: Float = 1f, val inset: Float = 0f) {
    val weightSum: Float get() = keys.sumOf { it.weight.toDouble() }.toFloat()
}

/** A laid-out keyboard ready to be measured. */
class Board(
    val rows: List<Row>,
    val language: Language,
    val layer: Layer,
    val shifted: Boolean,
    val capsLock: Boolean
) {
    fun allKeys(): List<Key> = rows.flatMap { it.keys }
}

/**
 * Turns the user's preferences and the current language into rows of keys.
 * The three classic Latin rows get the familiar staggering: the middle row is inset by half a
 * key on each side and the bottom letter row holds Shift and Backspace.
 */
object BoardBuilder {

    fun build(
        settings: Settings,
        language: Language,
        family: Family,
        layer: Layer,
        shifted: Boolean,
        capsLock: Boolean,
        showLanguageKey: Boolean
    ): Board {
        val rows = when (layer) {
            Layer.LETTERS -> letterRows(settings, language, family, shifted, capsLock, showLanguageKey)
            Layer.NUMBERS -> symbolRows(Symbols.numbers, Layer.NUMBERS)
            Layer.SYMBOLS -> symbolRows(Symbols.symbols, Layer.SYMBOLS)
        }
        return Board(rows, language, layer, shifted, capsLock)
    }

    private fun letterRows(
        settings: Settings,
        language: Language,
        family: Family,
        shifted: Boolean,
        capsLock: Boolean,
        showLanguageKey: Boolean
    ): List<Row> {
        val source = language.rowsFor(family)
        val result = ArrayList<Row>()

        if (settings.numberRow) {
            result += Row(Symbols.numberRow.map { char ->
                Key(
                    label = char.toString(),
                    text = char.toString(),
                    alt = if (char in '1'..'9') listOf(Symbols.numbers[1][char - '1'].toString()) else listOf("%", "‰")
                )
            }, heightFactor = 0.86f)
        }

        if (settings.cursorRow) {
            result += Row(
                listOf(
                    Key("", action = Action.CURSOR_LEFT, glyph = Glyph.ARROW_LEFT, weight = 1.4f),
                    Key("", action = Action.TAB, glyph = Glyph.TAB, weight = 1f),
                    Key(
                        app.limbo.keyboard.core.I18n.t("Выделить всё", "Select all"),
                        action = Action.SELECT_ALL,
                        glyph = Glyph.STAR,
                        weight = 2.2f
                    ),
                    Key("", action = Action.CURSOR_RIGHT, glyph = Glyph.ARROW_RIGHT, weight = 1.4f)
                ),
                heightFactor = 0.72f
            )
        }

        // Row 1: ten letters.
        result += Row(rowOne(source[0], language, shifted))

        // Row 2: nine letters, inset by half a key.
        result += Row(rowTwo(source[1], language, shifted), inset = 0.5f)

        // Row 3: Shift, letters, Backspace.
        val shiftKey = Key(
            label = "",
            action = Action.SHIFT,
            weight = 1.4f,
            glyph = when {
                capsLock -> Glyph.CAPS
                shifted -> Glyph.SHIFT_ACTIVE
                else -> Glyph.SHIFT
            }
        )
        val backspace = Key(
            label = "",
            action = Action.BACKSPACE,
            weight = 1.4f,
            glyph = Glyph.BACKSPACE,
            repeatable = true
        )
        val third = source[2].map { char -> letterKey(char, language, shifted) }
        result += Row(listOf(shiftKey) + third + listOf(backspace))

        // Row 4: the action row, fully configurable.
        result += Row(bottomRow(settings, language, layer = Layer.LETTERS, shifted = shifted, showLanguageKey = showLanguageKey))

        return result
    }

    private fun rowOne(text: String, language: Language, shifted: Boolean): List<Key> =
        text.map { letterKey(it, language, shifted) }

    private fun rowTwo(text: String, language: Language, shifted: Boolean): List<Key> =
        text.map { letterKey(it, language, shifted) }

    private fun letterKey(char: Char, language: Language, shifted: Boolean): Key {
        val lower = char.lowercaseChar().toString()
        val upper = char.uppercaseChar().toString()
        val visible = if (shifted) upper else lower
        val accents = language.accents[char.lowercaseChar()]?.map { it.toString() } ?: emptyList()
        val extra = if (shifted) accents.map { it.uppercase() } else accents
        return Key(
            label = visible,
            text = if (shifted) upper else lower,
            shiftedText = upper,
            shiftedLabel = upper,
            alt = extra,
            accent = extra.isNotEmpty()
        )
    }

    private fun bottomRow(
        settings: Settings,
        language: Language,
        layer: Layer,
        shifted: Boolean,
        showLanguageKey: Boolean
    ): List<Key> {
        val keys = ArrayList<Key>()

        keys += Key(
            label = if (layer == Layer.LETTERS) "123" else "ABC",
            action = if (layer == Layer.LETTERS) Action.LAYER_NUMBERS else Action.LAYER_LETTERS,
            weight = 1.5f
        )

        if (showLanguageKey) {
            keys += Key("", action = Action.LANGUAGE, glyph = Glyph.GLOBE, weight = 1.1f)
        }

        keys += Key("", action = Action.CLIPBOARD, glyph = Glyph.CLIPBOARD, weight = 1.1f)
        keys += Key("", action = Action.EMOJI, glyph = Glyph.EMOJI, weight = 1.1f, alt = listOf(":-)", ":-D", "<3", ";)"))

        val comma = Key(
            label = if (app.limbo.keyboard.core.I18n.isRussian) "," else ",",
            text = ",",
            weight = 0.9f,
            alt = listOf(";", ":", "'", "\"")
        )
        keys += comma

        keys += Key(
            label = "space",
            text = " ",
            action = Action.SPACE,
            weight = 4.4f,
            glyph = Glyph.SPACE
        )

        keys += Key(".", text = ".", weight = 0.9f, alt = listOf("!", "?", "…", "»"))

        keys += Key("", action = Action.ENTER, glyph = Glyph.ENTER, weight = 1.9f)

        return keys
    }

    private fun symbolRows(pages: List<String>, layer: Layer): List<Row> {
        val rows = ArrayList<Row>()
        pages.forEach { page ->
            val keys = page.map { char ->
                val alternatives = Symbols.alternativesFor(char).map { it.toString() }
                Key(char.toString(), text = char.toString(), alt = alternatives)
            }
            rows += Row(keys, inset = if (keys.size <= 9) 0.5f else 0f)
        }

        val switchLabel = if (layer == Layer.NUMBERS) "#+=" else "123"
        val switchAction = if (layer == Layer.NUMBERS) Action.LAYER_SYMBOLS else Action.LAYER_NUMBERS

        val bottom = listOf(
            Key(switchLabel, action = switchAction, weight = 1.5f),
            Key("ABC", action = Action.LAYER_LETTERS, weight = 1.5f),
            Key("", action = Action.CLIPBOARD, glyph = Glyph.CLIPBOARD, weight = 1.1f),
            Key(" ", text = " ", action = Action.SPACE, weight = 3.6f, glyph = Glyph.SPACE),
            Key(",", text = ",", weight = 0.9f, alt = listOf(";", ":", "-")),
            Key("", action = Action.BACKSPACE, glyph = Glyph.BACKSPACE, weight = 1.4f, repeatable = true),
            Key("", action = Action.ENTER, glyph = Glyph.ENTER, weight = 1.9f)
        )
        rows += Row(bottom)
        return rows
    }
}
