# Ticket — Initialisation technique

**Statut** : Brouillon pour challenge et validation PM (aucun développement lancé)
**Rédigé le** : 2026-10-16 (agent `product-specs`)
**Remplace** : le ticket Notion « Initialisation technique » (son contenu est repris et complété ici ; Notion n'a pas été modifié)
**Sources lues** : `CLAUDE.md`, `design/design-system.md`, `backend/data-model.md` (dont §15), `backend/firestore.rules` (survol), `backend/README.md`, `backend/rules-test-plan.md`, toutes les `docs/specs/*.md`.
**Convention** : « vérifié » = confirmé sur la documentation officielle le 2026-10-16. « À vérifier » = non confirmé, ne pas s'y fier avant contrôle.

---

## 1. Objectif et valeur

Ce ticket pose les fondations invisibles de Miff : une app Android et une app iOS « vides mais saines », branchées au même projet Firebase, avec le design system, les traductions, la sécurité de base et une chaîne de build automatique déjà en place. Aucun écran utilisateur n'est livré : à la fin, chaque ticket suivant (inscription, Miff, lieux...) n'a plus qu'à construire son écran sur un socle commun, au lieu de réinventer les bases deux fois. La valeur pour le PM est de lever tôt les gros risques (iOS compilé sans Mac, signature et TestFlight, tests des règles Firestore jamais exécutés) et de pouvoir tenir une première version de l'app sur son téléphone.

---

## 2. Périmètre

### Dans le périmètre
- Création des projets `android/` et `ios/` (identifiant `com.tomessa.miff`), qui compilent et démarrent.
- Connexion à Firebase (`miff-3d283`) : Firestore, Authentication (SDK seulement), App Check, Crashlytics, Analytics (désactivés par défaut).
- Design system en code : couleurs, typographies, arrondis, ombres, dégradés.
- Infrastructure de traduction (aucune chaîne en dur) et préparation à l'écriture droite-à-gauche.
- Icône d'application provisoire ou définitive selon les fichiers fournis.
- Un écran de diagnostic interne (« smoke test ») qui prouve que l'app parle bien à Firestore, avec les règles de sécurité actives.
- CI GitHub Actions : build et tests Android, build iOS, tests des règles Firestore à l'émulateur.
- Gestion des fichiers de configuration Firebase et des secrets sans les versionner.
- Lint/format, modèle de pull request, documentation de démarrage.

### Hors périmètre
- Toute fonctionnalité visible par un utilisateur final : onboarding, inscription/connexion (écrans), Miffs, lieux, feed, update, réglages, paywall.
- Géolocalisation, géofencing, demandes de permissions de localisation (aucune permission sensible n'est déclarée dans ce ticket).
- Notifications push (FCM, clé APNs, jetons d'appareil).
- Paiement réel, achats intégrés, validation d'abonnement.
- Cloud Functions et passage au plan Blaze (décision PM : Spark, aucune fonction).
- Soumission aux stores, fiches store, captures marketing, formulaire « Background Location », fiche Data Safety.
- Passage de l'application App Check en mode strict (décision séparée, voir §3).
- Écran de consentement Analytics pour l'utilisateur final (le mécanisme est livré, l'écran vient avec l'onboarding).

---

## 3. Décisions techniques actées

| Sujet | Décision | Origine |
|---|---|---|
| Android | Kotlin + Jetpack Compose, natif. `minSdk` 26 (Android 8.0). `targetSdk` et `compileSdk` = 36 (exigence Google Play pour toute nouvelle app et mise à jour depuis le 2026-08-31, extension possible jusqu'au 2026-11-01 ; vérifié) | PM 2026-08-30 et 2026-10-16 |
| iOS | Swift + SwiftUI, déploiement minimum **iOS 16**, compilé avec Xcode 26.x | PM 2026-10-16 |
| Postes de travail | Windows sans Mac. Android se compile et se teste en local (Android Studio, 8 Go de RAM). iOS ne se compile qu'en CI macOS et ne se teste que sur simulateur CI et sur l'iPhone du PM via TestFlight | PM |
| Identifiant d'app | `com.tomessa.miff` (applicationId Android = bundle ID iOS). Définitif une fois publié. Pas de suffixe `.debug` (il faudrait enregistrer une seconde app Firebase) | CLAUDE.md |
| Firebase | Projet `miff-3d283`, Firestore `europe-west9`, plan **Spark**, aucune Cloud Function. Le projet `ghostrun-app` n'est jamais ciblé ; toute commande porte `--project miff-3d283` | PM / CLAUDE.md |
| Authentification de lancement | E-mail/mot de passe + Google + Apple. Pas de SMS. Le mot de passe reste dans Firebase Auth, jamais dans nos données | PM |
| Schéma et règles | `backend/` fait foi (premium par utilisateur, photos facultatives, réciprocité stricte, lieux créés par le créateur de la Miff, zone de danger = premium) | `data-model.md` §15 |
| Langues | Français = langue source. Infrastructure multilingue complète dès ce ticket. Liste des langues : décision PM (voir §4.D et §9) | PM |
| Analytics et diagnostic | Désactivés par défaut. Jamais de position (ni coordonnées, ni nom de lieu, ni adresse) dans un événement | CLAUDE.md |
| Processus | Challenge, validation PM, dev Android puis iOS (une branche et une PR par plateforme, jamais de push sur `main`), revue de code en contexte neuf + revue sécurité, QA, recette PM, merge | CLAUDE.md |

### Écarts corrigés par rapport au ticket Notion d'origine

| Ticket d'origine | Corrigé en | Pourquoi |
|---|---|---|
| Android : Kotlin Multiplatform | Android natif Kotlin + Compose | Décision PM (2026-08-30, reconfirmée 2026-10-16) : deux apps natives séparées, sans code partagé. |
| iOS 13 minimum | iOS 16 minimum | Le SDK Firebase iOS actuel (13.0.x, 7 oct. 2026) exige iOS 15 au minimum et Xcode 26.2 : iOS 13 est impossible. iOS 16 donne les API SwiftUI modernes (`NavigationStack`) et écarte les iPhone les plus anciens (iPhone 7 et antérieurs ne vont pas au-delà d'iOS 15, connaissance générale). |
| Cloud Functions 2nd gen / Cloud Run (validation du code, geofencing, statut, fan-out de notifs) | Aucune fonction | Pas de plan Blaze (décision PM). Le code secret est validé par les règles, le statut vert/orange/rouge est calculé par chaque app, et le fan-out de notifications est repoussé (il exige Blaze, `data-model.md` §13). |
| Auth « phone auth suggéré » | Pas d'authentification par SMS | Décision PM : e-mail + Google + Apple. La connexion par téléphone n'est de toute façon pas disponible sur Spark (indiqué « non applicable » sur la page de tarifs Firebase ; vérifié). Le numéro reste un champ de profil (voir §6). |
| Modèle User avec « mot de passe » | Mot de passe jamais stocké dans nos données | Il est géré (haché) par Firebase Authentication ; le dupliquer serait une faille sans bénéfice. |
| Modèle Lieu avec « Miff associé (LISTE de Miffs) » | Schéma actuel : un lieu appartient à une seule Miff | Conflit avec le schéma actuel et avec la spec `lieux-de-confiance.md` (hors-scope « partage entre Miffs »). À trancher dans le ticket « Lieux de confiance », pas ici (voir §6). |

### Contraintes techniques vérifiées (2026-10-16)

| Sujet | Constat | Statut |
|---|---|---|
| Firebase iOS SDK | v13.0.x : iOS 15.0 minimum, Xcode 26.2 minimum ; la v13 ne se résout plus avec un Xcode plus ancien. Compatible avec iOS 16 | Vérifié (notes de version Firebase) |
| Firebase Android | Doc d'installation : API 24 minimum, AGP 7.3.0 minimum, BoM affichée 35.0.1, plugin `google-services` 4.5.0. `minSdk` 26 respecte tout | Vérifié |
| Compose | Version stable 1.12.1 (2026-09-09). Le `minSdk` exact de Compose n'est pas apparu sur les pages consultées (le modèle de projet propose API 21) ; 26 est au-dessus de toute valeur plausible | Partiellement vérifié : à vérifier lors de l'installation |
| Google Play | Cible API 36 exigée depuis le 2026-08-31 pour les nouvelles apps et mises à jour | Vérifié |
| Apple | Depuis 2026-04-28, tout envoi vers App Store Connect (donc TestFlight) exige Xcode 26 + SDK iOS 26. Depuis 2026-09-09, la cible de déploiement doit être iOS 13 ou plus. Les deux sont compatibles avec iOS 16 | Vérifié (page « Upcoming Requirements ») |
| Runners GitHub | `macos-26` (arm64) avec Xcode 26.0.1 à 26.6 (défaut 26.6) et simulateurs iOS 26.2, 26.4, 26.5 ; `macos-14` obsolète ; `ubuntu-latest` = 24.04 ; une image Xcode 27 en préversion existe | Vérifié |
| Quotas GitHub Free (dépôt privé) | 2 000 minutes/mois, 500 Mo d'artefacts, 10 Go de cache par dépôt. Tarif payant : macOS 0,062 $/min contre 0,006 $/min pour Linux 2 cœurs (environ 10 fois). Sans moyen de paiement, l'usage est bloqué à épuisement du quota. **La façon dont une minute macOS consomme les 2 000 minutes gratuites n'est pas précisée sur les pages consultées** : historiquement un multiplicateur de 10 (soit environ 200 minutes macOS par mois), non confirmé | Partiellement vérifié : **à vérifier** |
| Protection de branche | Les pages GitHub consultées ne listent pas les branches protégées ni les relectures obligatoires pour un dépôt privé en plan Free (listées sous Pro). Le scan de secrets avec blocage au push exige « GitHub Secret Protection » | **À vérifier** |
| TestFlight | 100 testeurs internes (utilisateurs App Store Connect), 10 000 externes, build utilisable 90 jours ; le premier build d'un groupe externe passe en revue Apple | Vérifié |
| Xcode Cloud | 25 heures de calcul/mois incluses avec l'Apple Developer Program. La configuration d'un workflow est documentée **via Xcode** (donc un Mac) ; la modification/lancement ensuite depuis le web | Vérifié partiellement : à vérifier si la création initiale peut se faire sans Mac |
| Play Console | Compte personnel créé après le 2023-11-13 : test fermé de 12 testeurs pendant 14 jours consécutifs avant l'accès à la production | Vérifié |
| Émulateur Android sous Windows | WHPX (Windows Hypervisor Platform) recommandé ; AEHD sera abandonné le 2026-12-31 | Vérifié |
| Exigences machine Android Studio | 8 Go de RAM minimum / 16 Go recommandés : valeurs habituelles, non lues sur la page officielle | **À vérifier** |
| Firebase Spark | App Distribution, Crashlytics, App Check, Analytics sans coût ; Cloud Storage non disponible ; Phone Auth non disponible | Vérifié |
| Quota App Check selon le fournisseur (Play Integrity, App Attest) | La page tarifs indique « selon quotas variables par fournisseur » sans chiffre | **À vérifier** (avant l'application stricte) |
| Émulateur Firestore | Doc : Node 16+, Java JDK 11+, firebase-tools 8.14+. Les versions récentes de l'outil peuvent exiger plus : valeurs probablement dépassées | **À vérifier** |
| Analytics iOS | `FIREBASE_ANALYTICS_COLLECTION_ENABLED = NO` puis `setAnalyticsCollectionEnabled(true)` après consentement. La clé `…_DEACTIVATED` est irréversible à l'exécution : ne pas l'utiliser. Produit SPM `FirebaseAnalyticsCore` (sans identifiant publicitaire) | Vérifié |
| Analytics et Crashlytics Android | Mécanisme équivalent (métadonnées du manifeste désactivant la collecte + activation à l'exécution) ; noms exacts de clés non lus | **À vérifier** par le dev dans la doc |
| App Check débogage | Fournisseur « debug » pour émulateurs et CI ; jeton à garder privé, jamais commité, à stocker en secret CI | Vérifié |
| Sign in with Apple via Firebase | Exige l'Apple Developer Program payant ; la doc liste Service ID, clé privée, Team ID | Vérifié ; nécessité exacte pour un flux 100 % natif iOS : à vérifier au ticket Inscription |
| Langue par app Android | Natif depuis Android 13 (API 33) ; retro-compatible via AppCompat 1.6+ ; génération automatique de `LocaleConfig` | Vérifié |
| Polices | Baloo Thambi 2 : Latin, Latin étendu, vietnamien, tamoul ; graisses 400 à 800 variables ; pas d'italique ; licence OFL. Hanken Grotesk : Latin, Latin étendu, vietnamien, cyrillique étendu ; graisses 100 à 900 + italique ; licence OFL. **Aucune des deux ne couvre l'arabe, le chinois/japonais/coréen, le devanagari ni le grec** | Vérifié (métadonnées Google Fonts) ; couverture du cyrillique de base : à vérifier |
| Icônes | Android : couches adaptatives de 108 dp, zone sûre de 66 dp, couche monochrome pour les icônes thématiques (Android 13+). iOS : maître 1024 x 1024 px, opaque, sans coins arrondis (appliqués par le système) ; Icon Composer (outil Mac) pour les icônes à couches iOS 26 | Android vérifié ; iOS en partie issu de connaissances générales : **à vérifier** |

---

## 4. Livrables

Les propositions d'architecture sont des **points de départ** : l'agent de dev peut proposer mieux avec une justification en une phrase dans la PR. Le PM n'a pas à trancher ces détails.

### 4.A Transverse

1. **Dépôt**
   - `.gitignore` complété : `google-services.json`, `GoogleService-Info.plist`, `*.p8`, `*.pem`, `key.properties`, `*.xcarchive`, `*.ipa`, `*.apk`, `*.aab`, `node_modules/`, sorties de build iOS/Android, jeton App Check de débogage. (Le fichier actuel ne couvre ni les fichiers de config Firebase, ni les clés `.p8` Apple.)
   - `.gitattributes` : fins de ligne LF forcées (poste Windows + CI Linux/macOS, sinon faux changements et scripts cassés).
   - `.editorconfig` commun.
2. **Fichiers de configuration Firebase : jamais dans le dépôt**
   - Principe : `google-services.json` (Android) et `GoogleService-Info.plist` (iOS) sont stockés encodés en base64 dans les **secrets GitHub** (gratuit : Settings > Secrets and variables > Actions). Les jobs CI les décodent au moment du build puis ils disparaissent avec le runner.
   - En local : chaque dev les régénère avec `firebase apps:sdkconfig <ANDROID|IOS> <APP_ID> --project miff-3d283` (App IDs dans `CLAUDE.md`), placés dans un chemin ignoré par git.
   - Pourquoi : ces fichiers contiennent des clés d'API Firebase qui sont des identifiants (Google précise que ce n'est pas un secret au sens strict) mais `CLAUDE.md` impose de ne pas les commiter tant qu'une règle n'est pas actée. La vraie protection reste les règles Firestore et App Check. Une fois ce ticket livré, le PM pourra décider de les versionner (dépôt privé) pour simplifier : option documentée, non retenue ici.
   - Limite à documenter : une PR venant d'un fork n'a pas accès aux secrets ; sans objet pour un dépôt privé à un seul contributeur.
3. **Tests des règles Firestore** (dans `backend/`, sans modifier `firestore.rules`)
   - Un test automatisé par cas de `backend/rules-test-plan.md` (sections 1 à 8), nommé avec son identifiant (R1, C8, J16, A10...), via `@firebase/rules-unit-testing` et l'émulateur Firestore.
   - Les tests utilisent un identifiant de projet de démonstration (préfixe `demo-`) : aucune requête ne peut partir vers `miff-3d283` ni `ghostrun-app`.
   - Mesure du nombre d'appels de lecture des règles par opération (limites 10 par requête simple, 20 par batch ; cas C17 et section 9 du plan).
   - Tout test en échec est remonté à `backend-data` ; ce ticket ne corrige pas les règles sans accord.
   - Cas A9 (deux comptes) : non testable, documenté. Section 9 du plan (App Check strict, absence de secret) : vérifiée à part (voir §5).
4. **GitHub Actions** (voir §4.E).
5. **Modèle de pull request** (`.github/PULL_REQUEST_TEMPLATE.md`) avec : objectif, ticket lié, plateforme, critères d'acceptation cochés, captures, risques, **choix « économique » documenté** (limite connue + signal de bascule payante, règle CLAUDE.md), vérifications sécurité (aucun secret, aucune règle ouverte, pas de position dans Analytics), impact sur le schéma (oui/non), checklist de revue.
6. **Dependabot** (alertes et mises à jour) pour Gradle, Swift Package Manager et GitHub Actions. Chaque mise à jour passe par une PR et la CI.
7. **Convention de clés de traduction commune** : mêmes clés en `snake_case` sur Android (`strings.xml`) et iOS (String Catalog), préfixe de fonctionnalité (`auth_`, `miff_`, `place_`, `common_`). Les clés propres à une plateforme (textes des permissions système) sont préfixées `ios_` ou `android_`. Un script en CI vérifie que les deux plateformes ont les mêmes clés en français.
8. **Contrat d'analytics partagé** : une liste blanche d'événements et de paramètres autorisés (nom, type). Tout paramètre hors liste, ou dont le nom évoque une position (`lat`, `lng`, `latitude`, `longitude`, `address`, `place`, `location`...), est refusé et testé unitairement sur les deux plateformes.
9. **Déploiement des règles Firestore** : n'a lieu qu'après (a) tests verts, (b) revue sécurité en contexte neuf, (c) accord explicite du PM. Il se fait en local avec `firebase deploy --only firestore:rules --project miff-3d283` (pas depuis la CI : cela exigerait une clé de compte de service dans les secrets, risque évitable).

### 4.B Android (`android/`)

- **Projet** : Gradle Kotlin DSL, catalogue de versions (`libs.versions.toml`), un seul module `app` pour commencer (pas de multi-modules prématuré), paquet `com.tomessa.miff`.
- **Architecture proposée** : MVVM avec état unidirectionnel (`ViewModel` + `StateFlow`), couches `ui/` (Composables), `domain/` (logique pure testable, par ex. calcul du niveau vert/orange/rouge plus tard), `data/` (repositories Firestore/Auth). Injection par constructeur avec un conteneur simple (`AppContainer`) pour limiter le poids de build sur 8 Go de RAM ; Hilt pourra être adopté si le graphe grossit (à justifier en PR). Navigation : Navigation Compose avec routes typées.
- **Firebase** : BoM unique, plugin `google-services`, Firestore (cache hors ligne par défaut conservé), Authentication (SDK seulement), App Check, Crashlytics, Analytics.
  - App Check : fournisseur « debug » en build debug ; Play Integrity en build release. Mode **non appliqué** pendant ce ticket.
  - Analytics et Crashlytics : collecte désactivée au démarrage via le manifeste ; activation à l'exécution uniquement par le mécanisme de consentement (dans ce ticket : un interrupteur sur l'écran de diagnostic interne).
- **Sécurité de l'app** : `allowBackup` désactivé (la sauvegarde automatique Google copierait le cache Firestore local, qui contiendra des états de position), trafic en clair interdit, pas de `debuggable` en release, aucune permission déclarée hormis l'accès réseau.
- **Design system** : fichier de tokens unique (`Color.kt`, `Type.kt`, `Shape.kt`, `Shadow.kt`, dégradés) reprenant **les noms de code** de `design/design-system.md` (`colorBrandPrimary`, `colorActionPrimary`, `colorStatusSafe`...). Aucune valeur en dur dans les écrans. Polices Baloo Thambi 2 et Hanken Grotesk embarquées en ressources (`res/font`) avec leur licence OFL dans le dépôt. Thème **clair imposé** (pas de mode sombre dans les maquettes). Tailles de texte en `sp` pour suivre la taille de police du système.
  - Écart de plateforme à accepter : l'ombre `shadow/default` (0/4/4, noir 25 %) se rend sur Android par une élévation qui ne peut pas reproduire exactement décalage et flou de la maquette. Le PM valide le rendu sur capture.
- **Localisation** : `res/values/strings.xml` en français (langue source), `values-en/` etc. Aucune chaîne en dur (Lint en erreur). Pluriels via ressources. Langue par app : sélecteur système (Android 13+) et retro-compatibilité AppCompat ; `LocaleConfig` générée automatiquement. Pseudo-locales (`en-XA` allongement, `ar-XB` droite-à-gauche) activées en debug pour tester sans traduction. Mise en page avec début/fin (jamais gauche/droite) ; Lint `RtlHardcoded` en erreur.
- **Icône** : icône adaptative (couche avant, couche arrière, couche monochrome pour Android 13+), vérifiée en rond, carré et « squircle ». Version 512 x 512 px pour la fiche Play : à vérifier (préparée, non utilisée avant la soumission).
- **Écran de diagnostic** : voir §4.D. Absent du build release.
- **Variantes de build** : `debug` (diagnostic + débogage) et `release` (minifié, sans diagnostic, non signé en CI tant que la clé de signature n'existe pas).
- **Tests et qualité** : tests unitaires JUnit sur la logique pure (liste blanche d'analytics, interprétation des erreurs de connexion, etc.) ; Android Lint + `ktlint` (via Spotless) ; `CHANGELOG.md` et `README.md` (installation, régénération de la config Firebase, lancement sur appareil).

### 4.C iOS (`ios/`)

- **Génération du projet sans Mac** : un fichier `project.yml` (outil **XcodeGen**, libre) décrit le projet ; la CI génère le `.xcodeproj` sur macOS. Évite d'écrire à la main un `.xcodeproj` fragile depuis Windows. Les catalogues d'actifs et de chaînes sont des fichiers texte (JSON) éditables sans Xcode. À valider lors du premier build (voir risques).
- **Dépendances** : Swift Package Manager uniquement (pas de CocoaPods) ; versions figées par `Package.resolved` versionné.
- **Architecture proposée** : MVVM avec `ObservableObject` et `@Published` (le macro `@Observable` demande iOS 17, donc indisponible en iOS 16), concurrence Swift (`async/await`), `NavigationStack`, injection par initialiseur via un `AppEnvironment`. Couches `Views/`, `ViewModels/`, `Models/`, `Services/`.
- **Firebase** : Firestore, Auth, App Check, Crashlytics, Analytics via `FirebaseAnalyticsCore` (sans identifiant publicitaire).
  - `FIREBASE_ANALYTICS_COLLECTION_ENABLED = NO` (pas la clé « DEACTIVATED », irréversible), `GOOGLE_ANALYTICS_IDFV_COLLECTION_ENABLED = NO`, Crashlytics désactivé au démarrage. Activation à l'exécution uniquement après consentement.
  - App Check : fournisseur « debug » en configuration Debug ; App Attest en configurations Internal et Release (capacité App Attest à activer sur l'App ID, voir §7). Mode non appliqué.
- **Sécurité de l'app** : transport sécurisé par défaut (aucune exception ATS), aucun accès réseau en clair, `PrivacyInfo.xcprivacy` présent et cohérent avec les API utilisées par l'app et les SDK, aucune clé `NSLocation…` déclarée dans ce ticket, déclaration de conformité au chiffrement (`ITSAppUsesNonExemptEncryption`) posée à `false` (décision PM 2026-10-22 : chiffrement standard HTTPS uniquement, exempté).
- **Design system** : mêmes noms de tokens que sur Android (`colorBrandPrimary`...), polices via `UIAppFonts`, texte dimensionné avec `relativeTo:` pour suivre Dynamic Type, apparence **claire imposée**. Baloo Thambi 2 et Hanken Grotesk : fichiers statiques (les graisses réellement utilisées) ou variables selon rendu validé en CI ; licences OFL dans le dépôt.
- **Localisation** : String Catalog `Localizable.xcstrings` (source : français) et `InfoPlist.xcstrings` (textes de permissions, vides tant qu'aucune permission n'est utilisée). Aucune chaîne en dur. SwiftLint avec règle personnalisée interdisant les littéraux dans `Text(...)` hors clés.
- **Icône** : `AppIcon` 1024 x 1024 px, opaque, sans coins arrondis. L'icône à couches de style iOS 26 (Icon Composer) demande un Mac : comportement d'une icône classique sous iOS 26 à vérifier sur TestFlight.
- **Configurations** : `Debug` (simulateur CI, diagnostic, fournisseur App Check de débogage), `Internal` (build TestFlight du PM : diagnostic visible, App Attest), `Release` (sans diagnostic). Le numéro de build suit le numéro d'exécution de la CI.
- **Tests et qualité** : tests XCTest sur la logique pure (mêmes cas que sur Android) ; SwiftLint ; `CHANGELOG.md` et `README.md` (comment la CI construit l'app, comment régénérer la config).
- **Garde-fou iOS 16** : aucune API de version supérieure sans garde de disponibilité. Les simulateurs des runners sont tous en iOS 26.x (voir risques) : le compilateur détecte les API absentes, pas les différences de comportement.

### 4.D Écran de diagnostic et galerie de tokens (build debug/internal uniquement)

Un seul écran interne (aucun écran utilisateur final), identique en comportement sur Android et iOS :

1. **Identité du build** : version, numéro de build, configuration, identifiant du projet Firebase affiché (doit être `miff-3d283` ; avertissement visible sinon), langue courante.
2. **Test 1, lecture non authentifiée** : tente de lire un document de Miff sans être connecté. **Résultat attendu : refus (`permission-denied`)**, affiché comme « Règles actives : OK ». Si la lecture réussit, l'écran affiche un avertissement sans ambiguïté : règles ouvertes, ne pas continuer. (Ce test prouve à la fois la connexion au bon projet et que les règles protègent. Il suppose que des règles non ouvertes sont déployées ou que la base est en mode verrouillé ; sinon l'avertissement est la bonne réponse.)
3. **Test 2, lecture authentifiée** : champs e-mail/mot de passe pour un compte de test créé à la main dans la console (jamais stocké ni écrit dans le code), connexion, puis lecture de `users/{uid}` et `entitlements/{uid}`. Un document absent est un résultat normal (affiché « Pas encore de profil », « Offre : Classic »). Aucune écriture.
4. **État App Check** : jeton obtenu ou non (mode non appliqué : l'échec n'empêche pas la lecture).
5. **Interrupteurs** « Analytics » et « Rapports de plantage » (désactivés par défaut, état conservé après redémarrage) ; un événement de test depuis la liste blanche ; un bouton « Provoquer un plantage de test » (actif seulement si les rapports sont activés).
6. **Galerie de tokens** : toutes les couleurs (avec leur nom de code), styles de texte, arrondis, ombres et dégradés de `design/design-system.md`, pour comparaison visuelle avec Figma.
7. **Bloc langue** : une phrase longue et un texte de test pour voir allongement et droite-à-gauche.
8. Messages d'erreur dans un ton neutre et bienveillant (« Pas de connexion pour le moment »), jamais alarmistes, y compris sur cet écran interne.

En build release : un écran d'accueil minimal avec le logo, sans aucun accès au diagnostic.

### 4.E CI GitHub Actions

| Workflow | Déclencheur | Contenu | Coût |
|---|---|---|---|
| `android` | PR touchant `android/**` | Ubuntu : décodage de `google-services.json`, Lint, `ktlint`, tests unitaires, `assembleDebug`, `bundleRelease` non signé ; APK debug en artefact (rétention courte) | Minutes Linux, faibles |
| `ios` | PR touchant `ios/**` | `macos-26` : `xcodegen`, SwiftLint, build + tests XCTest simulateur sans signature, captures du diagnostic en français, anglais, pseudo-langue allongée et droite-à-gauche en artefact | Minutes macOS, **rares et précieuses** |
| `ios-testflight` | **Manuel uniquement** (`workflow_dispatch`) | Archive signée, envoi vers TestFlight | Minutes macOS |
| `firestore-rules` | PR touchant `backend/**` | Ubuntu : Node + Java, `firebase emulators:exec --only firestore`, suite de tests des règles | Minutes Linux |
| `hygiene` | Toute PR | Fichiers interdits (config Firebase, clés, keystores), scan de secrets par un outil libre (par ex. gitleaks, conditions de licence à vérifier), parité des clés de traduction | Linux, négligeable |

Règles communes : filtres de chemins, annulation des exécutions obsolètes (`concurrency`), permissions minimales (`contents: read`), actions tierces épinglées par empreinte de commit, jamais de `pull_request_target`, cache Gradle/SPM, durée de chaque job notée dans la PR. Le secret App Check de débogage et les clés Apple ne sont lisibles que par le workflow qui en a besoin.

Économie des minutes macOS : aucun build iOS sur un changement qui ne touche pas `ios/`, pas de signature ni d'archive à chaque PR, TestFlight sur demande.

### 4.F Localisation : langues proposées (décision PM)

L'infrastructure accepte n'importe quelle langue ; ce qui coûte, c'est chaque langue ajoutée. Proposition par vagues (les coûts sont qualitatifs, non chiffrés) :

| Vague | Langues | Justification | Coût / spécificités |
|---|---|---|---|
| Source | Français | Langue des maquettes et du PM | Aucun coût supplémentaire |
| 1 (avec les premiers tests) | Anglais | Langue de repli universelle, exigée en pratique pour les stores et les tests externes | Faible |
| 2 | Espagnol, portugais (Brésil ou Portugal : à choisir), allemand, italien, néerlandais | Langues européennes et latino-américaines très répandues, écriture latine : mêmes polices, même sens de lecture, un seul jeu de captures à contrôler par langue | Moyen : traduction + relecture par un natif pour conserver le ton bienveillant, fiche store, captures, textes légaux. L'allemand et le néerlandais allongent souvent les textes (heuristique, non mesuré ici) |
| 3 | Arabe | Marché large, mais impacts forts (ci-dessous) | **Élevé** : écriture droite-à-gauche, polices à ajouter |
| 3 | Chinois, japonais, coréen, hindi, grec, russe... | Dépend de la stratégie de marché | Élevé : polices de repli, design et captures spécifiques |

À chaque langue ajoutée, il faut : traduire l'interface et relire le ton avec un natif (une traduction automatique brute peut devenir froide ou alarmiste) ; **faire valider juridiquement le texte de consentement à la localisation** (traçabilité par version dans `members.sharing.consentVersion`, un texte par langue) ; traduire CGU et politique de confidentialité ; traduire la fiche store (titre et sous-titre sont très courts, mots-clés limités) ; contrôler les captures sur les deux plateformes.

Points d'attention déjà identifiables :
- **Arabe (lecture droite-à-gauche)** : retourner la mise en page (flèche retour, barre d'onglets, pagination, carrousel de bienvenue, curseur de rayon), ne pas retourner les icônes sans direction (cœur, logo). Ni Baloo Thambi 2 ni Hanken Grotesk ne contiennent de glyphes arabes : le système utilise sa police de repli, d'où une identité visuelle différente. Le Figma n'a pas de variantes droite-à-gauche : travail de design à prévoir. **Dès ce ticket**, on construit avec « début/fin » et on teste en pseudo-langue : surcoût quasi nul, et on évite une réécriture si l'arabe arrive.
- **Longueur des textes** : les maquettes sont en français ; les éléments à largeur fixe ou très petits (par exemple la pastille 8 px « Annulable à tout moment ») ne supporteront pas d'autres langues et sont déjà peu lisibles. À signaler à `design-system` : prévoir de la place pour +30 à +40 % de texte (heuristique) et des composants qui se déploient.
- **Phrases composées** : des messages comme « [Prénom] a quitté la maison » ou « est bien arrivé à l'école » dépendent du genre et des prépositions selon la langue. Règle : une phrase complète par cas (par catégorie de lieu, voir §6), jamais d'assemblage de morceaux, et des formulations neutres quand la langue le permet.
- **Dates relatives** (« il y a 12 min »), nombres et pluriels : toujours via les formateurs du système et les ressources de pluriel.
- **Notifications futures** : elles seront composées côté serveur ; il faudra connaître la langue de chaque destinataire (impact sur le schéma, voir §6).
- **Langue de repli** pour un téléphone dans une langue non prise en charge : français tant que l'anglais n'est pas livré (voir §9).

---

## 5. Critères d'acceptation

### Transverses
- [ ] **T1** `git ls-files` ne contient aucun `google-services.json`, `GoogleService-Info.plist`, `.jks`/`.keystore`, `.p8`, `.p12`, `.mobileprovision`, `.env` ; le job `hygiene` échoue si l'un d'eux est ajouté (test : ajouter un faux fichier sur une branche jetable).
- [ ] **T2** `.gitignore`, `.gitattributes`, `.editorconfig` en place ; aucun fichier avec fins de ligne CRLF dans le dépôt.
- [ ] **T3** Tous les cas des sections 1 à 8 de `backend/rules-test-plan.md` sont automatisés (un test par identifiant) et exécutés en CI ; le rapport indique, pour chaque identifiant, réussi/échoué. Tout échec est consigné et transmis à `backend-data`.
- [ ] **T4** Les tests des règles tournent sur un projet de démonstration ; une tentative de contacter `miff-3d283` ou `ghostrun-app` fait échouer le job.
- [ ] **T5** Le nombre d'appels de lecture de règles par opération est mesuré et reporté (création de Miff premium comprise) ; tout dépassement de 10 (requête) ou 20 (batch) est signalé.
- [ ] **T6** Chaque workflow ne se déclenche que sur les chemins concernés ; les permissions sont minimales ; les actions tierces sont épinglées.
- [ ] **T7** Le modèle de PR existe et comporte les champs listés au §4.A.
- [ ] **T8** Le script de parité vérifie que les clés françaises sont identiques sur les deux plateformes (hors préfixes `ios_`/`android_`) ; il échoue sur une clé ajoutée d'un seul côté (test sur branche jetable).
- [ ] **T9** Dependabot actif sur Gradle, SwiftPM et GitHub Actions.
- [ ] **T10** Aucune dépense engendrée : plan Spark inchangé, aucun moyen de paiement ajouté (sauf décision explicite du PM, voir Q4).
- [ ] **T11** L'inventaire des dépendances tierces (nom, version, licence) est fourni dans chaque PR, sans dépendance lourde non justifiée.
- [ ] **T12** Les règles Firestore ne sont déployées qu'après T3, revue sécurité et accord du PM ; une fois déployées, l'écran de diagnostic affiche « Règles actives : OK ».

### Android
- [ ] **A1** `./gradlew assembleDebug` réussit sur le poste Windows (8 Go) et en CI ; `applicationId` = `com.tomessa.miff`, `minSdk` = 26, `targetSdk`/`compileSdk` = 36.
- [ ] **A2** Android Lint et `ktlint` passent ; `HardcodedText`, `MissingTranslation`, `ExtraTranslation` et `RtlHardcoded` sont configurés en erreur.
- [ ] **A3** Les tests unitaires passent (au minimum : liste blanche d'analytics qui refuse `lat`/`lng`/`address`/`place`, interprétation des erreurs de connexion).
- [ ] **A4** L'app démarre sans plantage sur Android 8.0 (API 26, émulateur léger ou appareil) et sur Android récent (API 36).
- [ ] **A5** L'écran de diagnostic affiche le projet `miff-3d283` ; Test 1 = refus attendu ; Test 2 = lecture réussie (document absent accepté) avec le compte de test.
- [ ] **A6** Au premier lancement, aucune donnée Analytics ni Crashlytics n'est envoyée (aucun événement visible en console avant activation) ; après activation, l'événement de test apparaît ; après désactivation, plus rien. L'état persiste après redémarrage.
- [ ] **A7** Le manifeste ne déclare aucune permission autre que le réseau ; `allowBackup` désactivé ; trafic en clair interdit ; pas de `debuggable` en release.
- [ ] **A8** La galerie affiche tous les tokens de `design/design-system.md` avec les valeurs exactes ; aucune couleur hexadécimale ni taille en dur hors du fichier de tokens.
- [ ] **A9** L'app reste en thème clair avec le téléphone en mode sombre ; avec la taille de police système au maximum, aucun texte de l'écran de diagnostic n'est tronqué sans possibilité de défiler.
- [ ] **A10** Aucune chaîne en dur ; français et anglais (selon décision §9) présents ; changement de langue de l'app fonctionnel (réglages système, Android 13+) ; en pseudo-locales `en-XA` et `ar-XB`, aucun texte coupé de façon bloquante et la mise en page se retourne en `ar-XB`.
- [ ] **A11** L'icône adaptative s'affiche correctement en rond, carré et squircle ; l'icône thématique (Android 13+) est reconnaissable.
- [ ] **A12** `bundleRelease` réussit en CI et le build release ne contient pas l'écran de diagnostic.
- [ ] **A13** En mode avion, l'écran de diagnostic ne plante pas et affiche un message neutre.
- [ ] **A14** `android/README.md` et `android/CHANGELOG.md` à jour ; la taille de l'APK debug est consignée à titre informatif.

### iOS
- [ ] **I1** Le workflow `ios` construit l'app sur `macos-26` avec Xcode 26.2 ou plus ; cible de déploiement = iOS 16.0 ; bundle ID = `com.tomessa.miff`.
- [ ] **I2** SwiftLint et les tests XCTest passent sur simulateur.
- [ ] **I3** Le workflow manuel `ios-testflight` produit une archive signée et la livre à TestFlight ; le build apparaît dans App Store Connect puis s'installe sur l'iPhone du PM.
- [ ] **I4** L'Info.plist ne déclare aucune clé de localisation ; `PrivacyInfo.xcprivacy` présent ; aucune exception ATS.
- [ ] **I5** `FIREBASE_ANALYTICS_COLLECTION_ENABLED = NO` (la clé « DEACTIVATED » n'est pas utilisée), `FirebaseAnalyticsCore` utilisé (aucune trace de l'identifiant publicitaire), Crashlytics désactivé au démarrage ; mêmes preuves de comportement que **A6**.
- [ ] **I6** App Check : fournisseur de débogage en Debug, App Attest en Internal/Release ; mode non appliqué.
- [ ] **I7** L'écran de diagnostic iOS donne les mêmes résultats que sur Android (mêmes Tests 1 et 2).
- [ ] **I8** Tokens identiques à Android (noms et valeurs) ; polices correctes (Baloo Thambi 2, Hanken Grotesk) ; Dynamic Type respecté ; apparence claire imposée.
- [ ] **I9** String Catalog avec source française, aucune chaîne en dur (règle SwiftLint), anglais présent ; les captures CI en français, anglais, pseudo-langue allongée et droite-à-gauche sont en artefact et lisibles.
- [ ] **I10** Icône 1024 x 1024 px opaque, sans coins arrondis ; rendu constaté sur l'iPhone du PM.
- [ ] **I11** Aucune dépendance CocoaPods ; `Package.resolved` versionné.
- [ ] **I12** Aucune API postérieure à iOS 16 utilisée sans garde de disponibilité.
- [ ] **I13** Le build `Release` ne contient pas l'écran de diagnostic.
- [ ] **I14** `ios/README.md` et `ios/CHANGELOG.md` à jour, durée des jobs macOS consignée.

### Ce que le PM peut vérifier lui-même sur téléphone et dans les consoles
- [ ] **P1 (iPhone, TestFlight)** Installer le build depuis TestFlight : l'icône Miff s'affiche sur l'écran d'accueil ; l'app s'ouvre sans plantage ; le nom affiché est « Miff ».
- [ ] **P2 (Android)** Installer l'APK debug téléchargé depuis la page de la CI (ou via la méthode choisie) : même vérification.
- [ ] **P3** Sur l'écran de diagnostic (deux téléphones) : le projet affiché est `miff-3d283`, Test 1 = « Règles actives : OK », Test 2 = OK avec le compte de test.
- [ ] **P4** Interrupteurs Analytics et plantage éteints au premier lancement ; allumer, déclencher le plantage de test, relancer l'app : le plantage apparaît dans Firebase > Crashlytics (peut prendre quelques minutes).
- [ ] **P5** Dans Firebase > Firestore > Utilisation, les lectures du jour restent à quelques unités après les tests ; dans Firebase > App Check, les requêtes des deux apps apparaissent (vérifiées ou non, le mode étant non appliqué).
- [ ] **P6** La galerie de tokens est comparée à Figma (couleurs, polices, arrondis, ombres) ; les écarts sont notés. Sur Android, valider le rendu de l'ombre.
- [ ] **P7** Passer le téléphone en mode sombre : l'app reste claire. Agrandir la taille de police : le texte suit sans se couper de façon bloquante.
- [ ] **P8** Passer le téléphone en anglais : le texte du diagnostic passe en anglais (si l'anglais est livré).
- [ ] **P9** Regarder les captures CI en droite-à-gauche et en pseudo-langue allongée (artefacts) : le PM valide que rien ne se superpose de façon rédhibitoire.
- [ ] **P10** Dans le dépôt GitHub (page Code), constater l'absence des fichiers `google-services.json` et `GoogleService-Info.plist`.
- [ ] **P11** Vérifier que la facture Firebase et GitHub reste à 0 € (consoles de facturation).

### Pour le QA (`qa-tester`)
- [ ] **Q1** Chaque critère ci-dessus est rejoué et consigné dans `docs/qa/` avec preuve (capture, journal, lien CI).
- [ ] **Q2** Recherche de secrets dans tout l'historique git (pas seulement dans le dernier commit).
- [ ] **Q3** Tentative d'envoi d'un événement Analytics contenant une position : refusé sur les deux plateformes.
- [ ] **Q4** Vérification du comportement sans réseau, avec langue arabe en pseudo-locale, avec police agrandie.

### Définition de « terminé »
Les critères ci-dessus cochés, revue de code en contexte neuf, revue sécurité (App Check, secrets, liste blanche d'analytics, permissions, permissions de la CI, dépendances), QA, recette du PM, puis merge par le PM. Aucun push direct sur `main`.

---

## 6. Impacts sur le schéma de données (à reporter par `backend-data` dans `backend/data-model.md` — non modifié ici)

### Correspondance entre le modèle Notion et le schéma actuel

| Notion | Schéma actuel | Écart |
|---|---|---|
| User : id | UID Firebase Auth (`users/{uid}`) | OK |
| User : e-mail | Géré par Firebase Auth, pas dans Firestore | OK (pas de doublon) |
| User : mot de passe | Géré par Firebase Auth | **Retirer du modèle Notion** : jamais stocké chez nous |
| User : prénom, nom, photo | `firstName`, `lastName`, `avatar` (facultatif) | OK |
| User : numéro de téléphone | **Absent** | À ajouter si le PM confirme le besoin (voir ci-dessous) |
| User : liste de Miff | Sous-collection `users/{uid}/memberships` | OK |
| User : statut d'abonnement | `entitlements/{uid}` (`active`, `expiresAt`, `source`) | Trop pauvre : voir ci-dessous |
| User : Free_trial_used | **Absent** | À ajouter, non modifiable par le client |
| Miff : id, nom, code secret, créateur | `miffId`, `name`, `joinCode` (+ `joinCodes`), `ownerUid` | OK |
| Miff : users associés, lieux associés | Sous-collections `members`, `places` | OK |
| Lieu : id, nom, latitude, longitude, adresse, rayon, créateur | `placeId`, `name`, `lat`, `lng`, `address`, `radiusM`, `createdBy` | OK |
| Lieu : type (Confiance / Danger) | `type` : `trusted` / `risk` | Vocabulaire à harmoniser : l'interface dit « Zone de confiance / Zone à risque », Notion « Danger », les tokens « danger » |
| Lieu : catégorie | **Absent** | À ajouter, voir ci-dessous |
| Lieu : Miff associé (liste) | Un lieu = une Miff (sous-collection de la Miff) | **Conflit**, voir ci-dessous |

### Modifications à prévoir

1. **Catégorie de lieu** : valeurs Maison, Travail, École, Sport, Chez un proche, Loisir, Autre. Un champ à valeurs fixes stockées sous forme de codes stables (jamais le libellé, qui est traduit). Elle sert à formuler les messages du feed et des notifications (« à la maison », « à l'école », « chez un proche ») dans chaque langue. Questions pour le ticket Lieux : obligatoire ou facultative ? Applicable aux zones à risque ?
2. **Statut d'abonnement détaillé** (Classic, Premium_month, Premium_year, Forced_premium) : à porter par `entitlements/{uid}` (écriture interdite au client, c'est la raison de la séparation d'avec `users`), par exemple par un champ de type d'offre. « Classic » = absence de document ou offre inactive. « Forced_premium » = octroi manuel par le PM ; attention, les règles actuelles exigent une date d'expiration future (`expiresAt > heure serveur`) : un premium forcé « sans fin » demande soit une date lointaine, soit une règle adaptée. À arbitrer par `backend-data`.
3. **Free_trial_used** : à stocker dans `entitlements/{uid}` pour qu'il ne soit pas modifiable par le client. Limites à connaître : (a) sans serveur (Spark), seul le PM peut l'écrire à la main ; (b) l'essai gratuit est de toute façon contrôlé par l'App Store et Google Play, ce champ n'est qu'un reflet ; (c) l'actuelle purge de `entitlements` à la suppression de compte (`data-model.md` §10) effacerait aussi cette trace, ce qui est un arbitrage vie privée/anti-abus à décider avec le paiement.
4. **Mot de passe** : aucun champ ; retirer la mention du modèle Notion.
5. **Numéro de téléphone** : champ de profil facultatif (décidé par le PM), à ajouter à `users/{uid}`. Questions de minimisation à trancher dans le ticket Profil : à quoi sert-il, est-il visible des autres membres (donc copié dans `members`) ou privé ?
6. **Langue préférée de l'utilisateur** (nouveau besoin) : nécessaire un jour pour envoyer des notifications dans la bonne langue ; à ajouter avec le push (par exemple sur le document de l'appareil). Pas bloquant ici.
7. **Conflit « un lieu appartient-il à plusieurs Miffs ? »**
   - Spec d'origine Notion : liste de Miffs, donc oui. Spec `lieux-de-confiance.md` : « hors-scope : partage d'un lieu entre plusieurs Miffs (chaque Miff a ses propres lieux) », donc non. Schéma actuel : non (`miffs/{miffId}/places/{placeId}`).
   - **Si un lieu = une Miff (schéma actuel)** : un utilisateur premium dans deux Miffs qui ont toutes deux « Maison » crée ou voit deux lieux presque identiques. Chaque téléphone surveille potentiellement deux géofences au même endroit, ce qui consomme deux emplacements sur les 20 d'iOS et les 100 d'Android. Le schéma prévoit une fusion côté appareil (`data-model.md` §7), mais comparer des lieux « quasi identiques » reste fragile (rayons différents, adresses saisies différemment). Les règles freemium restent simples (1 lieu par Miff, créé par le créateur).
   - **Si un lieu peut être lié à plusieurs Miffs** : un seul géofence système pour plusieurs destinataires (économie d'emplacements). Mais : le lieu ne peut plus vivre sous une seule Miff (stockage au premier niveau avec une liste de Miffs), les règles de lecture deviennent plus complexes (appartenance à au moins une des Miffs, limites de 10 lectures par requête), et la règle « seul le créateur de la Miff définit ses lieux » devient ambiguë quand les Miffs ont des créateurs différents. Il faut aussi définir le comportement à la suppression, au retrait d'une Miff, et pour le type (un lieu de confiance pour une Miff, à risque pour une autre ?) ainsi que le comptage du quota gratuit.
   - Chez un utilisateur gratuit (une seule Miff, un seul lieu), les deux options sont équivalentes : le conflit n'est visible qu'en premium.
   - **À trancher dans le ticket « Lieux de confiance »**, avec `backend-data` ; ce ticket n'y engage rien.

### Convention à fixer dès maintenant côté clients (sans impact schéma)
À la déconnexion, le cache local de Firestore (qui peut contenir des états de position) doit être purgé. Le comportement sera implémenté et testé dans le ticket Inscription/Connexion.

---

## 7. Actions humaines du PM (dans l'ordre)

Rien de ce qui suit n'est fait par un agent : ce sont des paiements, des comptes ou des accès nominatifs.

### Avant le début du dev (bloquant)
1. **Décider de l'emplacement du dépôt local.** Le dossier actuel est dans OneDrive (`Bureau`) : les builds Android y créent des milliers de fichiers que OneDrive synchronise (lenteurs, verrous, dépôt `.git` à risque), avec un chemin long et des espaces. Recommandation : cloner le dépôt dans un dossier hors OneDrive (par exemple `C:\dev\miff`) et travailler là. Voir Q1.
2. **Vérifier le plan GitHub** (compte propriétaire du dépôt) et les minutes disponibles : GitHub > Settings > Billing and plans. Noter si la protection de branche est proposée pour un dépôt privé. Voir Q3.
3. **Firebase (console, projet `miff-3d283`)** :
   - Firestore > Règles : constater qu'il n'y a **aucune règle ouverte** (pas de « mode test » qui autorise tout). Si c'est le cas, le signaler avant tout autre test.
   - Authentication > Utilisateurs > Ajouter un utilisateur : créer un **compte de test** (e-mail + mot de passe fort) réservé au diagnostic. Le mot de passe va dans votre gestionnaire de mots de passe, pas dans le dépôt.
   - Authentication > paramètres : ne pas activer la connexion anonyme ; activer la protection contre l'énumération d'adresses e-mail si elle est proposée.
   - Vérifier que Crashlytics est activé pour le projet (première ouverture du tableau de bord Crashlytics).
   - Analytics : régler la rétention des données au minimum proposé (Admin > Paramètres de données).
4. **Fournir les éléments de l'icône et du logo** (voir §9, bloc logo) : export du logo Miff et de l'icône (M blanc + cœur sur fond bleu) en SVG ou PNG haute résolution depuis le Figma. À défaut, une icône provisoire est mise en place et marquée « ne pas publier ».
5. **Fournir (ou confirmer) les modèles d'appareils** : version d'iOS de l'iPhone (Réglages > Général > Informations) et téléphone Android éventuel. Voir Q2.

### Pour que l'iOS puisse arriver dans TestFlight (à lancer tôt : les délais Apple sont hors de notre contrôle)
6. **Compte Apple Developer existant** : vérifier qu'il est actif (non expiré), accepter les accords en attente dans App Store Connect (bandeau d'avertissement éventuel) et noter si c'est un compte individuel ou une organisation.
7. **App Store Connect > Utilisateurs et accès > Intégrations > Clés** : créer une **clé d'API App Store Connect** (rôle minimal à confirmer par le spike : « Developer » ou « App Manager »). Télécharger le fichier `.p8` (**téléchargeable une seule fois**) ; noter l'identifiant de clé et l'identifiant d'émetteur (Issuer ID). Les stocker dans le gestionnaire de mots de passe.
8. **developer.apple.com > Certificates, Identifiers & Profiles > Identifiers** : enregistrer l'identifiant **explicite** `com.tomessa.miff` ; activer la capacité **App Attest**. Sign in with Apple et Push Notifications peuvent attendre leurs tickets (voir « peut attendre »).
9. **App Store Connect > Apps > Nouvelle app** : plateforme iOS, nom (celui affiché sur le store ; « Miff » peut être déjà pris, un nom provisoire convient pour TestFlight), langue principale français, bundle ID `com.tomessa.miff`, SKU libre.
10. **Noter le Team ID** (developer.apple.com > Compte > Appartenance).
11. **TestFlight** : s'ajouter comme testeur interne (le PM est déjà utilisateur App Store Connect) ; installer l'app TestFlight sur l'iPhone ; ajouter le build au groupe de test interne (ou activer la distribution automatique du groupe). La question de conformité au chiffrement n'est plus à renseigner à la main : `ITSAppUsesNonExemptEncryption = false` est posée dans l'app (décision PM 2026-10-22).

### Secrets GitHub (une fois les éléments ci-dessus en main)
12. Sur GitHub : Settings > Secrets and variables > Actions > New repository secret, un secret par ligne :
    - `GOOGLE_SERVICES_JSON_B64` et `GOOGLE_SERVICE_INFO_PLIST_B64` : contenu encodé en base64 des deux fichiers. Les obtenir avec `firebase apps:sdkconfig ANDROID 1:29899146512:android:307ffd8ea71a4a37659195 --project miff-3d283` (et `IOS 1:29899146512:ios:94e30481497dd74c659195`), ou depuis la console Firebase > Paramètres du projet. Encodage sous PowerShell : `[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\chemin\fichier")) | Set-Clipboard`.
    - `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8_B64` (clé `.p8` encodée), `APPLE_TEAM_ID`.
    - `APPCHECK_DEBUG_TOKEN` : créé dans Firebase > App Check > Apps > Gérer les jetons de débogage (jeton jamais montré dans un journal de CI).
13. Ne jamais coller un de ces secrets dans une conversation, un ticket ou un fichier du dépôt. En cas de doute sur une fuite : révoquer la clé (Apple) ou supprimer le jeton (App Check) et en recréer un.

### Peut attendre (ne bloque pas ce ticket)
- **Empreintes SHA-1 / SHA-256 Android** (`gradlew signingReport` ou `keytool`) à enregistrer dans Firebase : nécessaires pour la connexion Google (ticket Inscription) et pour Play Integrity (avant l'application stricte d'App Check).
- **Compte Google Play Console** (frais uniques, vérification d'identité qui prend du temps ; pour un compte personnel créé après nov. 2023, 12 testeurs pendant 14 jours avant la production) : à lancer assez tôt avant la première bêta, pas pour ce ticket.
- **Clé de signature Android** (clé d'upload) : à générer avec `keytool`, mot de passe et fichier sauvegardés hors du dépôt ; perte = problème de publication. Avant la première distribution via Play.
- **Sign in with Apple** (Service ID, clé, Team ID selon la doc Firebase) : ticket Inscription.
- **Clé APNs** et notifications : ticket Notifications (et Blaze).
- **URL de politique de confidentialité**, textes légaux, déclarations « Background Location » et confidentialité des stores : avant tests externes ou soumission.
- **Passage de l'application App Check en strict** : après distribution via Play interne et TestFlight (une app installée hors Google Play ne passe pas Play Integrity ; un build de test sans jeton valide serait bloqué).
- **Déploiement des règles Firestore** : après tests verts et revue sécurité (voir §4.A.9).

---

## 8. Risques

| # | Risque | Effet | Mitigation |
|---|---|---|---|
| R1 | **iOS sans Mac** : boucle de feedback lente (pousser, attendre la CI, attendre TestFlight), signature sans Xcode, `.xcodeproj` non éditable à la main | Délais, erreurs de configuration découvertes tard | Android d'abord ; XcodeGen ; **spike de signature et d'envoi vers TestFlight dès que les actions 6 à 11 sont faites** (voir §10), avant d'écrire du code applicatif iOS ; petits incréments ; captures du simulateur en artefact |
| R2 | Quota CI macOS : 2 000 minutes gratuites par mois en dépôt privé, mais consommation macOS non confirmée (historiquement x10, soit environ 200 minutes) ; la compilation de Firestore par SwiftPM peut être longue (non mesuré) | Quota épuisé en cours de mois, CI bloquée (sans moyen de paiement, l'usage s'arrête) | Filtres de chemins, annulation des runs obsolètes, cache SPM, TestFlight sur demande, mesure de la durée réelle dans le spike ; options dans Q4 (payer à l'usage, Xcode Cloud, GitHub Pro) ; **ne pas rendre le dépôt public pour contourner**, ce qui exposerait docs produit et identifiants |
| R3 | Xcode Cloud (25 h/mois offertes) : création initiale du workflow via Xcode, donc un Mac | Option attractive mais pas autonome | Emprunter un Mac pour une configuration unique, à vérifier avant de s'y engager |
| R4 | 8 Go de RAM : Android Studio + émulateur + Gradle | Lenteur, plantages, builds interminables | Appareil physique en USB plutôt qu'émulateur ; image d'émulateur légère ; limiter le démon Gradle (options mémoire) ; fermer le navigateur ; pas d'Hilt/KSP lourds au départ ; CI Linux pour les builds complets ; WHPX activé |
| R5 | Dépôt dans OneDrive | Corruption de `.git`, verrous, synchronisation de milliers de fichiers de build | Action n°1 du §7 |
| R6 | Règles Firestore écrites mais jamais exécutées | Bugs de règles découverts maintenant (bon moment), délais de correction par `backend-data` | Suite de tests automatisés ; corrections hors de ce ticket, tracées |
| R7 | Simulateurs CI uniquement en iOS 26.x alors que le minimum est iOS 16 | Un comportement SwiftUI propre à iOS 16 peut passer inaperçu | Interdire les API plus récentes sans garde ; tester sur un iPhone en iOS 16/17 si disponible (Q2) ; télécharger un runtime plus ancien sur le runner : faisabilité et durée à vérifier |
| R8 | Version Firebase / Xcode qui bouge vite (Xcode 26.2 minimum pour Firebase 13) | Builds cassés par une mise à jour | Versions figées (`Package.resolved`, catalogue Gradle), mises à jour via Dependabot en PR |
| R9 | Protection de branche indisponible en dépôt privé gratuit (à vérifier) | « Jamais de push sur `main` » repose sur la discipline | Discipline de processus ; ou GitHub Pro (Q3) |
| R10 | Fuite de secret (clé `.p8`, jeton de débogage, config Firebase) | Accès non autorisé, coûts d'usage abusif | Secrets GitHub uniquement, scan de secrets en CI, `.gitignore`, aucune clé de compte de service dans la CI, rotation documentée |
| R11 | Consentement : Analytics/Crashlytics mal configurés (collecte avant accord) | Risque RGPD et de confiance | Désactivés au démarrage, preuves A6/I5, test de la liste blanche, avis juridique sur Crashlytics (Q6) |
| R12 | Polices sans couverture arabe/CJK/etc. | Identité visuelle différente selon la langue, surprises tard | Décision explicite par langue (§4.F), polices de repli du système |
| R13 | Contraste des couleurs de statut : le jaune-vert `#d1d828` sur le fond `#f9f9f7` donne un ratio d'environ 1,5 pour 1 (calcul approximatif ; 3 pour 1 est le minimum usuel pour des éléments graphiques) | L'anneau « en sécurité » peut être peu visible pour certains utilisateurs | Le statut est aussi porté par un texte (badge) : ne jamais le coder par la seule couleur ; à signaler à `design-system` |
| R14 | Consommation Spark (50 000 lectures/jour) | Sans objet à ce stade (quelques lectures de diagnostic) | Suivi de l'utilisation (P5) |
| R15 | App Check avec Play Integrity et applications installées hors Play | Builds de test refusés le jour où l'application stricte est activée | Application stricte reportée après distribution Play/TestFlight ; jeton de débogage pour les builds de dev |

---

## 9. Décisions du PM et questions ouvertes

### Décisions prises (2026-10-16, après revue du ticket)
| Sujet | Décision |
|---|---|
| Critères d'acceptation | Conservés (le PM n'en voyait pas le besoin ; ils restent utiles au QA et à la recette pour savoir quand le socle est « terminé »). Le PM peut les alléger. |
| Paiement | **Abonnement natif** (App Store / Google Play), avec une couche d'abstraction côté apps pour pouvoir ajouter un service tiers plus tard. Pas d'achat réel possible tant qu'il n'y a pas de serveur (plan Blaze) : le premium est accordé à la main d'ici là. |
| Environnements | Un seul projet Firebase (`miff-3d283`) pour l'instant ; un projet de développement séparé sera créé plus tard (avant les premiers testeurs externes). |
| Catégorie de lieu | Ajoutée au schéma (codes stables : maison, travail, école, sport, chez un proche, loisir, autre). |
| Statut premium | L'utilisateur peut **passer du mensuel à l'annuel, démarrer ou arrêter** un abonnement. Le modèle doit représenter : l'offre (aucune / mensuel / annuel / forcé), l'état (essai, actif, résilié mais actif jusqu'à l'échéance, expiré), la date d'échéance (absente pour un premium forcé), le renouvellement automatique, la boutique d'origine (Apple / Google / manuel), l'essai déjà consommé. À détailler par `backend-data`. |
| Appareils de test | iPhone 11 Pro sous iOS 26.5.2 (iOS 16 minimum : compatible). Téléphone Android physique : OPPO Find X5 Lite sous Android 13 (API 33, minSdk 26 : compatible). Les tests Android se feront donc sur appareil réel, pas sur l'émulateur (8 Go de RAM). **Point d'attention** : les surcouches constructeur (ColorOS d'OPPO) tuent souvent les applications en arrière-plan ; à tester dès que le géofencing sera développé et à documenter pour l'utilisateur final. |
| Comptes développeur | Apple : compte payant existant (99 €/an), créé pour Ghostrun. Google Play : compte existant (25 €, paiement unique), créé pour Ghostrun. Miff y sera une **nouvelle app** distincte (`com.tomessa.miff`). À vérifier plus tard : exigences de test imposées par Google Play aux comptes personnels avant une première mise en production. |
| Langues | **Français et anglais** dès ce ticket. **Anglais = langue de repli** pour toute langue non prise en charge. Structure de traduction prête pour en ajouter ensuite (la préparation du droite-à-gauche reste incluse, sans livrer l'arabe). |
| Défauts retenus sauf avis contraire du PM | Dépôt de travail à déplacer hors de OneDrive (**déplacement non effectué : en attente du feu vert du PM**), GitHub gratuit sans protection de branche (discipline « jamais de push sur `main` »), CI iOS sur les minutes macOS gratuites de GitHub avec mesure dans le spike, écran de diagnostic dans les builds TestFlight uniquement, Crashlytics désactivé tant que l'utilisateur n'a pas consenti. |
| Bloc logo et icône | Confirmé : c'est l'image de la page « Design system » du Figma original (logo bleu à cœur jaune-vert, icône d'app = M blanc + cœur sur fond bleu, badge, palette). Les fichiers sources restent à fournir par le PM. |

### Questions qui bloquent ou orientent ce ticket
1. **Dépôt hors OneDrive** : êtes-vous d'accord pour travailler dans un dossier hors OneDrive (par exemple `C:\dev\miff`) ? (Recommandé fortement ; bloque le dev Android.)
2. **Vos appareils** : quel iPhone (modèle et version d'iOS) et avez-vous un téléphone Android physique (modèle, version) ? Cela confirme que iOS 16 convient à votre iPhone et décide comment vous testerez Android (téléphone ou émulateur).
3. **GitHub Pro** : acceptez-vous de passer le compte GitHub en Pro (tarif **à vérifier**) pour obtenir la protection de branche et plus de minutes, ou on s'en tient à la discipline « jamais de push sur `main` » avec le plan gratuit ?
4. **CI iOS** : parmi (A) les minutes macOS gratuites de GitHub, en acceptant des builds peu nombreux ; (B) un moyen de paiement avec plafond de dépense à l'usage ; (C) Xcode Cloud (25 h/mois offertes) après une configuration unique sur un Mac emprunté ou loué, laquelle choisissez-vous pour démarrer ? Recommandation : (A), mesurer dans le spike, puis décider.
5. **Écran de diagnostic dans TestFlight** : acceptez-vous qu'un écran technique interne soit visible dans les builds que vous testez (jamais dans les builds publics) ? Sans lui, il n'y a aucune preuve visible que l'app parle à Firestore.
6. **Crashlytics et consentement** : voulez-vous Crashlytics désactivé tant que l'utilisateur n'a pas consenti (recommandé en l'attente d'un avis juridique, au prix de ne pas voir les plantages avant consentement) ?
7. **Langues** : validez-vous les vagues proposées (§4.F) ? Dans ce ticket, quelles langues sont livrées à la place du français : **anglais seul** (recommandé) ou davantage ? Quelle langue de repli pour un téléphone dans une langue non prise en charge : français (aujourd'hui) ou anglais ?
8. **Comptes de développeur** : le compte Apple est-il individuel ou une organisation, et êtes-vous le titulaire du compte (Account Holder) ? Avez-vous déjà un compte Google Play Console ?

### Points à confirmer (non bloquants)
- **Bloc « logo et icône » du ticket Notion** : son contenu n'était pas lisible. Notre hypothèse, à confirmer : logo Miff bleu avec un cœur jaune-vert ; icône d'application = M blanc et cœur sur fond bleu ; variante « badge » ; palette. Les spécifications de format (taille minimale, zone de protection, usages interdits, variantes monochromes ou sur fond sombre) sont inconnues. Il faut aussi les fichiers sources (le recensement Figma indique une page « Design system » vide alors que `design-system.md` y situe l'icône : à clarifier).
- **Liste finale des langues** : décision PM (voir Q7 et §4.F).
- **Fournisseur de paiement futur** (validation native App Store/Google Play ou service tiers) : conditionne la fonction de validation d'achat (Blaze) et le sens de `Premium_month`, `Premium_year` et `Free_trial_used`. Question 19 de `data-model.md`.
- **Poids de Baloo Thambi 2** : `design-system.md` ne précise pas la graisse des styles `display/*`. À indiquer par `design-system` avant l'import des polices.
- **Pastille 8 px « Annulable à tout moment »** : taille illisible et non traduisible, à revoir côté design.
- **Mode sombre** : les maquettes n'en ont pas, nous imposons le thème clair. Confirmer.
- **Apple sur Android** : les maquettes affichent « Continuer avec Apple » ; sur Android, ce bouton passe par un navigateur web et demande une configuration supplémentaire. À trancher dans le ticket Inscription (le magasin Android ne l'exige pas).
- **Preuve du consentement Analytics** : le choix est stocké sur l'appareil. Si le juriste exige une preuve côté serveur, il faudra un champ de plus (§6).
- **Nom de l'app dans les stores** : « Miff » est-il disponible ? À vérifier au moment de la création de la fiche (action 9).

---

## 10. Découpage proposé

Le ticket est trop gros pour une seule PR par plateforme. Proposition de **4 sous-tickets**, tailles relatives (S, M, L) sans durée chiffrée :

| # | Sous-ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| INIT-1 | **Fondations transverses et règles Firestore** | `.gitignore`, `.gitattributes`, `.editorconfig`, modèle de PR, Dependabot, workflows `hygiene` et `firestore-rules`, tests des règles (plan sections 1 à 8), script de parité des clés, gestion des secrets, README de démarrage | M | Q1, PM actions 1 à 3 |
| INIT-2 | **Android : socle complet** | Projet, Firebase, App Check, Analytics/Crashlytics désactivés, tokens et polices, localisation (fr + langue(s) retenue(s)), icône adaptative, écran de diagnostic et galerie, workflow `android`, tests et lint | L | INIT-1 (secrets et hygiène) ; Q2, Q7 |
| INIT-3 | **iOS : spike CI, signature et TestFlight** | Application minimale « Hello Miff » (sans Firebase) construite avec XcodeGen sur `macos-26`, archivée, signée via la clé API, livrée à TestFlight et installée sur l'iPhone du PM ; mesure de la durée et du coût en minutes ; décision d'option CI (Q4) | S à M | INIT-1 ; PM actions 6 à 12 ; peut démarrer **en parallèle d'INIT-2** pour lever tôt le plus gros risque |
| INIT-4 | **iOS : socle complet** | Mêmes livrables fonctionnels qu'INIT-2 côté iOS : Firebase, App Check, consentement, tokens et polices, String Catalog, icône, diagnostic, workflow `ios` complet, captures de langues | L | INIT-3 ; INIT-2 (référence de comportement) |

Ordre recommandé : INIT-1, puis INIT-2 et INIT-3 en parallèle (INIT-3 dépend surtout des actions humaines du PM, donc à lancer dès que possible), puis INIT-4. Une branche et une PR par plateforme (INIT-2 pour Android, INIT-3 et INIT-4 pour iOS, INIT-1 transverse), chacune avec revue de code en contexte neuf et revue sécurité, QA, recette du PM, merge.

Dans la logique du processus (Android puis iOS), INIT-3 est une exception à valider : c'est une préparation d'infrastructure, non du code applicatif, mise en parallèle uniquement parce que ses délais dépendent d'Apple et de vos actions.

---

*Aucun fichier du projet autre que ce ticket n'a été modifié. Notion n'a pas été modifié.*
