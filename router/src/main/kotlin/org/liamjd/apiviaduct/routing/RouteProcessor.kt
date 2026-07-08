package org.liamjd.apiviaduct.routing

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.charleskorn.kaml.Yaml
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.liamjd.apiviaduct.routing.extensions.getHeader

/**
 * Shorthand for a function which responds to a [Request] and returns a [Response]
 */
typealias HandlerFunction<I, T> = (request: Request<I>) -> Response<T>

object RouteProcessor {

    /**
     * Process the route, extracting the body from the request and invoking the handler function
     * @param input the APIGatewayProxyRequestEvent from AWS
     * @param handlerFunction the function to invoke
     * @return a Response<Any> object
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun processRoute(
        input: APIGatewayProxyRequestEvent,
        handlerFunction: RouteFunction<*, *>
    ): Response<out Any> {
        println("Processing route ${handlerFunction.predicate.method} ${handlerFunction.predicate.pathPattern}, supplying ${handlerFunction.predicate.supplies}")

        val handler: (Nothing) -> Response<out Any> = handlerFunction.handler

        // extract the body from the request
        // a message body must not exist for HEAD requests, which we don't support
        // a message body must exist for POST, PUT, and PATCH requests (but it could have length 0)
        // a message body could exist for GET requests, but the body will be ignored
        when (input.httpMethod) {
            "POST", "PUT", "PATCH" -> {
                val inputSerializer = handlerFunction.predicate.inputSerializer
                if (inputSerializer == null) {
                    return Response.badRequest(body = "No serializer for handler function")
                } else {
                    // input.body is a platform type and is genuinely null when the client sends no body
                    val rawBody: String? = if (input.isBase64Encoded == true) {
                        try {
                            input.body?.let { String(java.util.Base64.getDecoder().decode(it)) }
                        } catch (iae: IllegalArgumentException) {
                            return Response.badRequest(body = "Request body is not valid base64")
                        }
                    } else {
                        input.body
                    }
                    return try {
                        val contentType = input.getHeader("Content-Type")
                        val contentLength = input.getHeader("Content-Length")
                        println("Processing route: Converting $contentType with serializer. Deserializing...")
                        @Suppress("UNCHECKED_CAST")
                        val typedSerializer = inputSerializer as kotlinx.serialization.KSerializer<Any>
                        val bodyObject =
                            if (contentLength == "0") ""
                            else if (rawBody == null) return missingBodyResponse(input.httpMethod)
                            else if (contentType != null) when (MimeType.parse(contentType)) {
                                MimeType.json -> {
                                    Json.decodeFromString(typedSerializer, rawBody)
                                }

                                MimeType.yaml -> {
                                    Yaml.default.decodeFromString(typedSerializer, rawBody)
                                }

                                else -> {
                                    rawBody
                                }
                            } else rawBody
                        val request = Request(input, bodyObject, handlerFunction.predicate.pathPattern)
                        // call the handler function with the request object; this will return a [Response]; if it catches an error then that's the fault of the handler function
                        invokeHandler(handler, request)
                    } catch (mfe: MissingFieldException) {
                        println("Invalid request. Error is: ${mfe.message}")
                        Response.badRequest(body = "Invalid request. Error is: ${mfe.message}")
                    } catch (se: SerializationException) {
                        println("Could not deserialize body. Error is: ${se.message}")
                        Response.badRequest(body = "Could not deserialize body. Error is: ${se.message}")
                    } catch (iae: IllegalArgumentException) {
                        println("Illegal argument exception. Error is: ${iae.message}")
                        Response.badRequest(body = "Could not deserialize body; IllegalArgumentException. Error is: ${iae.message}")
                    }
                }
            }

            "GET", "DELETE" -> {
                val request = Request(input, null, handlerFunction.predicate.pathPattern)
                return invokeHandler(handler, request)
            }

            else -> {
                // HEAD, OPTIONS, TRACE, CONNECT, and any other method
                // we don't support these methods
                return Response.methodNotAllowed(body = "Method ${input.httpMethod} not supported for path ${handlerFunction.predicate.pathPattern}")
            }
        }
    }

    private fun missingBodyResponse(httpMethod: String): Response<String> {
        println("Request body is missing but the route's content type requires one")
        return Response.badRequest(body = "Request body is required for $httpMethod requests")
    }

    /**
     * Invoke the handler function, converting any exception it throws into a 500 response
     * rather than letting it crash the Lambda invocation
     */
    private fun invokeHandler(handler: (Nothing) -> Response<out Any>, request: Request<*>): Response<out Any> =
        try {
            (handler as HandlerFunction<*, *>)(request)
        } catch (e: Exception) {
            println("Error calling handler function: ${e.message}")
            Response.serverError(body = "Server error in processing request for ${handler}: ${e.message}")
        }

}