package app.limbo.keyboard.ui.kit

import android.os.Build
import android.view.Window
import app.limbo.keyboard.core.Dp

/**
 * Real backdrop blur, where the platform actually allows it.
 *
 * Android 12 added blurring of whatever sits behind a window, but it is opt-in per window, needs
 * cross-window blur to be enabled by the system, and is unavailable to most floating surfaces.
 * The frosted look is therefore drawn by hand (see [Glass]); this call only adds the extra step
 * when the device is willing, and quietly does nothing when it is not.
 */
object Blur {

    fun enableBehind(window: Window?, context: android.content.Context, radiusDp: Float = 24f) {
        if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            window.setBackgroundBlurRadius(Dp.dpi(context, radiusDp))
        }
    }

    fun disable(window: Window?) {
        if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching { window.setBackgroundBlurRadius(0) }
    }
}
