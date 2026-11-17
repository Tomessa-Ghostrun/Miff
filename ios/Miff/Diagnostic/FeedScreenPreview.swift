#if DEBUG
import SwiftUI

/// Le Feed montré SEUL avec un état fixe, pour les captures d'écran de la CI (`-MiffPreview feed-green`, `feed-orange`, `feed-red`). Aucun
/// réseau, aucune session, aucune écriture : c'est la VRAIE mise en page (`FeedCollapsingLayout`, `FeedListView`, `FeedHeaderContent`, la
/// vraie bulle et les vraies cartes) nourrie par des données de démonstration (jamais de vraies personnes). Une ligne ROSE fine marque où la
/// liste croit que l'en-tête s'arrête : la bulle doit commencer sous elle, jamais coupée. Compilé en configuration Debug SEULEMENT.
struct FeedScreenPreview: View {
    let name: String
    @StateObject private var offlineRetry = OfflineRetryModel(network: AlwaysOnlineMonitor())

    private static let miffId = "AbCdEfGhIjKlMnOpQrSt"
    private static let now = CountdownPreviewSupport.now

    private var summary: MiffSummary {
        MiffSummary(id: Self.miffId, name: "Les Mfouka")
    }

    private var ready: DirectoryReady {
        DirectoryReady(miffs: [summary], activeId: Self.miffId, premium: false, fromCache: false)
    }

    private func member(_ uid: String, _ first: String) -> MiffMember {
        MiffMember(
            uid: uid, firstName: first, lastName: "Demo", avatarJpeg: nil,
            role: .member, sharingEnabled: true, phone: "+33612345678"
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
        case "feed-red":
            presence = .out
            ago = 6 * 60 * 60 * 1000
        default:
            presence = .inTrusted
            ago = 12 * 60 * 1000
        }
        // COMPTE À REBOURS : MA carte est verte, dehors d'un lieu (le décompte est dans le contexte, voir `body`).
        let mine = CountdownPreviewSupport.myFacts(for: name).map { facts in
            MemberStatus(
                presence: facts.presence, placeId: nil, presenceAtMillis: facts.presenceAtMillis,
                lastUpdateAtMillis: nil, hasPhoto: false, batteryPct: 80, locationAccess: nil, updatedAtMillis: Self.now
            )
        }
        let lea = MemberStatus(
            presence: presence, placeId: nil, presenceAtMillis: Self.now - ago, lastUpdateAtMillis: nil,
            hasPhoto: false, batteryPct: 80, locationAccess: nil, updatedAtMillis: Self.now
        )
        return ["me": mine, "lea": lea].compactMapValues { $0 }
    }

    private var content: FeedContent {
        let input = FeedInput(
            miffId: Self.miffId,
            miffName: summary.name,
            joinCode: "XTZRSP367988",
            members: [member("lea", "Léa"), member("me", "Moi")],
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
            bubble: BubbleActions(onMessage: { _ in }, onResumeSharing: {}),
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
                isActive: CountdownPreviewSupport.remainingMillis(for: name) != nil,
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
        .environment(\.countdownContext, CountdownPreviewSupport.context(for: name))
    }
}
#endif
