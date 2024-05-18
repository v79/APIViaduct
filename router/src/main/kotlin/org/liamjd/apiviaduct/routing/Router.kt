package org.liamjd.apiviaduct.routing

/**
 * The core routing class for the API which provides a DSL for defining routes.
 * This class is not intended to be used directly, but rather through the [lambdaRouter] function.

 */
class Router internal constructor()

/**
 * Entry point for creating an AWS Lambda routing function
 */
fun lambdaRouter(block: Router.() -> Unit): Router {
    val router = Router()
    router.block()
    return router
}

/**
 * Shorthand for a function which responds to a [Request] and returns a [Response]
 */
internal typealias Handler<I, T> = (request: Request<I>) -> Response<T>

// TODO: authorizer would be added here
internal data class RouteFunction<I, T : Any>(
    val requestPredicate: RequestPredicate, val handler: Handler<I, T>
)