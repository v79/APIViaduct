plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.23"
    `maven-publish`
}

group = "org.liamjd.apiviaduct"
version = "0.4-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // KSP
    implementation("com.google.devtools.ksp:symbol-processing-api:1.9.20-1.0.14")


    testImplementation(kotlin("test"))
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.5.0")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing-ksp:1.6.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}