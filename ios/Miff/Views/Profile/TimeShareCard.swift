import SwiftUI

// La carte « Répartition du temps » de l'onglet Profil (ticket « Profil, répartition du temps », TIME-4), sous le carrousel de Miffs, mise en
// page d'après la maquette du PM (retour du 2026-11-17) : carte blanche très arrondie ; en-tête (pastille verte, titre, « Sur les N derniers
// jours », sélecteur déroulant à droite) ; légende à GAUCHE (les 4 parts les plus longues : pastille, emoji, nom, % à droite) et anneau plus
// petit à DROITE (toutes les tranches), avec au centre le pourcentage en zone de confiance ; encart vert très léger « Tu as passé x % de ton temps
// dans tes zones de confiance » avec la coche et 🎉 ; ligne de confidentialité, toujours visible. Tout est calculé sur le téléphone ; rien n'est
// envoyé. Accessibilité : l'anneau est décoratif, UNE description liste TOUTES les parts (le NOM de la catégorie, jamais l'emoji), le sélecteur est
// annoncé « Période, 7 jours » (le choix actuel « choisi »), la ligne de la zone de danger est un bouton qui ouvre le détail ; aux grandes tailles de
// texte la légende passe SOUS l'anneau. Sans 7 jours de données : pas de graphique, pas de sélecteur, seulement le texte figé par le PM.

/// Les gestes de la carte.
struct TimeShareActions {
    var onSelectWindow: (Int) -> Void = { _ in }
    var onRetry: () -> Void = {}
    /// « Ouvrir la Miff » (partage arrêté : l'écran de la Miff permet de le reprendre).
    var onOpenMiff: () -> Void = {}
    /// Les réglages de l'iPhone pour Miff (localisation « Toujours » avec la position précise).
    var onOpenLocationSettings: () -> Void = {}
}

struct TimeShareCard: View {
    let display: TimeShareDisplay
    let actions: TimeShareActions

    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var showsDanger = false

    /// Aux grandes tailles de texte (accessibilité), l'anneau passe au-dessus de la légende et le sélecteur sous le titre.
    private var stacked: Bool {
        typeSize.isAccessibilitySize
    }

    /// Le texte du centre tient dans le trou de l'anneau jusqu'à cette taille ; au-delà il passe sous l'anneau.
    private var captionInside: Bool {
        typeSize <= .large
    }

