# Recette Android de LOC-1 (lieux de confiance : formulaire, carte, liste, détail, modification, suppression) — parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), **avec un second compte de test** (membre d'une de vos Miffs) et, pour la partie « limites », un compte **sans** Miff+ puis un compte **avec** Miff+ (accordé à la main dans la console : document `entitlements/{uid}` avec `active = true` et `expiresAt` dans le futur).
**Durée** : environ 45 minutes.
**Ce qui n'est PAS dans cette recette** : la détection automatique des arrivées et départs, les permissions de localisation et de notifications (ticket LOC-2), l'achat de Miff+ (ticket Premium), l'iPhone.

## Deux temps, parce que les règles de sécurité ne sont pas encore déployées

Les règles qui autorisent la catégorie, les 2 lieux gratuits et le type modifiable sont dans la PR #54 (**non déployée**, il faut votre « oui » explicite : `docs/dev/deploiement-regles.md`).

- **Temps 1, maintenant** (règles actuelles) : tout ce qui se lit et se saisit fonctionne ; **« Enregistrer » est refusé par le serveur** et l'app répond calmement « Ça n'a pas fonctionné. Réessaie dans un instant. » : **c'est normal et attendu**, ce n'est pas un défaut de l'app. Parties A, B et E.
- **Temps 2, après le déploiement des règles** : parties C, D, F, G (créer pour de vrai, liste, détail, modifier, supprimer, limites).

## Règles à retenir

- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- Les adresses se tapent **sur le téléphone** ; ne mettez pas une vraie adresse de proche dans un message partagé. Pour la recette, utilisez de préférence une adresse publique (une mairie, un stade).
- Aucune écriture n'est faite avant l'appui sur « Enregistrer » ; rien n'est mis en attente hors connexion.

## Préparation (3 min)

1. Installez l'APK de debug de la PR (artefact `miff-debug-apk` de l'exécution Android de la PR, ou le fichier indiqué dans la PR).
2. Compte **A** = créateur d'une Miff « Recette LOC-1 » (une seule Miff, compte sans Miff+). Compte **B** = membre de cette Miff.

## Partie A — Le formulaire (Temps 1, 10 min)

Accès : Profil > « Lieux de la Miff » > **Ajouter un lieu** (ou la carte de la Miff > section des lieux).

