# Recette Android : « Répartition de ton temps » sur le Profil (TIME-1 et TIME-2) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), avec **un compte dans une Miff qui a au moins deux lieux de confiance** (idéalement trois, dont un de catégorie Sport ; et, **si votre compte est Miff+ et créateur**, un lieu de type « Zone de danger »).
**Durée** : environ 30 minutes pour l'interface (parties A à F) ; la **partie G (vrai suivi) demande au moins 24 h** de téléphone sur vous.
**Spec** : `docs/tickets/profil-repartition-temps.md` (TIME-1 : journal local et calcul ; TIME-2 : carte du Profil). Cette recette ne couvre **pas** l'iPhone (TIME-3 et TIME-4).

## À savoir avant de commencer

- **Ce que c'est** : sous « Mes Miffs », une carte **« Répartition de ton temps »** (un anneau, une légende, une phrase) montre **votre** temps entre les lieux de la Miff affichée, ses éventuelles zones de danger et « Ailleurs », sur 7, 14 ou 30 jours. **Tout est calculé sur le téléphone : rien n'est envoyé, aucune lecture ni écriture de plus sur le serveur, aucun événement de mesure d'usage.**
- **L'historique démarre à l'installation de cette version** : sans données, la carte dit « Ton graphique arrive dans quelques jours, nous analysons ta situation » (le texte que vous avez figé). Il faut **24 h de temps mesuré** pour voir un graphique.
- **Pour tester sans attendre (APK de debug seulement)** : installez l'**APK de debug signé par la CI** (artefact `miff-debug-apk` de l'exécution Android de la PR : `gh run download <identifiant> -n miff-debug-apk`). Sous la carte, **un bloc « Données de test (builds de test seulement) »** propose **« Remplir avec des données de test »** (trois semaines de routine inventée sur les lieux de la Miff affichée : nuits au premier lieu, jours au deuxième, quelques soirées au troisième, une courte visite à la zone de danger s'il y en a une) et **« Vider l'historique de cette Miff »**. **Ce bloc n'existe pas dans la version du store** (preuve automatique à chaque PR) ; il n'écrit rien sur le serveur et ne lit aucune position.
- Si vous installez l'APK `releaseLike` à la place, **le bloc de test est absent** : il faudra alors un vrai suivi (partie G).
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.

## Partie A : la carte et son placement

1. [ ] Ouvrez l'onglet **Profil**. Sans données, la carte est **entre le carrousel « Mes Miffs » et « Lieux de confiance de … »**, avec le titre **« Répartition de ton temps »**, « Sur les 7 derniers jours », la pastille **« 7 jours »**, un anneau gris vide et **« Ton graphique arrive dans quelques jours, nous analysons ta situation »**, puis **« Ces données restent sur ton téléphone. Personne d'autre ne peut les voir. »**. La carte **« Mode voyage » reste en dernier**.
2. [ ] Appuyez sur **« Remplir avec des données de test »** : l'anneau se dessine **sans clignoter** : une tranche par lieu (le plus long d'abord), éventuellement **« Zone de danger »**, puis **« Ailleurs »** en dernier ; **le pourcentage au centre** est la somme des lieux de confiance seulement (« 72 % » dans votre maquette, ici la vôtre) ; **les pourcentages de la légende font exactement 100 %**.
3. [ ] **Comparez avec votre maquette** (`19.webp`) : carte blanche arrondie, pastille d'icône à gauche du titre, sélecteur en pastille bordée avec chevron, anneau avec pourcentage au centre, légende à droite (pastille de couleur, emoji, nom, pourcentage), bandeau de phrase en bas. **Les écarts voulus sont listés dans la PR** (couleurs bleues au lieu de vert/jaune/rouge, « Zone de danger » au lieu de « Zone à risque », pas de coche ni de fête, « Ailleurs » au lieu de « Hors-zone »).
4. [ ] **L'emoji de chaque ligne est le même que celui du lieu dans la liste « Lieux de confiance de … » juste en dessous** (⚽ pour le sport, 🏠 pour la maison…). Un lieu sans catégorie a la **petite épingle**.
5. [ ] La **phrase** du bandeau est descriptive : **« Sur les 7 derniers jours, c'est à <lieu> que tu as passé le plus de temps. »** (ou « …tu as surtout été ailleurs que dans tes lieux de confiance. », ou « Voici comment ton temps s'est réparti sur les 7 derniers jours. » quand la plus grande part est « Zone de danger » ou « autres lieux »). **Jamais** un mot sur la zone de danger, un pourcentage, une félicitation ou un reproche.
6. [ ] **Plusieurs Miffs** (compte Miff+) : balayez le carrousel : la carte **suit la Miff affichée**, garde la période choisie, **sans clignoter ni état vide** entre deux. Sur la carte « Créer une nouvelle Miff », la carte reste celle de la dernière Miff affichée. Une Miff **sans lieu** : **pas de carte** (la section des lieux porte déjà son message).

