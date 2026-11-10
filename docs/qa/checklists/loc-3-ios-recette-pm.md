# Recette iPhone de LOC-3 (lieux de confiance : formulaire, carte, liste, détail, modification, suppression) — parcours pour le PM

**Pour qui** : vous, sur votre **iPhone 11 Pro** (iOS 26.5.2), avec la build TestFlight de la PR (lancée par Claude après les revues), **un second compte de test** (membre d'une de vos Miffs) et, pour les parties « limites » et « plusieurs Miffs », un compte **sans** Miff+ puis un compte **avec** Miff+ (accordé à la main dans la console : document `entitlements/{uid}` avec `active = true` et `expiresAt` dans le futur).
**Durée** : environ 40 minutes.
**Ce qui n'est PAS dans cette recette** : la détection automatique des arrivées et des départs, les permissions de localisation et de notifications (tickets LOC-2 et LOC-4), l'achat de Miff+ (ticket Premium), Android (recette `loc-1-android-recette-pm.md`).

Les **règles de sécurité qui autorisent la catégorie, les 2 lieux gratuits et le type modifiable sont déployées** (2026-11-04) : « Enregistrer » fonctionne pour de vrai, il n'y a plus de « premier temps ».

## Règles à retenir

- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- Les adresses se tapent **sur le téléphone** ; ne mettez pas une vraie adresse de proche dans un message partagé. Pour la recette, utilisez de préférence une adresse publique (une mairie, un stade).
- Aucune écriture n'est faite avant l'appui sur « Enregistrer » ; rien n'est mis en attente hors connexion.
- **Ce qui est nouveau par rapport à Android** et que seul l'iPhone peut montrer : la carte et les suggestions viennent de **Plans (Apple)**, pas d'OpenStreetMap ; les pictogrammes sont des **pictogrammes système provisoires** ; « Voir sur la carte » ouvre **Plans**.

## Préparation (3 min)

1. Installez la build TestFlight de la PR « LOC-3 iOS » (numéro de build indiqué par Claude).
2. Compte **A** = créateur d'une Miff « Recette LOC-3 » (une seule Miff, compte sans Miff+). Compte **B** = membre de cette Miff.

## Partie A — Le formulaire (10 min)

Accès : écran de la Miff > section **Lieux de confiance** > **Ajouter un lieu**.

- [ ] Titre « Nouveau lieu », flèche retour, **aucun bouton « Valider » ni coche** en haut. Un seul bouton **« Enregistrer »** en bas, **grisé**.
- [ ] Sections dans l'ordre : Nom, Miff (une seule : pas de liste déroulante, la Miff est affichée avec la phrase « Les membres de cette Miff verront ce lieu : nom, adresse, carte et rayon. »), Adresse, Périmètre, Type, Catégorie. **Maison** est choisie. **Travail** est bien dans la liste (7 catégories).
- [ ] Tapez 1 lettre dans le nom : un compteur « 1/50 » apparaît. Collez ou tapez 60 lettres : le texte s'arrête à 50 et « Limite de 50 caractères atteinte » s'affiche.
- [ ] Tapez « 17 » dans l'adresse : **aucune suggestion** avant 3 caractères. Tapez « 17 avenue d'Italie Paris » : après une courte pause, une liste de 5 adresses au plus. **Le clavier ne masque pas la liste** (le formulaire défile). **Touchez-en une** : le champ affiche l'adresse avec un repère bleu et une croix ; **la carte apparaît avec un cercle** autour du point ; le curseur s'active. Notez si le délai entre le toucher et l'apparition de la carte est visible (le téléphone demande les coordonnées à Plans).
- [ ] Le bouton « Enregistrer » est **actif dès 1 lettre de nom + une adresse choisie**. Avec 1 seule lettre, touchez-le : « 2 caractères minimum » apparaît et rien n'est envoyé.
- [ ] Retapez une lettre dans l'adresse **après** l'avoir choisie : le choix s'efface, le bouton se grise, la carte se vide.
- [ ] Tapez une adresse complète **sans** toucher de suggestion : « Choisis ton adresse dans la liste pour continuer. » et le bouton reste grisé.
- [ ] Tapez du charabia (« zzzzqqqq ») : « On ne trouve pas cette adresse. Essaie avec moins de détails. »
- [ ] **Mode avion**, tapez une adresse : « La recherche d'adresse n'est pas disponible pour le moment. Réessaie dans un instant. » avec **Réessayer** ; réactivez le réseau, touchez Réessayer : les suggestions arrivent.
- [ ] Curseur du périmètre : de **50 à 800 m, par pas de 50**, 100 m au départ. Le cercle de la carte suit le curseur et la carte s'éloigne seule quand le rayon grandit. **Sous 100 m**, une note discrète « En dessous de 100 m, la détection peut manquer un passage. » (pas en rouge) ; elle disparaît à 100 m.
- [ ] La carte montre la **mention d'Apple** (logo et « Légal ») et **aucune position bleue de vous-même**.
- [ ] Type : « Zone de confiance » est choisie (avec une coche). **« Zone de danger » porte une pastille « Miff+ » avec un cadenas** ; la toucher ouvre l'écran « Les zones de danger font partie de Miff+ » (provisoire : « Miff+ arrive bientôt », pas de prix, pas de bouton d'essai) ; en le fermant (croix ou « Plus tard »), vous revenez au formulaire, **le type est resté « Zone de confiance »**.
- [ ] Remplissez nom + adresse choisie et touchez **Enregistrer** : « On enregistre ton lieu… » puis retour à la liste avec la confirmation (partie C).

