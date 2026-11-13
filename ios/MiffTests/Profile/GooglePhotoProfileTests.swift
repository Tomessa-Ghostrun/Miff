import XCTest
@testable import Miff

private struct SquarePhoto: DecodedPhoto {
    let width = 400
    let height = 400
}

private final class SpyPhotoSource: ProviderPhotoSource {
    var data: Data? = Data(count: 9_000)
    var delayNanoseconds: UInt64 = 0
    private(set) var fetchCount = 0

    func fetch(_ url: URL) async -> Data? {
        fetchCount += 1
        if delayNanoseconds > 0 {
            try? await Task.sleep(nanoseconds: delayNanoseconds)
        }
        return data
    }
}

private final class SpyPhotoCodec: AvatarCodec {
    let avatar = Data(repeating: 7, count: 12_000)
    var decodes = true

    func decode(_ source: Data) async -> (any DecodedPhoto)? {
        decodes ? SquarePhoto() : nil
    }

    func encode(_ photo: any DecodedPhoto, crop: SquareCrop) async -> Data? {
        avatar
    }
}

private final class SilentProfiles: UserProfileRepository {
    func create(uid: String, profile: NewProfile, consent: PendingConsent) async -> ProfileWriteResult { .done }
    func addPhone(uid: String, phone: String) async -> ProfileWriteResult { .done }
    func delete(uid: String) async -> ProfileWriteResult { .done }
    func read(uid: String) async -> ProfileSeed? { nil }
}

private struct OnlyFrance: PhoneNumberTools {
    func countries(locale: Locale) -> [PhoneCountry] { [] }
    func country(region: String, locale: Locale) -> PhoneCountry {
        PhoneCountry(region: region, callingCode: 33, name: region)
    }
    func isSupportedRegion(_ region: String?) -> Bool { region?.uppercased() == "FR" }
    func exampleHint(region: String) -> String? { nil }
    func normalize(region: String, input: String) -> PhoneResult { .invalid }
    func detectInternational(_ input: String) -> DetectedNumber? { nil }
}

/// L'inscription avec Google reprend la photo du compte Google comme photo de profil PRÉREMPLIE à l'étape profil : la personne peut la
/// changer ou la retirer, rien d'autre n'est écrit, et un échec ne se voit pas.
@MainActor
final class GooglePhotoProfileTests: XCTestCase {
    private struct Setup {
        let viewModel: ProfileViewModel
        let harness: SessionHarness
        let source: SpyPhotoSource
        let codec: SpyPhotoCodec
    }

    private func make(
        mode: OnboardingMode = .full,
        photo: URL? = URL(string: "https://lh3.googleusercontent.com/a/ACg8ocJabc=s96-c"),
        draft: ProfileDraft? = nil,
        source: SpyPhotoSource = SpyPhotoSource(),
        codec: SpyPhotoCodec = SpyPhotoCodec()
    ) async -> Setup {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1", method: .google)
        harness.gateway.photoURL = photo
        harness.pending.save(PendingConsent(uid: "uid-1", termsVersion: "1.0", analyticsAccepted: true))
        harness.drafts.stored = draft
        await harness.manager.startAndResolve()
        let dependencies = ProfileDependencies(
            session: harness.manager,
            pending: harness.pending,
            drafts: harness.drafts,
            profiles: SilentProfiles(),
            avatars: codec,
            network: FakeNetwork(online: true),
            phoneTools: OnlyFrance(),
            deviceRegion: { "FR" },
            googlePhoto: GooglePhotoPrefill(source: source, codec: codec, timeout: 5)
        )
        let viewModel = ProfileViewModel(mode: mode, dependencies: dependencies)
        return Setup(viewModel: viewModel, harness: harness, source: source, codec: codec)
    }

    private func existingDraft(avatar: Data?) -> ProfileDraft {
        ProfileDraft(
            uid: "uid-1",
            step: .profile,
            firstName: "Camille",
            lastName: "Dupont",
            regionCode: nil,
            phoneText: "",
            avatar: avatar
        )
    }

    func testTheGooglePhotoBecomesThePrefilledProfilePhotoWithAGentleHint() async {
        let setup = await make()
        await setup.harness.waitUntil { setup.viewModel.state.avatar != nil }
        XCTAssertEqual(setup.viewModel.state.avatar, setup.codec.avatar)
        XCTAssertTrue(setup.viewModel.state.googlePhotoHint)
        XCTAssertEqual(setup.source.fetchCount, 1)
        XCTAssertNil(setup.viewModel.state.message)
        // Rien n'est écrit sur le disque tant que la personne n'a rien saisi.
        XCTAssertNil(setup.harness.drafts.stored)
    }

