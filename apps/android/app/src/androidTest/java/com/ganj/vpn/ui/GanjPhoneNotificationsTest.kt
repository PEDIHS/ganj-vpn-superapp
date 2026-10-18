package com.ganj.vpn.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ganj.vpn.core.controlapi.NotificationKind
import com.ganj.vpn.core.controlapi.UserNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GanjPhoneNotificationsTest {
    @Test fun urgentAccountNoticeUsesHighPriorityNativeChannelWithoutDuplicatePost() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            runCatching { instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.POST_NOTIFICATIONS,
            ) }
        }
        assumeTrue(Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val now = System.currentTimeMillis()
        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(now))
        val id = UUID.randomUUID().toString()
        val notice = UserNotification(
            id = id, kind = NotificationKind.PAYMENT_FAILURE,
            title = "پرداخت نیاز به بررسی دارد",
            body = "وضعیت فاکتور خود را بررسی کنید.",
            action = null, read = false, createdAt = utc,
        )
        GanjPhoneNotifications.publish(context,"test_verified_user",listOf(notice),now)
        val first = manager.activeNotifications.filter { it.tag?.endsWith(":" + id) == true }
        assertEquals(1, first.size)
        if (Build.VERSION.SDK_INT >= 26) {
            assertEquals(NotificationManager.IMPORTANCE_HIGH,
                manager.getNotificationChannel("ganj_important_alerts_v1").importance)
        }
        GanjPhoneNotifications.publish(context,"test_verified_user",listOf(notice),now)
        assertEquals(1, manager.activeNotifications.count { it.tag?.endsWith(":" + id) == true })
        manager.cancel(first.single().tag, 42)
    }
}
