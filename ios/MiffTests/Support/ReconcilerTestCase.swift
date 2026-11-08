import XCTest
@testable import Miff

/// Le banc d'essai partagé des tests du réconciliateur : un faux système de notifications, une horloge et un compte à la main, des
/// stockages en mémoire, et les membres types. Aucune de ces classes ne touche au système, au réseau ni au disque.
@MainActor
class ReconcilerTestCase: XCTestCase {
    let midnight = 1_794_096_000_000
    let miffId = "miff-1"

    func at(_ hour: Int, _ minute: Int, ms: Int = 0) -> Int {
        midnight + hour * 3_600_000 + minute * 60_000 + ms
    }

    func after(_ hour: Int, _ minute: Int) -> Int {
        at(hour, minute, ms: 1)
    }

    var utc: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC") ?? .current
        return calendar
    }

    // MARK: - Le banc d'essai

    final class State {
        var now = 0
        var uid: String? = "me"
        var active: String? = "miff-1"
        var foreground = false
        var language = "fr"
        var waiting: Set<String> = []
    }

    final class FakeScheduler: LocalNotificationScheduling {
        private(set) var pending: [String: LocalNotificationRequest] = [:]
        var delivered: Set<String> = []
        private(set) var added: [LocalNotificationRequest] = []
        private(set) var removedDelivered: [String] = []

        func pendingIdentifiers() async -> Set<String> {
            Set(pending.keys)
        }

        func deliveredIdentifiers() async -> Set<String> {
            delivered
        }

        func add(_ request: LocalNotificationRequest) async {
            pending[request.identifier] = request
            added.append(request)
        }

        func removePending(_ identifiers: [String]) async {
            for identifier in identifiers {
                pending[identifier] = nil
            }
        }

        func removeDelivered(_ identifiers: [String]) async {
            removedDelivered += identifiers
            delivered.subtract(identifiers)
        }

        /// Le système a livré cette demande : elle n'est plus en attente.
        func fire(_ identifier: String) {
            pending[identifier] = nil
            delivered.insert(identifier)
        }
    }

    final class FakeAuthorization: NotificationAuthorizationReading {
        var value = NotificationAuthorization.authorized

        func authorization() async -> NotificationAuthorization {
            value
        }
    }

    struct Rig {
        let state: State
        let scheduler: FakeScheduler
        let authorization: FakeAuthorization
        let memory: NotificationMemory
        let preferences: NotificationPreferencesService
        let center: NotificationCenterService
        let reconciler: NotificationReconciler
    }

    func makeRig(now: Int) -> Rig {
        let state = State()
        state.now = now
        let scheduler = FakeScheduler()
        let authorization = FakeAuthorization()
        let memory = NotificationMemory()
        let preferences = NotificationPreferencesService(store: memory.preferences, currentUid: { state.uid })
        let center = NotificationCenterService(
            store: memory.center, currentUid: { state.uid }, nowMillis: { state.now }
        )
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC") ?? .current
        var deps = NotificationReconcilerDependencies(
            scheduler: scheduler,
            authorization: authorization,
            preferences: preferences,
            plans: memory.plan,
            center: center,
            currentUid: { state.uid }
        )
        deps.activeMiffId = { state.active }
        deps.agreementWaiting = { state.waiting.contains($0) }
        deps.nowMillis = { state.now }
        deps.calendar = { calendar }
        deps.isForeground = { state.foreground }
        deps.language = { state.language }
        deps.text = { $0 }
        return Rig(
            state: state, scheduler: scheduler, authorization: authorization, memory: memory,
            preferences: preferences, center: center, reconciler: NotificationReconciler(deps)
        )
    }

    func green(_ uid: String) -> PlanMember {
        PlanMember(
            uid: uid, sharing: true, presence: "in_trusted", presenceAtMillis: at(8, 0), lastUpdateAtMillis: at(8, 0)
        )
    }

    func out(_ uid: String, ref: Int, update: Int? = nil) -> PlanMember {
        PlanMember(uid: uid, sharing: true, presence: "out", presenceAtMillis: ref, lastUpdateAtMillis: update)
    }

    func seed(_ rig: Rig, _ members: [PlanMember], savedAt: Int? = nil) {
        let snapshot = NotificationPlanSnapshot(
            miffId: miffId, myUid: "me", members: members, savedAtMillis: savedAt ?? rig.state.now
        )
        rig.memory.plan.saveSnapshot(snapshot, uid: "me")
    }

    func identifier(_ type: NotificationType) -> String {
        NotificationIdentifier.make(type: type, miffId: miffId)
    }
}
