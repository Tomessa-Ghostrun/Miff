// swiftlint:disable file_length type_body_length -- scenarios de test longs par nature, un cas par methode
import XCTest
@testable import Miff

private struct FakePhoto: DecodedPhoto {
    let width: Int
    let height: Int
}

private final class FakeProfiles: UserProfileRepository {
    var createResult: ProfileWriteResult = .done
    var addPhoneResult: ProfileWriteResult = .done
    var deleteResult: ProfileWriteResult = .done
    var seed: ProfileSeed?
    private(set) var created: [NewProfile] = []
    private(set) var consents: [PendingConsent] = []
    private(set) var phonesAdded: [String] = []
    private(set) var deletes = 0

    func create(uid: String, profile: NewProfile, consent: PendingConsent) async -> ProfileWriteResult {
        created.append(profile)
        consents.append(consent)
        return createResult
    }

    func addPhone(uid: String, phone: String) async -> ProfileWriteResult {
        phonesAdded.append(phone)
        return addPhoneResult
    }

    func delete(uid: String) async -> ProfileWriteResult {
        deletes += 1
        return deleteResult
    }

    func read(uid: String) async -> ProfileSeed? {
        seed
    }
}

private final class FakeAvatars: AvatarCodec {
    var decoded: (any DecodedPhoto)? = FakePhoto(width: 1_200, height: 800)
    var encoded: Data? = Data(count: 15_000)
    private(set) var encodedCrops: [SquareCrop] = []

    func decode(_ source: Data) async -> (any DecodedPhoto)? {
        decoded
    }

    func encode(_ photo: any DecodedPhoto, crop: SquareCrop) async -> Data? {
        encodedCrops.append(crop)
        return encoded
    }
}

/// Une bibliothèque de numéros factice : trois saisies connues, tout le reste est invalide.
private struct FakePhoneTools: PhoneNumberTools {
    func countries(locale: Locale) -> [PhoneCountry] { [] }
    func country(region: String, locale: Locale) -> PhoneCountry {
        PhoneCountry(region: region, callingCode: region == "FR" ? 33 : 32, name: region)
    }
    func isSupportedRegion(_ region: String?) -> Bool {
        ["FR", "BE"].contains(region?.uppercased() ?? "")
    }
    func exampleHint(region: String) -> String? { nil }
    func normalize(region: String, input: String) -> PhoneResult {
        switch input {
        case "6 12 34 56 78":
            return .valid(e164: "+33612345678", region: "FR")
        case "0800 12 34 56":
            return .unsupportedType
        default:
            return .invalid
        }
    }
    func detectInternational(_ input: String) -> DetectedNumber? {
        input == "+32 470 12 34 56" ? DetectedNumber(region: "BE", national: "470123456") : nil
    }
}

@MainActor
final class ProfileViewModelTests: XCTestCase {
    private struct Setup {
        let viewModel: ProfileViewModel
        let harness: SessionHarness
        let profiles: FakeProfiles
        let avatars: FakeAvatars
        let network: FakeNetwork
    }

    /// Une personne connectée dont l'acceptation des conditions est enregistrée : l'écran du profil est ouvert.
    private func make(
        mode: OnboardingMode = .full,
        online: Bool = true,
        displayName: String? = nil,
        drafts existing: MemoryDraftStore? = nil
    ) async -> Setup {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1", method: .google)
        harness.gateway.name = displayName
        harness.pending.save(PendingConsent(uid: "uid-1", termsVersion: "0.1", analyticsAccepted: true))
        await harness.manager.startAndResolve()
        let profiles = FakeProfiles()
        let avatars = FakeAvatars()
        let network = FakeNetwork(online: online)
        let dependencies = ProfileDependencies(
            session: harness.manager,
            pending: harness.pending,
            drafts: existing ?? harness.drafts,
            profiles: profiles,
            avatars: avatars,
            network: network,
            phoneTools: FakePhoneTools(),
            deviceRegion: { "BE" }
        )
        let viewModel = ProfileViewModel(mode: mode, dependencies: dependencies)
        return Setup(viewModel: viewModel, harness: harness, profiles: profiles, avatars: avatars, network: network)
    }

    private func fillAndGoToThePhoneStep(_ setup: Setup) {
        setup.viewModel.onFirstNameChanged("Camille")
        setup.viewModel.onLastNameChanged("Dupont")
        setup.viewModel.onContinueFromProfile()
    }

