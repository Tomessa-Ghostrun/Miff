# Feed : cartes des membres et fil complet (maquettes 06, 07, 05)

**Pour** : FEED-1 (cercle), FEED-2 (statuts), FEED-3 (Localiser). **Date** : 2026-11-05 ; **alignée le 2026-11-07** sur les décisions du PM validées en recette (D39 à D44 de `docs/tickets/feed.md`). **Sources** : `docs/tickets/feed.md` §5.4 à §5.8, §7 (« Carte »), D2, D9, D11, D39, D42, D43, D44, C1 à C7 ; maquettes du PM 0, 4, 5, 8.

> **Les PNG 06 et 07 ne sont pas régénérés** : ils montrent encore la pastille « Toi », ma carte en premier et la batterie datée. **Le texte de cette fiche prime sur les PNG** (voir « Écarts connus » en bas de fiche).

## Anatomie d'une carte
Carte `bg/surface`, rayon `radius/sm` (5), ombre `shadow/subtle`, contour `border/default`. **Liseré** de 5 pt à gauche (couleur du niveau). Marges 12 pt (20 pt à gauche). De haut en bas :
1. Avatar 46 pt à anneau **de couleur seule, sans pictogramme** (décision du PM, 2026-11-06) ; **prénom** (Baloo Thambi 2, 17 / 24) ; **aucune pastille « Toi » sur ma carte** (**D42, décision du PM, 2026-11-07** : rien n'est ajouté à l'écran, c'est la ligne visible « Ce que tes proches voient de toi » qui dit que la carte est la mienne) ; **batterie** à droite (pastille grise : icône + « 85 % », **sans heure ni âge de la mesure**, **D44**).
2. **Étiquette d'état** : **texte seul, sans pictogramme** [décidé par le PM le 2026-11-06], sur teinte 30 % avec **texte foncé** (`status/*-text`, AA). Le badge « Localisation limitée » (bleu pâle, bord bleu, pictogramme « position barrée ») n'est pas une étiquette d'état : il garde son pictogramme et passe à la ligne si la place manque.
3. **Ligne de temps** 13 pt `text/description`.
4. **Photo d'update** : **aperçu carré, aussi large que la carte, centré sur le milieu de la photo, recadré sans déformer** (**décision du PM du 2026-11-06, D39**, remplace « 108 pt de haut, largeur pleine » ; PNG non régénérés) ; la photo reste **complète** en plein écran au toucher (le recadrage n'est qu'un affichage) ; même règle sur ma carte ; légende « Photo de 15 h 40 » (posée en bas à gauche de l'aperçu, fond `bg/surface`) ; toucher = plein écran (`localiser-et-photo.md`).
5. **Boutons** à droite : Localiser, Message, Appeler (24 pt, zones 44 pt, bleu `action/primary`).

**Ordre des cartes (D43, décision du PM, 2026-11-07) : les autres membres d'abord (le créateur, puis l'ordre d'arrivée `joinedAt`), MA carte en DERNIÈRE position ; stable ; jamais réordonné selon l'urgence.** Remplace « moi d'abord ». Conséquence : ma carte se trouve juste au-dessus de « Ajouter un proche ».

## États (maquette 06)
| | État | Étiquette | Ligne de temps | Boutons |
|---|---|---|---|---|
| A | Moi (**dernière carte**) | selon mon statut, **sans pastille « Toi »** | selon mon statut | **aucun** ; ligne « Ce que tes proches voient de toi » (œil + texte 12 pt) |
| B | Vert, avec photo | À la maison | Depuis 12 min | les trois |
| C | Vert, sans photo, sans numéro | À l'école | Depuis 6 h | Localiser seul |
| D | Vert après un départ (< 1 h) | A quitté le travail | Départ il y a 12 min | les trois |
| E | Orange (1 h à 2 h) | A quitté le travail | Dernière nouvelle à 16 h 02 | les trois |
| F | Rouge d'ancienneté (> 2 h) | A quitté la maison | Pas de nouvelles depuis 16 h 02 | les trois |
| G | Rouge, zone de danger | Dans une zone de danger | Depuis 16 h 02 | les trois |
| H | Neutre, pas de statut | Pas encore de nouvelles | aucune | Message, Appeler (pas de Localiser) |
| I | Neutre, partage arrêté | Partage arrêté | aucune | Message, Appeler (pas de Localiser) |
| J | Vert + « Localisation limitée » | À la maison | Depuis 12 min | les trois |
| K | Localiser en cours | — | — | l'icône de Localiser devient un indicateur |

