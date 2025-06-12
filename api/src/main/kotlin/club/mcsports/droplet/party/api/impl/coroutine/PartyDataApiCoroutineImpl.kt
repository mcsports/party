package club.mcsports.droplet.party.api.impl.coroutine

import app.simplecloud.droplet.api.auth.AuthCallCredentials
import club.mcsports.droplet.party.api.DataApi
import com.mcsports.party.v1.Party
import com.mcsports.party.v1.PartyDataGrpcKt
import com.mcsports.party.v1.PartyRole
import com.mcsports.party.v1.memberRoleRequest
import com.mcsports.party.v1.partyRequest
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

    override suspend fun getMemberRole(member: UUID): PartyRole {
        return api.getMemberRole(
            memberRoleRequest {
                this.memberId = member.toString()
            }
        ).role
    }

}