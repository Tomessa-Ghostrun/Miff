# Miff - application iOS

SwiftUI, iOS 16 minimum, iPhone seulement, identifiant `com.tomessa.miff`. Contenu actuel : le **socle technique** (ticket INIT-4, miroir d'INIT-2 Android) et la **parité Android** de l'authentification et de l'onboarding : **AUTH-3** (inscription et connexion par e-mail, Google et Apple), **AUTH-4** (mot de passe oublié), **ONB-5** (icône, carrousel de bienvenue, écran de lancement animé) et **ONB-6** (profil : prénom, nom, photo, numéro de téléphone obligatoire, routage de la session). Mêmes règles métier, mêmes textes et mêmes événements qu'Android. S'y ajoute **MIFF-3** (miroir d'Android MIFF-1) : **créer une Miff, la rejoindre par son code, l'écran de la Miff, « Partager mon statut », l'accès perdu** (voir « Miffs » plus bas). Retirer un proche, quitter, supprimer, renouveler le code et « Prévenir mes proches » sont **MIFF-4** (aucun bouton n'est dessiné pour eux). Le fil, l'onglet Profil et les Réglages n'existent pas encore sur iOS : après l'onboarding, l'app s'ouvre sur un **accueil provisoire** (la Miff de la personne, ou « Pas encore de Miff »), avec un bouton de déconnexion.

L'icône et le mot-symbole sont les **fichiers officiels** (`design/assets/logo`, validés par le PM le 2026-10-27) : l'icône est `Miff/Resources/Assets.xcassets/AppIcon.appiconset`, le mot-symbole est dessiné en vectoriel (`Miff/DesignSystem/Logo/`) à partir des tracés de `design/assets/logo` (générés par `tools/generate-logo-paths.mjs`, jamais retapés à la main). Le « G » du bouton Google est un **redessin** à remplacer par le visuel officiel des directives de marque de Google avant la fiche de l'App Store (voir « Écarts »).

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
| `Miff/Models/` | Logique pure, testée sans réseau : liste blanche Analytics, erreurs de connexion, formulaires, routage par état du compte (`Auth/`), numéro de téléphone, noms et recadrage de la photo (`Profile/`), chronologie du lancement (`Splash/`), lecteur de tracés SVG (`Support/`), code secret, nom d'une Miff, lots d'écritures, erreurs, limiteur d'essais, assemblage des écoutes (`Miff/`) |
| `Miff/Services/` | `Contracts/` (protocoles entre la logique et le monde extérieur), `Session/` (`SessionManager`, le seul responsable de « qui est connecté et où s'ouvre l'app »), `Firebase/` (Auth, Firestore, dont `FirestoreMiffStore` et `FirestoreMiffObserver`), `Social/` (fenêtres système Google et Apple), `Profile/` (PhoneNumberKit, encodage de la photo), `Storage/` (brouillon du profil, acceptation en attente, réseau, Miff active), `Miff/` (dépôt des Miffs et annuaire de la session), choix de confidentialité (`PrivacySettings`) |
| `Miff/ViewModels/` | Inscription, connexion, panneau de consentement, mot de passe oublié, profil et téléphone, reflet observable de la session, créer / rejoindre / écran d'une Miff |
| `Miff/DesignSystem/` | Tokens : couleurs, styles de texte, arrondis, ombres, dégradés, bouton primaire ; `Logo/` (mot-symbole et icône vectoriels), `GoogleBrand.swift` |
| `Miff/Views/` | Racine de l'app (`AppRootView`, `SignedOutHost`), `Auth/`, `Onboarding/` (carrousel et ses illustrations), `Profile/`, `Splash/`, `Miff/` (choix, création, saisie du code, écran de la Miff, accueil provisoire, accès perdu) |
| `Miff/Diagnostic/` | Écran de diagnostic et galerie de tokens (**compilés seulement en Debug et Internal**) |
| `Miff/Localization/` | `L10n` : accès aux textes du String Catalog |
| `Miff/Resources/` | Icône, polices (`Fonts/`), `Localizable.xcstrings`, `InfoPlist.xcstrings`, `PrivacyInfo.xcprivacy` |
| `Config/Miff.entitlements` | Capacités App Attest et « Se connecter avec Apple » (builds Internal et Release seulement) |
| `MiffTests/` | Tests XCTest |
| `ci/` | Scripts de la CI (durées, résolution des paquets, captures, contrôles du binaire, symboles Crashlytics, schéma d'URL de Google) |
| `tools/` | Générateurs en Node (données vectorielles du logo, des formes du carrousel, image de l'écran de lancement) |
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
| Écran affiché au démarrage | **Diagnostic interne** (lien « vrai parcours de l'app ») | **Le vrai parcours** (carrousel, inscription...) ; le diagnostic s'ouvre par un **appui long de 1,5 s en haut à gauche de l'écran** (sur l'heure), dans une feuille qu'on ferme en la faisant glisser vers le bas | Le vrai parcours (carrousel, inscription...) |
| Entitlement « Se connecter avec Apple » | non | oui | oui |
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

## Authentification, onboarding et profil (AUTH-3, AUTH-4, ONB-5, ONB-6)

Parité avec Android (mêmes tickets `docs/tickets/authentification.md` et `onboarding.md`, mêmes décisions du PM `onboarding-decisions-pm.md`) ; le code Android de référence est dans `android/.../ui/auth`, `ui/profile`, `ui/onboarding`, `ui/splash`, `data/session/SessionManager.kt`.

- **Une seule source de vérité pour « où s'ouvre l'app »** : `Services/Session/SessionManager` (Swift pur, testé avec des doubles). La destination vient de l'état du **compte** (profil ? numéro ? Miff ?), jamais de « une connexion vient d'avoir lieu » (`resolveDestination`). `SessionObservable` la publie à l'interface ; `Views/AppRootView` ne fait que l'afficher.
- **Inscription** : e-mail et mot de passe (8 caractères au moins, aucune règle de complexité), Google, Apple. **Aucune case à cocher** : la ligne au-dessus du bouton dit que « Rejoindre » (ou « Continuer avec Apple » / « Continuer avec Google ») accepte les conditions, la politique et le partage de statistiques anonymes ; le geste vaut acceptation et rien n'est collecté avant. Une personne nouvelle qui arrive par Google ou Apple depuis l'écran de **connexion** reçoit le panneau de consentement ; le refuser supprime le compte tout juste créé (ré-authentification Google ou Apple si Firebase la réclame).
- **Connexion** : un seul message pour « adresse inconnue » et « mauvais mot de passe », avec un plancher de 800 ms sur la durée de cette réponse ; pause de confort après 5 échecs (30 s). **Mot de passe oublié** : une confirmation identique pour toute adresse, pause de 60 s par adresse ; le nouveau mot de passe se choisit sur la page web d'AUTH-W.
- **Profil** (ONB-6) : étape profil (photo facultative, prénom, nom) puis étape téléphone (pays, numéro national, E.164 enregistré, **obligatoire**). Rien n'est écrit à l'étape profil : un **brouillon local** (stockage privé de l'app, exclu des sauvegardes, effacé à la déconnexion et après confirmation) garde la saisie ; UNE écriture par **transaction** Firestore (délai de 20 s) à la fin, confirmée par le serveur avant l'écran suivant.
- **Photo** : sélecteur de photos du système (`PhotosPicker`, **aucune permission**), recadrage carré obligatoire, puis JPEG **ré-encodé depuis les pixels** (aucune métadonnée de la photo d'origine : ni position, ni date, ni appareil), 20 000 octets au plus. Testé avec une photo qui porte une position GPS.
- **Numéro** : [PhoneNumberKit](https://github.com/PhoneNumberKit/PhoneNumberKit) 5.0.13 (MIT ; le dépôt historique `marmelroy/PhoneNumberKit` n'est pas archivé mais n'a plus de version depuis la 4.3.0 de mai 2026, les versions 5.x sont publiées par l'organisation `PhoneNumberKit`), mêmes métadonnées (libphonenumber 9.0.42) et mêmes cas de test qu'Android ; seuls les genres fixe, mobile et voix sur IP sont acceptés (comme Android).
- **Carrousel** (première ouverture seulement), **écran de lancement** : voir « Carrousel et lancement » ci-dessous.
- **Rien n'est journalisé** : ni mot de passe, ni adresse e-mail, ni nom, ni numéro, ni jeton. Les jetons Google et Apple vont à Firebase Authentication et nulle part ailleurs ; leur description textuelle est volontairement masquée.

### Connexion Google

- SDK `GoogleSignIn-iOS` 10.1.0 (Apache-2.0, version exacte). La 9.x n'est pas compatible avec Firebase 13 (conflit sur `app-check`).
- L'identifiant du client OAuth vient de `GoogleService-Info.plist` (`CLIENT_ID`). Le **schéma d'URL de retour** (`REVERSED_CLIENT_ID`) doit être déclaré dans l'`Info.plist` ; comme le fichier de configuration n'est jamais dans le dépôt, la phase de compilation « Google - schéma d'URL de retour » (`ci/inject-google-url-scheme.sh`) le lit dans le fichier embarqué et l'ajoute à l'`Info.plist` **du build** (sans jamais l'afficher). Le contrôle `ci/check-binaries.sh` vérifie qu'il y est.
- **Prérequis côté PM** : le fichier de configuration actuel a été généré **avant** l'activation de Google dans Firebase Authentication ; il n'a donc peut-être ni `CLIENT_ID` ni `REVERSED_CLIENT_ID`. Le régénérer (`firebase apps:sdkconfig IOS 1:29899146512:ios:94e30481497dd74c659195 --project miff-3d283`) puis mettre à jour le secret `GOOGLE_SERVICE_INFO_PLIST_B64`. Sans cela, le bouton Google affiche le message neutre d'erreur (la CI avertit).

### Connexion Apple

- Natif (`AuthenticationServices`) : jeton à usage unique (nonce de 32 caractères tirés avec `SecRandomCopyBytes`, seule l'empreinte SHA-256 est donnée à Apple), le prénom et le nom ne sont donnés qu'à la première connexion et ne servent qu'à préremplir l'étape profil. Bouton : `ASAuthorizationAppleIDButton` officiel (libellé « Continuer avec Apple » fourni par le système, selon la langue du téléphone).
- **Prérequis côté PM** (en cours) : activer la capacité « Sign in with Apple » sur l'identifiant `com.tomessa.miff` (developer.apple.com) **et** le fournisseur Apple dans Firebase Authentication. Sans la capacité, la signature automatique de l'archive TestFlight échoue ; sans le fournisseur Firebase, le bouton Apple répond par le message neutre d'erreur.

### Carrousel et lancement (ONB-5)

- **Icône** : l'icône officielle (`design/assets/logo/ios`).
- **Carrousel** : 3 slides (balayage seulement, aucun défilement automatique), une seule phrase d'accroche, « Commencer » sur chaque slide, « J'ai déjà un compte ». Affiché **uniquement à la première ouverture sans session** ; jamais après une déconnexion. Les illustrations sont **reconstruites en vectoriel et en vrais textes** à partir des maquettes (`design/exports/onboarding-original`), comme sur Android ; seules les photos (profils, salon, fond de carte) sont des images.
- **Écran de lancement** : l'écran système (fond bleu, « M + i » blanc, sans le cœur, `UILaunchScreen`) est suivi de l'animation (`Views/Splash`) : le cœur apparaît, gonfle puis éclate (2,9 s au total, 3 s au plus, mesurées en temps réel). Avec « Réduire les animations » : logo complet immobile 0,8 s. La chronologie est une fonction pure du temps (`SplashTimeline`, testée).

## Écarts avec les maquettes et avec Android

1. **Photo de profil sans option « appareil photo »** (Android la propose) : sur iOS elle demanderait la permission de la caméra, que l'app n'a pas (aucune permission demandée). Clés `profile_photo_take` et `profile_photo_camera_unavailable` conservées pour la parité du catalogue, non utilisées.
2. **Bouton Apple** : composant système (libellé, police et forme d'Apple) ; la maquette en dessine un. La ligne de consentement de l'inscription cite aussi ce bouton (clé `ios_auth_consent_notice`, propre à iOS).
3. **Bouton Google** : police du système (Roboto n'est pas embarquée) ; le « G » est un **redessin à remplacer** par le visuel officiel avant la fiche de l'App Store.
4. **Sélecteur de photos** : celui du système (les maquettes n'en dessinent pas) ; le recadrage carré est un écran ajouté (non dessiné), dans le même style.
5. **Retour** : flèche de l'en-tête ET geste natif de balayage depuis le bord (navigation native `NavigationStack`).
6. **Cible tactile** de 44 pt (Apple) au lieu de 48 dp.
7. **Session après réinstallation** : sur iOS, la session Firebase est gardée dans le **Trousseau**, qui survit à la désinstallation, alors que les réglages de l'app (carrousel vu, etc.) disparaissent avec elle. Pour qu'une app réinstallée ne s'ouvre pas connectée sans que la personne ait revu le carrousel ni le consentement, **au premier lancement d'une installation** (repère absent des réglages de l'app) une session trouvée est **fermée** avant tout routage : la personne se reconnecte (écran de connexion). Conséquence visible : après une réinstallation, il faut se reconnecter. Recommandation de la revue, appliquée sauf veto du PM ; testée (`SessionSafetyTests`). Android n'a pas ce cas (une désinstallation efface tout).
8. **Barre d'état pendant le lancement animé** : l'animation occupe tout l'écran, barre d'état comprise.
9. **Exemple de numéro** du champ téléphone : fourni par PhoneNumberKit (exemple de mobile du pays), formaté sans l'indicatif ni le premier zéro.
10. **Réglages et retrait du consentement** : l'écran Réglages / Confidentialité d'Android (ONB-3b) n'existe pas encore sur iOS. Le consentement aux statistiques est donné par le geste d'inscription, et le retirer doit rester aussi simple que le donner (RGPD art. 7(3)). Tant que l'écran n'existe pas, **le texte de consentement iOS ne promet plus « modifiable dans les Réglages »** : il dit « tu peux le retirer à tout moment en écrivant à » l'adresse de contact (clés `ios_auth_consent_notice` et `ios_auth_panel_notice`, propres à iOS ; les clés d'Android `auth_consent_notice` et `auth_panel_notice` restent dans le catalogue pour la parité). Choix : adapter le texte plutôt que créer un écran de confidentialité minimal, qui dupliquerait le ticket Réglages (retrait immédiat, effacement des rapports, écriture du compte) hors du périmètre de ce lot. **Limite** : le retrait passe par un e-mail traité à la main ; le ticket iOS Réglages devra rétablir un retrait dans l'app, puis remettre le texte d'Android. À valider par le PM (décision légale).

## Miffs : créer, rejoindre, écran de la Miff, partage, accès perdu (MIFF-3)

Miroir iOS d'Android MIFF-1 (tickets `docs/tickets/miff.md` §8 et §16, décisions du PM `miff-questions-pm.md`, `feed-profil-reglages.md` §13, maquettes `design/exports/miff/`, `onboarding-original/` -3 et -18, `feed/` -2 et -3, fiches `design/screens/miff-*.md`). Mêmes règles métier, **mêmes textes FR/EN** (clés `miff_*`, `feed_*`, `profile_tab_*` du catalogue, reprises d'Android), **mêmes écritures** (aucune règle Firestore modifiée) :

- **Créer** : « Nouvelle Miff » (nom de 2 à 40 caractères, texte de partage au-dessus de « Créer et partager »), puis « Ta Miff « X » est prête » (code par groupes de 4, Partager, Copier). **UN lot de 5 écritures** (Miff, code, fiche du créateur avec consentement daté et versionné, index, statut initial `unknown`) dans UNE transaction, code tiré à ce moment (`SecRandomCopyBytes`, échantillonnage par rejet : aucun biais de modulo), jusqu'à 3 tirages en cas de collision.
- **Rejoindre** : saisie du code (majuscules, clavier ASCII, ni correction ni suggestions, espaces et tirets normalisés, **bouton système « Coller » `PasteButton`** : aucune alerte de collage, jamais de lecture automatique du presse-papiers, message local sans réseau pour un caractère hors alphabet, pause d'une minute après 5 échecs), vérifications dans l'ordre du ticket §5.4 (format, lecture du code, nettoyage d'un index obsolète, déjà membre par le champ `miffId`), écran de confirmation avec le texte de partage **avant** « Rejoindre et partager », **UN lot de 3 écritures**. Rien n'est écrit avant l'appui.
- **Écran de la Miff** (créateur et membre) : nom modifiable par le créateur (lot Miff + nom du code), code et partage (`ShareLink`, feuille système), proches en direct, ligne « Partager mon statut » (arrêter / reprendre à tout moment : arrêt = fiche + suppression du statut et de la photo dans le même lot ; reprise = nouveau consentement + statut initial, dans une feuille qui montre le même cadre de consentement), lieux en liste, « Cette Miff est bien remplie ! » à 15 membres (plafond doux), lecture seule hors connexion.
- **Accès perdu** : détecté en direct (écoute refusée ou Miff inexistante) et à l'ouverture (contrôle auprès du serveur) ; **une erreur réseau n'en est jamais une**, et un refus n'est retenu que si le serveur répond par ailleurs. Un seul message, nettoyage (lot « Quitter », sinon l'index seul), purge du cache local, bascule vers l'état « Pas encore de Miff ».
- **Accueil provisoire** (destination « fil », à la place de l'ancien écran provisoire) : pastille avec le nom de la Miff (non touchable avec un compte gratuit ; sélecteur avec « + Rejoindre / + Créer » pour un Premium), carte « Mes Miffs » qui ouvre l'écran de la Miff (sur Android elle est dans l'onglet Profil), état « Pas encore de Miff » avec « Créer une Miff » / « Rejoindre une Miff », bouton « Se déconnecter » (faute d'écran Réglages). L'écran « Choisis comment tu veux commencer » remplace l'ancien écran provisoire de l'onboarding.
- **Architecture** : `Models/Miff/` (Swift pur, testé), `Services/Contracts/MiffContracts.swift` (`MiffStore`, `MiffObserver`, `MiffRepository`), `Services/Miff/MiffRepository.swift` (toute la logique, derrière `MiffStore`) et `MiffDirectory` (les Miffs en mémoire pour la session ; l'UNIQUE endroit où une perte d'accès est traitée), `Services/Firebase/FirestoreMiffStore.swift` et `FirestoreMiffObserver.swift` (les adaptateurs Firestore), `ViewModels/` et `Views/Miff/`.

**Sécurité et confidentialité** (revue demandée par le ticket, critères V1, V2, T5) :
- Le code et le nom d'une Miff ne sont **jamais journalisés** (aucun `print`, aucun journal ; chaque type qui porte un nom, un code ou un profil a une `description` masquée), jamais dans un événement Analytics (`miff_created` et `miff_joined` ne portent que `is_first_miff`), jamais dans un message d'erreur (toute erreur est réduite à une catégorie : `MiffFailure`).
- **Aucun équivalent de `rememberSaveable`** : le code tapé ou affiché ne vit que dans l'état du view model (mémoire) ; aucune restauration d'état de l'interface ne le garde. Seul l'identifiant de la Miff voyage dans une destination de navigation.
- **Presse-papiers** : « Copier » met le code SEUL, **marqué « local seulement »** (pas de presse-papiers universel vers les autres appareils) avec une **expiration à 2 minutes** ; « Coller » est le bouton système (iOS 16), l'app ne lit jamais le presse-papiers.
- **Sélecteur d'applications** : les écrans qui montrent ou saisissent le code (saisie du code, « Ta Miff est prête », écran de la Miff) sont **recouverts** (fond de l'app et mot-symbole) dès que l'app n'est plus au premier plan : la vignette du sélecteur ne montre jamais le code (`hidesInAppSwitcher`, d'après `scenePhase`). **Non vérifié sur iPhone** (voir « Limites »).
- **Noms reçus du réseau nettoyés** (`MiffName.clean`) : nom de la Miff lu dans `joinCodes` ET dans `miffs`, noms et prénoms des membres, noms des lieux (caractères de contrôle, contrôles bidirectionnels, espaces de largeur nulle) ; photos de plus de 20 000 octets ignorées ; tout identifiant lu sur le réseau est vérifié (20 lettres et chiffres, ou « free ») avant de devenir un morceau de chemin.
- **Écritures transactionnelles** : toutes les écritures sont des transactions (`runTransaction`) : « pas de confirmation » veut toujours dire « rien d'écrit », jamais de file d'attente rejouée plus tard ; le succès ne s'affiche qu'après l'accusé du serveur ; un lot parti dont la réponse s'est perdue est vérifié auprès du serveur avant d'être appelé un échec.
- **Purge du cache** à la déconnexion (déjà en place, `SessionManager`) ; l'annuaire des Miffs fait partie des arrêts de la session et oublie Miffs, message et choix. **Démarrage à froid** : rien de MIFF-3 n'ouvre Firestore avant la session (le dépôt, le magasin et l'écoute ne résolvent `Firestore.firestore()` qu'à la première requête d'un écran affiché après le routage ; une purge restée en attente passe toujours avant).
- **Une sortie volontaire d'une Miff ne renouvelle PAS le code** (décision du PM du 2026-10-31) : rien dans MIFF-3 ne renouvelle le code (le nettoyage après une perte d'accès ne touche pas au code) ; MIFF-4 ne devra pas le faire non plus pour « Quitter ».
- Budget des lectures de règles : mêmes lots qu'Android (créer 5 écritures, rejoindre 3, renommer 2, arrêter 3, reprendre 2, quitter 4) ; **rien n'est ajouté** à un lot.

**Marges et zones sûres** : tous les écrans défilent dans un `ScrollView` qui respecte les zones sûres (encoche, barre d'accueil) ; seuls les FONDS passent sous les barres (`ignoresSafeArea` sur le fond uniquement) ; le clavier fait remonter le contenu (`scrollDismissesKeyboard`). Vérifiable sur les captures de la CI (`50-*` à `67-*`, dont deux avec la plus grande taille de texte) et à relire sur l'iPhone 11 Pro.

### Écarts avec les maquettes (MIFF-3, listés dans la PR)

1. **Titre de l'écran = nom de la Miff** (la maquette garde « Nouvelle Miff »), **un seul bouton de validation** (la coche en haut à droite est retirée), **pas de corbeille rouge ni de « Supprimer cette Miff »** (MIFF-4).
2. **Le code n'apparaît pas sur « Nouvelle Miff »** : création en deux temps (décision QM15) ; la section « Lieux de confiance » de la création est retirée (les lieux n'existent qu'une fois la Miff créée).
3. **Le champ du code sort de la carte de choix** vers sa propre page (un seul champ de code, ticket §4 n° 9) ; le code s'affiche par groupes de 4 ; « Rejoindre » devient « Continuer » puis, sur l'écran de confirmation, « Rejoindre et partager ».
4. **Texte de partage ajouté** avant « Créer et partager » et « Rejoindre et partager » (décision QM2), avec ce que voient les membres, la phrase sur les lieux et le rappel sur la permission du téléphone.
5. **Tutoiement** à la place du vouvoiement ; la faute « saisit » de « Le code saisit est invalide » est supprimée ; mot « irrévocable » absent.
6. **Écrans non dessinés ajoutés**, d'après les fiches `design/screens/miff-*.md` : « Ta Miff est prête », confirmation de rejoindre, écran de la Miff vu par un membre, feuilles « Arrêter / Reprendre le partage », message d'accès perdu, états de chargement, d'erreur et hors connexion.
7. **Plateforme (HIG)** : flèche de retour dessinée dans l'en-tête ET geste de balayage natif ; zones tactiles de 44 pt ; `PasteButton` du système à la place du lien « Coller » ; `ShareLink` pour partager ; interrupteur natif (`Toggle`) pour « Partager mon statut » ; fenêtres de confirmation présentées en **feuilles** (une feuille peut porter le cadre de consentement de « Reprendre le partage », une alerte ne le peut pas) ; icônes de section en SF Symbols (`pencil`, `lock`, `person.2`, `mappin.and.ellipse`) au lieu des icônes du design.
8. **Balayage de retour coupé** pendant l'enregistrement, sur « Ta Miff est prête » (la Miff existe : « Continuer » est la sortie) et sur la confirmation de rejoindre (la sortie est « Annuler », qui ramène à la saisie du code, pas à l'écran précédent).
9. **Texte « Tu retrouveras ce code dans ta Miff, depuis l'onglet Profil »** remplacé sur iOS par `ios_miff_ready_hint` (« ... depuis l'écran d'accueil ») : il n'y a pas d'onglet Profil sur iOS.
10. **Pas de bandeau vert « Tout le monde est en sécurité »** sur l'accueil sans Miff ni avec une Miff (décision Q12 : l'app ne dit jamais « tout va bien » quand elle ne le sait pas).

### Limites connues (MIFF-3)

- **Rien n'est compilé ni testé en local** (poste Windows) : tout est validé par la CI. Le rendu réel (marges, vignette du sélecteur d'applications, `PasteButton`, `ShareLink`, feuilles, VoiceOver) **n'est vérifiable que sur l'iPhone** (checklist `docs/qa/checklists/miff-3-ios-recette-pm.md`).
- **`locationAccess` est toujours écrit `unknown`** : l'app ne demande pas la permission de localisation (ticket Localisation) et ne lie pas la bibliothèque de localisation ; tant qu'elle ne l'a jamais demandée, c'est l'état réel. Le ticket Localisation lira l'autorisation du système (`denied`, `foreground`, `background`).
- **Le lien du message de partage est la racine du site public** (`https://miff-3d283.web.app/`) ; la page d'atterrissage dédiée (QM14) viendra avec le site.
- **Édition au milieu du code** : le champ affiche le code par groupes de 4 en le réécrivant ; modifier un caractère au milieu fait sauter le curseur à la fin (SwiftUI n'a pas de transformation d'affichage). La frappe en fin de champ, le collage et la suppression en fin de champ ne sont pas touchés.
- **Le bouton système « Coller » est grisé quand le presse-papiers ne contient pas de texte** (comportement d'iOS) ; sa langue est celle du téléphone.
- **Aucune règle côté serveur n'impose les 15 membres** (plafond doux, comme Android) ; le coût en lectures croît avec le nombre de membres (voir `backend/data-model.md` §12).
- **Le lot « Créer et partager » d'un compte Premium qui garde un ancien emplacement gratuit** est à 19 appels de règles sur 20 au pire (marge de 1, voir `android/CHANGELOG.md` MIFF-1) : aucun contrôle ne doit être ajouté à ce lot.
- **Mêmes limites qu'Android (revues de la PR #45)** : (M2) le délai de 20 s d'une écriture arrête l'attente de l'app, pas la transaction : une écriture lente peut aboutir après le message d'échec (l'app relit le serveur avant de parler d'échec, mais une transaction qui aboutit après cette vérification reste possible) ; (M4) tout refus des règles à « Rejoindre » affiche « On ne trouve pas de Miff avec ce code » (emplacement gratuit pris entre-temps, conditions absentes ou App Check compris), et le limiteur ne compte pas ces cas ; (M5) un refus d'App Check et un refus des règles se confondent (même phrase générique) ; (F1) si la lecture de l'abonnement échoue, le compte est traité comme « non Premium » : un compte Premium peut alors se voir répondre « une seule Miff à la fois » à tort sur un réseau instable.
- **Noms venus du serveur** : le nom d'un code (écrit par son détenteur AVANT tout consentement du lecteur) et les noms de lieux passent par `MiffName.fromServer` ; sans nom visible, le code est traité comme inconnu et le lieu n'est pas affiché. Un nom du serveur n'est pas coupé à 40 unités (seules les règles le bornent).

## Confidentialité : Analytics et Crashlytics

- `Info.plist` : `FIREBASE_ANALYTICS_COLLECTION_ENABLED = NO` (la clé irréversible `..._DEACTIVATED` n'est **pas** utilisée), `FirebaseCrashlyticsCollectionEnabled = NO`, `FirebaseDataCollectionDefaultEnabled = NO` (collecte par défaut éteinte pour tous les SDK Firebase ; chaque SDK est réactivé explicitement après accord), `GOOGLE_ANALYTICS_IDFV_COLLECTION_ENABLED = NO`, signaux publicitaires et attribution désactivés, `FirebaseAutomaticScreenReportingEnabled = NO` (aucun nom d'écran dans Analytics).
- SDK utilisé : **`FirebaseAnalyticsCore`** (sans identifiant publicitaire). La CI vérifie sur le binaire construit que `AdSupport` et `AppTrackingTransparency` ne sont pas liés (Debug et Release : vert).
- Le choix qui compte est celui du **compte** (`users/{uid}.analyticsConsent`, ticket Authentification §8) : à chaque démarrage à froid la collecte est seulement **mise en pause** (`pauseCollection` : le rapport du plantage précédent reste sur le disque), et rien ne se rallume avant que le compte ait été lu, ou avant le geste « Rejoindre » / « Continuer » (qui vaut acceptation, décision du PM du 2026-10-28). La valeur gardée sur l'appareil (`PrivacySettings`, `UserDefaults`) n'est **plus** réappliquée au lancement. Retrait du consentement, déconnexion, refus du panneau : coupure des deux collectes ET effacement des rapports pas encore envoyés. L'écran de diagnostic (Debug et Internal) reste un second moyen d'allumer les collectes.
- Tout événement passe par `AnalyticsTracker` (point d'entrée typé `AnalyticsEvent`), qui applique la **liste blanche** de `docs/contracts/analytics-events.md` (version 1) implémentée dans `Models/Analytics/AnalyticsContract.swift`, avec les mêmes cas de test qu'Android. Aucun événement ne porte de position.
- **Ce que la liste blanche ne couvre pas** (idem Android) : une fois Analytics activé, le SDK envoie lui-même des événements automatiques (`first_open`, `session_start`...) et Google déduit une zone géographique approximative de l'adresse IP. À dire dans la politique de confidentialité ; réglage GA4 à faire par le PM (voir le contrat).
- **Non prouvé** : l'absence totale de trafic réseau Firebase avant consentement (Installations, App Check, Firestore peuvent émettre des appels techniques sans lien avec Analytics). Il faudrait observer le trafic d'un iPhone (proxy).
- **Aucune clé de localisation** (`NSLocation*`), aucune exception ATS, `ITSAppUsesNonExemptEncryption = false` (chiffrement standard seulement, décision PM du 2026-10-22). Ces points sont testés (`AppConfigurationTests`) et contrôlés sur l'`Info.plist` du build par la CI.
- `PrivacyInfo.xcprivacy` : pas de suivi, API à raison obligatoire déclarées : `UserDefaults` (raison CA92.1) et horloge du système `systemUptime` (catégorie `SystemBootTime`, raison 35F9.1, durées internes de l'app ; le test `PrivacyManifestApiTests` compare le manifeste aux appels réellement présents dans le code) ; données collectées *seulement après accord* : interactions produit, identifiant d'instance, plantages, diagnostics. **À faire relire par le release-manager** avant la soumission (la fiche « confidentialité » d'App Store Connect doit correspondre ; les SDK Firebase embarquent leurs propres manifestes).

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

Dépendances déclarées (versions exactes) : **firebase-ios-sdk 13.1.1** (produits `FirebaseCore`, `FirebaseFirestore`, `FirebaseAuth`, `FirebaseAppCheck`, `FirebaseCrashlytics`, `FirebaseAnalyticsCore`), **GoogleSignIn-iOS 10.1.0** (connexion Google) et **PhoneNumberKit 5.0.13** (numéros de téléphone). Les 14 autres paquets de `Package.resolved` (17 en tout) sont des dépendances indirectes. Licences d'après les dépôts amont (non relues fichier par fichier ici).

| Dépendance | Version | Licence |
|---|---|---|
| firebase-ios-sdk | 13.1.1 | Apache-2.0 |
| GoogleSignIn-iOS | 10.1.0 | Apache-2.0 |
| AppAuth-iOS (via GoogleSignIn) | 3.0.0 | Apache-2.0 |
| GTMAppAuth (via GoogleSignIn) | 6.0.0 | Apache-2.0 |
| PhoneNumberKit | 5.0.13 | MIT |
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
