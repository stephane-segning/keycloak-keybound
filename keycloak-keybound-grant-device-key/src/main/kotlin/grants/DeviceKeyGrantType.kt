package com.ssegning.keycloak.keybound.grants

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.ssegning.keycloak.keybound.core.models.DeviceSignaturePayload
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
import org.keycloak.models.Constants
import org.keycloak.models.SingleUseObjectProvider
import org.keycloak.models.UserSessionModel
import org.keycloak.protocol.oidc.grants.OAuth2GrantType
import org.keycloak.protocol.oidc.grants.OAuth2GrantTypeBase
import org.keycloak.representations.AccessToken
import org.keycloak.services.CorsErrorResponseException
import org.keycloak.services.Urls
import org.keycloak.services.util.DefaultClientSessionContext
import org.keycloak.storage.StorageId
import org.keycloak.util.JsonSerialization
import org.keycloak.util.TokenUtil
import org.slf4j.LoggerFactory
import java.io.Serializable
import java.util.*
import kotlin.math.abs

class DeviceKeyGrantType(
    private val apiGateway: ApiGateway,
) : OAuth2GrantTypeBase() {
    companion object {
        private val log = LoggerFactory.getLogger(DeviceKeyGrantType::class.java)
        private const val TTL = 300L // 5 minutes
        private val conf = Configuration.builder()
            .options(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS)
            .build()
    }

    override fun getEventType(): EventType = EventType.LOGIN

    override fun process(context: OAuth2GrantType.Context): Response {
        setContext(context) // DO NOT REMOVE THIS LINE.

        val httpRequest = session.context.httpRequest
        val params = httpRequest.decodedFormParameters
        log.debug(
            "Device key grant request clientId={} remoteAddr={} hasDeviceId={} hasUserId={} hasNonce={} hasTs={} hasSig={}",
            client.clientId,
            session.context.connection.remoteAddr,
            params.containsKey("device_id"),
            params.containsKey("user_id"),
            params.containsKey("nonce"),
            params.containsKey("ts"),
            params.containsKey("sig"),
        )

        if (client.isBearerOnly) {
            event.detail(Details.REASON, "Bearer-only client doesn't have device key")
            event.error(Errors.INVALID_CLIENT)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.UNAUTHORIZED_CLIENT,
                "Bearer-only client not allowed to retrieve service account",
                Response.Status.UNAUTHORIZED,
            )
        }

        val deviceId = params.getFirst("device_id")
        val tsStr = params.getFirst("ts")
        val nonce = params.getFirst("nonce")
        val sig = params.getFirst("sig")
        val userId = params.getFirst("user_id")
        val requestPublicKey = params.getFirst("public_key")

        if (deviceId == null || tsStr == null || nonce == null || sig == null || userId == null) {
            log.debug(
                "Device key grant rejected before validation due to missing params deviceId={} userId={} tsPresent={} noncePresent={} sigPresent={}",
                deviceId,
                userId,
                tsStr != null,
                nonce != null,
                sig != null,
            )
            event.error(Errors.INVALID_REQUEST)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_REQUEST,
                "Missing parameters: device_id, ts, nonce, sig, and user_id are required",
                Response.Status.BAD_REQUEST,
            )
        }

        log.debug("Device key grant invoked userId={} deviceId={} clientId={}", userId, deviceId, client.clientId)

        val user = session.users().getUserById(realm, userId)
        if (user == null || !user.isEnabled) {
            log.debug("Device key grant user lookup failed userId={} enabled={}", userId, user?.isEnabled)
            event.error(Errors.USER_NOT_FOUND)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "User not found or disabled",
                Response.Status.BAD_REQUEST,
            )
        }

        val lookup = apiGateway.lookupDevice(deviceId = deviceId)
        log.debug(
            "Device lookup completed deviceId={} found={} backendUserId={} hasDeviceRecord={}",
            deviceId,
            lookup?.found,
            lookup?.userId,
            lookup?.device != null,
        )
        if (lookup == null || !lookup.found) {
            log.debug("Device key grant rejected because device was not found deviceId={}", deviceId)
            event.error(Errors.INVALID_USER_CREDENTIALS)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "Device not found",
                Response.Status.BAD_REQUEST,
            )
        }

        val backendUserId = lookup.userId
        val userIdCandidates =
            linkedSetOf<String>().apply {
                add(user.id)
                StorageId.externalId(user.id)?.takeIf { it.isNotBlank() }?.let { add(it) }
                user.getFirstAttribute("backend_user_id")?.takeIf { it.isNotBlank() }?.let { add(it) }
                user.getFirstAttribute("user_id")?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
        if (backendUserId.isNullOrBlank() || backendUserId !in userIdCandidates) {
            log.debug(
                "Device ownership mismatch deviceId={} backendUserId={} requestUserId={} candidates={}",
                deviceId,
                backendUserId,
                user.id,
                userIdCandidates,
            )
            event.error(Errors.INVALID_USER_CREDENTIALS)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "Device not registered for this user",
                Response.Status.BAD_REQUEST,
            )
        }

        val deviceRecord =
            lookup.device ?: run {
                log.debug("Device metadata missing from lookup deviceId={}", deviceId)
                event.error(Errors.INVALID_USER_CREDENTIALS)
                throw CorsErrorResponseException(
                    cors,
                    OAuthErrorException.INVALID_GRANT,
                    "Device metadata not found",
                    Response.Status.BAD_REQUEST,
                )
            }

        if (deviceRecord.status != DeviceStatus.ACTIVE) {
            log.debug("Device rejected because status is not active deviceId={} status={}", deviceId, deviceRecord.status)
            event.error(Errors.INVALID_USER_CREDENTIALS)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "Device is disabled",
                Response.Status.BAD_REQUEST,
            )
        }

        // Timestamp Verification
        val ts =
            tsStr.toLongOrNull() ?: throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_REQUEST,
                "Invalid timestamp",
                Response.Status.BAD_REQUEST,
            )

        val currentTime = Time.currentTimeMillis() / 1000
        log.debug("Timestamp check deviceId={} providedTs={} currentTs={} delta={}", deviceId, ts, currentTime, abs(currentTime - ts))
        if (abs(currentTime - ts) > TTL) {
            log.debug("Device key grant rejected due to expired timestamp deviceId={} providedTs={} currentTs={}", deviceId, ts, currentTime)
            event.error(Errors.INVALID_USER_CREDENTIALS)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "Timestamp expired",
                Response.Status.BAD_REQUEST,
            )
        }

        // Nonce Verification
        val suo = session.getProvider(SingleUseObjectProvider::class.java)
        val nonceKey = "device-grant-replay:${realm.name}:$nonce"
        log.debug("Checking nonce replay realm={} deviceId={} nonceKey={}", realm.name, deviceId, nonceKey)
        if (!suo.putIfAbsent(nonceKey, TTL)) {
            log.debug("Nonce replay detected deviceId={} realm={} nonceKey={}", deviceId, realm.name, nonceKey)
            event.error(Errors.INVALID_USER_CREDENTIALS)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "Nonce replay detected",
                Response.Status.BAD_REQUEST,
            )
        }

        // Signature Verification
        log.debug("Starting signature verification deviceId={} hasRequestPublicKey={}", deviceId, requestPublicKey != null)
        try {
            val publicKeyJwk =
                lookup.publicJwk?.let { JsonSerialization.writeValueAsString(TreeMap(it)) }
                    ?: requestPublicKey
                    ?: throw CorsErrorResponseException(
                        cors,
                        OAuthErrorException.INVALID_GRANT,
                        "Device public key not found",
                        Response.Status.BAD_REQUEST,
                    )

            log.debug("Verifying device signature deviceId={} jkt={}", deviceId, deviceRecord.jkt)
            val jwkParser = JWKParser.create().parse(publicKeyJwk)
            val publicKey = jwkParser.toPublicKey()

            val canonicalString =
                DeviceSignaturePayload(
                    deviceId = deviceId,
                    publicKey = publicKeyJwk,
                    ts = tsStr,
                    nonce = nonce,
                ).toCanonicalJson()
            val data = canonicalString.toByteArray(Charsets.UTF_8)

            val signatureBytes =
                try {
                    Base64.getDecoder().decode(sig)
                } catch (_: IllegalArgumentException) {
                    Base64.getUrlDecoder().decode(sig)
                }

            log.debug("Decoded device signature bytes deviceId={} length={}", deviceId, signatureBytes.size)

            val key =
                KeyWrapper().apply {
                    setPublicKey(publicKey)
                    algorithm = Algorithm.ES256
                }

            val verifier = ECDSASignatureVerifierContext(key)
            if (!verifier.verify(data, signatureBytes)) {
                log.debug("Device signature verification failed deviceId={} jkt={}", deviceId, deviceRecord.jkt)
                event.error(Errors.INVALID_USER_CREDENTIALS)
                throw CorsErrorResponseException(
                    cors,
                    OAuthErrorException.INVALID_GRANT,
                    "Invalid signature",
                    Response.Status.BAD_REQUEST,
                )
            }
        } catch (e: Exception) {
            log.error("Signature verification failed deviceId={} userId={}", deviceId, userId, e)
            event.error(Errors.INVALID_USER_CREDENTIALS)
            throw CorsErrorResponseException(
                cors,
                OAuthErrorException.INVALID_GRANT,
                "Signature verification failed",
                Response.Status.BAD_REQUEST,
            )
        }

        log.debug("Signature verified deviceId={} userId={} backendUserId={}", deviceId, user.id, backendUserId)

        // Create Session
        val sessionId = deviceId.ifBlank { UUID.randomUUID().toString() }
        log.debug("Creating user session deviceId={} userId={} sessionIdHint={}", deviceId, user.id, sessionId)
        val userSession =
            session.sessions().getUserSession(realm, sessionId)
                ?: session
                    .sessions()
                    .createUserSession(
                        sessionId,
                        realm,
                        user,
                        user.username ?: user.id,
                        session.context.connection.remoteAddr,
                        "device-grant",
                        false,
                        null,
                        null,
                        UserSessionModel.SessionPersistenceState.PERSISTENT,
                    )

        log.debug("Created or reused user session userSessionId={} userId={} deviceId={}", userSession.id, user.id, deviceId)

        // Add JKT to session notes
        log.debug("Setting confirmation thumbprint on session userSessionId={} deviceId={}", userSession.id, deviceId)
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
        log.debug("Minting access token userId={} clientId={} deviceId={}", user.id, client.clientId, deviceId)
        val accessToken =
            tokenManager.createClientAccessToken(
                session,
                realm,
                client,
                user,
                userSession,
                clientSessionCtx,
            )
        accessToken.issuer(Urls.realmIssuer(session.context.uri.baseUri, realm.name))
        accessToken.setOtherClaims("device_id", deviceId)

        // Force backend's ID, since only the backend users can call this endpoint
        accessToken.subject(backendUserId)

        val parametersKyc = JsonPath
            .using(conf)
            .parse(user.attributes)

        val parametersFirst: String? = parametersKyc.read("$.parameters[0]")
        log.debug("Token enrichment parameters present userId={} hasParameters={}", user.id, parametersFirst != null)

        if (parametersFirst != null) {
            val documentKyc = JsonPath
                .using(conf)
                .parse(parametersFirst)

            val fineractId: Serializable? = documentKyc.read("$.fineractId.content")
            log.debug("Token enrichment fineractId present userId={} present={}", user.id, fineractId != null)
            if (fineractId != null) {
                accessToken.setOtherClaims("fineract_client_id", fineractId)
            }

            val savingsAccountId: Serializable? = documentKyc.read("$.savingsAccountId.content")
            log.debug("Token enrichment savingsAccountId present userId={} present={}", user.id, savingsAccountId != null)
            if (savingsAccountId != null) {
                accessToken.setOtherClaims("savings_account_id", savingsAccountId)
            }
        }

        accessToken.setConfirmation(
            AccessToken.Confirmation().apply {
                keyThumbprint = deviceRecord.jkt
            },
        )
        val responseBuilder =
            tokenManager
                .responseBuilder(realm, client, event, session, userSession, clientSessionCtx)
                .accessToken(accessToken)
        if (TokenUtil.isOIDCRequest(scopeParam)) {
            responseBuilder.generateIDToken().generateAccessTokenHash()
        }

        log.debug("Device key grant succeeded userId={} deviceId={} scope={}", user.id, deviceId, scopeParam)
        return createTokenResponse(responseBuilder, clientSessionCtx, false)
    }
}
