package com.tpj.millcar

import android.app.Notification
import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

const val WAZE = "com.waze"

/** A Waze értesítéséből kiolvasott navigációs adat (következő kanyar / érkezés). */
object WazeInfo {
    @Volatile var title = ""
    @Volatile var text = ""
    @Volatile var icon: Drawable? = null
    var onChange: (() -> Unit)? = null

    fun set(t: String, x: String, ic: Drawable?) {
        title = t; text = x; icon = ic
        App.I.ui.post { onChange?.invoke() }
    }
}

/** Zenevezérléshez kell, és ebből olvassuk a Waze kanyar-értesítését is. */
class NotifListener : NotificationListenerService() {
    override fun onListenerConnected() {
        runCatching { activeNotifications?.filter { it.packageName == WAZE }?.forEach { onNotificationPosted(it) } }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName != WAZE) return
        val e = sbn.notification.extras
        val t = e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val x = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
        if (t.isBlank() && x.isBlank()) return
        val ic = runCatching { sbn.notification.getLargeIcon()?.loadDrawable(this) }.getOrNull()
        WazeInfo.set(t, x, ic)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.packageName == WAZE) WazeInfo.set("", "", null)
    }
}
