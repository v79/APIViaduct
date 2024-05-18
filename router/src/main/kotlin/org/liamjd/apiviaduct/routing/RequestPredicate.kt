package org.liamjd.apiviaduct.routing

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import org.liamjd.apiviaduct.routing.extensions.acceptedMediaTypes
import org.liamjd.apiviaduct.routing.extensions.contentType
import java.util.*
import kotlin.reflect.KType

/**
 * A RequestPredicate is descriptor of a route set up in the router. It provides a way to match incoming requests from the AWSGatewayProxyRequest.
 * @param method the HTTP method (GET, PUT, etc.)
 * @param pathPattern the string representing the route, e.g. `/customers/get/{id}`
 * @param consumes a set of Mime Types it accepts
 * @param produces a set of Mime Types it replies with
 * @property accepts is an alias for consumes
 * @property supplies is an alias for produces
 * @property kType is the Kotlin type of the request body, or null
 */
internal data class RequestPredicate(
    val method: String, var pathPattern: String,
    internal var consumes: Set<MimeType>,
    internal var produces: Set<MimeType>
) {
    private var kType: KType? = null
    val accepts
        get() = consumes
    val supplies
        get() = produces

    private val routeParts
        get() = pathPattern.split("/")

    val pathVariables: List<String>
        get() = routeParts.filter { it.startsWith("{") && it.endsWith("}") }.map { it.removeSurrounding("{", "}") }

    var headerOverrides = mutableMapOf<String, String>()
        private set

    // FUTURE: Add filters or middleware
    // FUTURE: Add APISpecification

    fun match(request: APIGatewayProxyRequestEvent) =
        RequestMatchResult(matchPath = pathMatches(request.path),
            matchMethod = methodMatches(request),
            matchAcceptType = acceptMatches(request, produces),
            matchContentType = when {
                consumes.isEmpty() -> true
                request.contentType() == null -> false
                else -> {
                    val requestsType = request.contentType()
                    requestsType?.let {
                        consumes.contains(MimeType.parse(it))
                    } ?: false
                }
            })

    /**
     * Check if the given path matches the path pattern
     * @param inputPath the path to check
     * @return true if the path matches the pattern
     */
    private fun pathMatches(inputPath: String): Boolean {
        val inputParts = inputPath.split("/")
        val routeParts = pathPattern.split("/")

        if (routeParts.size != inputParts.size) return false

        return routeParts.indices.all { i ->
            routeParts[i].startsWith("{") && routeParts[i].endsWith("}") || inputParts[i] == routeParts[i]
        }
    }

    /**
     * Simple check to see if the method matches
     */
    private fun methodMatches(request: APIGatewayProxyRequestEvent) = method.equals(request.httpMethod, true)

    /**
     * Check if the request accepts the mime types the route produces
     */
    private fun acceptMatches(request: APIGatewayProxyRequestEvent, produces: Set<MimeType>): Boolean {
        return when {
            produces.isEmpty() && request.acceptedMediaTypes().isEmpty() -> true
            else -> produces.firstOrNull {
                request.acceptedMediaTypes().any { acceptedType -> it == acceptedType }
            } != null
        }
    }

    override fun toString(): String {
        val sB = StringBuilder()
        sB.append(method.uppercase(Locale.getDefault())).append(" ")
        sB.append(pathPattern).append("")
        sB.append("[" + { consumes.joinToString(", ") } + "]->[" + produces.joinToString(", ") + "]")
        return sB.toString()
    }
}

/**
 * Stores the results of the matching operation across path, method, and mime types
 * @property matches is true if all components are true
 */
internal data class RequestMatchResult(
    val matchPath: Boolean = false,
    val matchMethod: Boolean = false,
    val matchAcceptType: Boolean = false,
    val matchContentType: Boolean = false
) {
    val matches
        get() = matchPath && matchMethod && matchAcceptType && matchContentType
}