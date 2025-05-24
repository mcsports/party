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

        parties[informationParty]?.let {
            //TODO: Send message that the player is already in a party
            return it
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

        val informationHolder = informationHolder(member)

        if(informationHolder.partyId == party.id) {
            //TODO: Send message that he's already in the party
            return
        }

        if(informationHolder.partyId != null) {
            //TODO: has to be in no party to join another: send message
            return
        }

        party.members[member] = PartyMember(role)
        pendingInvites.remove(member)
        informationHolder(member).partyId = party.id
    }

    fun removePartyMember(
        member: UUID,
        executor: UUID
    ) {
        val partyId = informationHolder(executor).partyId ?: run {
            //TODO: Send player message that he isnt in a party
            return
        }
        val party = parties[partyId] ?: run {
            //TODO: Send player message that his party couldn't be fetched (may be deleted but not synced well enough? idk man)
            return
        }

        if(party.members[executor]?.role == PartyRole.MEMBER) {
            //TODO: Send player message that he isn't permitted to remove players
            return
        }

        val members = party.members
        val informationHolder = informationHolder(member)

        if(informationHolder.partyId != party.id) {
            //TODO: Send player message that the target isn't in his party
            return
        }

        if(member == executor) {
            members.remove(member)
            informationHolder.partyId = null

            //TODO: Send message to the player because HE LEFT!!
            return
        }

        if((members[executor]?.role?.strength ?: 0) < (members[member]?.role?.strength ?: 0)) {
            //TODO: Send message to executor because he's not "strong" enough to remove the target

            return
        }

        party.members.remove(member)
        informationHolder(member).partyId = null
        //TODO: Send executor successful message
        //TODO: Send member got kicked message

    }

    fun invitePartyMember(
        member: UUID,
        executor: UUID,
    ) {
        val partyId = informationHolder(executor).partyId ?: run {
            //TODO: Send player message that he isnt in a party
            return
        }
        val party = parties[partyId] ?: run {
            //TODO: Send player message that his party couldn't be fetched (may be deleted but not synced well enough? idk man)
            return
        }

        if(party.members[executor]?.role == PartyRole.MEMBER) {
            //TODO: Send player message that he isn't permitted to invite players
            //We'll add player invite requests later eventually
            return
        }

        if(member == executor) {
            //TODO: Send message that he cannot invite himself
            return
        }

        if(party.pendingInvites.containsKey(member)) {
            //TODO: Send message that he is already invited to executor
            return
        }

        //TODO: Send success (executor) and invite (member) message
        //TODO: Let the invite expire after 5m or something like that
    }

    fun deleteParty(
        executor: UUID
    ) {
        val partyId = informationHolder(executor).partyId ?: run {
            //TODO: Send player message that he isnt in a party
            return
        }
        val party = parties[partyId] ?: run {
            //TODO: Send player message that his party couldn't be fetched (may be deleted but not synced well enough? idk man)
            return
        }

        if(party.members[executor]?.role != PartyRole.OWNER) {
            //TODO: Send player message that he isn't permitted to invite players
            return
        }

        party.members.keys.forEach { uuid ->
            informationHolder(uuid).partyId = null
            //TODO: Send message that the party has been deleted
        }

        party.members.clear()

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