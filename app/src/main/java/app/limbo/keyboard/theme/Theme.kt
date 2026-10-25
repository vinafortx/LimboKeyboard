package app.limbo.keyboard.theme

import android.graphics.Color
import app.limbo.keyboard.core.Colors
import org.json.JSONArray
import org.json.JSONObject

/** How the keys themselves are painted. */
enum class KeyStyle(val labelRu: String, val labelEn: String) {
    GLASS("Матовое", "Frosted"),
    SOFT("Мягкие", "Soft"),
    FILLED("Плотные", "Filled"),
    OUTLINE("Контур", "Outline"),
    MINIMAL("Минимал", "Minimal"),
    CRYSTAL("Кристалл", "Crystal")
}

/** Where the suggestion strip sits. */
enum class BarStyle(val labelRu: String, val labelEn: String) {
    FLOATING("Парящая", "Floating"),
    ATTACHED("Встроенная", "Attached"),
    HIDDEN("Скрыть", "Hidden")
}

/**
 * A complete visual identity of the keyboard. Every field is user-editable — presets are just
 * starting points, never a ceiling.
 */
data class Theme(
    val id: String,
    val name: String,
    val dark: Boolean,

    // Wallpaper used behind the translucent interface
    val wallTop: Int,
    val wallBottom: Int,
    val wallGlow: Int,

    // Panel
    val panelTop: Int,
    val panelBottom: Int,
    val panelBlur: Float,

    // Keys
    val keyTop: Int,
    val keyBottom: Int,
    val keyPressedTop: Int,
    val keyPressedBottom: Int,
    val keyStroke: Float,

    // Type
    val text: Int,
    val textSecondary: Int,
    val accent: Int,
    val accentText: Int,

    // Geometry, in dp
    val corner: Float,
    val keyCorner: Float,
    val gap: Float,
    val keyHeight: Float,
    val rowGap: Float,

    // Character
    val keyStyle: KeyStyle,
    val shadow: Float,
    val sheen: Float,
    val fontScale: Float,
    val tracking: Float,
    val barStyle: BarStyle = BarStyle.FLOATING,

    /** How milky the glass reads, 0..1. Higher means softer and more opaque. */
    val frost: Float = 0.55f
) {
    val isLight: Boolean get() = !dark

    /** Soft tint used for chip backgrounds and selection washes. */
    fun tint(strength: Float = 0.16f): Int = Colors.alpha(accent, strength)

    fun with(name: String = this.name, id: String = this.id): Theme = copy(name = name, id = id)

    companion object {
        const val DEFAULT_PRESET = "frost"
    }
}

object ThemeFactory {

    /**
     * Builds a balanced theme from four decisions: two wallpaper stops, an accent, and whether
     * the result should read light or dark. Everything else is derived so the 30 presets stay
     * consistent instead of drifting into thirty different design languages.
     */
    /** Re-derives a theme in the opposite light/dark mode, keeping the user's own geometry. */
    fun recolor(theme: Theme, dark: Boolean): Theme {
        val rebuilt = build(
            id = theme.id,
            name = theme.name,
            wallTop = theme.wallTop,
            wallBottom = theme.wallBottom,
            accent = theme.accent,
            dark = dark,
            glow = theme.wallGlow,
            style = theme.keyStyle,
            corner = theme.corner,
            keyCorner = theme.keyCorner,
            keyHeight = theme.keyHeight,
            gap = theme.gap,
            shadow = theme.shadow,
            sheen = theme.sheen,
            bar = theme.barStyle
        )
        return rebuilt.copy(
            fontScale = theme.fontScale,
            tracking = theme.tracking,
            keyStroke = theme.keyStroke,
            frost = theme.frost
        )
    }

