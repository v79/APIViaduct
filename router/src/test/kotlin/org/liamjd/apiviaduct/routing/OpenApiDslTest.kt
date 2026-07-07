package org.liamjd.apiviaduct.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OpenApiDslTest {

    @Test
    fun `a router without an openApi block has no info`() {
        val router = lambdaRouter { }
        assertNull(router.openApiInfo)
    }

    @Test
    fun `openApi info populates title and version`() {
        val router = lambdaRouter {
            openApi {
                info {
                    title = "My API"
                    version = "1.2.0"
                }
            }
        }
        assertEquals("My API", router.openApiInfo?.title)
        assertEquals("1.2.0", router.openApiInfo?.version)
    }

    @Test
    fun `openApi captures the full info section and servers`() {
        val router = lambdaRouter {
            openApi {
                info {
                    title = "Book Library API"
                    version = "1.0.0"
                    summary = "Books"
                    description = "A CRUD API for books"
                    termsOfService = "https://example.com/tos"
                    contact(name = "Library Team", email = "library@example.com")
                    license(name = "Apache-2.0", identifier = "Apache-2.0")
                }
                server("https://api.example.com", "production")
                server("https://staging.example.com")
            }
        }
        val info = router.openApiInfo!!
        assertEquals("A CRUD API for books", info.description)
        assertEquals("https://example.com/tos", info.termsOfService)
        assertEquals(OpenApiInfo.Contact(name = "Library Team", email = "library@example.com"), info.contact)
        assertEquals(OpenApiInfo.License(name = "Apache-2.0", identifier = "Apache-2.0"), info.license)
        assertEquals(
            listOf(
                OpenApiInfo.Server("https://api.example.com", "production"),
                OpenApiInfo.Server("https://staging.example.com", null)
            ),
            info.servers
        )
    }

    @Test
    fun `openApi without an info block fails`() {
        assertFailsWith<IllegalStateException> {
            lambdaRouter {
                openApi {
                    server("https://api.example.com")
                }
            }
        }
    }

    @Test
    fun `info without a title fails`() {
        assertFailsWith<IllegalStateException> {
            lambdaRouter {
                openApi {
                    info { version = "1.0.0" }
                }
            }
        }
    }

    @Test
    fun `info without a version fails`() {
        assertFailsWith<IllegalStateException> {
            lambdaRouter {
                openApi {
                    info { title = "My API" }
                }
            }
        }
    }
}
