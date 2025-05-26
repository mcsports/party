package club.mcsports.droplet.party

import club.mcsports.droplet.party.extension.asUuid
import club.mcsports.droplet.party.player.PartyInformationHolder
import com.google.protobuf.timestamp
import com.mcsports.party.v1.Party
import com.mcsports.party.v1.PartyMember
import com.mcsports.party.v1.PartyRole
import com.mcsports.party.v1.partyInvite
import com.mcsports.party.v1.partyMember
import java.time.Instant
import java.util.UUID

class PartyManager {
    val parties = mutableMapOf<UUID, Party>()
    val informationHolders = mutableMapOf<UUID, PartyInformationHolder>()

    fun assignMemberToParty(member: UUID, role: PartyRole, party: Party) {
        party.membersList.add(partyMember {
            this.id = member.toString()
            this.role = role

            val now = Instant.now()
            val timeStampNow = timestamp {
                this.nanos = now.nano
                this.seconds = now.epochSecond
            }

            this.timeJoined = timeStampNow
        })

        party.invitesList.removeIf { it.id == member.toString() }
        informationHolder(member).partyId = party.id.asUuid()
    }

    fun removeMemberFromParty(member: UUID, party: Party): PartyMember? {
        party.membersList.removeIf { it.id == member.toString() }
        informationHolder(member).partyId = null

        if(party.membersList.isEmpty()) {
            parties.remove(party.id.asUuid())
            return null
        }

        return party.membersList.minByOrNull { loopMember ->
            val timeJoined = loopMember.timeJoined
            Instant.ofEpochSecond(timeJoined.seconds, timeJoined.nanos.toLong())
        }
    }

    fun inviteMemberToParty(member: UUID, invitorName: String, invitorId: UUID, party: Party) {
        val inviteHolder = informationHolder(member).invites
        inviteHolder.put(invitorName, party.id.asUuid())

        party.invitesList.add(partyInvite {
            this.invitorId = invitorId.toString()
            this.id = member.toString()
        })
    }

    fun deleteMemberInvite(member: UUID, party: Party) {
        party.invitesList.removeIf { it.id == member.toString() }

        val holderInvites = informationHolder(member).invites
        holderInvites.filter { it.value == party.id.asUuid() }.keys.forEach { key -> holderInvites.remove(key) }
    }

    fun generatePartyId(): UUID {
        if (parties.isEmpty()) return UUID.randomUUID()
        else {
            var uuid = UUID.randomUUID()

            while (parties[uuid] != null) {
                uuid = UUID.randomUUID()
            }

            return uuid
        }

    }

    fun informationHolder(uuid: UUID) = informationHolders[uuid] ?: PartyInformationHolder(null, mutableMapOf()).also { informationHolders[uuid] = it }
}