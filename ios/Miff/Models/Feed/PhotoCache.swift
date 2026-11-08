import Foundation

// CONFIDENTIALITÉ et COÛT, pour ce fichier (FEED-8) : les photos lues, EN MÉMOIRE seulement (jamais sur le disque : aucune écriture de fichier,
// aucune photothèque), pour la session ; vidées à la déconnexion. Au plus `maxEntries` photos (150 Ko au plus chacune : ~2,4 Mo). Une photo déjà
// lue n'est PAS relue tant que le statut n'a pas changé (`lastUpdateAt`) ; une absence ou un refus non plus (3 lectures de règles chacun). La
// photo que je publie moi-même y est posée au moment de la publication : ma carte la montre sans AUCUNE lecture du serveur.
// Miroir de `PhotoCache` d'Android.

@MainActor
final class PhotoCache {
    private struct Entry {
        let photo: SharedPhoto
        /// La `lastUpdateAt` du statut pour lequel cette copie a été lue ; nil pour la photo de l'auteur, valable jusqu'à son expiration.
        let forLastUpdateMillis: Int?
    }

    static let defaultMaxEntries = 16
    private static let missingFactor = 4

    private let maxEntries: Int
    private var entries: [String: Entry] = [:]
    private var entryOrder: [String] = []
    private var missing: [String: Int] = [:]
    private var missingOrder: [String] = []
    private var refused: [String: Int] = [:]
    private var refusedOrder: [String] = []

    init(maxEntries: Int = PhotoCache.defaultMaxEntries) {
        self.maxEntries = max(1, maxEntries)
    }

    /// Le nombre de photos gardées (les tests).
    var count: Int {
        entries.count
    }

    /// La copie à montrer pour ce statut, ou nil. Une copie expirée est oubliée.
    func photo(miffId: String, uid: String, lastUpdateAtMillis: Int?, nowMillis: Int) -> SharedPhoto? {
        let key = Self.key(miffId, uid)
        guard let entry = entries[key] else {
            return nil
        }
        if entry.photo.isExpired(nowMillis: nowMillis) {
            entries[key] = nil
            entryOrder.removeAll { $0 == key }
            return nil
        }
        guard entry.forLastUpdateMillis == nil || entry.forLastUpdateMillis == lastUpdateAtMillis else {
            return nil
        }
        touch(key, in: &entryOrder)
        return entry.photo
    }

    func putRead(miffId: String, uid: String, photo: SharedPhoto, lastUpdateAtMillis: Int) {
        store(miffId, uid, Entry(photo: photo, forLastUpdateMillis: lastUpdateAtMillis))
    }

    /// La photo que je viens de publier : montrée sur ma carte sans lecture, jusqu'à son expiration.
    func putOwn(miffId: String, uid: String, photo: SharedPhoto) {
        store(miffId, uid, Entry(photo: photo, forLastUpdateMillis: nil))
    }

    /// Le serveur a dit qu'il n'y a rien à montrer pour ce statut : pas redemandé pour la même `lastUpdateAt`.
    func putMissing(miffId: String, uid: String, lastUpdateAtMillis: Int) {
        let key = Self.key(miffId, uid)
        missing[key] = lastUpdateAtMillis
        touch(key, in: &missingOrder)
        trim(&missing, &missingOrder, limit: maxEntries * Self.missingFactor)
    }

    func isMissing(miffId: String, uid: String, lastUpdateAtMillis: Int) -> Bool {
        missing[Self.key(miffId, uid)] == lastUpdateAtMillis
    }

    /// Les règles ont refusé pour ce statut : pas redemandé tant que ma situation ne change pas (le refus coûte des lectures de règles).
    func putRefused(miffId: String, uid: String, lastUpdateAtMillis: Int) {
        let key = Self.key(miffId, uid)
        refused[key] = lastUpdateAtMillis
        touch(key, in: &refusedOrder)
        trim(&refused, &refusedOrder, limit: maxEntries * Self.missingFactor)
    }

    func isRefused(miffId: String, uid: String, lastUpdateAtMillis: Int) -> Bool {
        refused[Self.key(miffId, uid)] == lastUpdateAtMillis
    }

    /// La photo que le publieur vient d'envoyer, dans chaque Miff où elle est partie.
    func putOwn(_ published: PublishedPhoto) {
        for miffId in published.miffIds {
            putOwn(miffId: miffId, uid: published.uid, photo: published.photo)
        }
    }

    func remove(miffId: String, uid: String) {
        let key = Self.key(miffId, uid)
        entries[key] = nil
        missing[key] = nil
        refused[key] = nil
        entryOrder.removeAll { $0 == key }
        missingOrder.removeAll { $0 == key }
        refusedOrder.removeAll { $0 == key }
    }

    /// Fin de session : plus rien des photos ne reste en mémoire pour la personne suivante.
    func clear() {
        entries = [:]
        missing = [:]
        refused = [:]
        entryOrder = []
        missingOrder = []
        refusedOrder = []
    }

    private func store(_ miffId: String, _ uid: String, _ entry: Entry) {
        let key = Self.key(miffId, uid)
        missing[key] = nil
        refused[key] = nil
        entries[key] = entry
        touch(key, in: &entryOrder)
        trim(&entries, &entryOrder, limit: maxEntries)
    }

    private func touch(_ key: String, in order: inout [String]) {
        order.removeAll { $0 == key }
        order.append(key)
    }

    private func trim<Value>(_ values: inout [String: Value], _ order: inout [String], limit: Int) {
        while order.count > limit {
            values[order.removeFirst()] = nil
        }
    }

    private static func key(_ miffId: String, _ uid: String) -> String {
        "\(miffId)/\(uid)"
    }
}
