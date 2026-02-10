package com.ssegning.keycloak.keybound.core.helper

import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.models.KeycloakSession

fun KeycloakSession.getApi(): ApiGateway = getProvider(ApiGateway::class.java)
