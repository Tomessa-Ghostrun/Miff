# Contrat — lieux de la Miff du carrousel, visibles par tous les membres (onglet Profil)

**Statut** : contrat partagé entre `android-dev` et `ios-dev` (décision du PM du **2026-11-11**, sans maquette ni spec Notion). Android est livré (PR #181, fusionnée) ; **iOS : branche `ios/profil-lieux-membres` (miroir, PR brouillon)**. Toute modification passe par une PR qui touche ce fichier et change les deux plateformes ensemble.
**Code iOS** : `ProfilePlacesSection` (`Views/Place/`), `ProfilePlacesPresentation` et `PlaceRowSpeech` (`Models/Place/ProfilePlacesPresentation.swift`), `ProfilePlacesViewModel` (`ViewModels/`), `MiffPlacesBook` et `PlaceViewerRole` (`Models/Place/MiffPlacesBook.swift`, testés), `FirestorePlacesReader` (`Services/Firebase/`).
**Code Android** : `ProfilePlacesSection` (`ui/place/PlaceHosts.kt`), `ProfilePlacesViewModel` (`ui/profile/`), `MiffPlacesBook` et `PlaceViewerRole` (`domain/place/MiffPlacesBook.kt`, testés), `FirestorePlacesReader` (`data/miff/PlacesReader.kt`).

## 1. Règle produit

Sur l'onglet **Profil**, sous le carrousel « Mes Miffs », la liste des lieux de confiance est celle de la **Miff dont la carte est affichée** (la carte sur laquelle le carrousel s'est arrêté ; à l'ouverture, la Miff active du Feed). Elle est **visible par TOUS les membres de cette Miff**, pas seulement par son créateur.

| | Créateur de la Miff | Autre membre |
|---|---|---|
| Voit la liste (nom, emoji de catégorie, adresse, « Zone de danger ») | oui | **oui** |
| Ouvre le détail d'un lieu (carte, adresse, rayon, type, catégorie, « Visible par », « Créé par ») | oui, avec « Modifier » | **oui, en lecture seule : ni « Modifier », ni suppression, ni action d'édition** |
| « Ajouter un lieu » | oui (inchangé : pastille « Miff+ » à la limite des 3 lieux gratuits) | **jamais** |
| Compteur « n lieux sur 3 avec la formule gratuite » | oui, si le compte est gratuit | **jamais** |
| Offre / pastille « Miff+ » pour ce bloc | oui, à la limite (inchangé) | **jamais** (le forfait et les quotas ne concernent que le créateur) |
| Confirmation « Le lieu « X » est enregistré… » | oui | non |
| Phrase sous le titre | (aucune, ou le compteur) | `place_list_member_hint` |

**Qui est le créateur** : le champ `ownerUid` du document de la Miff (déjà lu pour construire la liste des Miffs : 0 lecture de plus), comparé à l'uid connecté. **Créateur inconnu = traité comme un membre** (les boutons du créateur ne s'affichent jamais dans le doute). L'affichage suit le rôle ; **ce qui est permis** reste décidé par les règles Firestore (`allow create, update, delete: if isOwner(miffId)`), qui n'ont pas changé.

## 2. États de la section

