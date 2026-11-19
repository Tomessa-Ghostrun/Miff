#if ALERT_DANGER
import FirebaseFirestore
import Foundation

// SÉCURITÉ, CONFIDENTIALITÉ et COÛT, pour ce fichier (ticket docs/tickets/alerte-danger.md, backend/data-model.md §2.18 et §2.20,
// backend/firestore.rules `match /alerts/{uid}`, NON DÉPLOYÉ ; miroir d'`AlertStore.kt` d'Android) :
// - Le début et la fin d'une alerte sont des TRANSACTIONS, jamais des lots : un lot fait hors ligne est gardé par Firestore et part PLUS TARD, tout
//   seul, ce qui serait une « alerte fantôme » envoyée des heures après. Une transaction n'existe qu'en ligne : « pas d'accusé » veut toujours
//   dire « rien d'écrit ». L'attente d'une alerte qui n'a pas pu partir est tenue par l'APP (`DangerAlertController`), où la personne la voit.
// - UNE lecture de MA fiche (mon partage est-il actif dans cette Miff ?), UNE de mon document d'alerte et UNE de mon compteur du jour dans le
//   début ; le début écrit le compteur ET l'alerte dans la même transaction (les règles refusent une alerte sans son crédit). Rien n'est écrit
//   dans une autre Miff : une alerte vit dans UNE Miff (décision du PM, D9).
// - ORDRE des vérifications du début (revue indépendante de l'Android) : une alerte VIVANTE de moi est adoptée AVANT de regarder la limite du jour,
//   sinon une réponse perdue au 3e début ferait dire « limite atteinte » alors que l'alerte est partie (alerte fantôme).
// - Le document ne contient AUCUNE position, AUCUN texte, AUCUNE photo. Aucun identifiant, nom ou date d'une alerte n'est journalisé ; aucun
//   message d'erreur ne sort d'ici (il peut citer un chemin).

