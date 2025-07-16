package club.mcsports.droplet.party.repository

import app.simplecloud.droplet.api.time.ProtobufTimestamp
import club.mcsports.droplet.party.extension.asUuid
import club.mcsports.droplet.party.extension.log
import club.mcsports.droplet.party.player.PartyPlayer
import com.mcsports.party.v1.Party
import com.mcsports.party.v1.PartyMember
import com.mcsports.party.v1.PartyRole
import com.mcsports.party.v1.copy
import com.mcsports.party.v1.partyMember
import io.grpc.Status
import org.apache.logging.log4j.LogManager
import java.time.LocalDateTime
import java.util.UUID

class PlayerRepository(
    private val partyRepository: PartyRepository
) {
    private val logger = LogManager.getLogger(PlayerRepository::class.java)
    val players = mutableMapOf<String, PartyPlayer>()

    /**
     * Gets a player
     * @param name
     * @return the party player object associated with the name
     */
    fun getPlayer(name: String): PartyPlayer =
        players[name] ?: PartyPlayer(null, mutableMapOf()).also { player -> players[name] = player }

    /**
     * Gets the party the player is part of
     * @param name the name of the player
     * @return the party object, null if the player isn't part of any party
     */
    fun getParty(name: String): Party? {
        val partyId = getPlayer(name).partyId
        return partyRepository.parties[partyId]
    }

    /**
     * Assigns a player to a party
     * @param name the name of the player
     * @param uuid the uuid of the player
     * @param role the role the player gets
     * @param party the party the player gets assigned to
     */
    fun assignParty(name: String, uuid: UUID, role: PartyRole = PartyRole.MEMBER, party: Party) {
        val player = getPlayer(name)

        party.copy {
            val tempMembers = this.members.toMutableList()

            val partyMember = partyMember {
                this.name = name
                this.uuid = uuid.toString()

                this.role = role
                this.timeJoined = ProtobufTimestamp.fromLocalDateTime(LocalDateTime.now())
            }

            tempMembers.add(partyMember)

            this.members.clear()
            this.members.addAll(tempMembers)
        }.also { updatedParty ->
            partyRepository.updateParty(updatedParty)
            player.partyId = updatedParty.id.asUuid()
        }
    }

    /**
     * Removes a player from a player
     * @param name the name of the player to remove
     * @return the updated party object
     * @throws Status.NOT_FOUND if the player isn't part of any party
     */
    fun removeParty(name: String): Pair<Party, PartyMember> {
        val player = getPlayer(name)
        val party = getParty(name)
            ?: throw Status.NOT_FOUND.withDescription("Failed to get party: $name isn't part of any party")
                .log(logger).asRuntimeException()

        val memberToRemove = party.membersList.find { it.name == name }
            ?: throw Status.NOT_FOUND.withDescription("Failed to get party: $name isn't part of any party")
                .log(logger).asRuntimeException()

        val updatedParty = party.copy {
            this.members.clear()
            this.members.addAll(party.membersList.filterNot { it.name == name })
        }

        partyRepository.updateParty(updatedParty)
        player.partyId = null

        return Pair(updatedParty, memberToRemove)
    }

    /**
     * Updates the role of a player
     *
     * @param name the name of the player
     * @param role the role the player should get
     *
     * @return true if the role was changed at all, false otherwise; paired with the party object
     * @throws Status.NOT_FOUND if the player isn't part of any party
     */
    fun updateRole(name: String, role: PartyRole): Pair<Boolean, Party> {
        val party = getParty(name)
            ?: throw Status.NOT_FOUND.withDescription("Failed to get party: $name isn't part of any party")
                .log(logger).asRuntimeException()

        party.copy {
            val tempMembers = this.members.toMutableList()

            val partyMember = party.membersList.first { it.name == name }
            if (partyMember.role == role) return false to party

            partyMember.copy {
                this.role = role
            }.also { updatedMember ->
                tempMembers.remove(partyMember)
                tempMembers.add(updatedMember)
            }

            if(role == PartyRole.OWNER) this.ownerId = partyMember.uuid

            this.members.clear()
            this.members.addAll(tempMembers)
        }.also { updatedParty ->
            partyRepository.updateParty(updatedParty)
            return true to updatedParty
        }
    }
}