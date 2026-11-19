# Ticket : « Tirer pour actualiser » du Feed revérifie aussi MA présence (position)

**Statut** : mini-ticket rédigé par l'agent `ios-dev` (demande du PM du 17/11/2026, sans spec : « je te laisse faire »). Décisions prises par délégation, listées en §3 avec leur raison. Aucun statut Notion modifié, rien écrit dans Notion, aucun texte légal modifié (voir §7 : la politique de confidentialité gagnerait une précision).
**Rédigé le** : 2026-11-17. **Plateformes** : iOS (cette section, REFRESH-POS-I) et Android (REFRESH-POS-A, section §6, à ajouter par l'agent Android dans sa PR ; mêmes règles §2 et §3 pour garder la parité).
**Convention** : [Décidé PM] = demande du PM. [Décidé] = décision prise ici par délégation, avec sa raison. [Existant] = comportement déjà dans le code, relu avant de décider.

## 1. Demande du PM (mot pour mot)

> fais en sorte que le pull to refresh sur le Feed actualise la position de l'utilisateur, si ce n'est pas déjà le cas (sur Android et iOS)

## 2. Règles communes (iOS et Android)

| # | Règle | Raison |
|---|---|---|
| R1 | Le geste « tirer pour actualiser » du Feed continue de relire les proches (inchangé : 15 s mini entre deux lectures, 8 s au plus, hors connexion il se termine tout de suite). | [Existant] |
| R2 | **En plus**, il demande à la détection de revérifier MA présence : **une mesure de position ponctuelle** (jamais de suivi continu), puis le recalcul par rapport aux lieux de confiance de mes Miffs **par la machinerie existante** (aucune logique dupliquée). | [Décidé PM] |
| R3 | La position mesurée est **toujours fraîche** (jamais une position réutilisée de l'optimisation batterie BATT-3) : la personne demande « maintenant ». | [Décidé] |
| R4 | **Pas d'écriture inutile** : un statut n'est écrit que si la présence change ; la dernière position n'est republiée que si le garde-fou de fréquence existant la laisse passer (15 min, ou 250 m, jamais deux fois en moins de 2 min). Rien d'autre n'est ajouté. | [Existant] + coûts Firestore |
| R5 | **Limite de fréquence** : une revérification par 30 s au plus, **ouverture de l'app comprise** (elle vient de mesurer). Un geste plus rapproché n'actualise que les proches, sans bruit. | [Décidé] : batterie et lectures |
| R6 | **L'actualisation ne dépend jamais de la position** : la revérification est lancée en parallèle et **n'est pas attendue** ; l'indicateur se termine comme avant. | [Décidé] : fluidité |
| R7 | **Hors connexion** : le geste se termine tout de suite (comme avant) et ne revérifie rien ; aucune alerte. | [Existant] + [Décidé] : calme |
| R8 | **Autorisation absente, refusée ou approximative** : rien n'est mesuré ni envoyé, et le geste n'ajoute **aucun message** sur iOS (voir §3, D2) ; Android affiche un court message calme (§6, A1), à trancher par le PM. | [Décidé] |
| R9 | **Partage arrêté** (aucune Miff où je partage) : rien n'est mesuré ni envoyé. | [Existant] |
| R10 | **Aucune position stockée en plus** : ni nouvelle copie, ni historique, ni journal, ni événement Analytics (seul le journal local des builds de test note « geste utilisé », sans coordonnée). | Confidentialité |
| R11 | **L'instant du statut** est celui où le système répond « dedans / dehors » (heure de détection), jamais une heure inventée : même règle que pour une ouverture de l'app. Une arrivée manquée est donc écrite « maintenant », une sortie manquée devient « pas de nouvelles récentes » (`unknown`) à cet instant. | [Existant] |
| R12 | Annonce VoiceOver / TalkBack du geste **inchangée**. Aucun nouveau texte, aucune nouvelle traduction (exception Android : le message de R8, une clé fr / en). | [Décidé] |

## 3. Décisions et points à confirmer

- **D1 [Décidé] : réutiliser le passage d'« ouverture de l'app » du moteur plutôt que d'écrire une logique à part.** Le moteur sait déjà : mesurer une position, comparer avec ce qu'il croit (dedans / dehors), redemander au système l'état des zones contredites, écrire un statut en cas de changement, republier la dernière position avec garde-fou et disjoncteur, et tout cela en respectant autorisations, partage, consentement de version 3, session de l'appareil et mode voyage. Le geste ajoute une seule « raison » de passage, sans batterie.
- **D2 [Décidé] : aucun message nouveau pour l'autorisation absente.** La localisation « Toujours » + position exacte est exigée par une porte qui prend tout l'écran avant le Feed (décision du PM du 2026-11-01), et revient dès que l'autorisation est retirée. Un geste sur le Feed ne rencontre donc ce cas que dans une fenêtre très courte ou dans un build qui relâche le blocage (interrupteur de compilation). Dans ces cas rien n'est mesuré ni envoyé et le Feed reste calme. Un message supplémentaire serait un texte et un parcours non designés. **À confirmer par le PM** si un message est voulu quand même (proposition : « Active la localisation « Toujours » pour que tes proches te voient », avec un lien vers les Réglages, ton calme, sans « erreur »).
- **D3 [Décidé] : mode voyage.** Aucun statut de présence n'est écrit pendant un voyage (inchangé). La **position** reste partagée pendant un voyage (décision du PM du 2026-11-10) : le geste la rafraîchit donc comme une ouverture de l'app le fait déjà, avec les mêmes gardes. Si le PM préfère que le geste ne fasse rien du tout en voyage, c'est un changement d'une ligne. **À confirmer.**
- **D4 [Décidé] : pas d'écriture forcée de la position.** Un geste 5 minutes après la dernière écriture, sans avoir bougé de 250 m, ne republie pas la position (le garde-fou de 15 min / 250 m / 2 min reste la règle). Conséquence visible : « Localiser » chez un proche peut afficher une heure de mesure un peu plus ancienne que le dernier geste. Si le PM veut que chaque geste rafraîchisse l'heure affichée, il faut raccourcir ce garde-fou pour le geste (coût : jusqu'à une écriture par Miff et par geste, au plus une par 30 s). **À confirmer.**
- **D5 [Décidé] : pas de « battement de cœur ».** Rester chez soi sans bouger ne réécrit aucun statut (une présence déjà « chez toi » n'est pas réécrite) : la couleur d'un proche continue de suivre ses règles habituelles (délais depuis la dernière update). Ajouter un signe de vie périodique serait une nouvelle règle produit : non fait.
- **D6 [Décidé] : hors connexion, pas de mesure.** Une détection faite hors ligne serait mise en attente et envoyée au retour du réseau ; la surveillance des lieux le fait déjà seule. Le geste hors ligne reste donc l'actuel : il se termine tout de suite.

## 4. Ce qui existait déjà (état des lieux iOS, relu sur `origin/main` au 2026-11-17)

- Le geste (`FeedListView.refreshable` puis `FeedViewModel.pullToRefresh`) **ne relisait que les données des proches** : `FeedRefresher.refresh` (statuts, membres, lieux de la Miff affichée). **Il ne revérifiait pas MA présence et ne mesurait aucune position** ; le Feed ne parlait pas au suivi.
- Ma présence était recalculée **seulement** : à l'ouverture de l'app (`TrackingRuntime.appBecameActive` puis `TrackingEngine.refresh(.opened)`, avec `recheckStates` qui redemande au système l'état des zones contredites par la position), à la sortie de l'app, à une entrée / sortie de zone du système (`onTransition`), après un changement de position significatif, au retour du réseau, au changement de partage ou d'accès, et à la reprise du partage.
- Le moteur écrit un statut **seulement** quand la présence change (`PresenceMachine.onTransition` / `onMissedExit`) et republie la dernière position avec garde-fou (`PositionThrottle`) et disjoncteur (150 par jour).
- Mode voyage : le statut de présence est suspendu, la position continue d'être publiée (ouverture, sortie, déplacement significatif) ; localisation insuffisante : rien n'est mesuré (`locationComplete`).

## 5. Section iOS (REFRESH-POS-I)

**Branche** `ios/feed-refresh-position` (issue de `origin/main`), PR brouillon. **Rien n'est compilé en local** (la CI fait foi).

- `TrackingEngine.Reason.pulled` : le passage « geste du Feed » du moteur. Même chemin que l'ouverture, avec : mesure **toujours fraîche** (`mayReuseSystemFix` faux), `recheckStates` actif (recalcul de MA présence), relecture serveur des Miffs et des lieux **seulement quand elle est due** (une heure) ; **sans** rafraîchissement de la batterie.
- `TrackingRuntime.feedPulled()` : la porte d'entrée (limite de 30 s partagée avec l'ouverture ; `TrackingLimits.pullMinIntervalMillis`), travail ordonné et borné comme tous les autres (temps d'arrière-plan demandé puis rendu, 25 s au plus). Rend la main tout de suite.
- `FeedDependencies.onPullOwnPresence` : appelé par `FeedViewModel.pullToRefresh` **après** les gardes existantes (hors connexion, 15 s) et **sans attendre**. Branché dans `FeedTab` sur `environment.tracking`.
- Journal local des builds de test : nouvelle ligne `wakePull` (geste utilisé et revérification lancée). Aucune coordonnée.
- Précision de la mesure : inchangée (100 m, une seule demande `requestLocation`, délai d'attente 3 s au plus ; sans position, le moteur garde son comportement habituel).
- **Retour du PM, TestFlight 38 (2026-11-18)** : (1) l'indicateur ne se voyait pas : la roue d'UIKit est derrière l'en-tête et l'indicateur propre du Feed ne durait que le temps de la lecture. Il reste maintenant **au moins 0,8 s** (`FeedTimings.minRefreshDisplayMillis`, un minimum d AFFICHAGE : rien n'est relu de plus, la position n'est toujours pas attendue), y compris pour un geste trop rapproché qui ne relit rien (hors connexion : inchangé, fin immédiate), dans une pastille claire posée sous l'en-tête, au-dessus du reste. (2) Le Feed sautillait : **cause** = UIKit ajoute la hauteur de la roue à la marge haute de la vue de défilement pendant le geste, `ScrollDistanceReader` la lisait comme une nouvelle origine du contenu et SwiftUI réduisait la marge haute de la liste d'autant (puis l'inverse à la fin), pendant qu'UIKit animait le contenu : deux sauts. **Correction** : `RefreshInsetGuard` ignore la mesure d'origine pendant l actualisation et 0,6 s après ; l ordre du Feed, les identifiants des cartes et les règles du geste (15 s, 30 s, une mesure, aucune écriture si inchangé) sont inchangés. Cause établie par lecture du code, **à confirmer sur l'iPhone** (recette §6).
- **Retour du PM, build contenant #263 (2026-11-19)** : la pastille pilotée par le seul état du Feed n'était toujours pas visible sur iPhone. Elle est remplacée par un indicateur piloté par le DÉCALAGE de la vue de défilement (`FeedPullIndicator`, `FeedPullIndicatorLogic`) : flèche qui se remplit pendant le tirage, roue dès que l'état du Feed, la roue d'UIKit ou le relâchement d'un tirage assez long le dit, 0,8 s au minimum, au-dessus de tout (captures de CI forcées `feed-pull`, `feed-pull-ready`, `feed-loading`).
- Tests : `PullRefreshEngineTests`, `PullRefreshRuntimeTests`, `FeedPullOwnPresenceTests`, `PullRefreshSourcesTests`.

## 6. Section Android (REFRESH-POS-A)

**Branche** `android/feed-refresh-position` (issue de `origin/main` `90d2b497`), PR brouillon, recette `docs/qa/checklists/feed-refresh-position-android-recette-pm.md`, entrée `android/CHANGELOG.md` (numéro provisoire, à renuméroter au merge). Compilé et testé en local (spotless, lint, tests unitaires).

**Ce qui existait déjà côté Android [Existant, relu sur `origin/main`]** : comme sur iOS, `FeedViewModel.onRefresh()` ne relisait que les données des proches (`FeedRefresher` : Miff, membres, lieux, statuts ; 15 s mini, 8 s au plus, aucune écriture) et **ne revérifiait pas MA présence**. Différence de fond avec iOS : sur Android la présence se déduit **uniquement des événements du géofencing** (`INITIAL_TRIGGER_ENTER` à l'armement des zones, puis ENTER / EXIT) ; la position lue à l'ouverture sert à choisir les 20 zones les plus proches et à publier la dernière position, mais n'en déduit une présence qu'après un redémarrage du téléphone (`REARM`) ou une reprise de partage. Un événement raté (surcouches constructeur qui tuent les apps, zone perdue) laissait donc le statut faux jusqu'au suivant, y compris un départ non vu (vert figé chez les proches). Il n'existe pas d'équivalent de `recheckStates` : le système ne « redit » pas l'état des zones sur demande.

**Ce qui est fait** :

- `TrackingEngine.refreshFromFeed()` (nouvelle `Reason.FEED_PULLED`) : la passe habituelle `doRefresh`, **sans** batterie. Une **seule** lecture de position (`PositionSource.currentFresh()`, même fournisseur « équilibré » que partout, **jamais** la copie de 2 min de l'ouverture : un cache de 15 s au plus), attendue **3 s** au plus ; aucun suivi continu, aucune nouvelle permission.
- La présence de chaque Miff est recalculée **par la même machinerie** : `reconcile` (la fonction qui rattrape déjà les zones après un redémarrage) avec deux fonctions pures ajoutées à `PresenceMachine` (`insideAfterReading`, `afterReading`). Elle passe par `tellPresence` / journal comme un événement : les rappels N1/N2/N6 et le **futur journal local du graphique de temps** (PR #260, `logPresence` dans `reconcile`) la voient, **sans rien dupliquer**.
- **Arrivée** : dès que la position est **dans** le rayon d'un lieu que le statut ne nomme pas. **Départ** : seulement **au-delà du rayon + 100 m** (hystérésis : la position équilibrée est précise à quelques dizaines de mètres, une fausse alerte « parti » est pire qu'un départ tardif que le géofencing signalera seul). Un départ non vu devient **`unknown`** à l'instant du constat (jamais un `out` inventé, comme sur iOS et comme après un redémarrage : sa vraie heure est inconnue, un `out` daté de maintenant afficherait deux heures de vert de plus). Aucun statut « de confirmation » : `lastUpdateAt` n'est jamais touché, rien n'est écrit si la présence est déjà la bonne.
- **Une position de plus de 30 s ne déplace jamais ma présence** (`PULL_FIX_MAX_AGE_MILLIS`) ; sans position (timeout, indisponible) la passe se termine seule, rien n'est écrit.
- **Limite** : `TrackingLimits.PULL_MIN_INTERVAL_MILLIS` = 30 s, appliquée **par le moteur** (en mémoire seulement), en plus des 15 s de la relecture des proches ; une passe du moteur déjà en cours n'est **pas** mise en file (`tryLock`).
- **Gardes** : session, « Toujours » + précis (la position approximative seule compte comme insuffisante), localisation du téléphone allumée, hors connexion → rien n'est mesuré ; partage arrêté ou rien de partagé → rien ; consentement v3, session unique, disjoncteurs inchangés. **Mode voyage** : aucun statut de présence, la position est rafraîchie comme à l'ouverture (aligné sur iOS, D3).
- `FeedViewModel.refreshMyPresence()` : tâche à part, **non attendue** par l'indicateur ; exécutée dans la portée propre du moteur (`AppContainer.presenceRefresh`) : quitter l'onglet ne coupe pas une écriture. Annonce d'accessibilité inchangée.
- Tests : `PresenceFromReadingTest`, `TrackingEnginePullTest`, `FeedViewModelTest` (voir le CHANGELOG).

**Écarts volontaires avec iOS (à confirmer par le PM, voir aussi §3)** :

| # | Sujet | Android | iOS | Raison |
|---|---|---|---|---|
| A1 | Message quand l'accès à la localisation est insuffisant (R8, D2) | **Un court toast calme** : « Ta position n'a pas pu être actualisée. Pour cela, Miff a besoin que la localisation soit activée, sur « Toujours » et en précis. Tu peux régler ça dans les réglages de ton téléphone. » Pas d'écran, pas de bouton. Seul cas qui parle. | Aucun (D2) | La demande faite à l'agent Android demandait un message calme et utile. Un toast n'a pas de lien : le texte dit où aller ; quand c'est l'autorisation qui manque, la porte `PermissionGate` ramène déjà à l'écran dédié ; le toast sert surtout quand la localisation du téléphone est **coupée** pendant que le Feed est ouvert (cas que la porte ne voit pas). Clé partagée **`feed_refresh_position_unavailable`**, ajoutée aussi au catalogue iOS pour la parité (non utilisée par iOS tant que D2 n'est pas tranché : l'agent iOS peut la brancher ou la retirer, des deux côtés ensemble). Exception à R12 « aucun nouveau texte ». |
| A2 | Limite de 30 s (R5) | Entre **deux gestes** seulement ; l'ouverture de l'app n'est pas comptée. | Ouverture comprise | Sur Android l'ouverture ne recalcule pas la présence depuis la position : un geste juste après l'ouverture reste utile pour un départ non vu. |
| A3 | Départ non vu | Hystérésis de 100 m (déduit de la position) | Le système redit l'état des zones | Pas d'équivalent de `recheckStates` sur Android. Résultat identique côté proches (`unknown` à l'instant du constat). |

Les **cas de test purs** de `PresenceFromReadingTest` (arrivée dans le rayon, départ au-delà du rayon + 100 m, bord, pas d'`out` inventé, rien à écrire si rien ne change) sont le contrat de parité de comportement ; l'agent iOS les reprend s'il le juge utile.

## 7. Critères d'acceptation

1. Tirer le Feed vers le bas relit les proches **et** lance une mesure de position ponctuelle (au plus une par 30 s), sans que l'indicateur attende la position.
2. Si ma présence a changé (j'ai quitté ou rejoint un lieu de confiance sans que l'app l'ait vu), mon statut est mis à jour dans les secondes qui suivent chez mes proches ; sinon **aucune écriture de statut**.
3. Aucune écriture de position si le garde-fou de fréquence la refuse.
4. Sans « Toujours » + position exacte, sans partage, ou hors connexion : aucune mesure, aucun envoi, aucun message d'erreur ; l'actualisation des proches se fait normalement (ou se termine tout de suite hors connexion).
5. Mode voyage : aucun statut de présence écrit ; la position reste rafraîchie comme à l'ouverture.
6. Un second geste moins de 30 s après le premier (ou après l'ouverture de l'app) ne mesure rien.
7. Aucune coordonnée dans un journal, un événement Analytics ou un texte ; aucune nouvelle donnée stockée.
8. VoiceOver / TalkBack : annonce inchangée ; aucun nouveau texte.

## 8. Coûts (plan gratuit, Spark) et batterie

- Lectures du geste : **inchangées** pour les proches (9 + 2 × membres + lieux). La revérification n'ajoute **aucune lecture** tant que la copie locale a moins d'une heure ; au-delà, c'est la relecture habituelle (1 + 2 par Miff + un document par lieu).
- Écritures : **0** si rien ne change ; sinon au plus celles que le moteur ferait de toute façon (un statut par Miff touchée, une position par Miff), sous les disjoncteurs existants (60 statuts et 150 positions par jour et par téléphone). Pire cas ajouté par le geste : une écriture de position par Miff toutes les 15 min, ou toutes les 2 min si la personne marche (250 m ou plus entre deux écritures), toujours plafonnée à 150 par jour et par téléphone.
- Batterie : une mesure `requestLocation` à 100 m (une seule, ponctuelle, aucun suivi continu), au plus une par 30 s, aucun mode d'arrière-plan nouveau. **Signal de bascule** : un testeur qui voit sa batterie chuter avec l'usage du geste, ou plus de 20 lignes `wakePull` par jour dans le journal d'un build Internal : monter la limite de 30 s.

## 9. Confidentialité et textes légaux

La position mesurée est celle que le suivi publie déjà (dernière position, remplace la précédente, aucun historique) ; **aucune donnée nouvelle**. La politique de confidentialité décrit les moments d'enregistrement par des exemples (« à l'ouverture ou à la fermeture de l'app, après un déplacement important, en arrivant dans un lieu de confiance ») : le geste n'est pas contraire à cette liste, qui n'est pas limitative, mais **gagnerait à être complétée** (« ou quand tu actualises l'écran principal en le tirant vers le bas ») aux trois endroits où elle apparaît (FR et EN). Rien n'a été modifié ici ; décision du PM.

## 10. Hors périmètre

Notification, nouveau texte, nouvelle permission, nouvel écran, « battement de cœur » périodique, relecture forcée des lieux, mesure hors connexion, suivi continu de la position.
