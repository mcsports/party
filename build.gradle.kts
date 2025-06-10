import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    alias(libs.plugins.kotlin)
    alias(libs.plugins.shadow)
}

allprojects {
    group = "club.mcsports.droplet.party"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://repo.mcsports.club/snapshots")
        maven("https://repo.mcsports.club/releases")
        maven("https://repo.simplecloud.app/snapshots")
        maven("https://buf.build/gen/maven")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

subprojects {
    apply {
        plugin(rootProject.libs.plugins.kotlin.get().pluginId)
        plugin(rootProject.libs.plugins.shadow.get().pluginId)
    }

    dependencies {
        implementation(rootProject.libs.kotlin.stdlib)
        testImplementation(rootProject.libs.kotlin.test)
        implementation(rootProject.libs.kotlin.coroutines)
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    tasks.named("build") {
        dependsOn("shadowJar")
    }

    tasks.named("shadowJar", ShadowJar::class.java) {
        mergeServiceFiles()
        archiveFileName.set("${rootProject.name}-${project.name}.jar")
    }

    tasks.test {
        useJUnitPlatform()
    }
}