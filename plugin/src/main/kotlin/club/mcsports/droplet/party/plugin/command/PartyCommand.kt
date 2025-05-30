package club.mcsports.droplet.party.plugin.command

import app.simplecloud.plugin.api.shared.extension.text
import club.mcsports.droplet.party.api.PartyApi
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player

class PartyCommand(
    private val api: PartyApi.Coroutine
) : SimpleCommand {

    override fun execute(invocation: SimpleCommand.Invocation) {

        if(invocation.source() !is Player) {
            invocation.source().sendMessage(text("<red>You have to be a player to do this."))
            return
        }

        val args = invocation.arguments()
        val player = invocation.source() as Player

        /*
        Command-Todo:

        create
        info
        delete
        leave

        invite <player> (create party if doesnt exist etc)
        promote <player>
        demote <player>
        kick <player>
        accept <player>
        deny <player>
        */

    }

}