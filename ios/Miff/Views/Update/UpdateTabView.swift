import SwiftUI

// L'écran Update (tickets UPDATE-3, décisions D16 à D39 du PM, design/screens/update.md) : l'explication et l'autorisation de la caméra, le
// viseur, l'aperçu de la photo, la publication et ses messages calmes. C'est un ONGLET (le bouton central de la barre, qui reste visible,
// en verre sombre sur le viseur et l'aperçu).
//
// La photo est prise EN MÉMOIRE et n'atteint jamais le disque. Rien ne part avant « Publier ». Le viseur n'existe que pendant que cet écran
// est visible. Quand l'app passe en arrière-plan, un voile opaque cache le viseur et la photo (aperçu des applications récentes).

struct UpdateTabView: View {
    @ObservedObject var model: UpdateViewModel
    let miffCount: Int
    let actions: UpdateActions

    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        stage
            .overlay(privacyShield)
            .onAppear {
                model.onOpened()
            }
            .onDisappear {
                // Quitter l'Update oublie la photo, quelle que soit la sortie (un autre onglet, une feuille) : rien ne reste en mémoire.
                model.onClosed()
            }
            .onChange(of: scenePhase) { phase in
                // Retour des Réglages ou du verrouillage : l'autorisation a pu changer, une photo qui attend est jugée (5 minutes).
                if phase == .active {
                    model.onOpened()
                }
            }
    }

    @ViewBuilder
    private var stage: some View {
        switch model.stage {
        case .idle:
            colorBgCanvas.color.ignoresSafeArea()
        case .explain:
            UpdatePopIn(
                systemImage: "camera",
                titleKey: "update_permission_title",
                bodyKey: "update_permission_body",
                primary: UpdatePopIn.Action(titleKey: "update_permission_continue", run: model.onExplanationAccepted),
                dismiss: UpdatePopIn.Action(titleKey: "update_permission_not_now", run: actions.onClose)
            )
        case .permissionDisabled:
            // iPhone : aucune alternative (D31), les Réglages sont la seule sortie ; la photo est obligatoire (D16).
            UpdatePopIn(
                systemImage: "video.slash",
                titleKey: "update_disabled_title",
                bodyKey: "update_disabled_body",
                primary: UpdatePopIn.Action(titleKey: "update_action_open_settings", run: model.openSettings),
                dismiss: UpdatePopIn.Action(titleKey: "update_action_close", run: actions.onClose)
            )
        case .cameraRestricted:
            // Le contrôle parental ou l'appareil la restreint : la personne ne peut pas le changer, donc pas de bouton « Réglages ».
            UpdatePopIn(
                systemImage: "lock",
                titleKey: "ios_update_restricted_title",
                bodyKey: "ios_update_restricted_body",
                dismiss: UpdatePopIn.Action(titleKey: "update_action_close", run: actions.onClose)
            )
        case .cameraUnavailable:
            UpdatePopIn(
                systemImage: "video.slash",
                titleKey: "update_unavailable_title",
                bodyKey: "update_unavailable_body",
                primary: UpdatePopIn.Action(titleKey: "update_action_retry", run: model.onRetryCamera),
                dismiss: UpdatePopIn.Action(titleKey: "update_action_close", run: actions.onClose)
            )
        case .sharingStopped:
            UpdatePopIn(
                systemImage: "pause.circle",
                titleKey: "update_stopped_title",
                bodyKey: "update_stopped_body",
                primary: UpdatePopIn.Action(titleKey: "update_action_resume_sharing", run: actions.onResumeSharing),
                dismiss: UpdatePopIn.Action(titleKey: "update_action_close", run: actions.onClose)
            )
        case .viewfinder(let showTip, let prepareFailed):
            ViewfinderView(
                showTip: showTip,
                prepareFailed: prepareFailed,
                onDoubleTapSwitch: model.onSwitchedByDoubleTap,
                onPhoto: model.onPhotoReady,
                onPhotoFailed: model.onPhotoFailed,
                onUnavailable: model.onCameraUnavailable
            )
        case .preview(let state):
            UpdatePreviewView(
                state: state,
                miffCount: miffCount,
                onRetake: model.onRetake,
                onPublish: model.onPublish,
                actions: actions
            )
        }
    }

    /// Aperçu des applications récentes : ni le viseur ni la photo ne doivent y apparaître. Le voile est posé dès que l'app n'est plus active
    /// (le système prend la capture à ce moment-là).
    @ViewBuilder
    private var privacyShield: some View {
        if scenePhase != .active && model.stage.hasDarkBackground {
            colorUpdateStage.color.ignoresSafeArea()
        }
    }
}
