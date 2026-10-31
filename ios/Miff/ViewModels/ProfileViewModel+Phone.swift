import Foundation

// Étape téléphone et écriture du profil (ticket Onboarding §6.4).
extension ProfileViewModel {
    func onCountrySelected(_ region: String) {
        guard deps.phoneTools.isSupportedRegion(region) else {
            return
        }
        state.regionCode = region.uppercased()
        state.phoneInvalid = false
        state.phoneTypeRefused = false
        state.message = nil
        saveDraft()
    }

    /// Frappe ou collage. Un numéro « + » d'un pays connu choisit ce pays et garde la partie nationale (TEL2).
    func onPhoneChanged(_ raw: String) {
        let text = String(raw.prefix(PhoneInput.maxInputLength))
        let detected = deps.phoneTools.detectInternational(text)
        state.phoneText = detected?.national ?? text
        state.regionCode = detected?.region ?? state.regionCode
        state.phoneInvalid = false
        state.phoneTypeRefused = false
        state.message = nil
        saveDraft()
    }

    /// La flèche de l'étape téléphone : contrôle la forme, puis UNE écriture confirmée par le serveur.
    func onSubmitPhone() {
        let typed = state
        guard typed.step == .phone, !typed.isWriting, PhoneInput.hasDigit(typed.phoneText) else {
            return
        }
        let checked = deps.phoneTools.normalize(region: typed.regionCode, input: typed.phoneText)
        guard case .valid(let e164, _) = checked else {
            // Rien ne quitte le téléphone : un message calme sous le champ.
            state.phoneInvalid = true
            state.phoneTypeRefused = checked == .unsupportedType
            state.message = nil
            return
        }
        guard let me = uid, deps.session.currentUid == me else {
            return
        }
        guard deps.network.isOnline() else {
            state.message = .network
            return
        }
        state.isWriting = true
        state.message = nil
        state.phoneInvalid = false
        state.phoneTypeRefused = false
        // Quoi qu'il arrive à l'écran, l'écriture est suivie jusqu'au bout : un résultat que personne ne lit laisserait le
        // brouillon et l'écran en décalage avec ce que détient le serveur.
        Task {
            await self.write(me: me, phone: e164, typed: typed)
        }
    }

    private func write(me: String, phone: String, typed: ProfileUiState) async {
        if mode == .phoneOnly {
            await writePhoneOnly(me: me, phone: phone)
            return
        }
        guard let consent = deps.pending.read(uid: me), typed.canContinueFromProfile else {
            // L'acceptation des conditions n'est plus sur cet appareil : retour au début du routage du compte, qui montre
            // le panneau de consentement. Rien n'a été écrit.
            state.isWriting = false
            deps.session.retry()
            return
        }
        let profile = NewProfile(
            firstName: ProfileForm.normalizeName(typed.firstName),
            lastName: ProfileForm.normalizeName(typed.lastName),
            avatarJpeg: typed.avatar,
            phone: phone
        )
        let result = await deps.profiles.create(uid: me, profile: profile, consent: consent)
        if await isSaved(result) {
            eraseDraft()
            await deps.session.onProfileSaved(analyticsConsentEnabled: consent.analyticsAccepted)
            wipe()
        } else {
            reportFailure(result)
        }
    }

    /// Ancien compte : le profil existe. Son numéro est ajouté ; ou, s'il est antérieur à AUTH-B, il est recréé.
    private func writePhoneOnly(me: String, phone: String) async {
        if deps.session.accountSnapshot?.hasTermsVersion == false {
            await recreateOlderProfile(me: me)
            return
        }
        let result = await deps.profiles.addPhone(uid: me, phone: phone)
        if await isSaved(result) {
            eraseDraft()
            // Les anciens comptes gardent leur propre choix d'analyse : le compte est relu par la session.
            let enabled = deps.session.accountSnapshot?.analyticsConsentEnabled ?? false
            await deps.session.onProfileSaved(analyticsConsentEnabled: enabled)
            wipe()
        } else {
            reportFailure(result)
        }
    }

    /// Les règles refusent toute mise à jour d'un profil sans `termsVersion` : il faut le supprimer puis le recréer, par le
    /// panneau de consentement et les deux étapes. JAMAIS pour un compte qui appartient à une Miff (un refus du panneau
    /// supprimerait alors tout le compte et laisserait la Miff orpheline) : message neutre, rien n'est touché.
    private func recreateOlderProfile(me: String) async {
        if deps.session.accountSnapshot?.hasMembership == true {
            state.isWriting = false
            state.message = .accountUnavailable
            return
        }
        // Les noms et la photo sont lus AVANT la suppression et gardés dans le brouillon : rien à retaper.
        guard let seed = await deps.profiles.read(uid: me) else {
            state.isWriting = false
            state.message = .saveFailed
            return
        }
        let deleted = await deps.profiles.delete(uid: me)
        guard deleted == .done else {
            reportFailure(deleted)
            return
        }
        deps.drafts.save(
            ProfileDraft(
                uid: me,
                step: .profile,
                firstName: ProfileForm.limitInput(seed.firstName).text,
                lastName: ProfileForm.limitInput(seed.lastName).text,
                regionCode: state.regionCode,
                phoneText: state.phoneText,
                avatar: seed.avatarJpeg
            )
        )
        state.isWriting = false
        deps.session.retry()
    }

    /// Le serveur a dit oui, ou a dit non alors que le profil EST là : un essai précédent qui a fini après notre délai peut
    /// arriver en retard, et le second essai est alors refusé (le profil existe et sa date de création ne peut pas
    /// changer). Un « non » est donc vérifié contre ce que le serveur détient vraiment, pour ne pas bloquer la personne
    /// sur un profil déjà enregistré. Jamais pour un échec de réseau (rien n'a pu être demandé).
    private func isSaved(_ result: ProfileWriteResult) async -> Bool {
        if result == .done {
            return true
        }
        guard result == .refused || result == .failed else {
            return false
        }
        return await deps.session.profileAlreadySaved()
    }

    /// Une fois le profil enregistré, rien de ce qui a été saisi ne reste en mémoire dans ce view model (il peut survivre à
    /// l'écran).
    private func wipe() {
        state = ProfileUiState(mode: mode, step: state.step, regionCode: state.regionCode)
    }

    private func reportFailure(_ result: ProfileWriteResult) {
        let message: ProfileMessage
        if result == .offline {
            message = .network
        } else {
            // Le serveur répond « non » (ou autre chose d'inattendu) encore et encore : ce n'est plus un accroc.
            serverRefusals += 1
            message = serverRefusals >= Self.refusalsBeforeAdvice ? .tryLater : .saveFailed
        }
        state.isWriting = false
        state.message = message
    }
}
