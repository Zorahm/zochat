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
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
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
}

tasks.test { useJUnitPlatform() }

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

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
