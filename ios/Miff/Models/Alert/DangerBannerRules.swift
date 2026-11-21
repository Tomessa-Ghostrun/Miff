import Foundation

// Quand le bandeau de l'alerte en cours montre le lien « Voir la Miff {nom} » (calcul pur, testé). Le titre du bandeau (« Mettre fin à l'alerte ? »,
// texte du PM) ne nomme plus la Miff d'origine : le lien est donc le seul endroit du bandeau qui la nomme. Il est montré dès que la personne a PLUSIEURS
// Miffs, y compris quand le Feed affiche déjà la Miff d'origine : une alerte ne part que dans UNE Miff, et la personne doit pouvoir la lire (réduction du
// risque de « fausse sécurité », décision du PM suivant la relecture de la PR Android jumelle).

enum DangerBannerRules {
    /// Vrai quand le nom de la Miff d'origine est connu ET (la Miff d'origine n'est pas celle du Feed OU la personne a plusieurs Miffs). Avec une seule Miff
    /// il n'y a rien à préciser : le lien n'apparaît pas.
    static func showsSeeMiff(originMiffId: String, activeMiffId: String?, miffCount: Int, nameKnown: Bool) -> Bool {
        guard nameKnown else {
            return false
        }
        return originMiffId != activeMiffId || miffCount > 1
    }
}
