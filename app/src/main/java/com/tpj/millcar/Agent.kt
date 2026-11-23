package com.tpj.millcar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Haiku hangvezérlés: a kimondott szövegből eszközhívások, amiket a telefonon végrehajtunk. */
object Agent {
    const val DEFAULT_MODEL = "claude-haiku-5-5"
    private const val NO_ACC = "A telefonvezérlés (kisegítő szolgáltatás) nincs bekapcsolva"
    @Volatile private var busy = false

    private val SYSTEM = """
Te a MillCar vagy, Patrik autós hangasszisztense egy Samsung S24 telefonon. Vezetés közben beszél hozzád, a válaszodat hangosan felolvassuk.
Szabályok:
- Magyarul válaszolj, nagyon röviden (1 rövid mondat), felolvasható szöveggel, markdown és emoji nélkül.
- A beszédfelismerés hibázhat, értelmezd jóhiszeműen.
- Zene: media. Gondolat, teendő, emlékeztető felírása: add_todo (a due mezőt angolul add meg, pl. "tomorrow 9am"). Navigáció: navigate (Waze). Hívás: call_contact (a név alapalakját add meg, pl. "Pistit" helyett "Pisti").
- Ha más appban kell valamit csinálni (üzenet írása, keresés, beállítás stb.): open_app, majd read_screen, és a képernyőelemek [szám] azonosítóival tap / type_text / scroll. Minden tap vagy gépelés után olvasd újra a képernyőt, mielőtt újabb számot használsz.
- Üzenet elküldése, fizetés, törlés, vásárlás vagy bármi visszafordíthatatlan előtt MINDIG kérdezz rá a confirm eszközzel, és csak "igen" válasz esetén folytasd.
- A Waze-t és a zenét ne zárd be, a végén ha kell, global_action home helyett hagyd a képernyőt.
- Ha valami nem sikerül, mondd el egy mondatban.
""".trimIndent()

    fun run(text: String) {
        if (busy) { App.I.say("Még az előzőn dolgozom"); return }
        busy = true
        thread {
            try { loop(text) }
            catch (e: Exception) { App.I.say("Hiba: " + (e.message ?: "ismeretlen").take(90)) }
            finally { busy = false }
        }
    }

    private fun loop(text: String) {
        val key = App.I.pref("api_key")
        if (key.isBlank()) { App.I.say("Nincs megadva Claude API kulcs"); return }
        val msgs = JSONArray().put(JSONObject().put("role", "user").put("content", text))
        repeat(30) {
            val r = api(key, msgs)
            val content = r.getJSONArray("content")
            msgs.put(JSONObject().put("role", "assistant").put("content", content))
            val results = JSONArray()
            val said = StringBuilder()
            for (i in 0 until content.length()) {
                val b = content.getJSONObject(i)
                when (b.getString("type")) {
                    "text" -> said.append(b.getString("text"))
                    "tool_use" -> {
                        val out = try { exec(b.getString("name"), b.optJSONObject("input") ?: JSONObject()) }
                                  catch (e: Exception) { "hiba: ${e.message}" }
                        results.put(JSONObject().put("type", "tool_result").put("tool_use_id", b.getString("id")).put("content", out))
                    }
                }
            }
            if (results.length() == 0) {
                val s = said.toString().trim()
                if (s.isNotEmpty()) App.I.say(s)
                return
            }
            msgs.put(JSONObject().put("role", "user").put("content", results))
        }
        App.I.say("Túl sok lépés volt, abbahagytam")
    }

