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
           initialInvites: List<UUID>
        ): Party

        suspend fun inviteMember(
            member: UUID,
            executor: UUID
        )

        suspend fun promoteMember(
            member: UUID,
            executor: UUID
        )

        suspend fun demoteMember(
            member: UUID,
            executor: UUID
        )

        suspend fun partyChat(
            member: UUID,
            message: Component
        )

        suspend fun kickMember(
            member: UUID,
            executor: UUID
        )

        suspend fun deleteParty(member: UUID)
        suspend fun memberLeaveParty(member: UUID)
    }

    interface Future {
        fun createParty(
            creator: UUID,
            settings: PartySettings,
            initialInvites: List<UUID>
        ): CompletableFuture<Party>

        fun inviteMember(
            member: UUID,
            executor: UUID
        )

        fun promoteMember(
            member: UUID,
            executor: UUID
        )

        fun demoteMember(
            member: UUID,
            executor: UUID
        )

        fun partyChat(
            member: UUID,
            message: Component
        )

        fun kickMember(
            member: UUID,
            executor: UUID
        )

        fun deleteParty(member: UUID)
        fun memberLeaveParty(member: UUID)
    }
}