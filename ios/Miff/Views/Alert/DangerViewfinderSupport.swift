#if ALERT_DANGER
import SwiftUI

// Ce que le viseur de l'Update fait de l'alerte (docs/tickets/alerte-danger.md 7.1 et 7.2 ; miroir de `AlertUpdateHooks` et de `Shutter` d'Android) : le maintien de
// 5 secondes sur l'obturateur, la bulle bleue, la voie accessible. Un appui COURT reste la photo, comme avant.

/// Ce que l'Update a besoin de savoir de l'alerte, en un bloc (donné par la coquille).
struct DangerUpdateHooks {
    /// La bulle bleue de l'onboarding se montre (l'indicateur `alertHintDone` est faux).
    var hintVisible: Bool
    var onHintDone: () -> Void
    /// L'IDENTIFIANT de la Miff sélectionnée dans le Feed (jamais le nom : deux Miffs peuvent porter le même) : le geste se rebranche quand il change.
    var activeMiffId: String?
    /// La Miff sélectionnée dans le Feed : celle où l'alerte irait.
    var targetMiffName: String?
    /// La personne a plusieurs Miffs : l'écran de maintien nomme la Miff avant que l'alerte parte.
    var several: Bool
    /// La Miff d'une alerte DÉJÀ en cours (dans une Miff quelconque), ou nil : un maintien n'en démarre jamais une seconde (D14).
    var activeAlertMiffName: String?
    /// Une alerte est en cours mais le nom de sa Miff n'est pas connu : même effet (jamais une seconde alerte).
    var alertInProgress: Bool
    /// Les 5 secondes ont été tenues (ou la confirmation accessible a été donnée).
    var onTrigger: () -> Void
    /// « Une alerte est déjà en cours pour la Miff {nom} ».
    var onAlreadyActive: (String?) -> Void
    /// Les numéros d'urgence depuis un écran de l'Update SANS viseur (donc sans geste) ; `stopped` : mon partage est arrêté (l'écran propose de le reprendre).
    var onOpenEmergency: (Bool) -> Void
}

private struct DangerUpdateHooksKey: EnvironmentKey {
    static let defaultValue: DangerUpdateHooks? = nil
}

extension EnvironmentValues {
    var dangerUpdateHooks: DangerUpdateHooks? {
        get { self[DangerUpdateHooksKey.self] }
        set { self[DangerUpdateHooksKey.self] = newValue }
    }
}

/// L'état de l'alerte sur le viseur : le geste, la bulle bleue, les fenêtres. Créé avec le viseur (un nouvel état à chaque ouverture).
@MainActor
final class DangerViewfinderState: ObservableObject {
    let hold = DangerHoldModel()
    @Published var hintShown = true
    @Published var infoOpen = false
    @Published var confirmOpen = false
    /// Ce que l'Update sait faire MAINTENANT : le geste lit ceci AU MOMENT où il agit, jamais une copie prise à l'apparition du viseur (la Miff sélectionnée
    /// peut changer pendant que l'Update est affiché : le tracé rouge dit alors une Miff et le maintien en servirait une autre).
    private var current: DangerUpdateHooks?
    private var photo: () -> Void = {}

    /// Branche le geste ; rappelé quand la Miff sélectionnée (son identifiant) ou l'état de l'alerte change.
    func bind(hooks: DangerUpdateHooks, onPhoto: @escaping () -> Void) {
        current = hooks
        photo = onPhoto
        hold.onTap = { [weak self] in
            self?.hintShown = false
            self?.photo()
        }
        hold.onArmed = { [weak self] in
            // Le geste est découvert : la bulle a fini pour de bon. Si une alerte est déjà en cours, la personne le sait calmement (pas de mode rouge).
            guard let self, let hooks = self.current else {
                return
            }
            hooks.onHintDone()
            self.hintShown = false
            if hooks.alertInProgress {
                hooks.onAlreadyActive(hooks.activeAlertMiffName)
            }
        }
        hold.onDone = { [weak self] in
            if let hooks = self?.current, !hooks.alertInProgress {
                hooks.onTrigger()
            }
        }
    }
}

/// L'obturateur de l'Update AVEC le maintien : un toucher plus court que 400 ms est la photo ; plus long arme l'alerte (l'anneau et le disque passent au rouge) ;
/// 5 secondes de plus l'envoient. Un lecteur d'écran obtient l'action « Déclarer une alerte ».
struct DangerShutter: View {
    @ObservedObject var state: DangerViewfinderState
    @ObservedObject private var hold: DangerHoldModel
    let hooks: DangerUpdateHooks
    let onPhoto: () -> Void
    let onSwitchCamera: (() -> Void)?

