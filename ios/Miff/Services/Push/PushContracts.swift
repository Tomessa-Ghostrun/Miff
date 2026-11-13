import Foundation

// SRV-2I : les contrats des notifications push. Du Swift pur : les vraies implémentations sont dans `Services/Firebase` (Firestore, Firebase
// Messaging) et `Services/Push` (fichier privé, réglages de l'app) ; les tests utilisent des doubles en mémoire.
//
// SÉCURITÉ : le jeton de notification n'est écrit dans aucun journal, aucun événement Analytics, aucun rapport de plantage, aucun message
// d'erreur. Aucun type ci-dessous ne le décrit dans sa représentation texte.

/// Le registre des appareils (`users/{uid}/devices/{deviceId}`). Chaque appel atteint le SERVEUR (une transaction ou une lecture auprès du
/// serveur) ou lève une erreur : rien n'est mis en file (une écriture différée de plusieurs jours serait périmée).
protocol DeviceTokenRegistry {
    /// Crée le document de `deviceId`, ou le met à jour s'il existe (contrat §4.1 : lire, puis créer ou mettre à jour).
    func upsert(uid: String, deviceId: String, entry: DeviceEntry) async throws
    /// Supprime les AUTRES documents du compte (« un compte, un appareil », contrat §4.2).
    func deleteOthers(uid: String, keepDeviceId: String) async throws
    /// Supprime le document de `deviceId` (un document déjà absent n'est pas une erreur).
    func delete(uid: String, deviceId: String) async throws
}

/// Le service de notification du téléphone (APNs + Firebase Messaging) : éteint tant que la personne n'a rien accordé, jamais de jeton avant.
@MainActor
protocol PushTokenSource: AnyObject {
    /// Allume le service (enregistrement auprès d'APNs, génération du jeton) et rend le jeton déjà connu, ou nil : il arrivera alors par
    /// `onNewToken` (le système le fournit de façon asynchrone).
    func enableAndFetch() async -> String?
    /// Éteint le service : l'invalidation du jeton chez Firebase (`deleteToken`), le démarrage automatique coupé, le désenregistrement
    /// auprès d'APNs. Ne lève jamais ; bornée dans le temps (hors connexion, le désenregistrement local suffit à couper la livraison).
    func disable() async
}

/// Ce que ce téléphone a retenu de sa dernière écriture (un fichier privé, hors sauvegarde).
protocol PushLocalState: AnyObject {
    func record() -> RegistrationRecord?
    func saveRecord(_ record: RegistrationRecord)
    /// Le service de notification est allumé (un jeton peut exister) : il faut l'éteindre à la sortie, même sans document.
    func serviceOn() -> Bool
    func setServiceOn(_ value: Bool)
    /// L'identifiant de la session dont les AUTRES documents ont déjà été supprimés (une fois par session).
    func othersClearedFor() -> String?
    func setOthersClearedFor(_ deviceId: String?)
    /// Tout oublier (sortie).
    func clear()
}

/// « Cette personne a dit OUI à être notifiée, APRÈS avoir lu que Miff enregistre un identifiant technique de son téléphone » : un drapeau par
/// compte. Le jeton n'est enregistré qu'avec lui (critère J1 du ticket). Posé SEULEMENT par un écran qui porte cette phrase.
protocol PushConsentStore {
    func isGiven(uid: String) -> Bool
    func give(uid: String)
    func forget(uid: String)
}

/// L'interrupteur « Quand un proche donne des nouvelles » (Réglages > Notifications), un par compte. Actif par défaut (ticket §6) ; sans effet
/// tant que l'autorisation du téléphone et l'accord ci-dessus ne sont pas là.
protocol CircleNewsSwitchStore {
    func isOn(uid: String) -> Bool
    func set(_ value: Bool, uid: String)
    func forget(uid: String)
}

/// L'autorisation des notifications telle que le système la dit MAINTENANT (accordée, y compris provisoire).
protocol NotificationAuthorizationProbe {
    func isAuthorized() async -> Bool
}
