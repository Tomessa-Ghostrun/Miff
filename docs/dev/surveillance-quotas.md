# Surveiller l'usage de la base (quotas) et reconnaître le moment de passer en Blaze

**Pour qui** : le PM, non-développeur. Rien à programmer ici : ce sont des relevés à faire dans la console Firebase, des seuils et des décisions à prendre.

**Contact et autorité de décision** : Tomessa MFOUKA, tomessa.mfouka@gmail.com (PM du projet Miff).

**Pourquoi c'est nécessaire** : tant que Miff est sur le plan gratuit (Spark), Firestore offre **50 000 lectures par jour** pour tout le projet. Si ce plafond est atteint, **l'application s'arrête pour tout le monde jusqu'à la remise à zéro quotidienne** : ce n'est pas une facture, c'est une panne. Ça peut arriver parce que le produit marche (beaucoup d'utilisateurs) ou parce que quelqu'un abuse (un script qui interroge la base en boucle). Cette page explique comment le voir venir.

**Ce que ça coûte de surveiller** : 0 € et environ 1 minute par jour pendant la bêta externe.

> Les règles de sécurité ne peuvent pas limiter le nombre d'essais d'un utilisateur (elles n'ont pas de compteur). La protection repose sur trois choses : App Check appliqué (voir `app-check-strict.md`), cette surveillance, et, si besoin, la bascule en Blaze décrite plus bas.

---

## 1. Les 4 chiffres à relever chaque jour de bêta externe

| # | Chiffre | Où le lire dans la console Firebase | Valeur normale (estimation, pas une mesure) |
|---|---|---|---|
| 1 | **Lectures du jour** | Projet **Miff** > Build > **Firestore Database** > onglet **Utilisation** > graphique « Lectures » | Quelques centaines à quelques milliers pendant la bêta |
| 2 | **Utilisateurs actifs du jour** | Projet **Miff** > **Analytics** > tableau de bord > « Utilisateurs actifs » sur 1 jour. Si Analytics n'affiche rien (le suivi n'est actif qu'avec le consentement des utilisateurs), compter à la main les testeurs qui ont ouvert l'app (tu les connais pendant une bêta fermée) | Le nombre de testeurs qui ont ouvert l'app |
| 3 | **Nouveaux comptes du jour** | Projet **Miff** > Build > **Authentication** > onglet **Utilisateurs** : trier par date de création et compter les comptes du jour | Proche du nombre de testeurs invités ce jour-là |
| 4 | **Part des requêtes « vérifiées »** | Projet **Miff** > Sécurité > **App Check** > onglet **API** > Cloud Firestore : le détail des requêtes (vérifiées, client obsolète, origine inconnue, invalides) | Proche de 100 % « vérifiées » une fois App Check en place |

Les graphiques de la console sont des **estimations** (délai possible d'environ 4 minutes, d'après la documentation Google). La « journée » des quotas se termine **vers minuit, heure du Pacifique** (soit vers 9 h du matin à Paris : calcul à partir de la documentation Google, à confirmer en observant le graphique). Le plus fiable est de relever le chiffre de la **veille complète**, dans la matinée.

### Le calcul : lectures par utilisateur actif

> **Ratio = lectures du jour ÷ utilisateurs actifs du jour**

Exemple : 6 000 lectures un jour où 50 testeurs ont ouvert l'app donnent un ratio de 120.

Pourquoi ce ratio : l'estimation de `backend/data-model.md` §12 est d'environ **75 lectures par utilisateur actif et par jour**. Un ratio qui s'écarte beaucoup de 75 signale soit un bug de l'application (une boucle de lecture), soit un abus. Il est indépendant du nombre de testeurs, contrairement au total brut.

Si les deux chiffres sont petits (moins de 10 utilisateurs actifs), le ratio varie beaucoup d'un jour à l'autre : regarde alors surtout le total des lectures et la tendance sur la semaine.

---

## 2. Les seuils et ce qu'il faut faire

| Niveau | Quand | Ce que ça veut dire | Que faire |
|---|---|---|---|
| **Normal** | Ratio ≤ 100, total < 30 000 lectures, nouveaux comptes habituels | Tout va bien | Rien, noter le relevé |
| **À examiner** | Ratio **> 100** un jour | Consommation plus forte que prévu : peut être un bug d'app, un testeur qui laisse l'app tourner, ou le début d'un abus | Voir « À examiner » ci-dessous |
| **SIGNAL de bascule** | Ratio **> 150 deux jours de suite**, ou total **> 30 000 lectures deux jours de suite** sans explication, ou comptes créés **de façon anormale**, ou quota de **50 000 lectures atteint** (même un seul jour) | Un abus ou une charge qui va finir par couper l'app pour tous | Voir « Signal de bascule » ci-dessous |

**Création de comptes anormale** : par exemple plus de **5 fois la moyenne des 7 jours précédents** sans campagne d'invitation de ta part (seuil de départ proposé dans le ticket de durcissement, à recalibrer avec le vrai volume de la bêta). Pour éviter les fausses alertes quand les chiffres sont petits, une règle simple : au moins **20 nouveaux comptes dans la journée** *et* plus de 5 fois la moyenne (cette condition supplémentaire est une proposition de l'agent `backend-data`, à ajuster). Une rafale de créations de comptes est le signe typique d'un script.

### À examiner (ratio > 100 un jour)

1. Regarde si un événement explique le chiffre : test intensif de ta part ou d'un testeur, nouvelle version de l'app, nouveau testeur qui a beaucoup navigué.
2. Regarde les **nouveaux comptes** (chiffre 3) : un pic est suspect.
3. Regarde App Check (chiffre 4) : une forte part de requêtes « origine inconnue » ou « invalides » est suspecte (quelqu'un interroge la base sans passer par l'application).
4. Note le relevé (voir le journal plus bas) et **surveille le lendemain**.
5. Si la cause est probablement un bug de l'application, signale-le à `ios-dev` / `android-dev` avec la date et les chiffres.

### Signal de bascule (ratio > 150 deux jours de suite, comptes anormaux, ou quota atteint)

Le plan gratuit risque de ne plus suffire, ou quelqu'un abuse. Dans cet ordre (du moins cher au plus cher) :

1. **Vérifier qu'App Check est bien « appliqué »** sur Cloud Firestore (et sur Authentication si la console le propose) : voir `app-check-strict.md`. Si ce n'est pas le cas, c'est la première chose à faire.
2. **Réexaminer l'exigence d'e-mail vérifié** (gratuit) : aujourd'hui les règles ne l'exigent pas (décision du PM du 2026-10-22). Si les comptes jetables sont le problème, demander à `backend-data` d'ajouter la condition `email_verified` (environ une ligne dans les règles, testée avant déploiement), puis déployer avec la procédure `deploiement-regles.md`.
3. **Décider du plan Blaze** (payant à l'usage, carte bancaire à saisir par toi). Blaze permet de remplacer la recherche directe d'un code par une fonction serveur « rejoindre » qui compte les essais par compte, et ouvre les notifications push. À savoir avant de décider :
   - Les quotas gratuits restent gratuits ; on ne paie que **au-delà**.
   - Une **alerte de budget prévient mais ne plafonne pas** la dépense : à créer (Google Cloud > Facturation > Budgets et alertes) dès l'activation.
   - L'ordre de grandeur à plusieurs milliers d'utilisateurs actifs est de quelques euros par mois (estimation grossière de `data-model.md` §12, à confirmer avec le calculateur de prix Google).
   - **La décision est la tienne** : elle est inscrite comme question ouverte à trancher **avant la première bêta externe** (ticket de durcissement, Q7). Aucun agent n'active Blaze ni n'ajoute de moyen de paiement.

### Quota atteint (l'app est refusée pour tout le monde)

1. Les requêtes reviennent à la normale après la remise à zéro (vers 9 h à Paris, à confirmer). Prévenir les testeurs par le canal habituel, sans entrer dans les détails techniques.
2. **Ne rien contourner** : pas de second projet, pas de comptes multiples, pas de règle assouplie « pour que ça passe » (un contournement des conditions d'utilisation de Firebase peut faire bannir le projet, ce qui est pire qu'un passage en Blaze).
3. Traiter ensuite comme un **signal de bascule** (liste ci-dessus).

---

## 3. Les autres quotas à garder en tête

| Quota (Spark) | Seuil d'alerte conseillé | Où |
|---|---|---|
| Écritures Firestore : 20 000 par jour | 12 000 par jour | Firestore > Utilisation |
| **Écritures de la détection (LOC-2b)** : statuts et dernières positions, par téléphone et par jour | normal : 6 à 10 statuts + 20 à 60 positions par Miff ; **borné** à 60 statuts + 150 positions par téléphone (disjoncteurs de l'app) ; relever **à part** la part des écritures de `positions` à la recette | Firestore > Utilisation, puis le journal de la recette (`docs/qa/checklists/loc-2b-android-recette-pm.md`, partie C) |
| **Écritures de la détection iOS (LOC-4b)** : mêmes bornes et mêmes valeurs qu'Android (`TrackingLimits`) | normal : 6 à 10 statuts + 20 à 60 positions par Miff ; **borné** à 60 statuts + 150 positions par téléphone ; **en plus**, sur iPhone, une relecture **forcée** des Miffs ET des lieux (sans attendre l'heure) à chaque lieu écrit par le créateur et à chaque changement de partage (`refresh(.sharingChanged)`, `ChangeNotifyingPlaceRepository`) : **10 à 30 lectures par enregistrement** selon le nombre de Miffs et de lieux (rare, mais à compter si le créateur modifie plusieurs lieux de suite) ; **relance par le système** : estimée à **4 à 6 lectures + 2 à 3 lectures de règles par écriture**, **à mesurer** à la recette partie C (relever Firestore > Utilisation avant et après la sortie, puis noter ici le chiffre réel) ; signal de bascule : lectures du jour > 40 000 de façon répétée avec peu de testeurs | Firestore > Utilisation, puis le journal de la recette (`docs/qa/checklists/loc-4b-ios-recette-pm.md`, partie C) |
| **Lectures par réveil de la détection (LOC-2b), estimation à mesurer à la recette** | une relecture (au plus 1 par heure de réveil, plus à chaque ouverture après 1 h, retour du réseau, changement de partage, redémarrage) = **M + N × (5 + P)** lectures, M = Miffs de la personne, N = Miffs où elle partage sous la v3, P = lieux d'une Miff (index : M documents ; par Miff : 1 fiche + 2 lectures de règles, puis P lieux + 2 lectures de règles) ; exemple 1 Miff, 3 lieux : ≈ 9. À cela s'ajoutent les lectures de règles des écritures : **1** par statut écrit, **2** par position écrite. Jusqu'à 12 relectures par jour : ≈ 110 lectures, plus 40 à 120 lectures de règles pour les positions : de l'ordre de **150 à 250 lectures par utilisateur actif et par jour**, soit bien plus que les ~75 estimés avant la détection. **À mesurer à la recette** ; signal : ratio lectures / utilisateurs actifs > 150 deux jours de suite (§2). | Firestore > Utilisation, journal de la recette |
| **Photos d'update (R-PHOTO-LOC, localisation obligatoire, décision du PM du 2026-11-06)** | chaque photo ouverte par un autre membre = **1 lecture du document + 3 lectures de règles = 4 lectures facturées** (3 avant la localisation de la photo), car toute photo est localisée et sa lecture vérifie la Miff, mon partage et **ma** position publiée. **Estimation, pas une mesure** : Miff de 4 membres, une photo ouverte par autre membre à chaque ouverture à froid (~3 par jour) = 36 lectures par jour et par utilisateur actif au lieu de 27 (+9) ; Miff de 8 membres : 84 au lieu de 63. Avec les ~176 à 320 lectures de la détection et de la session, on passe à environ **210 à 355** dans ce cas haut, soit **~140 à 240 utilisateurs actifs par jour** avant les 50 000 lectures de Spark (contre ~155 à 285 avant) ; l'ordre de grandeur est inchangé, la limite basse baisse jusqu'à ~10 à 15 % par rapport à un Feed sans photo (hypothèse pessimiste : chaque autre membre a toujours une photo et chaque ouverture à froid relit tout). **Limite connue** : le coût est proportionnel au nombre de photos OUVERTES, donc au comportement de l'application. **Signal** : celui du §2 (ratio > 150 deux jours de suite) ; si le ratio monte nettement dès que les photos sont en usage, **vérifier d'abord que l'application ne pré-charge pas les photos du Feed** (une lecture par ouverture, cache tant que la date de mise à jour ne change pas : levier gratuit), avant de parler de Blaze. | Firestore > Utilisation, journal de la recette |
| **Photos d'update dans le Feed iOS (FEED-8)** | **mêmes coûts** que la ligne ci-dessus (4 lectures facturées par photo d'un autre membre, 3 pour la sienne, **0 pour sa photo publiée** : posée en mémoire par le publieur), **estimés, non mesurés** : un seul `get` (`source: .server`) par photo et par statut, **à l'apparition de la carte** seulement, l'onglet en usage ; **mémoire de session** (16 photos), absences et refus mémorisés (un refus coûte 3 lectures de règles, une seule fois par statut) ; **aucune lecture hors connexion** ; aucun pré-chargement. **Scénario à mesurer (recette, partie F)** : **une arrivée ou un départ de zone d'un proche remet `lastUpdateAt` à jour, donc relance une lecture de sa photo (4 lectures facturées) chez chaque spectateur qui a le Feed ouvert, pendant les 23 h de la photo** (jusqu'à 72 lectures par jour pour une photo avec 3 spectateurs et 6 changements d'état par jour : le coût suit les changements d'état et les spectateurs, pas le nombre de photos). **Signal de bascule** : ratio > 150 deux jours de suite qui monte nettement quand les photos sont en usage ; **levier gratuit** (sans Blaze) : ne relire la photo que si `hasPhoto` change ou que la copie est ancienne (décision PM, aucun changement de code livré). Limite connue : le cache persistant du SDK peut garder le document (voir `data-model.md`, point 27). | Firestore > Utilisation, journal de la recette (`docs/qa/checklists/feed-8-ios-recette-pm.md`, partie F) |
| **Rappel** | le **20 000 écritures par jour** de Spark compte **toutes** les écritures du projet (statuts, positions, photos, adhésions, lots de lieux), et se remet à zéro à minuit **heure du Pacifique (≈ 9 h à Paris)**, pas à minuit chez la personne | |
| Suppressions Firestore : 20 000 par jour | rarement en jeu | Firestore > Utilisation |
| Transfert sortant : 10 GiB par mois | 6 GiB par mois | Firestore > Utilisation |
| Stockage : 1 GiB | rarement en jeu avant des milliers d'utilisateurs | Firestore > Utilisation |
| Authentication : 50 000 utilisateurs actifs par mois | très loin | Authentication > Utilisation |
| App Check, Play Integrity : 10 000 appels par jour (palier standard, d'après la documentation Firebase) | à surveiller dès quelques centaines d'utilisateurs : chaque jeton demandé compte | Console Google Cloud > API Play Integrity > Métriques (emplacement exact à vérifier) |


---

## 4. Alertes automatiques (facultatif, à vérifier par le PM)

Une alerte qui t'écrit quand les lectures dépassent un seuil serait idéale, mais **sa disponibilité sur le plan gratuit n'est pas confirmée** (la documentation Google dit que les alertes d'usage se configurent dans Cloud Monitoring, sans préciser si la facturation est requise). À tester par toi :

1. Console Google Cloud (projet `miff-3d283`) > **Monitoring** > **Alertes** > **Créer une règle d'alerte**.
2. Métrique : Cloud Firestore > « Document Reads » (lectures de documents). Seuil : par exemple 30 000 sur 24 heures.
3. Canal de notification : ton e-mail.
4. **Si Google demande d'activer la facturation ou de saisir un moyen de paiement : arrête-toi.** Il n'y a alors pas d'alerte gratuite ; on reste sur le relevé manuel de la section 1. Ne saisis aucun moyen de paiement sans décision explicite.

Dans tous les cas, ton relevé quotidien reste la référence.

---

### Le Feed (FEED-1 à FEED-7) : ce qu'il coûte et ce qui n'est pas protégé encore

Le Feed est le premier gros consommateur de lectures : **environ 130 lectures par utilisateur actif et par jour** (estimation du ticket `docs/tickets/feed.md` §11, pas encore mesurée ; le modèle de ce document en comptait ~75). Le ratio « normal » monte donc à ~130 : tant que le recalibrage de `backend-data` n'est pas fait, un faux « À examiner » est attendu.

- **Ouverture à froid** : 9 + 2 x membres + lieux lectures (environ 20 pour 4 membres et 3 lieux).
- **« Actualiser » (tirer pour actualiser) relit tout, comme une ouverture à froid (~20 lectures), SANS garde côté serveur** : seule la limite de l'application (une relecture forcée toutes les 15 s, soit au plus ~4 par minute) la borne, et un client modifié la contourne. **Firebase App Check est à prévoir** (déjà dans les décisions du projet) avant d'ouvrir la bêta à des inconnus ; c'est lui qui protège le quota contre un usage abusif.
- **Retour d'onglet ou d'arrière-plan** : l'écoute est détachée hors de l'onglet Feed (voulu, rien n'écoute sur le Profil) ; y revenir **dans les 30 minutes** ne coûte qu'environ 10 lectures (les lectures de règles ; les documents inchangés sont resservis par le cache). **Au-delà de 30 minutes**, Firestore refacture tous les documents : le retour coûte alors une ouverture à froid (~20 lectures).
- **« Localiser »** : 6 lectures par toucher (1 + 5 de règles).

### Le carrousel de Miffs du Profil (décision du PM du 2026-11-10) : ce qu'il coûte

La carte de chaque Miff du Profil montre le nombre de membres et leurs photos en miniature : cela demande la **liste des fiches `members` de chaque Miff** (les règles l'autorisent à un membre d'une Miff qui existe : `allow get, list: if isMember(miffId) && miffAlive(miffId)`). Android : `FirestoreMemberFacesReader` (une lecture à la demande, **jamais d'écouteur**), règles de réutilisation dans `MiffFacesBook` (testées).

| Cas | Lectures | Pourquoi |
|---|---|---|
| **Compte gratuit** (1 Miff) | **0 de plus** | la carte de la seule Miff est dessinée avec les membres que le Feed a déjà lus (la Miff active) |
| **Miff+**, la Miff active du Feed | **0 de plus** | idem : réutilisée (les photos aussi), **telle que le Feed l'a lue à sa dernière visite** : l'écoute du Feed n'est attachée que sur l'onglet Feed, donc un membre arrivé ou parti pendant que la personne est ailleurs n'apparaît qu'à son retour sur le Feed (limite connue, acceptée pour ne rien lire de plus). Sa propre photo et son prénom suivent le profil du téléphone tout de suite (0 lecture) ; l'ouverture de l'écran de la Miff relit la carte une fois |
| **Miff+**, une autre Miff, au moment où sa carte s'affiche (le carrousel s'arrête dessus) | **M + 2** (M fiches de membres, 15 au plus, + 2 lectures de règles) | une seule lecture, puis réutilisée **5 minutes** ; un voisin simplement préparé hors de l'écran (pour le lecteur d'écran) **ne lit rien** |
| Retour sur le Profil ou sur une carte déjà lue, dans les 5 minutes | **0** | réutilisé |
| Retour sur une carte après 5 minutes | **M + 2** | une relecture (jamais en boucle) |
| Retour d'un écran de Miff ouvert depuis sa carte | **M + 2** (une fois) | des membres ont pu être retirés : la carte est relue à sa prochaine apparition |
| Lecture qui échoue (hors ligne, lenteur) | 0 à M + 2, puis **attente de 30 s** avant un nouvel essai | la carte garde son nom, sans message ; jamais de boucle |

**Estimation, pas une mesure** : un Miff+ avec 3 Miffs de 5 membres : 2 x 7 = **14 lectures** à la première ouverture du Profil de la journée, **0 à 14** ensuite (selon qu'il repasse après 5 minutes). Avec N Miffs, au pire N x (M + 2) si la personne les fait toutes défiler d'un coup (10 Miffs de 15 membres : 170), puis plus rien pendant 5 minutes. Les photos (20 Ko au plus chacune, bornées par les règles) comptent dans le transfert sortant, pas dans les lectures : 15 membres = 300 Ko au plus par Miff lue.

**Limite connue** : le coût est proportionnel au nombre de Miffs d'un compte **Miff+** (aujourd'hui accordé à la main : quelques testeurs) et à leur défilement ; il n'y en a aucun pour un compte gratuit. **Signal** : celui du §2 (ratio > 150 deux jours de suite) ; si les comptes Miff+ font monter le ratio, **leviers gratuits** avant de parler de Blaze : allonger la réutilisation (5 minutes à 1 heure, constante `MiffFacesBook.TTL_MILLIS`), ou n'afficher que des initiales pour les Miffs autres que la Miff active.

### Les lieux de la Miff du carrousel, visibles par tous les membres (décision du PM du 2026-11-11) : ce que ça coûte

Sous le carrousel, l'onglet Profil montre la liste des lieux de confiance de la Miff dont la carte est affichée, **à tous ses membres** (les règles l'autorisent déjà : `match /places/{placeId}`, `allow get, list: if isMember(miffId) && miffAlive(miffId)`, aucune modification). Android : `FirestorePlacesReader` (**un seul `get`** de `miffs/{id}/places`, **jamais d'écouteur**), règles de réutilisation dans `MiffPlacesBook` (testées, mêmes règles que `MiffFacesBook`). **Ce changement supprime** l'écoute en direct (3 écouteurs : Miff, membres, lieux) que l'ancienne liste attachait à la Miff du Feed tant que le Profil était à l'écran.

| Cas | Lectures | Pourquoi |
|---|---|---|
| La Miff du Feed (compte gratuit : la seule) | **0 de plus** | ses lieux sont ceux que le Feed a déjà lus (comme pour les photos du carrousel : état de la dernière visite du Feed ; une copie du cache du téléphone non confirmée par le serveur n'est pas reprise) |
| Une autre Miff, au moment où sa carte s'affiche (le carrousel s'arrête dessus) | **P + 2** (P lieux, 3 au plus en gratuit, + 2 lectures de règles) | une seule lecture, puis réutilisée **5 minutes** ; un voisin préparé hors de l'écran ne lit rien |
| Retour sur le Profil ou sur une carte déjà lue, dans les 5 minutes | **0** | réutilisé |
| Retour sur une carte après 5 minutes | **P + 2** | une relecture (jamais en boucle) |
| Le **créateur** ouvre un lieu ou « Ajouter un lieu », puis revient | **P + 2** (une fois) | un lieu a pu être ajouté, modifié ou supprimé : les listes sont relues à leur prochaine apparition. Un **membre** qui ouvre un lieu (lecture seule) ne déclenche **aucune** relecture |
| Lecture qui échoue (hors ligne, refus) | 0 à P + 2, puis **attente de 30 s** | la liste dit calmement « Les lieux ne sont pas disponibles pour le moment. » avec « Réessayer » (qui relit tout de suite) ; jamais de boucle |

Ouvrir le **détail** d'un lieu garde son écoute en direct de la Miff (3 écouteurs, comme avant), tant que l'écran est ouvert : inchangé.

**Limite connue** : un lieu ajouté ou retiré par le créateur sur un autre téléphone n'apparaît chez les membres qu'à leur prochaine relecture (au plus 5 minutes sur le Profil, ou à leur retour sur le Feed qui écoute à nouveau). Les lieux changent rarement et la détection est lente par conception (« dans les prochaines heures », ticket Lieux). **Signal** : celui du §2 ; **levier gratuit** : allonger `MiffPlacesBook.TTL_MILLIS`.

**iOS (même décision, même principe)** : `FirestorePlacesReader` (un seul `getDocuments` de `miffs/{id}/places`, jamais d'écouteur), règles de réutilisation dans `MiffPlacesBook` (testées, mêmes chiffres : 5 minutes, 30 s après un échec), `ProfilePlacesViewModel`. La Miff du Feed est reprise sans lecture (`FeedDependencies.onPlaces`, seulement une réponse confirmée par le serveur). **Différence avec Android** : la liste n'existait pas encore sur le Profil iOS, donc ce changement n'ajoute aucun écouteur et n'en supprime aucun ; pour un compte gratuit (une seule Miff, celle du Feed) il ajoute **0 lecture**. Une copie du téléphone VIDE est traitée comme un échec (jamais « aucun lieu » inventé hors ligne). **Levier gratuit** : `MiffPlacesBook.freshMillis`.

## 5. Journal de relevé (modèle à recopier)

Une ligne par jour de bêta externe, dans un fichier à toi (ou dans une conversation avec Claude) :

| Date | Lectures | Utilisateurs actifs | Ratio | Nouveaux comptes | Requêtes vérifiées (App Check) | Niveau | Remarque |
|---|---|---|---|---|---|---|---|
| (jj/mm) | | | | | | Normal / À examiner / Signal | |

**Deux jours de suite au niveau « Signal » = décision à prendre**, pas à reporter.

---

## 5 bis. iOS : coût de la batterie rafraîchie à l'ouverture et à la sortie (décision du PM, 2026-11-07)

> Section propre à iOS (`ios/CHANGELOG.md`). Android a la sienne. **Estimations, pas des mesures** : à relever à la recette (`docs/qa/checklists/batterie-ios-recette-pm.md`).

**Ce que fait l'app** : quand la personne ouvre ou quitte l'app, elle réécrit son niveau de batterie dans `miffs/{id}/status/{uid}` de chaque Miff où elle partage (un `update` de `batteryPct` + `updatedAt`, aucune règle Firestore modifiée). Pour que ça reste raisonnable :

| Limite (constante `TrackingLimits`) | Valeur | Effet |
|---|---|---|
| `batteryMinIntervalMillis` | 15 min | jamais deux rafraîchissements (ou tentatives) d'une même Miff à moins de 15 min |
| `batteryMinDeltaPoints` | 5 points | rien si la batterie a bougé de moins de 5 points depuis la dernière valeur écrite... |
| `batteryMaxAgeMillis` | 3 h | ... sauf si cette dernière écriture a plus de 3 h (le Feed masque la batterie après 6 h) |
| `maxBatteryWritesPerDay` | 40 par jour et par téléphone | compteur **dédié** (pas les 60 statuts : un rafraîchissement ne prend jamais la place d'une arrivée ou d'un départ) ; pire cas 60 + 40 = 100 statuts par téléphone et par jour |

**Coût d'UN rafraîchissement dans UNE Miff** :

| Poste | Quantité | Remarque |
|---|---|---|
| Écriture Firestore | 1 | compte dans les 20 000 écritures par jour de Spark |
| Lectures de règles | 2 | `validStatus` : la session de l'appareil (1) + mon partage actif (1) |
| Lecture de session côté app | 1 par passage qui écrit (pas par Miff) | `DeviceSession.clearance`, déjà faite avant toute première écriture d'un passage |
| Lectures de snapshot chez les proches | jusqu'à N - 1 | une par proche dont le Feed écoute en ce moment (N = membres de la Miff) ; 0 si personne n'a l'app ouverte |

Soit **≈ 3 lectures + 1 écriture pour une Miff, plus N - 1 lectures de snapshot** au plus. Un passage sans rafraîchissement (moins de 15 min, moins de 5 points, niveau inconnu, hors ligne) **ne lit ni n'écrit rien**.

**Hypothèses (recalibrées après la revue indépendante)** : par utilisateur actif et par jour, **6 à 12 écritures de batterie par Miff** en usage normal (valeur centrale 9 : ouvertures et sorties espacées de 15 min, avec un niveau qui bouge de 5 points ou plus, plus le rafraîchissement de 3 h), 1 Miff de 4 membres (N - 1 = 3 proches à l'écoute : pire cas raisonnable, soit 3 + 3 = 6 lectures par écriture, 54 par utilisateur et par jour). Référence du reste de l'app, version prudente : 10 statuts + 50 à 60 positions = 60 à 70 écritures par utilisateur actif et par jour (haut des fourchettes du §3) et 210 à 355 lectures (§3).

| Utilisateurs actifs par jour | Écritures de batterie / jour (centrale ; plage 6 à 12) | Lectures de batterie / jour (centrale ; plage) | Part de Spark pour la batterie seule (écritures 20 000 / lectures 50 000) | Pire cas : 40 documents par jour et par téléphone |
|---|---|---|---|---|
| 100 | ≈ 900 (600 à 1 200) | ≈ 5 400 (3 600 à 7 200) | 4,5 % / 11 % | 4 000 écritures (20 %), 24 000 lectures (48 %) |
| 1 000 | ≈ 9 000 (6 000 à 12 000) | ≈ 54 000 (36 000 à 72 000) | 45 % / 108 % : les lectures dépassent déjà Spark | 40 000 écritures, 240 000 lectures |
| 10 000 | ≈ 90 000 (60 000 à 120 000) | ≈ 540 000 (360 000 à 720 000) | 4,5 fois le quota d'écritures, 11 fois celui de lectures | 400 000 écritures, 2,4 millions de lectures |

**Ce que ça change pour la limite de Spark** (estimation, à mesurer) : avec 60 à 70 écritures par utilisateur actif pour le reste de l'app, le plafond de 20 000 écritures par jour tient jusqu'à ~285 à 335 utilisateurs actifs par jour ; avec la batterie (+ 9 à 12), il est atteint vers **250 à 300 utilisateurs actifs par jour** (≈ 290 avec 10 statuts, 50 positions et 9 batteries ; ≈ 245 avec 60 positions et 12 batteries). Avec les fourchettes basses du §3 (6 statuts, 20 positions), on irait jusqu'à ~600, mais **le calcul prudent est celui à retenir**. Côté lectures, + 54 par utilisateur (+ 15 à 26 % des 210 à 355 du §3) : le plafond de 50 000 lectures passe de **~140 à 240** à **~120 à 190** utilisateurs actifs par jour. Autrement dit : **la batterie rapproche la bascule de 13 à 20 % environ ; elle ne la crée pas** (les positions pèsent plus), mais elle n'est pas négligeable.

**Cas Miff+ avec N Miffs** (M6) : le compteur de 40 compte les **documents écrits** (un par Miff) par téléphone, pas les ouvertures. Une personne Miff+ dans N Miffs écrit **N documents par rafraîchissement** : le coût d'usage normal est environ **9 × N** écritures par jour, et le plafond de 40 est atteint en 40 / N passages complets (4 passages avec 10 Miffs). Passé le plafond, les Miffs sont servies à tour de rôle le lendemain (curseur de rotation) et certaines gardent un niveau plus ancien : c'est le comportement voulu, pas un bogue. Les lectures de règles suivent (2 par document) ; la lecture de session reste 1 par passage.

**Horloge du téléphone** (M2) : le jour du disjoncteur et les durées de 15 min et de 3 h viennent de l'horloge du téléphone. Une horloge **reculée** rend la comparaison impossible : la règle laisse alors écrire (comme pour la position), et seuls le plafond de 40 et les règles de sécurité limitent. Une horloge en **avance** de plus de 5 minutes fait refuser l'écriture par les règles (`updatedAt` est l'heure du serveur, mais d'autres champs peuvent être jugés) ; le refus n'est pas rejoué avant 15 min. **Limite connue** : le disjoncteur est un garde-fou de l'app, pas une barrière de sécurité (un client modifié peut l'ignorer) ; la protection contre l'abus reste App Check et la surveillance de ce document.

**Ordre de grandeur sous Blaze** (tarifs publics Google à vérifier avec leur calculateur, non confirmés ici ; les quotas gratuits quotidiens restent offerts) : à 10 000 utilisateurs actifs (valeur centrale), la batterie ajoute ~2,7 millions d'écritures et ~16 millions de lectures par mois, soit **de l'ordre de 7 à 9 EUR par mois** (plage haute : jusqu'à ~12 EUR). Supportable, mais plus du double de la première estimation.

**Signal de bascule** (aucun agent n'active Blaze ; la décision est celle du PM) :
- le signal général du §2 (ratio lectures / utilisateurs actifs > 150 deux jours de suite) ou plus de **12 000 écritures par jour** dans la console ;
- spécifique à la batterie : un testeur dont le journal montre **plus de 15 `batteryRefreshed` par jour et par Miff** un jour normal (les limites ne jouent pas comme prévu), ou le disjoncteur de 40 atteint (`breakerStatus` dans le journal juste après un `batteryRefreshSkipped`).

**Leviers gratuits avant Blaze** (dans l'ordre, par un petit changement de constante) : passer le seuil de 5 à 10 points ; passer l'intervalle de 15 à 30 min ; ne rafraîchir qu'à la sortie ; baisser le plafond de 40 à 20. Aucun ne demande de réécriture.

---

## 5 ter. Android : batterie rafraîchie à l’ouverture et à la sortie de l’app (2026-11-07)

**Ce que fait l'application.** À chaque passage au premier plan et à chaque passage à l'arrière-plan, si la personne est éligible (connectée, appareil actif, consentement de localisation v3, partage activé, statut existant), elle réécrit **le niveau de batterie seul** (et l'heure d'écriture `updatedAt`) dans le statut de chaque Miff où elle partage : un lot d'une seule écriture par Miff, aucune position, aucune lecture du serveur par le rafraîchissement lui-même. Les écritures de statut existantes (arrivée, départ, update, accès à la localisation) continuent d'écrire le niveau frais et **comptent comme un rafraîchissement** (pas de doublon).

**Les garde-fous de coût** (constantes nommées dans `TrackingLimits.kt`, un seul endroit à changer) :
- **15 min** au moins entre deux tentatives (`BATTERY_MIN_INTERVAL_MILLIS`), quel que soit le résultat (un refus n'est pas répété à chaque ouverture) ;
- **pas d'écriture si le niveau a bougé de moins de 5 points** depuis la dernière valeur écrite (`BATTERY_MIN_DELTA_PCT`), **sauf si la dernière écriture a plus de 3 h** (`BATTERY_FORCE_AFTER_MILLIS` : le Feed masque la batterie au-delà de 6 h) ;
- **disjoncteur du jour dédié : 40 écritures de batterie par téléphone et par jour** (`MAX_BATTERY_WRITES_PER_DAY`, compté par document écrit, c'est-à-dire par Miff). Choix : un compteur **à part** et non les 60 écritures de statut existantes, pour qu'un téléphone souvent ouvert ne puisse jamais priver ses proches d'une arrivée ou d'un départ (le cœur du produit) ; le plafond absolu d'un téléphone passe de 60 statuts + 150 positions à **60 + 150 + 40** écritures par jour. **Alignement iOS** (même règle des deux côtés, voir « 5 bis. iOS ») : le rafraîchissement s'arrête aussi quand le disjoncteur des statuts est épuisé ;
- une écriture attend **4 s** au plus et le passage ENTIER (vérification de la session comprise) **8 s** au plus, chaque attente étant bornée par le temps restant, pour ne jamais retarder le recalcul des zones et de la position à la sortie ; un passage qui ne peut pas démarrer tout de suite (le moteur est occupé) est abandonné.

**Coût d'un rafraîchissement, par Miff** (estimations, pas des mesures) :
| Poste | Quantité |
|---|---|
| Écriture du statut (le niveau + `updatedAt` + `deviceId`) | **1 écriture** |
| Lectures de règles (session de l'appareil + fiche de membre, comptage SESS-1 de `backend/data-model.md`) | **3 lectures** (budget : très loin des 20 par lot) |
| Écouteurs des proches : chaque écriture de statut réveille l'écouteur de chaque autre membre qui a le Feed ouvert | **jusqu'à N−1 lectures** (N = membres de la Miff ; un proche dont l'app est fermée ne coûte rien de plus : il relit tout à sa prochaine ouverture, déjà comptée) |
| Lectures de données par le rafraîchissement lui-même | **0 lecture de données, au plus 1 lecture de session si la dernière vérification date de plus de 10 min** |

Soit, pour une Miff de 4 membres : **6 lectures facturées par écriture** (borne haute). Estimation par utilisateur actif et par jour : **~6 rafraîchissements** (5 ouvertures et sorties espacées de plus de 15 min, la plupart avec un changement d'au moins 5 points ou une dernière écriture de plus de 3 h) = **6 écritures + ~36 lectures** ; jour chargé : 10 écritures + 60 lectures ; plafond absolu : 40 écritures par téléphone.

**Scénarios** (uniquement ce chantier, Miff de 4 membres, borne haute des lectures ; part du quota gratuit Spark du jour, 20 000 écritures et 50 000 lectures pour tout le projet) :
| Utilisateurs actifs par jour | Écritures (6 / 10 par jour) | Part de 20 000 | Lectures (36 / 60 par jour) | Part de 50 000 |
|---|---|---|---|---|
| **100** | 600 / 1 000 | 3 % / 5 % | 3 600 / 6 000 | 7 % / 12 % |
| **1 000** | 6 000 / 10 000 | 30 % / 50 % | 36 000 / 60 000 | 72 % / 120 % |
| **10 000** | 60 000 / 100 000 | 300 % / 500 % | 360 000 / 600 000 | 720 % / 1 200 % |

**Ce que ça change à la limite de Spark** (l'application dépasse de toute façon les quotas gratuits bien avant 1 000 utilisateurs actifs, voir plus haut) :
- **Lectures** : de ~176–320 à ~212–356 par utilisateur actif et par jour (hors photos), donc le plafond de 50 000 lectures passe de **~155–285 à ~140–235 utilisateurs actifs par jour** (−10 à −18 %), et à ~130–210 les jours chargés. L'ordre de grandeur ne change pas.
- **Écritures** : l'estimation de départ (6 à 10 statuts + 20 à 60 positions par jour) donne 285 à 770 utilisateurs avant 20 000 écritures ; avec la batterie (+6) : **~265 à 625**. Les **écritures** sont donc, avec les lectures, un plafond du même ordre : à surveiller à part.
- **Sur Blaze** (non activé, décision du PM) : à 10 000 utilisateurs actifs, ce chantier représente ~2 à 3 millions d'écritures et ~11 à 18 millions de lectures par mois, soit de l'ordre de **10 à 20 € par mois** (estimation grossière à partir de tarifs publics non revérifiés pour `europe-west9` : à confirmer dans le calculateur Google). Pas de coût caché hors Firestore.

**Deux cas particuliers à connaître** (limites connues, assumées) :
- **Miff+ avec plusieurs Miffs** : le plafond de 40 est compté **par document écrit**, donc par Miff, pas par rafraîchissement. Un compte Premium dans N Miffs consomme N écritures par rafraîchissement (6 Miffs = 6 écritures et 18 lectures de règles pour un passage) et atteint les 40 en 6 à 7 rafraîchissements au lieu de 40 ; les écouteurs des proches se multiplient de la même façon (jusqu'à N−1 lectures par Miff et par rafraîchissement). Les chiffres des scénarios ci-dessus valent pour **une** Miff : pour un Premium dans 3 Miffs, multiplier par 3 les écritures et les lectures de ce chantier. Le plafond protège donc surtout les comptes gratuits (une Miff) ; pour un Premium il borne à environ 40 écritures par jour, ce qui reste très en dessous des 60 statuts et 150 positions.
- **Horloge reculée ou changement de date** : les compteurs du jour (statuts, positions, batterie) sont rangés par date du téléphone et **repartent de zéro** quand la date change, y compris si l'utilisateur recule l'horloge ou change de fuseau. C'est un défaut **préexistant** de tous les disjoncteurs de LOC-2b, que la batterie hérite sans l'aggraver : le plafond reste une protection contre une boucle de l'application, pas contre un utilisateur qui manipule son horloge (les règles refusent de toute façon une heure trop éloignée pour les statuts et les positions ; l'écriture de batterie, elle, ne porte aucune heure d'appareil, seulement `updatedAt` du serveur).

**Signal pour agir** (les seuils d'alerte des sections 2 et 3 ne sont **pas modifiés** par ce chantier) :
1. Les relevés habituels (écritures du jour > **12 000**, ratio lectures ÷ utilisateurs actifs > **150** deux jours de suite) restent les déclencheurs. Si l'un d'eux se déclenche après la mise en service, **regarder d'abord la part de la batterie** : dans la version de développement, le journal de détection compte les lignes `BATTERY_WRITTEN` (recette, partie C) ; plus de 10 par téléphone et par jour sur une journée normale est anormal.
2. **Leviers gratuits, un seul chiffre à changer chacun** : passer l'intervalle de 15 min à 30 min ou 60 min ; passer les 5 points à 10 ; ne rafraîchir qu'à l'ouverture et plus à la sortie (la moitié des écritures) ; baisser le plafond de 40. Tous réduisent le coût sans toucher à la sécurité.
3. **Bascule en Blaze** : la même que pour le reste (écritures ou lectures du jour au seuil des sections 2 et 3 de façon répétée), décision du PM ; le plafond de 40 écritures par téléphone borne une application **non modifiée** qui boucle ; il ne protège pas d'un client modifié (les règles ne peuvent pas compter les écritures : c'est le rôle d'App Check et de la surveillance de la section 1).

---

## 5 quinquies. Android : notifications locales (NOTIF-P0, 2026-11-08)

**Ce que fait l'application (PR A : autorisation, Réglages, centre).** Rien ne touche Firestore : les choix de Réglages > Notifications, le centre de notifications (30 jours ou 100 lignes), l'état de l'autorisation et la carte de « deuxième chance » vivent dans des fichiers privés du téléphone (hors sauvegarde), effacés à la déconnexion. **0 lecture, 0 écriture, 0 appel réseau, 0 service payant, 0 dépendance nouvelle.** Le centre n'écrit **jamais** sur le serveur : l'historique en base coûterait ≈ 15 écritures par utilisateur et par jour (≈ +7 $ par mois à 10 000 utilisateurs, ticket §4.5), c'est le choix écarté.

**Coût par notification** :

| Notification | Lectures Firestore | Écritures Firestore | Remarque |
|---|---|---|---|
| N1, N2 (rappels « à toi ») | **0** | **0** | décision locale à partir du dernier statut déjà connu du téléphone (l'instantané écrit par le Feed, qui lit déjà ces statuts) |
| N6 (j'entre dans une zone de danger) | **0** | **0** | locale et immédiate, déclenchée par le suivi des lieux déjà livré |
| N7 (mon mode voyage est terminé, VOY-4A) | **0** | **0** | locale, alarme inexacte à 08:00 ; la fin du voyage est connue du téléphone (aucune lecture) |
| N3, N4 (ma Miff change d'état) | **N + 3 par vérification** (N = membres de la Miff), relevées **à l'heure prévue** seulement | **0** | UNE lecture du serveur (`miffs/{id}/status`, `Source.SERVER`) juste avant d'afficher ; **au plus 2 vérifications par épisode** (la seconde seulement si la première n'a pas pu joindre le serveur) ; aucune vérification si le type est coupé, si la session n'est pas valide ou si la clé a déjà été notifiée |

**N3 / N4 en chiffres** (estimations, pas des mesures ; Miff de 4 membres : 4 lectures de statuts + 3 lectures de règles = **7 lectures** par vérification ; 15 membres : **18**). Un épisode (la Miff passe orange, puis rouge) coûte une vérification **par observateur et par couleur** : **7 à 14 lectures par jour et par utilisateur actif** (cas courant : 1 à 2 épisodes par jour, par exemple une journée de travail hors du seul lieu gratuit, le rouge ne venant que si personne ne donne de nouvelles). À comparer aux ~60 lectures par jour que le Feed coûte déjà.

| Utilisateurs actifs par jour | Lectures N3 / N4 (7 / 14 par jour) | Part des 50 000 lectures gratuites |
|---|---|---|
| **100** | 700 / 1 400 | 1,4 % / 2,8 % |
| **1 000** | 7 000 / 14 000 | 14 % / 28 % |
| **10 000** | 70 000 / 140 000 | 140 % / 280 % |

**Limite connue et signal de bascule.** Les notifications locales ne coûtent rien quel que soit le nombre d'utilisateurs : il n'y a pas de seuil de bascule pour ce qui est local. Le seul poste qui pèse sur le quota de lectures est la vérification de N3 / N4 ; il entre dans les relevés habituels (sections 1 à 3) : **si le ratio de lectures par utilisateur actif dépasse 150 deux jours de suite** (signal des sections 2 et 3), regarder la part de N3 / N4 (+7 à +14 lectures par jour) parmi les causes. **Leviers gratuits, un seul chiffre à changer chacun** : passer `MAX_VERIFICATIONS` de 2 à 1 (la seconde lecture, 10 minutes après une première tentative hors connexion, disparaît), ne vérifier N4 que si N3 a été vérifiée, ou retirer la vérification (alors N3 / N4 ne seraient plus fiables : à ne faire que sur décision du PM). **Ce que ces notifications ne font pas** : prévenir un **autre** téléphone au moment précis (arrivée, « pas de nouvelles de X », N5), qui exige un serveur (Blaze, décision du PM, phase 2).

**Sécurité de cette lecture.** Elle passe par les **règles Firestore existantes** (le membre qui lit est celui connecté, avec le jeton Firebase et App Check du téléphone) : aucune clé, aucun compte de service, aucune règle ouverte. Un refus des règles (« je ne partage plus », « on m'a retiré ») annule la notification en silence. **À vérifier en recette sur l'OPPO** : l'obtention du jeton d'authentification et d'App Check en arrière-plan, app fermée (`docs/qa/checklists/notifs-android-recette-pm.md`, partie 5).


---

## 5 quater. Android : ligne « Abonnement » des Réglages (ABO-1, 2026-11-07)

**Ce que fait l'application.** À l'ouverture de Profil > Réglages (et à ce moment seulement), elle lit **une fois** le document `entitlements/{uid}` du compte connecté pour savoir s'il faut afficher la ligne « Abonnement » (Miff+ actif et non expiré). Pas d'écouteur, pas de relecture en revenant d'un sous-écran ni en tournant le téléphone, pas de lecture à l'ouverture de l'écran Abonnement (la date lui est transmise). Lecture **depuis le serveur** (pas de copie locale périmée) ; hors connexion ou en erreur, la ligne reste simplement absente, sans message.

**Coût : 1 lecture de document par ouverture des Réglages et par personne** (comptes gratuits compris : le document est absent, la lecture compte quand même). Aucune écriture. Ordre de grandeur : un utilisateur qui ouvre les Réglages 2 fois par jour ajoute 2 lectures aux ~75 estimées par utilisateur actif et par jour, soit moins de 3 %. Aucun seuil d'alerte n'est modifié.

**Limite connue et signal de bascule.** Si le ratio de lectures de la section 1 dérive de façon inexpliquée, vérifier d'abord que les Réglages ne sont pas rouverts en boucle. Levier gratuit si besoin : mémoriser la réponse en mémoire pour la durée de la session (un seul chiffre à changer). Rien ne nécessite Blaze.

---

## 5 quinquies. iOS : carrousel de Miffs du Profil (décision du PM, 2026-11-10)

**Ce que fait l'application.** Chaque carte de Miff de l'onglet Profil montre le nombre de membres et leurs photos en miniature : cela demande la **liste des fiches `miffs/{id}/members`** de chaque Miff. Code : `MiffMemberPreviews` (quand lire) et `FirestoreMemberPreviewReader` (la lecture). Android a le même principe (section 4, « Le carrousel de Miffs du Profil »).

| Cas | Lectures | Pourquoi |
|---|---|---|
| **Compte gratuit** (1 Miff) | **0 de plus** | la carte de la seule Miff est dessinée avec les membres que le Feed a déjà lus (la Miff active) |
| **Miff+**, la Miff active du Feed | **0 de plus** | idem, même si le Profil est ouvert des heures après |
| **Miff+**, une autre Miff, quand sa carte est montrée (la page s'arrête dessus, ou un glissement vers elle commence) | **M + 2** (M fiches de membres, 15 au plus, + 2 lectures de règles) | UNE lecture ponctuelle (`getDocuments`), jamais d'écouteur |
| Retour sur le Profil ou sur une carte déjà lue, dans les 5 minutes | **0** | réutilisé |
| Retour sur une carte après 5 minutes | **M + 2** | une relecture, jamais en boucle |
| Même carte demandée plusieurs fois pendant la même ouverture du Profil | **0** de plus | une seule lecture par Miff et par ouverture |
| Lecture qui échoue (hors ligne, lenteur) | 0 à M + 2 | pas de nouvel essai avant la prochaine ouverture du Profil ; la carte garde son nom, sans message |

**Estimation, pas une mesure** : un Miff+ avec 3 Miffs de 5 membres : 2 x 7 = **14 lectures** à la première ouverture du Profil de la journée, 0 à 14 ensuite (selon qu'il repasse après 5 minutes). Les photos (20 Ko au plus chacune, bornées par les règles ; 5 gardées par Miff en mémoire) comptent dans le transfert, pas dans les lectures.

**Limite connue et signal de bascule** : le coût est proportionnel au nombre de Miffs d'un compte **Miff+** (accordé à la main aujourd'hui : quelques testeurs) et à leur défilement ; il est nul pour un compte gratuit. Signal : celui de la section 2 (ratio > 150 deux jours de suite). **Leviers gratuits** avant de parler de Blaze : allonger la réutilisation (constante `MiffMemberPreviews.freshMillis`, 5 minutes), ou n'afficher que des initiales pour les Miffs autres que la Miff active.

**Sécurité** : une photo vient d'un autre téléphone ; seules les photos de 20 000 octets au plus et de 2048 px de côté au plus sont décodées, et réduites à la taille du rond (`AvatarDecoding`). Les noms et photos restent en mémoire pour la session (jamais sur le disque, jamais dans Analytics ni dans un journal).

## 5 sexies. Cloud Functions (SRV-1, Blaze activé le 2026-11-11) : quoi regarder

Tant que `ping` est la seule fonction, le coût attendu est **0 €**. Dès qu'une vraie fonction existe (notifications, SRV-4), ajouter à la revue (1 minute, une fois par semaine suffit au début) :

| Quoi | Où | Normal | À examiner |
|---|---|---|---|
| Invocations du mois | Console Firebase > Functions > onglet **Tableau de bord** (ou Google Cloud > Cloud Run) | Très en dessous des 2 millions gratuits par mois | Plus de 1 million (50 % du gratuit) : revue de l'usage |
| Erreurs | Functions > **Journaux**, filtre gravité « Erreur » | Aucune ou quelques-unes isolées | Des erreurs en série, ou des réponses `429` (limite d'instances atteinte : voir `deploiement-functions.md` §9) |
| Dépense | Google Cloud > Facturation > Rapports, filtre projet `miff-3d283` | 0 € à quelques centimes | Une ligne qui monte sans explication, ou un e-mail de l'alerte de budget (5 €/mois : 50 %, 90 %, 100 %) |

Les alertes de budget **ne plafonnent pas** la dépense ; le plafond de dépense Preview de Cloud Functions n'existe pas pour Firestore (`deploiement-functions.md` §1). Procédure pour désactiver une fonction : `deploiement-functions.md` §6.

---

## 6. Ce que les agents font de leur côté

- `backend-data` garde ces seuils et la procédure à jour (`backend/data-model.md` §12) et prépare, si le signal se confirme, la condition d'e-mail vérifié (gratuite) ou le plan de fonction « rejoindre » (Blaze).
- `ios-dev` / `android-dev` limitent les essais de saisie de code côté application (pause après 5 échecs) : une aide pour l'utilisateur, **pas** une protection de sécurité.
- Aucun agent n'active Blaze, ne crée d'alerte, ne saisit de moyen de paiement.
- **À qui remonter un signal** : Tomessa MFOUKA (tomessa.mfouka@gmail.com), qui seule décide de la bascule en Blaze.
