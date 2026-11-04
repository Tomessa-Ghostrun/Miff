# Changelog iOS

## Non publié

### MIFF-3 - Créer, rejoindre, écran de la Miff, partage du statut, accès perdu (miroir d'Android MIFF-1)

Tickets `docs/tickets/miff.md` (§8, §16), décisions du PM `miff-questions-pm.md` (QM1 à QM15) et `feed-profil-reglages.md` §13 ; maquettes `design/exports/miff/`. Retirer, quitter, supprimer, renouveler le code et « Prévenir mes proches » sont **MIFF-4** : aucun bouton n'est dessiné pour eux. Détail de l'architecture, de la sécurité, des écarts avec les maquettes et des limites : `README.md`, section « Miffs ».

Ajouté :
- **Écran de choix** « Choisis comment tu veux commencer » (créer / rejoindre ; l'onboarding n'est complet qu'avec une Miff) à la place de l'écran provisoire ; un lien discret « Se déconnecter ».
- **Créer** en deux temps (nom 2 à 40 caractères avec texte de partage, puis « Ta Miff « X » est prête » : code par groupes de 4, Partager, Copier). **UN lot de 5 écritures** dans UNE transaction, code tiré à l'enregistrement par `SecRandomCopyBytes` (échantillonnage par rejet, jusqu'à 3 tirages).
- **Rejoindre** : saisie du code (majuscules, clavier ASCII, `PasteButton` système, message local pour un caractère hors alphabet, pause d'une minute après 5 échecs), vérifications dans l'ordre du ticket §5.4, écran de confirmation avec le texte de partage avant « Rejoindre et partager », **UN lot de 3 écritures**.
- **Écran de la Miff** (créateur et membre) : nom modifiable (créateur), code et partage (`ShareLink`), proches en direct (trois écoutes mises en une image), « Partager mon statut » (arrêter 3 écritures / reprendre 2, en feuille avec le même cadre de consentement), lieux, plafond doux de 15, lecture seule hors connexion (`NetworkMonitor.onlineChanges()` ajouté).
- **Accès perdu** : détecté en direct et à l'ouverture, **jamais sur une erreur réseau**, un seul message, nettoyage (lot « Quitter », sinon l'index seul), purge du cache.
- **Accueil provisoire** (`MiffHomeHost`) : pastille de la Miff (sélecteur Premium), carte « Mes Miffs », « Pas encore de Miff », déconnexion. `SessionManager.onMiffEntered` / `continueAfterMiff` (fin d'onboarding comptée une fois, `miff_created` / `miff_joined` avec `is_first_miff`).
- **Sécurité** : code via `SecRandomCopyBytes` ; aucun journal ; aucun état de restauration du code ; presse-papiers « local seulement » avec expiration à 2 minutes ; vignette du sélecteur d'applications masquée sur les écrans qui montrent le code ; `MiffName.clean` sur TOUT nom reçu du serveur (noms de Miff de `joinCodes` et de `miffs`, noms des membres, noms de lieux) ; identifiants lus sur le réseau vérifiés avant de devenir un chemin ; démarrage à froid : aucun accès Firestore avant la session ; annuaire vidé à la déconnexion.
- **Reprise du second passage des revues d'Android** (PR #45) : `MiffName.fromServer` pour le nom d'un code et les noms de lieux (un nom sans rien de visible = code inconnu, lieu non montré) ; « Arrêter / Reprendre le partage » refusé parce que la fiche est déjà dans l'état voulu (autre téléphone) = succès ; un écran de la Miff qui passe en arrière-plan pendant la vérification d'un accès perdu redevient « chargement » et entend la perte de nouveau au retour (pas de page blanche).
- **Textes** : clés `miff_*`, `feed_*`, `profile_tab_*` du catalogue (reprises d'Android) ; ajout de `ios_miff_ready_hint` ; retrait de `ios_next_miff_choice` et `ios_next_feed` (écran provisoire supprimé). `LocalizationTests` contrôle aussi les clés `miff_` et `feed_` du code.
- **Tests** : code (alphabet, 1 000 tirages, biais, normalisation), nom (émojis et accents non coupés, contrôles bidirectionnels), pause des essais, contenu exact des lots (clés des règles, dates serveur, aucune coordonnée), interprétation des erreurs (réseau ≠ accès perdu), dépôt (ordre des vérifications, nettoyage avant écriture, atomicité, hors connexion sans file d'attente, collision de code, clé de l'index gratuit / Premium, lot dont la réponse s'est perdue), annuaire (message unique, purge, pas de boucle, fin de session), view models (créer, rejoindre, écran de la Miff, hors connexion, double appui), session (fin d'onboarding). Captures CI des nouveaux écrans (`-MiffPreview miff-*`, 16 + 2 avec la plus grande taille de texte). Checklist de recette `docs/qa/checklists/miff-3-ios-recette-pm.md`.

Changé : `NetworkMonitor` (nouvelle méthode `onlineChanges()`), `LabeledFieldOptions` (style du texte saisi), `FirestoreErrorNumber` (code « quota épuisé »), `Spacing` (tailles du flux Miff), bouton secondaire.

**Choix économiques** : aucun service, aucune dépendance, aucune permission ajoutée ; 0 €. Aucune règle Firestore modifiée. Lectures : ouverture de l'accueil = 1 lecture des appartenances + 1 par appartenance + 1 de l'abonnement ; écran de la Miff = 3 écoutes (Miff, membres, lieux), arrêtées en arrière-plan ; une seule lecture de l'accueil par session (pas une à chaque retour sur l'écran).

**Limites connues** : `locationAccess` toujours `unknown` (l'app ne demande pas encore la localisation) ; lien de partage = racine du site public ; curseur qui saute en modifiant le milieu du code ; rendu, vignette du sélecteur d'applications, `PasteButton`, `ShareLink`, VoiceOver et zones sûres **à vérifier sur l'iPhone** (rien n'est compilé en local).

### AUTH-3, AUTH-4, ONB-5, ONB-6 - Parité Android de l'authentification et de l'onboarding

Ajouté :
- **AUTH-3** : inscription et connexion par e-mail et mot de passe, **Google** (GoogleSignIn-iOS 10.1.0) et **Apple** (natif, nonce SHA-256) ; routage par état du compte (`SessionManager`) ; panneau de consentement pour une personne nouvelle arrivée de l'écran de connexion (le refuser supprime le compte tout juste créé) ; consentement sans case à cocher (le bouton vaut acceptation) ; collecte Analytics et Crashlytics seulement mise en pause au démarrage puis réglée par le choix du compte ; purge du cache Firestore à la fin d'une session ; limite de confort après 5 échecs de connexion.
- **AUTH-4** : « Mot de passe oublié » (confirmation identique pour toute adresse, pause de 60 s par adresse).
- **ONB-5** : icône officielle, mot-symbole vectoriel, carrousel de bienvenue (illustrations reconstruites en vectoriel et en vrais textes), écran de lancement système + animation (le cœur gonfle puis éclate, 2,9 s ; version « réduire les animations » 0,8 s).
- **ONB-6** : étape profil (photo par le sélecteur du système sans permission, recadrage carré, JPEG ré-encodé sans métadonnées, 20 000 octets au plus), étape téléphone (PhoneNumberKit 5.0.13, E.164 obligatoire), brouillon local, écriture unique par transaction.
- Dépendances : GoogleSignIn-iOS 10.1.0 (Apache-2.0), PhoneNumberKit 5.0.13 (MIT), et leurs dépendances indirectes (voir `README.md`). Entitlement « Se connecter avec Apple ». Phase de compilation qui ajoute le schéma d'URL de Google depuis le fichier de configuration embarqué (jamais dans le dépôt).
- Textes : catalogue fr + en, parité stricte avec Android (clés `ios_*` propres à iOS), `PrivacyInfo.xcprivacy` complété (données de compte).
- Tests de la logique (session, routage, formulaires, view models, numéro, photo, chronologie du lancement...) ; checklist de recette `docs/qa/checklists/ios-auth-onboarding-recette-pm.md`.

Changé : `HomeView` utilise le mot-symbole officiel ; le diagnostic ouvre le vrai parcours de l'app ; `PrivacySettings` ne réapplique plus le choix gardé sur l'appareil au démarrage ; l'icône et le logo ne sont plus provisoires (seul le « G » de Google reste un redessin à remplacer avant la fiche de l'App Store). `tools/generate-app-icon.mjs` supprimé.

