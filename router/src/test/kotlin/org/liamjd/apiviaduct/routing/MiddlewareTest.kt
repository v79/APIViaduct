package org.liamjd.apiviaduct.routing

import com.amazonaws.services.lambda.runtime.ClientContext
import com.amazonaws.services.lambda.runtime.CognitoIdentity
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Simple mock implementation of the AWS Lambda Context
 */
class MockContext : Context {
    override fun getAwsRequestId(): String = "test-request-id"
    override fun getLogGroupName(): String = "test-log-group"
    override fun getLogStreamName(): String = "test-log-stream"
    override fun getFunctionName(): String = "test-function"
    override fun getFunctionVersion(): String = "test-version"
    override fun getInvokedFunctionArn(): String = "test-arn"
    override fun getIdentity(): CognitoIdentity? = null
    override fun getClientContext(): ClientContext? = null
    override fun getRemainingTimeInMillis(): Int = 1000
    override fun getMemoryLimitInMB(): Int = 128
    override fun getLogger(): LambdaLogger = object : LambdaLogger {
        override fun log(message: String) = println(message)
        override fun log(message: ByteArray) = println(String(message))
    }
}

class MiddlewareTest {

    @Test
    fun `test middleware registration`() {
        // Given
        val router = lambdaRouter {
            // Define routes
        }

        // When
        router.middleware(LoggingMiddleware())

        // Then
        assertEquals(1, router.middlewares.size)
        assertTrue(router.middlewares[0] is LoggingMiddleware)
    }

    @Test
    fun `test multiple middleware registration`() {
        // Given
        val router = lambdaRouter {
            // Define routes
        }

        // When
        router.middlewares(LoggingMiddleware(), TestMiddleware("test1"), TestMiddleware("test2"))

        // Then
        assertEquals(3, router.middlewares.size)
        assertTrue(router.middlewares[0] is LoggingMiddleware)
        assertTrue(router.middlewares[1] is TestMiddleware)
        assertTrue(router.middlewares[2] is TestMiddleware)
    }

    @Test
    fun `test middleware chain execution order`() {
        // Given
        val requestRecorder = StringBuilder()
        val responseRecorder = StringBuilder()

        val router = lambdaRouter {
            get<Unit, String>("/test") { _ ->
                requestRecorder.append("H")
                val response = Response.ok(body = "Test Response")
                responseRecorder.append("H")
                response
            }
        }

        router.middlewares(
            RecordingMiddleware(requestRecorder, responseRecorder, "1"),
            RecordingMiddleware(requestRecorder, responseRecorder, "2"),
            RecordingMiddleware(requestRecorder, responseRecorder, "3")
        )

        val handler = LambdaRequestHandler()
        handler.router = router

        val request = APIGatewayProxyRequestEvent()
            .withPath("/test")
            .withHttpMethod("GET")
            .withHeaders(mapOf("Accept" to "application/json"))

        val context = MockContext()

        // When
        handler.handleRequest(request, context)

        // Then
        assertEquals("123H", requestRecorder.toString())
        assertEquals("H321", responseRecorder.toString())
    }

    @Test
    fun `test middleware can modify request and response`() {
        // Given
        val router = lambdaRouter {
            get("/test", { request: Request<Unit> ->
                // Check if the header was added by the middleware
                val headerValue = request.headers["X-Test-Header"]
                Response.ok(body = "Header value: $headerValue")
            })
        }.middleware(object : Middleware {
            override fun processRequest(request: APIGatewayProxyRequestEvent): APIGatewayProxyRequestEvent {
                // Add a custom header to the request
                request.headers = (request.headers ?: emptyMap()) + mapOf("X-Test-Header" to "test-value")
                return request
            }

            override fun <T : Any> processResponse(
                response: Response<T>,
                request: APIGatewayProxyRequestEvent
            ): Response<T> {
                // Add a custom header to the response
                return response.copy(headers = response.headers + mapOf("X-Response-Header" to "response-value"))
            }
        })

        val handler = LambdaRequestHandler()
        handler.router = router

        val request = APIGatewayProxyRequestEvent()
            .withPath("/test")
            .withHttpMethod("GET")
            .withHeaders(mapOf("Accept" to "application/json"))

        val context = MockContext()

        // When
        val result = handler.handleRequest(request, context)

        // Then
        assertEquals("response-value", result.headers["X-Response-Header"])
        assertTrue(result.body.contains("test-value"))
    }
}

/**
 * Test middleware implementation that records the order of execution
 */
class RecordingMiddleware(
    private val requestRecorder: StringBuilder,
    private val responseRecorder: StringBuilder,
    private val id: String
) : Middleware {
    override fun processRequest(request: APIGatewayProxyRequestEvent): APIGatewayProxyRequestEvent {
        requestRecorder.append(id)
        return request
    }

    override fun <T : Any> processResponse(response: Response<T>, request: APIGatewayProxyRequestEvent): Response<T> {
        responseRecorder.append(id)
        return response
    }
}

/**
 * Simple test middleware for registration tests
 */
class TestMiddleware(private val id: String) : Middleware {
    override fun processRequest(request: APIGatewayProxyRequestEvent): APIGatewayProxyRequestEvent = request
    override fun <T : Any> processResponse(response: Response<T>, request: APIGatewayProxyRequestEvent): Response<T> =
        response
}
