package org.liamjd.apiviaduct.sample

import kotlinx.serialization.Serializable
import org.liamjd.apiviaduct.routing.LambdaRouter
import org.liamjd.apiviaduct.routing.MimeType
import org.liamjd.apiviaduct.routing.Request
import org.liamjd.apiviaduct.routing.Response
import org.liamjd.apiviaduct.routing.lambdaRouter
import org.liamjd.apiviaduct.routing.supplies

@Serializable
data class Person(val name: String, val age: Int)

@Serializable
data class Greeting(val message: String, val person: Person)

class SampleRouter : LambdaRouter() {
    override val router = lambdaRouter {
        get("/hello") { _: Request<Unit> ->
            Response.ok(body = "Hello from a native image!")
        }.supplies(MimeType.plainText)
        get("/person/{name}") { req: Request<Unit> ->
            Response.ok(body = Person(name = req.pathParameters["name"] ?: "unknown", age = 30))
        }
        post("/person") { req: Request<Person> ->
            Response.ok(body = Greeting(message = "Welcome, ${req.body.name}", person = req.body))
        }
        auth(cognitoAuthorizerFromEnv()) {
            get("/secure/hello") { _: Request<Unit> ->
                Response.ok(body = "Hello, authenticated caller!")
            }.supplies(MimeType.plainText)
        }
    }
}

/**
 * The Cognito pool details are injected by the OpenTofu deployment (see infra/main.tf).
 * The local self-test runs without them: the placeholder issuer can never match a real
 * token, and the self-test only sends requests that are rejected before any JWKS fetch.
 */
private fun cognitoAuthorizerFromEnv() = CognitoAuthorizer(
    region = System.getenv("COGNITO_REGION") ?: "local",
    userPoolId = System.getenv("COGNITO_USER_POOL_ID") ?: "local-pool",
    clientId = System.getenv("COGNITO_CLIENT_ID")
)
