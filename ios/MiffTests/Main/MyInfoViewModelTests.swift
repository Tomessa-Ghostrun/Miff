// Scénarios longs par nature (un cas par méthode) : les limites de longueur ne s'appliquent pas ici.
// swiftlint:disable file_length type_body_length
import XCTest
@testable import Miff

private struct FakePhoto: DecodedPhoto {
    let width: Int
    let height: Int
}

private final class FakeAvatars: AvatarCodec {
    var decoded: (any DecodedPhoto)? = FakePhoto(width: 1_200, height: 800)
    var encoded: Data? = Data(count: 15_000)
    private(set) var decodes = 0

    func decode(_ source: Data) async -> (any DecodedPhoto)? {
        decodes += 1
        return decoded
    }

    func encode(_ photo: any DecodedPhoto, crop: SquareCrop) async -> Data? {
        encoded
    }
}

/// Une bibliothèque de numéros factice : quelques saisies connues, tout le reste est invalide.
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
        switch (region, input) {
        case ("FR", "612345678"), ("FR", "6 12 34 56 78"), ("FR", "06 12 34 56 78"):
            return .valid(e164: "+33612345678", region: "FR")
        case ("FR", "699999999"):
            return .valid(e164: "+33699999999", region: "FR")
        case ("BE", "470123456"):
            return .valid(e164: "+32470123456", region: "BE")
        case ("FR", "0800 12 34 56"):
            return .unsupportedType
        default:
            return .invalid
        }
    }
    func detectInternational(_ input: String) -> DetectedNumber? {
        switch input {
        case "+33612345678":
            return DetectedNumber(region: "FR", national: "612345678")
        case "+334444":
            // Un numéro de l'ancien temps que la bibliothèque jugerait invalide s'il était saisi : jamais signalé tant qu'on n'y touche pas.
            return DetectedNumber(region: "FR", national: "4444")
        case "+32 470 12 34 56", "+32470123456":
            return DetectedNumber(region: "BE", national: "470123456")
        default:
            return nil
        }
    }
}

private final class FakeMyProfileRepository: MyProfileRepository {
    /// Les réponses, dans l'ordre ; `.saved` quand il n'y en a plus.
    var results: [ProfileSaveResult] = []
    var delayNanoseconds: UInt64 = 0
    private(set) var saved: [ProfileSummary] = []
    private(set) var uids: [String] = []

    func save(uid: String, profile: ProfileSummary) async -> ProfileSaveResult {
        uids.append(uid)
        saved.append(profile)
        if delayNanoseconds > 0 {
            try? await Task.sleep(nanoseconds: delayNanoseconds)
        }
        return results.isEmpty ? .saved : results.removeFirst()
    }
}

@MainActor
private final class FakeCurrentProfile: CurrentProfileHolding {
    private(set) var saved: [ProfileSummary] = []
    private(set) var uids: [String] = []

    func onSaved(uid: String, profile: ProfileSummary) {
        uids.append(uid)
        saved.append(profile)
    }
}

/// « Mes informations » : validations, enregistrement, messages (critères MI1 à MI11, MI13 et MI16 du ticket
/// `docs/tickets/modifier-infos-perso.md`).
@MainActor
final class MyInfoViewModelTests: XCTestCase {
    private struct Setup {
        let model: MyInfoViewModel
        let repository: FakeMyProfileRepository
        let current: FakeCurrentProfile
        let avatars: FakeAvatars
        let network: FakeNetwork
    }

    private let alex = ProfileSummary(firstName: "Alex", lastName: "Martin", phone: "+33612345678", avatarJpeg: nil)

    private func make(
        profile: ProfileSummary? = nil,
        online: Bool = true,
        uid: String? = "u1",
        deviceRegion: String? = "BE"
    ) -> Setup {
        let repository = FakeMyProfileRepository()
        let current = FakeCurrentProfile()
        let avatars = FakeAvatars()
        let network = FakeNetwork(online: online)
        let model = MyInfoViewModel(
            profile: profile ?? alex,
            dependencies: MyInfoDependencies(
                repository: repository,
                current: current,
                avatars: avatars,
                network: network,
                phoneTools: FakePhoneTools(),
                deviceRegion: { deviceRegion },
                currentUid: { uid }
            )
        )
        return Setup(model: model, repository: repository, current: current, avatars: avatars, network: network)
    }

