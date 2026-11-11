# Demande d'ajout d'un lieu : chemins, lots et erreurs à implémenter côté clients (LIEU-REQ)

**Source** : ticket `docs/tickets/demande-ajout-lieu.md` (décisions du PM D1 à D9 du 2026-11-11), règles `backend/firestore.rules` (point 13 de l'en-tête), modèle `backend/data-model.md` (section « Ce qui change : demande d'ajout d'un lieu », §2.14, §2.15, §8, §9, §11 point 31), tests `backend/rules-test-plan.md` §25.

**État : règles NON DÉPLOYÉES.** Tant que le PM ne les a pas déployées (procédure `docs/dev/deploiement-regles.md`, « oui » explicite), la production **refuse** ces écritures (`permission-denied`) : les clients se développent et se testent contre l'émulateur, la recette sur appareil ne commence qu'après le déploiement. **Cette PR ne touche ni `android/`, ni `ios/`, ni `web/`, ni `docs/legal/`.**

## 1. Chemins

| Chemin | Rôle |
|---|---|
| `miffs/{miffId}/placeRequests/{uid}_{k}` | Une demande. `{uid}` = uid du **demandeur**, `k` ∈ `1`, `2`, `3` : **trois emplacements fixes** par membre et par Miff. |
| `miffs/{miffId}/meta/placeRequests` | Compteur des demandes en attente de la Miff (plafond de 10). **Lisible du créateur seul.** |

### Document `placeRequests/{uid}_{k}` : exactement ces 9 clés

| Clé | Type / valeur | Remarque |
|---|---|---|
| `requestedBy` | string = mon uid | |
| `name` | string 1 à 60 | |
| `category` | `"home"`, `"work"`, `"school"`, `"sport"`, `"relative"`, `"leisure"`, `"other"` | **Obligatoire**, la même liste fermée qu'un lieu (R-LIEU-2) : le formulaire de demande est celui de la création, qui la demande déjà. Copiée dans le lieu à l'acceptation. |
| `type` | `"trusted"` ou `"risk"` | `risk` seulement si **le demandeur** est Miff+ (le client ne propose l'option que dans ce cas ; sinon paywall, comme la création d'un lieu). |
| `address` | string ≤ 200 ou `null` | Sensible. Le client la rend obligatoire comme pour un lieu. |
| `lat`, `lng` | nombres (5 décimales) | Sensible. |
| `radiusM` | **entier** 50 à 2000 | Mêmes valeurs que la création d'un lieu (50 à 800 par pas de 50, défaut 100). |
| `createdAt` | `serverTimestamp()` | **Jamais** une date du client (refusé). |

**Aucune autre clé** (ni `status`, ni message, ni nom du demandeur, ni `priority`) : refus. **Aucune modification** après création (un `update` ou un `set` sur une demande existante est refusé) : pour changer, on retire et on redemande.

### Document compteur `meta/placeRequests`

`{ pending: entier 0 à 10, lastReqId: string ou null, updatedAt: serverTimestamp() }`, ces 3 clés exactement.

## 2. Lots (batch Firestore : tout ou rien)

Les compteurs s'écrivent avec un **`set` avec fusion** (`SetOptions.merge()` sur Android, `setData(_, merge: true)` sur iOS) : cela crée le compteur s'il n'existe pas (premier lot) et l'incrémente sinon, avec `FieldValue.increment`. **Un `set` sans fusion remplacerait `pending` au lieu de l'incrémenter.**

### 2.1 Demander un lieu (membre non créateur) : lot de 2

1. Lire mes demandes : **1 requête** `placeRequests` avec `where('requestedBy', '==', moi)` (**le filtre est obligatoire** : sans lui, la requête est refusée). En déduire l'emplacement libre `{moi}_1`, `{moi}_2` ou `{moi}_3` : **un emplacement n'est libre que si son document n'existe pas** (une demande de plus de 30 jours est d'abord supprimée au passage, §2.6, ce qui libère son emplacement). Si les trois sont pris : message `place_request_limit_member`, **aucune écriture**.
2. Lot :
   - `set` `placeRequests/{moi}_{k}` avec les 9 clés (`category` comprise) ;
   - `set` avec fusion `meta/placeRequests` = `{ pending: increment(+1), lastReqId: "{moi}_{k}", updatedAt: serverTimestamp() }`.

**Les deux écritures sont indissociables** : la demande sans le compteur est refusée, le compteur sans la demande aussi. `lastReqId` doit être **exactement** l'identifiant de la demande du lot.

Jamais pour le **créateur** de la Miff (refusé par les règles : il ajoute directement un lieu).

### 2.2 Retirer sa demande (demandeur) : lot de 2

