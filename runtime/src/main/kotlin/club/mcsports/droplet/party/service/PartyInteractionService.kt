package club.mcsports.droplet.party.service

import app.simplecloud.droplet.player.api.PlayerApi
import club.mcsports.droplet.party.PartyManager
import club.mcsports.droplet.party.extension.asUuid
import club.mcsports.droplet.party.extension.miniMessage
import com.mcsports.party.v1.*
import io.grpc.Status
import io.grpc.StatusException
import net.kyori.adventure.text.minimessage.MiniMessage
import java.util.UUID

class PartyInteractionService(
    private val partyManager: PartyManager,
    private val playerApi: PlayerApi.Coroutine
) : PartyInteractionGrpcKt.PartyInteractionCoroutineImplBase() {

    override suspend fun createParty(request: CreatePartyRequest): CreatePartyResponse {
        val creator = request.creatorId.asUuid()

        if (partyManager.informationHolder(creator).partyId != null) {
            try {
                val player = playerApi.getOnlinePlayer(creator)
                player.sendMessage(miniMessage("<red>You cannot create a party as long as you're already part of one."))
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.FAILED_PRECONDITION.withDescription("Failed to create party for user $creator: User is already part of a party")
                .asRuntimeException()
        }

        val partyId = partyManager.generatePartyId()
        val party = party {
            this.id = partyId.toString()
            this.ownerId = creator.toString()
            this.settings = request.settings
            this.invites.addAll(request.invitedIdsList.map { uuid -> partyInvite { this.id = uuid.toString() } })
        }

        partyManager.parties[partyId] = party
        partyManager.assignMemberToParty(creator, PartyRole.OWNER, party)

        try {
            val player = playerApi.getOnlinePlayer(creator)
            player.sendMessage(miniMessage("<gray>You've successfully created your own party."))
        } catch (exception: StatusException) {
            exception.printStackTrace()
        }

        return createPartyResponse {
            this.createdParty = party
            //TODO: Set offlineIds
        }
    }

    override suspend fun deleteParty(request: DeletePartyRequest): DeletePartyResponse {
        val executor = request.executorId.asUuid()
        val party = retrieveParty(executor)
        val partyId = party.id.asUuid()

        val partyMember = party.membersList.firstOrNull { it.id == executor.toString() } ?: run {

            try {
                val player = playerApi.getOnlinePlayer(executor)
                player.sendMessage(miniMessage("<red>Failed to fetch your current party. Please call an administrator about this."))
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.DATA_LOSS.withDescription("Failed to delete party: User $executor isn't part of party $partyId anymore")
                .asRuntimeException()
        }

        if (partyMember.role != PartyRole.OWNER) {
            try {
                val player = playerApi.getOnlinePlayer(executor)
                player.sendMessage(miniMessage("<red>You don't have enough permissions to do delete the party."))
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.PERMISSION_DENIED.withDescription("Failed to delete party: User $executor isn't the party owner")
                .asRuntimeException()
        }

        party.membersList.toList().forEach { loopMember ->
            val uuid = loopMember.id.asUuid()
            partyManager.informationHolder(uuid).partyId = null

            try {
                val loopPlayer = playerApi.getOnlinePlayer(uuid)
                loopPlayer.sendMessage(miniMessage("<red>The party you were in got deleted."))
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }
        }

        party.membersList.clear()
        partyManager.parties.remove(partyId)
        return deletePartyResponse { }
    }

    override suspend fun chat(request: ChatRequest): ChatResponse {
        val executor = request.memberId.asUuid()
        val party = retrieveParty(executor)
        val partyId = party.id

        if (!party.settings.allowChatting) {
            try {
                val player = playerApi.getOnlinePlayer(executor)
                player.sendMessage(miniMessage("<red>Sorry, but chatting is currently disabled in your party."))
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.UNAVAILABLE.withDescription("Failed to send party chat message: Chatting is disabled in party $partyId")
                .asRuntimeException()
        }

        val partyMember = party.membersList.firstOrNull { it.id == executor.toString() } ?: run {

            try {
                val player = playerApi.getOnlinePlayer(executor)
                player.sendMessage(miniMessage("<red>Failed to fetch your current party. Please call an administrator about this."))
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.DATA_LOSS.withDescription("Failed to retrieve party: User $executor isn't part of party $partyId anymore")
                .asRuntimeException()
        }

        val playerName = playerApi.getOnlinePlayer(executor).getName()

        val senderInformationComponent = when (partyMember.role) {
            PartyRole.OWNER -> {
                miniMessage("<color:dark_red>")
            }

            PartyRole.MOD -> {
                miniMessage("<color:red>")
            }

            else -> {
                miniMessage("<color:gray>")
            }
        }.append(miniMessage("$playerName</color>"))

        val messageComponent = MiniMessage.miniMessage().deserialize(request.message.json)
        party.membersList.map { it.id.asUuid() }.forEach { uuid ->
            try {
                val loopPlayer = playerApi.getOnlinePlayer(uuid)
                loopPlayer.sendMessage(
                    miniMessage("<gray>Party-Chat:</gray> ").append(senderInformationComponent)
                        .append(miniMessage("<dark_gray>: <white>")).append(messageComponent)
                )
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }
        }

        return chatResponse { }
    }

    /**
     * Just some empty responses for now
     */
    override suspend fun demoteMember(request: DemoteMemberRequest): DemoteMemberResponse = demoteMemberResponse { }
    override suspend fun handleInvite(request: HandleInviteRequest): HandleInviteResponse = handleInviteResponse { }
    override suspend fun invitePlayer(request: InvitePlayerRequest): InvitePlayerResponse = invitePlayerResponse { }
    override suspend fun leaveParty(request: LeavePartyRequest): LeavePartyResponse = leavePartyResponse { }
    override suspend fun promoteMember(request: PromoteMemberRequest): PromoteMemberResponse = promoteMemberResponse { }

    private suspend fun retrieveParty(memberId: UUID): Party {
        val partyId = partyManager.informationHolder(memberId).partyId ?: run {

            try {
                val player = playerApi.getOnlinePlayer(memberId)
                player.sendMessage(miniMessage("<red>You aren't in a party!"))
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.FAILED_PRECONDITION.withDescription("Failed to retrieve party: User $memberId isn't part of any party")
                .asRuntimeException()
        }

        val party = partyManager.parties[partyId] ?: run {
            try {
                val player = playerApi.getOnlinePlayer(memberId)
                player.sendMessage(miniMessage("<red>Failed to fetch your current party. Please call an administrator about this."))
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.NOT_FOUND.withDescription("Failed to retrieve party: Party $partyId not found")
                .asRuntimeException()
        }

        return party
    }
}