import XCTest
@testable import Miff

/// CNC-I : le drapeau `NOTIF_JOURNAL` (comme `BuildConfig.NOTIF_JOURNAL` d'Android) et ce que le code du journal ne contient jamais. Lit les sources et la
/// configuration, pour qu'un remaniement ne puisse pas allumer la lecture du serveur dans une publication, ni ajouter une donnée personnelle au journal,
/// sans qu'un test échoue ici.
final class NotificationJournalFlagTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Notification
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    private func read(_ relative: String) throws -> String {
        try String(contentsOf: iosRoot.appendingPathComponent(relative), encoding: .utf8)
    }

    /// Les lignes de code d'un fichier : ni commentaires, ni lignes vides.
    private func code(_ relative: String) throws -> [String] {
        try read(relative).components(separatedBy: "\n")
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty && !$0.hasPrefix("//") }
    }

    func testTheFlagIsInTheBuildThisTestRunsIn() {
        let configuration = BuildInfo.current.configuration
        let expected = configuration == "Debug" || configuration == "Internal"

        XCTAssertEqual(BuildFlavor.includesNotificationJournal, expected, "Configuration : \(configuration)")
    }

    func testTheFlagIsInTheConditionsOfDebugAndInternalAndNotInRelease() throws {
        let flagged = try read("project.yml").components(separatedBy: "\n")
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.hasPrefix("#") && $0.contains("NOTIF_JOURNAL") }

        XCTAssertEqual(
            flagged,
            [
                "SWIFT_ACTIVE_COMPILATION_CONDITIONS: DEBUG REVIEW_PROMPT ALERT_DANGER NOTIF_JOURNAL",
                "SWIFT_ACTIVE_COMPILATION_CONDITIONS: INTERNAL REVIEW_PROMPT ALERT_DANGER NOTIF_JOURNAL"
            ],
            "NOTIF_JOURNAL ne doit être que dans Debug et Internal (jamais Release, sauf décision du PM)"
        )
    }

    func testTheFirestoreGatewayOfTheJournalIsEntirelyBehindTheFlag() throws {
        let lines = try code("Miff/Services/Firebase/FirestoreNotificationJournal.swift")

        XCTAssertEqual(lines.first, "#if NOTIF_JOURNAL")
        XCTAssertEqual(lines.last, "#endif")
    }

    func testTheOnlyUseOfTheGatewayOutsideItsFileIsBehindTheFlag() throws {
        let services = try read("Miff/App/NotificationServices.swift")

        let declaration = "let gateway: NotificationJournalGateway? ="
        XCTAssertTrue(services.contains("#if NOTIF_JOURNAL\n        \(declaration) FirestoreNotificationJournal()"))
        XCTAssertTrue(services.contains("#else\n        \(declaration) nil"))
        XCTAssertEqual(
            services.components(separatedBy: "FirestoreNotificationJournal()").count - 1, 1,
            "un seul endroit construit la passerelle"
        )
    }

    func testTheBinaryCheckKnowsTheJournalAndItsEscapeHatch() throws {
        let script = try read("ci/check-binaries.sh")

        XCTAssertTrue(script.contains("MIFF_ALLOW_NOTIF_JOURNAL_IN_RELEASE"))
        XCTAssertTrue(script.contains("FirestoreNotificationJournal"))
        XCTAssertTrue(script.contains("journal_hits"))
    }

    func testOnlyTheGatewayTalksToTheJournalCollection() throws {
        let app = iosRoot.appendingPathComponent("Miff")
        let files = try XCTUnwrap(FileManager.default.enumerator(at: app, includingPropertiesForKeys: nil))
        var users: [String] = []
        for case let file as URL in files where file.pathExtension == "swift" {
            let relative = String(file.path.dropFirst(app.path.count + 1))
            let text = try String(contentsOf: file, encoding: .utf8)
            let lines = text.components(separatedBy: "\n")
                .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
            if lines.contains(where: { $0.contains("NotificationJournal.collection") }) {
                users.append(relative)
            }
        }

        XCTAssertEqual(users, ["Services/Firebase/FirestoreNotificationJournal.swift"])
    }

    // MARK: - Aucune donnée personnelle de plus (point (d) de la demande, critère CNC-13)

    func testAnEntryHoldsExactlyTheSixThingsOfTheContractAndNoNamePhotoPlaceOrPosition() {
        let entry = JournalEntry(
            entryId: "u-m-uidB-1", kind: .update, miffId: "m", actorUid: "uidB", createdAtMillis: 1, expireAtMillis: 2
        )

        let fields = Mirror(reflecting: entry).children.compactMap { $0.label }

        XCTAssertEqual(
            fields, ["entryId", "kind", "miffId", "actorUid", "createdAtMillis", "expireAtMillis"],
            "l'identifiant du document, puis les cinq champs lus (`v` est vérifié, jamais gardé)"
        )
    }

    /// Le code des visages (photo, prénom) ne garde rien sur le téléphone, ne journalise rien et n'envoie aucun événement : en mémoire seulement.
    func testTheCodeOfTheFacesStaysInMemoryAndLogsNothing() throws {
        let app = iosRoot.appendingPathComponent("Miff")
        let folders = ["Models/CenterFaces", "Services/CenterFaces", "Views/CenterFaces"]
        var files = ["Services/Firebase/FirestoreMemberFaceReader.swift"]
        for folder in folders {
            let found = try FileManager.default.contentsOfDirectory(atPath: app.appendingPathComponent(folder).path)
            files += found.filter { $0.hasSuffix(".swift") }.map { "\(folder)/\($0)" }
        }
        let forbidden = [
            "FileManager", "UserDefaults", ".write(to", "print(", "NSLog", "Logger", "Analytics",
            "Crashlytics", "logEvent", "Keychain", "dump("
        ]

        XCTAssertGreaterThanOrEqual(files.count, 4)
        for file in files {
            for line in try code("Miff/" + file) {
                for word in forbidden where line.contains(word) {
                    XCTFail("\(file) : « \(word) » interdit dans le code des visages (\(line))")
                }
            }
        }
    }

    func testTheCodeOfTheJournalNeverMentionsANamePhotoPlaceOrPosition() throws {
        let files = [
            "Miff/Models/Notification/NotificationJournal.swift",
            "Miff/Services/Notification/NotificationJournalSync.swift",
            "Miff/Services/Notification/NotificationJournalCursorFileStore.swift",
            "Miff/Services/Firebase/FirestoreNotificationJournal.swift"
        ]
        let forbidden = [
            "firstName", "lastName", "avatar", "photo", "jpeg", "latitude", "longitude", "coordinate", "address",
            "placeName", "position", "phone", "print(", "NSLog", "Logger(", "Analytics", "Crashlytics"
        ]
        for file in files {
            for line in try code(file) {
                for word in forbidden where line.contains(word) {
                    XCTFail("\(file) : « \(word) » interdit dans le code du journal (\(line))")
                }
            }
        }
    }
}
