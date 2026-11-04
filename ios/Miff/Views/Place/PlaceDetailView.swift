import SwiftUI

/// Ce qui relance l'écoute du lieu : l'app qui revient au premier plan, ou « Réessayer ».
private struct PlaceObserveKey: Hashable {
    let active: Bool
    let restart: Int
}

/// Où le détail d'un lieu mène : retour, formulaire de modification, et sortie quand le lieu n'existe plus.
struct PlaceDetailNavigation {
    let onBack: () -> Void
    let onEdit: () -> Void
    let onGone: () -> Void
}

/// Le détail d'un lieu relié à son view model. Il LIT la Miff tant qu'il est à l'écran : l'écoute s'arrête quand l'écran disparaît
/// ou que l'app passe en arrière-plan (rien n'écoute une Miff que personne ne regarde).
@MainActor
struct PlaceDetailView: View {
    @StateObject private var viewModel: PlaceDetailViewModel
    @Environment(\.scenePhase) private var scenePhase
    @State private var restart = 0
    private let onBack: () -> Void
    private let onEdit: () -> Void
    private let onGone: () -> Void

    init(
        miffId: String,
        placeId: String,
        dependencies: PlaceDetailDependencies,
        navigation: PlaceDetailNavigation
    ) {
        _viewModel = StateObject(
            wrappedValue: PlaceDetailViewModel(miffId: miffId, placeId: placeId, dependencies: dependencies)
        )
        onBack = navigation.onBack
        onEdit = navigation.onEdit
        onGone = navigation.onGone
    }

    var body: some View {
        let model = viewModel
        let key = PlaceObserveKey(active: scenePhase != .background, restart: restart)
        PlaceDetailContent(
            state: model.state,
            onBack: onBack,
            onEdit: onEdit,
            onRetry: {
                model.retry()
                restart += 1
            }
        )
        .task(id: key) {
            if key.active {
                await model.observe()
            }
        }
        .onChange(of: model.state.status) { status in
            if status == .gone {
                onGone()
            }
        }
    }
}

/// Le détail d'un lieu (maquette 16) : la carte avec le cercle, l'adresse, « Voir sur la carte » (l'app Plans, sur le LIEU), puis
/// le rayon, le type, la catégorie, qui voit le lieu et qui l'a créé. Lecture seule ; « Modifier » (le créateur de la Miff
/// seulement) ouvre le formulaire. Sans état propre.
struct PlaceDetailContent: View {
    let state: PlaceDetailState
    let onBack: () -> Void
    let onEdit: () -> Void
    let onRetry: () -> Void

    private var title: String {
        state.place?.name ?? L10n.string("place_detail_title_loading")
    }

    var body: some View {
        VStack(spacing: 0) {
            PlaceDetailHeader(title: title, onBack: onBack, onEdit: state.canEdit && state.place != nil ? onEdit : nil)
                .padding(.horizontal, Spacing.lg)
                .padding(.top, Spacing.sm)
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    if state.offline {
                        MiffNotice(text: L10n.string("place_detail_cache_notice"))
                    }
                    switch state.status {
                    case .loading:
                        LoadingIndicator()
                    case .ready:
                        if let place = state.place {
                            PlaceDetailBody(place: place, state: state)
                        }
                    case .unavailable:
                        MiffNotice(text: L10n.string("miff_unavailable"))
                        Button(action: onRetry) {
                            Text("auth_retry_action")
                        }
                        .buttonStyle(.miffSecondaryFullWidth)
                    case .gone, .accessLost:
                        // Le message de la perte d'accès est dessiné par-dessus tout par l'hôte ; « Ce lieu n'existe plus » est
                        // dit par la liste, où l'on revient.
                        EmptyView()
                    }
                }
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.sm)
            }
        }
        .background(colorBgCanvas.color.ignoresSafeArea())
        .hidesInAppSwitcher()
    }
}

/// L'en-tête du détail : la flèche, le NOM du lieu, et « Modifier » à la fin pour le créateur seulement.
struct PlaceDetailHeader: View {
    let title: String
    let onBack: () -> Void
    let onEdit: (() -> Void)?

    var body: some View {
        ZStack {
            Text(verbatim: title)
                .miffTextStyle(textDisplayTitle)
                .foregroundColor(colorTextForm.color)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, Spacing.minTouchTarget + Spacing.md)
                .accessibilityAddTraits(.isHeader)
            HStack {
                Button(action: onBack) {
                    Image(systemName: "arrow.backward")
                        .font(.system(size: 20, weight: .regular))
                        .foregroundColor(colorTextForm.color)
                        .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                }
                .accessibilityLabel("auth_back_a11y")
                Spacer()
                if let onEdit {
                    Button(action: onEdit) {
                        Text("place_detail_edit")
                            .miffTextStyle(textBodyBold16)
                            .foregroundColor(colorActionPrimary.color)
                            .frame(minWidth: Spacing.minTouchTarget, minHeight: Spacing.minTouchTarget)
                    }
                }
            }
        }
        .frame(minHeight: Spacing.minTouchTarget + Spacing.sm)
    }
}