    private func save(_ setup: Setup) async {
        setup.model.onSave()
        await waitUntil { !setup.model.state.isSaving }
    }

    // MARK: - Ouverture

    func testItOpensWithTheValuesOfTheProfileAndNothingToSave() {
        let setup = make()
        let state = setup.model.state

        XCTAssertEqual(state.firstName, "Alex")
        XCTAssertEqual(state.lastName, "Martin")
        XCTAssertEqual(state.regionCode, "FR")
        XCTAssertEqual(state.phoneText, "612345678", "le pays se déduit de l'indicatif, le numéro est montré sans lui")
        XCTAssertNil(state.avatar)
        XCTAssertFalse(state.hasChanges)
        XCTAssertFalse(state.hasEdits)
        XCTAssertFalse(state.canSave, "« Enregistrer » est grisé tant que rien n'a changé (MI5)")
        XCTAssertNil(state.message)
    }

    func testAnAccountWithoutANumberOpensWithTheCountryOfThePhoneAndNeedsOne() {
        let legacy = ProfileSummary(firstName: "Alex", lastName: "Martin", phone: nil, avatarJpeg: nil)
        let setup = make(profile: legacy, deviceRegion: "BE")

        XCTAssertEqual(setup.model.state.regionCode, "BE")
        XCTAssertEqual(setup.model.state.phoneText, "")

        setup.model.onFirstNameChanged("Alexandre")
        XCTAssertFalse(setup.model.state.canSave, "le numéro est obligatoire : jamais vidé, jamais absent (MI3)")
    }

    func testAnUnsupportedDeviceRegionFallsBackToFrance() {
        let legacy = ProfileSummary(firstName: "Alex", lastName: "Martin", phone: nil, avatarJpeg: nil)

        XCTAssertEqual(make(profile: legacy, deviceRegion: "ZZ").model.state.regionCode, "FR")
        XCTAssertEqual(make(profile: legacy, deviceRegion: nil).model.state.regionCode, "FR")
    }

    func testANameStoredWithRepeatedSpacesIsNotAChangeUntilTheyTouchIt() {
        let spaced = ProfileSummary(
            firstName: "Anne  Marie", lastName: "Martin", phone: "+33612345678", avatarJpeg: nil
        )
        let setup = make(profile: spaced)

        XCTAssertFalse(setup.model.state.hasChanges)
        XCTAssertFalse(setup.model.state.canSave)
    }

    // MARK: - Noms (MI1, MI2)

    func testChangingANameEnablesSaveAndPuttingItBackDisablesIt() {
        let setup = make()

        setup.model.onFirstNameChanged("Alexandre")
        XCTAssertTrue(setup.model.state.canSave)
        XCTAssertTrue(setup.model.state.hasEdits)

        setup.model.onFirstNameChanged("Alex")
        XCTAssertFalse(setup.model.state.canSave)
        XCTAssertFalse(setup.model.state.hasEdits)
    }

    func testAnEmptyNameIsOnlyPointedOutOnceTheFieldWasTouched() {
        let setup = make()
        XCTAssertFalse(setup.model.state.firstNameBlank)
        XCTAssertFalse(setup.model.state.lastNameBlank)

        setup.model.onFirstNameChanged("")

        XCTAssertTrue(setup.model.state.firstNameBlank)
        XCTAssertFalse(setup.model.state.lastNameBlank, "l'autre champ n'a pas été touché")
        XCTAssertFalse(setup.model.state.canSave)

        setup.model.onFirstNameChanged("   ")
        XCTAssertTrue(setup.model.state.firstNameBlank, "des espaces seuls ne sont pas un nom")
        XCTAssertFalse(setup.model.state.canSave)

        setup.model.onFirstNameChanged("Al")
        XCTAssertFalse(setup.model.state.firstNameBlank)
        XCTAssertTrue(setup.model.state.canSave, "aucun minimum de 3 caractères")
    }

