// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// La mémoire des photos (FEED-8) : une photo déjà lue n'est PAS relue tant que le statut n'a pas changé, une photo expirée n'est jamais rendue,
// la mémoire est bornée, et la fin de session la vide. EN MÉMOIRE seulement (voir `FeedPhotoPrivacyTests`). Miroir de `PhotoCacheTest.kt`.
@MainActor
final class PhotoCacheTests: XCTestCase {
    private let cache = PhotoCache(maxEntries: 3)
    private let update = feedNow - feedMinutes(20)

    private func get(_ uid: String = "uidA", update: Int? = nil, now: Int = feedNow) -> SharedPhoto? {
        cache.photo(miffId: testMiffId, uid: uid, lastUpdateAtMillis: update ?? self.update, nowMillis: now)
    }

    func testAPhotoReadForAStatusIsGivenBackForTheSameStatusOnly() {
        let photo = PhotoFixtures.photo()
        cache.putRead(miffId: testMiffId, uid: "uidA", photo: photo, lastUpdateAtMillis: update)

        XCTAssertEqual(get(), photo)
        XCTAssertNil(get(update: update + 1), "un nouveau statut : la photo est relue")
    }

    func testAnExpiredCopyIsNeverGivenBackAndIsForgotten() {
        cache.putRead(miffId: testMiffId, uid: "uidA", photo: PhotoFixtures.photo(), lastUpdateAtMillis: update)

        XCTAssertNil(get(now: feedNow + feedHours(23)))
        XCTAssertEqual(cache.count, 0)
        XCTAssertNil(get(), "oubliée pour de bon")
    }

    func testMyOwnPhotoStaysUntilItExpiresWhateverTheStatus() {
        let photo = PhotoFixtures.photo(createdAgo: 0)
        cache.putOwn(miffId: testMiffId, uid: "me", photo: photo)

        XCTAssertEqual(get("me", update: 1), photo)
        XCTAssertEqual(get("me", update: feedNow), photo)
        XCTAssertNil(get("me", now: feedNow + feedHours(23)))
    }

    func testThePublishedPhotoIsPutInEveryMiffWhereItWent() {
        let photo = PhotoFixtures.photo(createdAgo: 0)

        cache.putOwn(PublishedPhoto(uid: "me", miffIds: [testMiffId, "OtherMiffNumber0001"], photo: photo))

        XCTAssertEqual(cache.photo(miffId: testMiffId, uid: "me", lastUpdateAtMillis: nil, nowMillis: feedNow), photo)
        XCTAssertEqual(cache.photo(miffId: "OtherMiffNumber0001", uid: "me", lastUpdateAtMillis: nil, nowMillis: feedNow), photo)
        XCTAssertEqual(cache.count, 2)
    }

    func testTheMemoryIsBoundedAndForgetsTheLeastRecentlyUsedFirst() {
        for uid in ["a", "b", "c"] {
            cache.putRead(miffId: testMiffId, uid: uid, photo: PhotoFixtures.photo(), lastUpdateAtMillis: update)
        }
        XCTAssertNotNil(get("a"), "a vient d'être utilisée : b devient la plus ancienne")

        cache.putRead(miffId: testMiffId, uid: "d", photo: PhotoFixtures.photo(), lastUpdateAtMillis: update)

        XCTAssertEqual(cache.count, 3)
        XCTAssertNil(get("b"))
        XCTAssertNotNil(get("a"))
        XCTAssertNotNil(get("c"))
        XCTAssertNotNil(get("d"))
    }

    func testAMissingPhotoIsNotAskedAgainForTheSameStatusButIsForANewOne() {
        cache.putMissing(miffId: testMiffId, uid: "uidA", lastUpdateAtMillis: update)

        XCTAssertTrue(cache.isMissing(miffId: testMiffId, uid: "uidA", lastUpdateAtMillis: update))
        XCTAssertFalse(cache.isMissing(miffId: testMiffId, uid: "uidA", lastUpdateAtMillis: update + 1))
        XCTAssertFalse(cache.isMissing(miffId: testMiffId, uid: "uidB", lastUpdateAtMillis: update))
    }

    func testARefusalIsRememberedForTheSameStatusToo() {
        cache.putRefused(miffId: testMiffId, uid: "uidA", lastUpdateAtMillis: update)

        XCTAssertTrue(cache.isRefused(miffId: testMiffId, uid: "uidA", lastUpdateAtMillis: update))
        XCTAssertFalse(cache.isRefused(miffId: testMiffId, uid: "uidA", lastUpdateAtMillis: update + 1))
    }

    func testAPhotoReadAfterAMissingOrARefusalReplacesThem() {
        cache.putMissing(miffId: testMiffId, uid: "uidA", lastUpdateAtMillis: update)
        cache.putRefused(miffId: testMiffId, uid: "uidA", lastUpdateAtMillis: update)

        cache.putRead(miffId: testMiffId, uid: "uidA", photo: PhotoFixtures.photo(), lastUpdateAtMillis: update)

        XCTAssertFalse(cache.isMissing(miffId: testMiffId, uid: "uidA", lastUpdateAtMillis: update))
        XCTAssertFalse(cache.isRefused(miffId: testMiffId, uid: "uidA", lastUpdateAtMillis: update))
    }

    func testClearEmptiesEverythingForTheNextPerson() {
        cache.putRead(miffId: testMiffId, uid: "uidA", photo: PhotoFixtures.photo(), lastUpdateAtMillis: update)
        cache.putOwn(miffId: testMiffId, uid: "me", photo: PhotoFixtures.photo())
        cache.putMissing(miffId: testMiffId, uid: "uidB", lastUpdateAtMillis: update)
        cache.putRefused(miffId: testMiffId, uid: "uidC", lastUpdateAtMillis: update)

        cache.clear()

        XCTAssertEqual(cache.count, 0)
        XCTAssertNil(get())
        XCTAssertNil(get("me"))
        XCTAssertFalse(cache.isMissing(miffId: testMiffId, uid: "uidB", lastUpdateAtMillis: update))
        XCTAssertFalse(cache.isRefused(miffId: testMiffId, uid: "uidC", lastUpdateAtMillis: update))
    }

    func testRemoveForgetsOneMember() {
        cache.putRead(miffId: testMiffId, uid: "uidA", photo: PhotoFixtures.photo(), lastUpdateAtMillis: update)
        cache.putRead(miffId: testMiffId, uid: "uidB", photo: PhotoFixtures.photo(), lastUpdateAtMillis: update)

        cache.remove(miffId: testMiffId, uid: "uidA")

        XCTAssertNil(get("uidA"))
        XCTAssertNotNil(get("uidB"))
    }
}
