package com.ssegning.keycloak.keybound.credentials

import com.ssegning.keycloak.keybound.core.models.DeviceCredentialData
import com.ssegning.keycloak.keybound.core.models.DeviceKeyCredentialModel
import com.ssegning.keycloak.keybound.core.models.DeviceSecretData
import com.ssegning.keycloak.keybound.core.models.DeviceStatus
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.common.util.Time
import org.keycloak.credential.*
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import org.keycloak.storage.StorageId
import org.keycloak.util.JsonSerialization

class DeviceKeyCredential(
    val session: KeycloakSession,
    private val apiGateway: ApiGateway,
) : CredentialProvider<DeviceKeyCredentialModel>,
    CredentialInputValidator {
    override fun getType() = DeviceKeyCredentialModel.TYPE

    override fun createCredential(
        realm: RealmModel,
        user: UserModel,
        credentialModel: DeviceKeyCredentialModel,
    ): CredentialModel = throw UnsupportedOperationException("Device credentials are persisted by backend APIs only.")

    override fun deleteCredential(
        realm: RealmModel,
        user: UserModel,
        credentialId: String,
    ): Boolean = disableDevice(user, credentialId)

    override fun getCredentialFromModel(model: CredentialModel): DeviceKeyCredentialModel =
        DeviceKeyCredentialModel().apply {
            id = model.id
            type = model.type
            userLabel = model.userLabel
            createdDate = model.createdDate
            secretData = model.secretData
            credentialData = model.credentialData
        }

    override fun getCredentialTypeMetadata(metadataContext: CredentialTypeMetadataContext): CredentialTypeMetadata =
        CredentialTypeMetadata
            .builder()
            .type(type)
            .category(CredentialTypeMetadata.Category.TWO_FACTOR)
            .displayName("Device Key")
            .helpText("A key bound to a specific device.")
            .removeable(true)
            .build(session)

    override fun supportsCredentialType(credentialType: String?): Boolean = getType() == credentialType

    override fun isConfiguredFor(
        realm: RealmModel?,
        user: UserModel?,
        credentialType: String?,
    ): Boolean {
        if (!supportsCredentialType(credentialType)) return false
        val backendUserId = user?.let { resolveBackendUserId(it) } ?: return false
        val devices = apiGateway.listUserDevices(backendUserId, includeDisabled = false) ?: return false
        return devices.any { it.status == DeviceStatus.ACTIVE }
    }

    override fun isValid(
        realm: RealmModel?,
        user: UserModel?,
        credentialInput: CredentialInput?,
    ): Boolean = false

    fun getByDeviceId(
        user: UserModel,
        deviceId: String,
    ): DeviceKeyCredentialModel? {
        val lookup = apiGateway.lookupDevice(deviceId = deviceId) ?: return null
        val backendUserId = resolveBackendUserId(user)
        if (!lookup.found || lookup.userId != backendUserId) return null
        val device = lookup.device ?: return null
        val publicJwk = lookup.publicJwk ?: return null

        val createdAtMillis = device.createdAt?.toEpochMilliseconds() ?: Time.currentTimeMillis()

        val credentialData =
            DeviceCredentialData(
                deviceId = device.deviceId,
                deviceOs = "Unknown", // TODO should come from API
                deviceModel = device.label ?: "Unknown",
                createdAt = createdAtMillis,
            )

        val secretData =
            DeviceSecretData(
                publicKey = JsonSerialization.writeValueAsString(publicJwk),
                jkt = device.jkt,
            )

        return DeviceKeyCredentialModel().apply {
            id = device.deviceId
            type = DeviceKeyCredentialModel.TYPE
            userLabel = device.label ?: device.deviceId
            createdDate = createdAtMillis
            this.credentialData = JsonSerialization.writeValueAsString(credentialData)
            this.secretData = JsonSerialization.writeValueAsString(secretData)
        }
    }

    fun getByJkt(
        user: UserModel,
        jkt: String,
    ): DeviceKeyCredentialModel? {
        val lookup = apiGateway.lookupDevice(jkt = jkt) ?: return null
        val deviceId = lookup.device?.deviceId ?: return null
        return getByDeviceId(user, deviceId)
    }

    fun isDeviceEnrolled(
        user: UserModel,
        deviceId: String,
    ): Boolean = getByDeviceId(user, deviceId) != null

    fun disableDevice(
        user: UserModel,
        deviceId: String,
    ): Boolean = apiGateway.disableDevice(resolveBackendUserId(user), deviceId)

    private fun resolveBackendUserId(user: UserModel): String {
        val backendAttributeId = user.getFirstAttribute("backend_user_id")?.trim()
        if (!backendAttributeId.isNullOrBlank()) {
            return backendAttributeId
        }
        return StorageId.externalId(user.id) ?: user.id
    }
}
