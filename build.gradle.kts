plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "8.3.6"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.skriptlang.org/releases")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.SkriptLang:Skript:2.16.0")
    implementation("com.google.code.gson:gson:2.10.1")
    compileOnly("com.github.shanebeee:SkriptRegistration:1.4.2")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    runServer {
        minecraftVersion("1.21.11")
        jvmArgs("-Xms2G", "-Xmx2G")
        systemProperty("com.mojang.eula.agree", "true")

        downloadPlugins {
            url("https://github.com/SkriptLang/Skript/releases/download/2.16.0/Skript-2.16.0.jar")
        }
    }

    shadowJar {
        archiveClassifier.set("")
        destinationDirectory.set(layout.buildDirectory.dir("libs"))
    }

    jar {
        archiveClassifier.set("original")
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