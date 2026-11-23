package com.tpj.millcar

import android.app.Application
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class App : Application() {
    companion object {
        lateinit var I: App
        @Volatile var main: MainActivity? = null
    }

    lateinit var prefs: SharedPreferences
    val ui = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pending = mutableListOf<Pair<String, (() -> Unit)?>>()
    private val doneCbs = ConcurrentHashMap<String, () -> Unit>()
    private val ttsAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val duck by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(ttsAttrs).setOnAudioFocusChangeListener { }.build()
    }

    override fun onCreate() {
        super.onCreate()
        I = this
        prefs = getSharedPreferences("millcar", MODE_PRIVATE)
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hu", "HU")
                tts?.setAudioAttributes(ttsAttrs)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {
                        if (tts?.isSpeaking != true) getSystemService(AudioManager::class.java).abandonAudioFocusRequest(duck)
                        id?.let { doneCbs.remove(it)?.let { cb -> ui.post(cb) } }
                    }
                    override fun onError(id: String?) { onDone(id) }
                })
                ttsReady = true
                pending.forEach { say(it.first, it.second) }
                pending.clear()
            }
        }
    }

    /** Felolvas; onDone a felolvasás végén fut (fő szálon). */
    fun say(text: String, onDone: (() -> Unit)? = null) {
        ui.post {
            if (!ttsReady) { pending.add(text to onDone); return@post }
            val id = UUID.randomUUID().toString()
            if (onDone != null) doneCbs[id] = onDone
            getSystemService(AudioManager::class.java).requestAudioFocus(duck)
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        }
    }

    fun pref(k: String, d: String = ""): String = prefs.getString(k, d) ?: d
    fun setPref(k: String, v: String) = prefs.edit().putString(k, v).apply()
}
