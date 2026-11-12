import Foundation
import UserNotifications

/// Un seul point où l'app apprend qu'un proche a donné des nouvelles PENDANT qu'elle est ouverte (Miff du compte vérifiée, jamais sa propre update).
/// Rien n'est branché dessus aujourd'hui : le centre de notifications (NOTIF-P0) y posera sa ligne silencieuse.
@MainActor
final class PushForegroundHook {
    var onCircleNews: ((CircleNewsTarget) -> Void)?
}

/// Les services des notifications push (SRV-2I et SRV-4I), construits d'un seul tenant. Rien ici n'ouvre Firestore ni ne demande quoi que ce
/// soit au système avant qu'une session confirmée, l'accord de la personne et l'autorisation du système soient là (voir `PushRegistrar`).
/// Notes d'exploitation : `docs/dev/fcm-ios.md`.
@MainActor
struct PushServices {
    /// Tient le document de CE téléphone dans le registre des appareils.
    let registrar: PushRegistrar
    /// L'interrupteur « Quand un proche donne des nouvelles » (Réglages > Notifications).
    let circleNews: CircleNewsSwitch
    /// L'accord à être notifiée, posé par l'écran qui parle de l'identifiant du téléphone.
    let consent: PushConsentStore
    /// La demande « ouvre le Feed de cette Miff » d'un toucher sur la notification.
    let openRequests: PushOpenRequests
    let hook = PushForegroundHook()
    /// Reçoit les réponses du système pour les messages du serveur. À retenir (le système n'en garde qu'une référence faible).
    let delegate = PushNotificationDelegate()

    private let tokens: FirebasePushTokenSource
    private let local: PushLocalState
    private let switchStore: CircleNewsSwitchStore
    private let currentUid: () -> String?
    private let directory: MiffDirectory

    static func make(
        gateway: AuthGateway,
        device: DeviceSession,
        network: NetworkMonitor,
        directory: MiffDirectory
    ) -> PushServices {
        let tokens = FirebasePushTokenSource()
        let local = PushLocalFileStore()
        let consent = UserDefaultsPushConsentStore()
        let switchStore = UserDefaultsCircleNewsSwitchStore()
        let probe = SystemNotificationAuthorizationProbe()
        let registrar = PushRegistrar(
            PushRegistrarDependencies(
                registry: FirestoreDeviceTokenRegistry(),
                local: local,
                tokens: tokens,
                currentUid: { gateway.currentUid },
                sessionDeviceId: { device.currentDeviceId },
                // L'accord de CE compte ET l'autorisation du système, lue maintenant (la personne peut l'avoir retirée dans les Réglages).
                consent: { uid in
                    guard consent.isGiven(uid: uid) else {
                        return false
                    }
                    return await probe.isAuthorized()
                },
                notifyUpdates: { uid in switchStore.isOn(uid: uid) },
                isOnline: { network.isOnline() },
                timeZoneId: { TimeZone.current.identifier },
                appVersion: { Self.appVersion() },
                nowMillis: { Int(Date().timeIntervalSince1970 * 1000) },
                clearDelivered: { UNUserNotificationCenter.current().removeAllDeliveredNotifications() }
            )
        )
        let circleNews = CircleNewsSwitch(store: switchStore, currentUid: { gateway.currentUid })
        circleNews.onChange = {
            Task { await registrar.sync() }
        }
        return PushServices(
            registrar: registrar,
            circleNews: circleNews,
            consent: consent,
            openRequests: PushOpenRequests(),
            tokens: tokens,
            local: local,
            switchStore: switchStore,
            currentUid: { gateway.currentUid },
            directory: directory
        )
    }

    /// Branche les récepteurs du système : le jeton d'APNs, les jetons de Firebase, les réponses aux notifications. Appelé une fois, au démarrage
    /// (jamais dans les tests). N'allume AUCUN service de notification : il ne l'est que par le registraire, après l'accord de la personne.
    func start() {
        guard !FirebaseBootstrap.isRunningUnitTests else {
            return
        }
        let tokens = self.tokens
        let registrar = self.registrar
        MiffAppDelegate.onAPNsToken = { @Sendable data in
            Task { @MainActor in tokens.setAPNsToken(data) }
        }
        tokens.attach { @Sendable token in
            Task { @MainActor in await registrar.sync(freshToken: token) }
        }
        let requests = openRequests
        let hook = self.hook
        let currentUid = self.currentUid
        let directory = self.directory
        delegate.onTap = { @Sendable target in
            Task { @MainActor in
                // Personne de connecté : le toucher ouvre l'app, rien de plus. La Miff est comparée à celles du compte par l'hôte du Feed.
                guard currentUid() != nil else {
                    return
                }
                requests.request(target)
            }
        }
        delegate.onForeground = { @Sendable target in
            Task { @MainActor in
                let accepted = PushOpenRules.accepts(
                    target, currentUid: currentUid(), miffIds: Self.knownMiffIds(directory)
                )
                if accepted {
                    hook.onCircleNews?(target)
                }
            }
        }
        UNUserNotificationCenter.current().delegate = delegate
    }

    /// « 0.1.0 (27) » : le nom de version et le numéro de build, coupés aux 32 caractères que la règle accepte.
    private static func appVersion() -> String {
        let info = Bundle.main.infoDictionary
        return DeviceAppVersion.of(
            name: info?["CFBundleShortVersionString"] as? String,
            build: info?["CFBundleVersion"] as? String
        )
    }

    /// Les Miffs du compte, ou nil si l'annuaire ne les a pas (encore) lues : l'appartenance est alors INCONNUE, et le message est ignoré.
    static func knownMiffIds(_ directory: MiffDirectory) -> [String]? {
        if case .ready(let ready) = directory.state {
            return ready.miffs.map { $0.id }
        }
        return nil
    }

    /// Une synchronisation du registre (retour au premier plan, session confirmée, accord donné).
    func sync() {
        let registrar = self.registrar
        Task { await registrar.sync() }
    }

    /// Fin de session (déconnexion, suppression du compte, session prise par un autre téléphone) : l'invalidation du jeton chez Firebase passe en
    /// PREMIER, puis le document et la mémoire du téléphone ; la demande d'ouverture en attente est oubliée.
    var teardown: SessionTeardown {
        let registrar = self.registrar
        let requests = openRequests
        return {
            await registrar.forget()
            await MainActor.run { requests.clear() }
        }
    }

    /// Ce que la suppression d'un compte efface du téléphone POUR CE COMPTE : l'accord, l'interrupteur et la mémoire de l'enregistrement.
    var erasers: [LocalDataEraser] {
        let consent = self.consent
        let switchStore = self.switchStore
        let local = self.local
        return [
            { uid in consent.forget(uid: uid) },
            { uid in switchStore.forget(uid: uid) },
            { _ in local.clear() }
        ]
    }
}
