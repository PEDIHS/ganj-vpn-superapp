package com.ganj.vpn.composition

import android.app.Activity
import android.app.Application
import com.ganj.vpn.core.billing.BillingGateway
import com.ganj.vpn.core.billing.BillingGatewayRegistry
import com.ganj.vpn.core.billing.BillingProvider
import com.ganj.vpn.core.controlapi.AccessToken
import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.AuthTokenProvider
import com.ganj.vpn.core.controlapi.CatalogProduct
import com.ganj.vpn.core.controlapi.CheckoutCommand
import com.ganj.vpn.core.controlapi.CheckoutOrder
import com.ganj.vpn.core.controlapi.ConnectionProfileCommand
import com.ganj.vpn.core.controlapi.ConnectionProfileLease
import com.ganj.vpn.core.controlapi.ControlApiRepository
import com.ganj.vpn.core.controlapi.ControlApiRepositoryFactory
import com.ganj.vpn.core.controlapi.PurchaseChannel
import com.ganj.vpn.core.controlapi.UserService
import com.ganj.vpn.core.playbilling.GooglePlayBillingAdapter
import com.ganj.vpn.core.playbilling.PlayBillingLifecycleBridge
import com.ganj.vpn.core.playbilling.PlayPurchaseEvent
import com.ganj.vpn.core.playbilling.PlayPurchaseObserver
import com.ganj.vpn.presentation.AuthenticatedCheckoutSession
import com.ganj.vpn.presentation.CheckoutActionHandle
import com.ganj.vpn.presentation.CheckoutActionVault
import com.ganj.vpn.presentation.CheckoutEffectResult
import com.ganj.vpn.presentation.ConnectionProfileContextProvider
import com.ganj.vpn.presentation.CoreBillingCheckoutCoordinator
import com.ganj.vpn.presentation.CurrentUserIdProvider
import com.ganj.vpn.presentation.GanjController
import com.ganj.vpn.presentation.GanjPresentationMapper
import com.ganj.vpn.presentation.GanjUiReducer
import com.ganj.vpn.presentation.GanjUiState
import com.ganj.vpn.presentation.GooglePlayCheckoutEffectExecutor
import com.ganj.vpn.presentation.StableIdGenerator
import java.io.Closeable
import java.net.URI
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class GanjComposition internal constructor(
    val controller: GanjController,
    val reducer: GanjUiReducer,
    private val playAdapter: GooglePlayBillingAdapter,
    private val playLifecycle: PlayBillingLifecycleBridge,
    private val purchaseEvents: PlayPurchaseEventRelay,
    private val checkoutEffects: GooglePlayCheckoutEffectExecutor,
    private val actionVault: CheckoutActionVault,
) : Closeable {
    @Volatile
    private var retainedUiState: GanjUiState = GanjUiState()

    fun restoreUiState(): GanjUiState = retainedUiState

    fun retainUiState(state: GanjUiState) {
        retainedUiState = state
    }

    suspend fun launchGooglePlayCheckout(
        activity: Activity,
        handle: CheckoutActionHandle,
    ): CheckoutEffectResult = checkoutEffects.execute(handle) { action ->
        playAdapter.launchCheckout(activity, action)
    }

    fun observePlayPurchases(observer: (PlayPurchaseEvent) -> Unit): Closeable =
        purchaseEvents.observe(observer)

    override fun close() {
        purchaseEvents.close()
        actionVault.clear()
        playLifecycle.close()
    }
}

class InMemorySessionTokenProvider : AuthTokenProvider {
    @Volatile
    private var token: AccessToken? = null

    override fun currentAccessToken(): AccessToken? = token

    fun update(rawToken: String?) {
        token = rawToken?.takeIf(String::isNotBlank)?.let { AccessToken.from(it) }
    }

    fun clear() {
        token = null
    }
}

class PlayPurchaseEventRelay : PlayPurchaseObserver, Closeable {
    private val closed = AtomicBoolean(false)
    private val lock = Any()
    private var registration: Registration? = null

