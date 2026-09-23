plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

group = "com.telestream"
version = "1.0.0"

allprojects {
    repositories {
        mavenCentral()
        google()
    }
}

val ktorVersion = "3.0.1"

dependencies {
    // Android Compatibility & Full CloudStream Library
    implementation(project(":android-compat"))
    implementation(project(":cloudstream3"))

    // Kotlin & Coroutines
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Ktor Server (Embedded lightweight HTTP server for /health and webhooks)
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")

    // Ktor Client (Async HTTP for Telegram Bot API & scrapers)
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // Web Scraping & Network (matching CloudStream libraries)
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.1")
    implementation("org.json:json:20240303")
    implementation("com.google.code.gson:gson:2.10.1")

    // SQLite Persistence
    implementation("org.xerial:sqlite-jdbc:3.47.0.0")

    // Dex to Jar translation for real CloudStream .cs3 plugin execution
    implementation("de.femtopedia.dex2jar:dex-translator:2.4.36")
    implementation("de.femtopedia.dex2jar:dex-tools:2.4.36")
    implementation("org.ow2.asm:asm:9.7.1")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.12")

    // Unit Testing
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

application {
    mainClass.set("com.telestream.MainKt")
    applicationDefaultJvmArgs = listOf("-noverify", "-Xverify:none")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-noverify", "-Xverify:none")
}

// Fat JAR task for 1-click self-contained deployment
tasks.register<Jar>("fatJar") {
    archiveBaseName.set("telestream")
    archiveClassifier.set("all")
    archiveVersion.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.telestream.MainKt"
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}
