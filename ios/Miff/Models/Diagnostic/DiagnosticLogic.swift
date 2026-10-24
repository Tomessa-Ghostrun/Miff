import Foundation

// Logique pure du diagnostic (interprétation de résultats). Sans dépendance à Firebase : testée sans réseau.
// Comportement identique à android/.../domain/diagnostic/DiagnosticLogic.kt.

/// Projet Firebase auquel cette app doit parler. Tout autre projet doit être signalé sur l'écran de diagnostic.
let expectedFirebaseProjectId = "miff-3d283"

func isExpectedProject(_ projectId: String?) -> Bool {
    projectId == expectedFirebaseProjectId
}

/// Résultat de la lecture non authentifiée d'un document de Miff (test 1).
enum RulesProbeOutcome: Equatable {
    /// Lecture refusée (permission-denied) : les règles de sécurité protègent les données. C'est le résultat attendu.
    case rulesActive
    /// La lecture a réussi sans être connecté : les règles sont ouvertes. À signaler sans ambiguïté.
    case rulesOpen
    /// Pas de réseau (ou serveur injoignable) : le test n'a pas pu être fait.
    case offline
    /// Tout le reste (App Check, quota, inconnu).
    case inconclusive
}

/// Codes d'erreur de Firestore (domaine « FIRFirestoreErrorDomain »), identiques aux codes gRPC.
private enum FirestoreErrorNumber {
    static let domain = "FIRFirestoreErrorDomain"
    static let deadlineExceeded = 4
    static let permissionDenied = 7
    static let unavailable = 14
}

/// Erreur levée quand une opération réseau dépasse le délai que nous lui accordons.
struct OperationTimeoutError: Error, Equatable {}

/// Interprète l'échec de la lecture non authentifiée. Une lecture réussie vaut `.rulesOpen` (décidé par l'appelant).
func interpretRulesProbeFailure(_ error: Error) -> RulesProbeOutcome {
    if error is OperationTimeoutError {
        return .offline
    }
    let nsError = error as NSError
    if nsError.domain == NSURLErrorDomain {
        return .offline
    }
    guard nsError.domain == FirestoreErrorNumber.domain else {
        return .inconclusive
    }
    switch nsError.code {
    case FirestoreErrorNumber.permissionDenied:
        return .rulesActive
    case FirestoreErrorNumber.unavailable, FirestoreErrorNumber.deadlineExceeded:
        return .offline
    default:
        return .inconclusive
    }
}

/// Offre affichée pour `entitlements/{uid}`. Un document absent, inactif ou expiré vaut « Classic » (gratuit).
enum OfferState: Equatable {
    case classic
    case premium(expiresAt: Date)
}

/// Lit le statut premium comme le font les règles de sécurité : `active == true` et `expiresAt` dans le futur.
/// Document absent, champs manquants ou date dépassée : Classic.
func interpretEntitlement(documentExists: Bool, active: Bool?, expiresAt: Date?, now: Date) -> OfferState {
    guard documentExists, active == true, let expiresAt, expiresAt > now else {
        return .classic
    }
    return .premium(expiresAt: expiresAt)
}
