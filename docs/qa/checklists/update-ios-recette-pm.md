# Recette iPhone de l'Update (UPDATE-3 : photo du moment, bouton central coloré, bulle d'incitation) : parcours pour le PM

**Pour qui** : vous, sur votre **iPhone 11 Pro (iOS 26.5.2)**, avec le build **TestFlight** de cette PR, et **deux comptes de test dans la même Miff** : le compte **A** sur l'iPhone et le compte **B** sur l'OPPO (Android). **La photo de A se lit sur l'OPPO** avec l'Update/Feed Android déjà livrés ; **la voir sur iPhone** demande FEED-8 (ticket à venir) : cette recette vérifie donc la **publication**, le **bouton**, la **bulle** et la **caméra** de l'iPhone, et la lecture se fait chez B (Android).
**Durée** : environ 40 minutes, sans sortir de chez vous (le orange et le rouge se fabriquent dans la console, voir l'astuce).

## À savoir avant de commencer (important)

- **Rien de cette version n'a pu être essayé sur un iPhone avant vous** (le poste de développement n'a pas de Mac, le simulateur n'a pas de caméra) : **le viseur, l'orientation de la photo, le reflet du selfie, le double toucher et le verre sombre sur le viseur sont à regarder de près**. Notez tout, avec une capture d'écran.
- **Les règles de la photo localisée (R-PHOTO-LOC) devaient être déployées AVANT d'installer ce build : elles le sont** (déployées par le PM le 2026-11-06, fichier de `main` redéployé le 2026-11-10 ; information du PM, non vérifiable par l'agent ; `docs/dev/deploiement-regles.md`). Sinon **aucune photo ne peut partir** : l'écran dit calmement « Ta photo n'est pas partie. Réessaie dans un instant. » à chaque essai (ce n'est pas un défaut de l'application).
- **Le texte de consentement de la position doit être celui de la version 3 ou plus** (comptes de test qui l'ont accepté). Cette livraison **ne touche à aucun texte légal**.
- **Aucune notification** : la bulle n'existe que **dans l'application ouverte**.
- **Votre position doit déjà être publiée dans la Miff** (le téléphone l'écrit à l'ouverture de l'application, après le consentement et « Toujours » donnés) : juste après une installation neuve, « Publier » peut dire « Position introuvable pour le moment, réessaie dans un instant. » ; réessayez après quelques secondes.
- **Une photo est toujours publiée avec votre position** : il n'y a ni option « sans lieu », ni « Je vais bien » sans photo.
- **Le bouton central est une pastille de couleur, sans aucun dessin dedans** (vert, orange, rouge ; **gris** quand rien n'est connu ou que le partage est arrêté). Il est **maintenant actif dans tous les builds** (avant : absent des builds de diffusion).
- **Un seul écouteur de mon statut est provisoirement dans l'Update** (le Feed iOS n'existe pas encore) : la pastille et la bulle suivent la **Miff choisie** en haut de l'accueil ; il n'y a qu'une Miff côté gratuit.

## Astuce : fabriquer un orange, un rouge ou une zone de danger depuis la console

Console Firebase > projet **Miff** (`miff-3d283`, **jamais l'autre projet**) > Firestore Database > `miffs/{id de la Miff}/status/{uid du compte A}`. Modifiez **à la main** :
- **Orange** : `presence` = `out`, `presenceAt` et `lastUpdateAt` = **il y a 1 h 10**.
- **Rouge d'ancienneté** : `presence` = `out`, `presenceAt` et `lastUpdateAt` = **il y a 3 h**.
- **Zone de danger** : `presence` = `in_risk`, `placeId` = un lieu de la Miff, `presenceAt` = maintenant, `lastUpdateAt` = **il y a 3 h** (donc **avant** l'entrée).
- **Retour au vert** : `presence` = `in_trusted`, `presenceAt` et `lastUpdateAt` = maintenant.
L'iPhone peut réécrire son vrai statut s'il bouge : refaites la modification. La pastille se met à jour **toute seule** (écoute), la couleur est recalculée **chaque minute**.

## Règles à retenir

- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- **Notez toute phrase qui vous semble trop dure ou anxiogène** : la bulle dit « Tu es passé en rouge, indique à la Miff que tout va bien en publiant une update » (texte de vos maquettes, repris tel quel).
- Pour **VoiceOver** (partie H), activez-le dans Réglages > Accessibilité.

## A. Caméra : explication, refus, autorisation (compte A, 8 min)

Première ouverture : Réglages > Miff > **Appareil photo : désactivé** si la boîte a déjà été montrée (ou **désinstallez** puis réinstallez le build : la boîte du système ne se montre qu'**une fois**).
- [ ] Touchez le **bouton rond du milieu** : une **fenêtre blanche à bord bleu** s'ouvre, **avant** toute boîte du système : « Une photo pour tes proches », le texte (qui voit la photo, 24 h, rien dans la galerie), « Continuer » / « Pas maintenant ». **Rien n'a été demandé** au téléphone à ce stade.
- [ ] **Pas maintenant** : retour au Feed, sans reproche.
- [ ] Rouvrez, **Continuer** : la **boîte du système** apparaît avec le texte « Miff utilise la caméra seulement quand tu ouvres l'écran « Donner des nouvelles »… » (rassurant, tutoiement). **Refusez** : écran « La caméra est désactivée » avec **« Ouvrir les réglages »** et « Fermer ». **Il n'y a aucun autre bouton** (pas d'« appareil photo du téléphone ») : **notez-le explicitement, c'est la vérification D31** (critère C3 du ticket).
- [ ] **Ouvrir les réglages** ouvre la page de Miff ; activez l'appareil photo ; revenez à Miff : **le viseur s'ouvre tout seul**.
- [ ] Les **autres** onglets (Feed, Profil) n'ont **jamais** demandé la caméra. Au démarrage de l'application : aucune demande.
- [ ] *(Si vous avez un iPhone d'entreprise ou le contrôle parental)* **caméra restreinte** : « La caméra n'est pas accessible » + « Fermer », **sans** bouton Réglages.
- [ ] Réglages > Miff : seulement **Appareil photo**, **Position**, **Notifications**, Actualisation en arrière-plan (**ni Photos, ni Micro**).

## B. Viseur et prise de vue (compte A, 8 min)

- [ ] **Rien sous la barre** (correctif du bug TestFlight « obturateur sous la barre ») : le **rond de prise de vue est entièrement AU-DESSUS de la barre**, avec un **bon espace** entre le bas du rond et le haut de la barre (environ 48 pt ; jamais collé) ; touchez-le **sur toute sa surface**, y compris son bas : la photo se prend à chaque fois. Le **bouton de bascule** (en haut à droite) est bien sous l'heure, jamais coupé. Sur l'**aperçu**, **Recommencer** et **Publier** sont **au-dessus de la barre** (marge d'au moins 16 pt), touchables partout. Le **viseur reste plein écran SOUS la barre** (l'image passe derrière le verre). Refaites le test avec **Texte plus grand** (Réglages > Accessibilité > Affichage et luminosité) et, si vous en avez un autre, sur un iPhone **à bouton** (SE) ou **grand écran** (Pro Max) : le rond reste toujours au-dessus de la barre.
- [ ] Le viseur est **plein cadre** (sous la barre, qui reste visible), en **selfie** ; **pas de croix**. Le bouton du milieu est **sélectionné**. **Barre sombre à icônes blanches** (verre) : lisible sur une pièce claire **et** sombre ? (**mesure : icône inactive et icône active lisibles, notez le contraste ressenti**).
- [ ] Une aide « Un selfie, la vue, ton repas : comme tu veux. » ; **tant que vous n'avez jamais basculé la caméra par un double toucher**, une astuce « Touche deux fois l'écran pour changer de caméra. » : elle revient à chaque ouverture, **disparaît pour toujours** après votre **premier double toucher** réussi (**y compris après avoir fermé et relancé l'application**) ; le **bouton de bascule** reste.
- [ ] **Bouton de bascule** en haut à droite : caméra arrière, puis selfie. **Deux touchers rapides** sur l'image : même effet. Deux touchers sur le **rond de prise de vue** : **une seule** photo. **Pas de zoom, de filtre, de minuterie.** (Le flash a sa propre section B bis.)
- [ ] Touchez le **rond** : l'**aperçu** apparaît, la photo est **un peu plus petite** que l'écran (marges, coins arrondis, fond sombre), **la croix** en haut à gauche, puis, dessous, « Tes proches verront cette photo pendant 24 h. », la ligne « **Publiée avec ta position. Ceux qui partagent aussi la leur peuvent voir où tu l'as prise.** », le bouton **Recommencer** (flèche) et le bouton bleu **Publier**.
- [ ] **Selfie = reflet du viseur** (décision D37) : en caméra **frontale**, la photo de l'**aperçu** est **identique à ce que montrait le viseur** (comme un miroir : écrivez un mot sur une feuille, il apparaît **à l'envers** dans le viseur **et** sur l'aperçu **et** chez B). En caméra **arrière**, jamais retournée (le mot se lit à l'endroit). **Vérifiez que ni l'une ni l'autre n'est couchée ni la tête en bas** (portrait et paysage).
- [ ] **La croix** et **Recommencer** font **exactement la même chose** : retour au viseur, la photo est oubliée, **rien n'est publié, aucune question**.
- [ ] Ouvrez l'aperçu, verrouillez l'iPhone **5 minutes**, rallumez : « Cette photo date un peu : reprends-en une. », **Publier** est grisé, **Recommencer** marche.
- [ ] Ouvrez l'aperçu, touchez **Profil** puis revenez à l'Update : la photo **n'est plus là**.
- [ ] Mettez l'application en arrière-plan sur le viseur : la **caméra s'arrête** (le **point vert** de la barre d'état disparaît). **Sélecteur d'applications** (balayage depuis le bas) : **ni le viseur ni la photo** ne s'y voient (un écran uni).

## B bis. Flash du viseur (demande du PM, 2026-11-11 ; iPhone 11 Pro, 5 min)

- [ ] **Éteint par défaut** : ouvrez l'Update. Le bouton du flash (éclair **barré**) est **à gauche** du bouton de bascule, même pastille sombre ; en **selfie** il est présent si l'iPhone sait faire le « flash d'écran » (l'iPhone 11 Pro le sait). Aucun flash ni lampe ne s'allume à l'ouverture.
- [ ] Touchez-le : l'éclair n'est plus barré (**activé**). Prenez une photo : l'**écran s'éclaire en blanc** une fraction de seconde (selfie) et la photo est plus lumineuse. Aucune lampe continue.
- [ ] **Caméra arrière** (bouton de bascule) : le flash est **de nouveau éteint** (éclair barré) ; activez-le, prenez une photo : le **flash de l'arrière part** une fois, puis s'éteint.
- [ ] Changez de caméra avec le flash activé (bouton **et** double toucher) : le flash revient **éteint** à chaque changement.
- [ ] Activez le flash, passez en **arrière-plan** (ou verrouillez) puis revenez ; ou changez d'onglet puis revenez : le flash est **éteint**. Fermez et relancez l'app : **éteint** aussi (rien n'est gardé).
- [ ] Le flash ne part **qu'avec la photo**, jamais avant ni après ; la photo, l'aperçu, « Recommencer » et « Publier » se comportent comme sans flash.
- [ ] **Aucune nouvelle autorisation** demandée ; Réglages > Miff : toujours seulement Appareil photo, Position, Notifications.
- [ ] **VoiceOver** : le bouton est annoncé « **Flash désactivé** » puis, après un toucher, « **Flash activé** » (annonce vocale à chaque toucher) ; zone de toucher confortable. **Texte plus grand** : bouton jamais coupé. Anglais : « Flash off » / « Flash on ».
- [ ] Textes **identiques à Android** : « Flash désactivé » / « Flash activé ».

## C. Publier avec la position, et ses cas calmes (compte A, 10 min)

- [ ] **Publier** : « Partage en cours… », puis **retour au Feed** avec le message calme « Photo partagée avec ton cercle » (3 secondes, sans son ni vibration). *(La carte « moi » avec la photo est FEED-8 : pas encore sur iPhone.)*
- [ ] Console Firebase : `miffs/{id}/photos/{uid A}` existe avec `jpeg`, `createdAt`, `expiresAt` (environ **23 h** plus tard), **`lat`, `lng` et `measuredAt`** (ensemble). **Pas** de `deviceId` dans la photo. Le **statut** a `hasPhoto` = `true`, `lastUpdateAt` = maintenant, `updateText` = `null`, un `deviceId`. `presence`, `placeId`, `presenceAt` **inchangés**. `positions/{uid A}` **non modifié** par l'Update.
- [ ] **Poids** : le champ `jpeg` pèse **moins de 150 000 octets** (en général 60 à 110 Ko) ; **notez la taille et la qualité de 5 photos** (selfie lumineux, scène chargée, nuit).
- [ ] **Aucune métadonnée** : téléchargez l'image du champ `jpeg` (ou regardez-la chez B) : pas de date ni de lieu dans les propriétés de l'image.
- [ ] Publiez une **seconde** photo (attendez 30 secondes) : **toujours un seul** document photo, avec la **seconde** image.
- [ ] **Localisation éteinte** (Réglages > Confidentialité > Service de localisation) puis **Publier** : « Ta position n'est pas accessible… » avec **« Ouvrir les réglages »**, **la photo reste à l'écran**, rien n'est publié (console). Rallumez : **Publier** part.
- [ ] **Signal lent** (à l'intérieur d'un bâtiment) : « Position introuvable pour le moment, réessaie dans un instant. » + « Réessayer ». Ce n'est **pas** le même message que ci-dessus.
- [ ] **Hors connexion** (mode avion) : **Publier** : « Pas de connexion pour le moment. Ta photo n'est pas partie… », la photo reste. **Attendez 2 minutes puis réactivez le réseau : rien ne part tout seul** (console : aucune photo nouvelle). Touchez Publier : elle part.
- [ ] **Deux « Publier » à moins de 30 secondes** : « Tu viens de partager une photo. Réessaie dans un instant. ».
- [ ] **Partage arrêté** (Profil > votre Miff > arrêter le partage) : le bouton rond est **gris** ; l'Update affiche « Tu as arrêté de partager… » avec « Reprendre le partage », **sans viseur ni demande de caméra**. *(Une lecture de votre fiche a lieu à l'ouverture, avec une seule Miff.)*
- [ ] **Cas mixte (deux Miffs, Premium)** : partage arrêté dans **l'une** : l'Update s'ouvre ; **Publier** met la photo dans l'autre Miff seulement, sans message d'erreur ; l'arrêt dans **les deux** donne « Tu as arrêté de partager… ». (Critère C5.)
- [ ] **Texte de consentement ancien** (compte qui n'a pas accepté la v3) : « Publier » dit « Pour partager une photo avec ta position, ton accord au nouveau texte est nécessaire. » avec « Revoir et accepter » ; **touchez-le** : l'écran du nouveau texte s'ouvre ; rien n'est publié avant l'accord.

## D. Le bouton du milieu et la bulle d'incitation (compte A, 8 min)

- [ ] **Vert** : pastille **verte**, aucune bulle. **Orange** (console) : pastille **orange** ; **rouge** : **rouge**. **Aucun dessin** dans la pastille. **Gris** si le partage est arrêté.
- [ ] **Orange** : une **bulle orange** au-dessus de la barre, la pointe vers le bouton du milieu : « Tu es passé en orange, indique à la Miff que tout va bien en publiant une update ». **Ni titre, ni bouton, ni croix.** Aucun son, aucune vibration, aucune animation. **Rouge** : bulle rouge.
- [ ] La bulle est aussi sur l'onglet **Profil**, et **absente** sur l'onglet **Update** ; elle **revient** quand vous quittez l'Update.
- [ ] **Rien n'est caché dessous** : faites défiler le Feed et le Profil jusqu'en bas, le dernier élément se voit **au-dessus** de la bulle. Le **bas de la bulle touche le haut de la barre** (la pointe à 4 pt).
- [ ] Touchez la **bulle** : il ne se passe rien. Touchez la **pastille** : l'Update s'ouvre.
- [ ] **Publiez** en orange puis en rouge : la bulle **disparaît** et la pastille repasse au **vert**.
- [ ] **Orange puis arrivée à la maison sans publier** (console : `in_trusted`) : la bulle **disparaît** aussi.
- [ ] **Zone de danger** (console) : pastille **rouge**, bulle rouge ; **publiez** : la bulle **disparaît**, la pastille **reste rouge**.
- [ ] **Deux Miffs (Premium)** : changez de Miff en haut de l'accueil : pastille et bulle suivent **la Miff affichée**.
- [ ] **Relisez à voix haute** la bulle rouge : trop dure pour quelqu'un qui est juste en retard ? (Décision à vous : le texte est celui de vos maquettes.)

## E. La photo vue par l'autre compte (B sur l'OPPO, 5 min)

- [ ] A publie : **B** voit la photo de A dans les **secondes**, avec le bon prénom et la bonne heure ; sa **position** (« Position mesurée… »), **dans le bon sens** (le selfie en reflet comme à l'aperçu).
- [ ] A publie une **seconde** photo : B voit **la seconde**.
- [ ] **Un seul appareil par compte** : connectez A sur un autre téléphone : l'iPhone est déconnecté (message existant) et **ne peut plus publier**.

## F. Nettoyage de la photo expirée (facultatif, avec l'assistant)

- [ ] Publiez, **avancez l'heure de l'iPhone de 25 h**, relancez l'application avec le réseau : la photo de A est **supprimée** dans la console et le statut dit `hasPhoto` = `false`, **sans** lecture du serveur par l'application.
- [ ] **Après déconnexion puis reconnexion du même compte** : la photo est aussi nettoyée (la date a survécu). **Avec un autre compte** sur l'iPhone : rien n'est supprimé.
- [ ] **Après une réinstallation** (avant 25 h : rien n'est supprimé, l'application relit **une fois** votre propre photo pour noter sa date).

## G. Accessibilité (VoiceOver, grand texte, 8 min)

- [ ] **VoiceOver** : le bouton du milieu est annoncé « Donner des nouvelles, tu es en orange » (ou « tout va bien », « en rouge », « Donner des nouvelles » quand rien n'est connu), **sélectionné** sur l'Update. **Rond de prise de vue** : « Prendre la photo », avec l'**action** « Changer de caméra » (rotor Actions). **Bascule** : « Changer de caméra ». L'aperçu annonce « Photo prise. Tu peux la publier ou recommencer. » **Croix** : « Fermer ». **Recommencer**, **Publier** ont leur nom. La **bulle est annoncée une fois** quand elle apparaît (et de nouveau de l'orange au rouge), sans son ni vibration, jamais chaque minute.
- [ ] **Grand texte** (Réglages > Accessibilité > Affichage > Taille du texte, au maximum) : « Recommencer » et « Publier » restent **visibles sans défiler**, la bulle ne dépasse pas **40 % de l'écran** (son texte défile **dans** la bulle), la pastille reste visible.
- [ ] **Réduire les animations** : pas de fondu de la bulle, l'indicateur de partage devient fixe.
- [ ] **Réduire la transparence** : la barre devient **opaque** (blanche, ou sombre sur le viseur).
- [ ] **Langue** : passez le téléphone en anglais, tous les textes de l'Update suivent.

## H. Ce qui ne doit jamais arriver

- [ ] **Aucune photo** dans l'app Photos après vos essais.
- [ ] Aucune demande de **photothèque**, de **micro**.
- [ ] Les **applications récentes** ne montrent ni le viseur ni la photo.
- [ ] Aucun message qui accuse (« échec », « erreur », « tu aurais dû »).
- [ ] La caméra ne s'ouvre **jamais** seule (pas au lancement, pas en revenant d'un autre onglet sans que l'Update soit à l'écran).

## Ce que vous me rapportez

Résultat de **chaque case** (ok / pas ok + capture), les **5 poids de photos**, le **verdict D31** (aucune alternative après le refus de la caméra), le **ressenti du verre sombre sur le viseur**, et tout texte trop dur.
