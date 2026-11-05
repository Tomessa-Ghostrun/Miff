import Foundation

/// L'INTERRUPTEUR DE COMPILATION qui relâche le blocage de la localisation (décision du PM du 2026-11-01 : « Toujours » est
/// obligatoire, iOS compris ; risque de rejet Apple 5.1.2(i) ASSUMÉ par le PM ; l'interrupteur permet de relâcher sans réécrire
/// si Apple refuse). Miroir de `BuildConfig.LOCATION_BLOCKING` d'Android.
///
/// Il vit dans le réglage de compilation `MIFF_LOCATION_BLOCKING` de `project.yml` (YES par défaut), recopié dans la clé
/// `MiffLocationBlocking` de l'Info.plist. Pour relâcher : `xcodebuild ... MIFF_LOCATION_BLOCKING=NO` (le workflow
/// `ios-testflight` a une entrée pour cela). SANS DANGER par défaut : toute valeur absente, vide ou non reconnue (par exemple
/// un `$(MIFF_LOCATION_BLOCKING)` non résolu) BLOQUE ; seul un « NO » explicite relâche.
enum LocationBlockingSwitch {
    static let infoKey = "MiffLocationBlocking"

    /// Les seules valeurs qui relâchent le blocage.
    private static let relaxingValues: Set<String> = ["NO", "FALSE", "0"]

    /// Vrai : l'écran de la localisation ne se quitte pas sans « Toujours » (par défaut).
    static func isBlocking(info: [String: Any]?) -> Bool {
        guard let raw = info?[infoKey] else {
            return true
        }
        if let flag = raw as? Bool {
            return flag
        }
        guard let text = raw as? String else {
            return true
        }
        return !relaxingValues.contains(text.trimmingCharacters(in: .whitespaces).uppercased())
    }

    /// La valeur de CE build.
    static var current: Bool {
        isBlocking(info: Bundle.main.infoDictionary)
    }
}
