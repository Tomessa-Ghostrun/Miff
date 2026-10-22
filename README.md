# Miff

App mobile de réassurance pour son cercle de confiance : savoir que ses proches vont bien, sans les déranger. Android natif (Kotlin, Jetpack Compose), iOS natif (Swift, SwiftUI), backend Firebase. Le contexte produit et les règles de l'équipe sont dans [`CLAUDE.md`](CLAUDE.md).

## Structure du dépôt

| Dossier | Contenu |
|---|---|
| `docs/` | Specs produit (`specs/`), tickets (`tickets/`), contrats partagés iOS/Android (`contracts/`), notes de développement (`dev/`), QA (`qa/`), release (`release/`) |
| `design/` | Design system écrit et audit Figma |
| `backend/` | Schéma Firestore (`data-model.md`), règles de sécurité (`firestore.rules`), plan et tests des règles |
| `android/` | App Android (pas encore créée : ticket INIT-2) |
| `ios/` | App iOS (pas encore créée : tickets INIT-3 et INIT-4) |
| `scripts/` | Scripts de contrôle utilisés par la CI (parité des traductions) |
| `marketing/` | Contenu ASO et lancement |
| `.github/` | Modèle de pull request, Dependabot, workflows CI |

## Outils nécessaires sur Windows

| Outil | Version | Pour quoi |
|---|---|---|
| Git | récent | Versionner. Le dépôt vit **hors OneDrive** (ex. `C:\dev\miff`). |
| Node.js | 24.12 ou plus | Tests des règles, scripts. |
| Java (JDK) | **21 ou plus** | Émulateur Firestore (voir ci-dessous). |
| Android Studio | récent | Compiler et tester l'app Android (ticket INIT-2). |

Les outils Firebase (`firebase-tools`) sont installés dans `backend/` par `npm ci` : pas d'installation globale nécessaire.

**Java 21 pour l'émulateur.** `firebase-tools` 15 refuse de lancer l'émulateur Firestore avec Java 17 (message : « firebase-tools no longer supports Java version before 21 »). Le Java fourni avec Android Studio (JBR 17) ne suffit donc pas. Un JDK 21 gratuit (par exemple Eclipse Temurin 21) est nécessaire pour lancer les tests des règles sur votre poste. Sans lui, la CI GitHub les exécute à chaque pull request qui touche `backend/`.

## Lancer les tests des règles Firestore

```powershell
cd backend
npm ci
npm run test:unit        # tests rapides, sans émulateur (garde-fou, plan, scripts)
npm run test:emulator    # démarre l'émulateur Firestore local et lance les tests des règles
```

- Les tests utilisent le projet de démonstration `demo-miff-rules` et l'émulateur local : **aucune requête ne peut partir vers `miff-3d283` ni `ghostrun-app`**. Un garde-fou fait échouer les tests si l'environnement pointe vers un vrai projet.
- Résultat : un tableau « un cas du plan = une ligne » dans `backend/test-output/rules-report.md`, et les lectures de règles par opération dans `backend/test-output/rule-reads.md` (dossier non versionné).
- Un test en échec n'est jamais « réparé » en modifiant `firestore.rules` à la volée : il est consigné dans `backend/rules-test-report.md` pour `backend-data` et le PM.
- Les règles ne sont déployées qu'après tests verts, revue sécurité et accord du PM (voir `backend/README.md`).

Vérifier la parité des traductions Android/iOS : `node scripts/check-i18n-parity.mjs` (et `node --test "scripts/tests/*.test.mjs"` pour tester le script).

## Process de travail (pull requests)

1. **Jamais de push sur `main`.** Une branche et une pull request par plateforme et par sous-ticket (ex. `init-1/fondations`).
2. Ouvrir la PR avec le modèle `.github/PULL_REQUEST_TEMPLATE.md` : objectif, critères d'acceptation, risques, choix « économique » documenté, vérifications de sécurité, impact sur le schéma.
3. La CI doit être verte : `hygiene` (fichiers interdits, recherche de secrets, parité des traductions) sur toute PR ; `firestore-rules` quand `backend/**` change.
4. Revue de code en contexte neuf, revue sécurité, QA, recette du PM, puis merge **par le PM**.

## Règles de sécurité à retenir

- Aucun secret, clé ni fichier de configuration Firebase dans le dépôt : voir [`docs/dev/secrets.md`](docs/dev/secrets.md).
- Toute commande Firebase porte `--project miff-3d283`. Le projet `ghostrun-app` n'est jamais ciblé.
- Aucune règle Firestore ouverte, même temporairement.
- Aucune position dans les événements Analytics : voir [`docs/contracts/analytics-events.md`](docs/contracts/analytics-events.md). Clés de traduction communes : [`docs/contracts/i18n-keys.md`](docs/contracts/i18n-keys.md).
- Aucun moyen de paiement ni service payant ajouté sans décision explicite du PM.
