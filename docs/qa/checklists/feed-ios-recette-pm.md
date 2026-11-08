# Recette iOS du Feed (FEED-5 à FEED-7 : en-tête, bulle, cartes, statuts, Localiser) — parcours pour le PM

**Pour qui** : vous, sur l'**iPhone 11 Pro** (iOS 26.5.2), avec **deux comptes de test dans la même Miff** : le compte **A** sur l'iPhone, le compte **B** sur l'OPPO Find X5 Lite (APK du Feed Android) ou, à défaut, un second compte que l'on modifie à la main dans la console (voir l'astuce).
**Durée** : environ 40 minutes, **sans sortir de chez vous** : pour voir l'orange et le rouge sans attendre 2 h et 4 h, on **modifie l'heure du statut dans la console Firebase** (voir l'astuce). La sortie réelle est facultative.
**Ce qui est testé** : le Feed iOS reproduit le Feed Android déjà validé en recette, **avec les trois ajustements du 2026-11-07** (D42 : plus de « Toi » à côté du prénom ; D43 : votre carte est la **dernière** ; D44 : la batterie n'affiche **plus l'heure**), et la décision D45 sur la bulle (mouvement plus visible, qui déforme la forme).

## Installer (TestFlight)

1. Sur GitHub > Actions > workflow **ios-testflight**, le build de la branche `ios/feed-5-7` est lancé **par vous** (option `archive_mode=signed`, configuration Internal) ; il n'est **jamais** déclenché par la PR.
2. Quand App Store Connect a fini le traitement (quelques minutes), ouvrez **TestFlight** sur l'iPhone, installez la dernière version de **Miff** (désinstallez l'ancienne si elle est signée autrement).
3. **Le Feed est branché sur la coquille d'ONB-7** : il est dans l'onglet de gauche de la barre en verre (la maison avec le petit cœur). Le bouton rond du milieu reste un emplacement « bientôt ». **Cette PR dépend de la PR #102** (coquille) : le build contient les deux.
4. **La photo d'update n'est pas dans cette livraison** (ticket FEED-8, avec l'Update iOS) : aucune carte n'a de photo, c'est normal. **Aucune notification** n'est envoyée et il n'y a pas de cloche : c'est voulu (plan gratuit).
5. Avant de commencer, vérifiez avec l'assistant que les règles Firestore des **statuts** et de la **position** sont bien déployées (sinon « Localiser » dira toujours « pas de position connue » et les statuts ne seront pas lisibles).

## Astuce : fabriquer un orange, un rouge ou une zone de danger depuis la console

Console Firebase > projet **Miff** (`miff-3d283`, jamais l'autre projet) > Firestore Database > `miffs/{id de la Miff}/status/{uid du compte}`. Modifiez **à la main** :
- **Orange** : `presence` = `out`, `presenceAt` et `lastUpdateAt` = **il y a 2 h 10** (type « timestamp »).
- **Rouge d'ancienneté** : `presence` = `out`, `presenceAt` et `lastUpdateAt` = **il y a 4 h 10**.
- **Zone de danger** : `presence` = `in_risk`, `placeId` = l'identifiant d'un lieu de la Miff, `presenceAt` = **il y a 3 h**. **Rouge** : `lastUpdateAt` absent, ou **avant** `presenceAt`, ou **il y a 1 h 10** ; **vert** : `lastUpdateAt` = **il y a 20 min** (donc après l'entrée).
- **Retour au vert** : `presence` = `in_trusted`, `presenceAt` = maintenant.
Les champs restent dans le schéma (ne rajoutez rien). Si un téléphone bouge ou rouvre l'app, il peut réécrire son vrai statut : refaites la modification.

## Règles à retenir

- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- **Notez toute phrase qui vous semble trop dure ou anxiogène** : « zone de danger » est un mot fort, les phrases sont à relire à voix haute.
- Les heures sont **absolues** à partir de l'orange (« à 16 h 40 ») ; avant, elles sont relatives (« il y a 12 min »).
- **Rien n'a été essayé sur un iPhone avant cette recette** : l'en-tête qui se masque, le mouvement de la bulle, le geste « tirer pour actualiser » et le rendu des cartes se jugent ici. Notez tout ce qui saccade ou se décale.

## Partie A — En-tête et sélecteur (4 min)

- [ ] À gauche **votre photo (ou vos initiales)** avec un **anneau de couleur seule** (vert si vous êtes chez vous), au centre le logo, **aucune cloche** (le côté droit est vide).
- [ ] Touchez votre photo : l'onglet **Profil** s'ouvre. Revenez au Feed.
- [ ] Défilez vers le bas puis touchez le **logo** : le fil remonte tout en haut **et** l'en-tête réapparaît.
- [ ] Avec **une seule Miff** : le nom est dans une pastille **plate, sans flèche**, qui ne réagit pas au toucher. Avec **plusieurs Miffs** (compte Premium) : pastille avec flèche ; elle ouvre une **feuille en bas de l'écran** (écart connu : une feuille du système, sans coche) avec vos Miffs, « + Rejoindre une Miff » et « + Créer une Miff ».
- [ ] **L'en-tête se masque quand on descend et revient dès qu'on remonte**, même au milieu de la liste ; aucune carte n'est cachée dessous ni ne laisse de bande vide ; au tout début de la liste il est entier. Faites un **tout petit défilement** depuis le haut : aucune bande vide entre l'en-tête à moitié caché et la bulle.
- [ ] **Toujours visible** : pendant le chargement, **hors connexion** (mode avion), avec **un seul proche** (liste trop courte pour défiler), avec **VoiceOver** activé (il ne se cache jamais), et tant que la feuille du sélecteur est ouverte.
- [ ] **Rotation et onglets** : passez à l'onglet Profil et revenez : l'en-tête est visible, sans saut ni bande vide.
- [ ] **Bas de liste** : faites défiler **jusqu'au bout** : « Ajouter un proche » finit **au-dessus** de la barre en verre, jamais dessous.

## Partie B — Cartes (6 min)

- [ ] **Les cartes des autres viennent d'abord, la vôtre est la dernière** (D43), **sans mention « Toi »** à côté de votre prénom (D42) ; elle porte la ligne « Ce que tes proches voient de toi » et **aucun** bouton.
- [ ] La carte de B : prénom, **étiquette d'état** (ex. « À la maison », sans icône), ligne de temps (« Depuis 12 min »), **batterie « 85 % » seule** (D44 : jamais d'heure à côté ; **elle disparaît au-delà de 6 h** sans mise à jour du statut, ou si elle est absente), puis **trois boutons** : position, message, téléphone.
- [ ] **Message** ouvre **Messages** avec le numéro de B **préparé** (rien n'est envoyé) ; **Téléphone** ouvre le composeur et **iOS demande confirmation** avant l'appel (aucun appel lancé par Miff). Aucune demande d'autorisation « contacts » ni « téléphone ».
- [ ] Un membre sans numéro (ancien compte) : seul le bouton position est visible.
- [ ] Un membre qui vient d'entrer : « Pas encore de nouvelles », étiquette grise, anneau gris. Un membre qui a **arrêté de partager** : « Partage arrêté », jamais rouge.
- [ ] Les cartes ne changent **jamais d'ordre** selon l'urgence.
- [ ] « Ajouter un proche » ouvre la **feuille de partage** (nom, code, lien).
- [ ] **VoiceOver** (facultatif) : une carte se lit comme un tout (« Thomas, À la maison, Depuis 12 min, Batterie à 85 % ») ; la vôtre ajoute « Ce que tes proches voient de toi » ; les boutons sont lus à part (« Localiser Thomas », « Écrire à Thomas », « Appeler Thomas »).

## Partie C — Couleur de la Miff et bulle (10 min, avec l'astuce de la console)

Pour chaque ligne, modifiez le statut de **B** puis regardez le Feed du compte **A** (il se met à jour tout seul).
- [ ] **Vert** : B `in_trusted` : bulle **verte** « Tout va bien » + « Dernière nouvelle il y a … » (l'heure de B, **jamais la vôtre**) ; avec un troisième membre sans statut : « 2 sur 3 ont donné des nouvelles ».
- [ ] **Neutre** : supprimez le statut de B ou mettez `presence` = `unknown` : bulle **grise** « Pas encore de nouvelles » (jamais « Tout va bien »).
- [ ] **Orange** : B `out` il y a 2 h 10 : bulle **orange** « B a quitté la maison à 15 h 50 » (heure absolue), **sans** point d'exclamation, bouton « Envoyer un message » (ouvre Messages).
- [ ] **Rouge d'ancienneté** : B `out` il y a 4 h 10 : bulle **rouge** « Pas de nouvelles de B depuis 14 h 00 ». Aucun son, aucune animation d'alarme.
- [ ] **Zone de danger** : B `in_risk` : bulle rouge « B est à {nom du lieu} » + « Depuis … » + « Une zone définie par ta Miff » ; carte « Dans une zone de danger ». **Nulle part** « zone à risque ».
- [ ] **Vous seul(e) en orange ou en rouge** (modifiez votre statut) : la bulle vous parle (« Tu as quitté la maison à … », « Tes proches n'ont pas eu de tes nouvelles depuis … », « Tu es dans une zone de danger de ta Miff »), **sans** bouton « Envoyer un message ».
- [ ] **Plusieurs alertes** : des **pastilles** sous la bulle (la page courante est **allongée**), le **rouge d'abord**, on change de page en **balayant** ; **rien ne défile tout seul**. Avec une seule alerte : pas de pastilles.
- [ ] **OBLIGATOIRE, VoiceOver : atteindre la 2e alerte sans balayer** (deux alertes en place). Activez VoiceOver, placez-vous sur la bulle, puis sur l'élément « **Page 1 sur 2** » (sous la bulle, à la place des pastilles) : **glissez un doigt vers le haut** : VoiceOver annonce « Page 2 sur 2 » et la bulle montre la 2e alerte ; vers le bas : retour à la 1re. L'élément propose aussi les actions « Alerte précédente » / « Alerte suivante » (rotor Actions). Notez si l'élément n'est pas atteignable : c'est un défaut à corriger.
- [ ] **Couleur qui avance seule** : mettez B `out` il y a **58 min** et **attendez 2 à 3 minutes sans toucher l'écran** : la carte passe de vert à orange **sans rien recharger**.
- [ ] Le texte est **lisible** sur chaque couleur ; **aucun pictogramme** sur l'anneau ni dans l'étiquette de la carte (la couleur est le seul signal, les mots restent) ; la bulle garde son petit dessin décoratif.

## Partie C bis — Mouvement de la bulle (3 min, **à juger**)

- [ ] Regardez la bulle quelques secondes : elle **s'écrase et s'étire doucement, de façon visible mais calme** (la largeur et la hauteur ne suivent pas le même rythme : la forme se déforme comme une goutte), s'incline très légèrement et monte et descend de quelques points ; un cycle prend **6 secondes**. Seule la **forme** est déformée ; le **texte et le bouton** ne sont pas déformés (ils suivent seulement la montée et la descente). Rien ne clignote ni ne rebondit, **même sur une bulle rouge**.
- [ ] **Trop ou pas assez ?** Dites si l'amplitude vous convient : ce sont des constantes nommées (`BubbleMotion.squashX`, `squashY`, `tiltDegrees`, `driftPoints`, `blobPeriodSeconds`) qu'on règle en une ligne.
- [ ] **Réduire les animations** : Réglages de l'iPhone > Accessibilité > Mouvement > **Réduire les animations**. Rouvrez le Feed : **la bulle est parfaitement immobile**. Remettez le réglage.
- [ ] **Pas de mouvement hors du Feed** : passez au Profil ou mettez l'app en arrière-plan puis revenez : le mouvement reprend doucement et ne tourne pas pendant ce temps (l'iPhone ne doit pas chauffer).

## Partie D — Tirer pour actualiser (4 min)

- [ ] En haut de la liste, tirez vers le bas : les cartes se mettent à jour (changez un statut dans la console juste avant) ; un petit indicateur **bleu** apparaît **sous l'en-tête** pendant la lecture. Au milieu de la liste, tirer ne fait rien d'autre que remonter.
- [ ] **Limite des 15 secondes** : tirez deux fois à quelques secondes d'intervalle : la **seconde** fois s'arrête aussitôt sans rien recharger. Après 15 secondes, tirer relit.
- [ ] **Sans réseau** : mode avion, tirez : tout s'arrête **tout de suite**, le bandeau « Dernières informations de … » reste, aucun message d'erreur.
- [ ] **Réseau très mauvais** : l'indicateur s'arrête au bout de **8 secondes au plus**, avec « Dernières informations de … », jamais une phrase alarmante.
- [ ] **VoiceOver** : « Actualiser » est proposée dans les actions de la liste ; l'iPhone annonce « Actualisation en cours » puis « Informations actualisées ».
- [ ] **Changer de Miff pendant une actualisation** (Premium, deux Miffs) : la seconde Miff ne montre **jamais** les statuts de la première.

## Partie E — « Localiser » (5 min)

Prérequis : B partage et a une position (console : `positions/{uid B}` existe) ; vous partagez aussi (réciprocité).
- [ ] Touchez le bouton position de B : un **indicateur** tourne un instant, puis une **feuille** s'ouvre : « Dernière position connue de B », « Mesurée à … (il y a … min). Ce n'est pas une position en direct. », la ligne « Vous partagez tous les deux… », « Ouvrir dans les cartes ». **Aucune coordonnée n'est écrite à l'écran.**
- [ ] « Ouvrir dans les cartes » ouvre **Plans** sur un **point**, **sans nom de personne ni de lieu**. (Si Plans affiche un titre bizarre à côté de l'épingle, notez-le : c'est le seul point incertain.)
- [ ] Fermez la feuille (bouton ou glissement) : plus rien n'est affiché ; rouvrir fait une **nouvelle** lecture (l'heure « Mesurée à » est celle de la console).
- [ ] **Plus de 24 h** : `measuredAt` de B **il y a 30 h** : « Elle date d'hier à … : elle n'est plus affichée. », **sans** bouton de carte.
- [ ] **Pas de position** : supprimez `positions/{uid B}` : « B n'a pas encore de position connue. »
- [ ] **Hors connexion** : mode avion : « Pas de connexion pour le moment. Réessaie dès que le réseau est revenu. » Aucun mot « erreur » ni « quota » dans aucun cas.
- [ ] **Refus** : arrêtez **votre** partage (écran de la Miff > Partager mon statut) : le bouton position a disparu.

## Partie F — Hors connexion et états du Feed (6 min)

- [ ] **Mode avion** avec tout vert : un bandeau gris « Dernières informations de 16 h 02 » (icône de wifi barré). **Jamais de vert sans date.** Remettez le réseau : il disparaît. Ce n'est **pas** l'écran « Tu n'as plus accès à la Miff ».
- [ ] Mode avion avec une alerte orange : la bulle ajoute « Informations de … : reconnecte-toi pour les mettre à jour. »
- [ ] **Vous arrêtez de partager** : la bulle grise dit « Tu as arrêté de partager. Reprends le partage pour voir les nouvelles de tes proches. » avec « Reprendre le partage » (il ouvre l'écran de la Miff, où se trouve la question). Les cartes des proches : « Nouvelles masquées », **plus de bouton position** ; votre carte : « Partage arrêté ». Reprenez : tout revient.
- [ ] **En attente de ton accord** (compte qui partage avec l'ancien texte de consentement) : au lancement, l'écran de renouvellement ; **« Pas maintenant »** : le Feed s'ouvre avec en tête une carte « En attente de ton accord » et « Revoir et accepter » (qui ramène l'écran de renouvellement), la bulle est grise, les cartes des autres disent « Nouvelles masquées ».
- [ ] **Premier chargement** (désinstallez/réinstallez) : des formes grises avec « Chargement des nouvelles… », **aucun** « Tout va bien » ni couleur avant la lecture.
- [ ] **Pas de fausse alerte au réveil** : B en orange (console), ouvrez le Feed, **puis** remettez B au vert et rouvrez l'app (fermée puis ouverte) : l'orange ne doit **pas** s'afficher un instant avant le vert.
- [ ] **Miff sans proche** : bulle grise « Personne d'autre dans cette Miff pour l'instant », votre carte, « Ajouter un proche ».
- [ ] **« Partage de ton statut en cours… »** : supprimez **votre** statut dans la console puis revenez au Feed : un bandeau bleu pâle avec un indicateur, **jamais** une erreur ; il disparaît quand le statut se republie.

## Partie G — Langue, taille de texte, VoiceOver, aperçu des applications (4 min)

- [ ] **Langue** : Réglages de l'iPhone > Miff > Langue > English : tous les textes passent en anglais (« All good », « Thomas left home at 4:40 PM »), puis revenez.
- [ ] **Grande taille de texte** (Réglages > Accessibilité > Affichage et luminosité > Taille du texte, au maximum, puis Texte plus grand) : cartes lisibles (rien de coupé), boutons atteignables, la bulle grandit en hauteur sans tronquer son titre.
- [ ] **VoiceOver** : l'en-tête est lu en premier (photo de profil « Ouvrir mon profil. Ton statut : … », logo, Miff), puis la bulle, puis les cartes ; la bulle n'est annoncée que **quand sa couleur change**.
- [ ] **Sélecteur d'applications** (balayage depuis le bas, ou double toucher du bouton) : l'aperçu du Feed **n'affiche ni noms ni photos** (le logo sur le fond de l'app).

## Partie H — Coût de lecture (facultatif, 5 min)

Relevez dans la console Firebase > Firestore > Utilisation le nombre de **lectures** avant, puis **ouvrez le Feed une fois à froid** (app fermée puis ouverte), notez le nombre après (le graphique a un délai d'environ 4 minutes). Estimation pour 4 membres et 3 lieux : **environ 20 lectures** ; un retour d'onglet : une dizaine ; « Localiser » : **6** ; « Actualiser » : environ 20. **Changer d'onglet détache les écoutes** (c'est voulu : rien n'écoute sur le Profil) : y revenir coûte une dizaine de lectures de règles. **À savoir** : « Actualiser » relit tout (environ 20 lectures) sans garde côté serveur (seule la limite de 15 s de l'application la borne ; Firebase App Check est à prévoir avant la bêta externe) ; un retour d'onglet ne coûte qu'une dizaine de lectures **sous 30 minutes** (au-delà, c'est le coût d'une ouverture à froid, environ 20). Voir `docs/dev/surveillance-quotas.md`. Reportez vos chiffres à l'assistant (recalibrage de ce document).

## Partie R — Retours du 2026-11-08 (étiquettes, bulle centrée, seuils)

- [ ] **Étiquettes** : dans un lieu **Maison** « À la maison », **Travail** « Au travail », **École** « À l'école », **Sport** « Au sport », **Chez un proche** « Chez un proche » ; **Loisir** et **Autre** : le **nom du lieu seul** (sans « À » devant). Un nom long (40 signes) tient sur deux lignes au plus. En anglais : At home / At work / At school / At the gym / At a loved one's.
- [ ] **Bulle centrée** : dans les **3 couleurs** (vert, orange, rouge) et sur **toutes les pages** du carrousel d'alertes, le texte et le bouton sont **au milieu de la tache**, à gauche comme à droite et en haut comme en bas, **pendant que la tache bouge**. Refaites-le avec une **grande taille de texte** (Réglages > Accessibilité > Affichage et luminosité > Taille du texte) : le texte reste centré et ne touche pas le disque du pictogramme en bas à droite.
- [ ] **Seuils** : sorti d'un lieu depuis moins de 2 h : **vert** ; au-delà de 2 h : **orange** ; au-delà de 4 h : **rouge**. Une update plus récente repart du vert. **Zone de danger** : **rouge dès l'entrée** (même avec une update faite juste avant) ; si vous publiez une update **après** l'entrée, la couleur passe **verte pendant 1 h** puis redevient rouge ; une nouvelle update redonne 1 h de vert. Jamais d'orange en zone de danger.
- [ ] **Détail d'un lieu de danger** (Lieux > un lieu de danger) : le texte d'information dit « …tu n'as pas donné de nouvelles depuis plus d'une heure, ta Miff voit ton statut en rouge. ».

## Points ouverts pour vous (à noter à la fin)

- [ ] L'amplitude de la bulle (D45) vous convient-elle, comparée à celle de l'OPPO ?
- [ ] Le sélecteur de Miff en **feuille du système** (sans coche) vous convient-il ?
- [ ] Les trois clés de texte devenues inutiles (`feed_card_you`, `feed_battery_dated`, `feed_battery_dated_a11y`) seront retirées par la PR Android #105 ; rien à faire ici.

## Ce que vous n'avez pas à vérifier (couvert par les tests)

La couleur à 1 h 59 / 2 h 00 / 2 h 01 / 3 h 59 / 4 h 00 / 4 h 01 (et zone de danger à 59 min 59 s / 1 h 00 / 1 h 00 min 01 s), les étiquettes de chaque catégorie, la géométrie de la bulle, toutes les branches de la couleur de la Miff, l'ordre (moi en dernier), les phrases FR et EN (lues dans le vrai catalogue), les six réponses de « Localiser », l'absence d'écriture dans Firestore, l'absence de coordonnée dans une description, les liens `sms:` / `tel:` (numéro vérifié), la logique de l'en-tête, les constantes du mouvement de la bulle, la batterie masquée à 6 h, la parité des clés de texte avec Android.
