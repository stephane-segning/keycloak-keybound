package com.ssegning.keycloak.keybound.api

import com.ssegning.keycloak.keybound.core.models.HttpConfig
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.keycloak.models.KeycloakSession
import java.security.SecureRandom
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SimpleCallFactory(
    private val session: KeycloakSession,
    private val config: HttpConfig = HttpConfig.fromEnv(session.context),
) : Call.Factory {
    val baseUrl: String = config.baseUrl

    // Keep the generated client's base-path prefix so we can replace only host/base-path per request.
    private val baseUrlPath = baseUrl.toHttpUrlOrNull()?.encodedPath?.trimEnd('/') ?: ""

    // Build request-time metadata (realm/client/trace headers), rewrite URL to realm target,
    // optionally sign, then execute via the shared pooled client.
    override fun newCall(request: Request): Call {
        val method = request.method.uppercase()
        val timestamp = Instant.now().epochSecond.toString()
        val requestId = UUID.randomUUID().toString()
        val realmName = session.context.realm?.name
        val clientId = session.context.client?.clientId
        val targetBaseUrl = config.baseUrlForRealm(realmName)
        val rewrittenUrl = rewriteUrl(request, targetBaseUrl)

        val builder =
            request
                .newBuilder()
                .url(rewrittenUrl)
                .header("X-KC-Request-Id", requestId)
                .header("X-KC-Actor", config.actor)
                .header("X-KC-Timestamp", timestamp)
                .header("X-KC-Signature-Version", config.signatureVersion)

        if (!realmName.isNullOrBlank()) {
            builder.header("X-KC-Realm", realmName)
        }

        if (!clientId.isNullOrBlank()) {
            builder.header("X-KC-Client-Id", clientId)
        }

        if (method in IDEMPOTENCY_METHODS && request.header("Idempotency-Key").isNullOrBlank()) {
            builder.header("Idempotency-Key", requestId)
        }

        if (config.telemetryEnabled) {
            val incomingHeaders = session.context.httpRequest?.httpHeaders
            val traceParent =
                incomingHeaders?.getHeaderString("traceparent")?.takeIf { it.isNotBlank() }
                    ?: generateTraceParent()
            val traceState = incomingHeaders?.getHeaderString("tracestate")?.takeIf { it.isNotBlank() }

            builder.header("traceparent", traceParent)
            if (!traceState.isNullOrBlank()) {
                builder.header("tracestate", traceState)
            }
        }

        val signedRequest = builder.build()
        val signature = buildSignature(signedRequest, timestamp, config.signatureSecret)
        val finalRequest =
            if (signature != null) {
                signedRequest
                    .newBuilder()
                    .header("X-KC-Signature", signature)
                    .build()
            } else {
                signedRequest
            }

        return SHARED_CLIENT.newCall(finalRequest)
    }

    // Generated clients are initialized with one base URL, but runtime realm may differ.
    // Rewrite scheme/host/base-path while preserving endpoint suffix and query string.
    private fun rewriteUrl(
        request: Request,
        targetBaseUrl: String,
    ): okhttp3.HttpUrl {
        val targetBase =
            targetBaseUrl.toHttpUrlOrNull()
                ?: return request.url
        val targetBasePath = targetBase.encodedPath.trimEnd('/')
        val currentPath = request.url.encodedPath
        val suffixPath =
            if (baseUrlPath.isNotBlank() && currentPath.startsWith(baseUrlPath)) {
                currentPath.removePrefix(baseUrlPath)
            } else {
                currentPath
            }.trimStart('/')
        val mergedPath = mergePaths(targetBasePath, suffixPath)

        return targetBase
            .newBuilder()
            .encodedPath(mergedPath)
            .encodedQuery(request.url.encodedQuery)
            .build()
    }

    private fun mergePaths(
        basePath: String,
        suffixPath: String,
    ): String {
        val normalizedBase = basePath.trimEnd('/')
        val normalizedSuffix = suffixPath.trimStart('/')

        return when {
            normalizedBase.isBlank() || normalizedBase == "/" -> {
                if (normalizedSuffix.isBlank()) "/" else "/$normalizedSuffix"
            }

            normalizedSuffix.isBlank() -> if (normalizedBase.startsWith("/")) normalizedBase else "/$normalizedBase"
            else -> {
                val start = if (normalizedBase.startsWith("/")) normalizedBase else "/$normalizedBase"
                "$start/$normalizedSuffix"
            }
        }
    }

    private fun buildSignature(
        request: Request,
        timestamp: String,
        secret: String?,
    ): String? {
        if (secret.isNullOrBlank()) {
            return null
        }

        val canonicalBody =
            request.body?.let { body ->
                val buffer = Buffer()
                body.writeTo(buffer)
                buffer.readUtf8()
            } ?: ""
        val canonicalPayload =
            listOf(
                timestamp,
                request.method.uppercase(),
                request.url.encodedPath,
                canonicalBody,
            ).joinToString("\n")

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(canonicalPayload.toByteArray(Charsets.UTF_8))

        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(digest)
    }

    private fun generateTraceParent(): String {
        val traceId = randomHex(16)
        val parentId = randomHex(8)
        return "00-$traceId-$parentId-01"
    }

    private fun randomHex(byteSize: Int): String {
        val bytes = ByteArray(byteSize)
        RANDOM.nextBytes(bytes)
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    companion object {
        private val RANDOM = SecureRandom()
        private val IDEMPOTENCY_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")

        // One shared client/pool for all provider instances to maximize connection reuse.
        private val CONNECTION_POOL = ConnectionPool(64, 10, TimeUnit.MINUTES)
        private val SHARED_CLIENT: OkHttpClient =
            OkHttpClient
                .Builder()
                .followRedirects(true)
                .connectionPool(CONNECTION_POOL)
                .build()
    }
}
