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
import org.keycloak.util.JsonSerialization

class DeviceKeyCredential(
    val session: KeycloakSession,
    private val apiGateway: ApiGateway
) : CredentialProvider<DeviceKeyCredentialModel>,
    CredentialInputValidator {
    override fun getType() = DeviceKeyCredentialModel.TYPE

    override fun createCredential(
        realm: RealmModel,
        user: UserModel,
        credentialModel: DeviceKeyCredentialModel
    ): CredentialModel {
        throw UnsupportedOperationException("Device credentials are persisted by backend APIs only.")
    }

    override fun deleteCredential(
        realm: RealmModel,
        user: UserModel,
        credentialId: String
    ): Boolean {
        return disableDevice(user, credentialId)
    }

    override fun getCredentialFromModel(model: CredentialModel): DeviceKeyCredentialModel {
        return DeviceKeyCredentialModel().apply {
            id = model.id
            type = model.type
            userLabel = model.userLabel
            createdDate = model.createdDate
            secretData = model.secretData
            credentialData = model.credentialData
        }
    }

    override fun getCredentialTypeMetadata(metadataContext: CredentialTypeMetadataContext): CredentialTypeMetadata {
        return CredentialTypeMetadata.builder()
            .type(type)
            .category(CredentialTypeMetadata.Category.TWO_FACTOR)
            .displayName("Device Key")
            .helpText("A key bound to a specific device.")
            .removeable(true)
            .build(session)
    }

    override fun supportsCredentialType(credentialType: String?): Boolean {
        return getType() == credentialType
    }

    override fun isConfiguredFor(realm: RealmModel?, user: UserModel?, credentialType: String?): Boolean {
        if (!supportsCredentialType(credentialType)) return false
        val userId = user?.id ?: return false
        val devices = apiGateway.listUserDevices(userId, includeDisabled = false) ?: return false
        return devices.any { it.status == DeviceStatus.ACTIVE }
    }

    override fun isValid(realm: RealmModel?, user: UserModel?, credentialInput: CredentialInput?): Boolean {
        // This method is primarily used for verifying the credential during authentication.
        // For the Device Key credential, the verification logic (signature check)
        // is typically handled within the Authenticator or a custom Grant Type,
        // as it involves checking a signed challenge against the public key.
        // However, if we have a custom CredentialInput that carries the signature,
        // we could implement the check here.
        // For now, we'll return false as the standard flow doesn't pass the signature here directly
        // in a way that fits the standard CredentialInput interface easily without a custom implementation.
        // The actual verification will happen in the Authenticator/GrantType which will then
        // likely not call this method directly, or we will implement a custom CredentialInput later.

        // TODO: Implement signature verification if a suitable CredentialInput is defined.
        return false
    }

    fun getByDeviceId(user: UserModel, deviceId: String): DeviceKeyCredentialModel? {
        val lookup = apiGateway.lookupDevice(deviceId = deviceId) ?: return null
        if (!lookup.found || lookup.userId != user.id) return null
        val device = lookup.device ?: return null
        val publicJwk = lookup.publicJwk ?: return null

        val createdAtMillis = device.createdAt?.toInstant()?.toEpochMilli() ?: Time.currentTimeMillis()

        val credentialData = DeviceCredentialData(
            deviceId = device.deviceId,
            deviceOs = "Unknown",
            deviceModel = device.label ?: "Unknown",
            createdAt = createdAtMillis
        )
        val secretData = DeviceSecretData(
            publicKey = JsonSerialization.writeValueAsString(publicJwk),
            jkt = device.jkt
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

    fun getByJkt(user: UserModel, jkt: String): DeviceKeyCredentialModel? {
        val lookup = apiGateway.lookupDevice(jkt = jkt) ?: return null
        val deviceId = lookup.device?.deviceId ?: return null
        return getByDeviceId(user, deviceId)
    }

    fun isDeviceEnrolled(user: UserModel, deviceId: String): Boolean {
        return getByDeviceId(user, deviceId) != null
    }

    fun disableDevice(user: UserModel, deviceId: String): Boolean {
        return apiGateway.disableDevice(user.id, deviceId)
    }
}