    private static let donutSize: CGFloat = 108
    /// Le rayon de la carte de la maquette (plus arrondie que les cartes du design system, voir l'écart listé dans la PR).
    private static let cornerRadius: CGFloat = 20

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            header
            content
            privacyLine
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: Self.cornerRadius, style: .continuous)
                .fill(colorBgSurface.color)
                .miffShadow(shadowDefault)
        )
        .accessibilityElement(children: .contain)
        .sheet(isPresented: $showsDanger) {
            dangerSheet
        }
    }

    // MARK: - L'en-tête

    /// Le sélecteur et le sous-titre n'existent qu'avec le graphique.
    private var showsPeriod: Bool {
        if case .nominal = display.state {
            return true
        }
        return false
    }

    private var header: some View {
        let layout = stacked ? AnyLayout(VStackLayout(alignment: .leading, spacing: Spacing.sm))
            : AnyLayout(HStackLayout(alignment: .center, spacing: Spacing.sm))
        return layout {
            if !stacked {
                headerIcon
            }
            titles
            if !stacked {
                Spacer(minLength: Spacing.xs)
            }
            if showsPeriod {
                periodMenu
            }
        }
    }

    /// La pastille d'icône verte de la maquette : un disque vert très léger avec le pictogramme en vert.
    private var headerIcon: some View {
        Image(systemName: "chart.bar.fill")
            .font(.system(size: 18, weight: .semibold))
            .foregroundColor(colorChart1.color)
            .frame(width: 40, height: 40)
            .background(Circle().fill(colorChartGreenTintStrong.color))
            .accessibilityHidden(true)
    }

    private var titles: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("profile_timeshare_title")
                .miffTextStyle(textBodyBold16)
                .foregroundColor(colorTextForm.color)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            if showsPeriod {
                Text(verbatim: TimeShareTexts.subtitle(window: display.windowDays))
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: stacked ? .infinity : nil, alignment: .leading)
    }

    /// « 7 jours ⌄ » : un menu à trois choix, le choix actuel coché. 44 pt au moins ; annoncé « Période, 7 jours, bouton ».
    private var periodMenu: some View {
        let current = TimeShareTexts.windowLabel(display.windowDays)
        return Menu {
            ForEach(TimeShareLimits.windowsDays, id: \.self) { days in
                periodChoice(days)
            }
        } label: {
            HStack(spacing: Spacing.xs) {
                Text(verbatim: current)
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextForm.color)
                Image(systemName: "chevron.down")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(colorTextForm.color)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, Spacing.md)
            .frame(minHeight: Spacing.minTouchTarget)
            .overlay(Capsule().stroke(colorBorderDefault.color, lineWidth: strokeHairline))
            .contentShape(Capsule())
        }
        .accessibilityLabel(Text(verbatim: L10n.format("profile_timeshare_window_a11y", current)))
    }

    /// Un choix du menu : le choix actuel est coché (et dit « choisi » au lecteur d'écran).
    private func periodChoice(_ days: Int) -> some View {
        let chosen = days == display.windowDays
        return Button {
            actions.onSelectWindow(days)
        } label: {
            if chosen {
                Label(TimeShareTexts.windowLabel(days), systemImage: "checkmark")
            } else {
                Text(verbatim: TimeShareTexts.windowLabel(days))
            }
        }
        .accessibilityValue(chosen ? Text("profile_timeshare_window_selected_a11y") : Text(verbatim: ""))
    }

    // MARK: - Le contenu, selon l'état

    @ViewBuilder
    private var content: some View {
        switch display.state {
        case .hidden:
            EmptyView()
        case .unavailable:
            message("profile_timeshare_error")
            action("auth_retry_action", actions.onRetry)
        case .travel:
            message("profile_timeshare_travel")
        case .sharingStopped:
            message("profile_timeshare_sharing_stopped")
            action("profile_timeshare_sharing_stopped_action", actions.onOpenMiff)
        case .locationInsufficient:
            message("profile_timeshare_location_needed")
            action("update_action_open_settings", actions.onOpenLocationSettings)
        case .notEnough(let daysLeft):
            notEnough(daysLeft)
        case .nominal(let result, let pause):
            nominal(result, pause: pause)
        }
    }

    private func message(_ key: String) -> some View {
        Text(LocalizedStringKey(key))
            .miffTextStyle(textBodyRegular14)
            .foregroundColor(colorTextDescription.color)
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
    }

    private func action(_ key: String, _ run: @escaping () -> Void) -> some View {
        Button(action: run) {
            Text(LocalizedStringKey(key))
        }
        .buttonStyle(.miffSecondaryFullWidth)
    }

    /// Moins de 7 jours d'historique, suivi actif : PAS de graphique, l'anneau vide et neutre (décor) et le texte figé par le PM.
    private func notEnough(_ daysLeft: Int) -> some View {
        let layout = stacked ? AnyLayout(VStackLayout(alignment: .leading, spacing: Spacing.md))
            : AnyLayout(HStackLayout(alignment: .center, spacing: Spacing.md))
        return layout {
            Text(verbatim: TimeShareTexts.notEnough(daysLeft: daysLeft))
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextDescription.color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            TimeShareDonut(slices: [], size: 88)
        }
    }

    // MARK: - L'état nominal

    @ViewBuilder
    private func nominal(_ result: TimeShareResult, pause: Bool) -> some View {
        chart(result)
        summary(result)
        if pause {
            note("profile_timeshare_paused")
        }
        if display.overWatchedLimit {
            note("profile_timeshare_over_limit")
        }
    }

    /// La légende à gauche (4 parts au plus) et l'anneau à droite ; sous les très grandes tailles de texte, l'anneau passe au-dessus. Pour le
    /// lecteur d'écran : l'anneau (caché, décoratif) est remplacé par UNE description qui liste TOUTES les parts ; les lignes de la légende ne
    /// sont pas relues (pas de doublon), sauf celle de la zone de danger, un bouton qui ouvre le détail.
    private func chart(_ result: TimeShareResult) -> some View {
        let layout = stacked ? AnyLayout(VStackLayout(alignment: .center, spacing: Spacing.md))
            : AnyLayout(HStackLayout(alignment: .center, spacing: Spacing.md))
        let styles = TimeShareDonutLayout.styles(for: result.rows)
        return layout {
            if stacked {
                donutColumn(result)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(Text(verbatim: TimeShareTexts.spokenSummary(result)))
                legend(result, styles: styles)
            } else {
                legend(result, styles: styles)
                donutColumn(result)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(Text(verbatim: TimeShareTexts.spokenSummary(result)))
            }
        }
    }

    private func donutColumn(_ result: TimeShareResult) -> some View {
        VStack(spacing: Spacing.xs) {
            ZStack {
                TimeShareDonut(slices: TimeShareDonutLayout.slices(for: result), size: Self.donutSize)
                VStack(spacing: 0) {
                    Text(verbatim: TimeShareTexts.percent(result.trustedPercent))
                        .miffTextStyle(textTitleBold20)
                        .foregroundColor(colorTextForm.color)
                        .lineLimit(1)
                        .minimumScaleFactor(0.5)
                    if captionInside {
                        Text("profile_timeshare_center_caption")
                            .miffTextStyle(textCaptionMicro10)
                            .foregroundColor(colorTextForm.color)
                            .multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .frame(width: Self.donutSize * 0.58)
                // Le texte du centre tient dans le trou de l'anneau : sa taille suit le texte du système jusqu'à cette limite.
                .dynamicTypeSize(...DynamicTypeSize.xxLarge)
            }
            if !captionInside {
                Text("profile_timeshare_center_caption")
                    .miffTextStyle(textBodyCaption)
                    .foregroundColor(colorTextForm.color)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: stacked ? .infinity : Self.donutSize + Spacing.xs)
    }

    private func legend(_ result: TimeShareResult, styles: [TimeShareSliceStyle]) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            ForEach(result.legendIndices, id: \.self) { index in
                legendRow(result.rows[index], style: styles[index])
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text("profile_timeshare_a11y_list"))
    }

    @ViewBuilder
    private func legendRow(_ row: TimeShareRow, style: TimeShareSliceStyle) -> some View {
        if case .danger = row.kind {
            Button {
                showsDanger = true
            } label: {
                TimeShareLegendRow(row: row, style: style, opensDetail: true)
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: TimeShareTexts.spokenDangerRow(row)))
            .accessibilityAddTraits(.isButton)
        } else {
            // Déjà dite par la description de l'anneau.
            TimeShareLegendRow(row: row, style: style)
                .accessibilityHidden(true)
        }
    }
}