    fun build(
        id: String,
        name: String,
        wallTop: Int,
        wallBottom: Int,
        accent: Int,
        dark: Boolean = true,
        glow: Int? = null,
        style: KeyStyle = KeyStyle.GLASS,
        corner: Float = 26f,
        keyCorner: Float = 13f,
        keyHeight: Float = 54f,
        gap: Float = 7f,
        shadow: Float = 0.32f,
        sheen: Float = 0.18f,
        bar: BarStyle = BarStyle.FLOATING
    ): Theme {
        val panelTop: Int
        val panelBottom: Int
        val keyTop: Int
        val keyBottom: Int
        val text: Int
        val secondary: Int

        // Frosted glass is neutral: the sheet is a milky, almost flat surface and the colour
        // of the wallpaper only faintly shows through. That keeps the interface calm instead of
        // turning every key into a rainbow gradient.
        val panelBase: Int
        if (dark) {
            panelBase = Colors.mix(Colors.mix(wallTop, wallBottom, 0.35f), Color.BLACK, 0.74f)
            panelTop = Colors.mix(panelBase, Color.WHITE, 0.05f)
            panelBottom = Colors.mix(panelBase, Color.WHITE, 0.03f)
            keyTop = Colors.mix(panelBase, Color.WHITE, 0.125f)
            keyBottom = Colors.mix(panelBase, Color.WHITE, 0.105f)
            text = Colors.alpha(Color.WHITE, 0.97f)
            secondary = Colors.alpha(Color.WHITE, 0.56f)
        } else {
            panelBase = Colors.mix(Colors.mix(wallTop, wallBottom, 0.35f), Color.WHITE, 0.55f)
            panelTop = Colors.mix(panelBase, Color.BLACK, 0.055f)
            panelBottom = Colors.mix(panelBase, Color.BLACK, 0.075f)
            keyTop = Colors.mix(panelBase, Color.WHITE, 0.75f)
            keyBottom = Colors.mix(panelBase, Color.WHITE, 0.66f)
            text = Colors.mix(panelBase, Color.BLACK, 0.84f)
            secondary = Colors.mix(panelBase, Color.BLACK, 0.48f)
        }

        // Pressing a key moves it towards the light, the way a translucent cap behaves.
        val pressedTop = if (dark) Colors.mix(keyTop, Color.WHITE, 0.1f) else Colors.mix(keyTop, Color.BLACK, 0.07f)
        val pressedBottom = if (dark) Colors.mix(keyBottom, Color.WHITE, 0.075f) else Colors.mix(keyBottom, Color.BLACK, 0.09f)

        return Theme(
            id = id,
            name = name,
            dark = dark,
            wallTop = wallTop,
            wallBottom = wallBottom,
            wallGlow = glow ?: Colors.alpha(accent, if (dark) 0.26f else 0.18f),
            panelTop = panelTop,
            panelBottom = panelBottom,
            panelBlur = 0.86f,
            keyTop = keyTop,
            keyBottom = keyBottom,
            keyPressedTop = pressedTop,
            keyPressedBottom = pressedBottom,
            keyStroke = 0.55f,
            text = text,
            textSecondary = secondary,
            accent = accent,
            accentText = Colors.on(accent),
            corner = corner,
            keyCorner = keyCorner,
            gap = gap,
            keyHeight = keyHeight,
            rowGap = gap,
            keyStyle = style,
            shadow = shadow,
            sheen = sheen,
            fontScale = 1f,
            tracking = 0.008f,
            barStyle = bar,
            frost = if (dark) 0.58f else 0.66f
        )
    }
}

/**
 * Presets. They are grouped by mood so the picker never feels like an endless grid: each row is
 * a family and every family works in both light and dark rooms.
 */
object Themes {

    private class Seed(
        val id: String,
        val nameRu: String,
        val nameEn: String,
        val top: String,
        val bottom: String,
        val accent: String,
        val dark: Boolean = true,
        val style: KeyStyle = KeyStyle.GLASS,
        val bar: BarStyle = BarStyle.FLOATING
    )