    private func waitForTheWrite(_ setup: Setup) async {
        await setup.harness.waitUntil { !setup.viewModel.state.isWriting }
    }

    // MARK: - Étape profil

    func testTheProviderNameIsOnlyASuggestionAndTheDraftWins() async {
        let suggested = await make(displayName: "Camille Dupont")
        XCTAssertEqual(suggested.viewModel.state.firstName, "Camille")
        XCTAssertEqual(suggested.viewModel.state.lastName, "Dupont")

        let drafts = MemoryDraftStore()
        drafts.save(
            ProfileDraft(
                uid: "uid-1", step: .phone, firstName: "Léa", lastName: "Martin",
                regionCode: "BE", phoneText: "470 12", avatar: nil))
        let restored = await make(displayName: "Camille Dupont", drafts: drafts)
        XCTAssertEqual(restored.viewModel.state.firstName, "Léa")
        XCTAssertEqual(restored.viewModel.state.step, .phone)
        XCTAssertEqual(restored.viewModel.state.regionCode, "BE")
        XCTAssertEqual(restored.viewModel.state.phoneText, "470 12")
    }

    func testTheDeviceRegionIsTheDefaultCountry() async {
        let setup = await make()

        XCTAssertEqual(setup.viewModel.state.regionCode, "BE")
    }

    func testTheArrowOfTheProfileStepNeedsBothNames() async {
        let setup = await make()

        setup.viewModel.onFirstNameChanged("Camille")
        XCTAssertFalse(setup.viewModel.state.canContinueFromProfile)
        setup.viewModel.onLastNameChanged("   ")
        XCTAssertFalse(setup.viewModel.state.canContinueFromProfile)
        setup.viewModel.onLastNameChanged("Dupont")
        XCTAssertTrue(setup.viewModel.state.canContinueFromProfile)
    }

    func testNothingIsWrittenAtTheProfileStepOnlyTheNextStepAndTheDraft() async {
        let setup = await make()

        fillAndGoToThePhoneStep(setup)

        XCTAssertEqual(setup.viewModel.state.step, .phone)
        XCTAssertTrue(setup.profiles.created.isEmpty)
        XCTAssertEqual(setup.harness.drafts.stored?.step, .phone)
        XCTAssertEqual(setup.harness.drafts.stored?.firstName, "Camille")
    }

    func testTheStepDoesNotMoveOnWithoutBothNames() async {
        let setup = await make()

        setup.viewModel.onFirstNameChanged("Camille")
        setup.viewModel.onContinueFromProfile()

        XCTAssertEqual(setup.viewModel.state.step, .profile)
    }

    func testTypingBeyondFiftyCharactersIsCutAndSaid() async {
        let setup = await make()

        setup.viewModel.onFirstNameChanged(String(repeating: "a", count: 60))

        XCTAssertEqual(setup.viewModel.state.firstName.utf16.count, 50)
        XCTAssertTrue(setup.viewModel.state.firstNameLimitHit)
        setup.viewModel.onFirstNameChanged("Camille")
        XCTAssertFalse(setup.viewModel.state.firstNameLimitHit)
    }

    func testBackToTheProfileStepKeepsEverythingTyped() async {
        let setup = await make()
        fillAndGoToThePhoneStep(setup)
        setup.viewModel.onPhoneChanged("6 12")

        setup.viewModel.onBackToProfile()

        XCTAssertEqual(setup.viewModel.state.step, .profile)
        XCTAssertEqual(setup.viewModel.state.firstName, "Camille")
        XCTAssertEqual(setup.viewModel.state.phoneText, "6 12")
    }

    // MARK: - Étape téléphone et écriture

    func testTheWholeFlowWritesTheProfileOnceThenMovesOn() async {
        let setup = await make(displayName: nil)
        fillAndGoToThePhoneStep(setup)
        setup.viewModel.onCountrySelected("FR")
        setup.viewModel.onPhoneChanged("6 12 34 56 78")
        setup.harness.accounts.setLoaded(profileExists: true, hasMembership: false, analyticsConsent: true)

        setup.viewModel.onSubmitPhone()
        XCTAssertTrue(setup.viewModel.state.isWriting)
        await waitForTheWrite(setup)

        XCTAssertEqual(
            setup.profiles.created,
            [NewProfile(firstName: "Camille", lastName: "Dupont", avatarJpeg: nil, phone: "+33612345678")])
        XCTAssertEqual(setup.profiles.consents.first?.termsVersion, ConsentVersions.terms)
        // L'écran suivant n'apparaît qu'après la confirmation du serveur.
        XCTAssertEqual(setup.harness.manager.state, .ready(.miffChoice))
        XCTAssertNil(setup.harness.drafts.stored)
        // Rien de ce qui a été saisi ne reste en mémoire dans le view model.
        XCTAssertEqual(setup.viewModel.state.firstName, "")
        XCTAssertEqual(setup.viewModel.state.phoneText, "")
    }

