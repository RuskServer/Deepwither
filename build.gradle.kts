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
    // Paper API (コンパイルに必要な最新バージョンを明示的に指定)
    val paperVersion = "1.21.10-R0.1-SNAPSHOT"
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    testImplementation("io.papermc.paper:paper-api:$paperVersion")

    // Database & Utils
    implementation("org.xerial:sqlite-jdbc:3.50.1.0")
    implementation("com.zaxxer:HikariCP:6.2.1")

    // Plugins API (Provided)
    val mythicVersion = "5.9.5"
    compileOnly("io.lumine:Mythic-Dist:$mythicVersion")
    testImplementation("io.lumine:Mythic-Dist:$mythicVersion")

    val papiVersion = "2.11.6"
    compileOnly("me.clip:placeholderapi:$papiVersion")
    testImplementation("me.clip:placeholderapi:$papiVersion")

    val vaultVersion = "2.16"
    compileOnly("net.milkbowl.vault:VaultUnlockedAPI:$vaultVersion")
    testImplementation("net.milkbowl.vault:VaultUnlockedAPI:$vaultVersion")

    val worldguardVersion = "7.0.13"
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:$worldguardVersion")
    testImplementation("com.sk89q.worldguard:worldguard-bukkit:$worldguardVersion")

    val votifierVersion = "2.7.2"
    compileOnly("com.github.NuVotifier:NuVotifier:$votifierVersion")
    testImplementation("com.github.NuVotifier:NuVotifier:$votifierVersion")

    val packeteventsVersion = "2.11.0"
    compileOnly("com.github.retrooper:packetevents-spigot:$packeteventsVersion")
    testImplementation("com.github.retrooper:packetevents-spigot:$packeteventsVersion")

    val bettermodelVersion = "1.15.0"
    compileOnly("io.github.toxicity188:bettermodel:$bettermodelVersion")
    testImplementation("io.github.toxicity188:bettermodel:$bettermodelVersion")

    val discordsrvVersion = "1.28.0"
    compileOnly("com.discordsrv:discordsrv:$discordsrvVersion")
    testImplementation("com.discordsrv:discordsrv:$discordsrvVersion")

    // Local library
    implementation(files("lib/EQF-Project-1.0-SNAPSHOT.jar"))

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    test {
        useJUnitPlatform()
    }
    // JavaDoc settings
    javadoc {
        options {
            encoding = "UTF-8"
            locale = "ja_JP"
            val options = this as StandardJavadocDocletOptions
            options.tags("apiNote:a:API Note:")
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
