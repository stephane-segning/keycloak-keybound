package com.ssegning.keycloak.keybound.core.helper

import org.jboss.logging.Logger

private val log: Logger = Logger.getLogger("AuthenticationFlowContextUtils")

fun String.getEnv(defaultValue: String? = null): String? {
    var getenv = System.getenv(this)
    if (!getenv.isNullOrEmpty()) {
        return getenv
    }

    getenv = System.getProperty(this, defaultValue)
    if (getenv.isNullOrEmpty()) {
        log.error("Environment variable $this is empty")
    }

    return getenv
}
