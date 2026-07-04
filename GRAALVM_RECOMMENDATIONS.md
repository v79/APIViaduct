# GraalVM Native Image Recommendations for APIViaduct

> **Status update (2026-07-02):** items 5, 6, 8 (documentation) and 9 are now
> implemented on this branch, and `:router:nativeTest` passes in CI — all 40 router
> tests run successfully inside a GraalVM CE 21 native image (52.86 MB binary, 12 ms
> test run). Sections below are annotated with ✅ DONE, ⚠️ STALE (claims corrected by
> what we learned), or ⏳ OPEN. The original text is retained for reference.

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

> **⚠️ STALE (2026-07-02): softer than originally framed.** The native test run
> proved that the event classes themselves link into a native image without any
> reflection configuration — all 40 tests pass, constructing
> `APIGatewayProxyRequestEvent` programmatically. Jackson only executes when the
> *Lambda runtime layer* deserializes the incoming event JSON, which the library
> never does itself. So this is a blocker for the **consumer's runtime setup**
> (custom runtime / runtime interface client), not for the library. It remains
> unproven end-to-end; the next validation step is a minimal consumer Lambda
> compiled with `nativeCompile` and invoked with a real API Gateway payload.
>
> **⚠️ Option B below is also misleading:** `aws.sdk.kotlin:lambda` is the SDK
> *management* client (for creating/invoking functions via the AWS API), not a
> runtime event model — it is not a replacement for `aws-lambda-java-events`.
> Worse, it drags OkHttp 5 (alpha) and Gson into every consumer's native image
> (visible as `OkHttpFeature`/`GsonFeature` in the build log) and should probably
> be **removed** from the router's dependencies if nothing uses it. The realistic
> paths are hand-rolled `@Serializable` event classes or the RIC approach.

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

> **⏳ OPEN for Auth0 itself, but the alternative is proven (2026-07-04):** the
> sample-native project now validates real AWS Cognito access tokens inside a
> native image on Lambda, end-to-end — Bearer JWT parsed, claims checked, JWKS
> fetched **over HTTPS** and the RS256 signature verified, all with a hand-rolled
> Jackson-free authorizer (`sample-native/.../CognitoAuthorizer.kt`:
> kotlinx.serialization + JDK crypto, no Auth0 involvement). Two consequences:
>
> - The router's shipped `--enable-url-protocols=https` metadata is confirmed to
>   reach consumer native images and enable the JWKS fetch. No reflect-config was
>   needed for the custom authorizer.
> - The Auth0 (`java-jwt`/`jwks-rsa`) Jackson question itself remains untested in
>   a native image. Given the hand-rolled path works in ~120 lines, consider
>   whether the router should keep the Auth0 dependencies at all.

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

> **⚠️ STALE (2026-07-02):** the `aws-lambda-java-log4j2` dependency lives in the
> **root** project's `build.gradle.kts`, not in the published `router` module, so
> it is not on any consumer's classpath and does not affect native image builds of
> consumer Lambdas. It appears to be vestigial and could simply be removed. The
> advice below only becomes relevant if a consumer adds Log4j2 themselves.

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

> **✅ DONE (2026-07-02):** the plugin (0.10.4) is applied in
> `router/build.gradle.kts` with `testSupport` enabled. One correction to the
> snippet below: the `graalvmNative { testSupport }` config alone was not enough —
> `--initialize-at-build-time=kotlin` was also required, because the JUnit
> platform native feature initializes Kotlin annotation classes during image
> build. Consumer-facing setup is documented in `GRAALVM_CONSUMER_GUIDE.md`.

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

> **✅ DONE (2026-07-02), with corrections.** The metadata directory exists at
> `router/src/main/resources/META-INF/native-image/org.liamjd.apiviaduct/router/`
> containing `reflect-config.json` (registers `NoAuth`) and
> `native-image.properties` (adds `--enable-url-protocols=https` for jwks-rsa).
> Two corrections to the original text below:
>
> - The suggested `-H:ResourceConfigurationResources`/`-H:ReflectionConfigurationResources`
>   args are unnecessary — native-image **auto-discovers** `reflect-config.json`
>   etc. in the `META-INF/native-image/<group>/<artifact>/` directory.
> - The claim that authorizers are "instantiated by class name" is wrong: the
>   `auth {}` DSL takes already-constructed instances, so no reflective
>   instantiation happens in the library. The `NoAuth` registration is cheap
>   insurance, not a hard requirement — confirmed by the passing native tests.
> - `resource-config.json` and `proxy-config.json` were not needed and were not
>   created.

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

