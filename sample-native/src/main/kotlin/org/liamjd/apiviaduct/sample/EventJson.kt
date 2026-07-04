package org.liamjd.apiviaduct.sample

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A Jackson-free bridge between API Gateway JSON payloads and the AWS event
 * classes. The managed Java runtime would normally do this with Jackson, which
 * is reflection-heavy and hostile to native images; a custom runtime is free to
 * deserialize the event however it likes, so we use kotlinx.serialization DTOs
 * and copy the fields across.
 */
@Serializable
data class ProxyRequest(
    val resource: String? = null,
    val path: String,
    val httpMethod: String,
    val headers: Map<String, String>? = null,
    val queryStringParameters: Map<String, String>? = null,
    val pathParameters: Map<String, String>? = null,
    val body: String? = null,
    val isBase64Encoded: Boolean = false
)

@Serializable
data class ProxyResponse(
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val isBase64Encoded: Boolean = false
)

val eventJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun parseApiGatewayEvent(json: String): APIGatewayProxyRequestEvent {
    val dto = eventJson.decodeFromString<ProxyRequest>(json)
    return APIGatewayProxyRequestEvent().apply {
        resource = dto.resource
        path = dto.path
        httpMethod = dto.httpMethod
        headers = dto.headers
        queryStringParameters = dto.queryStringParameters
        pathParameters = dto.pathParameters
        body = dto.body
        isBase64Encoded = dto.isBase64Encoded
    }
}

fun serializeResponseEvent(event: APIGatewayProxyResponseEvent): String =
    eventJson.encodeToString(
        ProxyResponse.serializer(),
        ProxyResponse(
            statusCode = event.statusCode ?: 500,
            headers = event.headers ?: emptyMap(),
            body = event.body ?: "",
            isBase64Encoded = event.isBase64Encoded ?: false
        )
    )
