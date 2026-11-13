#if DEBUG
import SwiftUI

/// Le Feed montré SEUL avec un état fixe, pour les captures d'écran de la CI (`-MiffPreview feed-green`, `feed-orange`, `feed-red`, `feed-gray` : pas encore de nouvelles, `feed-stopped` : partage arrêté,
/// `feed-carousel` : deux alertes ; chaque bulle montre le bouton « i » en bas à droite). Aucun
/// réseau, aucune session, aucune écriture : c'est la VRAIE mise en page (`FeedCollapsingLayout`, `FeedListView`, `FeedHeaderContent`, la
/// vraie bulle et les vraies cartes) nourrie par des données de démonstration (jamais de vraies personnes). Une ligne ROSE fine marque où la
/// liste croit que l'en-tête s'arrête : la bulle doit commencer sous elle, jamais coupée. Compilé en configuration Debug SEULEMENT.
struct FeedScreenPreview: View {
    let name: String
    @StateObject private var offlineRetry = OfflineRetryModel(network: AlwaysOnlineMonitor())

    private static let miffId = "AbCdEfGhIjKlMnOpQrSt"
    private static let now = 1_794_063_600_000

    private var summary: MiffSummary {
        MiffSummary(id: Self.miffId, name: "Les Mfouka")
    }

    private var ready: DirectoryReady {
        DirectoryReady(miffs: [summary], activeId: Self.miffId, premium: false, fromCache: false)
    }

    private func member(_ uid: String, _ first: String, sharing: Bool = true) -> MiffMember {
        MiffMember(
            uid: uid, firstName: first, lastName: "Demo", avatarJpeg: nil,
            role: .member, sharingEnabled: sharing, phone: "+33612345678"
        )
    }

    /// vert : Léa est dans un lieu depuis 12 min ; orange : sortie depuis 3 h ; rouge : sortie depuis 6 h.
    private var statuses: [String: MemberStatus] {
        let presence: PresenceValue
        let ago: Int
        switch name {
        case "feed-orange":
            presence = .out
            ago = 3 * 60 * 60 * 1000
        case "feed-red", "feed-carousel":
            presence = .out
            ago = 6 * 60 * 60 * 1000
        default:
            presence = .inTrusted
            ago = 12 * 60 * 1000
        }
        guard name != "feed-gray" else {
            return [:]
        }
        let lea = MemberStatus(
            presence: presence, placeId: nil, presenceAtMillis: Self.now - ago, lastUpdateAtMillis: nil,
            hasPhoto: false, batteryPct: 80, locationAccess: nil, updatedAtMillis: Self.now
        )
        guard name == "feed-carousel" else {
            return ["lea": lea]
        }
        // Deux alertes : Léa rouge (sortie depuis 6 h) et Sam orange (sortie depuis 3 h).
        let sam = MemberStatus(
            presence: .out, placeId: nil, presenceAtMillis: Self.now - 3 * 60 * 60 * 1000, lastUpdateAtMillis: nil,
            hasPhoto: false, batteryPct: 60, locationAccess: nil, updatedAtMillis: Self.now
        )
        return ["lea": lea, "sam": sam]
    }

    private var members: [MiffMember] {
        var all = [member("lea", "Léa")]
        if name == "feed-carousel" {
            all.append(member("sam", "Sam"))
        }
        all.append(member("me", "Moi", sharing: name != "feed-stopped"))
        return all
    }

    private var content: FeedContent {
        let input = FeedInput(
            miffId: Self.miffId,
            miffName: summary.name,
            joinCode: "XTZRSP367988",
            members: members,
            places: [],
            myUid: "me",
            statuses: .loaded(byUid: statuses, fromCache: false),
            waitingAgreement: false,
            offline: false
        )
        let zone = TimeZone(identifier: "Europe/Paris") ?? .current
        return FeedBuilder.build(input: input, nowMillis: Self.now, zone: zone)
    }

    private var listActions: FeedListActions {
        FeedListActions(
            card: MemberCardActions(onLocate: { _ in }, onMessage: { _ in }, onCall: { _ in }),
            bubble: BubbleActions(onMessage: { _ in }, onResumeSharing: {}, onOpenColors: {}),
            onRetry: {},
            onReviewAgreement: {},
            onRefresh: {},
            onSecondChanceActivate: {},
            onSecondChanceDismiss: {}
        )
    }

    private func header(_ onLogo: @escaping () -> Void) -> FeedHeaderContent {
        FeedHeaderContent(
            me: FeedMe(initials: "MD", avatarJpeg: nil),
            myLevel: .green,
            myStatusDescription: nil,
            pill: FeedPillState.make(from: ready),
            showsBell: false,
            unreadNotifications: 0,
            onOpenNotifications: {},
            onOpenProfile: {},
            onLogo: onLogo,
            onSelect: { _ in },
            onCreate: {},
            onJoin: {},
            selectorOpen: .constant(false)
        )
    }

    var body: some View {
        FeedCollapsingLayout(locked: false, isList: true, refreshing: false, header: header) { layout in
            FeedListView(
                feed: .ready(content),
                ready: ready,
                offlineRetry: offlineRetry,
                locate: .idle,
                texts: FeedTexts(),
                topInset: layout.listInset,
                isActive: false,
                agreementWaiting: false,
                secondChanceDue: false,
                scrollToTop: layout.scrollToTop,
                actions: listActions,
                onContentMoved: layout.onContentMoved,
                onScrolled: layout.onScrolled,
                onOrigin: layout.onOrigin
            )
            .overlay(alignment: .top) {
                Rectangle().fill(Color.pink).frame(height: 1).padding(.top, layout.listInset)
            }
        }
    }
}
#endif
