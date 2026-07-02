// Standalone build, deliberately NOT included in the root settings.gradle.kts —
// the previous Sample project confused Gradle when it was part of the main build.
// It consumes the router library from mavenLocal; run ../gradlew :router:publishToMavenLocal first.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "sample-native"
