package com.ssegning.keycloak.keybound.examples.resource

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.web.util.UriComponentsBuilder

@Component
class ApprovalStreamAuthInterceptor(
    private val jwtDecoder: JwtDecoder,
    private val backendUserIdResolver: BackendUserIdResolver
) : HandshakeInterceptor {
    companion object {
        const val ATTR_BACKEND_USER_ID = "backendUserId"
        const val ATTR_KEYCLOAK_SUBJECT = "keycloakSubject"

        private val log = LoggerFactory.getLogger(ApprovalStreamAuthInterceptor::class.java)
    }

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        val token = UriComponentsBuilder.fromUri(request.uri)
            .build()
            .queryParams
            .getFirst("access_token")
            ?.trim()
            .orEmpty()
        if (token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }

        val jwt = try {
            jwtDecoder.decode(token)
        } catch (exception: Exception) {
            log.warn("WebSocket approvals handshake rejected: invalid token", exception)
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }

        val backendUserId = backendUserIdResolver.resolve(jwt)
        if (backendUserId.isNullOrBlank()) {
            response.setStatusCode(HttpStatus.BAD_REQUEST)
            return false
        }

        attributes[ATTR_BACKEND_USER_ID] = backendUserId
        attributes[ATTR_KEYCLOAK_SUBJECT] = jwt.subject.orEmpty()
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) {
        // no-op
    }
}
