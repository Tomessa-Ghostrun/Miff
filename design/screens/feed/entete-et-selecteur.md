# Feed : en-tête et sélecteur de Miff (maquette 01)

**Pour** : FEED-1 (Android) puis FEED-5 (iOS). **Date** : 2026-11-05 ; **alignée le 2026-11-07** (en-tête qui se masque, D41 ; plus de « fixe »). **Sources** : `docs/tickets/feed.md` §5.1, D4, D12, H1, H2 ; maquettes du PM `Welcoming screen` 0 à 3 ; code Android provisoire `ui/feed/FeedScreen.kt` (pastille déjà en place).

## Structure
**Décision du PM du 2026-11-06 (D41 de `docs/tickets/feed.md`)** : l'en-tête **n'est plus fixe** : il **se masque quand on fait défiler vers le bas et réapparaît dès qu'on remonte** ; il **reste visible** pendant le chargement, en erreur, hors connexion, si la liste est courte, avec TalkBack / VoiceOver actif et quand le sélecteur est ouvert ; contenu : photo de profil, logo, nom de la Miff avec son sélecteur, futur bouton de notifications ; masquage instantané si « réduire les animations ». **Cette décision remplace « en-tête fixe » partout** (PNG non régénérés : le texte prime).

L'en-tête est **posé par-dessus la liste** (la liste garde en haut une marge égale à la hauteur mesurée de l'en-tête : aucune carte n'est cachée) ; **il n'est plus « fixe »** (le PNG 01 montre l'ancien en-tête fixe, à fond `bg/overlay-header` flouté : non régénéré, le texte prime). De haut en bas :
1. Barre d'état du système.
2. Rangée de 56 pt : **avatar** (46 pt, à gauche, marge 16 pt), **logo** Miff centré (100 pt de large). **Plus de cloche** (décision FPR Q15, D4) : le côté droit reste vide.
3. **Pastille de Miff** centrée, 38 pt de haut, 10 pt de marge sous elle.

## Comportement au défilement (D41, décision du PM, 2026-11-06)
- **Descente** : l'en-tête suit le doigt vers le haut et disparaît ; **remontée** : il réapparaît **tout de suite**, sans attendre d'être en haut de liste (il suit le doigt dans les deux sens). Mouvement fluide, sans saut ; le contenu garde une marge égale à la hauteur de l'en-tête (rien n'est masqué, pas de bande blanche en haut de liste : près du haut de la liste, l'en-tête n'est jamais plus masqué que la liste n'a défilé).
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
- Aucun clignotement, quel que soit l'état (l'en-tête lui-même se masque au défilement, D41).
- EN : « Open my profile. Your status: at home ».

## Logo
Toucher : le fil **remonte** tout en haut (animation courte ; instantané si « réduire les animations »). Nom pour le lecteur d'écran : texte existant `home_logo_a11y` ; action « Remonter en haut du fil » / "Scroll to top".

## Pastille de Miff
| Cas | Aspect | Comportement |
|---|---|---|
| Au moins une Miff, **pour tout le monde** (gratuit, Miff+, formule inconnue) [décision du PM, 2026-11-08, remplace « une seule Miff = pastille plate »] | Fond `#eee`, ombre `shadow/default`, flèche bleue | Ouvre la liste (même avec une seule Miff) |
| Aucune Miff (filet) | Fond plat `rgba(0,0,0,.05)`, **sans ombre, sans flèche**, nom en italique 16 pt, texte « Pas encore de Miff » | **Non cliquable**, annoncée comme du texte (pas « bouton ») |
- Nom long : une ligne, tronqué au milieu à 300 pt de large ; nom complet dans l'annonce.
- Lecteur d'écran, sélecteur : « Miff affichée : {nom}. Touche pour changer de Miff ou en ajouter une » (`feed_selector_a11y`, existant).

### Liste ouverte (pour tout le monde, maquette 01 G ; décision du PM du 2026-11-08)
Panneau `#eee` de 250 pt, rayon `radius/md`, ombre. Lignes de **44 pt**, italique 16 pt : les Miffs (coche bleue + gras sur la Miff affichée : le gras seul ne suffit pas), puis un séparateur, puis **« + Rejoindre une Miff »** et **« + Créer une Miff »** en bleu (`feed_selector_join`, `feed_selector_create`, existants). Ces deux entrées sont visibles **pour tout le monde** (le « jamais pour un compte gratuit » de FPR Q2 est remplacé). Au toucher : **compte Miff+** = le parcours normal ; **compte gratuit** (qui a déjà une Miff) = **l'offre** (`PlacePaywallContent`, raison « 2e Miff », sans achat ni prix) ; **formule inconnue / hors connexion** = le parcours normal, le serveur tranche. **iOS** : la liste est une feuille d'actions du système, qui n'affiche pas d'icône : la Miff affichée y est cochée par une coche écrite (« ✓ Famille ») ; l'en-tête reste visible tant qu'elle est ouverte (D41). Fermeture : toucher en dehors, retour système. La Miff choisie est une préférence locale purgée à la déconnexion.

