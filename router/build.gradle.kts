plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.20"
    id("org.jetbrains.kotlinx.kover") version "0.8.0"
    `maven-publish`
}

group = "org.liamjd.apiviaduct"
version = "0.5.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    mavenLocal()
}

dependencies {
    // serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.charleskorn.kaml:kaml:0.59.0")

    // aws kotlin sdk
    implementation(platform("aws.sdk.kotlin:bom:1.2.14"))

    // aws lambda functions
    implementation("aws.sdk.kotlin:lambda")

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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/${project.findProperty("githubOwner") ?: System.getenv("GITHUB_REPOSITORY")?.substringBefore('/') ?: "OWNER"}/${project.findProperty("githubRepo") ?: System.getenv("GITHUB_REPOSITORY")?.substringAfter('/') ?: "REPO"}")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}