import Foundation

// CONFIDENTIALITÉ, pour ce fichier (ticket « Profil, répartition du temps », critères TS-2, TS-3, TS-5, TS-7, TS-8) :
// - ce qui est écrit : l'identifiant de la Miff, l'état, l'identifiant du lieu (lettres et chiffres, 20 au plus) et l'heure, rien d'autre.
//   Une valeur qui n'a pas cette forme (une clé de zone, qui porte des coordonnées ; un nom) est REFUSÉE à l'écriture et à la lecture ;
// - où : UN fichier dans le stockage privé de l'app (Application Support), EXCLU des sauvegardes (iCloud et ordinateur : un téléphone
//   restauré ne retrouve rien) et jamais envoyé nulle part ;
// - protection du fichier : « jusqu'à la première authentification » (comme la copie locale du suivi) : écrit téléphone VERROUILLÉ et app
//   fermée, dès que l'appareil a été déverrouillé une fois après son démarrage. Avant cela, le fait est GARDÉ EN MÉMOIRE et écrit dès que le
//   stockage redevient lisible (un événement n'est pas perdu parce que le stockage est inaccessible) ;
// - durée : 35 jours glissants ; au plus `maxFacts` faits (le plus ancien part le premier) ;
// - effacement : déconnexion, suppression du compte, perte d'accès à une Miff (pour celle-ci), lieu supprimé (pour ce lieu), bouton de
//   Réglages, péremption.
// Jamais journalisé, jamais envoyé à Analytics ni à Crashlytics. Un fichier ABÎMÉ (illisible pour de bon) est réinitialisé : la carte passe
// à « pas assez de données ». Un fichier qui existe mais ne peut pas être lu pour l'instant (verrouillé) n'est JAMAIS effacé.

final class PresenceHistoryFileStore: PresenceHistory {
    /// Au plus 60 faits par jour et par appareil (disjoncteur du suivi) : 35 jours font quelques milliers de faits au pire. Garde-fou.
    static let maxFacts = 20_000
    private static let formatVersion = 1

    private struct StoredFact: Codable, Equatable {
        let miff: String
        let state: String
        let place: String?
        let at: Int
    }

    private struct StoredFile: Codable, Equatable {
        var version = PresenceHistoryFileStore.formatVersion
        var windowDays: Int?
        var facts: [StoredFact] = []
    }

    private enum Loaded {
        case file(StoredFile)
        case unreadable
    }

    private let directory: URL
    private let clock: () -> Int
    private let queue = DispatchQueue(label: "com.tomessa.miff.presence-history")
    /// Ce qui est sur le disque, gardé en mémoire (le moteur et l'écran sont dans le même processus : il n'y a qu'un écrivain).
    private var cache: StoredFile?
    /// Les faits reçus pendant que le stockage est illisible : écrits dès qu'il ne l'est plus.
    private var waiting: [PresenceFact] = []

    private var fileURL: URL {
        directory.appendingPathComponent("presence-history.json")
    }

