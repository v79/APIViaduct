package org.liamjd.apiviaduct.schema

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpenApiCliTest {

    private val bookRouterFqn = "org.liamjd.apiviaduct.schema.sample.BookLibraryRouter"

    @Test
    fun `generate reflectively builds the document from a LambdaRouter subclass`() {
        val yaml = OpenApiCli.generate(bookRouterFqn)
        assertTrue(yaml.startsWith("openapi: 3.1.0"))
        assertTrue(yaml.contains("title: Book Library API"))
        assertTrue(yaml.contains("/books/{id}:"))
    }

    @Test
    fun `main writes the document to the given file, creating parent directories`() {
        val out = File.createTempFile("openapi-cli", ".yaml").let { File(it.parentFile, "sub/dir/openapi.yaml") }
        out.parentFile.deleteRecursively()
        try {
            OpenApiCli.main(arrayOf(bookRouterFqn, out.path))
            assertTrue(out.exists(), "the CLI should create the output file and its parent directories")
            assertTrue(out.readText().contains("title: Book Library API"))
        } finally {
            out.parentFile.deleteRecursively()
        }
    }

    @Test
    fun `wrong number of arguments fails with usage`() {
        val error = assertFailsWith<IllegalArgumentException> { OpenApiCli.main(arrayOf("only-one-arg")) }
        assertTrue(error.message!!.contains("Usage"))
    }

    @Test
    fun `an unknown class name fails clearly`() {
        val error = assertFailsWith<IllegalArgumentException> { OpenApiCli.generate("com.acme.DoesNotExist") }
        assertTrue(error.message!!.contains("Could not instantiate"))
    }

    @Test
    fun `a class that is not a LambdaRouter fails clearly`() {
        val error = assertFailsWith<IllegalArgumentException> { OpenApiCli.generate("java.lang.String") }
        assertTrue(error.message!!.contains("is not a"))
    }

    @Test
    fun `a router with no openApi block surfaces the missing-info error`() {
        // NoInfoRouter declares routes but no openApi { }, so generation must fail.
        assertEquals(
            true,
            assertFailsWith<IllegalStateException> {
                OpenApiCli.generate("org.liamjd.apiviaduct.schema.NoInfoRouter")
            }.message!!.contains("No OpenAPI info")
        )
    }
}

/** A router without an `openApi { }` block, used to prove the CLI surfaces the missing-info error. */
class NoInfoRouter : org.liamjd.apiviaduct.routing.LambdaRouter() {
    override val router = org.liamjd.apiviaduct.routing.lambdaRouter {
        get("/ping", { _: org.liamjd.apiviaduct.routing.Request<Unit> ->
            org.liamjd.apiviaduct.routing.Response.ok("pong")
        })
    }
}
