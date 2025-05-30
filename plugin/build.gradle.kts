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
    relocate("io.grpc", "club.mcsports.droplet.queue.relocate.io.grpc")
    relocate("com.google.protobuf", "club.mcsports.droplet.queue.relocate.google.protobuf")
    relocate("com.google.common", "club.mcsports.droplet.queue.relocate.google.common")
    mergeServiceFiles()
    archiveFileName.set("${rootProject.name}-${project.name}.jar")
}