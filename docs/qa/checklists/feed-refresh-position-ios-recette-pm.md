# Recette iOS : « Tirer pour actualiser » revérifie aussi MA présence (REFRESH-POS-I) : parcours pour le PM

**Pour qui** : vous, sur votre **iPhone 11 Pro (iOS 26.5.2, TestFlight)**, avec **deux comptes jetables dans la même Miff** : **A** (sur l'iPhone) et **B** (un proche, sur l'OPPO ou un second téléphone). Le compte A a au moins **un lieu de confiance** (par exemple la maison) et a accordé la localisation **« Toujours »** + la position exacte.
**Ce que fait le geste** : tirer le Feed vers le bas relit les proches comme avant **et**, en parallèle, vérifie où est A : une seule mesure de position, au plus une par 30 s (ouverture de l'app comprise). Un statut n'est envoyé que si la présence a changé ; la dernière position n'est republiée que si la règle habituelle (15 min, ou 250 m, jamais deux fois en moins de 2 min) le permet.
**Durée** : environ 20 minutes. **Spec** : `docs/tickets/feed-refresh-position.md`. Pour **voir** ce que fait l'app (build Internal TestFlight seulement) : Réglages > Diagnostic > « journal de détection » : une ligne `wakePull` par geste exécuté, puis `positionRead`, `positionWritten` / `positionSkippedGuard`, `regionInside` / `regionOutside`, `statusWritten`. **Aucune coordonnée n'y figure.**

## Avant de commencer

- Installez le **build TestFlight** de la PR iOS (workflow `ios-testflight`, lancé par vous). **Projet Firebase : `miff-3d283` uniquement.**
- Les deux comptes partagent dans la Miff. A est chez lui, et sa carte est **verte** chez B. Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- Gardez le journal de détection de A ouvert entre les étapes (ou notez le nombre de lignes avant et après).

## Partie 1 : le geste de base (A, onglet Feed) (4 min)

1. [ ] Tirez le Feed vers le bas **une fois** : une **pastille claire avec une roue qui tourne** apparaît **juste sous l'en-tête**, bien visible, **au moins 0,8 s**, puis disparaît en fondu (en quelques secondes au plus). **Aucun sautillement** : la bulle et les cartes ne bougent pas brusquement, ni au début, ni à la fin du geste (elles peuvent descendre doucement avec le doigt, comme sur n'importe quelle liste iOS). Les cartes des proches se mettent à jour. **Aucun nouveau message, aucune fenêtre.**
2. [ ] Journal : une ligne **`wakePull`**, puis `positionRead` (une mesure). Rien d'autre d'obligatoire.
3. [ ] **VoiceOver** : l'action « Actualiser » est annoncée comme avant (rotor des actions, double toucher). Rien de nouveau n'est lu.
4. [ ] Tirez **immédiatement** une seconde fois : la roue s'affiche aussi **0,8 s** (le geste ne doit jamais avoir l'air de ne rien faire) mais **rien n'est relu** (limite habituelle de 15 s pour les proches), **aucune nouvelle ligne `wakePull`**.
5. [ ] Attendez **20 s**, tirez encore : les proches sont relus (plus de 15 s), mais **aucune nouvelle ligne `wakePull`** (moins de 30 s depuis le geste de l'étape 1). Attendez **encore 15 s**, tirez : une ligne `wakePull`.

## Partie 2 : ma présence est rattrapée (A, B regarde) (6 min)

6. [ ] **Arrivée rattrapée.** A est **dehors**, à quelques centaines de mètres de la maison, **l'app ouverte** sur le Feed. Marchez jusqu'à la maison. iOS met souvent **une à plusieurs minutes** à signaler l'entrée dans un lieu : **dès que vous êtes devant la porte, tirez le Feed**. En **quelques secondes** chez B la carte de A dit **« Chez {lieu} »** (verte). Journal : `wakePull`, `regionInside`, `statusWritten`. *(Si iOS avait déjà signalé l'arrivée avant votre geste, rien ne change : c'est correct, refaites l'essai au prochain trajet.)*
7. [ ] **Rien ne change.** Tirez le Feed deux fois à plus de 30 s d'intervalle, sans bouger : **aucune ligne `statusWritten`** (la présence n'a pas changé) et, dans les 15 minutes qui suivent la dernière position écrite, **aucune ligne `positionWritten`** (`positionSkippedGuard` à la place).
8. [ ] **Position rafraîchie.** Marchez **plus de 300 m** (ou attendez **plus de 15 minutes**), tirez le Feed : une ligne `positionWritten`. Chez B, « Localiser » sur A montre la **nouvelle position** (rappel : ce n'est pas une position en direct).
9. [ ] **Départ rattrapé.** A est chez lui, l'app ouverte (carte verte « Chez {lieu} » chez B). Sortez et marchez **plus de 300 m** sans toucher à l'iPhone ; si la carte de A chez B affiche encore « Chez {lieu} » (iOS peut tarder), **tirez le Feed** : la carte cesse d'afficher « Chez {lieu} » (« pas de nouvelles récentes » : l'heure de départ exacte n'est pas connue pour un départ rattrapé, c'est la règle habituelle). Aucune alerte rouge.

