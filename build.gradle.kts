plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.lunar_prototype"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://mvn.lumine.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.codemc.io/repository/creatorfromhell/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://jitpack.io")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
    maven("https://nexus.scarsz.me/content/groups/public/")
    maven("https://repo.ruskserver.com/repository/maven-public/")
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")

    // Database & Utils
    implementation("org.xerial:sqlite-jdbc:3.50.1.0")
    implementation("com.zaxxer:HikariCP:6.2.1")

    // Plugins API (Provided)
    compileOnly("io.lumine:Mythic-Dist:5.9.5")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.milkbowl.vault:VaultUnlockedAPI:2.16")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.13")
    compileOnly("com.github.NuVotifier:NuVotifier:2.7.2")
    compileOnly("com.github.retrooper:packetevents-spigot:2.11.0")
    compileOnly("io.github.toxicity188:bettermodel:1.15.0")
    compileOnly("com.discordsrv:discordsrv:1.28.0")

    // Local library
    implementation(files("lib/EQF-Project-1.0-SNAPSHOT.jar"))
}

tasks {
    // JavaDoc settings
    javadoc {
        options {
            encoding = "UTF-8"
            locale = "ja_JP"
            (this as StandardJavadocDocletOptions).addStringOption("apiNote", "a:API Note:")
        }
    }

    // Maven Shade equivalent
    shadowJar {
        archiveClassifier.set("")
        // Necessary relocation can be added here
    }

    // Resource filtering (plugin.yml etc)
    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    build {
        dependsOn(shadowJar)
    }
}
