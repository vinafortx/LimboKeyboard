package app.limbo.keyboard.core

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.util.Locale

/**
 * Everything the user reads. Text lives here (not in resources) because the interface is drawn
 * on a canvas: keeping both languages side by side makes it obvious that neither is an
 * afterthought, and lets layout code pick a string without a context lookup.
 */
object I18n {
    val isRussian: Boolean
        get() = Locale.getDefault().language == "ru"

    fun t(ru: String, en: String): String = if (isRussian) ru else en
}

/** Typographic scale shared by the whole interface. */
object Type {
    const val DISPLAY = 34f
    const val TITLE = 24f
    const val HEADLINE = 19f
    const val BODY = 16f
    const val CALLOUT = 14.5f
    const val CAPTION = 12.5f
    const val MICRO = 10.5f

    private val cache = HashMap<String, Typeface>()

    fun face(weight: Int): Typeface {
        val key = "w$weight"
        return cache.getOrPut(key) {
            when {
                weight >= 800 -> Typeface.create("sans-serif-black", Typeface.NORMAL)
                weight >= 700 -> Typeface.create("sans-serif-medium", Typeface.BOLD)
                weight >= 600 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
                weight >= 500 -> Typeface.create("sans-serif", Typeface.NORMAL)
                weight >= 400 -> Typeface.create("sans-serif-light", Typeface.NORMAL)
                else -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
            }
        }
    }

    fun paint(color: Int, weight: Int = 500, sizePx: Float = 40f, tracking: Float = 0f): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.color = color
            typeface = face(weight)
            textSize = sizePx
            letterSpacing = tracking
        }

    fun mono(color: Int, sizePx: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize = sizePx
            letterSpacing = 0.06f
            fontFeatureSettings = "tnum"
        }
}

/**
 * Text drawing with manual tracking so the visual rhythm is identical on every API level.
 */
object Tx {

    fun measure(paint: Paint, text: String, trackingPx: Float = 0f): Float {
        if (text.isEmpty()) return 0f
        var width = 0f
        for (i in text.indices) {
            width += paint.measureText(text, i, i + 1)
            if (i != text.lastIndex) width += trackingPx
        }
        return width
    }

    fun draw(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        paint: Paint,
        trackingPx: Float = 0f,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        if (text.isEmpty()) return
        val width = measure(paint, text, trackingPx)
        var cursor = when (align) {
            Paint.Align.CENTER -> x - width / 2f
            Paint.Align.RIGHT -> x - width
            else -> x
        }
        for (i in text.indices) {
            canvas.drawText(text, i, i + 1, cursor, baseline, paint)
            cursor += paint.measureText(text, i, i + 1) + trackingPx
        }
    }

    fun drawCenter(canvas: Canvas, text: String, cx: Float, cy: Float, paint: Paint, trackingPx: Float = 0f) {
        val metrics = paint.fontMetrics
        val baseline = cy - (metrics.ascent + metrics.descent) / 2f
        draw(canvas, text, cx, baseline, paint, trackingPx, Paint.Align.CENTER)
    }

    fun drawWrapped(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        maxWidth: Float,
        paint: Paint,
        lineGap: Float,
        trackingPx: Float = 0f,
        maxLines: Int = Int.MAX_VALUE
    ): Float {
        val words = text.split(' ')
        var line = StringBuilder()
        var y = baseline
        var lines = 0
        fun flush() {
            draw(canvas, line.toString(), x, y, paint, trackingPx)
            line = StringBuilder()
            y += paint.textSize + lineGap
            lines++
        }
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (measure(paint, candidate, trackingPx) > maxWidth && line.isNotEmpty()) {
                flush()
                if (lines >= maxLines) return y
                line.append(word)
            } else {
                if (line.isNotEmpty()) line.append(' ')
                line.append(word)
            }
        }
        if (line.isNotEmpty() && lines < maxLines) flush()
        return y
    }
}
