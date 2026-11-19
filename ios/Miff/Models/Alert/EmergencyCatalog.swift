import Foundation

// Les numéros d'urgence proposés à côté des proches de la Miff (ticket 8.4). UNE TABLE DE DONNÉES, jamais un pays écrit dans un écran : ajouter
// un pays, c'est ajouter une ligne ici. Miroir d'`EmergencyCatalog.kt` d'Android (mêmes régions, mêmes numéros : testés à l'identique).
//
// Le pays est la RÉGION DE LA LOCALE du téléphone, JAMAIS la position (aucune permission, rien à demander dans le stress). La langue ne choisit
// pas le pays : un téléphone en allemand en France garde les numéros français.
//
// L'appel passe TOUJOURS par le composeur du système (`tel:`, iOS demande confirmation) : Miff n'appelle jamais toute seule et n'a aucune
// permission pour cela.
//
// SOURCES contre lesquelles les numéros ont été vérifiés (pages officielles, listées dans la PR) : France : service-public.fr (17 Police secours,
// 15 SAMU, 18 Sapeurs-pompiers, 112 numéro d'urgence européen) ; Union européenne et EEE : le numéro unique européen 112 (directive (UE)
// 2018/1972, art. 109). Ailleurs : 112 est proposé avec un libellé honnête (il est redirigé vers le numéro local sur la plupart des réseaux
// mobiles, pas tous) : à revérifier quand un pays est ajouté.

/// Comment un numéro s'appelle : le texte est dans le catalogue de la langue (`alert_emergency_*`), jamais écrit ici.
enum EmergencyLabel: String, Equatable {
    case police
    case ambulance
    case fire
    case european112
    case international112

    /// La clé du catalogue de textes.
    var textKey: String {
        switch self {
        case .police:
            return "alert_emergency_police"
        case .ambulance:
            return "alert_emergency_ambulance"
        case .fire:
            return "alert_emergency_fire"
        case .european112:
            return "alert_emergency_european"
        case .international112:
            return "alert_emergency_international"
        }
    }
}

/// Une ligne de la section « Services d'urgence ».
struct EmergencyNumber: Equatable {
    let label: EmergencyLabel
    let number: String
}

enum EmergencyCatalog {
    private static let france = [
        EmergencyNumber(label: .police, number: "17"),
        EmergencyNumber(label: .ambulance, number: "15"),
        EmergencyNumber(label: .fire, number: "18"),
        EmergencyNumber(label: .european112, number: "112")
    ]

    private static let europe = [EmergencyNumber(label: .european112, number: "112")]

    private static let international = [EmergencyNumber(label: .international112, number: "112")]

    /// La France et ses départements d'outre-mer (mêmes numéros).
    private static let franceRegions: Set<String> = ["FR", "GP", "MQ", "GF", "RE", "YT"]

    /// Les États membres de l'Union européenne et les autres pays de l'EEE (112 y est le numéro d'urgence unique).
    private static let europeRegions: Set<String> = [
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "DE", "GR", "HU", "IE", "IT",
        "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "IS", "LI", "NO"
    ]

    /// Les numéros de `region` (un code ISO 3166, tel que la locale le donne ; nil ou étrange = inconnu). Jamais vide.
    static func forRegion(_ region: String?) -> [EmergencyNumber] {
        let code = region?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() ?? ""
        if franceRegions.contains(code) {
            return france
        }
        if europeRegions.contains(code) {
            return europe
        }
        return international
    }

    /// Ce qui peut arriver au composeur : deux à quatre chiffres (un numéro court), revérifié par celui qui construit l'adresse.
    static func isDialable(_ number: String) -> Bool {
        let scalars = Array(number.unicodeScalars)
        return (2...4).contains(scalars.count) && scalars.allSatisfy { $0.value >= 0x30 && $0.value <= 0x39 }
    }

    /// L'adresse `tel:` d'un numéro d'urgence du catalogue, ou nil quand ce n'est pas un numéro court.
    static func dialURL(_ number: String) -> URL? {
        guard isDialable(number) else {
            return nil
        }
        return URL(string: "tel:\(number)")
    }

