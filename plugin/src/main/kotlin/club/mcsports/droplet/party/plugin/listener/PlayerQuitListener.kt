package club.mcsports.droplet.party.plugin.listener

import app.simplecloud.droplet.player.api.PlayerApi
import club.mcsports.droplet.party.api.PartyApi
import io.grpc.StatusException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.logging.Logger

class PlayerQuitListener(
    private val partyApi: PartyApi.Coroutine,
    private val playerApi: PlayerApi.Coroutine,
    private val logger: Logger
) : Listener {

    @EventHandler
    fun handlePlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId

        CoroutineScope(Dispatchers.IO).launch {
            delay(500) // Delay to ensure the player is fully disconnected

            if(playerApi.isOnline(uuid)) {
                // Player is still connected, do not leave party
                // Happens if for example the player is online in 2 minecraft instances, already online and tries to connect
                // to the network but gets disconnected instantly because he obv is already here
                return@launch
            }

            try {
                partyApi.getInteraction().memberLeaveParty(event.player.uniqueId)
            } catch (exception: StatusException) {
                logger.warning(exception.status.description)
            }
        }
    }
}