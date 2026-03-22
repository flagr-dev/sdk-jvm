plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("com.vanniktech.maven.publish") version "0.29.0"
}

group = "dev.flagr"
version = System.getenv("SDK_VERSION") ?: "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // OpenFeature provider interface (optional — consumers can omit if not using OF)
    compileOnly("dev.openfeature:sdk:1.11.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("dev.openfeature:sdk:1.11.0")
}

kotlin {
    jvmToolchain(11)
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates("dev.flagr", "flagr-sdk", version.toString())

    pom {
        name.set("Flagr JVM SDK")
        description.set("Kotlin/Java SDK for flagr.dev — zero-latency feature flag evaluation via local cache + SSE")
        url.set("https://github.com/flagr-dev/flagr")
        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("flagr")
                name.set("Flagr")
                email.set("hello@flagr.dev")
            }
        }
        scm {
            connection.set("scm:git:https://github.com/flagr-dev/flagr.git")
            developerConnection.set("scm:git:https://github.com/flagr-dev/flagr.git")
            url.set("https://github.com/flagr-dev/flagr")
        }
    }
}
