package com.ssegning.keycloak.keybound.core.models

import kotlin.test.Test
import kotlin.test.assertEquals

class PublicKeyLoginSignaturePayloadTest {
    @Test
    fun `serializes canonical json in deterministic field order`() {
        val payload =
            PublicKeyLoginSignaturePayload(
                nonce = "nce_123",
                deviceId = "dvc_456",
                username = "alice",
                ts = "1739782800",
                publicKey = """{"kty":"EC","crv":"P-256","x":"x","y":"y"}""",
            )

        assertEquals(
            """{"nonce":"nce_123","deviceId":"dvc_456","username":"alice","ts":"1739782800","publicKey":"{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"x\",\"y\":\"y\"}"}""",
            payload.toCanonicalJson(),
        )
    }
}
