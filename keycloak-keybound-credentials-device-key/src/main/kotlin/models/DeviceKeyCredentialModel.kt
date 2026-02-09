package com.ssegning.keycloak.keybound.models

import org.keycloak.credential.CredentialModel

class DeviceKeyCredentialModel : CredentialModel() {
    companion object {
        const val TYPE = "device_key"
    }
}