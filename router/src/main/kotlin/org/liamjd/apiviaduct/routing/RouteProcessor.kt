package org.liamjd.apiviaduct.routing

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.charleskorn.kaml.Yaml
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
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
                val kType = handlerFunction.predicate.kType
                if (kType == null) {
                    return Response.badRequest(body = "No type information for handler function")
                } else {
                    return try {
                        val contentType = input.getHeader("Content-Type")
                        val contentLength = input.getHeader("Content-Length")
                        println("Processing route: Converting $contentType to ${kType}. Deserializing...")
                        val bodyObject =
                            if (contentLength == "0") ""
                            else if (contentType != null) when (MimeType.parse(contentType)) {
                                MimeType.json -> {
                                    Json.decodeFromString(serializer(kType), input.body)
                                }

                                MimeType.yaml -> {
                                    Yaml.default.decodeFromString(serializer(kType), input.body)
                                }

                                else -> {
                                    input.body
                                }
                            } else input.body
                        val request = Request(input, bodyObject, handlerFunction.predicate.pathPattern)
                        // call the handler function with the request object; this will return a [Response]; if it catches an error then that's the fault of the handler function
                        try {
                            (handler as HandlerFunction<*, *>)(request)
                        } catch (e: Exception) {
                            println("Error calling handler function: ${e.message}")
                            Response.serverError(body = "Server error in processing request for ${handler}: ${e.message}")
                        }
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
                return (handler as HandlerFunction<*, *>)(request)
            }

            else -> {
                // HEAD, OPTIONS, TRACE, CONNECT, and any other method
                // we don't support these methods
                return Response.methodNotAllowed(body = "Method ${input.httpMethod} not supported for path ${handlerFunction.predicate.pathPattern}")
            }
        }
    }

}