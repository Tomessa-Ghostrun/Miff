import Foundation

// SRV-2I : la FORME EXACTE du document `users/{uid}/devices/{deviceId}` (contrat `docs/dev/jetons-appareil-clients.md` §2 ; règles
// `backend/firestore.rules`, bloc `devices`). Sept clés, ni plus ni moins : `token`, `platform`, `appVersion`, `tz`, `notify.updates`,
// `updatedAt`, `createdAt`. Interdit d'y ajouter quoi que ce soit (position, nom, e-mail, identifiant publicitaire, identifiant de Miff, nom
// d'appareil, langue, état de l'autorisation) : le document serait refusé. Les dates sont celles du SERVEUR.
//
// SÉCURITÉ : le jeton est ici et dans la base, lisible par son propriétaire seul (règles), jamais ailleurs : aucune description texte ne le montre.

/// Ce que ce téléphone dit de lui-même.
struct DeviceEntry: Equatable, CustomStringConvertible {
    static let platform = "ios"

    let token: String
    let timeZoneId: String
    let notifyUpdates: Bool
    let appVersion: String

    /// nil quand un champ sort des limites des règles : rien n'est alors écrit.
    static func make(token: String, timeZoneId: String, notifyUpdates: Bool, appVersion: String) -> DeviceEntry? {
        guard (1...DeviceEntryLimits.maxTokenChars).contains(token.count),
              TimeZoneShape.isValid(timeZoneId),
              (1...DeviceEntryLimits.maxAppVersionChars).contains(appVersion.count) else {
            return nil
        }
        return DeviceEntry(token: token, timeZoneId: timeZoneId, notifyUpdates: notifyUpdates, appVersion: appVersion)
    }

    var description: String {
        "DeviceEntry(redacted)"
    }
}

enum DeviceDocument {
    /// Les sept clés d'un document complet.
    static let keys: Set<String> = ["token", "platform", "appVersion", "tz", "notify", "updatedAt", "createdAt"]

    /// `users/{uid}/devices/{deviceId}`, ou nil si l'un des deux identifiants n'a pas la forme attendue (un compte sans « / », un identifiant de
    /// session de 16 à 64 caractères).
    static func path(uid: String, deviceId: String) -> String? {
        guard !uid.isEmpty, !uid.contains("/"), DeviceId.isValid(deviceId) else {
            return nil
        }
        return "users/\(uid)/devices/\(deviceId)"
    }

    /// La CRÉATION : les sept clés, `createdAt` et `updatedAt` à l'heure du serveur.
    static func creation(_ entry: DeviceEntry) -> [String: FieldValueSpec] {
        var fields = changing(entry)
        fields["platform"] = .string(DeviceEntry.platform)
        fields["createdAt"] = .serverTimestamp
        return fields
    }

    /// La MISE À JOUR : tout sauf `createdAt` et `platform`, que la règle exige inchangés (un `set` complet sur un document existant
    /// porterait un `createdAt` neuf et serait refusé).
    static func update(_ entry: DeviceEntry) -> [String: FieldValueSpec] {
        changing(entry)
    }

    private static func changing(_ entry: DeviceEntry) -> [String: FieldValueSpec] {
        [
            "token": .string(entry.token),
            "appVersion": .string(entry.appVersion),
            "tz": .string(entry.timeZoneId),
            "notify": .map(["updates": .bool(entry.notifyUpdates)]),
            "updatedAt": .serverTimestamp
        ]
    }
}
