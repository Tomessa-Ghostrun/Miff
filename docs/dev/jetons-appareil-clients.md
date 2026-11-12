# Jetons d'appareil : contrat pour les clients Android et iOS (SRV-2R)

**Rédigé le 2026-11-12** par `backend-data`. **NON DÉPLOYÉ** : les règles décrites ici sont dans la PR `backend/jetons-appareil` ; sur `miff-3d283`, le fichier déployé porte encore l'ancien gabarit de `devices`. **Aucun client ne doit écrire un jeton avant que le PM ait déployé les règles** (déploiement sur « oui » explicite, `docs/dev/deploiement-regles.md`, jamais par un agent).
**Sources** : ticket `docs/tickets/notification-update-membres.md` §6, §8, §9 (PR #192) ; étude `docs/dev/etude-serveur-notifications-premium.md` §8 (SRV-2) ; schéma de référence `backend/data-model.md` §2.10, §2.16 ; tests `backend/rules-test-plan.md` §26.
**Périmètre** : ce document dit **quoi écrire, quand, avec quelles erreurs attendues**. L'envoi des notifications (Cloud Function), les textes légaux et l'écran d'explication sont d'autres tickets (SRV-4B, SRV-3, SRV-2A/2I).

En une phrase pour le PM : le téléphone dépose, à un endroit privé de la base, « l'adresse » où le serveur pourra lui envoyer une notification ; personne d'autre ne peut la lire, elle disparaît à la déconnexion, et elle coûte **au plus une écriture par jour et par utilisateur** (gratuit sous 20 000 écritures par jour).

---

## 1. Chemin et identifiant

`users/{uid}/devices/{deviceId}`

- `{uid}` : le compte connecté, **toujours le sien** (les règles refusent tout autre).
- `{deviceId}` : **l'identifiant de la session** de cet appareil, c'est-à-dire `session/active.deviceId` (SESS : 16 à 64 caractères `[A-Za-z0-9_-]`, aléatoire, généré à la connexion). **Pas** un identifiant d'installation séparé, **pas** `d1`..`d5`. Un compte = un appareil actif = un jeton. Si la session change (autre téléphone), l'ancien identifiant ne peut plus rien écrire.

## 2. Le document (9 clés exactement, aucune autre)

| Champ | Type | Valeur |
|---|---|---|
| `token` | string 1–4096 | Jeton FCM (Android) / jeton FCM adossé à APNs (iOS), tel que fourni par le SDK |
| `platform` | `"android"` \| `"ios"` | Figé après la création |
| `appVersion` | string 1–32 | Ex. `1.0.0 (12)` (nom de version + numéro de build) |
| `locale` | `"fr"` \| `"en"` | Langue de l'application (repli : `en` si la langue du téléphone n'est ni `fr` ni `en`) |
| `notificationsEnabled` | bool | `true` si l'autorisation du système est accordée **et** l'interrupteur général de l'app est actif |
| `tz` | string 1–64 | Fuseau du téléphone : `TimeZone.getDefault().id` (Android), `TimeZone.current.identifier` (iOS). Forme acceptée : une lettre puis `A-Za-z0-9_+:/-` |
| `notify` | map `{ updates: bool }` | Interrupteur « Quand un proche donne des nouvelles ». **Exactement** la clé `updates`. Défaut : `true` |
| `updatedAt` | timestamp | `FieldValue.serverTimestamp()` à **chaque** écriture (jamais une date du téléphone) |
| `createdAt` | timestamp | `serverTimestamp()` à la **création** seulement ; ne plus jamais l'écrire ensuite (la règle exige qu'il reste inchangé) |

Interdit d'ajouter quoi que ce soit (position, nom, e-mail, numéro, identifiant publicitaire, identifiant de Miff, nom d'appareil) : le document est refusé. **Ne jamais journaliser ni envoyer à Analytics** le jeton ni ce document (`analytics-events.md` : aucun événement sur les notifications).

## 3. Préconditions : quand il est permis d'écrire un jeton

