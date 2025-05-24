package club.mcsports.droplet.party.shared

import java.util.UUID

data class PartyInvite(
    val creator: UUID,
    val timeCreated: Long = System.currentTimeMillis()
)