package club.mcsports.droplet.party.shared.extension

import com.mcsports.party.v1.Party
import com.mcsports.party.v1.PartyMember
import java.util.UUID

fun Party.getMember(name: String): PartyMember? =
    this.membersList.find { partyMember -> partyMember.name.equals(name, true) }

fun Party.getMember(uuid: UUID): PartyMember? =
    this.membersList.find { partyMember -> partyMember.uuid == uuid.toString() }