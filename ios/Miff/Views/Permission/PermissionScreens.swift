import SwiftUI

// Les écrans de consentement avant le Feed (LOC-4a). Du pur dessin : l'état entre, les gestes sortent ; le view model décide et
// l'hôte (`PermissionGateHost`) appelle le système. RIEN ici ne lit une position, n'envoie un événement ni n'écrit un mot dans un
// journal (PermissionPrivacyTests lit ces sources).
//
// Maquettes : pop-ins du PM (design/exports/pop-ins 01), écrans 08, 09 et 10 de design/exports/lieux/claude. La pop-in est
// dessinée sur le fond de l'app et non sur le Feed flouté : rien du Feed n'est chargé derrière un écran bloquant (écart listé dans
// la PR).

/// Une pop-in du PM : carte blanche à contour bleu, centrée sur le fond ; le contenu défile quand il ne tient pas, le pied (les
/// boutons) reste visible sous lui.
struct PermissionPopIn<Content: View, Footer: View>: View {
    let content: Content
    let footer: Footer

    init(@ViewBuilder content: () -> Content, @ViewBuilder footer: () -> Footer) {
        self.content = content()
        self.footer = footer()
    }

    var body: some View {
        ZStack {
            gradientAuthBackground.ignoresSafeArea()
            // Le contenu est posé tel quel quand il tient ; sinon la même carte avec un contenu qui défile.
            ViewThatFits(in: .vertical) {
                card(scrolls: false)
                card(scrolls: true)
            }
            .frame(maxWidth: 420)
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.md)
        }
    }

    private var inner: some View {
        VStack(spacing: Spacing.md) {
            content
        }
        .padding(.horizontal, Spacing.md)
        .padding(.top, Spacing.lg)
        .padding(.bottom, Spacing.sm)
        .frame(maxWidth: .infinity)
    }

    private func card(scrolls: Bool) -> some View {
        VStack(spacing: 0) {
            if scrolls {
                ScrollView {
                    inner
                }
            } else {
                inner
            }
            VStack(spacing: Spacing.xs) {
                footer
            }
            .padding(.horizontal, Spacing.md)
            .padding(.bottom, Spacing.md)
        }
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderAction.color, lineWidth: strokeAccent)
        )
        .miffShadow(shadowDefault)
    }
}

private struct PopInTitle: View {
    let text: LocalizedStringKey

    var body: some View {
        Text(text)
            .miffTextStyle(textTitleBold20)
            .foregroundColor(colorActionPrimary.color)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityAddTraits(.isHeader)
    }
}

/// Le pictogramme de localisation des pop-ins (une épingle et son socle, couleur de la marque). Dessin PROVISOIRE : le PM
/// exportera l'original de Figma. Reprend le tracé de l'icône d'Android (viewBox 72 x 72).
struct PermissionPinShape: Shape {
    func path(in rect: CGRect) -> Path {
        let scale = min(rect.width, rect.height) / 72
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: rect.minX + x * scale, y: rect.minY + y * scale)
        }
        var path = Path()
        // L'épingle : une pointe en bas, une tête ronde de rayon 17 dessinée en deux quarts de cercle.
        path.move(to: point(36, 52))
        path.addCurve(to: point(19, 25), control1: point(36, 52), control2: point(19, 36))
        path.addCurve(to: point(36, 8), control1: point(19, 15.61), control2: point(26.61, 8))
        path.addCurve(to: point(53, 25), control1: point(45.39, 8), control2: point(53, 15.61))
        path.addCurve(to: point(36, 52), control1: point(53, 36), control2: point(36, 52))
        path.closeSubpath()
        // Le trou de l'épingle.
        path.addEllipse(
            in: CGRect(x: rect.minX + 30.5 * scale, y: rect.minY + 19.5 * scale, width: 11 * scale, height: 11 * scale)
        )
        // Le socle (une ellipse ouverte sous la pointe).
        path.move(to: point(24, 50))
        path.addCurve(to: point(10, 60), control1: point(14, 52), control2: point(10, 56))
        path.addCurve(to: point(36, 70), control1: point(10, 66), control2: point(22.5, 70))
        path.addCurve(to: point(62, 60), control1: point(49.5, 70), control2: point(62, 66))
        path.addCurve(to: point(48, 50), control1: point(62, 56), control2: point(58, 52))
        return path
    }
}

struct PermissionPinArt: View {
    var body: some View {
        PermissionPinShape()
            .stroke(colorBrandPrimary.color, style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round))
            .frame(width: 72, height: 72)
            .accessibilityHidden(true)
    }
}

