package com.tpj.millcar

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.ImageDecoder
import android.graphics.PorterDuff
import android.graphics.Rect
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.roundToInt

/** CarPlay-szerű felület: bal oldalsáv + kezdőképernyő (app-rács) vagy vezetés nézet (Waze + kártyák). */
class MainActivity : Activity() {
    private val M = LinearLayout.LayoutParams.MATCH_PARENT
    private val W = LinearLayout.LayoutParams.WRAP_CONTENT

    private lateinit var bg: ImageView
    private lateinit var clock: TextView
    private lateinit var speed: TextView
    private lateinit var home: View
    private lateinit var drive: View
    private lateinit var wazeSlot: FrameLayout
    private lateinit var turnTitle: TextView
    private lateinit var turnText: TextView
    private lateinit var turnIcon: ImageView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var playBtn: TextView
    private lateinit var dockPlay: TextView

    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val tick = object : Runnable {
        override fun run() { clock.text = fmt.format(Date()); App.I.ui.postDelayed(this, 5000) }
    }
    private var lm: LocationManager? = null
    private val locL = LocationListener { loc ->
        speed.text = if (loc.hasSpeed()) "${(loc.speed * 3.6f).roundToInt()}\nkm/h" else ""
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        App.main = this
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(build())
        loadBackground()
        if (b == null) { showDrive(); wazeSlot.post { launchWaze() } } else showHome()
    }

    // ---------------- felépítés ----------------

