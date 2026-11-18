import SwiftUI

// Le champ de DESCRIPTION facultative, sous l'aperçu de la photo (ticket docs/tickets/description-update.md, DESC-I ; miroir de `DescriptionField.kt`
// d'Android). CONFIDENTIALITÉ, pour ce fichier : le texte vit dans la mémoire du modèle de l'écran, rien d'autre : ni `@SceneStorage` (pas dans l'état
// sauvegardé par le système), ni journal, ni événement, ni presse-papiers écrit d'ici. Il est saisi sur le fond sombre de l'aperçu : les couleurs sont
// celles des autres surcouches de l'Update (blanc sur un voile clair).
//
// Ce qu'il fait : un champ qui grandit de 1 à 4 lignes puis défile dedans ; la touche d'action du clavier est « Terminé » (elle ferme le clavier et ne
// publie RIEN : « Publier » est le seul geste qui publie) ; les caractères de contrôle, invisibles et de direction sont retirés à la frappe et au
// collage, un saut de ligne devient une espace (aucun retour à la ligne n'est jamais saisi) ; 140 unités UTF-16 au plus sans jamais couper un emoji ;
// un petit compteur à partir de 120 seulement, calme, jamais rouge.

struct DescriptionField: View {
    let text: String
    let limitHit: Bool
    let enabled: Bool
    let focus: FocusState<Bool>.Binding
    let onChange: (String) -> Void

    private static let maxLines = 4

    private var length: Int {
        text.utf16.count
    }

    private var counterShown: Bool {
        PhotoDescription.showCounter(length: length)
    }

    private var atLimit: Bool {
        limitHit || PhotoDescription.isAtLimit(length: length)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            field
            if counterShown {
                counterRow
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        // Annoncé UNE fois à son apparition (120), puis une fois à la limite : jamais à chaque frappe.
        .onChange(of: counterShown) { shown in
            if shown {
                UpdateAnnouncer.post(counterSpoken)
            }
        }
        .onChange(of: atLimit) { reached in
            if reached {
                UpdateAnnouncer.post(limitSentence)
            }
        }
    }

    // MARK: - Le champ

    private var field: some View {
        ZStack(alignment: .topLeading) {
            // Le texte indicatif est dessiné ici (et pas par le système) : sa couleur est celle du jeton, lisible sur le fond sombre.
            if text.isEmpty {
                Text("update_description_placeholder")
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorUpdateFieldPlaceholder.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
            TextField(String(), text: binding, axis: .vertical)
                .lineLimit(1...Self.maxLines)
                .miffTextStyle(textBodyRegular)
                .foregroundColor(enabled ? colorUpdateOnPhoto.color : colorUpdateFieldDisabledText.color)
                .tint(colorUpdateOnPhoto.color)
                .focused(focus)
                .disabled(!enabled)
                .submitLabel(.done)
                .textInputAutocapitalization(.sentences)
                .onSubmit { focus.wrappedValue = false }
                .accessibilityLabel(Text("update_description_a11y_label"))
                .accessibilityHint(Text("update_description_a11y_hint"))
        }
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.sm + Spacing.xs)
        .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget, alignment: .topLeading)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorUpdateFieldVeil.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(
                    focus.wrappedValue ? colorUpdateOnPhoto.color : colorUpdateFieldBorder.color,
                    lineWidth: focus.wrappedValue ? 2 : 1
                )
        )
        .contentShape(Rectangle())
        // Toucher le champ hors du texte (le bord, le rembourrage) met le curseur dedans.
        .onTapGesture { focus.wrappedValue = true }
    }

    /// Ce que le champ montre et ce qu'il rend : la saisie passe par le modèle, qui la nettoie et la coupe (il rend le texte gardé). La touche
    /// « Retour » d'un champ à plusieurs lignes insère un saut de ligne au lieu de valider : il est reconnu, le clavier se ferme (« Terminé »), et
    /// le saut de ligne devient une espace comme tout saut de ligne (retirée à la publication, jamais une ligne vide).
    private var binding: Binding<String> {
        Binding(
            get: { text },
            set: { raw in
                if PhotoDescription.isReturnKey(old: text, new: raw) {
                    focus.wrappedValue = false
                }
                onChange(raw)
            }
        )
    }

    // MARK: - Le compteur

    /// « 125/140 », en blanc atténué (jamais rouge) ; à 140, la phrase calme « Limite de 140 caractères atteinte. » à gauche.
    private var counterRow: some View {
        HStack(alignment: .firstTextBaseline, spacing: Spacing.sm) {
            if atLimit {
                Text(verbatim: limitSentence)
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorUpdateOnPhoto.color)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            Text(verbatim: L10n.format("update_description_counter", length, PhotoDescription.maxLength))
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorUpdateOnPhoto.color.opacity(0.85))
                .accessibilityLabel(Text(verbatim: counterSpoken))
        }
        .padding(.horizontal, Spacing.xs)
    }

    /// « 125 caractères sur 140 » (jamais « 125 sur 140 » sans unité).
    private var counterSpoken: String {
        L10n.format("update_description_counter_a11y", length, PhotoDescription.maxLength)
    }

    private var limitSentence: String {
        L10n.format("update_description_limit", PhotoDescription.maxLength)
    }
}
