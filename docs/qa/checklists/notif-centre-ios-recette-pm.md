# Recette iPhone du centre de notifications complet (CNC-I) : parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5.2), avec le build TestFlight « interne » de la branche `ios/centre-notifications`, et **deux comptes de test** (A sur l'OPPO ou un second téléphone, B sur l'iPhone) dans une **Miff de test**. Pour l'étape « plusieurs Miffs » : un compte premium dans deux Miffs de test.
**Durée** : environ 40 minutes (la partie 3 demande le serveur déployé).
**Ticket** : `docs/tickets/centre-notifications-complet.md` (critères CNC-1 à CNC-27). **Version Android jumelle** : `docs/qa/checklists/notif-centre-android-recette-pm.md` (mêmes étapes, mêmes résultats attendus, sauf ce qui est dit « iPhone » ci-dessous).

## Règles de sécurité de cette recette

- **N'utilisez JAMAIS de vrais proches** : l'alerte envoie une vraie notification à **tous les membres** de la Miff. Créez une Miff dont les seuls membres sont vos comptes de test. **Ne composez jamais le 17, 15, 18 ou 112** (le bouton « Appeler » ouvre seulement le composeur ; revenez en arrière).
- **Projet Firebase : `miff-3d283` uniquement.** Jamais celui de Ghostrun.
- **Limite de 3 alertes par 24 h et par compte** : chaque début d'alerte compte, même terminé aussitôt.

## À savoir : l'état du serveur change ce que vous voyez

Le **journal du serveur** (`users/{uid}/notifications`) est écrit par les fonctions `notifierUpdate` et `notifierAlerte` **une fois redéployées** (règles + règle TTL d'abord, texte légal publié, puis fonctions : c'est à vous, sur votre « oui » explicite). Côté iPhone, sa **lecture** est derrière la condition de compilation `NOTIF_JOURNAL`, **allumée dans le build TestFlight « interne »** (celui de cette recette) et **éteinte dans toute version de publication**.

| Situation | Ce que vous voyez | Normal ? |
|---|---|---|
| **Journal non déployé** | Parties 1 et 2 : tout marche (images rondes, lignes écrites **au premier plan ou au toucher**). Partie 3 : **rien de plus** (la requête est refusée en silence, **aucune erreur affichée**, pas de nouvelle tentative avant 30 minutes). | **Oui.** Ne cherchez pas de bug. |
| **Règles déployées, fonctions non redéployées** | Journal vide : aucune ligne de plus ; aucune erreur. | Oui. |
| **Tout déployé** | Tout le parcours, partie 3 comprise. | Oui. |

- **Un défaut** : capture d'écran + numéro de l'étape, puis étape suivante.
- B a les notifications **autorisées** et le partage de position actif ; A et B se sont donné leur **photo de profil** (A **avec** photo ; un compte C **sans** photo si vous en avez un, sinon retirez la photo de A pour l'étape 2.2).

## Partie 1 : le logo et l'image ronde (B) (8 min)

| # | Étape | Résultat attendu |
|---|---|---|
| 1.1 | Sur l'iPhone de B, touchez la **cloche** du Feed pour ouvrir le centre. | Chaque ligne a une **image ronde de 40 pt** à gauche du texte (le point bleu « non lu » reste à gauche de l'image). Le texte, l'heure, le nom de la Miff (si plusieurs Miffs) sont **inchangés**. |
| 1.2 | **Le logo** : repérez une ligne « Ta Miff est passée en orange / rouge » (étape 2.7 pour la fabriquer). | Un **rond bleu** avec le **M blanc et le « i »** et un **cœur jaune-vert** au-dessus du « i ». **Le cœur est bien un cœur** (pas un point) ; pas de carré, pas de coin arrondi. |
| 1.3 | **Grande taille de texte** (Réglages > Accessibilité > Affichage et luminosité > Taille du texte, au maximum), rouvrez le centre. | Les lignes **grandissent**, l'image ronde **garde sa taille** (40 pt) ; **deux initiales** restent lisibles dans le rond. |
| 1.4 | **VoiceOver** : passez d'une ligne à l'autre. | Chaque ligne est lue **d'un bloc** : « **{Prénom} a donné des nouvelles**, {heure}, non lu ». **L'image n'est jamais annoncée.** |

## Partie 2 : chaque type de notification, avec son image (25 min)

