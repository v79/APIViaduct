package org.liamjd.apiviaduct.schema

import com.amazonaws.services.lambda.runtime.ClientContext
import com.amazonaws.services.lambda.runtime.CognitoIdentity
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import org.liamjd.apiviaduct.routing.LambdaRouter
import org.liamjd.apiviaduct.routing.MimeType
import org.liamjd.apiviaduct.routing.Request
import org.liamjd.apiviaduct.routing.Response
import org.liamjd.apiviaduct.routing.lambdaRouter
import org.liamjd.apiviaduct.routing.openApi
import org.liamjd.apiviaduct.routing.supplies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServeOpenApiTest {

    private fun get(path: String, accept: String = "application/yaml") =
        DocsRouter().handleRequest(
            APIGatewayProxyRequestEvent().apply {
                this.path = path
                httpMethod = "GET"
                headers = mapOf("accept" to accept)
            },
            StubContext
        )

    @Test
    fun `serves the generated document as yaml`() {
        val response = get("/openapi.yaml")
        assertEquals(200, response.statusCode)
        assertEquals("application/yaml", response.headers["Content-Type"])
        assertTrue(response.body.startsWith("openapi: 3.1.0"), "raw YAML is passed through un-encoded")
        assertTrue(response.body.contains("title: Docs API"))
    }

    @Test
    fun `document includes routes declared after the serveOpenApi call`() {
        // /things is registered after serveOpenApi() in DocsRouter; lazy generation still captures it.
        val response = get("/openapi.yaml")
        assertTrue(response.body.contains("/things:"), "generation is deferred until the router is fully built")
    }

    @Test
    fun `the docs endpoint appears in its own document`() {
        assertTrue(get("/openapi.yaml").body.contains("/openapi.yaml:"))
    }

    @Test
    fun `a custom path is served`() {
        val response = CustomPathRouter().handleRequest(
            APIGatewayProxyRequestEvent().apply {
                path = "/docs.yaml"; httpMethod = "GET"; headers = mapOf("accept" to "*/*")
            },
            StubContext
        )
        assertEquals(200, response.statusCode)
        assertTrue(response.body.startsWith("openapi: 3.1.0"))
    }
}

private class DocsRouter : LambdaRouter() {
    override val router = lambdaRouter {
        openApi { info { title = "Docs API"; version = "2.0.0" } }
        serveOpenApi()
        get("/things", { _: Request<Unit> -> Response.ok("things") }).supplies(MimeType.plainText)
    }
}

private class CustomPathRouter : LambdaRouter() {
    override val router = lambdaRouter {
        openApi { info { title = "Custom"; version = "1.0.0" } }
        serveOpenApi("/docs.yaml")
    }
}

/** Minimal AWS [Context] so the router's `handleRequest` can be exercised in-process. */
private object StubContext : Context {
    override fun getAwsRequestId() = "test-request"
    override fun getLogGroupName() = "test-log-group"
    override fun getLogStreamName() = "test-log-stream"
    override fun getFunctionName() = "test-function"
    override fun getFunctionVersion() = "test"
    override fun getInvokedFunctionArn() = "test-arn"
    override fun getIdentity(): CognitoIdentity? = null
    override fun getClientContext(): ClientContext? = null
    override fun getRemainingTimeInMillis() = 30_000
    override fun getMemoryLimitInMB() = 512
    override fun getLogger(): LambdaLogger = object : LambdaLogger {
        override fun log(message: String?) {}
        override fun log(message: ByteArray?) {}
    }
}
