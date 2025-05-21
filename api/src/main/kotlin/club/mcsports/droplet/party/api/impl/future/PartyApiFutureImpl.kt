package club.mcsports.droplet.party.api.impl.future

import club.mcsports.droplet.party.api.PartyApi
import club.mcsports.droplet.party.shared.Party
import club.mcsports.droplet.party.shared.PartySettings
import java.util.*
import java.util.concurrent.CompletableFuture

class PartyApiFutureImpl : PartyApi.Future {

    override fun getParty(id: UUID): CompletableFuture<Party> {
        TODO("Not yet implemented")
    }

    override fun deleteParty(id: UUID): CompletableFuture<Void> {
        TODO("Not yet implemented")
    }

    override fun createParty(
        id: UUID,
        creator: UUID,
        initialInvites: MutableSet<UUID>,
        settings: PartySettings,
    ): CompletableFuture<Party> {
        TODO("Not yet implemented")
    }

}