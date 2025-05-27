package club.mcsports.droplet.party.extension

import app.simplecloud.droplet.player.api.CloudPlayer
import app.simplecloud.droplet.player.api.PlayerApiSingleton
import java.util.UUID

fun String.asUuid() = UUID.fromString(this)

private val playerApi = PlayerApiSingleton.instance.getCoroutineApi()
suspend fun String.fetchPlayer(): CloudPlayer {
    return try {
        playerApi.getOnlinePlayer(UUID.fromString(this))
    } catch(_: IllegalArgumentException) {
        playerApi.getOnlinePlayer(this)
    }
}