**CI** : 357 tests, 0 échec, SwiftLint strict vert, Release et Internal compilées, 34 captures relues (run 41115100528, commit c69c8b9). Le job du simulateur a duré 21 à 25 minutes sur les machines actuelles (le temps est surtout passé AVANT le démarrage des tests, 6 à 14 minutes, et non dans les tests eux-mêmes : 14 secondes) ; la limite du job (relevée à 30 minutes sur `main` par la PR #44) était donc trop juste à 15 minutes ; une exécution a été coupée à 25 minutes pendant les captures (relancée une fois, verte). À surveiller : si cela se reproduit, séparer les captures dans un second job. Package.resolved régénéré (17 paquets) ; les versions des dépendances déjà présentes sont inchangées.

**Corrections de revue (2026-10-31)** :
- I1 : `PrivacyInfo.xcprivacy` déclare `SystemBootTime` (raison 35F9.1, `ProcessInfo.systemUptime` dans `AppClock`) ; le test `PrivacyManifestApiTests` compare le manifeste aux appels de chaque catégorie à raison obligatoire (horloge, UserDefaults, horodatages de fichiers, espace disque, claviers actifs) réellement présents dans le code.
- I2 : le build **Internal** (TestFlight) s'ouvre sur le vrai parcours ; le diagnostic reste derrière un appui long de 1,5 s en haut à gauche, dans une feuille (pas de navigation imbriquée : le balayage depuis le bord ne peut pas y ramener). Le lien « vrai parcours » du diagnostic n'existe plus qu'en Debug.
- I3 : pas d'écran Réglages sur iOS : le texte de consentement iOS (`ios_auth_consent_notice`, `ios_auth_panel_notice`) ne promet plus « modifiable dans les Réglages » mais « à retirer à tout moment en écrivant à » l'adresse de contact ; limite et suite documentées (README, écart n° 10).
- M1 le nom donné une seule fois par Apple n'est plus écrasé par une connexion sans nom (`AppleNameMemory`) ; M2 une déconnexion qui échoue est retentée et aucune connexion ne démarre tant qu'une ancienne session reste ouverte ; M3 le code d'autorisation d'Apple est capturé (mémoire seulement, jamais journalisé) pour la révocation future (AUTH-5) ; M4 le balayage de retour est coupé pendant l'écriture du profil (`SwipeBackLock`) ; M5 barre « OK » au-dessus du pavé numérique ; M6 phrase sur PhoneNumberKit corrigée (dépôt historique non archivé, plus de version depuis la 4.3.0) ; M7 tests ajoutés (manifeste, nom Apple, session après réinstallation, déconnexion qui échoue, script du schéma d'URL Google avec des fichiers factices : `ci/test-google-url-scheme.sh`, étape de la CI macOS).
- Q1 : au **premier lancement d'une installation**, une session trouvée (Trousseau conservé après désinstallation) est fermée avant tout routage : après une réinstallation il faut se reconnecter (recommandation de la revue, appliquée sauf veto du PM). Q3 : textes « Zone à risque / Lieu à éviter » du carrousel inchangés (décision du PM en attente).

