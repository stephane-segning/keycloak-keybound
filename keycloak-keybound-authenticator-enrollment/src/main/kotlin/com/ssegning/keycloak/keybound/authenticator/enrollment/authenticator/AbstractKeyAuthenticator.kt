package com.ssegning.keycloak.keybound.authenticator.enrollment.authenticator

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticator

abstract class AbstractKeyAuthenticator : AbstractAuthenticator() {
    companion object {
        const val DEVICE_ID_NOTE_NAME = "device.id"
        const val DEVICE_PUBLIC_KEY_NOTE_NAME = "device.public_key"
        const val DEVICE_TS_NOTE_NAME = "device.ts"
        const val DEVICE_NONCE_NOTE_NAME = "device.nonce"
        const val DEVICE_SIG_NOTE_NAME = "device.sig"
        const val DEVICE_ACTION_NOTE_NAME = "device.action"
        const val DEVICE_AUD_NOTE_NAME = "device.aud"
        const val USER_HINT_NOTE_NAME = "device.user_hint"
    }
}
