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

tasks.processResources {
    // Substitui variaveis no neoforge.mods.toml, se voce usar ${version}
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(props)
    }
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
