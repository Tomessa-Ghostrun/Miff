import CoreGraphics
import Foundation

// L'indicateur de « tirer pour actualiser » du Feed (retour du PM sur le TestFlight 39 : aucun symbole visible sur l'iPhone). La roue d'UIKit est
// cachée par l'en-tête et l'indicateur piloté par l'état du view model seul pouvait ne jamais se montrer : celui-ci est piloté par le DÉCALAGE de
// la vue de défilement (visible PENDANT le tirage, qui se remplit avec la distance), puis par QUATRE signaux de chargement qui suffisent chacun :
// l'état « actualisation » du Feed, la roue d'UIKit, le relâchement d'un tirage assez long, et un minimum d'affichage de 0,8 s. Logique PURE :
// aucune vue, aucune horloge (le temps est donné), testée sans interface.

enum FeedPullIndicatorState: Equatable {
    case hidden
    /// Le tirage est en cours : `progress` de 0 à 1 (1 = assez tiré pour actualiser au relâchement).
    case pulling(progress: Double)
    /// L'actualisation est en cours (ou vient d'être déclenchée) : la roue tourne.
    case loading

    var isShown: Bool {
        self != .hidden
    }
}

struct FeedPullIndicatorLogic: Equatable {
    /// Distance tirée (en points) à partir de laquelle l'indicateur apparaît : en dessous, c'est le bruit d'un défilement.
    static let minVisible: CGFloat = 8
    /// Distance tirée qui remplit l'indicateur (à peu près celle où le système lance l'actualisation au relâchement).
    static let threshold: CGFloat = 70
    /// Un tirage relâché plus bas que son point le plus bas de plus de ceci est un relâchement.
    static let releaseDrop: CGFloat = 6
    /// La roue reste affichée au moins ce temps une fois lancée (retour du PM : ≥ 0,8 s).
    static let minLoadingSeconds: TimeInterval = 0.8
    /// La progression est arrondie à 1/20 : l'écran n'est redessiné que si elle change vraiment.
    static let progressSteps = 20

    private var peak: CGFloat = 0
    private var wasRefreshing = false
    private var loadingUntil: TimeInterval = -.infinity
    private var refreshingNow = false

    /// L'état à montrer. `distance` : la distance tirée (0 au repos, jamais négative) ; `dragging` : un doigt tire ; `refreshing` : un des signaux
    /// d'actualisation (Feed ou roue d'UIKit) est vrai ; `now` : une horloge monotone, en secondes.
    mutating func update(
        distance: CGFloat,
        dragging: Bool,
        refreshing: Bool,
        now: TimeInterval
    ) -> FeedPullIndicatorState {
        refreshingNow = refreshing
        if refreshing && !wasRefreshing {
            loadingUntil = max(loadingUntil, now + Self.minLoadingSeconds)
        }
        wasRefreshing = refreshing
        trackRelease(distance: distance, dragging: dragging, now: now)
        if refreshing || now < loadingUntil {
            return .loading
        }
        guard distance >= Self.minVisible else {
            return .hidden
        }
        let raw = Double(min(distance / Self.threshold, 1))
        let steps = (raw * Double(Self.progressSteps)).rounded()
        return .pulling(progress: steps / Double(Self.progressSteps))
    }

    /// Dans combien de secondes l'état changera tout seul (la fin du minimum d'affichage), ou nil.
    func secondsUntilChange(now: TimeInterval) -> TimeInterval? {
        guard !refreshingNow, loadingUntil > now else {
            return nil
        }
        return loadingUntil - now
    }

    /// Un tirage assez long, doigt levé, qui redescend : c'est un relâchement, la roue se lance (même si aucun signal d'actualisation n'arrive).
    private mutating func trackRelease(distance: CGFloat, dragging: Bool, now: TimeInterval) {
        if distance <= 0.5 {
            peak = 0
            return
        }
        if dragging {
            peak = max(peak, distance)
            return
        }
        if peak >= Self.threshold, distance < peak - Self.releaseDrop {
            loadingUntil = max(loadingUntil, now + Self.minLoadingSeconds)
            peak = 0
        }
    }
}
