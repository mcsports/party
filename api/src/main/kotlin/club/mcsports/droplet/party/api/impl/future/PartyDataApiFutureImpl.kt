package club.mcsports.droplet.party.api.impl.future

import app.simplecloud.droplet.api.auth.AuthCallCredentials
import app.simplecloud.droplet.api.future.toCompletable
import club.mcsports.droplet.party.api.DataApi
import com.mcsports.party.v1.MemberRequest
import com.mcsports.party.v1.Party
import com.mcsports.party.v1.PartyDataGrpc
import com.mcsports.party.v1.PartyMember
import com.mcsports.party.v1.PartyRequest
import io.grpc.ManagedChannel
import java.util.UUID
import java.util.concurrent.CompletableFuture

class PartyDataApiFutureImpl(
    credentials: AuthCallCredentials,
    channel: ManagedChannel
) : DataApi.Future {
    private val api = PartyDataGrpc.newFutureStub(channel).withCallCredentials(credentials)

    override fun getParty(member: UUID): CompletableFuture<Party> {
        return api.getParty(
            PartyRequest.newBuilder().setMemberId(member.toString()).build()
        ).toCompletable().thenApply { it.party }
    }

    override fun getMember(member: UUID): CompletableFuture<PartyMember> {
        return api.getMember(
            MemberRequest.newBuilder().setMemberId(member.toString()).build()
        ).toCompletable().thenApply { it.member }
    }
}