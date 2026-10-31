import Foundation

/// Où en est l'animation de lancement. Version complète : `hold` (l'écran de lancement du système continue, « M + i »
/// seul), `appear` (le cœur apparaît sur le « i »), `inflate` (il gonfle comme un ballon), `burst` (il éclate, de petits
/// morceaux verts s'envolent), `exit` (fondu vers l'app), `done`. Version « réduire les animations » : `still` (logo
/// complet, rien ne bouge), `exit`, `done`.
enum SplashPhase: Equatable {
    case hold
    case appear
    case inflate
    case burst
    case still
    case exit
    case done
}

/// Ce qu'il faut dessiner à un instant donné. Tout est un simple nombre : l'animation entière se teste sans écran.
struct SplashFrame: Equatable {
    let phase: SplashPhase
    /// Taille du cœur par rapport à sa taille de repos sur le « i » (1 = comme sur l'icône de l'app).
    let heartScale: Double
    /// 0 = caché, 1 = pleinement visible.
    let heartAlpha: Double
    /// nil en dehors de l'éclatement, sinon 0 (à l'instant de l'éclat) à 1 (morceaux disparus).
    let burstProgress: Double?
    /// Opacité de tout le splash : 1 jusqu'à la sortie, puis décroît jusqu'à 0 pour révéler l'app dessous.
    let overlayAlpha: Double
}

/// Un petit morceau projeté par l'éclat. Distances et tailles sont des fractions, mises à l'échelle par le dessin.
struct BurstParticle: Equatable {
    let angleRadians: Double
    /// 0 = au bord du cœur éclaté, 1 = point le plus éloigné atteint.
    let distance: Double
    /// 1 = pleine taille, décroît jusqu'à 0.
    let size: Double
    let alpha: Double

    var directionX: Double {
        cos(angleRadians)
    }

    var directionY: Double {
        sin(angleRadians)
    }
}

/// L'animation de lancement comme fonction pure du temps (millisecondes depuis le premier dessin).
///
/// Durées (ticket Onboarding §5.1 et §15 SP1-SP6, demandes du PM) : 2,9 s en tout, 3,0 s au plus ; avec « réduire les
/// animations » : 0,8 s et aucun mouvement. L'appelant mesure le temps RÉEL : un appareil lent ne peut jamais étirer le
/// splash au-delà de `totalMs`.
///
/// Sécurité (SP4) : un seul apparition et une seule disparition du cœur, rien ne clignote ; les couleurs sont le bleu
/// de fond, le « M + i » blanc et le vert de marque : aucun éclair blanc, aucun rouge.
struct SplashTimeline: Equatable {
    static let holdMs = 300
    static let appearMs = 400
    /// Gonflement, y compris les `popPauseMs` finales où le cœur reste à sa plus grande taille.
    static let inflateMs = 1500
    static let popPauseMs = 120
    static let burstMs = 500
    static let exitMs = 200
    /// Version réduite : le logo complet reste immobile ce temps-là, puis s'efface.
    static let stillMs = 600
    /// Borne promise au PM : la version animée ne la dépasse jamais.
    static let maxAllowedMs = 3000
    /// Taille atteinte par le cœur juste avant d'éclater, par rapport à sa taille de repos (1 = celle de l'icône) :
    /// « un petit peu plus gros que sur le logo officiel » (PM, recette du 2026-10-30).
    static let maxScale = 1.2
    static let particleCount = 12

    let reduceMotion: Bool

    private var exitStartMs: Int {
        reduceMotion ? Self.stillMs : Self.holdMs + Self.appearMs + Self.inflateMs + Self.burstMs
    }

    /// Temps au bout duquel le splash doit avoir disparu.
    var totalMs: Int {
        exitStartMs + Self.exitMs
    }

    func isFinished(elapsedMs: Int) -> Bool {
        elapsedMs >= totalMs
    }

