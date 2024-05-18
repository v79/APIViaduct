package org.liamjd.apiviaduct.sample

import org.liamjd.apiviaduct.routing.LambdaRouter
import org.liamjd.apiviaduct.routing.lambdaRouter

/**
 * Sample router for the API
 */
object SampleRouter : LambdaRouter() {
    override val corsDomain: String = "https://example.com"
    override val router = lambdaRouter { }
}