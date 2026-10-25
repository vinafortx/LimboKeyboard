package app.limbo.keyboard.ui.kit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import app.limbo.keyboard.core.Colors
import app.limbo.keyboard.core.Dp
import app.limbo.keyboard.theme.Theme

/**
 * Background of the keyboard window: a soft sheet that lifts off the app behind it, with the top
 * corners rounded so the keyboard reads as a floating object rather than a system bar.
 */
class KeyboardSurfaceDrawable(private val ctx: Context, var theme: Theme) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val grainPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() == 0 || b.height() == 0) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val radius = Dp.dp(ctx, theme.corner)

        rect.set(0f, 0f, w, h + radius)
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)

        // Ambient shadow above the sheet: a soft seam, not a drop shadow.
        paint.shader = null
        paint.color = Colors.alpha(Color.BLACK, if (theme.dark) 0.16f else 0.09f)
        rect.set(0f, -Dp.dp(ctx, 2f), w, h)
        canvas.drawRoundRect(rect, radius, radius, paint)

        paint.shader = android.graphics.LinearGradient(
            0f, 0f, 0f, h,
            Colors.alpha(theme.panelTop, 0.99f), Colors.alpha(theme.panelBottom, 0.99f),
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null

        // A whisper of light from above; the sheet itself stays neutral.
        paint.color = Colors.alpha(Color.WHITE, if (theme.dark) 0.022f else 0.06f)
        rect.set(0f, 0f, w, h * 0.5f)
        canvas.save()
        canvas.clipPath(path)
        canvas.drawRect(rect, paint)
        canvas.restore()

        // Grain
        grainPaint.shader = android.graphics.BitmapShader(Glass.grain(ctx), android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
        grainPaint.alpha = 6
        canvas.save()
        canvas.clipPath(path)
        canvas.drawRect(0f, 0f, w, h, grainPaint)
        canvas.restore()

        // One pixel of light along the top edge
        paint.color = Colors.alpha(Color.WHITE, if (theme.dark) 0.07f else 0.45f)
        rect.set(radius * 0.6f, 0.5f, w - radius * 0.6f, 1.5f)
        canvas.drawRoundRect(rect, 1f, 1f, paint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
