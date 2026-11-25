# Recette Android : « Répartition de ton temps » sur le Profil (TIME-1 et TIME-2) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), avec **un compte dans une Miff qui a au moins deux lieux de confiance** (idéalement trois, dont un de catégorie Sport ; et, **si votre compte est Miff+ et créateur**, un lieu de type « Zone de danger »).
**Durée** : environ 30 minutes pour l'interface (parties A à F) ; la **partie G (vrai suivi) demande au moins 24 h** de téléphone sur vous.
**Spec** : `docs/tickets/profil-repartition-temps.md` (TIME-1 : journal local et calcul ; TIME-2 : carte du Profil). Cette recette ne couvre **pas** l'iPhone (TIME-3 et TIME-4).

## À savoir avant de commencer

- **Ce que c'est** : sous « Mes Miffs », une carte **« Répartition du temps »** (un petit anneau à gauche, la légende à droite avec les 4 plus grandes parts, un encart vert) montre **votre** temps entre les lieux de la Miff affichée, ses éventuelles zones de danger et « Hors-zone », sur 7, 14 ou 30 jours. **Tout est calculé sur le téléphone : rien n'est envoyé, aucune lecture ni écriture de plus sur le serveur, aucun événement de mesure d'usage.**
- **L'historique démarre dès que la détection de vos lieux est armée pour la Miff (dès l'ouverture de l'app avec la localisation « Toujours »), et le graphique n'apparaît qu'après 3 jours d'historique** (3 jours écoulés depuis ce début ; décision du PM du 2026-11-25, avant : 7 jours). Un historique déjà mesuré compte tout de suite : s'il remonte déjà à 3 jours ou plus, le graphique s'affiche dès la mise à jour. Avant, **ni graphique ni sélecteur** : seulement « **Ton graphique arrive dans X jours, nous analysons tes habitudes** » (X = jours restants, de 3 à 1 ; « dans 1 jour » au singulier ; le texte que vous avez figé, avec son risque assumé : le journal reste sur le téléphone).
- **Pas de données de test** : le graphique ne s'affiche qu'après **3 jours de port réel** du téléphone ; avant, vous vérifiez l'état « dans X jours » (X baisse d'un jour par jour, **même si vous n'avez ni quitté ni rejoint un lieu**). Les cas de calcul sont couverts par les tests automatiques ; la partie G est la recette de la vraie donnée. Pour repartir de zéro : Réglages > Confidentialité > « Effacer mon historique de temps ».
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.

## Partie A : la carte et son placement

1. [ ] Ouvrez l'onglet **Profil**. Sans données, la carte est **entre le carrousel « Mes Miffs » et « Les lieux de la Miff »** : pastille verte, titre **« Répartition du temps »**, puis **« Ton graphique arrive dans 3 jours, nous analysons tes habitudes »** (3 jours juste après un effacement de l'historique). **Pas d'anneau, pas de légende, pas de sélecteur, pas de sous-titre.** La carte **« Mode voyage » reste en dernier**.
2. [ ] **Les jours restants** : tant que le suivi a moins de 3 jours, la carte dit **« Ton graphique arrive dans X jours, nous analysons tes habitudes »** (X de 3 à 1 ; « dans 1 jour » au singulier), **sans graphique ni sélecteur** ; **X baisse d'un jour par jour (3, puis 2, puis 1), même sans arrivée ni départ**. **Au 4e jour environ**, le graphique apparaît **sans clignoter** (anneau à gauche, légende à droite, encart vert en dessous).
3. [ ] **Comparez avec votre maquette** (`19.webp`) : carte blanche aux grands angles, **pastille verte** à gauche du titre, titre « Répartition du temps » (**pas de sous-titre**), **sélecteur « 7 jours ⌄ » à droite**, **petit anneau à gauche** avec **« 72 % » en gros et « en zone de confiance »**, **légende à droite** (pastille de couleur, emoji, nom, **pourcentage aligné à droite**, **4 lignes au plus**), encart vert en bas. **Aucune mention « Ces données restent sur ton téléphone »** (retirée à votre demande ; le journal reste local). **Le total des 4 lignes n'est pas toujours 100 %** s'il y a plus de 4 parts (l'anneau, lui, montre tout). **Les couleurs** : lieux de confiance en **verts et bleus Miff**, zone de danger = **le rouge de la bulle de statut danger** (hachuré, icône d'avertissement), « Hors-zone » = **le gris de la maquette**. **Écarts** : listés dans la PR.
4. [ ] **L'emoji de chaque ligne est le même que celui du lieu dans la liste « Les lieux de la Miff » juste en dessous** (⚽ pour le sport, 🏠 pour la maison…). Un lieu sans catégorie a la **petite épingle**.
5. [ ] **L'encart vert** sous le graphique : coche à gauche, **« Tu as passé XX % de ton temps dans tes zones de confiance »** (le pourcentage en gras, le même que celui du centre) et **🎉** à droite. Il est **identique quelle que soit la valeur** (décision du PM ; **risque de ton assumé** si le pourcentage est très bas). **Jamais** un mot sur la zone de danger.
6. [ ] **Plusieurs Miffs** (compte Miff+) : balayez le carrousel : la carte **suit la Miff affichée**, garde la période choisie, **sans clignoter ni état vide** entre deux. Sur la carte « Créer une nouvelle Miff », la carte reste celle de la dernière Miff affichée. Une Miff **sans lieu** : **pas de carte** (la section des lieux porte déjà son message).

