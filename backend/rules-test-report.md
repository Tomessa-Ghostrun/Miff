# Rapport des tests des règles Firestore

Ce fichier consigne les **échecs** des tests de `backend/tests/rules/` pour `backend-data` et le PM. Les règles (`firestore.rules`) ne sont **jamais** modifiées pour faire passer un test : chaque échec est d'abord analysé (la règle est-elle fautive, ou le test, ou le plan ?) puis tranché par `backend-data` avec accord du PM.

## État actuel (après DUR-3)

- **Les tests tournent** (émulateur Firestore, Java 21, projet de démonstration `demo-miff-rules`) : en local (Temurin 21) et dans le workflow `firestore-rules` de la pull request. Dernière exécution locale (2026-10-24, règles inchangées depuis DUR-2) : **189 tests de règles dans l'émulateur, 188 réussis, 0 échoué, 1 ignoré** (A9, non automatisable, voir plus bas), plus **48 tests rapides sans émulateur** (`npm run test:unit`, tous réussis). DUR-3 ajoute 20 cas de règles (section 12 du plan, `tests/rules/13-complements-dur3.test.js`), une calibration des limites de lectures de l'émulateur et 20 tests rapides (garde-fou de projet, outil de mutation, dichotomie de la mesure). Historique : 138 tests avant DUR-1, 146 après DUR-1, 169 après DUR-2.
- Le tableau complet (un identifiant par ligne) est publié dans le résumé du job et dans l'artefact `rules-test-report`.
- **Solidité des tests (test par mutation).** `npm run test:mutation` affaiblit une à une **189** protections de `firestore.rules` (copie temporaire : le fichier n'est jamais modifié) et exige qu'au moins un test passe au rouge : **182 mutations sont détectées, 7 mutants sont documentés comme équivalents** (4 d'avant DUR-3 + 3 de DUR-3, raison écrite dans `tests/tools/mutations.mjs` et `mutations-dur3.mjs`), **0 survivant**. Durée d'une exécution complète : environ 15 minutes en local (non lancée par la CI, pour rester dans les minutes gratuites). Voir la section DUR-3 ci-dessous.
- Aucun test n'a dû être mis en `skip` à cause d'une règle manquante.

## Dernière position connue (R-LIEU-POS) — PRÉPARÉ, NON DÉPLOYÉ (PR `backend/regles-position`, 2026-11-01)

**Totaux (exécution locale du 2026-11-01, Java 21, Node 24)** : **302 tests de règles dans l'émulateur, 301 réussis, 0 échoué, 1 ignoré** (A9) ; **49 tests rapides** réussis ; **328 mutations (283 + 45), 308 détectées, 20 équivalentes documentées, 0 survivante** (exécution complète du 2026-11-01).

- Règles : nouveau document `miffs/{id}/positions/{uid}` = `{lat, lng, measuredAt}` (séparé du statut, qui reste sans coordonnées), écriture par l'intéressé avec partage actif et texte de consentement de version 3 ou plus (1 `getAfter`), lecture `get` seulement (soi-même ou membre avec partage actif + statut publié + position publiée), suppression exigée dans le lot d'arrêt du partage, de départ et de retrait ; nouvelle fonction `isOwnerOneRead` (1 lecture au lieu de 2) pour les purges du créateur, afin que le lot de retrait reste à 18 sur 20.
- Tests : **27 cas DP1 à DP16b** (`tests/rules/18-derniere-position.test.js`, section 17 du plan), dont deux vrais lots de 15 et de 21 Miffs ; 4 mesures de lectures ajoutées (`READS-position-*`) et 4 mesures existantes étendues à la suppression de la position (quitter, retrait, suppression de la Miff, arrêt du partage). Fixtures : `leaveOps` passe à 5 écritures et `removeMemberOps` à 7 (aucun test existant n'a été modifié ni affaibli ; la position n'existe pas dans le jeu de départ).
- **Rouge puis vert** : les 27 cas lancés sur les règles de `backend/regles-lieux` (avant cette PR) : **18 rouges, 9 verts**. Les 9 verts sont des **refus déjà obtenus par le refus par défaut** (aucune règle ne correspondait au chemin `positions`) : DP2b, DP3, DP4, DP6c, DP8, DP11b, DP13b, DP15, DP16b. Ils gardent leur valeur de non-régression (ils échoueraient si une règle trop large était ajoutée) mais ne prouvent pas, à eux seuls, la règle ajoutée : c'est le rôle des mutations. Sur les nouvelles règles : 24 verts et 3 rouges au premier lancement (DP14 : le test devait recopier la trace du consentement actuel à l'arrêt du partage ; DP16 et DP16b : la liste des Miffs préparées n'était pas renvoyée), puis 27 verts après correction **des tests** ; aucune règle modifiée pour faire passer un test.
- **Mutations (45 ajoutées dans `tests/tools/mutations-position.mjs`)** : écriture par autrui, schéma fermé, bornes et type de latitude et de longitude, heure de mesure (passé, futur, fenêtre élargie), partage actif, texte de version 3 (version 2 acceptée, version 3 refusée, versions à deux chiffres refusées, format libre, mois libre, type), `getAfter` remplacé par `get`, 6 conditions de lecture (une par condition, plus « membre seul » et « tout connecté »), lecture de soi, listes, 2 purges exigées dans les lots, 4 droits de suppression, `isOwnerOneRead`. **4 équivalentes documentées** : `hasAll` de la position (lire une clé absente est déjà une erreur => refus), `is timestamp` (comparer un texte à un horodatage est déjà une erreur), l'ancre de début de la regex (`matches()` compare le texte entier), la vérification de connexion de `isOwnerOneRead` (la comparaison à `request.auth.uid` échoue sans connexion). Les mutations existantes dont le motif a changé ont été adaptées (S18 : statut et photo ; listes R4 et R12 : 6 règles `allow list` au lieu de 5).
- **Lectures de règles** (`test-output/rule-reads.md`) : écrire sa position <= 1 sur 10 ; lire la position d'un membre <= 4 sur 10 ; supprimer la position d'un membre (créateur) <= 1 ; lots d'arrêt du partage, de départ et d'activation : « <= 0 » dans l'émulateur (la méthode ne voit pas les `getAfter` / `existsAfter`, limite déjà connue) ; retrait <= 1, suppression de la Miff <= 3. **Le comptage manuel sans cache est la borne prudente** (data-model.md §12) : écriture 1, lecture 4, arrêt du partage 3, départ 4, activation 2, **retrait 18 sur 20 (marge 2, inchangée)**, suppression de la Miff 13 sur 20, 15 positions dans un lot 15 sur 20.
- **Constat en route** : sans `isOwnerOneRead`, le lot de retrait d'un membre serait passé de 18 à **21 appels (au-dessus de 20)** dès que la position y est ajoutée (1 `existsAfter` sur la fiche + 2 pour la suppression de la position par le créateur avec `isOwner`) : refusé par Firestore en production alors que l'émulateur, qui met les lectures en cache, l'aurait accepté. D'où le changement de `isOwner` en `isOwnerOneRead` sur les trois purges du créateur (statut, photo, position), de même sécurité.

## Lieux de confiance : 2 lieux gratuits, catégorie, type modifiable, lieu dans plusieurs Miffs (PR `backend/regles-lieux`, 2026-11-01)

**Totaux (exécution locale du 2026-11-01, Java 21)** : **271 tests de règles dans l'émulateur, 270 réussis, 0 échoué, 1 ignoré** (A9) ; **49 tests rapides** réussis ; **283 mutations, 267 détectées, 16 équivalentes documentées, 0 survivante**.

- Règles : `p1`/`p2` gratuits (R-LIEU-5), `category` obligatoire en liste fermée (R-LIEU-2), `type` et `category` modifiables, passage à `risk` réservé au premium (R-LIEU-6), nouvelle fonction `isPremiumOneRead` (1 lecture au lieu de 3) pour tenir le budget des lots multi-Miffs (data-model.md §12).
- Tests : P2, P3 et P8 adaptés ; **P14 à P23 ajoutés** (dont lots réels de 5 Miffs : P20, P20b, P20c, P21) ; mesures `READS-place-create-free`, `READS-place-update-risk`, `READS-place-delete` (≤ 2, ≤ 3, ≤ 2 sur 10).
- Mutations : P8-type-modifiable redéfinie (passage à `risk` sans premium), ajoutées P17-type-non-modifiable, P16-categorie-non-modifiable, P15-categorie-facultative, P15-categorie-libre, P15-categorie-controle-retire, P14-troisieme-emplacement-gratuit, P14-un-seul-emplacement-gratuit, P19-creation-sans-premium, P19-premium-expiration, P19-premium-actif ; P2, P4 et les deux mutations de `updatedAt` mises à jour.
- Constat en route : retirer seulement `'category'` de la liste des clés exigées est une mutation **équivalente** (`isPlaceCategory(d.category)` échoue déjà sur une clé absente et le refuse) ; la mutation retire donc les deux garde-fous ensemble et est détectée par P15.
- Les fixtures `placeSeed` / `placeInput` portent maintenant `category` (sinon tous les tests de lieux auraient échoué pour une autre raison que celle testée).

## ONB-B (téléphone obligatoire) et MIFF-0 (lot d'entrée avec statut initial)

**Totaux (exécution locale du 2026-10-28)** : **249 tests de règles dans l'émulateur, 248 réussis, 0 échoué, 1 ignoré** (A9) ; **49 tests rapides** réussis ; **273 mutations (225 d'avant + 48 d'ONB-B / MIFF-0) : 257 détectées, 16 équivalentes documentées, 0 survivante** (exécution complète du 2026-10-28, puis ré-exécution des 6 mutations touchées ensuite). Nouveaux fichiers : `tests/rules/15-telephone-onb-b.test.js` (18 cas PH1 à PH11, section 14 du plan), `tests/rules/16-lot-entree-miff0.test.js` (9 cas EN1 à EN6, section 15), `tests/tools/mutations-onbb.mjs` (48 mutations) ; 8 mesures de lectures ajoutées à `09-rule-reads.test.js`.

Règles : clé `phone` (E.164) obligatoire et validée sur `users/{uid}` et `members/{uid}`, copie de la fiche égale au profil après le lot (`getAfter`) ; **aucune règle de lecture ni du lot d'entrée n'a été modifiée** (le lot d'entrée fonctionnait déjà).

### Rouge puis vert

Les 25 tests de PH et EN ont d'abord été lancés sur les règles de `main` (fixtures portant déjà `phone`) : **20 rouges, 5 verts** (PH3, PH5, PH10, PH10b, PH10c : non-régression ou lecture, vrais avant comme après). **Honnêteté sur les EN** : sur les anciennes règles ils sont rouges **uniquement parce que la clé `phone` est refusée** (les fixtures la portent), pas parce que le lot d'entrée serait refusé ; la preuve que « le lot d'entrée fonctionnait déjà » est que le diff des règles ne touche ni `sharing`, ni `status`, ni `memberships`, et que les EN passent au vert avec ce seul ajout du téléphone. Sur les nouvelles règles : **25 verts du premier coup**. Aucune règle n'a été modifiée pour faire passer un test.

### Ce que les tests ont trouvé en route (corrigé dans les tests, jamais dans les règles)
- **Mutation survivante `EN3-statut-ecrit-par-autrui`** : le cas « statut écrit pour un autre membre » visait `frank`, dont la fiche n'existe pas : le refus venait de l'absence de fiche, pas de `isSelf`. Corrigé : la tentative vise le statut de `bob` (qui partage) et vérifie qu'il n'est pas écrasé ; mutation tuée.
- **Effet de bord sur AB18, AB19, AB21 (AUTH-B)** : la vérification de la copie du téléphone lit le profil (`getAfter`) ; elle exige donc aussi l'existence du profil, et les 4 mutations d'AUTH-B qui retirent `termsAcceptedAfter` ont survécu (profil absent = refusé de toute façon). Cause : AB21 injectait des profils **sans téléphone**, donc refusés pour le téléphone et non pour l'absence de (a). Corrigé : les profils injectés d'AB21 portent un numéro valide, et les mutations `AB18-*`, `AB19-*` sont aussi rattachées à AB21 ; 4 mutations tuées. Le comportement de `termsAcceptedAfter` n'a pas changé.
- Les cas AB13 et AB17 (AUTH-B) affirmaient le refus de `phone` : adaptés (la clé est autorisée en E.164 ; un numéro national et toute autre clé de contact restent refusés). Rien d'autre n'a été modifié ni affaibli.

### Mutations (48 ajoutées)
Forme du numéro (10 : sans `+`, zéro initial, trop court, trop long, sans borne, séparateurs, tout caractère, type non texte ; **2 équivalentes** : ancres `^` et `# Rapport des tests des règles Firestore

Ce fichier consigne les **échecs** des tests de `backend/tests/rules/` pour `backend-data` et le PM. Les règles (`firestore.rules`) ne sont **jamais** modifiées pour faire passer un test : chaque échec est d'abord analysé (la règle est-elle fautive, ou le test, ou le plan ?) puis tranché par `backend-data` avec accord du PM.

## État actuel (après DUR-3)

- **Les tests tournent** (émulateur Firestore, Java 21, projet de démonstration `demo-miff-rules`) : en local (Temurin 21) et dans le workflow `firestore-rules` de la pull request. Dernière exécution locale (2026-10-24, règles inchangées depuis DUR-2) : **189 tests de règles dans l'émulateur, 188 réussis, 0 échoué, 1 ignoré** (A9, non automatisable, voir plus bas), plus **48 tests rapides sans émulateur** (`npm run test:unit`, tous réussis). DUR-3 ajoute 20 cas de règles (section 12 du plan, `tests/rules/13-complements-dur3.test.js`), une calibration des limites de lectures de l'émulateur et 20 tests rapides (garde-fou de projet, outil de mutation, dichotomie de la mesure). Historique : 138 tests avant DUR-1, 146 après DUR-1, 169 après DUR-2.
- Le tableau complet (un identifiant par ligne) est publié dans le résumé du job et dans l'artefact `rules-test-report`.
- **Solidité des tests (test par mutation).** `npm run test:mutation` affaiblit une à une **189** protections de `firestore.rules` (copie temporaire : le fichier n'est jamais modifié) et exige qu'au moins un test passe au rouge : **182 mutations sont détectées, 7 mutants sont documentés comme équivalents** (4 d'avant DUR-3 + 3 de DUR-3, raison écrite dans `tests/tools/mutations.mjs` et `mutations-dur3.mjs`), **0 survivant**. Durée d'une exécution complète : environ 15 minutes en local (non lancée par la CI, pour rester dans les minutes gratuites). Voir la section DUR-3 ci-dessous.
- Aucun test n'a dû être mis en `skip` à cause d'une règle manquante.

 redondantes car `matches()` compare le texte entier), création et mise à jour du profil (9 dont 1 équivalente), création et mise à jour de la fiche (15 dont 2 équivalentes), lecture (6, dont l'appartenance déduite de l'index : un membre retiré dont l'index n'est pas touché continuerait de lire), lot d'entrée (8). Équivalentes (5) : `PH2-regex-sans-ancre-debut`, `PH2-regex-sans-ancre-fin`, `PH2-users-creation-hasAll-seul`, `PH6-members-creation-hasAll-seul` (la présence de la clé est déjà exigée par `hasValidPhone`), `PH8-members-copie-profil-constante` (même refus avec ou sans `get('phone', null)`).

### Lectures de règles
Voir `data-model.md` §12 : le téléphone n'ajoute **aucune** lecture mesurée ; « Créer et partager » en premium est la marge la plus faible (7 mesurées au plus, **19 appels sur 20 au pire sans cache, marge de 1**, voir data-model.md §12 ; tout appel de règle ajouté à ce lot le fait dépasser 20). Le test `09-rule-reads` échoue si une marge mesurée passe sous 2.

### Limites de ces tests
- Les mesures de lectures sont celles de l'émulateur (cache, comportement de `getAfter` sur un document écrit dans le même lot non établi : voir DUR-3) ; la colonne « pire cas sans cache » de `data-model.md` §12 est un comptage manuel, non une mesure.
- `matches()` : le comportement « appariement complet » est constaté dans l'émulateur (mutants sans ancres équivalents), pas lu dans la documentation officielle consultée.
- Non testé (impossible dans l'émulateur) : le comportement de Firestore en production face aux limites de lectures de règles ; premier essai à faire sur le futur projet de développement.

## AUTH-B : consentements à l'inscription (a) CGU et (b) statistiques / plantages

**Totaux après fusion avec DUR-3 (exécution locale du 2026-10-24)** : **214 tests de règles, 213 réussis, 0 échoué, 1 ignoré** (A9) ; **48 tests rapides** réussis ; **225 mutations (189 de DUR-3 + 36 d'AUTH-B) : 214 détectées, 11 équivalentes documentées, 0 survivant**. Le fichier de tests d'AUTH-B est `tests/rules/14-consentement-statistiques.test.js` (22 cas AB1 à AB22, section 13 du plan).

Règles : bloc facultatif `analyticsConsent` sur `users/{uid}` (création et mise à jour par le propriétaire seul), `termsAcceptedAt`/`termsVersion` figés après la création. Plan : section 12 (AB1 à AB17). Tests : `tests/rules/14-consentement-statistiques.test.js`. Aucun test existant modifié ou affaibli ; seuls ajouts à des fichiers existants : assistants de `fixtures.js`, deux hôtes de mesure (`hosts.js`) et deux mesures dans `09-rule-reads.test.js`.

### Rouge puis vert (17 cas)

Les 17 tests ont d'abord été lancés sur les règles de `main` : **14 rouges, 3 verts** (AB1, AB14, AB16 : cas de non-régression ou lecture, vrais avant comme après). Une première version, sans « contrôle positif » (écriture légitime identique, acceptée en fin de test), donnait 10 rouges et 7 verts, car la clé `analyticsConsent` inconnue faisait refuser les tentatives frauduleuses « pour une mauvaise raison » ; un contrôle positif a été ajouté à AB4, AB7, AB8, AB12 pour que le refus prouve la bonne cause. Sur les règles AUTH-B : **17 verts**. Deux défauts de tests trouvés et corrigés pendant la mise au vert (aucune règle modifiée pour cela) : un « antidatage » de `termsAcceptedAt` qui réécrivait la même valeur (donc sans changement), et une fausse trace de retrait (AB10) qui échouait sur la date conservée au lieu du bon motif (mutation `AB10-retrait-sans-consentement-actif` survivante, test corrigé, mutation tuée).

### Mutations (32 ajoutées)

| Test | Mutations qui le font passer au rouge |
|---|---|
| AB1 | `AB1-bloc-obligatoire` (resserrement : l'absence du bloc doit rester acceptée) |
| AB4 | `AB4-creation-date-consentement-client`, `-version-facultative`, `-non-consenti-avec-donnees`, `-retrait-renseigne` |
| AB5 | `AB5-version-format`, `AB5-bloc-cles-en-trop` (+ `AB4-creation-version-facultative`) ; équivalentes : `AB5-bloc-cles-manquantes`, `AB5-bloc-pas-une-map` |
| AB6 | `AB6-cgu-horodatage-client`, `AB6-cgu-version-non-controlee` ; équivalente : `AB6-cgu-non-obligatoires` |
| AB7 | `AB7-creation-profil-usurpation` |
| AB8 | `AB8-consentement-modifie-par-autrui` |
| AB9 | `AB9-activation-date-client`, `-sans-version`, `-retrait-renseigne`, `-depuis-absent-refusee`, `AB9-mise-a-jour-sans-validation` |
| AB10 | `AB10-retrait-efface-la-date`, `-efface-la-version`, `-date-client`, `-sans-consentement-actif` |
| AB11 | `AB11-reactivation-refusee` |
| AB12 | `AB12-retrogradation-de-version`, `AB12-reecriture-sans-retrait` |
| AB13 | `AB13-profil-champs-non-verrouilles`, `AB13-cgu-modifiables` |
| AB15 | `AB15-profil-supprime-par-autre` |
| AB16 | `AB16-profil-lisible-par-tous`, `AB16-liste-des-profils` |
| AB17 | `AB17-creation-champs-inconnus`, `AB5-bloc-cles-en-trop` |

AB14 (non-régression) n'a pas de mutation : c'est un cas positif.

### Changements de comportement par rapport aux règles de `main` (non déployées de ce fait)

- `termsAcceptedAt` et `termsVersion` ne sont **plus modifiables** après la création (décision PM : (a) non modifiable). Les règles précédentes l'autorisaient ; aucun test existant ne s'appuyait dessus. Un futur parcours « nouvelle version des CGU » devra rouvrir cette règle.
- La comparaison alphabétique des versions (`>=` sur des chaînes) est validée dans l'émulateur (AB11, AB12) mais non confirmée par la documentation officielle consultée.

### Lectures de règles

Création du profil avec (a) et (b) : **0** lecture de règles (borne haute, marge >= 10 sur 10) ; retrait du consentement (b) : **0** (marge >= 10 sur 10). Aucune règle de `users` n'appelle `get`/`exists` : les autres chiffres ne changent pas. Ces deux mesures sont ajoutées (`READS-profile-create`, `READS-consent-stats-update`).

### Pas de Miff sans profil (décision PM du 2026-10-24) : AB18 à AB22

Règle : `termsAcceptedAfter(uid)` dans `validMemberCreate` (un seul point : la fiche membre est créée dans les deux cas, créateur ou adhésion ; la Miff exige déjà la fiche du créateur). `getAfter` permet « profil + Miff » ou « profil + adhésion » en un lot.

- **Rouge puis vert** : sur les règles précédentes, AB18, AB19 et AB21 sont **rouges** ; AB20 (profil dans le même lot) et AB22 (le départ n'est pas bloqué) sont verts (cas positif et non-régression). Sur les nouvelles règles : 5 verts. Mutation `J13-create-membre-autre` devenue survivante (zed sans profil faisait refuser J13 pour une autre raison) : J13 seede maintenant le profil de zed, mutation tuée.
- **Tests existants adaptés** (aucun affaibli ; U1b et U2b de DUR-3 attendaient qu'un profil se modifie après sa création : ils refusent maintenant la mise à jour de `termsAcceptedAt` et `termsVersion`, décision (a) figé ; les deux mutations `U1-termsAcceptedAt-maj-client` et `U2-termsVersion-maj` sont réécrites, la seconde devient équivalente) : `eve` et `frank` (comptes sans Miff) ont désormais un profil dans le jeu de départ de tous les fichiers sauf 07, 08 et 13 (`suite({ extraProfiles: false })`) ; sans cela, 8 tests positifs échouaient (C1, J4, J16, H2b, H7a, X7, READS-create-free, READS-join) et AB18 corrigé (identifiants distincts pour dave) et surtout des dizaines de refus auraient passé « pour une mauvaise raison ». X7 crée en plus le profil de `gina`.
- **Mutations ajoutées (4)** : `AB18-miff-sans-profil-nulle-part`, `AB18-creation-miff-sans-profil`, `AB19-adhesion-sans-profil`, `AB21-profil-sans-acceptation`.
- **Lectures de règles** (méthode calibrée de DUR-3, sur la limite de 20 par lot ; avant = règles de DUR-3 ci-dessous) : création de Miff gratuite 2 -> **3** ; premium 6 -> **7** ; adhésion par code 2 -> **3** (+1 chacune : un `getAfter` sur le profil) ; profil + Miff gratuite en un lot de 5 : **2** (le profil créé dans le lot ne coûte rien de plus, voire moins que sur un profil déjà présent) ; retrait d'un membre (2) et suppression de la Miff (3) : **inchangés** (le contrôle n'est pas évalué dans ces lots). **Aucune limite n'est dépassée ; marge d'au moins 13 sur 20 pour les opérations concernées** (17 pour la création gratuite et l'adhésion).

## DUR-1 : pierre tombale (ATK1) et code orphelin (ATK2)

### Les attaques reproduites sur les règles AVANT correction (états rouges)

Les tests de la section 11 ont d'abord été lancés sur les règles de `main` (sans `miffsDeleted`). Résultat : **6 tests rouges sur 8** (H1a, H1b, H1c, H1d, H1f, H2a). Les deux autres (H1g, H2b) étaient verts avant la correction : H1g parce que « tout est refusé par défaut » refuse déjà la lecture de n'importe quelle collection inconnue (la protection n'existe qu'une fois la collection déclarée, et la mutation `H1g-*` prouve que le test la voit), H2b parce que `memberJoinOk` exigeait déjà `exists(miffs)` (protection existante, maintenant prouvée par la mutation `H2b-rejoindre-miff-inexistante`).

Comme H1a et H1c échouent « trop tôt » sur les anciennes règles (la pierre tombale est refusée avant même de tenter l'attaque), l'attaque elle-même a été **reproduite à part** avec un test jetable (non conservé), toujours sur les règles de `main` :

| Attaque | Séquence reproduite | Résultat sur les anciennes règles |
|---|---|---|
| ATK1 | `alice` supprime M1 (lot sans pierre tombale), `bob` libère sa fiche, son statut et son index, puis recrée M1 avec 4 écritures | **Réussit** ; `bob`, nouveau créateur, relit ensuite le lieu `p1` (1 lieu lu) |
| ATK1, variante non prévue au ticket | `alice` supprime M1 en gardant sa fiche membre, puis recrée `[miffs/M1 + joinCodes]` sans aucune écriture de fiche membre | **Réussit** (le contrôle ne portait que sur la création de la fiche membre) |
| ATK2 | `alice` supprime M1 sans supprimer son code | Réussit ; `joinCodes/CODE_M1` reste présent après la suppression |

### Rouge puis vert, test par test

| Test | Sur les règles de `main` | Sur les règles DUR-1 | Mutation qui le fait passer au rouge |
|---|---|---|---|
| H1a (lot final valide) | rouge (la pierre tombale est refusée) | vert | — (cas positif) |
| H1b (sans pierre tombale) | rouge (le lot sans pierre tombale est accepté) | vert | `H1b-suppression-sans-pierre-tombale` |
| H1c (survivant recrée) | rouge | vert | `H1c-creation-ignore-pierre-tombale` |
| H1f (créatrice avec fiche gardée) | rouge | vert | `H1f-creation-ignore-pierre-tombale` |
| H1d (conditions de création) | rouge | vert | `H1d-tombstone-proprietaire`, `-miff-disparait`, `-cles`, `-date-serveur` |
| H1g (jamais lisible/modifiable/supprimable) | vert (refus par défaut) | vert | `H1g-tombstone-lecture`, `-modification`, `-suppression` |
| H2a (code laissé) | rouge | vert | `H2a-suppression-sans-suppression-du-code` |
| H2b (code orphelin) | vert (protection existante) | vert | `H2b-rejoindre-miff-inexistante` |

### Tests existants adaptés (R13 : aucun affaibli ni ignoré)

| Test | Changement | Raison |
|---|---|---|
| M18 | Le lot de suppression de la Miff contient maintenant la pierre tombale ; le même lot **sans** elle est ajouté comme cas refusé | La suppression exige la pierre tombale (R1) |
| U8 | Le lot contient la pierre tombale ; titre précisé | Idem |
| `READS-delete-miff` | Lot de 6 écritures (avec pierre tombale) ; hôte de mesure changé (voir plus bas) | Idem |
| Outil de mutation | `P7-membre-supprime-lieu` : motif `nth/of` 2/2 -> 1/1 | `allow delete: if isOwner(miffId);` n'apparaît plus qu'une fois (la suppression de Miff est devenue multi-ligne) |
| Outils | Les identifiants de cas acceptent une lettre minuscule finale (`H1a`) | Un cas du plan = une ligne |

## DUR-2 : miffAlive, réciprocité par statut publié, retrait avec nouveau code, photos

Les 16 tests de `tests/rules/12-durcissement-dur2.test.js` ont d'abord été lancés sur les règles DUR-1 (sans DUR-2) : **7 rouges** (H1e, H3a, H4a, H5a, H5b, H5c, H5d), 9 verts (H3b, H3c, H3d, H3e, H4b, H4c, H4d, H4e, H4f). Parmi les verts : H3b, H4b, H4d, H4f sont des cas positifs ; H3c, H3d, H4c, H4e sont des gardes de non-régression que des règles existantes garantissaient déjà (partage révoqué, ancien code déjà supprimé par le lot, retrait par un non-propriétaire) ; H3e vise la défense en profondeur `mySharingOn` (vert avant comme après, prouvé par mutation). Sur les règles DUR-2, les 16 sont verts.

| Test | Sur règles DUR-1 | Mutations qui le font passer au rouge |
|---|---|---|
| H1e | rouge | `H1e-miffAlive-membres`, `-lieux`, `-statuts`, `-photos` |
| H3a | rouge | `H3a-statut-publie`, `H3a-photos-exigent-statut` (le test fige la décision PM : pas d'extension aux photos) |
| H3e | vert | `H3e-statuts-sans-partage-actif` |
| H4a | rouge | `H4a-retrait-sans-nouveau-code`, `H4a-ancien-code-conserve` |
| H4b | vert | `H4b-code-renouvele-inverse` |
| H4c | vert | `H4c-rejoindre-sans-verifier-le-code` |
| H4f | vert | `H4f-depart-exige-nouveau-code` |
| H5a | rouge | `H5a-photo-expiree-lisible`, `H5a-photo-absente-erreur` |
| H5b | rouge | `H5b-duree-elargie` |
| H5c | rouge | `H5c-suppression-photo-valide`, `H5c-suppression-photo-hors-membre` |
| H5d | rouge | `H5d-duree-premium` |

H3b, H3c, H3d, H4d et H4e n'ont pas de mutation dédiée : cas positifs ou gardes (H3d et H4e sont aussi couverts par X1 et M17).

### Tests existants adaptés par DUR-2 (R13 : aucun affaibli ni ignoré)

| Test | Changement | Raison |
|---|---|---|
| M15 | Le retrait de `bob` est le lot de 6 écritures avec renouvellement du code | Le retrait exige un nouveau code (R8) |
| M16 | Le lot sans statut contient le renouvellement du code (seul le statut manque) | Isoler la raison du refus |
| X6 | Le retrait sans photo contient le renouvellement du code (seule la photo manque) | Idem |
| `READS-remove-member` | Lot de 6 avec photo, hôte `members/delete` ; ajout de `READS-remove-member-alt` (hôte `joinCodes/create`) | Nouveau lot |
| Mutations `M17-retrait-par-membre`, `X1-photo-reciprocite` | Motifs de texte mis à jour ; M17 scindée en une mutation à deux couches retirées et une mutation équivalente documentée | Les règles ont changé |
| R8, R9, S4, S8, X1, R12 (cités au ticket §7.2) | Aucun changement nécessaire : ils passent tels quels sur les nouvelles règles | — |

Ajouts à la mesure : `READS-read-status-get`, `READS-read-photo`, `READS-read-place`, `READS-remove-member-alt`.

## Risque accepté par le PM (revue de sécurité de DUR-2)

Les **écritures** de `status`, `photos` et `members` d'un survivant ne vérifient pas que la Miff existe encore (`miffAlive` n'est ajouté qu'aux lectures). Un survivant (ou un client modifié) peut donc, tant que sa fiche existe, écrire de nouveaux documents dans une Miff supprimée. Ces données sont illisibles (stockage seulement, quota Spark) et nettoyées au prochain lancement de l'app. Décision du PM : risque accepté, pas de contrôle ajouté. Documenté dans `data-model.md` §11 (point 9) et §14.

## Revue de sécurité : le code pointe vers cette Miff (H7a à H7c)

La relectrice a montré qu'une Miff pouvait être créée, ou son code renouvelé, avec le code **existant d'une autre Miff** (seule l'existence du code était vérifiée). Correction : `getAfter(joinCodes/code).data.miffId == miffId` à la création et au renouvellement. Sur les règles DUR-2 d'origine : **H7a et H7b rouges** (l'attaque réussit), H7c vert (garde contre la mutation « le code n'a plus à exister »). Mutations ajoutées : `H7a-creation-code-autre-miff`, `H7b-renouvellement-code-autre-miff`, `H7c-renouvellement-code-inexistant` (la mutation qui retire l'exigence d'existence, auparavant non détectée, est tuée par H7c).

## DUR-3 : garde-fou de projet, mutations étendues, mesure de lectures fiable

`firestore.rules` n'est **pas modifié** par DUR-3 : aucun des nouveaux tests n'a révélé de faille (les 20 nouveaux tests passent sur les règles déployées par DUR-1 et DUR-2).

### Test par mutation étendu (R14)

Les mutations de la liste du §7.3 du ticket (112 nouvelles, `tests/tools/mutations-dur3.mjs`) ont été lancées **avant** d'écrire les tests : sur ces 112, **66 étaient détectées, 1 équivalente (celle de `presenceAt is timestamp`) et 45 survivaient** — c'est-à-dire 45 protections qu'on pouvait retirer sans qu'aucun test ne le voie. Traitement des 45 survivants :

- **43 tués par 20 nouveaux cas de règles** (section 12 du plan : M19b, R6b, S1b, S3b, S6b, S12b, S16b, P9b, P11b, P12b, P2b, C3b, C13b, C14b, M6b, U1b, U2b, A5b, U5b, U6b) — dont 3 après correction d'un indice de motif erroné dans la mutation (la 3e occurrence de `d.createdAt == request.time` n'est pas celle du lieu : l'ordre dans le fichier est Miff, code, lieu, photo) ;
- **2 documentés comme équivalents** : le nom de la Miff à la création (`isStr(d.name, 1, 40)` de `validMiffCreate`) et le nom du code à la mise à jour (`isStr(miffName, 1, 40)` de la règle `joinCodes`) sont redondants avec le contrôle du code (`miffName` égal au nom de la Miff et borné) ; le troisième équivalent de DUR-3 est `presenceAt is timestamp` (comparer un texte à un horodatage est une erreur d'évaluation, donc un refus).

Constats utiles trouvés par ces nouveaux tests (protections qui existent dans les règles mais n'étaient prouvées par aucun test) : un code secret existant ne peut pas être « retargé » vers une autre Miff (C14b : sinon la créatrice de M1 ferait ouvrir la Miff d'une autre avec son propre code) ; un tiers ne peut pas libérer l'index orphelin d'un autre (M19b) ; le consentement refuse `revokedAt` renseigné, clés inconnues, version effacée ou date client (S1b, S3b, S6b) ; plusieurs bornes de lieux, statut, photo et profil (voir le plan, section 12).

**Garde-fou de l'outil** : une mutation dont les règles ne compilent pas ferait échouer tous les tests (fausse « détection »). L'outil la signale désormais comme mutation invalide (`Error compiling rules`), avec un test rapide.

### Garde-fou de projet (R16)

- **Constat (2026-10-23, firebase-tools 15.34.0)** : avec `.firebaserc` déclarant `default: miff-3d283`, `firebase emulators:exec` **sans** `--project` démarrait avec `GCLOUD_PROJECT=miff-3d283`. Après retrait de `default` mais en gardant un alias unique, **c'était encore le cas** (un projet unique devient implicite). Avec deux alias ou aucun, la CLI prend `demo-no-project`. `.firebaserc` ne déclare donc plus aucun projet.
- Le garde-fou lit le projet réellement annoncé par l'émulateur (`GCLOUD_PROJECT`, `FIREBASE_CONFIG` posés par `emulators:exec`) et refuse s'il manque, s'il diffère de celui des tests ou s'il n'est pas `demo-*`. Preuves : tests rapides du garde-fou, et deux processus réels (lanceur des tests, fichier de test) lancés avec `GCLOUD_PROJECT=miff-3d283` puis `ghostrun-app` : code de sortie 2 / échec sur le garde-fou, avant toute requête.
- Test H6 (`tests/unit/project-guard.test.js`) : détecteur de commandes `firebase` (prouvé sur des exemples refusés et acceptés), scripts npm, workflows (aucun déploiement, jeton ni compte de service), scripts shell, unicité de `firebase.json`/`.firebaserc`.

### Mesure des lectures de règles (R15)

**Causes établies des mesures « non concluantes » ou fausses** (détail dans l'en-tête de `tests/helpers/measure.js`) :

1. `(exists() || true)` : lectures factices parfois non comptées (corrigé dès DUR-1 avec `!exists`).
2. **Liste des statuts** : l'émulateur accepte **20** lectures de règles pour une liste (la documentation Firebase annonce 10 pour les requêtes) ; l'outil cherchait jusqu'à 11 et la requête ne échouait jamais. Établi par calibration sur des règles synthétiques : écriture isolée 10 (11 refusé), lot 10+10 accepté et 10+11 refusé, lot 7+7+6 accepté et 7+7+7 refusé (limite cumulée de 20), get 10 (11 refusé), **liste 20 (21 refusé)**. Cette calibration est un test (`READS-calibration`) : si l'émulateur change, il échoue.
3. **Lots** : mettre toutes les lectures factices sur une seule écriture butte sur la limite de 10 de cette écriture avant celle de 20 du lot (d'où « 10 à 12 » et « 11 », qui n'étaient pas des totaux). La mesure d'un lot passe maintenant par `measureLot` : lectures de chaque écriture seule, puis lot entier avec des lectures factices réparties sans dépasser 10 par écriture ; la borne retenue est celle que le lot entier **prouve** (le lot réussit avec E lectures factices en plus donc il en utilise au plus 20 − E).

Le test échoue désormais si une mesure est non concluante, si l'opération de référence échoue, ou si la marge sous la limite documentée est inférieure à 2 (R11). La recherche se fait par dichotomie (environ 7 essais au lieu de 12 à 22 ; le test de règles des lectures passe de 4 à 2 minutes).

Dernière exécution (2026-10-24), lectures de règles utilisées (borne haute) sur la limite **documentée** :

| Opération | Limite | Utilisées (borne haute) | Marge (au moins) | Par écriture (somme) |
|---|---|---|---|---|
| Création de Miff premium (lot de 4) | 20 | 6 | 14 | 4 (memberships 2, miffs 1, members 1) |
| Création de Miff gratuite (lot de 4) | 20 | 2 | 18 | 2 |
| Rejoindre une Miff (lot de 2) | 20 | 2 | 18 | 2 (avant : 3, plafonné par une seule écriture) |
| Quitter une Miff (lot de 4) | 20 | 0 | 20 | 0 |
| **Retirer un membre + nouveau code (lot de 6, avec photo)** | 20 | **2** | 18 | 2 (suppression de la photo) |
| **Supprimer la Miff (lot de 6, avec pierre tombale)** | 20 | **3** | 17 | 3 (pierre tombale 2, fiche membre 1) |
| Régénérer le code (lot de 3) | 20 | 1 | 19 | 1 |
| Désactiver le partage (lot de 3) | 20 | 0 | 20 | 0 |
| Activer le partage + créer son statut (lot de 2) | 20 | 0 | 20 | 0 (avant : 10, plafond de l'outil) |
| Mise à jour du statut | 10 | 1 | 9 | |
| Création d'un lieu premium | 10 | 4 | 6 | |
| **Lecture de la liste des statuts** | 10 | **3** | 7 | mesurée sur 20 dans l'émulateur (limite 20 constatée) |
| Lecture du statut d'un membre (get) | 10 | 3 | 7 | |
| Lecture d'une photo (get) | 10 | 2 | 8 | |
| Lecture d'un lieu (get) | 10 | 2 | 8 | |
| Lecture du document Miff | 10 | 1 | 9 | |
| Recherche d'un code secret | 10 | 0 | 10 | |

**Limites de ces chiffres (à ne pas oublier)** : l'émulateur met en cache les lectures d'un même document (la documentation Firebase dit seulement « Some document access calls may be cached ») ; le traitement de `getAfter`/`existsAfter` n'est pas établi (une sonde a montré qu'une série d'`existsAfter` est refusée dès 10 appels dans une écriture, alors que `!exists` passe : comportement distinct, non élucidé). Ces chiffres sont des **bornes mesurées dans l'émulateur** ; le comptage manuel du pire cas sans cache fait en DUR-2 (retrait 18 sur 20, suppression 12 sur 20) reste la borne prudente, avec une marge d'au moins 2 dans les deux approches.

## Échecs constatés

Aucun : les 168 tests exécutés passent sur les règles actuelles.

Format à utiliser pour chaque échec :

| Identifiant | Attendu (plan) | Obtenu | Hypothèse | Décision |
|---|---|---|---|---|
| _ex. C8_ | _refus_ | _succès_ | _la règle ... laisse passer ..._ | _à trancher par backend-data / PM_ |

## Cas non automatisables

| Identifiant | Raison |
|---|---|
| A9 | Contournement par deux comptes : deux comptes distincts sont deux utilisateurs légitimes pour les règles Firestore. Hors périmètre des règles (documenté dans `data-model.md`, section 6). Le test existe, marqué « ignoré » avec cette raison. |
| Section 9 du plan | Hors émulateur de règles : App Check appliqué en mode strict (console Firebase), « un seul `get joinCodes` par essai » (comportement du client), absence de clé de service ou de secret dans le dépôt (job `hygiene`, recherche de secrets). Le nombre de lectures de règles par opération est, lui, mesuré (voir `rule-reads.md` dans l'artefact de la CI). |

## Mesure des lectures de règles (historique, critère T5)

La méthode (lectures factices `!exists()` ajoutées en mémoire devant la condition d'une règle, recherche du plus grand nombre accepté) est inchangée ; sa version corrigée, les causes des anciennes mesures non concluantes et le tableau à jour sont dans la section **DUR-3 : Mesure des lectures de règles (R15)** ci-dessus. Les chiffres de DUR-1 et DUR-2 qui figuraient ici (« 10 à 12 », « 11 », « non concluante », 3 pour rejoindre, 10 pour activer le partage) étaient des plafonds de l'outil et non des totaux.

## MIFF-1 (Android) : longueur du nom d'une Miff et mesure du lot d'entrée (2026-10-31)

- **Aucune règle modifiée.** Suite complète relancée sur `main` (31e4361) avant le développement : **249 tests de règles dans l'émulateur, 248 réussis, 0 échoué, 1 ignoré** ; 49 tests rapides. Mesures de lectures de règles confirmées : « Rejoindre et partager » <= 3 sur 20, « Créer et partager » gratuit <= 3, **premium <= 7** (19 appels au pire sans cache, marge de 1, voir `data-model.md` §12), arrêt du partage <= 0, reprise du partage <= 0. Le client Android n'ajoute **aucun** appel de règle à ces lots (le contenu des lots est testé clé par clé contre les règles côté client : `MiffWritesTest`).
- **Nouveau fichier** `tests/rules/17-nom-miff-longueur.test.js` (section 16 du plan : ND1, ND1b, ND2, ND2b, ND3) : les règles comptent la longueur d'un nom en **unités UTF-16**. ND3 est une mesure d'information (elle ne peut pas échouer) : 30 emojis (60 unités) sont refusés.
