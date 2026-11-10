#if DEBUG
import SwiftUI

/// L'onglet Profil et son carrousel de Miffs montrés SEULS avec un état fixe, pour les captures d'écran de la CI
/// (`xcrun simctl launch ... -MiffPreview profile-carousel`). Aucun réseau, aucune session, aucune lecture : les membres sont écrits ici
/// (prénoms de démonstration, jamais de vraies données). Compilé en configuration Debug SEULEMENT, comme `ScreenPreview`.
@MainActor
struct ProfileScreenPreview: View {
    let name: String
    @StateObject private var previews = ProfileScreenPreview.demoPreviews()

    private static let miffA = "AbCdEfGhIjKlMnOpQrSt"
    private static let miffB = "ZyXwVuTsRqPoNmLkJiHg"

    /// Page de départ : « profile-carousel-2 » montre la 2e carte, « profile-carousel-create » la dernière.
    private var initialPage: Int {
        switch name {
        case "profile-carousel-2":
            return 1
        case "profile-carousel-create":
            return 2
        default:
            return 0
        }
    }

    private var directory: DirectoryState {
        let miffs = [
            MiffSummary(id: Self.miffA, name: "Famille Martin"),
            MiffSummary(id: Self.miffB, name: "Les copains du foot")
        ]
        return .ready(DirectoryReady(miffs: miffs, activeId: Self.miffA, premium: true, fromCache: false))
    }

    private var profile: ProfileTabState {
        .ready(ProfileSummary(firstName: "Emmanuelle", lastName: "Martin", phone: nil, avatarJpeg: nil))
    }

    var body: some View {
        ProfileTabView(
            state: profile,
            miffs: directory,
            previews: previews,
            isActive: false,
            initialCarouselPage: initialPage,
            actions: ProfileTabActions(
                onOpenMyInfo: {},
                onOpenSettings: {},
                onRetry: {},
                onOpenMiff: { _ in },
                onCreateMiff: {}
            )
        )
    }

    private static func demoMember(_ uid: String, _ first: String, _ last: String) -> MiffMember {
        MiffMember(uid: uid, firstName: first, lastName: last, avatarJpeg: nil, role: .member, sharingEnabled: true)
    }

    /// 4 membres pour la première Miff, 8 pour la deuxième (la rangée montre « +4 »).
    @MainActor
    private static func demoPreviews() -> MiffMemberPreviews {
        let store = MiffMemberPreviews(reader: DemoReader())
        let names = [
            ("Emmanuelle", "Martin"), ("Paul", "Martin"), ("Louise", "Martin"), ("Hugo", "Martin"),
            ("Inès", "Durand"), ("Karim", "Haddad"), ("Léa", "Bernard"), ("Tom", "Petit")
        ]
        let members = names.enumerated().map { index, name in demoMember("u\(index)", name.0, name.1) }
        store.record(miffId: miffA, members: Array(members.prefix(4)))
        store.record(miffId: miffB, members: members)
        return store
    }

    private struct DemoReader: MemberPreviewReader {
        func read(miffId: String) async -> MemberPreview? {
            nil
        }
    }
}
#endif
