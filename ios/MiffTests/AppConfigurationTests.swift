import XCTest
@testable import Miff

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

    func testExactlyTheTwoLocationUsageTextsAreDeclaredAndNothingElseOfTheKind() {
        // LOC-4a (critère IOS1) : « pendant l'utilisation » et « toujours », rien d'autre. Pas l'ancienne clé « Always » seule,
        // pas de clé de précision temporaire.
        let locationKeys = info.keys.filter { $0.hasPrefix("NSLocation") }

        XCTAssertEqual(
            Set(locationKeys),
            ["NSLocationWhenInUseUsageDescription", "NSLocationAlwaysAndWhenInUseUsageDescription"],
            "Clés de localisation inattendues : \(locationKeys)"
        )
        for key in locationKeys {
            let text = info[key] as? String ?? ""
            XCTAssertFalse(text.isEmpty, "\(key) : texte d'usage vide")
            // Le texte dit la dernière position connue (dans la langue du téléphone de test).
            XCTAssertTrue(
                text.contains("last known location") || text.contains("dernière position connue"),
                "\(key) : la dernière position connue doit être dite"
            )
        }
    }

    func testExactlyTheCameraUsageTextIsDeclaredAndOnlyTheAddOnlyPhotoLibraryKey() {
        // UPDATE-3 : la caméra du viseur de l'Update. La photo est prise en mémoire et n'est jamais écrite dans la photothèque.
        let cameraKeys = info.keys.filter { $0.hasPrefix("NSCamera") }

        XCTAssertEqual(Set(cameraKeys), ["NSCameraUsageDescription"], "Clés de caméra inattendues : \(cameraKeys)")
        XCTAssertFalse((info["NSCameraUsageDescription"] as? String ?? "").isEmpty, "texte d'usage de la caméra vide")
        // FEED-8 : EXACTEMENT la clé d'AJOUT à la galerie (« Enregistrer cette photo », au toucher), jamais une clé de lecture.
        XCTAssertEqual(Set(info.keys.filter { $0.hasPrefix("NSPhotoLibrary") }), ["NSPhotoLibraryAddUsageDescription"])
        XCTAssertFalse(
            (info["NSPhotoLibraryAddUsageDescription"] as? String ?? "").isEmpty, "texte d'usage vide"
        )
    }

    func testNoBackgroundModeIsDeclared() {
        // Le mode « location » est pour les mises à jour continues de position : Miff n'en fait pas (guideline 2.5.4).
        XCTAssertNil(info["UIBackgroundModes"])
    }

    func testTheLocationBlockingSwitchOfThisBuildIsOnByDefault() {
        XCTAssertEqual(info["MiffLocationBlocking"] as? String, "YES")
        XCTAssertTrue(LocationBlockingSwitch.isBlocking(info: info))
    }

    func testNoATSExceptionIsDeclared() {
        XCTAssertNil(info["NSAppTransportSecurity"])
    }

    func testAnalyticsAndCrashlyticsCollectNothingAtStartup() {
        XCTAssertEqual(info["FIREBASE_ANALYTICS_COLLECTION_ENABLED"] as? Bool, false)
        XCTAssertEqual(info["FirebaseCrashlyticsCollectionEnabled"] as? Bool, false)
        // Filet de sécurité : collecte par défaut éteinte pour l'ensemble des SDK Firebase.
        XCTAssertEqual(info["FirebaseDataCollectionDefaultEnabled"] as? Bool, false)
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

        // LOC-4a : la position précise (dernière position connue) est déclarée, liée, pour le fonctionnement de l'app, sans suivi.
        let collected = try XCTUnwrap(manifest["NSPrivacyCollectedDataTypes"] as? [[String: Any]])
        let types = collected.compactMap { $0["NSPrivacyCollectedDataType"] as? String }
        XCTAssertFalse(types.isEmpty)
        XCTAssertEqual(
            types.filter { $0.contains("Location") },
            ["NSPrivacyCollectedDataTypePreciseLocation"],
            "\(types)"
        )
        // Rien n'est utilisé pour du suivi.
        XCTAssertTrue(collected.allSatisfy { $0["NSPrivacyCollectedDataTypeTracking"] as? Bool == false })
        // Les données de COMPTE (inscription et profil) sont liées à l'identité, pour le fonctionnement de l'app seulement ;
        // les données d'USAGE (Analytics, rapports de plantage) ne le sont jamais.
        let accountTypes = [
            "UserID", "EmailAddress", "Name", "PhotosorVideos", "PhoneNumber", "PreciseLocation",
            // LOC-3 : l'adresse et le nom des lieux d'une Miff, écrits dans Firestore, visibles des membres.
            "PhysicalAddress", "OtherUserContent"
        ].map { "NSPrivacyCollectedDataType\($0)" }
        for entry in collected {
            let type = entry["NSPrivacyCollectedDataType"] as? String ?? ""
            let linked = entry["NSPrivacyCollectedDataTypeLinked"] as? Bool
            XCTAssertEqual(linked, accountTypes.contains(type), "\(type) : liaison à l'identité inattendue")
            if accountTypes.contains(type) {
                let purposes = entry["NSPrivacyCollectedDataTypePurposes"] as? [String]
                XCTAssertEqual(purposes, ["NSPrivacyCollectedDataTypePurposeAppFunctionality"], type)
            }
        }
        XCTAssertTrue(Set(accountTypes).isSubset(of: Set(types)), "données de compte manquantes : \(types)")
    }

    // MARK: - Langues

    func testEnglishIsTheFallbackAndFrenchAndEnglishAreDeclared() {
        XCTAssertEqual(info["CFBundleDevelopmentRegion"] as? String, "en")
        XCTAssertEqual(Set(info["CFBundleLocalizations"] as? [String] ?? []), ["fr", "en"])
    }
}
