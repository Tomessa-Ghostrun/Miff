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

## 2. Le document (7 clés exactement, aucune autre)

| Champ | Type | Valeur |
|---|---|---|
| `token` | string 1–4096 | Jeton FCM (Android) / jeton FCM adossé à APNs (iOS), tel que fourni par le SDK |
| `platform` | `"android"` \| `"ios"` | Figé après la création |
| `appVersion` | string 1–32 | Ex. `1.0.0 (12)` (nom de version + numéro de build) |
| `tz` | string 1–64 | Fuseau du téléphone : `TimeZone.getDefault().id` (Android), `TimeZone.current.identifier` (iOS). Forme acceptée : une lettre puis `A-Za-z0-9_+:/-` |
| `notify` | map `{ updates: bool }` | Interrupteur « Quand un proche donne des nouvelles ». **Exactement** la clé `updates`. Défaut : `true` |
| `updatedAt` | timestamp | `FieldValue.serverTimestamp()` à **chaque** écriture (jamais une date du téléphone) |
| `createdAt` | timestamp | `serverTimestamp()` à la **création** seulement ; ne plus jamais l'écrire ensuite (la règle exige qu'il reste inchangé) |

Interdit d'ajouter quoi que ce soit (position, nom, e-mail, numéro, identifiant publicitaire, identifiant de Miff, nom d'appareil, **langue**, **état de l'autorisation** : ces deux derniers ont été retirés du modèle, une clé `locale` ou `notificationsEnabled` est refusée) : le document est refusé. **Ne jamais journaliser ni envoyer à Analytics** le jeton ni ce document (`analytics-events.md` : aucun événement sur les notifications).

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

Les mises à jour peuvent être **partielles** : `update({ token, updatedAt })`, `update({ tz, updatedAt })`, `update({ 'notify.updates': false, updatedAt })`. Ne pas supprimer de clé (`FieldValue.delete()` est refusé).

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
- le fuseau a changé ; ou
- la dernière écriture date de **plus de 30 jours** (garder la date de la dernière écriture réussie en local, pas en base).

