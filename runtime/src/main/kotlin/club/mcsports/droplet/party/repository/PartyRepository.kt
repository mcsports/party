package club.mcsports.droplet.party.repository

import club.mcsports.droplet.party.extension.asUuid
import com.mcsports.party.v1.Party
import com.mcsports.party.v1.PartySettings
import com.mcsports.party.v1.party
import com.mcsports.party.v1.partySettings
import java.util.UUID

class PartyRepository {
    val parties = mutableMapOf<UUID, Party>()

    /**
     * Creates a party object
     *
     * @param owner the owner of the party
     * @param settings party-related settings
     *
     * @return the created party object
     */
    fun createParty(
        owner: UUID,
        settings: PartySettings = partySettings {
            this.isPrivate = true
            this.allowChatting = true
            this.allowInvites = true
        }
    ): Party {
        return party {
            this.id = UUID.randomUUID().toString()
            this.ownerId = owner.toString()
            this.settings = settings
        }.also { party -> updateParty(party) }
    }

    /**
     * Deletes a party
     * @param party the party to remove
     * @return the deleted party, null if no party was associated with the given id
     */
    fun deleteParty(party: Party): Party? = deleteParty(party.id.asUuid())

    /**
     * Deletes a party
     * @param id the id of the party to remove
     * @return the deleted party, null if no party was associated with the given id
     */
    fun deleteParty(id: UUID): Party? {
        return parties.remove(id)
    }

    /**
     * Gets a party
     * @param id the id of the party
     * @return the corresponding party object, null if no object was found obv
     */
    fun getParty(id: UUID): Party? = parties[id]

    /**
     * Updates a party
     * @param party the new updated party object
     */
    fun updateParty(party: Party) { parties[party.id.asUuid()] = party }
}