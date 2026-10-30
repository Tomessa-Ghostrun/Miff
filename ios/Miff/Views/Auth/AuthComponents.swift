import SwiftUI
import UIKit

/// Où sont les pages légales publiques et l'adresse d'aide. UN seul endroit pour le domaine (pages d'AUTH-W, pas encore
/// déployées) ; le contact est provisoire (décision du PM du 2026-10-24).
enum AppLinks {
    static let legalBaseURL = "https://miff-3d283.web.app"
    static let supportEmail = "tomessa.mfouka@gmail.com"

    static func termsURL() -> URL? {
        URL(string: legalPageUrl(baseUrl: legalBaseURL, path: L10n.string("ios_legal_terms_path")))
    }

    static func privacyURL() -> URL? {
        URL(string: legalPageUrl(baseUrl: legalBaseURL, path: L10n.string("ios_legal_privacy_path")))
    }
}

/// Annonce un texte au lecteur d'écran (VoiceOver) : une erreur ou une confirmation qui vient d'apparaître.
@MainActor
enum Announcer {
    static func announce(_ text: String) {
        UIAccessibility.post(notification: .announcement, argument: text)
    }
}

/// Le texte de chaque message d'authentification (ticket §7.3). Une clé par message, aucun code technique.
extension AuthMessage {
    var text: String {
        switch self {
        case .invalidCredentials:
            return L10n.string("auth_error_invalid_credentials")
        case .tooManyAttempts:
            return L10n.string("auth_error_too_many")
        case .tooManyRequests:
            return L10n.string("auth_error_too_many_requests")
        case .emailInUse:
            return L10n.string("auth_error_email_in_use")
        case .otherMethod:
            return L10n.string("auth_error_other_method")
        case .network:
            return L10n.string("auth_error_network")
        case .unknown:
            return L10n.string("auth_error_unknown")
        case .accountUnavailable:
            return L10n.format("auth_error_account_unavailable", AppLinks.supportEmail)
        }
    }
}

/// Cadre commun des écrans d'authentification (maquettes 04 à 11) : fond dégradé `gradient/auth-background`, zones sûres
/// respectées, défile quand le contenu dépasse l'écran (grande taille de texte, paysage, clavier). L'en-tête est la
/// flèche de retour à gauche et le mot-symbole officiel au centre. Les maquettes n'ont pas de titre visible : `paneTitle`
/// nomme l'écran pour le lecteur d'écran seulement.
struct AuthScaffold<Content: View>: View {
    let paneTitle: LocalizedStringKey
    let onBack: (() -> Void)?
    let content: Content

    init(paneTitle: LocalizedStringKey, onBack: (() -> Void)?, @ViewBuilder content: () -> Content) {
        self.paneTitle = paneTitle
        self.onBack = onBack
        self.content = content()
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                AuthHeader(onBack: onBack)
                Spacer().frame(height: Spacing.lg)
                content
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.sm)
            .accessibilityElement(children: .contain)
            .accessibilityLabel(paneTitle)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(gradientAuthBackground.ignoresSafeArea())
    }
}

/// La flèche de retour au début de la ligne (quand il y a un écran précédent) et le mot-symbole officiel centré.
struct AuthHeader: View {
    let onBack: (() -> Void)?

    var body: some View {
        ZStack {
            MiffWordmark()
                .frame(width: Spacing.logoWidth / 2)
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
        .frame(minHeight: 56)
    }
}

/// Un titre visible (le panneau de consentement, « Mot de passe oublié » : les maquettes d'inscription et de connexion
/// n'en ont pas).
struct ScreenTitle: View {
    let key: LocalizedStringKey
    var style: TextStyleToken = textDisplayHeadline

    var body: some View {
        Text(key)
            .miffTextStyle(style)
            .foregroundColor(colorTextForm.color)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.bottom, Spacing.lg)
            .accessibilityAddTraits(.isHeader)
    }
}

/// Un message lu poliment par le lecteur d'écran à son apparition. Couleur d'erreur ET mots, jamais la couleur seule.
struct MessageText: View {
    let text: String
    let isError: Bool

    var body: some View {
        Text(verbatim: text)
            .miffTextStyle(textBodyRegular14)
            .foregroundColor((isError ? colorTextError : colorTextPrimary).color)
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
            .onAppear {
                Announcer.announce(text)
            }
    }
}

/// « Ou : » entre le bouton social et le formulaire e-mail (maquettes : gras, centré, sans trait).
struct OrText: View {
    var body: some View {
        Text("auth_or_divider")
            .miffTextStyle(textBodyBold16)
            .foregroundColor(colorTextForm.color)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
    }
}

/// Indicateur d'attente annoncé par le lecteur d'écran. Ne remplace rien : l'écran reste visible derrière.
struct LoadingIndicator: View {
    var body: some View {
        ProgressView()
            .progressViewStyle(.circular)
            .tint(colorActionPrimary.color)
            .scaleEffect(1.3)
            .frame(maxWidth: .infinity, minHeight: Spacing.xl + Spacing.sm)
            .accessibilityLabel("auth_loading_a11y")
    }
}

/// Le lien gras souligné sous le bouton principal (« Déjà un compte ? », « Pas encore de compte ? »).
struct UnderlinedLink: View {
    let key: LocalizedStringKey
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(key)
                .miffTextStyle(textBodyBold16)
                .underline()
                .foregroundColor(colorActionPrimary.color)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
        }
    }
}

/// Petit lien gras au début de la ligne, sous un champ (maquette -8, « Mot de passe oublié ? »). Souligné comme les autres
/// liens (la maquette ne le souligne pas : la couleur seule ne doit pas être l'unique signe d'un lien) et de 44 pt de
/// haut pour être facile à toucher.
struct FieldLink: View {
    let key: LocalizedStringKey
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(key)
                .miffTextStyle(textBodyBold14)
                .underline()
                .foregroundColor(colorActionPrimary.color)
                .frame(minHeight: Spacing.minTouchTarget, alignment: .leading)
        }
    }
}

/// « Continuer avec Google », aux règles de marque de Google : fond blanc, bordure #747775 de 1 pt, texte #1F1F1F, aucune
/// ombre ajoutée. Police du système (Roboto n'est pas embarquée) : écart noté dans le README.
struct GoogleButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                GoogleGMark()
                    .frame(width: 18, height: 18)
                Text("auth_google_action")
                    .font(Font.callout.weight(.medium))
                    .foregroundColor(GoogleBrand.text.color)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
            .background(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .fill(GoogleBrand.fill.color)
            )
            .overlay(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .stroke(GoogleBrand.border.color, lineWidth: strokeHairline)
            )
        }
        .buttonStyle(.plain)
    }
}
