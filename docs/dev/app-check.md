# App Check : plan de mise en place avant les testeurs externes

**Rédigé le 2026-11-11** par `backend-data`. **Documentation seulement** : aucune commande Firebase, aucun accès à la console, rien n'a été modifié sur `miff-3d283`.
**Contact et autorité de décision** : Tomessa MFOUKA, tomessa.mfouka@gmail.com (PM). Seul le PM agit dans les consoles Firebase, Google Play et Apple, et seul le PM décide d'« appliquer ».

**Rôle de ce document** : c'est le **plan** (état, limites, pièges, ordre, tickets). La checklist de console pas à pas reste [`app-check-strict.md`](app-check-strict.md) ; ce plan la complète et signale en §9 ce qu'il faut y corriger. La surveillance quotidienne est dans [`surveillance-quotas.md`](surveillance-quotas.md).

**En une phrase (pour le PM)** : App Check est un contrôle gratuit qui fait refuser à la base toute requête qui ne vient pas d'une vraie application Miff installée par le circuit officiel ; le code des deux apps est déjà prêt, il reste surtout de la **distribution officielle côté Android**, de la **mesure**, puis **un clic** dans la console.

**Recommandation** : (a) **maintenant** : constat de la console (AC-0), correction des documents (AC-1), vérification iOS (AC-3), et lancement de la distribution Android officielle (AC-2), qui est de toute façon nécessaire aux testeurs ; (b) **avant les testeurs externes** : mesure puis application sur **Cloud Firestore** seulement (AC-5 à AC-7) ; (c) **plus tard, peut-être jamais** : application sur Authentication (AC-8), car elle est « Preview », peut empêcher la connexion et casse la page web de réinitialisation du mot de passe.

---

## 1. État actuel (constaté dans le dépôt, `origin/main` au 2026-11-11)

