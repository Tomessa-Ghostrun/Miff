import XCTest

/// Vérifie, dans l'app réellement construite, les réglages sensibles exigés par le ticket
/// « Initialisation technique » (critères I1 et I4). Les tests de l'app tournent dans
/// le processus de l'app : `Bundle.main` est donc le bundle de Miff.
final class AppConfigurationTests: XCTestCase {
    private var info: [String: Any] {
        Bundle.main.infoDictionary ?? [:]
    }

    func testBundleIdentifierIsTheOfficialOne() {
        XCTAssertEqual(Bundle.main.bundleIdentifier, "com.tomessa.miff")
    }

    func testDisplayNameIsMiff() {
        XCTAssertEqual(info["CFBundleDisplayName"] as? String, "Miff")
    }

    func testMinimumOSVersionIsIOS16() throws {
        // Xcode ajoute cette clé à l'Info.plist du build (valeur de IPHONEOS_DEPLOYMENT_TARGET).
        // Confirmé par la CI (test vert).
        let minimum = try XCTUnwrap(info["MinimumOSVersion"] as? String)

        XCTAssertEqual(minimum, "16.0")
    }

    func testAppIsIPhoneOnly() throws {
        // 1 = iPhone, 2 = iPad. Xcode écrit UIDeviceFamily dans l'Info.plist du build à partir
        // de TARGETED_DEVICE_FAMILY (qui doit être posé au niveau de la cible, voir project.yml).
        let family = try XCTUnwrap(info["UIDeviceFamily"] as? [Int])

        XCTAssertEqual(family, [1])
    }

    func testEncryptionExemptionIsDeclared() {
        // Décision PM 2026-10-22 : chiffrement standard uniquement (exempté).
        XCTAssertEqual(info["ITSAppUsesNonExemptEncryption"] as? Bool, false)
    }

    func testNoLocationPermissionIsDeclared() {
        let locationKeys = info.keys.filter { $0.hasPrefix("NSLocation") }

        XCTAssertTrue(locationKeys.isEmpty, "Clés de localisation inattendues : \(locationKeys)")
    }

    func testNoATSExceptionIsDeclared() {
        XCTAssertNil(info["NSAppTransportSecurity"])
    }

    func testPrivacyManifestIsBundled() {
        XCTAssertNotNil(Bundle.main.url(forResource: "PrivacyInfo", withExtension: "xcprivacy"))
    }
}
