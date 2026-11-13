# Recette iOS : carrousel de Miffs au Profil, emoji des catégories de lieux, 3 lieux gratuits — parcours pour le PM

**Pour qui** : vous, sur l'iPhone 11 Pro (iOS 26.5.2), avec un compte **gratuit** qui a une Miff (**G**), un compte avec **Miff+** accordé à la main et au moins **2 Miffs** (**P**, document `entitlements/{uid}` actif posé depuis la console), si possible dont une avec **plus de 5 membres**.
**Durée** : environ 20 minutes.
**Principe** : vos retours du 2026-11-10. **Aucun achat n'existe** : l'offre dit « Miff+ arrive bientôt ».
**Avant de commencer (3 lieux gratuits)** : la partie C ne marche qu'**après le déploiement des règles** par vous (PR `backend/lieux-gratuits-3`, procédure `docs/dev/deploiement-regles.md`). Avant : le 3e lieu est refusé par le serveur (message calme), le reste de la recette est valable.
**Build à installer** : le build de la PR « iOS : carrousel de Miffs au Profil, emoji des catégories, 3 lieux gratuits », par TestFlight (lancé après la revue).

## Ce qui change pour vous

- **Profil > Mes Miffs** devient un **carrousel** : une carte par Miff (nom, « N membres », petites photos des membres), puis en dernier une carte **« Créer une Miff »**. Les petits ronds sont les photos des membres (ou leurs initiales) ; au-delà de 5 membres, « +N ».
- Les **catégories de lieux** sont des **emoji** (🏠 💼 🏫 ⚽ 👨‍👩‍👧 🎉 📍), comme ceux du clavier de l'iPhone.
- Le compte gratuit peut avoir **3 lieux** (au lieu de 2).

## Partie A : le carrousel (Profil)

- [ ] **Ouverture** : le carrousel s'ouvre sur la Miff affichée dans le Feed (changez de Miff dans le Feed, revenez au Profil : c'est la nouvelle).
- [ ] Avec **P** : l'onglet Profil montre « Mes Miffs » et **une carte par Miff**, avec le **nom**, « 4 membres » en bleu et les **petits ronds** des membres (photo, sinon initiales). Les petits points sous la carte indiquent **combien de cartes** (Miffs + « Créer une Miff »).
- [ ] **Glissez** la carte vers la gauche : la carte suivante arrive avec un léger ressort ; la dernière est **« Créer une Miff »**. Glisser vers la droite revient. Au bout, la carte résiste et revient (rien ne part dans le vide).
- [ ] **Glisser ne touche jamais une carte** : glissez lentement, vite, court (quelques points) ou long, jusqu'à « Créer une Miff » puis en sens inverse, doigt posé sur n'importe quelle carte : **rien ne s'ouvre** au relâchement, ni l'offre ni la création ni l'écran d'une Miff. Un **simple toucher** (doigt qui ne bouge pas) ouvre bien la carte touchée. Un glissement qui revient au point de départ n'ouvre rien non plus.
- [ ] Glisser **vers le haut ou le bas** sur une carte fait toujours défiler la page (le carrousel ne gêne pas).
- [ ] Une Miff de **plus de 5 membres** : 4 ronds puis « +N » dans le cinquième (N = nombre de membres restants).
- [ ] Toucher une carte de Miff ouvre **l'écran de cette Miff** ; le retour revient au Profil, sur la même carte.
- [ ] Avec **G** (une seule Miff) : 2 cartes (votre Miff, « Créer une Miff »). Toucher **« Créer une Miff »** ouvre **l'offre** (« Une seule Miff avec la formule gratuite », « Miff+ arrive bientôt »), **pas** le parcours de création ; la croix ramène au Profil.
- [ ] Avec **P** : toucher **« Créer une Miff »** ouvre l'écran **Créer une Miff** (pas l'offre).
- [ ] Les petits ronds de la 2e Miff de **P** apparaissent **un instant après** l'arrivée de la carte (lecture à la demande), sans message d'erreur ; hors connexion (mode avion) : la carte garde son nom et une phrase d'aide, **sans message d'erreur**.
- [ ] **Grande taille de texte** (Réglages > Accessibilité > Taille du texte) : les cartes s'agrandissent, **aucun texte coupé**, toutes les cartes ont la même hauteur ; au plus 3 petits ronds par carte aux plus grandes tailles.
- [ ] **Mode sombre** de l'iPhone : l'app reste claire comme avant (aucun changement attendu).
- [ ] **VoiceOver** : chaque carte est lue « Ouvrir la Miff {nom}, 4 membres, Carte 2 sur 3 » ; la carte de création « Créer une Miff, Carte 3 sur 3 » ; l'élément sous le carrousel est lu « Carte 1 sur 3 » et se règle en glissant **un doigt vers le haut ou le bas** ; les actions « Carte précédente / suivante » sont proposées. Les petits ronds ne sont pas lus un par un.
- [ ] **Réduire les animations** (Accessibilité > Mouvement) : le changement de carte se fait sans animation.

