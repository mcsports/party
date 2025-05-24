package club.mcsports.droplet.party.shared.member

enum class PartyRole(
    val strength: Int
) {

    OWNER(2),
    MOD(1),
    MEMBER(0);

}