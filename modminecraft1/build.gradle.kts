import org.gradle.kotlin.dsl.`kotlin-dsl`
import java.net.URI

plugins {
    id("java")
    id("kotlin") version "1.9.22"
    id("com.mojang.mappings") version "1.0.0"
    id("net.neoforged.gradle") version "6.0.+"
}

group = "com.tlgd.navalwarfare"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases") {
        name = "NeoForge"
    }
    maven("https://maven.minecraftforge.net/releases") {
        name = "Forge"
    }
    maven("https://jitpack.io") {
        name = "JitPack"
    }
}

dependencies {
    // NeoForge
    minecraft "net.neoforged:neoforge:1.21.1-21.1.0"

    // Kotlin stdlib
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    // Gson for JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Optional: Create API (for vehicle detection)
    // compileOnly("com.simibubi.create:create:1.21.1-0.6.0")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf(
            "-Xjvm-default=all",
            "-Xopt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs << "-Xlint:unchecked" << "-Xlint:deprecation"
}

// NeoForge mod configuration
neoforge {
    mappingsChannel.set("official")
    mappingsVersion.set("1.21.1")
    modId.set("navalwarfare")
    modName.set("Naval Warfare")
    version.set(project.version.toString())

    // Mixins
    mixinConfigs.add("navalwarfare.mixins.json")
}

// Run configurations
tasks.named("runClient") {
    doLast {
        jvmArgs(
            "-Dforge.logging.console.level=debug",
            "-Dforge.logging.markers=SCAN,REGISTRIES,REGISTRYDUMP"
        )
    }
}

tasks.named("runServer") {
    doLast {
        jvmArgs(
            "-Dforge.logging.console.level=debug"
        )
    }
}

// Build JAR with all dependencies (shadow jar style)
tasks.named("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "Implementation-Title": "Naval Warfare",
            "Implementation-Version": version,
            "Implementation-Vendor": "TLGD",
            "NeoForge-Mod": "navalwarfare",
            "Main-Class": "net.neoforged.fml.loading.FMLServerLaunchProvider"
        )
    }
    from(configurations.runtimeClasspath.map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/MANIFEST.MF")
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }
}

// Source sets
sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin")
        }
        resources {
            srcDirs("src/main/resources")
        }
    }
}

// Compile tasks
tasks.compileKotlin {
    dependsOn(tasks.processResources)
}

// Signing (opcional, para publicação)
tasks.named("signMavenPublication") {
    onlyIf { project.hasProperty("signing.keyId") }
}