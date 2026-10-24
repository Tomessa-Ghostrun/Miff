# Plan de test des règles Firestore (à automatiser plus tard avec l'émulateur)

À exécuter avec `@firebase/rules-unit-testing` contre l'émulateur Firestore, **jamais contre le projet réel**. Les règles (`firestore.rules`) n'ont été relues que sur le papier : ce plan est la condition pour les déployer.

Légende : OK = doit être autorisé ; KO = doit être refusé (`permission-denied`).
Un batch est noté `[a + b + c]` : toutes les écritures sont envoyées ensemble ; il réussit ou échoue en bloc.

## 0. Jeu de données de départ (fixtures)

Comptes : `alice` (gratuite, créatrice), `bob` (gratuit, membre), `carol` (gratuite, membre), `dave` (premium actif), `eve` (gratuite, étrangère aux Miffs), `anon` (non connecté).

- `entitlements/dave` = `{active:true, expiresAt: +30 jours, source:"manual"}` ; `entitlements/frank` = `{active:true, expiresAt: -1 jour}` (expiré).
- Miff `M1` (ID de 20 caractères) : `ownerUid=alice`, `joinCode=K7M2QX9PDW4H`. `joinCodes/K7M2QX9PDW4H = {miffId:M1, miffName:"Famille"}`.
- Membres de M1 : `alice` (owner, sharing actif), `bob` (member, sharing actif), `carol` (member, **sharing désactivé**).
- Index : `users/alice/memberships/free → M1`, `users/bob/memberships/free → M1`, `users/carol/memberships/free → M1`.
- `miffs/M1/status/alice` et `status/bob` présents. `miffs/M1/places/p1` (type trusted).

## 1. Accès par défaut et lecture

| # | Cas | Attendu |
|---|---|---|
| R1 | `anon` lit `miffs/M1`, `members`, `status`, `places`, `users/alice`, `joinCodes/K7M2QX9PDW4H` | KO partout |
| R2 | `eve` (connectée, non membre) lit `miffs/M1` | KO |
| R3 | `eve` lit `miffs/M1/members` (list), `members/alice`, `places`, `status`, `photos/alice` | KO partout |
| R4 | `eve` fait `list` sur `miffs`, `users`, `joinCodes`, `entitlements`, `collectionGroup('members')`, `collectionGroup('status')` | KO partout |
| R5 | `bob` lit `miffs/M1`, `members` (list), `places` (list) | OK |
| R6 | `bob` lit `users/alice` | KO |
| R7 | `alice` lit `users/alice` | OK |
| R8 | `bob` lit `miffs/M1/status/alice` (list et get) — il partage sa position | OK |
| R9 | `carol` (partage désactivé) lit `status/alice` et la liste `status` | **KO** (réciprocité) ; elle lit toujours `members` et `places` : OK |
| R10 | `eve` lit `entitlements/dave` | KO ; `dave` lit le sien : OK |
| R11 | Chemin inconnu (`foo/bar`, `miffs/M1/autre/x`) en lecture/écriture | KO |
| R12 | `bob` lit `miffs/M1/photos/alice` (get) si `hasPhoto` ; list sur `photos` | get OK ; list KO |

## 2. Création d'une Miff

