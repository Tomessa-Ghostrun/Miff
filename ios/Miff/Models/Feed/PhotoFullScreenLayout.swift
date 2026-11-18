import CoreGraphics
import Foundation

// La mise en page du plein écran de la photo d'un proche (FEED-8, retours de la recette TestFlight). VRAI plein écran, SANS AUCUNE MARGE (exigence du PM) : la photo (`scaledToFill`)
// REMPLIT TOUT l'écran, zones sûres comprises, quel que soit son rapport (recadrage centré quand il diffère de celui de l'écran) ; la LÉGENDE, « Position mesurée… » et « Voir où c'était » sont POSÉES PAR-DESSUS, en bas,
// sur un voile sombre (texte blanc, contraste d'au moins 4,5:1 même sur la photo la plus claire), dans la zone sûre du bas ; leur zone est BORNÉE (jamais
// plus de 45 % de la hauteur) : en grand texte ou en paysage elle défile, et la photo n'est jamais masquée en entier. La croix et l'icône
// « Enregistrer cette photo » restent en haut. Du Swift pur : aucune mesure de vue, aucune lecture, aucune écriture.

enum PhotoFullScreenLayout {
    /// Part maximale de la hauteur que prend la légende (au-delà, elle défile).
    static let footerMaxShare: CGFloat = 0.45
    /// Hauteur du dégradé qui fond la photo dans le voile, au-dessus du texte.
    static let fadeHeight: CGFloat = 40
    /// Les espaces de la légende, SERRÉS pour qu'elle soit collée en bas, juste au-dessus de la zone sûre du bas (retour du PM sur le build 27 ;
    /// avant : 8 pt de marge basse et 8 pt entre la légende et le bouton). Le dégradé, lui, reste.
    static let captionTopPadding: CGFloat = 8
    static let captionSpacing: CGFloat = 4

    /// PLANCHERS de sécurité des zones sûres (portrait) : ils tiennent même quand le plein écran annonce des marges nulles (cas du `fullScreenCover` sur
    /// un vrai appareil, TestFlight 32). Haut : au moins l'encoche (47 pt) + 12 pt de marge pour la croix et l'icône ; bas : au moins la barre
    /// d'accueil (34 pt) + 16 pt de marge sous « Voir où c'était ».
    static let topInsetFloor: CGFloat = 47
    static let topExtraMargin: CGFloat = 12
    static let bottomInsetFloor: CGFloat = 34
    static let bottomExtraMargin: CGFloat = 16
    /// L'opacité du voile noir derrière le texte.
    static let scrimAlpha: Double = 0.70
    /// Le contrat de lisibilité (WCAG AA, texte courant).
    static let minimumContrast: Double = 4.5

    /// La hauteur du contenu de la légende en texte normal (titre, position, bouton de 44 pt, espaces), SANS la zone sûre du bas : volontairement
    /// large. Elle grandit avec la taille du texte (`@ScaledMetric` côté écran) ; au-delà de 45 % de la hauteur, la légende défile.
    static let footerEstimate: CGFloat = 170

    /// La même estimation QUAND il y a une description : « Photo de … » passe alors au corps 16 (22 pt de haut au lieu de 26) et la description porte
    /// seule le grand titre. Le titre et le lieu sont comptés sur une ligne chacun avec de la marge, le bouton à 44 pt : sans cette réduction,
    /// presque toute description ferait défiler le pied sur un iPhone de 812 pt (une zone de voile vide, jusqu'à 45 % de l'écran).
    static let footerEstimateWithDescription: CGFloat = 130

    /// Largeur moyenne d'un caractère de la description, en part du corps de la police : la description est en GRAS (titre 20/26), donc plus large
    /// que le corps normal (texte français, emojis compris : un emoji compte 2 unités UTF-16 pour environ 1 corps de large). Un peu large pour
    /// couvrir la perte des fins de ligne (mots qui ne tiennent pas).
    static let averageGlyphWidth: CGFloat = 0.56

    /// La hauteur ajoutée au pied par une description de `unitCount` unités UTF-16 (0 quand il n'y en a pas) : le nombre de lignes estimé pour
    /// `width` de large, à `lineHeight` chacune, plus l'espace sous le texte. Le corps et l'interligne passés sont DÉJÀ agrandis avec la taille du
    /// texte du système (titre 20 pt, interligne 26 pt avant agrandissement) : plus la police est grande, plus la description pèse, jusqu'à faire
    /// défiler le pied. Le plus long texte (140 unités) ne fait jamais plus d'une quinzaine de lignes à la plus grande taille : le pied, borné à
    /// 45 %, défile.
    static func descriptionHeight(unitCount: Int, fontSize: CGFloat, lineHeight: CGFloat, width: CGFloat) -> CGFloat {
        guard unitCount > 0, fontSize > 0, lineHeight > 0, width > 0 else {
            return 0
        }
        let perLine = max(1, Int((width / (fontSize * averageGlyphWidth)).rounded(.down)))
        let lines = (unitCount + perLine - 1) / perLine
        return CGFloat(lines) * lineHeight + captionSpacing
    }

    /// La légende défile-t-elle ? Oui quand son contenu estimé (zone sûre du bas comprise) dépasse sa hauteur maximale (grand texte, paysage).
    static func footerScrolls(estimate: CGFloat, screenHeight: CGFloat) -> Bool {
        estimate > footerMaxHeight(screenHeight: screenHeight)
    }

    /// La hauteur maximale de la zone de la légende : au plus 45 % de la hauteur de l'écran. Plus haut, elle défile.
    static func footerMaxHeight(screenHeight: CGFloat) -> CGFloat {
        max(screenHeight, 0) * footerMaxShare
    }