    func testOfflineWritesNothingAndKeepsTheFields() async {
        let setup = await make(online: false)
        fillAndGoToThePhoneStep(setup)
        setup.viewModel.onPhoneChanged("6 12 34 56 78")

        setup.viewModel.onSubmitPhone()

        XCTAssertEqual(setup.viewModel.state.message, .network)
        XCTAssertFalse(setup.viewModel.state.isWriting)
        XCTAssertTrue(setup.profiles.created.isEmpty)
        XCTAssertEqual(setup.viewModel.state.phoneText, "6 12 34 56 78")
    }

    func testAnInvalidNumberStaysOnThePhoneWithACalmMessageAndNoRequest() async {
        let setup = await make()
        fillAndGoToThePhoneStep(setup)
        setup.viewModel.onPhoneChanged("12 34")

        setup.viewModel.onSubmitPhone()

        XCTAssertTrue(setup.viewModel.state.phoneInvalid)
        XCTAssertFalse(setup.viewModel.state.phoneTypeRefused)
        XCTAssertTrue(setup.profiles.created.isEmpty)
        // Modifier le numéro retire l'erreur.
        setup.viewModel.onPhoneChanged("12 345")
        XCTAssertFalse(setup.viewModel.state.phoneInvalid)
    }

    func testAPremiumRateNumberGetsAMorePreciseSentence() async {
        let setup = await make()
        fillAndGoToThePhoneStep(setup)
        setup.viewModel.onPhoneChanged("0800 12 34 56")

        setup.viewModel.onSubmitPhone()

        XCTAssertTrue(setup.viewModel.state.phoneInvalid)
        XCTAssertTrue(setup.viewModel.state.phoneTypeRefused)
        XCTAssertTrue(setup.profiles.created.isEmpty)
    }

    func testThePhoneArrowIsGreyUntilADigitIsTyped() async {
        let setup = await make()
        fillAndGoToThePhoneStep(setup)

        XCTAssertFalse(setup.viewModel.state.canSubmitPhone)
        setup.viewModel.onPhoneChanged("6")
        XCTAssertTrue(setup.viewModel.state.canSubmitPhone)
    }

    func testAnInternationalNumberChoosesItsCountryAndKeepsTheNationalPart() async {
        let setup = await make()
        fillAndGoToThePhoneStep(setup)

        setup.viewModel.onPhoneChanged("+32 470 12 34 56")

        XCTAssertEqual(setup.viewModel.state.regionCode, "BE")
        XCTAssertEqual(setup.viewModel.state.phoneText, "470123456")
    }

    func testAnUnknownCountryIsIgnored() async {
        let setup = await make()

        setup.viewModel.onCountrySelected("ZZ")

        XCTAssertEqual(setup.viewModel.state.regionCode, "BE")
        setup.viewModel.onCountrySelected("fr")
        XCTAssertEqual(setup.viewModel.state.regionCode, "FR")
    }

    func testRepeatedRefusalsAdviseComingBackLaterAndKeepEverything() async {
        let setup = await make()
        setup.profiles.createResult = .refused
        setup.harness.accounts.setLoaded(profileExists: false)
        fillAndGoToThePhoneStep(setup)
        setup.viewModel.onPhoneChanged("6 12 34 56 78")

        var messages: [ProfileMessage?] = []
        for _ in 0..<3 {
            setup.viewModel.onSubmitPhone()
            await waitForTheWrite(setup)
            messages.append(setup.viewModel.state.message)
        }

        XCTAssertEqual(messages, [.saveFailed, .saveFailed, .tryLater])
        XCTAssertEqual(setup.viewModel.state.firstName, "Camille")
        XCTAssertEqual(setup.viewModel.state.phoneText, "6 12 34 56 78")
    }

    func testANetworkFailureDoesNotCountAsARefusal() async {
        let setup = await make()
        setup.profiles.createResult = .offline
        fillAndGoToThePhoneStep(setup)
        setup.viewModel.onPhoneChanged("6 12 34 56 78")

        for _ in 0..<4 {
            setup.viewModel.onSubmitPhone()
            await waitForTheWrite(setup)
        }

        XCTAssertEqual(setup.viewModel.state.message, .network)
    }

