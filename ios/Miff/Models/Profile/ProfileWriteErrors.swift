import Foundation

/// Codes d'erreur de Firestore (domaine « FIRFirestoreErrorDomain »), les mêmes valeurs que les codes gRPC. Écrits en
/// clair pour que l'interprétation reste testable sans le SDK.
enum FirestoreErrorNumber {
    static let domain = "FIRFirestoreErrorDomain"
    static let cancelled = 1
    static let deadlineExceeded = 4
    static let permissionDenied = 7
    static let unavailable = 14
    static let unauthenticated = 16
}

/// Ne montre jamais l'erreur, son message ni son code : ils peuvent citer le document (donc un nom ou un numéro).
func interpretWriteError(_ error: Error) -> ProfileWriteResult {
    let nsError = error as NSError
    if nsError.domain == NSURLErrorDomain {
        return .offline
    }
    guard nsError.domain == FirestoreErrorNumber.domain else {
        return .failed
    }
    switch nsError.code {
    case FirestoreErrorNumber.unavailable, FirestoreErrorNumber.deadlineExceeded, FirestoreErrorNumber.cancelled:
        return .offline
    case FirestoreErrorNumber.permissionDenied, FirestoreErrorNumber.unauthenticated:
        return .refused
    default:
        return .failed
    }
}

extension ProfileSeed {
    /// Noms et photo d'un profil existant, s'ils ont la forme qu'exigent les règles ; sinon nil.
    static func from(fields: [String: Any]) -> ProfileSeed? {
        guard let first = fields["firstName"] as? String, let last = fields["lastName"] as? String else {
            return nil
        }
        var avatar = fields["avatar"] as? Data
        if let bytes = avatar, bytes.isEmpty || bytes.count > ProfileDocument.maxAvatarBytes {
            avatar = nil
        }
        return ProfileSeed(firstName: first, lastName: last, avatarJpeg: avatar)
    }
}
