dependencies {
    api(libs.mcsports.proto.kotlin) {
        exclude("io.grpc")
        exclude("io.netty")
    }
    api(libs.mcsports.proto.java) {
        exclude("io.grpc")
        exclude("io.netty")
    }
    api(libs.bundles.configurate)
    api(libs.bundles.simplecloud) {
        exclude("org.slf4j")
        exclude("org.apache.logging")
        exclude("io.netty")
        exclude("io.grpc")
    }
    api(libs.bundles.grpc)
}