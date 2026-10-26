package android.content.res

import android.util.DisplayMetrics

class Configuration

class Resources {
    private val metrics: DisplayMetrics = DisplayMetrics()
    fun getDisplayMetrics(): DisplayMetrics = metrics
    fun getString(id: Int): String = ""
    fun getDimension(id: Int): Float = 0f
    fun getDimensionPixelSize(id: Int): Int = 0
    fun getColor(id: Int): Int = 0
}
