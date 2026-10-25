package app.limbo.keyboard.core

import android.view.Choreographer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Analytic critically-under-damped spring used for every piece of motion in the app.
 *
 * Springs are fixed timestep independent: [advance] receives elapsed seconds and keeps the
 * remaining motion in [velocity], so a dropped frame never makes the animation run long.
 */
class Spring(
    var stiffness: Float = 320f,
    var damping: Float = 26f,
    var mass: Float = 1f,
    start: Float = 0f,
    target: Float = 0f
) {
    var value: Float = start
        private set
    var target: Float = target
        private set
    var velocity: Float = 0f
        private set

    val isActive: Boolean
        get() = abs(velocity) > 0.01f || abs(target - value) > 0.001f

    fun setTarget(newTarget: Float) {
        target = newTarget
    }

    fun jumpTo(newValue: Float, target: Float = newValue) {
        value = newValue
        this.target = target
        velocity = 0f
    }

    fun nudge(impulse: Float) {
        velocity += impulse
    }

    fun configure(stiffness: Float = this.stiffness, damping: Float = this.damping) {
        this.stiffness = stiffness
        this.damping = damping
    }

    /** Advances the simulation; returns true while still moving. */
    fun advance(dtSeconds: Float): Boolean {
        if (!isActive) {
            value = target
            velocity = 0f
            return false
        }
        var remaining = dtSeconds.coerceIn(0f, 0.05f)
        val step = 1f / 240f
        while (remaining > 0f) {
            val h = if (remaining > step) step else remaining
            val force = -stiffness * (value - target) - damping * velocity
            velocity += (force / mass) * h
            value += velocity * h
            remaining -= h
        }
        if (!isActive) {
            value = target
            velocity = 0f
            return false
        }
        return true
    }

    /** One-shot settle used for unbounded values such as scroll offsets. */
    fun settle(dtSeconds: Float): Float {
        advance(dtSeconds)
        return value
    }

    companion object {
        fun snappy(start: Float = 0f) = Spring(420f, 30f, 1f, start)
        fun gentle(start: Float = 0f) = Spring(220f, 22f, 1f, start)
        fun bouncy(start: Float = 0f) = Spring(300f, 16f, 1f, start)
        fun stiff(start: Float = 0f) = Spring(700f, 38f, 1f, start)
    }
}

/** Spring presets exposed to the user as an "animation feel" setting. */
enum class MotionStyle(val stiffness: Float, val damping: Float, val labelRu: String, val labelEn: String) {
    CRISP(560f, 38f, "Собранные", "Crisp"),
    BALANCED(340f, 27f, "Плавные", "Balanced"),
    BOUNCY(300f, 15f, "Живые", "Lively"),
    CALM(180f, 24f, "Спокойные", "Calm");

    fun spring(start: Float = 0f): Spring = Spring(stiffness, damping, 1f, start)

    fun spring(from: Float, to: Float): Spring = Spring(stiffness, damping, 1f, from, to)
}

/**
 * Process-wide frame clock. Views subscribe while they have motion in flight instead of
 * running their own animator, which keeps every spring on the same vsync and lets the whole
 * interface settle in one pass.
 */
object Clock {
    private val listeners = LinkedHashSet<(Float) -> Unit>()
    private var scheduled = false
    private var lastFrameNs = 0L
    var reducedMotion: Boolean = false

    private val callback = Choreographer.FrameCallback { frameTimeNanos ->
        scheduled = false
        val dt = if (lastFrameNs == 0L) 1f / 60f else (frameTimeNanos - lastFrameNs) / 1_000_000_000f
        lastFrameNs = frameTimeNanos
        val snapshot = listeners.toList()
        snapshot.forEach { it(dt.coerceIn(0.001f, 0.05f)) }
        if (listeners.isNotEmpty()) schedule()
        else lastFrameNs = 0L
    }

    fun subscribe(listener: (Float) -> Unit) {
        listeners.add(listener)
        if (listeners.size == 1) lastFrameNs = 0L
        schedule()
    }

    fun unsubscribe(listener: (Float) -> Unit) {
        listeners.remove(listener)
        if (listeners.isEmpty()) lastFrameNs = 0L
    }

    private fun schedule() {
        if (scheduled) return
        scheduled = true
        Choreographer.getInstance().postFrameCallback(callback)
    }

    /** Single delayed tick, used for UI flourishes that should not own a frame loop. */
    fun after(delayMs: Long, action: () -> Unit) {
        Choreographer.getInstance().postFrameCallbackDelayed({ action() }, delayMs)
    }
}

/**
 * Convenience base for animated numbers that only need a repaint on change.
 * Not a View — the owner decides when to invalidate.
 */
class Motion(val spring: Spring) {
    constructor(style: MotionStyle, start: Float = 0f) : this(style.spring(start))

    var value: Float
        get() = spring.value
        set(v) = spring.jumpTo(v)

    fun to(target: Float) = spring.setTarget(target)

    fun advance(dt: Float, onChange: (Float) -> Unit): Boolean {
        val before = spring.value
        val moving = spring.advance(dt)
        if (moving || before != spring.value) onChange(spring.value)
        return moving
    }
}

/** Damped oscillator used for haptic-like key "pops" where we need an impulse response. */
class Impulse(private val frequency: Float = 9f, private val dampingRatio: Float = 0.35f) {
    var value: Float = 0f
        private set
    private var velocity = 0f

    fun kick(amount: Float = 1f) {
        velocity += amount * 30f
    }

    fun advance(dt: Float): Float {
        val omega = 6.2831855f * frequency
        val zeta = dampingRatio
        val acceleration = -omega * omega * value - 2f * zeta * omega * velocity
        velocity += acceleration * dt
        value += velocity * dt
        if (abs(value) < 0.0008f && abs(velocity) < 0.005f) {
            value = 0f
            velocity = 0f
        }
        return value
    }

    val isActive: Boolean get() = value != 0f || velocity != 0f
}

/** Closed-form spring step used when a caller needs an exact value at time t. */
fun springAt(t: Float, from: Float, to: Float, stiffness: Float, damping: Float): Float {
    val omega0 = sqrt(stiffness)
    val zeta = damping / (2f * sqrt(stiffness))
    if (zeta >= 1f) return to + (from - to) * exp(-omega0 * t) * (1f + omega0 * t)
    val omegaD = omega0 * sqrt(1f - zeta * zeta)
    val envelope = exp(-zeta * omega0 * t)
    return to + (from - to) * envelope * (kotlin.math.cos(omegaD * t) + (zeta * omega0 / omegaD) * sin(omegaD * t))
}
