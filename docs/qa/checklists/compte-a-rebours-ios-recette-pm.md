# Recette iOS : compte à rebours avant le passage à l'orange (COUNTDOWN-I) — parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5.2), avec le build TestFlight de la PR.
**Il vous faut** : un compte avec **une Miff et au moins un lieu de confiance** (par exemple « Maison »), la **localisation « Toujours »** accordée, et **deux comptes dans la même Miff** pour l'étape 12 (un deuxième téléphone ou un autre compte).
**Durée** : environ 20 minutes d'actions, mais le test réel du décompte demande de **rester dehors** jusqu'à 2 h : prévoir de le lire de temps en temps dans la journée (étapes 17 à 19 seulement).
**Source** : ticket `docs/tickets/compte-a-rebours-orange.md` (critères CD-1 à CD-27, CD-I1, CD-I2), décisions du PM du 2026-11-13 (pas de maquette). Miroir de la recette Android.

## À savoir avant de commencer

- L'encart n'existe que **pour vous, sur votre téléphone** : jamais pour un proche, rien n'est envoyé (aucune écriture, aucune notification). Sur le Profil et l'Update, l'app lit **votre seul statut** (un document) pour que l'encart reste juste.
- Il apparaît **quand vous êtes dehors de vos lieux de confiance ET encore en vert** (moins de 2 h après votre départ ou votre dernière update), et disparaît à l'orange.
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- **Limite connue** (voir la PR) : si vous avez fait défiler le Feed au-delà de l'encart au moment où il disparaît, le contenu remonte d'un cran (iOS 16 n'offre pas de position de lecture stable). Les autres cas sont sans saut.

## Partie A : il apparaît, où et comment

1. [ ] **Dans** un lieu de confiance (ou jamais passé par un lieu) : **aucun** encart, ni sur le Feed ni sur le Profil.
2. [ ] **Sortez** du lieu (dépassez son rayon, quelques minutes après le départ) : sur le **Feed**, un encart apparaît **juste sous la grande bulle colorée** de la Miff, **avant** les cartes des proches. Il dit **« Tu es en dehors de tes lieux de confiance »** puis **« Tu passeras orange dans 1 h 5x min »** (environ 2 h moins le temps écoulé), avec un **sablier** à gauche et un **chevron** discret à droite.
3. [ ] Onglet **Profil** : le **même** encart est le **premier bloc**, juste sous votre nom et l'engrenage, **au-dessus de « Mes Miffs »**. Le reste de l'écran est inchangé (« Mode voyage » reste tout en bas, la liste des lieux et l'encart « À valider » du créateur à leur place).
4. [ ] Passez d'un onglet à l'autre : la **même durée** partout, sans décalage.
5. [ ] La **bordure** : une piste grise fine et, par-dessus, un arc jaune-vert qui **part du haut au centre** et qui **se vide dans le sens des aiguilles d'une montre** (après 30 min, le quart en haut à droite est parti ; après 1 h, la moitié droite). Elle est **pleine** juste après une update.
6. [ ] **Dernière demi-heure** : la bordure **vire doucement** vers l'orange, **jamais rouge**, sans clignoter ni vibrer.
7. [ ] **Entre deux minutes, rien ne bouge** ; à chaque minute, la bordure glisse doucement (environ une demi-seconde) et le texte change.
8. [ ] Avec **« Réduire les animations »** (Réglages > Accessibilité > Mouvement) : la bordure et le texte changent **d'un coup**, l'encart apparaît et disparaît **sans fondu**.

## Partie B : ce qu'il dit

9. [ ] Les durées : « 1 h 12 min », « 1 h », « 47 min », et pour la dernière minute **« moins d'1 min »** ; **jamais de secondes**, jamais « 0 min ». Un espace insécable relie le nombre à son unité (« 47 min » ne se coupe pas sur deux lignes).
10. [ ] En **anglais** : « You're outside your trusted places » / « You'll turn orange in 1 h 12 min » / « less than 1 min ».
11. [ ] Ton : aucun point d'exclamation, aucun mot d'alerte, aucun rouge dans l'encart.

## Partie C : quand il disparaît, quand il revient

12. [ ] **Un proche (deuxième compte, même Miff)** ne voit **rien** : ni encart, ni durée, ni mention sur votre carte ni dans la liste des membres. Et inversement : l'encart du proche n'est jamais sur **votre** écran.
13. [ ] **Rentrez** dans un lieu de confiance : l'encart disparaît (fondu court). Ressortez : il revient, la durée repart de l'heure du départ.
14. [ ] **Touchez l'encart** : l'écran **Update** s'ouvre (le même que le bouton central). Quittez sans publier : rien ne change. Publiez une update (photo) : retour sur le Feed, l'encart affiche **« 2 h »** (remis à zéro) et la bordure est **pleine**.
14 bis. [ ] **Depuis le Profil** : touchez l'encart du **Profil**, publiez une update, puis ouvrez l'onglet **Profil** : l'encart affiche **« 2 h »**, bordure **pleine** (jamais l'ancienne durée). Même chose en passant du Feed au Profil juste après.
14 ter. [ ] **Restez sur le Profil**, écran allumé, et **entrez** dans un lieu de confiance : en quelques secondes (selon la détection du téléphone) l'encart **disparaît**, sans quitter le Profil ; il ne dit plus « Tu es en dehors de tes lieux de confiance ». Si l'encart ne se met pas à jour, changez d'onglet puis revenez : notez-le (étape 14 ter) avec une capture.
15. [ ] **Mode voyage** activé (Profil > Mode voyage) : l'encart disparaît. Désactivé : il revient seulement s'il y a lieu.
16. [ ] **Arrêter le partage** de la Miff affichée : l'encart disparaît ; il revient à la reprise s'il y a lieu.
17. [ ] **Mode avion** : l'encart **reste affiché et continue de décompter**, sans message d'erreur.
18. [ ] **Verrouillez l'iPhone**, attendez quelques minutes, **rouvrez** : dès la première image la durée est **juste** (pas l'ancienne valeur qui se corrige). Même chose en mettant l'app en arrière-plan.
19. [ ] **À l'échéance** (2 h après le départ ou la dernière update) : l'encart **disparaît** et la bulle d'incitation orange (« Tu es passé en orange… ») prend le relais ; **jamais** les deux ensemble, jamais « 0 min ». (Au plus une minute d'écart avec la couleur du bouton central : la minuterie existante de la coquille n'a pas été touchée.) Orange ou rouge : **aucun** encart. Une update depuis l'orange ou le rouge : l'encart **réapparaît** à « 2 h ».

