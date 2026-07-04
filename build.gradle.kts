plugins {
    kotlin("jvm") version "1.9.23"
}

group = "org.liamjd.apiviaduct"
version = "0.6.1-SNAPSHOT"

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