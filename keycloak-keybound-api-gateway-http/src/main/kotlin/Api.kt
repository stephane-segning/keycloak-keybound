package com.ssegning.keycloak.keybound.api

import com.ssegning.keycloak.keybound.api.openapi.client.handler.ApprovalsApi
import com.ssegning.keycloak.keybound.api.openapi.client.handler.DevicesApi
import com.ssegning.keycloak.keybound.api.openapi.client.handler.EnrollmentApi
import com.ssegning.keycloak.keybound.api.openapi.client.handler.UsersApi
import com.ssegning.keycloak.keybound.api.openapi.client.model.*
import com.ssegning.keycloak.keybound.api.openapi.client.model.DeviceDescriptor
import com.ssegning.keycloak.keybound.core.helper.maskPhone
import com.ssegning.keycloak.keybound.core.helper.noop
import com.ssegning.keycloak.keybound.core.models.*
import com.ssegning.keycloak.keybound.core.models.DeviceRecord
import com.ssegning.keycloak.keybound.core.models.EnrollmentPath as CoreEnrollmentPath
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.authentication.AuthenticationFlowContext
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneOffset

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

        log.debug(
            "Sending SMS hash realm={} client={} phone={}",
            smsSendRequest.realm,
            smsSendRequest.clientId,
            maskPhone(phoneNumber)
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

        log.debug("Confirming SMS hash={} phone={}", hash, maskPhone(phoneNumber))

        return enrollmentApi.confirmSms(smsConfirmRequest).confirmed.toString()
    }

    override fun resolveUserByPhone(
        context: AuthenticationFlowContext,
        phoneNumber: String,
        userHint: String?
    ): PhoneResolveResult? = try {
        val response = enrollmentApi.resolveUserByPhone(
            PhoneResolveRequest(
                realm = context.realm.name,
                clientId = context.authenticationSession.client.clientId,
                phoneNumber = phoneNumber,
                userHint = userHint
            )
        )

        PhoneResolveResult(
            phoneNumber = response.phoneNumber,
            userExists = response.userExists,
            hasDeviceCredentials = response.hasDeviceCredentials,
            enrollmentPath = when (response.enrollmentPath) {
                EnrollmentPath.APPROVAL -> CoreEnrollmentPath.APPROVAL
                EnrollmentPath.OTP -> CoreEnrollmentPath.OTP
            },
            userId = response.userId,
            username = response.username
        )
    } catch (e: Exception) {
        log.error("Failed to resolve user by phone {}", maskPhone(phoneNumber), e)
        null
    }

    override fun resolveOrCreateUserByPhone(
        context: AuthenticationFlowContext,
        phoneNumber: String
    ): PhoneResolveOrCreateResult? = try {
        val response = enrollmentApi.resolveOrCreateUserByPhone(
            PhoneResolveOrCreateRequest(
                realm = context.realm.name,
                clientId = context.authenticationSession.client.clientId,
                phoneNumber = phoneNumber
            )
        )

        PhoneResolveOrCreateResult(
            phoneNumber = response.phoneNumber,
            userId = response.userId,
            username = response.username,
            created = response.created
        )
    } catch (e: Exception) {
        log.error("Failed to resolve or create user by phone {}", maskPhone(phoneNumber), e)
        null
    }

    override fun checkApprovalStatus(requestId: String): ApprovalStatus? = try {
        log.debug("Checking approval status for request {}", requestId)
        val response = approvalsApi.getApproval(requestId)
        log.debug("Approval request {} reported status {}", requestId, response.status)
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
        log.debug("Creating approval request for user {} device {}", userId, deviceData.deviceId)
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
        log.debug(
            "Performing enrollment precheck realm={} user={} device={}",
            context.realm.name,
            userId,
            deviceData.deviceId
        )
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
        log.debug("Binding device {} for user {} realm={}", deviceData.deviceId, userId, context.realm.name)
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
                createdAt = LocalDateTime.now()
            )
        )
        response.boundUserId == userId
    } catch (e: Exception) {
        log.error("Failed to bind device ${deviceData.deviceId} for user $userId", e)
        false
    }

    override fun listUserDevices(userId: String, includeDisabled: Boolean): List<DeviceRecord>? = try {
        log.debug("Listing devices for user {} includeDisabled={}", userId, includeDisabled)
        devicesApi.listUserDevices(userId, includeDisabled).devices.map { device ->
            DeviceRecord(
                deviceId = device.deviceId,
                jkt = device.jkt,
                status = when (device.status) {
                    com.ssegning.keycloak.keybound.api.openapi.client.model.DeviceRecord.Status.ACTIVE ->
                        DeviceStatus.ACTIVE

                    else -> DeviceStatus.DISABLED
                },
                createdAt = device.createdAt.atOffset(ZoneOffset.UTC),
                label = device.label
            )
        }
    } catch (e: Exception) {
        log.error("Failed to list devices for user $userId", e)
        null
    }

    override fun lookupDevice(deviceId: String?, jkt: String?): DeviceLookupResult? = try {
        log.debug("Looking up device deviceId={} jkt={}", deviceId, jkt)
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
            device = response.device?.let {
                DeviceRecord(
                    deviceId = it.deviceId,
                    jkt = it.jkt,
                    status = when (it.status) {
                        com.ssegning.keycloak.keybound.api.openapi.client.model.DeviceRecord.Status.ACTIVE -> DeviceStatus.ACTIVE
                        else -> DeviceStatus.DISABLED
                    },
                    createdAt = it.createdAt.atOffset(ZoneOffset.UTC),
                    label = it.label
                )
            },
            publicJwk = response.publicJwk
        )
    } catch (e: Exception) {
        log.error("Failed to lookup device by deviceId=$deviceId and jkt=$jkt", e)
        null
    }

    override fun disableDevice(userId: String, deviceId: String): Boolean = try {
        log.debug("Disabling device {} for user {}", deviceId, userId)
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
        log.debug("Creating backend user realm={}", realmName)
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
        log.error("Failed to create user in realm={}", realmName, e)
        null
    }

    override fun getUser(userId: String): BackendUser? = try {
        log.debug("Fetching backend user {}", userId)
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
        log.debug("Updating backend user realm={}", realmName)
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
        log.debug("Deleting backend user {}", userId)
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
        log.debug("Searching backend users realm={} criteria={}", realmName, criteria)
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
        createdAt = createdAt?.atOffset(ZoneOffset.UTC)
    )

    override fun close() = noop()
}
