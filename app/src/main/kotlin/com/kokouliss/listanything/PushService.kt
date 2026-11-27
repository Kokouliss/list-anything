package com.kokouliss.listanything

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kokouliss.listanything.core.domain.model.OpenChat
import com.kokouliss.listanything.core.ui.R as CoreR

/**
 * Shows a notification from firebase/functions that arrives while the app is open. When the app is
 * closed the system shows it by itself and this is never called; without this, an open app drops it.
 */
// shortcut: no onNewToken, so a renewed address reaches the server at the next app start (the app
// registers on every start). Override it if notifications go missing until the app is opened.
class PushService : FirebaseMessagingService() {

    @SuppressLint("MissingPermission") // Returns early when notifications are off.
    override fun onMessageReceived(message: RemoteMessage) {
        val push = message.notification ?: return
        // A chat message that lands in the chat on screen is already being read.
        if (message.data["chat"] != null && message.data["spaceId"] == OpenChat.spaceId) return
        val notifications = NotificationManagerCompat.from(this)
        if (!notifications.areNotificationsEnabled()) return

        // The same words the system would show: the string the server names, in this phone's language.
        val text = pushText(push.bodyLocalizationKey)
            ?.let { runCatching { getString(it, *push.bodyLocalizationArgs.orEmpty()) }.getOrNull() }
            ?: push.body
        // Opens the list or the chat, like a tap on a notification the system showed.
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("spaceId", message.data["spaceId"])
            .putExtra("listId", message.data["listId"])
            .putExtra("chat", message.data["chat"])
        val tap = PendingIntent.getActivity(
            this,
            push.tag.hashCode(),
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, push.channelId ?: UPDATES_CHANNEL)
            .setSmallIcon(CoreR.drawable.core_ui_ic_checklist)
            .setContentTitle(push.title)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        // The server's tag, so a newer notification about the same list replaces the older one.
        notifications.notify(push.tag, 0, notification)
    }
}

/** The app string that firebase/functions/notify.js names in a notification, or null for one this app does not know. */
internal fun pushText(key: String?): Int? = when (key) {
    "push_item_added" -> R.string.push_item_added
    "push_item_checked" -> R.string.push_item_checked
    "push_items_added_checked" -> R.string.push_items_added_checked
    "push_list_changed" -> R.string.push_list_changed
    "push_going_shopping" -> R.string.push_going_shopping
    "push_reminder" -> R.string.push_reminder
    "push_chat_text" -> R.string.push_chat_text
    "push_chat_photo" -> R.string.push_chat_photo
    "push_chat_voice" -> R.string.push_chat_voice
    else -> null
}