    private fun api(key: String, msgs: JSONArray): JSONObject {
        val model = App.I.pref("model").ifBlank { DEFAULT_MODEL }
        val body = JSONObject().put("model", model).put("max_tokens", 1024)
            .put("system", SYSTEM).put("tools", tools()).put("messages", msgs)
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            c.setRequestProperty("x-api-key", key)
            c.setRequestProperty("anthropic-version", "2023-06-01")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val txt = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(txt).getJSONObject("error").getString("message") }.getOrNull() ?: txt.take(100)
                throw Exception("API $code: $msg")
            }
            return JSONObject(txt)
        } finally { c.disconnect() }
    }

    // ---------- eszközök ----------

    private fun str(d: String) = JSONObject().put("type", "string").put("description", d)
    private fun en(vararg v: String) = JSONObject().put("type", "string").put("enum", JSONArray(v.toList()))
    private fun num(d: String) = JSONObject().put("type", "integer").put("description", d)
    private fun tool(name: String, desc: String, props: JSONObject = JSONObject(), req: List<String> = emptyList()) =
        JSONObject().put("name", name).put("description", desc).put("input_schema",
            JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(req)))

    private fun tools() = JSONArray()
        .put(tool("media", "Zene vezérlése.", JSONObject().put("action", en("play", "pause", "toggle", "next", "previous")), listOf("action")))
        .put(tool("add_todo", "Feladat vagy gondolat felírása a Todoist Inboxba.",
            JSONObject().put("content", str("A feladat szövege magyarul")).put("due", str("Határidő angolul, opcionális")), listOf("content")))
        .put(tool("navigate", "Navigáció indítása a Waze-ben.", JSONObject().put("destination", str("Cím vagy hely neve")), listOf("destination")))
        .put(tool("call_contact", "Névjegy felhívása (a kód maga rákérdez).", JSONObject().put("name", str("Név")), listOf("name")))
        .put(tool("open_app", "App megnyitása a neve alapján.", JSONObject().put("name", str("App neve, pl. Messenger")), listOf("name")))
        .put(tool("read_screen", "A képernyőn látható elemek listája [szám] azonosítókkal."))
        .put(tool("tap", "Elem megnyomása.", JSONObject().put("id", num("Elem száma a read_screen-ből")), listOf("id")))
        .put(tool("type_text", "Szöveg beírása egy szerkeszthető mezőbe.",
            JSONObject().put("id", num("Mező száma")).put("text", str("Beírandó szöveg")), listOf("id", "text")))
        .put(tool("scroll", "Görgetés.", JSONObject().put("id", num("Görgethető elem száma, opcionális")).put("direction", en("forward", "backward")), listOf("direction")))
        .put(tool("global_action", "Rendszergomb.", JSONObject().put("action", en("back", "home", "recents", "notifications")), listOf("action")))
        .put(tool("confirm", "Igen/nem kérdés hangosan a felhasználónak. Visszaad: igen vagy nem.",
            JSONObject().put("question", str("Rövid kérdés")), listOf("question")))
        .put(tool("wait", "Várakozás, pl. app betöltésére.", JSONObject().put("seconds", num("1-5")), listOf("seconds")))

    private fun exec(name: String, a: JSONObject): String {
        val pc = PhoneControlService.I
        return when (name) {
            "media" -> Media.act(App.I, a.getString("action"))
            "add_todo" -> Todoist.add(a.getString("content"), a.optString("due").ifBlank { null }).let { if (it == "ok") "Felírva" else it }
            "navigate" -> {
                val u = "https://waze.com/ul?q=${Uri.encode(a.getString("destination"))}&navigate=yes"
                start(Intent(Intent.ACTION_VIEW, Uri.parse(u)).setPackage("com.waze")); "Waze navigáció elindítva"
            }
            "call_contact" -> call(a.getString("name"))
            "open_app" -> openApp(a.getString("name"))
            "read_screen" -> pc?.dump() ?: NO_ACC
            "tap" -> pc?.tap(a.getInt("id")) ?: NO_ACC
            "type_text" -> pc?.type(a.getInt("id"), a.getString("text")) ?: NO_ACC
            "scroll" -> pc?.scroll(a.optInt("id", -1), a.optString("direction", "forward")) ?: NO_ACC
            "global_action" -> pc?.global(a.getString("action")) ?: NO_ACC
            "confirm" -> if (askYesNo(a.getString("question"))) "igen" else "nem"
            "wait" -> { Thread.sleep((a.optInt("seconds", 1) * 1000L).coerceIn(300L, 5000L)); "ok" }
            else -> "ismeretlen eszköz"
        }
    }

    private fun start(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); App.I.startActivity(i) }

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").trim()

    private fun openApp(name: String): String {
        val pm = App.I.packageManager
        val acts = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val q = norm(name)
        val hit = acts.firstOrNull { norm(it.loadLabel(pm).toString()) == q }
            ?: acts.firstOrNull { norm(it.loadLabel(pm).toString()).contains(q) }
            ?: return "Nincs ilyen app: $name"
        start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(hit.activityInfo.packageName, hit.activityInfo.name)
            .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        Thread.sleep(1500)
        return "Megnyitva: ${hit.loadLabel(pm)}"
    }

    private fun call(name: String): String {
        val ctx = App.I
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return "Nincs névjegy-engedély"
        val cur = ctx.contentResolver.query(Phone.CONTENT_URI, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
            "${Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null) ?: return "Nincs találat"
        val (n, num) = cur.use {
            if (!it.moveToFirst()) return "Nincs ilyen névjegy: $name"
            (it.getString(0) ?: name) to (it.getString(1) ?: "")
        }
        if (!askYesNo("Felhívjam: $n?")) return "Nem kérte a hívást"
        if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num"))); return "Tárcsázó megnyitva"
        }
        start(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")))
        return "Hívás indítva: $n"
    }

    /** Hangosan megkérdezi, és megvárja a kimondott választ. */
    fun askYesNo(q: String): Boolean {
        val latch = CountDownLatch(1)
        var res = false
        VoiceActivity.confirmCb = { res = it; latch.countDown() }
        start(Intent(App.I, VoiceActivity::class.java).putExtra("mode", "confirm").putExtra("q", q))
        latch.await(40, TimeUnit.SECONDS)
        VoiceActivity.confirmCb = null
        return res
    }
}
