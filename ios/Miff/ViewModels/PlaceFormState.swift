import Foundation

// SÉCURITÉ, pour ce fichier : le nom, l'adresse (tapée et choisie), les coordonnées et les Miffs d'un lieu vivent en mémoire
// dans cet état SEULEMENT : jamais dans un journal, un événement Analytics (aucun événement ne porte un lieu : les seuls sont
// `paywall_*`, avec un déclencheur fixe), un état de restauration ou un rapport de plantage. Chaque `description` ne montre rien.

enum PlaceFormMode: Equatable {
    case create
    case edit
}

/// La liste sous le champ d'adresse.
enum SuggestionsState: Equatable {
    case idle
    case loading
    case found([AddressSuggestion])
    case noResults
    case unavailable
}

/// Ce que le formulaire dit sous le bouton (des phrases calmes, jamais un code).
enum PlaceMessage: Equatable {
    case offline
    case generic
    case miffGone

    init(failure: MiffFailure) {
        self = failure == .offline ? .offline : .generic
    }
}

/// Comment le formulaire se termine : l'hôte quitte l'écran (et montre ce qu'il faut montrer).
enum PlaceFormEnd: Equatable {
    case saved
    case deleted
    case placeGone
}

/// Les valeurs comparées pour savoir si une modification a changé quelque chose.
struct PlaceFormSnapshot: Equatable, CustomStringConvertible {
    let name: String
    let miffIds: Set<String>
    let label: String
    let lat: Double
    let lng: Double
    let radiusM: Int
    let type: PlaceType
    let category: PlaceCategory?

    var description: String {
        "PlaceFormSnapshot(redacted)"
    }
}

struct PlaceFormState: CustomStringConvertible {
    var mode = PlaceFormMode.create
    /// Le contexte (Miff+, mes Miffs, mes lieux) est en cours de lecture.
    var loading = true
    var loadFailed: PlaceMessage?
    var premium = false
    var name = ""
    var nameLimitReached = false
    /// « 2 caractères minimum », montré après un toucher sur « Enregistrer » avec un seul caractère (le bouton marche dès 1).
    var nameTooShort = false
    var owned: [OwnedMiff] = []
    var lastJoinedId: String?
    var selectedMiffIds: [String] = []
    var miffSheetOpen = false
    /// Ce que le champ d'adresse montre : ce qui est tapé, ou l'adresse choisie.
    var addressText = ""
    var chosen: ResolvedAddress?
    /// Une suggestion vient d'être touchée : ses coordonnées sont demandées au service.
    var resolving = false
    var suggestions = SuggestionsState.idle
    var radiusM = PlaceLimits.radiusDefault
    var type = PlaceType.trusted
    var category: PlaceCategory? = PlaceCategory.preselected
    var saving = false
    var message: PlaceMessage?
    var deleteDialogOpen = false
    var deleting = false
    var deleteMessage: ActionMessage?
    /// Le formulaire est fini : l'hôte quitte l'écran.
    var end: PlaceFormEnd?
    /// Miff+ est nécessaire : l'hôte ouvre l'écran de l'offre (et, pour la limite, ferme le formulaire).
    var paywall: PaywallReason?
    /// Modification : avec quoi le formulaire a été ouvert, pour savoir si quelque chose a changé.
    var initial: PlaceFormSnapshot?

    var snapshot: PlaceFormSnapshot {
        PlaceFormSnapshot(
            name: PlaceName.stored(name),
            miffIds: Set(selectedMiffIds),
            label: chosen?.label ?? "",
            lat: chosen?.lat ?? 0,
            lng: chosen?.lng ?? 0,
            radiusM: radiusM,
            type: type,
            category: category
        )
    }

    var changed: Bool {
        initial == nil || initial != snapshot
    }

    var busy: Bool {
        saving || deleting
    }

    /// « Enregistrer » est actif dès UN caractère de nom ET une adresse choisie (décision Q10), et (modification) quand quelque
    /// chose a changé.
    var canSave: Bool {
        !loading && loadFailed == nil && !busy && PlaceName.hasSomething(name) && chosen != nil
            && !selectedMiffIds.isEmpty && category != nil && changed
    }

    /// Les Miffs auxquelles le lieu est attaché MAINTENANT (celles dont une suppression le retire), dans l'ordre de la liste,
    /// quoi qu'il y ait de coché.
    var attachedMiffs: [OwnedMiff] {
        let attached = initial?.miffIds ?? []
        return owned.filter { attached.contains($0.id) }
    }

    /// Modification d'un lieu écrit avant que les adresses soient obligatoires : il n'en a pas, et il faut en choisir une pour
    /// enregistrer.
    var addressMissing: Bool {
        guard mode == .edit, let initial else {
            return false
        }
        return initial.label.isEmpty
    }

    /// Modification d'un lieu écrit avant que les catégories existent : il faut en choisir une pour enregistrer (rien n'est choisi
    /// à la place de la personne).
    var categoryMissing: Bool {
        mode == .edit && category == nil
    }

    /// Une seule Miff : pas de sélecteur, la Miff est montrée (maquette 05E).
    var singleMiff: Bool {
        owned.count <= 1
    }

    var canEditAddress: Bool {
        !loading && loadFailed == nil && !busy
    }

    var description: String {
        "PlaceFormState(redacted)"
    }
}
