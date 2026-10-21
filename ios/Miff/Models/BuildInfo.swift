import Foundation

/// Identité du build affichée à l'écran : version marketing et numéro de build.
///
/// Les valeurs viennent de l'`Info.plist` (générée par XcodeGen à partir de
/// `MARKETING_VERSION` et `CURRENT_PROJECT_VERSION`). Le numéro de build est injecté
/// par la CI (numéro d'exécution du workflow), ce qui permet de reconnaître sur
/// l'iPhone quel build TestFlight on est en train de regarder.
struct BuildInfo: Equatable {
    /// Valeur affichée quand la clé est absente ou vide : l'écran ne plante jamais.
    static let placeholder = "?"

    let version: String
    let build: String

    init(version: String, build: String) {
        self.version = BuildInfo.clean(version)
        self.build = BuildInfo.clean(build)
    }

    init(infoDictionary: [String: Any]?) {
        self.init(
            version: infoDictionary?["CFBundleShortVersionString"] as? String ?? "",
            build: infoDictionary?["CFBundleVersion"] as? String ?? ""
        )
    }

    /// Build en cours d'exécution (celui de l'app, pas celui du bundle de tests).
    static var current: BuildInfo {
        BuildInfo(infoDictionary: Bundle.main.infoDictionary)
    }

    /// Exemple : « Version 0.1.0 · build 12 ».
    var summary: String {
        "Version \(version) · build \(build)"
    }

    private static func clean(_ value: String) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? placeholder : trimmed
    }
}
