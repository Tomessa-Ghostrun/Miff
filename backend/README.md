# backend/

Schéma de données Firestore, règles de sécurité et (plus tard) Cloud Functions, produits par l'agent `backend-data`.

- Projet Firebase : **`miff-3d283`** (région Firestore `europe-west9`, édition Standard, plan **Spark**).
- Le projet `ghostrun-app` du même compte Google **ne doit jamais être ciblé**. Toute commande Firebase porte `--project miff-3d283` (ou `--project demo-miff-rules` pour l'émulateur) : `.firebaserc` **ne déclare volontairement aucun projet par défaut** (DUR-3), une commande qui oublie `--project` s'arrête au lieu de viser un projet au hasard. Les tests refusent tout script ou workflow qui ne respecte pas cette règle.
- **Règles déployées** : DUR-1 et DUR-2 sont déployées sur `miff-3d283` (information du PM, à confirmer dans la console : Firestore > Règles). Aucun agent ne déploie ni ne touche la console : la procédure du PM est dans [`../docs/dev/deploiement-regles.md`](../docs/dev/deploiement-regles.md).

## Index des fichiers

| Fichier | Rôle |
|---|---|
| [`data-model.md`](data-model.md) | Source de vérité pour iOS/Android : collections, champs, statuts, consentement, code secret, limites freemium, ce que chaque client implémente, données sensibles (RGPD), quotas et coûts, Blaze vs Spark, questions ouvertes pour le PM. |
| [`firestore.rules`](firestore.rules) | Règles de sécurité (tout refusé par défaut). Testées à l'émulateur (CI) ; DUR-1 et DUR-2 déployées (procédure : `../docs/dev/deploiement-regles.md`). |
| [`rules-test-plan.md`](rules-test-plan.md) | Cas de test autorisé/refusé (dont attaques) à automatiser avec l'émulateur. |
| [`package.json`](package.json), [`tests/`](tests/) | Tests automatisés des règles (un test par identifiant du plan), garde-fou projet de démonstration, mesure des lectures de règles. Voir `../README.md` pour les lancer. |
| [`rules-test-report.md`](rules-test-report.md) | Échecs constatés par les tests des règles (attendu, obtenu, hypothèse), à traiter par `backend-data` et le PM. |
| [`firestore.indexes.json`](firestore.indexes.json) | Aucun index composite nécessaire ; seulement l'exclusion d'index des champs binaires (avatars, photos) pour limiter le coût d'écriture. |
| [`firebase.json`](firebase.json), [`.firebaserc`](.firebaserc) | Configuration locale (règles et index). `.firebaserc` est vide **exprès** : avec un seul projet déclaré, la CLI le prend comme projet implicite (constaté le 2026-10-23), donc aucun projet n'y figure et chaque commande nomme le sien. |

Pas de dossier `functions/` : aucune Cloud Function n'est écrite tant que le PM n'a pas tranché sur Blaze (voir `data-model.md` §13 et question 1).

## État d'avancement et prochaines étapes pour l'équipe technique

1. Relecture par le PM de `data-model.md` §15 (questions ouvertes), en priorité les questions 1 (Blaze), 2 (photos), 3 (premium) et 6 (réciprocité).
2. Test des règles à l'émulateur avec `rules-test-plan.md` : **automatisé** (ticket INIT-1, dossier `tests/`, exécuté par la CI `firestore-rules` et en local avec `npm run test:emulator`, Java 21 requis). Résultats et échecs : `rules-test-report.md`.
3. Revue sécurité en contexte neuf des règles (process CLAUDE.md), puis seulement ensuite déploiement.
4. `ios-dev` et `android-dev` implémentent à partir de `data-model.md` §8 (mêmes chemins, mêmes champs, mêmes batches).

## Étapes manuelles du PM dans la console Firebase

Rien ne se fait automatiquement. À faire dans https://console.firebase.google.com (projet **Miff**, `miff-3d283`) :

1. **Vérifier qu'aucune règle ouverte n'existe** : Firestore Database > Règles. Si la base a été créée en « mode test », les règles autorisent tout pendant 30 jours : ne rien laisser en l'état. Les règles de ce dossier remplaceront celles de la console lors du déploiement (étape 3 ci-dessus).
2. **App Check** (avant toute vraie bêta externe) : suivre la checklist pas à pas [`../docs/dev/app-check-strict.md`](../docs/dev/app-check-strict.md) (ordre imposé : enregistrer les apps, laisser **non appliqué**, mesurer, puis **appliquer** sur Cloud Firestore, et sur Authentication si la console le propose). Sans cela, la protection contre l'énumération des codes et contre les abus de quota est affaiblie. Les builds de développement utilisent un jeton de debug (jamais commité).
3. **Activer la protection contre la suppression** de la base Firestore (déjà prévu au CLAUDE.md).
4. **Politique TTL : ne pas en créer sur Spark.** La documentation Google (quotas Firestore, mise à jour du 2026-10-21) liste les « TTL deletes » parmi les fonctions **sans usage gratuit** (« you must enable billing to use these features »). Pour les photos d'update, la solution retenue est gratuite : lecture refusée par les règles dès `expiresAt` + nettoyage par l'auteur et par tout membre (voir `data-model.md` §2.8 et §10). Le TTL n'est une option qu'avec Blaze, si le risque de conservation physique des photos devait être réduit. *Action PM (facultative)* : ouvrir Firestore > Time-to-live pour constater ce que la console propose ; **si elle demande d'activer la facturation, ne pas continuer**.
5. **Authentication** : garder uniquement e-mail/mot de passe et Google ; ne pas activer la connexion anonyme ; activer si proposée l'option de protection contre l'énumération d'adresses e-mail. Apple Sign-In sera ajouté plus tard (obligatoire sur iOS dès que Google est proposé).
6. **Suivi des quotas** : Firestore > Utilisation, chaque jour de bêta externe. Seuils, ratio lectures par utilisateur actif (> 100 à examiner, > 150 deux jours de suite = signal de bascule), création de comptes anormale, quota de 50 000 lectures atteint : tout est expliqué pour un non-développeur dans [`../docs/dev/surveillance-quotas.md`](../docs/dev/surveillance-quotas.md) (détails chiffrés : `data-model.md` §12).
7. **Ne pas activer** les sauvegardes / restauration à un instant donné de Firestore sans en parler au PM (elles dupliquent des données sensibles).
8. **Octroyer le premium à la main à un testeur** (Spark, avant Blaze) : récupérer son UID (Authentication > Utilisateurs), puis Firestore > Données > collection `entitlements` > ajouter un document dont l'ID est cet UID, avec les champs `active` (booléen `true`), `expiresAt` (horodatage dans le futur), `source` (texte `manual`), `updatedAt` (horodatage). Supprimer le document pour retirer le premium.
9. **Décision Blaze** (question 1, à trancher **avant la première bêta externe**) : si le PM choisit d'activer le plan Blaze, c'est lui qui saisit le moyen de paiement. Avant cela : créer une alerte de budget (Google Cloud > Facturation > Budgets et alertes). Une alerte ne plafonne pas la dépense. Les signaux qui doivent la déclencher sont dans `surveillance-quotas.md`.
10. **Clé APNs** (notifications iOS) et **empreinte SHA** Android : déjà notées « à prévoir plus tard » au CLAUDE.md ; nécessaires seulement pour le push et la connexion Google.

## Garde-fous rappelés

- Aucune clé de service, aucun secret dans le dépôt ni dans les apps.
- Aucune règle ouverte, même temporaire, même « pour tester » : utiliser l'émulateur local.
- Aucun contournement de quota (multi-comptes, projets en série) : un bannissement de projet est pire qu'un passage au plan payant.
