import Foundation

// MODE VOYAGE dans le Feed (docs/tickets/mode-voyage.md §6, VOY-3I) : les phrases d'un voyage, écrites dans la langue du téléphone avec les
// clés `voyage_*` du catalogue (mêmes clés et mêmes textes qu'Android). Le jour d'un voyage arrive du formateur du SYSTÈME, jamais assemblé
// à la main (« 15 nov. », « Nov 15 », l'année seulement quand ce n'est pas l'année en cours). Aucune phrase ne dit que la position est arrêtée :
// elle reste partagée. Rien n'est journalisé ; les prénoms reçus sont montrés, jamais gardés.

extension FeedTexts {
    /// « 15 nov. » (« 15 nov. 2027 » quand ce n'est pas l'année en cours), par le formateur du système.
    func travelDay(_ day: TravelDay) -> String {
        TravelDayFormat.short(day, locale: locale)
    }

    /// « En voyage jusqu'au 15 nov. » ; sur MA carte « Mode voyage jusqu'au 15 nov. ».
    func travelLabel(_ day: TravelDay, mine: Bool) -> String {
        text(mine ? "voyage_feed_mine" : "voyage_feed_label", travelDay(day))
    }

    /// « Depuis le 8 nov. » : le JOUR où le voyage a commencé, dans le fuseau du lecteur.
    func travelSince(_ millis: Int) -> String {
        text("voyage_feed_since", date(millis))
    }

    /// La phrase sur les AUTRES membres en voyage, sous la bulle habituelle ou comme bulle des voyageurs.
    func travelNote(_ note: TravelNote) -> String {
        switch note {
        case .one(let name, let until):
            return text("voyage_bubble_one", name, travelDay(until))
        case .many(let count, _):
            return text("voyage_bubble_many", count)
        }
    }

    /// Le titre de la bulle neutre quand tous les autres membres connus sont en voyage.
    func travelersHeadline(_ note: TravelNote) -> String {
        switch note {
        case .one:
            return travelNote(note)
        case .many(let count, let all):
            return all ? text("voyage_bubble_all") : text("voyage_bubble_many", count)
        }
    }

    /// Ce que le lecteur d'écran dit de la bulle : son titre, puis la ligne ajoutée.
    func announcement(_ bubble: Bubble, travelNote note: TravelNote?) -> String {
        guard let note else {
            return announcement(bubble)
        }
        return announcement(bubble) + ". " + travelNote(note)
    }
}
