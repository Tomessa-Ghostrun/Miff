import Foundation

/// Ce que l'une des trois écoutes d'une Miff (la Miff, ses membres, ses lieux) a dit en dernier.
struct ObservedPiece<Value> {
    var value: Value?
    var fromCache: Bool
    var lost: Bool
    var failed: Bool

    init(value: Value? = nil, fromCache: Bool = false, lost: Bool = false, failed: Bool = false) {
        self.value = value
        self.fromCache = fromCache
        self.lost = lost
        self.failed = failed
    }
}

/// Met ce que les trois écoutes ont dit en UNE réponse pour l'écran. Une perte sur la Miff ou sur ses membres l'emporte sur
/// tout ; un échec qui n'est pas une perte vient ensuite ; les lieux sont secondaires (une liste illisible est une liste
/// vide) ; tant que la Miff et les membres ne sont pas connus, la réponse est « chargement ».
func assembleObservation(
    miff: ObservedPiece<MiffDetails>?,
    members: ObservedPiece<[MiffMember]>?,
    places: ObservedPiece<[MiffPlace]>?
) -> MiffObservation {
    if miff?.lost == true || members?.lost == true {
        return .accessLost
    }
    if miff?.failed == true || members?.failed == true {
        return .failed
    }
    if let details = miff?.value, let people = members?.value, let placePiece = places {
        return .data(
            MiffSnapshot(
                miff: details,
                members: people,
                places: placePiece.value ?? [],
                fromCache: (miff?.fromCache ?? false) || (members?.fromCache ?? false)
            )
        )
    }
    return .loading
}
