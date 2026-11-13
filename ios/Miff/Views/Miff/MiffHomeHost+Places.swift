import SwiftUI

// Les gestes des lieux de l'hôte de navigation de l'app (LOC-3), séparés de `MiffHosts.swift` pour une taille de fichier et de type
// raisonnable. Rien de nouveau : le code est celui d'avant, déplacé.

extension MiffHomeHost {
    /// Ce que la liste des lieux d'une Miff reçoit : Miff+ ou non (nil tant que ce n'est pas lu auprès du serveur), le message d'un
    /// enregistrement, les gestes.
    func placeContext(for miffId: String) -> PlaceListContext {
        // Miff+ n'est affirmé (ou nié) que s'il a été LU auprès du serveur : sinon nil, et la liste n'affirme rien de la formule
        // gratuite (ni compteur, ni pastille).
        var premium: Bool?
        if case .ready(let ready) = directory.state, ready.premiumKnown {
            premium = ready.premium
        }
        let notices = placeNotices
        return PlaceListContext(
            premium: premium,
            notice: notices.notice,
            actions: PlaceListActions(
                onOpen: { place in open(.placeDetail(miffId: miffId, placeId: place.id)) },
                // Toujours le formulaire : il relit l'abonnement et les lieux auprès du SERVEUR et ne propose l'offre que si la limite
                // est vraiment atteinte (jamais une décision prise sur l'annuaire, qui peut ignorer un Miff+).
                // Le formulaire coche d'abord CETTE Miff (celle dont la liste est regardée), pas la dernière rejointe.
                onAdd: { _ in open(.placeForm(placeId: nil, miffId: miffId)) }
            ),
            onLeave: { notices.consume() }
        )
    }

    /// Le formulaire est fini. Un lieu supprimé (ou qui n'existe plus) ramène à la liste : le détail d'un lieu qui n'existe plus ne
    /// vaut pas le retour.
    func finishPlaceForm(_ end: PlaceFormEnd) {
        guard let last = path.last, last.isPlaceFormScreen else {
            return
        }
        if end == .placeGone {
            placeNotices.gone()
        }
        path.removeLast()
        if end != .saved, case .placeDetail? = path.last {
            path.removeLast()
        }
    }

    /// L'offre s'ouvre ; elle REMPLACE le formulaire quand celui-ci n'a rien à offrir (la limite trouvée à l'ouverture).
    func openPlacePaywall(_ reason: PaywallReason, replacesForm: Bool) {
        if replacesForm, case .placeForm? = path.last {
            path.removeLast()
        }
        presentPaywall(reason)
    }

    /// Le lieu a été supprimé ailleurs pendant que son détail était ouvert : « Ce lieu n'existe plus. », et retour à la liste.
    func leavePlaceDetail() {
        guard case .placeDetail? = path.last else {
            return
        }
        placeNotices.gone()
        path.removeLast()
    }
}
