package com.tpj.millcar

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var box: LinearLayout
    private lateinit var statusTv: TextView
    private lateinit var carTv: TextView
    private lateinit var wazeTv: TextView

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        val uri = data?.data
        if (rc == 10 && res == RESULT_OK && uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            App.I.setPref("bg_uri", uri.toString())
            App.main?.loadBackground()
            refresh()
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(16); setPadding(p, p, p, p)
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(box) })

        head("Autó Bluetooth")
        carTv = add(label("", 14f, DIM))
        add(button("Eszköz kiválasztása") { pickCar() })

        head("Megjelenés")
        add(button("Háttérkép választása a galériából") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), 10)
        })
        add(button("Háttérkép törlése") { App.I.setPref("bg_uri", ""); App.main?.loadBackground(); refresh() })

        head("Waze elhelyezése")
        wazeTv = add(label("", 14f, DIM))
        add(button("Lebegő ablak / osztott képernyő váltás") {
            App.I.setPref("waze_mode", if (App.I.pref("waze_mode", "float") == "float") "split" else "float"); refresh()
        })
        add(button("Root használata be / ki") {
            App.I.setPref("use_root", if (App.I.pref("use_root", "1") == "1") "0" else "1"); refresh()
        })

        head("Kulcsok")
        val api = field("Claude API kulcs (sk-ant-…)", "api_key", true)
        val todo = field("Todoist API token", "todoist_token", true)
        val model = field("Modell", "model", false).apply { if (text.isEmpty()) setText(Agent.DEFAULT_MODEL) }
        add(button("Mentés", bg = ACC) {
            App.I.setPref("api_key", api.text.toString().trim())
            App.I.setPref("todoist_token", todo.text.toString().trim())
            App.I.setPref("model", model.text.toString().trim())
            Toast.makeText(this, "Mentve", Toast.LENGTH_SHORT).show()
        })

        head("Engedélyek")
        statusTv = add(label("", 14f, DIM))
        add(button("1. Alap engedélyek (BT, mikrofon, hely, névjegyek, hívás)") {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_CONTACTS,
                Manifest.permission.CALL_PHONE, Manifest.permission.POST_NOTIFICATIONS), 1)
        })
        add(button("2. Megjelenítés más appok felett (automatikus indításhoz)") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        add(button("3. Értesítés-hozzáférés (zene vezérléséhez)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        add(button("4. Kisegítő szolgáltatás (telefonvezérlés)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        add(button("5. Alapértelmezett appok (asszisztens)") {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        })
        add(button("Alkalmazásinfó (⋮ → Korlátozott beállítások engedélyezése)") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })

        head("Teszt")
        add(button("Gombteszt (transzmitter)") { startActivity(Intent(this, ButtonTestActivity::class.java)) })
        add(button("Hangparancs próba") { startActivity(Intent(this, VoiceActivity::class.java)) })
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val car = App.I.pref("car_name")
        carTv.text = if (car.isEmpty()) "Nincs kiválasztva" else "Kiválasztva: $car"
        wazeTv.text = (if (App.I.pref("waze_mode", "float") == "float") "Mód: lebegő ablak" else "Mód: osztott képernyő") +
            (if (App.I.pref("use_root", "1") == "1") " · root: be" else " · root: ki") +
            (if (App.I.pref("bg_uri").isNotEmpty()) " · van háttérkép" else "")
        fun ok(b: Boolean) = if (b) "✓" else "✗"
        fun perm(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        val nm = getSystemService(NotificationManager::class.java)
        statusTv.text = listOf(
            "${ok(perm(Manifest.permission.BLUETOOTH_CONNECT))} Bluetooth",
            "${ok(perm(Manifest.permission.RECORD_AUDIO))} Mikrofon",
            "${ok(perm(Manifest.permission.ACCESS_FINE_LOCATION))} Hely (sebesség)",
            "${ok(perm(Manifest.permission.READ_CONTACTS))} Névjegyek",
            "${ok(Settings.canDrawOverlays(this))} Más appok felett",
            "${ok(nm.isNotificationListenerAccessGranted(ComponentName(this, NotifListener::class.java)))} Értesítés-hozzáférés",
            "${ok(PhoneControlService.I != null)} Telefonvezérlés",
            "${ok(App.I.pref("api_key").isNotBlank())} Claude API kulcs",
            "${ok(App.I.pref("todoist_token").isNotBlank())} Todoist token",
        ).joinToString("\n")
    }

    private fun pickCar() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 2); return
        }
        val devs = try {
            getSystemService(BluetoothManager::class.java).adapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) { emptyList() }
        if (devs.isEmpty()) { Toast.makeText(this, "Nincs párosított eszköz", Toast.LENGTH_LONG).show(); return }
        val names = devs.map { (try { it.name } catch (e: SecurityException) { null }) ?: it.address }
        AlertDialog.Builder(this).setTitle("Melyik az autó?")
            .setItems(names.toTypedArray()) { _, i ->
                App.I.setPref("car_mac", devs[i].address)
                App.I.setPref("car_name", names[i])
                refresh()
            }.show()
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) { refresh() }

    private fun head(s: String) { add(label(s, 20f).apply { setPadding(0, dp(18), 0, 0) }) }

    private fun field(hint: String, key: String, secret: Boolean): EditText = add(EditText(this).apply {
        this.hint = hint
        setText(App.I.pref(key))
        setTextColor(FG); setHintTextColor(DIM)
        isSingleLine = true
        inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_CLASS_TEXT
    })

    private fun <T : View> add(v: T): T {
        box.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(8) })
        return v
    }
}
