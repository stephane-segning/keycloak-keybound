package com.ssegning.keycloak.keybound.helper

import com.ssegning.keycloak.keybound.models.SmsRequest
import com.ssegning.keycloak.keybound.service.getAllCountries
import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext
import java.util.*

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

const val CFG_ALLOWED_COUNTRY_PATTERN = "allowedCountryPattern"
const val ALLOWED_COUNTRY_PATTERN = "SMS_API_COUNTRY_PATTERN"

const val CFG_USER_PHONE_ATTRIBUTE_NAME = "userPhoneAttributeName"
const val USER_PHONE_ATTRIBUTE_NAME = "SMS_API_ATTRIBUTE_PHONE_NAME"
const val DEFAULT_PHONE_KEY_NAME = "phoneNumber"

const val ATTEMPTED_PHONE_NUMBER = "ATTEMPTED_PHONE_NUMBER"
const val ATTEMPTED_HASH = "ATTEMPTED_HASH"

fun AuthenticationFlowContext.phoneNumber() = this.getConfigOrEnv(
    CFG_USER_PHONE_ATTRIBUTE_NAME,
    USER_PHONE_ATTRIBUTE_NAME,
    DEFAULT_PHONE_KEY_NAME
)

fun AuthenticationFlowContext.allowedCountryPattern() = this.getConfigOrEnv(
    CFG_ALLOWED_COUNTRY_PATTERN,
    ALLOWED_COUNTRY_PATTERN,
    ".*"
)

fun AuthenticationFlowContext.allowedCountries() = getAllCountries(this.allowedCountryPattern() ?: "")

fun AuthenticationFlowContext.smsRequestContext(step: String?): SmsRequest {
    val authSession = this.authenticationSession

    val client = if (authSession != null) authSession.client else null

    val connection = this.connection

    val realm = if (this.realm != null) this.realm.name else null
    val clientId = client?.clientId
    val ipAddress = if (connection != null) connection.remoteAddr else null
    val userAgent = if (this.httpRequest != null && this.httpRequest.httpHeaders != null)
        this.httpRequest.httpHeaders.getHeaderString("User-Agent")
    else
        null
    val sessionId = if (authSession != null) authSession.getParentSession().getId() else null

    val metadata = HashMap<String?, Any?>()
    if (step != null) {
        metadata["step"] = step
    }

    return SmsRequest(realm, clientId, ipAddress, userAgent, sessionId, UUID.randomUUID().toString(), metadata)
}