import Foundation

extension SocialCredential {
    /// Le mode de connexion tel que le contrat Analytics le nomme.
    var method: AnalyticsEvent.Method {
        switch self {
        case .google:
            return .google
        case .apple:
            return .apple
        }
    }
}

extension AccountLoad {
    var snapshot: AccountSnapshot? {
        if case .loaded(let value) = self {
            return value
        }
        return nil
    }
}

// Les opérations lancées par les écrans.
extension SessionManager {
    /// nil = connecté (l'état avance tout seul) ; sinon pourquoi ça n'a pas fonctionné.
    func signInWithEmail(email: String, password: String) async -> AuthFailure? {
        await cache.purgeIfPending()
        switch await gateway.signInWithEmail(email: email, password: password) {
        case .failure(let failure):
            return failure
        case .success(let uid, _):
            await completeAuthentication(uid: uid, method: .email, signUpConsent: nil)
            return nil
        }
    }

    /// `consent` doit avoir accepté (a) : un compte n'est jamais créé sans cela.
    func createAccountWithEmail(email: String, password: String, consent: ConsentChoice) async -> AuthFailure? {
        guard consent.termsAccepted else {
            return .unknown
        }
        await cache.purgeIfPending()
        switch await gateway.createAccountWithEmail(email: email, password: password) {
        case .failure(let failure):
            return failure
        case .success(let uid, _):
            completeEmailSignUp(uid: uid)
            return nil
        }
    }

    /// Jeton Google ou Apple -> Firebase. `signUpConsent` est l'acceptation donnée par le geste quand il vient de l'écran
    /// d'inscription (nil depuis l'écran de connexion : une personne nouvelle reçoit alors le panneau de consentement).
    func signIn(with credential: SocialCredential, signUpConsent: ConsentChoice?) async -> AuthFailure? {
        if let signUpConsent, !signUpConsent.termsAccepted {
            return .unknown
        }
        await cache.purgeIfPending()
        switch await gateway.signIn(with: credential) {
        case .failure(let failure):
            return failure
        case .success(let uid, _):
            await completeAuthentication(uid: uid, method: credential.method, signUpConsent: signUpConsent)
            return nil
        }
    }

    /// Panneau de consentement, « Continuer ». Rend faux (sans rien faire) si (a) n'est pas accepté.
    func acceptConsentPanel(_ consent: ConsentChoice) -> Bool {
        guard consent.termsAccepted else {
            return false
        }
        // Seulement depuis le panneau lui-même : un second appel (double toucher) n'enregistre rien deux fois et
        // n'envoie pas un second événement.
        guard case .ready(.consentPanel) = state, let uid = gateway.currentUid else {
            return false
        }
        // « Continuer » accepte les conditions ET les statistiques (décision du 2026-10-28) : les deux sont enregistrées
        // ensemble.
        pending.save(PendingConsent(uid: uid, termsVersion: ConsentVersions.terms, analyticsAccepted: true))
        flags.set(true, forKey: Self.keyReturningUser)
        privacy.applyAccountConsent(true)
        tracker.track(.signUpCompleted(method: gateway.signInMethod() ?? .email))
        setState(.ready(.profile))
        return true
    }

    /// Panneau de consentement refusé (bouton, geste de retour, fermeture) : le compte tout juste créé est supprimé,
    /// rien n'est gardé, rien n'est envoyé. Si Firebase réclame une connexion récente, `source` redemande à la personne de
    /// choisir son compte Google ou Apple ; c'est la seule étape en plus, et elle s'annule sans aucun message.
    func refuseConsentPanel(source: SocialCredentialSource) async -> RefuseResult {
        // Un compte qui appartient déjà à une Miff n'est jamais supprimé par un refus du panneau (ce serait orpheliner la
        // Miff et la vue qu'en ont les autres membres) : la personne garde le panneau, avec le message neutre.
        if accountSnapshot?.hasMembership == true {
            return .failed(.unknown)
        }
        ending = true
        cache.markPurgePending()
        var failure = await gateway.deleteCurrentAccount()
        if failure == .recentLoginRequired {
            switch await requestFreshCredential(source: source) {
            case .credential(let credential):
                failure = await gateway.reauthenticate(with: credential)
                if failure == nil {
                    failure = await gateway.deleteCurrentAccount()
                }
            case .cancelled:
                ending = false
                return .cancelled
            case .unavailable:
                failure = .unknown
            case .failed(let reason):
                failure = reason
            }
        }
        if let failure {
            // On reste sur le panneau : jamais un compte utilisable sans (a).
            ending = false
            return .failed(failure)
        }
        setState(.loading)
        await stopEverything()
        _ = await cache.purge()
        finishSignedOut(destination: .signIn, notice: .accountNotCreated, markReturning: false)
        return .done
    }