    init(directory: URL? = nil, clock: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) }) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("TimeShare", isDirectory: true)
        }
        self.clock = clock
    }

    // MARK: - PresenceHistory

    func record(_ fact: PresenceFact) {
        guard Self.isAcceptable(fact) else {
            return
        }
        queue.sync {
            guard var file = loadFile(allowing: fact) else {
                return
            }
            if Self.append(fact, to: &file) {
                persist(&file)
            }
        }
    }

    func read(miffId: String) -> PresenceHistoryRead {
        queue.sync {
            guard var file = loadFile() else {
                return .unreadable
            }
            let floor = clock() - TimeShareLimits.retentionMillis
            file.facts.removeAll { $0.at < floor }
            return .facts(file.facts.filter { $0.miff == miffId }.compactMap(Self.fact))
        }
    }

    func windowDays() -> Int {
        queue.sync {
            TimeShareLimits.validWindow(loadFile()?.windowDays)
        }
    }

    func setWindowDays(_ days: Int) {
        guard TimeShareLimits.windowsDays.contains(days) else {
            return
        }
        change { $0.windowDays = days }
    }

    func forgetPlaces(miffId: String, keeping placeIds: Set<String>) {
        change { file in
            file.facts = Self.collapsed(
                file.facts.map { stored in
                    let named = Self.namesAPlace(stored)
                    guard stored.miff == miffId, named, !(stored.place.map { placeIds.contains($0) } ?? false) else {
                        return stored
                    }
                    return StoredFact(miff: miffId, state: PresenceState.unknown.rawValue, place: nil, at: stored.at)
                }
            )
        }
    }

    func purge(miffId: String) {
        change { file in
            file.facts.removeAll { $0.miff == miffId }
        }
        queue.sync {
            waiting.removeAll { $0.miffId == miffId }
        }
    }

    func retainOnly(miffIds: Set<String>) {
        change { file in
            file.facts.removeAll { !miffIds.contains($0.miff) }
        }
        queue.sync {
            waiting.removeAll { !miffIds.contains($0.miffId) }
        }
    }

    func eraseFacts() {
        change { $0.facts = [] }
        queue.sync {
            waiting = []
        }
    }

    func clearAll() {
        queue.sync {
            cache = nil
            waiting = []
            try? FileManager.default.removeItem(at: directory)
        }
    }

    // MARK: - Intérieur (toujours appelé dans `queue`, sauf `change`)

    /// Une modification du contenu : lue, changée, rognée à 35 jours, réécrite. Sans effet quand le stockage est illisible.
    private func change(_ edit: (inout StoredFile) -> Void) {
        queue.sync {
            guard var file = loadFile() else {
                return
            }
            let before = file
            edit(&file)
            if file != before {
                persist(&file)
            }
        }
    }

    /// Le fichier (ou un fichier vide), avec les faits en attente déjà rangés dedans ; nil quand le stockage est illisible pour l'instant.
    /// `allowing` : un fait à garder en attente dans ce cas.
    private func loadFile(allowing pending: PresenceFact? = nil) -> StoredFile? {
        switch load() {
        case .unreadable:
            if let pending, waiting.count < Self.maxFacts {
                waiting.append(pending)
            }
            return nil
        case .file(var file):
            if !waiting.isEmpty {
                let late = waiting
                waiting = []
                for fact in late {
                    _ = Self.append(fact, to: &file)
                }
                persist(&file)
            }
            return file
        }
    }

    private func load() -> Loaded {
        if let cache {
            return .file(cache)
        }
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            return .file(StoredFile())
        }
        guard let data = try? Data(contentsOf: fileURL) else {
            // Le fichier existe mais ne se lit pas (verrouillé avant le premier déverrouillage, erreur d'entrée-sortie) : jamais effacé.
            return .unreadable
        }
        let decoded = try? JSONDecoder().decode(StoredFile.self, from: data)
        guard var file = decoded, file.version == Self.formatVersion else {
            // Abîmé pour de bon (ou d'un format inconnu) : réinitialisé. La carte repart de « pas assez de données ».
            try? FileManager.default.removeItem(at: directory)
            return .file(StoredFile())
        }
        file.facts = file.facts.filter { Self.fact($0) != nil }
        cache = file
        return .file(file)
    }

    private func persist(_ file: inout StoredFile) {
        let floor = clock() - TimeShareLimits.retentionMillis
        file.facts.removeAll { $0.at < floor }
        if file.facts.count > Self.maxFacts {
            file.facts.removeFirst(file.facts.count - Self.maxFacts)
        }
        cache = file
        guard prepareDirectory(), let data = try? JSONEncoder().encode(file) else {
            return
        }
        try? data.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    private func prepareDirectory() -> Bool {
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            var folder = directory
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            try folder.setResourceValues(values)
            return true
        } catch {
            return false
        }
    }

    // MARK: - Règles pures

    /// Un fait n'est écrit que s'il a la forme attendue : jamais une clé de zone (coordonnées), un nom ou un texte libre.
    static func isAcceptable(_ fact: PresenceFact) -> Bool {
        guard isMiffId(fact.miffId), fact.atMillis > 0 else {
            return false
        }
        if let placeId = fact.placeId {
            return Place.isPlaceId(placeId)
        }
        return true
    }

    /// L'identifiant d'une Miff : l'identifiant d'un document Firestore (lettres, chiffres, tiret et souligné, 40 au plus).
    static func isMiffId(_ value: String) -> Bool {
        let scalars = Array(value.unicodeScalars)
        return !scalars.isEmpty && scalars.count <= 40 && scalars.allSatisfy { scalar in
            (scalar.value >= 0x30 && scalar.value <= 0x39)
                || (scalar.value >= 0x41 && scalar.value <= 0x5A)
                || (scalar.value >= 0x61 && scalar.value <= 0x7A)
                || scalar.value == 0x2D || scalar.value == 0x5F
        }
    }

    /// Ajoute un fait si c'est un CHANGEMENT : identique au dernier de la même Miff (même état, même lieu), il n'est pas gardé ; une première
    /// présence « inconnue » non plus (rien n'a été mesuré, c'est déjà ce que dit l'absence de fait).
    private static func append(_ fact: PresenceFact, to file: inout StoredFile) -> Bool {
        let last = file.facts.last { $0.miff == fact.miffId }
        if let last {
            if last.state == fact.state.rawValue && last.place == fact.placeId {
                return false
            }
        } else if fact.state == .unknown {
            return false
        }
        file.facts.append(
            StoredFact(miff: fact.miffId, state: fact.state.rawValue, place: fact.placeId, at: fact.atMillis)
        )
        return true
    }

    /// Un fait « dans un lieu » (de confiance ou de danger), c'est-à-dire qui nomme un identifiant de lieu.
    private static func namesAPlace(_ stored: StoredFact) -> Bool {
        stored.state == PresenceState.trusted.rawValue || stored.state == PresenceState.danger.rawValue
    }

    /// Les faits sans les répétitions : un fait identique au précédent de la même Miff (même état, même lieu) n'est pas gardé, ni une
    /// première présence « inconnue ». Sert après qu'un lieu supprimé a été remplacé par « inconnue ».
    private static func collapsed(_ facts: [StoredFact]) -> [StoredFact] {
        var lastByMiff: [String: StoredFact] = [:]
        var kept: [StoredFact] = []
        for stored in facts {
            if let last = lastByMiff[stored.miff] {
                if last.state == stored.state && last.place == stored.place {
                    continue
                }
            } else if stored.state == PresenceState.unknown.rawValue {
                continue
            }
            lastByMiff[stored.miff] = stored
            kept.append(stored)
        }
        return kept
    }

    /// Le fait d'une ligne du fichier, ou nil si elle n'a pas la forme attendue (une ligne abîmée est ignorée).
    private static func fact(_ stored: StoredFact) -> PresenceFact? {
        guard let state = PresenceState(rawValue: stored.state) else {
            return nil
        }
        let fact = PresenceFact(miffId: stored.miff, state: state, placeId: stored.place, atMillis: stored.at)
        return isAcceptable(fact) ? fact : nil
    }
}
