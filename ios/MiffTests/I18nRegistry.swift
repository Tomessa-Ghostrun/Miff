import XCTest

/// Le registre des langues de Miff (`docs/contracts/i18n-languages.json`, source unique : ticket I18N-4L) lu par les tests iOS. Les tests de
/// contenu (mots interdits, tutoiement, valeurs insérées, pluriels...) bouclent sur ces langues au lieu d'écrire « fr » et « en » en dur :
/// quand une langue a ses textes, les règles s'appliquent à elle sans qu'il faille retoucher un test (critère 24 du ticket).
enum I18nRegistry {
    struct Language: Equatable {
        /// Le code iOS (dossier `xx.lproj`, langue du catalogue) : `fr`, `en`, `es`, `it`, `pt`, `de`.
        let code: String
        /// Le nom de la langue écrit dans sa langue (Réglages > Langue).
        let nativeName: String
        /// `required` : livrée, tout écart bloque ; `planned` : en préparation (textes pas encore dans l'app, aucune pour iOS depuis le lot 4).
        let status: String
        /// Les formes de pluriel que la langue connaît, et celles qui sont obligatoires.
        let pluralsAllowed: Set<String>
        let pluralsRequired: Set<String>
    }

    private struct RegistryError: Error, CustomStringConvertible {
        let description: String
    }

    /// Le dépôt, remonté depuis ce fichier (`ios/MiffTests/I18nRegistry.swift`). Le registre fait partie du dépôt : s'il manque, le test échoue
    /// (un test de contenu qui se passerait en silence de ses langues ne protégerait plus rien).
    static var registryURL: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .deletingLastPathComponent() // dépôt
            .appendingPathComponent("docs/contracts/i18n-languages.json")
    }

    /// Toutes les langues du registre, dans son ordre (source, repli, puis les langues à venir).
    static func languages() throws -> [Language] {
        let data = try Data(contentsOf: registryURL)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let entries = try XCTUnwrap(root["languages"] as? [[String: Any]])
        return try entries.map { entry in
            let platforms = try XCTUnwrap(entry["platforms"] as? [String: Any])
            let ios = try XCTUnwrap(platforms["ios"] as? [String: Any])
            let plurals = try XCTUnwrap(entry["plurals"] as? [String: Any])
            guard let code = ios["code"] as? String, let name = entry["nativeName"] as? String else {
                throw RegistryError(description: "Entrée de langue incomplète dans le registre")
            }
            return Language(
                code: code,
                nativeName: name,
                status: ios["status"] as? String ?? "planned",
                pluralsAllowed: Set(plurals["allowed"] as? [String] ?? []),
                pluralsRequired: Set(plurals["required"] as? [String] ?? [])
            )
        }
    }

    /// Les clés dont le registre dispense cette langue (exceptions `check: "missing-key"`, par clé ou par motif) : l'écran de diagnostic
    /// (`diag_*`, `ios_diag_*`) n'est pas traduit en es, it, pt, de ; il retombe sur l'anglais (`L10n.string`). Renvoie un test de clé.
    static func missingKeyExemption(language: String) throws -> (String) -> Bool {
        let data = try Data(contentsOf: registryURL)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let exceptions = (root["exceptions"] as? [[String: Any]] ?? []).filter {
            $0["check"] as? String == "missing-key" && $0["lang"] as? String == language
        }
        let keys = Set(exceptions.compactMap { $0["key"] as? String })
        let patterns = exceptions.compactMap { $0["keyPattern"] as? String }
        return { key in
            keys.contains(key) || patterns.contains { key.range(of: $0, options: .regularExpression) != nil }
        }
    }

    /// Les codes iOS de toutes les langues du registre.
    static func codes() throws -> [String] {
        try languages().map { $0.code }
    }

    /// Les codes iOS des langues `required` du registre : celles dont le catalogue a les textes (les six depuis le lot 4 iOS du ticket I18N-4L).
    static func requiredCodes() throws -> [String] {
        try languages().filter { $0.status == "required" }.map { $0.code }
    }

    /// Les langues dont l'app A les textes : leur `xx.lproj` est dans l'app (un catalogue sans aucun texte pour une langue n'en produit pas).
    /// C'est sur elles que les tests de contenu bouclent : les six langues depuis le lot 4 iOS.
    static func shipped() throws -> [String] {
        let found = try codes().filter { Bundle.main.path(forResource: $0, ofType: "lproj") != nil }
        XCTAssertTrue(Set(["fr", "en"]).isSubset(of: found), "fr et en doivent toujours être dans l'app : \(found)")
        return found
    }
}
