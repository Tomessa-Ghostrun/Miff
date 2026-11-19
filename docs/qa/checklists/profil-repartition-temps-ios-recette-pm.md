# Recette iOS : « Répartition du temps » sur le Profil (TIME-3 et TIME-4) — parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5.2), build TestFlight de la PR `ios/profil-repartition-temps`.
**Il vous faut** : un compte avec **au moins une Miff** qui a **un lieu de confiance** (idéalement 3 lieux de catégories différentes) ; pour la zone de danger, une Miff dont le créateur est Miff+ avec une zone de danger. **« Toujours » + position précise** accordés à Miff.
**Durée** : quelques minutes par étape, mais **le graphique n'apparaît qu'après 7 jours réels de port du téléphone** : il n'y a plus d'outil de données de test (le graphique est validé : TestFlight 38). Pour repartir de zéro à tout moment : **Réglages > Confidentialité > « Effacer mon historique de temps »** (le décompte repart à 7 jours). **Source** : ticket `docs/tickets/profil-repartition-temps.md` (critères TS-1 à TS-25), vos retours des 2026-11-17 et 2026-11-18 ; mêmes cas de calcul qu'Android.

## À savoir avant de commencer

- **Ce que ça fait** : sous « Mes Miffs », la carte « Répartition du temps » montre **où vous avez passé votre temps** (vos lieux de confiance, une éventuelle zone de danger, « Hors-zone ») sur 7, 14 ou 30 jours. **Seulement votre temps**, jamais celui de vos proches. **Tout reste sur votre téléphone** : rien n'est envoyé, aucune lecture ni écriture sur le serveur, aucune permission de plus.
- **L'historique commence à l'installation de cette version, et le graphique n'apparaît qu'après 7 jours** : avant, la carte dit **« Ton graphique arrive dans X jours, nous analysons tes habitudes »** (X = 7 le premier jour, puis 6, 5… jusqu'à « 1 jour »), **sans graphique et sans sélecteur**. Le nombre est recalculé quand vous ouvrez le Profil ou rouvrez l'app, pas à la seconde.
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.

## Partie A : avant 7 jours

1. [ ] Ouvrez l'onglet **Profil**. La carte est **entre le carrousel « Mes Miffs » et la liste « Lieux de confiance de … »** ; la carte « Mode voyage » reste **en dernier**. Elle montre le titre **« Répartition du temps »** (**sans** « Sur les N derniers jours »), le texte exact **« Ton graphique arrive dans X jours, nous analysons tes habitudes »** et l'anneau vide gris ; **ni graphique, ni sélecteur**. Carte blanche très arrondie, pastille d'icône **verte** à gauche du titre.
2. [ ] Le lendemain (et chaque jour suivant), X **baisse d'un jour** ; la veille du 7e jour : **« dans 1 jour »** (singulier).
3. [ ] **Balayez le carrousel** vers une autre Miff : la carte suit **cette Miff** (son propre décompte), **sans clignotement** ; sur la carte « Créer une Miff », la carte garde la dernière Miff regardée.
4. [ ] **Miff sans lieu** : la carte **n'est pas dessinée** (pas même une marge) ; la section des lieux porte son message.
5. [ ] **Mode voyage** : activez-le (carte en bas du Profil) : la carte dit **« Tu es en mode voyage : ta répartition est en pause jusqu'à ton retour. »** (pas le texte « nous analysons tes habitudes »). Désactivez-le.
6. [ ] **Localisation** (seulement si l'app vous laisse sur le Profil : tant que « Toujours » est exigé, l'écran de blocage peut s'afficher à la place, c'est normal) : Réglages iOS > Miff > Localisation > **« Lors de l'utilisation »** : **« Pour mesurer ton temps dans tes lieux, Miff a besoin de la localisation « Toujours » avec la position précise. Tu peux aussi continuer sans. »** et le bouton **« Ouvrir les réglages »**. Remettez « Toujours ».

## Partie B : après 7 jours de port (le graphique)

7. [ ] La carte **passe du texte au graphique** : l'**anneau est plus petit, à gauche**, avec **toutes** les tranches (la 1re à midi, sens horaire) ; la **légende est à droite** (pastille, emoji de la catégorie comme dans la liste en dessous, nom, pourcentage **aligné à droite**), limitée à **4 lignes**. **Au centre** : un grand pourcentage et **« en zone de confiance »**. Le sélecteur **« 7 jours ⌄ »** est à droite du titre, **sans** sous-titre. En dessous, l'**encart vert très léger** avec la coche et **« Tu as passé x % de ton temps dans tes zones de confiance »**, le **🎉 à droite**.
8. [ ] **Couleurs** : les lieux de confiance sont des **nuances de vert et de bleu** (en alternance), la **zone de danger** est **exactement le rouge de la bulle de statut danger** du Feed (hachurée, avec le triangle), « **Hors-zone** » est le **gris clair de la maquette**. Le total des pourcentages de la légende **peut ne pas faire 100 %** (la légende n'affiche que 4 lignes) ; l'anneau, lui, est complet.
9. [ ] **Sélecteur** : touchez « 7 jours ⌄ » : un **menu à trois choix** (7, 14, 30 jours), le choix actuel **coché**. Choisissez 14 puis 30 : les pourcentages changent. **Fermez complètement l'app et rouvrez-la** : la fenêtre choisie est **gardée**.
10. [ ] **Plus de 5 lieux** (Miff+) : **5 lieux nommés**, puis **« 2 autres lieux »**, la zone de danger, « Hors-zone » dans l'anneau (la légende ne montre que 4 lignes).
11. [ ] **Zone de danger** : **une seule ligne « Zone de danger »** (ou « Zones de danger »), avec le **triangle**, la tranche **hachurée** et une **flèche**, **toujours présente dès que sa part est d'au moins 1 %** (elle remplace alors la 4e ligne si besoin) ; sous 1 %, **pas de ligne** dans la légende (la tranche reste dans l'anneau). Touchez la ligne : une feuille liste **un lieu par ligne** (emoji, nom, pourcentage) dont la **somme est celle de la ligne** ; fermez avec la **croix** puis en **tirant la feuille vers le bas**. Le pourcentage **au centre ne compte pas** la zone de danger. Aucune zone de danger : **aucune ligne**, jamais « 0 % ».
12. [ ] **Ton** : le texte de l'**encart vert** **ne nomme jamais** la zone de danger ; il est affiché chaque fois que le graphique l'est, y compris avec un très petit pourcentage (décision du PM).
13. [ ] **Avec la localisation insuffisante** : le graphique **reste** et une ligne calme dit **« Le suivi de tes lieux est en pause. »**.
14. [ ] **Cohérence avec le Feed** : le temps passé dans un lieu correspond aux arrivées et départs que vos proches ont vus ; un lieu supprimé par le créateur **n'est plus compté** (jamais en « Hors-zone »). La répartition est **indicative** (événements datés à leur arrivée dans l'app ; un téléphone éteint ou à plat compte « Hors-zone » jusqu'à 48 h au plus).
15. [ ] **Écriture téléphone verrouillé et app fermée** (TS-8) : l'app **fermée** (balayée), le téléphone **verrouillé**, entrez dans un de vos lieux puis sortez-en ; **aucun événement n'est perdu** (le passage se retrouve dans le graphique).

## Partie C : accessibilité, langues

16. [ ] **VoiceOver** : le focus sur l'anneau lit **une description unique** (« Répartition du temps : Maison 48 %, Sport 24 %, … en zone de confiance, 72 % ») sans lire les emoji ; la **zone de danger n'est dite qu'une fois** : par sa ligne (« Zone de danger, 12 %, afficher le détail ») quand la légende la montre, sinon dans la description ; le sélecteur est annoncé **« Période, 7 jours »** et le choix actuel **« choisi »** ; cibles de 44 pt au moins.
17. [ ] **Plus grand texte** (Réglages > Accessibilité > Affichage et taille du texte > Texte plus grand, tailles d'accessibilité, au maximum) : l'**anneau passe au-dessus de la légende**, rien n'est coupé ni superposé, le sélecteur passe sous le titre.
18. [ ] **Anglais** : « Time breakdown », « in trusted zones », « Outside zones », « Danger zone », « You spent 72% of your time in your trusted zones », « Your chart arrives in 3 days, we're analysing your habits » (« in 1 day »), pourcentage au format « 72% ».

## Partie D : effacement et vie privée

19. [ ] **Réglages > Confidentialité** : la section **« Historique de temps »** explique que **35 jours** sont gardés **sur le téléphone** et ne le quittent jamais, avec le bouton **« Effacer mon historique de temps »**.
20. [ ] Touchez-le : **« Effacer l'historique ? Ta répartition repartira de zéro. Cela ne change rien pour tes proches. »** (**Annuler** / **Effacer**). **Effacer** : un message de confirmation ; de retour sur le Profil, la carte dit de nouveau **« Ton graphique arrive dans 7 jours… »**. **Vos proches ne voient aucun changement.**
21. [ ] **Déconnexion puis reconnexion** (preuve TS-5) : la carte repart de **« dans 7 jours »** ; la fenêtre choisie est **revenue à 7 jours**.
22. [ ] **Quitter une Miff** puis la rejoindre : son historique est **reparti de zéro**. **Aucune demande de permission de plus** n'est apparue pendant toute la recette. Mode avion : **aucun effet** sur la carte.

## Ce qui est volontairement différent d'Android ou connu

- **Aperçu des applications récentes** : sur iOS, la carte peut apparaître dans le sélecteur d'applications (aucun masquage dans l'app, limite connue D17). Android 13+ le masque.
- **Partage arrêté** : le message « Tu as arrêté de partager ton statut… » (avec un bouton vers la Miff) n'est affiché que pour la **Miff du Feed** ; pour une autre Miff du carrousel, la carte n'affirme rien.
- **Historique propre à l'appareil** : il commence à cette version et est perdu si vous changez de téléphone.
- **Texte « nous analysons tes habitudes »** : wording figé par le PM, **risque assumé** (il peut laisser croire à une analyse côté serveur alors que tout reste sur le téléphone) ; il n'est affiché que quand le suivi tourne (sinon : voyage, partage arrêté, localisation).
- **Ne partez pas sur l'App Store avec cette fonctionnalité avant la mise à jour de la politique de confidentialité** (ticket §8).
