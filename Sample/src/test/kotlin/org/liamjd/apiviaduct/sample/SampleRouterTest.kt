package org.liamjd.apiviaduct.sample

import kotlin.test.Test

/**
 * Basic integration tests for the SampleRouter

 */
class SampleRouterTest {

    @Test
    fun `test SampleRouter`() {
        val sampleRouter = SampleRouter
        assert(sampleRouter.corsDomain == "https://example.com")
        assert(sampleRouter.router.routes.isNotEmpty())
    }
}