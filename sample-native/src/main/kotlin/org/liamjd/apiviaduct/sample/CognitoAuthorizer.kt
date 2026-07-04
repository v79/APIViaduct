package org.liamjd.apiviaduct.sample

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.liamjd.apiviaduct.routing.AuthResult
import org.liamjd.apiviaduct.routing.AuthType
import org.liamjd.apiviaduct.routing.Authorizer
import org.liamjd.apiviaduct.routing.extensions.getHeader
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/**
 * Validates AWS Cognito access tokens without Jackson or the Auth0 libraries.
 * Like [parseApiGatewayEvent], this deliberately avoids reflection-heavy JSON
 * parsing so the sample stays native-image safe: the JWT segments and the JWKS
 * document are decoded with kotlinx.serialization, and the RS256 signature is
 * verified with JDK crypto alone.
 *
 * Claim checks run before any network call, so requests without a plausible
 * token are rejected offline — the JWKS document is only fetched (then cached
 * for the lifetime of the warm Lambda) to verify a token that passed them.
 */
class CognitoAuthorizer(region: String, userPoolId: String, private val clientId: String? = null) : Authorizer {
    override val simpleName = "Cognito user pool authorizer"
    override val type = AuthType.JWT

    private val issuer = "https://cognito-idp.$region.amazonaws.com/$userPoolId"
    private val jwksUrl = "$issuer/.well-known/jwks.json"
    private val json = Json { ignoreUnknownKeys = true }

    private var cachedJwks: List<Jwk>? = null

    override fun authorize(request: APIGatewayProxyRequestEvent): AuthResult {
        val authHeader = request.getHeader("authorization")?.trim()
            ?: return AuthResult(false, "Missing Authorization header")
        if (!authHeader.startsWith("Bearer ")) return AuthResult(false, "Authorization header is not a Bearer token")

        val segments = authHeader.removePrefix("Bearer ").trim().split('.')
        if (segments.size != 3) return AuthResult(false, "Token is not a JWT")

        val header: JwtHeader
        val payload: JwtPayload
        try {
            header = json.decodeFromString(JwtHeader.serializer(), decodeSegment(segments[0]))
            payload = json.decodeFromString(JwtPayload.serializer(), decodeSegment(segments[1]))
        } catch (e: Exception) {
            return AuthResult(false, "Token could not be parsed")
        }

        if (header.alg != "RS256") return AuthResult(false, "Unsupported token algorithm")
        if (payload.iss != issuer) return AuthResult(false, "Token issuer does not match the user pool")
        val now = System.currentTimeMillis() / 1000
        if (payload.exp == null || payload.exp <= now) return AuthResult(false, "Token has expired")
        if (payload.tokenUse != "access") return AuthResult(false, "Token is not an access token")
        if (clientId != null && payload.clientId != clientId) {
            return AuthResult(false, "Token was issued to a different client")
        }

        return try {
            val key = findKey(header.kid) ?: return AuthResult(false, "No JWKS key matches the token key id")
            if (verifySignature(key, segments[0], segments[1], segments[2])) {
                AuthResult(true, "")
            } else {
                AuthResult(false, "Token signature is invalid")
            }
        } catch (e: Exception) {
            AuthResult(false, "Token verification failed: ${e.message}")
        }
    }

    private fun findKey(kid: String?): Jwk? {
        if (kid == null) return null
        val keys = cachedJwks ?: fetchJwks().also { cachedJwks = it }
        return keys.firstOrNull { it.kid == kid }
    }

    private fun fetchJwks(): List<Jwk> {
        val connection = URL(jwksUrl).openConnection() as HttpURLConnection
        val body = connection.inputStream.bufferedReader().readText()
        return json.decodeFromString(JwksDocument.serializer(), body).keys
    }

    private fun verifySignature(key: Jwk, headerB64: String, payloadB64: String, signatureB64: String): Boolean {
        val decoder = Base64.getUrlDecoder()
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(
            RSAPublicKeySpec(BigInteger(1, decoder.decode(key.n)), BigInteger(1, decoder.decode(key.e)))
        )
        return Signature.getInstance("SHA256withRSA").run {
            initVerify(publicKey)
            update("$headerB64.$payloadB64".toByteArray(Charsets.US_ASCII))
            verify(decoder.decode(signatureB64))
        }
    }

    private fun decodeSegment(segment: String) = String(Base64.getUrlDecoder().decode(segment))
}

@Serializable
internal data class JwtHeader(val kid: String? = null, val alg: String? = null)

@Serializable
internal data class JwtPayload(
    val iss: String? = null,
    val exp: Long? = null,
    @SerialName("token_use") val tokenUse: String? = null,
    @SerialName("client_id") val clientId: String? = null
)

@Serializable
internal data class JwksDocument(val keys: List<Jwk> = emptyList())

@Serializable
internal data class Jwk(val kid: String? = null, val n: String = "", val e: String = "")
