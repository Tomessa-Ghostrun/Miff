import XCTest
@testable import Miff

/// Réglages > Mon compte : la déconnexion avec confirmation, le changement de mot de passe (e-mail de réinitialisation), la pause de
/// renvoi, et les mots que disent les Réglages (langue, contact, autorisations).
@MainActor
final class AccountViewModelTests: XCTestCase {
    private final class FakeResetGateway: PasswordResetGateway {
        var outcome: PasswordResetOutcome = .accepted
        private(set) var sentTo: [String] = []

        func sendResetEmail(_ email: String) async -> PasswordResetOutcome {
            sentTo.append(email)
            return outcome
        }
    }

    private final class Counter {
        private(set) var hits = 0

        func hit() {
            hits += 1
        }
    }

    private struct Setup {
        let viewModel: AccountViewModel
        let gateway: FakeResetGateway
        let signOuts: Counter
    }

    private var nowMillis = 0

    private func make(
        signIn: AccountSignIn = .password,
        email: String? = "alex@exemple.fr",
        online: Bool = true,
        outcome: PasswordResetOutcome = .accepted
    ) -> Setup {
        let gateway = FakeResetGateway()
        gateway.outcome = outcome
        let signOuts = Counter()
        let viewModel = AccountViewModel(
            email: email,
            signIn: signIn,
            signOut: { signOuts.hit() },
            passwordReset: gateway,
            network: FakeNetwork(online: online),
            cooldown: ResendCooldown(nowMillis: { [unowned self] in self.nowMillis })
        )
        return Setup(viewModel: viewModel, gateway: gateway, signOuts: signOuts)
    }

    // MARK: - Déconnexion

    func testSigningOutAsksFirstAndDoesNothingUntilConfirmed() {
        let setup = make()

        setup.viewModel.requestSignOut()

        XCTAssertTrue(setup.viewModel.state.confirmingSignOut)
        XCTAssertEqual(setup.signOuts.hits, 0)
    }

    func testCancellingLeavesEverythingAsItWas() {
        let setup = make()
        setup.viewModel.requestSignOut()

        setup.viewModel.cancelSignOut()

        XCTAssertFalse(setup.viewModel.state.confirmingSignOut)
        XCTAssertFalse(setup.viewModel.state.isSigningOut)
        XCTAssertEqual(setup.signOuts.hits, 0)
    }

    func testConfirmingSignsOutOnce() async {
        let setup = make()
        setup.viewModel.requestSignOut()

        setup.viewModel.confirmSignOut()
        setup.viewModel.confirmSignOut()
        await waitUntil { setup.signOuts.hits > 0 }

        XCTAssertEqual(setup.signOuts.hits, 1)
        XCTAssertFalse(setup.viewModel.state.confirmingSignOut)
    }

    func testASignOutThatFailsDurablyLeavesTheButtonUsableToTryAgain() async {
        // La session reste ouverte (Trousseau illisible) : le bouton ne reste pas bloqué sur « déconnexion en cours ».
        let setup = make()
        setup.viewModel.confirmSignOut()
        await waitUntil { setup.signOuts.hits > 0 }
        await waitUntil { !setup.viewModel.state.isSigningOut }

        XCTAssertFalse(setup.viewModel.state.isSigningOut)
        setup.viewModel.requestSignOut()
        XCTAssertTrue(setup.viewModel.state.confirmingSignOut)
        setup.viewModel.confirmSignOut()
        await waitUntil { setup.signOuts.hits > 1 }
        XCTAssertEqual(setup.signOuts.hits, 2)
    }

    func testNothingCanBeAskedWhileSigningOut() async {
        let setup = make()
        setup.viewModel.confirmSignOut()

        setup.viewModel.requestSignOut()

        XCTAssertFalse(setup.viewModel.state.confirmingSignOut)
    }

    // MARK: - Mot de passe

    func testOnlyAnEmailAccountCanChangeItsPassword() {
        XCTAssertTrue(make(signIn: .password).viewModel.state.canChangePassword)
        XCTAssertFalse(make(signIn: .google).viewModel.state.canChangePassword)
        XCTAssertFalse(make(signIn: .apple).viewModel.state.canChangePassword)
        XCTAssertFalse(make(email: nil).viewModel.state.canChangePassword)
    }

    func testAGoogleAccountNeverSendsAnEmail() {
        let setup = make(signIn: .google)

        setup.viewModel.changePassword()

        XCTAssertTrue(setup.gateway.sentTo.isEmpty)
        XCTAssertEqual(setup.viewModel.state.passwordStatus, .idle)
    }

    func testTheResetEmailGoesToTheAddressOfTheAccountThenThePauseStarts() async {
        let setup = make()

        setup.viewModel.changePassword()
        await waitUntil { setup.viewModel.state.passwordStatus == .sent }

        XCTAssertEqual(setup.gateway.sentTo, ["alex@exemple.fr"])
        XCTAssertEqual(setup.viewModel.state.passwordStatus, .sent)
        XCTAssertTrue(setup.viewModel.state.passwordLocked)
        XCTAssertNil(setup.viewModel.state.passwordMessage)
    }

