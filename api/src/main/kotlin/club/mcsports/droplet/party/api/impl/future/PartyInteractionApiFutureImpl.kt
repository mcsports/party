package club.mcsports.droplet.party.api.impl.future

import app.simplecloud.droplet.api.auth.AuthCallCredentials
import app.simplecloud.droplet.api.future.toCompletable
import club.mcsports.droplet.party.api.InteractionApi
import com.mcsports.friend.v1.AdventureComponent
import com.mcsports.party.v1.ChatRequest
import com.mcsports.party.v1.CreatePartyRequest
import com.mcsports.party.v1.DeletePartyRequest
import com.mcsports.party.v1.DemoteMemberRequest
import com.mcsports.party.v1.InvitePlayerRequest
import com.mcsports.party.v1.KickMemberRequest
import com.mcsports.party.v1.LeavePartyRequest
import com.mcsports.party.v1.Party
import com.mcsports.party.v1.PartyInteractionGrpc
import com.mcsports.party.v1.PartySettings
import com.mcsports.party.v1.PromoteMemberRequest
import io.grpc.ManagedChannel
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import java.util.UUID
import java.util.concurrent.CompletableFuture

class PartyInteractionApiFutureImpl(
    credentials: AuthCallCredentials,
    channel: ManagedChannel
) : InteractionApi.Future {
    private val api = PartyInteractionGrpc.newFutureStub(channel).withCallCredentials(credentials)
    private val gsonSerializer = GsonComponentSerializer.gson()

    override fun createParty(
        creator: UUID,
        settings: PartySettings,
        initialInvites: List<UUID>
    ): CompletableFuture<Party> {
        return api.createParty(
            CreatePartyRequest.newBuilder()
                .setCreatorId(creator.toString())
                .setSettings(settings)
                .addAllInvitedIds(initialInvites.map(UUID::toString))
                .build()
        ).toCompletable().thenApply { it.createdParty }
    }

    override fun inviteMember(member: UUID, executor: UUID) {
        api.invitePlayer(
            InvitePlayerRequest.newBuilder()
                .setMemberId(member.toString())
                .setExecutorId(executor.toString())
                .build()
        )
    }

    override fun promoteMember(member: UUID, executor: UUID) {
        api.promoteMember(
            PromoteMemberRequest.newBuilder()
                .setMemberId(member.toString())
                .setExecutorId(executor.toString())
                .build()
        )
    }

    override fun demoteMember(member: UUID, executor: UUID) {
        api.demoteMember(
            DemoteMemberRequest.newBuilder()
                .setMemberId(member.toString())
                .setExecutorId(executor.toString())
                .build()
        )
    }

    override fun partyChat(member: UUID, message: Component) {
        api.chat(
            ChatRequest.newBuilder()
                .setMemberId(member.toString())
                .setMessage(
                    AdventureComponent.newBuilder()
                        .setJson(gsonSerializer.serialize(message))
                        .build()
                ).build()
        )
    }

    override fun kickMember(member: UUID, executor: UUID) {
        api.kickMember(
            KickMemberRequest.newBuilder()
                .setMemberId(member.toString())
                .setExecutorId(executor.toString())
                .build()
        )
    }

    override fun deleteParty(member: UUID) {
        api.deleteParty(
            DeletePartyRequest.newBuilder()
                .setExecutorId(member.toString())
                .build()
        )
    }

    override fun memberLeaveParty(member: UUID) {
        api.leaveParty(
            LeavePartyRequest.newBuilder()
                .setMemberId(member.toString())
                .build()
        )
    }
}