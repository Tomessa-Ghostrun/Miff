import Foundation
#if canImport(Combine)
import Combine
#endif

// CNC-I (ticket docs/tickets/centre-notifications-complet.md §5.1, §6 et §8.5) : les VISAGES que le centre de notifications dessine dans ses lignes. Un
// visage est la photo de profil d'un membre (celle que les autres membres de sa Miff voient déjà), ou ses initiales, et son prénom : ils viennent de la
// MÊME fiche, donc un prénom et une photo ne se contredisent jamais.
//
// CONFIDENTIALITÉ : EN MÉMOIRE SEULEMENT, pour la session (cet objet fait partie de la fin de session) : jamais écrit dans un fichier (le centre
// lui-même ne garde aucune photo), jamais journalisé, jamais dans un événement. On ne garde que ce qu'une ligne dessine : le prénom, les initiales, la
// photo de 20 000 octets au plus.
// COÛT : 0 lecture pour la Miff que le Feed a déjà lue (ses membres sont donnés par `seedFromFeed`) et pour soi ; sinon UNE lecture de
// `miffs/{miffId}/members/{uid}` par membre et par session (relue après 5 minutes au plus), une douzaine au plus à chaque ouverture du centre, et
// JAMAIS en boucle après un échec (une fiche en échec n'est pas redemandée avant 30 secondes, et seulement à une nouvelle ouverture du centre).
// Aucun écouteur, jamais.

/// Ce qu'une lecture de la fiche d'un membre a donné.
enum FaceRead: Equatable {
    case found(ActorFace)
    /// La fiche n'existe pas (le membre est parti, a été retiré, a supprimé son compte), ou les règles la refusent (la Miff n'est plus celle de la personne).
    case gone
    /// Hors ligne, trop lent, illisible : introuvable pour l'instant.
    case failed
}

/// La lecture unique derrière un visage (un double dans les tests).
protocol MemberFaceReading {
    func read(miffId: String, uid: String) async -> FaceRead
}

/// Un membre d'une Miff : de quoi retrouver son visage.
struct FaceKey: Hashable, CustomStringConvertible {
    let miffId: String
    let uid: String

    var description: String {
        "FaceKey(redacted)"
    }
}

/// Les visages connus du centre, par Miff et par membre, en mémoire pour la session. `lookup` ne lit jamais : il répond depuis la mémoire ; `ensure` lit
/// ce qui manque ou est trop vieux (une douzaine au plus par appel) par `reader`. Le Feed donne les membres de la Miff qu'il montre (`seedFromFeed`), ce
/// qui dit aussi qui n'est PAS dans cette Miff.
@MainActor
final class CenterFaces: ObservableObject, CustomStringConvertible {
    /// Ce qui a été lu est réutilisé aussi longtemps (comme les visages du carrousel du Profil).
    static let ttlMillis = 5 * 60 * 1000
    /// Après un échec, la tentative suivante attend au moins ce délai, et seulement à une nouvelle ouverture du centre.
    static let retryMillis = 30 * 1000
    /// « Une douzaine au plus » à chaque ouverture du centre (ticket §8.5, critère CNC-19).
    static let maxReadsPerCall = 12

    private enum Source {
        case feed
        case read
        case failed
    }

    private struct Entry {
        let result: FaceLookup
        let source: Source
        let atMillis: Int
    }

    /// Change quand quelque chose est appris : l'écran qui dessine les visages se redessine. Ne contient qu'un numéro.
    @Published private(set) var version = 0
    /// MON visage tel que mon profil le dit (nil tant que mon profil n'est pas lu) : l'image de mes propres rappels, sans aucune lecture de plus.
    @Published private(set) var mine: ActorFace?

    private let reader: MemberFaceReading
    private let nowMillis: () -> Int
    private var entries: [FaceKey: Entry] = [:]
    /// Les membres de chaque Miff que le Feed a lue, complets : quelqu'un qui n'est pas dans la liste d'une Miff lue par le Feed n'en fait plus partie.
    private var rosters: [String: Set<String>] = [:]
    private var inFlight: Set<FaceKey> = []
    /// Monte à chaque fin de session : une réponse attendue à travers elle appartient à la personne d'avant.
    private var epoch = 0

    nonisolated var description: String {
        "CenterFaces(redacted)"
    }

    init(reader: MemberFaceReading, nowMillis: @escaping () -> Int = NotificationClock.wallMillis) {
        self.reader = reader
        self.nowMillis = nowMillis
    }

    /// Ce que la mémoire sait de `uid` dans `miffId`, maintenant. Ne lit jamais.
    func lookup(miffId: String, uid: String) -> FaceLookup {
        if let entry = entries[FaceKey(miffId: miffId, uid: uid)] {
            return entry.result
        }
        // Une Miff que le Feed a lue en entier : quelqu'un qui n'y figure pas n'en est pas (ou plus) membre.
        if let roster = rosters[miffId], !roster.contains(uid) {
            return .gone
        }
        return .pending
    }

