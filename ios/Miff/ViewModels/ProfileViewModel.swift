import Foundation
#if canImport(Combine)
import Combine
#endif

// SÉCURITÉ, pour ce fichier : prénom, nom, numéro de téléphone et photo sont des données personnelles. Elles vivent dans
// l'état de l'écran et dans le brouillon local seulement, partent vers Firestore par `UserProfileRepository` et nulle
// part ailleurs : jamais journalisées, jamais dans un événement Analytics, jamais dans un message d'erreur. Ces étapes
// n'envoient aucun événement d'analyse (ticket §6.3, « Analyse »).

/// `full` = profil puis téléphone (nouveau compte). `phoneOnly` = compte sans numéro : l'étape téléphone seule (ligne 2b).
enum OnboardingMode: Equatable {
    case full
    case phoneOnly
}

enum ProfileMessage: Equatable {
    /// Pas de réseau : rien n'a été écrit, les champs sont gardés.
    case network
    /// Le serveur n'a pas pris l'écriture (ou quelque chose d'inattendu) : les champs sont gardés, réessayer.
    case saveFailed
    /// Plusieurs refus de suite : ce n'est pas un accroc. Conseil bienveillant de revenir plus tard ; tout est gardé.
    case tryLater
    /// Un ancien compte qu'on ne peut pas compléter d'ici (il appartient à une Miff). Neutre, avec l'adresse d'aide.
    case accountUnavailable
    /// Le fichier choisi n'est pas une image utilisable.
    case photoUnusable
}

/// La photo en cours de recadrage : gardée ici (pas dans l'écran) pour qu'une rotation garde zoom et position.
struct CropState {
    let photo: any DecodedPhoto
    var zoom: Double
    /// Centre du carré, en pixels de la photo.
    var centerX: Double
    var centerY: Double
    var isEncoding = false
}

struct ProfileUiState {
    var mode: OnboardingMode
    var step: DraftStep
    var firstName = ""
    var lastName = ""
    /// La dernière frappe aurait dépassé 50 caractères : « 50 caractères maximum. »
    var firstNameLimitHit = false
    var lastNameLimitHit = false
    var avatar: Data?
    var crop: CropState?
    var regionCode: String
    var phoneText = ""
    /// Montré après un toucher sur la flèche avec un numéro qui ne semble pas correct. Retiré à la modification suivante.
    var phoneInvalid = false
    /// Le numéro est valide mais d'un genre refusé (surtaxé, gratuit...) : une phrase plus précise.
    var phoneTypeRefused = false
    var isWriting = false
    var message: ProfileMessage?

    var canContinueFromProfile: Bool {
        ProfileForm.isNameComplete(firstName) && ProfileForm.isNameComplete(lastName)
    }

    /// La flèche de l'étape téléphone reste grise tant qu'un chiffre n'a pas été saisi (critère TEL1).
    var canSubmitPhone: Bool {
        PhoneInput.hasDigit(phoneText) && !isWriting
    }
}

/// Tout ce dont le view model du profil a besoin.
struct ProfileDependencies {
    let session: SessionManager
    let pending: PendingConsentStore
    let drafts: ProfileDraftStore
    let profiles: UserProfileRepository
    let avatars: AvatarCodec
    let network: NetworkMonitor
    let phoneTools: PhoneNumberTools
    let deviceRegion: () -> String?
}

/// Les deux étapes de la création du profil (ticket Onboarding §6.3 et §6.4) et l'étape téléphone seule pour un ancien
/// compte.
///
/// - AUCUNE écriture à l'étape profil : noms, photo, pays et numéro sont gardés dans un brouillon local (jamais le mot de
///   passe) ;
/// - UNE écriture, à la fin de l'étape téléphone, avec toutes les clés du profil (`UserProfileRepository.create`) ; l'écran
///   suivant ne s'ouvre qu'une fois le serveur ayant confirmé ; hors ligne, un message calme et les champs restent ;
/// - le brouillon est effacé après la confirmation (et à chaque déconnexion, voir `AppEnvironment`).
@MainActor
final class ProfileViewModel: ObservableObject {
    /// Après ce nombre de refus de suite, le message conseille de revenir plus tard au lieu de « réessaie ».
    static let refusalsBeforeAdvice = 3

    @Published var state: ProfileUiState

    let mode: OnboardingMode
    let deps: ProfileDependencies
    let uid: String?

    /// Réponses « non » consécutives du serveur (un réseau absent ne compte pas) ; remises à zéro par un succès.
    var serverRefusals = 0
    /// Une fois le profil confirmé ou le brouillon effacé, plus rien n'est écrit dans le brouillon.
    var draftClosed = false

