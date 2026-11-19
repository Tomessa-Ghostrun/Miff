import Foundation

/// Les lectures du Feed (FEED-5 à FEED-7), construites d'un seul tenant. Rien ici n'ouvre Firestore : chaque lecture n'est faite
/// qu'à l'usage (l'écoute des statuts quand le Feed est à l'écran, la position au toucher de « Localiser », la relecture au geste
/// « Actualiser »). Le Feed n'écrit JAMAIS.
struct FeedServices {
    let statuses: StatusObserver
    let positions: PositionReader
    let refresher: FeedRefresher
    #if ALERT_DANGER
    /// ALERTE « Je suis en danger » : les alertes de la Miff affichée, en direct (UN écouteur sur `miffs/{id}/alerts` tant que le Feed est à l'écran ; aucune lecture
    /// quand il n'y en a pas). Absent dans un build sans la fonction : rien n'est lu.
    var dangerAlerts: DangerAlertFeed = FirestoreDangerAlertStore()
    #endif

    static func live() -> FeedServices {
        FeedServices(
            statuses: FirestoreStatusObserver(),
            positions: FirestorePositionReader(),
            refresher: FirestoreFeedRefresher()
        )
    }
}
