import Foundation

/// Qui a quitté la Miff depuis la dernière image que l'app du créateur avait (critère N3 : « Un proche a quitté la Miff. Tu
/// veux créer un nouveau code ? »). Seul compte ce que l'app a VU disparaître : sans image précédente (première ouverture sur
/// un téléphone, cache purgé) il n'y a rien à comparer, donc aucune invitation pour des départs anciens (ticket §10, cas 22).
/// La fiche du créateur lui-même, et les personnes que le créateur vient de retirer (le code a été renouvelé par ce lot même),
/// ne comptent jamais.
enum MemberDepartures {
    static func between(
        previous: [MiffMember]?,
        current: [MiffMember],
        myUid: String,
        removedByMe: Set<String>
    ) -> Set<String> {
        guard let previous else {
            return []
        }
        let still = Set(current.map { $0.uid })
        let gone = previous.map { $0.uid }.filter { uid in
            uid != myUid && !still.contains(uid) && !removedByMe.contains(uid)
        }
        return Set(gone)
    }
}