Autres règles :
- **Batterie** (D2, **modifiée par D44**) : la pastille affiche **« 85 % » seul** (icône + pourcentage), **jamais l'heure ni l'âge de la mesure** (plus de « 12 % · 16 h 02 », ni dans la pastille, ni dans la phrase du lecteur d'écran : « Batterie à 85 % ») ; **masquée si elle est absente ou si la mesure a plus de 6 h** (**D44 modifié par le PM le 2026-11-07** : la valeur « 24 h » du ticket et du code de la PR #105 est **dépassée**, voir « Écarts connus ») ; **aucune** alerte ni état « batterie faible ». Le modèle de données ne change pas : `updatedAt` continue d'être écrit et ne sert plus qu'à masquer la valeur.
- **Étiquette** : « À {catégorie} » ou « A quitté {catégorie} » ; le nom du lieu remplace la catégorie sauf Maison, Travail, École, Sport ; lieu supprimé ou introuvable : « Dans un lieu de confiance » / « A quitté un lieu de confiance ».
- **Photo** : absente = pas de bloc (pas de trou). Une photo expirée ou refusée : rien, aucun message.
- **Boutons absents** : Message et Appeler sans numéro de téléphone (compte ancien) ; les trois sur ma carte ; Localiser sans partage actif ou avant LOC-2b.
- Les neutres ne comptent pas dans la couleur de la bulle ni dans « {n} sur {m} » (sauf comme « sans nouvelles »).

## Textes (FR / EN)
| Élément | FR | EN |
|---|---|---|
| À (catégorie) | À la maison · Au travail · À l'école · Au sport | At home · At work · At school · At sport |
| À / A quitté (nom) | À {nom} · A quitté {nom} | At {name} · Left {name} |
| Zone de danger | Dans une zone de danger | In a danger zone |
| Neutres | Pas encore de nouvelles · Partage arrêté | No news yet · Sharing stopped |
| Localisation limitée | Localisation limitée | Limited location |
| Temps | Depuis 12 min · Départ il y a 12 min · Dernière nouvelle à 16 h 02 · Pas de nouvelles depuis 16 h 02 · Depuis 16 h 02 | For 12 min · Left 12 min ago · Latest news at 4:02 PM · No news since 4:02 PM · Since 4:02 PM |
| Photo | Photo de 15 h 40 | Photo from 3:40 PM |
| Moi | Ce que tes proches voient de toi (**plus de « Toi »**, D42) | What your loved ones see of you |
| Boutons (a11y) | Localiser {Prénom} · Écrire à {Prénom} · Appeler {Prénom} | Locate {Name} · Message {Name} · Call {Name} |
| Localiser en cours (a11y) | Localisation de {Prénom} en cours | Locating {Name}… |

## Accessibilité
- Chaque carte se lit **comme un tout** : « Thomas, à la maison depuis 12 min, batterie 85 % » (**sans heure de mesure**, D44). Sur ma carte, la ligne « Ce que tes proches voient de toi » est lue **dans le même bloc que le prénom** : c'est elle qui dit que c'est ma carte (D42). Les boutons sont des actions séparées après la carte ; ordre de lecture carte par carte (ma carte en dernier, D43).
- **Sur une carte, le texte de l'étiquette dit l'état** (« À la maison », « A quitté le travail », « Dans une zone de danger ») : la couleur du liseré, de l'anneau et de l'étiquette ne porte jamais l'état seule. **Contrastes calculés** : texte foncé sur teinte 30 % : vert 7,28:1, orange 6,76:1, rouge 5,73:1, neutre 9,22:1 (AA atteint). **Anneau et liseré seuls** (sans le texte) : mêmes limites que l'en-tête (vert 1,55:1, orange 1,74:1, rouge 3,13:1 contre le blanc ; vert et orange 1,12:1 entre eux, confondables par une personne daltonienne) : voir « Contraste de l'anneau seul » dans `entete-et-selecteur.md`, liseré fin facultatif `#222222` de 1 pt autour de l'anneau.
- Zones tactiles 48 dp / 44 pt ; texte au maximum de la taille système : l'étiquette passe à la ligne, les boutons restent sur une rangée (les pictogrammes ne grandissent pas au-delà de 28 pt).
- La couleur recalculée chaque minute n'est **annoncée** que si le niveau change.

## Fil complet (maquettes 07 et 05)
En-tête qui se masque au défilement vers le bas et réapparaît en remontant (D41, `entete-et-selecteur.md`) → [bandeau d'état éventuel] → bulle (+ pastilles) → cartes des autres membres → **MA carte (dernière)** → **« Ajouter un proche »** (bouton bleu, ouvre le partage du code, déjà livré) ; à 15 membres : note « Cette Miff est bien remplie ! » (`etats-du-feed.md`). Une seule colonne, défilement vertical, barre d'onglets flottante par-dessus (schématique ici). Marges latérales 16 pt, espace de 14 pt entre blocs.

## Écarts avec les maquettes du PM
Textes sans fautes et sans accord de genre (« Arrivé il ya 12 min », « Parti(e) » supprimés) ; étiquette à texte foncé ; étiquette rouge d'ancienneté « A quitté… » au lieu de « Plus de nouvelles » ; ma carte ajoutée **en dernière position, sans pastille « Toi »** (D42, D43) ; anneau de couleur seule et étiquette en texte seul, sans pictogramme (décision du PM) ; batterie **sans heure de mesure** et masquée au-delà de 6 h (D44) ; photo avec son heure (la même photo sur tous les états n'était qu'un exemple).

## Plateformes
- **Android** : `LazyColumn`, photo chargée à l'apparition de la carte ; boutons = `IconButton` 48 dp ; message / appel = intentions « écrire à » et « composer ».
- **iOS** : `LazyVStack`, mêmes règles ; `sms:` / `tel:` (iOS confirme avant l'appel) ; Dynamic Type. **Le Feed n'existe pas encore sur iOS** (aucune vue dans `ios/Miff/Views/` sur `main`) : les valeurs ci-dessous sont celles d'Android.

## Valeurs de référence lues dans le code livré (Android)
Fichier `android/app/src/main/java/com/tomessa/miff/ui/feed/FeedCards.kt` (version de la PR #105 pour D42 à D44, sinon `main`) ; jetons dans `android/app/src/main/java/com/tomessa/miff/ui/theme/`. Ce sont **les valeurs du code**, pas des propositions :
- Carte : fond `colorBgSurface`, forme `ShapeSm` (5 dp), ombre `ShadowSubtle`, contour `StrokeHairline` (1 dp) `colorBorderDefault` ; **liseré de 5 dp** à gauche (couleur du niveau, `StatusLevel.ring()` de `FeedStyle.kt`) ; marges internes 12 dp (haut, droite, bas) et 12 dp à gauche après le liseré ; espace de 8 dp (`Spacing.s`) entre les blocs de la carte.
- Avatar : 46 dp (`FeedAvatar`, `FeedStyle.kt`), anneau `StrokeAvatarRing` (3 dp), disque `colorStatusNeutral` (`#c9c4bd`), initiales en `textBodyBold14`.
- Prénom : `textDisplayLabel` (Baloo Thambi 2, **16 / 22**), noir. Espace avatar - prénom : 12 dp.
- Pastille de batterie : fond `colorBorderDefault`, forme `ShapeSm`, marges 8 dp / 2 dp, icône `ic_feed_battery` de 14 dp, texte `textCaptionBold12` en `colorTextDescription`, texte « 85 % » seul.
- Étiquette d'état : `textBodyRegular14`, couleur `onTint()` sur la teinte 30 %, forme `ShapeSm`, marges 8 dp / 4 dp.
- Ligne de temps : `textBodyRegular14`, `colorTextDescription`. Ligne « Ce que tes proches voient de toi » : icône `ic_visibility` de 16 dp + `textBodyCaption`.
- Aperçu de photo (`FeedPhoto.kt`) : `aspectRatio(1f)`, rogné au centre (`ContentScale.Crop`, `Alignment.Center`), forme `ShapeSm`, légende en bas à gauche (`textBodyCaption`, fond `colorBgSurface`).
- Boutons : trois `IconButton` de 48 dp (`Spacing.minTouchTarget`), icônes `colorActionPrimary` ; aucun sur ma carte. Espace entre cartes de la liste : 14 dp ; marges latérales de la liste : 16 dp (`FeedScreen.kt`).

## Écarts connus (fiche et maquettes contre implémentation)
| # | Écart | Où | Statut |
|---|---|---|---|
| 1 | Les PNG 06 et 07 montrent la pastille « Toi », ma carte en premier et la batterie datée | `design/exports/feed/claude/` | PNG non régénérés ; le texte de cette fiche prime |
| 2 | **Seuil de masquage de la batterie : 6 h (décision du PM du 2026-11-07) contre 24 h** dans `docs/tickets/feed.md` (D44, C4) et dans le code de la PR #105 (`FeedBuilder`, constante `BATTERY_HIDDEN_AFTER_MILLIS = DAY_MILLIS`) | ticket, code Android | **Incohérence non tranchée ici** : ticket et code à aligner sur 6 h (`product-specs`, puis `android-dev` et `ios-dev`) |
| 3 | Prénom en 17 / 24 dans l'anatomie ci-dessus, **16 / 22** (`display/label`) dans le code | `FeedCards.kt` | Le code suit le jeton du design system ; lire « 16 / 22 » |
| 4 | Marges internes : « 12 pt, 20 pt à gauche » dans la fiche ; dans le code, 12 dp après un liseré de 5 dp (soit 17 dp depuis le bord) | `FeedCards.kt` | Écart de 3 dp, imperceptible, non corrigé |
| 5 | Pastille de batterie « grise 22 pt » dans la fiche ; dans le code, hauteur dictée par le texte et des marges de 2 dp | `FeedCards.kt` | Non corrigé |
| 6 | Aperçu carré (D39) : livré sur `main` (`FeedPhoto.kt`) ; la PR #105 est basée sur un état plus ancien de `main` et ne contient pas ce fichier | branche `android/feed-ajustements` | Se résout à la fusion de `main` dans la PR #105 |
