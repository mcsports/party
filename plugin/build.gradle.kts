plugins {
    kotlin("kapt")
}

dependencies {
    implementation(project(":api"))
    compileOnly(libs.velocity)
    kapt(libs.velocity)
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