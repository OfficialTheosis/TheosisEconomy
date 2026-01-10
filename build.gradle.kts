plugins {
    id("java")
    id("com.gradleup.shadow") version "9.3.0"
}

group = "me.Short.TheosisEconomy"
version = "2.0.0"

tasks {
    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        // Make it so that a separate jar with "-all" at the end doesn't generate (https://imperceptiblethoughts.com/shadow/configuration/#configuring-output-name)
        archiveClassifier.set("")

        // Relocations
        relocate("org.bstats", "shadow.org.bstats")
    }
}

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.bstats:bstats-bukkit:3.1.0")

    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.7")
    compileOnly("com.gitlab.ruany:LiteBansAPI:0.6.1")
}