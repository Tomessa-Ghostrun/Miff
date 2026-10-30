import XCTest
@testable import Miff

/// `SessionManager` : « qui est connecté et où s'ouvre l'app ». Mêmes cas qu'Android : la destination vient de l'état du
/// COMPTE, rien n'est collecté avant le choix du compte (ou le geste « Rejoindre » / « Continuer »), une fin de session purge
/// tout, et un refus du panneau supprime le compte tout juste créé.
@MainActor
final class SessionManagerTests: XCTestCase {
    private func ready(_ destination: AppDestination) -> SessionState {
        .ready(destination)
    }

    // MARK: - Destination au lancement

    func testNoSessionTheFirstTimeOpensSignUp() async {
        let harness = SessionHarness()

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, ready(.signUp))
    }

    func testNoSessionAfterAPreviousSessionOpensSignInDirectly() async {
        let harness = SessionHarness()
        harness.flags.set(true, forKey: SessionManager.keyReturningUser)

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, ready(.signIn))
    }

    func testASessionWithoutProfileAndWithoutRecordedTermsOpensTheConsentPanel() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, ready(.consentPanel))
    }

    func testTermsRecordedForThisAccountOpenTheProfileStep() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.pending.save(PendingConsent(uid: "uid-1", termsVersion: "0.1", analyticsAccepted: true))

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, ready(.profile))
    }

    func testTermsRecordedForAnotherAccountNeverCountForThisOne() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-2")
        harness.pending.save(PendingConsent(uid: "uid-1", termsVersion: "0.1", analyticsAccepted: true))

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, ready(.consentPanel))
    }

    func testAProfileWithoutAMiffOpensTheChoiceAndNeverTheProfileAgain() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true)

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, ready(.miffChoice))
    }

    func testAProfileAndAMiffOpenTheFeed() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true)

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, ready(.feed))
    }

    func testAProfileWithoutAPhoneOpensThePhoneStepAlone() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasPhone: false)

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, ready(.phoneOnly))
    }

    func testAnAccountThatCannotBeReadKeepsTheSessionAndRetryWorks() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.result = .unavailable

        await harness.manager.startAndResolve()
        XCTAssertEqual(harness.manager.state, .lookupFailed)
        XCTAssertEqual(harness.gateway.currentUid, "uid-1")
        XCTAssertEqual(harness.gateway.signOutCalls, 0)

        harness.accounts.setLoaded(profileExists: true, hasMembership: true)
        harness.manager.retry()
        await harness.waitUntil { harness.manager.state == .ready(.feed) }

        XCTAssertEqual(harness.manager.state, ready(.feed))
    }

    func testAFailedPurgeFromLastTimeComesBeforeAnythingElse() async {
        let harness = SessionHarness()

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.cache.purgeIfPendingCalls, 1)
    }

    // MARK: - Collecte (critère X9)

    func testAColdStartPausesAnyCollectionAndOnlySwitchesOnAfterTheAccountSaidYes() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.privacy.states.first, "pause")
        XCTAssertEqual(harness.privacy.states, ["pause", "on"])
        // Au moment où le compte a été lu, rien n'était collecté.
        XCTAssertEqual(harness.collectionWhenAccountRead, false)
    }

    func testAColdStartWhoseAccountCannotBeReadCollectsNothing() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.result = .unavailable

        await harness.manager.startAndResolve()

        XCTAssertFalse(harness.privacy.analyticsEnabled)
        // Pause seulement : le rapport du plantage précédent est gardé pour plus tard.
        XCTAssertEqual(harness.privacy.states, ["pause"])
    }

    func testAnAccountThatWithdrewItsConsentCollectsNothing() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: false)

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.privacy.states, ["pause", "off"])
        XCTAssertFalse(harness.privacy.analyticsEnabled)
    }

    func testNoSessionAtLaunchDeletesWhatACrashLeftBecauseThereIsNoConsent() async {
        let harness = SessionHarness()

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.privacy.states, ["pause", "off"])
    }

    // MARK: - Inscription par e-mail

    func testEmailSignUpSwitchesCollectionOnThenSendsSignUpCompleted() async {
        let harness = SessionHarness()
        await harness.manager.startAndResolve()

        let failure = await harness.manager.createAccountWithEmail(
            email: "alex@exemple.fr", password: "motdepasse", consent: .implicit)

        XCTAssertNil(failure)
        XCTAssertEqual(harness.manager.state, ready(.profile))
        XCTAssertTrue(harness.privacy.analyticsEnabled)
        XCTAssertEqual(harness.sink.names, ["sign_up_completed"])
        XCTAssertEqual(harness.sink.events.first?.params["method"] as? String, "email")
        // La version des textes vus est gardée sur l'appareil jusqu'à la création du profil.
        XCTAssertEqual(
            harness.pending.read(uid: "uid-1"),
            PendingConsent(uid: "uid-1", termsVersion: ConsentVersions.terms, analyticsAccepted: true))
        XCTAssertTrue(harness.flags.value(forKey: SessionManager.keyReturningUser, default: false))
    }

    func testAnAccountIsNeverCreatedWithoutTheTerms() async {
        let harness = SessionHarness()
        await harness.manager.startAndResolve()

        let failure = await harness.manager.createAccountWithEmail(
            email: "alex@exemple.fr", password: "motdepasse", consent: ConsentChoice(termsAccepted: false))

        XCTAssertEqual(failure, .unknown)
        XCTAssertEqual(harness.gateway.createCalls, 0)
        XCTAssertEqual(harness.manager.state, ready(.signUp))
    }

    func testAFailedSignUpChangesNothing() async {
        let harness = SessionHarness()
        harness.gateway.createResult = .failure(.emailAlreadyInUse)
        await harness.manager.startAndResolve()

        let failure = await harness.manager.createAccountWithEmail(
            email: "alex@exemple.fr", password: "motdepasse", consent: .implicit)

        XCTAssertEqual(failure, .emailAlreadyInUse)
        XCTAssertEqual(harness.manager.state, ready(.signUp))
        XCTAssertNil(harness.pending.stored)
        XCTAssertTrue(harness.sink.names.isEmpty)
    }

    // MARK: - Connexion

    func testSignInCollectsNothingUntilTheAccountHasBeenReadThenFollowsItsChoice() async {
        let harness = SessionHarness()
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        await harness.manager.startAndResolve()

        let failure = await harness.manager.signInWithEmail(email: "alex@exemple.fr", password: "motdepasse")

        XCTAssertNil(failure)
        XCTAssertEqual(harness.collectionWhenAccountRead, false)
        XCTAssertTrue(harness.privacy.analyticsEnabled)
        XCTAssertEqual(harness.manager.state, ready(.feed))
        XCTAssertEqual(harness.sink.names, ["sign_in_completed"])
    }

    func testSignInOfAnAccountWithoutConsentCollectsAndSendsNothing() async {
        let harness = SessionHarness()
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: false)
        await harness.manager.startAndResolve()

        _ = await harness.manager.signInWithEmail(email: "alex@exemple.fr", password: "motdepasse")

        XCTAssertFalse(harness.privacy.analyticsEnabled)
        XCTAssertTrue(harness.sink.names.isEmpty)
    }

    func testSignInWhoseAccountCannotBeReadCollectsNothing() async {
        let harness = SessionHarness()
        harness.accounts.result = .unavailable
        await harness.manager.startAndResolve()

        _ = await harness.manager.signInWithEmail(email: "alex@exemple.fr", password: "motdepasse")

        XCTAssertEqual(harness.manager.state, .lookupFailed)
        XCTAssertFalse(harness.privacy.analyticsEnabled)
    }

    func testAFailedSignInReturnsTheFailureAndChangesNothing() async {
        let harness = SessionHarness()
        harness.gateway.signInResult = .failure(.invalidCredentials)
        await harness.manager.startAndResolve()

        let failure = await harness.manager.signInWithEmail(email: "alex@exemple.fr", password: "mauvais")

        XCTAssertEqual(failure, .invalidCredentials)
        XCTAssertEqual(harness.manager.state, ready(.signUp))
        XCTAssertTrue(harness.sink.names.isEmpty)
    }

    // MARK: - Google et Apple

    func testGoogleFromSignInForANewAccountShowsThePanelAndNothingIsCollectedOrWritten() async {
        let harness = SessionHarness()
        await harness.manager.startAndResolve()

        let failure = await harness.manager.signIn(
            with: .google(idToken: "id", accessToken: "acc"), signUpConsent: nil)

        XCTAssertNil(failure)
        XCTAssertEqual(harness.manager.state, ready(.consentPanel))
        XCTAssertFalse(harness.privacy.analyticsEnabled)
        XCTAssertNil(harness.pending.stored)
        XCTAssertTrue(harness.sink.names.isEmpty)
    }

    func testGoogleFromSignInForAnExistingAccountGoesStraightThroughWithoutThePanel() async {
        let harness = SessionHarness()
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        await harness.manager.startAndResolve()

        _ = await harness.manager.signIn(with: .google(idToken: "id", accessToken: "acc"), signUpConsent: nil)

        XCTAssertEqual(harness.manager.state, ready(.feed))
        XCTAssertEqual(harness.sink.names, ["sign_in_completed"])
        XCTAssertEqual(harness.sink.events.first?.params["method"] as? String, "google")
    }

    func testAppleFromSignInUsesTheAppleMethod() async {
        let harness = SessionHarness()
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        await harness.manager.startAndResolve()

        _ = await harness.manager.signIn(with: .apple(idToken: "id", rawNonce: "n", name: nil), signUpConsent: nil)

        XCTAssertEqual(harness.sink.events.first?.params["method"] as? String, "apple")
    }

    func testGoogleFromSignUpForANewAccountRecordsTheAcceptanceAndSendsSignUp() async {
        let harness = SessionHarness()
        await harness.manager.startAndResolve()

        _ = await harness.manager.signIn(with: .google(idToken: "id", accessToken: "acc"), signUpConsent: .implicit)

        // Pas de panneau : le geste vaut acceptation, l'étape profil s'ouvre directement.
        XCTAssertEqual(harness.manager.state, ready(.profile))
        XCTAssertTrue(harness.privacy.analyticsEnabled)
        XCTAssertEqual(harness.sink.names, ["sign_up_completed"])
        XCTAssertEqual(harness.pending.stored?.termsVersion, ConsentVersions.terms)
    }

    func testGoogleFromSignUpWhenTheAccountAlreadyExistsTheAccountChoiceRules() async {
        let harness = SessionHarness()
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: false)
        await harness.manager.startAndResolve()

        _ = await harness.manager.signIn(with: .google(idToken: "id", accessToken: "acc"), signUpConsent: .implicit)

        XCTAssertEqual(harness.manager.state, ready(.feed))
        XCTAssertFalse(harness.privacy.analyticsEnabled)
        XCTAssertTrue(harness.sink.names.isEmpty)
        // Rien n'est gardé pour un compte qui a déjà un profil.
        XCTAssertNil(harness.pending.stored)
    }

    // MARK: - Panneau de consentement

    private func openPanel(_ harness: SessionHarness) async {
        await harness.manager.startAndResolve()
        _ = await harness.manager.signIn(with: .google(idToken: "id", accessToken: "acc"), signUpConsent: nil)
        XCTAssertEqual(harness.manager.state, ready(.consentPanel))
    }

    func testPanelContinueRecordsBothSwitchesOnAndSendsSignUpCompleted() async {
        let harness = SessionHarness()
        harness.gateway.socialResult = .success(uid: "uid-1", isNewUser: true)
        await openPanel(harness)
        harness.gateway.setSignedIn("uid-1", method: .google)

        let accepted = harness.manager.acceptConsentPanel(.implicit)

        XCTAssertTrue(accepted)
        XCTAssertEqual(harness.manager.state, ready(.profile))
        XCTAssertTrue(harness.privacy.analyticsEnabled)
        XCTAssertEqual(harness.sink.names, ["sign_up_completed"])
        XCTAssertEqual(harness.pending.stored?.analyticsAccepted, true)
    }

    func testPanelContinueTwiceRecordsOnceAndSendsOneEvent() async {
        let harness = SessionHarness()
        await openPanel(harness)

        XCTAssertTrue(harness.manager.acceptConsentPanel(.implicit))
        XCTAssertFalse(harness.manager.acceptConsentPanel(.implicit))

        XCTAssertEqual(harness.sink.names, ["sign_up_completed"])
    }

    func testPanelContinueWithoutTheTermsDoesNothing() async {
        let harness = SessionHarness()
        await openPanel(harness)

        XCTAssertFalse(harness.manager.acceptConsentPanel(ConsentChoice(termsAccepted: false)))

        XCTAssertEqual(harness.manager.state, ready(.consentPanel))
        XCTAssertNil(harness.pending.stored)
    }

    func testPanelRefuseDeletesTheAccountKeepsNothingSendsNothingAndComesBackToSignIn() async {
        let harness = SessionHarness()
        await openPanel(harness)
        let source = FakeSocialSource()

        let result = await harness.manager.refuseConsentPanel(source: source)

        XCTAssertEqual(result, .done)
        XCTAssertEqual(harness.gateway.deleteCalls, 1)
        XCTAssertNil(harness.gateway.currentUid)
        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertNil(harness.pending.stored)
        XCTAssertTrue(harness.sink.names.isEmpty)
        XCTAssertFalse(harness.privacy.analyticsEnabled)
        XCTAssertEqual(harness.manager.consumeNotice(), .accountNotCreated)
        // Le cache est purgé (ticket §7.6) et un refus ne compte pas comme « app déjà utilisée ».
        XCTAssertEqual(harness.cache.purgeCalls, 1)
        XCTAssertFalse(harness.flags.value(forKey: SessionManager.keyReturningUser, default: false))
        // Le message ne s'affiche qu'une fois.
        XCTAssertNil(harness.manager.consumeNotice())
    }

    func testPanelRefuseWhenTheDeletionFailsStaysOnThePanel() async {
        let harness = SessionHarness()
        await openPanel(harness)
        harness.gateway.deleteResults = [.network]

        let result = await harness.manager.refuseConsentPanel(source: FakeSocialSource())

        XCTAssertEqual(result, .failed(.network))
        XCTAssertEqual(harness.manager.state, ready(.consentPanel))
        XCTAssertEqual(harness.gateway.currentUid, "uid-1")
    }

    func testPanelRefuseAfterAnOldSessionAsksForARecentSignInThenDeletes() async {
        let harness = SessionHarness()
        await openPanel(harness)
        harness.gateway.deleteResults = [.recentLoginRequired]
        let source = FakeSocialSource()

        let result = await harness.manager.refuseConsentPanel(source: source)

        XCTAssertEqual(result, .done)
        XCTAssertEqual(source.googleCalls, 1)
        XCTAssertEqual(harness.gateway.reauthCalls, 1)
        XCTAssertEqual(harness.gateway.deleteCalls, 2)
        XCTAssertEqual(harness.manager.state, ready(.signIn))
    }

    func testPanelRefuseOfAnAppleAccountAsksAppleNotGoogle() async {
        let harness = SessionHarness()
        harness.gateway.socialResult = .success(uid: "uid-1", isNewUser: true)
        await harness.manager.startAndResolve()
        _ = await harness.manager.signIn(with: .apple(idToken: "id", rawNonce: "n", name: nil), signUpConsent: nil)
        harness.gateway.deleteResults = [.recentLoginRequired]
        let source = FakeSocialSource()

        let result = await harness.manager.refuseConsentPanel(source: source)

        XCTAssertEqual(result, .done)
        XCTAssertEqual(source.appleCalls, 1)
        XCTAssertEqual(source.googleCalls, 0)
    }

    func testPanelRefuseClosingTheRecentSignInDialogChangesNothing() async {
        let harness = SessionHarness()
        await openPanel(harness)
        harness.gateway.deleteResults = [.recentLoginRequired]
        let source = FakeSocialSource()
        source.google = .cancelled

        let result = await harness.manager.refuseConsentPanel(source: source)

        XCTAssertEqual(result, .cancelled)
        XCTAssertEqual(harness.manager.state, ready(.consentPanel))
        XCTAssertEqual(harness.gateway.currentUid, "uid-1")
        XCTAssertEqual(harness.gateway.deleteCalls, 1)
    }

    func testPanelRefuseOfAnEmailAccountNeverOpensAGoogleDialogAndKeepsThePanel() async {
        let harness = SessionHarness()
        await harness.manager.startAndResolve()
        harness.gateway.setSignedIn("uid-1", method: .email)
        harness.manager.retry()
        await harness.waitUntil { harness.manager.state == .ready(.consentPanel) }
        harness.gateway.deleteResults = [.recentLoginRequired]
        let source = FakeSocialSource()

        let result = await harness.manager.refuseConsentPanel(source: source)

        XCTAssertEqual(result, .failed(.unknown))
        XCTAssertEqual(source.googleCalls, 0)
        XCTAssertEqual(source.appleCalls, 0)
        XCTAssertEqual(harness.manager.state, ready(.consentPanel))
    }

    func testAnAccountThatBelongsToAMiffIsNeverDeletedByARefusal() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, hasTermsVersion: false)
        await harness.manager.startAndResolve()

        let result = await harness.manager.refuseConsentPanel(source: FakeSocialSource())

        XCTAssertEqual(result, .failed(.unknown))
        XCTAssertEqual(harness.gateway.deleteCalls, 0)
    }

    func testTheSessionLossWatcherDoesNotReactToOurOwnRefusal() async {
        let harness = SessionHarness()
        await openPanel(harness)

        _ = await harness.manager.refuseConsentPanel(source: FakeSocialSource())
        await harness.settle()

        // Un seul message « compte non créé », pas « session perdue ».
        XCTAssertEqual(harness.manager.consumeNotice(), .accountNotCreated)
        XCTAssertEqual(harness.manager.state, ready(.signIn))
    }

    // MARK: - Fin de session

    func testSignOutStopsListenersFlushesWritesPurgesTheCacheThenClosesTheSession() async {
        var stopped = 0
        let harness = SessionHarness(teardown: [{ stopped += 1 }])
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        await harness.manager.startAndResolve()

        await harness.manager.signOut()

        XCTAssertEqual(stopped, 1)
        XCTAssertEqual(harness.timeline.entries.suffix(4), ["markPending", "flush", "purge", "signOut"])
        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertNil(harness.gateway.currentUid)
        XCTAssertFalse(harness.privacy.analyticsEnabled)
        // Le crash éventuel d'une session finie n'est plus envoyé : la collecte est coupée ET effacée.
        XCTAssertEqual(harness.privacy.log.last, "off")
    }

    func testSignOutForgetsTheAcceptanceAndTheDraftOfThisDevice() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.pending.save(PendingConsent(uid: "uid-1", termsVersion: "0.1", analyticsAccepted: true))
        await harness.manager.startAndResolve()

        await harness.manager.signOut()

        XCTAssertNil(harness.pending.stored)
        XCTAssertTrue(harness.flags.value(forKey: SessionManager.keyReturningUser, default: false))
    }

    func testAfterASignOutAnotherPersonStartsFromZero() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        await harness.manager.startAndResolve()
        let generation = harness.manager.generation

        await harness.manager.signOut()

        XCTAssertGreaterThan(harness.manager.generation, generation)
        XCTAssertNil(harness.manager.accountSnapshot)
    }

    func testASessionLostBehindOurBackPurgesAndExplainsKindly() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        await harness.manager.startAndResolve()
        XCTAssertEqual(harness.manager.state, ready(.feed))

        // Compte désactivé depuis la console : la session disparaît sans que l'app l'ait voulu.
        harness.gateway.setSignedIn(nil)
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }

        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertEqual(harness.manager.consumeNotice(), .accountUnavailable)
        XCTAssertEqual(harness.cache.purgeCalls, 1)
        XCTAssertFalse(harness.privacy.analyticsEnabled)
    }

    func testTheWatcherIgnoresSignOutsWhenNoOneWasSignedIn() async {
        let harness = SessionHarness()
        await harness.manager.startAndResolve()

        harness.gateway.setSignedIn(nil)
        await harness.settle()

        XCTAssertEqual(harness.manager.state, ready(.signUp))
        XCTAssertNil(harness.manager.consumeNotice())
        XCTAssertEqual(harness.cache.purgeCalls, 0)
    }

    func testASessionLostWhileTheAccountIsBeingReadIsStillCleanedUp() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true)
        harness.accounts.onLoad = { harness.gateway.setSignedIn(nil) }

        await harness.manager.startAndResolve()
        await harness.waitUntil { harness.manager.state == .ready(.signIn) }

        XCTAssertEqual(harness.manager.state, ready(.signIn))
        XCTAssertEqual(harness.manager.consumeNotice(), .accountUnavailable)
    }

    // MARK: - Profil enregistré

    func testAfterTheProfileIsSavedTheAppMovesOnToWhatTheAccountNeeds() async {
        let harness = SessionHarness()
        await harness.manager.startAndResolve()
        _ = await harness.manager.createAccountWithEmail(
            email: "alex@exemple.fr", password: "motdepasse", consent: .implicit)
        harness.accounts.setLoaded(profileExists: true, hasMembership: false, analyticsConsent: true)

        await harness.manager.onProfileSaved(analyticsConsentEnabled: true)

        XCTAssertEqual(harness.manager.state, ready(.miffChoice))
        // Le consentement en attente et le brouillon ne servent plus.
        XCTAssertNil(harness.pending.stored)
        XCTAssertGreaterThanOrEqual(harness.drafts.clearCalls, 1)
    }

    func testIfTheReadFailsRightAfterASaveTheAppMovesOnFromWhatWasWritten() async {
        let harness = SessionHarness()
        await harness.manager.startAndResolve()
        _ = await harness.manager.createAccountWithEmail(
            email: "alex@exemple.fr", password: "motdepasse", consent: .implicit)
        harness.accounts.result = .unavailable

        await harness.manager.onProfileSaved(analyticsConsentEnabled: true)

        // Jamais un écran « réessayer » juste après un succès : un nouveau profil n'a pas encore de Miff.
        XCTAssertEqual(harness.manager.state, ready(.miffChoice))
    }

    func testProfileAlreadySavedTellsALateFirstTryFromARealRefusal() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        await harness.manager.startAndResolve()

        harness.accounts.setLoaded(profileExists: true, hasPhone: true)
        let saved = await harness.manager.profileAlreadySaved()
        harness.accounts.setLoaded(profileExists: true, hasPhone: false)
        let withoutPhone = await harness.manager.profileAlreadySaved()
        harness.accounts.result = .unavailable
        let unknown = await harness.manager.profileAlreadySaved()

        XCTAssertTrue(saved)
        XCTAssertFalse(withoutPhone)
        XCTAssertFalse(unknown)
    }

    func testTheStateObserverIsToldOfEveryChange() async {
        let harness = SessionHarness()
        var seen: [SessionState] = []
        harness.manager.onStateChange = { seen.append($0) }

        await harness.manager.startAndResolve()

        XCTAssertEqual(seen.last, ready(.signUp))
    }

    func testTheEventNamesAreTheOnesOfTheContract() {
        XCTAssertEqual(AnalyticsEvent.signUpCompleted(method: .email).name, "sign_up_completed")
        XCTAssertEqual(AnalyticsEvent.signInCompleted(method: .apple).name, "sign_in_completed")
    }
}