- [ ] Titre « Nouveau lieu », flèche retour, **aucun bouton « Valider » ni coche** en haut. Un seul bouton **« Enregistrer »** en bas, **grisé**.
- [ ] Sections dans l'ordre : Nom, Miff (une seule : pas de liste déroulante, la Miff est affichée avec la phrase « Les membres de cette Miff verront ce lieu : nom, adresse, carte et rayon. »), Adresse, Périmètre, Type, Catégorie. **Maison** est cochée. **Travail** est bien dans la liste (7 catégories).
- [ ] Tapez 1 lettre dans le nom : un compteur « 1/50 » apparaît. Tapez 60 lettres : le texte s'arrête à 50 et « Limite de 50 caractères atteinte » s'affiche.
- [ ] Tapez 2 lettres puis « 17 » dans l'adresse : **aucune suggestion** avant 3 caractères. Tapez « 17 avenue d'Italie Paris » : après une courte pause, une liste de 5 adresses au plus, avec « © contributeurs OpenStreetMap » en bas. **Choisissez-en une** : le champ affiche l'adresse avec un petit repère bleu et une croix ; **la carte apparaît avec un cercle** autour du point ; le curseur s'active.
- [ ] Le bouton « Enregistrer » est **actif dès 1 lettre de nom + une adresse choisie**. Avec 1 seule lettre, touchez-le : « 2 caractères minimum » apparaît et rien n'est envoyé.
- [ ] Retapez une lettre dans l'adresse **après** l'avoir choisie : le choix s'efface, le bouton se grise, la carte se vide.
- [ ] Tapez une adresse complète **sans** toucher de suggestion : « Choisis ton adresse dans la liste pour continuer. » et le bouton reste grisé.
- [ ] Tapez du charabia (« zzzzqqqq ») : « On ne trouve pas cette adresse. Essaie avec moins de détails. »
- [ ] **Mode avion**, tapez une adresse : « La recherche d'adresse n'est pas disponible pour le moment. Réessaie dans un instant. » avec **Réessayer** ; réactivez le réseau, touchez Réessayer : les suggestions arrivent.
- [ ] Curseur du périmètre : de **50 à 800 m, par pas de 50**, 100 m au départ. Le cercle de la carte suit le curseur et la carte s'éloigne seule quand le rayon grandit. **Sous 100 m**, une note discrète « En dessous de 100 m, la détection peut manquer un passage. » (pas en rouge) ; elle disparaît à 100 m.
- [ ] Sous la carte, **« OpenFreeMap © OpenMapTiles Data from OpenStreetMap »** est toujours lu.
- [ ] Type : « Zone de confiance » est choisie (avec une coche). **« Zone de danger » porte une pastille « Miff+ » avec un cadenas** ; la toucher ouvre l'écran « Les zones de danger font partie de Miff+ » (provisoire : « Miff+ arrive bientôt », pas de prix, pas de bouton d'essai) ; en le fermant, vous revenez au formulaire, **le type est resté « Zone de confiance »**.
- [ ] Remplissez nom + adresse choisie et touchez **Enregistrer** : « On enregistre ton lieu… » puis (Temps 1) « Ça n'a pas fonctionné. Réessaie dans un instant. » et le formulaire reste rempli ; (Temps 2) retour à la liste avec la confirmation (partie C).

## Partie B — Accessibilité et confort (Temps 1, 8 min)

- [ ] **Plus grande taille de police** (Réglages du téléphone) : le formulaire reste lisible, les deux cartes « Zone de confiance » / « Zone de danger » se **superposent** au lieu de se serrer, le bouton « Enregistrer » reste atteignable avec le clavier ouvert.
- [ ] **TalkBack** : le curseur annonce « Rayon, 100 mètres » et se règle par pas de 50 ; les types s'annoncent avec leur état (« Sélectionné » ; « Fait partie de Miff+ » pour la zone de danger d'un compte gratuit) ; la carte est décrite par une phrase (« Carte : cercle de 150 mètres autour de… »). **L'adresse et le curseur suffisent à faire un lieu sans voir la carte.**
- [ ] **Rotation** de l'écran avec le formulaire rempli : rien ne se perd (l'adresse choisie reste choisie).
- [ ] Carte indisponible (mode avion **avant** d'ouvrir le formulaire, puis réseau après le choix de l'adresse, ou inversement) : message calme « La carte n'est pas disponible pour le moment. Ton lieu est bien placé, tu peux continuer. » ; le bouton reste utilisable.

## Partie C — Créer pour de vrai, la liste (Temps 2, 8 min)

- [ ] Créez « Maison de Papa » (Maison, 150 m) puis « Foot de Thomas » (Sport) : après chaque enregistrement, **retour à la liste** avec « Le lieu « … » est enregistré. Tes proches le prendront en compte dès qu'ils ouvriront Miff, ou dans les prochaines heures. »
- [ ] La liste montre, par ligne : l'icône de la catégorie, le nom, l'adresse, une flèche. Le compteur indique **« 2 lieux sur 2 avec la formule gratuite »**.
- [ ] **« Ajouter un lieu » reste visible, avec la pastille Miff+**, et ouvre l'écran « Tu as atteint 2 lieux » (jamais un bouton caché ou grisé). Rien n'est écrit.
- [ ] Avec **B** (membre) : même liste, **sans** « Ajouter un lieu » ni compteur, avec « Les lieux de ta Miff, définis par son créateur. » ; toucher une ligne ouvre le détail **sans** « Modifier ».

