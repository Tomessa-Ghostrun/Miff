import Foundation

// Écran « Licences et attributions » (ticket `docs/tickets/reglages-a-propos.md`, §5). Le texte COMPLET de chaque licence est embarqué
// dans l'app (`Miff/Resources/Licenses/`) : l'écran s'ouvre sans réseau (critère AP12). Aucune relecture juridique (décision du PM du
// 2026-10-23, risque assumé) : l'écran ne dit jamais « conforme » ni « relu ». Les licences sont celles des fichiers LICENSE des dépôts
// amont, à la révision exacte de `Package.resolved`.

/// Un logiciel ou une police livrés dans l'app, avec sa licence.
struct LicenseEntry: Equatable, Identifiable {
    /// L'identité du paquet dans `Package.resolved` (logiciels) ou un nom court (polices) : sert au contrôle de la CI.
    let id: String
    let name: String
    /// Nil quand la source ne donne pas de numéro (police variable).
    let version: String?
    /// Le nom court de la licence (identifiant SPDX), montré sous le nom.
    let licenseName: String
    /// Le fichier texte (sans extension) dans `Miff/Resources/Licenses/`.
    let resource: String
    /// Un paquet livré sous forme de binaire : le texte montré est celui du dépôt, pas forcément les conditions du binaire.
    var isBinary = false
}

enum LicenseCatalog {
    /// Les 17 paquets Swift de `Package.resolved` (3 déclarés, 14 indirects), triés par nom. Un paquet ajouté sans entrée ici fait échouer
    /// `LicenseCatalogTests` (critère AP15) : l'écran ne peut pas devenir faux sans que la CI le dise.
    static let software: [LicenseEntry] = [
        apacheEntry("abseil-cpp-binary", "abseil-cpp-binary", "1.2025051202.0"),
        apacheEntry("app-check", "app-check", "12.0.0"),
        apacheEntry("appauth-ios", "AppAuth-iOS", "3.0.0"),
        apacheEntry("firebase-ios-sdk", "firebase-ios-sdk", "13.1.1"),
        apacheEntry(
            "google-ads-on-device-conversion-ios-sdk",
            "google-ads-on-device-conversion-ios-sdk",
            "3.7.0",
            isBinary: true
        ),
        apacheEntry("googleappmeasurement", "GoogleAppMeasurement", "13.0.1", isBinary: true),
        apacheEntry("googledatatransport", "GoogleDataTransport", "10.1.1"),
        apacheEntry("googlesignin-ios", "GoogleSignIn-iOS", "10.1.0"),
        LicenseEntry(
            id: "googleutilities", name: "GoogleUtilities", version: "8.1.5",
            licenseName: "Apache-2.0, MIT", resource: "license_googleutilities"
        ),
        apacheEntry("grpc-binary", "grpc-binary", "1.83.1"),
        apacheEntry("gtm-session-fetcher", "gtm-session-fetcher", "5.3.1"),
        apacheEntry("gtmappauth", "GTMAppAuth", "6.0.0"),
        apacheEntry("interop-ios-for-google-sdks", "interop-ios-for-google-sdks", "101.0.0"),
        LicenseEntry(
            id: "leveldb", name: "leveldb", version: "1.22.5",
            licenseName: "BSD-3-Clause", resource: "license_leveldb"
        ),
        LicenseEntry(
            id: "nanopb", name: "nanopb", version: "2.30910.1",
            licenseName: "zlib", resource: "license_nanopb"
        ),
        LicenseEntry(
            id: "phonenumberkit", name: "PhoneNumberKit", version: "5.0.13",
            licenseName: "MIT", resource: "license_phonenumberkit"
        ),
        apacheEntry("promises", "Promises", "2.4.1")
    ]

    private static func apacheEntry(
        _ id: String, _ name: String, _ version: String, isBinary: Bool = false
    ) -> LicenseEntry {
        LicenseEntry(
            id: id, name: name, version: version, licenseName: "Apache-2.0",
            resource: "license_apache_2_0", isBinary: isBinary
        )
    }

    /// Les deux polices embarquées (`ios/third_party/fonts/README.md`). SIL OFL 1.1.
    static let fonts: [LicenseEntry] = [
        LicenseEntry(
            id: "font-baloo-thambi-2", name: "Baloo Thambi 2", version: nil,
            licenseName: "SIL OFL 1.1", resource: "license_ofl_baloo_thambi_2"
        ),
        LicenseEntry(
            id: "font-hanken-grotesk", name: "Hanken Grotesk", version: "3.014",
            licenseName: "SIL OFL 1.1", resource: "license_ofl_hanken_grotesk"
        )
    ]

    static var all: [LicenseEntry] {
        software + fonts
    }

    /// Le texte complet de la licence, lu dans l'app (aucun réseau). Nil si le fichier manque : un test l'interdit.
    static func text(for entry: LicenseEntry, bundle: Bundle = .main) -> String? {
        guard let url = bundle.url(forResource: entry.resource, withExtension: "txt") else {
            return nil
        }
        return try? String(contentsOf: url, encoding: .utf8)
    }
}

/// Le contrôle de la CI (critère AP15) : chaque paquet résolu a une entrée, à la même version, et aucune entrée ne reste pour un paquet
/// disparu. Renvoie les écarts en clair ; vide = tout est en ordre.
enum LicenseAudit {
    struct Pin: Equatable {
        let identity: String
        let version: String
    }

    static func problems(pins: [Pin], entries: [LicenseEntry]) -> [String] {
        var found: [String] = []
        for pin in pins {
            guard let entry = entries.first(where: { $0.id == pin.identity }) else {
                found.append("\(pin.identity) \(pin.version) : aucune licence dans LicenseCatalog")
                continue
            }
            if entry.version != pin.version {
                let known = entry.version ?? "?"
                found.append("\(pin.identity) : \(pin.version) dans Package.resolved, \(known) dans LicenseCatalog")
            }
        }
        for entry in entries where !pins.contains(where: { $0.identity == entry.id }) {
            found.append("\(entry.id) : dans LicenseCatalog mais absent de Package.resolved")
        }
        return found
    }

    /// Les paquets de `Package.resolved` (format version 3), lus dans le fichier du dépôt.
    static func pins(resolvedJSON data: Data) -> [Pin]? {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let pins = root["pins"] as? [[String: Any]] else {
            return nil
        }
        return pins.compactMap { pin in
            guard let identity = pin["identity"] as? String,
                  let state = pin["state"] as? [String: Any],
                  let version = state["version"] as? String else {
                return nil
            }
            return Pin(identity: identity, version: version)
        }
    }
}
