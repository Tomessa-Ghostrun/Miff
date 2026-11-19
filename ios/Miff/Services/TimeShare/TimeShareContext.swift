import Foundation

// CONFIDENTIALITÉ et COÛT, pour ce fichier : il LIT l'état des autorisations (jamais une position), l'état du voyage, ce que le Feed sait de
// mon partage et la copie locale du suivi (pour COMPTER les zones, sans en lire une coordonnée). Aucune lecture Firestore, aucune écriture,
// aucun événement (critères TS-1, TS-4).

/// Ce que la carte demande au téléphone pour dire la vérité sur l'état du suivi, MAINTENANT (un double dans les tests).
@MainActor
protocol TimeShareContextSource: AnyObject {
    func conditions(miffId: String) async -> TimeShareConditions
}

/// L'implémentation réelle : les mêmes sources que l'Update, le Feed et le mode voyage.
@MainActor
final class LiveTimeShareContext: TimeShareContextSource {
    private let permissions: () -> DevicePermissions
    private let travelActive: () -> Bool
    private let sharingStopped: (String) -> Bool
    private let zoneCount: () -> Int

    /// - `permissions` : l'état des autorisations (jamais une position) ;
    /// - `travelActive` : un voyage est en cours sur ce téléphone ;
    /// - `sharingStopped` : le Feed dit que je ne partage plus avec cette Miff (inconnu pour une Miff qu'il n'affiche pas : faux) ;
    /// - `zoneCount` : le nombre de zones que le suivi connaît, toutes Miffs confondues.
    init(
        permissions: @escaping () -> DevicePermissions,
        travelActive: @escaping () -> Bool,
        sharingStopped: @escaping (String) -> Bool,
        zoneCount: @escaping () -> Int
    ) {
        self.permissions = permissions
        self.travelActive = travelActive
        self.sharingStopped = sharingStopped
        self.zoneCount = zoneCount
    }

    func conditions(miffId: String) async -> TimeShareConditions {
        let snapshot = await permissions().snapshot()
        return TimeShareConditions(
            travelActive: travelActive(),
            sharingStopped: sharingStopped(miffId),
            locationInsufficient: !snapshot.locationComplete,
            overWatchedLimit: zoneCount() > TimeShareLimits.maxWatchedZones
        )
    }

    /// Le nombre de zones distinctes que le suivi connaît (les copies d'un même lieu dans plusieurs Miffs sont UNE zone), d'après la copie
    /// locale : une lecture de fichier, aucune coordonnée gardée ni montrée.
    static func zoneCount(of local: TrackingLocalStore) -> Int {
        guard let state = local.load() else {
            return 0
        }
        var items: [ZoneSelection.MiffPlace] = []
        for (miffId, miff) in state.miffs {
            for place in miff.places {
                if let fact = place.fact {
                    items.append(ZoneSelection.MiffPlace(miffId: miffId, place: fact))
                }
            }
        }
        return ZoneSelection.merge(items).count
    }
}
