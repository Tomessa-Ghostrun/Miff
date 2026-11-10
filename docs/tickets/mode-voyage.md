# Ticket : MODE VOYAGE (carte grise « En voyage », position toujours partagée, disponible pour tous)

**Statut** : spécification **réécrite le 2026-11-10** pour intégrer la décision « position partagée » ; **prête pour validation par le PM** (questions résiduelles en §18, chacune avec une valeur par défaut qui permet de démarrer). Aucun statut Notion modifié, aucune règle modifiée ni déployée par ce document.
**Rédigé le** : 2026-11-10 (agent `product-specs`).

**Décision du PM du 2026-11-10 (REMPLACE l'ancien principe « Miff arrête de suivre »)** : pendant un voyage, **la position reste partagée comme d'habitude** (« Localiser », photos localisées, le voyageur lit aussi les positions des autres). Ce qui change : la **carte du voyageur au Feed est GRISE**, avec un **avion** et **« En voyage jusqu'au <date> »**, et le voyageur n'a **AUCUN impact** sur la couleur ni le statut de la Miff (il est ignoré aussi dans le « n sur m »).
**Décision du PM du 2026-11-10 (déjà actée, maintenue)** : mode voyage **ouvert à tous** (gratuits comme Miff+), aucune barrière Premium.

**Sources lues** : `CLAUDE.md` ; `backend/firestore.rules` (`validStatus`, `validTravel`, règles `positions`, `photos`, **sur `main`, non modifiées par ce ticket**) ; `backend/data-model.md` (§2.7, bloc « mode voyage ») ; `docs/dev/mode-voyage-clients.md` ; `docs/tickets/feed.md`, `update.md`, `lieux-de-confiance.md`, `session-appareil-unique.md`, `docs/tickets/notifications.md` §20.

**Limites de cette lecture** : pas d'outil shell (aucun test, aucune mesure). Les faits iOS (réveil de l'app, tâche de fond) sont de connaissance générale et marqués [À vérifier]. Je n'ai modifié ni le code (`android/`, `ios/`), ni Notion, ni les règles Firestore, ni les textes légaux.

**Convention** : **[Décidé]** = décision du PM. **[Proposé]** = ma proposition avec valeur par défaut, à valider. **[À vérifier]** = fait non confirmé. **CADUC** = contenu d'une version précédente de ce ticket, **supprimé ou remplacé** (liste complète en §21).

---

## 1. Résumé pour le PM

1. **Ce que fait le mode voyage** : tu choisis une date de fin et tu l'actives dans le Profil. Ta carte devient **grise** chez tes proches, avec un avion et « En voyage jusqu'au 15 nov. » ; **tu es ignoré dans la couleur de la Miff** et dans le « n sur m ont donné des nouvelles » ; **à la date de fin** ta carte reprend ses couleurs toute seule.
2. **Ce que le mode voyage NE fait PAS (important, à dire honnêtement)** : il **n'arrête pas le partage de ta position**. Tes proches peuvent toujours te **localiser** (« Localiser »), tes **photos gardent leur localisation**, et toi tu lis les positions et photos des autres comme d'habitude. Les textes de l'app ne doivent **jamais** laisser croire le contraire (§11).
3. **Ce qui s'arrête vraiment** : ton **état de présence** (vert / orange / rouge, lieu de confiance) n'est plus écrit, et **ta batterie n'est plus affichée** (le statut de voyage n'a ni lieu, ni batterie, ni échéance).
4. **Pour tout le monde** : gratuits comme Miff+, aucune barrière, aucun écran Miff+. **Maximum proposé par le sélecteur : aujourd'hui + 89 jours** (soit 90 jours de voyage au plus, la fin étant à 00:00 le lendemain du jour choisi) ; les règles serveur acceptent jusqu'à 91 jours.
5. **Modèle (déjà dans les règles de `main`, non modifiées)** : `presence = "travel"` dans `miffs/{id}/status/{uid}` avec `travelUntil` (instant de fin) et `travelUntilDay` (jour choisi, affichage). Les anciennes versions de l'app affichent une carte neutre.
6. **Coût** : 0 €. À l'activation, 1 écriture de statut par Miff où tu partages ; pendant le voyage, **plus aucune écriture automatique de statut** ; **les écritures de position continuent comme d'habitude** (pas d'économie de ce côté-là).
7. **Ce qui devient sans objet** : l'ancienne Q1 (supprimer la dernière position à l'activation) et l'ancienne Q2 (photo localisée en voyage : c'est désormais le comportement normal, plus un conflit).
8. **Tickets** : 1 backend (règles déjà sur `main`, documentation et tests à aligner), puis Android, puis iOS ; **L au total** (§13).

---

## 2. Périmètre et frontières

| Dans ce ticket | Hors de ce ticket (propriétaire) |
|---|---|
| Statut « voyage » et ses règles (déjà sur `main`) | Achat, essai, prix de Miff+ (ticket **Premium**) : **aucun lien** avec le mode voyage |
| Comportement du voyageur : activation, pendant, fin, désactivation, changement de date, cas limites | Calcul des niveaux et de `miffLevel` : on ajoute **une** valeur neutre dans `StatusColor` / `FeedBuilder` |
| Feed : carte grise, étiquette, bulle, « n sur m » | Suivi des lieux et de la position (LOC-2b / 4b) : **inchangé**, seule l'écriture du statut de présence est neutralisée pendant le voyage |
| Profil : interrupteur, date, textes honnêtes | Push vers les proches (« Thomas part en voyage ») : **non**, aucun serveur |
| **N7** (notification locale de fin) et gardes NOTIF-P0 | Zones de danger, Update : inchangés sauf les gardes listées |

---

## 3. Décisions du PM

