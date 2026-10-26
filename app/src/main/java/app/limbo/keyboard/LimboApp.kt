package app.limbo.keyboard

import android.app.Application
import app.limbo.keyboard.core.Crash
import app.limbo.keyboard.core.Settings
import app.limbo.keyboard.ime.ClipboardStore
import app.limbo.keyboard.ime.Dictionary

/**
 * Holds the few long-lived objects the app needs. Everything is lazy so opening the keyboard
 * never pays for the home screen and vice versa.
 */
class LimboApp : Application() {

    val settings: Settings by lazy { Settings(this) }
    val clipboard: ClipboardStore by lazy { ClipboardStore(this, settings) }
    val dictionary: Dictionary by lazy { Dictionary(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Crash.install(this)
    }

    companion object {
        lateinit var instance: LimboApp
            private set
    }
}
