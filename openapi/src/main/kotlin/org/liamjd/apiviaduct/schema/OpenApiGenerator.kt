package org.liamjd.apiviaduct.schema

import kotlinx.serialization.KSerializer
import org.liamjd.apiviaduct.routing.OpenApiInfo
import org.liamjd.apiviaduct.routing.RequestPredicate
import org.liamjd.apiviaduct.routing.Router

/**
 * Generates an OpenAPI 3.1 document from a [Router], reflection-free (issue #30).
 *
 * Everything structural is derived from the registered routes — paths, methods, media types, path
 * parameters, and request/response schemas (from the captured input/output serializers via
 * [SchemaGenerator]). The hand-authored [org.liamjd.apiviaduct.routing.RouteSpec] attached with the
 * `spec { }` DSL only adds prose (summaries, descriptions, tags, parameter docs, extra responses).
 * Document-level metadata that no route can supply — `info` and `servers` — is passed in as
 * [OpenApiInfo].
 *
 * Call [generateYaml] for the document as a YAML string, or [buildDocument] for the underlying tree.
 *
 * Document-level `info`/`servers` come from the router's `openApi { }` block; pass [infoOverride] to
 * supply or replace them programmatically (it takes precedence over the router's declaration).
 *
 * Nullability follows OpenAPI 3.1 / JSON Schema 2020-12 (`type: [..., "null"]`, and `anyOf` for a
 * nullable `$ref`) rather than the obsolete 3.0 `nullable: true` keyword — see [schemaTree].
 */
