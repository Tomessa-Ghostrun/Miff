import Foundation

/// Acces aux textes du String Catalog (`Localizable.xcstrings`, langue source : francais, repli : anglais).
///
/// Regle : AUCUNE chaine visible en dur dans le code. Un texte statique s'ecrit `Text("cle_snake_case")`
/// (SwiftLint refuse tout autre litteral dans `Text(...)`, `Button(...)`, etc.) ; un texte avec valeurs
/// (« Version : %1$@ ») passe par `L10n.format`, parce que la cle partagee avec Android ne doit pas changer
/// quand on y insere une valeur. Les cles suivent docs/contracts/i18n-keys.md.
enum L10n {
    static func string(_ key: String, bundle: Bundle = .main) -> String {
        bundle.localizedString(forKey: key, value: nil, table: nil)
    }

    /// Texte avec valeurs positionnelles (`%1$@`, `%2$lld`) : l'ordre peut changer selon la langue.
    static func format(_ key: String, _ arguments: CVarArg...) -> String {
        String(format: string(key), locale: Locale.current, arguments: arguments)
    }

    /// Texte avec pluriel (variantes `one`, `other`... du catalogue). Le nombre est passe au format.
    static func plural(_ key: String, count: Int) -> String {
        String.localizedStringWithFormat(string(key), count)
    }
}