- **Chargement** (liste pas encore lue) : **rien n'est dessiné** (ni titre, ni phrase « aucun lieu » : pas d'absence inventée).
- **Liste** : titre `place_profile_title_named` (le nom de la Miff est **toujours** dit, même avec une seule Miff), puis la phrase `place_list_member_hint` (membre) ou le compteur (créateur gratuit), puis les lignes, puis (créateur seulement) « Ajouter un lieu ».
- **Aucun lieu** : membre : `place_list_empty_member` ; créateur : `miff_places_empty` (inchangé) + « Ajouter un lieu ».
- **Illisible** (hors ligne sans copie, refus, lenteur) : titre + `place_profile_unavailable` (annoncée poliment au lecteur d'écran) + bouton `auth_retry_action` qui relit tout de suite. Aucun mot d'erreur alarmiste.
- **Carte « Créer une Miff » affichée** : la liste reste celle de la **dernière carte de Miff** affichée (le titre nomme la Miff, pas d'ambiguïté).

## 3. Coût (lectures Firestore) — mêmes règles que les visages du carrousel

- **Un seul `get`** de `miffs/{miffId}/places`, **jamais d'écouteur** (l'écoute en direct de la liste du Profil est supprimée).
- Miff du Feed : **0 lecture** (les lieux que le Feed a déjà lus ; une copie du cache non confirmée par le serveur n'est pas reprise).
- Autre Miff : lue **une fois** quand sa carte s'arrête, puis **réutilisée 5 minutes** ; échec : attente de 30 s avant un nouvel essai, « Réessayer » relit tout de suite.
- Le **créateur** qui ouvre un lieu ou « Ajouter un lieu » : toutes les listes sont relues à leur prochaine apparition. Un membre qui ouvre un lieu : aucune relecture.
- Détail d'un lieu : garde son écoute en direct de la Miff (inchangé).

## 4. Affichage d'un lieu (inchangé, rappelé)

- Emoji de la catégorie (`docs/contracts/place-categories.md`, `CategoryGlyph`) ; un ancien lieu sans catégorie montre l'épingle.
- Type zone de danger : étiquette **« Zone de danger »** (`place_type_danger`), sans autre mot ; une zone de confiance n'affiche aucune étiquette.
- **Lecteur d'écran** : une ligne = un bloc « nom, catégorie X, adresse » ; pour une zone de danger, **« , Zone de danger » est ajouté** (clé `place_list_row_type_a11y`, `%1$s, %2$s` : la description de la ligne, puis le libellé du type).
- Aucune coordonnée en texte nulle part (la spec `docs/tickets/lieux-de-confiance.md` §6.8 donne à tout membre nom, adresse, position sur la carte, rayon, type et catégorie : rien n'est masqué aux non-créateurs, aucune coordonnée brute n'est affichée).

## 5. Clés de traduction (Android `values-fr` / `values`, iOS `Localizable.xcstrings`)

| Clé | Français | Anglais | Où |
|---|---|---|---|
| `place_profile_title_named` | Lieux de confiance de %1$s | Trusted places of %1$s | titre de la section (tous) |
| `place_list_empty_member` | Aucun lieu de confiance pour l'instant. | No trusted place yet. | liste vide, membre |
| `place_profile_unavailable` | Les lieux ne sont pas disponibles pour le moment. | The places aren't available right now. | liste illisible |
| `place_list_row_type_a11y` | %1$s, %2$s | %1$s, %2$s | description parlée d'une ligne de zone de danger |

Clés réutilisées : `place_list_member_hint`, `miff_places_empty`, `auth_retry_action`, `place_type_danger`, `place_list_add_action`. Les clés `place_list_title` et `place_list_title_named` ne servent plus sur le Profil Android (laissées dans les catalogues). **Les 4 clés ci-dessus sont dans le catalogue iOS** (écran iOS livré par la branche `ios/profil-lieux-membres`).

## 6. Ordre des blocs de l'onglet Profil (décision du PM du 2026-11-11, inchangée)

En-tête (photo, nom, roue) → carrousel « Mes Miffs » → **lieux de la Miff du carrousel** → carte « Mode voyage » **en dernier** (test Android `ProfileTravelCardOrderTest`).

## 7. Points à reproduire sur iOS

1. Suivre la carte du carrousel (pas la Miff du Feed) ; ne rien dessiner tant que la liste n'est pas lue.
2. Créateur = `ownerUid` du document de la Miff déjà lu pour la liste des Miffs ; inconnu = membre.
3. Un membre : liste + détail **sans** « Modifier », jamais « Ajouter un lieu », jamais de compteur ni de « Miff+ ».
4. Un seul `get` par Miff, réutilisation 5 minutes, 0 lecture pour la Miff du Feed, relecture après passage du créateur par un lieu ou le formulaire.
5. « Zone de danger » dite aussi à VoiceOver (`accessibilityLabel` de la ligne).

## 8. Formulaire « Ajouter un lieu » (revue de la PR, 2026-11-11)

Quand le créateur touche « Ajouter un lieu » sous la liste d'une Miff (Profil, ou écran de la Miff), le formulaire de **nouveau lieu coche en premier cette Miff** (si la personne en est la créatrice), au lieu de la dernière Miff rejointe : un lieu ne doit pas être partagé avec le mauvais cercle par surprise. Une Miff dont la personne n'est pas créatrice, ou aucune Miff transmise : choix habituel (Q4). Android : `PlaceFormRoute(miffId = …)` et `DefaultMiffChoice.of(owned, lastJoinedId, openedFromId)`.
