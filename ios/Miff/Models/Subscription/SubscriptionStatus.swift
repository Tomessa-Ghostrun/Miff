import Foundation

/// Valeur de `entitlements/{uid}.source` pour un accès accordé à la main (procédure `docs/dev/premium-manuel.md`).
let entitlementSourceManual = "manual"
/// Valeurs écrites par le SERVEUR pour un vrai achat (ticket `docs/tickets/premium-achat-reel.md` §9.1).
let entitlementSourceAppStore = "app_store"
let entitlementSourceGooglePlay = "google_play"

/// `entitlements/{uid}` tel que le téléphone le lit. Chaque champ est facultatif : le document est écrit par le SERVEUR (un vrai achat) ou à la main dans la console
/// (`premium-manuel.md`), et un document plus ancien n'a que les trois premiers champs. Seuls les types EXACTS comptent : tout le reste se lit comme un champ absent,
/// jamais comme une erreur. L'app n'écrit JAMAIS ce document (les règles le refusent).
struct EntitlementDocument: Equatable {
    let active: Bool?
    let expiresAtMillis: Int?
    let source: String?
    /// L'état écrit par le serveur (trial, active, canceled, grace, on_hold, expired, revoked) : informatif, l'ACCÈS est décidé par `active` et `expiresAt` seuls.
    let state: String?
    /// `yearly` ou `monthly`.
    let plan: String?
    let trial: Bool?
    let willRenew: Bool?
    /// Un achat de test (bac à sable) : accepté et marqué par le serveur.
    let test: Bool?

    init(
        active: Bool?,
        expiresAtMillis: Int?,
        source: String?,
        state: String? = nil,
        plan: String? = nil,
        trial: Bool? = nil,
        willRenew: Bool? = nil,
        test: Bool? = nil
    ) {
        self.active = active
        self.expiresAtMillis = expiresAtMillis
        self.source = source
        self.state = state
        self.plan = plan
        self.trial = trial
        self.willRenew = willRenew
        self.test = test
    }
}

enum SubscriptionSource: Equatable {
    case manual
    case appStore
    case googlePlay
    case other
}

enum SubscriptionPlan: Equatable {
    case yearly
    case monthly
}

/// Ce que le store dit d'un achat de Miff+ (ticket §8).
enum StoreState: Equatable {
    /// L'essai gratuit court.
    case trial
    case active
    /// Résilié par la personne, mais payé jusqu'à la fin de la période.
    case canceled
    /// Le paiement échoue, le store réessaie : l'accès est GARDÉ jusqu'à la fin de la période de grâce.
    case grace
    /// Suspendu par le store (paiement qui échoue toujours) : PAS d'accès.
    case onHold
    case expired
    /// Remboursé ou révoqué : PAS d'accès, tout de suite.
    case revoked

    var grantsAccess: Bool {
        self == .trial || self == .active || self == .canceled || self == .grace
    }
}

/// Un achat fait dans un store (dans n'importe quel état : l'écran de l'abonnement existe pour tous, même expiré).
struct StoreSubscription: Equatable {
    let source: SubscriptionSource
    let state: StoreState
    let plan: SubscriptionPlan?
    let expiresAtMillis: Int?
    let test: Bool
}

/// Ce que l'app sait du Miff+ du compte. L'ACCÈS est décidé exactement comme les règles : `active == true` et `expiresAt` dans le futur ; l'état écrit par le serveur ne
/// choisit que la phrase (un document qui se contredit n'accorde jamais plus que ses dates).
enum SubscriptionStatus: Equatable {
    /// Rien à montrer que l'offre : pas de document, un accès à la main terminé, un document qui ne dit rien.
    case free
    /// Miff+ accordé à la main par l'équipe, encore valide.
    case manual(expiresAtMillis: Int)
    case store(StoreSubscription)

    /// Vrai quand Miff+ est ALLUMÉ : les lieux et les Miffs illimités et les zones de danger en dépendent.
    var hasAccess: Bool {
        switch self {
        case .free:
            return false
        case .manual:
            return true
        case .store(let subscription):
            return subscription.state.grantsAccess
        }
    }
}

private func parseState(_ value: String?) -> StoreState? {
    switch value {
    case "trial":
        return .trial
    case "active":
        return .active
    case "canceled":
        return .canceled
    case "grace":
        return .grace
    case "on_hold":
        return .onHold
    case "expired":
        return .expired
    case "revoked":
        return .revoked
    default:
        return nil
    }
}

private func parsePlan(_ value: String?) -> SubscriptionPlan? {
    switch value {
    case "yearly":
        return .yearly
    case "monthly":
        return .monthly
    default:
        return nil
    }
}

private func parseSource(_ value: String?) -> SubscriptionSource {
    switch value {
    case entitlementSourceManual:
        return .manual
    case entitlementSourceAppStore:
        return .appStore
    case entitlementSourceGooglePlay:
        return .googlePlay
    default:
        return .other
    }
}

/// Lit `entitlements/{uid}` (nil = pas de document) pour les écrans (ticket §8).
///
/// - Pas de document : `.free`. Un accès à la main qui n'est plus valide : `.free` aussi (aucune trace d'abonnement, comme avant).
/// - Un achat du store est montré dans TOUS ses états, un expiré ou remboursé compris (la personne voudra peut-être le reprendre).
/// - L'ACCÈS vient des dates (comme les règles) : un document qui dit « actif » avec une fin dans le passé se montre expiré ; un qui dit « expiré » avec une fin dans le
///   futur se montre actif (les dates l'emportent, l'accès n'est jamais faux). Un état inconnu ou absent se déduit des dates.
/// - Un accès d'origine inconnue (ni à la main ni d'un store) mais valide garde l'ancien libellé neutre (`.other`) ; terminé, c'est `.free`.
func interpretSubscription(_ document: EntitlementDocument?, nowMillis: Int) -> SubscriptionStatus {
    guard let document else {
        return .free
    }
    let end = document.expiresAtMillis
    let access = document.active == true && (end ?? 0) > nowMillis
    let source = parseSource(document.source)
    if source == .manual {
        if access, let end {
            return .manual(expiresAtMillis: end)
        }
        return .free
    }
    let declared = parseState(document.state)
    let state: StoreState
    if access {
        switch declared {
        case .trial?, .active?, .canceled?, .grace?:
            state = declared ?? .active
        default:
            state = document.trial == true ? .trial : .active
        }
    } else {
        switch declared {
        case .onHold?, .revoked?, .expired?:
            state = declared ?? .expired
        default:
            state = .expired
        }
    }
    if source == .other && !access {
        return .free
    }
    return .store(
        StoreSubscription(
            source: source,
            state: state,
            plan: parsePlan(document.plan),
            expiresAtMillis: end,
            test: document.test == true
        )
    )
}

enum SubscriptionDates {
    /// Une date au format long de la langue (« 12 janvier 2027 », « January 12, 2027 »), dans le fuseau du téléphone : le formateur du système, jamais un texte
    /// fait à la main (contrat `docs/contracts/i18n-keys.md`, critère F4 du ticket).
    static func long(millis: Int, locale: Locale, zone: TimeZone = .current) -> String {
        let date = Date(timeIntervalSince1970: Double(millis) / 1000)
        let style = Date.FormatStyle(date: .long, time: .omitted, locale: locale, timeZone: zone)
        return date.formatted(style)
    }
}