Pour **chaque** étape : (a) la notification du système, (b) la **ligne dans le centre**, (c) **l'image ronde** : photo, initiales ou logo, (d) le **texte**.

| # | Étape | Résultat attendu |
|---|---|---|
| 2.1 | **N9 « a donné des nouvelles »**, app **au premier plan** : A publie une update pendant que B regarde le Feed. | Aucune bannière de plus. **Nouveau** : une ligne « **{Prénom de A} a donné des nouvelles** » apparaît dans le centre avec **la photo de profil de A** (pas la photo de l'update) ; la cloche a son point. |
| 2.2 | Même chose avec un compte **sans photo de profil**. | La ligne affiche les **initiales** de la personne sur un rond teinté. |
| 2.3 | **App en arrière-plan** : A publie ; B touche la notification de l'écran verrouillé. | Le **Feed de la Miff** s'ouvre **sur la carte de A**. **Nouveau** : la ligne est dans le centre, déjà **lue** (pas de point). |
| 2.3 bis | **Toucher une notification livrée plusieurs heures avant (ou la veille)** : laissez la notification de A dans le centre de notifications d'iOS, rouvrez-la le lendemain ou 3 h plus tard. | La ligne porte **l'heure de livraison** (pas celle du toucher) et est rangée dans la bonne section (« Hier » pour la veille). |
| 2.4 | **Deux personnes** (A puis C) donnent des nouvelles. | **Une ligne par personne**, chacune avec **sa** photo ou ses initiales. **Aucun regroupement.** |
| 2.5 | **Alerte, début** (A maintient 5 s sur le bouton rond). **App ouverte** chez B. | Bannière et son comme avant. **Nouveau** : une ligne « {Prénom de A} a déclaré une alerte / Prends de ses nouvelles : appelle {Prénom} ou ouvre Miff. » avec **la photo de A**, **sans rouge ni icône d'alarme**. |
| 2.6 | **Alerte, fin** (A maintient 5 s sur la bannière). | Ligne **« {Prénom de A} a mis fin à son alerte »** ; c'est **une ligne de plus** (le début n'est pas remplacé dans le centre). |
| 2.7 | **N3 / N4 « Ta Miff est passée en orange / rouge »** (astuce de `docs/qa/checklists/notifs-ios-recette-pm.md`) puis ouvrez l'app. | Ligne avec le **logo Miff** (la Miff entière, pas une personne). |
| 2.8 | **N1 / N2 « Tu es passé orange / rouge »** (rappel à vous-même). | Ligne avec **VOTRE photo de profil** (vos initiales sans photo), **pas le logo**. Ligne écrite comme avant. |
| 2.9 | **N6 (zone de danger) et N7 (mode voyage terminé).** | **Votre photo** sur la ligne. |
| 2.10 | **N8 « {Prénom} propose un lieu »** (créateur de la Miff, après la demande d'un membre) et **« Depuis ta dernière visite »**. | La **photo du demandeur / du proche** devant la ligne. |
| 2.11 | **Membre parti** : A **quitte la Miff**, puis B rouvre le centre (après être repassé par le Feed). | Les anciennes lignes de A deviennent **logo Miff + « Un proche a donné des nouvelles »** : **plus de prénom, plus de photo**. Jamais un rond vide ni un « ? ». |
| 2.12 | **Photo changée** : A change sa photo (Réglages > Mes informations), B repasse par le Feed puis rouvre le centre. | **Nom et photo changent ensemble** sur les lignes de A. |
| 2.13 | **Chargement** : forcez la fermeture de l'app, rouvrez-la et allez **directement au centre**. | Un rond gris neutre, puis la photo (sans clignotement de logo). Le texte dit « Un proche a donné des nouvelles » un instant, puis le prénom. |
| 2.14 | **Plusieurs Miffs** (premium) : quittez une Miff. | Les lignes de proches de cette Miff **disparaissent** du centre. |
| 2.15 | **Notifications coupées dans Réglages iOS** (CNC-7) : coupez les notifications de Miff, A publie, B ouvre le centre. | Le centre est **flouté pour les rappels** avec « Les notifications sont coupées », **mais les lignes des proches (nouvelles, alertes) restent visibles au-dessus**, avec la photo. |

## Partie 3 : ce que l'iPhone n'avait pas traité (journal du serveur, CNC-1 à CNC-5, CNC-21)

**Prérequis** : règles, règle TTL et fonctions déployées (sinon cette partie n'apporte rien, c'est normal).

| # | Étape | Résultat attendu |
|---|---|---|
| 3.1 | **App fermée** (balayée) sur l'iPhone de B. A publie une update, puis déclare une alerte et la termine. **Sans toucher les notifications**, attendez 1 minute et ouvrez l'app. | Dans le centre, **les trois lignes** sont là (non lues, point sur la cloche), avec les **photos**. **Aucune erreur affichée.** C'est le défaut du ticket (CNC-1 et CNC-2) : sur iPhone, rien n'entrait jamais. |
| 3.2 | **Mode avion** sur B pendant que A publie, **reprenez le réseau**, rouvrez l'app (au bout d'au moins 1 minute). | La ligne apparaît **sans doublon**, **sans** erreur pendant le mode avion. |
| 3.3 | **iPhone éteint plus de 2 heures**, A publie, rallumez et ouvrez l'app. | La ligne est là (CNC-3). |
| 3.4 | **Pas de doublon** : A publie, B touche la notification, puis ouvre le centre. | **Une seule** ligne pour cette update. |
| 3.5 | **Interrupteur « Quand un proche donne des nouvelles »** coupé chez B ; A publie. | **Pas de ligne** pour cette update. L'alerte, elle, a **toujours** sa ligne. |
| 3.6 | **Première installation** sur un iPhone qui a déjà des entrées de plus de 24 h : réinstallez le build, reconnectez B. | Les lignes **de plus de 24 h arrivent déjà lues** (aucun point pour du passé) ; celles des dernières 24 h sont non lues. Les notifications **d'avant le déploiement** ne sont **pas** là (rétroactivité nulle, décidée). |
| 3.7 | **Cadence** : ouvrez/fermez le centre dix fois en une minute. | Au plus **une** requête du journal par minute (console Firestore > Utilisation). Aucun écouteur permanent. |

