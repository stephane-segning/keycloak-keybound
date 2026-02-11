package com.ssegning.keycloak.keybound.core.helper

import com.google.i18n.phonenumbers.PhoneNumberUtil

private const val MASK = "***"
private val PHONE_UTIL = PhoneNumberUtil.getInstance()

fun maskPhone(phone: String?): String {
    if (phone.isNullOrBlank()) return MASK
    val normalized = phone.trim()
    val e164 = try {
        val parsed = PHONE_UTIL.parse(normalized, "")
        PHONE_UTIL.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
    } catch (_: Exception) {
        normalized
    }
    return maskNumberLike(e164)
}

fun maskEmail(email: String?): String {
    if (email.isNullOrBlank()) return MASK
    val parts = email.trim().split("@", limit = 2)
    if (parts.size != 2) return maskGeneric(email)

    val local = maskEmailLocal(parts[0])
    val domainParts = parts[1].split(".", limit = 2)
    val domain = when {
        domainParts.isEmpty() -> MASK
        domainParts.size == 1 -> maskDomainLabel(domainParts[0])
        else -> "${maskDomainLabel(domainParts[0])}.${domainParts[1]}"
    }
    return "$local@$domain"
}

fun maskGeneric(value: String?): String {
    if (value.isNullOrBlank()) return MASK
    val trimmed = value.trim()
    if (trimmed.length <= 3) return MASK
    val prefix = trimmed.take(2)
    val suffix = trimmed.takeLast(2)
    return "$prefix$MASK$suffix"
}

fun maskForAttribute(attributeName: String, attributeValue: String?): String {
    val normalized = attributeName.lowercase()
    return when {
        normalized.contains("phone") -> maskPhone(attributeValue)
        normalized.contains("email") -> maskEmail(attributeValue)
        else -> maskGeneric(attributeValue)
    }
}

private fun maskNumberLike(value: String?): String {
    if (value.isNullOrBlank()) return MASK
    val trimmed = value.trim()
    if (trimmed.length <= 4) return MASK
    val prefix = trimmed.take(4)
    val suffix = trimmed.takeLast(2)
    return "$prefix$MASK$suffix"
}

private fun maskEmailLocal(local: String): String {
    if (local.isBlank()) return MASK
    val segments = local.split(".")
    return segments.joinToString(".") { segment ->
        if (segment.isBlank()) {
            MASK
        } else {
            val keep = if (segment.length >= 2) segment.take(2) else segment.take(1)
            "$keep*"
        }
    }
}

private fun maskDomainLabel(label: String): String {
    if (label.isBlank()) return MASK
    if (label.length <= 2) return "${label.first()}$MASK"
    return "${label.first()}$MASK"
}
