import Foundation

// CONFIDENTIALITÉ et COÛT, pour ce fichier (les lieux de la Miff choisie dans le carrousel de l'onglet Profil, décision du PM du
// 2026-11-11) :
// - les noms et adresses des lieux restent en mémoire, pour la session, jamais dans un journal ni un événement Analytics ; chaque
//   type qui les porte cache son contenu dans `description` ;
// - COÛT : les lieux d'une Miff coûtent UNE lecture de `miffs/{id}/places` (une lecture Firestore par lieu). `MiffPlacesBook` décide
//   QUAND une lecture est nécessaire : jamais pour la Miff que le Feed a déjà lue, au plus une fois par Miff et par 5 minutes pour les
//   autres, jamais en boucle après un échec. Aucun écouteur n'est attaché pour cette liste. Même principe qu'Android (`MiffPlacesBook`).

/// Ce que la liste des lieux d'UNE Miff sait.
enum MiffPlacesView: Equatable, CustomStringConvertible {
    /// Rien n'est connu : pas de liste, pas de phrase « aucun lieu » (jamais une absence inventée).
    case loading
    /// Les lieux n'ont pas pu être lus (hors ligne, lenteur, refus) : la liste le dit calmement et réessaiera plus tard.
    case unavailable
    /// Les lieux de la Miff, dans l'ordre de l'écran de la Miff. Peut être vide : c'est alors un vrai « aucun lieu ».
    case ready([Place])

    var description: String {
        "MiffPlacesView(redacted)"
    }
}

/// Qui sait quoi des lieux de chaque Miff, et quand une lecture est nécessaire. Du Swift pur, sans autre horloge que celle qu'on lui
/// donne : les règles du coût vivent ici et sont testées ici. Utilisé depuis le fil principal par `ProfilePlacesViewModel`.
struct MiffPlacesBook {
    /// Ce qui a été lu est réutilisé aussi longtemps (en millisecondes) : rouvrir le Profil, ou revenir sur une carte, ne relit rien.
    static let freshMillis = 5 * 60 * 1000
    /// Après un échec, le prochain essai attend au moins ce délai (jamais de boucle ; la liste ne montre rien d'alarmant entre-temps).
    static let retryMillis = 30 * 1000

    private enum Source {
        case feed
        case read
        case failed
    }

    private struct Entry {
        var view: MiffPlacesView
        var source: Source
        var atMillis: Int
        var stale = false
        var reading = false
        /// La `generation` au départ de la lecture en cours : une lecture qui finit après une invalidation n'efface pas la marque.
        var startedAt = 0
    }

    private var entries: [String: Entry] = [:]
    /// Monte à chaque invalidation : une lecture commencée AVANT sait que ce qu'elle apporte peut déjà être périmé.
    private var generation = 0
    private let clock: () -> Int

