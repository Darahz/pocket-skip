package com.darahz.volumecontrol

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Handles the volume keys while the screen is on. Accessibility services can see and swallow
 * key events before anything else does, so the system shows its normal volume slider instead
 * of the cast slider that [VolumeKeyService]'s media session trick causes. Accessibility
 * services don't get key events with the screen off, which is why we still need both.
 */
class VolumeKeyAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: AppPrefs
    private lateinit var audio: AudioManager
    private var action: HoldAction? = null
    private var handlingPress = false

    private val holdDetector = HoldDetector(
        handler,
        holdMs = { prefs.holdMs.toLong() },
        onShortPress = { direction -> adjustMusicVolume(direction, showUi = true) },
        onLongPress = { direction -> action?.let { runHoldAction(it, direction) } },
    )

    override fun onServiceConnected() {
        prefs = AppPrefs(this)
        audio = getSystemService(AudioManager::class.java)
        running = true
        VolumeKeyService.requestRefresh()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
            KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            else -> return false
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    // Decide once per press whether it's ours; if not, the system handles it as usual
                    action = if (prefs.enabled && audio.mode == AudioManager.MODE_NORMAL) {
                        prefs.holdAction(otherPlayers())
                    } else {
                        null
                    }
                    handlingPress = action != null
                    Log.d(TAG, "accessibility down direction=$direction handling=$handlingPress")
                    if (handlingPress) holdDetector.press(direction)
                }
                return handlingPress
            }
            KeyEvent.ACTION_UP -> {
                if (!handlingPress) return false
                holdDetector.release()
                handlingPress = false
                return true
            }
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        stopped()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopped()
        super.onDestroy()
    }

    private fun stopped() {
        holdDetector.cancel()
        running = false
        VolumeKeyService.requestRefresh()
    }

    companion object {
        /** Whether the service is enabled and connected. Read by [VolumeKeyService]. */
        @Volatile
        var running = false
            private set
    }
}
