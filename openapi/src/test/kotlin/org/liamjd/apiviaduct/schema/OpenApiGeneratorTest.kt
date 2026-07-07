package org.liamjd.apiviaduct.schema

import kotlinx.serialization.Serializable
import org.liamjd.apiviaduct.routing.Request
import org.liamjd.apiviaduct.routing.Response
import org.liamjd.apiviaduct.routing.lambdaRouter
import org.liamjd.apiviaduct.routing.spec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenApiGeneratorTest {

    @Serializable
    data class Book(val id: Long, val title: String, val tags: List<String>)

    private val router = lambdaRouter {
        get("/books/{id}", { _: Request<Unit> -> Response.ok(Book(1, "Dune", emptyList())) })
            .spec {
                summary = "Fetch a single book"
                tags("books")
                pathParam("id", "The book's unique id")
                response(200, "The book record")
                response(404, "No book with that id")
            }
        post("/books", { req: Request<Book> -> Response.ok(req.body) })
    }

    private val generator = OpenApiGenerator(router, OpenApiInfo(title = "Library API", version = "1.0.0"))

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.map(key: String) = this[key] as Map<String, Any?>

    @Test
    fun `document has the OpenAPI preamble and info from OpenApiInfo`() {
        val doc = generator.buildDocument()
        assertEquals("3.1.0", doc["openapi"])
        assertEquals("Library API", doc.map("info")["title"])
        assertEquals("1.0.0", doc.map("info")["version"])
    }

    @Test
    fun `a GET route with spec produces a documented operation`() {
        val doc = generator.buildDocument()
        val op = doc.map("paths").map("/books/{id}").map("get")

        assertEquals("Fetch a single book", op["summary"])
        assertEquals(listOf("books"), op["tags"])

        @Suppress("UNCHECKED_CAST")
        val params = op["parameters"] as List<Map<String, Any?>>
        val idParam = params.single { it["name"] == "id" }
        assertEquals("path", idParam["in"])
        assertEquals(true, idParam["required"])
        assertEquals("The book's unique id", idParam["description"])
    }

    @Test
    fun `responses merge spec prose with the generated success schema`() {
        val doc = generator.buildDocument()
        val responses = doc.map("paths").map("/books/{id}").map("get").map("responses")

        assertEquals("The book record", responses.map("200")["description"])
        assertEquals("No book with that id", responses.map("404")["description"])

        // 200 carries the response body schema, referencing the Book component.
        val schema = responses.map("200").map("content").map("application/json").map("schema")
        assertEquals("#/components/schemas/Book", schema["\$ref"])

        // 404 has prose only, no content.
        assertNull(responses.map("404")["content"])
    }

    @Test
    fun `a POST route derives a request body from the input serializer`() {
        val doc = generator.buildDocument()
        val op = doc.map("paths").map("/books").map("post")

        val reqSchema = op.map("requestBody").map("content").map("application/json").map("schema")
        assertEquals("#/components/schemas/Book", reqSchema["\$ref"])
        assertEquals(true, op.map("requestBody")["required"])
    }

    @Test
    fun `nested serializable types land in components schemas`() {
        val doc = generator.buildDocument()
        val book = doc.map("components").map("schemas").map("Book")

        assertEquals("object", book["type"])
        val props = book.map("properties")
        assertEquals("int64", props.map("id")["format"])
        assertEquals("string", props.map("title")["type"])
        assertEquals("array", props.map("tags")["type"])

        @Suppress("UNCHECKED_CAST")
        val required = book["required"] as List<String>
        assertEquals(setOf("id", "title", "tags"), required.toSet())
    }

    @Test
    fun `generateYaml renders the document as valid-looking YAML`() {
        val yaml = generator.generateYaml()
        println(yaml)
        assertTrue(yaml.startsWith("openapi: 3.1.0"), "should start with the version line")
        assertTrue(yaml.contains("title: Library API"))
        assertTrue(yaml.contains("/books/{id}:"))
        assertTrue(yaml.contains("\$ref: \"#/components/schemas/Book\""), "refs are quoted (leading #)")
    }
}
