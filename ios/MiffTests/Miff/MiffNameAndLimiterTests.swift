import XCTest
@testable import Miff

/// Le nom d'une Miff (ticket Miff §5.2, critères C2 et C3) et la pause après 5 mauvais codes (critère J5).
final class MiffNameAndLimiterTests: XCTestCase {
    func testNameNeedsTwoCharactersAfterTrimming() {
        XCTAssertFalse(MiffName.isValid(""))
        XCTAssertFalse(MiffName.isValid("a"))
        XCTAssertFalse(MiffName.isValid("  a  "))
        XCTAssertFalse(MiffName.isValid("   "))
        XCTAssertTrue(MiffName.isValid("ab"))
        XCTAssertTrue(MiffName.isValid("  ab  "))
    }

    func testNameIsStoredWithoutTheSpacesAtBothEndsAndKeepsTheMiddleOnes() {
        XCTAssertEqual(MiffName.stored("  Les  Dupont "), "Les  Dupont")
    }

    func testNameStopsAt40AndSaysSo() {
        let forty = String(repeating: "a", count: 40)

        XCTAssertEqual(MiffName.limit(forty), NameInput(text: forty, limitReached: true))
        XCTAssertEqual(MiffName.limit(forty + "b"), NameInput(text: forty, limitReached: true))
        XCTAssertEqual(
            MiffName.limit(String(repeating: "a", count: 39)),
            NameInput(text: String(repeating: "a", count: 39), limitReached: false)
        )
        XCTAssertTrue(MiffName.isValid(forty))
    }

    func testNameIsNeverMoreThan40UnitsEmojisIncludedAndNeverCutInTheMiddleOfAnEmoji() {
        let house = "🏠" // un émoji : 2 unités UTF-16
        let typed = String(repeating: "a", count: 39) + house

        let limited = MiffName.limit(typed)

        // 39 + 2 = 41 unités : l'émoji ne tient pas, et il n'est pas coupé en deux.
        XCTAssertEqual(limited.text, String(repeating: "a", count: 39))
        XCTAssertTrue(limited.limitReached)
        let twenty = String(repeating: house, count: 20) // exactement 40 unités
        XCTAssertEqual(MiffName.limit(twenty).text, twenty)
        XCTAssertLessThanOrEqual(MiffName.stored(twenty + house).utf16.count, 40)
    }

    func testNameDropsControlCharacters() {
        XCTAssertEqual(MiffName.limit("a\u{0000}\nb").text, "ab")
    }

    func testNameIsCutBetweenWholeCharactersALetterIsNeverSeparatedFromItsAccent() {
        let accented = "e\u{0301}" // « é » écrit en lettre et accent combinant : 2 unités, 1 caractère
        let typed = String(repeating: "a", count: 39) + accented

        let limited = MiffName.limit(typed)

        // La lettre entière ne tient pas : elle est écartée, pas coupée.
        XCTAssertEqual(limited.text, String(repeating: "a", count: 39))
        XCTAssertTrue(limited.limitReached)
        // Elle tient quand il y a de la place pour le caractère entier.
        let fits = String(repeating: "a", count: 38) + accented
        XCTAssertEqual(MiffName.limit(fits).text, fits)
    }

    func testASingleCharacterLongerThanTheLimitFallsBackOnWholeCodePoints() {
        // Un seul « caractère » (une lettre et 60 accents combinants) bien plus long que 40 unités.
        let huge = "e" + String(repeating: "\u{0301}", count: 60)

        let limited = MiffName.limit(huge)

        XCTAssertLessThanOrEqual(limited.text.utf16.count, 40)
        XCTAssertFalse(limited.text.isEmpty)
        XCTAssertTrue(limited.limitReached)
    }

    func testNameRemovesBidirectionalControlsAndInvisibleCharactersButKeepsTheJoinersOfEmojiSequences() {
        let rtlOverride = "\u{202E}"
        let isolate = "\u{2067}"
        let zeroWidthSpace = "\u{200B}"
        let joiner = "\u{200D}"

        XCTAssertEqual(MiffName.limit("Fam\(rtlOverride)ille\(isolate)\(zeroWidthSpace)").text, "Famille")
        XCTAssertEqual(MiffName.clean("a\u{FEFF}\u{2060}\u{00AD}b"), "ab")
        XCTAssertEqual(MiffName.clean("a\(joiner)b"), "a\(joiner)b")
        XCTAssertEqual(
            MiffName.stored("Chez nous\(rtlOverride)"),
            "Chez nous",
            "un nom qui cache sa fin ne peut pas être enregistré"
        )
    }

