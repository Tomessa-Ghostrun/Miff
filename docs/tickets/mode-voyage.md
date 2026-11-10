# Ticket : MODE VOYAGE (pause du suivi et de la couleur, réservé à Miff+)

**Statut** : spécification **prête pour validation par le PM** (décisions du PM du 2026-11-10 intégrées ; 6 questions résiduelles en §17, chacune avec une valeur par défaut qui permet de démarrer). Aucun développement lancé, aucun statut Notion modifié, aucune règle déployée.
**Rédigé le** : 2026-11-10 (agent `product-specs`).
**Demande du PM (2026-11-10)** : dans l'onglet **Profil**, l'utilisateur active un **mode voyage** par un interrupteur et une **date de fin**. Pendant toute la période il n'a plus de statut vert / orange / rouge : il est **systématiquement gris**.

**Sources lues (worktree à jour, 2026-11-10)** : `CLAUDE.md` ; `backend/firestore.rules` (`validStatus`, `statusInstant`, `isPremiumOneRead`, `fromActiveDevice`, règles `status`, `positions`, `photos`) ; `backend/data-model.md` (§2.7, §3, §6, §8, §12, §13) ; code Android : `StatusColor`, `MemberStatusFacts`, `PresenceValue`, `PresenceMachine`, `TrackingEngine`, `TrackingReceiver`, `UpdatePrompt`, `MainTab`, `FeedBuilder` (miroir iOS lu : `FeedBuilder.swift`, `FeedBubbleBuilder.swift`, `TrackingRuntime.swift`) ; `docs/tickets/feed.md`, `update.md`, `lieux-de-confiance.md`, `session-appareil-unique.md`, `docs/tickets/notifications.md` §20 (NOTIF-P0), `docs/specs/paywall-premium.md`, `docs/contracts/analytics-events.md`, `docs/contracts/i18n-keys.md`.

**Limites de cette lecture** : pas d'outil shell (aucun test, aucune mesure, aucun commit). Les **coûts de lectures de règles** par lot sont des estimations à mesurer par `backend-data`. Les faits iOS (relance de l'app, tâche de fond) sont de **connaissance générale** et marqués [À vérifier]. Je n'ai modifié ni le code, ni Notion, ni les règles Firestore, ni les textes légaux.

**Convention** : **[Décidé]** = décision du PM (2026-11-10). **[Proposé]** = ma proposition avec valeur par défaut, à valider. **[À vérifier]** = fait non confirmé ce jour.

---

## 1. Résumé pour le PM

1. **Ce que fait le mode voyage** : tu choisis une date de fin et tu l'actives dans le Profil. Ta carte devient **grise** chez tes proches avec « En voyage jusqu'au 15 nov. » ; **le suivi des lieux s'arrête** (régions désarmées, plus de position, plus de batterie, plus d'écriture automatique de statut) ; **tu es ignoré dans la couleur de la Miff**, comme les neutres ; **plus aucun rappel** N1 à N4 ni N6 pour toi, et ton état ne déclenche plus N3 / N4 chez les autres ; **à la date de fin le suivi reprend tout seul** et tu reçois une notification locale **N7** « Ton mode voyage est terminé ».
2. **Réservé à Miff+, visible par tous** : l'interrupteur est affiché à tout le monde ; un compte gratuit qui l'active voit l'écran provisoire « Le mode voyage fait partie de Miff+ » (comme la zone de danger). **Barrière réelle côté règles Firestore** (`isPremiumOneRead`) : un client gratuit modifié qui écrirait le voyage est **refusé**.
3. **Modèle retenu [Proposé]** : une **nouvelle valeur `presence = "travel"`** dans le statut `miffs/{id}/status/{uid}` (un seul document, aucun nouveau chemin), avec **deux clés facultatives** `travelUntil` (instant de fin, sert à la logique) et `travelUntilDay` (date choisie, sert à l'affichage, sans piège de fuseau). Les **anciennes versions de l'app** lisent « `travel` » comme une valeur inconnue et affichent une carte **neutre** (déjà prévu par le code : « une valeur inconnue est neutre, jamais une supposition »).
4. **Coût** : **0 €**. À l'activation : 1 écriture par Miff où tu partages (+ 3 lectures de règles par écriture) ; pendant le voyage : **zéro** écriture automatique (c'est une économie par rapport au suivi normal, de l'ordre de 5 écritures par jour, hypothèse).
5. **Conflit à connaître (décision 8 du PM)** : la **photo d'update reste active** et, depuis la décision D27 de l'Update, **toute photo publiée est localisée** (position mesurée au moment de « Publier »). Pendant le voyage, publier une photo **écrit donc une position** (celle de la photo), geste volontaire. À dire honnêtement dans l'écran (§7) et à confirmer (Q2).
6. **Limite iOS à accepter (Q5)** : sans région armée, **iOS ne relance plus l'app** ; à la fin du voyage le suivi **reprend à la prochaine ouverture de l'app** (ou au toucher de N7), pas à la seconde près. Android reprend par une alarme.
7. **Vie privée** : tes proches savent que tu es en voyage et jusqu'à quand (c'est le but) ; une **confirmation à l'activation** le dit ; textes légaux à aligner (§11), sans modification ici.
8. **Tickets** : 1 backend (règles), puis Android 4, puis iOS 4 ; **L au total** (§13).

---

## 2. Périmètre et frontières

| Dans ce ticket | Hors de ce ticket (propriétaire) |
|---|---|
| Modèle de données et règles du statut « voyage » | Achat, essai, prix de Miff+ (ticket **Premium**) : on **réutilise** l'écran provisoire |
| Machine d'états (activation, pendant, fin, désactivation, changement de date, cas limites) | Calcul des niveaux et de `miffLevel` : on ajoute **une** valeur neutre dans `StatusColor` / `FeedBuilder` |
| Feed : carte grise, étiquette, bulle | Suivi des lieux (LOC-2b / 4b) : on y ajoute **un état « pause »** |
| Profil : interrupteur, date, textes, paywall provisoire | Push vers les proches (« Thomas part en voyage ») : **non**, aucun serveur |
| **N7** (notification locale de fin) et interactions avec NOTIF-P0 | Zones de danger, Update : inchangés sauf les gardes listées |

---

## 3. Décisions du PM (2026-11-10)

| # | Décision | Traduction dans ce ticket |
|---|---|---|
| 1 | Le **suivi des lieux** et les **écritures de statut, de position et de batterie** s'arrêtent (batterie, vie privée) | §5 : le moteur de suivi passe en pause ; régions désarmées ; aucune écriture automatique |
| 2 | Les proches voient une **carte grise** « En voyage jusqu'au <date> » (avec les dates) | §6 |
| 3 | Un membre en voyage est **ignoré** dans la couleur de la Miff (comme les neutres) | §6.4 : niveau **neutre**, `miffLevel` inchangé |
| 4 | **Plus aucun rappel N1 à N4 ni N6** pour lui ; ses états ne déclenchent plus N3 / N4 chez les autres | §9 |
| 5 | **Retour automatique** à la date de fin, avec une **notification locale « mode voyage terminé » (N7)** | §5, §9 |
| 6 | **Réservé au Premium mais visible par tous** ; un compte gratuit qui l'active voit le paywall | §4.3, §8 |
| 7 | **Désactivable à tout moment** avant la date de fin | §5 |
| 8 | **Photo d'update** et **« Localiser »** **non** désactivés pendant le voyage | §6.3, Q1, Q2 |

---

## 4. Modèle de données (pour `backend-data`)

### 4.1 Où et quoi [Proposé]