    init(clock: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) }) {
        self.clock = clock
    }

    /// Ce que la liste de cette Miff montre maintenant.
    func view(_ miffId: String) -> MiffPlacesView {
        entries[miffId]?.view ?? .loading
    }

    /// Le Feed a lu ces lieux : la liste de cette Miff les dessine, SANS lecture. La marque « périmé » s'efface (ce que le Feed vient
    /// d'entendre est au moins aussi récent que tout ce que l'on lirait maintenant).
    ///
    /// LIMITE CONNUE, acceptée pour le coût : une fois le Feed quitté, rien ne met cette copie à jour ; la liste montre les lieux tels
    /// qu'ils étaient à la dernière visite du Feed. Le créateur, seul à changer les lieux, est couvert par `invalidateAll` (une lecture à
    /// la prochaine apparition de la liste).
    mutating func seedFromFeed(miffId: String, places: [Place]) {
        let previous = entries[miffId]
        entries[miffId] = Entry(
            view: .ready(places),
            source: .feed,
            atMillis: clock(),
            reading: previous?.reading ?? false,
            startedAt: previous?.startedAt ?? generation
        )
    }

    /// La liste de cette Miff, qui vient d'apparaître, demande-t-elle une lecture du serveur ? Oui quand rien n'est connu, quand un lieu
    /// a été touché depuis (`invalidateAll`), quand la lecture date de plus de 5 minutes, ou quand le dernier essai a échoué il y a plus
    /// de 30 secondes. Non pendant une lecture, et non pour ce que le Feed a donné (à la dernière visite du Feed, jamais vieilli).
    func needsReading(_ miffId: String) -> Bool {
        guard let entry = entries[miffId] else {
            return true
        }
        if entry.reading {
            return false
        }
        if entry.stale {
            return true
        }
        let age = clock() - entry.atMillis
        switch entry.source {
        case .feed:
            return false
        case .failed:
            return age >= Self.retryMillis
        case .read:
            return age >= Self.freshMillis
        }
    }

    /// Une lecture commence : la liste garde ce qu'elle montrait (ou rien), et aucune seconde lecture ne démarre pour elle.
    mutating func readingStarted(_ miffId: String) {
        if var entry = entries[miffId] {
            entry.reading = true
            entry.startedAt = generation
            entries[miffId] = entry
        } else {
            entries[miffId] = Entry(
                view: .loading, source: .read, atMillis: clock(), reading: true, startedAt: generation
            )
        }
    }

    mutating func readingSucceeded(_ miffId: String, places: [Place]) {
        entries[miffId] = Entry(
            view: .ready(places),
            source: .read,
            atMillis: clock(),
            stale: startedBeforeInvalidation(miffId)
        )
    }

    /// Ce qui était dessiné reste dessiné (une liste qui était là ne disparaît pas parce que le réseau a cligné) ; seule une liste
    /// sans rien devient « indisponible ».
    mutating func readingFailed(_ miffId: String) {
        let previous = entries[miffId]?.view
        var keep = MiffPlacesView.unavailable
        if let previous, case .ready = previous {
            keep = previous
        }
        entries[miffId] = Entry(
            view: keep,
            source: .failed,
            atMillis: clock(),
            stale: startedBeforeInvalidation(miffId)
        )
    }

    /// La lecture de cette Miff a commencé avant la dernière invalidation : ce qu'elle apporte peut dater d'avant un changement, donc
    /// la prochaine apparition relit.
    private func startedBeforeInvalidation(_ miffId: String) -> Bool {
        (entries[miffId]?.startedAt ?? generation) != generation
    }

    /// Le créateur ouvre un lieu ou le formulaire d'un nouveau : un lieu peut être ajouté, changé ou supprimé (dans cette Miff ou dans
    /// une autre à laquelle le même lieu appartient), donc la prochaine apparition de chaque liste relit. Ne coûte rien maintenant.
    mutating func invalidateAll() {
        generation += 1
        for miffId in Array(entries.keys) {
            entries[miffId]?.stale = true
        }
    }

    /// « Réessayer » : la prochaine apparition lit tout de suite (sans attendre les 30 secondes).
    mutating func invalidate(_ miffId: String) {
        guard entries[miffId] != nil else {
            return
        }
        generation += 1
        entries[miffId]?.stale = true
    }

    /// Une Miff qui n'est plus dans la liste (quittée, supprimée, accès perdu) emporte ses lieux avec elle.
    mutating func retainOnly(_ miffIds: Set<String>) {
        for miffId in Array(entries.keys) where !miffIds.contains(miffId) {
            entries[miffId] = nil
        }
    }
}

/// Ce que l'onglet Profil dessine dépend du rôle de la personne dans la Miff ; ce qui est PERMIS, ce sont les règles de Firestore qui le
/// décident (`allow create, update, delete: if isOwner(miffId)`), pas ceci.
enum PlaceViewerRole {
    /// Le créateur de la Miff définit ses lieux (ajoute, change, supprime) ; tout autre membre lit. Un créateur inconnu, ou une
    /// personne inconnue, n'est JAMAIS créateur : les boutons du créateur ne s'affichent pas dans le doute.
    static func isCreator(ownerUid: String?, myUid: String?) -> Bool {
        guard let ownerUid, !ownerUid.isEmpty, let myUid else {
            return false
        }
        return ownerUid == myUid
    }
}
