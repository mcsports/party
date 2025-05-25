package club.mcsports.droplet.party.api.impl.future

import club.mcsports.droplet.party.api.PartyApi
import com.mcsports.party.v1.Party
import com.mcsports.party.v1.PartySettings
import java.util.*
import java.util.concurrent.CompletableFuture

class PartyApiFutureImpl : PartyApi.Future {

    override fun getParty(id: UUID): CompletableFuture<Party> {
        TODO("Not yet implemented")
    }

    override fun deleteParty(partyId: UUID, executor: UUID) {
        TODO("Not yet implemented")
    }

    override fun createParty(
        creator: UUID,
        initialInvites: MutableSet<UUID>,
        settings: PartySettings
    ): CompletableFuture<Party> {
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