    func testOneCharacterIsEnough() {
        let setup = make()

        setup.model.onFirstNameChanged("A")
        setup.model.onLastNameChanged("B")

        XCTAssertTrue(setup.model.state.canSave)
    }

    func testAFiftyFirstCharacterIsNotTypedAndTheLimitIsSaid() {
        let setup = make()

        setup.model.onLastNameChanged(String(repeating: "a", count: 51))

        XCTAssertEqual(setup.model.state.lastName.utf16.count, 50)
        XCTAssertTrue(setup.model.state.lastNameLimitHit)
        XCTAssertFalse(setup.model.state.firstNameLimitHit)

        setup.model.onLastNameChanged(String(repeating: "a", count: 50))
        XCTAssertFalse(setup.model.state.lastNameLimitHit)
    }

    func testANameOfEmojiIsCutAtTheLimitWithoutBreakingACharacter() {
        let setup = make()

        // Un émoji vaut 2 unités UTF-16, comme sur Android : 25 émojis = 50 unités.
        setup.model.onFirstNameChanged(String(repeating: "👍", count: 30))
        XCTAssertEqual(setup.model.state.firstName, String(repeating: "👍", count: 25))
        XCTAssertTrue(setup.model.state.firstNameLimitHit)

        // Une coupe au milieu d'une paire de substitution retire la paire entière.
        setup.model.onLastNameChanged("a" + String(repeating: "👍", count: 30))
        XCTAssertEqual(setup.model.state.lastName, "a" + String(repeating: "👍", count: 24))
        XCTAssertEqual(setup.model.state.lastName.utf16.count, 49)
        XCTAssertFalse(setup.model.state.lastName.contains("\u{FFFD}"))
    }

    func testControlAndReadingDirectionCharactersAreRemoved() {
        let setup = make()

        setup.model.onFirstNameChanged("Al\u{202E}ex\u{200B}\n")

        XCTAssertEqual(setup.model.state.firstName, "Alex ")
        XCTAssertFalse(setup.model.state.hasEdits, "un nom qui redevient celui du serveur n'est pas un changement")
    }

    func testTheCounterAppearsFromFortyFiveAndShowsTheRealCount() {
        XCTAssertNil(MyInfoUiState.counter(for: String(repeating: "a", count: 44)))
        XCTAssertEqual(MyInfoUiState.counter(for: String(repeating: "a", count: 45)), "45/50")
        XCTAssertEqual(MyInfoUiState.counter(for: String(repeating: "a", count: 50)), "50/50")
        XCTAssertEqual(MyInfoUiState.counter(for: String(repeating: "👍", count: 24)), "48/50", "en unités UTF-16")
    }

    // MARK: - Téléphone (MI3)

    func testWithoutADigitSaveStaysGrey() {
        let setup = make()
        setup.model.onFirstNameChanged("Alexandre")

        setup.model.onPhoneChanged("")
        XCTAssertFalse(setup.model.state.canSave)

        setup.model.onPhoneChanged("abc")
        XCTAssertFalse(setup.model.state.canSave, "un numéro sans chiffre n'est jamais envoyé")
    }

    func testANumberThatDoesNotLookRightIsPointedOutWhenTheFieldIsLeftAndNothingIsWritten() async {
        let setup = make()

        setup.model.onPhoneChanged("123")
        XCTAssertFalse(setup.model.state.phoneInvalid, "rien n'est dit pendant la frappe")
        setup.model.onPhoneFocusLost()

        XCTAssertTrue(setup.model.state.phoneInvalid)
        XCTAssertFalse(setup.model.state.phoneTypeRefused)
        XCTAssertFalse(setup.model.state.canSave)
        setup.model.onSave()
        XCTAssertTrue(setup.repository.saved.isEmpty)

        setup.model.onPhoneChanged("612345678")
        XCTAssertFalse(setup.model.state.phoneInvalid, "le message part dès que la saisie change")
    }

    func testAnUntouchedNumberIsNeverPointedOut() {
        let legacy = ProfileSummary(firstName: "Alex", lastName: "Martin", phone: "+334444", avatarJpeg: nil)
        let setup = make(profile: legacy)
        XCTAssertEqual(setup.model.state.phoneText, "4444")

        setup.model.onPhoneFocusLost()

        XCTAssertFalse(setup.model.state.phoneInvalid)
    }

