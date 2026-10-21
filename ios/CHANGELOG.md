# Changelog iOS

## Non publié

### INIT-3 - Spike : build cloud, signature et TestFlight

Code prêt, **jamais exécuté** (pas de Mac) : à valider par la CI puis sur l'iPhone du PM.

- Projet XcodeGen (`ios/project.yml`) : cible iOS 16.0, `com.tomessa.miff`, nom affiché « Miff », configurations `Debug`, `Internal`, `Release`, numéro de build injecté par la CI.
- App « Hello Miff » (nom, version, build). Couleurs du design system en dur, volontairement provisoire (tokens dans INIT-4).
- Icône d'application **provisoire** (1024 x 1024, opaque) générée par `ios/tools/generate-app-icon.mjs` : ne pas publier.
- `PrivacyInfo.xcprivacy` (aucune collecte, aucune API à raison requise), aucune permission, aucune exception ATS. `ITSAppUsesNonExemptEncryption` volontairement absente : le PM répond dans App Store Connect.
- Tests XCTest (`BuildInfo`, réglages sensibles de l'app) et SwiftLint.
- Workflow `ios` (build + tests sur simulateur sans signature, durées dans le résumé) et workflow manuel `ios-testflight` (archive signée via clé d'API App Store Connect, envoi TestFlight).
- Aucune dépendance Swift Package (pas de `Package.resolved`), pas de CocoaPods.

**Choix économiques documentés** (détails et signaux de bascule dans `docs/dev/ios-spike.md`, sections 7 et 8) :
- Minutes macOS du quota gratuit GitHub (2 000 min/mois sur dépôt privé ; multiplicateur macOS probablement x10, **à vérifier**). Signal de bascule : plus de 70 % du quota consommé avant le 20 du mois.
- Pas d'« environment » GitHub pour protéger les secrets (indisponible sur dépôt privé en offre gratuite). Signal : passage à plusieurs contributeurs ou à GitHub Pro.
- Outils téléchargés en versions épinglées avec empreinte SHA-256 (XcodeGen 2.46.0, SwiftLint 0.65.1) plutôt qu'installés par `brew`.

**Durées CI mesurées** : à consigner après les premières exécutions (voir `docs/dev/ios-spike.md`, section 7).
