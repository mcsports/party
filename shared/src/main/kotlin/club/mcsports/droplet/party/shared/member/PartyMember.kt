package club.mcsports.droplet.party.shared.member

data class PartyMember(
    var role: PartyRole,
    val timeJoined: Long = System.currentTimeMillis()
)
