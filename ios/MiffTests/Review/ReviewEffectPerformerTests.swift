#if REVIEW_PROMPT
import XCTest
@testable import Miff

/// Un ouvreur d'adresses de test : garde ce qu'on lui a demandé d'ouvrir et dit « oui » ou « non » pour chaque adresse.
@MainActor
private final class RecordingOpener: ReviewLinkOpener {
    var opened: [URL] = []
    var accepts: (URL) -> Bool

    init(accepts: @escaping (URL) -> Bool = { _ in true }) {
        self.accepts = accepts
    }

    func open(_ url: URL) async -> Bool {
        opened.append(url)
        return accepts(url)
    }
}

/// Ce que la fenêtre demande à l'appareil : la demande du store, le formulaire dans le navigateur (HTTPS, adresse telle quelle), le courrier
/// d'aide, et le repli calme quand rien ne peut s'ouvrir. Miroir des tests de `StoreReview.kt` d'Android.
@MainActor
final class ReviewEffectPerformerTests: XCTestCase {
    private let form = ReviewFeedbackForm.configuredAddress
    private let mail = URL(string: "mailto:aide@exemple.fr")

    private func performer(
        _ opener: ReviewLinkOpener,
        mail: URL?? = nil,
        onStoreReview: @escaping () -> Void = {}
    ) -> ReviewEffectPerformer {
        let address = mail ?? self.mail
        return ReviewEffectPerformer(
            opener: opener, supportMailURL: { address }, requestStoreReview: onStoreReview
        )
    }

    func testTheStoreRequestIsTheNativeWindowOnlyNothingIsOpenedByAddress() async {
        let opener = RecordingOpener()
        var asked = 0

        let done = await performer(opener, onStoreReview: { asked += 1 }).perform(.requestStoreReview)

        XCTAssertTrue(done)
        XCTAssertEqual(asked, 1)
        XCTAssertTrue(opener.opened.isEmpty)
    }

    func testTheFormIsOpenedInTheBrowserAsItIsWithNothingAddedAndNoStoreRequest() async throws {
        let opener = RecordingOpener()
        var asked = 0

        let done = await performer(opener, onStoreReview: { asked += 1 }).perform(.openFeedbackForm(address: form))

        XCTAssertTrue(done)
        XCTAssertEqual(opener.opened.map(\.absoluteString), [form])
        XCTAssertEqual(asked, 0)
        let components = try XCTUnwrap(URLComponents(url: opener.opened[0], resolvingAgainstBaseURL: false))
        XCTAssertEqual(components.scheme, "https")
        XCTAssertNil(components.query)
        XCTAssertNil(components.fragment)
    }

    func testAnAddressThatIsNotHttpsIsNeverOpenedWhateverTheConfigurationSays() async {
        let opener = RecordingOpener()

        _ = await performer(opener).perform(.openFeedbackForm(address: "http://forms.example.org/miff"))

        // Rien d'en clair n'est ouvert : le repli est le courrier d'aide, et lui seul.
        XCTAssertEqual(opener.opened.map(\.absoluteString), ["mailto:aide@exemple.fr"])
    }

    func testIfNothingCanOpenTheFormTheMailToTheSupportIsTried() async {
        let opener = RecordingOpener(accepts: { $0.scheme == "mailto" })

        let done = await performer(opener).perform(.openFeedbackForm(address: form))

        XCTAssertTrue(done)
        XCTAssertEqual(opener.opened.map(\.absoluteString), [form, "mailto:aide@exemple.fr"])
    }

    func testIfNothingOpensAtAllTheAnswerIsNoSoTheScreenSaysItCalmly() async {
        let opener = RecordingOpener(accepts: { _ in false })

        let formOpened = await performer(opener).perform(.openFeedbackForm(address: form))
        let mailOpened = await performer(opener).perform(.openMail)

        XCTAssertFalse(formOpened)
        XCTAssertFalse(mailOpened)
    }

    func testWriteToUsOpensTheMailToTheSupportAndNothingElse() async {
        let opener = RecordingOpener()

        let done = await performer(opener).perform(.openMail)

        XCTAssertTrue(done)
        XCTAssertEqual(opener.opened.map(\.absoluteString), ["mailto:aide@exemple.fr"])
    }

    func testWithoutASupportAddressNothingIsOpenedAndTheAnswerIsNo() async {
        let opener = RecordingOpener()

        let done = await performer(opener, mail: .some(nil)).perform(.openMail)

        XCTAssertFalse(done)
        XCTAssertTrue(opener.opened.isEmpty)
    }

    func testTheSupportMailIsTheRealOneAndHasNoSubjectNoBody() throws {
        let url = try XCTUnwrap(ContactLinks.supportMailURL())

        XCTAssertEqual(url.scheme, "mailto")
        XCTAssertTrue(url.absoluteString.contains(AppLinks.supportEmail))
        XCTAssertNil(url.query)
    }
}
#endif