    func frame(atMs elapsedMs: Int) -> SplashFrame {
        let time = max(elapsedMs, 0)
        if time >= totalMs {
            return SplashFrame(phase: .done, heartScale: 0, heartAlpha: 0, burstProgress: nil, overlayAlpha: 0)
        }
        if time >= exitStartMs {
            let fade = 1 - Double(time - exitStartMs) / Double(Self.exitMs)
            // Réduit : le logo complet s'efface. Complet : l'éclat est fini, il ne reste que le logo qui s'efface avec
            // le bleu.
            let heart = reduceMotion ? 1.0 : 0.0
            return SplashFrame(
                phase: .exit,
                heartScale: heart,
                heartAlpha: heart,
                burstProgress: nil,
                overlayAlpha: fade
            )
        }
        if reduceMotion {
            return SplashFrame(phase: .still, heartScale: 1, heartAlpha: 1, burstProgress: nil, overlayAlpha: 1)
        }
        return animatedFrame(atMs: time)
    }

    private func animatedFrame(atMs time: Int) -> SplashFrame {
        let appearStart = Self.holdMs
        let inflateStart = appearStart + Self.appearMs
        let burstStart = inflateStart + Self.inflateMs
        if time < appearStart {
            return SplashFrame(phase: .hold, heartScale: 0, heartAlpha: 0, burstProgress: nil, overlayAlpha: 1)
        }
        if time < inflateStart {
            let progress = Double(time - appearStart) / Double(Self.appearMs)
            return SplashFrame(
                phase: .appear,
                heartScale: easeOutBack(progress),
                heartAlpha: min(progress * 3, 1),
                burstProgress: nil,
                overlayAlpha: 1
            )
        }
        if time < burstStart {
            // Les `popPauseMs` dernières du gonflement : le cœur reste un instant à sa plus grande taille, pour que
            // l'éclat qui suit puisse se lire.
            let progress = min(Double(time - inflateStart) / Double(Self.inflateMs - Self.popPauseMs), 1)
            return SplashFrame(
                phase: .inflate,
                heartScale: inflateScale(progress),
                heartAlpha: 1,
                burstProgress: nil,
                overlayAlpha: 1
            )
        }
        // Éclat net : le cœur a disparu dès le tout premier instant.
        return SplashFrame(
            phase: .burst,
            heartScale: 0,
            heartAlpha: 0,
            burstProgress: Double(time - burstStart) / Double(Self.burstMs),
            overlayAlpha: 1
        )
    }
}

/// Léger dépassement puis retour : 0 -> environ 1,1 -> 1.
func easeOutBack(_ progress: Double) -> Double {
    let overshoot = 1.70158
    let steep = overshoot + 1
    let shifted = min(max(progress, 0), 1) - 1
    return 1 + steep * shifted * shifted * shifted + overshoot * shifted * shifted
}

/// Ballon que l'on gonfle : démarre doucement (1 = taille de repos), puis gonfle de plus en plus vite jusqu'à
/// `SplashTimeline.maxScale`, avec un petit effet élastique qui grandit quand le ballon se tend. L'oscillation est nulle
/// aux deux bouts : l'échelle est continue avec l'apparition qui précède.
func inflateScale(_ progress: Double) -> Double {
    let position = min(max(progress, 0), 1)
    let growth = 1 + (SplashTimeline.maxScale - 1) * pow(position, 2.2)
    let envelope = (0.3 + 0.7 * position) * pow(max(sin(Double.pi * position), 0), 0.5)
    let wobble = 1 + 0.03 * envelope * sin(2 * Double.pi * (2.5 * position + 4 * position * position))
    return growth * wobble
}

/// Les morceaux projetés par l'éclat, à `progress` 0 (à l'instant de l'éclat) jusqu'à 1 (disparus). Identiques à
/// chaque lancement (aucun hasard).
func burstParticles(progress: Double) -> [BurstParticle] {
    let position = min(max(progress, 0), 1)
    // Rapide au début, puis de plus en plus lent (l'éclat doit se lire).
    let outward = 1 - pow(1 - position, 2.2)
    let alpha = min(max(1 - position * position, 0), 1)
    return (0..<SplashTimeline.particleCount).map { index in
        let big = index % 2 == 0
        // Les 240 degrés du haut seulement (de 150 à 390 degrés, y vers le bas) : rien ne vole sur le « M » et le « i ».
        let degrees = 150 + 240 * (Double(index) + 0.5) / Double(SplashTimeline.particleCount)
        return BurstParticle(
            angleRadians: Double.pi * degrees / 180,
            distance: outward * (big ? 1 : 0.65),
            size: (1 - 0.8 * position) * (big ? 1 : 0.6),
            alpha: alpha
        )
    }
}