    /// Le Feed a lu les membres de `miffId` : leurs visages sont connus, GRATUITEMENT (aucune lecture). Ceux de la dernière visite du Feed (même limite
    /// acceptée que le carrousel du Profil : quelqu'un parti entre-temps s'affiche jusqu'à la prochaine écoute du Feed).
    func seedFromFeed(miffId: String, members: [MiffMember]) {
        let now = nowMillis()
        var seeded: [FaceKey: Entry] = [:]
        for member in members {
            seeded[FaceKey(miffId: miffId, uid: member.uid)] = Entry(
                result: .known(Self.face(of: member)), source: .feed, atMillis: now
            )
        }
        var others = entries.filter { $0.key.miffId != miffId }
        others.merge(seeded) { _, new in new }
        entries = others
        rosters[miffId] = Set(members.map { $0.uid })
        version += 1
    }

    /// MON profil est lu (ou vient de changer) : mon visage suit, sans lecture.
    func setMine(initials: String, photo: Data?) {
        let face = ActorFace(firstName: "", initials: initials, photo: Self.usable(photo))
        if mine != face {
            mine = face
        }
    }

    /// Lit les visages de `wanted` que la mémoire n'a pas ou qui sont trop vieux, `maxReadsPerCall` au plus (le reste attend la prochaine ouverture). Un
    /// à la fois : aucune rafale. Rend la main quand tout ce qui a été demandé est réglé. Ne lève jamais.
    func ensure(_ wanted: [FaceKey]) async {
        let startedIn = epoch
        var todo: [FaceKey] = []
        for key in wanted where !todo.contains(key) && needsReading(key) {
            todo.append(key)
            if todo.count == Self.maxReadsPerCall {
                break
            }
        }
        for key in todo {
            guard epoch == startedIn, !Task.isCancelled else {
                return
            }
            // Deux appels presque en même temps ne lisent pas deux fois la même fiche.
            guard !inFlight.contains(key), needsReading(key) else {
                continue
            }
            inFlight.insert(key)
            let read = await reader.read(miffId: key.miffId, uid: key.uid)
            guard epoch == startedIn else {
                return
            }
            inFlight.remove(key)
            record(key, read)
        }
    }

    /// Une Miff qui n'est plus la sienne (quittée, supprimée, accès perdu) emporte ses visages.
    func retainOnly(miffIds: Set<String>) {
        let kept = entries.filter { miffIds.contains($0.key.miffId) }
        let keptRosters = rosters.filter { miffIds.contains($0.key) }
        if kept.count != entries.count || keptRosters.count != rosters.count {
            entries = kept
            rosters = keptRosters
            version += 1
        }
    }

    /// Fin de session : plus aucun visage, plus aucune lecture attendue.
    func stop() {
        epoch += 1
        entries = [:]
        rosters = [:]
        inFlight = []
        mine = nil
        version += 1
    }

    private func needsReading(_ key: FaceKey) -> Bool {
        guard let entry = entries[key] else {
            // Quelqu'un qui n'est pas dans une Miff que le Feed a lue n'a pas besoin d'être lu : il n'en fait plus partie.
            if let roster = rosters[key.miffId] {
                return roster.contains(key.uid)
            }
            return true
        }
        let age = nowMillis() - entry.atMillis
        switch entry.source {
        case .feed:
            return false
        case .read:
            return age >= Self.ttlMillis
        case .failed:
            return age >= Self.retryMillis
        }
    }

    private func record(_ key: FaceKey, _ read: FaceRead) {
        let now = nowMillis()
        switch read {
        case .found(let face):
            entries[key] = Entry(result: .known(face), source: .read, atMillis: now)
        case .gone:
            entries[key] = Entry(result: .gone, source: .read, atMillis: now)
        case .failed:
            // Ce qui était dessiné reste dessiné quand une seconde lecture échoue (une photo ne disparaît pas parce que le réseau a cligné).
            if let previous = entries[key], case .known = previous.result {
                entries[key] = Entry(result: previous.result, source: .failed, atMillis: now)
            } else {
                entries[key] = Entry(result: .failed, source: .failed, atMillis: now)
            }
        }
        version += 1
    }

    /// Le visage d'une fiche de membre : le prénom (ou le nom de famille quand le prénom n'a rien de visible), les initiales, la photo si elle respecte
    /// la taille que les règles imposent.
    static func face(of member: MiffMember) -> ActorFace {
        ActorFace(
            firstName: member.callName,
            initials: Initials.of(first: member.firstName, last: member.lastName),
            photo: usable(member.avatarJpeg)
        )
    }

    private static func usable(_ photo: Data?) -> Data? {
        guard let photo, !photo.isEmpty, photo.count <= ProfileDocument.maxAvatarBytes else {
            return nil
        }
        return photo
    }
}
