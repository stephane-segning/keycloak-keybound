package com.ssegning.keycloak.keybound.credentials

import com.ssegning.keycloak.keybound.models.DeviceKeyCredentialModel
import com.ssegning.keycloak.keybound.spi.ApiGateway
import org.keycloak.credential.CredentialModel
import org.keycloak.credential.CredentialProvider
import org.keycloak.credential.CredentialTypeMetadata
import org.keycloak.credential.CredentialTypeMetadataContext
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel

class DeviceKeyCredential(val apiGateway: ApiGateway) : CredentialProvider<DeviceKeyCredentialModel> {
    override fun getType() = DeviceKeyCredentialModel.TYPE

    override fun createCredential(
        realm: RealmModel,
        user: UserModel,
        credentialModel: DeviceKeyCredentialModel
    ): CredentialModel {
        TODO("Not yet implemented")
    }

    override fun deleteCredential(
        realm: RealmModel,
        user: UserModel,
        credentialId: String
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun getCredentialFromModel(model: CredentialModel): DeviceKeyCredentialModel {
        TODO("Not yet implemented")
    }

    override fun getCredentialTypeMetadata(metadataContext: CredentialTypeMetadataContext): CredentialTypeMetadata {
        TODO("Not yet implemented")
    }
}