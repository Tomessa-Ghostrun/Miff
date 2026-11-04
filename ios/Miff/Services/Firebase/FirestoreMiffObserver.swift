import FirebaseFirestore
import Foundation

// SÉCURITÉ, pour ce fichier : noms, photos et prénoms des membres sont des données personnelles. Ils vivent en mémoire dans
// ce que l'écoute publie, jamais dans un journal, un événement Analytics ni un rapport de plantage. Tout nom lu sur le réseau
// est nettoyé (`MiffName.clean`) avant d'être publié.

/// L'état des trois écoutes, assemblé en UNE image pour l'écran, de façon à ne jamais montrer le nom d'une Miff avec les
/// membres d'une autre. Les rappels de Firestore peuvent arriver de n'importe quel fil : un verrou protège l'état.
private final class ObservationBoard {
    private let lock = NSLock()
    private let publish: (MiffObservation) -> Void
    private var miff: ObservedPiece<MiffDetails>?
    private var members: ObservedPiece<[MiffMember]>?
    private var places: ObservedPiece<[MiffPlace]>?

    init(publish: @escaping (MiffObservation) -> Void) {
        self.publish = publish
    }

    func setMiff(_ piece: ObservedPiece<MiffDetails>) {
        update { self.miff = piece }
    }

    func setMembers(_ piece: ObservedPiece<[MiffMember]>) {
        update { self.members = piece }
    }

    func setPlaces(_ piece: ObservedPiece<[MiffPlace]>) {
        update { self.places = piece }
    }

    private func update(_ change: () -> Void) {
        lock.lock()
        change()
        let current = assembleObservation(miff: miff, members: members, places: places)
        lock.unlock()
        publish(current)
    }
}

/// Trois écoutes (la Miff, ses membres, ses lieux) mises en UNE image. Un refus des règles, ou une Miff qui n'existe pas, est
/// `accessLost` ; un problème réseau ne l'est jamais (les écoutes servent le cache et rattrapent leur retard).
final class FirestoreMiffObserver: MiffObserver {
    func observe(miffId: String) -> AsyncStream<MiffObservation> {
        AsyncStream { continuation in
            guard FirebaseBootstrap.isConfigured, MiffId.isValid(miffId) else {
                continuation.yield(.failed)
                continuation.finish()
                return
            }
            let board = ObservationBoard { continuation.yield($0) }
            let miffReference = Firestore.firestore().collection("miffs").document(miffId)
            let miffListener = miffReference.addSnapshotListener(includeMetadataChanges: true) { snapshot, error in
                board.setMiff(FirestoreMiffObserver.miffPiece(snapshot: snapshot, error: error))
            }
            let membersListener = miffReference.collection("members")
                .addSnapshotListener(includeMetadataChanges: true) { snapshot, error in
                    board.setMembers(FirestoreMiffObserver.membersPiece(snapshot: snapshot, error: error))
                }
            let placesListener = miffReference.collection("places").addSnapshotListener { snapshot, error in
                board.setPlaces(FirestoreMiffObserver.placesPiece(snapshot: snapshot, error: error))
            }
            continuation.onTermination = { _ in
                miffListener.remove()
                membersListener.remove()
                placesListener.remove()
            }
        }
    }

    static func miffPiece(snapshot: DocumentSnapshot?, error: Error?) -> ObservedPiece<MiffDetails> {
        if let error {
            let lost = isAccessLostError(error)
            return ObservedPiece(lost: lost, failed: !lost)
        }
        guard let snapshot else {
            return ObservedPiece(failed: true)
        }
        // « Pas dans le cache » n'est pas « supprimée » : seul le SERVEUR peut dire qu'une Miff n'existe pas (le mode avion
        // n'est pas une perte d'accès).
        if !snapshot.exists && snapshot.metadata.isFromCache {
            return ObservedPiece()
        }
        // Une Miff qui n'existe pas (supprimée) est une perte d'accès, exactement comme un refus (ticket §5.7, message QM8).
        guard let details = FirestoreMiffStore.details(of: snapshot) else {
            return ObservedPiece(lost: true)
        }
        return ObservedPiece(value: details, fromCache: snapshot.metadata.isFromCache)
    }

    static func membersPiece(snapshot: QuerySnapshot?, error: Error?) -> ObservedPiece<[MiffMember]> {
        if let error {
            let lost = isAccessLostError(error)
            return ObservedPiece(lost: lost, failed: !lost)
        }
        guard let snapshot else {
            return ObservedPiece(failed: true)
        }
        return ObservedPiece(value: members(of: snapshot), fromCache: snapshot.metadata.isFromCache)
    }

    /// Les lieux sont secondaires : une liste qu'on ne peut pas lire est une liste vide.
    static func placesPiece(snapshot: QuerySnapshot?, error: Error?) -> ObservedPiece<[MiffPlace]> {
        guard error == nil, let snapshot else {
            return ObservedPiece(value: [])
        }
        let places = snapshot.documents.compactMap { document -> MiffPlace? in
            guard let name = document.data()["name"] as? String else {
                return nil
            }
            return MiffPlace.fromServer(id: document.documentID, rawName: name)
        }
        return ObservedPiece(value: places.sorted { $0.name.lowercased() < $1.name.lowercased() })
    }

    private struct DatedMember {
        let member: MiffMember
        let joinedAt: Date
    }

    /// Les membres : le créateur d'abord, puis par ordre d'arrivée (stable : une liste qui se réordonne bouge sous un doigt).
    /// Prénoms et noms viennent d'autres téléphones : nettoyés.
    private static func members(of snapshot: QuerySnapshot) -> [MiffMember] {
        let dated = snapshot.documents.compactMap { document -> DatedMember? in
            let data = document.data()
            guard let first = data["firstName"] as? String, let last = data["lastName"] as? String else {
                return nil
            }
            var avatar = data["avatar"] as? Data
            if let bytes = avatar, bytes.isEmpty || bytes.count > ProfileDocument.maxAvatarBytes {
                avatar = nil
            }
            let sharing = (data["sharing"] as? [String: Any])?["enabled"] as? Bool
            let member = MiffMember(
                uid: document.documentID,
                firstName: MiffName.clean(first),
                lastName: MiffName.clean(last),
                avatarJpeg: avatar,
                role: (data["role"] as? String) == "owner" ? .owner : .member,
                sharingEnabled: sharing == true
            )
            let joined = (data["joinedAt"] as? Timestamp)?.dateValue() ?? Date.distantFuture
            return DatedMember(member: member, joinedAt: joined)
        }
        let sorted = dated.sorted { left, right in
            if (left.member.role == .owner) != (right.member.role == .owner) {
                return left.member.role == .owner
            }
            if left.joinedAt != right.joinedAt {
                return left.joinedAt < right.joinedAt
            }
            return left.member.uid < right.member.uid
        }
        return sorted.map { $0.member }
    }
}
