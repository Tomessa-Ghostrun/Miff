import XCTest
@testable import Miff

/// Garde-fou : tout fichier de l'app qui garde quelque chose sur le téléphone (réglages, fichier privé) doit avoir été relu pour la
/// suppression d'un compte. Ce test échoue quand un NOUVEAU stockage local apparaît : il faut alors l'effacer à la suppression
/// (`AccountLocalData`, `SessionTeardown` ou `SessionManager.eraseAfterAccountDeletion`) ou décider qu'il ne contient rien de
/// personnel, puis l'ajouter ici avec sa raison.
final class AccountLocalStorageGuardTests: XCTestCase {
    /// Chaque stockage connu, avec ce qui l'efface (ou pourquoi il reste).
    private let known: [String: String] = [
        "App/AppEnvironment.swift": "branchement des réglages ; rien de plus",
        "App/AppEnvironment+AccountDeletion.swift": "branchement des effaceurs (AccountLocalData)",
        "Services/Notification/NotificationCenterFileStore.swift": "centre : teardown, AccountLocalData",
        "Services/Notification/NotificationDefaultsStores.swift": "choix : teardown ; carte : AccountLocalData",
        "Services/Notification/NotificationPlanFileStore.swift": "instantané et registre : teardown, AccountLocalData",
        "Services/Notification/NotificationLocalData.swift": "regroupe et efface les stockages des notifications",
        "App/PushServices.swift": "branchement des notifications push ; effaceurs : PushServices.erasers",
        "Services/Permission/DevicePermissions.swift": "drapeau par compte : AccountLocalData (notificationAsks)",
        "Services/PrivacySettings.swift": "choix Analytics / plantages : SessionManager (eraseCollectedData)",
        "Services/Push/PushLocalFileStore.swift": "mémoire de l'enregistrement : PushRegistrar.forget, erasers",
        "Services/Push/PushPreferenceStores.swift": "accord et interrupteur par compte : PushServices.erasers",
        "Services/Storage/AccountDeletionFileMarker.swift": "le marqueur lui-même : effacé en dernier",
        "Services/Storage/ActiveMiffStore.swift": "Miff active : AccountLocalData (activeMiff)",
        "Services/Storage/DeviceIdFileStore.swift": "session d'appareil : DeviceSession.forget",
        "Services/Storage/LocalStores.swift": "consentement en attente : pending.clear ; drapeaux d'onboarding gardés",
        "Services/Storage/PendingDeletionFileStore.swift": "marqueurs de suppression de Miff : AccountLocalData",
        "Services/Storage/ProfileDraftFileStore.swift": "brouillon du profil : drafts.clear",
        "Services/Update/PublishLedger.swift": "carnet de l'Update : AccountLocalData (updateLedger)",
        "Services/Tracking/FileDetectionJournal.swift": "journal : AccountLocalData (detectionJournal)",
        "Services/Tracking/TrackingLocalFileStore.swift": "copie locale du suivi : AccountLocalData (trackingLocal)",
        "Services/Review/ReviewStateFileStore.swift": "état de l'avis (dates, issue) : teardown (ReviewPromptServices)"
    ]

    func testEveryLocalStorageHasBeenReadForTheDeletionOfAnAccount() throws {
        let app = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Account
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff")
        guard let files = FileManager.default.enumerator(at: app, includingPropertiesForKeys: nil) else {
            throw XCTSkip("Sources introuvables.")
        }
        let markers = [
            "UserDefaults", "applicationSupportDirectory", "cachesDirectory", "documentDirectory", "Keychain", "SecItem"
        ]
        var found = Set<String>()
        for case let file as URL in files where file.pathExtension == "swift" {
            let relative = String(file.path.dropFirst(app.path.count + 1))
            if relative.hasPrefix("Diagnostic/") {
                continue
            }
            let text = try String(contentsOf: file, encoding: .utf8)
            let lines = text.components(separatedBy: "\n")
            let code = lines.filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
            if code.contains(where: { line in markers.contains { line.contains($0) } }) {
                found.insert(relative)
            }
        }

        XCTAssertEqual(found, Set(known.keys), "nouveau stockage local : le relire pour la suppression du compte")
    }
}
