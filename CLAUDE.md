# Miff — App mobile

## Contexte
Projet piloté par un Product Manager (5 ans d'expérience produit, non-développeur, découvre Claude Code). Le design existe sur Figma, les premières specs sont sur Notion.

## Concept produit
Miff est une app de réassurance pour son cercle de confiance : *savoir que ses proches vont bien, sans les déranger*. On rejoint ou crée une "Miff" (groupe) via un code secret, on définit des "lieux de confiance" (geofencing iOS region monitoring / Android geofencing), et l'app signale automatiquement à son cercle qu'on va bien à l'entrée/sortie de ces lieux — sans action manuelle, sans conversation imposée.
- Modèle freemium : gratuit = 1 lieu de confiance, 1 seule Miff à la fois ; premium = lieux et Miffs illimités.
- 1 Miff = 1 code secret pour la rejoindre. L'onboarding n'est complet qu'une fois qu'on a créé ou rejoint une Miff.
- Le cercle de confiance peut être familial, amical, romantique — pas limité à une relation parent-enfant, même si c'est un cas d'usage probable.

## Audience & sensibilité produit
Deux principes transverses à respecter par tous les agents, pas seulement le design :
- **Simplicité, faible charge cognitive** : le geste central (signaler qu'on va bien) doit être automatique ou quasi instantané — parcours courts, peu de friction, pas de surcharge d'options ou de notifications.
- **Ton bienveillant, non anxiogène** : l'app touche à l'inquiétude pour un proche — formulations rassurantes, jamais moralisatrices, culpabilisantes ou alarmistes, y compris dans les messages d'erreur et les notifications (ex: "plus de nouvelles depuis 16h" doit rassurer/informer, pas paniquer).
- **Confiance et consentement explicites sur la localisation** : le partage de position est au cœur du produit — chaque agent (design, backend, dev, release) doit garder à l'esprit qu'un partage de localisation mal consenti ou mal expliqué est à la fois un risque de confiance utilisateur et un motif de rejet par les stores (Apple/Google exigent une justification claire de l'usage de la localisation en arrière-plan).

## Stack
- iOS natif : Swift + SwiftUI (dossier `ios/`)
- Android natif : Kotlin + Jetpack Compose (dossier `android/`)
- Backend : Firebase (Firestore, Authentication, Cloud Functions si nécessaire, FCM pour le push) — schéma de référence dans `backend/`
- Lancement simultané iOS + Android visé.
- Choix assumé : natif séparé (pas de partage de code iOS/Android), malgré une ancienne note Notion mentionnant Kotlin Multiplatform — décision confirmée avec le PM le 2026-08-30.

## Sources externes connectées
- **Notion** : espace privé du projet, page racine "👪 Miff" (contient pitch, règles métier, to-do, base de tâches dev). Source de vérité pour les specs produit.
- **Figma** :
  - Fichier original "Miff - App" (fileKey `Byi9NgQxkBPbmbhU403HSf`) — **référence en lecture seule, ne jamais y écrire**. 10 pages de contenu (~85 frames), conçu par le PM (non-designer) : pas de composants, pas de tokens, quasi tous les frames nommés "Welcoming screen". Audit complet dans `design/figma-audit.md`.
  - Fichier de travail "Miff - Claude" (fileKey `RbX84qKbxppvAy5iypLYP2`, https://www.figma.com/design/RbX84qKbxppvAy5iypLYP2) — **c'est ici que l'agent `design-system` construit** le vrai design system et reconstruit les écrans, par phases (fondations → composants → écrans flow par flow). Le fichier original reste intact et consultable si besoin de revenir en arrière.

## Sécurité & maîtrise des coûts (agents ios-dev, android-dev, backend-data)
Objectif du projet : valider si le concept plaît, avant d'investir davantage — donc minimiser les coûts d'infra tant que possible, sans jamais sacrifier la sécurité pour y arriver.
- **La sécurité n'est jamais négociable, même pour économiser.** Pas d'exception "temporaire" : pas de règles Firestore ouvertes, pas de clé secrète côté client, pas de validation sautée, pas de désactivation d'un contrôle de sécurité pour aller plus vite ou rester dans un tier gratuit.
- **Priorité aux solutions gratuites/généreuses tant qu'elles ne compromettent pas la sécurité ni les CGU du service.** Un contournement qui viole les conditions d'utilisation d'un service gratuit (et risque un bannissement du compte/projet) est considéré **pire** qu'un coût maîtrisé — jamais une option acceptable pour "rester gratuit".
- **Penser à la montée en charge dès le choix initial.** Si le concept marche et que le volume d'utilisateurs explose, un changement d'échelle ne doit jamais nécessiter une réécriture complète ni casser brutalement. Préférer des services avec un modèle "gratuit puis pay-as-you-grow" progressif (Firebase en est un bon exemple) plutôt que des hacks fragiles qui s'effondrent après un certain volume.
- **Documenter chaque choix "économique".** Tout contournement fait pour rester dans un tier gratuit (quota, service alternatif, limite technique) doit être noté avec sa limite connue et le signal qui indiquera qu'il faut basculer vers une solution payante — pour que le PM voie venir le coût avant qu'il n'explose.
- Utiliser des mécanismes anti-abus (ex: Firebase App Check, rate limiting) dès le début : ça protège à la fois contre les failles de sécurité ET contre l'explosion de coûts liée à un usage abusif des quotas gratuits.

## Structure du projet
- `docs/` — specs produit. `docs/specs/` = specs par fonctionnalité, `docs/qa/` = checklists et bugs connus, `docs/release/` = checklists de soumission stores.
- `design/` — exports Figma et design system dérivé (`design/design-system.md`, `design/screens/`).
- `backend/` — schéma de données Firestore, règles de sécurité, Cloud Functions.
- `ios/` — code source iOS.
- `android/` — code source Android.
- `marketing/` — contenu ASO, fiches store, plan de lancement.

## Équipe d'agents (`.claude/agents/`)
| Agent | Rôle | Entrée | Sortie |
|---|---|---|---|
| `product-specs` | Transforme les notes/Notion en specs structurées | `docs/` | `docs/specs/*.md` |
| `design-system` | Transforme les exports Figma en design system écrit | `design/` | `design/design-system.md`, `design/screens/*.md` |
| `backend-data` | Conçoit le modèle de données Firebase et les règles de sécurité | `docs/specs/` | `backend/` |
| `ios-dev` | Implémente l'app iOS | `docs/specs/`, `design/`, `backend/` | `ios/` |
| `android-dev` | Implémente l'app Android | `docs/specs/`, `design/`, `backend/` | `android/` |
| `qa-tester` | Teste et valide contre les critères d'acceptation | code + specs | `docs/qa/` |
| `release-manager` | Prépare la soumission App Store / Play Store | tout | `docs/release/`, `marketing/store-listing-*.md` |
| `aso-marketing` | Contenu ASO et marketing de lancement | `docs/specs/` | `marketing/` |

Pour invoquer un agent explicitement, demande par exemple : "utilise l'agent ios-dev pour implémenter l'écran de connexion". Claude Code peut aussi les sélectionner automatiquement selon la description de la tâche.

## Workflow recommandé
1. Déposer les notes Notion (export markdown/PDF ou copier-coller) dans `docs/`, et les exports Figma (captures, valeurs de tokens) dans `design/`.
2. `product-specs` structure la spec de la fonctionnalité → `docs/specs/`.
3. `design-system` traduit les écrans Figma concernés → `design/screens/` ; `backend-data` dérive le modèle de données et les règles de sécurité → `backend/`.
4. `ios-dev` et `android-dev` implémentent en parallèle à partir des mêmes specs/design/backend.
5. `qa-tester` valide contre les critères d'acceptation.
6. `release-manager` prépare la soumission ; `aso-marketing` prépare la fiche store et le lancement.

## Intégrations externes
Notion et Figma sont connectés via MCP (voir "Sources externes connectées" plus haut). Limite Figma : 200 appels MCP/jour (plan payant). Les specs vivent à la fois dans `docs/specs/` et dans la base Notion "Tâches développement Miff" ; l'état d'avancement de la reconstruction Figma est suivi dans `design/figma-design-system-state.json`.

## Identifiants du projet (décidés le 2026-10-16)
- **Firebase** : projet "Miff", ID `miff-3d283`. Firestore en `europe-west9` (Paris), édition Standard. Authentication : e-mail/mot de passe + Google. Google Analytics activé (avec consentement utilisateur ; jamais de position dans les événements).
- **Projet Firebase de Ghostrun (`ghostrun-app`) présent sur le même compte Google : ne jamais le cibler.** Toute commande Firebase doit porter `--project miff-3d283`.
- **Identifiant d'application (Android applicationId et iOS bundle ID)** : `com.tomessa.miff` — définitif une fois publié.
- **Apps Firebase enregistrées (2026-10-16)** : Android `1:29899146512:android:307ffd8ea71a4a37659195`, iOS `1:29899146512:ios:94e30481497dd74c659195`. Les fichiers de config (`google-services.json`, `GoogleService-Info.plist`) se régénèrent via `firebase apps:sdkconfig <PLATEFORME> <APP_ID> --project miff-3d283` et ne sont pas commités tant qu'une règle n'est pas actée.
- **À prévoir plus tard** : empreinte SHA-1 Android (indispensable pour la connexion Google), clé APNs pour les notifications iOS, activation de la protection contre la suppression de Firestore, Firebase App Check.
- **iOS sans Mac** : aucune compilation locale possible (poste Windows). Les builds iOS passent par une CI cloud (macOS) et se testent sur iPhone via TestFlight. Android se compile et se teste en local (Android Studio).
- **Process par ticket** : ticket Notion → challenge (`product-specs`) → validation PM → dev Android puis iOS (une branche et une PR par plateforme, jamais de push direct sur `main`) → code review en contexte neuf + revue sécurité → QA → recette PM → merge. Confirmation PM aux portes : ticket validé, début du dev, review terminée, prêt à merger.
- **Fusion (décidé par le PM le 2026-10-22)** : une fois la porte « prêt à merger » validée par un « oui » explicite du PM pour la PR concernée (CI verte, revues faites, recette faite), c'est Claude qui fusionne (`gh pr merge --merge`), pas le PM. Jamais de push direct sur `main`.
- **Exception INIT-3 (spike iOS)** : `workflow_dispatch` n'étant exécutable que depuis `main`, le workflow `ios-testflight` ne peut pas être essayé avant la fusion. Le spike est donc fusionné avant l'essai TestFlight (workflow manuel, jamais déclenché par une PR), puis corrigé par de petites PR si besoin. Cette exception ne vaut pas pour le code applicatif.

## Décisions complémentaires (2026-10-21)
- **Android : natif Kotlin + Compose, minSdk 26. iOS : SwiftUI, iOS 16 minimum.** (KMP écarté.) Aucune Cloud Function tant que le plan Blaze n'est pas activé (décision PM : pas pour l'instant).
- **Appareils de test du PM** : iPhone 11 Pro (iOS 26.5.2) et OPPO Find X5 Lite (Android 13). Les surcouches constructeur peuvent tuer les apps en arrière-plan : à surveiller pour le géofencing.
- **Paiement** : abonnement natif (App Store / Google Play) avec couche d'abstraction ; le premium est accordé à la main tant que Blaze n'est pas activé. Le modèle premium doit gérer mensuel/annuel, démarrage, arrêt (voir `docs/tickets/initialisation-technique.md`).
- **Langues** : français + anglais au départ, anglais en langue de repli, structure prête pour d'autres langues.
- **Comptes développeur** : Apple (99 €/an) et Google Play (25 €) déjà existants (créés pour Ghostrun) ; Miff y est une app distincte.
- **Environnements** : un seul projet Firebase pour l'instant ; un projet de développement séparé sera créé avant les premiers testeurs externes.
- **Statuts du tableau Notion « Tâches développement Miff »** (ordre du circuit) : Backlog discovery → A cadrer → Spec en attente de review IA → Backlog dev IA → En dev IA → En code review IA → En recette IA → En recette (PM) → Ready pour MEP → En prod. Je ne change un statut qu'avec l'accord du PM.

## Dépôt Git
Repo GitHub privé : https://github.com/Tomessa-Ghostrun/Miff (branche `main`). Totalement distinct du projet Ghostrun, qui ne doit jamais être touché depuis ce dossier.

**Dossier de travail : `C:\dev\miff`** (hors OneDrive, depuis le 2026-10-21). L'ancien dossier `C:\Users\tomes\OneDrive\Bureau\Miff by Claude` n'est plus utilisé : ne plus y travailler (risque de corruption de `.git` et de synchronisation de fichiers de build).

## Règle d'or : solutions gratuites d'abord (décision PM, 2026-10-21)
Pour tout choix d'outil, de service, de CI ou de bibliothèque : retenir la solution **gratuite** quand elle existe et qu'elle respecte la sécurité et les conditions d'utilisation. Aucun moyen de paiement n'est ajouté (GitHub, Firebase Blaze, Xcode Cloud, etc.) sans décision explicite du PM, et toute option payante est présentée avec son coût et son alternative gratuite.

## Règles générales
- Ne jamais deviner une décision produit, design, ou légale non fournie — les agents doivent signaler les ambiguïtés plutôt que d'inventer.
- Aucune action de paiement, création de compte développeur, ou soumission réelle aux stores n'est effectuée par un agent — uniquement préparée.
