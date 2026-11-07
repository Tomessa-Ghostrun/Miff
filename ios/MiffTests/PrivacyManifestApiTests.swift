import XCTest
@testable import Miff

/// Le manifeste de confidentialité de l'app (`PrivacyInfo.xcprivacy`) déclare chaque API « à raison obligatoire » d'Apple
/// que le code de l'app utilise vraiment (sinon l'App Store refuse ou avertit au dépôt). Le test cherche dans les sources
/// les appels connus de chaque catégorie et compare avec ce que le manifeste déclare.
///
/// Limite : la liste des appels est celle des catégories d'Apple connues à ce jour (UserDefaults, horloge système,
/// horodatages de fichiers, espace disque, claviers actifs). Les SDK tiers (Firebase, Google, PhoneNumberKit) portent leur
/// propre manifeste, non vérifié ici.
final class PrivacyManifestApiTests: XCTestCase {
    private static let categoryPrefix = "NSPrivacyAccessedAPICategory"

    /// Catégorie -> fragments de code qui l'utilisent.
    private static let usages: [String: [String]] = [
        "SystemBootTime": ["systemUptime", "mach_absolute_time", "clock_gettime", "mach_continuous_time"],
        "UserDefaults": ["UserDefaults"],
        "FileTimestamp": [
            "creationDate", "modificationDate", "fileModificationDate", "contentModificationDateKey",
            "creationDateKey", "contentAccessDateKey", "getattrlist", "fstat(", "stat("
        ],
        "DiskSpace": [
            "volumeAvailableCapacity", "volumeTotalCapacity", "systemFreeSize", "systemSize", "statfs(", "statvfs("
        ],
        "ActiveKeyboards": ["activeInputModes"]
    ]

    /// Raison attendue pour chaque catégorie que l'app déclare.
    private static let expectedReasons: [String: [String]] = [
        "SystemBootTime": ["35F9.1"],
        "UserDefaults": ["CA92.1"]
    ]

    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    private func declared() throws -> [String: [String]] {
        let url = iosRoot.appendingPathComponent("Miff/Resources/PrivacyInfo.xcprivacy")
        let data = try Data(contentsOf: url)
        let manifest = try XCTUnwrap(
            PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any])
        let apis = try XCTUnwrap(manifest["NSPrivacyAccessedAPITypes"] as? [[String: Any]])
        var result: [String: [String]] = [:]
        for entry in apis {
            let type = entry["NSPrivacyAccessedAPIType"] as? String ?? ""
            let name = type.replacingOccurrences(of: Self.categoryPrefix, with: "")
            result[name] = entry["NSPrivacyAccessedAPITypeReasons"] as? [String] ?? []
        }
        return result
    }

    /// Les catégories dont un appel se trouve dans le code de l'app (lignes de commentaire exclues).
    private func used() throws -> [String: String] {
        let folder = iosRoot.appendingPathComponent("Miff")
        let files = try XCTUnwrap(FileManager.default.enumerator(at: folder, includingPropertiesForKeys: nil))
        var found: [String: String] = [:]
        for case let file as URL in files where file.pathExtension == "swift" {
            let text = try String(contentsOf: file, encoding: .utf8)
            let code = text.split(separator: "\n", omittingEmptySubsequences: true).filter { line in
                !line.trimmingCharacters(in: .whitespaces).hasPrefix("//")
            }
            for (category, fragments) in Self.usages where found[category] == nil {
                if let hit = fragments.first(where: { fragment in code.contains { $0.contains(fragment) } }) {
                    found[category] = "\(hit) dans \(file.lastPathComponent)"
                }
            }
        }
        return found
    }

    func testEveryRequiredReasonApiUsedByTheAppIsDeclaredInTheManifest() throws {
        let declaredCategories = try declared()

        for (category, evidence) in try used().sorted(by: { $0.key < $1.key }) {
            XCTAssertNotNil(
                declaredCategories[category],
                "\(category) est utilisée (\(evidence)) mais absente du manifeste PrivacyInfo.xcprivacy")
        }
    }

    func testTheReasonsOfTheDeclaredCategoriesAreTheExpectedOnes() throws {
        let declaredCategories = try declared()

        for (category, reasons) in Self.expectedReasons {
            XCTAssertEqual(declaredCategories[category], reasons, category)
        }
    }

    func testAnUndeclaredCategoryIsCaughtByTheSearch() throws {
        // Garde-fou du test : la recherche trouve bien l'horloge système et UserDefaults dans le code actuel.
        let categories = Set(try used().keys)

        XCTAssertTrue(categories.contains("SystemBootTime"))
        XCTAssertTrue(categories.contains("UserDefaults"))
    }

    func testNothingIsDeclaredThatTheAppDoesNotUse() throws {
        let usedCategories = Set(try used().keys)

        for category in try declared().keys {
            XCTAssertTrue(usedCategories.contains(category), "\(category) est déclarée mais plus utilisée")
        }
    }
}
