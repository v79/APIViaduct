plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "org.liamjd.apiviaduct"
version = "0.3-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // KSP
    implementation("com.google.devtools.ksp:symbol-processing-api:1.9.20-1.0.14")

    testImplementation(kotlin("test"))
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