import XCTest
@testable import Miff

/// La hauteur commune des cartes du carrousel du Profil : une mesure faite avant que les cartes aient leur vraie largeur (très haute : le
/// texte passe à la ligne mot à mot) ne doit jamais être retenue (espace vide énorme entre la carte et les pastilles, retour du PM,
/// TestFlight 43), sans réintroduire le clignotement que la hauteur « qui ne rétrécit pas » évite.
final class ProfileCarouselHeightTests: XCTestCase {
    private let realWidth: CGFloat = 335
    private let realHeight: CGFloat = 107
    /// Une hauteur de carte mesurée à une largeur quasi nulle : le texte passe à la ligne presque lettre à lettre.
    private let transientHeight: CGFloat = 330

    // MARK: - Une carte sans largeur ne publie rien

    func testACardMeasuredBeforeItHasItsWidthPublishesNothing() {
        XCTAssertEqual(CarouselCardHeight.measure(height: transientHeight, width: 0), .none)
        XCTAssertEqual(CarouselCardHeight.measure(height: transientHeight, width: 40), .none)
        XCTAssertEqual(
            CarouselCardHeight.measure(height: transientHeight, width: CarouselCardHeight.minMeasurableWidth - 1), .none
        )
    }

    func testACardWithItsRealWidthPublishesItsHeightAndItsWidth() {
        let measure = CarouselCardHeight.measure(height: realHeight, width: realWidth)

        XCTAssertEqual(measure.height, realHeight)
        XCTAssertEqual(measure.width, realWidth)
        XCTAssertEqual(
            CarouselCardHeight.measure(height: realHeight, width: CarouselCardHeight.minMeasurableWidth).height,
            realHeight
        )
    }

    func testAMeasureWithNoSenseIsIgnored() {
        XCTAssertEqual(CarouselCardHeight.measure(height: 0, width: realWidth), .none)
        XCTAssertEqual(CarouselCardHeight.measure(height: -5, width: realWidth), .none)
        XCTAssertEqual(CarouselCardHeight.measure(height: .nan, width: realWidth), .none)
        XCTAssertEqual(CarouselCardHeight.measure(height: realHeight, width: .nan), .none)
        XCTAssertEqual(CarouselCardHeight.measure(height: .infinity, width: realWidth), .none)
    }

    func testTheTallestCardWinsAndAnEmptyMeasureNeverDoes() {
        let tall = CarouselCardHeight.measure(height: 140, width: realWidth)
        let short = CarouselCardHeight.measure(height: realHeight, width: realWidth)

        XCTAssertEqual(CarouselCardHeight.combine(short, tall), tall)
        XCTAssertEqual(CarouselCardHeight.combine(tall, short), tall)
        XCTAssertEqual(CarouselCardHeight.combine(.none, short), short)
        XCTAssertEqual(CarouselCardHeight.combine(short, .none), short)
        XCTAssertEqual(CarouselCardHeight.combine(.none, .none), .none)
    }

    // MARK: - La hauteur retenue

    func testNoMeasureLeavesTheHeightAsItIs() {
        XCTAssertEqual(CarouselCardHeight.next(current: 0, measured: 0, sameTextSize: false, sameWidth: false), 0)
        XCTAssertEqual(CarouselCardHeight.next(current: 107, measured: 0, sameTextSize: false, sameWidth: false), 107)
        XCTAssertEqual(CarouselCardHeight.next(current: 107, measured: -3, sameTextSize: true, sameWidth: true), 107)
    }

    func testTheHeightStillNeverShrinksWhileTextSizeAndWidthStayTheSame() {
        XCTAssertEqual(CarouselCardHeight.next(current: 120, measured: 100, sameTextSize: true, sameWidth: true), 120)
        XCTAssertEqual(CarouselCardHeight.next(current: 100, measured: 120, sameTextSize: true, sameWidth: true), 120)
        // Sans préciser la largeur : comme avant.
        XCTAssertEqual(CarouselCardHeight.next(current: 120, measured: 100, sameTextSize: true), 120)
    }

    func testTheHeightFollowsANewWidthEvenDownwards() {
        XCTAssertEqual(
            CarouselCardHeight.next(
                current: transientHeight, measured: realHeight, sameTextSize: true, sameWidth: false
            ),
            realHeight
        )
    }

    func testTheHeightFollowsANewTextSizeEvenDownwards() {
        XCTAssertEqual(CarouselCardHeight.next(current: 200, measured: 120, sameTextSize: false, sameWidth: true), 120)
        XCTAssertEqual(CarouselCardHeight.next(current: 0, measured: 120, sameTextSize: false, sameWidth: false), 120)
    }

    // MARK: - Le cas du PM : une première mise en page transitoire

    /// Rejoue ce que fait le carrousel : chaque mesure publiée passe par `next`, avec le contexte (taille de texte, largeur) de la précédente.
    private struct Replay {
        var height: CGFloat = 0
        var width: CGFloat?
        var textSize: Int?

        mutating func publish(_ measure: CarouselCardHeight.Measure, textSize newSize: Int = 1) {
            guard measure.height > 0 else {
                return
            }
            height = CarouselCardHeight.next(
                current: height,
                measured: measure.height,
                sameTextSize: textSize == newSize,
                sameWidth: width == measure.width
            )
            textSize = newSize
            width = measure.width
        }
    }

    func testATransientMeasureAtZeroWidthIsNeverKept() {
        var replay = Replay()

        replay.publish(CarouselCardHeight.measure(height: transientHeight, width: 0))
        XCTAssertEqual(replay.height, 0, "rien n'est retenu : le carrousel garde sa hauteur de départ")
        replay.publish(CarouselCardHeight.measure(height: realHeight, width: realWidth))

        XCTAssertEqual(replay.height, realHeight, "l'espacement d'avant : pastilles juste sous la carte")
    }

    func testATransientMeasureAtAnOddWidthIsReplacedByTheRealOne() {
        var replay = Replay()

        // Largeur plausible (150 pt) mais pas la vraie : la hauteur retenue est celle d'une carte étroite.
        replay.publish(CarouselCardHeight.measure(height: transientHeight, width: 150))
        XCTAssertEqual(replay.height, transientHeight)
        replay.publish(CarouselCardHeight.measure(height: realHeight, width: realWidth))

        XCTAssertEqual(replay.height, realHeight, "la vraie largeur arrive : la hauteur redescend")
    }

    func testAContentChangeAtTheSameWidthStillDoesNotMakeTheHeightJump() {
        var replay = Replay()

        replay.publish(CarouselCardHeight.measure(height: 140, width: realWidth))
        // La phrase d'aide devient « 4 membres » : la carte est un peu plus basse, la hauteur commune ne bouge pas (pas de clignotement).
        replay.publish(CarouselCardHeight.measure(height: realHeight, width: realWidth))
        XCTAssertEqual(replay.height, 140)
        // Et inversement : plus haute, elle grandit.
        replay.publish(CarouselCardHeight.measure(height: 150, width: realWidth))
        XCTAssertEqual(replay.height, 150)
    }

    func testARotationOrANewTextSizeRestartsTheHeight() {
        var replay = Replay()
        replay.publish(CarouselCardHeight.measure(height: 200, width: 335))

        replay.publish(CarouselCardHeight.measure(height: realHeight, width: 760))
        XCTAssertEqual(replay.height, realHeight, "nouvelle largeur (iPad, rotation)")

        replay.publish(CarouselCardHeight.measure(height: 90, width: 760), textSize: 2)
        XCTAssertEqual(replay.height, 90, "nouvelle taille de texte")
    }
}
