package club.mcsports.droplet.party.service

import club.mcsports.droplet.party.PartyManager
import club.mcsports.droplet.party.extension.fetchPlayer
import club.mcsports.droplet.party.extension.log
import com.mcsports.party.v1.MemberRoleRequest
import com.mcsports.party.v1.MemberRoleResponse
import com.mcsports.party.v1.PartyDataGrpcKt
import com.mcsports.party.v1.PartyRequest
import com.mcsports.party.v1.PartyResponse
import com.mcsports.party.v1.memberRoleResponse
import com.mcsports.party.v1.partyResponse
import io.grpc.Status
import org.apache.logging.log4j.LogManager

class PartyDataService(private val partyManager: PartyManager) : PartyDataGrpcKt.PartyDataCoroutineImplBase() {

    private val logger = LogManager.getLogger(PartyDataService::class.java)
    override suspend fun getParty(request: PartyRequest): PartyResponse {
        val memberName = request.memberId.fetchPlayer()?.getName() ?: throw Status.NOT_FOUND.withDescription("Failed to fetch user data: No user to identify with ${request.memberId}").log(logger).asRuntimeException()
        val informationHolder = partyManager.informationHolder(memberName)

        val partyId = informationHolder.partyId
            ?: throw Status.NOT_FOUND.withDescription("Failed to get party: User $memberName isn't part of any party")
                .log(logger).asRuntimeException()

        val party = partyManager.parties[partyId]
            ?: run {
                informationHolder.partyId = null

                throw Status.UNAVAILABLE.withDescription("Failed to get party: Party $partyId not found").log(logger)
                    .asRuntimeException()
            }

        return partyResponse {
            this.party = party
        }
    }

    override suspend fun getMemberRole(request: MemberRoleRequest): MemberRoleResponse {
        val player = request.memberId.fetchPlayer() ?: throw Status.NOT_FOUND.withDescription("Failed to fetch user data: No user to identify with ${request.memberId}").log(logger).asRuntimeException()
        val playerName = player.getName()

        val party = partyManager.parties[partyManager.informationHolder(playerName).partyId] ?: run {
            throw Status.NOT_FOUND.withDescription("Failed to get party: User $playerName isn't part of any party")
                .log(logger).asRuntimeException()
        }

        val partyMember = party.membersList.firstOrNull { it.name.equals(playerName, true) } ?: run {

            throw Status.DATA_LOSS.withDescription("Failed to get party member: User $playerName isn't part of party ${party.id} anymore")
                .log(logger).asRuntimeException()
        }

        return memberRoleResponse {
            this.role = partyMember.role
        }
    }

}