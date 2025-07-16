package club.mcsports.droplet.party.service

import app.simplecloud.droplet.player.api.CloudPlayer
import app.simplecloud.plugin.api.shared.extension.text
import club.mcsports.droplet.party.extension.asUuid
import club.mcsports.droplet.party.extension.fetchPlayer
import club.mcsports.droplet.party.extension.log
import club.mcsports.droplet.party.repository.InviteRepository
import club.mcsports.droplet.party.repository.PartyRepository
import club.mcsports.droplet.party.repository.PlayerRepository
import club.mcsports.droplet.party.shared.Color
import club.mcsports.droplet.party.shared.Glyphs
import club.mcsports.droplet.party.shared.extension.getMember
import com.mcsports.party.v1.*
import io.grpc.Status
import io.grpc.StatusException
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.apache.logging.log4j.LogManager
import java.time.Instant
import java.util.*

class PartyInteractionService(
    private val playerRepository: PlayerRepository,
    private val partyRepository: PartyRepository,
    private val inviteRepository: InviteRepository
) : PartyInteractionGrpcKt.PartyInteractionCoroutineImplBase() {
    private val logger = LogManager.getLogger(PartyInteractionService::class.java)

    override suspend fun createParty(request: CreatePartyRequest): CreatePartyResponse {
        val creatorId = request.creatorId.asUuid()
        val creatorPlayer = creatorId.fetchPlayer() ?: handleUserFetchingFailed(creatorId)
        val creatorName = creatorPlayer.getName()

        if (playerRepository.getParty(creatorName) != null) {
            creatorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You cannot create a party as long as you're already part of one."))
            throw Status.ALREADY_EXISTS.withDescription("Failed to create party: User $creatorId is already part of a party")
                .log(logger).asRuntimeException()
        }

        val party = partyRepository.createParty(creatorId, request.settings)
        playerRepository.assignParty(creatorName, creatorId, PartyRole.OWNER, party)
        creatorPlayer.sendMessage(text("${Glyphs.BALLOONS} <white>Your party was created ${Color.GREEN}successfully</color>."))

        val invitePlayers = inviteRepository.invitePlayers(request.invitedNamesList.toSet(), creatorName)
        val invitedPlayerNames =
            invitePlayers.first.filter { entry -> entry.value.status.code == Status.Code.OK }.map { it.key }
        val notInvitedPlayerNames =
            invitePlayers.first.map { it.key }.subtract(invitedPlayerNames)

        creatorPlayer.sendMessage(
            text(
                "${Glyphs.BALLOONS} You ${Color.GREEN}successfully</color> invited ${
                    Color.BLUE + invitedPlayerNames.joinToString(
                        "<white>,</white>"
                    )
                } to your party."
            )
        )
        if (notInvitedPlayerNames.isNotEmpty()) creatorPlayer.sendMessage(
            text(
                "${Glyphs.SPACE + Color.RED} The following players haven't been invited: ${
                    notInvitedPlayerNames.joinToString(
                        ", "
                    )
                }"
            )
        )

        return createPartyResponse {
            this.createdParty = party
            this.offlineNames.addAll(notInvitedPlayerNames)
        }
    }

    override suspend fun deleteParty(request: DeletePartyRequest): DeletePartyResponse {
        val executorId = request.executorId
        val executorPlayer = executorId.fetchPlayer() ?: handleUserFetchingFailed(executorId)
        val executorName = executorPlayer.getName()

        val executorPartyPlayer = playerRepository.getPlayer(executorName)
        val party = partyRepository.deleteParty(executorPartyPlayer.partyId ?: run {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You aren't in a party."))
            throw Status.NOT_FOUND.withDescription("Failed to delete party: User $executorName isn't part of any party")
                .asRuntimeException()
        }) ?: run {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} Failed to fetch your current party. Please call an administrator about this."))
            throw Status.DATA_LOSS.withDescription("Failed to delete party: Party ${executorPartyPlayer.partyId} doesn't exist")
                .asRuntimeException()
        }

        if (party.getMember(executorName)?.role != PartyRole.OWNER) {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You don't have enough permissions to delete the party."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to delete party: User $executorName isn't the party owner")
                .log(logger).asRuntimeException()
        }

        party.membersList.forEach { member ->
            val memberUuid = member.uuid.asUuid()
            val memberPlayer = memberUuid.fetchPlayer()

            playerRepository.getPlayer(member.name).partyId = null
            memberPlayer?.sendMessage(text("${Glyphs.BALLOONS} The party you were in got ${Color.RED}deleted</color>."))
        }

        partyRepository.deleteParty(party)
        return deletePartyResponse { }
    }

    private val gsonSerializer = GsonComponentSerializer.gson()
    override suspend fun chat(request: ChatRequest): ChatResponse {
        val executorId = request.executorId
        val executorPlayer = executorId.fetchPlayer() ?: handleUserFetchingFailed(request.executorId)
        val executorName = executorPlayer.getName()

        val party = playerRepository.getParty(executorName) ?: run {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You aren't in a party."))
            throw Status.NOT_FOUND.withDescription("Failed to chat: User $executorName isn't part of any party")
                .asRuntimeException()
        }

        val executorMember = party.getMember(executorName) ?: run {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You aren't in a party."))
            throw Status.NOT_FOUND.withDescription("Failed to chat: User $executorName isn't part of any party")
                .asRuntimeException()
        }

        if (!party.settings.allowChatting && executorMember.role != PartyRole.OWNER) {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} Sorry, but chatting is currently disabled in your party."))
            throw Status.PERMISSION_DENIED.withDescription("Failed to chat: Chatting is disabled in party ${party.id}")
                .log(logger).asRuntimeException()
        }

        val message = gsonSerializer.deserialize(request.message.json).color(NamedTextColor.GRAY)
        val memberBadgeColor = when (executorMember.role) {
            PartyRole.OWNER -> NamedTextColor.DARK_GRAY
            PartyRole.MOD -> NamedTextColor.RED
            else -> NamedTextColor.GRAY
        }

        val memberBadge = Component.text(executorName).color(memberBadgeColor)
        party.announce(
            text("<hover:show_text:'Party-Chat'>${Glyphs.BALLOONS}</hover> ").append(memberBadge).append(
                Component.text(" » ").color(
                    NamedTextColor.DARK_GRAY
                ).append(message)
            )
        )

        return chatResponse { }
    }

    override suspend fun invitePlayer(request: InvitePlayerRequest): InvitePlayerResponse {
        val executorId = request.executorId
        val executorPlayer = executorId.fetchPlayer() ?: handleUserFetchingFailed(executorId)
        val executorName = executorPlayer.getName()

        val memberName = request.memberName

        if (executorName.equals(memberName, true)) {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You cannot invite yourself to the party."))

            throw Status.INVALID_ARGUMENT.withDescription("Failed to invite member: User $executorName cannot invite themselves")
                .log(logger).asRuntimeException()
        }

        val executorPartyPlayer = playerRepository.getPlayer(executorName)

        if (executorPartyPlayer.partyId == null) {
            createParty(
                createPartyRequest {
                    this.settings = partySettings {
                        this.allowInvites = true
                        this.isPrivate = true
                        this.allowChatting = true
                    }
                    this.creatorId = executorId
                    if (!request.memberName.equals(executorName, true)) this.invitedNames.add(request.memberName)
                    else executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You cannot invite yourself to a party, but we've created an empty one for you."))
                }
            )

            return invitePlayerResponse { }
        }

        try {
            val invitePlayers = inviteRepository.invitePlayers(setOf(memberName), executorName)
            val invite = invitePlayers.first.values.first()

            when (invite.status.code) {
                Status.Code.ALREADY_EXISTS -> {
                    executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $memberName already has a pending invite for your party."))
                    throw invite.status.asRuntimeException()
                }

                Status.Code.OK -> {
                    val partyOwnerName =
                        invitePlayers.second.membersList.firstOrNull { partyMember -> partyMember.role == PartyRole.OWNER }?.name
                            ?: executorName
                    invite.cloudPlayer?.sendInvitationText(executorName, partyOwnerName)
                    executorPlayer.sendMessage(text("${Glyphs.BALLOONS} You ${Color.GREEN}successfully</color> invited ${Color.BLUE + memberName} to your party."))
                }

                Status.Code.NOT_FOUND -> {
                    executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $memberName is offline."))
                    throw invite.status.asRuntimeException()
                }

                else -> throw invite.status.asRuntimeException()
            }
        } catch (exception: StatusException) {
            when (exception.status.code) {
                Status.Code.NOT_FOUND -> executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You aren't in a party."))
                Status.Code.PERMISSION_DENIED -> executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You don't have enough permissions to invite members to the party."))
                Status.Code.UNAVAILABLE -> executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} Sorry, but invites are currently disabled in your party."))
                else -> throw exception
            }
        }

        return invitePlayerResponse { }
    }

    override suspend fun kickMember(request: KickMemberRequest): KickMemberResponse {
        val executorId = request.executorId
        val executorPlayer = executorId.fetchPlayer() ?: handleUserFetchingFailed(request.executorId)
        val executorName = executorPlayer.getName()

        val party = playerRepository.getParty(executorName) ?: run {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You aren't in a party."))
            throw Status.NOT_FOUND.withDescription("Failed to kick member: User $executorName isn't part of any party")
                .asRuntimeException()
        }

        val memberName = request.memberName
        if (executorName.equals(memberName, true)) {
            leaveParty(leavePartyRequest {
                this.executorId = request.executorId
            })

            return kickMemberResponse { }
        }

        val executorPartyMember = party.getMember(executorName) ?: run {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You aren't in a party."))
            throw Status.NOT_FOUND.withDescription("Failed to kick member: User $executorName isn't part of any party")
                .asRuntimeException()
        }

        if (executorPartyMember.role == PartyRole.MEMBER) {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You don't have enough permissions to kick members."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to kick member: User $executorName isn't permitted to do that")
                .log(logger).asRuntimeException()
        }

        val targetPartyMember = party.getMember(memberName) ?: run {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $memberName isn't part of your party."))
            throw Status.NOT_FOUND.withDescription("Failed to kick $memberName: User isn't part of party ${party.id}")
                .log(logger).asRuntimeException()
        }

        if (executorPartyMember.roleValue < targetPartyMember.roleValue) {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You don't have enough permissions to kick $memberName."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to kick $memberName: User $executorName isn't permitted to kick member with higher role")
                .log(logger).asRuntimeException()
        }

        try {
            val party = playerRepository.removeParty(memberName).first
            party.announce(text("${Glyphs.BALLOONS} $memberName ${Color.RED}left</color> the party."))

            val memberPlayer = memberName.fetchPlayer()
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS} You ${Color.GREEN}successfully</color> removed $memberName from the party."))
            memberPlayer?.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You've got kicked out of the party."))
        } catch (exception: StatusException) {
            if (exception.status.code == Status.Code.NOT_FOUND) executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $memberName isn't part of your party."))

            throw exception
        }

        return kickMemberResponse { }
    }

    override suspend fun leaveParty(request: LeavePartyRequest): LeavePartyResponse {
        val executorPlayer = request.executorId.fetchPlayer() ?: handleUserFetchingFailed(request.executorId)
        val executorName = executorPlayer.getName()

        try {
            val (party, member) = playerRepository.removeParty(executorName)
            party.announce(text("${Glyphs.BALLOONS} $executorName ${Color.RED}left</color> the party."))

            if (member.role == PartyRole.OWNER) {
                val transferOwnershipMember = party.membersList.minByOrNull { partyMember ->
                    val timeJoined = partyMember.timeJoined
                    Instant.ofEpochSecond(timeJoined.seconds, timeJoined.nanos.toLong())
                } ?: run {
                    party.membersList.forEach { member ->
                        val memberUuid = member.uuid.asUuid()
                        val memberPlayer = memberUuid.fetchPlayer()

                        playerRepository.getPlayer(member.name).partyId = null
                        memberPlayer?.sendMessage(text("${Glyphs.BALLOONS} The party you were in got ${Color.RED}deleted</color>."))
                    }

                    partyRepository.deleteParty(party)
                    return leavePartyResponse { }
                }

                playerRepository.updateRole(transferOwnershipMember.name, PartyRole.OWNER)
                transferOwnershipMember.name.fetchPlayer()
                    ?.sendMessage(text("${Glyphs.BALLOONS} The party owner ${Color.RED}left</color>. You were automatically promoted to party owner due to being in the party the longest."))

                partyRepository.updateParty(
                    party.copy {
                        this.ownerId = transferOwnershipMember.uuid
                    })
            }
        } catch (exception: StatusException) {
            if (exception.status.code == Status.Code.NOT_FOUND) executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You aren't in a party."))
            throw exception
        }

        return leavePartyResponse { }
    }

    private val promoteConfirmation = mutableSetOf<UUID>()
    override suspend fun promoteMember(request: PromoteMemberRequest): PromoteMemberResponse {
        val executorId = request.executorId
        val executorPlayer = executorId.fetchPlayer() ?: handleUserFetchingFailed(request.executorId)
        val executorName = executorPlayer.getName()

        val party = playerRepository.getParty(executorName) ?: run {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You aren't in a party."))
            throw Status.NOT_FOUND.withDescription("Failed to promote member: User $executorName isn't part of any party")
                .asRuntimeException()
        }

        val executorPartyMember = party.getMember(executorName) ?: run {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You aren't in a party."))
            throw Status.NOT_FOUND.withDescription("Failed to promote member: User $executorName isn't part of any party")
                .asRuntimeException()
        }

        if (executorPartyMember.role != PartyRole.OWNER) {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You don't have enough permissions to promote members."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to promote member: User $executorName isn't permitted to do that.")
                .log(logger).asRuntimeException()
        }

        val memberName = request.memberName
        if (memberName.equals(executorName, true)) {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You can't promote yourself."))

            throw Status.INVALID_ARGUMENT.withDescription("Failed to promote member: User $executorName cannot promote themselves")
                .log(logger).asRuntimeException()
        }

        val member = memberName.fetchPlayer()

        val targetPartyMember = party.getMember(memberName) ?: run {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $memberName isn't part of your party."))
            throw Status.NOT_FOUND.withDescription("Failed to promote $memberName: User isn't part of party ${party.id}")
                .log(logger).asRuntimeException()
        }

        if (targetPartyMember.roleValue == (PartyRole.OWNER_VALUE - 1)) {
            if (promoteConfirmation.add(executorPlayer.getUniqueId())) {
                executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You're about to transfer the ownership of the party to $memberName. Please try again to confirm your action!"))
                throw Status.FAILED_PRECONDITION.withDescription("Failed to promote member: User $executorName has to confirm the action first")
                    .log(logger).asRuntimeException()
            }

            playerRepository.updateRole(executorName, PartyRole.MOD)

            partyRepository.updateParty(party.copy { this.ownerId = targetPartyMember.uuid })
            promoteConfirmation.remove(executorPlayer.getUniqueId())
        }

        val nextHigherRole = PartyRole.forNumber(targetPartyMember.roleValue + 1) ?: run {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $memberName already has the highest party role."))

            throw Status.INVALID_ARGUMENT.withDescription("Failed to promote member: User $memberName already has highest role (${targetPartyMember.roleValue})")
                .log(logger).asRuntimeException()
        }

        playerRepository.updateRole(memberName, nextHigherRole)
        executorPlayer.sendMessage(text("${Glyphs.BALLOONS} You ${Color.GREEN}successfully</color> promoted $memberName to ${Color.BLUE + nextHigherRole}"))
        member?.sendMessage(text("${Glyphs.BALLOONS} Your party role was updated to ${Color.BLUE + nextHigherRole}."))
        return promoteMemberResponse { }
    }

    override suspend fun demoteMember(request: DemoteMemberRequest): DemoteMemberResponse {
        val executorId = request.executorId
        val executorPlayer = executorId.fetchPlayer() ?: handleUserFetchingFailed(request.executorId)
        val executorName = executorPlayer.getName()

        val party = playerRepository.getParty(executorName) ?: run {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You aren't in a party."))
            throw Status.NOT_FOUND.withDescription("Failed to demote member: User $executorName isn't part of any party")
                .asRuntimeException()
        }

        val executorPartyMember = party.getMember(executorName) ?: run {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You aren't in a party."))
            throw Status.NOT_FOUND.withDescription("Failed to demote member: User $executorName isn't part of any party")
                .asRuntimeException()
        }

        if (executorPartyMember.role != PartyRole.OWNER) {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You don't have enough permissions to demote members."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to demote member: User $executorName isn't permitted to do that.")
                .log(logger).asRuntimeException()
        }

        val memberName = request.memberName
        if (memberName.equals(executorName, true)) {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You can't demote yourself."))

            throw Status.INVALID_ARGUMENT.withDescription("Failed to demote member: User $executorName cannot demote themselves")
                .log(logger).asRuntimeException()
        }

        val member = memberName.fetchPlayer()

        val targetPartyMember = party.getMember(memberName) ?: run {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $memberName isn't part of your party."))
            throw Status.NOT_FOUND.withDescription("Failed to promote $memberName: User isn't part of party ${party.id}")
                .log(logger).asRuntimeException()
        }

        if (targetPartyMember.roleValue > executorPartyMember.roleValue) {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $memberName has a higher or equal role than you, therefore you can't demote them."))

            throw Status.PERMISSION_DENIED.withDescription("Failed to demote member: User $executorName has a weaker role than $memberName")
                .log(logger).asRuntimeException()
        }

        val nextLowerRole = PartyRole.forNumber(targetPartyMember.roleValue - 1) ?: run {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $memberName already has the lowest party role."))

            throw Status.INVALID_ARGUMENT.withDescription("Failed to demote member: User $memberName already has lowest role (${targetPartyMember.roleValue})")
                .log(logger).asRuntimeException()
        }

        playerRepository.updateRole(memberName, nextLowerRole)
        executorPlayer.sendMessage(text("${Glyphs.BALLOONS} You ${Color.GREEN}successfully</color> demoted $memberName to ${Color.BLUE + nextLowerRole}"))
        member?.sendMessage(text("${Glyphs.BALLOONS} Your party role was updated to ${Color.BLUE + nextLowerRole}."))
        return demoteMemberResponse { }
    }

    override suspend fun handleInvite(request: HandleInviteRequest): HandleInviteResponse {
        val executorId = request.executorId
        val executor = executorId.fetchPlayer() ?: handleUserFetchingFailed(executorId)
        val executorName = executor.getName()

        val invitorName = request.invitorName

        val executorPartyPlayer = playerRepository.getPlayer(executorName)
        val invitesList = executorPartyPlayer.invites
        val partyId = invitesList[invitorName]

        var party = partyRepository.getParty(partyId ?: run {
            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You haven't been invited by $invitorName."))
            throw Status.NOT_FOUND.withDescription("Failed to handle invite: User $executorName wasn't invited by $invitorName")
                .log(logger).asRuntimeException()
        }) ?: run {
            invitesList.remove(invitorName)

            executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} Sorry, the corresponding party doesn't exist anymore."))
            throw Status.NOT_FOUND.withDescription("Failed to handle invite: Party $partyId wasn't found")
                .log(logger).asRuntimeException()
        }

        party = inviteRepository.deleteInvite(executorName, party)

        if (request.accepted) {
            if (executorPartyPlayer.partyId != null) {
                executor.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You're already part of a party."))

                throw Status.FAILED_PRECONDITION.withDescription("Failed to accept invite: User $executorName is already part of a party")
                    .log(logger).asRuntimeException()
            }

            party.announce(text("${Glyphs.BALLOONS} $executorName ${Color.GREEN}joined</color> the party!"))
            playerRepository.assignParty(executorName, executorId.asUuid(), PartyRole.MEMBER, party)
            executor.sendMessage(text("${Glyphs.BALLOONS} You ${Color.GREEN}successfully</color> accepted $invitorName's party invite."))
            return handleInviteResponse { }
        }

        executor.sendMessage(text("${Glyphs.BALLOONS} You ${Color.RED}denied</color> $invitorName's party invite."))
        return handleInviteResponse { }
    }

    override suspend fun joinParty(request: JoinPartyRequest): JoinPartyResponse {
        val executorId = request.executorId
        val executorPlayer = executorId.fetchPlayer() ?: handleUserFetchingFailed(executorId)
        val executorName = executorPlayer.getName()

        val partyOwnerName = request.partyOwnerName
        val party = playerRepository.getParty(partyOwnerName) ?: run {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $partyOwnerName isn't in a party."))
            throw Status.FAILED_PRECONDITION.withDescription("Failed to retrieve party: User $partyOwnerName isn't part of any party")
                .log(logger).asRuntimeException()
        }

        if (party.settings.isPrivate) {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} $partyOwnerName's party is private."))
            throw Status.PERMISSION_DENIED.withDescription("Failed to join party: Party ${party.id} is private")
                .log(logger).asRuntimeException()
        }

        party.announce(text("${Glyphs.BALLOONS} $executorName ${Color.GREEN}joined</color> the party!"))
        playerRepository.assignParty(executorName, executorId.asUuid(), PartyRole.MEMBER, party)
        executorPlayer.sendMessage(text("${Glyphs.BALLOONS} You ${Color.GREEN}successfully</color> joined $partyOwnerName's party."))
        return joinPartyResponse { }
    }

    override suspend fun modifyPartySettings(request: ModifyPartySettingsRequest): ModifyPartySettingsResponse {
        val executorId = request.executorId
        val executorPlayer = executorId.fetchPlayer() ?: handleUserFetchingFailed(executorId)
        val executorName = executorPlayer.getName()

        val party = playerRepository.getParty(executorName) ?: run {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You aren't in a party."))
            throw Status.NOT_FOUND.withDescription("Failed to modify settings: User $executorName isn't part of any party")
                .asRuntimeException()
        }

        if (!party.ownerId.equals(executorId, true)) {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} You don't have enough permissions to modify the party settings."))
            throw Status.PERMISSION_DENIED.withDescription("Failed to modify settings: User $executorName isn't permitted to do that.")
                .log(logger).asRuntimeException()
        }

        if (request.settings == party.settings) {
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS + Color.RED} The party settings you tried to apply are already set."))
            return modifyPartySettingsResponse { }
        }

        if (request.settings.isPrivate != party.settings.isPrivate) {
            val decision = if (request.settings.isPrivate) "${Color.RED}private" else "${Color.GREEN}public"
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS} You ${Color.GREEN}successfully</color> changed the party's privacy to $decision</color>."))
        }

        if (request.settings.allowInvites != party.settings.allowInvites) {
            val decision = if (request.settings.allowInvites) "${Color.GREEN}enabled" else "${Color.RED}disabled"
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS} You $decision</color> party invites."))
        }

        if (request.settings.allowChatting != party.settings.allowChatting) {
            val decision = if (request.settings.allowInvites) "${Color.GREEN}enabled" else "${Color.RED}disabled"
            executorPlayer.sendMessage(text("${Glyphs.BALLOONS} You $decision</color> chatting."))
        }

        partyRepository.updateParty(party.copy { this.settings = request.settings })
        return modifyPartySettingsResponse { }
    }

    private suspend fun Party.announce(message: Component) {
        membersList.map { member -> member.uuid.fetchPlayer() }.forEach { player ->
            player?.sendMessage(message)
        }
    }

    private fun handleUserFetchingFailed(identifier: Any): Nothing {
        throw Status.NOT_FOUND.withDescription("Failed to fetch user data: No user to identify with $identifier")
            .log(logger).asRuntimeException()
    }

    private fun CloudPlayer.sendInvitationText(invitorName: String, partyOwnerName: String) {
        sendMessage(
            text("${Glyphs.BALLOONS} You got invited to $partyOwnerName's party!").appendNewline().append(
                text("${Glyphs.SPACE + Color.GREEN} <hover:show_text:'<gray>Click to accept the invite'><click:run_command:'/party accept $invitorName'>Accept</click></hover></color> <dark_gray>| ${Color.RED}<hover:show_text:'<gray>Click here to deny the invite'><click:run_command:'/party deny $invitorName'>Deny</click></hover></color>")
            )
        )
    }
}