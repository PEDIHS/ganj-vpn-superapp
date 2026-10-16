package com.ganj.vpn.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjBotCatalogTest {
    private fun plan(code: String, panel: String, category: String, name: String) = GanjBotProduct(
        code = code, panelId = panel, panel = "اشتراک " + panel,
        category = category, name = name, description = "",
        priceToman = 320_000, volumeGb = 30.0, durationDays = 30,
        badge = "", featured = false, oneBuy = false,
    )

    @Test fun botPanelsAndCategoriesUseSourceOrder() {
        val plans = listOf(
            plan("1", "tr", "یک کاربر", "ترکیه ۳۰ گیگ"),
            plan("2", "de", "دو کاربر", "آلمان ۳۰ گیگ"),
            plan("3", "tr", "دو کاربر", "ترکیه ۶۰ گیگ"),
        )
        assertEquals(listOf("tr", "de"), botStorePanels(plans).map { it.first })
        assertEquals(listOf("یک کاربر", "دو کاربر"), botStoreCategories(plans, "tr"))
        assertEquals(listOf("یک کاربر", "دو کاربر"), botStoreCategories(plans, null))
        assertEquals(listOf("3"), botStoreFilter(plans, "tr", "دو کاربر", "").map { it.code })
    }

    @Test fun searchStaysInsideSelectedPanelAndCategory() {
        val plans = listOf(
            plan("1", "tr", "تک کاربر", "سرور ترکیه"),
            plan("2", "de", "تک کاربر", "سرور آلمان"),
            plan("3", "tr", "دو کاربر", "سرور ترکیه"),
        )
        assertEquals(listOf("1"), botStoreFilter(plans, "tr", "تک کاربر", "ترکیه").map { it.code })
        assertTrue(botStoreFilter(plans, "tr", "تک کاربر", "آلمان").isEmpty())
    }

    @Test fun officialCheckoutUsesExactBotProductAndPanel() {
        val product = plan("abc123", "panel-7", "دسته", "پلن")
        assertEquals(
            "https://bot.pedramhs.ir/site/?page=product&id=abc123&panel=panel-7",
            product.purchaseUrl,
        )
    }
    @Test fun nativeVipAliasesUseOfficialVolumeButNeverChangeBotCheckout() {
        val legacy = plan("original-30", "vip", "1 کاربر", "💎 ویژه تانل 30 گیگ 💎")
            .copy(panel = "💎 اشتراک VIP مولتی لوکیشن حجمی 💎", volumeGb = 30.0, deviceLimit = 1)
        assertEquals("اکونومی · ۳۰ گیگ · ۱ دستگاه", legacy.appDisplayName())
        assertEquals("استاندارد · ۵۰ گیگ · ۲ دستگاه",
            legacy.copy(volumeGb = 50.0, category = "2 کاربر").appDisplayName())
        assertEquals("حرفه‌ای · ۱۰۰ گیگ · ۳ دستگاه",
            legacy.copy(volumeGb = 100.0, category = "3 کاربر").appDisplayName())
        assertEquals("پلاس · ۸۰ گیگ · ۱ دستگاه",
            legacy.copy(volumeGb = 80.0).appDisplayName())
        assertEquals("پلاس · ۸۰ گیگ · ۲ دستگاه",
            legacy.copy(volumeGb = 80.0, category = "2 کاربر", appTier = "plus",
                appTierName = "پلاس", deviceLimit = 2).appDisplayName())
        assertEquals(legacy.name,
            legacy.copy(category = "اشتراک VIP نت ملی 🇮🇷").appDisplayName())
        assertEquals(legacy.name,
            legacy.copy(panel = "اشتراک سرور ترکیه").appDisplayName())
        assertTrue(botStoreFilter(listOf(legacy), null, null, "اکونومی").contains(legacy))
        assertEquals(
            "https://bot.pedramhs.ir/site/?page=product&id=original-30&panel=vip",
            legacy.purchaseUrl,
        )
    }

    @Test fun nativeAmountInputNormalizesPersianAndArabicDigitsWithoutGroupingCharacters() {
        assertEquals("140000", botStoreAmountDigits("۱۴۰٬۰۰۰"))
        assertEquals("250000", botStoreAmountDigits("٢٥٠,٠٠٠ تومان"))
        assertEquals("", botStoreAmountDigits("نامعتبر"))
    }
}
