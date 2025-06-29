package club.mcsports.droplet.party.plugin.listener

import club.mcsports.droplet.party.api.PartyApi
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.proxy.ProxyServer
import io.grpc.StatusException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger

class PlayerDisconnectListener(
    private val api: PartyApi.Coroutine,
    private val logger: Logger,
    private val proxyServer: ProxyServer
) {

    @Subscribe
    fun handlePlayerDisconnect(event: DisconnectEvent) {
        val uuid = event.player.uniqueId

        CoroutineScope(Dispatchers.IO).launch {
            delay(500) // Delay to ensure the player is fully disconnected

            if(proxyServer.getPlayer(uuid).isPresent) {
                // Player is still connected, do not leave party
                // Happens if for example the player is online in 2 minecraft instances, already online and tries to connect
                // to the network but gets disconnected instantly because he obv is already here
                return@launch
            }

            try {
                api.getInteraction().memberLeaveParty(event.player.uniqueId)
            } catch (exception: StatusException) {
                logger.warn(exception.status.description)
            }
        }
    }
}