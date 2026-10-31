# Journal des changements : Miff Android

Format : une entrée par fonctionnalité livrée, la plus récente en haut. Les choix "économiques" (contournements pour rester gratuit) y sont documentés avec leur limite et le signal de bascule.

## [0.8.0] - 2026-10-31 : MIFF-2, retirer un proche, quitter, supprimer la Miff, renouveler le code, « Prévenir mes proches »

Ticket `docs/tickets/miff.md` (§13 MIFF-2 : écrans H, I, J, K ; critères N, R, Q, S, D), décisions du PM `miff-questions-pm.md` (QM1, QM8, QM9) et du 2026-10-31 (vignette des applications récentes), fiches `design/screens/miff-dialogues.md` et `miff-ecran-miff.md`, maquettes `design/exports/miff/` (-1 : corbeilles rouges, -3 : dialogue de suppression). **Mise à jour du 2026-10-31** : MIFF-1 (PR #45, avec ses corrections de revue), les zones sûres (#46), le démarrage à froid (#49) et le suivi des revues (#50) sont fusionnés dans `main`, et la branche les a reçus (`git merge origin/main`, la PR cible `main`).

### Décision du PM du 2026-10-31 : le départ volontaire ne renouvelle pas le code
**Une sortie volontaire d'une Miff ne renouvelle PAS le code ; seul le retrait d'un membre par le créateur le renouvelle.** C'est déjà le comportement des règles déployées (un membre simple ne peut pas écrire la Miff) et du lot « Quitter » (4 suppressions, aucune écriture sur la Miff ni son code) : rien ne change dans le code ; un test (`MiffRepositoryMiff2Test`) le garantit. Tous les textes affichés ont été relus : aucun ne promet qu'un départ change le code (la question « Quitter » dit « Tu pourras revenir si on te redonne le code » ; l'invitation faite au créateur reste facultative). Consigné dans `docs/tickets/miff.md` §15 (QM16), `miff-questions-pm.md` et `backend/data-model.md`.

### Mis en conformité avec `main` à cette occasion
- **Marges communes** : l'écran de la Miff (avec ses nouveautés : menus « ⋯ », invitation, liens du pied d'écran) prend ses marges dans `ui/components/ScreenInsets.kt` (`miffScreenInsets()`) ; les questions et la fenêtre de progression sont des fenêtres du système (`MiffConfirmDialog`, `MiffProgressDialog`), placées hors des barres par Android, comme celles de MIFF-1. Aucune marge brute : `ScreenInsetsRuleTest` passe. Nouveaux cas dans `ScreenInsetsTest` (créateur avec invitation, paysage avec encoche, membre à la plus grande police, dernier lien atteignable au-dessus du clavier, pour le créateur et pour le membre).
- **Noms reçus du serveur** : les prénoms et noms des proches (ligne, menu « ⋯ », « Retirer {prénom} », « {prénom} ne fait plus partie… ») et le nom de la Miff (« Quitter la Miff « X » ? », « Supprimer la Miff « X » ? », message « Prévenir mes proches ») passent par `MiffName.fromServer` (caractères de contrôle et invisibles retirés, espaces retirés). Un prénom sans aucun caractère visible est remplacé, dans les questions, par le nom de famille (`MiffMember.callName`) ; il n'est jamais écarté de la liste (le créateur doit pouvoir le retirer).
- **Démarrage à froid** : `miffStore` et `miffDeletions` sont créés à la première utilisation (`by lazy`) ; la reprise d'une suppression est lancée par `MainShell`, affiché seulement une fois la session résolue (donc après la purge d'une déconnexion ratée) : aucun accès à Firestore avant `MiffApplication.startSession`.
- **Écran de la Miff** : `MiffScreenHost` (MIFF-1) reçoit les actions de MIFF-2 et ferme l'écran quand la personne a quitté la Miff. Les corrections de revue de MIFF-1 (page blanche après annulation, « déjà dans l'état voulu » traité comme un succès) sont conservées.

### Ajouté
- **Retirer un proche** (créateur) : menu « ⋯ » sur la ligne des autres proches (jamais sur la sienne, jamais pour un membre), question nominative, **un seul lot de 6 écritures qui renouvelle toujours le code** (fiche, statut, photo de la personne ; Miff, nouveau code, ancien code supprimé), puis « {Prénom} ne fait plus partie de la Miff » avec **le nouveau code** et « Partager le code ». Une cible déjà partie est acceptée (le code est renouvelé quand même). Les composants sans interface (`MiffRepository.removeMember`, `MiffDeletion`) sont prêts pour AUTH-5 et le ticket Premium.
- **Renouveler le code** (créateur) : lien « Créer un nouveau code » sous le code, question, lot de 3 écritures, « Nouveau code créé. » ; l'ancien code cesse de fonctionner, les membres restent et voient le nouveau code en direct. **Invitation discrète** après un départ constaté (« Un proche a quitté la Miff… C'est facultatif. », « Plus tard »), une fois par départ, jamais pour un retrait que le créateur vient de faire.
- **Quitter la Miff** (membre) : lien en pied d'écran, question, lot de 4 suppressions, purge du cache, retour à l'accueil ; **aucun message d'accès perdu** pour un départ voulu. Le créateur n'a pas de « Quitter » : une phrase lui explique qu'il peut supprimer la Miff (QM1).
- **Supprimer la Miff** (créateur) : question avec le nombre d'autres proches (pluriel, jamais « 0 proche »), **« Prévenir mes proches »** (feuille de partage avec un message prérempli sans code, que le créateur envoie lui-même ; une phrase dit qu'aucune notification n'est envoyée), puis « On finalise la suppression… », « La Miff a été supprimée. ». Procédure **reprenable et idempotente** (retraits un par un, lieux 5 par lot, lot final avec pierre tombale), marqueur local écrit avant la première écriture, pause sans réseau et reprise automatique, reprise au lancement (« On reprend la suppression de ta Miff… »), « Retour » quitte l'écran sans l'interrompre.
- **Vignette des applications récentes masquée** (Android 13+, `Activity.setRecentsScreenshotEnabled(false)`, sans `FLAG_SECURE`) sur les écrans qui montrent le code, les noms ou les photos d'une Miff.
- Textes `miff_member_*`, `miff_remove_*`, `miff_renew_*`, `miff_leave_*`, `miff_delete_*` FR/EN (et catalogue iOS, ajouts seulement). Icône `ic_more_vert`.
- Tests : contenu exact et nombre d'écritures de chaque lot ; dépôt (code lu dans la transaction, jamais identique, collisions, réponse perdue vérifiée) ; **suppression interrompue à chacun des 5 points puis relancée** (aucun doublon), réponse perdue à chaque lot, Miff déjà disparue, marqueur, pause réseau, autre compte ; écran (qui peut demander quoi, une question avant chaque lot, un appui = une opération, hors connexion, départ voulu jamais annoncé comme une perte, invitation) ; 23 cas de règles à l'émulateur (`backend/tests/rules/18-miff2-*`) et 3 mesures de lectures ; Robolectric (menus, dialogues, 48 dp, plus grande police, mots interdits).

