package club.mcsports.droplet.party.service

import club.mcsports.droplet.party.PartyManager
import club.mcsports.droplet.party.extension.fetchPlayer
import club.mcsports.droplet.party.extension.log
import com.mcsports.party.v1.PartyDataGrpcKt
import com.mcsports.party.v1.PartyRequest
import com.mcsports.party.v1.PartyResponse
import com.mcsports.party.v1.partyResponse
import io.grpc.Status
import org.apache.logging.log4j.LogManager

class PartyDataService(private val partyManager: PartyManager) : PartyDataGrpcKt.PartyDataCoroutineImplBase() {

    private val logger = LogManager.getLogger(PartyDataService::class.java)
    override suspend fun getParty(request: PartyRequest): PartyResponse {

        val memberName = request.memberId.fetchPlayer().getName()
        val informationHolder = partyManager.informationHolder(memberName)

        val partyId = informationHolder.partyId
            ?: throw Status.NOT_FOUND.withDescription("User $memberName isn't part of any party")
                .log(logger).asRuntimeException()

        val party = partyManager.parties[partyId]
            ?: run {
                informationHolder.partyId = null

                throw Status.UNAVAILABLE.withDescription("Party $memberName not found").log(logger)
                    .asRuntimeException()
            }

        return partyResponse {
            this.party = party
        }
    }

}