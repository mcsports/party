package club.mcsports.droplet.party.extension

import net.kyori.adventure.text.minimessage.MiniMessage

fun miniMessage(message: String) = MiniMessage.miniMessage().deserialize(message)