package club.mcsports.droplet.party.player

import java.util.UUID

/**
 * Stores data that you might've to get very fast without iterating through lists
 * At the moment it's only the current party of the player, if he is in one at all.
 */
data class PartyInformationHolder(var partyId: UUID?)