## Textes
| Élément | FR | EN |
|---|---|---|
| Avatar (a11y) | Ouvrir mon profil. Ton statut : {étiquette} | Open my profile. Your status: {label} |
| Logo (a11y) | Remonter en haut du fil | Scroll to top |
| Aucune Miff | Pas encore de Miff [décidé, existant] | No Miff yet |
| Entrées | + Rejoindre une Miff · + Créer une Miff [existants] | + Join a Miff · + Create a Miff |
| Offre d'une 2e Miff, titre (`feed_selector_paywall_title`) | Une seule Miff avec la formule gratuite | One Miff with the free plan |
| Offre d'une 2e Miff, texte (`feed_selector_paywall_body`) | Avec Miff+, tu peux créer ou rejoindre autant de Miffs que tu veux. | With Miff+, create or join as many Miffs as you like. |
| Offre d'une 2e Miff, « bientôt » (`feed_selector_paywall_soon_body`) | Cette option n'est pas encore ouverte. Ta Miff actuelle reste en place. | This option isn't open yet. Your current Miff stays as it is. |

## Écarts avec la maquette du PM
Cloche retirée ; flèche absente seulement quand il n'y a aucune Miff ; **anneau de couleur seule, sans pictogramme** (décision du PM, comme les maquettes) ; coche sur la Miff affichée dans la liste.

## Contraste de l'anneau seul (calculé, WCAG 2.1)
Anneau de 3 pt, sans pictogramme : critère 1.4.11 (éléments graphiques, 3:1) **non atteint pour le vert et l'orange**. Contre le fond d'en-tête (`bg/canvas` `#f9f9f7`) : vert **1,47:1**, orange **1,65:1**, rouge **2,97:1**, neutre **1,64:1** (contre le blanc : 1,55 / 1,74 / 3,13 / 1,73). Contre le disque de la photo ou des initiales `#cfc7bd` : 1,08 / 1,04 / 1,87 / 1,04.
**Limites connues, assumées par la décision** : (1) vert `#d1d828` et orange `#ffb650` ne se distinguent presque pas par la luminosité (**1,12:1**), orange et rouge 1,80:1, vert et rouge 2,02:1 : une personne daltonienne peut confondre ces états sur l'anneau seul ; (2) l'anneau gris neutre se distingue à peine du blanc (1,73:1). **Mitigations déjà en place** : le nom parlé, la bulle de la Miff (texte), la carte avec son étiquette en texte. **Atténuation facultative, sans pictogramme**, laissée à l'implémentation : un liseré fin `#222222` de 1 pt autour de l'anneau (10,28:1 contre le vert, 5,08:1 contre le rouge, 9,18:1 contre le neutre) rend le contour perceptible sans ajouter de signe.

## Plateformes
- **Android** : barre du haut ancrée sous la barre d'état (insets), liste = menu déroulant Material ancré à la pastille ; masquage par défilement imbriqué (l'en-tête suit les déplacements de la liste, une translation lue au dessin seulement : aucune mise en page ne bouge).
- **iOS** : zone sûre ; liste = menu contextuel ou popover ancré à la pastille ; avatar et logo avec `accessibilityLabel` ; masquage au défilement à écrire (le Feed n'existe pas encore sur iOS), mêmes règles « toujours visible » ci-dessus, `accessibilityReduceMotion` et VoiceOver actif = en-tête toujours visible.

## Valeurs de référence lues dans le code livré (Android)
Fichiers `android/app/src/main/java/com/tomessa/miff/ui/feed/FeedHeader.kt` et `CollapsingHeader.kt` :
- Rangée : hauteur minimale **56 dp** ; avatar de **46 dp** dans une zone tactile de **48 dp** à gauche ; logo de **100 dp** de large (moitié de `Spacing.logoWidth`), zone tactile d'au moins 48 dp ; **48 dp réservés** à droite (aucune cloche).
- Cadre : marges latérales 16 dp (`Spacing.m`), marge sous la pastille **10 dp**, fond **`bg/canvas` `#f9f9f7` opaque** (`colorBgCanvas`), ombre `ShadowSubtle`.
- Pastille de Miff : hauteur 40 dp (une seule Miff, fond noir à 5 %, non cliquable) ou 48 dp (Miff+, fond `bg/surface`, ombre `ShadowDefault`, flèche bleue) ; texte `textBodyItalic` (16 / 22) ; liste : lignes de 48 dp.
- « Toujours visible » (verrou) : chargement, erreur, aucune Miff, hors connexion ou copie du cache, service d'accessibilité actif (TalkBack, accès par commutateur), sélecteur de Miffs ouvert ; au verrouillage l'en-tête revient en douceur (animation). Le toucher du logo ou de l'onglet déjà actif remonte la liste et réaffiche l'en-tête.

## Écarts connus (fiche et maquettes contre implémentation)
| # | Écart | Où | Statut |
|---|---|---|---|
| 1 | Fond « `bg/overlay-header` flouté, ombre de 2 pt » dans la maquette 01 ; le code utilise **`bg/canvas` opaque** et `shadow/subtle` (un en-tête qui disparaît n'a pas besoin de flou) | `FeedHeader.kt` | Le code fait foi ; maquette et jeton `bg/overlay-header` non utilisés par le Feed |
| 2 | Le PNG 01 montre un en-tête fixe et des anneaux avec glyphes | `design/exports/feed/claude/01-*.png` | Non régénéré |
| 3 | Les commentaires du code (`FeedHeader.kt` : « The FIXED header » ; `FeedScreen.kt` : « the fixed header ») disent encore « fixe » alors que le comportement livré est celui de D41 | `android/.../ui/feed/` | Commentaires périmés, comportement correct ; à corriger par `android-dev` |
| 4 | Pastille de Miff : 38 pt dans la fiche, 40 dp (une Miff) ou 48 dp (Miff+) dans le code | `FeedHeader.kt` | Le code fait foi |
