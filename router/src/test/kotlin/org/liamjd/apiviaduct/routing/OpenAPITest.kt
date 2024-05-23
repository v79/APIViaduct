package org.liamjd.apiviaduct.routing

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import org.junit.jupiter.api.Test
import org.liamjd.apiviaduct.routing.RouterTest.TestContext
import org.liamjd.apiviaduct.routing.extensions.openAPI
import kotlin.test.assertEquals

class OpenAPITest {

    private val context = TestContext()

    @Test
    fun `openAPI route returns a valid OpenAPI document`() {
        val testRouter = OpenAPITestRouter()
        val openAPI = testRouter.openAPI()
        val response = testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/openapi"
            httpMethod = "GET"
            headers = mapOf("accept" to "text/plain", "Authorization" to "Basic dXNlc")
        }, context)
        assert(response.statusCode == 200)
        println()
        println(openAPI)
    }
}

internal class OpenAPITestRouter : LambdaRouter() {
    override val router = lambdaRouter {
        get("/openapi") { _: Request<Unit> ->
            Response(200, Response.ok("OpenAPI route"))
        }.supplies(MimeType.plainText)
        group("/group") {
            get("/grouped") { _: Request<Unit> ->
                Response(200, Response.ok("Grouped route"))
            }.supplies(MimeType.plainText)
            put("/new/{title}") { _: Request<Unit> ->
                Response(200, Response.ok("New route"))
            }.supplies(MimeType.yaml)
            post("/person") { _: Request<Person> ->
                Response(200, Response.ok("Add person route"))
            }.supplies(MimeType.json)
        }
        auth(OpenAPIFakeAuthorizer()) {
            get("/secure") { _: Request<Unit> ->
                Response(200, Response.ok("Secure route"))
            }.supplies(MimeType.json)
        }
    }
}

internal class Person(val name: String, val age: Int)
internal class OpenAPIFakeAuthorizer : Authorizer {
    override val simpleName: String = "Fake Authorizer"
    override val type: AuthType = AuthType.BASIC
    override fun authorize(request: APIGatewayProxyRequestEvent): AuthResult {
        return AuthResult(true, "")
    }
}