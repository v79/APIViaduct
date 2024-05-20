package org.liamjd.apiviaduct.routing

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import org.liamjd.apiviaduct.routing.extensions.acceptedMediaTypes
import org.liamjd.apiviaduct.routing.extensions.getHeader

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

    override fun handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent {
        println(
            "RequestHandlerWrapper: handleRequest(): looking for route which matches request ${input.httpMethod} ${input.path} <${
                input.getHeader(
                    "Content-Type"
                )
            }->${input.acceptedMediaTypes()}>"
        )
        router.routes.entries.map { route: MutableMap.MutableEntry<RequestPredicate, RouteFunction<*, *>> ->
            println("Checking route ${route.key.method} ${route.key.pathPattern}")
            // first, check if the request matches this route
            if (route.key.pathMatches(input.path)) {
                // now check if the method matches
                if (route.key.methodMatches(input)) {
                    // now check if the accept and content types match
                    if (route.key.acceptMatches(input, route.key.produces)) {
                        // all good, process the route
                        // TODO: Replace this with a processRoute function which needs to handle serialization
                        // and a createResponse function
                        val matchedAcceptType = route.key.matchedAcceptType(input.acceptedMediaTypes())
                            ?: router.produceByDefault.first()
                        return APIGatewayProxyResponseEvent()
                            .withStatusCode(200)
                            .withHeaders(
                                mapOf(
                                    "Content-Type" to matchedAcceptType.toString(),
                                    "Access-Control-Allow-Origin" to "*"
                                )
                            )
                            .withBody("Matched route ${input.httpMethod} ${input.path} with ${input.acceptedMediaTypes()}")
                    } else {
                        // accept doesn't match, return 406
                        return createNotAcceptableResponse(input.httpMethod, input.path, route.key.produces)
                    }
                } else {
                    // method doesn't match, return 405
                    return createMethodNotAllowedResponse(input.httpMethod, input.path)
                }
            }
        }
        // we have exhausted all routes, return 404
        return createNoMatchingRouteResponse(input.httpMethod, input.path, input.acceptedMediaTypes())
    }

    /**
     * Return a 404 message with some useful details
     */
    private fun createNoMatchingRouteResponse(
        httpMethod: String?,
        path: String?,
        acceptedMediaTypes: List<MimeType>
    ): APIGatewayProxyResponseEvent {
        println("No route match found for $httpMethod $path $acceptedMediaTypes")
        val possibleAlts =
            router.routes.filterKeys { it.pathPattern == path }.keys.map { "${it.method} ${it.pathPattern} ${it.consumes}" }
        if (possibleAlts.isNotEmpty()) {
            println("Possible alternatives: $possibleAlts")
        }
        return APIGatewayProxyResponseEvent()
            .withStatusCode(404)
            .withHeaders(mapOf("Content-Type" to "text/plain"))
            .withBody("No match found for route '$httpMethod' '$path' which accepts $acceptedMediaTypes")
    }

    /**
     * Return a 405 message with some useful details
     */
    private fun createMethodNotAllowedResponse(
        httpMethod: String?,
        path: String?
    ): APIGatewayProxyResponseEvent {
        println("Method not allowed for $httpMethod $path")
        // get list of allowed methods for this path
        val allowedMethods = router.routes.filterKeys { it.pathPattern == path }.keys.map { it.method }
        return APIGatewayProxyResponseEvent()
            .withStatusCode(405)
            .withHeaders(mapOf("Allow" to allowedMethods.joinToString(",")))
    }

    /**
     * Return a 406 message with some useful details
     */
    private fun createNotAcceptableResponse(
        httpMethod: String?,
        path: String?,
        wantedTypes: Set<MimeType>
    ): APIGatewayProxyResponseEvent {
        println("Route $httpMethod $path cannot provide requested content type ($wantedTypes)")
        // get list of allowed methods for this path
        val canProvide = router.routes.filterKeys { it.pathPattern == path }.keys.map { it.produces }
        return APIGatewayProxyResponseEvent()
            .withStatusCode(406)
            .withHeaders(mapOf("Content-Type" to canProvide.joinToString(",")))
    }
}


