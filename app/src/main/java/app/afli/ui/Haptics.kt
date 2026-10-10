package app.afli.ui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Small, deliberate haptics, played straight on the vibration motor so they're felt the same on
 * every phone (some phones mute or soften the system touch ticks). Android 11+ gets crisp
 * composed primitives — tick, click, low tick, thud — at chosen strengths; older phones get the
 * predefined tick and click. Turned off in Settings → Look and feel.
 */
object Haptics {
    private var vib: Vibrator? = null
    private var lastScrub = 0L

    private fun vibrator(view: View): Vibrator? = vib ?: run {
        val ctx = view.context
        val v = if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        v?.takeIf { it.hasVibrator() }.also { vib = it }
    }

    /** Plays one primitive at [scale] (0..1), or the closest predefined effect, or a view tick. */
    private fun play(view: View, primitive: Int, scale: Float, fallback: Int, viewFallback: Int) {
        if (!app.afli.Prefs.haptics) return
        val v = vibrator(view)
        if (v == null) {
            view.performHapticFeedback(viewFallback)
            return
        }
        if (Build.VERSION.SDK_INT >= 30 && v.areAllPrimitivesSupported(primitive)) {
            v.vibrate(VibrationEffect.startComposition().addPrimitive(primitive, scale).compose())
        } else if (Build.VERSION.SDK_INT >= 29) {
            v.vibrate(VibrationEffect.createPredefined(fallback))
        } else {
            view.performHapticFeedback(viewFallback)
        }
    }

    private val TICK = if (Build.VERSION.SDK_INT >= 30) VibrationEffect.Composition.PRIMITIVE_TICK else 0
    private val CLICK = if (Build.VERSION.SDK_INT >= 30) VibrationEffect.Composition.PRIMITIVE_CLICK else 0
    private val LOW_TICK = if (Build.VERSION.SDK_INT >= 31) VibrationEffect.Composition.PRIMITIVE_LOW_TICK else TICK
    private val THUD = if (Build.VERSION.SDK_INT >= 31) VibrationEffect.Composition.PRIMITIVE_THUD else CLICK
    private val E_TICK = if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_TICK else 0
    private val E_CLICK = if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_CLICK else 0
    private val E_HEAVY = if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_HEAVY_CLICK else 0

    fun toggle(view: View, on: Boolean) =
        if (on) play(view, CLICK, 0.7f, E_CLICK, HapticFeedbackConstants.VIRTUAL_KEY)
        else play(view, TICK, 0.6f, E_TICK, HapticFeedbackConstants.CLOCK_TICK)

    /** A segment snapped into place: a tab, a tile, a day. */
    fun segment(view: View) = play(view, TICK, 0.8f, E_TICK, HapticFeedbackConstants.CLOCK_TICK)

    /** Light, frequent tick for each hour passing under a finger (time strip, hour bars). */
    fun scrub(view: View) {
        val now = SystemClock.uptimeMillis()
        if (now - lastScrub < 18) return
        lastScrub = now
        play(view, TICK, 0.55f, E_TICK, HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Button press. */
    fun tap(view: View) = play(view, CLICK, 0.5f, E_CLICK, HapticFeedbackConstants.VIRTUAL_KEY)

    /** Something settled: the sheet let go, the strip landed, the score reached the header. */
    fun settle(view: View) = play(view, LOW_TICK, 0.8f, E_TICK, HapticFeedbackConstants.CLOCK_TICK)

    /** Something important happened: a trip started, a catch was logged. */
    fun confirm(view: View) = play(view, CLICK, 1f, E_HEAVY, HapticFeedbackConstants.LONG_PRESS)

    /** A long-press opened an explanation. */
    fun reveal(view: View) = play(view, THUD, 0.7f, E_HEAVY, HapticFeedbackConstants.LONG_PRESS)
}
