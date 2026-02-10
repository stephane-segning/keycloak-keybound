package com.ssegning.keycloak.keybound.api

import com.ssegning.keycloak.keybound.core.models.HttpConfig
import okhttp3.Call
import org.keycloak.models.KeycloakSession

class SimpleCallFactory(private val delegate: Call.Factory) : Call.Factory by delegate {
    constructor(session: KeycloakSession) : this(HttpConfig.fromEnv(session.context).client)
}