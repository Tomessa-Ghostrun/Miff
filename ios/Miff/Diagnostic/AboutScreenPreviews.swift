#if DEBUG
import SwiftUI

/// Réglages > « À propos et aide », ses licences et la liste des Réglages, seuls, pour les captures de la CI
/// (`-MiffPreview about-screen`, `about-restricted`, `about-licenses`, `about-licenses-open`, `about-settings`, `about-settings-restricted`).
/// Les vrais écrans ; aucun réseau, aucune session, aucune écriture. Version de démonstration : 0.1.0 (12).
struct AboutScreenPreview: View {
    let name: String

    private let demoBuild = BuildInfo(version: "0.1.0", build: "12", configuration: "Debug")

    private var aboutActions: AboutActions {
        AboutActions(onBack: {}, onOpenDetectionHelp: {}, onOpenNotificationsHelp: {}, onOpenLicenses: {})
    }

    var body: some View {
        switch name {
        case "about-licenses":
            LicensesView(onBack: {})
        case "about-licenses-open":
            LicensesView(onBack: {}, initiallyOpen: "firebase-ios-sdk")
        case "about-settings", "about-settings-restricted":
            SettingsListView(
                actions: SettingsActions(
                    onBack: {},
                    onOpenMyInfo: {},
                    onOpenAccount: {},
                    onOpenLanguage: {},
                    onOpenPrivacy: {},
                    onOpenNotifications: {}
                ),
                restricted: name == "about-settings-restricted"
            )
        case "about-restricted":
            AboutView(actions: aboutActions, build: demoBuild, restricted: true)
        default:
            AboutView(actions: aboutActions, build: demoBuild)
        }
    }
}
#endif
