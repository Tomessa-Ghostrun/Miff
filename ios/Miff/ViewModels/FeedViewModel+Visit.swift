import Foundation

// NOTIF-P1I, « Depuis ta dernière visite » : ce que le Feed dit à la comparaison. CONFIDENTIALITÉ et COÛT : tout est LOCAL, rien n'est lu ni
// écrit sur le serveur à cause de cet appel (les statuts sont ceux que le Feed écoute déjà) ; seuls des identifiants, des catégories et des
// heures sont donnés, les prénoms vont en mémoire seulement. Une comparaison qui échoue ne gêne jamais le Feed.

extension FeedViewModel {
    /// L'image de la Miff affichée, en faits, quand elle est FRAÎCHE : le serveur a confirmé les statuts, le téléphone est en ligne, mon
    /// partage est actif (`LastVisit.sample` dit nil sinon). Appelée à chaque calcul : l'image gardée suit ce qui est à l'écran, même
    /// quand quelqu'un passe au rouge pendant que le Feed est ouvert (ce qui a été vu n'est pas redit à la visite suivante).
    func tellVisit(_ content: FeedContent) {
        // Seulement pendant une observation en cours : les statuts gardés en mémoire d'avant une absence ne sont jamais pris pour frais.
        guard case .data(let miffId, let snapshot)? = piece, miffId == content.miffId, miffId == observedMiffId,
              let me = dependencies.currentUid() else {
            return
        }
        let input = VisitSampleInput(
            miffId: miffId,
            members: snapshot.members,
            places: snapshot.places,
            myUid: me,
            statuses: statusView,
            waitingAgreement: waiting.contains(miffId),
            offline: offline || staleAfterRefresh
        )
        guard let state = LastVisit.sample(input, nowMillis: nowMillis) else {
            return
        }
        var names: [String: String] = [:]
        for member in snapshot.members where member.uid != me {
            names[member.uid] = member.callName
        }
        dependencies.onFreshPicture(me, state, names)
    }
}
