# Miff — modèle de données Firestore (source de vérité)

Projet Firebase : `miff-3d283` — Firestore `europe-west9` (Paris), édition Standard, plan Spark aujourd'hui.
Ce document est la référence unique pour `ios-dev` et `android-dev` : même structure, mêmes noms de champs, mêmes batches. Toute évolution passe par `backend-data`.

**Statut de validation** : les règles (`firestore.rules`) sont testées dans l'émulateur Firestore (`rules-test-plan.md`, résultats dans `rules-test-report.md`) mais **rien n'est déployé** : le déploiement reste soumis à la procédure du ticket `docs/tickets/durcissement-regles-firestore.md` (§8.2 : revue de sécurité en contexte neuf, OK explicite du PM). Ce document ne prétend à aucune conformité juridique : il décrit ce que le modèle implique (§11).

Spécifications sources : `docs/specs/*.md` (statut "Backlog discovery", donc non validées). Les hypothèses prises là où une spec est muette sont marquées **[Hypothèse]** et listées en §15.

## Ce qui change : durcissement des règles (ticket `docs/tickets/durcissement-regles-firestore.md`)

**DUR-1 (appliqué dans cette version)** : une Miff supprimée ne peut plus renaître, et son code secret disparaît avec elle.
- Nouvelle collection **`miffsDeleted/{miffId}`** (« pierre tombale », §2.11) : créée par le créateur dans le lot qui supprime la Miff ; la création d'une Miff est refusée si la pierre tombale de son identifiant existe.
- La suppression d'une Miff (§2.5, §8) exige, dans le **même lot**, la pierre tombale **et** la suppression de `joinCodes/{joinCode}`.
- Constat de la mise en œuvre : le contrôle de la pierre tombale est placé sur le document `miffs/{id}` lui-même, pas seulement sur la fiche membre du créateur (le ticket l'esquissait dans `ownerCreateOk`). Un créateur qui a gardé sa fiche membre après la suppression pouvait sinon recréer la Miff sans écrire de fiche (test H1f).

*Impact PM* : un lot et une écriture de plus à la suppression d'une Miff (négligeable devant les quotas gratuits) ; aucun effet visible pour l'utilisateur.

**DUR-2 (appliqué dans cette version)** : réciprocité réelle, retrait avec nouveau code, photos, Miff supprimée illisible.
- **Statuts : il faut avoir publié son propre statut** (ATK3, décision PM) : la lecture de `status` (get et liste) exige que `status/{moi}` existe, en plus du partage activé. **Activer le partage = créer son statut dans le même lot** (valeur initiale `presence = "unknown"`, §8). **Photos : réciprocité inchangée** (décision PM) : le partage activé suffit, un membre qui n'a pas publié son statut peut donc lire les photos des autres.
- **Retirer un membre renouvelle le code** (ATK4, décision PM) : le lot de retrait contient obligatoirement le changement de `miffs.joinCode`, la création du nouveau `joinCodes` et la suppression de l'ancien (§5, §8). Le départ volontaire ne change rien (décision PM, Q1).
- **Miff supprimée = illisible** (complément ATK1) : `places`, `members`, `status`, `photos` ne sont plus lisibles dès que `miffs/{id}` n'existe plus, même pour un survivant dont la fiche existe encore. `get miffs/{id}` renvoie « introuvable » (pas une erreur) : c'est le signal de nettoyage pour l'app.
- **Le code d'une Miff pointe vers cette Miff** (revue de sécurité) : à la création et au renouvellement, `joinCodes/{code}` doit exister après le lot **et** avoir `miffId` = cette Miff (on ne peut pas se donner le code d'une autre Miff). Tests H7a à H7c.
- **Photos** : illisibles après `expiresAt` (appliqué par les règles, sans nettoyage nécessaire) ; `expiresAt` au plus +25 h (24 h + 1 h de marge) à la création, **identique pour tous les comptes, premium ou non** ; l'auteur, le créateur et **tout membre** (si la photo est expirée) peuvent la supprimer.

