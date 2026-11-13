import Foundation

// CONFIDENTIALITÉ, pour ce fichier : mêmes règles que `PlaceModels.swift` (aucun nom, adresse ni coordonnée dans un journal ou un
// événement). Ce sont de petites règles pures que l'onglet Profil utilise, pour qu'elles soient testées sans écran.

/// Ce que la ligne d'un lieu dit à VoiceOver : un seul bloc « nom, catégorie X, adresse », et pour une zone de danger le mot « Zone de
/// danger » à la fin (l'étiquette visible serait sinon cachée par le bloc : le type du lieu ne serait pas dit).
enum PlaceRowSpeech {
    static func spoken(for place: Place) -> String {
        let described = describe(place)
        guard place.type == .risk else {
            return described
        }
        return L10n.format("place_list_row_type_a11y", described, place.type.label)
    }

    private static func describe(_ place: Place) -> String {
        let address = place.address ?? ""
        switch (place.category, address.isEmpty) {
        case (.some(let category), false):
            return L10n.format("place_list_row_a11y", place.name, category.label, address)
        case (.some(let category), true):
            return L10n.format("place_list_row_noaddress_a11y", place.name, category.label)
        case (.none, false):
            return L10n.format("place_list_row_nocategory_a11y", place.name, address)
        case (.none, true):
            return L10n.format("place_list_row_nocategory_noaddress_a11y", place.name)
        }
    }
}

/// Ce que la section « Lieux de confiance de <Miff> » de l'onglet Profil dessine, pour la Miff dont le carrousel montre la carte. nil :
/// rien n'est dessiné (pas de carte de Miff montrée, Miff inconnue, ou lieux pas encore lus : jamais une absence inventée).
struct ProfilePlacesPresentation: Equatable, CustomStringConvertible {
    enum Body: Equatable {
        /// Illisible : une phrase calme et « Réessayer ».
        case unavailable
        /// Les lieux (éventuellement aucun : la liste dit alors la phrase qui convient au rôle).
        case list([Place])
    }

    let miffId: String
    let miffName: String
    let isCreator: Bool
    let body: Body

    var description: String {
        "ProfilePlacesPresentation(redacted)"
    }

    /// `miffs` : les Miffs de la personne (le créateur de chacune y est déjà). `myUid` : la personne connectée.
    static func make(state: ProfilePlacesState, miffs: [MiffSummary], myUid: String?) -> ProfilePlacesPresentation? {
        guard let selected = state.selectedMiffId, let miff = miffs.first(where: { $0.id == selected }) else {
            return nil
        }
        let isCreator = PlaceViewerRole.isCreator(ownerUid: miff.ownerUid, myUid: myUid)
        let body: Body
        switch state.view {
        case .loading:
            return nil
        case .unavailable:
            body = .unavailable
        case .ready(let places):
            body = .list(places)
        }
        return ProfilePlacesPresentation(miffId: miff.id, miffName: miff.name, isCreator: isCreator, body: body)
    }

    /// La liste des lieux pour `PlaceRows`. La formule (compteur, pastille « Miff+ ») ne concerne que le créateur : un membre n'en lit
    /// jamais un mot. Le message « enregistré » non plus (lui seul enregistre) ; « Ce lieu n'existe plus. » concerne tout le monde.
    func listUi(places: [Place], premium: Bool?, notice: PlaceNotice?) -> PlaceListUi {
        PlaceListUi(
            places: places,
            isCreator: isCreator,
            premium: isCreator ? premium : nil,
            notice: shownNotice(notice)
        )
    }

    private func shownNotice(_ notice: PlaceNotice?) -> PlaceNotice? {
        if isCreator {
            return notice
        }
        if case .gone? = notice {
            return notice
        }
        return nil
    }
}
