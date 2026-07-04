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
./gradlew publish               # publish to GitHub Packages (CI does this on v* tags)
```

JVM toolchain 17. Version (`0.5.0-SNAPSHOT`) is declared separately in the root, `router/`, and `openapi/` build.gradle.kts files — bump all three together.

## Hard Constraints

- **Kotlin is pinned to 1.9.x, not 2.0**: the `openapi` module's test dependency (kotlin-compile-testing) does not support KSP2. See https://github.com/ZacSweers/kotlin-compile-testing/issues/340.
- **No runtime reflection in the router**: `KType`/`typeOf<T>()`/`serializer(kType)` were deliberately removed for GraalVM native-image compatibility (see NATIVE_IMAGE_CHANGES.md). Serializers are captured at compile time via reified `serializer<I>()` at route registration (`RequestPredicate.inputSerializer`) and in `Response` factory methods (`Response.outputSerializer`). Do not reintroduce reflection-based serializer lookup.

## Architecture

Two Gradle modules with distinct roles:

### `router` — the runtime library (`org.liamjd.apiviaduct.routing`)

Users extend the abstract class `LambdaRouter` (the AWS `RequestHandler` entry point) and supply a `router` property built with the `lambdaRouter { }` DSL.

Request flow: `LambdaRouter.handleRequest` (lowercases headers) → internal `LambdaRequestHandler.handleRequest` → `validateRoute` matches routes in order path → method → accept header, returning 404/405/406 respectively on failure → authorizer check (401) → `RouteProcessor.processRoute` deserializes the body and invokes the handler → `serializeResponse` encodes the `Response` per the negotiated `MimeType` and adds CORS headers.

Key pieces:
- `Router` — the DSL (`get`, `post`, `put`, `patch`, `delete`). GET/DELETE don't consume a body; POST/PUT/PATCH capture `inputSerializer` at registration. `group("/path") { }` prefixes routes; `auth(authorizer) { }` wraps routes with an `Authorizer` (interface in `Authorizer.kt`; JWT deps are available but `NoAuth` is the default).
- `RequestPredicate` — one registered route: method + path pattern (path params in `{braces}`) + consumed/produced mime types. Defaults are JSON both ways; override per-route with the `expects(...)`/`supplies(...)` extension functions.
- `Requests.kt` / `Responses.kt` — `Request<I>` (typed body, path/query params) and `Response<T>` with factory methods (`Response.OK`, `Response.notFound`, ...).

### `openapi` — a KSP annotation processor (`org.liamjd.apiviaduct.schema`)

Build-time only; generates OpenAPI YAML fragments (info, schemas, paths) from annotations (`@OpenAPIInfo`, `@OpenAPISchema`, `@OpenAPIPath` by default; annotation FQNs are overridable via KSP options `infoAnnotation`/`schemaAnnotation`/`routeAnnotation`). `OpenAPIProcessor` orchestrates; `ObjectSchemaProcessor` and `OpenAPIInfoProcessor` do the YAML building. Tests compile Kotlin sources in-process with kotlin-compile-testing. Only a subset of the OpenAPI 3.1 spec is targeted — OpenAPISpecNotes.md lists which fields, in priority order.
