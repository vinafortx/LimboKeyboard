package app.limbo.keyboard.ui

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.WindowInsetsController
import app.limbo.keyboard.theme.Theme

/**
 * One window contract for every screen: draw under the system bars, keep the bars transparent and
 * pick dark or light icons from the theme. Was previously copied per screen, which is exactly the
 * kind of copy that quietly drifts apart.
 */
fun Activity.setupLimboWindow(theme: Theme) {
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
        updateBarAppearance(theme)
    }
}

/** Re-applies the status bar icon tint after a theme change. Safe on every version. */
fun Activity.updateBarAppearance(theme: Theme) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    runCatching {
        window.insetsController?.setSystemBarsAppearance(
            if (theme.dark) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        )
    }
}
