package com.ssegning.keycloak.keybound.api

import com.ssegning.keycloak.keybound.core.models.HttpConfig
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.keycloak.models.KeycloakSession
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SimpleCallFactory(
    private val session: KeycloakSession,
    private val config: HttpConfig = HttpConfig.fromEnv(session.context)
) : Call.Factory {
    val baseUrl: String = config.baseUrl

    private val client: OkHttpClient = config.client.newBuilder()
        .addInterceptor { chain ->
            val original = chain.request()
            val method = original.method.uppercase()
            val timestamp = Instant.now().epochSecond.toString()
            val requestId = UUID.randomUUID().toString()
            val realmName = session.context.realm?.name
            val clientId = session.context.client?.clientId

            val builder = original.newBuilder()
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

            if (method in IDEMPOTENCY_METHODS && original.header("Idempotency-Key").isNullOrBlank()) {
                builder.header("Idempotency-Key", requestId)
            }

            if (config.telemetryEnabled) {
                val incomingHeaders = session.context.httpRequest?.httpHeaders
                val traceParent = incomingHeaders?.getHeaderString("traceparent")?.takeIf { it.isNotBlank() }
                    ?: generateTraceParent()
                val traceState = incomingHeaders?.getHeaderString("tracestate")?.takeIf { it.isNotBlank() }

                builder.header("traceparent", traceParent)
                if (!traceState.isNullOrBlank()) {
                    builder.header("tracestate", traceState)
                }
            }

            val signedRequest = builder.build()
            val signature = buildSignature(signedRequest, timestamp, config.signatureSecret)
            if (signature != null) {
                chain.proceed(
                    signedRequest.newBuilder()
                        .header("X-KC-Signature", signature)
                        .build()
                )
            } else {
                chain.proceed(signedRequest)
            }
        }
        .build()

    override fun newCall(request: Request): Call = client.newCall(request)

    private fun buildSignature(request: Request, timestamp: String, secret: String?): String? {
        if (secret.isNullOrBlank()) {
            return null
        }

        val canonicalBody = request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        } ?: ""
        val canonicalPayload = listOf(
            timestamp,
            request.method.uppercase(),
            request.url.encodedPath,
            canonicalBody
        ).joinToString("\n")

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(canonicalPayload.toByteArray(Charsets.UTF_8))

        return Base64.getUrlEncoder()
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
    }
}
