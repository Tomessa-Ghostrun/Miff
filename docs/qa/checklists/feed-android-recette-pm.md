# Recette Android du Feed (FEED-1 à FEED-3 : en-tête, bulle, cartes, statuts, Localiser) — parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), avec **deux comptes de test dans la même Miff** : le compte **A** (créateur) sur l'OPPO et le compte **B** (membre) sur l'iPhone 11 Pro si possible (TestFlight), à défaut sur un second téléphone ou un second compte Google/e-mail sur l'OPPO (on se déconnecte et on se reconnecte entre les deux).
**Durée** : environ 40 minutes, **sans sortir de chez vous** : pour voir l'orange et le rouge sans attendre 1 h et 2 h, on **modifie l'heure du statut dans la console Firebase** (voir l'astuce plus bas). Une seule vraie sortie est facultative (partie G).
**À savoir avant de commencer** :
- L'APK à installer est celui **signé par la CI** (artefact `miff-debug-apk` de la PR), **pas** un APK compilé à la main (la connexion Google ne marcherait pas). Désinstallez l'ancienne version d'abord.
- **La photo d'update n'est pas dans cette livraison** (ticket FEED-4) : aucune carte n'a de photo, c'est normal.
- **Aucune notification** n'est envoyée et il n'y a plus de cloche : c'est voulu (plan gratuit).
- Avant de commencer, vérifiez avec l'assistant que les règles Firestore des **statuts** et de la **position** sont bien déployées (sinon « Localiser » dira toujours « pas de position connue » et les statuts ne seront pas lisibles).

## Astuce : fabriquer un orange, un rouge ou une zone de danger depuis la console

Console Firebase > projet **Miff** (`miff-3d283`, jamais l'autre projet) > Firestore Database > `miffs/{id de la Miff}/status/{uid du compte}`. Modifiez **à la main** :
- **Orange** : `presence` = `out`, `presenceAt` et `lastUpdateAt` = **il y a 1 h 10** (cliquez sur le champ, type « timestamp »).
- **Rouge d'ancienneté** : `presence` = `out`, `presenceAt` et `lastUpdateAt` = **il y a 3 h**.
- **Zone de danger** : `presence` = `in_risk`, `placeId` = l'identifiant d'un lieu de la Miff (le nom de ce lieu s'affichera chez les proches).
- **Retour au vert** : `presence` = `in_trusted`, `presenceAt` = maintenant.
Les champs restent dans le schéma (ne rajoutez rien). Si le téléphone du compte bouge ou si vous rouvrez son app, il peut réécrire son vrai statut : refaites la modification.

## Règles à retenir

- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- **Notez toute phrase qui vous semble trop dure ou anxiogène** (ticket FEED, risque R1) : « zone de danger » est un mot fort, les phrases sont à relire à voix haute.
- Les heures sont **absolues** à partir de l'orange (« à 16 h 40 ») ; avant, elles sont relatives (« il y a 12 min »).

## Préparation (5 min)

1. Installez l'APK de debug de la PR sur l'OPPO (compte **A**), connectez-vous, ouvrez la Miff.
2. Compte **B** : rejoignez la Miff avec le code, acceptez le partage de position (texte v3) et donnez « Toujours » + « Position précise » sur chaque téléphone.
3. Compte **A** : vérifiez qu'**au moins un lieu** « Maison » existe (onglet Profil > Lieux). Une zone de danger demande le Premium (accordé à la main).
4. Ouvrez la console sur `status/{uid A}` et `status/{uid B}` : les deux statuts doivent exister (sinon voir la partie F).

## Partie A — En-tête (3 min)

- [ ] L'en-tête reste **en haut** quand on fait défiler. À gauche **votre photo (ou vos initiales)** avec un **anneau** (vert si vous êtes chez vous) : **la couleur seule, sans coche ni autre petit dessin** (décision du PM du 2026-11-06) ; au centre le logo ; **aucune cloche**, le côté droit est vide.
- [ ] Touchez votre photo : l'onglet **Profil** s'ouvre. Revenez au Feed.
- [ ] Défilez vers le bas puis touchez le **logo** : le fil remonte tout en haut.
- [ ] Avec **une seule Miff** : le nom est dans une pastille **plate, sans flèche** (elle ne réagit pas au toucher). Avec **plusieurs Miffs** (compte Premium) : pastille avec flèche, liste avec **coche** sur la Miff affichée, « + Rejoindre / + Créer » (et seulement pour un Premium).

## Partie B — Cartes (5 min)

- [ ] **Votre carte est la première**, avec « Toi », votre état, la ligne « Ce que tes proches voient de toi » et **aucun** bouton.
- [ ] La carte de B vient ensuite : prénom, **pastille d'état** (ex. « À la maison », sans icône), ligne de temps (« Depuis 12 min »), **batterie** (« 85 % »), puis **trois boutons** : position, message, téléphone.
- [ ] **Message** ouvre l'application SMS avec le numéro de B **préparé** (rien n'est envoyé) ; **Téléphone** ouvre le **composeur** avec le numéro (aucun appel lancé). Revenez avec « retour ».
- [ ] Si un membre n'a pas de numéro (ancien compte) : seul le bouton position est visible.
- [ ] Un membre qui vient d'entrer (pas encore de statut) : « Pas encore de nouvelles », pastille grise ; il a quand même un bouton position (voir la partie D : « n'a pas encore de position connue »).
- [ ] Un membre qui a **arrêté de partager** : « Partage arrêté », jamais rouge.
- [ ] Batterie : après 1 h sans mise à jour du statut elle s'affiche **datée** (« 12 % · 16 h 02 ») ; après 24 h elle disparaît. **Aucune alerte de batterie faible.**
- [ ] « Ajouter un proche » ouvre la feuille de partage (nom, code, lien). (À 15 membres le bouton devient « Cette Miff est bien remplie ! » : couvert par un test, pas à refaire à la main.)

## Partie C — Couleur de la Miff et bulle (10 min, avec l'astuce de la console)

Pour chaque ligne, modifiez le statut de **B** puis regardez le Feed du compte **A** (il se met à jour tout seul).
- [ ] **Vert** : B `in_trusted`. Bulle **verte** « Tout va bien » + « Dernière nouvelle il y a … » (l'heure de B, **jamais la vôtre**). Si un troisième membre n'a pas encore de statut : « 2 sur 3 ont donné des nouvelles ».
- [ ] **Neutre** : supprimez le statut de B ou mettez `presence` = `unknown` : bulle **grise** « Pas encore de nouvelles » (jamais « Tout va bien »).
- [ ] **Orange** : B `out` il y a 1 h 10. Bulle **orange** : « B a quitté la maison à 15 h 50 » (heure absolue), **sans** point d'exclamation, bouton « Envoyer un message » (ouvre le SMS). La carte de B : « A quitté la maison », « Dernière nouvelle à … ».
- [ ] **Rouge d'ancienneté** : B `out` il y a 3 h. Bulle **rouge** : « Pas de nouvelles de B depuis 14 h 00 ». Aucune animation, aucun son.
- [ ] **Zone de danger** : B `in_risk` : bulle rouge **« B est à {nom du lieu} »** + « Depuis … » + « Une zone définie par ta Miff ». La carte de B : « Dans une zone de danger ». **Nulle part** le mot « zone à risque ».
- [ ] **Vous seul(e) en orange ou en rouge** (modifiez votre propre statut, B reste vert) : la bulle vous parle : « Tu as quitté la maison à … », « Tes proches n'ont pas eu de tes nouvelles depuis … », « Tu es dans une zone de danger de ta Miff » (+ « Tes proches le voient aussi »). **Pas** de bouton « Envoyer un message » sur votre propre page.
- [ ] **Plusieurs alertes** (B rouge et un troisième membre orange, ou deux comptes) : des **pastilles** apparaissent sous la bulle (la page courante est **allongée**), le **rouge d'abord**, on change de page en **glissant**, **rien ne défile tout seul**. Avec une seule alerte : pas de pastilles.
- [ ] **Les cartes ne changent jamais d'ordre** selon l'urgence (vous, puis l'ordre d'arrivée).
- [ ] **Couleur qui avance seule** : mettez B `out` il y a **58 min** et **attendez 2 à 3 minutes sans toucher l'écran** : la carte passe de vert à orange **sans rien recharger** (le calcul se refait chaque minute).
- [ ] Sur chaque couleur, **le texte est lisible** (teinte foncée sur fond clair). **Aucun pictogramme** (coche, horloge, « ! ») sur l'anneau de la photo ni dans la pastille d'état des cartes : la couleur est le seul signal, les mots de l'état restent dans la pastille, et la bulle garde son petit dessin décoratif (étoile, horloge, « ! », sablier) : points validés par le PM le 2026-11-06.

## Partie D — « Localiser » (5 min)

Prérequis : B partage et a une position (console : `positions/{uid B}` existe) ; vous partagez aussi (c'est la réciprocité).
- [ ] Touchez le bouton position de B : un **indicateur** tourne un instant, puis une **feuille** s'ouvre : « Dernière position connue de B », « Mesurée à … (il y a … min). Ce n'est pas une position en direct. », la ligne « Vous partagez tous les deux… », le bouton « Ouvrir dans les cartes ». **Aucune coordonnée n'est écrite à l'écran.**
- [ ] « Ouvrir dans les cartes » ouvre Google Maps (ou l'appli de cartes) sur un **point**, **sans nom de personne ni de lieu**. Revenez.
- [ ] Fermez la feuille (bouton, glissement vers le bas, retour système) : plus rien n'est affiché. (L'application Firebase garde une copie de la position dans la mémoire locale du téléphone jusqu'à la déconnexion : limite connue et assumée, elle n'apparaît nulle part dans Miff.) Rouvrez-la : une **nouvelle** lecture est faite (l'heure « Mesurée à » est celle de la console).
- [ ] **Position de plus de 24 h** : dans la console, mettez `measuredAt` de B **il y a 30 h** : la feuille dit « Elle date d'hier à … : elle n'est plus affichée. », **sans** bouton de carte.
- [ ] **Pas de position** : supprimez `positions/{uid B}` : « B n'a pas encore de position connue. » Faites la même chose avec un proche **qui vient d'entrer et n'a pas de statut** (carte grise « Pas encore de nouvelles ») : le bouton position existe et la réponse est la même, calme.
- [ ] **Imprévu** (facultatif, difficile à provoquer à la main) : si la lecture échoue pour une autre raison qu'un refus ou un réseau coupé (délai dépassé, quota atteint, connexion expirée), la feuille dit « Impossible d'afficher la position pour le moment. Réessaie dans un moment. » (jamais « Pas de connexion… » tant que le téléphone a du réseau). Cas couvert par des tests.
- [ ] **Refus** : arrêtez **votre** partage (Miff > Partager mon statut), revenez au Feed : le bouton position a disparu. (Le message de refus « On ne peut pas afficher la position de B… » ne s'affiche que si le refus arrive pendant la lecture : cas couvert par un test.)
- [ ] **Hors connexion** : mode avion, touchez le bouton : « Pas de connexion pour le moment. Réessaie dès que le réseau est revenu. » (réservé à une vraie absence de réseau). Aucun mot « erreur » ni « quota » dans aucun de ces cas.

## Partie E — Hors connexion et états du Feed (6 min)

- [ ] **Mode avion** avec tout vert : un bandeau gris « Dernières informations de 16 h 02 » apparaît (avec l'icône de nuage barré). **Jamais de vert sans date.** Remettez le réseau : le bandeau disparaît. Ce n'est **pas** l'écran « Tu n'as plus accès à la Miff ».
- [ ] Mode avion avec une alerte orange : la bulle ajoute « Informations de … : reconnecte-toi pour les mettre à jour. »
- [ ] **Vous arrêtez de partager** (Miff > Partager mon statut > arrêter) : au retour au Feed, la bulle grise dit « Tu as arrêté de partager. Reprends le partage pour voir les nouvelles de tes proches. » avec le bouton « Reprendre le partage » (il ouvre l'écran de la Miff, où se trouve la question). Les cartes des proches : « Nouvelles masquées », les boutons message et téléphone restent, **plus de bouton position**. Votre carte : « Partage arrêté ». Reprenez le partage : tout revient.
- [ ] **En attente de ton accord** (si vous avez un compte avec l'ancien texte de consentement, partie E de la recette LOC-2a) : l'encart de LOC-2b est en haut, la bulle est grise (« Pas encore de nouvelles » + la phrase d'explication).
- [ ] **Premier chargement** (désinstallez/réinstallez, ouvrez le Feed) : des formes grises avec « Chargement des nouvelles… », **aucun** « Tout va bien » ni couleur avant la lecture. Sur un réseau très lent, la phrase « Pas encore de nouvelles » n'apparaît qu'après une quinzaine de secondes.
- [ ] **Pas de fausse alerte au réveil** : mettez B en orange (astuce console), ouvrez le Feed, **puis** remettez B au vert dans la console et rouvrez l'app (fermée puis ouverte) : l'orange ne doit **pas** s'afficher un instant avant le vert (le Feed attend la confirmation du serveur).
- [ ] **Réseau coupé après un orange** : mode avion avec une alerte affichée : l'alerte reste, avec « Informations de … : reconnecte-toi pour les mettre à jour. »
- [ ] **Miff sans proche** (compte seul dans sa Miff) : bulle grise « Personne d'autre dans cette Miff pour l'instant », votre carte, « Ajouter un proche ».

## Partie F — « Partage de ton statut en cours… » (2 min)

- [ ] Dans la console, **supprimez votre propre statut** (`status/{uid A}`) puis revenez au Feed : un bandeau bleu pâle « Partage de ton statut en cours… » avec un indicateur, **jamais** un message d'erreur. Rouvrez l'app ou attendez quelques secondes : le statut se republie (si la détection tourne) et le bandeau disparaît.

## Partie G — Langue, grande police, lecteur d'écran (4 min)

- [ ] **Langue** : Réglages > Langue > English : tous les textes du Feed passent en anglais (« All good », « Thomas left home at 4:40 PM », heures « 4:40 PM »), puis revenez en français.
- [ ] **Grande police** : réglages du téléphone > Affichage > Taille de la police au maximum : les cartes restent lisibles (rien de coupé), les trois boutons restent atteignables.
- [ ] **TalkBack** (facultatif) : une carte se lit comme un tout, dans l'ordre personne, état, temps, puis batterie (« Thomas, à la maison, depuis 12 min, batterie à 85 % ») ; votre photo en haut annonce « Ouvrir mon profil. Ton statut : … » ; avec plusieurs alertes, « Alerte précédente / suivante » sont proposées dans les actions.
- [ ] **Applications récentes** : ouvrez la vue des applications récentes (Android 13) : l'aperçu du Feed **n'affiche pas** de noms ni de photos.

## Partie H — Coût de lecture (facultatif, 5 min, à faire une fois)

Le Feed est le premier gros consommateur de lectures du plan gratuit (50 000 par jour pour tout le projet). Relevez dans la console Firebase > Firestore > Utilisation le nombre de **lectures** avant, puis **ouvrez le Feed une fois à froid** (app fermée puis ouverte), notez le nombre après (le graphique a un délai d'environ 4 minutes). Estimation de l'ouverture à froid pour 4 membres et 3 lieux : **environ 20 lectures** ; un rattachement à chaud : une dizaine ; « Localiser » : **6** (1 + 5 de règles). Reportez vos chiffres à l'assistant : ils servent à recalibrer `docs/dev/surveillance-quotas.md`.

## Ce que vous n'avez pas à vérifier (couvert par les tests)

La couleur à 0 h 59 / 1 h 00 / 2 h 00 / 2 h 01, toutes les branches de la couleur de la Miff, les phrases FR et EN, les 6 réponses de « Localiser », l'absence d'écriture dans Firestore, l'absence de coordonnée dans un journal ou un message, les zones de 48 dp, la parité des textes avec iOS.
