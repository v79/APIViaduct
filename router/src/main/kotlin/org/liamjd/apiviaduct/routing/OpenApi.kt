package org.liamjd.apiviaduct.routing

/**
 * Document-level OpenAPI metadata — the `info` and `servers` sections — that cannot be derived from
 * the routes. Populated by the [Router.openApi] DSL and read back by the OpenAPI generator, which
 * turns everything else (paths, operations, schemas) into the document.
 *
 * Mirrors the subset of the Info Object in `OpenAPISpecNotes.md`. Lives in the router module (rather
 * than the generator's) for the same reason as [RouteSpec]: the `openApi { }` DSL is part of
 * `lambdaRouter { }`, so the data must hang off the [Router].
 *
 * @property title the API title (required)
 * @property version the API version (required)
 * @property summary a short summary of the API
 * @property description a longer description of the API
 * @property termsOfService a URL to the terms of service
 * @property contact contact information for the API
 * @property license license information for the API
 * @property servers the servers the API is available on
 */
data class OpenApiInfo(
    val title: String,
    val version: String,
    val summary: String? = null,
    val description: String? = null,
    val termsOfService: String? = null,
    val contact: Contact? = null,
    val license: License? = null,
    val servers: List<Server> = emptyList()
) {
    /** @property name contact name; @property url contact URL; @property email contact email. */
    data class Contact(val name: String? = null, val url: String? = null, val email: String? = null)

    /** @property name the (required) license name; @property identifier an SPDX id; @property url a URL. */
    data class License(val name: String, val identifier: String? = null, val url: String? = null)

    /** @property url the (required) server URL; @property description a description of the server. */
    data class Server(val url: String, val description: String? = null)
}

/**
 * Declare the document-level OpenAPI metadata for this router:
 *
 * ```kotlin
 * lambdaRouter {
 *     openApi {
 *         info {
 *             title = "Book Library API"
 *             version = "1.0.0"
 *             description = "A small CRUD API for managing a library of books."
 *             contact(name = "Library Team", email = "library@example.com")
 *             license(name = "Apache-2.0", identifier = "Apache-2.0")
 *         }
 *         server("https://api.example.com", "production")
 *     }
 *     get("/books", ::listBooks)
 * }
 * ```
 *
 * Declare it once at the top level; a block inside a `group { }` applies only to that group's
 * throwaway child router and is ignored.
 */
fun Router.openApi(block: OpenApiBuilder.() -> Unit) {
    openApiInfo = OpenApiBuilder().apply(block).build()
}

/** Builder for the [Router.openApi] block: an [info] section plus zero or more [server]s. */
class OpenApiBuilder {
    private var info: OpenApiInfo? = null
    private val servers = mutableListOf<OpenApiInfo.Server>()

    /** The required info section — title and version at minimum. */
    fun info(block: OpenApiInfoBuilder.() -> Unit) {
        info = OpenApiInfoBuilder().apply(block).build()
    }

    /** Add a server the API is available on. */
    fun server(url: String, description: String? = null) {
        servers += OpenApiInfo.Server(url, description)
    }

    internal fun build(): OpenApiInfo {
        val declared = info ?: error("openApi { } requires an info { } block declaring at least a title and version")
        return declared.copy(servers = servers.toList())
    }
}

/** Builder for the `info { }` section. [title] and [version] are mandatory. */
class OpenApiInfoBuilder {
    var title: String? = null
    var version: String? = null
    var summary: String? = null
    var description: String? = null
    var termsOfService: String? = null

    private var contact: OpenApiInfo.Contact? = null
    private var license: OpenApiInfo.License? = null

    /** Set the contact information. */
    fun contact(name: String? = null, url: String? = null, email: String? = null) {
        contact = OpenApiInfo.Contact(name, url, email)
    }

    /** Set the license. [name] is required by the spec. */
    fun license(name: String, identifier: String? = null, url: String? = null) {
        license = OpenApiInfo.License(name, identifier, url)
    }

    internal fun build(): OpenApiInfo {
        val declaredTitle = title ?: error("openApi info { } requires a title")
        val declaredVersion = version ?: error("openApi info { } requires a version")
        return OpenApiInfo(
            title = declaredTitle,
            version = declaredVersion,
            summary = summary,
            description = description,
            termsOfService = termsOfService,
            contact = contact,
            license = license
        )
    }
}