## Partie D : VoiceOver, grand texte

20. [ ] **VoiceOver** : l'encart est **un seul bouton** (pas le sablier, pas le chevron). Il lit « Tu es en dehors de tes lieux de confiance. Tu passeras orange dans 1 heure et 12 minutes. » puis l'indication **« Donner de tes nouvelles »**. Un double toucher ouvre l'Update. **Aucune annonce** chaque minute ni à la disparition.
21. [ ] **Grand texte** (Réglages > Accessibilité > Affichage et luminosité > Taille du texte, plus grande taille d'accessibilité) : **tout** le texte est visible sur plusieurs lignes, l'encart **grandit**, la bordure suit son contour, le sablier ne dépasse pas une fois et demi sa taille, la zone à toucher fait au moins 44 pt.
22. [ ] **Droite-à-gauche** (langue arabe par exemple, si vous voulez) : le sens de la bordure reste celui d'une horloge.

## Partie E : rien de plus coûté, rien d'abîmé

23. [ ] Aucune notification nouvelle ; la bulle d'incitation, le carrousel « Mes Miffs », l'encart « À valider » du créateur, le mode voyage et les couleurs des proches se comportent **comme avant**.
24. [ ] Batterie : l'encart ne réveille pas l'iPhone (aucun minuteur écran éteint ni app en arrière-plan) ; vérifiable à la fin de la journée dans Réglages > Batterie (l'app Miff ne doit pas sortir du lot).
