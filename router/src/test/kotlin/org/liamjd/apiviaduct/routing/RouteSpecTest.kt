package org.liamjd.apiviaduct.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class RouteSpecTest {

    @Test
    fun `predicate has no spec by default`() {
        val predicate = RequestPredicate("GET", "/test", setOf(), setOf())
        assertNull(predicate.spec)
    }

    @Test
    fun `spec DSL attaches metadata and returns the same predicate for chaining`() {
        val predicate = RequestPredicate("GET", "/customers/{id}", setOf(), setOf())

        val returned = predicate.spec {
            summary = "Fetch a single customer"
            description = "Returns the full customer record for the given id"
            tags("customers", "read")
            pathParam("id", "The customer's unique id")
            response(200, "The customer record")
            response(404, "No customer with that id")
        }

        assertSame(returned, predicate, "spec { } should return the same predicate to allow chaining")

        val spec = predicate.spec!!
        assertEquals("Fetch a single customer", spec.summary)
        assertEquals("Returns the full customer record for the given id", spec.description)
        assertEquals(listOf("customers", "read"), spec.tags)
        assertEquals("The customer's unique id", spec.pathParamDocs["id"]?.description)
        assertEquals(spec.pathParamDocs["id"]?.required, true)
        assertEquals("The customer record", spec.responses[200]?.description)
        assertEquals("No customer with that id", spec.responses[404]?.description)
    }

    @Test
    fun `spec derived route data remains available alongside the hand-authored spec`() {
        // The point of the design: baseline facts stay on the predicate, spec only adds prose.
        val predicate = RequestPredicate("GET", "/customers/{id}", setOf(), setOf())
            .spec { summary = "Fetch a customer" }

        assertEquals(listOf("id"), predicate.pathVariables)
        assertEquals("Fetch a customer", predicate.spec?.summary)
    }
}
