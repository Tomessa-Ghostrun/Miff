import SwiftUI

/// Les écrans des Réglages, empilés dans la pile UNIQUE de l'app (celle de `MiffHomeHost`, comme les écrans de la Miff). Chaque écran
/// reçoit ici tout ce dont il a besoin : l'environnement, le profil lu, les gestes d'ouverture et de retour. Aucune pile imbriquée.
struct SettingsDestinationView: View {
    let route: SettingsRoute
    let environment: AppEnvironment
    @ObservedObject var profile: ProfileTabViewModel
    let navigate: (SettingsRoute) -> Void
    let onBack: () -> Void

    var body: some View {
        switch route {
        case .settings:
            SettingsListView(
                actions: SettingsActions(
                    onBack: onBack,
                    onOpenMyInfo: { navigate(.myInfo) },
                    onOpenAccount: { navigate(.account) },
                    onOpenPrivacy: { navigate(.privacy) }
                ),
                build: .current
            )
        case .myInfo:
            MyInfoView(
                state: profile.state,
                tools: environment.phoneTools,
                onBack: onBack,
                onRetry: { profile.retry() }
            )
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
        }
    }
}
