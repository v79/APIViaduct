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
data class RequestPredicate(
    val method: String, var pathPattern: String,
    internal var consumes: Set<MimeType>,
    internal var produces: Set<MimeType>
) {
    var kType: KType? = null
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
    fun pathMatches(inputPath: String): Boolean {
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
    fun methodMatches(request: APIGatewayProxyRequestEvent) = method.equals(request.httpMethod, true)

    /**
     * Check if the request accepts the mime types the route produces
     */
    fun acceptMatches(request: APIGatewayProxyRequestEvent, produces: Set<MimeType>): Boolean {
        return when {
            produces.isEmpty() && request.acceptedMediaTypes().isEmpty() -> true
            else -> produces.firstOrNull {
                request.acceptedMediaTypes().any { acceptedType -> it == acceptedType }
            } != null
        }
    }

    fun matchedAcceptType(acceptedMediaTypes: List<MimeType>): MimeType? =
        produces.firstOrNull { acceptedMediaTypes.any { acceptedType -> it.isCompatibleWith(acceptedType) } }

    override fun toString(): String {
        val sB = StringBuilder()
        sB.append(method.uppercase(Locale.getDefault())).append(" ")
        sB.append(pathPattern).append("")
        sB.append("[" + { consumes.joinToString(", ") } + "]->[" + produces.joinToString(", ") + "]")
        return sB.toString()
    }
}

/**
 * Override the default consumes mime type
 * @param mimeTypes a set of mime types to accept
 */
fun RequestPredicate.expects(mimeTypes: Set<MimeType>?): RequestPredicate {
    mimeTypes?.let {
        this.consumes = mimeTypes
    }
    return this
}

/**
 * Override the default consumes mime type
 * @param mimeType a single mime type to accept
 */
fun RequestPredicate.expects(mimeType: MimeType): RequestPredicate {
    this.consumes = setOf(mimeType)
    return this
}

/**
 * Override the default produces mime type
 * @param mimeTypes a set of mime types to produce
 */
fun RequestPredicate.supplies(mimeTypes: Set<MimeType>?): RequestPredicate {
    mimeTypes?.let {
        produces = mimeTypes
    }
    return this
}

/**
 * Override the default produces mime type
 * @param mimeType a single mime type to produce
 */
fun RequestPredicate.supplies(mimeType: MimeType): RequestPredicate {
    produces = setOf(mimeType)
    return this
}

/**
 * Stores the results of the matching operation across path, method, and mime types
 * @property matches is true if all components are true
 */
data class RequestMatchResult internal constructor(
    val matchPath: Boolean = false,
    val matchMethod: Boolean = false,
    val matchAcceptType: Boolean = false,
    val matchContentType: Boolean = false
) {
    val matches
        get() = matchPath && matchMethod && matchAcceptType && matchContentType
}