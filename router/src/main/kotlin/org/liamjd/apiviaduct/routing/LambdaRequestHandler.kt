package org.liamjd.apiviaduct.routing

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.amazonaws.services.lambda.runtime.logging.LogLevel
import com.charleskorn.kaml.Yaml
import kotlinx.serialization.json.Json
import org.liamjd.apiviaduct.routing.RouteProcessor.processRoute
import org.liamjd.apiviaduct.routing.extensions.acceptedMediaTypes
import org.liamjd.apiviaduct.routing.extensions.getHeader

/**
 * Base class for creating a Lambda router. Your project should create an object that extends this class
 */
abstract class LambdaRouter : RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    open val corsDomain: String = "*"
    abstract val router: Router
    private val handler = LambdaRequestHandler()

    // The AWS API requires a function called handleRequest with the APIGatewayProxyRequestEvent and Context as parameters
    override fun handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent =
        input.apply {
            headers = headers?.mapKeys { it.key.lowercase() } ?: emptyMap()
        }
            .let {
                handler.corsDomain = corsDomain
                handler.router = router
                handler.handleRequest(input, context)
            }
}

/**
 * We don't want to expose the real handler function to users of the library
 */
internal class LambdaRequestHandler {
    lateinit var router: Router
    var corsDomain: String = "*"

    lateinit var logger: LambdaLogger

    fun handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent {
        logger = context.logger
        logger.log(
            "RequestHandlerWrapper: handleRequest(): looking for route which matches request ${input.httpMethod} ${input.path} <${
                input.getHeader(
                    "Content-Type"
                )
            }->${input.acceptedMediaTypes()}>",
            LogLevel.INFO
        )
        // TODO: Serialize the response instead
        val (response, negotiatedType) = validateRoute(input)

        // Prefer the mime type negotiated with the matched route; a raw accepted type such
        // as the */* wildcard (curl's default) must never become the response Content-Type
        val responseType = negotiatedType
            ?: input.acceptedMediaTypes().firstOrNull { !it.isWild }
            ?: MimeType.plainText

