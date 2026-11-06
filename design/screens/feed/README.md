# Maquettes « Feed » créées par l'agent design-system (2026-11-05)

Le PM n'a pas les maquettes manquantes listées au §16 de `docs/tickets/feed.md` et a autorisé à les créer **sans Figma**. Le fichier Figma original « Miff - App » n'a jamais été écrit ; « Miff - Claude » non plus. Ce sont des **maquettes HTML + PNG @2x** (390 pt de large), comme les 24 maquettes des Lieux (`design/screens/lieux/mockups/`), dont elles reprennent les composants et les tokens. Les 9 maquettes du PM (`design/exports/feed/Welcoming screen*.png`) restent la référence de style : bulle en forme de tache, carte à liseré, pastille de Miff, barre d'onglets flottante.

**Ce ne sont pas des exports Figma.** Les avatars, les photos d'update et les pictogrammes (position, message, appel, batterie, état) sont des **emplacements ou des approximations** à remplacer par les exports Figma du PM (voir « Assets manquants »).

## Index

| Fiche | Maquettes (`mockups/*.html` et PNG du même nom dans `design/exports/feed/claude/`) |
|---|---|
| `entete-et-selecteur.md` | 01 en-tête sans cloche, anneau de l'avatar (vert, orange, rouge, neutre) · pastille d'une seule Miff · sélecteur Miff+ fermé et ouvert |
| `bulles.md` | 02 vert et neutre · 03 orange, rouge d'ancienneté, zone de danger, carrousel · 04 les mêmes à la 2e personne (moi) · 05 Feed complet avec alertes |
| `cartes-membres.md` | 06 onze états de carte (moi, vert, sans photo, orange, rouge x2, neutres x2, « Localisation limitée », batterie datée, Localiser en cours) · 07 Feed « tout va bien » (corrige les maquettes 0 et 4) |
| `localiser-et-photo.md` | 08 feuille « Localiser » (récente, ancienne, introuvable, refus, hors connexion, sans application de cartes) · 09 photo en plein écran (avec et sans localisation) et photo localisée masquée |
| `etats-du-feed.md` | 10 hors connexion (vert et orange), « Tu as arrêté de partager », « Partage de ton statut en cours… », « En attente de ton accord » · 11 squelette, seul(e), aucune Miff, « Cette Miff est bien remplie ! » |

