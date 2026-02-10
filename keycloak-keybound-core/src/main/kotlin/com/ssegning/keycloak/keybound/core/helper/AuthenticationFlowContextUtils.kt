package com.ssegning.keycloak.keybound.core.helper

import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext

private val log: Logger = Logger.getLogger("AuthenticationFlowContextUtils")

fun AuthenticationFlowContext.getConfig(key: String): String? {
    if (this.authenticatorConfig == null) {
        return null
    }

    val config = this.authenticatorConfig.config ?: return null
    return config[key]
}

fun AuthenticationFlowContext.getConfigOrEnv(
    configKey: String,
    envKey: String,
    defaultValue: String? = null
): String? {
    val config = this.getConfig(configKey)

    if (!config.isNullOrEmpty()) {
        return config
    }

    val value = envKey.getEnv(defaultValue)
    if (value.isNullOrEmpty()) {
        log.warnf("Resolved blank value for %s (config=%s, env=%s)", configKey, configKey, envKey)
        return defaultValue
    }

    return value
}

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
