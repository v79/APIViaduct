package org.liamjd.apiviaduct.schema

/**
 * A minimal, in-memory model of an OpenAPI 3.1 Schema Object. This is the reflection-free
 * replacement for the KSP-scraped schema data (issue #30): every instance is produced by walking
 * a kotlinx.serialization [kotlinx.serialization.descriptors.SerialDescriptor], which is
 * compile-time generated code and therefore GraalVM native-image safe.
 *
 * Only the subset of the schema spec in `OpenAPISpecNotes.md` is represented. Nested `@Serializable`
 * types are emitted as a [Ref] pointing at a `components/schemas` entry rather than being inlined,
 * matching how OpenAPI documents deduplicate object definitions.
 */
sealed interface SchemaModel {
    val nullable: Boolean

    /** A scalar: `type` plus an optional OpenAPI `format` (e.g. `int32`, `int64`, `double`). */
    data class Primitive(
        val type: String,
        val format: String? = null,
        override val nullable: Boolean = false
    ) : SchemaModel

    /** An enum, rendered as a string schema with an `enum` list of the constant names. */
    data class EnumSchema(
        val values: List<String>,
        override val nullable: Boolean = false
    ) : SchemaModel

    /** An array; `items` is the schema of the element type. */
    data class ArraySchema(
        val items: SchemaModel,
        override val nullable: Boolean = false
    ) : SchemaModel

    /** A map, rendered via `additionalProperties` holding the value schema. */
    data class MapSchema(
        val additionalProperties: SchemaModel,
        override val nullable: Boolean = false
    ) : SchemaModel

    /** An inline object definition. Lives in `components/schemas`, referenced elsewhere by [Ref]. */
    data class ObjectSchema(
        val properties: Map<String, SchemaModel>,
        val required: List<String>,
        override val nullable: Boolean = false
    ) : SchemaModel

    /** A `$ref` to a named entry under `components/schemas`. */
    data class Ref(
        val name: String,
        override val nullable: Boolean = false
    ) : SchemaModel {
        val pointer: String get() = "#/components/schemas/$name"
    }
}