    private val seeds = listOf(
        // --- glass, cool
        Seed("frost", "Иней", "Frost", "#0E1630", "#29406B", "#8FB3FF"),
        Seed("glacier", "Ледник", "Glacier", "#0B1B24", "#1D4A5E", "#6FE3E1"),
        Seed("midnight", "Полночь", "Midnight", "#08090F", "#1A1F33", "#7C8CFF"),
        Seed("deep-ocean", "Глубина", "Deep Ocean", "#04121F", "#0B3B57", "#4EC3FF"),
        Seed("nebula", "Туманность", "Nebula", "#150D2B", "#3B2160", "#C08BFF"),
        Seed("graphite", "Графит", "Graphite", "#101114", "#2C3038", "#C7CCD8"),
        Seed("arctic", "Арктика", "Arctic", "#102A33", "#2F6E7A", "#9FF3F0"),
        Seed("ink", "Чернила", "Ink", "#0A0B10", "#161A24", "#5B8CFF"),

        // --- warm
        Seed("sunset", "Закат", "Sunset", "#2A1220", "#7A2E3F", "#FF9E7A"),
        Seed("ember", "Угли", "Ember", "#1F0D0A", "#5A2320", "#FF7A59"),
        Seed("amber", "Янтарь", "Amber", "#20150A", "#4E3216", "#FFC46B"),
        Seed("rose-quartz", "Розовый кварц", "Rose Quartz", "#2B1420", "#6B2A46", "#FF9CC6"),
        Seed("terra", "Терракота", "Terracotta", "#241310", "#5C2E24", "#F09B7A"),
        Seed("mulled", "Глинтвейн", "Mulled", "#1B0E14", "#472030", "#E58FA0"),

        // --- green
        Seed("mint", "Мята", "Mint", "#0C1F1A", "#1E4A3C", "#7CF0C0"),
        Seed("forest", "Лес", "Forest", "#0B1710", "#20402A", "#A8E86E"),
        Seed("jade", "Нефрит", "Jade", "#0A1B18", "#1C4438", "#63E6BE"),
        Seed("olive", "Олива", "Olive", "#14170E", "#333D1F", "#D2E27A"),

        // --- specials
        Seed("aurora", "Аврора", "Aurora", "#0B1226", "#2F1B4D", "#7AF0D8", style = KeyStyle.GLASS),
        Seed("neon-dusk", "Неон", "Neon Dusk", "#120A1E", "#2B0F3F", "#FF6BD6", style = KeyStyle.SOFT),
        Seed("terminal", "Терминал", "Terminal", "#060A07", "#0C1710", "#54F08A", style = KeyStyle.OUTLINE),
        Seed("blueprint", "Чертёж", "Blueprint", "#081524", "#12365C", "#8ECBFF", style = KeyStyle.OUTLINE),
        Seed("paper", "Бумага", "Paper", "#F2EFE8", "#E3DDD1", "#8A7B63", dark = false, style = KeyStyle.SOFT),
        Seed("porcelain", "Фарфор", "Porcelain", "#F6F7FB", "#E2E6F0", "#5C7CFF", dark = false, style = KeyStyle.GLASS),
        Seed("sakura", "Сакура", "Sakura", "#FDF3F5", "#F3DCE2", "#E8748F", dark = false, style = KeyStyle.GLASS),
        Seed("meadow", "Луг", "Meadow", "#F2F8F0", "#DCEBDA", "#4E9A57", dark = false, style = KeyStyle.SOFT),
        Seed("sky", "Небо", "Sky", "#EEF5FF", "#D7E6FB", "#3E7BF0", dark = false, style = KeyStyle.GLASS),
        Seed("linen", "Лён", "Linen", "#F7F5F0", "#E8E3D8", "#A08C6A", dark = false, style = KeyStyle.MINIMAL),
        Seed("graphite-light", "Светлый графит", "Light Graphite", "#F4F5F7", "#E1E4EA", "#4C5566", dark = false, style = KeyStyle.FILLED),
        Seed("cream", "Крем", "Cream", "#FFFAF0", "#F2E7D3", "#C8873E", dark = false, style = KeyStyle.SOFT)
    )

