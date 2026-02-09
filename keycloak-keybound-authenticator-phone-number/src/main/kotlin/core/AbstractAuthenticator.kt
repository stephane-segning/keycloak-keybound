package com.ssegning.keycloak.keybound.core

import com.ssegning.keycloak.keybound.helper.noop
import org.keycloak.authentication.Authenticator
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel

abstract class AbstractAuthenticator : Authenticator {
    override fun requiresUser(): Boolean = false

    override fun configuredFor(
        session: KeycloakSession,
        realm: RealmModel,
        user: UserModel?
    ): Boolean = true

    override fun setRequiredActions(
        session: KeycloakSession,
        realm: RealmModel,
        user: UserModel?
    ) = noop()

    override fun close() = noop()
}