class OpenApiGenerator(
    private val router: Router,
    infoOverride: OpenApiInfo? = null
) {
    private val info: OpenApiInfo = infoOverride ?: router.openApiInfo
        ?: error("No OpenAPI info: declare an openApi { } block in the router or pass infoOverride to OpenApiGenerator")

    private val schemaGen = SchemaGenerator()

    /** The full OpenAPI document rendered as YAML. */
    fun generateYaml(): String = YamlEmitter.emit(buildDocument())

    /**
     * Build the document as an ordered tree of maps/lists/scalars — the structure [YamlEmitter]
     * renders. Exposed so the document can be inspected or serialized by other means.
     */
    fun buildDocument(): Map<String, Any?> {
        // Grouped routes keep their serializers/spec only on the RouteFunction's predicate (the map
        // key is a copy that loses them), so always read predicates from the values.
        val predicates = router.routes.values.map { it.predicate }

        // paths must be built before components so the schema generator has walked every serializer.
        val paths = buildPaths(predicates)
        val components = buildComponents()

        return linkedMapOf(
            "openapi" to "3.1.0",
            "info" to buildInfo(),
            "servers" to info.servers.map { server ->
                linkedMapOf("url" to server.url, "description" to server.description)
            },
            "paths" to paths,
            "components" to components
        )
    }

    private fun buildInfo(): Map<String, Any?> = linkedMapOf(
        "title" to info.title,
        "version" to info.version,
        "summary" to info.summary,
        "description" to info.description,
        "termsOfService" to info.termsOfService,
        "contact" to info.contact?.let {
            linkedMapOf("name" to it.name, "url" to it.url, "email" to it.email)
        },
        "license" to info.license?.let {
            linkedMapOf("name" to it.name, "identifier" to it.identifier, "url" to it.url)
        }
    )

    private fun buildPaths(predicates: List<RequestPredicate>): Map<String, Any?> {
        val paths = linkedMapOf<String, LinkedHashMap<String, Any?>>()
        for (predicate in predicates) {
            val operations = paths.getOrPut(predicate.pathPattern) { linkedMapOf() }
            operations[predicate.method.lowercase()] = buildOperation(predicate)
        }
        return paths
    }

    private fun buildOperation(predicate: RequestPredicate): Map<String, Any?> {
        val spec = predicate.spec
        val operation = linkedMapOf<String, Any?>(
            "summary" to spec?.summary,
            "description" to spec?.description,
            "operationId" to spec?.operationId,
            "tags" to (spec?.tags ?: emptyList()),
            "parameters" to buildParameters(predicate),
            "requestBody" to buildRequestBody(predicate),
            "responses" to buildResponses(predicate)
        )
        return operation
    }

    private fun buildParameters(predicate: RequestPredicate): List<Map<String, Any?>> {
        val spec = predicate.spec
        val params = mutableListOf<Map<String, Any?>>()
        // Path parameters are known from the route pattern; the spec only documents them.
        for (name in predicate.pathVariables) {
            params += linkedMapOf(
                "name" to name,
                "in" to "path",
                "required" to true,
                "description" to spec?.pathParamDocs?.get(name)?.description,
                "schema" to linkedMapOf<String, Any?>("type" to "string")
            )
        }
        // Query parameters exist only if the spec documents them.
        spec?.queryParams?.forEach { (name, param) ->
            params += linkedMapOf(
                "name" to name,
                "in" to "query",
                "required" to param.required,
                "description" to param.description,
                "schema" to linkedMapOf<String, Any?>("type" to "string")
            )
        }
        return params
    }

    private fun buildRequestBody(predicate: RequestPredicate): Map<String, Any?>? {
        val serializer = predicate.inputSerializer ?: return null
        if (isBodyless(serializer)) return null
        return linkedMapOf(
            "required" to true,
            "content" to mediaTypeContent(predicate.accepts, serializer)
        )
    }

    private fun buildResponses(predicate: RequestPredicate): Map<String, Any?> {
        val spec = predicate.spec
        val responses = linkedMapOf<String, Any?>()

        // Documented responses first, preserving their prose.
        spec?.responses?.forEach { (code, resp) ->
            responses[code.toString()] = linkedMapOf<String, Any?>(
                "description" to (resp.description ?: reasonPhrase(code))
            )
        }

        // Attach the response body schema to the primary success response. A `Unit` return type
        // (e.g. a 204 handler) is serializable but carries no meaningful body, so it is skipped.
        val outputSerializer = predicate.outputSerializer?.takeUnless { isBodyless(it) }
        if (outputSerializer != null) {
            val successCode = spec?.responses?.keys?.firstOrNull { it in 200..299 } ?: 200
            @Suppress("UNCHECKED_CAST")
            val entry = responses.getOrPut(successCode.toString()) {
                linkedMapOf<String, Any?>("description" to reasonPhrase(successCode))
            } as LinkedHashMap<String, Any?>
            entry["content"] = mediaTypeContent(predicate.supplies, outputSerializer)
        }

        // responses is a required object; guarantee at least one entry.
        if (responses.isEmpty()) {
            responses["200"] = linkedMapOf<String, Any?>("description" to reasonPhrase(200))
        }
        return responses
    }

    /** `kotlin.Unit` is serializable but represents "no body" for request/response purposes. */
    private fun isBodyless(serializer: KSerializer<*>): Boolean =
        serializer.descriptor.serialName.removeSuffix("?") == "kotlin.Unit"

    /** Build a `content` map: one media-type entry per mime type, each carrying the same schema. */
    private fun mediaTypeContent(mimeTypes: Set<*>, serializer: KSerializer<*>): Map<String, Any?> {
        val schema = schemaTree(schemaGen.generate(serializer.descriptor))
        val effective = if (mimeTypes.isEmpty()) listOf("application/json") else mimeTypes.map { it.toString() }
        return effective.associateWithTo(linkedMapOf()) { linkedMapOf<String, Any?>("schema" to schema) }
    }

    private fun buildComponents(): Map<String, Any?> {
        val schemas = schemaGen.components.mapValuesTo(linkedMapOf<String, Any?>()) { (_, model) ->
            schemaTree(model)
        }
        return linkedMapOf("schemas" to schemas)
    }

    /**
     * Convert a [SchemaModel] into the map/list tree the [YamlEmitter] renders. Nullability follows
     * OpenAPI 3.1 / JSON Schema 2020-12: a nullable typed value carries `"null"` in its `type` list
     * (`type: [string, "null"]`), and a nullable `$ref` is wrapped in `anyOf` with a null type —
     * `nullable: true` (the 3.0 keyword) is not used, as it has no meaning under a 3.1.0 document.
     */
    private fun schemaTree(model: SchemaModel): Map<String, Any?> = when (model) {
        is SchemaModel.Primitive -> linkedMapOf<String, Any?>(
            "type" to nullableType(model.type, model.nullable),
            "format" to model.format
        )

        is SchemaModel.EnumSchema -> linkedMapOf<String, Any?>(
            "type" to nullableType("string", model.nullable),
            "enum" to model.values
        )

        is SchemaModel.ArraySchema -> linkedMapOf<String, Any?>(
            "type" to nullableType("array", model.nullable),
            "items" to schemaTree(model.items)
        )

        is SchemaModel.MapSchema -> linkedMapOf<String, Any?>(
            "type" to nullableType("object", model.nullable),
            "additionalProperties" to schemaTree(model.additionalProperties)
        )

        is SchemaModel.ObjectSchema -> linkedMapOf<String, Any?>(
            "type" to nullableType("object", model.nullable),
            "properties" to model.properties.mapValuesTo(linkedMapOf<String, Any?>()) { schemaTree(it.value) },
            "required" to model.required
        )

        is SchemaModel.Ref ->
            if (model.nullable) {
                // A $ref can't carry a sibling type in JSON Schema 2020-12, so wrap it.
                linkedMapOf<String, Any?>(
                    "anyOf" to listOf(
                        linkedMapOf("\$ref" to model.pointer),
                        linkedMapOf("type" to "null")
                    )
                )
            } else {
                linkedMapOf<String, Any?>("\$ref" to model.pointer)
            }
    }

    /** The 3.1 type form: a bare `type` when non-nullable, or a `[type, "null"]` list when nullable. */
    private fun nullableType(type: String, nullable: Boolean): Any =
        if (nullable) listOf(type, "null") else type

    private fun reasonPhrase(code: Int): String = when (code) {
        200 -> "OK"
        201 -> "Created"
        202 -> "Accepted"
        204 -> "No Content"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        406 -> "Not Acceptable"
        409 -> "Conflict"
        500 -> "Internal Server Error"
        501 -> "Not Implemented"
        else -> "Response"
    }
}