    /// Seul un compte Google ou Apple peut être invité à se prouver ici : on ne laisse jamais la personne coincée devant
    /// une fenêtre qui ne peut pas fonctionner.
    private func requestFreshCredential(source: SocialCredentialSource) async -> SocialTokenResult {
        switch gateway.signInMethod() ?? .email {
        case .google:
            return await source.requestGoogle()
        case .apple:
            return await source.requestApple()
        case .email:
            return .unavailable
        }
    }

    /// Le profil (ou son numéro) vient d'être ÉCRIT ET CONFIRMÉ par le serveur : le compte est relu et l'app passe à ce
    /// dont le compte a maintenant besoin (créer / rejoindre une Miff, ou le fil si un ancien compte en a déjà une). Si
    /// cette lecture échoue, l'app avance d'après ce qui vient d'être écrit (un nouveau profil n'a pas encore de Miff)
    /// plutôt que d'afficher un écran « réessayer » juste après un succès.
    func onProfileSaved(analyticsConsentEnabled: Bool) async {
        guard let uid = gateway.currentUid else {
            return
        }
        let load = await accounts.load(uid: uid)
        guard gateway.currentUid == uid else {
            return
        }
        let snapshot: AccountSnapshot
        if let read = load.snapshot, read.profileExists, read.hasPhone {
            snapshot = read
        } else {
            snapshot = AccountSnapshot(
                profileExists: true,
                hasMembership: load.snapshot?.hasMembership ?? false,
                analyticsConsentEnabled: analyticsConsentEnabled
            )
        }
        applyAccount(uid: uid, snapshot: snapshot, method: nil, signUpGesture: false)
    }

    /// Le serveur détient-il déjà un profil complet (avec son numéro) pour la personne connectée ? Sert après une
    /// écriture refusée, pour distinguer un vrai refus d'un premier essai arrivé en retard. Une lecture ; faux au moindre
    /// doute.
    func profileAlreadySaved() async -> Bool {
        guard let uid = gateway.currentUid, let read = await accounts.load(uid: uid).snapshot else {
            return false
        }
        return gateway.currentUid == uid && read.profileExists && read.hasPhone
    }

    /// Déconnexion volontaire (ticket §7.6) : les écoutes s'arrêtent, les écritures en attente partent, le cache est
    /// purgé, puis la déconnexion.
    func signOut() async {
        ending = true
        cache.markPurgePending()
        setState(.loading)
        await stopEverything()
        await cache.flushPendingWrites()
        _ = await cache.purge()
        finishSignedOut(destination: .signIn, notice: nil, markReturning: true)
    }

    // MARK: - Intérieur

    /// Démarrage à froid et « Réessayer » : la même règle, quoi qu'il soit arrivé avant.
    func resolveCurrent() async {
        guard let uid = gateway.currentUid else {
            sessionActive = false
            // Pas de session = pas de consentement à envoyer quoi que ce soit : un rapport laissé par un plantage avant que
            // la personne ait dit oui disparaît.
            setSnapshot(nil)
            drafts?.clear()
            privacy.applyAccountConsent(false)
            pending.clear()
            setState(.ready(entryDestination()))
            return
        }
        sessionActive = true
        // Éteint jusqu'à la lecture du compte (aussi à « Réessayer »), mais le rapport du plantage précédent est gardé.
        privacy.pauseCollection()
        setState(.loading)
        let load = await accounts.load(uid: uid)
        // La session a pu être perdue pendant la lecture : le gestionnaire de perte a déjà remis l'app sur la connexion.
        guard gateway.currentUid == uid else {
            return
        }
        switch load {
        case .unavailable:
            setState(.lookupFailed)
        case .loaded(let snapshot):
            applyAccount(uid: uid, snapshot: snapshot, method: nil, signUpGesture: false)
        }
    }

