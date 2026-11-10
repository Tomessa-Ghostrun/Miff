# Ticket : FEED, l'écran principal (cartes des membres, bulle de couleur de la Miff, Localiser, batterie, photo d'update)

**Statut** : challenge **à valider par le PM** avant tout développement (aucun développement lancé, aucun statut Notion modifié). **Décisions du PM du 2026-11-06 après essai de l'APK de l'Update, enregistrées ici : D39 (aperçu carré de la photo dans la carte), D40 (très léger mouvement continu de la bulle de la Miff), D41 (en-tête qui se masque au défilement vers le bas, remplace « en-tête fixe »)** ; **D42 à D45 le 2026-11-07, après la recette du Feed (D45 : bulle plus visible et déformante)** (D37 et D38, côté Update, sont dans `docs/tickets/update.md`). Tickets Notion concernés : « Feed » (statut Notion : « Spec en attente de review IA »), « Statut utilisateur (Vert, orange, rouge) », « Update », « Profil », « Paramètre », « Tabbar » (page vide), « Premium » (« A cadrer »), « Notifs » (« A cadrer », page vide).
**Rédigé le** : 2026-11-05 (agent `product-specs`, étape « challenge » du process par ticket).
**Remplace** le découpage FEED de `docs/tickets/feed-profil-reglages.md` §7 (FEED-1 à FEED-4) par celui du §9 ci-dessous ; **reprend** ses décisions (§13 : Q1 à Q15) et celles de `docs/tickets/lieux-de-confiance.md` (§6.6 à §6.9, §7, §16, « Décisions du PM » du 2026-11-01). **Ne remplace aucun autre document.**
**Règle de hiérarchie** : décisions du PM > Notion > ce ticket > reste du dépôt. Une réponse déjà présente dans un ticket Notion ou une décision du PM est une **décision**, pas une question (§3 les liste avec leur source).
**Sources lues** :
- **Notion, relu directement le 2026-11-05** (accès disponible cette fois) : « Feed » (page du 2026-09-30), « Statut utilisateur » (2026-09-30), « Update » (2026-09-30), « Profil » (2026-09-17), « Paramètre » (2026-09-30), « Notifs » (vide), « Tabbar » (page vide), « Premium » (2026-09-30). **Limite** : les blocs image et les « objets externes » embarqués dans ces pages (maquettes, messages d'alerte de l'Update) **ne sont pas lisibles** par l'outil ; les écrans viennent des PNG du dépôt. Les messages d'alerte « orange » et « rouge » du ticket Update sont donc **non lus** (§4, C14).
- Dépôt (`origin/main`, `11b4841`) : `CLAUDE.md` ; `docs/tickets/feed-profil-reglages.md` (en entier), `lieux-de-confiance.md` (décisions, §6.6 à §6.9, §7, §10, §16), `miff.md` (§5.7 à §5.13, §7, §9), `docs/specs/feed-cercle-de-confiance.md`, `update-et-escalade-statut.md`, `profil-et-reglages.md`, `notifications.md`, `paywall-premium.md` ; `backend/firestore.rules` (déployées le 2026-10-30 puis le 2026-11-04, **positions et lieux compris**) et `backend/data-model.md` (§2.6 à §2.12, §3, §8, §12) ; `docs/dev/surveillance-quotas.md` ; `docs/contracts/i18n-keys.md` et `analytics-events.md` ; `design/design-system.md` (statuts) ; les **9 maquettes** `design/exports/feed/` (README et PNG : j'ai regardé les écrans 0, 1, 3, 4, 6, 7 et 8 ; les 2 et 5 sont décrits par le README).
- Code Android existant : `ui/feed/FeedScreen.kt` (**provisoire** : pastille de Miff, état « Pas encore de Miff », texte « Ton fil n'est pas encore prêt »), `ui/main/MainTabBar.kt`, `ui/miff/MiffScreen*.kt`, `data/miff/MiffObserver.kt` (trois écouteurs : Miff, membres, lieux), `domain/miff/MiffModels.kt` (`MiffMember` : prénom, nom, photo de profil, rôle, partage ; **pas de téléphone**), `domain/place/PlaceModels.kt` (`Place.category`), `ui/miff/MiffSharing.kt` (partage du code, déjà livré). Sur la branche `android/loc-2b-tracking` (PR ouverte, **non fusionnée** à la date de ce document) : `domain/tracking/StatusLevel.kt` (`StatusColor.level`, `StatusThresholds` 1 h / 2 h, `MemberStatusFacts`), `TrackingLimits.kt` (garde-fous d'écriture), l'encart « En attente de ton accord » dans le Feed provisoire.
**Convention** : **[Décidé]** = décision du PM (source citée) ; **[Notion]** = repris du ticket Notion ; **[Proposé]** = ma proposition, applicable sauf objection du PM (tableau §5.9 et §3) ; **[À vérifier]** = non confirmé. Je ne tranche aucune décision produit ou légale. Je n'ai modifié ni le code, ni Notion, ni Figma, ni les règles Firestore, ni `backend/data-model.md`.

---

## 1. Résumé pour le PM

En une phrase : le Feed est l'écran qu'on ouvre pour **voir d'un coup d'œil que ses proches vont bien** ; il affiche une **bulle de couleur** (la Miff entière), puis **une carte par membre** (toi compris) avec son état, l'heure du dernier changement, sa batterie, sa dernière photo, et trois gestes : **Localiser** (carte du système), **Message** (SMS), **Appeler**. Aucune notification n'est envoyée (plan gratuit) : tout se calcule **sur le téléphone qui regarde**, à l'ouverture et chaque minute.

**Bonne nouvelle** : presque tout est décidé et le socle existe. Les règles Firestore déployées (2026-11-04) lisent déjà les statuts et **la dernière position** (document séparé, lecture réciproque) ; `MiffObserver` (membres, lieux) et la fonction `StatusColor.level` (LOC-2b) sont écrits. Le Feed **ne crée aucune règle**, sauf pour la localisation de la photo (point 1).

**Les six points qui comptent** (détail §4) :

| # | Constat | Conséquence |
|---|---|---|
| 1 | **La photo d'update ne peut pas porter sa localisation avec les règles déployées** : le document photo n'accepte que `jpeg`, `createdAt`, `expiresAt`. Or la décision par défaut du PM est « localisation figée au moment de la photo ». | **Q1** : une petite évolution de règles (`backend-data`, déploiement sur ton « oui ») ; en attendant, la photo s'affiche **avec son heure, sans localisation** (rien n'est bloqué). Le texte de consentement promet que la position n'est lisible que par ceux qui partagent aussi la leur : la règle de la photo localisée doit faire pareil. |
| 2 | **Quatre libellés Notion et maquettes sont remplacés par des décisions** : « Dans une zone à risque » devient « zone de danger » (wording du PM) ; « Tout le monde est en sécurité » devient « Tout va bien » ; « Plus de nouvelles de Thomas, depuis 16 heures » devient « Pas de nouvelles de Thomas depuis 16 h 02 » ; la cloche disparaît. | Maquettes à retoucher (§16). « Zone de danger » reste un mot fort devant ses proches : risque de ton noté (R1), Q3 pour la personne concernée. |
| 3 | **La couleur de la Miff** (rouge si un rouge, vert si tous verts, sinon orange) **ignore les membres neutres** (N6, décidé) ; « Tout va bien » exige au moins **un autre** membre avec un statut connu. | Règle écrite §5.2 ; texte « 3 sur 4 ont donné des nouvelles » **ajouté** sous « Tout va bien » quand certains sont neutres. |
| 4 | **Le Feed est le premier consommateur de lectures Spark** et les photos y pèsent : budget estimé **~130 lectures par utilisateur actif et par jour** (le modèle disait ~75) et jusqu'à ~1 Mo de photos par jour si on les re-télécharge à chaque ouverture. | Plafond estimé ~380 utilisateurs actifs par jour au lieu de ~650 ; **mesures obligatoires** dans chaque PR ; seuils de `surveillance-quotas.md` à recalibrer (§11). Mitigations gratuites écrites en critères. |
| 5 | **Les photos et les statuts ne se recettent sur téléphone qu'avec les tickets qui les écrivent** : statuts et positions = LOC-2b (PR ouverte), photo = ticket **Update** (pas encore écrit). | Découpage en quatre sous-tickets par plateforme (§9) ; **Q2** : on livre le Feed sans la photo, puis on l'ajoute avec l'Update. |
| 6 | **Quelques états ne sont dans aucune maquette** : bulle neutre, carte « moi », partage arrêté, hors connexion, feuille « Localiser », photo en plein écran, squelette. | Liste §16 pour `design-system` ; les écrans fournis ne couvrent que le cas nominal et les alertes. |

