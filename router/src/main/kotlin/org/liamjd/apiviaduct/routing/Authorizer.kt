package org.liamjd.apiviaduct.routing

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent

/**
 * General interface for any Authorizer class
 * @property simpleName a user-friendly name for the authorizer; not functionally relevant
 * Only one method, `authorize`, which returns an [AuthResult]
 */
interface Authorizer {
    val simpleName: String
    val type: AuthType
    fun authorize(request: APIGatewayProxyRequestEvent): AuthResult
}

/**
 * The result of an authorization attempt
 * @property authorized true or false
 * @property message helpful message, explaining why authorization has failed
 */
data class AuthResult(val authorized: Boolean, val message: String)

/** Supported authentication types */
enum class AuthType {
    NONE, BASIC, BEARER, JWT
}


/**
 * A default authorizer that allows all requests
 */
class NoAuth : Authorizer {
    override val simpleName: String = "No authorization"
    override val type: AuthType = AuthType.NONE
    override fun authorize(request: APIGatewayProxyRequestEvent): AuthResult = AuthResult(true, "")
}