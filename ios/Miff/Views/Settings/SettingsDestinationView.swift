import SwiftUI

/// Les écrans des Réglages, empilés dans la pile UNIQUE de l'app (celle de `MiffHomeHost` ou de `MiffOnboardingHost`, comme les écrans de
/// la Miff). Chaque écran reçoit ici tout ce dont il a besoin : l'environnement, le profil lu, les gestes d'ouverture et de retour.
/// Aucune pile imbriquée.
///
/// `restricted` : un compte SANS Miff (décision du PM du 2026-11-07) n'a que « Mon compte » et « Confidentialité ». Il n'a alors pas de
/// profil lu (`profile` est nil) : aucune lecture de plus pour atteindre la suppression de son compte.
struct SettingsDestinationView: View {
    let route: SettingsRoute
    let environment: AppEnvironment
    let profile: ProfileTabViewModel?
    let restricted: Bool
    let navigate: (SettingsRoute) -> Void
    let onBack: () -> Void
    /// Toucher une ligne du centre de notifications : le centre se referme et l'onglet demandé s'ouvre (jamais une erreur).
    var onOpenNotification: (NotificationDestination) -> Void = { _ in }
    /// SRV-8I : « Découvrir / Reprendre Miff+ » de Réglages > Abonnement : l'offre s'ouvre en plein écran, comme depuis les autres portes.
    var onOpenOffer: () -> Void = {}

    var body: some View {
        if SettingsAccess.isAllowed(route, restricted: restricted) {
            screen
        }
    }

    @ViewBuilder
    private var screen: some View {
        switch route {
        case .settings:
            SettingsListView(
                actions: SettingsActions(
                    onBack: onBack,
                    onOpenMyInfo: { navigate(.myInfo) },
                    onOpenAccount: { navigate(.account) },
                    onOpenLanguage: { navigate(.language) },
                    onOpenPrivacy: { navigate(.privacy) },
                    onOpenNotifications: { navigate(.notifications) },
                    onOpenAbout: { navigate(.about) },
                    onOpenSubscription: subscriptionOpener
                ),
                restricted: restricted
            )
        case .myInfo:
            if let profile {
                MyInfoHost(environment: environment, profile: profile, onBack: onBack)
            }
        case .account:
            AccountView(environment: environment, onBack: onBack, onDelete: { navigate(.deleteAccount) })
        case .deleteAccount:
            // AUTH-5c : le vrai parcours (information, confirmation, ré-authentification). Une fois confirmé, la suppression prend tout
            // l'écran (destination `accountDeletion`) et cette pile disparaît avec le reste de l'app connectée.
            DeleteAccountFlowView(environment: environment, onClose: onBack)
        case .language:
            LanguageView(controller: .shared, onBack: onBack)
        case .privacy:
            PrivacyView(
                permissions: environment.devicePermissions,
                awaiting: environment.awaitingAgreement,
                // ONB-3b : l'interrupteur « statistiques et rapports de plantage » (retrait du consentement du compte, reprise hors connexion).
                model: environment.makePrivacyViewModel(),
                onBack: onBack,
                onOpenDetectionHelp: { navigate(.detectionHelp) },
                // TIME-3 (D15) : « Effacer mon historique de temps » : le journal local des changements de présence part ; aucune écriture serveur.
                onEraseTimeHistory: { environment.presenceHistory.eraseFacts() }
            )
        case .detectionHelp:
            DetectionHelpView(onBack: onBack)
        case .about:
            AboutView(
                actions: AboutActions(
                    onBack: onBack,
                    onOpenDetectionHelp: { navigate(.detectionHelp) },
                    onOpenNotificationsHelp: { navigate(.notificationsHelp) },
                    onOpenLicenses: { navigate(.licenses) }
                ),
                build: .current,
                restricted: restricted
            )
        case .licenses:
            LicensesView(onBack: onBack)
        case .subscription:
            subscriptionScreen
        case .notifications, .notificationCenter, .notificationsHelp:
            notificationScreen
        }
    }

    /// La ligne « Abonnement » : seulement quand l'achat est allumé dans ce build (SRV-8I).
    private var subscriptionOpener: (() -> Void)? {
        guard environment.premium.coordinator != nil else {
            return nil
        }
        return { navigate(.subscription) }
    }

    /// SRV-8I : Réglages > Abonnement. Rien quand l'achat est éteint dans ce build (la ligne n'existe pas non plus).
    @ViewBuilder
    private var subscriptionScreen: some View {
        if let premium = environment.premium.coordinator, let subscriptions = environment.premium.subscriptions {
            SubscriptionHost(
                subscriptions: subscriptions,
                premium: premium,
                actions: SubscriptionActions(onBack: onBack, onOffer: onOpenOffer)
            )
        }
    }

    /// NOTIF-P0 : Réglages > Notifications, le centre de notifications et l'aide « notifications coupées ».
    @ViewBuilder
    private var notificationScreen: some View {
        switch route {
        case .notifications:
            NotificationSettingsView(
                model: environment.makeNotificationSettingsViewModel(),
                actions: NotificationSettingsActions(
                    onBack: onBack,
                    onOpenCenter: { navigate(.notificationCenter) },
                    onOpenHelp: { navigate(.notificationsHelp) }
                )
            )
        case .notificationCenter:
            NotificationCenterView(
                model: environment.makeNotificationCenterViewModel(),
                service: environment.notifications.center,
                names: environment.notificationCenterNames(),
                actions: NotificationCenterActions(
                    onBack: onBack,
                    onOpen: onOpenNotification,
                    onOpenHelp: { navigate(.notificationsHelp) }
                )
            )
        default:
            NotificationHelpView(onBack: onBack)
        }
    }
}

/// L'écran de l'abonnement : garde son modèle le temps où il est dans la pile (revenir de l'offre ne le recrée pas).
private struct SubscriptionHost: View {
    @StateObject private var model: SubscriptionViewModel
    let actions: SubscriptionActions

    init(subscriptions: SubscriptionRepository, premium: PremiumCoordinator, actions: SubscriptionActions) {
        self.actions = actions
        _model = StateObject(wrappedValue: SubscriptionViewModel(subscriptions: subscriptions, premium: premium))
    }

    var body: some View {
        SubscriptionView(model: model, actions: actions)
    }
}

/// Mes informations : suit le profil lu (un `ObservedObject` ne peut pas être optionnel, d'où ce petit écran à part).
private struct MyInfoHost: View {
    let environment: AppEnvironment
    @ObservedObject var profile: ProfileTabViewModel
    let onBack: () -> Void

    var body: some View {
        MyInfoView(
            state: profile.state,
            tools: environment.phoneTools,
            makeViewModel: { environment.makeMyInfoViewModel(current: profile, profile: $0) },
            onBack: onBack,
            onRetry: { profile.retry() }
        )
    }
}
