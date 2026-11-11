# Ticket : Notification push « Un proche a donné des nouvelles » (publication d'une update, envoi par le serveur aux autres membres)

**Statut** : **proposition de l'agent `product-specs`, en attente du GO et des réponses du PM** (aucune décision de ce document n'est prise tant que le PM n'a pas répondu à §22). Aucun développement lancé, aucun déploiement, aucune écriture Notion.
**Rédigé le** : 2026-11-11.
**Demande du PM (2026-11-11, verbatim)** : « les membres d'une Miff reçoivent une NOTIFICATION (push) lorsqu'un membre publie une update ».
**Décision amont** : Blaze **activé** par le PM le 2026-11-11 (CLAUDE.md mis à jour) ; étude de référence `docs/dev/etude-serveur-notifications-premium.md` (option A : Blaze + Cloud Functions, plan SRV-0 à SRV-8). Ce ticket **détaille SRV-1, SRV-2, SRV-3 et la partie « update » de SRV-4**.
**Ticket Notion** : à créer ou relier par le PM (je n'écris rien dans Notion). Statut Notion suggéré : « A cadrer ».

**Convention** : **[Décidé]** = décision du PM déjà écrite (source citée). **[Proposé]** = ma proposition, à valider. **[À vérifier]** = fait de plateforme non confirmé. Les passages techniques (§8 à §12) sont des **propositions pour `backend-data`, `android-dev` et `ios-dev`**, à challenger au cadrage : ils décrivent ce qui est nécessaire, pas le code.

**Limites de cette lecture (à connaître)** :
- Je n'ai **pas d'outil shell** dans cette session : je n'ai pu ni faire `git fetch`, ni créer le worktree `../miff-notif-update`. Le fichier est donc écrit hors dépôt (à copier dans le worktree par l'agent appelant, voir le rapport). J'ai lu les documents dans le worktree le plus proche (`C:\dev\miff-claudemd`), **peut-être en retard sur `origin/main`** : l'ID **N8** de `notifications.md` ne m'est pas connu, d'où l'usage de l'ID existant **B5** du catalogue (voir §3). À réaligner à la relecture si `origin/main` a changé.
- **Sources lues** : `CLAUDE.md` ; `docs/dev/etude-serveur-notifications-premium.md` (entier) ; `docs/tickets/notifications.md` (§1, §5, §7, §9 à §12, §20) ; `docs/tickets/update.md` (§3, §5, §6) ; `docs/tickets/session-appareil-unique.md` ; `docs/tickets/mode-voyage.md` (extraits) ; `docs/dev/app-check.md` (extraits) ; `backend/data-model.md` (§2.7, §2.8, §2.10, §8, §9, §10, §13) ; `backend/firestore.rules` (`devices`, `photos`, `status`) ; `docs/legal/politique-confidentialite.fr.md` et `README.md` ; `docs/release/alignement-decisions-2026-11-11.md` ; mémoire projet (Blaze adopté).
- **Faits externes** (prix Firestore, régions Functions, limites FCM, APNs) : repris de l'étude, qui les a vérifiés le 2026-11-11 (ou marqués non vérifiés). Je n'ai rien re-vérifié ni inventé.
- Aucun texte de ce ticket n'a été relu par un juriste (décision du PM du 2026-10-23, risque assumé).

---

## 1. Résumé pour le PM

