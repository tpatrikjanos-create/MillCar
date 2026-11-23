package com.tpj.millcar

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Todoist {
    /** Feladat az Inboxba. Visszatér: "ok" vagy felolvasható hibaüzenet. Háttérszálról hívd. */
    fun add(content: String, due: String?): String {
        val tok = App.I.pref("todoist_token")
        if (tok.isBlank()) return "Nincs Todoist token beállítva"
        val body = JSONObject().put("content", content)
        if (!due.isNullOrBlank()) body.put("due_string", due).put("due_lang", "en")
        for (url in listOf("https://api.todoist.com/api/v1/tasks", "https://api.todoist.com/rest/v2/tasks")) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 10000
                c.readTimeout = 15000
                c.setRequestProperty("Authorization", "Bearer $tok")
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                if (code in 200..299) return "ok"
                if (code == 404 || code == 410) continue
                return "Todoist hiba, kód $code"
            } catch (e: Exception) {
                return "Todoist hiba: ${e.message}"
            } finally {
                c.disconnect()
            }
        }
        return "Todoist hiba"
    }
}
