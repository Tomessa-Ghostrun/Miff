import Foundation

// L'offre périodique (ticket docs/tickets/paywall-periodique.md, PAY-PER-I ; décisions du PM des 2026-11-25 et 2026-11-26) : une personne SANS Miff+ voit l'écran d'offre
// existant une fois toutes les 10 ouvertures de l'app, après son premier appui de la session, une seule fois par session. LOGIQUE PURE : aucune classe d'UIKit ni de SwiftUI
// ici, pour que chaque règle se teste sans écran. Miroir de `PeriodicPaywallRules.kt` d'Android (mêmes constantes, mêmes noms, mêmes cas).
//
// CONFIDENTIALITÉ : la seule chose gardée sur le téléphone est UN entier (les ouvertures comptées depuis la dernière apparition, voir `PeriodicPaywallCounterStore`). Aucune
// date, aucun identifiant, aucun nom, aucune position ; jamais envoyé, jamais journalisé, aucun événement Analytics ne le porte. AUCUN DRAPEAU DE BUILD (décision du PM, P1) : la
// règle est active dans tous les builds ; le seul moyen de l'éteindre est `PeriodicPaywallDefaults.openingsBetween = 0` (une ligne, testée).

/// LES constantes de l'offre périodique, en un seul endroit (les 3 secondes de la croix sont `PaywallTiming.closeDelay`). En changer une = une PR d'une ligne.
enum PeriodicPaywallDefaults {
    /// Ouvertures de l'app entre deux apparitions de l'offre. 0 (ou moins) éteint toute la règle, comptage compris. Décision du PM du 2026-11-26 : 10 (15 avant).
    static let openingsBetween = 10

    /// Une « ouverture » est un démarrage à froid, ou un retour au premier plan après au moins cette durée en arrière-plan (même seuil que « Depuis ta dernière visite »).
    static let backgroundMillis = 30 * 60_000

    /// L'offre ne vient que si la personne n'a pas touché l'écran depuis cette durée (même valeur que le temps de pose de la fenêtre d'avis).
    static let quietAfterTouchMillis = 1_500

    /// PROPRE À iOS : tant qu'une présentation du système (feuille, boîte de dialogue, photo plein écran, partage) recouvre les onglets, l'app ne le sait que par un regard : il est
    /// repris à cet intervalle, et l'offre attend `quietAfterTouchMillis` de plus après la fermeture (donc au moins 1,5 s après, au plus 2 s).
    static let modalPollMillis = 500

    /// PROPRE À iOS : un menu du système (celui de « Répartition de ton temps ») ne dit pas qu'il est ouvert. Un appui sur lui est tenu pour « menu ouvert » jusqu'au prochain
    /// toucher, ou au bout de cette durée.
    static let menuHoldMillis = 8_000

    /// PROPRE À iOS : un doigt « posé » dont le système n'a plus rien dit depuis cette durée (ni mouvement ni relevé) est tenu pour relevé : un toucher perdu ne bloque jamais l'offre.
    static let fingerStaleMillis = 10_000

    /// Une session démarrée dans cette durée autour de l'appui sur la notification d'une alerte est celle que cet appui a ouverte (voir `excludeSession`).
    static let excludeWindowMillis = 10_000
}

/// Le seul entier gardé sur le téléphone : les ouvertures comptées depuis la dernière apparition de l'offre (fichier privé, hors de toute sauvegarde).
protocol PeriodicPaywallCounterStore: AnyObject {
    func read() -> Int

    func write(_ count: Int)

    /// L'oublie (déconnexion, suppression du compte : la personne suivante repart de zéro).
    func clear()
}

/// Dit par chaque surcouche qui recouvre les onglets sans être un écran de la navigation (feuille, boîte de dialogue, menu, photo plein écran, fenêtre d'avis) : tant qu'une est
/// ouverte, l'offre périodique ne vient jamais (ticket 4.1 « aucune feuille », PP9 et PP12). Équivalent d'`OverlayPresence` d'Android ; les écrans l'atteignent par
/// `.overlayPresent()` (`PeriodicPaywallEnvironment.swift`).
protocol OverlayPresence: AnyObject {
    /// Une surcouche est apparue.
    func onOverlayOpened()

