package club.mcsports.droplet.party.api.impl.coroutine

import club.mcsports.droplet.party.api.PartyApi
import club.mcsports.droplet.party.shared.Party
import club.mcsports.droplet.party.shared.PartySettings
import java.util.*

class PartyApiCoroutineImpl : PartyApi.Coroutine {

    override suspend fun getParty(id: UUID): Party {
        TODO("Not yet implemented")
    }

    override suspend fun deleteParty(id: UUID) {
        TODO("Not yet implemented")
    }

    override suspend fun createParty(
        id: UUID,
        creator: UUID,
        initialInvites: MutableSet<UUID>,
        settings: PartySettings,
    ): Party {
        TODO("Not yet implemented")
    }

}