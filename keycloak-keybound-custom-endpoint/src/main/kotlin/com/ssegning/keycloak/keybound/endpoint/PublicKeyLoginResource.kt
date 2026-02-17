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
import org.keycloak.models.utils.KeycloakModelUtils
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
    }

    data class PublicKeyLoginRequest(
        @param:JsonProperty("client_id")
        val clientId: String? = null,
        @param:JsonProperty("username")
        val username: String? = null,
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
        val usernameRaw = request.username.normalize()
        val deviceId = request.deviceId.normalize()
        val publicKeyJwk = request.publicKey.normalize()
        val nonce = request.nonce.normalize()
        val tsStr = request.ts.normalize()
        val sig = request.sig.normalize()

        val missing =
            listOfNotNull(
                "username".takeIf { usernameRaw == null },
                "device_id".takeIf { deviceId == null },
                "public_key".takeIf { publicKeyJwk == null },
                "nonce".takeIf { nonce == null },
                "ts".takeIf { tsStr == null },
                "sig".takeIf { sig == null },
            )
        if (missing.isNotEmpty()) {
            return badRequest("Missing required fields: ${missing.joinToString(", ")}")
        }

        val usernameRawValue = usernameRaw ?: return badRequest("Missing required fields: username")
        val deviceIdValue = deviceId ?: return badRequest("Missing required fields: device_id")
        val publicKeyJwkValue = publicKeyJwk ?: return badRequest("Missing required fields: public_key")
        val nonceValue = nonce ?: return badRequest("Missing required fields: nonce")
        val tsStrValue = tsStr ?: return badRequest("Missing required fields: ts")
        val sigValue = sig ?: return badRequest("Missing required fields: sig")
        val requestClientId = clientId

        val normalizedUsername = KeycloakModelUtils.toLowerCaseSafe(usernameRawValue)
        if (normalizedUsername.isBlank()) {
            return badRequest("username cannot be blank")
        }

        val fieldsToCheck =
            mapOf(
                "username" to normalizedUsername,
                "device_id" to deviceIdValue,
                "public_key" to publicKeyJwkValue,
                "nonce" to nonceValue,
                "ts" to tsStrValue,
                "sig" to sigValue,
            )
        val oversizedField = fieldsToCheck.entries.firstOrNull { (_, value) -> value.length > MAX_INPUT_LENGTH }?.key
        if (oversizedField != null) {
            return badRequest("$oversizedField exceeds max length of $MAX_INPUT_LENGTH")
        }

        val ttl = resolveTtlSeconds(realm.name)
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
                username = normalizedUsername,
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

        val userCreation = resolveOrCreateUser(realm, normalizedUsername)
        if (userCreation.response != null) {
            return userCreation.response
        }
        val user = userCreation.user ?: return internalError("Unable to resolve user")
        val createdUser = userCreation.created

        val backendUserId = resolveBackendUserId(user)

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

        val lookupByDevice = apiGateway.lookupDevice(deviceId = deviceIdValue) ?: return badGateway("Device lookup failed")
        val lookupByJkt = apiGateway.lookupDevice(jkt = jkt) ?: return badGateway("Device lookup failed")

        if (isConflict(lookupByDevice, backendUserId) || isConflict(lookupByJkt, backendUserId)) {
            return conflict("Device or key is already bound to another user")
        }

        val alreadyBoundToSameUser = isBoundToSameUser(lookupByDevice, backendUserId) || isBoundToSameUser(lookupByJkt, backendUserId)

        val credentialCreated =
            if (alreadyBoundToSameUser) {
                false
            } else {
                val bound =
                    apiGateway.enrollmentBindForRealm(
                        realmName = realm.name,
                        userId = backendUserId,
                        userHint = normalizedUsername,
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
                            ),
                    )
                if (!bound) {
                    return badGateway("Failed to persist device credential")
                }
                true
            }

        log.debug(
            "Public-key login succeeded client={} username={} keycloakUser={} backendUser={} created={} credentialCreated={}",
            requestClientId,
            normalizedUsername,
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
            ).build()
    }

    private data class UserCreationResult(
        val user: UserModel? = null,
        val created: Boolean = false,
        val response: Response? = null,
    )

    private fun resolveOrCreateUser(
        realm: RealmModel,
        username: String,
    ): UserCreationResult {
        val existing = session.users().getUserByUsername(realm, username)
        if (existing != null) {
            return UserCreationResult(user = existing, created = false)
        }

        val backendUser =
            apiGateway.createUser(
                realmName = realm.name,
                username = username,
            ) ?: return UserCreationResult(response = badGateway("Failed to create backend user"))

        val resolvedAfterCreate =
            resolveUserByBackendIdOrUsername(
                realm = realm,
                backendUserId = backendUser.userId,
                username = username,
            )

        if (resolvedAfterCreate == null) {
            log.error("Backend user {} created but not resolvable in Keycloak realm {}", backendUser.userId, realm.name)
            return UserCreationResult(response = internalError("Backend user created but unresolved in Keycloak"))
        }

        return UserCreationResult(user = resolvedAfterCreate, created = true)
    }

    private fun resolveUserByBackendIdOrUsername(
        realm: RealmModel,
        backendUserId: String,
        username: String,
    ): UserModel? =
        findSingleUserByAttribute(realm, "backend_user_id", backendUserId)
            ?: session.users().getUserByUsername(realm, username)
            ?: session.users().getUserByEmail(realm, username)

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

    private fun isConflict(
        lookup: DeviceLookupResult,
        backendUserId: String,
    ): Boolean {
        if (!lookup.found) return false
        val existingUserId = lookup.userId ?: return true
        return existingUserId != backendUserId
    }

    private fun isBoundToSameUser(
        lookup: DeviceLookupResult,
        backendUserId: String,
    ): Boolean = lookup.found && lookup.userId == backendUserId

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
            .build()
}
