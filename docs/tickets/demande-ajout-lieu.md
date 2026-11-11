# Demande d'ajout d'un lieu de confiance (LIEU-REQ)

**Statut** : brouillon, décisions du PM intégrées (2026-11-11) ; relecture par `backend-data` (règles, compteur) et `android-dev` à faire avant « Backlog dev IA »
**Origine** : demande directe du PM (2026-11-11), sans ticket Notion ni maquettes. Android d'abord, iOS ensuite.
**Plateformes** : Android (PR en premier), iOS (suite). Les règles Firestore sont communes.
**Dépend de** : lieux de la Miff (création par le créateur, gratuit = 3 lieux de confiance `p1`/`p2`/`p3`, zone de danger = Miff+), Profil, centre de notifications local (`docs/tickets/notifications.md` §20), paywall.

> **Note sur les sources (à lire par le relecteur).** Cette rédaction a été faite sans accès au shell ni à `origin/main` : j'ai lu le dépôt tel qu'il est dans `C:\dev\miff` (branche `main` locale `e8cdde6`, en retard sur `origin/main`). Dans ce checkout, `backend/firestore.rules` n'autorise que `p1` en gratuit et `docs/tickets/notifications.md` n'existe pas. J'ai donc suivi l'énoncé du PM (gratuit = `p1`, `p2`, `p3` ; centre de notifications local 30 jours / 100 éléments, cloche du Feed, planificateur). **À re-vérifier contre `origin/main`** : `validPlaceCreate` (lieux gratuits), le §20 du ticket Notifications et le nom exact des types (`trusted` / `risk`, libellé produit « zone de danger »).

---

## 1. Vision

