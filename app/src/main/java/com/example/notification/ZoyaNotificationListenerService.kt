package com.example.notification

import android.app.Notification
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class ZoyaNotificationListenerService : NotificationListenerService() {

    companion object {
        var isConnected = false
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.i("ZoyaNotifService", "Notification Listener connected!")
        loadActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.i("ZoyaNotifService", "Notification Listener disconnected!")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        processNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        val id = "${sbn.packageName}_${sbn.id}"
        NotificationStore.removeNotification(id)
    }

    private fun loadActiveNotifications() {
        try {
            val activeNotifs = activeNotifications ?: return
            for (sbn in activeNotifs) {
                processNotification(sbn)
            }
        } catch (e: Exception) {
            Log.e("ZoyaNotifService", "Error loading active notifications", e)
        }
    }

    private fun processNotification(sbn: StatusBarNotification) {
        try {
            val notification = sbn.notification ?: return
            val packageName = sbn.packageName ?: "unknown"

            // Ignore our own ongoing foreground service notification
            if (packageName == applicationContext.packageName) return

            val extras = notification.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() 
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString() 
                ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() 
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() 
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                ?: ""

            if (title.isBlank() && text.isBlank()) return

            val pm = packageManager
            val appName = try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName.substringAfterLast('.').capitalize()
            }

            val category = when {
                packageName.contains("whatsapp") || packageName.contains("telegram") || packageName.contains("messaging") -> "chat"
                packageName.contains("gm") || packageName.contains("mail") || packageName.contains("email") -> "email"
                packageName.contains("pay") || packageName.contains("bank") || packageName.contains("bhim") -> "payment"
                packageName.contains("dialer") || packageName.contains("phone") -> "call"
                else -> "general"
            }

            val appNotification = AppNotification(
                id = "${packageName}_${sbn.id}",
                packageName = packageName,
                appName = appName,
                title = title.ifBlank { appName },
                content = text.ifBlank { "Notification alert" },
                timestamp = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
                category = category
            )

            NotificationStore.addNotification(appNotification)
        } catch (e: Exception) {
            Log.e("ZoyaNotifService", "Error processing notification", e)
        }
    }
}