    init(mode: OnboardingMode, dependencies: ProfileDependencies) {
        self.mode = mode
        deps = dependencies
        let currentUid = dependencies.session.currentUid
        uid = currentUid
        let draft = currentUid.flatMap { dependencies.drafts.read(uid: $0) }
        // Les noms : le brouillon d'abord (ce que la personne a saisi), sinon le nom donné par le fournisseur (une
        // suggestion, modifiable).
        let suggested = ProfileForm.splitDisplayName(dependencies.session.suggestedDisplayName())
        let tools = dependencies.phoneTools
        let region = draft?.regionCode.flatMap { tools.isSupportedRegion($0) ? $0 : nil }
            ?? PhoneInput.defaultRegion(deviceRegion: dependencies.deviceRegion(), tools: tools)
        let full = mode == .full
        let draftFirst = Self.blankToNil(draft?.firstName) ?? suggested.first
        let draftLast = Self.blankToNil(draft?.lastName) ?? suggested.last
        state = ProfileUiState(
            mode: mode,
            step: mode == .phoneOnly ? .phone : (draft?.step ?? .profile),
            firstName: full ? draftFirst : "",
            lastName: full ? draftLast : "",
            avatar: full ? draft?.avatar : nil,
            regionCode: region,
            phoneText: draft?.phoneText ?? ""
        )
        // Charge le plan de numérotation du pays hors du fil principal, pour que l'étape téléphone s'ouvre sans accroc.
        Task.detached {
            _ = tools.exampleHint(region: region)
        }
    }

    private static func blankToNil(_ text: String?) -> String? {
        guard let text, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        return text
    }

    // MARK: - Étape profil

    func onFirstNameChanged(_ raw: String) {
        let limited = ProfileForm.limitInput(raw)
        state.firstName = limited.text
        state.firstNameLimitHit = limited.wasCut
        saveDraft()
    }

    func onLastNameChanged(_ raw: String) {
        let limited = ProfileForm.limitInput(raw)
        state.lastName = limited.text
        state.lastNameLimitHit = limited.wasCut
        saveDraft()
    }

    /// La flèche de l'étape profil : rien n'est écrit, seulement l'étape suivante.
    func onContinueFromProfile() {
        guard mode == .full, state.step == .profile, state.canContinueFromProfile else {
            return
        }
        state.step = .phone
        state.message = nil
        saveDraft()
    }

    /// Flèche de retour de l'étape téléphone (et geste de retour) : l'étape profil, tout ce qui a été saisi est gardé.
    func onBackToProfile() {
        guard mode == .full, !state.isWriting, state.step != .profile else {
            return
        }
        state.step = .profile
        state.message = nil
        state.phoneInvalid = false
        state.phoneTypeRefused = false
        saveDraft()
    }

    // MARK: - Photo

    /// Une photo a été choisie dans la photothèque. `data` vaut nil quand la personne a fermé le sélecteur.
    func onPhotoChosen(_ data: Data?) {
        guard let data else {
            return
        }
        state.message = nil
        Task {
            if let photo = await self.deps.avatars.decode(data) {
                self.state.crop = CropState(
                    photo: photo,
                    zoom: CropMath.minZoom,
                    centerX: Double(photo.width) / 2,
                    centerY: Double(photo.height) / 2
                )
            } else {
                self.state.message = .photoUnusable
            }
        }
    }

    /// Le sélecteur de photos a rendu une photo que le système n'a pas pu fournir : même message que pour un fichier qui
    /// n'est pas une image.
    func onPhotoLoadFailed() {
        state.message = .photoUnusable
    }

    /// Pincement et glissement dans l'écran de recadrage, ou curseur de zoom. Le carré reste dans la photo.
    func onCropChanged(zoom: Double, centerX: Double, centerY: Double) {
        guard var crop = state.crop else {
            return
        }
        let clampedZoom = CropMath.clampZoom(zoom)
        let center = CropMath.clampCenter(
            width: crop.photo.width,
            height: crop.photo.height,
            zoom: clampedZoom,
            center: (centerX, centerY)
        )
        crop.zoom = clampedZoom
        crop.centerX = center.x
        crop.centerY = center.y
        state.crop = crop
    }

    func onCropCancelled() {
        if state.crop?.isEncoding == true {
            return
        }
        state.crop = nil
    }

    /// « Utiliser cette photo » : découpe le carré, le ré-encode (sans métadonnées, 20 000 octets au plus) et le garde.
    func onCropConfirmed() {
        guard var crop = state.crop, !crop.isEncoding else {
            return
        }
        crop.isEncoding = true
        state.crop = crop
        let photo = crop.photo
        let square = CropMath.toCrop(
            width: photo.width,
            height: photo.height,
            zoom: crop.zoom,
            center: (crop.centerX, crop.centerY)
        )
        Task {
            let bytes = await self.deps.avatars.encode(photo, crop: square)
            self.state.crop = nil
            // L'encodeur respecte déjà 20 000 octets ; les règles refuseraient davantage, donc c'est aussi vérifié ici.
            if let bytes, bytes.count <= AvatarCompression.maxBytes {
                self.state.avatar = bytes
                self.state.message = nil
            } else {
                self.state.message = .photoUnusable
            }
            self.saveDraft()
        }
    }

    func onRemovePhoto() {
        state.avatar = nil
        saveDraft()
    }

    func onMessageDismissed() {
        state.message = nil
    }

    // MARK: - Brouillon

    func saveDraft() {
        guard let me = uid, !draftClosed else {
            return
        }
        deps.drafts.save(
            ProfileDraft(
                uid: me,
                step: state.step,
                firstName: state.firstName,
                lastName: state.lastName,
                regionCode: state.regionCode,
                phoneText: state.phoneText,
                avatar: state.avatar
            )
        )
    }

    func eraseDraft() {
        draftClosed = true
        deps.drafts.clear()
    }
}
