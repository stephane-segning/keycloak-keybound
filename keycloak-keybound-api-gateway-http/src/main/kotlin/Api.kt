package com.ssegning.keycloak.keybound.api

import com.ssegning.keycloak.keybound.api.openapi.client.handler.ApprovalsApi
import com.ssegning.keycloak.keybound.api.openapi.client.handler.DevicesApi
import com.ssegning.keycloak.keybound.api.openapi.client.handler.EnrollmentApi
import com.ssegning.keycloak.keybound.api.openapi.client.handler.UsersApi
import com.ssegning.keycloak.keybound.api.openapi.client.model.ApprovalCreateRequest
import com.ssegning.keycloak.keybound.api.openapi.client.model.ApprovalStatusResponse
import com.ssegning.keycloak.keybound.api.openapi.client.model.DeviceLookupRequest
import com.ssegning.keycloak.keybound.api.openapi.client.model.DeviceDescriptor
import com.ssegning.keycloak.keybound.api.openapi.client.model.EnrollmentBindRequest
import com.ssegning.keycloak.keybound.api.openapi.client.model.EnrollmentPrecheckRequest
import com.ssegning.keycloak.keybound.api.openapi.client.model.EnrollmentPrecheckResponse
import com.ssegning.keycloak.keybound.api.openapi.client.model.SmsConfirmRequest
import com.ssegning.keycloak.keybound.api.openapi.client.model.SmsSendRequest
import com.ssegning.keycloak.keybound.api.openapi.client.model.UserRecord
import com.ssegning.keycloak.keybound.api.openapi.client.model.UserSearchRequest
import com.ssegning.keycloak.keybound.api.openapi.client.model.UserUpsertRequest
import com.ssegning.keycloak.keybound.core.models.BackendUser
import com.ssegning.keycloak.keybound.core.models.BackendUserSearchCriteria
import com.ssegning.keycloak.keybound.core.models.DeviceLookupResult
import com.ssegning.keycloak.keybound.core.models.DeviceRecord
import com.ssegning.keycloak.keybound.core.models.DeviceStatus
import com.ssegning.keycloak.keybound.core.models.EnrollmentDecision
import com.ssegning.keycloak.keybound.core.models.EnrollmentPrecheckResult
import com.ssegning.keycloak.keybound.core.helper.noop
import com.ssegning.keycloak.keybound.core.models.ApprovalStatus
import com.ssegning.keycloak.keybound.core.models.SmsRequest
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.authentication.AuthenticationFlowContext
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime

