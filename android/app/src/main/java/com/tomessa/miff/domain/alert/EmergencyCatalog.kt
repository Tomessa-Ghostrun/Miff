package com.tomessa.miff.domain.alert

import com.tomessa.miff.domain.miff.MiffMember

/*
 * The emergency numbers offered next to the people of the Miff (ticket 8.4). A TABLE OF DATA, never a country written in a screen: adding a country is adding a line here.
 *
 * The country is the REGION OF THE LOCALE of the phone, never the position (no permission, nothing asked in the stress). The language does not choose the country: a phone in
 * German in France keeps the French numbers.
 *
 * The call is ALWAYS made through the dialler of the system (ACTION_DIAL): Miff never calls by itself and has no permission to ("CALL_PHONE" is not in the manifest).
 *
 * SOURCES the numbers were checked against (official pages, listed in the PR): France: service-public.fr (17 Police secours, 15 SAMU, 18 Sapeurs-pompiers, 112 numéro
 * d'urgence européen); European Union and EEA: the single European emergency number 112 (Directive (EU) 2018/1972, art. 109). Everywhere else: 112 is offered with an honest
 * label (it is redirected to the local number on most mobile networks, not on all of them): to be checked again when a country is added.
 */

/** What a number is called: the wording is a text of the catalogue of the language ([com.tomessa.miff.ui.alert.AlertTexts]), never written here. */
enum class EmergencyLabel { POLICE, AMBULANCE, FIRE, EUROPEAN_112, INTERNATIONAL_112 }

/** One line of the section "Services d'urgence". */
class EmergencyNumber(
    val label: EmergencyLabel,
    val number: String,
)

object EmergencyCatalog {
    private val FRANCE =
        listOf(
            EmergencyNumber(EmergencyLabel.POLICE, "17"),
            EmergencyNumber(EmergencyLabel.AMBULANCE, "15"),
            EmergencyNumber(EmergencyLabel.FIRE, "18"),
            EmergencyNumber(EmergencyLabel.EUROPEAN_112, "112"),
        )

    private val EUROPE = listOf(EmergencyNumber(EmergencyLabel.EUROPEAN_112, "112"))

    private val INTERNATIONAL = listOf(EmergencyNumber(EmergencyLabel.INTERNATIONAL_112, "112"))

    /** France and its overseas departments (the same numbers there). */
    private val FRANCE_REGIONS = setOf("FR", "GP", "MQ", "GF", "RE", "YT")

    /** The member states of the European Union and the other countries of the EEA (112 is the single emergency number there). */
    private val EUROPE_REGIONS =
        setOf(
            "AT",
            "BE",
            "BG",
            "HR",
            "CY",
            "CZ",
            "DK",
            "EE",
            "FI",
            "DE",
            "GR",
            "HU",
            "IE",
            "IT",
            "LV",
            "LT",
            "LU",
            "MT",
            "NL",
            "PL",
            "PT",
            "RO",
            "SK",
            "SI",
            "ES",
            "SE",
            "IS",
            "LI",
            "NO",
        )

    /** The numbers of [region] (an ISO 3166 code, as the locale gives it; null or odd = unknown). Never empty. */
    fun forRegion(region: String?): List<EmergencyNumber> {
        val code = region?.trim()?.uppercase().orEmpty()
        return when {
            code in FRANCE_REGIONS -> FRANCE
            code in EUROPE_REGIONS -> EUROPE
            else -> INTERNATIONAL
        }
    }

    /** What may reach the dialler: two to four digits (a short number), checked again by the caller of the intent. */
    private val SHORT_NUMBER = Regex("^[0-9]{2,4}$")

    fun isDialable(number: String): Boolean = SHORT_NUMBER.matches(number)

    /** Every number of the table, for the test that checks them all. */
    internal fun all(): List<EmergencyNumber> = FRANCE + EUROPE + INTERNATIONAL
}

/** A person of the Miff the author can call. */
class AlertContact(
    val uid: String,
    /** How the list writes the person: the first name, with the last name added when two lines would read the same. */
    val name: String,
    /** E.164, checked ([MiffMember.phoneFromServer]). */
    val phone: String,
) {
    override fun toString(): String = "AlertContact(redacted)"
}

/** The choice shown on the screen "avertis": a person, or an emergency service. */
sealed interface AlertDialTarget {
    class Person(
        val contact: AlertContact,
    ) : AlertDialTarget {
        override fun toString(): String = "Person(redacted)"
    }

    class Emergency(
        val entry: EmergencyNumber,
    ) : AlertDialTarget

    val number: String
        get() =
            when (this) {
                is Person -> contact.phone
                is Emergency -> entry.number
            }

    /** Stable identity of a line (to remember the choice and to compare). */
    val id: String
        get() =
            when (this) {
                is Person -> "p:${contact.uid}"
                is Emergency -> "e:${entry.number}:${entry.label.name}"
            }
}

/** The list of the screen "avertis" and the contact chosen at the start (ticket 8.2, 8.3). */
class AlertCallList(
    val people: List<AlertContact>,
    val emergency: List<EmergencyNumber>,
    val preselected: AlertDialTarget,
) {
    val targets: List<AlertDialTarget> =
        people.map { AlertDialTarget.Person(it) } + emergency.map { AlertDialTarget.Emergency(it) }

    override fun toString(): String = "AlertCallList(redacted)"

    companion object {
        /**
         * [members] are the members of the ORIGIN Miff only (never another Miff of the person), already in the order the app uses everywhere: the creator first, then the
         * order of arrival. That is exactly the rule of the PM: the preselected person is the creator if it is not me, else the first other member; a person without a
         * number is not in the list, so the one chosen is "the next one with a number". Nobody with a number (or I am alone): only the emergency services, and 112 (or
         * its regional equivalent) is preselected. [region] is the region of the locale of the phone.
         */
        fun build(
            members: List<MiffMember>,
            myUid: String,
            region: String?,
        ): AlertCallList {
            val others = members.filter { it.uid != myUid && it.phone != null }
            val names = others.map { it.callName.trim() }
            val people =
                others.mapIndexed { index, member ->
                    val first = names[index]
                    // Two lines that would read the same get the last name too (the list has no other way to tell two Emmanuelle apart).
                    val homonyms = names.count { it.equals(first, ignoreCase = true) } > 1
                    AlertContact(
                        uid = member.uid,
                        name =
                            if (homonyms) {
                                member.displayName.trim().ifEmpty {
                                    first
                                }
                            } else {
                                first.ifEmpty {
                                    member.displayName
                                        .trim()
                                }
                            },
                        phone = member.phone!!,
                    )
                }
            val emergency = EmergencyCatalog.forRegion(region)
            val preselected =
                people.firstOrNull()?.let { AlertDialTarget.Person(it) }
                    ?: AlertDialTarget.Emergency(emergency.firstOrNull { it.number == "112" } ?: emergency.first())
            return AlertCallList(people, emergency, preselected)
        }
    }
}