## Partie B : emoji des catégories de lieux

- [ ] **Nouveau lieu** : les sept catégories montrent chacune son emoji : 🏠 Maison, 💼 Travail, 🏫 École, ⚽ Sport, 👨‍👩‍👧 Chez un proche, 🎉 Loisir, 📍 Autre. « Maison » est cochée au départ.
- [ ] Après avoir choisi une adresse, le **repère de la carte** montre l'emoji de la catégorie choisie ; il change quand vous changez de catégorie.
- [ ] **Liste des lieux** (écran de la Miff) : chaque ligne montre l'emoji de sa catégorie dans le carré bleu pâle, puis le nom et l'adresse. Un ancien lieu **sans catégorie** garde le petit repère habituel (pas « Autre »).
- [ ] **Détail d'un lieu** : la ligne « Catégorie » affiche l'emoji devant le nom (« 🏠 Maison ») ; la carte a l'emoji dans son repère.
- [ ] **VoiceOver** : seul le **nom** de la catégorie est lu (« Maison, sélectionné »), jamais « maison avec jardin » ou le nom de l'emoji.
- [ ] **Grande taille de texte** : le carré et l'emoji grandissent un peu sans déformer les lignes. **Mode sombre** : les emoji restent lisibles.
- [ ] **Feed** : l'étiquette d'une personne **dans un lieu de confiance** commence par l'emoji de la catégorie (« 🏠 À la maison », « 💼 Au travail », « 🏫 À l'école », « ⚽ Au sport », « 👨‍👩‍👧 Chez un proche » ; un lieu Loisir ou Autre : « 🎉 Le club »). **Aucun emoji** sur « A quitté… », « Pas encore de nouvelles », une zone de danger, ni pour un lieu sans catégorie ou supprimé. VoiceOver lit le texte seul.

## Partie C : 3 lieux gratuits (après le déploiement des règles)

- [ ] Avec **G** : le compteur de la liste des lieux indique **« 1 lieu sur 3 avec la formule gratuite »**, puis « 2 lieux sur 3 » à mesure que vous en créez.
- [ ] Vous pouvez créer **3 lieux** de confiance sans voir l'offre ; chaque enregistrement réussit.
- [ ] La liste indique « 3 lieux sur 3 avec la formule gratuite » et **« Ajouter un lieu »** reste visible avec la pastille Miff+.
- [ ] Toucher « Ajouter un lieu » ouvre l'offre **« Tu as atteint 3 lieux »** (« C'est la limite de la formule gratuite. Avec Miff+, ajoute autant de lieux que tu veux. »), rien n'est écrit.
- [ ] Avec **P** : pas de compteur, pas de limite.
- [ ] **Avant** le déploiement des règles : le 3e lieu est refusé avec un message calme (pas de plantage, rien d'enregistré) — c'est attendu.

## Ce qu'il faut me dire

Notez les écarts avec ce qui est écrit ici (capture d'écran si possible), en particulier : la **fluidité du glissement**, le rendu des **photos réelles** de vos membres, la lisibilité des **emoji** (⚽ ou 🏋️ pour le sport, 👨‍👩‍👧 ou 🏡 pour « Chez un proche » : la table est commune avec Android, un changement se fait sur les deux).
