import XCTest

/// Vérifie, dans l'app réellement construite, les réglages sensibles exigés par le ticket
/// « Initialisation technique » (critères I1, I4, I5, I8). Les tests de l'app tournent dans
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

    func testLightAppearanceIsForced() {
        // Pas de mode sombre dans les maquettes : l'app reste claire même si le téléphone est en mode sombre.
        XCTAssertEqual(info["UIUserInterfaceStyle"] as? String, "Light")
    }

    // MARK: - Confidentialité (critères I4 et I5)

    func testNoLocationPermissionIsDeclared() {
        let locationKeys = info.keys.filter { $0.hasPrefix("NSLocation") }

        XCTAssertTrue(locationKeys.isEmpty, "Clés de localisation inattendues : \(locationKeys)")
    }

    func testNoATSExceptionIsDeclared() {
        XCTAssertNil(info["NSAppTransportSecurity"])
    }

    func testAnalyticsAndCrashlyticsCollectNothingAtStartup() {
        XCTAssertEqual(info["FIREBASE_ANALYTICS_COLLECTION_ENABLED"] as? Bool, false)
        XCTAssertEqual(info["FirebaseCrashlyticsCollectionEnabled"] as? Bool, false)
    }

    func testTheIrreversibleDeactivationKeyIsNotUsed() {
        // La clé « DEACTIVATED » ne peut pas être annulée à l'exécution : on utilise « ENABLED = NO ».
        XCTAssertNil(info["FIREBASE_ANALYTICS_COLLECTION_DEACTIVATED"])
    }

    func testNoAdvertisingOrIdentifierCollection() {
        XCTAssertEqual(info["GOOGLE_ANALYTICS_IDFV_COLLECTION_ENABLED"] as? Bool, false)
        XCTAssertEqual(info["GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS"] as? Bool, false)
        XCTAssertEqual(info["GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_STORAGE"] as? Bool, false)
        XCTAssertEqual(info["GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_USER_DATA"] as? Bool, false)
        XCTAssertEqual(info["GOOGLE_ANALYTICS_REGISTRATION_WITH_AD_NETWORK_ENABLED"] as? Bool, false)
    }

    func testAutomaticScreenReportingIsOff() {
        // Aucun nom d'écran dans Analytics (docs/contracts/analytics-events.md).
        XCTAssertEqual(info["FirebaseAutomaticScreenReportingEnabled"] as? Bool, false)
    }

    func testPrivacyManifestIsBundled() {
        XCTAssertNotNil(Bundle.main.url(forResource: "PrivacyInfo", withExtension: "xcprivacy"))
    }

    func testPrivacyManifestIsCoherent() throws {
        let url = try XCTUnwrap(Bundle.main.url(forResource: "PrivacyInfo", withExtension: "xcprivacy"))
        let data = try Data(contentsOf: url)
        let manifest = try XCTUnwrap(
            PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any])

        XCTAssertEqual(manifest["NSPrivacyTracking"] as? Bool, false)
        XCTAssertEqual((manifest["NSPrivacyTrackingDomains"] as? [Any])?.count, 0)

        // L'app enregistre le choix de consentement dans UserDefaults : API à raison requise, déclarée.
        let apis = try XCTUnwrap(manifest["NSPrivacyAccessedAPITypes"] as? [[String: Any]])
        let category = "NSPrivacyAccessedAPICategoryUserDefaults"
        let userDefaults = apis.first { $0["NSPrivacyAccessedAPIType"] as? String == category }
        XCTAssertEqual(userDefaults?["NSPrivacyAccessedAPITypeReasons"] as? [String], ["CA92.1"])

        // Aucune position déclarée comme collectée.
        let collected = try XCTUnwrap(manifest["NSPrivacyCollectedDataTypes"] as? [[String: Any]])
        let types = collected.compactMap { $0["NSPrivacyCollectedDataType"] as? String }
        XCTAssertFalse(types.isEmpty)
        XCTAssertTrue(types.allSatisfy { !$0.contains("Location") }, "\(types)")
        // Rien n'est lié à l'identité de l'utilisateur ni utilisé pour du suivi.
        XCTAssertTrue(collected.allSatisfy { $0["NSPrivacyCollectedDataTypeLinked"] as? Bool == false })
        XCTAssertTrue(collected.allSatisfy { $0["NSPrivacyCollectedDataTypeTracking"] as? Bool == false })
    }

    // MARK: - Langues

    func testEnglishIsTheFallbackAndFrenchAndEnglishAreDeclared() {
        XCTAssertEqual(info["CFBundleDevelopmentRegion"] as? String, "en")
        XCTAssertEqual(Set(info["CFBundleLocalizations"] as? [String] ?? []), ["fr", "en"])
    }
}
