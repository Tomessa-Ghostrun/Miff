# Recette Android de l'écran « Les couleurs de ta Miff » (COULEURS-1) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13). Un seul compte suffit pour la plupart des étapes ; un second compte (ou l'iPhone) aide à voir une bulle orange ou rouge.
**Ce que fait la fonctionnalité** : toucher la **grande bulle colorée du Feed** ouvre un écran poussé plein écran, **sans barre d'onglets**, qui explique les couleurs d'une personne et celles de la Miff. Écran **statique** : aucune lecture de données, aucun réseau, aucun événement de statistiques.
**Durée** : environ 15 minutes.
**Spec** : `docs/tickets/couleurs-info.md`. Cette recette ne couvre pas l'iPhone (COULEURS-2).

## Avant de commencer

- Installez l'**APK de debug signé par la CI** (artefact `miff-debug-apk` de l'exécution Android de la PR : `gh run download <identifiant> -n miff-debug-apk`).
- **Projet Firebase : `miff-3d283` uniquement.**
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- Aucun indice n'a été ajouté sur la bulle (décision : rien qui ressemble à un bouton). C'est voulu : on teste justement que l'on **trouve** l'écran en touchant la bulle.

## Partie 1 : ouvrir l'écran depuis chaque bulle (6 min)

1. [ ] Feed avec une bulle **verte** (« Tout va bien ») : touchez la **bulle** (sur le texte, puis sur une partie colorée sans texte). L'écran **« Les couleurs de ta Miff »** s'ouvre, **sans la barre d'onglets**, avec la flèche de retour en haut à gauche.
2. [ ] La bulle n'a **aucun** nouvel élément (ni « i », ni flèche, ni lien). Un appui montre un **léger retour visuel** (onde claire) **dans la forme de la bulle**, sans changer sa forme ni sa couleur.
3. [ ] **Retour** (flèche de la barre, puis le geste ou le bouton retour du téléphone à l'ouverture suivante) : le Feed est retrouvé **tel qu'avant** (même défilement, même page de carrousel). Touchez deux fois très vite la bulle : **un seul** écran s'ouvre ; un seul retour suffit.
4. [ ] Les autres bulles que vous pouvez obtenir (au moins deux autres) ouvrent le **même** écran : orange ou rouge (compte B en retard, ou une seule personne) ; grise « Pas encore de nouvelles » ; grise « Personne d'autre dans cette Miff » ; « Tu as arrêté de partager » (coupez le partage dans la Miff) ; voyageurs (mode voyage d'un proche).
5. [ ] **Carrousel** (au moins deux alertes) : touchez la bulle sur la **première** puis sur la **deuxième** page : même écran. Le glissement horizontal, les petits points et le changement de page fonctionnent comme avant.
6. [ ] **Les boutons gardent leur rôle** : « **Contacter** » ouvre la messagerie et **n'ouvre pas** l'écran des couleurs ; « **Reprendre le partage** » ouvre le parcours habituel et **n'ouvre pas** l'écran des couleurs.
7. [ ] **Rien d'autre n'a changé** : l'anneau de votre photo en haut ouvre toujours le Profil ; les cartes des membres ne s'ouvrent pas sur l'écran des couleurs ; la bulle d'encouragement de l'onglet Update non plus ; pendant le chargement, rien à toucher.

## Partie 2 : lire l'écran (5 min)

8. [ ] Le titre **« Les couleurs de ta Miff »**, puis dans l'ordre : une introduction (« … des repères, jamais des alarmes ») ; **La couleur d'une personne** (Vert, Orange, Rouge, Gris, Gris en voyage, puis un court paragraphe **Zone de danger**) ; **Ce qui change la couleur** (5 lignes) ; **La couleur de la Miff** (Vert, Orange, Rouge, Gris, puis « Les personnes en gris ne comptent pas… ») ; **Ce que les couleurs ne disent pas** (4 lignes).
9. [ ] Chaque ligne de couleur a, à gauche, une **pastille ronde** (personne) ou une **petite tache** (Miff) de la bonne couleur, sans symbole ni texte dedans, et le **nom de la couleur en gras**. La couleur n'est jamais seule.
10. [ ] **Les durées** : « partie depuis **2 heures** au plus » (vert), « plus de **2 heures** (jusqu'à **4 heures**) » (orange), « plus de **4 heures** » (rouge), « **1 heure** » (zone de danger), « orange après 2 heures, rouge après 4 heures ». Ce sont les seuils réels de l'app.
11. [ ] Le ton est calme : aucun point d'exclamation, aucun mot qui fait peur, jamais « en sécurité ». L'écran dit bien que ce n'est **pas** une alerte d'urgence et **pas** une position en direct.
12. [ ] L'écran défile jusqu'au bout (la dernière ligne « … le fil indique alors l'heure des dernières informations » est lisible entièrement).

## Partie 3 : cas particuliers (4 min)

13. [ ] **Mode avion** : ouvrez l'écran en mode avion (depuis une bulle déjà affichée) : il s'ouvre et se lit normalement, sans message de connexion.
14. [ ] **Texte agrandi au maximum** (Réglages du téléphone > Affichage > Taille de la police, au plus grand) : rien n'est coupé, tout se lit en défilant, les **pastilles passent au-dessus du texte** au lieu de le rétrécir, la flèche de retour reste facile à toucher. Revenez au Feed : la bulle est toujours lisible.
15. [ ] **Anglais** (langue de l'app ou du téléphone) : « Your Miff's colors », « Green / Orange / Red / Gray », « 2 hours / 4 hours ». Relisez-les à voix haute : le ton vous convient-il ?
16. [ ] **TalkBack** : sur la bulle, TalkBack lit son texte et propose l'action **« Comprendre les couleurs »** (double appui) ; le bouton **« Contacter Thomas »** (ou « Reprendre le partage ») reste un élément **à part**, activable séparément. Sur l'écran des couleurs, le titre et les titres de section sont annoncés comme des **titres** ; chaque ligne de couleur est lue **d'un seul bloc** (« Vert. Tout va bien. La personne est… ») ; les pastilles ne sont pas lues.
17. [ ] **Rotation** de l'écran : tout reste lisible et défile.

## Ce que je n'ai pas pu vérifier sans téléphone

- Le **retour visuel** exact de l'appui sur la bulle (onde dans la forme de la tache) et sa discrétion : à juger à l'œil.
- La **découverte** : sans aucun indice, combien de personnes pensent à toucher la bulle ? C'est le risque R1 du ticket ; notez si vous avez trouvé l'écran sans qu'on vous le dise.

## Limites connues

- Les coins transparents du rectangle de la bulle (quelques millimètres) répondent aussi au toucher : la zone est le rectangle de la bulle, pas sa forme exacte.
- Aucun événement de statistiques n'est envoyé : on ne saura pas combien de personnes ouvrent l'écran tant que vous n'aurez pas décidé d'en ajouter un.
