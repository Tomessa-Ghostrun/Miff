# Feed : en-tête et sélecteur de Miff (maquette 01)

**Pour** : FEED-1 (Android) puis FEED-5 (iOS). **Date** : 2026-11-05. **Sources** : `docs/tickets/feed.md` §5.1, D4, D12, H1, H2 ; maquettes du PM `Welcoming screen` 0 à 3 ; code Android provisoire `ui/feed/FeedScreen.kt` (pastille déjà en place).

## Structure
En-tête qui **se masque quand le fil défile vers le bas et revient dès qu'on remonte** (« enter always » ; remplace « fixe », décision du PM du 2026-11-06), fond `bg/overlay-header` flouté, ombre de 2 pt, de haut en bas :
1. Barre d'état du système.
2. Rangée de 56 pt : **avatar** (46 pt, à gauche, marge 16 pt), **logo** Miff centré (100 pt de large). **Plus de cloche** (décision FPR Q15, D4) : le côté droit reste vide.
3. **Pastille de Miff** centrée, 38 pt de haut, 10 pt de marge sous elle.

## Comportement au défilement (décision du PM, 2026-11-06)
- **Descente** : l'en-tête suit le doigt vers le haut et disparaît ; **remontée** : il réapparaît **tout de suite**, sans attendre d'être en haut de liste. Mouvement fluide, sans saut ; le contenu garde une marge égale à la hauteur de l'en-tête (rien n'est masqué, pas de bande blanche en haut de liste).
- **Toujours visible** : au chargement, en cas d'erreur, hors connexion, quand un service d'accessibilité (TalkBack, accès par commutateur) est actif, quand la liste est trop courte pour défiler, **tant que le sélecteur de Miff (Premium) est ouvert**. Rotation et changement d'onglet : il revient visible.
- Toucher le logo ou la touche de l'onglet déjà actif remonte la liste **et** réaffiche l'en-tête. Zones tactiles du logo et de l'avatar : 48 dp, inchangées.
- **Place réservée** à droite de la rangée (48 dp) pour le futur bouton de notifications ; rien n'est dessiné.

## Avatar à anneau
| État de MON statut | Anneau (3 pt) | Lecteur d'écran |
|---|---|---|
| Vert | `status/safe` | « Ouvrir mon profil. Ton statut : à la maison » |
| Orange | `status/warning` | « … Ton statut : a quitté la maison » |
| Rouge | `status/danger` | « … Ton statut : dans une zone de danger » |
| Neutre (inconnu, partage arrêté, en attente d'accord) | gris `#c9c4bd` | « … Ton statut : pas encore de nouvelles » |

**Couleur seule, aucun pictogramme sur l'anneau** [décidé par le PM le 2026-11-06 : « la couleur est le seul signal »]. Cela remplace la proposition du 2026-11-05 (coche, horloge, « ! » dans une pastille de 19 pt). L'état est dit par le **nom parlé** (colonne de droite) ; sur la carte du membre, l'état est dit aussi par le **texte** de l'étiquette (voir `cartes-membres.md`).

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
Cloche retirée ; flèche absente quand la pastille n'est pas cliquable ; **anneau de couleur seule, sans pictogramme** (décision du PM, comme les maquettes) ; coche sur la Miff affichée dans la liste.

## Contraste de l'anneau seul (calculé, WCAG 2.1)
Anneau de 3 pt, sans pictogramme : critère 1.4.11 (éléments graphiques, 3:1) **non atteint pour le vert et l'orange**. Contre le fond d'en-tête (`bg/canvas` `#f9f9f7`) : vert **1,47:1**, orange **1,65:1**, rouge **2,97:1**, neutre **1,64:1** (contre le blanc : 1,55 / 1,74 / 3,13 / 1,73). Contre le disque de la photo ou des initiales `#cfc7bd` : 1,08 / 1,04 / 1,87 / 1,04.
**Limites connues, assumées par la décision** : (1) vert `#d1d828` et orange `#ffb650` ne se distinguent presque pas par la luminosité (**1,12:1**), orange et rouge 1,80:1, vert et rouge 2,02:1 : une personne daltonienne peut confondre ces états sur l'anneau seul ; (2) l'anneau gris neutre se distingue à peine du blanc (1,73:1). **Mitigations déjà en place** : le nom parlé, la bulle de la Miff (texte), la carte avec son étiquette en texte. **Atténuation facultative, sans pictogramme**, laissée à l'implémentation : un liseré fin `#222222` de 1 pt autour de l'anneau (10,28:1 contre le vert, 5,08:1 contre le rouge, 9,18:1 contre le neutre) rend le contour perceptible sans ajouter de signe.

## Plateformes
- **Android** : barre du haut ancrée sous la barre d'état (insets), liste = menu déroulant Material ancré à la pastille.
- **iOS** : zone sûre ; liste = menu contextuel ou popover ancré à la pastille ; avatar et logo avec `accessibilityLabel`.
