import Foundation

// Les états de l'écran Update et ce que « Publier » peut répondre, réduits à ce que l'écran sait dire (jamais un message technique).
// CONFIDENTIALITÉ : la photo prise vit dans l'état `preview`, en MÉMOIRE, et nulle part ailleurs (jamais dans un fichier ni dans la photothèque de l'app, dans un journal,
// un événement, l'état restauré par le système ni le presse-papiers). Son `description` ne montre rien.

/// Ce qu'une publication a donné.
enum PublishResult: Equatable {
    /// La photo est partagée dans `sharedIn` Miff(s).
    case published(sharedIn: Int)
    /// Premium, plusieurs lots : certaines Miffs ont la photo, `remaining` ne l'ont pas (un lot a échoué). « Réessayer » ne vise que
    /// `remaining`. Rien n'est annulé : une photo publiée dans une partie du cercle y reste.
    case partial(sharedIn: Int, total: Int, remaining: [String])
    /// Pas de position récente : rien n'est publié, la photo reste, « Réessayer » mesure de nouveau.
    case positionNotFound
    /// L'autorisation de la position a été retirée, ou la localisation du téléphone est éteinte : seuls les Réglages y changent quelque chose.
    case locationOff
    /// La position est autorisée mais seulement approximative (« Position précise » éteinte) : seuls les Réglages y changent quelque chose.
    case locationApproximate
    /// Le serveur n'a pas répondu à temps : la photo a PEUT-ÊTRE été publiée. La phrase le dit honnêtement, et la date de l'envoi est gardée
    /// pour le nettoyage.
    case unconfirmed
    /// Pas de réseau : rien n'est parti.
    case offline
    /// Je ne partage dans aucune de mes Miffs : pas de viseur, pas de publication ; la sortie est l'écran de la Miff.
    case sharingStopped
    /// Je partage, mais sous un texte de consentement qui ne couvre pas la position : rien n'est partagé avant mon accord.
    case needsAgreement
    case tooMany
    case tooSoon
    /// La photo attend depuis plus de 5 minutes.
    case stale
    /// Le serveur a dit non (règles, App Check, un autre téléphone tient la session) : « réessaie dans un instant ».
    case refused
    case failed
}

/// La phrase calme montrée sur la photo quand « Publier » n'a pas abouti. La photo est toujours là.
enum PreviewNotice: Equatable {
    case offline
    case positionNotFound
    case tooMany
    case tooSoon
    case stale
    case refused
    case failed
    /// L'autorisation de la position a été retirée, ou la localisation est éteinte : « Ouvrir les réglages ».
    case locationOff
    /// La position n'est qu'approximative : « Active la position précise pour cette fonction » + « Ouvrir les réglages ».
    case locationApproximate
    /// Pas de réponse à temps : « On n'a pas pu confirmer. Regarde dans ton Feed. »
    case unconfirmed
    case needsAgreement
    case sharingStopped
    case partial(sharedIn: Int, total: Int)

    /// Le texte du catalogue (jamais d'erreur technique) ; l'icône est choisie par l'écran.
    var textKey: String {
        switch self {
        case .offline:
            return "update_notice_offline"
        case .positionNotFound:
            return "update_notice_position"
        case .locationOff:
            return "update_notice_location_off"
        case .locationApproximate:
            return "ios_update_notice_location_precise"
        case .unconfirmed:
            return "update_notice_unconfirmed"
        case .tooMany:
            return "update_notice_too_many"
        case .tooSoon:
            return "update_notice_too_soon"
        case .stale:
            return "update_preview_stale"
        case .refused, .failed:
            return "update_notice_failed"
        case .needsAgreement:
            return "update_notice_agreement"
        case .sharingStopped:
            return "update_stopped_body"
        case .partial:
            return "update_notice_partial"
        }
    }

    /// Sur ces deux phrases, « Publier » ne peut plus rien : il faut reprendre une photo, ou revenir plus tard.
    var blocksPublishing: Bool {
        self == .stale || self == .tooMany
    }
}

extension PreviewNotice {
    /// La phrase calme d'une réponse qui n'a pas publié (une publication réussie, sans phrase, est traitée par l'appelant).
    init(result: PublishResult) {
        switch result {
        case .offline:
            self = .offline
        case .positionNotFound:
            self = .positionNotFound
        case .tooMany:
            self = .tooMany
        case .tooSoon:
            self = .tooSoon
        case .stale:
            self = .stale
        case .refused:
            self = .refused
        case .locationOff:
            self = .locationOff
        case .locationApproximate:
            self = .locationApproximate
        case .unconfirmed:
            self = .unconfirmed
        case .needsAgreement:
            self = .needsAgreement
        case .sharingStopped:
            self = .sharingStopped
        case .partial(let shared, let total, _):
            self = .partial(sharedIn: shared, total: total)
        case .published, .failed:
            self = .failed
        }
    }
}

/// L'aperçu d'une photo prise : « Recommencer » et « Publier ».
struct PreviewState: Equatable, CustomStringConvertible {
    let photo: UpdatePhoto
    var publishing = false
    var notice: PreviewNotice?
    /// Après un succès partiel : les Miffs que la photo n'a pas atteintes (nil = toutes).
    var remaining: [String]?

    init(photo: UpdatePhoto, publishing: Bool = false, notice: PreviewNotice? = nil, remaining: [String]? = nil) {
        self.photo = photo
        self.publishing = publishing
        self.notice = notice
        self.remaining = remaining
    }

    var canPublish: Bool {
        !publishing && !(notice?.blocksPublishing ?? false)
    }

    var description: String {
        "PreviewState(redacted)"
    }
}

/// Ce que montre l'écran Update.
enum UpdateStage: Equatable {
    /// Pas encore ouvert (ou quitté) : rien n'est dessiné, la caméra est éteinte.
    case idle
    /// A : le pop-in qui dit pourquoi, avant la boîte du système.
    case explain
    /// C : la caméra est désactivée pour Miff (refus) : « Ouvrir les réglages », aucune alternative (D31).
    case permissionDisabled
    /// F : la caméra est restreinte par l'appareil : « Fermer » seulement.
    case cameraRestricted
    /// D : la caméra ne peut pas servir maintenant (une autre application, une politique, aucune caméra).
    case cameraUnavailable
    /// J : le partage est arrêté dans toutes mes Miffs : pas de viseur, aucune autorisation demandée.
    case sharingStopped
    /// Le viseur. `prepareFailed` : la phrase calme sous le viseur quand la dernière photo n'a pas pu être préparée.
    case viewfinder(showTip: Bool, prepareFailed: Bool)
    case preview(PreviewState)

    /// La barre en verre suit ce qui est DERRIÈRE elle : la variante sombre (icônes blanches) seulement sur le viseur et l'aperçu, dont les
    /// fonds sont sombres ; sur les pop-ins clairs (autorisation, caméra indisponible, partage arrêté) et l'état vide, c'est le verre clair.
    var hasDarkBackground: Bool {
        switch self {
        case .viewfinder, .preview:
            return true
        default:
            return false
        }
    }

    /// La variante de verre de l'onglet en cours : la sombre seulement sur l'onglet Update, et seulement quand son étape est sombre.
    func glassVariant(updateTabSelected: Bool) -> TabBarVariant {
        updateTabSelected && hasDarkBackground ? .onPhoto : .light
    }
}
