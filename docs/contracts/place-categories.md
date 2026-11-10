# Contrat — catégories de lieux (emoji) et carrousel de Miffs du Profil

**Statut** : contrat partagé entre `android-dev` et `ios-dev` (décisions du PM du 2026-11-10). Toute modification passe par une PR qui touche ce fichier, et change les deux plateformes ensemble.
**Code** : Android `PlaceCategory.emoji` (`android/.../domain/place/PlaceModels.kt`, testé par `PlaceCategoryEmojiTest`) ; iOS : `PlaceCategory.emoji` (même table, même test à écrire).

## 1. Emoji des sept catégories

Les icônes dessinées sont remplacées par des **emoji Unicode rendus comme du texte** (décision du PM : « des emoji type iPhone pour catégoriser les lieux »). La table est la même sur iOS et Android :

| Valeur technique (`category` du document, **inchangée**) | Libellé FR | Emoji | Points de code |
|---|---|---|---|
| `home` | Maison | 🏠 | U+1F3E0 |
| `work` | Travail | 💼 | U+1F4BC |
| `school` | École | 🏫 | U+1F3EB |
| `sport` | Sport | ⚽ | U+26BD |
| `relative` | Chez un proche | 👨‍👩‍👧 | U+1F468 U+200D U+1F469 U+200D U+1F467 |
| `leisure` | Loisir | 🎉 | U+1F389 |
| `other` | Autre | 📍 | U+1F4CD |

Choix et raisons : ⚽ pour le sport, comme dans la maquette du PM (« Foot de Thomas ») et plus lisible à 28 pt que 🏋️ ; 👨‍👩‍👧 pour « Chez un proche », l'emoji que le PM avait déjà choisi, plutôt que 🏡 qui se confond avec 🏠 ; 🎉 pour « Loisir », plus large que 🎮 (qui dit seulement « jeux vidéo »).

Règles communes :
- **Affichage seulement.** Rien de l'emoji n'est écrit sur le serveur : le document garde la valeur technique de la colonne 1.
- **Décoratif pour le lecteur d'écran** (VoiceOver, TalkBack) : le **nom** de la catégorie est lu, jamais l'emoji ; le nom reste écrit ou décrit à côté de l'emoji.
- **Un lieu sans catégorie** (écrit avant les catégories) garde le repère neutre de l'app (une épingle dessinée), jamais « Autre » par défaut.
- **Polices** : les emoji d'Apple sont propriétaires et **ne peuvent pas être embarqués dans l'app Android**. Android dessine ces emoji avec la **police du système** (Noto Color Emoji sur un Pixel, le jeu du fabricant sur certains téléphones) : le rendu est proche, pas identique à iOS. Les sept emoji existent tous depuis Android 8 (minSdk 26). Si une police ne sait pas dessiner l'un d'eux, Android affiche la première lettre du nom de la catégorie à sa place (jamais un carré vide).
- **Taille** : l'emoji reste dans sa pastille (28 pt dans le formulaire, 44 pt dans les listes) quelle que soit la taille de texte choisie ; les mots autour grandissent.

## 2. Carrousel « Mes Miffs » du Profil

Mêmes règles sur iOS et Android (ticket `docs/tickets/feed-profil-reglages.md`, maquettes `design/exports/profil/`) :

- **Une carte par Miff**, puis une **dernière carte « Créer une Miff »** (toujours présente, même sans Miff). Pastilles de pagination sous le carrousel (décoratives).
- **Carte de Miff** : nom, nombre de membres (« 4 membres », « 1 membre »), **miniatures des membres** (photo, sinon initiales), toucher = écran de la Miff.
- **Miniatures** : jusqu'à **5 membres**, tous affichés, sans « +N » ; **au-delà de 5**, **4 miniatures puis « +N »** dans le 5e rond (N = membres - 4, donc au moins 2). Ordre : le créateur d'abord, puis par ordre d'arrivée (celui de l'écran de la Miff).
- **Carte « Créer une Miff »** : même règle que l'entrée « Créer une Miff » du sélecteur du Feed (décision du 2026-11-08) : un compte **gratuit qui a déjà une Miff** ouvre le **paywall** (raison « Miff ») ; Miff+, ou aucune Miff, ouvre la création ; un **plan inconnu** n'est jamais pris pour « gratuit ».
- **Coût Firestore** : les membres de la Miff active sont **réutilisés** depuis le Feed (0 lecture) ; les autres Miffs sont lues **une fois**, quand leur carte s'arrête à l'écran, puis réutilisées 5 minutes ; jamais d'écouteur permanent. Détail et estimation : `docs/dev/surveillance-quotas.md`.
- **Photos d'un autre compte** : les règles bornent leur taille en octets (20 000), pas leurs dimensions : n'en décoder que de plausibles (côté ≤ 2048 px), réduites à la taille du rond ; sinon les initiales.
- **Clés de texte** (parité `check-i18n-parity`) : `profile_tab_miff_members` (pluriel : « %d membre(s) »), `profile_tab_miff_create`, `profile_tab_carousel_page_a11y` (« Carte %d sur %d »), `profile_tab_carousel_previous_a11y`, `profile_tab_carousel_next_a11y` ; le toucher sur une carte réutilise `profile_tab_miff_open_a11y`, le titre `profile_tab_miffs_title`. `profile_tab_miff_hint` (« Proches, code et partage ») : **iOS la garde** sous le nom de la carte tant que les membres ne sont pas lus et si leur lecture échoue (la carte garde ainsi sa hauteur, sans message d'erreur) ; Android n'affiche rien à cet endroit.
- **Accessibilité** : chaque carte est un seul bouton qui dit son nom, son nombre de membres et sa position (« Carte 2 sur 3 ») ; « Carte précédente / suivante » en actions du lecteur d'écran (sans glisser) ; les miniatures et les pastilles sont décoratives.
