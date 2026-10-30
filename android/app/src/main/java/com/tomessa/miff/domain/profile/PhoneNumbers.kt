package com.tomessa.miff.domain.profile

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import com.tomessa.miff.domain.auth.ProfileDocument
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** One entry of the country selector: flag, name in the language of the phone, calling code (the "+33" of France). */
data class PhoneCountry(
    /** ISO 3166-1 alpha-2 region code ("FR"). */
    val region: String,
    val callingCode: Int,
    val name: String,
) {
    /** Flag emoji built from the two letters of the region (regional indicator symbols). Decorative only. */
    val flag: String
        get() =
            region
                .uppercase(Locale.ROOT)
                .map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }
                .joinToString("")

    val dialCode: String get() = "+$callingCode"
}

/** Result of reading what the person typed in the phone field. */
sealed interface PhoneResult {
    /** [e164] is the ONLY thing stored ("+33612345678"); [region] is the country the number belongs to. */
    data class Valid(
        val e164: String,
        val region: String,
    ) : PhoneResult

    data object Invalid : PhoneResult

    /**
     * A valid number of a kind nobody can be called or texted on in the usual way: premium rate, shared cost, toll free,
     * pager, voicemail... (the "0899" and "0800" of France). Refused on purpose, see [PhoneNumbers.ALLOWED_TYPES].
     */
    data object UnsupportedType : PhoneResult
}

/** A "+..." number typed or pasted: the country it belongs to and the national part to show in the field. */
data class DetectedNumber(
    val region: String,
    val national: String,
)

/**
 * Phone number rules of the Onboarding ticket 6.4 (decision Q1 of the PM), on top of Google's libphonenumber (official
 * numbering plans, Apache 2.0): the person picks a country and types the NATIONAL number; we store the E.164 form only
 * ("+" then the digits, no space, 8 to 16 characters). Validity is a validity of FORM for the numbering plan of the country
 * (mobile or landline): nothing proves that the person owns the number (no SMS on the free plan), and nothing is unique.
 *
 * Tolerated in the input: spaces, dashes, dots, parentheses, slashes, a leading zero or national prefix, a leading "+" or
 * international prefix. Letters and any other character make the number invalid (no vanity numbers). The number is never
 * logged and never put in an error message.
 */
object PhoneNumbers {
    /** Region used when the phone has none, or a region without a numbering plan in the library. */
    const val FALLBACK_REGION = "FR"

    private const val MAX_INPUT_LENGTH = 40
    private const val SEPARATORS = "-.()/‐‑‒–—"
    private const val LRM = '‎'
    private const val RLM = '‏'

    /**
     * Kinds of number accepted: landline, mobile (the two are told apart only in some countries) and internet telephony.
     * Stricter than the ticket (6.4 says "any number of valid form"): a premium-rate or toll-free number would make
     * every call or message from a member cost money or reach a service, in an app that shows the number to a whole
     * circle. Review recommendation, kept by default; the PM can relax it by adding a type here.
     */
    val ALLOWED_TYPES: Set<PhoneNumberUtil.PhoneNumberType> =
        setOf(
            PhoneNumberUtil.PhoneNumberType.FIXED_LINE,
            PhoneNumberUtil.PhoneNumberType.MOBILE,
            PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE,
            PhoneNumberUtil.PhoneNumberType.VOIP,
        )

    private val util: PhoneNumberUtil by lazy(LazyThreadSafetyMode.PUBLICATION) { PhoneNumberUtil.getInstance() }

