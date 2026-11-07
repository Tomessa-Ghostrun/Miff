import XCTest
@testable import Miff

/// Les règles d'affichage et l'interprétation des erreurs de la suppression du compte (AUTH-5c).
final class DeleteAccountRulesTests: XCTestCase {
    func testTheScreenStaysOnOnlyWhileTheProcedureRuns() {
        let running = AccountDeletionState.running(resumed: false, retrying: false, step: .checking)

        XCTAssertTrue(DeleteAccountRules.keepsScreenOn(.idle))
        XCTAssertTrue(DeleteAccountRules.keepsScreenOn(running))
        XCTAssertFalse(DeleteAccountRules.keepsScreenOn(.waitingForNetwork))
        XCTAssertFalse(DeleteAccountRules.keepsScreenOn(.needsRecentLogin))
        XCTAssertFalse(DeleteAccountRules.keepsScreenOn(.stuck))
        XCTAssertFalse(DeleteAccountRules.keepsScreenOn(.needsSignIn))
        XCTAssertFalse(DeleteAccountRules.keepsScreenOn(.done(appleRevocation: .notNeeded)))
    }

    func testTheOneLineOfStepNeverShowsAPercentageNorTheDetailOfTheLots() {
        XCTAssertNil(DeleteAccountRules.stepLine(.checking))
        XCTAssertEqual(DeleteAccountRules.stepLine(.profile), L10n.string("settings_delete_progress_profile"))
        let line = DeleteAccountRules.stepLine(.miff(index: 1, total: 2, longWait: false)) ?? ""
        XCTAssertTrue(line.contains("1") && line.contains("2"), line)
        XCTAssertFalse(line.contains("%"), line)
    }

    func testOnlyAMiffWithMoreThanThreePeopleToRemoveAnnouncesALongWait() {
        XCTAssertTrue(DeleteAccountRules.isLongWait(.miff(index: 1, total: 1, longWait: true)))
        XCTAssertFalse(DeleteAccountRules.isLongWait(.miff(index: 1, total: 1, longWait: false)))
        XCTAssertFalse(DeleteAccountRules.isLongWait(.profile))
    }

    func testAMiffWithNobodyElseIsNeverSaidToHaveZeroOthers() {
        let alone = OwnedMiff(miffId: "a", name: "Chez nous", otherMembers: 0)
        let two = OwnedMiff(miffId: "b", name: "Chez nous", otherMembers: 2)

        let aloneText = DeleteAccountRules.ownedRowText(alone, fallbackName: "…")
        let twoText = DeleteAccountRules.ownedRowText(two, fallbackName: "…")

        XCTAssertTrue(aloneText.contains("Chez nous"))
        XCTAssertFalse(aloneText.contains("0"), aloneText)
        XCTAssertTrue(twoText.contains("Chez nous") && twoText.contains("2"), twoText)
    }

    func testABlankNameUsesTheFallback() {
        let blank = OwnedMiff(miffId: "a", name: "", otherMembers: 0)

        XCTAssertTrue(DeleteAccountRules.ownedRowText(blank, fallbackName: "Miff").contains("Miff"))
    }

    func testTheExplanationDependsOnHowManyMiffsAndWhetherAnyoneElseIsThere() {
        let alone = OwnedMiff(miffId: "a", name: "A", otherMembers: 0)
        let others = OwnedMiff(miffId: "b", name: "B", otherMembers: 1)

        XCTAssertEqual(DeleteAccountRules.ownedExplainKey(owned: [others]), "settings_delete_owned_explain_one")
        XCTAssertEqual(DeleteAccountRules.ownedExplainKey(owned: [others, alone]), "settings_delete_owned_explain")
        XCTAssertEqual(DeleteAccountRules.ownedExplainKey(owned: [alone]), "settings_delete_owned_explain_alone")
        XCTAssertEqual(
            DeleteAccountRules.ownedExplainKey(owned: [alone, alone]), "settings_delete_owned_explain_alone_many"
        )
    }

    func testTheAccountSummaryOnlyCountsMiffsThePersonKnowsAbout() {
        XCTAssertFalse(AccountMiffsSummary.none.hasMiffs)
        XCTAssertTrue(AccountMiffsSummary(joined: 1, owned: []).hasMiffs)
        let alone = OwnedMiff(miffId: "a", name: "A", otherMembers: 0)
        XCTAssertTrue(AccountMiffsSummary(joined: 0, owned: [alone]).hasMiffs)
    }

    func testNothingPersonalIsInTheTextualFormOfTheTypes() {
        let owned = OwnedMiff(miffId: "SecretMiffId", name: "Secret", otherMembers: 1)
        let summary = AccountMiffsSummary(joined: 1, owned: [owned])
        let inventory = MiffsInventory(joined: [], owned: [owned], stale: [])
        let mark = AccountDeletionMark(uid: "secret-uid")

        for text in [String(describing: owned), String(describing: summary), String(describing: inventory),
                     String(describing: mark), String(describing: summary.warnPlan)] {
            XCTAssertFalse(text.contains("Secret") || text.contains("secret"), text)
        }
    }
}

