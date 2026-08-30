# Miff — App mobile

## Contexte
Projet piloté par un Product Manager (5 ans d'expérience produit, non-développeur, découvre Claude Code). Le design existe sur Figma, les premières specs sont sur Notion.

## Stack
- iOS natif : Swift + SwiftUI (dossier `ios/`)
- Android natif : Kotlin + Jetpack Compose (dossier `android/`)
- Lancement simultané iOS + Android visé.

## Structure du projet
- `docs/` — specs produit. `docs/specs/` = specs par fonctionnalité, `docs/qa/` = checklists et bugs connus, `docs/release/` = checklists de soumission stores.
- `design/` — exports Figma et design system dérivé (`design/design-system.md`, `design/screens/`).
- `ios/` — code source iOS.
- `android/` — code source Android.
- `marketing/` — contenu ASO, fiches store, plan de lancement.

## Équipe d'agents (`.claude/agents/`)
| Agent | Rôle | Entrée | Sortie |
|---|---|---|---|
| `product-specs` | Transforme les notes/Notion en specs structurées | `docs/` | `docs/specs/*.md` |
| `design-system` | Transforme les exports Figma en design system écrit | `design/` | `design/design-system.md`, `design/screens/*.md` |
| `ios-dev` | Implémente l'app iOS | `docs/specs/`, `design/` | `ios/` |
| `android-dev` | Implémente l'app Android | `docs/specs/`, `design/` | `android/` |
| `qa-tester` | Teste et valide contre les critères d'acceptation | code + specs | `docs/qa/` |
| `release-manager` | Prépare la soumission App Store / Play Store | tout | `docs/release/`, `marketing/store-listing-*.md` |
| `aso-marketing` | Contenu ASO et marketing de lancement | `docs/specs/` | `marketing/` |

Pour invoquer un agent explicitement, demande par exemple : "utilise l'agent ios-dev pour implémenter l'écran de connexion". Claude Code peut aussi les sélectionner automatiquement selon la description de la tâche.

## Workflow recommandé
1. Déposer les notes Notion (export markdown/PDF ou copier-coller) dans `docs/`, et les exports Figma (captures, valeurs de tokens) dans `design/`.
2. `product-specs` structure la spec de la fonctionnalité → `docs/specs/`.
3. `design-system` traduit les écrans Figma concernés → `design/screens/`.
4. `ios-dev` et `android-dev` implémentent en parallèle à partir des mêmes specs/design.
5. `qa-tester` valide contre les critères d'acceptation.
6. `release-manager` prépare la soumission ; `aso-marketing` prépare la fiche store et le lancement.

## Intégrations externes
Aucun connecteur Figma/Notion n'est branché pour l'instant. En attendant, déposer manuellement les exports dans `docs/` et `design/`. À réévaluer si un connecteur devient disponible ou si le PM fournit un token API Figma/Notion.

## Règles générales
- Ne jamais deviner une décision produit, design, ou légale non fournie — les agents doivent signaler les ambiguïtés plutôt que d'inventer.
- Aucune action de paiement, création de compte développeur, ou soumission réelle aux stores n'est effectuée par un agent — uniquement préparée.