Aujourd'hui seul le créateur d'une Miff définit ses lieux. Un membre qui connaît un lieu utile au cercle (l'école, chez Mamie, le club de sport) n'a aucun moyen de le proposer autrement qu'en le demandant de vive voix.

Ce ticket ajoute une **demande d'ajout** : un membre propose un lieu, le créateur l'accepte ou non. Rien n'est jamais écrit par le membre comme un vrai lieu : il ne dépose qu'une **demande**, visible du seul créateur. La décision reste entièrement entre les mains du créateur (consentement, sécurité, limites gratuites).

Principes de ton (CLAUDE.md) : on ne culpabilise ni celui qui demande, ni celui qui refuse. **Le demandeur ne reçoit aucun message de refus** (décision du PM) : la demande disparaît simplement de sa liste.

---

## 2. Décisions du PM (2026-11-11), tranchées

| # | Sujet | Décision |
|---|---|---|
| D1 | Notification au créateur, sans serveur | Cloche + point non lu + ligne dans le centre de notifications + demande en tête de la liste à l'ouverture (§7). Pas de push (Spark). |
| D2 | Qui peut demander, plafonds | Tout membre **non créateur**. **3 demandes en attente par membre, 10 par Miff** (plafonds durs, §6). |
| D3 | Refus et expiration | **Refuser = supprimer la demande.** Le demandeur ne reçoit **rien** (ni notification, ni message). Il peut redemander (même lieu compris). **Une demande non traitée expire à 30 jours.** |
| D4 | Visibilité | Seulement le **créateur** et le **demandeur**. Les autres membres ne voient rien. |
| D5 | Type de lieu | **Zone de confiance** (`trusted`) ; **zone de danger** (`risk`) **uniquement si le demandeur est Miff+**. Pour l'**accepter**, le créateur doit être Miff+ (sinon paywall ; refuser reste possible). Les règles refusent toute demande `risk` d'un non Miff+. |
| D6 | Miffs concernées | Une demande = **une seule Miff** (la Miff sélectionnée sur le Profil). |
| D7 | Modification avant acceptation | Non : le créateur **accepte tel quel**, puis modifie le lieu avec l'écran de modification existant. |
| D8 | Statut côté demandeur | Il voit « En attente » dans sa liste. **Rien** sur l'acceptation, sauf que le lieu apparaît dans la liste de la Miff. |
| D9 | Le membre quitte la Miff | Ses demandes en attente sont **supprimées**. |

Détails dérivés de ces décisions, à relire : l'ordre de la liste, le point sur l'onglet Profil, etc. (§14, « Questions restantes »).

---

## 3. Parcours

### 3.1 Membre (non créateur)
1. Profil > Miff sélectionnée > « Lieux de la Miff » : le bouton « **Demander l'ajout d'un lieu** » remplace « Ajouter un lieu » (même emplacement, même design, un seul exemplaire).
2. Il ouvre le **même écran de configuration** que la création de lieu (nom, adresse, périmètre sur la carte, type). Le CTA est « **Soumettre la demande** ».
3. Confirmation : retour à la liste, où sa demande apparaît **en première position**, marquée « En attente ». Il peut la **retirer** (supprime la demande) ; il peut en avoir jusqu'à 3 à la fois.
4. Quand le créateur accepte, la demande disparaît de sa liste et **le lieu apparaît dans la liste de la Miff**. Quand le créateur refuse (ou laisse expirer), la demande disparaît simplement : aucun message.

### 3.2 Créateur
1. Il reçoit la demande selon §7 (cloche, ligne du centre, point non lu).
2. Profil > « Lieux de la Miff » : les demandes en attente sont **en tête de liste**, avec un design qui les distingue des lieux déjà dans la Miff, et le nom de **qui** a demandé.
3. Depuis la liste **ou** le détail du lieu demandé (qui montre aussi « Demandé par … »), il peut **Accepter** ou **Refuser**.
4. **Accepter** : le lieu est créé tel que proposé, la demande est supprimée. Si le créateur n'est pas Miff+ et a déjà ses 3 lieux gratuits (ou si la demande est une zone de danger), le **paywall** s'affiche et la demande reste en attente. **Refuser** reste toujours possible.
5. Il peut ensuite modifier le lieu avec l'écran existant.

---

## 4. Écrans et états (Android ; iOS identique en comportement)

Pas de maquette : on **réutilise** les composants de la liste des lieux, du détail d'un lieu et du formulaire de création. Les écarts (marquage « En attente », bandeau du détail, type verrouillé) sont listés dans la PR.

### 4.1 Liste « Lieux de la Miff »

| État | Membre | Créateur |
|---|---|---|
| **Liste avec lieux** | Bouton « Demander l'ajout d'un lieu ». Ses demandes en attente en tête (carte distincte, « En attente »). | Bouton « Ajouter un lieu » (inchangé). Demandes en attente en tête (carte distincte, « Demandé par Léa », boutons Accepter / Refuser), puis les lieux. |
| **Vide** (aucun lieu, aucune demande) | « Pas encore de lieu pour cette Miff. » + bouton de demande. | Texte existant + « Ajouter un lieu ». |
| **Vide de lieux mais demandes en attente** | Ses demandes, puis le texte « Pas encore de lieu pour cette Miff. » | Les demandes, puis le texte existant. |
| **Chargement / hors connexion** | Comme la liste actuelle (cache). Demande saisie hors connexion : « Ta demande partira dès que tu seras connecté. » | Comme la liste actuelle ; les demandes déjà en cache s'affichent. |
| **Limite de 3 atteinte** (membre) | Le tap sur le bouton affiche un message doux (clé `place_request_limit_member`), sans ouvrir le formulaire. | n/a |

**Design « demande en attente »** (à valider par `design-system`) : carte à fond atténué ou bordure en pointillés, pastille « En attente » ; **jamais de rouge ni d'orange** (ton non anxiogène). Elle est visuellement différente d'un lieu actif (pas de rayon actif, pas d'icône de présence).

### 4.2 Détail d'une demande
- Mêmes éléments que le détail d'un lieu (nom, adresse, carte et rayon, type) en lecture seule + bandeau « Ce lieu n'est pas encore dans la Miff. Il y sera si tu acceptes. » (créateur) ou « En attente de réponse. » (demandeur).
- Créateur : « Demandé par {prénom} » + date, boutons **Accepter** / **Refuser**.
- Demandeur : bouton « Retirer ma demande ».
- Pas de modification, pas de suppression de lieu (la demande n'est pas un lieu).
- **Demande disparue** (déjà traitée sur un autre appareil, expirée, retirée) : retour à la liste avec le message neutre `place_request_gone`.

### 4.3 Formulaire (membre)
- Même écran que la création, CTA « Soumettre la demande », titre « Proposer un lieu ».
- Type : « Zone de confiance » par défaut. « Zone de danger » : **même comportement que le formulaire de création pour un compte non Miff+** (option marquée Miff+, ouvrant le paywall) ; un demandeur Miff+ peut la choisir. À confirmer en recette (§14, Q-R4).
- Sous le bouton, une ligne d'information : « Le créateur de la Miff verra ta demande et décidera de l'ajouter ou non. »
- Avant l'envoi, l'app lit les demandes en attente du membre (1 requête) pour : connaître l'emplacement libre (§5) et afficher le plafond de 3.
- Erreurs : limite de 3, limite de la Miff (10), hors connexion, générique : toujours en phrases douces (§13).

### 4.4 États « attente / acceptée / refusée »

| État | Ce que voit le demandeur | Ce que voit le créateur |
|---|---|---|
| **En attente** | Carte « En attente » dans sa liste. | Carte « Demandé par X » en tête + ligne du centre + point non lu. |
| **Acceptée** | Rien de spécial : la carte disparaît, le lieu est dans la liste (D8). | Toast « « {lieu} » a été ajouté à la Miff. » |
| **Refusée** | **Rien** : la carte disparaît, sans message (D3). | Toast neutre « Demande retirée de la liste. » |
| **Expirée (30 jours)** | La carte disparaît sans message. | La carte disparaît sans message. |
| **Retirée par le demandeur** | Toast « Demande retirée. » | La carte disparaît (si l'écran est ouvert, en direct). |

### 4.5 Paywall (créateur)
- Déclencheur : tap sur **Accepter** quand (a) le créateur n'est pas Miff+ ET a déjà 3 lieux, ou (b) la demande est une zone de danger et le créateur n'est pas Miff+.
- Même paywall que l'ajout d'un lieu, avec une ligne de contexte (`place_request_paywall_limit` / `place_request_paywall_danger`) et la phrase « La demande reste en attente, tu peux aussi la refuser. »
- Fermer le paywall sans s'abonner : retour à la liste, **demande inchangée**. Abonnement fait : l'acceptation reprend sans ressaisie.
- **Abonnement inconnu** (hors connexion, lecture impossible) : on n'ouvre pas le paywall à tort ; message `place_request_subscription_unknown` (même logique que « Ajouter un lieu » existant).
- Le créateur non Miff+ **reçoit** toujours les demandes, même avec ses 3 lieux (aucun contrôle de limite à la création d'une demande).

---

## 5. Modèle de données proposé

### 5.1 `miffs/{miffId}/placeRequests/{reqId}`

**Identifiant** = `{uid du demandeur}_{k}`, avec `k` ∈ {1, 2, 3} : trois **emplacements fixes** par membre. C'est le même principe que les emplacements `p1`/`p2`/`p3` des lieux : les règles ne savent pas compter, elles savent seulement imposer un identifiant. Un 4e emplacement est impossible. L'app choisit le premier emplacement libre après avoir lu les demandes du membre.

| Champ | Type | Règle |
|---|---|---|
| `requestedBy` | string | = `request.auth.uid`. Immuable. |
| `name` | string 1–60 | Mêmes limites qu'un lieu. |
| `type` | `"trusted"` \| `"risk"` | `risk` exige que le **demandeur** soit Miff+ au moment de l'écriture (§6). |
| `address` | string ≤ 200 \| null | **Sensible** (peut être le domicile d'un tiers). |
| `lat`, `lng` | number | Bornes d'un lieu ; 5 décimales. **Sensible.** |
| `radiusM` | int 50–2000 | L'UI propose ≥ 100 m. |
| `createdAt` | timestamp | = heure serveur. Sert à l'expiration à 30 jours (côté client, §5.3). |

Aucun autre champ (clés refusées) : **pas de `status`** (une demande refusée est supprimée, D3), **pas de message libre** (moins de surface d'abus), **pas de nom du demandeur recopié** (le créateur le lit dans la fiche de membre qu'il a déjà en cache ; une demande dont l'auteur n'est plus membre est masquée, §5.4). **Aucune modification** après création (update interdit) : pour changer, on retire et on redemande.

### 5.2 Compteur de la Miff : `miffs/{miffId}/meta/placeRequests`

Les règles ne peuvent pas compter les documents d'une sous-collection ; pour tenir le plafond de **10 par Miff** (D2), un petit document compteur est tenu **dans le même lot** que chaque création ou retrait de demande.

| Champ | Type | Note |
|---|---|---|
| `pending` | int 0–10 | Nombre de demandes en attente. |
| `lastReqId` | string \| null | Dernière demande qui a modifié le compteur (sert à lier le compteur à la demande, §6). `null` quand c'est le créateur qui écrit. |
| `updatedAt` | timestamp | Heure serveur. |

- Les membres l'écrivent avec **`increment(+1)` / `increment(-1)`** (ils ne le lisent pas : **lecture réservée au créateur**).
- Limite connue : si un lot du créateur échoue en cours de route, le compteur peut dériver. Le créateur peut le recaler (il connaît le nombre réel de demandes qu'il lit). Signal : un membre bloqué par la limite de la Miff alors que le créateur voit moins de 10 demandes.
- **Repli si `backend-data` juge ce compteur trop fragile** : plafond dur par membre (3, emplacements) seulement, soit au plus 3 × 14 membres = 42 demandes ; le plafond de 10 devient alors un affichage côté créateur. À arbitrer avant le développement (§14, Q-R6).

### 5.3 Cycle de vie
- **Création** (membre) : lot de 2 écritures : demande + compteur (+1).
- **Retrait** (membre) : lot de 2 : suppression de la demande + compteur (−1).
- **Accepter** (créateur) : lot de 3 : création de `places/{id}` (tel que demandé, `type` identique, `createdBy` = créateur, valeurs par défaut de la création pour `priority`) + suppression de la demande + compteur (−1). Identifiant du lieu : premier de `p1`/`p2`/`p3` libre en gratuit ; identifiant automatique en Miff+. **Atomique : si le lieu est refusé (limite), la demande n'est pas supprimée.**
- **Refuser** (créateur) : lot de 2 : suppression de la demande + compteur (−1). Rien n'est écrit pour le demandeur.
- **Expiration à 30 jours** : **aucune suppression automatique sur Spark.** Une demande de plus de 30 jours est **masquée par les deux clients** (calcul sur `createdAt`) et **supprimée au passage** par celui qui la rencontre (créateur : lot de suppression ; demandeur : retrait), ce qui libère l'emplacement et le compteur. Avec Blaze, une fonction planifiée pourrait la supprimer.
- **Le membre quitte la Miff** (D9) : avant le lot « Quitter », son app retire ses demandes **une par une** (un lot de 2 par demande, 3 au plus), car la règle du compteur doit pouvoir lier chaque décrément à une demande ; puis lot « Quitter » inchangé. Un échec n'empêche pas de quitter.
- **Le créateur retire un membre** : le lot de retrait (déjà à 18 appels de règles sur 20) **n'est pas modifié**. Les demandes de l'ex-membre sont **masquées** (l'auteur n'est plus dans la liste des membres) puis supprimées par le créateur dans un lot séparé à son prochain affichage de la liste.
- **Suppression de la Miff / du compte créateur** : ajouter à la séquence existante (data-model §8) la suppression des `placeRequests` (≤ 5 par lot, comme les lieux) et du compteur, **avant** le lot final.
- **Fin du Miff+ du demandeur** : sa demande `risk` en attente reste valable ; l'acceptation ne dépend que du Miff+ du créateur.

### 5.4 Lien avec la création d'un lieu
Une demande a la forme d'un lieu **sans** `createdBy`, `updatedAt`, `priority`. Accepter = copier `name`, `type`, `address`, `lat`, `lng`, `radiusM` dans un lieu créé par le créateur. **Les règles de création d'un lieu ne changent pas** : limites gratuites (`p1`–`p3`, `trusted` seulement) et `risk` = Miff+ du créateur s'appliquent donc à l'acceptation exactement comme à la création directe. Le lieu accepté n'indique pas qui l'a demandé.

---

## 6. Règles de sécurité proposées (à écrire dans `backend/firestore.rules`, non déployées)

**Aucun déploiement sans l'accord explicite du PM** (procédure `docs/dev/deploiement-regles.md`). Tant que ces règles ne sont pas déployées, les écritures de demande sont refusées en production : la recette Android sur appareil ne peut commencer qu'après le déploiement.

### 6.1 Cas par cas

| Chemin | Qui lit | Qui écrit |
|---|---|---|
| `placeRequests/{reqId}` get / list | **Créateur** (toutes). **Demandeur** : seulement les siennes (`requestedBy == uid`, requête filtrée), s'il est **encore membre** et la Miff existe. **Les autres membres : refus.** | — |
| `placeRequests/{reqId}` create | **Membre non créateur**, identifiant `{uid}_{1..3}`, forme exacte (clés listées §5.1), `requestedBy == uid`, `createdAt == request.time`, `type == 'trusted'` ou (`type == 'risk'` ET **demandeur Miff+**), compteur de la Miff +1 dans le même lot. | |
| `placeRequests/{reqId}` update | — (jamais) | |
| `placeRequests/{reqId}` delete | **Demandeur** (sa demande, compteur −1 dans le même lot) ou **créateur** (aucune contrainte sur le compteur : le créateur est maître de sa Miff). | |
| `meta/placeRequests` get | **Créateur** | |
| `meta/placeRequests` create / update | **Créateur** (valeur libre 0–10, `lastReqId == null`) ou **membre** lié à une création/un retrait de **sa** demande dans le même lot (`increment` ±1 vérifié). | |
| `meta/placeRequests` delete | Créateur (nettoyage à la suppression de la Miff). | |
| `places/*` | **Inchangé** : un membre ne peut **jamais** écrire un lieu, seulement une demande. | |

Rien n'est lisible une fois la Miff supprimée (contrôle d'existence de la Miff, comme pour les autres sous-collections).

### 6.2 Brouillon de règles (à affiner par `backend-data`, avec tests dans l'émulateur)

```
// Emplacement de demande : {uid}_{1..3}. uid est celui de l'authentification (alphanumérique).
function isOwnRequestId(reqId) {
  return isSignedIn() && reqId.matches('^' + request.auth.uid + '_[123]$');
}

function validRequestShape(d) {
  return d.keys().hasOnly(['requestedBy', 'name', 'type', 'address', 'lat', 'lng', 'radiusM', 'createdAt'])
    && d.keys().hasAll(['requestedBy', 'name', 'type', 'address', 'lat', 'lng', 'radiusM', 'createdAt'])
    && isStr(d.name, 1, 60)
    && d.type in ['trusted', 'risk']
    && isNullOrStr(d.address, 200)
    && d.lat is number && d.lat >= -90 && d.lat <= 90
    && d.lng is number && d.lng >= -180 && d.lng <= 180
    && d.radiusM is int && d.radiusM >= 50 && d.radiusM <= 2000;
}

// Le compteur change dans le même lot, lié à CETTE demande, de delta (+1 création, -1 retrait).
function counterMoved(miffId, reqId, delta) {
  let c = /databases/$(database)/documents/miffs/$(miffId)/meta/placeRequests;
  return getAfter(c).data.lastReqId == reqId
    && getAfter(c).data.pending >= 0 && getAfter(c).data.pending <= 10
    && (
      (exists(c) && getAfter(c).data.pending == get(c).data.pending + delta)
      || (!exists(c) && delta == 1 && getAfter(c).data.pending == 1)
    );
}

match /placeRequests/{reqId} {
  allow get: if isOwner(miffId)
    || (isOwnRequestId(reqId) && isMember(miffId) && miffAlive(miffId));
  allow list: if isOwner(miffId)
    || (resource.data.requestedBy == request.auth.uid && isMember(miffId) && miffAlive(miffId));

  allow create: if isOwnRequestId(reqId)
    && validRequestShape(request.resource.data)
    && request.resource.data.requestedBy == request.auth.uid
    && request.resource.data.createdAt == request.time
    && (request.resource.data.type == 'trusted' || isPremium(request.auth.uid))  // D5
    && isMember(miffId)
    && get(/databases/$(database)/documents/miffs/$(miffId)).data.ownerUid != request.auth.uid
    && counterMoved(miffId, reqId, 1);

  allow update: if false;

  allow delete: if (isSelf(resource.data.requestedBy) && counterMoved(miffId, reqId, -1))
    || isOwner(miffId);
}

match /meta/placeRequests {
  allow get: if isOwner(miffId);
  allow list: if false;
  allow create, update: if
    (request.resource.data.lastReqId == null && isOwner(miffId) && /* forme : pending int 0..10, updatedAt == request.time */ true)
    || (request.resource.data.lastReqId != null && validRequestCounterByMember(miffId));
  allow delete: if isOwner(miffId);
}
```

`validRequestCounterByMember` (à écrire) : clés exactes `pending`, `lastReqId`, `updatedAt` ; `updatedAt == request.time` ; `isMember(miffId)` ; `lastReqId` de la forme `{uid}_{1..3}` du membre ; **soit** la demande `lastReqId` n'existe pas avant et existe après (création) **et** `pending` = ancienne valeur + 1 (ou 1 si le compteur n'existait pas), **soit** elle existe avant et pas après (retrait) **et** `pending` = ancienne valeur − 1. Une création et un retrait « croisés », ou un décrément sans suppression, sont refusés.

**Points de vigilance pour `backend-data`** :
1. `increment` : vérifier dans l'émulateur que `request.resource.data.pending` voit bien la valeur finale ; sinon, remplacer par un compteur géré autrement.
2. Le chemin `meta/placeRequests` (collection `meta`) ne doit pas entrer en conflit avec la règle finale « tout le reste refusé ».
3. L'ordre `||` pour limiter les lectures (branche créateur testée d'abord via `lastReqId == null`).
4. Le plafond 10 doit rester **dur** : test « 11e demande refusée », « un membre ne peut pas décrémenter sans retirer sa demande ».

### 6.3 Budget de lectures de règles (estimation par comptage manuel, sans cache ; à mesurer avec la méthode de DUR-3 ; limite : 20 par lot, marge visée ≥ 2)

| Opération | Appels de règles (pire cas) |
|---|---|
| Créer une demande `trusted` (lot de 2) | ~9 sur 20 (membre 1 + Miff 1 + compteur 3 + règle du compteur ~4) |
| Créer une demande `risk` (lot de 2) | ~12 sur 20 (+3 pour le Miff+ du demandeur) |
| Retirer une demande (lot de 2) | ~7 sur 20 |
| Accepter, créateur Miff+ (lieu + suppression + compteur, lot de 3) | ~11 sur 20 |
| Refuser (lot de 2) | ~5 sur 20 |
| Lister les demandes (créateur) | ≤ 2 sur 10 |
| Lister ses demandes (demandeur) | 2 sur 10 |

Rien ne s'ajoute aux lots existants (créer/rejoindre/quitter/retirer un membre/supprimer une Miff) : **le lot de retrait d'un membre reste à 18 sur 20, le lot « Créer et partager » premium à 19 sur 20 : ne pas y toucher.**

### 6.4 Plafonds (anti-abus)
| Plafond | Dur ? | Mécanisme |
|---|---|---|
| 3 demandes en attente par membre et par Miff | Oui | Emplacements `{uid}_{1..3}` |
| 10 demandes en attente par Miff | Oui (si le compteur est retenu) | Compteur `meta/placeRequests`, §5.2 |
| Fréquence (créer / retirer / recréer en boucle) | **Non** (aucune limitation de débit possible en règles) | App Check (à activer), plafonds ci-dessus ; une rafale ne dépasse jamais 3 demandes simultanées ; côté créateur la ligne du centre est regroupée (§7.1) ; recours : retirer le membre |
| Taille des textes | Oui | Mêmes bornes que les lieux, aucun champ libre supplémentaire |

---

## 7. Notification au créateur

### 7.1 Ce qui est livré (décision D1, contraintes Spark)
Quand l'app du créateur **est ouverte** (au premier plan) ou **au lancement à froid**, elle connaît les demandes en attente (requête sur toutes les demandes de la Miff, 10 documents au plus, via un écouteur) et :
1. **Ligne dans le centre de notifications local** : « Léa propose un lieu pour « Famille » » (un élément par demande ; **regroupé par demandeur et par Miff** tant qu'il n'est pas lu : « Léa a proposé 3 lieux pour « Famille » »). Dédoublonnée par identifiant de demande (jamais deux lignes pour la même demande). Règles existantes du centre : 30 jours, 100 éléments.
2. **Point non lu** sur la cloche du Feed, selon le mécanisme existant.
3. **À l'ouverture** de Profil > Lieux de la Miff concernée, la demande est **en tête de liste**. Toucher la ligne du centre ouvre cette liste (Miff concernée sélectionnée).
4. Une demande qui n'est plus en attente (acceptée, refusée, retirée, expirée) garde sa ligne dans le centre ; la toucher mène à la liste avec `place_request_gone` si la demande n'existe plus.

Aucune notification système (barre d'état) n'est nécessaire pour cette version : pas de permission de notification à demander pour ce ticket.

### 7.2 Ce qui n'est PAS possible sans serveur
- **Prévenir un créateur dont l'app est fermée ou en arrière-plan** : aucun push n'est possible (envoyer exige des identifiants serveur, donc Cloud Functions, donc Blaze).
- Garantir un délai de prise de connaissance : le créateur découvre la demande **à sa prochaine ouverture de l'app**, qui peut être des heures plus tard.
- Rappeler au créateur « 2 demandes attendent » : son téléphone ne sait pas qu'elles existent tant qu'il n'a pas lu.
- Une consultation périodique en arrière-plan n'est pas recommandée : 1 lecture minimale à chaque passage (même sans demande), soit 24 à 96 lectures par jour et par créateur contre ~75 lectures par jour pour tout l'usage actuel (data-model §12).

### 7.3 Ce qu'apporterait Blaze + FCM
Une fonction déclenchée à la création d'une demande enverrait un push au créateur (jetons déjà prévus : `users/{uid}/devices`) ; une fonction planifiée supprimerait les demandes de plus de 30 jours. Coût : une invocation par demande, négligeable face aux quotas gratuits. Le modèle de données de ce ticket **n'a pas à changer** pour l'accueillir. Décision Blaze : reportée par le PM (data-model §13).

### 7.4 Valeur par défaut recommandée (validée, D1)
In-app seulement (§7.1). Deux options à l'étude, **non retenues pour cette version** (§14, Q-R2, Q-R3) : un lien discret « Prévenir {créateur} » pour le demandeur, et une vérification opportuniste au réveil d'un géofence.

## 8. Notification au demandeur

**Aucune** (D3 et D8) : ni à l'acceptation, ni au refus, ni à l'expiration. Seul signe : sa demande « En attente » apparaît dans sa liste, puis disparaît ; si elle a été acceptée, le lieu est dans la liste de la Miff. Aucun élément n'est ajouté à son centre de notifications.

---

## 9. Coûts (Spark)

Estimations, pas des mesures.

| Poste | Lectures / écritures |
|---|---|
| Créateur, ouverture à froid (par Miff créée) | +1 lecture (requête des demandes, même vide) + 1 par demande en attente |
| Créateur, application ouverte | 1 lecture par demande nouvelle ou modifiée (écouteur actif seulement au premier plan) |
| Demandeur (membre non créateur), ouverture de la liste des lieux | +1 lecture (ses demandes, requête vide comprise) ; écouteur uniquement pendant que l'écran est visible |
| Demande (cycle complet) | 2 écritures (création) + 2 (suppression) ; acceptation : 3 |
| Stockage | Quelques centaines d'octets par demande, 10 au plus par Miff |
| Lectures de règles | §6.3 : aucune augmentation des lots existants |

Impact sur le ratio lectures ÷ utilisateurs actifs (~75) : de l'ordre de +1 à +3 lectures par jour et par utilisateur actif. Signaux de bascule inchangés (data-model §12). **Aucun coût payant.**

---

## 10. Risques

| # | Risque | Mitigation / statut |
|---|---|---|
| R1 | **Spam** : un membre inonde le créateur de demandes | Plafonds durs (3 / 10), regroupement des lignes du centre, retrait du membre par le créateur. Rafales création/retrait non limitables sur Spark : App Check, risque résiduel assumé. |
| R2 | **Surveillance abusive / stalkerware** (risques R16 / R18 du registre cité dans la demande du PM ; à recouper, `onboarding.md` utilise ces numéros pour d'autres risques) : un membre fait ajouter le lieu d'une personne (domicile, travail) pour la suivre via le cercle | Seul le créateur accepte, et voit **qui** demande et **quelle adresse** ; aucune acceptation automatique ; le demandeur n'obtient aucune donnée de position de plus. Le partage de position des membres reste soumis à leur consentement existant. Le texte de la carte ne juge pas mais affiche clairement nom, adresse, demandeur. |
| R3 | **Lieu sensible** : domicile d'un tiers (ex-conjoint, voisin) proposé comme lieu de confiance | Même mitigation ; aucun contrôle automatique possible. Les adresses restent visibles du seul créateur et du demandeur jusqu'à l'acceptation. Risque assumé par le PM, à mentionner dans la politique de confidentialité si les données de demande y sont listées (`release-manager`). |
| R4 | **Zone de danger demandée par un Miff+** (D5) : la zone de danger n'a pas de comportement défini (ticket Feed, Q10 ; ton potentiellement anxiogène ; peut servir de contrôle dans un couple) | Le créateur doit être Miff+ et accepter ; le libellé reprend celui du formulaire existant. **Ce ticket ne définit pas le comportement de la zone de danger.** Risque porté par la décision du PM. |
| R5 | **Découverte tardive** : le créateur ne sait pas qu'une demande attend (pas de push) | Limite connue de Spark (§7.2). Le demandeur voit « En attente » ; expiration à 30 jours. Signal pour Blaze : demandes expirées sans lecture. |
| R6 | **Demande sans réponse = frustration du demandeur** sans message de refus | Voulu par le PM (D3). À surveiller en recette. |
| R7 | **Dérive du compteur** (§5.2) | Test de règles dédiés, recalage par le créateur, repli possible (Q-R6). |
| R8 | **Données orphelines** (membre retiré, Miff supprimée) | Masquage + nettoyage (§5.3), lecture impossible une fois la Miff supprimée. |
| R9 | **Limite de géofences** : accepter ajoute un lieu ; iOS (20) / Android (100) partagés | Logique existante (data-model §7) ; hors périmètre. |
| R10 | **Écriture hors connexion** : la règle (plafond) peut refuser après coup | Écran « partira à la reconnexion » ; en cas de refus différé, la carte « En attente » disparaît sans message (cohérent avec D3). |

---

## 11. Critères d'acceptation (testables)

**Membre**
- **M1** Un membre non créateur voit « Demander l'ajout d'un lieu » (jamais « Ajouter un lieu »), un seul exemplaire, même design.
- **M2** Le créateur voit « Ajouter un lieu » (inchangé) et jamais le bouton de demande.
- **M3** Le bouton ouvre le formulaire de création ; le CTA est « Soumettre la demande ».
- **M4** Soumettre crée la demande (lot de 2) ; elle apparaît en tête de sa liste, « En attente ».
- **M5** Au maximum 3 demandes en attente ; à la 4e, message doux, aucune écriture tentée.
- **M6** « Retirer ma demande » supprime la demande ; l'emplacement est de nouveau libre.
- **M7** Un membre non Miff+ ne peut pas soumettre une zone de danger (UI) ; une écriture directe `risk` est refusée par les règles (test).
- **M8** Un membre Miff+ peut soumettre une zone de danger.
- **M9** Les demandes d'un membre ne sont visibles ni des autres membres, ni dans leur liste, ni via une requête directe (règles).
- **M10** Aucun message, notification ou ligne n'est jamais reçu à l'acceptation, au refus ou à l'expiration.
- **M11** Hors connexion : message « partira dès que tu seras connecté » ; à la reconnexion, la demande part.

**Créateur**
- **C1** À l'ouverture de l'app, une nouvelle demande crée une ligne dans le centre (une seule par demande) et un point non lu sur la cloche.
- **C2** Les demandes en attente sont en tête de la liste, avec un design distinct, et indiquent le prénom du demandeur ; liste et détail.
- **C3** Accepter depuis la liste ou depuis le détail crée le lieu tel que demandé (nom, type, adresse, coordonnées, rayon) et supprime la demande, en un seul lot ; la liste se met à jour.
- **C4** Refuser depuis la liste ou le détail supprime la demande ; rien n'est envoyé au demandeur.
- **C5** Créateur non Miff+ avec 3 lieux : il reçoit les demandes ; Accepter ouvre le paywall, la demande reste ; Refuser fonctionne.
- **C6** Créateur non Miff+ avec moins de 3 lieux : Accepter crée le lieu (identifiant libre parmi `p1`–`p3`).
- **C7** Créateur non Miff+ et demande de zone de danger : Accepter ouvre le paywall ; Refuser fonctionne.
- **C8** Abonnement inconnu : message neutre, pas de paywall.
- **C9** Le créateur peut modifier le lieu accepté avec l'écran existant (D7) ; il n'y a pas de modification avant acceptation.
- **C10** Une demande de plus de 30 jours n'est plus affichée et est supprimée au passage.
- **C11** Le compteur redescend de 1 à chaque acceptation, refus, retrait, expiration nettoyée.

**Départ / suppression**
- **L1** Un membre qui quitte la Miff : ses demandes sont supprimées avant ; elles ne restent pas visibles du créateur.
- **L2** Un membre retiré par le créateur : ses demandes sont masquées, puis supprimées par le créateur.
- **L3** Suppression de la Miff : demandes et compteur supprimés avant le lot final ; aucune donnée lisible ensuite.

**Règles (tests d'émulateur, `backend/rules-test-plan.md`)**
- **S1** Membre : crée `{uid}_1..3` valide, compteur +1 : accepté. `{uid}_4`, `{autreUid}_1`, clé en trop, `type` invalide, `createdAt` du client, `lat` hors bornes : refusés.
- **S2** Le créateur ne peut pas créer de demande ; un non-membre non plus ; un ex-membre non plus.
- **S3** 11e demande : refusée (compteur à 10). Décrément sans suppression : refusé. Incrément sans création : refusé.
- **S4** `risk` d'un non Miff+ : refusé ; d'un Miff+ : accepté.
- **S5** Lecture : créateur oui ; demandeur ses demandes oui (requête filtrée) ; autre membre non ; non-membre non ; Miff supprimée non.
- **S6** Update : refusé pour tous.
- **S7** Delete : demandeur sa demande (avec compteur −1) oui ; autre membre non ; créateur oui.
- **S8** Accepter : lot (lieu + suppression + compteur) accepté pour le créateur Miff+ ; refusé si le lieu est refusé (limite gratuite) : la demande reste.
- **S9** Aucune lecture de règle ne dépasse 20 par lot (mesure avec marge ≥ 2) ; les lots existants (créer/rejoindre/retirer/supprimer) sont inchangés.
- **S10** Un membre ne peut jamais écrire dans `places` (tests existants conservés).

**Ton et accessibilité**
- **T1** Tous les textes sont bienveillants (aucun « refusé », « rejeté », « échec » côté demandeur) ; pas de rouge ni d'orange.
- **T2** Les boutons ont des libellés d'accessibilité ; la carte « En attente » est annoncée comme telle ; tailles de police système respectées.
- **T3** Les chaînes existent en français et en anglais (clés §13).

---

## 12. Découpage en PR

Une branche et une PR par plateforme, jamais de push direct sur `main`.

| PR | Contenu | Remarques |
|---|---|---|
| **LIEU-REQ-A** (backend) | `firestore.rules` (§6), `data-model.md` (§2.12 placeRequests + compteur, §8 lignes Créer/Quitter/Retirer/Supprimer, §9 matrice, §11 données sensibles, §12 budget), `rules-test-plan.md`, tests émulateur S1–S10 | **Non déployée sans accord explicite du PM.** À fusionner avant le dev client ; le déploiement précède la recette. |
| **LIEU-REQ-B** (Android, côté membre) | Bouton, formulaire (CTA, type verrouillé pour non Miff+), liste « En attente », retrait, plafond, expiration côté demandeur, nettoyage au départ | Dépend de A. |
| **LIEU-REQ-C** (Android, côté créateur) | Écouteur, ligne dans le centre + cloche, liste et détail (design distinct), Accepter / Refuser, paywall, nettoyage des orphelins et des demandes expirées, séquence de suppression de Miff | Dépend de A et B. |
| **LIEU-REQ-D / E** (iOS) | Mêmes contenus que B et C | Après recette Android. iOS ne se compile pas en local : CI cloud + TestFlight. |

Pas de maquettes : écarts avec la création existante listés dans chaque PR (règle du 2026-10-28, ajout d'écrans non designés autorisé).

---

## 13. Clés de texte proposées (fr / en)

| Clé | Français | Anglais |
|---|---|---|
| `place_request_cta_member` | Demander l'ajout d'un lieu | Ask to add a place |
| `place_request_form_title` | Proposer un lieu | Suggest a place |
| `place_request_form_submit` | Soumettre la demande | Send request |
| `place_request_form_hint` | Le créateur de la Miff verra ta demande et décidera de l'ajouter ou non. | The Miff's creator will see your request and decide whether to add it. |
| `place_request_sent` | Demande envoyée. Elle est en attente dans la liste. | Request sent. It's pending in the list. |
| `place_request_sent_offline` | Ta demande partira dès que tu seras connecté. | Your request will be sent as soon as you're back online. |
| `place_request_badge_pending` | En attente | Pending |
| `place_request_pending_member` | Ta demande est en attente de réponse. | Your request is waiting for a reply. |
| `place_request_withdraw` | Retirer ma demande | Withdraw my request |
| `place_request_withdrawn` | Demande retirée. | Request withdrawn. |
| `place_request_limit_member` | Tu as déjà 3 demandes en attente. Tu pourras en proposer d'autres quand elles auront été traitées. | You already have 3 pending requests. You can suggest more once they've been handled. |
| `place_request_limit_miff` | Cette Miff a déjà plusieurs demandes en attente. Réessaie un peu plus tard. | This Miff already has several pending requests. Please try again a little later. |
| `place_request_error_generic` | Un petit souci est survenu. Réessaie dans un instant. | Something went wrong. Please try again in a moment. |
| `place_request_section_title` | À valider | To review |
| `place_request_by` | Demandé par {name} | Requested by {name} |
| `place_request_date` | Le {date} | On {date} |
| `place_request_detail_banner_creator` | Ce lieu n'est pas encore dans la Miff. Il y sera si tu acceptes. | This place isn't in the Miff yet. It will be added if you accept. |
| `place_request_detail_banner_member` | En attente de réponse. | Waiting for a reply. |
| `place_request_accept` | Accepter | Accept |
| `place_request_decline` | Refuser | Decline |
| `place_request_accepted_toast` | « {place} » a été ajouté à la Miff. | “{place}” was added to the Miff. |
| `place_request_declined_toast` | Demande retirée de la liste. | Request removed from the list. |
| `place_request_gone` | Cette demande n'est plus en attente. | This request is no longer pending. |
| `place_request_center_one` | {name} propose un lieu pour « {miff} » | {name} suggested a place for “{miff}” |
| `place_request_center_many` | {name} a proposé {count} lieux pour « {miff} » | {name} suggested {count} places for “{miff}” |
| `place_request_paywall_limit` | Tu as atteint la limite de lieux gratuits. Avec Miff+, tu peux ajouter celui-ci et bien d'autres. La demande reste en attente, tu peux aussi la refuser. | You've reached the free places limit. With Miff+, you can add this one and many more. The request stays pending; you can also decline it. |
| `place_request_paywall_danger` | Les zones de danger font partie de Miff+. La demande reste en attente, tu peux aussi la refuser. | Danger zones are part of Miff+. The request stays pending; you can also decline it. |
| `place_request_subscription_unknown` | Impossible de vérifier ton abonnement pour l'instant. Réessaie dans un instant. | We can't check your subscription right now. Please try again in a moment. |
| `place_request_a11y_pending_card` | Demande en attente : {place}, demandée par {name} | Pending request: {place}, requested by {name} |

Aucune clé de refus côté demandeur (D3). Les libellés « Zone de confiance » / « Zone de danger » et le paywall réutilisent les clés existantes.

---

## 14. Questions restantes pour le PM (valeur par défaut recommandée)

Les 9 questions initiales sont **tranchées** (§2). Restent des détails mineurs, avec la valeur par défaut qui sera appliquée sans réponse :

| # | Question | Défaut recommandé |
|---|---|---|
| Q-R1 | Point non lu **aussi sur l'onglet Profil** (utile si le créateur est sur le Profil ou le Feed) en plus de la cloche ? | Oui, jusqu'à l'ouverture de la liste de la Miff concernée. |
| Q-R2 | Offrir au demandeur, après l'envoi, un lien **facultatif** « Prévenir {créateur} » (feuille de partage du téléphone, texte doux, une seule fois, jamais répété) pour compenser l'absence de push ? | **Non** dans cette version (reste discret, comme voulu) ; à reconsidérer avec Blaze. |
| Q-R3 | Vérification **opportuniste** en arrière-plan pour le créateur (une requête, au plus toutes les 6 h, quand l'app se réveille déjà pour un géofence), avec notification système locale ? | **Non** dans cette version (coût : ≤ 4 lectures/jour/créateur, permission de notification à demander) ; candidat naturel avant Blaze. |
| Q-R4 | Un demandeur non Miff+ qui touche « Zone de danger » dans le formulaire : paywall (comme à la création) ou option simplement grisée ? | Paywall, comme pour la création. |
| Q-R5 | Ordre si plusieurs demandes en attente : | Plus ancienne d'abord ; celles du demandeur dans l'ordre inverse (plus récente en haut). |
| Q-R6 | Plafond de 10 par Miff : **compteur strict** (règles plus complexes, risque de dérive, §5.2) ou **repli** (plafond dur de 3 par membre seulement, soit ≤ 42 demandes, et 10 affiché au créateur) ? | Compteur strict, **sous réserve** de l'avis de `backend-data` après les tests ; bascule vers le repli sans nouvelle question si le compteur s'avère fragile. |
| Q-R7 | Refuser : confirmation ou annulation possible (« Annuler » pendant 5 s) ? | Ni l'une ni l'autre (refus sans conséquence pour le demandeur, qui peut redemander). |
| Q-R8 | Le créateur peut-il **désactiver** les demandes pour sa Miff ? | Non (v2) ; le créateur peut déjà retirer un membre. |
| Q-R9 | Faut-il mentionner les demandes de lieux dans la politique de confidentialité et la fiche Data safety / App Privacy (adresse et coordonnées saisies par un membre) ? | Oui, vérification par `release-manager` avant publication (risque juridique assumé, pas de relecture). |

---

## 15. Hors-scope
- Message libre joint à la demande, modification d'une demande, demande de modification ou de suppression d'un lieu existant.
- Notification au demandeur (acceptation, refus, expiration) : exclue par décision.
- Push (Blaze), expiration automatique côté serveur, rappels au créateur.
- Demande portant sur plusieurs Miffs à la fois ; demande depuis le Feed ou l'onboarding.
- Attribution visible du lieu au demandeur après acceptation.
- Définition du comportement d'une zone de danger (ticket Feed, Q10).
- Maquettes Figma (réutilisation des composants existants).