| # | Cas | Attendu |
|---|---|---|
| C1 | `eve` (gratuite, sans Miff) : batch `[miffs/NEW (ownerUid=eve, name, joinCode, createdAt=serverTime) + joinCodes/CODE + members/eve (role=owner, sharing) + memberships/free (miffId=NEW)]` | OK |
| C2 | Même batch avec `ownerUid=alice` (usurpation) | KO |
| C3 | Même batch sans `members/eve` | KO (Miff incomplète) |
| C4 | Même batch sans `joinCodes/CODE` | KO |
| C5 | `miffId` de moins/plus de 20 caractères, ou égal à `free` | KO |
| C6 | `joinCode` de 11 ou 13 caractères, ou contenant `O`, `0`, `I`, `1`, `L`, ou minuscules | KO |
| C7 | `createdAt` avec une date client (≠ heure serveur) | KO |
| C8 | `alice` (déjà `free → M1`) crée une 2e Miff avec la clé `free` | KO (second create = update interdit) |
| C9 | `alice` crée une 2e Miff avec la clé = nouveau `miffId` (gratuite) | KO |
| C10 | `dave` (premium) crée une 2e Miff avec la clé = `miffId` | OK |
| C11 | `frank` (premium expiré) crée une Miff avec la clé = `miffId` | KO |
| C12 | `eve` crée un `joinCodes/CODE` pour la Miff M1 d'alice (`miffId=M1`) | KO |
| C13 | `eve` crée `joinCodes/CODE` pour sa Miff avec `miffName` différent du nom de la Miff | KO |
| C14 | Collision : créer `joinCodes/K7M2QX9PDW4H` (existe déjà) | KO |
| C15 | Création de `members/eve` role=owner dans M1 (Miff existante) | KO |
| C16 | Champ inconnu dans le document Miff / membre / code | KO |
| C17 | Compte de plus de 20 appels de lecture de règles dans le batch de création (mesurer sur le cas premium, le plus coûteux) | OK (marge à confirmer) |

## 3. Rejoindre une Miff (code secret)

