package com.ssegning.keycloak.keybound.models

import okhttp3.OkHttpClient
import org.keycloak.models.KeycloakContext

class HttpConfig(val baseUrl: String, val client: OkHttpClient) {
    companion object {
        const val HTTP_BASE_PATH_KEY = "BACKEND_HTTP_BASE_PATH"
        const val HTTP_AUTH_USERNAME_KEY = "BACKEND_HTTP_AUTH_USERNAME"
        const val HTTP_AUTH_PASSWORD_KEY = "BACKEND_HTTP_AUTH_PASSWORD"

        fun fromEnv(context: KeycloakContext): HttpConfig {
            val baseUrl = "${HTTP_BASE_PATH_KEY}_${context.realm.name}"

            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .build()

            return HttpConfig(
                baseUrl, client
            )
        }
    }
}