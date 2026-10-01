package com.example.notification

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

object NotificationStore {

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    fun initTts(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsReady = true
                    try {
                        val langResult = tts?.setLanguage(Locale("hi", "IN"))
                        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                            tts?.setLanguage(Locale.US)
                        }
                    } catch (e: Exception) {
                        tts?.setLanguage(Locale.US)
                    }
                }
            }
        }
    }

    fun isNotificationAccessGranted(context: Context): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null) {
                    if (TextUtils.equals(packageName, cn.packageName)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun addNotification(notification: AppNotification) {
        val current = _notifications.value.toMutableList()
        // Remove existing with same id if any to update
        current.removeAll { it.id == notification.id }
        current.add(0, notification)
        // Keep max 50
        if (current.size > 50) {
            _notifications.value = current.take(50)
        } else {
            _notifications.value = current
        }
    }

    fun removeNotification(id: String) {
        _notifications.value = _notifications.value.filter { it.id != id }
    }

    fun clearAllNotifications() {
        _notifications.value = emptyList()
    }

    fun markAsRead(id: String) {
        _notifications.value = _notifications.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
    }

    fun readNotificationAloud(notification: AppNotification, onReadComplete: (() -> Unit)? = null) {
        markAsRead(notification.id)
        val textToSpeak = "${notification.appName} se notification: ${notification.title}. ${notification.content}"
        speakText(textToSpeak, onReadComplete)
    }

    fun readAllNotificationsAloud(onReadComplete: (() -> Unit)? = null) {
        val list = _notifications.value
        if (list.isEmpty()) {
            speakText("Abhi koi naya notification nahi hai.", onReadComplete)
            return
        }
        val builder = StringBuilder("Aapke pass ${list.size} notification hain. ")
        list.take(5).forEachIndexed { index, notif ->
            builder.append("Notification ${index + 1}: ${notif.appName} se, ${notif.title}, ${notif.content}. ")
        }
        speakText(builder.toString(), onReadComplete)
    }

    fun speakText(text: String, onComplete: (() -> Unit)? = null) {
        try {
            if (isTtsReady && tts != null) {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MJNotificationTTS")
            }
        } catch (e: Exception) {
            Log.e("NotificationStore", "TTS speak failed", e)
        }
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (e: Exception) {}
    }

    fun getNotificationsSummary(): String {
        val list = _notifications.value
        if (list.isEmpty()) {
            return "No active notifications on device."
        }
        val sb = StringBuilder("Current notifications (${list.size}):\n")
        list.take(8).forEachIndexed { index, notif ->
            sb.append("${index + 1}. [${notif.appName}] ${notif.title}: ${notif.content}\n")
        }
        return sb.toString()
    }

    private fun getDefaultSampleNotifications(): List<AppNotification> {
        val now = System.currentTimeMillis()
        return listOf(
            AppNotification(
                id = "sample_wa_1",
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                title = "Rahul Sharma",
                content = "Bhai, meeting kab shuru hogi? Call me when free.",
                timestamp = now - 1000 * 60 * 2,
                category = "chat"
            ),
            AppNotification(
                id = "sample_gmail_1",
                packageName = "com.google.android.gm",
                appName = "Gmail",
                title = "Google Security Alert",
                content = "New sign-in detected on your Google Account.",
                timestamp = now - 1000 * 60 * 15,
                category = "email"
            ),
            AppNotification(
                id = "sample_pay_1",
                packageName = "com.google.android.apps.nbu.paisa.user",
                appName = "Google Pay",
                title = "Payment Received",
                content = "₹1,500 received from Amit Verma successfully.",
                timestamp = now - 1000 * 60 * 45,
                category = "payment"
            )
        )
    }
}
