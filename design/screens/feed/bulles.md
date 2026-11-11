# Feed : la bulle de la Miff (maquettes 02, 03, 04, 05)

**Pour** : FEED-2 (Android) puis FEED-6 (iOS). **Date** : 2026-11-05 ; **alignée le 2026-11-07** (mouvement de la bulle, D40). **Sources** : `docs/tickets/feed.md` §5.2, §5.3, §7 (« Bulle »), D6 à D8, B1 à B6 ; maquettes du PM 0, 5, 6, 7, 8.

## Principe
Une **bulle** = la couleur de la Miff entière, en forme de tache (celle des maquettes du PM), sous l'en-tête. **Une seule à la fois.** Elle **informe, elle n'alarme pas** : heures absolues, **aucune animation d'alarme** (**exception décidée par le PM, D40 de `docs/tickets/feed.md` : la bulle de la Miff a un mouvement continu lent et calme, jamais un effet d'alerte** ; description complète dans « Mouvement de la bulle » ci-dessous), aucun son, aucun point d'exclamation, jamais « en sécurité ». Un pictogramme d'état est toujours présent (la couleur n'est jamais seule).

## Anatomie
Tache `status/{niveau}-tint` (30 %), minimum 176 pt de haut (206 pt avec un bouton), marges intérieures 34 / 40 / 56 / 36 pt. Titre `display/headline` (Baloo Thambi 2, 24 / 32), noir ; ligne 2 `body/regular-14` noir ; ligne 3 `body/caption` 13 pt `text/description`. Pictogramme dans un disque clair en bas à droite (étoile jaune-vert pour « Tout va bien », horloge orange, « ! » rouge, sablier gris). Bouton « Envoyer un message » : contour bleu, fond blanc, 44 pt de haut, aligné à gauche.

## Mouvement de la bulle (D40, puis précision du PM après sa recette)
Cette section remplace « aucune animation » pour la **bulle de la Miff en haut du Feed** (les autres éléments restent sans animation, D15).

**Ce que le PM a décidé**
- D40 (2026-11-06, `docs/tickets/feed.md`) : un mouvement continu **très léger**, plafonds du ticket **≤ 1,5 % d'échelle** et **≤ 3 dp de dérive**, cycle de **5 à 8 s**, **jamais** de clignotement ni de rebond.
- **Précision demandée ensuite par le PM** (consignée dans le ticket comme **D45**, 2026-11-07) : un **cycle lent** ; la tache **s'étire et s'écrase**, les deux axes (largeur et hauteur) **en déphasage** l'un de l'autre, avec un **léger déplacement vertical** ; une amplitude **visible mais calme**. Cette précision **peut dépasser les plafonds de D40** (« visible ») : **le ticket et les plafonds sont à réaligner** sur la valeur finale (voir « Écarts connus »).
- **Valeurs de référence (D45, `docs/tickets/feed.md`, PM 2026-11-07) : `android/app/src/main/java/com/tomessa/miff/ui/feed/BubbleMotion.kt`.** La tache s'**étire et s'écrase** avec les deux axes **déphasés** (largeur de 100 % à 92 %, hauteur de 100 % à 90 %, la forme ne dépasse jamais sa taille de départ), s'incline de ±0,8° et dérive de ±3,5 dp ; cycle de 6 s pour la tache, 7 s pour son pictogramme (qui respire de ±5 % et ajoute 0,5 dp de dérive) ; sinusoïdes pures. **Seule la forme et son fond se déforment ; le texte et le bouton ne sont pas déformés** (ils suivent seulement la dérive). Cette décision **remplace les plafonds de D40** (≤ 1,5 % et ≤ 3 dp).

**Ce qui ne change jamais**
- **Immobile** avec « Animations supprimées » (Android) / « Réduire les animations » (iOS) ; **arrêté** quand la bulle n'est pas à l'écran (défilement, autre onglet, écran de l'Update) et en arrière-plan.
- **Même mouvement quel que soit le niveau** (vert, orange, rouge, neutre) : aucun effet d'alarme, aucune accélération sur une alerte ; sur chaque page d'un carrousel d'alertes, le même mouvement.
- **Dessiné, pas mis en page** : le mouvement ne déplace ni les marges, ni les cartes, ni la barre d'onglets (couche graphique lue au dessin seulement).
- **Aucun effet sur le lecteur d'écran** : rien n'est annoncé, l'annonce de la bulle est un nœud à part.

## Choix de la bulle (une seule, par priorité)
| Situation | Bulle | Maquette |
|---|---|---|
| Au moins un rouge ou un orange | Carrousel d'alertes | 03, 05 |
| Pas d'alerte, au moins un AUTRE membre au statut connu, tous verts | « Tout va bien » | 02 A, B |
| Aucun autre membre au statut connu | « Pas encore de nouvelles » (neutre) | 02 C |
| Personne d'autre dans la Miff | « Tu es seul(e)… » (neutre) | 02 D |
| Je suis le seul rouge ou orange | Page à la 2e personne | 04 |

## Textes [décidés sauf mention]
| Cas | FR | EN |
|---|---|---|
| Vert | Tout va bien | All good |
| Repère | Dernière nouvelle il y a 2 min | Latest news 2 min ago |
| Neutres présents | 3 sur 4 ont donné des nouvelles | 3 of 4 have shared news |
| Aucun statut | Pas encore de nouvelles / Dès que tes proches donneront des nouvelles, tu les verras ici. | No news yet / As soon as your loved ones share news, you'll see it here. |
| Seul | Tu es seul(e) dans cette Miff pour l'instant (alternative sans accord : « Personne d'autre dans cette Miff pour l'instant ») | You're the only one in this Miff for now (alt.: "No one else in this Miff yet") |
| Orange | {Prénom} a quitté {lieu} à 16 h 40 | {Name} left {place} at 4:40 PM |
| Rouge, ancienneté | Pas de nouvelles de {Prénom} depuis 16 h 02 | No news from {Name} since 4:02 PM |
| Rouge, zone de danger | {Prénom} est dans une zone de danger / Depuis 16 h 02 | {Name} is in a danger zone / Since 4:02 PM |
| Seconde ligne, zone de danger (proches) **[proposé]** | Une zone définie par ta Miff | A zone set by your Miff |
| Action | Contacter | Contact |
| Moi, orange | Tu as quitté {lieu} à 16 h 40 | You left {place} at 4:40 PM |
| Moi, rouge d'ancienneté | Tes proches n'ont pas eu de tes nouvelles depuis 16 h 02 | Your loved ones haven't heard from you since 4:02 PM |
| Moi, zone de danger (Q3, recommandation) | Tu es dans une zone de danger de ta Miff / Depuis 16 h 02 | You're in a danger zone of your Miff / Since 4:02 PM |
| Seconde ligne, moi **[proposé]** | Tes proches le voient aussi | Your loved ones can see it too |
| Pagination (a11y) | Alerte {n} sur {m} · Alerte précédente · Alerte suivante | Alert {n} of {m} · Previous alert · Next alert |

« Zone à risque » et « Plus de nouvelles de… » des maquettes 7 et 8 sont **remplacés** et n'existent nulle part.

## États
- **Un seul repère de temps** par bulle (« Dernière nouvelle » **ou** l'heure de l'alerte), jamais les deux. « Dernière nouvelle » = le plus récent des repères des **autres** membres au statut connu (jamais le mien).
- **Carrousel** (03 D, E, 05) : une page par membre concerné ; rouges d'abord, puis oranges ; à niveau égal, le repère le plus ancien d'abord. **Pastilles seulement s'il y a plusieurs pages** : 8 pt, la page courante est une pastille **allongée** de 22 pt, teinte foncée du niveau. Balayage horizontal ; **aucun défilement automatique** ; une alerte qui disparaît retire sa page, les pastilles s'adaptent sans saut.
- **Bouton « Contacter »** (EN « Contact » ; décision du PM du 2026-11-11, clé `feed_bubble_message_action`, lu « Contacter {prénom} » par le lecteur d'écran, clé `feed_bubble_contact_a11y`) : sur une alerte concernant un **autre** membre, jamais sur ma page ; ouvre le SMS du système vers le numéro de la fiche ; **absent** si le membre n'a pas de numéro.
- **Hors connexion, partage arrêté, publication en cours, en attente d'accord** : voir `etats-du-feed.md` (maquette 10).
- **Chargement** : squelette (maquette 11 A).

## Accessibilité
- La bulle est une **région** annoncée **seulement quand le niveau change** (jamais à chaque minute). Annonce = le titre + la ligne de temps.
- Carrousel : chaque page atteignable sans balayage (boutons « Alerte précédente / suivante » exposés au lecteur d'écran), « Alerte 1 sur 2 » annoncé au changement de page.
- Contraste : texte noir sur teinte 30 % (> 15:1) ; pictogramme en teinte foncée (`status/warning-text`, `status/danger-text`).
- « Réduire les animations » : **le mouvement continu de la bulle s'arrête (bulle immobile, D40)** ; le défilement vers une page est instantané.
- Le mouvement n'est jamais annoncé ; l'annonce (région polie) ne se déclenche que si le **niveau** change.
- Taille de texte maximale : la tache grandit en hauteur ; le titre passe à la ligne, jamais tronqué.
- Droite-à-gauche : le pictogramme passe à gauche, la tache est symétrique.

## Écarts avec les maquettes du PM
« Tout va bien » ; « Dernière nouvelle » à la place de « Mis à jour il y a 2 min » ; heures absolues ; rouge d'ancienneté retexté ; « zone de danger » ; bouton « Envoyer un message » ; pastille allongée ; pas de défilement automatique ; bulle neutre grise (nouvelle).

## Plateformes
- **Android** : `HorizontalPager` ; boutons « précédent / suivant » = actions TalkBack personnalisées.
- **iOS** : `TabView` à pages ; actions VoiceOver (`accessibilityAdjustableAction`).
- Le dessin est identique. **Le Feed n'existe pas encore sur iOS** : le mouvement de la bulle y est à écrire d'après les valeurs d'Android (`BubbleMotion.kt`), avec « Réduire les animations » (`accessibilityReduceMotion`).

## Valeurs de référence lues dans le code livré (Android)
Fichier `android/app/src/main/java/com/tomessa/miff/ui/feed/FeedBubble.kt` ; jetons dans `android/app/src/main/java/com/tomessa/miff/ui/theme/` :
- Tache : fond = teinte 30 % du niveau (`StatusLevel.tint()`), forme libre à dix points (`BubbleShape`), **hauteur minimale 176 dp**, marges intérieures **40 dp (début) / 56 dp (fin) / 40 dp (haut) / 46 dp (bas)**, espace de 8 dp entre les lignes.
- Titre : `textDisplayHeadline` (Baloo Thambi 2, **24 / 30**), noir, marqué titre pour le lecteur d'écran ; ligne 2 : `textBodyRegular14`, noir ; ligne 3 : `textBodyCaption`, `colorTextDescription`.
- Pictogramme : disque de **40 dp** blanc à 70 % en bas à droite (marges 40 dp), icône de 24 dp ; teinte foncée du niveau (`onTint()`), grise pour le sablier.
- Bouton « Contacter » / « Reprendre le partage » (décision du PM du 2026-11-11 : plus petit, **sans icône**) : dessin de 36 dp de haut au minimum, forme `ShapeMd` (10 dp), fond `colorBgSurface`, contour `StrokeAccent` (2 dp) `colorBorderAction`, marges latérales 12 dp, texte `textBodyBold14` (14 / 20, gras) en `colorActionPrimary` ; **zone de toucher de 48 dp de haut et de large au moins** (marge invisible autour du dessin).
- Pastilles du carrousel : 8 x 8 dp, la page courante **22 x 8 dp** en teinte foncée du niveau, les autres en `colorStatusNeutral` ; espace de 8 dp entre la tache et les pastilles.
- Annonce : région polie dans un nœud de 1 dp à part, clé = niveau (ou ensemble d'alertes).

## Écarts connus (fiche et maquettes contre implémentation)
| # | Écart | Où | Statut |
|---|---|---|---|
| 1 | Marges de la tache **34 / 40 / 56 / 36 pt** et titre **24 / 32** dans l'« Anatomie » ; le code a **40 / 56 / 40 / 46 dp** (début / fin / haut / bas) et un titre **24 / 30** (jeton `display/headline`) | `FeedBubble.kt` | Le code fait foi ; non corrigé |
| 2 | Pictogramme « disque clair » sans taille dans la fiche ; code : 40 dp, blanc à 70 % | `FeedBubble.kt` | Documenté ci-dessus |
| 3 | **Mouvement : la précision du PM (étirement / écrasement déphasés entre les axes, déplacement vertical, amplitude « visible ») n'est pas dans le code de `main`** (échelle uniforme ±1,2 %, dérive ±2 dp, cycles 6,5 s et 7,5 s) et **n'est pas dans la PR #105** (qui ne touche pas à `BubbleMotion.kt`) ; les plafonds D40 (≤ 1,5 %, ≤ 3 dp) peuvent ne plus être valables | `BubbleMotion.kt`, `docs/tickets/feed.md` D40 | **Non tranché ici** : valeurs finales à fixer par le PM ou le dev, puis ticket et critère B7 à aligner |
| 4 | Les PNG 02 à 05 ne montrent aucun mouvement (images fixes) | `design/exports/feed/claude/` | Normal ; le mouvement se juge sur appareil |

## Mise à jour du 2026-11-11 (PM) : aucun symbole dans la bulle

**Aucun pictogramme dans la bulle, quelle que soit la couleur** : plus de symbole en filigrane (étoile, horloge, « ! », sablier), plus d'icône dans le bouton, et **pas non plus d'avion de voyage** (mode voyage : la couleur et les mots suffisent). Les passages de ce document qui parlent d'un pictogramme (contraste, droite-à-gauche) ne s'appliquent plus à la bulle. La phrase principale est en `display/bubble` (`design/design-system.md` §2.1).