    /** Every country with a numbering plan, sorted by name in [locale]. About 240 entries. */
    fun countries(locale: Locale): List<PhoneCountry> {
        val collator = Collator.getInstance(locale)
        return util.supportedRegions
            .map { region -> country(region, locale) }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    /** One country, for the closed selector ("France +33"): no need to build the whole list. */
    fun country(
        region: String,
        locale: Locale,
    ): PhoneCountry {
        val shown =
            Locale
                .Builder()
                .setRegion(region)
                .build()
                .getDisplayCountry(locale)
        return PhoneCountry(region, util.getCountryCodeForRegion(region), shown.ifBlank { region })
    }

    fun isSupportedRegion(region: String?): Boolean =
        region != null && region.uppercase(Locale.ROOT) in util.supportedRegions

    /** Country preselected: the region of the device, else France (ticket 6.4). */
    fun defaultRegion(deviceRegion: String?): String {
        val upper = deviceRegion?.uppercase(Locale.ROOT)
        return if (upper != null && isSupportedRegion(upper)) upper else FALLBACK_REGION
    }

    fun callingCodeOf(region: String): Int = util.getCountryCodeForRegion(region)

    /** The button of the phone step is active as soon as ONE digit was typed (ticket 6.4). */
    fun hasDigit(input: String): Boolean = input.any { Character.digit(it, 10) >= 0 }

    /**
     * Grey hint of the number field: a mobile example of the country, without the calling code and without the first zero
     * ("6 12 34 56 78" for France). Null if the library has no example.
     */
    fun exampleHint(region: String): String? {
        val example = util.getExampleNumberForType(region, PhoneNumberUtil.PhoneNumberType.MOBILE) ?: return null
        val international = util.format(example, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
        val prefix = "+${example.countryCode} "
        return if (international.startsWith(prefix)) international.removePrefix(prefix) else null
    }

    /**
     * Reads what the person typed for the country [region]. A leading "+" means an international number: the country is
     * then the one of the number itself. Valid = a number the numbering plan accepts, normalised to E.164.
     */
    fun normalize(
        region: String,
        input: String,
    ): PhoneResult {
        val cleaned = clean(input) ?: return PhoneResult.Invalid
        val number = parse(cleaned, if (cleaned.startsWith("+")) null else region) ?: return PhoneResult.Invalid
        if (!util.isValidNumber(number)) return PhoneResult.Invalid
        if (util.getNumberType(number) !in ALLOWED_TYPES) return PhoneResult.UnsupportedType
        val e164 = util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)
        // Last guard, the same shape as the Firestore rules: never hand a form the server would refuse.
        if (!ProfileDocument.E164.matches(e164)) return PhoneResult.Invalid
        val numberRegion = util.getRegionCodeForNumber(number) ?: util.getRegionCodeForCountryCode(number.countryCode)
        return PhoneResult.Valid(e164, numberRegion)
    }

    /**
     * Typing or pasting "+33 6 12 34 56 78" selects France and keeps "612345678" (ticket TEL2). Only when the text is a
     * plausible international number of a known country; otherwise null and the field is left as typed.
     */
    fun detectInternational(input: String): DetectedNumber? {
        val cleaned = clean(input) ?: return null
        if (!cleaned.startsWith("+")) return null
        val number = parse(cleaned, null) ?: return null
        if (!util.isPossibleNumber(number)) return null
        val region = util.getRegionCodeForNumber(number) ?: util.getRegionCodeForCountryCode(number.countryCode)
        if (!isSupportedRegion(region)) return null
        return DetectedNumber(region, util.getNationalSignificantNumber(number))
    }

    /** Matching of the search field of the country selector: accents and case ignored; name, region code or calling code. */
    fun matchesSearch(
        country: PhoneCountry,
        query: String,
    ): Boolean {
        val q = fold(query).trim().removePrefix("+")
        if (q.isEmpty()) return true
        if (q.all { it.isDigit() }) return country.callingCode.toString().startsWith(q)
        return fold(country.name).contains(q) || country.region.equals(q, ignoreCase = true)
    }

    private fun fold(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)

    /**
     * Keeps digits (any script, turned into 0-9) and one leading "+"; tolerates spaces, dashes, dots, parentheses and slashes.
     * Null if anything else is in the text (letters, other symbols) or if it is far too long to be a number.
     */
    private fun clean(input: String): String? {
        if (input.length > MAX_INPUT_LENGTH) return null
        val out = StringBuilder()
        var seenContent = false
        for (c in input) {
            val digit = Character.digit(c, 10)
            when {
                digit >= 0 -> {
                    out.append(('0'.code + digit).toChar())
                    seenContent = true
                }

                c == '+' && !seenContent -> {
                    out.append('+')
                    seenContent = true
                }

                Character.isWhitespace(c) || Character.isSpaceChar(c) || c in SEPARATORS || c == LRM || c == RLM -> {
                    Unit
                }

                else -> {
                    return null
                }
            }
        }
        return out.toString().takeIf { it.any(Char::isDigit) }
    }

    private fun parse(
        cleaned: String,
        region: String?,
    ): Phonenumber.PhoneNumber? =
        try {
            util.parse(cleaned, region)
        } catch (e: NumberParseException) {
            null
        }
}
