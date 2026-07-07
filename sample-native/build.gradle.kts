plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
    id("org.graalvm.buildtools.native") version "0.10.6"
    application
}

group = "org.liamjd.apiviaduct.sample"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    google()
}

// Build-time-only classpath for OpenAPI generation. Kept separate from `implementation` so the
// openapi generator (and its deps) never enter the runtime classpath or the native image.
val openApiGenerator: Configuration by configurations.creating

dependencies {
    implementation("org.liamjd.apiviaduct:router:0.6.1-SNAPSHOT")

    // The router declares these as implementation dependencies, so a consumer
    // that references the AWS event classes directly must declare them itself
    implementation("com.amazonaws:aws-lambda-java-core:1.4.0")
    implementation("com.amazonaws:aws-lambda-java-events:3.16.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    openApiGenerator("org.liamjd.apiviaduct:openapi:0.6.1-SNAPSHOT")
}

// Generates openapi.yaml from SampleRouter on the build JVM (never in the native image).
// Run `../gradlew :router:publishToMavenLocal :openapi:publishToMavenLocal` first.
tasks.register<JavaExec>("generateOpenApi") {
    group = "documentation"
    description = "Generates an OpenAPI document from SampleRouter"
    classpath = sourceSets.main.get().runtimeClasspath + openApiGenerator
    mainClass.set("org.liamjd.apiviaduct.schema.OpenApiCli")
    args(
        "org.liamjd.apiviaduct.sample.SampleRouter",
        layout.buildDirectory.file("openapi/openapi.yaml").get().asFile.path
    )
}

application {
    mainClass.set("org.liamjd.apiviaduct.sample.MainKt")
}

kotlin {
    jvmToolchain(21)
}

graalvmNative {
    binaries {
        named("main") {
            // AWS Lambda custom runtimes (provided.al2023) require the binary
            // to be named "bootstrap" at the root of the deployment zip
            imageName.set("bootstrap")
            buildArgs.add("--no-fallback")
            buildArgs.add("--initialize-at-build-time=kotlin")
            // http for the Lambda Runtime API loop (plain http inside the sandbox);
            // https for the CognitoAuthorizer's JWKS fetch
            buildArgs.add("--enable-url-protocols=http,https")
            buildArgs.add("-H:+ReportExceptionStackTraces")
        }
    }
}

tasks.register<Zip>("buildNativeLambdaZip") {
    dependsOn("nativeCompile")
    from(layout.buildDirectory.dir("native/nativeCompile")) {
        include("bootstrap")
    }
    archiveFileName.set("function.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
