package com.ganj.vpn.ui

import com.ganj.vpn.core.subscription.BillingPeriod
import com.ganj.vpn.core.subscription.Money
import com.ganj.vpn.core.subscription.SubscriptionProduct
import com.ganj.vpn.core.subscription.SubscriptionStoreState
import com.ganj.vpn.core.subscription.SubscriptionTier
import com.ganj.vpn.core.subscription.UserService
import com.ganj.vpn.core.subscription.UserServiceStatus

internal const val MOCK_USER_ID = "telegram:10001"

internal data class MockVpnServer(
    val id: String,
    val country: String,
    val city: String,
    val pingMs: Int,
    val loadPercent: Int,
    val vipOnly: Boolean,
)

internal val mockServers = listOf(
    MockVpnServer("de-fra-01", "Germany", "Frankfurt", 42, 23, vipOnly = false),
    MockVpnServer("nl-ams-01", "Netherlands", "Amsterdam", 58, 31, vipOnly = false),
    MockVpnServer("tr-ist-01", "Türkiye", "Istanbul", 71, 47, vipOnly = false),
    MockVpnServer("us-nyc-vip", "United States", "New York", 119, 18, vipOnly = true),
)

internal fun mockSubscriptionState(nowEpochMillis: Long): SubscriptionStoreState {
    val products = listOf(
        SubscriptionProduct(
            id = "free-monthly",
            title = "Free",
            tier = SubscriptionTier.FREE,
            billingPeriod = BillingPeriod.MONTHLY,
            price = Money(0, "USD"),
            dataLimitBytes = 10_000_000_000,
            maxDevices = 1,
            benefits = listOf("3 free locations", "Standard speed", "10 GB traffic"),
        ),
        SubscriptionProduct(
            id = "premium-monthly",
            title = "Premium",
            tier = SubscriptionTier.PREMIUM,
            billingPeriod = BillingPeriod.MONTHLY,
            price = Money(699, "USD"),
            dataLimitBytes = 100_000_000_000,
            maxDevices = 3,
            benefits = listOf("All standard locations", "High speed", "100 GB traffic"),
        ),
        SubscriptionProduct(
            id = "vip-monthly",
            title = "VIP",
            tier = SubscriptionTier.VIP,
            billingPeriod = BillingPeriod.MONTHLY,
            price = Money(999, "USD"),
            dataLimitBytes = null,
            maxDevices = 5,
            benefits = listOf("All global locations", "VIP routes", "Unlimited traffic"),
        ),
    )
    val services = listOf(
        UserService(
            id = "service-premium-10001",
            ownerUserId = MOCK_USER_ID,
            productId = "premium-monthly",
            displayName = "Premium Monthly",
            tier = SubscriptionTier.PREMIUM,
            status = UserServiceStatus.ACTIVE,
            validUntilEpochMillis = nowEpochMillis + 25L * 86_400_000L,
            remainingBytes = 74_000_000_000,
            maxDevices = 3,
            activeDevices = 1,
            allowedServerIds = setOf("de-fra-01", "nl-ams-01", "tr-ist-01"),
        ),
        UserService(
            id = "service-old-free-10001",
            ownerUserId = MOCK_USER_ID,
            productId = "free-monthly",
            displayName = "Free Starter",
            tier = SubscriptionTier.FREE,
            status = UserServiceStatus.EXPIRED,
            validUntilEpochMillis = nowEpochMillis - 86_400_000L,
            remainingBytes = 0,
            maxDevices = 1,
            activeDevices = 0,
            allowedServerIds = setOf("de-fra-01"),
        ),
    )

    return SubscriptionStoreState(
        currentUserId = MOCK_USER_ID,
        products = products,
        services = services,
        selectedProductId = "premium-monthly",
        selectedServiceId = "service-premium-10001",
    )
}

internal fun mockPurchasedService(
    product: SubscriptionProduct,
    nowEpochMillis: Long,
): UserService = UserService(
    id = "service-${product.id}-mock",
    ownerUserId = MOCK_USER_ID,
    productId = product.id,
    displayName = product.title,
    tier = product.tier,
    status = UserServiceStatus.ACTIVE,
    validUntilEpochMillis = nowEpochMillis + when (product.billingPeriod) {
        BillingPeriod.MONTHLY -> 30L
        BillingPeriod.QUARTERLY -> 90L
        BillingPeriod.SEMIANNUAL -> 180L
    } * 86_400_000L,
    remainingBytes = product.dataLimitBytes,
    maxDevices = product.maxDevices,
    activeDevices = 0,
    allowedServerIds = mockServers
        .filter { product.tier == SubscriptionTier.VIP || !it.vipOnly }
        .mapTo(mutableSetOf()) { it.id },
)
