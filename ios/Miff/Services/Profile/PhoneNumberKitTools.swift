import Foundation
import PhoneNumberKit

/// Les règles de numéro de l'Onboarding (ticket §6.4, décision Q1 du PM) sur la bibliothèque PhoneNumberKit (MIT, port
/// Swift des données officielles de libphonenumber : mêmes plans de numérotation qu'Android, qui utilise libphonenumber
/// 9.0.42). La personne choisit un pays et saisit le numéro NATIONAL ; seule la forme E.164 sort. Aucun SMS, aucune
/// unicité. Le numéro n'est jamais journalisé ni mis dans un message d'erreur.
final class PhoneNumberKitTools: PhoneNumberTools {
    /// Genres de numéro acceptés : ligne fixe, mobile (les deux ne se distinguent que dans certains pays) et téléphonie
    /// par Internet. Plus strict que le ticket (§6.4 : « tout numéro de forme valide »), comme sur Android : un numéro
    /// surtaxé ou gratuit ferait payer ou joindre un service à chaque appel ou message d'un membre, dans une app qui
    /// montre le numéro à tout un cercle. Recommandation de la revue ; le PM peut l'assouplir en ajoutant un genre ici.
    static let allowedTypes: Set<PhoneNumberType> = [.fixedLine, .mobile, .fixedOrMobile, .voip]

    /// Créé une seule fois (initialisation paresseuse sûre entre fils) : le chargement des données est coûteux.
    private static let utility: PhoneNumberUtility = {
        // L'app ne lit jamais les contacts : on interdit explicitement à la bibliothèque d'y toucher.
        PhoneNumberUtility.canUseContactsFramework = false
        return PhoneNumberUtility()
    }()

    func countries(locale: Locale) -> [PhoneCountry] {
        Self.utility.allCountries()
            .filter { $0.count == 2 }
            .map { country(region: $0, locale: locale) }
            .sorted { $0.name.compare($1.name, locale: locale) == .orderedAscending }
    }

    func country(region: String, locale: Locale) -> PhoneCountry {
        let name = locale.localizedString(forRegionCode: region) ?? region
        let code = Self.utility.countryCode(for: region) ?? 0
        return PhoneCountry(region: region, callingCode: Int(code), name: name.isEmpty ? region : name)
    }

    func isSupportedRegion(_ region: String?) -> Bool {
        guard let upper = region?.uppercased(), upper.count == 2 else {
            return false
        }
        return Self.utility.countryCode(for: upper) != nil
    }

    func exampleHint(region: String) -> String? {
        guard let example = Self.utility.getExampleNumber(forCountry: region, ofType: .mobile) else {
            return nil
        }
        let international = Self.utility.format(example, toType: .international)
        let prefix = "+\(example.countryCode) "
        return international.hasPrefix(prefix) ? String(international.dropFirst(prefix.count)) : nil
    }

    func normalize(region: String, input: String) -> PhoneResult {
        guard let cleaned = PhoneInput.clean(input),
              let number = try? Self.utility.parse(cleaned, withRegion: region, ignoreType: false) else {
            return .invalid
        }
        guard Self.allowedTypes.contains(number.type) else {
            return .unsupportedType
        }
        let e164 = Self.utility.format(number, toType: .e164)
        // Dernier garde-fou, de la même forme que les règles Firestore : ne jamais rendre un numéro que le serveur
        // refuserait.
        guard ProfileDocument.isE164(e164) else {
            return .invalid
        }
        return .valid(e164: e164, region: regionOf(number, fallback: region))
    }

    func detectInternational(_ input: String) -> DetectedNumber? {
        guard let cleaned = PhoneInput.clean(input), cleaned.hasPrefix("+"),
              let number = try? Self.utility.parse(
                  cleaned, withRegion: PhoneInput.fallbackRegion, ignoreType: true) else {
            return nil
        }
        let region = regionOf(number, fallback: "")
        guard isSupportedRegion(region) else {
            return nil
        }
        // Le numéro national significatif : le zéro initial (certains pays) fait partie du numéro.
        let national = (number.leadingZero ? "0" : "") + String(number.nationalNumber)
        return DetectedNumber(region: region, national: national)
    }

    private func regionOf(_ number: PhoneNumber, fallback: String) -> String {
        Self.utility.getRegionCode(of: number)
            ?? number.regionID
            ?? Self.utility.mainCountry(forCode: number.countryCode)
            ?? fallback
    }
}
