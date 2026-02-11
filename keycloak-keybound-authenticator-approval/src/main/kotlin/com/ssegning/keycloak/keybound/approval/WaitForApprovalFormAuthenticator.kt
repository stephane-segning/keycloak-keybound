package com.ssegning.keycloak.keybound.approval

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticator
import com.ssegning.keycloak.keybound.core.models.ApprovalStatus
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.keycloak.crypto.Algorithm
import org.keycloak.crypto.KeyUse
import org.keycloak.crypto.ServerAsymmetricSignatureSignerContext
import org.keycloak.jose.jws.JWSBuilder
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import org.slf4j.LoggerFactory

class WaitForApprovalFormAuthenticator(private val apiGateway: ApiGateway) : AbstractAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(WaitForApprovalFormAuthenticator::class.java)
        private const val POLLING_INTERVAL_MS = 2000
    }

    override fun requiresUser() = true

    override fun configuredFor(session: KeycloakSession, realm: RealmModel, user: UserModel?): Boolean {
        if (user == null) {
            return false
        }

        val deviceCount = apiGateway.listUserDevices(user.id, false)?.size
        return deviceCount != null && deviceCount > 0
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val session = context.authenticationSession
        val requestId = session.getAuthNote(StartApprovalRequestAuthenticator.APPROVAL_REQUEST_ID_NOTE)

        if (requestId == null) {
            log.error("Approval request ID not found in session")
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
            return
        }

        log.debug("Rendering approval wait page for request {}", requestId)
        val pollingToken = createPollingToken(context, requestId)
        val pollingUrl = "/realms/${context.realm.name}/device-approval/status"

        val form = context.form()
            .setAttribute("pollingUrl", pollingUrl)
            .setAttribute("pollingToken", pollingToken)
            .setAttribute("pollingInterval", POLLING_INTERVAL_MS)

        context.challenge(form.createForm("approval-wait.ftl"))
    }

    override fun action(context: AuthenticationFlowContext) {
        val session = context.authenticationSession
        val requestId = session.getAuthNote(StartApprovalRequestAuthenticator.APPROVAL_REQUEST_ID_NOTE)

        if (requestId == null) {
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
            return
        }

        val apiGateway = context.session.getProvider(ApiGateway::class.java)
        log.debug("Polling approval status for request {}", requestId)
        val status = apiGateway.checkApprovalStatus(requestId)

        when (status) {
            ApprovalStatus.APPROVED -> context.success()
            ApprovalStatus.DENIED -> context.failure(AuthenticationFlowError.ACCESS_DENIED)
            ApprovalStatus.EXPIRED -> context.failure(AuthenticationFlowError.EXPIRED_CODE)
            else -> {
                log.debug("Approval request {} still pending or error ({})", requestId, status)
                // Still pending or error, show form again
                authenticate(context)
            }
        }
    }

    private fun createPollingToken(context: AuthenticationFlowContext, requestId: String): String {
        // Create a short-lived signed JWT for polling
        return JWSBuilder()
            .jsonContent(
                mapOf(
                    "request_id" to requestId,
                    "exp" to (System.currentTimeMillis() / 1000) + 300 // 5 minutes expiration
                )
            )
            .sign(
                ServerAsymmetricSignatureSignerContext(
                    context.session.keys().getActiveKey(context.realm, KeyUse.SIG, Algorithm.RS256)
                )
            )
    }
}
