import SwiftUI
import UIKit

// Les briques du flux Miff que le design system n'a pas encore (ticket Miff §3.3 : en-tête, titre de section, bandeau calme,
// code, ligne de proche, étiquette de rôle, ligne à interrupteur, fenêtre de confirmation). Construites avec les tokens du design
// system : aucune couleur, aucun style de texte qui ne vienne pas d'eux.

/// Le texte d'un `ActionMessage`, tel que les écrans le disent.
func actionMessageText(_ message: ActionMessage) -> String {
    switch message {
    case .offline:
        return L10n.string("miff_message_offline")
    case .generic:
        return L10n.string("miff_message_generic")
    case .oneMiff:
        return L10n.string("miff_message_one_miff")
    case .noProfile:
        return L10n.string("miff_message_no_profile")
    }
}

/// Cadre des écrans du flux : fond dégradé de l'authentification (ou fond de l'app), zones sûres respectées (les fonds
/// passent sous les barres, jamais le contenu), défile quand le contenu dépasse l'écran (grande taille de texte, paysage,
/// clavier). `title` est le titre de l'en-tête ; nil = pas d'en-tête (l'écran en dessine un à sa façon).
struct MiffFrame<Content: View>: View {
    let title: String?
    let onBack: (() -> Void)?
    let usesCanvas: Bool
    let content: Content

    init(title: String?, onBack: (() -> Void)?, usesCanvas: Bool = false, @ViewBuilder content: () -> Content) {
        self.title = title
        self.onBack = onBack
        self.usesCanvas = usesCanvas
        self.content = content()
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                if let title {
                    MiffHeader(title: title, onBack: onBack)
                }
                content
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.sm)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(background.ignoresSafeArea())
    }

    @ViewBuilder
    private var background: some View {
        if usesCanvas {
            colorBgCanvas.color
        } else {
            gradientAuthBackground
        }
    }
}

/// L'en-tête des écrans du flux : la flèche de retour au début de la ligne, le titre au milieu (maquettes « Nouvelle Miff »).
struct MiffHeader: View {
    let title: String
    let onBack: (() -> Void)?

    var body: some View {
        ZStack {
            Text(verbatim: title)
                .miffTextStyle(textDisplayTitle)
                .foregroundColor(colorTextForm.color)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, Spacing.minTouchTarget + Spacing.xs)
                .accessibilityAddTraits(.isHeader)
            if let onBack {
                HStack {
                    Button(action: onBack) {
                        Image(systemName: "arrow.backward")
                            .font(.system(size: 20, weight: .regular))
                            .foregroundColor(colorTextForm.color)
                            .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                    }
                    .accessibilityLabel("auth_back_a11y")
                    Spacer()
                }
            }
        }
        .frame(minHeight: Spacing.minTouchTarget + Spacing.sm)
    }
}

