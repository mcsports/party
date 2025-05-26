package club.mcsports.droplet.party.api

import club.mcsports.droplet.party.api.impl.coroutine.PartyApiCoroutineImpl
import club.mcsports.droplet.party.api.impl.future.PartyApiFutureImpl

interface PartyApi {

    interface Coroutine {
        fun getData(): DataApi.Coroutine
        fun getInteraction(): InteractionApi.Coroutine
    }

    interface Future {
        fun getData(): DataApi.Future
        fun getInteraction(): InteractionApi.Future
    }

    companion object {

        @JvmStatic
        fun createFutureApi(authSecret: String, host: String, port: Int): Future {
            return PartyApiFutureImpl(authSecret, host, port)
        }

        @JvmStatic
        fun createFutureApi(): Future {
            return createFutureApi(
                System.getenv("CONTROLLER_SECRET"),
                System.getenv("PARTY_HOST") ?: "0.0.0.0",
                System.getenv("PARTY_PORT")?.toInt() ?: 5831
            )
        }

        @JvmStatic
        fun createCoroutineApi(authSecret: String, host: String, port: Int): Coroutine {
            return PartyApiCoroutineImpl(authSecret, host, port)
        }

        @JvmStatic
        fun createCoroutineApi(): Coroutine {
            return createCoroutineApi(
                System.getenv("CONTROLLER_SECRET"),
                System.getenv("PARTY_HOST") ?: "0.0.0.0",
                System.getenv("PARTY_PORT")?.toInt() ?: 5831
            )
        }

    }

}