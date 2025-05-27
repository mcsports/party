package club.mcsports.droplet.party

import club.mcsports.droplet.party.extension.asUuid
import club.mcsports.droplet.party.extension.fetchPlayer
import club.mcsports.droplet.party.player.PartyInformationHolder
import com.google.protobuf.timestamp
import com.mcsports.party.v1.Party
import com.mcsports.party.v1.PartyMember
import com.mcsports.party.v1.PartyRole
import com.mcsports.party.v1.copy
import com.mcsports.party.v1.partyInvite
import com.mcsports.party.v1.partyMember
import java.time.Instant
import java.util.UUID

class PartyManager {
    val parties = mutableMapOf<UUID, Party>()
    val informationHolders = mutableMapOf<String, PartyInformationHolder>()

    fun assignMemberToParty(memberName: String, role: PartyRole, party: Party) {
        party.membersList.add(partyMember {
            this.name = memberName
            this.role = role

            val now = Instant.now()
            val timeStampNow = timestamp {
                this.nanos = now.nano
                this.seconds = now.epochSecond
            }

            this.timeJoined = timeStampNow
        })

        party.invitesList.removeIf { it.invitedName == memberName }
        informationHolder(memberName).partyId = party.id.asUuid()
    }

    fun removeMemberFromParty(memberName: String, party: Party): PartyMember? {
        party.membersList.removeIf { it.name == memberName }
        informationHolder(memberName).partyId = null

        if(party.membersList.isEmpty()) {
            parties.remove(party.id.asUuid())
            return null
        }

        return party.membersList.filter { it.role != PartyRole.OWNER }.minByOrNull { loopMember ->
            val timeJoined = loopMember.timeJoined
            Instant.ofEpochSecond(timeJoined.seconds, timeJoined.nanos.toLong())
        }
    }

    fun inviteMemberToParty(memberName: String, invitorName: String, party: Party) {
        val inviteHolder = informationHolder(memberName).invites
        inviteHolder.put(invitorName, party.id.asUuid())

        party.invitesList.add(partyInvite {
            this.invitorName = invitorName
            this.invitedName = memberName
        })
    }

    fun deleteMemberInvite(memberName: String, party: Party) {
        party.invitesList.removeIf { it.invitedName == memberName }

        val holderInvites = informationHolder(memberName).invites
        holderInvites.filter { it.value == party.id.asUuid() }.keys.forEach { key -> holderInvites.remove(key) }
    }

    fun transferOwnership(memberName: String, leave: Boolean, party: Party) {
        val oldOwner = party.membersList.first { it.role == PartyRole.OWNER} //Predicate before was 'it.id == party.ownerId' wtf

        if(!leave) {
            val overwrittenOldOwner = oldOwner.copy {
                this.role = PartyRole.MOD
            }
            party.membersList.remove(oldOwner)
            party.membersList.add(overwrittenOldOwner)
        } else party.membersList.remove(oldOwner)

        val newOwner = party.membersList.first { it.name == memberName }
        val overwrittenNewOwner = newOwner.copy {
            this.role = PartyRole.OWNER
        }

        party.membersList.remove(newOwner)
        party.membersList.add(overwrittenNewOwner)
    }

    fun setMemberRole(memberName: String, role: PartyRole, party: Party) {
        val partyMember = party.membersList.first { it.name == memberName }.copy {
            this.role = role
        }

        party.membersList.removeIf { it.name == memberName }
        party.membersList.add(partyMember)
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

    fun informationHolder(name: String) = informationHolders[name] ?: PartyInformationHolder(null, mutableMapOf()).also { informationHolders[name] = it }
}