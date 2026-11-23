package com.tpj.millcar

import android.app.Activity
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gombteszt: a MillCar ideiglenesen „lejátszóvá" válik (csendet játszik),
 * így a transzmitter gombjai ide érkeznek, és kiírjuk, mi jön.
 */
class ButtonTestActivity : Activity() {
    companion object { @Volatile var active: ButtonTestActivity? = null }

    private lateinit var logTv: TextView
    private var session: MediaSession? = null
    private var track: AudioTrack? = null
    private var focus: AudioFocusRequest? = null
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            val p = dp(16); setPadding(p, p, p, p)
        }
        box.addView(label("Nyomd meg a transzmitter gombjait: röviden, hosszan, duplán. Itt látod, mi érkezik. A zene a teszt alatt szünetel.", 15f, DIM))
        box.addView(button("Bezárás") { finish() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        logTv = label("", 14f)
        val sv = ScrollView(this).apply { addView(logTv) }
        box.addView(sv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(10) })
        setContentView(box)
        active = this
        startSession()
        log("Kész, várom a gombokat…")
    }

    private fun startSession() {
        val am = getSystemService(AudioManager::class.java)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { }.build()
        am.requestAudioFocus(focus!!)

        val rate = 22050
        val silence = ShortArray(rate)
        track = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(silence.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build().apply {
                write(silence, 0, silence.size)
                setLoopPoints(0, silence.size, -1)
                play()
            }

        session = MediaSession(this, "MillCarGombteszt").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(i: Intent): Boolean {
                    @Suppress("DEPRECATION")
                    val k: KeyEvent? = i.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (k != null) log("MÉDIAGOMB ${KeyEvent.keyCodeToString(k.keyCode)} ${if (k.action == KeyEvent.ACTION_DOWN) "le" else "fel"} ism=${k.repeatCount}")
                    return super.onMediaButtonEvent(i)
                }
                override fun onPlay() { log("→ play") }
                override fun onPause() { log("→ pause") }
                override fun onStop() { log("→ stop") }
                override fun onSkipToNext() { log("→ következő") }
                override fun onSkipToPrevious() { log("→ előző") }
                override fun onFastForward() { log("→ előretekerés") }
                override fun onRewind() { log("→ visszatekerés") }
            })
            setPlaybackState(PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND)
                .setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
            setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "MillCar gombteszt").build())
            isActive = true
        }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        log("BILLENTYŰ ${KeyEvent.keyCodeToString(e.keyCode)} ${if (e.action == KeyEvent.ACTION_DOWN) "le" else "fel"} ism=${e.repeatCount}")
        return super.dispatchKeyEvent(e)
    }

    fun log(s: String) { runOnUiThread { logTv.append("${fmt.format(Date())}  $s\n") } }

    override fun onDestroy() {
        active = null
        session?.release()
        track?.let { runCatching { it.stop() }; it.release() }
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        super.onDestroy()
    }
}
