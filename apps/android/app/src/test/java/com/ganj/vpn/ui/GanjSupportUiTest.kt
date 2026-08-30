package com.ganj.vpn.ui

import com.ganj.vpn.core.controlapi.SupportCategory
import com.ganj.vpn.core.controlapi.SupportPriority
import com.ganj.vpn.core.controlapi.SupportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GanjSupportUiTest {
    @Test
    fun `support statuses have explicit Persian labels`() {
        assertEquals("باز", supportStatusLabel(SupportStatus.OPEN))
        assertEquals("منتظر پاسخ شما", supportStatusLabel(SupportStatus.WAITING_USER))
        assertEquals("منتظر پشتیبانی", supportStatusLabel(SupportStatus.WAITING_SUPPORT))
        assertEquals("حل‌شده", supportStatusLabel(SupportStatus.RESOLVED))
        assertEquals("بسته", supportStatusLabel(SupportStatus.CLOSED))
    }

    @Test
    fun `all support categories and priorities have non blank labels`() {
        SupportCategory.entries.forEach { category ->
            assertEquals(false, supportCategoryLabel(category).isBlank())
        }
        SupportPriority.entries.forEach { priority ->
            assertEquals(false, supportPriorityLabel(priority).isBlank())
        }
    }

    @Test
    fun `support notification target only accepts canonical ticket UUID`() {
        val id = "70000000-0000-4000-8000-000000000001"
        assertEquals(id, canonicalSupportTicketIdOrNull(id))
        assertNull(canonicalSupportTicketIdOrNull(null))
        assertNull(canonicalSupportTicketIdOrNull("not-a-ticket"))
        assertNull(canonicalSupportTicketIdOrNull("70000000-0000-4000-8000-00000000000Z"))
    }
}