- `delete` `placeRequests/{moi}_{k}` ;
- `set` avec fusion `meta/placeRequests` = `{ pending: increment(-1), lastReqId: "{moi}_{k}", updatedAt: serverTimestamp() }`.

**Repli** : si ce lot est refusé alors que la demande existe (compteur recalé à 0 par un créateur alors que des demandes existent : le résultat serait -1, hors 0 à 10), **réessayer une fois avec `pending: 0`** (valeur absolue, acceptée). Si le repli est aussi refusé, afficher le message générique (`place_request_error_generic`) : le créateur pourra supprimer la demande, ou elle expirera.

### 2.3 Accepter une demande (créateur) : lot de 3 atomique

1. Choisir l'identifiant du lieu **comme pour « Ajouter un lieu »** (premier de `p1`, `p2`, `p3` libre en gratuit ; identifiant automatique en Miff+) ; si le créateur n'est pas Miff+ et n'a plus d'emplacement libre, ou si la demande est une zone de danger et qu'il n'est pas Miff+ : **paywall sans écrire** (la demande reste en attente).
2. Lot :
   - `set` `places/{placeId}` : copie de `name`, `type`, `address`, `lat`, `lng`, `radiusM` ; **`category` = la catégorie de la demande**, `priority` = `null`, `createdBy` = moi, `createdAt` et `updatedAt` = `serverTimestamp()` ; **les règles de création d'un lieu s'appliquent telles quelles** ;
   - `delete` `placeRequests/{demandeur}_{k}` ;
   - `set` avec fusion `meta/placeRequests` = `{ pending: <nombre de demandes restantes>, lastReqId: null, updatedAt: serverTimestamp() }`.