## Partie B — Accessibilité et confort (8 min)

- [ ] **Plus grande taille de texte** (Réglages > Accessibilité > Affichage et taille du texte > Texte plus grand > Tailles d'accessibilité) : le formulaire reste lisible, les deux cartes « Zone de confiance » / « Zone de danger » se **superposent** au lieu de se serrer ; à ces tailles « Enregistrer » (et « Supprimer ce lieu » en modification) **défilent avec le formulaire** au lieu d'être fixés en bas (sinon ils prendraient la moitié de l'écran, clavier ouvert) : vérifiez qu'on les atteint en faisant défiler, y compris clavier ouvert. Le texte qui remplace la carte (« Choisis une adresse… ») ne doit pas être coupé.
- [ ] **VoiceOver** : le curseur annonce « Rayon, 100 mètres » et se règle par pas de 50 ; les types s'annoncent avec leur état (« Sélectionné » ; « Fait partie de Miff+ » pour la zone de danger d'un compte gratuit) ; les catégories annoncent leur nom et « sélectionné » ; la carte est décrite par une phrase (« Carte : cercle de 150 mètres autour de… »). **L'adresse et le curseur suffisent à faire un lieu sans voir la carte.**
- [ ] **Retour à l'écran d'accueil puis à l'app** avec le formulaire rempli : rien ne se perd (l'adresse choisie reste choisie). Si vous tournez l'iPhone, le formulaire reste utilisable.
- [ ] **Sélecteur d'applications** (balayage depuis le bas) avec le formulaire ouvert : la vignette est **masquée** (logo Miff), ni le nom ni l'adresse tapés n'y apparaissent.
- [ ] Carte indisponible (mode avion **avant** d'ouvrir le formulaire, réseau rendu après le choix de l'adresse, ou inversement) : message calme « La carte n'est pas disponible pour le moment. Ton lieu est bien placé, tu peux continuer. » avec **Réessayer** ; le bouton reste utilisable.

## Partie C — Créer pour de vrai, la liste (8 min)

- [ ] Créez « Maison de Papa » (Maison, 150 m) puis « Foot de Thomas » (Sport) : après chaque enregistrement, **retour à l'écran de la Miff** avec « Le lieu « … » est enregistré. Tes proches le prendront en compte dès qu'ils ouvriront Miff, ou dans les prochaines heures. »
- [ ] La liste montre, par ligne : l'emoji de la catégorie, le nom, l'adresse, une flèche. Le compteur indique **« 3 lieux sur 3 avec la formule gratuite »** (3 lieux gratuits depuis le 2026-11-10, voir `profil-carrousel-emoji-lieux3-ios-recette-pm.md`).
- [ ] **« Ajouter un lieu » reste visible, avec la pastille Miff+**, et mène à l'écran « Tu as atteint 3 lieux » (jamais un bouton caché ou grisé) : le formulaire s'ouvre un instant (il relit votre formule auprès du serveur) puis est remplacé par l'offre. Rien n'est écrit. Avec un compte **Miff+** ayant déjà 3 lieux ou plus : « Ajouter un lieu » ouvre bien le formulaire, sans compteur « sur 3 » ni pastille.
- [ ] Avec **B** (membre) : même liste, **sans** « Ajouter un lieu » ni compteur, avec « Les lieux de ta Miff, définis par son créateur. » ; toucher une ligne ouvre le détail **sans** « Modifier ». Si B a l'écran de la Miff ouvert pendant que A enregistre, le lieu apparaît chez B **en direct**.

## Partie D — Détail, modification, suppression (10 min)