1. L'utilisateur est connecté **et la session `users/{uid}/session/active` existe avec ce `deviceId`** (sinon `permission-denied`).
2. L'**autorisation du système** est accordée **et** l'écran d'explication corrigé (ticket §9, §15) a été vu. **Jamais avant** : la politique de confidentialité promet qu'aucun jeton n'est enregistré sans accord. Android 13+ : après `POST_NOTIFICATIONS` ; Android 8 à 12 : après l'interrupteur de l'app ; iOS : après l'autorisation, puis réception du jeton.
3. Les règles Firestore de cette PR sont **déployées** (sinon l'écriture est refusée par l'ancien gabarit).

## 4. Les opérations

Toutes passent par le **serveur** (transaction ou lot ; pas d'écriture mise en file hors connexion pour le jeton : une écriture différée de plusieurs jours serait obsolète et pourrait être refusée si la session a changé). En cas d'échec réseau : réessayer **à la prochaine ouverture**, sans boucle.

### 4.1 Enregistrer (création ou mise à jour) : une transaction « lire puis écrire »

```
transaction {
  snap = get(users/{uid}/devices/{deviceId})
  if snap n'existe pas :   set(ref, document complet, createdAt = serverTimestamp, updatedAt = serverTimestamp)
  else :                   update(ref, { champs qui changent..., updatedAt = serverTimestamp })   // jamais createdAt, jamais platform
}
```

Pourquoi : un `set` complet sur un document **déjà présent** est une mise à jour dont le `createdAt` serait neuf, donc **refusé** (`permission-denied`). Un `update` sur un document absent échoue aussi. La transaction choisit la bonne opération (coût : 1 lecture + 1 écriture).

Les mises à jour peuvent être **partielles** : `update({ token, updatedAt })`, `update({ tz, updatedAt })`, `update({ 'notify.updates': false, updatedAt })`, `update({ notificationsEnabled, updatedAt })`. Ne pas supprimer de clé (`FieldValue.delete()` est refusé).

### 4.2 Connexion : le lot [session + jeton + suppression des autres jetons]

À chaque connexion (nouvelle session), **si les préconditions du §3 sont réunies**, un **seul lot** (batch) :

1. `set(session/active, { deviceId: nouveauId, startedAt: serverTimestamp })` (comme aujourd'hui, SESS) ;
2. `set(devices/{nouveauId}, document complet, createdAt = updatedAt = serverTimestamp)` ;
3. `delete(devices/{ancienId})` pour **chaque autre document** de `users/{uid}/devices` (liste lue **côté serveur** juste avant, 1 à 3 documents en pratique ; ne jamais mettre deux opérations sur le même chemin dans un lot : l'émulateur n'en évalue qu'une).

Si le jeton ne peut pas encore être écrit (permission pas encore donnée), faire quand même l'étape 3 (supprimer les jetons des autres appareils) et écrire le jeton plus tard, en transaction (§4.1), quand l'autorisation arrive : la session existe alors déjà.

Pourquoi supprimer les autres : « un compte, un seul appareil ». L'ancien téléphone ne doit plus être joignable ; le serveur n'enverra de toute façon qu'au jeton de la session active (contrat SRV-4).

### 4.3 Rafraîchissement

À l'ouverture de l'app (et quand le SDK fournit un nouveau jeton), exécuter §4.1 **si** :
- le jeton fourni par le SDK diffère du dernier jeton enregistré, **immédiatement** (rotation FCM : `onNewToken` / `didReceiveRegistrationToken`) ; ou
- le fuseau, la version de l'app, la langue, ou `notificationsEnabled` ont changé ; ou
- la dernière écriture date de **plus de 24 h** (garder la date de la dernière écriture réussie en local, pas en base).

Sinon **ne rien écrire**. Résultat : **au plus une écriture par ouverture, et au plus une par 24 h** hors changement de jeton (le ticket parle de 30 jours ; 24 h donne au serveur une preuve de présence plus fraîche, pour un coût négligeable : voir §6). Pas de rafraîchissement en arrière-plan.

### 4.4 Interrupteur « Quand un proche donne des nouvelles »

À l'action de l'utilisateur, **sans délai ni bouton « Enregistrer »** : `update({ 'notify.updates': valeur, updatedAt })` (transaction §4.1). Le jeton reste.

### 4.5 Interrupteur général « Recevoir des notifications » coupé, autorisation du système retirée

- **Interrupteur général coupé** dans l'app : **supprimer** le document (`delete`), et invalider le jeton chez FCM (`deleteToken` / `Messaging.messaging().deleteToken`). Réactivé : nouvel enregistrement (§4.1, nouvelle création).
- **Autorisation du système retirée** (détectée à l'ouverture) : **supprimer** le document (ticket §6). Le champ `notificationsEnabled` reste dans le modèle comme ceinture de sécurité du serveur (« jamais d'envoi à `false` ») ; un client **peut** écrire `notificationsEnabled = false` à la place de la suppression si celle-ci échoue, mais la règle du produit est la suppression (question ouverte n° 2, §9 ci-dessous).

### 4.6 Déconnexion volontaire

Dans la séquence existante de purge : **après** `waitForPendingWrites` et **avant** `signOut`, supprimer `users/{uid}/devices/{mon deviceId}` (transaction ou `delete`, **sans condition de session** : les règles autorisent la suppression même si la session a déjà été supprimée), puis invalider le jeton chez FCM, puis le reste de la déconnexion (suppression de la session, etc.). Si la suppression échoue (hors connexion), la déconnexion continue : le document restera, **inerte** (le serveur n'envoie qu'au jeton de la session active, et la reconnexion de n'importe quel appareil du compte nettoie les autres).

### 4.7 Remplacement par un autre appareil (SESS)

Le nouvel appareil a déjà supprimé le jeton de l'ancien (§4.2). L'ancien appareil, quand il constate la disparition ou le remplacement de sa session, **tente** de supprimer son document (best effort : refus possible si `signOut` a déjà eu lieu) puis se déconnecte comme d'habitude. Il **ne doit pas** tenter d'écrire : ce serait refusé.

### 4.8 Suppression de compte : état des lieux (aucun lot existant n'est modifié)

Lu dans le code de `main` le 2026-11-12 :
- **Android** : `AccountDataStore.deviceIds(uid)` lit `users/{uid}/devices` **depuis le serveur** (`Source.SERVER`) ; `deleteDevice(uid, deviceId)` supprime chaque document dans une transaction ; `AccountDeletion` les appelle à l'étape 2, **après** la suppression de la session et avant le profil.
- **iOS** : `FirestoreAccountDataStore.deviceIds` (`getDocuments(source: .server)`) et `deleteDevice` (écriture `delete`), mêmes étapes.
- **Compatible avec les règles de cette PR sans changement** : la liste (`list`) et la suppression sont permises au propriétaire, **sans condition de session** (test DV10 : séquence « session supprimée d'abord, puis liste, puis suppression de chaque jeton »).
- **Reste à faire côté client (SRV-2A / SRV-2I)** : invalider le jeton chez FCM (`deleteToken`) à cette étape (ticket §9, J2). Rien à changer dans le lot Firestore. **Besoin documenté, pas de modification ici.**

## 5. Erreurs attendues

| Situation | Erreur Firestore | Conduite du client |
|---|---|---|
| Pas de session, session d'un autre appareil, session supprimée (déconnexion en cours, appareil remplacé) | `PERMISSION_DENIED` | Ne pas réessayer en boucle ; si l'appareil a été remplacé, la procédure SESS le déconnecte. Ne jamais afficher d'erreur à la personne (non bloquant, aucune alerte) |
| Document non conforme (clé en trop / manquante, fuseau, taille du jeton, `createdAt` modifié, `set` complet sur un jeton existant) | `PERMISSION_DENIED` | Bug du client : journaliser **sans** donnée personnelle (code d'erreur seul) ; réessayer une seule fois avec la lecture préalable (§4.1) |
| Hors connexion, serveur indisponible | `UNAVAILABLE` / délai dépassé | Abandonner ; réessayer à la prochaine ouverture ; ne pas mettre en file |
| Quota dépassé (Blaze sans budget, ou Spark) | `RESOURCE_EXHAUSTED` | Abandonner ; réessayer plus tard |
| Document absent à la suppression | aucune (la suppression d'un document absent réussit) | Rien à faire |
| App Check refusé | `PERMISSION_DENIED` / `UNAUTHENTICATED` | Même conduite que les autres écritures (`docs/dev/app-check.md`) |

Messages d'erreur côté interface : **aucun** pour ces écritures (ton non anxiogène, aucune action de la personne ne peut les résoudre). Le Feed reste la source de vérité ; sans jeton, la personne ne reçoit simplement pas la notification.

## 6. Coût en écritures

- **Une écriture par ouverture au plus, et au plus toutes les 24 h**, plus les changements de jeton (rares) et les actions de réglage. En pratique : ≈ **1 écriture + 1 lecture par utilisateur actif et par jour**, plus **1 lecture de règles** à chaque écriture (la session). Suppression et lecture : 0 lecture de règles.
- **1 000 utilisateurs actifs par jour** ≈ 1 000 écritures et 2 000 lectures par jour : sous les quotas gratuits (20 000 écritures, 50 000 lectures par jour). **10 000** ≈ 10 000 écritures par jour : encore sous le quota d'écritures ; en cas de dépassement, de l'ordre de 0,5 $ par mois (hypothèse, tarif Firestore de l'étude non vérifié pour Paris).
- **Signal à surveiller** (`docs/dev/surveillance-quotas.md`) : écritures de `devices` > 1 par utilisateur actif et par jour = un client qui réécrit trop (boucle sur `onNewToken`, comparaison de jeton défaillante).

## 7. Ce que le serveur fait des jetons (contrat pour SRV-4, pour information)

- Lit avec les droits Admin (les règles ne s'appliquent pas) ; `limit(3)` par destinataire.
- N'envoie qu'au jeton dont l'identifiant est celui de `session/active.deviceId` (ou, à défaut, `updatedAt ≥ session.startedAt`), et seulement si `notificationsEnabled` et `notify.updates` sont à `true`.
- Supprime le document quand FCM répond « jeton inconnu » ; purge optionnelle à 60 jours.
- Ne journalise ni jeton, ni uid, ni prénom. Utilise `tz` pour les heures calmes (22 h – 7 h). Les textes restent dans les apps (`locale` sert à un éventuel texte serveur ; candidat à suppression, question ouverte).
- Ne touche jamais à `notifState` depuis un client : cette collection n'a **aucune règle** (refus total, test DV12).

## 8. Liste de contrôle pour `android-dev` et `ios-dev`

- [ ] Aucun jeton écrit avant l'autorisation du système **et** l'écran d'explication (J1).
- [ ] Identifiant du document = `session/active.deviceId` (jamais un autre).
- [ ] Création et mise à jour par **transaction** (lire puis créer ou mettre à jour) ; `createdAt` jamais réécrit ; `platform` jamais modifié.
- [ ] Lot de connexion : session + jeton + suppression des autres jetons (aucun chemin en double dans le lot).
- [ ] Au plus une écriture par 24 h hors changement de jeton ; rien en arrière-plan.
- [ ] Suppression : interrupteur général coupé, autorisation retirée, déconnexion (avant `signOut`), suppression de compte (déjà en place), remplacement par un autre appareil (best effort) ; invalidation du jeton chez FCM à chaque fois.
- [ ] Aucune position, aucun nom, aucun identifiant publicitaire dans le document ; aucun journal ni événement Analytics contenant le jeton.
- [ ] Tests unitaires : choix créer / mettre à jour, seuils 24 h, changement de jeton, ordre déconnexion (jeton avant `signOut`), lot de connexion.

## 9. Questions ouvertes (à trancher par le PM, ne pas deviner)

1. **`locale`** : le ticket dit « pas de langue stockée » (les textes restent dans les apps) ; le cahier SRV-2 la demande. Gardée dans le modèle ; à retirer (minimisation) si le serveur n'en a pas besoin.
2. **`notificationsEnabled` et suppression** : le ticket supprime le document quand l'autorisation est retirée, ce qui rend `false` presque toujours absent. Garder le champ comme ceinture de sécurité, ou le retirer ?
3. **Fréquence de rafraîchissement** : 24 h (ce document) ou 30 jours (ticket) ? 24 h coûte ≈ 1 écriture par jour et par utilisateur actif, négligeable ; 30 jours réduit encore le coût mais retarde la détection d'un appareil inactif.
