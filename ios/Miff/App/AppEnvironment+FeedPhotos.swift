import Foundation

extension AppEnvironment {
    /// Les photos du Feed (FEED-8), construites par l'onglet Feed quand il est créé (rien n'est lu avant qu'une carte vienne à l'écran). La mémoire
    /// des photos est celle de l'Update (`update.photoCache`) : ma photo publiée y est déjà, ma carte ne la relit pas. Le Feed ne fait que LIRE.
    func makeFeedPhotos() -> FeedPhotos {
        let session = self.session
        let network = self.network
        let local = TrackingLocalFileStore()
        // Un refus de photo ne dit « Partage ta position… » que si MA position n'est pas publiée : la note locale du suivi (lue seulement après un
        // refus, un « oui » suffit), sinon UNE lecture de mon propre document de position, mémorisée 30 s par Miff. Au moindre doute : rien.
        let ownPosition = OwnPositionResolver(
            myUid: { session.currentUid },
            localPublished: { miffId in
                OwnPositionProbe.isPublished(state: local.load(), uid: session.currentUid, miffId: miffId)
            },
            server: FirestoreOwnPositionSource(myUid: { session.currentUid })
        )
        let loader = PhotoLoader(
            PhotoLoaderDependencies(
                source: FirestorePhotoSource(),
                cache: update.photoCache,
                isOnline: { network.isOnline() },
                ownPosition: ownPosition
            )
        )
        return FeedPhotos(reader: loader)
    }
}
