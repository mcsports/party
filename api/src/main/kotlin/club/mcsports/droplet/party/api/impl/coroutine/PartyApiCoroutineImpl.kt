package club.mcsports.droplet.party.api.impl.coroutine

import club.mcsports.droplet.party.api.PartyApi
import com.mcsports.party.v1.Party
import com.mcsports.party.v1.PartySettings
import java.util.*

class PartyApiCoroutineImpl : PartyApi.Coroutine {
    override suspend fun getParty(id: UUID): Party {
        TODO("Not yet implemented")
    }

    override suspend fun deleteParty(id: UUID) {
        TODO("Not yet implemented")
    }

    override suspend fun createParty(
        creator: UUID,
        initialInvites: MutableSet<UUID>,
        settings: PartySettings
    ): Party {
        TODO("Not yet implemented")
    }

    override fun invitePartyMember(memberId: UUID, executor: UUID) {
        TODO("Not yet implemented")
    }

    override fun joinPartyMember(memberId: UUID, partyId: UUID) {
        TODO("Not yet implemented")
    }

    override fun kickPartyMember(memberId: UUID, partyId: UUID, executor: UUID) {
        TODO("Not yet implemented")
    }

    override fun promotePartyMember(memberId: UUID, partyId: UUID, executor: UUID) {
        TODO("Not yet implemented")
    }

    override fun demotePartyMember(memberId: UUID, partyId: UUID, executor: UUID) {
        TODO("Not yet implemented")
    }


}