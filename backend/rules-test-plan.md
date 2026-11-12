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
| P2 | `alice` (gratuite) crée un lieu sous un identifiant automatique (hors `p1`, `p2` et `p3`) | KO |
| P3 | `alice` (gratuite) crée `places/p1` alors qu'il existe (= update avec champs verrouillés) | KO si elle change `type` vers `risk`/`createdBy`/`createdAt` ; modifier nom/rayon/coordonnées : OK |
| P4 | `alice` (gratuite) crée un lieu `type="risk"` (ID `p1`) | KO |
| P5 | `dave` (premium) crée 3 lieux (ID auto) dont 1 `risk` | OK |
| P6 | `frank` (premium expiré) crée un 2e lieu | KO |
| P7 | `bob` (membre) crée / modifie / supprime un lieu | KO |
| P8 | `alice` (gratuite) passe le `type` d'un lieu existant à `risk` ; puis, sur un lieu `risk` hérité, repasse à `trusted` (R-LIEU-6) | KO ; OK |
| P9 | `alice` : `radiusM=10` ou `5000`, `lat=95`, `lng=200`, nom vide | KO |
| P10 | `alice` supprime puis recrée `p1` | OK |
| P11 | `alice` modifie `createdBy` d'un lieu | KO |
| P12 | Lieu avec `createdAt` client | KO |
| P13 | `eve` lit `places` de M1 | KO |
| P14 | `alice` (gratuite, `p1` existe) crée `p2` puis `p3` en `trusted` ; puis `p4`, `p5`, un ID auto, `P2`, `P3` | OK ; OK ; KO ×5 (R-LIEU-5 : **3 lieux gratuits depuis le 2026-11-10**, avant : 2) |
| P14c | `alice` (gratuite) : `p3` en `risk` ; `p3` en `trusted` puis modifié (nom, rayon, catégorie) ; `p3` vers `risk` ; `p3` `risk` hérité vers `trusted` ; `createdBy` modifié ; `set` complet déguisé en `risk` ; suppression puis recréation de `p3` ; `p4` | KO ; OK ; KO ; OK ; KO ; KO ; OK ; OK ; KO |
| P14d | `alice` (gratuite, Miff sans lieu) crée `p1`, `p2`, `p3` dans UN lot (3 × 2 = 6 lectures de règles sur 20) ; lot de `p2` recréé + `p4` | OK ; KO en bloc, `p2` non écrit |
| P14e | `eve` (sans Miff) : lot « création de la Miff + `p1`, `p2`, `p3` » ; puis la Miff seule, puis les 3 lieux en un second lot ; puis `p4` | KO en entier (les règles de lieu lisent la Miff d'AVANT le lot) ; OK ; OK ; KO |
| P14b | `alice` (gratuite) crée `p2` en `risk` ; crée `p2` en `trusted` ; supprime `p1` puis le recrée | KO ; OK ; OK ; OK |
| P15 | Création sans `category`, ou avec `""`, `maison`, `Home`, `HOME`, ` home`, `home `, `danger`, `null`, `3`, liste, objet ; puis avec `home` (R-LIEU-2) | KO ×11 + sans clé ; OK |
| P15b | `dave` (premium) crée un lieu pour chacune des 7 catégories (`home`, `work`, `school`, `sport`, `relative`, `leisure`, `other`) | OK ×7 |
| P16 | `alice` change la `category` (valide), puis invalide (`maison`, `""`, `null`, `7`) ; un lieu sans `category` (hérité) : nom seul, puis nom + catégorie | OK ; KO ×4 ; KO ; OK |
| P17 | `dave` (premium) : `trusted` vers `risk` (+ catégorie), `risk` vers `trusted`, `type="banane"` (R-LIEU-6) | OK ; OK ; KO |
| P18 | `alice` (gratuite) : `trusted` vers `risk` (seul, ou avec catégorie et nom) ; nom + rayon + catégorie | KO ×2 ; OK |
| P19 | `frank` (abonnement expiré) : `p2` en `risk`, `p1` vers `risk`, lieu hors `p1`/`p2`/`p3`, `p2` puis `p3` en `trusted`, `p4` ; abonnement `active=false` : `p1` vers `risk` ; réactivé : OK ; `alice` (sans document d'abonnement) `p2` en `risk` | KO ×3 ; OK ; OK ; KO ; KO ; OK ; KO |
| P20 | `dave` (premium, 5 Miffs) crée en UN lot 5 lieux de même identifiant, un par Miff | OK |
| P20b | Même lot dont une Miff est celle d'`alice` | KO, rien n'est écrit dans les 4 autres |
| P20d | `frank` (ancien premium, 2 Miffs) : lot `p3` dans sa Miff A + `p4` dans sa Miff B ; puis `p3` dans les 2 Miffs (même identifiant, un document par Miff) en UN lot ; puis suppression des 2 copies | KO en bloc, rien d'écrit ; OK ; OK |
| P20c | Même lot avec une catégorie invalide dans une seule copie ; avec un identifiant invalide dans une seule copie | KO ; KO |
| P21 | `dave` modifie (`type="risk"`, catégorie) puis supprime ce lieu dans 5 Miffs, un lot chacune | OK ; OK |
| P22 | `bob` (membre) crée `p2`, crée `p3`, passe `p1` à `risk`, change sa catégorie, le supprime | KO ×5 |
| P23 | `alice` modifie `createdBy` ou `createdAt` en même temps que le type ou la catégorie | KO ×2 |
| P24 | Revue de sécurité (M5) : `frank` (ancien premium), créateur de 2 Miffs, crée `p1`, `p2` et `p3` dans CHACUNE ; `p4`, un identifiant libre ou le passage à `risk` | OK ×6 (6 lieux gratuits pour 2 Miffs : limite connue, à traiter dans le ticket Premium) ; KO ; KO ; KO |
| P25 | Échange de Miffs d'un lieu : 2 suppressions + 2 créations en UN lot ; même lot avec une Miff d'un autre créateur | OK ; KO, rien n'est écrit (au-delà de 4 + 4 le changement se fait en deux lots NON atomiques : budget figé dans `tests/unit/budgets.test.js`) |
| P26 | Abonnement dont `expiresAt` ou `active` n'a pas le bon type | KO (traité comme absent) |
| P27 | `alice` (gratuite) fait un `set` complet de `p1` existant avec `type="risk"` | KO |
| P28 | Contrats du client NON imposés par les règles : adresse nulle, rayon de 2000 m | OK (comportement figé) |

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
| U5 | (SRV-2R, jeton de notification : gabarit à 7 clés, section 26) `eve`, dont la session est `DEVICE_A`, crée `users/eve/devices/DEVICE_A` avec `platform:"ios"` ; `platform:"web"` | OK ; KO |
| U6 | `bob` lit `users/eve/devices/{DEVICE_A}` | KO |
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
| U1b | Profil : `termsAcceptedAt` ou `createdAt` client à la création ; `termsAcceptedAt` client à la mise à jour ; puis valeurs serveur | KO ×4 (dont la mise à jour avec l'heure serveur : (a) est figé, AUTH-B) ; OK ×1 |
| U2b | Profil : nom vide ; `termsVersion` vide ou de 21 caractères (création et mise à jour) ; puis valeurs conformes | KO ×6 ; OK ×2 |
| A5b | `eve` crée `users/mallory` | KO |
| U5b | Appareil (SRV-2R) : clé inconnue, jeton vide ou de 4097 caractères, `updatedAt` client, identifiant de 15 / 65 caractères ou avec un point (même quand c'est celui de la session) ; puis jeton de 4096 | KO ×7 ; OK |
| U6b | `eve` écrit `users/alice/devices/{DEVICE_A}` (alice a une session valide) ; puis `alice` | KO ; OK |

### 12.1 Cas hors émulateur (tests rapides, sans émulateur)

Pas des cas de règles : ils ne figurent pas dans le tableau ci-dessus (le test de couverture du plan ne lit que les cas de règles).

- **H6 (garde-fou de projet, critère R16)** : `tests/unit/project-guard.test.js` et `tests/unit/guard.test.js`. `.firebaserc` ne déclare aucun projet (un projet unique devient implicite, constaté) ; un script npm, un workflow ou un script shell qui appelle `firebase` sans `--project demo-*` ou autrement que pour l'émulateur fait échouer le test ; aucun workflow ne contient de déploiement, de jeton ni de compte de service ; le lanceur et un fichier de test refusent `GCLOUD_PROJECT=miff-3d283` et `ghostrun-app` (code 2, avant toute requête) ; le garde-fou lit le projet réellement annoncé par l'émulateur (`GCLOUD_PROJECT`, `FIREBASE_CONFIG`) et le compare à celui des tests.

## 13. AUTH-B : consentement facultatif (b) « statistiques et plantages » et acceptation (a) des CGU (ticket `docs/tickets/authentification.md`, §8, §10, §15)

Même jeu de données qu'en section 0 : `alice`, `bob`, `carol`, `dave`, `zoe` ont un profil `users/{uid}` à six clés sans bloc de consentement ; `eve` et `frank` n'en ont pas (dans les fichiers 07, 08 et 13 ; dans tous les autres fichiers ils en ont un, car créer ou rejoindre une Miff exige un profil depuis AB18). Le bloc s'appelle `analyticsConsent` (quatre clés `enabled`, `consentedAt`, `consentVersion`, `revokedAt`, comme `members.sharing` ; version au format `AAAA-MM-vNN`). Tests dans `tests/rules/14-consentement-statistiques.test.js` ; chaque cas a au moins une mutation dans `tests/tools/mutations.mjs`. Critères du ticket couverts entre parenthèses.

| # | Cas | Attendu |
|---|---|---|
| AB1 | `eve` crée son profil **sans** bloc de consentement (compte sans choix enregistré) (S12, X19) | OK ; aucun consentement n'est enregistré (absence = pas de consentement) |
| AB2 | `eve` crée son profil avec (b) refusée : `{enabled:false, consentedAt:null, consentVersion:null, revokedAt:null}` (S12, X19) | OK ; le bloc est enregistré tel quel |
| AB3 | `eve` crée son profil avec (b) acceptée : `enabled:true`, `consentedAt` = heure serveur, `consentVersion:"2026-10-v01"`, `revokedAt:null` (X19, X13, C11) | OK ; `consentedAt` = `termsAcceptedAt` (même instant serveur) |
| AB4 | Création avec un consentement falsifié : `consentedAt` ancien, futur ou pris sur l'horloge du client, absent ; version absente, nulle ou vide ; date de retrait renseignée ; état « non consenti » portant une date, une version ou un retrait (X19) | KO partout |
| AB5 | Création : bloc booléen, texte, nul, vide ; clé en trop ; clé manquante ; `enabled` texte ou nombre ; version non texte, sans zéro de tête, sans `v`, au mois 13 ou trop longue (X19) | KO partout ; la forme valide est acceptée |
| AB6 | (a) : création sans `termsAcceptedAt`, sans `termsVersion`, sans les deux (même avec (b) acceptée) ; `termsAcceptedAt` du client, nul ou sur l'horloge du client ; `termsVersion` vide, de 21 caractères ou non texte (X13, C14) | KO partout ; (a) + (b) valides : OK, `createdAt` = `termsAcceptedAt` |
| AB7 | Création du profil d'un autre uid (`eve` vers `users/frank`, avec ou sans (b)), écrasement de `users/alice`, création sans connexion (usurpation d'uid) | KO partout |
| AB8 | Mise à jour du consentement d'autrui : `bob`, `eve`, `alice` (créatrice de la Miff) ou un visiteur sans connexion active ou retire le consentement de `alice` ou `carol` ; `set` complet par un tiers | KO partout ; le consentement de `carol` reste intact |
| AB9 | Activer (b) soi-même (Réglages) : `dave` (profil sans bloc) et `zoe` (bloc « non consenti ») ; falsifications (date du client, sans date, sans version, date de retrait, clé en trop, « non consenti » daté) ; activation + changement de prénom dans la même écriture (X19, X20) | OK avec heure serveur et version ; falsifications KO ; écriture combinée OK |
| AB10 | Retirer (b) : retrait qui efface la date ou la version du consentement, les change, date de retrait du client ou absente, suppression pure du bloc, « retrait » d'un profil qui n'a jamais consenti (X20) | KO partout ; le retrait valide (`enabled:false`, `revokedAt` = heure serveur, date et version conservées) : OK |
| AB11 | Réactiver (b) après un retrait : `revokedAt` vidé, nouvelle date serveur, version identique ou plus récente (`v09` vers `v10`, `2026-09` vers `2026-10`) (X20) | OK |
| AB12 | Rétrogradation de la version : réactivation avec une version plus ancienne que la dernière enregistrée (`v02` vers `v01`, mois ou année antérieurs) ; réécriture d'un consentement actif sans retrait préalable (autre version, version plus récente, même version) | KO partout ; rien n'est écrit |
| AB13 | Mise à jour : `termsAcceptedAt` (ré-horodatage, antidatage), `termsVersion` (changement, rétrogradation), les deux ensemble, `createdAt`, champs inconnus (`email`, `isPremium`, `analytics`, `phone` glissé avec une modification valide) (X13, X6) | KO partout ; `termsVersion` et `termsAcceptedAt` inchangés |
| AB14 | Non-régression : un profil à six clés et un profil avec bloc modifient prénom, nom, avatar sans toucher au consentement ; un changement de profil ne fait pas passer un consentement falsifié | OK ; bloc identique ; falsification KO |
| AB15 | Suppression du profil : par un tiers (`bob`, `alice`) ; par `carol` elle-même (le consentement disparaît avec) ; suppression d'un profil inexistant par `eve` (refus du panneau d'un nouvel utilisateur social : rien n'a été écrit) ; recréation ensuite (C12, C14) | KO pour les tiers ; OK pour le propriétaire et pour un profil inexistant ; recréation OK |
| AB16 | Lecture du consentement : `carol` lit son profil ; `bob` (même Miff), `alice`, `eve`, un visiteur le lisent ; liste des profils et requête sur `analyticsConsent.enabled` | OK pour `carol` seule ; KO ailleurs |
| AB17 | Minimisation : création avec `birthDate`, `dateOfBirth`, `age`, `ageRange`, `email`, `phone`, `lat`, `location` ; clé en trop **dans** le bloc de consentement (S13, X6, X21) | KO partout ; le profil valide est accepté |
| AB18 | **Pas de Miff sans profil (décision PM du 2026-10-24).** `eve` (sans profil `users/eve`) crée une Miff : lot complet de 4 écritures ; idem pour `dave` (premium) dont le profil est supprimé | KO ; avec le profil présent : OK (gratuite et premium) |
| AB19 | `eve` sans profil rejoint M1 avec le bon code (lot de 2 écritures) | KO ; avec le profil présent : OK |
| AB20 | Profil créé **dans le même lot** (`getAfter`) : profil + Miff ; profil + adhésion. Lot dont le profil n'a pas (a) valide (sans CGU, CGU datées par le client) ou qui écrit le profil d'un autre uid | OK pour les lots valides ; KO pour les autres |
| AB21 | Profil existant mais **sans** `termsAcceptedAt` ou sans `termsVersion` (document ancien ou injecté) : création de Miff, adhésion | KO ; une fois (a) présente : OK |
| AB22 | Sans profil : `bob` quitte une Miff, `alice` retire `carol` (code renouvelé), `alice` supprime la Miff (lot final) | OK partout (le départ n'est jamais bloqué) |

## 14. ONB-B : téléphone obligatoire (`users/{uid}.phone` et copie `members/{uid}.phone`) (ticket `docs/tickets/onboarding.md`, §6.4, §8, critères B1 à B3)

Décision du PM (Q1, 2026-10-24) : numéro obligatoire, pays + numéro en **E.164** sans vérification ni unicité ; visible **uniquement** des membres des Miffs de la personne, via la copie dans la fiche de membre (N1, option A : aucune règle de lecture nouvelle). La clé s'appelle `phone` (chaîne `+` suivie de 7 à 15 chiffres, premier chiffre non nul, regex `^[+][1-9][0-9]{6,14}$`). Tests dans `tests/rules/15-telephone-onb-b.test.js` ; mutations dans `tests/tools/mutations-onbb.mjs`. Même jeu de données qu'en section 0, **toutes les fiches de départ et tous les profils portent désormais `phone`** ; `eve` et `frank` n'ont pas de profil dans ce fichier. Les cas AB13 et AB17 de la section 13 sont adaptés : `phone` n'est plus une clé inconnue, mais un numéro de forme nationale reste refusé.

| # | Cas | Attendu |
|---|---|---|
| PH1 | Création du profil avec un numéro E.164 valide (`+33612345678`, 7 chiffres `+1234567`, 15 chiffres, `+242061234567`) | OK ; valeur enregistrée telle quelle |
| PH2 | Création du profil : numéro absent, nul, vide, sans `+`, avec zéro après `+`, 6 ou 16 chiffres, espaces, tirets, parenthèses, point, lettres, double `+`, espace ou retour à la ligne en début ou fin, chiffres arabo-indiques, 200 caractères, nombre, booléen, liste ; clés de contact en trop (`phoneCountry`, `phoneNumber`) | KO partout ; le même profil avec un numéro valide : OK |
| PH3 | Usurpation d'uid : `eve` crée le profil de `frank`, écrase celui de `alice` ; `bob` change le numéro de `alice` ; visiteur sans connexion | KO partout ; le numéro de `alice` est intact |
| PH4 | Mise à jour du profil : numéro modifiable par son propriétaire (avec ou sans changement de prénom) ; tous les formats invalides, suppression de la clé, valeur nulle, clé en trop | OK pour les numéros valides ; KO pour le reste, numéro inchangé |
| PH4b | Compte antérieur à ONB-B (profil **sans** numéro) : un changement de prénom reste accepté ; l'ajout d'un numéro valide est accepté (étape 2b) ; numéro invalide, nul, ou ajout avec réécriture des CGU refusés | OK / KO comme indiqué |
| PH5 | Lecture : `bob` lit son profil ; `carol` (même Miff), `alice` (créatrice), `eve`, un visiteur lisent celui de `bob` ; liste des profils ; recherche par numéro (collection et groupe de collections) | OK pour le propriétaire seul ; KO partout ailleurs |
| PH6 | Création de la fiche (adhésion par code et création de Miff) avec un numéro : absent, invalide (toute la liste de PH2), valide **mais différent du profil** | KO partout, aucune fiche ni Miff écrite ; copie identique au profil : OK |
| PH6b | Profil créé **dans le même lot** (`getAfter`) : copie identique OK, copie différente KO, profil du lot sans numéro KO ; profil injecté avec un numéro invalide + fiche identique KO ; compte antérieur sans numéro : adhésion KO, puis OK avec « ajout du numéro au profil + adhésion » dans un lot | OK / KO comme indiqué |
| PH7 | Fiche avec une clé en trop (`phoneCountry`, `email`, `lat`) | KO ; la fiche valide : OK |
| PH8 | Mise à jour de la fiche : numéro différent du profil, invalide, supprimé, avec changement de rôle, clé en trop ; modification par un autre membre ou par la créatrice | KO partout, numéro inchangé ; après changement du profil, la fiche peut suivre : OK ; changement de prénom seul : OK |
| PH8b | Fiche antérieure sans numéro : prénom modifiable ; ajout d'un numéro différent du profil ou invalide KO ; ajout du numéro du profil OK | OK / KO comme indiqué |
| PH8c | Profil injecté avec un numéro invalide : mise à jour de la fiche vers ce même numéro | KO (la forme est contrôlée en plus de l'égalité) |
| PH9 | Mise à jour atomique profil + fiche dans un même lot : fiche avec un autre numéro, numéro invalide, profil invalide | KO, **rien n'est écrit** (profil compris) ; lot cohérent : OK |
| PH9b | Compte premium dans 15 Miffs : profil + 15 fiches dans un lot ; une fiche incohérente dans le lot | OK pour le lot cohérent ; KO et rien d'écrit sinon |
| PH10 | Lecture du numéro dans la fiche : `bob` (partage actif) et `carol` (partage désactivé) lisent la fiche de `alice` et la liste ; `eve`, `zoe` (autre Miff), visiteur, groupe de collections `members`, recherche d'un numéro dans toutes les Miffs | OK pour les membres ; KO pour les autres |
| PH10b | Membre qui quitte, membre retiré (code renouvelé) : plus aucune lecture, numéros supprimés avec les fiches | KO après le départ ou le retrait |
| PH10c | Miff supprimée : survivant (`bob`) lit la fiche ou la liste | KO (les données existent encore physiquement) |
| PH11 | Limites assumées : divergence passagère profil/fiche acceptée ; un nouvel arrivant qui connaît le code lit tous les numéros ; aucune unicité | Comportement figé par le test |

## 15. MIFF-0 : lot d'entrée « Rejoindre et partager » / « Créer et partager » (ticket `docs/tickets/miff.md`, §5.4, §9, décision QM2)

Un lot atomique contient : **adhésion** `members/{moi}` (partage **activé** : `consentedAt` = heure serveur, `consentVersion`, `revokedAt` vide) + index `memberships` + **statut initial** `status/{moi}` (`presence = "unknown"`, `placeId` nul, horodatages du serveur) ; **création** : Miff + code + fiche du créateur (partage activé) + index + statut initial. Aucune modification de règle n'est nécessaire (le statut exige le partage actif **après** le lot : `getAfter`). Tests dans `tests/rules/16-lot-entree-miff0.test.js` ; mutations dans `tests/tools/mutations-onbb.mjs`.

| # | Cas | Attendu |
|---|---|---|
| EN1 | `eve` rejoint M1 : fiche (partage activé) + index + statut initial, un lot de 3 ; même lot dans un autre ordre (`frank`) | OK ; consentement tracé (heure serveur, version), statut `unknown`, numéro copié |
| EN1b | Idem avec le profil créé dans le même lot (4 écritures) | OK |
| EN2 | `eve` crée une Miff : Miff + code + fiche du créateur (partage activé) + index + statut initial, un lot de 5 | OK |
| EN2b | Idem avec le profil dans le même lot (6 écritures) ; idem en premium (clé = identifiant de la Miff) | OK |
| EN3 | Adhésion : statut invalide (batterie 150, présence inconnue du schéma, clé en trop, `updatedAt` du client, statut d'un autre membre) ; fiche sans partage mais statut publié ; consentement daté par le client ou sans version ; code d'une autre Miff ; index absent ou vers une autre Miff ; numéro différent du profil | KO, **aucun des documents du lot n'existe** ; le lot corrigé : OK |
| EN3b | Lot sans statut initial : la fiche est créée (partage actif) mais la lecture des statuts est refusée (ATK3) tant que le statut n'est pas publié ; publié ensuite : OK | OK / KO comme indiqué |
| EN4 | Création : statut invalide (`locationAccess` inconnu, texte trop long), statut d'un autre, fiche sans partage, consentement daté par le client, code absent, index absent, propriétaire usurpé, numéro différent du profil | KO, **ni Miff, ni code, ni fiche, ni index, ni statut écrits** ; le lot corrigé : OK |
| EN5 | Les quatre valeurs de `locationAccess` sont acceptées ; une présence autre que `unknown` au premier statut est acceptée par les règles (convention du client, limite documentée) | OK |
| EN6 | Après le lot : le nouveau membre lit les statuts, les autres lisent le sien, `carol` (partage désactivé) toujours pas ; un créateur est lu par le membre qui le suit | OK / KO comme indiqué |

Lectures de règles de ces lots : voir `rules-test-report.md` (section ONB-B / MIFF-0) et `data-model.md` §12.

## 16. MIFF-1 : longueur du nom d'une Miff (ticket `docs/tickets/miff.md`, §5.2, décision QM6)

Constat à établir avant le développement Android : comment les règles comptent la longueur d'un nom (`isStr(d.name, 1, 40)`) avec des emojis et des lettres accentuées. **Aucune règle n'est modifiée** : ces cas documentent un comportement existant dont le client dépend (il limite à 40 unités UTF-16, `String.length` en Kotlin).

| # | Cas | Attendu |
|---|---|---|
| ND1 | `eve` crée une Miff dont le nom a 41, puis 40 caractères ASCII | KO, puis OK |
| ND1b | Le créateur renomme (Miff + nom du code, un lot) : 41, puis 40 caractères | KO, puis OK |
| ND2 | Nom de 20 emojis (40 unités UTF-16, 20 code points, 80 octets UTF-8) | OK |
| ND2b | Nom de 40 lettres « é » (40 unités, 80 octets UTF-8) : les règles comptent des caractères, pas des octets | OK |
| ND3 | Mesure d'information : 30 emojis (30 code points, 60 unités UTF-16) | **Refusé** constaté : les règles comptent des unités UTF-16 (le cas n'échoue jamais, il imprime le constat) |

## 17. MIFF-2 : renouveler le code, retirer, quitter, supprimer la Miff (ticket `docs/tickets/miff.md`, §5.3, §5.6, critères N, R, Q, S)

Les lots sont ceux que le client Android écrit (`MiffWrites.kt`) : mêmes chemins, mêmes clés, même ordre. **Aucune règle n'est modifiée** : ces cas figent le contrat dont le client dépend (dont deux refus qui expliquent des choix du client : supprimer un document **absent** d'un code ou d'un index est refusé, donc le client lit leur existence avant). Tests dans `tests/rules/18-miff2-retirer-quitter-supprimer.test.js` ; mesures de lectures dans `09-rule-reads.test.js` (`READS-delete-miff-final`, `READS-delete-places`, `READS-remove-member-gone`).

| # | Cas | Attendu |
|---|---|---|
| MN1 | `alice` renouvelle le code (lot de 3 : Miff, nouveau code, suppression de l'ancien) ; `bob` reste membre et lit le nouveau code ; `eve` essaie l'ancien code puis le nouveau | OK ; ancien code KO pour `eve`, nouveau OK |
| MN2 | Un membre simple ; lot sans nouveau code ; lot sans suppression de l'ancien ; nouveau code vers une autre Miff ; nom différent ; code mal formé ; créatrice d'une autre Miff | KO, **rien n'est écrit** ; le lot correct : OK |
| MN3 | Deux renouvellements de suite ; lot calculé sur un code périmé | OK ; KO pour le lot périmé |
| MR1 | `alice` retire `bob` (statut et photo présents) : lot de 6 | OK ; fiche, statut, photo supprimés, code renouvelé ; `bob` ne lit plus et ne revient pas avec l'ancien code |
| MR2 | Retrait d'une cible déjà partie (documents absents) | OK, le code est renouvelé quand même |
| MR3 | Retrait d'un membre sans statut ni photo, puis du dernier proche | OK ; la créatrice reste seule |
| MR4 | Lot sans changement de code ; ancien code conservé ; nouveau code absent ou vers une autre Miff ; statut oublié ; photo oubliée alors qu'elle existe | KO, `bob` toujours là, code inchangé ; le lot correct : OK |
| MR5 | `bob` ou `zoe` retire `carol` ; `alice` se retire elle-même avec code renouvelé | KO |
| MR6 | Après le retrait, l'index de `bob` reste : la créatrice ne peut pas le supprimer, `bob` ne peut pas rejoindre une autre Miff avant, puis le peut après avoir libéré son index | KO / KO / OK / OK |
| MR7 | Code courant absent (incohérence) : lot avec suppression de l'ancien code, puis sans | KO, puis OK |
| MQ1 | `bob` quitte (statut et photo présents) : lot de 4 ; le code n'est pas renouvelé ; il rejoint ensuite une autre Miff | OK |
| MQ2 | `carol` quitte avec partage arrêté (ni statut ni photo) | OK |
| MQ3 | Supprimer un index absent ; lot de 4 avec index absent ; lot de 3 sans index | KO ; KO ; OK |
| MQ4 | `alice` (créatrice) quitte sans supprimer la Miff ; lot sans statut | KO, rien n'est écrit |
| MS1 | Suppression complète : retrait de `bob`, retrait de `carol`, lieux, lot final de 7 (Miff, pierre tombale, code courant, statut, photo, fiche, index) ; relecture, recréation avec le même identifiant, création d'une autre Miff | OK ; Miff illisible ; recréation KO (pierre tombale) ; autre Miff OK (index libéré) |
| MS2 | Reprise après chaque étape : lieu déjà supprimé, membre déjà parti, relancés | OK |
| MS2b | Lot final sans index (non trouvé) : Miff illisible ensuite, l'index se supprime seul | OK |
| MS3 | Lot final sans pierre tombale, sans suppression du code, avec un retrait de membre dedans, par un membre simple, avec un autre code | KO, rien n'est supprimé |
| MS3b | **Limite constatée** : lot final accepté malgré des fiches restantes ; le survivant lit « Miff inexistante » (document absent, pas une erreur) et ne lit ni membres ni statuts | OK / lecture vide / KO |
| MS4 | Lot final sans statut ni photo de la créatrice (partage arrêté) | OK |
| MS5 | Cinq lieux supprimés dans un lot par la créatrice ; par un membre ; lieu absent | OK ; KO ; OK |
| MS6 | Code courant absent : lot final avec sa suppression, puis sans | KO, puis OK |
| MS7 | Après la suppression : arrivée avec le dernier code connu ou un ancien code | KO |

## 18. R-LIEU-POS : dernière position connue (ticket `docs/tickets/lieux-de-confiance.md` v3 §6.9, §7, risques R16 à R18 ; décisions du PM du 2026-11-01) — DÉPLOYÉ (2026-11-04 d'après l'en-tête de `firestore.rules` ; fichier de `main` redéployé par le PM le 2026-11-10 : information du PM, non vérifiable par l'agent)

Un document `miffs/{id}/positions/{uid}` = `{ lat, lng, measuredAt }` par membre et par Miff, **séparé du statut** (le statut garde un schéma fermé sans coordonnées : S11, S19). Écrit par son auteur seul, **Miff existante**, avec partage actif **et** texte de consentement de version 3 ou plus, au format **`AAAA-MM-vNN` de largeur fixe** (2 lectures de règles : Miff + `getAfter` de la fiche). Lu (`get` uniquement, jamais de liste) par soi-même, ou par un autre membre qui remplit les conditions des statuts (Miff existante, partage actif, statut publié) **plus** sa propre position publiée (réciprocité **formelle** : elle prouve qu'un document existe, pas que la position est vraie) et si la cible partage encore. Le créateur n'a aucun droit de plus. Supprimé **dans le même lot** que le statut et la photo (arrêt du partage, départ, retrait). Tests dans `tests/rules/19-derniere-position.test.js` ; mutations dans `tests/tools/mutations-position.mjs` ; mesures de lectures dans `tests/rules/09-rule-reads.test.js` (`READS-position-*`). Fixtures : `bothPublished` = alice et bob ont le texte v3 et une position (leur statut existe déjà dans le jeu de départ).

| # | Cas | Attendu |
|---|---|---|
| DP1 | `bob` (partage actif, texte v3) écrit, remplace puis modifie sa position | OK ; un seul document dans `positions` (aucun historique) |
| DP2 | Version du consentement : `v1`, `2026-10-v1`, `2026-10-v2`, `2026-10-v02`, `2026-11-v01`, `2026-09-v1` ; formats invalides (`v3`, `2026-11-v3` sans zéro de tête, `2026-11-v100`, `2026-13-v03`, espace, retour à la ligne, NUL, chiffres non latins) ; puis `2026-11-v03`, `2026-10-v03`, `2027-01-v04`, `2026-12-v09`, `2026-12-v10`, `2027-02-v99` | KO pour les deux premiers groupes ; OK pour le dernier (contrat de nommage : `AAAA-MM-vNN` de largeur fixe, NN de 03 à 99, quel que soit le mois ; sonde Y1) |
| DP2b | Version du consentement de la fiche `null` ou numérique | KO |
| DP3 | Partage jamais activé (`carol`) ou arrêté (`bob`, `revokedAt` renseigné), même avec le texte v3 | KO |
| DP4 | `bob` écrit `positions/alice` ; `alice` (créatrice) crée ou modifie `positions/bob` ; `eve`, visiteur, membre d'une autre Miff (`zoe`) | KO partout |
| DP5 | Clé en trop (`accuracyM`, `speed`, `altitude`, `updatedAt`, `history`, `address`, `placeId`), clé manquante (chacune des trois), `lat` > 90 ou < -90, `lng` > 180 ou < -180, NaN, infini, texte, `null`, booléen | KO ; bornes incluses (90, -90, 180, -180, 0) : OK |
| DP5b | `measuredAt` à -25 h, -3 jours, +10 min, +1 jour ; `null`, texte, nombre, booléen, 1970 | KO ; -23 h, +4 min, heure serveur : OK |
| DP6 | `bob` et `alice` (texte v3, position publiée, statut publié) lisent la position l'un de l'autre | OK ; la valeur lue est celle enregistrée |
| DP6b | `bob` sans sa propre position publiée ; sans son statut publié ; partage arrêté (état injecté) ; fiche retirée | KO pour chacun ; OK dès que la condition est rétablie |
| DP6c | `carol` (partage désactivé), `eve`, `zoe` (autre Miff), visiteur ; Miff supprimée (survivant) | KO partout |
| DP6d | Lecteur au consentement antérieur (texte sans position), statut publié, sans position : il ne lit rien ; il ne peut pas écrire sa position ; avec le texte v3 et sa position, il lit | KO, KO, puis OK |
| DP7 | La créatrice lit celle d'un membre dans les mêmes conditions ; sans sa propre position, ou partage arrêté, ou dans une autre Miff : aucune exception | OK dans les conditions communes ; KO sinon |
| DP8 | `list` de `positions` (membre qui partage, créatrice, requête filtrée, groupe de collections `positions`, étranger, visiteur) | KO partout |
| DP9 | On relit toujours sa propre position, même partage arrêté ; pas celle des autres dans ce cas | OK / KO |
| DP10 | Une position mesurée il y a plusieurs mois reste lisible : les règles n'appliquent pas de durée (le masquage après 24 h est un affichage du client, date toujours affichée) | OK (comportement figé, voir `data-model.md` §2.12) |
| DP11 | `bob` arrête son partage : lot sans la suppression de la position ; lot avec la position seule ; lot complet (fiche + statut + photo + position) | KO, KO, OK ; la position a disparu et un autre membre ne la lit plus |
| DP11b | Arrêt du partage sans aucune position enregistrée, lot de 3 comme avant | OK (rien à supprimer) |
| DP12 | `bob` quitte la Miff : lot sans la position ; lot complet de 5 | KO ; OK |
| DP12b | `alice` retire `bob` : lot sans la position ; lot de 7 (position comprise, nouveau code) | KO ; OK |
| DP12c | `alice` supprime la Miff : lot final sans sa position ; avec | KO ; OK |
| DP13 | Suppression d'une position par un autre membre, un étranger, un membre d'une autre Miff, un visiteur ; par la créatrice (retrait) ; par soi ; par un ex-membre dont la fiche a disparu | KO pour les quatre premiers ; OK pour les trois derniers |
| DP13b | Le droit de suppression de la créatrice ne lui donne ni l'écriture de la position d'un membre ni sa lecture sans les conditions communes | KO |
| DP14 | Après un arrêt, réactivation avec l'ancien texte : la présence repart, la position reste refusée ; arrêt puis réactivation avec le texte v3 : position acceptée | KO, puis OK |
| DP14b | `carol` active le partage (texte v3) et écrit statut + position en UN lot ; même lot avec l'ancien texte (rien d'écrit) ; `bob` révoque et écrit sa position dans le même lot | OK ; KO ; KO |
| DP15 | Aucune coordonnée ailleurs : statut (`lat`, `lng`, `measuredAt`), fiche de membre, profil, `positions/bob` hors d'une Miff, `users/bob/positions/x`, `places`, `photos`, chemin `position` | KO partout |
| DP16 | Un compte premium écrit sa position pour 8 Miffs dans UN lot | OK (2 lectures de règles par Miff : 16 sur 20) |
| DP16b | Le même pour 11 Miffs (22 lectures) | KO en bloc, rien d'écrit (limite de 20) : le client écrit 8 Miffs au plus par lot |
| DP6e | Sonde Y4 : position de reliquat d'un non-membre (`zoe`) ou d'un membre dont le partage est arrêté (`carol`) ; contrôle : un membre qui partage reste lisible | KO ; KO ; OK |
| DP17 | Sonde X4 / Y5 (assumé) : la créatrice supprime la position d'un membre à tout moment, hors lot de retrait ; elle ne gagne ni écriture ni lecture | OK ; KO ; KO |
| DP18 | Sonde X2 : position écrite ou modifiée sous une Miff supprimée par un membre survivant ; il peut effacer la sienne | KO ; KO ; OK |
| DP19 | Aucun chemin de suppression de fiche (soi, créatrice qui retire, créatrice qui part) ni d'arrêt du partage (seul, sans photo, avec une position écrite dans le lot, avec la position d'un autre) ne laisse la position | KO partout, aucune position supprimée ; le lot complet : OK |
| DP20 | Contrat client : arrêt du partage dans 6 Miffs en UN lot (3 × 6 = 18), départ de 4 Miffs (4 × 4 = 16) | OK (budgets figés dans `tests/unit/budgets.test.js` : l'émulateur accepte aussi des lots plus gros, grâce à son cache) |

Cas touchés dans les sections précédentes : `leaveOps` (quitter) passe à 5 écritures et `removeMemberOps` (retrait) à 7 (la suppression de la position y est ajoutée : S4, M-retrait, H5 et les autres cas qui les réutilisent restent valables, la position n'existant pas dans le jeu de départ) ; les mesures `READS-leave`, `READS-remove-member`, `READS-delete-miff` et `READS-consent-off` incluent la suppression de la position. Les cas S11 et S19 (statut sans coordonnées) restent inchangés.

## 19. R-LIEU-3 : renouvellement d'un consentement de partage actif (acceptée par le PM, confirmée le 2026-11-01 « avant le Tracking ») — DÉPLOYÉ (2026-11-04 d'après l'en-tête de `firestore.rules` ; fichier de `main` redéployé par le PM le 2026-11-10 : information du PM, non vérifiable par l'agent)

Nouvelle transition de `members/{uid}.sharing` : **actif -> actif** avec un texte plus récent, **sans arrêter le partage** (donc sans perdre statut, photo ni position, et sans rien exiger dans le lot). Conditions : `consentedAt` = heure serveur, `consentVersion` au format **`AAAA-MM-vNN` de largeur fixe** (sinon l'ordre alphabétique n'est pas l'ordre chronologique) et **strictement supérieure** à l'ancienne, **jamais de sortie de la couverture « position »** (si l'ancienne version couvre la position, la nouvelle aussi), `revokedAt` nul, les quatre clés exactes. **0 lecture de règles.** Tests dans `tests/rules/20-renouvellement-consentement.test.js` ; mutations dans `tests/tools/mutations-position.mjs` (RC-*) ; mesure `READS-consent-renew`.

| # | Cas | Attendu |
|---|---|---|
| RC1 | `bob` (partage actif, texte `2026-10-v2`) renouvelle avec `2026-11-v03`, avec photo et position déjà enregistrées | OK ; version et date mises à jour, `enabled` vrai, `revokedAt` nul ; statut, photo et position conservés |
| RC1b | Régression : `2026-10-v1` -> `2026-10-v2` -> `2026-11-v03` -> `2026-12-v04` ; position refusée en v2, permise dès la v3, conservée au renouvellement suivant | OK ; KO puis OK |
| RC1c | Renouvellement et écriture de la position dans le même lot | OK |
| RC2 | Même version, version plus ancienne (autre mois ou même mois), texte vide, `null`, nombre | KO ; `2026-12-v04` : OK |
| RC2b | `consentedAt` du client, conservé ou nul ; `revokedAt` renseigné ; clé en trop, clé manquante, bloc nul | KO partout |
| RC3 | `enabled=false` avec une version supérieure, sans purge (seul, avec statut et photo supprimés, ou avec la position supprimée mais déguisé en renouvellement) | KO : le renouvellement ne contourne pas l'arrêt du partage |
| RC4 | Renouvellement par la créatrice, un autre membre, un étranger, un visiteur ; partage arrêté (désactivé -> désactivé) ; l'activation d'un partage arrêté reste celle d'avant | KO ; OK pour l'activation |
| RC5 | Régression sur `2026-10-v2` : arrêt sans purge KO, arrêt avec purge OK, réactivation OK | KO ; OK ; OK |
| RC6 | Sondes X1, X1b, X1c (I1) : `2026-11-v10` -> `2026-11-v2` (alphabétiquement plus haut), `v9` sans zéro, texte libre `zzzzzzzz`, `v1000`, retour à la ligne, mois 13, `v09` plus ancienne ; puis `v11`, `2027-01-v12` ; `2026-10-v2` -> `2026-11-v02` | KO pour les sept premiers ; OK pour les suivants |
| RC7 | Renouvellement dans deux Miffs en un lot | OK |
| RC8 | Sondes X1, X1b (I1) : depuis un texte qui couvre la position, renouveler vers `2027-01-v01`, `v02`, `2026-12-v00`, `2027-01-v1`, `v2` ; vers `2027-01-v04` ; depuis un texte sans position vers un autre sans position | KO ×5 ; OK (la position reste écrivable et lisible) ; OK |
| RC9 | Sonde Y6 : plusieurs mises à jour de la même fiche dans un lot ; la seconde sort de la couverture « position » | OK pour l'aller-retour qui reste couvert ; KO pour la sortie, rien de changé |

## 21. SESS-1 : un compte, un seul appareil (mécanisme validé par le PM le 2026-11-01) — temps 1 DÉPLOYÉ (2026-11-05 d'après l'en-tête de `firestore.rules` ; fichier de `main` redéployé par le PM le 2026-11-10 : information du PM, non vérifiable par l'agent) ; temps 2 (`deviceIdRequired() = true`) NON DÉPLOYÉ

Document de session `users/{uid}/session/active` `{ deviceId, startedAt }` (propriétaire seul, schéma fermé, chemin fixe) ; clé `deviceId` **facultative** dans le statut et la dernière position (**temps 1**), **obligatoire** au **temps 2** (une seule ligne basculée : `deviceIdRequired()`), égale à celle de la session. +1 lecture de règles par statut / position écrits avec la clé ; rien d'autre ne lit la session. Mesure de COHÉRENCE, pas de sécurité. Tests : `tests/rules/22-session-appareil.test.js` (temps 1, règles telles que livrées), `23-session-appareil-temps2.test.js` (temps 2 : même fichier de règles, ligne basculée par `temps2()` de `fixtures.js`), `24-session-voie1-sonde.test.js` (sonde de la voie 1, écartée). Mutations `SS*` dans `tests/tools/mutations-session.mjs`. Budgets figés dans `tests/unit/budgets.test.js`.

| # | Cas | Attendu |
|---|---|---|
| SE1 | `alice` écrit sa session (identifiant + heure du serveur), la relit, la remplace par un autre identifiant (la dernière connexion gagne), puis reprend la place | OK partout |
| SE2 | Schéma fermé : plateforme, position ou toute clé en plus ; clé manquante ; identifiant vide, de 15 ou 65 caractères, avec `/`, espace, retour à la ligne, accent, `null`, nombre, booléen, objet ; heure du client ; mise à jour partielle qui ajoute une clé. Bornes de 16 et 64 caractères | KO partout ; OK pour les deux bornes |
| SE3 | La session de `bob` : `alice` (créatrice de sa Miff), `carol`, `zoe`, `eve` et un visiteur la lisent, l'écrivent, la suppriment | KO partout ; la session de `bob` ne change pas |
| SE4 | Déconnexion volontaire : `bob` supprime sa session (même absente) ; `alice` supprime celle de `bob` ; lecture d'une session absente | OK ; KO ; « introuvable » sans erreur de droits |
| SE5 | Un seul chemin : autre identifiant sous `session/`, `sessions/active`, liste de la sous-collection, sous-document ; l'ancien gabarit de jeton (`devices/abc {token, platform, updatedAt}`) est refusé (SRV-2R, section 26) ; la session s'écrit sans profil | KO ×7 ; OK |
| SE6 | Temps 1 : statut et position SANS `deviceId` (applications actuelles), sans session puis avec une session existante | OK partout (le trou du temps 1) |
| SE7 | Statut avec `deviceId` : sans session ; égal à la session ; différent ; casse différente ; `null`, vide, nombre, booléen, tableau, objet ; mise à jour partielle qui l'envoie | KO ; OK ; KO ; KO ; KO ; OK |
| SE8 | Scénario complet : A écrit, B se connecte, A refusé (le statut de B n'est pas écrasé), B accepté, A reprend la place, B refusé | OK / KO / OK / OK / KO / OK |
| SE9 | `bob` utilise l'identifiant de la session d'`alice` pour son statut et sa position (sans session, puis avec la sienne) ; il écrit le statut d'`alice` | KO ; KO ; OK avec le sien ; KO |
| SE10 | Session supprimée : statut et position portant le `deviceId` ; statut sans la clé | KO ; KO ; OK (temps 1) |
| SE11 | Position : sans session ; égal ; différent après remplacement (la position de l'appareil actif n'est pas écrasée) ; `null` ; clé en plus (`accuracyM`) | KO ; OK ; KO ; KO ; KO |
| SE12 | Lots d'entrée (rejoindre et partager, créer et partager gratuit, créer et partager premium avec emplacement gratuit conservé) : statut initial avec un `deviceId` faux, puis le bon | KO (rien n'est écrit) puis OK |
| SE13 | Activer le partage (fiche + statut + position, un lot) et mise à jour manuelle (photo + statut) : appareil remplacé, puis appareil actif | KO en entier (la photo n'est pas écrite) ; OK |
| SE14 | **Limite connue** : un appareil remplacé quitte une Miff (lot des applications actuelles) ; la créatrice retire un membre | OK (une suppression n'a pas de charge utile) |
| SE15 | **Limite connue** : un appareil remplacé écrit un lieu, le supprime, met à jour sa fiche, écrit une photo seule, renouvelle son consentement | OK partout (actions manuelles, budget des lots de lieux préservé) |
| SE16 | **Limite connue** : un membre autorisé lit le statut de `bob` avec son `deviceId` ; un étranger ne lit rien | OK ; KO |
| SE17 | Régression : les écritures SANS `deviceId` d'avant (rejoindre, créer, statut invalide, batterie 101, clé en plus avec ou sans `deviceId`) | comme avant : OK / KO selon le cas |
| SE18 | R-LIEU-1 + SESS-1 ensemble : statut de plus de 24 h, départ en mise à jour partielle avec `lastUpdateAt` ancien conservé : mauvais `deviceId`, `deviceId` nul, nouvelle valeur ancienne avec le bon `deviceId`, bon `deviceId` ; puis un autre appareil prend la place | KO ; KO ; KO ; OK (heure conservée) ; ancien appareil KO, nouveau OK |
| SE19 | **Limite connue (temps 1)** : un `update` qui retire la clé `deviceId` (`FieldValue.delete()`) après remplacement de l'appareil | OK (refusé au temps 2 : SF11) |
| SE20 | Ancienne application sur un compte déjà passé par une application SESS puis déconnecté (session supprimée, clé en base) : mise à jour partielle sans la clé ; `set` complet sans la clé ; mise à jour partielle ensuite | KO ; OK ; OK |
| SF1 | Garde-fou : la bascule du temps 2 change exactement UNE ligne de `firestore.rules` | OK |
| SF2 | Temps 2 : statut sans `deviceId` (avec ou sans session), avec un faux, avec `null` ; avec le bon ; mise à jour avec le bon | KO ; KO ; KO ; OK ; OK |
| SF3 | Temps 2 : position sans `deviceId`, avec un faux (appareil remplacé), avec le bon | KO ; KO ; OK |
| SF4 | Temps 2 : lots d'entrée (rejoindre, créer, créer en premium) sans `deviceId`, puis avec | KO ; OK |
| SF5 | Temps 2 : entrée sans partage (aucun statut), lieux, fiche, photo seule, renouvellement du consentement, quitter (lot actuel), retrait | OK partout |
| SF6 | Temps 2 : départ avec la position, sans session ni `deviceId` | OK |
| SF7 | Temps 2 : activer le partage (fiche + statut + position) sans `deviceId` sur le statut, sans sur la position, avec les deux | KO ; KO ; OK |
| SF8 | Temps 2 : la session s'écrit avant tout statut, un nouvel appareil prend la place | OK ; l'ancien refusé ; le nouveau OK |
| SF9 | **Limite connue** : mise à jour PARTIELLE sans `deviceId`, jugée sur le document résultant | OK si la valeur en base est celle de la session ; KO si elle est périmée ; OK de nouveau quand l'appareil actif a écrit |
| SF10 | Temps 2 + R-LIEU-1 : statut ancien sans `deviceId` en base, départ partiel sans clé, avec un faux, avec le bon | KO ; KO ; OK |
| SF11 | Temps 2 : `update` qui retire la clé ; recréation du statut après suppression sans la clé, avec une fausse, avec la bonne | KO ; KO ; KO ; OK |
| SF12 | Temps 2 : lot mixte statut avec la clé + position sans la clé (puis avec une fausse) ; puis les deux avec la bonne | KO en entier, rien d'écrit ; KO ; OK |
| SV1 | Sonde (voie 1, écartée) : `request.auth.token.auth_time` est un entier utilisable dans une règle (l'écriture n'accepte que l'`auth_time` du jeton) | OK |
| SV2 | Sonde : deux jetons de `auth_time` différents ; seul celui de la session passe | OK / KO |
| SV3 | Sonde, limite : deux connexions de même `auth_time` (même seconde) ne sont pas distinguées | OK pour les deux |

## 20. R-LIEU-1 : statut dont les horodatages ont plus de 24 h (acceptée par le PM le 2026-11-01 ; demandée par le client Android LOC-2b) — DÉPLOYÉ par le PM le 2026-11-10 au plus tard (fichier de `main` de ce jour ; information du PM, non vérifiable par l'agent)

`presenceAt` et `lastUpdateAt` restent bornés (pas plus de 24 h dans le passé, pas plus de 5 min dans le futur) **sauf si la valeur envoyée est identique, à la microseconde (précision de stockage de Firestore), à celle déjà enregistrée dans le même statut** : elle peut alors être conservée quel que soit son âge. Chaque champ n'est comparé qu'à lui-même ; le statut doit déjà exister ; tout le reste de la validation (clés, valeurs, heure serveur, consentement actif) est inchangé. `escalationDueAt` n'est PAS concerné (limite connue). **0 lecture de règles de plus** (`resource` ne coûte rien). Tests dans `tests/rules/21-statut-ancien.test.js` ; mutations `R-LIEU-1-*` dans `tests/tools/mutations-statut.mjs`.

| # | Cas | Attendu |
|---|---|---|
| RL1 | Cas Android LOC-2b : statut en base vieux de plus de 24 h ; `bob` écrit un départ (`out`) avec `presenceAt` récent et `lastUpdateAt` **conservé** | OK ; `lastUpdateAt` inchangé en base |
| RL1b | Mise à jour manuelle après un long séjour (`lastUpdateAt` = maintenant, `presenceAt` conservé) ; rafraîchissement de `locationAccess` avec les deux horodatages conservés | OK ; OK |
| RL1c | Régression : deux horodatages récents (statut récent, puis statut ancien), jusqu'à +4 min 55 s dans le futur | OK partout |
| RL2 | Nouvelle valeur ancienne : à ±1 ms de celle de la base, avancée de 1 h mais encore ancienne, il y a 3 jours, 1970, ±1 microseconde ; pour `lastUpdateAt` puis pour `presenceAt` | KO partout |
| RL2b | La valeur de `lastUpdateAt` mise dans `presenceAt` et inversement ; puis chacune à sa place | KO ; KO ; OK |
| RL3 | Conservation sans statut en base : création d'un statut (activation du partage, valeurs anciennes) ; recréation après suppression dans une écriture SÉPARÉE avec les anciennes valeurs | KO ; KO ; (valeurs récentes : OK) |
| RL3b | `resource` vaut l'état d'AVANT le lot : un lot `[supprimer son statut, le recréer avec les anciennes valeurs]` est accepté (équivaut à une mise à jour, inoffensif : aucune valeur nouvelle) ; le même lot avec une nouvelle ancienne valeur est refusé | OK ; KO |
| RL6b | Base injectée (règles désactivées) SANS `lastUpdateAt` (puis sans `presenceAt`) : une ancienne valeur n'est pas acceptée (une clé absente en base ne vaut jamais « conservée ») | KO ; KO |
| RL4 | `bob` écrit le statut d'`alice` avec les valeurs d'`alice` conservées, ou avec les siennes ; `bob` recopie dans son statut la valeur ancienne d'`alice` ; visiteur ; membre d'une autre Miff (`zoe`) | KO partout |
| RL5 | L'avenir reste borné : +10 min (conservé ou non), +1 jour ; une valeur de +4 min écrite puis conservée | KO ; KO ; KO ; OK |
| RL6 | Type : `null`, texte, nombre, booléen, objet à la place d'un horodatage (les deux champs) ; base contenant `null` et `null` renvoyé | KO partout |
| RL7 | Le reste ne bouge pas : `escalationDueAt` ancien conservé (limite connue) ; `updatedAt` client ; clé en trop (`lat`) ; `presence`, `locationAccess`, `batteryPct`, `updateText` invalides ; clé manquante | KO partout ; `escalationDueAt` nul : OK |
| RL8 | Consentement exigé : partage arrêté, puis fiche retirée, avec les valeurs conservées | KO ; KO |
| RL9 | Lecture inchangée : un statut ancien reste lisible par `alice` (membre qui partage), pas par un étranger ni un visiteur | OK ; KO ; KO |

## 22. R-PHOTO-LOC : localisation de la photo d'update, OBLIGATOIRE (décision du PM du 2026-11-05 « OK reco », resserrée le 2026-11-06 : « une photo d'update doit être attachée à une position, forcément ») — DÉPLOYÉ (2026-11-06 d'après l'en-tête de `firestore.rules` ; fichier de `main` redéployé par le PM le 2026-11-10 : information du PM, non vérifiable par l'agent)

Le document `miffs/{id}/photos/{uid}` porte **toujours** `lat`, `lng` et `measuredAt` (jamais nulles) : la position figée au moment de la photo et l'heure de sa mesure (bornes de `positions.measuredAt` : -24 h, +5 min). Écriture : par l'intéressé seul ; **toute** photo exige un consentement de version 03 ou plus (fonction dédiée `isPhotoLocationConsentVersion`, même seuil que la dernière position) ; une photo sans localisation, une seule ou deux clés sur trois, une mise à jour qui supprimerait une clé, sont **refusées** (le document fusionné doit rester valide). Lecture (**inchangée**) : une photo n'est lisible que par son auteur ou par un membre qui a **publié sa propre position** ; **pas** de contrôle « l'auteur partage encore » (décision du PM du 2026-11-06 : un reliquat est borné par l'expiration de 24 h) ; un éventuel **ancien document** sans localisation (aucune application publiée n'en écrit) reste lisible comme avant. Expiration (24 h), suppression et lots de purge : **inchangés**. Fichiers : `tests/rules/25-photo-localisee.test.js` (PL1 à PL16, PL18 à PL23), `tests/rules/26-photo-localisee-temps2.test.js` (PL17).

| ID | Cas | Résultat |
|---|---|---|
| PL1 | **Photo sans localisation refusée** : aucune clé (jpeg, createdAt, expiresAt), `lat`/`lng` nuls, les trois nuls, avec le texte v1 ; mise à jour d'un **ancien document** qui le laisse sans localisation ; contrôle : la même photo localisée ; ajout de la localisation à l'ancien document par mise à jour ; **lecture** d'un ancien document par un membre sans position (et avec `lat`/`lng` nuls) ; suppression | KO ×6 ; OK ; KO ; OK ; OK ; OK |
| PL2 | Schéma fermé : `deviceId`, précision, adresse, `locationAt`, `updatedAt`, vitesse, objet `position` en plus ; **chacune des six clés manquante, une par une** (`jpeg`, `createdAt`, `expiresAt`, `lat`, `lng`, `measuredAt`) | KO partout |
| PL3 | **Toute** photo avec le consentement `v1`, `2026-10-v1`, `2026-10-v2`, `v02`, `2026-11-v3` (sans zéro), `v00`, `v01`, `v003`, mois 13, nul ; puis `2026-11-v03`, `2026-12-v04`, `2027-01-v05`, `2026-11-v99` | KO ; OK |
| PL4 | Photo avec partage arrêté ; activer avec le texte v1 dans le même lot ; activer avec le texte v3 dans le même lot ; révoquer dans le même lot | KO ; KO ; OK ; KO |
| PL5 | Écrire la photo d'un autre : membre, créatrice, étranger, visiteur ; mise à jour de la localisation de la photo d'un autre ; l'intéressé | KO ×5 ; KO ; OK |
| PL6 | **Jamais nulles** : une seule clé, ou une valeur nulle, ou les deux nulles, ou la paire valide sans heure de mesure ; bornes 90 / -90 / 180 / -180 / 0 ; juste au-delà ; NaN, infini, texte, booléen, tableau, objet ; mise à jour partielle qui ajoute la localisation à un ancien document (valide, puis `lat` 91) ; taille, durée, `createdAt` client | KO ; OK ; KO ; KO ; OK puis KO ; KO |
| PL7 | Lecture d'une photo : sans position publiée ; avec (coordonnées **et heure de mesure** lues telles qu'écrites) ; la position publiée de la seule cible ne suffit pas | KO ; OK ; KO |
| PL8 | L'auteur relit sa photo sans position publiée ; **ancien document** sans localisation d'un autre sans position publiée (contrôle) ; document **à moitié localisé** (une seule coordonnée, injecté) lu sans position publiée | OK ; OK ; KO ×2 |
| PL9 | Photo : membre au partage arrêté (position injectée), étranger, membre d'une autre Miff, visiteur, liste de la sous-collection | KO partout (le cas nominal : OK) |
| PL10 | La créatrice sans position publiée lit la photo d'un membre ; puis avec sa position | KO ; OK (aucun droit de lecture de plus) |
| PL11 | **Reliquat** (décision du PM du 2026-11-06, pas de contrôle de l'auteur) : photo dont l'auteur ne partage plus, non expirée ; la même, expirée ; ancien document sans localisation (comportement d'avant) ; lecteur au partage arrêté | OK (borné par l'expiration) ; KO ; OK ; KO |
| PL12 | Photo expirée (lecteur avec position, auteur) ; photo absente avec ou sans position publiée ; photo de 24 h 59 min | KO ; « introuvable » sans erreur de droits ; OK |
| PL13 | Arrêt du partage et départ avec une photo localisée : lot sans la suppression de la photo, puis avec | KO ; OK (photo disparue) |
| PL13b | Retrait par la créatrice (7 écritures) et suppression de la Miff avec la photo localisée : sans la photo, puis avec | KO ; OK (photo disparue) |
| PL13c | Lots des applications actuelles (sans position, avec un **ancien document** de photo) : départ, retrait | OK |
| PL14 | Suppression d'une photo localisée : membre (valide), étranger, visiteur ; auteur ; créatrice ; étranger (expirée) ; membre (expirée) | KO ; KO ; KO ; OK ; OK ; KO ; OK |
| PL15 | SESS-1 : mise à jour manuelle (photo localisée + statut) avec un `deviceId` périmé puis le bon ; **limite connue** : photo SEULE d'un appareil remplacé | KO en entier (photo non écrite) ; OK ; OK |
| PL16 | Revue de schéma : clés de la photo = `jpeg, createdAt, expiresAt, lat, lng, measuredAt` exactement, **et les six sont dans `hasAll`** (aucune facultative) ; aucune clé de précision, adresse, `deviceId` ; le statut ne porte toujours aucune coordonnée | OK |
| PL17 | Temps 2 de SESS-1 : photo localisée seule sans `deviceId` ; photo sans localisation (refusée aussi) ; avec `deviceId` ; mise à jour manuelle sans `deviceId` ou avec un périmé, puis avec le bon | OK ; KO ; KO ; KO ; KO ; OK |
| PL18 | **Heure de la mesure** : `measuredAt` absente, nulle, ou d'un autre type ; sans paire ou avec une paire nulle (même avec une heure valide) ; bornes (maintenant, -1 min, -2 h, -23 h, +4 min / -25 h, -3 jours, +10 min, +30 min, +1 jour) ; mises à jour partielles (retirer l'heure, mettre la paire à null, retirer la localisation, renouveler l'heure, remplacer la localisation) ; mise à jour qui garde une mesure de plus de 24 h, puis de 2 h ; l'heure d'une photo d'un autre | KO ; KO ; OK / KO ; KO ; OK ; KO ; OK ; KO |
| PL19 | Suppression d'**une seule** clé de la localisation (`FieldValue.delete` sur `lat`, sur `lng`, sur `measuredAt`), de deux d'entre elles, **des trois à la fois** (une photo ne peut plus être non localisée) ; la photo garde sa localisation | KO partout |
| PL20 | Un non-membre (position injectée dans la Miff), un ex-membre (position restée, fiche supprimée), un visiteur lisent la photo ou un ancien document | KO partout |
| PL21 | **Une ou deux clés de localisation sur trois**, absentes puis nulles, dans toutes les combinaisons ; les trois clés valides | KO ×14 ; OK |
| PL22 | **Trois clés valides + partage + consentement dans le même lot** (carol, partage arrêté) ; le même lot sans localisation, sans heure, ou avec le texte v1 ; mise à jour manuelle suivante (photo + statut) | OK ; KO ; KO ; KO ; OK |
| PL23 | **Lecture par réciprocité** d'une photo écrite par les règles : lecteur sans position publiée, auteur, lecteur avec sa position, membre au partage arrêté, étranger | KO ; OK ; OK ; KO ; KO |

## 23. Rafraîchissement de la batterie par une mise à jour PARTIELLE du statut (PR iOS #106 `ios/batterie-rafraichie`, PR Android #110 `android/batterie-rafraichie`) — AUCUNE modification des règles

Les applications rafraîchissent `batteryPct` avec un `update()` qui n'envoie que `{ batteryPct, updatedAt (heure du serveur), deviceId }`. Les règles jugent le document **résultant** (base fusionnée avec la requête) : `presenceAt` et `lastUpdateAt` y sont « conservés » (R-LIEU-1, section 20), `deviceId` est comparé à la session (SESS-1, section 21), `updatedAt` doit être l'heure du serveur. **`firestore.rules` n'est pas modifié** : ces tests prouvent que `validStatus` suffit. Aucune création possible (un `update()` sur un statut absent, ou un `set` avec fusion, est refusé). Le plafond (40 par jour et par appareil) et les seuils (15 min / 5 points / 3 h) sont un contrat des clients, pas des règles. Limite connue et inoffensive : un `escalationDueAt` ancien et non nul en base bloque le rafraîchissement (tous les clients l'écrivent `null`). Tests : `tests/rules/27-batterie-partielle.test.js` (temps 1, règles telles que livrées), `tests/rules/28-batterie-partielle-temps2.test.js` (temps 2).

| ID | Cas | Attendu |
|---|---|---|
| BP1 | `presenceAt` et `lastUpdateAt` vieux de plus de 24 h, `deviceId` déjà en base et égal à la session : rafraîchissement ; relu : `presenceAt`, `lastUpdateAt`, `locationAccess`, `presence`, `updateText`, jeu de clés inchangés, `updatedAt` avancé ; un second rafraîchissement d'affilée | OK ; OK |
| BP2 | Temps 1 : `deviceId` absent de la base (la requête l'apporte) ; présent en base mais périmé (session passée à B) avec l'ancien, puis le nouveau | OK ; KO ; OK |
| BP2b | Temps 1 : rafraîchissement SANS `deviceId` (applications d'avant SESS-1) | OK |
| BP3 | `deviceId` ≠ session : session absente, appareil remplacé, identifiant inventé, nul, nombre, 15 caractères, session d'un autre compte ; contrôle avec le bon | KO partout ; OK |
| BP4 | Statut absent : `update()` partiel ; `set` avec `merge` des trois clés | KO ; KO ; aucun statut créé |
| BP5 | Partage désactivé (`members/{uid}.sharing.enabled = false`), puis fiche retirée (contrôle : OK avant) | OK ; KO ; KO |
| BP6 | `batteryPct` = 101, -1, 1000, texte, vide, décimal, booléen, objet, tableau ; bornes 0, 100 et `null` | KO partout (base inchangée) ; OK ×3 |
| BP7 | `updatedAt` fourni par le client (passé, futur, 1970, valeur de la base), omis, `FieldValue.delete()`, texte ; contrôle avec l'heure du serveur | KO partout ; OK |
| BP8 | **Cas documentaire** : `escalationDueAt` ancien non nul (limite connue, inoffensive), ou à +2 jours ; `null` ; dans la fenêtre (-2 h, +4 min) | KO ; KO ; OK ; OK ; OK |
| BP9 | Autre que le propriétaire : `alice` (créatrice), `carol` (partage arrêté), `zoe` (autre Miff), `eve`, visiteur ; le statut de `bob` ne change pas ; contrôle propriétaire | KO ×5 ; OK |
| BP10 | Pas de contournement : `lat`, `lng`, clé inconnue, `locationAccess` ou `presence` hors schéma, nouvelle ancienne `presenceAt` dans le même update partiel | KO partout |
| BP11 | Temps 2 : avec le bon `deviceId` sur un statut de plus de 24 h (base avec ou sans `deviceId`) ; horodatages et `locationAccess` inchangés | OK ; OK |
| BP12 | Temps 2 : SANS `deviceId` quand la base n'en porte pas ou porte un identifiant périmé ; faux ou nul ; puis le bon | KO ×6 ; OK |
| BP13 | Temps 2, **limite connue (SF9)** : sans `deviceId` dans la requête, le document résultant garde celui de la base ; il passe s'il est égal à la session (contrat : les clients l'envoient toujours) | OK |

## 24. MODE VOYAGE (VOY-B, ticket `docs/tickets/mode-voyage.md` §4, décisions du PM du 2026-11-10) — DÉPLOYÉ (règles déployées par le PM le 2026-11-10)

Nouvelle valeur `presence = "travel"` du statut `miffs/{id}/status/{uid}` avec deux clés **facultatives** `travelUntil` (timestamp, fin du voyage) et `travelUntilDay` (texte `AAAA-MM-JJ`, jour affiché). **Disponible pour TOUS les comptes** (décision du PM du 2026-11-10, qui remplace la réserve Miff+ de la première version du ticket) : **aucune barrière premium, aucune lecture de règles de plus** (un statut de voyage coûte 2, comme un statut ordinaire). Règle `validTravel` (appelée par `validStatus`) : **hors voyage** = autre présence et clés de voyage absentes ou nulles (tous les statuts actuels) ; **en voyage** = `placeId`, `batteryPct` et `escalationDueAt` nuls, fin strictement future et **au plus 91 jours** après l'heure du serveur, jour bien formé (année 2020-2099, mois 01-12, jour 01-31). **Désactiver** (écrire une autre présence) est toujours possible. Comptes : `dave` (premium actif, Miff M3), `frank` (premium **expiré**, Miff M4), `bob` (gratuit, M1), `alice` (créatrice gratuite de M1) : **le résultat est le même pour tous**. Fichiers : `tests/rules/29-mode-voyage.test.js` (temps 1 de SESS-1, règles telles que livrées), `tests/rules/30-mode-voyage-temps2.test.js` (VY25) ; VY26 et VY27 sont dans le fichier `29` ; lectures de règles : `tests/rules/09-rule-reads.test.js` (`READS-voyage-*`, `READS-sess-status` borné à 2) et `tests/unit/budgets-voyage.test.js` ; mutations : `tests/tools/mutations-voyage.mjs`. Les bornes de dates sont testées avec une marge d'une minute côté refus (`Date.now()` du test précède toujours l'heure du serveur de quelques millisecondes).

| ID | Cas | Attendu |
|---|---|---|
| VY1 | Tout compte (`bob`, `alice`, `dave`, `frank`) active le voyage sur un statut existant, puis sur un statut recréé après suppression ; document relu | OK ×8 ; clés `travelUntil`, `travelUntilDay` écrites, `placeId` / `batteryPct` / `escalationDueAt` nuls |
| VY2 | Compte **gratuit** (aucun document `entitlements`) : activer par `set`, par `update` en lot, par création ; `alice` 90 jours ; mêmes limites que pour tous : 91 jours + 1 minute, fin passée, lieu renseigné | OK ×4 ; KO ×3 |
| VY3 | Premium **expiré** (`frank`), `active:false`, droit échu il y a une minute, document `entitlements` supprimé : activer le voyage | OK ×5 (aucune dépendance au droit) |
| VY4 | `presence = travel` sans `travelUntil`, sans `travelUntilDay`, sans aucune des deux, avec l'une nulle, l'autre nulle ; contrôle avec les deux | KO ×5 ; OK |
| VY5 | Fenêtre de `travelUntil` : -1 jour, -1 minute, maintenant ; +1 minute, 90 jours, **91 jours** ; 91 jours + 1 minute, 1 an, 100 ans | KO ×3 ; OK ×3 ; KO ×3 |
| VY6 | Types : `travelUntil` texte, nombre (millisecondes), booléen, tableau, objet, vide ; `travelUntilDay` nombre, booléen, tableau, objet, horodatage | KO partout |
| VY7 | `travelUntilDay` : `2026-11-1`, `2026-1-15`, `26-11-15`, `15/11/2026`, `2026/11/15`, mois 13 et 00, jour 32 et 00, espace avant / après, lettre avant / après, `2026-11-15T00:00:00Z`, `20261115`, vide, `demain`, fin de ligne, 1000 caractères ; puis `2026-11-15` ; **limite connue** : un jour sans lien avec la fin (`2031-02-28`) | KO partout ; OK ; OK |
| VY8 | En voyage : `placeId` renseigné (`p1`, `zz`), `batteryPct` 80, 0, 100, `escalationDueAt` futur ou passé, les trois ; puis tous nuls | KO ×8 ; OK |
| VY9 | Clés de voyage avec chaque présence ordinaire (`in_trusted`, `out`, `in_risk`, `unknown`) : `travelUntil` seul, `travelUntilDay` seul, les deux ; clés nulles ; clés absentes ; un compte gratuit avec les deux | KO ×3 ; OK ×2 (pour chaque présence) ; KO |
| VY10 | Présences voisines (`Travel`, `TRAVEL`, `traveling`, `travel ` avec espace, `voyage`, vide, nulle, booléen, nombre) ; clés inconnues (`travelFrom`, `travel`, `travelUntilAt`, `destination`, `lat`, `travelUntilDays`) | KO partout ; contrôle OK |
| VY11 | Écrire **mon** voyage : `alice` (premium **et** créatrice de M1), `dave` (pas membre de M1), `carol`, `eve`, visiteur ; désactiver le voyage d'un autre | KO partout (statut de `bob` inchangé) |
| VY12 | Partage arrêté, puis fiche de membre retirée (contrôle : OK avant) | OK ; KO ; KO |
| VY13 | Voyage en cours, pour `dave`, `frank`, `bob` : date plus longue, plus courte, **jour seul** modifié, même date + texte, désactivation par `set` sans clés | OK ×5 pour chacun (clés de voyage retirées) |
| VY14 | Désactivation par `set` sans clés, par clés nulles, par `update` avec `FieldValue.delete` : `frank` (expiré) et `bob` (gratuit) | OK ×3 pour chacun (clés supprimées) |
| VY15 | Changer la date d'une minute (±), réactiver après une désactivation : `frank`, `bob` ; changement vers une date trop lointaine ou passée | OK ; OK ; KO ; KO ; OK (réactivation) |
| VY16 | Aucune dépendance à `entitlements` : document absent, actif, expiré, désactivé : activer, changer, désactiver, réactiver ; lieu renseigné | OK ×4 ; KO |
| VY17 | Voyage dont la fin est **passée** en base (app pas encore reprise) : update qui garde `travel` (même fin) ; désactivation | KO ; OK |
| VY18 | **R-LIEU-1 compatible** : voyage de plusieurs jours (`presenceAt` vieux de 3 jours, `lastUpdateAt` de 2 jours) : update manuelle qui **conserve** `presenceAt` (et `lastUpdateAt` neuf, ou conservé) ; `presenceAt` modifié d'1 ms ; nouvelle valeur ancienne ; pour `dave` et `frank` | OK ; OK ; KO ; KO |
| VY19 | Rafraîchissement **partiel** de la batterie en voyage (`{batteryPct, updatedAt, deviceId}`) : 55, 0 ; `null` ; update partielle de texte ; sans voyage (contrôle BP1) | KO ; KO ; OK ; OK ; OK |
| VY20 | SESS-1 : voyage avec le `deviceId` de la session ; périmé, trop court, nul ; sans clé (temps 1) ; session passée à B : ancien refusé, nouveau accepté | OK ; KO ×3 ; OK ; KO ; OK |
| VY21 | **Atomicité et propriété de la suppression de position (règle générale DP13, NON utilisée par l'activation depuis la décision du PM du 2026-11-10 : la position reste partagée et l'activation n'en supprime aucune)** : un lot « voyage + suppression de MA position » reste accepté (compte premium puis gratuit) ; lot dont le voyage est invalide : refusé en entier, position conservée ; un autre membre ne supprime pas ma position (assertions inchangées, aucun resserrement ni relâchement) | OK ; OK ; KO en entier ; KO |
| VY22 | Lot de **8 Miffs** (2 lectures de règles par statut = 16 sur 20) : 8 statuts de voyage avec `deviceId` (**cas réel de l'activation**) ; puis variante de borne 8 statuts + 8 suppressions de positions (non utilisée par les clients, conservée pour figer le coût : la suppression de sa propre position ne lit rien) | OK ; OK |
| VY23 | Lot de 8 dont un seul statut invalide (un lieu renseigné) | KO en entier, aucun voyage écrit |
| VY24 | Régression : statuts ordinaires sans clés de voyage, avec clés nulles, `out`, `in_risk` ; `dave`, `frank`, `bob` ; batterie 101 et présence `banane` toujours refusées | OK ×6 ; KO ×2 |
| VY25 | **Temps 2** de SESS-1 : voyage sans `deviceId`, avec un périmé, avec celui de la session ; désactivation sans la clé, avec la clé | KO ; KO ; OK ; KO ; OK |
| VY26 | `update()` **partielle** de « travel » vers `unknown` / `out` **sans** supprimer `travelUntil` / `travelUntilDay` (ni l'une, ni l'autre seule) ; témoin : les deux supprimées | KO ×3 par présence ; OK |
| VY27 | **Caractérisation** : pendant un voyage, écriture de ma position et de ma photo (consentement v3) : autorisée : **c'est le comportement voulu** (décision du PM du 2026-11-10 : la position reste partagée pendant le voyage ; ce n'est plus une limite connue). Ce qui reste un contrat du client : ne pas écrire de statut de présence ordinaire pendant le voyage (tests clients V2 bloquants). À changer en connaissance de cause si une décision future du PM impose de refuser la position ou la photo en voyage | OK ×3 |

**Lectures de règles** : statut de voyage = statut ordinaire = **2** (consentement + session), (comptage sans cache ; aucune lecture du premium) ; lots de statuts de voyage : **8 Miffs au plus** (16 sur 20, marge 4), avec la variante « + suppression des positions » (non utilisée par l'activation) : 16 aussi (la suppression de sa propre position ne lit rien). Mesures : `rules-test-report.md`.

## 25. DEMANDE D'AJOUT D'UN LIEU (LIEU-REQ-A, ticket `docs/tickets/demande-ajout-lieu.md`, décisions du PM D1 à D9 du 2026-11-11) — NON DÉPLOYÉ

Nouveau document `miffs/{id}/placeRequests/{uid}_{1..3}` (trois emplacements fixes par membre) et document compteur `miffs/{id}/meta/placeRequests` (`pending` 0 à 10, `lastReqId`, `updatedAt`). Un membre **non créateur** dépose une demande ; le créateur la lit, l'accepte (lot atomique : **lieu créé avec les règles EXISTANTES** + suppression de la demande + compteur) ou la refuse (suppression ; le demandeur ne reçoit rien). Les règles de `places` **ne changent pas** (aucune ligne modifiée, vérifié par `tests/unit/budgets-demande-lieu.test.js`). Fichiers : `tests/rules/31-demande-lieu.test.js` (RQ1 à RQ27), `tests/helpers/requests.js`, mutations `RQ-*` (`tests/tools/mutations-demande-lieu.mjs`), budgets (`tests/unit/budgets-demande-lieu.test.js`, scénarios `READS-req-*` de `09-rule-reads.test.js`). Correspondance avec les critères S1 à S10 du ticket : S1 = RQ1 à RQ4, S2 = RQ5 et RQ6, S3 = RQ7 à RQ12, S4 = RQ13, S5 = RQ14 et RQ15, S6 = RQ16, S7 = RQ17 et RQ18, S8 = RQ19 à RQ22, S9 = budgets (ci-dessous), S10 = RQ23 ; L1 à L3 = RQ24 et RQ25.

Comptes : `alice` (créatrice gratuite de M1, a déjà `p1`), `bob` et `carol` (membres gratuits), `dave` (Miff+ actif, ajouté comme membre de M1 quand il faut ; créateur de M3), `frank` (Miff+ **expiré**), `eve` (étrangère), `zoe` (créatrice de M2). Les lots sont ceux d'un client : compteur par `set` **avec fusion** et `increment(±1)` pour un membre ; valeur absolue pour le créateur.

| ID | Cas | Attendu |
|---|---|---|
| RQ1 | S1 : `bob` crée `bob_1`, `bob_2`, `bob_3`, `carol` crée `carol_1`, chacune avec son compteur +1 (le compteur n'existe pas au départ : créé à 1) ; relecture des documents | OK ×4 ; compteur à 1, 2, 3, 4 ; 9 clés exactement dont `category` ; `lastReqId` et `updatedAt` écrits |
| RQ2 | S1 : bornes acceptées : adresse nulle, nom de 60 caractères, rayon 50 et 2000, latitude ±90, longitude ±180, adresse de 200 caractères | OK ×4 |
| RQ3 | S1 : forme : chacune des 9 clés manquante (dont `category`) ; clé en trop (`status`, `message`, `priority`, `createdBy`, `updatedAt`, `requestedByName`) ; nom vide / 61 caractères / nombre / nul ; catégorie inconnue / `Home` / `HOME` / vide / nulle / nombre / tableau ; type inconnu / `Trusted` / nul ; adresse de 201 caractères / nombre ; latitude 90,0001 / -90,1 / NaN / infinie / texte / nulle ; longitude idem ; rayon 49 / 2001 / 100,5 / texte / nul / négatif ; `createdAt` du client, du passé, du futur, nul, texte ; `requestedBy` d'un autre, nul, vide ; contrôle valide | KO partout ; OK (contrôle) |
| RQ4 | S1 : identifiant d'emplacement : `bob_4`, `bob_0`, `bob_01`, `bob_`, `bob`, `bob_1x`, `xbob_1`, ` bob_1`, `bobb_1`, `bob_1_1`, `BOB_1`, `bob_a`, identifiant automatique ; `carol_1` (de `carol` ou de `bob`) ; `bob_1` avec `requestedBy` de `carol` ; emplacement de la créatrice ; contrôle `bob_3` | KO partout ; OK (contrôle) |
| RQ5 | S2 : ne peuvent pas créer : la créatrice (`alice_1`), un non-membre (`eve`, `zoe`, `dave`), un visiteur, un ex-membre (fiche supprimée) ; contrôle `carol` | KO ×6 ; OK ; aucun compteur écrit |
| RQ6 | S2 : Miff supprimée (document absent, fiches de membres survivantes) ; Miff jamais créée | KO ×2 |
| RQ7 | S3 : compteur à 10 : la 11e est refusée ; à 9 : acceptée (compteur à 10) ; puis `carol` et `bob_2` refusées ; après un retrait : acceptée | KO ; OK ; KO ×2 ; OK ; OK |
| RQ8 | S3 : compteur incohérent écrit par un membre : +1 sans création, +1 pour une demande déjà là, -1 sans suppression, remise à zéro, valeur absolue fausse, gonfler à 10, `lastReqId` nul, la demande d'un autre ; création avec +2, 0, -1, `lastReqId` d'une autre demande ou nul, sans `updatedAt`, `updatedAt` du client, clé en trop, 1,5, texte ; demande sans compteur | KO partout ; compteur inchangé |
| RQ9 | S3 : valeur absolue = ancienne valeur + 1 avec la création : acceptée (le compteur est une somme vérifiée) ; deux demandes pour un seul +1 (ou +2) ; deux retraits pour un seul -1 | KO ×2 ; OK ; KO |
| RQ10 | S3 : `lastReqId` périmé : demande créée sans toucher le compteur alors que `lastReqId` vaut déjà `bob_1` ; retrait sans compteur | KO ; OK (contrôle avec le compteur dans le lot) ; KO ×2 |
| RQ11 | S3 : compteur recalé à 0 alors que des demandes existent : retrait par `increment(-1)` (résultat -1), `pending: -1`, `pending: 1` ; retrait avec la valeur absolue 0 (repli du client) ; le créateur : `increment(-1)` refusé, valeur absolue acceptée | KO ×3 ; OK ; KO ; OK |
| RQ12 | S3 : le compteur : `get` par `bob`, `carol`, `eve`, visiteur ; `alice` ; suppression par `bob` ; liste de `meta` ; autre document `meta/autre` (membre et créatrice) ; Miff étrangère (`eve`, `zoe`) | KO ; OK (`alice`) ; KO partout ailleurs |
| RQ13 | S4 : `risk` : `bob`, `carol` (gratuits), `frank` (expiré) ; `dave` (Miff+) ; `frank` et `bob` en `trusted` ; `dave` désactivé (`active:false`) puis sans document `entitlements` | KO ×3 ; OK ; OK ×2 ; KO, OK, KO |
| RQ14 | S5 : lecture : `alice` liste et `get` ; `bob` liste filtrée (2), `carol` (1), `get` de sa demande et d'un emplacement libre ; `get` de la demande ou de l'emplacement de `carol`, de `alice` ; liste sans filtre, filtrée sur un autre, sur `type`, avec `orderBy`, avec `in` ; `eve`, `zoe`, `dave` (liste, filtrée, `get`) ; visiteur ; groupe de collections | OK ; KO partout ailleurs |
| RQ15 | S5 : une demande de 40 jours reste lisible (aucune règle d'âge : masquage côté client) ; membre retiré : liste et `get` refusés ; Miff supprimée : plus aucune lecture (demandeur, créatrice) | OK ×2 ; KO ×2 ; OK (contrôle) ; KO ×4 |
| RQ16 | S6 : `update`, `set` sur l'existante (avec ou sans compteur), changement de type, de `requestedBy` ; par le demandeur, la créatrice, un tiers, un non-membre, un visiteur | KO partout ; demande inchangée |
| RQ17 | S7 : retrait : sans compteur, avec le compteur d'un autre, `carol` qui supprime la demande de `bob` et inversement, `eve`, `dave`, visiteur, suppression seule ; remise à zéro ou compteur inchangé en retirant ; retraits corrects ; emplacement libéré puis réutilisé | KO ×11 ; OK ×3 ; OK |
| RQ18 | S7 : suppression par la créatrice : seule, avec le compteur, en lot de plusieurs dont une demande expirée ; créatrice d'une autre Miff | OK ×3 ; compteur recalé ; KO |
| RQ19 | S8 : accepter (créatrice gratuite, `p2` puis `p3`) : lot lieu + suppression + compteur ; lieu relu tel que demandé ; compteur à 1 puis 0 ; rien pour le demandeur | OK ×2 |
| RQ20 | S8 : accepter refusé en entier : `p3` déjà pris, identifiant automatique (4e lieu), `p4`, zone de danger, catégorie inconnue ; la demande et le compteur restent ; contrôle `p3` libre | KO ×5 ; OK |
| RQ21 | S8 : créateur Miff+ : zone de danger et lieu au-delà de 3 (identifiant libre) ; créateur Miff+ **expiré** : identifiant libre refusé, `p1` accepté | OK ×2 ; KO ; OK |
| RQ22 | S8 : refuser (lot suppression + compteur) ; supprimer sans toucher le compteur (dérive possible, limite connue) ; recaler : 1, 10 acceptés ; 11, -1, `lastReqId` renseigné, `updatedAt` du client, clé en trop, 1,5 refusés ; créer le compteur absent, le supprimer (créatrice) ; suppression par `bob` | OK ×4 ; KO ×6 ; OK ×2 ; KO |
| RQ23 | S10 : un membre n'écrit jamais un lieu : création seule, avec la demande dans le lot (retrait ou « acceptation » par son auteur), modification, suppression, lieu au nom de la créatrice | KO ×6 ; demande inchangée |
| RQ24 | L1 / L2 : départ et retrait : `bob` retire ses deux demandes puis quitte (lot de départ inchangé) ; retrait de `carol` par `alice` (lot de 6, inchangé) alors qu'elle a une demande : la demande devient orpheline ; `carol` ne crée ni ne lit plus mais retire la sienne ; la créatrice nettoie une orpheline | OK ×4 ; KO ×3 ; OK ×2 |
| RQ25 | L3 : suppression de la Miff : lot de 5 suppressions de demandes puis suppression du compteur par la créatrice ; plus rien en base | OK ×2 |
| RQ26 | Cas particuliers : emplacement d'un autre uid voisin (`bobby_1` vs `bob`), `zoe` ne lit pas M1, redemander le même lieu au même emplacement après un retrait | OK ; KO ×3 ; OK ×3 |
| RQ27 | Catégorie : les 7 valeurs de R-LIEU-2 acceptées ; absente, inconnue, casse, espace, vide, nulle, nombre, tableau, objet, booléen refusées ; l'acceptation copie la catégorie dans le lieu | OK ×7 ; KO ×12 ; OK ; lieu relu avec la catégorie de la demande |

**Lectures de règles** (comptage sans cache) : créer une demande `trusted` = 5 sur 20 (borne haute du texte 7), `risk` = 6 (8), retirer = 4 (5), refuser = 2, accepter (créatrice gratuite) = 4 (borne haute 7), accepter avec un lieu Miff+ = 5 (8) ; lister : 1 (créateur) ou 3 (demandeur) sur 10. **Aucun lot existant ne change** (créer, rejoindre, quitter, retirer un membre, supprimer la Miff, lieux : aucune règle existante n'appelle les nouvelles fonctions). Chiffres figés par `tests/unit/budgets-demande-lieu.test.js` ; mesures de l'émulateur (scénarios `READS-req-*`) dans `rules-test-report.md`.

**Cas hors émulateur** (documentés, non testables en règles) : expiration à 30 jours (masquage et nettoyage côté clients) ; rafales de création / retrait (aucune limitation de débit possible : App Check) ; contenu des demandes (adresse d'un tiers) ; envoi d'une notification au créateur (aucun serveur sur Spark).

## 26. JETONS D'APPAREIL pour les notifications push (SRV-2R, ticket `docs/tickets/notification-update-membres.md` §8, étude `docs/dev/etude-serveur-notifications-premium.md` §8) — NON DÉPLOYÉ

Document `users/{uid}/devices/{deviceId}` : `deviceId` = **l'identifiant de la session active** (`users/{uid}/session/active.deviceId`, un compte = un appareil) ; 7 clés exactes (`token` 1 à 4096, `platform` ios | android, `appVersion` 1 à 32, `tz` fuseau, `notify` { `updates` booléen }, `updatedAt` et `createdAt` = heure serveur) ; **propriétaire seul** (lire, lister, écrire, supprimer) ; écriture seulement si l'identifiant est celui de la session (`getAfter`, une lecture de règles) ; suppression sans condition. **Plafond « 5 appareils »** : non compté par les règles (impossible sans lecture coûteuse) ; un seul identifiant est écrivable à la fois et le nouvel appareil supprime les autres dans son lot de connexion (DV11 montre la limite : des jetons périmés peuvent subsister s'il oublie, ils sont inertes et le serveur les ignore). Collection serveur `notifState/{miffId}` : **aucune règle**, refus par défaut (DV12). **Seule règle existante modifiée** : l'ancien bloc `devices` (gabarit `{token ≤ 512, platform, updatedAt}`, identifiants libres), qu'aucune application publiée n'écrit ; les cas U5, U5b, U6, U6b, SE5 ont été réécrits en conséquence. Aucune autre règle ni aucun lot existant ne change (test unitaire de budget).

Comptes : `alice` (créatrice de M1), `bob`, `carol` (membres), `dave` (Miff+), `eve` (étrangère), `zoe` (créatrice de M2). Les sessions sont injectées sans les règles (`DEVICE_A`, `DEVICE_B`).

| ID | Cas | Attendu |
|---|---|---|
| DV1 | `bob` (session `DEVICE_A`) crée son jeton : 7 clés exactement, heures du serveur ; relecture (get et liste) ; bornes acceptées : jeton de 4096, version de 32, fuseaux `America/Argentina/Buenos_Aires`, `Etc/GMT+5`, `GMT+01:00`, `UTC`, une lettre, 64 caractères, iOS, interrupteur « un proche donne des nouvelles » coupé | OK ×9 |
| DV2 | Lot de connexion [session B + jeton B + suppression du jeton A] en UN lot ; jeton sous un identifiant qui n'est pas celui de la nouvelle session ; ancien jeton mis à jour sous la nouvelle session ; jeton écrit seul quand la session existe déjà | KO ×2 ; OK ; OK |
| DV3 | Forme : chacune des 7 clés manquante ; clés en trop (position, nom, `userAgent`, identifiant publicitaire, `deviceId`, **`locale` et `notificationsEnabled`, retirés du modèle**) ; jeton vide / 4097 / nombre / nul ; plateforme `web`, casse, vide, nombre ; version vide / 33 / nombre ; fuseau vide, 65, commence par `/` ou un chiffre, espace, retour à la ligne, accent, barre oblique inverse ; `notify` booléen / vide / clé en trop / clé manquante / valeur texte ; `updatedAt` et `createdAt` du client, nuls, textes ; ancien gabarit `{token, platform, updatedAt}` ; contrôle valide | KO partout ; OK (contrôle) |
| DV4 | Identifiant : `d1` à `d5`, trop court, autre que la session, suffixe, minuscules, session d'un autre appareil ; identifiants de session mal formés injectés (court, 15, 65, point, espace, accents) : le jeton refuse le sien ; identifiants valides de 16 et 64 caractères | KO partout ; OK (identifiants valides) |
| DV5 | Sans session ; session d'un autre appareil ; session supprimée ; session d'un autre compte avec le même identifiant | KO partout ; OK (contrôle avec la bonne session) |
| DV6 | Mise à jour **partielle** : jeton, fuseau, version, `notify.updates` ; `createdAt` figé ; refusés : `updatedAt` absent / du client, `createdAt` modifié ou supprimé, plateforme changée, clé supprimée (`tz`, `notify`), clé ajoutée, jeton vide ou de 4097, fuseau, `notify` invalide ; `set` complet sur un jeton existant (nouveau `createdAt`) refusé, accepté s'il reprend le `createdAt` lu | OK (mises à jour valides) ; KO (le reste) ; KO ; OK |
| DV7 | Appareil remplacé : la session passe à `DEVICE_B` ; l'ancien jeton ne peut plus être mis à jour ni recréé ; il reste supprimable ; le nouvel appareil s'enregistre | OK ; KO ×3 ; OK ; OK |
| DV8 | Lecture par un tiers : un membre de la même Miff, la créatrice, une étrangère, une autre créatrice, `dave`, un visiteur : `get`, liste, liste filtrée, **groupe de collections** `devices` (même pour le propriétaire) ; profil du propriétaire non lisible ; le propriétaire lit et liste | KO partout ; OK ×2 |
| DV9 | Usurpation : création, mise à jour, suppression du jeton d'un autre (avec la forme valide et une session de même identifiant chez la cible), visiteur compris ; le jeton du propriétaire reste intact ; contrôle propriétaire | KO partout ; OK |
| DV10 | Suppression : jeton existant, jeton absent, session déjà supprimée, session d'un autre appareil ; séquence de la suppression de compte des applications (session supprimée d'abord, puis liste côté serveur et suppression de chaque jeton) ; lot de plusieurs suppressions | OK partout ; `devices` vide |
| DV11 | Pas de compteur : trois connexions sans nettoyage laissent trois jetons, seul le dernier est modifiable (limite connue, jetons périmés inertes) ; identifiant d'une session pas encore ouverte refusé ; six connexions avec nettoyage [session + jeton + 5 suppressions] : un seul jeton à chaque fois | OK ; KO ; OK (6 connexions, un seul jeton à chaque fois) |
| DV12 | `notifState/{miffId}` : créatrice, membres, `dave`, étrangers, visiteur : `get`, liste, liste filtrée, groupe de collections, création, mise à jour, suppression, sous-documents, autre Miff, `miffs/{id}/notifState` ; l'écriture Admin (serveur) fonctionne et l'état n'a pas changé | KO partout ; OK (Admin) |

**Lectures de règles** (comptage sans cache, test unitaire `budgets-jetons.test.js`) : créer ou mettre à jour un jeton = **1** sur 10 (la session) ; lire, lister, supprimer = **0** ; lot de connexion [session + jeton + N suppressions] = **1** sur 20 quel que soit N (mesuré à l'émulateur : au plus 0, la session faisant partie du lot ; la borne prudente documentée reste 1). **Aucune règle existante n'appelle les fonctions des jetons.**

**Cas hors émulateur** (documentés, non testables en règles) : plafond du nombre de jetons (voir ci-dessus) ; coût d'une boucle d'écritures d'un propriétaire malveillant sur son propre jeton (App Check strict) ; jeton réellement valide chez FCM (le serveur supprime un jeton refusé par FCM) ; purge des jetons inactifs (fonction planifiée, SRV-4) ; expiration TTL de `notifState` (console, action du PM) ; `getAfter` d'un document du même lot facturé ou non en production (comptage prudent : 1).
