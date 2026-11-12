import Foundation

// Demande d'ajout d'un lieu (ticket `docs/tickets/demande-ajout-lieu.md`, LIEU-REQ ; miroir d'Android `PlaceRequests.kt`) : un membre qui
// n'est PAS le créateur d'une Miff propose un lieu, le créateur l'accepte ou non. Rien ici n'est jamais un vrai lieu : c'est un document
// `miffs/{miffId}/placeRequests/{uid}_{1..3}` que seuls le créateur et la personne qui l'a fait peuvent lire.
//
// SÉCURITÉ, pour ce fichier : le nom, l'adresse et les coordonnées d'une demande sont aussi sensibles que ceux d'un lieu (ce peut être
// la maison de quelqu'un qui n'utilise pas Miff). Chaque type qui en porte un a une `description` qui ne montre rien ; rien n'est jamais
// journalisé ni envoyé dans un événement. Les écritures ci-dessous sont le contrat avec backend/firestore.rules (règles `placeRequests`
// et `meta/placeRequests`, DÉPLOYÉES le 2026-11-12 ; contrat client `docs/dev/demande-lieu-clients.md`) : une demande écrit CHAQUE clé
// (neuf, `category` comprise ; les règles refusent une clé manquante et une clé inconnue), la date est celle du serveur, `requestedBy`
// est l'identifiant de la personne.

/// Les chiffres du ticket (décisions D2 et D3), à UN seul endroit.
enum PlaceRequestLimits {
    /// Demandes en attente par membre et par Miff : trois emplacements FIXES `{uid}_1`, `{uid}_2`, `{uid}_3` (les règles ne savent pas
    /// compter, elles savent imposer un identifiant).
    static let perMember = 3

    /// Demandes en attente par Miff (le compteur `meta/placeRequests`, lisible du créateur seul).
    static let perMiff = 10

    /// Une demande sans réponse s'en va après 30 jours : masquée par les deux apps et supprimée par celle qui la rencontre (aucun
    /// serveur ne le fait : plan gratuit).
    static let expiryMillis = 30 * 24 * 60 * 60 * 1000

    static let slots = [1, 2, 3]

    /// Au plus ce nombre de suppressions dans un lot du créateur (les règles lisent 2 documents par écriture : budget de 20).
    static let creatorDeletesPerLot = 4

    static func idOf(uid: String, slot: Int) -> String {
        "\(uid)_\(slot)"
    }
}

/// Un document `miffs/{miffId}/placeRequests/{requestId}`, tel que le serveur le décrit.
struct PlaceRequest: Equatable, CustomStringConvertible {
    /// `{uid du demandeur}_{emplacement}`.
    let id: String
    let miffId: String
    let requestedBy: String
    let name: String
    let type: PlaceType
    let address: String?
    let lat: Double
    let lng: Double
    let radiusM: Int
    /// La catégorie choisie (les mêmes valeurs qu'un lieu : neuvième clé du contrat) ; nil pour un document qui n'en a pas.
    let category: PlaceCategory?
    /// Quand le serveur l'a écrite ; nil tant que l'écriture est en route (une demande tout juste faite sur ce téléphone).
    let createdAtMillis: Int?

    var description: String {
        "PlaceRequest(redacted)"
    }

    /// Plus de 30 jours. Une demande sans date est toute neuve, jamais expirée.
    func isExpired(nowMillis: Int) -> Bool {
        guard let createdAtMillis else {
            return false
        }
        return nowMillis - createdAtMillis > PlaceRequestLimits.expiryMillis
    }

    var slot: Int {
        PlaceRequest.parseId(id)?.slot ?? 0
    }

    // Les bornes des règles (`validRequestShape`) : plus larges que celles de l'écran (50 à 800).
    private static let ruleRadiusMin = 50
    private static let ruleRadiusMax = 2000

    /// `{uid}_{1, 2 ou 3}` : l'uid fait 1 à 128 lettres et chiffres. Jamais un chemin construit avec un autre texte.
    static func parseId(_ id: String) -> (requester: String, slot: Int)? {
        guard let separator = id.lastIndex(of: "_") else {
            return nil
        }
        let requester = String(id[..<separator])
        let slotText = String(id[id.index(after: separator)...])
        guard slotText.count == 1, let slot = Int(slotText), PlaceRequestLimits.slots.contains(slot) else {
            return nil
        }
        let scalars = Array(requester.unicodeScalars)
        guard !scalars.isEmpty, scalars.count <= 128, scalars.allSatisfy(isAlphanumeric) else {
            return nil
        }
        return (requester, slot)
    }

    private static func isAlphanumeric(_ scalar: Unicode.Scalar) -> Bool {
        (scalar.value >= 0x30 && scalar.value <= 0x39)
            || (scalar.value >= 0x41 && scalar.value <= 0x5A)
            || (scalar.value >= 0x61 && scalar.value <= 0x7A)
    }

