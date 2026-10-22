# Ticket — Durcissement des règles de sécurité Firestore

**Statut** : Brouillon pour challenge et validation PM (aucun développement lancé, `backend/firestore.rules` et les tests ne sont **pas** modifiés par ce ticket de cadrage)
**Rédigé le** : 2026-10-22 (agent `backend-data`)
**Origine** : revue indépendante de la PR #2 (règles + tests), dont les 4 attaques ci-dessous ont été **prouvées dans l'émulateur** par la relectrice ; décisions du PM du 2026-10-22 sur ATK3 et ATK4.
**Sources lues** : `CLAUDE.md`, `backend/data-model.md` (surtout §5, §6, §8, §10, §12, §15), `backend/firestore.rules`, `backend/rules-test-plan.md`, `backend/rules-test-report.md`, `backend/tests/` (garde-fou, mesure des lectures, liste des mutations), `backend/firebase.json`, `backend/.firebaserc`, `docs/tickets/initialisation-technique.md` (format).
**Convention** : « vérifié » = confirmé sur une source officielle le 2026-10-22. « À vérifier » = non confirmé, ne pas s'y fier. « Estimation » = raisonnement à partir des règles, pas une mesure. Les extraits de règles de ce document sont des **esquisses indicatives** (non appliquées) : la version réelle est écrite et testée au développement.

---

## 1. Objectif et valeur (pour le PM)

Les règles Firestore sont le seul « vigile » de Miff : il n'y a pas de serveur (plan gratuit Spark). La revue indépendante a trouvé **quatre manières de contourner ce vigile**, toutes reproduites en conditions de test. Aucune n'a pu servir à de vrais utilisateurs : **à ma connaissance, rien n'est encore déployé** (à confirmer dans la console Firebase > Firestore > Règles) et aucune app n'est publiée. C'est donc le bon moment pour corriger, sans migration de données ni risque pour des utilisateurs.

En une phrase par sujet :