    /// Une surcouche est partie. Compte comme un toucher : l'offre attend un moment calme APRÈS elle.
    func onOverlayClosed()
}

/// Ce que l'écran sait au moment de la décision. Tout est un état, rien n'est personnel.
struct PeriodicPaywallSituation: Equatable {
    /// La personne a Miff+ (essai, grâce et accord à la main compris) : vrai ; pas de Miff+ : faux ; pas connu (pas encore lu, ou simple estimation du cache) : nil.
    var miffPlus: Bool?
    /// Les onglets sont l'écran devant (rien d'empilé dessus, l'app est au premier plan).
    var tabsInFront: Bool
    /// Le viseur de l'Update est l'écran en usage (photo, caméra, aperçu).
    var updateScreenOpen: Bool
    var keyboardOpen: Bool
    /// Un message de l'app est à l'écran ou attend (accès perdu, accord, « Localiser », « Pas de connexion »...). Les fenêtres qui sont des feuilles, des boîtes de dialogue ou des
    /// menus (la fenêtre d'avis aussi) le disent elles-mêmes : voir `OverlayPresence`.
    var messageOpen: Bool
    /// MON alerte de danger est en route ou active, quelle que soit la Miff.
    var ownAlertInProgress: Bool
    /// Une alerte d'un proche est sur le Feed, ou sa ligne attend, non lue, dans le centre de notifications.
    var lovedOneAlertUnseen: Bool
    /// Faux seulement quand l'achat est allumé dans ce build et que le téléphone est hors connexion (l'offre n'aurait aucun prix) : rien n'est montré par surprise.
    var offerReachable = true
    /// PROPRE À iOS : une présentation du système recouvre les onglets (voir `PeriodicPaywallDefaults.modalPollMillis`). Rempli par l'observateur, pas par l'écran.
    var modalPresented = false

    /// Une des situations où l'offre ne vient jamais (`evaluate` laisse le compteur tranquille dans toutes).
    var forbidden: Bool {
        !tabsInFront || updateScreenOpen || keyboardOpen || messageOpen || ownAlertInProgress || lovedOneAlertUnseen
            || !offerReachable
    }
}

/// Ce que la règle décide à un instant.
enum PeriodicPaywallDecision: Equatable {
    /// Ouvre l'offre maintenant.
    case show
    /// Pas maintenant, et rien à attendre (le prochain changement de situation, de toucher ou de session redemandera).
    case no
    /// Tout est bon sauf un toucher (ou une surcouche fermée) tout récent : redemande dans `millis`.
    case wait(Int)
}

/// La machine à états de l'offre périodique. Une seule par processus (`PeriodicPaywallServices`). Sûre entre fils (le cycle de vie, les touchers et les écrans appellent du fil
/// principal, les effaceurs de n'importe où).
///
/// Vocabulaire (ticket §4) : une OUVERTURE est un démarrage à froid ou un retour après `backgroundMillis` ou plus ; elle démarre une SESSION et est COMPTÉE la première fois que
/// l'app connectée (la coquille, l'onboarding fini) est à l'écran dans cette session. Une session montre l'offre au plus une fois.
final class PeriodicPaywallRules: OverlayPresence, @unchecked Sendable {
    private let store: PeriodicPaywallCounterStore
    private let clock: () -> Int
    private let openingsBetween: Int
    private let backgroundMillis: Int
    private let quietMillis: Int

    private let lock = NSLock()
    private var started = false
    private var backgroundedAt: Int?
    private var openingPending = false
    private var shellVisible = false
    private var tapped = false
    private var shownThisSession = false
    private var excluded = false
    private var excludeRequestedAt: Int?
    private var lastTouchAt: Int?
    private var fingerDown = false
    private var openOverlays = 0
    private var lastModalSeenAt: Int?
    private var menuMayBeOpenUntil: Int?

    /// Appelé (hors du verrou) chaque fois qu'une chose qui peut changer la décision arrive : un toucher relâché, une session, le premier appui, une surcouche. L'observateur
    /// regarde alors de nouveau.
    var onChange: (() -> Void)?

