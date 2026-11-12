import Foundation

// N7 « Ton mode voyage est terminé » dans le planificateur (VOY-4I ; ticket mode-voyage §9.1). Séparé de `NotificationPlanner` pour garder
// chaque fichier court. Du Swift PUR.

extension NotificationPlanner {
    enum TravelEndTiming {
        case scheduled
        case catchUp
        case silent
    }

    struct TravelEndPlanned {
        let planned: PlannedNotification
        let timing: TravelEndTiming
    }

    /// N7 est-elle voulue, et quand ? Il faut une fin de voyage connue, une session, l'autorisation du téléphone, l'interrupteur général ET celui
    /// de N7, et une clé jamais notifiée. Aucune Miff, aucun instantané n'est nécessaire. Passée de moins de 15 minutes : rattrapage affiché ;
    /// passée de plus, jusqu'à 24 h : entrée silencieuse du centre ; au-delà : oubliée.
    static func travelEndPlanned(_ input: NotificationPlanInput) -> TravelEndPlanned? {
        guard let uid = input.sessionUid,
              let until = input.myTravelUntilMillis,
              input.authorization.isAllowed,
              input.preferences.isEnabled(.n7) else {
            return nil
        }
        let key = TravelEndNotice.key(uid: uid, untilMillis: until)
        guard !input.notifiedKeys.contains(key) else {
            return nil
        }
        let due = TravelEndNotice.dueAt(untilMillis: until, calendar: input.calendar)
        let late = input.nowMillis - due
        let timing: TravelEndTiming
        if late < 0 {
            timing = .scheduled
        } else if late <= catchUpWindowMillis {
            timing = .catchUp
        } else if late <= TravelEndNotice.catchUpWindowMillis {
            timing = .silent
        } else {
            return nil
        }
        // N7 part à 8 h : jamais la nuit, donc jamais « quiet ». Pas de Miff : l'identifiant de Miff est vide.
        return TravelEndPlanned(
            planned: PlannedNotification(type: .n7, miffId: "", key: key, fireAtMillis: due, quiet: false),
            timing: timing
        )
    }

    /// Une N7 déjà AFFICHÉE doit-elle rester à l'écran ? Tant que l'interrupteur de N7 et l'autorisation tiennent (elle ne dépend d'aucune couleur).
    static func travelEndStillWanted(
        preferences: NotificationPreferences,
        authorization: NotificationAuthorization
    ) -> Bool {
        authorization.isAllowed && preferences.isEnabled(.n7)
    }
}
