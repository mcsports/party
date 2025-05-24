package club.mcsports.droplet.party.api

import club.mcsports.droplet.party.api.impl.coroutine.PartyApiCoroutineImpl
import club.mcsports.droplet.party.api.impl.future.PartyApiFutureImpl
import club.mcsports.droplet.party.shared.Party
import club.mcsports.droplet.party.shared.PartySettings
import io.grpc.Status
import java.util.*
import java.util.concurrent.CompletableFuture

interface PartyApi {

    interface Coroutine {

        suspend fun getParty(id: UUID): Party

        suspend fun deleteParty(id: UUID)

        suspend fun createParty(
            creator: UUID,
            initialInvites: MutableSet<UUID>,
            settings: PartySettings
        ): Party

        fun invitePartyMember(memberId: UUID, executor: UUID)
        fun joinPartyMember(memberId: UUID, partyId: UUID)
        fun kickPartyMember(memberId: UUID, partyId: UUID, executor: UUID)
        fun promotePartyMember(memberId: UUID, partyId: UUID, executor: UUID)
        fun demotePartyMember(memberId: UUID, partyId: UUID, executor: UUID)
    }

    interface Future {

        fun getParty(partyId: UUID): CompletableFuture<Party>

        fun deleteParty(partyId: UUID, executor: UUID)

        fun createParty(
            creator: UUID,
            initialInvites: MutableSet<UUID>,
            settings: PartySettings
        ): CompletableFuture<Party>

        fun invitePartyMember(memberId: UUID, executor: UUID)
        fun joinPartyMember(memberId: UUID, partyId: UUID)
        fun kickPartyMember(memberId: UUID, partyId: UUID, executor: UUID)
        fun promotePartyMember(memberId: UUID, partyId: UUID, executor: UUID)
        fun demotePartyMember(memberId: UUID, partyId: UUID, executor: UUID)

    }

    companion object {

        @JvmStatic
        fun createFutureApi(): Future {
            return PartyApiFutureImpl()
        }

        @JvmStatic
        fun createCoroutineApi(): Coroutine {
            return PartyApiCoroutineImpl()
        }

    }

}