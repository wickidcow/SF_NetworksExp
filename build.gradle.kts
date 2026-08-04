plugins {
    java
    id("com.gradleup.shadow") version "9.3.2"
}

group = "com.wickidcow.networks"
version = "2.1.112-Legacy-Alpha1"

val slimefunLegacyJarPath = providers.gradleProperty("slimefunLegacyJar")
    .orElse(providers.environmentVariable("SLIMEFUN_LEGACY_JAR"))
    .orElse(layout.projectDirectory.file("lib/Slimefun-Legacy.jar").asFile.absolutePath)
val slimefunLegacyJar = file(slimefunLegacyJarPath.get())

if (!slimefunLegacyJar.isFile) {
    throw GradleException(
        "Slimefun Legacy JAR not found at '${slimefunLegacyJar.absolutePath}'. " +
            "Pass -PslimefunLegacyJar=/path/to/Slimefun-*.jar or set SLIMEFUN_LEGACY_JAR."
    )
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

configurations.configureEach {
    exclude(group = "com.github.SlimefunGuguProject", module = "Slimefun4")
    exclude(group = "com.github.slimefun", module = "Slimefun4")
    exclude(group = "io.github.thebusybiscuit", module = "Slimefun4")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://nexus.neetgames.com/repository/maven-public")
    maven("https://repo.bg-software.com/repository/api/")
    maven("https://repo.rosewooddev.io/repository/public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.codemc.org/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.alessiodp.com/releases/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.jeff-media.com/public")
}

dependencies {
    // Core server APIs. The exact Slimefun Legacy JAR is supplied by CI or the developer.
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly(files(slimefunLegacyJar))

    implementation("org.bstats:bstats-bukkit:3.2.1")
    implementation("com.jeff-media:MorePersistentDataTypes:2.4.0")
    implementation("dev.sefiraat:SefiLib:0.2.6")

    compileOnly("com.google.code.findbugs:annotations:3.0.1u2") {
        exclude("net.jcip", "jcip-annotations")
        exclude("com.google.code.findbugs", "jsr305")
    }
    compileOnly("org.projectlombok:lombok:1.18.44")
    annotationProcessor("org.projectlombok:lombok:1.18.44")

    // Optional integrations. These remain compile-only and are never bundled.
    compileOnly("com.github.SlimefunGuguProject:InfinityExpansion:3c5db3650a")
    compileOnly("com.github.Sefiraat:Netheopoiesis:8d1af6c570")
    compileOnly("com.github.schntgaispock:SlimeHUD:1.2.7")
    compileOnly("com.bgsoftware:WildChestsAPI:2024.1")
    compileOnly("com.bgsoftware:WildStackerAPI:2023.2")
    compileOnly("dev.rosewood:rosestacker:1.5.23")
    compileOnly("com.gmail.nossr50.mcMMO:mcMMO:2.2.017") {
        exclude("com.sk89q.worldedit", "worldedit-bukkit")
        exclude("com.sk89q.worldedit", "worldedit-core")
        exclude("com.sk89q.worldguard", "worldguard-legacy")
        exclude("com.comphenix.protocol", "ProtocolLib")
    }
    compileOnly("com.github.balugaq:FluffyMachines:43d7444e4c")
    compileOnly("com.github.TimetownDev:GuguSlimefunLib:45627c6f8e")
    compileOnly("com.github.balugaq:JustEnoughGuide:7f21e113a2")
    compileOnly(fileTree(mapOf("dir" to "lib", "include" to listOf("*.jar"))))
}

tasks {
    compileJava {
        options.release.set(21)
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:-removal")
    }

    processResources {
        filesMatching("plugin.yml") {
            expand(project.properties)
        }
    }

    shadowJar {
        archiveBaseName.set("Networks-Legacy")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("")

        minimize()
        relocate("org.bstats", "io.github.sefiraat.networks.bstats")
        relocate("io.papermc.lib", "dev.sefiraat.cultivation.paperlib")
        exclude("META-INF/*")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        mergeServiceFiles()
    }

    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }
}

defaultTasks("clean", "build")
