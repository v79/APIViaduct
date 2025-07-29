plugins {
    kotlin("jvm") version "1.9.23"
}

group = "org.liamjd"
version = "0.4.1-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    mavenLocal()
}

dependencies {
    testImplementation(kotlin("test"))
    runtimeOnly("com.amazonaws:aws-lambda-java-log4j2:1.6.0")

}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}