extension TimeShareCard {
    /// L'encart de synthèse de la maquette : fond vert très léger, coche, « Tu as passé x % de ton temps dans tes zones de confiance » et 🎉 à
    /// droite. Affiché dans tous les cas où le graphique l'est (décision du PM : le ton est assumé, y compris pour un très petit pourcentage) ;
    /// ne nomme jamais la zone de danger.
    private func summary(_ result: TimeShareResult) -> some View {
        HStack(alignment: .center, spacing: Spacing.sm) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 22))
                .foregroundColor(colorChart1.color)
                .accessibilityHidden(true)
            Text(verbatim: TimeShareTexts.summary(trustedPercent: result.trustedPercent))
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextForm.color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            Text(verbatim: "🎉")
                .miffTextStyle(textBodyLarge)
                .accessibilityHidden(true)
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorChartGreenTint.color))
    }

    /// Une ligne calme sous l'anneau (suivi en pause, zones non toutes suivies).
    private func note(_ key: String) -> some View {
        Text(LocalizedStringKey(key))
            .miffTextStyle(textBodyCaption)
            .foregroundColor(colorTextDescription.color)
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: - La confidentialité, toujours visible

    private var privacyLine: some View {
        HStack(alignment: .top, spacing: Spacing.xs) {
            Image(systemName: "lock")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(colorTextDescription.color)
                .padding(.top, 2)
                .accessibilityHidden(true)
            Text("profile_timeshare_privacy")
                .miffTextStyle(textBodyCaption)
                .foregroundColor(colorTextDescription.color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: - Le détail de la zone de danger

    private func hasDanger(_ result: TimeShareResult) -> Bool {
        !result.dangerDetails.isEmpty
    }

    @ViewBuilder
    private var dangerSheet: some View {
        if case .nominal(let result, _) = display.state, hasDanger(result) {
            TimeShareDangerSheet(
                placesCount: result.dangerDetails.count,
                details: result.dangerDetails,
                onClose: { showsDanger = false }
            )
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        } else {
            // Les données ont changé pendant que la feuille était ouverte : elle se ferme d'elle-même.
            Color.clear
                .onAppear {
                    showsDanger = false
                }
        }
    }
}