    /// `clock` : horloge MONOTONE en millisecondes (voir `PeriodicPaywallClock`) ; jamais l'heure murale (critère PP3).
    init(
        store: PeriodicPaywallCounterStore,
        clock: @escaping () -> Int,
        openingsBetween: Int = PeriodicPaywallDefaults.openingsBetween,
        backgroundMillis: Int = PeriodicPaywallDefaults.backgroundMillis,
        quietMillis: Int = PeriodicPaywallDefaults.quietAfterTouchMillis
    ) {
        self.store = store
        self.clock = clock
        self.openingsBetween = openingsBetween
        self.backgroundMillis = backgroundMillis
        self.quietMillis = quietMillis
    }

    /// Vrai quand la règle est allumée (un seuil de 0 ou moins l'éteint : rien n'est compté, rien n'est montré).
    var isOn: Bool {
        openingsBetween > 0
    }

    // MARK: - La vie de l'app

    /// L'app est devenue active (JAMAIS un réveil sans écran : la surveillance des lieux relance l'app en arrière-plan, sans qu'elle devienne active). Sans passage par
    /// l'arrière-plan entre deux appels, rien ne change (le centre de contrôle, une alerte du système, un retour des Réglages ne sont pas des ouvertures).
    func onForeground() {
        guard isOn else {
            return
        }
        locked {
            let now = clock()
            let away = backgroundedAt.map { now - $0 }
            backgroundedAt = nil
            // Jamais passée en arrière-plan avant = démarrage à froid (le processus vient de naître).
            if !started || (away.map { $0 >= backgroundMillis } ?? false) {
                startSession(now)
            } else {
                // Même session (retour de moins de 30 minutes, centre de contrôle, alerte du système) : la personne vient de revenir, l'offre attend elle aussi 1,5 s de calme.
                lastTouchAt = now
                fingerDown = false
            }
            started = true
        }
        changed()
    }

    /// L'app est partie en arrière-plan.
    func onBackground() {
        guard isOn else {
            return
        }
        locked {
            backgroundedAt = clock()
            fingerDown = false
        }
    }

    /// L'app connectée (onboarding fini, consentements donnés) est à l'écran : l'ouverture de cette session est comptée, une fois.
    func onShellShown() {
        guard isOn else {
            return
        }
        locked {
            shellVisible = true
            if openingPending {
                countOpening()
            }
        }
        changed()
    }

    /// L'app connectée a quitté l'écran (déconnexion, ou écran refait) : rien n'est compté ni décidé tant qu'elle ne revient pas. Le premier appui et l'offre déjà montrée de
    /// cette session sont gardés ; la déconnexion les efface par `clear`.
    func onShellGone() {
        guard isOn else {
            return
        }
        locked {
            shellVisible = false
        }
    }

    /// L'app a été ouverte par l'appui sur la notification d'une ALERTE d'un proche : aucune offre dans TOUTE la session, aussi calme soit-elle plus tard (ticket P11). Appelée
    /// avant ou après le début de la session (le système livre la réponse avant ou après l'activation) : une session qui démarre dans les secondes autour de l'appel est celle
    /// qu'il concerne.
    func excludeSession() {
        guard isOn else {
            return
        }
        locked {
            excluded = true
            excludeRequestedAt = clock()
        }
        changed()
    }

    // MARK: - Les touchers

    /// Un toucher sur l'écran (un doigt posé, ou relevé) : l'offre attend un moment calme. `pressed` : un doigt est encore posé. Rien n'est signalé à l'observateur à la pose
    /// (un défilement ferait des dizaines d'événements) : seulement au relâchement.
    func onTouch(pressed: Bool) {
        guard isOn else {
            return
        }
        locked {
            lastTouchAt = clock()
            fingerDown = pressed
            if pressed {
                // Un nouveau toucher ferme tout menu : la personne a choisi ou est sortie du menu.
                menuMayBeOpenUntil = nil
            }
        }
        if !pressed {
            changed()
        }
    }

    /// Le doigt posé bouge : le toucher est toujours vivant (un défilement qui dure ne passe jamais pour un toucher perdu). Rien n'est signalé à l'observateur.
    func onTouchMoved() {
        guard isOn else {
            return
        }
        locked {
            lastTouchAt = clock()
        }
    }

