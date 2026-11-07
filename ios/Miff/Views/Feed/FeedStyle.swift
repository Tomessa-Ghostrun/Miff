import SwiftUI
import UIKit

// À quoi ressemble un NIVEAU. Sur l'anneau d'un avatar, le liseré et l'étiquette d'une carte, la COULEUR est le seul signal : ni
// coche, ni horloge, ni « ! » (décision du PM, 2026-11-06) ; les mots de l'état restent, et le lecteur d'écran les lit. Les teintes
// foncées du texte (`colorStatus*Text`) sont celles qui atteignent le contraste AA sur les teintes à 30 %. Une personne dont le
// statut n'est pas connu est GRISE : jamais verte quand personne ne sait.

extension StatusLevel {
    /// L'anneau d'un avatar, le liseré d'une carte.
    var ring: Color {
        switch self {
        case .green:
            return colorStatusSafe.color
        case .orange:
            return colorStatusWarning.color
        case .red:
            return colorStatusDanger.color
        case .neutral:
            return colorStatusNeutral.color
        }
    }

    /// La teinte à 30 % derrière une étiquette ou une bulle.
    var tint: Color {
        switch self {
        case .green:
            return colorStatusSafeTint.color
        case .orange:
            return colorStatusWarningTint.color
        case .red:
            return colorStatusDangerTint.color
        case .neutral:
            return colorStatusNeutralTint.color
        }
    }

    /// La couleur du texte et du pictogramme posés sur `tint`.
    var onTint: Color {
        switch self {
        case .green:
            return colorStatusSafeText.color
        case .orange:
            return colorStatusWarningText.color
        case .red:
            return colorStatusDangerText.color
        case .neutral:
            return colorTextForm.color
        }
    }
}

/// La mémoire des photos déjà décodées (en mémoire seulement, vidée par le système en cas de besoin, jamais sur le disque).
enum FeedAvatarCache {
    private static let cache = NSCache<NSData, UIImage>()

    static func image(for jpeg: Data) -> UIImage? {
        let key = jpeg as NSData
        if let known = cache.object(forKey: key) {
            return known
        }
        guard let decoded = UIImage(data: jpeg) else {
            return nil
        }
        cache.setObject(decoded, forKey: key)
        return decoded
    }
}

/// La photo ronde (ou les initiales) d'une personne avec l'anneau de son niveau : la COULEUR SEULE, aucun pictogramme dessus. Muette
/// pour le lecteur d'écran (le parent dit qui c'est et dans quel état). La photo vient d'un autre téléphone : elle n'est décodée que
/// si elle respecte la taille que les règles imposent.
struct FeedAvatar: View {
    let jpeg: Data?
    let initials: String
    let level: StatusLevel
    var size: CGFloat = Spacing.memberAvatar

    /// Décodée UNE fois par photo (le résultat est gardé en mémoire, clé = les octets) : un défilement ou une minute qui passe ne
    /// redécode rien.
    private var image: UIImage? {
        guard let jpeg, !jpeg.isEmpty, jpeg.count <= ProfileDocument.maxAvatarBytes else {
            return nil
        }
        return FeedAvatarCache.image(for: jpeg)
    }

    var body: some View {
        ZStack {
            Circle().fill(colorStatusNeutral.color)
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(width: size, height: size)
                    .clipShape(Circle())
            } else {
                Text(verbatim: initials)
                    .miffTextStyle(textBodyBold14)
                    .foregroundColor(colorTextForm.color)
            }
        }
        .frame(width: size, height: size)
        .overlay(Circle().strokeBorder(level.ring, lineWidth: strokeAvatarRing))
        .accessibilityHidden(true)
    }
}
