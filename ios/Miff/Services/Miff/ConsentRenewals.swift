import Foundation

// SÉCURITÉ, pour ce fichier : le nom d'une Miff est une donnée personnelle. Il arrive à l'écran qui demande le renouvellement
// et nulle part ailleurs : ni journal, ni événement, ni message. Chaque échec sort d'ici sous la forme d'une `MiffFailure`.
// RIEN n'est écrit sans un toucher explicite de la personne sur « J'accepte » (l'appelant est cet écran), et la vérification
// n'écrit rien du tout. Miroir de `ConsentRenewals` d'Android (LOC-2a).

/// Une Miff où la personne partage sous un texte plus ancien : ce que nomme l'écran de renouvellement.
struct RenewalMiff: Equatable, CustomStringConvertible {
    let miffId: String
    /// Le nom tel que le téléphone le connaît ; vide quand il n'a pas pu être lu (l'écran le dit alors sans le nom).
    let name: String
    /// Le créateur ne peut pas quitter une Miff (il la supprime) : le lien « Quitter la Miff » de l'écran est pour un membre.
    let isOwner: Bool

    init(miffId: String, name: String, isOwner: Bool = false) {
        self.miffId = miffId
        self.name = name
        self.isOwner = isOwner
    }

    var description: String {
        "RenewalMiff(redacted)"
    }
}

/// Ce qu'a trouvé la lecture des consentements de la personne.
enum ConsentRenewalCheck: Equatable, CustomStringConvertible {
    /// Chaque Miff où la personne partage porte le texte en vigueur (ou la personne ne partage nulle part).
    case nothingToRenew
    /// Le serveur n'a pas pu être interrogé (hors ligne, trop lent) : rien n'est affirmé, rien ne bloque, la prochaine
    /// ouverture demandera de nouveau.
    case unknown
    case due([RenewalMiff])

    var description: String {
        switch self {
        case .nothingToRenew:
            return "nothingToRenew"
        case .unknown:
            return "unknown"
        case .due:
            return "due(redacted)"
        }
    }
}

/// Le renouvellement du consentement (LOC-4a, règle R-LIEU-3) : les membres qui partagent sous un texte PLUS ANCIEN doivent
/// accepter le texte en vigueur avant qu'une position puisse être écrite (LOC-4b). La vérification lit, auprès du serveur,
/// l'index de la personne (1 lecture) et la fiche de chaque Miff (1 document chacune, 2 lectures de règles chacune) : une
/// seule Miff au plus pour un compte gratuit. Le renouvellement est UN lot (0 lecture de règles).
protocol ConsentRenewals {
    func check(uid: String) async -> ConsentRenewalCheck

    /// Écrit la version en vigueur dans la fiche de chacune des Miffs de `miffIds`, en UN lot, après le « oui » de la personne.
    func renew(uid: String, miffIds: [String]) async -> MiffWriteResult
}

final class FirestoreConsentRenewals: ConsentRenewals {
    private let store: MiffStore
    private let timeoutSeconds: TimeInterval

    init(store: MiffStore, timeoutSeconds: TimeInterval = 20) {
        self.store = store
        self.timeoutSeconds = timeoutSeconds
    }

    func check(uid: String) async -> ConsentRenewalCheck {
        let store = self.store
        let membership = await guardedCall(timeoutSeconds: timeoutSeconds) {
            try await store.readMemberships(uid: uid, fromServer: true)
        }
        let refs: [MembershipRef]
        switch membership {
        case .value(let found):
            refs = found
        case .failed:
            return .unknown
        }
        var due: [RenewalMiff] = []
        var incomplete = false
        var seen = Set<String>()
        for ref in refs {
            // Une Miff n'est regardée qu'une fois (l'index d'un compte Premium peut en citer deux fois la même).
            if !seen.insert(ref.miffId).inserted {
                continue
            }
            let read = await guardedCall(timeoutSeconds: timeoutSeconds) {
                try await store.readSharingConsent(miffId: ref.miffId, uid: uid)
            }
            switch read {
            case .value(let consent):
                if let consent, consent.enabled, MiffSharingConsent.isRenewalDue(consent.version) {
                    due.append(await describe(miffId: ref.miffId, uid: uid))
                }
            case .failed(_, let lostAccess):
                // Un refus des règles veut dire que la personne n'est plus dans cette Miff (la perte d'accès est traitée
                // ailleurs) : rien à renouveler là. Tout le reste (pas de réseau...) laisse la réponse inconnue.
                if !lostAccess {
                    incomplete = true
                }
            }
        }
        if !due.isEmpty {
            return .due(due)
        }
        return incomplete ? .unknown : .nothingToRenew
    }

    func renew(uid: String, miffIds: [String]) async -> MiffWriteResult {
        var ids: [String] = []
        for id in miffIds where !ids.contains(id) {
            ids.append(id)
        }
        guard !ids.isEmpty, ids.allSatisfy(MiffId.isValid), !uid.isEmpty, !uid.contains("/") else {
            return .failed(.unknown)
        }
        let store = self.store
        let outcome = await guardedCall(timeoutSeconds: timeoutSeconds) {
            // La fiche est lue DANS la transaction : seules les Miffs où la personne partage encore sont renouvelées.
            try await store.commitWhereSharing(uid: uid, miffIds: ids) { active in
                active.flatMap { MiffWrites.renewConsent(miffId: $0, uid: uid) }
            }
        }
        switch outcome {
        case .value:
            return .done
        case .failed(let failure, _):
            // Un lot dont la réponse s'est perdue a pu partir, et un refus peut vouloir dire « déjà renouvelé » (un autre
            // téléphone l'a fait : les règles veulent une version strictement plus récente) : le serveur est interrogé avant
            // de dire à la personne que ça a échoué.
            if await check(uid: uid) == .nothingToRenew {
                return .done
            }
            return .failed(failure)
        }
    }

    private func describe(miffId: String, uid: String) async -> RenewalMiff {
        let store = self.store
        let read = await guardedCall(timeoutSeconds: timeoutSeconds) {
            try await store.readMiff(miffId: miffId, fromServer: false)
        }
        guard case .value(.exists(let details)) = read else {
            return RenewalMiff(miffId: miffId, name: "")
        }
        return RenewalMiff(miffId: miffId, name: MiffName.clean(details.name), isOwner: details.ownerUid == uid)
    }
}
