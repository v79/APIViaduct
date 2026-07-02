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
    }
}