    /// Nouveau compte créé par e-mail et mot de passe : on sait qu'il n'y a pas de profil, aucune lecture n'est utile.
    func completeEmailSignUp(uid: String) {
        sessionActive = true
        flags.set(true, forKey: Self.keyReturningUser)
        // Le geste sur « Rejoindre » a accepté les conditions ET les statistiques (décision du 2026-10-28).
        pending.save(PendingConsent(uid: uid, termsVersion: ConsentVersions.terms, analyticsAccepted: true))
        privacy.applyAccountConsent(true)
        tracker.track(.signUpCompleted(method: .email))
        setState(.ready(.profile))
    }

    /// Connexion par n'importe quelle méthode (ou Google / Apple depuis l'écran d'inscription) : lire le compte, PUIS
    /// décider de ce qui est collecté.
    func completeAuthentication(
        uid: String,
        method: AnalyticsEvent.Method,
        signUpConsent: ConsentChoice?
    ) async {
        sessionActive = true
        // Rien n'est collecté tant que le choix propre du compte n'a pas été lu (en pause, rien n'est encore effacé).
        privacy.pauseCollection()
        if signUpConsent != nil {
            pending.save(PendingConsent(uid: uid, termsVersion: ConsentVersions.terms, analyticsAccepted: true))
        }
        setState(.loading)
        let load = await accounts.load(uid: uid)
        guard gateway.currentUid == uid else {
            return
        }
        switch load {
        case .unavailable:
            setState(.lookupFailed)
        case .loaded(let snapshot):
            applyAccount(uid: uid, snapshot: snapshot, method: method, signUpGesture: signUpConsent != nil)
        }
    }

    /// `method` est renseigné quand un geste de connexion ou d'inscription vient d'avoir lieu (des événements peuvent
    /// partir), nil au démarrage à froid. Profil existant : le choix du COMPTE commande (même si l'écran d'inscription
    /// a servi) ; pas de profil : le choix donné par le geste sur l'écran d'inscription et enregistré sur cet appareil
    /// commande, et seulement s'il l'a été pour CE compte (une personne qui arrive sur le panneau n'a encore rien
    /// enregistré : rien n'est collecté).
    func applyAccount(uid: String, snapshot: AccountSnapshot, method: AnalyticsEvent.Method?, signUpGesture: Bool) {
        setSnapshot(snapshot)
        var recorded: PendingConsent?
        if snapshot.profileExists {
            pending.clear()
            drafts?.clear()
            privacy.applyAccountConsent(snapshot.analyticsConsentEnabled)
            if let method, snapshot.analyticsConsentEnabled {
                tracker.track(.signInCompleted(method: method))
            }
        } else {
            recorded = pending.read(uid: uid)
            privacy.applyAccountConsent(recorded?.analyticsAccepted == true)
            if let method, signUpGesture, recorded?.analyticsAccepted == true {
                tracker.track(.signUpCompleted(method: method))
            }
        }
        let destination = resolveDestination(
            signedIn: true,
            account: snapshot,
            hasPendingTerms: recorded != nil,
            returningUser: true
        )
        // L'app compte comme « utilisée » dès qu'un compte utilisable existe ; un panneau refusé ne laisse aucune trace.
        if method != nil && destination != .consentPanel {
            flags.set(true, forKey: Self.keyReturningUser)
        }
        setState(.ready(destination))
    }

    private func entryDestination() -> AppDestination {
        resolveDestination(
            signedIn: false,
            account: nil,
            hasPendingTerms: false,
            returningUser: flags.value(forKey: Self.keyReturningUser, default: false)
        )
    }
}