### Modifié
- `MiffRepository` : `renewCode`, `removeMember`, `leave` ; `MiffStore` : `readMemberIds`, `readPlaceIds`, `commitWithMiff` (transaction qui lit la Miff et l'existence de son code) ; `MiffWrites.leave` accepte un index introuvable. `MiffConfirmDialog` : bouton secondaire, ordre de lecture (question, « Annuler », action), variante informative. `MiffMemberRow` : menu. `MiffDirectory.expectDeparture`. `AppContainer` : `miffDeletions`, `onMiffLeft`.
- (Le défaut de MIFF-1 corrigé ici à l'origine, une ligne d'attache au cycle de vie entièrement en commentaire, est réglé dans `main` par `MiffScreenHost` et son test.)

### Écarts avec les maquettes et les fiches (liste justifiée dans la PR)
Corbeille rouge remplacée par « ⋯ » (§3.3 du ticket) ; dialogue de suppression : tutoiement, « Cette action est définitive » au lieu de « de manière irrévocable », bouton « Supprimer la Miff » blanc à contour rouge (pas de fond rouge, fiche `Tone=Sensitive`), « Annuler » en lien, phrase ajoutée sous « Prévenir mes proches » (le libellé validé par le PM pourrait faire croire à une notification automatique : risque R6) ; « Nouveau code créé » est une ligne qui s'annonce au lecteur d'écran, pas un toast (fiche K2) ; progression et fin de suppression en dialogue sur l'écran de la Miff, et en **message système court** quand l'écran est quitté (la fiche prévoit une `Notice Info` : un bandeau global est gardé pour plus tard, le message système n'ayant aucune marge à gérer) ; textes de pied pour le membre (§G de la fiche) et pour le créateur (QM1) ; les libellés « Terminer », « Fermer », « Plus tard » sont ceux de la fiche (à confirmer par le PM).

### Choix économiques et limites
- Aucun service, aucune dépendance, aucune permission ajoutée ; 0 €. **Aucune règle modifiée** (`backend/firestore.rules` identique à `main`).
- Lectures de règles (voir `backend/rules-test-report.md`, section MIFF-2) : retrait **18 sur 20 au pire cas, inchangé** (aucun appel ajouté) ; lot final de suppression 12 sur 20 ; 5 lieux par lot 10 sur 20. Écritures : retirer 6, supprimer N x 6 + lieux + 7 : très loin des 20 000 par jour de Spark. Lectures de la suppression : quelques lectures par membre retiré (code, Miff et document du code lus dans la transaction) : sans effet à 15 membres au plus.
- **Limite connue** : la suppression d'une grande Miff est longue (un lot par membre) ; si l'OPPO tue l'app, elle reprend au lancement suivant et non en arrière-plan (pas de service d'arrière-plan, volontairement).
- **Choix** : le marqueur local de suppression contient l'identifiant de la Miff et celui du compte qui l'a lancée (identifiants opaques, aucun nom ni code, fichier hors sauvegarde) : il survit à une déconnexion et reprend quand le même compte revient ; un autre compte sur le même téléphone ne le voit pas.
- **Limite connue (règles, R17)** : le lot final est accepté même s'il reste des fiches de membres ; la procédure du client les retire d'abord, mais un client modifié pourrait laisser des fiches orphelines (illisibles de tous : la Miff n'existe plus).
- **Limite connue** : avant Android 13, la vignette des applications récentes n'est pas masquée.
- Signal de bascule : voir `backend/data-model.md` §12 (ratio lectures par utilisateur actif).

## [0.7.0] - 2026-10-31 : MIFF-1, créer, rejoindre, écran de la Miff, partage du statut, accès perdu

Ticket `docs/tickets/miff.md` (§13, écrans A à G, L et M ; décisions du PM `miff-questions-pm.md` QM1 à QM15 et `feed-profil-reglages.md` §13, dont Q2), maquettes `design/exports/miff/`, `onboarding-original/` (-3 à -6, -18) et `feed/` (-2, -3). Retirer, quitter, supprimer, renouveler le code et « Prévenir mes proches » sont **MIFF-2** : aucun bouton n'est dessiné pour eux.

### Ajouté
- **Écran de choix** « Choisis comment tu veux commencer » (onboarding) : créer ou rejoindre ; l'onboarding n'est complet qu'avec une Miff. Le bouton de debug « Aperçu interne » est **retiré** (l'écran de choix permet de créer une vraie Miff) ; une déconnexion discrète reste sur cet écran.
- **Créer** en deux temps : « Nouvelle Miff » (nom 2 à 40 caractères, texte de partage au-dessus de « Créer et partager »), puis « Ta Miff « X » est prête » (code par groupes de 4, Partager, Copier). **Un seul lot atomique** de 5 écritures (Miff, code, fiche du créateur avec consentement daté et versionné, index, statut initial), code tiré à ce moment par `SecureRandom` (rejet des octets biaisés).
- **Rejoindre** : saisie du code (majuscules, ASCII, sans correction, espaces et tirets normalisés, « Coller » sur appui seulement, message local pour un caractère hors alphabet, pause d'une minute après 5 échecs), vérifications dans l'ordre du ticket §5.4 (format, lecture du code, nettoyage d'un index obsolète, déjà membre par le champ `miffId`), écran de confirmation avec le texte de partage **avant** « Rejoindre et partager », **un seul lot atomique** de 3 écritures (fiche avec partage activé, index, statut initial `unknown`). Rien n'est écrit avant l'appui.
- **Écran de la Miff** (créateur et membre) : nom (modifiable par le créateur, lot Miff + nom du code), code et partage (feuille système ; copie marquée « sensible » sur Android 13+), proches en direct (photo ou initiales, « Créateur », « Toi »), ligne **« Partager mon statut »** (arrêter / reprendre à tout moment, avec confirmation ; arrêt = fiche + suppression du statut et de la photo dans le même lot ; reprise = nouveau consentement + statut initial), lieux en liste, texte « Cette Miff est bien remplie ! » à 15 membres (plafond doux, non imposé par le serveur), lecture seule hors connexion.
- **Accès perdu** : détecté en direct (écouteur refusé ou Miff inexistante) et à l'ouverture (contrôle auprès du serveur) ; **une erreur réseau n'en est jamais une** (et un refus n'est retenu que si le serveur répond par ailleurs). Un seul message (« Tu n'as plus accès à la Miff « X » »), nettoyage (lot « Quitter », sinon l'index seul), purge du cache local, bascule vers l'état sans Miff.
- **Feed** : pastille avec le nom de la Miff (non cliquable avec un compte gratuit, sélecteur avec « + Rejoindre / + Créer une Miff » pour un Premium), état « Pas encore de Miff » avec « Créer une Miff » / « Rejoindre une Miff ». **Profil** : carte « Mes Miffs » qui ouvre l'écran de la Miff.
- **Modèle unique de dialogue de confirmation** (`MiffConfirmDialog`, ton calme, « Annuler » toujours disponible), réutilisable par MIFF-2 ; composants : champ de nom, champ de code, ligne de proche, pastille de rôle, ligne à interrupteur, en-tête de section, notice.
- `SessionManager.onMiffEntered` / `continueAfterMiff` ; `NetworkMonitor.observeOnline()` ; `MiffDirectory` (Miffs en mémoire pour la session, vidé à la déconnexion) ; `MiffRepository` (toute la logique Firestore, derrière `MiffStore`).
- Textes `miff_*`, `feed_*`, `profile_tab_*`, `common_cancel` FR/EN (tutoiement), **aussi ajoutés au catalogue iOS** pour la parité (code iOS : MIFF-3).
- Tests : code (alphabet, 1 000 tirages, biais, normalisation), nom (emojis non coupés), pause des essais, contenu exact des lots (clés des règles, dates serveur, aucune coordonnée), interprétation des erreurs (réseau ≠ accès perdu), dépôt (ordre des vérifications, nettoyage avant écriture, atomicité, hors connexion sans file d'attente, collision de code, clé de l'index gratuit / Premium), annuaire (message unique, purge, pas de boucle), view models, session (fin d'onboarding), écrans Robolectric (consentement avant le bouton, grande police, absence des actions MIFF-2, aucun statut sur l'écran de la Miff). Backend : `17-nom-miff-longueur.test.js` (voir ci-dessous).

### Modifié
- `AppRoot` : `MIFF_CHOICE` ouvre le vrai écran (l'écran provisoire de l'Auth et ses deux textes `android_*` sont supprimés). `FeedPlaceholderScreen` devient `FeedScreen`.
- `AppContainer` : un seul `CachePurger` partagé ; `miffDirectory` ajouté à la liste d'arrêt de la session.

### Fusion de main (zones sûres #46, démarrage à froid #49) et migration des écrans de MIFF-1
- **Zones sûres.** Tous les écrans ajoutés par MIFF-1 passent par `ui/components/ScreenInsets.kt` : `MiffFrame` (Créer, Rejoindre, Ta Miff est prête, choix), l'écran de la Miff, le message d'accès perdu : `miffScreenInsets()` à la place de `safeDrawingPadding()` ; le Feed (qui remplace `FeedPlaceholderScreen`) : `miffTopInsets()` à la place de `statusBarsPadding()`. Plus aucune marge système brute dans le code (la règle `ScreenInsetsRuleTest` passe). Clavier : le contenu défile au-dessus du clavier sur le nom de la Miff, le code et le nom à l'écran de la Miff. Dialogues et menu du sélecteur : fenêtres du système, rien à faire.
- **Tests** (`ScreenInsetsTest`) : 22 nouveaux cas pour MIFF-1 (Feed avec une Miff, sans Miff, illisible ; carte de Miff du Profil ; choix ; Créer (nom, prête) ; Rejoindre (code, confirmation) ; écran de la Miff (créateur, membre, chargement) ; accès perdu ; paysage avec encoche ; très grande police ; clavier ouvert). Les appels à `MainTabs` suivent ses nouveaux paramètres.
- **Démarrage à froid.** `cachePurger`, `miffRepository`, `joinLimiter` et `miffDirectory` deviennent paresseux (`lazy`) dans `AppContainer` ; `MiffDirectory` rejoint la liste d'arrêt de la session. Aucun accès Firestore de MIFF-1 avant `MiffApplication.startSession` (vérifié : la construction n'ouvre pas Firestore, tout passe par des fonctions appelées à la première requête, depuis des écrans affichés après le routage).
- **Catalogue iOS** (`Localizable.xcstrings`) : fusion des clés de l'authentification iOS (main) et des clés `miff_*` / `feed_*` / `profile_tab_*` ajoutées ici pour la parité ; aucune clé en conflit, parité Android / iOS vérifiée (349 clés communes).

### Corrigé après les revues sécurité et code (PR #45, second passage)
- **Nom de la Miff lu dans le code (M1 sécurité)** : `joinCodes.miffName` est écrit par le détenteur du code avant tout consentement du lecteur ; il passe maintenant par `MiffName.fromServer` (caractères de contrôle, bidirectionnels et invisibles retirés, espaces des bords retirés) avant d'être affiché (« Rejoindre la Miff « X » ? », « Tu fais déjà partie de la Miff X »). S'il ne reste rien de visible, la réponse est celle d'un code inconnu. Le commentaire de `MiffName` ne prétendait plus à tort que tous les noms du serveur étaient nettoyés.
- **Noms de lieux (L5)** : même nettoyage (`MiffPlace.fromServer`) ; un lieu sans nom visible n'est pas affiché.
- **Commentaire (L3)** : « Reprendre le partage » montre le même cadre et la même version de consentement qu'à l'entrée, avec sa propre phrase d'ouverture (textes inchangés).
- **Page blanche (CR-M1)** : si l'écran de la Miff passe en arrière-plan ou pivote pendant la vérification d'un accès perdu, l'état est remis à « chargement » et l'accès perdu est entendu de nouveau au retour (avant : état muet, sans message ni « Réessayer »). Test d'annulation en pleine vérification.
- **Partage déjà dans l'état voulu (CR-M3)** : si les règles refusent l'écriture (état déjà atteint depuis un autre appareil), la fiche du serveur est relue et l'état voulu compte comme un succès. Tests.

### Corrigé après la revue indépendante de la PR #45
- **I1** : « Tu n'as plus accès » n'est annoncé qu'après confirmation par le serveur, aussi quand c'est l'écran de la Miff qui entend le refus ; sans confirmation, rien n'est annoncé ni nettoyé et l'écran propose « Réessayer » (test : écouteur refusé + serveur injoignable = zéro message).
- **I2** : « Reprendre le partage » affiche le même cadre de consentement (même version de consentement ; seule la phrase d'ouverture diffère) que l'entrée.
- **I3** : le cadre de consentement dit que les lieux de confiance sont choisis par le créateur et visibles de tous les membres ; `MiffSharingConsent.VERSION` passe à `2026-10-v2` (documenté dans `backend/data-model.md` §4 et le README). Phrase par défaut, le PM peut encore l'ajuster (alors `v3`).
- **M1** limiteur d'essais porté par `AppContainer` (durée du processus). **M2** nettoyage silencieux d'un index obsolète : même purge du cache que pour un accès perdu. **M3** nom : coupe entre deux caractères entiers (`BreakIterator`), caractères de contrôle et bidirectionnels retirés à la saisie, à la lecture et dans le message de partage. **M4** écoute en direct sous `repeatOnLifecycle`. **M5** textes honnêtes pour « une seule Miff à la fois » et « profil manquant » (retour à l'étape profil). **M6** hors connexion avec un cache vide : « on ne sait pas » et non « pas de Miff ». **M7** décision H4 documentée (quota = « pas de connexion » ; refus App Check indistinguable d'un refus des règles = phrase générique). **M8** « Compris » ne ferme que l'écran de la Miff perdue ; « Ouvrir la Miff » compte la fin de l'onboarding ; lot à réponse perdue vérifié aussi pour renommer et arrêter / reprendre le partage. **M9** constantes de taille et d'opacité remplacées par des jetons (`Spacing`, `Type`, `Color`), `details()` supprimé, tests ajoutés (assemblage des écouteurs, message de partage, copie « sensible »).

### Écarts avec les maquettes (liste justifiée dans la PR)
Titre de l'écran = nom de la Miff ; un seul bouton de validation (coche retirée) ; pas de corbeille rouge ni de « Supprimer cette Miff » (MIFF-2) ; code absent de « Nouvelle Miff » (QM15) ; section « Lieux » de la création retirée ; champ de code sorti de la carte de choix vers une page dédiée ; vouvoiement remplacé par le tutoiement ; faute « saisit » supprimée ; code par groupes de 4 ; texte de partage ajouté ; « Rejoindre » devient « Continuer » puis « Rejoindre et partager » ; bandeau vert « Tout le monde est en sécurité » non dessiné quand il n'y a pas de Miff (décision Q12).

### Choix économiques et limites
- Aucun service, aucune dépendance, aucune permission ajoutée ; 0 €. Aucune règle modifiée.
- Lectures : ouverture du Feed = 1 lecture des appartenances + 1 lecture de la Miff par appartenance + 1 de l'abonnement ; écran de la Miff = 3 écouteurs (Miff, membres, lieux) soit 1 + N + nombre de lieux lectures à l'ouverture (le ticket borne à N + 4). Création : 1 lecture de vérification du code tiré + le lot. Entrée : lecture du code + appartenances + lot.
- **Limite connue** : le lot d'entrée « Créer et partager » d'un compte **Premium** qui garde un ancien emplacement gratuit est à **19 appels de règles sur 20** au pire sans cache (marge de 1) : aucun contrôle ne doit y être ajouté (voir `backend/data-model.md` §12). Le lot gratuit : 14 sur 20.
- **Limite connue** : sans permission de localisation demandée par l'app (ticket Localisation), `locationAccess` est écrit `unknown` ; il passera à `denied` / `foreground` / `background` avec ce ticket.
- **Limite connue** : le lien du message de partage est la racine du site public ; une page d'atterrissage dédiée (QM14) viendra avec le site.
- Signal de bascule : voir `backend/data-model.md` §12 (ratio lectures par utilisateur actif).
- **Limite connue (M2)** : le délai de 20 s d'une écriture (`write { ... }`) arrête l'attente de l'app, pas la transaction Firestore : une écriture dont la réponse tarde peut aboutir après le message d'échec. L'app le vérifie par une lecture serveur avant de parler d'échec (créer, rejoindre, renommer, partager) ; le cas résiduel est une transaction qui aboutit après cette vérification.
- **Limite connue (M4)** : tout refus des règles lors de « Rejoindre » affiche « On ne trouve pas de Miff avec ce code » : voulu pour un code renouvelé, mais cela couvre aussi un emplacement gratuit pris entre-temps, des conditions d'utilisation absentes ou un refus d'App Check ; le message peut égarer et le limiteur d'essais ne compte pas ces cas. Non modifié dans cette PR.
- **Limite connue (M5)** : un refus d'App Check et un refus des règles sont tous deux `PERMISSION_DENIED` et reçoivent la même phrase générique (écart H4, documenté dans le README).
- **Limite connue (F1)** : si la lecture de l'abonnement échoue (réseau instable), `premium()` renvoie « non premium » : un compte Miff+ peut alors se voir répondre « une seule Miff à la fois » à tort. À rendre explicite (échec) plutôt que « non premium » ; non corrigé dans cette PR.

## [0.6.5] - 2026-10-31 : suivi des revues (marges et démarrage), sans nouvelle fonctionnalité

Remarques non bloquantes des revues indépendantes des PR #46 (zones sûres) et #49 (démarrage à froid). Aucun changement visible attendu sur un téléphone qui fonctionne.

### Corrigé
- **Bas du repli des marges (`ScreenInsets.kt`)** : il lisait la hauteur et la position de la vue dans un résultat mémorisé ; à la première composition la vue n'est pas encore mesurée (hauteur 0), donc le bas restait figé à 0 sur un téléphone qui rapporte 0 pour la barre à 3 boutons. La position de la fenêtre et de l'écran vient maintenant du gestionnaire de fenêtres (connue avant toute mise en page), mémorisée et redemandée seulement quand la vue ou la configuration (rotation, redimensionnement) change, car cet appel n’est pas gratuit ; la visibilité des barres est lue comme un état Compose, donc le repli se recalcule quand elle change. Inchangé : une valeur réellement rapportée n'est jamais remplacée (maximum côté par côté), pas de double marge. Le haut utilise la même position de fenêtre (même défaut possible).
- **`MiffApplication.warmUp`** attrape `Throwable` (et non seulement `Exception`) : une erreur (`LinkageError`, mémoire) dans ce fil d'arrière-plan facultatif ne fait plus tomber l'app au lancement ; le fil principal construit ce qui manque. Aucun journal, aucune donnée personnelle.

### Ajouté
- Tests du bas du repli (`ScreenInsetsTest`) : 3 boutons rapportés à 0 (le bas de l'app reste au-dessus de la vraie barre), valeur rapportée conservée (plus petite et plus grande que la barre déclarée), barre de navigation masquée, paysage (inactif), tout premier affichage (test qui échoue avec l'ancienne lecture de la vue : vérifié), et règles pures (fenêtre qui n'atteint pas le bas ou le haut, mode gestes, fenêtre inconnue).
- `ScreenInsetsRuleTest` : exemptions **par chemin** (`ScreenInsets.kt`, `InsetsDiagnostic.kt`) et non par nom de fichier ; interdit en plus le paramètre `windowInsets =`, `WindowInsetsCompat`, `setOnApplyWindowInsetsListener` et `Scaffold` / `TopAppBar` (et variantes) / `BottomAppBar` / `NavigationBar` / `NavigationRail` (marges par défaut) : aucun faux positif sur le code actuel (test de motifs inclus). Limite : lecture du texte ligne par ligne, un alias d'import n'est pas vu.
- Commentaires : le repli est **inerte sous Android 10 et moins** ; `config_navBarInteractionMode` est une ressource privée (absente : bas du repli désactivé, côté sûr) ; `STARTUPMETRICSOVERLAY` ne doit pas être renommé (la CI le cherche dans le dex release) ; `MainTabs` devra gérer le clavier si un onglet reçoit un champ de saisie.

### Documenté
- `README.md` : l'APK `releaseLike` ne pourra plus joindre Firebase une fois l'application d'App Check activée (Play Integrity ne valide pas hors Google Play) ; il faudra alors un jeton de débogage App Check.

### Choix « économiques »
- Aucun : pas de nouvelle dépendance ni de service.

## [0.6.4] - 2026-10-31 : démarrage à froid, le « M » ne doit plus rester seul longtemps

Retour du PM après recette sur l'OPPO Find X5 Lite (APK debug de la CI) : le « M » seul reste près de 4 s avant le cœur ; pas sur iOS. Diagnostic complet et chronologie : `README.md`, « Démarrage à froid ».

### Cause trouvée (lecture du code ; non mesurée sur appareil)
Tout ce qui précède la première image de l'animation allonge le « M seul » : `Application.onCreate` construisait tout le conteneur, initialisait Firebase Auth et lançait la lecture de session (client Firestore compris) sur le thread principal ; puis la première composition de l'app (en debug : l'écran de diagnostic entier, galerie de tokens comprise) gelait l'écran **avant** que l'horloge de l'animation démarre.

### Modifié
- **`AppContainer` entièrement paresseux** (`lazy`) : le créer ne construit plus rien. `MiffApplication` construit le reste **sur un fil d'arrière-plan** (`warmUp`), après l'installation d'App Check.
- **Session démarrée après la première image** de l'animation (`MiffApplication.startSession`, appelée par `SplashHost.onFirstFrame`) ; la lecture Firestore du compte tourne hors du thread principal (`FirestoreAccountRepository`, `Dispatchers.IO`). Routage, règle « collecte en pause jusqu'à la lecture de `users/{uid}` » et purge du cache avant tout accès Firestore : **inchangés**. App Check reste installé en premier dans `Application.onCreate`, la pause de la collecte aussi (synchrone).
- **Le nettoyage des photos de la caméra** (fichiers) sort du constructeur : fil d'arrière-plan, après la première image.
- **L'app est composée sous l'animation quand le cœur est apparu** (0,5 s) et non plus à la 2e image : son gel tombe pendant le début du gonflement (cœur presque immobile) ; temps réel conservé, jamais plus de 3,0 s.
- **Le « M + i » est dessiné comme un tracé** (même contour que l'écran système, contrôlé par `SplashArtworkTest`) ; les tracés sont préparés hors du thread principal.
- **Premier beat de l'animation 300 → 100 ms** (`HOLD_MS`) : l'écran système a déjà montré le « M » ; durée totale **2,7 s** (2,9 s avant), toujours ≤ 3,0 s. Une constante à remettre à 300 si le PM préfère l'ancien rythme.
- **Écran de diagnostic (debug)** : seul l'en-tête est composé sous l'animation, le reste après son départ.

### Ajouté
- **Mesure du démarrage** (`startup/StartupTrace`, `domain/startup/StartupReport`) : sections de trace système `Miff.*` (`android.os.Trace`) ; encart « Mesure du démarrage » (debug et `releaseLike` seulement, retiré du release par R8 et contrôlé par la CI) avec départ → première image, départ → cœur visible, durée dans `Application.onCreate`, plus long arrêt de l'animation. Aucun journal (l'app ne journalise pas), aucune donnée personnelle.
- **Build `releaseLike`** (code release, R8, non « debuggable », signé avec la clé de debug) et son artefact CI `miff-releaselike-apk` (3 jours) pour comparer debug et release sur l'OPPO. Jamais publié ; aucune nouvelle clé, aucun secret ajouté (la CI utilise le keystore de debug déjà décodé depuis `DEBUG_KEYSTORE_B64`).
- **Baseline Profile** écrit à la main (`src/main/baseline-prof.txt`) + `androidx.profileinstaller` 1.4.1 (Apache 2.0, gratuit). Limite : sans appareil il liste des paquets et non les méthodes réellement appelées ; un profil enregistré (Macrobenchmark) viendra plus tard ; aucun effet en debug.
- Tests : `StartupReportTest`, `StartupTraceAndContainerTest` (le conteneur ne construit rien sans Firebase), `SplashHostTest` (l'app attend le cœur, composée tout de suite en « réduire les animations », première image), `SplashTimelineTest` (premier beat, moment de composition, 3,0 s).

### Choix « économiques »
- Aucun coût : bibliothèque AndroidX gratuite, build supplémentaire en CI (quelques minutes de calcul sur le quota gratuit de GitHub Actions ; signal de bascule : si la CI dépasse le quota gratuit, retirer `assembleReleaseLike` de la CI et le lancer à la demande).

### Rejeté
- **Icône animée (AnimatedVectorDrawable) de l'écran système** : raccord sans saut impossible à garantir sans appareil (durées inconnues de l'écran système, ColorOS, risque d'une double apparition du cœur contre la règle photosensible). Détails dans le README.

### Limites
- Aucune durée n'a pu être mesurée sur appareil : les gains sont attendus, pas chiffrés. À chronométrer par le PM (checklist dans la PR).
- `FirebaseInitProvider` et les initialiseurs `androidx.startup` s'exécutent encore avant `Application.onCreate`.

## [0.6.3] - 2026-10-31 : zones sûres, repli et diagnostic (la recette sur l'OPPO a montré le même défaut avec la PR #46)

Retour du PM : « j'ai toujours le même problème avec les marges » (logo du Feed à y≈45 px, sous l'heure). La cause du 0.6.2 (Scaffold + consumeWindowInsets) était réelle mais **pas suffisante** sur ce téléphone, et la cause exacte sur l'OPPO n'est **pas établie** (non reproduite : tests Robolectric API 35 et émulateur Android 14 corrects).

### Ajouté
- **Repli** dans `ScreenInsets.kt` (`miffSafeInsets`, `SystemBarsFallback`) : si le système rapporte 0 en haut (ou en bas avec boutons) alors que la barre existe, la hauteur déclarée par le système est utilisée ; jamais à la place d'une vraie valeur. Branché dans `MainActivity` seulement. Tests : `feed_whenThePhoneReportsNoStatusBarAtAll...` et `feed_whenThePhoneReportsARealValue...`.
- **Carte de diagnostic des marges** (debug seulement, première carte de l'écran de diagnostic) pour lire ce que l'OPPO rapporte vraiment.
- `adjustResize` conservé (ne peut pas être la cause : le défaut existait avant).

## [0.6.2] - 2026-10-31 : zones sûres (barre d'état, barre de navigation, encoche, clavier)

Retour du PM après sa recette sur l'OPPO Find X5 Lite : « tout est systématiquement trop haut » ; le logo « Miff » du Feed était dessiné par-dessus l'heure et les icônes.

### Corrigé
- **Cause** : `MainTabs` (Feed et Profil) utilisait un `Scaffold` puis `consumeWindowInsets(padding)` : la barre d'état était déclarée « déjà gérée » sans qu'aucune marge ne soit appliquée, donc le `statusBarsPadding()` des onglets recevait 0. Le `Scaffold` est retiré (colonne simple : contenu de l'onglet + barre d'onglets).
- **Un seul endroit pour les marges système** : `ui/components/ScreenInsets.kt` (`MiffScreen`, `miffScreenInsets`, `miffTopInsets`, `miffBottomInsets`, `miffTopInset`, `miffKeyboardVisible`). Tous les écrans existants (inscription, connexion, mot de passe oublié, panneau de consentement, accueil, profil, téléphone, recadrage, carrousel, onglets Feed et Profil, barre d'onglets, Réglages et ses 4 sous-écrans, diagnostic, feuilles Photo et Pays) l'utilisent ; plus aucun appel brut à `safeDrawingPadding`/`statusBarsPadding`/`navigationBarsPadding` ailleurs (`ScreenInsetsRuleTest`).
- Réglages : la barre du haut respecte aussi l'encoche sur les côtés en paysage ; barre d'onglets : respecte la barre de navigation latérale (3 boutons en paysage) et l'encoche ; feuille des pays : le champ de recherche reste visible au-dessus du clavier.
- `windowSoftInputMode="adjustResize"` sur `MainActivity` : le clavier est transmis à Compose sur Android 8 à 10 (sans cela l'écran pouvait « glisser » vers le haut, sous la barre d'état).

### Ajouté
- `ScreenInsetsTest` (22 tests Robolectric : insets réels simulés) et `ScreenInsetsRuleTest` ; checklist de recette `docs/qa/checklists/insets-android-recette-pm.md`.
- Vérifié : `targetSdk` 36 (edge-to-edge imposé sur Android 15, déjà le comportement de l'app depuis le début, donc aucun changement visible), icônes de la barre d'état sombres sur fond clair (`enableEdgeToEdge` + thème), splash volontairement plein écran. Aucun choix économique, aucune dépendance ajoutée.

## [0.6.1] - 2026-10-30 : ONB-3b, durcissement du retrait de consentement hors connexion

Décision du PM : « Oui pour durcir le consentement » (le risque noté en 0.6.0 est supprimé).

### Ajouté
- **Retrait en attente mémorisé sur l'appareil** (`PendingWithdrawals`) : « retrait en attente » + heure + clé = empreinte à sens unique de l'identifiant du compte, dans un fichier privé écrit sur le disque avant de rendre la main et exclu des sauvegardes. Aucune donnée personnelle.
- **La collecte reste coupée au démarrage** tant que le retrait n'est pas confirmé côté serveur pour ce compte (`SessionManager` : le compte qui dit encore « accepté » ne l'emporte plus).
- **Enregistrement en arrière-plan** (`ConsentWithdrawals`) : attend le retour du réseau (rappel système, sans interrogation répétée), nouvelles tentatives espacées (5 s, 15 s, 1 min, 5 min), transitions des règles déployées respectées. **Pas de boucle** après un refus du serveur (collecte coupée, « Enregistrer mon choix » proposé, un dernier essai au démarrage suivant).
- Cas gérés : retrait puis ré-acceptation avant enregistrement (verrou commun, marque annulée seulement après la prise en compte de l'acceptation) ; déconnexion ou changement de compte (la marque suit son compte, jamais écrite pour un autre) ; plusieurs comptes sur le même téléphone (20 au plus).
- `NetworkMonitor.onlineChanges()`.
- Texte de l'écran Confidentialité mis à jour (FR/EN, catalogue iOS) : « C'est coupé sur ce téléphone, même après un redémarrage. Ton choix sera enregistré dès que la connexion revient… ».
- Tests : stockage (rien d'identifiable, survit à un redémarrage, limite de 20), démarrage avec marque (collecte coupée, autre compte non touché, aucun événement), enregistreur (réseau de retour, serveur injoignable, refus sans boucle, déconnexion / autre compte, ré-acceptation), view model hors connexion (retrait, enregistrement automatique, bouton, ré-acceptation).

### Limites
- Une désinstallation efface la marque (sans conséquence : la collecte repart éteinte à la réinstallation, le compte sera relu).
- Un téléphone qui ne retrouve jamais le réseau garde la collecte coupée mais le compte reste « accepté » côté serveur.
- Aucune règle Firestore modifiée.

## [0.6.0] - 2026-10-30 : ONB-3b, Mon compte (mot de passe) et Confidentialité

Dépend d'ONB-3a (PR « ONB-3a » : barre d'onglets, Profil, Réglages, Mon compte minimal). Ticket `docs/tickets/feed-profil-reglages.md` (§6.6, critères B1 à B7 ; décisions Q5 et Q6 du PM du 2026-10-30).

### Ajouté
- **Mon compte : « Changer mon mot de passe »** (décision Q5) : un bouton qui envoie l'e-mail de réinitialisation d'AUTH-2 à l'adresse du compte, sans formulaire à trois champs ni ré-authentification ; confirmation unique, « Renvoyer l'e-mail » en pause de 60 s ; **masqué pour un compte Google** (« Connecté avec Google »). « Supprimer mon compte » reste absent (AUTH-5).
- **Réglages > Confidentialité** : interrupteur unique « Statistiques d'usage et rapports de plantage anonymes » lu et écrit dans `users/{uid}.analyticsConsent` selon les transitions des règles déployées (activer / retirer, trace du consentement conservée, version jamais plus ancienne), en transaction ; **accepter = enregistré d'abord**, **retirer = collecte coupée tout de suite** (rapports non envoyés supprimés) puis enregistré, avec message et « Enregistrer mon choix » si le réseau manque ; localisation et notifications dites honnêtement (rien n'est utilisé ni envoyé pour l'instant) avec « Ouvrir les réglages du téléphone » ; liens « Conditions d'utilisation » et « Politique de confidentialité » (adresses centralisées, vérifiées contre `web/src/pages.json`) ; « Nous contacter » (contact provisoire).
- Ligne « Confidentialité » dans la liste des Réglages (quatre lignes : Mes informations, Mon compte, Langue, Confidentialité).
- Textes `settings_account_password_*`, `settings_privacy_*` FR/EN (tutoiement), ajoutés aussi au catalogue iOS (ajouts seulement).
- Tests : transitions du consentement (toutes les branches), dépôt Firestore (une transaction, erreurs triées, texte plus récent refusé), view model de l'interrupteur (accepter / retirer / réseau / refus / double appui / aucun événement), mot de passe (compte e-mail, compte Google, pause, réseau, quota), écrans Robolectric (structure, 48 dp, grande police, absence de formulaire et de suppression), adresses légales comparées à `web/src/pages.json`.

### Modifié
- `AuthGateway.accountSignIn()` (mot de passe ou Google) ; `AccountViewModel` reçoit l'envoi de l'e-mail, le réseau et la pause.

### Écarts avec le ticket et les maquettes
- Les maquettes n'ont **pas** d'écran Confidentialité ni de changement de mot de passe par e-mail : écrans ajoutés d'après le ticket (liberté du CLAUDE.md), liste dans la PR.
- **Pas de lien « Mentions légales »** : aucune page de ce nom n'existe dans `web/src/pages.json` ; seuls les liens vers les conditions et la politique sont proposés.
- Les **états réels** de la localisation et des notifications (« toujours », « approximative », « refusée »...) ne sont pas affichés : aucune permission n'est déclarée (aucun usage).

### Choix économiques et limites
- Aucun service, aucune dépendance, aucune permission ; 0 €. Une lecture et une écriture Firestore par changement de l'interrupteur.
- ~~Limite : retrait non enregistré puis app fermée~~ : **corrigée en 0.6.1** (retrait mémorisé sur l'appareil).
- Aucune règle Firestore modifiée.

## [0.5.0] - 2026-10-30 : ONB-3a, barre d'onglets, Profil, Réglages (Mes informations, Mon compte, Langue)

Ticket `docs/tickets/feed-profil-reglages.md` (décisions du PM du 2026-10-30 : « OK pour tout » aux 15 questions), maquettes `design/exports/profil/`, `reglages/` et barres d'onglets de `feed/`.

### Ajouté
- **Barre d'onglets** flottante (Feed, bouton rond central, Profil) visible seulement quand une Miff existe ; noms parlés pour TalkBack, zones de 48 dp, ordre inversé en droite-à-gauche, masquée avec le clavier ouvert. **Bouton central** (décision Q1) : visible mais désactivé dans le build debug, **absent** du build release (deux onglets). Le build debug ajoute sur l'écran « prochaine étape » un bouton d'aperçu interne des onglets (absent du release).
- **Feed provisoire** honnête : « Ton fil n'est pas encore prêt », sans cloche, sans avatar, sans phrase de sécurité.
- **Onglet Profil** : photo ou initiales, « Prénom Nom », « C'est toi », engrenage ; toucher la photo ou le nom ouvre Mes informations. Aucune donnée fictive.
- **Réglages** : Mes informations, Mon compte, Langue.
- **Mes informations** : photo (sélecteur et appareil photo du système, recadrage, 20 000 octets sans métadonnée), prénom, nom (1 à 50, compteur réel), pays + téléphone (E.164, libphonenumber), mention de visibilité du numéro. Écriture de `users/{uid}` **et** de la copie de chaque Miff en une transaction confirmée (rien en file d'attente hors connexion) ; repli si une Miff n'existe plus ; réparation des copies sans numéro. Confirmation avant d'abandonner des changements.
- **Langue** : Français, English, « Langue du téléphone », effet immédiat sans bouton (`LocaleManager` sur Android 13+, contexte localisé sur Android 8 à 12), sans AppCompat.
- **Mon compte (provisoire)** : e-mail en lecture seule, « Se déconnecter » avec confirmation (la déconnexion de l'écran provisoire disparaît). Complété par ONB-3b (changement de mot de passe, Confidentialité).
- Textes `tabbar_`, `feed_`, `settings_`, `profile_tab_` FR/EN (tutoiement), ajoutés aussi au catalogue iOS (ajouts seulement) pour la parité ; préfixes ajoutés au contrat `docs/contracts/i18n-keys.md` et au script de parité.
- Tests : modèle de la barre, langues, initiales, dépôt du profil (transaction, repli, plus de 15 Miffs, identifiants non fiables, réseau absent), `CurrentProfile`, view models (Mes informations, onglet Profil, Langue, Compte), écrans Robolectric (structure, absence de Notifications / Premium / suppression, 48 dp, grande police, droite-à-gauche, TalkBack).

### Modifié
- `data-model.md` : les règles ONB-B (téléphone) sont **déployées** depuis le 2026-10-30 (la mention « non déployées » était périmée).
- Le champ de saisie commun accepte un rappel « le champ a perdu le focus » (numéro signalé en quittant le champ).
- Nouveau bouton secondaire `MiffSecondaryButton` ; dégradé `GradientProfileHeader` (approximation de la maquette, hors design system).

### Écarts avec les maquettes (détail dans la PR)
Tutoiement, « Téléphone » (et non « Nom »), compteurs réels, pas de « 3 caractères minimum », mention N1, « Enregistrer » grisé sans changement, deux langues sans bouton « Enregistrer », pas de « Notifications » ni d'encart Premium ni « Abonnement », e-mail en lecture seule, « Déconnexion » en bouton secondaire, pas de mot de passe à trois champs ni de « Supprimer mon compte », pas de carte de Miff ni de lieux.

### Choix économiques et limites
- Aucun service, aucune dépendance, aucune permission ajoutée ; coût 0 €. Lectures : une pour le profil (mise en mémoire pour la session), une lecture serveur des appartenances à chaque enregistrement.
- **Limite** : le « reste à écrire » des copies de Miff (réseau coupé entre deux écritures, ou Premium au-delà de 15 Miffs) n'est pas conservé si l'app est fermée ; signal : des proches qui voient un ancien nom.
- Aucune règle Firestore modifiée.

## [0.4.0] - 2026-10-30 : ONB-2, profil et téléphone (étapes de création du profil)

### Ajouté
- **Étape « Profil »** (maquette « Welcoming screen -1 ») : photo facultative, prénom, nom (1 à 50 caractères après suppression des espaces, compteur « 45/50 » près de la limite, « 50 caractères maximum. » au-delà), flèche ronde grisée tant que les deux noms ne sont pas remplis. Aucune flèche retour, aucune déconnexion (décision Q14) : le retour du système met l'app en arrière-plan. Nom et prénom proposés à partir du nom du compte Google (modifiables, jamais de photo importée).
- **Photo** : sélecteur de photos **du système** (aucune permission), ou appareil photo **du système** (aucune permission `CAMERA` : un seul fichier du cache privé via un `FileProvider` non exporté, supprimé dès qu'il est lu) ; **recadrage carré obligatoire** (glisser, pincer, curseur de zoom, actions « déplacer la photo » pour TalkBack) ; ré-encodage depuis les pixels en JPEG 160 x 160 de **20 000 octets au plus**, **sans aucune métadonnée** (vérifié sur un fichier source contenant une position, une date et un nom d'appareil) ; jamais copiée dans la galerie ; texte « Visible par les membres de tes Miffs ».
- **Étape « Téléphone »** (maquettes « -2 » et « -6 ») : pays (feuille Material avec recherche, ~240 pays, région de l'appareil par défaut, sinon France), numéro national avec préfixe visuel et exemple du pays, clavier numérique, remplissage automatique autorisé, un numéro « +… » choisit son pays ; validation de forme libphonenumber, **E.164 sans espace** ; aucun SMS, aucune unicité ; message calme « Ce numéro ne semble pas correct. » ; **mention de visibilité** obligatoire sous le champ (décision N1). Flèche et geste « retour » vers le profil (animation de retour prédictif).
- **Une seule écriture** de `users/{uid}`, à la fin de l'étape téléphone, avec toutes les clés : `firstName`, `lastName`, `phone`, `avatar?`, `createdAt` / `termsAcceptedAt` en heure serveur, `termsVersion`, `analyticsConsent {enabled:true, consentedAt, consentVersion, revokedAt:null}` (consentement réputé donné au clic sur « Rejoindre »). L'écran suivant n'apparaît qu'après la confirmation du serveur ; hors connexion : message neutre, champs conservés.
- **Brouillon local** (prénom, nom, photo, pays, numéro, étape) : stockage privé, jamais le mot de passe, supprimé après la confirmation du serveur et à toute fin de session ; un brouillon d'un autre compte n'est jamais repris. Fermer l'app en plein onboarding ramène à la bonne étape.
- **Routage** : profil absent -> panneau de consentement puis profil et téléphone ; profil **sans numéro** (compte antérieur à ONB-B) -> étape téléphone seule (ordre 2b) ; un profil antérieur à AUTH-B (sans `termsVersion`, mise à jour refusée par les règles) est supprimé puis recréé en repassant par le panneau de consentement. Remplace l'écran provisoire du profil.
- Textes `profile_` FR/EN (tutoiement), ajoutés aussi au catalogue iOS pour la parité des clés ; préfixe `profile_` ajouté au contrat `docs/contracts/i18n-keys.md` et au script de parité.
- Accessibilité : titres, champs étiquetés (libellé et champ ne font qu'un pour TalkBack), erreurs en zones live, cibles de 48 dp minimum, police agrandie et droite-à-gauche testés.
- Tests : validation E.164 (table pays et numéros + exemple de chaque pays de la bibliothèque), noms, géométrie du recadrage, recherche de la taille de l'avatar, codec (métadonnées, 20 000 octets sur 20 images de formes variées, orientation, PNG transparent, fichiers refusés), brouillon, view model (écriture unique et clés, confirmation, hors connexion, compte sans numéro, reprise), session, écrans (Robolectric) et captures.

### Modifié : revue indépendante de la PR (2026-10-30)
- **Numéros plus stricts que le ticket** : surtaxés, gratuits, coût partagé, pager, messagerie vocale, numéro personnel et UAN refusés (seuls ligne fixe, mobile et VoIP passent), avec un message bienveillant propre ; le PM peut assouplir (`PhoneNumbers.ALLOWED_TYPES`).
- **Compte antérieur à AUTH-B** : jamais supprimé s'il appartient à une Miff (message neutre) ; sinon prénom, nom et photo lus avant la suppression et pré-remplis. Le refus du panneau de consentement ne supprime plus un compte qui a une Miff.
- **Recadrage** : le contenu sous l'écran de recadrage est caché au lecteur d'écran et ne reçoit plus les touches. Retour désactivé pendant l'écriture.
- **Après 3 refus du serveur de suite** : message « Ça ne passe toujours pas… » (tout est gardé). Brouillon aussi effacé au lancement sans compte ou quand le profil existe.
- Dépôt de la lecture des indicateurs du compte (`hasPhone`, `hasTermsVersion`) dans une fonction pure testée ; accès Firestore de l'Onboarding derrière une interface testée avec un faux (transactions seulement, jamais de `set` simple : test sur le code source).
- **Limite documentée** : une transaction qui dépasse le délai de 20 s peut aboutir plus tard ; l'app relit alors le compte au refus suivant et continue si le profil est déjà complet.
- **README réparé** : un remplacement de texte avait dupliqué le début du fichier.

### Corrigé
- **I1 de la revue d'AUTH-1** : `ProfileDocument.forCreation` n'écrivait pas `phone` alors que les règles (ONB-B) l'exigent ; il prend maintenant le numéro E.164 (refusé avant toute écriture s'il n'a pas la forme des règles). `ProfileDocumentTest`, qui affirmait le contraire, est corrigé ; README mis à jour.

### Dépendances
- `com.googlecode.libphonenumber:libphonenumber` 9.0.42 (Google, Apache 2.0, gratuite, ~360 Ko) : plans de numérotation officiels de tous les pays ; une table maison de longueurs de numéros serait fausse pour certains pays. Signal de remplacement : bibliothèque non maintenue (dernière version Maven Central du 2026-10-23).
- `androidx.exifinterface:exifinterface` 1.4.2 (AndroidX, Apache 2.0) : lit seulement l'orientation de la photo choisie, avant ré-encodage sans métadonnée.
- Aucun coût ; aucun SDK d'analyse ; aucune permission ajoutée au manifeste (un `FileProvider` non exporté est déclaré pour l'appareil photo du système).

### Choix et limites à connaître
- **Transaction Firestore** (et non un `set` simple) pour que « pas de confirmation » signifie « rien d'écrit » : un `set` hors connexion resterait en file d'attente et repartirait seul plus tard.
- **Règles du téléphone non déployées** : tant qu'elles ne le sont pas sur `miff-3d283`, **toute** création de profil par cette app est refusée par le serveur (message neutre « Ça n'a pas fonctionné cette fois »). À déployer avant la recette (`docs/dev/deploiement-regles.md`, avec l'accord du PM).
- Compte antérieur à ONB-B qui appartient déjà à des Miffs : seul `users/{uid}` reçoit le numéro ; la copie dans les fiches de membre viendra avec « Réglages > Mes informations » (ONB-3).
- Libellés des champs au-dessus du cadre (maquettes) et non flottants comme à l'inscription ; liste complète des écarts design / plateforme dans le README (« Profil et téléphone »).
- Non vérifié sur téléphone dans ce ticket : Photo Picker réel, appareil photo réel, remplissage automatique, retour prédictif, clavier ; voir le parcours de recette de la PR.
## [0.3.0] - 2026-10-29 : AUTH-2, mot de passe oublié (v1)

### Ajouté
- Lien **« Mot de passe oublié ? »** sous le champ du mot de passe de la connexion (maquette -8) et écran **« Mot de passe oublié »** (maquette -10) : adresse e-mail (préremplie si elle était déjà saisie), « Envoyer », puis une confirmation sur le même écran. L'e-mail est envoyé par Firebase Authentication (`sendPasswordResetEmail`, dans la langue du téléphone) ; le **nouveau mot de passe se choisit sur la page web d'AUTH-W** (pas d'écran « Nouveau mot de passe » dans l'app en v1, il reste dans AUTH-6).
- **Même confirmation que l'adresse ait un compte ou non** (aucune énumération) : une adresse inconnue ou un compte désactivé (erreur de Firebase quand sa protection est coupée) est traité comme un envoi réussi, et une réponse acceptée n'est jamais montrée avant 0,8 s.
- **Pause de renvoi de 60 s** par adresse (le bouton devient « Renvoyer l'e-mail » et se réactive seul), conservée tant que l'app tourne ; confort, pas une protection de sécurité.
- Format d'adresse vérifié sur le téléphone (« Cette adresse e-mail ne semble pas correcte. »), hors connexion = message neutre sans vider le champ, trop de demandes = message dédié, aucun code technique, aucun événement Analytics.
- Textes FR (tutoiement) + EN `auth_signin_forgot_password`, `auth_reset_*` et `auth_error_too_many_requests`, ajoutés aussi au catalogue iOS (ajouts seulement, pour la parité des clés).
- Tests : tri des erreurs de Firebase et pause (`PasswordResetTest`), écran dans le temps virtuel (`PasswordResetViewModelTest` : format, confirmation identique et durée identique pour une adresse connue ou inconnue, pause de 60 s, hors connexion, double appui), écrans et accessibilité (`AuthScreensTest` : lien, champ, confirmation annoncée, 48 dp, très grande police, français).

### Modifié
- Messages de connexion « identifiants incorrects » et « trop d'essais » : textes finaux du ticket §7.3 qui renvoient à « Mot de passe oublié ? » (FR + EN). **Valeurs du catalogue iOS non modifiées** (consigne : ajouts seulement) : `auth_error_invalid_credentials` et `auth_error_too_many` diffèrent donc entre Android et iOS jusqu'à AUTH-4.
- Inscription et Google, en cas de « trop de demandes » : variante `auth_error_too_many_requests`, sans conseil de réinitialiser un mot de passe.
- Champ de saisie : variante encadrée (maquettes -10 et -11), touche d'action « Envoyer » ; lien souligné d'une hauteur de 48 dp au moins (aussi pour « Déjà un compte ? » et « Pas encore de compte ? »).

### À faire par le PM (aucune action dans l'app)
- Une fois la page web AUTH-W déployée : Firebase > Authentication > Modèles > Réinitialisation du mot de passe > « Personnaliser l'URL d'action » = `https://miff-3d283.web.app/reset-password`, texte du modèle en FR et EN. Tant que ce n'est pas fait, le lien de l'e-mail ouvre la page par défaut de Firebase (voir `android/README.md`).

### Choix économiques
- Aucun : Firebase Authentication au plan gratuit Spark, aucune dépendance ajoutée, aucun service d'e-mail tiers. Limite connue : le quota quotidien d'e-mails de réinitialisation de Spark n'est **pas lu** (ticket §14) ; signal : un envoi refusé par Firebase (« trop de demandes ») sans raison apparente chez plusieurs personnes le même jour.

## [0.2.2] - 2026-10-29 : correctifs de la 2e revue indépendante (AUTH-1 et splash)

### Corrigé
- **Les rapports de plantage n'arrivaient jamais** : à chaque démarrage à froid, la coupure de précaution effaçait le rapport du plantage précédent avant que la collecte soit rallumée. Désormais le démarrage met la collecte en **pause** sans rien supprimer (`PrivacySettings.pauseCollection`) ; le rapport part dès que le compte a répondu oui. Les rapports non envoyés ne sont **supprimés** que s'il n'y a pas de consentement : retrait, refus du panneau, déconnexion, ou aucune session au démarrage (un plantage survenu avant que la personne ait dit oui ne doit jamais partir plus tard). Si le compte ne peut pas être lu (hors-ligne), le rapport est gardé pour plus tard.
- **Retour système pendant le splash** : il ne déclenche plus le « retour = refus » du panneau de consentement composé dessous (le panneau se met en retrait tant que l'animation le couvre ; le retour ne fait rien pendant l’animation et ne quitte pas l'app).
- Consentement : le chemin « statistiques refusées à l'inscription » n'existe plus (le clic accepte tout, `ConsentChoice` n'a plus que `termsAccepted`) ; commentaires et noms de tests mis à jour (plus de « case (b) »).
- `LEGAL_BASE_URL` avec un « / » final (réglage `-Pmiff.legalBaseUrl=...`) ne produit plus de double barre : normalisé au build et à l'ouverture des liens (`legalPageUrl`).
- Imports inutilisés supprimés (`AuthComponents`, `WelcomeCarousel`, `SplashHost`).

### Corrigé : retours de recette du PM (2026-10-30)
- **Carrousel de bienvenue fidèle aux maquettes** (`design/exports/onboarding-original/`) : mêmes phrases (titre à dégradé « Savoir qu’ils vont bien, sans les déranger », identique sur les 3 slides ; les légendes inventées sont retirées), mêmes images (slides 1 et 2 : recadrages des maquettes avec leurs vraies photos ; slide 3 : trois cartes refaites en Compose avec les vraies photos de profil recadrées), mêmes formes vert pâle (vecteurs tracés sur les exports), mise en page proportionnelle aux maquettes, points de pagination, bouton « Commencer ». Poids ajouté : environ 70 Ko de PNG. **Limite** : les exports du PM font 320 x 640 px, donc les images des slides 1 et 2 (155 x 290 px) sont un peu floues sur un écran haute définition et leur texte reste en français quelle que soit la langue du téléphone ; le PM fournira de meilleurs exports. Les droits d’image sont réglés par le PM (mention « droits non vérifiés » retirée de la documentation).
- **Splash : le cœur vert était beaucoup trop gros** (5 fois sa taille). Il apparaît sur le « i » et gonfle maintenant de la taille du logo officiel jusqu’à **120 %** (jamais au-delà de 125 %, élastique léger conservé), puis éclate net avec ses éclats (rayon de départ et portée des éclats réduits en proportion). Durée retouchée ensuite (voir la série suivante : 2,9 s). Test : échelle maximale entre 1,15 et 1,25.

### Corrigé : 2e série de retours de recette du PM (2026-10-30)
- **Splash plus lent** (« à peine le temps de comprendre ce qu’il se passe ») : 0,3 s logo seul, 0,4 s apparition du cœur, 1,5 s de gonflement (courbe plus douce, accélération vers la fin, élastique léger ; le cœur tient 0,12 s à sa taille maximale juste avant l’éclat), 0,5 s d’éclatement (éclats plus lents), 0,2 s de fondu : **2,9 s au total, 3,0 s au plus** (horloge réelle). Le cœur reste à 100-120 % du logo officiel (max ≤ 125 %). Version « animations supprimées » inchangée (0,8 s). Tests : durée ≤ 3,0 s, phases dans l’ordre, éclatement ≥ 0,4 s, pause avant l’éclat ; 12 captures au lieu de 10.
- **Carrousel : plus aucune image floue.** Les recadrages raster à 155 x 290 px sont abandonnés : les trois illustrations sont **reconstruites en vectoriel et en vrais textes** à partir des maquettes (formes, icônes, ombres, cartes, polices Baloo Thambi 2 / Hanken Grotesk, couleurs des tokens), nettes à toute densité (captures mdpi / xxxhdpi / très grande police). Textes FR/EN (clés `android_onboarding_*`, valeurs des clés partagées alignées sur les maquettes). Seules les photos restent des bitmaps : **photos libres de droits Pexels** validées par le PM (portraits de Thomas, Amandine et « Maman », salon de la slide 1 ; crédits dans `design/exports/onboarding-photos/CREDITS.md`, rien d’affiché dans l’app), JPEG optimisés (environ 220 Ko en tout) avec masque rond vectoriel, remplaçables sans changement de code. Écart connu : le salon n’a pas le décor de branches de la maquette (télévision sur un meuble blanc). Le fond de carte de la slide 2 reste le recadrage de la maquette (un cercle et un point nets sont dessinés par-dessus).

### Tests ajoutés ou durcis
- Rapports de plantage conservés au démarrage à froid avec consentement (jusqu'à la réponse du compte) et supprimés au retrait, au refus, à la déconnexion et sans session ; `FirebaseCrashlyticsSwitch` testé avec un faux client.
- Règle « carrousel seulement à la première ouverture sans session » extraite en fonction pure (`shouldShowWelcomeCarousel`, critère CA5) et testée.
- Splash : touches absorbées, retour système sans effet sur le panneau, non rejoué à la rotation (après la fin, et rotation en cours de route). Panneau : retour = refus (et absorbé pendant le chargement).
- Liens légaux : cibles en `https`, `/fr/cgu`, `/fr/confidentialite`, `/en/terms`, `/en/privacy`, ouverture dans le navigateur ; `SharedPreferencesPendingConsentStore` filtré par compte et vidé par `clear()` ; consentement en attente effacé à la déconnexion ; plancher de 800 ms de la réponse de connexion testé avec la valeur en dur (le test comparait la constante à elle-même).

### Documentation
- README : phrase « Consentement et collecte » rétablie ; CHANGELOG 0.2.0 : lignes périmées (cases, « seulement avec (b) ») retirées ; `backend/data-model.md` §8 et `docs/tickets/authentification.md` (§7.8, §8, S11/C8) alignés sur la décision du 2026-10-28 ; textes légaux (`docs/legal`) alignés sur l'app réelle.
- Rien n'est modifié côté règles Firestore ni iOS ; I1 (`ProfileDocument.forCreation` sans téléphone) est traité par ONB-2.

## [0.2.1] - 2026-10-28 : splash animé (demande du PM après recette sur l'OPPO)

### Ajouté
- **Écran de lancement animé** : fond bleu uni, logo « M + i » sans cœur, puis le cœur jaune-vert apparaît sur le « i », gonfle comme un ballon (léger élastique), éclate (pop net, 12 petits éclats verts) et l'app s'ouvre en fondu. Environ 2,2 s (2,5 s au plus), une fois par démarrage à froid.
- **Écran de lancement système** (API Splash Screen d'Android 12+, rétro-portage jusqu'à minSdk 26) : mêmes fond et « M + i » sans cœur, dessiné à la même taille et au même endroit que l'animation, pour un relais sans saut. Dépendance ajoutée : `androidx.core:core-splashscreen` 1.2.0 (AndroidX, gratuite, quelques dizaines de Ko).
- **« Réduire les animations »** (échelle d'animation à 0) : aucun mouvement, logo complet fixe 0,6 s puis fondu (0,8 s au total).
- Accessibilité : décoratif, annoncé « Miff » une seule fois ; app masquée du lecteur d'écran et touches absorbées pendant l'animation ; aucun son, aucune vibration, aucun événement d'analyse, aucun clignotement.
- Tests : machine d'états de l'animation sans interface (`SplashTimelineTest`), détection du réglage (`ReduceMotionTest`), cohérence logo/tokens/thème (`SplashArtworkTest`), enchaînement vers l'app avec et sans mouvement et capture de dix instants (`SplashHostTest`).

### Choix techniques (visibles ou non)
- Horloge en **temps réel** (pas `Animatable`) : l'échelle d'animation des options développeur ne peut pas rallonger le splash.
- L'app est composée sous l'animation : la session se résout pendant ce temps, le splash n'attend jamais le réseau. Si le premier écran n'est pas encore prêt à la fin, il affiche son propre chargement (pas de maintien du fond bleu : écart avec le ticket Onboarding §5.1 point 3, voulu pour respecter « jamais plus longtemps que prévu »).
- Aucun contournement « pour rester gratuit » ; aucune bibliothèque d'animation, aucune ressource externe.

## [0.2.0] - 2026-10-27 : AUTH-1, inscription et connexion

### Ajouté
- Écrans d'**inscription** et de **connexion** : e-mail + mot de passe et « Continuer avec Google » (Credential Manager). Textes `auth_` en français (tutoiement) et anglais, ajoutés aussi au catalogue iOS pour la parité des clés.
- **Consentement par le clic** (aucune case, voir « retours de recette » plus bas) : une ligne au-dessus du bouton principal, avec liens vers les pages web, dit que le clic accepte les conditions, la politique et le partage de statistiques et rapports de plantage anonymes.
- **Panneau de consentement** du nouvel utilisateur Google arrivé par la connexion ; refus = suppression du compte tout juste créé, purge du cache, aucun événement, aucun document.
- **Routage au lancement** selon l'état du compte, session persistante, écran « réessayer » si le compte ne peut pas être lu.
- **Déconnexion** : arrêt des écoutes, envoi des écritures en attente, purge du cache Firestore (terminate + clearPersistence, drapeau repris au démarrage), arrêt de la collecte. Perte de session détectée : même nettoyage, message calme.
- **Collecte Analytics / Crashlytics** : coupée tant que `users/{uid}` n'est pas lu, puis selon le choix du compte ; `sign_up_completed` envoyé après la création du compte (le consentement étant donné par le clic) ; `sign_in_completed` seulement après lecture d'un consentement accepté.
- `ProfileDocument` / `UserProfileRepository` : la seule écriture de création de `users/{uid}` (dates serveur, `analyticsConsent` toujours écrit), pour le ticket Onboarding.
- CI : signature de debug stable (secret `DEBUG_KEYSTORE_B64`), contrôle de la SHA-1 de l'APK, échec explicite sans client OAuth web.
- Accessibilité : titres, champs étiquetés, erreurs annoncées (zones live), boutons d'au moins 48 dp, liens légaux focalisables (aussi proposés comme actions au lecteur d'écran), contrastes AA (`#D60700` pour les erreurs).

### Modifié : retours de recette du PM (2026-10-28)
- **Plus aucune case à cocher** (décision PM, risque légal et stores assumé par lui) : une ligne explicative juste au-dessus du bouton principal, avec liens vers les Conditions d'utilisation et la Politique de confidentialité ; le clic accepte (a) et (b) (`analyticsConsent.enabled = true` à la création du profil). Panneau Google : même ligne, « Continuer » / « Annuler » (refus = suppression du compte, inchangé). Bouton principal « Rejoindre ». Textes `auth_` FR/EN et catalogue iOS mis à jour (clés des cases supprimées).
- **Carrousel de bienvenue** à la première ouverture seulement (3 slides, points, « Commencer », « J'ai déjà un compte », manuel, sans événement, « Page 1 sur 3 »), illustrations provisoires à l’époque, remplacées par les images des maquettes en 0.2.2. Préfixe `onboarding_` ajouté au contrat de clés et au script de parité.
- **Point d'extension du splash** (`StartupSlot`) en tête d'`AppRoot`.
- Documentation : `docs/tickets/authentification.md` §8 et §17 (Q8 remplacée), `backend/data-model.md` §8 (aucune règle modifiée).

### Modifié : fidélité aux maquettes
- Écrans d'inscription et de connexion alignés sur les maquettes du PM (en-tête avec flèche et mot-symbole officiel, bouton Google, « Ou : », champs soulignés, bouton principal, lien souligné, œil du mot de passe). Écarts conscients listés dans le README (tutoiement, une ligne de consentement avant le bouton principal, pas d'Apple sur Android, message unique, pas de « Mot de passe oublié ? » avant AUTH-2, règles de marque Google, accessibilité).
- Libellé du bouton principal en `body/large` (design system). Clé `auth_signin_no_account_action` supprimée (le lien entier est « Pas encore de compte ? »). « Ou : » remplace « ou ».

### Corrigé après la revue indépendante
- **Google en release** : la ressource `default_web_client_id` (lue par son nom) était supprimée par le réducteur de ressources du build release ; `res/raw/keep.xml` la conserve.
- **Collecte au démarrage** : Analytics et Crashlytics restent coupés tant que `users/{uid}` n'a pas été lu (le choix stocké sur l'appareil n'est plus réappliqué au lancement) ; tests dont un qui tue la suppression de la coupure à la connexion.
- Panneau de consentement d'un compte **e-mail** : plus de demande de reconnexion Google impossible ; message neutre, la personne peut accepter. Perte de session pendant un chargement : nettoyée aussi. Double appui sur « Continuer » : un seul enregistrement, un seul événement. Drapeau « purge à faire » écrit dès le début de la déconnexion, du refus et de la perte de session. Fenêtre Google limitée à 3 minutes. Les échecs de connexion de plus de 10 minutes sont oubliés.
- `USE_BIOMETRIC` et `USE_FINGERPRINT` (ajoutées par androidx.credentials) retirées du manifeste ; commentaire et README mis à jour.
- Bouton Google : règles de marque (Roboto Medium 14 sp, `#1F1F1F`, bordure `#747775`, sans ombre). Le « G » reste un redessin à remplacer avant Google Play.
- Textes « identifiants incorrects » et « trop d'essais » : plus de renvoi vers un lien « Mot de passe oublié ? » inexistant ; **le texte final avec ce lien reviendra avec AUTH-2**.
- `LEGAL_BASE_URL` doit commencer par `https://` (le build échoue sinon). `backend/data-model.md` §8 : `sign_up_completed` « après la création du compte » (documentation seulement).

### Dépendances
- `androidx.credentials:credentials` et `credentials-play-services-auth` 1.6.0 (Apache 2.0), `googleid` 1.2.1 : nécessaires à la connexion Google (alternative gratuite, maintenue par Google, à l'ancien SDK Google Sign-In). Aucun coût.

### Choix et limites à connaître
- Aucun délai propre sur les appels d'authentification : une requête abandonnée par un délai pourrait aboutir plus tard et ouvrir une session que personne ne regarde ; Firebase échoue vite hors connexion. Signal de réexamen : plaintes de chargement sans fin.
- Le mot de passe est vidé par une rotation de l'écran (seul l'e-mail est conservé), écart assumé avec le cas limite 14 du ticket.
- Le lien « Mot de passe oublié ? » arrive avec AUTH-2, avec le texte final des messages « identifiants incorrects » et « trop d'essais ».
- Le « G » du bouton Google est un redessin à remplacer par l'asset officiel de Google avant publication.
- Non vérifié sur téléphone dans ce ticket : voir la description de la PR.

## [Non publié] : logo et icône officiels

### Modifié
- **Icône adaptative officielle** (M et i soudés blancs + cœur jaune-vert sur fond `#043CD1`) à la place de l'icône provisoire, avec calque monochrome (thème d'icônes Android 13+). Avant-plan dans la zone sûre de 66 dp. L'ancienne icône et le commentaire « provisoire, ne pas publier » sont supprimés.
- **Ecran d'accueil** : le mot-symbole « Miff » (VectorDrawable) remplace le logo provisoire ET le titre texte « Miff » (qui aurait doublonné). Il est le titre de l'écran pour les lecteurs d'écran, avec la description « Logo de Miff » / « Miff logo » (clé `home_logo_a11y`, sans « provisoire »). La clé `home_title` est conservée (non utilisée sur Android) pour la parité des clés avec iOS ; à retirer des deux plateformes ensemble. Aucun autre écran n'utilise le logo (pas d'écran d'authentification sur `main` à ce jour).
- `HomeScreenScrollTest` adapté : il retrouve le logo par sa description au lieu du titre texte.

### Ajouté
- `app/src/main/play/play-store-icon-512.png` + README : icône 512 x 512 de la fiche Google Play, hors dossier de ressources donc jamais dans l'APK (vérifié sur l'APK release).
- `LogoColorsTest` : les couleurs des dessins du logo doivent rester celles des tokens du design system.

### Note
- Pas de splash personnalisé (SplashScreen API non utilisée) : rien à changer. Si un splash est ajouté plus tard, utiliser l'avant-plan de l'icône sur fond `#043CD1`.

## [0.1.1] - 2026-10-23 : correctifs de la recette INIT-2

### Corrigé
- **AND-003** : l'écran d'accueil (seul écran du build release) défile maintenant verticalement quand son contenu dépasse l'écran (police au maximum, paysage, écran partagé). Apparence inchangée quand tout tient : le contenu reste centré. Test `HomeScreenScrollTest` (2 cas, sur JVM avec Robolectric, sans émulateur) : vérifié qu'il échoue sur l'ancien écran et réussit sur le nouveau.
- **AND-001** : documentation du script de parité des traductions (README et CHANGELOG) : il lit `res/values-fr/strings.xml` (le français), pas `values/`.

### Ajouté
- CI : `gradle/actions/wrapper-validation` (épinglé par empreinte de commit) contrôle le `gradle-wrapper.jar` avant tout lancement de Gradle.
- README : options (non implémentées) pour une clé de débogage stable en CI (AND-002), avec recommandation et étapes pour le PM.

### Dépendances de test (aucune dans l'appli livrée)
- Robolectric 4.17 (Apache 2.0) et `ui-test-junit4` de Compose (Apache 2.0) en `testImplementation` : indispensables pour tester un écran sans téléphone ni émulateur. `ui-test-manifest` (Apache 2.0, debug uniquement) ajoute une activité vide au **manifeste debug** seulement ; le build release n'est pas concerné. Limite : Robolectric simule Android 15 (API 35) car l'API 36 exige Java 21 alors que le projet compile en Java 17. Signal : passage du projet à Java 21, alors remonter l'API simulée.

## [0.1.0] - 2026-10-21 : INIT-2, socle Android complet

### Ajouté
- Projet Gradle (Kotlin DSL, catalogue `libs.versions.toml`), module unique `app`, `com.tomessa.miff`, `minSdk` 26, `targetSdk` 36, `compileSdk` 37. Wrapper Gradle 9.6.1 avec somme SHA-256 vérifiée.
- Architecture MVVM (`ViewModel` + `StateFlow`), Navigation Compose à routes typées, conteneur d'injection manuel.
- Design system en code : couleurs, styles de texte (`sp`), arrondis, ombres, dégradés, espacements provisoires, thème clair imposé. Polices Baloo Thambi 2 et Hanken Grotesk embarquées (licences OFL dans `third_party/fonts`).
- Traductions : anglais (langue de repli, `res/values`) et français (`res/values-fr`), `LocaleConfig` générée, pseudo-langues `en-XA` et `ar-XB` en debug, Lint `HardcodedText`, `MissingTranslation`, `ExtraTranslation`, `RtlHardcoded` en erreur.
- Firebase (BoM 35.1.0) : Firestore, Authentication, App Check (debug en debug, Play Integrity en release, mode non appliqué), Crashlytics, Analytics. Collecte Analytics et Crashlytics **désactivée au démarrage** (manifeste) et activable uniquement à l'exécution par l'utilisateur ; choix conservé sur l'appareil.
- Liste blanche d'événements Analytics (refus de tout paramètre évoquant une position, de toute valeur ressemblant à des coordonnées) testée unitairement.
- Ecran de diagnostic interne (debug uniquement) : identité du build et projet Firebase, Test 1 (règles actives), Test 2 (compte de test, lecture seule), état App Check, interrupteurs de collecte, événement de test, tentative de position (doit être refusée), plantage de test, galerie de tokens, bloc langue. Messages neutres et bienveillants, comportement hors-ligne prévu.
- Ecran d'accueil minimal (release) avec logo provisoire.
- Icône adaptative **provisoire (ne pas publier)** avec couche monochrome Android 13+.
- Tests unitaires JUnit (46 tests), Android Lint, ktlint via Spotless.
- Sécurité : `allowBackup=false` et règles d'extraction vides, trafic en clair interdit, uniquement des permissions réseau/techniques sans demande à l'utilisateur, permissions publicitaires retirées, pas de `debuggable` en release, R8 + réduction des ressources en release (non signée).
- `README.md` (démarrage, génération de `google-services.json`, installation sur téléphone, points de vigilance ColorOS pour le futur géofencing).

### Modifié après revue (PM)
- `colorTextError` passe à `#D60700` (contraste ≥ 4,5:1) ; `colorStatusDanger` inchangé.
- Liste blanche Analytics alignée sur `docs/contracts/analytics-events.md` (version 1).
- Clé `home_logo_description` renommée `home_logo_a11y` (contrat des clés).
- Interrupteurs du diagnostic accessibles ; e-mail de test conservé à la rotation (jamais le mot de passe).
- Limite documentée : cache Firestore non purgé à la déconnexion.
- Workflow CI `android.yml` (durée des jobs, contrôle du bundle release, configuration Firebase factice pour Dependabot uniquement).
- Correction du téléversement du mapping Crashlytics (interrupteur inversé) ; suivi automatique des écrans coupé ; exception de la collecte automatique du SDK documentée dans le contrat Analytics ; événement de test retiré de la liste blanche en release ; accessibilité du diagnostic (titres, annonces des résultats) ; tests du ViewModel de diagnostic.

### Choix économiques et contournements
- **Firebase plan Spark** : rien à payer. Limite connue : 50 000 lectures Firestore par jour. Signal de bascule : l'onglet Firestore > Utilisation approche du quota, ou besoin de Cloud Functions / Cloud Storage (plan Blaze).
- **Envoi automatique du fichier de correspondance R8 vers Crashlytics désactivé** (un build local ne doit pas écrire dans Firebase). Limite : les plantages d'un build release sont difficiles à lire. Signal : premiers testeurs sur des builds release distribués par la CI ; activer alors `-Pmiff.uploadCrashlyticsMapping=true` en CI.
- **Polices variables** (toutes les graisses embarquées, environ 580 Ko) faute d'outil de découpage de polices. Limite : poids de l'APK. Signal : taille de l'APK release en hausse sensible (aujourd'hui environ 2,7 Mo).
- **Pas de Hilt/KSP** : conteneur d'injection manuel pour garder un build léger sur 8 Go de RAM. Signal : le graphe d'objets grossit au point que `AppContainer` devient pénible.

### Ecarts et points ouverts
- `compileSdk` 37 (les bibliothèques AndroidX actuelles l'exigent) ; `targetSdk` reste 36.
- Anglais dans `res/values`, français dans `res/values-fr` (nécessaire pour le repli anglais) : les deux dossiers ont les mêmes clés (Lint l'impose), et le script de parité des traductions lit le **français** dans `values-fr/strings.xml` (l'anglais de `values/` est ignoré par ce script).
- Poids des polices `display/*`, interprétation du "dégradé à 17 %", noms de code des styles de texte et des couleurs non listés dans `design-system.md` : décisions à confirmer côté design (voir README).
- Non vérifié sur appareil dans ce ticket (aucun téléphone ni émulateur disponible) : démarrage sur Android 8.0 et récent, rendu visuel (icône en rond/carré/squircle, ombres, polices, droite-à-gauche), tests 1 et 2 contre le vrai projet, preuves Analytics/Crashlytics en console, mode avion.
