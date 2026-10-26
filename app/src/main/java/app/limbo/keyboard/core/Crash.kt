package app.limbo.keyboard.core

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last unexpected failure on disk and lets the interface step over smaller ones.
 *
 * Two jobs, both deliberately small:
 *
 *  - [install] remembers the trace of a fatal failure, so the next launch can offer the user a way
 *    back and an address to send the details to, instead of a blank screen and a shrug;
 *  - [guard] runs a piece of screen building so a failure in one card cannot take the whole app
 *    down with it. What was drawn before the failure stays on screen.
 */
object Crash {

    private const val FILE = "last-failure.txt"
    private const val LIMIT = 16_000
    private val lock = Any()

    fun install(context: Context) {
        val app = context.applicationContext
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current is Recorder) return
        Thread.setDefaultUncaughtExceptionHandler(Recorder(app, current))
    }

    private class Recorder(
        private val app: Context,
        private val next: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            remember(app, error)
            // The platform handler has to run: without it the process is left half dead.
            next?.uncaughtException(thread, error)
        }
    }

    fun remember(context: Context, error: Throwable) {
        runCatching {
            val text = buildString {
                append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                append(" · Android ").append(Build.VERSION.RELEASE)
                append(" · API ").append(Build.VERSION.SDK_INT)
                append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                append('\n')
                append(error.stackTraceToString().take(LIMIT))
            }
            val app = context.applicationContext
            synchronized(lock) {
                File(app.filesDir, FILE).writeText(text)
                // A second copy where a file manager or a cable can reach it, for the times the
                // interface itself is what is broken.
                runCatching { app.getExternalFilesDir(null)?.let { dir -> File(dir, FILE).writeText(text) } }
            }
        }
    }

    /** The stored report, if any. Reading it clears it, so it is offered exactly once. */
    fun take(context: Context): String? = runCatching {
        val file = File(context.applicationContext.filesDir, FILE)
        if (!file.isFile) return null
        val text = file.readText()
        file.delete()
        text.ifBlank { null }
    }.getOrNull()

    /**
     * Builds a part of the interface, remembering a failure instead of throwing it further.
     * Returns null when [block] failed.
     */
    inline fun <T : Any> guard(context: Context, block: () -> T?): T? =
        try {
            block()
        } catch (t: Throwable) {
            remember(context, t)
            null
        }
}