/// « Nom », « Ajouter des proches »... : un carré bleu pâle avec une icône, un titre et une courte ligne (maquettes).
struct MiffSectionHeader: View {
    let systemImage: String
    let title: String
    let description: String?

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            Image(systemName: systemImage)
                .font(.system(size: Spacing.sectionIcon, weight: .regular))
                .foregroundColor(colorActionPrimary.color)
                .frame(width: Spacing.sectionIconBox, height: Spacing.sectionIconBox)
                .background(
                    RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorActionPrimaryTint20.color)
                )
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 0) {
                Text(verbatim: title)
                    .miffTextStyle(textDisplayTitle)
                    .foregroundColor(colorTextPrimary.color)
                if let description {
                    Text(verbatim: description)
                        .miffTextStyle(textBodyCaption)
                        .foregroundColor(colorTextDescription.color)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

/// Une phrase calme dans un cadre (jamais une alarme rouge) : une information, ou la mention « pas de connexion ». Annoncée
/// par le lecteur d'écran à son apparition.
struct MiffNotice: View {
    let text: String
    var isError = false

    var body: some View {
        Text(verbatim: text)
            .miffTextStyle(textBodyRegular14)
            .foregroundColor((isError ? colorTextError : colorTextPrimary).color)
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
            .padding(Spacing.md)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
            .overlay(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .stroke((isError ? colorTextError : colorBorderStrong).color, lineWidth: strokeHairline)
            )
            .onAppear {
                Announcer.announce(text)
            }
    }
}

/// Le code, grand, par groupes de 4 ; lu par le lecteur d'écran groupe par groupe, lettre par lettre (critère T2).
struct MiffCodeDisplay: View {
    let code: String
    var isLarge = false

    var body: some View {
        Text(verbatim: JoinCode.grouped(code))
            .tracking(2)
            .miffTextStyle(isLarge ? textTitleBold24 : textTitleBold20)
            .foregroundColor(colorActionPrimary.color)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .padding(.horizontal, Spacing.sm)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
            .overlay(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .stroke(colorBorderStrong.color, lineWidth: strokeHairline)
            )
            .accessibilityLabel(Text(verbatim: L10n.format("miff_code_a11y", JoinCode.spoken(code))))
    }
}

/// Une petite étiquette près d'un nom : « Créateur », « Toi ». Jamais verte (cette couleur est celle du statut « tout va
/// bien »).
struct MiffRolePill: View {
    let text: String
    let isOwner: Bool

    var body: some View {
        Text(verbatim: text)
            .miffTextStyle(textCaptionBold12)
            .foregroundColor((isOwner ? colorActionPrimary : colorTextPrimary).color)
            .padding(.horizontal, Spacing.sm)
            .frame(minHeight: Spacing.pillHeight)
            .background(Capsule().fill((isOwner ? colorActionPrimaryTint10 : colorBorderDefault).color))
    }
}

/// La photo ronde d'un proche, ou ses initiales. Aucun anneau de couleur : cet écran ne montre aucun statut (critère G7). La
/// photo vient d'un autre téléphone : elle n'est décodée que si elle respecte la taille que les règles imposent.
struct MiffAvatar: View {
    let jpeg: Data?
    let initials: String
    var size: CGFloat = Spacing.memberAvatar

    private var image: UIImage? {
        guard let jpeg, !jpeg.isEmpty, jpeg.count <= ProfileDocument.maxAvatarBytes else {
            return nil
        }
        return UIImage(data: jpeg)
    }

    var body: some View {
        ZStack {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                Circle().fill(colorActionPrimaryTint20.color)
                Text(verbatim: initials)
                    .miffTextStyle(textDisplayLabel)
                    .foregroundColor(colorActionPrimary.color)
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityHidden(true)
    }
}

/// Un proche : photo ronde (ou initiales), prénom et nom, puis les étiquettes « Créateur » et « Toi ».
struct MiffMemberRow: View {
    let member: MiffMember
    let isMe: Bool

    var body: some View {
        HStack(spacing: Spacing.md) {
            MiffAvatar(jpeg: member.avatarJpeg, initials: Initials.of(first: member.firstName, last: member.lastName))
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(verbatim: member.displayName)
                    .miffTextStyle(textDisplayLabel)
                    .foregroundColor(colorTextPrimary.color)
                if member.role == .owner || isMe {
                    HStack(spacing: Spacing.xs) {
                        if member.role == .owner {
                            MiffRolePill(text: L10n.string("miff_role_owner"), isOwner: true)
                        }
                        if isMe {
                            MiffRolePill(text: L10n.string("miff_role_you"), isOwner: false)
                        }
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .padding(Spacing.sm + Spacing.xs)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
        )
        .accessibilityElement(children: .combine)
    }
}

/// « Partager mon statut » : toute la ligne est l'interrupteur (44 pt de haut au moins), l'état est dit en mots au lecteur
/// d'écran. L'interrupteur ne bouge pas tout seul : la personne est d'abord interrogée, et il ne change qu'une fois que le
/// serveur a répondu (le dessin suit l'état de la fiche, jamais le toucher).
struct MiffSwitchRow: View {
    let title: String
    let description: String
    let isOn: Bool
    let isEnabled: Bool
    let stateText: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.md) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(verbatim: title)
                        .miffTextStyle(textBodyBold16)
                        .foregroundColor(colorTextPrimary.color)
                    Text(verbatim: description)
                        .miffTextStyle(textBodyRegular14)
                        .foregroundColor(colorTextDescription.color)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                // Le dessin de l'interrupteur : la ligne porte le toucher et le texte pour le lecteur d'écran.
                Toggle(isOn: .constant(isOn)) {
                    Text(verbatim: "")
                }
                .labelsHidden()
                .tint(colorActionPrimary.color)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm + Spacing.xs)
            .frame(minHeight: Spacing.minTouchTarget)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
            .overlay(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
            )
            .miffShadow(shadowDefault)
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .opacity(isEnabled ? 1 : 0.5)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: title))
        .accessibilityValue(Text(verbatim: stateText))
        .accessibilityHint(Text(verbatim: description))
        .accessibilityAddTraits(.isButton)
    }
}