open class Api(
    val devicesApi: DevicesApi,
    val approvalsApi: ApprovalsApi,
    val enrollmentApi: EnrollmentApi,
    val usersApi: UsersApi
) : ApiGateway {
    companion object {
        private val log = LoggerFactory.getLogger(Api::class.java)
    }

    /**
     * Sends an SMS with an OTP to the given phone number.
     *
     * This method makes a POST request to the backend endpoint `/v1/sms/send`.
     * The request body contains the phone number and the OTP.
     * The backend is expected to return a JSON object containing a hash, which is returned by this method.
     */
    override fun sendSmsAndGetHash(
        context: AuthenticationFlowContext,
        request: SmsRequest,
        phoneNumber: String
    ): String {
        val otp = request.metadata?.get("otp") as? String
            ?: throw IllegalArgumentException("OTP not found in request metadata")

        val smsSendRequest = SmsSendRequest(
            realm = request.realm ?: context.realm?.name.orEmpty(),
            clientId = request.clientId ?: context.authenticationSession.client.clientId,
            phoneNumber = phoneNumber,
            otp = otp,
            userId = context.user?.id,
            sessionId = request.sessionId,
            traceId = request.traceId,
            metadata = request.metadata?.mapNotNull { (key, value) ->
                key?.let { nonNullKey ->
                    value?.let { nonNullValue -> nonNullKey to nonNullValue }
                }
            }?.toMap()
        )

        return enrollmentApi.sendSms(smsSendRequest).hash
    }

    /**
     * Confirms the SMS code (OTP) using the hash received earlier.
     *
     * This method makes a POST request to the backend endpoint `/v1/sms/confirm`.
     * The request body contains the hash and the OTP.
     * The backend is expected to return `true` if the code is valid, and `false` otherwise.
     */
    override fun confirmSmsCode(
        context: AuthenticationFlowContext,
        request: SmsRequest,
        phoneNumber: String,
        code: String,
        hash: String
    ): String {
        val smsConfirmRequest = SmsConfirmRequest(hash = hash, otp = code)
        return enrollmentApi.confirmSms(smsConfirmRequest).confirmed.toString()
    }

    override fun checkApprovalStatus(requestId: String): ApprovalStatus? = try {
        val response = approvalsApi.getApproval(requestId)
        when (response.status) {
            ApprovalStatusResponse.Status.PENDING -> ApprovalStatus.PENDING
            ApprovalStatusResponse.Status.APPROVED -> ApprovalStatus.APPROVED
            ApprovalStatusResponse.Status.DENIED -> ApprovalStatus.DENIED
            ApprovalStatusResponse.Status.EXPIRED -> ApprovalStatus.EXPIRED
        }
    } catch (e: Exception) {
        log.error("Failed to check approval status for request $requestId", e)
        null
    }

    override fun createApprovalRequest(
        context: AuthenticationFlowContext,
        userId: String,
        deviceData: com.ssegning.keycloak.keybound.core.models.DeviceDescriptor
    ): String? = try {
        val response = approvalsApi.createApproval(
            ApprovalCreateRequest(
                realm = context.realm.name,
                clientId = context.authenticationSession.client.clientId,
                userId = userId,
                newDevice = DeviceDescriptor(
                    deviceId = deviceData.deviceId,
                    jkt = deviceData.jkt,
                    publicJwk = deviceData.publicJwk,
                    platform = deviceData.platform,
                    model = deviceData.model,
                    appVersion = deviceData.appVersion
                )
            )
        )
        response.requestId
    } catch (e: Exception) {
        log.error("Failed to create approval request for user $userId", e)
        null
    }

    override fun enrollmentPrecheck(
        context: AuthenticationFlowContext,
        userId: String,
        userHint: String?,
        deviceData: com.ssegning.keycloak.keybound.core.models.DeviceDescriptor
    ): EnrollmentPrecheckResult? = try {
        val response = enrollmentApi.enrollmentPrecheck(
            EnrollmentPrecheckRequest(
                realm = context.realm.name,
                clientId = context.authenticationSession.client.clientId,
                userHint = userHint ?: userId,
                deviceId = deviceData.deviceId,
                jkt = deviceData.jkt,
                publicJwk = deviceData.publicJwk
            )
        )

        EnrollmentPrecheckResult(
            decision = when (response.decision) {
                EnrollmentPrecheckResponse.Decision.ALLOW -> EnrollmentDecision.ALLOW
                EnrollmentPrecheckResponse.Decision.REQUIRE_APPROVAL -> EnrollmentDecision.REQUIRE_APPROVAL
                EnrollmentPrecheckResponse.Decision.REJECT -> EnrollmentDecision.REJECT
            },
            reason = response.reason,
            boundUserId = response.boundUserId,
            retryAfterSeconds = response.retryAfterSeconds
        )
    } catch (e: Exception) {
        log.error("Failed to precheck device enrollment for user $userId", e)
        null
    }

    override fun enrollmentBind(
        context: AuthenticationFlowContext,
        userId: String,
        userHint: String?,
        deviceData: com.ssegning.keycloak.keybound.core.models.DeviceDescriptor,
        attributes: Map<String, String>?,
        proof: Map<String, Any>?
    ): Boolean = try {
        val publicJwk = deviceData.publicJwk ?: return false
        val response = enrollmentApi.enrollmentBind(
            EnrollmentBindRequest(
                realm = context.realm.name,
                clientId = context.authenticationSession.client.clientId,
                userId = userId,
                userHint = userHint,
                deviceId = deviceData.deviceId,
                jkt = deviceData.jkt,
                publicJwk = publicJwk,
                attributes = attributes,
                proof = proof,
                createdAt = OffsetDateTime.now()
            )
        )
        response.boundUserId == userId
    } catch (e: Exception) {
        log.error("Failed to bind device ${deviceData.deviceId} for user $userId", e)
        false
    }

    override fun listUserDevices(userId: String, includeDisabled: Boolean): List<DeviceRecord>? = try {
        devicesApi.listUserDevices(userId, includeDisabled).devices.map { device ->
            DeviceRecord(
                deviceId = device.deviceId,
                jkt = device.jkt,
                status = when (device.status) {
                    com.ssegning.keycloak.keybound.api.openapi.client.model.DeviceRecord.Status.ACTIVE ->
                        DeviceStatus.ACTIVE

                    else -> DeviceStatus.DISABLED
                },
                createdAt = device.createdAt,
                label = device.label
            )
        }
    } catch (e: Exception) {
        log.error("Failed to list devices for user $userId", e)
        null
    }

    override fun lookupDevice(deviceId: String?, jkt: String?): DeviceLookupResult? = try {
        if (deviceId.isNullOrBlank() && jkt.isNullOrBlank()) {
            return null
        }

        val response = devicesApi.lookupDevice(
            DeviceLookupRequest(
                deviceId = deviceId,
                jkt = jkt
            )
        )
        DeviceLookupResult(
            found = response.found,
            userId = response.userId,
            device = response.device?.let { device ->
                DeviceRecord(
                    deviceId = device.deviceId,
                    jkt = device.jkt,
                    status = when (device.status) {
                        com.ssegning.keycloak.keybound.api.openapi.client.model.DeviceRecord.Status.ACTIVE ->
                            DeviceStatus.ACTIVE

                        else -> DeviceStatus.DISABLED
                    },
                    createdAt = device.createdAt,
                    label = device.label
                )
            },
            publicJwk = response.publicJwk
        )
    } catch (e: Exception) {
        log.error("Failed to lookup device by deviceId=$deviceId and jkt=$jkt", e)
        null
    }

    override fun disableDevice(userId: String, deviceId: String): Boolean = try {
        devicesApi.disableUserDevice(userId, deviceId)
        true
    } catch (e: Exception) {
        log.error("Failed to disable device $deviceId for user $userId", e)
        false
    }

    override fun createUser(
        realmName: String,
        username: String,
        firstName: String?,
        lastName: String?,
        email: String?,
        enabled: Boolean?,
        emailVerified: Boolean?,
        attributes: Map<String, String>?
    ): BackendUser? = try {
        usersApi.createUser(
            UserUpsertRequest(
                realm = realmName,
                username = username,
                firstName = firstName,
                lastName = lastName,
                email = email,
                enabled = enabled,
                emailVerified = emailVerified,
                attributes = attributes
            )
        ).toBackendUser()
    } catch (e: Exception) {
        log.error("Failed to create user username={} in realm={}", username, realmName, e)
        null
    }

    override fun getUser(userId: String): BackendUser? = try {
        usersApi.getUser(userId).toBackendUser()
    } catch (e: Exception) {
        log.error("Failed to get user {}", userId, e)
        null
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
        attributes: Map<String, String>?
    ): BackendUser? = try {
        usersApi.updateUser(
            userId = userId,
            userUpsertRequest = UserUpsertRequest(
                realm = realmName,
                username = username,
                firstName = firstName,
                lastName = lastName,
                email = email,
                enabled = enabled,
                emailVerified = emailVerified,
                attributes = attributes
            )
        ).toBackendUser()
    } catch (e: Exception) {
        log.error("Failed to update user {}", userId, e)
        null
    }

    override fun deleteUser(userId: String): Boolean = try {
        usersApi.deleteUser(userId)
        true
    } catch (e: Exception) {
        log.error("Failed to delete user {}", userId, e)
        false
    }

    override fun searchUsers(
        realmName: String,
        criteria: BackendUserSearchCriteria
    ): List<BackendUser>? = try {
        usersApi.searchUsers(
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
                maxResults = criteria.maxResults
            )
        ).users.map { it.toBackendUser() }
    } catch (e: Exception) {
        log.error("Failed to search users in realm={}", realmName, e)
        null
    }

    private fun UserRecord.toBackendUser() = BackendUser(
        userId = userId,
        realm = realm,
        username = username,
        firstName = firstName,
        lastName = lastName,
        email = email,
        enabled = enabled,
        emailVerified = emailVerified,
        attributes = attributes.orEmpty(),
        createdAt = createdAt
    )

    override fun close() = noop()
}
