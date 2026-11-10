# Recette Android du Feed (FEED-1 à FEED-3 : en-tête, bulle, cartes, statuts, Localiser) — parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), avec **deux comptes de test dans la même Miff** : le compte **A** (créateur) sur l'OPPO et le compte **B** (membre) sur l'iPhone 11 Pro si possible (TestFlight), à défaut sur un second téléphone ou un second compte Google/e-mail sur l'OPPO (on se déconnecte et on se reconnecte entre les deux).
**Durée** : environ 40 minutes, **sans sortir de chez vous** : pour voir l'orange et le rouge sans attendre 2 h et 4 h, on **modifie l'heure du statut dans la console Firebase** (voir l'astuce plus bas). Une seule vraie sortie est facultative (partie G).
**À savoir avant de commencer** :
- L'APK à installer est celui **signé par la CI** (artefact `miff-debug-apk` de la PR), **pas** un APK compilé à la main (la connexion Google ne marcherait pas). Désinstallez l'ancienne version d'abord.
- **La photo d'update** s'affiche depuis FEED-4 ; le bouton « Enregistrer la photo » du plein écran (décision du 2026-11-08) se recette en **Partie J**.
- **Aucune notification** n'est envoyée et il n'y a plus de cloche : c'est voulu (plan gratuit).
- Avant de commencer, vérifiez avec l'assistant que les règles Firestore des **statuts** et de la **position** sont bien déployées (sinon « Localiser » dira toujours « pas de position connue » et les statuts ne seront pas lisibles).

## Astuce : fabriquer un orange, un rouge ou une zone de danger depuis la console

Console Firebase > projet **Miff** (`miff-3d283`, jamais l'autre projet) > Firestore Database > `miffs/{id de la Miff}/status/{uid du compte}`. Modifiez **à la main** :
- **Orange** : `presence` = `out`, `presenceAt` et `lastUpdateAt` = **il y a 2 h 10** (cliquez sur le champ, type « timestamp »).
- **Rouge d'ancienneté** : `presence` = `out`, `presenceAt` et `lastUpdateAt` = **il y a 5 h**.
- **Zone de danger** : `presence` = `in_risk`, `placeId` = l'identifiant d'un lieu de la Miff (le nom de ce lieu s'affichera chez les proches).
- **Retour au vert** : `presence` = `in_trusted`, `presenceAt` = maintenant.
Les champs restent dans le schéma (ne rajoutez rien). Si le téléphone du compte bouge ou si vous rouvrez son app, il peut réécrire son vrai statut : refaites la modification.

## Règles à retenir

- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- **Notez toute phrase qui vous semble trop dure ou anxiogène** (ticket FEED, risque R1) : « zone de danger » est un mot fort, les phrases sont à relire à voix haute.
- Les heures sont **absolues** à partir de l'orange (« à 16 h 40 ») ; avant, la ligne d'une carte dit déjà l'heure d'arrivée ou de sortie (« Depuis 16 h 48 »), jamais une durée (PM, 2026-11-10) ; seules les bulles disent encore « Dernière nouvelle il y a 2 min ».

## Préparation (5 min)

1. Installez l'APK de debug de la PR sur l'OPPO (compte **A**), connectez-vous, ouvrez la Miff.
2. Compte **B** : rejoignez la Miff avec le code, acceptez le partage de position (texte v3) et donnez « Toujours » + « Position précise » sur chaque téléphone.
3. Compte **A** : vérifiez qu'**au moins un lieu** « Maison » existe (onglet Profil > Lieux). Une zone de danger demande le Premium (accordé à la main).
4. Ouvrez la console sur `status/{uid A}` et `status/{uid B}` : les deux statuts doivent exister (sinon voir la partie F).

## Partie A — En-tête (3 min)

- [ ] L'en-tête **se masque quand on fait défiler vers le bas** (il glisse vers le haut avec le fil, sans saut) **et réapparaît dès qu'on remonte**, même au milieu de la liste, sans attendre d'être tout en haut ; aucune carte n'est cachée dessous ni ne laisse de bande blanche. À gauche **votre photo (ou vos initiales)** avec un **anneau** (vert si vous êtes chez vous) : **la couleur seule, sans coche ni autre petit dessin** (décision du PM du 2026-11-06) ; au centre le logo ; **aucune cloche**, le côté droit est vide.
- [ ] Touchez votre photo : l'onglet **Profil** s'ouvre. Revenez au Feed.
- [ ] Défilez vers le bas puis touchez le **logo** : le fil remonte tout en haut.
- [ ] **Sélecteur de Miff (D47, 2026-11-08)** : pour **tout le monde**, même avec une seule Miff, la pastille a la flèche et l'ombre et s'ouvre ; la liste montre la ou les Miffs (coche sur celle affichée) puis « + Rejoindre une Miff » et « + Créer une Miff ». Un compte gratuit déjà dans une Miff arrive sur le paywall en les touchant : voir la **Partie I**.
- [ ] **Icône de l'onglet Feed** (décision du 2026-11-08) : dans la barre du bas, le Feed est une **maison classique** (aucun cœur), **au trait** quand l'onglet n'est pas actif et **pleine** quand il l'est, comme sur l'iPhone.

## Partie A bis — Comportement de l'en-tête au défilement (4 min)

- [ ] **Descendre puis remonter** : avec un Feed assez long (plusieurs proches), faites défiler vers le bas : photo de profil, logo et nom de la Miff disparaissent en douceur ; remontez même un tout petit peu : ils reviennent **tout de suite**. Au tout début de la liste ils sont toujours entiers (pas de bande vide au-dessus de la bulle).
- [ ] **Toujours visible** : (1) pendant le chargement (réinstallez ou coupez le réseau avant d'ouvrir), (2) avec **TalkBack** activé (ou tout autre service d'accessibilité) : faites défiler, l'en-tête **ne se cache jamais** et le focus de TalkBack ne se perd pas, (3) avec **un seul proche** (liste trop courte pour défiler), (4) **hors connexion** (mode avion), (5) **sélecteur de Miff ouvert** (compte Premium) : tant que la liste est ouverte l'en-tête reste.
- [ ] **Rotation et onglets** : tournez le téléphone, ou passez à l'onglet Profil et revenez : l'en-tête est **visible**, sans saut ni bande vide d'une image.
- [ ] **Petit défilement près du haut** : faites un tout petit défilement depuis le haut de la liste puis relâchez : aucune bande vide ne se forme entre l'en-tête à moitié caché et la bulle.
- [ ] **Grande police** : avec la taille de police au maximum, l'en-tête et la bulle ne se chevauchent pas, y compris juste après une rotation.
- [ ] **Autres éléments** : la bulle (animée) et le carrousel d'alertes (glissement manuel) ne saccadent pas pendant que l'en-tête bouge ; le logo et la photo se touchent facilement ; la barre d'onglets et les marges ne bougent pas.
- [ ] **Bas de liste et barre en verre** : faites défiler le Feed **jusqu'au bout** : le bouton « Ajouter un proche » finit **au-dessus** de la barre d'onglets, jamais caché dessous ; remontez : l'en-tête revient et aucun trou n'apparaît en haut ni en bas.
- [ ] Toucher le **logo**, ou la touche de l'onglet Feed quand on y est déjà, remonte le fil **et** réaffiche l'en-tête.

## Partie A ter — Tirer pour actualiser (4 min)

- [ ] **Le geste** : en haut de la liste, tirez vers le bas : un petit indicateur rond (bleu, discret) apparaît **sous l'en-tête** et tourne un instant ; les cartes se mettent à jour (changez un statut dans la console juste avant pour le voir). **L'en-tête ne bouge pas et ne clignote pas** pendant le geste. Au milieu de la liste, tirer ne fait rien d'autre que remonter : le geste n'agit qu'en haut.
- [ ] **Sortir de la copie du cache** : coupez puis rétablissez le réseau, ou ouvrez l'app juste après une coupure : si « Dernières informations de … » est affiché alors que le réseau est revenu, tirez vers le bas : le bandeau disparaît dès que le serveur répond.
- [ ] **Limite des 15 secondes** : tirez deux fois de suite à quelques secondes d'intervalle : la **seconde** fois l'indicateur s'arrête aussitôt sans rien recharger (vous pouvez le vérifier dans la console, partie H : la seconde actualisation n'ajoute aucune lecture). Après 15 secondes, tirer relit à nouveau.
- [ ] **Sans réseau** : mode avion, tirez : l'indicateur s'arrête **tout de suite**, le bandeau « Dernières informations de … » reste, aucun message d'erreur.
- [ ] **Serveur qui ne répond pas** (réseau très mauvais) : l'indicateur s'arrête au bout de **8 secondes au plus** avec le bandeau « Dernières informations de … » ; jamais de phrase alarmante ou culpabilisante.
- [ ] **TalkBack** : sur la liste, l'action « Actualiser » est proposée (menu des actions) ; l'activer fait la même chose que le geste ; TalkBack annonce poliment « Actualisation en cours » puis « Informations actualisées » (pas « à jour » si le bandeau « Dernières informations de… » est affiché).
- [ ] **Liste courte** : avec un seul proche (liste qui ne défile pas), tirer vers le bas actualise quand même.
- [ ] **Autres éléments** : la bulle (animée), le carrousel d'alertes (glissement manuel), la barre d'onglets en verre et les marges restent normaux pendant et après l'actualisation.
- [ ] **Coût (facultatif)** : une actualisation = **environ 20 lectures** (4 membres, 3 lieux), à noter dans la console avec la partie H. Usage abusif connu et assumé : tirer sans arrêt coûte au plus ~4 actualisations par minute, soit ~80 lectures par minute à 4 membres (~176 à 15 membres, ~300 ko d'avatars par actualisation à 15 membres) ; à reporter dans `docs/dev/surveillance-quotas.md` (recalibrage par `backend-data`).
- [ ] **Changer de Miff pendant une actualisation** (compte Premium, deux Miffs) : tirez pour actualiser puis, tout de suite, choisissez l'autre Miff dans la pastille : les cartes de la seconde Miff ne montrent **jamais** les statuts de la première (pas de « a quitté un lieu de confiance » inattendu), le squelette reste le temps que ses propres statuts arrivent.
- [ ] **Après une actualisation sans réponse** : le bandeau « Dernières informations de … » reste tant que le serveur n'a pas reparlé (limite connue : un changement de statut ou une nouvelle actualisation réussie le lève).

## Partie B — Cartes (5 min)

- [ ] **Votre carte est la DERNIÈRE** (D43), **sans** mention « Toi » à côté de votre prénom (D42), avec votre état, la ligne « Ce que tes proches voient de toi » et **aucun** bouton.
- [ ] La carte de B vient avant la vôtre : prénom, **pastille d'état** (ex. « À la maison », sans icône), ligne de temps (**l'heure d'arrivée**, « Depuis 16 h 48 », jamais « Depuis 12 min » ; hier : « Depuis hier à … », puis la date courte), **batterie** (« 85 % »), puis **trois boutons** : position, message, téléphone.
- [ ] **Étiquette selon la catégorie du lieu** (décision du 2026-11-08) : B `in_trusted` dans un lieu de catégorie Maison : « À la maison » ; Travail : « Au travail » ; École : « À l'école » ; Sport : « Au sport » ; Chez un proche : « Chez un proche » ; Loisir ou Autre : **le nom du lieu seul** (un nom très long est coupé proprement par « … » sur deux lignes au plus, y compris en grande police). Le Feed n'envoie ce nom à aucune notification (voir NOTIF-P0, Q9 : les notifications afficheront le nom des lieux Loisir / Autre).
- [ ] **Message** ouvre l'application SMS avec le numéro de B **préparé** (rien n'est envoyé) ; **Téléphone** ouvre le **composeur** avec le numéro (aucun appel lancé). Revenez avec « retour ».
- [ ] Si un membre n'a pas de numéro (ancien compte) : seul le bouton position est visible.
- [ ] Un membre qui vient d'entrer (pas encore de statut) : « Pas encore de nouvelles », pastille grise ; il a quand même un bouton position (voir la partie D : « n'a pas encore de position connue »).
- [ ] Un membre qui a **arrêté de partager** : « Partage arrêté », jamais rouge.
- [ ] Batterie (D44) : le pourcentage s'affiche **seul** (« 12 % »), **jamais d'heure de mesure**, même après 1 h sans mise à jour du statut ; **au-delà de 6 h sans mise à jour elle disparaît** (D44). **Aucune alerte de batterie faible.**
- [ ] « Ajouter un proche » ouvre la feuille de partage (nom, code, lien). (À 15 membres le bouton devient « Cette Miff est bien remplie ! » : couvert par un test, pas à refaire à la main.)

## Partie C — Couleur de la Miff et bulle (10 min, avec l'astuce de la console)

Pour chaque ligne, modifiez le statut de **B** puis regardez le Feed du compte **A** (il se met à jour tout seul).
- [ ] **Vert** : B `in_trusted`. Bulle **verte** « Tout va bien » + « Dernière nouvelle il y a … » (l'heure de B, **jamais la vôtre**). Si un troisième membre n'a pas encore de statut : « 2 sur 3 ont donné des nouvelles ».
- [ ] **Neutre** : supprimez le statut de B ou mettez `presence` = `unknown` : bulle **grise** « Pas encore de nouvelles » (jamais « Tout va bien »).
- [ ] **Bulle centrée, sans étoile en vert** (D49, PM 2026-11-08) : dans les bulles verte, orange et rouge, le titre, les lignes et le bouton « Envoyer un message » sont **centrés** dans la tache (marges égales à gauche et à droite), avec une police normale puis la plus grande ; **la bulle verte n'a plus aucun pictogramme** (plus d'étoile) ; orange (horloge), rouge (point d'exclamation) et neutre (sablier) gardent le leur, mais **sans cercle autour** (rien qui ressemble à un bouton, PM 2026-11-10) : un grand symbole **en filigrane doux** posé dans le coin bas droit de la tache, derrière le texte, qui occupe l'espace sans être voyant. Faites défiler les pages d'un carrousel : toutes sont centrées. TalkBack lit le même texte qu'avant (le pictogramme n'était pas lu).
- [ ] **Orange** : B `out` il y a 2 h 10. Bulle **orange** : « B a quitté la maison à 14 h 50 » (heure absolue), **sans** point d'exclamation, bouton « Envoyer un message » (ouvre le SMS). La carte de B : « A quitté la maison », « Dernière nouvelle à … ».
- [ ] **Rouge d'ancienneté** : B `out` il y a 5 h. Bulle **rouge** : « Pas de nouvelles de B depuis 12 h 00 ». Aucune animation, aucun son.
- [ ] **Zone de danger (règle du 2026-11-08)** : B `in_risk` avec `presenceAt` il y a 20 min : rouge **dès l'entrée**, même avec un `lastUpdateAt` récent mais **antérieur** à `presenceAt` (il ne compte pas). Mettez ensuite `lastUpdateAt` à il y a 5 min (postérieur à l'entrée) : la carte passe au vert (l'étiquette reste « Dans une zone de danger ») et la bulle rouge disparaît ; remettez-le à il y a 1 h 05 : rouge de nouveau. Votre propre statut en zone de danger : rouge jusqu'à ce que l'update soit acquittée par le serveur (indéfiniment tant que le téléphone est hors ligne). Rouge : bulle rouge **« B est à {nom du lieu} »** + « Depuis … » + « Une zone définie par ta Miff ». La carte de B : « Dans une zone de danger ». **Nulle part** le mot « zone à risque ».
- [ ] **Vous seul(e) en orange ou en rouge** (modifiez votre propre statut, B reste vert) : la bulle vous parle : « Tu as quitté la maison à … », « Tes proches n'ont pas eu de tes nouvelles depuis … », « Tu es dans une zone de danger de ta Miff » (+ « Tes proches le voient aussi »). **Pas** de bouton « Envoyer un message » sur votre propre page.
- [ ] **Plusieurs alertes** (B rouge et un troisième membre orange, ou deux comptes) : des **pastilles** apparaissent sous la bulle (la page courante est **allongée**), le **rouge d'abord**, on change de page en **glissant**, **rien ne défile tout seul**. Avec une seule alerte : pas de pastilles.
- [ ] **Les cartes ne changent jamais d'ordre** selon l'urgence (les autres dans l'ordre d'arrivée, puis vous en dernier).
- [ ] **Couleur qui avance seule** : mettez B `out` il y a **1 h 55** et **attendez 6 à 7 minutes sans toucher l'écran** : la carte passe de vert à orange **sans rien recharger** (le calcul se refait chaque minute).
- [ ] Sur chaque couleur, **le texte est lisible** (teinte foncée sur fond clair). **Aucun pictogramme** (coche, horloge, « ! ») sur l'anneau de la photo ni dans la pastille d'état des cartes : la couleur est le seul signal, les mots de l'état restent dans la pastille, et la bulle garde son petit dessin décoratif (étoile, horloge, « ! », sablier) : points validés par le PM le 2026-11-06.

## Partie C bis — Mouvement très léger de la bulle (3 min)

- [ ] **Constater le mouvement** : ouvrez le Feed et regardez la bulle de couleur quelques secondes, sans y toucher : elle **respire de façon bien visible mais calme, comme une bulle de savon** (D45) : la forme s'**étire puis s'écrase**, tantôt plus large et plus basse, tantôt plus étroite et plus haute (les deux directions ne bougent pas ensemble), s'incline très légèrement et monte et descend de quelques millimètres ; **le texte et le bouton à l'intérieur ne se déforment pas** ; un cycle prend **environ 6 secondes**. L'étoile (ou l'horloge, le « ! », le sablier) du coin suit un rythme un peu différent. **Rien ne clignote, ne rebondit ni ne s'accélère**, y compris sur une bulle rouge (essayez avec l'astuce de la console).
- [ ] **Rien d'autre ne bouge** : les cartes, le texte de la bulle, la barre d'onglets et les marges restent immobiles ; le carrousel des alertes ne défile **jamais tout seul** (seulement quand vous glissez).
- [ ] **Supprimer le mouvement** : Réglages du téléphone > Accessibilité > Améliorations de la visibilité (ou « Options pour les développeurs » > « Échelle de durée d'animation ») > **Supprimer les animations** (ou échelle à « Animation désactivée »). Revenez dans Miff (fermez-la puis rouvrez-la, ou changez d'onglet et revenez) : **la bulle est parfaitement immobile**. Remettez le réglage normal pour la suite.
- [ ] **Pas de mouvement hors du Feed** : passez à l'onglet Profil, ou mettez l'app en arrière-plan, puis revenez : le mouvement reprend doucement, sans à-coup, et ne tourne pas pendant ce temps (batterie : le téléphone ne doit pas chauffer, la consommation de Miff en arrière-plan ne change pas).
- [ ] **Batterie avec la bulle animée** : laissez le Feed ouvert 10 minutes écran allumé, puis comparez dans Réglages > Batterie > Utilisation de la batterie : Miff ne doit pas apparaître parmi les gros consommateurs ; écran éteint, la consommation de Miff ne change pas.
- [ ] **TalkBack** (facultatif) : aucune annonce liée au mouvement ; la bulle est annoncée seulement quand sa couleur change.

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
- [ ] **Ordre de focus TalkBack** (facultatif) : en glissant vers la droite depuis le début de l'écran, TalkBack lit d'abord l'en-tête (photo de profil, logo, Miff), puis la bulle, puis les cartes ; l'action « Actualiser » est proposée dans le menu des actions de la liste (et fait la même chose que le geste).
- [ ] **TalkBack** (facultatif) : une carte se lit comme un tout, dans l'ordre personne, état, temps, puis batterie (« Thomas, à la maison, depuis 12 min, batterie à 85 % ») ; votre photo en haut annonce « Ouvrir mon profil. Ton statut : … » ; avec plusieurs alertes, « Alerte précédente / suivante » sont proposées dans les actions.
- [ ] **Applications récentes** : ouvrez la vue des applications récentes (Android 13) : l'aperçu du Feed **n'affiche pas** de noms ni de photos.

## Partie H — Coût de lecture (facultatif, 5 min, à faire une fois)

Le Feed est le premier gros consommateur de lectures du plan gratuit (50 000 par jour pour tout le projet). Relevez dans la console Firebase > Firestore > Utilisation le nombre de **lectures** avant, puis **ouvrez le Feed une fois à froid** (app fermée puis ouverte), notez le nombre après (le graphique a un délai d'environ 4 minutes). Estimation de l'ouverture à froid pour 4 membres et 3 lieux : **environ 20 lectures** ; un rattachement à chaud : une dizaine ; « Localiser » : **6** (1 + 5 de règles). Reportez vos chiffres à l'assistant : ils servent à recalibrer `docs/dev/surveillance-quotas.md`.


## Partie I — Sélecteur de Miff et paywall « 2e Miff » (5 min, retour du PM du 2026-11-08)

Avec un compte **gratuit** (sans droit Miff+) déjà dans une Miff, puis, si possible, avec un compte **Miff+** (droit accordé à la main dans la console).
- [ ] **Compte gratuit, une seule Miff** : la pastille du nom de la Miff **a une flèche bleue et une ombre** (elle n'est plus plate) et **s'ouvre** au toucher. La liste montre **votre Miff (avec la coche)**, puis **« + Rejoindre une Miff »** et **« + Créer une Miff »**.
- [ ] Touchez **« + Rejoindre une Miff »** : l'écran **« Une seule Miff avec la formule gratuite »** s'ouvre (pas l'écran de saisie du code). Il dit qu'avec Miff+ on peut créer ou rejoindre autant de Miffs que l'on veut, **sans prix**, **sans le mot « Premium »**, sans bouton d'achat, avec « **Miff+ arrive bientôt** » et « Cette option n'est pas encore ouverte. Ta Miff actuelle reste en place. » **« Plus tard »** (ou la croix, ou le geste retour) vous ramène au Feed **sans rien avoir changé**.
- [ ] Refaites avec **« + Créer une Miff »** : même écran, même retour.
- [ ] **Compte Miff+** : « + Rejoindre une Miff » ouvre l'écran de saisie du code, « + Créer une Miff » ouvre l'écran de création, comme avant. Aucun paywall.
- [ ] **Hors connexion** (mode avion) puis rouvrez l'app, ouvrez la liste et touchez « + Créer une Miff » : l'écran de **création** s'ouvre (on ne vous prend pas pour un compte gratuit sur un doute) ; il vous dit calmement ce qui ne va pas si l'envoi est impossible.
- [ ] **Compte sans aucune Miff** : « Pas encore de Miff » (inchangé : deux boutons Créer / Rejoindre, aucun paywall).
- [ ] **TalkBack** (facultatif) : la pastille s'annonce « Miff affichée : … Appuie pour changer de Miff ou en ajouter une » ; les deux entrées se lisent « Rejoindre une Miff » / « Créer une Miff ».
- [ ] Le serveur reste la vraie barrière : même un client modifié ne peut pas ajouter une 2e Miff à un compte gratuit (règles Firestore, tests C8, C9, C11, J10, J11 de `backend/tests/rules`) ; rien à vérifier à la main.

## Partie J — « Enregistrer la photo » dans le plein écran (5 min, retour du PM du 2026-11-08)

Il faut une carte avec une **photo d'update** (le compte B publie une photo depuis l'onglet Update).
- [ ] **Sur la carte**, l'aperçu de la photo n'a **aucun bouton d'enregistrement**. Touchez la photo : le plein écran s'ouvre. En bas, au-dessus de « Voir où c'était », un bouton **« Enregistrer la photo »** (pictogramme flèche vers le bas, contour blanc, pas plus petit qu'un doigt).
- [ ] **Rien n'est enregistré avant le toucher** : ouvrez et fermez la photo plusieurs fois, puis regardez la galerie : aucune photo « Miff » n'est apparue.
- [ ] Touchez **« Enregistrer la photo »** : le bouton passe un instant à « Enregistrement… », puis la phrase calme **« Photo enregistrée sur ton téléphone. »** apparaît. Ouvrez la **Galerie / Google Photos** : un album **« Miff »** contient la photo, nommée **`Miff-AAAA-MM-JJ-HHMMSS.jpg`** (la date et l'heure de l'enregistrement : **ni prénom, ni lieu**). Dans les informations de la photo : **aucun lieu GPS**.
- [ ] **Aucune demande d'autorisation** n'est apparue (ni stockage, ni photos). Dans les réglages Android > Applications > Miff > Autorisations : seulement Position, Appareil photo et Notifications, comme avant.
- [ ] Touchez deux fois très vite : **un seul fichier** est créé pour ce toucher.
- [ ] Mettez le téléphone en **mode avion** après avoir ouvert la photo : l'enregistrement marche encore (la photo est déjà sur le téléphone). Si le stockage est plein : la phrase calme **« On n'a pas pu enregistrer la photo. Tu peux réessayer. »** apparaît, sans alarme.
- [ ] **Ailleurs : pas de bouton** : l'écran Update (aperçu avant publication) et l'aperçu de la carte n'ont pas de bouton d'enregistrement.
- [ ] **TalkBack** (facultatif) : le bouton se lit « Enregistrer cette photo sur ton téléphone » ; la phrase de confirmation est annoncée.
- [ ] **Posture vie privée** (à lire, pas un test) : une photo enregistrée sort de Miff (les 24 heures ne s'appliquent plus à la copie). Les textes légaux doivent être alignés avant publication : `docs/release/update-photo-declarations.md` §15 et `docs/legal/README.md` §7.
## Ce que vous n'avez pas à vérifier (couvert par les tests)

La couleur à 1 h 59 / 2 h 00 / 2 h 01 / 3 h 59 / 4 h 00 / 4 h 01, toutes les branches de la couleur de la Miff, les phrases FR et EN, les 6 réponses de « Localiser », l'absence d'écriture dans Firestore, l'absence de coordonnée dans un journal ou un message, les zones de 48 dp, la parité des textes avec iOS.
