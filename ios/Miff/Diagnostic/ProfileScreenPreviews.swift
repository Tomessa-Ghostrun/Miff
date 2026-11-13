#if DEBUG
import SwiftUI

/// L'onglet Profil et son carrousel de Miffs montrés SEULS avec un état fixe, pour les captures d'écran de la CI
/// (`xcrun simctl launch ... -MiffPreview profile-carousel`). Aucun réseau, aucune session, aucune lecture : les membres sont écrits ici
/// (prénoms de démonstration, jamais de vraies données). Compilé en configuration Debug SEULEMENT, comme `ScreenPreview`.
@MainActor
struct ProfileScreenPreview: View {
    let name: String
    @StateObject private var previews = ProfileScreenPreview.demoPreviews()
    /// La carte « Mode voyage » (dernière de l'écran), sans voyage en cours : un contrôle qui ne fait rien.
    @StateObject private var travel = TravelViewModel(control: DemoTravelControl())
    /// Les lieux de la Miff regardée (démonstration, sans lecture) : Miff A = créatrice (« Ajouter un lieu »), Miff B = membre (lecture seule).
    @StateObject private var places: ProfilePlacesViewModel
    @StateObject private var placeNotices = PlaceNotices()
    /// Les demandes d'ajout d'un lieu (démonstration) : la Miff B (membre) montre une demande en attente de la personne.
    @StateObject private var requests: ProfileRequestsViewModel

    init(name: String) {
        self.name = name
        let shown = ProfileScreenPreview.shownMiff(for: name)
        _places = StateObject(wrappedValue: ProfileScreenPreview.demoPlaces(selecting: shown))
        _requests = StateObject(wrappedValue: ProfileScreenPreview.demoRequests(selecting: shown))
    }

    private static let miffA = "AbCdEfGhIjKlMnOpQrSt"
    private static let miffB = "ZyXwVuTsRqPoNmLkJiHg"
    private static let me = "demoMe"

    /// Page de départ : « profile-carousel-2 » montre la 2e carte, « profile-carousel-create » la dernière.
    private var initialPage: Int? {
        switch name {
        case "profile-carousel-2":
            return 1
        case "profile-carousel-create":
            return 2
        default:
            return nil
        }
    }

    private var directory: DirectoryState {
        let miffs = [
            MiffSummary(id: Self.miffA, name: "Famille Martin", ownerUid: Self.me),
            MiffSummary(id: Self.miffB, name: "Les copains du foot", ownerUid: "someoneElse")
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
            travel: travel,
            placesModel: places,
            placeNotices: placeNotices,
            requestsModel: requests,
            placesContext: ProfilePlacesContext(myUid: Self.me),
            actions: ProfileTabActions(
                onOpenMyInfo: {},
                onOpenSettings: {},
                onRetry: {},
                onOpenMiff: { _ in },
                onCreateMiff: {}
            )
        )
    }

    /// La Miff dont la liste est montrée : celle de la carte de départ (la dernière carte, « Créer une Miff », garde la dernière Miff).
    private static func shownMiff(for name: String) -> String {
        name == "profile-carousel-2" || name == "profile-carousel-create" ? miffB : miffA
    }

    private static func demoPlace(_ miffId: String, _ id: String, _ name: String, _ type: PlaceType) -> Place {
        Place(
            id: id, miffId: miffId, name: name, type: type, category: type == .risk ? .other : .home,
            address: "26 rue du Volnay, 21000 Dijon, France", lat: 47.32207, lng: 5.04148, radiusM: 150, createdBy: "u1"
        )
    }

    @MainActor
    private static func demoPlaces(selecting miffId: String) -> ProfilePlacesViewModel {
        let model = ProfilePlacesViewModel(reader: DemoPlacesReader())
        let home = demoPlace(miffA, "p1", "Maison de Papa", .trusted)
        let site = demoPlace(miffA, "p2", "Chantier rue Haute", .risk)
        model.seedFromFeed(miffId: miffA, places: [home, site])
        model.seedFromFeed(miffId: miffB, places: [demoPlace(miffB, "p3", "Stade des Poussots", .trusted)])
        model.onCardShown(miffId: miffId)
        return model
    }

    @MainActor
    private static func demoRequests(selecting miffId: String) -> ProfileRequestsViewModel {
        let model = ProfileRequestsViewModel(repository: DemoRequestRepository(), network: DemoNetwork(), myUid: { me })
        model.onCardShown(miffId: miffId, creator: miffId == miffA)
        return model
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

    private struct DemoTravelControl: TravelControl {
        func current() -> TravelPeriod? {
            nil
        }

        func set(_ period: TravelPeriod) async -> TravelResult {
            .unavailable
        }

        func end() async -> Bool {
            false
        }
    }

    private struct DemoNetwork: NetworkMonitor {
        func isOnline() -> Bool {
            true
        }

        func onlineChanges() -> AsyncStream<Bool> {
            AsyncStream { continuation in
                continuation.yield(true)
                continuation.finish()
            }
        }
    }

    /// Un membre (la personne) a UNE demande en attente dans la Miff B ; rien n'est lu ni écrit.
    private struct DemoRequestRepository: PlaceRequestRepository {
        func prepare(uid: String, miffId: String) async -> RequestContextLoad {
            .unavailable
        }

        func submit(
            uid: String,
            miffId: String,
            draft: PlaceRequestDraft,
            context: RequestContext,
            online: Bool
        ) async -> RequestSubmitResult {
            .failed(.unknown)
        }

        func withdraw(_ request: PlaceRequest) async -> RequestWithdrawResult {
            .failed(.unknown)
        }

        func readMine(uid: String, miffId: String) async -> MineRead {
            guard miffId == ProfileScreenPreview.miffB else {
                return .loaded([])
            }
            let request = PlaceRequest(
                id: "\(uid)_1", miffId: miffId, requestedBy: uid, name: "Club de judo", type: .trusted,
                address: "12 rue des Écoles, 21000 Dijon, France", lat: 47.32, lng: 5.04, radiusM: 100,
                category: .sport, createdAtMillis: nil
            )
            return .loaded([request])
        }

        func withdrawAllMine(uid: String, miffId: String) async {}

        func observe(miffId: String) -> AsyncStream<RequestsObservation> {
            AsyncStream { $0.finish() }
        }

        func readAll(miffId: String) async -> MineRead {
            .loaded([])
        }

        func readRequester(miffId: String, uid: String) async -> RequesterState {
            .unknown
        }

        func accept(uid: String, request: PlaceRequest, knownCount: Int) async -> RequestAcceptResult {
            .failed(.unknown)
        }

        func decline(_ request: PlaceRequest, knownCount: Int) async -> RequestDeclineResult {
            .failed(.unknown)
        }

        func purge(miffId: String, requestIds: [String], remaining: Int) async {}
    }

    private struct DemoPlacesReader: PlacesReader {
        func read(miffId: String) async -> PlacesReadResult {
            .failed
        }
    }

    private struct DemoReader: MemberPreviewReader {
        func read(miffId: String) async -> MemberPreview? {
            nil
        }
    }
}
#endif