| # | Décision | Statut | Traduction dans ce ticket |
|---|---|---|---|
| 1 | ~~Le suivi des lieux et les écritures de statut, de position et de batterie s'arrêtent~~ | **CADUC** (remplacé le 2026-11-10) | Remplacé par D1 ci-dessous |
| **D1** | **La position reste partagée comme d'habitude** pendant le voyage : mesure et écriture de `positions/{moi}` continuent avec **les mêmes garde-fous et consentements** | **[Décidé] 2026-11-10** | §5.2, §11, V2b |
| **D2** | Carte du voyageur au Feed **grise**, avec un **avion** et **« En voyage jusqu'au <date> »** | **[Décidé] 2026-11-10** | §6 |
| **D3** | Le voyageur n'a **aucun impact** sur la couleur ni le statut de la Miff, **ni sur le « n sur m »** | **[Décidé] 2026-11-10** | §6.4 |
| 4 | Plus aucun rappel N1 à N4 ni N6 pour le voyageur ; ses états ne déclenchent plus N3 / N4 chez les autres | Maintenu **[Proposé]**, à reconfirmer au vu de D1 (**Q7**) | §9 |
| 5 | Retour automatique à la date de fin, avec notification locale **N7** | Maintenu, **texte de N7 modifié** (**Q6**) | §5.6, §9 |
| 6 | ~~Réservé au Premium mais visible par tous~~ | **CADUC** : **ouvert à TOUS** (PM, 2026-11-10) | §4.3, §8 |
| 7 | Désactivable à tout moment avant la date de fin | Maintenu | §5.6 |
| 8 | ~~Photo d'update et « Localiser » **non** désactivés (exception au suivi arrêté)~~ | **CADUC en tant qu'exception** : c'est désormais la **règle générale** (D1) | §6.2, §11 |
| **D9** | Fin = **00:00 du lendemain du jour choisi** ; **sélecteur : au plus aujourd'hui + 89 jours** (les règles serveur acceptent ≤ 91 jours) | **[Décidé] 2026-11-10** | §4, §7.3 |
| **D10** | Aucune écriture de **statut de présence ordinaire** en voyage (elle **écraserait** le statut voyage) | **[Décidé] 2026-11-10** | §5.2, V2 |

---

## 4. Modèle de données (pour `backend-data` ; règles de `main` à ne pas modifier)

### 4.1 Où et quoi

Le voyage est un **état du statut du membre dans chaque Miff** : `miffs/{miffId}/status/{uid}`. Aucun nouveau chemin, aucun écouteur de plus pour les proches.

| Champ | Valeur en voyage | Valeur hors voyage |
|---|---|---|
| `presence` | `"travel"` (en plus de `in_trusted`, `out`, `in_risk`, `unknown`) | inchangé |
| `placeId` | `null` | inchangé |
| `presenceAt` | l'**instant d'activation** (« Depuis le… ») | inchangé |
| `lastUpdateAt` | conservé, ou l'instant d'une update manuelle faite pendant le voyage | inchangé |
| `batteryPct` | `null` : **la batterie n'est pas partagée pendant le voyage** | inchangé |
| `escalationDueAt` | `null` | inchangé |
| `locationAccess` | conservé tel quel | inchangé |
| `travelUntil` *(facultatif)* | timestamp : fin = **00:00 heure locale du lendemain du jour choisi** | absent ou `null` |
| `travelUntilDay` *(facultatif)* | chaîne `AAAA-MM-JJ` : jour choisi (**affichage seul**) | absent ou `null` |

**`positions/{uid}` : inchangé et NON concerné par le voyage.** Le voyage ne supprime pas la dernière position, ne l'interdit pas, ne la masque pas. **Anciennes apps** : `travel` est une valeur inconnue → carte **neutre** sans étiquette de voyage, sans erreur (dégradation acceptable).

### 4.2 Règles `firestore.rules` (déjà sur `main`, **ce ticket ne les modifie pas**)

`validTravel` : en voyage, `placeId`, `batteryPct`, `escalationDueAt` nuls ; `travelUntil` strictement futur et **≤ 91 jours** ; `travelUntilDay` bien formé ; hors voyage, les deux clés absentes ou nulles. Aucune barrière Miff+, **2 lectures de règles** par statut (comme un statut ordinaire), **8 statuts au plus par lot**. Un compte gratuit, premium ou premium expiré obtient le même résultat.

