package com.ssegning.keycloak.keybound.examples.backend.store

import com.ssegning.keycloak.keybound.examples.backend.api.*
import com.ssegning.keycloak.keybound.examples.backend.model.*
import org.openapitools.jackson.nullable.JsonNullable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Service
class BackendDataStore {
    private val users = ConcurrentHashMap<String, StoredUser>()
    private val usernameIndex = ConcurrentHashMap<String, String>()
    private val emailIndex = ConcurrentHashMap<String, String>()

    private val devicesById = ConcurrentHashMap<String, StoredDevice>()
    private val devicesByJkt = ConcurrentHashMap<String, StoredDevice>()

    private val approvals = ConcurrentHashMap<String, StoredApproval>()

    private val smsChallenges = ConcurrentHashMap<String, StoredSmsChallenge>()

    fun createUser(request: UserUpsertRequest): UserRecord {
        val realm = request.realm
        val username = request.username
        val key = usernameKey(realm, username)
        if (usernameIndex.containsKey(key)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Username already exists")
        }

        val emailKey = request.email?.let { usernameKey(realm, it.lowercase()) }
        if (emailKey != null && emailIndex.containsKey(emailKey)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email already exists")
        }

        val userId = UUID.randomUUID().toString()
        val attributes = request.attributes.orElse(null) ?: emptyMap()
        val stored = StoredUser(
            userId = userId,
            realm = realm,
            username = username,
            firstName = request.firstName,
            lastName = request.lastName,
            email = request.email?.lowercase(),
            enabled = request.enabled ?: true,
            emailVerified = request.emailVerified ?: false,
            attributes = attributes
        )

        users[userId] = stored
        usernameIndex[key] = userId
        emailKey?.let { emailIndex[it] = userId }

        return stored.toRecord()
    }

    fun getUser(userId: String): UserRecord? = users[userId]?.toRecord()

    fun updateUser(userId: String, request: UserUpsertRequest): UserRecord? {
        val existing = users[userId] ?: return null

        val realm = request.realm
        val username = request.username
        val email = request.email?.lowercase()

        if (existing.username != username) {
            val newKey = usernameKey(realm, username)
            if (usernameIndex.containsKey(newKey)) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Username already exists")
            }
            usernameIndex.remove(usernameKey(existing.realm, existing.username))
            usernameIndex[newKey] = userId
        }