**Prérequis côté PM pour tester** : capacité « Sign in with Apple » sur l'identifiant `com.tomessa.miff` et fournisseur Apple dans Firebase Authentication ; régénérer `GoogleService-Info.plist` après l'activation de Google et mettre à jour le secret `GOOGLE_SERVICE_INFO_PLIST_B64`.

**Choix économiques** : aucun service payant ajouté ; PhoneNumberKit (local) plutôt qu'un service de vérification (aucun SMS) ; sélecteur de photos du système (aucune permission) ; aucune Cloud Function.

**Non vérifié (seulement possible sur iPhone ou avec des comptes réels)** : rendu et fluidité sur iPhone 11 Pro, fenêtres Google et Apple, signature avec la nouvelle capacité, VoiceOver, iOS 16.

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
- **Durées après revue** (cache des paquets, commit `3cdeeed`, run 40103131819, 99 tests, 0 ignoré) : job **6 min 19 s** (résolution 13 s ; SwiftLint 2 s ; compilation Debug 44 s ; tests 58 s ; compilation Release 51 s ; compilation **Internal** 48 s ; captures 110 s). Une PR qui ne change ni l'interface, ni les ressources, ni le projet saute Release, Internal et captures (environ 3,5 min de moins) ; une PR qui ne touche que des `.md` ne lance plus rien. Quota : ~6,5 min facturées par exécution complète, multiplicateur macOS probable x10 (à vérifier).

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
