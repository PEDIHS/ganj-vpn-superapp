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
import com.ganj.vpn.core.controlapi.ConnectionProfileBroker
import com.ganj.vpn.core.controlapi.ConnectionProfileLease
import com.ganj.vpn.core.controlapi.ControlApiComponents
import com.ganj.vpn.core.controlapi.ControlApiRepository
import com.ganj.vpn.core.controlapi.ControlApiRepositoryFactory
import com.ganj.vpn.core.controlapi.Gvp1CryptoProvider
import com.ganj.vpn.core.controlapi.ManagedServer
import com.ganj.vpn.core.controlapi.ProfileProvisioningBinding
import com.ganj.vpn.core.controlapi.ProfileProvisioningError
import com.ganj.vpn.core.controlapi.ProfileProvisioningResult
import com.ganj.vpn.core.controlapi.PurchaseChannel
import com.ganj.vpn.core.controlapi.SubscriptionTier
import com.ganj.vpn.core.controlapi.UnavailableGvp1CryptoProvider
import com.ganj.vpn.core.controlapi.UserService
import com.ganj.vpn.core.controlapi.VpnProtocol
import com.ganj.vpn.core.playbilling.GooglePlayBillingAdapter
import com.ganj.vpn.core.playbilling.PlayBillingLifecycleBridge
import com.ganj.vpn.core.playbilling.PlayPurchaseEvent
import com.ganj.vpn.core.playbilling.PlayPurchaseObserver
import com.ganj.vpn.enterprise.EnterpriseController
import com.ganj.vpn.enterprise.EnterpriseDeviceContextProvider
import com.ganj.vpn.enterprise.EnterpriseExperienceRepository
import com.ganj.vpn.enterprise.EnterpriseReducer
import com.ganj.vpn.enterprise.EnterpriseSessionGate
import com.ganj.vpn.enterprise.EnterpriseUiState
import com.ganj.vpn.enterprise.FailClosedEnterpriseRepository
import com.ganj.vpn.enterprise.PrivacySafeDiagnosticCollector
import com.ganj.vpn.presentation.AuthenticatedCheckoutSession
import com.ganj.vpn.presentation.CheckoutActionHandle
import com.ganj.vpn.presentation.CheckoutActionVault
import com.ganj.vpn.presentation.CheckoutEffectResult
import com.ganj.vpn.presentation.ConnectionActionHandle
import com.ganj.vpn.presentation.ConnectionActionVault
import com.ganj.vpn.presentation.ConnectionEffectExecutor
import com.ganj.vpn.presentation.ConnectionEffectResult
import com.ganj.vpn.presentation.ConnectionProfileContextProvider
import com.ganj.vpn.presentation.CoreBillingCheckoutCoordinator
import com.ganj.vpn.presentation.CurrentUserIdProvider
import com.ganj.vpn.presentation.GanjController
import com.ganj.vpn.presentation.GanjPresentationMapper
import com.ganj.vpn.presentation.GanjUiReducer
import com.ganj.vpn.presentation.GanjUiState
import com.ganj.vpn.presentation.GooglePlayCheckoutEffectExecutor
import com.ganj.vpn.presentation.InMemoryConnectionActionVault
import com.ganj.vpn.presentation.StableIdGenerator
import com.ganj.vpn.presentation.TunnelConnector
import com.ganj.vpn.vpn.AndroidVpnTunnelClient
import java.io.Closeable
import java.net.URI
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class GanjComposition internal constructor(
    val controller: GanjController,
    val reducer: GanjUiReducer,
    val enterpriseController: EnterpriseController,
    val enterpriseReducer: EnterpriseReducer,
    private val telegramAuth: TelegramBotAuthCoordinator?,
    private val playAdapter: GooglePlayBillingAdapter,
    private val playLifecycle: PlayBillingLifecycleBridge,
    private val purchaseEvents: PlayPurchaseEventRelay,
    private val checkoutEffects: GooglePlayCheckoutEffectExecutor,
    private val actionVault: CheckoutActionVault,
    private val connectionEffects: ConnectionEffectExecutor,
    private val connectionActions: ConnectionActionVault,
) : Closeable {
    @Volatile
    private var retainedUiState: GanjUiState = GanjUiState()

    @Volatile
    private var retainedEnterpriseState: EnterpriseUiState = EnterpriseUiState()

    fun restoreUiState(): GanjUiState = retainedUiState

    fun retainUiState(state: GanjUiState) {
        retainedUiState = state
    }

    fun restoreEnterpriseState(): EnterpriseUiState = retainedEnterpriseState

    fun retainEnterpriseState(state: EnterpriseUiState) {
        retainedEnterpriseState = state
    }

    fun isTelegramLinked(): Boolean = telegramAuth?.isLinked() == true

    fun hasPendingTelegramLogin(): Boolean = telegramAuth?.hasPendingFlow() == true

    fun beginTelegramLogin(): TelegramBotAuthResult =
        telegramAuth?.begin() ?: TelegramBotAuthResult.Failed("auth.unavailable")

    fun resumeTelegramLogin(): TelegramBotAuthResult =
        telegramAuth?.resume() ?: TelegramBotAuthResult.Failed("auth.unavailable")

    fun logoutTelegram(): TelegramBotAuthResult =
        telegramAuth?.logout() ?: TelegramBotAuthResult.LoggedOut

    suspend fun launchGooglePlayCheckout(
        activity: Activity,
        handle: CheckoutActionHandle,
    ): CheckoutEffectResult = checkoutEffects.execute(handle) { action ->
        playAdapter.launchCheckout(activity, action)
    }

    fun observePlayPurchases(observer: (PlayPurchaseEvent) -> Unit): Closeable =
        purchaseEvents.observe(observer)

    suspend fun launchVpnConnection(handle: ConnectionActionHandle): ConnectionEffectResult =
        connectionEffects.execute(handle)

    suspend fun disconnectVpn(): Result<Unit> = connectionEffects.disconnect()

    override fun close() {
        purchaseEvents.close()
        actionVault.clear()
        connectionActions.clear()
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
        telegramAuth: TelegramBotAuthCoordinator? = null,
        enterpriseRepository: EnterpriseExperienceRepository = FailClosedEnterpriseRepository(),
        enterpriseDeviceContext: EnterpriseDeviceContextProvider = EnterpriseDeviceContextProvider { null },
        diagnosticCollector: PrivacySafeDiagnosticCollector = PrivacySafeDiagnosticCollector { emptyList() },
        cryptoProvider: Gvp1CryptoProvider = UnavailableGvp1CryptoProvider,
        tunnelConnector: TunnelConnector = AndroidVpnTunnelClient(application),
        ids: StableIdGenerator = StableIdGenerator { UUID.randomUUID().toString() },
    ): GanjComposition {
        val mapper = GanjPresentationMapper()
        val reducer = GanjUiReducer()
        val enterpriseReducer = EnterpriseReducer()
        val actionVault = OneTimeCheckoutActionVault()
        val connectionActions = InMemoryConnectionActionVault()
        val purchaseEvents = PlayPurchaseEventRelay()
        val playAdapter = GooglePlayBillingAdapter.Factory(application).create(purchaseEvents)
        val playLifecycle = PlayBillingLifecycleBridge(application, playAdapter).also { it.start() }
        val billingGateways = GooglePlayOnlyBillingGatewayRegistry(playAdapter)
        val api = if (endpoint.isValidControlApiEndpoint()) {
            ControlApiRepositoryFactory.createComponents(
                baseUrl = endpoint,
                tokenProvider = tokenProvider,
                cryptoProvider = cryptoProvider,
            )
        } else {
            ControlApiComponents(
                repository = UnavailableControlApiRepository("control_api_endpoint_not_configured"),
                profileBroker = UnavailableConnectionProfileBroker,
            )
        }
        val repository = api.repository
        val billing = CoreBillingCheckoutCoordinator(
            gateways = billingGateways,
            currentUser = currentUser,
            ids = ids,
            mapper = mapper,
            actionVault = actionVault,
        )
        // Guest sessions are sufficient for Free browsing/connect but deliberately not for paid
        // ownership. The linked marker is UX-only; the backend still verifies the resulting linked
        // session and commercial ownership for protected operations.
        val checkoutSession = AuthenticatedCheckoutSession {
            telegramAuth?.isLinked() == true &&
                tokenProvider.currentAccessToken() != null &&
                !currentUser.currentUserId().isNullOrBlank()
        }
        val enterpriseSession = EnterpriseSessionGate {
            tokenProvider.currentAccessToken() != null &&
                !currentUser.currentUserId().isNullOrBlank()
        }
        return GanjComposition(
            controller = GanjController(
                repository = repository,
                billing = billing,
                checkoutSession = checkoutSession,
                currentUser = currentUser,
                connectionContext = connectionContext,
                connectionActions = connectionActions,
                mapper = mapper,
                reducer = reducer,
                ids = ids,
            ),
            reducer = reducer,
            enterpriseController = EnterpriseController(
                repository = enterpriseRepository,
                session = enterpriseSession,
                deviceContext = enterpriseDeviceContext,
                diagnostics = diagnosticCollector,
                ids = ids,
                nowEpochMillis = System::currentTimeMillis,
                reducer = enterpriseReducer,
            ),
            enterpriseReducer = enterpriseReducer,
            telegramAuth = telegramAuth,
            playAdapter = playAdapter,
            playLifecycle = playLifecycle,
            purchaseEvents = purchaseEvents,
            checkoutEffects = GooglePlayCheckoutEffectExecutor(actionVault, mapper),
            actionVault = actionVault,
            connectionEffects = ConnectionEffectExecutor(connectionActions, api.profileBroker, tunnelConnector),
            connectionActions = connectionActions,
        )
    }

    /** No user identifier or checkout operation is created until both session values are injected. */
    fun failClosed(
        application: Application,
        endpoint: String,
        cryptoProvider: Gvp1CryptoProvider = UnavailableGvp1CryptoProvider,
    ): GanjComposition = create(
        application = application,
        endpoint = endpoint,
        tokenProvider = InMemorySessionTokenProvider(),
        currentUser = CurrentUserIdProvider { null },
        connectionContext = ConnectionProfileContextProvider { null },
        telegramAuth = null,
        cryptoProvider = cryptoProvider,
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
    override fun servers(
        tier: SubscriptionTier?,
        countryCode: String?,
        protocol: VpnProtocol?,
    ): ApiResult<List<ManagedServer>> = failure()
    override fun checkout(command: CheckoutCommand): ApiResult<CheckoutOrder> = failure()
    override fun prepareConnection(command: ConnectionProfileCommand): ApiResult<ConnectionProfileLease> = failure()

    private fun failure(): ApiResult.Failure = ApiResult.Failure(ApiError.Protocol(null, reason))
}

private object UnavailableConnectionProfileBroker : ConnectionProfileBroker {
    override fun provision(
        lease: ConnectionProfileLease,
        binding: ProfileProvisioningBinding,
    ): ProfileProvisioningResult =
        ProfileProvisioningResult.Failure(ProfileProvisioningError.CRYPTO_UNAVAILABLE)
}