Sinon **ne rien écrire**. **Décision (ticket §9, relu le 2026-11-12)** : le ticket fixe la péremption à **30 jours** (rafraîchissement « si le document a plus de 30 jours », purge serveur des inactifs à 60 jours) et plafonne à **une écriture par jour** ; ce contrat le suit à la lettre (le coordinateur proposait 7 jours « sauf contrainte du ticket » : le ticket est explicite, c'est donc 30). Résultat : **à l'ouverture, au plus une écriture, et au plus une par jour** ; en régime normal, une écriture par mois et par utilisateur actif en plus des changements de jeton et de fuseau. La version de l'app est écrite avec les autres champs quand une écriture a lieu, elle ne déclenche pas d'écriture à elle seule. Pas de rafraîchissement en arrière-plan.

### 4.4 Interrupteur « Quand un proche donne des nouvelles »

À l'action de l'utilisateur, **sans délai ni bouton « Enregistrer »** : `update({ 'notify.updates': valeur, updatedAt })` (transaction §4.1). Le jeton reste.

### 4.5 Interrupteur général « Recevoir des notifications » coupé, autorisation du système retirée

- **Interrupteur général coupé** dans l'app : **supprimer** le document (`delete`), et invalider le jeton chez FCM (`deleteToken` / `Messaging.messaging().deleteToken`). Réactivé : nouvel enregistrement (§4.1, nouvelle création).
- **Autorisation du système retirée** (détectée à l'ouverture) : **supprimer** le document (ticket §6) et invalider le jeton chez FCM. Il n'y a plus de champ d'état d'autorisation : un document présent veut dire « cet appareil peut recevoir » ; le serveur n'envoie qu'aux appareils dont le document existe. Si la personne rétablit l'autorisation, nouvel enregistrement (§4.1).

### 4.6 Déconnexion volontaire

Dans la séquence existante de purge : **après** `waitForPendingWrites` et **avant** `signOut`, supprimer `users/{uid}/devices/{mon deviceId}` (transaction ou `delete`, **sans condition de session** : les règles autorisent la suppression même si la session a déjà été supprimée), puis invalider le jeton chez FCM, puis le reste de la déconnexion (suppression de la session, etc.). Si la suppression échoue (hors connexion), la déconnexion continue : le document restera, **inerte** (le serveur n'envoie qu'au jeton de la session active, et la reconnexion de n'importe quel appareil du compte nettoie les autres).

**Invalidation du jeton chez FCM (`deleteToken`) : obligatoire à CHAQUE chemin de sortie du compte, même quand la suppression du document Firestore échoue** (hors connexion, refus, délai). Les deux opérations sont indépendantes : tenter la suppression du document, puis appeler `deleteToken` quoi qu'il arrive (un échec de l'une n'empêche jamais l'autre ; `deleteToken` fonctionne sans réseau côté appareil et rend le jeton inutilisable). Chemins concernés : déconnexion volontaire (§4.6), remplacement par un autre appareil (§4.7), suppression de compte (§4.8), interrupteur général coupé et autorisation retirée (§4.5). C'est ce qui ferme le risque « le compte précédent reçoit encore des notifications sur ce téléphone après qu'un autre compte s'y est connecté » : un jeton FCM est lié au téléphone, pas au compte, donc un document périmé pointant vers ce jeton pourrait sinon le faire sonner pour l'ancien compte.

### 4.7 Remplacement par un autre appareil (SESS)

Le nouvel appareil a déjà supprimé le jeton de l'ancien (§4.2). L'ancien appareil, quand il constate la disparition ou le remplacement de sa session, **tente** de supprimer son document (best effort : refus possible si `signOut` a déjà eu lieu) puis se déconnecte comme d'habitude. Il **ne doit pas** tenter d'écrire : ce serait refusé.

### 4.8 Suppression de compte : état des lieux (aucun lot existant n'est modifié)

Lu dans le code de `main` le 2026-11-12 :
- **Android** : `AccountDataStore.deviceIds(uid)` lit `users/{uid}/devices` **depuis le serveur** (`Source.SERVER`) ; `deleteDevice(uid, deviceId)` supprime chaque document dans une transaction ; `AccountDeletion` les appelle à l'étape 2, **après** la suppression de la session et avant le profil.
- **iOS** : `FirestoreAccountDataStore.deviceIds` (`getDocuments(source: .server)`) et `deleteDevice` (écriture `delete`), mêmes étapes.
- **Compatible avec les règles de cette PR sans changement** : la liste (`list`) et la suppression sont permises au propriétaire, **sans condition de session** (test DV10 : séquence « session supprimée d'abord, puis liste, puis suppression de chaque jeton »).
- **Reste à faire côté client (SRV-2A / SRV-2I)** : invalider le jeton chez FCM (`deleteToken`) à cette étape (ticket §9, J2). Rien à changer dans le lot Firestore. **Besoin documenté, pas de modification ici.**

### 4.9 À la réception d'une notification : ne faire confiance qu'à son propre compte

Le message porte des identifiants techniques (Miff, auteur) pour ouvrir le bon écran. **Le client ignore toute donnée dont le `miffId` n'appartient pas au compte actuellement connecté** (Miff dont il n'est pas membre selon `memberships`/fiche de membre lue sur le serveur, ou aucun compte connecté) : pas de navigation, pas de ligne dans le centre de notifications, pas de lecture de la Miff nommée, aucune erreur visible. Cela couvre le jeton resté sur un téléphone où un autre compte s'est connecté, et tout message forgé ou périmé.

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

- **Une écriture par ouverture au plus, une par jour au plus**, en pratique : l'enregistrement initial, un changement de jeton ou de fuseau (rares), le rafraîchissement au bout de 30 jours, et les actions de réglage. ≈ **1 écriture + 1 lecture par utilisateur actif et par mois** en régime normal, plus **1 lecture de règles** à chaque écriture (la session). Suppression et lecture : 0 lecture de règles.
- Même en comptant un changement de jeton par utilisateur et par semaine, **10 000 utilisateurs actifs** font de l'ordre de 2 000 écritures par jour : très loin du quota gratuit (20 000 par jour). Les écritures de `devices` ne comptent pas parmi les postes de coût du projet (le coût dominant reste la lecture du Feed).
- **Côté serveur (lectures)** : à chaque update publiée, la fonction lit la session active de chaque destinataire (`users/{uid}/session/active`, **1 lecture par destinataire**, voir §7). Un jeton non supprimé (déconnexion échouée) ne coûte donc **aucune écriture** mais **1 lecture de session par destinataire et par update** ; l'envoi lui-même est écarté si la session n'est pas celle du document.
- **Signal à surveiller** (`docs/dev/surveillance-quotas.md`) : écritures de `devices` > 1 par utilisateur actif et par jour (plafond du contrat) = un client qui réécrit trop (boucle sur `onNewToken`, comparaison de jeton défaillante).

## 7. Ce que le serveur fait des jetons (contrat pour SRV-4, pour information)

- Lit avec les droits Admin (les règles ne s'appliquent pas). Pour chaque destinataire : lit `users/{uid}/session/active`, puis **`users/{uid}/devices/{session.deviceId}` en lecture directe (`get`)**, jamais une liste ni `limit(3)` : un seul jeton est concerné, les éventuels jetons périmés d'autres identifiants ne sont jamais lus ni utilisés (2 lectures par destinataire).
- **Session absente (déconnexion, compte sans session) : le serveur n'envoie à AUCUN jeton de ce compte**, même s'il reste des documents `devices` (ils sont inertes). Idem si le document `devices/{session.deviceId}` n'existe pas.
- N'envoie que si `notify.updates` est à `true` ; un document présent pour la session active = un appareil qui peut recevoir.
- Supprime le document quand FCM répond « jeton inconnu » ; purge optionnelle à 60 jours.
- Ne journalise ni jeton, ni uid, ni prénom. Utilise `tz` pour les heures calmes (22 h – 7 h). Les textes restent dans les apps (aucune langue n'est stockée).
- Ne touche jamais à `notifState` depuis un client : cette collection n'a **aucune règle** (refus total, test DV12).

## 8. Liste de contrôle pour `android-dev` et `ios-dev`

- [ ] Aucun jeton écrit avant l'autorisation du système **et** l'écran d'explication (J1).
- [ ] Identifiant du document = `session/active.deviceId` (jamais un autre).
- [ ] Création et mise à jour par **transaction** (lire puis créer ou mettre à jour) ; `createdAt` jamais réécrit ; `platform` jamais modifié.
- [ ] Lot de connexion : session + jeton + suppression des autres jetons (aucun chemin en double dans le lot).
- [ ] Au plus une écriture par jour ; rafraîchissement au changement de jeton ou de fuseau, ou après 30 jours ; rien en arrière-plan.
- [ ] `deleteToken` appelé à chaque sortie même si la suppression Firestore échoue (§4.6).
- [ ] À la réception : tout message dont le `miffId` n'est pas celui d'une Miff du compte connecté est ignoré (§4.9).
- [ ] Suppression : interrupteur général coupé, autorisation retirée, déconnexion (avant `signOut`), suppression de compte (déjà en place), remplacement par un autre appareil (best effort) ; invalidation du jeton chez FCM à chaque fois.
- [ ] Aucune position, aucun nom, aucun identifiant publicitaire dans le document ; aucun journal ni événement Analytics contenant le jeton.
- [ ] Tests unitaires : choix créer / mettre à jour, seuils 30 jours / 1 jour, changement de jeton, ordre déconnexion (jeton avant `signOut`), lot de connexion.

## 9. Décisions prises (coordinateur, PM ayant validé le ticket tel quel, 2026-11-12)

1. **`locale` et `notificationsEnabled` retirés du registre** : le ticket dit « pas de langue stockée » et supprime le document quand l'autorisation est retirée. Forme à 7 clés.
2. **Rafraîchissement** : 30 jours (ticket §9) et au plus une écriture par jour, voir §4.3.
3. **Règle « 30 s entre deux photos »** : non appliquée (analyse dans `backend/data-model.md` §2.17).