/// Le consentement explicite : la phrase du PM, ce que les membres voient de la personne, la phrase sur les lieux, et le
/// rappel sur la permission du téléphone. Visible SANS action et au-dessus du bouton (critère K1) ; lu d'un seul tenant par le
/// lecteur d'écran, avant le bouton.
struct SharingConsentCard: View {
    let sentence: String
    let systemNote: String

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(verbatim: sentence)
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextPrimary.color)
            Text("miff_consent_members_see_title")
                .miffTextStyle(textBodyBold14)
                .foregroundColor(colorTextPrimary.color)
            Text("miff_consent_members_see_body")
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextPrimary.color)
            Text("miff_consent_places")
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextPrimary.color)
            Text(verbatim: systemNote)
                .miffTextStyle(textBodyCaption)
                .foregroundColor(colorTextDescription.color)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .fixedSize(horizontal: false, vertical: true)
        .padding(Spacing.md)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderAction.color, lineWidth: strokeAccent)
        )
        .accessibilityElement(children: .combine)
    }
}

/// Ce que dit une fenêtre de confirmation.
struct MiffConfirmContent {
    let title: String
    let message: String?
    let confirmLabel: String
    let isBusy: Bool
    let failure: ActionMessage?
}

struct MiffConfirmActions {
    let onConfirm: () -> Void
    let onCancel: () -> Void
}

/// LA fenêtre de confirmation du flux (un seul modèle pour toutes les questions, ticket §3.3) : un titre, une ou deux phrases
/// calmes, le bouton principal, et « Annuler », qui est toujours la sortie sans risque. Pendant l'opération, le bouton
/// principal est grisé, un indicateur tourne et rien d'autre n'est possible (un appui = une opération). Présentée en feuille :
/// le lecteur d'écran y reste enfermé et la taille de texte maximale la fait défiler.
struct MiffConfirmSheet<Extra: View>: View {
    let content: MiffConfirmContent
    let actions: MiffConfirmActions
    let extra: Extra

    init(content: MiffConfirmContent, actions: MiffConfirmActions, @ViewBuilder extra: () -> Extra) {
        self.content = content
        self.actions = actions
        self.extra = extra()
    }

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.md) {
                Text(verbatim: content.title)
                    .miffTextStyle(textTitleBold20)
                    .foregroundColor(colorTextPrimary.color)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                if let message = content.message {
                    Text(verbatim: message)
                        .miffTextStyle(textBodyRegular)
                        .foregroundColor(colorTextDescription.color)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                extra
                if let failure = content.failure {
                    MiffNotice(text: actionMessageText(failure))
                }
                Button(action: actions.onConfirm) {
                    Text(verbatim: content.confirmLabel)
                }
                .buttonStyle(.miffPrimaryFullWidth)
                .disabled(content.isBusy)
                if content.isBusy {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(colorActionPrimary.color)
                        .accessibilityLabel("miff_dialog_working_a11y")
                }
                Button(action: actions.onCancel) {
                    Text("common_cancel")
                        .miffTextStyle(textBodyBold16)
                        .foregroundColor(colorActionPrimary.color)
                        .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
                }
                .disabled(content.isBusy)
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.lg)
        }
        .background(colorBgSurface.color.ignoresSafeArea())
        .interactiveDismissDisabled(content.isBusy)
        .presentationDetents([.medium, .large])
    }
}

extension MiffConfirmSheet where Extra == EmptyView {
    init(content: MiffConfirmContent, actions: MiffConfirmActions) {
        self.init(content: content, actions: actions) {
            EmptyView()
        }
    }
}

/// Le sélecteur d'applications d'iOS garde une vignette de l'écran quand l'app passe en arrière-plan : un écran qui montre
/// le code d'une Miff ne doit jamais y laisser le code. Dès que l'app n'est plus au premier plan (barre de l'App Switcher,
/// retour à l'écran d'accueil, appel entrant), l'écran est recouvert par le fond de l'app et le mot-symbole.
private struct AppSwitcherCover: ViewModifier {
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content.overlay {
            if scenePhase != .active {
                ZStack {
                    colorBgCanvas.color
                    MiffWordmark()
                        .frame(width: Spacing.logoWidth / 2)
                }
                .ignoresSafeArea()
                .accessibilityHidden(true)
            }
        }
    }
}

extension View {
    /// Masque l'écran dans la vignette du sélecteur d'applications (voir `AppSwitcherCover`).
    func hidesInAppSwitcher() -> some View {
        modifier(AppSwitcherCover())
    }
}
