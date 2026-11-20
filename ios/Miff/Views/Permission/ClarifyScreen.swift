import SwiftUI

// La carte calme « Une précision sur ce que tu partages » (CONS-4-I, option D du PM). Du pur dessin : l'état entre, les gestes sortent. RIEN ici
// ne lit une position, n'envoie un événement ni n'écrit dans un journal. Rien n'est écrit tant que la personne n'a pas touché « J'ai compris » ;
// « Plus tard » n'écrit RIEN.

struct ClarifyActions {
    let onAccept: () -> Void
    let onLater: () -> Void
}

/// « Une précision sur ce que tu partages », pour la personne qui partage déjà sous le texte de la version 3. RIEN n'est en pause (ni la
/// position, ni le statut, ni la photo), quelle que soit sa réponse : « J'ai compris » enregistre la version courante (le même
/// renouvellement que l'écran bloquant), « Plus tard » n'écrit rien. Elle revient 3 fois au plus, puis seules les Réglages > Confidentialité
/// la proposent. Aucun compte à rebours, aucun reproche, aucun rouge.
///
/// Un écran plein cadre qui DÉFILE (une grande taille de texte ne coupe rien) avec deux boutons ÉPINGLÉS en bas, de même taille : « Plus
/// tard » est un vrai bouton, aussi visible que « J'ai compris » (jamais un lien discret, jamais puni). Pas de maquette : bâti à partir de
/// l'écran de renouvellement (écart listé dans la PR).
struct ClarifyScreen: View {
    let state: ClarifyUiState
    let actions: ClarifyActions

    private var acceptKey: LocalizedStringKey {
        state.busy ? "miff_clarify_saving" : "miff_clarify_accept"
    }

    var body: some View {
        MiffPinnedFrame(title: nil, onBack: nil) {
            VStack(spacing: Spacing.md) {
                MiffWordmark()
                    .frame(width: Spacing.logoWidth / 2)
                    .padding(.top, Spacing.sm)
                Text("miff_clarify_title")
                    .miffTextStyle(textDisplayHeadline)
                    .foregroundColor(colorTextForm.color)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                if state.miffs.count > 1 {
                    names
                }
                Text(verbatim: intro)
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextPrimary.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                // La rassurance n'est VRAIE que parce que rien n'est en pause et qu'aucun seuil des règles ne bouge pour la version 3.
                Text("miff_clarify_continue")
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorTextPrimary.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity)
        } footer: {
            if let message = state.message {
                MiffNotice(text: actionMessageText(message), isError: true)
                Text("miff_clarify_error_extra")
                    .miffTextStyle(textBodyCaption)
                    .foregroundColor(colorTextDescription.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Button(action: actions.onAccept) {
                Text(acceptKey)
            }
            .buttonStyle(.miffPrimaryFullWidth)
            .disabled(state.busy)
            if state.busy {
                LoadingIndicator()
            }
            Button(action: actions.onLater) {
                Text("miff_clarify_later")
            }
            .buttonStyle(.miffSecondaryFullWidth)
            .disabled(state.busy)
        }
    }

    /// Plusieurs Miffs : leurs noms en pastilles, en lecture seule.
    private var names: some View {
        VStack(spacing: Spacing.xs) {
            ForEach(state.miffs.filter { !$0.name.isEmpty }, id: \.miffId) { miff in
                Text(verbatim: miff.name)
                    .miffTextStyle(textBodyBold14)
                    .foregroundColor(colorActionPrimary.color)
                    .padding(.horizontal, Spacing.md)
                    .padding(.vertical, Spacing.xs)
                    .background(Capsule().fill(colorActionPrimaryTint10.color))
                    .overlay(Capsule().stroke(colorBorderAction.color, lineWidth: strokeHairline))
            }
        }
    }

    /// Le texte nomme la Miff quand il n'y en a qu'une (et que son nom est connu), sinon il parle de « cette Miff » ou de « tes Miffs ».
    private var intro: String {
        guard state.miffs.count == 1, let only = state.miffs.first else {
            return L10n.string("miff_clarify_body_many")
        }
        if only.name.isEmpty {
            return L10n.string("miff_clarify_body_unnamed")
        }
        return L10n.format("miff_clarify_body_one", only.name)
    }
}
