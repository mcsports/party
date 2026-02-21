package club.mcsports.droplet.party.plugin

import app.simplecloud.droplet.player.api.PlayerApi
import club.mcsports.droplet.party.api.PartyApi
import club.mcsports.droplet.party.plugin.command.PartyCommand
import club.mcsports.droplet.party.plugin.listener.PlayerQuitListener
import org.bukkit.plugin.java.JavaPlugin

class PartyPaperPlugin : JavaPlugin() {

    lateinit var partyApi: PartyApi.Coroutine
    lateinit var playerApi: PlayerApi.Coroutine

    override fun onEnable() {
        logger.info("Initializing mcsports-party...")
        partyApi = PartyApi.createCoroutineApi()
        playerApi = PlayerApi.createCoroutineApi()

        server.pluginManager.registerEvents(PlayerQuitListener(partyApi, playerApi, logger), this)
        server.getPluginCommand("party")?.setExecutor(PartyCommand(partyApi, logger))
        logger.info("Initializing mcsports-party... done!")
    }

}