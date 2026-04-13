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
    // Paper API (これはサーバーが提供するため同梱しない)
    val paperVersion = "1.21.10-R0.1-SNAPSHOT"
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    testImplementation("io.papermc.paper:paper-api:$paperVersion")

    // Database & Utils (同梱対象)
    implementation("org.xerial:sqlite-jdbc:3.50.1.0")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.google.code.gson:gson:2.11.0") // バージョンをPaperに合わせる

    // Plugins API (Provided - サーバーに別途プラグインとして導入されるもの)
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

    // Libraries to be bundled (同梱対象に戻す)
    compileOnly("com.github.NuVotifier:NuVotifier:2.7.2")
    compileOnly("com.github.retrooper:packetevents-spigot:2.11.0")
    implementation("io.github.toxicity188:bettermodel:1.15.0")
    implementation("com.discordsrv:discordsrv:1.28.0")

    testImplementation("com.github.NuVotifier:NuVotifier:2.7.2")
    testImplementation("com.github.retrooper:packetevents-spigot:2.11.0")

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

    // assembleタスク（buildの一部）が呼ばれた際にshadowJarをメインの成果物にする
    assemble {
        dependsOn(shadowJar)
    }
}
