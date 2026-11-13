# Recette iPhone de l'écran « Les couleurs de ta Miff » (COULEURS-2) : parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5.2), avec le build TestFlight de la branche `ios/couleurs-info`. Un compte suffit pour la plupart des étapes ; un second compte (ou l'OPPO) aide à voir une bulle orange ou rouge.
**Ce que fait la fonctionnalité** : un petit bouton **« i »**, discret, **en bas à droite de la grande bulle colorée du Feed**, ouvre un écran poussé plein écran, **sans barre d'onglets**, qui explique les couleurs d'une personne et celles de la Miff. **Toucher la bulle elle-même ne fait rien** (décision du PM du 2026-11-13, elle remplace « toucher la bulle » du ticket). Écran **statique** : aucune lecture de données, aucun réseau, aucun événement de statistiques.
**Durée** : environ 20 minutes.
**Spec** : `docs/tickets/couleurs-info.md` (l'entrée par le bouton « i » prime sur « toucher la bulle »). Android : `docs/qa/checklists/couleurs-info-android-recette-pm.md`.

## Avant de commencer

- Installez le build TestFlight de la PR (workflow `ios-testflight`, lancé à la main). **Projet Firebase : `miff-3d283` uniquement.**
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- Les captures de la CI (`-MiffPreview feed-green`, `feed-gray`, `feed-stopped`, `feed-carousel`, `colors-info`) montrent déjà le bouton et l'écran ; cette recette vérifie le toucher réel.

## Partie 1 : le bouton « i » sur chaque bulle (8 min)

1. [ ] Feed avec une bulle **verte** (« Tout va bien ») : un petit « i » dans un cercle, **sans fond ni contour**, apparaît **en bas à droite** de la bulle. Il est lisible (couleur foncée sur la teinte) mais **discret** : il ne tire pas l'œil.
2. [ ] Touchez le « i » : l'écran **« Les couleurs de ta Miff »** s'ouvre, **sans la barre d'onglets**, flèche de retour en haut à gauche.
3. [ ] **Retour** (flèche, puis geste de retour par le bord gauche à l'ouverture suivante) : le Feed est retrouvé **tel qu'avant** (même défilement, même page de carrousel). Touchez deux fois très vite le « i » : **un seul** écran s'ouvre ; un seul retour suffit.
4. [ ] **Toucher la bulle elle-même** (sur le texte, sur une partie colorée sans texte) **ne fait rien**.
5. [ ] Le bouton est présent, bien placé et lisible sur chaque bulle que vous pouvez obtenir (au moins quatre) : orange ou rouge ; grise « Pas encore de nouvelles » ; grise « Personne d'autre dans cette Miff » ; « Tu as arrêté de partager » ; voyageurs (mode voyage d'un proche) ; accord de partage en attente.
6. [ ] Le « i » est **toujours dans la bulle** (jamais à moitié sorti de la tache) et **ne touche jamais le texte**.
7. [ ] **Carrousel** (au moins deux alertes) : le « i » est **au même endroit** sur chaque page pendant le balayage, et **prend la couleur** de la page (orange ou rouge). Il ouvre le même écran depuis chaque page. Le balayage, les pastilles (sous la bulle) et le changement de page fonctionnent comme avant.
8. [ ] **Les boutons gardent leur rôle** : « **Contacter** » ouvre la messagerie et **n'ouvre pas** l'écran des couleurs ; « **Reprendre le partage** » ouvre le parcours habituel et **n'ouvre pas** l'écran. Les boutons et le « i » ne se recouvrent pas ; chacun répond à son propre toucher (essayez des deux pouces, près du « i »).
9. [ ] **Zone de toucher** : le « i » se touche sans viser (comme un bouton normal, pas besoin de précision au millimètre).
10. [ ] **Rien d'autre n'a changé** : l'anneau de votre photo en haut ouvre toujours le Profil ; les cartes des membres ne s'ouvrent pas sur l'écran des couleurs ; pendant le chargement, rien à toucher.

## Partie 2 : lire l'écran (5 min)

11. [ ] Le titre **« Les couleurs de ta Miff »**, puis dans l'ordre : une introduction (« … des repères, jamais des alarmes ») ; **La couleur d'une personne** (Vert, Orange, Rouge, Gris, Gris en voyage, puis un court paragraphe **Zone de danger**) ; **Ce qui change la couleur** (5 lignes) ; **La couleur de la Miff** (Vert, Orange, Rouge, Gris, puis « Les personnes en gris ne comptent pas… ») ; **Ce que les couleurs ne disent pas** (4 lignes).
12. [ ] Chaque ligne de couleur a, à gauche, une **pastille ronde** (personne) ou une **petite tache** (Miff) de la bonne couleur, sans symbole ni texte dedans, et le **nom de la couleur en gras**. La couleur n'est jamais seule.
13. [ ] **Les durées** : « partie depuis **2 heures** au plus » (vert), « plus de **2 heures** (jusqu'à **4 heures**) » (orange), « plus de **4 heures** » (rouge), « **1 heure** » (zone de danger), « orange après 2 heures, rouge après 4 heures ». Ce sont les seuils réels de l'app.
14. [ ] Le ton est calme : aucun point d'exclamation, aucun mot qui fait peur, jamais « en sécurité ». L'écran dit bien que ce n'est **pas** une alerte d'urgence et **pas** une position en direct.
15. [ ] L'écran défile jusqu'au bout (la dernière ligne « … le fil indique alors l'heure des dernières informations » est lisible entièrement).

## Partie 3 : cas particuliers (7 min)

16. [ ] **Mode avion** : ouvrez l'écran en mode avion (depuis une bulle déjà affichée) : il s'ouvre et se lit normalement, sans message de connexion.
17. [ ] **Texte agrandi au maximum** (Réglages > Accessibilité > Affichage et luminosité > Taille du texte > Tailles d'accessibilité, au plus grand) : sur le **Feed**, la bulle grandit, le texte n'est **pas masqué par le « i »**, le « i » n'est pas masqué par le texte ni par « Contacter » ; sur l'**écran des couleurs**, rien n'est coupé, tout se lit en défilant, les **pastilles passent au-dessus du texte** au lieu de le rétrécir, la flèche de retour reste facile à toucher.
18. [ ] **Anglais** (langue de l'app ou de l'iPhone) : « Your Miff's colors », « Green / Orange / Red / Gray », « 2 hours / 4 hours ». Relisez-les à voix haute : le ton vous convient-il ?
19. [ ] **VoiceOver** : sur le Feed, VoiceOver lit le texte de la bulle, puis propose un bouton **« Comprendre les couleurs »** (double toucher) ; « Contacter Thomas » (ou « Reprendre le partage ») reste un élément **à part**. Sur l'écran des couleurs, le titre et les titres de section sont annoncés comme des **titres** ; chaque ligne de couleur est lue **d'un seul bloc** (« Vert. Tout va bien. La personne est… ») ; les pastilles ne sont pas lues.
20. [ ] **Rotation** : l'app est en portrait seulement sur iPhone ; si elle tourne chez vous, tout reste lisible et défile.

## Ce que je n'ai pas pu vérifier sans iPhone

- Le **rendu réel** du « i » (taille, discrétion, position dans le coin arrondi de la tache) sur chaque couleur : à juger à l'œil (captures de la CI jointes à la PR, mais le toucher réel n'est vérifié que par vous).
- Le projet n'a pas de test d'interface : la présence du bouton dans chaque variante est vérifiée sur le code et par les captures, pas par un toucher automatisé.

## Limites connues

- Le « i » est volontairement discret : tant qu'on ne sait pas qu'il existe, on ne trouve pas l'écran. Notez si vous l'avez trouvé sans qu'on vous le dise.
- Aucun événement de statistiques n'est envoyé : on ne saura pas combien de personnes ouvrent l'écran tant que vous n'aurez pas décidé d'en ajouter un.
- Les marges haut et bas de la bulle passent de 44 à 52 pt (pour loger la zone de toucher du « i ») : une bulle courte garde sa hauteur, une bulle haute (texte agrandi) gagne 16 pt.
