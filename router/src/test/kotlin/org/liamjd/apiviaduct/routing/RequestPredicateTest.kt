package org.liamjd.apiviaduct.routing

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import kotlin.test.Test

class RequestPredicateTest {

    @Test
    fun `match path from basic AWS event`() {
        val testEvent = APIGatewayProxyRequestEvent().apply {
            path = "/test"
            httpMethod = "GET"
        }
        val predicate = RequestPredicate("GET", "/test", setOf(), setOf())
        assert(predicate.match(testEvent).matches)
    }

    @Test
    fun `match path from basic AWS event with overridden accepts header`() {
        val testEvent = APIGatewayProxyRequestEvent().apply {
            path = "/test"
            httpMethod = "GET"
            headers = mapOf("accept" to "text/plain")
        }
        val predicate = RequestPredicate(method = "GET", pathPattern = "/test", consumes = setOf(), produces = setOf(MimeType.plainText))
        assert(predicate.match(testEvent).matches)
    }

    @Test
    fun `match path from basic AWS event with overridden content type`() {
        val testEvent = APIGatewayProxyRequestEvent().apply {
            path = "/test"
            httpMethod = "GET"
            headers = mapOf("content-type" to "text/plain")
        }
        val predicate = RequestPredicate(method = "GET", pathPattern = "/test", consumes = setOf(MimeType.plainText), produces = setOf())
        assert(predicate.match(testEvent).matches)
    }
}