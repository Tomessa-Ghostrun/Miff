import Foundation

// Ce que décide la TRANSACTION qui démarre une alerte, d'après ce qu'elle a lu (ma fiche, mon document d'alerte, mon compteur du jour), en fonction PURE : l'ORDRE des
// vérifications compte et il est testé. Elle n'écrit jamais rien elle-même. Miroir d'`AlertStartPlan.kt` d'Android (v2).
//
// L'ORDRE (revue indépendante de l'Android, M1) : une alerte de moi DÉJÀ en cours passe AVANT la limite du jour. Scénario : le 3e début du jour a été accepté mais la
// réponse s'est perdue (délai, app tuée) ; à la reprise le compteur est plein. Juger la limite d'abord dirait « limite atteinte » alors que le cercle voit déjà la carte
// rouge, et la personne n'aurait ni bannière ni moyen de la terminer pendant 7 jours. Une alerte vivante est donc ADOPTÉE d'abord ; la limite n'est jugée qu'avant d'écrire
// un nouveau crédit. Entre les deux, un ré-armement à moins de 60 s attend (les règles le refuseraient) sans rien consommer.

enum DangerStartDecision: Equatable {
    case notMember
    case sharingStopped
    /// Mon alerte est déjà en cours dans cette Miff : rien n'est écrit, elle est adoptée (bannière, fin).
    case adopt(DangerAlertDoc)
    /// Le début précédent date de moins de 60 s : attendre `waitMillis`, puis réessayer. Ne consomme rien.
    case wait(waitMillis: Int)
    /// Les trois débuts des dernières 24 h sont pris : rien n'est tenté.
    case quotaFull
    /// Écrire le crédit (créer le compteur quand `slot` est nil, sinon remplacer cette case) et l'alerte avec ce `seq`.
    case write(slot: Int?, seq: Int)
}

enum DangerAlertStartPlan {
    static func decide(
        cardExists: Bool,
        sharing: Bool,
        alert: DangerAlertDoc?,
        counter: DangerAlertQuota?,
        nowMillis: Int
    ) -> DangerStartDecision {
        guard cardExists else {
            return .notMember
        }
        guard sharing else {
            return .sharingStopped
        }
        if let alert, alert.isLive(nowMillis: nowMillis) {
            return .adopt(alert)
        }
        if let startedAt = alert?.startedAtMillis {
            let age = nowMillis - startedAt
            if age >= 0 && age < DangerAlertRules.rearmMinGapMillis {
                return .wait(waitMillis: DangerAlertRules.rearmMinGapMillis - age + DangerAlertRules.rearmSafetyMillis)
            }
        }
        let seq = (alert?.seq ?? 0) + 1
        // Pas de compteur : sa CRÉATION est le crédit du premier début, jamais « plein ».
        guard let counter else {
            return .write(slot: nil, seq: seq)
        }
        guard let slot = counter.slotToReplace(nowMillis: nowMillis) else {
            return .quotaFull
        }
        return .write(slot: slot, seq: seq)
    }
}