final class FirestoreDangerAlertStore: DangerAlertStore {
    private func firestore() throws -> Firestore {
        guard FirebaseBootstrap.isConfigured else {
            throw NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet)
        }
        return Firestore.firestore()
    }

    private func references(_ database: Firestore, uid: String, miffId: String) -> References {
        let miff = database.collection("miffs").document(miffId)
        return References(
            card: miff.collection("members").document(uid),
            alert: miff.collection("alerts").document(uid),
            quota: database.collection("users").document(uid).collection("quota").document("alerts")
        )
    }

    private struct References {
        let card: DocumentReference
        let alert: DocumentReference
        let quota: DocumentReference
    }

    // MARK: - Le début

    func start(uid: String, miffId: String, deviceId: String, nowMillis: Int) async -> DangerStartResult {
        guard MiffId.isValid(miffId), PushIdentifier.isValid(uid, maxLength: PushIdentifier.authorMaxLength) else {
            return .failed(.refused)
        }
        do {
            let database = try firestore()
            let refs = references(database, uid: uid, miffId: miffId)
            let value = try await database.runTransaction { transaction, errorPointer in
                do {
                    // Toutes les lectures AVANT la première écriture (la règle des transactions Firestore).
                    let reads = StartReads(
                        card: try transaction.getDocument(refs.card),
                        existing: try transaction.getDocument(refs.alert),
                        counter: try transaction.getDocument(refs.quota)
                    )
                    return Self.decideStart(
                        reads, transaction: transaction, refs: refs,
                        context: StartContext(miffId: miffId, deviceId: deviceId, nowMillis: nowMillis)
                    )
                } catch let error as NSError {
                    errorPointer?.pointee = error
                    return nil
                }
            }
            return (value as? DangerStartResult) ?? .failed(.refused)
        } catch {
            return .failed(Self.failure(of: error))
        }
    }

    private struct StartReads {
        let card: DocumentSnapshot
        let existing: DocumentSnapshot
        let counter: DocumentSnapshot
    }

    private struct StartContext {
        let miffId: String
        let deviceId: String
        let nowMillis: Int
    }

    private static func decideStart(
        _ reads: StartReads,
        transaction: Transaction,
        refs: References,
        context: StartContext
    ) -> DangerStartResult {
        let now = context.nowMillis
        let card = reads.card
        let existing = reads.existing
        let counter = reads.counter
        let sharing = (card.data()?["sharing"] as? [String: Any])?["enabled"] as? Bool
        let doc = existing.exists ? parse(existing) : nil
        // L'ORDRE des vérifications est celui du plan pur : une alerte vivante de moi est ADOPTÉE avant que la limite du jour soit jugée (revue de l'Android, M1).
        let plan = DangerAlertStartPlan.decide(
            cardExists: card.exists,
            sharing: sharing == true,
            alert: doc,
            counter: counter.exists ? quota(of: counter) : nil,
            nowMillis: now
        )
        switch plan {
        case .notMember:
            return .failed(.notMember)
        case .sharingStopped:
            return .failed(.sharingStopped)
        case .adopt(let live):
            return .adopted(live)
        case .wait(let waitMillis):
            return .tooSoon(waitMillis: waitMillis)
        case .quotaFull:
            return .failed(.quotaReached)
        case .write(let slot, let seq):
            let credit: [String: FieldValueSpec]?
            if let slot {
                credit = DangerAlertWrites.quotaUpdate(slot: slot, miffId: context.miffId, deviceNowMillis: now)
            } else {
                credit = DangerAlertWrites.quotaCreate(miffId: context.miffId, deviceNowMillis: now)
            }
            let start = DangerAlertWrites.start(deviceId: context.deviceId, deviceNowMillis: now, seq: seq)
            guard let credit, let start else {
                return .failed(.refused)
            }
            let converted = FirestoreFieldConversion.convert(credit)
            if counter.exists {
                transaction.updateData(converted, forDocument: refs.quota)
            } else {
                transaction.setData(converted, forDocument: refs.quota)
            }
            transaction.setData(FirestoreFieldConversion.convert(start), forDocument: refs.alert)
            let expires = DangerAlertRules.expiresAtMillis(deviceNowMillis: now)
            return .started(startedAtMillis: now, expiresAtMillis: expires)
        }
    }

    // MARK: - La fin

    func end(uid: String, miffId: String, deviceId: String, nowMillis: Int) async -> DangerEndResult {
        guard MiffId.isValid(miffId), PushIdentifier.isValid(uid, maxLength: PushIdentifier.authorMaxLength),
              let fields = DangerAlertWrites.end(deviceId: deviceId, deviceNowMillis: nowMillis) else {
            return .failed(.refused)
        }
        do {
            let database = try firestore()
            let alert = references(database, uid: uid, miffId: miffId).alert
            _ = try await database.runTransaction { transaction, errorPointer in
                do {
                    let existing = try transaction.getDocument(alert)
                    // Disparue, ou déjà terminée : rien à écrire, et le souhait de la personne (« c'est fini ») est un fait.
                    if existing.exists, let doc = Self.parse(existing), doc.active {
                        transaction.updateData(FirestoreFieldConversion.convert(fields), forDocument: alert)
                    }
                    return nil
                } catch let error as NSError {
                    errorPointer?.pointee = error
                    return nil
                }
            }
            return .ended
        } catch {
            return .failed(Self.failure(of: error))
        }
    }

    // MARK: - Les lectures

    func readMine(uid: String, miffId: String) async -> DangerMineRead {
        guard MiffId.isValid(miffId), PushIdentifier.isValid(uid, maxLength: PushIdentifier.authorMaxLength) else {
            return .unavailable
        }
        do {
            let snapshot = try await firestore().collection("miffs").document(miffId)
                .collection("alerts").document(uid).getDocument(source: .server)
            return .value(snapshot.exists ? Self.parse(snapshot) : nil)
        } catch let error as NSError {
            if error.domain == FirestoreErrorNumber.domain, error.code == FirestoreErrorNumber.permissionDenied {
                return .denied
            }
            return .unavailable
        }
    }

    func observe(miffId: String) -> AsyncStream<DangerAlertsHeard> {
        AsyncStream { continuation in
            guard FirebaseBootstrap.isConfigured, MiffId.isValid(miffId) else {
                continuation.finish()
                return
            }
            let registration = Firestore.firestore().collection("miffs").document(miffId).collection("alerts")
                .addSnapshotListener(includeMetadataChanges: true) { snapshot, error in
                    // Un refus (les règles ne sont pas encore déployées, la personne a quitté la Miff...) veut dire « pas d'alerte », jamais une
                    // erreur sur le Feed.
                    guard error == nil, let snapshot else {
                        continuation.finish()
                        return
                    }
                    let docs = snapshot.documents.compactMap { Self.parse($0) }
                    continuation.yield(DangerAlertsHeard(docs: docs, fromCache: snapshot.metadata.isFromCache))
                }
            continuation.onTermination = { _ in
                registration.remove()
            }
        }
    }

    // MARK: - Ce qui vient du réseau

    private static func failure(of error: Error) -> DangerAlertFailure {
        let nsError = error as NSError
        if nsError.domain == FirestoreErrorNumber.domain, nsError.code == FirestoreErrorNumber.permissionDenied {
            return .refused
        }
        return .offline
    }

    /// Le compteur du jour, lu comme tout ce qui vient du réseau : une case qui n'est pas une date compte comme libre.
    static func quota(of snapshot: DocumentSnapshot) -> DangerAlertQuota {
        let data = snapshot.data() ?? [:]
        return DangerAlertQuota(slots: ["s0", "s1", "s2"].map { key in millis(data[key]) })
    }

    /// Le document, lu comme tout ce qui vient du réseau : chaque champ vérifié, un document qui n'est pas un des nôtres est ignoré.
    static func parse(_ snapshot: DocumentSnapshot) -> DangerAlertDoc? {
        // Une date que le serveur n'a pas encore remplie (une écriture de ce téléphone en attente) est ESTIMÉE par le SDK au lieu d'être nulle.
        let data = snapshot.data(with: .estimate) ?? [:]
        return DangerAlertDoc.parse(
            uid: snapshot.documentID,
            status: data["status"],
            startedAtMillis: millis(data["startedAt"]),
            expiresAtMillis: millis(data["expiresAt"]),
            seq: data["seq"]
        )
    }

    private static func millis(_ value: Any?) -> Int? {
        (value as? Timestamp).map { Int($0.dateValue().timeIntervalSince1970 * 1000) }
    }
}

/// Les membres de la Miff d'origine pour l'écran « avertis » (leurs numéros) : UNE lecture ponctuelle, jamais un écouteur, aucune permission
/// « Contacts » et aucun carnet d'adresses.
protocol DangerAlertMembersReader {
    /// nil quand la lecture n'a pas pu être faite (hors ligne sans copie) : seuls les services d'urgence sont alors proposés.
    func members(miffId: String) async -> [MiffMember]?
}

final class FirestoreDangerAlertMembersReader: DangerAlertMembersReader {
    func members(miffId: String) async -> [MiffMember]? {
        guard FirebaseBootstrap.isConfigured, MiffId.isValid(miffId) else {
            return nil
        }
        do {
            let snapshot = try await timedOperation(seconds: 10) {
                try await Firestore.firestore().collection("miffs").document(miffId)
                    .collection("members").getDocuments()
            }
            return FirestoreMiffObserver.members(of: snapshot)
        } catch {
            return nil
        }
    }
}
#endif
