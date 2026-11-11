# Optimiser la batterie de Miff sans perdre en pertinence ni en précision (proposition BATT)

**Pour qui** : le PM (décisions et protocole de mesure), puis `android-dev` et `ios-dev` (tickets de la section 6). **Rien n'est modifié dans le code par ce document, rien n'est mesuré non plus.** C'est une analyse du code et de la documentation officielle, rédigée le 2026-11-10 à la demande du PM (« optimiser la consommation de batterie de Miff sans perdre en pertinence ni précision ; propose des optimisations »). Aucun texte ici n'est relu par un juriste (risque assumé par le PM, voir `CLAUDE.md`).

**Mise à jour du 2026-11-10** : par décision du PM, **BATT-2 et BATT-4 sont implémentés côté Android** (section 9, PR brouillon `android/batterie-gains-surs`). Le reste du document est inchangé et reste une analyse : rien n'y est mesuré.

**État du code lu** : l'analyse (sections 1 à 8) a été rédigée le 2026-11-10 sur `origin/main` au commit `f612768c`. Depuis, les notifications locales (NOTIF-P0, PR A, B et C) ont été fusionnées dans `main` ; ce document n'a pas relu leur code final, ses passages sur les notifications restent ceux de l'analyse d'origine. Le code de BATT-2 et BATT-4 (section 9) a été mis à jour sur `main` du 2026-11-11 (fusion simple, aucun conflit de code).

**Règle de lecture des chiffres** : aucun gain de batterie n'est chiffré ici. Il n'existe pas de mesure. Chaque affirmation porte une étiquette : **[Doc]** (page officielle, lien en section 7), **[Code]** (lu dans le dépôt), **[Hypothèse]** (raisonnement, à mesurer). Les estimations de volume (écritures par jour) viennent de `docs/dev/surveillance-quotas.md` et sont des estimations, pas des mesures.

---

## 0. En bref

