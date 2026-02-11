package com.ssegning.keycloak.keybound.examples.resource

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Component
class ApprovalStreamWebSocketHandler(
    private val backendApprovalsClient: BackendApprovalsClient,
    private val approvalStreamScheduler: TaskScheduler,
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {
    private data class SessionState(
        var lastBackendPayload: String? = null,
        var task: ScheduledFuture<*>? = null
    )

    companion object {
        private val log = LoggerFactory.getLogger(ApprovalStreamWebSocketHandler::class.java)
        private val PUBLISH_INTERVAL: Duration = Duration.ofSeconds(2)
    }

    private val sessionStates = ConcurrentHashMap<String, SessionState>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val backendUserId = session.attributes[ApprovalStreamAuthInterceptor.ATTR_BACKEND_USER_ID] as? String
        if (backendUserId.isNullOrBlank()) {
            session.close(CloseStatus.BAD_DATA)
            return
        }

        val state = SessionState()
        sessionStates[session.id] = state
        publishApprovalsIfChanged(session, state, backendUserId)
        state.task = approvalStreamScheduler.scheduleAtFixedRate(
            { publishApprovalsIfChanged(session, state, backendUserId) },
            PUBLISH_INTERVAL
        )
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessionStates.remove(session.id)?.task?.cancel(true)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        log.warn("Approvals WebSocket transport error for session {}", session.id, exception)
        sessionStates.remove(session.id)?.task?.cancel(true)
    }

    private fun publishApprovalsIfChanged(
        session: WebSocketSession,
        state: SessionState,
        backendUserId: String
    ) {
        if (!session.isOpen) {
            return
        }

        val backendResponse = try {
            backendApprovalsClient.listUserApprovals(backendUserId)
        } catch (exception: Exception) {
            mapOf(
                "user_id" to backendUserId,
                "approvals" to emptyList<Map<String, Any?>>(),
                "error" to "Backend approvals request failed: ${exception.message}"
            )
        }

        val backendPayload = objectMapper.writeValueAsString(backendResponse)
        if (backendPayload == state.lastBackendPayload) {
            return
        }

        val responsePayload = mapOf(
            "keycloak_subject" to (session.attributes[ApprovalStreamAuthInterceptor.ATTR_KEYCLOAK_SUBJECT] as? String),
            "backend_user_id" to backendUserId,
            "backend" to backendResponse
        )
        val responseText = objectMapper.writeValueAsString(responsePayload)

        synchronized(session) {
            if (!session.isOpen) {
                return
            }

            session.sendMessage(TextMessage(responseText))
            state.lastBackendPayload = backendPayload
        }
    }
}
