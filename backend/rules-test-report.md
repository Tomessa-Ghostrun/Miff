# Rapport des tests des règles Firestore

Ce fichier consigne les **échecs** des tests de `backend/tests/rules/` pour `backend-data` et le PM. Les règles (`firestore.rules`) ne sont **jamais** modifiées pour faire passer un test : chaque échec est d'abord analysé (la règle est-elle fautive, ou le test, ou le plan ?) puis tranché par `backend-data` avec accord du PM.

## État actuel

- **Aucune exécution des tests de règles n'a encore eu lieu.** Le sous-ticket INIT-1 a écrit les tests mais n'a pas pu lancer l'émulateur Firestore sur le poste de développement : `firebase-tools` 15.34.0 refuse Java 17 (« firebase-tools no longer supports Java version before 21 »), et le seul Java disponible est celui d'Android Studio (OpenJDK 17.0.6).
- La première exécution fera foi : workflow `firestore-rules` (Java 21 Temurin) sur la pull request INIT-1. Le tableau complet (un identifiant par ligne) est publié dans le résumé du job et dans l'artefact `rules-test-report`.
- Ce qui a été vérifié sans émulateur : chargement et syntaxe de tous les tests, présence d'un test pour chacun des 117 identifiants des sections 1 à 8 du plan (test automatique `PLAN`), garde-fou « projet de démonstration », transformation en mémoire utilisée pour la mesure des lectures.

## Échecs constatés

Aucun pour l'instant (aucune exécution).

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

Méthode : l'émulateur ne fournit pas ce compteur. Les tests exécutent chaque opération avec une copie des règles, modifiée **en mémoire seulement**, où l'on ajoute k lectures factices (`exists()` sur des documents inexistants) devant la condition de l'opération ; le plus grand k accepté donne la marge restante sous la limite (10 par requête simple, 20 par batch). Pour un batch, le résultat est une **borne haute** des lectures réellement utilisées. Si l'émulateur ne compte pas ces lectures factices, la mesure est marquée « non concluante » dans le tableau. Premiers chiffres : à reporter ici après la première exécution CI.
