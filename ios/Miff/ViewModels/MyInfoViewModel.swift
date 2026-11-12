import Foundation
#if canImport(Combine)
import Combine
#endif

// SÉCURITÉ, pour ce fichier : prénom, nom, numéro de téléphone et photo sont des données personnelles. Ils vivent dans l'état de
// l'écran, partent vers Firestore par `MyProfileRepository` et nulle part ailleurs : jamais journalisés, jamais dans un événement
// Analytics, jamais dans un message d'erreur. Cet écran n'envoie AUCUN événement d'analyse (critère MI17, décision A11).

/// Tout ce dont « Mes informations » a besoin.
struct MyInfoDependencies {
    let repository: MyProfileRepository
    let current: CurrentProfileHolding
    let avatars: AvatarCodec
    let network: NetworkMonitor
    let phoneTools: PhoneNumberTools
    let deviceRegion: () -> String?
    let currentUid: () -> String?
}

/// Réglages > Mes informations (ticket `docs/tickets/modifier-infos-perso.md`, à l'identique d'Android : `MyInfoViewModel.kt`) : la
/// photo, le prénom, le nom, le pays et le numéro, avec les règles des étapes de l'inscription (noms de 1 à 50 unités UTF-16, le numéro
/// est obligatoire et enregistré en E.164).
///
/// L'enregistrement est UNE écriture confirmée du profil et de la fiche de chaque Miff (`MyProfileRepository`) ; hors ligne, un message
/// calme et RIEN n'est mis en file. Si seules les fiches des Miffs restent à écrire, le même bouton finit le travail.
@MainActor
final class MyInfoViewModel: ObservableObject {
    /// Après ce nombre de refus de suite, le message conseille de revenir plus tard au lieu de « réessaie ».
    static let refusalsBeforeAdvice = 3

    @Published private(set) var state: MyInfoUiState

    private let deps: MyInfoDependencies
    /// Ce que le serveur détient : la référence de « quelque chose a changé ».
    private var original: ProfileSummary
    private var originalRegion: String
    private var originalNational: String
    private var firstNameTouched = false
    private var lastNameTouched = false
    /// Le profil est enregistré mais des fiches ne le sont pas : le bouton reste utilisable bien que rien n'ait changé.
    private var syncPending = false
    /// Réponses « non » consécutives du serveur (un réseau absent ne compte pas) ; remises à zéro par un succès.
    private var serverRefusals = 0

    init(profile: ProfileSummary, dependencies: MyInfoDependencies) {
        deps = dependencies
        original = profile
        let place = Self.originalPhone(of: profile, deps: dependencies)
        originalRegion = place.region
        originalNational = place.national
        state = MyInfoUiState(
            firstName: profile.firstName,
            lastName: profile.lastName,
            avatar: profile.avatarJpeg,
            regionCode: place.region,
            phoneText: place.national
        )
        state = derive(state)
    }

    /// Le pays et le numéro national du profil : déduits de l'indicatif ; sans numéro (ancien compte), le pays de l'appareil.
    private static func originalPhone(
        of profile: ProfileSummary,
        deps: MyInfoDependencies
    ) -> (region: String, national: String) {
        let fallback = PhoneInput.defaultRegion(deviceRegion: deps.deviceRegion(), tools: deps.phoneTools)
        guard let phone = profile.phone, let detected = deps.phoneTools.detectInternational(phone) else {
            return (fallback, "")
        }
        return (detected.region, detected.national)
    }

    // MARK: - Champs

    func onFirstNameChanged(_ raw: String) {
        firstNameTouched = true
        let limited = ProfileForm.limitInput(raw)
        change {
            $0.firstName = limited.text
            $0.firstNameLimitHit = limited.wasCut
            $0.message = nil
        }
    }

    func onLastNameChanged(_ raw: String) {
        lastNameTouched = true
        let limited = ProfileForm.limitInput(raw)
        change {
            $0.lastName = limited.text
            $0.lastNameLimitHit = limited.wasCut
            $0.message = nil
        }
    }

