package club.mcsports.droplet.party.repository

import app.simplecloud.droplet.api.time.ProtobufTimestamp
import app.simplecloud.droplet.player.api.CloudPlayer
import club.mcsports.droplet.party.extension.asUuid
import club.mcsports.droplet.party.extension.fetchPlayer
import club.mcsports.droplet.party.extension.log
import club.mcsports.droplet.party.invitation.InviteResult
import club.mcsports.droplet.party.shared.extension.getMember
import com.mcsports.party.v1.Party
import com.mcsports.party.v1.PartyInvite
import com.mcsports.party.v1.PartyRole
import com.mcsports.party.v1.copy
import com.mcsports.party.v1.partyInvite
import io.grpc.Status
import org.apache.logging.log4j.LogManager
import java.time.LocalDateTime

class InviteRepository(
    private val playerRepository: PlayerRepository,
    private val partyRepository: PartyRepository
) {
    private val logger = LogManager.getLogger(InviteRepository::class.java)

    /**
     * Invites players to a party
     *
     * @param names names of the players to invite
     * @param invitorName the name of the player who invites our players
     *
     * @return a map of player names with their invite result paired with the party
     *
     * @throws Status.NOT_FOUND if the player isn't part of any party
     * @throws Status.PERMISSION_DENIED if the invitor doesn't have enough permissions to invite other players
     * @throws Status.UNAVAILABLE if the party has invites toggled off
     */
    suspend fun invitePlayers(names: Set<String>, invitorName: String): Pair<Map<String, InviteResult>, Party> {
        val party = playerRepository.getParty(invitorName)
            ?: throw Status.NOT_FOUND.withDescription("Failed to invite players: User $invitorName isn't part of any party")
                .log(logger).asRuntimeException()

        val invitorPartyMember = party.getMember(invitorName)
            ?: throw Status.NOT_FOUND.withDescription("Failed to invite players: User $invitorName isn't part of any party")
                .log(logger).asRuntimeException()

        if (invitorPartyMember.role == PartyRole.MEMBER) throw Status.PERMISSION_DENIED.withDescription("Failed to invite $names: User $invitorName's role is too low")
            .log(logger).asRuntimeException()

        if (!party.settings.allowInvites && invitorPartyMember.role != PartyRole.OWNER) throw Status.UNAVAILABLE.withDescription(
            "Failed to invite players: Party ${party.id} has toggled off invites"
        ).log(logger).asRuntimeException()

        val lowercaseNames = names.map { name -> name.lowercase() }
        val lowercaseInvites = party.invitesList.map { it.invitedName }

        val inviteResults = lowercaseNames.associate { invitedName ->
            if (invitedName.equals(invitorName, true)) {
                val status =
                    Status.INVALID_ARGUMENT.withDescription("Failed to invite $invitedName: User cannot invite themselves").log(logger)
                return@associate invitedName to InviteResult(status, null, null)
            }

            if (invitedName.lowercase() !in lowercaseInvites) {
                val player = invitedName.fetchPlayer() ?: run {
                    val status =
                        Status.NOT_FOUND.withDescription("Failed to invite $invitedName: Failed to fetch player. Probably offline.")
                            .log(logger)

                    val inviteResult = InviteResult(status, null, null)
                    return@associate invitedName to inviteResult
                }

                val inviteResult = InviteResult(Status.OK, player, partyInvite {
                    this.invitedName = invitedName
                    this.invitorName = invitorName
                    this.timeCreated = ProtobufTimestamp.fromLocalDateTime(LocalDateTime.now())
                })

                return@associate invitedName to inviteResult
            }

            val status =
                Status.ALREADY_EXISTS.withDescription("Failed to invite $invitedName: User already has a pending invite for party ${party.id}")
                    .log(logger)

            return@associate invitedName to InviteResult(
                status,
                null,
                null
            )
        }

        party.copy {
            val tempInvites = this.invites.toMutableList()

            tempInvites.addAll(inviteResults.mapNotNull { (name, inviteResult) ->
                return@mapNotNull inviteResult.invite
            })

            this.invites.clear()
            this.invites.addAll(tempInvites)
        }.also { updatedParty ->
            partyRepository.updateParty(updatedParty)
            return inviteResults to updatedParty
        }
    }

    /**
     * Deletes invitations
     *
     * @param name the name of the player that got invited
     * @param party the party the player got invited to
     *
     * @return the party invite that got deleted
     *
     * @throws Status.NOT_FOUND if the player has no pending invite for the party
     */
    fun deleteInvite(name: String, party: Party): PartyInvite {
        val invite = party.invitesList.firstOrNull { invite -> invite.invitedName.equals(name, true) }
            ?: throw Status.NOT_FOUND.withDescription("Failed to delete invite: User $name has no pending invite for party ${party.id}")
                .log(logger).asRuntimeException()

        val player = playerRepository.getPlayer(name)

        party.copy {
            val tempInvites = this.invites.toMutableList()
            tempInvites.remove(invite)

            this.invites.clear()
            this.invites.addAll(tempInvites)
        }.also { updatedParty ->
            partyRepository.updateParty(updatedParty)
            player.invites.remove(invite.invitorName)
        }

        return invite
    }

}