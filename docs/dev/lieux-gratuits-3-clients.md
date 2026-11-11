# 3 lieux de confiance gratuits : valeurs à changer côté clients et textes

**Décision du PM, 2026-11-10** : la limite gratuite passe de **2 à 3 lieux de confiance** ; au-delà de 3 = Premium (Miff+). Le reste ne change pas (1 Miff à la fois, zones de danger réservées au Premium, seul le créateur crée ses lieux).

**État au 2026-11-11** : les textes de la section « Hors applications » (CGU, page d'accueil et son test, fiches des stores, documents de `docs/release/`, specs, tickets d'initialisation et d'onboarding, `design/screens/lieux/*.md`) ont été alignés sur 3 lieux par la PR `docs/aligner-decisions-publication` (voir `docs/release/alignement-decisions-2026-11-11.md`). Restent : les clients (Android fait dans `main` ; iOS = PR #145), les maquettes HTML et les checklists QA.

Ce fichier liste ce qu'il faut changer **sans le faire** : la PR `backend/lieux-gratuits-3` ne touche ni `android/`, ni `ios/`, ni `web/`, ni `docs/legal/`, ni `CLAUDE.md`. Numéros de ligne relevés sur `origin/main` au 2026-11-10 (ils peuvent bouger).

## Ordre à respecter (important)

1. Fusion de la PR backend (après « oui » du PM).
2. **Déploiement des règles par le PM** (commande exacte dans la PR et dans `docs/dev/deploiement-regles.md`).
3. Seulement ensuite : livraison des applications qui écrivent `p3`.

Tant que les règles ne sont pas déployées, la production n'accepte que `p1` et `p2` : **un client à 3 lieux serait refusé** sur le 3e lieu (erreur de permission, rien d'écrit). L'inverse est sans risque : une application actuelle (2 lieux) continue de fonctionner après le déploiement des règles.

## Contrat client (inchangé sauf la liste d'identifiants)

