# Miff Android

Application Android native (Kotlin + Jetpack Compose) de Miff. Ce dossier contient le **socle technique** (ticket INIT-2) : projet, design system en code, traductions, Firebase, écran de diagnostic interne. Aucune fonctionnalité visible par un utilisateur final n'existe encore.

> **ICONE ET LOGO PROVISOIRES : NE PAS PUBLIER.**
> Le logo n'a pas encore été fourni par le PM. L'icône actuelle (M blanc + coeur jaune-vert sur fond bleu `#043cd1`, avec couche monochrome Android 13+) est un dessin vectoriel simple fait pour le socle. Fichiers à remplacer dès que le vrai logo arrive : `app/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `app/src/main/res/values/colors.xml` (`ic_launcher_background`). L'écran d'accueil réutilise le même dessin. Une icône 512 x 512 pour la fiche Play reste à préparer avec le vrai logo.

## Identité technique

| | |
|---|---|
| Identifiant d'application | `com.tomessa.miff` (définitif une fois publié) |
| `minSdk` / `targetSdk` / `compileSdk` | 26 (Android 8.0) / 36 / **37** (voir "Ecarts") |
| Langage / UI | Kotlin 2.4.21, Jetpack Compose (BOM 2026.09.00), Material 3 |
| Build | Gradle 9.6.1 (wrapper, somme SHA-256 vérifiée), Android Gradle Plugin 9.4.1, JDK 17 |
| Firebase | projet `miff-3d283` (BoM 35.1.0) : Firestore, Authentication, App Check, Crashlytics, Analytics |
| Architecture | MVVM : `ViewModel` + `StateFlow`, Navigation Compose (routes typées), conteneur d'injection manuel (`AppContainer`, pas de Hilt/KSP : build léger sur 8 Go) |

## Démarrer sur un poste Windows

Prérequis : Android Studio installé (il fournit le JDK 17 "JBR" et le SDK). Aucun Gradle global n'est nécessaire : le wrapper (`gradlew`) télécharge la bonne version.

1. **Générer la configuration Firebase** (une seule fois par poste). Le fichier `app/google-services.json` n'est **jamais commité** (il est dans `.gitignore`) :

   ```powershell
   cd android\app
   npx firebase-tools apps:sdkconfig ANDROID 1:29899146512:android:307ffd8ea71a4a37659195 --project miff-3d283 > google-services.json
   ```

   Cette commande lit seulement la configuration. Elle doit toujours porter `--project miff-3d283` (le projet `ghostrun-app` n'est jamais ciblé). Vérifier que le fichier commence par `{` : si le CLI a écrit des lignes de statut dedans, les retirer.
2. **Indiquer où est le SDK** : Android Studio crée `local.properties` tout seul à l'ouverture du projet. En ligne de commande, créer `android/local.properties` (ignoré par git) avec : `sdk.dir=C\:/Users/<vous>/AppData/Local/Android/Sdk`.
3. **Construire** (PowerShell, depuis `android/`) :

   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   .\gradlew assembleDebug
   ```

   Le premier build télécharge beaucoup de dépendances et le SDK Android 37 (environ 6 minutes sur le poste du projet) ; les suivants prennent 1 à 3 minutes. La mémoire du démon Gradle est limitée à 1,5 Go (`gradle.properties`).

### Commandes utiles

| Objectif | Commande |
|---|---|
| APK de test (avec écran de diagnostic) | `.\gradlew assembleDebug` puis `app\build\outputs\apk\debug\app-debug.apk` |
| Version "release" non signée (sans diagnostic) | `.\gradlew assembleRelease bundleRelease` |
| Tests unitaires | `.\gradlew testDebugUnitTest` |
| Android Lint | `.\gradlew lintDebug` |
| Format (ktlint via Spotless) | `.\gradlew spotlessCheck` (vérifier) / `.\gradlew spotlessApply` (corriger) |

### Installer l'APK de test sur un téléphone (par exemple l'OPPO Find X5 Lite)

1. Copier `app-debug.apk` sur le téléphone (câble USB, ou envoi à soi-même).
2. L'ouvrir depuis l'application Fichiers. Android demande d'autoriser l'installation depuis cette source : accepter pour cette fois.
3. Alternative avec câble : activer les options pour les développeurs et le débogage USB, puis `adb install -r app-debug.apk` (ou le bouton "Run" d'Android Studio).

L'APK de test fait environ 20 Mo (non réduit, avec les outils de débogage) ; la version release fait environ 2,7 Mo.

## Variantes de build

| | `debug` | `release` |
|---|---|---|
| Ecran affiché au démarrage | **Diagnostic interne** (peut ouvrir l'écran d'accueil) | Ecran d'accueil minimal avec le logo provisoire |
| Code du diagnostic | présent (`src/debug`) | **absent** (preuve : aucune chaîne `Diagnostic`, `DIAGNOSTICPROBE` ni `DebugAppCheck` dans le dex de l'APK release) |
| App Check | fournisseur "debug" | Play Integrity (mode non appliqué) |
| Réduction du code (R8) | non | oui, avec réduction des ressources |
| Signature | automatique (clé de débogage locale) | **non signée** : aucune clé de signature n'existe encore (ne jamais en commiter) |
| Pseudo-langues `en-XA`, `ar-XB` | oui | non |

## Ecran de diagnostic (debug uniquement)

C'est la preuve que l'app parle au bon projet Firebase, avec des règles qui protègent les données. Il ne fait **aucune écriture**.

1. **Version installée** : version, build, configuration, projet Firebase (doit être `miff-3d283`, sinon avertissement), langue, sens du texte.
2. **Test 1** : lecture d'une Miff sans être connecté. Résultat attendu : refus = "Règles actives : OK". Si la lecture réussit : avertissement clair (règles ouvertes). Ce test déconnecte d'abord l'éventuelle session.
3. **Test 2** : saisir e-mail et mot de passe du compte de test (créé à la main dans la console Firebase). L'app se connecte, lit `users/{uid}` et `entitlements/{uid}` ("Pas encore de profil" et "Offre : Classic" sont des résultats normaux), puis se déconnecte. Le mot de passe n'est jamais conservé ni écrit dans le code ; l'e-mail saisi survit à une rotation de l'écran (état sauvegardé de l'app), pas le mot de passe.
4. **App Check** : indique si un jeton est obtenu (non exigé pour l'instant). En debug, le SDK affiche un jeton de débogage dans Logcat (étiquette `DebugAppCheckProvider`) ; il peut être enregistré dans la console Firebase (App Check > Gérer les jetons de débogage). Ce jeton reste privé : ne jamais le commiter ni le coller dans une conversation.
5. **Analytics et rapports de plantage** : deux interrupteurs éteints par défaut, état conservé après redémarrage, un événement de test, un bouton qui tente d'envoyer une position (doit être refusé) et un bouton de plantage de test (actif seulement si les rapports sont activés).
6. **Galerie de tokens** : couleurs (nom de code et valeur), styles de texte, arrondis, ombres, dégradés, à comparer au Figma.
7. **Langue** : phrase longue, texte court et pluriel pour voir allongement et droite-à-gauche.

Les messages d'erreur restent neutres ("Pas de connexion pour le moment"), y compris hors-ligne (mode avion).

## Confidentialité : Analytics et Crashlytics

- Le manifeste désactive la collecte au démarrage (`firebase_analytics_collection_enabled=false`, `firebase_crashlytics_collection_enabled=false`, identifiant publicitaire désactivé). La clé irréversible `…_collection_deactivated` n'est **pas** utilisée.
- Le choix de l'utilisateur est stocké sur l'appareil (`PrivacySettings`) et réappliqué à chaque lancement. Pour l'instant le seul moyen d'allumer ces collectes est l'écran de diagnostic ; l'écran de consentement viendra avec l'onboarding.
- Tout événement passe par `AnalyticsTracker` (point d'entrée typé : `AnalyticsEvent`), qui applique la **liste blanche** de `docs/contracts/analytics-events.md` (version 1, neuf événements, valeurs issues de listes fermées) implémentée dans `domain/analytics/AnalyticsContract.kt`. Sont refusés : événement inconnu, paramètre inconnu, mauvais type, valeur hors liste et **tout nom de paramètre qui évoque une position** (mots `lat`, `lng`, `address`, `place`, `location`, `gps`, `distance`... découpés sur `_`, ou fragments comme `latitude` même collés). Ajouter un événement = PR sur le contrat **et** sur les deux plateformes, validée par le PM.
- Les permissions publicitaires ajoutées par le SDK Analytics (`AD_ID`, `ACCESS_ADSERVICES_*`, référent d'installation) sont retirées du manifeste.
- **Ce que la liste blanche ne couvre pas** : une fois Analytics activé, le SDK Firebase envoie lui-même des événements automatiques (`first_open`, `session_start`, `user_engagement`...) avec des informations techniques sur l'appareil, et Google déduit une **zone géographique approximative à partir de l'adresse IP**. Ce n'est pas une position précise (l'app n'a aucune permission de localisation), mais ce n'est pas « aucune information de localisation » : à dire dans la politique de confidentialité. Le suivi automatique des écrans (`screen_view`) est coupé dans le manifeste (`google_analytics_automatic_screen_reporting_enabled=false`). **Action du PM** : régler la collecte des données géographiques dans la console GA4 (détail dans `docs/contracts/analytics-events.md`).
- L'événement de test `analytics_diagnostic_test` n'existe que dans les builds debug : il est retiré de la liste blanche en release.

## Sécurité du manifeste (critère A7)

Permissions finales de l'APK release : `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK` (ajoutée par Firebase pour ses envois en arrière-plan), `READ_GSERVICES` (Google Play services) et une permission interne `com.tomessa.miff.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (AndroidX). Aucune ne déclenche de demande à l'utilisateur ; **aucune permission de localisation, notification, stockage, contacts, caméra**. `allowBackup=false` (+ règles d'extraction vides pour Android 12+), trafic en clair interdit (`networkSecurityConfig` sans exception), `debuggable` uniquement en debug.

## Traductions

- **Les textes anglais sont dans `res/values/` (non qualifié) et les textes français dans `res/values-fr/`.** C'est voulu : Android utilise le dossier non qualifié quand la langue du téléphone n'est pas prise en charge, donc c'est le seul moyen d'avoir l'**anglais comme langue de repli** (un téléphone en allemand ou en arabe affiche de l'anglais). `res/resources.properties` (`unqualifiedResLocale=en-US`) déclare cette langue de référence, et `generateLocaleConfig = true` produit la liste des langues du sélecteur Android 13+ ("Langues de l'application").
- Le français reste la **langue source** du texte produit. Les clés suivent `docs/contracts/i18n-keys.md` : `snake_case` avec préfixe de fonctionnalité (`home_`, `diag_`...), suffixe `_a11y` pour un texte de lecteur d'écran, `android_` pour ce qui est propre à Android. `res/values/` (anglais) et `res/values-fr/` (français) ont exactement les mêmes clés (Lint l'impose) : le script de parité du dépôt, qui compare des clés, fonctionne sur `res/values/strings.xml`, même si ce fichier contient l'anglais et non le français.
- Ajouter une langue : créer `res/values-<code>/strings.xml` avec toutes les clés (Lint échoue sinon : `MissingTranslation` en erreur), puis ajouter le code dans `localeFilters` (`app/build.gradle.kts`, bloc `androidComponents`).
- Lint en erreur : `HardcodedText`, `MissingTranslation`, `ExtraTranslation`, `RtlHardcoded`. Limite connue : `HardcodedText` ne regarde que les fichiers XML ; les textes écrits directement dans le code Compose ne sont pas détectés par Lint (aucun dans ce socle, à surveiller en relecture).
- Mise en page avec début/fin (`start`/`end`), jamais gauche/droite. Pseudo-langues `en-XA` (texte allongé accentué) et `ar-XB` (droite-à-gauche) disponibles dans le build debug : les ajouter dans les langues du téléphone (elles n'apparaissent que si les options pour les développeurs sont activées, selon le constructeur).
- Ni Baloo Thambi 2 ni Hanken Grotesk ne contiennent de glyphes arabes, chinois, japonais, coréens, devanagari ou grecs : le système utilise sa police de repli.

## Design system en code

`app/src/main/java/com/tomessa/miff/ui/theme/` : `Color.kt`, `Type.kt`, `Shape.kt`, `Shadow.kt`, `Gradient.kt`, `Spacing.kt`, `Theme.kt`.

- Couleurs : noms de code de `design/design-system.md` (`colorBrandPrimary`, `colorActionPrimary`, `colorStatusSafe`...). Les noms non listés dans le document suivent le même schéma (`bg/canvas` devient `colorBgCanvas`) : à aligner à l'identique sur iOS.
- Styles de texte : `textDisplayHero`, `textDisplayHeadline`, `textBodyRegular`, `textCaptionTag8`... (token `body/regular-14` devient `textBodyRegular14`). Tailles en `sp`.
- Arrondis `RadiusMd`, `RadiusSm`, `RadiusFull` ; ombres `ShadowDefault`, `ShadowSubtle` (voir limite ci-dessous) ; dégradés `GradientBrandTitle`, `GradientAuthBackground`.
- Texte d'erreur : `colorTextError` est `#D60700` (et non le rouge de statut `#FF5650`) pour atteindre un contraste d'au moins 4,5:1 sur les fonds de l'app ; `colorStatusDanger` reste réservé aux anneaux, badges et fonds. Un test unitaire (`ColorContrastTest`) calcule le contraste.
- Thème **clair imposé** (le mode sombre du téléphone et le "mode sombre forcé" des surcouches constructeur sont ignorés).
- Polices variables embarquées dans `res/font` avec leurs licences OFL (`third_party/fonts/`). Une police variable contient toutes les graisses : on ne peut pas n'embarquer que les graisses utilisées sans outil de découpage de polices (non disponible ici). Poids total des trois fichiers : environ 580 Ko.
- Points ouverts de design (non inventés, signalés) : poids de Baloo Thambi 2 (le fichier d'état Figma dit "Regular", c'est ce qui est utilisé) ; "dégradé à 17 %" lu comme position de l'arrêt de couleur ; les espacements (`Spacing.kt`) ne sont pas des tokens Figma.
- Limite de plateforme : l'ombre se rend par une élévation Android, qui ne reproduit pas exactement décalage et flou de Figma. Le PM valide le rendu sur capture.

## Structure

```
app/src/main      code commun : MiffApplication, AppContainer, MainActivity,
                  ui/ (theme, components, home, navigation), domain/ (logique pure testée), data/privacy
app/src/debug     écran de diagnostic, galerie de tokens, accès Firestore/Auth en lecture, App Check "debug"
app/src/release   App Check Play Integrity, aucune destination interne
app/src/test      tests unitaires JUnit (46 tests, dont app/src/testDebug pour le ViewModel du diagnostic)
third_party/fonts licences et provenance des polices
```

## Limites connues

- **Le cache Firestore local n'est pas purgé à la déconnexion.** Il peut contenir des états de position. Aucun écran de connexion n'existe encore ; la purge (et son test) est à traiter dans le ticket Inscription/Connexion, comme prévu au ticket INIT. L'écran de diagnostic se déconnecte seulement.
- Accessibilité : les interrupteurs de l'écran de diagnostic sont des éléments uniques (rôle interrupteur, libellé lu par TalkBack, zone d'au moins 48 dp). A valider au lecteur d'écran sur l'OPPO.

## Intégration continue

Le workflow vérifie aussi que le bundle release ne contient aucune trace de `DIAGNOSTICPROBE` ni de `DebugAppCheck` (diagnostic ou fournisseur App Check de debug) et échoue sinon. **L'essai du build release sur un appareil (OPPO) n'est pas fait par la CI : il se fait à la recette** (installer l'APK release signé localement ou le bundle via un canal de test).

`.github/workflows/android.yml` (déclenché par une PR touchant `android/**`, ou à la main) exécute `spotlessCheck`, `lintDebug`, `testDebugUnitTest`, `assembleDebug` et `bundleRelease` (non signé), et publie l'APK debug 3 jours. Il lit le secret GitHub `GOOGLE_SERVICES_JSON_B64` (voir le message d'erreur du job s'il manque).

## A surveiller plus tard : surcouches constructeur (géofencing)

Les surcouches comme ColorOS (OPPO) ferment souvent les applications en arrière-plan pour économiser la batterie. Ce n'est pas un problème pour ce socle (aucun géofencing), mais le suivi automatique des lieux de confiance y sera sensible : à tester sur l'OPPO Find X5 Lite dès que le géofencing existera, et à documenter pour l'utilisateur (autoriser le démarrage automatique et l'activité en arrière-plan, exclure Miff de l'optimisation de la batterie). Rien n'est implémenté ici.

## Choix économiques et signaux de bascule

- Firebase au plan gratuit **Spark** : Firestore, Auth, App Check, Crashlytics et Analytics sans coût à ce stade. Limite : 50 000 lectures par jour pour Firestore ; l'écran de diagnostic fait quelques lectures. Signal de bascule : suivre la page Firestore > Utilisation (critère P5).
- Pas d'envoi automatique du fichier de correspondance R8 vers Crashlytics depuis un poste de développement (un build ne doit jamais écrire dans le projet Firebase). Pour l'activer en CI : `-Pmiff.uploadCrashlyticsMapping=true`. Tant que ce n'est pas fait, les rapports de plantage d'un build release sont difficiles à lire.
- Aucun service payant, aucune dépendance payante.

## Ecarts par rapport au ticket

- **`compileSdk` = 37 au lieu de 36** : les versions actuelles d'AndroidX (Compose 1.12, Navigation 2.10, Core 1.19) exigent de compiler contre l'API 37. `compileSdk` n'agit qu'à la compilation ; le comportement à l'exécution est fixé par `targetSdk`, **resté à 36** (exigence Google Play). L'alternative serait de rester sur d'anciennes versions des bibliothèques.
- **Anglais dans `res/values/`, français dans `res/values-fr/`** (et non l'inverse) pour obtenir le repli anglais, voir "Traductions".
- **AppCompat non ajouté** : la rétro-compatibilité de la langue par application sous Android 13 n'est utile que s'il y a un sélecteur de langue dans l'app ; il n'y en a pas (le sélecteur système suffit sur Android 13+). A ajouter avec un sélecteur interne si besoin.
- Thème de base de la plateforme (`Theme.Material.Light.NoActionBar`) : la bibliothèque "Material Components" (vues) n'est pas utilisée, seulement Material 3 pour Compose.

## Inventaire des dépendances

Toutes gratuites. Versions résolues (BoM Compose 2026.09.00, BoM Firebase 35.1.0).

| Dépendance | Version | Licence |
|---|---|---|
| Kotlin (stdlib, plugin Compose, plugin sérialisation) | 2.4.21 | Apache 2.0 |
| AndroidX Core KTX | 1.19.1 | Apache 2.0 |
| AndroidX Activity Compose | 1.13.0 | Apache 2.0 |
| AndroidX Lifecycle (ViewModel Compose, runtime Compose) | 2.11.0 | Apache 2.0 |
| AndroidX Navigation Compose | 2.10.2 | Apache 2.0 |
| Compose UI, Foundation | 1.12.1 | Apache 2.0 |
| Compose Material 3 | 1.4.0 | Apache 2.0 |
| kotlinx-serialization-json | 1.12.0 | Apache 2.0 |
| kotlinx-coroutines-play-services | 1.11.0 | Apache 2.0 |
| Firebase Firestore | 27.1.0 | Apache 2.0 |
| Firebase Authentication | 25.0.0 | Android SDK License (Google) |
| Firebase Analytics | 24.0.0 | Android SDK License (Google) |
| Firebase Crashlytics | 21.0.1 | Apache 2.0 |
| Firebase App Check (Play Integrity ; debug en debug seulement) | 20.0.0 | Apache 2.0 |
| Compose UI Tooling (debug seulement) | 1.12.1 | Apache 2.0 |
| JUnit (tests) | 4.13.2 | EPL 1.0 |
| kotlinx-coroutines-test (tests) | 1.11.0 | Apache 2.0 |
| Plugin Android Gradle | 9.4.1 | Apache 2.0 |
| Plugin google-services | 4.5.0 | Apache 2.0 |
| Plugin Firebase Crashlytics | 3.0.8 | Apache 2.0 |
| Plugin Spotless (ktlint 1.8.0) | 8.10.4 | Apache 2.0 (ktlint : MIT) |
| Polices Baloo Thambi 2 et Hanken Grotesk | google/fonts, commit `45e3d52` | SIL OFL 1.1 |

Dependabot (ticket INIT-1) proposera les mises à jour par PR. Comme Dependabot n'a pas accès aux secrets GitHub, le workflow `android.yml` génère pour lui, et pour lui seul, un `google-services.json` factice (projet `fake-ci-config-do-not-use`, clé évidemment fausse) afin de compiler ; l'APK de ces exécutions n'est pas publié.

## Documentation liée

`CHANGELOG.md` (historique), `docs/tickets/initialisation-technique.md` (ticket et critères A1 à A14), `design/design-system.md`, `backend/data-model.md`.
