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
- [ ] Autorisées : « **Autorisées par ton téléphone** », l'interrupteur « Recevoir des notifications » est allumé, puis **Pour toi** (« Quand je passe orange », « Quand je passe rouge », « Quand j'entre dans une zone de danger » + la phrase « Couper cette notification ne change rien à ce que ta Miff voit de toi. »), **Pour ta Miff** (« Quand ma Miff passe orange / rouge »), **Nuit calme**. **Aucun interrupteur « zone de danger d'un proche »** (abandonné).
- [ ] Éteignez l'interrupteur général : les cinq interrupteurs et la nuit calme sont **grisés mais gardent leur valeur** ; rallumez : tout est revenu comme avant.
- [ ] Coupez les notifications dans Réglages iOS puis revenez : « **Coupées dans ton téléphone** », interrupteur **grisé et éteint** (il ne prétend jamais autoriser), bouton « Ouvrir les réglages du téléphone » (ouvre Réglages iOS > Miff) et lien « Comment les réactiver ? » (page « Notifications coupées », trois étapes, ton calme).
- [ ] Installation neuve avec « Plus tard » : « **Pas encore activées** », interrupteur éteint ; l'allumer ouvre **l'amorce** (puis la fenêtre du système si « Autoriser »).
- [ ] Réglages > Confidentialité : le texte sur les notifications dit que **le téléphone prépare les notifications, Miff n'en envoie aucune à tes proches**, avec le même état.
- [ ] Quittez et rouvrez l'app : vos choix sont **gardés**. Déconnectez-vous puis reconnectez-vous avec un autre compte : il repart des **valeurs par défaut** (tout actif).

## Partie C — Centre de notifications (PR A) — 8 min

- [ ] Dans le Feed, une **cloche** à droite de l'en-tête (aucune cloche sans Miff). Sans rien de nouveau : cloche **sans point**.
- [ ] Touchez la cloche : écran « Notifications », **état vide** « Rien à signaler / Ici, tu retrouves tes rappels et les nouvelles de ta Miff. ». Si les notifications sont coupées ou jamais demandées : un **bandeau** calme avec un bouton (aide ou amorce).
- [ ] (Quand les rappels existent, PR suivantes) la cloche porte **un point, jamais un chiffre** ; la liste est groupée **Aujourd'hui / Hier / Plus ancien**, l'heure est celle de l'iPhone, une entrée non lue a un point ; toucher une ligne la marque lue et ouvre **l'écran Update** (N1, N2) ou le **Feed** (N3, N4, N6), jamais une erreur.
- [ ] Menu **⋯** : « Tout marquer comme lu » ; « Tout effacer » demande **confirmation**. Balayage vers la gauche ou appui long sur une ligne : « Supprimer ».
- [ ] **VoiceOver** : la cloche est lue « Notifications » (« Notifications, 2 non lues » s'il y en a) ; une ligne est lue « {titre}, {heure}, non lu ».

## Partie D — N1 et N2 : « Tu es passé orange / rouge » (livrés dans la PR B)

À remplir avec la PR B (sortie d'un lieu, 2 h puis 4 h ; N1 silencieuse la nuit, N2 normale ; annulation par une update ; un seul rappel par épisode ; plafond 2 par type et par jour, 6 au total).

## Partie E — N3 et N4 : « Ta Miff est passée en orange / rouge » (PR B)

À remplir avec la PR B. **Limite iOS acceptée** : la notification est programmée à l'avance et ne peut pas être vérifiée avant son affichage ; une fausse alerte est possible si la couleur de la Miff est revenue entre-temps (parades : recalcul à chaque ouverture, tâche de fond opportuniste, horizon de 3 h, formulation au passé, retrait à la reprise). **Notez chaque fausse alerte** : le compteur local sert à décider.

## Partie F — N6 : « Tu es dans une zone de danger de ta Miff » (PR C)

À remplir avec la PR C (entrée réelle dans une zone de danger posée par le créateur Premium ; une seule notification, nuit silencieuse, 3 par jour au plus, jamais pour une zone de danger couverte par un lieu de confiance).

## Ce que le PM décide ensuite

- La phrase de l'amorce « **Les alertes sur tes proches arriveront plus tard** » reste-t-elle vraie avec N3 et N4 (« Prends des nouvelles de tes proches ») ?
- Le texte de **Nuit calme** et celui de **Confidentialité** (propres à cette livraison) vous conviennent-ils ?
