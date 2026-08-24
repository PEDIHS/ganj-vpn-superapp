package com.ganj.vpn.core.controlapi

import java.util.UUID

interface ControlApiRepository {
    fun catalog(channel: PurchaseChannel): ApiResult<List<CatalogProduct>>
    fun myServices(): ApiResult<List<UserService>>
    fun checkout(command: CheckoutCommand): ApiResult<CheckoutOrder>
    fun prepareConnection(command: ConnectionProfileCommand): ApiResult<ConnectionProfileLease>
}

object ControlApiRepositoryFactory {
    fun create(
        baseUrl: String,
        tokenProvider: AuthTokenProvider,
        authenticationEvents: AuthenticationEventSink = AuthenticationEventSink.NONE,
    ): ControlApiRepository {
        val client = ControlApiClient(
            transport = UrlConnectionTransport(baseUrl),
            tokenProvider = tokenProvider,
            authenticationEvents = authenticationEvents,
        )
        return DefaultControlApiRepository(client, InMemoryConnectionEnvelopeVault())
    }
}

internal class DefaultControlApiRepository(
    private val client: ControlApiClient,
    private val envelopeVault: ConnectionEnvelopeVault,
) : ControlApiRepository {
    override fun catalog(channel: PurchaseChannel): ApiResult<List<CatalogProduct>> = client.getCatalog(channel)

    override fun myServices(): ApiResult<List<UserService>> = client.getMyServices()

    override fun checkout(command: CheckoutCommand): ApiResult<CheckoutOrder> = client.createCheckout(command)

    override fun prepareConnection(command: ConnectionProfileCommand): ApiResult<ConnectionProfileLease> {
        return when (val result = client.issueConnectionProfile(command)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                val envelope = result.value
                if (envelope.serverId != command.serverId) {
                    envelope.destroy()
                    ApiResult.Failure(
                        ApiError.Protocol(
                            requestId = result.metadata.requestId,
                            reason = "Issued profile server does not match the requested entitlement server",
                        ),
                    )
                } else {
                    val handle = envelopeVault.put(envelope)
                    ApiResult.Success(
                        value = ConnectionProfileLease(
                            profileId = envelope.profileId,
                            serverId = envelope.serverId,
                            expiresAt = envelope.expiresAt,
                            vaultHandle = handle,
                        ),
                        metadata = result.metadata,
                    )
                }
            }
        }
    }
}

internal interface ConnectionEnvelopeVault {
    fun put(envelope: EncryptedConnectionEnvelope): String
    fun take(handle: String): EncryptedConnectionEnvelope?
    fun clear()
}

internal class InMemoryConnectionEnvelopeVault(
    private val maximumEntries: Int = 4,
) : ConnectionEnvelopeVault {
    private val values = linkedMapOf<String, EncryptedConnectionEnvelope>()

    init {
        require(maximumEntries in 1..32)
    }

    @Synchronized
    override fun put(envelope: EncryptedConnectionEnvelope): String {
        while (values.size >= maximumEntries) {
            val oldest = values.entries.first()
            values.remove(oldest.key)?.destroy()
        }
        val handle = UUID.randomUUID().toString()
        values[handle] = envelope
        return handle
    }

    @Synchronized
    override fun take(handle: String): EncryptedConnectionEnvelope? = values.remove(handle)

    @Synchronized
    override fun clear() {
        values.values.forEach(EncryptedConnectionEnvelope::destroy)
        values.clear()
    }
}
