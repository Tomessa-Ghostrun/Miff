import XCTest
@testable import Miff

/// L'animation de lancement comme fonction pure du temps (mêmes cas qu'Android) : 2,9 s en tout, 3,0 s au plus, un seul
/// éclat sans clignotement, et une version sans mouvement quand « réduire les animations » est actif.
final class SplashTimelineTests: XCTestCase {
    private let full = SplashTimeline(reduceMotion: false)
    private let reduced = SplashTimeline(reduceMotion: true)

    private var burstStart: Int {
        SplashTimeline.holdMs + SplashTimeline.appearMs + SplashTimeline.inflateMs
    }

    func testTheFullAnimationLastsAboutTwoPointNineSecondsAndNeverMoreThanThree() {
        XCTAssertEqual(full.totalMs, 2_900)
        XCTAssertLessThanOrEqual(full.totalMs, 3_000)
        XCTAssertEqual(SplashTimeline.maxAllowedMs, 3_000)
        XCTAssertFalse(full.isFinished(elapsedMs: full.totalMs - 1))
        XCTAssertTrue(full.isFinished(elapsedMs: full.totalMs))
        XCTAssertTrue(full.isFinished(elapsedMs: 60_000))
    }

    func testTheSwellingAndThePopAreSlowAndThePopIsReadable() {
        XCTAssertGreaterThanOrEqual(SplashTimeline.inflateMs, 1_400)
        XCTAssertGreaterThanOrEqual(SplashTimeline.burstMs, 400)
        XCTAssertGreaterThanOrEqual(burstStart, 2_000)
        // Une pause juste avant l'éclat : le cœur reste à sa plus grande taille.
        XCTAssertTrue((80...250).contains(SplashTimeline.popPauseMs))
        let pauseStart = burstStart - SplashTimeline.popPauseMs
        let held = stride(from: pauseStart, to: burstStart, by: 10).map {
            (full.frame(atMs: $0).heartScale * 1_000).rounded()
        }
        XCTAssertEqual(Set(held), [(SplashTimeline.maxScale * 1_000).rounded()])
        // Les morceaux avancent assez lentement pour être suivis.
        XCTAssertLessThan(burstParticles(progress: 0.25)[0].distance, 0.65)
    }

    func testPhasesFollowTheRequestedStoryInOrder() {
        var phases: [SplashPhase] = []
        for time in stride(from: 0, to: full.totalMs, by: 10) {
            let phase = full.frame(atMs: time).phase
            if phases.last != phase {
                phases.append(phase)
            }
        }

        XCTAssertEqual(phases, [.hold, .appear, .inflate, .burst, .exit])
        XCTAssertEqual(full.frame(atMs: full.totalMs).phase, .done)
    }

    func testTheFirstInstantMatchesTheSystemLaunchScreenWithoutTheHeart() {
        let first = full.frame(atMs: 0)

        XCTAssertEqual(first.phase, .hold)
        XCTAssertEqual(first.heartAlpha, 0)
        XCTAssertEqual(first.overlayAlpha, 1)
        XCTAssertNil(first.burstProgress)
    }

    func testTheHeartAppearsThenSwellsLikeABalloonUpToItsMaximum() {
        let inflate = SplashTimeline.holdMs + SplashTimeline.appearMs

        XCTAssertEqual(full.frame(atMs: inflate).heartScale, 1, accuracy: 0.01)
        XCTAssertEqual(full.frame(atMs: inflate).heartAlpha, 1)
        let almostBurst = full.frame(atMs: burstStart - 1).heartScale
        XCTAssertGreaterThan(almostBurst, SplashTimeline.maxScale * 0.9)

        // Il grossit dans l'ensemble (l'oscillation peut rentrer un peu, jamais jusqu'au départ).
        let samples = stride(from: inflate, to: burstStart, by: 100).map { full.frame(atMs: $0).heartScale }
        XCTAssertGreaterThan(samples[samples.count - 1], samples[0] * 1.1)
        for (previous, next) in zip(samples, samples.dropFirst()) {
            XCTAssertGreaterThan(next, previous * 0.9)
        }
        // Élastique : la courbe n'est pas une droite.
        let middle = full.frame(atMs: inflate + SplashTimeline.inflateMs / 2).heartScale
        XCTAssertLessThan(middle, (1 + SplashTimeline.maxScale) / 2)
    }

    func testInflationIsContinuousWithTheAppearanceAndStartsAtRestingSize() {
        XCTAssertEqual(inflateScale(0), 1, accuracy: 1e-4)
        XCTAssertEqual(inflateScale(1), SplashTimeline.maxScale, accuracy: 1e-3)
    }