| # | Cas | Attendu |
|---|---|---|
| J1 | `eve`, `get joinCodes/K7M2QX9PDW4H` | OK (renvoie miffId + nom) |
| J2 | `eve`, `get joinCodes/ZZZZZZZZZZZZ` (inexistant) | OK, document introuvable (pas d'erreur de permission, pas de fuite) |
| J3 | `eve`, `get joinCodes/abc` (mauvais format) | KO |
| J4 | `eve`, batch `[members/eve (role=member, joinCode=K7M2QX9PDW4H) + memberships/free (miffId=M1)]` | OK |
| J5 | `eve` rejoint M1 avec un `joinCode` d'une autre Miff (ex. celui de M2) | KO |
| J6 | `eve` rejoint M1 sans champ `joinCode` | KO |
| J7 | `eve` rejoint M1 en connaissant `miffId` mais avec un code inventé | KO |
| J8 | `eve` rejoint M1 avec `role="owner"` | KO |
| J9 | `eve` rejoint sans écrire l'index `memberships` | KO |
| J10 | `bob` (déjà `free → M1`) rejoint M2 (gratuit) avec clé `free` | KO |
| J11 | `bob` rejoint M2 avec clé = `M2` (non premium) | KO |
| J12 | `dave` (premium) rejoint M2 avec clé = `M2` | OK |
| J13 | `eve` crée `members/bob` (écrire pour un autre uid) | KO |
| J14 | `eve` rejoint avec `sharing.enabled=true` mais `consentedAt` ≠ heure serveur | KO |
| J15 | `eve` rejoint avec `sharing.enabled=true` sans `consentVersion` | KO |
| J16 | Après régénération du code (K7M2QX9PDW4H supprimé), `eve` tente de rejoindre avec l'ancien code | KO |
| J17 | Énumération : tentative de `list` sur `joinCodes` (avec/sans `where`, `startAt`) | KO |
| J18 | `eve` tente de lire `miffs/M1` avant de rejoindre (pour en lire le code) | KO |

## 4. Rôles, modification, retrait, départ

| # | Cas | Attendu |
|---|---|---|
| M1 | `bob` met à jour son `firstName` / `avatar` (≤ 20 Ko) | OK |
| M2 | `bob` met à jour `role` en `owner` | KO |
| M3 | `bob` modifie `joinedAt` ou `joinCode` de son document | KO |
| M4 | `bob` modifie `members/carol` | KO |
| M5 | `bob` met `ownerUid=bob` sur la Miff M1 | KO |
| M6 | `alice` renomme M1 (`name`) avec batch `[miffs update + joinCodes update miffName]` | OK |
| M7 | `alice` modifie `ownerUid` ou `createdAt` | KO |
| M8 | `bob` renomme M1 ou supprime M1 | KO |
| M9 | `alice` régénère le code : `[miffs update joinCode + joinCodes create nouveau + joinCodes delete ancien]` | OK |
| M10 | `alice` change `joinCode` sans supprimer l'ancien | KO |
| M11 | `bob` régénère le code | KO |
| M12 | `bob` quitte : `[members/bob delete + status/bob delete + photos/bob delete + memberships/free delete]` | OK |
| M13 | `bob` quitte en laissant `status/bob` | KO (trace de position) |
| M14 | `bob` supprime l'index `memberships/free` sans quitter la Miff | KO (sinon 2 Miffs gratuites) |
| M15 | `alice` retire `bob` : `[members/bob delete + status/bob delete + photos/bob delete]` | OK |
| M16 | `alice` retire `bob` sans supprimer son statut | KO |
| M17 | `bob` retire `carol` | KO |
| M18 | `alice` (créatrice) quitte sans supprimer la Miff | KO ; avec `[miffs delete + miffsDeleted create + joinCodes delete + members/alice delete + status/alice delete + memberships/free delete]` : OK (les autres membres doivent déjà avoir été retirés) ; le même lot **sans** `miffsDeleted` : KO (DUR-1, voir H1b) |
| M19 | `bob` (retiré) supprime son index orphelin `memberships/free` | OK ; avant d'être retiré : KO |
| M20 | `bob` (retiré) relit `miffs/M1`, `members`, `status`, `places` | KO partout |

## 5. Consentement et statut de position

| # | Cas | Attendu |
|---|---|---|
| S1 | `carol` active le partage : `members/carol.sharing {enabled:true, consentedAt=serverTime, consentVersion:"v1", revokedAt:null}` | OK |
| S2 | Même chose avec `consentedAt` client | KO |
| S3 | `carol` active le partage sans `consentVersion` | KO |
| S4 | `bob` désactive son partage : `[members/bob update + status/bob delete + photos/bob delete]` | OK |
| S5 | `bob` désactive son partage sans supprimer `status/bob` | KO |
| S6 | `bob` repasse `enabled=false` en effaçant `consentedAt` (pour effacer la preuve) | KO |
| S7 | `carol` (partage désactivé) crée `status/carol` | KO |
| S8 | `carol` active le partage et crée `status/carol` dans un batch | OK |
| S9 | `bob` écrit `status/alice` | KO |
| S10 | `bob` met à jour son statut : tous les champs, `updatedAt=serverTime` | OK |
| S11 | Statut avec clé inconnue (ex. `lat`) ou coordonnées | KO |
| S12 | Statut avec `presence="banane"`, `batteryPct=150`, `updateText` > 140 caractères | KO |
| S13 | Statut avec `presenceAt` à +1 jour (futur) ou −3 jours | KO ; à −2 h : OK |
| S14 | Statut avec `updatedAt` client | KO |
| S15 | Statut avec une clé manquante (non nullable absente) | KO |
| S16 | Photo : `photos/bob` ≤ 150 000 octets, `expiresAt` = +24 h | OK ; 150 001 octets ou `expiresAt` +30 jours : KO |
| S17 | `bob` écrit `photos/alice` | KO |
| S18 | `alice` supprime `status/bob` (purge) | OK ; `bob` supprime `status/alice` : KO |
| S19 | Le statut et les photos d'un membre ne contiennent aucun champ de coordonnées (revue de schéma) | OK |

## 6. Lieux et limites freemium

| # | Cas | Attendu |
|---|---|---|
| P1 | `alice` (gratuite) crée `places/p1` type `trusted` dans une Miff sans lieu | OK |
| P2 | `alice` (gratuite) crée un 2e lieu (ID auto) | KO |
| P3 | `alice` (gratuite) crée `places/p1` alors qu'il existe (= update avec champs verrouillés) | KO si elle change `type`/`createdBy`/`createdAt` ; modifier nom/rayon/coordonnées : OK |
| P4 | `alice` (gratuite) crée un lieu `type="risk"` (ID `p1`) | KO |
| P5 | `dave` (premium) crée 3 lieux (ID auto) dont 1 `risk` | OK |
| P6 | `frank` (premium expiré) crée un 2e lieu | KO |
| P7 | `bob` (membre) crée / modifie / supprime un lieu | KO |
| P8 | `alice` change le `type` d'un lieu existant | KO |
| P9 | `alice` : `radiusM=10` ou `5000`, `lat=95`, `lng=200`, nom vide | KO |
| P10 | `alice` supprime puis recrée `p1` | OK |
| P11 | `alice` modifie `createdBy` d'un lieu | KO |
| P12 | Lieu avec `createdAt` client | KO |
| P13 | `eve` lit `places` de M1 | KO |

## 7. Premium et attaques de privilèges

| # | Cas | Attendu |
|---|---|---|
| A1 | `eve` écrit `entitlements/eve = {active:true, expiresAt: +1 an}` | KO |
| A2 | `eve` modifie / supprime `entitlements/dave` | KO |
| A3 | `eve` ajoute un champ `premium:true` à `users/eve` | KO (clé inconnue) |
| A4 | `eve` crée un document `users/eve` avec un champ `isPremium` | KO |
| A5 | `eve` écrit dans `users/alice` | KO |
| A6 | `eve` écrit `users/eve/memberships/M1` (clé premium) en étant gratuite | KO |
| A7 | `eve` modifie une entrée `memberships` existante (réaffecter un slot) | KO |
| A8 | `eve` écrit des documents arbitraires hors schéma (`secret/x`) | KO |
| A9 | Contournement par deux comptes : hors périmètre des règles (documenté, §6 data-model) | — |
| A10 | Premium expiré : `dave` avec `expiresAt` passé ne peut plus créer de Miff/lieu premium | KO |

## 8. Profil, appareils, suppression

| # | Cas | Attendu |
|---|---|---|
| U1 | `eve` crée `users/eve` avec `termsAcceptedAt` = heure serveur | OK |
| U2 | Idem avec `firstName` vide, `avatar` > 20 Ko, clé inconnue | KO |
| U3 | `eve` modifie `createdAt` | KO |
| U4 | `eve` supprime `users/eve` | OK |
| U5 | `eve` crée `users/eve/devices/d1 {token, platform:"ios", updatedAt=serverTime}` | OK ; `platform:"web"` : KO |
| U6 | `bob` lit `users/eve/devices/d1` | KO |
| U7 | Suppression de compte d'une membre simple (batch par Miff) | OK |
| U8 | Suppression de compte d'une créatrice avec membres restants : suppression de la Miff sans retirer les autres membres | OK pour les règles **si** le lot contient la pierre tombale `miffsDeleted/{id}` et la suppression du code (DUR-1) ; la Miff devient orpheline (risque documenté) ; le client doit retirer les membres d'abord |

## 9. Hors règles (vérifier à part, pas avec l'émulateur de règles)

- App Check appliqué en mode strict sur Firestore (console) : une requête sans jeton est refusée. Checklist du PM : `docs/dev/app-check-strict.md`.
- Nombre d'appels de lecture des règles par opération : **automatisé depuis DUR-3** (`tests/rules/09-rule-reads.test.js`, calibration des limites de l'émulateur incluse ; un test échoue si une mesure est non concluante ou si la marge est inférieure à 2).
- Un seul `get joinCodes` par essai côté client ; limitation d'essais dans l'UI.
- Aucune clé de service ni secret dans le dépôt ou dans les apps.

