package com.ssegning.keycloak.keybound.authenticator.enrollment

import com.ssegning.keycloak.keybound.core.authenticator.AbstractAuthenticator
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.models.UserModel
import org.slf4j.LoggerFactory

class CheckUserByPhoneAuthenticator : AbstractAuthenticator() {
    companion object {
        private val log = LoggerFactory.getLogger(CheckUserByPhoneAuthenticator::class.java)
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val phoneE164 = context.authenticationSession.getAuthNote(KeyboundFlowNotes.PHONE_E164_NOTE_NAME)?.trim()
        if (phoneE164.isNullOrBlank()) {
            log.debug("Phone note missing; skipping user lookup")
            context.success()
            return
        }

        val resolvedUser = resolveByPhone(context, phoneE164)
        if (resolvedUser == null) {
            log.debug("No user resolved by phone {}", phoneE164)
            context.success()
            return
        }

        log.debug("Resolved existing user {} by phone {}", resolvedUser.username, phoneE164)
        context.user = resolvedUser
        context.success()
    }

    override fun action(context: AuthenticationFlowContext) {
        // No action needed for this authenticator
    }

    private fun resolveByPhone(context: AuthenticationFlowContext, phoneE164: String): UserModel? {
        val session = context.session
        val realm = context.realm
        return findSingleUserByAttribute(context, "phone_e164", phoneE164)
            ?: findSingleUserByAttribute(context, "phone_number", phoneE164)
            ?: session.users().getUserByUsername(realm, phoneE164)
            ?: session.users().getUserByEmail(realm, phoneE164)
    }

    private fun findSingleUserByAttribute(
        context: AuthenticationFlowContext,
        attributeName: String,
        attributeValue: String
    ): UserModel? {
        val stream = context.session.users()
            .searchForUserByUserAttributeStream(context.realm, attributeName, attributeValue)
        return try {
            val iterator = stream.iterator()
            if (!iterator.hasNext()) {
                null
            } else {
                val first = iterator.next()
                if (iterator.hasNext()) {
                    log.error(
                        "Multiple users resolved for {}='{}' in realm {}",
                        attributeName,
                        attributeValue,
                        context.realm.name
                    )
                    null
                } else {
                    first
                }
            }
        } finally {
            stream.close()
        }
    }
}
