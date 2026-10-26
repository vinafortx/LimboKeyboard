package android.os

class Bundle

object Build {
    const val MANUFACTURER = "shim"
    const val MODEL = "jvm"
    object VERSION { const val SDK_INT = 34; const val RELEASE = "14" }
    object VERSION_CODES { const val R = 30; const val S = 31; const val TIRAMISU = 33; const val UPSIDE_DOWN_CAKE = 34 }
}

class Looper private constructor() {
    companion object {
        private val main = Looper()
        @JvmStatic fun getMainLooper(): Looper = main
        @JvmStatic fun myLooper(): Looper? = main
    }
}

object SimClock {
    @JvmStatic var now: Long = 0L
}

class Handler(looper: Looper? = null) {
    init { all.add(this) }
    private class Task(val due: Long, val r: Runnable)
    private val tasks = ArrayList<Task>()

    fun post(r: Runnable): Boolean { tasks.add(Task(SimClock.now, r)); return true }
    fun postDelayed(r: Runnable, delay: Long): Boolean { tasks.add(Task(SimClock.now + delay, r)); return true }
    fun postAtTime(r: Runnable, time: Long): Boolean { tasks.add(Task(time, r)); return true }
    fun removeCallbacks(r: Runnable) { tasks.removeAll { it.r === r } }
    fun removeCallbacksAndMessages(token: Any?) { tasks.clear() }

    fun runDue(now: Long, budget: Int): Int {
        var ran = 0
        while (ran < budget) {
            val idx = tasks.indexOfFirst { it.due <= now }
            if (idx < 0) break
            val t = tasks.removeAt(idx)
            t.r.run()
            ran++
        }
        return ran
    }

    companion object {
        @JvmStatic val all = ArrayList<Handler>()
    }
}

object SystemClock {
    @JvmStatic fun uptimeMillis(): Long = SimClock.now
    @JvmStatic fun elapsedRealtime(): Long = SimClock.now
}

class VibrationEffect private constructor() {
    companion object {
        @JvmStatic fun createOneShot(ms: Long, amplitude: Int): VibrationEffect = VibrationEffect()
        @JvmStatic fun createWaveform(timings: LongArray, amplitudes: IntArray): VibrationEffect = VibrationEffect()
    }
}

open class Vibrator {
    fun hasVibrator(): Boolean = true
    fun vibrate(effect: VibrationEffect) {}
    fun vibrate(ms: Long) {}
    fun cancel() {}
}

class VibratorManager { val defaultVibrator: Vibrator get() = Vibrator() }