## Partie B : la période

7. [ ] Appuyez sur **« 7 jours »** : un menu propose **7, 14, 30 jours**, le choix actuel est **coché**. Choisissez **14 jours** : le sous-titre devient « Sur les 14 derniers jours », la phrase aussi, l'anneau est recalculé.
8. [ ] **Fermez complètement l'app et rouvrez-la** : la période choisie est **conservée**. **Déconnectez-vous puis reconnectez-vous** : la période revient à **7 jours** (et la carte repart de « pas assez de données », voir la partie D).

## Partie C : la zone de danger (si vous avez un lieu de ce type)

9. [ ] Avec du temps passé dans une zone de danger : **une seule ligne « Zone de danger »** (« Zones de danger » s'il y en a plusieurs), avec une **icône d'avertissement** et son pourcentage, et la **tranche est hachurée** (jamais la couleur seule). La couleur d'alerte n'apparaît **que** sur cette tranche et cette ligne.
10. [ ] **Touchez cette ligne** : une **feuille du bas** liste chaque lieu (emoji, nom, pourcentage) ; **la somme égale la ligne**. Fermez avec **« Fermer »** puis rouvrez et fermez avec le **geste « retour »** du téléphone : les deux marchent.
11. [ ] **Sans temps passé** dans une zone de danger (ou sans zone de danger) : **aucune ligne**, jamais « 0 % ».

## Partie D : les états

12. [ ] **Pas assez de données** : « Vider l'historique de cette Miff » → l'anneau gris vide et le **texte que vous avez figé**, **sans aucune couleur d'alerte**.
13. [ ] **Mode voyage** : activez le mode voyage (carte en bas du Profil), puis videz l'historique : la carte dit **« Tu es en mode voyage : ta répartition est en pause jusqu'à ton retour. »** (sans bouton). Avec des données de test **déjà remplies avant** le voyage : le graphique reste affiché avec **« Le suivi de tes lieux est en pause. »** sous l'anneau. Désactivez le voyage.
14. [ ] **Partage arrêté** : dans l'écran de la Miff, arrêtez votre partage ; revenez sur le Profil, historique vide : **« Tu as arrêté de partager ton statut avec cette Miff : ta répartition n'est plus mise à jour pour elle. »** avec le bouton **« Voir cette Miff »** (il ouvre l'écran de la Miff, où vous pouvez reprendre). Reprenez le partage.
15. [ ] **Localisation insuffisante** : dans les réglages du téléphone, passez la localisation de Miff à « Seulement pendant l'utilisation », historique vide : **« Pour mesurer ton temps dans tes lieux, Miff a besoin de la localisation « Toujours » avec la position précise. Tu peux aussi continuer sans. »** et le bouton **« Toujours autoriser »** (même étape que dans Réglages > Confidentialité, **sans insister**). Remettez « Toujours ».
16. [ ] **Hors connexion** (mode avion) : la carte est **identique** (aucune dépendance au réseau).

## Partie E : effacement (réglages et sortie de compte)

17. [ ] **Réglages > Confidentialité** : une section **« Historique de temps »** explique ce que le téléphone garde (35 jours, sans position ni adresse, rien n'est envoyé) et propose **« Effacer mon historique de temps »**. Touchez-le : confirmation **« Effacer l'historique ? Ta répartition repartira de zéro. Cela ne change rien pour tes proches. »** (« Effacer » / « Annuler »). Confirmez : **« C'est fait : ta répartition repart de zéro. »** ; sur le Profil, **« pas assez de données »**. Ouvrez la Miff d'un proche : **rien n'a changé pour lui**.
18. [ ] **Se déconnecter, relancer l'app, se reconnecter** (preuve de l'effacement à la déconnexion) : la carte repart de **« pas assez de données »**, période à 7 jours.
19. [ ] **Quitter une Miff** (compte avec deux Miffs) : l'historique de cette Miff est effacé (si vous la rejoignez de nouveau, la carte repart de zéro).

## Partie F : lecteur d'écran, texte, langue

20. [ ] **TalkBack** : le titre est un **titre** ; le sélecteur est annoncé **« Période, 7 jours, bouton »** ; l'anneau **n'est pas lu** ; **une phrase unique** décrit le graphique (« Répartition de ton temps. Sur les 7 derniers jours : Maison 48 %, Sport 24 %, Zone de danger 12 %, Ailleurs 16 %. ») ; la ligne « Zone de danger » est un **bouton** (« …, afficher le détail ») ; **les emoji ne sont pas lus** ; les cibles font 48 dp.
21. [ ] **Taille de police maximale** : **la légende passe sous l'anneau**, le pourcentage reste au centre et sa légende passe **sous** l'anneau ; **rien n'est coupé ni superposé**, y compris dans les états de la partie D.
22. [ ] **Anglais** (langue du téléphone) : « How your time is spread », « Over the last 7 days », « Elsewhere », « Danger zone », « Your chart will be ready in a few days, we are looking into your situation », « This data stays on your phone. Nobody else can see it. », « Erase my time history ». Pourcentages à l'anglaise (« 72% »).
23. [ ] **Aperçu des applications récentes** (Android 13) : sur l'onglet Profil, avec la carte affichée, l'aperçu reste **masqué** (comme avant).

## Partie G : le vrai suivi (au moins 24 h)

24. [ ] **Avec le téléphone sur vous**, l'APK installé, « Toujours » + position précise, partage actif : passez **plusieurs fois** dans vos lieux (entrée, sortie) pendant une journée, **verrouillé et app fermée** (balayée de la liste des applications récentes). Le lendemain, **sans utiliser le bloc de test** : la carte affiche un graphique plausible. **Point à surveiller sur l'OPPO** : les surcouches qui ferment les applications ou retardent les événements de lieu : une entrée ou une sortie **manquée ou tardive** fausse le graphique (limite connue, indiquée par l'app : pourcentages entiers, jamais de durée précise ; un état n'est jamais compté plus de 48 h sans nouvel événement).
25. [ ] Comparez ce que dit le **Feed** (arrivées / départs, heures) avec la carte : **les deux utilisent les mêmes instants** (le graphique ne peut pas contredire le Feed).

## Ce qui est volontairement ainsi (à confirmer)

- **Seulement votre temps**, jamais celui des proches. Pas de comparaison, d'objectif ni de félicitation.
- **Historique propre au téléphone** : changement de téléphone ou réinstallation = repart de zéro. 35 jours gardés. **Si vous voulez qu'il survive à un changement de téléphone ou voir celui des proches, c'est une autre décision** (journal sur le serveur, nouveau consentement : voir le ticket §18).
- **Politique de confidentialité à mettre à jour avant toute publication sur les stores** : elle dit encore « pas d'historique de trajets » alors que le **téléphone** garde 35 jours de changements de lieu (rien ne quitte l'appareil). Voir le ticket §8 : **cette version ne doit pas partir sur Google Play avant cette mise à jour** ; l'APK interne peut la recevoir.
- La **période d'un voyage** n'est pas comptée (ni dans un lieu, ni dans « Ailleurs ») ; le temps où le suivi est coupé non plus.
- **Plus de 20 zones** (Miff+ avec beaucoup de lieux) : le système n'en surveille que 20 à la fois ; la carte l'indique sous l'anneau.
- **iOS** : TIME-3 et TIME-4, à suivre.
