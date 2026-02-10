package com.ssegning.keycloak.keybound.core.models

import org.keycloak.models.KeycloakContext
import java.util.concurrent.ConcurrentHashMap

class HttpConfig(
    private val defaultBaseUrl: String,
    val telemetryEnabled: Boolean,
    val actor: String,
    val signatureSecret: String?,
    val signatureVersion: String
) {
    // Cache resolved per-realm base URLs to avoid repeated env/property lookups.
    private val realmBaseUrls = ConcurrentHashMap<String, String>()

    // Provider creation may happen with realm == null; in that case we use the startup default.
    // Once a realm is available for a request, resolve (and cache) a realm-specific URL if present.
    fun baseUrlForRealm(realmName: String?): String {
        if (realmName.isNullOrBlank()) {
            return defaultBaseUrl
        }

        return realmBaseUrls.computeIfAbsent(realmName) {
            readScopedValue(HTTP_BASE_PATH_KEY, realmName)
                ?: readScopedValue(HTTP_BASE_PATH_KEY, null)
                ?: throw IllegalStateException(
                    "Missing backend base URL for realm '$realmName'. Set ${HTTP_BASE_PATH_KEY}_$realmName or $HTTP_BASE_PATH_KEY."
                )
        }
    }

    val baseUrl: String
        get() = defaultBaseUrl

    companion object {
        const val HTTP_BASE_PATH_KEY = "BACKEND_HTTP_BASE_PATH"
        const val HTTP_TELEMETRY_ENABLED_KEY = "BACKEND_HTTP_TELEMETRY_ENABLED"
        const val HTTP_ACTOR_KEY = "BACKEND_HTTP_ACTOR"
        const val HTTP_SIGNATURE_SECRET_KEY = "BACKEND_HTTP_SIGNATURE_SECRET"
        const val HTTP_SIGNATURE_VERSION_KEY = "BACKEND_HTTP_SIGNATURE_VERSION"
        const val DEFAULT_BACKEND_BASE_URL = "https://backend.example.com"

        // Lookup order: realm-scoped env -> global env -> realm-scoped JVM prop -> global JVM prop.
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

        // Build a startup-safe config:
        // - prefers explicit scoped/global values
        // - falls back to any discovered scoped value
        // - only then uses a neutral placeholder to keep provider bootstrap alive
        fun fromEnv(context: KeycloakContext): HttpConfig {
            val realmName = context.realm?.name
            val baseUrl = readScopedValue(HTTP_BASE_PATH_KEY, realmName)
                ?: readScopedValue(HTTP_BASE_PATH_KEY, null)
                ?: discoverAnyScopedBaseUrl()
                ?: DEFAULT_BACKEND_BASE_URL
            val telemetryEnabled = readScopedValue(HTTP_TELEMETRY_ENABLED_KEY, realmName)?.toBooleanStrictOrNull() ?: true
            val actor = readScopedValue(HTTP_ACTOR_KEY, realmName) ?: "keycloak"
            val signatureSecret = readScopedValue(HTTP_SIGNATURE_SECRET_KEY, realmName)
            val signatureVersion = readScopedValue(HTTP_SIGNATURE_VERSION_KEY, realmName) ?: "v1"

            return HttpConfig(
                defaultBaseUrl = baseUrl,
                telemetryEnabled = telemetryEnabled,
                actor = actor,
                signatureSecret = signatureSecret,
                signatureVersion = signatureVersion
            )
        }

        // Best-effort discovery used only during bootstrap when realm is not available yet.
        private fun discoverAnyScopedBaseUrl(): String? {
            val envScoped = System.getenv().entries
                .firstOrNull { (key, value) ->
                    key.startsWith("${HTTP_BASE_PATH_KEY}_") && value.isNotBlank()
                }?.value

            if (!envScoped.isNullOrBlank()) {
                return envScoped
            }

            return System.getProperties().entries
                .firstOrNull { (key, value) ->
                    key.toString().startsWith("${HTTP_BASE_PATH_KEY}_") && value.toString().isNotBlank()
                }?.value?.toString()
        }
    }
}