*Impact PM* : un membre qui active son partage voit les autres dès que son propre statut est publié (instantané dans l'usage normal) ; retirer quelqu'un oblige le créateur à repartager un nouveau code ; coût : environ 1 à 2 lectures de règles de plus par ouverture de flux (§12).

**AUTH-B (consentements à l'inscription, appliqué dans cette version, ticket `docs/tickets/authentification.md` §8, §10, §15)** : deux consentements distincts sur `users/{uid}`, décidés par le PM le 2026-10-23.
- **(a) Conditions de service et politique de confidentialité : obligatoire.** Inchangé dans son principe : `termsAcceptedAt` (heure serveur) et `termsVersion` sont écrits à la création du profil et **sans eux le profil est refusé**. Nouveauté : **ces deux champs ne sont plus modifiables après la création** (décision PM : « non modifiable »). Les règles précédentes (non déployées) autorisaient leur mise à jour ; le ticket futur « nouvelle version des CGU » devra rouvrir cette règle explicitement.
- **(b) Statistiques d'usage et rapports de plantage anonymes : facultatif, décoché par défaut.** Nouveau bloc facultatif `analyticsConsent` (§2.1) : état, date de consentement (heure serveur), version du texte, date de retrait. **Absence du bloc = pas de consentement.** Modifiable ensuite par l'utilisateur lui-même seulement (Réglages) : activer, retirer, réactiver ; la version ne peut pas reculer.
- Aucune date de naissance, aucun âge, aucun e-mail : toute autre clé est refusée (déjà le cas, maintenant testé : AB17).
- Un utilisateur social qui **refuse** le panneau de consentement n'a **aucun document** : le client n'écrit rien dans Firestore avant l'acceptation, puis supprime le compte d'authentification (ticket §7.8). Les règles interdisent déjà de créer un profil sans (a).

*Impact PM* : aucun coût supplémentaire (la lecture du profil qui sert au routage au lancement lit aussi le consentement ; la création ou la mise à jour du profil n'ajoute aucune lecture de règles) ; la preuve du consentement (b) est **supprimée avec le profil** (§10) ; l'horodatage serveur est celui de la **création du profil**, pas du clic (point RGPD assumé par le PM, §11).

**À venir (DUR-3), non appliqué ici** : App Check strict, signal de bascule vers Blaze, garde-fous de projet, mesure de lectures complétée, extension des mutations.

---

## 1. Décisions de modélisation clés (en une phrase chacune)

| Décision | Impact pour le PM |
|---|---|
| **On ne stocke jamais de coordonnées de l'utilisateur**, seulement un état ("dans la Maison", "hors lieu", "zone à risque") + horodatages. La position GPS reste sur le téléphone. | Beaucoup moins de données sensibles à protéger/justifier ; pas d'historique de trajet possible ; le feed ne coûte presque rien. |
| **Pas d'historique** : un seul document "état courant" par membre et par Miff, écrasé à chaque événement. | Coût stable, conforme à l'esprit "sans être intrusif" (spec hors-scope : pas de journal). |
| **Le niveau vert/orange/rouge est calculé par l'app** à partir des horodatages, pas stocké. | Fonctionne même si le téléphone du proche est éteint ; zéro Cloud Function pour afficher l'escalade. En revanche, **envoyer une notification quand l'app est fermée exige Blaze** (§13). |
| **Consentement par membre et par Miff**, enregistré (date + version du texte), révocable ; révoquer **supprime** l'état et la photo. | Répond à l'exigence anti-stalkerware des stores et à la traçabilité du consentement. |
| **Réciprocité** : on ne voit les statuts des autres que si l'on a activé son partage **et publié son propre statut** (pour les photos : partage activé suffit, décision PM). | Évite la surveillance à sens unique. **[Hypothèse]** à confirmer (Q6). |
| **Le premium vit dans `entitlements/{uid}`, écriture interdite au client.** | Impossible de se déclarer premium soi-même. Sans serveur (Spark), le PM l'accorde à la main dans la console pour les testeurs. |
| **Limites freemium appliquées par les règles** avec des "slots" à identifiant fixe (§6), sans Cloud Function. | Marche sur Spark. Failles résiduelles listées §6. |
| **Code secret dans `joinCodes/{code}`**, sans `list`, 12 caractères sur 31 symboles (~59 bits). | Non énumérable. Le brute force n'est pas impossible mais irréaliste ; App Check le rend encore plus coûteux (§5). |
| **Photos stockées dans Firestore** (≤ 150 Ko, durée de vie courte), car Cloud Storage exige Blaze. | Évite Blaze pour les photos, mais c'est le poste qui consomme le plus de bande passante (§12). Décision PM (Q2). |

---

## 2. Arborescence et schéma

```
users/{uid}                              profil privé (soi uniquement)
  memberships/{key}                      index "mes Miffs" + slot freemium
  devices/{deviceId}                     jetons FCM (uniquement avec le push -> Blaze)
entitlements/{uid}                       statut premium (lecture seule côté client)
joinCodes/{code}                         résolution du code secret -> miffId
miffsDeleted/{miffId}                    pierre tombale d'une Miff supprimée (jamais lisible par un client)
miffs/{miffId}                           le cercle
  members/{uid}                          profil (copie), rôle, CONSENTEMENT
  status/{uid}                           ÉTAT COURANT (sensible), existe seulement si partage activé
  photos/{uid}                           photo de la dernière update (sensible), durée de vie courte
  places/{placeId}                       lieux : zone de confiance / zone à risque
```

Conventions : `{uid}` = UID Firebase Auth. `miffId` = ID auto Firestore (20 caractères alphanumériques, imposé par les règles). Tous les horodatages sont des `Timestamp` Firestore. **Écrire toutes les clés d'un document à chaque écriture** (valeur `null` si non applicable) : les règles exigent le jeu de clés complet et refusent les clés inconnues.

### 2.1 `users/{uid}` — profil privé
| Champ | Type | Note |
|---|---|---|
| `firstName`, `lastName` | string 1–50 | Obligatoires (spec inscription). La règle UX "3 caractères minimum" n'est pas imposée côté serveur (Q15). |
| `avatar` | bytes ≤ 20 000 octets, optionnel | JPEG carré ~160×160, qualité ~70 (≈ 8–15 Ko). **Sensible** (visage). |
| `createdAt` | timestamp | = heure serveur. **Immuable.** |
| `termsAcceptedAt`, `termsVersion` | timestamp, string 1–20 | **(a)** Preuve d'acceptation des CGU/politique de confidentialité. **Obligatoires** à la création (pas de profil sans eux) ; `termsAcceptedAt` = heure serveur ; **immuables** après la création (décision PM, AUTH-B). `termsVersion` : identifiant de la version publiée des textes (les règles n'en imposent pas le format). |
| `analyticsConsent` | map, **facultatif** | **(b)** Consentement aux statistiques d'usage et rapports de plantage anonymes. Absent = pas de consentement. Voir ci-dessous. |

Lisible et modifiable **uniquement** par son propriétaire. Les autres membres voient la copie dans `members/{uid}`. Pas de champ premium ici (volontairement). **Aucune date de naissance, aucun âge, aucun e-mail, aucun téléphone** : toute autre clé est refusée par les règles.

**Bloc `analyticsConsent`** (mêmes quatre clés que `members.sharing`, §2.6 ; **toujours les quatre clés**, `null` si non applicable ; seul ce bloc et le profil changent à la mise à jour) :
| Clé | Type | Règle |
|---|---|---|
| `enabled` | bool | `true` = l'utilisateur a accepté la collecte (statistiques **et** plantages : une seule case, ticket §8) ; `false` = refusé ou retiré. |
| `consentedAt` | timestamp \| null | **Heure serveur** (`serverTimestamp()`) à l'activation ; `null` tant que jamais accepté ; **conservé tel quel au retrait** (trace du dernier consentement). |
| `consentVersion` | string \| null | Version du **texte de la case (b)** affiché, au format `AAAA-MM-vNN` avec zéros de tête (ex. `2026-10-v01`), `null` tant que jamais accepté ; conservée au retrait. Le format fixe rend l'ordre alphabétique égal à l'ordre chronologique : **la version ne peut pas reculer** (voir transitions). Constante partagée iOS/Android, à faire évoluer avec le texte. |
| `revokedAt` | timestamp \| null | `null` tant que le consentement est actif ou n'a jamais été donné ; **heure serveur** du retrait ensuite. |

Valeurs autorisées (état = `enabled`) et transitions (tout le reste est refusé) :
| État | Bloc | Écriture autorisée |
|---|---|---|
| Jamais demandé / compte ancien | **bloc absent** | création du profil sans bloc ; ou plus tard activation (ci-dessous) |
| Refusé à l'inscription | `{enabled:false, consentedAt:null, consentVersion:null, revokedAt:null}` | à la création du profil ; activation possible ensuite |
| Accepté à l'inscription | `{enabled:true, consentedAt:<heure serveur>, consentVersion:"AAAA-MM-vNN", revokedAt:null}` | à la création du profil, avec `termsAcceptedAt` (même instant) |
| Activer / réactiver (depuis absent, refusé ou retiré) | idem « accepté » ; `consentVersion` **≥ la dernière version enregistrée** (égale ou plus récente, jamais plus ancienne) | `update` du profil |
| Retirer (depuis accepté **uniquement**) | `{enabled:false, consentedAt:<inchangé>, consentVersion:<inchangée>, revokedAt:<heure serveur>}` | `update` du profil |
| Réécrire un consentement actif (autre version, nouvelle date) sans le retirer d'abord | — | **refusé** (une nouvelle version du texte = un parcours futur, hors périmètre) |
| Supprimer le bloc par `update` | — | **refusé** (le retrait passe par la transition de retrait, qui laisse une trace) |

Qui peut écrire : **le propriétaire seul**, y compris pour la lecture (`get`) ; un autre membre, le créateur de la Miff ou un visiteur ne peuvent ni lire ni écrire ce bloc. L'état du consentement (b) est **par compte** ; l'appareil garde en plus son propre état local qui commande réellement la collecte (voir §8).

### 2.2 `users/{uid}/memberships/{key}` — "mes Miffs" + slot freemium
| Champ | Type |
|---|---|
| `miffId` | string (ID de Miff) |
| `joinedAt` | timestamp (heure serveur) |

`{key}` = `"free"` pour le slot gratuit (un seul possible), ou `miffId` pour un utilisateur premium. Pour lister ses Miffs : lire tous les documents de ce sous-ensemble et utiliser le champ `miffId` (jamais l'ID du document). Immuable (pas d'update). Suppression autorisée seulement si le document membre n'existe plus.

### 2.3 `entitlements/{uid}` — premium (écrit par le serveur / la console uniquement)
| Champ | Type | Note |
|---|---|---|
| `active` | bool | |
| `expiresAt` | timestamp | Les règles vérifient `expiresAt > maintenant` : un abonnement expiré cesse de fonctionner sans fonction. |
| `source` | string | `manual` / `app_store` / `google_play` (informatif) |
| `updatedAt` | timestamp | |

Le client peut lire son propre document (pour afficher l'état premium) et rien d'autre. Exemple :
```json
{ "active": true, "expiresAt": "2026-12-01T00:00:00Z", "source": "manual", "updatedAt": "2026-10-16T12:00:00Z" }
```

### 2.4 `joinCodes/{code}` — code secret
`{code}` = 12 caractères parmi `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (31 symboles, sans I/L/O/0/1), généré **côté client par un générateur cryptographique** (SecRandomCopyBytes / SecureRandom, avec rejet pour éviter le biais modulo).
| Champ | Type |
|---|---|
| `miffId` | string |
| `miffName` | string 1–40 (pour afficher "Rejoindre la Miff « Famille »" avant de s'engager) |
| `createdAt` | timestamp |

Pas de `list`. `get` exige de connaître le code entier. Ne contient ni le créateur ni les membres.

### 2.5 `miffs/{miffId}`
| Champ | Type | Note |
|---|---|---|
| `ownerUid` | string | **Immuable.** Le créateur gère lieux, code, retraits. |
| `name` | string 1–40 | **[Hypothèse]** : un nom existe (le sélecteur de Miff en a besoin). |
| `joinCode` | string (12) | Visible de tous les membres **[Hypothèse]** (action "Ajouter un proche", Q7). |
| `createdAt` | timestamp | |

Lisible par les membres uniquement (`get` ; pas de `list`). Modifiable par le créateur : `name`, `joinCode` (régénération).

**Création** : refusée si `miffsDeleted/{miffId}` existe (un identifiant supprimé ne sert plus jamais). **Suppression** (créateur) : refusée si, dans le même lot, la pierre tombale `miffsDeleted/{miffId}` n'est pas créée **ou** si `joinCodes/{joinCode}` existe encore après le lot.

### 2.6 `miffs/{miffId}/members/{uid}` — profil (copie), rôle, consentement
| Champ | Type | Note |
|---|---|---|
| `role` | `"owner"` \| `"member"` | Immuable. |
| `firstName`, `lastName`, `avatar` | comme `users` | **Copie** (dénormalisation : évite N lectures de profils). Le client met à jour toutes ses copies quand le profil change. |
| `joinedAt` | timestamp | Heure serveur, immuable. |
| `joinCode` | string | Présent seulement pour `role = member` : code utilisé pour rejoindre (preuve exigée par les règles). Immuable. |
| `sharing.enabled` | bool | Le membre partage-t-il sa position avec CETTE Miff ? |
| `sharing.consentedAt` | timestamp \| null | Date du consentement explicite (heure serveur). |
| `sharing.consentVersion` | string \| null | Version du texte de consentement affiché (ex. `"2026-10-v1"`). |
| `sharing.revokedAt` | timestamp \| null | Date de révocation, `null` si actif. |

Exemple :
```json
{
  "role": "member", "firstName": "Amandine", "lastName": "Roy", "joinedAt": "2026-10-16T09:00:00Z",
  "joinCode": "K7M2QX9PDW4H",
  "sharing": { "enabled": true, "consentedAt": "2026-10-16T09:00:00Z", "consentVersion": "2026-10-v1", "revokedAt": null }
}
```
Lisible par tous les membres de la Miff, tant que la Miff existe (c'est la liste des membres). Un membre ne modifie que son profil et son consentement.

### 2.7 `miffs/{miffId}/status/{uid}` — état courant (SENSIBLE)
Un seul document par membre et par Miff, **écrasé**. N'existe que si `members/{uid}.sharing.enabled = true`. **Lecture (get et liste)** : seulement si la Miff existe, que j'ai activé mon partage **et** que mon propre `status/{moi}` existe (réciprocité réelle, ATK3). Conséquence : à l'activation du partage, créer `status/{moi}` dans le même lot (§8) ; tant qu'il n'existe pas, la lecture des autres statuts est refusée et l'app l'affiche sans alarme.
| Champ | Type | Note |
|---|---|---|
| `presence` | `"in_trusted"` \| `"out"` \| `"in_risk"` \| `"unknown"` | État issu des géofences, **aucune coordonnée**. |
| `placeId` | string \| null | Lieu concerné (celui où l'on est, ou celui que l'on vient de quitter). |
| `presenceAt` | timestamp | Heure de l'événement de géofence (horloge appareil, bornée par les règles : −24 h / +5 min). |
| `lastUpdateAt` | timestamp | Dernier signal "tout va bien" : update manuelle OU entrée en zone de confiance. |
| `updateText` | string ≤ 140 \| null | Légende de la dernière update. |
| `hasPhoto` | bool | `true` si `photos/{uid}` existe. |
| `batteryPct` | int 0–100 \| null | Écrit avec les autres champs (pas d'écriture périodique dédiée, pour limiter le coût). |
| `locationAccess` | `"background"` \| `"foreground"` \| `"denied"` \| `"unknown"` | Permission système constatée (alimente l'état "localisation limitée"). |
| `escalationDueAt` | timestamp \| null | Prochaine échéance d'escalade. **Inutile en Spark** ; prévu pour la fonction planifiée Blaze (§13). |
| `updatedAt` | timestamp | Heure serveur de l'écriture ("mis à jour il y a X min"). |

Exemple (membre parti de la maison à 16h02, sans update depuis) :
```json
{ "presence": "out", "placeId": "p1", "presenceAt": "2026-10-16T16:02:00Z", "lastUpdateAt": "2026-10-16T15:40:00Z",
  "updateText": null, "hasPhoto": false, "batteryPct": 62, "locationAccess": "background",
  "escalationDueAt": "2026-10-16T18:02:00Z", "updatedAt": "2026-10-16T16:02:03Z" }
```

### 2.8 `miffs/{miffId}/photos/{uid}` — photo de la dernière update (SENSIBLE)
| Champ | Type | Note |
|---|---|---|
| `jpeg` | bytes ≤ 150 000 | Compresser côté client (~1024 px, qualité ~60). Un seul document : la nouvelle photo écrase l'ancienne. |
| `createdAt` | timestamp | |
| `expiresAt` | timestamp | > maintenant et **≤ maintenant + 25 h** (24 h de durée voulue + 1 h de marge, car la valeur vient de l'horloge du téléphone). **Même durée pour tous les comptes, premium ou non** (décision PM : photo supprimée de la base pour économiser la place). |

Lecture : `get` uniquement (pas de `list`), seulement si la Miff existe, pour qui a activé son partage (**pas** de condition de statut publié pour les photos : décision PM, conséquence assumée), et **seulement tant que `expiresAt` n'est pas passé** : une photo expirée renvoie un refus de permission, une photo absente renvoie « introuvable » ; l'app traite les deux comme « pas de photo ». À ne charger que si `status.hasPhoto = true`, et à re-télécharger seulement si `lastUpdateAt` a changé (sinon coût de bande passante inutile).

Suppression : l'auteur, le créateur, ou **tout membre de la Miff une fois la photo expirée** (nettoyage opportuniste : l'app qui constate une photo expirée la supprime, et son auteur supprime la sienne à chaque lancement et mise à jour avec `hasPhoto = false`). **Limite connue** : sur Spark, aucune suppression automatique garantie (les politiques TTL de Firestore n'ont pas de quota gratuit selon la documentation Google, à vérifier dans la console) ; la formule exacte est donc « **illisible après 24 h ; suppression physique au mieux** », à faire valider par le juriste (§11).

### 2.9 `miffs/{miffId}/places/{placeId}` — lieux
| Champ | Type | Note |
|---|---|---|
| `name` | string 1–60 | |
| `type` | `"trusted"` \| `"risk"` | Immuable après création. |
| `address` | string ≤ 200 \| null | **Sensible** (peut être un domicile). |
| `lat`, `lng` | number | **Sensible.** 5 décimales suffisent (~1 m). |
| `radiusM` | int 50–2000 | Les règles tolèrent 50 ; l'UI doit proposer ≥ 100 m (les géofences plus petites sont peu fiables). |
| `priority` | int 0–999 \| null | Indice de priorité (0 = le plus important), voir §7. |
| `createdBy`, `createdAt`, `updatedAt` | string, timestamp, timestamp | `createdBy` = créateur. |

Lisible par tous les membres tant que la Miff existe (ils en « héritent »). Écrit uniquement par le créateur.

### 2.10 `users/{uid}/devices/{deviceId}` — jetons de push (Blaze)
`token` (string ≤ 512), `platform` (`ios`/`android`), `updatedAt` (heure serveur). À n'implémenter que le jour où l'envoi de push existe (§13). Supprimé à la déconnexion.

### 2.11 `miffsDeleted/{miffId}` — pierre tombale d'une Miff supprimée
| Champ | Type | Note |
|---|---|---|
| `deletedAt` | timestamp | **Heure serveur** (`serverTimestamp()`), jamais une date du client. |

C'est le **seul** champ. Aucune donnée personnelle (ni créateur, ni nom, ni membres) : uniquement l'identifiant de la Miff supprimée (dans le chemin) et la date. Créée **uniquement** par le créateur, dans le lot qui supprime la Miff (la Miff doit avoir disparu après le lot). **Jamais lisible** (`get` et `list` refusés), **jamais modifiable ni supprimable** par un client : seules les règles la lisent (`exists`) pour refuser la recréation de l'identifiant. Conservation : indéfinie (quelques dizaines d'octets par Miff supprimée). Sert à empêcher qu'un ancien membre recrée la Miff sous le même identifiant et relise les lieux et statuts restés en base (ATK1).

---

## 3. Statuts : ce qui est stocké, ce qui est calculé

**Stocké** : `presence`, `placeId`, `presenceAt`, `lastUpdateAt`, `locationAccess`, `batteryPct`, `sharing.*`.
**Calculé par chaque client à l'affichage** (algorithme identique iOS/Android, paramètres en constantes partagées) :

```
ORANGE_APRES = 2 h      ROUGE_APRES = 4 h      (spec : orange à +2 h, rouge à +2 h supplémentaires)

niveau(membre, statut, maintenant):
  si !membre.sharing.enabled           -> PARTAGE_DESACTIVE      (carte neutre, jamais alarmiste)
  si statut absent                     -> INCONNU
  selon statut.presence:
    in_risk     -> ZONE_A_RISQUE
    in_trusted  -> VERT
    unknown     -> PAS_DE_NOUVELLES_NEUTRE  [Hypothèse : pas d'escalade sans départ connu, Q10]
    out         -> ref = max(presenceAt, lastUpdateAt)
                   écoulé = maintenant - ref
                   < 2 h -> VERT (parti, tout va bien)   < 4 h -> ORANGE   sinon -> ROUGE
  en plus : si locationAccess in (denied, foreground) -> badge "localisation limitée" (mises à jour manuelles encouragées)
```

- Une **update manuelle** écrit `lastUpdateAt = maintenant` : le niveau repasse à vert, le compteur repart (spec : "update manuelle ou ré-entrée en zone ramène à vert").
- **Rouge « toutes les heures »** : la spec ne dit pas ce qui se passe si l'update horaire manque (Q10). Le modèle fournit tout ce qu'il faut (`lastUpdateAt`) pour afficher "pas de nouvelles depuis X h" ; la relance éventuelle est une notification (Blaze).
- Le calcul repose sur l'heure de l'appareil du lecteur : un décalage d'horloge de quelques minutes est possible. Le serveur (`updatedAt`) permet de détecter une dérive grossière.
- Le feed n'a donc **pas besoin d'écriture serveur** pour passer orange/rouge : il suffit que l'app d'un proche soit ouverte. Ce qui est impossible sans serveur, c'est de **prévenir** quelqu'un dont l'app est fermée.

**Déclencheurs de géofence** (écrits par l'appareil du membre, dans le document `status` de chaque Miff concernée) :
| Événement | Écriture |
|---|---|
| Entrée zone de confiance | `presence=in_trusted`, `placeId`, `presenceAt=t`, `lastUpdateAt=t`, `escalationDueAt=null` |
| Sortie zone de confiance | `presence=out`, `placeId` (lieu quitté), `presenceAt=t`, `escalationDueAt=t+2h` |
| Entrée zone à risque | `presence=in_risk`, `placeId`, `presenceAt=t` (alerte immédiate = notification, Blaze) |
| Sortie zone à risque | `presence=out`, `presenceAt=t`, `escalationDueAt=t+2h` |
Si un lieu de confiance et une zone à risque se chevauchent, la zone à risque prime **[Hypothèse]**.

---

## 4. Consentement au partage de position

- Par membre **et** par Miff : `members/{uid}.sharing`. Un utilisateur dans deux Miffs peut partager avec l'une et pas l'autre.
- Activer : le client affiche l'écran d'explication (primer), puis écrit `enabled=true`, `consentedAt=maintenant`, `consentVersion="…"`, `revokedAt=null`. Les règles imposent `consentedAt = heure serveur` : impossible d'antidater un consentement.
- Révoquer à tout moment (Réglages) : **dans un même batch** `members/{uid}.sharing` (`enabled=false`, `revokedAt=maintenant`) + suppression de `status/{uid}` + suppression de `photos/{uid}`. Les règles **refusent** la révocation si ces suppressions ne sont pas dans le batch : aucune trace de position ne survit à un retrait de consentement.
- Les autres membres voient `sharing.enabled=false` et affichent "partage désactivé" (ton neutre). Savoir si on les **notifie** de la désactivation est une question produit (Q16).
- "Voir ce que je partage" : l'écran Réglages affiche, pour chaque Miff, la liste des membres (`members`) et l'interrupteur de partage. Le contenu partagé = le document `status/{uid}` (à afficher tel quel à l'utilisateur).
- Le consentement ne peut pas être donné "par défaut" : à la création/jonction, le document membre peut naître avec `enabled=false` (sans consentement) ou `true` (avec l'écran de consentement). Aucun autre chemin.

---

## 5. Rejoindre une Miff avec le code secret

Flux : (1) le client lit `joinCodes/{CODE}` (`get`) → obtient `miffId` + `miffName` ; (2) affiche l'écran de confirmation + consentement ; (3) écrit en **un batch** `members/{uid}` (avec `joinCode`) + `users/{uid}/memberships/{key}`. Les règles vérifient que `joinCodes/{joinCode}.miffId` = la Miff visée : on ne peut pas rejoindre sans connaître le code.

**Pourquoi non énumérable** : pas de `list` sur `joinCodes`, ni sur `miffs`, ni sur `users`. Un code inexistant renvoie simplement "introuvable".

**Risques résiduels et mitigations**
| Risque | Mitigation |
|---|---|
| Brute force du code (deviner 12 caractères). | ~7,9 × 10^17 combinaisons. À 100 tentatives/s (très au-dessus d'un client légitime), il faudrait ~250 millions d'années pour tout parcourir ; la probabilité de toucher un code existant dans une base de 100 000 Miffs reste négligeable (~10^-13 par tentative). **App Check** (à activer en mode "enforce" sur Firestore) bloque les scripts hors application officielle. |
| Les règles ne peuvent pas limiter le nombre de tentatives par compte. | Surveiller les lectures anormales de `joinCodes` dans la console (alerte d'usage). Si un abus apparaît : fonction appelable "rejoindre" avec compteur de tentatives (Blaze, §13). |
| Code mal généré (aléa faible). | Imposer un générateur cryptographique côté client (§2.4). Le format est vérifié par les règles, pas l'aléa. |
| Code qui fuite (capture d'écran, ex-membre). | Régénération du code par le créateur : batch `miffs.update(joinCode)` + création du nouveau `joinCodes` + suppression de l'ancien (les règles **refusent** de garder l'ancien code valide). **Le retrait d'un membre par le créateur renouvelle le code, imposé par les règles** (DUR-2, décision PM) : l'ancien code cesse de fonctionner (même après libération de l'ancien emplacement gratuit de l'ex-membre), le créateur repartage le nouveau avec qui il veut. Un **départ volontaire** ne renouvelle rien (un membre simple ne peut pas écrire la Miff) : l'app du créateur lui propose discrètement, à l'ouverture suivante, de renouveler le code (facultatif). Reste possible : un membre qui se retire lui-même juste avant d'être retiré garde l'ancien code (course non empêchable sans serveur, risque K3 du ticket) ; le créateur peut renouveler le code à la main. |
| Pas de validation du créateur à l'arrivée. | Non spécifié ; tout porteur du code rejoint immédiatement. Option : file d'attente d'approbation (Q7). |

---

## 6. Limites freemium : comment elles sont appliquées

Le client ne peut jamais s'attribuer le premium (`entitlements` en écriture interdite). Les limites sont des **slots à identifiant fixe**, vérifiés par les règles, sans compteur ni fonction :

| Limite | Mécanisme | Failles / limites connues |
|---|---|---|
| **1 Miff à la fois (gratuit)** | L'index `users/{uid}/memberships/free` ne peut être créé qu'une fois (un second `create` devient un `update`, interdit). Un document membre n'est accepté que si un index pointant vers la Miff existe dans le même batch. Une clé autre que `free` exige `entitlements` actif. | (a) Un utilisateur qui était premium garde ses Miffs en plus du slot gratuit après expiration (au plus +1) : acceptable. (b) Si un membre est retiré, son index reste jusqu'à ce que son appli le nettoie (au lancement) : tant que ce n'est pas fait il ne peut pas rejoindre une autre Miff en gratuit. (c) Un client modifié peut ouvrir un second **compte** gratuit : propre à tout freemium. |
| **1 lieu de confiance (gratuit)** | En gratuit, seul l'identifiant `p1` et le type `trusted` sont acceptés pour créer un lieu. Premium : identifiants libres et zones à risque. | (a) Le compte "lieux" est par Miff ; comme un compte gratuit n'a qu'une Miff, cela revient à 1 lieu. (b) Un créateur redevenu gratuit conserve ses lieux premium existants (comportement à la rétrogradation : Q3). (c) Un membre gratuit d'une Miff dont le créateur est premium bénéficie des lieux : voulu par la spec ("les membres héritent"). |
| **Zones à risque / notifications intelligentes (premium)** | Création d'une zone `risk` : exige le premium du créateur. Les notifications intelligentes sont du ressort du serveur (Blaze). | Savoir si le premium est "par utilisateur" ou "par Miff" : Q3. |
| **Premium actif** | `entitlements.active == true` et `expiresAt > heure serveur` (les règles évaluent l'expiration elles-mêmes). | Tant qu'aucune fonction ne valide les achats, l'octroi est **manuel** (console) : ne pas ouvrir le paywall payant au public avant d'avoir la validation serveur (Blaze, §13). |

La consommation en lectures des règles (get/exists) est facturée (§12) : c'est le prix de cette approche sans Cloud Function.

---

## 7. Ce que le serveur expose pour choisir les géofences (iOS 20 / Android 100)

Le serveur ne reçoit **jamais** la position de l'utilisateur pour décider. Il expose, pour chaque lieu : `type`, `radiusM`, `priority`, `createdAt`, `lat/lng`. Le nombre de lieux = taille de la liste `places` de chaque Miff (aucun compteur stocké).

Algorithme proposé (client, en local ; priorisation exacte à valider, Q9) :
1. Récupérer les `places` de toutes les Miffs de l'utilisateur (listener ; déjà en cache hors ligne).
2. Fusionner les lieux quasi identiques de plusieurs Miffs (un seul géofence système, plusieurs destinataires) pour économiser les emplacements.
3. Si le total dépasse 20 (iOS) / 100 (Android) : trier par `priority` croissante puis `createdAt`, puis préférer les lieux les plus proches de la position courante **calculée sur l'appareil** (changements significatifs de position), et ré-enregistrer les géofences au fil des déplacements.
4. Réserver une partie du quota iOS aux zones à risque si elles existent (**[Hypothèse]**).
5. Un lieu non surveillé à cause de la limite : état local ("Ce lieu n'est pas surveillé actuellement") ; rien à écrire côté serveur.

---

## 8. Ce que chaque client (iOS et Android) doit implémenter, par fonctionnalité

Toutes les écritures multi-documents sont des **batches** (`WriteBatch` côté Firestore SDK), à garder courts : les règles sont limitées à 20 appels de lecture par batch.

| Fonctionnalité | Lectures | Écritures (dans un batch sauf mention) |
|---|---|---|
| **Inscription / profil** (AUTH-B) | — | **Une seule écriture** `users/{uid}` **set** (création) avec **toutes** ces clés : `firstName`, `lastName`, `avatar` (facultatif), `createdAt` = `serverTimestamp()`, `termsAcceptedAt` = `serverTimestamp()`, `termsVersion` (version affichée à l'inscription et conservée sur l'appareil jusqu'ici), et **`analyticsConsent` écrit toujours** : case (b) cochée -> `{enabled:true, consentedAt:serverTimestamp(), consentVersion:<constante>, revokedAt:null}` ; décochée -> `{enabled:false, consentedAt:null, consentVersion:null, revokedAt:null}` (les règles acceptent aussi son absence, mais le client l'écrit toujours pour que l'état soit explicite). **Ne jamais** écrire de date du client pour ces champs (refusé), ni âge, date de naissance, e-mail, téléphone (refusés). Modif. de profil : `users/{uid}` update **limité à** `firstName`, `lastName`, `avatar` (+ `analyticsConsent`, voir ci-dessous) + `members/{uid}` update dans **chaque** Miff (liste via `memberships`). **`createdAt`, `termsAcceptedAt`, `termsVersion` ne se modifient jamais** (refusé). |
| **Nouvel utilisateur social (Google / Apple) : panneau de consentement** (AUTH-B, ticket §7.8) | — | **Aucune écriture Firestore avant l'acceptation de (a).** Acceptation : mêmes valeurs que ci-dessus, dans l'écriture de création du profil à l'étape profil. **Refus** (Annuler, retour, fermeture) : **ne rien écrire**, supprimer le compte d'authentification tout juste créé, purger le cache local ; il n'y a aucun document `users/{uid}` à supprimer (si, par erreur, un `delete users/{uid}` est envoyé, il est accepté et sans effet). Aucun événement Analytics ni rapport de plantage, même si (b) était cochée sur le panneau. Suppression du compte impossible (hors connexion) : rester sur le panneau, sans accès au reste de l'app, jamais de profil créé. |
| **Réglages : statistiques et plantages** (AUTH-B ; l'écran est dans le ticket Réglages) | `get users/{uid}` (déjà lu pour le routage du lancement : **aucune lecture de plus**) : lire `analyticsConsent` ; absent = désactivé | **Activer / réactiver** : `users/{uid}` update `{analyticsConsent: {enabled:true, consentedAt:serverTimestamp(), consentVersion:<constante>, revokedAt:null}}` avec une `consentVersion` **égale ou plus récente** que celle déjà enregistrée. **Retirer** : update `{analyticsConsent: {enabled:false, consentedAt:<valeur lue>, consentVersion:<valeur lue>, revokedAt:serverTimestamp()}}` (recopier à l'identique les deux valeurs lues ; ne jamais les effacer). Ne pas tenter d'« activer » un consentement déjà actif ni de « retirer » un consentement jamais donné (refusé). **Le retrait coupe la collecte sur l'appareil immédiatement, avant même la fin de l'écriture** (hors connexion, l'écriture part plus tard ; c'est l'état local qui commande la collecte). **À l'activation, la collecte ne démarre qu'après l'acceptation de l'écriture** (si elle est refusée : collecte restée coupée, message neutre, pas de nouvelle tentative en boucle). |
| **Connexion / nouvel appareil : appliquer le choix (b)** (AUTH-B, ticket §8) | `get users/{uid}` (la lecture du routage, §5 du ticket) | La collecte reste **coupée** tant que ce document n'a pas été lu. Lu : `analyticsConsent.enabled == true` -> activer la collecte sur cet appareil ; bloc absent, `enabled == false` ou lecture impossible -> collecte coupée, `sign_in_completed` non envoyé. `sign_up_completed` : seulement si (b) cochée, après la création du profil. L'application d'un consentement donné sur un autre appareil est un point RGPD assumé par le PM (§11). |
| **Créer une Miff** | `entitlements/{uid}` (savoir quelle clé utiliser) ; `memberships` (si gratuit et déjà un `free` → afficher le paywall sans tenter l'écriture) | Générer `miffId` (ID auto) et `code` (aléa cryptographique). Batch de 4 : `miffs/{miffId}` create {ownerUid, name, joinCode, createdAt} ; `joinCodes/{code}` create {miffId, miffName, createdAt} ; `miffs/{miffId}/members/{uid}` create {role:"owner", profil copié, joinedAt, sharing} ; `users/{uid}/memberships/{key}` create {miffId, joinedAt} avec `key="free"` si non premium, sinon `miffId`. Collision de code (très improbable) : régénérer et réessayer. |
| **Rejoindre une Miff** | `joinCodes/{CODE}` (get) | Après l'écran de confirmation + consentement. Batch : `members/{uid}` create {role:"member", joinCode, profil copié, joinedAt, sharing} + `memberships/{key}` create. Erreur "introuvable" ou refus : message unique et bienveillant ("Ce code ne correspond à aucune Miff"), sans distinguer les causes. Limiter les essais côté UI (ex. pause après 5 échecs). |
| **Feed** | Listener `miffs/{id}/members` + listener `miffs/{id}/status` (**n'attacher cet écouteur qu'après avoir publié son propre statut** ; il est terminé par Firestore sur `permission-denied` si le statut disparaît ou n'existe pas encore : le rattacher après chaque republication ; en attendant, état neutre « Partage de ton statut en cours… », jamais une erreur) + `get photos/{uid}` à la demande (**refus ou introuvable = « pas de photo »**, y compris pour sa propre photo expirée : une photo expirée est refusée même à son auteur ; ignorer toute photo dont `expiresAt` est passé) ; `get miffs/{id}` pour le nom et le code. Si `get miffs/{id}` est refusé ou introuvable : perte d'accès (voir « Perte d'accès »). | — |
| **Sélecteur de Miff (premium)** | Documents de `users/{uid}/memberships` puis `get miffs/{id}` pour chaque nom. | — |
| **Activer le partage** | — | `members/{uid}` update {sharing: enabled=true, consentedAt=maintenant, consentVersion, revokedAt=null} **dans le même batch** : création de `status/{uid}` avec le statut initial `presence = "unknown"` (le schéma impose toutes les clés ; `unknown` s'affiche en état neutre, jamais en « tout va bien »), `locationAccess` = permission réelle de l'appareil, `lastUpdateAt` = maintenant, `updatedAt` = heure serveur. Sans ce statut, la lecture des autres statuts est refusée (ATK3). Le premier événement réel l'écrase. |
| **Révoquer le partage** | — | Batch : `members/{uid}` update (sharing.enabled=false, revokedAt=maintenant) + delete `status/{uid}` + delete `photos/{uid}` (ignorer si absent). Arrêter la surveillance géofence pour cette Miff. |
| **Événement géofence** | — | `status/{uid}` set (document complet, §3), une écriture par Miff concernée. Fonctionne hors-ligne (file d'attente Firestore) ; l'horodatage envoyé est l'heure de l'événement. **Ne pas** ajouter de throttling côté règles : la file hors-ligne rejouerait des écritures rapprochées. |
| **Update manuelle (texte + photo optionnelle)** | — | Batch : (si photo) `photos/{uid}` set {jpeg, createdAt, expiresAt} + `status/{uid}` set {lastUpdateAt=maintenant, updateText, hasPhoto, …}. |
| **Créer / modifier / supprimer un lieu** (créateur) | `places` | Gratuit : `places/p1` uniquement, `type="trusted"`. Premium : ID auto, `trusted` ou `risk`. L'UI doit lire `entitlements` et ouvrir le paywall *avant* l'écriture ; le refus des règles est le filet de sécurité. Modif : `name, address, lat, lng, radiusM, priority, updatedAt` ; le `type` ne se modifie pas. |
| **Quitter une Miff** (membre) | — | Batch : delete `members/{uid}` + delete `status/{uid}` + delete `photos/{uid}` + delete `memberships/{key}`. Le créateur ne peut partir qu'en supprimant la Miff (voir ci-dessous). |
| **Informations pour les clients (photos, codes)** | — | (iv) **Horloge du téléphone en avance de plus d'1 h** : les règles refusent `expiresAt` au-delà de maintenant + 25 h (heure serveur) ; pour rester tolérant, **borner `expiresAt` à maintenant + 23 h** côté client. (v) **Un code libéré peut être réutilisé** : une fois supprimé (renouvellement, suppression de Miff), un code redevient libre et n'importe qui peut le créer pour sa propre Miff (probabilité de collision négligeable avec 59 bits, mais le code n'est pas « brûlé »). |
| **Retirer un membre** (créateur) | `get miffs/{id}` (code actuel) | **Un membre par batch, 6 écritures atomiques** : delete `members/{cible}` + delete `status/{cible}` + delete `photos/{cible}` (ignorer si absent) + update `miffs/{id}` `{joinCode: NOUVEAU}` + create `joinCodes/{NOUVEAU}` `{miffId, miffName, createdAt}` + delete `joinCodes/{ANCIEN}`. **Refusé si le code ne change pas dans le même batch.** Nouveau code : même générateur cryptographique que le premier (§2.4) ; collision (très improbable) : le batch échoue, regénérer et réessayer. Afficher ensuite le nouveau code avec l'action de partage (ton bienveillant, jamais accusateur ; texte à valider par le PM). L'index `memberships` de la cible n'est pas touché (voir "Nettoyage au lancement"). |
| **Nettoyage au lancement / Perte d'accès** | `get miffs/{id}` pour chaque index (refus = retiré ; introuvable = Miff supprimée) | Message neutre (« Tu ne fais plus partie de la Miff « X » » / « La Miff « X » n'existe plus »), sans reproche. Puis, **sans pouvoir lire ma propre fiche** (sur une Miff supprimée, `get members/{moi}` est refusé par le contrôle d'existence de la Miff : on ne peut donc pas tester « ma fiche existe encore ») : **essayer d'abord le batch « Quitter » complet** (delete fiche + statut + photo + index ; la suppression d'un document absent est sans effet) ; **s'il est refusé** (retiré : la fiche n'existe plus), **en repli supprimer l'index seul** (autorisé car je ne suis plus membre) ; puis vider le cache local. Supprimer aussi ma propre photo expirée (`hasPhoto = false`) et celle d'un autre membre si je la constate expirée (autorisé à tout membre). |
| **Régénérer le code** (créateur) | — | Batch : `miffs/{id}` update {joinCode} + create `joinCodes/{nouveau}` + delete `joinCodes/{ancien}`. |
| **Renommer la Miff** (créateur) | — | Batch : `miffs/{id}` update {name} + `joinCodes/{code}` update {miffName}. |
| **Supprimer une Miff** (créateur) | — | Par étapes, en petits batches : retirer chaque autre membre avec le batch de retrait ci-dessus (**un membre par batch, le code est renouvelé à chaque fois** ; un retrait dans le batch final qui supprime la Miff est refusé), supprimer les `places` (≤ 5 par batch), puis en dernier un batch : delete `miffs/{id}` + **create `miffsDeleted/{id}` `{deletedAt: serverTimestamp()}`** (pierre tombale, §2.11) + delete `joinCodes/{code}` (le code actuel de la Miff, lu dans `miffs/{id}.joinCode`) + delete mon `status`/`photos` (si existent) + delete mon `members/{uid}` + delete mon `memberships/{key}`. **Le batch est refusé sans la pierre tombale ou sans la suppression du code.** Si interrompu : relancer (idempotent : si le code a déjà été supprimé dans un batch précédent, le batch final reste accepté sans lui). Les sous-collections ne sont pas supprimées automatiquement par Firestore. |
| **Premium (lecture)** | `get entitlements/{uid}` (peut ne pas exister = gratuit) | Aucune. L'achat store ne suffit pas : c'est le serveur (ou le PM en manuel) qui écrit `entitlements`. |
| **Suppression de compte** | — | §10. |
| **Push (Blaze)** | — | À chaque lancement et rafraîchissement de jeton : `users/{uid}/devices/{deviceId}` set {token, platform, updatedAt}. À la déconnexion : delete. |

**App Check** : les deux apps doivent initialiser Firebase App Check (App Attest/DeviceCheck sur iOS, Play Integrity sur Android) avant toute requête Firestore ; jeton de debug pour les builds de développement. Sans cela, le PM ne pourra pas activer l'application stricte (README).

---

## 9. Matrice d'accès

Légende : `S` = soi-même, `M` = membre de la Miff, `P` = membre qui partage lui-même sa position (réciprocité), `O` = créateur de la Miff, `—` = refusé.

| Chemin | get | list | create | update | delete |
|---|---|---|---|---|---|
| `users/{uid}` | S | — | S (profil, (a) CGU, (b) consentement facultatif ; heure serveur) | S (profil et consentement (b) seulement ; (a) et `createdAt` figés) | S |
| `users/{uid}/memberships/{key}` | S | S | S (slot) | — | S (si plus membre) |
| `users/{uid}/devices/{id}` | S | S | S | S | S |
| `entitlements/{uid}` | S | — | — (serveur/console) | — | — |
| `joinCodes/{code}` | tout compte connecté qui connaît le code | **—** | O (avec sa Miff) | O (nom) | O |
| `miffsDeleted/{id}` | **—** | **—** | O (dans le lot qui supprime la Miff, champ `deletedAt` seul) | **—** | **—** |
| `miffs/{id}` | M | **—** | S (devient O) ; refusé si `miffsDeleted/{id}` existe | O (nom, code) | O (avec pierre tombale et suppression du code dans le lot) |
| `miffs/{id}/members/{uid}` | M (Miff existante) | M (Miff existante) | S (via code ou création) | S (profil, consentement) | S (quitter) / O (retirer, **avec nouveau code dans le même lot**) |
| `miffs/{id}/status/{uid}` | P + statut publié (Miff existante) | P + statut publié (Miff existante) | S (si partage actif) | S (si partage actif) | S / O |
| `miffs/{id}/photos/{uid}` | P (Miff existante, photo non expirée) | **—** | S (si partage actif ; expiresAt ≤ +25 h) | S | S / O / tout membre si expirée |
| `miffs/{id}/places/{pid}` | M (Miff existante) | M (Miff existante) | O (limites freemium) | O | O |
| tout le reste | — | — | — | — | — |

Les Cloud Functions / la console (Admin SDK) contournent les règles : c'est ainsi que `entitlements` est écrit.

---

## 10. Suppression de compte et durées de conservation

**Ordre côté client** (tant que le compte Auth existe, car les règles exigent l'authentification) :
1. Pour chaque Miff dont je suis membre simple : batch "Quitter" (membre + statut + photo + index).
2. Pour chaque Miff dont je suis créateur : procédure "Supprimer une Miff" (ou transfert de propriété si le PM le décide, Q8).
3. Supprimer `users/{uid}/devices/*`, puis `users/{uid}`.
4. Supprimer l'utilisateur Auth (ré-authentification récente exigée par Firebase).

**Non supprimable par le client** : `entitlements/{uid}` (ne contient aucune donnée personnelle hormis l'UID et l'état d'abonnement). À purger à la main ou par une fonction (Blaze). **Risque** : si l'app plante en cours de procédure, des données restent orphelines ; une fonction `onDelete` du compte (ou l'extension Firebase "Delete User Data", Blaze) rend la suppression fiable (§13).

| Donnée | Conservation proposée (à valider) |
|---|---|
| Profil `users`, copies `members` | Jusqu'à suppression du compte ou départ de la Miff. |
| `status` (état courant) | Pas d'historique : écrasé à chaque événement ; supprimé à la révocation du partage, au départ, au retrait. |
| `photos` | **Illisible après 24 h ; suppression physique au mieux** (nettoyage par l'auteur, le créateur ou tout membre ; pas de TTL garanti sur Spark). Même durée pour tous les comptes. Supprimée avec le statut au départ / retrait / révocation. |
| `places` (coordonnées, adresses) | Jusqu'à suppression du lieu ou de la Miff. |
| `joinCodes` | Jusqu'à régénération ou suppression de la Miff (la suppression de la Miff exige celle du code, dans le même lot). |
| `miffsDeleted` (pierre tombale) | Indéfinie : identifiant de Miff + date de suppression, sans donnée personnelle. À faire examiner par le juriste (§11). |
| Preuve d'acceptation (a) (`termsAcceptedAt`, `termsVersion`) et consentement statistiques (b) (`analyticsConsent`) | Tant que le profil existe ; **supprimées avec `users/{uid}`** à la suppression du compte. Conserver la preuve après suppression du compte exigerait un journal séparé sans donnée personnelle (Q13) : non modélisé. Un utilisateur social qui refuse le panneau de consentement n'a **aucun** enregistrement. |
| Preuve de consentement (`sharing.*`) | Tant que le membre est dans la Miff ; supprimée avec le document membre. Si le juriste exige une conservation de la preuve après départ, il faudra un journal séparé (Q13). |
| `devices` (jetons) | Supprimés à la déconnexion ; purge des jetons inactifs > 60 jours par fonction (Blaze). |
| `entitlements` | Durée de l'abonnement ; purge à la suppression du compte (manuelle ou fonction). |
| Sauvegardes Firestore | Désactivées par défaut. Ne pas activer de sauvegarde/restauration à un instant donné sans décision du PM (elle dupliquerait les données sensibles). |

---

## 11. Données sensibles et points RGPD (à faire valider légalement par le PM)

Ce tableau décrit ce que le modèle implique ; il ne constitue pas une analyse de conformité. Les bases légales sont des **propositions** à valider avec un conseil juridique.

| Donnée | Où | Sensibilité | Base légale proposée (à valider) | Conservation proposée |
|---|---|---|---|---|
| Présence par rapport à un lieu (`presence`, `placeId`, horodatages) = information de localisation dérivée | `status` | **Très élevée** (localisation ; révèle des habitudes et l'absence du domicile) | Consentement explicite et révocable (le modèle le trace) | État courant uniquement, supprimé à la révocation / départ |
| Coordonnées et adresses des lieux | `places` | **Élevée** (peut être un domicile, une école) | Contrat + consentement du créateur ; information des membres | Jusqu'à suppression du lieu / de la Miff |
| Photo de profil, photo d'update | `users`, `members`, `photos` | Élevée (image d'une personne, parfois d'un tiers ou d'un mineur) | Contrat (profil) ; consentement (update) | Profil : durée du compte ; update : 24 h |
| Nom, prénom | `users`, `members` | Moyenne | Contrat | Durée du compte |
| Appartenance à un cercle (lien social, familial) | `members`, `memberships` | Moyenne à élevée (révèle des relations, éventuellement parent-enfant) | Contrat | Jusqu'au départ |
| Niveau de batterie, permission de localisation du téléphone | `status` | Faible à moyenne | Intérêt légitime / contrat | État courant |
| Jetons de notification | `devices` | Faible (identifiant d'appareil) | Contrat | Jusqu'à déconnexion |
| Preuve d'acceptation des CGU (a) et du consentement | `users`, `members` | Faible | Obligation de pouvoir démontrer le consentement | Voir §10 |
| Consentement (b) aux statistiques d'usage et rapports de plantage (état, date, version, retrait) | `users` (`analyticsConsent`) | Faible en soi (un booléen daté) ; **conditionne** la collecte d'une donnée à caractère personnel (identifiant d'installation, zone déduite de l'IP, journaux de plantage) | Consentement (RGPD art. 6.1.a et 7 : libre, spécifique, retirable aussi facilement que donné) | Tant que le profil existe (§10) |
| État d'abonnement | `entitlements` | Faible | Contrat | Durée de l'abonnement |
| Données Analytics et rapports de plantage (collectés seulement si (b) acceptée) | Google Analytics / Crashlytics (hors Firestore) | Faible si jamais de position | Consentement (b) | Régler la rétention GA4 au minimum dans la console |

Points RGPD à connaître (le PM assume le risque juridique, décision du 2026-10-23 : plus d'avis de juriste demandé) :
1. **Localisation** : donnée à haut risque ; le choix de ne stocker qu'un état (et non les coordonnées de la personne) réduit l'exposition mais ne la supprime pas.
2. **Mineurs** : si des mineurs peuvent utiliser l'app (cas parent-enfant mentionné au CLAUDE.md), le RGPD prévoit un âge de consentement numérique (15 ans en France d'après la loi nationale, à vérifier) et un consentement parental en dessous. Le modèle **ne contient aucune date de naissance ni âge** (minimisation) : il n'existe donc aucun contrôle d'âge. Q12.
3. **Analyse d'impact (AIPD)** : localisation + éventuels mineurs sont des critères qui peuvent la rendre nécessaire.
4. **Transferts hors UE** : Firestore est en région Paris, mais Firebase Authentication, FCM et Analytics peuvent traiter des données hors UE. À vérifier avec la documentation Google et les clauses contractuelles.
5. **Droit d'accès / portabilité / effacement** : l'effacement est réalisable (§10) ; une procédure d'export (accès/portabilité) n'est pas spécifiée (le client peut lire les documents propres à l'utilisateur ; un export formaté n'existe pas).
6. **Consentement réciproque anti-stalkerware** : le modèle impose la réciprocité, la révocation immédiate avec suppression, et un historique nul. Les stores appliquent des règles propres : à confronter par `release-manager`.
7. **Ex-membre qui connaît le code** : voir §5.
8. **Pierre tombale `miffsDeleted`** (DUR-1) : conservée indéfiniment, ne contient que l'identifiant d'une Miff supprimée et une date (aucune donnée personnelle à ma connaissance : un identifiant aléatoire de groupe). À faire confirmer par le juriste, avec la durée de conservation.
9. **Données des survivants d'une Miff supprimée** : la fiche, le statut et la photo d'un membre resté après la suppression d'une Miff restent *physiquement stockés* tant que l'app de ce membre ne les a pas nettoyés (au prochain lancement), mais ils sont **illisibles immédiatement** (contrôle d'existence de la Miff, DUR-2). **Risque accepté par le PM (revue de sécurité de DUR-2)** : les *écritures* de statut, photo et fiche d'un survivant ne vérifient pas que la Miff existe encore ; un survivant (ou un client modifié) peut donc écrire de nouveaux documents dans une Miff morte tant que sa fiche existe. Ces données restent illisibles (stockage seulement, quota Spark) et sont nettoyées au prochain lancement de l'app ; sur Spark aucune fonction ne peut les purger (décision PM : acceptable pour la bêta ; **avis juridique à obtenir avant le lancement public**, risque K4 du ticket). La procédure client « retirer chaque membre avant de supprimer la Miff » (§8) limite ce cas ; la suppression de la Miff n'est pas bloquée s'il reste des membres (les règles ne peuvent pas vérifier qu'une sous-collection est vide).
10. **Photos** : la formulation « 24 h maximum » est remplacée par « illisible après 24 h ; suppression physique au mieux » tant qu'une suppression automatique n'est pas disponible sur Spark (TTL de Firestore sans quota gratuit selon la documentation Google, à vérifier dans la console) : à faire valider par le juriste.
11. **Cache local** : les SDK conservent un cache sur l'appareil (statuts, lieux) ; contrat client : vider le cache à la déconnexion, à la suppression de compte et à la perte d'accès à une Miff (ticket durcissement §5.3, traité en DUR-3).
12. **Deux consentements à l'inscription (AUTH-B)** : (a) CGU et politique, obligatoire ; (b) statistiques et plantages, facultatif, décoché par défaut, retirable dans Réglages (retrait enregistré avec sa date). Point RGPD, risque assumé par le PM (décision du 2026-10-23) : le caractère libre et spécifique de (b) (une seule case pour statistiques d'usage **et** rapports de plantage, risque R16 du ticket) et la formulation du texte.
13. **Preuve de consentement** : l'horodatage serveur de (a) et de (b) est celui de la **création du profil**, pas du clic sur le bouton d'inscription ; sans profil créé, aucune preuve serveur n'existe (le choix reste seulement sur l'appareil). La preuve est **supprimée avec le profil** à la suppression du compte. Point RGPD, risque assumé par le PM (ticket §17.3).
14. **Compte d'authentification créé avant le consentement (connexion sociale d'un nouvel utilisateur)** : en cas de refus, le compte est supprimé et **aucun document Firestore** n'est écrit ; mais l'identifiant et l'adresse fournis par Google ou Apple existent dans Firebase Authentication pendant quelques secondes, et plus longtemps si l'app est fermée de force pendant le panneau (ticket R17). Les règles Firestore n'imposent le consentement (a) que pour la création du **profil** : elles n'empêchent pas un client modifié d'écrire une fiche de membre ou une Miff sans profil (voir §14, point 7).
15. **Mineurs** : aucun âge minimum, aucune date de naissance (décision PM Q7) ; un mineur peut accepter (a) et (b) seul. Cela relève du point 2 ci-dessus (RGPD art. 8, 15 ans en France à vérifier).
16. **Application du choix (b) à un autre appareil** du même compte : le modèle le permet (le choix est lu sur le compte) ; acceptable ou nouveau consentement par appareil ? Point RGPD, risque assumé par le PM (ticket §17.3).

---

## 12. Coûts : quotas gratuits Spark et signal de bascule

**Quotas Spark vérifiés le 2026-10-16** sur https://firebase.google.com/pricing (Firestore édition Standard) :
- Firestore : 1 GiB stockés, **50 000 lectures/jour**, **20 000 écritures/jour**, 20 000 suppressions/jour, 10 GiB/mois de transfert sortant.
- Authentication : 50 000 utilisateurs actifs mensuels (MAU).
- **Cloud Functions : non disponible sur Spark** (listé "Not applicable").
- **Cloud Storage : non disponible sur Spark** (aucune quote gratuite listée en Spark ; un article tiers indique que Storage exige Blaze depuis février 2026 : **à vérifier** dans la console avant de s'y fier).
- FCM et App Check : sans coût, mais l'envoi de push exige un serveur (donc Blaze).
- Les quotas gratuits sont conservés après passage en Blaze ; au-delà, facturation à l'usage (tarifs de europe-west9 **à vérifier** dans le calculateur ; ordre de grandeur indicatif : quelques centimes pour 100 000 lectures). Une budget alert Blaze prévient mais **ne plafonne pas** la dépense.
- Au dépassement d'un quota Spark, les requêtes sont refusées jusqu'à la réinitialisation quotidienne : l'app « casse » pour tous (heure exacte de réinitialisation : à vérifier).

**Estimation par utilisateur actif/jour** (Miff de 4 membres, 5 ouvertures de l'app par jour dont ~3 « à froid », 6 transitions de géofence, 1 update manuelle tous les 2 jours) — *estimations, pas des mesures* :
| Poste | Lectures | Écritures |
|---|---|---|
| Ouverture à froid : memberships (1) + Miff (1) + membres (4) + statuts (4) + lieux (rarement) + entitlement (1) + lectures faites par les règles (~4) ≈ 15–20 | ~50–60 (×3) | — |
| Changements des 3 autres membres reçus en direct (~7/jour/membre, application ouverte ~30 % du temps) | ~7 | — |
| Photos consultées | ~2 | — |
| Événements de géofence + updates + consentement | — | ~7 |
| **Total** | **~60–80** | **~7** |

- Lectures : 50 000 ÷ ~75 ≈ **650 utilisateurs actifs/jour** (≈ 330 si la consommation est deux fois plus forte). **Les lectures sont le premier plafond.**
- Écritures : 20 000 ÷ 7 ≈ 2 800 utilisateurs actifs/jour (doublé de coût pour un utilisateur premium dans deux Miffs).
- **Transfert sortant** : avatars (~15 Ko × 4 membres × 3 ouvertures) et photos (≤ 150 Ko) pèsent ; ordre de grandeur 10–15 Mo/utilisateur/mois → 10 GiB couvrent ~700–1 000 utilisateurs. Les photos sont le levier principal : taille ≤ 150 Ko, une seule consultation par update (le client met en cache).
- Stockage : ~100–300 Ko par utilisateur (avatars en double) → 1 GiB est loin d'être le premier plafond.

**Signal pour passer en Blaze** (l'un ou l'autre) :
1. Console Firebase > Firestore > Utilisation : lectures du jour > **30 000** de façon répétée (60 % du quota), ou écritures > 12 000, ou transfert sortant du mois > 6 GiB.
2. Besoin des notifications push, de la validation d'abonnement, des photos Storage ou d'une suppression de compte fiable (voir §13) — ce besoin arrive **avant** le plafond de volume pour le produit visé.
Ordre de grandeur de coût à ~2 000 utilisateurs actifs/jour après bascule : quelques euros par mois (lectures et transfert au-delà du gratuit) — estimation grossière à confirmer.

**Lectures de règles supplémentaires du durcissement (DUR-1 et DUR-2), mesurées dans l'émulateur le 2026-10-22 (bornes hautes à ±1 près, voir `rules-test-report.md`)** : création de Miff +1 (pierre tombale) ; lecture d'un statut ≈ 3 (existence de la Miff, partage actif, statut publié) contre 1 avant ; lecture d'une photo ≈ 2 ; lecture d'un lieu ≈ 2 ; lot de suppression de Miff : base mesurée ≈ 4, pire cas recompté à la main sans cache 12 sur 20 ; lot de retrait d'un membre : base mesurée ≈ 2, pire cas 18 sur 20 (marge d'au moins 2 dans tous les cas ; les valeurs plus hautes de notre outil sont plafonnées à 10 par évaluation d'écriture et ne sont pas des totaux) ; la liste des statuts n'est pas mesurable avec la méthode actuelle. Impact (estimation, pas une mesure d'usage réel) : quelques lectures de règles de plus par ouverture à froid, sans changer l'ordre de grandeur des estimations ci-dessus. À surveiller avec le ratio lectures ÷ utilisateurs actifs.

**Lectures de règles d'AUTH-B (mesurées dans l'émulateur le 2026-10-24)** : création du profil avec (a) et (b) = 0 lecture de règles ; mise à jour ou retrait du consentement (b) = 0. Aucune règle de `users` ne lit un autre document. La lecture du consentement au lancement est la lecture du profil déjà faite pour le routage (1 lecture, §5 du ticket authentification) : aucun surcoût.

**Coût caché des règles** : chaque `get()/exists()` dans une règle compte comme une lecture et est facturé même si la requête est refusée. Le schéma limite ces appels (ex. `isMember` = 1 lecture par requête de lecture/écriture). Ne pas multiplier les listeners sur de petites requêtes.

---

## 13. Ce qui exige Blaze (Cloud Functions) et ce qui peut s'en passer

Cloud Functions n'existent pas sur Spark. Aucune fonction n'est écrite à ce stade (décision du PM requise, Q1). Tableau fonction par fonction :

| Fonctionnalité | Sur Spark (sans fonction) | Avec Blaze |
|---|---|---|
| Auth, profil, créer/rejoindre une Miff, code secret, consentement | **Oui**, règles seules | — |
| Feed en temps réel (app ouverte), géofences, statut, update manuelle | **Oui** (écriture par le client, lecture par listeners) | — |
| Niveau orange/rouge **affiché** | **Oui** (calculé par les clients) | — |
| Limites freemium (1 Miff / 1 lieu), verrouillage du premium | **Oui** (slots + règles, §6) | Fonction possible pour une application plus stricte |
| Octroi du premium | **Manuel** par le PM dans la console (testeurs) | Webhook de validation d'achat (RevenueCat ou notifications App Store/Google Play) qui écrit `entitlements` : **indispensable avant tout paywall réel**, sinon on ne peut pas prouver un achat |
| **Notifications push** aux proches (entrée/sortie de lieu, arrivée dans une Miff) | **Non** : envoyer un push exige des identifiants serveur qu'on ne met jamais dans l'app | Fonction déclenchée à l'écriture de `status` → FCM |
| **Escalade orange/rouge quand l'app du proche est fermée** | **Non** : le niveau n'est visible que si l'app d'un proche est ouverte | Fonction planifiée (toutes les ~10 min) qui interroge `status` où `escalationDueAt <= maintenant` (ajouter un index de groupe de collections sur ce champ), notifie, puis avance l'échéance |
| Alerte zone à risque (premium) | Visible dans le feed seulement | Même fonction que le push |
| Photos hébergées dans Cloud Storage | **Non** (Storage exige Blaze) → photos ≤ 150 Ko dans Firestore | Migration possible : le champ `jpeg` serait remplacé par un chemin |
| Suppression de compte fiable (cascade) | Partielle, côté client (§10) | Extension "Delete User Data" ou fonction `onDelete` |
| Anti brute force du code | App Check + entropie | Fonction appelable "rejoindre" avec compteur de tentatives |
| Purge des jetons FCM périmés, TTL | Non nécessaire sans push | Fonction planifiée |

**Compromis et recommandation (décision du PM)**
- **Option A — rester sur Spark** : zéro risque de facture, suffisant pour construire et tester entre quelques personnes. Mais le produit promet de rassurer "sans ouvrir l'app" : sans push, la valeur centrale est absente, et le paywall réel est impossible.
- **Option B — passer en Blaze avant la première bêta avec de vrais utilisateurs** : on conserve les quotas gratuits, on paie seulement le dépassement ; carte bancaire requise (action du PM) ; une alerte de budget prévient mais ne bloque pas la dépense, donc App Check est obligatoire en parallèle.
- **Recommandation** : développer et tester sur Spark (les règles et le client n'ont pas besoin de Blaze), puis passer en Blaze au moment de la première bêta externe, avec alertes de budget et App Check activés. Les fonctions prévues (≈ 4 : notification de statut, escalade planifiée, validation d'achat, nettoyage de compte) resteraient très en dessous des 2 millions d'invocations gratuites par mois à ce volume (à vérifier). Région des fonctions : UE (europe-west9 si prise en charge, sinon europe-west1 : à vérifier).

---

## 14. Risques de sécurité principaux et mitigations

1. **Lecture de données d'une Miff étrangère** → tout passe par `isMember`, pas de `list` sur `miffs`, `users`, `joinCodes`.
2. **Auto-promotion premium** → aucun champ premium dans `users` ; `entitlements` en écriture interdite ; expiration contrôlée par les règles.
3. **Prise de contrôle d'une Miff (rôle, créateur)** → `ownerUid`, `role`, `type` immuables ; création du créateur seulement si la Miff est créée dans le même batch ; seul le créateur retire un membre.
4. **Deviner/énumérer les codes** → §5 (App Check à activer en mode strict, sinon ce risque reste ouvert).
5. **Trace de position après retrait de consentement / départ** → suppression obligatoire dans le même batch (règles) ; mais **la suppression reste côté client** : un bug client pourrait laisser un état tant que le membre est dans la Miff (il reste lisible aux seuls membres partageants).
5 bis. **Réutilisation de l'identifiant d'une Miff supprimée (ATK1) et code orphelin (ATK2)** → corrigés par DUR-1 : pierre tombale `miffsDeleted/{id}` (création de Miff refusée, §2.11) et suppression obligatoire du code dans le lot de suppression de la Miff (§2.5). Tests H1 et H2 (`rules-test-plan.md` §11), chacun prouvé par mutation.
5 ter. **Réciprocité contournable (ATK3)**, **retour d'un membre retiré (ATK4)** → corrigés par DUR-2 : lecture des statuts exigeant d'avoir publié son propre statut ; retrait d'un membre impossible sans renouvellement du code dans le même lot. Limites connues : photos lisibles avec le seul partage activé (décision PM) ; membre qui se retire lui-même juste avant son retrait (garde l'ancien code) ; suppression physique des photos non garantie sur Spark.
5 quater. **Écritures d'un survivant sur une Miff supprimée** (risque accepté par le PM) : voir §11 point 9 ; aucun `miffAlive` sur les écritures `status`, `photos`, `members`.
6 bis. **Consentement (b) falsifié ou modifié par un tiers (AUTH-B)** -> corrigé par les règles : bloc lisible et modifiable par son propriétaire seul, horodatage = heure serveur (impossible d'antidater), retrait avec trace conservée, version non rétrogradable, champs (a) figés, aucune clé supplémentaire (âge, e-mail...). Tests AB1 à AB17 (`rules-test-plan.md` §12), chacun prouvé par mutation. Limites : un client modifié peut ne **pas** respecter l'état local (collecter sans consentement) : le consentement n'est pas techniquement contraignant côté appareil, il l'est seulement par le code des applications (revue de code et QA, critères X9 et C13 du ticket) ; la **version courante** du texte n'est connue que du client (les règles ne peuvent pas la comparer à une valeur de référence).
7. **Pas de profil exigé pour créer ou rejoindre une Miff (AUTH-B, constat, non modifié)** : les règles de `miffs`, `members` et `memberships` ne vérifient pas que `users/{uid}` existe, donc qu'(a) a été enregistrée. Seuls les clients officiels imposent « pas d'usage avant (a) ». Exiger le profil dans ces lots coûterait une lecture de règles de plus par création/jonction et casserait des tests existants : **question ouverte pour le PM** (§15, question 20).
8. **Comparaison de versions par ordre alphabétique (AUTH-B)** : la règle « pas de rétrogradation » compare deux textes. Validée dans l'émulateur Firestore ; la documentation officielle consultée ne précise pas cet opérateur sur les chaînes. En cas de refus par le serveur réel, l'échec est **fermé** (la réactivation après retrait serait refusée, aucun accès en plus) ; à reconfirmer au premier essai sur le projet de développement.
6. Résiduel : abus de volume par un compte légitime (écritures en boucle, avatars) → tailles bornées, mais pas de limitation de débit en règles ; App Check + surveillance des quotas.

---

## 15. Décisions du PM (2026-10-16) et questions encore ouvertes

### Décisions prises
| # | Sujet | Décision |
|---|---|---|
| 1 | Plan Blaze | **Non pour l'instant.** On développe sur Spark ; on réévaluera au moment où une fonctionnalité l'exige (notifications push, escalade app fermée, validation d'achat). Aucune Cloud Function n'est écrite. |
| 2 | Photos d'update | Stockées dans Firestore (≤ 150 Ko, 24 h). **La photo n'est pas obligatoire.** |
| 3 | Premium | **Par utilisateur** (`entitlements/{uid}`), pas par Miff. |
| 4 | Réciprocité | Réciprocité stricte retenue par défaut (on ne voit les autres que si on partage soi-même), modifiable via un ticket futur. |
| 5 | Lieux | **Seul le créateur de la Miff** crée/modifie/supprime les lieux (confiance et danger). **Zone de danger = premium uniquement.** |

Les autres questions ci-dessous seront tranchées au fil des tickets, quand la fonctionnalité concernée sera rédigée.

### Questions ouvertes pour le PM (détail)

1. **Blaze** : accord pour passer en Blaze (carte bancaire + alerte de budget) avant la première bêta externe ? Sinon, accepter qu'il n'y ait ni push, ni escalade app fermée, ni paywall réel.
2. **Photos** : Firestore (≤ 150 Ko, 24 h, sans Blaze) ou Cloud Storage (meilleure qualité, exige Blaze) ? Durée de vie de la photo d'update (24 h proposé) ? La photo est-elle obligatoire dans une update ?
3. **Premium** : par utilisateur ou par Miff ? Qui bénéficie des zones à risque et des notifications intelligentes (le créateur premium, ou tout le cercle) ? Que devient l'excédent (lieux, Miffs) à l'expiration ?
4. **Compte du quota "1 lieu"** : la zone à risque est supposée premium ; le gratuit = 1 zone de confiance. À confirmer.
5. **Qui définit les zones à risque** : le créateur seulement (supposé) ou chaque membre pour lui-même ? Que déclenche l'entrée (alerte immédiate à tout le cercle) ?
6. **Réciprocité** : confirmer qu'un membre qui ne partage pas sa position ne voit pas celle des autres (supposé, pour limiter le risque de surveillance à sens unique). Un parent doit-il pouvoir voir un enfant sans partager la sienne (ce qui lèverait la réciprocité) ?
7. **Code d'invitation** : visible par tous les membres (supposé) ou seulement le créateur ? Régénération (prévue) ? ~~Faut-il bloquer le retour d'un membre retiré ?~~ **Tranché le 2026-10-22 : le retrait d'un membre renouvelle le code (imposé par les règles, DUR-2) ; le départ volontaire ne change rien.** Validation du créateur à l'arrivée ? Taille maximale d'une Miff (coût du feed proportionnel) ?
8. **Créateur qui part** : suppression de la Miff (supposé), transfert de propriété, ou impossible tant que d'autres membres restent ?
9. **Géofences au-delà de la limite** : priorité manuelle (`priority`), proximité ou fréquence de visite ? Prévenir l'utilisateur qu'un lieu n'est plus surveillé ?
10. **Escalade** : orange et/ou rouge déclenchent-ils un push ? Que fait-on d'un membre sans permission de localisation arrière-plan (aujourd'hui : pas d'escalade sans départ connu) ? Si un membre reste rouge, quelle relance ?
11. **Update manuelle** : suffit-il de la poster pour repasser au vert, ou faut-il un geste "je vais bien" explicite ?
12. **Mineurs** : l'app est-elle ouverte aux mineurs ? Âge minimum, consentement parental, collecte d'un âge ou d'une date de naissance (non prévue pour l'instant) ?
13. **Juridique** : bases légales (§11), analyse d'impact, durées de conservation, conservation éventuelle de la preuve de consentement après départ, transferts hors UE, procédure d'export des données.
14. **E-mail vérifié** : ~~exigé avant de rejoindre/créer une Miff ?~~ **Tranché le 2026-10-22 : non exigé** (les règles ne testent pas `email_verified`).
15. **Nom/prénom** : conserver la règle "3 caractères minimum" des maquettes (elle exclut des prénoms réels, ex. "Li") ?
16. **Désactivation du partage** : notifier les autres membres ? (ton à définir.)
17. **Rayon** : bornes 50–2 000 m acceptables ? Minimum recommandé côté UI : 100 m.
18. **Suppression de compte d'un créateur** : supprimer sa Miff et les données des autres membres associés, ou transférer ?
20. **Profil exigé avant de créer/rejoindre une Miff ?** (AUTH-B, §14 point 7) : faut-il que les règles refusent la création ou la jonction d'une Miff tant que `users/{uid}` n'existe pas (preuve serveur d'(a)) ? Coût : une lecture de règles de plus par lot ; effet : un client modifié ne peut plus contourner le consentement (a).
19. **Fournisseur d'abonnement** : validation native (App Store / Play Billing) ou service tiers (RevenueCat) — conditionne la fonction de webhook.
