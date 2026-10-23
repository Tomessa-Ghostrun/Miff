# Rapport des tests des règles Firestore

Ce fichier consigne les **échecs** des tests de `backend/tests/rules/` pour `backend-data` et le PM. Les règles (`firestore.rules`) ne sont **jamais** modifiées pour faire passer un test : chaque échec est d'abord analysé (la règle est-elle fautive, ou le test, ou le plan ?) puis tranché par `backend-data` avec accord du PM.

## État actuel (après DUR-2)

- **Les tests tournent** (émulateur Firestore, Java 21, projet de démonstration `demo-miff-rules`) : en local (Temurin 21) et dans le workflow `firestore-rules` de la pull request. Dernière exécution locale : **166 tests, 165 réussis, 0 échoué, 1 ignoré** (A9, non automatisable, voir plus bas), plus 28 tests rapides sans émulateur (`npm run test:unit`, tous réussis). Les 166 tests = 117 cas du plan (sections 1 à 8) + 8 cas complémentaires X1 à X8 (section 10) + **8 cas DUR-1** (H1a, H1b, H1c, H1d, H1f, H1g, H2a, H2b) + **16 cas DUR-2** (H1e, H3a à H3e, H4a à H4f, H5a à H5d ; section 11) + 17 mesures de lectures de règles (13 d'origine, dont C17, + 4 ajoutées). Historique : 138 tests avant DUR-1, 146 après DUR-1.
- Le tableau complet (un identifiant par ligne) est publié dans le résumé du job et dans l'artefact `rules-test-report`.
- **Solidité des tests (test par mutation).** `npm run test:mutation` affaiblit une à une **74** protections de `firestore.rules` (copie temporaire : le fichier n'est jamais modifié) et exige qu'au moins un test passe au rouge : **70 mutations sont détectées, 4 mutants sont documentés comme équivalents** dans `tests/tools/mutations.mjs` (`hasAll` du statut, `is timestamp` de `escalationDueAt`, la couche `ownerUid` du seul document Miff, et — nouveau avec DUR-2 — le contrôle `isOwner` de la branche « retrait », redondant avec `codeRenewed` puisque seul le propriétaire peut modifier `miffs.joinCode`). DUR-1 a ajouté 12 mutations (42 -> 54), DUR-2 20 (54 -> 74), toutes détectées sauf l'équivalent cité. Le ticket (§7.3) liste d'autres protections encore sans mutation : c'est le travail de DUR-3, non fait ici.
- Aucun test n'a dû être mis en `skip` à cause d'une règle manquante.

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

## Échecs constatés

Aucun : les 165 tests exécutés passent sur les règles actuelles.

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
| **Retirer un membre + nouveau code (lot de 6, avec photo)** (DUR-2) | 20 | **12** (10 à 12 selon l'exécution et l'hôte de mesure) | **8** (10 à 12 selon les essais) | lot de 3 : 3 à 4 (lot différent) |
| **Supprimer la Miff (lot de 6, avec pierre tombale)** | 20 | **11** | **9** | non mesurable (lot différent) |
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

Note sur les valeurs « 0 » : les lectures de documents modifiés dans le même lot (`existsAfter`, `getAfter`) ne semblent pas être comptées par l'émulateur (constat, non confirmé dans la documentation) ; il faut donc lire ces chiffres comme « lectures supplémentaires hors documents du lot ». Le critère R11 (marge d'au moins 2 lectures) est satisfait par toutes les opérations mesurées, dont le lot de suppression de Miff (marge 9) et **le lot de retrait de membre avec renouvellement du code (DUR-2) : 10 à 12 lectures utilisées sur 20, marge d'au moins 8**. Recoupement : sept essais avec des hôtes de mesure différents (`members/delete`, `miffs/update`, `joinCodes/create`, `joinCodes/delete`, `status/delete`, `photos/delete`) donnent 10 (12 pour `photos/delete`, hôte moins précis). Comptage à la main du pire cas, sans aucune mise en commun d'un même document : 6 (suppression de la fiche) + 2 (statut) + 2 (photo) + 4 (mise à jour de la Miff) + 3 (création du nouveau code) + 1 (suppression de l'ancien) = **18 sur 20**, soit encore une marge de 2. Aucune limite (10 par requête, 20 par lot) n'est dépassée. Une répartition des lectures factices sur deux hôtes à la fois (`status/delete` + `photos/delete`) a donné une valeur aberrante (au plus 1) : méthode écartée pour ce lot.

Lecture d'un statut : 3 lectures de règles (la Miff existe, partage actif, statut publié) ; la valeur est mesurée sur un `get` ; la liste des statuts reste non concluante.

Pour la suppression de la Miff, les lectures factices sont comptées avec l'hôte `members/delete` (sur les hôtes `miffs`, `status`, `memberships`, `joinCodes` la mesure reste non concluante : cause non établie, nouvelle raison de ne pas conclure sans le contrôle de cohérence ci-dessus). Le tableau `rule-reads.md` de l'artefact de la CI donne le détail.
