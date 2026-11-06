# Feed : cartes des membres et fil complet (maquettes 06, 07, 05)

**Pour** : FEED-1 (cercle), FEED-2 (statuts), FEED-3 (Localiser). **Date** : 2026-11-05. **Sources** : `docs/tickets/feed.md` §5.4 à §5.8, §7 (« Carte »), D2, D9, D11, C1 à C7 ; maquettes du PM 0, 4, 5, 8.

## Anatomie d'une carte
Carte `bg/surface`, rayon `radius/sm` (5), ombre `shadow/subtle`, contour `border/default`. **Liseré** de 5 pt à gauche (couleur du niveau). Marges 12 pt (20 pt à gauche). De haut en bas :
1. Avatar 46 pt à anneau + glyphe ; **prénom** (Baloo Thambi 2, 17 / 24) ; pastille « Toi » sur ma carte ; **batterie** à droite (pastille grise 22 pt : icône + « 85 % »).
2. **Étiquette d'état** : pictogramme + texte, sur teinte 30 % avec **texte foncé** (`status/*-text`, AA). Le badge « Localisation limitée » (bleu pâle, bord bleu, pictogramme « position barrée ») passe à la ligne si la place manque.
3. **Ligne de temps** 13 pt `text/description`.
4. **Photo d'update** (108 pt de haut, largeur pleine) avec légende « Photo de 15 h 40 » ; toucher = plein écran (`localiser-et-photo.md`).
5. **Boutons** à droite : Localiser, Message, Appeler (24 pt, zones 44 pt, bleu `action/primary`).

**Ordre des cartes : moi d'abord, puis par ordre d'arrivée dans la Miff, stable ; jamais réordonné selon l'urgence.**

## États (maquette 06)
| | État | Étiquette | Ligne de temps | Boutons |
|---|---|---|---|---|
| A | Moi | selon mon statut + « Toi » | selon mon statut | **aucun** ; ligne « Ce que tes proches voient de toi » **[proposé]** |
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
- **Batterie** (D2) : « 85 % » ; plus d'1 h depuis l'écriture : « 12 % · 16 h 02 » (valeur datée) ; **masquée** si absente ou de plus de 24 h ; **aucune** alerte ni état « batterie faible ».
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
| Moi **[proposé]** | Toi · Ce que tes proches voient de toi | You · What your loved ones see of you |
| Boutons (a11y) | Localiser {Prénom} · Écrire à {Prénom} · Appeler {Prénom} | Locate {Name} · Message {Name} · Call {Name} |
| Localiser en cours (a11y) | Localisation de {Prénom} en cours | Locating {Name}… |

## Accessibilité
- Chaque carte se lit **comme un tout** : « Thomas, à la maison depuis 12 min, batterie 85 % ». Les boutons sont des actions séparées après la carte ; ordre de lecture carte par carte.
- Couleur jamais seule : liseré + anneau + glyphe + texte + pictogramme de l'étiquette.
- Zones tactiles 48 dp / 44 pt ; texte au maximum de la taille système : l'étiquette passe à la ligne, les boutons restent sur une rangée (les pictogrammes ne grandissent pas au-delà de 28 pt).
- La couleur recalculée chaque minute n'est **annoncée** que si le niveau change.

## Fil complet (maquettes 07 et 05)
En-tête fixe → [bandeau d'état éventuel] → bulle (+ pastilles) → cartes → **« Ajouter un proche »** (bouton bleu, ouvre le partage du code, déjà livré) ; à 15 membres : note « Cette Miff est bien remplie ! » (`etats-du-feed.md`). Une seule colonne, défilement vertical, barre d'onglets flottante par-dessus (schématique ici). Marges latérales 16 pt, espace de 14 pt entre blocs.

## Écarts avec les maquettes du PM
Textes sans fautes et sans accord de genre (« Arrivé il ya 12 min », « Parti(e) » supprimés) ; étiquette à texte foncé ; étiquette rouge d'ancienneté « A quitté… » au lieu de « Plus de nouvelles » ; ma carte ajoutée ; glyphe d'anneau ; batterie datée ; photo avec son heure (la même photo sur tous les états n'était qu'un exemple).

## Plateformes
- **Android** : `LazyColumn`, photo chargée à l'apparition de la carte ; boutons = `IconButton` 48 dp ; message / appel = intentions « écrire à » et « composer ».
- **iOS** : `LazyVStack`, mêmes règles ; `sms:` / `tel:` (iOS confirme avant l'appel) ; Dynamic Type.
