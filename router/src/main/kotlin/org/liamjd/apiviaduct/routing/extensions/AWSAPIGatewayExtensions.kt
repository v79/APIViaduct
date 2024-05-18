package org.liamjd.apiviaduct.routing.extensions

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import org.liamjd.apiviaduct.routing.MimeType

/**
 * General helper function to get a named header
 */
fun APIGatewayProxyRequestEvent.getHeader(httpHeader: String): String? =
    this.headers?.entries?.firstOrNull { httpHeader.equals(it.key, ignoreCase = true) }?.value

/**
 * Get the 'accept' header
 */
fun APIGatewayProxyRequestEvent.acceptHeader() = getHeader("accept")

/**
 * Get the 'content-type' header
 */
fun APIGatewayProxyRequestEvent.contentType() = getHeader("content-type")

/**
 * Split the 'accept' header into a list of [MimeType]
 */
fun APIGatewayProxyRequestEvent.acceptedMediaTypes() =
    acceptHeader()?.split(",")?.map { it.trim() }?.map { MimeType.parse(it) }.orEmpty()