import CoreGraphics
import XCTest
@testable import Miff

/// La mise en page du viseur (retour du PM, iPhone 16 : « l'écran n'est pas en full screen, il est coupé en haut (bande noire en haut) »).
/// L'aperçu doit remplir TOUT l'écran, derrière la barre d'état et l'île dynamique, quelles que soient les zones sûres annoncées par SwiftUI.
final class ViewfinderLayoutTests: XCTestCase {
    private struct Device {
        let name: String
        let size: CGSize
        let safeTop: CGFloat
        let safeBottom: CGFloat
    }

    private let devices = [
        Device(name: "iPhone SE", size: CGSize(width: 375, height: 667), safeTop: 20, safeBottom: 0),
        Device(name: "iPhone 11 Pro (encoche)", size: CGSize(width: 375, height: 812), safeTop: 44, safeBottom: 34),
        Device(name: "iPhone 14 Pro (île)", size: CGSize(width: 393, height: 852), safeTop: 59, safeBottom: 34),
        Device(name: "iPhone 16 (île dynamique)", size: CGSize(width: 393, height: 852), safeTop: 59, safeBottom: 34),
        Device(name: "iPhone 16 Pro", size: CGSize(width: 402, height: 874), safeTop: 62, safeBottom: 34),
        Device(name: "iPhone Pro Max", size: CGSize(width: 430, height: 932), safeTop: 59, safeBottom: 34)
    ]

    /// La zone visible (sans zones sûres) d'un appareil, telle que le lecteur de géométrie l'annonce quand il fonctionne bien.
    private func visible(_ device: Device, barInset: CGFloat = 0) -> CGSize {
        CGSize(width: device.size.width, height: device.size.height - device.safeTop - device.safeBottom - barInset)
    }

    // MARK: - La taille de l'écran

    func testTheScreenIsTheWindowWhenItIsKnownEvenIfTheReaderAnnouncesNullInsets() {
        for device in devices {
            // Le cas du bug : le lecteur annonce des zones sûres NULLES alors que sa zone visible, elle, les exclut déjà.
            let screen = ViewfinderLayout.screenSize(
                visible: visible(device), top: 0, bottom: 0, leading: 0, trailing: 0, window: device.size
            )
            XCTAssertEqual(screen, device.size, device.name)
        }
    }

    func testWithoutAWindowTheScreenIsTheVisibleAreaPlusTheInsets() {
        for device in devices {
            let screen = ViewfinderLayout.screenSize(
                visible: visible(device), top: device.safeTop, bottom: device.safeBottom,
                leading: 0, trailing: 0, window: nil
            )
            XCTAssertEqual(screen, device.size, device.name)
        }
        // Une fenêtre sans taille (pas encore posée) n'est pas crue.
        let unsized = ViewfinderLayout.screenSize(
            visible: CGSize(width: 393, height: 700), top: 59, bottom: 93, leading: 0, trailing: 0, window: .zero
        )
        XCTAssertEqual(unsized, CGSize(width: 393, height: 852))
    }

    // MARK: - L'aperçu remplit l'écran, sans bande

    func testThePreviewFillsTheWholeScreenOnEveryPhoneWithNoBandAtTheTop() {
        for device in devices {
            let window = CGRect(origin: .zero, size: device.size)
            let aspect = UpdatePhotoFormat.boundedAspect(
                widthPx: Int(device.size.width), heightPx: Int(device.size.height)
            )
            let rect = ViewfinderLayout.previewRect(in: window, aspect: aspect)

            XCTAssertEqual(rect.minY, 0, accuracy: 0.5, "\(device.name) : rien ne doit rester au-dessus de l'aperçu")
            XCTAssertEqual(rect.minX, 0, accuracy: 0.5, device.name)
            XCTAssertEqual(rect.maxY, device.size.height, accuracy: 0.5, device.name)
            XCTAssertEqual(rect.maxX, device.size.width, accuracy: 0.5, device.name)
        }
    }

    func testThePreviewIsPlacedOnTheWindowNotOnTheSafeAreaWhateverTheReaderSays() {
        // Le lecteur annonce zones sûres nulles et une zone visible RACCOURCIE : l'écran, lui, vient de la fenêtre, donc le ratio et le
        // rectangle sont ceux de l'écran entier (iPhone 16).
        let window = CGSize(width: 393, height: 852)
        let screen = ViewfinderLayout.screenSize(
            visible: CGSize(width: 393, height: 679), top: 0, bottom: 0, leading: 0, trailing: 0, window: window
        )
        let aspect = UpdatePhotoFormat.boundedAspect(widthPx: Int(screen.width), heightPx: Int(screen.height))
        let rect = ViewfinderLayout.previewRect(in: CGRect(origin: .zero, size: screen), aspect: aspect)

        XCTAssertEqual(rect, CGRect(x: 0, y: 0, width: 393, height: 852))
    }

