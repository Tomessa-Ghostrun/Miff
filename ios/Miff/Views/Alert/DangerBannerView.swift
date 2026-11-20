#if ALERT_DANGER
import SwiftUI
import UIKit

// La bannière d'une alerte en cours (maquette 05, docs/tickets/alerte-danger.md 7.4 ; miroir d'`AlertBanner` d'Android), posée au-dessus de la barre d'onglets,
// sur tous les onglets. Pendant l'alerte en cours, elle pose la question en GRAND titre (« Mettre fin à l'alerte ? », deux fois la taille d'avant) puis la consigne (« Maintiens
// 5 secondes pour mettre fin à l'alerte… ») : textes donnés par le PM en recette, sans nom de Miff (pendant l'ENVOI, elle nomme encore la Miff d'origine). Maintenir le corps 5 secondes termine l'alerte (un
// voile clair se remplit depuis le début, le même haptique) ; « Appeler » rouvre l'écran des numéros ; « Voir la Miff {nom} » montre la Miff d'origine quand le Feed
// en montre une autre. Pas de croix tant que l'alerte est en cours : elle part à la fin ou à l'échéance. Tant que l'alerte est encore EN ROUTE (pas de réseau,
// refusée, limite du jour, plus de 15 minutes), la bannière le dit calmement ET offre une croix pour l'ABANDONNER (retour de la recette du PM) : l'abandon n'efface
// le pointeur qu'après une relecture du serveur (`DangerAlertController.cancelPending`).
//
// Lecteur d'écran : le texte est une région lue d'un bloc ; l'action « Terminer l'alerte » (avec confirmation en deux étapes) remplace le maintien ; la croix a
// un nom parlé.

struct DangerBannerActions {
    var onCall: () -> Void = {}
    var onSeeMiff: () -> Void = {}
    /// Les 5 secondes sont tenues sur la bannière : l'alerte prend fin.
    var onEnd: () -> Void = {}
    /// L'action d'accessibilité « Terminer l'alerte » (confirmation en deux étapes).
    var onEndAccessible: () -> Void = {}
    var onRetry: () -> Void = {}
    /// La croix : abandonne l'envoi d'une alerte qui n'est pas partie.
    var onClose: () -> Void = {}
}

struct DangerBannerView: View {
    let alert: MyDangerAlert
    /// nil : le nom de la Miff n'est pas (encore) connu (après un relancement, l'annuaire n'est pas lu) : la phrase sans nom est dite, jamais un trou.
    let miffName: String?
    let showSeeMiff: Bool
    let actions: DangerBannerActions
    @StateObject private var hold = DangerHoldModel()

    private static let shape = RoundedRectangle(cornerRadius: 10, style: .continuous)

    private var endable: Bool {
        if case .active(_, _, _, let step) = alert {
            return step != .ending
        }
        return false
    }

    private var sendStep: DangerSendStep? {
        if case .sending(_, _, let step) = alert {
            return step
        }
        return nil
    }

    private var closable: Bool {
        sendStep.map { $0 != .sending } ?? false
    }

    /// Le plus haut Dynamic Type que suit le grand titre : au-delà, un titre de 32 pt de départ remplirait l'écran (le texte qui suit, lui, continue de grandir).
    static let titleMaxDynamicType = DynamicTypeSize.accessibility1

    /// Le grand titre (« Mettre fin à l'alerte ? ») est celui de l'alerte EN COURS ; l'envoi, lui, garde le titre de taille normale.
    private var hasBigTitle: Bool {
        if case .sending = alert {
            return false
        }
        return true
    }

    private var title: String {
        if case .sending = alert {
            let fallback = L10n.string("alert_banner_sending_no_name")
            return miffName.map { L10n.format("alert_banner_sending", $0) } ?? fallback
        }
        // Retour du PM : la question « Mettre fin à l'alerte ? » ne nomme plus la Miff (le lien « Voir la Miff {nom} » le fait quand le Feed en montre une autre).
        return L10n.string("alert_banner_title")
    }

    private var note: String? {
        if let step = sendStep {
            switch step {
            case .waitingNetwork:
                return L10n.string("alert_banner_waiting")
            case .needsConfirmation:
                return L10n.string("alert_banner_needs_confirmation")
            case .refused:
                return L10n.string("alert_banner_refused")
            case .quota:
                return L10n.plural("alert_banner_quota", count: DangerAlertRules.maxStartsPerDay)
            case .sending:
                return nil
            }
        }
        if case .active(_, _, _, .waitingNetwork) = alert {
            return L10n.string("alert_banner_end_pending")
        }
        return L10n.string("alert_banner_body")
    }

