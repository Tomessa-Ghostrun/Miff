// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// La description d'une update (ticket description-update, critères S2, S3, S5 à S7, P1 à P3, C5, cas limites du §11) : le nettoyage commun avec
/// les prénoms, la limite de 140 unités UTF-16 qui ne coupe jamais un emoji, le texte vide qui veut dire « pas de description », le compteur qui
/// n'apparaît qu'à partir de 120. Miroir de `PhotoDescriptionTest.kt` d'Android. Les caractères spéciaux sont construits depuis leur code (jamais
/// tapés dans la source : un caractère invisible dans un fichier ne se relit pas).
final class PhotoDescriptionTests: XCTestCase {
    private func scalars(_ codes: UInt32...) -> String {
        var view = String.UnicodeScalarView()
        for code in codes {
            if let scalar = Unicode.Scalar(code) {
                view.append(scalar)
            }
        }
        return String(view)
    }

    private func repeated(_ text: String, _ count: Int) -> String {
        String(repeating: text, count: count)
    }

    private var joiner: String { scalars(0x200D) }
    /// Une famille : trois personnes et deux joints, 8 unités UTF-16.
    private var family: String { scalars(0x1F468) + joiner + scalars(0x1F469) + joiner + scalars(0x1F467) }
    /// Un emoji simple : 2 unités.
    private var smile: String { scalars(0x1F600) }
    /// Un drapeau : deux indicateurs régionaux, 4 unités.
    private var flagFr: String { scalars(0x1F1EB, 0x1F1F7) }

    // MARK: - Nettoyage

    func testAnOrdinaryTextIsKeptAsItIs() {
        XCTAssertEqual(
            PhotoDescription.normalize("Je suis bien arrivé, à tout à l'heure"), "Je suis bien arrivé, à tout à l'heure"
        )
    }

    func testNewLinesAndTabsBecomeASpaceNeverALineBreak() {
        XCTAssertEqual(PhotoDescription.normalize("a\nb\r\nc\td"), "a b c d")
        let typed = PhotoDescription.limitInput("a\nb\tc" + scalars(0x2028) + "d" + scalars(0x2029) + "e").text
        XCTAssertEqual(typed, "a b c d e", "U+2028 et U+2029 deviennent une espace (comme sur Android), ils ne sont pas retirés")
        XCTAssertFalse(typed.contains("\n"))
    }

    func testControlInvisibleAndDirectionCharactersAreRemovedLikeTheFirstNames() {
        let unsafe: [UInt32] = [
            0x0000, 0x0007, 0x007F, 0x009F, 0x200B, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
            0x2060, 0x2066, 0x2067, 0x2068, 0x2069, 0xFEFF
        ]
        for code in unsafe {
            let text = "a" + scalars(code) + "b"
            XCTAssertEqual(PhotoDescription.normalize(text), "ab", "U+\(String(code, radix: 16))")
            XCTAssertEqual(PhotoDescription.limitInput(text).text, "ab", "U+\(String(code, radix: 16))")
        }
    }

    func testTheZeroWidthJoinerIsKeptAFamilyStaysWhole() {
        XCTAssertEqual(PhotoDescription.normalize(family), family)
        XCTAssertEqual(PhotoDescription.limitInput("Nous " + family).text, "Nous " + family)
    }

    func testOnPublicationRepeatedSpacesAreReducedToOneAndTheEdgesAreTrimmed() {
        XCTAssertEqual(PhotoDescription.normalize("   a   b " + scalars(0xA0) + " c   "), "a b c")
        // Pendant la frappe les espaces sont laissées telles quelles (le curseur de la personne ne saute pas).
        XCTAssertEqual(PhotoDescription.limitInput("a  b ").text, "a  b ")
    }

    func testTheTextIsNeverInterpretedALinkOrMarkdownStaysAsWritten() {
        let text = "**bien** https://exemple.fr 06 12 34 56 78"

        XCTAssertEqual(PhotoDescription.normalize(text), text)
    }

    // MARK: - Vide = pas de description

    func testAnEmptyTextOrOnlySpacesOrOnlyInvisibleCharactersIsNoDescription() {
        let empties = [
            "", " ", "     ", "\t\n\r", scalars(0xA0, 0x2003), scalars(0x200B, 0x200E, 0xFEFF), " \n " + scalars(0x202E) + " "
        ]
        for text in empties {
            XCTAssertNil(PhotoDescription.normalize(text), "longueur \(text.utf16.count)")
        }
    }

    // MARK: - La limite de 140 unités UTF-16

    func testExactly140UnitsIsAcceptedAnd141IsCut() {
        let at140 = repeated("a", 140)

        XCTAssertEqual(PhotoDescription.normalize(at140), at140)
        XCTAssertFalse(PhotoDescription.limitInput(at140).wasCut)
        let cut = PhotoDescription.limitInput(repeated("a", 141))
        XCTAssertEqual(cut.text, at140)
        XCTAssertTrue(cut.wasCut)
    }

    func testTheLimitCountsUnitsNotCharacters70SimpleEmojisFit71DoNot() {
        let seventy = repeated(smile, 70)

        XCTAssertEqual(seventy.utf16.count, 140)
        XCTAssertEqual(PhotoDescription.limitInput(seventy).text, seventy)
        let cut = PhotoDescription.limitInput(repeated(smile, 71))
        XCTAssertEqual(cut.text, seventy)
        XCTAssertTrue(cut.wasCut)
    }

