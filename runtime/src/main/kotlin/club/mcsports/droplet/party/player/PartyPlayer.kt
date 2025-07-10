package club.mcsports.droplet.party.player

import java.util.UUID

/**
 * Stores data that you might've to get very fast without iterating through lists
 */
data class PartyPlayer(
    /**
     * id of the party the player is in
     */
    var partyId: UUID?,

    /**
     * invites the player got associated with the party id
     */
    val invites: MutableMap<String, UUID>
) {

    /**
     * Gets an invitation
     * @param name the name of the player who invited this player
     * @return the corresponding party id
     */
    fun getInvite(name: String): UUID? = invites[name]

}