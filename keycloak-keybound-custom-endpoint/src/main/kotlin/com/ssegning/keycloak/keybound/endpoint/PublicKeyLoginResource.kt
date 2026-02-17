package com.ssegning.keycloak.keybound.endpoint

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import com.ssegning.keycloak.keybound.core.endpoint.AbstractResource
import com.ssegning.keycloak.keybound.core.helper.computeJkt
import com.ssegning.keycloak.keybound.core.helper.getEnv
import com.ssegning.keycloak.keybound.core.helper.parsePublicJwk
import com.ssegning.keycloak.keybound.core.models.DeviceDescriptor
import com.ssegning.keycloak.keybound.core.models.DeviceLookupResult
import com.ssegning.keycloak.keybound.core.models.PublicKeyLoginSignaturePayload
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.OPTIONS
import jakarta.ws.rs.POST
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.keycloak.common.util.Time
import org.keycloak.crypto.Algorithm
import org.keycloak.crypto.ECDSASignatureVerifierContext
import org.keycloak.crypto.KeyWrapper
import org.keycloak.jose.jwk.JWKParser
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.SingleUseObjectProvider
import org.keycloak.models.UserModel
import org.keycloak.storage.StorageId
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.Base64
import kotlin.math.abs

class PublicKeyLoginResource(
    private val session: KeycloakSession,
    apiGateway: ApiGateway,
) : AbstractResource(apiGateway) {
    companion object {
        private val log = LoggerFactory.getLogger(PublicKeyLoginResource::class.java)
        private const val DEFAULT_TTL_SECONDS = 300L
        private const val MAX_INPUT_LENGTH = 2048
        private const val NONCE_KEY_PREFIX = "public-key-login-replay"
        private const val SOURCE_ATTRIBUTE_VALUE = "device-public-key-login"
        private const val CORS_ALLOW_ORIGIN = "*"
        private const val CORS_ALLOW_METHODS = "POST, OPTIONS"
        private const val CORS_ALLOW_HEADERS = "Content-Type, Authorization, X-Requested-With"
        private const val CORS_ALLOW_CREDENTIALS = "true"
    }

    data class PublicKeyLoginRequest(
        @param:JsonProperty("client_id")
        val clientId: String? = null,
        @param:JsonProperty("device_id")
        val deviceId: String? = null,
        @param:JsonProperty("public_key")
        val publicKey: String? = null,
        @param:JsonProperty("nonce")
        val nonce: String? = null,
        @param:JsonProperty("ts")
        @param:JsonAlias("timestamp")
        val ts: String? = null,
        @param:JsonProperty("sig")
        val sig: String? = null,
        @param:JsonProperty("pow_nonce")
        val powNonce: String? = null,
    )

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun login(request: PublicKeyLoginRequest?): Response {
        if (request == null) {
            return badRequest("Invalid JSON body")
        }

        val realm = session.context.realm
        val clientId = request.clientId.normalize()
        val deviceId = request.deviceId.normalize()
        val publicKeyJwk = request.publicKey.normalize()
        val nonce = request.nonce.normalize()
        val tsStr = request.ts.normalize()
        val sig = request.sig.normalize()
        val powNonce = request.powNonce.normalize()

        val missing =
            listOfNotNull(
                "device_id".takeIf { deviceId == null },
                "public_key".takeIf { publicKeyJwk == null },
                "nonce".takeIf { nonce == null },
                "ts".takeIf { tsStr == null },
                "sig".takeIf { sig == null },
            )
        if (missing.isNotEmpty()) {
            return badRequest("Missing required fields: ${missing.joinToString(", ")}")
        }

        val deviceIdValue = deviceId ?: return badRequest("Missing required fields: device_id")
        val publicKeyJwkValue = publicKeyJwk ?: return badRequest("Missing required fields: public_key")
        val nonceValue = nonce ?: return badRequest("Missing required fields: nonce")
        val tsStrValue = tsStr ?: return badRequest("Missing required fields: ts")
        val sigValue = sig ?: return badRequest("Missing required fields: sig")
        val requestClientId = clientId

        val fieldsToCheck =
            buildMap<String, String> {
                put("device_id", deviceIdValue)
                put("public_key", publicKeyJwkValue)
                put("nonce", nonceValue)
                put("ts", tsStrValue)
                put("sig", sigValue)
                powNonce?.let { put("pow_nonce", it) }
            }
        val oversizedField = fieldsToCheck.entries.firstOrNull { (_, value) -> value.length > MAX_INPUT_LENGTH }?.key
        if (oversizedField != null) {
            return badRequest("$oversizedField exceeds max length of $MAX_INPUT_LENGTH")
        }

        val ttl = resolveTtlSeconds(realm.name)
        val powDifficulty = resolvePowDifficulty(realm.name)
        if (powDifficulty > 0) {
            val powNonceValue = powNonce ?: return badRequest("Missing required fields: pow_nonce")
            if (!verifyPow(realm.name, deviceIdValue, tsStrValue, nonceValue, powNonceValue, powDifficulty)) {
                return unauthorized("Invalid proof-of-work")
            }
        }
        val ts = tsStrValue.toLongOrNull() ?: return badRequest("Invalid timestamp")
        val now = Time.currentTimeMillis() / 1000
        if (abs(now - ts) > ttl) {
            return unauthorized("Timestamp expired")
        }

        val singleUse = session.getProvider(SingleUseObjectProvider::class.java)
        val nonceKey = "$NONCE_KEY_PREFIX:${realm.name}:$nonceValue"
        if (!singleUse.putIfAbsent(nonceKey, ttl)) {
            return unauthorized("Nonce replay detected")
        }

        val signatureBytes = decodeBase64OrBase64Url(sigValue) ?: return badRequest("Malformed signature encoding")
        if (signatureBytes.size != 64) {
            return badRequest("Invalid signature format: expected compact ES256 (64 bytes)")
        }

        val parsedJwk =
            try {
                JWKParser.create().parse(publicKeyJwkValue)
            } catch (e: Exception) {
                log.debug("Failed to parse public_key JWK", e)
                return badRequest("Malformed public_key JWK")
            }

        val publicKey =
            try {
                parsedJwk.toPublicKey()
            } catch (e: Exception) {
                log.debug("Failed to convert JWK to public key", e)
                return badRequest("Unable to parse EC public key")
            }

        val canonicalPayload =
            PublicKeyLoginSignaturePayload(
                nonce = nonceValue,
                deviceId = deviceIdValue,
                ts = tsStrValue,
                publicKey = publicKeyJwkValue,
            ).toCanonicalJson()

        val verifier =
            ECDSASignatureVerifierContext(
                KeyWrapper().apply {
                    setPublicKey(publicKey)
                    algorithm = Algorithm.ES256
                },
            )
        if (!verifier.verify(canonicalPayload.toByteArray(Charsets.UTF_8), signatureBytes)) {
            return unauthorized("Invalid signature")
        }

        val jkt =
            try {
                computeJkt(publicKeyJwkValue)
            } catch (e: Exception) {
                return badRequest("Invalid public_key thumbprint data")
            }
        val publicJwkMap =
            try {
                parsePublicJwk(publicKeyJwkValue)
            } catch (e: Exception) {
                return badRequest("Malformed public_key JWK")
            }

        // Some backends answer 404 for first-time lookups; treat null as "not found" at this pre-bind stage.
        val lookupByDevice = apiGateway.lookupDevice(deviceId = deviceIdValue) ?: DeviceLookupResult(found = false)
        val lookupByJkt = apiGateway.lookupDevice(jkt = jkt) ?: DeviceLookupResult(found = false)

        if (lookupByDevice.found || lookupByJkt.found) {
            return conflict("Device or key is already associated with a user")
        }

        val userCreation =
            createBackendUserAndResolve(
                realm = realm,
                deviceId = deviceIdValue,
                nonce = nonceValue,
            )
        if (userCreation.response != null) {
            return userCreation.response
        }
        val user = userCreation.user ?: return internalError("Unable to resolve user")
        val createdUser = userCreation.created
        val backendUserId = resolveBackendUserId(user)

        val credentialCreated =
            run {
                val bound =
                    apiGateway.enrollmentBindForRealm(
                        realmName = realm.name,
                        userId = backendUserId,
                        userHint = backendUserId,
                        deviceData =
                            DeviceDescriptor(
                                deviceId = deviceIdValue,
                                jkt = jkt,
                                publicJwk = publicJwkMap,
                                platform = null,
                                model = null,
                                appVersion = null,
                            ),
                        attributes =
                            buildMap {
                                put("source", SOURCE_ATTRIBUTE_VALUE)
                                requestClientId?.let { put("request_client_id", it) }
                            },
                        proof =
                            mapOf(
                                "ts" to tsStrValue,
                                "nonce" to nonceValue,
                                "sig_sha256" to sha256Base64Url(signatureBytes),
                                "pow_nonce" to (powNonce ?: ""),
                                "pow_difficulty" to powDifficulty,
                            ),
                    )
                if (!bound) {
                    return badGateway("Failed to persist device credential")
                }
                true
            }

        log.debug(
            "Public-key login succeeded client={} keycloakUser={} backendUser={} created={} credentialCreated={}",
            requestClientId,
            user.id,
            backendUserId,
            createdUser,
            credentialCreated,
        )
        return Response
            .ok(
                mapOf(
                    "user_id" to user.id,
                    "created_user" to createdUser,
                    "credential_created" to credentialCreated,
                ),
            )
            .withCorsHeaders()
            .build()
    }

    @OPTIONS
    @Produces(MediaType.APPLICATION_JSON)
    fun options(): Response = Response
        .ok()
        .withCorsHeaders()
        .build()

    private data class UserCreationResult(
        val user: UserModel? = null,
        val created: Boolean = false,
        val response: Response? = null,
    )

    private fun createBackendUserAndResolve(
        realm: RealmModel,
        deviceId: String,
        nonce: String,
    ): UserCreationResult {
        val technicalUsername = "kb_${sha256Base64Url("$deviceId:$nonce".toByteArray(Charsets.UTF_8)).take(32)}"
        val backendUser =
            apiGateway.createUser(
                realmName = realm.name,
                username = technicalUsername,
            ) ?: return UserCreationResult(response = badGateway("Failed to create backend user"))

        val resolvedAfterCreate =
            resolveUserByBackendId(
                realm = realm,
                backendUserId = backendUser.userId,
            )

        if (resolvedAfterCreate == null) {
            log.error("Backend user {} created but not resolvable in Keycloak realm {}", backendUser.userId, realm.name)
            return UserCreationResult(response = internalError("Backend user created but unresolved in Keycloak"))
        }

        return UserCreationResult(user = resolvedAfterCreate, created = true)
    }

    private fun resolveUserByBackendId(
        realm: RealmModel,
        backendUserId: String,
    ): UserModel? =
        findSingleUserByAttribute(realm, "backend_user_id", backendUserId)
            ?: session.users().getUserById(realm, backendUserId)

    private fun findSingleUserByAttribute(
        realm: RealmModel,
        attributeName: String,
        attributeValue: String,
    ): UserModel? {
        val stream = session.users().searchForUserByUserAttributeStream(realm, attributeName, attributeValue)
        return try {
            val iterator = stream.iterator()
            if (!iterator.hasNext()) {
                null
            } else {
                val first = iterator.next()
                if (iterator.hasNext()) {
                    log.error("Multiple users resolved for {}={} in realm {}", attributeName, attributeValue, realm.name)
                    null
                } else {
                    first
                }
            }
        } finally {
            stream.close()
        }
    }

    private fun resolveBackendUserId(user: UserModel): String {
        val backendAttributeId = user.getFirstAttribute("backend_user_id")?.trim()
        if (!backendAttributeId.isNullOrBlank()) {
            return backendAttributeId
        }
        return StorageId.externalId(user.id) ?: user.id
    }

    private fun resolveTtlSeconds(realmName: String): Long {
        val ttlFromEnv = "NONCE_CACHE_TTL_$realmName".getEnv()?.toLongOrNull()
        if (ttlFromEnv != null && ttlFromEnv > 0L) {
            return ttlFromEnv
        }
        return DEFAULT_TTL_SECONDS
    }

    private fun resolvePowDifficulty(realmName: String): Int {
        val fromEnv = "PUBLIC_KEY_LOGIN_POW_DIFFICULTY_$realmName".getEnv()?.toIntOrNull()
        if (fromEnv != null && fromEnv >= 0) {
            return fromEnv
        }
        return 0
    }

    private fun decodeBase64OrBase64Url(value: String): ByteArray? =
        try {
            Base64.getUrlDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            try {
                Base64.getDecoder().decode(value)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

    private fun sha256Base64Url(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(digest)
    }

    private fun verifyPow(
        realmName: String,
        deviceId: String,
        ts: String,
        nonce: String,
        powNonce: String,
        difficulty: Int,
    ): Boolean {
        val material = "$realmName:$deviceId:$ts:$nonce:$powNonce"
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return hasLeadingZeroNibbles(digest, difficulty)
    }

    private fun hasLeadingZeroNibbles(
        digest: ByteArray,
        difficulty: Int,
    ): Boolean {
        if (difficulty <= 0) return true
        var remaining = difficulty
        for (byteValue in digest) {
            val unsigned = byteValue.toInt() and 0xFF
            val highNibble = unsigned ushr 4
            val lowNibble = unsigned and 0x0F

            if (remaining > 0) {
                if (highNibble != 0) return false
                remaining--
            }

            if (remaining > 0) {
                if (lowNibble != 0) return false
                remaining--
            }

            if (remaining == 0) return true
        }
        return remaining == 0
    }

    private fun String?.normalize(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    private fun badRequest(message: String): Response = error(Response.Status.BAD_REQUEST, message)

    private fun unauthorized(message: String): Response = error(Response.Status.UNAUTHORIZED, message)

    private fun conflict(message: String): Response = error(Response.Status.CONFLICT, message)

    private fun internalError(message: String): Response = error(Response.Status.INTERNAL_SERVER_ERROR, message)

    private fun badGateway(message: String): Response = error(Response.Status.BAD_GATEWAY, message)

    private fun error(
        status: Response.Status,
        message: String,
    ): Response =
        Response
            .status(status)
            .entity(mapOf("error" to message))
            .withCorsHeaders()
            .build()

    private fun Response.ResponseBuilder.withCorsHeaders(): Response.ResponseBuilder =
        this
            .header("Access-Control-Allow-Origin", CORS_ALLOW_ORIGIN)
            .header("Access-Control-Allow-Methods", CORS_ALLOW_METHODS)
            .header("Access-Control-Allow-Headers", CORS_ALLOW_HEADERS)
            .header("Access-Control-Allow-Credentials", CORS_ALLOW_CREDENTIALS)
}
