import XCTest
@testable import Miff

/// La barre d'onglets (ONB-7) : les trois entrées, qui les voit, le niveau de verre selon les réglages d'accessibilité, les mesures
/// des maquettes, les couleurs du bouton central et les contrastes du §6 de `design/tab-bar-liquid-glass.md`.
final class TabBarModelsTests: XCTestCase {
    // MARK: - Les onglets

    func testAnInternalBuildHasThreeTabsInOrder() {
        let config = TabBarConfig.forBuild(isInternalBuild: true)

        XCTAssertEqual(config.tabs, [.feed, .update, .profile])
    }

    func testAnExternalBuildHasTwoTabsAndNoCentralButton() {
        // Décision Q1 : un bouton qui n'a pas encore de fonction n'atteint jamais un testeur extérieur.
        let config = TabBarConfig.forBuild(isInternalBuild: false)

        XCTAssertEqual(config.tabs, [.feed, .profile])
        XCTAssertFalse(config.tabs.contains(.update))
    }

    func testEveryTabHasASpokenNameInBothLanguages() {
        for tab in MainTab.allCases {
            let key = TabBarNames.key(for: tab)
            XCTAssertNotEqual(L10n.string(key), key, "Le nom parlé de l'onglet \(tab) n'a pas de texte")
        }
    }

    func testTheCentralButtonNameSaysItIsComingSoon() {
        XCTAssertEqual(TabBarNames.key(for: .update), "tabbar_update_soon_a11y")
    }

    // MARK: - Le niveau de verre

    func testNativeGlassWhenItExistsAndNothingAsksForOpacity() {
        let level = GlassPolicy.level(nativeAvailable: true, reduceTransparency: false, increasedContrast: false)

        XCTAssertEqual(level, .native)
    }

    func testMaterialFallbackBeforeIOS26() {
        let level = GlassPolicy.level(nativeAvailable: false, reduceTransparency: false, increasedContrast: false)

        XCTAssertEqual(level, .material)
    }

    func testReduceTransparencyGivesAnOpaqueBarWhateverTheVersion() {
        for native in [true, false] {
            let level = GlassPolicy.level(nativeAvailable: native, reduceTransparency: true, increasedContrast: false)
            XCTAssertEqual(level, .opaque)
        }
    }

    func testIncreasedContrastGivesAnOpaqueBarWhateverTheVersion() {
        for native in [true, false] {
            let level = GlassPolicy.level(nativeAvailable: native, reduceTransparency: false, increasedContrast: true)
            XCTAssertEqual(level, .opaque)
        }
    }

    func testTheFallbackSwitchTurnsNativeGlassOff() {
        let level = GlassPolicy.level(
            nativeAvailable: true,
            reduceTransparency: false,
            increasedContrast: false,
            nativeAllowed: false
        )

        XCTAssertEqual(level, .material)
    }

    func testNativeGlassIsAllowedByDefault() {
        XCTAssertTrue(GlassPolicy.nativeGlassAllowed)
    }

    // MARK: - Les mesures des maquettes

    func testTheBarHasTheShapeOfTheMockups() {
        XCTAssertEqual(TabBarLayout.barHeight, 64)
        XCTAssertEqual(TabBarLayout.sideMargin, 24)
        XCTAssertEqual(TabBarLayout.cornerRadius, 20)
        XCTAssertEqual(TabBarLayout.bottomMargin, 22)
    }

    func testEveryTouchTargetIsAtLeast44Points() {
        XCTAssertGreaterThanOrEqual(TabBarLayout.entryWidth, Spacing.minTouchTarget)
        XCTAssertGreaterThanOrEqual(TabBarLayout.entryHeight, Spacing.minTouchTarget)
        XCTAssertGreaterThanOrEqual(TabBarLayout.barHeight, Spacing.minTouchTarget)
        XCTAssertGreaterThanOrEqual(TabBarLayout.plateSize, Spacing.minTouchTarget)
    }

    func testTheDotKeepsItsAirInsideTheRing() {
        XCTAssertEqual(TabBarLayout.plateSize, 44)
        XCTAssertEqual(TabBarLayout.ringSize, 36)
        XCTAssertEqual(TabBarLayout.ringWidth, 3)
        XCTAssertEqual(TabBarLayout.dotSize, 24)
        let innerRing = TabBarLayout.ringSize - 2 * TabBarLayout.ringWidth
        XCTAssertEqual((innerRing - TabBarLayout.dotSize) / 2, 3, "3 pt d'air blanc autour de la pastille")
    }

