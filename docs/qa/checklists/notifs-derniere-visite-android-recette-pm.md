# Recette Android : « Depuis ta dernière visite » dans le centre de notifications (NOTIF-P1A) — parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13).
**Il vous faut** : **deux comptes dans la même Miff** : le vôtre (compte A, celui qui regarde) et un proche (compte B, sur un second téléphone ou un émulateur), idéalement un **troisième membre** (C) pour tester « au plus 3 lignes ». La Miff doit avoir **au moins un lieu de confiance avec une catégorie** (Maison, École, Travail…) et **un lieu Loisir ou Autre**.
**Durée** : environ 30 minutes (le silence demande d'attendre plus de 4 h : voir l'étape 9, à faire en dernier ou le lendemain). **Source** : décisions du PM du 2026-11-13 (`docs/tickets/notifications-p1-derniere-visite.md`, §11), critères C1-1 à C1-10 et vecteurs C1-T1 à C1-T20, **pas de maquette** (même ligne que les autres éléments du centre, plus un titre de groupe).

## À savoir avant de commencer

- **Ce que ça fait** : quand vous rouvrez l'app après une absence, elle compare ce que vous aviez vu de vos proches avec ce qu'ils sont maintenant, et range **au plus 3 lignes** dans le centre (la cloche), sous **« Depuis ta dernière visite »**. **Aucune notification** (ni bannière, ni son, ni vibration, ni pastille sur l'icône) : le **point de la cloche** est le seul signe.
- **Quand ça compare** : au **lancement de l'app** (fermée puis rouverte) ou au retour au premier plan après **30 minutes ou plus**. Pas en dessous. Et **seulement quand le Feed affiche des informations fraîches** (réseau présent) : jamais sur une donnée périmée.
- **Aucune lecture ni écriture de plus sur le serveur** : l'état « vu » est gardé **sur le téléphone**.
- Pour provoquer un changement chez le proche (B), vous pouvez : **entrer / sortir d'un lieu** (ou, sur un build de test, faire changer son statut), ou lui faire **donner de ses nouvelles** (bouton Update). Pour attendre : **fermer complètement l'app** (balayage dans la liste des applications récentes), laisser B agir, puis rouvrir.
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.

## Partie A : le parcours de base (compte A)

1. [ ] **Première visite** : installez l'APK et ouvrez le Feed (réseau présent). Ouvrez la **cloche** : **aucun** groupe « Depuis ta dernière visite » (rien à comparer la première fois). Laissez l'app ouverte **sur le Feed** quelques secondes (l'état est alors enregistré).
2. [ ] **Fermez complètement** l'app. Côté B : **arrivée** dans un lieu de catégorie École (ou le lieu de votre choix avec une catégorie). Rouvrez l'app (compte A, réseau présent) : sur le Feed, **un point** apparaît sur la cloche, **aucune notification**, aucun son, aucune vibration, **aucune pastille** sur l'icône de l'app.
3. [ ] Ouvrez la cloche : le groupe **« Depuis ta dernière visite »** est **tout en haut**, **au-dessus** d'« Aujourd'hui », avec **une ligne** du type **« <Prénom de B> a rejoint l'école à <heure> »** (jamais « est arrivé » : pas d'accord de genre), puis la phrase discrète **« Ces lignes résument ce qui a changé, pas tout ce qui s'est passé. »**. La ligne est en gras avec un petit point (non lue) ; **pas de seconde ligne d'heure**.
4. [ ] **Touchez la ligne** : le centre se ferme, le Feed s'ouvre **et défile jusqu'à la carte de B**. Rouvrez la cloche : la ligne est **lue** (plus de point), toujours là.
5. [ ] **Départ** : refaites le tour (fermer l'app, B **quitte** son lieu, rouvrir) : la ligne devient **« <Prénom> a quitté l'école à <heure> »** ; l'ancienne ligne a été **remplacée** (il n'y a jamais plus de 3 lignes, jamais une ligne de plusieurs jours).
6. [ ] **Update** : B **donne de ses nouvelles** (bouton Update) ; fermer, rouvrir : **« <Prénom> a donné de ses nouvelles à <heure> »**.
7. [ ] **Rien de neuf** : fermez et rouvrez l'app sans que B ait rien fait : le groupe **disparaît** (« Rien à signaler » si le centre est vide) ; **ce que vous avez vu à l'écran ne vous est pas redit**.
8. [ ] **Lieu Loisir ou Autre** : B arrive dans un lieu **sans catégorie parmi les cinq** : la ligne dit **« a rejoint un lieu de confiance »** (jamais le nom du lieu).
9. [ ] **Silence** (à faire à la fin) : B reste **plus de 4 h** sans donner de nouvelles après avoir quitté un lieu (le Feed le montre en rouge, « Pas de nouvelles depuis… »). Fermer l'app **avant** ces 4 h puis la rouvrir **après** : la ligne dit **« Dernière nouvelle de <Prénom> : <heure> »** (« d'<Prénom> » si le prénom commence par une voyelle) : **un fait et une heure**, sans durée ni mot d'alarme. Refaites une ouverture : la ligne **ne revient pas** (une seule mention, au moment où ça arrive). **Orange seulement** (entre 2 h et 4 h) : **aucune** ligne « dernière nouvelle ».

