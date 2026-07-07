package org.liamjd.apiviaduct.routing

/**
 * Human-authored OpenAPI metadata for a single route, attached via the [spec] DSL.
 *
 * Everything the router already knows — path, method, media types (`consumes`/`produces`),
 * path variables (`pathVariables`), and request/response schemas (from the captured serializers) —
 * is derived automatically and is *not* stored here. [RouteSpec] only holds the prose and extra
 * detail a machine cannot infer: summaries, descriptions, tags, parameter documentation, and
 * additional response codes.
 *
 * @property summary a short summary of what the operation does
 * @property description a longer, richer description of the operation
 * @property operationId an optional unique identifier for the operation
 * @property tags tags used to group operations in generated documentation
 * @property pathParamDocs documentation for path parameters, keyed by parameter name
 * @property queryParams documented query parameters, keyed by parameter name
 * @property responses documented responses, keyed by HTTP status code
 */
data class RouteSpec(
    var summary: String? = null,
    var description: String? = null,
    var operationId: String? = null,
    val tags: MutableList<String> = mutableListOf(),
    val pathParamDocs: MutableMap<String, ParamSpec> = mutableMapOf(),
    val queryParams: MutableMap<String, ParamSpec> = mutableMapOf(),
    val responses: MutableMap<Int, ResponseSpec> = mutableMapOf()
)

/**
 * Documentation for a single parameter (path or query).
 * @property description a human-readable description of the parameter
 * @property required whether the parameter is required (always true for path parameters)
 */
data class ParamSpec(
    val description: String? = null,
    val required: Boolean = true
)

/**
 * Documentation for a single response.
 * @property description a human-readable description of the response
 */
data class ResponseSpec(
    val description: String? = null
)

/**
 * DSL builder for a [RouteSpec]. Mirrors the router's existing chaining idiom so a spec block
 * reads naturally on the end of a route definition:
 *
 * ```kotlin
 * get("/{id}", ::getCustomer)
 *     .spec {
 *         summary = "Fetch a single customer"
 *         tags("customers")
 *         pathParam("id", "The customer's unique id")
 *         response(200, "The customer record")
 *         response(404, "No customer with that id")
 *     }
 * ```
 */
class SpecBuilder {
    var summary: String? = null
    var description: String? = null
    var operationId: String? = null

    private val tags = mutableListOf<String>()
    private val pathParamDocs = mutableMapOf<String, ParamSpec>()
    private val queryParams = mutableMapOf<String, ParamSpec>()
    private val responses = mutableMapOf<Int, ResponseSpec>()

    /** Add one or more tags used to group this operation in generated documentation. */
    fun tags(vararg tag: String) {
        tags.addAll(tag)
    }

    /** Document a path parameter. Path parameters are always required per the OpenAPI spec. */
    fun pathParam(name: String, description: String? = null) {
        pathParamDocs[name] = ParamSpec(description = description, required = true)
    }

    /** Document a query parameter. */
    fun queryParam(name: String, description: String? = null, required: Boolean = false) {
        queryParams[name] = ParamSpec(description = description, required = required)
    }

    /** Document a response for the given HTTP status code. */
    fun response(statusCode: Int, description: String? = null) {
        responses[statusCode] = ResponseSpec(description = description)
    }

    internal fun build(): RouteSpec = RouteSpec(
        summary = summary,
        description = description,
        operationId = operationId,
        tags = tags,
        pathParamDocs = pathParamDocs,
        queryParams = queryParams,
        responses = responses
    )
}

/**
 * Attach OpenAPI documentation to a route. Returns the same [RequestPredicate] so it can be
 * chained after `expects`/`supplies`, consistent with the rest of the routing DSL.
 */
fun RequestPredicate.spec(block: SpecBuilder.() -> Unit): RequestPredicate {
    this.spec = SpecBuilder().apply(block).build()
    return this
}
