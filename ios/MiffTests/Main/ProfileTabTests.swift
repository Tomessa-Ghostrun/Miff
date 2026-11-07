import XCTest
@testable import Miff

/// Le profil lu pour l'onglet Profil et « Mes informations » : ce qu'on accepte du serveur, ce qu'on cache, et l'en-tête qui ne montre
/// jamais le profil d'une autre personne.
final class ProfileSummaryTests: XCTestCase {
    private func fields(
        first: Any? = "Alex",
        last: Any? = "Martin",
        phone: Any? = "+33612345678",
        avatar: Any? = nil
    ) -> [String: Any] {
        var result: [String: Any] = [:]
        result["firstName"] = first
        result["lastName"] = last
        result["phone"] = phone
        result["avatar"] = avatar
        return result
    }

    func testAValidDocumentIsRead() {
        let profile = ProfileSummary.from(fields: fields(avatar: Data([1, 2, 3])))

        XCTAssertEqual(profile?.firstName, "Alex")
        XCTAssertEqual(profile?.lastName, "Martin")
        XCTAssertEqual(profile?.phone, "+33612345678")
        XCTAssertEqual(profile?.avatarJpeg, Data([1, 2, 3]))
        XCTAssertEqual(profile?.displayName, "Alex Martin")
        XCTAssertEqual(profile?.initials, "AM")
    }

    func testAMissingOrEmptyNameMakesTheProfileUnusable() {
        XCTAssertNil(ProfileSummary.from(fields: fields(first: nil)))
        XCTAssertNil(ProfileSummary.from(fields: fields(last: "   ")))
        XCTAssertNil(ProfileSummary.from(fields: fields(first: 12)))
    }

    func testANameOfMoreThanFiftyUnitsIsRefused() {
        XCTAssertNil(ProfileSummary.from(fields: fields(first: String(repeating: "a", count: 51))))
        XCTAssertNotNil(ProfileSummary.from(fields: fields(first: String(repeating: "a", count: 50))))
    }

    func testAPhoneThatIsNotE164IsLeftOutButTheProfileStays() {
        XCTAssertNil(ProfileSummary.from(fields: fields(phone: "0612345678"))?.phone)
        XCTAssertNil(ProfileSummary.from(fields: fields(phone: "+0123456789"))?.phone)
        XCTAssertNil(ProfileSummary.from(fields: fields(phone: nil))?.phone)
    }

    func testAnOversizedOrEmptyPhotoIsLeftOut() {
        let tooBig = Data(count: ProfileDocument.maxAvatarBytes + 1)
        let limit = Data(count: ProfileDocument.maxAvatarBytes)

        XCTAssertNil(ProfileSummary.from(fields: fields(avatar: tooBig))?.avatarJpeg)
        XCTAssertNil(ProfileSummary.from(fields: fields(avatar: Data()))?.avatarJpeg)
        XCTAssertEqual(ProfileSummary.from(fields: fields(avatar: limit))?.avatarJpeg, limit)
    }

    func testNamesAreTrimmed() {
        let profile = ProfileSummary.from(fields: fields(first: "  Alex ", last: " Martin  "))

        XCTAssertEqual(profile?.displayName, "Alex Martin")
    }

    func testTheDescriptionShowsNothingPersonal() {
        let profile = ProfileSummary(firstName: "Alex", lastName: "Martin", phone: "+33612345678", avatarJpeg: nil)

        XCTAssertEqual(String(describing: profile), "ProfileSummary(redacted)")
        XCTAssertEqual("\(profile)", "ProfileSummary(redacted)")
        XCTAssertFalse("\(ProfileTabState.ready(profile))".contains("Alex"))
    }

    func testTheCountryIsDeducedFromTheDialCode() {
        let tools = PhoneNumberKitTools()
        let display = PhoneDisplay.of(phone: "+33612345678", tools: tools, locale: Locale(identifier: "fr"))

        XCTAssertEqual(display?.country.region, "FR")
        XCTAssertEqual(display?.country.callingCode, 33)
    }

    func testNoPhoneMeansNoCountry() {
        XCTAssertNil(PhoneDisplay.of(phone: nil, tools: PhoneNumberKitTools(), locale: .current))
    }
}

@MainActor
final class ProfileTabViewModelTests: XCTestCase {
    private final class FakeReader: ProfileReading {
        var result: ProfileSummary?
        private(set) var reads = 0

        init(_ result: ProfileSummary?) {
            self.result = result
        }

        func read(uid: String) async -> ProfileSummary? {
            reads += 1
            return result
        }
    }

    private let alex = ProfileSummary(firstName: "Alex", lastName: "Martin", phone: nil, avatarJpeg: nil)

    func testItStartsLoadingThenShowsTheProfile() async {
        let viewModel = ProfileTabViewModel(reader: FakeReader(alex), currentUid: { "u1" })

        XCTAssertEqual(viewModel.state, .loading)
        await waitUntil { viewModel.state == .ready(alex) }

        XCTAssertEqual(viewModel.state, .ready(alex))
    }

    func testAnUnreadableProfileIsCalmAndCanBeRetried() async {
        let reader = FakeReader(nil)
        let viewModel = ProfileTabViewModel(reader: reader, currentUid: { "u1" })
        await waitUntil { viewModel.state == .unavailable }
        XCTAssertEqual(viewModel.state, .unavailable)

        reader.result = alex
        viewModel.retry()
        await waitUntil { viewModel.state == .ready(alex) }

        XCTAssertEqual(viewModel.state, .ready(alex))
        XCTAssertEqual(reader.reads, 2)
    }

    func testWithoutASignedInAccountNothingIsRead() {
        let reader = FakeReader(alex)
        let viewModel = ProfileTabViewModel(reader: reader, currentUid: { nil })

        XCTAssertEqual(viewModel.state, .unavailable)
        XCTAssertEqual(reader.reads, 0)
    }

    func testAnAnswerThatArrivesAfterTheAccountChangedIsIgnored() async {
        var uid: String? = "u1"
        let reader = FakeReader(alex)
        let viewModel = ProfileTabViewModel(reader: reader, currentUid: { uid })
        uid = "u2"
        await waitUntil { reader.reads > 0 }
        try? await Task.sleep(nanoseconds: 50_000_000)

        XCTAssertEqual(viewModel.state, .loading, "Le profil de la personne d'avant ne doit jamais s'afficher")
    }

    func testAFailedRereadKeepsWhatIsAlreadyShown() async {
        let reader = FakeReader(alex)
        let viewModel = ProfileTabViewModel(reader: reader, currentUid: { "u1" })
        await waitUntil { viewModel.state == .ready(alex) }

        reader.result = nil
        viewModel.retry()
        await waitUntil { reader.reads == 2 }
        try? await Task.sleep(nanoseconds: 50_000_000)

        XCTAssertEqual(viewModel.state, .ready(alex))
    }
}
