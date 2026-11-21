#if ALERT_DANGER
import SwiftUI
import UIKit

// Le GESTE de maintien de l'alerte (ticket docs/tickets/alerte-danger.md 7.2 et 7.4), partagé par l'obturateur de l'Update (départ) et la bannière
// (fin). Les règles du temps vivent dans `DangerHoldTracker` (pures, testées) ; ce fichier ne fait que lire le doigt et rythmer l'horloge.
//
// ANTI-ACCIDENT (critères AD1 à AD3) : un toucher plus court que 400 ms est un APPUI (une photo, comme avant) ; ensuite seulement l'écran devient
// l'alerte et les 5 secondes comptent. Relâcher, l'app qui passe en arrière-plan, un appel entrant ou une annulation du toucher par le système
// ANNULE, SANS message (seulement un très léger retour haptique) : un faux départ ne se commente pas.
//
// LE DOIGT PEUT SORTIR DU BOUTON (retour du PM en recette) : il faut le poser SUR la cible (le bouton, ou la bannière) pour engager le geste ; une
// fois posé, la distance ne compte PLUS : le chargement continue jusqu'au relâchement. Un `DragGesture(minimumDistance: 0)` suit le toucher jusqu'au
// relâchement où qu'il aille (un `LongPressGesture`, lui, s'annule dès que le doigt s'éloigne) et rien ne lit la position. Un doigt posé HORS de la
// cible ne déclenche rien : le geste n'existe que pour les toucher qui commencent sur la vue. Haptique : un léger à l'armement, un crescendo
// de retours courts de plus en plus rapprochés, un net à 5 s. AUCUN son. La barre se remplit en ligne droite, au rythme de l'horloge : « Réduire
// les animations » n'y change rien (aucun rebond), l'état final est le même.

@MainActor
final class DangerHoldModel: ObservableObject {
    /// Vrai de l'armement jusqu'à la fin ou l'annulation.
    @Published private(set) var armed = false
    /// 0,0 à 1,0 : la barre qui se remplit ; 0 tant que l'écran n'est pas armé.
    @Published private(set) var progress = 0.0

    /// Un toucher plus court que l'armement : l'appui d'avant (une photo).
    var onTap: () -> Void = {}
    /// Le doigt vient de se poser sur la cible : la première interaction (appui, maintien, début d'alerte). Retire la bulle d'onboarding.
    var onTouchDown: () -> Void = {}
    /// L'écran vient de s'armer.
    var onArmed: () -> Void = {}
    /// Les 5 secondes sont tenues.
    var onDone: () -> Void = {}

    private var tracker: DangerHoldTracker
    /// Le doigt est posé (jusqu'au relâchement) : un geste terminé ou annulé n'est jamais relancé par le même doigt.
    private var fingerDown = false
    private var ticker: Task<Void, Never>?
    private let nowMillis: () -> Int
    private let haptics: DangerHaptics

    init(
        armMillis: Int = DangerAlertRules.armMillis,
        holdMillis: Int = DangerAlertRules.holdMillis,
        nowMillis: @escaping () -> Int = { Int(ProcessInfo.processInfo.systemUptime * 1000) },
        haptics: DangerHaptics = DangerHaptics()
    ) {
        tracker = DangerHoldTracker(armMillis: armMillis, holdMillis: holdMillis)
        self.nowMillis = nowMillis
        self.haptics = haptics
    }

    // MARK: - Ce que lit le doigt

    func touchBegan() {
        guard !fingerDown else {
            return
        }
        fingerDown = true
        onTouchDown()
        tracker.begin(atMillis: nowMillis())
        ticker?.cancel()
        ticker = Task { await self.run() }
    }

    func touchEnded() {
        fingerDown = false
        apply(tracker.release(nowMillis: nowMillis()))
    }

    /// Le système a repris le toucher (le doigt n'est plus suivi et `touchEnded` ne viendra jamais) : un geste encore en cours est annulé sans un mot,
    /// jamais mené jusqu'à 5 secondes sans doigt. Sans effet quand le doigt s'est relevé normalement (`touchEnded` est déjà passé).
    func touchLost() {
        if fingerDown {
            cancel()
        }
    }