/// Seul « utilisateur introuvable » dit « le compte n'existe plus » ; un jeton expiré ou révoqué dit « reconnecte-toi » ; un mauvais
/// justificatif est un mauvais mot de passe pour la ré-authentification par mot de passe (et pour elle seule).
final class AuthFailureDeletionTests: XCTestCase {
    private func error(_ code: Int) -> NSError {
        NSError(domain: AuthErrorNumber.domain, code: code)
    }

    func testOnlyUserNotFoundOnTheOpenSessionMeansTheAccountIsGone() {
        for attempt in [AuthAttempt.deleteAccount, .reauthEmail, .reauthSocial, .checkSession] {
            let failure = interpretAuthError(error(AuthErrorNumber.userNotFound), attempt: attempt)
            XCTAssertEqual(failure, .accountGone, "\(attempt)")
        }
    }

    func testUserNotFoundAtSignInIsStillTheSameAnswerAsAWrongPassword() {
        let notFound = error(AuthErrorNumber.userNotFound)
        XCTAssertEqual(interpretAuthError(notFound, attempt: .signInEmail), .invalidCredentials)
        XCTAssertEqual(interpretAuthError(notFound, attempt: .social), .unknown)
    }

    func testAnExpiredOrRevokedTokenIsNeverTheProofThatTheAccountIsGone() {
        for code in [AuthErrorNumber.userTokenExpired, AuthErrorNumber.invalidUserToken] {
            XCTAssertEqual(interpretAuthError(error(code), attempt: .deleteAccount), .sessionExpired, "\(code)")
            XCTAssertEqual(interpretAuthError(error(code), attempt: .checkSession), .sessionExpired, "\(code)")
            XCTAssertNotEqual(interpretAuthError(error(code), attempt: .deleteAccount), .accountGone)
        }
    }

    func testAnotherAccountThanTheSessionOneIsRecognised() {
        XCTAssertEqual(interpretAuthError(error(AuthErrorNumber.userMismatch), attempt: .reauthSocial), .wrongAccount)
    }

    func testTheReauthenticationByPasswordCallsAWrongPasswordWhatItIs() {
        let wrong = error(AuthErrorNumber.wrongPassword)
        let invalid = error(AuthErrorNumber.invalidCredential)
        XCTAssertEqual(interpretAuthError(wrong, attempt: .reauthEmail), .invalidCredentials)
        XCTAssertEqual(interpretAuthError(invalid, attempt: .reauthEmail), .invalidCredentials)
        XCTAssertEqual(interpretAuthError(invalid, attempt: .reauthSocial), .unknown)
    }

    func testNetworkAndTooManyAttemptsKeepTheirMeaningOnTheOpenSession() {
        let network = error(AuthErrorNumber.networkError)
        let tooMany = error(AuthErrorNumber.tooManyRequests)
        let offline = NSError(domain: NSURLErrorDomain, code: -1009)
        XCTAssertEqual(interpretAuthError(network, attempt: .reauthEmail), .network)
        XCTAssertEqual(interpretAuthError(tooMany, attempt: .reauthEmail), .tooManyAttempts)
        XCTAssertEqual(interpretAuthError(offline, attempt: .checkSession), .network)
    }

    func testTheCalmSentenceOfEachCause() {
        XCTAssertEqual(AuthFailure.invalidCredentials.reauthMessage(isSocial: false), .reauthPasswordWrong)
        XCTAssertEqual(AuthFailure.wrongAccount.reauthMessage(isSocial: true), .reauthWrongAccount)
        XCTAssertEqual(AuthFailure.sessionExpired.reauthMessage(isSocial: true), .reauthExpired)
        XCTAssertEqual(AuthFailure.accountGone.reauthMessage(isSocial: false), .reauthExpired)
        XCTAssertEqual(AuthFailure.tooManyAttempts.reauthMessage(isSocial: false), .tooManyAttempts)
        XCTAssertEqual(AuthFailure.tooManyAttempts.reauthMessage(isSocial: true), .tooManyRequests)
        XCTAssertEqual(AuthFailure.network.reauthMessage(isSocial: false), .network)
    }

    func testEveryNewMessageHasATextInBothLanguagesAndNoTechnicalCode() {
        for message in [AuthMessage.reauthPasswordWrong, .reauthWrongAccount, .reauthExpired] {
            XCTAssertFalse(message.text.isEmpty)
            XCTAssertFalse(message.text.contains("17"), message.text)
        }
    }

    func testTheProvidersOfTheAccountAreReadFromFirebaseIdentifiers() {
        let found = FirebaseAuthGateway.providers(from: ["password", "google.com", "apple.com", "phone"])

        XCTAssertEqual(found, [.password, .google, .apple])
        XCTAssertTrue(FirebaseAuthGateway.providers(from: []).isEmpty)
    }
}
