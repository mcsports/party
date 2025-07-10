package club.mcsports.droplet.party.invitation

import app.simplecloud.droplet.player.api.CloudPlayer
import com.mcsports.party.v1.PartyInvite
import io.grpc.Status

/**
 * Helper class to determine the status of a party invite
 * @param status **OK** for successful invite, **ALREADY_EXISTS** for already pending invite in party, **NOT_FOUND** if the player is offline or **INVALID_ARGUMENT** if the player to invite is equal to the invitor
 * @param cloudPlayer the cloud player object for further actions
 * @param invite the created party invite, null if status isn't OK
 */
data class InviteResult(
    val status: Status,
    val cloudPlayer: CloudPlayer?,
    val invite: PartyInvite?
)