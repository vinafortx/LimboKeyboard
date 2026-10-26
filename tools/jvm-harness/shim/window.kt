package android.view

class Window {
    private var statusBar: Int = 0
    private var navBar: Int = 0
    var decorView: View? = null
    val insetsController: WindowInsetsController = WindowInsetsControllerImpl()
    fun setDecorFitsSystemWindows(fit: Boolean) {}
    fun setBackgroundBlurRadius(radius: Int) {}
    fun setDimAmount(amount: Float) {}
    fun setBackgroundDrawable(d: android.graphics.drawable.Drawable?) {}
    fun setBackgroundDrawableResource(resId: Int) {}
    fun setSoftInputMode(mode: Int) {}
    fun setFlags(w: Int, h: Int) {}
    fun addFlags(flags: Int) {}
    fun clearFlags(flags: Int) {}
    fun setLayout(w: Int, h: Int) {}
    fun <T : View> findViewById(id: Int): T? = null
    fun setStatusBarColor(color: Int) { statusBar = color }
    fun setNavigationBarColor(color: Int) { navBar = color }
    fun getStatusBarColor(): Int = statusBar
    fun getNavigationBarColor(): Int = navBar
    companion object {
        const val FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS = -2147483648
        const val FLAG_BLUR_BEHIND = 4
    }
}

class Choreographer private constructor() {
    fun interface FrameCallback { fun doFrame(frameTimeNanos: Long) }
    companion object {
        private val instance = Choreographer()
        @JvmStatic fun getInstance(): Choreographer = instance
    }

    private class Pending(val cb: FrameCallback, val due: Long)
    private val pending = ArrayList<Pending>()

    fun postFrameCallback(cb: FrameCallback) { pending.add(Pending(cb, 0L)) }
    fun postFrameCallbackDelayed(cb: FrameCallback, delayMillis: Long) {
        pending.add(Pending(cb, android.os.SimClock.now + delayMillis))
    }
    fun removeFrameCallback(cb: FrameCallback) { pending.removeAll { it.cb === cb } }

    fun pumpFrame(nowMs: Long, budget: Int): Int {
        val due = pending.filter { it.due <= nowMs }
        if (due.isEmpty()) return 0
        pending.removeAll(due)
        var ran = 0
        for (p in due) {
            if (ran >= budget) { pending.add(p); continue }
            p.cb.doFrame(nowMs * 1_000_000)
            ran++
        }
        return ran
    }
}
