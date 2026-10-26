package com.darahz.volumecontrol

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.VolumeProvider
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * Turns long presses on the volume buttons into track skips while the screen is off.
 *
 * With the screen off, the system doesn't send volume keys to apps. It sends them to the
 * active media session. So while another app is playing music, we hold a media session in
 * the "playing" state with remote volume handling, which makes the system route volume keys
 * to [VolumeProvider.onAdjustVolume]: RAISE/LOWER when the button goes down, SAME when it
 * comes up. A short press changes the music volume ourselves; a long press skips the track.
 * If a start app is set, we also hold the session while nothing is playing at all, so a long
 * press can start that app.
 *
 * The system treats a remote session like a cast device and shows a cast volume slider for it,
 * so while the screen is on we step aside and let [VolumeKeyAccessibilityService] handle the
 * keys instead. Without that service enabled we handle them with the screen on too.
 *
 * This is a NotificationListenerService because notification access is what lets us see and
 * control other apps' media sessions, and the system keeps the service bound for us.
 */
class VolumeKeyService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audio: AudioManager
    private lateinit var power: PowerManager
    private lateinit var sessionManager: MediaSessionManager
    private lateinit var prefs: AppPrefs

    private var session: MediaSession? = null
    private var volumeProvider: VolumeProvider? = null
    private var players: List<MediaController> = emptyList()
    private var playingTokens: Set<MediaSession.Token> = emptySet()
    private var handlingKeys = false

    private val holdDetector = HoldDetector(
        handler,
        holdMs = { prefs.holdMs.toLong() },
        onShortPress = { direction ->
            adjustMusicVolume(direction, showUi = power.isInteractive)
            volumeProvider?.currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        },
        onLongPress = { direction -> prefs.holdAction(players)?.let { runHoldAction(it, direction) } },
    )

    private val playerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
    }
    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { setPlayers(it.orEmpty()) }
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
    private val modeListener = AudioManager.OnModeChangedListener { refresh() }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    override fun onListenerConnected() {
        audio = getSystemService(AudioManager::class.java)
        power = getSystemService(PowerManager::class.java)
        sessionManager = getSystemService(MediaSessionManager::class.java)
        prefs = AppPrefs(this)

        val provider = object : VolumeProvider(
            VOLUME_CONTROL_RELATIVE,
            audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        ) {
            override fun onAdjustVolume(direction: Int) {
                Log.d(TAG, "session onAdjustVolume direction=$direction")
                when (direction) {
                    AudioManager.ADJUST_RAISE, AudioManager.ADJUST_LOWER -> holdDetector.press(direction)
                    AudioManager.ADJUST_SAME -> holdDetector.release()
                }
            }
        }
        volumeProvider = provider
        session = MediaSession(this, "VolumeKeys").apply {
            // Without a callback the session silently drops incoming commands, volume changes included
            setCallback(object : MediaSession.Callback() {}, handler)
            setPlaybackToRemote(provider)
            setPlaybackState(playbackState(PlaybackState.STATE_PAUSED))
            isActive = true
        }

        val listenerComponent = ComponentName(this, VolumeKeyService::class.java)
        sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
        prefs.registerListener(prefsListener)
        audio.addOnModeChangedListener(mainExecutor, modeListener)
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            RECEIVER_NOT_EXPORTED,
        )
        instance = this
        setPlayers(sessionManager.getActiveSessions(listenerComponent))
    }

    override fun onListenerDisconnected() = tearDown()

    override fun onDestroy() {
        tearDown()
        super.onDestroy()
    }

    private fun tearDown() {
        val session = session ?: return
        instance = null
        holdDetector.cancel()
        sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
        prefs.unregisterListener(prefsListener)
        audio.removeOnModeChangedListener(modeListener)
        unregisterReceiver(screenReceiver)
        players.forEach { it.unregisterCallback(playerCallback) }
        players = emptyList()
        playingTokens = emptySet()
        handlingKeys = false
        session.release()
        this.session = null
        volumeProvider = null
    }

    private fun setPlayers(controllers: List<MediaController>) {
        players.forEach { it.unregisterCallback(playerCallback) }
        players = controllers.filter { it.packageName != packageName }
        players.forEach { it.registerCallback(playerCallback, handler) }
        refresh()
    }

    /** Takes over the volume keys while a long press would do something, and releases them otherwise. */
    private fun refresh() {
        val session = session ?: return
        val nowPlaying = players.filter { it.playbackState?.isActive == true }.map { it.sessionToken }.toSet()
        val newlyPlaying = nowPlaying - playingTokens
        playingTokens = nowPlaying

        val accessibilityHandlesKeys = power.isInteractive && VolumeKeyAccessibilityService.running
        val wantsKeys = when (prefs.holdAction(players)) {
            is HoldAction.Skip -> true
            // Holding the keys shows a cast slider, so with nothing playing only do it with the screen off
            is HoldAction.StartApp -> !power.isInteractive
            null -> false
        }
        val shouldHandle = prefs.enabled && wantsKeys &&
            audio.mode == AudioManager.MODE_NORMAL && !accessibilityHandlesKeys
        if (shouldHandle && (!handlingKeys || newlyPlaying.isNotEmpty())) {
            // The system sends volume keys to whichever session most recently started playing.
            // Going paused -> playing puts us back in front of a player that just started.
            session.setPlaybackState(playbackState(PlaybackState.STATE_PAUSED))
            session.setPlaybackState(playbackState(PlaybackState.STATE_PLAYING))
            volumeProvider?.currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        } else if (!shouldHandle && handlingKeys) {
            session.setPlaybackState(playbackState(PlaybackState.STATE_PAUSED))
            holdDetector.cancel()
        }
        handlingKeys = shouldHandle
    }

    private fun playbackState(state: Int) = PlaybackState.Builder()
        .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
        .build()

    companion object {
        private var instance: VolumeKeyService? = null

        /** Re-evaluates whether we should hold the volume keys, e.g. after the accessibility service starts or stops. */
        fun requestRefresh() {
            instance?.let { it.handler.post { it.refresh() } }
        }
    }
}
