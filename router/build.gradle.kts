plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    `maven-publish`
}

group = "org.liamjd.apiviaduct"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    mavenLocal()
}

dependencies {
    // serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0-RC")
    implementation("com.charleskorn.kaml:kaml:0.59.0")

    // aws sdk v2
    implementation(platform("software.amazon.awssdk:bom:2.25.55"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:lambda")
    implementation("software.amazon.awssdk:sqs")

    // aws lambda functions
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.5")

    // auth
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("com.auth0:jwks-rsa:0.22.1")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}