        if (email != null && email != existing.email) {
            val newEmailKey = usernameKey(realm, email)
            if (emailIndex.containsKey(newEmailKey)) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Email already exists")
            }
            existing.email?.let { emailIndex.remove(usernameKey(existing.realm, it)) }
            emailIndex[newEmailKey] = userId
        }

        val attributes = request.attributes.orElse(null) ?: emptyMap()
        val updated = existing.copy(
            realm = realm,
            username = username,
            firstName = request.firstName,
            lastName = request.lastName,
            email = email,
            enabled = request.enabled ?: existing.enabled,
            emailVerified = request.emailVerified ?: existing.emailVerified,
            attributes = attributes
        )
        users[userId] = updated
        return updated.toRecord()
    }

    fun deleteUser(userId: String): Boolean {
        val removed = users.remove(userId) ?: return false
        usernameIndex.remove(usernameKey(removed.realm, removed.username))
        removed.email?.let { emailIndex.remove(usernameKey(removed.realm, it)) }
        return true
    }

    fun searchUsers(request: UserSearchRequest): List<UserRecord> {
        val stream = users.values.asSequence()
        val filtered = stream.filter { stored ->
            if (stored.realm != request.realm) return@filter false
            request.username?.let { if (!stored.username.equals(it, ignoreCase = true)) return@filter false }
            request.email?.let { if (stored.email?.equals(it, ignoreCase = true) != true) return@filter false }
            request.firstName?.let { if (stored.firstName != it) return@filter false }
            request.lastName?.let { if (stored.lastName != it) return@filter false }
            request.search?.let { search ->
                val haystack = listOfNotNull(stored.username, stored.firstName, stored.lastName, stored.email).joinToString(" ")
                if (!haystack.contains(search, ignoreCase = true)) return@filter false
            }
            val attributeFilters = request.attributes.orElse(null) ?: emptyMap()
            attributeFilters.forEach { (k, v) ->
                if (stored.attributes[k] != v) return@filter false
            }
            request.enabled?.let { if (stored.enabled != it) return@filter false }
            request.emailVerified?.let { if (stored.emailVerified != it) return@filter false }
            true
        }.drop(request.firstResult ?: 0).let {
            request.maxResults?.let { limit -> it.take(limit) } ?: it
        }
        return filtered.map { it.toRecord() }.toList()
    }

    fun precheck(request: EnrollmentPrecheckRequest): EnrollmentPrecheckResponse {
        val deviceId = request.deviceId
        val jkt = request.jkt

        devicesById[deviceId]?.let { stored ->
            return EnrollmentPrecheckResponse(EnrollmentPrecheckResponse.DecisionEnum.REJECT)
                .reason("device_already_bound")
                .boundUserId(stored.userId)
        }

        devicesByJkt[jkt]?.let { stored ->
            return EnrollmentPrecheckResponse(EnrollmentPrecheckResponse.DecisionEnum.REJECT)
                .reason("device_jkt_already_bound")
                .boundUserId(stored.userId)
        }

        return EnrollmentPrecheckResponse(EnrollmentPrecheckResponse.DecisionEnum.ALLOW)
    }

    fun bindDevice(request: EnrollmentBindRequest): EnrollmentBindResponse {
        val deviceId = request.deviceId
        val jkt = request.jkt
        val user = request.userId

        val existing = devicesById[deviceId]
        if (existing != null && existing.userId != user) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Device already bound to another user")
        }

        val stored = StoredDevice(
            deviceId = deviceId,
            jkt = jkt,
            userId = user,
            publicJwk = request.publicJwk ?: emptyMap(),
            status = DeviceRecord.StatusEnum.ACTIVE,
            createdAt = LocalDateTime.now(),
            lastSeenAt = LocalDateTime.now()
        )
        devicesById[deviceId] = stored
        devicesByJkt[jkt] = stored

        return EnrollmentBindResponse()
            .status(if (existing != null) EnrollmentBindResponse.StatusEnum.ALREADY_BOUND else EnrollmentBindResponse.StatusEnum.BOUND)
            .deviceRecordId(deviceId)
            .boundUserId(user)
    }

    fun listUserDevices(userId: String, includeDisabled: Boolean): List<DeviceRecord> {
        return devicesById.values.asSequence()
            .filter { it.userId == userId }
            .filter { includeDisabled || it.status == DeviceRecord.StatusEnum.ACTIVE }
            .map { it.toRecord() }
            .toList()
    }

    fun disableDevice(userId: String, deviceId: String): DeviceRecord? {
        val stored = devicesById[deviceId] ?: return null
        if (stored.userId != userId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found for user")
        }
        stored.status = DeviceRecord.StatusEnum.REVOKED
        return stored.toRecord()
    }

    fun lookupDevice(deviceId: String?, jkt: String?): DeviceLookupResponse {
        val stored = deviceId?.let { devicesById[it] } ?: jkt?.let { devicesByJkt[it] }
        return if (stored != null) {
            DeviceLookupResponse()
                .found(true)
                .userId(stored.userId)
                .device(stored.toRecord())
                .publicJwk(stored.publicJwk)
        } else {
            DeviceLookupResponse().found(false)
        }
    }

    fun sendSms(request: SmsSendRequest): SmsSendResponse {
        val hash = UUID.randomUUID().toString()
        val otp = (100000..999999).random().toString()
        smsChallenges[hash] = StoredSmsChallenge(hash, otp, OffsetDateTime.now().plusSeconds(300))
        return SmsSendResponse(hash)
            .ttlSeconds(300)
            .status("queued")
    }

    fun confirmSms(request: SmsConfirmRequest): SmsConfirmResponse {
        val stored = smsChallenges[request.hash]
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown hash")

        if (stored.expiresAt.isBefore(OffsetDateTime.now())) {
            smsChallenges.remove(request.hash)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP expired")
        }

        return if (stored.otp == request.otp) {
            smsChallenges.remove(request.hash)
            SmsConfirmResponse(true)
        } else {
            SmsConfirmResponse(false).reason("invalid_otp")
        }
    }

    fun createApproval(request: ApprovalCreateRequest): ApprovalCreateResponse {
        val requestId = "apr-${UUID.randomUUID()}"
        val created = StoredApproval(
            requestId = requestId,
            userId = request.userId,
            deviceId = request.newDevice.deviceId,
            status = ApprovalStatus.PENDING
        )
        approvals[requestId] = created
        return ApprovalCreateResponse()
            .requestId(requestId)
            .status(ApprovalCreateResponse.StatusEnum.PENDING)
    }

    fun getApproval(requestId: String): ApprovalStatusResponse? {
        val stored = approvals[requestId] ?: return null
        return ApprovalStatusResponse()
            .requestId(stored.requestId)
            .status(ApprovalStatusResponse.StatusEnum.valueOf(stored.status.name))
    }

    fun cancelApproval(requestId: String): Boolean {
        val stored = approvals[requestId] ?: return false
        stored.status = ApprovalStatus.EXPIRED
        return true
    }

    private fun usernameKey(realm: String, key: String) = "$realm|${key.lowercase()}"

    private data class StoredUser(
        val userId: String,
        val realm: String,
        val username: String,
        val firstName: String?,
        val lastName: String?,
        val email: String?,
        val enabled: Boolean,
        val emailVerified: Boolean,
        val attributes: Map<String, String>
    ) {
        fun toRecord(): UserRecord = UserRecord()
            .userId(userId)
            .realm(realm)
            .username(username)
            .firstName(firstName)
            .lastName(lastName)
            .email(email)
            .enabled(enabled)
            .emailVerified(emailVerified)
            .createdAt(LocalDateTime.now())
            .attributes(attributes)
    }

    private data class StoredDevice(
        val deviceId: String,
        val jkt: String,
        val publicJwk: Map<String, Any?>,
        val userId: String,
        var status: DeviceRecord.StatusEnum,
        val createdAt: LocalDateTime,
        var lastSeenAt: LocalDateTime,
        val label: String? = null
    ) {
        fun toRecord(): DeviceRecord = DeviceRecord()
            .deviceId(deviceId)
            .jkt(jkt)
            .status(status)
            .createdAt(createdAt)
            .lastSeenAt(lastSeenAt)
            .label(label)
    }

    private data class StoredSmsChallenge(
        val hash: String,
        val otp: String,
        val expiresAt: OffsetDateTime
    )

    private data class StoredApproval(
        val requestId: String,
        val userId: String,
        val deviceId: String,
        var status: ApprovalStatus,
        val createdAt: OffsetDateTime = OffsetDateTime.now()
    )

    private enum class ApprovalStatus {
        PENDING,
        APPROVED,
        DENIED,
        EXPIRED
    }
}
