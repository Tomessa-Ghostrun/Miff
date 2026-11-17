# Recette iOS : « Répartition de ton temps » sur le Profil (TIME-3 et TIME-4) — parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5.2), build TestFlight de la PR `ios/profil-repartition-temps`.
**Il vous faut** : un compte avec **au moins une Miff** qui a **un lieu de confiance** (idéalement 3 lieux de catégories différentes), et, pour les étapes de la zone de danger, **une Miff dont le créateur est Miff+ avec une zone de danger** (ou la vôtre si vous êtes Miff+). **« Toujours » + position précise** accordés à Miff.
**Durée** : environ 20 minutes pour l'interface (étapes 1 à 17, grâce aux données de test) ; **une journée de port du téléphone** pour valider la vraie collecte (partie D). **Source** : ticket `docs/tickets/profil-repartition-temps.md` (critères TS-1 à TS-25) ; maquette `19.webp` (carte Android) ; mêmes cas de calcul qu'Android (`profil-repartition-temps-android-recette-pm.md`).

## À savoir avant de commencer

- **Ce que ça fait** : sous « Mes Miffs », la carte « Répartition de ton temps » montre **où vous avez passé votre temps** (vos lieux de confiance, une éventuelle zone de danger, « Ailleurs ») sur 7, 14 ou 30 jours. **Seulement votre temps**, jamais celui de vos proches. **Tout reste sur votre téléphone** : rien n'est envoyé, aucune lecture ni écriture sur le serveur, aucune permission de plus.
- **L'historique commence à l'installation de cette version** : sans données de test, la carte dit « Ton graphique arrive dans quelques jours, nous analysons ta situation » tant qu'il y a moins de **24 h mesurées**. C'est voulu (jamais de donnée inventée).
- **Pour ne pas attendre 7 jours** : cette build de TestFlight contient un outil de recette. **Sous la carte, le bouton « Afficher des données de test »** montre 30 jours d'habitudes **inventées à partir des lieux de la Miff** (en mémoire seulement, jamais enregistrées, votre vrai historique n'est pas touché ; elles disparaissent à la relance de l'app). **Cet outil n'existe pas dans l'app publiée.**
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.

## Partie A : la carte et ses états

1. [ ] Ouvrez l'onglet **Profil**. La carte est **entre le carrousel « Mes Miffs » et la liste « Lieux de confiance de … »** ; la carte « Mode voyage » reste **en dernier**. Sans données : l'**anneau vide gris** et le texte exact **« Ton graphique arrive dans quelques jours, nous analysons ta situation »**, la ligne **« Ces données restent sur ton téléphone. Personne d'autre ne peut les voir. »** avec un cadenas, et le sélecteur **« 7 jours ⌄ »**.
2. [ ] Touchez **« Afficher des données de test »** : l'**anneau** apparaît (tranches séparées par un petit espace, **première tranche à midi**, sens horaire), **au centre un pourcentage** et « dans tes lieux de confiance », la **légende** (pastille de couleur, **emoji de la catégorie** du lieu comme dans la liste en dessous, nom, pourcentage à droite), puis un **bandeau bleuté** avec une phrase **descriptive** (« Sur les 7 derniers jours, c'est à <lieu> que tu as passé le plus de temps. »), **sans coche verte, sans emoji de fête, sans point d'exclamation**.
3. [ ] Le total des pourcentages de la légende fait **100 %**. « Ailleurs » est **toujours en dernier**. Un lieu à 0 n'est pas listé. Un compte gratuit (3 lieux au plus) a **2 à 4 parts** lisibles.
4. [ ] **Sélecteur** : touchez « 7 jours ⌄ » : un **menu à trois choix** (7, 14, 30 jours), le choix actuel **coché**. Choisissez 14 puis 30 : le sous-titre (« Sur les 14 derniers jours ») et les pourcentages changent. **Fermez complètement l'app et rouvrez-la** : la fenêtre choisie est **gardée** (les données de test, elles, ont disparu : c'est normal).
5. [ ] **Balayez le carrousel** vers une autre Miff : la carte suit **cette Miff** (ses lieux, ses pourcentages), **sans clignotement** ni trou intermédiaire ; sur la carte « Créer une Miff », la carte garde la dernière Miff regardée.
6. [ ] **Miff sans lieu** : la carte **n'est pas dessinée** (pas même une marge) ; la section des lieux porte son message.
7. [ ] **Plus de 5 lieux** (si vous en avez en Miff+) : **5 lieux nommés**, puis **« 2 autres lieux »**, la zone de danger, « Ailleurs ».

## Partie B : la zone de danger

