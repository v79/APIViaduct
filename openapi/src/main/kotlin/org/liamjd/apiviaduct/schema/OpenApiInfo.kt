package org.liamjd.apiviaduct.schema

/**
 * Document-level OpenAPI metadata that cannot be derived from the routes — the `info` and `servers`
 * sections of the document. Supplied by the consumer to [OpenApiGenerator]; everything else (paths,
 * operations, schemas) is generated from the router.
 *
 * Mirrors the subset of the Info Object in `OpenAPISpecNotes.md`.
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