    /// Une demande telle que le serveur la décrit, nettoyée avant d'être montrée (le nom et l'adresse ont été écrits par quelqu'un
    /// d'autre). nil quand le document ne peut pas être une demande de cette app : identifiant invalide, `requestedBy` qui n'est pas
    /// l'uid de l'identifiant (un document forgé), pas de nom visible, type inconnu, coordonnées ou rayon hors bornes. Elle n'est alors
    /// simplement pas montrée, jamais un plantage. `createdAtMillis` est lu par l'adaptateur Firestore (le `Timestamp` n'entre pas ici).
    static func fromServer(
        id: String,
        miffId: String,
        fields: [String: Any]?,
        createdAtMillis: Int?
    ) -> PlaceRequest? {
        guard let fields, let parsed = parseId(id),
              (fields["requestedBy"] as? String) == parsed.requester,
              let rawName = fields["name"] as? String,
              let name = MiffName.fromServer(rawName),
              let typeValue = fields["type"] as? String,
              let type = PlaceType(rawValue: typeValue),
              let lat = number(fields["lat"]), lat.isFinite, lat >= -90, lat <= 90,
              let lng = number(fields["lng"]), lng.isFinite, lng >= -180, lng <= 180,
              let radius = number(fields["radiusM"]), radius.isFinite, radius == radius.rounded(),
              radius >= Double(ruleRadiusMin), radius <= Double(ruleRadiusMax) else {
            return nil
        }
        return PlaceRequest(
            id: id,
            miffId: miffId,
            requestedBy: parsed.requester,
            name: name,
            type: type,
            address: (fields["address"] as? String).flatMap { MiffName.fromServer($0) },
            lat: lat,
            lng: lng,
            radiusM: Int(radius),
            category: (fields["category"] as? String).flatMap { PlaceCategory(rawValue: $0) },
            createdAtMillis: createdAtMillis
        )
    }

    /// Un nombre lu sur le réseau : décimal ou entier (le SDK rend l'un ou l'autre selon la valeur).
    private static func number(_ value: Any?) -> Double? {
        if let double = value as? Double {
            return double
        }
        if let integer = value as? Int {
            return Double(integer)
        }
        if let integer = value as? Int64 {
            return Double(integer)
        }
        return nil
    }

    /// La demande vue comme le lieu qu'elle deviendrait, pour les écrans qui savent déjà dessiner un lieu (le détail : carte, cercle,
    /// adresse, rayon, type). Ce N'EST PAS un lieu de la Miff : son identifiant est celui de la demande.
    var asPlace: Place {
        Place(
            id: id,
            miffId: miffId,
            name: name,
            type: type,
            category: category,
            address: address,
            lat: lat,
            lng: lng,
            radiusM: radiusM,
            createdBy: requestedBy
        )
    }
}

/// Ce que la personne a rempli pour demander un lieu : ce que construit le formulaire (mêmes champs et mêmes contrôles qu'un lieu,
/// catégorie comprise).
struct PlaceRequestDraft: Equatable, CustomStringConvertible {
    let name: String
    let address: String
    let lat: Double
    let lng: Double
    let radiusM: Int
    let type: PlaceType
    let category: PlaceCategory

    var description: String {
        "PlaceRequestDraft(redacted)"
    }

    /// Ce qui ne va pas dans ce brouillon, ou nil. Reprend les règles et le formulaire d'un lieu.
    var problem: DraftProblem? {
        if !PlaceName.isValid(name) {
            return .name
        }
        let blank = address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        if blank || address.utf16.count > PlaceLimits.addressMax || !PlaceCoordinates.isValid(lat: lat, lng: lng) {
            return .address
        }
        return PlaceRadius.isAllowed(radiusM) ? nil : .radius
    }
}

/// L'ordre des demandes dans une liste.
enum PlaceRequestOrder {
    /// Le créateur : la plus ancienne d'abord (c'est celle qui attend depuis le plus longtemps). Une demande sans date vient en dernier.
    static func forCreator(_ requests: [PlaceRequest]) -> [PlaceRequest] {
        requests.sorted { first, second in
            let a = first.createdAtMillis ?? Int.max
            let b = second.createdAtMillis ?? Int.max
            return a != b ? a < b : first.id < second.id
        }
    }

    /// Le demandeur : la plus récente en haut (ce qu'on vient d'envoyer est ce qu'on cherche).
    static func forMember(_ requests: [PlaceRequest]) -> [PlaceRequest] {
        requests.sorted { first, second in
            let a = first.createdAtMillis ?? Int.max
            let b = second.createdAtMillis ?? Int.max
            return a != b ? a > b : first.id < second.id
        }
    }
}

