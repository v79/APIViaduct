plugins {
    kotlin("jvm") version "1.9.23"
}

group = "org.liamjd"
version = "0.2-SNAPSHOT"

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
    jvmToolchain(17)
}