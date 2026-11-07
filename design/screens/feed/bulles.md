# Feed : la bulle de la Miff (maquettes 02, 03, 04, 05)

**Pour** : FEED-2 (Android) puis FEED-6 (iOS). **Date** : 2026-11-05. **Sources** : `docs/tickets/feed.md` §5.2, §5.3, §7 (« Bulle »), D6 à D8, B1 à B6 ; maquettes du PM 0, 5, 6, 7, 8.

## Principe
Une **bulle** = la couleur de la Miff entière, en forme de tache (celle des maquettes du PM), sous l'en-tête. **Une seule à la fois.** Elle **informe, elle n'alarme pas** : heures absolues, **aucune animation d'alarme** (**exception décidée par le PM le 2026-11-06, D40 de `docs/tickets/feed.md` : la bulle de la Miff a un mouvement continu calme** ; amplitudes révisées par le PM le 2026-11-07, D45 : forme qui s'étire et s'écrase avec les deux axes déphasés (largeur de 100 % à 92 %, hauteur de 100 % à 90 %), inclinaison ±0,8°, dérive ±3,5 dp, cycle de 6 s, le texte et le bouton ne sont pas déformés ; jamais de clignotement ni de rebond, immobile avec « Animations supprimées », arrêtée hors écran), aucun son, aucun point d'exclamation, jamais « en sécurité ». Un pictogramme d'état est toujours présent (la couleur n'est jamais seule).

## Anatomie
Tache `status/{niveau}-tint` (30 %), minimum 176 pt de haut (206 pt avec un bouton), marges intérieures 34 / 40 / 56 / 36 pt. Titre `display/headline` (Baloo Thambi 2, 24 / 32), noir ; ligne 2 `body/regular-14` noir ; ligne 3 `body/caption` 13 pt `text/description`. Pictogramme dans un disque clair en bas à droite (étoile jaune-vert pour « Tout va bien », horloge orange, « ! » rouge, sablier gris). Bouton « Envoyer un message » : contour bleu, fond blanc, 44 pt de haut, aligné à gauche.

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
| Action | Envoyer un message | Send a message |
| Moi, orange | Tu as quitté {lieu} à 16 h 40 | You left {place} at 4:40 PM |
| Moi, rouge d'ancienneté | Tes proches n'ont pas eu de tes nouvelles depuis 16 h 02 | Your loved ones haven't heard from you since 4:02 PM |
| Moi, zone de danger (Q3, recommandation) | Tu es dans une zone de danger de ta Miff / Depuis 16 h 02 | You're in a danger zone of your Miff / Since 4:02 PM |
| Seconde ligne, moi **[proposé]** | Tes proches le voient aussi | Your loved ones can see it too |
| Pagination (a11y) | Alerte {n} sur {m} · Alerte précédente · Alerte suivante | Alert {n} of {m} · Previous alert · Next alert |

« Zone à risque » et « Plus de nouvelles de… » des maquettes 7 et 8 sont **remplacés** et n'existent nulle part.

## États
- **Un seul repère de temps** par bulle (« Dernière nouvelle » **ou** l'heure de l'alerte), jamais les deux. « Dernière nouvelle » = le plus récent des repères des **autres** membres au statut connu (jamais le mien).
- **Carrousel** (03 D, E, 05) : une page par membre concerné ; rouges d'abord, puis oranges ; à niveau égal, le repère le plus ancien d'abord. **Pastilles seulement s'il y a plusieurs pages** : 8 pt, la page courante est une pastille **allongée** de 22 pt, teinte foncée du niveau. Balayage horizontal ; **aucun défilement automatique** ; une alerte qui disparaît retire sa page, les pastilles s'adaptent sans saut.
- **Bouton « Envoyer un message »** : sur une alerte concernant un **autre** membre, jamais sur ma page ; ouvre le SMS du système vers le numéro de la fiche ; **absent** si le membre n'a pas de numéro.
- **Hors connexion, partage arrêté, publication en cours, en attente d'accord** : voir `etats-du-feed.md` (maquette 10).
- **Chargement** : squelette (maquette 11 A).

## Accessibilité
- La bulle est une **région** annoncée **seulement quand le niveau change** (jamais à chaque minute). Annonce = le titre + la ligne de temps.
- Carrousel : chaque page atteignable sans balayage (boutons « Alerte précédente / suivante » exposés au lecteur d'écran), « Alerte 1 sur 2 » annoncé au changement de page.
- Contraste : texte noir sur teinte 30 % (> 15:1) ; pictogramme en teinte foncée (`status/warning-text`, `status/danger-text`).
- « Réduire les animations » : **le mouvement continu de la bulle s'arrête (bulle immobile, D40 / D45)** ; le défilement vers une page est instantané.
- Taille de texte maximale : la tache grandit en hauteur ; le titre passe à la ligne, jamais tronqué.
- Droite-à-gauche : le pictogramme passe à gauche, la tache est symétrique.

## Écarts avec les maquettes du PM
« Tout va bien » ; « Dernière nouvelle » à la place de « Mis à jour il y a 2 min » ; heures absolues ; rouge d'ancienneté retexté ; « zone de danger » ; bouton « Envoyer un message » ; pastille allongée ; pas de défilement automatique ; bulle neutre grise (nouvelle).

## Plateformes
- **Android** : `HorizontalPager` ; boutons « précédent / suivant » = actions TalkBack personnalisées.
- **iOS** : `TabView` à pages ; actions VoiceOver (`accessibilityAdjustableAction`).
- Le dessin est identique.
