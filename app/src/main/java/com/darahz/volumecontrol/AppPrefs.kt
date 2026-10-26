package com.darahz.volumecontrol

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    /** How long a volume button must be held before it skips a track. */
    var holdMs: Int
        get() = prefs.getInt(KEY_HOLD_MS, DEFAULT_HOLD_MS)
        set(value) = prefs.edit { putInt(KEY_HOLD_MS, value) }

    /** false: hold up = next, hold down = previous. true: the opposite. */
    var swapDirections: Boolean
        get() = prefs.getBoolean(KEY_SWAP, false)
        set(value) = prefs.edit { putBoolean(KEY_SWAP, value) }

    /** Packages we skip tracks in. Empty means every app. */
    var allowedApps: Set<String>
        get() = prefs.getStringSet(KEY_ALLOWED_APPS, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit { putStringSet(KEY_ALLOWED_APPS, value) }

    fun isAllowed(packageName: String) = allowedApps.let { it.isEmpty() || packageName in it }

    /** Package to start playing when a button is held while nothing is playing, or null for none. */
    var startApp: String?
        get() = prefs.getString(KEY_START_APP, null)
        set(value) = prefs.edit { putString(KEY_START_APP, value) }

    /** Whether the first-run setup wizard has been finished. */
    var setupDone: Boolean
        get() = prefs.getBoolean(KEY_SETUP_DONE, false)
        set(value) = prefs.edit { putBoolean(KEY_SETUP_DONE, value) }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val MIN_HOLD_MS = 300
        const val MAX_HOLD_MS = 1500
        const val DEFAULT_HOLD_MS = 600

        private const val KEY_ENABLED = "enabled"
        private const val KEY_HOLD_MS = "hold_ms"
        private const val KEY_SWAP = "swap_directions"
        private const val KEY_ALLOWED_APPS = "allowed_apps"
        private const val KEY_START_APP = "start_app"
        private const val KEY_SETUP_DONE = "setup_done"
    }
}
