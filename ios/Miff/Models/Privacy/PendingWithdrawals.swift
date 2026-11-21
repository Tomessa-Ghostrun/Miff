import Combine
import CryptoKit
import Foundation

/// Où sont gardés, sur l'appareil, les retraits en attente : des paires `clé -> heure`. Aucune donnée personnelle (voir
/// `PendingWithdrawals`).
protocol WithdrawalStorage {
    func read() -> [String: Int]

    /// Écrit sur le disque AVANT de rendre la main : la marque doit survivre à une app tuée juste après le toucher. Faux si le
    /// disque refuse.
    func write(_ entries: [String: Int]) -> Bool
}

/// Les retraits du consentement « statistiques et rapports de plantage » que le COMPTE ne connaît pas encore (la personne a coupé sans
/// réseau). Gardés sur l'appareil pour que son choix survive à un redémarrage : tant qu'un retrait est en attente, la collecte reste
/// ÉTEINTE à chaque démarrage, quoi que dise encore le compte.
///
/// On ne garde que `clé + heure`, jamais un nom, un e-mail, une position ni l'identifiant du compte lui-même : la clé est une
/// empreinte à sens unique de cet identifiant (coupée à 32 caractères), assez pour suivre le bon compte et n'appliquer jamais un
/// retrait à un autre. Le fichier est privé à l'app et EXCLU des sauvegardes (voir `WithdrawalFileStorage`). 20 comptes au plus sont
/// retenus (le plus ancien est oublié). Même rôle et mêmes règles qu'Android (`PendingWithdrawals`).
@MainActor
final class PendingWithdrawals: ObservableObject {
    static let maxEntries = 20

    /// Les clés des comptes dont un retrait attend. Publié : l'écran Confidentialité le suit (le message disparaît tout seul quand
    /// le retrait est enregistré en arrière-plan).
    @Published private(set) var keys: Set<String>

    private var entries: [String: Int]
    private let storage: WithdrawalStorage
    private let nowMillis: () -> Int

    init(storage: WithdrawalStorage, nowMillis: @escaping () -> Int) {
        self.storage = storage
        self.nowMillis = nowMillis
        let stored = storage.read()
        entries = stored
        keys = Set(stored.keys)
    }

    /// Un retrait attend-il CE compte ?
    func isPending(uid: String) -> Bool {
        keys.contains(Self.keyOf(uid))
    }

    /// Marque le retrait de ce compte (sur le disque d'abord, puis en mémoire).
    func mark(uid: String) {
        let key = Self.keyOf(uid)
        guard entries[key] == nil else {
            return
        }
        var next = entries
        next[key] = nowMillis()
        if next.count > Self.maxEntries {
            let kept = next.sorted { $0.value < $1.value }.suffix(Self.maxEntries)
            next = Dictionary(uniqueKeysWithValues: kept.map { ($0.key, $0.value) })
        }
        commit(next)
    }

    /// Le compte connaît maintenant son retrait (ou la personne a accepté de nouveau) : la marque part.
    func clear(uid: String) {
        let key = Self.keyOf(uid)
        guard entries[key] != nil else {
            return
        }
        var next = entries
        next[key] = nil
        commit(next)
    }

    /// Faux sur un disque qui refuse : la marque reste alors EN MÉMOIRE pour cette exécution (la collecte reste coupée tant que l'app
    /// vit), mais ne survivra pas à un redémarrage. Limite connue.
    private func commit(_ next: [String: Int]) {
        _ = storage.write(next)
        entries = next
        keys = Set(next.keys)
    }

    /// Empreinte à sens unique de l'identifiant du compte (SHA-256, 32 premiers caractères hexadécimaux).
    nonisolated static func keyOf(_ uid: String) -> String {
        let digest = SHA256.hash(data: Data(uid.utf8))
        return String(digest.map { String(format: "%02x", $0) }.joined().prefix(32))
    }
}