    /// Un APPUI (un doigt posé et relevé au même endroit, sans autre doigt) : le premier de la session rend l'offre possible. Un défilement, un balayage ou deux doigts
    /// n'appellent jamais ceci. Pas compté tant que l'app n'est pas l'app connectée (connexion, onboarding, consentement).
    func onActivation() {
        guard isOn else {
            return
        }
        let counted = locked { () -> Bool in
            if !shellVisible {
                return false
            }
            tapped = true
            return true
        }
        if counted {
            changed()
        }
    }

    /// PROPRE À iOS : l'appui est tombé sur le menu de « Répartition de ton temps », qui ne dit pas qu'il s'ouvre : il est tenu pour ouvert jusqu'au prochain toucher.
    func onMenuAreaTapped() {
        guard isOn else {
            return
        }
        locked {
            menuMayBeOpenUntil = clock() + PeriodicPaywallDefaults.menuHoldMillis
        }
    }

    // MARK: - Les surcouches sur les onglets

    func onOverlayOpened() {
        guard isOn else {
            return
        }
        locked {
            openOverlays += 1
        }
        changed()
    }

    func onOverlayClosed() {
        guard isOn else {
            return
        }
        locked {
            if openOverlays > 0 {
                openOverlays -= 1
            }
            // L'appui qui l'a fermée est tombé dans une autre fenêtre : le moment calme se compte à partir de maintenant.
            lastTouchAt = clock()
            fingerDown = false
        }
        changed()
    }

    // MARK: - L'offre

    /// Décide à un instant. Montre SEULEMENT si TOUT est vrai : la règle est allumée ; l'app connectée est là ; la personne n'a pas Miff+ (connu) ; la session n'est pas exclue et
    /// n'a pas déjà montré l'offre ; elle a appuyé sur quelque chose ; le compteur a atteint le seuil ; la situation le permet ; aucune surcouche n'est ouverte ; et elle n'a pas
    /// touché l'écran depuis `quietAfterTouchMillis`. Une personne qui A Miff+ remet le compteur à zéro ici (la fin d'un abonnement repart de zéro, jamais de « 10 déjà
    /// atteints »). Dans tous les « non », le compteur est laissé tranquille.
    func evaluate(_ situation: PeriodicPaywallSituation) -> PeriodicPaywallDecision {
        guard isOn else {
            return .no
        }
        return locked { decide(situation) }
    }

    /// L'observateur ouvre l'offre : plus rien d'autre ne l'ouvre dans cette session, même avant qu'elle soit à l'écran (aucune double ouverture). Le compteur n'est PAS encore
    /// remis à zéro.
    func onLaunched() {
        guard isOn else {
            return
        }
        locked {
            shownThisSession = true
        }
    }

    /// L'offre est RÉELLEMENT à l'écran, quelle qu'en soit la cause (4e lieu, 2e Miff, zone de danger, écran Abonnement, ou cette règle) : le compteur retombe à zéro à cet
    /// instant, même si la personne ferme l'écran une seconde plus tard ou si l'app est tuée pendant les 3 secondes, et cette session ne montre plus d'offre périodique.
    func onPaywallDisplayed() {
        guard isOn else {
            return
        }
        locked {
            shownThisSession = true
            if store.read() != 0 {
                store.write(0)
            }
        }
    }

    /// Déconnexion ou suppression du compte : le compteur et la session partent (la personne suivante repart de zéro).
    func clear() {
        locked {
            store.clear()
            tapped = false
            shownThisSession = false
        }
        changed()
    }

    // MARK: - Intérieur

    private func decide(_ situation: PeriodicPaywallSituation) -> PeriodicPaywallDecision {
        if !shellVisible {
            return .no
        }
        guard let miffPlus = situation.miffPlus else {
            return .no
        }
        if miffPlus {
            if store.read() != 0 {
                store.write(0)
            }
            return .no
        }
        guard isDue(situation) else {
            return .no
        }
        return timing(situation)
    }

