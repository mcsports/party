package club.mcsports.droplet.party.extension

import java.util.UUID
fun String.asUuid() = UUID.fromString(this)