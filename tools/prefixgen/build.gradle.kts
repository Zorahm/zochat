// Standalone generator for the rank-badge resource packs (one per style); not part of the plugin jar.
// Run: ./gradlew :prefixgen:run
plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

application {
    mainClass.set("zorahm.zochat.prefixgen.MainKt")
}

val badgeTable = rootProject.file("src/main/resources/badges.yml")

tasks.test {
    useJUnitPlatform()
    systemProperty("zochat.badgeTable", badgeTable.absolutePath)
}

tasks.named<JavaExec>("run") {
    jvmArgs("-Djava.awt.headless=true")
    // The glyph table goes straight into the plugin's resources, so the jar and the packs built
    // from the same commit never disagree on which character is which badge.
    args(
        "--packs", layout.buildDirectory.dir("resourcepack").get().asFile.absolutePath,
        "--table", badgeTable.absolutePath,
        "--preview", layout.buildDirectory.dir("preview").get().asFile.absolutePath,
    )
}