Le voyage est un **état du statut du membre dans chaque Miff** : `miffs/{miffId}/status/{uid}` (document déjà lu par le Feed en temps réel : aucun écouteur, aucun chemin, aucune lecture de plus pour les proches). **Alternative écartée** : un champ dédié à côté de `presence` (la dernière présence connue resterait lisible pendant le voyage : fuite de vie privée) ou un champ sur `users/{uid}` (non lisible par les proches sans nouvelle règle).

| Champ | Valeur en voyage | Valeur hors voyage |
|---|---|---|
| `presence` | `"travel"` (nouvelle valeur ; les autres : `in_trusted`, `out`, `in_risk`, `unknown`) | inchangé |
| `placeId` | `null` | inchangé |
| `presenceAt` | l'**instant d'activation** (« En voyage depuis… ») | inchangé |
| `lastUpdateAt` | conservé, ou l'instant d'une update manuelle faite pendant le voyage | inchangé |
| `batteryPct` | `null` (**rien n'est mesuré**) | inchangé |
| `escalationDueAt` | `null` | inchangé |
| `locationAccess` | conservé tel quel | inchangé |
| **`travelUntil`** *(nouveau, facultatif)* | **timestamp** : fin du voyage = **00:00 heure locale du lendemain du jour choisi** (donc fin de journée incluse) | absent ou `null` |
| **`travelUntilDay`** *(nouveau, facultatif)* | **chaîne `AAAA-MM-JJ`** : le jour choisi (**affichage seul**, sans fuseau) | absent ou `null` |

**Pourquoi deux champs** : l'instant `travelUntil` est absolu (logique, alarmes, expiration) ; le jour `travelUntilDay` évite qu'un proche dans un autre fuseau lise « jusqu'au 14 nov. » pour un voyageur qui a choisi le 15. Le jour est **cosmétique** (un client modifié ne peut tromper que l'affichage de sa propre carte).

**Anciennes apps** : `PresenceValue.fromWire("travel")` renvoie `null` → niveau **neutre** → carte grise sans étiquette de voyage (« Pas encore de nouvelles ») : **dégradation acceptable**, aucune erreur.

### 4.2 Évolutions de `backend/firestore.rules` (`validStatus`) [Proposé]

```
// 1. Clés : ajouter 'travelUntil', 'travelUntilDay' à hasOnly (PAS à hasAll : facultatives).
// 2. presence in ['in_trusted','out','in_risk','unknown','travel']
// 3. Cohérence (disjonction POSITIVE, jamais derrière « ! », comme l'exige le commentaire de isPremiumOneRead) :
function validTravel(d) {
  let hors = d.presence != 'travel'
    && d.get('travelUntil', null) == null
    && d.get('travelUntilDay', null) == null;
  let enVoyage = d.presence == 'travel'
    && d.placeId == null && d.batteryPct == null && d.escalationDueAt == null
    && d.travelUntil is timestamp
    && d.travelUntil > request.time
    && d.travelUntil <= request.time + duration.value(91, 'd')     // fenêtre : 90 jours + marge
    && d.travelUntilDay is string && d.travelUntilDay.matches('^[0-9]{4}-[0-9]{2}-[0-9]{2}$')
    && (
         // Barrière Miff+ : création ou changement de date => premium (UNE lecture de règles).
         isPremiumOneRead(request.auth.uid)
         // Mise à jour qui ne change pas le voyage (update manuelle pendant le voyage, premium expiré entre-temps) : sans lecture.
         || (resource != null && resource.data.get('presence', null) == 'travel'
             && resource.data.get('travelUntil', null) == d.travelUntil)
       );
  return hors || enVoyage;
}
// validStatus : ajouter « && validTravel(d) ».
```

