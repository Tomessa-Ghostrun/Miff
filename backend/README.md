# backend/

Schéma de données Firestore, règles de sécurité et (plus tard) Cloud Functions, produits par l'agent `backend-data`.

- Projet Firebase : **`miff-3d283`** (région Firestore `europe-west9`, édition Standard, plan **Spark**).
- Le projet `ghostrun-app` du même compte Google **ne doit jamais être ciblé**. Toute commande Firebase porte `--project miff-3d283` ; `.firebaserc` fixe aussi ce projet par défaut.
- **Rien n'a été déployé ni modifié sur Firebase.** Tous les fichiers ci-dessous sont locaux.

## Index des fichiers

| Fichier | Rôle |
|---|---|
| [`data-model.md`](data-model.md) | Source de vérité pour iOS/Android : collections, champs, statuts, consentement, code secret, limites freemium, ce que chaque client implémente, données sensibles (RGPD), quotas et coûts, Blaze vs Spark, questions ouvertes pour le PM. |
| [`firestore.rules`](firestore.rules) | Règles de sécurité (tout refusé par défaut). **Écrites mais jamais exécutées** : à valider à l'émulateur avant déploiement. |
| [`rules-test-plan.md`](rules-test-plan.md) | Cas de test autorisé/refusé (dont attaques) à automatiser avec l'émulateur. |
| [`package.json`](package.json), [`tests/`](tests/) | Tests automatisés des règles (un test par identifiant du plan), garde-fou projet de démonstration, mesure des lectures de règles. Voir `../README.md` pour les lancer. |
| [`rules-test-report.md`](rules-test-report.md) | Échecs constatés par les tests des règles (attendu, obtenu, hypothèse), à traiter par `backend-data` et le PM. |
| [`firestore.indexes.json`](firestore.indexes.json) | Aucun index composite nécessaire ; seulement l'exclusion d'index des champs binaires (avatars, photos) pour limiter le coût d'écriture. |
| [`firebase.json`](firebase.json), [`.firebaserc`](.firebaserc) | Configuration locale pour déployer règles et index plus tard, verrouillée sur `miff-3d283`. |

Pas de dossier `functions/` : aucune Cloud Function n'est écrite tant que le PM n'a pas tranché sur Blaze (voir `data-model.md` §13 et question 1).

## État d'avancement et prochaines étapes pour l'équipe technique

1. Relecture par le PM de `data-model.md` §15 (questions ouvertes), en priorité les questions 1 (Blaze), 2 (photos), 3 (premium) et 6 (réciprocité).
2. Test des règles à l'émulateur avec `rules-test-plan.md` : **automatisé** (ticket INIT-1, dossier `tests/`, exécuté par la CI `firestore-rules` et en local avec `npm run test:emulator`, Java 21 requis). Résultats et échecs : `rules-test-report.md`.
3. Revue sécurité en contexte neuf des règles (process CLAUDE.md), puis seulement ensuite déploiement.
4. `ios-dev` et `android-dev` implémentent à partir de `data-model.md` §8 (mêmes chemins, mêmes champs, mêmes batches).

## Étapes manuelles du PM dans la console Firebase

Rien ne se fait automatiquement. À faire dans https://console.firebase.google.com (projet **Miff**, `miff-3d283`) :

1. **Vérifier qu'aucune règle ouverte n'existe** : Firestore Database > Règles. Si la base a été créée en « mode test », les règles autorisent tout pendant 30 jours : ne rien laisser en l'état. Les règles de ce dossier remplaceront celles de la console lors du déploiement (étape 3 ci-dessus).
2. **App Check** (avant toute vraie bêta) : Build > App Check > enregistrer l'app Android (Play Integrity ; empreinte SHA-256 de signature à fournir) et l'app iOS (App Attest, DeviceCheck en secours). Laisser d'abord en mode **non appliqué** pour vérifier dans les métriques que les apps envoient bien leurs jetons, puis **appliquer** sur Cloud Firestore. Sans cela, la protection contre l'énumération des codes et contre les abus de quota est affaiblie. Les builds de développement utilisent un jeton de debug (jamais commité).
3. **Activer la protection contre la suppression** de la base Firestore (déjà prévu au CLAUDE.md).
4. **Politique TTL** : Firestore > Time-to-live > ajouter une politique sur le champ `expiresAt` du groupe de collections `photos` (suppression automatique des photos d'update). Vérifier que l'option est disponible sur Spark.
5. **Authentication** : garder uniquement e-mail/mot de passe et Google ; ne pas activer la connexion anonyme ; activer si proposée l'option de protection contre l'énumération d'adresses e-mail. Apple Sign-In sera ajouté plus tard (obligatoire sur iOS dès que Google est proposé).
6. **Suivi des quotas** : Firestore > Utilisation. Seuils d'alerte à surveiller : lectures > 30 000/jour, écritures > 12 000/jour, transfert sortant > 6 GiB/mois (détails `data-model.md` §12).
7. **Ne pas activer** les sauvegardes / restauration à un instant donné de Firestore sans en parler au PM (elles dupliquent des données sensibles).
8. **Octroyer le premium à la main à un testeur** (Spark, avant Blaze) : récupérer son UID (Authentication > Utilisateurs), puis Firestore > Données > collection `entitlements` > ajouter un document dont l'ID est cet UID, avec les champs `active` (booléen `true`), `expiresAt` (horodatage dans le futur), `source` (texte `manual`), `updatedAt` (horodatage). Supprimer le document pour retirer le premium.
9. **Décision Blaze** (question 1) : si le PM choisit d'activer le plan Blaze, c'est lui qui saisit le moyen de paiement. Avant cela : créer une alerte de budget (Google Cloud > Facturation > Budgets et alertes). Une alerte ne plafonne pas la dépense.
10. **Clé APNs** (notifications iOS) et **empreinte SHA** Android : déjà notées « à prévoir plus tard » au CLAUDE.md ; nécessaires seulement pour le push et la connexion Google.

## Garde-fous rappelés

- Aucune clé de service, aucun secret dans le dépôt ni dans les apps.
- Aucune règle ouverte, même temporaire, même « pour tester » : utiliser l'émulateur local.
- Aucun contournement de quota (multi-comptes, projets en série) : un bannissement de projet est pire qu'un passage au plan payant.
