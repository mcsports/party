package club.mcsports.droplet.party.api

import com.mcsports.party.v1.Party
import java.util.UUID
import java.util.concurrent.CompletableFuture

interface DataApi {

    interface Coroutine {
        suspend fun getParty(member: UUID): Party
    }

    interface Future {
        fun getParty(member: UUID): CompletableFuture<Party>
    }

}