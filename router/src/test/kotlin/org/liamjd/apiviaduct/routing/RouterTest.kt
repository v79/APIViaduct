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
            headers = mapOf("accept" to "text/plain")
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

    // multiple methods on same route
    @Test
    fun `returns correct response for POST request on route that accepts both POST and PUT`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/multipleMethods"
            httpMethod = "POST"
            headers = mapOf("accept" to "text/plain")
        }, context)
        println(response)
        assertEquals(200, response.statusCode)
        assertEquals("POST: This route accepts PUT and POST", response.body)
    }

    @Test
    fun `returns correct response for PUT request on route that accepts both POST and PUT`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/multipleMethods"
            httpMethod = "PUT"
            headers = mapOf("accept" to "text/plain")
        }, context)
        assertEquals(200, response.statusCode)
        assertEquals("PUT: This route accepts PUT and POST", response.body)
    }

    // route grouping
    @Test
    fun `returns correct response for GET request on grouped route`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/group/test"
            httpMethod = "GET"
            headers = mapOf("accept" to "text/plain")
        }, context)
        assertEquals(200, response.statusCode)
        assertEquals("This route was /group/test", response.body)
    }

    @Test
    fun `returns correct response for GET request on nested grouped route`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/group/nested/test"
            httpMethod = "GET"
            headers = mapOf("accept" to "text/plain")
        }, context)
        assertEquals(200, response.statusCode)
        assertEquals("This route was /group/nested/test", response.body)
    }

    // path parameters
    @Test
    fun `returns correct response for GET request with path parameter`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/params/123"
            httpMethod = "GET"
            headers = mapOf("accept" to "text/plain")
        }, context)
        assertEquals(200, response.statusCode)
        assertEquals("Getting item with id=123", response.body)
    }

    @Test
    fun `returns correct response for PUT request with longer path parameter`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/params/new/456/book"
            httpMethod = "PUT"
            headers = mapOf("accept" to "text/plain")
        }, context)
        assertEquals(200, response.statusCode)
        assertEquals("Creating new book with ISBN=456", response.body)
    }

    @Test
    fun `returns correct response for POST request with multiple path parameters`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/params/new/Christopher/42"
            httpMethod = "POST"
            headers = mapOf("accept" to "text/plain")
        }, context)
        assertEquals(200, response.statusCode)
        assertEquals("Creating new person 'Christopher' aged 42", response.body)
    }

    // controller testing
    @Test
    fun `returns correct response for GET request on controller route`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/controller/test"
            httpMethod = "GET"
            headers = mapOf("accept" to "text/plain")
        }, context)
        assertEquals(200, response.statusCode)
        assertEquals("test", response.body)
    }

    @Test
    fun `returns correct response for PUT request on controller route with object body`() {
        val testRouter = TestRouter()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/controller/putObj"
            httpMethod = "PUT"
            body = """{"name":"Christopher","age":42}"""
            headers = mapOf("Content-Type" to "application/json", "accept" to "application/json")
        }, context)
        assertEquals(200, response.statusCode)
        assertEquals("""{"happy":true,"favouriteColor":"Christopher's favourite colour is blue"}""", response.body)
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
            return object : LambdaLogger {
                override fun log(message: String) {
                    println(message)
                }

                override fun log(message: ByteArray) {
                    println(String(message))
                }
            }
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
        // multiple methods on same route
        put(
            "/multipleMethods",
            handler = { _: Request<Unit> -> Response.ok("PUT: This route accepts PUT and POST") }).supplies(
            MimeType.plainText
        )
        post(
            "/multipleMethods",
            handler = { _: Request<Unit> -> Response.ok("POST: This route accepts PUT and POST") }).supplies(
            MimeType.plainText
        )
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

        // route grouping
        group("/group") {
            get("/test", handler = { _: Request<Unit> -> Response.ok(body = "This route was /group/test") }).supplies(
                MimeType.plainText
            )
            group("/nested") {
                get(
                    "/test",
                    handler = { _: Request<Unit> -> Response.ok(body = "This route was /group/nested/test") }).supplies(
                    MimeType.plainText
                )
            }
        }

        // path parameters
        group("/params") {
            get(
                "/{id}",
                handler = { request: Request<Unit> -> Response.ok(body = "Getting item with id=${request.pathParameters["id"]}") }).supplies(
                MimeType.plainText
            )
            put(
                "/new/{isbn}/book",
                handler = { request: Request<Unit> -> Response.ok(body = "Creating new book with ISBN=${request.pathParameters["isbn"]}") }).supplies(
                MimeType.plainText
            )
            post(
                "/new/{name}/{age}",
                handler = { request: Request<Unit> -> Response.ok(body = "Creating new person '${request.pathParameters["name"]}' aged ${request.pathParameters["age"]}") }).supplies(
                MimeType.plainText
            )
        }

        // controller testing
        group("/controller") {
            get("/test", TestController()::testRoute).supplies(MimeType.plainText)
            put("/putObj", TestController()::testRouteWithObject).expects(MimeType.json)
        }
    }
}

@Serializable
internal class ReqObj(val name: String, val age: Int)

@Serializable
internal class RespObj(val happy: Boolean, val favouriteColor: String)

internal class TestController {
    fun testRoute(request: Request<String>): Response<String> {
        return Response.ok("test")
    }

    fun testRouteWithObject(request: Request<ReqObj>): Response<RespObj> {
        return Response.ok(RespObj(true, "${request.body.name}'s favourite colour is blue"))
    }
}