plugins {
    `maven-publish`
}

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

publishing {
    repositories {
        maven {
            name = "MCSports"
            url = uri("https://repo.mcsports.club/releases")
            credentials {
                username = "deploy"
                password = System.getenv("REPO_TOKEN") ?: ""
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            val hash = System.getenv("COMMIT_HASH")
            version = if (hash != null) "${rootProject.version}-$hash" else rootProject.version.toString()
        }
    }
}