    func testTheBarSitsTwentyTwoPointsFromTheBottomOfTheScreen() {
        // Sans zone sûre (iPhone à bouton) : 22 pt. Avec 34 pt de zone sûre : elle déborde de 12 pt sur l'indicateur d'accueil.
        XCTAssertEqual(TabBarLayout.bottomPadding(safeAreaBottom: 0), 22)
        XCTAssertEqual(TabBarLayout.bottomPadding(safeAreaBottom: 34), -12)
        let safeAreas: [CGFloat] = [0, 20, 34]
        for safeArea in safeAreas {
            XCTAssertEqual(safeArea + TabBarLayout.bottomPadding(safeAreaBottom: safeArea), TabBarLayout.bottomMargin)
        }
    }

    // MARK: - Le bouton central

    func testTheCentralButtonIsGreyWhenTheStatusIsUnknown() {
        XCTAssertEqual(CentralButtonState.neutral.dotColor.hexString, "#595959")
        XCTAssertEqual(CentralButtonState.neutral.ringColor.hexString, "#595959")
    }

    func testTheCentralButtonTakesTheStatusColorsWithABlueRing() {
        XCTAssertEqual(CentralButtonState.safe.dotColor, colorStatusSafe)
        XCTAssertEqual(CentralButtonState.warning.dotColor, colorStatusWarning)
        XCTAssertEqual(CentralButtonState.danger.dotColor, colorStatusDanger)
        for state in CentralButtonState.allCases where state != .neutral {
            XCTAssertEqual(state.ringColor, colorActionPrimary)
        }
    }

    func testTheNeutralDotIsReadableOnTheWhitePlate() {
        let white = ColorToken(hex: 0xFFFFFF)

        XCTAssertGreaterThanOrEqual(CentralButtonState.neutral.dotColor.contrastRatio(with: white), 6.99)
    }

    // MARK: - Les variantes et leurs contrastes (§5 et §6 de la spec)

    func testTheLightVariantTints() {
        let palette = TabBarVariant.light.palette

        XCTAssertEqual(palette.tint, ColorToken(hex: 0xFFFFFF, alpha: 0.80))
        XCTAssertEqual(palette.opaque.hexString, "#FFFFFF")
        XCTAssertEqual(palette.iconInactive.hexString, "#595959")
        XCTAssertEqual(palette.iconActive, colorActionPrimary)
        XCTAssertEqual(palette.pillActive, colorActionPrimaryTint20)
    }

    func testThePhotoVariantIsTheDarkSet() {
        let palette = TabBarVariant.onPhoto.palette

        XCTAssertEqual(palette.tint, ColorToken(hex: 0x000000, alpha: 0.60))
        XCTAssertEqual(palette.opaque.hexString, "#222222")
        XCTAssertEqual(palette.iconActive.hexString, "#FFFFFF")
        XCTAssertEqual(palette.pillActive.alphaPercent, 24)
    }

    func testTheOpaqueLightBarKeepsEveryIconReadable() {
        let palette = TabBarVariant.light.palette

        // Icône inactive 7,0 : 1 et icône active 8,16 : 1 sur le fond opaque (tableau du §6).
        XCTAssertGreaterThanOrEqual(palette.iconInactive.contrastRatio(with: palette.opaque), 6.99)
        XCTAssertGreaterThanOrEqual(palette.iconActive.contrastRatio(with: palette.opaque), 8.1)
    }

    func testTheOpaquePhotoBarKeepsTheWhiteIconsReadable() {
        let palette = TabBarVariant.onPhoto.palette

        XCTAssertGreaterThanOrEqual(palette.iconActive.contrastRatio(with: palette.opaque), 15.8)
    }

    func testTheNewInactiveGreyFixesTheOldOne() {
        let white = ColorToken(hex: 0xFFFFFF)
        let blackUnderThe80PercentVeil = ColorToken(hex: 0xCCCCCC)

        // Le gris d'avant (#7D7C7C) tombait à 2,59 : 1 au pire fond ; le nouveau tient au moins 4,3 : 1.
        XCTAssertLessThan(ColorToken(hex: 0x7D7C7C).contrastRatio(with: blackUnderThe80PercentVeil), 3.0)
        XCTAssertGreaterThan(tabBarIconInactiveLight.contrastRatio(with: blackUnderThe80PercentVeil), 4.3)
        XCTAssertGreaterThan(tabBarIconInactiveLight.contrastRatio(with: white), 6.99)
    }
}