| Sujet | Ce que ça change, en clair |
|---|---|
| **Miff supprimée** | Après suppression d'une Miff, plus personne ne peut la « ressusciter » sous le même identifiant pour relire les lieux et les statuts des anciens membres. |
| **Code orphelin** | Supprimer une Miff supprime obligatoirement son code secret : un vieux code ne peut plus rouvrir une porte. |
| **Réciprocité réelle** (décision PM) | Pour voir où en sont les autres, il faut avoir soi-même **publié son propre statut**, pas seulement avoir activé le partage. C'est un vrai « donnant-donnant ». |
| **Membre retiré** (décision PM) | Quand le créateur retire quelqu'un, le code de la Miff est **automatiquement renouvelé** : l'ancien code ne marche plus, le créateur partage le nouveau avec qui il veut. |
| **Avant les premiers testeurs** | Protection contre les abus de lectures (qui pourraient bloquer l'app pour tout le monde), photos réellement illisibles après 24 h, données effacées de l'appareil à la déconnexion, garde-fous contre une erreur de projet Firebase, tests plus solides. |

**Coût : 0 €.** Tout ce qui est proposé reste sur le plan Spark. Les options payantes (Blaze) sont présentées en §11 avec leur alternative gratuite.

**Ce que l'utilisateur verra** : presque rien, sauf (a) le créateur qui retire un membre obtient un nouveau code à repartager, (b) un membre qui vient d'activer le partage voit les autres dès que son propre statut est publié (instantané dans l'usage normal), (c) un membre retiré voit un message neutre à l'ouverture. Aucun message ne doit être alarmiste (voir §6.4).

---

## 2. Périmètre

### Dans le périmètre
- Correction des 4 attaques (ATK1 à ATK4) dans `backend/firestore.rules`, un test par attaque, prouvé par mutation.
- Défense complémentaire sur la même cause que ATK1 (données de membres survivantes après suppression d'une Miff : lecture refusée si la Miff n'existe plus, voir §4.1.b).
- Alignement de la lecture des photos sur la réciprocité réelle (ATK3) et lecture refusée après expiration (§5.2).
- Avant les premiers testeurs externes : App Check strict, mesure et signal d'abus de lectures, durée de vie réelle des photos, purge du cache local, garde-fous de projet, mesure des lectures de règles, trous de couverture des tests.
- Mise à jour de `backend/data-model.md`, `backend/rules-test-plan.md` (nouvelle section 11), `backend/rules-test-report.md`, et du contrat que les apps iOS et Android doivent implémenter.

### Hors périmètre
- Cloud Functions, plan Blaze, notifications push (décision PM : pas de Blaze pour l'instant).
- Transfert de propriété d'une Miff, liste de membres bannis, validation du créateur à l'arrivée (Q7, Q8 de `data-model.md` §15, non tranchées).
- Écrans et code des apps (tickets Android/iOS dédiés ; ce ticket fixe seulement leur contrat, §9).
- Déploiement des règles (procédure en §8 ; rien n'est déployé par ce ticket).

---

## 3. Les 4 attaques, expliquées

### ATK1 — Réutilisation de l'identifiant d'une Miff supprimée (bloquant avant tout déploiement)

**En clair** : le créateur supprime sa Miff alors que des membres restent. Firestore ne supprime pas automatiquement ce que la Miff contenait (lieux, statuts, fiches membres). Un ancien membre peut alors **recréer une Miff avec le même identifiant**, s'en déclarer créateur, activer son partage, et relire les anciens lieux (coordonnées, adresses) et statuts restés en place.

**Cause technique** : rien n'empêche de réutiliser un `miffId` dont le document a été supprimé : `ownerCreateOk` (`firestore.rules:170-174`) ne vérifie que `!exists(miffs/{id})`, et la suppression (`:464`) n'exige rien d'autre que d'être créateur. Le cas U8 du plan de test (« suppression de la Miff sans retirer les autres membres : OK pour les règles ») laisse volontairement cet état possible, car **les règles ne savent pas vérifier qu'une sous-collection est vide**.

**Correction retenue : une « pierre tombale »** `miffsDeleted/{miffId}`.
- Créée **dans le même lot** que la suppression de la Miff ; la suppression est refusée sans elle.
- Une création de Miff est refusée si la pierre tombale de cet identifiant existe.
- Le document ne contient aucune donnée personnelle (identifiant de Miff + date). Il est illisible par les apps, jamais modifiable ni supprimable.
- Coût : 1 écriture à la suppression d'une Miff, 1 lecture de règle à la création d'une Miff, quelques dizaines d'octets par Miff supprimée (négligeable devant 1 GiB gratuit).

Esquisse :
```
match /miffsDeleted/{miffId} {
  allow read: if false;
  allow create: if isOwner(miffId)               // propriétaire (état avant le lot)
    && !existsAfter(miffs/$(miffId))             // seulement si la Miff disparaît dans ce lot
    && keys == ['deletedAt'] && deletedAt == request.time;
  allow update, delete: if false;
}
// ownerCreateOk : ajouter  && !exists(miffsDeleted/$(miffId))
// miffs : allow delete: if isOwner(miffId) && existsAfter(miffsDeleted/$(miffId)) && <ATK2>
```

### ATK2 — Code secret orphelin « ressuscité » (bloquant)

**En clair** : si la Miff est supprimée sans que son code le soit, le code reste valable en base. Si la Miff est un jour recréée avec le même identifiant (ATK1), l'ancien code rouvre l'accès.

**Cause technique** : le document `joinCodes/{code}` ne peut être supprimé que par le créateur *tant que la Miff existe* (`firestore.rules:451-452`) ; la suppression de la Miff (`:464`) n'exige pas celle du code, et `memberJoinOk` (`:178-183`) se contente de vérifier que le code pointe vers une Miff qui existe.

**Correction retenue** : à la suppression d'une Miff, exiger que son code soit supprimé dans le **même lot** :
```
miffs : allow delete: if isOwner(miffId)
  && existsAfter(miffsDeleted/$(miffId))                                  // ATK1
  && !existsAfter(joinCodes/$(resource.data.joinCode));                   // ATK2
```
(`resource.data.joinCode` est lu sur le document supprimé : aucune lecture de règle en plus.) Avec ATK1 corrigé, ATK2 est une seconde barrière (défense en profondeur) et supprime une donnée orpheline inutile.

### ATK3 — Réciprocité contournable (décision PM du 2026-10-22)

**En clair** : aujourd'hui, un membre qui **a activé** le partage peut lire les statuts des autres, même s'il n'a **jamais rien publié** lui-même. La réciprocité promise (« on ne voit les autres que si l'on partage soi-même ») est donc plus faible que celle annoncée.

**Cause technique** : `status` est lisible si `mySharingOn(miffId)` (`firestore.rules:477`), c'est-à-dire si `members/{moi}.sharing.enabled == true`, même sans document `status/{moi}`.

**Décision du PM** : il faut avoir publié son propre statut : `exists(status/{moi})`.

**Mise en œuvre** :
- `status` (get et list) : `miffAlive && mySharingOn && statusPublished`, avec `statusPublished(miffId) = exists(miffs/{id}/status/{moi})`.
- **Même exigence pour `photos`** (get) : sinon la photo d'un proche resterait lisible avec le seul partage activé, ce qui contredirait la décision. C'est un ajout par rapport au texte de la décision, signalé ici pour validation.
- On garde `mySharingOn` **et** on ajoute l'existence du statut : l'invariant « un statut existe ⇒ le partage est actif » est aujourd'hui garanti par les règles d'écriture, mais la défense en profondeur coûte 1 lecture de règle ; à retirer seulement si la mesure (§4.3 et §5.5) montre un coût visible, en le justifiant dans la PR.
- Conséquence naturelle : révoquer son partage (qui supprime son statut) coupe aussi sa lecture des autres.
- Conséquence pour les apps : à l'activation du partage, **créer le statut initial dans le même lot** (voir §6.3).

### ATK4 — Un membre retiré peut revenir (décision PM du 2026-10-22)

**En clair** : le créateur retire quelqu'un. Cette personne libère son ancien « emplacement gratuit » (nettoyage normal), puis rejoint de nouveau avec l'**ancien code** qu'elle connaît toujours. Le retrait ne sert à rien (c'est la question Q7 de `data-model.md`).

**Décision du PM** : le code de la Miff est **régénéré à chaque retrait de membre** ; l'ancien code cesse de fonctionner ; le créateur repartage le nouveau.

Le mécanisme complet (sous contraintes Spark, sans Cloud Function) est décrit en §4.2.

---

## 4. Conception technique des corrections

### 4.1 ATK1 et ATK2 : suppression d'une Miff

#### a) Lot de suppression de la Miff (créateur)
Un lot unique, dernier d'une suite de petits lots (comme prévu en `data-model.md` §8) :

| # | Écriture | Règle clé |
|---|---|---|
| 1 | `delete miffs/{id}` | propriétaire ; pierre tombale créée dans le lot ; code supprimé dans le lot |
| 2 | `create miffsDeleted/{id}` `{deletedAt: serverTimestamp}` | propriétaire ; la Miff disparaît dans le lot |
| 3 | `delete joinCodes/{code}` | propriétaire (déjà en place) |
| 4 | `delete miffs/{id}/members/{moi}` | créateur qui part avec sa Miff (déjà en place) |
| 5 | `delete status/{moi}`, `delete photos/{moi}` (si existent) | déjà en place |
| 6 | `delete users/{moi}/memberships/{key}` | déjà en place |

Avant ce lot : suppression des lieux (≤ 5 par lot), puis **retrait de chaque autre membre** par le lot de retrait de §4.2 (qui renouvelle le code à chaque fois : voir « Choix » ci-dessous).

**Choix : pas d'exception « retrait sans nouveau code pendant une suppression de Miff »**. Raison : un retrait de membre dans le lot même qui supprime la Miff est impossible à valider avec la limite de 20 lectures de règles par lot (estimation : 5 à 6 lectures par membre retiré) et compliquerait les règles. Conséquence : supprimer une Miff de N membres = N petits lots de retrait (rapides, rares). Si l'app est interrompue, on relance : la procédure reprend là où elle s'est arrêtée (idempotente).

*Impact PM* : la suppression d'une Miff prend quelques secondes de plus avec beaucoup de membres ; aucun coût visible (quelques écritures de plus, très loin des 20 000 écritures gratuites par jour).

#### b) Complément recommandé : les données restantes ne sont plus lisibles
**Observation (déduite de la lecture des règles, à prouver par test, non reproduite à ce jour)** : tant que la fiche `members/{uid}` d'un membre survivant existe, `isMember(miffId)` reste vrai (`firestore.rules:79-82`). Après suppression de la Miff, un membre survivant peut donc **continuer à lire `places`** (coordonnées), `members`, et les `status`/`photos` (s'il partage lui-même) tant que la fiche d'un membre n'est pas supprimée. La pierre tombale empêche la renaissance de la Miff, **pas** cette lecture.

**Correction recommandée** : ajouter `miffAlive(miffId) = exists(miffs/{id})` aux lectures de `places`, `status`, `photos` et `members` (get/list). Coût : environ 1 lecture de règle de plus par requête (un listener = une évaluation à l'ouverture, estimation) ; à mesurer (§5.5). En contrepartie, une Miff supprimée devient illisible pour tout le monde immédiatement, même si l'app d'un ancien membre ne s'est pas encore nettoyée.
Le `get miffs/{id}` n'est pas modifié : un survivant reçoit « introuvable », ce qui permet à son app de détecter la suppression et de se nettoyer (§6.5).

**Reste vrai (limite connue, à faire valider juridiquement)** : les statuts, photos et fiches des survivants restent *physiquement stockés* jusqu'au prochain lancement de leur app (illisibles entre-temps). Sur Spark, aucune fonction ne peut les purger (voir Q6 et §10).

### 4.2 ATK4 : régénération du code à chaque retrait d'un membre

#### Lot de retrait (créateur), un membre par lot
Toutes les écritures sont envoyées en **un seul lot atomique** par l'app du créateur (tout passe ou rien ne passe) :

| # | Écriture | Pourquoi |
|---|---|---|
| 1 | `delete miffs/{id}/members/{cible}` | le retrait |
| 2 | `delete miffs/{id}/status/{cible}` | pas de trace de position (déjà exigé) |
| 3 | `delete miffs/{id}/photos/{cible}` (si existe) | idem |
| 4 | `update miffs/{id}` `{joinCode: NOUVEAU}` | le code change |
| 5 | `create joinCodes/{NOUVEAU}` `{miffId, miffName, createdAt}` | le nouveau code devient valable |
| 6 | `delete joinCodes/{ANCIEN}` | l'ancien code cesse de fonctionner |

L'index `users/{cible}/memberships/{key}` de la personne retirée n'est pas touché (le créateur n'a pas le droit de l'écrire) : l'app de la personne le nettoie à son prochain lancement, comme aujourd'hui (`data-model.md` §8, « Nettoyage au lancement »).

#### Règles qui l'imposent
- La branche « le créateur retire un autre membre » de `memberDeleteOk` (`firestore.rules:222`) exige en plus que le code **change dans le même lot** :
  `getAfter(miffs/{id}).data.joinCode != get(miffs/{id}).data.joinCode`.
- La mise à jour de `miffs.joinCode` est déjà contrainte par `validMiffUpdate` (`:243-257`) : le nouveau code doit exister après le lot et l'ancien ne doit plus exister. La création du nouveau code est déjà contrainte par `validJoinCodeCreate` (`:261-271`) : seul le propriétaire, et seulement pour le code que la Miff porte après le lot.
- Résultat : **impossible de retirer un membre en laissant l'ancien code valable**, et impossible de changer de code sans retirer le nouveau/ancien proprement.
- Conséquence assumée : retirer un membre dans le même lot qui **supprime** la Miff est refusé (`getAfter` d'un document supprimé échoue) ; voir §4.1.a.

#### Impact sur l'index et le format du code
- **Aucun changement de format** : toujours 12 caractères parmi 31 symboles (~59 bits), toujours `joinCodes/{code}` ; l'index est inchangé. Seule la **fréquence** de création/suppression d'un `joinCodes` augmente (une fois par retrait).
- Le nouveau code est généré par le même générateur cryptographique que le premier (`data-model.md` §2.4). Collision avec un code existant (~10^-13 par tentative, négligeable) : le lot échoue, l'app regénère et réessaie.
- Les fiches `members/{uid}.joinCode` des membres restent inchangées (preuve historique « j'ai rejoint avec ce code », immuable) ; après renouvellement, elles contiennent un code **mort**. Pas de risque, mais à connaître.
- Les autres membres voient le nouveau code dans `miffs.joinCode` (hypothèse actuelle de `data-model.md` Q7 : code visible de tous les membres). Une personne qui a **quitté d'elle-même** (voir ci-dessous) connaît toujours le code qu'elle avait vu.

#### Cas limites (à trancher ; ma recommandation en gras)
| Cas | Ce qui se passe avec les règles proposées | Recommandation / question |
|---|---|---|
| **Retrait par le créateur (expulsion)** | Code renouvelé dans le même lot (décision PM). | Appliqué. |
| **Départ volontaire d'un membre** | Un membre simple **ne peut pas** renouveler le code (seul le créateur écrit `miffs`), donc l'imposer dans le lot de départ est impossible sans serveur. Le départ reste tel quel ; la personne peut revenir avec le code qu'elle connaît. | **Ne pas renouveler automatiquement ; l'app du créateur peut proposer, à l'ouverture suivante, « Un membre est parti : souhaitez-vous renouveler le code ? » sans insister** (Q1). Le renouvellement manuel existe déjà. |
| **Un membre prévenu qu'il va être retiré se « retire lui-même » juste avant** | Il garde l'ancien code (cas « départ volontaire »). Course improbable, non empêchable sans serveur. | Documenté (risque K3, §10). Le créateur peut renouveler le code à la main. |
| **Plusieurs retraits d'affilée** | Un lot par membre : chaque retrait renouvelle le code. | Accepté ; l'app peut n'afficher que le code final. |
| **Retrait d'un membre sans nouveau code** (client modifié ou bug) | **Refusé par les règles.** | Test H4. |
| **Création d'une Miff / suppression de compte d'un créateur** | Non concernés ; la suppression passe par §4.1.a. | — |
| **Retrait du dernier membre non créateur** | Le code est renouvelé quand même (règle uniforme). | Accepté (simplicité). |
| **Budget de lectures de règles du lot** | Estimation à la main : environ 18 lectures sur 20 pour un retrait avec photo (borne haute, sans mise en cache). **Marge faible.** | Mesurer (§5.5). Mitigation prévue si nécessaire : simplifier `isOwner` en un seul `get` (économie estimée de 4 lectures par lot), et rester à **un membre par lot**. |

### 4.3 Récapitulatif des lectures de règles (estimation, à mesurer)
Estimation par lecture des règles, **borne haute, sans mise en cache** ; la limite est 10 par opération et 20 par lot. Les mesures actuelles (`rule-reads.md`) ne sont pas toutes concluantes (§5.5).

| Opération | Avant | Après (estimation) |
|---|---|---|
| Création de Miff | ≤ 3 sur 20 (mesuré, cas premium) | +1 (`!exists(miffsDeleted)`) |
| Suppression de Miff (lot de 6) | non concluant | ≈ 11 à 13 sur 20 |
| Retrait d'un membre (lot de 6) | non concluant | ≈ 18 sur 20 (marge faible) |
| Lecture de la liste des statuts | 1 | ≈ 3 (`miffAlive` + `mySharingOn` + `statusPublished`) |
| Lecture d'une photo | 1 | ≈ 3 |

---

## 5. Avant les premiers testeurs externes

### 5.1 App Check strict et abus de lectures (le plus gros risque de coût et de disponibilité)

**Le risque, en clair** : n'importe qui peut créer des comptes jetables (e-mail + mot de passe) et boucler sur « ce code existe-t-il ? » (`get joinCodes/{code}`). Chaque essai est une **lecture facturée**, même si le code n'existe pas. Le quota gratuit est de **50 000 lectures par jour** : à 10 essais par seconde il est épuisé en environ 1 h 20, à 100 par seconde en environ 8 min (calcul, pas une mesure). Une fois le quota épuisé, **l'app s'arrête pour tout le monde jusqu'à la remise à zéro quotidienne**. Ce n'est pas un vol de données (le code est introuvable en pratique : ~59 bits) mais un **déni de service** et, en cas de passage en Blaze, une facture.

**Ce que les règles ne peuvent pas faire** : limiter le nombre d'essais par compte (elles n'ont pas de compteur persistant).

**Mitigations gratuites, par ordre d'efficacité** :
1. **App Check en mode « appliqué »** sur Firestore : seules les vraies apps Miff (Play Integrity, App Attest) peuvent interroger la base ; un script sans app officielle est refusé. Procédure : app enregistrée ➜ mode « non appliqué » pour vérifier dans les métriques que les apps envoient bien leur jeton ➜ « appliqué ». Prérequis déjà listés (`backend/README.md`, ticket d'initialisation) : empreintes SHA-256 Android, App Attest iOS, jetons de debug pour les builds de développement, distribution par Play interne/TestFlight (une app installée hors Play échoue à Play Integrity). Un blocage par erreur se corrige en repassant le service en « non appliqué » dans la console (jusqu'à environ 15 minutes de délai, vérifié sur la documentation Firebase).
   *Limite honnête* : App Check n'arrête pas un attaquant équipé d'un vrai appareil ; il augmente son coût. **À vérifier** : si une requête refusée par App Check est comptée dans les quotas Firestore (la documentation consultée ne le précise pas).
2. **E-mail vérifié obligatoire** pour résoudre un code et pour créer/rejoindre une Miff (`request.auth.token.email_verified == true`, gratuit, 0 lecture de règle) : un compte jetable doit au moins disposer d'une vraie boîte mail (des boîtes jetables existent : barrière partielle). Les comptes Google sont déjà vérifiés. C'est la question Q14 de `data-model.md` ; elle ajoute un peu de friction à l'inscription par e-mail (Q3 ci-dessous).
3. **Authentification** : connexion anonyme désactivée, protection contre l'énumération d'e-mails activée (déjà prévu au ticket d'initialisation).
4. **Surveillance avec seuil** (gratuite) : console Firebase > Firestore > Utilisation, contrôlée à chaque jour de bêta. La console ne détaille pas les lectures par collection ; l'indicateur retenu est **lectures par jour ÷ utilisateurs actifs** : l'estimation de `data-model.md` §12 est d'environ 75 ; **au-delà de 150 de façon répétée, on suspecte un abus**. Une alerte Cloud Monitoring sur le nombre de lectures serait idéale : **disponibilité sur Spark à vérifier**.
5. **Côté app** : limiter les essais (pause après 5 échecs) ; ce n'est pas une protection de sécurité (un attaquant n'utilise pas l'app) mais elle réduit les erreurs de saisie.

**Mesure du débit** : (a) calcul théorique ci-dessus ; (b) mesure sur le **futur projet de développement** (prévu avant les premiers testeurs externes), jamais sur `miff-3d283` : une rafale de quelques centaines de `get joinCodes` avec un compte de test et un jeton de debug, pour relever les lectures comptées par la console et le débit réel d'un client ; réalisée avec l'accord du PM, hors CI. Elle sert aussi à vérifier si les requêtes refusées par App Check sont comptées.

**Signal de bascule payante** (à écrire dans `data-model.md` §12) : un des trois suivants ➜ le PM décide de passer en Blaze (carte bancaire, alerte de budget qui prévient mais ne plafonne pas) pour disposer d'une fonction appelable « rejoindre » avec compteur de tentatives par compte :
- lectures par jour > 30 000 (60 % du quota) deux jours de suite sans explication ;
- ratio lectures ÷ utilisateurs actifs > 150 ;
- une journée où l'app a été refusée pour quota dépassé.

### 5.2 Durée de vie réelle des photos
- **Constat** : `photos.expiresAt` n'est qu'**indicatif** ; rien ne supprime physiquement la photo. Les politiques de suppression automatique de Firestore (« TTL ») sont listées par Google parmi les opérations **sans quota gratuit** (`docs.cloud.google.com/firestore/quotas`, consulté le 2026-10-22) : elles sont probablement indisponibles ou payantes sur Spark. **À vérifier dans la console** (Firestore > Stratégies TTL) avant de s'y fier.
- **Solution gratuite proposée (3 couches)** :
  1. **Les règles refusent la lecture d'une photo expirée** : `get` autorisé seulement si `resource == null || resource.data.expiresAt > request.time` (aucune lecture de règle en plus). L'effet est immédiat et ne dépend d'aucun nettoyage. À noter : le comportement d'une photo absente reste « introuvable » ; une photo expirée renvoie un refus de permission, que les apps traitent comme « pas de photo ».
  2. **Nettoyage par l'auteur** : à chaque lancement et à chaque nouvelle mise à jour, l'app supprime sa propre photo expirée (et met `hasPhoto` à `false`).
  3. **Nettoyage opportuniste par n'importe quel membre** (recommandé, petit) : `delete` de `photos/{uid}` autorisé à un membre si `resource.data.expiresAt <= request.time` (coût : 1 lecture de règle) ; l'app qui constate une photo expirée la supprime. Évite qu'une photo reste stockée parce que son auteur n'ouvre plus l'app.
- **Aligner les règles sur la décision du PM (photo valable 24 h)** : aujourd'hui les règles tolèrent jusqu'à **7 jours** (`firestore.rules:350`). Proposition : `expiresAt <= request.time + 25 h` (24 h + 1 h de marge, car `expiresAt` est calculé avec l'horloge du téléphone).
- **Pour le juriste** : « 24 h maximum » (`data-model.md` §10) doit être reformulé en **« illisible après 24 h ; suppression physique au mieux »** tant que le TTL n'est pas disponible.
- **Option payante** : TTL géré (nécessite probablement le plan Blaze). Alternative gratuite : les trois couches ci-dessus.

### 5.3 Cache Firestore non purgé à la déconnexion
- **Constat** : les SDK iOS et Android conservent un cache local qui contient des états de position et des lieux. Aujourd'hui rien n'impose de le vider ; sur un téléphone partagé, ou après un retrait, ces données restent lisibles hors ligne.
- **Contrat pour les clients** (identique iOS/Android) : à la **déconnexion**, à la **suppression de compte** et quand l'app **constate la perte d'accès** à une Miff (retrait, Miff supprimée) : arrêter les écouteurs, `terminate()` du client Firestore, puis `clearPersistence()`, puis recréer le client à la prochaine connexion. Vider tout le cache est acceptable (il se reconstruit à la connexion).
- **Autres points à vérifier par les devs** : `allowBackup` désactivé côté Android (déjà exigé) ; exclusion du cache de la sauvegarde côté iOS **(à vérifier)**.
- **Preuve attendue** : test manuel sur appareil — se déconnecter, couper le réseau, relancer l'app : aucune donnée de Miff visible (critère R17).
- Cette convention est déjà annoncée au ticket d'initialisation (§6) ; ce ticket en fait un critère vérifiable.

### 5.4 Garde-fou « demo- » peu utile et projet Firebase par défaut
- **Constat 1** : le garde-fou des tests vérifie que l'identifiant de projet est `demo-…`, mais cet identifiant est une **constante écrite dans le code des tests** : le contrôle ne peut donc pas échouer. Ce qui protège réellement est (a) l'obligation d'un émulateur local (`FIRESTORE_EMULATOR_HOST`) et (b) l'absence d'identifiants réels dans l'environnement.
- **Constat 2** : `backend/.firebaserc` fixe `default: miff-3d283`. Un `firebase emulators:exec` lancé **sans** `--project` partirait de ce projet par défaut, et un `firebase deploy` oublié ciblerait la production.
- **Correction proposée** (code de `backend/`, au développement) :
  1. Retirer `default` de `.firebaserc` et ne garder qu'un alias explicite (`prod` ➜ `miff-3d283`) : toute commande doit désigner son projet (la règle du `CLAUDE.md` « toute commande porte `--project miff-3d283` » est ainsi imposée par l'outil lui-même).
  2. Ajouter un test unitaire (dans `tests/unit/`, sans émulateur) qui échoue si : `.firebaserc` contient un `default` ; un script `npm` appelle `firebase` sans `--project demo-…` ; un workflow appelle `firebase` avec un projet non `demo-`.
  3. Faire vérifier au garde-fou le projet **réellement utilisé par l'émulateur** (variables d'environnement posées par `emulators:exec`) plutôt que la constante des tests.
  4. Test négatif : lancer la suite avec `GCLOUD_PROJECT=miff-3d283` ➜ refus.

### 5.5 Mesure des lectures de règles incomplète
- **Constat** : la mesure (`backend/tests/helpers/measure.js`) ajoute des lectures factices de type `(exists(...) || true)` et cherche combien l'émulateur en accepte. Pour certaines opérations, l'émulateur ne semble pas les compter (« mesure non concluante » : 7 cas dans le rapport actuel, 2 cas signalés par la revue de la PR #2). Sans chiffre fiable, on ne sait pas si la marge est réelle (c'est précisément le cas des deux lots qui nous intéressent ici).
- **À faire** : (1) établir la cause (hypothèse de départ : l'évaluateur court-circuite `|| true` ; essayer `!exists(...)`, qui vaut `true` sur un document absent mais ne peut pas être court-circuité ; hypothèse à tester, pas un fait) ; (2) à défaut, lire les traces de l'émulateur (`firestore-debug.log`) pour compter les accès par opération ; (3) rendre le test **bloquant** : une mesure non concluante fait échouer la CI au lieu d'afficher un avertissement ; (4) mesurer les nouveaux lots du §4.3.

---

## 6. Ce que les apps (iOS et Android) devront implémenter

Contrat commun, à reporter dans les tickets « Créer/Rejoindre/Quitter une Miff », « Feed » et « Réglages ». **Les deux plateformes implémentent à l'identique.** Aucun client Miff n'existe encore : pas de compatibilité ascendante à gérer.

### 6.1 Retirer un membre (créateur)
- Lot de 6 écritures de §4.2, **un membre par lot** ; générer le nouveau code avec le générateur cryptographique ; réessayer avec un autre code si collision.
- Afficher ensuite le nouveau code avec l'action de partage. Texte à valider par le PM, dans le ton bienveillant : par exemple « Julie ne fait plus partie de la Miff. Voici le nouveau code, à partager avec qui vous voulez. » (jamais accusateur).
- Après un départ volontaire, proposition discrète de renouveler le code (selon Q1).

### 6.2 Supprimer une Miff (créateur)
Dans l'ordre, tout reprenable : (1) supprimer les `places` (≤ 5 par lot) ; (2) pour chaque autre membre, lot de retrait de §6.1 ; (3) lot final de §4.1.a avec la **pierre tombale** `miffsDeleted/{id}` et la suppression du code. Un lot final sans pierre tombale ou sans suppression du code est refusé.

### 6.3 Activer le partage puis lire les autres
- Activer le partage = un lot : `members/{moi}.sharing` (consentement explicite, écran d'explication d'abord) **+ création de `status/{moi}`**.
- Statut initial : `presence = "unknown"` (le schéma impose tous les champs ; avec `unknown` le calcul de `data-model.md` §3 affiche un état neutre, jamais un « tout va bien »), `locationAccess` = permission réelle de l'appareil, `lastUpdateAt = maintenant`. Le premier événement réel l'écrase.
- Tant que `status/{moi}` n'existe pas, la lecture des statuts et des photos des autres est refusée : l'app l'affiche **sans alarme** (« Partage de ton statut en cours… »), jamais comme une erreur.
- Si le membre révoque : le lot de révocation existant supprime son statut ; il ne voit alors plus les autres, état « partage désactivé » neutre.

### 6.4 Gestion d'accès perdu (retrait, Miff supprimée)
- `get miffs/{id}` refusé (membre retiré) ou « introuvable » (Miff supprimée) ➜ message neutre à l'ouverture (« Tu ne fais plus partie de la Miff « X » » / « La Miff « X » n'existe plus »), sans reproche ni détail.
- Puis nettoyage : si ma fiche `members/{moi}` existe encore (Miff supprimée), exécuter le lot « Quitter » standard ; supprimer l'index `memberships`; vider le cache local (§5.3).

### 6.5 Photos
Ignorer toute photo dont `expiresAt` est passé ; supprimer sa propre photo expirée au lancement et à chaque mise à jour (`hasPhoto = false`) ; supprimer la photo expirée d'un autre membre si l'option §5.2.3 est retenue ; traiter « refus » et « introuvable » d'une photo comme « pas de photo ».

### 6.6 Transverse
App Check initialisé avant toute requête ; erreurs App Check ou quota affichées en ton neutre (« Pas de connexion pour le moment ») ; vérification de l'e-mail avant de saisir un code si Q3 est retenue ; purge du cache (§5.3).

---

## 7. Trous de couverture des tests et cas à ajouter

### 7.1 Un test par attaque, prouvé par mutation
Nouvelle section 11 du plan de test (même fixtures que la section 0). Chaque identifiant a un test **et** au moins une mutation dans `tests/tools/mutations.mjs` (le test doit passer au rouge quand la protection est retirée).

| ID | Cas | Attendu | Mutation associée |
|---|---|---|---|
| **H1a** | `alice` supprime M1 avec `[miffs delete + miffsDeleted create + joinCodes delete + members/alice delete + status/alice delete + memberships/free delete]` alors que `bob` reste | OK | — |
| **H1b** | Même lot sans la pierre tombale | KO | « la suppression n'exige plus la pierre tombale » |
| **H1c** | Après H1a, `bob` (survivant) recrée M1 : `[miffs create + joinCodes + members/bob (owner) + memberships]` | **KO** (c'est ATK1) | « la création ignore la pierre tombale » |
| **H1d** | Pierre tombale : créée sans supprimer la Miff ; créée par un non-propriétaire ; avec un champ en trop ou `deletedAt` client ; lue, modifiée, supprimée par n'importe qui | KO partout | une mutation par contrôle |
| **H1e** | Après H1a, `bob` (survivant) lit `places`, `members`, `status`, `photos` de M1 | KO (si §4.1.b retenu) ; `get miffs/M1` : introuvable (pas d'erreur) | « `miffAlive` retiré » |
| **H2a** | `alice` supprime M1 en laissant `joinCodes/{code}` | KO | « la suppression n'exige plus la suppression du code » |
| **H2b** | État orphelin injecté par l'administrateur de test (code sans Miff) : `eve` tente de rejoindre avec ce code | KO | (déjà couvert par `exists(miffs)`, vérifier qu'une mutation le détecte) |
| **H3a** | `carol` active son partage **sans** statut : lit `status` (get, list) et `photos/alice` | KO | « `statusPublished` retiré » (status) ; idem (photos) |
| **H3b** | `carol` active son partage **et** crée son statut dans le même lot, puis lit | OK | — |
| **H3c** | `bob` révoque (supprime son statut) puis lit `status/alice` | KO | — |
| **H4a** | `alice` retire `bob` **sans** changer le code (remplace la partie « OK » de M15) | **KO** | « le retrait n'exige plus un nouveau code » |
| **H4b** | `alice` retire `bob` avec `[delete members/bob + status/bob + photos/bob + update miffs.joinCode + create joinCodes nouveau + delete joinCodes ancien]` | OK | — |
| **H4c** | Après H4b, `bob` supprime son index orphelin puis tente de rejoindre avec **l'ancien** code | **KO** (c'est ATK4) | « l'ancien code n'est plus supprimé » |
| **H4d** | Après H4b, `bob` rejoint avec le **nouveau** code (comportement documenté : le créateur l'a partagé) | OK | — |
| **H4e** | Retrait avec un « nouveau » code identique à l'ancien ; retrait par un non-propriétaire ; régénération par un non-propriétaire (M11) | KO | — |
| **H4f** | Départ volontaire d'un membre (M12) | OK (inchangé, sous réserve de Q1) | — |
| **H5a** | Photo : lecture refusée si `expiresAt` est passé (document injecté par l'administrateur de test) ; photo absente ➜ introuvable | KO ; introuvable | « le contrôle d'expiration retiré » |
| **H5b** | Photo : création avec `expiresAt` à +26 h ou +7 jours | KO ; +24 h : OK | « borne de durée élargie » |
| **H5c** | Suppression d'une photo expirée par un autre membre ; d'une photo non expirée par un autre membre (si §5.2.3 retenu) | OK ; KO | — |
| **H6** | Garde-fou : `.firebaserc` avec `default`, script `npm` ou workflow sans projet `demo-…`, environnement avec `GCLOUD_PROJECT=miff-3d283` | échec du test unitaire | test unitaire (pas de mutation de règles) |

### 7.2 Tests existants à adapter (conséquences des nouvelles règles)
M15 et M16 (le retrait exige désormais un nouveau code), M18 et U8 (suppression de Miff avec pierre tombale et suppression du code), R8, R9, S4, S8, X1, X6 (la lecture exige le statut ou `miffAlive`), les fixtures (`miffsDeleted`, statut de `bob` déjà présent), les mesures de lectures (nouveaux lots) et la liste des mutations (les motifs `find` avec `nth/of` comptent des occurrences du texte des règles : toute règle ajoutée peut décaler ces comptes ; l'outil échoue bruyamment, c'est voulu).

### 7.3 Trous de couverture de l'outil de mutation (survivants)
La liste actuelle ne mute que **42 protections** (3 mutants équivalents documentés). Par lecture de `tests/tools/mutations.mjs`, les protections suivantes n'ont **aucune mutation** : un test qui les vise pourrait donc être inopérant sans que personne ne s'en aperçoive. C'est une liste de **candidats** à confirmer en exécutant l'outil, pas un constat de défaut :
- régénération du code : ancien code supprimé dans le lot (M10), régénération réservée au propriétaire (M11) ;
- départ : index non libérable tant qu'on est membre (M14), le créateur ne part qu'avec sa Miff (M18), libération de l'index après retrait (M19) ;
- consentement : date serveur à l'activation (S1/S2), version obligatoire (S3/J15), révocation qui conserve la preuve (S6), statut impossible sans partage actif (S7) ;
- statut / photo : écriture réservée à soi (S9, S17), clés inconnues et coordonnées (S11), bornes de `presence`/`batteryPct`/`updateText` (S12), `presenceAt` (S13), `updatedAt` serveur (S14), taille de photo (S16), purge par le créateur seulement (S18) ;
- lieux : toutes les bornes de `radiusM`/`lat`/`lng`/nom (P9), type et `createdBy` immuables (P8, P11), `createdAt` serveur (P12), limite gratuite à `p1` et au type `trusted` (P2, P4, P6) ;
- slots : clé `free` unique et clé = `miffId` réservée au premium (C9, C11, J11), expiration du premium `expiresAt > maintenant` (A10), écriture de `entitlements` interdite (A1, A2) ;
- rejoindre : code qui doit pointer vers CETTE Miff (J5, J7), rôle `member` (J8), index obligatoire (J9), `createdAt` du code serveur, `miffName` identique (C13) ;
- lectures : `list` interdit sur `joinCodes`, `miffs`, `users` (R4, J17), refus par défaut (R11, A8), lecture du profil réservée à soi (R6) ;
- profil : `termsAcceptedAt` serveur, clés inconnues, `devices` (U1 à U6).

**À faire** : ajouter une mutation par protection de cette liste ; pour chaque **survivant** (mutation non détectée) : soit renforcer le test, soit documenter le mutant comme équivalent avec sa raison (comme les 3 existants). Critère : zéro survivant non expliqué.

---

## 8. Plan de test et ordre de déploiement

### 8.1 Plan de test
1. Écrire d'abord les tests H1 à H6 (ils doivent **échouer** sur les règles actuelles pour H1c, H1e, H2a, H3a, H4a, H4c, H5a, H5b : c'est la preuve que les attaques sont reproduites dans notre suite).
2. Modifier les règles ; faire passer H1 à H6 ; adapter les tests existants (§7.2) **sans** les affaiblir.
3. Étendre et lancer `npm run test:mutation` ; traiter les survivants (§7.3).
4. Mesurer les lectures de règles (§5.5) ; documenter dans `rules-test-report.md`.
5. CI : workflow `firestore-rules` vert ; rapport par identifiant publié.
6. Test manuel de bout en bout sur le **futur projet de développement** avec les premières apps (créer, rejoindre, retirer, supprimer, révoquer) avant la production.

### 8.2 Ordre de déploiement sûr
Rien n'est déployé par ce ticket. Quand le ticket est terminé :
1. **Tests verts** (CI, y compris la mutation et les mesures de lectures).
2. **Revue de sécurité en contexte neuf** (relecteur qui n'a pas écrit les règles), avec tentative de reproduction des 4 attaques sur les nouvelles règles.
3. **OK explicite du PM** pour déployer.
4. **Déploiement en local**, jamais depuis la CI (une clé de compte de service dans les secrets serait un risque évitable) : d'abord sur le projet de développement, puis sur `miff-3d283` avec `firebase deploy --only firestore:rules --project miff-3d283` (la commande ne s'exécute qu'après l'accord du PM).
5. **Vérification** : version des règles et horodatage dans la console ; écran de diagnostic « Règles actives : OK » ; rejouer H1c et H4c à la main sur le projet de développement ; consulter Firestore > Utilisation 24 h plus tard (aucune explosion de lectures).
6. **Retour arrière** : console > Règles > historique des versions ; restaurer la version précédente (qui contient les failles : à n'utiliser qu'avant ouverture à de vrais utilisateurs).
7. **App Check strict** : séparément et **après** que les apps envoient leurs jetons (jamais le même jour que le déploiement des règles, pour pouvoir isoler la cause d'un problème).

---

## 9. Critères d'acceptation

Découpage proposé : **DUR-1** = R1 à R5 (bloquant avant tout déploiement) ; **DUR-2** = R6 à R13 ; **DUR-3** = R14 à R22 (avant les premiers testeurs externes).

- [ ] **R1** (ATK1) La suppression d'une Miff est refusée sans création de `miffsDeleted/{id}` dans le même lot.
- [ ] **R2** (ATK1) La création d'une Miff est refusée si `miffsDeleted/{id}` existe ; le test H1c reproduit l'attaque et passe au vert **après** la correction.
- [ ] **R3** (ATK1) `miffsDeleted` ne peut être créée que par le propriétaire, uniquement quand la Miff disparaît dans le lot, avec le seul champ `deletedAt` (heure serveur) ; jamais lisible, modifiable ni supprimable par un client.
- [ ] **R4** (ATK2) La suppression d'une Miff est refusée si `joinCodes/{joinCode}` existe encore après le lot.
- [ ] **R5** Les tests H1 et H2 existent, échouent sur les règles actuelles (là où c'est attendu), passent sur les nouvelles règles, et chacun est détecté par une mutation.
- [ ] **R6** (complément ATK1) Après suppression d'une Miff, un membre survivant ne peut plus lire `places`, `members`, `status`, `photos` (H1e), et son app détecte « Miff introuvable » (ou décision explicite de ne pas retenir ce complément, motivée dans la PR).
- [ ] **R7** (ATK3) `status` (get et list) et `photos` (get) sont refusés tant que `status/{moi}` n'existe pas, même avec le partage activé ; autorisés dès que le statut est publié ; refusés de nouveau après révocation (H3a, H3b, H3c).
- [ ] **R8** (ATK4) Le retrait d'un membre par le créateur est refusé si le code ne change pas dans le même lot (H4a, H4e).
- [ ] **R9** (ATK4) Après un retrait, l'ancien code ne permet plus de rejoindre, même après libération de l'index de l'ex-membre (H4c) ; le nouveau code fonctionne (H4d).
- [ ] **R10** Le départ volontaire d'un membre reste possible et inchangé (H4f), sous réserve de la réponse à Q1.
- [ ] **R11** Le lot de retrait et le lot de suppression de Miff réussissent sous les limites de lectures de règles (10 par opération, 20 par lot) avec une **marge mesurée d'au moins 2 lectures** ; sinon, mitigation du §4.2 appliquée et documentée.
- [ ] **R12** Les photos ne sont plus lisibles après `expiresAt` (H5a) ; la durée maximale à la création est alignée sur 24 h + marge (H5b) ; la politique de nettoyage retenue est décrite dans `data-model.md` (avec la mention « illisible après 24 h ; suppression physique au mieux »).
- [ ] **R13** Les 138 tests existants (hors cas volontairement modifiés, listés dans la PR avec la raison) passent ; aucun test n'est affaibli ni mis en `skip`.
- [ ] **R14** La liste de mutations couvre les protections du §7.3 ; aucun survivant sans test renforcé ou équivalence documentée.
- [ ] **R15** Aucune mesure de lectures de règles « non concluante » : la cause est établie et le test échoue si une mesure ne conclut pas ; tableau à jour dans `rules-test-report.md`.
- [ ] **R16** `.firebaserc` sans projet par défaut ; le test H6 échoue si un script ou workflow appelle `firebase` sans projet `demo-…` ; le garde-fou refuse `GCLOUD_PROJECT=miff-3d283`.
- [ ] **R17** (clients, vérifié par le QA sur appareil) Après déconnexion puis relance hors ligne, aucune donnée de Miff n'est visible ; même constat après retrait d'un membre sur son téléphone.
- [ ] **R18** (PM, avant testeurs externes) App Check est passé en mode « appliqué » sur Firestore après vérification en mode « non appliqué » ; une requête sans jeton valide est refusée ; les builds de développement et de test restent fonctionnels.
- [ ] **R19** Le signal d'abus de lectures (ratio ≥ 150 lectures par utilisateur actif, > 30 000 lectures par jour) et la procédure de bascule vers Blaze sont écrits dans `data-model.md` §12 ; la mesure du débit sur le projet de développement est consignée.
- [ ] **R20** `data-model.md` (section « Ce qui change », §5, §8, §9, §10, §12, §14, §15), `rules-test-plan.md` (section 11) et `rules-test-report.md` sont à jour.
- [ ] **R21** La procédure de §8.2 est respectée : tests verts ➜ revue de sécurité en contexte neuf ➜ OK explicite du PM ➜ déploiement local (jamais depuis la CI) ➜ vérification.
- [ ] **R22** Aucune dépense : plan Spark inchangé, aucun moyen de paiement ajouté, aucun secret ou clé dans le dépôt.

---

## 10. Risques

| # | Risque | Effet | Mitigation |
|---|---|---|---|
| K1 | Marge faible de lectures de règles dans le lot de retrait (≈ 18 sur 20, estimation) | Retrait refusé par la limite de Firestore alors que l'intention est valide | Mesure bloquante (critères R11 et R15) ; `isOwner` en un seul `get` ; un membre par lot ; en dernier recours, simplifier la règle plutôt que contourner la limite |
| K2 | Suppression de Miff interrompue en cours de route | Miff à moitié vidée, code déjà changé | Procédure idempotente et reprenable ; le créateur relance depuis ses réglages |
| K3 | Un membre quitte juste avant son retrait et garde l'ancien code | Retour possible | Non empêchable sans serveur ; le créateur peut renouveler le code à la main ; documenté |
| K4 | Données des survivants stockées mais illisibles après suppression de Miff | Rétention physique plus longue que souhaité (RGPD) | Nettoyage à l'ouverture de leur app ; point à faire valider par un juriste (Q6) ; Blaze + fonction de nettoyage si exigé |
| K5 | Statut initial `unknown` perçu comme une « mise à jour » | Confusion d'affichage | Contrat §6.3 (état neutre, jamais « tout va bien ») ; test de recette |
| K6 | Règles plus coûteuses en lectures (≈ +2 par lecture de statut) | Quota gratuit consommé plus vite | Mesure, retrait éventuel de `mySharingOn` si redondant, suivi du ratio lectures/utilisateur |
| K7 | App Check strict bloque des testeurs légitimes (app hors Play/TestFlight, jeton de debug manquant) | Testeurs refusés | Mode « non appliqué » d'abord, bascule seulement quand les métriques montrent ~100 % de jetons valides, retour arrière dans la console |
| K8 | E-mail vérifié obligatoire (si retenu) ajoute de la friction | Abandon à l'inscription | Q3 ; comptes Google déjà vérifiés |
| K9 | Maintenance des mutations fragile (motifs textuels) | Faux sentiment de sécurité si un motif ne cible plus la bonne règle | Le garde-fou `nth/of` échoue bruyamment ; revue des mutations à chaque modification des règles |
| K10 | Comportement exact de `getAfter`/`existsAfter` sur un document supprimé dans le lot | Règle trop stricte ou trop souple | Tests H1a/H4b dans l'émulateur avant toute conclusion ; je n'ai pas exécuté ces règles dans ce ticket de cadrage |
| K11 | Orphelins existants à la création de la pierre tombale | Aucun (rien n'est déployé, aucune donnée de production) | Sans objet aujourd'hui ; à reconsidérer si des données existent avant le déploiement |
| K12 | L'abus de lectures reste possible avec un vrai appareil | Quota épuisé malgré App Check | Surveillance + signal de bascule Blaze (§5.1) ; c'est la limite intrinsèque d'un plan sans serveur |

---

## 11. Coût

**Objectif : 0 €.** Rien dans ce ticket n'exige un moyen de paiement.

| Poste | Gratuit (retenu) | Option payante | Quand la considérer |
|---|---|---|---|
| Pierre tombale, code, retrait, réciprocité | Règles Firestore + écritures supplémentaires négligeables (quelques écritures par retrait/suppression, très loin de 20 000 par jour) | — | — |
| Lectures de règles supplémentaires | +1 à +2 lectures par ouverture de flux (estimation ; mesurée avant déploiement) | — | Si le ratio lectures/utilisateur dépasse 150 |
| Abus de lectures | App Check appliqué, e-mail vérifié, surveillance de la console | **Plan Blaze** (carte bancaire ; paiement à l'usage au-delà des quotas gratuits ; l'alerte de budget **prévient mais ne plafonne pas**) + fonction appelable « rejoindre » avec compteur d'essais | Signal de §5.1 ; décision du PM à ce moment-là (Q7) |
| Durée de vie des photos | Refus de lecture par les règles + nettoyage par les apps | Stratégie TTL de Firestore (sans quota gratuit selon la documentation : probablement Blaze) | Si le juriste exige une suppression physique garantie à 24 h |
| Purge du cache, garde-fous, tests | Code et CI gratuits (minutes Linux GitHub) | — | — |
| Alerte d'usage | Console Firebase (consultation manuelle) | Alerte Cloud Monitoring (disponibilité sur Spark à vérifier) | Dès que les testeurs externes arrivent |

Quotas Spark rappelés (`data-model.md` §12, vérifiés le 2026-10-16) : 50 000 lectures, 20 000 écritures, 20 000 suppressions par jour ; 1 GiB de stockage ; 10 GiB de transfert sortant par mois. L'app est refusée pour tout le monde à l'épuisement d'un quota jusqu'à la remise à zéro.

---

## 12. Dépendances

| Avec | Nature |
|---|---|
| Tickets Android / iOS « Créer, rejoindre, quitter, retirer, supprimer une Miff » (non encore rédigés) | Doivent implémenter les lots de §6.1 et §6.2 et la gestion d'accès perdu §6.4. **Ce ticket peut être livré avant eux** : aucun client n'utilise encore ces écritures. |
| Tickets Android / iOS « Feed » et « Réglages / partage » | Statut initial dans le lot d'activation (§6.3), photos expirées (§6.5). |
| Tickets Android / iOS « Inscription / connexion » | Purge du cache à la déconnexion (§5.3), vérification d'e-mail (Q3), App Check. |
| Ticket « Initialisation technique » | App Check initialisé dans les deux apps en mode non appliqué ; écran de diagnostic « Règles actives : OK » ; workflow `firestore-rules`. |
| Projet Firebase de développement (décision PM du 2026-10-16 : avant les premiers testeurs externes) | Répétition du déploiement, mesure du débit, tests de bout en bout. |
| Actions du PM | Console : constater l'état actuel des règles ; vérifier la disponibilité de TTL et d'une alerte d'usage ; plus tard, passer App Check en « appliqué ». Liste précise ajoutée à `backend/README.md` au développement. |

---

## 13. Estimation de taille (relative, sans durée)

| Sous-ticket | Contenu | Taille |
|---|---|---|
| **DUR-1** (bloquant avant tout déploiement) | ATK1 + ATK2 : pierre tombale, suppression du code, tests H1/H2, mutations, adaptation de M18/U8 | S à M |
| **DUR-2** | ATK3 + ATK4 + photos (expiration, 24 h) + `miffAlive` : règles, tests H3/H4/H5, adaptation de M15/M16/R8/R9/S8/X1, mesures de lectures | M |
| **DUR-3** (avant testeurs externes) | App Check strict et mesure du débit, garde-fous de projet, mesure de lectures fiable, extension des mutations (§7.3), documentation du signal de bascule, contrat de purge de cache | M |
| **Total** | | **L** |

Trois PR distinctes, dans cet ordre, chacune avec revue de code en contexte neuf et revue de sécurité, QA, recette du PM ; fusion par Claude après « oui » explicite du PM (CLAUDE.md, décision du 2026-10-22).

---

## 14. Ce qui change pour le modèle de données (à reporter dans `backend/data-model.md`, non modifié ici)

1. **Nouvelle collection `miffsDeleted/{miffId}`** : `{ deletedAt: timestamp (heure serveur) }`. Pierre tombale ; créée uniquement dans le lot de suppression d'une Miff par son créateur ; jamais lisible, modifiable ni supprimable par un client ; lue seulement par les règles (`exists`). Ajouter à l'arborescence du §2 et à la matrice du §9 (« — » partout, création par `O`). Aucune donnée personnelle ; conservation indéfinie (quelques dizaines d'octets).
2. **§2.5 `miffs`** : suppression conditionnée à la pierre tombale **et** à la suppression de `joinCodes/{joinCode}` dans le lot ; création refusée si `miffsDeleted/{id}` existe. `joinCode` **change à chaque retrait de membre**.
3. **§2.6 `members`** : suppression d'un autre membre par le créateur conditionnée au changement du code dans le lot. `joinCode` de la fiche = code historique (mort après renouvellement). Les fiches des membres survivants d'une Miff supprimée restent stockées, illisibles (si `miffAlive` retenu), jusqu'au nettoyage par leur app.
4. **§2.7 `status`, §2.8 `photos`** : lecture (get/list pour `status`, get pour `photos`) exige d'avoir **publié son propre statut** (`status/{moi}` existe) en plus du partage activé. `status/{moi}` est créé dans le même lot que l'activation du partage (valeur initiale `presence = "unknown"`).
5. **§2.8 `photos`** : lecture refusée après `expiresAt` ; `expiresAt` ≤ création + 25 h (au lieu de 7 jours) ; suppression possible par l'auteur, le créateur, et (si retenu) tout membre une fois la photo expirée. La conservation « 24 h maximum » devient « illisible après 24 h ; suppression physique au mieux (TTL non disponible sur Spark, à vérifier) ».
6. **§5 (rejoindre)** : la ligne « Le retrait d'un membre ne change pas le code » est remplacée par « le retrait d'un membre renouvelle le code, imposé par les règles ». Ajouter le risque d'abus de lectures sur `get joinCodes` et ses mitigations (§5.1 de ce ticket).
7. **§8 (clients)** : lignes « Retirer un membre », « Supprimer une Miff », « Activer le partage », « Quitter » et « Nettoyage au lancement » mises à jour selon §6 ; ajout de « Purge du cache » et « Perte d'accès ».
8. **§10 (conservation)** : photos, `joinCodes`, données des survivants d'une Miff supprimée, pierre tombale.
9. **§11 (RGPD)** : nouveaux points à faire examiner par un juriste : (a) rétention physique des données des survivants d'une Miff supprimée jusqu'à nettoyage par leur app ; (b) suppression physique des photos non garantie à 24 h sur Spark ; (c) cache local d'un appareil après retrait ou déconnexion ; (d) pierre tombale (identifiant de groupe supprimé, sans donnée personnelle).
10. **§12 (coûts)** : signal de bascule lié à l'abus de lectures sur `joinCodes` (§5.1), coût de lectures de règles des nouveaux lots (§4.3).
11. **§14 (risques)** : ajout de quatre risques (réutilisation d'identifiant d'une Miff supprimée, code orphelin, réciprocité contournable, retour d'un membre retiré) avec leur correction.
12. **§15 (questions)** : Q7 tranchée en partie (retrait ➜ renouvellement du code) ; nouvelles questions Q1 à Q8 ci-dessous.

---

## 15. Questions ouvertes pour le PM (réponse courte attendue)

1. **Départ volontaire : le code change-t-il aussi ?** Impossible à imposer sans serveur (seul le créateur peut changer le code). *Recommandation : non ; l'app du créateur propose discrètement de renouveler le code après un départ.*
2. **Message au membre retiré** : à l'ouverture de son app, un message neutre « Tu ne fais plus partie de la Miff « X » », sans plus de détail ? (Pas de notification : elle exigerait Blaze.) *Recommandation : oui, texte à valider avec vous.*
3. **E-mail vérifié obligatoire avant de saisir un code ou de créer une Miff ?** Réduit les comptes jetables, ajoute une étape pour l'inscription par e-mail (les comptes Google sont déjà vérifiés). *Recommandation : oui.*
4. **Supprimer une Miff avec plusieurs membres** : acceptez-vous que l'app retire les membres un par un (quelques secondes de plus) ? *Recommandation : oui, c'est le prix de règles simples et strictes.*
5. **Photo : valable 24 h, avec 1 h de marge technique** (25 h acceptées par les règles) ? *Recommandation : oui.* Et le nettoyage par n'importe quel membre d'une photo expirée (§5.2.3) ? *Recommandation : oui.*
6. **Données des membres restants après suppression d'une Miff** : acceptez-vous qu'elles soient illisibles immédiatement mais effacées physiquement seulement au prochain lancement de leur app, avec point à faire valider par un juriste ? *Recommandation : oui pour la bêta, à confirmer par le juriste.*
7. **Ligne rouge pour Blaze** : si l'usage montre un abus de lectures (§5.1), acceptez-vous de passer en Blaze (carte + alerte de budget) plutôt que de subir un arrêt de l'app ? *Recommandation : décider avant la première bêta externe ; par défaut, rester sur Spark tant que le signal n'apparaît pas.*
8. **Réciprocité appliquée aussi aux photos** (ajout par rapport à votre décision du 2026-10-22) : voir la photo d'un proche exige d'avoir publié son statut ? *Recommandation : oui, par cohérence.*

*Écarté, pour mémoire* : une liste de membres bannis (`miffs/{id}/banned/{uid}`) interdirait le retour définitif même avec le nouveau code ; non retenue car la décision du PM est le renouvellement du code, plus simple et sans nouvelle donnée personnelle. À rouvrir si le renouvellement s'avère insuffisant.

---

*Aucun fichier du projet autre que ce ticket n'a été modifié. Aucune commande Firebase n'a été exécutée. `backend/firestore.rules`, les tests et `backend/data-model.md` sont inchangés.*
