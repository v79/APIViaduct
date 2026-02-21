# GraalVM Native Image Recommendations for APIViaduct

## Executive Summary

APIViaduct has already completed the most critical step toward GraalVM native image
compatibility: replacing runtime `KType` reflection with compile-time `KSerializer<T>`
instances. This document identifies what remains to be done, with the most significant
blocker being the `aws-lambda-java-events` library's internal use of Jackson.

---

## 1. Understand the Architecture: Library vs Application

**APIViaduct is a library, not an application.** This is the most important framing
for any GraalVM work.

- The library itself does not get compiled to a native image.
- Instead, the library must be **native-image compatible** so that *consumer* Lambda
  functions can be compiled as native images.
- Consumers (Lambda handlers that extend `LambdaRouter`) add the native image Gradle
  plugin to their own build; the library provides **reachability metadata** to help.

This means the work has two tracks:
1. Make the `router` library fully native-image safe (no runtime reflection, correct metadata).
2. Document how consumers build their Lambda functions as native images.

---

## 2. Critical Blocker: `aws-lambda-java-events` Uses Jackson

The single biggest obstacle to native image compatibility is this dependency in
`router/build.gradle.kts`:

```kotlin
implementation("com.amazonaws:aws-lambda-java-events:3.11.5")
```

`aws-lambda-java-events` uses **Jackson** (`jackson-databind`) internally to deserialize
incoming Lambda event payloads (i.e., `APIGatewayProxyRequestEvent`). Jackson is
reflection-heavy and requires extensive reflection configuration for native image.
Without that configuration, the native executable will fail at runtime trying to
deserialize incoming API Gateway events.

### Option A: Provide Jackson reflection configuration (more work)

Add a `reflect-config.json` under:
```
router/src/main/resources/META-INF/native-image/org.liamjd.apiviaduct/router/reflect-config.json
```

This file must enumerate every field and constructor of `APIGatewayProxyRequestEvent`,
`APIGatewayProxyResponseEvent`, and all their nested types. This is fragile—it breaks
when the library updates.

### Option B: Switch to the AWS Lambda Kotlin SDK (recommended)

The project already imports `aws.sdk.kotlin:lambda` (the Kotlin SDK). The Kotlin SDK
uses `smithy-kotlin` for marshalling, which has much better native image support.
There is an ongoing effort in the AWS ecosystem to provide a Jackson-free Lambda
runtime for native image.