    /// La taille de l'écran ENTIER : la zone visible (celle qu'un lecteur de géométrie qui respecte les zones sûres annonce) plus les zones sûres
    /// (haut, bas, côtés). Le contenu qui ignore les zones sûres a cette taille.
    static func screenSize(
        visible: CGSize, top: CGFloat, bottom: CGFloat, leading: CGFloat, trailing: CGFloat
    ) -> CGSize {
        CGSize(
            width: visible.width + leading + trailing,
            height: visible.height + top + bottom
        )
    }

    /// La taille de la photo qui REMPLIT la zone (aspect « fill », `scaledToFill`) : le plus grand des deux rapports, de sorte que la photo touche les
    /// QUATRE bords de la zone, quel que soit le rapport photo / écran, et quelle que soit la taille propre de l'image (jamais limitée par elle : la
    /// zone décide). Quand les rapports diffèrent, elle DÉPASSE de la zone d'un côté (recadrage centré, voir `croppedShare`) : jamais de bande vide.
    static func filledSize(imageSize: CGSize, in area: CGSize) -> CGSize {
        guard imageSize.width > 0, imageSize.height > 0, area.width > 0, area.height > 0 else {
            return .zero
        }
        let scale = max(area.width / imageSize.width, area.height / imageSize.height)
        return CGSize(width: imageSize.width * scale, height: imageSize.height * scale)
    }

    /// La part de la photo (de 0 à moins de 1) qui sort de l'écran avec le recadrage centré : 0 quand la photo a le rapport de l'écran. Sert aux
    /// tests et à la recette (« combien de la photo est coupé sur un écran plus allongé »).
    static func croppedShare(imageSize: CGSize, in area: CGSize) -> CGFloat {
        let filled = filledSize(imageSize: imageSize, in: area)
        guard filled.width > 0, filled.height > 0 else {
            return 0
        }
        return max(0, 1 - (area.width * area.height) / (filled.width * filled.height))
    }

    /// Le voile léger du HAUT (la barre d'état et les boutons sont maintenant sur la photo elle-même) : opacité de départ, en part de celle du voile
    /// du design system (`colorPhotoCaptionScrim`, 0,70), qui s'efface vers le bas sur la hauteur de la zone sûre haute.
    static let topScrimOpacity: Double = 0.6

    /// Le contraste d'un texte BLANC posé sur le voile noir d'opacité `alpha`, dans le PIRE cas (une photo toute blanche dessous), selon WCAG 2.x.
    static func whiteTextContrast(overScrimAlpha alpha: Double) -> Double {
        let gray = max(0, min(1, 1 - alpha))
        let linear = gray <= 0.04045 ? gray / 12.92 : pow((gray + 0.055) / 1.055, 2.4)
        return 1.05 / (linear + 0.05)
    }
}

/// Le niveau typographique d'une ligne de texte du pied (retour du PM sur la recette Android de la description).
enum PhotoFooterTextLevel: Equatable {
    /// Le grand titre : gras, 20 pt (`textTitleBold20`).
    case heading
    /// Le texte courant : normal, 16 pt (`textBodyRegular`).
    case body
}

/// Les lignes du pied du plein écran, de haut en bas.
enum PhotoFooterRow: Hashable {
    /// La description de l'auteur (facultative).
    case description
    /// « Photo de {Prénom} · {heure} ».
    case title
    /// « Position mesurée … », et « Voir où c'était » (quand la photo a un lieu).
    case place
}

extension PhotoFullScreenLayout {
    /// L'ordre du pied : la DESCRIPTION d'abord (quand il y en a une), puis « Photo de … », puis le lieu. Sans description, le pied est exactement celui
    /// d'avant : aucune ligne vide, aucun libellé « pas de description ».
    static func footerRows(hasDescription: Bool, hasPlace: Bool) -> [PhotoFooterRow] {
        (hasDescription ? [PhotoFooterRow.description] : []) + [.title] + (hasPlace ? [.place] : [])
    }

    /// Le niveau de la DESCRIPTION de l'auteur : toujours le grand titre (c'est elle que le proche vient lire).
    static let descriptionLevel = PhotoFooterTextLevel.heading

    /// Le niveau de « Photo de {Prénom} · {heure} » : le grand titre comme avant quand il n'y a PAS de description (pied inchangé) ; le texte
    /// courant, plus petit, quand une description existe (elle prend alors le titre). L'en-tête VoiceOver reste « Photo de … » dans les deux cas.
    static func titleLevel(hasDescription: Bool) -> PhotoFooterTextLevel {
        hasDescription ? .body : .heading
    }

    /// Le décalage de la croix et de l'icône depuis le bord HAUT de l'écran : le plus grand de la zone sûre lue et du plancher (47 pt), plus 12 pt.
    /// Le plancher ne vaut qu'en portrait (en paysage il n'y a pas d'encoche en haut : le plancher ferait perdre de la place pour rien).
    static func topMargin(safeInset: CGFloat, isPortrait: Bool) -> CGFloat {
        (isPortrait ? max(safeInset, topInsetFloor) : max(safeInset, 0)) + topExtraMargin
    }

    /// Le décalage de la légende et du bouton depuis le bord BAS de l'écran : le plus grand de la zone sûre lue et du plancher (34 pt), plus 16 pt.
    static func bottomMargin(safeInset: CGFloat, isPortrait: Bool) -> CGFloat {
        (isPortrait ? max(safeInset, bottomInsetFloor) : max(safeInset, 0)) + bottomExtraMargin
    }

    /// Vrai quand l'écran est plus haut que large (les planchers ne valent qu'alors).
    static func isPortrait(_ size: CGSize) -> Bool {
        size.height >= size.width
    }
}
