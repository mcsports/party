package club.mcsports.droplet.party.api.impl.coroutine

import app.simplecloud.droplet.api.auth.AuthCallCredentials
import club.mcsports.droplet.party.api.DataApi
import club.mcsports.droplet.party.api.InteractionApi
import club.mcsports.droplet.party.api.PartyApi
import io.grpc.ManagedChannelBuilder

class PartyApiCoroutineImpl(
    authSecret: String,
    host: String,
    port: Int,
) : PartyApi.Coroutine {
    private val channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build()
    private val credentials = AuthCallCredentials(authSecret)
    private val dataApi = PartyDataApiCoroutineImpl(credentials, channel)
    private val interactionApi = PartyInteractionApiCoroutineImpl(credentials, channel)

    override fun getData(): DataApi.Coroutine = dataApi
    override fun getInteraction(): InteractionApi.Coroutine = interactionApi
}