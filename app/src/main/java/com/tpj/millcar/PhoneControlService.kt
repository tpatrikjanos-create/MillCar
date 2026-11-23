package com.tpj.millcar

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** Kisegítő szolgáltatás: ezzel olvassa a Haiku a képernyőt, nyom gombot, gépel más appokba. */
class PhoneControlService : AccessibilityService() {
    companion object { @Volatile var I: PhoneControlService? = null }

    private val nodes = mutableListOf<AccessibilityNodeInfo>()

    override fun onServiceConnected() { I = this }
    override fun onDestroy() { I = null; super.onDestroy() }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    @Synchronized
    fun dump(): String {
        nodes.clear()
        val sb = StringBuilder()
        val roots = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION || it.type == AccessibilityWindowInfo.TYPE_SYSTEM }
            .mapNotNull { it.root }
            .filter { it.packageName?.toString() != packageName }
        val list = roots.ifEmpty { listOfNotNull(rootInActiveWindow) }
        for (r in list) {
            sb.append("== ").append(r.packageName).append(" ==\n")
            walk(r, sb, 0)
        }
        if (nodes.isEmpty()) return "A képernyő üres vagy nem olvasható"
        return sb.toString().take(7000)
    }

    private fun walk(n: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 40 || nodes.size > 220 || !n.isVisibleToUser) return
        val t = (n.text ?: n.contentDescription)?.toString()?.replace('\n', ' ')?.take(80)
        val hint = n.hintText?.toString()
        if (!t.isNullOrBlank() || n.isClickable || n.isEditable || n.isScrollable || n.isCheckable) {
            val id = nodes.size
            nodes.add(n)
            sb.append('[').append(id).append("] ").append(n.className?.toString()?.substringAfterLast('.') ?: "")
            if (!t.isNullOrBlank()) sb.append(" \"").append(t).append('"')
            if (!hint.isNullOrBlank() && hint != t) sb.append(" tipp=\"").append(hint).append('"')
            if (n.isClickable) sb.append(" kattintható")
            if (n.isEditable) sb.append(" szerkeszthető")
            if (n.isScrollable) sb.append(" görgethető")
            if (n.isCheckable) sb.append(if (n.isChecked) " bejelölve" else " nincs bejelölve")
            sb.append('\n')
        }
        for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, sb, depth + 1) }
    }

    @Synchronized
    fun tap(id: Int): String {
        val n = nodes.getOrNull(id) ?: return "Nincs ilyen elem, olvasd újra a képernyőt"
        var c: AccessibilityNodeInfo? = n
        while (c != null && !c.isClickable) c = c.parent
        val ok = c?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        if (!ok) {
            val r = Rect(); n.getBoundsInScreen(r)
            val p = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
            dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 60)).build(), null, null)
        }
        Thread.sleep(900)
        return "Megnyomva"
    }

    @Synchronized
    fun type(id: Int, text: String): String {
        val n = nodes.getOrNull(id) ?: return "Nincs ilyen elem, olvasd újra a képernyőt"
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        val ok = n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
        Thread.sleep(400)
        return if (ok) "Beírva" else "Nem sikerült beírni"
    }

    @Synchronized
    fun scroll(id: Int, dir: String): String {
        val n = nodes.getOrNull(id)?.takeIf { it.isScrollable } ?: nodes.firstOrNull { it.isScrollable } ?: return "Nincs görgethető elem"
        val ok = n.performAction(if (dir == "backward") AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        Thread.sleep(700)
        return if (ok) "Görgetve" else "Nem görgethető"
    }

    fun global(a: String): String {
        val code = when (a) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return "ismeretlen"
        }
        performGlobalAction(code)
        Thread.sleep(800)
        return "ok"
    }
}
