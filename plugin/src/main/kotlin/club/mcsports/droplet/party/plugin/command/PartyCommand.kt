package club.mcsports.droplet.party.plugin.command

import app.simplecloud.plugin.api.shared.extension.text
import club.mcsports.droplet.party.api.PartyApi
import com.mcsports.party.v1.PartyRole
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component

class PartyCommand(
    private val api: PartyApi.Coroutine
) : SimpleCommand {

    private val help = mutableMapOf(
        "create" to "Creates a new party",
        "info" to "Shows information about your party",
        "delete" to "Deletes your party",
        "leave" to "Leave your party",
        "invite <player>" to "Invite a player to your party",
        "promote <player>" to "Promotes a player in your party",
        "demote <player>" to "Demotes a player in your party",
        "kick <player>" to "Kicks a player from your party",
        "accept <player>" to "Accepts a party invite from a player",
        "deny <player>" to "Denies a party invite from a player"
    )

    override fun execute(invocation: SimpleCommand.Invocation) {

        if(invocation.source() !is Player) {
            invocation.source().sendMessage(text("<red>You have to be a player to do this."))
            return
        }

        val args = invocation.arguments()
        val player = invocation.source() as Player
        val alias = invocation.alias()

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

        if(args.isEmpty()) {
            player.sendHelp(alias)
            return
        }

        if(args.size == 1) {
            CoroutineScope(Dispatchers.IO).launch {
                when (args[0].lowercase()) {
                    "create" -> {
                        //TODO: Create party
                    }
                    "info" -> {
                        try {
                            val party = api.getData().getParty(player.uniqueId)
                            player.sendMessage(text("<gray>Party Info:"))
                            player.sendMessage(text("<yellow>Owner: <gray>${party.membersList.first { it.role == PartyRole.OWNER }.name}"))
                            player.sendMessage(text("<yellow>Members: <gray>${party.membersList.joinToString(", ") { it.name }}"))

                            player.sendMessage(Component.empty())

                            val settings = party.settings
                            player.sendMessage(text("<yellow>Settings:"))
                            player.sendMessage(text("<yellow>Public: <gray>${if(settings.isPrivate) "No" else "Yes"}"))
                            player.sendMessage(text("<yellow>Invite Only: <gray>${if(settings.allowInvites) "No" else "Yes"}"))
                        } catch (exception: StatusRuntimeException) {
                            if(exception.status == Status.NOT_FOUND) player.sendMessage(text("<red>You are not part of any party."))
                            else player.sendMessage(text("<red>Failed to fetch your party member data. Please call an administrator about this."))

                        }

                    }
                    "delete" -> {
                        api.getInteraction().deleteParty(player.uniqueId)
                    }
                    "leave" -> {
                        api.getInteraction().memberLeaveParty(player.uniqueId)
                    }
                }
            }

            return
        }

    }

    private fun Player.sendHelp(alias: String) {

        this.sendMessage(text("<gray>Party Commands:"))
        help.forEach { (command, description) ->
            this.sendMessage(text("<yellow>/$alias $command <dark_gray>-> <gray>$description"))
        }

    }

}