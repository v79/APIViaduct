package org.liamjd.apiviaduct.sample

import org.liamjd.apiviaduct.routing.LambdaRouter
import org.liamjd.apiviaduct.routing.Request
import org.liamjd.apiviaduct.routing.Response
import org.liamjd.apiviaduct.routing.lambdaRouter

/**
 * Sample router for the API
 */
class SampleRouter : LambdaRouter() {
    override val corsDomain: String = "https://example.com"

    override val router = lambdaRouter {
        get("/test", handler = { _: Request<Unit> -> Response<String>(200) })
    }
}