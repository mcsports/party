package club.mcsports.droplet.party.service

import app.simplecloud.droplet.player.api.PlayerApi
import club.mcsports.droplet.party.PartyManager
import club.mcsports.droplet.party.extension.asUuid
import club.mcsports.droplet.party.extension.miniMessage
import com.mcsports.party.v1.*
import io.grpc.Status
import io.grpc.StatusException
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
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

        val partyMember = party.retrieveMember(executor)
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

        val partyMember = party.retrieveMember(executor)
        if (!party.settings.allowChatting && partyMember.role != PartyRole.OWNER) {
            try {
                val player = playerApi.getOnlinePlayer(executor)
                player.sendMessage(miniMessage("<red>Sorry, but chatting is currently disabled in your party."))
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.UNAVAILABLE.withDescription("Failed to send party chat message: Chatting is disabled in party $partyId")
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
            .hoverEvent(
                HoverEvent.hoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    miniMessage("Party-Role: ${partyMember.role}")
                )
            )

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

    override suspend fun invitePlayer(request: InvitePlayerRequest): InvitePlayerResponse {
        val executor = request.executorId.asUuid()
        val party = retrieveParty(executor)
        val executingPartyMember = party.retrieveMember(executor)
        val partyId = party.id

        if (executingPartyMember.role == PartyRole.MEMBER) {
            try {
                val player = playerApi.getOnlinePlayer(executor)
                player.sendMessage(miniMessage("<red>You don't have enough permissions to invite members to the party."))
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.PERMISSION_DENIED.withDescription("Failed to invite member: User $executor isn't permitted to do that").asRuntimeException()
        }

        if(!party.settings.allowInvites && executingPartyMember.role != PartyRole.OWNER) {
            try {
                val player = playerApi.getOnlinePlayer(executor)
                player.sendMessage(miniMessage("<red>Sorry, but invites are currently disabled in your party."))
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.UNAVAILABLE.withDescription("Failed to invite member: Invites are disabled in party $partyId").asRuntimeException()
        }

        val invitedMemberId = request.memberId
        if(invitedMemberId == executor.toString()) {
            try {
                val player = playerApi.getOnlinePlayer(executor)
                player.sendMessage(miniMessage("<red>You cannot invite yourself to the party."))
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.INVALID_ARGUMENT.withDescription("Failed to invite member: User $executor cannot invite himself").asRuntimeException()
        }

        if(!playerApi.isOnline(invitedMemberId.asUuid())) {
            try {
                val player = playerApi.getOnlinePlayer(executor)
                player.sendMessage(miniMessage("<red>The player you're trying to invite is offline."))
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.NOT_FOUND.withDescription("Failed to invite member: User $invitedMemberId is offline").asRuntimeException()
        }

        if(party.invitesList.any { invite -> invite.id ==  invitedMemberId}) {
            try {
                val player = playerApi.getOnlinePlayer(executor)
                player.sendMessage(miniMessage("<red>The player you're trying to invite already has a pending invite for your party."))
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.ALREADY_EXISTS.withDescription("Failed to invite member: User $invitedMemberId already has a pending invite for party $partyId").asRuntimeException()
        }

        try {
            val executorPlayer = playerApi.getOnlinePlayer(executor)
            val executorName = executorPlayer.getName()
            partyManager.inviteMemberToParty(invitedMemberId.asUuid(), executorPlayer.getName(), executor, party)

            val invitedPlayer = playerApi.getOnlinePlayer(invitedMemberId.asUuid())
            val partyOwnerName = playerApi.getOnlinePlayer(party.ownerId.asUuid()).getName()

            executorPlayer.sendMessage(miniMessage("<gray>You successfully invited ${invitedPlayer.getName()}"))
            invitedPlayer.sendMessage(miniMessage("<gray>You got invited to $partyOwnerName's party!").append(Component.newline()).append(
                miniMessage("<green><hover:show_text:'Click to accept the invite'><click:run_command:'/party accept $executorName'>Accept</click></hover> <dark_gray>| <red><hover:show_text:'Click here to deny the invite'><click:run_command:'/party deny $executorName'>Deny</click></hover>")
            ))
        } catch (exception: StatusException) {
            exception.printStackTrace()
        }

        return invitePlayerResponse { }
    }

    override suspend fun kickMember(request: KickMemberRequest): KickMemberResponse {
        val executor = request.executorId.asUuid()
        val party = retrieveParty(executor)
        val partyId = party.id
        val executingPartyMember = party.retrieveMember(executor)

        val member = request.memberId.asUuid()
        if(executor == member) {
            leaveParty(leavePartyRequest {
                this.memberId = memberId
            })
            return kickMemberResponse {  }
        }

        if(executingPartyMember.role == PartyRole.MEMBER) {
            try {
                val executingPlayer = playerApi.getOnlinePlayer(executor)
                executingPlayer.sendMessage(miniMessage("<red>You don't have enough permissions to do kick members."))
            } catch(exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.PERMISSION_DENIED.withDescription("Failed to kick member: User $executor isn't permitted to do that").asRuntimeException()
        }

        if(party.membersList.none { it.id == member.toString() }) {
            try {
                val executingPlayer = playerApi.getOnlinePlayer(executor)
                executingPlayer.sendMessage(miniMessage("<red>The given player isn't part of your party."))
            } catch(exception: StatusException) {
                exception.printStackTrace()
            }
            throw Status.NOT_FOUND.withDescription("Failed to kick member: User $member isn't part of party $partyId").asRuntimeException()
        }

        val partyMember = party.retrieveMember(member)

        if(executingPartyMember.roleValue < partyMember.roleValue) {
            try {
                val executingPlayer = playerApi.getOnlinePlayer(executor)
                val memberPlayer = playerApi.getOnlinePlayer(member)
                executingPlayer.sendMessage(miniMessage("<red>You don't have enough permissions to do kick ${memberPlayer.getName()}."))
            } catch(exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.PERMISSION_DENIED.withDescription("Failed to kick member: User $executor isn't permitted to kick higher role member $member").asRuntimeException()
        }

        partyManager.removeMemberFromParty(member, party)
        try {
            val executingPlayer = playerApi.getOnlinePlayer(executor)
            val memberPlayer = playerApi.getOnlinePlayer(member)
            executingPlayer.sendMessage(miniMessage("<gray>You successfully removed ${memberPlayer.getName()} from the party."))
            memberPlayer.sendMessage(miniMessage("<gray>You got kicked out of the party."))
        } catch(exception: StatusException) {
            exception.printStackTrace()
        }

        return kickMemberResponse { }
    }

    override suspend fun leaveParty(request: LeavePartyRequest): LeavePartyResponse {
        val member = request.memberId.asUuid()
        val party = retrieveParty(member)
        val partyMember = party.retrieveMember(member)

        val removeMemberResult = partyManager.removeMemberFromParty(member, party) ?: run {
            try {
                val executingPlayer = playerApi.getOnlinePlayer(member)
                executingPlayer.sendMessage(miniMessage("<gray>By leaving your party, it automatically got deleted."))
            } catch(exception: StatusException) {
                exception.printStackTrace()
            }

            return leavePartyResponse { }
        }

        if(partyMember.role == PartyRole.OWNER) {
            try {
                val newOwner = removeMemberResult.id.asUuid()

                val overwrittenParty = party.copy {
                    this.ownerId = newOwner.toString()
                }
                partyManager.parties[party.id.asUuid()] = overwrittenParty

                val newOwnerPlayer = playerApi.getOnlinePlayer(newOwner)
                partyManager.transferOwnership(newOwner, true, party)
                newOwnerPlayer.sendMessage(miniMessage("<gray>You were automatically promoted to party owner due to being in the party the longest."))
            } catch(exception: StatusException) {
                exception.printStackTrace()
            }
        }

        try {
            val executingPlayer = playerApi.getOnlinePlayer(member)
            executingPlayer.sendMessage(miniMessage("<gray>You left the party."))
        } catch(exception: StatusException) {
            exception.printStackTrace()
        }

        return leavePartyResponse { }
    }

    private val promoteConfirmation = mutableListOf<UUID>()
    override suspend fun promoteMember(request: PromoteMemberRequest): PromoteMemberResponse {
        val executor = request.executorId.asUuid()
        val party = retrieveParty(executor)
        val executingPartyMember = party.retrieveMember(executor)

        if(executingPartyMember.role != PartyRole.OWNER) {
            try {
                val executingPlayer = playerApi.getOnlinePlayer(executor)
                executingPlayer.sendMessage(miniMessage("<red>You don't have enough permissions to promote members."))
            } catch(exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.PERMISSION_DENIED.withDescription("Failed to promote member: User $executor isn't permitted to do that.").asRuntimeException()
        }

        val member = request.memberId.asUuid()
        if(member == executor) {
            try {
                val executingPlayer = playerApi.getOnlinePlayer(executor)
                executingPlayer.sendMessage(miniMessage("<red>You can't promote yourself."))
            } catch(exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.INVALID_ARGUMENT.withDescription("Failed to promote member: User $executor cannot promote himself").asRuntimeException()
        }

        val partyMember = party.retrieveMember(member)

        if(partyMember.roleValue == (PartyRole.OWNER_VALUE - 1)) {
            if(!promoteConfirmation.contains(executor)) {
                promoteConfirmation.add(executor)

                try {
                    val executingPlayer = playerApi.getOnlinePlayer(executor)
                    val memberPlayer = playerApi.getOnlinePlayer(member)
                    executingPlayer.sendMessage(miniMessage("<red>You're about to transfer the ownership of the party to ${memberPlayer.getName()}. Please try again to confirm your action!"))
                } catch(exception: StatusException) {
                    exception.printStackTrace()
                }

                throw Status.FAILED_PRECONDITION.withDescription("Failed to promote member: User $executor has to confirm the action first").asRuntimeException()
            }

            promoteConfirmation.remove(executor)
            partyManager.transferOwnership(member, false, party)
        }

        val nextHigherRole = PartyRole.forNumber(partyMember.roleValue + 1) ?: run {
            try {
                val executingPlayer = playerApi.getOnlinePlayer(executor)
                val memberPlayer = playerApi.getOnlinePlayer(member)
                executingPlayer.sendMessage(miniMessage("<red>${memberPlayer.getName()} already has the highest party role."))
            } catch(exception: StatusException) {
                exception.printStackTrace()
            }
            throw Status.INVALID_ARGUMENT.withDescription("Failed to promote member: User $member already has highest role (${partyMember.roleValue})").asRuntimeException()
        }

        partyManager.setMemberRole(member, nextHigherRole, party)

        try {
            val executingPlayer = playerApi.getOnlinePlayer(executor)
            val memberPlayer = playerApi.getOnlinePlayer(member)
            executingPlayer.sendMessage(miniMessage("<gray>You successfully promoted $memberPlayer to $nextHigherRole"))
            memberPlayer.sendMessage(miniMessage("<gray>Your party role was updated to $nextHigherRole."))
        } catch(exception: StatusException) {
            exception.printStackTrace()
        }
        return promoteMemberResponse { }
    }

    override suspend fun demoteMember(request: DemoteMemberRequest): DemoteMemberResponse {
        val executor = request.executorId.asUuid()
        val party = retrieveParty(executor)
        val executingPartyMember = party.retrieveMember(executor)

        if(executingPartyMember.role != PartyRole.OWNER) {
            try {
                val executingPlayer = playerApi.getOnlinePlayer(executor)
                executingPlayer.sendMessage(miniMessage("<red>You don't have enough permissions to demote members."))
            } catch(exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.PERMISSION_DENIED.withDescription("Failed to demote member: User $executor isn't permitted to do that.").asRuntimeException()
        }

        val member = request.memberId.asUuid()
        if(member == executor) {
            try {
                val executingPlayer = playerApi.getOnlinePlayer(executor)
                executingPlayer.sendMessage(miniMessage("<red>You can't demote yourself."))
            } catch(exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.INVALID_ARGUMENT.withDescription("Failed to demote member: User $executor cannot demote himself").asRuntimeException()
        }

        val partyMember = party.retrieveMember(member)

        if(partyMember.roleValue > executingPartyMember.roleValue) {
            try {
                val executingPlayer = playerApi.getOnlinePlayer(executor)
                val memberPlayer = playerApi.getOnlinePlayer(member)
                executingPlayer.sendMessage(miniMessage("<red>${memberPlayer.getName()} has a higher role than you, therefore you can't demote them."))
            } catch(exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.PERMISSION_DENIED.withDescription("Failed to demote member: User $executor has a weaker role than $member").asRuntimeException()
        }

        val nextLowerRole = PartyRole.forNumber(partyMember.roleValue - 1) ?: run {
            try {
                val executingPlayer = playerApi.getOnlinePlayer(executor)
                val memberPlayer = playerApi.getOnlinePlayer(member)
                executingPlayer.sendMessage(miniMessage("<red>${memberPlayer.getName()} already has the lowest party role."))
            } catch(exception: StatusException) {
                exception.printStackTrace()
            }
            throw Status.INVALID_ARGUMENT.withDescription("Failed to demote member: User $member already has lowest role (${partyMember.roleValue})").asRuntimeException()
        }

        partyManager.setMemberRole(member, nextLowerRole, party)

        try {
            val executingPlayer = playerApi.getOnlinePlayer(executor)
            val memberPlayer = playerApi.getOnlinePlayer(member)
            executingPlayer.sendMessage(miniMessage("<gray>You successfully promoted $memberPlayer to $nextLowerRole"))
            memberPlayer.sendMessage(miniMessage("<gray>Your party role was updated to $nextLowerRole."))
        } catch(exception: StatusException) {
            exception.printStackTrace()
        }
        return demoteMemberResponse { }
    }

    /**
     * Just some empty responses for now
     */
    override suspend fun handleInvite(request: HandleInviteRequest): HandleInviteResponse = handleInviteResponse { }

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

    private suspend fun Party.retrieveMember(memberId: UUID): PartyMember {
        val partyMember = membersList.firstOrNull { it.id == memberId.toString() } ?: run {
            try {
                val player = playerApi.getOnlinePlayer(memberId)
                player.sendMessage(miniMessage("<red>Failed to fetch your current party. Please call an administrator about this."))
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.DATA_LOSS.withDescription("Failed to retrieve party: User $memberId isn't part of party $id anymore")
                .asRuntimeException()
        }

        return partyMember
    }
}