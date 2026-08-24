package com.ganj.vpn.core.subscription

enum class SubscriptionTier {
    FREE,
    PREMIUM,
    VIP,
}

enum class BillingPeriod {
    MONTHLY,
    QUARTERLY,
    SEMIANNUAL,
}

data class Money(
    val amountMinor: Long,
    val currencyCode: String,
) {
    init {
        require(amountMinor >= 0) { "Money cannot be negative" }
        require(currencyCode.length == 3) { "Currency must use an ISO 4217 code" }
    }
}

data class SubscriptionProduct(
    val id: String,
    val title: String,
    val tier: SubscriptionTier,
    val billingPeriod: BillingPeriod,
    val price: Money,
    val dataLimitBytes: Long?,
    val maxDevices: Int,
    val benefits: List<String>,
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(dataLimitBytes == null || dataLimitBytes > 0)
        require(maxDevices > 0)
    }
}

enum class UserServiceStatus {
    ACTIVE,
    SUSPENDED,
    EXPIRED,
    EXHAUSTED,
}

data class UserService(
    val id: String,
    val ownerUserId: String,
    val productId: String,
    val displayName: String,
    val tier: SubscriptionTier,
    val status: UserServiceStatus,
    val validUntilEpochMillis: Long,
    val remainingBytes: Long?,
    val maxDevices: Int,
    val activeDevices: Int,
    val allowedServerIds: Set<String>,
) {
    init {
        require(id.isNotBlank())
        require(ownerUserId.isNotBlank())
        require(productId.isNotBlank())
        require(displayName.isNotBlank())
        require(validUntilEpochMillis > 0)
        require(remainingBytes == null || remainingBytes >= 0)
        require(maxDevices > 0)
        require(activeDevices >= 0)
        require(allowedServerIds.isNotEmpty())
    }
}

/**
 * The only user-facing connection command in the product.
 *
 * It deliberately carries only account entitlement identifiers and no connection material. The
 * trusted backend resolves [serviceId] and [serverId] to an encrypted, short-lived profile after
 * it has re-validated ownership. User-supplied profiles are not part of this boundary.
 */
data class SubscriptionConnectionCommand(
    val userId: String,
    val serviceId: String,
    val serverId: String,
)

enum class ConnectionDenial {
    NOT_AUTHENTICATED,
    WRONG_OWNER,
    SERVICE_NOT_ACTIVE,
    SUBSCRIPTION_EXPIRED,
    TRAFFIC_EXHAUSTED,
    DEVICE_LIMIT_REACHED,
    SERVER_NOT_INCLUDED,
}

sealed interface ConnectionDecision {
    data class Allowed(val command: SubscriptionConnectionCommand) : ConnectionDecision
    data class Denied(val reason: ConnectionDenial) : ConnectionDecision
}

class SubscriptionConnectionPolicy {
    fun evaluate(
        service: UserService,
        userId: String,
        serverId: String,
        nowEpochMillis: Long,
    ): ConnectionDecision {
        val denial = when {
            userId.isBlank() -> ConnectionDenial.NOT_AUTHENTICATED
            service.ownerUserId != userId -> ConnectionDenial.WRONG_OWNER
            service.status == UserServiceStatus.SUSPENDED -> ConnectionDenial.SERVICE_NOT_ACTIVE
            service.status == UserServiceStatus.EXPIRED -> ConnectionDenial.SUBSCRIPTION_EXPIRED
            service.status == UserServiceStatus.EXHAUSTED -> ConnectionDenial.TRAFFIC_EXHAUSTED
            service.validUntilEpochMillis <= nowEpochMillis -> ConnectionDenial.SUBSCRIPTION_EXPIRED
            service.remainingBytes == 0L -> ConnectionDenial.TRAFFIC_EXHAUSTED
            service.activeDevices >= service.maxDevices -> ConnectionDenial.DEVICE_LIMIT_REACHED
            serverId !in service.allowedServerIds -> ConnectionDenial.SERVER_NOT_INCLUDED
            else -> null
        }

        return if (denial == null) {
            ConnectionDecision.Allowed(
                SubscriptionConnectionCommand(
                    userId = userId,
                    serviceId = service.id,
                    serverId = serverId,
                ),
            )
        } else {
            ConnectionDecision.Denied(denial)
        }
    }
}
