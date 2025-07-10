package club.mcsports.droplet.party.service

import club.mcsports.droplet.party.extension.fetchPlayer
import club.mcsports.droplet.party.extension.log
import club.mcsports.droplet.party.repository.PlayerRepository
import com.mcsports.party.v1.MemberRequest
import com.mcsports.party.v1.MemberResponse
import com.mcsports.party.v1.PartyDataGrpcKt
import com.mcsports.party.v1.PartyRequest
import com.mcsports.party.v1.PartyResponse
import com.mcsports.party.v1.memberResponse
import com.mcsports.party.v1.partyResponse
import io.grpc.Status
import org.apache.logging.log4j.LogManager

class PartyDataService(
    private val playerRepository: PlayerRepository
) : PartyDataGrpcKt.PartyDataCoroutineImplBase() {

    private val logger = LogManager.getLogger(PartyDataService::class.java)

    override suspend fun getParty(request: PartyRequest): PartyResponse {
        val memberId = request.memberId
        val memberCloudPlayer = memberId.fetchPlayer()
            ?: throw Status.INVALID_ARGUMENT.withDescription("Failed to fetch user data: No user to identify with $memberId")
                .log(logger).asRuntimeException()

        val party = playerRepository.getParty(memberCloudPlayer.getName())
            ?: throw Status.NOT_FOUND.withDescription("Failed to get party: User $memberId isn't part of any party")
                .log(logger).asRuntimeException()

        return partyResponse {
            this.party = party
        }
    }

    override suspend fun getMember(request: MemberRequest): MemberResponse {
        val memberId = request.memberId
        val memberCloudPlayer = memberId.fetchPlayer()
            ?: throw Status.INVALID_ARGUMENT.withDescription("Failed to fetch user data: No user to identify with $memberId")
                .log(logger).asRuntimeException()
        val party = playerRepository.getParty(memberCloudPlayer.getName())
            ?: throw Status.NOT_FOUND.withDescription("Failed to get party: User $memberId isn't part of any party")
                .log(logger).asRuntimeException()

        val partyMember = party.membersList.firstOrNull { it.uuid == memberId }
            ?: throw Status.DATA_LOSS.withDescription("Failed to get party: User $memberId isn't part of party ${party.id} anymore")
                .log(logger).asRuntimeException()

        return memberResponse {
            this.member = partyMember
        }
    }
}