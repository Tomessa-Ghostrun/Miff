# Changelog iOS

## Non publié

### INIT-4 - Socle complet (PR #13)

Ajouté :
- **Firebase 13.1.1** (Swift Package Manager, version exacte, `Package.resolved` versionné, 13 paquets figés) : Firestore, Auth, App Check, Crashlytics, `FirebaseAnalyticsCore` (sans identifiant publicitaire). Analytics et Crashlytics **éteints au démarrage** (`Info.plist`), activés seulement par un choix enregistré (`PrivacySettings`). App Check : fournisseur de débogage en Debug, App Attest en Internal et Release (entitlement `Config/Miff.entitlements`). Mode non appliqué.
- Liste blanche d'événements Analytics (contrat v1) avec les mêmes cas de test qu'Android ; aucun événement ne porte de position.
- Design system en code (mêmes noms de tokens qu'Android, comparés par un test qui lit les fichiers Android), polices Baloo Thambi 2 et Hanken Grotesk (licences OFL), Dynamic Type, apparence claire imposée.
- String Catalog fr (source) + en (repli, `CFBundleDevelopmentRegion = en`), 83 clés communes avec Android ; SwiftLint refuse textes et couleurs en dur (avec auto-test des règles en CI).
- Écran de diagnostic et galerie de tokens (Debug et Internal seulement) + écran d'accueil ; l'écran de diagnostic est absent du binaire Release (vérifié en CI).
- `PrivacyInfo.xcprivacy` complet ; aucune clé de localisation ; aucune exception ATS.
- CI `ios` : configuration Firebase recréée depuis le secret `GOOGLE_SERVICE_INFO_PLIST_B64` (factice et évidente pour Dependabot), cache des paquets, contrôles du binaire, compilation Release, captures (français, anglais, langue non prise en charge, pseudo-langue allongée, droite-à-gauche, mode sombre, grande taille de texte, test 1). `ios-testflight` : même étape de configuration, `Package.resolved` appliqué, garde-fous de signature **inchangés**.
- 94 tests XCTest ; entrée Dependabot `swift` pour `/ios` (efficacité non vérifiée).

Changé : `HelloView` du spike remplacé par `HomeView` ; `BuildInfo.summary` supprimé (texte en dur) au profit de textes du catalogue.

**Durées CI macOS** (`macos-26`, Xcode 26.x) :
- Premier run (sans cache, commit `fb9f779`) : job 7 min 36 s (XcodeGen 0 s ; garde-fou d'identifiant 68 s car il résolvait les paquets ; résolution 20 s ; SwiftLint 2 s ; compilation Debug 49 s ; tests 88 s dont 94 tests en 3,5 s ; compilation Release 62 s ; captures 124 s). Environ 8 min facturées avant multiplicateur macOS (à vérifier, probablement x10).
- Deuxième run (cache des paquets, commit `fd1905e`) : job 5 min 55 s (résolution 15 s ; compilation Debug 50 s ; tests 71 s ; compilation Release 60 s ; captures 104 s).
- Run après les corrections de revue (compilation Internal ajoutée, ~+1 min) : voir « Durées après revue » ci-dessous.

**Corrections de revue (PM, 2026-10-24)** :
- I-1 : la CI compile la configuration **Internal** (appareil, sans signature) et vérifie diagnostic présent, App Check de débogage absent, Info.plist cohérent ; `ios-testflight` contrôle après l'archive l'Info.plist, le projet Firebase embarqué, le diagnostic et les entitlements (App Attest) en plus de l'identité.
- I-2 : `ci/crashlytics-upload-symbols.sh` ne fait plus rien en CI ni avec la clé App Store Connect dans l'environnement ; l'envoi des symboles est une étape séparée sans secret d'`ios-testflight` (non testée).
- I-3 : délai maximal du job 30 -> 15 min ; le déclencheur ignore `ios/**/*.md` et `ios/third_party/**` et ajoute le thème Android ; compilations Release et Internal et captures seulement si l'interface, les ressources, le projet ou la CI changent.
- I-4 (renvoyé à DUR-3) : ligne dans le README, rien n'est purgé ni exclu des sauvegardes ici.
- I-5 : le résultat de chaque test est annoncé à VoiceOver.
- M-1 `FirebaseDataCollectionDefaultEnabled = NO` + contrôle CI de l'Info.plist du binaire ; M-2 témoin positif en Debug et recherche de `advertisingIdentifier` ; M-3 un test ignoré fait échouer le job (sauf Dependabot) + test des clés de texte du code ; M-4 règle SwiftLint étendue (`DiagLine`, `SectionCard`, `SwitchRow`, `NavigationLink`, `ProgressView`, `Picker`, `Menu`, `.alert`, `.accessibilityValue`) ; M-6 `-onlyUsePackageVersionsFromResolvedFile` à l'archive ; M-7 mot de passe conservé si la saisie locale est invalide, raison de refus en `snake_case` (« unknown_event »), texte `diag_privacy_description` corrigé (« aucune donnée d'usage ni de plantage n'est envoyée avant votre accord ») ; M-8 options de lancement du diagnostic limitées à Debug et projet vérifié (`miff-3d283`) avant `FirebaseApp.configure` ; M-5 Dependabot `swift` laissé tel quel (noté dans le README).

**Choix économiques** : cache des paquets Swift (GitHub Actions, gratuit) ; compilation Release de contrôle (environ 1 min de macOS) désactivable avec `RELEASE_CHECK: "false"` ; captures limitées à 11 images. Signal de bascule : mêmes seuils que le spike (plus de 70 % du quota avant le 20 du mois).

**Non vérifié (seulement possible sur iPhone ou avec un compte de test)** : Test 2 (compte de test), App Attest et signature (`ios-testflight` jamais exécuté), rendu sur iPhone réel, ombres, absence de trafic Firebase avant consentement, plantage de test visible dans Crashlytics, envoi des symboles Crashlytics, rendu en iOS 16.

### INIT-3 - Spike : build cloud, signature et TestFlight

État au 2026-10-22 :
- Workflow `ios` : **vert** sur la PR #1 (run 39659012614 : génération XcodeGen, SwiftLint 0 violation, build-for-testing, 10 tests, 0 échec ; 1 min 40 de job, environ 2 min facturées).
- Workflow `ios-testflight` (signature et envoi TestFlight) : **jamais exécuté** (pas de Mac ; attend les actions du PM, la clé d'API et la fusion sur `main`, car manuel). Installation sur l'iPhone non vérifiée.

- Projet XcodeGen (`ios/project.yml`) : cible iOS 16.0, `com.tomessa.miff`, nom affiché « Miff », configurations `Debug`, `Internal`, `Release`, numéro de build injecté par la CI.
- App « Hello Miff » (nom, version, build). Couleurs du design system en dur, volontairement provisoire (tokens dans INIT-4).
- Icône d'application **provisoire** (1024 x 1024, opaque) générée par `ios/tools/generate-app-icon.mjs` : ne pas publier.
- `PrivacyInfo.xcprivacy` (aucune collecte, aucune API à raison requise), aucune permission, aucune exception ATS. `ITSAppUsesNonExemptEncryption` volontairement absente : le PM répond dans App Store Connect.
- Tests XCTest (`BuildInfo`, réglages sensibles de l'app) et SwiftLint.
- Workflow `ios` (build + tests sur simulateur sans signature, durées dans le résumé, timeout 15 min) et workflow manuel `ios-testflight` (archive via clé d'API App Store Connect, envoi TestFlight, timeout 20 min).
- Garde-fous (compte Apple partagé avec Ghostrun) : échec si `PRODUCT_BUNDLE_IDENTIFIER` n'est pas exactement `com.tomessa.miff` (les deux workflows) ; étape « Identité de l'archive » bloquante (identifiant, `MinimumOSVersion` 16.0, numéro de build). Non testés sur CI.
- `archive_mode` vaut `unsigned` par défaut (évite des certificats de développement orphelins « Created via API » ; support non confirmé), `signed` en repli.
- Corrections de revue (2026-10-22) : `TARGETED_DEVICE_FAMILY = 1` posé au niveau de la cible (XcodeGen y applique « 1,2 »), test `UIDeviceFamily` ; `ITSAppUsesNonExemptEncryption = false` (décision PM) avec test ; `ios-testflight` : `archive_mode` validé, chemin de la clé non exporté (clé non écrite pour l'archive `unsigned`), artefacts d'échec assainis (Team/Key/Issuer ID masqués, pas de dossier d'export), timeouts 10 min par étape d'archive/export, avertissement si Xcode 27+, contrôle bloquant de `UIDeviceFamily` et du chiffrement.
- Décision PM 2026-10-22 : le spike est fusionné avant l'essai TestFlight ; I3 sera prouvé depuis `main`.
- Aucune dépendance Swift Package (pas de `Package.resolved`), pas de CocoaPods.

**Choix économiques documentés** (détails et signaux de bascule dans `docs/dev/ios-spike.md`, sections 7 et 8) :
- Minutes macOS du quota gratuit GitHub (2 000 min/mois sur dépôt privé ; multiplicateur macOS probablement x10, **à vérifier**). Signal de bascule : plus de 70 % du quota consommé avant le 20 du mois.
- Pas d'« environment » GitHub pour protéger les secrets (indisponible sur dépôt privé en offre gratuite). Signal : passage à plusieurs contributeurs ou à GitHub Pro.
- Outils téléchargés en versions épinglées avec empreinte SHA-256 (XcodeGen 2.46.0, SwiftLint 0.65.1) plutôt qu'installés par `brew`.

**Points ouverts** : rôle minimal de la clé d'API (Admin temporaire prévu, puis App Manager à vérifier) ; plan B d'envoi `altool` documenté, non testé ; chemin de la clé `.p8` présent dans l'environnement des étapes suivantes (à revoir avec les paquets tiers d'INIT-4) ; durées de `ios-testflight` à mesurer.
