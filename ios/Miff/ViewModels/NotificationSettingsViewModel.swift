import Combine
import Foundation

// CONFIDENTIALITÉ, pour ce fichier : Réglages > Notifications LIT l'état de l'autorisation du téléphone et garde des choix sur CET
// appareil seulement. Il ne lit aucune position, n'écrit rien dans Firestore, n'envoie aucun événement Analytics et ne journalise rien.

/// Réglages > Notifications (ticket §9 et §20.11) : l'état de l'autorisation du téléphone, l'interrupteur général, les cinq
/// interrupteurs de type et la nuit calme. Un changement a effet aussitôt, sans bouton « Enregistrer ».
@MainActor
final class NotificationSettingsViewModel: ObservableObject {
    /// L'état du téléphone ; nil tant qu'il n'est pas lu.
    @Published private(set) var authorization: NotificationAuthorization?
    @Published private(set) var preferences: NotificationPreferences
    /// L'amorce (la fenêtre qui explique) est ouverte : la personne a allumé l'interrupteur alors que le téléphone n'a jamais été
    /// interrogé.
    @Published var primeVisible = false

    private let permissions: DevicePermissions
    private let service: NotificationPreferencesService
    private let counters: () -> FollowUpCounters?

    init(
        permissions: DevicePermissions,
        service: NotificationPreferencesService,
        counters: @escaping () -> FollowUpCounters? = { nil }
    ) {
        self.permissions = permissions
        self.service = service
        self.counters = counters
        preferences = service.preferences
        service.$preferences.assign(to: &$preferences)
    }

    /// L'interrupteur général tel qu'il est dessiné : allumé seulement si la personne le veut ET que le téléphone l'autorise.
    var generalIsOn: Bool {
        guard let authorization else {
            return false
        }
        return NotificationGeneralSwitch.isOn(preferences: preferences, authorization: authorization)
    }

    /// Le téléphone a coupé les notifications : l'interrupteur est grisé (un interrupteur de l'app ne contourne pas un refus).
    var generalIsEnabled: Bool {
        guard let authorization else {
            return false
        }
        return NotificationGeneralSwitch.isEnabled(authorization: authorization)
    }

    /// Les interrupteurs de type ne se touchent que si les notifications peuvent arriver.
    var typesAreEnabled: Bool {
        generalIsOn
    }

    /// Pour la recette seulement (builds Debug et Internal) : N3 / N4 jugées et fausses alertes, comptées sur CET appareil, jamais envoyées.
    var recordedCounters: FollowUpCounters? {
        counters()
    }

    func refresh() async {
        service.reload()
        authorization = await permissions.snapshot().notifications
    }

    func setGeneral(_ isOn: Bool) {
        guard let authorization else {
            return
        }
        guard isOn else {
            service.update { $0.settingGeneral(false) }
            return
        }
        switch authorization {
        case .authorized:
            service.update { $0.settingGeneral(true) }
        case .notDetermined:
            // Le téléphone n'a jamais été interrogé : l'amorce explique d'abord, puis la fenêtre du système.
            service.update { $0.settingGeneral(true) }
            primeVisible = true
        case .denied:
            break
        }
    }

    func setType(_ type: NotificationType, isOn: Bool) {
        service.update { $0.setting(type, to: isOn) }
    }

    func setNightCalm(_ isOn: Bool) {
        service.update { $0.settingNightCalm(isOn) }
    }

    /// « Autoriser » sur l'amorce : la fenêtre du système.
    func primeAllowed() async {
        primeVisible = false
        await permissions.requestNotifications()
        await refresh()
    }

    /// « Plus tard » sur l'amorce : la fenêtre du système n'est PAS consommée ; l'interrupteur reste éteint (le téléphone n'autorise
    /// toujours rien).
    func primeDeferred() {
        primeVisible = false
    }

    func openPhoneSettings() {
        permissions.openSettings()
    }
}
