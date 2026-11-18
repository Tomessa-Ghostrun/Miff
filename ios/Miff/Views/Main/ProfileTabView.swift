import SwiftUI

/// Les gestes de l'onglet Profil.
struct ProfileTabActions {
    let onOpenMyInfo: () -> Void
    let onOpenSettings: () -> Void
    let onRetry: () -> Void
    let onOpenMiff: (String) -> Void
    /// « Créer une Miff » (dernière carte du carrousel) : l'hôte choisit entre l'offre (compte gratuit qui a déjà une Miff) et le parcours.
    let onCreateMiff: () -> Void
}

/// Ce que l'onglet Profil sait de la personne et des gestes pour la liste des lieux sous le carrousel.
struct ProfilePlacesContext {
    /// La personne connectée : elle est comparée au créateur de la Miff pour savoir quels boutons dessiner.
    var myUid: String?
    var actions = ProfilePlacesActions.none
}

/// L'onglet Profil (maquette « profil », ticket Onboarding §6.8 et décision Q2 du ticket Feed / Profil / Réglages) : la photo (ou les
/// initiales), « Prénom Nom », « C'est toi » et l'engrenage qui ouvre les Réglages ; en dessous, « Mes Miffs » : un carrousel, une carte
/// par Miff (nom, nombre de membres, miniatures des membres ; elle ouvre l'écran de la Miff) et, en dernier, « Créer une Miff ». Un
/// toucher sur la photo ou le nom ouvre « Mes informations ». Aucune donnée inventée : pas de « Famille » par défaut.
/// Sous le carrousel, les lieux de confiance de la Miff dont la carte est montrée, visibles par TOUS ses membres (décision du PM, 2026-11-11).
struct ProfileTabView: View {
    let state: ProfileTabState
    let miffs: DirectoryState
    /// Les membres des cartes (la Miff du Feed : ce que le Feed a déjà lu ; les autres : une lecture ponctuelle à l'apparition).
    @ObservedObject var previews: MiffMemberPreviews
    /// Vrai quand l'onglet Profil est celui en usage : aucune lecture de membres avant.
    let isActive: Bool
    /// La carte montrée au départ (nil : la Miff du Feed ; les écrans de démonstration des captures en choisissent une autre).
    var initialCarouselPage: Int?
    /// MODE VOYAGE (VOY-2I) : la carte « Mode voyage », en DERNIÈRE position de l'écran (après tous les autres blocs), ouverte à tous les comptes.
    @ObservedObject var travel: TravelViewModel
    /// La pop-in « Pas de connexion » du Profil : fermée par la personne, la page dit la même chose à la place de l'indicateur.
    @ObservedObject var offlineRetry: OfflineRetryModel
    /// Les lieux de la Miff du carrousel (lus une fois par Miff, sans écouteur) et le message calme d'un geste.
    @ObservedObject var placesModel: ProfilePlacesViewModel
    /// La carte « Répartition de ton temps » (TIME-4), sous le carrousel : calculée sur le téléphone d'après le journal local, pour la Miff du
    /// carrousel, avec les lieux déjà lus ci-dessus (aucune lecture de plus).
    @ObservedObject var timeShare: TimeShareViewModel
    @ObservedObject var placeNotices: PlaceNotices
    /// Les demandes d'ajout d'un lieu (LIEU-REQ) : celles du membre, lues avec la liste (un membre ne lit que les siennes).
    @ObservedObject var requestsModel: ProfileRequestsViewModel
    let placesContext: ProfilePlacesContext
    let actions: ProfileTabActions
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                // COMPTE À REBOURS (COUNTDOWN-I) : premier bloc de contenu, au-dessus de « Mes Miffs ». L'en-tête et l'encart sont rangés dans
                // UNE pile d'espacement 0 (l'en-tête garde son propre espacement de 24 pt) : l'espace au-dessus de l'encart est porté par
                // l'encart, donc masqué il ne change RIEN à la mise en page d'avant (jamais un enfant vide avec ses 24 pt).
                VStack(alignment: .leading, spacing: 0) {
                    VStack(alignment: .leading, spacing: Spacing.lg) {
                        header
                        if state == .unavailable {
                            unavailable
                        } else if state == .loading && offlineRetry.isInlineVisible {
                            OfflineInlineNote(model: offlineRetry)
                        }
                    }
                    .padding(.horizontal, Spacing.lg)
                    CountdownSlot(isActive: isActive, topSpacing: Spacing.lg)
                        .padding(.horizontal, Spacing.lg)
                }
                miffCards
                timeShareSection
                placesSection
                // MODE VOYAGE : l'encart est le DERNIER bloc de l'écran (décision du PM après la recette du build 26).
                TravelCardView(model: travel)
            }
            .padding(.vertical, Spacing.md)
        }
        .background(gradientProfileHeader.ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text("profile_tab_pane_a11y"))
        // Retour d'un lieu ou d'un formulaire : la liste regardée est relue si elle est périmée. Départ de l'onglet : le message calme s'en va.
        .onAppear {
            if isActive {
                placesContext.actions.onAppear()
            }
        }
        .onChange(of: isActive) { active in
            if !active {
                placesContext.actions.onLeave()
            } else {
                // Le journal a pu changer pendant que l'onglet n'était pas regardé (le suivi y écrit même app fermée).
                timeShare.refresh()
            }
        }
        // La carte suit la Miff du carrousel et ses lieux (déjà lus) ; elle est relue au retour au premier plan.
        .onAppear { syncTimeShare() }
        .onChange(of: placesModel.state) { _ in syncTimeShare() }
        .onChange(of: miffs) { _ in syncTimeShare() }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                timeShare.refresh()
            }
        }
    }

    private var header: some View {
        HStack(spacing: Spacing.sm) {
            switch state {
            case .ready(let profile):
                identity(profile)
            case .loading:
                LoadingIndicator()
            case .unavailable:
                Spacer(minLength: 0)
            }
            gearButton
        }
    }

    private func identity(_ profile: ProfileSummary) -> some View {
        Button(action: actions.onOpenMyInfo) {
            HStack(spacing: Spacing.md) {
                MiffAvatar(jpeg: profile.avatarJpeg, initials: profile.initials, size: 80)
                VStack(alignment: .leading, spacing: 0) {
                    Text(verbatim: profile.displayName)
                        .miffTextStyle(textDisplayHeadline)
                        .foregroundColor(colorTextForm.color)
                        .accessibilityAddTraits(.isHeader)
                    Text("profile_tab_you_label")
                        .miffTextStyle(textBodyRegular)
                        .foregroundColor(colorTextDescription.color)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            }
            .frame(minHeight: Spacing.minTouchTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "\(profile.displayName), \(L10n.string("profile_tab_you_label"))"))
        .accessibilityHint(Text("profile_tab_open_my_info_a11y"))
        .accessibilityAddTraits(.isButton)
    }

    /// L'engrenage des maquettes : un disque blanc, un engrenage bleu, 44 pt au moins.
    private var gearButton: some View {
        Button(action: actions.onOpenSettings) {
            Image(systemName: "gearshape")
                .font(.system(size: Spacing.sectionIcon, weight: .regular))
                .foregroundColor(colorActionPrimary.color)
                .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                .background(Circle().fill(colorBgSurface.color))
                .miffShadow(shadowDefault)
        }
        .accessibilityLabel(Text("profile_tab_settings_a11y"))
    }

    private var unavailable: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("profile_tab_unavailable")
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
            Button(action: actions.onRetry) {
                Text("auth_retry_action")
            }
            .buttonStyle(.miffSecondaryFullWidth)
        }
    }

    /// « Mes Miffs » : le carrousel (une carte par Miff, puis « Créer une Miff »). Le titre garde les marges de l'écran ; le carrousel, lui,
    /// a les siennes (ses cartes sont pleine largeur, ombre comprise).
    @ViewBuilder
    private var miffCards: some View {
        if case .ready(let ready) = miffs {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text("profile_tab_miffs_title")
                    .miffTextStyle(textDisplayTitle)
                    .foregroundColor(colorTextForm.color)
                    .accessibilityAddTraits(.isHeader)
                    .padding(.horizontal, Spacing.lg)
                ProfileMiffCarousel(
                    content: ProfileCarouselContent(
                        miffs: ready.miffs, activeId: ready.activeId, initialPage: initialCarouselPage
                    ),
                    previews: previews,
                    isActive: isActive,
                    actions: ProfileCarouselActions(
                        onOpenMiff: actions.onOpenMiff,
                        onCreate: actions.onCreateMiff,
                        onMiffShown: { miffId in
                            placesModel.onCardShown(miffId: miffId)
                            requestsModel.onCardShown(miffId: miffId, creator: isCreator(of: miffId))
                        },
                        onMiffsKnown: { miffIds in
                            placesModel.retainOnly(miffIds)
                            requestsModel.retainOnly(miffIds: miffIds)
                        }
                    )
                )
                .padding(.horizontal, Spacing.lg)
            }
        }
    }

    /// La carte de répartition du temps de la Miff du carrousel : rien n'est dessiné (pas même une marge) tant que ses lieux n'ont pas été lus,
    /// ni quand elle n'en a aucun (la section « Lieux » porte déjà le message).
    @ViewBuilder
    private var timeShareSection: some View {
        if timeShare.display.state != .hidden {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                TimeShareCard(
                    display: timeShare.display,
                    actions: TimeShareActions(
                        onSelectWindow: { timeShare.selectWindow($0) },
                        onRetry: { timeShare.refresh() },
                        onOpenMiff: {
                            if let miffId = placesModel.state.selectedMiffId {
                                actions.onOpenMiff(miffId)
                            }
                        },
                        onOpenLocationSettings: { ExternalOpener.openSystemSettings() }
                    )
                )
            }
            .padding(.horizontal, Spacing.lg)
        }
    }

    private func syncTimeShare() {
        guard case .ready(let ready) = miffs,
              let presentation = ProfilePlacesPresentation.make(
                  state: placesModel.state, miffs: ready.miffs, myUid: placesContext.myUid
              ),
              case .list(let places) = presentation.body else {
            timeShare.show(miffId: nil, places: nil)
            return
        }
        timeShare.show(miffId: presentation.miffId, places: places)
    }

    /// La personne connectée a-t-elle créé cette Miff ? (le champ `ownerUid` de l'annuaire ; inconnu : non, donc un membre.)
    private func isCreator(of miffId: String) -> Bool {
        PlaceViewerRole.isCreator(ownerUid: miffs.ownerUid(of: miffId), myUid: placesContext.myUid)
    }

    /// Les lieux de la Miff du carrousel, pour tous ses membres. Rien n'est dessiné (pas même une marge) tant qu'ils n'ont pas été lus.
    @ViewBuilder
    private var placesSection: some View {
        if case .ready(let ready) = miffs,
           let presentation = ProfilePlacesPresentation.make(
               state: placesModel.state, miffs: ready.miffs, myUid: placesContext.myUid
           ) {
            ProfilePlacesSection(
                presentation: presentation,
                // Miff+ n'est affirmé (ou nié) que s'il a été LU auprès du serveur : sinon nil, et la liste n'affirme rien de la formule gratuite.
                premium: ready.premiumKnown ? ready.premium : nil,
                notice: placeNotices.notice,
                actions: placesContext.actions,
                requests: ProfileRequestsContent.make(
                    state: requestsModel.state,
                    miffId: presentation.miffId,
                    isCreator: presentation.isCreator,
                    sentNotice: placeNotices.requestNotice,
                    kind: requestsModel.notice
                )
            )
            .padding(.horizontal, Spacing.lg)
        }
    }
}
