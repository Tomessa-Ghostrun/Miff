import XCTest
@testable import Miff

/// Le logo officiel et les formes du carrousel sont dessinés à partir de DONNÉES VECTORIELLES : ces tests vérifient le
/// lecteur de chemins SVG et que les données générées sont complètes et à l'intérieur de leur cadre.
final class SVGPathTests: XCTestCase {
    private func point(_ valueX: Double, _ valueY: Double) -> PathPoint {
        PathPoint(px: valueX, py: valueY)
    }

    func testAbsoluteCommandsAndImplicitRepeats() {
        let commands = SVGPathParser.parse("M0 0L10 0 10 10 0 10Z")

        XCTAssertEqual(
            commands,
            [.move(point(0, 0)), .line(point(10, 0)), .line(point(10, 10)), .line(point(0, 10)), .close])
    }

    func testRelativeCommandsStartFromTheCurrentPoint() {
        let commands = SVGPathParser.parse("M10,10l5,0 0,5h-5v-5z")

        XCTAssertEqual(
            commands,
            [
                .move(point(10, 10)), .line(point(15, 10)), .line(point(15, 15)),
                .line(point(10, 15)), .line(point(10, 10)), .close
            ])
    }

    func testCubicCurvesAbsoluteAndRelative() {
        let absolute = SVGPathParser.parse("M0 0C1 2,3 4,5 6")
        let relative = SVGPathParser.parse("M10 10c1 2,3 4,5 6")

        XCTAssertEqual(absolute[1], .curve(control1: point(1, 2), control2: point(3, 4), end: point(5, 6)))
        XCTAssertEqual(relative[1], .curve(control1: point(11, 12), control2: point(13, 14), end: point(15, 16)))
    }

    func testSmoothCurvesReflectThePreviousControlPoint() {
        let commands = SVGPathParser.parse("M0 0C0 10,10 10,10 0S20 -10,20 0")

        // Le premier point de contrôle de S est le reflet (10, 10) autour de (10, 0), donc (10, -10).
        XCTAssertEqual(commands[2], .curve(control1: point(10, -10), control2: point(20, -10), end: point(20, 0)))
    }

    func testNumbersWithoutSeparatorsAndExponentsAreRead() {
        let commands = SVGPathParser.parse("M1.5-2.5L.5.25 1e1-1E1")

        XCTAssertEqual(commands, [.move(point(1.5, -2.5)), .line(point(0.5, 0.25)), .line(point(10, -10))])
    }

    func testAnUnsupportedCommandStopsTheReadingWithoutCrashing() {
        // Un arc (A) n'est pas géré : on garde ce qui a été lu avant.
        let commands = SVGPathParser.parse("M0 0L5 5A1 1 0 0 1 9 9L7 7")

        XCTAssertEqual(commands, [.move(point(0, 0)), .line(point(5, 5))])
    }

    func testGarbageGivesNothingInsteadOfCrashing() {
        XCTAssertEqual(SVGPathParser.parse(""), [])
        XCTAssertEqual(SVGPathParser.parse("   "), [])
        XCTAssertEqual(SVGPathParser.parse("M"), [])
        XCTAssertEqual(SVGPathParser.parse("L1"), [])
        XCTAssertEqual(SVGPathParser.parse("--..e"), [])
    }
}

final class LogoPathsTests: XCTestCase {
    /// Les coordonnées de tous les points d'un chemin.
    private func points(_ commands: [PathCommand]) -> [PathPoint] {
        commands.flatMap { command -> [PathPoint] in
            switch command {
            case .move(let target), .line(let target):
                return [target]
            case .curve(let control1, let control2, let end):
                return [control1, control2, end]
            case .close:
                return []
            }
        }
    }

