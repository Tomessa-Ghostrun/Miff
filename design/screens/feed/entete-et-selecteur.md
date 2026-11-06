# Feed : en-tête et sélecteur de Miff (maquette 01)

**Pour** : FEED-1 (Android) puis FEED-5 (iOS). **Date** : 2026-11-05. **Sources** : `docs/tickets/feed.md` §5.1, D4, D12, H1, H2 ; maquettes du PM `Welcoming screen` 0 à 3 ; code Android provisoire `ui/feed/FeedScreen.kt` (pastille déjà en place).

## Structure
En-tête **fixe** (reste en haut quand le fil défile), fond `bg/overlay-header` flouté, ombre de 2 pt, de haut en bas :
1. Barre d'état du système.
2. Rangée de 56 pt : **avatar** (46 pt, à gauche, marge 16 pt), **logo** Miff centré (100 pt de large). **Plus de cloche** (décision FPR Q15, D4) : le côté droit reste vide.
3. **Pastille de Miff** centrée, 38 pt de haut, 10 pt de marge sous elle.

## Avatar à anneau
| État de MON statut | Anneau (3 pt) | Glyphe (pastille 19 pt, bas droite) | Lecteur d'écran |
|---|---|---|---|
| Vert | `status/safe` | coche | « Ouvrir mon profil. Ton statut : à la maison » |
| Orange | `status/warning` | horloge | « … Ton statut : a quitté la maison » |
| Rouge | `status/danger` | « ! » dans un cercle | « … Ton statut : dans une zone de danger » |
| Neutre (inconnu, partage arrêté, en attente d'accord) | gris `#c9c4bd` | aucun | « … Ton statut : pas encore de nouvelles » |

- Contenu : photo de profil ou initiales (disque `#cfc7bd`). L'annonce reprend **l'étiquette de ma carte** (une seule source de texte).
- Toucher : ouvre l'onglet **Profil**. Zone tactile 48 dp / 44 pt (le disque fait 46 pt, la zone s'étend autour).
- Aucune animation, aucun clignotement, quel que soit l'état.
- EN : « Open my profile. Your status: at home ».

## Logo
Toucher : le fil **remonte** tout en haut (animation courte ; instantané si « réduire les animations »). Nom pour le lecteur d'écran : texte existant `home_logo_a11y` ; action « Remonter en haut du fil » / "Scroll to top".

## Pastille de Miff
| Cas | Aspect | Comportement |
|---|---|---|
| Une seule Miff (compte gratuit) | Fond plat `rgba(0,0,0,.05)`, **sans ombre, sans flèche**, nom en italique 16 pt | **Non cliquable**, annoncée comme du texte (pas « bouton ») |
| Plusieurs Miffs (Miff+) | Fond `#eee`, ombre `shadow/default`, flèche bleue | Ouvre la liste |
| Aucune Miff (filet) | Comme « une seule », texte « Pas encore de Miff » | Non cliquable |
- Nom long : une ligne, tronqué au milieu à 300 pt de large ; nom complet dans l'annonce.
- Lecteur d'écran, sélecteur : « Miff affichée : {nom}. Touche pour changer de Miff ou en ajouter une » (`feed_selector_a11y`, existant).

### Liste ouverte (Miff+ seulement, maquette 01 G)
Panneau `#eee` de 250 pt, rayon `radius/md`, ombre. Lignes de **44 pt**, italique 16 pt : les Miffs (coche bleue + gras sur la Miff affichée : le gras seul ne suffit pas), puis un séparateur, puis **« + Rejoindre une Miff »** et **« + Créer une Miff »** en bleu (`feed_selector_join`, `feed_selector_create`, existants). Un compte **gratuit** n'a **jamais** ces deux entrées (FPR Q2). Fermeture : toucher en dehors, retour système. La Miff choisie est une préférence locale purgée à la déconnexion.

## Textes
| Élément | FR | EN |
|---|---|---|
| Avatar (a11y) | Ouvrir mon profil. Ton statut : {étiquette} | Open my profile. Your status: {label} |
| Logo (a11y) | Remonter en haut du fil | Scroll to top |
| Aucune Miff | Pas encore de Miff [décidé, existant] | No Miff yet |
| Entrées | + Rejoindre une Miff · + Créer une Miff [existants] | + Join a Miff · + Create a Miff |

## Écarts avec la maquette du PM
Cloche retirée ; flèche absente quand la pastille n'est pas cliquable ; glyphe sur l'anneau ; coche sur la Miff affichée dans la liste.

## Plateformes
- **Android** : barre du haut ancrée sous la barre d'état (insets), liste = menu déroulant Material ancré à la pastille.
- **iOS** : zone sûre ; liste = menu contextuel ou popover ancré à la pastille ; avatar et logo avec `accessibilityLabel`.