    val presets: List<Theme> = seeds.map { seed ->
        ThemeFactory.build(
            id = seed.id,
            name = if (app.limbo.keyboard.core.I18n.isRussian) seed.nameRu else seed.nameEn,
            wallTop = Colors.parse(seed.top, Color.BLACK),
            wallBottom = Colors.parse(seed.bottom, Color.DKGRAY),
            accent = Colors.parse(seed.accent, Color.WHITE),
            dark = seed.dark,
            style = seed.style,
            bar = seed.bar
        )
    }

    fun byId(id: String?): Theme = presets.firstOrNull { it.id == id } ?: preset(Theme.DEFAULT_PRESET)

    fun preset(id: String): Theme = presets.firstOrNull { it.id == id } ?: presets.first()

    /** Name in both languages, resolved lazily so a locale change is picked up. */
    fun presetName(id: String): String {
        val index = seeds.indexOfFirst { it.id == id }
        if (index < 0) return id
        val seed = seeds[index]
        return if (app.limbo.keyboard.core.I18n.isRussian) seed.nameRu else seed.nameEn
    }
}

object ThemeCodec {

    fun encode(theme: Theme): String = JSONObject().apply {
        put("id", theme.id)
        put("name", theme.name)
        put("dark", theme.dark)
        put("wallTop", theme.wallTop)
        put("wallBottom", theme.wallBottom)
        put("wallGlow", theme.wallGlow)
        put("panelTop", theme.panelTop)
        put("panelBottom", theme.panelBottom)
        put("panelBlur", theme.panelBlur.toDouble())
        put("keyTop", theme.keyTop)
        put("keyBottom", theme.keyBottom)
        put("keyPressedTop", theme.keyPressedTop)
        put("keyPressedBottom", theme.keyPressedBottom)
        put("keyStroke", theme.keyStroke.toDouble())
        put("text", theme.text)
        put("textSecondary", theme.textSecondary)
        put("accent", theme.accent)
        put("accentText", theme.accentText)
        put("corner", theme.corner.toDouble())
        put("keyCorner", theme.keyCorner.toDouble())
        put("gap", theme.gap.toDouble())
        put("keyHeight", theme.keyHeight.toDouble())
        put("rowGap", theme.rowGap.toDouble())
        put("keyStyle", theme.keyStyle.name)
        put("shadow", theme.shadow.toDouble())
        put("sheen", theme.sheen.toDouble())
        put("fontScale", theme.fontScale.toDouble())
        put("tracking", theme.tracking.toDouble())
        put("barStyle", theme.barStyle.name)
        put("frost", theme.frost.toDouble())
    }.toString()

