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
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
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
import java.util.*
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
        private const val DEVICE_ID_ATTRIBUTE = "device_id"
        private const val DEVICE_OS_ATTRIBUTE = "device_os"
        private const val DEVICE_MODEL_ATTRIBUTE = "device_model"
        private const val DEVICE_APP_VERSION_ATTRIBUTE = "device_app_version"
        private const val CORS_ALLOW_ORIGIN = "*"
        private const val CORS_ALLOW_METHODS = "POST, OPTIONS"
        private const val CORS_ALLOW_HEADERS =
            "Content-Type, Authorization, X-Requested-With, x-public-key, x-signature, x-signature-timestamp"

        // Keep this endpoint usable cross-origin with wildcard origin; credentials would require echoing Origin.
        private const val CORS_ALLOW_CREDENTIALS = "false"
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
        @param:JsonProperty("device_os")
        val deviceOs: String? = null,
        @param:JsonProperty("device_model")
        val deviceModel: String? = null,
        @param:JsonProperty("device_app_version")
        val deviceAppVersion: String? = null,
    )

    @POST
    @Path("")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun login(request: PublicKeyLoginRequest?): Response {
        if (request == null) {
            return badRequest("Invalid JSON body")
        }

        val realm = session.context.realm
        val deviceId = request.deviceId.normalize()
        val publicKeyJwk = request.publicKey.normalize()
        val nonce = request.nonce.normalize()
        val tsStr = request.ts.normalize()
        val sig = request.sig.normalize()
        val powNonce = request.powNonce.normalize()
        val deviceOs = request.deviceOs.normalize()
        val deviceModel = request.deviceModel.normalize()
        val deviceAppVersion = request.deviceAppVersion.normalize()

        val missing =
            listOfNotNull(
                "device_id".takeIf { deviceId == null },
                "public_key".takeIf { publicKeyJwk == null },
                "nonce".takeIf { nonce == null },
                "ts".takeIf { tsStr == null },
                "sig".takeIf { sig == null },
                "device_os".takeIf { deviceOs == null },
                "device_model".takeIf { deviceModel == null },
            )
        if (missing.isNotEmpty()) {
            return badRequest("Missing required fields: ${missing.joinToString(", ")}")
        }

        val deviceIdValue = deviceId ?: return badRequest("Missing required fields: device_id")
        val publicKeyJwkValue = publicKeyJwk ?: return badRequest("Missing required fields: public_key")
        val nonceValue = nonce ?: return badRequest("Missing required fields: nonce")
        val tsStrValue = tsStr ?: return badRequest("Missing required fields: ts")
        val sigValue = sig ?: return badRequest("Missing required fields: sig")
        val deviceOsValue = deviceOs ?: return badRequest("Missing required fields: device_os")
        val deviceModelValue = deviceModel ?: return badRequest("Missing required fields: device_model")

        val fieldsToCheck =
            buildMap {
                put("device_id", deviceIdValue)
                put("public_key", publicKeyJwkValue)
                put("nonce", nonceValue)
                put("ts", tsStrValue)
                put("sig", sigValue)
                put("device_os", deviceOsValue)
                put("device_model", deviceModelValue)
                powNonce?.let { put("pow_nonce", it) }
                deviceAppVersion?.let { put("device_app_version", it) }
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
                log.error("Malformed public_key JWK", e)
                return badRequest("Invalid public_key thumbprint data")
            }
        val publicJwkMap =
            try {
                parsePublicJwk(publicKeyJwkValue)
            } catch (e: Exception) {
                log.error("Malformed public_key JWK", e)
                return badRequest("Malformed public_key JWK")
            }

        // Some backends answer 404 for first-time lookups; treat null as "not found" at this pre-bind stage.
        val lookupByDevice = apiGateway.lookupDevice(deviceId = deviceIdValue) ?: DeviceLookupResult(found = false)
        val lookupByJkt = apiGateway.lookupDevice(jkt = jkt) ?: DeviceLookupResult(found = false)

        if (lookupByDevice.found || lookupByJkt.found) {
            return conflict("Device or key is already associated with a user")
        }

        val userResolution =
            resolveOrCreateUser(
                realm = realm,
                deviceId = deviceIdValue,
                nonce = nonceValue,
                deviceOs = deviceOsValue,
                deviceModel = deviceModelValue,
                deviceAppVersion = deviceAppVersion,
            ) ?: return internalError("Unable to create user")
        val user = userResolution.user
        val createdUser = userResolution.created
        val backendUserId = user.id

        val credentialCreated =
            run {
                val bound =
                    apiGateway.enrollmentBindForRealm(
                        realmName = realm.name,
                        userId = StorageId.externalId(backendUserId),
                        userHint = user.username ?: backendUserId,
                        deviceData =
                            DeviceDescriptor(
                                deviceId = deviceIdValue,
                                jkt = jkt,
                                publicJwk = publicJwkMap,
                                platform = deviceOsValue,
                                model = deviceModelValue,
                                appVersion = deviceAppVersion,
                            ),
                        attributes =
                            buildMap {
                                put("source", SOURCE_ATTRIBUTE_VALUE)
                                put(DEVICE_OS_ATTRIBUTE, deviceOsValue)
                                put(DEVICE_MODEL_ATTRIBUTE, deviceModelValue)
                                deviceAppVersion?.let { put(DEVICE_APP_VERSION_ATTRIBUTE, it) }
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
            "Public-key login succeeded keycloakUser={} backendUser={} created={} credentialCreated={}",
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
            ).withCorsHeaders()
            .build()
    }

    @OPTIONS
    @Path("{path:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    fun options(
        @PathParam("path") ignored: String?,
    ): Response =
        Response
            .ok()
            .withCorsHeaders()
            .build()

    private fun resolveTtlSeconds(realmName: String): Long {
        val ttlFromEnv = "NONCE_CACHE_TTL_$realmName".getEnv()?.toLongOrNull()
        if (ttlFromEnv != null && ttlFromEnv > 0L) {
            return ttlFromEnv
        }
        return DEFAULT_TTL_SECONDS
    }

    private data class UserResolution(
        val user: UserModel,
        val created: Boolean,
    )

    private fun resolveOrCreateUser(
        realm: RealmModel,
        deviceId: String,
        nonce: String,
        deviceOs: String,
        deviceModel: String,
        deviceAppVersion: String?,
    ): UserResolution? {
        val existingUser = findSingleUserByAttribute(realm, DEVICE_ID_ATTRIBUTE, deviceId)
        if (existingUser != null) {
            updateDeviceAttributes(existingUser, deviceId, deviceOs, deviceModel, deviceAppVersion)
            return UserResolution(existingUser, created = false)
        }

        val username = generateTechnicalUsername(deviceId, nonce)
        val newUser =
            session.users().addUser(realm, username) ?: run {
                log.error("Failed to create Keycloak user for device {}", deviceId)
                return null
            }
        newUser.isEnabled = true
        updateDeviceAttributes(newUser, deviceId, deviceOs, deviceModel, deviceAppVersion)
        return UserResolution(newUser, created = true)
    }

    private fun updateDeviceAttributes(
        user: UserModel,
        deviceId: String,
        deviceOs: String,
        deviceModel: String,
        deviceAppVersion: String?,
    ) {
        user.setSingleAttribute(DEVICE_ID_ATTRIBUTE, deviceId)
        user.setSingleAttribute(DEVICE_OS_ATTRIBUTE, deviceOs)
        user.setSingleAttribute(DEVICE_MODEL_ATTRIBUTE, deviceModel)
        if (deviceAppVersion != null) {
            user.setSingleAttribute(DEVICE_APP_VERSION_ATTRIBUTE, deviceAppVersion)
        } else {
            user.removeAttribute(DEVICE_APP_VERSION_ATTRIBUTE)
        }
    }

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
                    log.error(
                        "Multiple users resolved for {}={} in realm {}",
                        attributeName,
                        attributeValue,
                        realm.name,
                    )
                    null
                } else {
                    first
                }
            }
        } finally {
            stream.close()
        }
    }

    private fun generateTechnicalUsername(
        deviceId: String,
        nonce: String,
    ): String = "kb_${sha256Base64Url("$deviceId:$nonce".toByteArray(Charsets.UTF_8)).take(32)}"

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
