# Notifications push côté Android (SRV-2) : notes d'exploitation

**Rédigé le 2026-11-12** par `android-dev`. Code : `android/app/src/main/java/com/tomessa/miff/data/push/`, `domain/push/`, `domain/notification/CircleNews.kt`, `data/notification/` (affichage). Ticket : `docs/tickets/notification-update-membres.md`. Étude : `docs/dev/etude-serveur-notifications-premium.md`. Recette : `docs/qa/checklists/fcm-android-recette-pm.md`.

## 1. Ce que le téléphone fait

| Sujet | Comportement |
|---|---|
| Message reçu | **DATA seulement** (aucune notification « display » de FCM). `MiffMessagingService.onMessageReceived` → `PushMessageHandler` → `CircleNewsParser` (validation stricte) → `NotificationScheduler.onCircleNews` (interrupteurs, plafonds, premier plan, nuit) → notification locale + ligne du centre. **Aucune lecture Firestore.** |
| Contrat du message v1 | `{ type: "n9_update", miffId, authorUid?, authorFirstName, sentAt }`. `sentAt` en **millisecondes**. Un autre `type` est ignoré sans trace. Le serveur doit envoyer en **priorité haute**, avec une durée de vie de **2 h**. |
| Texte | Composé par l'app dans la langue de l'app : « {Prénom} a donné des nouvelles » / « Retrouve-les dans ta Miff. ». Prénom nettoyé et coupé à 20 caractères. Jamais de photo, position, nom de Miff, nom de famille, heure. |
| Canaux | `miff_updates` (« Nouvelles des proches », importance par défaut, son + vibration selon le téléphone) et `miff_updates_quiet` (sans son ni vibration, de 22 h à 7 h). **L'importance d'un canal ne peut plus être changée une fois créé** : pour changer la décision du PM un jour, créer un nouvel identifiant de canal et supprimer l'ancien. |
| Remplacement | Un auteur = une notification (étiquette `n9|u|{authorUid}`) ; sans `authorUid`, une par Miff. |
| Plafonds | 6 par jour sur le téléphone (hors total des rappels) ; au-delà, ligne du centre silencieuse. Le plafond d'**une par heure et par auteur** est tenu par le **serveur** (ticket §7). |
| Appui | Ouvre le Feed de la Miff concernée (`NotificationIntents.miffId`, forme vérifiée ; `MiffDirectory.preselect` ignore une Miff dont la personne n'est pas membre). |

## 2. Le registre d'appareils

Document `users/{uid}/devices/{deviceId}` (contrat final du backend, 7 clés) : `token`, `platform = "android"`, `appVersion`, `tz` (fuseau), `notify = { updates: bool }`, `updatedAt`, `createdAt`. `deviceId` = identifiant de session SESS. Écrit par `PushRegistrar` (transaction), derrière `DeviceTokenRegistry`.

- **Quand** : jamais sans compte + session valide + consentement ; seulement si quelque chose a changé, ou 30 jours après la dernière écriture, à l’ouverture (le fuseau : une fois par jour au plus). Nouvelle session : les autres documents du compte sont supprimés.
- **Fin** : déconnexion, perte de session, suppression du compte, interrupteur général coupé, permission retirée : suppression du document + `FirebaseMessaging.deleteToken()` + démarrage automatique éteint + mémoire locale effacée.
- **Démarrage automatique du jeton coupé** dans le manifeste (`firebase_messaging_auto_init_enabled=false`) : aucun jeton n'existe avant le consentement.
- **Contrat et dépendance du backend** : `docs/dev/jetons-appareil-clients.md` (branche `backend/jetons-appareil`, **règles non déployées**, à déployer par le PM sur « oui » explicite). Le document a 7 clés exactement (`token`, `platform`, `appVersion`, `tz`, `notify.updates`, `updatedAt`, `createdAt`) ; les règles actuellement déployées n’acceptent encore que `token`, `platform`, `updatedAt` : tant que les nouvelles ne sont pas déployées, l’écriture est refusée et réessayée plus tard. Écriture en transaction « lire puis créer ou mettre à jour » ; une **nouvelle session** supprime les autres documents du compte, avec ou sans consentement (contrat 4.2). Seule `FirestoreDeviceTokenRegistry` connaît la forme du document.

## 3. OPPO / ColorOS et autres surcouches : limites de livraison en arrière-plan

**Établi** (étude §4, sources officielles lues le 2026-11-11) : FCM n'a aucune garantie de livraison ; un message est **abandonné si l'app est « forcée à l'arrêt »** au moment de la livraison, jusqu'à sa réouverture ; la priorité haute tente de livrer tout de suite, y compris en Doze ; les messages en attente sont limités (100 par instance, un seul par clé de regroupement).

**Non vérifié sur l'OPPO Find X5 Lite (Android 13, ColorOS)** : les surcouches arrêtent parfois les applications au balayage ou à l'extinction de l'écran (dontkillmyapp, donnée limitée, ancien modèle). Ce qui est connu côté réglages de la personne : **démarrage automatique** de Miff, **batterie : aucune restriction**, **activité en arrière-plan**, **ne pas balayer l'app** de la liste des récentes, éviter l'économiseur de batterie permanent. Aucune exemption de batterie n'est **demandée** par l'app (décision Q13 du ticket des notifications) : l'aide existante les **décrit** seulement.

**Ce que fait l'app** : l'écran d'aide « Si les arrivées et départs tardent » (Réglages > Notifications > « Si les rappels n'arrivent pas ») cite maintenant les **nouvelles des proches** et « Forcer l'arrêt », sans culpabiliser, et rappelle que rien n'est perdu : tout est dans la Miff. Le Feed reste la source de vérité ; la notification est un accélérateur.

