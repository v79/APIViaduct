package org.liamjd.apiviaduct.schema.sample

import org.liamjd.apiviaduct.schema.OpenApiGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookLibraryRouterTest {

    // Info comes from the router's openApi { } DSL — no override passed.
    private val doc = OpenApiGenerator(BookLibraryRouter().router).buildDocument()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.map(key: String) = this[key] as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.list(key: String) = this[key] as List<Map<String, Any?>>

    private fun operation(path: String, method: String) =
        doc.map("paths").map(path).map(method)

    @Test
    fun `info carries the document-level metadata`() {
        val info = doc.map("info")
        assertEquals("Book Library API", info["title"])
        assertEquals("1.0.0", info["version"])
        assertEquals("library@example.com", info.map("contact")["email"])
        assertEquals("Apache-2.0", info.map("license")["name"])
    }

    @Test
    fun `all six CRUD operations are present under the two grouped paths`() {
        assertEquals(setOf("get", "post"), doc.map("paths").map("/books").keys)
        assertEquals(setOf("get", "put", "patch", "delete"), doc.map("paths").map("/books/{id}").keys)
    }

    @Test
    fun `the list operation documents its query parameters`() {
        val params = operation("/books", "get").list("parameters")
        assertEquals(setOf("author", "genre", "limit"), params.map { it["name"] }.toSet())
        assertTrue(params.all { it["in"] == "query" })
        assertEquals("Only return books by this author", params.single { it["name"] == "author" }["description"])
    }

    @Test
    fun `the list operation returns an array of Book`() {
        val schema = operation("/books", "get")
            .map("responses").map("200").map("content").map("application/json").map("schema")
        assertEquals("array", schema["type"])
        assertEquals("#/components/schemas/Book", schema.map("items")["\$ref"])
    }

    @Test
    fun `create documents a NewBook request body and a 201 response`() {
        val post = operation("/books", "post")
        assertEquals("createBook", post["operationId"])
        assertEquals(
            "#/components/schemas/NewBook",
            post.map("requestBody").map("content").map("application/json").map("schema")["\$ref"]
        )
        val created = post.map("responses").map("201")
        assertEquals("The newly created book", created["description"])
        assertEquals("#/components/schemas/Book", created.map("content").map("application/json").map("schema")["\$ref"])
        assertEquals("The request body was invalid", post.map("responses").map("400")["description"])
    }

    @Test
    fun `getOne documents the path parameter and 404`() {
        val get = operation("/books/{id}", "get")
        val idParam = get.list("parameters").single { it["name"] == "id" }
        assertEquals("path", idParam["in"])
        assertEquals(true, idParam["required"])
        assertEquals("The book's unique id", idParam["description"])
        assertEquals("No book with that id", get.map("responses").map("404")["description"])
    }

    @Test
    fun `patch uses the BookPatch body whose fields are all optional`() {
        val patch = operation("/books/{id}", "patch")
        assertEquals(
            "#/components/schemas/BookPatch",
            patch.map("requestBody").map("content").map("application/json").map("schema")["\$ref"]
        )
        val bookPatch = doc.map("components").map("schemas").map("BookPatch")
        // No field is required, and each is nullable.
        assertTrue((bookPatch["required"] as? List<*>).isNullOrEmpty())
        assertEquals(true, bookPatch.map("properties").map("title")["nullable"])
    }

    @Test
    fun `delete returns 204 with no response body schema`() {
        val del = operation("/books/{id}", "delete")
        val noContent = del.map("responses").map("204")
        assertEquals("The book was deleted", noContent["description"])
        assertNull(noContent["content"], "204 carries no body — Response<Unit> yields no serializer")
    }

    @Test
    fun `components hold every serializable type reachable from the routes`() {
        val schemas = doc.map("components").map("schemas")
        assertEquals(setOf("Book", "NewBook", "BookPatch", "Genre"), schemas.keys)
        assertEquals(listOf("FICTION", "NON_FICTION", "SCIENCE_FICTION", "HISTORY", "POETRY"), schemas.map("Genre")["enum"])
    }
}