    fun observe(observer: (PlayPurchaseEvent) -> Unit): Closeable {
        check(!closed.get()) { "Purchase event relay is closed" }
        val next = Registration(observer)
        synchronized(lock) { registration = next }
        return Closeable {
            synchronized(lock) {
                if (registration === next) registration = null
            }
        }
    }

    override fun onPurchaseEvent(event: PlayPurchaseEvent) {
        if (closed.get()) return
        val current = synchronized(lock) { registration }
        current?.observer?.invoke(event)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            synchronized(lock) { registration = null }
        }
    }

    private class Registration(val observer: (PlayPurchaseEvent) -> Unit)
}

object GanjCompositionFactory {
    fun create(
        application: Application,
        endpoint: String,
        tokenProvider: AuthTokenProvider,
        currentUser: CurrentUserIdProvider,
        connectionContext: ConnectionProfileContextProvider,
        ids: StableIdGenerator = StableIdGenerator { UUID.randomUUID().toString() },
    ): GanjComposition {
        val mapper = GanjPresentationMapper()
        val reducer = GanjUiReducer()
        val actionVault = OneTimeCheckoutActionVault()
        val purchaseEvents = PlayPurchaseEventRelay()
        val playAdapter = GooglePlayBillingAdapter.Factory(application).create(purchaseEvents)
        val playLifecycle = PlayBillingLifecycleBridge(application, playAdapter).also { it.start() }
        val billingGateways = GooglePlayOnlyBillingGatewayRegistry(playAdapter)
        val repository = if (endpoint.isValidControlApiEndpoint()) {
            ControlApiRepositoryFactory.create(endpoint, tokenProvider)
        } else {
            UnavailableControlApiRepository("control_api_endpoint_not_configured")
        }
        val billing = CoreBillingCheckoutCoordinator(
            gateways = billingGateways,
            currentUser = currentUser,
            ids = ids,
            mapper = mapper,
            actionVault = actionVault,
        )
        val checkoutSession = AuthenticatedCheckoutSession {
            tokenProvider.currentAccessToken() != null &&
                !currentUser.currentUserId().isNullOrBlank()
        }
        return GanjComposition(
            controller = GanjController(
                repository = repository,
                billing = billing,
                checkoutSession = checkoutSession,
                connectionContext = connectionContext,
                mapper = mapper,
                reducer = reducer,
                ids = ids,
            ),
            reducer = reducer,
            playAdapter = playAdapter,
            playLifecycle = playLifecycle,
            purchaseEvents = purchaseEvents,
            checkoutEffects = GooglePlayCheckoutEffectExecutor(actionVault, mapper),
            actionVault = actionVault,
        )
    }

    /** No user identifier or checkout operation is created until both session values are injected. */
    fun failClosed(application: Application, endpoint: String): GanjComposition = create(
        application = application,
        endpoint = endpoint,
        tokenProvider = InMemorySessionTokenProvider(),
        currentUser = CurrentUserIdProvider { null },
        connectionContext = ConnectionProfileContextProvider { null },
    )

    private fun String.isValidControlApiEndpoint(): Boolean = runCatching {
        val uri = URI(this)
        isNotBlank() && uri.scheme == "https" && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.query == null && uri.fragment == null
    }.getOrDefault(false)
}

private class GooglePlayOnlyBillingGatewayRegistry(
    private val play: GooglePlayBillingAdapter,
) : BillingGatewayRegistry {
    override fun gateway(provider: BillingProvider): BillingGateway? =
        play.takeIf { provider == BillingProvider.GOOGLE_PLAY }
}

private class UnavailableControlApiRepository(
    private val reason: String,
) : ControlApiRepository {
    override fun catalog(channel: PurchaseChannel): ApiResult<List<CatalogProduct>> = failure()
    override fun myServices(): ApiResult<List<UserService>> = failure()
    override fun checkout(command: CheckoutCommand): ApiResult<CheckoutOrder> = failure()
    override fun prepareConnection(command: ConnectionProfileCommand): ApiResult<ConnectionProfileLease> = failure()

    private fun failure(): ApiResult.Failure = ApiResult.Failure(ApiError.Protocol(null, reason))
}
