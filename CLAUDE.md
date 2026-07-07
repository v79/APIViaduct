# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

APIViaduct is a Kotlin library for building serverless RESTful APIs on AWS Lambda (behind API Gateway). Routes are defined with a Kotlin DSL; JSON (de)serialization uses kotlinx.serialization, with YAML support via kaml. It is not a general web server framework.

## Build & Test Commands

```bash
./gradlew build                 # build everything
./gradlew test                  # run all tests
./gradlew :router:test          # tests for one module
./gradlew :router:test --tests "org.liamjd.apiviaduct.routing.RouterTest"                 # single test class
./gradlew :router:test --tests "org.liamjd.apiviaduct.routing.RouterTest.testMethodName"  # single test method
./gradlew :router:koverHtmlReport   # coverage report (router module only)
./gradlew :router:nativeTest    # run router tests as a GraalVM native image (requires a GraalVM JDK 21+; CI runs this in native-image.yml)
./gradlew publish               # publish to GitHub Packages (CI does this on v* tags)
```

JVM toolchain 17. Version (`0.6.1-SNAPSHOT`) is declared separately in the root, `router/`, and `openapi/` build.gradle.kts files — bump all three together.

The `sample-native/` project is a **standalone Gradle build**, deliberately not included in the root settings.gradle.kts (a sample inside the main build previously confused Gradle). It consumes the router from mavenLocal:

```bash
./gradlew :router:publishToMavenLocal
cd sample-native
../gradlew nativeCompile            # native binary (requires GraalVM JDK)
../gradlew buildNativeLambdaZip     # function.zip with the "bootstrap" binary for Lambda
```

## Hard Constraints

- **Kotlin is currently 1.9.23 across all modules**: this used to be a hard pin because the `openapi` module's KSP test dependency (kotlin-compile-testing) did not support KSP2. That processor and its test deps were removed in the issue #30 rewrite, so the KSP2 blocker is gone; the version is still 1.9.23 everywhere and should be bumped in lockstep, but a move to Kotlin 2.0 is no longer blocked by the OpenAPI module. (Note: Gradle 9.6.1 also wants Kotlin 2 and was deliberately not adopted — see commit history.)
- **No runtime reflection in the router**: `KType`/`typeOf<T>()`/`serializer(kType)` were deliberately removed for GraalVM native-image compatibility (see NATIVE_IMAGE_CHANGES.md). Serializers are captured at compile time via reified `serializer<I>()`/`serializer<T>()` at route registration (`RequestPredicate.inputSerializer` and `outputSerializer`) and in `Response` factory methods (`Response.outputSerializer`). Do not reintroduce reflection-based serializer lookup. Any class the router must access reflectively (e.g. `NoAuth`) has to be registered in the router's `reflect-config.json` (see below).

## Architecture

Two Gradle modules with distinct roles:

### `router` — the runtime library (`org.liamjd.apiviaduct.routing`)

Users extend the abstract class `LambdaRouter` (the AWS `RequestHandler` entry point) and supply a `router` property built with the `lambdaRouter { }` DSL.

Request flow: `LambdaRouter.handleRequest` (lowercases headers) → internal `LambdaRequestHandler.handleRequest` → `validateRoute` matches routes in order path → method → accept header, returning 404/405/406 respectively on failure → authorizer check (401) → `RouteProcessor.processRoute` deserializes the body and invokes the handler → `serializeResponse` encodes the `Response` per the negotiated `MimeType` and adds CORS headers.

Content negotiation: `MimeType.isCompatibleWith` understands wildcard and parameterised Accept headers (`*/*`, `text/*`, `application/xml;q=0.9`). The response Content-Type is the type negotiated with the matched route (`RequestPredicate.matchedAcceptType`), never a raw wildcard from the Accept header.

