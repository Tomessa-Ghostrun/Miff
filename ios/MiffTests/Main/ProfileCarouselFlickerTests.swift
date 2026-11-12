import Combine
import UIKit
import XCTest
@testable import Miff

private final class FlickerReader: MemberPreviewReader {
    var answers: [String: MemberPreview] = [:]
    private(set) var reads: [String] = []

    func read(miffId: String) async -> MemberPreview? {
        reads.append(miffId)
        return answers[miffId]
    }
}

/// Le carrousel de Miffs ne doit rien faire clignoter quand on balaie d'une Miff à l'autre (retour du PM, TestFlight 32) : des identités
/// stables, aucun passage par un état vide pour ce qui était déjà montré, aucune animation implicite, une hauteur qui ne saute pas, des
/// photos dessinées dès la première image quand elles sont en mémoire.
@MainActor
final class ProfileCarouselFlickerTests: XCTestCase {
    private func member(_ uid: String) -> MiffMember {
        MiffMember(
            uid: uid, firstName: "Alex", lastName: "Martin", avatarJpeg: nil, role: .member, sharingEnabled: true
        )
    }

    private func preview(_ count: Int) -> MemberPreview {
        MemberPreview(members: (0..<count).map { member("u\($0 + 1)") })
    }

    private func source(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Main
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent(relativePath)
        return try String(contentsOf: url, encoding: .utf8)
    }

    // MARK: - Hauteur des cartes

    func testTheCardHeightNeverShrinksWhileTheTextSizeStaysTheSame() {
        XCTAssertEqual(CarouselCardHeight.next(current: 120, measured: 100, sameTextSize: true), 120)
        XCTAssertEqual(CarouselCardHeight.next(current: 100, measured: 120, sameTextSize: true), 120)
    }

    func testTheCardHeightFollowsAChangeOfTextSize() {
        XCTAssertEqual(CarouselCardHeight.next(current: 200, measured: 120, sameTextSize: false), 120)
        XCTAssertEqual(CarouselCardHeight.next(current: 0, measured: 120, sameTextSize: false), 120)
    }

    // MARK: - Aucun passage par un état vide

    func testACardNeverGoesBackToLoadingOnceItsMembersAreKnown() async {
        let reader = FlickerReader()
        reader.answers["B"] = preview(3)
        var clock = 1_000_000
        let store = MiffMemberPreviews(reader: reader, clock: { clock })

        store.beginOpening()
        await store.loadIfNeeded(miffId: "B", isActive: false)
        XCTAssertEqual(store.state(for: "B"), .known(preview(3)))

        // Une relecture (5 minutes plus tard, ou après l'ouverture de l'écran de la Miff) garde l'ancienne valeur pendant et après.
        clock += MiffMemberPreviews.freshMillis
        store.invalidate(miffId: "B")
        store.beginOpening()
        XCTAssertEqual(store.state(for: "B"), .known(preview(3)), "jamais « en lecture » pour une carte déjà connue")

        reader.answers["B"] = nil
        await store.loadIfNeeded(miffId: "B", isActive: false)
        XCTAssertEqual(store.state(for: "B"), .known(preview(3)), "une relecture qui échoue garde ce qui était montré")
    }

    func testARereadingThatBringsTheSameThingPublishesNothing() async {
        let reader = FlickerReader()
        reader.answers["B"] = preview(3)
        var clock = 1_000_000
        let store = MiffMemberPreviews(reader: reader, clock: { clock })
        store.beginOpening()
        await store.loadIfNeeded(miffId: "B", isActive: false)
        var changes = 0
        let watching = store.objectWillChange.sink { _ in changes += 1 }
        defer {
            watching.cancel()
        }

        clock += MiffMemberPreviews.freshMillis
        store.beginOpening()
        await store.loadIfNeeded(miffId: "B", isActive: false)
        store.retain(only: ["A", "B"])

        XCTAssertEqual(reader.reads, ["B", "B"])
        XCTAssertEqual(changes, 0, "rien à redessiner : la même valeur n'est pas republiée")
    }

    // MARK: - Identités stables, aucune animation implicite (analyse des sources)