    /// Tous les numéros de la table, pour le test qui les vérifie un par un.
    static func all() -> [EmergencyNumber] {
        france + europe + international
    }
}

/// Une personne de la Miff que l'auteur peut appeler.
struct AlertContact: Equatable, CustomStringConvertible {
    let uid: String
    /// Comment la liste écrit la personne : le prénom, avec le nom de famille quand deux lignes se liraient pareil.
    let name: String
    /// E.164, vérifié (`MiffMember.phoneFromServer`).
    let phone: String

    var description: String {
        "AlertContact(redacted)"
    }
}

/// Le choix montré sur l'écran « avertis » : une personne, ou un service d'urgence.
enum AlertDialTarget: Equatable, CustomStringConvertible {
    case person(AlertContact)
    case emergency(EmergencyNumber)

    var number: String {
        switch self {
        case .person(let contact):
            return contact.phone
        case .emergency(let entry):
            return entry.number
        }
    }

    /// L'identité stable d'une ligne (pour retenir le choix et comparer).
    var id: String {
        switch self {
        case .person(let contact):
            return "p:\(contact.uid)"
        case .emergency(let entry):
            return "e:\(entry.number):\(entry.label.rawValue)"
        }
    }

    var description: String {
        switch self {
        case .person:
            return "AlertDialTarget.person(redacted)"
        case .emergency:
            return "AlertDialTarget.emergency"
        }
    }
}

/// La liste de l'écran « avertis » et le contact choisi au départ (ticket 8.2 et 8.3).
struct AlertCallList: Equatable, CustomStringConvertible {
    let people: [AlertContact]
    let emergency: [EmergencyNumber]
    let preselected: AlertDialTarget

    var targets: [AlertDialTarget] {
        people.map { AlertDialTarget.person($0) } + emergency.map { AlertDialTarget.emergency($0) }
    }

    var description: String {
        "AlertCallList(redacted)"
    }

    /// `members` sont les membres de la Miff d'ORIGINE seulement (jamais ceux d'une autre Miff de la personne), déjà dans l'ordre que l'app
    /// utilise partout : le créateur d'abord, puis l'ordre d'arrivée. C'est exactement la règle du PM : le contact présélectionné est le
    /// créateur s'il n'est pas moi, sinon le premier autre membre ; une personne sans numéro n'est pas dans la liste, donc celle choisie est
    /// « la suivante avec un numéro ». Personne avec un numéro (ou je suis seul) : seulement les services d'urgence, et 112 (ou son
    /// équivalent régional) est présélectionné. `region` est la région de la locale du téléphone.
    static func build(members: [MiffMember], myUid: String, region: String?) -> AlertCallList {
        let others = members.filter { $0.uid != myUid && $0.phone != nil }
        let firsts = others.map { $0.callName.trimmingCharacters(in: .whitespacesAndNewlines) }
        var people: [AlertContact] = []
        for (index, member) in others.enumerated() {
            guard let phone = member.phone else {
                continue
            }
            let first = firsts[index]
            // Deux lignes qui se liraient pareil reçoivent aussi le nom de famille (la liste n'a pas d'autre moyen de distinguer deux
            // Emmanuelle).
            let homonyms = firsts.filter { $0.caseInsensitiveCompare(first) == .orderedSame }.count > 1
            let full = member.displayName.trimmingCharacters(in: .whitespacesAndNewlines)
            let name = homonyms ? (full.isEmpty ? first : full) : (first.isEmpty ? full : first)
            people.append(AlertContact(uid: member.uid, name: name, phone: phone))
        }
        let emergency = EmergencyCatalog.forRegion(region)
        let preselected: AlertDialTarget
        if let firstPerson = people.first {
            preselected = .person(firstPerson)
        } else {
            let fallback = emergency.first { $0.number == "112" } ?? emergency[0]
            preselected = .emergency(fallback)
        }
        return AlertCallList(people: people, emergency: emergency, preselected: preselected)
    }
}
