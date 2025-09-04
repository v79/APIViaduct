plugins {
    kotlin("jvm") version "1.9.23"
}

group = "org.liamjd.apiviaduct"
version = "0.4.2-SNAPSHOT"

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