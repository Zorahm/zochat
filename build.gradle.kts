plugins {
    java
    kotlin("jvm") version "2.1.0"
    id("com.gradleup.shadow") version "8.3.5"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "zorahm"
version = "3.0.1"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // PlaceholderAPI's repo moved to repo.helpch.at; the old extendedclip URL is now a 301 redirect
    // that the Gradle resolver times out on. Resolve against the canonical host instead.
    maven("https://repo.helpch.at/releases/")
    // DiscordSRV's own Nexus serves its 10 MB jar at a few KB/s; Modrinth's CDN has the identical
    // plugin jar. exclusiveContent keeps every other dependency from being looked up there.
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
}

configurations {
    testImplementation { extendsFrom(configurations.compileOnly.get()) }
    // DiscordSRV's plugin jar bundles an old, unrelocated SnakeYAML that shadows Paper's in tests and
    // breaks YamlConfiguration. No test touches DiscordSRV (only DiscordSrvHook does), so leave it out.
    testCompileClasspath { exclude(group = "maven.modrinth") }
    testRuntimeClasspath { exclude(group = "maven.modrinth") }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("me.clip:placeholderapi:2.11.6")
    // The plugin jar already carries its relocated JDA (github.scarsz.discordsrv.dependencies.jda).
    compileOnly("maven.modrinth:discordsrv:1.30.5") { isTransitive = false }
    implementation("com.mysql:mysql-connector-j:9.1.0")
    implementation(kotlin("stdlib"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    // Paper provides SQLite at runtime; tests need the real driver to exercise the repositories.
    testImplementation("org.xerial:sqlite-jdbc:3.49.1.0")
}

tasks.test { useJUnitPlatform() }

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

// The plain jar would land on the same zoChat-<version>.jar path as the shaded one (classifier ""),
// and the result depended on which task ran last — an unshaded jar crashes on missing Kotlin.
tasks.jar { enabled = false }

tasks.shadowJar {
    archiveBaseName.set("zoChat")
    archiveClassifier.set("")
    relocate("com.mysql", "zorahm.zochat.libs.mysql")
    relocate("com.google.protobuf", "zorahm.zochat.libs.protobuf")
    relocate("kotlin", "zorahm.zochat.libs.kotlin")
    mergeServiceFiles()
}

tasks.build { dependsOn(tasks.shadowJar) }

tasks.runServer { minecraftVersion("1.21.10") }
