package org.liamjd.apiviaduct.routing.extensions

import org.liamjd.apiviaduct.routing.AuthType
import org.liamjd.apiviaduct.routing.LambdaRouter

fun LambdaRouter.openAPI(): String {
    val sb = StringBuilder()

    // get all the authorizers
    val authorizers = this.router.routes.values.map { it.authorizer }.filter { it.type != AuthType.NONE }

    sb.appendLine("openapi: 3.0.3")
    // info, title, description, version
    sb.appendLine("info:")
    sb.appendLine("  title: ${this.router::class.simpleName}")
    sb.appendLine("  version: 0.0.1")
    // servers

    // add tags as defined by the route groups
    if (this.router.groupPaths.isNotEmpty()) {
        sb.appendLine("tags:")
        this.router.groupPaths.forEach {
            sb.appendLine("  - name: ${it.split("/")[1]}")
        }
    } else {
        sb.appendLine("tags: []")
    }

    // paths
    sb.appendLine("paths:")
    this.router.routes.entries.groupBy { it.key.pathPattern }.forEach { path ->
        sb.appendLine("  ${path.key}:")
        path.value.forEach { route ->
            sb.appendLine("    ${route.key.method.lowercase()}:")
//            sb.appendLine("      summary: ${route.key.method} ${route.key.pathPattern}")
//            sb.appendLine("      description: ${route.value.predicate.kType}")
//            sb.appendLine("      operationId: ${route.key.method}${route.key.pathPattern.replace("/", "_")}")
            sb.appendLine("      tags:")
            sb.appendLine("        - ${route.key.pathPattern.split("/")[1]}")


            // display summary and description, if any

            // display authorization, if any
            if (route.value.authorizer.type != AuthType.NONE) {
                sb.appendLine("      security:")
                sb.appendLine("        - ${route.value.authorizer.simpleName.replace(' ','_')}: []")
            }

            // display path variables, if any
            if (route.key.pathVariables.isNotEmpty()) {
                sb.appendLine("      parameters:")
                route.key.pathVariables.forEach { variable ->
                    sb.appendLine("        - name: $variable")
                    sb.appendLine("          in: path")
                    sb.appendLine("          required: true")
                    sb.appendLine("          schema:")
                    // TODO: this should the type and the format but my Request class doesn't have that information
                    sb.appendLine("            type: string")
                }
            }

            // display request body details for POST and PUT
            when (route.key.method) {
                "POST", "PUT" -> {
                    sb.appendLine("      requestBody:")
                    sb.appendLine("        required: true")
                    sb.appendLine("        content:")
                    route.key.consumes.forEach { consumes ->
                        sb.appendLine("          ${consumes.toString().lowercase()}:")
                        sb.appendLine("            schema:")
                        route.value.predicate.kType?.let { kType ->
                            val typeString =
                                kType.toString().removeSuffix("(Kotlin reflection is not available)").trim()
                            if (typeString != "kotlin.Unit") {
                                sb.appendLine("              type: object")
                                // ${typeString.substringAfterLast(".")}
                            } else {
                                sb.appendLine("              type: string") // fallback to string
                            }
                        }
                    }
                }
            }

            // display response options
            sb.appendLine("      responses:")
            sb.appendLine("        200:")
            sb.appendLine("          description: OK")
            sb.appendLine("          content:")
            route.key.supplies.forEach { supplies ->
                sb.appendLine("            ${supplies.toString().lowercase()}:")
                sb.appendLine("              schema:")
                sb.appendLine("                type: string")
            }
        }
    }
    // components:
    sb.appendLine("components:")
    // display security schemes, if any
    if (authorizers.isNotEmpty()) {
        sb.appendLine("  securitySchemes:")
        authorizers.forEach { authorizer ->
            sb.appendLine("    ${authorizer.simpleName.replace(' ','_')}:")
            sb.appendLine("      type: http")
            sb.appendLine("      scheme: ${authorizer.type}")
//                        sb.appendLine("      bearerFormat: JWT")
        }
    }
    return sb.toString()
}