package org.liamjd.apiviaduct.schema

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

class OpenAPIProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {

        // get and parse arguments
        val schemaAnnotation = environment.options["schemaAnnotation"] ?: "org.liamjd.apiviaduct.schema.OpenAPISchema"
        val routeAnnotation = environment.options["routeAnnotation"] ?: "org.liamjd.apiviaduct.schema.OpenAPIRoute"

        // debugging - list all the scanned files
        /* environment.logger.info("----------")
         resolver.getAllFiles().forEach {
             environment.logger.info("APIViaduct scanning file: ${it.fileName}")
         }*/
        // debugging - list all the declarations in the scanned files
        resolver.getAllFiles().forEach { file ->
            file.declarations.forEach { declaration ->
                environment.logger.info("\tAPIViaduct found declaration in: ${declaration.qualifiedName?.asString()}")
                declaration.annotations.forEach { annotation ->
                    environment.logger.info("\t\tAPIViaduct found annotation: $annotation")
                }
            }
        }
        // get the classes annotated with the schema annotation
        val schemaClasses = resolver.getSymbolsWithAnnotation(schemaAnnotation, true)
            .filterIsInstance<KSClassDeclaration>()
        buildSchemaYaml(schemaClasses, schemaAnnotation)


//        environment.logger.info("**** APIViaduct OpenAPI Processor scanning for '$routeAnnotation' annotated functions ****")
        // get the functions annotated with the route annotation
        val routeMethods =
            resolver.getSymbolsWithAnnotation(routeAnnotation, true).filterIsInstance<KSFunctionDeclaration>()
        buildFunctionYaml(routeMethods, routeAnnotation)

        return schemaClasses.toList()
    }

    private fun buildSchemaYaml(schemas: Sequence<KSClassDeclaration>, schemaAnnotation: String) {
        val stringBuilder = StringBuilder()
        if (schemas.iterator().hasNext()) {
            schemas.forEach {
                environment.logger.warn("APIViaduct found class ${it.simpleName.asString()} with @$schemaAnnotation annotation")
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
            environment.logger.warn("APIViaduct No more classes found with $schemaAnnotation annotation")
        }
    }

    private fun buildFunctionYaml(routes: Sequence<KSFunctionDeclaration>, routeAnnotation: String) {
        val stringBuilder = StringBuilder()
        if (routes.iterator().hasNext()) {
            routes.forEach { route ->
                environment.logger.warn("APIViaduct found function ${route.simpleName.asString()} with @$routeAnnotation annotation")
                if (route.parameters.isEmpty()) {
                    environment.logger.warn("\tNo parameters found for route. Skipping")
                    return@forEach
                } else {
                    // the first parameter MUST be a Request<>
                    val param = route.parameters.first()
                    if (param.type.toString() != "Request") {
                        environment.logger.warn("\tFirst parameter is not a Request<>. Skipping")
                        return@forEach
                    }
                    // the first parameter MUST have a type argument <>
                    if (param.type.element?.typeArguments?.isEmpty() == true) {
                        environment.logger.warn("\tFirst parameter does not have a type argument. Skipping")
                        return@forEach
                    }

                    route.parameters.forEach { rParam ->
                        environment.logger.warn("\tParameter: ${rParam.name?.asString()}, type: ${rParam.type}<${rParam.type.element?.typeArguments?.first()?.type}>")
                    }
                    stringBuilder.appendLine()
                }

                // the return must be a Response<>
                route.returnType?.let {
                    environment.logger.warn("\tReturn type: $it")
                }
//                stringBuilder.appendLine("${it.simpleName.asString()}:")
//                stringBuilder.appendLine("  type: object")
//                stringBuilder.appendLine("  properties:")
//                it.parameters.forEach { parameter ->
//                    stringBuilder.appendLine("    ${parameter.name?.asString()}:")
//                    stringBuilder.appendLine("      type: ${parameter.type}")
//                }
            }
            writeToFile(stringBuilder.toString(), "api-routes")
        } else {
            environment.logger.warn("APIViaduct No more functions found with $routeAnnotation annotation")
        }
    }

    private fun writeToFile(content: String, filename: String) {
        try {
            if (environment.codeGenerator.generatedFile.isEmpty()) {
                val output = environment.codeGenerator.createNewFileByPath(
                    dependencies = Dependencies.ALL_FILES,
                    path = "src/main/resources/$filename",
                    extensionName = "yaml"
                )
                output.write(content.toByteArray(Charsets.UTF_8))
                environment.logger.info("APIViaduct Written output file to ${environment.codeGenerator.generatedFile.first().path}")
            } else {
                environment.logger.warn("APIViaduct No output file generated")
            }
        } catch (fee: FileAlreadyExistsException) {
            environment.logger.info("APIViaduct Ignoring file already exists error writing output file: ${fee.message}")
        }
    }
}

class OpenAPISchemaProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return OpenAPIProcessor(environment)
    }
}