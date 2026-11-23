package com.tpj.millcar

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.concurrent.thread

/**
 * Átlátszó hangbeviteli ablak.
 * mode = command (Haiku), note (gondolat egyből a Todoistba), confirm (igen/nem kérdés a Haikutól).
 */
class VoiceActivity : Activity() {
    companion object { @Volatile var confirmCb: ((Boolean) -> Unit)? = null }

    private var sr: SpeechRecognizer? = null
    private lateinit var status: TextView
    private var mode = "command"
    private var finished = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        mode = intent.getStringExtra("mode") ?: "command"

        // Gombteszt közben csak naplózzuk, hogy a hanggomb megérkezett
        val act = intent.action
        ButtonTestActivity.active?.let {
            if (act != null && act != Intent.ACTION_MAIN) { it.log("HANGGOMB → $act"); finished = true; finish(); return }
        }

        val root = FrameLayout(this).apply { setOnClickListener { cancel() } }
        status = label("…", 20f).apply {
            gravity = Gravity.CENTER
            val p = dp(18); setPadding(p, p, p, p)
            background = neonBox(20)
        }
        root.addView(status, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM).apply { val m = dp(16); setMargins(m, m, m, m) })
        setContentView(root)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        } else begin()
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        if (r.firstOrNull() == PackageManager.PERMISSION_GRANTED) begin() else done(null)
    }

    private fun begin() {
        if (mode == "confirm") {
            val q = intent.getStringExtra("q") ?: "Biztos?"
            status.text = q
            App.I.say(q) { listen() }
        } else listen()
    }

    private fun listen() {
        if (finished) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { status.text = "Nincs beszédfelismerő"; done(null); return }
        if (mode == "note") status.text = "Mondd a gondolatot…" else if (mode == "command") status.text = "Figyelek…"
        sr = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(t: Int, p: Bundle?) {}
                override fun onError(e: Int) { done(null) }
                override fun onPartialResults(r: Bundle?) {
                    r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { if (it.isNotBlank()) status.text = it }
                }
                override fun onResults(r: Bundle?) {
                    done(r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
                }
            })
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hu-HU")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        sr?.startListening(i)
    }

    private fun done(t: String?) {
        if (finished) return
        finished = true
        sr?.destroy(); sr = null
        when (mode) {
            "confirm" -> {
                val yes = t != null && Regex("igen|persze|mehet|küld|jó|oké|ok|rendben|hívd|csináld").containsMatchIn(t.lowercase())
                confirmCb?.invoke(yes); confirmCb = null
                finish()
            }
            "note" -> {
                if (t.isNullOrBlank()) { App.I.say("Nem értettem"); finish(); return }
                status.text = t
                thread { val r = Todoist.add(t, null); App.I.say(if (r == "ok") "Felírtam" else r) }
                App.I.ui.postDelayed({ finish() }, 700)
            }
            else -> {
                if (t.isNullOrBlank()) { status.text = "Nem értettem"; App.I.say("Nem értettem"); App.I.ui.postDelayed({ finish() }, 900); return }
                status.text = t
                finish()
                Agent.run(t)
            }
        }
    }

    private fun cancel() {
        if (!finished) {
            finished = true
            sr?.destroy(); sr = null
            if (mode == "confirm") { confirmCb?.invoke(false); confirmCb = null }
        }
        finish()
    }

    override fun onDestroy() {
        sr?.destroy(); sr = null
        if (mode == "confirm" && !finished) { confirmCb?.invoke(false); confirmCb = null }
        super.onDestroy()
    }
}
