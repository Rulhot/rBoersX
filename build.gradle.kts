plugins {
    id("java-library")
    id("io.freefair.lombok") version "9.0.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        content { excludeGroupByRegex("org\\.by1337\\.bvault.*") }
    }
    maven("https://repo.nightexpressdev.com/releases") {
        content { includeGroupByRegex("su\\.nightexpress.*") }
    }
    maven("https://repo.rosewooddev.io/repository/public/") {
        content { includeGroup("org.black_ixx") }
    }
    maven("https://repo.by1337.space/repository/maven-releases/") {
        content { includeGroupByRegex("org\\.by1337\\.bvault.*") }
    }
    maven("https://maven.enginehub.org/repo/") {
        content { includeGroupByRegex("com\\.sk89q.*") }
    }
    maven("https://repo.codemc.io/repository/maven-releases/") {
        content { includeGroup("com.github.retrooper") }
    }
    maven("https://repo.fancyinnovations.com/releases") {
        content { includeGroup("de.oliver") }
    }
    maven("https://jitpack.io") {
        content {
            includeGroup("com.github.MilkBowl")
            includeGroup("com.github.decentsoftware-eu")
        }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.j256.ormlite:ormlite-jdbc:6.1")
    compileOnly("com.zaxxer:HikariCP:6.2.1")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    compileOnly("org.by1337.bvault.api:BVaultApi:1.1") { isTransitive = false }
    compileOnly("su.nightexpress.excellenteconomy:ExcellentEconomy:2.8.0") { isTransitive = false }
    compileOnly("org.black_ixx:playerpoints:3.3.4") { isTransitive = false }
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
    compileOnly("de.oliver:FancyHolograms:2.10.0") { isTransitive = false }
    compileOnly("com.github.decentsoftware-eu:decentholograms:2.8.12") { isTransitive = false }
    compileOnly("org.apache.logging.log4j:log4j-core:2.25.3") { isTransitive = false }
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.9") { isTransitive = false }
    compileOnly("com.sk89q.worldguard:worldguard-core:7.0.9") { isTransitive = false }
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.0") { isTransitive = false }
    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.0") { isTransitive = false }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

lombok {
    version = "1.18.42"
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal"))
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