1. **Ce que ça fait** : quand quelqu'un publie une update (photo) depuis l'écran Update, **les autres membres de sa Miff qui partagent reçoivent une notification discrète** : « Tomessa a donné des nouvelles ». Toucher la notification ouvre le **Feed**. La photo et la position **ne sont jamais** dans la notification.
2. **Pourquoi c'est la première vraie notification entre téléphones** : elle exige un **serveur** (une Cloud Function, rendue possible par Blaze). Les rappels « à toi » (N1 à N7) restent locaux et ne changent pas.
3. **Gardes-fous** : l'auteur n'est jamais notifié de sa propre update ; un membre qui a **arrêté son partage**, **coupé l'interrupteur** ou **refusé l'autorisation** ne reçoit rien ; **au plus une notification par auteur et par heure** (le reste reste visible dans le Feed) ; **heures calmes** la nuit ; texte **bienveillant, sans nom de famille, sans lieu, sans « Miff » nommée**.
4. **Coût estimé** : environ **0 $ à 100 utilisateurs actifs par jour, 0,2 $/mois à 1 000, 2,4 $/mois à 10 000** pour cette seule fonction (§14, hors frais annexes de Blaze non chiffrés) ; les envois FCM sont gratuits. Le coût dominant reste la lecture de l'app elle-même.
5. **Ce que doit faire le PM** (rien n'est fait par un agent) : alertes de budget Blaze, **clé APNs `.p8`** déposée par lui dans la console Firebase, capacité Push Notifications, **chaque déploiement sur « oui » explicite** (§16).
6. **Textes légaux à corriger avant l'activation** : la politique dit aujourd'hui que Miff « n'enregistre aucun jeton de notification » ; ce sera faux. Fiches des stores (« pas de notification ») à retirer **le jour de la livraison**, pas avant (§15).
7. **8 questions** avec une valeur par défaut recommandée en §22 (interrupteur activé par défaut, discret, plafond d'une notification par auteur et par heure, réglage global et non par Miff, etc.).

---

## 2. Périmètre et frontières

| Dans ce ticket | Hors de ce ticket (propriétaire) |
|---|---|
| Notification push **B5** : « un proche a donné des nouvelles » | **B1 / B2** « pas de nouvelles » et clôture, **B3** arrivée, **B4** départ, **N5** zone de danger : futurs tickets (SRV-4 suite, SRV-6) ; cette fonction est conçue pour les accueillir |
| Registre de jetons d'appareil (`users/{uid}/devices/{id}`), cycle de vie | Notifications **locales** N1 à N7 et centre local (`notifications.md`) : inchangés, sauf la ligne du centre (§13) |
| Fonction Cloud (déclencheur, destinataires, plafonds, idempotence, journaux) | Publication d'une update, écran Update (`update.md`) : inchangé, une phrase d'aperçu ajoutée (§15) |
| Réglage Réglages > Notifications, réception Android et iOS | Premium réel / validation des achats (SRV-7, SRV-8) |
| Garde-fous de coût et de sécurité de Blaze (SRV-1) | Demande d'ajout de lieu (SRV-5) |
| Mise à jour des textes légaux et des déclarations des stores | Rédaction finale des fiches des stores (`release-manager`, `aso-marketing`) |

---

## 3. Vision et parcours

**Vision** : « savoir que ses proches vont bien, sans les déranger ». Donner des nouvelles est un geste **volontaire** (photo prise en direct) ; la notification en est le prolongement naturel : elle évite au proche d'ouvrir l'app pour vérifier, et elle ne dit rien d'autre que ce que l'auteur a choisi de montrer. Elle ne dramatise rien (aucune notification « d'absence » dans ce ticket).

**Lien avec le catalogue** : c'est **B5** de `notifications.md` §5.2 (« Update publiée », P2), qui y était **opt-in, désactivée par défaut**. Ce ticket l'active (valeur par défaut proposée : **activée**, Q1) et remplace son texte par celui du PM. Ni l'ID ni la place dans la grille de priorité (P4, supprimable, regroupable, discrète) ne changent.

### Parcours nominal

1. Tomessa ouvre l'écran Update, prend la photo, touche « Publier ». L'aperçu a dit : « Tes proches seront prévenus » (§15). L'app écrit, **comme aujourd'hui**, `photos/{moi}` et `status/{moi}` dans chaque Miff où elle partage (`update.md` §5.7). **Aucune écriture nouvelle, aucun appel supplémentaire du téléphone.**
2. **Le serveur** voit la nouvelle photo (déclencheur sur l'écriture de `miffs/{id}/photos/{uid}`), retrouve les **autres membres** de cette Miff qui partagent, vérifie leurs préférences, applique les plafonds, puis envoie **un message FCM par appareil** (jamais de « sujet », voir §9).
3. Chez Amandine : l'app est fermée ; une notification **discrète** arrive : « Tomessa a donné des nouvelles / Retrouve-les dans ta Miff ». Si l'app est ouverte sur le Feed : **aucune notification système** (le Feed se met déjà à jour).
4. Amandine touche la notification : le **Feed** s'ouvre sur la carte de Tomessa (la bonne Miff est sélectionnée si besoin).
5. Si Amandine n'a pas encore publié sa propre position ou n'a pas accepté la version de consentement requise, le Feed lui explique calmement pourquoi elle ne voit pas encore la photo (comportement existant de FEED-4, inchangé).

### Ce que le serveur ne fait pas
Il ne lit ni ne copie la photo, ne dit jamais où est la personne, ne calcule aucune couleur, n'envoie rien à propos d'une absence, et n'envoie rien à l'auteur.

---

## 4. Ce que contient la notification

| Élément | Règle | Source / raison |
|---|---|---|
| **Titre** | « {Prénom} a donné des nouvelles » | Exemple du PM |
| **Corps** | « Retrouve-les dans ta Miff. » | [Proposé] ; ne dit pas « photo », ne promet rien |
| **Prénom** | Le **prénom** de l'auteur (champ `firstName` de sa fiche de membre, **lu par le serveur**, jamais fourni par l'appareil de l'auteur), **tronqué à 20 caractères** avec « … » | Évite qu'un prénom très long ou trompeur ne devienne un message ; jamais le nom de famille |
| **Interdits** | Photo, miniature, texte de légende, position, nom de lieu, adresse, batterie, **nom de la Miff**, couleur du statut, heure précise | Écran verrouillé sobre (`notifications.md` §5.1.6) ; le nom d'une Miff (« Famille », « Chez Mamie ») peut révéler une relation à un tiers qui voit l'écran |
| **Au toucher** | Ouvre le Feed sur la carte de l'auteur (identifiants techniques de la Miff et de l'auteur dans le message, jamais de donnée personnelle de plus) | `notifications.md` §10.2 |
| **Ton** | Tutoiement, sans accord de genre, sans « enfin », « toujours pas », sans majuscules ni émoticône | CLAUDE.md |
| **Regroupée** | Plusieurs auteurs : « {n} nouvelles dans ta Miff » (clé existante `notif_group_title`), prénoms dans le corps (3 au plus, puis « et d'autres ») | `notifications.md` §5.3 |

**Textes et clés** (préfixe `notif_` existant ; remplace `notif_circle_update_*` de `notifications.md` §11, qui disait « Nouvelle update de {prénom} / Ouvre Miff pour la voir ») :

| Clé | FR | EN |
|---|---|---|
| `notif_circle_update_title` | {prénom} a donné des nouvelles | {prénom} checked in |
| `notif_circle_update_body` | Retrouve-les dans ta Miff. | See it in your Miff. |
| `notif_circle_update_group_body` | {prénoms} | {prénoms} |
| `notif_settings_circle_update` | Quand un proche donne des nouvelles | When someone in your circle checks in |
| `notif_settings_circle_update_hint` | Une notification discrète quand un proche de ta Miff publie une update. | A quiet notification when someone in your Miff posts an update. |

Remarque d'accord : « a donné des nouvelles » (exemple du PM) diffère de « a donné de ses nouvelles » (B2 de `notifications.md`). Une seule formule doit rester : **celle du PM** ; B2 sera alignée quand SRV-6 sera écrit (Q4).

---

## 5. Qui est notifié

Pour une update de l'auteur A dans la Miff M, le destinataire D reçoit la notification **si et seulement si** :

| # | Condition | Raison |
|---|---|---|
| R1 | D est **membre actuel** de M (sa fiche existe au moment de l'événement) et **D ≠ A** | Pas de notification à soi ; un ex-membre, retiré ou parti, ne reçoit plus rien (pas de « sujet » FCM : un abonnement ne s'éteint pas tout seul) |
| R2 | **D partage** (`sharing.enabled = true` sur sa fiche) | Réciprocité : sans partage, D ne peut pas lire les photos (règles) ; la notification l'inviterait à voir ce qu'il ne peut pas voir |
| R3 | D a **au moins un appareil enregistré** (jeton) : il a accepté l'autorisation du téléphone **et** l'enregistrement du jeton | Consentement (§15) |
| R4 | Le réglage « Quand un proche donne des nouvelles » de cet appareil est **activé** | Réglage (§6) |
| R5 | A **partage** (`sharing.enabled = true` sur sa fiche) | Imposé de fait par les règles d'écriture de la photo ; revérifié par le serveur |
| R6 | Le plafond de A pour M n'est pas atteint (§7) | Anti-spam |

Cas voulus :
- **Membre en mode voyage** : reçoit normalement (il lit les autres normalement, `mode-voyage.md` D1). **Auteur en voyage** : la notification part normalement (la publication reste un geste volontaire, la photo reste visible) [Proposé, Q5]. Aucune mention du voyage dans la notification.
- **Compte gratuit** (1 Miff) et **Premium** (plusieurs Miffs) : mêmes règles. Un destinataire présent dans deux Miffs de l'auteur peut recevoir deux messages : ils portent le **même identifiant de remplacement** (par auteur), le second remplace le premier à l'écran (§11, §12).
- **Moi seul dans la Miff** : aucun destinataire, aucune lecture au-delà de la liste des membres.
- **Grande Miff** : le plafond de 15 membres est **doux** (rien ne l'impose côté serveur, `miff.md` QM5) : la fonction **s'arrête à 30 fiches lues** et à 20 destinataires par événement (garde-fou de coût, §10).

---

## 6. Réglage

**Emplacement** : Réglages > Notifications (ticket `notifications.md` §9, section « Pour tes proches »).

```
Pour tes proches
  Quand un proche donne des nouvelles   [ ON ]
  Une notification discrète quand un proche de ta Miff publie une update.
```

| Sujet | Règle [Proposé] |
|---|---|
| **Défaut** | **Activé**, mais **sans effet tant que l'autorisation du téléphone n'est pas accordée** (rien n'est enregistré avant). Le PM a demandé la fonction ; l'ancien défaut « désactivé » de B5 est abandonné (Q1) |
| **Portée** | **Un seul interrupteur global**, pas un par Miff en v1 (Q8). Compte gratuit : une seule Miff, donc équivalent |
| **Stockage** | Sur le **document de l'appareil** (§8, champ `notify.updates`), pas dans le profil : le serveur le lit avec le jeton, **zéro lecture en plus**, et il disparaît avec le jeton |
| **Effet immédiat** | Comme la langue : pas de bouton « Enregistrer ». Désactiver écrit `notify.updates = false` (le jeton reste) ; réactiver le remet à `true` |
| **Interrupteur général « Recevoir des notifications »** | Désactivé = le document de l'appareil est **supprimé** (plus de jeton chez nous) ; réactivé = nouvel enregistrement (`notifications.md` §9.2 : « retire le jeton de l'appareil du compte ») |
| **Autorisation du téléphone refusée ou retirée** | L'interrupteur est affiché désactivé avec « Coupées dans ton téléphone » (comportement existant) ; au lancement suivant, l'app **supprime le document de l'appareil** |
| **Compte sans Miff** | Pas de ligne (comportement existant) |
| **Analytics** | Aucun événement sur l'état des notifications (`analytics-events.md`) |

---

## 7. Plafonds anti-spam, regroupement, heures calmes

| Règle | Valeur [Proposé] | Raison |
|---|---|---|
| **Par auteur, par Miff** | **1 notification par heure** (fenêtre glissante depuis la dernière notification envoyée pour cet auteur dans cette Miff). Les updates suivantes de l'heure **ne notifient pas** ; elles restent visibles dans le Feed | Un voyageur qui poste dix photos ne doit pas faire sonner dix fois son cercle. L'écran Update limite déjà à 20 publications par jour et 30 s entre deux (client seulement) |
| **Remplacement** | Un message d'un même auteur **remplace** le précédent encore affiché (identifiant de remplacement par auteur) | Pas d'empilement ; couvre aussi le cas de deux Miffs |
| **Regroupement** | Plusieurs auteurs : regroupement **par le système** (groupe Android, fil iOS), titre « {n} nouvelles dans ta Miff ». Pas de résumé retardé par le serveur en v1 (il demanderait un minuteur payant et complexe) | Réduire le bruit sans coût |
| **Plafond par destinataire et par jour** | **6 par jour** au total (`notifications.md` §5.4), tenu **sur l'appareil Android** (au-delà : ligne de centre silencieuse, pas de notification) ; **non tenu sur iOS** en v1 (aucun code avant l'affichage d'une alerte) : le plafond horaire par auteur borne déjà le total | Cohérence avec le plafond existant sans écriture serveur par destinataire |
| **Heures calmes** | **22 h – 7 h, heure locale du destinataire** (fuseau enregistré avec l'appareil, §8) : la notification est **livrée sans son ni vibration et sans allumer l'écran** (iOS : niveau « passif » ; Android : canal discret) | Même fenêtre que les autres notifications (`notifications.md` §10.5). Le personnalisable reste Premium |
| **Durée de vie** | Message abandonné s'il n'est pas livré en **2 h** | Une « nouvelle » de la veille serait trompeuse ; la photo ne vit que 24 h |
| **Jamais** | Aucun envoi pour une photo d'un événement plus ancien que 5 min au moment du traitement | Pas de notification tardive après un incident du serveur |

Une future clôture **B2** (« {Prénom} a donné de ses nouvelles » après un « pas de nouvelles ») devra **ignorer** le plafond horaire : le plafond de ce ticket ne s'applique qu'à B5.

---

## 8. Modèle de données : registre de jetons d'appareil

### 8.1 `users/{uid}/devices/{deviceId}` (déjà prévu, `data-model.md` §2.10 ; à étendre)

| Champ | Type | Règle |
|---|---|---|
| `token` | string 1 à 512 | Jeton FCM (Android) ou jeton FCM adossé à APNs (iOS). **Aucune lecture par un autre utilisateur, jamais** |
| `platform` | `"ios"` \| `"android"` | |
| `updatedAt` | timestamp | Heure serveur à chaque écriture (règle existante) |
| `tz` | string 1 à 64 | **Nouveau.** Fuseau IANA de l'appareil (ex. `Europe/Paris`), pour les heures calmes. Rafraîchi quand il change |
| `notify` | map `{ updates: bool }` | **Nouveau.** Un seul booléen en v1 ; d'autres types plus tard (même map) |

- **Identifiant** `{deviceId}` : l'identifiant d'installation de la session (SESS, `users/{uid}/session/active.deviceId`), qui respecte déjà la forme exigée par la règle existante (`[A-Za-z0-9_-]{1,64}`). **Un compte, un appareil** : à la connexion, le nouvel appareil **supprime les autres documents** `devices` du compte avant d'écrire le sien (les règles autorisent déjà la suppression par le propriétaire). Le serveur lit **au plus 3 documents** par destinataire.
- **Pas de langue stockée** : les textes restent dans les apps (Android compose le texte dans la langue de l'appareil ; iOS résout une clé de texte par le système). Moins de données, aucun risque de langue périmée.
- **Règles** [Proposé à `backend-data`] : étendre la liste fermée de clés (`token`, `platform`, `updatedAt` + `tz`, `notify`), valider `tz` (forme), `notify` (map fermée, booléens). Lecture, création, mise à jour, suppression **par le propriétaire seul** (inchangé). Tests : un autre membre ne lit jamais un jeton ; une clé inconnue est refusée ; le gabarit de l'ancienne règle reste accepté (compatibilité des builds déjà installés, qui n'écrivent rien).
- **Déploiement des règles** : sur « oui » explicite du PM, **avant** les builds qui écrivent le jeton (`docs/dev/deploiement-regles.md`).

### 8.2 Données serveur seulement : `notifState/{miffId}`

Collection **non lisible ni écrivable par un client** (refus par défaut des règles, à prouver par un test). Un document par Miff :
- pour chaque auteur : `lastSeenCreatedAt` (dernière publication traitée) et `lastNotifiedAt` (dernière notification envoyée) ;
- `expireAt` = dernière écriture + 2 jours (**suppression automatique par la durée de vie (TTL) de Firestore**, à activer par le PM dans la console ; TTL payant à l'usage sur Blaze, négligeable : [À vérifier] dans la console).
Sert à l'**idempotence** et au **plafond horaire** (§7, §10) : une lecture et une écriture par événement. Contient des identifiants d'utilisateurs : d'où la durée de vie courte (RGPD, §15).

---

## 9. Cycle de vie des jetons

| Moment | Comportement | Plateformes |
|---|---|---|
| **Enregistrement** | Seulement **après** l'autorisation du téléphone **et** l'écran d'explication corrigé (§15, « Miff enregistre l'identifiant de ton téléphone pour t'envoyer ces notifications »). Écrit `devices/{deviceId}` avec `token`, `platform`, `tz`, `notify.updates` | Android 13+ : après `POST_NOTIFICATIONS` ; Android 8 à 12 : après l'interrupteur de l'app ; iOS : après l'autorisation, puis jeton APNs |
| **Rafraîchissement** | À chaque nouveau jeton fourni par le système, **et** au lancement si le jeton a changé, si le fuseau a changé ou si le document a plus de **30 jours** (au plus une écriture par jour) | Les deux |
| **Connexion sur un autre appareil (SESS)** | Le nouvel appareil supprime les anciens documents du compte avant d'écrire le sien ; l'ancien appareil, déconnecté, n'est plus joignable | Les deux |
| **Déconnexion volontaire** | Dans le contrat de purge existant (`data-model.md` §8) : **après** `waitForPendingWrites` et **avant** `signOut`, supprimer `devices/{moi}` **et** invalider le jeton chez FCM | Les deux |
| **Suppression de compte** | Déjà prévu (§10 : « supprimer `devices/*` ») ; ajouter l'invalidation du jeton | Les deux |
| **Interrupteur général coupé / autorisation retirée** | Suppression du document de l'appareil (§6) | Les deux |
| **Jeton invalide** | Quand FCM répond « jeton inconnu ou désinstallé » à un envoi, **le serveur supprime ce document** (seule écriture du serveur dans `devices`) | Serveur |
| **Purge des inactifs** | Document de plus de **60 jours** sans rafraîchissement : suppression par une **fonction planifiée** (optionnelle, v2 : consomme l'une des **3 tâches planifiées gratuites par compte de facturation**, partagé avec Ghostrun si le compte de facturation est le même, voir l'étude §2) | Serveur |
| **Visibilité** | Aucun autre membre, aucun créateur de Miff, ne lit jamais un jeton [Décidé par défaut ; décision contraire = Q à poser] | |

---

## 10. La fonction Cloud (comportement attendu)

| Sujet | Exigence [Proposé à `backend-data`] |
|---|---|
| **Déclencheur** | Création **ou** remplacement de `miffs/{miffId}/photos/{uid}`. Un **effacement** (nettoyage d'une photo expirée, arrêt de partage, départ) **ne déclenche rien**. Pourquoi la photo et non le statut : le statut est aussi écrit par les arrivées et départs de lieu (l'horodatage `lastUpdateAt` y change aussi) ; la photo n'est écrite **que** par une publication |
| **Région / génération** | `europe-west9` (Paris), 2e génération (seule prise en charge de cette région, étude §2). À confirmer par SRV-1 (prototype) |
| **Compte de service** | **Dédié**, aux droits minimaux (lecture et écriture Firestore, envoi FCM), pas le compte par défaut du projet. **Aucune clé JSON, nulle part** (`docs/dev/secrets.md`) ; l'identité du service est gérée par Google |
| **Limites d'exécution** | `maxInstances` bas (**10** au départ), mémoire minimale (256 Mio), délai court (30 s). Pas de relance automatique en cas d'échec |
| **Idempotence** | Livraison « au moins une fois » : la fonction compare la date de publication de l'événement à `lastSeenCreatedAt` de l'auteur dans `notifState/{miffId}` (transaction) : déjà vue = **abandon silencieux**. Choix assumé : **au plus une fois** à l'envoi (en cas de plantage entre la décision et l'envoi, une notification peut être perdue ; le Feed la montre quand même) |
| **Bornes d'erreur** | Au plus **2 nouvelles tentatives** d'envoi FCM pour une erreur passagère, dans la même exécution, avec délai croissant ; jamais de relance de l'exécution entière (évite une notification retardée de plusieurs heures) |
| **Lectures par événement** | Liste des fiches de membres (1 lecture par membre, **30 au plus**) + `devices` de chaque destinataire (1 à 3 lectures, souvent 1) + `notifState` (1) ; pour une Miff de 4 : **≈ 8 lectures**, pour 15 : **≈ 30** ; **1 écriture** (`notifState`) |
| **Message** | Un envoi **par jeton** ; titre/corps par clé de texte (iOS) ou données (Android) ; prénom lu dans la fiche, tronqué (§4) ; identifiant de remplacement par auteur ; durée de vie 2 h ; niveau « passif » la nuit |
| **Journaux** | **Aucune donnée personnelle** : ni identifiant d'utilisateur, ni jeton, ni prénom, ni position, ni contenu de l'événement (qui contient la photo et ses coordonnées). Seulement : un compteur de destinataires examinés, envoyés, écartés **par motif** (code), les codes d'erreur FCM, la durée |
| **Abus** | Pas d'appel client à protéger (déclencheur Firestore, donc **App Check n'est pas applicable ici**) : l'abus passe par les écritures de photo, que **App Check strict sur Firestore** et le plafond horaire couvrent. Proposition à `backend-data` : exiger dans les règles **30 s minimum** entre deux photos du même auteur (comparaison avec `resource.data.createdAt`, **0 lecture de règles en plus**) pour transformer la limite du client en limite serveur ; sinon une boucle d'écritures coûte une invocation + 2 opérations par tour (budget d'alerte, §14) |
| **Aucune lecture de la photo** | La fonction ne décode, ne stocke ni ne copie le JPEG ni les coordonnées |
| **Mode dégradé** | Si la fonction est indisponible : aucune notification, **rien d'autre ne casse** (le Feed est la source de vérité) |

---

## 11. Côté Android

| Sujet | Règle [Proposé à `android-dev`] |
|---|---|
| **Messagerie** | Firebase Cloud Messaging ; messages **de type « données » à priorité haute**, l'app compose et affiche la notification (langue de l'appareil, pas d'affichage au premier plan, plafond quotidien, canal discret/nuit, ligne de centre) |
| **Canal** | « Nouvelles de ta Miff » (discret, sans son ni vibration), déjà prévu comme canal du catalogue ; version « calme » pour la nuit. Importance fixée à la création (`notifications.md` §8.1) |
| **Autorisation** | `POST_NOTIFICATIONS` (Android 13+), déjà demandée par l'écran d'explication des notifications ; Android 8 à 12 : pas de fenêtre |
| **OPPO Find X5 Lite (ColorOS, Android 13)** | Livraison **app balayée** et après « forcer l'arrêt » : **à mesurer** au prototype SRV-2 (non vérifié, l'étude le note). L'aide « Si les rappels n'arrivent pas » (batterie générique, **sans** demande directe d'exemption) couvre aussi ces notifications. Repli si les messages « données » sont retardés : message avec notification affichée par le système (à décider au vu de la mesure) |
| **App force-arrêtée** | Rien n'est livré tant que l'app n'est pas rouverte (comportement Android, étude §4) : dit sobrement dans l'aide, sans culpabiliser |
| **Premier plan** | App ouverte sur le Feed : aucune notification système ; ligne de centre silencieuse |
| **Au toucher** | Ouvre le Feed (Miff et carte de l'auteur) |
| **Manifeste** | Service de messagerie, permission ; mettre à jour le commentaire « pas de notification » |

## 12. Côté iOS

| Sujet | Règle [Proposé à `ios-dev`] |
|---|---|
| **Messagerie** | Notification « alerte » (pas silencieuse), texte résolu par le système depuis une clé de texte et un argument (le prénom) ; niveau d'interruption « passif » la nuit ; fil de regroupement ; identifiant de remplacement par auteur. Pas d'extension de service en v1 (elle ajoute une cible, un profil et de la signature en CI) |
| **Clé APNs `.p8`** | **Le PM la crée** sur son compte Apple Developer (Certificats, identifiants et profils > Clés, service Apple Push Notifications), la **télécharge une seule fois**, note l'identifiant de clé et l'identifiant d'équipe, et la **dépose lui-même** dans la console Firebase du projet **`miff-3d283`** (Paramètres du projet > Cloud Messaging > configuration de l'app Apple). **Jamais** dans le dépôt, dans un message, dans la CI ni donnée à un agent (`docs/dev/secrets.md`). Vérifier en haut de la console que le projet est « Miff », jamais Ghostrun. Une seule clé sert en principe au développement et à la production [À vérifier en recette] |
| **Capacité** | Activer **Push Notifications** sur l'identifiant `com.tomessa.miff` (Apple Developer) ; l'entitlement de l'app (`aps-environment`) est ajouté par `ios-dev` ; les **profils de signature de la CI** sont à régénérer (`docs/dev/apple-certificats-ci.md`). Le commentaire « pas de notifications » de `Miff.entitlements` est à mettre à jour |
| **TestFlight / production** | Utilisent l'environnement APNs de **production** [À vérifier, source non officielle, étude §4] ; test d'abord sur l'iPhone 11 Pro par TestFlight (aucune compilation locale, CI cloud) |
| **Premier plan** | Le Feed visible : aucune bannière ; sinon bannière discrète |
| **Au toucher** | Ouvre le Feed (Miff et carte de l'auteur) |
| **Centre local** | La ligne est inscrite **à l'ouverture suivante** à partir des notifications encore présentes dans le centre de notifications du système (même principe de réconciliation que N1 à N4) |
| **Limite** | Pas de plafond quotidien côté appareil (§7) ; une notification affichée ne peut pas être contrôlée avant son affichage |

---

## 13. Interaction avec les notifications locales et le centre

| Sujet | Règle |
|---|---|
| **Aucun doublon dans le tiroir** | Un seul message par auteur à l'écran (identifiant de remplacement) ; premier plan : rien. N1 à N7 sont **locales et pour soi** : aucune ne parle d'une update d'un autre |
| **Clôture N3 / N4** | Si Thomas publie après « Ta Miff est passée en rouge », la notification B5 **joue le rôle de clôture** sans l'avoir demandé. **Android** : à la réception, l'app peut retirer la N3/N4 encore affichée de cette Miff (« jamais d'alerte sans clôture », `notifications.md` §5.1.2) : amélioration facultative, v2. iOS : impossible avant affichage |
| **Centre de notifications** | **Garde une trace** : une ligne « {Prénom} a donné des nouvelles » (type B5, prénom et heure, **sans photo ni lieu**), rétention existante (30 jours / 100 éléments). Android : à la réception ; iOS : à l'ouverture suivante, depuis les notifications encore présentes (au mieux). Toucher = Feed. Q6 si le PM préfère ne pas en garder |
| **Publier annule-t-il quelque chose ?** | Pour l'auteur : oui, comme aujourd'hui, N1 à N4 locales annulées. **Aucun lien** avec la notification B5 envoyée aux autres |
| **Plafond des rappels locaux** | B5 ne compte pas dans les plafonds quotidiens de N1 à N6 (autre mécanisme, §7) |
| **Réglage** | Section « Pour tes proches » (wireframe `notifications.md` §9.1, ligne B5 passée de « OFF » à « ON » par défaut) |

---

## 14. Coûts et signaux de bascule

**Hypothèses (aucune n'est une mesure)** : Miffs de 4 membres (3 destinataires) ; **2 updates par utilisateur actif et par jour** (hypothèse haute : la photo est un geste volontaire) ; par update : **1 invocation, ≈ 8 lectures, 1 écriture** (§10) ; invocation de ≈ 300 ms à 256 Mio. Prix repris de l'étude §6 : Firestore Paris, lectures 0,036 $ et écritures 0,107 $ pour 100 000, quotas gratuits conservés sous Blaze (50 000 lectures et 20 000 écritures par jour, 2 millions d'invocations par mois). **FCM : gratuit.** Coût marginal de **cette** fonction (le reste de l'app est dans l'étude) :

| Utilisateurs actifs par jour | 100 | 1 000 | 10 000 |
|---|---|---|---|
| Updates (événements) par jour | 200 | 2 000 | 20 000 |
| Invocations par mois | ≈ 6 000 | ≈ 60 000 | ≈ 600 000 |
| Lectures par jour / par mois | 1 600 / 48 000 | 16 000 / 480 000 | 160 000 / 4,8 M |
| Écritures par jour / par mois | 200 / 6 000 | 2 000 / 60 000 | 20 000 / 600 000 |
| Notifications FCM par jour | ≈ 600 | ≈ 6 000 | ≈ 60 000 |
| **Coût mensuel marginal** | **0 $** | **≈ 0,2 $** (lectures) | **≈ 1,7 $** (lectures) **+ 0,6 $** (écritures) **≈ 2,4 $** |

Frais annexes de Blaze (images de fonction, journaux) **non chiffrés** (l'étude les situe à « quelques centimes »). **Sensibilité** : deux fois plus d'updates ou de membres = coût doublé. **Le coût dominant reste la lecture de l'app elle-même** (étude §6), pas cette fonction.

**Signaux de bascule** (le premier atteint ; à surveiller dans `docs/dev/surveillance-quotas.md`, à compléter par SRV-1) :
1. Facture Blaze > **5 $/mois** (alerte de budget suggérée : 50 / 90 / 100 %), ou toute alerte inattendue ;
2. Invocations de la fonction > **1 million/mois** ou erreurs FCM > 5 % des envois ;
3. Part de jetons « inconnus » > 10 % (nettoyage insuffisant) ;
4. ≈ 5 000 utilisateurs actifs/jour : revoir les lectures (mettre en mémoire la liste des membres, lister les destinataires dans un document dérivé).
**Risque résiduel à écrire** : une alerte de budget **ne plafonne pas** la dépense ; le plafond Preview de Cloud Functions ne couvre **pas** Firestore (étude §5). `maxInstances`, App Check strict et la règle des 30 s limitent l'exposition.

---

## 15. Sécurité, consentement, RGPD, textes légaux et stores

### Sécurité
- **Aucun secret côté client**, **aucune clé de compte de service** (identité gérée par Google) ; la clé APNs n'est que dans la console Firebase.
- **Qui reçoit** est décidé **par le serveur** à partir des fiches de membres au moment de l'événement, jamais d'une liste fournie par un téléphone ; pas de « sujets » FCM (un ancien membre continuerait à recevoir).
- **Une notification ne révèle pas l'appartenance à une Miff à un tiers** : jamais de nom de Miff, de lieu ni de nom de famille ; le prénom seul est visible à l'écran verrouillé, **comme pour N3 et N4** ; le recours est le réglage d'aperçu du téléphone (risque identique à celui déjà assumé par le PM pour les lieux, Q9 du 2026-11-08).
- **Jetons jamais lisibles par les autres membres** (règles et tests, §8.1). Le jeton seul ne permet pas d'envoyer sans identifiants serveur.
- **Phishing par le prénom** : texte fixe + prénom tronqué ; jamais de légende ni de texte libre de l'auteur dans la notification.
- **Identifiants dans le message** : identifiants techniques de la Miff et de l'auteur (pour ouvrir le bon écran) ; à déclarer comme transitant par les services de notification de Google et d'Apple.

### Consentement
- **Jeton** : la politique actuelle promet « nous t'en informerons dans l'app et nous te demanderons ton accord avant d'enregistrer un jeton ». Donc **pas d'enregistrement avant** l'écran d'explication corrigé + l'autorisation du téléphone (§9). L'écran d'explication des notifications (maquette M1 de `notifications.md` §8.3, texte actuel : « aucune notification n'est envoyée aujourd'hui ») est à **réécrire** : dire ce que la personne recevra (« tes proches qui donnent des nouvelles ») et que Miff enregistre l'identifiant de son téléphone pour cela.
- **Côté auteur** : l'aperçu de l'Update ajoute une ligne sobre « Tes proches seront prévenus » (clé `update_*` à créer par `product-specs`/design) ; transparence, pas de case à cocher (Q7).
- **Texte de consentement au partage** (version en vigueur) : mentionner la notification du cercle à chaque update, **dans la prochaine version du texte**, sans re-consentement forcé en v1 (Q7, risque assumé à écrire).

### Textes légaux et déclarations (PR SRV-3, `release-manager`)
| Document | À faire |
|---|---|
| `docs/legal/politique-confidentialite.fr.md` / `.en.md` (même PR, deux langues) | Art. 3 : **supprimer** « n'enregistre aucun jeton / aucune notification push » et décrire : jeton de notification (finalité : notifier ; base : contrat/consentement, retirable en coupant l'interrupteur ou dans les réglages du téléphone), fuseau horaire, préférence ; contenu du message (prénom, identifiants techniques, jamais photo ni position) ; mention des rappels locaux. Art. 5 (qui voit quoi) : « tes proches reçoivent une notification quand tu publies une update ». Art. 6 : **ajouter** Firebase Cloud Messaging et le service de notification push d'Apple au tableau des prestataires + **Cloud Functions** ; transferts hors UE (FCM, APNs : possible hors UE) ; région des fonctions Paris. Art. 7 : jeton supprimé à la déconnexion, à la coupure, à la suppression de compte, ou quand il devient invalide ; aucune conservation de l'historique des envois ; données de limitation supprimées sous 2 jours ; journaux sans donnée personnelle. Art. 11 : identifiants de notification. Art. 3, point « Preuves de consentement » : inchangé. Passer le numéro de version |
| CGU (`cgu.*.md`) | Les notifications « peuvent arriver en retard ou ne pas arriver » (déjà dans l'esprit) ; la notification n'est pas un service d'urgence |
| `docs/release/google-play-data-safety.md` | Déclarer l'identifiant d'appareil/jeton de notification (finalité : fonctionnalités de l'app) ; la qualification « collecté / partagé » est tranchée par `release-manager` selon la définition de Google. **Aucune affirmation de conformité** |
| `docs/release/app-store-confidentialite-et-revue.md` (étiquettes App Privacy) | Idem pour Apple ; capacité Push Notifications ; note de revue |
| `docs/release/README.md` (V7 « aucun jeton ») et `alignement-decisions-2026-11-11.md` (A8) | V7 devient « jeton enregistré uniquement après autorisation, supprimé à la déconnexion » |
| **Fiches des stores** (`marketing/store-listing-ios.md`, `store-listing-android.md`, `docs/release/changements-testeurs-externes.md` point 7, `marketing/screenshots-brief.md`, `marketing/launch-plan.md`) | Retirer « pas de notification » / « tes proches ne sont pas notifiés » **le jour de la livraison de SRV-4, pas avant** (ne rien promettre ni nier qui ne soit livré) ; ne jamais écrire « tu seras toujours prévenu » : le push est un accélérateur sans garantie (étude §4) |

**Risques assumés, jamais « à valider »** (décision du PM du 2026-10-23, pas de relecture juridique) : traitement hors UE possible par FCM/APNs ; mineurs possibles (une notification sur un mineur vers son cercle) ; preuve du consentement au jeton non conservée après suppression du compte.

---

## 16. Actions du PM (rien n'est fait par un agent)

| # | Action | Quand |
|---|---|---|
| P1 | **Alertes de budget** Blaze (suggestion : 5 $/mois, alertes à 50 / 90 / 100 %, e-mail du PM) ; décider du plafond Preview sur Cloud Functions | Avant SRV-1 |
| P2 | Vérifier le **compte de facturation** (3 tâches planifiées partagées avec Ghostrun si le même) ; confirmer le projet **`miff-3d283`** dans toute console | Avant SRV-1 |
| P3 | **Clé APNs `.p8`** + capacité Push Notifications + régénération des profils CI (§12) | Avant SRV-2 iOS |
| P4 | Activer la **durée de vie (TTL)** sur `notifState.expireAt` dans la console | Avant SRV-4 |
| P5 | **« Oui » explicite** à chaque déploiement : règles `devices` (SRV-2), fonction (SRV-4), site légal (SRV-3). Rien n'est déployé depuis la CI | Chaque étape |
| P6 | Vérifier avant la livraison qu'aucune fiche de store ne dit encore « pas de notification » | SRV-3 / SRV-4 |

---

## 17. Découpage en PR

Process par ticket (CLAUDE.md) : une branche et une PR par plateforme, Android avant iOS, jamais de push direct sur `main`, revue en contexte neuf + revue sécurité, recette PM, fusion sur « oui » explicite. **Maquettes** : à demander au PM avant le développement (réglage, écran d'explication corrigé, formats de notification) ; ligne d'aperçu de l'Update à ajouter par le design.

| # | PR | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| **SRV-1** | Socle Functions + CI + garde-fous de coût (`backend-data`) | `backend/functions/` (région, génération, compte de service dédié, `maxInstances`, tests sur émulateur, **aucun déploiement dans la CI**), mise à jour de `data-model.md` (§2.10, §12, §13, nouvelle `notifState`), `secrets.md`, `surveillance-quotas.md` (indicateurs de §14), prototype de livraison FCM | M | P1, P2 ; réponses du PM à §22 |
| **SRV-2R** | Règles `devices` (`backend-data`) | Extension de la liste de clés, `tz`, `notify`, tests (y compris « un autre membre ne lit jamais un jeton »), règle des 30 s entre deux photos (si retenue), déploiement sur « oui » | S | SRV-1 |
| **SRV-2A** | Jetons : Android (`android-dev`) | Enregistrement après autorisation, rafraîchissement, suppression (déconnexion, coupure, suppression de compte, SESS), `tz`, `notify` ; écran d'explication corrigé ; réception **sans envoi réel** (message de test) ; mesure OPPO | M | SRV-2R |
| **SRV-2I** | Jetons : iOS (`ios-dev`) | Idem ; capacité Push, `aps-environment`, profils CI ; TestFlight sur l'iPhone 11 Pro | M | SRV-2R, P3 |
| **SRV-3** | Textes légaux et déclarations (`release-manager`) | §15, FR + EN dans la même PR ; **la fiche de store n'est modifiée qu'à la livraison** | S | Parallèle de SRV-2 ; publié avant l'activation |
| **SRV-4B** | La fonction B5 (`backend-data`) | §5 à §10, tests d'émulateur (vecteurs de §19), déploiement **sur « oui »** (c'est l'interrupteur de mise en service) | M | SRV-2A et SRV-2I fusionnées, SRV-3 prête, P4 |
| **SRV-4A / SRV-4I** | Réglage « Quand un proche donne des nouvelles », réception complète, ligne de centre, ligne « Tes proches seront prévenus » (`android-dev`, puis `ios-dev`) | §4, §6, §11 à §13 | M chacune | SRV-2A / SRV-2I |
| **Recette** | `qa-tester` puis recette PM sur l'OPPO et l'iPhone (2 comptes de test, un appareil par compte, SESS) | §20 | S | SRV-4 |

Ordre conseillé : SRV-1, SRV-2R, SRV-2A (+ SRV-3), SRV-2I, SRV-4A et SRV-4I, **déploiement de SRV-4B en dernier**, recette. Tant que la fonction n'est pas déployée, les apps enregistrent des jetons mais **rien n'est envoyé**.

---

## 18. Critères d'acceptation (testables)

**Destinataires et contenu**
- [ ] **U1** Tomessa publie ; Amandine (partage activé, interrupteur activé, autorisation accordée, app fermée) reçoit « Tomessa a donné des nouvelles » (FR) ou « Tomessa checked in » (EN, selon la langue du téléphone) en moins de **2 minutes** dans les conditions normales.
- [ ] **U2** Tomessa **ne reçoit pas** de notification de sa propre update.
- [ ] **U3** Un membre dont le **partage est arrêté**, un membre **retiré** ou **parti** avant la publication, un membre dont **l'interrupteur est coupé**, un membre sans autorisation ou déconnecté : **aucune** notification.
- [ ] **U4** La notification ne contient **ni photo, ni légende, ni position, ni nom de lieu, ni nom de famille, ni nom de la Miff** (inspection du message envoyé dans l'émulateur et de l'écran verrouillé sur appareil).
- [ ] **U5** Prénom de plus de 20 caractères : tronqué avec « … ».
- [ ] **U6** Toucher la notification ouvre le Feed sur la carte de l'auteur ; si le compte est dans plusieurs Miffs, la bonne Miff est affichée sans erreur.
- [ ] **U7** App ouverte sur le Feed : **aucune** notification système ; le Feed se met à jour ; une ligne de centre silencieuse existe.
- [ ] **U8** Mode voyage : l'auteur en voyage notifie normalement ; un destinataire en voyage reçoit normalement ; aucun texte ne mentionne le voyage.

**Plafonds et nuit**
- [ ] **P1** Deux updates du même auteur dans la même heure et la même Miff : **une seule** notification ; la seconde est visible dans le Feed.
- [ ] **P2** Deux Miffs communes à l'auteur et au destinataire (Premium) : un seul message **visible** (le second remplace le premier).
- [ ] **P3** Deux auteurs : les deux notifications sont regroupées par le système.
- [ ] **P4** Entre 22 h et 7 h (heure du destinataire, y compris après changement de fuseau) : livraison **sans son, sans vibration, sans allumer l'écran**.
- [ ] **P5** Un message non livré après 2 h n'apparaît plus.
- [ ] **P6** Android : au-delà de 6 par jour, plus de notification système, ligne de centre silencieuse.

**Jetons**
- [ ] **J1** Aucun jeton enregistré avant l'autorisation du téléphone **et** l'écran d'explication.
- [ ] **J2** Déconnexion, suppression de compte, interrupteur général coupé, autorisation retirée : le document `devices` disparaît (vérifié en base) ; le jeton est invalidé chez FCM.
- [ ] **J3** Connexion sur un second appareil du même compte : un seul document `devices` subsiste (celui du nouvel appareil).
- [ ] **J4** Réponse « jeton inconnu » de FCM : le document est supprimé par le serveur.
- [ ] **J5** Règles : un autre membre, le créateur d'une Miff ou un visiteur **ne lisent ni n'écrivent** `devices` d'autrui ; `notifState` n'est lisible ni écrivable par aucun client ; clés inconnues refusées.

**Fonction**
- [ ] **F1** Un événement rejoué (livraison en double) n'envoie **qu'une** notification.
- [ ] **F2** La suppression d'une photo (expiration, arrêt du partage, départ) n'envoie rien.
- [ ] **F3** Une Miff de 15 membres : au plus **≈ 30 lectures** et **1 écriture** par événement (mesuré à l'émulateur) ; une Miff de 40 membres : la fonction s'arrête à 30 fiches et 20 destinataires.
- [ ] **F4** Les journaux de la fonction ne contiennent aucun identifiant d'utilisateur, jeton, prénom, coordonnée ni contenu d'événement (test sur le texte des journaux).
- [ ] **F5** Échec FCM passager : au plus 2 nouvelles tentatives dans la même exécution, aucune relance de l'exécution.
- [ ] **F6** Fonction indisponible : la publication réussit quand même, le Feed est à jour.

**Légal, coûts, processus**
- [ ] **L1** Les quatre textes (politique FR/EN, CGU FR/EN) ne contiennent plus « n'enregistre aucun jeton » ni « aucune notification push » ; les fiches de stores sont mises à jour **à la livraison**.
- [ ] **L2** Data safety et App Privacy citent le jeton de notification (formulation de `release-manager`).
- [ ] **C1** Alerte de budget active avant le premier déploiement ; `maxInstances` posé ; aucune clé JSON dans le dépôt, la CI ou les journaux (balayage de secrets).
- [ ] **C2** Aucun événement Analytics sur les notifications ; aucune position dans un événement ou un journal.
- [ ] **C3** Tous les déploiements (règles, fonction, site) ont été précédés d'un « oui » explicite du PM ; commandes avec `--project miff-3d283`.

---

## 19. Vecteurs de test (émulateur Firestore + Functions, FCM simulé)

Membres A (auteur), B, C, D ; tous partagent sauf mention ; interrupteurs activés ; un appareil chacun.

| # | Situation | Résultat attendu |
|---|---|---|
| V1 | A publie à 14:00 | B, C, D notifiés ; A non |
| V2 | C a `sharing.enabled = false` | B, D notifiés ; C non |
| V3 | D a `notify.updates = false` | B, C notifiés |
| V4 | B n'a aucun document `devices` | C, D notifiés |
| V5 | A publie à 14:00 puis 14:40 | une seule notification (14:00) ; à 15:05 une nouvelle publication notifie |
| V6 | Même événement livré deux fois | une seule notification |
| V7 | Suppression de `photos/A` | aucun envoi |
| V8 | D est retiré de la Miff à 13:59 | D non notifié |
| V9 | FCM répond « jeton inconnu » pour B | document de B supprimé ; C, D notifiés |
| V10 | A publie à 23:30, B en `Europe/Paris`, D en `America/New_York` | B : livraison calme ; D : selon son heure locale |
| V11 | Événement dont la photo a plus de 5 min au traitement | aucun envoi |
| V12 | Miff de 40 membres | lecture bornée, 20 destinataires au plus |
| V13 | A dans deux Miffs avec B ; publie une fois | deux messages, même identifiant de remplacement |

---

## 20. Plan de recette PM (OPPO Find X5 Lite + iPhone 11 Pro, deux comptes de test)

| # | Étape | Attendu |
|---|---|---|
| 1 | Compte 1 sur l'OPPO, compte 2 sur l'iPhone, même Miff, partage activé des deux côtés ; iPhone : accepter les notifications | Réglage « Quand un proche donne des nouvelles » activé |
| 2 | L'iPhone est verrouillé, l'app fermée ; le compte 1 publie | Notification discrète sur l'écran verrouillé, sans photo ni lieu |
| 3 | Idem dans l'autre sens (l'OPPO est verrouillé, app balayée) | Notification reçue ; noter le délai |
| 4 | Le compte 1 publie une 2e fois dans l'heure | Pas de seconde notification ; la photo est dans le Feed |
| 5 | L'iPhone coupe l'interrupteur, le compte 1 publie (1 h plus tard) | Rien sur l'iPhone |
| 6 | Le compte 2 arrête son partage, le compte 1 publie | Rien |
| 7 | Déconnexion du compte 2 puis publication du compte 1 | Rien ; `devices` vide pour le compte 2 |
| 8 | Toucher la notification | Feed sur la carte de l'auteur |
| 9 | OPPO : « forcer l'arrêt » de Miff puis publication | Rien tant que l'app n'est pas rouverte (attendu) ; noter |
| 10 | Nuit simulée (changer l'heure du téléphone ou le fuseau) | Livraison sans son |
| 11 | Console Firebase (projet Miff) : invocations, erreurs, coût du jour | Dans la fourchette de §14 |

---

## 21. Risques

| # | Risque | Gravité | Mitigation |
|---|---|---|---|
| R1 | **Surveillance / stalkerware** : la notification pourrait servir à « fliquer » | Moyenne | Elle ne part que d'un geste volontaire de l'auteur ; aucune notification d'absence ; réciprocité du partage ; aucun envoi à un non-membre ; jamais de position ni de photo. Les notifications d'absence (B1) sont un ticket séparé, avec leur propre examen du ton |
| R2 | **Bruit / fatigue** | Moyenne | Plafond horaire, remplacement par auteur, regroupement, discret, nuit calme, interrupteur visible, 6 par jour sur Android |
| R3 | **Jetons obsolètes** (désinstallation, réinstallation) | Faible | Suppression sur erreur FCM, rafraîchissement à 30 jours, option de purge à 60 jours |
| R4 | **Coût** non plafonné | Moyenne | Alertes de budget, `maxInstances`, règle des 30 s, App Check strict, surveillance (§14) ; le budget n'est qu'une alerte |
| R5 | **Livraison non garantie** (OPPO, app forcée à l'arrêt, économie d'énergie) | Moyenne | Dit honnêtement dans l'aide ; le Feed reste la source de vérité ; mesure sur l'OPPO avant d'annoncer |
| R6 | **Promesse non tenue** dans les stores et la politique | Haute | SRV-3 avant l'activation ; fiches modifiées le jour de la livraison ; V7 de la checklist de release mis à jour |
| R7 | **Fausse réassurance** : la notification « tout va bien » alors que c'est le téléphone d'un tiers | Faible | Texte neutre (pas « tout va bien »), même limite que la photo elle-même |
| R8 | **Fuite du jeton ou de la clé APNs** | Haute | Clé uniquement en console, jamais dans le dépôt ; jetons privés ; pas de clé de service |
| R9 | **Écran verrouillé** : prénom visible par un tiers | Faible | Prénom seul, réglage d'aperçu du téléphone, même risque que N3/N4 |
| R10 | **Fuseau erroné** : notification bruyante la nuit | Faible | Fuseau rafraîchi à chaque changement ; discret par défaut de toute façon (Q2) |

---

## 22. Questions pour le PM (8 au plus, valeur par défaut recommandée)

**Q1. Interrupteur et défaut.** Le réglage « Quand un proche donne des nouvelles » est-il **activé par défaut** (dès que le téléphone autorise les notifications) ou **désactivé** (comme l'ancienne ligne B5 du catalogue) ?
- *Reco* : **activé par défaut**. C'est ce que tu demandes ; l'autorisation du téléphone reste le vrai accord ; le plafond horaire limite le bruit.

**Q2. Son et nuit.** La notification doit-elle être **toujours discrète** (ni son ni vibration) ou **sonore le jour** ? Heures calmes **22 h – 7 h** (livrée sans réveiller l'écran la nuit) ?
- *Reco* : **toujours sans son ni vibration**, nuit livrée en mode passif. Cohérent avec la grille de priorité (B5 = discret) et « sans déranger ».

**Q3. Plafond anti-spam.** **Une notification par auteur, par Miff et par heure** (les updates suivantes restent dans le Feed) ?
- *Reco* : **oui, 1 par heure** (valeur modifiable sans nouvelle version de l'app ; on la règle avec les premières mesures de la bêta).

**Q4. Texte.** « {Prénom} a donné des nouvelles » / « Retrouve-les dans ta Miff. », prénom seul tronqué à 20 caractères, **sans nom de Miff, sans lieu, sans nom de famille** ; à lire à voix haute. Aligner B2 sur cette formule plus tard ?
- *Reco* : **oui** aux deux.

**Q5. Voyageur.** Un membre en **mode voyage** qui publie une update **notifie-t-il** son cercle, et un membre en voyage **reçoit-il** les notifications ?
- *Reco* : **oui aux deux** (geste volontaire, il lit les autres normalement) ; aucune mention du voyage dans la notification.

**Q6. Centre de notifications.** Garde-t-on **une ligne** « {Prénom} a donné des nouvelles » dans le centre local (Android à la réception, iOS à l'ouverture suivante, au mieux) ?
- *Reco* : **oui**, pour tenir l'exigence « toutes consultables dans le centre » ; sans photo ni lieu.

**Q7. Consentement et transparence.** Suffit-il de (a) la ligne « Tes proches seront prévenus » dans l'aperçu de l'Update, (b) l'écran d'explication corrigé pour le jeton, (c) la politique mise à jour, **sans re-demander le consentement au partage** aux comptes existants (le texte de consentement citera la notification à sa **prochaine** version) ?
- *Reco* : **oui, sans re-consentement forcé en v1**, risque assumé écrit (décision du 2026-10-23) ; la publication reste un geste explicite.

**Q8. Réglage par Miff.** Un interrupteur **global** en v1, ou un par Miff dès maintenant (utile surtout en Premium, plusieurs Miffs) ?
- *Reco* : **global en v1** ; le réglage par Miff viendra avec les autres granularités Premium (`notifications.md` §6).

**Décisions déjà posées par défaut dans ce ticket (à contester si besoin, sans question dédiée)** : jetons jamais lisibles par d'autres membres ; envoi par jeton et jamais par sujet ; pas de nom de Miff dans la notification ; fonction « au plus une fois » (une perte est préférée à un doublon) ; fonction planifiée de purge à 60 jours repoussée en v2 ; plafond quotidien de 6 tenu sur Android seulement.
