package com.ssegning.keycloak.keybound.examples.backend.store

import com.ssegning.keycloak.keybound.examples.backend.model.*
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Service
class BackendDataStore {
    private val users = ConcurrentHashMap<String, StoredUser>()
    private val usernameIndex = ConcurrentHashMap<String, String>()
    private val emailIndex = ConcurrentHashMap<String, String>()

    private val devicesById = ConcurrentHashMap<String, StoredDevice>()
    private val devicesByJkt = ConcurrentHashMap<String, StoredDevice>()

    private val approvals = ConcurrentHashMap<String, StoredApproval>()

    private val smsChallenges = ConcurrentHashMap<String, StoredSmsChallenge>()
    private val userIdCounter = AtomicLong()
    private val secureRandom = SecureRandom()

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

        val userId = nextUserId()
        val attributes = canonicalizeAttributes(request.attributes.orElse(null) ?: emptyMap())
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

        val attributes = canonicalizeAttributes(request.attributes.orElse(null) ?: emptyMap())
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
                val haystack =
                    listOfNotNull(stored.username, stored.firstName, stored.lastName, stored.email).joinToString(" ")
                if (!haystack.contains(search, ignoreCase = true)) return@filter false
            }
            val attributeFilters = canonicalizeAttributes(request.attributes.orElse(null) ?: emptyMap())
            attributeFilters.forEach { (k, v) ->
                val storedValue = stored.attributes[k]
                    ?: if (k == "phone_e164") stored.attributes["phone_number"] else null
                if (storedValue != v) return@filter false
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
        val now = LocalDateTime.now()
        val attributes = request.attributes.orElse(null) ?: emptyMap()
        val deviceOs = attributes["device_os"]?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "device_os is required")
        val deviceModel = attributes["device_model"]?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "unknown-model"
        val label = "$deviceOs / $deviceModel"

        val existingByDeviceId = devicesById[deviceId]
        if (existingByDeviceId != null) {
            if (existingByDeviceId.userId != user) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Device already bound to another user")
            }
            if (existingByDeviceId.jkt != jkt) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Device ID already bound with a different key")
            }
            existingByDeviceId.lastSeenAt = now
            existingByDeviceId.deviceOs = deviceOs
            existingByDeviceId.deviceModel = deviceModel
            existingByDeviceId.label = label
            return EnrollmentBindResponse()
                .status(EnrollmentBindResponse.StatusEnum.ALREADY_BOUND)
                .deviceRecordId(existingByDeviceId.recordId)
                .boundUserId(user)
        }

        val existingByJkt = devicesByJkt[jkt]
        if (existingByJkt != null) {
            if (existingByJkt.userId != user || existingByJkt.deviceId != deviceId) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Device key already bound")
            }
            existingByJkt.lastSeenAt = now
            existingByJkt.deviceOs = deviceOs
            existingByJkt.deviceModel = deviceModel
            existingByJkt.label = label
            return EnrollmentBindResponse()
                .status(EnrollmentBindResponse.StatusEnum.ALREADY_BOUND)
                .deviceRecordId(existingByJkt.recordId)
                .boundUserId(user)
        }

        val stored = StoredDevice(
            recordId = nextPrefixedId("dvc"),
            deviceId = deviceId,
            jkt = jkt,
            userId = user,
            publicJwk = request.publicJwk ?: emptyMap(),
            status = DeviceRecord.StatusEnum.ACTIVE,
            createdAt = now,
            lastSeenAt = now,
            deviceOs = deviceOs,
            deviceModel = deviceModel,
            label = label
        )
        devicesById[deviceId] = stored
        devicesByJkt[jkt] = stored

        return EnrollmentBindResponse()
            .status(EnrollmentBindResponse.StatusEnum.BOUND)
            .deviceRecordId(stored.recordId)
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
            stored.lastSeenAt = LocalDateTime.now()
            DeviceLookupResponse()
                .found(true)
                .userId(stored.userId)
                .device(stored.toRecord())
                .publicJwk(stored.publicJwk)
        } else {
            DeviceLookupResponse().found(false)
        }
    }

    fun snapshot(): BackendStoreSnapshot {
        return BackendStoreSnapshot(
            users = users.values.map(StoredUser::toRecord),
            usernameIndex = usernameIndex.entries.map { KeyValueSnapshot(it.key, it.value) },
            emailIndex = emailIndex.entries.map { KeyValueSnapshot(it.key, it.value) },
            devices = devicesById.values.map {
                DeviceSnapshot(
                    recordId = it.recordId,
                    deviceId = it.deviceId,
                    userId = it.userId,
                    status = it.status.name,
                    jkt = it.jkt,
                    deviceOs = it.deviceOs,
                    deviceModel = it.deviceModel
                )
            },
            devicesByJkt = devicesByJkt.entries.map {
                DeviceJktSnapshot(
                    jkt = it.key,
                    recordId = it.value.recordId,
                    deviceId = it.value.deviceId,
                    userId = it.value.userId
                )
            },
            approvals = approvals.values.map(StoredApproval::toSnapshot),
            smsChallenges = smsChallenges.values.map {
                SmsChallengeSnapshot(
                    hash = it.hash,
                    otp = it.otp,
                    expiresAt = it.expiresAt.toString()
                )
            }
        )
    }

    fun sendSms(request: SmsSendRequest): SmsSendResponse {
        val hash = nextPrefixedId("sms")
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

    fun resolveUserByPhone(request: PhoneResolveRequest): PhoneResolveResponse {
        val normalizedPhone = request.phoneNumber.trim()
        val matchedUser = findSingleUserByPhone(request.realm, normalizedPhone)
        val hasActiveDevices = matchedUser?.let { hasActiveDeviceCredentials(it.userId) } == true
        val enrollmentPath = if (matchedUser != null && hasActiveDevices) EnrollmentPath.APPROVAL else EnrollmentPath.OTP

        return PhoneResolveResponse()
            .phoneNumber(normalizedPhone)
            .userExists(matchedUser != null)
            .hasDeviceCredentials(hasActiveDevices)
            .enrollmentPath(enrollmentPath)
            .userId(matchedUser?.userId)
            .username(matchedUser?.username)
    }

    fun resolveOrCreateUserByPhone(request: PhoneResolveOrCreateRequest): PhoneResolveOrCreateResponse {
        val normalizedPhone = request.phoneNumber.trim()
        val existingUser = findSingleUserByPhone(request.realm, normalizedPhone)
        if (existingUser != null) {
            return PhoneResolveOrCreateResponse()
                .phoneNumber(normalizedPhone)
                .userId(existingUser.userId)
                .username(existingUser.username)
                .created(false)
        }

        val createdUser = createUser(
            UserUpsertRequest()
                .realm(request.realm)
                .username(normalizedPhone)
                .enabled(true)
                .attributes(mapOf("phone_e164" to normalizedPhone))
        )

        return PhoneResolveOrCreateResponse()
            .phoneNumber(normalizedPhone)
            .userId(createdUser.userId)
            .username(createdUser.username)
            .created(true)
    }

    fun createApproval(request: ApprovalCreateRequest): ApprovalCreateResponse {
        val requestId = nextPrefixedId("apr")
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
        return stored.toApprovalStatusResponse()
    }

    fun listUserApprovals(
        userId: String,
        statuses: Collection<String>? = null
    ): UserApprovalsResponse {
        if (!users.containsKey(userId) && approvals.values.none { it.userId == userId }) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }

        val statusFilter = statuses
            ?.mapNotNull { raw -> ApprovalStatus.entries.firstOrNull { it.name == raw } }
            ?.toSet()
            ?: emptySet()

        val records = approvals.values.asSequence()
            .filter { it.userId == userId }
            .filter { statusFilter.isEmpty() || it.status in statusFilter }
            .sortedByDescending { it.createdAt }
            .map { it.toUserApprovalRecord() }
            .toList()

        return UserApprovalsResponse()
            .userId(userId)
            .approvals(records)
    }

    fun decideApproval(requestId: String, request: ApprovalDecisionRequest): ApprovalStatusResponse? {
        val stored = approvals[requestId] ?: return null
        if (stored.status != ApprovalStatus.PENDING) {
            return stored.toApprovalStatusResponse()
        }

        stored.status = when (request.decision) {
            ApprovalDecisionRequest.DecisionEnum.APPROVE -> ApprovalStatus.APPROVED
            ApprovalDecisionRequest.DecisionEnum.DENY -> ApprovalStatus.DENIED
        }
        stored.decidedAt = OffsetDateTime.now()
        stored.decidedByDeviceId = request.decidedByDeviceId
        stored.message = request.message

        return stored.toApprovalStatusResponse()
    }

    fun cancelApproval(requestId: String): Boolean {
        val stored = approvals[requestId] ?: return false
        stored.status = ApprovalStatus.EXPIRED
        stored.decidedAt = OffsetDateTime.now()
        stored.message = "cancelled"
        return true
    }

    private fun usernameKey(realm: String, key: String) = "$realm|${key.lowercase()}"

    private fun canonicalizeAttributes(attributes: Map<String, String>): Map<String, String> {
        if (attributes.isEmpty()) return emptyMap()
        return attributes.entries.associate { (key, value) ->
            canonicalAttributeKey(key) to value
        }
    }

    private fun canonicalAttributeKey(attributeKey: String): String = when (attributeKey.trim()) {
        "phone_number", "phone" -> "phone_e164"
        else -> attributeKey
    }

    private fun hasActiveDeviceCredentials(userId: String): Boolean =
        devicesById.values.any { it.userId == userId && it.status == DeviceRecord.StatusEnum.ACTIVE }

    private fun findSingleUserByPhone(realm: String, phoneE164: String): StoredUser? {
        val matches = users.values
            .asSequence()
            .filter { it.realm == realm }
            .filter { candidate ->
                candidate.attributes["phone_e164"] == phoneE164 ||
                    candidate.attributes["phone_number"] == phoneE164 ||
                candidate.username.equals(phoneE164, ignoreCase = true)
            }
            .toList()

        if (matches.size > 1) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Multiple users matched phone number")
        }

        return matches.firstOrNull()
    }

    private fun nextUserId(): String = nextPrefixedId("usr")

    private fun nextPrefixedId(prefix: String): String = "${prefix}_${nextCuidLikeId()}"

    private fun nextCuidLikeId(): String {
        val timestamp = java.lang.Long.toString(System.currentTimeMillis(), 36)
        val counter = java.lang.Long.toString(userIdCounter.getAndIncrement() and 0xFFFFF, 36).padStart(4, '0')
        val randomPart = buildString(12) {
            repeat(12) {
                append(BASE36[secureRandom.nextInt(BASE36.length)])
            }
        }
        return "knd2$timestamp$counter$randomPart"
    }

    companion object {
        private const val BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz"
    }

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
        val recordId: String,
        val deviceId: String,
        val jkt: String,
        var publicJwk: Map<String, Any?>,
        val userId: String,
        var status: DeviceRecord.StatusEnum,
        val createdAt: LocalDateTime,
        var lastSeenAt: LocalDateTime,
        var deviceOs: String,
        var deviceModel: String,
        var label: String? = null
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
        val createdAt: OffsetDateTime = OffsetDateTime.now(),
        var decidedAt: OffsetDateTime? = null,
        var decidedByDeviceId: String? = null,
        var message: String? = null
    ) {
        fun toSnapshot(): ApprovalSnapshot = ApprovalSnapshot(
            requestId = requestId,
            userId = userId,
            deviceId = deviceId,
            status = status.name
        )

        fun toUserApprovalRecord(): UserApprovalRecord = UserApprovalRecord()
            .requestId(requestId)
            .userId(userId)
            .deviceId(deviceId)
            .status(UserApprovalRecord.StatusEnum.valueOf(status.name))
            .createdAt(createdAt.toLocalDateTime())
            .decidedAt(decidedAt?.toLocalDateTime())
            .decidedByDeviceId(decidedByDeviceId)
            .message(message)

        fun toApprovalStatusResponse(): ApprovalStatusResponse = ApprovalStatusResponse()
            .requestId(requestId)
            .status(ApprovalStatusResponse.StatusEnum.valueOf(status.name))
            .decidedAt(decidedAt?.toLocalDateTime())
            .decidedByDeviceId(decidedByDeviceId)
            .message(message)
    }

    private enum class ApprovalStatus {
        PENDING,
        APPROVED,
        DENIED,
        EXPIRED
    }
}

data class BackendStoreSnapshot(
    val users: List<UserRecord>,
    val usernameIndex: List<KeyValueSnapshot>,
    val emailIndex: List<KeyValueSnapshot>,
    val devices: List<DeviceSnapshot>,
    val devicesByJkt: List<DeviceJktSnapshot>,
    val approvals: List<ApprovalSnapshot>,
    val smsChallenges: List<SmsChallengeSnapshot>
)

data class KeyValueSnapshot(
    val key: String,
    val value: String
)

data class DeviceSnapshot(
    val recordId: String,
    val deviceId: String,
    val userId: String,
    val status: String,
    val jkt: String,
    val deviceOs: String,
    val deviceModel: String
)

data class ApprovalSnapshot(
    val requestId: String,
    val userId: String,
    val deviceId: String,
    val status: String
)

data class DeviceJktSnapshot(
    val jkt: String,
    val recordId: String,
    val deviceId: String,
    val userId: String
)

data class SmsChallengeSnapshot(
    val hash: String,
    val otp: String,
    val expiresAt: String
)
