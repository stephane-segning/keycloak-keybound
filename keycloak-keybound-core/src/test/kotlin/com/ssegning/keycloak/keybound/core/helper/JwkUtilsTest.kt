package com.ssegning.keycloak.keybound.core.helper

import kotlin.test.Test
import kotlin.test.assertTrue

class JwkUtilsTest {
    @Test
    fun `computeJkt returns deterministic thumbprint`() {
        val jwk = """{"kty":"EC","crv":"P-256","x":"x","y":"y"}"""
        val thumbprint = computeJkt(jwk)
        assertTrue(thumbprint.isNotBlank())
    }
}
