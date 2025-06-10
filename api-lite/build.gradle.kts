plugins {
    `maven-publish`
}

dependencies {
    api(project(":api"))
}

tasks.shadowJar {
    dependencies {
        include(project(":api"))
        include(project(":shared"))
        include(dependency(libs.mcsports.proto.java.get()))
        include(dependency(libs.mcsports.proto.kotlin.get()))
    }
    mergeServiceFiles()
    archiveFileName.set("${rootProject.name}-${project.name}.jar")
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