import Foundation

// SÉCURITÉ, pour ce fichier : prénoms (par leurs initiales) et photos des membres sont des données personnelles. Ils vivent en mémoire,
// pour la durée de la session, jamais sur le disque, jamais journalisés, jamais dans un événement Analytics. `description` ne montre rien.

/// Ce que la carte d'une Miff du Profil montre d'UN membre : sa photo, ou ses initiales. Rien d'autre (ni nom entier, ni numéro).
struct MemberThumb: Equatable, CustomStringConvertible {
    let uid: String
    let initials: String
    /// JPEG carré de 20 000 octets au plus (`ProfileDocument.maxAvatarBytes`, déjà vérifié à la lecture), ou nil : les initiales.
    let avatarJpeg: Data?

    var description: String {
        "MemberThumb(redacted)"
    }

    init(uid: String, initials: String, avatarJpeg: Data?) {
        self.uid = uid
        self.initials = initials
        self.avatarJpeg = avatarJpeg
    }

    init(member: MiffMember) {
        self.init(
            uid: member.uid,
            initials: Initials.of(first: member.firstName, last: member.lastName),
            avatarJpeg: member.avatarJpeg
        )
    }
}

/// Ce que l'on sait des membres d'une Miff pour sa carte : le nombre, et les premières photos seulement (de quoi remplir la rangée).
struct MemberPreview: Equatable, CustomStringConvertible {
    /// Combien de photos on garde par Miff : de quoi remplir la rangée (5 au plus), pas plus (au plus 5 x 20 Ko par Miff en mémoire).
    static let keptThumbs = 5

    let count: Int
    let thumbs: [MemberThumb]

    var description: String {
        "MemberPreview(redacted)"
    }

    init(count: Int, thumbs: [MemberThumb]) {
        self.count = count
        self.thumbs = Array(thumbs.prefix(Self.keptThumbs))
    }

    /// Depuis les fiches des membres, dans l'ordre où la Miff les montre (le créateur d'abord).
    init(members: [MiffMember]) {
        self.init(count: members.count, thumbs: members.prefix(Self.keptThumbs).map(MemberThumb.init(member:)))
    }
}

/// Ce que dit la carte d'une Miff de ses membres.
enum MiffCardMembers: Equatable {
    /// Pas encore lus : la carte montre son nom et réserve la place de la rangée (pas de saut quand elle arrive).
    case loading
    /// Impossible à lire (hors ligne sans cache, refusé) : la carte garde son nom et sa phrase d'aide, sans photos, sans message d'erreur.
    case unavailable
    case known(MemberPreview)
}

/// La rangée de photos : jusqu'à `maxVisible` pastilles, dont la dernière est « +N » quand il y a plus de membres que de places.
enum MemberStack {
    /// Les pastilles montrées normalement (la maquette en montre 4 ; « 3 à 5 », décision du PM, 2026-11-10).
    static let maxVisible = 5
    /// À une grande taille de texte, les pastilles grossissent : on en montre moins pour que la rangée tienne sur une ligne.
    static let maxVisibleLargeText = 3

    struct Layout: Equatable {
        /// Les photos (ou initiales) montrées.
        let visible: Int
        /// Le N de « +N » ; 0 : pas de pastille « +N ».
        let extra: Int
    }

    /// 4 membres avec 5 places : 4 photos. 8 membres avec 5 places : 4 photos et « +4 » (la pastille « +N » prend la 5e place).
    static func layout(total: Int, maxVisible: Int) -> Layout {
        let places = max(maxVisible, 1)
        let count = max(total, 0)
        if count <= places {
            return Layout(visible: count, extra: 0)
        }
        return Layout(visible: places - 1, extra: count - (places - 1))
    }
}

/// Le carrousel de Miffs : une carte par Miff, puis TOUJOURS « Créer une Miff » en dernier. Les règles de pagination, sans écran.
enum CarouselPaging {
    /// Le nombre de pages : une par Miff, plus la carte de création.
    static func pageCount(miffs: Int) -> Int {
        max(miffs, 0) + 1
    }

    /// La page où le carrousel s'ouvre : la Miff que le Feed affiche (comme Android), sinon la première.
    static func startPage(miffIds: [String], activeId: String?) -> Int {
        guard let activeId, let index = miffIds.firstIndex(of: activeId) else {
            return 0
        }
        return index
    }

    static func clamp(_ page: Int, count: Int) -> Int {
        min(max(page, 0), max(count - 1, 0))
    }

    /// La page où le geste s'arrête. Un glissement qui dépasse un quart de page, ou un élan (la fin prévue du geste) qui dépasse un tiers
    /// de page, tourne la page ; sinon la carte revient. Un glissement vers la gauche (négatif) montre la page SUIVANTE. Jamais hors des bornes.
    static func settle(
        current: Int,
        translation: CGFloat,
        predictedEnd: CGFloat,
        pageWidth: CGFloat,
        count: Int
    ) -> Int {
        guard pageWidth > 0 else {
            return clamp(current, count: count)
        }
        let moved = abs(translation) > pageWidth * 0.25 || abs(predictedEnd) > pageWidth * 0.33
        guard moved else {
            return clamp(current, count: count)
        }
        let decisive = abs(predictedEnd) > abs(translation) ? predictedEnd : translation
        return clamp(current + (decisive < 0 ? 1 : -1), count: count)
    }

    /// La page voisine vers laquelle un glissement commence (celle qui va apparaître), nil au bout du carrousel.
    static func neighbour(current: Int, translation: CGFloat, count: Int) -> Int? {
        let target = current + (translation < 0 ? 1 : -1)
        return target >= 0 && target < count ? target : nil
    }
}

/// « Créer une Miff » au Profil : le même choix que le sélecteur du Feed (décision du PM, 2026-11-08), l'offre pour un compte gratuit
/// qui a déjà une Miff, le parcours normal sinon (Miff+, formule inconnue, aucune Miff).
enum ProfileCarouselRules {
    static func createOutcome(for state: DirectoryState) -> FeedSelectorOutcome {
        if case .ready(let ready) = state {
            return FeedSelectorRules.outcome(for: ready)
        }
        return .openFlow
    }
}
