import SwiftUI

// CONFIDENTIALITÉ, pour ce fichier : les lieux (noms, adresses) vivent en mémoire dans l'état donné par l'hôte seulement : jamais dans un
// journal ni un événement.

/// Les gestes de la section des lieux de l'onglet Profil.
struct ProfilePlacesActions {
    let onOpenPlace: (_ miffId: String, _ placeId: String) -> Void
    /// « Ajouter un lieu » (le créateur seulement) : le formulaire s'ouvre avec cette Miff cochée en premier.
    let onAddPlace: (_ miffId: String) -> Void
    let onRetry: () -> Void
    /// La personne quitte l'onglet : le message calme d'un geste (« enregistré ») n'est montré qu'une fois.
    let onLeave: () -> Void
    /// Le Profil revient à l'écran (retour d'un lieu ou d'un formulaire) : la liste regardée est relue si elle est périmée.
    let onAppear: () -> Void
    /// Les demandes d'ajout d'un lieu (LIEU-REQ) : ouvrir le détail d'une demande, et « Demander l'ajout d'un lieu » (un membre).
    var onOpenRequest: (_ miffId: String, _ requestId: String) -> Void = { _, _ in }
    var onRequestPlace: (_ miffId: String) -> Void = { _ in }

    static let none = ProfilePlacesActions(
        onOpenPlace: { _, _ in }, onAddPlace: { _ in }, onRetry: {}, onLeave: {}, onAppear: {}
    )
}

/// « Lieux de confiance de <la Miff> » sur l'onglet Profil (maquette 17, décision du PM du 2026-11-11) : les lieux de la Miff dont le
/// carrousel montre la carte, pour TOUS les membres de cette Miff.
///
/// - Un membre LIT seulement : la liste, le détail d'un lieu (sans « Modifier »), jamais « Ajouter un lieu », jamais le compteur de la
///   formule gratuite, jamais une offre « Miff+ » (cela ne concerne que le créateur, qui définit les lieux). Ce qui est dessiné suit le
///   rôle ; ce qui est PERMIS, ce sont les règles de Firestore (`allow create, update, delete: if isOwner(miffId)`).
/// - Rien n'est dessiné tant que les lieux n'ont pas été lus (`ProfilePlacesPresentation.make` rend nil : ni titre, ni « aucun lieu »).
///   Une liste qui ne peut pas être lue le dit calmement et propose de réessayer.
struct ProfilePlacesSection: View {
    let presentation: ProfilePlacesPresentation
    /// Miff+ ou non (nil tant que ce n'est pas lu auprès du serveur) : seul le créateur en voit quelque chose.
    let premium: Bool?
    let notice: PlaceNotice?
    let actions: ProfilePlacesActions
    /// Les demandes d'ajout d'un lieu : celles du membre, les messages calmes.
    var requests = ProfileRequestsContent.none

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(verbatim: L10n.format("place_profile_title_named", presentation.miffName))
                .miffTextStyle(textDisplayTitle)
                .foregroundColor(colorTextForm.color)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityAddTraits(.isHeader)
            switch presentation.body {
            case .unavailable:
                unavailable
            case .list(let places):
                PlaceRows(
                    ui: presentation.listUi(places: places, premium: premium, notice: notice, requests: requests),
                    actions: rowActions
                )
            }
        }
    }

    private var rowActions: PlaceListActions {
        let miffId = presentation.miffId
        let actions = self.actions
        return PlaceListActions(
            onOpen: { place in actions.onOpenPlace(miffId, place.id) },
            // Toujours le formulaire : il relit l'abonnement et les lieux auprès du SERVEUR et ne propose l'offre que si la limite est
            // vraiment atteinte. La liste ne connaît qu'une copie de la formule, inconnue ou périmée : elle ne ferme jamais la porte à
            // un créateur Miff+.
            onAdd: { _ in actions.onAddPlace(miffId) },
            onOpenRequest: { request in actions.onOpenRequest(miffId, request.id) },
            onRequestPlace: { actions.onRequestPlace(miffId) }
        )
    }

    private var unavailable: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("place_profile_unavailable")
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
                .fixedSize(horizontal: false, vertical: true)
                .onAppear {
                    MiffNoticeAnnouncements.announceOnce(L10n.string("place_profile_unavailable"))
                }
            Button(action: actions.onRetry) {
                Text("auth_retry_action")
            }
            .buttonStyle(.miffSecondaryFullWidth)
        }
    }
}
