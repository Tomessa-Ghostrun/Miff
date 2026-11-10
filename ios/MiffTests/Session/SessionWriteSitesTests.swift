import XCTest
@testable import Miff

/// Contrôles sur les SOURCES (comme `PrivacyManifestApiTests`) : le contrat « `deviceId` dans CHAQUE écriture de statut et de
/// position » ne tient que si aucun site d'écriture n'oublie le garde. Ces tests échouent quand un nouveau site d'écriture réel
/// (LOC-4b par exemple) n'appelle pas `SessionWriteGuard.isSendable`, ou quand une nouvelle écriture de statut ou de position
/// apparaît sans que la liste ci-dessous soit relue.
final class SessionWriteSitesTests: XCTestCase {
    private var appSources: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Session
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff")
    }

    /// Chemin relatif (depuis `Miff/`) -> lignes du fichier, sans les lignes de commentaire.
    private func sources() throws -> [String: [String]] {
        guard let files = FileManager.default.enumerator(at: appSources, includingPropertiesForKeys: nil) else {
            throw XCTSkip("Sources introuvables (\(appSources.path)).")
        }
        var result: [String: [String]] = [:]
        for case let file as URL in files where file.pathExtension == "swift" {
            let text = try String(contentsOf: file, encoding: .utf8)
            let relative = String(file.path.dropFirst(appSources.path.count + 1))
            let lines = text.components(separatedBy: "\n")
            result[relative] = lines.filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
        }
        XCTAssertGreaterThan(result.count, 100, "Presque aucune source trouvée : chemin cassé ?")
        return result
    }

    private func count(of fragment: String, in lines: [String]) -> Int {
        lines.filter { $0.contains(fragment) }.count
    }

    func testEveryRealWriteSiteOfALotCallsTheSharedGuard() throws {
        let all = try sources()
        var applyCalls = 0
        for (path, lines) in all {
            // L'appel `FirestoreMiffStore.apply(` (la définition est `static func apply(`) envoie un lot.
            let sends = count(of: "FirestoreMiffStore.apply(", in: lines)
            let guards = count(of: "SessionWriteGuard.isSendable(", in: lines)
            if path.hasSuffix("SessionWriteGuard.swift") {
                continue
            }
            XCTAssertEqual(sends, guards, "\(path) : \(sends) envois de lot, \(guards) gardes")
            applyCalls += sends
        }
        // UPDATE-3 : la transaction de l'Update (`FirestorePhotoStore`, photo + statut dans le même lot) est le cinquième site ; elle appelle
        // le même garde.
        XCTAssertEqual(applyCalls, 5, "cinq sites d'écriture réels")
    }

    func testOnlyTheKnownFilesWriteDocumentsDirectly() throws {
        let all = try sources()
        let allowed: Set<String> = [
            "Services/Firebase/FirestoreMiffStore.swift",
            "Services/Firebase/FirestoreDeviceSessionRemote.swift",
            "Services/Firebase/FirestoreUserProfileRepository.swift"
        ]
        let writers = all.filter { _, lines in
            lines.contains { $0.contains("setData(") || $0.contains("updateData(") || $0.contains(".deleteDocument(") }
        }
        XCTAssertEqual(Set(writers.keys), allowed, "nouveau fichier qui écrit dans Firestore : garde obligatoire")
    }

    func testTheStatusAndPositionWritesThatExistAreTheOnesTheGuardKnows() throws {
        let all = try sources()
        var found: [String: Int] = [:]
        for (path, lines) in all {
            for (index, line) in lines.enumerated() where line.contains("/status/") || line.contains("/positions/") {
                let window = lines[index..<min(index + 4, lines.count)].joined(separator: " ")
                let pattern = "kind: \\.(set|update|delete)"
                guard let kindRange = window.range(of: pattern, options: .regularExpression) else {
                    continue
                }
                if !window[kindRange].hasSuffix("delete") {
                    found[path, default: 0] += 1
                }
            }
        }
        // Créer, rejoindre et reprendre le partage : trois statuts initiaux, tous estampillés par `initialStatus`. LOC-4b : la mise à
        // jour automatique du statut, la mise à jour de l'accès, le rafraîchissement de la batterie (ouverture / sortie de l'app) et la
        // dernière position (`TrackingWrites`), toutes estampillées par
        // `SessionWriteGuard.stamped`. UPDATE-3 : le statut de la photo (`UpdateWrites.photoStatus`, estampillé lui aussi). MODE VOYAGE : le début
        // d'un voyage et le changement de sa fin (`TrackingWrites.travelStart`, `travelDate`, estampillés eux aussi). Toute autre écriture de
        // statut ou de position doit s'ajouter ICI après relecture.
        let expected = [
            "Models/Miff/MiffWrites.swift": 3,
            "Models/Tracking/TrackingWrites.swift": 6,
            "Models/Update/UpdateWrites.swift": 1
        ]
        XCTAssertEqual(found, expected)
    }

    func testOnlyTheKnownFilesBuildStatusAndPositionFields() throws {
        let all = try sources()
        let builders = all.filter { _, lines in
            lines.contains { $0.contains("SessionWriteGuard.stamped(") }
        }
        XCTAssertEqual(
            Set(builders.keys),
            [
                "Models/Miff/MiffWrites.swift", "Models/Tracking/TrackingWrites.swift",
                "Models/Update/UpdateWrites.swift"
            ],
            "les champs d'un statut ou d'une position : stamped"
        )
    }
}
