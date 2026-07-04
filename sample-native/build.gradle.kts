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

dependencies {
    implementation("org.liamjd.apiviaduct:router:0.6.1-SNAPSHOT")

    // The router declares these as implementation dependencies, so a consumer
    // that references the AWS event classes directly must declare them itself
    implementation("com.amazonaws:aws-lambda-java-core:1.4.0")
    implementation("com.amazonaws:aws-lambda-java-events:3.16.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
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