        val apiGatewayResponse = serializeResponse(response, responseType, corsDomain)
        return apiGatewayResponse
    }

    /**
     * Validate the route, checking the method, path, and accept headers to find a matching handler function
     * @param input the APIGatewayProxyRequestEvent from AWS
     * @return a Response<Any> object, which may be an error response, paired with the mime type
     * negotiated with the matched route (null when no route was matched)
     */
    private fun validateRoute(input: APIGatewayProxyRequestEvent): Pair<Response<out Any>, MimeType?> {
        var routeFound = false
        // find all the routes which match the path
        router.routes.filter { matchPath(input, it.value) }.entries.forEach { route ->
            routeFound = true
            println("Checking route ${route.key.method} ${route.key.pathPattern}")
            // now check if the method matches
            if (route.key.methodMatches(input)) {
                // now check if the accept and content types match
                if (route.key.acceptMatches(input, route.key.produces)) {
                    // resolve what the response type will be: the first of the route's declared
                    // produces types which is compatible with what the request accepts
                    val negotiatedType = route.key.matchedAcceptType(input.acceptedMediaTypes())
                        ?: route.key.produces.firstOrNull()
                    // all good, process the route
                    // TODO: Add Authentication here
                    if (route.value.authorizer.type != AuthType.NONE) {
                        val authResult = route.value.authorizer.authorize(input)
                        if (!authResult.authorized) {
                            return Response.unauthorized(
                                body = authResult.message,
                                headers = mapOf("Content-Type" to MimeType.plainText.toString())
                            ) to null
                        }
                    }
                    // TODO: Add filters here?
                    return processRoute(input, route.value) to negotiatedType
                } else {
                    // accept doesn't match, return 406
                    println("Route $input.path cannot provide requested content type (${input.acceptedMediaTypes()})")
                    return createNotAcceptableResponse(input.httpMethod, input.path, route.key.produces) to null
                }
            }
        }
        // I want to 405 if the route exists but the method is wrong
        if (routeFound) {
            return createMethodNotAllowedResponse(input.httpMethod, input.path) to null
        }
        // we have exhausted all routes, return 404
        return createNoMatchingRouteResponse(input.httpMethod, input.path, input.acceptedMediaTypes()) to null
    }

    /**
     * Match the path of the incoming request to the path pattern of the route
     * Checking for parameters specified in { braces }
     * @param input the incoming request
     * @param route the route to match against
     */
    private fun matchPath(input: APIGatewayProxyRequestEvent, route: RouteFunction<*, *>): Boolean {
        val pathParts = input.path.split("/")
        val routeParts = route.predicate.pathPattern.split("/")
        if (pathParts.size != routeParts.size) {
            return false
        }
        for (i in routeParts.indices) {
            if (routeParts[i].startsWith("{") && routeParts[i].endsWith("}")) {
                // this is a path parameter, so we don't need to match it
                continue
            }
            if (routeParts[i] != pathParts[i]) {
                return false
            }
        }
        return true
    }


    /**
     * Serialize the response to an APIGatewayProxyResponseEvent, according to the mime type.
     * Add additional headers as required
     * @param response the response to serialize
     * @param mimeType the mime type to serialize to
     * @param corsDomain the domain to allow CORS requests from
     * @return a serialized APIGatewayProxyResponseEvent
     */
    private fun serializeResponse(
        response: Response<out Any>,
        mimeType: MimeType,
        corsDomain: String
    ): APIGatewayProxyResponseEvent {
        // what if the response is an error and I can't return the requested object?
        val responseString = when (response.statusCode) {
            in 200..299 -> {
                if (response.body != null) {
                    val bodyString: String = when {
                        // If body is already a String, just use it directly
                        response.body is String -> response.body.toString()

                        else -> when (mimeType) {

                        MimeType.json -> {
                            val jsonFormat = Json { prettyPrint = false; encodeDefaults = true }
                            response.outputSerializer?.let { serializer ->
                                @Suppress("UNCHECKED_CAST")
                                jsonFormat.encodeToString(serializer as kotlinx.serialization.KSerializer<Any>, response.body)
                            } ?: response.body.toString()
                        }

                        MimeType.yaml -> {
                            response.outputSerializer?.let { serializer ->
                                @Suppress("UNCHECKED_CAST")
                                Yaml.default.encodeToString(serializer as kotlinx.serialization.KSerializer<Any>, response.body)
                            } ?: response.body.toString()
                        }

                        MimeType.plainText -> {
                            response.body.toString()
                        }

                        MimeType.html -> {
                            """
                    <html>
                    <head>
                    <title>${response.statusCode}</title>
                    </head>
                    <body>
                    <h1>${response.statusCode}</h1>
                    <p>${response.body}</p>
                    </body>
                    </html>
                """.trimIndent()
                        }

                        MimeType.xml -> {
                            """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <response>
                    <status>${response.statusCode}</status>
                    <body>${response.body}</body>
                    </response>
                """.trimIndent()
                        }

                        else -> {
                            response.body.toString()
                        }
                    }
                    }
                    bodyString
                } else {
                    // return empty string for null body
                    // do I need to sanity check this?
                    ""
                }
            }

            in 300..399 -> {
                response.body?.toString() ?: ""
            }

            in 400..499 -> {
                response.body?.toString() ?: ""
            }

            else -> {
                response.body?.toString() ?: ""
            }
        }


        return APIGatewayProxyResponseEvent().apply {
            statusCode = response.statusCode
            headers = response.headers + mapOf(
                "Content-Type" to mimeType.toString(),
                "Access-Control-Allow-Origin" to corsDomain
            )
            body = responseString
        }
    }

    /**
     * Return a 404 message with some useful details
     */
    private fun createNoMatchingRouteResponse(
        httpMethod: String?,
        path: String?,
        acceptedMediaTypes: List<MimeType>
    ): Response<String> {
        println("No route match found for $httpMethod $path $acceptedMediaTypes")
        val possibleAlts =
            router.routes.filterKeys { it.pathPattern == path }.keys.map { "${it.method} ${it.pathPattern} ${it.consumes}" }
        if (possibleAlts.isNotEmpty()) {
            println("Possible alternatives: $possibleAlts")
        }
        return Response.notFound(
            body = "No match found for route '$httpMethod' '$path' which accepts $acceptedMediaTypes",
            headers = mapOf("Content-Type" to MimeType.plainText.toString())
        )
    }

    /**
     * Return a 405 message with some useful details
     */
    private fun createMethodNotAllowedResponse(
        httpMethod: String?,
        path: String?
    ): Response<String> {
        println("Method not allowed for $httpMethod $path")
        // get list of allowed methods for this path
        val allowedMethods = router.routes.filterKeys { it.pathPattern == path }.keys.map { it.method }
        return Response.methodNotAllowed(
            body = "",
            headers = mapOf(
                "Content-Type" to MimeType.plainText.toString(),
                "Allow" to allowedMethods.joinToString(",")
            )
        )
    }

    /**
     * Return a 406 message with some useful details
     */
    private fun createNotAcceptableResponse(
        httpMethod: String?,
        path: String?,
        wantedTypes: Set<MimeType>
    ): Response<String> {
        println("Route $httpMethod $path cannot provide requested content type ($wantedTypes)")
        // get list of allowed methods for this path
        val canProvide = router.routes.filterKeys { it.pathPattern == path }.keys.map { it.produces }
        return Response.notAcceptable(body = "", headers = mapOf("Content-Type" to canProvide.joinToString(",")))
    }
}
