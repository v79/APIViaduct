package org.liamjd.apiviaduct.schema

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration

class OpenAPIInfoProcessor(private val logger: KSPLogger) {

    /**
     * Validates the class annotated with @OpenAPIInfo
     * The class must inherit from LambdaRouter
     */
    fun validateSymbol(symbol: KSAnnotated): Boolean {
        logger.info("Validating symbol: $symbol is KSClassDeclaration and inherits from LambdaRouter")
        // I want to check the fully qualified name of the super type but it's always null, so I'm checking the simple name instead
        // this is not ideal
        return symbol is KSClassDeclaration &&
                symbol.superTypes.any { it.resolve().declaration.simpleName.asString() == "LambdaRouter" }
    }

    /**
     * Builds the YAML for the OpenAPI info object from the class annotated with @OpenAPIInfo
     * The title and version are required, the rest are optional
     */
    fun buildInfoYamlFromAnnotation(infoClass: KSClassDeclaration): String {
        // get the attributes from the annotation
        val annotation = infoClass.annotations.first { it.shortName.asString() == "OpenAPIInfo" }
        val stringBuilder = StringBuilder()
        stringBuilder.appendLine("openapi: 3.1.0")
        stringBuilder.appendLine("info:")
        stringBuilder.appendLine("  title: ${annotation.getArgumentValue("title")}")
        stringBuilder.appendLine("  version: ${annotation.getArgumentValue("version")}")
        if (annotation.getArgumentValue("description").isNotEmpty()) {
            stringBuilder.appendLine("  description: ${annotation.getArgumentValue("description")}")
        }
        if (annotation.getArgumentValue("termsOfService").isNotEmpty()) {
            stringBuilder.appendLine("  termsOfService: ${annotation.getArgumentValue("termsOfService")}")
        }
        if (annotation.getArgumentValue("contact").isNotEmpty()) {
            stringBuilder.appendLine("  contact: ${annotation.getArgumentValue("contact")}")
        }
        if (annotation.getArgumentValue("license").isNotEmpty()) {
            stringBuilder.appendLine("  license: ${annotation.getArgumentValue("license")}")
        }
        return stringBuilder.toString()
    }

    /**
     * Safely checks for the value of an argument in an annotation
     */
    private fun KSAnnotation.getArgumentValue(name: String): String {
        return this.arguments.firstOrNull { it.name?.asString() == name }?.value.toString()
    }
}