- Gratuit : `placeId` parmi `p1`, `p2`, `p3` seulement, type `trusted` seulement ; le client prend le premier libre ; si les trois existent, paywall **sans écrire**.
- Un lieu rattaché à plusieurs Miffs = un document par Miff avec **le même identifiant** (inchangé). Le compte « lieux » du client est celui des lieux **distincts** (`distinctPlaces`), pas des documents.
- Budget des lots : **inchangé** (un lieu à la fois, 5 Miffs au plus par lot). Si le client veut créer plusieurs lieux dans un même lot : 3 lieux dans une Miff = 6 lectures de règles sur 20, sans problème ; ne pas mettre la création de la Miff et ses lieux dans le même lot (les règles de lieu lisent la Miff d'avant le lot : refus ; deux lots).
- Un compte qui a déjà plus de 3 lieux (ancien Premium) : pas de blocage de lecture, de modification ni de suppression ; seule la création d'un lieu hors `p1`/`p2`/`p3` exige le Premium.

## Android (`android/`)

| Fichier | Ligne | Valeur actuelle | À changer en |
|---|---|---|---|
| `app/src/main/java/com/tomessa/miff/domain/place/PlaceRules.kt` | 27-28 | `FREE_PLACES = 2` (commentaire « 2 places ») | `3` |
| idem | 29 | `FREE_IDS = listOf("p1", "p2")` | `listOf("p1", "p2", "p3")` |
| idem | 127, 140, 173 | commentaires « `p1` / `p2` », « 2 places », « 2 places already » | 3 |
| `app/src/main/java/com/tomessa/miff/data/place/PlaceRepository.kt` | 70 | commentaire « the free plan has 2 places » | 3 |
| `app/src/main/java/com/tomessa/miff/ui/place/PlaceFormViewModel.kt` | 237 | commentaire « The server says 2 places already » | 3 |
| `app/src/main/java/com/tomessa/miff/ui/place/PlaceListSection.kt` | 63 | commentaire « who already has 2 places » | 3 |
| `app/src/main/res/values/strings.xml` | 667 | `place_paywall_limit_title` : « You've reached 2 places » | « You've reached 3 places » |
| idem | 689-690 | `place_list_counter_free` : « %1$d of 2 places on the free plan » | « of 3 places » |
| `app/src/main/res/values-fr/strings.xml` | 644 | `place_paywall_limit_title` : « Tu as atteint 2 lieux » | « Tu as atteint 3 lieux » |
| idem | 666-668 | `place_list_counter_free` : « %1$d lieu(x) sur 2 avec la formule gratuite » | « sur 3 » |
| `app/src/test/java/com/tomessa/miff/domain/place/PlaceRulesTest.kt` | 169-200 | cas « limite à 2 lieux », `isAtFreeLimit(false, 2)` vrai, `(false, 1)` faux | limite à 3 : `(false, 3)` vrai, `(false, 2)` faux, ajouter le cas `p3` libre |
| `app/src/testDebug/java/com/tomessa/miff/ui/place/PlaceScreensTest.kt` | 413 | « 2 lieux sur 2 avec la formule gratuite » | « 3 lieux sur 3 … » |

À vérifier à la relecture Android : tout test qui crée 2 lieux pour atteindre la limite (`PlaceFormViewModelTest`, `PlaceRepositoryTest`) et les fixtures de lieux gratuits.

## iOS (`ios/`)

| Fichier | Ligne | Valeur actuelle | À changer en |
|---|---|---|---|
| `Miff/Models/Place/PlaceRules.swift` | 21-23 | `freePlaces = 2` ; `freeIds = ["p1", "p2"]` | `3` ; `["p1", "p2", "p3"]` |
| idem | 158, 165, 203 | commentaires « `p1` / `p2` », « 2 lieux », « 2 lieux déjà » | 3 |
| `Miff/Models/Place/PlacePresentation.swift` | 21, 27 | commentaires « 2 lieux sur 2 », « déjà 2 lieux » | 3 |
| `Miff/Services/Contracts/PlaceContracts.swift` | 84 | commentaire « la formule gratuite a 2 lieux » | 3 |
| `Miff/ViewModels/PlaceFormViewModel.swift` | 127 | commentaire « Le serveur dit 2 lieux déjà » | 3 |
| `Miff/Views/Place/PlaceListSection.swift` | 56 | commentaire « 2 lieux sur 2 avec la formule gratuite » | 3 |
| `Miff/Resources/Localizable.xcstrings` | 7711, 7717 | `place_list_counter_free` (EN) : « of 2 places on the free plan » | « of 3 places … » |
| idem | 7729, 7735, 7741 | `place_list_counter_free` (FR) : « sur 2 avec la formule gratuite » | « sur 3 … » |
| idem | 8537, 8543 | `place_paywall_limit_title` : « You've reached 2 places » / « Tu as atteint 2 lieux » | 3 |
| `MiffTests/Place/PlaceRulesTests.swift` | 146-195 | `["p1","p2"]` plein, `isAtFreeLimit(distinctPlaces: 2)` vrai, `1` faux | limite à 3 : `["p1","p2","p3"]` plein, `3` vrai, `2` faux ; cas `.use("p3")` |
| `MiffTests/Place/PlacePresentationTests.swift` | 13-38 | listes de 2 lieux = « à la limite » | listes de 3 |
| `MiffTests/Place/PlaceFormViewModelTests.swift`, `PlaceRepositoryTests.swift` | 118 ; 186-193, 601 | scénarios « 2 lieux = limite » | à rejouer avec 3 |

## Paywall et textes visibles (clients)

- Titre de l'écran de limite : « Tu as atteint **3** lieux » / "You've reached **3** places" (clé `place_paywall_limit_title` sur les deux plateformes). Le corps (« C'est la limite de la formule gratuite. Avec Miff+, ajoute autant de lieux que tu veux. ») ne change pas.
- Compteur de la liste : « N lieu(x) sur **3** avec la formule gratuite » / "N of **3** places on the free plan" (clé `place_list_counter_free`).
- Pastilles du paywall (trois pastilles dont 2 pleines et 1 en pointillé « + » dans la maquette 14) : à redessiner (design) pour 3 pleines + 1 en pointillé, ou à garder comme illustration ; **décision de design à demander au PM** : `design/screens/lieux/paywall-contextuel-lieux.md`, `design/screens/lieux/mockups/14-paywall-limite-lieux.html`, `17-liste-des-lieux.html`, `screens-misc.mjs`, `nouveau-lieu.md` (« au 3e lieu »), `detail-et-liste-lieux.md`, `README.md`.

## Hors applications (textes publiés ou documents : à traiter à part, NON modifiés ici)

| Où | Texte actuel | Remarque |
|---|---|---|
| `CLAUDE.md` ligne 8 | « gratuit = 2 lieux de confiance, 1 seule Miff à la fois ; premium (offre « Miff+ ») = lieux et Miffs illimités… » | à passer à **3** par le PM / l'agent habilité (demande explicite : ne pas modifier dans cette PR) |
| `CLAUDE.md` ligne 94 | « Gratuit = 2 lieux de confiance (décision PM, 2026-11-01) (avant : 1)… » | idem : ajouter « porté à 3 le 2026-11-10 » |
| `docs/legal/cgu.fr.md` lignes 60, 101, 106 ; `docs/legal/cgu.en.md` lignes 60, 101, 106 | « 2 lieux de confiance » / "2 trusted places" | **texte légal publié** : changement sous la responsabilité du PM (pas de relecture juridique, risque assumé) ; vérifier si une nouvelle version des CGU doit être acceptée ; `docs/legal/README.md` ligne 76 |
| `web/src/landing.json` lignes 88, 90, 107, 210, 212, 229 ; `web/tests/landing.test.mjs` | « 2 lieux de confiance » / "2 trusted places" (page d'accueil publiée) | à changer avec le test de la page |
| `marketing/store-listing-android.md` lignes 65, 140 ; `marketing/store-listing-ios.md` lignes 32, 93, 173 (la ligne 32 cite `freePlaces = 2`) | « 2 lieux de confiance » | fiches des stores (à mettre à jour avant soumission) |
| `docs/release/changements-testeurs-externes.md` ligne 10 ; `docs/release/decisions-stores-avant-soumission.md` ligne 239-241 | « 2 lieux » | information testeurs / décisions stores |
| `docs/contracts/analytics-events.md` ligne 32 | « 2 lieux de confiance gratuits depuis le 2026-11-01 » (`extra_trusted_place`) | nom de l'événement inchangé ; mettre à jour la note |
| `docs/qa/checklists/loc-1-android-recette-pm.md` lignes 9, 53-55, 80 ; `docs/qa/checklists/loc-3-ios-recette-pm.md` lignes 7, 50-51, 77 | « 2 lieux sur 2 », « Tu as atteint 2 lieux » | à rejouer avec 3 à la prochaine recette |

## Risque connu élargi (à traiter dans le ticket Premium)

Un ancien Premium créateur de K Miffs peut créer `p1`, `p2` et `p3` dans **chacune** : 3K lieux gratuits (au lieu de 2K). Les règles ne comptent pas les lieux entre Miffs. Jamais d'identifiant libre ni de zone de danger sans abonnement actif (test P24).
