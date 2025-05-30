package club.mcsports.droplet.party.service

import app.simplecloud.droplet.player.api.PlayerApi
import app.simplecloud.plugin.api.shared.extension.text
import club.mcsports.droplet.party.PartyManager
import club.mcsports.droplet.party.extension.asUuid
import club.mcsports.droplet.party.extension.fetchPlayer
import com.mcsports.party.v1.*
import io.grpc.Status
import io.grpc.StatusException
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import java.util.UUID

class PartyInteractionService(
    private val partyManager: PartyManager,
    private val playerApi: PlayerApi.Coroutine
) : PartyInteractionGrpcKt.PartyInteractionCoroutineImplBase() {

    override suspend fun createParty(request: CreatePartyRequest): CreatePartyResponse {
        val creator = request.creatorId.fetchPlayer()
        val creatorName = creator.getName()

        if (partyManager.informationHolder(creatorName).partyId != null) {
            creator.sendMessage(text("<red>You cannot create a party as long as you're already part of one."))

            throw Status.FAILED_PRECONDITION.withDescription("Failed to create party for user $creatorName: User is already part of a party")
                .asRuntimeException()
        }

        val partyId = partyManager.generatePartyId()

        val party = party {
            this.id = partyId.toString()
            this.ownerId = creator.toString()
            this.settings = request.settings
        }

        partyManager.parties[partyId] = party
        partyManager.assignMemberToParty(creatorName, PartyRole.OWNER, party)

        val offlinePlayers = mutableListOf<String>()

        offlinePlayers.addAll(request.invitedNamesList.mapNotNull { invitedName ->
            val inviteResult = party.inviteMember(invitedName, creatorName)

            if (inviteResult == Status.NOT_FOUND) playerApi.getOfflinePlayer(invitedName).getName()
            else null
        })

        creator.sendMessage(text("<gray>You've successfully created your own party."))


        return createPartyResponse {
            this.createdParty = party
            this.offlineNames.addAll(offlinePlayers)
        }
    }

    override suspend fun deleteParty(request: DeletePartyRequest): DeletePartyResponse {
        val executor = request.executorId.fetchPlayer()
        val executorName = executor.getName()

        val party = retrieveParty(executorName)
        val partyId = party.id.asUuid()

        val partyMember = party.retrieveMember(executorName)
        if (partyMember.role != PartyRole.OWNER) {
            executor.sendMessage(text("<red>You don't have enough permissions to do delete the party."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to delete party: User $executorName isn't the party owner")
                .asRuntimeException()
        }

        party.membersList.toList().forEach { loopMember ->
            val name = loopMember.name
            partyManager.informationHolder(name).partyId = null

            val loopPlayer = name.fetchPlayer()
            loopPlayer.sendMessage(text("<red>The party you were in got deleted."))
        }

        party.membersList.clear()
        partyManager.parties.remove(partyId)
        return deletePartyResponse { }
    }

    private val gsonSerializer = GsonComponentSerializer.gson()
    override suspend fun chat(request: ChatRequest): ChatResponse {
        val executor = request.executorId.fetchPlayer()
        val executorName = executor.getName()

        val party = retrieveParty(executorName)
        val partyId = party.id

        val partyMember = party.retrieveMember(executorName)
        if (!party.settings.allowChatting && partyMember.role != PartyRole.OWNER) {
            executor.sendMessage(text("<red>Sorry, but chatting is currently disabled in your party."))

            throw Status.UNAVAILABLE.withDescription("Failed to send party chat message: Chatting is disabled in party $partyId")
                .asRuntimeException()
        }

        val senderInformationComponent = when (partyMember.role) {
            PartyRole.OWNER -> {
                text("<color:dark_red>")
            }

            PartyRole.MOD -> {
                text("<color:red>")
            }

            else -> {
                text("<color:gray>")
            }
        }.append(text("$executorName</color>"))
            .hoverEvent(
                HoverEvent.hoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    text("Party-Role: ${partyMember.role}")
                )
            )

        val messageComponent = gsonSerializer.deserialize(request.message.json)
        party.membersList.map { it.name }.forEach { name ->
            val loopPlayer = name.fetchPlayer()

            loopPlayer.sendMessage(
                text("<gray>Party-Chat:</gray> ").append(senderInformationComponent)
                    .append(text("<dark_gray>: <white>")).append(messageComponent)
            )
        }

        return chatResponse { }
    }

    suspend fun inviteMember(request: InvitePlayerRequest): InvitePlayerResponse {
        val executor = request.executorId.fetchPlayer()
        val executorName = executor.getName()

        val party = retrieveParty(executorName)
        val executingPartyMember = party.retrieveMember(executorName)
        val partyId = party.id

        if (executingPartyMember.role == PartyRole.MEMBER) {
            executor.sendMessage(text("<red>You don't have enough permissions to invite members to the party."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to invite member: User $executorName isn't permitted to do that")
                .asRuntimeException()
        }

        if (!party.settings.allowInvites && executingPartyMember.role != PartyRole.OWNER) {
            executor.sendMessage(text("<red>Sorry, but invites are currently disabled in your party."))

            throw Status.UNAVAILABLE.withDescription("Failed to invite member: Invites are disabled in party $partyId")
                .asRuntimeException()
        }

        val invitedMemberName = request.memberName
        if (invitedMemberName.equals(executorName, true)) {
            executor.sendMessage(text("<red>You cannot invite yourself to the party."))

            throw Status.INVALID_ARGUMENT.withDescription("Failed to invite member: User $executorName cannot invite themself")
                .asRuntimeException()
        }

        val targetPlayer = playerApi.getOfflinePlayer(invitedMemberName)
        if (!targetPlayer.isOnline()) {
            executor.sendMessage(text("<red>The player you're trying to invite is offline."))

            throw Status.NOT_FOUND.withDescription("Failed to invite member: User $invitedMemberName is offline")
                .asRuntimeException()
        }

        if (party.invitesList.any { invite -> invite.invitedName == invitedMemberName }) {
            executor.sendMessage(text("<red>The player you're trying to invite already has a pending invite for your party."))

            throw Status.ALREADY_EXISTS.withDescription("Failed to invite member: User $invitedMemberName already has a pending invite for party $partyId")
                .asRuntimeException()
        }

        party.inviteMember(invitedMemberName, executorName)
        executor.sendMessage(text("<gray>You successfully invited $invitedMemberName"))

        return invitePlayerResponse { }
    }

    override suspend fun kickMember(request: KickMemberRequest): KickMemberResponse {
        val executor = request.executorId.fetchPlayer()
        val executorName = executor.getName()

        val party = retrieveParty(executorName)
        val partyId = party.id
        val executingPartyMember = party.retrieveMember(executorName)

        val memberName = request.memberName
        if (executorName.equals(memberName, true)) {
            leaveParty(leavePartyRequest {
                this.executorId = request.executorId
            })

            return kickMemberResponse { }
        }

        if (executingPartyMember.role == PartyRole.MEMBER) {
            executor.sendMessage(text("<red>You don't have enough permissions to do kick members."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to kick member: User $executorName isn't permitted to do that")
                .asRuntimeException()
        }

        if (party.membersList.none { it.name == memberName }) {
            executor.sendMessage(text("<red>The given player isn't part of your party."))

            throw Status.NOT_FOUND.withDescription("Failed to kick member: User $memberName isn't part of party $partyId")
                .asRuntimeException()
        }

        val partyMember = party.retrieveMember(memberName)

        if (executingPartyMember.roleValue < partyMember.roleValue) {
            executor.sendMessage(text("<red>You don't have enough permissions to do kick $memberName."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to kick member: User $executorName isn't permitted to kick higher role member $memberName")
                .asRuntimeException()
        }

        partyManager.removeMemberFromParty(memberName, party)
        val memberPlayer = memberName.fetchPlayer()

        executor.sendMessage(text("<gray>You successfully removed ${memberPlayer.getName()} from the party."))
        memberPlayer.sendMessage(text("<gray>You got kicked out of the party."))

        return kickMemberResponse { }
    }

    override suspend fun leaveParty(request: LeavePartyRequest): LeavePartyResponse {
        val executor = request.executorId.fetchPlayer()
        val executorName = executor.getName()

        val party = retrieveParty(executorName)
        val partyMember = party.retrieveMember(executorName)

        val removeMemberResult = partyManager.removeMemberFromParty(executorName, party) ?: run {
            executor.sendMessage(text("<gray>By leaving your own party, it automatically got deleted."))

            return leavePartyResponse { }
        }

        if (partyMember.role == PartyRole.OWNER) {
            val newOwner = removeMemberResult.name
            val newOwnerPlayer = newOwner.fetchPlayer()

            val overwrittenParty = party.copy {
                this.ownerId = newOwnerPlayer.getUniqueId().toString()
            }

            partyManager.parties[party.id.asUuid()] = overwrittenParty
            partyManager.transferOwnership(newOwner, true, party)

            newOwnerPlayer.sendMessage(text("<gray>You were automatically promoted to party owner due to being in the party the longest."))
        }

        executor.sendMessage(text("<gray>You left the party."))

        return leavePartyResponse { }
    }

    private val promoteConfirmation = mutableListOf<UUID>()
    override suspend fun promoteMember(request: PromoteMemberRequest): PromoteMemberResponse {
        val executor = request.executorId.fetchPlayer()
        val executorName = executor.getName()

        val party = retrieveParty(executorName)
        val executingPartyMember = party.retrieveMember(executorName)

        if (executingPartyMember.role != PartyRole.OWNER) {
            executor.sendMessage(text("<red>You don't have enough permissions to promote members."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to promote member: User $executorName isn't permitted to do that.")
                .asRuntimeException()
        }

        val memberName = request.memberName
        val member = memberName.fetchPlayer()

        if (memberName.equals(executorName, true)) {
            executor.sendMessage(text("<red>You can't promote yourself."))

            throw Status.INVALID_ARGUMENT.withDescription("Failed to promote member: User $executorName cannot promote themself")
                .asRuntimeException()
        }

        val partyMember = party.retrieveMember(memberName)

        if (partyMember.roleValue == (PartyRole.OWNER_VALUE - 1)) {
            if (!promoteConfirmation.contains(executor.getUniqueId())) {
                promoteConfirmation.add(executor.getUniqueId())
                executor.sendMessage(text("<red>You're about to transfer the ownership of the party to $memberName. Please try again to confirm your action!"))

                throw Status.FAILED_PRECONDITION.withDescription("Failed to promote member: User $executorName has to confirm the action first")
                    .asRuntimeException()
            }

            promoteConfirmation.remove(executor.getUniqueId())
            partyManager.transferOwnership(memberName, false, party)
        }

        val nextHigherRole = PartyRole.forNumber(partyMember.roleValue + 1) ?: run {
            executor.sendMessage(text("<red>$memberName already has the highest party role."))

            throw Status.INVALID_ARGUMENT.withDescription("Failed to promote member: User $memberName already has highest role (${partyMember.roleValue})")
                .asRuntimeException()
        }

        partyManager.setMemberRole(memberName, nextHigherRole, party)

        executor.sendMessage(text("<gray>You successfully promoted $memberName to $nextHigherRole"))
        member.sendMessage(text("<gray>Your party role was updated to $nextHigherRole."))
        return promoteMemberResponse { }
    }

    override suspend fun demoteMember(request: DemoteMemberRequest): DemoteMemberResponse {
        val executor = request.executorId.fetchPlayer()
        val executorName = executor.getName()

        val party = retrieveParty(executorName)
        val executingPartyMember = party.retrieveMember(executorName)

        if (executingPartyMember.role != PartyRole.OWNER) {
            executor.sendMessage(text("<red>You don't have enough permissions to demote members."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to demote member: User $executorName isn't permitted to do that.")
                .asRuntimeException()
        }

        val memberName = request.memberName
        val member = memberName.fetchPlayer()

        if (memberName.equals(executorName, true)) {
            executor.sendMessage(text("<red>You can't demote yourself."))

            throw Status.INVALID_ARGUMENT.withDescription("Failed to demote member: User $executorName cannot demote themself")
                .asRuntimeException()
        }

        val partyMember = party.retrieveMember(memberName)

        if (partyMember.roleValue > executingPartyMember.roleValue) {
            executor.sendMessage(text("<red>$memberName has a higher role than you, therefore you can't demote them."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to demote member: User $executor has a weaker role than $member")
                .asRuntimeException()
        }

        val nextLowerRole = PartyRole.forNumber(partyMember.roleValue - 1) ?: run {
            executor.sendMessage(text("<red>$memberName already has the lowest party role."))

            throw Status.INVALID_ARGUMENT.withDescription("Failed to demote member: User $memberName already has lowest role (${partyMember.roleValue})")
                .asRuntimeException()
        }

        partyManager.setMemberRole(memberName, nextLowerRole, party)

        executor.sendMessage(text("<gray>You successfully promoted $memberName to $nextLowerRole."))
        member.sendMessage(text("<gray>Your party role was updated to $nextLowerRole."))
        return demoteMemberResponse { }
    }

    /**
     * TODO: Implement invite handling
     */
    override suspend fun handleInvite(request: HandleInviteRequest): HandleInviteResponse {
        val executor = request.executorId.fetchPlayer()
        val executorName = executor.getName()

        val invitorName = request.invitorName

        val invitesList = partyManager.informationHolder(executorName).invites
        if(!invitesList.contains(invitorName)) {
            executor.sendMessage(text("<red>You haven't been invited by $invitorName."))
            throw Status.NOT_FOUND.withDescription("Failed to handle invite: User $executorName wasn't invited by $invitorName").asRuntimeException()
        }

        val partyId = invitesList[invitorName]
        val party = partyManager.parties[partyId] ?: run {
            invitesList.remove(invitorName)

            executor.sendMessage(text("<red>Sorry, the corresponding party doesn't exist anymore."))
            throw Status.NOT_FOUND.withDescription("Failed to handle invite: Party $partyId wasn't found").asRuntimeException()
        }

        if(request.accepted) {
            partyManager.assignMemberToParty(executorName, PartyRole.MEMBER, party)
            executor.sendMessage(text("<gray>You successfully accepted $invitorName's party invite."))
            return handleInviteResponse { }
        }

        party.invitesList.removeIf { it.invitedName.equals(executorName, true) }
        executor.sendMessage(text("<gray>You denied $invitorName's party invite."))
        return handleInviteResponse { }
    }

    private suspend fun retrieveParty(memberName: String): Party {
        val partyId = partyManager.informationHolder(memberName).partyId ?: run {

            try {
                val player = playerApi.getOnlinePlayer(memberName)
                player.sendMessage(text("<red>You aren't in a party!"))
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.FAILED_PRECONDITION.withDescription("Failed to retrieve party: User $memberName isn't part of any party")
                .asRuntimeException()
        }

        val party = partyManager.parties[partyId] ?: run {
            try {
                val player = playerApi.getOnlinePlayer(memberName)
                player.sendMessage(text("<red>Failed to fetch your current party. Please call an administrator about this."))
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.NOT_FOUND.withDescription("Failed to retrieve party: Party $partyId not found")
                .asRuntimeException()
        }

        return party
    }

    private suspend fun Party.retrieveMember(memberName: String): PartyMember {
        val partyMember = membersList.firstOrNull { it.name == memberName } ?: run {
            try {
                val player = playerApi.getOnlinePlayer(memberName)
                player.sendMessage(text("<red>Failed to fetch your current party. Please call an administrator about this."))
            } catch (exception: StatusException) {
                exception.printStackTrace()
            }

            throw Status.DATA_LOSS.withDescription("Failed to retrieve party: User $memberName isn't part of party $id anymore")
                .asRuntimeException()
        }

        return partyMember
    }

    private suspend fun Party.inviteMember(memberName: String, invitorName: String): Status {
        try {
            val invitedPlayer = playerApi.getOnlinePlayer(memberName)
            val partyOwnerName = playerApi.getOnlinePlayer(ownerId.asUuid()).getName()
            partyManager.inviteMemberToParty(memberName, invitorName, this)

            invitedPlayer.sendMessage(
                text("<gray>You got invited to $partyOwnerName's party!").append(Component.newline()).append(
                    text("<green><hover:show_text:'Click to accept the invite'><click:run_command:'/party accept $invitorName'>Accept</click></hover> <dark_gray>| <red><hover:show_text:'Click here to deny the invite'><click:run_command:'/party deny $invitorName'>Deny</click></hover>")
                )
            )

            return Status.OK
        } catch (exception: StatusException) {
            exception.printStackTrace()
            return exception.status
        }
    }
}