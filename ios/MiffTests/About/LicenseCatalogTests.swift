import XCTest
@testable import Miff

/// « Licences et attributions » (ticket `reglages-a-propos.md`, §5, critères AP12 à AP15) : chaque paquet livré a sa licence (la CI échoue
/// sinon), les textes complets sont dans l'app (lisibles hors connexion), rien ne vient du réseau.
final class LicenseCatalogTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // About
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    // MARK: - AP15 : une dépendance sans licence fait échouer la CI

    func testEveryResolvedPackageHasALicenseAtTheSameVersion() throws {
        let url = iosRoot.appendingPathComponent("Package.resolved")
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Package.resolved introuvable (\(url.path)).")
        }
        let pins = try XCTUnwrap(LicenseAudit.pins(resolvedJSON: Data(contentsOf: url)))

        XCTAssertGreaterThanOrEqual(pins.count, 17, "Package.resolved relu de travers ?")
        XCTAssertEqual(LicenseAudit.problems(pins: pins, entries: LicenseCatalog.software), [])
    }

    func testAPackageShippedWithoutALicenseIsReported() {
        let known = LicenseCatalog.software.compactMap { entry in
            entry.version.map { LicenseAudit.Pin(identity: entry.id, version: $0) }
        }
        let withNewcomer = known + [LicenseAudit.Pin(identity: "paquet-sans-licence", version: "1.0.0")]

        let problems = LicenseAudit.problems(pins: withNewcomer, entries: LicenseCatalog.software)

        XCTAssertEqual(problems.count, 1)
        XCTAssertTrue(problems.first?.contains("paquet-sans-licence") ?? false)
    }

    func testAPackageUpdatedWithoutUpdatingItsLicenseEntryIsReported() {
        var pins = LicenseCatalog.software.compactMap { entry in
            entry.version.map { LicenseAudit.Pin(identity: entry.id, version: $0) }
        }
        pins[0] = LicenseAudit.Pin(identity: pins[0].identity, version: "99.0.0")

        XCTAssertEqual(LicenseAudit.problems(pins: pins, entries: LicenseCatalog.software).count, 1)
    }

    func testALicenseEntryForAPackageThatLeftIsReported() {
        let pins = LicenseCatalog.software.dropFirst().compactMap { entry in
            entry.version.map { LicenseAudit.Pin(identity: entry.id, version: $0) }
        }

        XCTAssertEqual(LicenseAudit.problems(pins: Array(pins), entries: LicenseCatalog.software).count, 1)
    }

    func testResolvedFileThatCannotBeReadIsNotTakenForAnEmptyOne() {
        XCTAssertNil(LicenseAudit.pins(resolvedJSON: Data("pas du json".utf8)))
    }

    // MARK: - AP12 : le texte complet est dans l'app, sans réseau

    func testEveryLicenseHasItsFullTextInTheApp() throws {
        for entry in LicenseCatalog.all {
            let text = try XCTUnwrap(LicenseCatalog.text(for: entry), "\(entry.name) : texte introuvable dans l'app")
            XCTAssertGreaterThan(text.count, 500, "\(entry.name) : texte trop court")
        }
    }

    func testTheTextsAreTheRightLicenses() throws {
        for entry in LicenseCatalog.all {
            let text = try XCTUnwrap(LicenseCatalog.text(for: entry))
            switch entry.licenseName {
            case "Apache-2.0", "Apache-2.0, MIT":
                XCTAssertTrue(text.contains("Apache License"), entry.name)
                XCTAssertTrue(text.contains("Version 2.0, January 2004"), entry.name)
            case "MIT":
                XCTAssertTrue(text.contains("Permission is hereby granted, free of charge"), entry.name)
                XCTAssertTrue(text.contains("Copyright"), entry.name)
            case "BSD-3-Clause":
                XCTAssertTrue(text.contains("Redistribution and use in source and binary forms"), entry.name)
            case "zlib":
                XCTAssertTrue(text.contains("provided 'as-is'"), entry.name)
            case "SIL OFL 1.1":
                XCTAssertTrue(text.contains("SIL OPEN FONT LICENSE Version 1.1"), entry.name)
            default:
                XCTFail("\(entry.name) : licence « \(entry.licenseName) » sans contrôle de texte")
            }
        }
    }

    func testTheLicenseTextsOfTheFontsAreTheOnesOfTheThirdPartyFolder() throws {
        let pairs = [
            ("font-baloo-thambi-2", "OFL-BalooThambi2.txt"),
            ("font-hanken-grotesk", "OFL-HankenGrotesk.txt")
        ]
        for (id, file) in pairs {
            let source = iosRoot.appendingPathComponent("third_party/fonts/\(file)")
            guard FileManager.default.fileExists(atPath: source.path) else {
                throw XCTSkip("\(file) introuvable.")
            }
            let entry = try XCTUnwrap(LicenseCatalog.fonts.first { $0.id == id })

            let expected = try String(contentsOf: source, encoding: .utf8)

            XCTAssertEqual(LicenseCatalog.text(for: entry), expected, id)
        }
    }

    func testTheLicenseScreenNeverCallsTheNetwork() throws {
        let files = ["Miff/Models/About/LicenseModels.swift", "Miff/Views/About/LicensesView.swift"]
        for file in files {
            let url = iosRoot.appendingPathComponent(file)
            guard FileManager.default.fileExists(atPath: url.path) else {
                throw XCTSkip("\(file) introuvable.")
            }
            let text = try String(contentsOf: url, encoding: .utf8)
            XCTAssertFalse(text.contains("URLSession"), file)
            XCTAssertFalse(text.contains("Firestore"), file)
            XCTAssertFalse(text.contains("Analytics"), file)
        }
    }

    // MARK: - Contenu (AP13, AP14)

    func testTheEntriesAreUniqueAndSortedByName() {
        let ids = LicenseCatalog.all.map(\.id)
        let names = LicenseCatalog.software.map { $0.name.lowercased() }

        XCTAssertEqual(Set(ids).count, ids.count)
        XCTAssertEqual(names, names.sorted())
    }

    func testTheTwoEmbeddedFontsAreListedUnderTheOpenFontLicense() {
        XCTAssertEqual(LicenseCatalog.fonts.map(\.name), ["Baloo Thambi 2", "Hanken Grotesk"])
        XCTAssertTrue(LicenseCatalog.fonts.allSatisfy { $0.licenseName == "SIL OFL 1.1" })
    }

    func testNoAndroidMapServiceNorTestToolIsListedOnTheIPhone() {
        let names = LicenseCatalog.all.map { $0.name.lowercased() }.joined(separator: " ")

        for forbidden in ["maplibre", "openfreemap", "photon", "xctest", "swiftlint", "xcodegen"] {
            XCTAssertFalse(names.contains(forbidden), forbidden)
        }
    }

    func testTheShippedBinaryPackagesAreFlaggedSoTheScreenCanSayWhatItShows() {
        let flagged = LicenseCatalog.software.filter(\.isBinary).map(\.id)

        XCTAssertEqual(flagged, ["google-ads-on-device-conversion-ios-sdk", "googleappmeasurement"])
    }
}
