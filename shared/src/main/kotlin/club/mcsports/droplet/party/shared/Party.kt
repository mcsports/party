package club.mcsports.droplet.party.shared

import club.mcsports.droplet.party.shared.member.PartyMember
import java.util.*

data class Party(
    val id: UUID,
    val members: MutableMap<UUID, PartyMember> = mutableMapOf(),
    val pendingInvites: MutableSet<UUID> = mutableSetOf(),
    val settings: PartySettings = PartySettings()
)