    func testASecondTapDuringThePauseSendsNothing() async {
        let setup = make()
        setup.viewModel.changePassword()
        await waitUntil { setup.viewModel.state.passwordStatus == .sent }

        setup.viewModel.changePassword()

        XCTAssertEqual(setup.gateway.sentTo.count, 1)
    }

    func testOfflineSaysSoCalmlyAndSendsNothing() {
        let setup = make(online: false)

        setup.viewModel.changePassword()

        XCTAssertEqual(setup.viewModel.state.passwordMessage, .network)
        XCTAssertTrue(setup.gateway.sentTo.isEmpty)
        XCTAssertEqual(setup.viewModel.state.passwordStatus, .idle)
    }

    func testAFailureKeepsTheButtonUsableAndNamesNoAccount() async {
        let setup = make(outcome: .failed(.tooManyAttempts))

        setup.viewModel.changePassword()
        await waitUntil { setup.viewModel.state.passwordMessage != nil }

        XCTAssertEqual(setup.viewModel.state.passwordMessage, .tooManyRequests)
        XCTAssertEqual(setup.viewModel.state.passwordStatus, .idle)
        XCTAssertFalse(setup.viewModel.state.passwordLocked)
    }

    func testTheSignInMethodMapsToTheAccountKind() {
        XCTAssertEqual(AccountSignIn(method: .google), .google)
        XCTAssertEqual(AccountSignIn(method: .apple), .apple)
        XCTAssertEqual(AccountSignIn(method: .email), .password)
        XCTAssertEqual(AccountSignIn(method: nil), .password)
    }
}

final class SettingsModelsTests: XCTestCase {
    func testTheLanguageIsTheFirstOfThePersonThatTheAppSpeaks() {
        let code = AppLanguageName.code(preferred: ["de-DE", "fr-FR", "en-US"], available: ["en", "fr", "Base"])

        XCTAssertEqual(code, "fr")
    }

    func testAnUnsupportedLanguageFallsBackToEnglish() {
        let code = AppLanguageName.code(preferred: ["de-DE", "ja-JP"], available: ["en", "fr", "Base"])

        XCTAssertEqual(code, "en")
    }

    func testTheLanguageNameIsWrittenInItsOwnLanguage() {
        XCTAssertEqual(AppLanguageName.nativeName(code: "fr"), "Français")
        XCTAssertEqual(AppLanguageName.nativeName(code: "en"), "English")
    }

    func testTheMailLinkIsBuiltFromComponents() {
        let url = ContactLinks.mailURL(address: "aide@exemple.fr")

        XCTAssertEqual(url?.scheme, "mailto")
        XCTAssertEqual(url?.absoluteString, "mailto:aide@exemple.fr")
    }

    func testTheSupportAddressIsTheProvisionalContact() {
        XCTAssertEqual(ContactLinks.supportMailURL()?.absoluteString, "mailto:\(AppLinks.supportEmail)")
    }

    func testThePrivacyScreenReadsTheAuthorizationsWithoutAskingForAnything() {
        let full = PermissionSnapshot(location: .always, notifications: .authorized)
        let limited = PermissionSnapshot(location: .whenInUse, notifications: .denied)
        let complete = PrivacyPermissionState(snapshot: full)
        let partial = PrivacyPermissionState(snapshot: limited)

        XCTAssertEqual(complete.locationStatusKey, "settings_privacy_location_status_ok")
        XCTAssertEqual(complete.notificationsStatusKey, "ios_privacy_notifications_status_on")
        XCTAssertEqual(partial.locationStatusKey, "settings_privacy_location_status_todo")
        XCTAssertEqual(partial.notificationsStatusKey, "ios_privacy_notifications_status_off")
    }

    func testPreciseLocationIsRequiredForTheLocationToBeCalledComplete() {
        let approximate = PermissionSnapshot(location: .always, preciseLocation: false, notifications: .authorized)

        XCTAssertFalse(PrivacyPermissionState(snapshot: approximate).locationComplete)
    }

    func testEveryTextOfTheSettingsScreensIsTranslated() {
        let keys = [
            "settings_title", "settings_my_info_title", "settings_account_title", "settings_language_title",
            "settings_privacy_title", "ios_settings_language_current", "ios_settings_version", "ios_myinfo_edit_soon",
            "ios_account_apple", "ios_settings_delete_entry_hint",
            "ios_privacy_notifications_status_on", "ios_privacy_notifications_status_off",
            "ios_update_restricted_title", "ios_update_restricted_body", "ios_detection_help_intro",
            "ios_detection_help_step_location"
        ]
        for key in keys {
            XCTAssertNotEqual(L10n.string(key), key, "Pas de texte pour la clé \(key)")
        }
    }
}
