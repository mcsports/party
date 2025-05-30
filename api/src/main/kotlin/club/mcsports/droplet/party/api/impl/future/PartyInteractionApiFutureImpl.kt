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
import com.mcsports.party.v1.handleInviteRequest
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
        initialInvites: List<String>
    ): CompletableFuture<Party> {
        return api.createParty(
            CreatePartyRequest.newBuilder()
                .setCreatorId(creator.toString())
                .setSettings(settings)
                .addAllInvitedNames(initialInvites)
                .build()
        ).toCompletable().thenApply { it.createdParty }
    }

    override fun inviteMember(memberName: String, executor: UUID) {
        api.invitePlayer(
            InvitePlayerRequest.newBuilder()
                .setMemberName(memberName)
                .setExecutorId(executor.toString())
                .build()
        )
    }

    override fun promoteMember(memberName: String, executor: UUID) {
        api.promoteMember(
            PromoteMemberRequest.newBuilder()
                .setMemberName(memberName)
                .setExecutorId(executor.toString())
                .build()
        )
    }

    override fun demoteMember(memberName: String, executor: UUID) {
        api.demoteMember(
            DemoteMemberRequest.newBuilder()
                .setMemberName(memberName)
                .setExecutorId(executor.toString())
                .build()
        )
    }

    override fun partyChat(executor: UUID, message: Component) {
        api.chat(
            ChatRequest.newBuilder()
                .setExecutorId(executor.toString())
                .setMessage(
                    AdventureComponent.newBuilder()
                        .setJson(gsonSerializer.serialize(message))
                        .build()
                ).build()
        )
    }

    override fun kickMember(memberName: String, executor: UUID) {
        api.kickMember(
            KickMemberRequest.newBuilder()
                .setMemberName(memberName)
                .setExecutorId(executor.toString())
                .build()
        )
    }

    override fun acceptPartyInvite(invitorName: String, executor: UUID) {
        handleInvite(invitorName, executor, true)
    }

    override fun denyPartyInvite(invitorName: String, executor: UUID) {
        handleInvite(invitorName, executor, false)
    }

    override fun deleteParty(executor: UUID) {
        api.deleteParty(
            DeletePartyRequest.newBuilder()
                .setExecutorId(executor.toString())
                .build()
        )
    }

    override fun memberLeaveParty(member: UUID) {
        api.leaveParty(
            LeavePartyRequest.newBuilder()
                .setExecutorId(member.toString())
                .build()
        )
    }

    private fun handleInvite(invitorName: String, executor: UUID, accepted: Boolean) {
        api.handleInvite(
            handleInviteRequest {
                this.executorId = executor.toString()
                this.invitorName = invitorName
                this.accepted = accepted
            }
        )
    }
}