import Foundation

extension AppEnvironment {
    /// Les photos du Feed (FEED-8), construites par l'onglet Feed quand il est créé (rien n'est lu avant qu'une carte vienne à l'écran). La mémoire
    /// des photos est celle de l'Update (`update.photoCache`) : ma photo publiée y est déjà, ma carte ne la relit pas. Le Feed ne fait que LIRE.
    func makeFeedPhotos() -> FeedPhotos {
        let session = self.session
        let network = self.network
        let local = TrackingLocalFileStore()
        let loader = PhotoLoader(
            PhotoLoaderDependencies(
                source: FirestorePhotoSource(),
                cache: update.photoCache,
                isOnline: { network.isOnline() },
                // Ce que veut dire un refus : ai-je publié MA position dans cette Miff ? (la copie locale du suivi, lue seulement après un refus)
                ownPositionPublished: { miffId in
                    OwnPositionProbe.isPublished(state: local.load(), uid: session.currentUid, miffId: miffId)
                }
            )
        )
        return FeedPhotos(reader: loader)
    }
}