    init(
        state: DangerViewfinderState,
        hooks: DangerUpdateHooks,
        onPhoto: @escaping () -> Void,
        onSwitchCamera: (() -> Void)?
    ) {
        self.state = state
        _hold = ObservedObject(wrappedValue: state.hold)
        self.hooks = hooks
        self.onPhoto = onPhoto
        self.onSwitchCamera = onSwitchCamera
    }

    private var ring: Color {
        (hold.armed ? colorAlertFill : colorUpdateOnPhoto).color
    }

    var body: some View {
        ZStack {
            Circle().strokeBorder(ring, lineWidth: UpdateLayout.shutterRingWidth)
            Circle()
                .fill(hold.armed ? colorAlertFill.withAlpha(0.7).color : Color.clear)
                .padding(UpdateLayout.shutterInnerInset)
            Circle()
                .strokeBorder(
                    (hold.armed ? colorAlertFill : colorActionPrimary).color,
                    lineWidth: UpdateLayout.shutterInnerRingWidth
                )
                .padding(UpdateLayout.shutterInnerInset)
        }
        .frame(width: UpdateLayout.shutterSize, height: UpdateLayout.shutterSize)
        .contentShape(Circle())
        .dangerHoldGesture(hold)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("update_shutter_a11y"))
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { onPhoto() }
        .accessibilityAction(named: Text("alert_shutter_a11y_action")) { declareAccessibly() }
        .modifier(ShutterSwitchAction(action: onSwitchCamera))
        .onAppear { bind() }
        .onChange(of: hooks.activeMiffId) { _ in bind() }
        .onChange(of: hooks.targetMiffName) { _ in bind() }
        .onChange(of: hooks.activeAlertMiffName) { _ in bind() }
        .onChange(of: hooks.alertInProgress) { _ in bind() }
    }

    /// Branche le geste sur ce que l'Update sait faire à cet instant.
    private func bind() {
        state.bind(hooks: hooks, onPhoto: onPhoto)
    }

    private func declareAccessibly() {
        if hooks.alertInProgress {
            hooks.onAlreadyActive(hooks.activeAlertMiffName)
        } else {
            state.confirmOpen = true
        }
    }
}

/// L'action d'accessibilité « Changer de caméra », seulement quand il y a deux caméras (la même que celle du bouton d'avant).
private struct ShutterSwitchAction: ViewModifier {
    let action: (() -> Void)?

    @ViewBuilder
    func body(content: Content) -> some View {
        if let action {
            content.accessibilityAction(named: Text("update_flip_a11y")) {
                action()
            }
        } else {
            content
        }
    }
}

/// Le mode rouge pendant le maintien (maquette 02) et les fenêtres de l'alerte, posés par-dessus le viseur.
struct DangerViewfinderLayer: ViewModifier {
    @ObservedObject var state: DangerViewfinderState
    @ObservedObject private var hold: DangerHoldModel
    let hooks: DangerUpdateHooks

    init(state: DangerViewfinderState, hooks: DangerUpdateHooks) {
        self.state = state
        _hold = ObservedObject(wrappedValue: state.hold)
        self.hooks = hooks
    }

    func body(content: Content) -> some View {
        content
            .overlay {
                if hold.armed && !hooks.alertInProgress {
                    DangerArmedOverlay(progress: hold.progress, miffName: hooks.several ? hooks.targetMiffName : nil)
                }
            }
            .dangerInfoAlert(isPresented: $state.infoOpen)
            .dangerStartConfirm(isPresented: $state.confirmOpen, miffName: hooks.targetMiffName) {
                hooks.onTrigger()
            }
    }
}

/// La bulle bleue au-dessus de l'obturateur, tant que la personne n'a pas découvert le geste. Elle ne couvre jamais l'obturateur et n'empêche personne
/// de prendre une photo.
struct DangerHintSlot: View {
    @ObservedObject var state: DangerViewfinderState
    @ObservedObject private var hold: DangerHoldModel
    let hooks: DangerUpdateHooks

    init(state: DangerViewfinderState, hooks: DangerUpdateHooks) {
        self.state = state
        _hold = ObservedObject(wrappedValue: state.hold)
        self.hooks = hooks
    }

    var body: some View {
        if hooks.hintVisible && state.hintShown && !hold.armed {
            DangerHintBubble(onMore: { state.infoOpen = true }, onGone: { state.hintShown = false })
                .padding(.horizontal, Spacing.lg)
        }
    }
}

extension View {
    /// Pose le mode rouge et les fenêtres de l'alerte sur le viseur, SEULEMENT quand la coquille a donné ses branchements (sinon le viseur d'avant).
    @ViewBuilder
    func dangerViewfinder(state: DangerViewfinderState, hooks: DangerUpdateHooks?) -> some View {
        if let hooks {
            modifier(DangerViewfinderLayer(state: state, hooks: hooks))
        } else {
            self
        }
    }
}
#endif
