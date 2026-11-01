#if DEBUG
import SwiftUI
import UIKit

/// Les écrans du flux Miff montrés SEULS avec un état fixe, pour les captures d'écran de la CI
/// (`xcrun simctl launch ... -MiffPreview miff-screen-owner`). Aucun réseau, aucune session, aucune écriture : ce sont les vrais
/// écrans, nourris par des états écrits ici (noms et code de démonstration, jamais des vraies données). Compilé en
/// configuration Debug SEULEMENT, comme `ScreenPreview`.
struct MiffScreenPreview: View {
    let name: String

    private static let demoCode = "XTZRSP367988"
    private static let demoMiff = "AbCdEfGhIjKlMnOpQrSt"

    private var noAction: () -> Void {
        {}
    }

    var body: some View {
        switch name {
        case "miff-choice":
            MiffChoiceView(onCreate: noAction, onJoin: noAction, onSignOut: noAction)
        case "miff-create", "miff-create-limit", "miff-create-saving", "miff-create-offline", "miff-ready":
            CreateMiffContent(state: createState, actions: createActions)
        case "miff-join", "miff-join-error", "miff-join-foreign", "miff-join-paused", "miff-join-member",
             "miff-join-confirm":
            JoinMiffContent(state: joinState, actions: joinActions)
        case "miff-lost":
            AccessLostView(miffName: "Famille Moudugenou", onAcknowledge: noAction)
        case "miff-home", "miff-home-premium", "miff-home-empty", "miff-home-unavailable":
            MiffHomeContent(state: homeState, actions: homeActions)
        default:
            MiffScreenContent(state: screenState, actions: screenActions)
        }
    }

    // MARK: - Créer

    private var createActions: CreateMiffActions {
        CreateMiffActions(onBack: noAction, onNameChanged: { _ in }, onSubmit: noAction, onContinue: noAction)
    }

    private var createState: CreateUiState {
        var state = CreateUiState()
        switch name {
        case "miff-create":
            state.name = "Famille Moudugenou"
        case "miff-create-limit":
            state.name = String(repeating: "Maison ", count: 6).trimmingCharacters(in: .whitespaces)
            state.name = String(state.name.prefix(40))
            state.limitReached = true
        case "miff-create-saving":
            state.name = "Famille Moudugenou"
            state.saving = true
        case "miff-create-offline":
            state.name = "Famille Moudugenou"
            state.message = .offline
        default:
            state.step = .ready
            state.created = MiffCreation(
                miffId: Self.demoMiff,
                joinCode: Self.demoCode,
                name: "Famille Moudugenou",
                firstMiff: true
            )
        }
        return state
    }

    // MARK: - Rejoindre

    private var joinActions: JoinMiffActions {
        JoinMiffActions(
            onBack: noAction,
            onCodeChanged: { _ in },
            onContinue: noAction,
            onConfirm: noAction,
            onCancelConfirm: noAction,
            onOpenMiff: { _ in }
        )
    }

    private var joinState: JoinUiState {
        var state = JoinUiState()
        switch name {
        case "miff-join-error":
            state.code = "XXARST943256"
            state.problem = .notFound
        case "miff-join-foreign":
            state.code = "XTZRSP36798O"
            state.foreignCharacter = true
        case "miff-join-paused":
            state.code = "XXARST943256"
            state.problem = .paused
        case "miff-join-member":
            state.code = Self.demoCode
            state.alreadyMember = AlreadyMemberInfo(miffId: Self.demoMiff, miffName: "Famille Moudugenou")
        case "miff-join-confirm":
            state.code = Self.demoCode
            state.step = .confirm
            state.miffName = "Famille Moudugenou"
        default:
            state.code = "XTZRSP36"
        }
        return state
    }

    // MARK: - Accueil

    private var homeActions: MiffHomeActions {
        MiffHomeActions(
            onRetry: noAction,
            onSelect: { _ in },
            onCreate: noAction,
            onJoin: noAction,
            onOpenMiff: { _ in },
            onSignOut: noAction
        )
    }

    private var homeState: DirectoryState {
        switch name {
        case "miff-home-empty":
            return .ready(DirectoryReady(miffs: [], activeId: nil, premium: false, fromCache: false))
        case "miff-home-unavailable":
            return .unavailable
        default:
            let miffs = [
                MiffSummary(id: Self.demoMiff, name: "Famille Moudugenou"),
                MiffSummary(id: "ZyXwVuTsRqPoNmLkJiHg", name: "Les copains")
            ]
            let premium = name == "miff-home-premium"
            let shown = premium ? miffs : [miffs[0]]
            return .ready(DirectoryReady(miffs: shown, activeId: Self.demoMiff, premium: premium, fromCache: false))
        }
    }

    // MARK: - Écran d'une Miff

    private var screenActions: MiffScreenActions {
        MiffScreenActions(
            onBack: noAction,
            onRetry: noAction,
            onNameChanged: { _ in },
            onSaveName: noAction,
            onSharingRequested: noAction,
            onSharingConfirmed: noAction,
            onSharingDismissed: noAction
        )
    }

    private var screenState: MiffScreenState {
        var state = MiffScreenState()
        state.status = .ready
        state.miffId = Self.demoMiff
        state.name = "Famille Moudugenou"
        state.nameInput = "Famille Moudugenou"
        state.joinCode = Self.demoCode
        state.isOwner = name != "miff-screen-member"
        state.myUid = state.isOwner ? "u1" : "u2"
        state.members = members
        state.places = [MiffPlace(id: "p1", name: "Maison de Papa"), MiffPlace(id: "p2", name: "École")]
        state.sharing = name != "miff-screen-sharing-off"
        applyVariant(to: &state)
        return state
    }

    private func applyVariant(to state: inout MiffScreenState) {
        switch name {
        case "miff-screen-offline":
            state.offline = true
        case "miff-screen-resume":
            state.sharing = false
            state.sharingDialog = .resume
        case "miff-screen-stop":
            state.sharingDialog = .stop
        case "miff-screen-loading":
            state = MiffScreenState()
        case "miff-screen-unavailable":
            state = MiffScreenState()
            state.status = .unavailable
        case "miff-screen-full":
            state.members = (1...15).map { index in
                MiffMember(
                    uid: "u\(index)",
                    firstName: "Prénom \(index)",
                    lastName: "Nom",
                    avatarJpeg: nil,
                    role: index == 1 ? .owner : .member,
                    sharingEnabled: true
                )
            }
        default:
            break
        }
    }

    private var members: [MiffMember] {
        [
            MiffMember(
                uid: "u1",
                firstName: "Emmanuelle",
                lastName: "Pluchard",
                avatarJpeg: sampleAvatar,
                role: .owner,
                sharingEnabled: true
            ),
            MiffMember(
                uid: "u2",
                firstName: "Ryadh",
                lastName: "Pluchard",
                avatarJpeg: nil,
                role: .member,
                sharingEnabled: true
            )
        ]
    }

    /// Une photo de démonstration dessinée ici (aucune vraie photo dans le dépôt pour les captures).
    private var sampleAvatar: Data? {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 96, height: 96))
        return renderer.jpegData(withCompressionQuality: 0.8) { context in
            UIColor.systemOrange.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 96, height: 96))
        }
    }
}
#endif
