package club.mcsports.droplet.party.plugin.listener

import club.mcsports.droplet.party.api.PartyApi
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import io.grpc.StatusException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.Logger

class PlayerDisconnectListener(
    private val api: PartyApi.Coroutine,
    private val logger: Logger
) {

    @Subscribe
    fun handlePlayerDisconnect(event: DisconnectEvent) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                api.getInteraction().memberLeaveParty(event.player.uniqueId)
            } catch (exception: StatusException) {
                logger.warn(exception.status.description)
            }
        }
    }
}