// MARK: - Notifications (pop-in 01, décision (b) du PM)

/// « Te prévenir, seulement quand c'est utile ! » : la pop-in du PM avec ses deux exemples, une ligne honnête dessous (rien n'est
/// envoyé aujourd'hui : plan Spark) et « Autoriser ». JAMAIS bloquante : quelle que soit la réponse du système, la personne continue.
struct NotificationsPopIn: View {
    let onAllow: () -> Void

    var body: some View {
        PermissionPopIn {
            PopInTitle(text: "permission_notifications_title")
            NotificationExample(
                photo: "OnboardingPhotoThomas",
                messageId: "onboarding_sample_msg_1",
                timeId: "onboarding_sample_when_1",
                unread: true
            )
            NotificationExample(
                photo: "OnboardingPhotoAmandine",
                messageId: "onboarding_sample_msg_2",
                timeId: "onboarding_sample_when_2",
                unread: false
            )
            Text("ios_permission_notifications_note")
                .miffTextStyle(textBodyCaption)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        } footer: {
            Button(action: onAllow) {
                Text("permission_notifications_action")
            }
            .buttonStyle(.miffPrimaryFullWidth)
        }
    }
}

/// Un exemple de notification : la photo à anneau bleu, la phrase, l'heure ; un point jaune-vert au coin du premier. Exemple
/// décoratif : le lecteur d'écran lit la phrase et l'heure, pas l'image.
private struct NotificationExample: View {
    let photo: String
    let messageId: String
    let timeId: String
    let unread: Bool

