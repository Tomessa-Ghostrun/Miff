# Miff - application iOS

SwiftUI, iOS 16 minimum, iPhone seulement, identifiant `com.tomessa.miff`. Contenu actuel : le **socle technique** (ticket INIT-4, miroir d'INIT-2 Android) : Firebase, design system en code, traductions, écran de diagnostic interne. Aucune fonctionnalité visible par un utilisateur final n'existe encore.

> **ICÔNE ET LOGO PROVISOIRES : NE PAS PUBLIER.**
> Le logo n'a pas encore été fourni par le PM. L'icône (`Miff/Resources/Assets.xcassets/AppIcon.appiconset`, générée par `tools/generate-app-icon.mjs`) et le logo de l'écran d'accueil (`MiffProvisionalLogo` dans `Miff/Views/HomeView.swift`, dessiné en SwiftUI : « M » blanc et cœur jaune-vert sur fond bleu) sont des dessins simples faits pour le socle.

**Poste Windows sans Mac** : rien ne se compile ni ne se teste en local. Tout est validé par la CI (GitHub Actions, runner `macos-26`) et, pour ce qui touche à l'iPhone (signature, App Attest, rendu réel), par TestFlight. Les passages marqués « à vérifier » sont des points que seul un vrai iPhone peut confirmer.

## Principe

Il n'y a **pas de `.xcodeproj` dans le dépôt**. Le projet est décrit dans `project.yml` et généré par [XcodeGen](https://github.com/yonaskolb/XcodeGen) (MIT) :

```sh
xcodegen generate --spec project.yml   # sur un Mac ; la CI le fait toute seule
```

## Contenu

| Chemin | Rôle |
|---|---|
| `project.yml` | Projet XcodeGen : cible `Miff`, tests `MiffTests`, configurations `Debug` / `Internal` / `Release`, dépendances Swift Package, clés `Info.plist` |
| `Miff/App/` | Démarrage de Firebase (`FirebaseBootstrap`), fournisseur App Check, conteneur de dépendances (`AppEnvironment`) |
| `Miff/Models/` | Logique pure, testée sans réseau : liste blanche Analytics, interprétation des erreurs de connexion, logique du diagnostic |
| `Miff/Services/` | Choix de confidentialité (`PrivacySettings`) et branchement sur les SDK Firebase |
| `Miff/DesignSystem/` | Tokens : couleurs, styles de texte, arrondis, ombres, dégradés, bouton primaire |
| `Miff/Views/` | Écran d'accueil et racine de l'app |
| `Miff/Diagnostic/` | Écran de diagnostic et galerie de tokens (**compilés seulement en Debug et Internal**) |
| `Miff/Localization/` | `L10n` : accès aux textes du String Catalog |
| `Miff/Resources/` | Icône, polices (`Fonts/`), `Localizable.xcstrings`, `InfoPlist.xcstrings`, `PrivacyInfo.xcprivacy` |
| `Config/Miff.entitlements` | Capacité App Attest (builds Internal et Release seulement) |
| `MiffTests/` | Tests XCTest (94 au dernier contrôle) |
| `ci/` | Scripts de la CI (durées, résolution des paquets, captures, contrôles du binaire, symboles Crashlytics) |
| `third_party/fonts/` | Licences et provenance des polices |
| `.swiftlint.yml` | Règles SwiftLint, dont l'interdiction des textes et couleurs en dur |
| `Package.resolved` | Versions figées de **toutes** les dépendances Swift (versionné) |

Générés, non versionnés : `Miff.xcodeproj/`, `Config/Info.plist`, `Config/GoogleService-Info.plist`, `build/`.

## Architecture

MVVM simple, sans bibliothèque d'injection : `View` (SwiftUI) -> `ViewModel` (`ObservableObject`, `@Published`, `async/await`) -> `Services` / `Models`. Le `@Observable` d'iOS 17 n'est pas utilisé (iOS 16 minimum). `NavigationStack` pour la navigation. Les dépendances sont passées par initialiseur via `AppEnvironment`. Mode de langage Swift 5 (pas Swift 6 strict) : décision du spike, à réévaluer quand les écrans de fonctionnalités arriveront.

## Configurations de build

| | `Debug` | `Internal` | `Release` |
|---|---|---|---|
| Usage | simulateur, CI `ios` | TestFlight (CI `ios-testflight`, manuel) | futur build public |
| Écran affiché au démarrage | **Diagnostic interne** (lien vers l'accueil) | **Diagnostic interne** | Écran d'accueil |
| Code du diagnostic | compilé | compilé | **absent** (`#if DEBUG \|\| INTERNAL`) |
| App Check | fournisseur de débogage | **App Attest** | **App Attest** |
| Entitlement App Attest | non | oui | oui |
| Événement Analytics `analytics_diagnostic_test` | autorisé | autorisé | refusé (hors liste blanche) |

Preuve que le build Release ne contient pas le diagnostic : la CI le compile (simulateur) et cherche dans l'exécutable les marqueurs `DIAGNOSTICPROBE`, `MiffDebugAppCheck`, `DiagnosticViewModel`... (`ci/check-binaries.sh release`) ; résultat vert au dernier contrôle. Les textes `diag_*` du catalogue restent dans l'app (texte inerte, sans code pour l'afficher) ; Android les retire par réduction des ressources, iOS ne le fait pas.

## Configuration Firebase (projet `miff-3d283`)

`GoogleService-Info.plist` n'est **jamais** dans le dépôt (`.gitignore`, contrôlé par le job `hygiene`).

- **CI** : le workflow `ios` (et `ios-testflight`) le recrée avant la génération du projet depuis le secret GitHub `GOOGLE_SERVICE_INFO_PLIST_B64` (base64), vérifie `PROJECT_ID = miff-3d283` et `BUNDLE_ID = com.tomessa.miff`, puis le supprime en fin de job. Une PR de Dependabot, qui ne peut pas lire les secrets, reçoit un fichier **évidemment factice** (`fake-ci-config-do-not-use`) : le projet compile, aucune capture n'est publiée.
- **Secret manquant** : le job échoue avec « Secret manquant ». Création (PowerShell) : `[Convert]::ToBase64String([IO.File]::ReadAllBytes("chemin\GoogleService-Info.plist")) | gh secret set GOOGLE_SERVICE_INFO_PLIST_B64 --repo Tomessa-Ghostrun/Miff`.
- **Poste de développement (Mac)** : `firebase apps:sdkconfig IOS 1:29899146512:ios:94e30481497dd74c659195 --project miff-3d283 > Config/GoogleService-Info.plist`, puis `xcodegen generate`. Toujours avec `--project miff-3d283` ; le projet `ghostrun-app` n'est jamais ciblé. Sans ce fichier l'app démarre quand même (Firebase n'est simplement pas configuré, le diagnostic affiche « Projet non identifié »).
- **Tests unitaires** : Firebase n'est pas configuré dans le processus de test (aucun réseau, rien d'envoyé).

## Confidentialité : Analytics et Crashlytics

- `Info.plist` : `FIREBASE_ANALYTICS_COLLECTION_ENABLED = NO` (la clé irréversible `..._DEACTIVATED` n'est **pas** utilisée), `FirebaseCrashlyticsCollectionEnabled = NO`, `FirebaseDataCollectionDefaultEnabled = NO` (collecte par défaut éteinte pour tous les SDK Firebase ; chaque SDK est réactivé explicitement après accord), `GOOGLE_ANALYTICS_IDFV_COLLECTION_ENABLED = NO`, signaux publicitaires et attribution désactivés, `FirebaseAutomaticScreenReportingEnabled = NO` (aucun nom d'écran dans Analytics).
- SDK utilisé : **`FirebaseAnalyticsCore`** (sans identifiant publicitaire). La CI vérifie sur le binaire construit que `AdSupport` et `AppTrackingTransparency` ne sont pas liés (Debug et Release : vert).
- Le choix de l'utilisateur est stocké sur l'appareil (`PrivacySettings`, `UserDefaults`, déclaré dans le manifeste de confidentialité) et réappliqué à chaque lancement. Éteint par défaut. Pour l'instant, seul l'écran de diagnostic peut l'allumer ; le vrai écran de consentement viendra avec l'onboarding.
- Tout événement passe par `AnalyticsTracker` (point d'entrée typé `AnalyticsEvent`), qui applique la **liste blanche** de `docs/contracts/analytics-events.md` (version 1) implémentée dans `Models/Analytics/AnalyticsContract.swift`, avec les mêmes cas de test qu'Android. Aucun événement ne porte de position.
- **Ce que la liste blanche ne couvre pas** (idem Android) : une fois Analytics activé, le SDK envoie lui-même des événements automatiques (`first_open`, `session_start`...) et Google déduit une zone géographique approximative de l'adresse IP. À dire dans la politique de confidentialité ; réglage GA4 à faire par le PM (voir le contrat).
- **Non prouvé** : l'absence totale de trafic réseau Firebase avant consentement (Installations, App Check, Firestore peuvent émettre des appels techniques sans lien avec Analytics). Il faudrait observer le trafic d'un iPhone (proxy).
- **Aucune clé de localisation** (`NSLocation*`), aucune exception ATS, `ITSAppUsesNonExemptEncryption = false` (chiffrement standard seulement, décision PM du 2026-10-22). Ces points sont testés (`AppConfigurationTests`) et contrôlés sur l'`Info.plist` du build par la CI.
- `PrivacyInfo.xcprivacy` : pas de suivi, `UserDefaults` (raison CA92.1) ; données collectées *seulement après accord* : interactions produit, identifiant d'instance, plantages, diagnostics. **À faire relire par le release-manager** avant la soumission (la fiche « confidentialité » d'App Store Connect doit correspondre ; les SDK Firebase embarquent leurs propres manifestes).

## App Check

- `Debug` : fournisseur de débogage. Au premier lancement, le SDK écrit un jeton dans la console Xcode ; il peut être enregistré dans Firebase > App Check > Gérer les jetons de débogage. **Ce jeton est privé** (jamais dans le dépôt, un ticket ou une conversation ; `.gitignore` bloque les fichiers `appcheck-debug-token*`).
- `Internal` / `Release` : **App Attest** (iPhone réel, build signé). Mode **non appliqué** côté Firebase : un échec d'obtention du jeton n'empêche pas les lectures.
- L'entitlement `com.apple.developer.devicecheck.appattest-environment = production` est posé par `Config/Miff.entitlements` pour `Internal` et `Release`.

### Risque : signature et App Attest (à surveiller au premier TestFlight)

Le workflow manuel `ios-testflight` signe par « signature automatique » avec la clé d'API App Store Connect. Deux choses peuvent mal tourner (non vérifiées, aucun Mac) :

1. **La capacité « App Attest » n'est pas activée sur l'identifiant d'app** `com.tomessa.miff` : l'archive ou l'export échoue avec un message du type « Provisioning profile ... doesn't include the com.apple.developer.devicecheck.appattest-environment entitlement ». *Plan B* : le PM active la capacité à la main dans developer.apple.com > Certificats, identifiants et profils > Identifiants > `com.tomessa.miff` > Capacités > App Attest, puis relance le workflow.
2. **Mode d'archive `unsigned`** (par défaut du workflow) : l'archive est faite sans signature et signée à l'export ; les entitlements pourraient ne pas être repris dans le binaire final. Symptôme : build installé, mais « Pas de jeton pour le moment » à la vérification App Check du diagnostic. *Plan B* : relancer en mode `signed`.
3. **Plan C** (si rien ne marche et que le PM veut tester quand même) : retirer temporairement les deux lignes `CODE_SIGN_ENTITLEMENTS` de `project.yml`. L'app fonctionne alors sans App Check (mode non appliqué), au prix de ne pas tester App Attest.

## Design system en code

`Miff/DesignSystem/` : `ColorTokens.swift`, `TypographyTokens.swift`, `ShapeTokens.swift` (arrondis, traits, ombres, dégradés, espacements), `MiffButtonStyle.swift`.

- **Mêmes noms de code qu'Android** pour les couleurs (`colorBrandPrimary`, `colorActionPrimary`, `colorStatusSafe`, `colorTextError`...) et les styles de texte (`textDisplayHero`, `textBodyRegular14`...). Arrondis, ombres et dégradés : mêmes noms en `lowerCamelCase` (`radiusMd`, `shadowDefault`, `gradientAuthBackground`), car Swift interdit une constante globale qui commence par une majuscule ; la galerie affiche les noms Android d'origine. Un test (`ColorTokensTests`) **lit les fichiers Android** (`Color.kt`, `Type.kt`) et compare noms, couleurs, opacités, tailles et interlignes (vert en CI).
- `colorTextError` vaut `#D60700` (contraste d'au moins 4,5 : 1 sur les fonds de l'app, testé) ; `colorStatusDanger` (`#FF5650`) reste réservé aux anneaux, pastilles et fonds.
- **Polices** : Baloo Thambi 2 (styles `display/*`) et Hanken Grotesk (le reste), embarquées avec leurs licences OFL (`third_party/fonts/README.md`). Déclarées dans `UIAppFonts` ; rendu constaté sur les captures de la CI.
- **Dynamic Type** : taille et interligne suivent la taille de texte du système (`ScaledMetric`) ; capture « très grande taille de texte » : le texte passe à la ligne, l'écran défile.
- **Apparence claire imposée** : `UIUserInterfaceStyle = Light` et `preferredColorScheme(.light)` ; capture avec le système en mode sombre : l'app reste claire.
- Écarts de plateforme (à valider sur capture, critère P6) : l'ombre SwiftUI utilise la moitié du flou de la maquette ; SwiftUI sur iOS 16 ne fixe pas une hauteur de ligne exacte (l'interligne est reproduit pour les textes sur plusieurs lignes, pas la hauteur d'une ligne seule) ; cible tactile minimale de 44 pt (Apple) au lieu de 48 dp ; les polices Hanken Grotesk sont les fichiers statiques du dépôt amont (v3.014), Android embarque la police variable de Google Fonts (mesures verticales potentiellement très légèrement différentes).
- Points de design ouverts, hérités d'Android : graisse de Baloo Thambi 2 (Regular utilisée), « dégradé à 17 % » lu comme position d'un arrêt de couleur, espacements non tokenisés.

## Traductions

- `Miff/Resources/Localizable.xcstrings` : String Catalog, **français = langue source**, anglais présent. `CFBundleDevelopmentRegion = en` : un téléphone dans une langue non prise en charge affiche l'**anglais** (capture CI avec la langue allemande : texte anglais).
- **Aucun texte en dur** : un texte statique s'écrit `Text("cle_snake_case")` ; un texte avec valeurs passe par `L10n.format` (clés positionnelles `%1$@`, `%2$lld`) ; un pluriel par `L10n.plural`. SwiftLint **refuse** tout autre littéral dans `Text`, `Button`, `Toggle`, `Label`, `TextField`, `SecureField`, `Section`, `.navigationTitle` et `.accessibilityLabel`, ainsi que toute couleur hexadécimale hors du design system. La CI contient un **auto-test** de ces règles (un fichier d'essai fautif doit être signalé).
- **Mêmes clés qu'Android** (contrat `docs/contracts/i18n-keys.md`, contrôle `scripts/check-i18n-parity.mjs` du job `hygiene` : 83 clés communes, vert). Le catalogue a été produit à partir des textes Android puis adapté pour iOS (unités `pt`, texte App Check, version/build).
- **Clé héritée d'Android** : `diag_build_android` affiche ici « iOS 26.5 (iPhone) ». Le nom de la clé est trompeur côté iOS ; la renommer en `diag_build_os` demande une PR sur les deux plateformes (non fait ici : `android/` n'est pas touché).
- `InfoPlist.xcstrings` : vide (aucune permission, donc aucun texte de permission).
- Tests : chaque clé a un texte français et anglais, mêmes valeurs insérées dans les deux langues, clés conformes au contrat, pluriels (`LocalizationTests`).
- Pour ajouter une langue : ajouter ses textes dans `Localizable.xcstrings` pour toutes les clés, puis son code dans `CFBundleLocalizations` (`project.yml`).
- Droite-à-gauche : mise en page avec `leading`/`trailing` (jamais gauche/droite) ; la capture CI force la direction droite-à-gauche et la mise en page se retourne. Ni Baloo Thambi 2 ni Hanken Grotesk n'ont de glyphes arabes : le système utilise sa police de repli.
- Pseudo-langue « double longueur » de la CI (`-NSDoubleLocalizedStrings YES`) : les textes sont doublés sans rien couper. Limite de l'outil : sur les textes à valeurs insérées (`%1$@`), le doublage du système fait afficher « 1$@ » dans la première moitié ; c'est un artefact de l'outil de test, pas des vraies traductions.

## Écran de diagnostic (Debug et Internal uniquement)

Même contenu et mêmes résultats que sur Android. Il ne fait **aucune écriture**.

1. **Version installée** : version, build, configuration, appareil, projet Firebase (avertissement si ce n'est pas `miff-3d283`), langue, sens du texte.
2. **Test 1** : lecture d'une Miff sans être connecté ; attendu : refus = « Règles actives : OK ». Si la lecture réussit : avertissement clair (règles ouvertes). La CI le lance au simulateur contre le vrai Firestore (une lecture, sans compte) : capture `10-fr-test1`.
3. **Test 2** : e-mail et mot de passe du compte de test (créé à la main dans la console Firebase ; jamais conservés ni écrits dans le code), lecture de `users/{uid}` et `entitlements/{uid}`, puis déconnexion. « Pas encore de profil » et « Offre : Classic » sont des résultats normaux. **Non exécuté par la CI** (pas de compte de test dans la CI).
4. **App Check** : indique si un jeton est obtenu (la valeur n'est jamais lue ni affichée).
5. **Analytics et rapports de plantage** : deux interrupteurs éteints par défaut, un événement de test, un bouton qui tente d'envoyer une position (doit être refusé), un bouton de plantage de test (actif seulement si les rapports sont activés).
6. **Galerie de tokens** : couleurs (nom de code et valeur), styles de texte, arrondis, ombres, dégradés.
7. **Langue** : phrase longue, texte court, pluriel.

Options de lancement réservées à la CI (`xcrun simctl launch ... -MiffForceRTL YES`, `-MiffShowHome YES`, `-MiffScrollTo gallery`, `-MiffAutoTest1 YES`) : `Diagnostic/DiagnosticLaunchOptions.swift`. Elles ne fonctionnent qu'en configuration **Debug** ; dans `Internal` (TestFlight) et `Release` elles sont inertes (aucun argument de lancement ne peut changer le comportement d'un build distribué).

**Accessibilité** : le résultat d'un test (apparu après un appui) est annoncé par VoiceOver (`UIAccessibility.post(.announcement)`), les lignes d'interrupteur sont une seule cible de 44 pt. **Non vérifié avec VoiceOver sur un iPhone.**

**Sécurité du diagnostic** : au démarrage, Firebase n'est configuré **que si** le `GoogleService-Info.plist` embarqué vise `miff-3d283` (sinon l'app ne parle à aucun projet : le diagnostic affiche « Projet non identifié »). Le mot de passe du test 2 n'est vidé qu'une fois la saisie validée localement (une adresse mal tapée ne force pas à retaper le mot de passe).

## CI

- `.github/workflows/ios.yml` : sur les PR qui touchent `ios/**` (**sauf** les fichiers `.md` et `ios/third_party/**`) ou les fichiers de thème Android (`android/.../ui/theme/**`, comparés par `ColorTokensTests`) : XcodeGen, résolution des paquets (cache), SwiftLint (et auto-test de ses règles), compilation et tests sur simulateur **sans signature** (un test ignoré fait échouer le job), contrôles du binaire Debug (avec témoin positif : le diagnostic et le fournisseur de débogage **doivent** y être trouvés). Les contrôles **lourds**, seulement si `Views`, `Diagnostic`, `Resources`, `DesignSystem`, `Localization`, `App`, `project.yml`, `Config`, `ci` ou le workflow changent : compilation **Release** (aucune trace du diagnostic), compilation **Internal** pour appareil (diagnostic présent, **pas** de fournisseur de débogage, Info.plist cohérent), captures d'écran (artefact `ios-screenshots-<n>`). Durées dans le résumé du job, délai maximal 30 min (relevé le 2026-10-30 : 15 min ne suffisaient plus sur les runners du dépôt public). Les contrôles s'exécutent tous même si l'un échoue ; l'étape « Verdict » fait échouer le job à la fin.
- `.github/workflows/ios-testflight.yml` : **manuel** : archive (clé d'API App Store Connect) et envoi à TestFlight. Recrée aussi `GoogleService-Info.plist` depuis le secret, applique `Package.resolved` (`-onlyUsePackageVersionsFromResolvedFile`) et le supprime en fin de job. Après l'archive : contrôle bloquant de l'identité, **puis du contenu** (Info.plist, projet Firebase embarqué, diagnostic présent, pas de fournisseur de débogage, entitlement App Attest quand il est lisible). **Jamais exécuté** (voir risques ci-dessus).
- **Symboles de plantage (Crashlytics)** : le script de phase de compilation (`ci/crashlytics-upload-symbols.sh`) **ne fait rien** en CI ou quand la clé App Store Connect est dans l'environnement : il lancerait du code tiers dans un processus qui peut lire la clé `.p8` (mode `signed`). L'envoi des dSYM se fait dans une **étape séparée sans secret** d'`ios-testflight` (la clé y est supprimée avant), jamais bloquante. **Non testé** (workflow jamais exécuté).
- Captures produites (simulateur iPhone, build Debug) : français, anglais, langue non prise en charge (doit afficher l'anglais), pseudo-langue « double longueur », droite-à-gauche (mise en page forcée), écran d'accueil, galerie, confidentialité, mode sombre du système, très grande taille de texte, et le résultat du test 1.
- **Paquets Swift** : `Package.resolved` est versionné (`ios/Package.resolved`) et copié dans le projet généré à chaque exécution. Si `project.yml` change de version sans mise à jour du fichier, le job échoue et fournit le bon fichier en artefact (`ios-resolved-<n>`). Dependabot (`swift`, dossier `/ios`) est déclaré mais **peut ne rien détecter** (projet Xcode non versionné) : voir `.github/dependabot.yml` (laissé tel quel volontairement ; à revoir si aucune PR n'arrive).
- **Données locales** : cache Firestore iOS (persistance hors ligne), sauvegardes de l'appareil : **à décider avec le contrat de purge** (DUR-3). Rien n'est purgé ni exclu des sauvegardes dans ce socle.
- Durées mesurées : voir `CHANGELOG.md` ; `docs/dev/ios-spike.md` explique le quota de minutes macOS.

## Règles

- Aucun secret dans le dépôt : clé `.p8`, configuration Firebase, jeton App Check de débogage vivent dans les secrets GitHub ou hors du dépôt (`.gitignore`, job `hygiene`).
- Aucune permission, aucune exception ATS, iPhone uniquement (`TARGETED_DEVICE_FAMILY` posé au niveau de la cible).
- Aucune API plus récente qu'iOS 16 sans garde de disponibilité (les simulateurs de la CI sont tous en iOS 26 : le compilateur détecte les API absentes, pas les différences de comportement). Aucune API plus récente n'est utilisée dans ce socle (`NavigationStack`, `ViewThatFits`, `scrollDismissesKeyboard` sont iOS 16).

## Dépendances tierces (inventaire, versions de `Package.resolved`)

Seule dépendance déclarée : **firebase-ios-sdk 13.1.1** (version exacte ; produits `FirebaseCore`, `FirebaseFirestore`, `FirebaseAuth`, `FirebaseAppCheck`, `FirebaseCrashlytics`, `FirebaseAnalyticsCore`). Les 12 autres sont des dépendances indirectes imposées par ce SDK. Licences d'après les dépôts amont (non relues fichier par fichier ici).

| Dépendance | Version | Licence |
|---|---|---|
| firebase-ios-sdk | 13.1.1 | Apache-2.0 |
| app-check (Google) | 12.0.0 | Apache-2.0 |
| googleappmeasurement (binaire, utilisé via `GoogleAppMeasurementCore`) | 13.0.1 | conditions de service de Google (binaire) |
| google-ads-on-device-conversion-ios-sdk (résolu parce que GoogleAppMeasurement le déclare ; **utilisation par FirebaseAnalyticsCore non vérifiée**) | 3.7.0 | conditions de service de Google (binaire) |
| googledatatransport | 10.1.1 | Apache-2.0 |
| googleutilities | 8.1.5 | Apache-2.0 |
| gtm-session-fetcher | 5.3.1 | Apache-2.0 |
| interop-ios-for-google-sdks | 101.0.0 | Apache-2.0 |
| promises | 2.4.1 | Apache-2.0 |
| nanopb (fork Firebase) | 2.30910.1 | zlib |
| leveldb (fork Firebase) | 1.22.5 | BSD-3-Clause |
| abseil-cpp-binary | 1.2025051202.0 | Apache-2.0 |
| grpc-binary | 1.83.1 | Apache-2.0 |

Outils de build (pas dans l'app) : XcodeGen 2.46.0 (MIT), SwiftLint 0.65.1 (MIT). Pas de CocoaPods.