**Coût : 0 €.** Aucun service, aucune bibliothèque, aucune clé de carte (Localiser et Voir la carte ouvrent l'application du système). **Taille globale** (relative) : L, en quatre PR Android puis quatre PR iOS.

---

## 2. Périmètre et frontières

### Dans le périmètre
- **En-tête qui se masque au défilement (D41, remplace « en-tête fixe »)** : avatar avec anneau de ton statut (mène au Profil), logo (remonte le fil), pastille / sélecteur de Miff, futur bouton de notifications.
- **Bulle** de la Miff (couleur, phrase, repère de temps, carrousel d'alertes).
- **Cartes des membres, toi compris** : avatar à anneau, prénom, étiquette d'état, repère de temps, batterie, photo d'update, boutons Localiser / Message / Appeler.
- **Tirer pour actualiser (« pull to refresh », ajouté le 2026-11-06, décision du PM)** : tirer la liste vers le bas en haut de liste force une relecture serveur des statuts et de ce qui alimente les cartes, recalcule les couleurs, remet à zéro les relances des statuts et sort du mode « copie du cache » ; borné à **une relecture serveur toutes les 15 s**, indicateur de 8 s au plus, aucune écriture, hors connexion il se termine tout de suite ; action TalkBack « Actualiser ». Coût : 9 + 2 × membres + lieux lectures par actualisation.
- **Bouton « Ajouter un proche »** (partage du code, déjà livré par le ticket Miff).
- **États** : aucune Miff, seul dans la Miff, aucun statut, partage arrêté, en attente d'accord, hors connexion, chargement, accès perdu.
- **Calcul de la couleur à la lecture**, rafraîchi chaque minute sans lecture serveur.

### Hors périmètre (propriétaire indiqué)
| Sujet | Propriétaire | Frontière |
|---|---|---|
| Créer, rejoindre, écran de la Miff, retirer, quitter, supprimer | Tickets Miff (livrés) | Le Feed **ouvre** leurs écrans, ne les refait pas. |
| Détection des lieux, écriture des statuts et de la position, permissions | **LOC-2a / LOC-2b** (Tracking) | Le Feed **lit** ; il n'écrit **jamais** un statut, une position ni une photo. |
| **Écran Update** (appareil photo, « Publier »), écriture de la photo, alertes « passe en orange / rouge » invitant à publier, couleur du **bouton central** de la barre | Ticket **UPDATE-1** (à écrire) | Le Feed **affiche** la photo ; la couleur du bouton central réutilise la même fonction `StatusColor.level`. |
| Cloche, centre de notifications, « notifications intelligentes » | Ticket **Notifications** (exige Blaze) | **Cloche retirée** en v1 [Décidé, FPR Q15] ; la page Notion « Notifs » est vide. |
| Paywall (première visite du Feed après inscription ou connexion, 2e Miff, « toutes les 10 visites ») | Ticket **Premium** | Le Feed n'affiche aucun paywall ; l'entrée « + Créer / + Rejoindre » du sélecteur reste **réservée au Premium** [Décidé, FPR Q2]. |
| Profil (carrousel de Miffs, lieux) et Réglages | PRO-1, ONB-3 (feed-profil-reglages) | Seul le bouton avatar y mène. |
| Création des lieux, zones de danger | Ticket Lieux (LOC-1, livré) | Le Feed **lit** nom et catégorie des lieux. |
| Règles Firestore de la photo localisée | `backend-data` (R-PHOTO-LOC, §6.3) | Dépend de Q1. |

---

## 3. Décisions déjà prises (reprises telles quelles, avec leur source)

| # | Décision | Source |
|---|---|---|
| D1 | La **dernière position connue** est **stockée côté serveur** (`positions/{uid}`) ; « Localiser » l'utilise ; lecture **réciproque** (il faut avoir publié la sienne) ; masquée après **24 h**, **date toujours affichée** ; jamais « en direct ». | LOC §7, « Décisions du PM » 2026-11-01 ; `data-model.md` §2.12 |
| D2 | La **batterie** est visible des membres ; **gardée**, **datée**, masquée si absente ou de plus de 24 h ; **aucune alerte** de batterie faible en v1 ; le texte de consentement la cite. | FPR Q14 ; Notion Feed |
| D3 | La **couleur est calculée par le téléphone de celui qui regarde, à la lecture, jamais écrite** ; seuils **2 h (orange) / 4 h (rouge)** (**D47**, PM, 2026-11-08 ; 1 h / 2 h avant) ; repère = **max(départ, dernière update)** ; en zone de confiance : vert ; zone de danger : rouge dès l'entrée, vert 1 h après une update postérieure à l'entrée (**D47**, remplace « toujours rouge » ; la zone de confiance l'emporte en cas de chevauchement) ; jamais passé en zone de confiance (`unknown`) : neutre. | LOC §6.9, N2, N4 ; Notion Statut ; `StatusColor.level` (LOC-2b) |
| D4 | **Aucune notification** n'est envoyée (plan Spark, Blaze plus tard) ; **pas de cloche** ; l'avatar mène à l'onglet Profil ; logo : le fil remonte en haut. | FPR Q15 ; CLAUDE.md ; Notion Feed (logo) |
| D5 | Le wording est **« zone de danger »** (pas « zone à risque »). La zone de danger est **Premium**, définie par le **créateur** seul ; entrer dans une zone de danger rend la personne **rouge** immédiatement ; une update (photo) **publiée après l'entrée** la rend verte pendant 1 h, puis rouge de nouveau (**D47**, PM 2026-11-08, qui **remplace** « l'update ne ramène pas au vert ») ; la règle « une update par heure en rouge » reste **abandonnée**. | LOC « Décisions du PM » 2026-11-01 (soir) ; N3, N5 |
| D6 | Couleur de la Miff : **rouge** si au moins un membre rouge, **vert** si tous verts, **orange** sinon ; les membres **neutres sont ignorés** du calcul ; « Tout va bien » **seulement** si au moins un statut connu et tous les connus verts. | Notion Feed ; LOC N6 (appliqué par défaut le 2026-11-01) ; FPR Q12 |
| D7 | Bulle vide : « Tout va bien » ; « {n} sur {m} ont donné des nouvelles » ; « Pas encore de nouvelles » ; « Tu es seul(e) dans cette Miff pour l'instant » ; repère « **Dernière nouvelle il y a 2 min** » (plus de « Mis à jour il y a 2 min ») ; **fin** de « Tout le monde est en sécurité ». | FPR Q12 ; Miff QM12 |
| D8 | Alertes : une page de bulle **par alerte**, seulement s'il y en a plusieurs ; **heure absolue** pour orange et rouge (« à 16 h 02 »), relative pour le vert ; rouge d'ancienneté : « **Pas de nouvelles de {Prénom} depuis 16 h 02** » + bouton « **Envoyer un message** » ; orange : « {Prénom} a quitté {lieu} à 16 h 40 ». | FPR Q11 ; Notion Feed (carrousel) |
| D9 | Libellés d'étiquette : « À {catégorie} » si dans une zone de confiance, « A quitté {catégorie} » sinon ; le **nom du lieu** remplace la catégorie sauf pour **Maison, Travail, École, Sport** ; **depuis le 2026-11-08 (D48) : Chez un proche a aussi son libellé, seuls Loisir et Autre affichent le nom du lieu** ; « Dans une zone de danger » pour la zone de danger. | Notion Feed |
| D10 | **Update** = photo publiée depuis l'écran Update ; une seule photo par personne, **valable pour toutes ses Miffs**, **écrasée** à chaque nouvelle publication, **illisible après 24 h** ; la photo en plein écran affiche **l'heure** et **la localisation figée au moment de la photo**. | Notion Update ; `data-model.md` §2.8 ; LOC (lecture retenue des tickets Notion) |
| D11 | **Message** = application SMS du système, **Appeler** = téléphone du système, avec le **numéro de la fiche membre** (visible des membres, d'eux seuls) ; aucun appel ni SMS lancé par Miff, aucune permission « appel », « SMS », « contacts » ; masqués pour un proche sans numéro et sur sa propre carte. | Notion Feed ; Miff QM11, F2 ; FPR C5 |
| D12 | **Sélecteur** : avec une seule Miff, **pastille non cliquable** (le nom) ; avec plusieurs (Premium), liste ; entrées « + Rejoindre / + Créer » **réservées au Premium** ; la Miff active est une **préférence locale** purgée à la déconnexion. | FPR Q2 ; Miff F8 |
| D13 | **« Ajouter un proche »** en bas du Feed : feuille de partage native (nom, code, lien) ; **15 membres maximum** (plafond doux) : le bouton devient « Cette Miff est bien remplie ! ». | Notion Feed ; Miff QM5, QM14 |
| D14 | Aucune Miff = **jamais atteint** (écran de choix plein écran) ; filet de sécurité : « Pas encore de Miff » avec deux boutons, **aucune phrase de sécurité**. | FPR Q2, C1 ; Miff QM12 ; code Android existant |
| D15 | Tutoiement, **français et anglais** (anglais de repli), clés `feed_` en parité, état porté par texte + icône **jamais par la couleur seule**, contraste AA (texte des étiquettes dans une teinte foncée dédiée, pas `#ff5650`). | CLAUDE.md ; FPR C11, A11 ; `design-system.md` |
| **D39** | **Aperçu de la photo d'un membre dans sa carte = un CARRÉ aussi large que la carte, centré sur le milieu de la photo** (recadrage centré, **sans déformer**) ; **la photo reste complète en plein écran au toucher**. Remplace la « fenêtre fixe de 108 pt » de `design/screens/feed/cartes-membres.md`. | **PM, 2026-11-06** (essai de l'APK de l'Update) |
| **D40** | **La bulle d'état de la Miff en haut du Feed a un TRÈS LÉGER mouvement continu** : ~~respiration d'échelle ≤ 1,5 % et dérive ≤ 3 dp~~ (**amplitudes remplacées par D45**), **cycle de 5 à 8 s**, **jamais de clignotement ni de rebond** ; **immobile** avec « Animations supprimées » ; **arrêtée hors écran** (et en arrière-plan). Remplace « aucune animation » pour la bulle de la Miff (les autres éléments restent sans animation d'alarme, D15). | **PM, 2026-11-06** |
| **D41** | **L'EN-TÊTE du Feed** (photo de profil, logo, nom de la Miff avec son sélecteur, futur bouton de notifications) **se masque quand on fait défiler vers le bas et réapparaît dès qu'on remonte** ; **reste visible** pendant le **chargement**, en **erreur**, **hors connexion**, si la **liste est courte**, avec **TalkBack**, et quand le **sélecteur est ouvert**. **Remplace « en-tête fixe »** (FEED-1, H1). | **PM, 2026-11-06** |
| **D45** | **La bulle d'état de la Miff est NETTEMENT plus visible et se DÉFORME** (retour du PM après sa recette sur un téléphone réel : l'animation de D40 était trop subtile). **Remplace les amplitudes de D40** (échelle ≤ 1,5 %, dérive ≤ 3 dp). Mouvement organique, comme une bulle de savon ou une goutte qui respire : **étirement / écrasement avec les deux axes déphasés** (largeur de 100 % à 92 %, hauteur de 100 % à 90 %, soit environ ±4 % et ±5 %, jamais une échelle uniforme), **légère inclinaison de la forme (±0,8°)**, **dérive verticale de ±3,5 dp** (l'étoile ajoute au plus 0,5 dp). **Cycle de 6 s** pour la forme, 7 s pour l'étoile (la plage de 5 à 8 s de D40 est conservée). **Seule la forme et son fond sont déformés, pas le texte ni le bouton** (ils suivent seulement la dérive) ; la forme ne dépasse jamais sa taille de départ (aucun bord coupé). Inchangé : **immobile** avec « Animations supprimées », **arrêtée** hors écran et en arrière-plan, jamais de clignotement ni de rebond, **même mouvement en vert, orange et rouge**, couleurs de statut inchangées, rien pour TalkBack, zones tactiles inchangées, animation sur la carte graphique (aucune recomposition). Les valeurs sont des constantes nommées dans `BubbleMotion` (réglage rapide). | **PM, 2026-11-07** (recette sur l'OPPO) |
| **D42** | **Dans les cartes du Feed, plus de mention « Toi » à côté du prénom** de l'utilisateur connecté. Rien n'est ajouté à l'écran ; pour le lecteur d'écran, la ligne visible « Ce que tes proches voient de toi », lue dans le même bloc que le prénom, indique que c'est ma carte. (L'étiquette « Toi » de l'écran de la Miff ne change pas.) | **PM, 2026-11-07** (recette du Feed) |
| **D43** | **L'utilisateur connecté est en DERNIÈRE position** de la liste des cartes ; les autres membres le précèdent dans l'ordre déjà défini (le créateur, puis l'ordre d'arrivée `joinedAt`). Toujours jamais réordonné selon l'urgence. **Remplace « moi d'abord »** (§5.4, C1, C7). | **PM, 2026-11-07** |
| **D44** | **À côté du niveau de batterie, plus de mention de « quand » il a été mesuré** (plus de « 85 % · 16 h 02 », ni dans la pastille, ni dans la phrase du lecteur d'écran : « Batterie à 85 % »). Pourcentage et icône inchangés ; la batterie est **masquée au-delà de 6 h (`BATTERY_MAX_AGE`, décision du PM du 2026-11-07, au lieu de 24 h : « là où on n'a pas l'info ») ou si absente** ; aucune alerte. **L'âge se compte depuis `updatedAt` du statut** (heure serveur de la dernière écriture, qui porte toujours une batterie fraîche, y compris pour une écriture qui ne rafraîchit que la batterie), **jamais depuis `lastUpdateAt`** (la dernière photo). Modèle de données et règles Firestore inchangés (`updatedAt` est déjà écrit et déjà lu côté client). **Remplace la partie « datée » de D2, §5.8 et C4.** | **PM, 2026-11-07** |
| **D47** | **Nouveaux seuils de couleur** (PM, 2026-11-08, retour de recette TestFlight, confirmés le jour même) : après avoir QUITTÉ une zone de confiance, orange quand le repère `max(sortie, dernière update)` a **plus de 2 h**, rouge quand il a **plus de 4 h** (strict : à 2 h 00 pile encore vert, à 4 h 00 pile encore orange). **Zone de danger** *(règle mise à jour le 2026-11-08, voir plus bas)* : rouge DÈS L'ENTRÉE ; vert pendant 1 h après une update POSTÉRIEURE à l'entrée (à 1 h 00 pile encore vert, strictement plus d'1 h après cette update : rouge) ; jamais d'orange ; la zone de confiance l'emporte toujours ; **remplace** D8 (« l'update ne ramène pas au vert ») et la règle intermédiaire « rouge seulement après 1 h sans update ». Constantes nommées partagées `orangeAfter` (2 h), `redAfter` (4 h), `dangerZoneRedAfter` (1 h). | PM, 2026-11-08 |
| **D48** | **L'étiquette d'une carte dépend de la CATÉGORIE du lieu** (PM, 2026-11-08) : Maison « À la maison » ; Travail « Au travail » ; École « À l'école » ; Sport « Au sport » ; **Chez un proche** « Chez un proche » ; **Loisir** et **Autre** : le **nom du lieu** seul (plus de « À » devant) ; lieu sans catégorie (écrit avant la catégorie) : le nom. EN : At home / At work / At school / At the gym / At a loved one's. Phrases « A quitté… » : la maison, le travail, l'école, le sport, le domicile d'un proche, ou le nom (EN : home, work, school, the gym, a loved one's place). Le nom est lu dans la liste des lieux déjà écoutée (aucune lecture de plus), visible des seuls membres de la Miff, jamais dans une notification ni à l'écran verrouillé (le Feed n'envoie aucune notification). | PM, 2026-11-08 |
| **D49** | **La bulle est centrée** : titre, lignes et bouton sont centrés dans la tache (marges égales), à toute taille de texte et pendant le mouvement ; la forme est écrasée / inclinée autour de son centre. Écart avec les maquettes (texte aligné à gauche) assumé sur retour du PM. | PM, 2026-11-08 (recette) | *Android (PR #125, PM 2026-11-08, build 22 iOS validé) : même centrage (texte, bouton et pastille, marges égales) et la bulle VERTE n'a plus de pictogramme (étoile supprimée) ; orange, rouge et neutre gardent le leur.*
| **D50** | **Plus de pictogramme (étoile) sur la bulle VERTE** (PM, 2026-11-08, retour sur le build 22) : la bulle verte « Tout va bien » n'a que ses textes, centrés ; les bulles orange (horloge), rouge (point d'exclamation) et neutre (sablier) gardent leur pictogramme. Le symbole était déjà masqué au lecteur d'écran. Même règle sur Android. | PM, 2026-11-08 |
| **D51** | **La ligne de temps d'une carte dit l'HEURE, pas une durée** (PM, 2026-11-10) : « Depuis 13 h 36 » (arrivée, ou départ d'une zone quittée encore verte), « Depuis hier à 13 h 36 » (le « à » du libellé partagé est conservé, écart avec « hier 13 h 36 » du PM), puis la date courte. Format de la langue ET du choix 12 h / 24 h du téléphone, jour calculé dans son fuseau. iOS : clés réutilisées (`feed_time_since`, `feed_moment_since_*`), aucune clé ajoutée ; `feed_time_since_minutes|hours` et `feed_time_departed_minutes|hours` ne sont plus lues. | PM, 2026-11-10 |
| **D52** | **Plus de cercle autour du symbole de la bulle** (PM, 2026-11-10) : il faisait penser à un bouton. Le symbole (horloge, point d'exclamation, sablier) est posé directement dans la tache, **grand (76 pt) et en filigrane doux** (teinte du niveau à 20 % d'opacité, derrière le texte et le bouton, en bas à droite), avec la même respiration lente ; ni cliquable ni lu. Toujours aucun symbole sur la bulle verte (D50). | PM, 2026-11-10 |
| D16 | **Aucun événement Analytics** sur le Feed, les statuts ou la localisation ; aucune coordonnée, adresse, nom de lieu ni nom de personne dans un journal, un rapport de plantage ou un message de partage. | `analytics-events.md` ; LOC VIE |
| **D46** | **La batterie est rafraîchie à l'ouverture de l'app et à sa sortie** (en plus des écritures de statut existantes, qui continuent d'écrire le niveau frais : arrivée, départ, update, changement d'accès à la localisation). Si la personne est éligible (connectée, appareil actif, statut existant, **consentement v3 + partage actif suffisent ; « Toujours » n'est pas requis pour la batterie, le statut portant déjà `locationAccess`**), l'app réécrit **seulement le niveau de batterie** (et `updatedAt`) du statut de chaque Miff où elle partage ; **jamais de création** d'un statut, **aucune position** lue ni écrite par ce chantier, **aucune règle Firestore modifiée**. **Limites de coût** : au plus **une tentative toutes les 15 min** ; pas d'écriture si le niveau a bougé de **moins de 5 points** depuis la dernière valeur écrite, **sauf** si la dernière écriture date de **plus de 3 h** (pour que la limite d'affichage de 6 h ne masque pas la batterie pour rien) ; compteur du jour **dédié, plafonné à 40 écritures par jour et par appareil** (il ne consomme pas les 60 écritures de statut des arrivées et départs). **Effet visible** : `updatedAt` avance à chaque rafraîchissement ; le Feed s'en sert pour l'âge de la batterie et pour la date « Dernières informations de… » hors connexion, **jamais** pour la couleur ni pour « Dernière nouvelle » (calculées depuis `presenceAt` / `lastUpdateAt` : un simple rafraîchissement de batterie ne rassure donc pas à tort). Coût et signal de bascule : `docs/dev/surveillance-quotas.md`. | **PM, 2026-11-07** (« actualise le niveau de batterie des utilisateurs lorsqu'ils ouvrent l'app ou la quittent ») |

---

## 4. Contradictions, ambiguïtés, écarts justifiés

### 4.1 Contradictions entre sources (traitement retenu)
| # | Constat | Traitement |
|---|---|---|
| C1 | **« Dans une zone à risque »** (Notion Feed, maquettes 7 et 8) contre **« zone de danger »** (Notion Statut et Premium, `lieux-de-confiance.md`, décision du 2026-11-01). | **« Zone de danger »** partout (textes, clés, a11y). Aucune occurrence de « zone à risque » dans l'app. La page Notion « Feed » et les maquettes 7 et 8 sont à aligner. Le mot reste fort : voir R1 et Q3. |
| C2 | **« Tout le monde est en sécurité »** (Notion, maquettes 0 à 4) contre **« Tout va bien »** (FPR Q12 : l'app ne peut pas promettre la sécurité). | **« Tout va bien »** (décision, D7). |
| C3 | **Rouge d'ancienneté** : Notion dit « même logique que l'orange » (« {Prénom} a quitté {lieu} ») ; la maquette 8 dit « Plus de nouvelles de Thomas / Depuis 16 heures » ; FPR Q11 dit « Pas de nouvelles de {Prénom} depuis 16 h 02 ». | **Bulle : FPR Q11** (décision postérieure à Notion). **Carte** : étiquette « A quitté {lieu} » (Notion, garde l'information du lieu) et ligne de temps « Pas de nouvelles depuis 16 h 02 » ; l'étiquette « Plus de nouvelles » de la maquette 8 est abandonnée. |
| C4 | FPR Q12 (« {n} sur {m} » **sinon**) contre LOC N6 (membres neutres **ignorés**). Cas limite : trois verts et un neutre. | **Les deux** : « Tout va bien » et, en seconde ligne, « 3 sur 4 ont donné des nouvelles ». |
| C5 | **Orange après 18 min** (maquettes 5 et 6, « Parti(e) il y a 18 min »). | La **règle** (orange de 2 h à 4 h depuis le 2026-11-08) l'emporte ; la maquette est un exemple. Vert avant 2 h, étiquette « A quitté la maison » en vert. |
| C6 | **Horodatage de la bulle** : Notion dit « la dernière fois où on a checké le statut de chaque membre » ; avec des écouteurs en direct, cela n'a plus de sens (FPR A7). | « **Dernière nouvelle il y a 2 min** » : le plus récent des repères des **autres** membres (§5.3). |
| C7 | **Cloche** et centre de notifications (Notion Feed, maquettes) contre aucune notification possible et page « Notifs » vide. | Cloche **retirée** (D4) ; la page Notion Feed est à aligner. |
| C8 | **Carte « moi »** : Notion la prévoit (« y compris moi-même ») ; la maquette 1 ne montre aucune carte quand on est seul. | **Ma carte est affichée** (Notion), sans boutons ; elle montre **ce que mes proches voient de moi**. |
| C9 | **Une seule Miff** : Notion dit « seule option dans le dropdown » ; la maquette 3 montre « + Rejoindre / + Créer » avec deux Miffs. | D12 : pastille non cliquable avec une Miff ; entrées « + » réservées au Premium. |
| C10 | **Localiser** : Notion « renvoie vers Google Maps ou Plans » **directement** ; la décision D1 exige que la **date de la position soit toujours affichée** et que la position de plus de 24 h soit masquée. | Une **petite feuille** s'ouvre au toucher (date de la mesure, bouton « Ouvrir dans les cartes ») avant la carte du système : **écart justifié, un toucher de plus** (le PM peut objecter, voir §5.6). |
| C11 | **Photo en plein écran avec localisation** (Notion Update et Feed, D10) contre le schéma de la photo (`validPhoto` : `jpeg`, `createdAt`, `expiresAt` seulement). | **Q1** ; sans évolution de règles, pas de localisation sur la photo. |
| C12 | **Chronomètre rouge** : Notion Statut dit « était en orange et n'a pas publié d'update depuis plus de 2 h » (lisible comme 2 h après être passé orange, soit 3 h) ; sa règle d'évaluation dit « plus de 2 heures » depuis le dernier repère ; la même ligne « moins d'une heure » est écrite deux fois. | **2 h après le repère** (LOC §6.9, `StatusColor.level`) ; non rouvert. *Remplacé le 2026-11-08 : orange à 2 h, rouge à 4 h après le repère (même repère).* |
| C13 | **« Catégorie du dernier lieu de confiance consulté »** (Notion) : le statut ne porte que `placeId` (le lieu où l'on est, ou **celui qu'on vient de quitter**). | On lit le lieu par `placeId` dans la liste locale des lieux ; « consulté » = « le dernier lieu concerné par le statut ». |
| C14 | **Alertes d'update** « passe en orange / rouge, le message ne disparaît que lorsqu'un update est publié » (Notion Update) : **images non lisibles**. | Hors Feed (ticket Update). Le Feed n'invente aucun texte d'alerte propre à l'auteur, sauf la phrase de bulle à la 2e personne (§7). |
| C15 | **Tabbar** : page Notion vide ; le rôle et la couleur du bouton central sont dans « Update ». | Hors Feed ; même fonction de couleur. |

### 4.2 Écarts avec les maquettes (justifiés, à lister dans la PR)
Maquettes 0 à 8 de `design/exports/feed/` : cloche retirée ; « Tout va bien » ; textes sans accord de genre (« Arrivé il ya 12 min », « Parti(e) » : fautes d'orthographe et d'accord, FPR A6) ; heures absolues pour orange et rouge ; étiquette rouge d'ancienneté ; « zone de danger » ; carte « moi » ajoutée ; contraste des étiquettes (teinte foncée pour le texte, FPR A11) ; un seul repère de temps par bulle ; la **même photo de salon** sur tous les états des maquettes n'est qu'un exemple (une photo vit 24 h, FPR A10) ; pagination = une page par alerte, **sans défilement automatique**.

### 4.3 Ambiguïtés restantes (non devinées)
Trois points ne sont couverts par aucun ticket : la **localisation de la photo** (Q1), l'**ordre de livraison avec l'Update** (Q2) et le **texte de zone de danger pour la personne concernée** (Q3). Tout le reste est décidé ou fixé par défaut au §5.9.

---

## 5. Règles métier

### 5.1 En-tête
- **D41 (2026-11-06)** : l'en-tête **se masque quand on fait défiler vers le bas et réapparaît dès qu'on remonte** ; il **reste visible** pendant le chargement, en erreur, hors connexion, si la liste est courte (rien à défiler), avec TalkBack (lecteur d'écran actif) et quand le sélecteur de Miff est ouvert ; au masquage, le sélecteur ouvert ne se ferme pas par accident. Contenu : **avatar** à gauche (photo ou initiales) avec **anneau de la couleur de mon statut** (neutre si inconnu) → **ouvre l'onglet Profil** ; **logo** au centre → le fil **remonte** tout en haut (animation ; sans animation si « réduire les animations ») ; **pastille de Miff** dessous. **Pas de cloche.**
- « Mon statut » = le document `status/{moi}` de la **Miff affichée**, lu par le même écouteur que les autres (aucune lecture de plus). Sa couleur sert aussi au bouton central de la barre (ticket Update ; fonction partagée, un seul calcul).

### 5.2 Couleur de la Miff et choix de la bulle
Calcul sur l'ensemble des membres **y compris moi** [Notion : « tous les membres »], membres neutres ignorés [D6] :
1. au moins un rouge → **rouge** ; 2. sinon au moins un orange → **orange** ; 3. sinon, au moins un vert → **vert** ; 4. sinon (aucun statut connu) → **neutre**.

**Choix de la bulle** (une seule à la fois ; ordre de priorité) :
| Situation | Bulle |
|---|---|
| Alertes (rouge ou orange) | **Carrousel** : une page par membre concerné ; rouges d'abord, puis oranges ; dans un même niveau, le repère le plus ancien d'abord ; pastilles de pagination **seulement s'il y a plusieurs pages** ; **aucun défilement automatique** ; chaque page est atteignable sans balayage (« Alerte 1 sur 2 », boutons précédent / suivant pour le lecteur d'écran) |
| Pas d'alerte, au moins **un autre** membre au statut connu, tous les connus verts | « Tout va bien » + « Dernière nouvelle il y a 2 min » (+ « 3 sur 4 ont donné des nouvelles » s'il y a des neutres) |
| Pas d'alerte, aucun **autre** membre au statut connu | « Pas encore de nouvelles » (neutre) |
| Personne d'autre dans la Miff | « Tu es seul(e) dans cette Miff pour l'instant » (neutre) |
| Je suis le seul rouge ou orange | page à la 2e personne (« Tu as quitté la maison ») : mon état compte dans la couleur (Notion) |
- **Aucune phrase de sécurité sans statut connu** [Décidé, QM12].
- Le repère « Dernière nouvelle » = le plus récent des repères `max(presenceAt, lastUpdateAt)` des **autres** membres au statut connu (jamais le mien : ma propre update ne doit pas rassurer sur les autres).
- La bulle **ne dit jamais « en sécurité »** ; elle informe.
- **Mouvement de la bulle (D40, 2026-11-06 ; amplitudes remplacées par D45, 2026-11-07 : étirement / écrasement ±4 à 5 % déphasé, inclinaison ±0,8°, dérive ±3,5 dp, forme seule déformée)** : la bulle de la Miff a un mouvement continu, cycle de **5 à 8 s**, **jamais de clignotement ni de rebond** ; **immobile** si le système demande de supprimer les animations ; **arrêtée** quand la bulle n'est pas à l'écran (défilement, autre onglet, arrière-plan) ; le mouvement **ne dépend pas du niveau** (même amplitude en vert, orange, rouge : aucun effet d'alarme) ; **aucun effet sur le lecteur d'écran** (rien n'est annoncé).

### 5.3 Calcul d'un niveau (une seule fonction, partagée iOS / Android)
Celle de LOC-2b, **inchangée** : partage arrêté → neutre ; statut absent → neutre ; `in_trusted` → vert ; `in_risk` → rouge, sauf vert si une update postérieure à l'entrée date d'1 h au plus (D47) ; `unknown` → neutre ; `out` → repère `max(presenceAt, lastUpdateAt)` : jusqu'à 2 h **inclus** vert, de plus de 2 h à 4 h **inclus** orange, au-delà rouge (bornes strictes « plus de », alignées sur iOS) ; repère dans le futur = vert (**seuils du PM du 2026-11-08**, 1 h / 2 h avant). Les deux constantes (2 h, 4 h) sont **lues dans `StatusThresholds`** : le Feed ne réécrit jamais ces durées (STA9). **Zone de danger (D47)** : avec `entry = presenceAt` et `u = lastUpdateAt`, vert si `u` existe, `u > entry` (strictement postérieure) et `now - u <= StatusThresholds.DANGER_ZONE_RED_AFTER_MILLIS` (1 h : 59:59 et 1:00:00 verts, 1:00:01 rouge) ; sinon rouge (pas d'orange). L'écriture du statut d'entrée en zone de danger ne touche pas `lastUpdateAt` : une update d'avant l'entrée reste donc plus ancienne que `presenceAt` et ne compte pas. Cas limites : update à l'instant exact de l'entrée = rouge ; `lastUpdateAt` encore en attente du serveur (mon statut) = rouge jusqu'à l'acquittement, indéfiniment hors ligne ; horloge dans le futur = « maintenant » (vert si postérieure) ; `presenceAt` absent = rouge. Plusieurs entrées/sorties : chaque entrée écrit un nouveau `presenceAt`, donc une update d'une visite précédente ne compte pas. Étiquette « Dans une zone de danger » inchangée (factuelle) même quand la carte est verte.
- **Rafraîchissement** [Proposé, retenu avec LOC §6.9] : à l'ouverture, au retour au premier plan, à chaque changement d'un document, et **toutes les minutes** tant que l'écran est visible (minuteur local, **aucune lecture serveur**). Téléphone du proche fermé ou verrouillé : rien ne se calcule, rien n'est notifié.
- **Annonce lecteur d'écran** : seulement quand un **niveau change**, pas à chaque minute.

### 5.4 Carte d'un membre (moi compris)
Contenu, du haut vers le bas :
1. **Avatar** (photo de profil de la fiche membre, ou initiales) avec **anneau** de la couleur du niveau ; **prénom** ; **batterie** (pastille en haut à droite).
2. **Étiquette d'état** (texte + icône + couleur ; D9).
3. **Ligne de temps** (§5.5).
4. **Photo d'update** si elle existe et n'est pas expirée, **avec son heure** (« Photo de 15 h 40 ») ; **aperçu en carré aussi large que la carte, centré sur le milieu de la photo, sans déformer (D39)** ; toucher = plein écran, **photo complète** (§5.7).
5. **Boutons** Localiser, Message, Appeler (§5.6 ; **absents de ma carte**).
- **Liseré coloré** à gauche de la carte (Notion) = couleur du niveau ; jamais la seule information.
- **Ordre** [Proposé, **remplacé par D43 : moi en DERNIER**] : ~~moi d'abord~~, puis les autres dans l'ordre d'arrivée dans la Miff (`joinedAt`) ; **jamais réordonné selon l'urgence** (une liste qui bouge déplace les boutons sous le doigt et stresse).
- **Libellés d'étiquette** (le lieu se lit dans la liste locale des lieux, par `placeId` de **cette Miff**) :
| Niveau / présence | Étiquette | Remarque |
|---|---|---|
| Vert, `in_trusted` | libellé de la **catégorie** du lieu, ou **le nom du lieu** | **D48** : Maison « À la maison » ; Travail « Au travail » ; École « À l'école » ; Sport « Au sport » ; Chez un proche « Chez un proche » ; **Loisir et Autre : le nom du lieu de confiance seul** (deux lignes au plus puis « … », visible des seuls membres de la Miff) ; lieu sans catégorie (écrit avant les catégories) : le nom ; lieu introuvable ou nom vide : « Dans un lieu de confiance ». Dans une phrase (« A quitté … ») rien ne change : Maison, Travail, École, Sport ont leur nom commun, les autres catégories nomment le lieu |
| Vert, `out` (moins de 2 h) | « A quitté {catégorie / nom} » | en vert |
| Orange ou rouge, `out` | « A quitté {catégorie / nom} » | couleur du niveau |
| `in_risk` | « Dans une zone de danger » | D5 ; rouge dès l'entrée, vert 1 h après une update postérieure à l'entrée (D47) |
| Lieu supprimé ou introuvable [Proposé] | « Dans un lieu de confiance » / « A quitté un lieu de confiance » | libellé générique (LOC §6.9) ; rien tant que la liste des lieux n'est pas chargée (pas de scintillement) |
| Neutre, statut absent ou `unknown` | « Pas encore de nouvelles » | pas de couleur de statut |
| Neutre, partage arrêté | « Partage arrêté » | jamais alarmiste |
| `locationAccess` = `denied` ou `foreground` | petit badge doux « Localisation limitée » en plus [Proposé, FPR D8] | aucun effet sur la bulle |

### 5.5 Ligne de temps (D8 : relatif pour le vert, absolu pour orange et rouge)
| Cas | Texte |
|---|---|
| `in_trusted` | **L'HEURE d'arrivée, jamais une durée** (D51, PM 2026-11-10) : « Depuis 08 h 12 » (aujourd'hui), « Depuis hier à 08 h 12 », puis « Depuis le 3 nov. à 08 h 12 » ; heure dans le fuseau du téléphone, 24 h en français, « Since 8:12 AM » en anglais selon le téléphone |
| `out`, vert | **L'HEURE de sortie** (mêmes formes : « Depuis 08 h 12 », « Depuis hier à 08 h 12 », date courte) |
| `out`, orange ou rouge | « Pas de nouvelles depuis 16 h 02 » pour le rouge ; « Dernière nouvelle à 16 h 02 » pour l'orange |
| `in_risk` | « Depuis 16 h 02 » (heure d'entrée dans la zone, mêmes formes) |
| Neutre | aucune ligne |
- Heures au format du téléphone ; en français « 16 h 02 » (motif propre à la langue, clé de texte). Un jour différent : « hier à 16 h 02 » puis la date.
- **Un seul repère de temps** par carte et par bulle (FPR A7).

### 5.6 Boutons de la carte
- **Message** : SMS vers le numéro de la fiche membre (Android : « écrire à » ; iOS : `sms:`). **Appeler** : composeur du système (Android : « composer le numéro », aucun appel direct ; iOS : `tel:`, confirmation d'iOS). Absents sans numéro ou sur ma carte. Nom lisible : « Écrire à Thomas », « Appeler Thomas ». Si aucune application ne répond : message calme, pas d'erreur technique.
- **Localiser** [D1, C10] : visible sur les cartes des **autres** membres dont le partage est actif. Au toucher : **une lecture** de `positions/{uidMembre}` **depuis le serveur** (jamais depuis le cache, pas d'écouteur, pas de liste) ; pendant la lecture le bouton affiche un chargement.
  - Succès, mesure de **moins de 24 h** : **feuille** « Dernière position connue de {Prénom} » + « Mesurée à 08 h 12 (il y a 20 min). Ce n'est pas une position en direct. » + bouton « Ouvrir dans les cartes » (Android : intention `geo:`, repli sur la page web de Google Maps ; iOS : Plans) avec **les coordonnées seules** (**aucun nom, aucune étiquette** transmis à l'application de cartes).
  - Succès, mesure de **plus de 24 h** : la feuille dit la date (« Elle date d'hier à 08 h 12 : elle n'est plus affichée. »), **sans bouton de carte, sans coordonnées**.
  - Introuvable : « {Prénom} n'a pas encore de position connue. »
  - Refusé (réciprocité, partage arrêté, retrait) : **un seul message neutre** (« On ne peut pas afficher la position de {Prénom} pour le moment. Elle apparaît quand vous partagez tous les deux. »), jamais « erreur ».
  - Hors connexion : « Pas de connexion pour le moment. Réessaie dès que le réseau est revenu. »
  - **Livré seulement avec LOC-2b** (avant : bouton absent) [Décidé, FPR Q13 mis à jour].
- **Envoyer un message** dans la bulle d'alerte (orange ou rouge, jamais sur ma propre page) : même intention SMS que le bouton de la carte du membre concerné.

### 5.7 Photo d'update
- Le Feed ne lit la photo que si `status.hasPhoto = true` **et** que `lastUpdateAt` a **moins de 25 h** (une photo est créée au plus tard à `lastUpdateAt` et vit au plus 25 h : au-delà, elle est forcément expirée, **ne pas lire** : chaque lecture refusée coûte quand même des lectures de règles). Lecture **à l'apparition de la carte** à l'écran (liste paresseuse), pas pour toutes les cartes à l'ouverture.
- **Copie locale** : une photo déjà lue n'est **pas retéléchargée** tant que `lastUpdateAt` n'a pas dépassé le `createdAt` de la copie (+ 5 min de marge d'horloge) ; lecture depuis le cache d'abord. **Une photo dont `expiresAt` est passé n'est jamais affichée**, même en cache. Refus ou introuvable = **aucune photo, aucun message d'erreur**.
- **Aperçu dans la carte (D39)** : **carré aussi large que la carte**, **centré sur le milieu de la photo**, recadrage **sans déformation** (la photo stockée est entière, le recadrage n'est qu'un affichage) ; le toucher ouvre la photo **complète**.
- **Plein écran** [Notion, D10] : photo **complète (aucun recadrage)**, « Photo de {Prénom} · {heure} » (heure de publication), bouton de fermeture ; **localisation** : voir Q1 (« Voir où c'était » → carte du système sur la position figée de la photo, **sans nom**). Sans localisation disponible : rien n'est affiché à sa place.
- Le Feed **ne supprime jamais** une photo expirée (il ne peut pas distinguer expirée d'absente) : le nettoyage reste celui de l'auteur et du contrat `data-model.md` §2.8.

### 5.8 Batterie [D2]
« 85 % » dans une pastille, **sans heure de mesure (D44, remplace l'ancienne version « datée » « 85 % · 16 h 02 »)** ; **plus de 6 h (D44, `BATTERY_MAX_AGE`)** depuis l'écriture (`updatedAt`, jamais `lastUpdateAt`) ou absente : masquée. Lecteur d'écran : « Batterie à 85 % ». **Aucune alerte, aucun état « batterie faible »** en v1.

### 5.9 Valeurs par défaut retenues (applicables sauf objection du PM)
| # | Sujet | Valeur retenue |
|---|---|---|
| V1 | Ordre des cartes | Moi d'abord, puis par `joinedAt`, stable |
| V2 | Miff affichée au lancement | La dernière ouverte (préférence locale) ; sinon la plus ancienne (`memberships.joinedAt`) ; Miff supprimée ou accès perdu : bascule vers une autre, sinon état vide |
| V3 | Rafraîchissement du niveau | Chaque minute, écran visible, sans lecture serveur |
| V4 | Ma propre carte | Reflet exact de mon document de statut (ce que voient mes proches) ; mention « Partage en attente d'accord » si LOC-2b le signale |
| V5 | Hors connexion | Mention « Dernières informations de {heure} » = plus récent `updatedAt` connu ; les bulles orange et rouge ajoutent « Informations de {heure} : reconnecte-toi pour les mettre à jour » ; **jamais de vert sans date** |
| V6 | Partage arrêté **par moi** | Je ne peux plus lire les statuts (réciprocité) : cartes neutres + « Tu as arrêté de partager. Reprends le partage pour voir les nouvelles de tes proches. » |
| V7 | Statuts pas encore lisibles (le mien n'est pas encore publié) | « Partage de ton statut en cours… », **jamais une erreur** (`permission-denied` géré) |
| V8 | En attente de mon accord (consentement version 3, LOC-2b) | L'encart de LOC-2b **est conservé** en tête du Feed ; la bulle reste neutre |
| V9 | Retours à l'écran | Écouteurs **détachés** quand l'application passe en arrière-plan, rattachés au retour ; **un seul jeu d'écouteurs** pour la Miff affichée (pas de lecture des autres Miffs) |
| V10 | Photo et position dans les « applications récentes » | `RecentsPrivacy()` **conservé** (déjà dans le Feed provisoire) |

---

## 6. Contrat avec les règles Firestore déployées et le modèle de données

### 6.1 Ce que les règles permettent (vérifié dans `backend/firestore.rules`, déployées le 2026-11-04)
| Lecture du Feed | Règle | Lectures de règles | Remarque |
|---|---|---|---|
| `get miffs/{id}` (nom, code) | `isMember` | 1 | refus ou introuvable = accès perdu (Miff §5.7) |
| Écouteur `members` | `isMember` + `miffAlive` | 2 par requête | prénom, nom, photo de profil, rôle, `sharing.enabled`, **`phone`** (déjà lisible de tous les membres) |
| Écouteur `status` (liste) | `miffAlive` + `mySharingOn` + `statusPublished` | 3 par requête | **terminé par Firestore sur `permission-denied`** si mon partage est arrêté ou mon statut absent : à **rattacher** après chaque republication (V6, V7) |
| Écouteur `places` | `isMember` + `miffAlive` | 2 par requête | **déjà dans `MiffObserver`** : partagé avec l'écran de la Miff |
| `get positions/{uid}` | soi, ou `miffAlive` + `mySharingOn` + `statusPublished` + `positionPublished` + `targetSharingOn` | **5** (+1 lecture) | **à l'appui seulement** ; jamais de `list` |
| `get photos/{uid}` | `miffAlive` + `mySharingOn` + `expiresAt` non passé | **2** (+1 lecture) | **aucune** condition de position publiée aujourd'hui (décision du 2026-10-22) |
- **Le Feed n'écrit rien** : 0 écriture, 0 suppression (D16, critère Q1). Aucun nouveau champ de statut n'est demandé.
- `status` : schéma fermé, **aucune coordonnée** ; toutes les clés sont toujours présentes.
- **Contrat de lecture à respecter** (`data-model.md` §8) : l'écouteur de statuts n'est attaché **qu'après publication de mon propre statut** ; photo : refus ou introuvable = « pas de photo » ; position : refus ou introuvable = message neutre.

### 6.2 Ce que le Feed doit lire en plus dans le code existant
`MiffMember` n'a **pas** de téléphone (« not shown here ») : ajouter `phone` (déjà dans le document `members`, aucune lecture de plus). Les lieux (`Place`) portent déjà `category` et `name` ; **aucun lieu n'est lu par le Feed en plus de ceux de `MiffObserver`**.

### 6.3 Évolution de règles demandée (Q1) : **R-PHOTO-LOC** (`backend-data`, non préparée ici)
- **Constat** : `validPhoto` accepte `jpeg`, `createdAt`, `expiresAt` et rien d'autre ; la position (`positions/{uid}`) est **écrasée** à chaque mesure : elle ne peut donc pas servir de « localisation figée au moment de la photo ».
- **Proposition** : `lat` et `lng` **nullables** dans le document photo (même validation de plages que `validPosition`, 5 décimales côté client), écrits **seulement** avec un consentement de version 3 ou plus ; **supprimés avec la photo** (aucun lot de purge ne change : la photo y est déjà). Lecture : **si `lat` est renseignée, exiger `positionPublished`** (court-circuit : +1 lecture de règles **seulement pour les photos localisées**), pour que la localisation de la photo suive la **même réciprocité** que « Localiser ». Sinon la photo localisée contredirait la promesse du texte de consentement (« les membres qui partagent aussi la leur peuvent la voir »).
- **Conséquence connue** : un membre qui n'a pas publié sa propre position ne voit pas **les photos localisées** (message neutre : « Partage ta position pour voir cette photo. » [Proposé]). Les photos sans localisation restent lisibles comme avant.
- Déploiement **seulement sur ton « oui » explicite** (`docs/dev/deploiement-regles.md`). Budget : photo localisée en lecture = 4 lectures de règles et lecture (au lieu de 3) ; écriture : aucune lecture de règles de plus que `validPhoto` (consentement via `getAfter` déjà présent).
- **Écrit par** : le ticket **Update** (le Feed ne fait que lire).

### 6.4 Budget de lectures Spark (voir §11)

---

## 7. Textes proposés (FR / EN, tutoiement, **à relire à voix haute par le PM**)

Clés `feed_` en parité ; les prénoms, lieux et heures s'insèrent par paramètres ; formulations **sans accord de genre** ; aucun texte n'a été relu par un juriste (décision du 2026-10-23). Textes existants conservés : `feed_selector_*`, `feed_nomiff_body`, `feed_create_action`, `feed_join_action`, `feed_offline_note`, `feed_unavailable`.

### Bulle
| Situation | FR | EN |
|---|---|---|
| Tout va bien | « Tout va bien » | « All good » |
| Repère | « Dernière nouvelle il y a 2 min » / « … il y a 3 h » / « … hier à 16 h 02 » | « Latest news 2 min ago » / « … 3 h ago » / « … yesterday at 4:02 PM » |
| Neutres présents | « 3 sur 4 ont donné des nouvelles » | « 3 of 4 have shared news » |
| Aucun statut | « Pas encore de nouvelles » + « Dès que tes proches donneront des nouvelles, tu les verras ici. » | « No news yet » + « As soon as your loved ones share news, you'll see it here. » |
| Seul | « Tu es seul(e) dans cette Miff pour l'instant » | « You're the only one in this Miff for now » |
| Orange | « {Prénom} a quitté {lieu} à 16 h 40 » | « {Name} left {place} at 4:40 PM » |
| Rouge, ancienneté | « Pas de nouvelles de {Prénom} depuis 16 h 02 » | « No news from {Name} since 4:02 PM » |
| Rouge, zone de danger | « {Prénom} est dans une zone de danger » + « Depuis 16 h 02 » | « {Name} is in a danger zone » + « Since 4:02 PM » |
| Action d'alerte | « Envoyer un message » | « Send a message » |
| Moi, orange | « Tu as quitté {lieu} à 16 h 40 » | « You left {place} at 4:40 PM » |
| Moi, rouge d'ancienneté | « Tes proches n'ont pas eu de tes nouvelles depuis 16 h 02 » | « Your loved ones haven't heard from you since 4:02 PM » |
| Moi, zone de danger | « Tu es dans une zone de danger de ta Miff » (voir Q3) | « You're in a danger zone of your Miff » |
| Pagination (lecteur d'écran) | « Alerte {n} sur {m} » | « Alert {n} of {m} » |
| Hors connexion | « Dernières informations de 16 h 02 » ; sur alerte : « Informations de 16 h 02 : reconnecte-toi pour les mettre à jour. » | « Latest information from 4:02 PM » ; « Information from 4:02 PM: reconnect to update it. » |
| Partage arrêté (moi) | « Tu as arrêté de partager. Reprends le partage pour voir les nouvelles de tes proches. » | « You stopped sharing. Resume sharing to see your loved ones' news. » |
| Publication en cours | « Partage de ton statut en cours… » | « Sharing your status… » |

### Carte
| Élément | FR | EN |
|---|---|---|
| À (catégorie) | « À la maison » / « Au travail » / « À l'école » / « Au sport » / « Chez un proche » | « At home » / « At work » / « At school » / « At the gym » / « At a loved one's » |
| À (nom : Loisir, Autre, sans catégorie) | « {nom} » (le nom seul, PM 2026-11-08) | « {name} » |
| A quitté | « A quitté la maison » / « A quitté {nom} » | « Left home » / « Left {name} » |
| Zone de danger | « Dans une zone de danger » | « In a danger zone » |
| Lieu générique | « Dans un lieu de confiance » / « A quitté un lieu de confiance » | « In a trusted place » / « Left a trusted place » |
| Neutre | « Pas encore de nouvelles » / « Partage arrêté » | « No news yet » / « Sharing stopped » |
| Localisation limitée | « Localisation limitée » | « Limited location » |
| Temps | « Depuis 13 h 36 » (arrivée ou départ, D51) ; « Dernière nouvelle à 16 h 02 » ; « Pas de nouvelles depuis 16 h 02 » ; « Depuis 16 h 02 » | « For 12 min » ; « Left 12 min ago » ; « Latest news at 4:02 PM » ; « No news since 4:02 PM » ; « Since 4:02 PM » |
| Photo | « Photo de 15 h 40 » | « Photo from 3:40 PM » |
| Batterie | « 85 % » ; « 85 % · 16 h 02 » | idem |
| Boutons (a11y) | « Localiser {Prénom} », « Écrire à {Prénom} », « Appeler {Prénom} » | « Locate {Name} », « Message {Name} », « Call {Name} » |
| Ajouter un proche | « Ajouter un proche » ; « Cette Miff est bien remplie ! » | « Add a loved one » ; « This Miff is nicely full! » |

### Feuille « Localiser » et photo
| Cas | FR | EN |
|---|---|---|
| Titre | « Dernière position connue de {Prénom} » | « {Name}'s last known position » |
| Récente | « Mesurée à 08 h 12 (il y a 20 min). Ce n'est pas une position en direct. » | « Measured at 8:12 AM (20 min ago). This isn't a live position. » |
| Bouton | « Ouvrir dans les cartes » / « Fermer » | « Open in Maps » / « Close » |
| Ancienne | « Elle date d'hier à 08 h 12 : elle n'est plus affichée. » | « It dates from yesterday at 8:12 AM: it's no longer shown. » |
| Introuvable | « {Prénom} n'a pas encore de position connue. » | « {Name} has no known position yet. » |
| Refus | « On ne peut pas afficher la position de {Prénom} pour le moment. Elle apparaît quand vous partagez tous les deux. » | « We can't show {Name}'s position right now. It appears when you both share. » |
| Hors connexion | « Pas de connexion pour le moment. Réessaie dès que le réseau est revenu. » | « No connection right now. Try again once you're back online. » |
| Photo plein écran | « Photo de {Prénom} · 15 h 40 » ; « Voir où c'était » ; « Partage ta position pour voir cette photo. » | « {Name}'s photo · 3:40 PM » ; « See where it was » ; « Share your position to see this photo. » |
| Sans application | « Cette action n'est pas disponible sur cet appareil. » | « This action isn't available on this device. » |

---

## 8. Cas limites

1. **Seul dans la Miff** : bulle neutre « Tu es seul(e)… », ma carte, « Ajouter un proche ».
2. **Membre sans téléphone** (compte antérieur à ONB-B) : boutons Message et Appeler absents ; Localiser reste.
3. **Membre dont le partage est arrêté** : sa fiche existe, son statut est supprimé : carte neutre « Partage arrêté », **ignorée** de la couleur et du « {n} sur {m} » (compté « sans nouvelles », sans alerte, `data-model.md` §3).
4. **Membre qui vient d'entrer** : statut initial `unknown` → neutre « Pas encore de nouvelles ».
5. **Plusieurs alertes** : carrousel ; l'une disparaît (retour au vert) : la page disparaît, la pagination s'adapte, pas de saut de page brutal.
6. **Je suis le seul rouge ou orange** : bulle à la 2e personne.
7. **Plusieurs Miffs** : écouteurs de la seule Miff affichée ; changement de Miff = détacher puis rattacher ; mon anneau d'avatar suit mon statut dans la Miff affichée.
8. **Lieu supprimé ou modifié** : statut `unknown` côté appareil de l'auteur ; côté Feed, libellé générique tant que `placeId` est introuvable.
9. **Même `placeId` dans deux Miffs** (`p1` / `p2` gratuits) : toujours lu dans les lieux **de la Miff affichée**.
10. **Téléphone d'un proche éteint avant l'écriture du départ** : il reste « vert, à la maison » sans limite (limite connue N9, **acceptée par le PM**) ; la batterie datée et « Depuis … » en sont les seuls indices.
11. **Horloge du lecteur décalée** : un repère dans le futur = vert ; une horloge en avance fait passer au orange / rouge trop tôt (limite connue de `data-model.md` §3).
12. **Photo expirée mais `hasPhoto = true`** (l'auteur ne l'a pas nettoyée) : aucune photo, aucun message.
13. **Position de plus de 24 h** : feuille avec la date, sans carte.
14. **Mode avion ≠ accès perdu** : seul un refus de permission ou une Miff introuvable déclenche le message d'accès perdu (Miff §5.7).
15. **Accès perdu** pendant que le Feed est ouvert : message unique du ticket Miff, nettoyage, bascule.
16. **Retrait de mon statut** (révocation depuis un autre appareil) : l'écouteur est terminé ; V6.
17. **Taille de texte maximale, droite-à-gauche, TalkBack / VoiceOver** : cartes lisibles, boutons d'au moins 48 dp / 44 pt, ordre de lecture : carte par carte.
18. **15 membres** : jusqu'à ~90 lectures à froid (§11) ; photos paresseuses ; « Cette Miff est bien remplie ! ».
19. **Premier lancement hors connexion** : « Pas de connexion… » sans « tout va bien ».
20. **Changement de langue en cours** : tous les textes et formats d'heure suivent immédiatement.
21. **En-tête qui se masque (D41)** : liste courte (rien à défiler), chargement, erreur, hors connexion, TalkBack actif, sélecteur ouvert : l'en-tête **reste visible** ; un défilement rapide vers le bas puis un léger retour réaffiche l'en-tête dès la remontée ; le masquage n'enlève **aucune** information (le nom de la Miff et l'anneau de mon statut reviennent à la remontée).
22. **Mouvement de la bulle (D40, amplitudes D45)** : « Animations supprimées » = bulle immobile ; bulle hors écran ou application en arrière-plan = mouvement arrêté (aucune consommation) ; même amplitude en vert, orange et rouge.

---

## 9. Découpage proposé (Android d'abord, puis iOS ; une branche et une PR par sous-ticket)

| # | Ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| **FEED-0** | **Décisions et maquettes** (PM, `design-system`) | Réponses à Q1 à Q3 ; maquettes corrigées et complétées (§16) dans « Miff - Claude » ; exports PNG dans `design/exports/feed/` | S à M | Validation de ce document |
| **FEED-1** | **Android : cercle** | **En-tête qui se masque au défilement vers le bas et réapparaît en remontant (D41, remplace « fixe »)** (avatar, logo, pastille / sélecteur), cartes **sans statuts** (avatar, prénom, boutons **Message** et **Appeler**), ma carte, « Ajouter un proche », états aucune Miff / seul / chargement / hors connexion / accès perdu ; `MiffMember.phone` | M | ONB-3a, MIFF-1 (livrés) |
| **FEED-2** | **Android : statuts** | Écouteur de statuts, `StatusColor.level` (+ minuteur), couleur de la Miff, bulle et carrousel, étiquettes, lignes de temps, batterie, états neutres (partage arrêté, en attente d'accord, hors connexion), « Localisation limitée » | M à L | FEED-1, **LOC-2b fusionné** ; Q3 |
| **FEED-3** | **Android : Localiser** | Lecture de `positions/{uid}` au toucher, feuille, ouverture des cartes du système | S à M | FEED-2, LOC-2b (écrit les positions) |
| **FEED-4** | **Android : photo d'update** | Lecture paresseuse, copie locale, **aperçu carré centré dans la carte (D39)**, plein écran (photo complète), (localisation de la photo si Q1 déployée) | M | FEED-2, **UPDATE-1**, R-PHOTO-LOC (pour la localisation seulement) ; Q1, Q2 |
| **FEED-5 à FEED-8** | **iOS** : mêmes livrables que FEED-1 à FEED-4 | Spécificités §10 | Même taille | Équivalents Android, INIT-4 |
- **Ordre conseillé** [Proposé, Q2] : FEED-1 → FEED-2 → FEED-3 maintenant ; FEED-4 avec l'Update. Chaque PR **liste ses écarts avec les maquettes** et **mesure ses lectures** (§11).
- Aucune sous-tâche n'écrit de règle Firestore. `backend-data` prépare R-PHOTO-LOC (sur « oui » pour la préparation et le déploiement).

---

## 10. Différences Android / iOS

| Sujet | Android | iOS |
|---|---|---|
| En-tête qui se masque au défilement (D41), minuteur | Barre en haut qui se masque en descendant et réapparaît en remontant ; coroutine alignée sur la minute | `TimelineView(.everyMinute)` ; zone sûre |
| Carrousel de bulles | Pager horizontal ; boutons précédent / suivant pour TalkBack | `TabView` à pages ; actions VoiceOver |
| Message / Appeler | Intentions « écrire à » et « composer » (pas d'appel direct) | `sms:` / `tel:` (iOS demande confirmation avant l'appel) ; aucune clé de permission « contacts » |
| Localiser | `geo:` + repli web ; aucun libellé transmis | Plans (`maps.apple.com`), coordonnées seules |
| Écouteurs | Détachés à l'arrêt de l'activité | Détachés au passage en arrière-plan de la scène |
| Photo en plein écran | Zoom au pincement, retour système | Zoom au pincement, geste de fermeture, Dynamic Type |
| Applications récentes | `RecentsPrivacy()` | Écran de masquage au passage en arrière-plan [À vérifier par `ios-dev`] |
| Tests | OPPO Find X5 Lite (Android 13), **ColorOS** peut tuer l'app : sans effet sur le Feed (rien n'est écrit) | iPhone 11 Pro via TestFlight uniquement (aucune compilation locale) |
| Zones tactiles | 48 dp | 44 pt |

---

## 11. Coût Spark : budget de lectures (objectif : 0 €, et **voir venir** le plafond)

**Hypothèses** (estimations, pas des mesures) : Miff de 4 membres (moi + 3), 3 lieux, 2 photos non expirées parmi les 3 proches ; 5 ouvertures par jour dont 3 à froid ; 3 proches qui changent d'état ~7 fois par jour ; 2 appuis sur Localiser par jour.

**Une ouverture à froid** (formule : **9 + 2 × membres + lieux** lectures, plus **3 par photo lue**) :
| Taille de la Miff | Sans photo | Avec photos (la moitié des membres) | Pire cas (toutes les photos) |
|---|---|---|---|
| 4 membres, 3 lieux | ~20 | ~26 | ~32 |
| 8 membres, 4 lieux | ~29 | ~41 | ~53 |
| 15 membres, 5 lieux | ~44 | ~68 | ~89 |

**Par utilisateur actif et par jour** (4 membres) : 3 ouvertures à froid ≈ 78 ; 2 rattachements à chaud ≈ 24 ; changements reçus en direct ≈ 7 ; nouvelles photos ≈ 6 ; Localiser (1 + 5 règles = 6 chacun) ≈ 12 → **~127 lectures**, contre **~75** dans `data-model.md` §12 (qui ne comptait ni photo ni position).
- **Plafond** : 50 000 ÷ ~127 ≈ **~390 utilisateurs actifs par jour** (au lieu de ~650) ; seuil d'alerte (30 000 lectures) vers **~235**. Une **Miff Premium dans deux Miffs** double le coût d'ouverture.
- **Écritures** : **0** (le Feed n'écrit rien). **Transfert** : une photo ≤ 150 Ko ; sans copie locale, ~0,9 Mo par jour et par utilisateur (~27 Mo par mois, soit ~370 utilisateurs sur 10 Gio) ; **avec** la copie locale (une photo n'est retéléchargée que si elle change) ~7 Mo par mois.
- **Premier plafond atteint** : les **lectures**, puis le **transfert** si la copie locale n'est pas faite.

**Mitigations gratuites (écrites en critères d'acceptation)** : photos **paresseuses** (carte visible) et **non retéléchargées** (§5.7) ; photo non lue si `lastUpdateAt` a plus de 25 h ; écouteurs **détachés en arrière-plan** et **un seul jeu** pour la Miff affichée ; aucune interrogation périodique ; Localiser **à l'appui seulement** ; minuteur local sans lecture.

**Signal et option payante** : lectures du jour > **30 000** de façon répétée ou **ratio > 150 lectures par utilisateur actif deux jours de suite** (voir ci-dessous) → décision PM sur **Blaze** (coût indicatif : quelques euros par mois à ~2 000 utilisateurs actifs par jour, estimation à confirmer). **Jamais de contournement** des conditions d'utilisation pour rester gratuit.
- **Recalibrage demandé à `backend-data`** : `docs/dev/surveillance-quotas.md` fixe « À examiner » à un ratio **> 100** et « Signal » à **> 150** en partant de ~75 ; avec le Feed complet, le **ratio normal est ~130**. **Sans recalibrage, la surveillance donnerait un faux « À examiner » tous les jours.** À proposer après la première mesure réelle (PR FEED-2 ou FEED-4).
- **Chaque PR Feed mesure** : lectures d'une ouverture à froid, d'un rattachement à chaud, d'une photo, de Localiser (émulateur et compteurs de la console), et les reporte.

---

## 12. Risques

| # | Risque | Effet | Mitigation |
|---|---|---|---|
| R1 | **Ton anxiogène** : bulle rouge, « zone de danger » (wording du PM) montré à tout le cercle, « Pas de nouvelles » | Perte de confiance, contre-sens avec CLAUDE.md | Phrases factuelles à l'heure absolue ; aucune animation d'alarme ni son ; pas de défilement automatique ; action douce « Envoyer un message » ; relecture à voix haute par le PM ; **R16 déjà assumé** (zone de danger utilisable comme outil de contrôle) |
| R2 | **Fausse réassurance** : « vert, à la maison » d'un téléphone éteint (N9), batterie ou photo anciennes, cache hors connexion | Un proche croit que tout va bien | Tout est **daté** ; « Dernières informations de … » ; photo et batterie avec leur heure ; limite N9 **acceptée par le PM** |
| R3 | **Fausse alerte** : horloge décalée, faux départ en bordure de zone (aucune temporisation, décidé), cache hors connexion qui passe orange | Inquiétude inutile | Mention « Informations de … » sous l'alerte hors connexion ; heures absolues ; écart d'horloge documenté |
| R4 | **Position stockée** lisible des proches (**R18 assumé**) ; Localiser sur une position ancienne | Perte de confiance, anti-stalkerware | Réciprocité, masquage après 24 h, date toujours visible, **aucune étiquette** transmise aux cartes, texte de consentement (version 3), pas de carte intégrée |
| R5 | **Photo localisée contournant la réciprocité** si R-PHOTO-LOC n'exige pas `positionPublished` | Contradiction avec le texte de consentement | Q1 ; règle écrite au §6.3 |
| R6 | **Quota Spark** : Feed = premier consommateur (~127 lectures par utilisateur actif) | Application « cassée » pour tous au plafond | §11 ; mesures ; recalibrage des seuils ; Blaze décidé par le PM |
| R7 | **Numéro de téléphone rendu plus visible** par les boutons (R7 d'`onboarding.md`, déjà assumé) | Fuite de numéros | Aucune nouvelle donnée ; mention de visibilité déjà exigée |
| R8 | **Recette impossible sans écriture réelle** : sans LOC-2b, aucun statut ; sans Update, aucune photo | Recette PM retardée | Découpage §9 ; jeu de données de test documenté (un statut par cas) ; recette sur téléphone dès FEED-2 + LOC-2b |
| R9 | **Maquettes divergentes du code** | Développement qui invente | §4 liste les écarts ; chaque PR y renvoie |
| R10 | **Aucun test iOS local** (pas de Mac) | Cycle lent | Android d'abord ; **mêmes cas de test** pour la logique de couleur et de libellés (contrat) |
| R11 | **Textes non relus par un juriste** (décision du 2026-10-23) | Risque assumé | Aucun texte ne prétend l'avoir été |

---

## 13. Critères d'acceptation (testables ; Android puis iOS sauf mention)

**En-tête**
- [ ] **H1** L'en-tête **se masque quand on fait défiler vers le bas et réapparaît dès qu'on remonte (D41, remplace « fixe » ; voir H3)** ; l'avatar (photo ou initiales) a un **anneau** de la couleur de mon statut (neutre si inconnu) et ouvre l'onglet Profil ; le logo fait remonter le fil tout en haut ; **aucune cloche** ; chaque élément a un nom pour le lecteur d'écran (l'avatar annonce aussi mon état).
- [ ] **H3** (D41) L'en-tête (photo de profil, logo, nom de la Miff avec son sélecteur, futur bouton de notifications) **se masque au défilement vers le bas et réapparaît dès qu'on remonte** ; il **reste visible** pendant le **chargement**, en **erreur**, **hors connexion**, quand la **liste est courte** (pas de défilement possible), avec **TalkBack / VoiceOver actif**, et **tant que le sélecteur est ouvert** ; le logo (remonter le fil) et le masquage ne se contredisent pas (le toucher du logo ramène en haut et réaffiche l'en-tête) ; animation supprimée si « réduire les animations » (masquage instantané) ; test de chacun de ces états.
- [ ] **H2** Une seule Miff : la pastille affiche son nom et **n'est pas cliquable** ; plusieurs Miffs (Premium) : liste ; « + Rejoindre / + Créer » **uniquement** pour un Premium ; la Miff affichée est une préférence locale purgée à la déconnexion.

**Bulle**
- [ ] **B1** La couleur de la Miff suit §5.2 (tests unitaires de toutes les branches, dont : un rouge + des verts = rouge ; orange + vert = orange ; neutres ignorés ; aucun statut connu = neutre).
- [ ] **B2** « Tout va bien » n'apparaît que s'il y a au moins **un autre** membre au statut connu et tous les connus sont verts ; « 3 sur 4 ont donné des nouvelles » s'ajoute s'il y a des neutres ; **aucune phrase de sécurité** sans statut connu ni avec zéro autre membre.
- [ ] **B3** Plusieurs alertes = carrousel (rouges puis oranges, repère le plus ancien d'abord) ; les pastilles n'existent **que** s'il y a plusieurs pages ; **aucun défilement automatique** ; chaque page est atteignable sans balayage ; une alerte qui disparaît retire sa page.
- [ ] **B4** Les phrases sont celles du §7 : « {Prénom} a quitté {lieu} à 16 h 40 », « Pas de nouvelles de {Prénom} depuis 16 h 02 », « {Prénom} est dans une zone de danger » ; **« zone à risque » n'existe nulle part** (code, clés, a11y).
- [ ] **B5** Le repère « Dernière nouvelle » est le plus récent des repères des **autres** membres (jamais le mien) ; **un seul repère de temps** par bulle.
- [ ] **B7** (D40, amplitudes remplacées par D45) La bulle de la Miff a un **mouvement continu visible et organique** : **étirement / écrasement déphasé (largeur de 100 % à 92 %, hauteur de 100 % à 90 %), inclinaison ±0,8°, dérive ±3,5 dp** (forme seule déformée, **pas le texte**), **cycle de 5 à 8 s**, **aucun clignotement ni rebond** ; **immobile** avec « Animations supprimées » ; **arrêté hors écran** (défilement, autre onglet, arrière-plan) ; **même amplitude** quel que soit le niveau ; rien n'est annoncé au lecteur d'écran.
- [ ] **B6** Une alerte (orange ou rouge) sur un **autre** membre propose « Envoyer un message » (SMS) ; jamais sur ma propre page.

**Cartes**
- [ ] **C1** Chaque membre, moi compris (**en dernier, D43**), a une carte : avatar à anneau, prénom, étiquette (texte + icône), ligne de temps, batterie ; **ma carte n'a ni Localiser, ni Message, ni Appeler** et reflète mon document de statut.
- [ ] **C2** Étiquettes et lignes de temps conformes aux tableaux §5.4 et §5.5, dont : vert jusqu'à 2 h 00 incluses après un départ ; orange au-delà de 2 h 00 ; rouge au-delà de 4 h 00 (**tests aux limites 1 h 59 vert, 2 h 00 vert, 2 h 01 orange, 3 h 59 et 4 h 00 orange, 4 h 01 rouge**, constantes lues dans `StatusThresholds`) ; lieu supprimé = libellé générique ; **aucune couleur seule** ; contraste AA.
- [ ] **C3** Le niveau est **recalculé chaque minute** sans lecture serveur (test avec une horloge simulée) et annoncé au lecteur d'écran **seulement quand il change**.
- [ ] **C4** Batterie : pastille « 85 % » **sans heure de mesure (D44)** ; **masquée** au-delà de 6 h (`updatedAt`, D44) ou si absente ; aucune alerte de batterie faible.
- [ ] **C5** Partage arrêté = « Partage arrêté », neutre, **ignoré** du calcul ; « Localisation limitée » = badge doux sans effet sur la bulle.
- [ ] **C6** « Ajouter un proche » ouvre le partage du code (déjà livré) ; à 15 membres : « Cette Miff est bien remplie ! ».
- [ ] **C7** L'ordre est stable (les autres dans l'ordre du créateur puis `joinedAt`, **moi en dernier, D43**) et ne change **jamais** selon l'urgence.

**Message / Appeler**
- [ ] **M1** « Message » et « Appeler » ouvrent l'application du système avec le numéro de la fiche membre **préparé** (aucun appel ni SMS envoyé par Miff) ; absents sans numéro et sur ma carte ; **aucune permission** « appel », « SMS » ou « contacts » dans le manifeste (iOS : aucune clé « contacts ») ; zone tactile ≥ 48 dp / 44 pt ; nom lisible (« Appeler Thomas »).

**Localiser**
- [ ] **L1** Le bouton n'existe qu'avec LOC-2b ; il lit **une seule** `positions/{uid}` **depuis le serveur** (pas d'écouteur, pas de liste, jamais le cache) ; chargement visible.
- [ ] **L2** Mesure de moins de 24 h : la feuille affiche **la date et l'heure** et « Ce n'est pas une position en direct » ; « Ouvrir dans les cartes » ouvre la carte du système sur les **coordonnées seules** (**aucun nom ni étiquette** transmis).
- [ ] **L3** Mesure de plus de 24 h : date affichée, **aucune coordonnée, aucun bouton de carte** ; introuvable, refusé, hors connexion : les trois messages calmes du §7, **jamais un message d'erreur technique** ni « quota ».
- [ ] **L4** Aucune coordonnée dans un journal, un rapport de plantage, un événement d'analyse, un message de partage ni l'état sauvegardé de l'écran ; la position n'est pas conservée après la fermeture de la feuille.

**Photo**
- [ ] **P1** La photo n'est lue que si `hasPhoto` est vrai et `lastUpdateAt` a moins de 25 h ; **à l'apparition de la carte** ; **pas de retéléchargement** d'une photo inchangée (copie locale par `createdAt` / `lastUpdateAt`) ; une photo expirée n'est **jamais** affichée, même en cache ; refus ou introuvable = **aucune photo, aucun message**.
- [ ] **P2** Plein écran : photo, « Photo de {Prénom} · {heure} », fermeture ; zoom ; **avec** localisation si Q1 est déployée (carte du système, sans nom), **sans** rien d'inventé sinon.
- [ ] **P3** Le Feed **n'écrit et ne supprime aucune photo**.
- [ ] **P4** (D39) Dans la carte d'un membre (la mienne comprise), l'aperçu de la photo est un **carré aussi large que la carte, centré sur le milieu de la photo, sans déformation** (test avec des photos de ratios différents : l'image n'est jamais étirée) ; **au toucher, la photo s'ouvre complète en plein écran** (aucun recadrage).

**États et hors connexion**
- [ ] **E1** Aucune Miff : « Pas encore de Miff » + deux boutons, **aucune phrase de sécurité** ; chargement : squelette (sans animation si « réduire les animations »).
- [ ] **E2** Statuts pas encore lisibles : « Partage de ton statut en cours… », **jamais une erreur** ; l'écouteur de statuts est **rattaché après** publication de mon statut et **rattaché de nouveau** après chaque republication ; refus de permission géré sans crash.
- [ ] **E3** En attente de mon accord : l'encart de LOC-2b est affiché ; partage arrêté par moi : V6.
- [ ] **E4** Hors connexion : « Dernières informations de {heure} », **jamais de vert sans date**, bulles orange et rouge avec la mention de reconnexion ; **mode avion ≠ accès perdu** ; accès perdu : message unique, nettoyage, bascule vers une autre Miff ou l'écran de choix.

**Vie privée et quota**
- [ ] **V1** `RecentsPrivacy` conservé ; **aucun événement Analytics** ; aucun prénom, nom, numéro, lieu ni coordonnée dans un journal ou un rapport de plantage ; `toString()` masqués sur les classes qui portent ces données.
- [ ] **Q1** **0 écriture, 0 suppression** sur Firestore depuis le Feed.
- [ ] **Q2** Écouteurs détachés en arrière-plan ; **un seul jeu** d'écouteurs (Miff affichée) ; aucune interrogation périodique ; les **lectures sont mesurées et reportées dans la PR** (ouverture à froid, rattachement à chaud, une photo, Localiser) et comparées au §11.

**Textes, accessibilité, thème**
- [ ] **T1** Textes `feed_` en FR et EN, clés identiques sur les deux plateformes (parité vérifiée par la CI), tutoiement, sans accord de genre, formats d'heure du téléphone ; aucune valeur de design en dur ; préfixe ajouté dans `docs/contracts/i18n-keys.md` et le script de parité.
- [ ] **T2** Lecteur d'écran : chaque carte se lit comme un tout (« Thomas, à la maison depuis 12 min, batterie 85 % ») ; la bulle est une région annoncée au changement de niveau ; taille de texte maximale et droite-à-gauche testées.

**iOS (FEED-5 à FEED-8)**
- [ ] **I1** Mêmes critères, vérifiés par TestFlight sur l'iPhone 11 Pro ; `sms:` et `tel:` ; Plans pour Localiser ; VoiceOver ; Dynamic Type ; mêmes cas de test de la fonction de couleur et des libellés que l'Android (contrat).

**Recette PM sur téléphone** (deux comptes, OPPO et iPhone, dans la même Miff, LOC-2b installé) : sortir d'un lieu et vérifier vert, puis orange à 2 h, puis rouge à 4 h (seuils du 2026-11-08) ; toucher Localiser et vérifier l'heure ; arrêter le partage d'un des deux ; couper le réseau ; changer la langue.

---

## 14. Dépendances

| Dépendance | État au 2026-11-05 |
|---|---|
| ONB-3a (barre d'onglets, Profil), MIFF-1 / MIFF-2 (Miff, membres, partage du code) | **Livrés** sur `main` |
| LOC-1 (lieux, `Place.category`) | **Livré** |
| **LOC-2b** (écriture des statuts et des positions, `StatusColor.level`, encart « En attente de ton accord ») | PR ouverte (`android/loc-2b-tracking`), **non fusionnée** : **bloque FEED-2 et FEED-3** |
| **Règles Firestore** (statuts, positions, photos, lieux) | **Déployées** (2026-10-30 et 2026-11-04) |
| **UPDATE-1** (écran Update, écriture de la photo) | **Ticket à écrire** : bloque FEED-4 |
| **R-PHOTO-LOC** (localisation de la photo) | **À préparer** par `backend-data` sur Q1 : bloque seulement la localisation de la photo |
| Recalibrage de `docs/dev/surveillance-quotas.md` | `backend-data`, après la première mesure |
| iOS : INIT-4 (socle), CI TestFlight | Livrés d'après le dépôt [À vérifier par `ios-dev`] |
| Export PNG des maquettes corrigées | **Le PM** (§15) |

---

## 15. Ce que le PM doit fournir ou faire

Rien de ceci n'est fait par un agent.
1. **Répondre à Q1, Q2, Q3** (§17).
2. **Fournir l'export PNG** des maquettes corrigées et complétées (§16), rangés dans `design/exports/feed/` (règle du 2026-10-28).
3. **Confirmer** que la PR de LOC-2b est fusionnée avant FEED-2 (ou accepter de la recetter sur sa branche).
4. **Aligner la page Notion « Feed »** (« zone à risque », « Tout le monde est en sécurité », cloche) sur les décisions ci-dessus ; compléter ou supprimer la page vide « Notifs » ; la page « Tabbar » est vide.
5. **Autoriser** (si Q1 = oui) la préparation puis le déploiement de R-PHOTO-LOC par un « oui » explicite.
6. **Lancer le challenge du ticket Update** (écran de publication, alertes d'update) avant FEED-4.
7. Fournir pour la recette : **deux appareils** (OPPO Find X5 Lite, iPhone 11 Pro via TestFlight) et deux comptes dans la même Miff.

---

## 16. Maquettes manquantes (liste pour `design-system`, dans « Miff - Claude » uniquement)

Ce que les 9 écrans fournis **ne montrent pas** ou montrent avec un texte à corriger :
1. **En-tête sans cloche**, avec l'**anneau** de l'avatar dans les trois couleurs et en neutre.
2. **Bulle neutre** (« Pas encore de nouvelles », « Tu es seul(e)… »), **bulle verte avec seconde ligne** (« 3 sur 4 ont donné des nouvelles »).
3. **Bulle orange et rouge** retextées : « {Prénom} a quitté {lieu} à 16 h 40 », « Pas de nouvelles de {Prénom} depuis 16 h 02 », **« zone de danger »** (les maquettes 7 et 8 disent « zone à risque » et « Plus de nouvelles ») ; **bouton « Envoyer un message »** ; **version à la 2e personne** (moi).
4. **Pagination** : pastilles (existent aux maquettes 6 et 8), **sans défilement automatique**, avec « Alerte 1 sur 2 » pour le lecteur d'écran.
5. **Carte « moi »** (sans boutons), **carte neutre** (« Partage arrêté », « Pas encore de nouvelles »), **carte sans photo** (existe : Amandine), **carte avec badge « Localisation limitée »**, **carte rouge d'ancienneté** (étiquette « A quitté… » + « Pas de nouvelles depuis 16 h 02 »), **batterie datée** (« 85 % · 16 h 02 ») et masquée.
6. **Texte des étiquettes** dans une teinte foncée (contraste AA) et **textes sans fautes** (« Arrivé il ya 12 min », « Parti(e) »).
7. **Feuille « Localiser »** : récente, ancienne (sans bouton de carte), introuvable, refus, hors connexion, chargement.
8. **Photo en plein écran** : heure, bouton de fermeture, « Voir où c'était » (si Q1), photo localisée masquée faute de position publiée.
9. **Bandeaux d'état du Feed** : hors connexion, « Tu as arrêté de partager… », « Partage de ton statut en cours… », encart « En attente de ton accord » (existe : design 23 de LOC-2b).
10. **Squelette de chargement** ; **état « Cette Miff est bien remplie ! »** ; **sélecteur à une seule Miff** (pastille non cliquable ; la maquette 3 ne montre que le Premium).
11. **Variante iOS** : aucune différence de dessin attendue ; confirmer que le même export sert aux deux plateformes.

---

## 17. Questions pour le PM (courtes, avec ma recommandation)

Seuls les points qu'**aucun ticket ni aucune décision ne couvre**. Tout le reste est au §3 (décidé) ou au §5.9 (valeur par défaut, objection possible).

**Q1. Localisation de la photo d'update : où est-elle stockée et qui peut la lire ?** Les règles déployées n'ont **aucune place** pour elle dans le document photo ; la position (`positions`) est écrasée à chaque mesure, donc inutilisable pour « figée au moment de la photo ». Et le texte de consentement promet que la position n'est lisible que par ceux qui partagent aussi la leur.
- *Recommandation* : **autoriser `backend-data` à préparer R-PHOTO-LOC** : `lat` / `lng` nullables **dans le document photo** (effacés avec elle, 24 h), écrits avec le consentement version 3, et **lisibles seulement par un membre qui a publié sa propre position** (mêmes règles que « Localiser »). Conséquence : sans position publiée, on ne voit pas **les photos localisées** (message neutre). Déploiement sur ton « oui ».
- *Alternative sans règle* : la photo s'affiche **sans localisation** (heure seulement). *Impact* : FEED-4 ; aucun blocage du reste du Feed.

**Q2. Ordre de livraison avec l'Update.** LOC-2b écrit déjà les statuts et les positions, mais **la photo n'existe qu'avec le ticket Update** (pas encore écrit).
- *Recommandation* : livrer **FEED-1, FEED-2, FEED-3** maintenant (cercle, statuts, Localiser), puis **FEED-4 (photo)** avec le ticket Update, que tu lances en parallèle. *Impact* : tu recettes tout sauf la photo et sa localisation plus tôt.

**Q3. Texte de « zone de danger » pour la personne qui s'y trouve.** Le wording « zone de danger » est décidé pour ses proches ; le texte **à la 2e personne** n'est dans aucun ticket. « Tu es dans une zone de danger » est alarmant pour la personne elle-même.
- *Recommandation* : « **Tu es dans une zone de danger de ta Miff** » (dit que c'est une zone définie par ton cercle) avec la même couleur rouge ; la formule des proches reste « {Prénom} est dans une zone de danger ». À relire à voix haute avec les autres textes (§7). *Impact* : une clé de texte ; bloque seulement la rédaction définitive de FEED-2.

---

*Ce document ne modifie ni le code, ni Notion, ni Figma, ni les règles Firestore, ni `backend/data-model.md`. Aucun texte légal n'est rédigé et aucun n'est présenté comme relu par un juriste. Le statut des tickets Notion n'est pas modifié ; le PM en garde la main.*
