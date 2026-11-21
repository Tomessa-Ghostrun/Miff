import Foundation

/// Ce qu'il faut écrire dans `users/{uid}.analyticsConsent` quand la personne allume ou éteint « statistiques d'usage et rapports de
/// plantage anonymes » dans Réglages > Confidentialité (ticket ONB-3b, critère RG6).
enum AnalyticsConsentChange: Equatable {
    /// Le compte est déjà dans l'état voulu : aucune écriture.
    case unchanged
    /// Activer ou réactiver : `enabled` vrai, `consentedAt` à l'heure du serveur, `consentVersion` du texte montré, `revokedAt` vide.
    case activate
    /// Retirer (depuis un consentement ACTIF seulement) : `enabled` faux, `revokedAt` à l'heure du serveur ; la TRACE du consentement
    /// (`consentedAt`, `consentVersion`) reste telle qu'elle était.
    case withdraw
    /// Un texte plus récent que celui que cette app connaît a déjà été accepté : l'app n'écrit jamais une version plus ancienne.
    case refused
}

/// Logique pure, sans Firestore : les transitions sont celles qu'autorisent les règles (`validAnalyticsConsentChange`,
/// `backend/data-model.md` §2.1), rien d'autre ne serait accepté. Même logique qu'Android (`AnalyticsConsentTransition`).
/// Ni position ni donnée personnelle : quatre clés qui décrivent un choix.
enum AnalyticsConsentTransition {
    /// `previous` est le bloc tel que le serveur le détient maintenant (nil = absent), lu dans la même transaction.
    static func change(
        previous: [String: Any]?,
        enable: Bool,
        version: String = ConsentVersions.analytics
    ) -> AnalyticsConsentChange {
        let wasEnabled = (previous?["enabled"] as? Bool) == true
        if enable {
            if wasEnabled {
                return .unchanged
            }
            // Le format fixe `AAAA-MM-vNN` rend l'ordre alphabétique égal à l'ordre chronologique : la version ne recule jamais.
            if let lastVersion = previous?["consentVersion"] as? String, version < lastVersion {
                return .refused
            }
            return .activate
        }
        // Seul un consentement ACTIF se retire.
        return wasEnabled ? .withdraw : .unchanged
    }
}
