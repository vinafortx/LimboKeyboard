package android.app

import android.content.Context
import android.os.Bundle
import android.view.Window

open class Application : Context() {
    open fun onCreate() {}
    open fun onTerminate() {}
}

open class Activity : Context() {
    open lateinit var application: Application
    private val windowRef: Window = Window()
    var visible: Boolean = false
    var isFinishingV = false

    open fun onCreate(savedInstanceState: Bundle?) {}
    open fun onStart() {}
    open fun onResume() {}
    open fun onPause() {}
    open fun onStop() {}
    open fun onDestroy() {}
    open fun onSaveInstanceState(outState: Bundle) {}
    private var contentRef: android.view.View? = null
    open fun setContentView(view: android.view.View) { contentRef = view }
    fun getContentView(): android.view.View? = contentRef
    open fun setTitle(title: CharSequence?) {}
    fun finish() { isFinishingV = true }
    fun isFinishing(): Boolean = isFinishingV
    fun runOnUiThread(action: Runnable) { action.run() }
    fun getWindow(): Window = windowRef
    fun requestWindowFeature(feature: Int): Boolean = true
    fun onBackPressed() {}
    fun recreate() {}
    override val applicationContext: Context get() = this
}


class Dialog : android.content.Context() {
    val window: android.view.Window = android.view.Window()
    fun setContentView(v: android.view.View) {}
    fun show() {}
    fun dismiss() {}
    fun isShowing(): Boolean = true
    fun setCanceledOnTouchOutside(b: Boolean) {}
}
