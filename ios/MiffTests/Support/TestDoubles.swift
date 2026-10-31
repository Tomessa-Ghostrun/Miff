import Foundation
@testable import Miff

// Doubles en mémoire pour les tests d'authentification et d'onboarding. Aucun n'enregistre un mot de passe ni un jeton
// réel, aucun ne touche au réseau ni à Firebase. Du Swift pur : ces fichiers compilent aussi hors de l'app.

final class MemoryBooleanStore: BooleanStore {
    var values: [String: Bool] = [:]

    func value(forKey key: String, default defaultValue: Bool) -> Bool {
        values[key] ?? defaultValue
    }

    func set(_ value: Bool, forKey key: String) {
        values[key] = value
    }
}

final class MemoryPendingConsentStore: PendingConsentStore {
    var stored: PendingConsent?

    func save(_ consent: PendingConsent) {
        stored = consent
    }

    func read(uid: String) -> PendingConsent? {
        stored.flatMap { $0.uid == uid ? $0 : nil }
    }

    func clear() {
        stored = nil
    }
}

final class MemoryDraftStore: ProfileDraftStore {
    var stored: ProfileDraft?
    private(set) var clearCalls = 0

    func read(uid: String) -> ProfileDraft? {
        guard let draft = stored else {
            return nil
        }
        if draft.uid != uid {
            stored = nil
            return nil
        }
        return draft
    }

    func save(_ draft: ProfileDraft) {
        stored = draft
    }

    func clear() {
        clearCalls += 1
        stored = nil
    }
}

/// Fait office de réglages de confidentialité : note chaque appel, dans l'ordre.
final class RecordingPrivacy: CollectionConsentApplying {
    private(set) var log: [String] = []
    private(set) var analyticsEnabled = false

    func pauseCollection() {
        analyticsEnabled = false
        log.append("pause")
    }

    func applyAccountConsent(_ enabled: Bool) {
        analyticsEnabled = enabled
        log.append(enabled ? "on" : "off")
    }

    /// Le journal sans répétitions immédiates (le gestionnaire peut mettre en pause deux fois de suite).
    var states: [String] {
        log.enumerated().filter { index, entry in index == 0 || log[index - 1] != entry }.map { $0.element }
    }
}

final class EventLogSink: AnalyticsSink {
    private(set) var events: [(name: String, params: [String: Any])] = []

    func log(eventName: String, params: [String: Any]) {
        events.append((eventName, params))
    }

    var names: [String] {
        events.map { $0.name }
    }
}

final class FakeNetwork: NetworkMonitor {
    var online: Bool

    init(online: Bool = true) {
        self.online = online
    }

    func isOnline() -> Bool {
        online
    }
}

/// Chronologie partagée des appels dont l'ordre compte (vidage, purge, déconnexion...).
final class Timeline {
    private(set) var entries: [String] = []

    func add(_ entry: String) {
        entries.append(entry)
    }
}

final class FakeAuthGateway: AuthGateway {
    private let timeline: Timeline
    private var uid: String?
    private var continuation: AsyncStream<String?>.Continuation?

    var method: AnalyticsEvent.Method? = .email
    var signInResult: AuthOutcome = .success(uid: "uid-1", isNewUser: false)
    var createResult: AuthOutcome = .success(uid: "uid-1", isNewUser: true)
    var socialResult: AuthOutcome = .success(uid: "uid-1", isNewUser: true)
    var deleteResults: [AuthFailure?] = []
    var reauthResult: AuthFailure?
    var name: String?
    /// Nombre de déconnexions qui échouent encore (la session reste ouverte), pour tester la reprise.
    var signOutFailures = 0
    private(set) var signInCalls = 0
    private(set) var createCalls = 0
    private(set) var socialCalls = 0
    private(set) var deleteCalls = 0
    private(set) var signOutCalls = 0
    private(set) var reauthCalls = 0

    init(timeline: Timeline = Timeline()) {
        self.timeline = timeline
    }

    var currentUid: String? {
        uid
    }

    func uidChanges() -> AsyncStream<String?> {
        AsyncStream { continuation in
            self.continuation = continuation
            continuation.yield(self.uid)
        }
    }

    func setSignedIn(_ newUid: String?, method newMethod: AnalyticsEvent.Method? = .email) {
        method = newMethod
        uid = newUid
        continuation?.yield(newUid)
    }

    func signInWithEmail(email: String, password: String) async -> AuthOutcome {
        signInCalls += 1
        if case .success(let id, _) = signInResult {
            setSignedIn(id, method: .email)
        }
        return signInResult
    }

    func createAccountWithEmail(email: String, password: String) async -> AuthOutcome {
        createCalls += 1
        if case .success(let id, _) = createResult {
            setSignedIn(id, method: .email)
        }
        return createResult
    }

    func signIn(with credential: SocialCredential) async -> AuthOutcome {
        socialCalls += 1
        if case .success(let id, _) = socialResult {
            setSignedIn(id, method: credential.method)
        }
        return socialResult
    }

    func reauthenticate(with credential: SocialCredential) async -> AuthFailure? {
        reauthCalls += 1
        timeline.add("reauth")
        return reauthResult
    }

    func deleteCurrentAccount() async -> AuthFailure? {
        deleteCalls += 1
        timeline.add("delete")
        let result: AuthFailure? = deleteResults.isEmpty ? nil : deleteResults.removeFirst()
        if result == nil {
            uid = nil
            continuation?.yield(nil)
        }
        return result
    }

    func signInMethod() -> AnalyticsEvent.Method? {
        method
    }