    func testAWiderThanBoundedScreenKeepsACenteredBoundedZone() {
        // Hors des bornes 9:21 à 9:16 (tablette, écran partagé) : zone bornée, centrée (jamais appelé sur un iPhone).
        let container = CGRect(x: 0, y: 0, width: 820, height: 1_180)
        let rect = ViewfinderLayout.previewRect(in: container, aspect: UpdatePhotoFormat.maxAspect)
        XCTAssertEqual(rect.height, 1_180, accuracy: 0.01)
        XCTAssertEqual(rect.width, 1_180 * 9 / 16, accuracy: 0.01)
        XCTAssertEqual(rect.midX, 410, accuracy: 0.01)
    }

    func testPreviewRectFollowsAContainerWithAnOrigin() {
        let container = CGRect(x: 10, y: 20, width: 393, height: 852)
        let rect = ViewfinderLayout.previewRect(in: container, aspect: 393.0 / 852.0)
        XCTAssertEqual(rect.minX, 10, accuracy: 0.01)
        XCTAssertEqual(rect.minY, 20, accuracy: 0.01)
    }

    func testBoundedBoxIgnoresEmptyInput() {
        XCTAssertEqual(ViewfinderLayout.boundedBox(full: .zero, aspect: 0.5), .zero)
        let size = CGSize(width: 100, height: 200)
        XCTAssertEqual(ViewfinderLayout.boundedBox(full: size, aspect: 0), size)
    }

    // MARK: - La zone sûre du haut

    func testTheTopInsetIsTheRealOneWhenKnown() {
        for device in devices {
            let inset = ViewfinderLayout.topInset(readTop: device.safeTop, isPortrait: true)
            XCTAssertEqual(inset, device.safeTop, device.name)
        }
        // La vraie valeur n'est jamais réduite au plancher (iPhone SE : 20 pt, pas 47).
        XCTAssertEqual(ViewfinderLayout.topInset(readTop: 20, isPortrait: true), 20)
    }

    func testWhenNothingIsReadThePortraitFloorHoldsAndLandscapeNeedsNone() {
        XCTAssertEqual(ViewfinderLayout.topInset(readTop: 0, isPortrait: true), ViewfinderLayout.topInsetFloor)
        XCTAssertEqual(ViewfinderLayout.topInset(readTop: 0, isPortrait: false), 0)
        // L'encoche d'un iPhone X à 11 Pro (44 à 47 pt) est couverte.
        XCTAssertGreaterThanOrEqual(ViewfinderLayout.topInsetFloor, 47)
    }

    func testOnlyWhatIsMissingIsAddedAtTheTop() {
        // Cas normal : SwiftUI a déjà posé le contenu sous la zone sûre, rien à ajouter.
        XCTAssertEqual(ViewfinderLayout.extraTopPadding(topInset: 59, layoutTop: 59), 0)
        XCTAssertEqual(ViewfinderLayout.extraTopPadding(topInset: 20, layoutTop: 59), 0)
        // Le lecteur annonçait 0 : on ajoute toute la zone sûre.
        XCTAssertEqual(ViewfinderLayout.extraTopPadding(topInset: 59, layoutTop: 0), 59)
        // Une mesure partielle : seulement la différence.
        XCTAssertEqual(ViewfinderLayout.extraTopPadding(topInset: 59, layoutTop: 44), 15)
        XCTAssertEqual(ViewfinderLayout.extraTopPadding(topInset: 59, layoutTop: -3), 59)
    }

    func testTheTopShadowCoversTheStatusBarAndANarrowStripOfImage() {
        // Elle suit la zone sûre : plus haute sous l'île dynamique que sur un iPhone SE, jamais une valeur unique.
        XCTAssertEqual(ViewfinderLayout.gradientHeight(topInset: 59), 59 + ViewfinderLayout.gradientExtra)
        XCTAssertEqual(ViewfinderLayout.gradientHeight(topInset: 20), 20 + ViewfinderLayout.gradientExtra)
        XCTAssertEqual(ViewfinderLayout.gradientHeight(topInset: -5), ViewfinderLayout.gradientExtra)
        // Une très légère ombre : bien en dessous du voile des pastilles.
        XCTAssertLessThanOrEqual(ViewfinderLayout.gradientTopAlpha, 0.4)
        XCTAssertGreaterThan(ViewfinderLayout.gradientTopAlpha, 0)
    }

    func testTheHelpTextAndTheShutterKeepTheirPosition() {
        // Le texte d'aide et l'obturateur n'ont pas bougé : toujours 48 pt au-dessus de ce que la barre laisse libre.
        XCTAssertEqual(UpdateLayout.shutterAboveBar, 48)
        XCTAssertEqual(UpdateLayout.shutterSize, 80)
    }

    func testPortraitDetection() {
        XCTAssertTrue(ViewfinderLayout.isPortrait(CGSize(width: 393, height: 852)))
        XCTAssertFalse(ViewfinderLayout.isPortrait(CGSize(width: 852, height: 393)))
    }
}

