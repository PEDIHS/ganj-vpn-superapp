package com.ganj.vpn.presentation

import com.ganj.vpn.core.billing.BillingFailure
import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.CatalogProduct
import com.ganj.vpn.core.controlapi.ServiceStatus
import com.ganj.vpn.core.controlapi.SubscriptionTier
import com.ganj.vpn.core.controlapi.UserService

class GanjPresentationMapper {
    fun catalog(result: ApiResult<List<CatalogProduct>>): ContentState<PlanUiModel> = when (result) {
        is ApiResult.Success -> if (result.value.isEmpty()) {
            ContentState.Empty
        } else {
            ContentState.Ready(result.value.map(::plan))
        }
        is ApiResult.Failure -> contentFailure(result.error)
    }

    fun services(result: ApiResult<List<UserService>>): ContentState<ServiceUiModel> = when (result) {
        is ApiResult.Success -> if (result.value.isEmpty()) {
            ContentState.Empty
        } else {
            ContentState.Ready(result.value.map(::service))
        }
        is ApiResult.Failure -> contentFailure(result.error)
    }

    fun apiFailure(error: ApiError): UiFailure = when (error) {
        is ApiError.AuthenticationRequired,
        is ApiError.AuthenticationExpired,
        -> UiFailure(UiFailureKind.AUTHENTICATION, "auth.required", retryable = false, error.requestId)
        is ApiError.Forbidden -> UiFailure(UiFailureKind.ENTITLEMENT, "entitlement.denied", false, error.requestId)
        is ApiError.Conflict -> UiFailure(UiFailureKind.CONFLICT, "request.conflict", true, error.requestId)
        is ApiError.RateLimited -> UiFailure(UiFailureKind.RATE_LIMIT, "request.rate_limited", true, error.requestId)
        is ApiError.Network -> UiFailure(UiFailureKind.NETWORK, "network.unavailable", true, error.requestId)
        is ApiError.Server -> UiFailure(UiFailureKind.SERVER, "server.unavailable", error.retryable, error.requestId)
        is ApiError.Validation -> UiFailure(UiFailureKind.PROTOCOL, "request.invalid", false, error.requestId)
        is ApiError.NotFound -> UiFailure(UiFailureKind.PROTOCOL, "resource.not_found", false, error.requestId)
        is ApiError.Protocol -> UiFailure(UiFailureKind.PROTOCOL, "response.invalid", false, error.requestId)
    }

    fun billingFailure(failure: BillingFailure): UiFailure = UiFailure(
        kind = UiFailureKind.BILLING,
        messageKey = failure.safeMessageKey,
        retryable = failure.retryable,
    )

    private fun plan(product: CatalogProduct): PlanUiModel = PlanUiModel(
        id = product.id,
        code = product.code,
        title = product.name,
        tier = product.tier.toUiTier(),
        durationDays = product.durationDays,
        trafficLimitBytes = product.trafficLimitBytes,
        deviceLimit = product.deviceLimit,
        benefits = product.features,
        amountMinor = product.price.amountMinor,
        currency = product.price.currency,
    )

    private fun service(service: UserService): ServiceUiModel = ServiceUiModel(
        entitlementId = service.id,
        displayName = service.name,
        status = when (service.status) {
            ServiceStatus.PENDING -> ServiceUiStatus.PENDING
            ServiceStatus.ACTIVE -> ServiceUiStatus.ACTIVE
            ServiceStatus.DISABLED -> ServiceUiStatus.DISABLED
            ServiceStatus.EXPIRED -> ServiceUiStatus.EXPIRED
            ServiceStatus.REVOKED -> ServiceUiStatus.REVOKED
        },
        tier = service.tier.toUiTier(),
        countryCode = service.countryCode,
        trafficLimitBytes = service.trafficLimitBytes,
        trafficUsedBytes = service.trafficUsedBytes,
        expiresAt = service.expiresAt,
        deviceLimit = service.deviceLimit,
        allowedProtocols = service.allowedProtocols.mapTo(linkedSetOf()) { it.name },
    )

    private fun SubscriptionTier.toUiTier(): UiTier = when (this) {
        SubscriptionTier.FREE -> UiTier.FREE
        SubscriptionTier.PREMIUM -> UiTier.PREMIUM
        SubscriptionTier.VIP -> UiTier.VIP
    }

    private fun <T> contentFailure(error: ApiError): ContentState<T> = when (error) {
        is ApiError.AuthenticationRequired,
        is ApiError.AuthenticationExpired,
        -> ContentState.AuthRequired
        else -> ContentState.Error(apiFailure(error))
    }
}