    /// Tout est vrai sauf, peut-être, le moment : la session n'est pas exclue ni déjà servie, un premier appui a eu lieu, le compteur a atteint le seuil, la situation le permet
    /// et aucune surcouche n'est ouverte.
    private func isDue(_ situation: PeriodicPaywallSituation) -> Bool {
        if excluded || shownThisSession || !tapped {
            return false
        }
        return store.read() >= openingsBetween && !situation.forbidden && openOverlays == 0
    }

    /// Le moment calme : ni présentation du système ni menu ouvert, puis `quietMillis` sans toucher.
    private func timing(_ situation: PeriodicPaywallSituation) -> PeriodicPaywallDecision {
        let now = clock()
        if situation.modalPresented {
            lastModalSeenAt = now
            return .wait(PeriodicPaywallDefaults.modalPollMillis)
        }
        if let until = menuMayBeOpenUntil {
            if now < until {
                return .wait(PeriodicPaywallDefaults.modalPollMillis)
            }
            menuMayBeOpenUntil = nil
        }
        let afterModal = quietMillis + PeriodicPaywallDefaults.modalPollMillis
        if let seen = lastModalSeenAt, now - seen < afterModal {
            return .wait(afterModal - (now - seen))
        }
        if fingerIsStillDown(now) {
            return .wait(quietMillis)
        }
        if let last = lastTouchAt, now - last < quietMillis {
            return .wait(quietMillis - (now - last))
        }
        return .show
    }

    /// Un doigt est posé, et le système en a parlé il y a moins de `fingerStaleMillis` (chaque mouvement le renouvelle). Un doigt « posé » dont plus rien n'est venu depuis longtemps
    /// (un toucher perdu par le système) est tenu pour relevé : l'offre n'attend jamais indéfiniment.
    private func fingerIsStillDown(_ now: Int) -> Bool {
        guard fingerDown else {
            return false
        }
        if let last = lastTouchAt, now - last >= PeriodicPaywallDefaults.fingerStaleMillis {
            fingerDown = false
            return false
        }
        return true
    }

    private func startSession(_ now: Int) {
        openingPending = true
        tapped = false
        shownThisSession = false
        lastTouchAt = nil
        fingerDown = false
        lastModalSeenAt = nil
        menuMayBeOpenUntil = nil
        excluded = excludeRequestedAt.map { now - $0 < PeriodicPaywallDefaults.excludeWindowMillis } ?? false
        if shellVisible {
            countOpening()
        }
    }

    private func countOpening() {
        openingPending = false
        // Plafonné au seuil : une offre due reste due, le nombre ne grossit pas sans fin.
        store.write(min(store.read() + 1, openingsBetween))
    }

    private func locked<Result>(_ body: () -> Result) -> Result {
        lock.lock()
        defer { lock.unlock() }
        return body()
    }

    private func changed() {
        onChange?()
    }
}

#if DEBUG || INTERNAL
/// Ce que la carte « Offre périodique (essai) » du diagnostic lit et pose (builds Debug et Internal seulement : le build de publication ne contient pas ces fonctions).
extension PeriodicPaywallRules {
    /// L'état de la session en faits simples, sans rien de personnel.
    struct SessionSnapshot: Equatable {
        let appOpen: Bool
        let firstTapDone: Bool
        let shownThisSession: Bool
        let excluded: Bool
        let overlaysOpen: Int
    }

    /// Le nombre d'ouvertures comptées.
    func counterForDiagnostic() -> Int {
        locked { store.read() }
    }

    /// Pose le compteur (borné entre 0 et le seuil) pour essayer la règle sans ouvrir l'app des dizaines de fois.
    func setCounterForDiagnostic(_ count: Int) {
        locked {
            store.write(min(max(count, 0), max(openingsBetween, 0)))
        }
        changed()
    }

    /// Le seuil utilisé.
    var thresholdForDiagnostic: Int {
        openingsBetween
    }

    func sessionForDiagnostic() -> SessionSnapshot {
        locked {
            SessionSnapshot(
                appOpen: shellVisible,
                firstTapDone: tapped,
                shownThisSession: shownThisSession,
                excluded: excluded,
                overlaysOpen: openOverlays
            )
        }
    }
}
#endif