A concrete path is to adopt
[`aws-lambda-java-runtime-interface-client`](https://github.com/aws/aws-lambda-java-libs/tree/main/aws-lambda-java-runtime-interface-client)
(RIC) with a custom bootstrap script, which avoids the Jackson dependency in event
deserialization.

---

## 3. Auth0 Libraries Are Also Jackson-Dependent

```kotlin
implementation("com.auth0:java-jwt:4.4.0")
implementation("com.auth0:jwks-rsa:0.22.1")
```

Both Auth0 libraries use Jackson internally for JSON parsing. This compounds the
Jackson reflection configuration problem. Alternatives to consider:

- **`java-jwt` replacement**: Use `com.nimbusds:nimbus-jose-jwt` which has native
  image support, or `io.jsonwebtoken:jjwt` with its native image configuration.
- **`jwks-rsa` replacement**: Nimbus SDK also handles JWKS natively.

Alternatively, add explicit Jackson reflection configuration for the Auth0 types, but
this is fragile and version-sensitive.

---

## 4. Log4j2 Plugin Discovery

```kotlin
runtimeOnly("com.amazonaws:aws-lambda-java-log4j2:1.6.0")
```

Log4j2 uses a **classpath scanning and reflection-based plugin system** that is
incompatible with native image by default. Native image cannot scan the classpath at
runtime.

### Fix

Add a `Log4j2Plugins.dat` pre-generated cache file, which Log4j2 can use instead of
classpath scanning. This is generated with the `log4j-core` annotation processor
during the build. Alternatively, switch to a simpler logging approach:

- `java.util.logging` (included in native image, no configuration needed)
- `SLF4J` with `logback-classic`, which has native image support
- The Lambda `context.logger` API (already used in `LambdaRequestHandler.kt`) is the
  most native-image-friendly option—consider relying on it exclusively

---

## 5. Add the GraalVM Native Image Gradle Plugin (to consumer builds)

Consumers of APIViaduct who want to build native Lambda functions should add:

```kotlin
// In consumer's build.gradle.kts
plugins {
    id("org.graalvm.buildtools.native") version "0.10.4"
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("bootstrap")         // Lambda requires the binary to be named "bootstrap"
            mainClass.set("com.example.MyLambdaHandlerKt")
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("--initialize-at-build-time=kotlin")
        }
    }
}
```

The library itself should add this plugin to the **test** configuration in `router`
to run native image tests as part of CI:

```kotlin
// router/build.gradle.kts
plugins {
    id("org.graalvm.buildtools.native") version "0.10.4"
}

graalvmNative {
    testSupport.set(true)
}
```

---

## 6. Provide GraalVM Reachability Metadata in the Library

Create the metadata directory structure in the `router` module:

```
router/src/main/resources/META-INF/native-image/
  org.liamjd.apiviaduct/
    router/
      native-image.properties
      reflect-config.json
      resource-config.json
      proxy-config.json
```

### `native-image.properties`
```properties
Args = --initialize-at-build-time=org.liamjd.apiviaduct.routing \
       -H:ResourceConfigurationResources=${.}/resource-config.json \
       -H:ReflectionConfigurationResources=${.}/reflect-config.json
```

### `reflect-config.json` (minimum needed)
The `Authorizer` interface and its implementations need reflection registration
because they are instantiated by class name in the `auth {}` DSL block:

```json
[
  {
    "name": "org.liamjd.apiviaduct.routing.NoAuth",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  }
]
```

Custom `Authorizer` implementations will need to be registered by consumers.

---

## 7. Upgrade Kotlin Version

The `router` module is on Kotlin 1.9.23 (`kotlin("jvm") version "1.9.23"`). The
`openapi` module cannot yet upgrade to Kotlin 2.0 due to the KSP2 testing library
blocker ([kotlin-compile-testing#340](https://github.com/ZacSweers/kotlin-compile-testing/issues/340)).

However, **the `router` module has no such constraint** and can be upgraded
independently. Kotlin 2.0+ provides:

- The K2 compiler with better inlining, which benefits the `inline reified` serializer
  capture pattern already in use
- Better dead-code elimination, reducing native image size
- Improved native image support via K2 compiler metadata

To upgrade the `router` module independently:
```kotlin
// router/build.gradle.kts
plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    // ...
}
```

The `openapi` module can remain on 1.9.x until the testing library issue is resolved.

---

## 8. Lambda Native Image Deployment Requirements

Building a native image for AWS Lambda has specific requirements that differ from
standard native image builds:

### Build environment
- The native image **must be compiled on Linux** targeting the same architecture as
  the Lambda runtime (x86_64 or arm64/aarch64).
- Cross-compilation is not supported; use Linux CI (GitHub Actions `ubuntu-latest`).
- GraalVM CE 21+ (or Oracle GraalVM 21+) is required. GraalVM 21 supports Java 21,
  which is a good target since Lambda now supports Java 21.

### Lambda runtime configuration
- Set the Lambda runtime to `provided.al2023` (custom runtime), not `java17` or `java21`.
- The native binary must be named `bootstrap` and placed in the deployment ZIP root.
- A typical deployment package structure:
  ```
  bootstrap          ← the native executable
  ```

### Build task to create the ZIP
```kotlin
// In consumer's build.gradle.kts
tasks.register<Zip>("buildNativeLambdaZip") {
    dependsOn("nativeCompile")
    from(layout.buildDirectory.dir("native/nativeCompile")) {
        include("bootstrap")
    }
    archiveFileName.set("function.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
```

---

## 9. Update the CI/CD Workflow for Native Image Builds

The current `publish.yml` uses JDK 17 (Temurin). A native image build workflow
should use GraalVM and target Linux:

```yaml
# .github/workflows/native-build.yml
name: Native Image Build

on:
  workflow_dispatch:
  push:
    tags:
      - 'v*-native'

jobs:
  native-build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up GraalVM
        uses: graalvm/setup-graalvm@v1
        with:
          java-version: '21'
          distribution: 'graalvm-community'
          github-token: ${{ secrets.GITHUB_TOKEN }}

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build native image
        run: ./gradlew :router:nativeCompile

      - name: Run native tests
        run: ./gradlew :router:nativeTest
```

---

## 10. kotlinx.serialization Is Already Compatible

The recent change to compile-time `KSerializer<T>` capture is the correct approach.
`kotlinx.serialization` works with native image when using the Gradle plugin
(`kotlin("plugin.serialization")`), which generates serializer factories at compile
time and registers them statically. No additional configuration is needed for
the serialization layer.

The `@Serializable` annotation on `Response<T>` and `@Transient` on `outputSerializer`
are both compatible with native image.

---

## 11. KAML (YAML) Compatibility

`com.charleskorn.kaml:kaml:0.59.0` is built on `kotlinx.serialization`, so it
follows the same compatibility rules. It should work with native image without
additional configuration. Verify by running native image tests that exercise YAML
responses.

---

## 12. The `openapi` Module (KSP Processor)

The `openapi` module is a **build-time KSP annotation processor**, not a runtime
dependency. It runs during compilation of consumer projects and produces YAML files.
**It does not need to be native-image compatible at all.** KSP processors run on the
JVM during the build and are never part of the native executable.

The `openapi` module's Kotlin version can remain at 1.9.x independently.

---

## Summary: Prioritized Action List

| Priority | Action | Difficulty |
|----------|--------|------------|
| 1 (blocker) | Resolve Jackson dependency from `aws-lambda-java-events` | High |
| 2 (blocker) | Replace Auth0 libraries with native-image-compatible alternatives | Medium |
| 3 | Fix Log4j2 plugin discovery or switch to simpler logging | Low–Medium |
| 4 | Add GraalVM Native Image Gradle plugin to `router` test config | Low |
| 5 | Create `META-INF/native-image/` reachability metadata in `router` | Medium |
| 6 | Upgrade `router` module to Kotlin 2.0+ | Low |
| 7 | Add native image GitHub Actions workflow | Low |
| 8 | Document consumer build requirements (bootstrap binary, ZIP packaging) | Low |
| 9 | Run and validate native image tests | Medium |

The Jackson problem (items 1 and 2) is the gating issue. Everything else is
straightforward configuration work once those dependencies are resolved.
