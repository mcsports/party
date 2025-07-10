package club.mcsports.droplet.party.extension

import app.simplecloud.droplet.player.api.CloudPlayer
import club.mcsports.droplet.party.PartyRuntime
import io.grpc.Status
import io.grpc.StatusException
import java.util.UUID

fun String.asUuid() = UUID.fromString(this)

private val playerApi = PartyRuntime.playerApiSingleton

suspend fun String.fetchPlayer(): CloudPlayer? {
    try {
        val uuid = UUID.fromString(this)
        return playerApi.getOnlinePlayer(uuid)
    } catch (_: IllegalArgumentException) {

        try {
            return playerApi.getOnlinePlayer(this)
        } catch (exception: StatusException) {
            if (exception.status.code == Status.Code.NOT_FOUND) return null
            throw exception
        }

    } catch (exception: StatusException) {
        if (exception.status.code == Status.Code.NOT_FOUND) return null
        throw exception
    }
}

suspend fun UUID.fetchPlayer(): CloudPlayer? {
    try {
        return playerApi.getOnlinePlayer(this)
    } catch (exception: StatusException) {
        if (exception.status.code == Status.Code.NOT_FOUND) return null
        throw exception
    }
}