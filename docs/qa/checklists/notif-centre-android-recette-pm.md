# Recette Android du centre de notifications complet (CNC-L et CNC-A) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), avec **deux comptes de test** (A et B) dans une **Miff de test** (A sur le second téléphone ou l'iPhone, B sur l'OPPO). Pour l'étape « plusieurs Miffs » : un compte premium dans deux Miffs de test.
**Durée** : environ 50 minutes (les parties 3 et 4 demandent le backend déployé).
**Ticket** : `docs/tickets/centre-notifications-complet.md` (critères CNC-1 à CNC-27). **Ce qui n'est PAS dans cette recette** : l'iPhone (CNC-I : l'iPhone n'écrit encore aucune ligne), les textes légaux (CNC-J), le déploiement du serveur (CNC-B).

## Règles de sécurité de cette recette

- **N'utilisez JAMAIS de vrais proches** : l'alerte envoie une vraie notification à **tous les membres** de la Miff. Créez une Miff dont les seuls membres sont vos comptes de test. **Ne composez jamais le 17, 15, 18 ou 112** (le bouton « Appeler » ouvre seulement le composeur ; revenez en arrière sans appuyer sur le bouton vert).
- **Projet Firebase : `miff-3d283` uniquement.** Jamais celui de Ghostrun. Ne modifiez rien dans la console sauf ce que cette recette demande (les statuts de la partie 2, en lecture/écriture à la main).
- **Limite de 3 alertes par 24 h et par compte** : chaque début d'alerte compte, même terminé aussitôt. Prévoyez 1 début pour la partie 2.

## À savoir : l'état du serveur change ce que vous voyez

Le **journal du serveur** (`users/{uid}/notifications`) est écrit par les fonctions `notifierUpdate` et `notifierAlerte` **une fois redéployées** (règles + règle TTL d'abord, texte légal publié, puis fonctions : c'est à vous, sur votre « oui » explicite). Côté appli, sa **lecture** est derrière un drapeau de build (`BuildConfig.NOTIF_JOURNAL`) **allumé dans l'APK de debug de la CI** (celui de cette recette) et **éteint dans toute version distribuable**.

| Situation | Ce que vous voyez | Normal ? |
|---|---|---|
| **Journal non déployé** (règles refusent la lecture) | Parties 1 et 2 : tout marche (les lignes sont écrites par le téléphone, avec les photos). Partie 3 : **rien de plus** (une requête est refusée en silence, **aucune erreur affichée**, pas de nouvelle tentative avant 30 minutes). | **Oui.** Ne cherchez pas de bug. |
| **Règles déployées, fonctions non redéployées** | Journal vide : aucune ligne de plus ; aucune erreur. | Oui. |
| **Tout déployé** | Tout le parcours, parties 3 et 4 comprises. | Oui. |

