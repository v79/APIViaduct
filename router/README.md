## API Viaduct Router Module
This is the core module of the API Viaduct project. It is a serverless RESTful API and router written in Kotlin for AWS Lambda. The project is built using Gradle and the AWS SDK for Kotlin.

### Features
- RESTful API routing for AWS Lambda
- Support for path parameters
- Content negotiation (JSON, YAML, plain text, HTML, XML)
- Authorization mechanism
- Middleware support for cross-cutting concerns

### Middleware Support
The router now supports middleware components that can process requests before they reach the handler and process responses after they are generated. This allows for implementing cross-cutting concerns such as logging, authentication, request validation, etc.

#### Implementing a Custom Middleware
To create a custom middleware, implement the `Middleware` interface:

```kotlin
interface Middleware {
    fun processRequest(request: APIGatewayProxyRequestEvent): APIGatewayProxyRequestEvent
    fun <T : Any> processResponse(response: Response<T>, request: APIGatewayProxyRequestEvent): Response<T>
}
```

Example implementation of a logging middleware:

```kotlin
class LoggingMiddleware : Middleware {
    override fun processRequest(request: APIGatewayProxyRequestEvent): APIGatewayProxyRequestEvent {
        println("[LoggingMiddleware] Processing request: ${request.httpMethod} ${request.path}")
        return request
    }

    override fun <T : Any> processResponse(response: Response<T>, request: APIGatewayProxyRequestEvent): Response<T> {
        println("[LoggingMiddleware] Processing response: ${response.statusCode} for ${request.httpMethod} ${request.path}")
        return response
    }
}
```

#### Registering Middlewares
Middlewares can be registered with the router using the `middleware` or `middlewares` methods:

```kotlin
// Register a single middleware
val router = lambdaRouter {
    // Define routes
}.middleware(LoggingMiddleware())

// Register multiple middlewares
val router = lambdaRouter {
    // Define routes
}.middlewares(
    LoggingMiddleware(),
    AuthenticationMiddleware(),
    RequestValidationMiddleware()
)
```

Middlewares are executed in the order they are registered for request processing, and in reverse order for response processing.
