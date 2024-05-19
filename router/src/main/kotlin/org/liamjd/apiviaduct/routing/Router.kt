package org.liamjd.apiviaduct.routing

import kotlin.reflect.typeOf

/**
 * The core routing class for the API which provides a DSL for defining routes.
 * This class is not intended to be used directly, but rather through the [lambdaRouter] function.
 */
class Router internal constructor() {
    val routes: MutableMap<RequestPredicate, RouteFunction<*, *>> = mutableMapOf()
    val consumeByDefault = setOf(MimeType.json)
    val produceByDefault = setOf(MimeType.json)

    /**
     * HTTP GET
     */
    inline fun <reified I, T : Any> get(
        pattern: String,
        noinline handler: Handler<I, T>
    ): RequestPredicate {
        val requestPredicate = defaultRequestPredicate(
            pattern = pattern, method = "GET", consuming = emptySet(), handler = handler
        ).also { predicate ->
            routes[predicate] = RouteFunction(predicate, handler)
        }
        return requestPredicate
    }


    /**
     * HTTP POST, used to create new data
     */
    inline fun <reified I, T : Any> post(
        pattern: String,
        noinline handler: Handler<I, T>
    ): RequestPredicate {
        val requestPredicate = defaultRequestPredicate(
            pattern = pattern, method = "POST", consuming = consumeByDefault, handler = handler
        ).also { predicate ->
            predicate.kType = typeOf<I>()
            routes[predicate] = RouteFunction(predicate, handler)
        }
        return requestPredicate
    }

    /**
     * HTTP PUT, to update or replace existing data
     * https://httpwg.org/specs/rfc9110.html#PUT
     */
    inline fun <reified I, T : Any> put(
        pattern: String,
        noinline handler: Handler<I, T>
    ): RequestPredicate {
        val requestPredicate = defaultRequestPredicate(
            pattern = pattern, method = "PUT", consuming = consumeByDefault, handler = handler
        ).also { predicate ->
            predicate.kType = typeOf<I>()
            routes[predicate] = RouteFunction(predicate, handler)
        }
        return requestPredicate
    }

    /**
     * HTTP PATCH, to update or modify existing data
     */
    inline fun <reified I, T : Any> patch(
        pattern: String,
        noinline handler: Handler<I, T>
    ): RequestPredicate {
        val requestPredicate = defaultRequestPredicate(
            pattern = pattern, method = "PATCH", consuming = consumeByDefault, handler = handler
        ).also { predicate ->
            predicate.kType = typeOf<I>()
            routes[predicate] = RouteFunction(predicate, handler)
        }
        return requestPredicate
    }

    /**
     * HTTP DELETE, to remove data
     * https://httpwg.org/specs/rfc9110.html#DELETE
     */
    inline fun <reified I, T : Any> delete(
        pattern: String,
        noinline handler: Handler<I, T>
    ): RequestPredicate {
        val requestPredicate = defaultRequestPredicate(
            pattern = pattern, method = "DELETE", consuming = emptySet(), handler = handler
        ).also { predicate ->
            routes[predicate] = RouteFunction(predicate, handler)
        }
        return requestPredicate
    }

    /**
     * The default request predicate forms the basis of all the HTTP methods.
     * It sets a default set of [MimeType]s for consuming and producing, and creates a [RequestPredicate] object.
     * The defaults are `application/json` for both consuming and producing but these can be overridden with the `supplies` and `consumes` modifiers.
     */
    inline fun <reified I, T : Any> defaultRequestPredicate(
        pattern: String,
        method: String,
        consuming: Set<MimeType> = consumeByDefault,
        producing: Set<MimeType> = produceByDefault,
        noinline handler: Handler<I, T>
    ) = RequestPredicate(
        method = method, pathPattern = pattern, consumes = consuming, produces = producing
    ).also { predicate ->
        routes[predicate] = RouteFunction(predicate, handler)
    }
}

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
data class RouteFunction<I, T : Any>(
    val predicate: RequestPredicate, val handler: Handler<I, T>
)