    func testThePopIsSharpWithTheHeartGoneOnTheFirstInstantAndPiecesFlyingAway() throws {
        let before = full.frame(atMs: burstStart - 1)
        let pop = full.frame(atMs: burstStart)

        XCTAssertGreaterThan(before.heartAlpha, 0)
        XCTAssertEqual(pop.heartAlpha, 0)
        XCTAssertEqual(try XCTUnwrap(pop.burstProgress), 0, accuracy: 0.01)

        let start = burstParticles(progress: 0)
        let end = burstParticles(progress: 1)
        XCTAssertEqual(start.count, SplashTimeline.particleCount)
        XCTAssertTrue(start.allSatisfy { $0.distance == 0 && $0.alpha == 1 })
        XCTAssertTrue(end.allSatisfy { $0.distance > 0.6 && $0.alpha == 0 })
        // Les morceaux partent dans des directions différentes, et seulement vers le haut.
        XCTAssertEqual(Set(start.map { $0.angleRadians }).count, SplashTimeline.particleCount)
        XCTAssertTrue(start.allSatisfy { $0.directionY < 0.5 })
        // Disparus à la fin de l'éclat, puis la sortie ne montre que le logo.
        let burstEnd = burstStart + SplashTimeline.burstMs
        XCTAssertNil(full.frame(atMs: burstEnd).burstProgress)
        XCTAssertEqual(full.frame(atMs: burstEnd).heartAlpha, 0)
    }

    func testTheExitFadesTheWholeSplashOutThenHandsOverToTheApp() {
        let exitStart = full.totalMs - SplashTimeline.exitMs

        XCTAssertEqual(full.frame(atMs: exitStart).overlayAlpha, 1, accuracy: 0.01)
        XCTAssertEqual(full.frame(atMs: exitStart + SplashTimeline.exitMs / 2).overlayAlpha, 0.5, accuracy: 0.01)
        XCTAssertEqual(full.frame(atMs: full.totalMs).overlayAlpha, 0)
        let alphas = (exitStart..<full.totalMs).map { full.frame(atMs: $0).overlayAlpha }
        for (previous, next) in zip(alphas, alphas.dropFirst()) {
            XCTAssertLessThanOrEqual(next, previous)
        }
    }

    func testNothingFlashesTheHeartAppearsOnceAndVanishesOnce() {
        // Accessibilité SP4 : au plus 3 flashs par seconde. Le cœur est montré, caché, et ne revient jamais.
        let visibility = (0..<full.totalMs).map { full.frame(atMs: $0).heartAlpha > 0 }
        let changes = zip(visibility, visibility.dropFirst()).filter { $0 != $1 }.count

        XCTAssertEqual(changes, 2)
    }

    // MARK: - « Réduire les animations »

    func testWithReducedMotionTheFullLogoStaysStillThenFades() {
        XCTAssertEqual(reduced.totalMs, 800)
        XCTAssertLessThan(reduced.totalMs, full.totalMs)
        for time in stride(from: 0, to: SplashTimeline.stillMs, by: 20) {
            let frame = reduced.frame(atMs: time)
            XCTAssertEqual(frame.phase, .still)
            XCTAssertEqual(frame.heartScale, 1)
            XCTAssertEqual(frame.heartAlpha, 1)
            XCTAssertEqual(frame.overlayAlpha, 1)
            XCTAssertNil(frame.burstProgress)
        }
        let fade = reduced.frame(atMs: SplashTimeline.stillMs + SplashTimeline.exitMs / 2)
        XCTAssertEqual(fade.phase, .exit)
        XCTAssertEqual(fade.heartScale, 1)
        XCTAssertEqual(fade.overlayAlpha, 0.5, accuracy: 0.01)
    }

    func testWithReducedMotionNothingMovesAtAnyInstant() {
        let scales = Set((0..<reduced.totalMs).map { reduced.frame(atMs: $0).heartScale })

        XCTAssertEqual(scales, [1])
        XCTAssertTrue((0..<reduced.totalMs).allSatisfy { reduced.frame(atMs: $0).burstProgress == nil })
        XCTAssertTrue(reduced.isFinished(elapsedMs: reduced.totalMs))
        XCTAssertEqual(reduced.frame(atMs: reduced.totalMs).phase, .done)
    }

    /// Recette du PM du 2026-10-30 : le cœur était bien trop gros. L'échelle 1 est la taille du cœur sur le logo et l'icône
    /// officiels ; il doit gonfler jusqu'à « un petit peu plus gros » : 115 à 125 %.
    func testTheHeartNeverGetsBiggerThanAboutOneAndAQuarterOfItsOfficialSize() throws {
        let scales = (0..<full.totalMs).map { full.frame(atMs: $0).heartScale }
        let biggest = try XCTUnwrap(scales.max())

        XCTAssertTrue((1.15...1.25).contains(biggest), "échelle maximale \(biggest)")
        XCTAssertTrue((1.15...1.25).contains(full.frame(atMs: burstStart - 1).heartScale))
        XCTAssertTrue((1.15...1.25).contains(SplashTimeline.maxScale))
    }

    func testNegativeTimeIsTreatedAsTheStart() {
        XCTAssertEqual(full.frame(atMs: -50), full.frame(atMs: 0))
    }

    func testTheGeometryOfTheArtworkMatchesTheSystemLaunchImage() {
        XCTAssertEqual(SplashArtwork.canvasPoints, 288)
        XCTAssertEqual(SplashArtwork.canvas, 108)
        XCTAssertGreaterThan(SplashArtwork.particleStartRadius, SplashArtwork.heartHalfWidth)
    }
}