    func testARefusalOnAProfileThatIsAlreadyThereIsTreatedAsSaved() async {
        let setup = await make()
        setup.profiles.createResult = .refused
        // Le premier essai, arrivé en retard, a abouti : le serveur détient déjà un profil complet.
        setup.harness.accounts.setLoaded(profileExists: true, hasMembership: false, analyticsConsent: true)
        fillAndGoToThePhoneStep(setup)
        setup.viewModel.onPhoneChanged("6 12 34 56 78")

        setup.viewModel.onSubmitPhone()
        await waitForTheWrite(setup)

        XCTAssertEqual(setup.harness.manager.state, .ready(.miffChoice))
        XCTAssertNil(setup.viewModel.state.message)
    }

    func testWithoutTheRecordedAcceptanceTheFlowGoesBackToTheConsentPanelAndWritesNothing() async {
        let setup = await make()
        fillAndGoToThePhoneStep(setup)
        setup.viewModel.onPhoneChanged("6 12 34 56 78")
        setup.harness.pending.clear()

        setup.viewModel.onSubmitPhone()
        await waitForTheWrite(setup)
        await setup.harness.waitUntil { setup.harness.manager.state == .ready(.consentPanel) }

        XCTAssertTrue(setup.profiles.created.isEmpty)
        XCTAssertEqual(setup.harness.manager.state, .ready(.consentPanel))
    }

    func testNothingIsWrittenForAnotherAccount() async {
        let setup = await make()
        fillAndGoToThePhoneStep(setup)
        setup.viewModel.onPhoneChanged("6 12 34 56 78")
        setup.harness.gateway.setSignedIn("uid-2")

        setup.viewModel.onSubmitPhone()

        XCTAssertTrue(setup.profiles.created.isEmpty)
        XCTAssertFalse(setup.viewModel.state.isWriting)
    }

    // MARK: - Ancien compte sans numéro

    func testAnOlderAccountOnlyGetsItsPhoneNumberAdded() async {
        let setup = await make(mode: .phoneOnly)
        setup.harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        setup.viewModel.onPhoneChanged("6 12 34 56 78")

        XCTAssertEqual(setup.viewModel.state.step, .phone)
        setup.viewModel.onSubmitPhone()
        await waitForTheWrite(setup)

        XCTAssertEqual(setup.profiles.phonesAdded, ["+33612345678"])
        XCTAssertTrue(setup.profiles.created.isEmpty)
        XCTAssertEqual(setup.harness.manager.state, .ready(.feed))
    }

    func testAProfileOlderThanTheTermsIsDeletedAndCreatedAgainWithItsNamesKept() async {
        let setup = await make(mode: .phoneOnly)
        setup.harness.accounts.setLoaded(profileExists: true, hasMembership: false, hasTermsVersion: false)
        await setup.harness.manager.startAndResolve()
        setup.harness.manager.retry()
        await setup.harness.waitUntil { setup.harness.manager.accountSnapshot?.hasTermsVersion == false }
        setup.profiles.seed = ProfileSeed(firstName: "Léa", lastName: "Martin", avatarJpeg: Data(count: 100))
        // Une fois le profil supprimé, le compte n'en a plus : la relecture qui suit ne doit pas effacer le brouillon.
        setup.harness.accounts.setLoaded(profileExists: false)
        setup.viewModel.onPhoneChanged("6 12 34 56 78")

        setup.viewModel.onSubmitPhone()
        await waitForTheWrite(setup)

        XCTAssertEqual(setup.profiles.deletes, 1)
        XCTAssertTrue(setup.profiles.phonesAdded.isEmpty)
        // Les noms et la photo lus AVANT la suppression sont dans le brouillon : rien à retaper.
        XCTAssertEqual(setup.harness.drafts.stored?.firstName, "Léa")
        XCTAssertEqual(setup.harness.drafts.stored?.avatar, Data(count: 100))
        XCTAssertEqual(setup.harness.drafts.stored?.step, .profile)
    }

    func testAnOlderAccountThatBelongsToAMiffIsNeverDeleted() async {
        let setup = await make(mode: .phoneOnly)
        setup.harness.accounts.setLoaded(profileExists: true, hasMembership: true, hasTermsVersion: false)
        setup.harness.manager.retry()
        await setup.harness.waitUntil { setup.harness.manager.accountSnapshot?.hasTermsVersion == false }
        setup.viewModel.onPhoneChanged("6 12 34 56 78")

        setup.viewModel.onSubmitPhone()
        await waitForTheWrite(setup)

        XCTAssertEqual(setup.profiles.deletes, 0)
        XCTAssertEqual(setup.viewModel.state.message, .accountUnavailable)
    }

