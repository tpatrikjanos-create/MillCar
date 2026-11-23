package com.tpj.millcar

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent

/** Bármelyik zenelejátszó (MillMusic, YouTube, Spotify…) vezérlése a rendszer médiamenedzserén át. */
object Media {
    private var msm: MediaSessionManager? = null
    var ctrl: MediaController? = null
        private set
    var onChange: (() -> Unit)? = null

    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { onChange?.invoke() }
        override fun onPlaybackStateChanged(state: PlaybackState?) { onChange?.invoke() }
        override fun onSessionDestroyed() { ctrl = null; onChange?.invoke() }
    }
    private val sessL = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

    /** Fő szálról hívd. false = nincs értesítés-hozzáférés. */
    fun init(c: Context): Boolean {
        if (msm != null) return true
        return try {
            val m = c.getSystemService(MediaSessionManager::class.java)
            val cn = ComponentName(c, NotifListener::class.java)
            m.addOnActiveSessionsChangedListener(sessL, cn)
            msm = m
            pick(m.getActiveSessions(cn))
            true
        } catch (e: SecurityException) { false }
    }

    private fun pick(list: List<MediaController>?) {
        val l = list?.filter { it.packageName != "com.tpj.millcar" } ?: emptyList()
        val n = l.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: l.firstOrNull()
        if (n?.sessionToken != ctrl?.sessionToken) {
            ctrl?.unregisterCallback(cb)
            ctrl = n
            n?.registerCallback(cb)
        }
        onChange?.invoke()
    }

    val playing get() = ctrl?.playbackState?.state == PlaybackState.STATE_PLAYING
    fun title(): String = ctrl?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
    fun artist(): String = ctrl?.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
    fun art(): Bitmap? = ctrl?.metadata?.let {
        it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART)
    }

    fun act(c: Context, a: String): String {
        val t = ctrl?.transportControls
        if (t != null) {
            when (a) {
                "play" -> t.play()
                "pause" -> t.pause()
                "toggle" -> if (playing) t.pause() else t.play()
                "next" -> t.skipToNext()
                "previous" -> t.skipToPrevious()
                else -> return "ismeretlen művelet"
            }
            return "ok"
        }
        // Tartalék: médiagomb-esemény a rendszernek (értesítés-hozzáférés nélkül is megy)
        val code = when (a) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return "ismeretlen művelet"
        }
        val am = c.getSystemService(AudioManager::class.java)
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return "ok"
    }
}