    func testThePhotoIsKeptInTheDraftWithTheProfileLikeAnyOtherPhoto() async {
        let setup = await make()
        await setup.harness.waitUntil { setup.viewModel.state.avatar != nil }
        setup.viewModel.onFirstNameChanged("Camille")
        XCTAssertEqual(setup.harness.drafts.stored?.avatar, setup.codec.avatar)
    }

    func testThePersonCanRemoveThePhotoAndTheHintGoes() async {
        let setup = await make()
        await setup.harness.waitUntil { setup.viewModel.state.avatar != nil }
        setup.viewModel.onRemovePhoto()
        XCTAssertNil(setup.viewModel.state.avatar)
        XCTAssertFalse(setup.viewModel.state.googlePhotoHint)
        XCTAssertNil(setup.harness.drafts.stored?.avatar)
    }

    func testAPhotoPickedByThePersonReplacesThePrefilledOneAndTheHintGoes() async {
        let setup = await make()
        await setup.harness.waitUntil { setup.viewModel.state.avatar != nil }
        setup.viewModel.onPhotoChosen(Data(count: 10))
        XCTAssertFalse(setup.viewModel.state.googlePhotoHint)
        await setup.harness.waitUntil { setup.viewModel.state.crop != nil }
        XCTAssertNotNil(setup.viewModel.state.crop)
    }

    func testAPhotoThatArrivesAfterThePersonRemovedTheirsIsNotApplied() async {
        let source = SpyPhotoSource()
        source.delayNanoseconds = 300_000_000
        let setup = await make(source: source)
        setup.viewModel.onRemovePhoto()
        try? await Task.sleep(nanoseconds: 600_000_000)
        XCTAssertNil(setup.viewModel.state.avatar)
        XCTAssertFalse(setup.viewModel.state.googlePhotoHint)
    }

    func testAnAccountWithoutAGooglePhotoChangesNothing() async {
        let setup = await make(photo: nil)
        try? await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertNil(setup.viewModel.state.avatar)
        XCTAssertFalse(setup.viewModel.state.googlePhotoHint)
        XCTAssertEqual(setup.source.fetchCount, 0)
    }

    func testTheGenericGoogleSilhouetteIsNotUsed() async {
        let generic = URL(string: "https://lh3.googleusercontent.com/a/default-user=s96-c")
        let setup = await make(photo: generic)
        try? await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertNil(setup.viewModel.state.avatar)
        XCTAssertEqual(setup.source.fetchCount, 0)
    }

    func testAFailedDownloadContinuesWithoutPhotoAndWithoutAnyMessage() async {
        let source = SpyPhotoSource()
        source.data = nil
        let setup = await make(source: source)
        await setup.harness.waitUntil { source.fetchCount > 0 }
        try? await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertNil(setup.viewModel.state.avatar)
        XCTAssertFalse(setup.viewModel.state.googlePhotoHint)
        XCTAssertNil(setup.viewModel.state.message)
    }

    func testAnImageThatCannotBeReadContinuesWithoutPhoto() async {
        let codec = SpyPhotoCodec()
        codec.decodes = false
        let setup = await make(codec: codec)
        await setup.harness.waitUntil { setup.source.fetchCount > 0 }
        try? await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertNil(setup.viewModel.state.avatar)
        XCTAssertNil(setup.viewModel.state.message)
    }

    func testAnExistingDraftIsRespectedSoNothingIsDownloaded() async {
        let withoutPhoto = await make(draft: existingDraft(avatar: nil))
        try? await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertNil(withoutPhoto.viewModel.state.avatar)
        XCTAssertEqual(withoutPhoto.source.fetchCount, 0)
        let withPhoto = await make(draft: existingDraft(avatar: Data(count: 50)))
        XCTAssertEqual(withPhoto.viewModel.state.avatar, Data(count: 50))
        XCTAssertEqual(withPhoto.source.fetchCount, 0)
        XCTAssertFalse(withPhoto.viewModel.state.googlePhotoHint)
    }

    func testThePhoneOnlyStepOfAnExistingProfileNeverTouchesThePhoto() async {
        let setup = await make(mode: .phoneOnly)
        try? await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertNil(setup.viewModel.state.avatar)
        XCTAssertEqual(setup.source.fetchCount, 0)
    }

    func testThePhotoIsNotAppliedOnceThePersonHasMovedOnToThePhoneStep() async {
        let source = SpyPhotoSource()
        source.delayNanoseconds = 300_000_000
        let setup = await make(source: source)
        setup.viewModel.onFirstNameChanged("Camille")
        setup.viewModel.onLastNameChanged("Dupont")
        setup.viewModel.onContinueFromProfile()
        try? await Task.sleep(nanoseconds: 600_000_000)
        XCTAssertNil(setup.viewModel.state.avatar)
        XCTAssertFalse(setup.viewModel.state.googlePhotoHint)
    }
}
