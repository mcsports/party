package club.mcsports.droplet.party.service

import app.simplecloud.droplet.api.time.ProtobufTimestamp
import app.simplecloud.droplet.player.api.PlayerApi
import app.simplecloud.plugin.api.shared.extension.text
import club.mcsports.droplet.party.PartyManager
import club.mcsports.droplet.party.extension.asUuid
import club.mcsports.droplet.party.extension.fetchPlayer
import club.mcsports.droplet.party.extension.log
import club.mcsports.droplet.party.shared.Color
import club.mcsports.droplet.party.shared.Glyphs
import com.mcsports.party.v1.*
import io.grpc.Status
import io.grpc.StatusException
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.apache.logging.log4j.LogManager
import java.time.LocalDateTime
import java.util.*

class PartyInteractionService(
    private val partyManager: PartyManager,
    private val playerApi: PlayerApi.Coroutine,
) : PartyInteractionGrpcKt.PartyInteractionCoroutineImplBase() {
    private val logger = LogManager.getLogger(PartyInteractionService::class.java)

    override suspend fun createParty(request: CreatePartyRequest): CreatePartyResponse {
        val creator = request.creatorId.fetchPlayer() ?: handleUserFetchingFailed(request.creatorId)
        val creatorName = creator.getName()

        if (partyManager.informationHolder(creatorName).partyId != null) {
            creator.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You cannot create a party as long as you're already part of one."))
            throw Status.FAILED_PRECONDITION.withDescription("Failed to create party for user $creatorName: User is already part of a party")
                .log(logger)
                .asRuntimeException()
        }

        val partyId = partyManager.generatePartyId()
        val party = party {
            this.id = partyId.toString()
            this.ownerId = creator.getUniqueId().toString()
            this.settings = request.settings

            this.members.add(
                partyMember {
                    this.name = creatorName
                    this.uuid = creator.getUniqueId().toString()
                    this.role = PartyRole.OWNER
                    this.timeJoined = ProtobufTimestamp.fromLocalDateTime(LocalDateTime.now())
                }
            )
        }

        partyManager.parties[partyId] = party
        partyManager.informationHolder(creatorName).partyId = partyId

        val offlinePlayers = mutableListOf<String>()

        offlinePlayers.addAll(request.invitedNamesList.mapNotNull { invitedName ->
            val inviteResult = party.inviteMember(invitedName, creatorName)

            return@mapNotNull if (inviteResult.code != Status.Code.OK) invitedName
            else null
        })

        creator.sendMessage(text("${Glyphs.BALLOONS} <white>Your party was created ${Color.GREEN}successfully</color>."))
        if (offlinePlayers.isNotEmpty()) creator.sendMessage(
            text(
                "${Glyphs.SPACE + Color.RED} The following players haven't been invited: ${
                    offlinePlayers.joinToString(
                        ", "
                    )
                }"
            )
        )

        return createPartyResponse {
            this.createdParty = party
            this.offlineNames.addAll(offlinePlayers)
        }
    }

    override suspend fun deleteParty(request: DeletePartyRequest): DeletePartyResponse {
        val executor = request.executorId.fetchPlayer() ?: handleUserFetchingFailed(request.executorId)
        val executorName = executor.getName()

        val party = retrieveParty(executorName)
        val partyId = party.id.asUuid()

        val partyMember = party.retrieveMember(executorName) ?: run {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} Failed to fetch your current party. Please call an administrator about this."))
            handleRetrieveMemberFailed(partyId.toString(), executorName)
        }

        if (partyMember.role != PartyRole.OWNER) {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You don't have enough permissions to delete the party."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to delete party: User $executorName isn't the party owner")
                .log(logger).asRuntimeException()
        }

        party.membersList.toList().forEach { loopMember ->
            val name = loopMember.name
            partyManager.informationHolder(name).partyId = null

            val loopPlayer = name.fetchPlayer()
            loopPlayer?.sendMessage(text("${Glyphs.BALLOONS + Color.RED} The party you were in got deleted."))
        }

        party.membersList.clear()
        partyManager.parties.remove(partyId)
        return deletePartyResponse { }
    }

    private val gsonSerializer = GsonComponentSerializer.gson()
    override suspend fun chat(request: ChatRequest): ChatResponse {
        val executor = request.executorId.fetchPlayer() ?: handleUserFetchingFailed(request.executorId)
        val executorName = executor.getName()

        val party = retrieveParty(executorName)
        val partyId = party.id

        val partyMember = party.retrieveMember(executorName) ?: run {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} Failed to fetch your current party. Please call an administrator about this."))
            handleRetrieveMemberFailed(partyId, executorName)
        }

        if (!party.settings.allowChatting && partyMember.role != PartyRole.OWNER) {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} Sorry, but chatting is currently disabled in your party."))

            throw Status.UNAVAILABLE.withDescription("Failed to send party chat message: Chatting is disabled in party $partyId")
                .log(logger).asRuntimeException()
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
        }.append(text(executorName))
            .hoverEvent(
                HoverEvent.hoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    text("<gray>Party-Role: ${Color.BLUE}${partyMember.role}")
                )
            ).append(Component.empty().color(TextColor.color(0xFFFFFF)))

        val messageComponent = gsonSerializer.deserialize(request.message.json).color(TextColor.color(0xAAAAAA))

        party.membersList.map { it.name }.forEach { name ->
            val loopPlayer = name.fetchPlayer()

            loopPlayer?.sendMessage(
                text("${Glyphs.BALLOONS} Party-Chat").append(Component.space()).append(senderInformationComponent.append(text("<dark_gray>: ")).append(messageComponent)))
        }

        return chatResponse { }
    }

    override suspend fun invitePlayer(request: InvitePlayerRequest): InvitePlayerResponse {
        val executorId = request.executorId
        val executor = executorId.fetchPlayer() ?: handleUserFetchingFailed(executorId)
        val executorName = executor.getName()

        if (partyManager.informationHolder(executorName).partyId == null) {
            createParty(
                createPartyRequest {
                    this.settings = partySettings {
                        this.allowInvites = true
                        this.isPrivate = true
                        this.allowChatting = true
                    }
                    this.creatorId = executorId
                    if (!request.memberName.equals(executorName, true)) this.invitedNames.add(request.memberName)
                    else executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You cannot invite yourself to a party, but we've created an empty one for you."))
                }
            )

            return invitePlayerResponse { }
        }

        val party = retrieveParty(executorName)
        val partyId = party.id

        val executingPartyMember = party.retrieveMember(executorName) ?: run {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} Failed to fetch your current party. Please call an administrator about this."))
            handleRetrieveMemberFailed(partyId, executorName)
        }

        if (executingPartyMember.role == PartyRole.MEMBER) {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You don't have enough permissions to invite members to the party."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to invite member: User $executorName isn't permitted to do that")
                .log(logger).asRuntimeException()
        }

        if (!party.settings.allowInvites && executingPartyMember.role != PartyRole.OWNER) {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} Sorry, but invites are currently disabled in your party."))

            throw Status.UNAVAILABLE.withDescription("Failed to invite member: Invites are disabled in party $partyId")
                .log(logger).asRuntimeException()
        }

        val invitedMemberName = request.memberName
        if (invitedMemberName.equals(executorName, true)) {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You cannot invite yourself to the party."))

            throw Status.INVALID_ARGUMENT.withDescription("Failed to invite member: User $executorName cannot invite themself")
                .log(logger).asRuntimeException()
        }

        if(invitedMemberName.fetchPlayer() == null) {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $invitedMemberName is offline."))

            throw Status.NOT_FOUND.withDescription("Failed to invite member: User $invitedMemberName is offline")
                .log(logger).asRuntimeException()
        }

        if (party.invitesList.any { invite -> invite.invitedName == invitedMemberName }) {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $invitedMemberName already has a pending invite for your party."))

            throw Status.ALREADY_EXISTS.withDescription("Failed to invite member: User $invitedMemberName already has a pending invite for party $partyId")
                .log(logger).asRuntimeException()
        }

        party.inviteMember(invitedMemberName, executorName)
        executor.sendMessage(text("${Glyphs.BALLOONS} You ${Color.GREEN}successfully</color> invited $invitedMemberName"))

        return invitePlayerResponse { }
    }

    override suspend fun kickMember(request: KickMemberRequest): KickMemberResponse {
        val executor = request.executorId.fetchPlayer() ?: handleUserFetchingFailed(request.executorId)
        val executorName = executor.getName()

        val party = retrieveParty(executorName)
        val partyId = party.id
        val executingPartyMember = party.retrieveMember(executorName) ?: run {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} Failed to fetch your current party. Please call an administrator about this."))
            handleRetrieveMemberFailed(partyId, executorName)
        }

        val memberName = request.memberName
        if (executorName.equals(memberName, true)) {
            leaveParty(leavePartyRequest {
                this.executorId = request.executorId
            })

            return kickMemberResponse { }
        }

        if (executingPartyMember.role == PartyRole.MEMBER) {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You don't have enough permissions to kick members."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to kick member: User $executorName isn't permitted to do that")
                .log(logger).asRuntimeException()
        }

        if (party.membersList.none { it.name == memberName }) {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $memberName isn't part of your party."))

            throw Status.NOT_FOUND.withDescription("Failed to kick member: User $memberName isn't part of party $partyId")
                .log(logger).asRuntimeException()
        }

        val partyMember = party.retrieveMember(memberName) ?: run {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $memberName isn't part of your party."))
            handleRetrieveMemberFailed(partyId, memberName)
        }

        if (executingPartyMember.roleValue < partyMember.roleValue) {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You don't have enough permissions to kick $memberName."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to kick member: User $executorName isn't permitted to kick higher role member $memberName")
                .log(logger).asRuntimeException()
        }

        partyManager.removeMemberFromParty(memberName, party)
        val memberPlayer = memberName.fetchPlayer()

        executor.sendMessage(text("${Glyphs.BALLOONS} You ${Color.GREEN}successfully</color> removed $memberName from the party."))
        memberPlayer?.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You've got kicked out of the party."))

        return kickMemberResponse { }
    }

    override suspend fun leaveParty(request: LeavePartyRequest): LeavePartyResponse {
        val executor = request.executorId.fetchPlayer() ?: handleUserFetchingFailed(request.executorId)
        val executorName = executor.getName()

        val party = retrieveParty(executorName)
        val partyMember = party.retrieveMember(executorName) ?: run {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} Failed to fetch your current party. Please call an administrator about this."))
            handleRetrieveMemberFailed(party.id, executorName)
        }

        val removeMemberResult = partyManager.removeMemberFromParty(executorName, party) ?: run {
            executor.sendMessage(text("${Glyphs.BALLOONS} By leaving your own party, it automatically got ${Color.RED}deleted</color>."))

            return leavePartyResponse { }
        }

        if (partyMember.role == PartyRole.OWNER) {
            val newOwner = removeMemberResult.name
            val newOwnerPlayer = newOwner.fetchPlayer()
            partyManager.transferOwnership(newOwner, true, party)

            newOwnerPlayer?.sendMessage(text("${Glyphs.BALLOONS} The party owner ${Color.RED}left</color>. You were automatically promoted to party owner due to being in the party the longest."))
        }

        executor.sendMessage(text("${Glyphs.BALLOONS} You ${Color.RED}left</color> the party."))
        party.announce(text("${Glyphs.BALLOONS} $executorName ${Color.RED}left</color> the party."))
        return leavePartyResponse { }
    }

    private val promoteConfirmation = mutableListOf<UUID>()
    override suspend fun promoteMember(request: PromoteMemberRequest): PromoteMemberResponse {
        val executor = request.executorId.fetchPlayer() ?: handleUserFetchingFailed(request.executorId)
        val executorName = executor.getName()

        val party = retrieveParty(executorName)
        val executingPartyMember = party.retrieveMember(executorName) ?: run {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} Failed to fetch your current party. Please call an administrator about this."))
            handleRetrieveMemberFailed(party.id, executorName)
        }

        if (executingPartyMember.role != PartyRole.OWNER) {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You don't have enough permissions to promote members."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to promote member: User $executorName isn't permitted to do that.")
                .log(logger).asRuntimeException()
        }

        val memberName = request.memberName
        if (memberName.equals(executorName, true)) {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You can't promote yourself."))

            throw Status.INVALID_ARGUMENT.withDescription("Failed to demote member: User $executorName cannot promote themself")
                .log(logger).asRuntimeException()
        }

        val member = memberName.fetchPlayer()

        val partyMember = party.retrieveMember(memberName) ?: run {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $memberName isn't part of your party."))
            handleRetrieveMemberFailed(party.id, executorName)
        }

        if (partyMember.roleValue == (PartyRole.OWNER_VALUE - 1)) {
            if (!promoteConfirmation.contains(executor.getUniqueId())) {
                promoteConfirmation.add(executor.getUniqueId())
                executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You're about to transfer the ownership of the party to $memberName. Please try again to confirm your action!"))

                throw Status.FAILED_PRECONDITION.withDescription("Failed to promote member: User $executorName has to confirm the action first")
                    .log(logger).asRuntimeException()
            }

            partyManager.transferOwnership(memberName, false, party)
            promoteConfirmation.remove(executor.getUniqueId())
        }

        val nextHigherRole = PartyRole.forNumber(partyMember.roleValue + 1) ?: run {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $memberName already has the highest party role."))

            throw Status.INVALID_ARGUMENT.withDescription("Failed to promote member: User $memberName already has highest role (${partyMember.roleValue})")
                .log(logger).asRuntimeException()
        }

        if(nextHigherRole != PartyRole.OWNER) partyManager.setMemberRole(memberName, nextHigherRole, party)

        executor.sendMessage(text("${Glyphs.BALLOONS} You ${Color.GREEN}successfully</color> promoted $memberName to ${Color.BLUE + nextHigherRole}"))
        member?.sendMessage(text("${Glyphs.BALLOONS} Your party role was updated to ${Color.BLUE + nextHigherRole}."))
        return promoteMemberResponse { }
    }

    override suspend fun demoteMember(request: DemoteMemberRequest): DemoteMemberResponse {
        val executor = request.executorId.fetchPlayer() ?: handleUserFetchingFailed(request.executorId)
        val executorName = executor.getName()

        val party = retrieveParty(executorName)
        val executingPartyMember = party.retrieveMember(executorName) ?: run {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} Failed to fetch your current party. Please call an administrator about this."))
            handleRetrieveMemberFailed(party.id, executorName)
        }

        if (executingPartyMember.role != PartyRole.OWNER) {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You don't have enough permissions to demote members."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to demote member: User $executorName isn't permitted to do that.")
                .log(logger).asRuntimeException()
        }

        val memberName = request.memberName
        if (memberName.equals(executorName, true)) {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You can't demote yourself."))

            throw Status.INVALID_ARGUMENT.withDescription("Failed to demote member: User $executorName cannot demote themself")
                .log(logger).asRuntimeException()
        }

        val member = memberName.fetchPlayer()

        val partyMember = party.retrieveMember(memberName) ?: run {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $memberName isn't part of your party."))
            handleRetrieveMemberFailed(party.id, executorName)
        }

        if (partyMember.roleValue > executingPartyMember.roleValue) {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $memberName has a higher role than you, therefore you can't demote them."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to demote member: User $executor has a weaker role than $memberName")
                .log(logger).asRuntimeException()
        }

        val nextLowerRole = PartyRole.forNumber(partyMember.roleValue - 1) ?: run {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $memberName already has the lowest party role."))

            throw Status.INVALID_ARGUMENT.withDescription("Failed to demote member: User $memberName already has lowest role (${partyMember.roleValue})")
                .log(logger).asRuntimeException()
        }

        partyManager.setMemberRole(memberName, nextLowerRole, party)

        executor.sendMessage(text("${Glyphs.BALLOONS} You ${Color.GREEN}successfully</color> demoted $memberName to ${Color.BLUE + nextLowerRole}"))
        member?.sendMessage(text("${Glyphs.BALLOONS} Your party role was updated to ${Color.BLUE + nextLowerRole}."))
        return demoteMemberResponse { }
    }

    override suspend fun handleInvite(request: HandleInviteRequest): HandleInviteResponse {
        val executor = request.executorId.fetchPlayer() ?: handleUserFetchingFailed(request.executorId)
        val executorName = executor.getName()

        val invitorName = request.invitorName

        val informationHolder = partyManager.informationHolder(executorName)
        val invitesList = informationHolder.invites
        if (!invitesList.contains(invitorName)) {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You haven't been invited by $invitorName."))
            throw Status.NOT_FOUND.withDescription("Failed to handle invite: User $executorName wasn't invited by $invitorName")
                .log(logger).asRuntimeException()
        }

        val partyId = invitesList[invitorName]
        val party = partyManager.parties[partyId] ?: run {
            invitesList.remove(invitorName)

            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} Sorry, the corresponding party doesn't exist anymore."))
            throw Status.NOT_FOUND.withDescription("Failed to handle invite: Party $partyId wasn't found")
                .log(logger).asRuntimeException()
        }

        if (request.accepted) {
            if (informationHolder.partyId != null) {
                executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You're already part of a party."))

                throw Status.FAILED_PRECONDITION.withDescription("Failed to accept invite: User $executorName is already part of a party")
                    .log(logger).asRuntimeException()
            }

            party.announce(text("${Glyphs.BALLOONS} $executorName ${Color.GREEN}joined</color> the party!"))
            partyManager.assignMemberToParty(executorName, request.executorId.asUuid(), PartyRole.MEMBER, party)
            executor.sendMessage(text("${Glyphs.BALLOONS} You ${Color.GREEN}successfully</color> accepted $invitorName's party invite."))
            return handleInviteResponse { }
        }

        partyManager.deleteMemberInvite(executorName, party)
        executor.sendMessage(text("${Glyphs.BALLOONS} You ${Color.RED}denied $invitorName's party invite."))
        return handleInviteResponse { }
    }

    private suspend fun retrieveParty(memberName: String): Party {
        val player = memberName.fetchPlayer() ?: handleUserFetchingFailed(memberName)
        val partyId = partyManager.informationHolder(memberName).partyId ?: run {
            player.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You aren't in a party!"))

            throw Status.FAILED_PRECONDITION.withDescription("Failed to retrieve party: User $memberName isn't part of any party")
                .log(logger).asRuntimeException()
        }

        val party = partyManager.parties[partyId] ?: run {
            player.sendMessage(text("${Glyphs.BALLOONS + Color.RED} Failed to fetch your current party. Please call an administrator about this."))

            throw Status.NOT_FOUND.withDescription("Failed to retrieve party: Party $partyId not found")
                .log(logger).asRuntimeException()
        }

        return party
    }

    private fun Party.retrieveMember(memberName: String): PartyMember? {
        val partyMember = membersList.firstOrNull { it.name == memberName }
        return partyMember
    }

    private fun handleRetrieveMemberFailed(partyId: String, memberName: String): Nothing {
        throw Status.DATA_LOSS.withDescription("Failed to retrieve party: User $memberName isn't part of party $partyId")
            .log(logger).asRuntimeException()
    }

    private suspend fun Party.inviteMember(memberName: String, invitorName: String): Status {
        try {
            val invitedPlayer = playerApi.getOnlinePlayer(memberName)
            val partyOwnerName = membersList.firstOrNull() { it.role == PartyRole.OWNER }?.name ?: invitorName
            partyManager.inviteMemberToParty(memberName, invitorName, this)

            invitedPlayer.sendMessage(
                text("${Glyphs.BALLOONS} You got invited to $partyOwnerName's party!").appendNewline().append(
                    text("${Glyphs.SPACE + Color.GREEN} <hover:show_text:'<gray>Click to accept the invite'><click:run_command:'/party accept $invitorName'>Accept</click></hover></color> <dark_gray>| ${Color.RED}<hover:show_text:'<gray>Click here to deny the invite'><click:run_command:'/party deny $invitorName'>Deny</click></hover></color>")
                )
            )

            return Status.OK
        } catch (exception: StatusException) {
            return exception.status
        }
    }

    private suspend fun Party.announce(message: Component) {
        membersList.map { it.name }.forEach { name ->
            val loopPlayer = name.fetchPlayer()
            loopPlayer?.sendMessage(message)
        }
    }

    private fun handleUserFetchingFailed(string: String): Nothing {
        throw Status.NOT_FOUND.withDescription("Failed to fetch user data: No user to identify with $string").log(logger).asRuntimeException()
    }
}