    func testTouchingSaveWithABadNumberPointsItOutWithoutWritingAnything() {
        let setup = make()
        setup.model.onPhoneChanged("123")

        setup.model.onSave()

        XCTAssertTrue(setup.model.state.phoneInvalid)
        XCTAssertTrue(setup.repository.saved.isEmpty)
        XCTAssertFalse(setup.model.state.isSaving)
    }

    func testANumberOfARefusedKindHasItsOwnSentence() {
        let setup = make()
        setup.model.onPhoneChanged("0800 12 34 56")

        setup.model.onPhoneFocusLost()

        XCTAssertTrue(setup.model.state.phoneInvalid)
        XCTAssertTrue(setup.model.state.phoneTypeRefused)
    }

    func testPastingAnInternationalNumberChoosesTheCountry() {
        let setup = make()

        setup.model.onPhoneChanged("+32 470 12 34 56")

        XCTAssertEqual(setup.model.state.regionCode, "BE")
        XCTAssertEqual(setup.model.state.phoneText, "470123456")
        XCTAssertTrue(setup.model.state.canSave)
    }

    func testAnUnknownCountryIsIgnoredAndTheTypedTextIsLimited() {
        let setup = make()

        setup.model.onCountrySelected("ZZ")
        XCTAssertEqual(setup.model.state.regionCode, "FR")

        setup.model.onCountrySelected("be")
        XCTAssertEqual(setup.model.state.regionCode, "BE")

        setup.model.onPhoneChanged(String(repeating: "1", count: 100))
        XCTAssertEqual(setup.model.state.phoneText.count, PhoneInput.maxInputLength)
    }

    func testTheSameNumberIsAlwaysSavedAsTheSameE164() async {
        let first = make()
        first.model.onPhoneChanged("699999999")
        await save(first)
        let second = make()
        second.model.onPhoneChanged("699999999")
        second.model.onFirstNameChanged("Alexandre")
        await save(second)

        XCTAssertEqual(first.repository.saved.first?.phone, "+33699999999")
        XCTAssertEqual(second.repository.saved.first?.phone, "+33699999999")
    }

    // MARK: - Photo (MI4)

    func testAChosenPhotoOpensTheCropThenIsKeptOnceConfirmed() async {
        let setup = make()

        setup.model.onPhotoChosen(Data([1, 2, 3]))
        await waitUntil { setup.model.state.crop != nil }
        let crop = setup.model.state.crop
        XCTAssertEqual(crop?.centerX, 600)
        XCTAssertEqual(crop?.centerY, 400)
        XCTAssertNil(setup.model.state.avatar, "rien n'est gardé avant « Utiliser cette photo »")

        setup.model.onCropConfirmed()
        await waitUntil { setup.model.state.crop == nil }

        XCTAssertEqual(setup.model.state.avatar?.count, 15_000)
        XCTAssertTrue(setup.model.state.canSave)
        XCTAssertTrue(setup.model.state.hasEdits)
    }

    func testAnUnusableImageChangesNothingAndSaysSo() async {
        let setup = make()
        setup.avatars.decoded = nil

        setup.model.onPhotoChosen(Data([1]))
        await waitUntil { setup.model.state.message == .photoUnusable }

        XCTAssertEqual(setup.model.state.message, .photoUnusable)
        XCTAssertNil(setup.model.state.crop)
        XCTAssertNil(setup.model.state.avatar)
        XCTAssertFalse(setup.model.state.hasEdits)
    }

    func testAPickerThatGaveNothingIsIgnoredAndAFailedLoadSaysSo() {
        let setup = make()

        setup.model.onPhotoChosen(nil)
        XCTAssertNil(setup.model.state.message)
        XCTAssertEqual(setup.avatars.decodes, 0)

        setup.model.onPhotoLoadFailed()
        XCTAssertEqual(setup.model.state.message, .photoUnusable)
    }

