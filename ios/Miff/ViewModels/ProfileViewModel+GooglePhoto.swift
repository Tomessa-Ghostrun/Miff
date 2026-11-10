import Foundation

// SÉCURITÉ, pour ce fichier : l'adresse et les octets de la photo Google sont des données personnelles. Ils restent en mémoire
// (l'état de l'écran), partent vers Firestore avec le profil comme n'importe quelle photo de profil, et nulle part ailleurs :
// jamais journalisés, jamais dans un événement d'analyse, jamais dans un message d'erreur.

extension ProfileViewModel {
    /// Lance le téléchargement de la photo du compte Google (une fois), sans jamais bloquer l'écran : au moindre échec ou au bout du
    /// délai, rien ne se passe et la personne continue sans photo, sans message.
    func startGooglePhotoPrefill(_ prefill: GooglePhotoPrefill, address: URL?) {
        guard address != nil else {
            return
        }
        googlePhotoTask = Task { [weak self] in
            let jpeg = await prefill.load(address)
            guard !Task.isCancelled, let self else {
                return
            }
            self.applyGooglePhoto(jpeg)
        }
    }

    /// La photo Google devient la photo du profil PRÉREMPLIE : seulement si la personne n'a rien choisi entre-temps, qu'aucune photo
    /// n'est déjà là et qu'elle est encore sur l'étape profil (sinon elle ne verrait pas la phrase qui le dit). Rien n'est écrit
    /// sur le disque ici : le brouillon (comme toute photo de profil) ne la reçoit qu'à la prochaine saisie.
    func applyGooglePhoto(_ jpeg: Data?) {
        guard let jpeg, !photoTouched, !draftClosed, state.avatar == nil, state.crop == nil, state.step == .profile,
              jpeg.count <= AvatarCompression.maxBytes else {
            return
        }
        state.avatar = jpeg
        state.googlePhotoHint = true
    }

    /// La personne a choisi, recadré ou retiré une photo : c'est elle qui décide, la photo Google n'intervient plus.
    func markPhotoTouched() {
        photoTouched = true
        googlePhotoTask?.cancel()
        state.googlePhotoHint = false
    }
}
