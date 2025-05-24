package club.mcsports.droplet.party.service

import app.simplecloud.droplet.player.api.PlayerApi
import club.mcsports.droplet.party.shared.Party
import club.mcsports.droplet.party.shared.PartyInvite
import club.mcsports.droplet.party.shared.PartySettings
import club.mcsports.droplet.party.shared.member.PartyMember
import club.mcsports.droplet.party.shared.member.PartyRole
import club.mcsports.droplet.party.shared.player.PartyInformationHolder
import java.util.UUID

class PartyService(
    private val playerApi: PlayerApi.Coroutine
) {

    val parties = mutableMapOf<UUID, Party>()
    val informationHolders = mutableMapOf<UUID, PartyInformationHolder>()

    fun createParty(
        creator: UUID,
        initialInvites: MutableSet<UUID>,
        settings: PartySettings
    ): Party {
        val informationHolder = informationHolder(creator)

        val informationParty = informationHolder.partyId
        if(informationParty != null && parties[informationParty] != null) {
            //TODO: Send message that the player is already in a party
            return parties[informationParty]!!
        }

        val partyId = generatePartyId()
        val party = Party(
            id = partyId,
            members = mutableMapOf(),
            pendingInvites = initialInvites.associateWith { PartyInvite(creator) }.toMutableMap(),
            settings = settings,
            owner = creator
        )

        parties[partyId] = party
        addPartyMember(party, creator, PartyRole.OWNER)

        return party
    }

    /**
     * IMPORTANT: Will overwrite the player's current party
     */
    fun addPartyMember(
        party: Party,
        member: UUID,
        role: PartyRole
    ) {
        val pendingInvites = party.pendingInvites

        if(party.settings.isPrivate && !pendingInvites.containsKey(member)) {
            //TODO: Send player message that he isn't invited
            return
        }

        if(informationHolder(member).partyId != null) {
            //TODO: has to be in no party to join another: send message
            return
        }

        party.members[member] = PartyMember(role)
        pendingInvites.remove(member)
        informationHolder(member).partyId = party.id
    }

    fun removePartyMember(
        party: Party,
        member: UUID,
        executor: UUID
    ) {
        val members = party.members
        val partyMember = members[member]

        if(partyMember == executor) {
            members.remove(member)
            informationHolder(member).partyId = null

            //TODO: Send message to the player because HE LEFT!!
            return
        }

        if((members[executor]?.role?.strength ?: 0) < (partyMember?.role?.strength ?: 0)) {
            //TODO: Send message to executor because he's not "strong" enough to remove the target

            return
        }

        party.members.remove(member)
        informationHolder(member).partyId = null
        //TODO: Send executor successfull message
        //TODO: Send member got kicked message

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

    fun informationHolder(uuid: UUID) = informationHolders[uuid] ?: PartyInformationHolder(null).also { informationHolders[uuid] = it }

}