plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("net.neoforged.moddev") version "2.0.78"
}

group = "com.tlgd.navalwarfare"
version = "1.0.0"

base {
    archivesName.set("navalwarfare")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Usa o compilador K1 (1.9), pois o K2 travou com um erro interno
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
    }
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases") {
        name = "NeoForge"
    }
    // Kotlin for Forge (fornece o Kotlin em runtime para mods NeoForge)
    maven("https://thedarkcolour.github.io/KotlinForForge/") {
        name = "KotlinForForge"
    }
}

neoForge {
    version = "21.1.172"

    runs {
        create("client") {
            client()
        }
        create("server") {
            server()
            programArgument("--nogui")
        }
    }

    mods {
        create("navalwarfare") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    // Kotlin para NeoForge (nao empacotamos o Kotlin dentro do jar)
    implementation("thedarkcolour:kotlinforforge-neoforge:5.6.0")

    // Opcional: Create API
    // compileOnly("com.simibubi.create:create:1.21.1-0.6.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "Implementation-Title" to "Naval Warfare",
            "Implementation-Version" to project.version.toString(),
            "Implementation-Vendor" to "TLGD"
        )
    }
}
