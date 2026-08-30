package com.ganj.vpn.ui

import com.ganj.vpn.core.controlapi.NotificationActionType
import com.ganj.vpn.core.controlapi.NotificationKind
import org.junit.Assert.assertEquals
import org.junit.Test

class GanjNotificationsUiTest {
    @Test
    fun `notification categories have explicit Persian labels`() {
        assertEquals("تمدید سرویس", notificationKindLabel(NotificationKind.SUBSCRIPTION_EXPIRY))
        assertEquals("خرید", notificationKindLabel(NotificationKind.PURCHASE_SUCCESS))
        assertEquals("پرداخت", notificationKindLabel(NotificationKind.PAYMENT_FAILURE))
        assertEquals("نگهداری", notificationKindLabel(NotificationKind.MAINTENANCE))
        assertEquals("امنیت", notificationKindLabel(NotificationKind.SECURITY_UPDATE))
        assertEquals("پشتیبانی", notificationKindLabel(NotificationKind.SUPPORT_REPLY))
        assertEquals("پیشنهاد", notificationKindLabel(NotificationKind.MARKETING))
    }

    @Test
    fun `notification actions use explicit in-app destination labels`() {
        assertEquals("رفتن به فروشگاه", notificationActionLabel(NotificationActionType.OPEN_STORE))
        assertEquals("مشاهده سرویس", notificationActionLabel(NotificationActionType.OPEN_SUBSCRIPTION))
        assertEquals("مشاهده پشتیبانی", notificationActionLabel(NotificationActionType.OPEN_SUPPORT))
        assertEquals("مشاهده کیف پول", notificationActionLabel(NotificationActionType.OPEN_WALLET))
        assertEquals("باز کردن تنظیمات", notificationActionLabel(NotificationActionType.OPEN_SETTINGS))
    }
}