> **⚠️ STALE — the premise is wrong (2026-07-02).** The Kotlin version is declared
> once in the **root** `build.gradle.kts` and shared by both modules; Gradle loads
> a single Kotlin plugin version per build, so the `router` module **cannot**
> upgrade to Kotlin 2.x independently while `openapi` is held back by the KSP2
> testing blocker. This item is deferred until kotlin-compile-testing supports
> KSP2 (or the build is restructured). Note also that native tests pass fine on
> Kotlin 1.9.23 — the upgrade is an optimization, not a compatibility requirement.
> (The serialization plugin version was aligned to 1.9.23 as a minor cleanup.)

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

> **✅ DONE (documentation) (2026-07-02):** this material is now maintained in
> `GRAALVM_CONSUMER_GUIDE.md`. The requirements themselves remain valid but are
> untested until an end-to-end consumer Lambda deployment is attempted.

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

> **✅ DONE (2026-07-02), with corrections.** Implemented as
> `.github/workflows/native-image.yml` and passing in CI. Corrections to the
> draft below:
>
> - `./gradlew :router:nativeCompile` is wrong for a library — there is no main
>   class, so no "main" binary. The workflow runs `:router:nativeTest` only.
> - The workflow triggers on pushes to `*graalvm*` branches rather than tags,
>   because `workflow_dispatch` is only available once the workflow file exists
>   on the default branch. Swap to a normal push/pull_request trigger after merge.

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

> **✅ CONFIRMED (2026-07-02):** validated by the passing native test run — every
> JSON serialization/deserialization test succeeds in the native image with no
> extra configuration.

The recent change to compile-time `KSerializer<T>` capture is the correct approach.
`kotlinx.serialization` works with native image when using the Gradle plugin
(`kotlin("plugin.serialization")`), which generates serializer factories at compile
time and registers them statically. No additional configuration is needed for
the serialization layer.

The `@Serializable` annotation on `Response<T>` and `@Transient` on `outputSerializer`
are both compatible with native image.

---

## 11. KAML (YAML) Compatibility

> **✅ CONFIRMED (2026-07-02):** the YAML request/response tests pass in the native
> image. Note snakeyaml-engine shows up as one of the larger contributors to the
> image code area (~450 kB) — acceptable, but worth knowing.

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

| Priority | Action | Difficulty | Status (2026-07-02) |
|----------|--------|------------|---------------------|
| 1 (blocker) | Resolve Jackson dependency from `aws-lambda-java-events` | High | ⏳ Open — but downgraded: only affects consumer runtime event deserialization, not the library (see §2) |
| 2 (blocker) | Replace Auth0 libraries with native-image-compatible alternatives | Medium | ⏳ Open — links fine in native image; JWT/JWKS path not yet exercised (see §3) |
| 3 | Fix Log4j2 plugin discovery or switch to simpler logging | Low–Medium | ⚠️ Stale — dependency is only in the root project, not the published library (see §4) |
| 4 | Add GraalVM Native Image Gradle plugin to `router` test config | Low | ✅ Done |
| 5 | Create `META-INF/native-image/` reachability metadata in `router` | Medium | ✅ Done (smaller than expected — see §6) |
| 6 | Upgrade `router` module to Kotlin 2.0+ | Low | ⚠️ Blocked — premise wrong; single root Kotlin version, held back by openapi/KSP2 (see §7) |
| 7 | Add native image GitHub Actions workflow | Low | ✅ Done and passing |
| 8 | Document consumer build requirements (bootstrap binary, ZIP packaging) | Low | ✅ Done — `GRAALVM_CONSUMER_GUIDE.md` |
| 9 | Run and validate native image tests | Medium | ✅ Done — 40/40 tests pass in the native image |
| new | Remove `aws.sdk.kotlin:lambda` if unused (drags OkHttp 5 alpha + Gson into images) | Low | ⏳ Open |
| new | End-to-end proof: minimal consumer Lambda, `nativeCompile`, real API Gateway payload | Medium | ✅ **Done, deployed to AWS (2026-07-02)** — `sample-native/` deployed via OpenTofu to eu-west-2 (provided.al2023 + HTTP API payload v1). All routes work: **186 ms init, 1–2 ms warm requests, 42 MB memory**. Event JSON deserialized via the Jackson-free kotlinx bridge. Deployment surfaced a router content-negotiation bug (`Accept: */*` rejected with 406), fixed in the library |

~~The Jackson problem (items 1 and 2) is the gating issue.~~ **Revised:** the
library itself is now demonstrably native-image compatible. The remaining work is
proving the *consumer runtime* story end-to-end (event deserialization, JWT auth)
and trimming unnecessary dependencies.
