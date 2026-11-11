# Mode voyage : valeurs et gestes à implémenter côté clients (VOY-B)

**Source** : ticket `docs/tickets/mode-voyage.md` (décisions du PM du 2026-11-10 ; **le mode voyage est disponible pour TOUS les utilisateurs, gratuits comme Miff+** : aucune barrière premium, aucun écran « fait partie de Miff+ »). **Décision du PM du 2026-11-10 : pendant un voyage, la POSITION RESTE PARTAGÉE comme d'habitude** (« Localiser », photos localisées, lecture des positions et photos des autres) ; l'ancien principe « Miff arrête de suivre » (suivi en pause, positions supprimées) est **caduc**. Schéma de référence : `backend/data-model.md` (§2.7, bloc « Ce qui change : mode voyage »). Règles : `backend/firestore.rules` (`validTravel`, **non modifiées par cette décision**), tests `backend/tests/rules/29-mode-voyage.test.js` et `30-mode-voyage-temps2.test.js` (plan §24).

**Cette PR ne touche ni `android/`, ni `ios/`, ni `web/`, ni `docs/legal/`.** Ce document liste ce que les applications doivent lire et écrire, sans le faire.

## Ordre à respecter (important)

1. Fusion de la PR backend VOY-B, puis **déploiement des règles par le PM** : **fait le 2026-11-10** (information du PM, non vérifiable par l'agent ; tout redéploiement reste soumis à un « oui » explicite).
2. Seulement ensuite : livraison des applications qui écrivent `presence = "travel"`.

Avant ce déploiement, la production refusait `presence = "travel"` (valeur inconnue). L'inverse est sans risque : une application actuelle continue d'écrire ses statuts ordinaires après le déploiement. Conséquence pour les clients (ticket §5.3) : **écrire le voyage d'abord, ne cesser d'écrire des statuts de présence ordinaires qu'après acceptation ou mise en attente hors connexion** ; un refus `permission-denied` ne doit rien changer au comportement du suivi.

## Statut `miffs/{miffId}/status/{moi}` en voyage

| Champ | Valeur en voyage | Valeur hors voyage |
|---|---|---|
| `presence` | `"travel"` | `"in_trusted"`, `"out"`, `"in_risk"`, `"unknown"` |
| `placeId` | **`null`** (obligatoire) | inchangé |
| `presenceAt` | instant d'activation (affichage « Depuis le … ») | inchangé |
| `lastUpdateAt` | conservé, ou l'heure d'une update manuelle | inchangé |
| `updateText`, `hasPhoto` | inchangés (update manuelle autorisée) | inchangé |
| `batteryPct` | **`null`** (obligatoire : la batterie n'est pas partagée en voyage) | inchangé |
| `locationAccess` | conservé tel quel | inchangé |
| `escalationDueAt` | **`null`** (obligatoire) | inchangé |
| `updatedAt` | `serverTimestamp()` | inchangé |
| `deviceId` | celui de la session (SESS-1), comme tout statut | inchangé |
| **`travelUntil`** | **Timestamp obligatoire** : fin du voyage = **00:00 heure locale du lendemain du jour choisi** | **absent, ou `null`** |
| **`travelUntilDay`** | **Chaîne `AAAA-MM-JJ` obligatoire** : le jour choisi (affichage seul, sans fuseau) | **absent, ou `null`** |

Les deux clés de voyage sont **facultatives dans le schéma** (un statut ordinaire ne les écrit pas) mais **obligatoires avec `presence = "travel"`** ; avec une autre présence elles doivent être absentes ou `null` (sinon la règle refuse).

**`positions/{moi}` n'est pas concerné par le voyage** : elle continue d'être mesurée et écrite comme d'habitude, avec les mêmes garde-fous et consentements (voir plus bas).

## Valeurs que les clients lisent et doivent valider eux-mêmes

| Sujet | Valeur | Qui la contrôle |
|---|---|---|
| Fenêtre choisie par l'utilisateur | date minimale = **aujourd'hui** (le voyage se termine ce soir), maximale = **aujourd'hui + 89 jours** (décision du PM du 2026-11-10 : la fin à 00:00 le lendemain donne alors 90 jours au plus) ; défaut proposé : aujourd'hui + 7 jours | **Client** |
| Borne des règles | `travelUntil` doit être **strictement futur** (> heure serveur) et **<= heure serveur + 91 jours** (marge de 1 jour pour le fuseau et la dérive d'horloge au-delà des 90 jours) | **Règles** (refus si dépassé) |
| Format de `travelUntilDay` | `AAAA-MM-JJ` avec zéros de remplissage, année 2020 à 2099, mois 01 à 12, jour 01 à 31 (expression exacte : `firestore.rules`, `validTravel`) | **Règles** (forme) ; la cohérence avec `travelUntil` n'est **pas** vérifiée (aucune règle ne lit le fuseau ; un client modifié ne trompe que l'affichage de sa propre carte) |
| Qui peut activer, changer la date, désactiver | **Tout compte**, gratuit, premium ou premium expiré, sans condition : **aucune lecture de `entitlements/{moi}`, aucun écran Miff+ pour le voyage** | **Règles** (aucune barrière) |
| Update manuelle (photo, texte) pendant le voyage | Autorisée : le statut réécrit `presence="travel"`, `travelUntil` et `travelUntilDay` (mêmes valeurs ou nouvelles, dans la fenêtre), `batteryPct=null` | **Règles** (la fenêtre se juge à chaque écriture) |
| Lots multi-Miffs | **8 statuts de voyage au plus par lot** (2 lectures de règles chacun = 16 sur 20) | **Client** (sinon refus du lot entier) |
| Plafond d'âge de `presenceAt` / `lastUpdateAt` | Inchangé (R-LIEU-1) : un voyage de plusieurs jours dépasse 24 h, donc une update manuelle **doit réécrire `presenceAt` et `lastUpdateAt` à l'identique de la base** (ou une valeur récente) ; ne jamais inventer une ancienne valeur | **Règles** |

## Gestes à implémenter

| Geste | Écritures | Notes |
|---|---|---|
| **Activer** | Par lot de **8 Miffs au plus**, pour chaque Miff où je partage : `status/{moi}` **set** complet avec `presence="travel"`, `placeId=null`, `presenceAt=serverTimestamp()` (ou l'instant client, borné), `lastUpdateAt` conservé ou maintenant, `batteryPct=null`, `escalationDueAt=null`, `travelUntil`, `travelUntilDay`, `updatedAt=serverTimestamp()`, `deviceId`. | **Aucune suppression de `positions/{moi}`** (~~ancien « dans le même lot, delete positions/{moi} » : CADUC, la position reste partagée~~). La photo d'update **n'est pas supprimée** non plus. |
| **Changer la date** | Même écriture que l'activation, nouvelles valeurs de `travelUntil` et `travelUntilDay` **dans chaque Miff** | Ouvert à tous. |
| **Update manuelle pendant le voyage** | Comme aujourd'hui (photo + statut), le statut **réécrit** `presence="travel"`, `travelUntil` et `travelUntilDay`, `batteryPct=null`, `lastUpdateAt` = maintenant, `presenceAt` conservé | **Ne ramène pas au vert.** La photo reste **localisée** comme toute photo. |
| **Position pendant le voyage** | **Inchangé** : mesure et écriture de `positions/{moi}` avec les mêmes garde-fous et consentements qu'hors voyage (session, consentement en vigueur, permission « Toujours », appareil actif, partage actif, plafonds habituels) | Aucune permission ni consentement redemandé, ni contourné. |
| **Statut de présence ordinaire pendant le voyage** | **Interdit côté client** : ne jamais écrire `in_trusted`, `out`, `in_risk` ni `unknown` pendant le voyage (décision D10 du PM) | Les règles **acceptent** cette écriture (c'est le geste « désactiver ») : elle **écraserait le voyage** côté proches. Garde du moteur à tous les points d'entrée (événement de zone, ouverture, retour du réseau…). |
| **Rafraîchissement partiel de la batterie** | **Interdit en voyage** : `update({ batteryPct: n })` est refusé par les règles (le document résultant aurait `travel` + batterie non nulle). Le moteur ne doit **pas** l'envoyer | `batteryPct: null` reste accepté (inoffensif). |
| **Désactiver / fin du voyage** | `status/{moi}` **set** avec `presence="unknown"`, `placeId=null`, `presenceAt=maintenant`, **sans** `travelUntil` ni `travelUntilDay` (ou les deux à `null`, ou `FieldValue.delete()` avec `update`) | À faire **avant** de reprendre l'écriture de statuts de présence ordinaires (ticket §5.6). **Envoyer toujours les deux clés à `null` dans chaque Miff de la liste, même celle qui n'est pas notée comme informée du début** : la réponse d'une écriture de début peut se perdre après que le serveur l'a prise, et sans cela le voyage resterait en base (carte « Voyage terminé » durable). |
| **Nouvelle Miff pendant le voyage** | Lot d'entrée habituel (statut initial `unknown`), **puis** un second lot qui écrit le voyage dans cette Miff | Le statut d'entrée n'est pas un voyage : les règles n'ont pas de raccourci. |
| **Appareil remplacé** | Lire **mon** `status/{moi}` (1 lecture) **avant d'écrire un statut de présence** ; si `presence == "travel"` et `travelUntil` futur, adopter le voyage | Voir ticket §5.7. |
| **Après la fin sans reprise** | Les proches calculent `maintenant >= travelUntil` : carte grise « Voyage terminé ». **Une update qui garde `presence="travel"` après `travelUntil` est refusée** (la règle exige `travelUntil` futur) : terminer le voyage (écrire `unknown`) **avant** toute update | Cas d'un voyageur qui rouvre l'app et publie une photo avant que la fin ait été traitée. |

## Ce que les règles ne font PAS (contrats clients)

- **Aucune règle n'empêche d'écrire une position ou une photo pendant le voyage : c'est voulu** (la position reste partagée, test de caractérisation VY27 = comportement attendu, plus une limite). **En revanche, les règles ne peuvent pas empêcher un statut de présence ordinaire** (accepté : c'est le geste « désactiver ») : **ne pas en écrire pendant le voyage est un contrat du client**, vérifié par les tests V2 du ticket (**bloquants**), de même que V2b (la position continue d'être mesurée et écrite avec les mêmes garde-fous, sans suppression). Les règles garantissent seulement que le **statut** lui-même ne contient ni lieu, ni batterie, ni échéance en voyage.
- **Aucune règle ne compare `travelUntilDay` à `travelUntil`** (limite connue, cosmétique), ni ne limite le total de jours cumulés : **chaque écriture est bornée à 91 jours côté serveur, mais un client peut renouveler sa date** (le plafond affiché à l'utilisateur est une limite d'interface, pas une garantie serveur).
- **Le comptage de lectures de règles des lots n'est pas vérifiable dans l'émulateur** (cache) : les chiffres de `backend/data-model.md` sont les comptages sans cache, figés par `backend/tests/unit/budgets-voyage.test.js`.

## Honnêteté des textes (contrat de confiance)

Le mode voyage **ne réduit pas le partage de position**. Aucun texte de l'application (carte du Profil, confirmation, Feed, notification de fin) ne doit dire ni laisser croire qu'il met la position en pause, l'arrête, la suspend ou la masque. Textes proposés : `docs/tickets/mode-voyage.md` §7.4 (les clés `travel_*` gardent leur nom ; six textes changent).

## Ancienne application

Une version sans cette PR lit `"travel"` comme une valeur de présence inconnue (`PresenceValue.fromWire` renvoie `null`) : carte **neutre** sans étiquette de voyage, aucune erreur (ticket §4.1). Aucune migration de données.