    func testACroppedPhotoOverTwentyThousandBytesIsRefused() async {
        let setup = make()
        setup.avatars.encoded = Data(count: ProfileDocument.maxAvatarBytes + 1)
        setup.model.onPhotoChosen(Data([1]))
        await waitUntil { setup.model.state.crop != nil }

        setup.model.onCropConfirmed()
        await waitUntil { setup.model.state.crop == nil }

        XCTAssertNil(setup.model.state.avatar)
        XCTAssertEqual(setup.model.state.message, .photoUnusable)
    }

    func testAnEncoderThatFailsLeavesThePhotoAsItWas() async {
        let withPhoto = ProfileSummary(
            firstName: "Alex", lastName: "Martin", phone: "+33612345678", avatarJpeg: Data([9, 9])
        )
        let setup = make(profile: withPhoto)
        setup.avatars.encoded = nil
        setup.model.onPhotoChosen(Data([1]))
        await waitUntil { setup.model.state.crop != nil }

        setup.model.onCropConfirmed()
        await waitUntil { setup.model.state.crop == nil }

        XCTAssertEqual(setup.model.state.avatar, Data([9, 9]))
        XCTAssertEqual(setup.model.state.message, .photoUnusable)
        XCTAssertFalse(setup.model.state.hasEdits)
    }

    func testCancellingTheCropKeepsTheCurrentPhoto() async {
        let setup = make()
        setup.model.onPhotoChosen(Data([1]))
        await waitUntil { setup.model.state.crop != nil }

        setup.model.onCropCancelled()

        XCTAssertNil(setup.model.state.crop)
        XCTAssertNil(setup.model.state.avatar)
        XCTAssertFalse(setup.model.state.hasEdits)
    }

    func testTheCropStaysInsideThePhoto() async {
        let setup = make()
        setup.model.onPhotoChosen(Data([1]))
        await waitUntil { setup.model.state.crop != nil }

        setup.model.onCropChanged(zoom: 99, centerX: -5_000, centerY: 99_999)

        let crop = setup.model.state.crop
        XCTAssertEqual(crop?.zoom, CropMath.clampZoom(99))
        XCTAssertGreaterThanOrEqual(crop?.centerX ?? -1, 0)
        XCTAssertLessThanOrEqual(crop?.centerY ?? .infinity, 800)
    }

    func testRemovingThePhotoIsAChangeAndIsSavedAsNoPhoto() async {
        let withPhoto = ProfileSummary(
            firstName: "Alex", lastName: "Martin", phone: "+33612345678", avatarJpeg: Data([9, 9])
        )
        let setup = make(profile: withPhoto)
        XCTAssertFalse(setup.model.state.canSave)

        setup.model.onRemovePhoto()
        XCTAssertTrue(setup.model.state.canSave)
        XCTAssertNil(setup.model.state.avatar)

        await save(setup)
        XCTAssertNil(setup.repository.saved.first?.avatarJpeg)
        XCTAssertNil(setup.current.saved.first?.avatarJpeg)
    }

    // MARK: - Enregistrer (MI5, MI13)

    func testASaveWritesTheNormalizedValuesThenSaysSoAndPutsTheProfileInTheHeader() async {
        let setup = make()
        setup.model.onFirstNameChanged("  Alexandre  ")
        setup.model.onLastNameChanged("Martin   Dupont")
        setup.model.onPhoneChanged("06 12 34 56 78")

        await save(setup)

        let expected = ProfileSummary(
            firstName: "Alexandre", lastName: "Martin Dupont", phone: "+33612345678", avatarJpeg: nil
        )
        XCTAssertEqual(setup.repository.saved, [expected])
        XCTAssertEqual(setup.repository.uids, ["u1"])
        // L'en-tête du Profil change tout de suite, sans seconde lecture.
        XCTAssertEqual(setup.current.saved, [expected])
        XCTAssertEqual(setup.current.uids, ["u1"])
        XCTAssertEqual(setup.model.state.message, .saved)
        XCTAssertFalse(setup.model.state.canSave)
        XCTAssertFalse(setup.model.state.hasEdits, "plus rien à confirmer en quittant")
        XCTAssertEqual(setup.model.state.firstName, "Alexandre")
        XCTAssertEqual(setup.model.state.lastName, "Martin Dupont")
        XCTAssertEqual(setup.model.state.phoneText, "612345678")
    }

