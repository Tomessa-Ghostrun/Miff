import Foundation
@testable import Miff

// Doubles en mémoire pour les tests des notifications. Aucun ne touche au système, au réseau ni au disque.

final class MemoryNotificationPreferencesStore: NotificationPreferencesStore {
    private(set) var values: [String: NotificationPreferences] = [:]
    private(set) var forgetCalls = 0

    func load(uid: String) -> NotificationPreferences {
        values[uid] ?? NotificationPreferences()
    }

    func save(_ preferences: NotificationPreferences, uid: String) {
        values[uid] = preferences
    }

    func forgetAll() {
        forgetCalls += 1
        values = [:]
    }
}

final class MemoryNotificationCenterStore: NotificationCenterStoring {
    private(set) var storedUid: String?
    private(set) var items: [NotificationItem] = []
    private(set) var saveCalls = 0
    private(set) var clearCalls = 0

    func load(uid: String) -> [NotificationItem] {
        storedUid == uid ? items : []
    }

    func save(_ items: [NotificationItem], uid: String) {
        saveCalls += 1
        storedUid = uid
        self.items = items
    }

    func clear() {
        clearCalls += 1
        storedUid = nil
        items = []
    }

    /// Prépare le contenu du « fichier » comme si une session précédente l'avait écrit.
    func seed(_ items: [NotificationItem], uid: String) {
        storedUid = uid
        self.items = items
    }
}

final class MemorySecondChanceStore: NotificationSecondChanceStore {
    private(set) var states: [String: SecondChanceState] = [:]

    func state(uid: String) -> SecondChanceState {
        states[uid] ?? SecondChanceState()
    }

    func save(_ state: SecondChanceState, uid: String) {
        states[uid] = state
    }

    func forget(uid: String) {
        states[uid] = nil
    }
}

final class MemoryNotificationPlanStore: NotificationPlanStoring {
    private(set) var snapshots: [String: NotificationPlanSnapshot] = [:]
    private(set) var ledgers: [String: NotificationLedger] = [:]
    private(set) var clearCalls = 0

    func loadSnapshot(uid: String) -> NotificationPlanSnapshot? {
        snapshots[uid]
    }

    func saveSnapshot(_ snapshot: NotificationPlanSnapshot, uid: String) {
        snapshots[uid] = snapshot
    }

    func loadLedger(uid: String) -> NotificationLedger {
        ledgers[uid] ?? NotificationLedger()
    }

    func saveLedger(_ ledger: NotificationLedger, uid: String) {
        ledgers[uid] = ledger
    }

    func clear() {
        clearCalls += 1
        snapshots = [:]
        ledgers = [:]
    }
}

/// Un ensemble complet de stockages en mémoire.
struct NotificationMemory {
    let preferences = MemoryNotificationPreferencesStore()
    let center = MemoryNotificationCenterStore()
    let secondChance = MemorySecondChanceStore()
    let plan = MemoryNotificationPlanStore()

    var localData: NotificationLocalData {
        NotificationLocalData(preferences: preferences, center: center, secondChance: secondChance, plan: plan)
    }
}

enum NotificationFixtures {
    static let hour = 60 * 60 * 1000
    static let day = 24 * hour

    static func item(
        _ id: String,
        type: NotificationType = .n1,
        miffId: String = "miff-1",
        at createdAtMillis: Int,
        readAt: Int? = nil,
        state: NotificationItemState = .shown
    ) -> NotificationItem {
        NotificationItem(
            id: id,
            type: type,
            miffId: miffId,
            createdAtMillis: createdAtMillis,
            readAtMillis: readAt,
            state: state
        )
    }
}

/// L'état des notifications du téléphone, à la main : ce que le système dit, ce qu'il dit APRÈS sa fenêtre, combien de fois on l'a
/// interrogé et combien de fois les Réglages d'iOS ont été ouverts.
@MainActor
final class NotificationStubPermissions: DevicePermissions {
    var notifications: NotificationAuthorization
    /// Ce que le système dit une fois que la personne a répondu à sa fenêtre.
    var afterWindow: NotificationAuthorization = .authorized
    private(set) var notificationRequests = 0
    private(set) var settingsOpened = 0
    var onLocationChange: (() -> Void)?

    init(_ notifications: NotificationAuthorization) {
        self.notifications = notifications
    }

    func snapshot() async -> PermissionSnapshot {
        PermissionSnapshot(location: .always, notifications: notifications)
    }

    func requestLocationAuthorization() {
        // Les notifications ne demandent jamais la localisation.
    }

    func requestNotifications() async {
        notificationRequests += 1
        notifications = afterWindow
    }

    func openSettings() {
        settingsOpened += 1
    }
}
