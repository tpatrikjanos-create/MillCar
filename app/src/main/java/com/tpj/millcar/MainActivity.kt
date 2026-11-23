package com.tpj.millcar

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** A panel: Waze mellett, osztott képernyőn. */
class MainActivity : Activity() {
    private lateinit var clock: TextView
    private lateinit var speed: TextView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var playBtn: TextView
    private lateinit var art: ImageView

    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val tick = object : Runnable {
        override fun run() { clock.text = fmt.format(Date()); App.I.ui.postDelayed(this, 5000) }
    }
    private var lm: LocationManager? = null
    private val locL = LocationListener { loc ->
        speed.text = if (loc.hasSpeed()) "${(loc.speed * 3.6f).roundToInt()} km/h" else ""
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        App.main = this
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(build())
        if (b == null) App.I.ui.postDelayed({ if (!isInMultiWindowMode) openWaze() }, 600)
    }

    private fun build(): View {
        val m = LinearLayout.LayoutParams.MATCH_PARENT
        val w = LinearLayout.LayoutParams.WRAP_CONTENT
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            val p = dp(14); setPadding(p, p, p, p)
        }

        // Felső sor: óra, sebesség, beállítások
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        clock = label("--:--", 38f)
        speed = label("", 18f, DIM).apply { setPadding(dp(12), 0, dp(12), 0) }
        top.addView(clock, LinearLayout.LayoutParams(0, w, 1f))
        top.addView(speed)
        top.addView(label("⚙", 26f, DIM).apply {
            setPadding(dp(8), dp(4), dp(4), dp(4))
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        })
        root.addView(top, LinearLayout.LayoutParams(m, w))

        root.addView(View(this), LinearLayout.LayoutParams(m, 0, 1f))

        // Most szól
        val now = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        art = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(CARD) }
        now.addView(art, LinearLayout.LayoutParams(dp(64), dp(64)))
        val txt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        title = label("", 18f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        artist = label("", 14f, DIM).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        txt.addView(title); txt.addView(artist)
        now.addView(txt, LinearLayout.LayoutParams(0, w, 1f))
        root.addView(now, LinearLayout.LayoutParams(m, w))

        // Zenevezérlés: nagy gombok
        val ctl = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun ctlBtn(s: String, a: String) = button(s, 30f) { Media.act(this, a) }
        playBtn = ctlBtn("▶", "toggle")
        listOf(ctlBtn("⏮", "previous"), playBtn, ctlBtn("⏭", "next")).forEachIndexed { i, v ->
            ctl.addView(v, LinearLayout.LayoutParams(0, dp(84), 1f).apply { if (i > 0) marginStart = dp(10) })
        }
        root.addView(ctl, LinearLayout.LayoutParams(m, w).apply { topMargin = dp(14) })

        root.addView(View(this), LinearLayout.LayoutParams(m, 0, 1f))

        // Alsó sor: Haiku hangvezérlés, halk gondolat-gomb, Waze visszaállítás
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(button("🎤  Haiku", 18f, ACC) {
            startActivity(Intent(this, VoiceActivity::class.java))
        }, LinearLayout.LayoutParams(0, dp(64), 2f))
        bottom.addView(button("✎", 20f, CARD, DIM) {
            startActivity(Intent(this, VoiceActivity::class.java).putExtra("mode", "note"))
        }, LinearLayout.LayoutParams(0, dp(64), 1f).apply { marginStart = dp(10) })
        bottom.addView(button("⧉", 20f, CARD, DIM) { openWaze() },
            LinearLayout.LayoutParams(0, dp(64), 1f).apply { marginStart = dp(10) })
        root.addView(bottom, LinearLayout.LayoutParams(m, w))
        return root
    }

    override fun onResume() {
        super.onResume()
        Media.onChange = { refresh() }
        if (!Media.init(this)) { title.text = "Zenéhez: ⚙ → Értesítés-hozzáférés"; artist.text = "" } else refresh()
        App.I.ui.removeCallbacks(tick); App.I.ui.post(tick)
        startLoc()
    }

    override fun onPause() {
        super.onPause()
        App.I.ui.removeCallbacks(tick)
        lm?.removeUpdates(locL)
    }

    override fun onDestroy() {
        if (App.main === this) App.main = null
        Media.onChange = null
        super.onDestroy()
    }

    private fun refresh() {
        title.text = Media.title().ifEmpty { "Nincs zene" }
        artist.text = Media.artist().ifEmpty { Media.ctrl?.packageName ?: "" }
        playBtn.text = if (Media.playing) "⏸" else "▶"
        art.setImageBitmap(Media.art())
    }

    private fun startLoc() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            lm = getSystemService(LocationManager::class.java)
            lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locL)
        } catch (_: Exception) { }
    }

    /** Waze a MillCar mellé, osztott képernyőre. */
    fun openWaze() {
        val i = packageManager.getLaunchIntentForPackage("com.waze")
            ?: return Toast.makeText(this, "A Waze nincs telepítve", Toast.LENGTH_LONG).show()
        i.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }
}
