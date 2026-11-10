import XCTest
@testable import Miff

/// Ma photo publiée est dite au Feed, EN MÉMOIRE, pour que ma carte la montre sans la relire (FEED-8) : seulement dans les Miffs où elle est vraiment
/// partie, jamais quand rien n'a été publié, avec l'heure du téléphone et la durée de vie de 23 h. Miroir de `afterSuccess` d'`UpdatePublisher.kt`.
@MainActor
final class UpdatePublishedPhotoTests: XCTestCase {
    private let rig = UpdateRig()
    private var announced: [PublishedPhoto] = []

    private func makePublisher() -> UpdatePublisher {
        var dependencies = UpdatePublisherDependencies(
            store: rig.store,
            device: rig.device,
            position: rig.position,
            network: rig.network,
            ledger: rig.ledger,
            environment: rig.environment
        )
        dependencies.isPositionPublished = { _ in true }
        dependencies.onPhotoPublished = { [unowned self] in self.announced.append($0) }
        return UpdatePublisher(dependencies)
    }

    func testAPublishedPhotoIsToldOnceWithItsMiffsItsPlaceAndItsLifetime() async throws {
        let photo = UpdateFixtures.photo()

        let result = await makePublisher().publish(photo)

        XCTAssertEqual(result, .published(sharedIn: 1))
        let told = try XCTUnwrap(announced.first)
        XCTAssertEqual(announced.count, 1)
        XCTAssertEqual(told.uid, updateUid)
        XCTAssertEqual(told.miffIds, [testMiffId])
        XCTAssertEqual(told.photo.jpeg, photo.jpeg)
        XCTAssertEqual(told.photo.createdAtMillis, rig.now)
        XCTAssertEqual(told.photo.expiresAtMillis, rig.now + 23 * FeedMillis.hour)
        XCTAssertEqual(told.photo.place?.measuredAtMillis, UpdateFixtures.fix().measuredAtMillis)
    }

    func testOnlyTheMiffsWhereThePhotoWentAreTold() async {
        let first = "MiffNumber0000000001"
        let second = "MiffNumber0000000002"
        rig.miffIds = [first, second]
        rig.store.notSharing = [second]

        _ = await makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(announced.first?.miffIds, [first], "pas dans la Miff où je ne partage plus")
    }

    func testNothingIsToldWhenNothingWasPublished() async {
        rig.network.online = false

        let result = await makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .offline)
        XCTAssertTrue(announced.isEmpty)
    }

    func testNothingIsToldWhenThereIsNoPosition() async {
        rig.position.result = .notFound

        let result = await makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .positionNotFound)
        XCTAssertTrue(announced.isEmpty)
    }

    func testThePhotoOfAMemoryReceiptDoesNotShowItsContentInADescription() {
        let told = PublishedPhoto(uid: updateUid, miffIds: [testMiffId], photo: PhotoFixtures.photo())

        XCTAssertEqual("\(told)", "PublishedPhoto(redacted)")
    }
}
