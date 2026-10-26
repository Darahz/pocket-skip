package com.darahz.volumecontrol

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.media.MediaBrowserService
import android.util.Log
import android.view.KeyEvent

data class MusicApp(val packageName: String, val label: String)

/** Installed apps with a media browser service or a media button receiver. */
fun Context.musicApps(): List<MusicApp> {
    val pm = packageManager
    val services = pm.queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE), 0)
        .map { it.serviceInfo.applicationInfo }
    val receivers = pm.queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON), 0)
        .map { it.activityInfo.applicationInfo }
    return (services + receivers)
        .distinctBy { it.packageName }
        // Leaves out system parts like Bluetooth that handle media buttons but aren't players
        .filter { it.packageName != packageName && pm.getLaunchIntentForPackage(it.packageName) != null }
        .map { MusicApp(it.packageName, pm.getApplicationLabel(it).toString()) }
        .sortedBy { it.label.lowercase() }
}

/**
 * Starts [packageName] playing: through its session if it has one, otherwise through its media
 * browser service (like the system's "resume playback"), otherwise with a play button press.
 */
fun Context.startPlayback(packageName: String) {
    val existing = otherPlayers().firstOrNull { it.packageName == packageName }
    if (existing != null) {
        Log.d(TAG, "startPlayback via existing session $packageName")
        existing.transportControls.play()
        return
    }

    val browserService = packageManager
        .queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(packageName), 0)
        .firstOrNull()?.serviceInfo
    if (browserService == null) {
        sendPlayButton(packageName)
        return
    }

    Log.d(TAG, "startPlayback via media browser ${browserService.name}")
    lateinit var browser: MediaBrowser
    browser = MediaBrowser(
        this,
        ComponentName(browserService.packageName, browserService.name),
        object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                MediaController(this@startPlayback, browser.sessionToken).transportControls.play()
                // Staying bound for a bit gives the player time to move itself to the foreground,
                // so the system doesn't stop its service as soon as we let go
                Handler(Looper.getMainLooper()).postDelayed({ browser.disconnect() }, BROWSER_LINGER_MS)
            }

            override fun onConnectionFailed() {
                Log.d(TAG, "media browser connection refused by $packageName")
                sendPlayButton(packageName)
            }
        },
        Bundle().apply { putBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT, true) },
    )
    browser.connect()
}

private fun Context.sendPlayButton(packageName: String) {
    val receiver = packageManager
        .queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(packageName), 0)
        .firstOrNull()?.activityInfo
    if (receiver == null) {
        Log.d(TAG, "no way to start playback in $packageName")
        return
    }
    Log.d(TAG, "startPlayback via media button ${receiver.name}")
    for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
        sendBroadcast(
            Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(receiver.packageName, receiver.name))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY))
        )
    }
}

private const val BROWSER_LINGER_MS = 10_000L
