package club.mcsports.droplet.party.api.impl.coroutine

import app.simplecloud.droplet.api.auth.AuthCallCredentials
import club.mcsports.droplet.party.api.InteractionApi
import com.mcsports.friend.v1.adventureComponent
import com.mcsports.party.v1.Party
import com.mcsports.party.v1.PartyInteractionGrpcKt
import com.mcsports.party.v1.PartySettings
import com.mcsports.party.v1.chatRequest
import com.mcsports.party.v1.createPartyRequest
import com.mcsports.party.v1.deletePartyRequest
import com.mcsports.party.v1.demoteMemberRequest
import com.mcsports.party.v1.invitePlayerRequest
import com.mcsports.party.v1.kickMemberRequest
import com.mcsports.party.v1.leavePartyRequest
import com.mcsports.party.v1.promoteMemberRequest
import io.grpc.ManagedChannel
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import java.util.UUID

class PartyInteractionApiCoroutineImpl(
    credentials: AuthCallCredentials,
    channel: ManagedChannel
) : InteractionApi.Coroutine {
    private val api = PartyInteractionGrpcKt.PartyInteractionCoroutineStub(channel).withCallCredentials(credentials)
    private val gsonSerializer = GsonComponentSerializer.gson()

    override suspend fun createParty(
        creator: UUID,
        settings: PartySettings,
        initialInvites: List<UUID>
    ): Party {
        return api.createParty(
            createPartyRequest {
                this.creatorId = creator.toString()
                this.settings = settings
                this.invitedIds.addAll(initialInvites.map(UUID::toString))
            }
        ).createdParty
    }

    override suspend fun inviteMember(member: UUID, executor: UUID) {
        api.invitePlayer(
            invitePlayerRequest {
                this.memberId = member.toString()
                this.executorId = executor.toString()
            }
        )
    }

    override suspend fun promoteMember(member: UUID, executor: UUID) {
        api.promoteMember(
            promoteMemberRequest {
                this.memberId = member.toString()
                this.executorId = executor.toString()
            }
        )
    }

    override suspend fun demoteMember(member: UUID, executor: UUID) {
        api.demoteMember(
            demoteMemberRequest {
                this.memberId = member.toString()
                this.executorId = executor.toString()
            }
        )
    }

    override suspend fun partyChat(member: UUID, message: Component) {
        api.chat(
            chatRequest {
                this.memberId = member.toString()
                this.message = adventureComponent { this.json = gsonSerializer.serialize(message) }
            }
        )
    }

    override suspend fun kickMember(member: UUID, executor: UUID) {
        api.kickMember(
            kickMemberRequest {
                this.memberId = member.toString()
                this.executorId = executor.toString()
            }
        )
    }

    override suspend fun deleteParty(member: UUID) {
        api.deleteParty(
            deletePartyRequest {
                this.executorId = member.toString()
            }
        )
    }

    override suspend fun memberLeaveParty(member: UUID) {
        api.leaveParty(
            leavePartyRequest {
                this.memberId = member.toString()
            }
        )
    }

}