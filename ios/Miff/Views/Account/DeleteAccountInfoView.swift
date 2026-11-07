import SwiftUI

/// A. « Supprimer mon compte » (information), puis la fenêtre B (ticket AUTH-5, §3 ; maquettes `design/exports/suppression-compte/claude/`
/// 02 à 04 et 10). Lit les Miffs du compte auprès du SERVEUR et n'affiche jamais une liste incomplète (SC2) : pendant la lecture, hors
/// connexion et après une lecture ratée « Continuer » reste grisé et rien n'est perdu. Avec des Miffs créées, elles sont listées avec le
/// nombre des AUTRES personnes (trois noms, puis « et {k} autres »), et « Prévenir mes proches » (facultatif, SC7) ouvre la feuille de
/// partage du système avec le message de la décision QM9 : l'app n'envoie rien toute seule et la suppression n'avance pas.
///
/// Rien n'est écrit d'ici. « Annuler », la flèche de retour et le geste de retour quittent tous sans rien avoir touché (SC4).
struct DeleteAccountInfoView: View {
    @ObservedObject var model: DeleteAccountInfoViewModel
    let onBack: () -> Void
    /// La personne a confirmé : on passe à la ré-authentification.
    let onConfirmed: () -> Void

    var body: some View {
        MiffFrame(title: L10n.string("settings_delete_title"), onBack: onBack, usesCanvas: true) {
            content
            if model.state.confirmOffline {
                MiffNotice(text: L10n.string("settings_delete_confirm_offline"))
            }
            actions
        }
        .alert(
            Text("settings_delete_confirm_title"),
            isPresented: Binding(
                get: { model.state.confirming },
                set: { if !$0 { model.dismissConfirmation() } }
            ),
            actions: {
                // Deux boutons de même poids : l'action destructive est dite par le texte (et le système la teinte), le premier choix
                // est « Garder mon compte ».
                Button("settings_delete_confirm_keep", role: .cancel) {
                    model.onKeep()
                }
                Button("settings_delete_confirm_ok", role: .destructive) {
                    if model.onConfirm() {
                        onConfirmed()
                    }
                }
            },
            message: {
                Text(verbatim: L10n.string(confirmBodyKey))
            }
        )
    }

    /// La phrase de la fenêtre dit « tes Miffs » aussi quand le compte en a créé (maquette B2).
    private var confirmBodyKey: String {
        model.state.ownsMiffs ? "settings_delete_confirm_body_owner" : "settings_delete_confirm_body"
    }

    @ViewBuilder
    private var content: some View {
        switch model.state.inventory {
        case .loading:
            HStack(spacing: Spacing.md) {
                LoadingIndicator()
                    .frame(width: Spacing.xl)
                MessageText(text: L10n.string("settings_delete_loading"), isError: false)
            }
        case .offline:
            MiffNotice(text: L10n.string("settings_delete_offline_list"))
        case .readFailed:
            MiffNotice(text: L10n.string("settings_delete_read_failed"))
            Button {
                model.load()
            } label: {
                Text("settings_delete_retry")
            }
            .buttonStyle(.miffSecondaryFullWidth)
        case .loaded:
            loaded
        }
    }

    @ViewBuilder
    private var loaded: some View {
        let miffs = model.state.miffs
        Text("settings_delete_intro")
            .miffTextStyle(textBodyRegular)
            .foregroundColor(colorTextForm.color)
            .frame(maxWidth: .infinity, alignment: .leading)
        DeleteInfoCard(rows: infoRows(for: miffs))
        if model.state.ownsMiffs {
            DeleteOwnedMiffsBlock(owned: miffs.owned, plan: miffs.warnPlan)
        }
        DeleteContactSentence(
            text: L10n.format("settings_delete_copy", AppLinks.supportEmail),
            systemImage: "envelope",
            hint: L10n.string("settings_privacy_open_mail_a11y")
        )
        DeleteInfoBox(text: L10n.string("settings_delete_irreversible"), boldLead: true)
    }

    /// Ce qui disparaît pour la personne. « Tu quitteras les Miffs que tu as rejointes » seulement quand il y en a une à quitter ; la
    /// ligne de la dernière position seulement quand la personne a au moins une Miff (fiche de design, à valider).
    private func infoRows(for miffs: AccountMiffsSummary) -> [DeleteInfoRow] {
        let key = miffs.joined > 0 ? "settings_delete_you" : "settings_delete_you_nomiff"
        var rows = [DeleteInfoRow(systemImage: "person", text: L10n.string(key))]
        if miffs.hasMiffs {
            rows.append(DeleteInfoRow(systemImage: "mappin.and.ellipse", text: L10n.string("settings_delete_position")))
        }
        return rows
    }

