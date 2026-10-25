package app.limbo.keyboard.ui

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import app.limbo.keyboard.LimboApp
import app.limbo.keyboard.core.Dp
import app.limbo.keyboard.core.Settings
import app.limbo.keyboard.theme.Theme
import app.limbo.keyboard.ui.kit.Ui
import app.limbo.keyboard.ui.kit.WallpaperDrawable

/** Views that want to be told when the user changes the theme. */
interface ThemeAware {
    fun applyTheme(theme: Theme)
}

/**
 * Shared scaffolding: an edge-to-edge window, the soft wallpaper and a scroller that already
 * respects the status and navigation bars.
 */
abstract class BaseActivity : Activity(), ThemeAware {

    protected val limbo: LimboApp get() = application as LimboApp
    protected val settings: Settings get() = limbo.settings
    protected val theme: Theme get() = settings.theme

    private lateinit var wallpaper: WallpaperDrawable
    protected lateinit var root: FrameLayout
    protected lateinit var content: LinearLayout
    protected lateinit var scroller: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.setSystemBarsAppearance(
                if (theme.dark) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
        buildShell()
        onBuild()
        settings.onChange(settingsListener)
    }

    private val settingsListener: (String?) -> Unit = { key ->
        if (key == Settings.KEY_THEME) {
            applyTheme(settings.theme)
        }
    }

    private fun buildShell() {
        wallpaper = WallpaperDrawable(this, theme)
        root = FrameLayout(this).apply { background = wallpaper }

        scroller = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, 0, 0, Dp.dpi(this@BaseActivity, 26f))
        }
        content = Ui.column(this).apply {
            setPadding(Dp.dpi(this@BaseActivity, 20f), 0, Dp.dpi(this@BaseActivity, 20f), 0)
        }
        scroller.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroller, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        root.setOnApplyWindowInsetsListener { view, insets ->
            val top = insets.systemWindowInsetTop
            val bottom = insets.systemWindowInsetBottom
            scroller.setPadding(0, top + Dp.dpi(this, 12f), 0, bottom + Dp.dpi(this, 18f))
            insets
        }
    }

    /** Add your screen here. */
    protected abstract fun onBuild()

    /** Extra overlay host, used for sheets. */
    protected fun overlay(view: View) {
        root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    protected fun scrollToTopOnShow(view: View) {
        view.post { scroller.smoothScrollTo(0, 0) }
    }

    override fun applyTheme(theme: Theme) {
        wallpaper.theme = theme
        root.invalidate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                if (theme.dark) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
        refresh(theme, content)
        root.invalidate()
    }

    private fun refresh(theme: Theme, view: View) {
        if (view is ThemeAware) view.applyTheme(theme)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) refresh(theme, view.getChildAt(i))
        }
    }

    override fun onDestroy() {
        settings.offChange(settingsListener)
        super.onDestroy()
    }
}