    fun decode(json: String): Theme? = try {
        val o = JSONObject(json)
        val fallback = ThemeFactory.build(
            id = o.optString("id", "custom"),
            name = o.optString("name", "Custom"),
            wallTop = o.optInt("wallTop", 0xFF101828.toInt()),
            wallBottom = o.optInt("wallBottom", 0xFF243A5E.toInt()),
            accent = o.optInt("accent", 0xFF8FB3FF.toInt()),
            dark = o.optBoolean("dark", true)
        )
        Theme(
            id = o.optString("id", fallback.id),
            name = o.optString("name", fallback.name),
            dark = o.optBoolean("dark", fallback.dark),
            wallTop = o.optInt("wallTop", fallback.wallTop),
            wallBottom = o.optInt("wallBottom", fallback.wallBottom),
            wallGlow = o.optInt("wallGlow", fallback.wallGlow),
            panelTop = o.optInt("panelTop", fallback.panelTop),
            panelBottom = o.optInt("panelBottom", fallback.panelBottom),
            panelBlur = o.optDouble("panelBlur", fallback.panelBlur.toDouble()).toFloat(),
            keyTop = o.optInt("keyTop", fallback.keyTop),
            keyBottom = o.optInt("keyBottom", fallback.keyBottom),
            keyPressedTop = o.optInt("keyPressedTop", fallback.keyPressedTop),
            keyPressedBottom = o.optInt("keyPressedBottom", fallback.keyPressedBottom),
            keyStroke = o.optDouble("keyStroke", fallback.keyStroke.toDouble()).toFloat(),
            text = o.optInt("text", fallback.text),
            textSecondary = o.optInt("textSecondary", fallback.textSecondary),
            accent = o.optInt("accent", fallback.accent),
            accentText = o.optInt("accentText", fallback.accentText),
            corner = o.optDouble("corner", fallback.corner.toDouble()).toFloat(),
            keyCorner = o.optDouble("keyCorner", fallback.keyCorner.toDouble()).toFloat(),
            gap = o.optDouble("gap", fallback.gap.toDouble()).toFloat(),
            keyHeight = o.optDouble("keyHeight", fallback.keyHeight.toDouble()).toFloat(),
            rowGap = o.optDouble("rowGap", fallback.rowGap.toDouble()).toFloat(),
            keyStyle = runCatching { KeyStyle.valueOf(o.optString("keyStyle", "GLASS")) }.getOrDefault(KeyStyle.GLASS),
            shadow = o.optDouble("shadow", fallback.shadow.toDouble()).toFloat(),
            sheen = o.optDouble("sheen", fallback.sheen.toDouble()).toFloat(),
            fontScale = o.optDouble("fontScale", 1.0).toFloat(),
            tracking = o.optDouble("tracking", fallback.tracking.toDouble()).toFloat(),
            barStyle = runCatching { BarStyle.valueOf(o.optString("barStyle", "FLOATING")) }.getOrDefault(BarStyle.FLOATING),
            frost = o.optDouble("frost", fallback.frost.toDouble()).toFloat()
        )
    } catch (_: Throwable) {
        null
    }
}

/**
 * Keeps presets and the user's own themes in one list. Custom themes live in preferences as
 * plain JSON so they survive updates and can be shared by copy-paste.
 */
class ThemeStore(private val settings: app.limbo.keyboard.core.Settings, context: android.content.Context) {

    private val sp = context.applicationContext.getSharedPreferences("limbo_themes", android.content.Context.MODE_PRIVATE)

    var current: Theme
        get() {
            val json = sp.getString("current", null) ?: return Themes.preset(Theme.DEFAULT_PRESET)
            return ThemeCodec.decode(json) ?: Themes.preset(Theme.DEFAULT_PRESET)
        }
        set(value) {
            sp.edit().putString("current", ThemeCodec.encode(value)).apply()
        }

    fun customThemes(): List<Theme> {
        val raw = sp.getString("custom", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { ThemeCodec.decode(array.getJSONObject(it).toString()) }
        }.getOrDefault(emptyList())
    }

    fun all(): List<Theme> = Themes.presets + customThemes()

    fun find(id: String): Theme? = all().firstOrNull { it.id == id }

    fun save(theme: Theme) {
        val list = customThemes().filterNot { it.id == theme.id } + theme
        val array = JSONArray()
        list.forEach { array.put(JSONObject(ThemeCodec.encode(it))) }
        sp.edit().putString("custom", array.toString()).apply()
        current = theme
    }

    fun delete(id: String) {
        val list = customThemes().filterNot { it.id == id }
        val array = JSONArray()
        list.forEach { array.put(JSONObject(ThemeCodec.encode(it))) }
        sp.edit().putString("custom", array.toString()).apply()
        if (current.id == id) current = Themes.preset(Theme.DEFAULT_PRESET)
    }

    fun selectPreset(id: String) {
        current = Themes.preset(id)
    }

    fun reset() {
        sp.edit().clear().apply()
    }
}
