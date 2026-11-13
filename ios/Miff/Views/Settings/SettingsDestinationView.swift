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
                    onOpenPrivacy: { navigate(.privacy) },
                    onOpenNotifications: { navigate(.notifications) }
                ),
                build: .current,
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
        case .privacy:
            PrivacyView(
                permissions: environment.devicePermissions,
                onBack: onBack,
                onOpenDetectionHelp: { navigate(.detectionHelp) }
            )
        case .detectionHelp:
            DetectionHelpView(onBack: onBack)
        case .notifications, .notificationCenter, .notificationsHelp:
            notificationScreen
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
