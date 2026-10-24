# Rapport des tests des règles Firestore

Ce fichier consigne les **échecs** des tests de `backend/tests/rules/` pour `backend-data` et le PM. Les règles (`firestore.rules`) ne sont **jamais** modifiées pour faire passer un test : chaque échec est d'abord analysé (la règle est-elle fautive, ou le test, ou le plan ?) puis tranché par `backend-data` avec accord du PM.

## État actuel (après AUTH-B)

- **Les tests tournent** (émulateur Firestore, Java 21, projet de démonstration `demo-miff-rules`) : en local (Temurin 21) et dans le workflow `firestore-rules` de la pull request. Dernière exécution locale (2026-10-24, Temurin 21) : **194 tests, 193 réussis, 0 échoué, 1 ignoré** (A9, non automatisable, voir plus bas ; 169 avant AUTH-B : +22 cas AB1 à AB22 +3 mesures de lectures), plus 28 tests rapides sans émulateur (`npm run test:unit`, tous réussis). Détail des 169 tests d'avant AUTH-B : 117 cas du plan (sections 1 à 8) + 8 cas complémentaires X1 à X8 (section 10) + **8 cas DUR-1** (H1a, H1b, H1c, H1d, H1f, H1g, H2a, H2b) + **16 cas DUR-2** (H1e, H3a à H3e, H4a à H4f, H5a à H5d ; section 11) + **3 cas issus de la revue de sécurité** (H7a à H7c) + 17 mesures de lectures de règles (13 d'origine, dont C17, + 4 ajoutées). Historique : 138 tests avant DUR-1, 146 après DUR-1.
- **Solidité des tests après AUTH-B** : `npm run test:mutation` affaiblit **113** protections (77 + 32 + 4 d'AUTH-B) : **106 sont détectées, 7 mutants documentés comme équivalents** (les 4 d'avant + 3 d'AUTH-B : `hasAll` du bloc de consentement, `is map` du bloc, `hasAll` des champs CGU, tous redondants car lire une clé absente ou appeler `keys()` sur autre chose qu'une map fait échouer la règle, donc refuse).
- Le tableau complet (un identifiant par ligne) est publié dans le résumé du job et dans l'artefact `rules-test-report`.
- **Solidité des tests (test par mutation).** `npm run test:mutation` affaiblit une à une **77** protections de `firestore.rules` (copie temporaire : le fichier n'est jamais modifié) et exige qu'au moins un test passe au rouge : **73 mutations sont détectées, 4 mutants sont documentés comme équivalents** dans `tests/tools/mutations.mjs` (`hasAll` du statut, `is timestamp` de `escalationDueAt`, la couche `ownerUid` du seul document Miff, et — nouveau avec DUR-2 — le contrôle `isOwner` de la branche « retrait », redondant avec `codeRenewed` puisque seul le propriétaire peut modifier `miffs.joinCode`). DUR-1 a ajouté 12 mutations (42 -> 54), DUR-2 20 (54 -> 74) puis 3 de la revue (74 -> 77), toutes détectées sauf l'équivalent cité. Le ticket (§7.3) liste d'autres protections encore sans mutation : c'est le travail de DUR-3, non fait ici.
- Aucun test n'a dû être mis en `skip` à cause d'une règle manquante.

## AUTH-B : consentements à l'inscription (a) CGU et (b) statistiques / plantages

Règles : bloc facultatif `analyticsConsent` sur `users/{uid}` (création et mise à jour par le propriétaire seul), `termsAcceptedAt`/`termsVersion` figés après la création. Plan : section 12 (AB1 à AB17). Tests : `tests/rules/13-consentement-statistiques.test.js`. Aucun test existant modifié ou affaibli ; seuls ajouts à des fichiers existants : assistants de `fixtures.js`, deux hôtes de mesure (`hosts.js`) et deux mesures dans `09-rule-reads.test.js`.

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
- **Tests existants adaptés** (aucun affaibli) : `eve` et `frank` (comptes sans Miff) ont désormais un profil dans le jeu de départ de tous les fichiers sauf 07, 08 et 13 (`suite({ extraProfiles: false })`) ; sans cela, 8 tests positifs échouaient (C1, J4, J16, H2b, H7a, X7, READS-create-free, READS-join) et AB18 corrigé (identifiants distincts pour dave) et surtout des dizaines de refus auraient passé « pour une mauvaise raison ». X7 crée en plus le profil de `gina`.
- **Mutations ajoutées (4)** : `AB18-miff-sans-profil-nulle-part`, `AB18-creation-miff-sans-profil`, `AB19-adhesion-sans-profil`, `AB21-profil-sans-acceptation`.
- **Lectures de règles** (bornes hautes, mesure à ±1 ou 2, sur la limite de 20 par lot) : création de Miff gratuite 2 -> **3** ; premium 6 -> **7** ; adhésion par code 3 -> **5** ; profil + Miff gratuite en un lot de 5 : **2** (valeur anormalement basse, la méthode sous-estime ce lot ; ne pas la lire comme un gain) ; retrait d'un membre et suppression de la Miff : **inchangés** (10 et 11 par l'outil, plafonds de 10 par écriture, bases mesurées ≈ 2 et ≈ 4, pires cas recomptés 18 et 12 sur 20 : le contrôle n'est pas évalué dans ces lots). **Aucune limite n'est dépassée ; marge d'au moins 2 conservée partout** (la plus faible : retrait, 18 sur 20 au pire cas à la main, inchangé).

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

## Mesure des lectures de règles (critère T5)

Méthode : l'émulateur ne fournit pas ce compteur. Les tests exécutent chaque opération avec une copie des règles, modifiée **en mémoire seulement**, où l'on ajoute k lectures factices (`!exists()` sur des documents inexistants) devant la condition de l'opération ; le plus grand k accepté donne la marge restante sous la limite (10 par requête simple, 20 par batch). Pour un batch, le résultat est une **borne haute** des lectures réellement utilisées.

**Cause des mesures « non concluantes » trouvée pendant DUR-1 (hypothèse confirmée en pratique, pas dans une documentation).** La version initiale ajoutait `(exists(...) || true)` : la condition étant vraie d'avance, l'émulateur peut conclure avant d'avoir compté la lecture factice, d'où des résultats **différents d'une exécution à l'autre** pour la même opération (observé : création gratuite « <= 2 » puis « non concluante » entre deux exécutions sur les mêmes règles). Avec `!exists(...)` (vrai sur un document absent, donc impossible à court-circuiter), 13 mesures sur 14 deviennent concluantes. Sur trois exécutions complètes (règles de `main`, puis règles DUR-1 deux fois), les valeurs sont identiques pour toutes les opérations sauf **Retirer un membre : 3, 3 puis 4** (variation de 1 sur le lot à deux hôtes de mesure, cause non établie). Les chiffres sont donc des bornes hautes à ±1 près ; on retient la valeur la plus haute observée. Seule reste non concluante la **lecture de la liste des statuts** (une requête, pas un lot) : à traiter en DUR-3 (R15). La suppression de la Miff était « non concluante » avant DUR-1 (donc sans chiffre) ; elle est désormais mesurée.

Dernière exécution (règles DUR-1, `!exists`), lectures de règles utilisées (borne haute) sur la limite :

| Opération | Limite | Utilisées (borne haute) | Marge (au moins) | Avant DUR-1 (mêmes méthode) |
|---|---|---|---|---|
| Création de Miff premium (lot de 4) | 20 | 6 | 14 | 5 (+1 : `!exists(miffsDeleted)`) |
| Création de Miff gratuite (lot de 4) | 20 | 2 | 18 | 1 (+1) |
| Rejoindre une Miff (lot de 2) | 20 | 3 | 17 | 3 |
| Quitter une Miff (lot de 4) | 20 | 0 | 20 | 0 |
| **Retirer un membre + nouveau code (lot de 6, avec photo)** (DUR-2) | 20 | outil : 10 à 12 (plafond de 10 par écriture, voir note) ; **base mesurée ≈ 2, pire cas 18** | au moins 2 | lot de 3 : 3 à 4 (lot différent) |
| **Supprimer la Miff (lot de 6, avec pierre tombale)** | 20 | outil : 11 (plafond, voir note) ; **base mesurée ≈ 4, pire cas 12** | au moins 8 | non mesurable (lot différent) |
| Régénérer le code (lot de 3) | 20 | 1 | 19 | 1 |
| Désactiver le partage (lot de 3) | 20 | 0 | 20 | 0 |
| Activer le partage + créer son statut (lot de 2) | 20 | 10 | 10 | 10 |
| Mise à jour du statut | 10 | 1 | 9 | 1 |
| Création d'un lieu premium | 10 | 4 | 6 | 4 |
| Lecture du document Miff | 10 | 1 | 9 | 1 |
| Recherche d'un code secret | 10 | 0 | 10 | 0 |
| Lecture de la liste des statuts | 10 | non concluante | | non concluante |
| Lecture du statut d'un membre (get) (DUR-2) | 10 | 3 | 7 | 1 (estimation, non mesurée avant) |
| Lecture d'une photo (get) (DUR-2) | 10 | 2 | 8 | 1 (estimation) |
| Lecture d'un lieu (get) (DUR-2) | 10 | 2 | 8 | 1 (estimation) |

**Comment lire ces chiffres (correction après la revue de sécurité indépendante).** Les valeurs « 10 à 12 » (retrait) et « 11 » (suppression de Miff) données par notre outil **ne sont pas le nombre de lectures du lot** : l'émulateur applique un **plafond de 10 lectures par évaluation d'écriture**, et avec nos lectures factices placées sur une seule règle l'outil atteint ce plafond avant celui du lot. Ce sont des **majorants grossiers**, pas des mesures. Les bases mesurées par la relectrice (sans lectures factices) sont : **retrait d'un membre ≈ 2**, **suppression de Miff ≈ 4**, **activation du partage 0**. Le **pire cas recompté à la main sans aucun cache** (chaque appel `get`/`exists`/`getAfter`/`existsAfter` compté, même sur le même document) est de **18 sur 20 pour le retrait** (6 pour la suppression de la fiche + 2 statut + 2 photo + 4 mise à jour de la Miff + 3 création du nouveau code + 1 suppression de l'ancien) et de **12 sur 20 pour la suppression de Miff**. L'émulateur **ne compte pas deux fois le même document** (constat de la relectrice, non confirmé par la documentation), donc la consommation réelle est bien plus basse que le pire cas. Dans tous les cas, **la marge est d'au moins 2 lectures** (critère R11) et aucune limite (10 par requête, 20 par lot) n'est dépassée. Une répartition des lectures factices sur deux hôtes à la fois (`status/delete` + `photos/delete`) a donné une valeur aberrante (au plus 1) : méthode écartée.

Les valeurs « 0 » (quitter, désactiver le partage) confirment que les lectures de documents du lot lui-même (`existsAfter`, `getAfter`) ne pèsent presque rien dans l'émulateur.

Lecture d'un statut : 3 lectures de règles (la Miff existe, partage actif, statut publié) ; la valeur est mesurée sur un `get` ; la liste des statuts reste non concluante.

Pour la suppression de la Miff, les lectures factices sont comptées avec l'hôte `members/delete` (sur les hôtes `miffs`, `status`, `memberships`, `joinCodes` la mesure reste non concluante : cause non établie, nouvelle raison de ne pas conclure sans le contrôle de cohérence ci-dessus). Le tableau `rule-reads.md` de l'artefact de la CI donne le détail.
