package org.liamjd.apiviaduct.schema

import org.liamjd.apiviaduct.routing.MimeType
import org.liamjd.apiviaduct.routing.OpenApiInfo
import org.liamjd.apiviaduct.routing.RequestPredicate
import org.liamjd.apiviaduct.routing.Request
import org.liamjd.apiviaduct.routing.Response
import org.liamjd.apiviaduct.routing.Router
import org.liamjd.apiviaduct.routing.supplies

/**
 * Opt-in: register a `GET` route that serves this router's own OpenAPI document as YAML, for live docs.
 *
 * This is deliberately **not** wired in by default — calling it is what adds the endpoint, and it
 * pulls the generator (this `openapi` module) into the deployed/native-image runtime, which the
 * build-time [OpenApiCli] path avoids. Reach for this only when you want the spec served live.
 *
 * ```kotlin
 * lambdaRouter {
 *     openApi { info { title = "My API"; version = "1.0.0" } }
 *     serveOpenApi()                 // GET /openapi.yaml
 *     get("/things", ::listThings)   // still included — generation is deferred to first request
 * }
 * ```
 *
 * The document is generated lazily on the first request and cached, so routes declared after this
 * call are included (the whole router is built by the time a request arrives). Declare it at the top
 * level, not inside a `group { }` — a grouped call would only see that group's routes.
 *
 * The router must carry `info`/`servers` (via its `openApi { }` block, or [infoOverride] here); if
 * neither is present the endpoint responds with a 500 when first hit, since the document cannot be
 * built. The served endpoint itself appears in the generated document, like any other route.
 *
 * @param path the path to serve the document at (default `/openapi.yaml`)
 * @param infoOverride optional document-level info, overriding the router's `openApi { }` declaration
 * @return the registered [RequestPredicate], so it can be further modified if needed
 */
fun Router.serveOpenApi(
    path: String = "/openapi.yaml",
    infoOverride: OpenApiInfo? = null
): RequestPredicate {
    val document: String by lazy { OpenApiGenerator(this, infoOverride).generateYaml() }
    return get(path) { _: Request<Unit> -> Response.ok(document) }.supplies(MimeType.yaml)
}
