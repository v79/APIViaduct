package org.liamjd.apiviaduct.routing

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import kotlin.test.Test
import kotlin.test.assertEquals

internal class RequestTest {

    @Test
    fun `construct a basic request object from proxy event`() {
        val testEvent = APIGatewayProxyRequestEvent().apply {
            path = "/test"
            httpMethod = "GET"
        }
        val body = "Hello"
        val request = Request(testEvent, body, "/test")
        assert(request.headers.isEmpty())
    }

    @Test
    fun `construct request object from proxy event with header`() {
        val testEvent = APIGatewayProxyRequestEvent().apply {
            path = "/test"
            httpMethod = "GET"
            headers = mapOf("accept" to "application/json")
        }
        val body = "Hello"
        val request = Request(testEvent, body, "/test")
        assert(request.headers.isNotEmpty())
    }

    @Test
    fun `construct request object from proxy event with path parameters`() {
        val testEvent = APIGatewayProxyRequestEvent().apply {
            path = "/test/123"
            httpMethod = "GET"
        }
        val body = "Hello"
        val request = Request(testEvent, body, "/test/{id}")
        assert(request.pathParameters.isNotEmpty())
        assertEquals("123", request.pathParameters["id"])
    }
}