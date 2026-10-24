plugins {
    java
    kotlin("jvm") version "2.1.0"
    id("com.gradleup.shadow") version "8.3.5"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "zorahm"
version = "2.0.0"

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
}

configurations {
    testImplementation { extendsFrom(configurations.compileOnly.get()) }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("me.clip:placeholderapi:2.11.6")
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
