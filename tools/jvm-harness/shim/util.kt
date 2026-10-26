package android.util

class DisplayMetrics {
    @JvmField var density: Float = 3f
    @JvmField var scaledDensity: Float = 3f
    @JvmField var densityDpi: Int = 480
    @JvmField var widthPixels: Int = 1080
    @JvmField var heightPixels: Int = 2340
    @JvmField var xdpi: Float = 480f
    @JvmField var ydpi: Float = 480f
}

object TypedValue {
    const val COMPLEX_UNIT_DIP = 1
    const val COMPLEX_UNIT_SP = 2
    const val COMPLEX_UNIT_PX = 0
    @JvmStatic fun applyDimension(unit: Int, value: Float, metrics: DisplayMetrics): Float = when (unit) {
        COMPLEX_UNIT_DIP -> value * metrics.density
        COMPLEX_UNIT_SP -> value * metrics.scaledDensity
        else -> value
    }
}

class SparseArray<T> {
    private val map = HashMap<Int, T>()
    fun put(k: Int, v: T) { map[k] = v }
    fun get(k: Int): T? = map[k]
    fun size(): Int = map.size
}
