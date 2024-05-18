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