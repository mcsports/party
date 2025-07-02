package club.mcsports.droplet.party.api.impl.coroutine

import app.simplecloud.droplet.api.auth.AuthCallCredentials
import club.mcsports.droplet.party.api.DataApi
import com.mcsports.party.v1.*
import io.grpc.ManagedChannel
import java.util.UUID

class PartyDataApiCoroutineImpl(
    credentials: AuthCallCredentials,
    channel: ManagedChannel
): DataApi.Coroutine {
    private val api = PartyDataGrpcKt.PartyDataCoroutineStub(channel).withCallCredentials(credentials)

    override suspend fun getParty(member: UUID): Party {
        return api.getParty(
            partyRequest {
                this.memberId = member.toString()
            }
        ).party
    }

    override suspend fun getMember(member: UUID): PartyMember {
        return api.getMember(
            memberRequest {
                this.memberId = member.toString()
            }
        ).member
    }

}