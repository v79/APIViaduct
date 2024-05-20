package org.liamjd.apiviaduct.routing

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * A response object that can be returned from a route handler
 * @param statusCode the HTTP Status Code
 * @param body the object returned from the server, which could be text, an object to be serialized later, or null
 * @param headers the HTTP response headers, which should always include a Content-Type header
 * @property kType internal property to keep track of the type of the body, for serialization
 */
@Serializable
data class Response<T : Any>(
    val statusCode: Int,
    val body: T? = null,
    val headers: Map<String, String> = emptyMap(),
) {
    @Transient
    var kType: KType? = null

    companion object {
        /**
         * 200 - Create a default successful response with the body and headers provided
         */
        inline fun <reified T : Any> ok(body: T? = null, headers: Map<String, String> = emptyMap()): Response<T> {
            val tt: KType = typeOf<T>()
            return Response(HttpCodes.OK.code, body, headers).apply { kType = tt }
        }

        /**
         * 202
         * @param body will be ignored as no content is sent with the HTTP 202 message
         */
        inline fun <reified T : Any> accepted(
            body: T? = null,
            headers: Map<String, String> = emptyMap()
        ): Response<T> {
            val tt: KType = typeOf<T>()
            return Response(HttpCodes.ACCEPTED.code, body, headers).apply { kType = tt }
        }

        /**
         * 204
         * @param body will be ignored as no content is sent with the HTTP 204 message
         */
        inline fun <reified T : Any> noContent(
            body: T? = null,
            headers: Map<String, String> = emptyMap()
        ): Response<T> {
            val tt: KType = typeOf<T>()
            return Response(HttpCodes.NO_CONTENT.code, body, headers).apply { kType = tt }
        }

        /**
         * 401
         */
        inline fun <reified T : Any> unauthorized(
            body: T? = null,
            headers: Map<String, String> = emptyMap()
        ): Response<T> {
            val tt: KType = typeOf<T>()
            return Response(HttpCodes.UNAUTHORIZED.code, body, headers).apply { kType = tt }
        }

        /**
         * 400
         */
        inline fun <reified T : Any> badRequest(
            body: T? = null,
            headers: Map<String, String> = emptyMap()
        ): Response<T> {
            val tt: KType = typeOf<T>()
            return Response(HttpCodes.BAD_REQUEST.code, body, headers).apply { kType = tt }
        }

        /**
         * 404
         */
        inline fun <reified T : Any> notFound(
            body: T? = null,
            headers: Map<String, String> = emptyMap()
        ): Response<T> {
            val tt: KType = typeOf<T>()
            return Response(HttpCodes.NOT_FOUND.code, body, headers).apply { kType = tt }
        }

        /**
         * 405
         * https://httpwg.org/specs/rfc9110.html#status.405
         * The serer MUST generate an Allow header file containing a list of the target resource's currently supported methods.
         */
        inline fun <reified T : Any> methodNotAllowed(
            body: T? = null,
            headers: Map<String, String> = emptyMap()
        ): Response<T> {
            val tt: KType = typeOf<T>()
            return Response(HttpCodes.METHOD_NOT_ALLOWED.code, body, headers).apply { kType = tt }
        }

        /**
         * 406
         * https://httpwg.org/specs/rfc9110.html#status.405
         * The server should generate a list of acceptable media types in the Content-Type header
         */
        inline fun <reified T : Any> notAcceptable(
            body: T? = null,
            headers: Map<String, String> = emptyMap()
        ): Response<T> {
            val tt: KType = typeOf<T>()
            return Response(HttpCodes.NOT_ACCEPTABLE.code, body, headers).apply { kType = tt }
        }

        /**
         * 409
         */
        inline fun <reified T : Any> conflict(
            body: T? = null,
            headers: Map<String, String> = emptyMap()
        ): Response<T> {
            val tt: KType = typeOf<T>()
            return Response(HttpCodes.CONFLICT.code, body, headers).apply { kType = tt }
        }

        /**
         * 500
         */
        inline fun <reified T : Any> serverError(
            body: T? = null,
            headers: Map<String, String> = emptyMap()
        ): Response<T> {
            val tt: KType = typeOf<T>()
            return Response(HttpCodes.SERVER_ERROR.code, body, headers).apply { kType = tt }
        }

        /**
         * 501
         * @param body will not be sent as part of the message
         */
        inline fun <reified T : Any> notImplemented(
            body: T? = null,
            headers: Map<String, String> = emptyMap()
        ): Response<T> {
            val tt: KType = typeOf<T>()
            return Response(HttpCodes.NOT_IMPLEMENTED.code, body, headers).apply { kType = tt }
        }
    }
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
    NOT_ACCEPTABLE(406, "Not Acceptable"),
    CONFLICT(409, "Conflict"),
    TEAPOT(418, "I'm a teapot"), SERVER_ERROR(500, "Internal Server Error"), NOT_IMPLEMENTED(501, "Not Implemented"),
}