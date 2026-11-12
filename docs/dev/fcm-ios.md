# Notifications push côté iOS (SRV-2I et SRV-4I) : notes d'exploitation

**Rédigé le 2026-11-12** par `ios-dev`. Code : `ios/Miff/Services/Push/`, `ios/Miff/Models/Push/`, `ios/Miff/App/PushServices.swift`, `ios/Miff/Services/Firebase/FirebasePushTokenSource.swift` et `FirestoreDeviceTokenRegistry.swift`. Ticket : `docs/tickets/notification-update-membres.md` (§6, §8, §9, §12, décision du PM du 2026-11-12). Contrat du registre : `docs/dev/jetons-appareil-clients.md`. Miroir Android : `docs/dev/fcm-android.md`. Étapes du PM côté Apple et Firebase : `docs/dev/apns-etapes-pm.md`. Recette : `docs/qa/checklists/fcm-ios-recette-pm.md`.

**Rien n'est compilé en local (poste Windows) : la CI macOS fait foi, et la livraison réelle ne se teste que sur l'iPhone (TestFlight), après les déploiements du PM.**

## 1. Ce que le téléphone fait

| Sujet | Comportement |
|---|---|
| Message reçu (app fermée ou en arrière-plan) | Une **alerte** affichée **par le système**, sans code de l'app : le serveur envoie les clés de texte `notif_circle_update_title` (avec le prénom) et `notif_circle_update_body`, que le système résout dans la langue du téléphone depuis `Localizable.xcstrings`. Pas d'extension de service en v1. Le jour : son par défaut du téléphone. La nuit (22 h à 7 h, heure du destinataire) : niveau « passif » (ni son, ni écran allumé), décidé par le serveur d'après le fuseau enregistré. |
| Données du message | `type = circle_update`, `miffId`, `authorUid` (rien d'autre : ni photo, ni position, ni nom de Miff, ni prénom dans les données). Tout autre `type` est ignoré. |
| App au premier plan | **Aucune notification du système** (`willPresent` répond « rien »). Le Feed se met déjà à jour tout seul. Si la Miff est bien une Miff du compte connecté, un crochet (`PushForegroundHook.onCircleNews`) est appelé : le centre de notifications (NOTIF-P0, PR #127/#129/#130, pas encore dans `main`) y posera sa ligne silencieuse. |
| Toucher la notification | Le Feed de la Miff concernée s'ouvre (`PushOpenRequests` → `MiffHomeHost.openRequestedFeed`) : onglet Feed, pile vidée, Miff choisie dans le sélecteur. |
| Validation (donnée du réseau) | `CircleNewsParser` : type exact, `miffId` de 1 à 64 caractères `A-Za-z0-9_-`, `authorUid` **obligatoire** (sans auteur l'auto-filtre ne peut pas jouer : on ne le contourne pas) de 1 à 128 caractères de la même forme. Jamais d'URL ni de chemin. Puis `PushOpenRules` : il faut un compte connecté, la Miff doit être une Miff **de ce compte** (annuaire lu auprès du serveur si besoin, au démarrage à froid aussi), et l'auteur ne doit pas être soi-même. **Appartenance inconnue (annuaire pas lu, pas de réponse) : ignoré.** Miff étrangère, personne de connecté : ignoré, le toucher ouvre seulement l'app. |

## 2. Choix techniques (et pourquoi, en une phrase)

| Choix | Pourquoi |
|---|---|
| `FirebaseMessaging` ajouté **sans nouveau paquet** (même version exacte de Firebase, 13.1.1 ; ses dépendances sont déjà dans `Package.resolved`) | Aucune dépendance de plus à surveiller. |
| `FirebaseMessagingAutoInitEnabled = false` (Info.plist) | **Aucun jeton n'existe avant l'accord** de la personne : Firebase ne contacte ni Apple ni Google tant que le registraire n'a pas allumé le service. |
| `FirebaseAppDelegateProxyEnabled = false` (Info.plist) | Firebase ne remplace plus en cachette les méthodes de l'app : le jeton d'APNs est transmis **explicitement** (`MiffAppDelegate` → `Messaging.apnsToken`). Rien n'est branché dans notre dos sur les notifications. SwiftUI n'a pas d'autre porte que `@UIApplicationDelegateAdaptor`. |
| **Pas de `UIBackgroundModes` / `remote-notification`** | L'alerte est affichée par le système sans réveiller l'app, et le serveur n'envoie aucun message silencieux : ce mode ne servirait à rien (guideline 2.5.4 : aucun mode d'arrière-plan inutile). Les contrôles existants (`AppConfigurationTests`, `InfoPlistStaticTests`, `ci/check-binaries.sh`) continuent donc de l'exiger **absent**. À rouvrir seulement si un jour un message silencieux est ajouté. |
| `aps-environment = development` écrit dans `Config/Miff.entitlements` | À la signature, Xcode remplace la valeur par celle du **profil** : un profil de développement donne `development`, le profil de **distribution** (TestFlight, App Store) donne `production`. **Une app TestFlight utilise donc l'environnement PRODUCTION d'APNs** [connu, non revérifié aujourd'hui : à constater sur l'iPhone]. Le SDK Firebase Messaging détecte seul l'environnement du jeton (profil embarqué absent = production). Les builds Debug (simulateur) n'ont aucune entitlement. |
| Une seule clé APNs `.p8` pour développement et production | C'est le comportement des clés d'authentification APNs [connu, à constater en recette]. Elle est déposée par le PM **dans la console Firebase uniquement**. |
| L'accord est enregistré **seulement** par l'écran des notifications (la pop-in de `PermissionGate`) | C'est le seul écran de l'app qui ouvre la fenêtre du système, et il porte la phrase : « un identifiant technique de ton téléphone, fourni par Apple pour envoyer les notifications ». Un test (`PushPrivacyTests`) vérifie qu'aucun autre fichier n'enregistre l'accord. |
| Les comptes qui avaient autorisé les notifications **avant** cette version revoient la pop-in **une fois** | Leur ancienne pop-in disait « Miff n'envoie pas encore de notification » : sans cette nouvelle réponse, aucun jeton n'est enregistré (critère J1). Visible : une pop-in de plus, une seule fois, pour les testeurs actuels. |
| Le jeton n'est jamais gardé sur le téléphone | Seule son **empreinte SHA-256** l'est, dans un fichier privé hors sauvegarde. |

## 3. Le registre des appareils

Document `users/{uid}/devices/{deviceId}` (contrat final du backend) : **7 clés exactement** `token`, `platform = "ios"`, `appVersion` (« 0.1.0 (27) »), `tz` (`TimeZone.current.identifier`), `notify = { updates: Bool }`, `updatedAt`, `createdAt`. `deviceId` = identifiant de la session SESS **confirmée** (`DeviceSession.currentDeviceId`). Seul `FirestoreDeviceTokenRegistry` connaît la forme (construite par `DeviceDocument`, testée à l'octet près). Création en transaction « lire puis créer ou mettre à jour » ; `createdAt` et `platform` jamais réécrits.

**Quand** (`RegistrationRules`, identique à Android) : jamais sans compte connecté + session confirmée + accord + autorisation du système lue **maintenant** ; ensuite seulement si le jeton, l'interrupteur ou (une fois par jour au plus) le fuseau ont changé, ou 30 jours après la dernière écriture. Une écriture ratée attend 10 minutes ; hors connexion rien n'est tenté. Une **nouvelle session** supprime les autres documents du compte, une fois par session, avec ou sans accord (contrat §4.2).

**Quand `sync()` est appelée** : retour au premier plan, changement d'état de la session (connexion, reprise), **réponse à l'écran d'accord (tout de suite)**, changement de l'interrupteur, nouveau jeton fourni par Firebase.

**Interrupteur « Quand un proche donne des nouvelles »** : `CircleNewsSwitch` (actif par défaut, un réglage par compte, écrit dans `notify.updates` dans la seconde). **L'écran Réglages > Notifications n'est pas dans `main`** (PR #127 ouverte) : tant qu'elle n'est pas fusionnée, il n'y a **pas encore de bouton** pour cet interrupteur ; l'arrêt se fait en retirant l'autorisation dans les réglages iOS (le document est alors supprimé). Branchement prévu : la ligne de réglage lit `push.circleNews.isOn` et appelle `push.circleNews.set(_:)`. **Toute surface qui ouvrira la fenêtre du système (carte de seconde chance du Feed, bouton du centre) devra porter la même phrase sur l'identifiant du téléphone et appeler `push.consent.give(uid:)` puis `push.sync()`.**

## 4. Cycle de vie du jeton

| Moment | Ce qui se passe, dans cet ordre |
|---|---|
| Déconnexion, session prise par un autre téléphone, perte de session, **début** d'une suppression de compte | `PushRegistrar.forget()` est le **premier** arrêt de la liste (avant le suivi des lieux, l'annuaire...) : 1. service éteint, **`deleteToken` chez Firebase** (borné à 3 s), 2. `unregisterForRemoteNotifications` (coupe la livraison sur ce téléphone même hors connexion), 3. notifications déjà affichées retirées du tiroir, 4. document `devices` supprimé si le compte est encore joignable et qu'on est en ligne (borné à 4 s), 5. mémoire du téléphone effacée. **Ne passe par aucun verrou** : une écriture en cours ne retient pas la sortie, et elle ne laisse rien en mémoire si elle finit après (compteur de sortie). |
| Suppression de compte | Ce qui précède au tout début (`beginAccountDeletion` arrête tout avant la première écriture) ; la procédure existante supprime ensuite les documents `devices` restants ; l'accord et l'interrupteur de ce compte sont effacés (`PushServices.erasers`). |
| Autorisation retirée dans les réglages iOS | Au retour dans l'app : jeton invalidé **d'abord**, puis document supprimé, service éteint. |
| App tuée au milieu d'une sortie | Au lancement suivant, sans compte et service resté allumé : le service est éteint (`cleanUpWithoutAccount`). |
| Hors connexion | `deleteToken` échoue (borné) ; `unregisterForRemoteNotifications` coupe quand même la livraison locale. Le document peut rester, **inerte** : le serveur le supprime à la première réponse « jeton inconnu » de FCM. |

Limite connue : si la sortie a lieu hors connexion, le jeton reste valide **côté Google** jusqu'à la première réponse « inconnu » ; la livraison est coupée sur le téléphone par le désenregistrement APNs, mais ce point est à constater en recette (étape « déconnexion hors connexion »).

## 5. Vie privée et sécurité

Aucun jeton dans un journal, Analytics, Crashlytics ou message d'erreur (descriptions expurgées ; `PushPrivacyTests` lit les sources) ; `Firebase Messaging` n'est importé que dans **un** fichier ; aucun secret côté client ; clé APNs seulement dans la console Firebase ; HTTPS partout (Firebase). Le prénom n'est jamais dans les données du message (seulement dans l'alerte composée par le serveur). Le manifeste de confidentialité `PrivacyInfo.xcprivacy` **n'est pas modifié** : la qualification du jeton de notification dans les étiquettes App Privacy (« Device ID », liée ou non, finalité) est tranchée par `release-manager` (SRV-3) ; elle changera aussi le test `AppConfigurationTests.testPrivacyManifestIsCoherent`.

## 6. Coût

Aucune lecture de plus par message reçu. Registre : 1 lecture + 1 écriture par changement ou tous les 30 jours et par utilisateur actif (contrat §6). FCM gratuit. Aucun service de plus.

## 7. Pour les PR en cours (NOTIF-P0 iOS, #127/#129/#130)

Cette PR est faite sur `main` et ne dépend pas d'elles. À la fusion des deux : (1) `ios/Config/Miff.entitlements` et `ios/Miff/Resources/Localizable.xcstrings`, `ios/README.md`, `ios/CHANGELOG.md` auront des conflits triviaux (garder les deux côtés ; le commentaire des entitlements : garder le mien) ; (2) le **délégué des notifications** : un seul délégué peut être posé sur `UNUserNotificationCenter`. `PushNotificationDelegate` passe à son champ `next` tout ce qui n'est pas un message du serveur : poser `push.delegate.next = notificationResponseDelegate` et ne laisser que `push.start()` poser le délégué ; (3) la ligne de centre silencieuse : brancher `push.hook.onCircleNews`.

## 8. Contrat entre Android et le serveur : écart constaté

En lisant `backend/functions/src/notifRegles.ts` (branche `backend/srv4b-fonction-update`) et `domain/notification/CircleNews.kt` (branche `android/fcm-client`), **les deux ne parlent pas le même langage** : le serveur envoie à Android `type = "circle_update"`, `firstName`, `quiet` et **pas de `sentAt`** ; Android n'accepte que `type = "n9_update"`, `authorFirstName` et `sentAt` (entier en millisecondes) et **ignore tout le reste**. Sans correction d'un côté, **Android n'afficherait aucune notification**. iOS suit le serveur (`circle_update`). Point à régler entre `backend-data` et `android-dev` avant le déploiement de la fonction.
