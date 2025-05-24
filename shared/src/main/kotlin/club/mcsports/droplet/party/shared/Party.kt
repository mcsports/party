package club.mcsports.droplet.party.shared

import club.mcsports.droplet.party.shared.member.PartyMember
import java.util.*

data class Party(
    val id: UUID,
    val owner: UUID,
    val members: MutableMap<UUID, PartyMember>,
    val pendingInvites: MutableMap<UUID, PartyInvite>,
    val settings: PartySettings
)