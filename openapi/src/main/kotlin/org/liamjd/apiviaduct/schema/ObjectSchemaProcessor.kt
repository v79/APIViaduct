package org.liamjd.apiviaduct.schema

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import kotlin.math.log

/**
 * This class analyses the classes annotated with @OpenAPISchema, validating the source code, and generating the
 * OpenAPI schema objects for the valid classes
 * See https://spec.openapis.org/oas/latest.html#schema-object
 */
class ObjectSchemaProcessor(private val logger: KSPLogger) {

    /**
     * Validates the class annotated with @OpenAPISchema
     * The class must also be annotated with @Serializable from kotlinx.serialization
     * The class must have at least one property field
     */
    fun validateSymbol(symbol: KSAnnotated): Boolean {
        logger.info("Validating symbol: $symbol is KSClassDeclaration and has @Serializable annotation and at least one property field")
        return symbol is KSClassDeclaration && symbol.annotations.any { it.shortName.asString() == "Serializable" } && symbol.getAllProperties()
            .any()
    }

    /**
     * Builds the YAML for each class annotated with @OpenAPISchema
     */
    fun buildYamlForClasses(classes: Sequence<KSClassDeclaration>): String {
        val stringBuilder = StringBuilder()
        classes.forEach {
            val yaml = it.toYaml(logger)
            stringBuilder.append(yaml)
//            logger.info(yaml)
        }
        return stringBuilder.toString()
    }

    /**
     * Builds the YAML for a class annotated with @OpenAPISchema
     */
    private fun KSClassDeclaration.toYaml(logger: KSPLogger): String {
        val collections: List<String> = listOf("List", "Set", "Map")

        val stringBuilder = StringBuilder()
        stringBuilder.appendLine("${this.simpleName.asString()}:")
        stringBuilder.appendLine("  type: object")

        // get the list of required properties, i.e. those not nullable
        val requiredProperties = this.getAllProperties().filter { it.type.resolve().isMarkedNullable.not() }
        if (requiredProperties.any()) {
            stringBuilder.appendLine("  required:")
            requiredProperties.forEach { property ->
                stringBuilder.appendLine("    - ${property.simpleName.asString()}")
            }
        }

        stringBuilder.appendLine("  properties:")
        this.getAllProperties().forEach { property ->
            stringBuilder.appendLine("    ${property.simpleName.asString()}:")
            // if the property is a collection, we need to get the type of the collection
            // For now, we will represent all collections as arrays
            if (collections.contains(property.type.resolve().declaration.simpleName.asString())) {
                stringBuilder.appendLine("      type: array")
                stringBuilder.appendLine("      items:")
                // get the type of the collection
                stringBuilder.appendLine("        \$ref: '#/components/schemas/${property.type.resolve().arguments.first().type}'")
            } else {
                // if the property is an enum class, we should get the enum values
                // but I can't find out how to do that in KSP
                val kclassDec = property.type.resolve().declaration as KSClassDeclaration
                if (kclassDec.getEnumEntries().any()) {
                    // this appears to be an enum
                    stringBuilder.appendLine("      type: string") // assuming string for now
                    stringBuilder.appendLine("      enum:")
                    kclassDec.getEnumEntries().forEach {
                        stringBuilder.appendLine("        - ${it.simpleName.asString()}")
                    }
                } else {
                    // this is a simple property
                    stringBuilder.appendLine("      type: ${property.type.resolve().declaration.simpleName.asString()}")
                }
            }
        }

        return stringBuilder.toString()
    }

    fun KSClassDeclaration.getEnumEntries(): Sequence<KSDeclaration> {
        return declarations.filter { it.closestClassDeclaration()?.classKind == ClassKind.ENUM_ENTRY }
    }
}