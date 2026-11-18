// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// La FORME du document d'une photo qui porte une DESCRIPTION (ticket description-update, critères P1 à P4, P10, R1 à R3, contrat de `validPhoto`) :
/// sept clés au plus, les six de la photo toujours là, `description` SEULEMENT quand le texte nettoyé n'est pas vide, et alors un texte de 1 à 140
/// unités UTF-16. La forme est comparée à la règle telle que le ticket l'écrit (`hasOnly` de sept clés, `hasAll` de six, `isStr(description, 1, 140)`)
/// pour qu'une dérive du client se voie ici. Miroir d'`UpdateDescriptionWritesTest.kt` d'Android.
final class UpdateDescriptionWritesTests: XCTestCase {
    private let now = UpdateFixtures.noon
    private let requiredKeys: Set<String> = ["jpeg", "createdAt", "expiresAt", "lat", "lng", "measuredAt"]
    private var allowed: Set<String> { requiredKeys.union(["description"]) }

    private func makePlace() throws -> PhotoPlace {
        try XCTUnwrap(PhotoPlace.from(UpdateFixtures.fix(), nowMillis: now))
    }

    private func write(_ description: String?, miff: String = testMiffId) throws -> DocWrite {
        try XCTUnwrap(
            UpdateWrites.photo(
                miffId: miff, uid: updateUid, photo: UpdateFixtures.photo(), place: try makePlace(), nowMillis: now,
                description: description
            )
        )
    }

    /// La règle du ticket, appliquée à ce que construit le client.
    private func acceptedByTheRule(_ data: [String: FieldValueSpec]) -> Bool {
        guard Set(data.keys).isSubset(of: allowed), requiredKeys.isSubset(of: Set(data.keys)) else {
            return false
        }
        if let value = data["description"] {
            guard case .string(let text) = value, (1...140).contains(text.utf16.count) else {
                return false
            }
        }
        return true
    }

    func testWithoutADescriptionTheDocumentIsExactlyTheOldOneNoKeyAtAll() throws {
        for description in [nil, "", "   ", "\n\t"] {
            let data = try write(description).data

            XCTAssertEqual(Set(data.keys), requiredKeys, "pour « \(description ?? "nil") »")
            XCTAssertNil(data["description"])
            XCTAssertTrue(acceptedByTheRule(data))
        }
    }

    func testTheDefaultCallOfTheOldCodeHasNoDescription() throws {
        let write = try XCTUnwrap(
            UpdateWrites.photo(
                miffId: testMiffId, uid: updateUid, photo: UpdateFixtures.photo(), place: try makePlace(), nowMillis: now
            )
        )

        XCTAssertEqual(Set(write.data.keys), requiredKeys)
    }

    func testWithADescriptionTheKeyIsThereAsACleanedTextNeverNullNeverEmpty() throws {
        let data = try write("  Bien   arrivé \n ").data

        XCTAssertEqual(Set(data.keys), allowed)
        XCTAssertEqual(data["description"], .string("Bien arrivé"))
        XCTAssertTrue(acceptedByTheRule(data))
        XCTAssertFalse(data.values.contains(.null))
        XCTAssertFalse(data.values.contains(.string("")))
    }

    func testTheLimitIs140UnitsAnythingLongerNeverReachesTheRules() throws {
        XCTAssertEqual(try write(String(repeating: "a", count: 140)).data["description"], .string(String(repeating: "a", count: 140)))
        let longer = try write(String(repeating: "a", count: 500)).data

        XCTAssertTrue(acceptedByTheRule(longer))
        if case .string(let text)? = longer["description"] {
            XCTAssertEqual(text.utf16.count, 140)
        } else {
            XCTFail("la description manque")
        }
    }

    func testSeventyEmojisFitAndSeventyOneAreCutToSeventyNeverBroken() throws {
        let smile = String(repeating: "\u{1F600}", count: 71)

        let data = try write(smile).data

        XCTAssertEqual(data["description"], .string(String(repeating: "\u{1F600}", count: 70)))
        XCTAssertTrue(acceptedByTheRule(data))
    }

    func testTheSixKeysOfThePhotoAreNeverTouchedByTheDescription() throws {
        let with = try write("Coucou").data
        let without = try write(nil).data

        for key in requiredKeys {
            XCTAssertNotNil(with[key], "\(key) est toujours là")
            XCTAssertEqual(with[key], without[key], key)
        }
    }

    func testTheLotWritesTheSameTextInEveryMiffAndTheStatusNeverCarriesIt() throws {
        let miffs = ["MiffNumber0000000001", "MiffNumber0000000002"]
        let context = PublishContext(nowMillis: now, batteryPct: 80, deviceId: testDeviceId, photoDescription: "Même texte")
        let lot = miffs.flatMap {
            UpdateWrites.publish(
                miffId: $0, uid: updateUid, photo: UpdateFixtures.photo(), place: UpdateFixtures.placeOrFail(now), context: context
            )
        }

        let photos = lot.filter { $0.path.contains("/photos/") }
        XCTAssertEqual(photos.count, 2)
        for photo in photos {
            XCTAssertEqual(photo.data["description"], .string("Même texte"))
        }
        // 2 écritures par Miff, pas une de plus : la description voyage dans le document de la photo.
        XCTAssertEqual(lot.count, 4)
        for status in lot.filter({ $0.path.contains("/status/") }) {
            XCTAssertFalse(status.data.keys.contains { $0.lowercased().contains("description") }, "le statut ne porte pas la description")
            XCTAssertEqual(status.data["updateText"], .null, "updateText reste réservé : null")
        }
    }

    func testANewPhotoWithoutADescriptionIsASetOfAWholeDocumentSoItNeverInheritsTheOldOne() throws {
        let first = try write("Ancien texte")
        let second = try write(nil)

        XCTAssertEqual(first.kind, .set)
        XCTAssertEqual(second.kind, .set, "jamais un update ni une fusion : le document est remplacé en entier")
        XCTAssertNil(second.data["description"])
    }

    func testTheWriteShowsNothingOfTheText() throws {
        let text = try write("secret words")

        XCTAssertFalse("\(text)".contains("secret"))
        let context = PublishContext(nowMillis: now, batteryPct: nil, deviceId: testDeviceId, photoDescription: "secret words")
        XCTAssertFalse("\(context)".contains("secret"))
    }
}

extension UpdateFixtures {
    /// Le lieu de démonstration d'une photo ; un lieu qui manquerait ferait échouer le test qui l'utilise.
    static func placeOrFail(_ now: Int) -> PhotoPlace {
        PhotoPlace(lat: 48.85661, lng: 2.35222, measuredAtMillis: now)
    }
}
// swiftlint:enable line_length