    /// L'app passe en arrière-plan, la fenêtre perd le focus (appel entrant) : un geste armé est annulé sans un mot.
    func cancel() {
        fingerDown = false
        apply(tracker.cancel())
    }

    // MARK: - L'horloge

    private func run() async {
        var pause = Self.firstPulsePauseMillis
        var sincePulse = 0
        while !Task.isCancelled && tracker.isTouching {
            try? await Task.sleep(nanoseconds: Self.tickNanoseconds)
            guard !Task.isCancelled, tracker.isTouching else {
                return
            }
            let now = nowMillis()
            let outcome = tracker.advance(nowMillis: now)
            progress = tracker.progress(nowMillis: now)
            if tracker.armed {
                sincePulse += Self.tickMillis
                if sincePulse >= pause {
                    haptics.pulse()
                    sincePulse = 0
                    pause = max(Int(Double(pause) * Self.pulseShrink), Self.minPulsePauseMillis)
                }
            }
            apply(outcome)
        }
    }

    private func apply(_ outcome: DangerHoldOutcome) {
        switch outcome {
        case .nothing:
            break
        case .tap:
            stop()
            onTap()
        case .armed:
            armed = true
            haptics.armed()
            onArmed()
        case .done:
            stop()
            haptics.done()
            onDone()
        case .cancelled:
            let wasArmed = armed
            stop()
            if wasArmed {
                haptics.cancelled()
            }
        }
    }

    private func stop() {
        ticker?.cancel()
        ticker = nil
        armed = false
        progress = 0
    }

    private static let tickMillis = 33
    private static let tickNanoseconds: UInt64 = 33_000_000
    private static let firstPulsePauseMillis = 800
    private static let minPulsePauseMillis = 150
    private static let pulseShrink = 0.8
}

/// Les retours haptiques du geste (jamais un son). Réunis ici pour que l'écran ne touche pas à UIKit.
struct DangerHaptics {
    func armed() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    func pulse() {
        UIImpactFeedbackGenerator(style: .soft).impactOccurred()
    }

    func done() {
        UINotificationFeedbackGenerator().notificationOccurred(.warning)
    }

    func cancelled() {
        UIImpactFeedbackGenerator(style: .soft).impactOccurred(intensity: 0.4)
    }
}

/// Lit le doigt sur la vue : `DragGesture` à distance zéro (le toucher commence tout de suite), un `onEnded` pour le relâchement. La position du doigt
/// n'est JAMAIS lue : sortir de la vue ne change rien. Un `@GestureState` retombe à faux quand le toucher finit OU quand le système l'annule (auquel
/// cas `onEnded` ne vient pas) : le modèle est alors prévenu.
struct DangerHoldGestureModifier: ViewModifier {
    @ObservedObject var model: DangerHoldModel
    @Environment(\.scenePhase) private var scenePhase
    @GestureState private var touching = false

    func body(content: Content) -> some View {
        content
            .gesture(
                DragGesture(minimumDistance: 0)
                    .updating($touching) { _, state, _ in
                        state = true
                    }
                    .onChanged { _ in
                        model.touchBegan()
                    }
                    .onEnded { _ in
                        model.touchEnded()
                    }
            )
            .onChange(of: touching) { isTouching in
                if !isTouching {
                    notifyTouchLost()
                }
            }
            .onChange(of: scenePhase) { phase in
                if phase != .active {
                    model.cancel()
                }
            }
    }

    /// Un tour de boucle plus tard : quand le doigt s'est relevé normalement, `touchEnded` (un appui court est une photo) est déjà passé et il ne reste
    /// rien à annuler.
    private func notifyTouchLost() {
        let model = self.model
        Task { @MainActor in
            await Task.yield()
            model.touchLost()
        }
    }
}

extension View {
    /// Fait du geste de maintien le toucher de cette vue : appui court, armement, fin des 5 secondes sont rendus par `model`.
    func dangerHoldGesture(_ model: DangerHoldModel) -> some View {
        modifier(DangerHoldGestureModifier(model: model))
    }
}
#endif
