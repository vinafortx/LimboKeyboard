package app.limbo.keyboard.ui.kit

import android.content.Context
import android.graphics.Canvas
import android.view.View
import app.limbo.keyboard.core.Clock
import app.limbo.keyboard.core.Spring

/**
 * A view that owns a few springs and keeps itself repainting only while something moves.
 * Motion stops dead when the view leaves the window, which keeps scrolling cheap.
 */
abstract class SpringyView(context: Context) : View(context) {

    private val springs = ArrayList<Spring>()
    private val active = ArrayList<Spring>()
    private val tick: (Float) -> Unit = { dt -> step(dt) }
    private var running = false

    /** Drops every spring, used when a whole new set of keys is created. */
    protected fun clearSprings() {
        springs.clear()
    }

    protected fun spring(stiffness: Float = 340f, damping: Float = 26f, start: Float = 0f): Spring {
        val s = Spring(stiffness, damping, 1f, start)
        springs.add(s)
        return s
    }

    /** Ensures the frame loop is running. Call after any setTarget. */
    protected fun wake() {
        if (running) return
        running = true
        Clock.subscribe(tick)
    }

    protected fun sleepIfIdle() {
        val moving = springs.any { it.isActive }
        if (!moving && running) {
            running = false
            Clock.unsubscribe(tick)
        }
    }

    private fun step(dt: Float) {
        var changed = false
        for (s in springs) {
            if (s.isActive) {
                s.advance(dt)
                changed = true
            }
        }
        if (changed) {
            onMotionStep(dt)
            invalidate()
        }
        sleepIfIdle()
    }

    protected open fun onMotionStep(dt: Float) {}

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (springs.any { it.isActive }) wake()
    }

    override fun onDetachedFromWindow() {
        if (running) {
            running = false
            Clock.unsubscribe(tick)
        }
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawContent(canvas)
    }

    abstract fun drawContent(canvas: Canvas)
}
