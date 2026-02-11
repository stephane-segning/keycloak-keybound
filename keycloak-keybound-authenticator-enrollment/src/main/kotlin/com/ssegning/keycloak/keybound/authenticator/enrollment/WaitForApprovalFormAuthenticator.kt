package com.ssegning.keycloak.keybound.authenticator.enrollment

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

        val backendUserId = KeyboundUserResolver.resolveBackendUserId(user)
        val deviceCount = apiGateway.listUserDevices(backendUserId, false)?.size
        return deviceCount != null && deviceCount > 0
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val authSession = context.authenticationSession
        val requestId = authSession.getAuthNote(StartApprovalRequestAuthenticator.APPROVAL_REQUEST_ID_NOTE)

        if (requestId.isNullOrBlank()) {
            log.error("Approval request ID missing in authentication session")
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
            return
        }

        val pollingToken = createPollingToken(context, requestId)
        val pollingUrl = "/realms/${context.realm.name}/device-approval/status"

        val form = context.form()
            .setAttribute("pollingUrl", pollingUrl)
            .setAttribute("pollingToken", pollingToken)
            .setAttribute("pollingInterval", POLLING_INTERVAL_MS)

        context.challenge(form.createForm("approval-wait.ftl"))
    }

    override fun action(context: AuthenticationFlowContext) {
        val authSession = context.authenticationSession
        val requestId = authSession.getAuthNote(StartApprovalRequestAuthenticator.APPROVAL_REQUEST_ID_NOTE)
        if (requestId.isNullOrBlank()) {
            context.failure(AuthenticationFlowError.INTERNAL_ERROR)
            return
        }

        val status = apiGateway.checkApprovalStatus(requestId)
        when (status) {
            ApprovalStatus.APPROVED -> context.success()
            ApprovalStatus.DENIED -> context.failure(AuthenticationFlowError.ACCESS_DENIED)
            ApprovalStatus.EXPIRED -> context.failure(AuthenticationFlowError.EXPIRED_CODE)
            else -> {
                log.debug("Approval request {} still pending or unavailable status={}", requestId, status)
                authenticate(context)
            }
        }
    }

    private fun createPollingToken(context: AuthenticationFlowContext, requestId: String): String {
        return JWSBuilder()
            .jsonContent(
                mapOf(
                    "request_id" to requestId,
                    "exp" to (System.currentTimeMillis() / 1000) + 300
                )
            )
            .sign(
                ServerAsymmetricSignatureSignerContext(
                    context.session.keys().getActiveKey(context.realm, KeyUse.SIG, Algorithm.RS256)
                )
            )
    }
}