    private fun build(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(BG) }
        bg = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.32f
            setColorFilter(NEON, PorterDuff.Mode.MULTIPLY)
        }
        root.addView(bg, FrameLayout.LayoutParams(M, M))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(buildDock(), LinearLayout.LayoutParams(dp(118), M))
        row.addView(View(this).apply { setBackgroundColor(NEON); alpha = 0.5f }, LinearLayout.LayoutParams(dp(1), M))

        val content = FrameLayout(this)
        home = buildHome()
        drive = buildDrive()
        content.addView(home, FrameLayout.LayoutParams(M, M))
        content.addView(drive, FrameLayout.LayoutParams(M, M))
        row.addView(content, LinearLayout.LayoutParams(0, M, 1f))
        root.addView(row, FrameLayout.LayoutParams(M, M))
        return root
    }

    private fun buildDock(): View {
        val d = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val p = dp(8); setPadding(p, p, p, p)
        }
        clock = label("--:--", 30f).apply { gravity = Gravity.CENTER }
        speed = label("", 15f, DIM).apply { gravity = Gravity.CENTER }
        d.addView(clock)
        d.addView(speed)
        d.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        fun dockBtn(s: String, onClick: () -> Unit) = button(s, 30f, onClick = onClick).also {
            d.addView(it, LinearLayout.LayoutParams(M, dp(76)).apply { topMargin = dp(8) })
        }
        dockBtn("⌂") { showHome() }
        dockBtn("➤") { showDrive(); launchWaze() }
        dockPlay = dockBtn("▶") { Media.act(this, "toggle") }
        dockBtn("◉") { startActivity(Intent(this, VoiceActivity::class.java)) }
        return d
    }

    private fun buildHome(): View {
        val tiles = listOf(
            Triple("➤", "Waze") { showDrive(); launchWaze() },
            Triple("♫", "Zene") { openMusic() },
            Triple("✆", "Telefon") { start(Intent(Intent.ACTION_DIAL)) },
            Triple("✉", "Üzenetek") { start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)) },
            Triple("✔", "Todoist") { openPkg("com.todoist") },
            Triple("◉", "Haiku") { startActivity(Intent(this, VoiceActivity::class.java)) },
            Triple("✎", "Gondolat") { startActivity(Intent(this, VoiceActivity::class.java).putExtra("mode", "note")) },
            Triple("⚙︎", "Beállítás") { startActivity(Intent(this, SettingsActivity::class.java)) },
        )
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val p = dp(16); setPadding(p, p, p, p)
        }
        tiles.chunked(4).forEach { rowTiles ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowTiles.forEach { (glyph, name, act) ->
                val t = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    isClickable = true
                    setOnClickListener { act() }
                }
                t.addView(label(glyph, 40f).apply {
                    gravity = Gravity.CENTER
                    background = neonBox(26, CARD, 3)
                }, LinearLayout.LayoutParams(dp(96), dp(96)))
                t.addView(label(name, 17f).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
                r.addView(t, LinearLayout.LayoutParams(0, W, 1f))
            }
            grid.addView(r, LinearLayout.LayoutParams(M, W).apply { topMargin = dp(14) })
        }
        return grid
    }

    private fun buildDrive(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val p = dp(10); setPadding(p, p, p, p)
        }
        // A Waze ablak helye
        wazeSlot = FrameLayout(this).apply { background = neonBox(18, 0x33000000, 1) }
        wazeSlot.addView(button("Waze ide", 20f) { launchWaze() },
            FrameLayout.LayoutParams(W, W, Gravity.CENTER))
        row.addView(wazeSlot, LinearLayout.LayoutParams(0, M, 1.75f))

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Kanyar / érkezés kártya
        val turn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = neonBox()
            val p = dp(12); setPadding(p, p, p, p)
            setOnClickListener { launchWaze() }
        }
        turnIcon = ImageView(this).apply { setColorFilter(NEON, PorterDuff.Mode.SRC_IN) }
        turn.addView(turnIcon, LinearLayout.LayoutParams(dp(52), dp(52)))
        val tt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        turnTitle = label("", 26f).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        turnText = label("", 15f, DIM).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        tt.addView(turnTitle); tt.addView(turnText)
        turn.addView(tt, LinearLayout.LayoutParams(0, W, 1f))
        col.addView(turn, LinearLayout.LayoutParams(M, 0, 1f))

        // Zene kártya
        val music = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = neonBox()
            val p = dp(12); setPadding(p, p, p, p)
        }
        title = label("", 20f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        artist = label("", 15f, DIM).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        music.addView(title); music.addView(artist)
        music.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        val ctl = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun ctlBtn(s: String, a: String) = button(s, 32f) { Media.act(this, a) }
        playBtn = ctlBtn("▶", "toggle")
        listOf(ctlBtn("⏮︎", "previous"), playBtn, ctlBtn("⏭︎", "next")).forEachIndexed { i, v ->
            ctl.addView(v, LinearLayout.LayoutParams(0, dp(84), 1f).apply { if (i > 0) marginStart = dp(8) })
        }
        music.addView(ctl, LinearLayout.LayoutParams(M, W))
        col.addView(music, LinearLayout.LayoutParams(M, 0, 1.4f).apply { topMargin = dp(10) })

        row.addView(col, LinearLayout.LayoutParams(0, M, 1f).apply { marginStart = dp(10) })
        return row
    }

    // ---------------- nézetek ----------------

    private fun showHome() { home.visibility = View.VISIBLE; drive.visibility = View.GONE }
    private fun showDrive() { home.visibility = View.GONE; drive.visibility = View.VISIBLE; layoutForWindowMode() }

    /** Osztott képernyőn a Waze helye nem kell, csak a kártyák. */
    private fun layoutForWindowMode() {
        wazeSlot.visibility = if (isInMultiWindowMode) View.GONE else View.VISIBLE
    }

    override fun onMultiWindowModeChanged(m: Boolean, c: Configuration) {
        super.onMultiWindowModeChanged(m, c); layoutForWindowMode()
    }

    // ---------------- Waze ----------------

    /** Waze a kijelölt helyre: root → lebegő ablak pontos méretre; enélkül lebegő kísérlet; tartalék: osztott képernyő. */
    fun launchWaze() {
        val i = packageManager.getLaunchIntentForPackage(WAZE)
            ?: return Toast.makeText(this, "A Waze nincs telepítve", Toast.LENGTH_LONG).show()
        if (App.I.pref("waze_mode", "float") == "split" || isInMultiWindowMode) { split(i); return }
        val r = slotRect()
        val cmp = i.component
        if (App.I.pref("use_root", "1") == "1" && cmp != null) {
            thread {
                val ok = Root.placeWaze(cmp, r)
                if (!ok) App.I.ui.post { floatNoRoot(i, r) }
            }
        } else floatNoRoot(i, r)
    }

    private fun slotRect(): Rect {
        val loc = IntArray(2)
        wazeSlot.getLocationOnScreen(loc)
        return Rect(loc[0], loc[1], loc[0] + max(wazeSlot.width, dp(300)), loc[1] + max(wazeSlot.height, dp(200)))
    }

    private fun floatNoRoot(i: Intent, r: Rect) {
        val opts = ActivityOptions.makeBasic().setLaunchBounds(r)
        runCatching {
            ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(opts, 5)
        }
        try {
            startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), opts.toBundle())
        } catch (e: Exception) { split(i) }
    }

    private fun split(i: Intent) {
        startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---------------- appok ----------------

    private fun start(i: Intent) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { Toast.makeText(this, "Nem található app", Toast.LENGTH_SHORT).show() }
    }

    private fun openPkg(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
            ?: return Toast.makeText(this, "Nincs telepítve: $pkg", Toast.LENGTH_SHORT).show()
        start(i)
    }

    private fun openMusic() {
        val pkg = listOf("com.tpj.millmusic", Media.ctrl?.packageName)
            .firstOrNull { it != null && packageManager.getLaunchIntentForPackage(it) != null }
        if (pkg != null) openPkg(pkg) else Media.act(this, "toggle")
    }

    // ---------------- életciklus ----------------

    override fun onResume() {
        super.onResume()
        Media.onChange = { refreshMusic() }
        WazeInfo.onChange = { refreshTurn() }
        if (!Media.init(this)) { title.text = "Zenéhez: ⚙ → Értesítés-hozzáférés"; artist.text = "" } else refreshMusic()
        refreshTurn()
        layoutForWindowMode()
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
        WazeInfo.onChange = null
        super.onDestroy()
    }

    private fun refreshMusic() {
        title.text = Media.title().ifEmpty { "Nincs zene" }
        artist.text = Media.artist().ifEmpty { Media.ctrl?.packageName ?: "" }
        val p = if (Media.playing) "⏸︎" else "▶"
        playBtn.text = p; dockPlay.text = p
    }

    private fun refreshTurn() {
        if (WazeInfo.title.isBlank() && WazeInfo.text.isBlank()) {
            turnTitle.text = "Waze"; turnText.text = "Nincs aktív útvonal"; turnIcon.setImageDrawable(null)
        } else {
            turnTitle.text = WazeInfo.title; turnText.text = WazeInfo.text; turnIcon.setImageDrawable(WazeInfo.icon)
        }
    }

    private fun startLoc() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            lm = getSystemService(LocationManager::class.java)
            lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locL)
        } catch (_: Exception) { }
    }

    /** Háttérkép a galériából, narancsra színezve és sötétítve. */
    fun loadBackground() {
        val s = App.I.pref("bg_uri")
        if (s.isEmpty()) { bg.setImageDrawable(null); return }
        thread {
            val bmp = runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, Uri.parse(s))) { d, info, _ ->
                    d.setTargetSampleSize(max(1, info.size.width / 1600))
                }
            }.getOrNull()
            runOnUiThread { bg.setImageBitmap(bmp) }
        }
    }
}