/// L'apparence de l'app : claire, sauf une barre d'état en clair quand un écran sombre (le viseur, l'aperçu) le demande.
final class AppAppearanceTests: XCTestCase {
    func testTheAppIsLightByDefault() {
        let appearance = AppAppearance()
        XCTAssertFalse(appearance.wantsLightStatusBar)
        XCTAssertEqual(appearance.colorScheme, .light)
    }

    func testADarkScreenTurnsTheStatusBarLightAndLeavingTurnsItBack() {
        let appearance = AppAppearance()
        appearance.beginLightStatusBar()
        XCTAssertTrue(appearance.wantsLightStatusBar)
        XCTAssertEqual(appearance.colorScheme, .dark)
        appearance.endLightStatusBar()
        XCTAssertEqual(appearance.colorScheme, .light)
    }

    func testTheViewfinderLeavingAndThePreviewArrivingOverlapInAnyOrder() {
        // Le viseur part et l'aperçu arrive : les deux événements peuvent se croiser, la barre reste claire entre les deux.
        let appearance = AppAppearance()
        appearance.beginLightStatusBar()
        appearance.beginLightStatusBar()
        appearance.endLightStatusBar()
        XCTAssertEqual(appearance.colorScheme, .dark)
        appearance.endLightStatusBar()
        XCTAssertEqual(appearance.colorScheme, .light)
    }

    func testAnExtraLeaveNeverGoesBelowZeroNorBlocksTheNextRequest() {
        let appearance = AppAppearance()
        appearance.endLightStatusBar()
        XCTAssertEqual(appearance.lightStatusBarRequests, 0)
        appearance.beginLightStatusBar()
        XCTAssertEqual(appearance.colorScheme, .dark)
    }
}

/// Les points de câblage qui ne se testent que dans le code : le remplissage, l'unique décideur du schéma de couleurs, les options de la CI.
final class ViewfinderFullScreenWiringTests: XCTestCase {
    private func source(_ path: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Miff")
            .appendingPathComponent(path)
        return try String(contentsOf: url, encoding: .utf8)
    }

    func testThePreviewFillsAndNeverFitsWithBands() throws {
        let viewfinder = try source("Views/Update/ViewfinderView.swift")
        XCTAssertTrue(viewfinder.contains("previewLayer.videoGravity = .resizeAspectFill"))
        XCTAssertFalse(viewfinder.contains(".resizeAspect\n"))
        XCTAssertFalse(viewfinder.contains("videoGravity = .resizeAspect)"))
        // Le calque est posé sur la fenêtre, jamais sur « bounds » de la vue (que SwiftUI peut raccourcir).
        XCTAssertFalse(viewfinder.contains("previewLayer.frame = bounds"))
        XCTAssertTrue(viewfinder.contains("window.bounds"))
        XCTAssertTrue(viewfinder.contains(".ignoresSafeArea()"))
    }

    func testTheViewfinderAndThePreviewAskForALightStatusBarAndStayUnderTheTopSafeArea() throws {
        let viewfinder = try source("Views/Update/ViewfinderView.swift")
        let preview = try source("Views/Update/UpdatePreviewView.swift")
        for code in [viewfinder, preview] {
            XCTAssertTrue(code.contains(".lightStatusBar()"))
            XCTAssertTrue(code.contains(".topSafeAreaFloor()"))
        }
    }

    func testOnlyTheAppRootDecidesTheColorSchemeSoTwoModifiersNeverFight() throws {
        let root = try source("MiffApp.swift")
        XCTAssertTrue(root.contains(".modifier(AppColorSchemeModifier())"))
        XCTAssertFalse(root.contains(".preferredColorScheme(.light)"))
        let names = ["ViewfinderView", "UpdatePreviewView", "UpdateTabView"]
        let paths = names.map { "Views/Update/" + $0 + ".swift" }
        for path in paths {
            XCTAssertFalse(try source(path).contains("preferredColorScheme"), path)
        }
    }

    func testTheDemoBackdropAndControlsOnlyExistInDebug() throws {
        let viewfinder = try source("Views/Update/ViewfinderView.swift")
        let controller = try source("Services/Update/CameraController.swift")
        for (name, code, marker) in [
            ("aplat de démonstration", viewfinder, "paintDemoBackdrop(previewLayer)"),
            ("contrôles de démonstration (écran)", viewfinder, "camera.showDemoControls()"),
            ("contrôles de démonstration (contrôleur)", controller, "func showDemoControls()")
        ] {
            let at = try XCTUnwrap(code.range(of: marker)?.lowerBound, name)
            let before = String(code[..<at])
            let opened = before.components(separatedBy: "#if DEBUG").count - 1
            let closed = before.components(separatedBy: "#endif").count - 1
            XCTAssertGreaterThan(opened, closed, "\(name) : doit être dans un bloc #if DEBUG")
        }
    }
}
