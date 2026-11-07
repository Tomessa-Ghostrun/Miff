import Foundation

/// Où s'ouvre l'app, décidé d'après l'ÉTAT DU COMPTE (ticket §5), jamais d'après « une connexion vient d'avoir lieu ».
enum AppDestination: Equatable, Hashable {
    /// Pas de session, première visite : écran d'inscription.
    case signUp
    /// Pas de session, l'app a déjà servi (après une déconnexion) : écran de connexion directement.
    case signIn
    /// Session, mais ni profil ni acceptation des conditions enregistrée sur l'appareil : le panneau passe en premier.
    case consentPanel
    /// Session, conditions acceptées sur cet appareil, pas de profil : étape profil, puis étape téléphone.
    case profile
    /// Profil présent mais sans numéro (compte antérieur à ONB-B) : l'étape téléphone seule.
    case phoneOnly
    /// Profil et numéro présents, aucune Miff créée ou rejointe : choix créer / rejoindre.
    case miffChoice
    /// Une Miff existe : le fil.
    case feed

    /// Vrai si la destination suppose une session ouverte.
    var requiresSession: Bool {
        self != .signUp && self != .signIn
    }
}

/// Ce que deux lectures peu coûteuses disent du compte.
struct AccountSnapshot: Equatable {
    let profileExists: Bool
    let hasMembership: Bool
    /// `users/{uid}.analyticsConsent.enabled` ; absent veut dire faux.
    let analyticsConsentEnabled: Bool
    /// `users/{uid}.phone` est présent (absent sur un compte antérieur à ONB-B).
    let hasPhone: Bool
    /// `users/{uid}.termsVersion` est présent. Un profil sans lui (créé avant AUTH-B) ne peut pas recevoir son numéro
    /// par une mise à jour (les règles lisent `termsVersion`) : il faut le supprimer et le recréer.
    let hasTermsVersion: Bool

    init(
        profileExists: Bool,
        hasMembership: Bool,
        analyticsConsentEnabled: Bool,
        hasPhone: Bool = true,
        hasTermsVersion: Bool = true
    ) {
        self.profileExists = profileExists
        self.hasMembership = hasMembership
        self.analyticsConsentEnabled = analyticsConsentEnabled
        self.hasPhone = hasPhone
        self.hasTermsVersion = hasTermsVersion
    }

    /// Lit les indicateurs de présence dans les valeurs BRUTES du document de profil (quel que soit leur type) : un
    /// numéro ne compte que s'il s'agit d'une chaîne de la forme E.164 des règles, `termsVersion` que si c'est une
    /// chaîne non vide. Pur, pour que les tests couvrent tous les cas sans Firestore. Le numéro n'est pas conservé.
    static func fromProfileFields(
        profileExists: Bool,
        hasMembership: Bool,
        analyticsConsentEnabled: Bool,
        phone: Any?,
        termsVersion: Any?
    ) -> AccountSnapshot {
        let phoneIsValid = (phone as? String).map(ProfileDocument.isE164) ?? false
        let termsVersionIsPresent = (termsVersion as? String).map { !$0.isEmpty } ?? false
        return AccountSnapshot(
            profileExists: profileExists,
            hasMembership: profileExists && hasMembership,
            analyticsConsentEnabled: profileExists && analyticsConsentEnabled,
            hasPhone: profileExists && phoneIsValid,
            hasTermsVersion: profileExists && termsVersionIsPresent
        )
    }
}

/// Règle de routage (ordre du ticket §5 et Onboarding §6.2) :
/// 1. pas de session : inscription, ou connexion si l'app a déjà servi ;
/// 2. session sans profil : panneau de consentement si l'acceptation n'est pas enregistrée sur l'appareil, sinon profil ;
/// 2b. profil sans numéro : l'étape téléphone seule ;
/// 3. profil et numéro, aucune Miff : choix créer / rejoindre ;
/// 4. sinon : le fil.
func resolveDestination(
    signedIn: Bool,
    account: AccountSnapshot?,
    hasPendingTerms: Bool,
    returningUser: Bool
) -> AppDestination {
    guard signedIn else {
        return returningUser ? .signIn : .signUp
    }
    guard let account, account.profileExists else {
        return hasPendingTerms ? .profile : .consentPanel
    }
    if !account.hasPhone {
        return .phoneOnly
    }
    return account.hasMembership ? .feed : .miffChoice
}

/// Le carrousel de bienvenue n'apparaît qu'à la toute première ouverture (critère CA5) : l'app s'ouvre sur l'inscription
/// (aucune session, jamais servi) ET le carrousel n'a jamais été validé. Jamais avec une session, jamais après une
/// déconnexion, jamais une fois « Commencer » ou « J'ai déjà un compte » touché.
func shouldShowWelcomeCarousel(start: AppDestination, carouselSeen: Bool) -> Bool {
    start == .signUp && !carouselSeen
}

/// Adresse d'une page légale publique : l'adresse de base (un seul endroit) et le chemin de la langue. Une « / » à la
/// fin de la base (ou au début du chemin) ne donne jamais une double ni une absente.
func legalPageUrl(baseUrl: String, path: String) -> String {
    var base = baseUrl
    while base.hasSuffix("/") {
        base.removeLast()
    }
    var tail = path
    while tail.hasPrefix("/") {
        tail.removeFirst()
    }
    return base + "/" + tail
}