    private var secondActionKey: String {
        switch sendStep {
        case nil:
            return "alert_banner_call"
        case .needsConfirmation?:
            return "alert_banner_decide"
        default:
            return "alert_banner_emergency"
        }
    }

    var body: some View {
        if alert != .idle {
            content
                .background(Self.shape.fill(colorAlertBanner.color))
                .overlay(veil)
                .clipShape(Self.shape)
                .modifier(BannerHold(model: hold, enabled: endable))
                .onAppear {
                    hold.onDone = actions.onEnd
                }
                // Le geste disparaît de la vue quand la fin est en cours (`endable` faux) : un doigt encore posé ne le préviendrait jamais de son
                // relâchement, il serait alors bloqué « doigt posé » pour la prochaine tentative (fin hors ligne). On le remet à zéro.
                .onChange(of: endable) { _ in
                    hold.cancel()
                }
        }
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            HStack(alignment: .top, spacing: Spacing.sm) {
                textBlock
                if closable {
                    closeButton
                }
            }
            HStack(spacing: Spacing.md) {
                if sendStep == .refused {
                    action("alert_banner_retry", actions.onRetry)
                }
                // « Appeler » est pour les proches de la Miff une fois l'alerte active ; avant, l'écran offre les numéros d'urgence (et, pour la
                // question des 15 minutes, la décision). Aucun bouton ne renvoie quoi que ce soit en disant « Appeler ».
                action(secondActionKey, actions.onCall)
                if showSeeMiff, let miffName {
                    action(L10n.format("alert_banner_see_miff", miffName), actions.onSeeMiff, verbatim: true)
                }
            }
        }
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.sm + Spacing.xs)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Le texte, lu d'un bloc ; l'action « Terminer l'alerte » du lecteur d'écran y est posée.
    private var textBlock: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            titleText
            if let note {
                Text(verbatim: note)
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorOnAlert.color)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
        .modifier(EndAccessibleAction(enabled: endable, perform: actions.onEndAccessible))
    }

    /// Le titre : DEUX FOIS plus grand qu'avant pendant l'alerte en cours (retour du PM : la question d'abord, la consigne ensuite). Il passe à la ligne
    /// (jamais tronqué : `fixedSize` vertical) et suit Dynamic Type jusqu'à `titleMaxDynamicType`.
    @ViewBuilder
    private var titleText: some View {
        if hasBigTitle {
            Text(verbatim: title)
                .miffTextStyle(textAlertBannerTitle)
                .dynamicTypeSize(...Self.titleMaxDynamicType)
                .foregroundColor(colorOnAlert.color)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
        } else {
            Text(verbatim: title)
                .miffTextStyle(textBodyBold16)
                .foregroundColor(colorOnAlert.color)
                .accessibilityAddTraits(.isHeader)
        }
    }

    private var closeButton: some View {
        Button(action: actions.onClose) {
            Image(systemName: "xmark")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(colorOnAlert.color)
                .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("alert_banner_close_a11y"))
    }

    /// Le voile de la fin : un remplissage droit depuis le début, rien quand on ne tient pas. Il suit l'horloge, pas une animation : « Réduire les
    /// animations » n'y change rien.
    private var veil: some View {
        GeometryReader { proxy in
            colorAlertBannerProgress.color
                .frame(width: proxy.size.width * CGFloat(min(max(hold.progress, 0), 1)))
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func action(_ key: String, _ run: @escaping () -> Void, verbatim: Bool = false) -> some View {
        Button(action: run) {
            Text(verbatim: verbatim ? key : L10n.string(key))
                .underline()
                .miffTextStyle(textBodyBold14)
                .foregroundColor(colorOnAlert.color)
                .frame(minHeight: Spacing.minTouchTarget, alignment: .leading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Le maintien sur le corps de la bannière (seulement quand la fin est possible).
private struct BannerHold: ViewModifier {
    @ObservedObject var model: DangerHoldModel
    let enabled: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        if enabled {
            content.dangerHoldGesture(model)
        } else {
            content
        }
    }
}

/// L'action d'accessibilité « Terminer l'alerte », seulement quand la fin est possible.
private struct EndAccessibleAction: ViewModifier {
    let enabled: Bool
    let perform: () -> Void

    @ViewBuilder
    func body(content: Content) -> some View {
        if enabled {
            content.accessibilityAction(named: Text("alert_banner_end_action")) {
                perform()
            }
        } else {
            content
        }
    }
}
#endif