- [ ] Détail d'un lieu (A) : carte avec cercle, adresse sur deux lignes, **« Voir sur la carte »** (ouvre **Plans sur le lieu** : un repère est posé au bon endroit, pas sur votre position ; **Plans peut garder ces coordonnées dans son historique** : c'est un geste volontaire de votre part, l'app n'envoie rien d'elle-même), Rayon, Type (étiquette + pictogramme), Catégorie, « Visible par les membres de… », « Créé par… (toi) ». **Pas** de phrase sur « ce que ça change pour toi » : elle est masquée jusqu'à LOC-4. **« Modifier »** en haut à droite (absent chez B).
- [ ] **Modifier** : tout est pré-rempli ; « Enregistrer » est grisé tant que rien n'a changé. Changez le nom, le rayon, la catégorie, **le type** (vers « Zone de danger » : refusé pour un compte sans Miff+, avec l'écran « Miff+ arrive bientôt »), l'adresse : après enregistrement, le détail montre les nouvelles valeurs.
- [ ] **Supprimer ce lieu** (bouton blanc au contour rouge, pas de rouge plein) : la feuille « Supprimer ce lieu ? Ce lieu sera définitivement supprimé pour toi et pour tous les membres des Miffs associées. » avec **Annuler** ; glisser la feuille vers le bas ou Annuler ne supprime rien. Confirmez : retour à la liste (pas au détail), le lieu a disparu (chez B aussi, en direct si son écran est ouvert).
- [ ] **Mode avion** + Supprimer + confirmer : « Pas de connexion pour le moment… », rien n'est supprimé, rien n'attend dans une file.
- [ ] Depuis la console Firebase ou un second téléphone : supprimez le lieu **pendant** que son détail est ouvert chez B : B voit « Ce lieu n'existe plus. » (sur la liste) et revient à l'écran de la Miff.

## Partie E — Vie privée (4 min)

- [ ] L'app ne demande **aucune permission** dans tout ce parcours (ni localisation, ni notifications), et **Réglages > Confidentialité et sécurité > Service de localisation > Miff** n'existe pas (ou « Jamais » sans que l'app l'ait demandé).
- [ ] La carte ne montre **pas de point bleu** (votre position) et ne bouge pas au doigt.
- [ ] Après une **déconnexion** puis une connexion avec un autre compte : aucune adresse ni aucun nom de lieu de l'autre compte n'apparaît (liste, suggestions déjà tapées, message « Le lieu … est enregistré »).
- [ ] Aucune adresse, aucun nom de lieu dans les événements Analytics (console, DebugView) : seuls `paywall_shown` / `paywall_dismissed` (avec `extra_trusted_place`) existent, au 3e lieu seulement (rien pour la zone de danger).
- [ ] **Limite connue à constater** : les tuiles de la carte sont gardées par le système (iOS) et ne sont pas effacées à la déconnexion (Android les efface) ; rien d'autre de la carte n'est gardé par l'app.

## Partie F — Plusieurs Miffs et Miff+ (10 min)

À faire avec un compte **A+** doté de Miff+ et d'au moins **3 Miffs créées** (plus une Miff seulement rejointe).

- [ ] Le formulaire montre un champ de Miffs avec des pastilles (une par Miff cochée) ; **la Miff cochée au départ est la dernière rejointe si A+ l'a créée, sinon la première créée**. Toucher la flèche du champ ouvre une feuille avec des cases ; la pastille « Dernière rejointe » est sur la bonne ligne ; **la Miff seulement rejointe n'est pas dans la liste**.
- [ ] Cochez 3 Miffs : « 3 Miffs sur 5 au maximum » ; décochez tout : « Choisis au moins une Miff. » et « Terminé » est grisé (et « Enregistrer » aussi). La croix d'une pastille la retire.
- [ ] Enregistrez : le lieu apparaît dans **chacune** des 3 Miffs. Dans la console, **les 3 documents ont le même identifiant**.
- [ ] Zone de danger **autorisée** (pas de pastille Miff+) ; plus de 2 lieux possibles ; pas de compteur « sur 3 ».
- [ ] Modifiez le lieu : retirez une Miff, ajoutez-en une autre : les listes suivent. **Supprimer** : le lieu disparaît des 3 Miffs et la feuille liste ces Miffs.

## Partie G — Cas limites (5 min)

- [ ] Ouvrez le formulaire avec A+, puis **supprimez une des Miffs cochées** depuis un autre appareil, puis enregistrez : « Une des Miffs choisies n'est plus disponible. Retire-la de la liste et réessaie. » ; rien n'est écrit.
- [ ] Deux appuis rapides sur Enregistrer : **un seul** lieu créé.
- [ ] Lieu « Maison » à 50 m puis 800 m : la carte garde le cercle entier.
- [ ] Le même texte d'adresse retapé deux fois : la seconde liste arrive **tout de suite** (cache de la session).
- [ ] Ouvrez le formulaire sans réseau : « Pas de connexion pour le moment… » avec **Réessayer** ; réseau rendu, Réessayer ouvre le formulaire.

## Ce que je note pour Claude

- Le délai d'apparition des suggestions et de la carte (Plans) : rapide, lent, ou en échec ?
- La précision des suggestions pour des adresses que vous connaissez (Plans trouve-t-il bien les numéros de rue ?).
- Les pictogrammes provisoires (à remplacer par l'export Figma) et la mise en page des cartes de type, du pied d'écran et de la feuille des Miffs.
- Tout texte qui vous semble trop sec, trop alarmiste ou mal tutoyé.