Chaque fiche donne : texte FR / EN (tutoiement, sans accord de genre quand c'est possible), états, accessibilité, comportement. Les textes marqués **[décidé]** viennent de `docs/tickets/feed.md` §3 et §7 ; ceux marqués **[proposé]** sont des ajouts de design à faire valider (liste ci-dessous).

**Un seul dessin pour iOS et Android** (confirmation du §16, point 11). Les adaptations de plateforme sont dans les fiches (feuille du bas, pager, fermeture de la photo, barre d'état) ; le dessin ne change pas.

## Régénérer
`node design/screens/feed/mockups/build.mjs` écrit les HTML (CSS inclus) ; avec `--png`, rend aussi les PNG dans `design/exports/feed/claude/` (Chrome ou Edge installé, aucune dépendance npm). Les HTML lisent les polices de `ios/Miff/Resources/Fonts/` et le logo de `design/assets/logo/` par chemin relatif : ne pas les déplacer. Sources : `feed.css` (tokens du Feed, s'ajoute à `lieux.css`), `feed-lib.mjs` (composants), `screens-a.mjs` (01 à 05), `screens-b.mjs` (06, 07), `screens-c.mjs` (08 à 11).

## Assets manquants (à exporter depuis Figma par le PM)
- **Photos d'update** : emplacement hachuré « photo d'update (exemple) ». La même photo de salon sur tous les états des maquettes du PM n'était qu'un exemple.
- **Photos de profil** : initiales dans un disque gris. Les initiales sont aussi la vraie valeur de repli de l'app.
- **Pictogrammes** : Localiser (position), Message, Appeler, batterie, glyphes d'état (coche, horloge, « ! », sortie), sablier, pause, cadenas, œil barré, position barrée, flèches. Traits de 1,8 pt dessinés pour la maquette ; remplacer par les icônes Figma du PM.
- **Étoile de la bulle verte** : reprise approximativement de la maquette 0 (jaune-vert) ; à exporter.
- **Barre d'onglets** : schématique (anneau central à la couleur de MON statut), hors périmètre du Feed ; à dessiner avec le ticket Update.

## Décisions de design à valider par le PM
Rien de ce qui suit n'est dans un ticket ou une réponse du PM : ce sont des choix par défaut, faciles à changer.

**Contraste et couleurs (écarts avec les maquettes)**
1. **Texte des étiquettes en teinte foncée dédiée** (le design system signale déjà le problème, `design-system.md` §1) : `status/safe-text` `#4d5200` (7,3:1 sur la teinte 30 % verte), `status/warning-text` `#7a4300` (6,8:1), `status/danger-text` `#a30500` (5,7:1). Les maquettes du PM mettaient du texte coloré plein (`#d1d828`, `#ffb650`, `#ff5650`) sur teinte : contraste insuffisant. **Trois nouveaux tokens à ajouter au design system.**
2. **Texte secondaire des cartes en `rgba(0,0,0,.7)`** au lieu du gris `#7d7c7c` des maquettes (4,2:1 sur blanc, sous le seuil AA de 4,5:1 pour du 13 pt).
3. **Glyphe sur l'anneau de l'avatar** (coche verte, horloge orange, « ! » rouge, rien pour le neutre) : la couleur n'est jamais seule, y compris sur l'avatar de l'en-tête et des cartes. Nouveau détail par rapport aux maquettes.
4. **Gris neutre `#c9c4bd` pour l'anneau, le liseré et la bulle « sans statut »** : jamais de vert quand on ne sait pas.

**Ton (CLAUDE.md : bienveillant, non anxiogène)**
5. **Aucune animation d'alarme, aucun son, aucun point d'exclamation** sur les bulles orange et rouge ; heure **absolue** (« à 16 h 40 ») plutôt que « il y a 62 min » qui s'aggrave ; action douce « Envoyer un message » (SMS du système), jamais un appel imposé.
6. **Seconde ligne PROPOSÉE sous « zone de danger »** : proches, « Une zone définie par ta Miff » (rappelle que c'est un lieu connu choisi par le cercle, pas un incident) ; moi, « Tes proches le voient aussi » (qui voit quoi). Les phrases de titre sont celles du ticket (§7, Q3 : « Tu es dans une zone de danger de ta Miff »). **« Zone de danger » reste un mot fort** (risque R1 du ticket) : si le PM le juge trop dur pour la personne concernée, alternative à relire : « Tu es dans un lieu défini comme zone de danger par ta Miff ».
7. **« Tu es seul(e) dans cette Miff pour l'instant »** est le texte décidé (D7) mais contient un accord de genre. Alternative sans accord : « Personne d'autre dans cette Miff pour l'instant » / "No one else in this Miff yet". À trancher.

**Confiance et localisation (qui voit quoi)**
8. **Ligne sous ma carte : « Ce que tes proches voient de toi »** (œil + texte 12 pt), pour rendre visible que ma carte est celle que mes proches voient. Ajoute 16 pt de hauteur à ma carte ; supprimable sans autre effet.
9. **Ligne de réciprocité dans la feuille « Localiser »** : « Vous partagez tous les deux : {Prénom} peut aussi voir ta dernière position. » Redit la règle de réciprocité déjà décidée (lecture réciproque, D1).
10. **« Position figée au moment de la photo. »** sous le titre du plein écran, pour que « Voir où c'était » ne soit pas pris pour du direct.
11. **Pas de carte intégrée** dans la feuille « Localiser » (décision du ticket) : texte + bouton « Ouvrir dans les cartes ». Les coordonnées seules partent vers l'application de cartes.

**Composants et structure**
12. **Pastille de Miff non cliquable (une seule Miff)** : fond plat, sans ombre ni flèche (la maquette du PM montrait toujours la flèche). **Côté droit de l'en-tête vide** depuis la suppression de la cloche, logo resté centré : alternative possible, logo à gauche de l'avatar décalé ; non dessinée.
13. **Boutons Localiser / Message / Appeler en pictogrammes seuls** (comme la maquette du PM), zones de 44 pt, noms pour le lecteur d'écran. Alternative : une rangée avec libellés visibles (« Localiser », « Message », « Appeler ») : plus lisible pour tous, 24 pt de hauteur en plus par carte.
14. **Pagination des alertes** : la page courante est une pastille **allongée** (forme) et teintée, pas seulement colorée ; **aucun défilement automatique** ; boutons « Alerte précédente / suivante » pour le lecteur d'écran.
15. **Bulle : un pictogramme d'état par couleur** (étoile jaune-vert reprise du PM pour « Tout va bien », horloge orange, « ! » rouge, sablier neutre) dans un disque clair en bas à droite.
16. **Hors connexion** : bandeau sous l'en-tête (« Dernières informations de 16 h 02 ») ; sur une alerte, mention supplémentaire dans la bulle. Pas de teinte d'erreur : gris, icône nuage barré.
17. **« Tu as arrêté de partager »** : bulle neutre + bouton **« Reprendre le partage »** (non écrit au ticket : il ouvre le dialogue existant « Reprendre le partage ? ») ; cartes des proches avec le libellé **« Nouvelles masquées »** [proposé : le ticket dit seulement « cartes neutres »].
18. **« En attente de ton accord »** : encart conservé de la maquette 23 des Lieux ; la bulle neutre reprend sa phrase d'explication au lieu d'un second bloc de texte.
19. **« Cette Miff est bien remplie ! »** : note verte non interactive qui remplace le bouton, sans phrase d'explication.
20. **Photo localisée masquée faute de position publiée** : tuile bleu pâle + cadenas + « Partage ta position pour voir cette photo. » (texte du ticket) ; une photo expirée ou refusée n'affiche rien.

**Points du ticket non résolus par le dessin**
- Q1 (localisation de la photo) : le plein écran est dessiné **avec et sans** « Voir où c'était ».
- Q3 (texte de zone de danger pour la personne concernée) : dessiné avec la recommandation du ticket.
- La **barre d'onglets** n'est pas redessinée : seule la couleur du bouton central est montrée.