    func testAPasteThatEndsInTheMiddleOfAnEmojiDropsTheWholeEmojiNeverHalfOfIt() {
        let cut = PhotoDescription.limitInput(repeated("a", 139) + smile)

        XCTAssertEqual(cut.text, repeated("a", 139))
        XCTAssertTrue(cut.wasCut)
    }

    func testAComposedEmojiThatDoesNotFitIsDroppedWholeNotLeftAsAnIncompleteFamily() {
        // 135 unités + une famille de 8 = 143 : toute la famille part (jamais un homme seul, ni un joint qui pend).
        XCTAssertEqual(PhotoDescription.limitInput(repeated("a", 135) + family).text, repeated("a", 135))
        // Deux familles : la première tient (132 + 8 = 140), la seconde part entière.
        let tight = PhotoDescription.limitInput(repeated("a", 132) + family + family)
        XCTAssertEqual(tight.text, repeated("a", 132) + family)
        XCTAssertFalse(tight.text.hasSuffix(joiner))
        // Une coupe qui tomberait juste après un joint d'une famille qui ne tient pas.
        XCTAssertEqual(PhotoDescription.limitInput(repeated("a", 134) + family).text, repeated("a", 134))
    }

    func testAFlagIsNeverCutInTwo() {
        XCTAssertEqual(PhotoDescription.limitInput(repeated("a", 138) + flagFr).text, repeated("a", 138))
        XCTAssertEqual(PhotoDescription.limitInput(repeated("a", 136) + flagFr).text, repeated("a", 136) + flagFr)
    }

    func testASkinToneAKeycapAndAnAccentStayWithTheirBaseSymbol() {
        let thumbs = scalars(0x1F44D, 0x1F3FD) // pouce + teinte de peau : 4 unités
        XCTAssertEqual(PhotoDescription.limitInput(repeated("a", 138) + thumbs).text, repeated("a", 138))
        XCTAssertEqual(PhotoDescription.limitInput(repeated("a", 136) + thumbs).text, repeated("a", 136) + thumbs)
        // Un « e » suivi d'un accent combinant : l'accent ne reste pas seul au début de la coupe.
        let accent = PhotoDescription.limitInput(repeated("a", 139) + "e" + scalars(0x0301))
        XCTAssertEqual(accent.text, repeated("a", 139))
        let keycap = PhotoDescription.limitInput(repeated("a", 138) + "1" + scalars(0xFE0F, 0x20E3))
        XCTAssertEqual(keycap.text, repeated("a", 138))
    }

    func testTheTextPublishedIsNeverLongerThan140EvenWhenTheInputWas() throws {
        let published = try XCTUnwrap(PhotoDescription.normalize(repeated("mot ", 100)))

        XCTAssertTrue((1...140).contains(published.utf16.count))
        XCTAssertFalse(published.hasSuffix(" "))
    }

    // MARK: - Le compteur

    func testTheCounterOnlyShowsFrom120AndNeverBefore() {
        XCTAssertFalse(PhotoDescription.showCounter(length: 0))
        XCTAssertFalse(PhotoDescription.showCounter(length: 119))
        XCTAssertTrue(PhotoDescription.showCounter(length: 120))
        XCTAssertTrue(PhotoDescription.showCounter(length: 140))
    }

    func testTheLimitIsReachedAt140() {
        XCTAssertFalse(PhotoDescription.isAtLimit(length: 139))
        XCTAssertTrue(PhotoDescription.isAtLimit(length: 140))
    }

    // MARK: - Ce qui vient du réseau (quelqu'un d'autre l'a écrit)

    func testAValueFromTheServerIsShownOnlyWhenItIsATextOf1To140UnitsAndCleaned() {
        XCTAssertEqual(PhotoDescription.fromServer("Bien rentré"), "Bien rentré")
        XCTAssertEqual(PhotoDescription.fromServer("a\n" + scalars(0x202E) + "b"), "a b")
        XCTAssertEqual(PhotoDescription.fromServer(repeated("a", 140)), repeated("a", 140))
    }

    func testAValueFromTheServerThatIsNotATextOrEmptyOrTooLongIsIgnored() {
        let ignored: [Any?] = [
            nil, "", "  \n ", 12, true, ["a"], Data([1, 2, 3]), NSNull(), repeated("a", 141)
        ]
        for value in ignored {
            XCTAssertNil(PhotoDescription.fromServer(value), "\(String(describing: value).prefix(20))")
        }
    }

    // MARK: - La touche « Retour »

    func testTheReturnKeyIsRecognisedWhateverThePlaceOfTheCursor() {
        XCTAssertTrue(PhotoDescription.isReturnKey(old: "abc", new: "abc\n"))
        XCTAssertTrue(PhotoDescription.isReturnKey(old: "abc", new: "a\nbc"))
        XCTAssertTrue(PhotoDescription.isReturnKey(old: "", new: "\n"))
    }

    func testAnythingElseIsNotTheReturnKey() {
        XCTAssertFalse(PhotoDescription.isReturnKey(old: "abc", new: "abcd"))
        XCTAssertFalse(PhotoDescription.isReturnKey(old: "abc", new: "abc"))
        XCTAssertFalse(PhotoDescription.isReturnKey(old: "abc", new: "ab"))
        XCTAssertFalse(PhotoDescription.isReturnKey(old: "abc", new: "abc\n\n"), "deux sauts de ligne : un collage")
        XCTAssertFalse(PhotoDescription.isReturnKey(old: "", new: "a\nb"), "un collage de plusieurs lignes")
        XCTAssertFalse(PhotoDescription.isReturnKey(old: "abc", new: "abx\n"))
    }
}
// swiftlint:enable line_length