Key pieces:
- `Router` — the DSL (`get`, `post`, `put`, `patch`, `delete`). GET/DELETE don't consume a body; POST/PUT/PATCH capture `inputSerializer` at registration. All verbs capture `outputSerializer` at registration too (via `outputSerializerOrNull<T>()`, which is null when the handler only returns a body-less `Response.ok()` — `T` inferred as `Any`), driving OpenAPI response-schema generation. `group("/path") { }` prefixes routes; `auth(authorizer) { }` wraps routes with an `Authorizer` (interface in `Authorizer.kt`; JWT deps are available but `NoAuth` is the default). `RequestPredicate.spec { }` (in `RouteSpec.kt`) attaches optional OpenAPI prose to a route; the top-level `openApi { }` DSL (in `OpenApi.kt`) declares document-level info/servers on the router.
- `RequestPredicate` — one registered route: method + path pattern (path params in `{braces}`) + consumed/produced mime types. Defaults are JSON both ways; override per-route with the `expects(...)`/`supplies(...)` extension functions.
- `Requests.kt` / `Responses.kt` — `Request<I>` (typed body, path/query params) and `Response<T>` with factory methods (`Response.OK`, `Response.notFound`, ...).

### `sample-native` — a standalone consumer sample (not part of the root build)

Proves the GraalVM path end-to-end: a `LambdaRouter` subclass compiled with `nativeCompile` into a `bootstrap` binary for the Lambda `provided.al2023` custom runtime (deployed successfully to AWS). `Main.kt` implements the Lambda custom runtime event loop (polls the Runtime API when `AWS_LAMBDA_RUNTIME_API` is set; runs a built-in self-test otherwise). `EventJson.kt` is a Jackson-free bridge: hand-rolled `@Serializable` DTOs deserialize the API Gateway JSON and copy fields into the AWS event classes. `CognitoAuthorizer.kt` secures `/secure/hello` via the `auth { }` DSL by validating Cognito access tokens the same Jackson-free way (kotlinx.serialization + JDK crypto for RS256, JWKS fetched over HTTPS and cached) — proven end-to-end on AWS in the native image, with no reflection config needed. `infra/` holds a minimal OpenTofu/Terraform deployment including the Cognito user pool, client, and test user (password supplied at apply time via `-var test_user_password=…`).

### `openapi` — a runtime OpenAPI generator (`org.liamjd.apiviaduct.schema`)

Reflection-free and reads a live `Router` (depends on `:router`). Replaced the old KSP annotation processor in the issue #30 rewrite. Pipeline: `SchemaGenerator` walks kotlinx.serialization `SerialDescriptor`s (compile-time generated, native-image safe) into a `SchemaModel` tree, emitting nested `@Serializable` types as `components/schemas` `$ref`s; `OpenApiGenerator(router)` stitches paths/methods/media-types/params/request+response schemas from the registered routes, layering hand-authored prose from each route's `RouteSpec` (the `spec { }` DSL); `YamlEmitter` renders the document tree to YAML. Document-level `info`/`servers` come from the router's `openApi { }` DSL (model + builder in the router module's `OpenApi.kt`, stored as `Router.openApiInfo`); an `infoOverride` can be passed to `OpenApiGenerator` to supply/replace them programmatically. Only a subset of the OpenAPI 3.1 spec is targeted — OpenAPISpecNotes.md lists which fields, in priority order. Known simplification: nullable schemas emit `nullable: true` (3.0 style), not the strict 3.1 `type: [..., "null"]` form.

## GraalVM Native Image

APIViaduct is a **library**: it is never compiled to a native image itself; consumer Lambda projects are. The router's job is to be native-image *safe* and to ship reachability metadata that consumers pick up automatically from `router/src/main/resources/META-INF/native-image/org.liamjd.apiviaduct/router/` (`reflect-config.json` registers `NoAuth`; `native-image.properties` adds `--enable-url-protocols=https` for jwks-rsa). Consumers must register their own `Authorizer` implementations in their own reflect-config.

- `./gradlew :router:nativeTest` compiles the router test suite to a native executable and runs it — the primary native-compatibility check. All router tests pass. Requires GraalVM; CI runs it (`.github/workflows/native-image.yml`, currently triggered on `*graalvm*` branches).
- Native builds use `--no-fallback` and `--initialize-at-build-time=kotlin` (the JUnit native feature conflicts with run-time init of Kotlin annotation classes otherwise).
- The remaining blocker is the consumer's runtime layer, not the library: `aws-lambda-java-events` only invokes Jackson when the managed runtime deserializes events, which the sample sidesteps with its custom runtime loop. `GRAALVM_RECOMMENDATIONS.md` tracks status (sections annotated DONE/STALE/OPEN — trust the annotations over the original text); `GRAALVM_CONSUMER_GUIDE.md` is the user-facing build guide.