    func testTheInitialsFollowTheNewName() async {
        let setup = make()
        setup.model.onFirstNameChanged("zoé")
        setup.model.onLastNameChanged("lopez")

        await save(setup)

        XCTAssertEqual(setup.current.saved.first?.initials, "ZL")
    }

    func testOnlyOneSaveRunsAtATime() async {
        let setup = make()
        setup.repository.delayNanoseconds = 100_000_000
        setup.model.onFirstNameChanged("Alexandre")

        setup.model.onSave()
        XCTAssertTrue(setup.model.state.isSaving)
        XCTAssertFalse(setup.model.state.canSave, "« Enregistrer » est grisé pendant l'envoi")
        setup.model.onSave()
        setup.model.onSave()
        await waitUntil { !setup.model.state.isSaving }

        XCTAssertEqual(setup.repository.saved.count, 1)
    }

    func testNothingIsSavedWithoutAChange() {
        let setup = make()

        setup.model.onSave()

        XCTAssertTrue(setup.repository.saved.isEmpty)
        XCTAssertFalse(setup.model.state.isSaving)
    }

    func testNothingIsWrittenWhenNobodyIsSignedInAnyMore() {
        let setup = make(uid: nil)
        setup.model.onFirstNameChanged("Alexandre")

        setup.model.onSave()

        XCTAssertTrue(setup.repository.saved.isEmpty)
        XCTAssertFalse(setup.model.state.isSaving)
    }

    func testTheOnlyEditsOfASavedScreenCountFromTheSavedValues() async {
        let setup = make()
        setup.model.onFirstNameChanged("Alexandre")
        await save(setup)

        setup.model.onFirstNameChanged("Alex")

        XCTAssertTrue(setup.model.state.hasEdits)
        XCTAssertTrue(setup.model.state.canSave)
        setup.model.onFirstNameChanged("Alexandre")
        XCTAssertFalse(setup.model.state.hasEdits)
    }

    // MARK: - Hors connexion et refus (MI9, MI10, MI11)

    func testOfflineNothingIsSentTheMessageIsCalmAndTheFieldsAreKept() {
        let setup = make(online: false)
        setup.model.onFirstNameChanged("Alexandre")

        setup.model.onSave()

        XCTAssertEqual(setup.model.state.message, .network)
        XCTAssertTrue(setup.repository.saved.isEmpty, "rien n'est envoyé, rien n'est mis en file")
        XCTAssertEqual(setup.model.state.firstName, "Alexandre")
        XCTAssertTrue(setup.model.state.canSave)
        XCTAssertTrue(setup.model.state.hasEdits)
    }

    func testTheNetworkLostDuringTheSaveGivesTheSameMessageAndKeepsTheFields() async {
        let setup = make()
        setup.repository.results = [.offline]
        setup.model.onFirstNameChanged("Alexandre")

        await save(setup)

        XCTAssertEqual(setup.model.state.message, .network)
        XCTAssertEqual(setup.model.state.firstName, "Alexandre")
        XCTAssertTrue(setup.model.state.canSave)
        XCTAssertTrue(setup.current.saved.isEmpty, "rien n'est confirmé : l'en-tête ne change pas")
    }

    func testARefusalKeepsTheFieldsThenAdvisesToComeBackLaterAfterThree() async {
        let setup = make()
        setup.repository.results = [.refused, .failed, .refused, .refused]
        setup.model.onFirstNameChanged("Alexandre")

        await save(setup)
        XCTAssertEqual(setup.model.state.message, .saveFailed)
        XCTAssertEqual(setup.model.state.firstName, "Alexandre")
        await save(setup)
        XCTAssertEqual(setup.model.state.message, .saveFailed)
        await save(setup)
        XCTAssertEqual(setup.model.state.message, .tryLater)
        await save(setup)
        XCTAssertEqual(setup.model.state.message, .tryLater)
        XCTAssertTrue(setup.current.saved.isEmpty)
    }

