plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "9.6.1"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.skriptlang.org/releases")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.SkriptLang:Skript:2.17.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.ow2.asm:asm-tree:9.8")
    implementation("org.ow2.asm:asm-analysis:9.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    test { useJUnitPlatform() }
    runServer {
        minecraftVersion("26.3")
        jvmArgs("-Xms2G", "-Xmx2G")
        systemProperty("com.mojang.eula.agree", "true")

        downloadPlugins {
            url("https://github.com/SkriptLang/Skript/releases/download/2.17.0/Skript-2.17.0.jar")
            if (providers.gradleProperty("verifyAddons").isPresent) {
                url("https://cdn.modrinth.com/data/a0tlbHZO/versions/bTBlzhGZ/SkBee-3.25.4.jar")
                url("https://cdn.modrinth.com/data/mn8WEUWe/versions/ofui29Kj/Lusk-1.3.14.jar")
                url("https://cdn.modrinth.com/data/CmkvxP9q/versions/ZymauMmW/PacketEventsSK-1.1.3.jar")
                url("https://cdn.modrinth.com/data/HYKaKraK/versions/m78nFxYg/packetevents-spigot-2.14.0.jar")
            }
        }
    }

    shadowJar {
        relocate("org.objectweb.asm", "org.skdocs.skdocstool.internal.asm")
        archiveClassifier.set("")
        destinationDirectory.set(layout.buildDirectory.dir("libs"))
    }

    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