    func testTheCardsOfTheCarouselKeepTheirIdentityFromOnePageToTheNext() throws {
        let carousel = try source("Miff/Views/Main/ProfileMiffCarousel.swift")
        let cards = try source("Miff/Views/Main/ProfileCarouselCards.swift")
        let parts = try source("Miff/Views/Main/ProfileCarouselParts.swift")

        // Une carte = sa position (0..<count) : ni identifiant dérivé de la page montrée, ni `.id(` qui recréerait une vue.
        XCTAssertTrue(cards.contains(#"ForEach(0..<count, id: \.self)"#))
        for text in [carousel, cards] {
            XCTAssertFalse(text.contains(".id("), "aucune identité forcée")
            XCTAssertFalse(text.contains("UUID()"), "aucune identité aléatoire")
        }
        // Les pastilles de membres sont identifiées par le membre, pas par leur position.
        XCTAssertTrue(parts.contains(#"id: \.uid"#))
        XCTAssertFalse(parts.contains("UUID()"))
    }

    func testNothingInTheCarouselAnimatesImplicitly() throws {
        let paths = [
            "Miff/Views/Main/ProfileMiffCarousel.swift",
            "Miff/Views/Main/ProfileCarouselCards.swift",
            "Miff/Views/Main/ProfileCarouselParts.swift"
        ]
        for path in paths {
            let text = try source(path)
            XCTAssertFalse(text.contains(".animation("), "\(path) : pas d'animation implicite")
            XCTAssertFalse(text.contains(".transition("), "\(path) : pas de transition d'apparition")
            XCTAssertFalse(text.contains("matchedGeometryEffect"), "\(path) : pas de géométrie partagée")
            XCTAssertFalse(text.contains("drawingGroup"), "\(path) : pas de rendu hors écran forcé")
        }
    }

    // MARK: - Aucun redessin des cartes pendant un balayage

    private func row(members: [MiffCardMembers], current: Int = 0) -> CarouselCardsRow {
        CarouselCardsRow(
            miffIds: ["A"], names: ["Famille"], members: members, count: 2, current: current,
            width: 300, gap: 28, cardHeight: 120, maxVisible: 5, onOpenMiff: { _ in }, onCreate: {}
        )
    }

    func testTheRowOfCardsIsEqualToItselfWhateverTheFingerDoes() {
        // Pendant un balayage seul le décalage change : il n'est PAS une donnée de la rangée, qui reste égale image après image.
        let first = row(members: [.known(preview(4))])
        for _ in 0..<60 {
            XCTAssertEqual(first, row(members: [.known(preview(4))]))
        }
    }

    func testTheRowOfCardsChangesWhenWhatItShowsChanges() {
        let base = row(members: [.loading])

        XCTAssertNotEqual(base, row(members: [.known(preview(4))]), "les membres arrivent")
        XCTAssertNotEqual(base, row(members: [.loading], current: 1), "la page change (VoiceOver)")
    }

    func testPrefetchingTheNeighbourDuringASwipeDoesNotChangeTheCardsOrPublish() async {
        let reader = FlickerReader()
        reader.answers["B"] = preview(3)
        let store = MiffMemberPreviews(reader: reader, clock: { 1_000_000 })
        store.beginOpening()
        await store.loadIfNeeded(miffId: "B", isActive: false)
        let before = row(members: [store.state(for: "B")])
        var changes = 0
        let watching = store.objectWillChange.sink { _ in changes += 1 }
        defer {
            watching.cancel()
        }

        // Le doigt balaie : le voisin est demandé de nouveau à chaque image, sans rien relire ni republier.
        for _ in 0..<20 {
            await store.loadIfNeeded(miffId: "B", isActive: false)
        }

        XCTAssertEqual(changes, 0, "aucun changement d'état observé pendant le balayage")
        XCTAssertEqual(before, row(members: [store.state(for: "B")]))
        XCTAssertEqual(reader.reads, ["B"])
    }

    func testTheDragOffsetAndThePageChangeInOneTransactionAndNoGestureStateOffset() throws {
        let carousel = try source("Miff/Views/Main/ProfileMiffCarousel.swift")

        XCTAssertTrue(carousel.contains(".equatable()"), "la rangée n'est pas réévaluée à chaque image")
        XCTAssertFalse(carousel.contains("@GestureState private var track"), "plus de décalage remis à zéro hors transaction")
        // À la fin du geste, la page ET le décalage changent dans le même bloc animé.
        XCTAssertTrue(carousel.contains("page = target\n                    dragX = 0"))
    }

    func testAnAvatarDrawsItsPhotoOnTheFirstFrameWhenItIsInMemory() throws {
        let components = try source("Miff/Views/Miff/MiffComponents.swift")

        XCTAssertTrue(components.contains("AvatarDecoding.cachedImage("))
    }

    // MARK: - Photos en mémoire

    func testACachedPhotoIsFoundWithoutDecodingAndAnUnknownOneIsNot() throws {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 64, height: 64))
        let jpeg = renderer.jpegData(withCompressionQuality: 0.8) { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 64, height: 64))
        }

        XCTAssertNil(AvatarDecoding.cachedImage(from: jpeg, pixelSize: 33))
        XCTAssertNotNil(AvatarDecoding.image(from: jpeg, pixelSize: 33))
        XCTAssertNotNil(AvatarDecoding.cachedImage(from: jpeg, pixelSize: 33))
        XCTAssertNil(AvatarDecoding.cachedImage(from: jpeg, pixelSize: 77), "une autre taille n'est pas la même image")
        XCTAssertNil(AvatarDecoding.cachedImage(from: nil, pixelSize: 33))
        AvatarDecoding.removeAll()
    }
}
