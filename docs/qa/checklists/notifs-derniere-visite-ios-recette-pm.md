# Recette iOS : « Depuis ta dernière visite » dans le centre de notifications (NOTIF-P1I) — parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5.2), build TestFlight de la PR `ios/notifs-derniere-visite`.
**Il vous faut** : **deux comptes dans la même Miff** : le vôtre (compte A, celui qui regarde) et un proche (compte B, sur un second téléphone ou un émulateur), idéalement un **troisième membre** (C) pour tester « au plus 3 lignes ». La Miff doit avoir **au moins un lieu de confiance avec une catégorie** (Maison, École, Travail…) et **un lieu Loisir ou Autre**.
**Durée** : environ 30 minutes (le silence demande d'attendre plus de 4 h : étape 9, à faire en dernier ou le lendemain). **Source** : décisions du PM du 2026-11-13 (`docs/tickets/notifications-p1-derniere-visite.md`, §11), critères C1-1 à C1-10 et vecteurs C1-T1 à C1-T20 (mêmes que la recette Android validée, `notifs-derniere-visite-android-recette-pm.md`), **pas de maquette**.

## À savoir avant de commencer

- **Ce que ça fait** : quand vous rouvrez l'app après une absence, elle compare ce que vous aviez vu de vos proches avec ce qu'ils sont maintenant, et range **au plus 3 lignes** dans le centre (la cloche), sous **« Depuis ta dernière visite »**. **Aucune notification** (ni bannière, ni son, ni vibration, ni pastille sur l'icône) : le **point de la cloche** est le seul signe.
- **Quand ça compare** : au **lancement de l'app** (fermée puis rouverte) ou au retour au premier plan après **30 minutes ou plus** (un passage par le centre de contrôle ou le sélecteur d'applications n'est pas une absence). Et **seulement quand le Feed affiche des informations fraîches** (réseau présent) : jamais sur une donnée périmée.
- **Aucune lecture ni écriture de plus sur le serveur, aucune permission de plus** : l'état « vu » est gardé **sur le téléphone**.
- Pour provoquer un changement chez B : **entrer / sortir d'un lieu**, ou **donner de ses nouvelles** (bouton Update). Pour attendre : **fermer complètement l'app** (balayer vers le haut dans le sélecteur d'applications), laisser B agir, puis rouvrir.
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.

## Partie A : le parcours de base (compte A)

1. [ ] **Première visite** : ouvrez le Feed (réseau présent). Ouvrez la **cloche** : **aucun** groupe « Depuis ta dernière visite ». Laissez l'app ouverte **sur le Feed** quelques secondes.
2. [ ] **Fermez complètement** l'app. Côté B : **arrivée** dans un lieu de catégorie École. Rouvrez l'app (réseau présent) : **un point** sur la cloche, **aucune notification**, aucun son, aucune vibration, **aucune pastille** sur l'icône.
3. [ ] Ouvrez la cloche : le groupe **« Depuis ta dernière visite »** est **tout en haut**, **au-dessus** d'« Aujourd'hui », avec **une ligne** du type **« <Prénom de B> a rejoint l'école à <heure> »**, puis la phrase **« Ces lignes résument ce qui a changé, pas tout ce qui s'est passé. »**. Ligne en gras avec un point (non lue) ; **pas de seconde ligne d'heure**.
4. [ ] **Touchez la ligne** : le centre se ferme, le Feed s'ouvre **et défile jusqu'à la carte de B**. Rouvrez la cloche : la ligne est **lue** (plus de point), toujours là.
5. [ ] **Départ** : fermer l'app, B **quitte** son lieu, rouvrir : **« <Prénom> a quitté l'école à <heure> »** ; l'ancienne ligne est **remplacée** (jamais plus de 3 lignes, jamais une ligne de plusieurs jours).
6. [ ] **Update** : B **donne de ses nouvelles** ; fermer, rouvrir : **« <Prénom> a donné de ses nouvelles à <heure> »**.
7. [ ] **Rien de neuf** : fermer et rouvrir sans que B ait rien fait : le groupe **disparaît** ; **ce que vous avez vu à l'écran ne vous est pas redit**.
8. [ ] **Lieu Loisir ou Autre** : B arrive dans un lieu **sans catégorie parmi les cinq** : **« a rejoint un lieu de confiance »** (jamais le nom du lieu).
9. [ ] **Silence** (à la fin) : B reste **plus de 4 h** sans nouvelles après avoir quitté un lieu (rouge dans le Feed). Fermer l'app **avant** ces 4 h puis la rouvrir **après** : **« Dernière nouvelle de <Prénom> : <heure> »** (« d'<Prénom> » devant une voyelle), sans durée ni mot d'alarme. Une nouvelle ouverture : la ligne **ne revient pas**. **Orange seulement** (2 h à 4 h) : **aucune** ligne « dernière nouvelle ».

## Partie B : seuil, réseau, fermeture forcée

10. [ ] **Moins de 30 minutes** en arrière-plan, B change pendant ce temps, retour : **aucune ligne**.
11. [ ] **30 minutes ou plus** en arrière-plan (sans fermer l'app) : au retour, la ligne apparaît (si B a changé).
12. [ ] **Pas de réseau** (mode avion), fermer et rouvrir : le Feed dit « Dernières informations de… », **aucune ligne** ; remettez le réseau : au retour d'informations fraîches, **la comparaison a lieu**.
13. [ ] **Fermeture forcée** (C1-T20) : regardez le Feed (B en lieu X), **balayez l'app** dans le sélecteur d'applications, B bouge, rouvrez : la ligne décrit **le changement depuis ce que vous aviez vu**, pas depuis plus loin.
14. [ ] **Au plus 3 lignes** : B, C et un 4e proche ont changé : **3 lignes**, la plus récente en haut, **sans** « +1 autre ».
15. [ ] **Jamais affiché** : proche **en mode voyage**, qui a **arrêté de partager**, **vous-même**, un **nouveau membre**, un proche en **zone de danger** : **aucune ligne**.
16. [ ] **Plusieurs Miffs** (Miff+) : changer de Miff dans le Feed : la première visite sur l'autre Miff **ne compare rien** ; toucher une ligne d'une autre Miff **bascule le Feed** sur cette Miff et amène la carte.

## Partie C : indépendance des notifications, suppression, purge

17. [ ] **Notifications du téléphone coupées** (Réglages iOS > Notifications > Miff) : le groupe **reste visible**, **au-dessus** du centre flou et de la carte « Activer les notifications » (le reste du centre reste flou : le groupe n'est jamais dessiné sous le flou). Quittez le centre : la ligne est lue. **Interrupteur général de l'app coupé** : le groupe reste visible.
18. [ ] **Supprimer une ligne** (balayage vers la gauche ou appui long), **« Tout marquer comme lu »**, **« Tout effacer »** : comme les autres lignes.
19. [ ] **Se déconnecter** puis se reconnecter : le groupe a disparu, la **première visite** suivante ne compare rien. **Quitter une Miff** : ses lignes disparaissent. **Un membre retiré** : sa ligne disparaît dès que le Feed le constate.

## Partie D : VoiceOver, texte, langues

20. [ ] **VoiceOver** : chaque ligne est lue **d'un bloc** « <phrase>, non lu » ; « Depuis ta dernière visite » est annoncé comme **un titre** ; la cible fait au moins 44 pt ; l'information n'est jamais portée par la couleur seule.
21. [ ] **Plus grand texte** (Réglages > Accessibilité > Taille du texte, accessibilité activée) : les phrases passent à la ligne, rien n'est coupé, la phrase de limite reste lisible. **Réduire les animations** : le défilement jusqu'à la carte est instantané.
22. [ ] **Anglais** : « Since your last visit », « Thomas arrived at school at 8:02 AM », « Thomas left school at 5:10 PM », « Amandine shared an update at 2:05 PM », « Amandine's latest news: 1:40 PM », « These lines sum up what changed, not everything that happened. ». Heure **au format du téléphone**. Jours : **hier** « hier à 22 h 15 » ; **2 à 6 jours** « mardi à 9 h 30 » ; **plus ancien** « le 3 nov. à 9 h 30 » (« yesterday at 10:15 PM », « on Tuesday at 9:30 AM », « on Nov 3 at 9:30 AM »).
23. [ ] **Ton** : aucun mot d'alarme, aucun point d'exclamation, aucune durée (« depuis 4 h »).

## Ce qui n'est volontairement pas fait (comme Android)

- **Aucun historique** : on voit **la différence**, pas le trajet (dit dans l'app).
- **Le Feed n'est pas harmonisé** : « Pas de nouvelles de… » dans la bulle rouge, « Dernière nouvelle de… » dans le centre (petit ticket Feed à part).
- **Pas de notification quand l'app est fermée** ; **pas de message pré-écrit** dans la ligne.
