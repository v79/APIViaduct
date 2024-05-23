package org.liamjd.apiviaduct.routing

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import org.liamjd.apiviaduct.routing.RouterTest.TestContext
import org.liamjd.apiviaduct.routing.extensions.getHeader
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthorizationTest {

    private val context = TestContext()

    @Test
    fun `NoAuth route just passes as normal`() {
        val testRouter = SecurityRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/public"
            httpMethod = "GET"
            headers = mapOf("accept" to "text/plain")
        }, context)
        assert(response.statusCode == 200)
        assertEquals("text/plain", response.headers["Content-Type"])
    }

    @Test
    fun `Basic auth route fails without the header`() {
        val testRouter = SecurityRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/secure"
            httpMethod = "GET"
            headers = mapOf("accept" to "text/plain")
        }, context)
        assert(response.statusCode == 401)
        assertEquals("text/plain", response.headers["Content-Type"])
    }

    @Test
    fun `Basic auth route passes with the header`() {
        val testRouter = SecurityRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/secure"
            httpMethod = "GET"
            headers = mapOf("accept" to "text/plain", "Authorization" to "Basic dXNlc")
        }, context)
        assert(response.statusCode == 200)
        assertEquals("Secure route", response.body)
    }
}

internal class SecurityRouter : LambdaRouter() {
    override val corsDomain: String = "https://secure.example.com"
    override val router = lambdaRouter {
        auth(NoAuth()) {
            get("/public") { _: Request<Unit> ->
                Response(200, Response.ok("Public route"))
            }.supplies(MimeType.plainText)
        }

        auth(BasicFakeAuthorizer()) {
            get("/secure") { _: Request<Unit> ->
                Response.ok("Secure route")
            }.supplies(MimeType.plainText)
        }
    }
}

internal class BasicFakeAuthorizer : Authorizer {
    override val simpleName: String = "Fake Basic Authorizer"
    override val type: AuthType = AuthType.BASIC
    override fun authorize(request: APIGatewayProxyRequestEvent): AuthResult {
        if (request.getHeader("Authorization") == "Basic dXNlc") {
            return AuthResult(true, "")
        }
        return AuthResult(false, "Unauthorized")
    }
}
