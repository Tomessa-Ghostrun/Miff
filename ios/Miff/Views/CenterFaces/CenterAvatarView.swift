import SwiftUI

// CNC-L et CNC-I (ticket docs/tickets/centre-notifications-complet.md §5 et §8.5) : l'image ronde d'une ligne du centre de notifications : la photo d'un
// proche (ou ses initiales), ma propre photo pour mes rappels, ou le LOGO de Miff (la Miff entière, quelqu'un qu'on ne peut pas nommer). Elle est
// DÉCORATIVE pour VoiceOver : la ligne se lit « Léa a donné des nouvelles, 14:05, non lu » en UN élément, le prénom est déjà dans le texte.
// La photo n'est jamais gardée ici : elle est dessinée depuis la mémoire (voir `CenterFaces`), décodée à la taille du rond par `AvatarDecoding` (jamais en
// pleine taille).

/// Les mesures du logo en avatar : le glyphe « M + i + cœur » de l'icône, dessiné PLUS GRAND que dans l'icône pour que le cœur reste un cœur à 40 pt.
/// Mêmes nombres que `ic_launcher_foreground` agrandi dans `ic_miff_logo_avatar.xml` d'Android (×1,3 autour du centre du glyphe, décalé de 1,25).
enum LogoAvatarGeometry {
    /// Le diamètre d'une ligne du centre.
    static let size: CGFloat = 40
    /// L'agrandissement du glyphe par rapport à l'icône.
    static let glyphScale: CGFloat = 1.3
    /// Le point fixe de l'agrandissement, dans le canevas de 108 unités de l'icône.
    static let pivotX: CGFloat = 54
    static let pivotY: CGFloat = 52.75
    /// Le décalage vertical après l'agrandissement, en unités du canevas.
    static let shiftY: CGFloat = 1.25
    /// Les bords du glyphe dans le canevas de 108 unités (le M et le i, puis le cœur au-dessus).
    static let glyphLeft: CGFloat = 31.34
    static let glyphRight: CGFloat = 77.5
    static let glyphTop: CGFloat = 28.31
    static let glyphBottom: CGFloat = 77.29
    static let canvas: CGFloat = 108

    /// Où un point du glyphe tombe après l'agrandissement.
    static func placed(x: CGFloat, y: CGFloat) -> CGPoint {
        CGPoint(
            x: pivotX + (x - pivotX) * glyphScale,
            y: pivotY + (y - pivotY) * glyphScale + shiftY
        )
    }

    /// Le coin de la boîte du glyphe le plus loin du centre du rond, en part du rayon : le glyphe doit rester bien à l'intérieur du disque (environ
    /// 80 %, 85 % au plus : la boîte est un peu plus large que le tracé, dont le coin le plus loin est à 80 % comme sur Android).
    static var farthestCornerShareOfRadius: CGFloat {
        let center = canvas / 2
        let corners = [
            placed(x: glyphLeft, y: glyphTop), placed(x: glyphRight, y: glyphTop),
            placed(x: glyphLeft, y: glyphBottom), placed(x: glyphRight, y: glyphBottom)
        ]
        let farthest = corners.map { hypot($0.x - center, $0.y - center) }.max() ?? 0
        return farthest / center
    }
}

/// Le logo de Miff sur son rond bleu (`#043CD1`) : le glyphe du M, du i et du cœur, sans carré arrondi ni masque (l'icône de l'app ne peut pas servir
/// telle quelle). Bleu, blanc et jaune-vert se lisent sur les deux thèmes : pas de variante sombre. Les couleurs viennent des jetons du design system.
struct MiffLogoAvatar: View {
    var size: CGFloat = LogoAvatarGeometry.size

    var body: some View {
        ZStack {
            Circle().fill(colorActionPrimary.color)
            glyph
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityHidden(true)
    }

    private var glyph: some View {
        ZStack {
            VectorShape(
                commands: LogoPaths.markLetters, width: LogoPaths.markCanvas, height: LogoPaths.markCanvas
            )
            .fill(colorTextOnAction.color)
            VectorShape(commands: LogoPaths.markHeart, width: LogoPaths.markCanvas, height: LogoPaths.markCanvas)
                .fill(colorBrandPrimary.color)
        }
        .frame(width: size, height: size)
        .scaleEffect(
            LogoAvatarGeometry.glyphScale,
            anchor: UnitPoint(
                x: LogoAvatarGeometry.pivotX / LogoAvatarGeometry.canvas,
                y: LogoAvatarGeometry.pivotY / LogoAvatarGeometry.canvas
            )
        )
        .offset(y: LogoAvatarGeometry.shiftY * size / LogoAvatarGeometry.canvas)
    }
}

/// L'image ronde d'une ligne du centre.
struct CenterAvatarView: View {
    let avatar: CenterAvatar

    var body: some View {
        switch avatar {
        case .logo:
            MiffLogoAvatar()
        case .placeholder:
            // La photo n'est pas connue ENCORE : un rond neutre, ni lettres ni logo (rien ne clignote quand elle arrive).
            Circle()
                .fill(colorActionPrimaryTint10.color)
                .frame(width: LogoAvatarGeometry.size, height: LogoAvatarGeometry.size)
                .accessibilityHidden(true)
        case .initials(let text):
            MiffAvatar(jpeg: nil, initials: text, size: LogoAvatarGeometry.size)
        case .photo(let data, let initials):
            MiffAvatar(jpeg: data, initials: initials, size: LogoAvatarGeometry.size)
        }
    }
}
