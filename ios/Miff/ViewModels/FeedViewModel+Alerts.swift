#if ALERT_DANGER
import Foundation

// ALERTE « Je suis en danger » (docs/tickets/alerte-danger.md 7.6 ; miroir de `watchAlerts` d'Android) : UN écouteur sur `miffs/{id}/alerts` de la Miff AFFICHÉE, tant que le
// Feed est à l'écran (0 lecture quand la collection est vide, 1 lecture par document qui change). Le Feed n'écrit rien. Les écrans de proches (carte rouge première, bulle
// rouge) en tirent leur image par `FeedBuilder`, qui ne garde que les alertes EN COURS à l'instant du calcul.

extension FeedViewModel {
    /// Un écouteur qui se termine (un refus : les règles ne sont pas à jour, l'accès est perdu) veut dire « pas d'alerte », jamais une erreur sur le Feed. Une alerte est tenue pour présente
    /// tant que l'écouteur ne dit pas autre chose, même depuis le cache : un doute sur une alerte ne se règle jamais en la cachant.
    func watchAlerts(_ miffId: String, generation mine: Int) async {
        for await heard in dependencies.services.dangerAlerts.observe(miffId: miffId) {
            guard generation == mine else {
                return
            }
            if heard.docs != alertDocs {
                alertDocs = heard.docs
            }
        }
        if generation == mine, !alertDocs.isEmpty {
            alertDocs = []
        }
    }
}
#endif
