package club.mcsports.droplet.party.api

import com.mcsports.party.v1.Party
import com.mcsports.party.v1.PartyRole
import java.util.UUID
import java.util.concurrent.CompletableFuture

interface DataApi {

    interface Coroutine {
        suspend fun getParty(member: UUID): Party
        suspend fun getMemberRole(member: UUID): PartyRole
    }

    interface Future {
        fun getParty(member: UUID): CompletableFuture<Party>
        fun getMemberRole(member: UUID): CompletableFuture<PartyRole>
    }

}