/// Le contenu d'un lieu lu : la carte, l'adresse, « Voir sur la carte », puis la carte des faits.
struct PlaceDetailBody: View {
    let place: Place
    let state: PlaceDetailState

    @Environment(\.openURL) private var openURL
    @State private var noMapApp = false

    private var address: String {
        place.address ?? ""
    }

    var body: some View {
        PlaceMapBlock(
            chosen: ResolvedAddress(label: address, lat: place.lat, lng: place.lng),
            radiusM: place.radiusM,
            isDanger: place.type == .risk,
            height: 210
        )
        if !address.isEmpty {
            addressRow
        }
        Button(action: openMap) {
            HStack(spacing: Spacing.sm) {
                Text("place_detail_view_map")
                Image(systemName: "arrow.up.right.square")
                    .accessibilityHidden(true)
            }
        }
        .buttonStyle(.miffSecondaryFullWidth)
        if noMapApp {
            MiffNotice(text: L10n.string("place_detail_no_map_app"))
        }
        facts
        if PlaceFeatures.explainsWhatItChanges {
            let key = place.type == .trusted ? "place_detail_info_trusted" : "place_detail_info_danger"
            PlaceInfoLine(text: L10n.string(key))
        }
    }

    private var addressRow: some View {
        let lines = PlaceAddressLines.split(address)
        return HStack(alignment: .center, spacing: Spacing.md) {
            PlaceCategoryIcon(category: nil, size: 44)
            VStack(alignment: .leading, spacing: 0) {
                Text(verbatim: lines.title)
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorTextForm.color)
                if !lines.subtitle.isEmpty {
                    Text(verbatim: lines.subtitle)
                        .miffTextStyle(textBodyRegular14)
                        .foregroundColor(colorTextDescription.color)
                }
            }
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }

    /// Ouvre l'application Plans sur le LIEU (ses coordonnées, jamais la position d'une personne). Sans Plans : une phrase calme.
    private func openMap() {
        guard let url = PlaceMapLink.url(lat: place.lat, lng: place.lng) else {
            noMapApp = true
            return
        }
        openURL(url) { accepted in
            noMapApp = !accepted
        }
    }

    private var facts: some View {
        VStack(spacing: 0) {
            PlaceFactRow(symbol: "scope", label: L10n.string("place_detail_radius")) {
                Text(verbatim: L10n.format("place_radius_value", place.radiusM))
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorTextPrimary.color)
            }
            divider
            PlaceFactRow(symbol: "eye", label: L10n.string("place_detail_type")) {
                PlaceTypeTag(type: place.type)
            }
            divider
            PlaceFactRow(symbol: "square.grid.2x2", label: L10n.string("place_detail_category")) {
                categoryValue
            }
            divider
            PlaceFactRow(symbol: "person.2", label: L10n.string("place_detail_visible_by")) {
                Text(verbatim: state.miffName)
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorTextPrimary.color)
            }
            if !state.creatorName.isEmpty {
                divider
                PlaceFactRow(symbol: "pencil", label: L10n.string("place_detail_created_by")) {
                    Text(verbatim: creator)
                        .miffTextStyle(textBodyRegular)
                        .foregroundColor(colorTextPrimary.color)
                }
            }
        }
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
        )
    }

    private var divider: some View {
        Rectangle().fill(colorBorderDefault.color).frame(height: strokeHairline)
    }

    @ViewBuilder
    private var categoryValue: some View {
        if let category = place.category {
            Text(verbatim: category.label)
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextPrimary.color)
        }
    }

    private var creator: String {
        state.creatorIsMe ? L10n.format("place_detail_created_by_me", state.creatorName) : state.creatorName
    }
}

/// Une ligne de la carte des faits : un pictogramme, un petit libellé, puis la valeur.
struct PlaceFactRow<Value: View>: View {
    let symbol: String
    let label: String
    let value: Value

    init(symbol: String, label: String, @ViewBuilder value: () -> Value) {
        self.symbol = symbol
        self.label = label
        self.value = value()
    }

    var body: some View {
        HStack(spacing: Spacing.md) {
            Image(systemName: symbol)
                .font(.system(size: 18, weight: .regular))
                .foregroundColor(colorActionPrimary.color)
                .frame(width: 40, height: 40)
                .background(
                    RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorActionPrimaryTint20.color)
                )
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(verbatim: label)
                    .miffTextStyle(textBodyCaption)
                    .foregroundColor(colorTextSecondary.color)
                value
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.sm)
        .frame(minHeight: Spacing.minTouchTarget + Spacing.md)
        .accessibilityElement(children: .combine)
    }
}
