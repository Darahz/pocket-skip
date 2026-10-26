package com.darahz.volumecontrol

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log

const val TAG = "VolumeKeys"

/**
 * Tells short presses from long presses. [press] takes AudioManager.ADJUST_RAISE or ADJUST_LOWER.
 * A long press fires [onLongPress] while the button is still held; a short press fires
 * [onShortPress] on release.
 */
class HoldDetector(
    private val handler: Handler,
    private val holdMs: () -> Long,
    private val onShortPress: (direction: Int) -> Unit,
    private val onLongPress: (direction: Int) -> Unit,
) {
    private var pressedDirection = 0
    private var lastEventAt = 0L
    private var longPressed = false
    private val holdRunnable = Runnable {
        longPressed = true
        onLongPress(pressedDirection)
    }

    fun press(direction: Int) {
        val now = SystemClock.uptimeMillis()
        val sinceLastEvent = now - lastEventAt
        lastEventAt = now
        // Holding a button can send repeated presses; only the first counts.
        // The time check stops us getting stuck if a release was ever missed.
        if (pressedDirection != 0 && sinceLastEvent < STALE_PRESS_MS) return
        pressedDirection = direction
        longPressed = false
        handler.removeCallbacks(holdRunnable)
        handler.postDelayed(holdRunnable, holdMs())
    }

    fun release() {
        lastEventAt = SystemClock.uptimeMillis()
        handler.removeCallbacks(holdRunnable)
        if (pressedDirection != 0 && !longPressed) onShortPress(pressedDirection)
        pressedDirection = 0
    }

    fun cancel() {
        handler.removeCallbacks(holdRunnable)
        pressedDirection = 0
    }

    private companion object {
        const val STALE_PRESS_MS = 2000L
    }
}

/** Other apps' media sessions. Needs notification access, so returns nothing without it. */
fun Context.otherPlayers(): List<MediaController> = try {
    getSystemService(MediaSessionManager::class.java)
        .getActiveSessions(ComponentName(this, VolumeKeyService::class.java))
        .filter { it.packageName != packageName }
} catch (_: SecurityException) {
    emptyList()
}

/** What holding a volume button does. */
sealed interface HoldAction {
    class Skip(val player: MediaController) : HoldAction
    class StartApp(val packageName: String) : HoldAction
}

/** What a hold should do given [players], or null if we should leave the volume keys alone. */
fun AppPrefs.holdAction(players: List<MediaController>): HoldAction? {
    val playing = players.filter { it.playbackState?.isActive == true }
    if (playing.isEmpty()) return startApp?.let { HoldAction.StartApp(it) }
    return playing.firstOrNull { isAllowed(it.packageName) }?.let { HoldAction.Skip(it) }
}

fun Context.runHoldAction(action: HoldAction, direction: Int) {
    when (action) {
        is HoldAction.Skip -> skipTrack(action.player, direction)
        is HoldAction.StartApp -> startPlayback(action.packageName)
    }
    getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(
        VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE),
        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA),
    )
}

fun Context.adjustMusicVolume(direction: Int, showUi: Boolean) {
    getSystemService(AudioManager::class.java)
        .adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, if (showUi) AudioManager.FLAG_SHOW_UI else 0)
}

/** Skips [player] forward or back depending on which button was held and the swap setting. */
fun Context.skipTrack(player: MediaController, direction: Int) {
    val forward = (direction == AudioManager.ADJUST_RAISE) != AppPrefs(this).swapDirections
    Log.d(TAG, "skipTrack forward=$forward player=${player.packageName}")
    if (forward) player.transportControls.skipToNext() else player.transportControls.skipToPrevious()
}