| Sujet | État | Source |
|---|---|---|
| SDK Android | `firebase-appcheck-playintegrity` (releases `release` et `releaseLike`) et `firebase-appcheck-debug` (`debug` seulement), via le BoM Firebase | `android/app/build.gradle.kts`, `android/gradle/libs.versions.toml` |
| Initialisation Android | `AppCheckInstaller.install()` appelé dans `MiffApplication.onCreate`, une version par type de build (`src/debug` : fournisseur de debug ; `src/release` : Play Integrity) | `android/app/src/{debug,release}/.../AppCheckInstaller.kt` |
| SDK et initialisation iOS | `FirebaseAppCheck` ; `AppCheck.setAppCheckProviderFactory(...)` **avant** `FirebaseApp.configure` ; `Debug` : fournisseur de debug (simulateur) ; `Internal` et `Release` : `AppAttestProvider` | `ios/Miff/App/AppCheckFactory.swift`, `FirebaseBootstrap.swift`, `Diagnostic/DebugAppCheck.swift` |
| Entitlement iOS | `com.apple.developer.devicecheck.appattest-environment = production` pour `Internal` et `Release` ; la CI vérifie sa présence quand l'archive est signée (mode `signed`, le défaut) | `ios/Config/Miff.entitlements`, `.github/workflows/ios-testflight.yml` |
| Garde-fous CI | Le debug n'a pas le droit de fuiter dans les builds distribuables : Android cherche `DebugAppCheck` dans le dex release et releaseLike ; iOS cherche `MiffDebugAppCheck` dans l'exécutable Internal et Release | `.github/workflows/android.yml`, `ios-testflight.yml`, `ios/ci/check-binaries.sh` |
| Mode côté Firebase | **« Non appliqué »** (jamais appliqué ; le code le suppose partout). **L'état réel de la console n'est pas vérifiable depuis le dépôt** : apps enregistrées dans App Check ou non, Play Integrity lié ou non, métriques actuelles = **inconnus** (AC-0) | `android/README.md`, `ios/README.md` |
| Écran de diagnostic (debug / Internal) | Une ligne « App Check » dit si un jeton est obtenu (la valeur n'est jamais affichée) ; le test de lecture avec compte de test dit si la base répond | `Diagnostic*` des deux apps |
| Erreur affichée si la base refuse | Refus d'App Check et refus des règles sont **indiscernables** côté client (décision H4) ; message neutre « Pas de connexion pour le moment » | `MiffFailure.kt`, `ProfileWriteErrorsTests.swift` |
| Règles Firestore | **Aucune référence à App Check** : l'application se fait dans la console (voulu) | `backend/firestore.rules` |
| Stockage de fichiers | **Aucun Cloud Storage** (indisponible sur Spark, voir §2) : les photos sont des octets dans Firestore, `miffs/{id}/photos/{uid}` | `backend/data-model.md` §2.8 |
| Secret CI de jeton de debug | `docs/dev/secrets.md` prévoit un secret `APPCHECK_DEBUG_TOKEN`, mais **aucun workflow ne le lit** : la CI ne parle jamais à `miff-3d283` (Android : tests sur JVM avec configuration factice ; iOS : `isRunningUnitTests` empêche le démarrage de Firebase ; règles : émulateur `demo-miff-rules`) | `.github/workflows/*.yml`, `FirebaseBootstrap.swift` |
| Distribution Android actuelle | Le PM installe un **APK de la CI signé avec la clé de debug** (`debug` ou `releaseLike`). **Aucune clé d'envoi, aucune piste Play** n'existe encore (`secrets.md` : « avant la première distribution ») | `.github/workflows/android.yml`, `docs/dev/secrets.md` |
| Distribution iOS actuelle | TestFlight via `ios-testflight` (configuration `Internal`, App Attest) | `ios/README.md` |
| Textes légaux | La politique de confidentialité et la fiche Data safety citent déjà App Check (Play Integrity / App Attest) et le jeton | `docs/legal/politique-confidentialite.fr.md`, `docs/release/google-play-data-safety.md` |

**Conséquence importante (nouvelle par rapport à `app-check-strict.md`)** : aujourd'hui, **aucun build Android que le PM peut installer ne peut obtenir un jeton Play Integrity** (APK hors Play). Les seuls jetons Android valides possibles sont ceux du **fournisseur de debug**, et seulement s'ils sont enregistrés. La partie Android du circuit « officiel » est donc **bloquée par la première distribution via Google Play**.

## 2. Ce qu'App Check protège chez nous, et ce qu'il ne protège pas

D'après la page d'ensemble Firebase ([présentation App Check](https://firebase.google.com/docs/app-check)) : produits protégés = Authentication (**Preview**), Cloud Firestore, Realtime Database, Cloud Storage, Cloud Functions (appelables), etc. ; **Cloud Messaging, Crashlytics, Analytics et Installations ne figurent pas dans la liste** (donc non protégés d'après cette page). La page dit aussi qu'App Check « empêche certains abus, mais pas tous ».

| Service Miff | Protégé ? | Explication simple |
|---|---|---|
| **Cloud Firestore** (toutes les données, dont la recherche de code secret `get joinCodes`) | **Oui, une fois « appliqué »** | Un script qui a obtenu un compte jetable mais n'a pas la vraie app est refusé : c'est la parade principale à l'abus de lectures (K8, `surveillance-quotas.md`). |
| **Authentication** (création de compte, connexion, mot de passe oublié) | **Possible, en Preview, pas recommandé tout de suite** | Bloquerait les créations de comptes par script, mais peut bloquer une vraie connexion si le jeton échoue, et casse la page web `/reset-password` (aucun fournisseur web, `web/README.md` §6). |
| Cloud Storage | Sans objet | Pas utilisé (indisponible sur Spark d'après la [grille des tarifs](https://firebase.google.com/pricing), qui n'affiche aucun quota Spark pour Storage). |
| Cloud Functions | Sans objet | Aucune fonction (pas de Blaze). Si Blaze un jour : les fonctions *appelables* peuvent exiger App Check. |
| Notifications (FCM), Crashlytics, Analytics, Installations | **Non** (non listés) | Ils ne sont pas facturés à la lecture ; l'abus possible y est limité (le consentement Analytics/Crashlytics reste l'unique protection de vie privée). |
| Firebase Hosting (pages légales, reset) | Non | Pages publiques et statiques. |

**Ce qu'App Check ne fait pas** (à ne jamais présenter comme plus que ça) :
- **Il ne remplace pas les règles Firestore** : un utilisateur légitime avec la vraie app reste limité par les règles (un membre ne voit que ses Miffs).
- **Un vrai téléphone avec la vraie app** passe le contrôle : un attaquant équipé d'un appareil réel peut encore créer des comptes jetables et boucler sur `get joinCodes` (risque résiduel K8, assumé, `data-model.md` §5). App Check **augmente le coût** de l'attaque, il ne l'annule pas.
- **Il n'empêche pas** un membre autorisé de mal utiliser ce qu'il a le droit de lire.
- **La protection contre le rejeu de jeton** (jeton à usage unique) est en bêta et, d'après la [page de documentation consultée](https://firebase.google.com/docs/app-check/custom-resource-backend), limitée aux SDK Node.js et Python : **non disponible pour Firestore**.
- Un **jeton de debug volé** contourne tout (voir §5).

## 3. Fournisseurs d'attestation

| | Android : **Play Integrity** | iOS : **App Attest** |
|---|---|---|
| Coût sur Spark | App Check est « sans frais, avec des quotas qui varient selon le fournisseur » ([tarifs](https://firebase.google.com/pricing)). Aucun tarif trouvé pour l'API Play Integrity elle-même. | Idem pour App Check. **Aucun tarif Apple trouvé** dans les pages consultées (absence d'information, pas confirmation de gratuité). |
| Quota | **10 000 requêtes par jour** par projet Cloud (« token requests », partagées entre classique et standard) ([Play Integrity, usage limits](https://developer.android.com/google/play/integrity/setup)) ; mentionné aussi par Firebase ([présentation App Check](https://firebase.google.com/docs/app-check)). Augmentation possible **seulement si l'app est sur Google Play et le projet lié dans la Play Console** ; traitement « jusqu'à une semaine ». | **Aucun chiffre publié.** Apple demande d'**activer progressivement** pour éviter la limitation ; l'échec par limitation pendant la montée en charge est cité comme cas normal ([WWDC21, session 10244](https://developer.apple.com/videos/play/wwdc2021/10244/)). Firebase renvoie aux quotas d'Apple. |
| Durée de vie du jeton Firebase | Réglable de 30 min à 7 jours, **1 h par défaut**, renouvellement vers la moitié de la durée ([Play Integrity provider](https://firebase.google.com/docs/app-check/android/play-integrity-provider)). Plus court = plus de vérifications, de latence et de quota. **Garder 1 h.** | Idem ([App Attest provider](https://firebase.google.com/docs/app-check/ios/app-attest-provider)). |
| Prérequis | (1) **Empreinte SHA-256** du certificat de signature, saisie à l'enregistrement de l'app ; (2) **Play Console > App integrity** (ou « Protected with Play ») : **lier le projet Cloud** ; il faut être **propriétaire (Owner)** du projet, être membre d'un groupe Owner ne suffit pas ; (3) même projet que celui où l'app est enregistrée. | (1) Capacité **App Attest** sur l'identifiant `com.tomessa.miff` + entitlement **`production`** ; (2) Xcode 12.5 ou plus, iOS 14 ou plus ; (3) **rien à saisir dans la console Firebase** pour App Attest. Notre minimum est iOS 16 : **pas besoin du repli DeviceCheck**. |
| Piège « hors du circuit officiel » | Par défaut l'étiquette **`PLAY_RECOGNIZED`** est exigée ; une app non publiée sur Google Play ne peut pas la recevoir. Un APK envoyé à la main échoue. | Les jetons de l'environnement **sandbox** d'App Attest sont **refusés** par App Check. TestFlight / App Store : l'environnement de production est utilisé ; sources non officielles (forum Apple), et la page d'entitlement Apple n'a pas pu être lue : **à constater sur l'iPhone** (déjà prévu, `ios/README.md`, plan B). |
| Émulateurs / simulateurs | Un émulateur qui ne passe pas Play Integrity donne un verdict d'appareil vide : **fournisseur de debug**. | Le simulateur ne passe pas App Attest : **fournisseur de debug**. |
| Appareils sans services Google | Pas de jeton : l'app ne fonctionnera plus pour eux après application (public non visé). | `isSupported` peut être faux dans certains cas (extensions d'app) : **Miff n'a pas d'extension**. |

**Pièges propres à Miff**
1. **APK de la CI sur l'OPPO** : un APK `releaseLike` (signé debug) ou `debug` ne passe pas Play Integrity. Après application, il **ne fonctionnera que s'il est en `debug` avec un jeton enregistré**, ou installé depuis Google Play. Le `releaseLike` (mesure du temps de démarrage) deviendra inutilisable hors Play : à prévoir pour les recettes de performance.
2. **Testeurs Android externes** : uniquement via Google Play (test interne, puis test fermé, qui est aussi la voie imposée par la règle des 12 testeurs, `docs/release/decisions-stores-avant-soumission.md` §6). Jamais d'APK envoyé à la main. *Non vérifié* : que l'étiquette `PLAY_RECOGNIZED` soit bien donnée à un build de la piste de test interne (la documentation Firebase parle d'apps « publiées sur Google Play ») : **à constater dès la première piste interne**, avant toute application.
3. **Géofence en arrière-plan** (le geste central de Miff) : l'écriture d'un statut quand l'app est réveillée par une zone a besoin d'un jeton valide. Si l'appareil (surcouche OPPO qui coupe les apps) n'arrive pas à en obtenir un au bon moment, *à ma connaissance* la base refuse l'écriture et le client n'en refait pas une (comportement de Firestore pour un refus définitif, **non vérifié dans la documentation consultée**). C'est le risque produit n° 1 de l'application : il se **teste** (AC-5, cas « arrière-plan, plus d'une heure d'inactivité »), il ne se suppose pas.
4. **Latence** : la première attestation d'une session peut ajouter quelques secondes (« quelques secondes, majorité sous 10 s » pour l'initialisation de Play Integrity, [documentation Android](https://developer.android.com/google/play/integrity/standard)). Le splash masque déjà l'initialisation (`docs/tickets/onboarding.md`) ; à mesurer en AC-5.
5. **iOS TestFlight** : le premier build signé doit **prouver** qu'il obtient un jeton (ligne « App Check » du diagnostic). Si le PM voit « Pas de jeton pour le moment », ne rien appliquer (`ios/README.md`, plans B et C).
6. **Quota Play Integrity vs nombre d'utilisateurs** (estimation de l'agent, **pas une mesure**) : un utilisateur actif demande environ 5 à 15 jetons par jour (ouvertures de l'app + réveils en arrière-plan, durée de vie 1 h). Soit **de l'ordre de 700 à 2 000 utilisateurs actifs par jour** avant d'atteindre 10 000. La demande d'augmentation prend jusqu'à une semaine : la déposer **à 50 %** (5 000 appels/jour), pas à 100 %.

## 4. Plan de déploiement par étapes (sans casser les testeurs actuels)

Principe : **l'ordre ne se raccourcit pas** : constat, enregistrement, mode « non appliqué », mesure, application (Firestore d'abord), vérification. Jamais le même jour qu'un déploiement de règles (`deploiement-regles.md`) ni qu'une nouvelle version distribuée.

| Étape | Quoi | Condition de passage |
|---|---|---|
| 0. Constat | Le PM relève dans la console ce qui existe : apps enregistrées dans App Check, produits en « non appliqué », métriques actuelles (AC-0). | Tableau rempli. |
| 1. Enregistrer iOS | App iOS dans App Check, fournisseur App Attest (rien d'autre à saisir). Un build `Internal` signé sur l'iPhone doit afficher « Jeton obtenu ». | Jeton obtenu sur l'iPhone 11 Pro. |
| 2. Distribuer Android par Google Play | Clé d'envoi, Play App Signing, app dans la Play Console, **piste de test interne**, lien du projet Cloud dans la Play Console, **SHA-256 de la clé de signature Play** saisie dans l'enregistrement App Check. (AC-2, ticket de distribution.) Pas à pas, et build signé par le workflow `android-release` : `docs/release/checklist-android.md`. | Un build installé **depuis Play** sur l'OPPO affiche « Jeton obtenu ». |
| 3. Mode « non appliqué » + mesure | Parcours complets sur les deux appareils, par le circuit officiel, au moins **48 h** d'usage réel (voir mesures ci-dessous). | Critères de décision ci-dessous tous vrais. |
| 4. Appliquer **Cloud Firestore** | Console > App Check > API > Cloud Firestore > Appliquer. Effet en **15 minutes au plus** ([activer l'application](https://firebase.google.com/docs/app-check/enable-enforcement)). | Vérifications de l'étape 5. |
| 5. Vérifier | Les deux appareils : connexion, fil, création d'une mise à jour, **un événement de zone en arrière-plan**. Une requête sans jeton est refusée (AC-4 + AC-7). Le lendemain : toujours ~100 % vérifiées, lectures sans baisse inexpliquée. | R18 du ticket de durcissement cochable. |
| 6. Authentication (plus tard) | Seulement après une décision séparée (AC-8). | Décision écrite du PM. |

**Mesures pour décider** (console : Sécurité > App Check > API > Cloud Firestore, vue des métriques). Définitions officielles ([surveiller les métriques](https://firebase.google.com/docs/app-check/monitor-metrics)) : *vérifiées* = jeton valide ; *client obsolète* = pas de jeton ; *origine inconnue* = pas de jeton et ne ressemble pas au SDK Firebase ; *invalides* = jeton invalide (app imitée, émulateur).

Passer à l'application **seulement si tout est vrai** :
1. Sur **24 h complètes**, au moins **99 %** des requêtes sont *vérifiées*, avec **au moins 200 requêtes par plateforme** sur la période (en dessous, un pourcentage ne veut rien dire : refaire des parcours). La documentation ne publie aucun seuil ; ceux-ci sont une proposition de l'agent. *Si la console ne sépare pas Android et iOS*, mesurer chaque plateforme sur une plage où seule elle est utilisée.
2. Zéro requête *origine inconnue* ou *invalide* **non expliquée** (une explication = un build debug dont le jeton n'était pas enregistré ; la corriger).
3. Chaque appareil a fait le parcours complet, y compris le cas « arrière-plan après plus d'une heure d'inactivité ».
4. Aucun testeur n'utilise un APK hors Play (sinon : le basculer sur la piste Play avant).
5. Aucune version distribuée depuis moins de 24 h.

**Retour arrière** : console, même écran, repasser Cloud Firestore en « non appliqué ». **La page officielle consultée ne décrit pas le retour arrière** ; l'option existe à ma connaissance : **le PM vérifie qu'elle est visible sur l'écran avant de cliquer sur Appliquer**. Délai de prise d'effet du retour : supposé identique (jusqu'à 15 minutes), non confirmé. Déclencheurs : un testeur légitime reçoit durablement « Pas de connexion pour le moment » alors qu'il a du réseau ; une baisse inexpliquée des lectures de plus de 30 % d'un jour à l'autre ; des écritures de zone qui n'arrivent plus. Ensuite : chercher la cause avec le tableau de `app-check-strict.md`, recommencer à l'étape 3.

## 5. Changements de code et de configuration

**Constat : l'essentiel est déjà fait** (§1). Rien n'est à changer dans les règles Firestore (`firestore.rules`), l'application se fait dans la console.

| # | Changement | Plateforme | Pourquoi | Ticket |
|---|---|---|---|---|
| C1 | **Interrupteur de debug « sans App Check »** : un fournisseur qui ne donne aucun jeton, installable seulement en `debug` (réglage mémorisé dans l'écran de diagnostic, pris au lancement suivant). Nom de classe contenant `DebugAppCheck` pour être couvert par le contrôle CI existant (absent des builds release et releaseLike). | Android | La preuve « une requête sans jeton est refusée » (R18) n'a aujourd'hui aucun build pour la faire. Une seule plateforme suffit : l'application est par produit Firebase, pas par plateforme. | AC-4 |
| C2 | Aucun changement de fournisseur, d'initialisation ni de durée de vie du jeton. | les deux | L'ordre d'initialisation (App Check avant les autres services) est déjà correct. | |
| C3 | Vérifier, sur le build `Internal` signé, que l'entitlement `production` est bien dans le binaire et que le diagnostic dit « Jeton obtenu ». | iOS | Plan B/C déjà écrits dans `ios/README.md`. | AC-3 |
| C4 | Libellés du diagnostic « Il n'est pas encore exigé » : à reformuler au moment de l'application (le texte serait faux ensuite). | les deux | Clés `diag_appcheck_*`, parité Android/iOS à garder (`docs/contracts/i18n-keys.md`). | AC-7 |
| C5 | Message d'erreur si la base refuse : déjà neutre (« Pas de connexion pour le moment »). Aucun changement ; ne pas créer de message « App Check » (refus indiscernable des règles, décision H4). | les deux | Ton non anxiogène. | |

**Jetons de debug (règles, sans exception)**
- Un jeton de debug **contourne** le contrôle. Il vit **hors du dépôt** : `.gitignore` bloque déjà `appcheck-debug-token*` et `*appcheck*debug*token*`. Jamais dans un ticket, une PR, une conversation, une capture d'écran, un journal de CI.
- **Pas de jeton dans la CI** : elle ne contacte jamais le projet réel (voir §1). Donc **ne pas créer** le secret GitHub `APPCHECK_DEBUG_TOKEN`. Si un jour des tests instrumentés parlent à Firebase, ce sera avec le **projet de développement** et un jeton propre à lui (la [documentation](https://firebase.google.com/docs/app-check/android/debug-provider) décrit le mécanisme : secret de CI exposé en variable d'environnement, passé au test).
- **Jetons des appareils du PM** : un jeton **par installation de debug** (Android : visible dans Logcat, étiquette `DebugAppCheckProvider` ; iOS simulateur : console Xcode ; réinstaller l'app en crée un nouveau, [doc iOS](https://firebase.google.com/docs/app-check/ios/debug-provider)). Enregistrés dans la console Firebase (Gérer les jetons de débogage), nommés par appareil, **supprimés dès que l'appareil passe en build Play**.
- À terme, les jetons de debug n'existent **que** sur le projet de développement (`CLAUDE.md`, environnements) ; sur `miff-3d283`, seulement ceux des deux appareils du PM, le temps de la transition.
- Ne **jamais** afficher le jeton dans l'écran de diagnostic pour « faciliter la copie » : c'est un secret.

## 6. Ce que le PM fait (pas à pas, sans jargon)

Les libellés exacts peuvent différer un peu à l'écran : le chemin général est **Firebase > Sécurité > App Check**. **Toujours vérifier en haut de la console que le projet est « Miff » (`miff-3d283`), jamais Ghostrun.** Détail des écrans : [`app-check-strict.md`](app-check-strict.md).

| # | Action | Quand | Besoin d'un accord explicite ? |
|---|---|---|---|
| P1 | **Regarder, sans rien changer** : onglet « Apps » (les deux apps sont-elles listées ?), onglet « API » (Cloud Firestore est-il « Non appliqué » ?). Noter ce que tu vois et me le dire (AC-0). | Maintenant | Non |
| P2 | **iOS** : installer le dernier build TestFlight sur l'iPhone, ouvrir le diagnostic, toucher « Vérifier » dans App Check. Si « Jeton obtenu » : enregistrer l'app iOS dans App Check avec le fournisseur **App Attest**. Sinon : ne rien enregistrer, me le dire. | Maintenant | Non |
| P3 | **Android, côté Google Play** : avec l'équipe de livraison (ticket de distribution), créer l'app dans la Play Console, activer Play App Signing, ouvrir une **piste de test interne**, puis **Release > App integrity (ou Protected with Play) > Play Integrity API > Lier le projet Cloud** (projet `miff-3d283` ; il faut en être **propriétaire**). | Avant les testeurs Android | **Oui** (création de l'app, signature) |
| P4 | **Android, côté Firebase** : App Check > Apps > enregistrer l'app Android avec **Play Integrity** et l'**empreinte SHA-256 de la clé de signature de l'application**, affichée dans la Play Console (pas celle de la clé d'envoi). Laisser la durée du jeton à **1 heure**. | Après P3 | Non |
| P5 | **Appareils de test en debug** : pour chaque appareil sur lequel tu installes un build de la CI, enregistrer son jeton de debug (Apps > menu de l'app > Gérer les jetons de débogage). Je t'aiderai à le lire dans Android Studio sans l'afficher dans une conversation. | Avant d'appliquer | Non |
| P6 | **Mesurer** 48 h : tableau de `app-check-strict.md` (étape 3), une ligne par jour. | Avant d'appliquer | Non |
| P7 | **Appliquer Cloud Firestore** (un clic + confirmation), après avoir vérifié que l'option pour **revenir en arrière** est visible sur l'écran. Attendre 15 minutes, vérifier sur les deux téléphones. | Avant les testeurs externes | **Oui, explicite et daté** |
| P8 | **Ne PAS toucher** à Authentication tant que AC-8 n'est pas décidé. Ne saisir **aucun moyen de paiement** : rien ici n'en demande (si un écran en demande, t'arrêter). | Toujours | |

## 7. Risques et signaux de bascule vers un plan payant

| Risque | Effet | Signal | Réponse (du moins cher au plus cher) |
|---|---|---|---|
| Application trop tôt | Un testeur légitime est bloqué | Plainte « pas de connexion », lectures qui baissent | Retour arrière (§4), corriger, remesurer |
| Quota Play Integrity (10 000 appels/jour) atteint | Plus de jeton Android, donc plus d'accès une fois appliqué : **panne pour tous les Android** | Appels à 50 % de 10 000 sur la console Google Cloud (API Play Integrity > Métriques, **emplacement à confirmer**) ; plus de 700 utilisateurs actifs/jour environ | Demande d'augmentation via la Play Console (gratuite, jusqu'à une semaine, app sur Play + projet lié) ; **pas de lien trouvé avec Blaze** |
| Limitation Apple pendant la montée en charge | Des iPhone sans jeton | Part des requêtes iOS non vérifiées qui monte lors d'une vague d'invitations | Inviter par **petites vagues** (recommandation d'Apple), pas des centaines d'iPhone le même jour |
| Abus avec de vrais appareils (K8) | Lectures épuisées malgré App Check | Signaux de `surveillance-quotas.md` | E-mail vérifié (gratuit) ; puis décision Blaze + fonction « rejoindre » à compteur d'essais (payant, décision du PM) |
| Jeton de debug fuité | Contournement | Requêtes « vérifiées » sans raison, lectures inhabituelles | Supprimer le jeton dans la console, en recréer un |
| Fournisseur web absent | Page de réinitialisation refusée si Authentication est appliqué | Impossible de réinitialiser un mot de passe | Ne pas appliquer Authentication, ou fournisseur web (reCAPTCHA Enterprise : gratuit jusqu'à 10 000 évaluations par mois d'après la [présentation](https://firebase.google.com/docs/app-check), conditions de facturation à vérifier) |

**App Check lui-même est gratuit sur Spark.** Le plan payant n'est pas déclenché par App Check, mais par l'abus de lectures qu'il ne bloque pas entièrement ; les seuils de bascule restent ceux de `surveillance-quotas.md` (quota Firestore : 50 000 lectures et 20 000 écritures par jour, [tarifs](https://firebase.google.com/pricing)). Aucun agent n'active Blaze.

**Risques juridiques connus (risque assumé par le PM, décision du 2026-10-23, aucune relecture juridique)** : App Check échange des signaux d'appareil avec Google (Play Integrity) et Apple (App Attest) : la politique de confidentialité et la fiche Data safety les citent déjà ; localisation de ces traitements « non précisée » (J5 de `docs/legal/README.md`). Ce plan ne modifie aucun texte légal et n'affirme aucune conformité.

## 8. Découpage en tickets (ordre et taille)

Tailles : XS < 1 h, S ~ 1/2 journée, M ~ 1 journée, L > 1 journée. Ce plan couvre la partie « App Check strict » du ticket **DUR-3** (`docs/tickets/durcissement-regles-firestore.md`, critère R18).

| Ordre | Ticket | Qui | Taille | Dépend de | Quand |
|---|---|---|---|---|---|
| 1 | **AC-0** Constat de la console (P1) et envoi du tableau | PM | XS | | Maintenant |
| 2 | **AC-1** Corriger les documents : retirer `APPCHECK_DEBUG_TOKEN` de `secrets.md` (ou le marquer « non utilisé »), ajouter les constats de ce plan à `app-check-strict.md` (§9) | `backend-data` | XS | | Maintenant |
| 3 | **AC-3** Vérifier le jeton App Attest sur l'iPhone (build `Internal` signé), enregistrer l'app iOS (P2) | PM + `ios-dev` en support | S | | Maintenant |
| 4 | **AC-2** Distribution Android par Google Play (clé d'envoi, Play App Signing, piste interne, lien Play Integrity, SHA-256, enregistrement) : à fusionner avec le ticket de première distribution | PM + `release-manager` + `android-dev` | **L** | | Maintenant (chemin critique) |
| 5 | **AC-4** Interrupteur de debug « sans App Check » (C1) + test unitaire + vérification CI (marqueur absent des builds distribuables) | `android-dev` | S | | Pendant AC-2 |
| 6 | **AC-5** Mesure 48 h, jetons de debug des appareils, parcours complets dont l'**événement de zone en arrière-plan après plus d'une heure d'inactivité sur l'OPPO** | PM + `qa-tester` | M | AC-2, AC-3, AC-4 | Avant les testeurs externes |
| 7 | **AC-6** Décision écrite du PM : appliquer ou non, daté, avec le tableau de mesure | PM | XS | AC-5 | idem |
| 8 | **AC-7** Appliquer Cloud Firestore (P7), reformuler les libellés `diag_appcheck_*` (C4), preuve de refus avec le build AC-4, vérification le lendemain, cocher R18 | PM + `android-dev`/`ios-dev` | S | AC-6 | idem |
| 9 | **AC-8** Décision sur Authentication (après mesure de son onglet, test de connexion e-mail et Google sur les deux téléphones, avenir de la page web de réinitialisation) | PM + `backend-data` | S | AC-7 + au moins 1 semaine | Plus tard, peut-être jamais |
| 10 | **AC-9** Projet Firebase de développement séparé, jetons de debug uniquement là, **mesure : une requête refusée par App Check est-elle comptée dans les lectures Firestore ?** (jamais sur `miff-3d283`, avec l'accord du PM) | PM + `backend-data` | M | création du projet de développement | Plus tard |

Chemin critique : **AC-2** (dépend du compte Play et de la signature). Si AC-2 n'est pas prêt avant les testeurs externes, **ne pas appliquer** : les testeurs Android seraient bloqués. Alternative dégradée, à décider par le PM : n'ouvrir que l'iOS aux testeurs externes pendant ce temps (l'application est par produit, pas par plateforme : appliquer sur Firestore bloque aussi les Android hors Play).

## 9. Corrections à apporter aux documents existants (AC-1) et points non vérifiés

**À corriger**
- `docs/dev/secrets.md` : le secret `APPCHECK_DEBUG_TOKEN` n'est lu par aucun workflow ; ne pas le créer.
- `docs/dev/app-check-strict.md` : (a) ajouter que le circuit Android officiel n'existe pas encore (APK de la CI = hors Play) ; (b) l'écran « mesure » doit exiger un volume minimal avant de lire un pourcentage ; (c) ajouter le cas « géofence en arrière-plan » et le piège du `releaseLike` ; (d) la phrase « retour arrière en 15 minutes » vient de la documentation sur l'**application**, pas sur le retour : à reformuler ; (e) le test sans jeton (étape 5, ligne 3) renvoie à un build qui n'existe pas : AC-4.
- `CLAUDE.md` : la ligne « À prévoir plus tard : Firebase App Check » est périmée côté code (initialisé depuis INIT-2/INIT-4) ; reste à faire = console et distribution. À proposer au PM, non modifié ici.

**Non vérifié (à ne pas affirmer tant que ce n'est pas constaté)**
- L'état réel de la console (apps enregistrées, liaison Play, métriques) : pas d'accès.
- Que la piste de **test interne** Play donne `PLAY_RECOGNIZED` ; que la console accepte d'enregistrer l'app Android avant l'existence de la clé de signature Play ; que les métriques soient séparables par plateforme.
- Existence et délai du bouton de retour arrière ; si les requêtes refusées comptent dans les quotas Firestore (la documentation consultée ne le dit pas).
- Tarification et quotas chiffrés d'**Apple** pour App Attest (pages Apple dynamiques non lisibles par l'outil ; seule la session WWDC21 a pu être lue).
- Environnement App Attest réellement utilisé par TestFlight (source : forum Apple, non officielle).
- Comportement exact d'une écriture Firestore refusée faute de jeton, en arrière-plan (§3, piège 3).
- Exigences de la Preview d'Authentication et liste exacte des appels couverts.
- Consommation réelle de jetons par utilisateur (§3, piège 6 : estimation).

## 10. Sources officielles consultées (2026-11-11)

- [Firebase App Check, présentation](https://firebase.google.com/docs/app-check) (produits protégés, fournisseurs, quotas)
- [Fournisseur Play Integrity](https://firebase.google.com/docs/app-check/android/play-integrity-provider)
- [Fournisseur App Attest](https://firebase.google.com/docs/app-check/ios/app-attest-provider) et [fournisseur DeviceCheck](https://firebase.google.com/docs/app-check/ios/devicecheck-provider)
- [Fournisseur de debug Android](https://firebase.google.com/docs/app-check/android/debug-provider) et [iOS](https://firebase.google.com/docs/app-check/ios/debug-provider)
- [Activer l'application](https://firebase.google.com/docs/app-check/enable-enforcement) et [surveiller les métriques](https://firebase.google.com/docs/app-check/monitor-metrics)
- [Tarifs Firebase](https://firebase.google.com/pricing)
- [Play Integrity : configuration et limites d'usage](https://developer.android.com/google/play/integrity/setup) et [requêtes standard](https://developer.android.com/google/play/integrity/standard)
- [Apple, WWDC21 « Mitigate fraud with App Attest and DeviceCheck »](https://developer.apple.com/videos/play/wwdc2021/10244/)
