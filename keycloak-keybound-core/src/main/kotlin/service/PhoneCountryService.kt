package com.ssegning.keycloak.keybound.service

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import org.jboss.logging.Logger
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate
import java.util.regex.Pattern
import java.util.stream.Collectors

private val log: Logger = Logger.getLogger("PhoneCountryService")

class CountryPhoneCode(val label: String, val code: String)

val PHONE_NUMBER_UTIL: PhoneNumberUtil = PhoneNumberUtil.getInstance()
val COUNTRIES_CACHE = ConcurrentHashMap<String, MutableSet<CountryPhoneCode>>()

fun String.parsePhoneNumber(): Phonenumber.PhoneNumber? {
    try {
        return PHONE_NUMBER_UTIL.parse(
            this,
            null
        )
    } catch (err: NumberParseException) {
        log.warn("Could not parse phone number", err)
        return null
    }
}

fun String.formatE164(): String? {
    val parsed = this.parsePhoneNumber() ?: return null

    return PHONE_NUMBER_UTIL.format(
        parsed,
        PhoneNumberUtil.PhoneNumberFormat.E164
    )
}

fun getAllCountries(allowedCountryPattern: String) = COUNTRIES_CACHE.computeIfAbsent(allowedCountryPattern) {
    allowedCountryPattern.computeCountries()
}

private fun String.computeCountries(): MutableSet<CountryPhoneCode> {
    val allowedCountries = compileCountryPredicate(this)

    return PHONE_NUMBER_UTIL
        .supportedCallingCodes
        .stream()
        .map {
            val countryCode = PHONE_NUMBER_UTIL.getRegionCodeForCountryCode(it)
            val locale = Locale.of("", countryCode)
            val countryName = locale.displayCountry

            if (!allowedCountries.test(countryCode)) {
                return@map null
            }
            CountryPhoneCode(countryName, "+$it")
        }
        .filter { it != null }
        .sorted(Comparator.comparing<CountryPhoneCode, String> { it.label })
        .collect(Collectors.toCollection { LinkedHashSet() })
}

private fun compileCountryPredicate(pattern: String = ".*"): Predicate<String?> {
    return try {
        Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).asMatchPredicate()
    } catch (_e: Exception) {
        Pattern.compile(".*").asMatchPredicate()
    }
}