1. **Le socle est déjà frugal** : aucun service de premier plan, aucune mise à jour continue de position, géofencing du système (Android) et surveillance de régions + changement significatif (iOS), écouteurs Firestore et minuteur du Feed attachés **seulement** quand le Feed est à l'écran, écritures plafonnées (15 min, 250 m, 2 min, disjoncteurs 60 / 150 / 40 par jour), alarmes de notification **inexactes**, photos de 150 Ko au plus, caméra libérée dès qu'on quitte le viseur. Il y a peu de « gros » gains faciles ; beaucoup de pistes sont des micro-gains ou des arbitrages avec la fiabilité.
2. **Trois gains sûrs identifiés dans le code** (section 3.1) : Android relance une passe complète quand le système annonce une « entrée » dans une zone où l'on est déjà (iOS s'arrête avant) ; iOS redemande une position à chaque ouverture et sortie même rapprochées ; Android relit tout le serveur à chaque retour du réseau tant qu'un statut attend.
3. **Les deux plus gros inconnus sont à mesurer avant de toucher quoi que ce soit** : (a) Android garde **en permanence** une demande de déplacement (250 m / 5 min) et un **écouteur de session** Firestore non lié à l'écran ; (b) le **nombre de réveils** du processus (chacun redémarre la pile Firebase) par jour.
4. **Ce que le PM peut mesurer lui-même, gratuitement, sans Mac** : Réglages > Batterie sur les deux téléphones, le journal de détection (Android debug, iOS build Internal via l'écran de diagnostic), `adb shell dumpsys batterystats` sur l'OPPO, la console Firebase. Protocole de 48 h en section 5.
5. **Aucune optimisation proposée ne touche** à un contrôle de sécurité (session unique SESS-2, consentement v3, disjoncteurs, App Check) : la règle « la sécurité n'est jamais négociable » de `CLAUDE.md` est respectée (section 4).

---

## 1. Ce que dit la documentation officielle (vérifié le 2026-11-10)

| # | Fait | Source | Conséquence pour Miff |
|---|---|---|---|
| D1 | Android : **100 géofences par app** et par utilisateur de l'appareil. Rayon minimal conseillé **100 à 150 m** : « helps reduce device power consumption » et tient compte de la précision du Wi-Fi (20 à 50 m avec Wi-Fi ; supposer environ 50 m sans position intérieure). Un grand rayon ne réduit pas directement la consommation mais espace les vérifications | Android, « Create and monitor geofences » | Miff plafonne à **20** (décision Q9) ; son curseur de rayon commence à **50 m** (section 3, O9) |
| D2 | Android : le service de géofencing utilise surtout la **position réseau** (Wi-Fi + antennes) ; Wi-Fi éteint = précision et alertes dégradées. Latence habituelle **moins de 2 min**, **2 à 3 min** avec les limites d'arrière-plan, **jusqu'à 6 min** si l'appareil est immobile depuis longtemps | idem | Une arrivée n'est jamais instantanée, quel que soit notre code : la marge d'optimisation de la latence est faible |
| D3 | Android : `setNotificationResponsiveness` : valeur plus haute = moins de consommation, plus de latence ; défaut **0** ; « best effort » | Google, `Geofence.Builder` | O7 |
| D4 | Android : `INITIAL_TRIGGER_ENTER` déclenche **ENTER si l'appareil est déjà dans la zone** (à l'ajout) ; `DWELL` + délai de séjour réduit les « alertes parasites » (passages en voiture) | Android, géofencing | O1 (le comportement exact quand on **remplace** une zone de même identifiant n'est pas documenté : **[Hypothèse]**) |
| D5 | Android : les géofences **survivent** à la mise à jour ou au redémarrage de Google Play services ; elles sont **perdues** au redémarrage du téléphone, à la réinstallation, à l'effacement des données de l'app ou de Play services, et après une alerte `GEOFENCE_NOT_AVAILABLE` : il faut les réenregistrer | idem | `RECEIVE_BOOT_COMPLETED` et le réarmement sont **indispensables** (E5) |
| D6 | Android 8+ : en arrière-plan, le fournisseur fusionné ne calcule une position pour l'app que **quelques fois par heure** ; le géofencing répond « toutes les quelques minutes » en moyenne ; Google recommande le géofencing pour ces cas | Android, « Background location limits » | La demande de déplacement d'Android (O5) est **bornée par le système** en arrière-plan : son coût réel est à mesurer, pas à supposer |
| D7 | Android : priorités : `BALANCED_POWER_ACCURACY` ≈ **100 m** (Wi-Fi + antennes, « likely to consume less power ») ; `HIGH_ACCURACY` → GPS probable ; `LOW_POWER` ≈ **10 km** ; `PASSIVE` ne déclenche rien | Android, « Change location settings » | Miff utilise déjà BALANCED pour le suivi et HIGH **seulement** pour la photo (E2) |
| D8 | Android Doze : réseau suspendu, alarmes standard reportées ; `setAndAllowWhileIdle` **au plus une fois toutes les 9 min par app** ; WorkManager ne tourne pas en Doze. La page ne dit **rien** du géofencing en Doze (**à constater sur l'OPPO**, recette `loc-2b` partie I). Les apps de **sécurité des proches** sont citées comme cas **acceptable** de demande d'exemption, mais la politique Play interdit la demande directe sauf fonction cœur dégradée | Android, « Doze and App Standby » | Alarmes de notification déjà conformes (section 2.1) ; E9 |
| D9 | iOS : **20 régions** au maximum ; une traversée n'est signalée qu'après avoir franchi la frontière, s'être éloigné d'une **distance minimale** et y être resté **au moins 20 s** ; « pour les tests, supposer environ **200 m** » ; **Wi-Fi éteint = surveillance nettement moins précise** ; l'app terminée est relancée en arrière-plan pour environ **10 s** ; **pas de relance si l'actualisation en arrière-plan est coupée** (globalement ou pour l'app) ; après redémarrage, surveillance seulement après le premier déverrouillage. (Source : guide d'archive de 2016 et page actuelle ; l'archive peut être dépassée) | Apple, « Region monitoring » (archive) et « Monitoring the user's proximity… » | Sur iPhone, un rayon de 50 m **ne rend pas** la détection plus fine : la marge du système domine (O9) |
| D10 | iOS : changement de position significatif : notification dès **500 m** de déplacement, jamais plus d'**une fois toutes les 5 min** ; relance l'app terminée | Apple, `startMonitoringSignificantLocationChanges` | Équivalent iOS de la demande de déplacement Android, conçu pour être sobre |
| D11 | App Check (Play Integrity) : durée de vie du jeton **1 h** par défaut, réglable de **30 min à 7 jours** ; « shorter TTLs … deplete your quota faster » ; le SDK rafraîchit vers la moitié de la durée | Firebase, App Check Android | E7 : réglage de **sécurité**, pas de batterie |
| D12 | Outils de mesure : **Battery Historian n'est plus maintenu** (Google conseille le traçage système, la métrique d'énergie de Macrobenchmark ou le Power Profiler) ; `dumpsys batterystats` reste fourni par Android | Android, « Batterystats and Battery Historian » | Section 5 : on s'appuie sur `dumpsys batterystats` en texte |
| D13 | MetricKit expose des durées d'usage de la localisation par précision et le temps d'arrière-plan avec localisation, livrés **une fois par jour** ; l'Organizer de Xcode (Mac) affiche un sous-ensemble ; **non confirmé** : que les builds TestFlight y alimentent l'énergie | Apple, MetricKit (via recherche) | O15 ; l'Organizer est **inutilisable sans Mac** |
| D14 | Firestore ferme ses flux après un court délai d'inactivité (60 s cité par un ingénieur Google sur un forum) : **non officiel, non vérifié** pour le SDK Android | forum Firebase | Ne pas fonder une décision dessus |

---

## 2. Inventaire des consommateurs de batterie

Légende du « poids probable » : **N** négligeable, **F** faible, **M** moyen, **E** élevé, **?** inconnu. C'est un **classement de plausibilité, pas une mesure** ; la colonne « Confiance » dit d'où vient l'affirmation. Volumes : « 6 à 10 événements de zone par jour », « 20 à 60 positions et 6 à 12 écritures de batterie par Miff et par jour » sont les estimations de `docs/dev/surveillance-quotas.md` (§3 et §5 bis).

### 2.1 Android (OPPO Find X5 Lite, Android 13)

| # | Consommateur | Déclencheur et fréquence | Détails (code) | Poids | Confiance |
|---|---|---|---|---|---|
| A1 | **Géofencing**, jusqu'à 20 zones (`PlayGeofenceArming`) | Permanent, géré par Google Play services ; chaque entrée/sortie réveille `TrackingReceiver` | ENTER + EXIT, sans DWELL, sans `setNotificationResponsiveness` (défaut 0), expiration jamais, déclencheur initial ENTER | F à M | Doc D1-D2 / Code ; coût réel **?** |
| A2 | **Demande de déplacement** (`FusedMovementUpdates`) | **Permanente** (enregistrée tant qu'on partage) ; `PRIORITY_BALANCED_POWER_ACCURACY`, intervalle 5 min, plus rapide 60 s, **distance 250 m** ; livrée par `PendingIntent` (pas de service) ; ré-enregistrée à **chaque ouverture** | Chaque position livrée réveille le récepteur (`Reason.MOVED`, position fournie : aucune lecture en plus) | M ? (borné par D6) | Code ; **à mesurer** |
| A3 | **Lectures de position uniques** (`FusedPositionSource`) | Ouverture, passage en arrière-plan, entrée dans une zone, redémarrage ; `getCurrentLocation` BALANCED, **âge max accepté 2 min**, attente **3 s** ; repli sur `lastLocation` si récent | Une position de plus de 10 min n'est jamais utilisée | F (chacune) ; F à M cumulé | Code ; fréquence = 2 × nombre d'ouvertures + entrées |
| A4 | **Réveils du récepteur** (`TrackingReceiver`) | Zone (6 à 10 par jour), déplacement 250 m, retour du réseau si un statut attend, redémarrage / mise à jour de l'app | Si le processus est mort : `MiffApplication.onCreate` complet (App Check, conteneur, `startWarmUp` : Splash + session + Analytics + réseau) puis purge du cache, puis le moteur ; budget 8,5 s | M ? | Code ; **le nombre de réveils à froid par jour est inconnu** |
| A5 | **Écritures Firestore automatiques** (radio) | Statut (6 à 10 / jour), position (≤ 1 par 15 min, ou ≥ 250 m, plancher 2 min), batterie (ouverture / sortie : ≥ 15 min, ≥ 5 points, ou > 3 h) ; **un commit par Miff** ; lecture de session ≤ 1 par passage (si dernière vérification > 10 min) | Disjoncteurs 60 + 150 + 40 par jour | M ? | Code + estimation volumes ; **le temps radio est la vraie inconnue** |
| A6 | **Relectures serveur du suivi** (`syncMiffs`) | Au plus 1 par heure à un réveil ; **toujours** à : redémarrage, changement de partage, **retour du réseau** | Index des Miffs + consentement + lieux, `Source.SERVER` : de l'ordre de 9 lectures pour 1 Miff et 3 lieux | F | Code + `surveillance-quotas.md` §3 |
| A7 | **Écouteur de session** (SESS-2, `DeviceSession.watch`) | **Permanent tant qu'on est connecté et que le processus vit** ; **non lié à l'écran** (relancé à chaque retour du réseau, 3 reprises espacées 15 / 30 / 60 s) | Un `addSnapshotListener` sur le document de session | ? (M si le processus reste en cache des heures) | Code ; **à mesurer** (O6) |
| A8 | **Écouteurs du Feed** (Miff, membres, lieux, statuts) | Seulement **Feed à l'écran** (`repeatOnLifecycle(STARTED)`) ; reprise 10 / 30 / 90 / 300 s puis arrêt | Détachés en arrière-plan et hors de l'onglet Feed | F (écran allumé) | Code |
| A9 | **Minuteur du Feed** (1 / min, aligné sur la minute) | Seulement Feed à l'écran, dans le même `repeatOnLifecycle(STARTED)` | Aucune lecture serveur ; la couleur se recalcule avec l'horloge | N | Code |
| A10 | **Tirer pour actualiser** | Geste de la personne ; 1 lecture forcée par 15 s au plus ; délai de 8 s | ~20 lectures par geste (`surveillance-quotas.md`) | N (ponctuel) | Code |
| A11 | **Alarmes de notification** (NOTIF-P0, non fusionné) | `setAndAllowWhileIdle`, `RTC_WAKEUP`, **inexactes**, **une par type, 4 au plus en attente** ; plafond de 2 par type et 6 au total par jour (N6 : 3) ; N3 / N4 : **une lecture serveur** des statuts (de l'ordre de N + 3 lectures, 7 s au plus, 2 essais par épisode) avant l'affichage | Aucune alarme exacte, aucune permission d'alarme exacte, aucun service | F | Code (branche) + Doc D8 |
| A12 | **Récepteur de redémarrage** (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, + `TIME_SET` / `TIMEZONE_CHANGED` pour les alarmes) | Rare | Réarme zones, déplacement et alarmes | N | Code |
| A13 | **Rappel de réseau** (`ConnectivityNetworkWake`) | **Seulement** tant qu'un statut attend d'être écrit | Réveille le récepteur à chaque réseau validé | F (N si rien n'attend) | Code |
| A14 | **Caméra de l'Update** (CameraX) | Seulement viseur à l'écran ; libérée au départ du viseur (`unbindAll`) ; aperçu 4:3 en mode `COMPATIBLE` (TextureView) ; capture ≈ 2 Mpx en mémoire, encodage JPEG 1024 px au plus, **≤ 150 Ko** | Position de la photo : **`HIGH_ACCURACY`, 1 mesure, 5 s**, seulement au toucher de « Publier » (réutilise une mesure de moins de 10 min) | E pendant l'usage (secondes à minutes), N sinon | Code |
| A15 | **Animation de la bulle** du Feed | Écran allumé, Feed visible : `rememberInfiniteTransition` (deux cycles) ; coupée si « animations réduites » ou écran non démarré | Se redessine à chaque image de l'écran (l'écran de l'OPPO peut dépasser 60 Hz : **[Hypothèse]**) | F à M, écran allumé | Code |
| A16 | **Sauvegarde locale** (`SharedPreferences.commit()`, état entier en JSON) | 5 à 6 fois par passage du moteur | Écriture **synchrone** voulue (statut en attente gardé **avant** l'envoi) | N | Code |
| A17 | **Journal de détection** | Build **debug** seulement (absent du build de publication, contrôle CI) | Fichier texte, aucune coordonnée | N en production ; **biaise la mesure** en debug | Code + `android-loc-2b-comportement-reel.md` |

### 2.2 iOS (iPhone 11 Pro, iOS 26)

| # | Consommateur | Déclencheur et fréquence | Détails (code) | Poids | Confiance |
|---|---|---|---|---|---|
| I1 | **Surveillance de régions**, jusqu'à 20 (`SystemLocationManager.watch`) | Permanent, côté système ; relance de l'app terminée (~10 s, `beginBackgroundTask`, budget interne 25 s) | Entrée et sortie ; rayon plafonné à `maximumRegionMonitoringDistance` ; **aucun mode d'arrière-plan « location »** | F (le système fait le travail) | Doc D9 / Code |
| I2 | **Changement de position significatif** | Permanent ; ~500 m, pas plus d'une fois par 5 min ; relance l'app | La position fournie sert de position de l'occasion (`Reason.moved`) | F | Doc D10 / Code |
| I3 | **Position unique** (`requestLocation`, précision 100 m) | Ouverture, passage en arrière-plan, entrée dans une zone, réarmement ; attente 3 s ; repli sur la position système si < 2 min | **Demandée à chaque ouverture et sortie**, même rapprochées (O2) | F (chacune) ; F à M cumulé | Code |
| I4 | **Réveils / relances** | Zone, déplacement significatif, réarmement ; chaque relance = démarrage de l'app (SwiftUI, Firebase, App Check / App Attest) | Travaux **ordonnés**, un à la suite de l'autre | M ? | Code ; **à mesurer** |
| I5 | **Écritures Firestore automatiques** | Mêmes plafonds qu'Android | Une écriture par Miff | M ? | Code + `surveillance-quotas.md` |
| I6 | **Relectures serveur** | Mêmes règles (1 h, changement de partage, retour du réseau, **changement d'accès**) ; plus une relecture forcée à chaque lieu modifié par le créateur (`ChangeNotifyingPlaceRepository`) | 10 à 30 lectures par enregistrement | F | `surveillance-quotas.md` §3 |
| I7 | **Écouteurs Firestore** (Miff, lieux, statuts, session) | Feed : `running = actif et scenePhase ≠ background` ; l'app est **suspendue** par iOS peu après le passage en arrière-plan, donc les flux se ferment d'eux-mêmes | Différence avec Android : pas d'écouteur « fantôme » en arrière-plan | N | Code |
| I8 | **Minuteurs** | Feed : minute alignée (tant que le Feed tourne) ; coque principale : `Timer` d'une minute (horloge de l'invite d'Update) tant que l'app est au premier plan | Deux minuteurs de 60 s en parallèle au premier plan | N | Code |
| I9 | **Notifications locales** (non fusionné) | `UNNotificationRequest` à intervalle : **aucun réveil de l'app** ; réconciliation au premier plan ; N6 inscrite par le moteur au réveil existant | Aucune alarme, aucune lecture serveur de N3 / N4 en arrière-plan | N | Code (branche) |
| I10 | **Caméra** (`AVCaptureSession`, preset `.photo`) | Seulement viseur à l'écran ; arrêtée à `onDisappear` et quand l'app quitte l'écran actif | Preset `.photo` = résolution photo pleine pour l'aperçu | E pendant l'usage, N sinon | Code |
| I11 | **Animation de la bulle** | `TimelineView(.animation(minimumInterval: 1/30))` ; en pause si animations réduites, bulle hors écran ou app en arrière-plan | 30 images par seconde tant que le Feed est visible | F à M, écran allumé | Code |
| I12 | **Journal de détection** | Builds Debug et Internal (TestFlight) ; carte dans l'écran de diagnostic | Aucune coordonnée | N ; biaise un peu la mesure | Code |

### 2.3 Ce qui est déjà optimisé (à ne pas « re-optimiser »)

- **Pas de service de premier plan, pas de notification permanente, pas de mise à jour continue de position, aucun mode d'arrière-plan « location »** (Android : `android-loc-2b-comportement-reel.md` ; iOS : `ios-loc-4b-comportement-reel.md`).
- **Lecture de position jamais faite aux événements de zone** (la position jointe à l'événement n'est pas lue), une position de plus de 10 min jamais utilisée.
- **Écritures plafonnées** (15 min / 250 m / plancher 2 min, trois disjoncteurs par jour, batterie 15 min / 5 points / 3 h) et **une seule écriture par passage et par Miff** quand c'est possible.
- **Reprises espacées** (Feed 10 / 30 / 90 / 300 s puis arrêt ; session 15 / 30 / 60 s ; reprise du retrait de consentement 5 / 15 / 60 / 300 s) : le backoff existe déjà.
- **Écouteurs et minuteur du Feed** liés à l'écran (Android `STARTED`, iOS `scenePhase`).
- **Photos** : 1024 px au plus, **150 Ko** au plus (cible 100 Ko), caméra libérée au départ du viseur : rien à gagner côté taille.
- **Priorité de position** : BALANCED pour le suivi ; HIGH seulement pour la photo localisée (décision PM R-PHOTO-LOC, une mesure, 5 s).
- **Alarmes inexactes**, 4 au plus, sans permission d'alarme exacte (NOTIF-P0).

---

## 3. Optimisations candidates

Barème de coût de développement (estimation de l'auteur, pas un engagement) : **S** moins d'une demi-journée, **M** une demi-journée à deux jours, **L** davantage. Chaque candidate porte : plateforme, gain attendu, risque pour la pertinence / la précision, coût, réversibilité, ce qui se mesure.

### 3.1 Sûres maintenant (le comportement de détection ne change pas)

**O1. Android : une « entrée » dans une zone où l'on est déjà ne relance pas la passe complète** — **BATT-2 : implémenté (2026-11-10)**, voir la section 9.
- Plateforme : Android. iOS fait déjà `if enter && déjà dedans : return` (`doTransition`).
- Constat [Code] : `TrackingEngine.doTransition` relance `doRefresh(ZONE_ENTERED)` pour **toute** entrée, même si la zone est déjà dans `state.inside` (aucun statut n'est alors écrit, mais la passe lit une position, sauvegarde l'état 5 à 6 fois et peut écrire une position). À l'ouverture de l'app, `armZones` ré-enregistre les 20 zones avec `INITIAL_TRIGGER_ENTER` ; si l'on est chez soi, le système peut renvoyer une entrée pour la zone ré-enregistrée (D4 : **[Hypothèse]** pour une zone de même identifiant remplacée, à confirmer par le journal, O-mesure BATT-1).
- Gain : supprime une passe complète par ouverture dans un lieu (cas fréquent : ouvrir l'app à la maison). Taille inconnue, probablement faible par passe mais **répétée à chaque ouverture**.
- Risque pertinence / précision : **nul** si l'on ne saute que le cas « déjà dedans » : un vrai retour dans la zone est précédé d'une sortie qui a retiré la clé de `inside`. La position à l'arrivée réelle est conservée.
- Coût : **S** (une condition + test unitaire). Réversibilité : totale.
- Mesure : nombre de lignes `TRANSITION_ENTER` sans `STATUS_WRITTEN` qui suivent un `ARMED` (journal debug) avant / après.

**O2. iOS : réutiliser la position système récente à l'ouverture et à la sortie**
- Plateforme : iOS. Android est déjà couvert côté Google Play services : `getCurrentLocation` accepte une mesure de moins de 2 min (`setMaxUpdateAgeMillis`).
- Constat [Code] : `LocationTracking.current()` appelle toujours `requestLocation()`. La position déjà connue du système (`manager.location`) n'est utilisée que **en repli**, si elle a moins de 2 min.
- Proposition : pour `opened` et `backgrounded` **uniquement**, accepter d'abord la position système de moins de 2 min (précision correcte) avant d'en demander une autre. **Jamais** pour l'entrée dans une zone ni le réarmement (la personne vient de bouger : une position de 2 min serait fausse).
- Gain : une ouverture suivie d'une sortie en moins de 2 min ne fait qu'une demande. Dépend de l'habitude d'usage (nombre d'ouvertures brèves).
- Risque : la dernière position partagée « Localiser » peut dater de 2 min au lieu de quelques secondes ; la règle de 15 min / 250 m et le plancher de 2 min font déjà que la position n'est écrite qu'au mieux toutes les 2 min. Négligeable.
- Coût : **S**. Réversibilité : totale (constante). Mesure : lignes `positionRead` par jour (journal Internal) avant / après.
- **Statut : implémenté (BATT-3, iOS, décision du PM du 2026-11-10 ; PR « iOS : optimisation batterie sûre (BATT-3) », en brouillon, pas encore recettée sur l'iPhone).** Voir « Statut d'avancement » en section 6.

**O3. Android : borner les relectures complètes au retour du réseau** — **BATT-4 : implémenté (2026-11-10), borne de 5 min seulement** ; la seconde moitié de la proposition (« ne pas relire quand le seul blocage est le disjoncteur du jour ») n'est **pas** faite (hors décision du PM du 2026-11-10). Voir la section 9.
- Constat [Code] : `onNetworkBack` appelle `syncMiffs(…, NETWORK_BACK)` qui est **toujours due** (index + consentement + lieux, `Source.SERVER`). Tant qu'un statut attend (rappel de réseau actif) et que le disjoncteur de statuts est atteint, chaque réseau validé (par exemple un Wi-Fi qui saute) refait ces lectures. iOS a déjà une borne de 5 min pour son réarmement (`rearmMinIntervalMillis`).
- Proposition : pas plus d'une relecture complète par 5 min au retour du réseau (la constante `RECHECK_AFTER_REFUSAL_MILLIS` existe déjà) ; ne pas relire quand le seul blocage est le disjoncteur du jour.
- Gain : faible (cas rare) mais supprime une boucle potentielle radio + lectures. Risque : un statut en attente part au plus 5 min plus tard dans ce cas précis. Coût : **S**. Réversibilité : totale.

**O4. Instrumenter le journal de détection (debug Android, builds Internal iOS) — prérequis de toute mesure**
- Voir ticket BATT-1 : ajouter des types de lignes **sans coordonnée ni nom** (réveil par déplacement, réveil de zone, ouverture, passage en arrière-plan, zones enregistrées, relecture serveur, durée d'un passage, taille de la file en attente). La liste est fermée par conception (`JournalKind`) ; le build de publication reste muet (contrôle CI existant). Pas un gain en soi : sans lui, O1, O5, O6 et O8 ne se jugent pas.

### 3.2 À mesurer d'abord

**O5. Android : la demande de déplacement permanente (250 m / 5 min)**
- Constat [Code, Doc D6] : c'est la **seule demande de position permanente** d'Android ; en arrière-plan, le système ne calcule pour l'app qu'« environ quelques fois par heure ». Elle sert à : écrire la dernière position de « Localiser » quand on bouge (≥ 250 m), choisir les 20 zones les plus proches (seulement utile au-delà de 20 lieux). iOS fait l'équivalent avec le changement significatif (500 m, ≥ 5 min, D10), conçu pour être sobre.
- Variantes : (a) distance **500 m** (harmonisée avec iOS) ; (b) regroupement des livraisons (`setMaxUpdateDelayMillis`) ; (c) ne pas ré-enregistrer à chaque ouverture (voir O8).
- Gain attendu : **inconnu**, potentiellement le plus grand poste Android en arrière-plan (**[Hypothèse]**). Si la mesure montre un coût négligeable, on ne touche à rien.
- Risque pertinence : la position partagée devient moins fraîche entre deux ouvertures (un déplacement de 250 à 500 m n'est plus signalé). Les arrivées / départs de **zones** ne dépendent pas de cette demande (géofencing). **Décision PM** sur la fraîcheur de « Localiser » (question Q2).
- Coût : **S** (constantes), **M** avec regroupement. Réversibilité : totale.
- Mesure : `dumpsys batterystats` (réveils, radio, « location » par UID) et nombre de lignes `MOVED` du journal, version A contre B (section 5).

**O6. Android : l'écouteur de session SESS-2 en arrière-plan prolongé**
- Constat [Code] : `DeviceSession.watch` garde un écouteur Firestore sur le document de session tant que la personne est connectée et que le processus existe, **sans lien avec l'écran** (le Feed, lui, détache tout). Android garde les processus en cache des heures. En Doze le réseau est suspendu (D8) : le flux se coupe et se rétablit aux fenêtres de maintenance (**[Hypothèse]** d'un surcoût radio). iOS n'a pas ce problème : l'app est suspendue.
- Proposition à étudier : détacher l'écouteur après quelques minutes en arrière-plan et vérifier la session à l'ouverture (déjà le cas : `recheck = true` à `OPENED`). **La sécurité ne baisse pas** : toute écriture automatique revérifie déjà la session (`WriteGate`, SESS-2) ; seule la **détection du remplacement par l'ancien téléphone** serait différée jusqu'à son prochain réveil ou ouverture.
- Risque : l'ancien téléphone, resté en arrière-plan, se déconnecte plus tard (à l'ouverture suivante au lieu de « en direct »). Compatible avec la limite déjà validée par le PM (« un téléphone hors ligne ne peut pas être déconnecté »). **Décision PM** (Q5).
- Coût : **M**. Réversibilité : bonne. Mesure : nuit de 8 h immobile, Wi-Fi, A contre B : octets et temps radio de l'UID Miff (`batterystats`) ; c'est le test le plus parlant.

**O7. Android : `setNotificationResponsiveness` des zones**
- Constat [Doc D3, D2] : défaut 0 ; une valeur plus haute économise de l'énergie au prix de la latence. En arrière-plan, Google documente déjà **2 à 3 min** de latence moyenne (jusqu'à 6 min à l'arrêt), donc une valeur de l'ordre de 2 à 3 min ne dégraderait pas ce que l'on obtient de toute façon en arrière-plan, mais dégraderait le cas « en mouvement » ou « app ouverte ».
- Gain : **non chiffré par Google**. Risque : retard d'arrivée / de départ jusqu'à la valeur choisie. **Décision PM** sur le retard acceptable (Q7). Coût : **S**. Réversibilité : totale.
- Mesure : latence réelle de 8 transitions scriptées par bras (section 5.4) et la consommation de nuit.

**O8. Android : limiter le réenregistrement complet des zones et de la demande de déplacement à chaque ouverture**
- Constat [Code] : `fullRegistration` est vrai à `OPENED`, `REARM` et `SHARING_CHANGED` (revue B2 : ce que le système a perdu revient). iOS ne réenregistre tout qu'à `rearm` et `accessChanged`.
- Raison d'être : fiabilité sur les surcouches constructeur (OPPO) qui peuvent nettoyer des données de Play services (D5 liste le cas « données de Play services effacées »). C'est un **arbitrage fiabilité contre batterie** : ne pas le toucher sans mesure.
- Proposition conditionnelle : après O1, ne réenregistrer à l'ouverture que si le dernier enregistrement complet date de plus de quelques heures (valeur à fixer par la mesure).
- Risque : zones perdues plus longtemps si le système les oublie. Coût : **S**. Réversibilité : totale.

**O9. Rayon minimal des lieux : 50 m aujourd'hui, 100 m recommandé**
- Constat : le curseur propose **50 à 800 m par pas de 50** (défaut 100) alors que `backend/data-model.md` écrit « l'UI doit proposer ≥ 100 m (les géofences plus petites sont peu fiables) » (écart déjà présent dans le dépôt). Google conseille **100 à 150 m** (D1) ; Apple n'annonce pas de détection plus fine que sa marge d'environ 200 m en test (D9).
- Gain : fiabilité (moins de faux départs et de retards) et consommation, selon Google ; non chiffré. Risque : deux lieux voisins (maison / école d'à côté) se recouvrent ; un petit appartement sera vu comme « dedans » de plus loin.
- Proposition : (a) **compter** dans la console les lieux de 50 à 75 m (aucune nouvelle collecte) ; (b) si peu nombreux, relever le minimum du curseur à 100 m pour les nouveaux lieux et expliquer pourquoi (texte rassurant). Ne pas modifier les lieux existants en silence : le rayon armé et le rayon utilisé pour « dans la zone » doivent rester identiques.
- Coût : **S** à **M** (interface, textes FR / EN, tests). **Décision PM** (Q3).

**O10. Animation de la bulle du Feed (écran allumé)**
- Constat [Code] : iOS : 30 images / seconde en continu tant que le Feed est visible ; Android : transition infinie redessinée à chaque image. Coupée par « Réduire les animations » sur les deux plateformes.
- Mesure **sans code** : comparer 30 min d'écran allumé sur le Feed avec et sans « Réduire les animations / Supprimer les animations » (Réglages d'accessibilité), même luminosité.
- Si l'écart est net : baisser la fréquence (iOS 30 → ~15 images), ou figer la bulle après quelques dizaines de secondes sans toucher l'écran. **Validation design** (la respiration fait partie de l'identité du Feed). Coût : **S**. Réversibilité : totale. **Décision PM / design** (Q4).

**O11. Écritures de batterie (ouverture / sortie) : seuils**
- Déjà bornées (15 min, 5 points, 3 h, 40 par jour). Les « leviers gratuits » sont écrits dans `surveillance-quotas.md` §5 bis : seuil 5 → 10 points, 15 → 30 min, ne rafraîchir qu'à la sortie, plafond 40 → 20.
- Particularité batterie : chaque écriture fait aussi **réveiller le réseau des proches** qui ont le Feed ouvert (lectures de snapshot, `N - 1`). Mais c'est une **décision explicite du PM (2026-11-07)** : ne pas la modifier sans mesure ni accord. Mesure : nombre de lignes `BATTERY_WRITTEN` par jour et par Miff (le signal d'alarme existant est > 15). Coût : **S**.

**O12. Android : regrouper les positions de plusieurs Miffs dans un seul commit**
- Constat [Code, backend] : une position est écrite **une Miff à la fois, seule** ; les règles acceptent un lot de positions seules (SESS-1 : **6 Miffs au plus** par lot avec `deviceId`, tableau des budgets de règles de `backend/data-model.md`, ligne « Écrire sa position dans N Miffs »). Un compte gratuit n'a qu'une Miff : **aucun gain** ; un compte Miff+ avec N Miffs fait N allers-retours.
- Risque : l'isolation (un refus dans une Miff n'arrête pas les autres) serait perdue sans reprise unitaire. Coût : **M**. À ne considérer que si la mesure d'un compte Miff+ à plusieurs Miffs montre un temps radio notable.

**O13. Relecture des lieux (toutes les heures au réveil)**
- Constat [Code] : `PLACES_REREAD_MILLIS` = 1 h ; Android n'a pas de poussée (plan Spark, pas de Cloud Function), donc c'est la seule façon d'apprendre qu'un lieu a été déplacé. L'allonger **dégraderait la pertinence** (zones périmées plus longtemps).
- Piste meilleure : quand le Feed (ou l'écran du lieu) a **déjà reçu** les lieux par son écouteur, mettre à jour la copie locale du suivi avec ces données (zéro lecture en plus) au lieu de relire. Gain : lectures et un aller-retour réseau à l'ouverture. Coût : **M** (cohérence de la copie locale, tests). À faire seulement si la mesure montre que ces relectures pèsent.

**O14. Android : réveil à froid plus léger**
- Constat [Code] : un réveil du récepteur sans interface exécute `startWarmUp` (préparation du Splash, session, Analytics, réseau). Seule la session / l'authentification est utile au moteur. Piste : ne lancer la partie « interface » que depuis `MainActivity`.
- **Risque de régression du démarrage** : `startWarmUp` a été introduit pour la recette PM du 2026-10-31 (« le M reste des secondes »). Ne rien déplacer sans mesurer le temps de démarrage à froid avant / après. Coût : **S** à **M**. Gain : par réveil, faible ; cumulé selon le nombre de réveils à froid (inconnu, d'où BATT-1).

**O15. iOS : MetricKit dans les builds Internal**
- Un abonné `MXMetricManager` recevrait chaque jour les compteurs d'énergie / localisation du système (D13) et les verserait dans la carte de diagnostic (compteurs seulement, aucune coordonnée). Utile **sans Mac**, mais : la livraison est quotidienne, TestFlight non confirmé, API de localisation de MetricKit marquée dépréciée dans la documentation actuelle au profit de nouveaux types (à vérifier à l'implémentation). Coût : **M**. Seulement si les mesures manuelles de la section 5 ne suffisent pas.

**O16. Caméra (écran allumé, brève) : mode d'aperçu et préréglage**
- Android : `PreviewView.ImplementationMode.COMPATIBLE` (TextureView) choisi pour la superposition des commandes ; le mode `PERFORMANCE` (SurfaceView) est annoncé plus efficace par CameraX (**[Hypothèse]**, non revérifié ici). iOS : préréglage `.photo`. Gain probablement minime (quelques secondes par photo, ≤ 20 photos par jour d'après `PublishGuard`) et risque visuel (rognage, superposition). **Priorité basse.**

### 3.3 À éviter (perte de fiabilité, de confiance ou de sécurité pour un gain douteux)

- **E1. Détection de mouvement (Activity Recognition Android, Motion & Fitness iOS)** : ajouterait une **permission** de plus (déclaration Play « Data safety », étiquette de confidentialité Apple, écran de consentement) pour un gain non démontré ; le système fait déjà son propre arbitrage mouvement / immobilité pour le géofencing. Latence « variable selon l'appareil » (Android). Contraire à « faible charge cognitive » et à la sensibilité confiance de `CLAUDE.md`. La page Android consultée ne détaille pas le type de permission : à vérifier avant toute étude.
- **E2. Baisser la précision demandée** (`PRIORITY_LOW_POWER` ≈ 10 km, précision « kilomètre » iOS) : fausserait l'arrivée dans un lieu et « Localiser ». Le suivi est déjà en BALANCED / 100 m.
- **E3. Réduire les 20 zones surveillées** : aucun chiffre officiel de coût par zone ; la plupart des comptes ont moins de 5 lieux (1 lieu gratuit, 1 Miff) ; on retirerait une fonction Miff+. À mesurer seulement comme variante (section 5.4).
- **E4. Ajouter une temporisation / un séjour (`DWELL`) aux zones** : Google le recommande contre les « alertes parasites » (D4), et Apple impose déjà 20 s + distance (D9), mais **le PM a décidé de ne pas temporiser** les statuts (commentaire de `TrackingLimits`). À ne rouvrir qu'avec une décision produit, pas pour la batterie.
- **E5. Retirer `RECEIVE_BOOT_COMPLETED` ou le réarmement** : les zones sont perdues au redémarrage (D5).
- **E6. Toucher au contrôle de session SESS-2 avant les écritures, aux disjoncteurs, au consentement v3, aux règles Firestore** : sécurité non négociable (`CLAUDE.md`).
- **E7. Allonger la durée de vie du jeton App Check** pour économiser des attestations (D11) : c'est un réglage de **sécurité** (fenêtre d'abus d'un jeton volé), à décider avec `backend-data` pour les quotas Play Integrity, **pas pour la batterie**.
- **E8. Notifications N3 / N4 : supprimer la vérification serveur avant affichage, ou passer à des alarmes exactes** : fausses alertes sur Android, permissions `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` écartées par la spec. Les alarmes sont déjà inexactes et peu nombreuses (≤ 4).
- **E9. Demander à la personne l'exemption d'optimisation de batterie (Android)** : augmente la consommation ; la politique Play n'autorise la demande directe que si la fonction cœur est dégradée (D8). L'aide « Si les détections tardent » existe déjà, en lecture seule.
- **E10. Service de premier plan « pour fiabiliser »** : consommation continue, notification permanente, contraire à une décision du PM (`android-loc-2b-comportement-reel.md`).
- **E11. Regrouper les sauvegardes locales de l'Android (`commit()` synchrone)** : gain nul pour le risque de perdre un statut en attente si le processus meurt entre deux écritures.
- **E12. Taille des photos** : déjà ≤ 150 Ko ; rien à gagner.

---

## 4. Garde-fous communs à toute optimisation

- **Aucune** ne modifie les règles Firestore, le consentement v3, la session unique, App Check, les disjoncteurs (`CLAUDE.md` : « sécurité jamais négociable »).
- **Réversibilité** : chaque variante tient dans une **constante** ou une condition isolée, avec les tests de détection existants maintenus verts (`TrackingEngineTest` sur Android, `TrackingEngineTests` sur iOS).
- **Critère d'acceptation « pertinence »** (proposition à valider par le PM) : sur les transitions scriptées de la section 5.4, **0 transition manquée** et **pas de fausse transition en plus** par rapport au bras A ; latence médiane et maximale **pas pires de plus de 2 minutes** que A (valeur proposée, à valider).
- **Ton et confiance** : tout texte d'interface ajouté (O9) reste rassurant, sans alarmisme ; rien ne change la déclaration de localisation (Play, App Store) : aucune permission ajoutée.

---

## 5. Plan de mesure avant / après (protocole de 48 h)

### 5.1 Principe

Comparer **trois états** de l'app sur **les deux téléphones**, avec les mêmes scénarios :

| Bras | Contenu | Pourquoi |
|---|---|---|
| **T (témoin)** | Miff installée mais **déconnectée** (aucune zone, aucun suivi : `stop()` retire tout) | Donne le coût de base du téléphone dans la même routine |
| **A (avant)** | Build de `main` actuel | Référence |
| **B (après)** | Build avec les optimisations retenues (une à la fois si possible) | Effet |
| **A' (répétition)** | Le build A de nouveau, en fin de série | Mesure la **variabilité d'un cycle à l'autre** : un gain n'est retenu que si l'écart A - B dépasse nettement A - A' |

Un bras = **48 h**. Les deux téléphones tournent **en parallèle**. Durée totale : 4 bras × 48 h, soit environ 8 jours (T peut être ramené à 24 h).

**Même type de build entre A et B** (Android : debug ou publication, pas un mélange ; un build debug est plus lent et biaise la valeur absolue, mais pas la comparaison A / B ; iOS : configuration Internal, qui contient le journal). Les **comptages** viennent du journal ; les **mesures d'énergie** de l'écart A / B.

### 5.2 Conditions identiques à chaque bras

- Téléphone chargé à 100 %, débranché à la même heure, mêmes réglages (luminosité fixe, pas de mode économie d'énergie, **Miff « Aucune restriction » de batterie sur l'OPPO** comme dans la partie I de `loc-2b-android-recette-pm.md`, actualisation en arrière-plan **activée** sur l'iPhone, Wi-Fi **activé**).
- Même SIM, mêmes lieux de passage, **aucune autre app installée ou mise à jour** pendant la série.
- Même configuration Miff : 1 Miff, 2 membres (le second compte sur un autre appareil ou vu dans la console), 3 lieux (par exemple maison 100 m, travail 150 m, un lieu de loisir).
- Usage scripté par jour : 8 ouvertures de 30 s sur le Feed (8 h, 10 h, 12 h 30, 14 h, 16 h, 18 h, 20 h, 22 h), 1 tirer-pour-actualiser, 0 photo (une série séparée « photo » est facultative).
- Deux trajets réels par jour (domicile - travail - domicile) franchissant les mêmes zones : choisir **deux jours de semaine** comparables pour chaque bras. Noter l'**heure réelle** de chaque franchissement.

### 5.3 Ce qui est relevé

| Mesure | Android (OPPO) | iPhone 11 Pro (sans Mac) |
|---|---|---|
| Batterie à heures fixes (7 h, 12 h, 18 h, 23 h) | Capture d'écran de Réglages > Batterie | idem |
| Part de Miff dans la consommation | Réglages > Batterie > consommation par app (intitulés ColorOS à noter) | Réglages > Batterie, 24 h puis 10 jours, **temps d'écran / d'arrière-plan** par app (intitulés à noter) |
| Détail système | `adb shell dumpsys batterystats --reset` **au débranchement**, puis `adb shell dumpsys batterystats > batterystats.txt` et `adb bugreport` à la fin (D12, procédure officielle) ; relever pour `com.tomessa.miff` : réveils, temps de radio mobile / Wi-Fi, octets, verrous de réveil, localisation. `adb shell dumpsys alarm` (alarmes par paquet) en complément. Le format varie selon la version et ColorOS : comparer **relativement** | Non disponible sans Mac. Indicateur de localisation et Réglages > Confidentialité > Service de localisation > Miff (dernier usage) |
| Journal de détection (comptages de réveils, de positions, d'écritures, batterie à chaque ligne) | `adb shell run-as com.tomessa.miff cat files/detection-journal.txt` (build debug) | Carte « journal de détection » de l'écran de diagnostic (build Internal TestFlight) |
| Écritures Firestore (radio, proxy) | Console Firebase > Firestore > Utilisation (écritures / lectures du jour), comptes de test seulement | idem |
| **Nuit immobile** (sans usage, téléphone posé, Wi-Fi) | Perte de batterie de 23 h à 7 h dans chaque bras : le test le plus propre du coût « Miff au repos » (zones, demande de déplacement, écouteur de session) | idem |
| **Pertinence** | Pour chaque franchissement scripté : heure réelle notée, heure du statut dans le Feed / `presenceAt` dans la console, transitions manquées ou en trop | idem |

### 5.4 Variantes de la série B (une par bras si possible)

1. `B1` : O1 + O3 (Android), O2 (iOS) : sans effet de détection attendu.
2. `B2` : O5 (500 m) et/ou O7 (réactivité 2 à 3 min) : **arbitrage pertinence**, à n'essayer qu'avec l'accord du PM.
3. `B3` : O6 (écouteur de session détaché en arrière-plan) : Android seulement.
4. `B4` (facultatif) : 20 lieux (compte Miff+ ou jeu de lieux de test) contre 3 lieux, pour répondre à E3 avec un chiffre.

Les variantes passent par des **constantes de compilation** (ticket BATT-5), jamais par un réglage visible de la personne.

### 5.5 Lecture des résultats

- Un gain est **retenu** si : l'écart A - B (consommation de nuit **ou** part de Miff) dépasse l'écart A - A' **et** le critère de pertinence de la section 4 est tenu.
- Un gain **non mesurable** (sous le bruit) n'est pas livré : le code reste comme il est.
- Les résultats sont consignés dans un document `docs/qa/` (BATT-M), avec les intitulés réels des réglages de chaque téléphone.

---

## 6. Tickets de développement résultants (à créer dans Notion après GO du PM)

Process projet : ticket Notion, challenge `product-specs`, validation PM, dev Android puis iOS (une branche et une PR par plateforme), revue, recette. Aucun statut Notion n'est modifié par ce document.

| Ticket | Contenu | Plateforme | Coût | Dépend de |
|---|---|---|---|---|
| **BATT-1** | Instrumenter le journal (debug Android, Internal iOS) : réveils par type (zone, déplacement, réseau, redémarrage), ouverture, arrière-plan, zones enregistrées, relecture serveur, durée d'un passage, file en attente. Sans coordonnée ni nom ; build de publication muet (contrôle CI) | Android + iOS | S-M | rien |
| **BATT-M** | Checklist de recette de mesure (`docs/qa/checklists/batterie-mesure-pm.md`) et modèle de relevé, avec les commandes `adb` | docs | S | BATT-1 |
| **BATT-2** | O1 : entrée dans une zone déjà « dedans » ne relance pas la passe complète ; test unitaire | Android | S | rien (mieux après BATT-1) — **implémenté** (PR brouillon `android/batterie-gains-surs`, section 9) |
| **BATT-3** | O2 : réutiliser la position système de moins de 2 min à l'ouverture / sortie (pas à l'entrée de zone). **Statut : implémenté (iOS, PR en brouillon)** | iOS | S | rien |
| **BATT-4** | O3 : borne de 5 min des relectures au retour du réseau ; pas de relecture si seul le disjoncteur bloque | Android | S | rien — **implémenté pour la borne de 5 min** (même PR, section 9) ; la partie « disjoncteur » n'est pas faite |
| **BATT-5** | Variantes de mesure par constantes de compilation (distance de déplacement, réactivité des zones, délai de détachement de la session, rayon armé minimal en test) | Android (+ iOS pour 20 lieux) | M | BATT-1 |
| **BATT-6** | O6 : détacher l'écouteur de session en arrière-plan prolongé (conditionnel à la mesure) | Android | M | BATT-5, décision PM Q5 |
| **BATT-7** | O5 / O7 : valeurs retenues pour le déplacement et la réactivité (conditionnel) | Android | S | BATT-5, décisions PM Q2, Q7 |
| **BATT-8** | O8 : limiter le réenregistrement complet à l'ouverture (conditionnel) | Android | S | BATT-2, mesure |
| **BATT-9** | O9 : rayon minimal 100 m pour les nouveaux lieux + texte rassurant FR / EN (conditionnel à la décision PM) | Android + iOS | S-M | décision PM Q3, comptage des lieux |
| **BATT-10** | O10 : animation de la bulle (fréquence ou arrêt après inactivité) (conditionnel) | Android + iOS | S | mesure « animations réduites », validation design Q4 |
| **BATT-11** | O13 : alimenter la copie locale des lieux depuis l'écouteur du Feed (conditionnel) | Android + iOS | M | mesure des relectures |
| **BATT-12** | O14 : réveil à froid allégé, sans régresser le démarrage (conditionnel) | Android | S-M | BATT-1, mesure du démarrage |
| **BATT-13** | O15 : MetricKit dans les builds Internal (conditionnel) | iOS | M | mesure manuelle insuffisante |
| **BATT-14** | O11 / O12 : seuils d'écriture de la batterie, regroupement des positions Miff+ (conditionnel, faible priorité) | Android + iOS | S / M | mesure ; décision PM du 2026-11-07 |

### Statut d'avancement

**Décision du PM (2026-11-10) : seuls les trois gains « sûrs » de la section 3.1 sont engagés** (O1 / BATT-2 et O3 / BATT-4 sur Android, O2 / BATT-3 sur iOS). Rien d'autre de ce document n'est décidé : ni les régions (20), ni le changement significatif, ni les disjoncteurs, ni SESS-3, ni App Check, ni les points « à mesurer d'abord ».

**BATT-3 (iOS) : implémenté** (PR « iOS : optimisation batterie sûre (BATT-3) », brouillon ; compilé et testé par la CI macOS seulement, jamais sur l'iPhone à ce jour).

- **Ce qui change** : à l'**ouverture** de l'app et au **passage en arrière-plan**, si le système connaît déjà une position de **moins de 2 minutes** (constante `TrackingLimits.reusableFixMaxAgeMillis`) et assez précise (**100 m ou mieux**, constante `reusableFixMaxAccuracyMeters` : la précision demandée au système reste 100 m), elle est utilisée **sans nouvelle demande** (`requestLocation`). Sinon, comportement d'avant.
- **Ce qui ne change pas** : l'**entrée dans une zone** (et le réarmement, le déplacement significatif, le changement de partage ou d'accès) lit toujours une position fraîche. La **photo de l'Update** garde sa mesure fraîche à « Publier » (les règles exigent une position de moins de 10 minutes). Les régions, le changement significatif, les disjoncteurs, SESS-3 et App Check ne sont pas touchés.
- **Compte** : une position mesurée **avant la dernière déconnexion** (ou arrêt du partage) n'est jamais réutilisée (elle peut être celle d'un autre compte sur le même téléphone).
- **Retour en arrière** : mettre `reusableFixMaxAgeMillis` à `0` redemande une position à chaque fois, comme avant.
- **Effet sur la dernière position partagée (« Localiser »)** : elle peut dater de 2 minutes au lieu de quelques secondes à l'ouverture ou à la sortie. La règle d'écriture (15 min / 250 m, plancher de 2 min) n'en est pas modifiée.
- **Mesure pour le PM (sans Mac)** : (1) **Réglages > Batterie** de l'iPhone sur 48 h, build avant puis après, comme au protocole de la section 5 (un gain n'est retenu que s'il dépasse l'écart entre deux mesures identiques) ; (2) dans le build **Internal** (TestFlight), la carte « journal de détection » de l'écran de diagnostic compte une ligne **`positionReused`** chaque fois qu'une demande de position a été évitée (aucune coordonnée dans le journal). Le nombre de lignes `positionReused` par jour, comparé aux ouvertures et sorties, dit combien de demandes ont été économisées ; une journée sans aucune ligne signifie que le système n'avait pas de position assez récente (ou assez précise) : rien n'est cassé, le gain est alors nul pour cet usage.

---

## 7. Sources (consultées le 2026-11-10)

- Android, géofencing : https://developer.android.com/develop/sensors-and-location/location/geofencing
- Android, limites de localisation en arrière-plan (8.0) : https://developer.android.com/about/versions/oreo/background-location-limits et https://developer.android.com/develop/sensors-and-location/location/background
- Android, Doze et App Standby : https://developer.android.com/training/monitoring-device-state/doze-standby
- Android, priorités de localisation : https://developer.android.com/develop/sensors-and-location/location/change-location-settings
- Google, `Geofence.Builder` (réactivité, séjour, expiration) : https://developers.google.com/android/reference/com/google/android/gms/location/Geofence.Builder
- Android, reconnaissance d'activité (Transition API) : https://developer.android.com/develop/sensors-and-location/location/transitions
- Android, Batterystats et Battery Historian : https://developer.android.com/topic/performance/power/setup-battery-historian
- Apple, surveillance de régions (guide d'archive) : https://developer.apple.com/library/archive/documentation/UserExperience/Conceptual/LocationAwarenessPG/RegionMonitoring/RegionMonitoring.html
- Apple, « Monitoring the user's proximity to geographic regions » : https://developer.apple.com/documentation/corelocation/monitoring-the-user-s-proximity-to-geographic-regions (lu par son flux de données `tutorials/data/…`)
- Apple, `startMonitoringSignificantLocationChanges()` : https://developer.apple.com/documentation/corelocation/cllocationmanager/startmonitoringsignificantlocationchanges()
- Apple, MetricKit (`MXLocationActivityMetric`) : https://developer.apple.com/documentation/metrickit/mxlocationactivitymetric
- Firebase, App Check avec Play Integrity (durée de vie des jetons) : https://firebase.google.com/docs/app-check/android/play-integrity-provider

**Non vérifié** (à ne pas citer comme fait) : délai d'inactivité des flux Firestore (D14) ; effet d'un remplacement d'une zone de même identifiant sur `INITIAL_TRIGGER_ENTER` (D4) ; comportement du géofencing sous Doze et mode économie sur ColorOS (à constater sur l'OPPO) ; disponibilité du Power Profiler d'Android Studio sur l'OPPO ; alimentation de l'Organizer par TestFlight.

---

## 8. Questions pour le PM

1. **Q1. Protocole** : acceptez-vous la série A / T / B / A' de 48 h par bras sur les deux téléphones (environ 8 jours), et qui lance les commandes `adb` (vous, ou on prépare un script à copier) ?
2. **Q2. Fraîcheur de « Localiser » (O5)** : peut-on passer la demande de déplacement d'Android de 250 m à 500 m (comme iOS) si la mesure montre un gain ?
3. **Q3. Rayon minimal (O9)** : relever le minimum du curseur à 100 m pour les nouveaux lieux, après comptage des lieux de 50 à 75 m ?
4. **Q4. Animation de la bulle (O10)** : acceptez-vous une respiration plus lente ou figée après quelques dizaines de secondes d'inactivité, si l'écran allumé pèse sur la batterie ?
5. **Q5. Session unique (O6)** : accepter que l'ancien téléphone se déconnecte à son prochain réveil ou à sa prochaine ouverture plutôt qu'« en direct » s'il est resté longtemps en arrière-plan ?
6. **Q6. Batterie des proches (O11)** : la décision du 2026-11-07 (écrire à l'ouverture et à la sortie) reste-t-elle entière, ou veut-on regarder l'option « à la sortie seulement » après mesure ?
7. **Q7. Retard acceptable des arrivées / départs (O7)** : combien de minutes de plus acceptez-vous (en arrière-plan Google documente déjà 2 à 3 minutes) ?
8. **Q8. Journal** : OK pour ajouter des types de lignes (sans coordonnée ni nom) au journal des builds de test (BATT-1) ?

---

## 9. Gains livrés côté Android : BATT-2 et BATT-4 (décision du PM du 2026-11-10)

**Statut : BATT-2 implémenté, BATT-4 implémenté (borne de 5 min).** Branche `android/batterie-gains-surs`, PR brouillon « Android : optimisations batterie sûres (BATT-2, BATT-4) ». **Aucun gain n'est chiffré : rien n'est encore mesuré** (règle de lecture du début du document). Ne sont **pas** touchés : l'écouteur de session SESS-2, la demande de déplacement de 250 m, les rayons, les disjoncteurs, App Check, les 20 zones. Côté iOS, BATT-3 est une autre PR.

### 9.1 BATT-2 : ce qui change et ce qui ne change pas

- **Avant** : toute « entrée » annoncée par le système relançait `doRefresh(ZONE_ENTERED)` (lecture de position, 5 à 6 sauvegardes locales, écriture possible de la position), même quand la zone était déjà dans `inside`.
- **Après** : si la zone est **déjà** dans `inside` **et** que l'événement ne change le statut d'aucune Miff, `TrackingEngine.doTransition` s'arrête tôt, comme iOS : pas de lecture de position, pas de sauvegarde, pas d'écriture, pas de relecture serveur. Il reste : la ligne `TRANSITION_ENTER` du journal (pour pouvoir compter ces événements), l'envoi d'un statut **déjà en attente** s'il y en a un, et le redémarrage de la demande de déplacement si elle n'avait jamais démarré (ce que la passe complète refaisait).
- **Aucune transition réelle n'est perdue** (vérifié par le code et par `TrackingEngineBatteryGainsTest`) :
  - *Entrée après une sortie* : la sortie retire la clé de `inside`, l'entrée suivante n'est donc **pas** « déjà dedans » : passe complète et statut, comme avant.
  - *Entrée après une sortie manquée* (téléphone éteint) : le statut dit déjà « dans le lieu », il n'y avait rien à écrire avant non plus ; la sortie réelle suivante est écrite.
  - *Redémarrage* : `REARM` reconstruit `inside` depuis une position récente, **sans** écrire de statut quand aucun n'existait encore ; l'entrée du système qui suit **change** alors le statut (`derived` absent) : elle n'est **pas** ignorée, le statut part. Test dédié.
  - *Zone d'une autre catégorie, ou statut différent* : même règle, la passe n'est ignorée que si **aucune** Miff ne change.
  - *Ré-enregistrement des zones à l'ouverture* (`OPENED`, `REARM`, `SHARING_CHANGED`) : **inchangé** (BATT-8 reste conditionnel) ; test : des zones « oubliées » du système reviennent à l'ouverture.
  - *Accès retiré* : l'événement est toujours traité avant (zones retirées, copie effacée).
- **Ce que la passe ignorée faisait en plus** et qui est maintenant fait au réveil suivant (ouverture, déplacement de 250 m, redémarrage, retour du réseau, entrée réelle) : écrire la position partagée « Localiser » à cette occasion (la règle 15 min / 250 m s'applique de toute façon), relire le serveur si la copie a plus d'une heure, réarmer une zone manquante. **Limite connue** : après une sortie manquée, la position de « Localiser » n'est pas rafraîchie à l'entrée qui suit ; elle l'est au premier déplacement de 250 m ou à la prochaine ouverture.

- **Pendant un voyage, rien ne change** : `doTransition` retire les zones et s'arrête **avant** le code de BATT-2 ; la position reste partagée par les déplacements de 250 m. Test dédié.
- **Remise de la position de l'Update (#173)** : elle passe par `refresh(SHARING_CHANGED, fix)`, pas par une entrée de zone ; aucune dépendance à une passe `ZONE_ENTERED`. Test dédié.
- **Interrupteur** : **BATT-2 n'en a pas** (retour arrière = `git revert` du commit de BATT-2).

### 9.2 BATT-4 : ce qui change et ce qui ne change pas

- **Avant** : chaque retour du réseau (réseau validé) tant qu'un statut attend relisait tout le serveur (index des Miffs, consentement, lieux ; de l'ordre de 9 lectures pour 1 Miff et 3 lieux).
- **Après** : `TrackingEngine.onNetworkBack` ne relit **au plus qu'une fois par 5 minutes** (`TrackingLimits.NETWORK_REREAD_MIN_INTERVAL_MILLIS`, 5 min, borne **stricte** : à 4 min 59 s 999 on ne relit pas, à 5 min pile on relit). La référence est la date de la dernière relecture **complète** (`checkedAtMillis`), quelle que soit l'occasion qui l'a faite.
- **Le statut en attente n'est pas retardé** : dans la fenêtre de 5 minutes, la dernière relecture complète est considérée comme à jour, le statut part **tout de suite**, la session SESS-2 est **revérifiée** avant l'écriture (inchangé), le retrait de l'accès « Toujours » est contrôlé avant tout (inchangé). Le consentement utilisé a au plus 5 minutes ; les règles Firestore le revérifient de toute façon à l'écriture.
- **Une relecture incomplète (pas de réponse) ne compte pas** : le retour du réseau suivant réessaie aussitôt. Après un refus du serveur (`recheckSoon`), la relecture voulue n'est pas confondue avec une relecture récente. Une horloge reculée ne bloque jamais la relecture.
- **Interrupteur** : mettre `TrackingLimits.NETWORK_REREAD_MIN_INTERVAL_MILLIS` à 0 désactive la borne (chaque retour du réseau relit tout, comme avant).
- **Pendant un voyage**, un retour du réseau passe par `doTravelPass` (avant le code de BATT-4) : les Miffs sont relus à chaque fois, la borne n'y joue pas. Test dédié.
- **Non fait** : « ne pas relire quand le seul blocage est le disjoncteur du jour » (deuxième moitié de O3), hors du périmètre décidé par le PM. La borne de 5 min limite déjà le pire cas à une relecture par 5 min.

### 9.3 Méthode de mesure avant / après pour le PM (gratuite, sans Mac)

À faire avec **deux builds debug** : A = `main` avant la fusion de cette PR, B = la branche (même type de build, voir section 5.1). Le journal de détection n'existe qu'en **debug** (absent du build de publication). Il est lu par `adb shell run-as com.tomessa.miff cat files/detection-journal.txt` (aucune coordonnée dedans). BATT-1 (nouveaux types de lignes) n'est **pas** fait : on se sert des lignes existantes.

**BATT-2 (entrée « déjà dedans »)** :
1. Être chez soi (un lieu de confiance armé), build A, vider le journal (se déconnecter / reconnecter l'efface) ou noter le nombre de lignes.
2. Ouvrir puis fermer l'app 6 fois, à 3 min d'intervalle (chaque ouverture ré-enregistre les zones ; le système peut renvoyer une entrée).
3. Lire le journal : compter les `TRANSITION_ENTER` **sans** `STATUS_WRITTEN` juste après, et les `POSITION_READ` qui les suivent. **Attendu en A** : un `POSITION_READ` après chacune de ces entrées. **Attendu en B** : la ligne `TRANSITION_ENTER` reste, mais **plus de `POSITION_READ`** après elle. Si aucune entrée ne revient à l'ouverture (hypothèse D4 non confirmée par le système), le gain est nul et le journal le dira : on garde le code, qui est inoffensif.
4. Énergie : nuit de 8 h (section 5.3) avec le scénario ci-dessus répété le soir ; `adb shell dumpsys batterystats --reset` au débranchement, puis `adb shell dumpsys batterystats com.tomessa.miff` le matin (réveils, localisation, radio de `com.tomessa.miff`). Comparer à A' (le build A rejoué) pour connaître le bruit.

**BATT-4 (retour du réseau)** :
1. Créer un statut en attente : couper le Wi-Fi et les données, **sortir** d'un lieu (la sortie attend), rester hors réseau.
2. Remettre le Wi-Fi puis le couper et le remettre **5 fois en 4 minutes** (le statut ne doit pas être écrit entre-temps : pour cela, faire le test avec un compte de test dont le disjoncteur de statuts est atteint, ou en mode avion sauf pour la dernière remise du réseau).
3. Compter : lignes `WAKE_NETWORK` du journal (identiques en A et en B : chaque réveil est conservé) et **lectures Firestore** de la console Firebase (Firestore > Utilisation, compte de test seulement) : **attendu en A** environ 9 lectures par réveil ; **en B** une série de lectures au premier réveil puis aucune pendant 5 minutes.
4. Vérifier que le statut arrive dans le Feed d'un proche **aussi vite** qu'en A (heure du statut = heure réelle de la sortie).

**Critère de pertinence (section 4)** : 0 transition manquée, aucune fausse transition en plus, par rapport à A.