3. Si le lot est refusé par la règle du lieu (abonnement expiré entre-temps, limite atteinte), **tout est refusé** et la demande reste : rouvrir la liste, afficher le paywall (jamais d'erreur brute). Les géofences se programment comme pour un lieu créé par le créateur (data-model §7).

Le lieu accepté **n'indique pas qui l'a demandé**. Le demandeur ne reçoit rien (D8).

### 2.4 Refuser une demande (créateur) : lot de 2

- `delete` `placeRequests/{demandeur}_{k}` ;
- `set` avec fusion `meta/placeRequests` = `{ pending: <nombre restant>, lastReqId: null, updatedAt: serverTimestamp() }`.

**Rien n'est écrit pour le demandeur** (D3 : ni message, ni notification, ni statut).

### 2.5 Le créateur écrit TOUJOURS une valeur absolue

`<nombre restant>` = nombre de demandes de la Miff **après** l'action, lu dans la liste que le créateur affiche déjà (0 à 10). **Ne pas utiliser `increment(-1)` côté créateur** : si le compteur avait dérivé trop bas, le résultat serait négatif et le lot serait refusé. Une valeur absolue **répare** le compteur à chaque action du créateur (bornes : 0 à 10 ; 11 est refusé ; `lastReqId` doit être `null`).

### 2.6 Nettoyer (créateur) : demandes expirées et demandes d'un ex-membre

- **Expiration à 30 jours** : **aucune règle ne la contrôle** ni ne supprime : les **deux clients masquent** toute demande dont `createdAt` date de plus de 30 jours, et la **suppriment au passage** : le créateur par un lot « refuser » (suppression + compteur absolu) ; le demandeur par un lot « retirer ». Avec Blaze, une fonction planifiée pourrait le faire (aucune pour l'instant).
- **Demande d'un ex-membre** (l'auteur n'est plus dans la liste des membres de la Miff, cas du membre retiré par le créateur ou parti sans avoir pu retirer ses demandes) : la **masquer**, puis la supprimer dans un lot séparé (suppression + compteur absolu). Le lot « retirer un membre » (data-model §8) **n'est pas modifié** : ne **pas** y ajouter de suppression de demande (il est à 18 lectures de règles sur 20).
- Ces suppressions du créateur sont **sans contrainte** sur le compteur (le créateur est maître de sa Miff) : par lots de 5 suppressions au plus par commodité.

### 2.7 Départ d'un membre qui a des demandes (D9)

1. **Avant** le lot « Quitter » (inchangé) : pour chaque demande en attente du membre (3 au plus), lot « retirer » de 2 (§2.2).
2. Puis le lot « Quitter » habituel. **Un échec n'empêche pas de quitter** : les demandes restantes deviennent orphelines et seront nettoyées par le créateur (§2.6). Après son départ, le membre peut encore supprimer sa propre demande (avec le compteur) mais ne peut ni en créer ni en lire.
3. Même retrait des demandes **avant** la suppression de compte (séquence data-model §10, étape 1).

### 2.8 Suppression de la Miff (créateur) : ajout à la séquence existante

**Avant** le lot final (et avant la suppression des lieux) : supprimer les demandes (5 par lot au plus, aucune contrainte), puis `meta/placeRequests`. Après la suppression de la Miff, rien n'est lisible.

## 3. Lectures

| Qui | Requête | Remarque |
|---|---|---|
| Créateur | `miffs/{miffId}/placeRequests` (liste, **sans filtre**) | 10 documents au plus. **1 lecture de règles.** Écouteur seulement au premier plan / au lancement à froid (ticket §7.1). |
| Demandeur | `miffs/{miffId}/placeRequests` **`where('requestedBy', '==', moi)`** | **Filtre obligatoire.** 3 lectures de règles. Écouteur seulement tant que l'écran est visible. |
| Demandeur | `get` d'un emplacement `{moi}_{k}` | Un emplacement libre est « introuvable », pas refusé. |
| Autre membre | rien | Toute lecture est refusée. |
| Créateur | `get meta/placeRequests` | Seulement pour un recalage ; en pratique inutile (il écrit des valeurs absolues). |

Lecture d'un **groupe de collections** (`collectionGroup('placeRequests')`) : **refusée** pour tous ; ne pas l'utiliser.

Cache local : les demandes (adresse, coordonnées) sont stockées dans le cache de l'appareil ; même contrat de purge que les lieux (data-model §8 : déconnexion, suppression de compte, perte d'accès à la Miff).

## 4. Erreurs attendues (`permission-denied`) et comportement du client

| Situation | Cause probable | Comportement (ton bienveillant, jamais « refusé ») |
|---|---|---|
| Création d'une demande refusée alors que le client a vérifié la limite de 3 | Course entre deux appareils : l'emplacement vient d'être pris | Relire les demandes, afficher `place_request_limit_member` ou recommencer avec l'emplacement libre. |
| Création refusée, tous emplacements libres | **Plafond de 10 de la Miff atteint** (ou compteur dérivé trop haut) | `place_request_limit_miff`. Ne pas réessayer en boucle. |
| Création refusée, type `risk` | Demandeur non Miff+ (abonnement expiré entre-temps) | Retomber sur le paywall / proposer « zone de confiance ». |
| Création / retrait refusé hors connexion (écriture différée) | Règle évaluée à la reconnexion | La carte « En attente » disparaît sans message (ticket R10) ; relire. |
| Création refusée : membre retiré, Miff supprimée | Plus d'accès | Message neutre habituel de perte d'accès (data-model §8 « Perte d'accès »). |
| Accepter refusé | Règle de lieu (limite gratuite, zone de danger sans Miff+) | Paywall ; la demande reste. |
| Accepter ou refuser : la demande n'existe plus | Retirée, expirée, traitée sur un autre appareil | `place_request_gone`, retour à la liste. (Une suppression d'un document absent n'est pas une erreur : vérifier l'existence dans l'écouteur.) |
| Retrait refusé | Compteur recalé à 0 | Repli `pending: 0` (§2.2). |
| Tout autre refus | Bug de client ou schéma non à jour | `place_request_error_generic` ; remonter dans les journaux, **ne jamais écrire sans `serverTimestamp()` ni avec une clé de plus**. |

## 5. Ordre de livraison et sécurité

1. Fusion de la PR backend LIEU-REQ-A (cette PR) ; **revue indépendante** ; **déploiement des règles par le PM** (la commande exacte n'est donnée qu'après cette revue et son accord).
2. Seulement ensuite : recette Android sur appareil (LIEU-REQ-B / C). Avant le déploiement, toute écriture de demande est refusée (aucun risque pour les données).
3. **Retour arrière** (soumis à un « oui » du PM) : redéployer le fichier de `main` d'avant cette PR ; les demandes déjà écrites ne seraient plus ni lisibles ni supprimables.

App Check : à activer (les rafales de création / retrait ne sont pas limitables par les règles ; plafonds durs de 3 par membre et 10 par Miff).

## 6. Catégorie (décision du PM du 2026-11-11)

La demande porte `category` (obligatoire, liste fermée des lieux). Le client du créateur la **copie** dans le lieu à l'acceptation ; le créateur peut ensuite la modifier avec l'écran existant (D7). Une demande dont la catégorie manque ou est inconnue est refusée à la création (`permission-denied`) : le formulaire de demande doit donc **imposer le choix** comme le fait la création d'un lieu.

## 7. Tests de référence

`backend/tests/rules/31-demande-lieu.test.js` (RQ1 à RQ27) rejoue exactement ces lots (`backend/tests/helpers/requests.js` : `createRequestOps`, `withdrawRequestOps`, `declineRequestOps`, `acceptRequestOps`). Les clients peuvent s'en servir comme exemples d'écriture ; les clés, les valeurs absolues et les `set` avec fusion y sont tels que les règles les attendent.
