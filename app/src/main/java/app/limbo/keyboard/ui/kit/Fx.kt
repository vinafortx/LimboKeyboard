package app.limbo.keyboard.ui.kit

import android.view.View
import app.limbo.keyboard.core.Clock
import app.limbo.keyboard.core.Dp
import app.limbo.keyboard.core.MotionStyle
import app.limbo.keyboard.core.Spring

/** Small spring-driven flourishes for ordinary views (not only the hand-drawn ones). */
object Fx {

    fun springValue(
        view: View,
        from: Float,
        to: Float,
        style: MotionStyle = MotionStyle.BALANCED,
        onFrame: (View, Float) -> Unit,
        onEnd: (() -> Unit)? = null
    ) {
        val spring = Spring(style.stiffness, style.damping, 1f, from, to)
        lateinit var tick: (Float) -> Unit
        tick = { dt ->
            spring.advance(dt)
            onFrame(view, spring.value)
            if (!spring.isActive) {
                Clock.unsubscribe(tick)
                onEnd?.invoke()
            }
        }
        Clock.subscribe(tick)
    }

    /** Slides a view up from [offsetDp] while fading it in. */
    fun enter(view: View, offsetDp: Float = 18f, delayMs: Long = 0L, style: MotionStyle = MotionStyle.BALANCED) {
        val offset = Dp.dp(view.context, offsetDp)
        view.alpha = 0f
        view.translationY = offset
        val start = {
            springValue(view, offset, 0f, style, onFrame = { v, value -> v.translationY = value })
            springValue(view, 0f, 1f, MotionStyle.CRISP, onFrame = { v, value -> v.alpha = value.coerceIn(0f, 1f) })
        }
        if (delayMs > 0) Clock.after(delayMs, start) else start()
    }

    fun exit(view: View, offsetDp: Float = 24f, onEnd: (() -> Unit)? = null) {
        val offset = Dp.dp(view.context, offsetDp)
        springValue(view, 0f, offset, MotionStyle.CRISP, onFrame = { v, value -> v.translationY = value })
        springValue(view, 1f, 0f, MotionStyle.CRISP, onFrame = { v, value -> v.alpha = value.coerceIn(0f, 1f) }, onEnd = { onEnd?.invoke() })
    }
}
