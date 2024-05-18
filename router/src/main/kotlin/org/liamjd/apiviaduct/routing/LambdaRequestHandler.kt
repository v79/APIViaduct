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
            headers = headers.mapKeys { it.key.lowercase() }
        }
            .let {
                handler.router = router
                handler.handleRequest(input, context)
            }

}

/**
 * We don't want to expose the real handler function to users of the library
 */
internal class LambdaRequestHandler() :
    RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    lateinit var router: Router

    override fun handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent {
        println(
            "RequestHandlerWrapper: handleRequest(): looking for route which matches request  ${input.httpMethod} ${input.path} <${
                input.getHeader(
                    "Content-Type"
                )
            }->${input.acceptedMediaTypes()}>"
        )
        router.routes.entries.map { route: MutableMap.MutableEntry<RequestPredicate, RouteFunction<*, *>> ->
            val matchResult = route.key.match(input)
            if (matchResult.matches) {
                val matchedAcceptType = route.key.matchedAcceptType(input.acceptedMediaTypes())
                    ?: router.produceByDefault.first()

                // NEXT: Replace this with a processRoute function
                // and a createResponse function
                return APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(
                        mapOf(
                            "Content-Type" to matchedAcceptType.toString(),
                            "Access-Control-Allow-Origin" to "*"
                        )
                    )
                    .withBody("Matched route ${input.httpMethod} ${input.path} with ${input.acceptedMediaTypes()}")
            }
            matchResult
        }
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
        println("No route match found for $httpMethod $path")
        val possibleAlts = router.routes.filterKeys { it.pathPattern == path }
        if (possibleAlts.isNotEmpty()) {
            println("Possible alternatives: ${possibleAlts.keys}")
        }
        return APIGatewayProxyResponseEvent()
            .withStatusCode(404)
            .withHeaders(mapOf("Content-Type" to "text/plain"))
            .withBody("No match found for route '$httpMethod' '$path' which accepts $acceptedMediaTypes")
    }
}


