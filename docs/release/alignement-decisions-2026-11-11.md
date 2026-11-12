# Alignement des documents de publication sur les décisions du PM (2026-11-11)

Préparé le 2026-11-11 par `release-manager` (PR `docs/aligner-decisions-publication`). **Rien n'est publié, déployé, soumis ni rempli dans une console.** Ces textes ne sont **pas** relus par un juriste (décision du PM du 2026-10-23, risque assumé) et ne doivent jamais être présentés comme tels. Rien n'est « prêt à soumettre » : la checklist QA `docs/qa/release-checklist.md` n'existe pas encore.

## 1. Les décisions du PM prises en compte

| # | Décision | Date |
|---|---|---|
| D1 | **Gratuit = 3 lieux de confiance** (2 depuis le 2026-11-01, 1 avant) ; 1 Miff à la fois | 2026-11-10 |
| D2 | **« Enregistrer cette photo »** (plein écran de la photo d'un proche) : Android, icône en haut à droite, enregistre par un geste explicite dans `Pictures/Miff`, **sans permission** ; iPhone, enregistrement direct dans la galerie par une autorisation « ajout seulement » (`NSPhotoLibraryAddUsageDescription`, PR #158, **brouillon, non fusionnée**). Formule honnête : « **seulement si tu choisis de l'enregistrer** », jamais « jamais enregistrée » | 2026-11-08, modalités du 2026-11-11 |
| D3 | **Photo Google** reprise à l'inscription : téléchargée **une fois**, en HTTPS, depuis `googleusercontent.com` ; **l'adresse IP de l'appareil est exposée à Google** | 2026-11-08 |
| D4 | **Mode voyage** : **la position reste partagée** ; carte grise ; ne jamais écrire que le voyage arrête la position | 2026-11-10 |
| D5 | Position stockée côté serveur (dernière position seulement) ; notifications **locales** sans serveur (pas de push FCM) ; zone de danger = Miff+ ; offre Miff+ (essai 7 jours sur l'annuel, 59,88 EUR par an, 6,99 EUR par mois, « presque 30 % d'économie ») | 2026-10-31 à 2026-11-07 |

## 2. Ce qui a été corrigé dans cette PR (textes de référence)

| Fichiers | Correction |
|---|---|
| `docs/legal/cgu.fr.md`, `cgu.en.md` | D1 : art. 5 et 9 (3 occurrences par langue). D2 : art. 8 (mention factuelle de la copie par capture ou par « Enregistrer cette photo », sans règle de non-republication). D3 : art. 4. Art. 6 : « dernière position et, si tu publies une photo, l'endroit pendant 24 heures » |
| `docs/legal/politique-confidentialite.fr.md`, `.en.md` | D2 : point 7 du résumé, art. 3 (« Caméra et photo », « Ce que Miff ne collecte pas »), art. 4, 5, 7, 10, 11. D3 : art. 3 (« Profil »), art. 5, art. 6 (ligne « Serveurs d'images de Google », IP exposée). D4 : art. 3 (« Statut automatique »), art. 5. D5 : art. 3 (« Notifications », **au futur** : les rappels locaux ne sont pas encore dans `main` ; aujourd'hui aucun push, aucun jeton) |
| `docs/legal/README.md` | §8 (récapitulatif), risques J38 et J39, lignes « à aligner » marquées « fait » |
| `docs/release/update-photo-declarations.md` | Nouveau §16 (modalités D2 et D3, liste des permissions Android et iOS, déclarations des stores) ; lignes 61, 74, 108, 130, 148 corrigées |
| `docs/release/README.md` | Contrôles V22 (enregistrement), V23 (voyage), V24 (photo Google) ; ce document ajouté à la liste |
| `docs/release/google-play-data-safety.md`, `app-store-confidentialite-et-revue.md` | Lignes « Photos » complétées ; `NSPhotoLibraryAddUsageDescription` FR/EN (§4) ; deux paragraphes ajoutés à la note de revue Apple (§5) |
| `docs/release/changements-testeurs-externes.md`, `decisions-stores-avant-soumission.md`, `photo-profil-google-a-aligner.md` | 3 lieux ; rappels locaux ; enregistrement de la photo, photo Google, mode voyage |
| `marketing/store-listing-ios.md`, `store-listing-android.md`, `launch-plan.md`, `positioning.md` | 3 lieux (FR et EN) ; phrase sur la copie d'une photo |
| `web/src/landing.json`, `web/tests/landing.test.mjs`, `web/README.md` | 3 lieux (FR et EN) ; « Miff ne garde que ta dernière position » devenu faux (l'endroit de la photo est gardé 24 h) : `t2Text`, `t4Text`, `who2What` |
| `docs/specs/*` (README, paywall-premium, miff-groupes, localisation-lieux-de-confiance, lieux-de-confiance) | 3 lieux, « 4e lieu » pour le paywall |
| `docs/tickets/initialisation-technique.md`, `onboarding.md` | 3 lieux ; critère PR8 : photo Google préremplie (remplace « aucune photo importée ») |
| `design/screens/lieux/*.md` | 3 lieux, texte du paywall et compteur ; encadré sur les maquettes à régénérer |

**Non modifié volontairement** : `docs/legal/consentement-localisation-v3.md` (il ne parle ni de copie ni d'enregistrement ; le changer changerait sa version et imposerait un re-consentement) ; version « 0.1 » et date « 4 novembre 2026 » des textes légaux ; historiques de décisions datées (tickets, `backend/data-model.md`, `docs/dev/lieux-gratuits-3-clients.md` hors une ligne d'état) ; `docs/tickets/feed.md` et les checklists QA (la PR #158 les modifie : conflit évité).

## 3. À faire au moment de la publication (avec les fichiers)

### 3.1 Par le PM (aucun agent ne le fait)

| # | Action | Où |
|---|---|---|
| P1 | **Relire** les textes légaux modifiés (diff de cette PR) : ils sont sous ta seule responsabilité, sans relecture juridique ; trancher les points ambigus du §4 | `docs/legal/*.md` |
| P2 | Actions de console Google Analytics (durée 2 mois, collecte précise désactivée dans l'UE) **avant** de publier la politique | `docs/legal/README.md` §3 |
| P3 | Dire **« oui, déploie »** pour publier le site (Firebase Hosting, projet `miff-3d283` uniquement) : page d'accueil, CGU, politique, suppression de compte | `web/`, `scripts/web-predeploy-check.mjs` |
| P4 | À la soumission seulement : remplir **Data safety**, **App Privacy**, la note de revue, la classification d'âge, avec les réponses identiques partout | `google-play-data-safety.md`, `app-store-confidentialite-et-revue.md`, `update-photo-declarations.md` §5, §6, §16 |
| P5 | Vérifier dans Play Console et TestFlight si un testeur externe a accepté le consentement `2026-11-v03` d'origine (sinon plan v4) | `docs/legal/consentement-localisation-v3.md` §1 bis |
| P6 | Décider s'il faut une **règle d'usage** dans l'art. 8 des CGU (ne pas republier une photo enregistrée). **Elle n'est PAS écrite aujourd'hui** (jamais décidée) ; seule la mention factuelle de la copie y figure | `docs/legal/cgu.fr.md`, `cgu.en.md` art. 8 |
| P7 | Décider de l'**illustration des pastilles** du paywall (2 pleines + 1 en pointillé aujourd'hui) pour 3 lieux | `design/screens/lieux/paywall-contextuel-lieux.md` |

### 3.2 Par les agents (avant de déclarer quoi que ce soit « prêt »)

| # | À faire | Fichiers | Qui |
|---|---|---|---|
| A1 | **iOS : `freePlaces = 2` encore dans `main`** (la PR #145 le passe à 3) ; clés `place_list_counter_free` et `place_paywall_limit_title` à 3 ; tant que ce n'est pas fait, la fiche iOS (C13) et la page d'accueil décrivent 3 lieux alors que l'app en donne 2 | `ios/Miff/Models/Place/PlaceRules.swift`, `Localizable.xcstrings` ; `docs/dev/lieux-gratuits-3-clients.md` | `ios-dev` |
| A2 | **Après fusion de la PR #158** (enregistrement iOS) : relire `update-photo-declarations.md` §13 bis (la PR le remplace) et §16, V11 et V22 de `docs/release/README.md`, `docs/legal/README.md` (§ du 2026-11-08), `docs/tickets/feed.md` D52 ; vérifier que le texte d'usage et le moment de la demande (« au premier toucher ») sont ceux de la politique art. 3 ; résoudre les conflits de fusion éventuels sur ces fichiers | idem | `release-manager` |
| A3 | **Libellé du bouton** : la décision dit « Enregistrer cette photo » (iOS, PR #158) ; Android a `feed_photo_download` = « Enregistrer la photo » (infobulle « Enregistrer cette photo sur ton téléphone »). Les textes légaux écrivent « Enregistrer cette photo » : aligner l'app ou le texte | `android/app/src/main/res/values*/strings.xml` | `android-dev` |
| A4 | Écran d'explication de la caméra et ligne de visibilité de l'aperçu : ajouter « … et ton cercle peut l'enregistrer » (code des apps, ticket Update §7) ; ne pas étendre « rien n'est enregistré sur ton téléphone » à la photo qu'on **reçoit** | `docs/tickets/update.md` (lignes 30, 147, 157, 204, 293, 481), apps | `product-specs`, devs |
| A5 | **Checklists QA** à rejouer avec 3 lieux (« 3 lieux sur 3 », « Tu as atteint 3 lieux ») et à limiter pour « aucune écriture dans la galerie » à la prise de vue | `docs/qa/checklists/loc-1-android-recette-pm.md`, `loc-3-ios-recette-pm.md`, `update-android-recette-pm.md` et l'équivalent iOS | `qa-tester` |
| A6 | **Maquettes à régénérer** avec 3 lieux (texte « 2 lieux » encore dans le HTML et les scripts) | `design/screens/lieux/mockups/14-paywall-limite-lieux.html`, `17-liste-des-lieux.html`, `screens-misc.mjs` ; `design/exports/profil/README.md` (description d'une maquette d'origine, à laisser telle quelle) | `design-system` |
| A7 | **Mode voyage** : vérifier (V23) qu'aucun texte d'app, de fiche ou de page web ne dit que le voyage arrête la position ; la politique cite le statut « Traveling until / En voyage jusqu'au » avant que la fonction soit livrée sur iOS et Android (même situation que J24) | `docs/legal/politique-confidentialite.*.md` art. 3 et 5 | devs, `release-manager` |
| A8 | **Notifications** : « rappels programmés par le téléphone » décrit NOTIF-P0, dont les PR ne sont pas toutes fusionnées ; les fiches des stores disent encore « pas de notification » (vrai tant que NOTIF-P0 n'est pas livré) : retirer cette phrase quand c'est livré. **Le jour où un push FCM/APNs est ajouté** : politique art. 3 et 6, Data safety, App Privacy, V7. **Mise à jour du 2026-11-12 (SRV-3, PR `docs/legal-jetons-push`) : la politique (art. 1, 3, 5, 6, 7, 8, 10, 11), les CGU (avertissement, art. 6), la ligne « jeton » de Data safety, la ligne « Device ID » d'App Privacy et V7 décrivent maintenant le push « {Prénom} a donné des nouvelles ». Il reste, au jour de la livraison : les fiches des stores (`marketing/store-listing-*.md`, `screenshots-brief.md`, `changements-testeurs-externes.md` point 7, `launch-plan.md`) qui disent encore « pas de notification »** | `marketing/store-listing-*.md` (§0 C14, case finale), `docs/legal/` | `release-manager`, `aso-marketing` |
| A9 | **Miff+ à l'activation** : retirer la ligne « Dans cette version… 3 lieux » des fiches, remplir les achats intégrés, lire les prix depuis les stores (jamais en dur) ; texte « presque 30 % d'économie » / « almost 30% off » seulement, à recalculer si les tarifs changent (28,6 % réels) ; l'écran « Miff+ arrive bientôt » reste à décider | `marketing/store-listing-*.md`, `decisions-stores-avant-soumission.md` §16 | PM, `aso-marketing` |
| A10 | **Date et version des textes légaux** (« 0.1 — 4 novembre 2026 ») : mettre à jour le jour réel de la mise en ligne ; ré-acceptation des CGU non prévue (J32) : les testeurs déjà inscrits n'ont pas accepté les nouveaux textes | `docs/legal/*.md`, `termsVersion` dans les apps | `release-manager`, `backend-data` |
| A11 | Vérifier dans le code livré : V12 (photo sans métadonnée), V22, V24 (photo Google téléchargée une seule fois, brouillon local effacé) | `docs/release/README.md` | devs |
| A12 | La ligne « Autres messages dans l'app » (texte de 140 caractères) des déclarations de stores reste à retirer (le texte n'est pas livré : `updateText = null`) ; non touchée ici | `google-play-data-safety.md`, `app-store-confidentialite-et-revue.md` §2 | `release-manager` |

## 4. Points ambigus, non tranchés (jamais devinés)

1. **CGU art. 8, règle de non-republication d'une photo enregistrée** : proposée par les documents du 2026-11-08, jamais décidée par le PM : **retirée du texte** ; reste à décider (P6 ci-dessus).
2. **Le texte de consentement v3 ne cite pas la copie d'une photo** : volontaire (changer sa version imposerait un re-consentement) ; à confirmer par le PM.
3. **Mode voyage dans la politique** : ajouté parce que le statut « en voyage » et sa date de fin sont écrits dans le statut et visibles des proches ; aucune décision du PM ne dit s'il faut le citer dans les textes légaux.
4. **Texte iPhone de l'ajout seul à la galerie** (politique art. 3, `NSPhotoLibraryAddUsageDescription`, « demandée au premier toucher ») : il dépend de la PR #158 (non fusionnée) ; **à relire à la fusion de #158**.
5. **Android 9 et moins** : le sélecteur de documents remplace le dossier `Pictures/Miff` (décrit dans la politique, art. 3) ; minSdk est 26, donc ces versions existent.
6. **Rien n'est dit du prix ni de l'essai de Miff+ dans les textes légaux** (affichés par les stores) : cohérent avec la décision du 2026-11-07, mais les CGU parlent d'« éventuelle période d'essai ».

## 5. Vérifications faites

- `node --test "web/tests/*.test.mjs"` : 70 tests passés (la page d'accueil et les pages légales générées sont contrôlées) ; `scripts/check-forbidden-files.mjs` : aucun fichier interdit ; plus aucun « 2 lieux » dans les textes de référence listés au §2 (les occurrences restantes sont des historiques datés, des maquettes ou du code, voir §3.2).
- Règles des stores : la nécessité de `NSPhotoLibraryAddUsageDescription` pour l'ajout seul à la galerie (iPhone) et l'absence de permission pour une écriture MediaStore dans `Pictures` (Android 10 et plus) ont été recoupées par recherche web le 2026-11-11, **sur des sources secondaires (forums Apple, tutoriels)** : les pages officielles d'Apple et de Google n'ont pas été lues. `release-manager` les relit avant la soumission (voir `update-photo-declarations.md` §14).
