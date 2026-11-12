# Recette iOS des notifications (NOTIF-P0) — parcours pour le PM

**Pour qui** : vous, sur l'**iPhone 11 Pro** (iOS 26.5.2), avec un compte de test dans une Miff (un second compte, sur l'OPPO ou modifié à la main dans la console, sert pour N3 et N4).
**Ce qui est testé** : l'autorisation des notifications, Réglages > Notifications et le centre de notifications. Les rappels eux-mêmes arrivent par étapes : **chaque partie ci-dessous dit dans quelle livraison elle devient testable**. Aucune notification ne part vers vos proches (c'est la phase 2, qui exige un serveur) : tout est calculé et affiché par le téléphone, pour 0 €.
**Pas de maquettes** : les écrans suivent les wireframes de la spec ; notez tout ce qui vous déplaît visuellement, ce sont des écarts à corriger.

## Installer (TestFlight)

1. Le build de la branche `ios/notifications-p0` est lancé **par vous** dans GitHub > Actions > workflow **ios-testflight** (jamais par la PR).
2. Installez la dernière version de **Miff** depuis TestFlight. Pour retester l'amorce : supprimez l'app puis réinstallez-la (iOS ne réaffiche sa fenêtre qu'une fois), ou, pour les autres états, changez l'autorisation dans Réglages iOS > Miff > Notifications.

## Règles à retenir

- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- **Notez toute phrase trop dure ou anxiogène**, et toute formulation à relire à voix haute (les textes N1 à N4 et N6 sont ceux que vous avez écrits, mot pour mot).
- **Rien n'est envoyé hors de l'iPhone** : si vous voyez une notification arriver sur le téléphone d'un proche, c'est un défaut.

## Partie A — Amorce et autorisation (livrée dans la PR A) — 6 min

- [ ] Sur une installation neuve, après la création ou l'entrée dans une Miff, l'écran **« Un rappel, seulement quand c'est utile »** s'affiche avec l'exemple « Tu es passé orange / Indique à ta Miff que tout va bien. », le texte en **tutoiement**, « **Autoriser** » et « **Plus tard** ».
- [ ] **« Plus tard »** : l'app continue (jamais bloquée), l'écran **ne revient pas** au lancement suivant, et **aucune fenêtre du système** n'est apparue.
- [ ] Réglages iOS > Miff > Notifications : **bannières et sons seulement**, **pas de pastilles** (pas de rond rouge sur l'icône de Miff, jamais).
- [ ] Réinstallez, choisissez **« Autoriser »** : la fenêtre du système apparaît ; refusez : l'app continue, **sans message culpabilisant**.
- [ ] **Deuxième chance** (installation neuve, « Plus tard » choisi) : après avoir publié votre première update (ou si votre statut a déjà une photo), une **carte discrète** en haut du Feed dit « Un rappel si tu oublies de donner de tes nouvelles ? » avec « Activer » et « Pas maintenant ». Elle n'apparaît **jamais** si vous avez déjà accepté ou refusé. « Pas maintenant » la fait partir **pour de bon** ; « Activer » ouvre la fenêtre du système.

## Partie B — Réglages > Notifications (PR A) — 8 min

- [ ] Profil > Réglages : une ligne **Notifications** (cloche) apparaît entre Mon compte et Langue. **Un compte sans Miff ne l'a pas** (Réglages restreints : seulement Mon compte et Confidentialité).
- [ ] Autorisées : « **Autorisées par ton téléphone** », l'interrupteur « Recevoir des notifications » est allumé, puis **Pour toi** (« Quand je passe orange », « Quand je passe rouge », « Quand j'entre dans une zone de danger » + « Quand mon mode voyage se termine » (N7, actif par défaut) + la phrase « Couper cette notification ne change rien à ce que ta Miff voit de toi. »), **Pour ta Miff** (« Quand ma Miff passe orange / rouge »), **Nuit calme**. **Aucun interrupteur « zone de danger d'un proche »** (abandonné).
- [ ] Éteignez l'interrupteur général : les cinq interrupteurs et la nuit calme sont **grisés mais gardent leur valeur** ; rallumez : tout est revenu comme avant.
- [ ] Coupez les notifications dans Réglages iOS puis revenez : « **Coupées dans ton téléphone** », interrupteur **grisé et éteint** (il ne prétend jamais autoriser), bouton « Ouvrir les réglages du téléphone » (ouvre Réglages iOS > Miff) et lien « Comment les réactiver ? » (page « Notifications coupées », trois étapes, ton calme).
- [ ] Installation neuve avec « Plus tard » : « **Pas encore activées** », interrupteur éteint ; l'allumer ouvre **l'amorce** (puis la fenêtre du système si « Autoriser »).
- [ ] Réglages > Confidentialité : le texte sur les notifications dit que **le téléphone prépare les notifications, Miff n'en envoie aucune à tes proches**, avec le même état.
- [ ] Quittez et rouvrez l'app : vos choix sont **gardés**. Déconnectez-vous puis reconnectez-vous avec un autre compte : il repart des **valeurs par défaut** (tout actif).

## Partie C — Centre de notifications (PR A) — 8 min

- [ ] Dans le Feed, une **cloche** à droite de l'en-tête (aucune cloche sans Miff). Sans rien de nouveau : cloche **sans point**.
- [ ] Touchez la cloche **avec les notifications autorisées et rien de nouveau** : écran « Notifications », **état vide calme** « Rien à signaler / Ici, tu retrouves tes rappels et les nouvelles de ta Miff. ».
- [ ] **Jamais demandées** (installation neuve, « Plus tard » choisi) : touchez la cloche. Le centre s'ouvre **flouté**, comme s'il y avait des notifications derrière (de simples **barres**, aucun texte lisible, aucun nom), avec une **carte calme** « Pas encore activées » et un seul bouton **« Activer les notifications »**. Le bouton ouvre **tout de suite la fenêtre du système** (pas d'écran « maison » avant). Autorisez : le flou disparaît et l'état vide « Rien à signaler » apparaît. Refusez : le centre reste flouté, **sans message culpabilisant**.
- [ ] **Refusées** (Réglages iOS > Miff > Notifications coupées, ou refus à la fenêtre ci-dessus) : même centre flouté, carte « Les notifications sont coupées / Tu ne recevras pas de rappel. », même bouton. Le bouton **n'ouvre pas de fenêtre du système** (iOS ne la montre plus) : il ouvre la page calme « Notifications coupées » (trois étapes), dont le bouton « Ouvrir les réglages du téléphone » mène aux Réglages d'iOS. Réactivez-les là-bas puis **revenez dans l'app** : le centre n'est plus flouté.
- [ ] Le centre flouté ne montre **jamais une vraie notification** (même s'il y en avait avant le refus), n'a **pas de menu ⋯**, et **rien n'est marqué comme lu** quand on le quitte (la pastille de la cloche reste).
- [ ] **VoiceOver** sur le centre flouté : seule la carte est lue (titre, phrase, bouton « Activer les notifications ») ; les fausses barres sont ignorées.
- [ ] (Quand les rappels existent, PR suivantes) la cloche porte **un point, jamais un chiffre** ; la liste est groupée **Aujourd'hui / Hier / Plus ancien**, l'heure est celle de l'iPhone, une entrée non lue a un point ; toucher une ligne la marque lue et ouvre **l'écran Update** (N1, N2) ou le **Feed** (N3, N4, N6), jamais une erreur.
- [ ] Menu **⋯** : « Tout marquer comme lu » ; « Tout effacer » demande **confirmation**. Balayage vers la gauche ou appui long sur une ligne : « Supprimer ».
- [ ] **VoiceOver** : la cloche est lue « Notifications » (« Notifications, 2 non lues » s'il y en a) ; une ligne est lue « {titre}, {heure}, non lu ».

## Partie D — N1 et N2 : « Tu es passé orange / rouge » (PR B) — 25 min, avec l'astuce de la console

**Pour ne pas attendre 2 h et 4 h** : modifiez dans la console Firebase (projet **miff-3d283**, jamais l'autre) le statut de **votre** compte, `miffs/{id de la Miff}/status/{votre uid}` : `presence` = `out` et `presenceAt` = **il y a 1 h 55** (type « timestamp »). Ouvrez le Feed (la couleur est encore verte), puis fermez l'app : la notification orange arrive dans environ 5 minutes (le téléphone la programme d'après vos dernières données). Pour le rouge : **il y a 3 h 55**.
**Autorisation** : acceptée. **Aucun des textes ne contient d'heure, de prénom ni de lieu.**

- [ ] **N1** : « **Tu es passé orange** / Indique à ta Miff que tout va bien. » arrive (sonore le jour). Toucher la notification ouvre **l'écran Update** (le bouton central ; dans un build sans bouton central, le Feed).
- [ ] **N2** : « **Tu es passé rouge** / Indique à ta Miff que tout va bien. » arrive 2 h après N1 (ou, avec l'astuce, à la minute prévue), toucher ouvre l'écran Update. **Une seule N1 et une seule N2 par épisode** : rien ne se répète.
- [ ] **Annulation** : juste après avoir programmé N1 (avant son heure), **publiez une update** (ou rentrez dans un lieu de confiance). Au retour au premier plan, la notification ne part pas ; si elle était déjà affichée, elle **disparaît du tiroir** à l'ouverture de l'app.
- [ ] **Zone de danger** : un compte dans une zone de danger (créée par un créateur Premium) ne reçoit **ni N1 ni N2**.
- [ ] **Nuit calme** (22 h à 7 h) : N1 arrive **sans son ni vibration** (dans le tiroir, sans bannière) ; N2 garde son niveau normal. Pour l'essayer sans attendre la nuit, ajustez le `presenceAt` pour que l'heure prévue tombe la nuit.
- [ ] **Réglages > Notifications** : éteindre « Quand je passe orange » annule N1 **immédiatement** (rien n'arrive) ; éteindre « Recevoir des notifications » annule tout **et retire** les notifications Miff du tiroir.
- [ ] **Plafond** : pas plus de 2 par type et 6 au total par jour ; au-delà, rien n'est affiché mais le centre garde une entrée discrète.
- [ ] Le **centre** (cloche) contient la notification, avec le point « non lu » ; toucher la ligne la marque lue.
- [ ] **Premier plan** : si l'app est ouverte à l'heure prévue, **rien ne s'affiche** (la bulle du Feed parle déjà), mais le centre garde l'entrée.
- [ ] **Redémarrage** de l'iPhone avant l'heure : la notification arrive quand même (les demandes survivent au redémarrage — à noter si ce n'est pas le cas).
- [ ] **Changement de langue** (Réglages iOS) : au prochain passage au premier plan, la notification à venir est dans la nouvelle langue.

## Partie E — N3 et N4 : « Ta Miff est passée en orange / rouge » (PR B) — 25 min

**Limite iOS acceptée** : la notification est programmée à l'avance et ne peut pas être vérifiée avant son affichage ; une fausse alerte est possible si la couleur de la Miff est revenue entre-temps. Parades en place : recalcul à chaque ouverture et à chaque réponse du serveur, horizon de 3 h, formulation au passé, retrait à la reprise, compteur local. **La tâche de fond opportuniste n'est pas construite dans cette livraison** (voir la PR) : sur iPhone, une fausse alerte est donc plus probable que prévu.
**Comment faire** : avec **deux comptes** (A sur l'iPhone, B sur l'autre téléphone ou modifié dans la console), A **vert** et B `out` depuis **il y a 1 h 55** : la Miff de A passe orange dans 5 minutes et N3 doit arriver 30 minutes plus tard. Ouvrez le Feed de A avant de fermer l'app.

- [ ] **N3** : « **Ta Miff est passée en orange** / Prends des nouvelles de tes proches. » arrive environ 35 minutes après le départ de B, **sauf si vous êtes vous-même orange** (vous avez déjà eu N1 : pas de N3). Toucher ouvre le **Feed**.
- [ ] **N4** : « **Ta Miff est passée en rouge** » arrive 30 minutes après le passage au rouge de la Miff (même règle).
- [ ] **Si B donne de ses nouvelles avant l'heure** et que vous rouvrez l'app (ou qu'elle était ouverte) : la notification est **annulée** ; si elle était déjà affichée elle **disparaît du tiroir** à l'ouverture.
- [ ] **Si B donne de ses nouvelles pendant que l'app est fermée** : N3 s'affiche quand même (limite iOS acceptée) ; à l'ouverture le Feed dit la vérité et la notification est retirée. **Notez chaque fausse alerte** : en bas de Réglages > Notifications (builds Debug et Internal) un compteur dit « x fausse(s) alerte(s) sur y ».
- [ ] **Compte seul dans la Miff** : jamais de N3 ni N4. **Miff de deux personnes dont vous êtes la cause** : pas de N3 / N4 non plus (vous avez N1 / N2).
- [ ] **Autre Miff active** (Premium) : changer de Miff active annule les rappels de l'ancienne.
- [ ] Les mêmes vérifications que la partie D pour : nuit calme (N3 sans son, N4 normale), interrupteurs (« Quand ma Miff passe orange / rouge »), plafonds, centre, premier plan.

## Partie F — N6 : « Tu es dans une zone de danger de ta Miff » (PR C) — 20 min

**Il faut une zone de danger** : posée par le **créateur** de la Miff avec le Premium accordé à la main (une personne gratuite de cette Miff la reçoit aussi). **« Toujours » et la position exacte** doivent être accordés, le partage actif. Essayez **en vrai** : marchez jusqu'au rayon de la zone (le système signale une entrée au bout d'une à deux minutes).

- [ ] **Entrée réelle** dans la zone de danger (hors de toute zone de confiance) : « **Tu es dans une zone de danger de ta Miff** / Pendant que tu y es, tes proches voient un état différent. » arrive **tout de suite**, **une seule fois**, **sans nom de lieu**. Toucher ouvre **le Feed** (carte « Tu es dans une zone de danger de ta Miff »), pas l'Update.
- [ ] **Dans une zone de confiance qui recouvre la zone de danger** : aucune N6. En sortant de la zone de confiance en restant dans la zone de danger : une N6.
- [ ] **Sortir puis rentrer en moins d'une heure** : une seule N6. **Plus d'une heure après** : une nouvelle.
- [ ] **Redémarrer l'app** (ou l'iPhone) en restant dans la zone : **aucune** nouvelle N6 si le statut était déjà « zone de danger » ; **une** si l'app n'avait pas vu l'entrée.
- [ ] **Nuit** (22 h à 7 h) : la N6 arrive **sans son ni vibration**. **Plafond** : pas plus de 3 par jour ; au-delà, seulement une ligne dans le centre.
- [ ] **App ouverte** : aucune bannière, une ligne discrète dans le centre.
- [ ] **Interrupteurs** : « Quand j'entre dans une zone de danger » éteint (ou « Recevoir des notifications » éteint) : **aucune N6**, mais ce que votre Miff voit de vous ne change pas (la phrase sous l'interrupteur le dit).
- [ ] Vous avez eu N1 / N2 programmées (vous étiez sorti d'un lieu) : en entrant en zone de danger elles sont **annulées** (plus de rappel « à toi » en zone de danger).
- [ ] **Mode voyage actif** : en entrant dans une zone de danger pendant votre voyage, **aucune N6** n'arrive (valeur par défaut, question Q7 à reconfirmer). Voyage terminé ou désactivé : N6 reprend à l'entrée suivante.
- [ ] **Plusieurs Miffs** (Premium) : la N6 est reçue pour **n'importe laquelle** de vos Miffs (pas seulement la Miff affichée) ; le centre montre le **nom de la Miff**.
- [ ] Notez la **durée** entre l'entrée réelle et la notification, et tout cas où elle manque (app fermée de force, mode économie d'énergie, Actualisation en arrière-plan coupée) : ce sont les limites d'iOS à documenter.

## Partie G — Mode voyage : N7 « Ton mode voyage est terminé » et rappels coupés pendant un voyage (PR B) — 15 min

**Préparation** : le mode voyage (Profil > carte « Mode voyage ») est livré ; les notifications sont **autorisées** ; un compte A (l'iPhone) dans une Miff avec un second compte B. Pour ne pas attendre plusieurs jours, choisissez comme date de fin **aujourd'hui** : le voyage finit ce soir à minuit et N7 part à **8 h demain matin**. **Limite iOS acceptée** : iOS ne peut pas finir le voyage app fermée ; N7 dit donc « Ouvre Miff pour que ta carte reprenne ses couleurs. Bon retour ! » et la fin est traitée à l'ouverture de l'app (ou au toucher de N7).

- [ ] **N7 programmée** : A active le voyage (fin = aujourd'hui). Le lendemain à **8 h** : « **Ton mode voyage est terminé** / Ouvre Miff pour que ta carte reprenne ses couleurs. Bon retour ! » arrive (**sonore**, jamais silencieuse : elle n'arrive pas la nuit). Toucher ouvre le **Feed**, la carte de A reprend ses couleurs.
- [ ] **Une seule fois** : N7 n'arrive qu'**une** fois par voyage (jamais deux), même si l'app est ouverte et fermée plusieurs fois.
- [ ] **Désactivation anticipée** : A active le voyage puis le **désactive** à la main avant minuit : **aucune** N7 le lendemain.
- [ ] **Changement de date** : A active le voyage jusqu'à après-demain puis **change la date** pour demain : N7 arrive à 8 h le lendemain de la **nouvelle** date (une seule fois).
- [ ] **Interrupteur** : Réglages > Notifications > « **Quand mon mode voyage se termine** » (section « Pour toi », actif par défaut). Coupé avant 8 h : aucune N7 ; rallumé **avant** 8 h : elle revient. Il suit l'interrupteur général (grisé, avec sa valeur gardée).
- [ ] **Sans Miff** : un compte sans Miff où il partage peut quand même recevoir N7 (un voyage n'a besoin d'aucune Miff).
- [ ] **Pendant MON voyage, N1 à N4 coupées** : A est **sorti d'un lieu** et active le voyage : les rappels « Tu es passé orange / rouge » et « Ta Miff est passée en orange / rouge » **n'arrivent pas** ; ceux déjà programmés sont retirés (au plus tard au prochain retour de l'app au premier plan, à noter si ce n'est pas le cas). **Aucune N6** non plus (valeur par défaut de la spec, question Q7 à reconfirmer).
- [ ] **Quand le voyage finit** : le plan normal reprend (si A est de nouveau sorti d'un lieu, N1 / N2 repartent) et N7 attend ses 8 h.
- [ ] **Un proche en voyage** (mode voyage actif chez B, qui était « sorti » depuis longtemps) : **ni N3 ni N4** à cause de lui (un voyageur est neutre et ignoré dans la couleur de la Miff), **même longtemps après la fin de son voyage**. Une N3 qui reposait sur lui avant son départ en voyage est annulée.
- [ ] **Déconnexion** de A pendant son voyage : aucune N7 sur le téléphone ensuite ; le **centre** contient la ligne N7 (sans nom de Miff) après son envoi, toucher = Feed.
- [ ] **Console Firestore (Utilisation)** : **aucune lecture ni écriture de plus** à cause de N7.
- [ ] Aucun texte ne dit que la **position** s'arrête (critère P5).

## Ce que le PM décide ensuite

- La phrase de l'amorce « **Les alertes sur tes proches arriveront plus tard** » reste-t-elle vraie avec N3 et N4 (« Prends des nouvelles de tes proches ») ?
- Le texte de **Nuit calme** et celui de **Confidentialité** (propres à cette livraison) vous conviennent-ils ?
