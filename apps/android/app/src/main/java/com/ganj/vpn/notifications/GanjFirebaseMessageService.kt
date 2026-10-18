package com.ganj.vpn.notifications

import com.ganj.vpn.BuildConfig
import com.ganj.vpn.composition.GanjMultiAccountVault
import com.ganj.vpn.core.controlapi.NotificationKind
import com.ganj.vpn.core.controlapi.UserNotification
import com.ganj.vpn.ui.GanjPhoneNotifications
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM DATA-only push (no system-duplicated notification payload).
 * A push is only a user-scoped, public-safe notice, never a balance or
 * authorization grant. FCM token must be bound to active authenticated
 * device on the server. Old account notifications are rejected here.
 */
class GanjFirebaseMessageService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (!BuildConfig.FCM_ENABLED) return
        val data = message.data
        if (data["ganj_event"] != "1") return
        val account = data["account_id"] ?: return
        val vault = GanjMultiAccountVault(applicationContext)
        if (vault.currentUserId() != account || account !in vault.verifiedAccounts()) return
        val id = data["notification_id"] ?: return
        if (!id.matches(Regex("^[a-fA-F0-9-]{36}$"))) return
        val kind = data["kind"]?.uppercase()?.let {
            runCatching { NotificationKind.valueOf(it) }.getOrNull()
        } ?: return
        val title = data["title"]?.takeIf { it.isNotBlank() && it.length <= 160 } ?: return
        val body = data["body"]?.takeIf { it.isNotBlank() && it.length <= 2000 } ?: return
        val createdAt = data["created_at"] ?: return
        val item = UserNotification(
            id = id, kind = kind, title = title, body = body, read = false,
            action = null, createdAt = createdAt,
        )
        GanjPhoneNotifications.publish(applicationContext, account, listOf(item))
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Registration occurs after verified account sign-in through the
        // authenticated /notifications/push-device endpoint. Never send a
        // token to an unverified guest or embed it in a Telegram URL.
    }
}
