package org.liamjd.apiviaduct.schema

import org.liamjd.apiviaduct.routing.LambdaRouter
import java.io.File

/**
 * Build-time entry point that generates an OpenAPI document from a router and writes it to a file.
 * It is designed to be driven from a Gradle `JavaExec` task on the consumer's runtime classpath — the
 * router is instantiated and the document generated on the build JVM, never inside a native image, so
 * the reflective class-loading here does not affect the router's GraalVM native-image safety.
 *
 * ```kotlin
 * // consumer build.gradle.kts
 * val openApiGenerator by configurations.creating
 * dependencies {
 *     openApiGenerator("org.liamjd.apiviaduct:openapi:<version>")
 * }
 * tasks.register<JavaExec>("generateOpenApi") {
 *     group = "documentation"
 *     classpath = sourceSets.main.get().runtimeClasspath + openApiGenerator
 *     mainClass.set("org.liamjd.apiviaduct.schema.OpenApiCli")
 *     args(
 *         "com.acme.MyRouter",
 *         layout.buildDirectory.file("openapi/openapi.yaml").get().asFile.path
 *     )
 * }
 * ```
 *
 * Arguments: `[0]` the fully-qualified name of a [LambdaRouter] subclass with a public no-arg
 * constructor; `[1]` the path of the YAML file to write (parent directories are created). The router
 * must declare an `openApi { }` block so its `info`/`servers` metadata is available.
 */
object OpenApiCli {

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) {
            "Usage: OpenApiCli <router-class-fqn> <output-file>. Received ${args.size} argument(s)."
        }
        val (routerClassName, outputPath) = args
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(generate(routerClassName))
        println("OpenAPI document written to ${outputFile.absolutePath}")
    }

    /**
     * Instantiate the named [LambdaRouter] subclass and generate its OpenAPI document as YAML.
     * Visible for testing so the reflective path can be exercised without touching the filesystem.
     */
    internal fun generate(routerClassName: String): String {
        val instance = try {
            Class.forName(routerClassName).getDeclaredConstructor().newInstance()
        } catch (e: ReflectiveOperationException) {
            throw IllegalArgumentException(
                "Could not instantiate '$routerClassName'. It must be a ${LambdaRouter::class.qualifiedName} " +
                    "subclass with a public no-arg constructor.",
                e
            )
        }
        val lambdaRouter = instance as? LambdaRouter
            ?: throw IllegalArgumentException(
                "'$routerClassName' is not a ${LambdaRouter::class.qualifiedName}."
            )
        return OpenApiGenerator(lambdaRouter.router).generateYaml()
    }
}
