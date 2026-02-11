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
import org.keycloak.representations.AccessToken
import org.keycloak.protocol.oidc.grants.OAuth2GrantType
import org.keycloak.protocol.oidc.grants.OAuth2GrantTypeBase
import org.keycloak.models.Constants
import org.keycloak.services.CorsErrorResponseException
import org.keycloak.services.Urls
import org.keycloak.services.util.DefaultClientSessionContext
import org.keycloak.storage.StorageId
import org.keycloak.util.JsonSerialization
import org.keycloak.util.TokenUtil
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
        setContext(context) // DO NOT REMOVE THIS LINE.

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
        val userId = params.getFirst("user_id")
        val requestPublicKey = params.getFirst("public_key")

        if (deviceId == null || tsStr == null || nonce == null || sig == null || userId == null) {
            event.error(Errors.INVALID_REQUEST)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_REQUEST,
                "Missing parameters: device_id, ts, nonce, sig, and user_id are required",
                Response.Status.BAD_REQUEST
            )
        }

        log.debug("DeviceKeyGrantType invoked userId={} deviceId={}", userId, deviceId)

        val user = session.users().getUserById(realm, userId)
        if (user == null || !user.isEnabled) {
            log.debug("User {} not found or disabled", userId)
            event.error(Errors.USER_NOT_FOUND)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "User not found or disabled",
                Response.Status.BAD_REQUEST
            )
        }

        val lookup = apiGateway.lookupDevice(deviceId = deviceId)
        log.debug("Lookup result for device {} -> found={} userId={}", deviceId, lookup?.found, lookup?.userId)
        if (lookup == null || !lookup.found) {
            event.error(Errors.INVALID_USER_CREDENTIALS)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "Device not found",
                Response.Status.BAD_REQUEST
            )
        }

        val backendUserId = lookup.userId
        val userIdCandidates = linkedSetOf<String>().apply {
            add(user.id)
            StorageId.externalId(user.id)?.takeIf { it.isNotBlank() }?.let { add(it) }
            user.getFirstAttribute("backend_user_id")?.takeIf { it.isNotBlank() }?.let { add(it) }
            user.getFirstAttribute("user_id")?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        if (backendUserId.isNullOrBlank() || backendUserId !in userIdCandidates) {
            log.debug("Device {} belongs to backend user {} but request user {} not in {}", deviceId, backendUserId, user.id, userIdCandidates)
            event.error(Errors.INVALID_USER_CREDENTIALS)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "Device not registered for this user",
                Response.Status.BAD_REQUEST
            )
        }

        val deviceRecord = lookup.device ?: run {
            log.debug("Device metadata for {} missing from lookup", deviceId)
            event.error(Errors.INVALID_USER_CREDENTIALS)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "Device metadata not found",
                Response.Status.BAD_REQUEST
            )
        }

        if (deviceRecord.status != DeviceStatus.ACTIVE) {
            log.debug("Device {} status {} not active", deviceId, deviceRecord.status)
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
        log.debug("Timestamp check device={} provided={} current={}", deviceId, ts, currentTime)
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
        log.debug("Checking nonce replay for key {}", nonceKey)
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
            val publicKeyJwk = lookup.publicJwk?.let { JsonSerialization.writeValueAsString(it) }
                ?: requestPublicKey
                ?: throw CorsErrorResponseException(
                    cors,
                    OAuthErrorException.INVALID_GRANT,
                    "Device public key not found",
                    Response.Status.BAD_REQUEST
                )

            log.debug("Verifying signature for device {} jkt={}", deviceId, deviceRecord.jkt)
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

        log.debug("Signature verified for device {} bound to user {}", deviceId, user.id)
        // Create Session
        // TODO Do we really wanna create a session if the same device_id + public_key is used?
        val userSession = session
            .sessions()
            .createUserSession(
                UUID.randomUUID().toString(),
                realm, user,
                user.username ?: user.id, session.context.connection.remoteAddr,
                "device-grant", false,
                null, null,
            UserSessionModel.SessionPersistenceState.PERSISTENT
        )

        log.debug("Created user session {} for grant user {}", userSession.id, user.id)

        // Add JKT to session notes
        userSession.setNote("cnf.jkt", deviceRecord.jkt)

        val clientSession = session.sessions().createClientSession(realm, client, userSession)
        val clientSessionCtx = DefaultClientSessionContext.fromClientSessionScopeParameter(clientSession, session)

        event.user(user)
        event.session(userSession)
        event.detail(Details.AUTH_METHOD, "device_key")
        updateClientSession(clientSession)
        updateUserSessionFromClientAuth(userSession)
        val scopeParam = params.getFirst("scope")
        clientSessionCtx.setAttribute(Constants.GRANT_TYPE, context.grantType)
        log.debug("Minting access token for user={} client={} device={}", user.id, client.clientId, deviceId)
        val accessToken = tokenManager.createClientAccessToken(
            session,
            realm,
            client,
            user,
            userSession,
            clientSessionCtx
        )
        accessToken.issuer(Urls.realmIssuer(session.context.uri.baseUri, realm.name))
        accessToken.setOtherClaims("device_id", deviceId)
        accessToken.setConfirmation(
            AccessToken.Confirmation().apply {
                keyThumbprint = deviceRecord.jkt
            }
        )
        val responseBuilder = tokenManager
            .responseBuilder(realm, client, event, session, userSession, clientSessionCtx)
            .accessToken(accessToken)
        if (TokenUtil.isOIDCRequest(scopeParam)) {
            responseBuilder.generateIDToken().generateAccessTokenHash()
        }

        log.debug("Device key grant succeeded user={} device={} scope={}", user.id, deviceId, scopeParam)
        return createTokenResponse(responseBuilder, clientSessionCtx, false)
    }
}