    func testANetworkFailureDoesNotCountAsARefusalAndASuccessResetsTheCount() async {
        let setup = make()
        setup.repository.results = [.refused, .refused, .offline, .saved, .refused]
        setup.model.onFirstNameChanged("Alexandre")

        await save(setup)
        await save(setup)
        await save(setup)
        XCTAssertEqual(setup.model.state.message, .network)
        await save(setup)
        XCTAssertEqual(setup.model.state.message, .saved)

        setup.model.onFirstNameChanged("Alex")
        await save(setup)
        XCTAssertEqual(setup.model.state.message, .saveFailed, "le compte est reparti de zéro")
    }

    func testAnOlderAccountRefusedByTheRulesGetsTheNeutralMessage() async {
        // Un compte sans `termsVersion` : toute mise à jour est refusée par les règles. Rien de technique n'est dit.
        let setup = make()
        setup.repository.results = [.refused]
        setup.model.onFirstNameChanged("Alexandre")

        await save(setup)

        XCTAssertEqual(setup.model.state.message, .saveFailed)
        XCTAssertTrue(setup.model.state.canSave)
    }

    func testACopyStillToWriteKeepsSaveUsableAndSavingAgainFinishesTheJob() async {
        let setup = make()
        setup.repository.results = [.savedPartial, .saved]
        setup.model.onFirstNameChanged("Alexandre")

        await save(setup)

        XCTAssertEqual(setup.model.state.message, .syncPending)
        XCTAssertEqual(setup.current.saved.count, 1, "le profil, lui, est enregistré")
        XCTAssertFalse(setup.model.state.hasEdits)
        XCTAssertTrue(setup.model.state.canSave, "le bouton reste actif bien que rien n'ait changé")

        await save(setup)

        XCTAssertEqual(setup.repository.saved.count, 2)
        XCTAssertEqual(setup.repository.saved[0], setup.repository.saved[1], "les mêmes valeurs sont réécrites partout")
        XCTAssertEqual(setup.model.state.message, .saved)
        XCTAssertFalse(setup.model.state.canSave)
    }

    func testEditingClearsTheMessage() async {
        let setup = make()
        setup.model.onFirstNameChanged("Alexandre")
        await save(setup)
        XCTAssertEqual(setup.model.state.message, .saved)

        setup.model.onLastNameChanged("Durand")

        XCTAssertNil(setup.model.state.message)
    }
}

/// Le profil gardé en mémoire pour l'en-tête du Profil (et, plus tard, mon visage dans le carrousel).
@MainActor
final class CurrentProfileHoldingTests: XCTestCase {
    private final class Reader: ProfileReading {
        var result: ProfileSummary?
        var delayNanoseconds: UInt64 = 0

        init(_ result: ProfileSummary?) {
            self.result = result
        }

        func read(uid: String) async -> ProfileSummary? {
            if delayNanoseconds > 0 {
                try? await Task.sleep(nanoseconds: delayNanoseconds)
            }
            return result
        }
    }

    private let before = ProfileSummary(firstName: "Alex", lastName: "Martin", phone: "+33612345678", avatarJpeg: nil)
    private let after = ProfileSummary(
        firstName: "Zoé", lastName: "Lopez", phone: "+33699999999", avatarJpeg: Data([1])
    )

    func testASavedProfileReplacesWhatTheHeaderShowsAtOnce() async {
        let viewModel = ProfileTabViewModel(reader: Reader(before), currentUid: { "u1" })
        await waitUntil { viewModel.state == .ready(before) }

        viewModel.onSaved(uid: "u1", profile: after)

        XCTAssertEqual(viewModel.state, .ready(after))
    }

    func testASaveOfAnotherAccountIsIgnored() async {
        let viewModel = ProfileTabViewModel(reader: Reader(before), currentUid: { "u1" })
        await waitUntil { viewModel.state == .ready(before) }

        viewModel.onSaved(uid: "u2", profile: after)

        XCTAssertEqual(viewModel.state, .ready(before))
    }

    func testAReadThatStartedBeforeTheSaveDoesNotPutTheOldProfileBack() async {
        let reader = Reader(before)
        reader.delayNanoseconds = 100_000_000
        let viewModel = ProfileTabViewModel(reader: reader, currentUid: { "u1" })

        viewModel.onSaved(uid: "u1", profile: after)
        try? await Task.sleep(nanoseconds: 300_000_000)

        XCTAssertEqual(viewModel.state, .ready(after))
    }
}