## Partie 4 : effacer, vie privée (CNC-13 à CNC-18)

| # | Étape | Résultat attendu |
|---|---|---|
| 4.1 | Dans le centre : balayez une ligne de la partie 3 vers la gauche > **Supprimer**. | La ligne disparaît. **Console Firestore** (`users/{uid de B}/notifications`) : l'entrée correspondante est **supprimée** aussi. |
| 4.2 | Menu « … » > **Tout effacer** > confirmer. | Centre vide. Console : toutes les entrées de B sont supprimées. **Supprimez l'app, réinstallez, reconnectez B** : les lignes **ne reviennent pas**. |
| 4.3 | Console Firestore : ouvrez une entrée. | Elle contient **exactement** `v`, `kind`, `miffId`, `actorUid`, `createdAt`, `expireAt`. **Aucun prénom, photo, lieu, position, nom de Miff.** |
| 4.4 | **Déconnexion** de B, reconnexion avec **un autre compte**. | Le centre est **vide** ; le journal de B n'est pas lu par l'autre compte. Le journal de B reste sur le serveur (il appartient au compte). |
| 4.5 | **Suppression de compte** de B. | Le centre local est vidé ; l'app **vide `users/{uid}/notifications` avant le profil** (un échec n'empêche jamais la suppression). |
| 4.6 | Verrouillez l'écran et regardez une notification. | **Rien n'a changé** : pas de photo, pas de pastille sur l'icône, pas de nouveau son (CNC-17). |

## Partie 5 : ton et textes

| # | Étape | Résultat attendu |
|---|---|---|
| 5.1 | Relisez à voix haute toutes les lignes du centre. | Aucune phrase alarmiste ou culpabilisante. Tutoiement. **Notez** toute phrase qui vous semble trop dure. |
| 5.2 | Passez l'app en **anglais** (Réglages > Langue). | « {Name} checked in », « {Name} declared an alert », « {Name} ended their alert » ; membre parti : « Someone in your circle checked in ». |

## Ce que vous nous rapportez

- Les captures du centre avec : une photo, des initiales, le logo, votre propre photo, un membre parti.
- **Le rendu du cœur du logo** à 40 pt sur l'iPhone 11 Pro : « cœur reconnaissable » ou « un point » (risque CNC-12).
- Le résultat de 3.1 (app fermée) : c'est la mesure H2 / H4 du ticket côté iPhone.
- **Une décision pour vous** : la confirmation que vous détenez les droits sur le dessin du logo (`design/assets/logo/README.md`, risque A4 du ticket) avant toute publication.
