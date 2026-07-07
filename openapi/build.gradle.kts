plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.23"
    `maven-publish`
}

group = "org.liamjd.apiviaduct"
version = "0.6.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // kotlinx.serialization descriptors drive the reflection-free schema generation (issue #30)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3")

    // the generator reads registered routes (RequestPredicate, RouteSpec, serializers) at runtime
    implementation(project(":router"))

    // OpenApiCli instantiates a LambdaRouter, whose supertype is the AWS RequestHandler; :router
    // hides the AWS deps (implementation-scoped), so declare it here — needed at both compile and
    // runtime, and inherited by the test classpath (testImplementation extends implementation)
    implementation("com.amazonaws:aws-lambda-java-core:1.4.0")

    testImplementation(kotlin("test"))
    // the sample router (test sources) reads the raw API Gateway event (queryStringParameters etc.)
    testImplementation("com.amazonaws:aws-lambda-java-events:3.16.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
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