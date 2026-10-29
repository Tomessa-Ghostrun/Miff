# Miff Android

Application Android native (Kotlin + Jetpack Compose) de Miff. Ce dossier contient le **socle technique** (ticket INIT-2) et l'**inscription / connexion** (ticket AUTH-1) : projet, design system en code, traductions, Firebase, écran de diagnostic interne, écrans d'authentification.

> **Logo et icône OFFICIELS (PM, validés le 2026-10-27).** Source de vérité : `design/assets/logo/` (recréation vectorielle des deux visuels fournis par le PM ; le PM doit encore confirmer ses droits sur le dessin avant publication). Dans l'app : icône adaptative (`mipmap-anydpi-v26/ic_launcher*.xml` : fond `ic_launcher_background` = `#043CD1`, avant-plan `drawable/ic_launcher_foreground.xml`, calque monochrome `ic_launcher_monochrome.xml`) et mot-symbole `drawable/miff_wordmark_color.xml` sur l'écran d'accueil (description accessible "Logo de Miff"). Les couleurs de ces dessins sont des copies des tokens (`colorActionPrimary`, `colorBrandPrimary`, `colorTextOnAction`) : `LogoColorsTest` échoue si elles divergent. L'icône 512 x 512 de la fiche Google Play est dans `app/src/main/play/` (hors ressources : jamais embarquée dans l'APK). Écran de lancement : voir « Écran de lancement animé ».

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
| Ecran affiché au démarrage | **Diagnostic interne** (peut ouvrir l'écran d'accueil) | Ecran d'accueil minimal avec le mot-symbole officiel |
| Code du diagnostic | présent (`src/debug`) | **absent** (preuve : aucune chaîne `Diagnostic`, `DIAGNOSTICPROBE` ni `DebugAppCheck` dans le dex de l'APK release) |
| App Check | fournisseur "debug" | Play Integrity (mode non appliqué) |
| Réduction du code (R8) | non | oui, avec réduction des ressources |
| Signature | clé de débogage : celle du poste, ou celle du secret `DEBUG_KEYSTORE_B64` en CI (voir « Authentification ») | **non signée** : aucune clé de signature n'existe encore (ne jamais en commiter) |
| Pseudo-langues `en-XA`, `ar-XB` | oui | non |

## Ecran de diagnostic (debug uniquement)

C'est la preuve que l'app parle au bon projet Firebase, avec des règles qui protègent les données. Il ne fait **aucune écriture**.

1. **Version installée** : version, build, configuration, projet Firebase (doit être `miff-3d283`, sinon avertissement), langue, sens du texte.
2. **Test 1** : lecture d'une Miff sans être connecté. Résultat attendu : refus = "Règles actives : OK". Si la lecture réussit : avertissement clair (règles ouvertes). Ce test déconnecte d'abord l'éventuelle session.
3. **Test 2** : saisir e-mail et mot de passe du compte de test (créé à la main dans la console Firebase). L'app se connecte, lit `users/{uid}` et `entitlements/{uid}` ("Pas encore de profil" et "Offre : Classic" sont des résultats normaux), puis se déconnecte. Le mot de passe n'est jamais conservé ni écrit dans le code ; l'e-mail saisi survit à une rotation de l'écran (état sauvegardé de l'app), pas le mot de passe.
4. **App Check** : indique si un jeton est obtenu (non exigé pour l'instant). En debug, le SDK affiche un jeton de débogage dans Logcat (étiquette `DebugAppCheckProvider`) ; il peut être enregistré dans la console Firebase (App Check > Gérer les jetons de débogage). Ce jeton reste privé : ne jamais le commiter ni le coller dans une conversation.
5. **Analytics et rapports de plantage** : deux interrupteurs éteints par défaut (remis à zéro à chaque démarrage : la collecte ne suit plus que le choix du compte, voir « Authentification »), un événement de test, un bouton qui tente d'envoyer une position (doit être refusé) et un bouton de plantage de test (actif seulement si les rapports sont activés).
6. **Galerie de tokens** : couleurs (nom de code et valeur), styles de texte, arrondis, ombres, dégradés, à comparer au Figma.
7. **Langue** : phrase longue, texte court et pluriel pour voir allongement et droite-à-gauche.

Les messages d'erreur restent neutres ("Pas de connexion pour le moment"), y compris hors-ligne (mode avion).

## Authentification (ticket AUTH-1)

Code : `domain/auth` (règles pures testées), `data/auth` (Firebase Auth, lectures Firestore, purge, Credential Manager), `data/session/SessionManager` (un seul responsable de « qui est connecté et où s'ouvre l'app »), `ui/auth` (écrans et view models), `ui/navigation/AppRoot`.

- **Parcours** : carrousel de bienvenue (première ouverture seulement), puis inscription (e-mail + mot de passe ou Google) et connexion. **Aucune case à cocher** (décision PM du 2026-10-28, risque légal et stores assumé par lui) : une ligne explicative juste au-dessus du bouton « Rejoindre » (liens vers les Conditions d'utilisation et la Politique de confidentialité) dit que le clic accepte les conditions, la politique et le partage de statistiques anonymes (modifiable dans les Réglages). Le clic est l'acceptation de (a) et de (b) : à la création du profil, `analyticsConsent.enabled = true` (`ConsentChoice.IMPLICIT`). Une personne nouvelle qui arrive par « Continuer avec Google » depuis la connexion voit un **panneau** (même ligne, boutons « Continuer » / « Annuler ») ; un refus (bouton, retour système) **supprime le compte tout juste créé**, purge le cache et n'envoie aucun événement.
- **Routage** (`resolveDestination`) : lu dans l'état du compte, jamais déduit du fait qu'on vient de se connecter : pas de session -> inscription (ou connexion si l'app a déjà servi) ; session sans profil -> panneau si (a) n'est pas enregistrée sur l'appareil, sinon profil ; profil sans Miff -> choix créer/rejoindre ; sinon fil. Les écrans profil, choix de Miff et fil sont **provisoires** (écran d'attente avec un bouton « Se déconnecter », à remplacer par le ticket Onboarding).
- **Profil** : `ProfileDocument.forCreation` fabrique la **seule écriture** de `users/{uid}` (dates serveur, `analyticsConsent` toujours renseigné) et `UserProfileRepository.create` l'envoie ; l'écran de profil (ticket Onboarding, qui ajoutera aussi le téléphone une fois les règles étendues) les appelle. L'authentification elle-même n'écrit rien dans Firestore.
- **Consentement et collecte** :$1Au clic sur « Rejoindre » (ou « Continuer »), (b) étant donnée, ils s'allument localement et `sign_up_completed` part après la création du compte (ticket §8, décision PM Q8 ; `backend/data-model.md` §8 a été aligné). `sign_in_completed` ne part qu'après lecture d'un choix « accepté ». Versions des textes : `ConsentVersions` (CGU `0.1`, case (b) `2026-10-v01`).
- **Déconnexion** (`SessionManager.signOut`) : arrêt des écoutes (`SessionTeardown`, aucune pour l'instant), envoi des écritures en attente (5 s), `terminate()` + `clearPersistence()` avec drapeau « purge à faire » relu au démarrage, puis `signOut()`, collecte coupée, consentement en attente effacé.
- **Mot de passe** : jamais stocké, jamais dans un état d'écran, jamais journalisé (le test `NoSensitiveLoggingTest` interdit tout journal dans le code de l'app). Seul l'e-mail survit à une rotation (`rememberSaveable`) ; le mot de passe est vidé par une rotation.
- **Un seul message à la connexion** pour « adresse inconnue » et « mot de passe faux », avec un délai plancher de 0,8 s sur cette réponse ; après 5 échecs de suite, pause de 30 s côté app (confort, pas une protection : la vraie protection est celle de Firebase et d'App Check).
- **Liens légaux** : `BuildConfig.LEGAL_BASE_URL` (par défaut `https://miff-3d283.web.app`, pages AUTH-W non déployées pour l'instant) + chemins par langue (`android_legal_*_path`). Modifiable par `-Pmiff.legalBaseUrl=...`. Contact provisoire : `BuildConfig.SUPPORT_EMAIL` (`-Pmiff.supportEmail=...`).
- **Google** : Credential Manager (« Sign in with Google ») ; il faut un client OAuth **web** dans `google-services.json` (ressource `default_web_client_id`, lue par son nom : `res/raw/keep.xml` la protège du réducteur de ressources du build release, sans quoi le bouton ne marcherait jamais en release) et la SHA-1 de la clé de signature enregistrée dans Firebase. Sans client web, le bouton affiche le message d'erreur neutre. Une fenêtre Google laissée ouverte plus de 3 minutes est refermée sans message.
- **Un compte par adresse e-mail (décision Q12)** : côté app, une adresse déjà liée à une autre méthode produit un message d'orientation sans code technique (`LINKED_TO_OTHER_METHOD` : « Un compte existe déjà avec cette adresse, avec un autre mode de connexion »), et une adresse déjà utilisée à l'inscription produit « Un compte existe déjà… Tu peux te connecter ». **Reste à tester sur un projet de développement (pas sur la production)** : le réglage de la console « un compte par adresse » ; ce que Firebase fait réellement quand une personne inscrite par e-mail choisit ensuite Google avec la même adresse (fusion automatique ? l'e-mail non vérifié est-il écrasé ?) ; l'analyse de sécurité dédiée demandée par le ticket (R6). Rien n'a été constaté sur un vrai projet dans ce ticket.
- **Bouton Google** : respecte les règles de marque (fond blanc, bordure `#747775` 1 dp, texte `#1F1F1F` Roboto Medium 14 sp, aucune ombre ajoutée). **À remplacer avant le dépôt sur Google Play** : le « G » (`res/drawable/ic_google_g.xml`) est un redessin, pas le fichier officiel ; à remplacer par l'asset des directives de marque de Google (https://developers.google.com/identity/branding-guidelines), puis revérifier espacement, taille et libellé sur la page de ces directives.
- **Debug seulement** : l'écran de diagnostic reste le premier écran d'un APK de debug ; le bouton « Ouvrir l'application » mène au vrai parcours.

### Fidélité au design (maquettes du PM, écrans « Welcoming screen -7 » à « -13 »)

Les écrans d'inscription et de connexion reprennent les maquettes : fond dégradé `gradient/auth-background`, flèche retour à gauche et mot-symbole officiel « Miff » centré en haut, bouton Google pleine largeur (arrondi 10), « Ou : » en gras centré, champs à **soulignement** dont le libellé est à la place du texte indicatif, erreurs en rouge sous le champ, bouton principal bleu (grisé à 30 % tant que les champs sont vides, libellé `body/large`), lien gras souligné « Déjà un compte ? » / « Pas encore de compte ? » sous le bouton, œil du mot de passe à droite (barré = visible). Polices Baloo Thambi 2 / Hanken Grotesk, couleurs et arrondis des tokens. Captures de contrôle générées par `AuthScreensTest` dans `app/build/screenshots/` (non commitées).

**Écarts conscients design / implémentation** (décisions du PM, du ticket, de la plateforme ou de l'accessibilité) :
1. Tutoiement partout (les maquettes vouvoient) ; « Continuer avec Google » (règles de marque) au lieu de « Rejoindre avec Google ».
2. **Plus de cases de consentement** (décision PM du 2026-10-28) : une ligne explicative juste au-dessus du bouton principal, avec deux liens dans la phrase (aussi proposés comme actions au lecteur d'écran) ; elle cite « Rejoindre » **et** « Continuer avec Google » (les deux créent un compte). Le bouton Google se trouve au-dessus de cette ligne, comme dans la maquette (le PM assume que le clic Google soit lu avant la ligne). Bouton principal « Rejoindre » comme la maquette.
3. Pas de bouton Apple sur Android (décision Q1).
4. Message d'erreur **unique** à la connexion, sous les champs, à la place de « Adresse email invalide » / « Mot de passe invalide » (ticket §7.3, décision Q3). Texte d'erreur en `#D60700` (AA) et non `#ea4335`.
5. Pas de lien « Mot de passe oublié ? » sous le mot de passe : il arrive avec AUTH-2.
6. Libellés des champs : flottants (ils remontent en petit au-dessus du texte saisi) au lieu de disparaître ; le lecteur d'écran garde ainsi le nom du champ. Couleur du libellé plus foncée que le gris des maquettes (contraste AA).
7. Bouton Google aux règles de marque de Google (Roboto Medium 14 sp, texte `#1F1F1F`, bordure `#747775`, **sans ombre**) et non en Ubuntu Bold avec l'ombre `shadow/subtle` des maquettes (`design-system.md` : composants officiels des plateformes).
8. Aucune phrase de titre visible (comme les maquettes) : le nom de l'écran est donné au lecteur d'écran (`paneTitle`). Le panneau de consentement, absent des maquettes, a un titre visible.
9. Boutons de 48 dp minimum ; ligne explicative en 14 sp (les 11 sp de la maquette sont trop petits).
10. Le mot de passe est vidé par une rotation de l'écran (déjà noté).

### Carrousel de bienvenue (maquettes « Welcoming screen », « -16 », « -17 »)

`ui/onboarding/WelcomeCarousel.kt` : 3 slides, points, titre à dégradé (« Savoir qu'ils vont bien, sans les déranger »), bouton « Commencer » (-> inscription), lien « J'ai déjà un compte » (décision Q8 du ticket Onboarding, -> connexion). Affiché **à la première ouverture seulement** (aucune session, app jamais utilisée, drapeau local `welcome_carousel_seen` non posé) ; le drapeau (non personnel, jamais envoyé) est posé à « Commencer » ou « J'ai déjà un compte ». Après une déconnexion : connexion directement (décision Q11). La flèche retour de l'inscription ramène au carrousel. Défilement manuel (balayage ou actions du lecteur d'écran), aucun défilement automatique, aucune animation d'entrée ; les points ne s'animent pas si « supprimer les animations » est actif ; « Page 1 sur 3 » est annoncé ; **aucun événement Analytics** (l'écran n'a pas accès au traceur). Le premier écran d'un APK de debug reste le diagnostic.

**Provisoire dans le carrousel** : les trois illustrations sont des dessins simples en Compose (carte de statut, fiche de lieu, trois messages) avec des avatars à initiales ; **aucune photo de personne** (les captures des maquettes sont de basse résolution et contiennent des photos de banque d'images à droits non vérifiés, donc non reprises). Le PM fournira de meilleurs exports. Les légendes des slides 2 et 3 sont des **propositions** reprises du ticket Onboarding (§6.11) et non validées ; le titre à dégradé est celui du PM, identique sur les 3 slides comme la maquette (le ticket recommandait un titre différent par slide : la légende sous l'illustration porte cette différence). Exemples d'illustration traduits (clés `onboarding_sample_*`).

**Point d'extension du splash** : `ui/startup/StartupSlot.kt`, appelé en tout premier par `AppRoot` (avant le carrousel et le routage) ; il se termine tout de suite tant que le splash animé (autre branche) n'existe pas. Ordre à conserver : splash -> (carrousel si première ouverture) -> connexion / routage.

### Signature de debug stable en CI (secret `DEBUG_KEYSTORE_B64`)

Le workflow décode le secret dans `~/.android/debug.keystore` du runner (alias `androiddebugkey`, mots de passe de debug standard), sans jamais l'afficher, et le supprime en fin de job. Il compare ensuite la SHA-1 de l'APK à celles de `google-services.json` et échoue si elle n'y est pas. Sans le secret (Dependabot, fork), la clé jetable par défaut reste utilisée. Création du keystore : voir « Signature de l'APK debug de la CI » plus bas. Le workflow échoue aussi, avec un message explicite, si `google-services.json` n'a pas de client OAuth web. Sur le poste, copier le même keystore dans `%USERPROFILE%\.android\debug.keystore` donne la même empreinte.

## Écran de lancement animé (splash)

Demande du PM après sa recette sur l'OPPO : « on voit le logo de l'app sans le cœur vert, le cœur vert apparaît et gonfle comme un ballon de baudruche jusqu'à éclater, puis l'app s'ouvre ». Ordre du démarrage : **écran de lancement système → animation Compose → app** (le carrousel de première ouverture, ticket Onboarding, fera partie de l'app : il n'apparaît qu'après le splash).

| Étape | Ce qui se passe | Où |
|---|---|---|
| Écran système (Android 12+ : API Splash Screen ; avant : rétro-portage `androidx.core:core-splashscreen`, minSdk 26 conservé) | Fond bleu `#043CD1` uni et « M + i » blanc **sans cœur** (`drawable/splash_logo_mark.xml`, copie du premier tracé de l'icône officielle, dessiné à 288 dp comme l'icône du splash Android 12) | thème `Theme.Miff.Starting`, `MainActivity.installSplashScreen()` |
| Animation (≈ 2,2 s au total, 2,5 s au plus) | 0 à 0,25 s : logo seul (relais de l'écran système) ; 0,25 à 0,6 s : le cœur apparaît sur le « i » ; 0,6 à 1,7 s : il gonfle jusqu'à 5 fois sa taille avec un léger élastique ; 1,7 à 2,0 s : pop net (disparition, 12 petits éclats verts qui s'éloignent) ; 2,0 à 2,2 s : fondu vers l'app | `domain/splash/SplashTimeline.kt` (fonction pure du temps, testée), `ui/splash/SplashHost.kt` (dessin) |
| « Réduire les animations » (`ANIMATOR_DURATION_SCALE` = 0) | Aucun mouvement : logo **complet** fixe 0,6 s puis fondu 0,2 s (0,8 s) | `ui/splash/ReduceMotion.kt` |

Règles tenues :
- **Une seule fois par démarrage à froid** (pas à la reprise depuis l'arrière-plan). Une rotation pendant l'animation la poursuit au lieu de la relancer.
- **Ne retarde jamais l'app** : l'app (donc la résolution de session, lancée dans `MiffApplication`) est composée *sous* l'animation dès la deuxième image ; le splash ne dépend ni du réseau ni de la session, et ne dépasse jamais sa durée. Si le premier écran n'est pas prêt à la fin du fondu, c'est son propre état de chargement qui s'affiche (écart assumé avec le ticket §5.1 point 3, qui prévoyait de garder le fond bleu : la consigne du PM prime, « jamais plus longtemps que prévu »).
- **Temps réel** (horloge d'images), pas `Animatable` : l'échelle d'animation des options développeur ne peut pas rallonger le splash.
- **Accessibilité** : décoratif, annoncé une seule fois « Miff » (TalkBack) ; l'app dessous est masquée du lecteur d'écran et les touches sont absorbées tant que l'animation dure ; aucun son, aucune vibration, aucun événement d'analyse ; rien ne clignote (le cœur apparaît une fois et disparaît une fois ; couleurs : bleu, blanc, vert de marque, ni éclair blanc ni rouge). Icônes de barres système claires pendant le splash, rétablies ensuite.
- **Couleurs** : tokens (`colorActionPrimary`, `colorBrandPrimary`, `colorTextOnAction`) ; les deux drawables du logo gardent leurs valeurs hexadécimales, contrôlées par `LogoColorsTest` et `SplashArtworkTest` (le cœur dessiné en Kotlin et le « M + i » sont identiques à l'icône officielle).
- **Coût** : aucune ressource externe, aucune bibliothèque d'animation ; une seule dépendance AndroidX (`core-splashscreen` 1.2.0, quelques dizaines de Ko).
- **Limite connue** : sur certaines surcouches constructeur (OPPO/ColorOS), l'écran de lancement système peut s'afficher différemment (icône de l'app au lieu du « M + i »). L'animation Compose reste identique ; à valider par le PM sur l'OPPO. Après une mort du processus par le système, l'app est restaurée sans rejouer le splash.
- Captures de plusieurs instants (test `SplashHostTest`, fichiers `app/build/splash-frames/`).

## Confidentialité : Analytics et Crashlytics

- Le manifeste désactive la collecte au démarrage (`firebase_analytics_collection_enabled=false`, `firebase_crashlytics_collection_enabled=false`, identifiant publicitaire désactivé). La clé irréversible `…_collection_deactivated` n'est **pas** utilisée.
- Le choix du compte (case (b), puis Réglages) commande la collecte ; l'appareil le garde pour la session en cours, mais **ne le réapplique pas au démarrage** : tout démarre coupé et ne se rallume qu'après la lecture de `users/{uid}`. L'écran de diagnostic (debug) reste un second moyen d'allumer ces collectes pendant une session.
- Tout événement passe par `AnalyticsTracker` (point d'entrée typé : `AnalyticsEvent`), qui applique la **liste blanche** de `docs/contracts/analytics-events.md` (version 1, neuf événements, valeurs issues de listes fermées) implémentée dans `domain/analytics/AnalyticsContract.kt`. Sont refusés : événement inconnu, paramètre inconnu, mauvais type, valeur hors liste et **tout nom de paramètre qui évoque une position** (mots `lat`, `lng`, `address`, `place`, `location`, `gps`, `distance`... découpés sur `_`, ou fragments comme `latitude` même collés). Ajouter un événement = PR sur le contrat **et** sur les deux plateformes, validée par le PM.
- Les permissions publicitaires ajoutées par le SDK Analytics (`AD_ID`, `ACCESS_ADSERVICES_*`, référent d'installation) sont retirées du manifeste.
- **Ce que la liste blanche ne couvre pas** : une fois Analytics activé, le SDK Firebase envoie lui-même des événements automatiques (`first_open`, `session_start`, `user_engagement`...) avec des informations techniques sur l'appareil, et Google déduit une **zone géographique approximative à partir de l'adresse IP**. Ce n'est pas une position précise (l'app n'a aucune permission de localisation), mais ce n'est pas « aucune information de localisation » : à dire dans la politique de confidentialité. Le suivi automatique des écrans (`screen_view`) est coupé dans le manifeste (`google_analytics_automatic_screen_reporting_enabled=false`). **Action du PM** : régler la collecte des données géographiques dans la console GA4 (détail dans `docs/contracts/analytics-events.md`).
- L'événement de test `analytics_diagnostic_test` n'existe que dans les builds debug : il est retiré de la liste blanche en release.

## Sécurité du manifeste (critère A7)

Permissions finales de l'APK release : `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK` (ajoutée par Firebase pour ses envois en arrière-plan), `READ_GSERVICES` (Google Play services) et une permission interne `com.tomessa.miff.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (AndroidX). `androidx.credentials` ajoute `USE_BIOMETRIC` et `USE_FINGERPRINT` (clés d'accès) : **retirées** du manifeste (`tools:node="remove"`) car Miff n'a ni clé d'accès ni invite biométrique (seule la fenêtre de compte Google est utilisée) ; elles ne sont pas nécessaires à « Sign in with Google », et une app de réassurance familiale qui déclare la biométrie attirerait une question inutile à la revue Play. Si une clé d'accès est ajoutée un jour, retirer ces deux lignes du manifeste. Aucune ne déclenche de demande à l'utilisateur ; **aucune permission de localisation, notification, stockage, contacts, caméra**. `allowBackup=false` (+ règles d'extraction vides pour Android 12+), trafic en clair interdit (`networkSecurityConfig` sans exception), `debuggable` uniquement en debug.

## Traductions

- **Les textes anglais sont dans `res/values/` (non qualifié) et les textes français dans `res/values-fr/`.** C'est voulu : Android utilise le dossier non qualifié quand la langue du téléphone n'est pas prise en charge, donc c'est le seul moyen d'avoir l'**anglais comme langue de repli** (un téléphone en allemand ou en arabe affiche de l'anglais). `res/resources.properties` (`unqualifiedResLocale=en-US`) déclare cette langue de référence, et `generateLocaleConfig = true` produit la liste des langues du sélecteur Android 13+ ("Langues de l'application").
- Le français reste la **langue source** du texte produit. Les clés suivent `docs/contracts/i18n-keys.md` : `snake_case` avec préfixe de fonctionnalité (`home_`, `diag_`...), suffixe `_a11y` pour un texte de lecteur d'écran, `android_` pour ce qui est propre à Android. `res/values/` (anglais) et `res/values-fr/` (français) ont exactement les mêmes clés (Lint l'impose) : le script de parité du dépôt (`scripts/check-i18n-parity.mjs`) lit le **français** dans `res/values-fr/strings.xml` et le compare au catalogue iOS ; `res/values/` (anglais) n'est pas lu par ce script, c'est Lint qui garantit que les deux dossiers ont les mêmes clés. Pour une clé nouvelle, c'est donc `values-fr/` que le script contrôle.
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
                  ui/ (theme, components, home, navigation, splash), domain/ (logique pure testée, dont splash), data/privacy
app/src/debug     écran de diagnostic, galerie de tokens, accès Firestore/Auth en lecture, App Check "debug"
app/src/release   App Check Play Integrity, aucune destination interne
app/src/test      tests unitaires JUnit (48 tests ; app/src/testDebug : ViewModel du diagnostic et test de défilement de l'écran d'accueil, via Robolectric)
third_party/fonts licences et provenance des polices
```

## Limites connues

- **Purge du cache Firestore** : faite à la déconnexion, au refus du panneau de consentement et à la perte de session (voir « Authentification »). Seules les déconnexions de l'écran de diagnostic (debug) ne purgent pas.
- Accessibilité : les interrupteurs de l'écran de diagnostic sont des éléments uniques (rôle interrupteur, libellé lu par TalkBack, zone d'au moins 48 dp). A valider au lecteur d'écran sur l'OPPO.

## Intégration continue

Le workflow vérifie aussi que le bundle release ne contient aucune trace de `DIAGNOSTICPROBE` ni de `DebugAppCheck` (diagnostic ou fournisseur App Check de debug) et échoue sinon. **L'essai du build release sur un appareil (OPPO) n'est pas fait par la CI : il se fait à la recette** (installer l'APK release signé localement ou le bundle via un canal de test).

`.github/workflows/android.yml` (déclenché par une PR touchant `android/**`, ou à la main) exécute `spotlessCheck`, `lintDebug`, `testDebugUnitTest`, `assembleDebug` et `bundleRelease` (non signé), et publie l'APK debug 3 jours. Il lit le secret GitHub `GOOGLE_SERVICES_JSON_B64` (voir le message d'erreur du job s'il manque).

Avant tout lancement de Gradle, l'étape `gradle/actions/wrapper-validation` (épinglée par empreinte de commit) vérifie que `gradle/wrapper/gradle-wrapper.jar` est un fichier officiel de Gradle : un jar de wrapper trafiqué s'exécuterait avec accès à la configuration Firebase du job. La somme SHA-256 de la distribution Gradle (`distributionSha256Sum`) reste vérifiée en plus, en local comme en CI.

### Signature de l'APK debug de la CI : situation et options (AND-002, option A implémentée par AUTH-1)

**Situation.** Le runner de la CI est jetable : à chaque exécution, Android Gradle Plugin y crée une clé de débogage neuve. Deux conséquences : (1) deux APK issus de deux exécutions (ou un APK de la CI puis un APK construit sur le poste) ne peuvent pas s'installer l'un par-dessus l'autre, il faut désinstaller d'abord ; (2) l'empreinte SHA-1 de l'APK change à chaque fois, alors que la connexion Google (ticket Inscription) exige une empreinte fixe déclarée dans Firebase.

**Une clé de débogage n'est jamais une clé de release.** Elle ne signe que des APK de test, jamais ce qui part sur Google Play. Elle ne doit jamais être réutilisée pour la release, et la clé de release (qui n'existe pas encore) ne doit jamais être mélangée avec elle. Dans les deux cas : **jamais dans le dépôt** (le script `check-forbidden-files` refuse les `.keystore` / `.jks`).

| Option | Principe | Coût | Résout (1) | Résout (2) | Limites |
|---|---|---|---|---|---|
| **A. Keystore de débogage partagé, secret GitHub** | Le PM génère une fois un keystore de débogage dédié à Miff, le stocke en base64 dans un secret GitHub `DEBUG_KEYSTORE_B64`. La CI le décode vers `~/.android/debug.keystore` avant `assembleDebug` (AGP l'utilise tout seul, aucun changement des fichiers Gradle). Le même fichier, copié sur le poste, donne la même empreinte en local | 0 EUR | oui, CI et poste | oui | Un secret de plus à protéger (peu sensible : il ne signe que des APK de test, mais quiconque l'a peut fabriquer un APK qui remplace le debug de ses testeurs). Les PR Dependabot et les forks n'ont pas accès aux secrets : la CI doit alors garder la clé jetable (étape à écrire avec cette condition). En cas de perte : on en recrée un et on met à jour l'empreinte dans Firebase, rien d'irréversible |
| **B. Chacun son keystore local, pas de clé en CI** | Le PM installe l'APK construit par Android Studio sur son poste ; la CI n'est utilisée que pour vérifier. Empreinte stable par poste, à déclarer dans Firebase | 0 EUR | oui, mais il faut un poste de build | oui, pour ce poste | Chaque poste a une empreinte différente (une par développeur à déclarer). Pas d'APK de la CI installable par-dessus. A éviter pour une équipe, acceptable à une personne |
| **C. Google Play, test interne + signature par Google (Play App Signing)** | L'AAB est envoyé dans un test interne ; Google signe avec une clé qu'il garde ; l'empreinte à déclarer dans Firebase est celle fournie par la console Play | 0 EUR (compte Play déjà payé) | oui | oui (empreinte Play) | Exige une vraie clé de téléversement (release) et une fiche Play : c'est l'étape suivante, lourde, qui concerne la distribution aux testeurs externes, pas le dépannage d'aujourd'hui. Le build debug ne passe pas par là |

(Firebase App Distribution ne règle pas le problème : il distribue un APK déjà signé, avec la clé qu'on lui donne.)

**Recommandation : option A**, tant que l'on teste sur l'OPPO avec des APK de la CI ; option C plus tard, au moment des testeurs externes (le projet de développement séparé prévu avant eux est le bon moment pour le décider). A est gratuite, ne change aucun fichier Gradle et résout les deux problèmes d'un coup.

**Etapes pour le PM (une fois, ~10 minutes), puis une PR de l'android-dev :**

1. Choisir un dossier **hors dépôt** (par exemple le dossier de secrets du poste) et y générer le keystore (PowerShell ; le JDK d'Android Studio fournit `keytool`). Les mots de passe `android` et l'alias `androiddebugkey` sont ceux que AGP attend pour une clé de débogage ; le fichier, lui, reste secret :

   ```powershell
   & "C:Program FilesAndroidAndroid Studiojbrinkeytool.exe" -genkeypair -keystore miff-debug.keystore -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Miff Debug,O=Miff,C=FR"
   ```
2. Lire l'empreinte (SHA-1 et SHA-256), à noter pour Firebase : `keytool -list -v -keystore miff-debug.keystore -alias androiddebugkey -storepass android`.
3. Créer le secret (même méthode que `GOOGLE_SERVICES_JSON_B64`) : `[Convert]::ToBase64String([IO.File]::ReadAllBytes("cheminmiff-debug.keystore")) | gh secret set DEBUG_KEYSTORE_B64 --repo Tomessa-Ghostrun/Miff`.
4. Sauvegarder le fichier dans un endroit privé (gestionnaire de mots de passe ou disque chiffré). Ne jamais le coller dans une conversation ni dans le dépôt.
5. Sur le poste, remplacer `%USERPROFILE%.androiddebug.keystore` par ce fichier (garder l'ancien à côté) : les builds locaux auront la même empreinte que la CI.
6. Au démarrage du ticket Inscription : déclarer le SHA-1 de l'étape 2 dans la console Firebase (Paramètres du projet > application Android > Ajouter une empreinte), puis régénérer `google-services.json` (et mettre à jour le secret `GOOGLE_SERVICES_JSON_B64`).
7. Dire à l'android-dev de passer à l'implémentation : étape de CI qui décode le secret (sans jamais l'afficher, sauté si le secret est absent), et ajout de `DEBUG_KEYSTORE_B64` à la liste fermée de `backend/tests/unit/workflows.test.js` (ce test impose qu'un nouveau secret soit une décision visible en revue). A la première installation, désinstaller une dernière fois l'ancienne version de l'application.

**Signal de bascule vers C** : premiers testeurs hors équipe, ou besoin de tester l'abonnement via Google Play (la facturation Play exige un APK/AAB distribué par Play).

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
| AndroidX Core SplashScreen | 1.2.0 | Apache 2.0 |
| AndroidX Activity Compose | 1.13.0 | Apache 2.0 |
| AndroidX Lifecycle (ViewModel Compose, runtime Compose) | 2.11.0 | Apache 2.0 |
| AndroidX Navigation Compose | 2.10.2 | Apache 2.0 |
| Compose UI, Foundation | 1.12.1 | Apache 2.0 |
| Compose Material 3 | 1.4.0 | Apache 2.0 |
| kotlinx-serialization-json | 1.12.0 | Apache 2.0 |
| kotlinx-coroutines-play-services | 1.11.0 | Apache 2.0 |
| Firebase Firestore | 27.1.0 | Apache 2.0 |
| Firebase Authentication | 25.0.0 | Android SDK License (Google) |
| AndroidX Credentials (+ play-services-auth) | 1.6.0 | Apache 2.0 |
| Google ID (googleid) | 1.2.1 | Android SDK License (Google) |
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