/// Lequel des trois emplacements d'un membre est libre.
enum PlaceRequestSlots {
    /// Le premier emplacement libre parmi les demandes qui EXISTENT (une demande expirée occupe son emplacement tant qu'elle n'est pas
    /// supprimée), ou nil quand les trois sont pris.
    static func firstFree(uid: String, existing: [PlaceRequest]) -> Int? {
        let taken = Set(existing.filter { $0.requestedBy == uid }.map { $0.id })
        return PlaceRequestLimits.slots.first { !taken.contains(PlaceRequestLimits.idOf(uid: uid, slot: $0)) }
    }
}

/// Les lots du ticket LIEU-REQ, en données : testés sans Firestore, appliqués chacun par UNE transaction (ou, hors connexion, par un lot
/// que le téléphone garde).
enum PlaceRequestWrites {
    static func requestPath(miffId: String, requestId: String) -> String {
        "miffs/\(miffId)/placeRequests/\(requestId)"
    }

    static func counterPath(miffId: String) -> String {
        "miffs/\(miffId)/meta/placeRequests"
    }

    /// Une demande entière : chaque clé, comme `validRequestShape` la veut (neuf clés).
    static func requestDocument(uid: String, draft: PlaceRequestDraft) -> [String: FieldValueSpec] {
        [
            "requestedBy": .string(uid),
            "name": .string(PlaceName.stored(draft.name)),
            "type": .string(draft.type.rawValue),
            "category": .string(draft.category.rawValue),
            "address": .string(PlaceAddressText.stored(draft.address)),
            "lat": .double(PlaceCoordinates.round5(draft.lat)),
            "lng": .double(PlaceCoordinates.round5(draft.lng)),
            "radiusM": .integer(draft.radiusM),
            "createdAt": .serverTimestamp
        ]
    }

    /// Le compteur de la Miff tel qu'un MEMBRE le fait bouger : `increment(delta)` fusionné dans le document (il marche que le document
    /// existe déjà ou non), lié à CETTE demande par `lastReqId` (les règles refusent un mouvement qui n'est pas lié à une demande créée
    /// ou supprimée dans le même lot).
    static func memberCounter(requestId: String, delta: Int) -> [String: FieldValueSpec] {
        [
            "pending": .increment(delta),
            "lastReqId": .string(requestId),
            "updatedAt": .serverTimestamp
        ]
    }

    /// Le compteur tel que le CRÉATEUR l'écrit : le nombre réel de demandes qu'il lit (cela corrige aussi une dérive), aucune demande
    /// attachée (`lastReqId` nul). Toujours une valeur ABSOLUE, jamais `increment(-1)` (un compteur trop bas irait sous 0).
    static func creatorCounter(pending: Int) -> [String: FieldValueSpec] {
        [
            "pending": .integer(min(max(pending, 0), PlaceRequestLimits.perMiff)),
            "lastReqId": .null,
            "updatedAt": .serverTimestamp
        ]
    }

    /// UN lot de 2 : la demande et le compteur (+1).
    static func create(uid: String, miffId: String, slot: Int, draft: PlaceRequestDraft) -> [DocWrite] {
        let id = PlaceRequestLimits.idOf(uid: uid, slot: slot)
        let request = DocWrite(
            path: requestPath(miffId: miffId, requestId: id),
            kind: .set,
            data: requestDocument(uid: uid, draft: draft)
        )
        let counter = DocWrite(
            path: counterPath(miffId: miffId),
            kind: .merge,
            data: memberCounter(requestId: id, delta: 1)
        )
        return [request, counter]
    }

    /// UN lot de 2 : la demande est supprimée par son auteur et le compteur baisse de 1.
    static func withdraw(miffId: String, requestId: String) -> [DocWrite] {
        [
            DocWrite(path: requestPath(miffId: miffId, requestId: requestId), kind: .delete),
            DocWrite(
                path: counterPath(miffId: miffId),
                kind: .merge,
                data: memberCounter(requestId: requestId, delta: -1)
            )
        ]
    }

    /// LE REPLI d'un retrait que les règles ont refusé (contrat 2.2) : un compteur remis à 0 par un créateur alors que des demandes
    /// existent irait à -1, hors bornes. Le même lot avec le compteur à la valeur ABSOLUE 0 est accepté.
    static func withdrawWithCounterAtZero(miffId: String, requestId: String) -> [DocWrite] {
        [
            DocWrite(path: requestPath(miffId: miffId, requestId: requestId), kind: .delete),
            DocWrite(
                path: counterPath(miffId: miffId),
                kind: .merge,
                data: ["pending": .integer(0), "lastReqId": .string(requestId), "updatedAt": .serverTimestamp]
            )
        ]
    }
}
