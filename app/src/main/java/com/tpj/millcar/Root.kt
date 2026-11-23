package com.tpj.millcar

import android.content.ComponentName
import android.graphics.Rect

/** Root-os segédek: a Waze lebegő ablakba tétele és pontos helyre igazítása. Háttérszálról hívd. */
object Root {
    fun run(cmd: String): String? = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        if (p.waitFor() == 0) out else null
    } catch (e: Exception) { null }

    val available: Boolean get() = run("id")?.contains("uid=0") == true

    /** Waze indítása lebegő (freeform) ablakban, majd átméretezés a megadott téglalapra. */
    fun placeWaze(cmp: ComponentName, r: Rect): Boolean {
        run("settings put global enable_freeform_support 1")
        if (run("am start --windowingMode 5 -n ${cmp.flattenToShortString()}") == null) return false
        Thread.sleep(1200)
        val id = taskId() ?: return false
        return run("am task resize $id ${r.left} ${r.top} ${r.right} ${r.bottom}") != null
    }

    private fun taskId(): Int? {
        val out = run("am stack list") ?: return null
        return Regex("taskId=(\\d+): ${Regex.escape(WAZE)}/").find(out)?.groupValues?.get(1)?.toIntOrNull()
    }
}
