# Recette iOS : « Répartition de ton temps » sur le Profil (TIME-3 et TIME-4) — parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5.2), build TestFlight de la PR `ios/profil-repartition-temps`.
**Il vous faut** : un compte avec **au moins une Miff** qui a **un lieu de confiance** (idéalement 3 lieux de catégories différentes), et, pour les étapes de la zone de danger, **une Miff dont le créateur est Miff+ avec une zone de danger** (ou la vôtre si vous êtes Miff+). **« Toujours » + position précise** accordés à Miff.
**Durée** : environ 20 minutes pour l'interface (grâce aux données de test) ; **7 jours de port du téléphone** pour voir le vrai graphique (partie D). **Source** : ticket `docs/tickets/profil-repartition-temps.md` (critères TS-1 à TS-25) et vos retours du 2026-11-17 ; maquette `19.webp` (carte Android) ; mêmes cas de calcul qu'Android (`profil-repartition-temps-android-recette-pm.md`).

## À savoir avant de commencer

- **Ce que ça fait** : sous « Mes Miffs », la carte « Répartition de ton temps » montre **où vous avez passé votre temps** (vos lieux de confiance, une éventuelle zone de danger, « Ailleurs ») sur 7, 14 ou 30 jours. **Seulement votre temps**, jamais celui de vos proches. **Tout reste sur votre téléphone** : rien n'est envoyé, aucune lecture ni écriture sur le serveur, aucune permission de plus.
- **L'historique commence à l'installation de cette version, et le graphique n'apparaît qu'après 7 jours** : avant, la carte dit **« Ton graphique arrive dans X jours, nous analysons tes habitudes »** (X = 7 le premier jour, puis 6, 5… jusqu'à « 1 jour »), **sans graphique et sans sélecteur**. C'est voulu (jamais de donnée inventée).
- **Pour ne pas attendre 7 jours** : cette build de TestFlight contient un outil de recette. **Sous la carte, un bouton de données de test** passe à chaque toucher d'un état à l'autre : **graphique** (30 jours d'habitudes inventées à partir des lieux de la Miff), **2 jours d'historique** (« dans 5 jours »), **6 jours** (« dans 1 jour »), puis retour à votre vrai historique. Ces données sont **en mémoire seulement, jamais enregistrées** (votre vrai historique n'est pas touché ; elles disparaissent à la relance de l'app). **Cet outil n'existe pas dans l'app publiée.**
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.

## Partie A : la carte et ses états

1. [ ] Ouvrez l'onglet **Profil**. La carte est **entre le carrousel « Mes Miffs » et la liste « Lieux de confiance de … »** ; la carte « Mode voyage » reste **en dernier**. **Sans données de test et avant 7 jours** : le titre **« Répartition du temps »**, le texte exact **« Ton graphique arrive dans X jours, nous analysons tes habitudes »** et l'anneau vide gris ; **ni graphique, ni sélecteur, ni sous-titre, ni ligne de confidentialité**. Carte blanche très arrondie, pastille d'icône **verte** à gauche du titre.
2. [ ] Touchez le bouton de données de test (**« le graphique »**) : l'**anneau est plus petit, à gauche**, avec **toutes** les tranches, et la **légende est à droite** (pastille, emoji de la catégorie comme dans la liste en dessous, nom, pourcentage **aligné à droite**), limitée aux **4 parts où vous avez passé le plus de temps** (la 1re à midi, sens horaire). **Au centre** : un grand pourcentage et **« en zone de confiance »**. L'en-tête montre le titre et le sélecteur **« 7 jours ⌄ »** à droite, **sans** « Sur les 7 derniers jours ». En dessous, l'**encart vert très léger** avec la coche et **« Tu as passé x % de ton temps dans tes zones de confiance »**, le **🎉 à droite**.
3. [ ] **Couleurs** : les lieux de confiance sont des **nuances de vert et de bleu** (en alternance), la **zone de danger** est **exactement le rouge de la bulle de statut danger** du Feed (hachurée, avec le triangle), « **Hors-zone** » est le **gris clair de la maquette**. Le total des pourcentages de la légende **peut ne pas faire 100 %** (la légende n'affiche que 4 parts) ; l'anneau, lui, est complet. Un compte gratuit (3 lieux au plus) a **2 à 4 parts**.
4. [ ] **Sélecteur** : touchez « 7 jours ⌄ » : un **menu à trois choix** (7, 14, 30 jours), le choix actuel **coché**. Choisissez 14 puis 30 : les pourcentages changent. **Fermez complètement l'app et rouvrez-la** : la fenêtre choisie est **gardée** (les données de test, elles, ont disparu : c'est normal).
5. [ ] **Balayez le carrousel** vers une autre Miff : la carte suit **cette Miff** (ses lieux, ses pourcentages), **sans clignotement** ni trou intermédiaire ; sur la carte « Créer une Miff », la carte garde la dernière Miff regardée.
6. [ ] **Miff sans lieu** : la carte **n'est pas dessinée** (pas même une marge) ; la section des lieux porte son message.
7. [ ] **Plus de 5 lieux** (si vous en avez en Miff+) : **5 lieux nommés**, puis **« 2 autres lieux »**, la zone de danger, « Hors-zone ».

## Partie B : la zone de danger

8. [ ] Dans une Miff qui a une **zone de danger** (les données de test y passent quelques heures tous les cinq jours en semaine) : **une seule ligne « Zone de danger »** (ou « Zones de danger »), avec le **triangle**, la tranche **hachurée** et une **flèche**, **toujours présente dès que sa part est d’au moins 1 %** (elle remplace alors le 4e lieu de la légende si besoin) ; sous 1 %, **pas de ligne** dans la légende (la tranche reste dans l’anneau). Le pourcentage **au centre ne compte pas** la zone de danger. **VoiceOver** : la zone de danger n’est dite qu’une fois (par sa ligne, « …, afficher le détail »).
9. [ ] **Touchez la ligne** : une feuille s'ouvre avec **un lieu par ligne** (emoji, nom, pourcentage) dont la **somme est celle de la ligne**. Fermez avec la **croix** puis rouvrez et fermez en **tirant la feuille vers le bas**.
10. [ ] **Aucune zone de danger** dans la Miff (ou aucun temps passé dedans) : **aucune ligne** « Zone de danger », jamais « 0 % ».
11. [ ] Le texte de l'**encart vert** **ne nomme jamais** la zone de danger. **Ton** : l'encart est affiché chaque fois que le graphique l'est, y compris avec un très petit pourcentage (« Tu as passé 3 % de ton temps… » avec 🎉) : c'est votre décision, notez simplement si cela vous gêne.

## Partie C : états du suivi, langue, accessibilité

12. [ ] **Mode voyage** : activez le mode voyage (carte en bas du Profil), **sans données de test** : la carte dit **« Tu es en mode voyage : ta répartition est en pause jusqu'à ton retour. »** (pas le texte « nous analysons ta situation »). Désactivez le voyage.
13. [ ] **Localisation** (à faire seulement si l’app vous laisse sur le Profil : tant que la localisation « Toujours » est exigée, l’écran de blocage de la localisation peut s’afficher à la place, c’est normal et cette étape est alors sans objet) : Réglages iOS > Miff > Localisation > **« Lors de l’utilisation »**, rouvrez le Profil sans données de test : **« Pour mesurer ton temps dans tes lieux, Miff a besoin de la localisation « Toujours » avec la position précise. Tu peux aussi continuer sans. »** et le bouton **« Ouvrir les réglages »**. Remettez « Toujours ».
14. [ ] **Avec des données de test** et la localisation insuffisante : le graphique **reste** et une ligne calme dit **« Le suivi de tes lieux est en pause. »**.
15. [ ] **VoiceOver** : le focus sur l'anneau lit **une description unique de TOUTES les parts** (« Répartition du temps : Maison 48 %, Sport 24 %, … en zone de confiance, 72 % ») sans lire les emoji ; la ligne « Zone de danger » est lue « …, afficher le détail » et ouvre la feuille ; le sélecteur est annoncé **« Période, 7 jours »** et le choix actuel **« choisi »** ; cibles de 44 pt au moins.
16. [ ] **Plus grand texte** (Réglages > Accessibilité > Affichage et taille du texte > Texte plus grand, **Tailles d'accessibilité** activées, au maximum) : la **légende passe sous l'anneau**, rien n'est coupé ni superposé, le sélecteur passe sous le titre.
17. [ ] **Anglais** (langue de l'app dans Réglages iOS) : « Time breakdown », « in trusted zones », « Outside zones », « Danger zone », « You spent 72% of your time in your trusted zones », « Your chart arrives in 3 days, we're analysing your habits » (« in 1 day »), pourcentage au format « 72% ».

## Partie D : la vraie collecte (une journée de port du téléphone)

Sans les données de test, avec **un vrai trajet** (ou une localisation simulée si vous préférez) :

18. [ ] **Écriture téléphone verrouillé et app fermée** (TS-8) : l'app **fermée** (balayée), le téléphone **verrouillé**, entrez dans un de vos lieux puis sortez-en. Rouvrez l'app **plus tard** : le journal a bien enregistré le passage (la carte le montre **après 7 jours d'historique** ; avant, le décompte « dans X jours » baisse d'un jour par jour et aucun message d'erreur n'apparaît). **Aucun événement n'est perdu** parce que le téléphone était verrouillé.
19. [ ] **Après 7 jours de port** (le décompte « dans X jours » descend jusqu'à « dans 1 jour ») : la carte **passe du texte au graphique**. Les pourcentages sont **cohérents avec le Feed** (le temps passé dans un lieu correspond aux arrivées et départs que vos proches ont vus). La répartition est **indicative** (événements datés à leur arrivée dans l'app ; un téléphone éteint ou à plat compte « Hors-zone » jusqu'à 48 h au plus).
20. [ ] **Ce que vous voyez est cohérent avec le Feed** : un lieu supprimé par le créateur **n'est plus compté** (jamais en « Hors-zone »).

## Partie E : effacement et vie privée

21. [ ] **Réglages > Confidentialité** : la section **« Ton historique de temps »** explique que **35 jours** sont gardés **sur le téléphone** et ne le quittent jamais, avec le bouton **« Effacer mon historique de temps »**.
22. [ ] Touchez-le : la confirmation **« Effacer l'historique ? Ta répartition repartira de zéro. Cela ne change rien pour tes proches. »** (**Annuler** / **Effacer**). **Effacer** : le message « C'est fait… » ; de retour sur le Profil, la carte dit de nouveau « Ton graphique arrive dans 7 jours… ». **Vos proches ne voient aucun changement.**
23. [ ] **Déconnexion puis reconnexion** (preuve TS-5) : la carte repart de **« Ton graphique arrive dans 7 jours… »** ; la fenêtre choisie est **revenue à 7 jours**. **« Effacer mon historique de temps »** fait aussi repartir le décompte à **7 jours**.
24. [ ] **Quitter une Miff** puis la rejoindre : son historique est **reparti de zéro**. **Aucune demande de permission de plus** n'est apparue pendant toute la recette.
25. [ ] Mode avion : **aucun effet** sur la carte (aucune dépendance au réseau).

## Ce qui est volontairement différent d'Android ou connu

- **Aperçu des applications récentes** : sur iOS, la carte peut apparaître dans le sélecteur d'applications (aucun masquage dans l'app, comme pour le reste ; limite connue D17). Android 13+ le masque.
- **Partage arrêté** : le message « Tu as arrêté de partager ton statut… » (avec un bouton vers la Miff) n'est affiché que pour la **Miff du Feed** ; pour une autre Miff du carrousel, la carte n'affirme rien.
- **Historique propre à l'appareil** : il commence à cette version et est perdu si vous changez de téléphone.
- **Texte « nous analysons tes habitudes »** : wording figé par le PM, **risque assumé** (il peut laisser croire à une analyse côté serveur alors que tout reste sur le téléphone, ce que la ligne du dessous dit) ; il n'est affiché que quand le suivi tourne (sinon : voyage, partage arrêté, localisation). Le nombre de jours est recalculé quand vous ouvrez le Profil ou rouvrez l'app, pas à la seconde.
- **Aperçu de ton** : l'encart « Tu as passé x % de ton temps dans tes zones de confiance » avec 🎉 s'affiche aussi pour un très petit pourcentage (décision du PM).
- **Ne partez pas sur l'App Store avec cette fonctionnalité avant la mise à jour de la politique de confidentialité** (ticket §8).
