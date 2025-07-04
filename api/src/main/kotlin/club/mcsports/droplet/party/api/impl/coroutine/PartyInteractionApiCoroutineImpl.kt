package club.mcsports.droplet.party.api.impl.coroutine

import app.simplecloud.droplet.api.auth.AuthCallCredentials
import club.mcsports.droplet.party.api.InteractionApi
import com.mcsports.friend.v1.adventureComponent
import com.mcsports.party.v1.*
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
        initialInvites: List<String>
    ): Party {
        return api.createParty(
            createPartyRequest {
                this.creatorId = creator.toString()
                this.settings = settings
                this.invitedNames.addAll(initialInvites)
            }
        ).createdParty
    }

    override suspend fun inviteMember(memberName: String, executor: UUID) {
        api.invitePlayer(
            invitePlayerRequest {
                this.memberName = memberName
                this.executorId = executor.toString()
            }
        )
    }

    override suspend fun promoteMember(memberName: String, executor: UUID) {
        api.promoteMember(
            promoteMemberRequest {
                this.memberName = memberName
                this.executorId = executor.toString()
            }
        )
    }

    override suspend fun demoteMember(memberName: String, executor: UUID) {
        api.demoteMember(
            demoteMemberRequest {
                this.memberName = memberName
                this.executorId = executor.toString()
            }
        )
    }

    override suspend fun partyChat(executor: UUID, message: Component) {
        api.chat(
            chatRequest {
                this.executorId = executor.toString()
                this.message = adventureComponent {
                    this.json = gsonSerializer.serialize(message)
                }
            }
        )
    }

    override suspend fun kickMember(memberName: String, executor: UUID) {
        api.kickMember(
            kickMemberRequest {
                this.memberName = memberName
                this.executorId = executor.toString()
            }
        )
    }

    override suspend fun acceptPartyInvite(invitorName: String, executor: UUID) {
        handleInvite(invitorName, executor, true)
    }

    override suspend fun denyPartyInvite(invitorName: String, executor: UUID) {
        handleInvite(invitorName, executor, false)
    }

    override suspend fun deleteParty(executor: UUID) {
        api.deleteParty(
            deletePartyRequest {
                this.executorId = executor.toString()
            }
        )
    }

    override suspend fun memberLeaveParty(member: UUID) {
        api.leaveParty(
            leavePartyRequest {
                this.executorId = member.toString()
            }
        )
    }

    override suspend fun memberJoinParty(partyOwnerName: String, member: UUID) {
        api.joinParty(
            joinPartyRequest {
                this.executorId = member.toString()
                this.partyOwnerName = partyOwnerName
            }
        )
    }

    override suspend fun modifyPartySettings(executor: UUID, partySettings: PartySettings) {
        api.modifyPartySettings(
            modifyPartySettingsRequest {
                this.executorId = executor.toString()
                this.settings = partySettings
            }
        )
    }

    private suspend fun handleInvite(invitorName: String, executor: UUID, accepted: Boolean) {
        api.handleInvite(
            handleInviteRequest {
                this.executorId = executor.toString()
                this.invitorName = invitorName
                this.accepted = accepted
            }
        )
    }


}