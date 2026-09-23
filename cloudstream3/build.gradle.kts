plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()

    sourceSets {
        all {
            languageSettings {
                optIn("com.lagradost.cloudstream3.InternalAPI")
                optIn("com.lagradost.cloudstream3.Prerelease")
                optIn("kotlin.uuid.ExperimentalUuidApi")
                optIn("kotlin.io.encoding.ExperimentalEncodingApi")
            }
        }

        val commonMain by getting {
            dependencies {
                implementation(project(":android-compat"))
                implementation("androidx.annotation:annotation:1.7.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.5.4")
                implementation("org.jetbrains.kotlinx:atomicfu:0.25.0")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.1")
                implementation("org.json:json:20240303")
                implementation("com.google.code.gson:gson:2.10.1")
                implementation("org.jsoup:jsoup:1.18.1")
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
                implementation("io.ktor:ktor-http:3.0.1")
                implementation("org.mozilla:rhino:1.7.15")
                implementation("dev.whyoleg.cryptography:cryptography-core:0.4.0")
                implementation("dev.whyoleg.cryptography:cryptography-provider-jdk:0.4.0")
                implementation("org.jetbrains.kotlin:kotlin-reflect")
            }
        }
        val jvmCommonMain by creating {
            dependsOn(commonMain)
        }
        val jvmMain by getting {
            dependsOn(jvmCommonMain)
        }
    }
}
