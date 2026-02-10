package com.ssegning.keycloak.keybound.grants

import com.ssegning.keycloak.keybound.core.models.DeviceStatus
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import jakarta.ws.rs.core.Response
import org.keycloak.OAuthErrorException
import org.keycloak.common.util.Time
import org.keycloak.crypto.Algorithm
import org.keycloak.crypto.ECDSASignatureVerifierContext
import org.keycloak.crypto.KeyWrapper
import org.keycloak.events.Details
import org.keycloak.events.Errors
import org.keycloak.events.EventType
import org.keycloak.jose.jwk.JWKParser
import org.keycloak.models.SingleUseObjectProvider
import org.keycloak.models.UserSessionModel
import org.keycloak.protocol.oidc.grants.OAuth2GrantType
import org.keycloak.protocol.oidc.grants.OAuth2GrantTypeBase
import org.keycloak.services.CorsErrorResponseException
import org.keycloak.services.util.DefaultClientSessionContext
import org.keycloak.util.JsonSerialization
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.math.abs

class DeviceKeyGrantType(
    private val apiGateway: ApiGateway
) : OAuth2GrantTypeBase() {

    companion object {
        private val log = LoggerFactory.getLogger(DeviceKeyGrantType::class.java)
        private const val TTL = 300L // 5 minutes
    }

    override fun getEventType(): EventType = EventType.LOGIN

    override fun process(context: OAuth2GrantType.Context): Response {
        val session = context.session
        val realm = context.realm
        val client = context.client
        val httpRequest = session.context.httpRequest
        val params = httpRequest.decodedFormParameters

        if (client.isBearerOnly) {
            event.detail(Details.REASON, "Bearer-only client doesn't have device key")
            event.error(Errors.INVALID_CLIENT)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.UNAUTHORIZED_CLIENT,
                "Bearer-only client not allowed to retrieve service account",
                Response.Status.UNAUTHORIZED
            )
        }

        val deviceId = params.getFirst("device_id")
        val tsStr = params.getFirst("ts")
        val nonce = params.getFirst("nonce")
        val sig = params.getFirst("sig")
        val username = params.getFirst("username")

        if (deviceId == null || tsStr == null || nonce == null || sig == null || username == null) {
            event.error(Errors.INVALID_REQUEST)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_REQUEST,
                "Missing parameters: device_id, ts, nonce, sig, and username are required",
                Response.Status.BAD_REQUEST
            )
        }

        val user = session.users().getUserByUsername(realm, username)
        if (user == null || !user.isEnabled) {
            event.error(Errors.USER_NOT_FOUND)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "User not found or disabled",
                Response.Status.BAD_REQUEST
            )
        }

        val lookup = apiGateway.lookupDevice(deviceId = deviceId)
        if (lookup == null || !lookup.found) {
            event.error(Errors.INVALID_USER_CREDENTIALS)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "Device not found",
                Response.Status.BAD_REQUEST
            )
        }

        if (lookup.userId != user.id) {
            event.error(Errors.INVALID_USER_CREDENTIALS)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "Device not registered for this user",
                Response.Status.BAD_REQUEST
            )
        }

        val deviceRecord = lookup.device ?: run {
            event.error(Errors.INVALID_USER_CREDENTIALS)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "Device metadata not found",
                Response.Status.BAD_REQUEST
            )
        }

        if (deviceRecord.status != DeviceStatus.ACTIVE) {
            event.error(Errors.INVALID_USER_CREDENTIALS)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "Device is disabled",
                Response.Status.BAD_REQUEST
            )
        }

        // Timestamp Verification
        val ts = tsStr.toLongOrNull() ?: throw CorsErrorResponseException(
            cors,
            OAuthErrorException.INVALID_REQUEST,
            "Invalid timestamp",
            Response.Status.BAD_REQUEST
        )

        val currentTime = Time.currentTimeMillis() / 1000
        if (abs(currentTime - ts) > TTL) {
            event.error(Errors.INVALID_USER_CREDENTIALS)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "Timestamp expired",
                Response.Status.BAD_REQUEST
            )
        }

        // Nonce Verification
        val suo = session.getProvider(SingleUseObjectProvider::class.java)
        val nonceKey = "device-grant-replay:$nonce"
        if (!suo.putIfAbsent(nonceKey, TTL)) {
            event.error(Errors.INVALID_USER_CREDENTIALS)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "Nonce replay detected",
                Response.Status.BAD_REQUEST
            )
        }

        // Signature Verification
        try {
            val publicJwk = lookup.publicJwk ?: throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "Device public key not found",
                Response.Status.BAD_REQUEST
            )
            val publicKeyJwk = JsonSerialization.writeValueAsString(publicJwk)

            val jwkParser = JWKParser.create().parse(publicKeyJwk)
            val publicKey = jwkParser.toPublicKey()

            val canonicalData = mapOf(
                "deviceId" to deviceId,
                "publicKey" to publicKeyJwk,
                "ts" to tsStr,
                "nonce" to nonce
            )
            val canonicalString = JsonSerialization.writeValueAsString(canonicalData)
            val data = canonicalString.toByteArray(Charsets.UTF_8)

            val signatureBytes = try {
                Base64.getDecoder().decode(sig)
            } catch (_: IllegalArgumentException) {
                Base64.getUrlDecoder().decode(sig)
            }

            val key = KeyWrapper().apply {
                setPublicKey(publicKey)
                algorithm = Algorithm.ES256
            }

            val verifier = ECDSASignatureVerifierContext(key)
            if (!verifier.verify(data, signatureBytes)) {
                event.error(Errors.INVALID_USER_CREDENTIALS)
                throw CorsErrorResponseException(
                    cors,
                    OAuthErrorException.INVALID_GRANT,
                    "Invalid signature",
                    Response.Status.BAD_REQUEST
                )
            }

        } catch (e: Exception) {
            log.error("Signature verification failed", e)
            event.error(Errors.INVALID_USER_CREDENTIALS)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "Signature verification failed",
                Response.Status.BAD_REQUEST
            )
        }

        // Create Session
        val userSession = session
            .sessions()
            .createUserSession(
                UUID.randomUUID().toString(),
                realm, user,
                username, session.context.connection.remoteAddr,
                "device-grant", false,
                null, null,
                UserSessionModel.SessionPersistenceState.PERSISTENT
        )

        // Add JKT to session notes
        userSession.setNote("cnf.jkt", deviceRecord.jkt)

        val clientSession = session.sessions().createClientSession(realm, client, userSession)
        val clientSessionCtx = DefaultClientSessionContext.fromClientSessionScopeParameter(clientSession, session)

        event.user(user)
        event.session(userSession)
        event.detail(Details.AUTH_METHOD, "device_key")
        event.success()

        val accessTokenResponse =
            tokenManager.responseBuilder(realm, client, event, session, userSession, clientSessionCtx)
                .build()

        return Response.ok(accessTokenResponse).type("application/json").build()
    }
}