    func displayName() -> String? {
        name
    }

    func signOut() {
        signOutCalls += 1
        timeline.add("signOut")
        if signOutFailures > 0 {
            signOutFailures -= 1
            return
        }
        uid = nil
        continuation?.yield(nil)
    }
}

final class FakeAccountRepository: AccountRepository {
    var result: AccountLoad = .loaded(
        AccountSnapshot(profileExists: false, hasMembership: false, analyticsConsentEnabled: false)
    )
    private(set) var loadCalls = 0
    var onLoad: () -> Void = {}

    func setLoaded(
        profileExists: Bool,
        hasMembership: Bool = false,
        analyticsConsent: Bool = false,
        hasPhone: Bool = true,
        hasTermsVersion: Bool = true
    ) {
        result = .loaded(
            AccountSnapshot(
                profileExists: profileExists,
                hasMembership: hasMembership,
                analyticsConsentEnabled: analyticsConsent,
                hasPhone: hasPhone,
                hasTermsVersion: hasTermsVersion
            )
        )
    }

    func load(uid: String) async -> AccountLoad {
        loadCalls += 1
        onLoad()
        return result
    }
}

final class FakeCachePurger: CachePurger {
    private let timeline: Timeline
    private(set) var purgeCalls = 0
    private(set) var purgeIfPendingCalls = 0

    init(timeline: Timeline) {
        self.timeline = timeline
    }

    func flushPendingWrites() async {
        timeline.add("flush")
    }

    func purge() async -> Bool {
        purgeCalls += 1
        timeline.add("purge")
        return true
    }

    func markPurgePending() {
        timeline.add("markPending")
    }

    func purgeIfPending() async {
        purgeIfPendingCalls += 1
    }
}

/// Fenêtre Google / Apple factice : rend ce qu'on lui demande.
final class FakeSocialSource: SocialCredentialSource {
    var google: SocialTokenResult = .credential(.google(idToken: "id-token", accessToken: "access-token"))
    var apple: SocialTokenResult = .credential(.apple(idToken: "id-token", rawNonce: "nonce", name: nil))
    private(set) var googleCalls = 0
    private(set) var appleCalls = 0

    func requestGoogle() async -> SocialTokenResult {
        googleCalls += 1
        return google
    }

    func requestApple() async -> SocialTokenResult {
        appleCalls += 1
        return apple
    }
}

/// Horloge de test : le temps n'avance que quand on le dit, et attendre ne fait qu'avancer l'horloge (rien n'attend pour de
/// vrai). Avec `holdsLongPauses`, une longue attente (10 s ou plus, comme la pause de renvoi) SUSPEND jusqu'à `release()`,
/// pour pouvoir observer l'état pendant la pause.
final class TestClock {
    private(set) var now = 0
    private(set) var pauses: [Int] = []
    var holdsLongPauses = false
    private var waiting: [CheckedContinuation<Void, Never>] = []

    func advance(by millis: Int) {
        now += millis
    }

    func release() {
        let continuations = waiting
        waiting = []
        continuations.forEach { $0.resume() }
    }

    var clock: AppClock {
        AppClock(
            nowMillis: { self.now },
            pause: { millis in
                self.pauses.append(millis)
                if self.holdsLongPauses && millis >= 10_000 {
                    await withCheckedContinuation { continuation in
                        self.waiting.append(continuation)
                    }
                } else {
                    self.now += millis
                }
            }
        )
    }
}

/// Tout ce dont un `SessionManager` a besoin, câblé avec des doubles, et ce qu'il faut regarder ensuite.
@MainActor
final class SessionHarness {
    let timeline = Timeline()
    let gateway: FakeAuthGateway
    let flags = MemoryBooleanStore()
    let pending = MemoryPendingConsentStore()
    let privacy = RecordingPrivacy()
    let sink = EventLogSink()
    let tracker: AnalyticsTracker
    let cache: FakeCachePurger
    let accounts = FakeAccountRepository()
    let drafts = MemoryDraftStore()
    let manager: SessionManager

    /// Ce qui était collecté au moment où le compte a été lu : ce doit être rien (critère X9).
    private(set) var collectionWhenAccountRead: Bool?

    /// `freshInstall` vrai : la première ouverture d'une installation (aucun repère d'installation sur l'appareil).
    init(teardown: [SessionTeardown] = [], freshInstall: Bool = false) {
        let timeline = self.timeline
        let gateway = FakeAuthGateway(timeline: timeline)
        let privacy = self.privacy
        self.gateway = gateway
        tracker = AnalyticsTracker(sink: sink, isEnabled: { privacy.analyticsEnabled })
        cache = FakeCachePurger(timeline: timeline)
        manager = SessionManager(
            SessionDependencies(
                gateway: gateway,
                accounts: accounts,
                pending: pending,
                flags: flags,
                privacy: privacy,
                tracker: tracker,
                cache: cache,
                teardown: teardown,
                drafts: drafts
            )
        )
        accounts.onLoad = { [weak self] in
            self?.collectionWhenAccountRead = privacy.analyticsEnabled
        }
        if !freshInstall {
            flags.set(true, forKey: SessionManager.keyInstallMarker)
        }
    }

    /// Laisse aux tâches de fond (guetteur de session perdue) le temps de réagir à ce qui vient d'arriver.
    func settle() async {
        for _ in 0..<5 {
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
    }

    /// Attend (par petites pauses) qu'une condition soit vraie : le guetteur de session perdue travaille en arrière-plan.
    func waitUntil(_ condition: () -> Bool) async {
        for _ in 0..<200 where !condition() {
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
    }
}
