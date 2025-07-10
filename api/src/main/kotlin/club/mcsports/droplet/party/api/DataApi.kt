package club.mcsports.droplet.party.api

import com.mcsports.party.v1.Party
import com.mcsports.party.v1.PartyMember
import java.util.UUID
import java.util.concurrent.CompletableFuture
import io.grpc.Status

interface DataApi {

    interface Coroutine {
        /**
         * Gets a party from a player's uuid
         *
         * @param member the uuid of the player
         *
         * @return the party object
         * @throws Status.INVALID_ARGUMENT if the given player couldn't be fetched properly
         * @throws Status.NOT_FOUND if the player isn't part of any party
         */
        suspend fun getParty(member: UUID): Party

        /**
         * Gets a party member object from a player's uuid
         *
         * @param member the uuid of the player
         *
         * @return the party member object
         * @throws Status.INVALID_ARGUMENT if the given player couldn't be fetched properly
         * @throws Status.NOT_FOUND if the player isn't part of any party
         * @throws Status.DATA_LOSS if somehow the player isn't in the party that he was in milliseconds before anymore
         */
        suspend fun getMember(member: UUID): PartyMember
    }

    interface Future {
        /**
         * Gets a party from a player's uuid
         *
         * @param member the uuid of the player
         *
         * @return the party object
         * @throws Status.INVALID_ARGUMENT if the given player couldn't be fetched properly
         * @throws Status.NOT_FOUND if the player isn't part of any party
         */
        fun getParty(member: UUID): CompletableFuture<Party>

        /**
         * Gets a party member object from a player's uuid
         *
         * @param member the uuid of the player
         *
         * @return the party member object
         * @throws Status.INVALID_ARGUMENT if the given player couldn't be fetched properly
         * @throws Status.NOT_FOUND if the player isn't part of any party
         * @throws Status.DATA_LOSS if somehow the player isn't in the party that he was in milliseconds before anymore
         */
        fun getMember(member: UUID): CompletableFuture<PartyMember>
    }

}