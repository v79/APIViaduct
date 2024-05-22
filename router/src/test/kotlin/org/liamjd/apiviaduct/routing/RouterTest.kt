package org.liamjd.apiviaduct.routing

import com.amazonaws.services.lambda.runtime.ClientContext
import com.amazonaws.services.lambda.runtime.CognitoIdentity
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

internal class RouterTest {

    private val context = TestContext()

    @Test
    fun `basic test of router`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/test"
            httpMethod = "GET"
            headers = mapOf("accept" to "application/json")
        }, context)
        assert(response.statusCode == 200)
        assertEquals("application/json", response.headers["Content-Type"])
        assertEquals("https://example.com", response.headers["Access-Control-Allow-Origin"])
    }

    @Test
    fun `returns a 404 for a route that doesn't exist`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/notfound"
            httpMethod = "GET"
            headers = mapOf("accept" to "application/json")
        }, context)
        assert(response.statusCode == 404)
    }

    @Test
    fun `returns 405 for POST request on GET only route`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/test"
            httpMethod = "POST"
            headers = mapOf("accept" to "application/json")
        }, context)
        assertEquals(405, response.statusCode)
        assertEquals(response.headers["Allow"], "GET")
    }

    @Test
    fun `returns correct response for valid DELETE request`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/deleteTest"
            httpMethod = "DELETE"
            headers = mapOf("accept" to "application/json")
        }, context)
        assertEquals(200, response.statusCode)
    }

    @Test
    fun `returns 405 for DELETE request on GET only route`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/test"
            httpMethod = "DELETE"
            headers = mapOf("accept" to "application/json")
        }, context)
        assertEquals(405, response.statusCode)
        assertEquals(response.headers["Allow"], "GET")
    }

    @Test
    fun `returns correct response for valid PATCH request`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/patchTest"
            httpMethod = "PATCH"
            headers = mapOf("accept" to "application/json")
        }, context)
        assertEquals(200, response.statusCode)
    }

    @Test
    fun `returns 405 for PATCH request on GET only route`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/test"
            httpMethod = "PATCH"
            headers = mapOf("accept" to "application/json")
        }, context)
        assertEquals(405, response.statusCode)
        assertEquals(response.headers["Allow"], "GET")
    }

    @Test
    fun `not implemented 500 response will have empty body`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/notImplemented"
            httpMethod = "GET"
            headers = mapOf("accept" to "application/json")
        }, context)
        assertEquals(501, response.statusCode)
        assertEquals("", response.body)
    }

    // accepts and content type overrides
    @Test
    fun `returns correct response for GET request with overridden accept type`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/getText"
            httpMethod = "GET"
            headers = mapOf("accept" to "text/plain")
        }, context)
        assertEquals(200, response.statusCode)
    }

    @Test
    fun `returns correct response for GET request with overridden content type`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/putText"
            httpMethod = "PUT"
            headers = mapOf("Content-Type" to "text/plain", "accept" to "application/json")
        }, context)
        assertEquals(200, response.statusCode)
    }

    @Test
    fun `returns 406 for GET request with overridden accept type that doesn't match`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/putText"
            httpMethod = "PUT"
            headers = mapOf("Content-Type" to "text/plain", "accept" to "text/plain")
        }, context)
        assertEquals(406, response.statusCode)
    }

    // serialization and deserialization
    @Test
    fun `returns correct response for POST request with object body`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/postObj"
            httpMethod = "POST"
            body = """{"name":"Christopher","age":42}"""
            headers = mapOf("Content-Type" to "application/json", "accept" to "application/json")
        }, context)
        assertEquals(200, response.statusCode)
        assertEquals("""{"happy":true,"favouriteColor":"Christopher's favourite colour is blue"}""", response.body)
    }

    @Test
    fun `returns a yaml response for POST request with object body and yaml accept type`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/giveMeYaml"
            httpMethod = "POST"
            body = """{"name":"Christopher","age":42}"""
            headers = mapOf("Content-Type" to "application/json", "accept" to "application/yaml")
        }, context)
        assertEquals(200, response.statusCode)
        assertEquals("happy: false\nfavouriteColor: \"Christopher's favourite colour is blue\"", response.body)
    }

    @Test
    fun `returns 400 for POST when body cannot be serialized according to content type`() {
        val testRouter = TestRouter()
        // in this test we are sending Yaml data with a JSON content type
        // not that the other way around is successfully handled and returns a 200
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/giveMeYaml"
            httpMethod = "POST"
            body = """name: Christoper\nage: 42"""
            headers = mapOf("Content-Type" to "application/json", "accept" to "application/yaml")
        }, context)
        assertEquals(400, response.statusCode)
        assert(response.body.startsWith("Could not deserialize body"))
    }

    // This is a test context that can be used to test the LambdaRouter
    // The values don't matter, as long as they are not null
    class TestContext : Context {
        override fun getAwsRequestId(): String = "awsRequestId"
        override fun getLogGroupName(): String = "logGroupName"
        override fun getLogStreamName(): String = "logStreamName"
        override fun getFunctionName(): String = "functionName"
        override fun getFunctionVersion() = "functionVersion"
        override fun getInvokedFunctionArn() = "invokedFunctionArn"
        override fun getIdentity(): CognitoIdentity {
            TODO("Not yet implemented")
        }

        override fun getClientContext(): ClientContext {
            TODO("Not yet implemented")
        }

        override fun getRemainingTimeInMillis() = 1000
        override fun getMemoryLimitInMB() = 512
        override fun getLogger(): LambdaLogger {
            TODO("Not yet implemented")
        }
    }
}

internal class TestRouter : LambdaRouter() {
    override val corsDomain: String = "https://example.com"
    override val router = lambdaRouter {
        // basic methods
        get("/test", handler = { _: Request<Unit> -> Response.ok() })
        patch("/patchTest", handler = { _: Request<Unit> -> Response.ok() })
        delete("/deleteTest", handler = { _: Request<Unit> -> Response.ok() })
        post("/postTest", handler = { _: Request<Unit> -> Response.ok() })
        put("/putTest", handler = { _: Request<Unit> -> Response.ok() })
        get("/notImplemented", handler = { _: Request<Unit> -> Response.notImplemented() })
        // overriding the default consumes and produces
        get("/getText", handler = { _: Request<Unit> -> Response.ok() }).supplies(setOf(MimeType.plainText))
        put("/putText", handler = { _: Request<Unit> -> Response.ok() }).expects(setOf(MimeType.plainText))

        // serialization and deserialization
        post(
            "/postObj",
            handler = { request: Request<ReqObj> ->
                Response.ok(
                    RespObj(
                        true,
                        "${request.body.name}'s favourite colour is blue"
                    )
                )
            })

        post(
            "/giveMeYaml",
            handler = { request: Request<ReqObj> ->
                Response.ok(
                    RespObj(
                        false,
                        "${request.body.name}'s favourite colour is blue"
                    )
                )
            }).supplies(MimeType.yaml).expects(MimeType.json)
    }
}

@Serializable
internal class ReqObj(val name: String, val age: Int)

@Serializable
internal class RespObj(val happy: Boolean, val favouriteColor: String)