**Conséquences voulues** :
- **Un compte gratuit** qui écrit `presence = "travel"` est **refusé** (pas de document `entitlements`, `get()` échoue : même mécanisme que la création d'un lieu de danger) ; **un premium expiré** ne peut ni activer ni changer la date, mais **peut désactiver** (écrire une autre présence ne demande aucun premium) et **publier une update** pendant un voyage déjà en cours (même `travelUntil`).
- **`travelUntil` ne peut pas être dans le passé** ni dépasser 91 jours (borne **de la règle** ; le client propose 90 jours).
- Le voyage ne **contient jamais** de lieu, de batterie ni d'échéance d'escalade.
- `statusInstant(presenceAt)` et `lastUpdateAt` : inchangés (la valeur conservée à l'identique reste acceptée).
- **Aucune règle ne lit le fuseau** : l'instant est calculé par le client.

**Budget de lectures de règles** : un statut de voyage coûte **3 appels** (`fromActiveDevice` 1 + `getAfter(members)` 1 + `isPremiumOneRead` 1) contre 2 pour un statut ordinaire. La limite est de **20 appels par lot** : **au plus 6 statuts de voyage par lot** (18 appels). Un compte Premium dans plus de 6 Miffs écrit donc **par lots de 6** (comme les fiches de membre par lots de 15, `data-model.md` §2.6). **`backend-data` mesure** (les lectures répétées d'un même document par plusieurs écritures du lot peuvent être comptées une seule fois : à confirmer par un test, auquel cas le lot passe à 15 comme pour les fiches).

**Tests de règles à écrire (`backend/rules-test-plan.md`)** : création / mise à jour de `travel` par un compte **gratuit** (refus) et **premium** (accepté) ; sans `travelUntil` ou sans `travelUntilDay` (refus) ; `travelUntil` passé, = maintenant, à 91 jours + 1 s (refus), à 90 jours (accepté) ; `travelUntilDay` mal formé (refus) ; `placeId`, `batteryPct` ou `escalationDueAt` renseignés en voyage (refus) ; clés de voyage avec une présence ordinaire (refus) ; **premium expiré** : activation (refus), update avec le **même** `travelUntil` (accepté), changement de date (refus), **désactivation** (accepté) ; un autre membre ne peut pas écrire mon voyage ; lot de **6** statuts de voyage (accepté) et de **7** (mesurer) ; clé `deviceId` présente et différente (refus, inchangé) ; document sans les clés de voyage (écritures actuelles inchangées).

**Déploiement** : règles **avant** les apps (sur le « oui » explicite du PM, comme les autres évolutions). Tant qu'elles ne sont pas déployées, une app qui écrit `travel` est refusée : elle l'affiche calmement (§7.4) et **ne désarme pas le suivi** (l'ordre de la machine d'états le garantit, §5.3).

### 4.3 Barrière Premium côté client

Le client lit `entitlements/{uid}` (comme `readEntitlement` des lieux) **uniquement pour choisir ce qu'il montre** (le vrai contrôle est la règle). Compte sans document ou expiré = gratuit.

### 4.4 Coût Firestore (hypothèses explicites, pas des mesures)

| Poste | Estimation |
|---|---|
| Activation, changement de date, désactivation | 1 écriture par Miff où je partage + 3 lectures de règles par écriture |
| Proches : réception du changement | 1 lecture par proche et par changement (écouteur existant) |
| **Pendant** le voyage | **0 écriture automatique** (le suivi normal en fait environ 5 par jour : hypothèse de `data-model.md`) |
| Voyage de 90 jours, 1 Miff | ≈ 3 écritures au total (activer, éventuellement changer la date, fin) contre ≈ 450 sans le mode |

Rien ne s'approche du quota gratuit (20 000 écritures et 50 000 lectures par jour).

---

## 5. Machine d'états

### 5.1 États (côté appareil du voyageur)

`NORMAL` → `TRAVEL_ACTIVE(until, untilDay)` → `NORMAL` (fin automatique, désactivation anticipée, ou déconnexion). L'état est **conservé localement** (`TrackingLocalStore`) **et** porté par les statuts (vérité côté proches). **Source de vérité au démarrage d'une session** : le statut `travel` de **mon propre document** (lu une fois, voir 5.6).

### 5.2 Gardes communes pendant `TRAVEL_ACTIVE`

Le **moteur de suivi** (`TrackingEngine`, Android ; `TrackingEngine` et `TrackingRuntime`, iOS) vérifie l'état **à chaque point d'entrée**, avant tout accès : `refresh(OPENED / BACKGROUNDED / MOVED / ZONE_ENTERED / REARM / SHARING_CHANGED / NETWORK_BACK)`, `onTransition`, `refreshBattery`, `onNetworkBack`. Si `TRAVEL_ACTIVE` et que `until` n'est pas passé : **retour immédiat**, sans lire la position, sans écrire de statut, de position ni de batterie, **sans armer de région**. Les gardes existantes (session, « Toujours », accord v3, suppression de compte) **restent**.

### 5.3 Activation (transition `NORMAL` → `TRAVEL_ACTIVE`)

Déclencheur : le Premium confirme (interrupteur + date + « Activer »). **Ordre imposé** (pour qu'un refus du serveur ne laisse jamais le suivi arrêté sans voyage) :
1. **Valider localement** : date ≥ aujourd'hui et ≤ aujourd'hui + 90 jours ; `until` = 00:00 local du lendemain ; `untilDay`.
2. **Écrire d'abord le voyage** dans `status/{moi}` de **chaque Miff où je partage** (par lots de 6, §4.2) : `presence = travel`, `placeId = null`, `presenceAt = maintenant`, `batteryPct = null`, `escalationDueAt = null`, `travelUntil`, `travelUntilDay` ; `deviceId` de la session. **Passe par la porte d'écriture du moteur** (vérification de la session, écritures en attente conservées sur le disque comme aujourd'hui).
3. Dès que l'écriture est **acceptée, ou conservée en attente hors connexion** : passer l'état local à `TRAVEL_ACTIVE`. **Si le serveur la refuse** (règles pas encore déployées, Premium expiré) : **rien n'a changé** (suivi intact), message calme (§7.4).
4. **Mettre le suivi en pause** : désarmer toutes les régions (`removeAll`), arrêter les mises à jour de position (« déplacement de 250 m » et « changements significatifs »), arrêter le réveil au retour du réseau, **effacer la copie locale de la dernière position** (elle sert à choisir les 20 lieux les plus proches : inutile et sensible).
5. **Annuler** les notifications programmées N1 à N4 et N6 (liste vide du planificateur, §9) et **programmer** N7 (§9) ; **programmer l'alarme de fin** (Android).
6. **Dernière position serveur** `positions/{moi}` : voir **Q1** (valeur par défaut : **supprimée** à l'activation, 1 suppression par Miff).

### 5.4 Pendant le voyage

- **Aucune écriture automatique** : ni présence, ni position, ni batterie (la batterie n'est **pas** rafraîchie à l'ouverture ni en quittant l'app).
- **Mise à jour manuelle (photo, texte)** : **autorisée** (décision 8). Elle écrit le statut **en conservant** `presence = travel`, `travelUntil`, `travelUntilDay`, `batteryPct = null`, et ne change que `lastUpdateAt`, `updateText`, `hasPhoto` (+ le document photo, localisé : Q2). Elle **ne met pas fin** au voyage et **ne ramène pas au vert** (le voyageur reste gris).
- **Nouvelle Miff pendant le voyage** (je crée ou je rejoins une Miff, ou je réactive mon partage dans une Miff) : le lot d'entrée crée le statut initial `unknown` ; **juste après**, le moteur **écrit le voyage** dans cette Miff (une écriture de plus), pour que ses membres me voient en voyage.
- **Quitter ou perdre une Miff, retrait, Miff supprimée** : comme aujourd'hui (le statut est supprimé) ; rien de plus.
- **Partage arrêté** dans une Miff : le statut y est supprimé (comportement actuel) ; le voyage s'applique aux autres Miffs.

### 5.5 Changement de date (voyage déjà actif)

Mêmes écritures qu'à l'activation **sans** pause (déjà en pause) : nouvelle valeur de `travelUntil` / `travelUntilDay` dans chaque Miff ; **reprogrammer** N7 et l'alarme de fin. Règles : Premium requis (§4.2). Une date **plus courte** est permise.

### 5.6 Fin du voyage (transition `TRAVEL_ACTIVE` → `NORMAL`)

**Trois causes** : (a) **fin automatique** à `until` ; (b) **désactivation anticipée** (interrupteur) ; (c) **déconnexion / perte de session** (voir 5.7).

**Fin automatique** :
- **Android** : une **alarme inexacte** (`setAndAllowWhileIdle`, `RTC_WAKEUP`) à `until`, reprogrammée au redémarrage, au changement d'heure ou de fuseau et à la mise à jour de l'app, déclenche `TrackingEngine.refresh(TRAVEL_ENDED)` dans le récepteur existant (`goAsync`, budget de 8,5 s). **Filets** : au redémarrage (`BOOT_COMPLETED`) et à chaque ouverture, le moteur compare `until` à l'heure : s'il est passé, il termine le voyage.
- **iOS** : **aucune région armée**, donc **aucune relance par le système** [À vérifier]. La fin est traitée **à la prochaine ouverture** de l'app, **au toucher de N7**, ou si une **tâche de fond opportuniste** (`BGAppRefreshTask` demandée pour `until`) est accordée par le système. **Limite à accepter (Q5).**
- **Opérations (dans cet ordre)** : (1) écrire dans chaque Miff `presence = unknown`, `placeId = null`, `presenceAt = maintenant`, **sans** `travelUntil` ni `travelUntilDay` (le statut **neutre** « inconnu » : « personne jamais passée en zone de confiance » = gris, jusqu'à la première mesure réelle) ; (2) **réarmer** le suivi (`refresh(REARM)` : régions, position récente pour reconstruire « où je suis », écriture d'une présence **si** elle est dans un lieu) ; (3) effacer l'état local ; (4) N7 : voir §9. L'écriture (1) ne demande **pas** le Premium (§4.2).
- **Première mesure** : le moteur applique ses règles habituelles (arrivée dans une zone : `in_trusted` ou `in_risk` ; hors zone : le statut reste `unknown` jusqu'au prochain événement, comportement existant de la reprise).

**Désactivation anticipée** : mêmes opérations que la fin automatique, **immédiatement** ; N7 **annulée** (la personne vient de le faire elle-même : une notification serait superflue) ; une entrée de centre silencieuse peut l'attester.

### 5.7 Autres cas

| Cas | Comportement |
|---|---|
| **App tuée** pendant le voyage | Rien ne tourne ; le statut reste `travel` côté serveur ; Android : l'alarme de fin survit à la fermeture (sauf arrêt forcé ou nettoyage constructeur : les filets de 5.6 reprennent) ; iOS : comme ci-dessus |
| **Redémarrage** | Android : reprogrammation de l'alarme de fin et de N7 depuis l'état local ; **aucune région n'est réarmée** tant que le voyage dure ; iOS : N7 conservée par le système [À vérifier] |
| **Changement d'heure ou de fuseau** (le voyageur change de pays) | `until` est un **instant absolu** : il ne bouge pas ; `untilDay` ne bouge pas (« jusqu'au 15 nov. » reste vrai) ; reprogrammer l'alarme et N7 (leur heure locale de 08:00 se recalcule) |
| **Déconnexion volontaire** | Le moteur est **arrêté** et **toutes** ses données locales effacées (`stop`) ; **le statut `travel` reste côté serveur** (comme tout statut à la déconnexion) : les proches voient le voyage jusqu'à `until`, puis le neutre. L'état local de voyage est **effacé** |
| **Suppression de compte** | Le statut est supprimé avec le compte (procédure existante) ; N7 et l'alarme annulées |
| **Appareil remplacé** (`session-appareil-unique.md`) | L'ancien appareil est déconnecté (état local effacé). Le **nouvel appareil**, **avant d'armer le suivi**, lit **mon propre statut** (1 lecture) : s'il est `travel` avec `until` futur, il **adopte** le voyage (état local, pas d'armement, N7 et alarme programmées) ; sinon il démarre normalement. **Sans cette lecture, le nouvel appareil écraserait le voyage** |
| **Premium expiré pendant le voyage** | **Le voyage continue jusqu'à sa fin** (aucune rétro-suppression) ; le changement de date est refusé (la désactivation reste possible) : **Q3** |
| **Si l'app du voyageur n'a pas repris après `until`** | Les **proches** calculent à la lecture : `maintenant ≥ travelUntil` → la carte reste grise mais **cesse de dire « En voyage »** : étiquette **« Voyage terminé »** (§6.2), sans couleur, jusqu'à la première mesure réelle |
| **Écriture refusée** (règles non déployées, Premium expiré, session remplacée) | À l'**activation** : rien n'a changé, message calme, interrupteur revient sur « désactivé ». À la **fin** : le moteur retente à chaque réveil ; le suivi reprend localement même si l'écriture attend |

---

## 6. Feed (ce que voient les proches, et le voyageur)

### 6.1 Calcul

- `PresenceValue` : ajouter `TRAVEL("travel")`. `MemberStatusFacts` : ajouter `travelUntilMillis: Long?` (+ `travelUntilDay: String?` pour l'affichage). **Mêmes ajouts sur Android et iOS** (les cas de test sont le contrat).
- `StatusColor.level` : `TRAVEL` → **`NEUTRAL`**, **quel que soit** l'instant (avant ou après `until`). Aucun seuil, aucun `changeInstants`.
- `FeedBuilder.miffLevel` : **inchangé** : un membre neutre est ignoré (décision 3). Un voyageur ne rend jamais la Miff orange ou rouge, et ne la rend pas verte non plus.

### 6.2 Carte du voyageur

| Élément | Règle |
|---|---|
| Anneau de l'avatar | **Gris** (neutre), jamais la couleur seule : l'étiquette porte le sens |
| Badge | **Icône d'avion** + **« En voyage jusqu'au 15 nov. »** tant que `now < travelUntil` ; **« Voyage terminé »** (gris) quand `now ≥ travelUntil` et que le statut est encore `travel` |
| Ligne de temps | « Depuis le 8 nov. » (date de `presenceAt` dans le fuseau du lecteur) |
| Batterie | **Masquée** (rien n'est mesuré) |
| « Localisation limitée » | **Masquée** |
| Photo d'update | **Affichée** si non expirée (comme aujourd'hui) |
| **Localiser** | **Inchangé** (décision 8) : feuille avec la date de la dernière position ou « pas de position » ; **si Q1 = supprimer la position**, la feuille dit calmement « Pas de position partagée en ce moment » |
| **Message / Appeler** | **Inchangés** |
| Date | Format court de la langue du lecteur (« 15 nov. », « Nov 15 ») ; **année ajoutée** si elle n'est pas l'année en cours |

### 6.3 Ma propre carte

« Mode voyage jusqu'au 15 nov. » + lien **« Modifier »** (ouvre la section du Profil). L'anneau de mon avatar en tête de Feed, le point du bouton central et la **bulle d'incitation** sont **neutres** : `UpdatePrompt.level` renvoie `NONE` (niveau neutre) : **aucune invitation à publier** pendant le voyage.

### 6.4 Bulle de la Miff et synthèse

- Les voyageurs sont **exclus du dénominateur** de « {n} sur {m} ont donné des nouvelles » (aujourd'hui `someSilent = known < cards.count` les compterait comme silencieux : **à corriger**).
- **Au moins un voyageur parmi les autres** : la bulle garde son message habituel et ajoute une ligne : « {Prénom} est en voyage jusqu'au 15 nov. » (un voyageur) ou « {n} proches sont en voyage. » (plusieurs).
- **Tous les autres sont en voyage** (ou neutres) : bulle **neutre** : « {Prénom} est en voyage jusqu'au 15 nov. » / « Tes proches sont en voyage. » (jamais « Tout va bien », jamais « Pas encore de nouvelles »).
- Aucune **page d'alerte** n'est créée pour un voyageur (il n'est ni orange ni rouge).

### 6.5 Rafraîchissement

Le minuteur local d'une minute du Feed **réévalue** `now ≥ travelUntil` (passage de « En voyage » à « Voyage terminé ») **sans lecture serveur**.

---

## 7. Profil : interrupteur, date, textes

### 7.1 Placement

Dans l'**onglet Profil**, **sous l'en-tête** et **avant** « Mes Miffs » : une **carte « Mode voyage »** (visible pour **tous** les comptes, [Décidé] décision 6). Pas de ligne dans Réglages.

### 7.2 Maquette texte (à fournir par le PM / design, §15)

```
┌────────────────────────────────────────┐
│ ✈  Mode voyage                  [ OFF ]│   « Miff+ » en pastille si compte gratuit
│    Mets ton suivi en pause pendant     │
│    un voyage.                          │
└────────────────────────────────────────┘

Activé (Premium) :
┌────────────────────────────────────────┐
│ ✈  Mode voyage                  [ ON  ]│
│    En voyage jusqu'au 15 nov.          │
│    Date de fin           15 nov. 2026 >│   → sélecteur de date natif
│    Pendant ton voyage, tes proches     │
│    voient « En voyage jusqu'au 15 nov. »│
│    et ta carte reste grise. Miff       │
│    arrête de suivre tes lieux et n'    │
│    envoie plus ta batterie. Tu peux    │
│    toujours donner des nouvelles avec  │
│    une photo (elle garde sa            │
│    localisation, comme d'habitude).    │
│    Le suivi reprend tout seul à la fin.│
└────────────────────────────────────────┘
```

### 7.3 Parcours

| Étape | Comportement |
|---|---|
| **Compte gratuit** touche l'interrupteur | **Rien n'est écrit** ; l'écran provisoire de Miff+ s'ouvre (réutilise le composant de la zone de danger) : « Le mode voyage fait partie de Miff+. » / "Travel mode is part of Miff+." ; l'interrupteur reste désactivé |
| **Premium** touche l'interrupteur (OFF → ON) | Le **sélecteur de date natif** s'ouvre (Android : sélecteur de date Material ; iOS : `DatePicker` en feuille) ; **date minimale : aujourd'hui** (le voyage se termine ce soir) ; **date maximale : aujourd'hui + 90 jours** ; défaut proposé : aujourd'hui + 7 jours |
| Après la date | Feuille de **confirmation** : « Activer le mode voyage ? » / « Tes proches verront « En voyage jusqu'au 15 nov. » à la place de ton état. Tu peux l'arrêter quand tu veux. » / [Activer] [Annuler] (deux boutons de poids égal) |
| « Activer » | Séquence §5.3 ; l'interrupteur passe à ON dès que l'écriture est **acceptée ou en attente** |
| Voyage actif : **changer la date** | Sélecteur de date ; mêmes bornes ; **pas** de confirmation (réversible) |
| Voyage actif : interrupteur ON → OFF | **Immédiat**, sans confirmation ; texte « Le suivi reprend. » ; N7 annulée |
| **Hors connexion** | L'activation est **acceptée localement** (le suivi se met en pause) ; message calme : « Tes proches verront ton voyage dès que tu seras en ligne. » |

### 7.4 Textes et clés (`travel_`, tutoiement, ton calme)

Préfixe **`travel_`** à ajouter au tableau de `docs/contracts/i18n-keys.md` et à `KNOWN_PREFIXES` par la PR (parité Android / iOS vérifiée par la CI).

| Clé | FR | EN |
|---|---|---|
| `travel_card_title` | Mode voyage | Travel mode |
| `travel_card_off_body` | Mets ton suivi en pause pendant un voyage. | Pause your tracking while you travel. |
| `travel_card_on_status` | En voyage jusqu'au {date} | Traveling until {date} |
| `travel_card_end_label` | Date de fin | End date |
| `travel_card_explain` | Pendant ton voyage, tes proches voient « En voyage jusqu'au {date} » et ta carte reste grise. Miff arrête de suivre tes lieux et n'envoie plus ta batterie. Tu peux toujours donner des nouvelles avec une photo : elle garde sa localisation, comme d'habitude. Le suivi reprend tout seul à la fin. | While you travel, your circle sees “Traveling until {date}” and your card stays grey. Miff stops tracking your places and no longer sends your battery level. You can still check in with a photo: it keeps its location, as usual. Tracking resumes on its own at the end. |
| `travel_confirm_title` | Activer le mode voyage ? | Turn on travel mode? |
| `travel_confirm_body` | Tes proches verront « En voyage jusqu'au {date} » à la place de ton état. Tu peux l'arrêter quand tu veux. | Your circle will see “Traveling until {date}” instead of your status. You can stop it whenever you like. |
| `travel_confirm_action` / `travel_confirm_cancel` | Activer / Annuler | Turn on / Cancel |
| `travel_off_done` | Le suivi reprend. | Tracking is resuming. |
| `travel_offline_note` | Tes proches verront ton voyage dès que tu seras en ligne. | Your circle will see your trip as soon as you're online. |
| `travel_error_refused` | Le mode voyage n'a pas pu être activé. Tu peux réessayer plus tard. | Travel mode couldn't be turned on. You can try again later. |
| `travel_paywall_title` | Le mode voyage fait partie de Miff+. | Travel mode is part of Miff+. |
| `travel_feed_label` | En voyage jusqu'au {date} | Traveling until {date} |
| `travel_feed_label_ended` | Voyage terminé | Trip ended |
| `travel_feed_since` | Depuis le {date} | Since {date} |
| `travel_feed_mine` / `travel_feed_mine_edit` | Mode voyage jusqu'au {date} / Modifier | Travel mode until {date} / Edit |
| `travel_bubble_one` | {prénom} est en voyage jusqu'au {date}. | {name} is traveling until {date}. |
| `travel_bubble_many` | {n} proches sont en voyage. | {n} loved ones are traveling. |
| `travel_bubble_all` | Tes proches sont en voyage. | Your loved ones are traveling. |
| `travel_n7_title` | Ton mode voyage est terminé | Your travel mode has ended |
| `travel_n7_body` (Android) | Miff reprend le suivi de tes lieux. Bon retour ! | Miff is picking up your places again. Welcome back! |
| `travel_n7_body_ios` | Ouvre Miff pour que le suivi reprenne. Bon retour ! | Open Miff so tracking can resume. Welcome back! |
| `travel_*_a11y` | Libellés lecteur d'écran (« Mode voyage, interrupteur, activé, jusqu'au 15 novembre », avion = décoratif, l'état est dit par le texte) | Screen-reader labels |

**Accessibilité** : l'interrupteur et sa ligne forment **un seul élément** annoncé « Mode voyage, interrupteur, activé, jusqu'au 15 novembre » ; cibles 48 dp / 44 pt ; le sélecteur de date est **natif** (donc accessible) ; l'état n'est **jamais** porté par la couleur seule (icône d'avion + texte) ; pas d'animation si « réduire les animations ».

---

## 8. Premium (ligne du catalogue)

- **Miff+ gagne une ligne** : « Mode voyage : mets ton suivi en pause et grise ta carte pendant un voyage ». À ajouter à `docs/specs/paywall-premium.md` (liste des règles Premium : Miffs et lieux illimités, zones de danger, notifications intelligentes) et à la **maquette du paywall** (liste des bénéfices), par le ticket Premium / `design-system`.
- **Visible par tous, activable par les seuls Premium** [Décidé] : la **barrière réelle est dans les règles** (§4.2) ; l'interface gratuite ne fait que **ne pas écrire** et ouvrir l'écran provisoire.
- **Événement d'analyse** : aucun nouvel événement de voyage. **Option** : réutiliser `paywall_shown` avec un **nouveau déclencheur** `travel_mode` (le contrat `analytics-events.md` ne liste que `extra_miff` et `extra_trusted_place`) : **modification du contrat par une PR dédiée**, avec le PM et sous consentement Analytics ; **hors périmètre de ce ticket** par défaut.
- **Premium accordé à la main** (aucun achat branché) : le mode voyage fonctionne tel quel pour les testeurs dont `entitlements/{uid}` est actif.

---

## 9. Notifications : N7 et interactions avec NOTIF-P0

### 9.1 N7 « Mode voyage terminé » (nouvelle, sans serveur, 0 €)

| Élément | Règle |
|---|---|
| Nature | **Notification locale**, programmée à l'activation et à chaque changement de date |
| **Heure** | **08:00 heure locale du lendemain du jour de fin** (= `travelUntil + 8 h`) [Proposé] : jamais en pleine nuit ; la fin **technique** (suivi repris, carte « Voyage terminé ») a lieu à `travelUntil` |
| Titre / description | « Ton mode voyage est terminé » / « Miff reprend le suivi de tes lieux. Bon retour ! » (iOS : « Ouvre Miff pour que le suivi reprenne. Bon retour ! ») |
| Au toucher | **Le Feed** (ma carte revient à la normale) [Proposé] ; sur iOS le toucher **ouvre l'app et déclenche la reprise du suivi** |
| Annulation | **Désactivation anticipée** ; déconnexion ; suppression de compte ; session prise par un autre appareil ; interrupteur N7 coupé ; autorisation retirée ; **fin déjà traitée app ouverte** (alors : simple entrée de centre silencieuse) |
| Reprogrammation | Changement de date ; **redémarrage** (Android : `BOOT_COMPLETED`) ; changement d'heure ou de fuseau ; mise à jour de l'app ; changement de langue (iOS) |
| Clé de déduplication | `n7|{moi}|{travelUntil}` (jamais deux fois) |
| Plafond, nuit | **Hors plafonds** de NOTIF-P0 ; à 08:00 la nuit calme (22 h – 7 h) n'intervient pas ; l'importance est **normale** |
| Premier plan | Pas de notification système ; entrée de centre silencieuse |
| Centre | Entrée de type N7 ; toucher = Feed |
| Réglage | **Interrupteur « Quand mon mode voyage se termine »** dans Réglages > Notifications (**actif par défaut**) |
| Permission refusée | Rien n'est affiché ; la fin du voyage **n'en dépend pas** |
| Clés | `travel_n7_title`, `travel_n7_body`, `travel_n7_body_ios`, `notif_settings_n7` = « Quand mon mode voyage se termine » / "When my travel mode ends" |

### 9.2 Gardes à ajouter au planificateur de NOTIF-P0 (`plan`, §20.3 de `notifications.md`)

1. **Moi en voyage** (état local `TRAVEL_ACTIVE` **ou** mon statut `travel`) : `plan` renvoie **uniquement N7** (si autorisé) ; **N1, N2, N3, N4 et N6 sont vides** (décision 4 du PM : plus aucun rappel pour lui, N3 / N4 compris : un voyageur ne reçoit plus de « Prends des nouvelles » pendant sa pause).
2. **Un autre membre en voyage** : son niveau est **neutre** ; `StatusColor.changeInstants` renvoie `[]` pour `presence = travel` ; `miff(t)` l'ignore ; ses états **ne créent** donc **aucun** épisode orange ou rouge et **n'entrent pas** dans la règle « je suis responsable ». Un N3 / N4 déjà programmé qui reposait sur lui est **replanifié** à la réception de son statut `travel` (annulé s'il n'y a plus de cause) ; **sur iOS** une demande en attente peut encore s'afficher (fausse alerte possible, **limite déjà acceptée** pour N3 / N4).
3. **N6** : le suivi étant en pause, **aucun événement de zone ne peut se produire** ; garde en plus dans le crochet de N6 (état `TRAVEL_ACTIVE` ⇒ aucune notification).
4. **Début de voyage** : annulation **immédiate** de toutes les demandes en attente N1 à N4 et N6 (dans la même séquence que 5.3).
5. **Vecteurs de test à ajouter** (T16 à T20) : moi en voyage (liste = N7 seul) ; X en voyage et Y vert (Miff neutre ou verte, pas de N3 / N4) ; X orange puis X passe en voyage (N3 annulée) ; fin de voyage (reprise du plan normal) ; X en voyage dont `until` est passé (toujours neutre).

---

## 10. Interactions avec le suivi, la couleur et les autres tickets

| Sujet | Effet |
|---|---|
| **Suivi (LOC-2b / 4b)** | Un **état de pause** dans `TrackingEngine` (Android) et `TrackingEngine` / `TrackingRuntime` (iOS) ; nouvelles raisons `TRAVEL_STARTED`, `TRAVEL_ENDED` ; `PresenceMachine` **inchangé** (pur) |
| **Permissions** | « Toujours » et la localisation précise **restent accordées** ; rien n'est demandé de plus ; aucune permission supprimée |
| **Update** | Inchangé : publier une update reste possible (la photo reste localisée, Q2) ; `UpdatePrompt` neutre ; l'update **ne met pas fin** au voyage |
| **Couleur de Miff** | `miffLevel` inchangé (le neutre est ignoré) |
| **Zone de danger** | En voyage, **plus d'événements de zone** : aucune entrée en zone de danger n'est détectée (ni `in_risk`, ni N6) |
| **Session unique** | §5.7 (appareil remplacé) |
| **Cache et purge** | L'état local de voyage est purgé avec le cache à la déconnexion (contrat DUR-3) |
| **Premium (ticket futur)** | Une fin de Premium **ne coupe pas** un voyage en cours (Q3) |

---

## 11. Vie privée, consentement, textes légaux

- **Ce que les proches apprennent** : « cette personne est en voyage jusqu'au {date} » : c'est **voulu et dit à l'activation** (feuille de confirmation §7.3). Ils **n'apprennent plus** : lieu, présence, batterie, position récente.
- **Ce qui s'arrête** : tout ce que le suivi écrit (§5.2), la copie locale de la position, et (par défaut) la dernière position serveur (Q1).
- **Ce qui continue (volontaire, geste manuel)** : photo d'update et sa localisation (Q2), message, appel, « Localiser » (si une position existe).
- **Textes légaux à aligner par les agents concernés, sans les modifier ici** (et **sans prétendre à une relecture juridique**, décision du 2026-10-23) : politique de confidentialité (liste de ce qui est partagé : ajouter « un état de voyage et sa date de fin » ; durée : supprimé avec le statut) ; CGU (Premium : ajouter le mode voyage à la liste des options) ; **texte de consentement d'entrée dans une Miff** : **Q4** (bump de version ou confirmation en contexte).
- **Stores** : aucune nouvelle collecte ; Play (Sécurité des données) et App Store (confidentialité) : **pas de changement** (l'état de voyage est un statut déjà partagé) ; à reconfirmer par `release-manager`.
- **Mineurs** : aucun changement (risque assumé, `politique-confidentialite` §10).
- **Risque d'usage** (R5) : le mode voyage permet de **disparaître de la couleur** de la Miff ; le PM l'a voulu ; atténuations : date visible, 90 jours au plus, retour automatique, message toujours possible.

---

## 12. Différences Android / iOS

| Sujet | Android | iOS |
|---|---|---|
| Pause du suivi | `GeofencingClient.removeGeofences`, arrêt des mises à jour de position, arrêt du réveil réseau, effacement de la copie locale de position | `stopMonitoring` de toutes les régions, arrêt des changements significatifs, idem |
| Fin automatique | **Alarme inexacte** `RTC_WAKEUP` à `until` → `TrackingReceiver` ; filets au redémarrage et à l'ouverture | **Pas de relance système** ; reprise à l'ouverture, au toucher de N7, ou par `BGAppRefreshTask` opportuniste [À vérifier] |
| N7 | `AlarmManager` inexacte, reprogrammée au redémarrage | Notification locale par intervalle, 1 demande (très loin de la limite de 64) |
| Sélecteur de date | Sélecteur de date Material | `DatePicker` natif |
| Paywall | Composant provisoire des lieux, généralisé | Idem |
| Tests | OPPO Find X5 Lite en local | iPhone 11 Pro par TestFlight |

---

## 13. Découpage en tickets (backend, puis Android, puis iOS)

Une branche et une PR par plateforme et par ticket ; maquettes avant développement (règle du 2026-10-28) ; **les développeurs peuvent commencer sans attendre les maquettes** en suivant les wireframes de ce document et en listant leurs écarts dans la PR.

| # | Ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| **VOY-0** | Décisions et maquettes | Réponses Q1 à Q6 ; maquettes §15 | S | Cette spec |
| **VOY-B** | **Backend** (`backend-data`) | §4 : règles `validStatus`, tests de règles, `data-model.md` (§2.7, §3, §6, §12), `rules-test-plan.md`, mesure du lot de 6 ; **déploiement sur « oui » explicite du PM** | S à M | VOY-0 |
| **VOY-1A** | **Android : modèle et moteur** | `PresenceValue.TRAVEL`, `MemberStatusFacts.travelUntil*`, `StatusColor` (neutre), état de pause du moteur, séquences §5.3 à §5.7 (activation, fin, désactivation, changement de date, appareil remplacé, nouvelle Miff), alarme de fin, gardes ; tests unitaires de toutes les branches | **L** | VOY-B (déployé pour tester sur serveur) |
| **VOY-2A** | **Android : Profil** | Carte « Mode voyage », sélecteur de date, confirmation, états gratuit / premium / hors connexion / refusé, paywall provisoire | M | VOY-1A |
| **VOY-3A** | **Android : Feed** | Carte grise, étiquettes, bulle (exclusion du dénominateur, lignes voyage), ma carte, feuille Localiser inchangée | M | VOY-1A |
| **VOY-4A** | **Android : N7 + gardes NOTIF-P0** | N7 (programmation, annulation, centre, interrupteur), gardes du planificateur (§9.2), vecteurs T16 à T20 | S à M | VOY-1A ; **NOTIF-3A / 4A** (si le planificateur n'est pas livré, N7 est livrée seule et les gardes sont ajoutées avec NOTIF-3A) |
| **VOY-1I à 4I** | **iOS** : mêmes livrables ; `TrackingRuntime` en pause ; fin à l'ouverture / N7 / tâche de fond ; TestFlight | idem | idem | Équivalents Android ; INIT-4 |

**Ordre conseillé** : VOY-0 → VOY-B → VOY-1A → (VOY-2A, VOY-3A en parallèle) → VOY-4A → iOS.

---

## 14. Critères d'acceptation vérifiables

**Règles et données (VOY-B)**
- [ ] **VB1** Un compte gratuit ne peut ni créer ni modifier un statut `travel` (test de règles) ; un premium le peut ; un premium expiré peut **désactiver** et **publier une update** sans changer `travelUntil`.
- [ ] **VB2** `travelUntil` : futur, ≤ 91 jours ; `travelUntilDay` bien formé ; aucune autre clé nouvelle ; aucun lieu, aucune batterie, aucune échéance en voyage.
- [ ] **VB3** Les écritures de statut actuelles (sans clés de voyage) passent inchangées.
- [ ] **VB4** Le nombre d'appels de règles d'un lot de 6 statuts de voyage est mesuré et reporté (≤ 20).

**Moteur et machine d'états (VOY-1)**
- [ ] **V1** À l'activation, **l'écriture du voyage précède la pause** ; si elle est refusée, **le suivi n'a pas été arrêté**.
- [ ] **V2** Pendant le voyage : **aucune** position lue, **aucune** écriture automatique de présence, de position ou de batterie, **aucune région armée** (vérifié par la liste des régions du système et par un compteur d'écritures dans les tests) ; la batterie n'est pas rafraîchie à l'ouverture.
- [ ] **V3** Une update manuelle pendant le voyage **conserve** `presence = travel`, `travelUntil`, `travelUntilDay` et met `batteryPct` à `null`.
- [ ] **V4** Fin automatique : Android reprend **sans ouvrir l'app** (alarme) ; iOS reprend **à l'ouverture** ; dans les deux cas le statut passe à `unknown` **avant** la première mesure et le suivi est réarmé.
- [ ] **V5** Désactivation anticipée : reprise **immédiate** ; N7 annulée.
- [ ] **V6** Changement de date : `travelUntil` et `travelUntilDay` changent dans **chaque** Miff ; N7 et l'alarme sont reprogrammées.
- [ ] **V7** Appareil remplacé : le nouvel appareil **adopte** un voyage en cours (lecture de mon statut avant d'armer) et ne l'écrase pas.
- [ ] **V8** Nouvelle Miff pendant le voyage : le voyage y est écrit juste après le statut initial.
- [ ] **V9** Redémarrage, changement d'heure et de fuseau : alarme et N7 reprogrammées, aucune région armée tant que le voyage dure.
- [ ] **V10** Déconnexion : l'état local est effacé ; le statut serveur reste jusqu'à `travelUntil`.
- [ ] **V11** Compte sans Miff où je partage : aucune écriture ; l'état local suffit (voyage « sans carte »).

**Feed (VOY-3)**
- [ ] **F1** La carte d'un voyageur est **grise**, avec l'icône d'avion et « En voyage jusqu'au {date} » (jour = `travelUntilDay`), sans batterie ni « Localisation limitée » ; photo, Localiser, Message et Appeler **inchangés**.
- [ ] **F2** `miffLevel` ignore le voyageur (test : Miff {voyageur, vert} = verte ; {voyageur seul} = neutre).
- [ ] **F3** « n sur m » exclut les voyageurs du dénominateur ; la ligne « est en voyage » apparaît ; « Tout va bien » n'apparaît jamais **à cause** d'un voyageur seul.
- [ ] **F4** `now ≥ travelUntil` : « Voyage terminé » sans relecture serveur.
- [ ] **F5** Ma carte : « Mode voyage jusqu'au {date} » + « Modifier » ; anneau, point du bouton central et bulle d'incitation **neutres** ; aucune invitation à publier.
- [ ] **F6** Une app **sans** cette version affiche une carte neutre sans erreur (valeur inconnue).

**Profil et Premium (VOY-2)**
- [ ] **P1** La carte « Mode voyage » est visible pour **tous** ; un compte **gratuit** qui touche l'interrupteur voit l'écran provisoire de Miff+ et **rien n'est écrit**.
- [ ] **P2** Sélecteur natif : date minimale = aujourd'hui, maximale = aujourd'hui + 90 jours ; confirmation avant activation ; texte exact de §7.4.
- [ ] **P3** Hors connexion : activation acceptée localement + message calme ; refus du serveur : message calme et retour à « désactivé ».
- [ ] **P4** Textes FR et EN, clés `travel_` identiques sur les deux plateformes (parité CI), tutoiement ; accessibilité de §7.4.

**Notifications (VOY-4)**
- [ ] **N7-1** N7 est programmée à `travelUntil + 8 h` locale, annulée à la désactivation, déconnexion, suppression de compte et session perdue, reprogrammée au redémarrage et au changement de date ; jamais deux fois.
- [ ] **N7-2** En voyage, **aucune** N1, N2, N3, N4 ni N6 n'est programmée ni affichée (vecteurs T16 à T20).
- [ ] **N7-3** N7 est dans le centre de notifications ; toucher = Feed (iOS : reprise du suivi).

---

## 15. Maquettes à demander au PM

1. **Profil : carte « Mode voyage »** : désactivé (gratuit avec pastille Miff+, premium), activé, chargement, hors connexion, erreur.
2. **Sélecteur de date** (natif, aucune maquette sur-mesure nécessaire : un export de la feuille suffit) et **feuille de confirmation**.
3. **Écran provisoire Miff+ pour le mode voyage** (variante du texte des lieux).
4. **Feed : carte grise « En voyage jusqu'au 15 nov. »** (autre membre) et **ma carte** ; carte « Voyage terminé ».
5. **Bulle de la Miff** : un voyageur parmi d'autres, plusieurs, tous.
6. **Notification N7** (écran verrouillé, Android et iOS).
7. **Ligne Premium** ajoutée à la liste des bénéfices du paywall.

---

## 16. Plan de recette PM

| # | Scénario | Appareil | Attendu |
|---|---|---|---|
| 1 | Compte **gratuit** : toucher l'interrupteur | OPPO ; iPhone | Écran « fait partie de Miff+ » ; rien ne change chez un proche |
| 2 | Compte **Miff+** (accordé à la main) : activer jusqu'à demain | OPPO ; iPhone | Ma carte grise ; sur le téléphone d'un proche : carte grise « En voyage jusqu'au … », batterie absente |
| 3 | Pendant le voyage : sortir de mes lieux, marcher, ouvrir / fermer l'app | OPPO ; iPhone | **Aucun** changement de statut chez le proche ; aucune notification N1 à N4 ni N6 |
| 4 | Publier une photo pendant le voyage | OPPO ; iPhone | La photo s'affiche chez le proche ; le voyageur reste gris |
| 5 | Changer la date ; puis désactiver avant la fin | OPPO ; iPhone | La date change chez le proche ; la désactivation reprend le suivi tout de suite ; pas de N7 |
| 6 | **Fin automatique** (build interne : fin dans quelques minutes) | OPPO ; iPhone | OPPO : reprise sans ouvrir l'app ; iPhone : reprise à l'ouverture ; N7 reçue ; carte neutre puis couleur après la première mesure |
| 7 | Un proche orange pendant que je regarde puis il part en voyage | OPPO | La couleur de la Miff l'ignore ; pas de N3 / N4 |
| 8 | Redémarrage pendant le voyage ; changement de fuseau | OPPO | Rien ne se réarme ; N7 reprogrammée |
| 9 | Changer de téléphone (ou se reconnecter ailleurs) pendant le voyage | — | Le nouvel appareil conserve le voyage |
| 10 | Compte avec plusieurs Miffs | OPPO | Voyage visible dans toutes ; nouvelle Miff rejointe pendant le voyage : le voyage y apparaît |

*Un build interne peut raccourcir la fin du voyage à quelques minutes ; jamais en production.*

---

## 17. Risques

| # | Risque | Effet | Mitigation |
|---|---|---|---|
| **R1** | **iOS ne reprend pas tout seul** : aucune région armée, aucune relance par le système | Le voyageur reste « inconnu » (neutre) tant qu'il n'ouvre pas l'app | N7 à 08:00 qui demande d'ouvrir l'app ; tâche de fond opportuniste ; **limite à accepter (Q5)** ; la carte dit « Voyage terminé » (pas de faux « en voyage ») |
| **R2** | **Oubli de désactiver / date trop longue** | Un proche perd de vue la personne | 90 jours au plus, retour automatique, date toujours visible |
| **R3** | **Masquer un danger réel** : un état gris à la place d'un rouge | Réassurance trompeuse | Voulu par le PM ; texte « En voyage » explicite ; Message / Appeler et photo inchangés ; **la fin automatique** |
| **R4** | **Photo localisée en voyage** | Une position est écrite malgré la pause | Texte honnête (§7.4), Q2 |
| **R5** | **Usage de contrôle ou de dissimulation** (se « cacher » d'un cercle) | Tension entre proches | Fonctionnalité **à soi seul** ; le voyage se voit ; un proche ne peut pas l'imposer à un autre |
| **R6** | **Fonction de vie privée réservée au payant** | Perception de pression vers Miff+ | Décision du PM (6) ; textes sans culpabilisation ; aucune notification d'incitation |
| **R7** | **Règles non déployées avant les apps** | Activation refusée | Ordre de §5.3 (écrire d'abord) ; message calme ; déploiement avant les apps sur « oui » du PM |
| **R8** | **Lot de 6 statuts de voyage** (budget de règles) | Plus de 6 Miffs en une écriture refusée | Lots de 6 ; mesure `backend-data` |
| **R9** | **Nouvel appareil qui écrase un voyage** | Voyage perdu | Lecture de mon statut avant d'armer (§5.7) |
| **R10** | **Alarme de fin tuée** (ColorOS) | Reprise tardive | Filets au redémarrage et à l'ouverture ; N7 |
| **R11** | **Textes non relus par un juriste** | Risque assumé par le PM (décision du 2026-10-23) | Aucune prétention de relecture |

---

## 18. Questions pour le PM (les seules qui restent)

Chaque question donne une **valeur par défaut** (recommandation) : le développement peut commencer avec elle.

**Q1. Dernière position serveur** (`positions/{moi}`) : la **supprime-t-on à l'activation** ? Le PM a voulu que « Localiser » reste actif et que le suivi s'arrête « pour la vie privée » : sans suppression, la dernière position connue **reste sur le serveur** pendant tout le voyage (masquée par l'interface après 24 h, mais présente).
- *Reco* : **supprimer à l'activation** (1 suppression par Miff, aucune écriture de position) : « Localiser » répond alors « Pas de position partagée en ce moment » ; le bouton **n'est pas désactivé** (décision 8 respectée). *Impact* : vie privée ; feuille Localiser ; une suppression de plus.

**Q2. Photo localisée pendant le voyage.** Depuis la décision D27 de l'Update, **toute photo publiée écrit la position mesurée au moment de « Publier »**. Pendant le voyage, publier une photo **écrit donc une position** (geste volontaire).
- *Reco* : **oui, on garde** (décision 8) et on le **dit** dans l'écran (déjà dans le texte de §7.4). *Alternative* : une option « photo sans localisation » en voyage (**exige une évolution de règles et de l'Update**, hors périmètre).

**Q3. Premium qui expire pendant un voyage.**
- *Reco* : **le voyage continue jusqu'à sa date de fin** (pas de rétro-suppression) ; on ne peut plus changer la date, mais on peut désactiver. *Impact* : règle `validTravel` déjà écrite ainsi.

**Q4. Consentement.** Le texte de consentement d'entrée dans une Miff liste ce que le cercle voit. Faut-il **changer sa version** (`consentVersion`) pour y ajouter « ton mode voyage et sa date de fin » (ce qui **redemande l'accord à tous les comptes existants**) ou se contenter de la **confirmation à l'activation** + mise à jour de la politique ?
- *Reco* : **confirmation en contexte + politique** (pas de nouvelle version : le voyage est un état que la personne choisit et voit). *Impact* : `consentVersion`, parcours des comptes existants.

**Q5. Reprise sur iOS.** Accepter qu'**à la fin du voyage le suivi ne reprenne qu'à la prochaine ouverture de l'app** (ou au toucher de N7, ou par une tâche de fond accordée par le système), faute de région armée pour réveiller l'app ?
- *Reco* : **accepter** : la carte dit « Voyage terminé » (jamais un faux « en voyage ») et N7 invite à ouvrir l'app. *Alternative* : garder actifs les « changements significatifs » pendant le voyage (contredit la décision 1 : aucun suivi).

**Q6. Valeurs par défaut à confirmer** (une seule réponse « oui » suffit) : **90 jours** au plus ; fin = **00:00 du lendemain du jour choisi** (donc fin de journée incluse) ; **date minimale = aujourd'hui** ; **N7 à 08:00** le lendemain ; le voyage s'applique à **toutes** les Miffs où je partage (pas de choix par Miff) ; toucher de N7 = **Feed**.
- *Reco* : oui à tout. *Impact* : constantes et textes.

---

## 19. Écarts et contradictions relevés

| # | Écart | Traitement |
|---|---|---|
| **E1** | **Décision 1 (aucune écriture de position) et décision 8 (photo active) se heurtent** : la photo est obligatoirement localisée (D27 de l'Update) | Q2 ; texte honnête |
| **E2** | **Décision 8 (« Localiser » actif) et vie privée** : la dernière position serveur resterait en place | Q1 |
| **E3** | **`FeedBuilder`** : `someSilent = known < cards.count` compterait les voyageurs comme silencieux | Corrigé par VOY-3 (§6.4) |
| **E4** | **`validStatus`** : `presence` n'accepte que 4 valeurs ; `hasOnly` ne connaît pas les clés de voyage ; budget de règles passe de 2 à 3 appels par statut de voyage | VOY-B (§4.2) |
| **E5** | **Analytics** : `paywall_shown` ne connaît que `extra_miff` et `extra_trusted_place` | Hors périmètre, PR de contrat dédiée (§8) |
| **E6** | **Catalogue Premium** (`paywall-premium.md`, maquette du paywall) : la liste ne cite pas le mode voyage | Ligne à ajouter (§8) |
| **E7** | **NOTIF-P0** (`notifications.md` §20) : N1 à N4 et N6 ne connaissent pas le voyage ; N7 est nouvelle | §9 ; ce ticket demande les gardes et N7 ; à répercuter dans NOTIF-P0 |
| **E8** | **Textes légaux** (politique, CGU) : ne citent pas le mode voyage | Alignement par les agents concernés (§11), sans modification ici |

---

## 20. Ce que le PM doit faire (rien n'est fait par un agent)

1. **Répondre** à Q1 à Q6 (une réponse « oui aux recommandations » suffit).
2. **Fournir** les maquettes de §15 (les développeurs peuvent démarrer sans).
3. **Donner le « oui » explicite** pour **déployer** les règles de VOY-B sur `miff-3d283` (avant les apps).
4. **Faire accorder Miff+ à la main** aux testeurs (document `entitlements/{uid}`) pour la recette.

*Ce document ne modifie ni le code, ni Notion, ni Figma, ni les règles Firestore, ni les textes légaux. Aucun texte n'est présenté comme relu par un juriste.*