    func onCountrySelected(_ region: String) {
        guard deps.phoneTools.isSupportedRegion(region) else {
            return
        }
        change {
            $0.regionCode = region.uppercased()
            $0.phoneInvalid = false
            $0.phoneTypeRefused = false
            $0.message = nil
        }
    }

    /// Frappe ou collage. Un numéro « + » d'un pays connu choisit ce pays et garde la partie nationale (critère MI3).
    func onPhoneChanged(_ raw: String) {
        let text = String(raw.prefix(PhoneInput.maxInputLength))
        let detected = deps.phoneTools.detectInternational(text)
        change {
            $0.phoneText = detected?.national ?? text
            $0.regionCode = detected?.region ?? $0.regionCode
            $0.phoneInvalid = false
            $0.phoneTypeRefused = false
            $0.message = nil
        }
    }

    /// La personne a quitté le champ du numéro : un numéro qui ne semble pas correct est signalé, calmement, maintenant. Un numéro qu'elle
    /// n'a pas touché n'est jamais signalé.
    func onPhoneFocusLost() {
        let typed = state
        guard !typed.isSaving, PhoneInput.hasDigit(typed.phoneText) else {
            return
        }
        if typed.regionCode == originalRegion && typed.phoneText == originalNational {
            return
        }
        _ = checkPhone()
    }
}

/// La photo : choix, recadrage carré, retrait.
extension MyInfoViewModel {
    // MARK: - Photo

    /// Une photo a été choisie dans la photothèque. `data` vaut nil quand la personne a fermé le sélecteur.
    func onPhotoChosen(_ data: Data?) {
        guard let data else {
            return
        }
        change { $0.message = nil }
        Task {
            if let photo = await self.deps.avatars.decode(data) {
                self.change {
                    $0.crop = CropState(
                        photo: photo,
                        zoom: CropMath.minZoom,
                        centerX: Double(photo.width) / 2,
                        centerY: Double(photo.height) / 2
                    )
                }
            } else {
                self.change { $0.message = .photoUnusable }
            }
        }
    }

    /// Le sélecteur a rendu une photo que le système n'a pas pu fournir : même message que pour un fichier qui n'est pas une image.
    func onPhotoLoadFailed() {
        change { $0.message = .photoUnusable }
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
        change { $0.crop = crop }
    }

    func onCropCancelled() {
        if state.crop?.isEncoding == true {
            return
        }
        change { $0.crop = nil }
    }

    /// « Utiliser cette photo » : découpe le carré, le ré-encode (sans métadonnées, 20 000 octets au plus) et le garde.
    func onCropConfirmed() {
        guard var crop = state.crop, !crop.isEncoding else {
            return
        }
        crop.isEncoding = true
        change { $0.crop = crop }
        let photo = crop.photo
        let square = CropMath.toCrop(
            width: photo.width,
            height: photo.height,
            zoom: crop.zoom,
            center: (crop.centerX, crop.centerY)
        )
        Task {
            let bytes = await self.deps.avatars.encode(photo, crop: square)
            // L'encodeur respecte déjà 20 000 octets ; les règles refuseraient davantage, donc c'est aussi vérifié ici.
            if let bytes, bytes.count <= AvatarCompression.maxBytes {
                self.change {
                    $0.crop = nil
                    $0.avatar = bytes
                    $0.message = nil
                }
            } else {
                self.change {
                    $0.crop = nil
                    $0.message = .photoUnusable
                }
            }
        }
    }

    func onRemovePhoto() {
        change {
            $0.avatar = nil
            $0.message = nil
        }
    }

    func onMessageDismissed() {
        change { $0.message = nil }
    }
}

/// L'enregistrement, et l'état dérivé des valeurs saisies.
extension MyInfoViewModel {
    // MARK: - Enregistrer