**Conséquences à connaître (changement de lecture par rapport à l'ancienne version)** :
- **Les règles n'empêchent pas** d'écrire une position ou une photo pendant un voyage : **c'est voulu** (D1), plus une « limite connue ». Le cas de test de caractérisation **VY27** décrit donc le **comportement attendu**, plus un trou à combler.
- **Ce que les règles ne peuvent pas empêcher** et que les **clients doivent garantir (D10)** : écrire un **statut de présence ordinaire** (`out`, `in_trusted`…) pendant le voyage est **accepté** par les règles (c'est le geste « désactiver ») mais **mettrait fin au voyage côté proches**. C'est donc un **contrat du client**, vérifié par les tests V2 (**bloquants**).
- La batterie ne s'affiche pas en voyage : le rafraîchissement partiel de la batterie est **refusé** par les règles (document résultant `travel` + batterie non nulle) ; le moteur ne l'envoie pas.
- Aucun plafond de durée cumulée : un client peut renouveler sa date (limite d'interface de 90 jours, voulue pour que le voyage reste temporaire et visible).

### 4.3 Aucune barrière Premium

Le client ne lit pas `entitlements/{uid}` pour le mode voyage : interrupteur actif pour tous, aucun écran Miff+, aucun paywall.

### 4.4 Coût Firestore (hypothèses, pas des mesures)

| Poste | Estimation |
|---|---|
| Activation, changement de date, désactivation | 1 écriture de statut par Miff où je partage (2 lectures de règles chacune) |
| Proches : réception du changement | 1 lecture par proche et par changement (écouteur existant) |
| **Pendant** le voyage | **0 écriture automatique de statut** ; **écritures de `positions/{moi}` inchangées** (mêmes plafonds que d'habitude) |

Rien ne s'approche du quota gratuit.

---

## 5. Comportement (observable ; le détail technique est à la charge des agents dev)

### 5.1 États (côté appareil du voyageur)

`NORMAL` → `TRAVEL_ACTIVE(until, untilDay)` → `NORMAL` (fin automatique, désactivation anticipée ou déconnexion). L'état est **conservé localement** et porté par les statuts (vérité côté proches). **Source de vérité au démarrage d'une session** : le statut `travel` de **mon propre document** (§5.7).

### 5.2 Pendant `TRAVEL_ACTIVE` : ce qui continue, ce qui s'arrête

| Continue **comme d'habitude** (D1) | S'arrête (D10) |
|---|---|
| **Mesure de la position** et **écriture de `positions/{moi}`**, avec **les mêmes garde-fous et consentements** (session, accord de consentement en vigueur, permission « Toujours », appareil actif, partage actif, plafonds habituels) | **Écriture d'un statut de présence ordinaire** (`in_trusted`, `out`, `in_risk`, `unknown`) : elle écraserait le statut voyage |
| « **Localiser** » (les proches me localisent ; je localise les autres) | **Rafraîchissement de la batterie** (refusé par les règles, batterie non affichée) |
| **Photos localisées** (publier / lire, les miennes et celles des autres) | Rappels N1 à N4 et N6 pour moi (**Q7**) |
| Lecture des **positions et photos des autres** | |
| Message, Appeler | |

Le voyageur peut entrer ou sortir de ses lieux : **aucune carte, aucune couleur, aucune notification** ne le reflète pendant le voyage. Les permissions et consentements **ne sont ni redemandés ni contournés** par le mode voyage.

### 5.3 Activation (`NORMAL` → `TRAVEL_ACTIVE`)

Déclencheur : l'utilisateur confirme (interrupteur + date + « Activer »). **Ordre imposé** (pour qu'un refus du serveur ne laisse jamais le statut de présence figé) :
1. **Valider localement** la date (≥ aujourd'hui, ≤ aujourd'hui + 89 jours) ; calculer la fin (00:00 local du lendemain) et le jour affiché.
2. **Écrire d'abord le voyage** dans `status/{moi}` de **chaque Miff où je partage** (par lots de 8 au plus) : `presence = travel`, `placeId`, `batteryPct`, `escalationDueAt` nuls, `presenceAt` = maintenant, `travelUntil`, `travelUntilDay`.
3. Dès que l'écriture est **acceptée ou conservée en attente hors connexion** : passer l'état local à `TRAVEL_ACTIVE` et **cesser** d'écrire des statuts de présence ordinaires (D10). **Si le serveur la refuse** (règles pas encore déployées) : **rien n'a changé**, message calme (§7.4).
4. **Annuler** les rappels N1 à N4 et N6 en attente (Q7) ; **programmer** N7 (§9) et, sur Android, l'alarme de fin.
5. **Aucune suppression de position.** ~~(ancien point 4 « mise en pause du suivi » et ancien point 6 « suppression de `positions/{moi}` » : CADUCS)~~.

### 5.4 Pendant le voyage

- **Aucune écriture de statut de présence ordinaire, aucun rafraîchissement de batterie** ; la mesure et l'écriture de la position continuent (§5.2).
- **Mise à jour manuelle (photo, texte)** : autorisée. Elle écrit le statut **en conservant** `presence = travel`, `travelUntil`, `travelUntilDay`, `batteryPct = null`, et ne change que `lastUpdateAt`, `updateText`, `hasPhoto` (+ le document photo, **localisé comme toute photo**). Elle **ne met pas fin** au voyage et **ne ramène pas au vert**.
- **Nouvelle Miff pendant le voyage** (je crée ou rejoins une Miff, ou je réactive mon partage) : le lot d'entrée crée le statut initial `unknown` ; **juste après**, le voyage y est **écrit**, pour que ses membres me voient en voyage.
- **Quitter, perdre ou supprimer une Miff, partage arrêté** : comme aujourd'hui (le statut est supprimé) ; le voyage s'applique aux autres Miffs.

### 5.5 Changement de date (voyage déjà actif)

Mêmes écritures qu'à l'activation, **sans** autre effet : nouvelle valeur de `travelUntil` / `travelUntilDay` dans chaque Miff ; reprogrammer N7 et l'alarme de fin. Ouvert à tous. Une date plus courte est permise.

### 5.6 Fin du voyage (`TRAVEL_ACTIVE` → `NORMAL`)

**Trois causes** : (a) fin automatique à la date de fin ; (b) désactivation anticipée ; (c) déconnexion (§5.7).

**Fin automatique et désactivation anticipée** :
1. Écrire dans chaque Miff `presence = unknown`, `placeId = null`, `presenceAt = maintenant`, **sans** clés de voyage (statut **neutre** « inconnu » : gris, jusqu'à la première mesure réelle).
2. **Reprendre l'écriture des statuts de présence** : une nouvelle évaluation a lieu tout de suite (si l'utilisateur est dans un lieu, sa présence est écrite) ; sinon le statut reste `unknown` jusqu'au prochain événement (comportement existant de la reprise). [Proposé, Q6]
3. Effacer l'état local ; N7 : voir §9 (annulée en cas de désactivation anticipée).

- **Android** : reprend **sans ouvrir l'app** à la date de fin (alarme inexacte, reprogrammée au redémarrage / changement d'heure / mise à jour ; filets à l'ouverture et au redémarrage).
- **iOS** : la fin est traitée **au premier réveil de l'app** ou **à l'ouverture** qui suit la date de fin (la position continuant d'être mesurée, l'app est réveillée comme d'habitude, [À vérifier]), ou au toucher de N7 ; **pas à la seconde près** (limite à accepter, Q5).
- **Si l'app du voyageur n'a pas repris après la date** : les **proches** calculent à la lecture : `maintenant ≥ travelUntil` → carte grise qui dit **« Voyage terminé »** (jamais un faux « en voyage »), sans couleur, jusqu'à la première mesure réelle.

### 5.7 Autres cas

| Cas | Comportement |
|---|---|
| **App tuée** | Le statut reste `travel` côté serveur ; Android : l'alarme survit (sauf arrêt forcé : filets) ; iOS : voir §5.6 |
| **Redémarrage** | Android : alarme de fin et N7 reprogrammées depuis l'état local |
| **Changement d'heure ou de fuseau** | La fin est un **instant absolu** : elle ne bouge pas ; le jour affiché ne bouge pas ; N7 et l'alarme sont recalculées |
| **Déconnexion volontaire** | L'état local est effacé ; **le statut `travel` reste côté serveur** jusqu'à la fin (les proches voient le voyage, puis « Voyage terminé ») |
| **Suppression de compte** | Statut supprimé avec le compte ; N7 et alarme annulées |
| **Appareil remplacé** | Le **nouvel appareil**, **avant d'écrire le moindre statut de présence**, lit **mon propre statut** (1 lecture) : s'il est `travel` avec fin future, il **adopte** le voyage ; sinon il démarre normalement. **Sans cette lecture il écraserait le voyage** avec un statut ordinaire |
| **Écriture refusée** | À l'activation : rien n'a changé, message calme, l'interrupteur revient sur « désactivé ». À la fin : le moteur retente à chaque réveil |

---

## 6. Feed (ce que voient les proches, et le voyageur)

### 6.1 Calcul

- Nouvelle valeur `TRAVEL`. Le niveau de couleur est **NEUTRE quel que soit l'instant** (avant ou après la date de fin). `miffLevel` **inchangé** : un membre neutre est ignoré (D3). **Mêmes ajouts sur Android et iOS** (les cas de test sont le contrat).

### 6.2 Carte du voyageur

| Élément | Règle |
|---|---|
| Anneau de l'avatar | **Gris** (neutre) ; l'étiquette porte le sens, jamais la couleur seule |
| Badge | **Icône d'avion** + **« En voyage jusqu'au 15 nov. »** tant que `now < travelUntil` ; **« Voyage terminé »** (gris) ensuite, tant que le statut est encore `travel` |
| Ligne de temps | « Depuis le 8 nov. » |
| Batterie | **Masquée** (non partagée en voyage) |
| « Localisation limitée » | **Masquée** |
| Photo d'update | **Affichée** si non expirée (comme aujourd'hui) |
| **Localiser** | **Inchangé** : feuille avec la **dernière position et sa date**, comme pour tout membre ; **aucun libellé spécial voyage** (~~« Pas de position partagée en ce moment » lié à la suppression : CADUC~~) |
| **Message / Appeler** | Inchangés |
| Date | Format court de la langue du lecteur ; année ajoutée si ce n'est pas l'année en cours |

### 6.3 Ma propre carte

« Mode voyage jusqu'au 15 nov. » + lien **« Modifier »**. L'anneau de mon avatar en tête de Feed, le point du bouton central et la **bulle d'incitation** sont **neutres** : aucune invitation à publier pendant le voyage. Je vois et je localise les autres **normalement**.

### 6.4 Bulle de la Miff et synthèse

- Les voyageurs sont **exclus du « n sur m »** (ni dans n, ni dans m : D3) ; aujourd'hui le code les compterait comme silencieux : **à corriger**.
- **Au moins un voyageur parmi les autres** : la bulle garde son message habituel et ajoute « {Prénom} est en voyage jusqu'au 15 nov. » (un) ou « {n} proches sont en voyage. » (plusieurs).
- **Tous les autres sont en voyage** : bulle **neutre** « Tes proches sont en voyage. » (jamais « Tout va bien », jamais « Pas encore de nouvelles »).
- Aucune **page d'alerte** pour un voyageur (ni orange, ni rouge).

### 6.5 Rafraîchissement

Le minuteur local d'une minute du Feed réévalue `now ≥ travelUntil` (« En voyage » → « Voyage terminé ») sans lecture serveur.

---

## 7. Profil : interrupteur, date, textes

### 7.1 Placement

Onglet **Profil**, sous l'en-tête et avant « Mes Miffs » : carte « Mode voyage », **visible et activable par tous**.

### 7.2 Maquette texte (à confirmer par le PM / design, §15)

```
Désactivé :
┌──────────────────────────────────────────┐
│ ✈  Mode voyage                    [ OFF ]│
│    Ta carte reste grise pendant ton      │
│    voyage, sans effet sur la couleur de  │
│    la Miff. Ta position reste partagée   │
│    comme d'habitude.                     │
└──────────────────────────────────────────┘
Activé :
┌──────────────────────────────────────────┐
│ ✈  Mode voyage                    [ ON  ]│
│    En voyage jusqu'au 15 nov.            │
│    Date de fin             15 nov. 2026 >│  → sélecteur de date natif
│    Pendant ton voyage, tes proches voient│
│    « En voyage jusqu'au 15 nov. » et ta  │
│    carte reste grise. Ta position reste  │
│    partagée comme d'habitude : ils       │
│    peuvent toujours te localiser et tes  │
│    photos gardent leur localisation.     │
│    Ta batterie n'est plus affichée.      │
└──────────────────────────────────────────┘
```

### 7.3 Parcours

| Étape | Comportement |
|---|---|
| Tout compte touche l'interrupteur (OFF → ON) | **Sélecteur de date natif** ; **date minimale : aujourd'hui** ; **date maximale : aujourd'hui + 89 jours** ; défaut : aujourd'hui + 7 jours |
| Après la date | Feuille de **confirmation** (texte §7.4, qui dit que la position reste partagée) ; [Activer] [Annuler] de poids égal |
| « Activer » | Séquence §5.3 ; l'interrupteur passe à ON dès que l'écriture est **acceptée ou en attente** |
| Voyage actif : changer la date | Sélecteur ; mêmes bornes ; pas de confirmation |
| Voyage actif : ON → OFF | **Immédiat**, sans confirmation ; N7 annulée |
| Hors connexion | Activation acceptée localement ; message calme « Tes proches verront ton voyage dès que tu seras en ligne. » |

### 7.4 Textes et clés (préfixe `travel_`, tutoiement, ton calme)

Les **noms de clés ne changent pas** (les agents Android / iOS en cours n'ont rien à renommer) ; seuls les **textes marqués « modifié »** changent. **Interdit dans tout texte du voyage** : « pause », « arrête », « suspend », « désactive », « masque », « privé », « invisible » appliqués à la **position** (la position reste partagée).

| Clé | FR | EN | Statut |
|---|---|---|---|
| `travel_card_title` | Mode voyage | Travel mode | inchangé |
| `travel_card_off_body` | Ta carte reste grise pendant ton voyage, sans effet sur la couleur de la Miff. Ta position reste partagée comme d'habitude. | Your card stays grey while you travel, with no effect on the Miff's color. Your location stays shared as usual. | **modifié** (ancien : « Mets ton suivi en pause pendant un voyage. », CADUC) |
| `travel_card_on_status` | En voyage jusqu'au {date} | Traveling until {date} | inchangé |
| `travel_card_end_label` | Date de fin | End date | inchangé |
| `travel_card_explain` | Pendant ton voyage, tes proches voient « En voyage jusqu'au {date} » et ta carte reste grise : elle n'a plus d'effet sur la couleur de la Miff. Ta position reste partagée comme d'habitude : tes proches peuvent toujours te localiser et tes photos gardent leur localisation. Ta batterie n'est plus affichée. | While you travel, your circle sees “Traveling until {date}” and your card stays grey: it no longer affects the Miff's color. Your location stays shared as usual: your circle can still locate you and your photos keep their location. Your battery level is no longer shown. | **modifié** (ancien : « Miff arrête de suivre tes lieux et n'envoie plus ta batterie… », CADUC) |
| `travel_confirm_title` | Activer le mode voyage ? | Turn on travel mode? | inchangé |
| `travel_confirm_body` | Tes proches verront « En voyage jusqu'au {date} » à la place de ton état, et ta carte restera grise. Ta position reste partagée comme d'habitude : ce mode ne l'arrête pas. Tu peux l'arrêter quand tu veux. | Your circle will see “Traveling until {date}” instead of your status, and your card will stay grey. Your location stays shared as usual: this mode doesn't stop it. You can turn it off whenever you like. | **modifié** |
| `travel_confirm_action` / `travel_confirm_cancel` | Activer / Annuler | Turn on / Cancel | inchangé |
| `travel_off_done` | Le mode voyage est arrêté. | Travel mode is off. | **modifié** (ancien : « Le suivi reprend. », CADUC) |
| `travel_offline_note` | Tes proches verront ton voyage dès que tu seras en ligne. | Your circle will see your trip as soon as you're online. | inchangé |
| `travel_error_refused` | Le mode voyage n'a pas pu être activé. Tu peux réessayer plus tard. | Travel mode couldn't be turned on. You can try again later. | inchangé |
| `travel_feed_label` | En voyage jusqu'au {date} | Traveling until {date} | inchangé |
| `travel_feed_label_ended` | Voyage terminé | Trip ended | inchangé |
| `travel_feed_since` | Depuis le {date} | Since {date} | inchangé |
| `travel_feed_mine` / `travel_feed_mine_edit` | Mode voyage jusqu'au {date} / Modifier | Travel mode until {date} / Edit | inchangé |
| `travel_bubble_one` / `_many` / `_all` | {prénom} est en voyage jusqu'au {date}. / {n} proches sont en voyage. / Tes proches sont en voyage. | {name} is traveling until {date}. / {n} loved ones are traveling. / Your loved ones are traveling. | inchangé |
| `travel_n7_title` | Ton mode voyage est terminé | Your travel mode has ended | inchangé |
| `travel_n7_body` (Android) | Ta carte reprend ses couleurs chez tes proches. Bon retour ! | Your card is back to normal for your circle. Welcome back! | **modifié** (ancien : « Miff reprend le suivi de tes lieux… », CADUC) |
| `travel_n7_body_ios` | Ouvre Miff pour que ta carte reprenne ses couleurs. Bon retour ! | Open Miff so your card can go back to normal. Welcome back! | **modifié** |
| `travel_*_a11y` | « Mode voyage, interrupteur, activé, jusqu'au 15 novembre » ; avion décoratif | Screen-reader labels | inchangé |

**Accessibilité** : l'interrupteur et sa ligne forment un seul élément ; cibles 48 dp / 44 pt ; sélecteur natif ; l'état n'est jamais porté par la couleur seule (avion + texte) ; pas d'animation si « réduire les animations ».

---

## 8. Pas de Premium pour le mode voyage

Aucune ligne au catalogue Miff+, aucun écran provisoire, aucun paywall, aucun événement d'analyse de paywall pour le voyage. **Tout passage de ce ticket parlant de Premium, de « Miff+ expiré » ou de barrière est CADUC** (voir §21).

---

## 9. Notifications : N7 et NOTIF-P0

### 9.1 N7 « Mode voyage terminé » (locale, sans serveur, 0 €) [Proposé : maintenue, texte modifié, Q6]

| Élément | Règle |
|---|---|
| Nature | Locale, programmée à l'activation et à chaque changement de date |
| **Heure** | **08:00 heure locale du lendemain du jour de fin** (jamais en pleine nuit) ; la fin « technique » a lieu à 00:00 |
| Titre / description | « Ton mode voyage est terminé » / « Ta carte reprend ses couleurs chez tes proches. Bon retour ! » (iOS : « Ouvre Miff pour que ta carte reprenne ses couleurs. Bon retour ! ») |
| Au toucher | Le Feed (iOS : ouvre l'app et déclenche la fin si elle n'a pas encore été traitée) |
| Annulation | Désactivation anticipée ; déconnexion ; suppression de compte ; session prise par un autre appareil ; interrupteur N7 coupé ; autorisation retirée ; fin déjà traitée app ouverte (entrée de centre silencieuse) |
| Reprogrammation | Changement de date ; redémarrage (Android) ; changement d'heure ou de fuseau ; mise à jour ; langue (iOS) |
| Déduplication, plafonds | Clé `n7|{moi}|{travelUntil}` ; hors plafonds NOTIF-P0 ; importance normale |
| Réglage | « Quand mon mode voyage se termine », actif par défaut |
| Permission refusée | Rien n'est affiché ; la fin du voyage n'en dépend pas |

### 9.2 Gardes du planificateur de NOTIF-P0 (`notifications.md` §20)

1. **Moi en voyage** : liste = **N7 seul** ; N1, N2, N3, N4 et N6 vides (D4 maintenue, **Q7**).
2. **Un autre membre en voyage** : niveau **neutre**, aucun instant de changement, ignoré par `miffLevel` et par la règle « je suis responsable » ; un N3 / N4 reposant sur lui est replanifié (iOS : fausse alerte possible, limite déjà acceptée).
3. **N6** : tant que `TRAVEL_ACTIVE`, aucune N6 pour moi, **même si une position dans une zone de danger est mesurée** (Q7).
4. **Début de voyage** : annulation immédiate des demandes N1 à N4 et N6 en attente.
5. **Vecteurs de test T16 à T20** : moi en voyage (N7 seul) ; X en voyage, Y vert (pas de N3 / N4) ; X orange puis X en voyage (N3 annulée) ; fin de voyage (plan normal repris) ; X en voyage dont la fin est passée (toujours neutre).

---

## 10. Interactions avec les autres tickets

| Sujet | Effet |
|---|---|
| **Suivi (LOC-2b / 4b)** | **Inchangé** pour la mesure et l'écriture de la position ; seule l'**écriture du statut de présence ordinaire** est neutralisée pendant le voyage ; `PresenceMachine` inchangé |
| **Permissions** | « Toujours » et localisation précise **restent nécessaires et utilisées** ; rien n'est demandé de plus ni de moins |
| **Update** | Inchangé : la photo reste localisée ; `UpdatePrompt` neutre en voyage ; l'update ne met pas fin au voyage |
| **Couleur de Miff** | `miffLevel` inchangé (le neutre est ignoré) |
| **Zone de danger** | Aucune carte ni couleur ni N6 pour le voyageur (Q7) |
| **Session unique** | §5.7 (appareil remplacé) |
| **Premium** | Aucun lien |

---

## 11. Vie privée, consentement, textes légaux (point sensible)

- **Principe de vérité** : le mode voyage **ne réduit pas le partage de position**. Aucun texte (Profil, confirmation, Feed, N7, fiches des stores, politique de confidentialité, ASO) ne doit dire ou laisser croire qu'il le met en pause, l'arrête ou le masque. Ne pas surpromettre une discrétion qui n'existe pas : un voyageur qui se croirait non localisable alors qu'il l'est subirait une rupture de confiance grave, et l'app s'exposerait à un rejet ou à un retrait des stores (justification de la localisation en arrière-plan).
- **Ce que les proches apprennent** : « en voyage jusqu'au {date} » (voulu, dit à l'activation : **absence prévue du domicile**, sensibilité élevée, `data-model.md` §11). Ils **continuent** de voir : dernière position (« Localiser »), photos localisées. Ils **n'apprennent plus** : lieu de confiance, état de présence, batterie (le statut n'en porte pas).
- **Consentement** : l'accord de consentement en vigueur couvre déjà le partage de position ; le mode voyage ajoute **uniquement** l'état « en voyage » et sa date. Confirmation en contexte à l'activation (Q4).
- **Textes légaux à aligner par les agents concernés, sans les modifier ici** (et **sans prétendre à une relecture juridique**, décision du 2026-10-23) : politique de confidentialité (« un état de voyage et sa date de fin » ; **la position reste partagée pendant le voyage**) ; texte de consentement d'entrée dans une Miff (Q4). **CGU : plus de mention du mode voyage parmi les options Premium** (CADUC).
- **Stores** : aucune nouvelle collecte ; la justification de la localisation en arrière-plan reste exactement la même ; à reconfirmer par `release-manager` ; aucune fiche ne doit présenter le mode voyage comme un mode « privé ».
- **Mineurs** : aucun changement (risque assumé).
- **Risque d'usage** : le mode voyage permet de **disparaître de la couleur** de la Miff ; le PM l'a voulu ; atténuations : date visible, 90 jours au plus côté interface, retour automatique, Message / Appeler / photo / Localiser inchangés.

---

## 12. Différences Android / iOS

| Sujet | Android | iOS |
|---|---|---|
| Position | Mesure et écriture inchangées | Idem |
| Fin automatique | Alarme inexacte, reprise **sans ouvrir l'app** ; filets au redémarrage / à l'ouverture | Traitée **au premier réveil ou à l'ouverture** après la fin, ou au toucher de N7 [À vérifier] ; pas à la seconde près |
| N7 | Alarme inexacte reprogrammée au redémarrage | Notification locale par intervalle (1 demande) |
| Sélecteur de date | Material | `DatePicker` natif |
| Tests | OPPO Find X5 Lite en local | iPhone 11 Pro par TestFlight |

---

## 13. Découpage en tickets (backend, puis Android, puis iOS)

Une branche et une PR par plateforme et par ticket ; maquettes avant développement (règle du 2026-10-28), mais les devs peuvent démarrer sans en listant leurs écarts dans la PR.

| # | Ticket | Contenu (mis à jour 2026-11-10) | Taille | Dépend de |
|---|---|---|---|---|
| **VOY-0** | Décisions et maquettes | Réponses Q4 à Q7 ; maquettes §15 | S | Cette spec |
| **VOY-B** | **Backend** | Règles **déjà sur `main`, non modifiées** ; alignement de `data-model.md`, `docs/dev/mode-voyage-clients.md`, `rules-test-plan.md` §24 (VY21 / VY22 : suppression de position retirée du geste d'activation ; VY27 : comportement attendu, plus limite) ; déploiement sur « oui » explicite du PM | S | VOY-0 |
| **VOY-1A** | Android : modèle et moteur | `PresenceValue.TRAVEL`, `MemberStatusFacts.travelUntil*`, neutre ; **garde « aucune écriture de statut de présence ordinaire » (le suivi de la position continue)** ; séquences §5.3 à §5.7 ; alarme de fin ; tests de toutes les branches | **L** | VOY-B |
| **VOY-2A** | Android : Profil | Carte, sélecteur (max +89 j), confirmation honnête, états hors connexion / refusé | M | VOY-1A |
| **VOY-3A** | Android : Feed | Carte grise, étiquettes, bulle, « n sur m » sans voyageurs, ma carte, Localiser inchangé | M | VOY-1A |
| **VOY-4A** | Android : N7 + gardes NOTIF-P0 | N7, gardes §9.2, T16 à T20 | S à M | VOY-1A ; NOTIF-3A / 4A |
| **VOY-1I à 4I** | iOS : mêmes livrables | Garde de présence du `TrackingRuntime` ; fin au réveil / à l'ouverture / N7 ; TestFlight | idem | Équivalents Android ; INIT-4 |

---

## 14. Critères d'acceptation vérifiables

**Règles et données (VOY-B)**
- [ ] **VB1** Un compte gratuit, un premium et un premium expiré créent, modifient et désactivent un statut `travel` dans les mêmes limites ; aucune lecture de `entitlements`.
- [ ] **VB2** `travelUntil` : futur, ≤ 91 jours ; `travelUntilDay` bien formé ; aucun lieu, aucune batterie, aucune échéance en voyage.
- [ ] **VB3** Les écritures de statut actuelles passent inchangées ; **`firestore.rules` n'est pas modifié par ce ticket**.
- [ ] **VB4** Lot de 8 statuts de voyage : appels de règles mesurés ≤ 20 ; l'activation ne supprime plus de position.

**Moteur et machine d'états (VOY-1)**
- [ ] **V1** À l'activation, **l'écriture du voyage précède** l'arrêt des statuts de présence ; si elle est refusée, la présence ordinaire n'a pas été coupée.
- [ ] **V2 (BLOQUANT : contrat du client, les règles acceptent un statut ordinaire)** Pendant le voyage : **aucune écriture de statut de présence ordinaire** ni de rafraîchissement de batterie (compteur d'écritures de statut dans les tests) ; seules sont permises la réécriture du voyage (changement de date, update manuelle).
- [ ] **V2b (BLOQUANT)** Pendant le voyage, **la mesure de la position et l'écriture de `positions/{moi}` continuent avec les mêmes garde-fous et consentements** (session, consentement en vigueur, permission « Toujours », appareil actif, partage actif, plafonds habituels) ; **aucune suppression de position** à l'activation ni pendant ; aucune permission ni consentement redemandé ou contourné.
- [ ] **V3** Une update manuelle pendant le voyage conserve `presence = travel`, `travelUntil`, `travelUntilDay` et met `batteryPct` à `null`.
- [ ] **V4** Fin automatique : Android reprend sans ouvrir l'app ; iOS au premier réveil / à l'ouverture ; dans les deux cas le statut passe à `unknown` **avant** l'écriture d'un statut ordinaire.
- [ ] **V5** Désactivation anticipée : reprise immédiate ; N7 annulée.
- [ ] **V6** Changement de date : valeurs changées dans **chaque** Miff ; N7 et alarme reprogrammées.
- [ ] **V7** Appareil remplacé : le nouvel appareil **adopte** un voyage en cours (lecture de mon statut avant tout statut de présence) et ne l'écrase pas.
- [ ] **V8** Nouvelle Miff pendant le voyage : voyage écrit juste après le statut initial.
- [ ] **V9** Redémarrage, changement d'heure et de fuseau : alarme et N7 reprogrammées.
- [ ] **V10** Déconnexion : état local effacé ; statut serveur conservé jusqu'à la fin.
- [ ] **V11** Compte sans Miff où je partage : aucune écriture de statut ; l'état local suffit.
- [ ] **V12** Pendant mon voyage, un proche peut me **localiser** (position à jour, avec sa date) et je **localise** les autres ; mes photos restent localisées.

**Feed (VOY-3)**
- [ ] **F1** Carte **grise**, icône d'avion et « En voyage jusqu'au {date} » (jour = `travelUntilDay`), sans batterie ni « Localisation limitée » ; photo, Localiser, Message, Appeler inchangés.
- [ ] **F2** `miffLevel` ignore le voyageur ({voyageur, vert} = verte ; {voyageur seul} = neutre).
- [ ] **F3** « n sur m » exclut les voyageurs du numérateur **et** du dénominateur ; ligne « est en voyage » ; « Tout va bien » jamais **à cause** d'un voyageur seul.
- [ ] **F4** `now ≥ travelUntil` : « Voyage terminé » sans relecture serveur.
- [ ] **F5** Ma carte : « Mode voyage jusqu'au {date} » + « Modifier » ; anneau, point central et bulle d'incitation neutres.
- [ ] **F6** Une app sans cette version affiche une carte neutre sans erreur.
- [ ] **F7** La feuille « Localiser » d'un voyageur est identique à celle d'un autre membre (aucun libellé spécial voyage).

**Profil (VOY-2)**
- [ ] **P1** Carte « Mode voyage » visible et activable par **tous** les comptes, sans écran Miff+ ni paywall.
- [ ] **P2** Sélecteur natif : minimum aujourd'hui, **maximum aujourd'hui + 89 jours** ; confirmation avant activation ; textes de §7.4.
- [ ] **P3** Hors connexion : activation acceptée localement + message calme ; refus du serveur : message calme et retour à « désactivé ».
- [ ] **P4** Textes FR et EN, clés `travel_` identiques sur les deux plateformes (parité CI), tutoiement ; accessibilité de §7.4.
- [ ] **P5 (BLOQUANT confiance)** **Aucun texte** de l'app (carte, confirmation, Feed, N7) ne dit ni ne laisse croire que le voyage arrête, suspend ou masque le partage de la position ; la confirmation dit explicitement que **la position reste partagée**.

**Notifications (VOY-4)**
- [ ] **N7-1** N7 programmée à `travelUntil + 8 h` locale, annulée à la désactivation / déconnexion / suppression de compte / session perdue, reprogrammée au redémarrage et au changement de date ; jamais deux fois.
- [ ] **N7-2** En voyage, aucune N1, N2, N3, N4 ni N6 pour moi (T16 à T20, sous réserve de Q7).
- [ ] **N7-3** N7 dans le centre de notifications ; toucher = Feed.

---

## 15. Maquettes à demander au PM

1. **Profil : carte « Mode voyage »** (désactivé, activé, chargement, hors connexion, erreur) : avec la mention « Ta position reste partagée ».
2. **Sélecteur de date** (export de la feuille native) et **feuille de confirmation** (avec la mention de position).
3. ~~Écran provisoire Miff+~~ : **supprimé**.
4. **Feed : carte grise « En voyage jusqu'au 15 nov. »** (autre membre), **ma carte**, carte « Voyage terminé ».
5. **Bulle de la Miff** : un voyageur parmi d'autres, plusieurs, tous.
6. **Notification N7** (écran verrouillé, Android et iOS).
7. ~~Ligne Premium dans le paywall~~ : **supprimée**.

---

## 16. Plan de recette PM

| # | Scénario | Appareil | Attendu |
|---|---|---|---|
| 1 | Compte **gratuit** : activer jusqu'à demain | OPPO ; iPhone | Ma carte grise ; chez un proche : carte grise avec avion « En voyage jusqu'au … » ; aucun écran Miff+ |
| 2 | Compte **Miff+** : même scénario | OPPO ; iPhone | Même résultat |
| 3 | Pendant le voyage : sortir de mes lieux, marcher, ouvrir / fermer l'app | OPPO ; iPhone | Carte du voyageur **inchangée (grise)** chez le proche ; pas de N1 à N4 / N6 |
| 4 | Pendant le voyage : le proche appuie sur **Localiser** | OPPO ; iPhone | **Position à jour affichée**, comme d'habitude |
| 5 | Pendant le voyage : le voyageur ouvre **Localiser** sur un proche et regarde ses photos | OPPO ; iPhone | Fonctionne comme d'habitude |
| 6 | Publier une photo pendant le voyage | OPPO ; iPhone | Photo (localisée) visible chez le proche ; voyageur reste gris |
| 7 | Changer la date ; puis désactiver avant la fin | OPPO ; iPhone | La date change ; désactivation : la carte reprend ses couleurs ; pas de N7 |
| 8 | **Fin automatique** (build interne : fin dans quelques minutes) | OPPO ; iPhone | OPPO : reprise sans ouvrir l'app ; iPhone : au réveil / à l'ouverture ; N7 reçue ; carte neutre puis couleur à la première mesure |
| 9 | Un proche orange pendant que je regarde puis il part en voyage | OPPO | Couleur de la Miff l'ignore ; « n sur m » l'exclut ; pas de N3 / N4 |
| 10 | Redémarrage pendant le voyage ; changement de fuseau | OPPO | N7 et alarme reprogrammées ; la carte reste grise |
| 11 | Changer de téléphone pendant le voyage | — | Le nouvel appareil conserve le voyage |
| 12 | Plusieurs Miffs ; Miff rejointe pendant le voyage | OPPO | Voyage visible partout, y compris dans la nouvelle |
| 13 | Relire tous les textes du voyage | — | Aucun ne laisse croire que la position est arrêtée |

*Un build interne peut raccourcir la fin du voyage à quelques minutes ; jamais en production.*

---

## 17. Risques

| # | Risque | Effet | Mitigation |
|---|---|---|---|
| **R1** | **iOS ne traite pas la fin à la seconde près** | Carte « Voyage terminé » (neutre) avant la reprise des couleurs | Honnête (« Voyage terminé »), N7, limite à accepter (Q5) |
| **R2** | **Oubli de désactiver / date trop longue** | Un proche perd de vue la personne | 90 jours au plus côté interface, retour automatique, date visible |
| **R3** | **Masquer un danger réel** (gris à la place d'un rouge) | Réassurance trompeuse | Voulu par le PM ; texte « En voyage » explicite ; Message / Appeler / photo / **Localiser** inchangés |
| **R4** | **Croire que le voyage arrête le partage de la position** (confiance + rejet des stores) | L'utilisateur se croit invisible alors qu'il est localisable | Textes honnêtes (§7.4), critère **P5 bloquant**, mots interdits, fiches des stores sans « privé » ; revue des textes avant recette |
| **R5** | **Usage de contrôle ou de dissimulation** | Tension entre proches | Fonction à soi seul ; le voyage se voit |
| **R6** | ~~Fonction de vie privée réservée au payant~~ | **Sans objet** (ouvert à tous) | — |
| **R7** | **Règles non déployées avant les apps** | Activation refusée | Écrire d'abord (§5.3) ; message calme ; déploiement avant les apps sur « oui » du PM |
| **R8** | **Lot de 8 statuts** | Plus de 8 Miffs en une écriture refusée | Lots de 8 |
| **R9** | **Nouvel appareil ou réveil tardif qui écrit un statut ordinaire** | Le voyage est écrasé côté proches | Lecture de mon statut avant tout statut de présence (§5.7) ; V2 / V7 bloquants |
| **R10** | **Alarme de fin tuée** (ColorOS) | Reprise tardive | Filets à l'ouverture / au redémarrage ; N7 |
| **R11** | **Textes non relus par un juriste** | Risque assumé par le PM (2026-10-23) | Aucune prétention de relecture |
| **R12** | **Absence prévue du domicile visible de tout le cercle** | Information sensible | Confirmation à l'activation ; supprimé avec le statut |

---

## 18. Questions ouvertes pour le PM

Chaque question donne une **valeur par défaut** : le développement peut commencer avec elle.

**Q1. ~~Suppression de la dernière position à l'activation~~ : CADUQUE** (la position reste partagée, D1).
**Q2. ~~Photo localisée en voyage~~ : CADUQUE** (la photo reste localisée comme d'habitude, D1).
**Q3. ~~Premium expiré pendant un voyage~~ : CADUQUE** (ouvert à tous).

**Q4. Consentement.** Faut-il changer la **version** du texte de consentement d'entrée dans une Miff (redemande l'accord à tous les comptes) pour y ajouter « ton mode voyage et sa date de fin » ?
- *Reco* : **non** : confirmation en contexte + politique (la position, elle, est déjà couverte).

**Q5. Fin de voyage sur iOS.** Accepter que la fin soit traitée **au premier réveil ou à l'ouverture** de l'app qui suit la date, pas à la seconde près (la position étant toujours mesurée, c'est moins contraignant qu'avant) ?
- *Reco* : **accepter** ; la carte dit « Voyage terminé » entre-temps.

**Q6. Valeurs par défaut à confirmer** (un « oui » suffit) : sélecteur **aujourd'hui + 89 jours** (règle ≤ 91) ; fin = 00:00 du lendemain du jour choisi ; date minimale = aujourd'hui ; **N7 conservée à 08:00 le lendemain, avec le nouveau texte** (« Ta carte reprend ses couleurs chez tes proches ») ; voyage appliqué à **toutes** les Miffs où je partage ; à la fin, le statut repasse à **« inconnu » (gris) puis se recalcule** à la première évaluation ; toucher de N7 = Feed.
- *Reco* : oui à tout.

**Q7. Rappels du voyageur (nouveau, à cause de D1).** La position étant toujours mesurée, un événement de zone (dont une **zone de danger**) peut avoir lieu pendant le voyage. Confirmes-tu que **N1 à N4 et N6 restent coupés pour le voyageur** (aucun rappel, aucune notification d'entrée en zone de danger) ?
- *Reco* : **oui, coupés** (cohérent avec « aucun impact » et la carte grise ; sinon un rappel « donne des nouvelles » contredirait le mode voyage). *Alternative* : laisser N6 actif (seul le voyageur le reçoit, pas de carte) ; à trancher par toi.

---

## 19. Écarts et contradictions relevés

| # | Écart | Traitement |
|---|---|---|
| **E1** | ~~Décisions 1 et 8 se heurtent (photo localisée)~~ | **Résolu** par D1 |
| **E2** | ~~« Localiser » et position serveur conservée~~ | **Résolu** par D1 |
| **E3** | `FeedBuilder` compterait les voyageurs comme silencieux | Corrigé par VOY-3 (§6.4) |
| **E4** | `validStatus` / `validTravel` | **Déjà sur `main`**, non modifiés |
| **E5** | Un statut de présence ordinaire écrit pendant le voyage **écrase** le voyage (les règles l'acceptent) | Contrat du client, V2 bloquant |
| **E6** | **NOTIF-P0** (`notifications.md` §20.12bis) décrit N7 avec « Miff reprend le suivi de tes lieux » et des gardes fondées sur la pause du suivi | À répercuter (texte de N7, justification des gardes) |
| **E7** | Textes légaux, `rules-test-plan.md` §24 (VY21, VY22, VY27), commentaires de `firestore.rules` (« la pause du suivi est un contrat du client »), tests des règles | À aligner par `backend-data` ; **règles non modifiées ici** |

---

## 20. Ce que le PM doit faire (rien n'est fait par un agent)

1. **Répondre** à Q4 à Q7 (un « oui aux recommandations » suffit).
2. **Fournir** les maquettes de §15 (les développeurs peuvent démarrer sans).
3. **Donner le « oui » explicite** pour déployer les règles sur `miff-3d283` (avant les apps).

---

## 21. Contenu CADUC (remplacé ou supprimé le 2026-11-10)

| Ancien contenu | Pourquoi caduc |
|---|---|
| « Miff arrête de suivre » ; **pause du suivi** ; régions désarmées ; arrêt de la mesure de position ; arrêt du réveil réseau ; effacement de la copie locale de la position ; réarmement à la fin | La position reste partagée (D1) |
| **Suppression de `positions/{moi}` à l'activation** (ancienne Q1) ; libellé « Pas de position partagée en ce moment » lié au voyage | La position n'est pas supprimée |
| **Photo localisée = conflit** (ancienne Q2, E1, R4 « photo localisée en voyage ») | Comportement normal |
| Décision 8 comme **exception** | Devenue la règle générale |
| **Premium** : décision 6 (réservé), Q3, E5/E6 anciens, R6, barrière Miff+, écran provisoire, ligne du paywall, ligne CGU « options Premium » | Ouvert à tous |
| Textes `travel_card_off_body`, `travel_card_explain`, `travel_confirm_body`, `travel_off_done`, `travel_n7_body`, `travel_n7_body_ios` (anciennes valeurs) | Remplacés en §7.4 |
| V2 « aucune écriture automatique de présence, de position ou de batterie » | Devient « aucune écriture de **statut de présence** » + V2b (la position continue) |
| Sélecteur « aujourd'hui + 90 jours » | Remplacé par **+ 89 jours** (D9) |
| Q5 : iOS « sans région armée, plus de relance du système » | Hypothèse caduque (la position est toujours mesurée) ; Q5 reformulée |
| Lots « de 6 » (copie Notion précédente) | Erreur : c'est **8** |

*Ce document ne modifie ni le code, ni Notion, ni Figma, ni les règles Firestore, ni les textes légaux. Aucun texte n'est présenté comme relu par un juriste.*
