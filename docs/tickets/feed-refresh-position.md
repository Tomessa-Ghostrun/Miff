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
| R8 | **Autorisation absente, refusée ou approximative** : rien n'est mesuré ni envoyé, et le geste n'ajoute **aucun message** (voir §3, D2). | [Décidé] |
| R9 | **Partage arrêté** (aucune Miff où je partage) : rien n'est mesuré ni envoyé. | [Existant] |
| R10 | **Aucune position stockée en plus** : ni nouvelle copie, ni historique, ni journal, ni événement Analytics (seul le journal local des builds de test note « geste utilisé », sans coordonnée). | Confidentialité |
| R11 | **L'instant du statut** est celui où le système répond « dedans / dehors » (heure de détection), jamais une heure inventée : même règle que pour une ouverture de l'app. Une arrivée manquée est donc écrite « maintenant », une sortie manquée devient « pas de nouvelles récentes » (`unknown`) à cet instant. | [Existant] |
| R12 | Annonce VoiceOver / TalkBack du geste **inchangée**. Aucun nouveau texte, aucune nouvelle traduction. | [Décidé] |

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
- Tests : `PullRefreshEngineTests`, `PullRefreshRuntimeTests`, `FeedPullOwnPresenceTests`, `PullRefreshSourcesTests`.

## 6. Section Android (REFRESH-POS-A)

*À compléter par l'agent Android dans sa PR (mêmes règles §2).*

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
