package android.app

open class Service : android.content.Context() {
    open fun onCreate() {}
    open fun onDestroy() {}
    lateinit var application: Application
}