    func testServerNameIsCleanedOfBidirectionalAndInvisibleCharacters() {
        XCTAssertEqual(MiffName.fromServer("\u{202E}Chez\u{200B} nous\u{2066}\u{0000} "), "Chez nous")
        XCTAssertEqual(MiffName.fromServer("  Chez nous  "), "Chez nous")
    }

    func testServerNameWithNothingVisibleLeftIsMissing() {
        XCTAssertNil(MiffName.fromServer("\u{202E}\u{200B}\u{FEFF} \u{2066}"))
        XCTAssertNil(MiffName.fromServer(""))
    }

    func testServerPlaceIsCleanedAndDroppedWhenNothingVisibleIsLeft() {
        let place = MiffPlace.fromServer(id: "p1", rawName: "\u{202E}Maison\u{200B}")

        XCTAssertEqual(place?.name, "Maison")
        XCTAssertEqual(place?.id, "p1")
        XCTAssertNil(MiffPlace.fromServer(id: "p2", rawName: "\u{200B}\u{202E} "))
    }

    func testCleanIsAppliedToWhatComesFromTheServerToo() {
        XCTAssertEqual(MiffName.clean("Les\u{202E} copains\u{0007}"), "Les copains")
    }

    func testNameFeedbackNeverShowsTheName() {
        XCTAssertFalse(String(describing: MiffName.limit("Famille Secrète")).contains("Secrète"))
    }

    func testInitialsOfAPerson() {
        XCTAssertEqual(Initials.of(first: "Léa", last: "Martin"), "LM")
        XCTAssertEqual(Initials.of(first: " ", last: "dupont"), "D")
        XCTAssertEqual(Initials.of(first: "", last: ""), "?")
    }

    func testAMiffIdHasTheShapeOfAFirestoreIdentifier() {
        XCTAssertTrue(MiffId.isValid(testMiffId))
        XCTAssertFalse(MiffId.isValid("short"))
        XCTAssertFalse(MiffId.isValid(testMiffId + "x"))
        XCTAssertFalse(MiffId.isValid("AbCdEfGhIjKlMnOpQr/t"))
        XCTAssertFalse(MiffId.isValid("AbCdEfGhIjKlMnOpQr..t"))
    }

    // MARK: - Pause après 5 essais

    func testLimiterPausesAfterFiveFailuresForOneMinute() {
        var now = 0
        let limiter = JoinAttemptLimiter(nowMillis: { now })
        for _ in 0..<4 {
            limiter.recordFailure()
        }
        XCTAssertFalse(limiter.isPaused())

        limiter.recordFailure()

        XCTAssertTrue(limiter.isPaused())
        XCTAssertEqual(limiter.remainingPauseMillis(), 60_000)
        now = 59_999
        XCTAssertTrue(limiter.isPaused())
        now = 60_000
        XCTAssertFalse(limiter.isPaused())
    }

    func testLimiterASuccessStartsAgainFromZero() {
        let limiter = JoinAttemptLimiter(nowMillis: { 0 })
        for _ in 0..<4 {
            limiter.recordFailure()
        }
        limiter.recordSuccess()

        limiter.recordFailure()

        XCTAssertFalse(limiter.isPaused(), "1 échec après un succès n'est pas le 5e")
    }

    func testLimiterFailuresOlderThan10MinutesAreForgotten() {
        var now = 0
        let limiter = JoinAttemptLimiter(nowMillis: { now })
        for _ in 0..<4 {
            limiter.recordFailure()
        }
        now = 10 * 60_000

        limiter.recordFailure()

        XCTAssertFalse(limiter.isPaused(), "les 4 premiers échecs sont oubliés au bout de 10 minutes")
    }

    func testLimiterAfterThePauseANewSeriesOfFiveIsNeededForAnotherPause() {
        var now = 0
        let limiter = JoinAttemptLimiter(nowMillis: { now })
        for _ in 0..<5 {
            limiter.recordFailure()
        }
        now = 61_000
        XCTAssertFalse(limiter.isPaused())

        limiter.recordFailure()

        XCTAssertFalse(limiter.isPaused())
    }
}
