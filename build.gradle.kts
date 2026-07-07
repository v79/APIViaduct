plugins {
    kotlin("jvm") version "1.9.23"
}

group = "org.liamjd.apiviaduct"
version = "0.7.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    mavenLocal()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}