plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.1"
}

group = "me.Short.TheosisEconomy"
version = "2.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        // Make it so that a separate jar with "-all" at the end doesn't generate (https://imperceptiblethoughts.com/shadow/configuration/#configuring-output-name)
        archiveClassifier.set("")

        dependencies {
            // Only merge bStats into the final jar, no other dependencies
            exclude { it.moduleGroup != "org.bstats" }
        }

        // Relocations
        relocate("org.bstats", "shadow.org.bstats")
    }
}

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    implementation("org.bstats:bstats-bukkit:3.2.1")

    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.12.2")
    compileOnly("com.gitlab.ruany:LiteBansAPI:0.6.1")
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")
}