# Bugs connus et points ouverts

Dernière mise à jour : 2026-10-22 (QA INIT-2 Android, commit `f7e5ce1`).
Gravités : **bloquant** (empêche la soumission au store), **majeur**, **mineur**. Un bloquant est toujours indiqué en toutes lettres.

## Résumé

| Gravité | Nombre ouvert | Détail |
|---|---|---|
| Bloquant | **0** | Aucun trouvé dans ce qui est vérifiable sans téléphone. Ceci ne vaut pas validation : le comportement sur appareil n'est pas encore constaté (voir `docs/qa/init-2-android.md`) |
| Majeur | 0 | |
| Mineur | 3 | AND-001, AND-002, AND-003 (AND-003 est une suspicion à confirmer) |

## Défauts ouverts

### AND-001 — Mineur — Documentation inexacte sur le fichier lu par le script de parité des traductions
- **Plateforme** : Android (documentation). **À corriger par** : `android-dev`.
- **Reproduction** : lire `android/README.md` ligne 100 et `android/CHANGELOG.md` ligne 38, puis `scripts/check-i18n-parity.mjs` lignes 5 et 20.
- **Attendu** : la documentation décrit ce que fait le script. Le contrat `docs/contracts/i18n-keys.md` dit que le script lit le **français** dans `values-fr/strings.xml`.
- **Observé** : le README dit « le script de parité … fonctionne sur `res/values/strings.xml`, même si ce fichier contient l'anglais » et le CHANGELOG dit « le script de parité … fonctionne sur `values/` ». Or le script lit `android/app/src/main/res/values-fr/strings.xml` (et ses tests vérifient « le français est lu dans `values-fr`, l'anglais de repli `values/` est ignoré »). Les phrases du README/CHANGELOG sont restées de l'ancienne organisation, avant la fusion de `main`.
- **Impact** : aucun sur le produit. Risque de confusion pour le prochain développeur (qui pourrait modifier le mauvais fichier en pensant que la CI contrôle l'anglais).

### AND-002 — Mineur — L'APK de test de la CI est signé avec une clé de débogage différente à chaque exécution
- **Plateforme** : Android (CI). **À corriger par** : `android-dev` (avant le ticket Inscription pour le second effet).
- **Reproduction** : télécharger l'artefact `miff-debug-apk` de deux exécutions CI successives (ou comparer l'artefact et un APK construit sur le poste) et exécuter `apksigner verify --print-certs` sur chacun.
- **Observé** : empreinte SHA-256 du certificat de l'artefact de l'exécution `39804616624` : `95369c79195c5725ea3e21bd5295abf3fab702231677ac4cda73194b7e55c57d` ; APK construit sur le poste Windows : `de4d039d7cacf3e740e6c05db82ef56056b61b19d5cbfafb299e4f1b807daf1a`. Le job CI crée une clé de débogage neuve (les runners sont jetables).
- **Attendu** : une signature de débogage stable, ou une procédure claire.
- **Conséquences** : (1) le PM ne peut pas installer par-dessus une version précédente venant d'une autre exécution CI ou du poste : Android refuse (« application non installée », conflit de signature) ; il faut désinstaller d'abord (c'est écrit dans le parcours de recette). (2) L'empreinte SHA-1 changeant à chaque exécution, la connexion Google (ticket Inscription) ne pourra pas fonctionner avec les APK de la CI tant qu'une clé de débogage stable n'est pas décidée. Piste : une clé de débogage dédiée au projet, stockée comme secret GitHub (jamais dans le dépôt), à décider avec le PM.

### AND-003 — Mineur (suspicion, à confirmer sur appareil) — L'écran d'accueil n'est pas défilable
- **Plateforme** : Android (build release : c'est son seul écran). **À corriger par** : `android-dev` si confirmé.
- **Lecture du code** : `HomeScreen` place un logo de 120 dp, un titre et une phrase dans une `Column` centrée, sans `verticalScroll` (contrairement à l'écran de diagnostic).
- **Reproduction proposée** (parcours PM, étape 10 et partie B) : mettre la taille de police du téléphone au maximum, ouvrir l'écran d'accueil et tourner le téléphone en paysage.
- **Attendu** : tout le contenu est lisible ou accessible en défilant.
- **Observé** : non constaté (aucun appareil à la disposition de la QA). Le haut ou le bas de la colonne pourrait être coupé sans moyen d'y accéder, ce qui contredirait l'esprit du critère A9 (aucun texte coupé sans possibilité de défiler), même si A9 vise explicitement l'écran de diagnostic.
- **Impact** : faible aujourd'hui (écran provisoire sans action) mais le même schéma serait gênant pour les écrans suivants.

## Écarts par rapport au ticket à faire accepter (décision PM ou revue sécurité, pas des défauts)

| Réf. | Écart | Où c'est documenté | Décision attendue |
|---|---|---|---|
| E-1 | `compileSdk` = 37 alors que le ticket (A1) dit 36 ; `targetSdk` reste 36 | `android/README.md`, section « Ecarts par rapport au ticket » | PM : accepter (les bibliothèques AndroidX actuelles l'exigent) |
| E-2 | Le manifeste release déclare 5 permissions (`INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `READ_GSERVICES`, et une permission interne AndroidX) alors que le critère A7 dit « aucune permission hormis le réseau » | `android/README.md`, section « Sécurité du manifeste » | Revue sécurité : accepter ou exiger le retrait de `WAKE_LOCK` (ajoutée automatiquement par Firebase). Aucune ne déclenche de demande à l'utilisateur ; aucune localisation |
| E-3 | Le manifeste release contient des activités exportées du SDK Firebase Auth (`GenericIdpActivity`, `RecaptchaActivity`) | manifeste fusionné | Information pour la revue sécurité : comportement standard du SDK, à confirmer qu'il est sans risque quand on n'utilise pas la connexion par fournisseur externe |
| E-4 | Anglais dans `res/values/` et français dans `res/values-fr/` (l'inverse de l'intuition du ticket) | README, contrat `i18n-keys.md` | Déjà tranché dans le contrat ; rien à faire |

## Limites connues documentées par le développeur (pas des défauts de ce ticket)

- Le cache Firestore local n'est pas purgé à la déconnexion (à traiter au ticket Inscription/Connexion ; il contiendra alors des états de position).
- Le téléversement du fichier de correspondance R8 vers Crashlytics est désactivé : les plantages d'un build release seront difficiles à lire.
- Le rendu des ombres Android ne reproduit pas exactement Figma (accepté dans le ticket, à valider par capture : parcours PM, étape 7).
- Points de design ouverts : graisse de Baloo Thambi 2, lecture du « dégradé à 17 % », espacements non tokenisés.
- Le logo et l'icône sont provisoires : **ne pas publier** en l'état. Ce n'est pas bloquant pour ce ticket, mais **bloquant pour toute soumission aux stores** tant que le vrai logo n'est pas livré.

## Non vérifié (pas un défaut, un trou de couverture)

| Sujet | Pourquoi | Comment le couvrir |
|---|---|---|
| Démarrage réel sur Android 8.0 (API 26) et Android 16 (API 36) | Aucun appareil ni émulateur | Émulateurs en CI, ou accepter le risque pour cette étape |
| Rendu de l'icône (rond/carré/squircle/thématique), polices, ombres | Rendu visuel | Parcours PM, étapes 1 et 7 |
| TalkBack, grande police, mode sombre forcé par ColorOS | Appareil | Parcours PM, étapes 9, 10, 13 |
| Build release (R8) sur appareil | Aucun APK release signé n'existe | Parcours PM, partie B (APK signé avec une clé jetable, fourni hors dépôt) |
| Tests 1 et 2 de l'appli contre le vrai projet | Compte de test du PM et appareil | Parcours PM, étapes 3 et 4. Le refus serveur de Test 1 a été confirmé par une requête REST hors appli (HTTP 403 `PERMISSION_DENIED`) |
| Absence de trafic Firebase avant consentement, preuves Analytics/Crashlytics en console | Pas d'observation réseau possible ; consoles | Parcours PM, étape 6 et partie C. Une preuve réseau formelle exigerait un proxy ou une capture |
| Mode avion | Appareil | Parcours PM, étape 12 |
| Pseudo-langues `en-XA` et `ar-XB` (lisibilité, retournement) | Peuvent ne pas être proposées par ColorOS | Si absentes de l'OPPO : captures d'émulateur ou de la CI |

## Défauts corrigés

Aucun pour l'instant.
