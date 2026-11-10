import Foundation

// Le sélecteur de Miff de l'en-tête du Feed : qui voit quoi, et ce que fait un toucher sur « Rejoindre » ou « Créer » (décision du PM,
// 2026-11-08, qui REMPLACE D12 / FPR Q2 : « pastille non cliquable avec une seule Miff, entrées réservées au Premium »).
// Règles pures, sans écran : elles sont testées seules. Elles n'écrivent rien et ne lisent rien.

/// Ce que dit la pastille de la Miff.
enum FeedPillState: Equatable {
    /// Aucune Miff : « Pas encore de Miff », ne fait rien.
    case noMiff
    /// Au moins une Miff, POUR TOUT LE MONDE (gratuit, Miff+ ou formule inconnue) : le nom, une flèche, une liste.
    case selectable(name: String, miffs: [MiffSummary], activeId: String?)

    /// L'état de la pastille d'après l'annuaire. `nil` tant que l'annuaire n'est pas lu (rien n'est dessiné).
    static func make(from ready: DirectoryReady?) -> FeedPillState? {
        guard let ready else {
            return nil
        }
        guard let active = ready.active, !ready.miffs.isEmpty else {
            return .noMiff
        }
        return .selectable(name: active.name, miffs: ready.miffs, activeId: ready.activeId)
    }
}

/// La formule de la personne, telle que l'annuaire la connaît. Une formule INCONNUE (abonnement non lu, ou réponse du cache : hors
/// connexion) n'est JAMAIS prise pour « gratuite » : un doute ne ferme pas la porte à un compte qui a Miff+.
enum FeedSelectorFormula: Equatable {
    case free
    case premium
    case unknown

    init(_ ready: DirectoryReady) {
        if !ready.premiumKnown {
            self = .unknown
        } else {
            self = ready.premium ? .premium : .free
        }
    }
}

/// Ce qui suit un toucher sur « Rejoindre une Miff » ou « Créer une Miff ».
enum FeedSelectorOutcome: Equatable {
    /// Le parcours normal (saisie du code, ou nom de la nouvelle Miff).
    case openFlow
    /// L'offre : un compte gratuit qui a déjà une Miff. Une aide du produit, pas la barrière : le serveur refuse de toute façon une
    /// 2e Miff à un compte gratuit (emplacement unique `free` des règles).
    case openPaywall
}

enum FeedSelectorRules {
    /// Le sélecteur s'ouvre dès qu'il y a une Miff à montrer, pour tout le monde.
    static func isOpenable(_ state: FeedPillState?) -> Bool {
        if case .selectable? = state {
            return true
        }
        return false
    }

    /// Gratuit avec au moins une Miff : l'offre. Miff+ : le parcours normal. Formule inconnue (hors connexion, cache) : le parcours
    /// normal aussi, et c'est le SERVEUR qui tranche (le parcours relit l'abonnement et dit calmement « une seule Miff » si besoin).
    static func outcome(formula: FeedSelectorFormula, miffCount: Int) -> FeedSelectorOutcome {
        formula == .free && miffCount >= 1 ? .openPaywall : .openFlow
    }

    static func outcome(for ready: DirectoryReady?) -> FeedSelectorOutcome {
        guard let ready else {
            return .openFlow
        }
        return outcome(formula: FeedSelectorFormula(ready), miffCount: ready.miffs.count)
    }

    /// La ligne d'une Miff dans la feuille : la Miff affichée est cochée (le système n'affiche pas d'icône dans une feuille d'actions,
    /// donc une coche écrite ; le gras seul ne suffirait pas).
    static func rowTitle(name: String, isActive: Bool) -> String {
        isActive ? "\u{2713} \(name)" : name
    }
}