## Partie B : les règles de silence et de seuil

10. [ ] **Moins de 30 minutes** : l'app en arrière-plan **moins de 30 minutes**, B change pendant ce temps, retour au premier plan : **aucune ligne**. (Ce que vous voyez sur le Feed est à jour, et l'état vu suit l'écran.)
11. [ ] **30 minutes ou plus** en arrière-plan (sans fermer l'app) : au retour, la ligne apparaît (si B a changé).
12. [ ] **Pas de réseau** : coupez le réseau (mode avion), fermez et rouvrez l'app : le Feed dit « Dernières informations de… », **aucune ligne n'est créée** ; remettez le réseau : au retour d'informations fraîches, **la comparaison a lieu**.
13. [ ] **Fermeture forcée** (critère C1-T20, **à vérifier sur l'OPPO** qui ferme volontiers les applications) : regardez le Feed (B en lieu X), **fermez l'app de force** (paramètres de l'app > Forcer l'arrêt, ou balayage), B bouge, rouvrez : la ligne décrit **le changement depuis ce que vous aviez vu**, pas depuis plus loin.
14. [ ] **Au plus 3 lignes** : avec B, C (et un 4e proche si possible) qui ont tous changé : **3 lignes**, la plus récente en haut, **sans** « +1 autre ». Le Feed montre les autres.
15. [ ] **Jamais affiché** : un proche **en mode voyage**, un proche qui a **arrêté de partager**, **vous-même** (votre propre carte), un **nouveau membre** : **aucune ligne** pour eux. Un proche qui entre dans une **zone de danger** : **aucune ligne** (le Feed et la notification existante s'en chargent).
16. [ ] **Plusieurs Miffs** (compte Miff+) : changez de Miff dans le Feed : la première visite sur l'autre Miff **ne compare rien** ; chaque Miff garde son état. Les lignes d'une Miff portent le nom de la Miff en petit quand le compte en a plusieurs ; toucher une ligne d'une autre Miff **bascule le Feed** sur cette Miff et amène la carte.

## Partie C : indépendance des notifications, suppression, purge

17. [ ] **Notifications du téléphone coupées** (Paramètres Android > Applications > Miff > Notifications : désactivées) : le groupe **reste visible** dans le centre au-dessus de la carte « Activer les notifications » (le reste du centre reste flouté). **Interrupteur général de l'app coupé** (Réglages > Notifications) : le bandeau « Les notifications sont coupées » est inchangé et le groupe reste visible.
18. [ ] **Supprimer une ligne** (appui long ou action « Supprimer cette notification » de TalkBack), **« Tout marquer comme lu »**, **« Tout effacer »** : fonctionnent comme sur les autres lignes.
19. [ ] **Se déconnecter** puis se reconnecter : le groupe a disparu, et la **première visite** suivante ne compare rien. **Quitter une Miff** : ses lignes disparaissent. **Un membre retiré de la Miff** : sa ligne disparaît dès que le Feed le constate.

## Partie D : lecteur d'écran, texte, langues

20. [ ] **TalkBack** : chaque ligne est lue **d'un bloc**, avec « non lu » tant qu'elle ne l'est pas (« <phrase>, non lu ») ; « Depuis ta dernière visite » est annoncé comme **un titre** ; la ligne a une action « Supprimer cette notification » ; la cible fait au moins 48 dp. L'information n'est jamais portée par la couleur seule (gras **et** point).
21. [ ] **Grand texte** (taille de police maximale du système) : les phrases passent à la ligne, rien n'est coupé, la phrase de limite reste lisible. **Animations réduites** : le défilement jusqu'à la carte est instantané.
22. [ ] **Anglais** (langue du téléphone) : « Since your last visit », « Thomas arrived at school at 8:02 AM », « Thomas left school at 5:10 PM », « Amandine shared an update at 2:05 PM », « Amandine's latest news: 1:40 PM », « These lines sum up what changed, not everything that happened. ». Heure **au format du téléphone** (24 h ou 12 h). Les jours, en français : **hier** : « hier à 22 h 15 » ; **2 à 6 jours** : « mardi à 9 h 30 » ; **plus ancien** : « le 3 nov. à 9 h 30 » (en anglais : « yesterday at 10:15 PM », « on Tuesday at 9:30 AM », « on Nov 3 at 9:30 AM »).
23. [ ] **Ton** : aucun mot d'alarme (« urgent », « inquiétant », « toujours pas »), aucun point d'exclamation, aucune durée (« depuis 4 h »).

## Ce qui n'est volontairement pas fait (à confirmer)

- **Aucun historique** : un statut est écrasé, on voit **la différence**, pas le trajet. Si B arrive puis repart entre deux visites, vous ne verrez que « a quitté… » : c'est écrit dans l'app (phrase de limite).
- **Le Feed n'est pas harmonisé** : il dit encore « Pas de nouvelles de… » dans la bulle rouge, alors que le centre dit « Dernière nouvelle de… » (décision Q1 : petit ticket Feed à part).
- **Pas de notification quand l'app est fermée** (il faudrait le plan Blaze et un serveur) ; **pas de message pré-écrit** dans la ligne.
- **iOS** : NOTIF-P1I, à suivre.
