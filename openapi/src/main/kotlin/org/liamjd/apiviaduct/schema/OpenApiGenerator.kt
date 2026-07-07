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
 * Known simplification: nullable schemas are emitted as `nullable: true` (OpenAPI 3.0 style) rather
 * than the 3.1 `type: [..., "null"]` form. Adequate for the targeted subset; revisit if strict 3.1
 * validation is required.
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

    /** Convert a [SchemaModel] into the map/list tree the [YamlEmitter] renders. */
    private fun schemaTree(model: SchemaModel): Map<String, Any?> = when (model) {
        is SchemaModel.Primitive -> linkedMapOf<String, Any?>(
            "type" to model.type,
            "format" to model.format
        ).withNullable(model.nullable)

        is SchemaModel.EnumSchema -> linkedMapOf<String, Any?>(
            "type" to "string",
            "enum" to model.values
        ).withNullable(model.nullable)

        is SchemaModel.ArraySchema -> linkedMapOf<String, Any?>(
            "type" to "array",
            "items" to schemaTree(model.items)
        ).withNullable(model.nullable)

        is SchemaModel.MapSchema -> linkedMapOf<String, Any?>(
            "type" to "object",
            "additionalProperties" to schemaTree(model.additionalProperties)
        ).withNullable(model.nullable)

        is SchemaModel.ObjectSchema -> linkedMapOf<String, Any?>(
            "type" to "object",
            "properties" to model.properties.mapValuesTo(linkedMapOf<String, Any?>()) { schemaTree(it.value) },
            "required" to model.required
        ).withNullable(model.nullable)

        is SchemaModel.Ref -> linkedMapOf<String, Any?>("\$ref" to model.pointer)
    }

    private fun LinkedHashMap<String, Any?>.withNullable(nullable: Boolean): LinkedHashMap<String, Any?> {
        if (nullable) this["nullable"] = true
        return this
    }

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
