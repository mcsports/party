package club.mcsports.droplet.party.plugin.command

import app.simplecloud.plugin.api.shared.extension.text
import club.mcsports.droplet.party.api.PartyApi
import club.mcsports.droplet.party.shared.Color
import club.mcsports.droplet.party.shared.Glyphs
import com.mcsports.party.v1.PartyRole
import com.mcsports.party.v1.partySettings
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import org.slf4j.Logger

class PartyCommand(
    private val api: PartyApi.Coroutine,
    private val logger: Logger
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
        "deny <player>" to "Denies a party invite from a player",
        "chat <message>" to "Sends a message to your party chat"
    )

    override fun execute(invocation: SimpleCommand.Invocation) {

        if (invocation.source() !is Player) {
            invocation.source().sendMessage(text("${Color.RED}You have to be a player to do this."))
            return
        }

        val args = invocation.arguments()
        val player = invocation.source() as Player
        val alias = invocation.alias()

        if(args.size >= 2 && args[0].lowercase() == "chat") {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    api.getInteraction().partyChat(player.uniqueId, text(args.drop(1).joinToString(" ")))
                } catch (exception: StatusException) {
                    logger.warn(exception.status.description)
                }
            }
            return
        }

        if (args.isEmpty()) {
            player.sendHelp(alias)
            return
        }

        if (args.size == 1) {
            CoroutineScope(Dispatchers.IO).launch {
                when (args[0].lowercase()) {
                    "create" -> {
                        try {
                            api.getInteraction().createParty(player.uniqueId, partySettings {
                                this.isPrivate = true
                                this.allowInvites = true
                                this.allowChatting = true
                            }, emptyList())
                        } catch (exception: StatusException) {
                            logger.warn(exception.status.description)
                        }
                    }

                    "info" -> {
                        try {
                            val party = api.getData().getParty(player.uniqueId)
                            player.sendMessage(text("${Glyphs.BALLOONS} Your party"))
                            player.sendMessage(text("${Glyphs.SPACE} <gray>Owner: <color:#38bdf8>${party.membersList.first { it.role == PartyRole.OWNER }.name}"))
                            player.sendMessage(text("${Glyphs.SPACE} <gray>Members: <color:#38bdf8>${party.membersList.joinToString(", ") { it.name }}"))

                            player.sendMessage(Component.empty())

                            val settings = party.settings
                            player.sendMessage(text("${Glyphs.SPACE}<white> Settings"))
                            player.sendMessage(text("${Glyphs.SPACE} <gray>Public: <color:#38bdf8>${if (settings.isPrivate) "no" else "yes"}"))
                            player.sendMessage(text("${Glyphs.SPACE} <gray>Invites: <color:#38bdf8>${if (settings.allowInvites) "enabled" else "disabled"}"))
                            player.sendMessage(text("${Glyphs.SPACE} <gray>Chat: <color:#38bdf8>${if (settings.allowChatting) "enabled" else "disabled"}"))
                        } catch (exception: StatusException) {
                            logger.warn(exception.status.description)

                            if (exception.status.code == Status.Code.NOT_FOUND) player.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You are not part of any party."))
                            else player.sendMessage(text("${Glyphs.BALLOONS + Color.RED} Failed to fetch your party member data. Please call an administrator about this."))
                        }

                    }

                    "delete" -> {
                        try {
                            api.getInteraction().deleteParty(player.uniqueId)
                        } catch(exception: StatusException) {
                            logger.warn(exception.status.description)
                        }
                    }

                    "leave" -> {
                        api.getInteraction().memberLeaveParty(player.uniqueId)
                    }

                    else -> {
                        player.sendHelp(alias)
                    }
                }
            }

            return
        }

        if (args.size == 2) {
            val targetName = args[1]

            CoroutineScope(Dispatchers.IO).launch {
                when (args[0].lowercase()) {
                    "invite", "add" -> {
                        try {
                            api.getInteraction().inviteMember(targetName, player.uniqueId)
                        } catch (exception: StatusException) {
                            logger.warn(exception.status.description)
                        }
                    }

                    "promote" -> {
                        try {
                            api.getInteraction().promoteMember(targetName, player.uniqueId)
                        } catch (exception: StatusException) {
                            logger.warn(exception.status.description)
                        }
                    }

                    "demote" -> {
                        try {
                            api.getInteraction().demoteMember(targetName, player.uniqueId)
                        } catch (exception: StatusException) {
                            logger.warn(exception.status.description)
                        }
                    }

                    "kick", "remove" -> {
                        try {
                            api.getInteraction().kickMember(targetName, player.uniqueId)
                        } catch (exception: StatusException) {
                            logger.warn(exception.status.description)
                        }
                    }

                    "accept" -> {
                        try {
                            api.getInteraction().acceptPartyInvite(targetName, player.uniqueId)
                        } catch (exception: StatusException) {
                            logger.warn(exception.status.description)
                        }
                    }

                    "deny" -> {
                        try {
                            api.getInteraction().denyPartyInvite(targetName, player.uniqueId)
                        } catch (exception: StatusException) {
                            logger.warn(exception.status.description)
                        }
                    }

                    else -> {
                        player.sendHelp(alias)
                    }
                }
            }

            return
        }

    }

    private fun Player.sendHelp(alias: String) {
        this.sendMessage(text("${Glyphs.BALLOONS} Commands of Party"))
        help.forEach { (command, description) ->
            this.sendMessage(text("${Glyphs.SPACE} <gray>/$alias $command"))
        }

    }

}