    // MARK: - Photo

    func testAPhotoThatCannotBeReadGivesAKindMessageAndNothingChanges() async {
        let setup = await make()
        setup.avatars.decoded = nil

        setup.viewModel.onPhotoChosen(Data(count: 10))
        await setup.harness.waitUntil { setup.viewModel.state.message != nil }

        XCTAssertEqual(setup.viewModel.state.message, .photoUnusable)
        XCTAssertNil(setup.viewModel.state.crop)
        XCTAssertNil(setup.viewModel.state.avatar)
    }

    func testClosingThePickerChangesNothing() async {
        let setup = await make()

        setup.viewModel.onPhotoChosen(nil)

        XCTAssertNil(setup.viewModel.state.crop)
        XCTAssertNil(setup.viewModel.state.message)
    }

    func testAChosenPhotoOpensTheCropAtTheCenterThenTheCropIsConfirmedIntoTheAvatar() async {
        let setup = await make()

        setup.viewModel.onPhotoChosen(Data(count: 10))
        await setup.harness.waitUntil { setup.viewModel.state.crop != nil }
        let crop = setup.viewModel.state.crop
        XCTAssertEqual(crop?.zoom, 1)
        XCTAssertEqual(crop?.centerX, 600)
        XCTAssertEqual(crop?.centerY, 400)

        // Le carré reste dans la photo même si on le tire trop loin.
        setup.viewModel.onCropChanged(zoom: 2, centerX: -500, centerY: 5_000)
        XCTAssertEqual(setup.viewModel.state.crop?.centerX, 200)
        XCTAssertEqual(setup.viewModel.state.crop?.centerY, 600)

        setup.viewModel.onCropConfirmed()
        await setup.harness.waitUntil { setup.viewModel.state.crop == nil }

        XCTAssertEqual(setup.viewModel.state.avatar?.count, 15_000)
        XCTAssertEqual(setup.avatars.encodedCrops, [SquareCrop(left: 0, top: 400, size: 400)])
        XCTAssertEqual(setup.harness.drafts.stored?.avatar?.count, 15_000)
    }

    func testAnAvatarOverTheLimitIsRefusedEvenIfTheCodecLetsItThrough() async {
        let setup = await make()
        setup.avatars.encoded = Data(count: 20_001)
        setup.viewModel.onPhotoChosen(Data(count: 10))
        await setup.harness.waitUntil { setup.viewModel.state.crop != nil }

        setup.viewModel.onCropConfirmed()
        await setup.harness.waitUntil { setup.viewModel.state.crop == nil }

        XCTAssertNil(setup.viewModel.state.avatar)
        XCTAssertEqual(setup.viewModel.state.message, .photoUnusable)
    }

    func testCancellingTheCropKeepsNothingAndRemovingThePhotoWorks() async {
        let setup = await make()
        setup.viewModel.onPhotoChosen(Data(count: 10))
        await setup.harness.waitUntil { setup.viewModel.state.crop != nil }

        setup.viewModel.onCropCancelled()
        XCTAssertNil(setup.viewModel.state.crop)
        XCTAssertNil(setup.viewModel.state.avatar)

        setup.viewModel.onPhotoChosen(Data(count: 10))
        await setup.harness.waitUntil { setup.viewModel.state.crop != nil }
        setup.viewModel.onCropConfirmed()
        await setup.harness.waitUntil { setup.viewModel.state.avatar != nil }
        setup.viewModel.onRemovePhoto()
        XCTAssertNil(setup.viewModel.state.avatar)
    }

    // MARK: - Brouillon

    func testReopeningTheAppPutsThePersonBackWhereTheyWere() async {
        let first = await make()
        fillAndGoToThePhoneStep(first)
        first.viewModel.onPhoneChanged("6 12")

        // « Fermer l'app » : un nouveau view model relit le brouillon du même compte.
        let second = await make(drafts: first.harness.drafts)

        XCTAssertEqual(second.viewModel.state.step, .phone)
        XCTAssertEqual(second.viewModel.state.firstName, "Camille")
        XCTAssertEqual(second.viewModel.state.phoneText, "6 12")
    }
}
