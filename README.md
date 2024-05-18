# APIViaduct
A serverless RESTful API and router written in Kotlin for AWS Lambda

Inspired by my earlier project [Cantilever](https://www.cantilevers.org/), I wanted to create a more lightweight and flexible API framework for AWS Lambda. This project is a work in progress and is not yet ready for production use. This project will currently run on the JVM, and so has the inherent cold start issues of Java on AWS Lambda. I may explore GraalVM in the future as an alternative.

RESTful routes will be defined using a Kotlin DSL, and uses Kotlinx Serialization for JSON (de)serialization. The project is built using Gradle and the AWS SDK for Kotlin.

The library will support route grouping, authentication, all the main REST methods, and more. I hope to add an OpenAPI specification generator, and middleware our filters in the future. While the router defaults to JSON, it is possible to specify other content types such as text, YAML or any others supported by kotlinx.serialization.

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
