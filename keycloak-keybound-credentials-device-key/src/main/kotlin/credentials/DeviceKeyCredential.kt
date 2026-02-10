package com.ssegning.keycloak.keybound.credentials

import com.ssegning.keycloak.keybound.core.models.DeviceCredentialData
import com.ssegning.keycloak.keybound.core.models.DeviceKeyCredentialModel
import com.ssegning.keycloak.keybound.core.models.DeviceSecretData
import org.keycloak.common.util.Time
import org.keycloak.credential.*
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import org.keycloak.util.JsonSerialization

class DeviceKeyCredential(val session: KeycloakSession) : CredentialProvider<DeviceKeyCredentialModel>,
    CredentialInputValidator {
    override fun getType() = DeviceKeyCredentialModel.TYPE

    override fun createCredential(
        realm: RealmModel,
        user: UserModel,
        credentialModel: DeviceKeyCredentialModel
    ): CredentialModel {
        if (credentialModel.createdDate == null) {
            credentialModel.createdDate = Time.currentTimeMillis()
        }
        return user.credentialManager().createStoredCredential(credentialModel)
    }

    override fun deleteCredential(
        realm: RealmModel,
        user: UserModel,
        credentialId: String
    ): Boolean {
        return user.credentialManager().removeStoredCredentialById(credentialId)
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
            .createAction(DeviceKeyCredentialModel.TYPE)
            .removeable(true)
            .build(session)
    }

    override fun supportsCredentialType(credentialType: String?): Boolean {
        return getType() == credentialType
    }

    override fun isConfiguredFor(realm: RealmModel?, user: UserModel?, credentialType: String?): Boolean {
        if (!supportsCredentialType(credentialType)) return false
        return user?.credentialManager()?.getStoredCredentialsByTypeStream(credentialType)?.findAny()?.isPresent
            ?: false
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
        return user.credentialManager().getStoredCredentialsByTypeStream(type)
            .map { getCredentialFromModel(it) }
            .filter {
                val data = JsonSerialization.readValue(it.credentialData, DeviceCredentialData::class.java)
                data.deviceId == deviceId
            }
            .findFirst()
            .orElse(null)
    }

    fun getByJkt(user: UserModel, jkt: String): DeviceKeyCredentialModel? {
        return user.credentialManager().getStoredCredentialsByTypeStream(type)
            .map { getCredentialFromModel(it) }
            .filter {
                val data = JsonSerialization.readValue(it.secretData, DeviceSecretData::class.java)
                data.jkt == jkt
            }
            .findFirst()
            .orElse(null)
    }

    fun isDeviceEnrolled(user: UserModel, deviceId: String): Boolean {
        return getByDeviceId(user, deviceId) != null
    }

    fun disableDevice(user: UserModel, deviceId: String): Boolean {
        val credential = getByDeviceId(user, deviceId) ?: return false
        return user.credentialManager().removeStoredCredentialById(credential.id)
    }
}