- Installez l'**APK de debug signé par la CI** (`miff-notif-centre-android-CI-<sha>.apk`), après avoir désinstallé l'ancienne version.
- **Un défaut** : capture d'écran + numéro de l'étape, puis étape suivante.
- B a les notifications **autorisées** et le partage de position actif ; A et B se sont donné leur **photo de profil** (A **avec** photo, un troisième compte C **sans** photo si vous en avez un ; sinon, retirez la photo de A pour l'étape 9).

## Partie 1 : le logo et l'image ronde (B) (8 min)

| # | Étape | Résultat attendu |
|---|---|---|
| 1.1 | Sur le téléphone de B, touchez la **cloche** du Feed pour ouvrir le centre. | Chaque ligne a une **image ronde de 40 dp** à gauche du texte (le point bleu « non lu » reste à gauche de l'image). Le texte, l'heure, le nom de la Miff (si plusieurs Miffs) sont **inchangés**. |
| 1.2 | **Le logo** : repérez une ligne « Ta Miff est passée en orange / rouge » (étape 4.1 pour la fabriquer). | Un **rond bleu** avec le **M blanc et le « i »**, et un **cœur jaune-vert** au-dessus du « i ». **Le cœur est bien un cœur** (pas un point) ; le M est plus grand que dans l'icône de l'appli. Pas de carré, pas de coin arrondi. |
| 1.3 | Passez le téléphone en **mode sombre** (réglages d'affichage) et rouvrez le centre. | Le logo est **identique** (même bleu, même cœur). Le rond neutre des initiales reste lisible. |
| 1.4 | Réglez la **taille de police** du téléphone au maximum, rouvrez le centre. | Les lignes **grandissent**, l'image ronde **garde sa taille** (40 dp) ; rien n'est coupé. |
| 1.4 bis | Réglez la taille de police au maximum et repérez une ligne avec des **initiales à deux lettres**. | Les deux lettres restent **lisibles dans le rond**, sans être coupées. |
| 1.5 | **TalkBack** : passez d'une ligne à l'autre. | Chaque ligne est lue **d'un bloc** : « **{Prénom} a donné des nouvelles**, Retrouve-les dans ta Miff, {heure}, non lu ». **L'image n'est jamais annoncée** (« image », « photo »). Une action « Supprimer cette notification » est proposée. |

**Non testable à la main** : une photo de profil qui annonce des dimensions énormes dans quelques octets (attaque d'un compte malveillant) ne peut pas être fabriquée depuis l'appli ; elle est couverte par les tests automatiques (`NotificationCenterAvatarScreenTest` : le centre et le Feed affichent les initiales, sans plantage).

## Partie 2 : chaque type de notification, avec son avatar (B, A) (25 min)

Pour **chaque** étape : vérifiez (a) la notification du système, (b) la **ligne dans le centre** (cloche), (c) **l'image ronde** : photo, initiales ou logo, et (d) le **texte**.

| # | Étape | Résultat attendu |
|---|---|---|
| 2.1 | **N9 « a donné des nouvelles »** : A publie une update (photo). B a l'appli **fermée** (balayée, écran verrouillé). | Notification « **{Prénom de A} a donné des nouvelles** ». Dans le centre : une ligne **« {Prénom de A} a donné des nouvelles »** avec **la photo de profil de A** (pas la photo de l'update). |
| 2.2 | Même chose avec un compte **sans photo de profil** (C, ou A après avoir retiré sa photo dans Réglages > Mes informations). | La ligne affiche les **initiales** de la personne (par exemple « LD ») sur un rond gris-beige. |
| 2.3 | **Deux personnes** (A puis C, ou A deux fois à plus d'une heure d'écart) donnent des nouvelles. | **Une ligne par personne**, chacune avec **sa** photo ou ses initiales. **Aucun regroupement** (« 2 proches… »). |
| 2.4 | **Alerte, début** (A, partie 2 de la recette de l'alerte : maintenez 5 s sur le bouton rond). | Chez B : notification « **{Prénom de A} a déclaré une alerte** ». **Nouveau** : une ligne **dans le centre** « {Prénom de A} a déclaré une alerte / Prends de ses nouvelles : appelle {Prénom} ou ouvre Miff. » avec **la photo de A**, **sans rouge ni icône d'alarme** (le Feed porte la couleur). Même **app ouverte** : la ligne est écrite. |
| 2.5 | **Alerte, fin** (A maintient 5 s sur la bannière). | Ligne **« {Prénom de A} a mis fin à son alerte »** avec la photo de A ; la notification de fin **remplace** celle du début dans le tiroir. |
| 2.6 | Touchez la ligne d'alerte (ou de nouvelles) dans le centre. | Le centre se ferme, le **Feed** de la Miff s'ouvre **sur la carte de A**. La ligne passe en « lu ». La notification du tiroir disparaît. |
| 2.7 | **N3 / N4 « Ta Miff est passée en orange / rouge »** : fabriquez le statut de A (astuce de `docs/qa/checklists/notifs-android-recette-pm.md`, partie 5) et ouvrez le Feed de B. | Ligne « Ta Miff est passée en orange / rouge » avec le **logo Miff** (la Miff entière, pas une personne). |
| 2.8 | **N1 / N2 « Tu es passé orange / rouge »** (rappel à vous-même) : fabriquez « orange dans 5 min » pour **B** (même astuce, partie 4 de la recette citée) puis laissez venir. | Ligne « Tu es passé orange / rouge » avec **VOTRE photo de profil** (vos initiales si vous n'en avez pas), **pas le logo**. La ligne s'écrit comme avant (mêmes plafonds, même rétention). |
| 2.9 | **N6 « zone de danger de ta Miff »** et **N7 « mode voyage terminé »** (recette citée, parties 6 et 7). | Mêmes règles : **votre photo** sur la ligne. |
| 2.10 | **N8 « {Prénom} propose un lieu »** (le créateur de la Miff ouvre le Profil après qu'un membre a proposé un lieu). | Ligne « {Prénom} propose un lieu pour « Miff » » avec la **photo du demandeur**. |
| 2.11 | **« Depuis ta dernière visite »** (rouvrez l'appli après plus de 30 min d'absence, alors qu'un proche a changé de lieu). | Les 1 à 3 lignes du groupe affichent **la photo du proche** concerné. |
| 2.12 | **Membre parti** : A **quitte la Miff** (ou le créateur le retire), puis B rouvre le centre. | Les anciennes lignes de A deviennent **logo Miff + « Un proche a donné des nouvelles »** (ou « Un proche a déclaré une alerte ») : **plus de prénom, plus de photo** de A. Jamais un rond vide ni un « ? ». |
| 2.13 | **Photo changée** : A change sa photo dans Réglages > Mes informations, B rouvre le centre (après 5 minutes ou après être repassé par le Feed). | **Nom et photo changent ensemble** sur les lignes de A. |
| 2.14 | **Chargement** : forcez l'arrêt de l'appli, rouvrez-la **directement sur le centre** (cloche dès le Feed). | Les images de personnes que le Feed n'a pas encore lues passent **d'un rond gris neutre à leur photo** (sans clignotement de logo). |
| 2.15 | **Plusieurs Miffs** (premium) : une ligne d'une Miff dont vous n'êtes **plus** membre (quittez-la). | Les lignes de proches de cette Miff **disparaissent** du centre (elles ne reviennent pas). |

## Partie 3 : ce que le téléphone n'avait pas reçu (journal du serveur, CNC-1 à CNC-5, CNC-20, CNC-21)

**Prérequis** : règles, règle TTL et fonctions déployées (sinon cette partie n'apporte rien, c'est normal).

| # | Étape | Résultat attendu |
|---|---|---|
| 3.1 | **App arrêtée de force** (Réglages du téléphone > Applis > Miff > Forcer l'arrêt) sur le téléphone de B. A publie une update, puis déclare une alerte et la termine. Attendez 1 minute. Ouvrez l'appli de B. | Dans le centre, **les trois lignes** sont là (« a donné des nouvelles », « a déclaré une alerte », « a mis fin à son alerte »), avec les **photos**. La cloche a son point (non lues). **Aucune erreur affichée.** (Android ne livre pas les messages à une appli arrêtée de force : c'est la cause que le journal corrige.) |
| 3.2 | **Mode avion** sur B pendant que A publie, puis **reprenez le réseau** et rouvrez le centre (au bout d'au moins 1 minute depuis l'ouverture précédente). | La ligne apparaît **sans doublon**, **sans** erreur pendant le mode avion. |
| 3.3 | **Téléphone éteint plus de 2 heures** (le message FCM a expiré), A publie, rallumez B et ouvrez l'appli. | La ligne est là (CNC-3). |
| 3.4 | **Pas de doublon** : A publie, B reçoit la notification (appli en arrière-plan), puis ouvre le centre. | **Une seule** ligne pour cette update (le push et le journal portent le même identifiant). |
| 3.5 | **Notifications coupées dans le téléphone** (CNC-7) : B coupe les notifications de Miff dans les réglages du téléphone ; A publie une update ; B ouvre le centre. | Le centre est **flouté pour les rappels** avec « Les notifications sont coupées », **mais les lignes « a donné des nouvelles » et les alertes sont visibles au-dessus**, avec la photo. |
| 3.6 | **Interrupteur « Quand un proche donne des nouvelles »** coupé chez B ; A publie. | **Pas de ligne** pour cette update chez B. L'alerte, elle, a **toujours** sa ligne (pas d'interrupteur de type). |
| 3.7 | **Première installation** sur un téléphone qui a déjà des entrées de plus de 24 h dans le journal : installez l'APK sur un téléphone neuf avec le compte B. | Les lignes **de plus de 24 h arrivent déjà lues** (aucun point sur la cloche pour du passé) ; celles des dernières 24 h sont non lues. Les notifications **d'avant le déploiement** ne sont **pas** là (rétroactivité nulle, décidée). |
| 3.8 | **Cadence** : ouvrez/fermez le centre dix fois en une minute. | Au plus **une** requête du journal par minute (console Firestore > Utilisation : quelques lectures, pas des dizaines). Aucun écouteur permanent. |

## Partie 4 : effacer, vie privée (CNC-13 à CNC-18)

| # | Étape | Résultat attendu |
|---|---|---|
| 4.1 | Dans le centre : appui long sur une ligne de la partie 3 > **Supprimer cette notification**. | La ligne disparaît. **Console Firestore** (`users/{uid de B}/notifications`) : l'entrée correspondante est **supprimée** aussi. |
| 4.2 | Menu « ⋯ » > **Tout effacer** > confirmer. | Centre vide. Console : les entrées correspondantes sont supprimées. **Désinstallez, réinstallez, reconnectez B** : les lignes **ne reviennent pas**. |
| 4.3 | Console Firestore : ouvrez une entrée du journal. | Elle contient **exactement** `v`, `kind`, `miffId`, `actorUid`, `createdAt`, `expireAt`. **Aucun prénom, aucune photo, aucun lieu, aucune position, aucun nom de Miff.** |
| 4.4 | **Déconnexion** de B, reconnexion avec **un autre compte**. | Le centre est **vide** (rien de B ne reste) ; le journal de B n'est pas lu par l'autre compte. **Le journal de B reste sur le serveur** (il appartient au compte) : en vous reconnectant avec B, les lignes des 30 derniers jours reviennent (celles de plus de 24 h déjà lues). |
| 4.5 | **Suppression de compte** de B (recette `auth-5a`). | Le centre local est vidé ; l'appli **vide `users/{uid}/notifications` avant le profil** (console : la collection est vide ; un échec n'empêche jamais la suppression). |
| 4.6 | Verrouillez l'écran et regardez une notification. | **Rien n'a changé** : pas de photo, pas de pastille sur l'icône, pas de nouveau son (CNC-17). |

## Partie 5 : ton et textes

| # | Étape | Résultat attendu |
|---|---|---|
| 5.1 | Relisez à voix haute toutes les lignes du centre. | Aucune phrase alarmiste, culpabilisante ou moralisatrice. Tutoiement. **Notez** toute phrase qui vous semble trop dure. |
| 5.2 | Passez le téléphone en **anglais** (Réglages > Langue). | « {Name} checked in », « {Name} declared an alert », « {Name} ended their alert » ; membre parti : « Someone in your circle declared an alert ». Mêmes images. |
| 5.3 | Passez en **pseudo-langue arabe** si disponible (réglage développeur) ou une langue de droite à gauche. | L'image ronde est du côté du **début** (à droite) ; le texte s'aligne à son côté. |

## Ce que vous nous rapportez

- Les captures du centre avec : une photo, des initiales, le logo, votre propre photo, un membre parti.
- **Le rendu du cœur du logo** à 40 dp sur l'OPPO (point 1.2) : « cœur reconnaissable » ou « un point ».
- Le résultat de 3.1 (appli arrêtée de force) : c'est la mesure H1 du ticket.
- **Une décision pour vous** : la confirmation que vous détenez les droits sur le dessin du logo (`design/assets/logo/README.md`, risque A4 du ticket) avant toute publication.
