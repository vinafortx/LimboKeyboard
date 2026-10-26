package app.limbo.keyboard.ui

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import app.limbo.keyboard.LimboApp
import app.limbo.keyboard.core.Colors
import app.limbo.keyboard.core.Crash
import app.limbo.keyboard.core.Dp
import app.limbo.keyboard.core.I18n
import app.limbo.keyboard.core.Settings
import app.limbo.keyboard.theme.Theme
import app.limbo.keyboard.ui.kit.SoftButton
import app.limbo.keyboard.ui.kit.Ui
import app.limbo.keyboard.ui.kit.WallpaperDrawable

/** Views that want to be told when the user changes the theme. */
interface ThemeAware {
    fun applyTheme(theme: Theme)
}

/**
 * Shared scaffolding: an edge-to-edge window, the soft wallpaper and a scroller that already
 * respects the status and navigation bars.
 *
 * Screen building is wrapped twice: the shell must survive on its own, and each screen's own
 * content is built behind [Crash.guard]. A screen that cannot be drawn now falls back to a small
 * recovery card instead of taking the process with it.
 */
abstract class BaseActivity : Activity(), ThemeAware {

    protected val limbo: LimboApp get() = application as LimboApp
    protected val settings: Settings get() = limbo.settings
    protected val theme: Theme get() = settings.theme

    protected lateinit var root: FrameLayout
    protected lateinit var content: LinearLayout
    protected lateinit var scroller: ScrollView
    private var wallpaper: WallpaperDrawable? = null
    private var recovered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { setupLimboWindow(theme) }

        val shell = Crash.guard(this) {
            buildShell()
            true
        } == true

        if (shell) {
            if (Crash.guard(this) { buildContent() } != true) recover()
        } else {
            recover()
        }

        settings.onChange(settingsListener)
    }

    private val settingsListener: (String?) -> Unit = { key ->
        if (key == Settings.KEY_THEME) {
            runCatching { applyTheme(settings.theme) }
        }
    }

    /** [onBuild] wrapped so a broken card cannot end the screen. */
    private fun buildContent(): Boolean {
        onBuild()
        return true
    }

    private fun buildShell() {
        val drawable = WallpaperDrawable(this, theme)
        wallpaper = drawable
        root = FrameLayout(this).apply { background = drawable }

        scroller = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, 0, 0, Dp.dpi(this@BaseActivity, 26f))
        }
        content = Ui.column(this).apply {
            setPadding(Dp.dpi(this@BaseActivity, 20f), 0, Dp.dpi(this@BaseActivity, 20f), 0)
        }
        scroller.addView(
            content,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        root.addView(
            scroller,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        setContentView(root)
        root.setOnApplyWindowInsetsListener { _, insets ->
            runCatching {
                val top = insets.systemWindowInsetTop
                val bottom = insets.systemWindowInsetBottom
                scroller.setPadding(0, top + Dp.dpi(this, 12f), 0, bottom + Dp.dpi(this, 18f))
            }
            insets
        }
    }

    /** Add your screen here. */
    protected abstract fun onBuild()

    /** Extra overlay host, used for sheets. */
    protected fun overlay(view: View) {
        root.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    protected fun scrollToTopOnShow(view: View) {
        view.post { scroller.smoothScrollTo(0, 0) }
    }

    override fun applyTheme(theme: Theme) {
        wallpaper?.theme = theme
        root.invalidate()
        updateBarAppearance(theme)
        refresh(theme, content)
        root.invalidate()
    }

    private fun refresh(theme: Theme, view: View) {
        if (view is ThemeAware) view.applyTheme(theme)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) refresh(theme, view.getChildAt(i))
        }
    }

    /**
     * The smallest useful screen: what happened, one tap to try again and one to go back to the
     * stock look. Everything here is a plain view, so it cannot fail the way the real screen did.
     */
    private fun recover() {
        if (recovered) return
        recovered = true
        runCatching {
            val host: FrameLayout = if (::root.isInitialized) root else FrameLayout(this).also {
                it.setBackgroundColor(Color.BLACK)
                setContentView(it)
                root = it
            }
            host.removeAllViews()

            val currentTheme = runCatching { settings.theme }.getOrNull() ?: theme
            host.setBackgroundColor(currentTheme.wallTop)

            val column = Ui.column(this).apply {
                setPadding(Dp.dpi(this@BaseActivity, 22f), 0, Dp.dpi(this@BaseActivity, 22f), 0)
                gravity = Gravity.CENTER_VERTICAL
            }
            // Flat colour and a plain outline only: this screen has to survive the very failure
            // that brought the user here, so it deliberately shares nothing with the normal look.
            val card = Ui.column(this).apply {
                background = plainCard(currentTheme)
                setPadding(
                    Dp.dpi(this@BaseActivity, 22f), Dp.dpi(this@BaseActivity, 22f),
                    Dp.dpi(this@BaseActivity, 22f), Dp.dpi(this@BaseActivity, 22f)
                )
                addView(Ui.textView(this@BaseActivity, I18n.t("Экран не открылся", "This screen did not open"), 21f, 700, currentTheme.text, tracking = -0.02f))
                addView(Ui.textView(
                    this@BaseActivity,
                    I18n.t(
                        "Остальное приложение работает. Попробуйте ещё раз — или верните исходное оформление, если дело в нём.",
                        "The rest of the app still works. Try again — or go back to the default look if that was the cause."
                    ),
                    15f,
                    400,
                    currentTheme.textSecondary,
                    lineSpacing = 1.3f
                ).apply { setPadding(0, Dp.dpi(this@BaseActivity, 8f), 0, 0) })

                val row = LinearLayout(this@BaseActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, Dp.dpi(this@BaseActivity, 16f), 0, 0)
                }
                row.addView(
                    SoftButton(this@BaseActivity, currentTheme, I18n.t("Ещё раз", "Try again"), SoftButton.Kind.PRIMARY).apply {
                        setOnClickListener { recreate() }
                    },
                    LinearLayout.LayoutParams(0, Dp.dpi(this@BaseActivity, 44f), 1f)
                )
                row.addView(
                    SoftButton(this@BaseActivity, currentTheme, I18n.t("Исходное", "Default look"), SoftButton.Kind.QUIET).apply {
                        setOnClickListener {
                            runCatching { settings.resetEverything() }
                            recreate()
                        }
                    },
                    LinearLayout.LayoutParams(0, Dp.dpi(this@BaseActivity, 44f), 1f).apply {
                        marginStart = Dp.dpi(this@BaseActivity, 10f)
                    }
                )
                addView(row)
            }
            column.addView(card)
            host.addView(
                column,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
    }

    private fun plainCard(theme: Theme) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        cornerRadius = Dp.dp(this@BaseActivity, 22f)
        setColor(Colors.alpha(theme.panelBottom, 0.96f))
        setStroke(Dp.dpi(this@BaseActivity, 1f), Colors.alpha(theme.text, 0.08f))
    }

    override fun onDestroy() {
        runCatching { settings.offChange(settingsListener) }
        super.onDestroy()
    }
}
