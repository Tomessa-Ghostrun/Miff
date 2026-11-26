import Foundation

// CONFIDENTIALITÉ, pour ce fichier (TIME-3, ticket « Profil, répartition du temps » §13) : le moteur dit au journal LOCAL des changements de
// présence « la présence de la Miff X est devenue (état, lieu) à l'instant t ». Seuls un identifiant de Miff, un état, un identifiant de lieu
// et une heure passent ici : jamais une coordonnée, un nom, une adresse. Rien n'est envoyé nulle part. Ce fichier ne CALCULE aucune présence
// (`PresenceMachine.derive` reste l'unique calculateur) : il recopie ce que le moteur décide déjà, à l'instant qu'il écrit dans le statut
// (`presenceAt`), pour que le Feed et le graphique ne se contredisent jamais. Il ne lit jamais la position et n'ajoute aucun réveil.

extension TrackingEngine {
    /// Un changement de présence d'une Miff, tel que le moteur l'écrit dans son statut : même état, même lieu, même instant.
    func noteHistory(_ miffId: String, _ fact: StatusFact) {
        history.record(PresenceFact(miffId: miffId, fact: fact))
    }

    /// La détection est armée pour ces Miffs : le début de leur suivi (le journal ne retient que le PREMIER appel par Miff ; un instant dans le
    /// futur est ramené à « maintenant »). Seuls un identifiant de Miff et une heure passent ici.
    func noteBegin(_ miffIds: Set<String>, at millis: Int) {
        for miffId in miffIds.sorted() {
            history.begin(miffId: miffId, atMillis: millis)
        }
    }

    /// La présence d'une Miff ne peut plus être confirmée (lieu supprimé ou modifié, suivi arrêté, voyage, fin de session...) : le temps qui
    /// suit est « non mesuré », jamais une supposition.
    func noteUnknown(_ miffId: String, at millis: Int) {
        history.record(PresenceFact(miffId: miffId, state: .unknown, placeId: nil, atMillis: millis))
    }

    /// Des Miffs qui ne sont plus suivies par ce téléphone. Celles dont la personne n'est plus membre (`noLongerMember`) perdent tout leur
    /// journal ; les autres (partage arrêté, texte à renouveler) gardent leurs faits et passent à « inconnue ».
    func noteLeft(_ miffIds: Set<String>, noLongerMember: Set<String>, now: Int) {
        for miffId in miffIds.sorted() {
            if noLongerMember.contains(miffId) {
                history.purge(miffId: miffId)
            } else {
                noteUnknown(miffId, at: now)
            }
        }
    }
}