## Partie 3 : sans accord, hors connexion, partage arrêté (A) (6 min)

10. [ ] **Hors connexion** : mode avion, tirez le Feed : l'indicateur se **termine tout de suite**, la mention « Dernières informations de… » est celle d'habitude, **aucune fenêtre d'erreur**. Journal : **aucune** ligne `wakePull`.
11. [ ] **Partage arrêté** dans la Miff (Profil > ma Miff > arrêter le partage), puis tirez le Feed : les proches sont relus ; **aucune** ligne `positionRead` ni `positionWritten`. Reprenez le partage ensuite.
12. [ ] **Localisation retirée** (Réglages d'iOS > Miff > Position > « Jamais » ou « Lorsque l'app est active »), puis retour dans l'app : **l'écran de la localisation prend la place du Feed**, comme avant ce chantier. Rétablissez « Toujours » + position exacte.
13. [ ] **Position approximative** (Réglages d'iOS > Miff > Position > désactiver « Position exacte »), puis retour dans l'app : même chose, l'écran de la localisation apparaît. Réactivez la position exacte.
14. [ ] *(Seulement si vous disposez d'un build qui relâche le blocage de la localisation)* sans « Toujours », tirez le Feed : les proches sont relus, **aucune** mesure, **aucun** message supplémentaire. **Dites-moi si vous voulez un message** dans ce cas (voir le ticket, D2).

## Partie 4 : mode voyage (A) (3 min)

15. [ ] Activez le **mode voyage** pour demain. Tirez le Feed : **aucun** `statusWritten` (la carte de A reste grise « En voyage… » chez B). La position, elle, reste partagée comme avant (le geste la rafraîchit, sous les mêmes gardes : ligne `positionWritten` seulement après 15 min ou 250 m). Désactivez le voyage.

## Partie 5 : fluidité et batterie (1 min d'observation)

16. [ ] Tirez le Feed **cinq fois de suite** à quelques secondes d'intervalle : le Feed reste fluide, **une seule** ligne `wakePull` dans la minute.
17. [ ] Dans les 24 h qui suivent un usage normal, **Réglages iOS > Batterie > Miff** : pas d'écart notable avec les jours précédents (le geste ne mesure qu'au plus une fois par 30 s). Notez le pourcentage « Miff » si vous voulez que je compare.

## Partie 6 : retour du TestFlight 38 (indicateur visible, aucun sautillement) (3 min)

18. [ ] Tirez le Feed **en haut de liste**, puis **après avoir un peu défilé** (l'en-tête partiellement masqué) : la pastille est visible **sous l'en-tête**, jamais cachée derrière lui ni derrière la bulle, et **rien ne saute**.
19. [ ] Filmez l'écran (enregistrement d'écran d'iOS) et rejouez-le au ralenti : **aucune image où la bulle ou une carte disparaît, remonte puis redescend**. Si un sautillement reste, envoyez-moi la vidéo et l'étape où il apparaît (début du geste, relâchement ou fin de la roue).
20. [ ] **Réglages iOS > Accessibilité > Mouvement > Réduire les animations** activé : la pastille apparaît et disparaît **sans fondu**, le Feed ne saute pas non plus.

21. [ ] **Indicateur piloté par le tirage.** Tirez le Feed LENTEMENT et sans relâcher : une pastille ronde apparaît sous l'en-tête dès les premiers points et **se remplit** (flèche vers le bas) ; quand c'est assez tiré (environ 70 points) la flèche se **retourne**. Relâchez : une **roue qui tourne** pendant **au moins 0,8 s**. Tirez un peu et relâchez avant la fin : la pastille disparaît sans roue. Si vous ne voyez toujours rien : dites-moi si c'est pendant le tirage, au relâchement, ou jamais.

## Ce que je vous demande de trancher (voir `docs/tickets/feed-refresh-position.md`, §3)

- **D2** : un message calme quand l'autorisation manque (aujourd'hui : aucun, l'écran de la localisation prend déjà toute la place) ?
- **D3** : pendant un voyage, le geste doit-il rafraîchir la position (aujourd'hui oui, comme l'ouverture de l'app) ou ne rien faire ?
- **D4** : chaque geste doit-il rafraîchir l'**heure** de la dernière position visible par les proches (aujourd'hui non : la règle des 15 min / 250 m reste la règle) ?