    private var actions: some View {
        VStack(spacing: Spacing.xs) {
            Button {
                model.onContinue()
            } label: {
                Text("settings_delete_continue")
            }
            .buttonStyle(.miffPrimaryFullWidth)
            .disabled(!model.state.canContinue)
            DeleteTextAction(key: "settings_delete_cancel", action: onBack)
        }
        .padding(.top, Spacing.sm)
    }
}

/// Les Miffs créées, comme l'écran d'information les liste (maquettes A7 et A8) : trois noms au plus, puis « et {k} autres ». Chaque
/// nom est une donnée personnelle : il n'est ni journalisé ni envoyé ailleurs que dans la feuille de partage choisie par la personne.
private struct DeleteOwnedMiffsBlock: View {
    let owned: [OwnedMiff]
    let plan: WarnPlan

    private var titleKey: String {
        owned.count == 1 ? "settings_delete_owned_title_one" : "settings_delete_owned_title"
    }

    /// Les trois premières Miffs, numérotées pour que deux Miffs de même nom n'aient pas la même identité.
    private var listed: [(offset: Int, element: OwnedMiff)] {
        Array(owned.prefix(AccountMiffsSummary.maxListedOwned).enumerated())
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(verbatim: L10n.string(titleKey))
                .miffTextStyle(textTitleBold20)
                .foregroundColor(colorTextPrimary.color)
                .accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: Spacing.sm) {
                ForEach(listed, id: \.offset) { item in
                    row(item.element)
                }
                if owned.count > AccountMiffsSummary.maxListedOwned {
                    Text(
                        verbatim: L10n.format(
                            "settings_delete_owned_more", owned.count - AccountMiffsSummary.maxListedOwned
                        )
                    )
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorTextDescription.color)
                }
                Text(verbatim: L10n.string(DeleteAccountRules.ownedExplainKey(owned: owned)))
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorTextDescription.color)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, Spacing.xs)
                warnButtons
            }
            .padding(Spacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        }
    }

    private var fallbackName: String {
        L10n.string("miff_screen_title_loading")
    }

    private func row(_ miff: OwnedMiff) -> some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            Image(systemName: "person.2")
                .foregroundColor(colorActionPrimary.color)
                .accessibilityHidden(true)
            Text(verbatim: DeleteAccountRules.ownedRowText(miff, fallbackName: fallbackName))
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextForm.color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
    }

    /// « Prévenir mes proches » : seulement s'il y a quelqu'un à prévenir (SC7). UNE Miff : le bouton simple ; plusieurs : un bouton par
    /// Miff listée qui a d'autres personnes, et un de plus, sans nom, pour celles qui ne peuvent pas être nommées. Ce n'est qu'une feuille
    /// de partage : la personne choisit à qui l'envoyer, l'app ne sait pas qui l'a reçu et la suppression n'avance pas.
    @ViewBuilder
    private var warnButtons: some View {
        if plan.single, let name = plan.named.first {
            WarnShareLink(
                label: L10n.string("settings_delete_warn_button"),
                message: MiffSharing.closingNotice(miffName: name)
            )
        } else {
            ForEach(plan.named, id: \.self) { name in
                WarnShareLink(
                    label: L10n.format("settings_delete_warn_button_named", name),
                    message: MiffSharing.closingNotice(miffName: name)
                )
            }
            if plan.generic {
                WarnShareLink(
                    label: L10n.string("settings_delete_warn_button_other"),
                    message: L10n.string("settings_delete_warn_message_generic")
                )
            }
        }
    }
}

/// Un bouton de partage secondaire (jamais plus visible que « Continuer »), avec l'icône de partage. Le message ne contient ni code ni
/// identifiant.
private struct WarnShareLink: View {
    let label: String
    let message: String

    var body: some View {
        ShareLink(item: message) {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "square.and.arrow.up")
                    .accessibilityHidden(true)
                Text(verbatim: label)
                    .multilineTextAlignment(.center)
            }
            .miffTextStyle(textBodyLarge)
            .foregroundColor(colorActionPrimary.color)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
            .overlay(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .stroke(colorActionPrimary.color, lineWidth: strokeAccent)
            )
        }
        .padding(.top, Spacing.xs)
    }
}