    private func assertInside(
        _ commands: [PathCommand],
        width: Double,
        height: Double,
        name: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        XCTAssertGreaterThan(commands.count, 4, "\(name) : chemin vide", file: file, line: line)
        for point in points(commands) {
            // Une marge d'un peu moins d'une unité : le cœur du mot-symbole touche le haut du cadre (-0,6).
            XCTAssertGreaterThanOrEqual(point.px, -1, name, file: file, line: line)
            XCTAssertGreaterThanOrEqual(point.py, -1, name, file: file, line: line)
            XCTAssertLessThanOrEqual(point.px, width + 1, name, file: file, line: line)
            XCTAssertLessThanOrEqual(point.py, height + 1, name, file: file, line: line)
        }
    }

    func testTheWordmarkIsCompleteAndInsideItsFrame() {
        let width = LogoPaths.wordmarkWidth
        let height = LogoPaths.wordmarkHeight
        assertInside(LogoPaths.wordmarkLetters, width: width, height: height, name: "lettres")
        assertInside(LogoPaths.wordmarkHeart, width: width, height: height, name: "coeur")
        // M, i et deux f : au moins quatre sous-chemins de lettres.
        let starts = LogoPaths.wordmarkLetters.filter { command in
            if case .move = command {
                return true
            }
            return false
        }
        XCTAssertGreaterThanOrEqual(starts.count, 4)
    }

    func testTheMarkIsCompleteAndInsideTheIconCanvas() {
        assertInside(LogoPaths.markLetters, width: LogoPaths.markCanvas, height: LogoPaths.markCanvas, name: "M + i")
        assertInside(LogoPaths.markHeart, width: LogoPaths.markCanvas, height: LogoPaths.markCanvas, name: "coeur")
    }

    func testTheHeartOfTheMarkIsCenteredWhereTheSplashSwellsIt() {
        let heart = points(LogoPaths.markHeart)
        let minX = heart.map { $0.px }.min() ?? 0
        let maxX = heart.map { $0.px }.max() ?? 0
        let minY = heart.map { $0.py }.min() ?? 0
        let maxY = heart.map { $0.py }.max() ?? 0

        // Le ballon gonfle autour de ce point : il doit être au milieu du dessin du cœur.
        XCTAssertEqual((minX + maxX) / 2, SplashArtwork.heartCenterX, accuracy: 1.0)
        XCTAssertEqual((minY + maxY) / 2, SplashArtwork.heartCenterY, accuracy: 1.0)
        XCTAssertEqual((maxX - minX) / 2, SplashArtwork.heartHalfWidth, accuracy: 0.6)
    }
}

final class WelcomeBlobsTests: XCTestCase {
    func testEverySlideHasItsShapesInsideTheMockupArea() {
        for page in 0..<WelcomeBlobs.pageCount {
            let outlines = WelcomeBlobs.outlines(page: page)

            XCTAssertGreaterThanOrEqual(outlines.count, 3, "slide \(page)")
            for outline in outlines {
                XCTAssertGreaterThanOrEqual(outline.count, 8, "slide \(page) : contour trop court")
                for point in outline {
                    XCTAssertGreaterThanOrEqual(point.px, -1)
                    XCTAssertLessThanOrEqual(point.px, WelcomeBlobs.unitsWidth + 1)
                    XCTAssertGreaterThanOrEqual(point.py, 0)
                    XCTAssertLessThanOrEqual(point.py, WelcomeBlobs.unitsHeight + 1)
                }
            }
        }
    }

    func testAPageThatDoesNotExistHasNoShape() {
        XCTAssertTrue(WelcomeBlobs.outlines(page: -1).isEmpty)
        XCTAssertTrue(WelcomeBlobs.outlines(page: 3).isEmpty)
    }

    func testParsingKeepsTheCoordinatesAndSkipsBrokenPairs() {
        XCTAssertEqual(
            WelcomeBlobs.parse("1.5,2 -3,4.25 oops 7,x 8,9"),
            [PathPoint(px: 1.5, py: 2), PathPoint(px: -3, py: 4.25), PathPoint(px: 8, py: 9)])
    }
}
