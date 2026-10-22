# Rapport des tests des règles Firestore

Ce fichier consigne les **échecs** des tests de `backend/tests/rules/` pour `backend-data` et le PM. Les règles (`firestore.rules`) ne sont **jamais** modifiées pour faire passer un test : chaque échec est d'abord analysé (la règle est-elle fautive, ou le test, ou le plan ?) puis tranché par `backend-data` avec accord du PM.

## État actuel

- **Les tests tournent** (émulateur Firestore, Java 21, projet de démonstration `demo-miff-rules`) : en local (Temurin 21) et dans le workflow `firestore-rules` de la pull request. Dernière exécution : **138 tests, 137 réussis, 0 échoué, 1 ignoré** (A9, non automatisable, voir plus bas). Ces 138 tests = 117 cas du plan (sections 1 à 8) + 8 cas complémentaires X1 à X8 (section 10 du plan) + 13 mesures de lectures de règles (dont C17).
- Le tableau complet (un identifiant par ligne) est publié dans le résumé du job et dans l'artefact `rules-test-report`.
- **Solidité des tests (test par mutation).** `npm run test:mutation` affaiblit une à une 42 protections de `firestore.rules` (copie temporaire : le fichier n'est jamais modifié) et exige qu'au moins un test passe au rouge : 39 mutations sont détectées, 3 mutants sont documentés comme équivalents dans `tests/tools/mutations.mjs` (`hasAll` du statut, `is timestamp` de `escalationDueAt`, et la couche `ownerUid` du seul document Miff : chacune est redondante avec une autre protection qui refuse quand même). Avant renforcement, cinq tests (C3, C12, C15, J13, M17) ne détectaient pas la suppression de la règle qu'ils visaient.
- Durcissement des règles : hors de ce lot. Aucun test n'a dû être mis en `skip` à cause d'une règle manquante.

## Échecs constatés

Aucun : les 137 tests exécutés passent sur les règles actuelles.

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

Méthode : l'émulateur ne fournit pas ce compteur. Les tests exécutent chaque opération avec une copie des règles, modifiée **en mémoire seulement**, où l'on ajoute k lectures factices (`exists()` sur des documents inexistants) devant la condition de l'opération ; le plus grand k accepté donne la marge restante sous la limite (10 par requête simple, 20 par batch). Pour un batch, le résultat est une **borne haute** des lectures réellement utilisées. Si l'émulateur ne compte pas ces lectures factices, la mesure est marquée « non concluante » dans le tableau. Résultat de la dernière exécution : 7 opérations mesurées (marge d'au moins 6 lectures sur 10, ou 16 sur 20 ; création de Miff premium : au plus 3 lectures sur 20), 7 mesures non concluantes (les lectures factices n'y sont pas comptées, cause non établie : ces opérations n'ont donc pas de chiffre fiable, mais toutes réussissent sous les limites). Le tableau `rule-reads.md` de l'artefact de la CI donne le détail.