**À mesurer en recette** (parties 3.1 à 3.3) : délai app fermée, app balayée, après « Forcer l'arrêt ». Si les messages DATA sont retardés ou perdus app balayée, le repli prévu par le ticket (§11) est un message avec notification affichée par le système (à décider au vu de la mesure ; il changerait le contrat du message et la façon de composer le texte).

## 4. Coût

Aucune lecture Firestore par message. Registre : une écriture par changement ou tous les 30 jours et par utilisateur actif (`RegistrationRules.REFRESH_AFTER_MILLIS`, 30 jours comme le ticket). FCM est gratuit.

## 4 bis. Contrôles de la revue indépendante (PR #201)

- **Un message sur une Miff qui n’est pas celle du compte est ignoré en entier** (contrat §4.9) : ni notification, ni ligne du centre, ni navigation, ni mémoire de message traité. La liste des Miffs du compte est **gardée sur le téléphone** (`KnownMiffs` : identifiants seulement, un seul compte à la fois, préférences privées hors sauvegarde), mise à jour à chaque lecture de la liste par l’annuaire, retirée quand on quitte une Miff, **effacée à la déconnexion**. Au démarrage à froid par un message, c’est cette liste qui répond ; **liste inconnue ou autre compte = ignoré** (jamais affiché par défaut). Un message sans `authorUid` est traité (seul le filtre « pas pour soi-même » ne peut pas s’appliquer).
- **Le consentement n’est enregistré que depuis un écran qui porte la phrase sur l’identifiant du téléphone** : l’amorce (texte corrigé), Réglages > Notifications (sous l’interrupteur général), la carte de seconde chance du Feed et le bandeau / l’aperçu du centre (`PushIdentifierNote`). Chaque « oui » déclenche aussitôt une synchronisation du registre (le premier enregistrement n’attend pas un retour au premier plan).
- **Déconnexion hors connexion** : `PushRegistrar.forget()` invalide le jeton et éteint le service **avant** de prendre le verrou ; chaque appel au serveur du registre est borné à 15 s.
- **Manifeste fusionné vérifié (release, 2026-11-12)** : le seul composant de la bibliothèque FCM exporté est `FirebaseInstanceIdReceiver`, protégé par la permission `com.google.android.c2dm.permission.SEND` (que seuls les services de Google détiennent) ; le `FirebaseMessagingService` de la bibliothèque est non exporté, comme le nôtre (`MiffMessagingService`). Les composants exportés d’Auth (`GenericIdpActivity`, `RecaptchaActivity`) existaient déjà.
/
## 5. Sécurité

Service non exporté ; jeton jamais écrit sur le disque (empreinte SHA-256 seulement), jamais journalisé ni envoyé comme événement (tous les `toString` sont expurgés, `PushPrivacyTest` surveille les sources) ; message validé champ par champ, prénom assaini ; identifiant de Miff d'un appui vérifié (l'activité de lancement est exportée : un autre logiciel peut construire l'intention) ; consentement par compte ; aucun secret côté client.
