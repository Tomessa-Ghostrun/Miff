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

- **Parcours** : carrousel de bienvenue (première ouverture seulement), puis inscription (e-mail + mot de passe ou Google) et connexion. **Aucune case à cocher** (décision PM du 2026-10-28, risque légal et stores assumé par lui) : une ligne explicative juste au-dessus du bouton « Rejoindre » (liens vers les Conditions d'utilisation et la Politique de confidentialité) dit que le clic accepte les conditions, la politique et le partage de statistiques anonymes (modifiable dans les Réglages). Le clic vaut acceptation des conditions, de la politique ET du partage de statistiques (un seul consentement) : à la création du profil, `analyticsConsent.enabled = true` (`ConsentChoice.IMPLICIT`). Une personne nouvelle qui arrive par « Continuer avec Google » depuis la connexion voit un **panneau** (même ligne, boutons « Continuer » / « Annuler ») ; un refus (bouton, retour système) **supprime le compte tout juste créé**, purge le cache et n'envoie aucun événement.
- **Routage** (`resolveDestination`) : lu dans l'état du compte, jamais déduit du fait qu'on vient de se connecter : pas de session -> inscription (ou connexion si l'app a déjà servi) ; session sans profil -> panneau si l'acceptation n'est pas enregistrée sur l'appareil, sinon profil ; profil sans Miff -> choix créer/rejoindre ; sinon fil. Les écrans profil, choix de Miff et fil sont **provisoires** (écran d'attente avec un bouton « Se déconnecter », à remplacer par le ticket Onboarding).
- **Profil** : `ProfileDocument.forCreation` fabrique la **seule écriture** de `users/{uid}` avec **toutes** les clés des règles (dont `phone` en E.164, **obligatoire depuis ONB-B** : sans lui le serveur refuse le profil) et `UserProfileRepository.create` l'envoie, depuis les deux étapes de l'Onboarding (voir « Profil et téléphone (ticket ONB-2) »). L'authentification elle-même n'écrit rien dans Firestore.
- **Consentement et collecte** : Analytics et Crashlytics sont coupés par le manifeste ET au démarrage (`PrivacySettings.pauseCollection`) : le choix mémorisé sur l'appareil n'est jamais réappliqué tel quel, c'est le choix du **compte** (`users/{uid}.analyticsConsent`, lu avant tout) qui décide. Il n'y a qu'**un seul consentement** pour les statistiques et les rapports de plantage (un seul bloc, pas deux interrupteurs). Au démarrage la collecte est seulement **mise en pause** : le rapport du plantage précédent reste sur le disque et part dès que le compte a répondu oui ; il n'est **supprimé** que s'il n'y a pas de consentement (retrait, refus du panneau, déconnexion, ou aucune session au démarrage). Au clic sur « Rejoindre » (ou « Continuer »), le consentement étant donné par ce clic, la collecte s'allume localement et `sign_up_completed` part après la création du compte (ticket §8, décision PM Q8 ; `backend/data-model.md` §8 a été aligné). `sign_in_completed` ne part qu'après lecture d'un choix « accepté ». Versions des textes : `ConsentVersions` (CGU `0.1`, ligne statistiques `2026-10-v01`).
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
5. Le lien « Mot de passe oublié ? » sous le mot de passe est arrivé avec AUTH-2 (voir la section suivante).
6. Libellés des champs : flottants (ils remontent en petit au-dessus du texte saisi) au lieu de disparaître ; le lecteur d'écran garde ainsi le nom du champ. Couleur du libellé plus foncée que le gris des maquettes (contraste AA).
7. Bouton Google aux règles de marque de Google (Roboto Medium 14 sp, texte `#1F1F1F`, bordure `#747775`, **sans ombre**) et non en Ubuntu Bold avec l'ombre `shadow/subtle` des maquettes (`design-system.md` : composants officiels des plateformes).
8. Aucune phrase de titre visible (comme les maquettes) : le nom de l'écran est donné au lecteur d'écran (`paneTitle`). Le panneau de consentement, absent des maquettes, a un titre visible.
9. Boutons de 48 dp minimum ; ligne explicative en 14 sp (les 11 sp de la maquette sont trop petits).
10. Le mot de passe est vidé par une rotation de l'écran (déjà noté).

### Carrousel de bienvenue (maquettes « Welcoming screen », « -16 », « -17 »)

`ui/onboarding/WelcomeCarousel.kt` : 3 slides, points, titre à dégradé (« Savoir qu'ils vont bien, sans les déranger »), bouton « Commencer » (-> inscription), lien « J'ai déjà un compte » (décision Q8 du ticket Onboarding, -> connexion). Affiché **à la première ouverture seulement** (aucune session, app jamais utilisée, drapeau local `welcome_carousel_seen` non posé) ; le drapeau (non personnel, jamais envoyé) est posé à « Commencer » ou « J'ai déjà un compte ». Après une déconnexion : connexion directement (décision Q11). La flèche retour de l'inscription ramène au carrousel. Défilement manuel (balayage ou actions du lecteur d'écran), aucun défilement automatique, aucune animation d'entrée ; les points ne s'animent pas si « supprimer les animations » est actif ; « Page 1 sur 3 » est annoncé ; **aucun événement Analytics** (l'écran n'a pas accès au traceur). Le premier écran d'un APK de debug reste le diagnostic.

**Fidélité aux maquettes (recette du PM, 2026-10-30)** : le carrousel reprend les maquettes de `design/exports/onboarding-original/` : le même titre à dégradé sur les trois slides (« Savoir qu’ils vont bien, / sans les déranger »), le mot-symbole, les formes vert pâle, trois points, le bouton « Commencer », et **aucune autre phrase** (les légendes inventées de la première version sont retirées ; leurs clés `onboarding_slide*_caption` restent dans les deux catalogues pour la parité avec iOS, inutilisées sur Android, à retirer des deux plateformes ensemble). **Les trois illustrations sont reconstruites en vectoriel et en vrais textes** (`ui/onboarding/WelcomeIllustrations.kt`), donc parfaitement nettes à toute densité (vérifié par captures en mdpi et xxxhdpi, et en très grande police) : slide 1 l’écran Feed (sélecteur « Famille Alberta », bulle « Tout le monde est en sécurité », cartes de Thomas et d’Amandine avec pastille de lieu, batterie, icônes localiser / message / appeler), slide 2 l’écran « Nouveau lieu » (Nom, Adresse, Périmètre avec carte et curseur, Type de zone), slide 3 les trois cartes de notification. Polices Baloo Thambi 2 et Hanken Grotesk du design system, couleurs des tokens. Tout est placé dans les unités des maquettes (320 de large), textes compris : les illustrations suivent la largeur de l’écran, pas la taille de police du système (elles sont décoratives, décrites par une phrase pour TalkBack, leurs textes réels étant masqués du lecteur d’écran ; textes FR/EN, nouvelles clés `android_onboarding_*`). Les formes vert pâle sont des vecteurs tracés sur les exports (`WelcomeBlobs.kt`). **Seules les photos restent des images** : des **photos libres de droits** (licence Pexels, usage commercial libre, validées par le PM le 2026-10-30 ; auteurs dans `design/exports/onboarding-photos/CREDITS.md`, pas de crédit affiché dans l’app) : portraits de Thomas (la variante aux cheveux bouclés), d’Amandine et de « Maman » en JPEG 480 x 480, salon de la slide 1 en JPEG 1000 x 385, dans `res/drawable-nodpi/onboarding_photo_{thomas,amandine,maman,salon,map}.jpg` (environ 220 Ko en tout), avec un masque rond vectoriel net. **Écart connu** : le salon de la maquette avait un mur à arbre et papillons, la photo libre de droits montre une télévision sur un meuble blanc. **Reste en raster flou** : le fond de carte de la slide 2 (recadrage de la maquette, 122 x 46 px agrandi, avec le cercle de la maquette dessous ; un cercle et un point nets sont dessinés par-dessus) ; à remplacer par un fond de carte libre de droits. Pour changer une photo : remplacer le fichier, sans changement de code (détails dans `design/exports/onboarding-photos/README.md`). Écarts voulus : le lien « J’ai déjà un compte » sous le bouton (décision du ticket), « À l’instant » et « À la maison » avec leur accent (les maquettes l’omettent), « définissez-vous » avec son trait d’union. Les droits d’image sont réglés par le PM.

**Point d'extension du splash** : `ui/startup/StartupSlot.kt`, appelé en tout premier par `AppRoot` (avant le carrousel et le routage) ; il se termine tout de suite tant que le splash animé (autre branche) n'existe pas. Ordre à conserver : splash -> (carrousel si première ouverture) -> connexion / routage.

### Signature de debug stable en CI (secret `DEBUG_KEYSTORE_B64`)

Le workflow décode le secret dans `~/.android/debug.keystore` du runner (alias `androiddebugkey`, mots de passe de debug standard), sans jamais l'afficher, et le supprime en fin de job. Il compare ensuite la SHA-1 de l'APK à celles de `google-services.json` et échoue si elle n'y est pas. Sans le secret (Dependabot, fork), la clé jetable par défaut reste utilisée. Création du keystore : voir « Signature de l'APK debug de la CI » plus bas. Le workflow échoue aussi, avec un message explicite, si `google-services.json` n'a pas de client OAuth web. Sur le poste, copier le même keystore dans `%USERPROFILE%\.android\debug.keystore` donne la même empreinte.

## Mot de passe oublié (ticket AUTH-2)

Code : `ui/auth/PasswordResetScreen.kt` et `PasswordResetViewModel.kt`, `domain/auth/PasswordReset.kt` (tri des réponses de Firebase, pause de renvoi, logique pure testée), `data/auth/PasswordResetGateway.kt` (l'appel à Firebase), route `PasswordResetRoute` dans `ui/navigation/AppRoot.kt`. Le lien « Mot de passe oublié ? » est dans `SignInScreen`.

- **Parcours (v1, décision Q4)** : connexion -> lien « Mot de passe oublié ? » (sous le champ du mot de passe, maquette -8) -> écran « Mot de passe oublié » (maquette -10 : adresse e-mail, « Envoyer ») -> confirmation sur le même écran -> l'e-mail de Firebase contient un lien qui ouvre la **page web d'AUTH-W** (`/reset-password`), où l'on choisit le nouveau mot de passe -> retour dans l'app (« Retour à la connexion », ou flèche / geste retour) -> connexion avec le nouveau mot de passe. **Il n'y a pas d'écran « Nouveau mot de passe » dans l'app** (maquette -11) : il reste dans AUTH-6.
- **Aucune énumération des comptes** (critères M2, C6) : l'écran affiche le **même texte** que l'adresse ait un compte ou non (« Si un compte existe avec cette adresse, un e-mail vient d'être envoyé… » ; même la phrase d'introduction est au conditionnel). Quand la protection contre l'énumération est désactivée côté Firebase, une adresse inconnue renvoie une erreur : `interpretPasswordResetError` la traite comme un succès (de même « compte désactivé »). Une réponse « acceptée » n'est jamais montrée avant 0,8 s (plancher de durée, comme à la connexion) pour que le temps de réponse ne trahisse pas non plus une adresse inconnue. Limite connue : l'envoi réel d'un e-mail peut être plus lent que la réponse « inconnu » ; seul le plancher est garanti.
- **Format** : vérifié sur le téléphone (`AuthForm.isEmailFormatValid`, espaces retirés), message « Cette adresse e-mail ne semble pas correcte. » (texte du ticket §7.3, et non « Email incorrect » de la maquette) à la sortie du champ ou à l'appui sur « Envoyer », jamais pendant la frappe ; aucune requête pour une adresse mal formée. Si le serveur la juge quand même mal formée : même message.
- **Pause de renvoi (critère M5)** : après un envoi, le bouton devient « Renvoyer l'e-mail », grisé **60 s**, avec la phrase « Tu peux demander un nouvel e-mail dans quelques instants. ». La pause est **par adresse** (qui corrige une faute de frappe n'attend pas ; la même adresse, quelle que soit la casse, attend) et vit dans `AppContainer` (en mémoire, jamais sur le disque, jamais journalisée) : quitter l'écran et revenir ne donne pas un renvoi gratuit. Horloge monotone (`SystemClock.elapsedRealtime`). **Ce n'est pas une protection de sécurité** (un client modifié la saute) : la protection réelle est le quota de Firebase et App Check. Pas de compte à rebours qui défile : le bouton se réactive tout seul, sans annonces répétées au lecteur d'écran.
- **Hors connexion et erreurs** : message neutre « Pas de connexion pour le moment… », le champ garde son contenu, aucune requête, aucune pause. Trop de demandes (quota Firebase) : « Un peu trop de demandes pour le moment. Patiente quelques minutes avant de réessayer. » (clé `auth_error_too_many_requests`, sans le conseil « réinitialise ton mot de passe » du texte de connexion, absurde ici). Aucun code technique n'est jamais affiché.
- **Aucun événement Analytics** : l'écran n'a pas accès au traceur (la liste blanche du contrat n'a d'ailleurs aucun événement de ce genre) ; l'adresse n'est ni journalisée ni conservée (seule la dernière adresse envoyée reste en mémoire vive, pour la pause).
- **Textes de connexion** : « Cette combinaison ne fonctionne pas… ou choisis « Mot de passe oublié ? » » et « Un peu trop d'essais… ou réinitialise ton mot de passe. » reprennent les textes finaux du ticket §7.3 (FR + EN). Ces deux messages ne servent qu'à la connexion par e-mail ; l'inscription et Google utilisent la variante sans conseil de réinitialisation (`AuthMessage.TOO_MANY_REQUESTS`).
- **Langue de l'e-mail** : `FirebaseAuth.useAppLanguage()` avant chaque envoi : l'e-mail suit la langue du téléphone et son lien porte `lang=fr` ou `lang=en` (lu par la page web). Pour une autre langue du téléphone, c'est le modèle par défaut de la console.

### À faire par le PM dans la console, une fois la page web AUTH-W déployée (rien n'est dans l'app)

L'app appelle `sendPasswordResetEmail(email)` **sans URL d'action dans le code** (rien n'est inventé) : l'adresse de la page web se règle dans Firebase.
1. **Firebase (projet `miff-3d283`) > Authentication > Modèles > Réinitialisation du mot de passe > modifier > « Personnaliser l'URL d'action »** : `https://miff-3d283.web.app/reset-password` (valeur de `web/README.md` §6). Compléter aussi l'objet et le texte du modèle en **français et en anglais** (tutoiement, texte du PM) et choisir la langue du modèle.
2. Les autres actions de console listées dans `web/README.md` §6 (clé web restreinte, domaine autorisé, Hosting) et dans le ticket §13 (protection contre l'énumération des adresses activée, politique de mot de passe).
3. **Tant que (1) n'est pas fait**, l'envoi fonctionne mais le lien de l'e-mail ouvre la page par défaut de Firebase (sans l'identité de Miff) : suffisant pour essayer, pas pour la bêta.

### Fidélité au design (maquettes -8 et -10) et écarts conscients

Reprend : fond dégradé, flèche retour et mot-symbole centré, titre gras centré, texte d'introduction centré, **champ encadré** blanc aux coins arrondis (arrondi du design system), message d'erreur rouge sous le champ, bouton principal bleu grisé tant que le champ est vide, lien « Mot de passe oublié ? » en petit gras bleu sous le mot de passe.

Écarts **design / ticket** (chacun justifié) :
1. Tutoiement, et introduction reformulée au conditionnel (maquette : « Merci de renseigner votre adresse email d'inscription afin que nous puissions vous faire parvenir un lien… ») : elle ne promet pas d'e-mail (pas d'énumération). **Texte à valider par le PM.**
2. « Email incorrect » devient « Cette adresse e-mail ne semble pas correcte. » (ticket §7.3) ; libellé du champ « Adresse e-mail » (clé commune à l'inscription) au lieu de « Email ».
3. Écran de **confirmation** absent des maquettes : affiché sur le même écran (texte du ticket §7.3, annoncé au lecteur d'écran), sans écran supplémentaire, avec « Retour à la connexion » en lien souligné. Le bouton « Envoyer » devient « Renvoyer l'e-mail ».
4. **Écran « Nouveau mot de passe » (-11) non réalisé dans l'app** (décision PM : page web AUTH-W en v1).
5. Lien « Mot de passe oublié ? » **souligné** (la maquette ne le souligne pas, contrairement aux autres liens : la couleur seule ne doit pas faire le lien) et de 48 dp de haut (maquette : environ 12 sp).
6. Préremplissage de l'adresse déjà saisie à la connexion (ajout : moins de saisie).
7. Erreurs en `#D60700` (contraste AA, comme AUTH-1) et non le rouge `#ea4335` de la maquette ; introduction en 14 sp (les ~11 sp de la maquette sont trop petits).
8. Le bouton suit le contenu (comme les écrans d'AUTH-1), il n'est pas collé au bas de l'écran (le clavier et les grandes polices le feraient disparaître).
9. Étiquette du champ : étiquette Material (dans le cadre, elle remonte sur la bordure à la saisie) au lieu d'un libellé fixe au-dessus du cadre : natif Android, et le lecteur d'écran garde le nom du champ.

Écarts **de plateforme** (natif Android) :
- Bouton ou geste retour du système (y compris le **geste retour prédictif**, activé dans le manifeste) : même effet que la flèche, retour à la connexion.
- Clavier d'adresse e-mail, correction automatique coupée, touche d'action « Envoyer » qui envoie (si le champ est valide et la pause terminée) et ferme le clavier ; **saisie automatique** : le champ déclare « adresse e-mail » (le gestionnaire de mots de passe peut la proposer).
- **TalkBack** : l'écran est nommé et son titre est un titre ; l'erreur du champ est liée au champ (`error`) et annoncée ; la confirmation et les messages sont annoncés poliment ; le chargement est décrit ; flèche, bouton et liens font 48 dp au moins. Polices Hanken Grotesk / Baloo Thambi 2 embarquées, repli sur la police du système pour les écritures qu'elles ne couvrent pas ; taille de police agrandie : l'écran défile, le bouton reste atteignable.
- Pas d'Apple sur Android : la phrase de confirmation du ticket (« Si tu t'es inscrit avec Apple ou Google, utilise plutôt le bouton correspondant… ») évoque un bouton Apple qui n'existe pas sur Android. Texte du ticket conservé (clé commune) ; **le PM peut choisir une variante Android**.

Captures de contrôle : générées par `AuthScreensTest` dans `app/build/screenshots/` (`reset-*.png`, `signin-forgot-*.png`, non commitées).

## Profil et téléphone (ticket ONB-2)

Étapes de création du profil, après l'inscription ou la connexion Google d'un nouveau compte (ticket `docs/tickets/onboarding.md` §6.2 à §6.4, maquettes « Welcoming screen -1 », « -2 », « -6 »).

- **Deux étapes, une seule écriture.** « Profil » (photo facultative, prénom, nom) puis « Téléphone » (pays + numéro). **Rien n'est écrit à l'étape profil.** À la fin de l'étape téléphone, `users/{uid}` est écrit **en une fois** avec toutes les clés (`firstName`, `lastName`, `phone`, `avatar` si présente, `createdAt` et `termsAcceptedAt` en heure serveur, `termsVersion`, `analyticsConsent {enabled:true, consentedAt, consentVersion, revokedAt:null}` : consentement réputé donné au clic sur « Rejoindre », décision du PM). L'écran suivant n'apparaît qu'après **confirmation du serveur**.
- **Transaction Firestore plutôt que `set` simple.** Un `set` fait sans réseau reste en file d'attente et repart plus tard tout seul, peut-être après que la personne a modifié sa saisie ou s'est déconnectée. Une transaction n'existe qu'en ligne et ne laisse rien derrière elle en cas d'échec : « pas de confirmation » veut toujours dire « rien d'écrit ». Délai de 20 s, puis message neutre.
- **Brouillon local** (`data/onboarding/ProfileDraftStore.kt`) : prénom, nom, photo recadrée, pays, numéro tapé et étape atteinte, dans le stockage privé de l'app (aucune sauvegarde cloud : `allowBackup` désactivé et règles d'extraction qui excluent tout). Jamais le mot de passe. Supprimé après la confirmation du serveur et à toute fin de session (déconnexion, refus du panneau, perte de session) ; un brouillon d'un autre compte est effacé et jamais repris. Fermer l'app en plein onboarding ramène à la bonne étape.
- **Routage** (`AppDestination`) : pas de profil -> panneau de consentement ou **PROFILE** ; profil **sans numéro** -> **PHONE_ONLY** (étape téléphone seule, mise à jour du document) ; sinon choix de Miff ou Feed. Compte antérieur à AUTH-B (profil sans `termsVersion`, mise à jour refusée par les règles) : le profil est **supprimé puis recréé** (panneau de consentement, puis les deux étapes), jamais mis à jour. Cela ne se déclenche que d'après la présence réelle de `termsVersion`, **pas** après un refus (règles non déployées, App Check) : un refus ne supprime jamais rien.
- **Numéro.** libphonenumber (Google, Apache 2.0, 360 Ko) : pays par défaut = région de l'appareil sinon France ; tous les pays ; zéro initial, espaces, tirets, points, parenthèses tolérés ; un numéro « +… » collé ou saisi par le remplissage automatique choisit son pays ; validité de **forme** selon le plan de numérotation du pays (mobile ou fixe) ; seul le format **E.164** sort (forme exacte des règles : `^[+][1-9][0-9]{6,14}$`). Aucun SMS, aucune unicité. La mention « Seuls les membres de tes Miffs verront ce numéro… » est affichée sous le champ, toujours. Le numéro n'est jamais écrit dans un journal, un événement, un message d'erreur ni un rapport de plantage.
- **Photo.** Sélecteur de photos **du système** (Photo Picker, **aucune permission**) ou appareil photo **du système** (aucune permission `CAMERA` : la photo est écrite dans **un seul fichier du cache privé**, via un `FileProvider` non exporté, puis supprimée dès qu'elle est lue). Recadrage carré obligatoire (déplacer, pincer, curseur de zoom ; actions « déplacer la photo vers… » pour TalkBack). Ré-encodage **depuis les pixels** : JPEG 160 x 160 (puis 128, 96 si besoin) de **20 000 octets au plus** (limite de `users.avatar` dans `backend/data-model.md` §2.1 ; les 150 Ko concernent les photos d'update, pas l'avatar), **sans aucune métadonnée** (pas de position, de date ni d'appareil : `AndroidAvatarCodecTest` le vérifie sur un fichier source qui contient une position). Rien n'est copié dans la galerie. Fichiers refusés sans plantage : pas une image, plus de 100 millions de pixels, adresse autre que `content://`.
- **Aucune échappatoire pendant l'onboarding** (décision Q14) : pas de flèche retour sur la première étape, pas de « Se déconnecter » ; le bouton ou le geste « retour » du système met l'app en arrière-plan. Sur l'étape téléphone, la flèche et le geste « retour » reviennent au profil (animation de retour prédictif de la navigation).
- **Aucun événement d'analyse** n'est émis par ces étapes.
- **Écrans** : `ui/profile/` (`ProfileScreens.kt`, `CountryPicker.kt`, `PhotoCropScreen.kt`, `ProfileFlow.kt`, `ProfileViewModel.kt`). Captures de contrôle générées par `ProfileScreensTest` dans `app/build/screenshots/` (non commitées).
- **Limite connue** : pour un compte antérieur à ONB-B qui appartient déjà à des Miffs, seul `users/{uid}` reçoit le numéro ; la copie dans chaque fiche de membre est ajoutée par « Réglages > Mes informations » (ONB-3a : un enregistrement réécrit aussi le numéro dans les copies). Tant que la fiche n'a pas de numéro, les boutons « Appeler » et « Message » du Feed ne s'affichent pas pour ce membre (ticket, cas limite 29).

- **Plus strict que le ticket §6.4 (« tout numéro de forme valide »)** : un numéro valide mais **surtaxé, à coût partagé, gratuit (0800), pager, messagerie vocale, numéro personnel ou UAN** est refusé (`PhoneNumbers.ALLOWED_TYPES` : ligne fixe, mobile, fixe-ou-mobile, VoIP). Raison : ce numéro est montré à tout un cercle, et appeler ou écrire à un numéro surtaxé coûterait de l'argent à ses proches. Message : « Ce type de numéro ne peut pas être utilisé ici. Saisis plutôt un numéro de mobile ou de ligne fixe. ». Recommandation de la revue, retenue par défaut ; le PM peut l'assouplir en ajoutant un type à `ALLOWED_TYPES`.
- **Compte antérieur à AUTH-B qui appartient déjà à une Miff** : le profil n'est **jamais** supprimé ni recréé (un refus du panneau de consentement supprimerait le compte entier et laisserait la Miff orpheline) ; message neutre avec l'adresse d'aide, rien n'est touché. `SessionManager.refuseConsentPanel` refuse aussi de supprimer un compte qui a une Miff. Sans Miff, le prénom, le nom et la photo du profil sont **lus avant la suppression** et pré-remplissent les étapes (rien à retaper).
- **Après plusieurs refus du serveur de suite** (3), le message conseille de revenir plus tard (tout est gardé) au lieu de « réessaie ». Un réseau absent ne compte pas.
- **Une transaction qui dépasse le délai de 20 s peut quand même aboutir plus tard** : « rien n'a été écrit » n'est donc pas garanti à 100 % après un message de réseau absent. L'app s'y prépare : si le serveur refuse ensuite l'écriture suivante, elle relit le compte et, si le profil est déjà complet, continue au lieu d'afficher une erreur.
- **Pendant l'écriture**, la flèche et le geste « retour » de l'étape téléphone ne font rien ; l'écran de recadrage rend le contenu en dessous invisible au lecteur d'écran et absorbe les touches.
- **Brouillon** : en plus de la fin de session et de la confirmation, il est effacé au lancement quand personne n'est connecté ou quand le profil existe déjà.

### Écarts conscients design / plateforme (ONB-2)

Design commun aux deux plateformes ; adaptations Android (natif d'abord) :

1. **Tutoiement** : « Permets à tes proches de t'identifier » / « … de te contacter » (les maquettes vouvoient). Les titres proposés par le ticket §6.11 (« Pour que tes proches te reconnaissent »...) n'ont **pas** été retenus : la fidélité aux maquettes l'a emporté ; trivial à changer (`profile_title`, `profile_phone_title`).
2. **Pas de flèche retour sur le profil** (la maquette -1 en montre une) : décision Q14 et Authentification Q11. Le retour du système met l'app en arrière-plan.
3. **Photo** : la maquette ne montre que le rond avec l'appareil photo. Ajouts : légende « Ajouter une photo (facultatif) » et « Visible par les membres de tes Miffs » (ticket §6.3, PR6), feuille de choix Material « Prendre une photo / Choisir dans la galerie / Supprimer la photo », écran de recadrage (non dessinés).
4. **Sélecteur de pays** : le champ fermé reprend la maquette (drapeau, « France +33 », triangle) ; la liste des ~240 pays est une **feuille Material avec recherche** (sans accents ni casse), au lieu d'une liste déroulante non dessinée trop longue.
5. **Numéro** : « +33 » est un préfixe visuel dans le champ, avec un exemple du pays en indice (« 6 12 34 56 78 ») ; clavier **numérique** du téléphone, correction orthographique coupée, remplissage automatique du système autorisé (l'app ne lit jamais le numéro de l'appareil).
6. **Libellé « Téléphone »** de la maquette -6 remplacé par « Numéro de téléphone » (comme la maquette -2 ; ticket §3.2).
7. **Erreur de numéro** : « Ce numéro ne semble pas correct. » (texte du ticket) à la place de « Format de numéro de téléphone invalide » ; sous le champ, en `#D60700` (AA), annoncée par le lecteur d'écran ; l'aide « sans le premier zéro » reste dessous, comme la maquette. **Le numéro d'exemple de la maquette -6 (07 83 30 00 44) est en fait un mobile français valide** : l'app l'accepte.
8. **Mention de visibilité du numéro** (décision N1, obligatoire), absente des maquettes, sous le champ.
9. **Flèche ronde** : bleue, ou ce même bleu à 30 % tant qu'elle ne peut pas servir (comme le bouton principal) ; elle reste au-dessus du clavier ; pendant l'écriture elle affiche un indicateur annoncé par le lecteur d'écran. 56 dp (cibles de 48 dp minimum).
10. **Champs** : libellé **au-dessus** du cadre blanc arrondi, comme les maquettes -1, -2, -6 (et **non** flottant comme les champs soulignés d'inscription, qui suivent leurs propres maquettes) ; libellé et champ ne forment **qu'un seul élément** pour TalkBack. Compteur « 45/50 » à partir de 45 caractères ; « 50 caractères maximum. » au-delà.
11. **Couleurs** : libellés en `text/description` (contraste AA) au lieu du gris des maquettes.
12. **Noms** : on retire les caractères de contrôle et les caractères invisibles ou qui inversent le sens du texte (un nom est affiché aux autres membres) ; tout alphabet est accepté.
13. **Nom proposé par Google** : le nom complet de Google est coupé au premier espace (prénom / reste), modifiable ; jamais de photo importée.
14. Thème clair imposé, droite-à-gauche et police agrandie testés (`ProfileScreensTest`).

## Barre d'onglets, Profil et Réglages (ticket ONB-3a)

Ce que la personne voit une fois l'onboarding terminé et qu'une Miff existe (destination `FEED` du routage) : ticket `docs/tickets/feed-profil-reglages.md` (§6.5, §6.6, §7, critères A1 à A11, décisions du PM du 2026-10-30), maquettes `design/exports/profil/`, `design/exports/reglages/` et les barres d'onglets de `design/exports/feed/`. Code : `ui/main/` (barre et coque de navigation), `ui/feed/` (écran provisoire), `ui/profile/ProfileTab*.kt`, `ui/settings/`, `domain/navigation/`, `domain/settings/`, `data/profile/MyProfileRepository.kt` et `CurrentProfile.kt`, `data/settings/LanguageStore.kt`.

- **Qui voit les onglets.** Seulement la destination `FEED` (profil complet **et** au moins une Miff). `MIFF_CHOICE` (aucune Miff) reste l'écran provisoire plein écran **sans barre**, comme le décide le ticket Onboarding (§6.7 et Q13). Comme aucun écran « Créer / rejoindre une Miff » n'existe encore, **le build debug ajoute un bouton « Aperçu interne : ouvrir les onglets et les Réglages »** sur cet écran provisoire, pour recetter sans créer de Miff à la main dans la console Firebase. Ce bouton est derrière `BuildConfig.DEBUG` : il n'existe pas dans le build release.
- **Barre d'onglets** (`MainTabBar`) : carte blanche flottante arrondie, Feed (maison avec un cœur) à gauche, bouton rond au centre, Profil à droite ; onglet actif = pastille bleu pâle derrière l'icône bleue ; pas de libellé visible (maquettes), **un nom parlé par onglet** pour TalkBack (rôle « onglet », état « sélectionné »), zones d'au moins 48 dp. Disposition avec début/fin : l'ordre s'inverse en droite-à-gauche, les icônes (sans direction) ne sont pas retournées. Masquée quand le clavier est ouvert et dans les écrans de Réglages (comme les maquettes). Toucher l'onglet déjà actif remonte en haut ; l'état de chaque onglet survit au changement d'onglet et à la rotation (`rememberSaveable`) ; « retour » sur un onglet racine quitte l'app.
- **Bouton central (décision Q1)** : « Donner des nouvelles » n'a pas encore de destination (ticket Update). Build **debug** : visible mais **désactivé** (« Donner des nouvelles, bientôt disponible » pour TalkBack). Build **release** (testeurs externes) : **absent**, la barre n'a que deux onglets (`TabBarConfig.forBuild`).
- **Feed provisoire** : logo, « Ton fil n'est pas encore prêt », une phrase honnête (écran en construction, position non utilisée pour l'instant). Ni cloche (décision Q15), ni avatar, ni personne inventée, ni « tout va bien ».
- **Profil (en-tête)** : photo ou initiales, « Prénom Nom », « C'est toi », engrenage rond de 48 dp. Toucher la photo ou le nom ouvre « Mes informations ». **Aucune donnée fictive** : pas de carte de Miff, pas de « Lieux » (tickets Miff et Lieux). Le profil est lu une fois (`CurrentProfile`, en mémoire, effacé à la déconnexion) ; illisible = message calme + « Réessayer ».
- **Réglages** : Mes informations, Mon compte, Langue, Confidentialité (ONB-3b). Ni « Notifications » (décision Q6), ni bandeau Premium ni « Abonnement » (décision Q7), ni « Supprimer mon compte » (AUTH-5).
- **Mes informations** : photo (Photo Picker du système, ou appareil photo du système ; recadrage carré ; JPEG 160 x 160 de 20 000 octets au plus sans métadonnée : **les composants d'ONB-2 sont réutilisés**), prénom et nom (1 à 50 caractères, compteur « n/50 » du nombre réel de caractères), pays + « Téléphone » (libphonenumber, E.164, `PhoneNumbers.kt`), mention de visibilité du numéro. « Enregistrer » est grisé tant que rien n'a changé ou qu'une valeur est inutilisable ; un numéro qui ne semble pas correct est signalé calmement quand on quitte le champ ou en appuyant sur « Enregistrer ». Quitter avec des changements non enregistrés demande une confirmation (deux boutons de même poids).
- **Écriture (critère A7)** : `FirestoreMyProfileRepository.save` fait **une transaction** qui met à jour `users/{uid}` **et** la copie `miffs/{miffId}/members/{uid}` de chaque Miff (les `miffId` viennent de `users/{uid}/memberships`, lus **sur le serveur** et vérifiés contre la forme `[A-Za-z0-9]{20}` avant de devenir un chemin). Les quatre clés (`firstName`, `lastName`, `phone`, `avatar` ou suppression) sont toujours toutes envoyées : une copie restée en retard est réparée par n'importe quel enregistrement. Conforme à `backend/firestore.rules` **déployées** (`validMemberUpdate` : la copie doit égaler le profil après le lot, `getAfter` ; au plus 15 copies par requête : 20 lectures de règles autorisées) : **aucune règle modifiée**. Une transaction, pas un lot simple : hors connexion, rien n'est écrit **ni mis en file d'attente** (message neutre, champs conservés).
  - **Une Miff disparue ne bloque jamais le changement de nom** : si le serveur refuse la transaction complète alors qu'il y a des copies, le profil est écrit **seul**, puis chaque copie **séparément** ; une copie refusée seule (Miff qui n'existe plus) est ignorée.
  - **Plus de 15 Miffs (Premium)** : le profil part avec les 15 premières copies, les suivantes par groupes de 15.
  - **Copies restées en attente** (réseau perdu entre deux écritures) : message « Tes informations sont enregistrées. Tes proches verront les changements dès que la connexion le permet… » et le bouton **reste actif** pour terminer (un appui renvoie tout, c'est sans risque : les quatre clés sont réécrites à l'identique). **Limite** : ce « reste à faire » n'est pas conservé si l'app est fermée avant ; il ne concerne que le Premium au-delà de 15 Miffs ou un réseau coupé pile entre deux écritures. Signal de revoir : des proches qui voient un ancien nom ou numéro.
  - **Réparation du compte antérieur à ONB-B** (limite notée à ONB-2) : un enregistrement écrit aussi le numéro dans les copies qui n'en avaient pas.
- **Langue (décision Q4)** : Français, English, « Langue du téléphone », chaque nom dans sa langue, **effet immédiat sans bouton**. Android 13 et plus : `LocaleManager` (le choix apparaît aussi dans « Langues des applications » d'Android et la liste vient de `generateLocaleConfig`). Android 8 à 12 : le choix est gardé dans un petit fichier privé de l'app (`miff_language`, un code de langue : jamais une donnée personnelle) et appliqué par `MainActivity.attachBaseContext` ; l'écran est reconstruit quand le choix change. **AppCompat n'est pas ajouté** : cela exigerait de changer le thème de l'activité (thème de la plateforme voulu) et une dépendance pour le même résultat. Le choix change aussi `Locale.getDefault()` : les liens légaux (`android_legal_*_path`) et l'e-mail de réinitialisation (Firebase, `useAppLanguage`) suivent la langue de l'app.
- **Mon compte** (version minimale livrée par ONB-3a ; mot de passe ci-dessous, ONB-3b) : e-mail en lecture seule (lu dans Firebase Auth pour l'affichage, **jamais copié** ailleurs : ni Firestore, ni événement, ni journal) et « Se déconnecter » (bouton secondaire) avec confirmation « Te déconnecter ? Tes proches ne verront plus tes mises à jour tant que tu es déconnecté(e). ». Raison de sa présence dans ONB-3a : le bouton de déconnexion de l'écran provisoire disparaît du Feed ; sans cet écran, aucune déconnexion ne serait possible depuis l'app.
- **Mon compte, complété (ticket ONB-3b)** : **Changer mon mot de passe** (décision Q5) = un bouton qui envoie l'e-mail de réinitialisation d'AUTH-2 à l'adresse du compte (`PasswordResetGateway`, nouveau mot de passe choisi sur la page web d'AUTH-W), **sans formulaire à trois champs** ni ré-authentification ; confirmation unique et calme, bouton « Renvoyer l'e-mail » en pause 60 s (`ResendCooldown` partagé avec « Mot de passe oublié »), messages de réseau et de quota sans conseil de réinitialiser. **Masqué pour un compte Google** (« Connecté avec Google »). « Supprimer mon compte » reste **absent** (AUTH-5).
- **Confidentialité (ticket ONB-3b)** : `ui/settings/PrivacyScreen.kt`, `PrivacyViewModel.kt`, `domain/privacy/AnalyticsConsentTransition.kt`, `data/privacy/AnalyticsConsentRepository.kt`.
  - **Un seul interrupteur** « Statistiques d'usage et rapports de plantage anonymes » (deux SDK, un choix : `PrivacySettings.applyAccountConsent`), lu et écrit dans `users/{uid}.analyticsConsent` selon **les transitions des règles** déployées (activer : quatre clés, version `ConsentVersions.ANALYTICS` jamais plus ancienne ; retirer : `revokedAt` = heure serveur, trace `consentedAt` / `consentVersion` conservée ; rien à écrire si l'état est déjà celui demandé). Écriture par **transaction** (en ligne seulement, rien en file d'attente).
  - **Accepter** : enregistré **d'abord**, la collecte ne démarre qu'ensuite (jamais de collecte sans consentement inscrit au compte). **Retirer** : la collecte est **coupée tout de suite** sur le téléphone (et les rapports de plantage non envoyés sont supprimés), puis le retrait est enregistré.
  - **Retrait hors connexion, durci (décision du PM « Oui pour durcir le consentement »)** : `data/privacy/ConsentWithdrawals.kt` et `PendingWithdrawals.kt`. Le retrait est **d'abord marqué sur l'appareil** (fichier privé `miff_consent_pending`, écrit sur le disque avant de rendre la main, exclu de toute sauvegarde et de tout transfert par `backup_rules.xml` / `data_extraction_rules.xml`), puis enregistré dans le compte. Ne sont gardés que **« retrait en attente » + l'heure** et une **clé** = empreinte à sens unique (SHA-256 coupée à 32 caractères) de l'identifiant du compte : ni nom, ni e-mail, ni identifiant en clair, ni position. Au plus 20 comptes (le plus ancien est oublié).
    - **Au démarrage suivant** (ou à la connexion), `SessionManager` lit le compte puis **garde la collecte coupée tant que la marque existe pour CE compte**, même si le compte dit encore « accepté » ; aucun événement de connexion n'est envoyé.
    - **Enregistrement en arrière-plan, sans bloquer l'écran** : sans réseau, la tâche attend son retour (`NetworkMonitor.onlineChanges()`, rappel système, aucune interrogation répétée) ; si le réseau est là mais le serveur injoignable, nouvelles tentatives après 5 s, 15 s, 1 min puis toutes les 5 min. Elle respecte les transitions des règles déployées (`AnalyticsConsentTransition` : retirer = `enabled` false, `revokedAt` heure serveur, `consentedAt` et `consentVersion` conservés ; rien à écrire si le compte est déjà « retiré »). Une fois enregistré, la marque disparaît.
    - **Pas de boucle** : à la première réponse « non » du serveur (règles, App Check), la tâche s'arrête, la collecte **reste coupée**, l'écran continue de proposer « Enregistrer mon choix », et le démarrage suivant essaie une dernière fois.
    - **Retrait puis ré-acceptation avant l'enregistrement** : accepter exige le réseau (le compte doit enregistrer l'acceptation) ; l'acceptation et le retrait partagent un verrou ; **la marque n'est annulée qu'une fois l'acceptation prise par le serveur**. Hors connexion, accepter ne change rien et le dit.
    - **Déconnexion ou autre compte** : la marque **suit son compte**. Elle n'est écrite que pour ce compte et seulement tant qu'il est connecté ; après une déconnexion ou un changement de compte, la tâche s'arrête et la marque attend le retour de son compte (jamais appliquée à un autre). Un compte supprimé laisse une empreinte inoffensive (au plus 20).
    - **À l'écran** : tant que la marque existe, un message calme dit que c'est coupé sur le téléphone (même après un redémarrage) et sera enregistré dès que la connexion revient, avec « Enregistrer mon choix » pour essayer tout de suite.
    - **Reste vrai** : une désinstallation de l'app efface la marque (et la collecte repart de zéro, éteinte) ; un téléphone qui ne retrouve jamais le réseau garde la collecte coupée mais le compte « accepté ».
  - **Aucun événement d'analyse** n'est envoyé par cet écran (`docs/contracts/analytics-events.md` ne définit rien pour les réglages) ; **jamais de position** dans ce qui est collecté.
  - **Localisation** et **notifications** : dites honnêtement. Miff n'utilise pas la position pour l'instant (aucune permission de localisation n'est déclarée) et rien n'est envoyé ; aucun état inventé (« toujours », « approximative »). Les deux ont « Ouvrir les réglages du téléphone » (réglages de l'app, réglages de notification de l'app). Les états réels (permissions) arrivent avec les tickets de localisation et de notifications.
  - **Liens** « Conditions d'utilisation » et « Politique de confidentialité » : `LegalLinks` (base `BuildConfig.LEGAL_BASE_URL`, chemin par langue `android_legal_*_path`, **vérifiés contre `web/src/pages.json` par `LegalLinksTest`**). Les pages AUTH-W ne sont **pas encore déployées** : les liens mènent à l'adresse où elles seront. **Pas de page « Mentions légales » séparée** dans `web/src/pages.json` (l'éditeur est dans les textes légaux) : aucun lien de ce nom n'a été inventé. « Nous contacter » ouvre l'application e-mail sur le contact provisoire de CLAUDE.md (`BuildConfig.SUPPORT_EMAIL`), sans aucune donnée ajoutée.
- **Aucune donnée personnelle dans les journaux, événements d'analyse ni rapports de plantage** : le code n'écrit aucun journal (`NoSensitiveLoggingTest`) ; `MyProfile.toString()` ne montre rien ; aucun événement n'est émis par ces écrans.
- **Écarts conscients design / plateforme (ONB-3a)** : voir la description de la PR (tableau) ; les principaux : tutoiement, libellé « Téléphone », compteurs réels, aucun « 3 caractères minimum », mention de visibilité du numéro, « Enregistrer » grisé sans changement, deux langues, pas de « Notifications » ni d'encart Premium, « Déconnexion » en bouton secondaire, pas de carte de Miff ni de lieux, confirmation de déconnexion et d'abandon ajoutées (non dessinées).

## Zones sûres : barre d'état, barre de navigation, encoche, clavier (règle pour tout écran)

L'app dessine « bord à bord » (`enableEdgeToEdge` dans `MainActivity`, et Android 15 l'impose de toute façon : `targetSdk` 36). Sans précaution, un écran se dessine donc SOUS l'heure, la batterie, les touches ou la barre de gestes, et l'encoche.

**Règle : tout écran, barre ou feuille prend ses marges système dans `ui/components/ScreenInsets.kt`, et nulle part ailleurs.**

| Je construis... | J'utilise |
|---|---|
| un écran entier | `MiffScreen(Modifier.background(...)) { ... }` (ou `Modifier.miffScreenInsets()` sur la racine d'un écran existant) |
| une barre du haut qui dessine sous la barre d'état (Réglages) | `Modifier.miffTopInsets()` sur la barre, puis `Modifier.miffBottomInsets()` sur le corps |
| le contenu d'un onglet | `Modifier.miffTopInsets()` sur sa racine (la barre d'onglets gère le bas) |
| le contenu d'une feuille (`ModalBottomSheet`) | `Modifier.miffBottomInsets()` |
| une barre du bas | `Modifier.miffBottomInsets()` |
| un élément à pousser seulement vers le bas | `miffTopInset()` (hauteur) |

- Le fond (couleur, dégradé) se met AVANT le modificateur : il continue ainsi sous les barres système, sans que le contenu y passe.
- Ces modificateurs **consomment** ce qu'ils appliquent : les imbriquer ne double jamais une marge.
- **Interdits ailleurs que dans ce fichier** (`ScreenInsetsRuleTest` échoue sinon) : `safeDrawingPadding`, `statusBarsPadding`, `navigationBarsPadding`, `imePadding`, `windowInsetsPadding`, `WindowInsets.*`, `consumeWindowInsets`, `contentWindowInsets`. Pas de `Scaffold` sans y réfléchir : son `contentWindowInsets` par défaut remplit le haut, et un `consumeWindowInsets(padding)` ensuite « mangeait » la barre d'état avant que les onglets s'en servent.
- Exception voulue : l'écran de lancement animé (`ui/splash`) est plein écran et ne prend rien d'ici ; il passe seulement les icônes de la barre d'état en clair le temps de l'animation.
- Fenêtres de dialogue (`AlertDialog`) : Android les place lui-même hors des barres, rien à faire.
- Clavier : `AndroidManifest.xml` déclare `windowSoftInputMode="adjustResize"` (Android 8 à 10 : sans cela le clavier n'est pas transmis à Compose). Les écrans à formulaire défilent ; la barre d'onglets se cache quand le clavier est ouvert.
- Icônes de la barre d'état : `MainActivity` demande des icônes sombres (app claire seulement), thème `windowLightStatusBar=true` pour les premières images.

- **Repli si le téléphone ne rapporte rien (ajouté après la recette du 31/10/2026, l'OPPO ayant gardé le défaut)** : `miffSafeInsets()` prend les marges rapportées par le système ; si le haut vaut 0 alors que la fenêtre commence tout en haut de l'écran et que la barre d'état n'est pas déclarée masquée, il utilise la hauteur que le système déclare pour sa barre (`status_bar_height`) ; idem en bas, uniquement en navigation à 3 ou 2 boutons (`navigation_bar_height`, portrait). Une valeur réelle du système n'est jamais remplacée. Actif seulement dans `MainActivity` (`ProvideSystemBarsFallback`) : les tests voient exactement les marges qu'ils envoient.
- **Diagnostic (APK debug seulement)** : l'écran de diagnostic, ouvert au lancement de l'APK debug, commence par la carte « Marges système (insets) : à photographier » (valeurs brutes de Compose et du système, densité, version d'Android, mode de navigation, repli appliqué, marqueur `insets-r2`). Fichier `src/debug/.../InsetsDiagnostic.kt`, seul autre fichier autorisé à lire les marges brutes.

**Cause du défaut vu à la recette (31/10/2026)** : `MainTabs` utilisait un `Scaffold` (rempli par défaut avec la hauteur de la barre d'état) puis `consumeWindowInsets(padding)` : le haut était déclaré « déjà géré » alors que personne n'y avait appliqué de marge, et le `statusBarsPadding()` du Feed et du Profil recevait 0. Les autres écrans appliquaient déjà leurs marges ; ils passent maintenant eux aussi par le fichier commun.

**Tests** : `ScreenInsetsTest` (Robolectric, `src/testDebug/.../ui/insets`) envoie à chaque écran de vraies marges système (barre d'état 48 dp, barre de navigation 48 dp, encoche, clavier) et échoue si un texte, une icône ou un bouton passe sous la barre d'état, l'encoche ou la barre latérale, ou si la barre d'onglets passe sous la barre de navigation ; il a été vérifié qu'il échoue avec l'ancien `MainTabs`. `ScreenInsetsRuleTest` (JVM) interdit les fonctions brutes. Un nouvel écran doit être ajouté à `ScreenInsetsTest`. Recette PM : `docs/qa/checklists/insets-android-recette-pm.md`.

## Écran de lancement animé (splash)

Demande du PM après sa recette sur l'OPPO : « on voit le logo de l'app sans le cœur vert, le cœur vert apparaît et gonfle comme un ballon de baudruche jusqu'à éclater, puis l'app s'ouvre ». Ordre du démarrage : **écran de lancement système → animation Compose → app** (le carrousel de première ouverture, ticket Onboarding, fera partie de l'app : il n'apparaît qu'après le splash).

| Étape | Ce qui se passe | Où |
|---|---|---|
| Écran système (Android 12+ : API Splash Screen ; avant : rétro-portage `androidx.core:core-splashscreen`, minSdk 26 conservé) | Fond bleu `#043CD1` uni et « M + i » blanc **sans cœur** (`drawable/splash_logo_mark.xml`, copie du premier tracé de l'icône officielle, dessiné à 288 dp comme l'icône du splash Android 12) | thème `Theme.Miff.Starting`, `MainActivity.installSplashScreen()` |
| Animation (2,9 s au total, 3,0 s au plus, demande du PM : « à peine le temps de comprendre ce qu’il se passe ») | 0 à 0,3 s : logo seul (relais de l'écran système) ; 0,3 à 0,7 s : le cœur apparaît sur le « i » ; 0,7 à 2,2 s : il gonfle lentement de sa taille du logo officiel jusqu’à **120 %** (jamais au-delà de 125 % ; courbe douce, élastique léger, accélération vers la fin ; « un petit peu plus gros que sur le logo officiel », recette du PM du 2026-10-30), et tient 0,12 s à sa taille maximale juste avant l’éclat ; 2,2 à 2,7 s : pop net (disparition, 12 petits éclats verts qui s’éloignent lentement, vers le haut seulement) ; 2,7 à 2,9 s : fondu vers l’app | `domain/splash/SplashTimeline.kt` (fonction pure du temps, testée), `ui/splash/SplashHost.kt` (dessin) |
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
- Le choix du compte (donné par le clic, modifiable ensuite dans les Réglages) commande la collecte ; l'appareil le garde pour la session en cours, mais **ne le réapplique pas au démarrage** : tout démarre coupé et ne se rallume qu'après la lecture de `users/{uid}`. L'écran de diagnostic (debug) reste un second moyen d'allumer ces collectes pendant une session.
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
                  ui/ (theme, components (dont ScreenInsets.kt : zones sûres), home, navigation, main = barre d'onglets, feed, profile, settings, splash), domain/ (logique pure testée),
                  data/ (privacy, auth, profile, settings)
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
- **AppCompat toujours non ajouté**, même avec le sélecteur de langue interne d'ONB-3a : `LocaleManager` (Android 13+) et un contexte localisé (Android 8 à 12) donnent le même résultat sans changer le thème de l'activité ni ajouter de dépendance (voir « Barre d'onglets, Profil et Réglages »).
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
| libphonenumber (Google) | 9.0.42 | Apache 2.0 |
| AndroidX ExifInterface | 1.4.2 | Apache 2.0 |
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