    /// « Enregistrer » : contrôle le numéro, puis UNE écriture confirmée (profil et fiches). Hors ligne, rien n'est écrit ni mis en file.
    func onSave() {
        let typed = state
        guard typed.canSave, let e164 = checkPhone() else {
            return
        }
        guard let me = deps.currentUid() else {
            return
        }
        guard deps.network.isOnline() else {
            change { $0.message = .network }
            return
        }
        let profile = ProfileSummary(
            firstName: ProfileForm.normalizeName(typed.firstName),
            lastName: ProfileForm.normalizeName(typed.lastName),
            phone: e164,
            avatarJpeg: typed.avatar
        )
        change {
            $0.isSaving = true
            $0.message = nil
        }
        // Quoi qu'il arrive à l'écran, l'écriture est suivie jusqu'au bout (la tâche garde ce view model) : un résultat que personne ne lit
        // laisserait l'en-tête du Profil en décalage avec ce que détient le serveur.
        Task {
            let result = await self.deps.repository.save(uid: me, profile: profile)
            self.finish(me: me, profile: profile, result: result)
        }
    }

    private func finish(me: String, profile: ProfileSummary, result: ProfileSaveResult) {
        switch result {
        case .saved, .savedPartial:
            serverRefusals = 0
            syncPending = result == .savedPartial
            deps.current.onSaved(uid: me, profile: profile)
            original = profile
            let place = Self.originalPhone(of: profile, deps: deps)
            originalRegion = place.region
            originalNational = place.national
            firstNameTouched = false
            lastNameTouched = false
            let message: MyInfoMessage = syncPending ? .syncPending : .saved
            change {
                $0.isSaving = false
                $0.firstName = profile.firstName
                $0.lastName = profile.lastName
                $0.firstNameLimitHit = false
                $0.lastNameLimitHit = false
                $0.regionCode = place.region
                $0.phoneText = place.national
                $0.message = message
            }
        case .offline:
            change {
                $0.isSaving = false
                $0.message = .network
            }
        case .refused, .failed:
            serverRefusals += 1
            let message: MyInfoMessage = serverRefusals >= Self.refusalsBeforeAdvice ? .tryLater : .saveFailed
            change {
                $0.isSaving = false
                $0.message = message
            }
        }
    }

    /// Le numéro E.164 de ce qui est saisi, ou nil (et la phrase calme est montrée).
    private func checkPhone() -> String? {
        let typed = state
        let checked = deps.phoneTools.normalize(region: typed.regionCode, input: typed.phoneText)
        guard case .valid(let e164, _) = checked else {
            change {
                $0.phoneInvalid = true
                $0.phoneTypeRefused = checked == .unsupportedType
                $0.message = nil
            }
            return nil
        }
        return e164
    }

    // MARK: - État

    private func change(_ transform: (inout MyInfoUiState) -> Void) {
        var next = state
        transform(&next)
        state = derive(next)
    }

    /// Quelque chose que la personne a saisi ou choisi diffère-t-il de ce que détient le serveur ? Les noms sont comparés une fois normalisés
    /// (un nom que le serveur garde avec deux espaces ne compte pas comme un changement), la photo octet pour octet.
    private func differsFromServer(_ typed: MyInfoUiState) -> Bool {
        if ProfileForm.normalizeName(typed.firstName) != ProfileForm.normalizeName(original.firstName) {
            return true
        }
        if ProfileForm.normalizeName(typed.lastName) != ProfileForm.normalizeName(original.lastName) {
            return true
        }
        return typed.avatar != original.avatarJpeg
            || typed.regionCode != originalRegion
            || typed.phoneText != originalNational
    }

    /// Les valeurs qui se déduisent des autres : noms complets, messages de champ vide, « quelque chose a changé ».
    private func derive(_ typed: MyInfoUiState) -> MyInfoUiState {
        var next = typed
        let first = ProfileForm.normalizeName(typed.firstName)
        let last = ProfileForm.normalizeName(typed.lastName)
        let changed = differsFromServer(typed)
        next.namesComplete = ProfileForm.isNameComplete(typed.firstName) && ProfileForm.isNameComplete(typed.lastName)
        next.firstNameBlank = firstNameTouched && first.isEmpty
        next.lastNameBlank = lastNameTouched && last.isEmpty
        next.hasChanges = changed || syncPending
        next.hasEdits = changed
        return next
    }
}
