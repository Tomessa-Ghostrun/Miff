import SwiftUI

// L'emplacement du compte à rebours, un SEUL composant pour le Feed (sous la bulle de la Miff) et le Profil (premier bloc) : même valeur au même
// instant (les frontières de minute viennent de l'échéance, pas du moment de l'affichage).
//
// COÛT ET BATTERIE (ticket §2.5) : aucune lecture ni écriture Firestore, aucun réseau, aucune notification, aucun événement d'analyse. Le statut
// vient de `MyStatusStore` (nourri par l'écouteur du Feed, ou par celui de MON seul statut sur le Profil et l'Update) et l'heure de l'appareil. UNE mise à jour par minute, alignée sur
// l'échéance (jamais une minuterie de 60 s qui dérive, jamais à la seconde) ; AUCUNE minuterie quand l'encart n'est pas visible (onglet quitté,
// app en arrière-plan, encart masqué) ; au retour, la valeur est recalculée depuis l'heure absolue, dès l'image qui revient.
//
// CONFIDENTIALITÉ : c'est MON statut, sur MON appareil ; les autres membres n'ont ni cet écran ni cette valeur.

/// Ce que l'hôte donne au compte à rebours : MON statut, l'horloge et le geste « ouvrir l'écran Update » (le point d'entrée EXISTANT : le
/// bouton central de la barre d'onglets).
struct CountdownContext {
    let status: MyStatusStore
    let clock: () -> Int
    let openUpdate: () -> Void
}

private struct CountdownContextKey: EnvironmentKey {
    static let defaultValue: CountdownContext? = nil
}

extension EnvironmentValues {
    /// nil (aperçus, écrans sans coquille) : aucun compte à rebours.
    var countdownContext: CountdownContext? {
        get { self[CountdownContextKey.self] }
        set { self[CountdownContextKey.self] = newValue }
    }
}

/// L'heure de l'appareil en millisecondes depuis 1970.
func countdownSystemClock() -> Int {
    Int(Date().timeIntervalSince1970 * 1000)
}

struct CountdownSlot: View {
    /// L'onglet qui porte l'encart est celui en usage.
    let isActive: Bool
    /// L'espace AU-DESSUS de l'encart, porté par l'encart lui-même : masqué, l'emplacement ne prend AUCUNE place (comme Android). L'hôte
    /// range donc le slot dans une pile d'espacement 0 avec ce qui le précède, jamais comme enfant direct d'une pile qui espace (un enfant
    /// vide y garderait son espacement).
    var topSpacing: CGFloat = 0
    @Environment(\.countdownContext) private var context

    var body: some View {
        if let context {
            CountdownSlotContent(context: context, status: context.status, isActive: isActive, topSpacing: topSpacing)
        }
    }
}

private struct CountdownSlotContent: View {
    let context: CountdownContext
    @ObservedObject var status: MyStatusStore
    let isActive: Bool
    let topSpacing: CGFloat
    /// Ne sert qu'à FORCER un nouveau calcul à chaque frontière de minute : la valeur affichée est toujours recalculée avec l'horloge.
    @State private var tick = 0
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var watching: Bool {
        isActive && scenePhase != .background
    }

    /// Calculé à CHAQUE affichage depuis l'heure absolue : jamais un compteur décrémenté.
    private var state: CountdownState {
        _ = tick
        return CountdownRules.state(status.facts, sharingStopped: status.sharingStopped, nowMillis: context.clock())
    }

    var body: some View {
        let current = state
        Group {
            if case .visible(let display) = current {
                CountdownCard(display: display, texts: CountdownTexts(), onOpen: context.openUpdate)
                    .padding(.top, topSpacing)
                    .transition(.opacity)
            }
        }
        // Apparition et disparition : un fondu court, et le contenu dessous suit sans saut. Instantané avec « Réduire les animations ».
        .animation(
            CountdownBorder.fadeDuration(reduceMotion: reduceMotion).map { Animation.easeOut(duration: $0) },
            value: current.isVisible
        )
        .task(id: TickKey(watching: watching, nextChangeMillis: current.nextChangeMillis)) {
            await runTicks(watching: watching)
        }
    }

    /// Dort jusqu'à la prochaine frontière de minute de l'échéance, puis demande un nouveau calcul ; s'arrête quand l'encart disparaît (ou n'est
    /// pas regardé). La tâche est annulée dès que l'onglet change, que l'app part ou que la valeur change.
    private func runTicks(watching: Bool) async {
        guard watching else {
            return
        }
        while !Task.isCancelled {
            let now = context.clock()
            guard case .visible(let display) = CountdownRules.state(
                status.facts, sharingStopped: status.sharingStopped, nowMillis: now
            ) else {
                return
            }
            // Au moins 50 ms : une veille qui se réveille un peu tôt ne fait jamais tourner la boucle à vide.
            let waitMillis = max(display.nextChangeMillis - now, 50)
            try? await Task.sleep(nanoseconds: UInt64(waitMillis) * 1_000_000)
            guard !Task.isCancelled else {
                return
            }
            advance()
        }
    }

    /// Un nouveau calcul. La bordure glisse en 600 ms (instantané avec « Réduire les animations ») ; le fondu de la disparition est celui de
    /// `.animation(value:)` ci-dessus.
    private func advance() {
        guard let seconds = CountdownBorder.glideDuration(reduceMotion: reduceMotion) else {
            tick += 1
            return
        }
        withAnimation(.easeInOut(duration: seconds)) {
            tick += 1
        }
    }
}

/// Ce qui relance la veille : l'encart regardé ou non, et l'instant du prochain changement (nil : masqué). Il ne change pas entre deux
/// minutes : la veille en cours n'est jamais relancée pour rien.
private struct TickKey: Equatable {
    let watching: Bool
    let nextChangeMillis: Int?
}

private extension CountdownState {
    var nextChangeMillis: Int? {
        if case .visible(let display) = self {
            return display.nextChangeMillis
        }
        return nil
    }
}
