package org.liamjd.apiviaduct.routing

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import kotlinx.serialization.Serializable
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteProcessorTest {

    @Test
    fun `returns error when no type information for handler function`() {
        val input = APIGatewayProxyRequestEvent().apply {
            httpMethod = "POST"
            body = "test"
        }
        val handlerFunction: RouteFunction<String, String> = RouteFunction(
            predicate = RequestPredicate(
                method = "POST",
                pathPattern = "/test",
                produces = setOf(MimeType.json),
                consumes = setOf(MimeType.json)
            ),
            handler = { _: Any -> Response.ok("test") }
        )
        val response = RouteProcessor.processRoute(input, handlerFunction)
        assertEquals(400, response.statusCode)
        assertEquals("No type information for handler function", response.body)
    }

    @Test
    fun `returns method not allowed for TRACE`() {
        val input = APIGatewayProxyRequestEvent().apply {
            httpMethod = "TRACE"
        }
        val handlerFunction: RouteFunction<String, String> = RouteFunction(
            predicate = RequestPredicate(
                method = "POST",
                pathPattern = "/test",
                produces = setOf(MimeType.json),
                consumes = setOf(MimeType.json)
            ),
            handler = { _: Any -> Response.ok("test") }
        )
        val response = RouteProcessor.processRoute(input, handlerFunction)
        assertEquals(405, response.statusCode)
        assertEquals("Method TRACE not supported for path /test", response.body)
    }

    @Test
    fun `deserialize a PUT request with a json SimpleObject body`() {
        val input = APIGatewayProxyRequestEvent().apply {
            httpMethod = "PUT"
            body = """{"name":"test","age":42}"""
            headers = mapOf("Content-Type" to "application/json")
        }
        val handlerFunction: RouteFunction<SimpleObject, SimpleObject> = RouteFunction(
            predicate = RequestPredicate(
                method = "PUT",
                pathPattern = "/test",
                produces = setOf(MimeType.json),
                consumes = setOf(MimeType.json)
            ),
            handler = { request: Request<SimpleObject> -> Response.ok(request.body) }
        )
        handlerFunction.predicate.kType = typeOf<SimpleObject>()
        val response = RouteProcessor.processRoute(input, handlerFunction)
        assertEquals(200, response.statusCode)
        assertEquals("test", (response.body as SimpleObject).name)
        assertEquals(42, (response.body as SimpleObject).age)
    }

    @Test
    fun `deserialize a PUT request with a yaml SimpleObject body`() {
        val input = APIGatewayProxyRequestEvent().apply {
            httpMethod = "PUT"
            body = """name: test
                |age: 42""".trimMargin()
            headers = mapOf("Content-Type" to "application/yaml")
        }
        val handlerFunction: RouteFunction<SimpleObject, SimpleObject> = RouteFunction(
            predicate = RequestPredicate(
                method = "PUT",
                pathPattern = "/test",
                produces = setOf(MimeType.json),
                consumes = setOf(MimeType.yaml)
            ),
            handler = { request: Request<SimpleObject> -> Response.ok(request.body) }
        )
        handlerFunction.predicate.kType = typeOf<SimpleObject>()
        val response = RouteProcessor.processRoute(input, handlerFunction)
        assertEquals(200, response.statusCode)
        assertEquals("test", (response.body as SimpleObject).name)
        assertEquals(42, (response.body as SimpleObject).age)
    }

    @Test
    fun `returns bad request when json string is missing a field`() {
        val input = APIGatewayProxyRequestEvent().apply {
            httpMethod = "PUT"
            body = """{"name":"test"}"""
            headers = mapOf("Content-Type" to "application/json")
        }
        val handlerFunction: RouteFunction<SimpleObject, SimpleObject> = RouteFunction(
            predicate = RequestPredicate(
                method = "PUT",
                pathPattern = "/test",
                produces = setOf(MimeType.json),
                consumes = setOf(MimeType.json)
            ),
            handler = { request: Request<SimpleObject> -> Response.ok(request.body) }
        )
        handlerFunction.predicate.kType = typeOf<SimpleObject>()
        val response = RouteProcessor.processRoute(input, handlerFunction)
        assertEquals(400, response.statusCode)
        assert((response.body as String).startsWith("Invalid request"))
    }

    @Test
    fun `returns bad request when json is invalid`() {
        val input = APIGatewayProxyRequestEvent().apply {
            httpMethod = "PUT"
            body = """wibble"""
            headers = mapOf("Content-Type" to "application/json")
        }
        val handlerFunction: RouteFunction<SimpleObject, SimpleObject> = RouteFunction(
            predicate = RequestPredicate(
                method = "PUT",
                pathPattern = "/test",
                produces = setOf(MimeType.json),
                consumes = setOf(MimeType.json)
            ),
            handler = { request: Request<SimpleObject> -> Response.ok(request.body) }
        )
        handlerFunction.predicate.kType = typeOf<SimpleObject>()
        val response = RouteProcessor.processRoute(input, handlerFunction)
        assertEquals(400, response.statusCode)
        assert((response.body as String).startsWith("Could not deserialize body."))

    }
}

@Serializable
internal class SimpleObject(val name: String, val age: Int)
