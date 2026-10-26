package harness

import android.graphics.Canvas
import android.os.Build
import android.view.View
import android.view.ViewGroup
import app.limbo.keyboard.LimboApp
import app.limbo.keyboard.ui.HomeActivity
import app.limbo.keyboard.ui.OnboardingActivity
import app.limbo.keyboard.ui.SettingsActivity
import app.limbo.keyboard.ui.ThemeStudioActivity
import app.limbo.keyboard.ime.LimboIME
import android.view.inputmethod.EditorInfo
import app.limbo.keyboard.core.Crash
import app.limbo.keyboard.ime.FallbackBoard
import android.text.InputType
import java.util.Locale

private fun <T> stage(name: String, block: () -> T): T? {
    println("- $name")
    return try {
        val v = block()
        println("   ok")
        v
    } catch (t: Throwable) {
        println("   FAIL ${t.javaClass.name}: ${t.message}")
        t.stackTrace.take(14).forEach { println("      at $it") }
        var c = t.cause
        while (c != null) {
            println("   caused by ${c.javaClass.name}: ${c.message}")
            c.stackTrace.take(8).forEach { println("      at $it") }
            c = c.cause
        }
        null
    }
}

var totalFrames = 0
var totalTasks = 0

private fun pumpFrames(count: Int, canvas: Canvas?) {
    val choreographer = android.view.Choreographer.getInstance()
    val handlers = android.os.Handler.all.toList()
    repeat(count) {
        android.os.SimClock.now += 16L
        val now = android.os.SimClock.now
        var guard = 0
        while (guard++ < 200) {
            var ran = choreographer.pumpFrame(now, 50)
            for (h in handlers) ran += h.runDue(now, 50)
            totalFrames = totalFrames
            totalTasks += ran
            if (ran == 0) break
        }
        totalFrames += 0
        if (canvas != null) {
            // repaint the tree like a real frame
        }
    }
}

private fun exercise(view: View, canvas: Canvas, depth: Int = 0) {
    view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                 View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.AT_MOST))
    view.layout(0, 0, 1080, view.measuredHeight.coerceAtLeast(2000))
    view.drawSelf(canvas)
}

private fun <T : android.app.Activity> run(name: String, app: LimboApp, make: () -> T, exerciseIt: Boolean): T? =
    stage(name) {
        val a = make()
        a.application = app
        a.onCreate(null)
        a.onResume()
        if (exerciseIt) {
            val cv = a.getContentView() ?: error("no content view")
            exercise(cv, Canvas())
            pumpFrames(180, null)
            exercise(cv, Canvas())
        }
        a.onDestroy()
        a
    }

fun main() {
    val exerciseIt = System.getenv("EXERCISE") == "1"
    println("sdk=${Build.VERSION.SDK_INT} exercise=$exerciseIt")

    val app = stage("LimboApp.onCreate") {
        val a = LimboApp()
        a.onCreate()
        a
    } ?: return

    for (locale in listOf(Locale("ru", "RU"), Locale("en", "US"))) {
        Locale.setDefault(locale)
        println("===== ${locale.language} =====")
        run("HomeActivity", app, { HomeActivity() }, exerciseIt)
        run("OnboardingActivity", app, { OnboardingActivity() }, exerciseIt)
        run("SettingsActivity", app, { SettingsActivity() }, exerciseIt)
        run("ThemeStudioActivity", app, { ThemeStudioActivity() }, exerciseIt)
    }
    println("===== IME =====")
    stage("LimboIME") {
        val ime = LimboIME()
        ime.application = app
        ime.onCreate()
        val view = ime.onCreateInputView()
        if (exerciseIt && view != null) exercise(view, Canvas())
        ime.onStartInputView(EditorInfo().apply { setInputType(InputType.TYPE_CLASS_TEXT) }, false)
        if (exerciseIt && view != null) {
            exercise(view, Canvas())
            pumpFrames(180, null)
            exercise(view, Canvas())
        }
        ime.onFinishInputView(false)
        ime.onDestroy()
        view
    }
    println("===== safety =====")
    stage("Crash report round-trip") {
        val ctx = app
        Crash.remember(ctx, IllegalStateException("simulated failure"))
        val report = Crash.take(ctx)
        check(report != null && report.contains("simulated failure")) { "report was not read back: $report" }
        check(Crash.take(ctx) == null) { "report should be consumed once" }
        val guarded: Unit? = Crash.guard(ctx) { error("guarded failure") }
        check(guarded == null) { "guard should swallow the failure" }
    }
    stage("FallbackBoard") {
        val board = FallbackBoard(app, app.settings.theme, {}, {}, {})
        if (exerciseIt) exercise(board, Canvas())
        board
    }
    println("callbacks run: $totalTasks")
    println("done")
}