## 10. Cas complémentaires (revue de la PR #2, ajoutés avec les tests)

Cas ajoutés pour que chaque protection des règles soit couverte par un test qui échoue si elle disparaît (voir `tests/tools/mutations.mjs` et `npm run test:mutation`). Même fixtures qu'en section 0.

| # | Cas | Attendu |
|---|---|---|
| X1 | Lecture d'une photo : `bob` (partage activé) lit `photos/alice` ; `carol` (partage désactivé) | OK ; KO (réciprocité) |
| X2 | `bob` ou `eve` modifie / supprime `joinCodes/K7M2QX9PDW4H` | KO |
| X3 | `eve` ou `bob` supprime `users/alice` | KO |
| X4 | `carol` (partage désactivé) crée `photos/carol` ; idem avec activation du partage dans le même batch | KO ; OK |
| X5 | Statut : `lastUpdateAt` (+1 jour, -3 jours), `locationAccess` inconnu, `escalationDueAt` (+3 jours, -3 jours, texte), `hasPhoto` non booléen | KO ; valeurs valides (-2 h, `foreground`, +2 h, null, true) : OK |
| X6 | `photos/bob` existe : départ, retrait par la créatrice et désactivation du partage sans supprimer la photo | KO ; avec la photo supprimée : OK |
| X7 | `entitlements` avec `active:false` et `expiresAt` futur : création d'une Miff avec la clé premium | KO ; avec `active:true` : OK |
| X8 | `bob` (simple membre) crée `places/p1` libre (type `trusted`) ; `alice` | KO ; OK |

