package club.mcsports.droplet.party

import app.simplecloud.controller.api.ControllerApi
import app.simplecloud.droplet.api.auth.AuthCallCredentials
import app.simplecloud.droplet.api.auth.AuthSecretInterceptor
import app.simplecloud.droplet.api.droplet.Droplet
import app.simplecloud.droplet.player.api.PlayerApi
import build.buf.gen.simplecloud.controller.v1.ControllerDropletServiceGrpcKt
import club.mcsports.droplet.party.controller.Attacher
import club.mcsports.droplet.party.launcher.PartyStartCommand
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.apache.logging.log4j.LogManager

class PartyRuntime(
    private val args: PartyStartCommand
) {

    companion object {
        private val logger = LogManager.getLogger(PartyRuntime::class.java)
    }

    private val controllerApi = ControllerApi.createCoroutineApi(args.authSecret)
    private val playerApi = PlayerApi.createCoroutineApi(args.authSecret)
    private val pubSubClient = controllerApi.getPubSubClient()
    private val callCredentials = AuthCallCredentials(args.authSecret)

    private val server = createGrpcServer()
    private val channel =
        ManagedChannelBuilder.forAddress(args.controllerGrpcHost, args.controllerGrpcPort).usePlaintext().build()
    private val controllerStub = ControllerDropletServiceGrpcKt.ControllerDropletServiceCoroutineStub(channel)
        .withCallCredentials(callCredentials)
    private val attacher =
        Attacher(Droplet("party", "internal-party", args.grpcHost, args.grpcPort, 8081), channel, controllerStub)

    suspend fun start() {
        logger.info("Attaching to Controller...")
        attacher.enforceAttachBlocking()
        attacher.enforceAttach()
        startGrpcServer()

        suspendCancellableCoroutine<Unit> { continuation ->
            Runtime.getRuntime().addShutdownHook(Thread {
                server.shutdown()
                continuation.resume(Unit) { cause, _, _ ->
                    logger.info("Server shutdown due to: $cause")
                }
            })
        }
    }

    private fun startGrpcServer() {
        logger.info("Starting gRPC server...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                server.start()
                server.awaitTermination()
            } catch (e: Exception) {
                logger.error("Error in gRPC server", e)
                throw e
            }
        }
    }

    private fun createGrpcServer(): Server {
        return ServerBuilder.forPort(args.grpcPort)
            .intercept(AuthSecretInterceptor(args.grpcHost, args.authorizationPort))
            .build()
    }

}