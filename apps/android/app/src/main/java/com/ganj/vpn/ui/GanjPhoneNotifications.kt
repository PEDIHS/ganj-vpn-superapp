package com.ganj.vpn.ui

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.ganj.vpn.MainActivity
import com.ganj.vpn.R
import com.ganj.vpn.core.controlapi.NotificationKind
import com.ganj.vpn.core.controlapi.UserNotification
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Native phone notifications. Android controls heads-up presentation via
 * channel preferences, user permission, DND and OEM policy.
 * No background polling service is created here.
 */
internal object GanjPhoneNotifications {
    private const val IMPORTANT_CHANNEL = "ganj_important_alerts_v1"
    private const val NORMAL_CHANNEL = "ganj_account_updates_v1"
    private const val FRESH_MS = 10 * 60 * 1000L
    private const val SEEN_LIMIT = 96

    fun publish(context: Context, account: String?, events: List<UserNotification>, nowMs: Long = System.currentTimeMillis()) {
        if (account.isNullOrBlank()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        configureChannels(manager)
        val identity = MessageDigest.getInstance("SHA-256").digest(account.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
        val prefs = context.getSharedPreferences("ganj_visible_account_notifications", Context.MODE_PRIVATE)
        val key = "shown_" + identity
        synchronized(this) {
            val delivered = prefs.getString(key, "")?.split(",")?.filter(String::isNotBlank).orEmpty().toMutableList()
            var modified = false
            for (entry in events) {
                if (entry.read || entry.id in delivered || !recent(entry.createdAt, nowMs)) continue
                val urgent = entry.kind == NotificationKind.SUBSCRIPTION_EXPIRY ||
                    entry.kind == NotificationKind.SUBSCRIPTION_USAGE ||
                    entry.kind == NotificationKind.PAYMENT_FAILURE ||
                    entry.kind == NotificationKind.SECURITY_UPDATE
                val launch = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("ganj_notification_id", entry.id)
                }
                val tap = PendingIntent.getActivity(context, entry.id.hashCode(), launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val builder = if (Build.VERSION.SDK_INT >= 26)
                    Notification.Builder(context, if (urgent) IMPORTANT_CHANNEL else NORMAL_CHANNEL)
                else Notification.Builder(context)
                val notification = builder.setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(entry.title.take(160))
                    .setContentText(entry.body.take(240))
                    .setStyle(Notification.BigTextStyle().bigText(entry.body.take(1000)))
                    .setContentIntent(tap)
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .setWhen(nowMs)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setPriority(if (urgent) Notification.PRIORITY_HIGH else Notification.PRIORITY_DEFAULT)
                    .build()
                manager.notify("ganj:" + identity + ":" + entry.id, 42, notification)
                delivered.add(entry.id)
                modified = true
            }
            if (modified) prefs.edit().putString(key, delivered.takeLast(SEEN_LIMIT).joinToString(",")).apply()
        }
    }

    private fun recent(value: String, now: Long): Boolean {
        // java.time.Instant needs API 26 unless desugaring is enabled;
        // keep compatibility with all supported Android 24+ devices.
        val whenMs = runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US)
            parser.isLenient = false
            parser.timeZone = TimeZone.getTimeZone("UTC")
            parser.parse(value)?.time
        }.getOrNull() ?: return false
        val age = now - whenMs
        return age in 0..FRESH_MS
    }

    private fun configureChannels(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < 26) return
        manager.createNotificationChannel(NotificationChannel(
            IMPORTANT_CHANNEL, "هشدارهای فوری گنج", NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "مشکلات پرداخت، هشدار مصرف و انقضای اشتراک" })
        manager.createNotificationChannel(NotificationChannel(
            NORMAL_CHANNEL, "اعلان‌های حساب گنج", NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "خرید موفق، پیام پشتیبانی و اطلاع‌رسانی‌های معمولی" })
    }
}
