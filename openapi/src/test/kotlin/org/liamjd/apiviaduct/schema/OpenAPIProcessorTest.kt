package org.liamjd.apiviaduct.schema

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspArgs
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenAPIProcessorTest {

    @Language("kotlin")
    val dataModels = """
          import org.liamjd.apiviaduct.schema.OpenAPISchema
          import org.liamjd.apiviaduct.schema.TestSchemaAnno
          import kotlinx.serialization.Serializable
           
          @TestSchemaAnno
          @Serializable
          data class TestClass(val name: String, val age: Int)
           
          @OpenAPISchema
          @Serializable
          data class OpenAPITestClass(val name: String, val age: Int)
            
            @Serializable
            @OpenAPISchema
            data class Person(val name: String, val nicknames: List<String>, val color: Color) 
enum class Color {
  RED, GREEN, BLUE
}


    """.trimIndent()

    @Language("kotlin")
    val controllerFunction = """
        import org.liamjd.apiviaduct.schema.OpenAPIPath
        import org.liamjd.apiviaduct.schema.OpenAPISchema
        import org.liamjd.apiviaduct.routing.Request
        import org.liamjd.apiviaduct.routing.Response
        
        class TestController {
            @OpenAPIPath("Test function", "This is a test function")
            fun testFunction(request: Request<Person>): Response<String> {
                return Response.OK("Controller says hi")
            }
        }
    """.trimIndent()

    /**
     * This imagined use case is NOT possible with KSP, as KSP only recognizes declared symbols (functions, classes, types etc) and does NOT
     * look inside function bodies. Therefore, it is not possible to scan for annotations on a call to a lambda function like "get(/path)"
     */
    @Language("kotlin")
    val lambdaRouteFunction = """
        import org.liamjd.apiviaduct.schema.OpenAPILambdaRoute
        import org.liamjd.apiviaduct.routing.Request
        import org.liamjd.apiviaduct.routing.Response
              
        fun get(path: String, handler: (Request<T>) -> Response<I>) {
            // do something
        }
        class Greep {
          init {
            @OpenAPILambdaRoute
            get("/test") { request: Request<Person> -> Response.OK("Controller says hi") }
          }
        }
    """.trimIndent()

    @Language("kotlin")
    val completeLambdaRouter = """
        import org.liamjd.apiviaduct.routing.*
        import org.liamjd.apiviaduct.schema.OpenAPIInfo

        @OpenAPIInfo("My test API", "1.0", "This is a test API")
        class MyLambdaRouter : LambdaRouter() {
            override val router = lambdaRouter {
                get("/hello") {
                    request: Request<Unit> -> Response.OK("Hello, world!")
                }
            }
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
//        assert(result.messages.contains("APIViaduct found class TestClass with @org.liamjd.apiviaduct.schema.TestSchemaAnno annotation"))
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
//        assert(result.messages.contains("APIViaduct found class OpenAPITestClass with @org.liamjd.apiviaduct.schema.OpenAPISchema annotation"))
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `can find controller functions with OpenAPIPath annotation`() {
        val kotlinSource = SourceFile.kotlin(
            name = "TestController.kt",
            contents = controllerFunction, trimIndent = true, isMultiplatformCommonSource = false
        )

        val result = KotlinCompilation().apply {
            sources = listOf(kotlinSource)
            symbolProcessorProviders = listOf(OpenAPISchemaProcessorProvider())
            inheritClassPath = true
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        assert(result.messages.contains("APIViaduct found function testFunction with @org.liamjd.apiviaduct.schema.OpenAPIPath annotation"))
    }


    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `can find openAPIInfo annotation`() {
        val kotlinSource = SourceFile.kotlin(
            name = "TestController.kt",
            contents = completeLambdaRouter, trimIndent = true, isMultiplatformCommonSource = false
        )

        val result = KotlinCompilation().apply {
            sources = listOf(kotlinSource)
            symbolProcessorProviders = listOf(OpenAPISchemaProcessorProvider())
            inheritClassPath = true
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class TestSchemaAnno

@OpenAPISchema
class Person(val name: String, val age: Int)
