package org.liamjd.apiviaduct.routing

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.charleskorn.kaml.Yaml
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.liamjd.apiviaduct.routing.RouteProcessor.processRoute
import org.liamjd.apiviaduct.routing.extensions.acceptedMediaTypes
import org.liamjd.apiviaduct.routing.extensions.getHeader
import kotlin.reflect.typeOf

/**
 * Base class for creating a Lambda router. Your project should create an object that extends this class
 */
abstract class LambdaRouter {
    open val corsDomain: String = "*"
    abstract val router: Router
    private val handler = LambdaRequestHandler()

    // The AWS API requires a function called handleRequest with the APIGatewayProxyRequestEvent and Context as parameters
    fun handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent =
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
internal class LambdaRequestHandler :
    RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    lateinit var router: Router
    var corsDomain: String = "*"

    override fun handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent {
        println(
            "RequestHandlerWrapper: handleRequest(): looking for route which matches request ${input.httpMethod} ${input.path} <${
                input.getHeader(
                    "Content-Type"
                )
            }->${input.acceptedMediaTypes()}>"
        )
        // TODO: Serialize the response instead
        val response: Response<out Any> = validateRoute(input)

        val apiGatewayResponse = serializeResponse(response, input.acceptedMediaTypes().first(), corsDomain)
        return apiGatewayResponse
    }

    /**
     * Validate the route, checking the method, path, and accept headers to find a matching handler function
     * @param input the APIGatewayProxyRequestEvent from AWS
     * @return a Response<Any> object, which may be an error response
     */
    private fun validateRoute(input: APIGatewayProxyRequestEvent): Response<out Any> {
        var routeFound = false
        // find all the routes which match the path
        router.routes.filter { matchPath(input, it.value) }.entries.forEach { route ->
            routeFound = true
            println("Checking route ${route.key.method} ${route.key.pathPattern}")
            // now check if the method matches
            if (route.key.methodMatches(input)) {
                // now check if the accept and content types match
                if (route.key.acceptMatches(input, route.key.produces)) {
                    // Authentication check
                    if(route.value.authorizer.type != AuthType.NONE) {
                        val authResult = route.value.authorizer.authorize(input)
                          if(!authResult.authorized) {
                            return Response.unauthorized(
                                body = authResult.message,
                                headers = mapOf("Content-Type" to MimeType.plainText.toString())
                            )
                        }
                    }
                    // TODO: Add filters here?
                    return processRoute(input, route.value)
                } else {
                    // accept doesn't match, return 406
                    println("Route $input.path cannot provide requested content type (${input.acceptedMediaTypes()})")
                    return createNotAcceptableResponse(input.httpMethod, input.path, route.key.produces)
                }
            }
        }
        // I want to 405 if the route exists but the method is wrong
        if (routeFound) {
            return createMethodNotAllowedResponse(input.httpMethod, input.path)
        }
        // we have exhausted all routes, return 404
        return createNoMatchingRouteResponse(input.httpMethod, input.path, input.acceptedMediaTypes())
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
                    val bodyString: String = when (mimeType) {

                        MimeType.json -> {
                            val jsonFormat = Json { prettyPrint = false; encodeDefaults = true }
                            response.kType?.let { kType ->
                                val kSerializer = serializer(kType)
                                kSerializer.let {
                                    jsonFormat.encodeToString(kSerializer, response.body)
                                }
                            } ?: """{ "error" : "could not get json serializer for $response" }"""
                        }

                        MimeType.yaml -> {
                            response.kType?.let { kType ->
                                val kSerializer = serializer(kType)
                                kSerializer.let {
                                    Yaml.default.encodeToString(kSerializer, response.body)
                                }
                            } ?: "error: could not get yaml serializer for $response"
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
                    bodyString
                } else {
                    // return empty string for null body
                    // do I need to sanity check this?
                    ""
                }
            }

            in 300..399 -> {
                response.kType = typeOf<String>()
                response.body?.toString() ?: ""
            }

            in 400..499 -> {
                response.kType = typeOf<String>()
                response.body?.toString() ?: ""
            }

            else -> {
                response.kType = typeOf<String>()
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


