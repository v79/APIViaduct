package org.liamjd.apiviaduct.routing

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent

/**
 * Simple wrapper around the AWS [APIGatewayProxyRequestEvent] class, created after a matching route has been found
 * @property apiRequest the full event from API Gateway
 * @property body the body, which will be empty for a GET but should have a value for PUT, POST etc
 * @property pathPattern the path pattern from the predicate, with the {parameters} etc
 * @property pathParameters a map of matching path parameters and their values, i.e. path /get/{id} with id = 3 becomes `map[id] = 3`
 * @property headers a wrapper around apiRequest.headers
 */
data class Request<I>(
    val apiRequest: APIGatewayProxyRequestEvent, val body: I, val pathPattern: String
) {
    // TODO: Ideally, this should be Map<String, Any> but I can't figure out how to do that
    private val pathParameters: Map<String, String> by lazy {
        buildMap {
            val inputParts = apiRequest.path.split("/")
            val routeParts = pathPattern.split("/")
            for (i in routeParts.indices) {
                if (routeParts[i].startsWith("{") && routeParts[i].endsWith("}")) {
                    put(routeParts[i].removeSurrounding("{", "}"), inputParts[i])
                }
            }
        }
    }
    val headers: MutableMap<String, String> = if (apiRequest.headers != null) apiRequest.headers else mutableMapOf()
}

/**
 * Collection of useful HTTP status codes I'm likely to need
 */
enum class HttpCodes(val code: Int, val message: String) {
    OK(200, "OK"), CREATED(201, "Created"), ACCEPTED(202, "Accepted"), NO_CONTENT(204, "No Content"), BAD_REQUEST(
        400,
        "Bad Request"
    ),
    UNAUTHORIZED(401, "Unauthorized"), FORBIDDEN(403, "Forbidden"), NOT_FOUND(404, "Not Found"), METHOD_NOT_ALLOWED(
        405,
        "Method Not Allowed"
    ),
    CONFLICT(409, "Conflict"),
    TEAPOT(418, "I'm a teapot"), SERVER_ERROR(500, "Internal Server Error"), NOT_IMPLEMENTED(501, "Not Implemented"),
}