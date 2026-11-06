import Foundation
import Security

/// Octets aléatoires du générateur cryptographique du système (`SecRandomCopyBytes`).
enum SecureRandom {
    /// nil si le système n'a pas pu fournir les octets : aucun repli sur un générateur non cryptographique.
    static func bytes(_ count: Int) -> [UInt8]? {
        guard count > 0 else {
            return nil
        }
        var buffer = [UInt8](repeating: 0, count: count)
        let status = SecRandomCopyBytes(kSecRandomDefault, count, &buffer)
        return status == errSecSuccess ? buffer : nil
    }
}
