package com.ssegning.keycloak.keybound.api

import com.ssegning.keycloak.keybound.api.openapi.client.handler.DevicesApi
import com.ssegning.keycloak.keybound.api.openapi.client.handler.EnrollmentApi
import com.ssegning.keycloak.keybound.api.openapi.client.handler.UsersApi
import com.ssegning.keycloak.keybound.api.openapi.client.model.*
import com.ssegning.keycloak.keybound.api.openapi.client.model.DeviceDescriptor
import com.ssegning.keycloak.keybound.core.helper.noop
import com.ssegning.keycloak.keybound.core.models.*
import com.ssegning.keycloak.keybound.core.models.DeviceRecord
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryRegistry
import org.slf4j.LoggerFactory
import kotlin.time.Clock

open class Api(
    val devicesApi: DevicesApi,
    val enrollmentApi: EnrollmentApi,
    val usersApi: UsersApi,
) : ApiGateway {
    companion object {
        private val log = LoggerFactory.getLogger(Api::class.java)
        private const val PUBLIC_KEY_LOGIN_CLIENT_ID = "device-public-key-login"
    }

    private val circuitBreakers = CircuitBreakerRegistry.ofDefaults()
    private val retries = RetryRegistry.ofDefaults()

    override fun enrollmentBindForRealm(
        realmName: String,
        userId: String,
        userHint: String?,
        deviceData: com.ssegning.keycloak.keybound.core.models.DeviceDescriptor,
        attributes: Map<String, String>?,
        proof: Map<String, Any>?,
    ): Boolean =
        executeGuarded(
            operation = "enrollment.bind.forClient",
            errorMessage = "Failed to bind device ${deviceData.deviceId} for user $userId in realm=$realmName",
        ) {
            val publicJwk = deviceData.publicJwk ?: return@executeGuarded false
            log.debug(
                "Binding device {} for user {} realm={} via non-client endpoint path",
                deviceData.deviceId,
                userId,
                realmName,
            )

            val response =
                enrollmentApi.enrollmentBind(
                    EnrollmentBindRequest(
                        realm = realmName,
                        clientId = PUBLIC_KEY_LOGIN_CLIENT_ID,
                        userId = userId,
                        userHint = userHint,
                        deviceId = deviceData.deviceId,
                        jkt = deviceData.jkt,
                        publicJwk = publicJwk,
                        attributes = attributes,
                        proof = proof,
                        createdAt = Clock.System.now(),
                    ),
                )
            response.boundUserId == userId
        } ?: false

    override fun lookupDevice(
        deviceId: String?,
        jkt: String?,
    ): DeviceLookupResult? {
        log.debug("Looking up device deviceId={} jkt={}", deviceId, jkt)
        if (deviceId.isNullOrBlank() && jkt.isNullOrBlank()) {
            return null
        }

        return executeGuarded(
            operation = "device.lookup",
            errorMessage = "Failed to lookup device by deviceId=$deviceId and jkt=$jkt",
        ) {
            val response =
                devicesApi.lookupDevice(
                    DeviceLookupRequest(
                        deviceId = deviceId,
                        jkt = jkt,
                    ),
                )

            DeviceLookupResult(
                found = response.found,
                userId = response.userId,
                device =
                    response.device?.let {
                        DeviceRecord(
                            deviceId = it.deviceId,
                            jkt = it.jkt,
                            status =
                                when (it.status) {
                                    DeviceRecordStatus.ACTIVE -> DeviceStatus.ACTIVE
                                    else -> DeviceStatus.DISABLED
                                },
                            createdAt = it.createdAt,
                            label = it.label,
                        )
                    },
                publicJwk = response.publicJwk,
            )
        }
    }

    override fun createUser(
        realmName: String,
        username: String,
        firstName: String?,
        lastName: String?,
        email: String?,
        enabled: Boolean?,
        emailVerified: Boolean?,
        attributes: Map<String, String>?,
    ): BackendUser? =
        executeGuarded(
            operation = "user.create",
            errorMessage = "Failed to create user in realm=$realmName",
        ) {
            log.debug("Creating backend user realm={}", realmName)
            usersApi
                .createUser(
                    UserUpsertRequest(
                        realm = realmName,
                        username = username,
                        firstName = firstName,
                        lastName = lastName,
                        email = email,
                        enabled = enabled,
                        emailVerified = emailVerified,
                        attributes = attributes,
                    ),
                ).toBackendUser()
        }

    override fun getUser(userId: String): BackendUser? =
        executeGuarded(
            operation = "user.get",
            errorMessage = "Failed to get user $userId",
        ) {
            log.debug("Fetching backend user {}", userId)
            usersApi.getUser(userId).toBackendUser()
        }

    override fun updateUser(
        userId: String,
        realmName: String,
        username: String,
        firstName: String?,
        lastName: String?,
        email: String?,
        enabled: Boolean?,
        emailVerified: Boolean?,
        attributes: Map<String, String>?,
    ): BackendUser? =
        executeGuarded(
            operation = "user.update",
            errorMessage = "Failed to update user $userId",
        ) {
            log.debug("Updating backend user realm={}", realmName)
            usersApi
                .updateUser(
                    userId = userId,
                    userUpsertRequest =
                        UserUpsertRequest(
                            realm = realmName,
                            username = username,
                            firstName = firstName,
                            lastName = lastName,
                            email = email,
                            enabled = enabled,
                            emailVerified = emailVerified,
                            attributes = attributes,
                        ),
                ).toBackendUser()
        }

    override fun deleteUser(userId: String): Boolean =
        executeGuarded(
            operation = "user.delete",
            errorMessage = "Failed to delete user $userId",
        ) {
            log.debug("Deleting backend user {}", userId)
            usersApi.deleteUser(userId)
            true
        } ?: false

    override fun searchUsers(
        realmName: String,
        criteria: BackendUserSearchCriteria,
    ): List<BackendUser>? =
        executeGuarded(
            operation = "user.search",
            errorMessage = "Failed to search users in realm=$realmName",
        ) {
            log.debug("Searching backend users realm={} criteria={}", realmName, criteria)
            usersApi
                .searchUsers(
                    UserSearchRequest(
                        realm = realmName,
                        search = criteria.search,
                        username = criteria.username,
                        firstName = criteria.firstName,
                        lastName = criteria.lastName,
                        email = criteria.email,
                        enabled = criteria.enabled,
                        emailVerified = criteria.emailVerified,
                        exact = criteria.exact,
                        attributes = criteria.attributes,
                        firstResult = criteria.firstResult,
                        maxResults = criteria.maxResults,
                    ),
                ).users
                .map { it.toBackendUser() }
        }

    private fun <T> executeGuarded(
        operation: String,
        errorMessage: String,
        block: () -> T,
    ): T? {
        val retry = retries.retry(operation)
        val circuitBreaker = circuitBreakers.circuitBreaker(operation)
        val retryingCall = Retry.decorateSupplier(retry) { block() }
        val guardedCall = CircuitBreaker.decorateSupplier(circuitBreaker, retryingCall)

        return try {
            guardedCall.get()
        } catch (e: Exception) {
            log.error(errorMessage, e)
            null
        }
    }

    private fun UserRecord.toBackendUser() =
        BackendUser(
            userId = userId,
            realm = realm,
            username = username,
            firstName = firstName,
            lastName = lastName,
            email = email,
            enabled = enabled,
            emailVerified = emailVerified,
            attributes = attributes.orEmpty(),
            createdAt = createdAt,
        )

    override fun close() = noop()
}
