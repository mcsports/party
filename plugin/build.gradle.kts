plugins {
    alias(libs.plugins.paperweight.userdev)
}

dependencies {
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
    implementation(project(":api"))
}

tasks.shadowJar {
    exclude("io.netty")
    exclude("app.simplecloud")
    relocate("io.grpc", "club.mcsports.droplet.party.relocate.io.grpc")
    relocate("com.google.protobuf", "club.mcsports.droplet.party.relocate.google.protobuf")
    relocate("com.google.common", "club.mcsports.droplet.party.relocate.google.common")
    mergeServiceFiles()
    archiveFileName.set("${rootProject.name}-${project.name}.jar")
}