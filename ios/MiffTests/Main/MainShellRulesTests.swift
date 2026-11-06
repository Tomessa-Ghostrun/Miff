import XCTest
@testable import Miff

/// Les règles de la coquille : quel onglet est montré et quand la barre est visible. Le cas qui compte : quitter ou supprimer sa
/// seule Miff depuis l'onglet Profil ne doit jamais laisser la personne sur un écran sans barre pour en sortir.
final class MainShellRulesTests: XCTestCase {
    private func ready(miffCount: Int) -> DirectoryState {
        let miffs = (0..<miffCount).map { MiffSummary(id: "miff\($0)", name: "Miff \($0)") }
        return .ready(
            DirectoryReady(miffs: miffs, activeId: miffs.first?.id, premium: false, fromCache: false)
        )
    }

    // MARK: - L'onglet réellement montré

    func testWhenTheOnlyMiffDisappearsFromTheProfileTabTheFeedIsShown() {
        // Sur le Profil avec une Miff : le Profil.
        let before = ready(miffCount: 1)
        let noMiffBefore = MainShellRules.hasNoMiff(before)
        XCTAssertEqual(MainShellRules.effectiveTab(selected: .profile, hasNoMiff: noMiffBefore), .profile)
        // La Miff est quittée, supprimée ou perdue : l'état devient « liste vide » et le Feed (« Pas encore de Miff ») prend la place.
        let after = ready(miffCount: 0)
        XCTAssertTrue(MainShellRules.hasNoMiff(after))
        XCTAssertEqual(MainShellRules.effectiveTab(selected: .profile, hasNoMiff: true), .feed)
        XCTAssertEqual(MainShellRules.effectiveTab(selected: .update, hasNoMiff: true), .feed)
        XCTAssertFalse(MainShellRules.barVisible(keyboardVisible: false, pathIsEmpty: true, directory: after))
    }

    func testWithMiffsTheSelectedTabIsKept() {
        for tab in MainTab.allCases {
            XCTAssertEqual(MainShellRules.effectiveTab(selected: tab, hasNoMiff: false), tab)
        }
    }

    func testAnUnreadDirectoryIsNotAnAccountWithoutMiff() {
        XCTAssertFalse(MainShellRules.hasNoMiff(.loading))
        XCTAssertFalse(MainShellRules.hasNoMiff(.unavailable))
        XCTAssertFalse(MainShellRules.hasNoMiff(ready(miffCount: 2)))
    }

    // MARK: - La barre

    func testTheBarIsVisibleOnARootScreenWithAMiffAndNoKeyboard() {
        let one = ready(miffCount: 1)
        XCTAssertTrue(MainShellRules.barVisible(keyboardVisible: false, pathIsEmpty: true, directory: one))
    }

    func testTheBarIsHiddenWhileAScreenIsPushed() {
        let one = ready(miffCount: 1)
        XCTAssertFalse(MainShellRules.barVisible(keyboardVisible: false, pathIsEmpty: false, directory: one))
    }

    func testTheBarIsHiddenWhileTheKeyboardIsOpen() {
        let one = ready(miffCount: 1)
        XCTAssertFalse(MainShellRules.barVisible(keyboardVisible: true, pathIsEmpty: true, directory: one))
    }

    func testTheBarWaitsForTheMiffsToBeRead() {
        // Pas de clignotement pendant le chargement, ni hors connexion sans cache.
        XCTAssertFalse(MainShellRules.barVisible(keyboardVisible: false, pathIsEmpty: true, directory: .loading))
        XCTAssertFalse(MainShellRules.barVisible(keyboardVisible: false, pathIsEmpty: true, directory: .unavailable))
    }
}
