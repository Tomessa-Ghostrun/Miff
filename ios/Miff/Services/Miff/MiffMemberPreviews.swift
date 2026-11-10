import Foundation
#if canImport(Combine)
import Combine
#endif

// COÛT FIRESTORE et CONFIDENTIALITÉ, pour ce fichier : les cartes du Profil montrent les photos des membres de CHAQUE Miff. Pour ne
// pas faire exploser les lectures (quota gratuit de 50 000 par jour pour tout le projet) :
//   - la Miff AFFICHÉE par le Feed n'est jamais relue : le Feed a déjà lu ses membres, il les donne ici (`record`, zéro lecture) ;
//   - les autres Miffs sont lues PARESSEUSEMENT, quand leur carte est à l'écran (ou qu'un glissement vers elle commence), une seule
//     fois par ouverture du Profil, et pas du tout si une lecture de moins de 5 minutes existe déjà ;
//   - UNE lecture ponctuelle (`getDocuments`) par Miff, jamais d'écouteur permanent : elle coûte autant de lectures que la Miff a de
//     membres (15 au plus, plafond doux) ;
//   - les photos sont déjà bornées à 20 000 octets (`ProfileDocument.maxAvatarBytes`) et on n'en garde que 5 par Miff.
// Les noms (initiales) et les photos restent en mémoire pour la durée de la session (cet objet est créé avec la session de l'hôte et
// disparaît avec elle) : jamais sur le disque, jamais journalisés, jamais dans un événement Analytics.

/// La lecture ponctuelle des membres d'UNE Miff, pour sa carte du Profil. nil quand elle n'a pas pu être faite (hors ligne sans cache,
/// refusée) : la carte le dit sans bruit, ce n'est jamais une « perte d'accès » (l'annuaire et le Feed s'en chargent).
protocol MemberPreviewReader {
    func read(miffId: String) async -> MemberPreview?
}

@MainActor
final class MiffMemberPreviews: ObservableObject {
    /// Ce qu'on sait déjà, par identifiant de Miff.
    @Published private(set) var known: [String: MemberPreview] = [:]
    /// Les Miffs dont la lecture a échoué pendant cette ouverture du Profil (pas de nouvel essai avant la prochaine ouverture).
    @Published private(set) var unavailable: Set<String> = []

    /// Combien de temps une lecture du Profil reste bonne (en millisecondes) : rouvrir le Profil dans ce délai ne relit rien.
    static let freshMillis = 5 * 60 * 1000

    private struct Stamp {
        let at: Int
        let fromFeed: Bool
    }

    private let reader: MemberPreviewReader
    private let clock: () -> Int
    private var stamps: [String: Stamp] = [:]
    private var readThisOpening: Set<String> = []

    init(reader: MemberPreviewReader, clock: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) }) {
        self.reader = reader
        self.clock = clock
    }

    /// Fin de session (l'hôte est recréé à la connexion suivante) : les photos réduites gardées en mémoire s'effacent aussi.
    deinit {
        AvatarDecoding.removeAll()
    }

    // MARK: - Ce que le Feed a déjà lu (zéro lecture)

    /// Le Feed a les membres de la Miff affichée : ils sont gardés pour la carte, sans rien relire.
    func record(miffId: String, members: [MiffMember]) {
        let preview = MemberPreview(members: members)
        if known[miffId] != preview {
            known[miffId] = preview
        }
        stamps[miffId] = Stamp(at: clock(), fromFeed: true)
        unavailable.remove(miffId)
    }

    // MARK: - Ce que la carte demande

    /// Le Profil s'ouvre : une nouvelle « ouverture » commence (chaque Miff peut être lue UNE fois, si elle n'est pas déjà fraîche).
    func beginOpening() {
        readThisOpening = []
        if !unavailable.isEmpty {
            unavailable = []
        }
    }

    func state(for miffId: String) -> MiffCardMembers {
        if let preview = known[miffId] {
            return .known(preview)
        }
        return unavailable.contains(miffId) ? .unavailable : .loading
    }

    /// Faut-il lire les membres de cette Miff maintenant ? Non si la Miff est affichée par le Feed et qu'il les a donnés (le Feed les
    /// tient à jour), non si une lecture de moins de 5 minutes existe, non si elle a déjà été lue (ou a échoué) pendant cette ouverture.
    func needsRead(miffId: String, isActive: Bool) -> Bool {
        if readThisOpening.contains(miffId) {
            return false
        }
        guard let stamp = stamps[miffId] else {
            return true
        }
        if stamp.fromFeed && isActive {
            return false
        }
        return clock() - stamp.at >= Self.freshMillis
    }

    /// La carte de cette Miff est à l'écran : lit ses membres si c'est nécessaire (voir `needsRead`).
    func loadIfNeeded(miffId: String, isActive: Bool) async {
        guard needsRead(miffId: miffId, isActive: isActive) else {
            return
        }
        // Marquée AVANT l'attente : deux demandes presque en même temps (la carte apparaît, un glissement commence) ne lisent qu'une fois.
        readThisOpening.insert(miffId)
        let preview = await reader.read(miffId: miffId)
        if let preview {
            known[miffId] = preview
            stamps[miffId] = Stamp(at: clock(), fromFeed: false)
            unavailable.remove(miffId)
        } else if known[miffId] == nil {
            unavailable.insert(miffId)
        }
    }

    /// Une Miff a disparu de l'annuaire (quittée, supprimée, accès perdu) : ses photos ne sont pas gardées.
    func retain(only miffIds: Set<String>) {
        for miffId in known.keys where !miffIds.contains(miffId) {
            known[miffId] = nil
            stamps[miffId] = nil
        }
        unavailable.formIntersection(miffIds)
    }
}
