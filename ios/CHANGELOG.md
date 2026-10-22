# Changelog iOS

## Non publié

### INIT-3 - Spike : build cloud, signature et TestFlight

État au 2026-10-22 :
- Workflow `ios` : **vert** sur la PR #1 (run 39659012614 : génération XcodeGen, SwiftLint 0 violation, build-for-testing, 10 tests, 0 échec ; 1 min 40 de job, environ 2 min facturées).
- Workflow `ios-testflight` (signature et envoi TestFlight) : **jamais exécuté** (pas de Mac ; attend les actions du PM et la clé d'API). Installation sur l'iPhone non vérifiée.

- Projet XcodeGen (`ios/project.yml`) : cible iOS 16.0, `com.tomessa.miff`, nom affiché « Miff », configurations `Debug`, `Internal`, `Release`, numéro de build injecté par la CI.
- App « Hello Miff » (nom, version, build). Couleurs du design system en dur, volontairement provisoire (tokens dans INIT-4).
- Icône d'application **provisoire** (1024 x 1024, opaque) générée par `ios/tools/generate-app-icon.mjs` : ne pas publier.
- `PrivacyInfo.xcprivacy` (aucune collecte, aucune API à raison requise), aucune permission, aucune exception ATS. `ITSAppUsesNonExemptEncryption` volontairement absente : le PM répond dans App Store Connect.
- Tests XCTest (`BuildInfo`, réglages sensibles de l'app) et SwiftLint.
- Workflow `ios` (build + tests sur simulateur sans signature, durées dans le résumé, timeout 15 min) et workflow manuel `ios-testflight` (archive via clé d'API App Store Connect, envoi TestFlight, timeout 20 min).
- Garde-fous (compte Apple partagé avec Ghostrun) : échec si `PRODUCT_BUNDLE_IDENTIFIER` n'est pas exactement `com.tomessa.miff` (les deux workflows) ; étape « Identité de l'archive » bloquante (identifiant, `MinimumOSVersion` 16.0, numéro de build). Non testés sur CI.
- `archive_mode` vaut `unsigned` par défaut (évite des certificats de développement orphelins « Created via API » ; support non confirmé), `signed` en repli.
- Aucune dépendance Swift Package (pas de `Package.resolved`), pas de CocoaPods.

**Choix économiques documentés** (détails et signaux de bascule dans `docs/dev/ios-spike.md`, sections 7 et 8) :
- Minutes macOS du quota gratuit GitHub (2 000 min/mois sur dépôt privé ; multiplicateur macOS probablement x10, **à vérifier**). Signal de bascule : plus de 70 % du quota consommé avant le 20 du mois.
- Pas d'« environment » GitHub pour protéger les secrets (indisponible sur dépôt privé en offre gratuite). Signal : passage à plusieurs contributeurs ou à GitHub Pro.
- Outils téléchargés en versions épinglées avec empreinte SHA-256 (XcodeGen 2.46.0, SwiftLint 0.65.1) plutôt qu'installés par `brew`.

**Points ouverts** : rôle minimal de la clé d'API (Admin temporaire prévu, puis App Manager à vérifier) ; plan B d'envoi `altool` documenté, non testé ; chemin de la clé `.p8` présent dans l'environnement des étapes suivantes (à revoir avec les paquets tiers d'INIT-4) ; durées de `ios-testflight` à mesurer.
