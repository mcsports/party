package club.mcsports.droplet.party.service

import club.mcsports.droplet.party.PartyManager
import club.mcsports.droplet.party.extension.fetchPlayer
import com.mcsports.party.v1.PartyDataGrpcKt
import com.mcsports.party.v1.PartyRequest
import com.mcsports.party.v1.PartyResponse
import com.mcsports.party.v1.partyResponse
import io.grpc.Status

class PartyDataService(private val partyManager: PartyManager) : PartyDataGrpcKt.PartyDataCoroutineImplBase() {

    override suspend fun getParty(request: PartyRequest): PartyResponse {
        return partyResponse {
            val memberName = request.memberId.fetchPlayer().getName()
            val informationHolder = partyManager.informationHolder(memberName)

            val partyId = informationHolder.partyId
                ?: throw Status.NOT_FOUND.withDescription("User $memberName isn't part of any party")
                    .asRuntimeException()

            val party = partyManager.parties[partyId]
                ?: run {
                    informationHolder.partyId = null

                    throw Status.UNAVAILABLE.withDescription("Party $memberName not found")
                        .asRuntimeException()
                }

            this.party = party
        }
    }

}