## Partie B : la période

7. [ ] Appuyez sur **« 7 jours »** : un menu propose **7, 14, 30 jours**, le choix actuel est **coché**. Choisissez **14 jours** : le sous-titre devient « Sur les 14 derniers jours », la phrase aussi, l'anneau est recalculé.
8. [ ] **Fermez complètement l'app et rouvrez-la** : la période choisie est **conservée**. **Déconnectez-vous puis reconnectez-vous** : la période revient à **7 jours** (et la carte repart de « pas assez de données », voir la partie D).

## Partie C : la zone de danger (si vous avez un lieu de ce type)

9. [ ] Avec du temps passé dans une zone de danger : **une seule ligne « Zone de danger »** (« Zones de danger » s'il y en a plusieurs), avec une **icône d'avertissement** et son pourcentage, et la **tranche est hachurée** (jamais la couleur seule). La couleur d'alerte n'apparaît **que** sur cette tranche et cette ligne. **Avec plus de 4 parts**, la légende garde 4 lignes : les plus grosses, mais la zone de danger y est quand même si elle fait 1 % ou plus (le 4e lieu lui cède sa place).
10. [ ] **Touchez cette ligne** : une **feuille du bas** liste chaque lieu (emoji, nom, pourcentage) ; **la somme égale la ligne**. Fermez avec **« Fermer »** puis rouvrez et fermez avec le **geste « retour »** du téléphone : les deux marchent. **La ligne « Zone de danger » est toujours dans la légende dès que sa part arrondie est de 1 % ou plus** (elle prend la place du 4e lieu si besoin) ; en dessous de 1 %, pas de ligne.
11. [ ] **Sans temps passé** dans une zone de danger (ou sans zone de danger) : **aucune ligne**, jamais « 0 % ».

## Partie D : les états

12. [ ] **Pas assez de données** : « Effacer mon historique de temps » (Réglages) → **« Ton graphique arrive dans 3 jours, nous analysons tes habitudes »**, **sans anneau ni sélecteur** et **sans aucune couleur d'alerte**.
13. [ ] **Mode voyage** : activez le mode voyage (carte en bas du Profil), puis effacez l'historique (Réglages) : la carte dit **« Tu es en mode voyage : ta répartition est en pause jusqu'à ton retour. »** (sans bouton). Avec des données de test **déjà remplies avant** le voyage : le graphique reste affiché avec **« Le suivi de tes lieux est en pause. »** sous l'anneau. Désactivez le voyage.
14. [ ] **Partage arrêté** : dans l'écran de la Miff, arrêtez votre partage ; revenez sur le Profil, historique vide : **« Tu as arrêté de partager ton statut avec cette Miff : ta répartition n'est plus mise à jour pour elle. »** avec le bouton **« Voir cette Miff »** (il ouvre l'écran de la Miff, où vous pouvez reprendre). Reprenez le partage.
15. [ ] **Localisation insuffisante** : dans les réglages du téléphone, passez la localisation de Miff à « Seulement pendant l'utilisation », historique vide : **« Pour mesurer ton temps dans tes lieux, Miff a besoin de la localisation « Toujours » avec la position précise. Tu peux aussi continuer sans. »** et le bouton **« Toujours autoriser »** (même étape que dans Réglages > Confidentialité, **sans insister**). Remettez « Toujours ».
16. [ ] **Hors connexion** (mode avion) : la carte est **identique** (aucune dépendance au réseau).

## Partie E : effacement (réglages et sortie de compte)

17. [ ] **Réglages > Confidentialité** : une section **« Historique de temps »** explique ce que le téléphone garde (35 jours, sans position ni adresse, rien n'est envoyé) et propose **« Effacer mon historique de temps »**. Touchez-le : confirmation **« Effacer l'historique ? Ta répartition repartira de zéro. Cela ne change rien pour tes proches. »** (« Effacer » / « Annuler »). Confirmez : **« C'est fait : ta répartition repart de zéro. »** ; sur le Profil, **« pas assez de données »**. Ouvrez la Miff d'un proche : **rien n'a changé pour lui**.
18. [ ] **Se déconnecter, relancer l'app, se reconnecter** (preuve de l'effacement à la déconnexion) : la carte repart de **« pas assez de données »**, période à 7 jours.
19. [ ] **Quitter une Miff** (compte avec deux Miffs) : l'historique de cette Miff est effacé (si vous la rejoignez de nouveau, la carte repart de zéro).

## Partie F : lecteur d'écran, texte, langue

20. [ ] **TalkBack** : le titre est un **titre** ; le sélecteur est annoncé **« Période, 7 jours, bouton »** ; l'anneau **n'est pas lu** ; **une phrase unique** décrit le graphique (« Répartition du temps : Maison 48 %, Sport 24 %, Zone de danger 12 %, Hors-zone 16 %. ») ; la ligne « Zone de danger » est un **bouton** (« …, afficher le détail ») ; **les emoji ne sont pas lus** ; les cibles font 48 dp.
21. [ ] **Taille de police maximale** : **l'anneau passe au-dessus de la légende**, le pourcentage reste au centre et « en zone de confiance » passe **sous** l'anneau ; **rien n'est coupé ni superposé**, y compris dans les états de la partie D.
22. [ ] **Anglais** (langue du téléphone) : « Time breakdown », « Outside zones », « Danger zone », « in trusted zones », « You spent 72% of your time in your trusted zones », « Your chart arrives in 7 days, we're analysing your habits » (« in 1 day » au singulier), « Erase my time history ». Pourcentages à l'anglaise (« 72% »).
23. [ ] **Aperçu des applications récentes** (Android 13) : sur l'onglet Profil, avec la carte affichée, l'aperçu reste **masqué** (comme avant).

## Partie G : le vrai suivi (au moins 24 h)

24. [ ] **Avec le téléphone sur vous**, l'APK installé, « Toujours » + position précise, partage actif : passez **plusieurs fois** dans vos lieux (entrée, sortie) pendant une journée, **verrouillé et app fermée** (balayée de la liste des applications récentes). Pendant les 3 premiers jours la carte compte **« …dans X jours… »** (3, puis 2, puis 1 : X diminue d'un jour par jour, jamais sous 1, **même les jours où vous n'avez ni quitté ni rejoint un lieu**) ; **au 4e jour environ, sans utiliser le bloc de test**, la carte affiche un graphique plausible. **Point à surveiller sur l'OPPO** : les surcouches qui ferment les applications ou retardent les événements de lieu : une entrée ou une sortie **manquée ou tardive** fausse le graphique (limite connue, indiquée par l'app : pourcentages entiers, jamais de durée précise ; un état n'est jamais compté plus de 48 h sans nouvel événement).
25. [ ] Comparez ce que dit le **Feed** (arrivées / départs, heures) avec la carte : **les deux utilisent les mêmes instants** (le graphique ne peut pas contredire le Feed).

## Ce qui est volontairement ainsi (à confirmer)

- **Seulement votre temps**, jamais celui des proches. Pas de comparaison, d'objectif ni de félicitation.
- **Historique propre au téléphone** : changement de téléphone ou réinstallation = repart de zéro. 35 jours gardés. **Si vous voulez qu'il survive à un changement de téléphone ou voir celui des proches, c'est une autre décision** (journal sur le serveur, nouveau consentement : voir le ticket §18).
- **Politique de confidentialité à mettre à jour avant toute publication sur les stores** : elle dit encore « pas d'historique de trajets » alors que le **téléphone** garde 35 jours de changements de lieu (rien ne quitte l'appareil). Voir le ticket §8 : **cette version ne doit pas partir sur Google Play avant cette mise à jour** ; l'APK interne peut la recevoir.
- La **période d'un voyage** n'est pas comptée (ni dans un lieu, ni dans « Hors-zone ») ; le temps où le suivi est coupé non plus.
- **Plus de 20 zones** (Miff+ avec beaucoup de lieux) : le système n'en surveille que 20 à la fois ; la carte l'indique sous l'anneau.
- **iOS** : TIME-3 et TIME-4, à suivre.
