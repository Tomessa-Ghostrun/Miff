#if DEBUG && REVIEW_PROMPT
import SwiftUI

/// La fenêtre d'avis montrée SEULE avec un état fixe, par-dessus un Feed de démonstration, pour les captures d'écran de la CI
/// (`-MiffPreview review-question`, `review-thanks`, `review-feedback`). Aucun réseau, aucune session, aucune écriture : c'est la VRAIE
/// carte (`ReviewPromptOverlay`), sans rien retenir sur l'appareil. Compilé en configuration Debug SEULEMENT (et seulement avec le
/// drapeau REVIEW_PROMPT, toujours présent en Debug).
struct ReviewScreenPreview: View {
    let name: String

    private var stage: ReviewStage {
        switch name {
        case "review-thanks":
            return .thanksForLove
        case "review-feedback":
            return .thanksForFeedback
        default:
            return .question
        }
    }

    var body: some View {
        ZStack {
            FeedScreenPreview(name: "feed-green")
            ReviewPromptOverlay(stage: stage, actions: .inert)
        }
    }
}
#endif
