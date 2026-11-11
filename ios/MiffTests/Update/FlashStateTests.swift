import XCTest
@testable import Miff

/// Le bouton « flash » du viseur (demande du PM, 2026-11-11) : éteint par défaut, jamais gardé, seulement s'il est possible, dit en mots.
final class FlashStateTests: XCTestCase {
    func testTheFlashIsOffByDefaultAndHiddenUntilTheCameraSaysItCanDoIt() {
        let state = FlashState()
        XCTAssertFalse(state.isOn)
        XCTAssertFalse(state.isVisible)
        XCTAssertFalse(state.shouldFire)
    }

    func testTouchingTheButtonTurnsTheFlashOnThenOff() {
        var state = FlashState()
        state.setAvailable(true)
        XCTAssertTrue(state.isVisible)
        XCTAssertFalse(state.shouldFire)
        state.toggle()
        XCTAssertTrue(state.isOn)
        XCTAssertTrue(state.shouldFire)
        state.toggle()
        XCTAssertFalse(state.isOn)
        XCTAssertFalse(state.shouldFire)
    }

    func testTouchingWhenTheCameraCannotDoItChangesNothing() {
        var state = FlashState()
        state.toggle()
        XCTAssertFalse(state.isOn)
        XCTAssertFalse(state.shouldFire)
    }

    func testResetTurnsTheFlashOffButKeepsItAvailable() {
        // Nouvelle ouverture du viseur, retour de l'arrière-plan, changement de caméra : toujours éteint.
        var state = FlashState()
        state.setAvailable(true)
        state.toggle()
        state.reset()
        XCTAssertFalse(state.isOn)
        XCTAssertTrue(state.isAvailable)
        XCTAssertFalse(state.shouldFire)
    }

    func testANewStateNeverInheritsThePreviousOne() {
        // Aucun état n'est gardé : un viseur neuf (nouveau contrôleur) repart éteint, quoi qu'ait fait le précédent.
        var first = FlashState()
        first.setAvailable(true)
        first.toggle()
        XCTAssertTrue(first.isOn)
        XCTAssertEqual(FlashState(), FlashState())
        XCTAssertFalse(FlashState().isOn)
    }

    func testALensWithoutFlashTurnsTheRequestOffAndNeverFires() {
        var state = FlashState()
        state.setAvailable(true)
        state.toggle()
        state.setAvailable(false)
        XCTAssertFalse(state.isOn)
        XCTAssertFalse(state.isVisible)
        XCTAssertFalse(state.shouldFire)
        // Quand la caméra redevient capable, le flash reste éteint tant que la personne ne le rallume pas.
        state.setAvailable(true)
        XCTAssertFalse(state.shouldFire)
    }

    func testTheStateIsSaidInWordsAndInTheSymbolNotByColorAlone() throws {
        var state = FlashState()
        state.setAvailable(true)
        XCTAssertEqual(state.textKey, "update_flash_state_off")
        XCTAssertEqual(state.symbolName, "bolt.slash.fill")
        state.toggle()
        XCTAssertEqual(state.textKey, "update_flash_state_on")
        XCTAssertEqual(state.symbolName, "bolt.fill")
        for key in ["update_flash_state_on", "update_flash_state_off"] {
            for language in ["fr", "en"] {
                let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"), language)
                let bundle = try XCTUnwrap(Bundle(path: path))
                XCTAssertNotEqual(L10n.string(key, bundle: bundle), key, "\(key) non traduit en \(language)")
            }
        }
    }

    // MARK: - Le matériel

    private func support(_ outputSupportsOn: Bool, _ deviceHasFlash: Bool, _ deviceFlashAvailable: Bool) -> Bool {
        FlashSupport.isAvailable(
            outputSupportsOn: outputSupportsOn, deviceHasFlash: deviceHasFlash, deviceFlashAvailable: deviceFlashAvailable
        )
    }

    func testBackCameraWithAFlashNeedsTheSystemToAllowIt() {
        XCTAssertTrue(support(true, true, true))
        XCTAssertFalse(support(true, true, false))
    }

    func testFrontCameraWithRetinaFlashIsAvailableWithoutADeviceFlash() {
        XCTAssertTrue(support(true, false, false))
    }

    func testNoSupportedOnModeMeansNoFlashWhateverTheDeviceSays() {
        XCTAssertFalse(support(false, true, true))
        XCTAssertFalse(support(false, false, false))
    }
}