8. [ ] Dans une Miff qui a une **zone de danger** (les données de test y passent quelques heures tous les cinq jours en semaine) : **une seule ligne « Zone de danger »** (ou « Zones de danger » s'il y en a plusieurs), avec le **triangle d'avertissement**, la tranche **hachurée** et une **flèche** ; le pourcentage **au centre ne compte pas** la zone de danger. Aucune autre couleur d'alerte sur la carte (ni dans le bandeau, ni dans les états vides).
9. [ ] **Touchez la ligne** : une feuille s'ouvre avec **un lieu par ligne** (emoji, nom, pourcentage) dont la **somme est celle de la ligne**. Fermez avec la **croix** puis rouvrez et fermez en **tirant la feuille vers le bas**.
10. [ ] **Aucune zone de danger** dans la Miff (ou aucun temps passé dedans) : **aucune ligne** « Zone de danger », jamais « 0 % ».
11. [ ] La **phrase** du bandeau **ne nomme jamais** la zone de danger et ne donne aucun pourcentage.

## Partie C : états du suivi, langue, accessibilité

12. [ ] **Mode voyage** : activez le mode voyage (carte en bas du Profil), **sans données de test** : la carte dit **« Tu es en mode voyage : ta répartition est en pause jusqu'à ton retour. »** (pas le texte « nous analysons ta situation »). Désactivez le voyage.
13. [ ] **Localisation** (à faire seulement si l’app vous laisse sur le Profil : tant que la localisation « Toujours » est exigée, l’écran de blocage de la localisation peut s’afficher à la place, c’est normal et cette étape est alors sans objet) : Réglages iOS > Miff > Localisation > **« Lors de l’utilisation »**, rouvrez le Profil sans données de test : **« Pour mesurer ton temps dans tes lieux, Miff a besoin de la localisation « Toujours » avec la position précise. Tu peux aussi continuer sans. »** et le bouton **« Ouvrir les réglages »**. Remettez « Toujours ».
14. [ ] **Avec des données de test** et la localisation insuffisante : le graphique **reste** et une ligne calme dit **« Le suivi de tes lieux est en pause. »**.
15. [ ] **VoiceOver** : le focus sur l'anneau lit **une description unique** (« Répartition de ton temps. Sur les 7 derniers jours : Maison 48 %, Sport 24 %, … ») sans lire les emoji ; la ligne « Zone de danger » est lue « …, afficher le détail » et ouvre la feuille ; le sélecteur est annoncé **« Période, 7 jours »** et le choix actuel **« choisi »** ; cibles de 44 pt au moins.
16. [ ] **Plus grand texte** (Réglages > Accessibilité > Affichage et taille du texte > Texte plus grand, **Tailles d'accessibilité** activées, au maximum) : la **légende passe sous l'anneau**, rien n'est coupé ni superposé, le sélecteur passe sous le titre.
17. [ ] **Anglais** (langue de l'app dans Réglages iOS) : « How you spend your time », « Over the last 7 days », « in your trusted places », « Elsewhere », « Danger zone », « This data stays on your phone. Nobody else can see it. », pourcentage au format « 72% ».

## Partie D : la vraie collecte (une journée de port du téléphone)

Sans les données de test, avec **un vrai trajet** (ou une localisation simulée si vous préférez) :

18. [ ] **Écriture téléphone verrouillé et app fermée** (TS-8) : l'app **fermée** (balayée), le téléphone **verrouillé**, entrez dans un de vos lieux puis sortez-en. Rouvrez l'app **plus tard** : le journal a bien enregistré le passage (la carte le montre **dès qu'il y a 24 h mesurées**, ou, avant, aucun message d'erreur). **Aucun événement n'est perdu** parce que le téléphone était verrouillé.
19. [ ] **Après environ 24 h de port** : la carte **passe de « pas assez de données » à l'anneau**. Les pourcentages sont **cohérents avec le Feed** (le temps passé dans un lieu correspond aux arrivées et départs que vos proches ont vus). La répartition est **indicative** (événements datés à leur arrivée dans l'app ; un téléphone éteint ou à plat compte « Ailleurs » jusqu'à 48 h au plus).
20. [ ] **Ce que vous voyez est cohérent avec le Feed** : un lieu supprimé par le créateur **n'est plus compté** (jamais en « Ailleurs »).

## Partie E : effacement et vie privée

21. [ ] **Réglages > Confidentialité** : la section **« Ton historique de temps »** explique que **35 jours** sont gardés **sur le téléphone** et ne le quittent jamais, avec le bouton **« Effacer mon historique de temps »**.
22. [ ] Touchez-le : la confirmation **« Effacer l'historique ? Ta répartition repartira de zéro. Cela ne change rien pour tes proches. »** (**Annuler** / **Effacer**). **Effacer** : le message « C'est fait… » ; de retour sur le Profil, la carte dit de nouveau « Ton graphique arrive dans quelques jours… ». **Vos proches ne voient aucun changement.**
23. [ ] **Déconnexion puis reconnexion** (preuve TS-5) : la carte repart de **« pas assez de données »** ; la fenêtre choisie est **revenue à 7 jours**.
24. [ ] **Quitter une Miff** puis la rejoindre : son historique est **reparti de zéro**. **Aucune demande de permission de plus** n'est apparue pendant toute la recette.
25. [ ] Mode avion : **aucun effet** sur la carte (aucune dépendance au réseau).

## Ce qui est volontairement différent d'Android ou connu

- **Aperçu des applications récentes** : sur iOS, la carte peut apparaître dans le sélecteur d'applications (aucun masquage dans l'app, comme pour le reste ; limite connue D17). Android 13+ le masque.
- **Partage arrêté** : le message « Tu as arrêté de partager ton statut… » (avec un bouton vers la Miff) n'est affiché que pour la **Miff du Feed** ; pour une autre Miff du carrousel, la carte n'affirme rien.
- **Historique propre à l'appareil** : il commence à cette version et est perdu si vous changez de téléphone.
- **Texte « nous analysons ta situation »** : wording figé par le PM ; il n'est affiché que quand le suivi tourne (sinon : voyage, partage arrêté, localisation).
- **Ne partez pas sur l'App Store avec cette fonctionnalité avant la mise à jour de la politique de confidentialité** (ticket §8).
