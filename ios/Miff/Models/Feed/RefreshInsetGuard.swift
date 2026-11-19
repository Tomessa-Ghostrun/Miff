import Foundation

// CAUSE du « sautillement » du Feed quand on le tire pour l'actualiser (retour du PM, TestFlight 38). Pendant une actualisation, UIKit ajoute la
// hauteur de la roue d'actualisation à la marge haute de la vue de défilement, puis la retire en fin de geste. `ScrollDistanceReader` mesure
// « où commence le contenu » avec cette marge (`FeedTopInset`) : il lisait donc une origine plus basse de ~60 pt pendant le geste, la marge
// haute de la liste se RÉDUISAIT d'autant (SwiftUI, tout de suite) pendant qu'UIKit déplaçait le contenu (animé), puis l'inverse à la fin :
// le contenu sautait deux fois. Cette garde ignore les mesures d'origine pendant l'actualisation et un court instant après (le temps que
// la roue se retire). Du Swift pur : testé sans interface.

struct RefreshInsetGuard: Equatable {
    /// Le temps, après la fin de l'actualisation, pendant lequel la marge de la roue peut encore se retirer (animation d'UIKit : ~0,3 s).
    static let settleSeconds: TimeInterval = 0.6

    private var holdUntil: TimeInterval = -.infinity

    /// Peut-on prendre une mesure de l'origine du contenu maintenant ? `now` : une horloge monotone, en secondes. Pendant l'actualisation, non, et
    /// l'attente repart de zéro ; ensuite, non jusqu'à la fin du délai de retrait.
    mutating func allowsMeasure(refreshing: Bool, now: TimeInterval) -> Bool {
        if refreshing {
            holdUntil = now + Self.settleSeconds
            return false
        }
        return now >= holdUntil
    }

    /// Dans combien de secondes la mesure ignorée doit être reprise (nil : rien n'est en attente).
    func secondsUntilMeasure(now: TimeInterval) -> TimeInterval? {
        holdUntil > now ? holdUntil - now : nil
    }
}
