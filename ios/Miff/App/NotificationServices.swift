import Foundation
import UIKit
import UserNotifications

/// Les services des notifications (NOTIF-P0), construits d'un seul tenant. Rien ici n'ouvre Firestore, ne lit une position ni ne demande
/// quoi que ce soit au système : l'autorisation n'est demandée que par un toucher de la personne (amorce, Réglages, carte du Feed). Les
/// notifications sont LOCALES : le téléphone se les programme lui-même, aucun serveur ne les envoie (aucune clé APNs, aucun push).
@MainActor
struct NotificationServices {
    /// Le centre de notifications local (cloche, liste).
    let center: NotificationCenterService
    /// Les choix de Réglages > Notifications.
    let preferences: NotificationPreferencesService
    /// Les changements d'onglet demandés par un toucher sur une ligne du centre ou sur une notification.
    let tabRequests: TabRequests
    /// Tout ce que les notifications gardent sur le téléphone, avec ce qui l'efface.
    let localData: NotificationLocalData
    /// Fait correspondre les notifications du système à ce que dit le planificateur (N1 à N4).
    let reconciler: NotificationReconciler
    /// Reçoit les réponses du système ; retenu ici parce que le système n'en garde qu'une référence faible.
    let responseDelegate: NotificationResponseDelegate

    static func make(currentUid: @escaping () -> String?) -> NotificationServices {
        let data = NotificationLocalData.live()
        let center = NotificationCenterService(store: data.center, currentUid: currentUid)
        let preferences = NotificationPreferencesService(store: data.preferences, currentUid: currentUid)
        let tabRequests = TabRequests()
        let scheduler = SystemLocalNotificationScheduler()
        scheduler.registerCategories()
        let reconciler = NotificationReconciler(
            NotificationReconcilerDependencies(
                scheduler: scheduler,
                authorization: SystemNotificationAuthorizationReader(),
                preferences: preferences,
                plans: data.plan,
                center: center,
                currentUid: currentUid
            )
        )
        // Un choix qui change (interrupteur général ou de type, nuit calme) : les rappels sont réconciliés dans la seconde.
        preferences.onChange = { _ in
            Task { await reconciler.reconcile() }
        }
        let delegate = NotificationResponseDelegate()
        // Les rappels du système arrivent sur un fil quelconque : ces fermetures ne dépendent d'aucun acteur, elles passent par le fil principal.
        delegate.onForeground = { @Sendable identifier in
            Task { @MainActor in reconciler.foregroundDelivery(identifier: identifier) }
        }
        delegate.onTap = { @Sendable type, key, miffId in
            Task { @MainActor in
                guard let destination = reconciler.notificationTapped(type: type, key: key, miffId: miffId) else {
                    return
                }
                let showsUpdate = TabBarConfig.current.showsUpdateButton
                tabRequests.request(NotificationNavigation.tab(for: destination, showsUpdateButton: showsUpdate))
            }
        }
        UNUserNotificationCenter.current().delegate = delegate
        return NotificationServices(
            center: center,
            preferences: preferences,
            tabRequests: tabRequests,
            localData: data,
            reconciler: reconciler,
            responseDelegate: delegate
        )
    }

    /// Branche ce qui n'existe qu'une fois l'annuaire des Miffs construit : la Miff active, l'accord attendu, l'état du premier plan.
    func connect(directory: MiffDirectory, awaiting: AwaitingAgreement) {
        reconciler.configure { deps in
            deps.activeMiffId = {
                // Annuaire lu : sans Miff active (plus aucune Miff), une valeur qui ne correspond à aucune Miff annule les rappels.
                // Annuaire pas encore lu (réveil en arrière-plan) : nil, et l'instantané dit la Miff qu'il suivait.
                if case .ready(let ready) = directory.state {
                    return ready.activeId ?? ""
                }
                return nil
            }
            deps.agreementWaiting = { awaiting.waiting.contains($0) }
            deps.isForeground = { UIApplication.shared.applicationState == .active }
        }
    }

    /// Le suivi des lieux a détecté un nouveau statut de MA présence (arrivée, départ) : N1 et N2 sont programmées ou annulées aussitôt,
    /// même app fermée. Peut être appelé depuis n'importe quel fil.
    var presenceChanged: (String, StatusFact) -> Void {
        let reconciler = self.reconciler
        return { @Sendable miffId, fact in
            Task { @MainActor in
                await reconciler.myPresenceChanged(miffId: miffId, fact: fact)
                // N6 : j'entre moi-même dans une zone de danger (dans toutes mes Miffs), notification locale immédiate.
                if fact.presence == .inRisk {
                    await reconciler.dangerZoneEntered(
                        miffId: miffId, placeId: fact.placeId, enteredAtMillis: fact.presenceAtMillis
                    )
                }
            }
        }
    }

    /// MODE VOYAGE : le suivi des lieux dit que mon voyage commence, change de date ou se termine (N7, et la coupure de N1 à N4). Peut être appelé
    /// depuis n'importe quel fil.
    var travelChanged: (String, Int?, Int?) -> Void {
        let reconciler = self.reconciler
        return { @Sendable uid, until, previous in
            Task { @MainActor in
                await reconciler.myTravelChanged(uid: uid, untilMillis: until, previousUntilMillis: previous)
            }
        }
    }

    /// Fin de session (déconnexion, suppression du compte, session prise par un autre appareil) : le centre, les choix et l'instantané sont
    /// vidés, et les notifications encore affichées ou en attente sont retirées pour que la personne suivante n'en voie aucune.
    var teardown: SessionTeardown {
        let center = self.center
        let preferences = self.preferences
        let data = self.localData
        return {
            await MainActor.run {
                center.purge()
                preferences.forget()
            }
            data.eraseSession()
            await SystemLocalNotificationsCleaner().removeAll()
        }
    }
}
