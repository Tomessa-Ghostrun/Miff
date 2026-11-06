import Foundation

/// `locationAccess` du statut (ticket §6.7, quatre valeurs seulement), d'après ce que dit le téléphone. Du Swift pur.
enum LocationAccessRule {
    /// - `background` : « Toujours » ET la position exacte ET le service de localisation de l'iPhone allumé ;
    /// - `foreground` : une autorisation qui ne suffit pas (« pendant l'utilisation », « une fois », ou « Toujours » avec la
    ///   position approximative seulement) ;
    /// - `denied` : refusée, restreinte, ou le service de localisation de tout l'iPhone est coupé ;
    /// - `unknown` : jamais demandée.
    static func of(_ snapshot: PermissionSnapshot) -> LocationAccess {
        if !snapshot.servicesEnabled {
            return .denied
        }
        switch snapshot.location {
        case .notDetermined:
            return .unknown
        case .restricted, .denied:
            return .denied
        case .whenInUse:
            return .foreground
        case .always:
            return snapshot.preciseLocation ? .background : .foreground
        }
    }
}
