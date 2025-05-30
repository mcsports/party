package club.mcsports.droplet.party.api

import com.mcsports.party.v1.Party
import com.mcsports.party.v1.PartySettings
import net.kyori.adventure.text.Component
import java.util.UUID
import java.util.concurrent.CompletableFuture

interface InteractionApi {

    interface Coroutine {
        suspend fun createParty(
           creator: UUID,
           settings: PartySettings,
           initialInvites: List<String>
        ): Party

        suspend fun inviteMember(
            memberName: String,
            executor: UUID
        )

        suspend fun promoteMember(
            memberName: String,
            executor: UUID
        )

        suspend fun demoteMember(
            memberName: String,
            executor: UUID
        )

        suspend fun partyChat(
            executor: UUID,
            message: Component
        )

        suspend fun kickMember(
            memberName: String,
            executor: UUID
        )

        suspend fun acceptPartyInvite(
            invitorName: String,
            executor: UUID
        )

        suspend fun denyPartyInvite(
            invitorName: String,
            executor: UUID
        )

        suspend fun deleteParty(executor: UUID)
        suspend fun memberLeaveParty(member: UUID)
    }

    interface Future {
        fun createParty(
            creator: UUID,
            settings: PartySettings,
            initialInvites: List<String>
        ): CompletableFuture<Party>

        fun inviteMember(
            memberName: String,
            executor: UUID
        )

        fun promoteMember(
            memberName: String,
            executor: UUID
        )

        fun demoteMember(
            memberName: String,
            executor: UUID
        )

        fun partyChat(
            executor: UUID,
            message: Component
        )

        fun kickMember(
            memberName: String,
            executor: UUID
        )

        fun acceptPartyInvite(
            invitorName: String,
            executor: UUID
        )

        fun denyPartyInvite(
            invitorName: String,
            executor: UUID
        )

        fun deleteParty(executor: UUID)
        fun memberLeaveParty(member: UUID)
    }
}