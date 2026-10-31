import SwiftUI

/// Le dessin du splash à un instant donné : le « M + i » blanc, le cœur qui gonfle puis éclate, et les petits morceaux.
/// Décoratif : le lecteur d'écran n'entend que « Miff ». Aucun son, aucune vibration, aucun événement d'analyse.
struct SplashArtworkView: View {
    let frame: SplashFrame

    private var points: CGFloat {
        CGFloat(SplashArtwork.canvasPoints)
    }

    var body: some View {
        ZStack {
            VectorShape(commands: LogoPaths.markLetters, width: LogoPaths.markCanvas, height: LogoPaths.markCanvas)
                .fill(colorTextOnAction.color)
            if frame.heartAlpha > 0 && frame.heartScale > 0 {
                VectorShape(commands: LogoPaths.markHeart, width: LogoPaths.markCanvas, height: LogoPaths.markCanvas)
                    .fill(colorBrandPrimary.color)
                    .scaleEffect(
                        CGFloat(frame.heartScale),
                        anchor: UnitPoint(
                            x: CGFloat(SplashArtwork.heartCenterX / SplashArtwork.canvas),
                            y: CGFloat(SplashArtwork.heartCenterY / SplashArtwork.canvas)
                        )
                    )
                    .opacity(frame.heartAlpha)
            }
            if let progress = frame.burstProgress {
                BurstView(progress: progress)
            }
        }
        .frame(width: points, height: points)
    }
}

/// Les morceaux projetés par l'éclat, dessinés dans le canevas de 108 unités.
private struct BurstView: View {
    let progress: Double

    var body: some View {
        Canvas { context, size in
            let unit = size.width / CGFloat(SplashArtwork.canvas)
            let centerX = CGFloat(SplashArtwork.heartCenterX) * unit
            let centerY = CGFloat(SplashArtwork.heartCenterY) * unit
            for piece in burstParticles(progress: progress) where piece.alpha > 0 && piece.size > 0 {
                let radius = SplashArtwork.particleStartRadius + SplashArtwork.particleTravel * piece.distance
                let pieceRadius = CGFloat(SplashArtwork.particleRadius * piece.size) * unit
                let center = CGPoint(
                    x: centerX + CGFloat(piece.directionX * radius) * unit,
                    y: centerY + CGFloat(piece.directionY * radius) * unit
                )
                let disc = Path(
                    ellipseIn: CGRect(
                        x: center.x - pieceRadius,
                        y: center.y - pieceRadius,
                        width: pieceRadius * 2,
                        height: pieceRadius * 2
                    )
                )
                context.fill(disc, with: .color(colorBrandPrimary.withAlpha(piece.alpha).color))
            }
        }
    }
}

/// Le splash : fond bleu uni et dessin centré, qui s'efface à la sortie pour révéler l'app dessous.
struct SplashFrameView: View {
    let frame: SplashFrame

    var body: some View {
        ZStack {
            colorActionPrimary.color
                .ignoresSafeArea()
            SplashArtworkView(frame: frame)
        }
        .opacity(frame.overlayAlpha)
    }
}

/// Joue l'animation de lancement AU-DESSUS de l'app, une fois par démarrage à froid (ticket Onboarding §5.1 et §15, SP1 à
/// SP6). Ordre du lancement : écran de lancement du système (bleu, « M + i » sans le cœur) -> cette animation -> l'app.
///
/// L'app (`content`) est montée SOUS l'animation dès la deuxième image : la session est en cours de résolution et le
/// premier écran est prêt quand l'animation s'efface ; le splash n'attend jamais l'app ni le réseau et ne dure jamais plus
/// de `SplashTimeline.totalMs` (3 s au plus). Il mesure le temps RÉEL : un appareil lent ne peut pas l'étirer.
///
/// Pendant l'animation l'app est cachée du lecteur d'écran (il entend « Miff » une seule fois) et ne reçoit aucun toucher.
/// « Réduire les animations » : logo complet immobile, puis fondu (0,8 s, aucun mouvement).
@MainActor
struct SplashHost<Content: View>: View {
    private let reduceMotion: Bool
    private let isEnabled: Bool
    private let content: Content

    @State private var finished: Bool
    @State private var mounted: Bool
    @State private var start = Date()

    /// `isEnabled` ne vaut faux que pour les captures d'écran de la CI (build Debug) : l'app s'affiche tout de suite.
    init(reduceMotion: Bool, isEnabled: Bool = true, @ViewBuilder content: () -> Content) {
        self.reduceMotion = reduceMotion
        self.isEnabled = isEnabled
        self.content = content()
        _finished = State(initialValue: !isEnabled)
        _mounted = State(initialValue: !isEnabled)
    }

    private var timeline: SplashTimeline {
        SplashTimeline(reduceMotion: reduceMotion)
    }

    var body: some View {
        ZStack {
            if mounted || finished {
                content
                    .accessibilityHidden(!finished)
                    .allowsHitTesting(finished)
            }
            if !finished {
                splash
            }
        }
        .task {
            if isEnabled {
                await play()
            }
        }
    }

    private var splash: some View {
        TimelineView(.animation) { context in
            SplashFrameView(frame: timeline.frame(atMs: Int(context.date.timeIntervalSince(start) * 1000)))
        }
        .ignoresSafeArea()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("ios_splash_a11y")
    }

    /// La première image n'affiche que le bleu et le « M + i », exactement comme l'écran de lancement du système ; l'app est
    /// montée une image plus tard pour que sa première composition (plus lourde) ne retarde pas la toute première image.
    private func play() async {
        await Task.yield()
        mounted = true
        let timeline = self.timeline
        while !timeline.isFinished(elapsedMs: Int(Date().timeIntervalSince(start) * 1000)) {
            try? await Task.sleep(nanoseconds: 30_000_000)
        }
        finished = true
    }
}
