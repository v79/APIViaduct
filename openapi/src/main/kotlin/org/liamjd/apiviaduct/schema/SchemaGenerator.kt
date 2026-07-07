package org.liamjd.apiviaduct.schema

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind

/**
 * Walks kotlinx.serialization [SerialDescriptor]s to produce [SchemaModel]s, entirely at runtime
 * and without reflection — the core of the KSP-free OpenAPI generator (issue #30).
 *
 * A single generator instance accumulates the object definitions it discovers in [components],
 * keyed by their (simple) schema name. Call [generate] for each route's input/output descriptor;
 * afterwards [components] holds every `@Serializable` object type reachable from those roots, ready
 * to be emitted under `components/schemas`. Object types are always referenced by [SchemaModel.Ref]
 * so recursive and shared types are represented once.
 */
@OptIn(ExperimentalSerializationApi::class)
class SchemaGenerator {

    private val _components = linkedMapOf<String, SchemaModel>()

    /** The object/enum definitions discovered so far, keyed by schema name. Emit under `components/schemas`. */
    val components: Map<String, SchemaModel> get() = _components

    /**
     * Produce the schema for [descriptor]. Object and enum types are registered in [components] and
     * returned as a [SchemaModel.Ref]; everything else is returned inline.
     */
    fun generate(descriptor: SerialDescriptor): SchemaModel {
        val nullable = descriptor.isNullable
        return when (descriptor.kind) {
            is PrimitiveKind -> primitive(descriptor.kind as PrimitiveKind, nullable)

            SerialKind.ENUM -> {
                val values = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
                register(schemaName(descriptor), SchemaModel.EnumSchema(values))
                SchemaModel.Ref(schemaName(descriptor), nullable)
            }

            StructureKind.LIST ->
                SchemaModel.ArraySchema(generate(descriptor.getElementDescriptor(0)), nullable)

            StructureKind.MAP ->
                // OpenAPI keys are strings; the value schema is the second type argument.
                SchemaModel.MapSchema(generate(descriptor.getElementDescriptor(1)), nullable)

            StructureKind.CLASS, StructureKind.OBJECT -> {
                val name = schemaName(descriptor)
                // Register a placeholder first so recursive types don't loop forever.
                if (name !in _components) {
                    _components[name] = PLACEHOLDER
                    _components[name] = objectSchema(descriptor)
                }
                SchemaModel.Ref(name, nullable)
            }

            is PolymorphicKind, SerialKind.CONTEXTUAL ->
                // Not modelled in the targeted spec subset yet; treat as a free-form object.
                SchemaModel.ObjectSchema(emptyMap(), emptyList(), nullable)
        }
    }

    private fun objectSchema(descriptor: SerialDescriptor): SchemaModel.ObjectSchema {
        val properties = linkedMapOf<String, SchemaModel>()
        val required = mutableListOf<String>()
        for (i in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(i)
            properties[name] = generate(descriptor.getElementDescriptor(i))
            // OpenAPI "required" means no default value is available.
            if (!descriptor.isElementOptional(i)) required += name
        }
        return SchemaModel.ObjectSchema(properties, required)
    }

    private fun primitive(kind: PrimitiveKind, nullable: Boolean): SchemaModel.Primitive =
        when (kind) {
            PrimitiveKind.STRING, PrimitiveKind.CHAR -> SchemaModel.Primitive("string", nullable = nullable)
            PrimitiveKind.BOOLEAN -> SchemaModel.Primitive("boolean", nullable = nullable)
            PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT ->
                SchemaModel.Primitive("integer", "int32", nullable)
            PrimitiveKind.LONG -> SchemaModel.Primitive("integer", "int64", nullable)
            PrimitiveKind.FLOAT -> SchemaModel.Primitive("number", "float", nullable)
            PrimitiveKind.DOUBLE -> SchemaModel.Primitive("number", "double", nullable)
        }

    private fun register(name: String, schema: SchemaModel) {
        _components[name] = schema
    }

    /** kotlinx qualifies serial names and suffixes nullable ones with `?`; we want the bare simple name. */
    private fun schemaName(descriptor: SerialDescriptor): String =
        descriptor.serialName.removeSuffix("?").substringAfterLast('.')

    private companion object {
        val PLACEHOLDER = SchemaModel.ObjectSchema(emptyMap(), emptyList())
    }
}
