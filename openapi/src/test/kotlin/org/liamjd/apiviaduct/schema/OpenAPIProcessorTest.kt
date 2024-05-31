package org.liamjd.apiviaduct.schema

import com.tschuchort.compiletesting.*
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.ir.backend.js.compile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenAPIProcessorTest {

    @Language("kotlin")
    val dataModels = """
              import org.liamjd.apiviaduct.schema.OpenAPISchema
              import org.liamjd.apiviaduct.schema.TestSchemaAnno
               
                @TestSchemaAnno
                data class TestClass(val name: String, val age: Int)
               
                @OpenAPISchema
                data class OpenAPITestClass(val name: String, val age: Int)

    """.trimIndent()

    @Language("kotlin")
    val controllerFunction = """
        import org.liamjd.apiviaduct.schema.OpenAPIRoute
        import org.liamjd.apiviaduct.schema.OpenAPISchema
        import org.liamjd.apiviaduct.routing.Request
        import org.liamjd.apiviaduct.routing.Response
        
        @TestSchemaAnno
        fun testFunction(request: Request<Unit>): Response<String> {
            return Response.OK("Controller says hi")
        }
    """.trimIndent()

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `can find data classes with TestSchemaAnno annotation`() {
        val kotlinSource = SourceFile.kotlin(
            name = "TestModels.kt",
            contents = dataModels, trimIndent = true, isMultiplatformCommonSource = false
        )
        val result = KotlinCompilation().apply {
            sources = listOf(kotlinSource)
            symbolProcessorProviders = listOf(OpenAPISchemaProcessorProvider())
            inheritClassPath = true
            kspArgs = mapOf("schemaAnnotation" to "org.liamjd.apiviaduct.schema.TestSchemaAnno").toMutableMap()
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        assert(result.messages.contains("APIViaduct Found class TestClass with @org.liamjd.apiviaduct.schema.TestSchemaAnno annotation"))
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `can find data classes with default OpenAPISchema annotation`() {
        val kotlinSource = SourceFile.kotlin(
            name = "TestModels.kt",
            contents = dataModels, trimIndent = true, isMultiplatformCommonSource = false
        )
        val result = KotlinCompilation().apply {
            sources = listOf(kotlinSource)
            symbolProcessorProviders = listOf(OpenAPISchemaProcessorProvider())
            inheritClassPath = true
        }.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        assert(result.messages.contains("APIViaduct Found class OpenAPITestClass with @org.liamjd.apiviaduct.schema.OpenAPISchema annotation"))
    }

/*    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `can find controller functions with OpenAPIRoute annotation`() {
        val kotlinSource = SourceFile.kotlin(
            name = "TestController.kt",
            contents = controllerFunction, trimIndent = true, isMultiplatformCommonSource = false
        )

        val result = KotlinCompilation().apply {
            sources = listOf(kotlinSource)
            symbolProcessorProviders = listOf(OpenAPISchemaProcessorProvider())
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        assert(result.messages.contains("TestController.kt"))
    }*/
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class TestSchemaAnno
