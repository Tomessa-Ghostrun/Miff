import Foundation

/// Les lectures du Feed (FEED-5 à FEED-7), construites d'un seul tenant. Rien ici n'ouvre Firestore : chaque lecture n'est faite
/// qu'à l'usage (l'écoute des statuts quand le Feed est à l'écran, la position au toucher de « Localiser », la relecture au geste
/// « Actualiser »). Le Feed n'écrit JAMAIS.
struct FeedServices {
    let statuses: StatusObserver
    let positions: PositionReader
    let refresher: FeedRefresher

    static func live() -> FeedServices {
        FeedServices(
            statuses: FirestoreStatusObserver(),
            positions: FirestorePositionReader(),
            refresher: FirestoreFeedRefresher()
        )
    }
}
