package org.liamjd.apiviaduct.schema

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration

class OpenAPIProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        environment.logger.warn("**** APIViaduct OpenAPI Processor")
        resolver.getAllFiles().forEach {
            environment.logger.warn("**** APIViaduct File: ${it.fileName}")
        }
        val annotatedClasses = resolver.getSymbolsWithAnnotation(OpenAPISchema::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()

        annotatedClasses.forEach {
            environment.logger.warn("\t**** APIViaduct ${it.simpleName.asString()}")
        }

        val stringBuilder = StringBuilder()
        if (annotatedClasses.iterator().hasNext()) {
            annotatedClasses.forEach {
                environment.logger.warn("**** APIViaduct Found class ${it.simpleName.asString()} with @OpenAPISchema annotation")
                stringBuilder.appendLine("${it.simpleName.asString()}:")
                stringBuilder.appendLine("  type: object")
                stringBuilder.appendLine("  properties:")
                it.getAllProperties().forEach { property ->
                    stringBuilder.appendLine("    ${property.simpleName.asString()}:")
                    stringBuilder.appendLine("      type: ${property.type}")
                }
            }
        } else {
            environment.logger.warn("**** APIViaduct No more classes found with @OpenAPISchema annotation")

            return emptyList()
        }
        environment.logger.warn("**** APIViaduct Output is:")
        environment.logger.warn(stringBuilder.toString())
        writeToFile(stringBuilder.toString())
        return emptyList()
    }

    private fun writeToFile(content: String) {
        val output = environment.codeGenerator.createNewFile(
            Dependencies.ALL_FILES,
            "openapi.schema",
            "api-schema",
            "yaml"
        )
        output.write(content.toByteArray(Charsets.UTF_8))
        environment.logger.info("**** APIViaduct Written output file to build/generated/ksp/kotlin/main/openapi/schema/api-schema.yaml")
    }
}

class OpenAPISchemaProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return OpenAPIProcessor(environment)
    }
}