## 11. Durcissement des règles (ticket `docs/tickets/durcissement-regles-firestore.md`)

Même jeu de données qu'en section 0. Chaque cas a un test dans `tests/rules/11-durcissement.test.js` **et** au moins une mutation dans `tests/tools/mutations.mjs` (le test doit passer au rouge quand la protection est retirée). Les identifiants de cette section portent une lettre minuscule (H1a, H1b...) : un cas = une ligne. « Lot final » = `[delete miffs/M1 + create miffsDeleted/M1 {deletedAt: heure serveur} + delete joinCodes/CODE_M1 + delete members/alice + delete status/alice + delete memberships/free]`.

### 11.1 DUR-1 : pierre tombale (ATK1) et code orphelin (ATK2)

| # | Cas | Attendu |
|---|---|---|
| H1a | `alice` envoie le lot final alors que `bob` et `carol` restent | OK ; ensuite la Miff n'existe plus, `miffsDeleted/M1` existe (seul champ `deletedAt`), le code est supprimé, la fiche de `bob` existe toujours |
| H1b | Même lot final **sans** la pierre tombale | KO ; avec la pierre tombale : OK |
| H1c | Après H1a : `bob` (survivant) libère sa fiche, son statut et son index (OK), puis tente `[miffs/M1 create + joinCodes + members/bob (owner) + memberships/free]` | **KO** (c'est ATK1) ; le même lot sous un identifiant neuf : OK |
| H1d | Pierre tombale : créée alors que la Miff existe encore ; avec un champ en trop ; avec `deletedAt` client ; sans champ ; créée par `alice`, `eve` ou `zoe` quand la Miff n'existe plus (état injecté) | KO partout ; le lot final valide : OK |
| H1f | `alice` supprime la Miff + pierre tombale + code **en gardant sa fiche membre**, puis recrée `[miffs/M1 create + joinCodes]` (aucune fiche membre écrite dans le lot) | **KO** (variante d'ATK1 non prévue au ticket : le contrôle de la pierre tombale est sur le document Miff lui-même) ; sous un identifiant sans pierre tombale, avec une fiche membre déjà présente : OK |
| H1g | Pierre tombale existante : lecture (get, list) par `alice`, `bob`, `eve`, non connecté ; mise à jour et suppression par `alice`, `bob`, `eve` | KO partout |
| H2a | Lot final **sans** suppression de `joinCodes/CODE_M1` | KO (c'est ATK2) ; code supprimé avant dans un lot séparé, puis lot final sans lui : OK |
| H2b | État injecté : `joinCodes/CODE_M2` existe, `miffs/M2` n'existe plus ; `eve` tente de rejoindre M2 avec ce code | KO ; rejoindre M1 avec son code valide : OK |

### 11.2 DUR-2 : miffAlive, réciprocité par statut publié (ATK3), retrait avec nouveau code (ATK4), photos

« Lot de retrait de `bob` » = `[delete members/bob + delete status/bob + delete photos/bob + update miffs/M1 {joinCode: NOUVEAU} + create joinCodes/NOUVEAU + delete joinCodes/ANCIEN]`. Tests dans `tests/rules/12-durcissement-dur2.test.js`.

| # | Cas | Attendu |
|---|---|---|
| H1e | Avant : `bob` lit `places` (liste, get), `members` (liste, get), `status` (liste, get), `photos/carol`. `alice` supprime M1 (lot final de DUR-1). Après : les mêmes lectures par `bob` (survivant) ; `get miffs/M1` ; puis `bob` quitte | Avant : OK. Après : KO partout ; `get miffs/M1` : OK, document introuvable (pas d'erreur) ; départ de `bob` : OK |
| H3a | `carol` active son partage **sans** créer son statut, puis lit `status/alice`, `status/bob`, la liste `status` ; puis `photos/alice`, `members`, `places` | KO pour les statuts ; **OK** pour la photo (décision PM : la réciprocité par statut publié ne vise que les statuts), les membres et les lieux |
| H3b | `carol` active son partage et crée son statut dans le même lot, puis lit `status/alice` et la liste | OK |
| H3c | `bob` lit les statuts (OK), révoque son partage (statut et photo supprimés dans le lot), relit | OK ; puis KO |
| H3d | `eve` (étrangère), `carol` (partage désactivé), `bob` après révocation lisent `photos/alice` | KO ; `bob` avant révocation : OK |
| H3e | État injecté : `status/carol` existe mais `carol` a son partage désactivé ; elle lit `status/alice` (get, liste) | KO (défense en profondeur) ; avec le partage activé : OK |
| H4a | `alice` retire `bob` : lot sans nouveau code ; lot avec seulement le nom modifié ; lot avec nouveau code créé mais ancien code laissé | **KO** (c'est ATK4) ; le lot complet : OK |
| H4b | Lot de retrait complet de `bob` | OK ; ensuite la Miff porte le nouveau code, l'ancien code n'existe plus, le nouveau pointe vers M1, `bob` n'est plus membre |
| H4c | Après H4b : `bob` libère son index orphelin (OK) puis tente de rejoindre avec l'**ancien** code | **KO** |
| H4d | Après H4b : `bob` libère son index puis rejoint avec le **nouveau** code ; `carol` lit le nouveau code dans `miffs/M1` | OK |
| H4e | `bob` (simple membre) tente le lot de retrait complet de `carol` ; suppression de la seule fiche `members/carol` par `bob` | KO |
| H4f | Départ volontaire de `bob` (lot de départ) | OK ; le code de la Miff et `joinCodes/CODE_M1` sont inchangés (décision PM Q1) |
| H5a | Photo : valide puis expirée (document injecté) lue par `bob` ; photo absente | OK puis KO ; absente : introuvable (OK, document inexistant) |
| H5b | `bob` crée sa photo avec `expiresAt` à +26 h, +7 jours, +24 h59, +24 h | KO, KO, OK, OK |
| H5c | Photo de `alice` valide : suppression par `bob`, `carol` ; expirée : par `eve`, par `bob`, par `carol` (partage désactivé) | KO, KO ; KO, OK, OK |
| H5d | Mêmes créations (+26 h KO, +7 jours KO, +24 h OK) et même illisibilité après expiration pour `bob` (gratuit) et `dave` (premium) | Identique pour les deux comptes |

### 11.3 Revue de sécurité de DUR-1/DUR-2 : le code d'une Miff pointe vers cette Miff

| # | Cas | Attendu |
|---|---|---|
| H7a | `eve` crée une Miff avec, comme `joinCode`, le code **existant d'une autre Miff** (celui de M2) | **KO** ; avec son propre code créé dans le lot : OK |
| H7b | `alice` renouvelle le code de M1 vers le code existant de M2 (et supprime l'ancien) | **KO** ; avec un nouveau code créé pour M1 : OK |
| H7c | `alice` renouvelle le code de M1 vers un code qui n'existe pas (non créé dans le lot) | KO |

## 12. Compléments de couverture issus de l'extension des mutations (DUR-3, critère R14)

Cas ajoutés pour tuer les **survivants** du test par mutation étendu (`tests/tools/mutations-dur3.mjs`) : avant eux, la protection visée pouvait être retirée de `firestore.rules` sans qu'aucun test ne passe au rouge. Tests dans `tests/rules/13-complements-dur3.test.js`, même jeu de données qu'en section 0. Chaque cas n'introduit qu'**un** défaut à la fois (sinon une autre règle refuserait à sa place) et finit par un contrôle positif quand c'est possible.

| # | Cas | Attendu |
|---|---|---|
| M19b | `bob` est retiré (fiche et statut supprimés) ; `eve` puis `alice` tentent de supprimer `users/bob/memberships/free` ; puis `bob` | KO, KO ; OK |
| R6b | `eve` lit `users/alice/memberships/free` ; `bob` liste `users/alice/memberships` ; `alice` lit les deux | KO, KO ; OK |
| S1b | Rejoindre avec un consentement : `revokedAt` renseigné (désactivé, activé), clé inconnue (désactivé, activé) ; puis consentement conforme | KO ×4 ; OK |
| S3b | `carol` active son partage avec `revokedAt` renseigné ; avec une clé inconnue ; conforme | KO ; KO ; OK |
| S6b | `bob` révoque avec `consentVersion` effacée, changée, `revokedAt` fourni par le client, ou `revokedAt` vide ; puis révocation conforme | KO ×4 ; OK |
| S12b | Statut : `batteryPct` −5, 50,5 ou « 80 » ; `placeId` vide ou de 41 caractères ; puis 0, 100, `null`, 40 caractères | KO ×5 ; OK |
| S16b | Photo : vide, clé inconnue, `createdAt` client, `expiresAt` déjà passé ; puis photo valide | KO ×4 ; OK |
| P9b | Lieu : rayon 100,5 ; lat −95 ; lng −200 ; adresse de 201 caractères ; priorité −1, 1000, 1,5, texte ; type « banane » et clé inconnue (création premium) ; puis bornes incluses | KO partout ; OK |
| P11b | `alice` crée `p1` avec `createdBy: bob` ; puis avec `createdBy: alice` | KO ; OK |
| P12b | Lieu `p1` : `createdAt` client, `updatedAt` client à la création, `updatedAt` client à la mise à jour ; puis valeurs serveur | KO ×3 ; OK ×2 |
| P2b | `dave` (premium) crée un lieu dont l'identifiant est `mauvais-id` ou de 21 caractères ; puis de 20 caractères | KO ×2 ; OK |
| C3b | `eve` crée une Miff sans l'index `memberships` ; puis le lot complet | KO ; OK |
| C13b | Création d'une Miff avec `joinCodes.createdAt` fourni par le client | KO |
| C14b | `alice` modifie `joinCodes/CODE_M1` : `miffId` vers M2, `createdAt`, clé inconnue ; puis renommage conforme (Miff + code) | KO ×3 ; OK |
| M6b | `alice` renomme M1 avec un nom vide, de 41 caractères, puis de 40 | KO ; KO ; OK |
| U1b | Profil : `termsAcceptedAt` ou `createdAt` client à la création ; `termsAcceptedAt` client à la mise à jour ; puis valeurs serveur | KO ×3 ; OK ×2 |
| U2b | Profil : nom vide ; `termsVersion` vide ou de 21 caractères (création et mise à jour) ; puis valeurs conformes | KO ×6 ; OK ×2 |
| A5b | `eve` crée `users/mallory` | KO |
| U5b | Appareil : clé inconnue, jeton vide ou de 513 caractères, `updatedAt` client, identifiant `a.b` ou de 65 caractères ; puis jeton de 512 et identifiant de 64 | KO ×6 ; OK ×2 |
| U6b | `eve` écrit `users/alice/devices/d1` ; puis `alice` | KO ; OK |

### 12.1 Cas hors émulateur (tests rapides, sans émulateur)

Pas des cas de règles : ils ne figurent pas dans le tableau ci-dessus (le test de couverture du plan ne lit que les cas de règles).

- **H6 (garde-fou de projet, critère R16)** : `tests/unit/project-guard.test.js` et `tests/unit/guard.test.js`. `.firebaserc` ne déclare aucun projet (un projet unique devient implicite, constaté) ; un script npm, un workflow ou un script shell qui appelle `firebase` sans `--project demo-*` ou autrement que pour l'émulateur fait échouer le test ; aucun workflow ne contient de déploiement, de jeton ni de compte de service ; le lanceur et un fichier de test refusent `GCLOUD_PROJECT=miff-3d283` et `ghostrun-app` (code 2, avant toute requête) ; le garde-fou lit le projet réellement annoncé par l'émulateur (`GCLOUD_PROJECT`, `FIREBASE_CONFIG`) et le compare à celui des tests.
