package com.ssegning.keycloak.keybound.core.models

import okhttp3.OkHttpClient
import org.keycloak.models.KeycloakContext

class HttpConfig(
    val baseUrl: String,
    val client: OkHttpClient,
    val telemetryEnabled: Boolean,
    val actor: String,
    val signatureSecret: String?,
    val signatureVersion: String
) {
    companion object {
        const val HTTP_BASE_PATH_KEY = "BACKEND_HTTP_BASE_PATH"
        const val HTTP_TELEMETRY_ENABLED_KEY = "BACKEND_HTTP_TELEMETRY_ENABLED"
        const val HTTP_ACTOR_KEY = "BACKEND_HTTP_ACTOR"
        const val HTTP_SIGNATURE_SECRET_KEY = "BACKEND_HTTP_SIGNATURE_SECRET"
        const val HTTP_SIGNATURE_VERSION_KEY = "BACKEND_HTTP_SIGNATURE_VERSION"

        private fun readScopedValue(baseName: String, realmName: String?): String? {
            val scopedName = realmName?.takeIf { it.isNotBlank() }?.let { "${baseName}_$it" }

            return when {
                scopedName != null && !System.getenv(scopedName).isNullOrBlank() -> System.getenv(scopedName)
                !System.getenv(baseName).isNullOrBlank() -> System.getenv(baseName)
                scopedName != null && !System.getProperty(scopedName).isNullOrBlank() -> System.getProperty(scopedName)
                !System.getProperty(baseName).isNullOrBlank() -> System.getProperty(baseName)
                else -> null
            }
        }

        fun fromEnv(context: KeycloakContext): HttpConfig {
            val realmName = context.realm?.name
            val baseUrl = readScopedValue(HTTP_BASE_PATH_KEY, realmName)
                ?: throw IllegalStateException(
                    "Missing backend base URL. Set ${HTTP_BASE_PATH_KEY}_${realmName ?: "<realm>"} or $HTTP_BASE_PATH_KEY."
                )
            val telemetryEnabled = readScopedValue(HTTP_TELEMETRY_ENABLED_KEY, realmName)?.toBooleanStrictOrNull() ?: true
            val actor = readScopedValue(HTTP_ACTOR_KEY, realmName) ?: "keycloak"
            val signatureSecret = readScopedValue(HTTP_SIGNATURE_SECRET_KEY, realmName)
            val signatureVersion = readScopedValue(HTTP_SIGNATURE_VERSION_KEY, realmName) ?: "v1"

            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .build()

            return HttpConfig(
                baseUrl = baseUrl,
                client = client,
                telemetryEnabled = telemetryEnabled,
                actor = actor,
                signatureSecret = signatureSecret,
                signatureVersion = signatureVersion
            )
        }
    }
}