    var body: some View {
        ZStack(alignment: .topTrailing) {
            HStack(spacing: Spacing.sm) {
                Image(photo)
                    .resizable()
                    .scaledToFill()
                    .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                    .clipShape(Circle())
                    .overlay(Circle().stroke(colorActionPrimary.color, lineWidth: strokeAccent))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 0) {
                    Text(verbatim: L10n.string(messageId))
                        .miffTextStyle(textBodyBold14)
                        .foregroundColor(colorTextPrimary.color)
                    Text(verbatim: L10n.string(timeId))
                        .miffTextStyle(textBodyCaption)
                        .foregroundColor(colorTextSecondary.color)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            }
            .padding(Spacing.sm)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
            .overlay(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .stroke(colorActionPrimaryTint20.color, lineWidth: strokeHairline)
            )
            .miffShadow(shadowSubtle)
            if unread {
                Circle()
                    .fill(colorBrandPrimary.color)
                    .frame(width: 12, height: 12)
                    .padding(.trailing, Spacing.sm)
                    .offset(y: -6)
                    .accessibilityHidden(true)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Localisation (écrans 08, 09, 10)

/// Les textes d'une variante de l'écran de la localisation. Les clés `permission_location_*` sont partagées avec Android ; celles
/// des étapes des Réglages sont propres à iOS (`ios_permission_location_*`) : les mots du système ne sont pas les mêmes.
private struct LocationCopy {
    let title: LocalizedStringKey
    let body: LocalizedStringKey
    let steps: [LocalizedStringKey]
    let button: LocalizedStringKey
    let note: LocalizedStringKey?

    /// 08 : jamais demandée.
    private static let first = LocationCopy(
        title: "permission_location_title",
        body: "permission_location_body",
        steps: [],
        button: "permission_location_action",
        note: nil
    )

    /// 09 : « pendant l'utilisation » accordé, « Toujours » se choisit dans les Réglages.
    private static let upgrade = LocationCopy(
        title: "ios_permission_location_upgrade_title",
        body: "ios_permission_location_upgrade_body",
        steps: [
            "ios_permission_location_step_open_miff",
            "ios_permission_location_step_position",
            "ios_permission_location_step_always"
        ],
        button: "ios_permission_location_open_settings",
        note: "ios_permission_location_settings_note"
    )

    /// 10A : refusée ou retirée.
    private static let denied = LocationCopy(
        title: "ios_permission_location_denied_title",
        body: "ios_permission_location_denied_body",
        steps: [
            "ios_permission_location_step_open_miff_short",
            "ios_permission_location_step_position",
            "ios_permission_location_step_always"
        ],
        button: "ios_permission_location_open_settings",
        note: "ios_permission_location_denied_note"
    )

    /// 10B : le service de localisation est coupé sur tout l'iPhone.
    private static let servicesOff = LocationCopy(
        title: "ios_permission_location_denied_title",
        body: "ios_permission_location_services_body",
        steps: [
            "ios_permission_location_services_step_1",
            "ios_permission_location_services_step_2",
            "ios_permission_location_services_step_3"
        ],
        button: "ios_permission_location_open_settings",
        note: "ios_permission_location_services_note"
    )

    /// Non dessiné : localisation restreinte (aucune action possible : le bouton n'est pas montré).
    private static let restricted = LocationCopy(
        title: "ios_permission_location_denied_title",
        body: "ios_permission_location_restricted_body",
        steps: [],
        button: "ios_permission_location_open_settings",
        note: nil
    )

    /// Non dessiné : « Toujours » accordé mais la position exacte éteinte.
    private static let approximate = LocationCopy(
        title: "ios_permission_location_approximate_title",
        body: "ios_permission_location_approximate_body",
        steps: [
            "ios_permission_location_step_open_miff_short",
            "ios_permission_location_step_position",
            "ios_permission_location_step_precise"
        ],
        button: "ios_permission_location_open_settings",
        note: "ios_permission_location_settings_note"
    )

    static func of(_ variant: LocationVariant) -> LocationCopy {
        switch variant {
        case .first:
            return first
        case .upgrade:
            return upgrade
        case .denied:
            return denied
        case .servicesOff:
            return servicesOff
        case .restricted:
            return restricted
        case .approximate:
            return approximate
        }
    }
}

/// L'écran de la localisation (BLOQUANT par défaut) : premier affichage (08), « plus qu'une étape » vers les Réglages (09), refusée
/// ou retirée, service coupé ou restreinte (10). Aucun rouge, aucune faute attribuée. `onPutOff` (« Plus tard ») n'existe que
/// quand l'interrupteur de compilation relâche le blocage ; `onSignOut` est la sortie discrète (GatePolicy).
struct LocationPopIn: View {
    let model: LocationScreenModel
    let onAction: () -> Void
    let onPutOff: (() -> Void)?
    let onSignOut: (() -> Void)?

    var body: some View {
        let copy = LocationCopy.of(model.variant)
        PermissionPopIn {
            PopInTitle(text: copy.title)
            PermissionPinArt()
            Text(copy.body)
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextPrimary.color)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            if !copy.steps.isEmpty {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    ForEach(Array(copy.steps.enumerated()), id: \.offset) { index, step in
                        PermissionStepRow(number: index + 1, text: step)
                    }
                }
            }
            if model.variant == .first {
                LocationFactsCard()
            }
        } footer: {
            if model.action != .none {
                Button(action: onAction) {
                    Text(copy.button)
                }
                .buttonStyle(.miffPrimaryFullWidth)
            }
            if let note = copy.note {
                Text(note)
                    .miffTextStyle(textBodyCaption)
                    .foregroundColor(colorTextDescription.color)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.vertical, Spacing.xs)
            }
            if let onPutOff {
                Button(action: onPutOff) {
                    Text("permission_location_later")
                        .miffTextStyle(textBodyBold16)
                        .foregroundColor(colorActionPrimary.color)
                        .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
                }
            }
            if let onSignOut {
                Button(action: onSignOut) {
                    Text("auth_signout_action")
                        .miffTextStyle(textBodyRegular14)
                        .foregroundColor(colorActionPrimary.color)
                        .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
                }
            }
        }
    }
}

/// L'encart de trois lignes de la maquette 08 : quand, qui voit quoi, et la main que la personne garde. Ces textes disent la même
/// chose que la phrase d'usage du système (`NSLocationAlwaysAndWhenInUseUsageDescription`) et le texte de consentement.
private struct LocationFactsCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            ConsentLine(
                systemImage: "iphone",
                titleId: "permission_location_closed_title",
                bodyId: "permission_location_closed_body"
            )
            ConsentLine(
                systemImage: "eye",
                titleId: "permission_location_seen_title",
                bodyId: "permission_location_seen_body"
            )
            ConsentLine(
                systemImage: "arrow.left.arrow.right",
                titleId: "permission_location_control_title",
                bodyId: "permission_location_control_body"
            )
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.md)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgCanvas.color))
    }
}

/// Une étape numérotée des Réglages : un rond bleu pâle avec le numéro, puis le texte (les mots du système en gras).
private struct PermissionStepRow: View {
    let number: Int
    let text: LocalizedStringKey

    var body: some View {
        HStack(spacing: Spacing.sm) {
            Text(verbatim: String(number))
                .miffTextStyle(textBodyBold14)
                .foregroundColor(colorActionPrimary.color)
                .frame(width: Spacing.xl, height: Spacing.xl)
                .background(Circle().fill(colorActionPrimaryTint20.color))
            Text(text)
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextPrimary.color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
    }
}
