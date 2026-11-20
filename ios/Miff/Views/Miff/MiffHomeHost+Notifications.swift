import SwiftUI

// Les gestes de l'hôte de navigation liés aux notifications et aux suppressions en cours (SRV-4I, NOTIF-P0), séparés de `MiffHosts.swift`
// pour une taille de fichier et de type raisonnable. Rien de nouveau : le code est celui d'avant, déplacé.

extension MiffHomeHost {
    /// Ouvre le Feed de la Miff demandée par une notification, SEULEMENT si c'est une Miff du compte connecté (annuaire lu auprès du serveur si
    /// besoin ; appartenance inconnue, Miff étrangère, propre update : rien ne s'ouvre, la demande est oubliée).
    func openRequestedFeed() async {
        guard let target = pushRequests.pending else {
            return
        }
        await directory.refreshIfNeeded()
        let accepted = PushOpenRules.accepts(
            target,
            currentUid: environment.session.currentUid,
            miffIds: PushServices.knownMiffIds(directory)
        )
        pushRequests.clear()
        guard accepted else {
            return
        }
        path.removeAll()
        directory.select(miffId: target.miffId)
        // CNC-I (ticket §8.4) : le Feed amène aussi la carte de l'auteur à l'écran (comme une ligne du centre) ; sans effet si elle n'y est pas.
        environment.notifications.tabRequests.requestFocus(FeedFocus(miffId: target.miffId, uid: target.authorUid))
        feedOpenCount += 1
    }

    /// Le flux d'annonces est ouvert AVANT la reprise : rien de ce qu'elle annonce n'est perdu.
    func watchDeletions() async {
        let deletions = environment.miffDeletions
        let notices = deletions.notices()
        deletions.resumePending()
        for await notice in notices {
            switch notice {
            case .resumed:
                banner = .resumed
            case .completed(let miffId):
                // Sur l'écran de cette Miff, c'est l'écran lui-même qui le dit : rien n'est dit deux fois.
                if path.last != .miff(miffId) {
                    banner = .done
                }
            }
        }
    }

    /// Toucher une ligne du centre de notifications : la pile revient à la coquille et l'onglet demandé s'ouvre (le Feed, ou l'écran
    /// Update — remplacé par le Feed dans les builds qui n'ont pas le bouton central).
    func openNotification(_ destination: NotificationDestination) {
        path.removeAll()
        if case .placeRequests(let miffId) = destination {
            // Une ligne de demande ouvre le Profil sur la Miff concernée (sans effet si la personne n'en fait plus partie).
            directory.select(miffId: miffId)
        }
        if case .feedCard(let miffId, let uid) = destination {
            // Une ligne « Depuis ta dernière visite » : la Miff concernée devient celle du Feed (sans effet si la personne n'en fait plus
            // partie : le Feed s'ouvre tel quel, jamais une erreur), et le Feed amène la carte du proche à l'écran.
            directory.select(miffId: miffId)
            environment.notifications.tabRequests.requestFocus(FeedFocus(miffId: miffId, uid: uid))
        }
        environment.notifications.tabRequests.request(
            NotificationNavigation.tab(for: destination, showsUpdateButton: TabBarConfig.current.showsUpdateButton)
        )
    }
}
