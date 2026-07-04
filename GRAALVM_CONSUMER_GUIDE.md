# Building an APIViaduct Lambda as a GraalVM Native Image

APIViaduct is a library: it is not compiled to a native image itself. Instead, your
Lambda function (the project containing your `LambdaRouter` subclass) is compiled to
a native executable. This guide covers what your project needs.

> **Status:** native image support is a work in progress. The library's serialization
> layer is native-image safe (compile-time `KSerializer` capture, no runtime
> reflection), but the `aws-lambda-java-events` and Auth0 dependencies still rely on
> Jackson and require reflection configuration or replacement. See
> `GRAALVM_RECOMMENDATIONS.md` for the current state.

## Requirements

- **GraalVM CE 21+** (or Oracle GraalVM 21+) as the build JDK.
- **Linux build environment** matching the Lambda architecture (x86_64 or arm64).
  Cross-compilation is not supported — build on Linux CI (e.g. GitHub Actions
  `ubuntu-latest`) or in a matching Docker container.
- Lambda runtime set to **`provided.al2023`** (custom runtime), not `java17`/`java21`.

## Gradle setup

```kotlin
// build.gradle.kts of your Lambda project
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.graalvm.buildtools.native") version "0.10.4"
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("bootstrap")   // Lambda requires the binary to be named "bootstrap"
            mainClass.set("com.example.MyLambdaHandlerKt")
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("--initialize-at-build-time=kotlin")
        }
    }
}
```

## Packaging for deployment

The native binary must be named `bootstrap` and sit at the root of the deployment ZIP:

```kotlin
tasks.register<Zip>("buildNativeLambdaZip") {
    dependsOn("nativeCompile")
    from(layout.buildDirectory.dir("native/nativeCompile")) {
        include("bootstrap")
    }
    archiveFileName.set("function.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
```

Deploy `function.zip` with the runtime set to `provided.al2023`.

## Reflection configuration for your code

The router library ships its own reachability metadata under
`META-INF/native-image/org.liamjd.apiviaduct/router/`, which native-image picks up
automatically. Your project must additionally register:

- **Custom `Authorizer` implementations** — add them to your own
  `reflect-config.json` under `src/main/resources/META-INF/native-image/<your
  group>/<your artifact>/`:

  ```json
  [
    {
      "name": "com.example.MyCustomAuthorizer",
      "allDeclaredConstructors": true,
      "allDeclaredMethods": true
    }
  ]
  ```

- Any classes of your own that are accessed reflectively. `@Serializable` classes
  used in request/response bodies do **not** need registration — kotlinx.serialization
  generates their serializers at compile time.

In practice custom authorizers have not needed reflection registration: the
`sample-native` project's `CognitoAuthorizer` (a Jackson-free Cognito JWT validator
using kotlinx.serialization and JDK crypto) runs in a native image on Lambda with no
`reflect-config.json` entry at all.

An authorizer that fetches keys over HTTPS (as `CognitoAuthorizer` does for JWKS)
does need the `https` URL protocol enabled in the image — add it to your own
`buildArgs`, since the library no longer enables it for you:

```kotlin
buildArgs.add("--enable-url-protocols=https")
```

If the native binary fails at runtime with `ClassNotFoundException` or
`MissingReflectionRegistrationError`, run your test suite with the native-image
agent to generate the missing configuration:

```
./gradlew -Pagent test
```
