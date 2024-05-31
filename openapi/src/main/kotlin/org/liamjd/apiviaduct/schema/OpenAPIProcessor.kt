package org.liamjd.apiviaduct.schema

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration

class OpenAPIProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {

        // get and parse arguments
        val schemaAnnotation = environment.options["schemaAnnotation"] ?: "org.liamjd.apiviaduct.schema.OpenAPISchema"
        environment.logger.info("**** APIViaduct OpenAPI Processor scanning for '$schemaAnnotation' annotated classes ****")

        resolver.getAllFiles().forEach {
            environment.logger.info("APIViaduct scanning file: ${it.fileName}")
        }
        environment.logger.info("----------")
        resolver.getAllFiles().forEach { file ->
            file.declarations.forEach { declaration ->
                declaration.annotations.forEach { annotation ->
                    environment.logger.info("APIViaduct found annotation: $annotation")
                }
            }
        }
        val schemaClasses = resolver.getSymbolsWithAnnotation(schemaAnnotation, true)
            .filterIsInstance<KSClassDeclaration>()
        buildSchemaYaml(schemaClasses, schemaAnnotation)

        return schemaClasses.toList()
    }

    private fun buildSchemaYaml(schemas: Sequence<KSClassDeclaration>, schemaAnnotation: String) {
        val stringBuilder = StringBuilder()
        if (schemas.iterator().hasNext()) {
            schemas.forEach {
                environment.logger.warn("**** APIViaduct Found class ${it.simpleName.asString()} with @$schemaAnnotation annotation")
                stringBuilder.appendLine("${it.simpleName.asString()}:")
                stringBuilder.appendLine("  type: object")
                stringBuilder.appendLine("  properties:")
                it.getAllProperties().forEach { property ->
                    stringBuilder.appendLine("    ${property.simpleName.asString()}:")
                    stringBuilder.appendLine("      type: ${property.type}")
                }
            }
            writeToFile(stringBuilder.toString(), "api-schema")
        } else {
            environment.logger.warn("**** APIViaduct No more classes found with $schemaAnnotation annotation")
        }
    }

    private fun writeToFile(content: String, filename: String) {
        try {
            if (environment.codeGenerator.generatedFile.isEmpty()) {
                val output = environment.codeGenerator.createNewFile(
                    Dependencies.ALL_FILES,
                    "openapi.schema",
                    filename,
                    "yaml"
                )
                output.write(content.toByteArray(Charsets.UTF_8))
                environment.logger.info("**** APIViaduct Written output file to ${environment.codeGenerator.generatedFile.first().path}")
            } else {
                environment.logger.warn("**** APIViaduct No output file generated")
            }
        } catch (fee: FileAlreadyExistsException) {
            environment.logger.info("**** APIViaduct Ignoring file already exists error writing output file: ${fee.message}")
        }
    }
}

class OpenAPISchemaProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return OpenAPIProcessor(environment)
    }
}