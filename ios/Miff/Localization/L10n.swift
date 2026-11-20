import Foundation

/// Acces aux textes du String Catalog (`Localizable.xcstrings`, langue source : francais, repli : anglais).
///
/// Regle : AUCUNE chaine visible en dur dans le code. Un texte statique s'ecrit `Text("cle_snake_case")`
/// (SwiftLint refuse tout autre litteral dans `Text(...)`, `Button(...)`, etc.) ; un texte avec valeurs
/// (« Version : %1$@ ») passe par `L10n.format`, parce que la cle partagee avec Android ne doit pas changer
/// quand on y insere une valeur. Les cles suivent docs/contracts/i18n-keys.md.
enum L10n {
    /// `bundle` : par défaut le paquet de la LANGUE CHOISIE dans Réglages > Langue (`AppLanguage.bundle` : le paquet principal quand c'est
    /// la langue du téléphone, `fr.lproj` ou `en.lproj` sinon). Le texte suit donc le choix, même calculé hors d'une vue.
    static func string(_ key: String, bundle: Bundle = AppLanguage.bundle) -> String {
        bundle.localizedString(forKey: key, value: nil, table: nil)
    }

    /// Texte avec valeurs positionnelles (`%1$@`, `%2$lld`) : l'ordre peut changer selon la langue.
    static func format(_ key: String, _ arguments: CVarArg...) -> String {
        String(format: string(key), locale: AppLanguage.locale, arguments: arguments)
    }

    /// Texte avec pluriel (variantes `one`, `other`... du catalogue). Le nombre qui decide de la forme est PREMIER (`%1$lld`) ; les
    /// autres valeurs (`%2$lld`...), s'il y en a (« 2 lieux sur 3 »), suivent dans l'ordre. Dans le catalogue, un pluriel a plusieurs
    /// valeurs s'ecrit avec des `substitutions` (le pluriel ne porte que sur le premier nombre).
    static func plural(_ key: String, count: Int, bundle: Bundle = AppLanguage.bundle, _ others: CVarArg...) -> String {
        let arguments: [CVarArg] = [count] + others
        return String(format: string(key, bundle: bundle), locale: AppLanguage.locale, arguments: arguments)
    }
}
