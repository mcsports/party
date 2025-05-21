package club.mcsports.droplet.party.api

import club.mcsports.droplet.party.api.impl.coroutine.PartyApiCoroutineImpl
import club.mcsports.droplet.party.api.impl.future.PartyApiFutureImpl
import club.mcsports.droplet.party.shared.Party
import club.mcsports.droplet.party.shared.PartySettings
import java.util.*
import java.util.concurrent.CompletableFuture

interface PartyApi {

    interface Coroutine {

        suspend fun getParty(id: UUID): Party

        suspend fun deleteParty(id: UUID)

        suspend fun createParty(id: UUID, creator: UUID, initialInvites: MutableSet<UUID>, settings: PartySettings): Party

    }

    interface Future {

        fun getParty(id: UUID): CompletableFuture<Party>

        fun deleteParty(id: UUID): CompletableFuture<Void>

        fun createParty(id: UUID, creator: UUID, initialInvites: MutableSet<UUID>, settings: PartySettings): CompletableFuture<Party>

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