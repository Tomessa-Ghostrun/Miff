import Foundation

/// Identité du build affichée à l'écran : version marketing, numéro de build et configuration Xcode.
///
/// Les valeurs viennent de l'`Info.plist` (générée par XcodeGen à partir de `MARKETING_VERSION`,
/// `CURRENT_PROJECT_VERSION` et `CONFIGURATION`). Le numéro de build est injecté par la CI (numéro
/// d'exécution du workflow), ce qui permet de reconnaître sur l'iPhone quel build TestFlight on regarde.
struct BuildInfo: Equatable {
    /// Valeur affichée quand la clé est absente ou vide : l'écran ne plante jamais.
    static let placeholder = "?"

    let version: String
    let build: String
    let configuration: String

    init(version: String, build: String, configuration: String = BuildInfo.placeholder) {
        self.version = BuildInfo.clean(version)
        self.build = BuildInfo.clean(build)
        self.configuration = BuildInfo.clean(configuration)
    }

    init(infoDictionary: [String: Any]?) {
        self.init(
            version: infoDictionary?["CFBundleShortVersionString"] as? String ?? "",
            build: infoDictionary?["CFBundleVersion"] as? String ?? "",
            configuration: infoDictionary?["MiffBuildConfiguration"] as? String ?? ""
        )
    }

    /// Build en cours d'exécution (celui de l'app, pas celui du bundle de tests).
    static var current: BuildInfo {
        BuildInfo(infoDictionary: Bundle.main.infoDictionary)
    }

    private static func clean(_ value: String) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? placeholder : trimmed
    }
}

/// Ce que contient CE build : le diagnostic interne n'existe que dans les configurations Debug et Internal
/// (compilation conditionnelle : en Release, son code n'est même pas compilé).
enum BuildFlavor {
    static var includesDiagnostic: Bool {
        #if DEBUG || INTERNAL
        return true
        #else
        return false
        #endif
    }
}
