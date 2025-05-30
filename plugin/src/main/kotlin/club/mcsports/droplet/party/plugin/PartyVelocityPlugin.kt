package club.mcsports.droplet.party.plugin

import club.mcsports.droplet.party.api.PartyApi
import club.mcsports.droplet.party.plugin.command.PartyCommand
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.Logger

@Plugin(
    id = "mcsports-party",
    name = "Party",
    version = "1.0.0",
    authors = ["ishde"],
    dependencies = [Dependency(id = "simplecloud-api")]
)
class PartyVelocityPlugin() {

    lateinit var server: ProxyServer
    lateinit var logger: Logger

    lateinit var api: PartyApi.Coroutine

    @Inject
    constructor(server: ProxyServer, logger: Logger) : this() {
        this.server = server
        this.logger = logger
    }

    @Subscribe
    fun onInit(event: ProxyInitializeEvent) {
        api = PartyApi.createCoroutineApi()
        server.commandManager.register(
            server.commandManager.metaBuilder("queue").plugin(this).build(),
            PartyCommand(api)
        )
        logger.info("Initializing mcsports-queue")
    }

    @Subscribe
    fun onQuit(event: DisconnectEvent) {
        CoroutineScope(Dispatchers.IO).launch {

        }
    }

}