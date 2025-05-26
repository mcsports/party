package club.mcsports.droplet.party.api.impl.future

import app.simplecloud.droplet.api.auth.AuthCallCredentials
import club.mcsports.droplet.party.api.DataApi
import club.mcsports.droplet.party.api.InteractionApi
import club.mcsports.droplet.party.api.PartyApi
import io.grpc.ManagedChannelBuilder

class PartyApiFutureImpl(
    authSecret: String,
    host: String,
    port: Int,
) : PartyApi.Future {
    private val channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build()
    private val credentials = AuthCallCredentials(authSecret)
    private val dataApi = PartyDataApiFutureImpl(credentials, channel)
    private val interactionApi = PartyInteractionApiFutureImpl(credentials, channel)

    override fun getData(): DataApi.Future = dataApi
    override fun getInteraction(): InteractionApi.Future = interactionApi

}