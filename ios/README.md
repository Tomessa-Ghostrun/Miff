# Miff - application iOS

SwiftUI, iOS 16 minimum, identifiant `com.tomessa.miff`. État actuel : **spike INIT-3** (écran « Hello Miff », sans Firebase ni fonctionnalité) dont le but est de prouver la chaîne CI -> signature -> TestFlight sans Mac.

## Principe

Il n'y a **pas de `.xcodeproj` dans le dépôt**. Le projet est décrit dans `project.yml` et généré par [XcodeGen](https://github.com/yonaskolb/XcodeGen) (MIT) :

```sh
xcodegen generate --spec project.yml   # sur un Mac ; la CI le fait toute seule
```

Poste de travail Windows : on édite les fichiers texte (Swift, `project.yml`, catalogue d'actifs en JSON) ; la compilation et les tests ont lieu sur GitHub (runner `macos-26`).

## Contenu

| Chemin | Rôle |
|---|---|
| `project.yml` | Projet XcodeGen : cible `Miff`, tests `MiffTests`, configurations `Debug` / `Internal` (TestFlight) / `Release` |
| `Miff/` | Code (`Views/`, `Models/`) et ressources (`Resources/` : icône, `PrivacyInfo.xcprivacy`) |
| `MiffTests/` | Tests XCTest |
| `tools/generate-app-icon.mjs` | Génère l'icône **provisoire** (`node ios/tools/generate-app-icon.mjs`) |
| `ci/` | `timed.sh` (durées dans le résumé CI) et modèle `ExportOptions.plist` |
| `.swiftlint.yml` | Règles SwiftLint (lancer `swiftlint lint --strict` depuis `ios/`) |

Générés, non versionnés : `Miff.xcodeproj/`, `Config/Info.plist`, `build/`.

## CI

- `.github/workflows/ios.yml` : sur les PR qui touchent `ios/**` : XcodeGen, SwiftLint, build et tests sur simulateur **sans signature**, durées dans le résumé du job.
- `.github/workflows/ios-testflight.yml` : **manuel** : archive signée (clé d'API App Store Connect) et envoi à TestFlight.
- Pas de dépendance Swift Package, donc pas de `Package.resolved` ; pas de CocoaPods.

Mode d'emploi complet pour le PM, erreurs probables et suivi des minutes macOS : [`docs/dev/ios-spike.md`](../docs/dev/ios-spike.md).

## Règles

- Aucun secret dans le dépôt : la clé `.p8` et les identifiants vivent dans les secrets GitHub (`.gitignore` bloque `*.p8`).
- Aucune permission, aucune exception ATS, `ITSAppUsesNonExemptEncryption = false` (chiffrement standard uniquement, décision PM 2026-10-22), iPhone uniquement (`TARGETED_DEVICE_FAMILY` posé au niveau de la cible).
- Couleurs et textes en dur **uniquement pour le spike** : remplacés par les tokens du design system et le String Catalog dans INIT-4.
