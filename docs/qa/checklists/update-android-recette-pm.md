# Recette Android de l'Update (UPDATE-1, UPDATE-2, FEED-4, avec la localisation de la photo) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), avec **deux comptes de test dans la même Miff** : le compte **A** sur l'OPPO et le compte **B** sur l'iPhone 11 Pro (la photo se **lit** sur iOS seulement avec FEED-8, ticket à venir : en attendant, le compte B est un **second compte sur l'OPPO** ou un second téléphone Android, on se déconnecte et on se reconnecte entre les deux).
**Durée** : environ 45 minutes, **sans sortir de chez vous** : le orange et le rouge se fabriquent dans la console (astuce plus bas).

## À savoir avant de commencer (important)

- **Les règles de la photo localisée (R-PHOTO-LOC) doivent être déployées AVANT d'installer cette version** (sur votre « oui » explicite, `docs/dev/deploiement-regles.md`). Tant qu'elles ne le sont pas, **aucune photo ne peut partir** (le serveur refuse les trois clés de la position) : l'écran dit calmement « Ta photo n'est pas partie. Réessaie dans un instant. » à chaque essai. Ce n'est pas un défaut de l'application. Dites-le à l'assistant avant de commencer.
- **Le texte de consentement de la position doit être celui de la version 3 ou plus** (c'est le cas de vos comptes de test s'ils ont accepté le texte v3). Le texte sera amendé pour citer la photo localisée par ailleurs : cette livraison **ne touche à aucun texte légal**.
- L'APK à installer est celui **signé par la CI** (artefact `miff-debug-apk` de la PR), **pas** un APK compilé à la main. Désinstallez l'ancienne version d'abord.
- **Aucune notification** n'est envoyée : la bulle d'incitation n'existe que **dans l'application ouverte**.
- **Votre position doit déjà être publiée dans la Miff** (le téléphone l'écrit à l'ouverture de l'application, une fois le consentement et la localisation « Toujours » donnés) : juste après une installation neuve, « Publier » peut dire « Position introuvable pour le moment, réessaie dans un instant. » le temps que ce soit fait ; réessayez après quelques secondes.
- **Une photo est toujours publiée avec votre position** (décision du 2026-11-06) : il n'y a pas d'option « sans lieu » ni de « Je vais bien » sans photo.
- **Le bouton central est une pastille de couleur** (vert, orange, rouge, gris quand rien n'est connu), **sans aucun dessin dedans**. La barre est celle d'aujourd'hui (le verre est le ticket TABBAR-GLASS).

## Astuce : fabriquer un orange, un rouge ou une zone de danger depuis la console

Console Firebase > projet **Miff** (`miff-3d283`, jamais l'autre projet) > Firestore Database > `miffs/{id de la Miff}/status/{uid du compte}`. Modifiez **à la main** :
- **Orange** : `presence` = `out`, `presenceAt` et `lastUpdateAt` = **il y a 1 h 10**.
- **Rouge d'ancienneté** : `presence` = `out`, `presenceAt` et `lastUpdateAt` = **il y a 3 h**.
- **Zone de danger** : `presence` = `in_risk`, `placeId` = un lieu de la Miff, `presenceAt` = maintenant, `lastUpdateAt` = **il y a 3 h** (donc **avant** l'entrée).
- **Retour au vert** : `presence` = `in_trusted`, `presenceAt` et `lastUpdateAt` = maintenant.
Si le téléphone bouge ou si vous rouvrez l'application, elle peut réécrire son vrai statut : refaites la modification.

## Règles à retenir

- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- **Notez toute phrase qui vous semble trop dure ou anxiogène** : la bulle dit « Tu es passé en rouge, indique à la Miff que tout va bien en publiant une update » (texte de vos maquettes, repris tel quel).
- Pour le **TalkBack** (partie H), activez-le dans les réglages d'accessibilité du téléphone.

## A. Caméra : explication, refus, autorisation (compte A, 8 min)

Première ouverture : réglages du téléphone > Applications > Miff > Autorisations > **Appareil photo : Refuser** (ou installation neuve).
- [ ] Touchez le **bouton rond du milieu** de la barre : une **fenêtre blanche à bord bleu** s'ouvre, **avant** toute boîte du système : « Une photo pour tes proches », le texte (qui voit la photo, 24 h, rien dans la galerie), « Continuer » / « Pas maintenant ». **Rien n'a été demandé** au téléphone à ce stade.
- [ ] **Pas maintenant** : retour au Feed, sans reproche.
- [ ] Rouvrez, **Continuer** : la boîte du système apparaît. **Refusez** : écran « On réessaie ? » (« Tu pourras donner de tes nouvelles en photo dès que la caméra sera autorisée. »), boutons « Réessayer » et « Pas maintenant ».
- [ ] **Réessayer**, **refusez** encore : « La caméra est désactivée » avec **« Ouvrir les réglages »** et « Fermer ». **Il n'y a pas** de bouton « appareil photo du téléphone » (voir l'écart n° 4 de la PR : Android l'interdit quand l'application déclare la caméra sans l'avoir obtenue).
- [ ] **Ouvrir les réglages** ouvre la page de Miff ; autorisez l'appareil photo ; revenez : **le viseur s'ouvre tout seul**.
- [ ] Les **autres** onglets (Feed, Profil) n'ont **jamais** demandé la caméra. Au démarrage de l'application : aucune demande.
- [ ] **Photo de profil par l'appareil photo du téléphone** (Profil > votre photo > « Prendre une photo », et Réglages > Mes informations), caméra **non autorisée** : la boîte du système apparaît **avant** l'appareil photo (nouveau : Android l'exige dès que l'application déclare la caméra) ; **autorisez** : l'appareil photo du téléphone s'ouvre, la photo prise est recadrée comme avant ; **refusez** : un message calme et le choix dans la galerie reste possible. Caméra déjà autorisée : l'appareil photo s'ouvre directement, sans boîte.

## B. Viseur et prise de vue (compte A, 8 min)

- [ ] Le viseur est **plein cadre** (au-dessus de la barre, qui reste visible), en **selfie** ; **pas de croix**. Le bouton rond du milieu de la barre est **sélectionné**. Les icônes de la barre d'état sont blanches.
- [ ] Une aide « Un selfie, la vue, ton repas : comme tu veux. » ; **tant que vous n'avez jamais basculé la caméra par un double toucher**, une astuce « Touche deux fois l'écran pour changer de caméra. » : elle revient à chaque ouverture, **disparaît pour toujours** après votre **premier double toucher** réussi (changement de caméra), **y compris après avoir fermé et rouvert l'application** ; le **bouton de bascule** reste. Réinstallez l'application pour la revoir.
- [ ] **Bouton de bascule** en haut à droite : caméra arrière, puis selfie. **Deux touchers rapides** sur l'image : même effet. Deux touchers sur le **rond de prise de vue** : **une seule** photo.
- [ ] **Pas de flash, de zoom, de filtre, de minuterie.**
- [ ] Touchez le **rond** : l'**aperçu** apparaît, la photo est **un peu plus petite** que l'écran (marges, coins arrondis, fond sombre), **la croix** en haut à gauche, puis, dessous, « Tes proches verront cette photo pendant 24 h. », la ligne avec l'épingle « **Publiée avec ta position. Ceux qui partagent aussi la leur peuvent voir où tu l'as prise.** », le bouton rond **Recommencer** et le bouton bleu **Publier**.
- [ ] **Selfie = reflet du viseur** : en caméra **frontale**, la photo de l'**aperçu** est **identique à ce que montrait le viseur** (comme un miroir : écrivez un mot sur une feuille, il apparaît **à l'envers** dans le viseur **et** sur l'aperçu, la photo publiée, la carte du Feed et le plein écran ; votre grain de beauté à gauche dans le viseur est à gauche sur la photo). En caméra **arrière**, la photo n'est **jamais** retournée (le mot se lit à l'endroit). Vérifiez que ni l'une ni l'autre n'est couchée ni la tête en bas (ColorOS).
- [ ] **La croix** et **Recommencer** font **exactement la même chose** : retour au viseur, la photo est oubliée, **rien n'est publié, aucune question**.
- [ ] Ouvrez l'aperçu, laissez le téléphone **5 minutes**, rallumez : « Cette photo date un peu : reprends-en une. », **Publier** est grisé, **Recommencer** marche.
- [ ] Ouvrez l'aperçu, touchez **Profil** puis revenez à l'Update : la photo **n'est plus là** (on repart du viseur).
- [ ] Mettez l'application en arrière-plan sur le viseur : la **caméra s'arrête** (le voyant vert de l'OPPO disparaît).

## C. Publier avec la position, et ses cas calmes (compte A, 10 min)

- [ ] **Publier** : « Partage en cours… », puis **retour au Feed** avec le message calme « Photo partagée avec ton cercle » (3 secondes, sans son ni vibration). **Votre carte (en haut) montre la photo** (une fenêtre de 108 px de haut), « Photo de 16 h 05 » ; touchez-la : **plein écran** (voir E).
- [ ] Console Firebase : `miffs/{id}/photos/{uid A}` existe avec `jpeg` (octets), `createdAt`, `expiresAt` (environ **23 h** plus tard), **`lat`, `lng` et `measuredAt`** (ensemble). **Pas** de `deviceId` dans la photo. Le **statut** a `hasPhoto` = `true`, `lastUpdateAt` = maintenant, `updateText` = `null`, un `deviceId`. `presence`, `placeId`, `presenceAt` **inchangés**. `positions/{uid A}` **non modifié** par l'Update.
- [ ] **Poids** : la taille du champ `jpeg` dans la console est **inférieure à 150 000 octets** (en général 60 à 110 Ko).
- [ ] Publiez une **seconde** photo (attendez 30 secondes) : il n'y a **toujours qu'un seul** document photo, avec la **seconde** image.
- [ ] **Localisation éteinte** : coupez la **localisation du téléphone** (volet rapide) puis **Publier** : « **Ta position n'est pas accessible : la localisation du téléphone est éteinte, ou Miff n'a plus le droit de l'utiliser…** » avec **« Ouvrir les réglages »**, **la photo reste à l'écran**. Rien n'est publié (console). Même message si vous retirez l'autorisation de position dans les réglages. Rallumez, **Publier** : la photo part.
- [ ] **Signal lent** (difficile à provoquer : à l'intérieur d'un bâtiment, localisation allumée) : « **Position introuvable pour le moment, réessaie dans un instant.** » avec « Réessayer ». Ce n'est **pas** le même message que ci-dessus.
- [ ] **Hors connexion** : passez en **mode avion**, **Publier** : « Pas de connexion pour le moment. Ta photo n'est pas partie : réessaie dès que le réseau est revenu. », la photo reste. **Attendez 2 minutes puis réactivez le réseau : rien ne part tout seul** (console : aucune photo nouvelle). Touchez Publier : elle part. *(Critère U3 nuancé : aucune écriture n'est mise en file ; seul un envoi **déjà parti** peut aboutir après un délai.)*
- [ ] **Réseau très lent** (facultatif) : si le serveur ne répond pas en 20 secondes, l'écran dit « **On n'a pas pu confirmer. Regarde dans ton Feed…** » (et **non** « Ta photo n'est pas partie ») avec « Voir mon Feed » ; regardez le Feed et la console : la photo y est peut-être.
- [ ] **Deux « Publier » à moins de 30 secondes** : « Tu viens de partager une photo. Réessaie dans un instant. ».
- [ ] **Partage arrêté** (Profil > votre Miff > arrêter le partage) : le bouton rond du milieu est **gris** ; l'Update affiche « Tu as arrêté de partager. Reprends le partage pour donner de tes nouvelles. » avec « Reprendre le partage », **sans viseur ni demande de caméra**.
- [ ] **Cas mixte (deux Miffs, Premium)** : arrêtez le partage dans **l'une des deux** seulement : l'Update reste ouvert (pas d'écran « partage arrêté », on ne le sait qu'avec une seule Miff) ; **Publier** met la photo dans l'autre Miff seulement (console) avec le message de succès habituel (la Miff où le partage est arrêté est simplement ignorée) ; l'arrêt dans **les deux** donne « Tu as arrêté de partager… » avec « Reprendre le partage ».
- [ ] **Texte de consentement ancien** (compte qui n'a pas accepté la v3) : « Publier » dit « Pour partager une photo avec ta position, ton accord au nouveau texte est nécessaire. » avec « Revoir et accepter » ; rien n'est publié.

## D. Le bouton du milieu et la bulle d'incitation (compte A, 8 min)

- [ ] **Vert** (vous êtes chez vous) : pastille **verte**, aucune bulle. **Orange** (console) : pastille **orange** ; **rouge** : pastille **rouge** ; l'anneau de votre photo en haut à gauche prend la même couleur. **Aucun dessin** (ni coche, ni horloge, ni point d'exclamation) dans la pastille. **Gris** si le partage est arrêté.
- [ ] **Orange** : une **bulle orange** au-dessus de la barre, la petite pointe vers le bouton du milieu : « Tu es passé en orange, indique à la Miff que tout va bien en publiant une update ». **Ni titre, ni bouton, ni croix.** Aucun son, aucune vibration, aucune animation. **Rouge** : bulle rouge, « Tu es passé en rouge, … ».
- [ ] La bulle est aussi sur l'onglet **Profil**, et **absente** sur l'onglet **Update**. Elle **revient** quand vous quittez l'Update.
- [ ] **Rien n'est caché dessous** : faites défiler le Feed et le Profil jusqu'en bas, la dernière carte et « Ajouter un proche » se voient **au-dessus** de la bulle.
- [ ] Touchez la **bulle** : il ne se passe rien (elle n'est pas un bouton). Touchez la **pastille du milieu** : l'Update s'ouvre.
- [ ] **Publiez** une photo en orange puis en rouge : la bulle **disparaît** et la pastille repasse au **vert**.
- [ ] **Orange puis arrivée à la maison sans publier** (console : `in_trusted`) : la bulle **disparaît** aussi.
- [ ] **Zone de danger** (console, voir l'astuce) : la pastille reste **rouge**, la bulle rouge est là ; **publiez** : la bulle **disparaît**, mais la pastille **reste rouge** (une photo ne ramène pas au vert dans une zone de danger).
- [ ] **Deux Miffs (Premium)** : changez de Miff dans le Feed : la pastille et la bulle suivent **la Miff affichée**. Publier met la photo dans **toutes** vos Miffs où vous partagez.
- [ ] **Relisez à voix haute** la bulle rouge : est-elle trop dure pour quelqu'un qui est juste en retard ? (Décision à vous : le texte est celui de vos maquettes.)

## E. Le Feed et la photo en plein écran (compte B regarde la photo de A, 8 min)

Compte **B** : il doit avoir **publié sa propre position** (ouvrir l'application avec la localisation « Toujours » suffit après quelques secondes) pour voir la photo de A.
- [ ] La carte de A montre la photo (un **carré** aussi large que la carte, **centré sur le milieu** de la photo, la photo complète au toucher), « Photo de 16 h 05 » en pastille blanche.
- [ ] Touchez la photo : **plein écran, fond noir**, la photo **entière** (jamais rognée), « Photo de A · 16 h 05 », « Position mesurée à 16 h 02. », bouton **« Voir où c'était »** (ouvre les cartes du téléphone sur les coordonnées seules, **sans nom**), bouton de fermeture en haut à gauche (48 px), **pincer** pour zoomer, **retour système** pour fermer.
- [ ] Une photo **expirée** (modifiez `expiresAt` à « il y a 1 h » dans la console) : **aucune photo, aucun message**. **Rien n'est supprimé** (console : le document photo est toujours là).
- [ ] **Réciprocité** : sur un compte qui **n'a pas publié sa position**, la photo de A est remplacée par une tuile bleu pâle avec un cadenas : « **Partage ta position pour voir cette photo.** » (jamais le mot « erreur »). **Rien n'est supprimé** de la photo de A.
- [ ] Le Feed ne relit **pas** la photo à chaque ouverture : fermez et rouvrez l'application, ouvrez le Feed deux fois de suite : dans la console Firebase (Usage), pas de lecture de photo en plus pour la **même** photo. (Contrôle facultatif, à faire avec l'assistant.)

## F. Photo sur deux appareils (A et B, 5 min)

- [ ] A publie une photo ; **B** (autre appareil) la voit dans les **secondes** qui suivent, avec le bon prénom et la bonne heure.
- [ ] A publie une **seconde** photo : B voit **la seconde** (pas l'ancienne en copie).
- [ ] **Un seul appareil par compte** : connectez le compte A sur un second téléphone : le premier est déconnecté (message existant) et **ne peut plus publier**.

## G. Nettoyage de la photo expirée (facultatif, avec l'assistant)

- [ ] **Après une déconnexion puis reconnexion du même compte** : publiez, déconnectez-vous (Réglages > Mon compte), **avancez l'heure de 25 h**, reconnectez-vous avec le même compte : la photo est supprimée (la date a survécu à la déconnexion). Avec **un autre compte** sur le téléphone : rien n'est supprimé.
- [ ] **Après une réinstallation** : publiez, **désinstallez et réinstallez** l'application, reconnectez-vous, **avancez l'heure de 25 h** : le Feed affiche la carte, la photo est supprimée au lancement (la date de la dernière mise à jour remplace la date perdue). Avant 25 h : rien n'est supprimé (l'application relit **une fois** votre propre photo pour noter sa date, 2 lectures de règles).

- [ ] Publiez une photo, **avancez l'heure du téléphone de 25 h** (ou attendez), relancez l'application avec le réseau : la photo de A est **supprimée** dans la console et le statut dit `hasPhoto` = `false`, **sans** lecture du serveur par l'application.

## H. Accessibilité (TalkBack, grande police, 8 min)

- [ ] **TalkBack** : le bouton du milieu est annoncé « Donner des nouvelles, tu es en orange » (ou « tout va bien », « en rouge », « Donner des nouvelles » quand rien n'est connu). Le **rond de prise de vue** : « Prendre la photo ». **Bascule** : « Changer de caméra » ; sur l'image, l'**action** « Changer de caméra » existe dans le menu d'actions. L'aperçu annonce « Photo prise. Tu peux la publier ou recommencer. » **La croix** : « Fermer ». **Recommencer**, **Publier** ont leur nom. La **bulle est annoncée** une fois quand elle apparaît (et de nouveau quand elle passe de l'orange au rouge), sans son ni vibration ; le **bouton qui suit** est la pastille.
- [ ] **Bulle plafonnée** : à la plus grande police, la bulle ne dépasse pas **40 % de la hauteur de l'écran** ; son texte défile **dans** la bulle ; la pastille reste visible.
- [ ] **Grande police** (réglages > Affichage > taille de la police, au maximum) : les boutons « Recommencer » et « Publier » restent **visibles sans défiler** et à 48 px au moins, la bulle ne cache pas la pastille.
- [ ] **Réduire les animations** activé : pas d'animation de la bulle.
- [ ] **Après la fusion avec la barre en verre** : sur le Feed et sur le Profil, le **bas de la bulle touche le haut de la barre** (ni dessous, ni en l'air) ; la **dernière carte du Feed** et le **dernier élément du Profil** défilent **au-dessus** de la bulle ; juste après une publication, le message « publié » est **au-dessus** de la bulle, jamais dessus.
- [ ] **Verre de la barre** : sur les fenêtres d'explication de l'Update (appareil photo), la barre est **claire** (icônes foncées) ; elle est **sombre** (icônes blanches) seulement sur le viseur et l'aperçu.
- [ ] **À observer (comportement voulu)** : la pastille du bouton central et la bulle ne se mettent à jour que sur l'onglet **Feed** (une seule écoute) ; sur le Profil ou l'Update elles peuvent être périmées jusqu'au retour sur le Feed.

## I. Ce qui ne doit jamais arriver

- [ ] Aucune demande de **stockage**, de **photothèque**, de **micro** (réglages > Applications > Miff > Autorisations : seulement **Appareil photo**, **Position**, **Notifications**).
- [ ] **Aucune photo** dans la galerie du téléphone après vos essais, et aucun fichier de photo (le gestionnaire de fichiers de l'OPPO ne montre rien de nouveau).
- [ ] Les **applications récentes** (carré en bas) ne montrent ni le viseur ni la photo (écran masqué).
- [ ] Aucun message qui accuse (« échec », « erreur », « tu aurais dû »).
