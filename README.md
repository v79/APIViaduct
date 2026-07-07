# APIViaduct
A serverless RESTful API and router written in Kotlin for AWS Lambda

Inspired by my earlier project [Cantilever](https://www.cantilevers.org/), I wanted to create a more lightweight and flexible API framework for AWS Lambda. This project is a work in progress and is not yet ready for production use. This project will currently run on the JVM, and so has the inherent cold start issues of Java on AWS Lambda. Work is underway to make the library compatible with GraalVM native image compilation — see [GRAALVM_RECOMMENDATIONS.md](GRAALVM_RECOMMENDATIONS.md) for the current state and [GRAALVM_CONSUMER_GUIDE.md](GRAALVM_CONSUMER_GUIDE.md) for how to build a native Lambda with the library.

RESTful routes will be defined using a Kotlin DSL, and uses Kotlinx Serialization for JSON (de)serialization. The project is built using Gradle and the AWS SDK for Kotlin.

The library will support route grouping, authentication, all the main REST methods, and more. I hope to add an OpenAPI specification generator, and middleware our filters in the future. While the router defaults to JSON, it is possible to specify other content types such as text, YAML or any others supported by kotlinx.serialization.

This library is not intended to be used as a general web server framework.

The library currently builds against Kotlin 1.9.23. The previous KSP2 blocker (the OpenAPI module's `kotlin-compile-testing` dependency) has been removed along with the old annotation processor, so a move to Kotlin 2.0 is no longer blocked by the OpenAPI module.

## Example
```kotlin
  // a simple get request handler
  get("/hello") { _: Request<Unit> ->
    Response.OK("Hello, world!")  
  }
  // a simple post request handler, expecting a JSON body for a data class of type 'Thingy'
  post("/new") { req: Request<Thingy> ->
    val body = req.body
    Response.OK("You sent: ${body.name}")
  }
```
