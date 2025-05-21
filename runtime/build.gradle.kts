plugins {
    application
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.bundles.log4j)
    implementation(libs.minimessage)
    implementation(libs.clikt)
}

application {
    mainClass.set("club.mcsports.droplet.party.launcher.LauncherKt")
}