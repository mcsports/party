package club.mcsports.droplet.party

import app.simplecloud.droplet.api.time.ProtobufTimestamp
import club.mcsports.droplet.party.extension.asUuid
import club.mcsports.droplet.party.extension.fetchPlayer
import club.mcsports.droplet.party.player.PartyInformationHolder
import com.mcsports.party.v1.Party
import com.mcsports.party.v1.PartyMember
import com.mcsports.party.v1.PartyRole
import com.mcsports.party.v1.copy
import com.mcsports.party.v1.partyInvite
import com.mcsports.party.v1.partyMember
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

class PartyManager {
    val parties = mutableMapOf<UUID, Party>()
    val informationHolders = mutableMapOf<String, PartyInformationHolder>()

    fun assignMemberToParty(memberName: String, uuid: UUID, role: PartyRole, party: Party) {
        party.copy {
            this.members.add(partyMember {
                this.name = memberName
                this.uuid = uuid.toString()
                this.role = role
                this.timeJoined = ProtobufTimestamp.fromLocalDateTime(LocalDateTime.now())
            })

            val tempInvites = this.invites.toMutableList()
            tempInvites.removeIf { it.invitedName == memberName }

            this.invites.clear()
            this.invites.addAll(tempInvites)
        }.also { updatedParty ->
            parties[updatedParty.id.asUuid()] = updatedParty
        }

        informationHolder(memberName).partyId = party.id.asUuid()
    }

    fun removeMemberFromParty(memberName: String, party: Party): PartyMember? {
        party.copy {
            val tempMembers = this.members.toMutableList()
            tempMembers.toMutableList().removeIf { it.name == memberName }
            informationHolder(memberName).partyId = null

            this.members.clear()
            if(tempMembers.isEmpty()) {
                parties.remove(party.id.asUuid())
                return null
            }
            this.members.addAll(tempMembers)
        }.also { updatedParty ->
            parties[updatedParty.id.asUuid()] = updatedParty

            return updatedParty.membersList.filter { it.role != PartyRole.OWNER }.minByOrNull { loopMember ->
                val timeJoined = loopMember.timeJoined
                Instant.ofEpochSecond(timeJoined.seconds, timeJoined.nanos.toLong())
            }
        }
    }

    fun inviteMemberToParty(memberName: String, invitorName: String, party: Party) {
        val inviteHolder = informationHolder(memberName).invites
        inviteHolder[invitorName] = party.id.asUuid()

        party.copy {
            val tempInvites = this.invites.toMutableList()
            tempInvites.add(partyInvite {
                this.invitorName = invitorName
                this.invitedName = memberName
            })

            this.invites.clear()
            this.invites.addAll(tempInvites)
        }.also { updatedParty ->
            parties[updatedParty.id.asUuid()] = updatedParty
        }
    }

    fun deleteMemberInvite(memberName: String, party: Party) {
        party.copy {
            val tempInvites = this.invites.toMutableList()
            tempInvites.removeIf { it.invitedName == memberName }

            this.invites.clear()
            this.invites.addAll(tempInvites)
        }.also { updatedParty ->
            parties[updatedParty.id.asUuid()] = updatedParty
        }

        val holderInvites = informationHolder(memberName).invites
        holderInvites.filter { it.value == party.id.asUuid() }.keys.forEach { key -> holderInvites.remove(key) }
    }

    suspend fun transferOwnership(memberName: String, leave: Boolean, party: Party) {
        val oldOwner = party.membersList.first { it.role == PartyRole.OWNER } //Predicate before was 'it.id == party.ownerId' wtf

        party.copy {
            val tempMembers = this.members.toMutableList()

            if(!leave) {
                val overwrittenOldOwner = oldOwner.copy {
                    this.role = PartyRole.MOD
                }
                tempMembers.remove(oldOwner)
                tempMembers.add(overwrittenOldOwner)
            } else tempMembers.remove(oldOwner)

            val newOwner = tempMembers.first { it.name == memberName }
            val overwrittenNewOwner = newOwner.copy {
                this.role = PartyRole.OWNER
            }

            tempMembers.remove(newOwner)
            tempMembers.add(overwrittenNewOwner)

            this.members.clear()
            this.members.addAll(tempMembers)

            this.ownerId = newOwner.uuid
        }.also { updatedParty ->
            parties[updatedParty.id.asUuid()] = updatedParty

            if (leave) informationHolder(oldOwner.name).partyId = null
            else informationHolder(oldOwner.name).partyId = updatedParty.id.asUuid()
        }

    }

    fun setMemberRole(memberName: String, role: PartyRole, party: Party) {
        val partyMember = party.membersList.first { it.name == memberName }.copy {
            this.role = role
        }

        party.copy {
            val tempMembers = this.members.toMutableList()
            tempMembers.removeIf { it.name == memberName }
            tempMembers.add(partyMember)

            this.members.clear()
            this.members.addAll(tempMembers)
        }.also { updatedParty ->
            parties[updatedParty.id.asUuid()] = updatedParty
        }
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