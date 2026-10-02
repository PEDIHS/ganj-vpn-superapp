package com.ganj.vpn.ui

import com.ganj.vpn.core.controlapi.Money
import com.ganj.vpn.core.controlapi.WalletTransaction
import com.ganj.vpn.core.controlapi.WalletTransactionDirection
import com.ganj.vpn.core.controlapi.WalletTransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class GanjWalletTransactionFilterTest {
    private val transactions = listOf(
        transaction("00000000-0000-4000-8000-000000000001", WalletTransactionType.TOPUP, WalletTransactionDirection.CREDIT),
        transaction("00000000-0000-4000-8000-000000000002", WalletTransactionType.PURCHASE, WalletTransactionDirection.DEBIT),
        transaction("00000000-0000-4000-8000-000000000003", WalletTransactionType.REFUND, WalletTransactionDirection.CREDIT),
        transaction("00000000-0000-4000-8000-000000000004", WalletTransactionType.PURCHASE, WalletTransactionDirection.DEBIT),
    )

    @Test
    fun `null filter preserves the real ledger order`() {
        assertEquals(transactions, filterWalletTransactions(transactions, null))
    }

    @Test
    fun `type filter returns only matching real ledger entries`() {
        val result = filterWalletTransactions(transactions, WalletTransactionType.PURCHASE)
        assertEquals(2, result.size)
        assertEquals(listOf(transactions[1], transactions[3]), result)
    }

    @Test
    fun `missing type returns empty without inventing rows`() {
        assertEquals(emptyList<WalletTransaction>(), filterWalletTransactions(transactions, WalletTransactionType.REVERSAL))
    }

    private fun transaction(
        id: String,
        type: WalletTransactionType,
        direction: WalletTransactionDirection,
    ) = WalletTransaction(
        id = id,
        type = type,
        direction = direction,
        amount = Money(1000, "IRR"),
        balanceAfter = Money(5000, "IRR"),
        referenceType = "order",
        referenceId = "ref-$id",
        description = null,
        createdAt = "2026-08-30T08:00:00Z",
    )
}
