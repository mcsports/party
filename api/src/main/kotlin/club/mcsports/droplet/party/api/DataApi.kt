package club.mcsports.droplet.party.api

import com.mcsports.party.v1.Party
import com.mcsports.party.v1.PartyMember
import java.util.UUID
import java.util.concurrent.CompletableFuture

interface DataApi {

    interface Coroutine {
        suspend fun getParty(member: UUID): Party
        suspend fun getMember(member: UUID): PartyMember
    }

    interface Future {
        fun getParty(member: UUID): CompletableFuture<Party>
        fun getMember(member: UUID): CompletableFuture<PartyMember>
    }

}