## Partie D — Détail, modification, suppression (Temps 2, 10 min)

- [ ] Détail d'un lieu (A) : carte avec cercle, adresse, **« Voir sur la carte »** (ouvre l'application de cartes sur le lieu), Rayon, Type (étiquette + icône), Catégorie, « Visible par les membres de… », « Créé par… (toi) », et la phrase sur ce que ça change pour vous. **« Modifier »** en haut à droite (absent chez B).
- [ ] **Modifier** : tout est pré-rempli ; « Enregistrer » est grisé tant que rien n'a changé. Changez le nom, le rayon, la catégorie, **le type** (vers « Zone de danger » : refusé pour un compte sans Miff+, avec l'écran « Miff+ arrive bientôt »), l'adresse : après enregistrement, le détail montre les nouvelles valeurs.
- [ ] **Supprimer ce lieu** (bouton blanc au contour rouge, pas de rouge plein) : la question « Supprimer ce lieu ? Ce lieu sera définitivement supprimé pour toi et pour tous les membres des Miffs associées. » avec **Annuler** ; Annuler ne supprime rien. Confirmez : retour à la liste, le lieu a disparu (chez B aussi, en direct si son écran est ouvert).
- [ ] **Mode avion** + Supprimer + confirmer : « Pas de connexion pour le moment… », rien n'est supprimé, rien n'attend dans une file.
- [ ] Depuis la console Firebase (lecture/écriture à la main) ou un second téléphone : supprimez le lieu **pendant** que son détail est ouvert chez B : B voit « Ce lieu n'existe plus. » et revient à la liste.

## Partie E — Vie privée (Temps 1, 3 min)

- [ ] L'app ne demande **aucune permission** (ni localisation, ni notifications) dans tout ce parcours.
- [ ] Liste des applications récentes (Android 13) : l'aperçu de l'app est masqué sur les écrans des lieux.
- [ ] Aucune adresse, aucun nom de lieu dans les événements Analytics (console, DebugView) : seuls `paywall_shown` / `paywall_dismissed` (avec `extra_trusted_place`) existent, au 3e lieu seulement.

## Partie F — Plusieurs Miffs et Miff+ (Temps 2, 10 min)

À faire avec un compte **A+** doté de Miff+ et d'au moins **3 Miffs créées** (plus une Miff seulement rejointe).

- [ ] Le formulaire montre un champ de Miffs avec des pastilles (une par Miff cochée) ; **la Miff cochée au départ est la dernière rejointe si A+ l'a créée, sinon la première créée**. Toucher le champ ouvre une feuille avec des cases ; la pastille « Dernière rejointe » est sur la bonne ligne ; **la Miff seulement rejointe n'est pas dans la liste**.
- [ ] Cochez 3 Miffs : « 3 Miffs sur 5 au maximum » ; décochez tout : « Choisis au moins une Miff. » et « Terminé » est grisé (et « Enregistrer » aussi).
- [ ] Enregistrez : le lieu apparaît dans **chacune** des 3 Miffs. Dans la console, **les 3 documents ont le même identifiant**.
- [ ] Zone de danger **autorisée** (pas de pastille Miff+) ; plus de 2 lieux possibles ; pas de compteur « sur 2 ».
- [ ] Modifiez le lieu : retirez une Miff, ajoutez-en une autre : les listes suivent. **Supprimer** : le lieu disparaît des 3 Miffs et la question liste ces Miffs.

## Partie G — Cas limites (Temps 2, 5 min)

- [ ] Ouvrez le formulaire avec A+, puis **supprimez une des Miffs cochées** depuis un autre appareil, puis enregistrez : « Une des Miffs choisies n'est plus disponible. Retire-la de la liste et réessaie. » ; rien n'est écrit.
- [ ] Deux appuis rapides sur Enregistrer : **un seul** lieu créé.
- [ ] Lieu « Maison » à 50 m puis 800 m : la carte garde le cercle entier.
