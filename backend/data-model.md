# Miff — modèle de données Firestore (source de vérité)

Projet Firebase : `miff-3d283` — Firestore `europe-west9` (Paris), édition Standard, plan Spark aujourd'hui.
Ce document est la référence unique pour `ios-dev` et `android-dev` : même structure, mêmes noms de champs, mêmes batches. Toute évolution passe par `backend-data`.

**Statut de validation** : les règles (`firestore.rules`) sont testées dans l'émulateur Firestore (`rules-test-plan.md`, résultats dans `rules-test-report.md`) et **les règles de DUR-1 et DUR-2 sont déployées sur `miff-3d283`** (information donnée par le PM le 2026-10-23 ; l'agent `backend-data` n'a pas accès à la console : à confirmer par la date de publication affichée dans Firestore > Règles). Tout nouveau déploiement suit `docs/dev/deploiement-regles.md` (commandes avec `--project miff-3d283` explicite, jamais depuis la CI, OK explicite du PM, retour arrière documenté). Ce document ne prétend à aucune conformité juridique : il décrit ce que le modèle implique (§11).

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
- **(a) Conditions de service et politique de confidentialité : obligatoire.** Inchangé dans son principe : `termsAcceptedAt` (heure serveur) et `termsVersion` sont écrits à la création du profil et **sans eux le profil est refusé**. Nouveauté : **ces deux champs ne sont plus modifiables après la création** (décision PM : « non modifiable »). Les règles précédentes (celles de `main` au 2026-10-23, commit 1017391, déployées sur `miff-3d283` depuis le poste du PM ; celles de cette PR ne sont **pas** déployées) autorisaient leur mise à jour ; le ticket futur « nouvelle version des CGU » devra rouvrir cette règle explicitement.
- **(b) Statistiques d'usage et rapports de plantage anonymes : plus de case ; consentement réputé donné au clic sur « Rejoindre » (décision PM du 2026-10-28, risque assumé), `enabled = true` écrit à la création du profil ; révocable dans les Réglages.** (Version du 2026-10-23, remplacée : facultatif, décoché par défaut.) Nouveau bloc facultatif `analyticsConsent` (§2.1) : état, date de consentement (heure serveur), version du texte, date de retrait. **Absence du bloc = pas de consentement.** Modifiable ensuite par l'utilisateur lui-même seulement (Réglages) : activer, retirer, réactiver ; la version ne peut pas reculer.
- **Pas de Miff sans profil (décision PM du 2026-10-24, question 21)** : créer une Miff **ou** en rejoindre une par code est refusé tant que `users/{uid}` n'existe pas avec `termsAcceptedAt` et `termsVersion`. Le profil peut être créé **dans le même lot** (contrôle `getAfter`) : « profil + Miff » en un lot de 5 écritures, ou « profil + adhésion » en un lot de 3. Quitter, être retiré, retirer un membre et supprimer une Miff **ne dépendent pas** du profil (le départ n'est jamais bloqué).
- Aucune date de naissance, aucun âge, aucun e-mail : toute autre clé est refusée (déjà le cas, maintenant testé : AB17).
- Un utilisateur social qui **refuse** le panneau de consentement n'a **aucun document** : le client n'écrit rien dans Firestore avant l'acceptation, puis supprime le compte d'authentification (ticket §7.8). Les règles interdisent déjà de créer un profil sans (a).

*Impact PM* : aucun coût supplémentaire (la lecture du profil qui sert au routage au lancement lit aussi le consentement ; la création ou la mise à jour du profil n'ajoute aucune lecture de règles) ; la preuve du consentement (b) est **supprimée avec le profil** (§10) ; l'horodatage serveur est celui de la **création du profil**, pas du clic (point RGPD assumé par le PM, §11).

**DUR-3 (appliqué dans cette version, côté dépôt uniquement ; `firestore.rules` n'est pas modifié)** : tout ce qui se fait avant les premiers testeurs externes sans toucher aux vrais services.
- **Garde-fou de projet (R16)** : `backend/.firebaserc` ne déclare plus aucun projet (avec un seul projet déclaré, la CLI le prend comme projet implicite : constaté le 2026-10-23, `emulators:exec` sans `--project` partait avec `miff-3d283`). Les tests refusent un script npm, un workflow ou un script shell qui appelle `firebase` sans `--project demo-*` ou autrement que pour l'émulateur, un déploiement ou un jeton dans un workflow, et `GCLOUD_PROJECT=miff-3d283` / `ghostrun-app` ; le garde-fou lit le projet réellement annoncé par l'émulateur.
- **Test par mutation étendu (R14)** : toutes les protections de la liste du ticket (§7.3) ont maintenant au moins une mutation ; les survivants ont été tués par de nouveaux tests (section 12 du plan de test) ou documentés comme équivalents.
- **Mesure des lectures de règles fiable (R15)** : cause des mesures « non concluantes » établie et corrigée (voir `rules-test-report.md`) ; une mesure non concluante, discordante ou à marge inférieure à 2 fait échouer le test.
- **Contrat de purge du cache** ajouté au §8 (ligne « Purge du cache local »).
- **Photos** : suppression automatique (TTL) = fonction payante, non retenue sur Spark ; solution gratuite documentée (§2.8, §10, §11).
- **Signal de bascule vers Blaze, risque résiduel « comptes jetables »** : §12 ; procédures pour le PM dans `docs/dev/surveillance-quotas.md`, `docs/dev/deploiement-regles.md` et `docs/dev/app-check-strict.md`.

*Reste au PM (console, hors dépôt)* : enregistrer puis mesurer puis appliquer App Check, vérifier ce que la console propose pour TTL (ne pas activer la facturation), tester les alertes d'usage, trancher la question Blaze avant la première bêta externe.

**ONB-B (téléphone obligatoire) et MIFF-0 (lot d'entrée avec statut initial) : appliqués dans cette version ; règles DÉPLOYÉES sur `miff-3d283` depuis le 2026-10-30 (information donnée par le PM ; à confirmer par la date de publication affichée dans Firestore > Règles)** (tickets `docs/tickets/onboarding.md` §6.4, §8 et `docs/tickets/miff.md` §5.4, §9 ; décisions du PM du 2026-10-24).
- **Téléphone** : nouvelle clé `phone` (E.164, regex `^[+][1-9][0-9]{6,14}$`) **obligatoire** à la création de `users/{uid}` et de la fiche `members/{uid}`, modifiable, jamais vidée (§2.1, §2.6). **La copie de la fiche doit être égale au numéro du profil à l'issue du lot** (`getAfter`, **0 lecture de règles de plus**, §12) : vérifiable, donc vérifiée. Aucune règle de lecture ajoutée : le numéro n'est lisible de quelqu'un d'autre que par la fiche de membre, c'est-à-dire des seuls membres de la Miff (décision N1, option A).
- **Lot d'entrée** (§4, §8) : adhésion ou création + consentement de partage activé + `status/{moi}` initial en UN lot atomique : **les règles le permettaient déjà** ; ajouté : tests (EN1 à EN6), y compris l'échec d'une écriture (rien n'est écrit), et mesure des lectures de règles (§12).
- **Compatibilité** : les apps qui écrivent un profil ou une fiche **sans `phone`** (AUTH-B actuel d'Android et d'iOS) seront **refusées** dès le déploiement de ces règles ; ONB-2 et ONB-6 écrivent le numéro. Les règles ont été déployées **avant** la livraison d'une app qui écrit le numéro (ticket Onboarding §8, point 7), avec l'accord explicite du PM ; ONB-2 et ONB-3a (Réglages > Mes informations) écrivent le numéro. Les profils et fiches existants sans numéro restent valides et modifiables.
- Les cas de test `AB13` et `AB17` de l'authentification sont adaptés (`phone` n'est plus une clé inconnue ; un numéro de forme nationale reste refusé). Aucun autre test existant n'a été modifié ; les fixtures portent désormais un numéro.

*Impact PM* : aucun coût supplémentaire (aucune lecture de règles de plus), une donnée de contact de plus à déclarer (§11 points 17 à 22).

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
| `phone` | string E.164, **obligatoire** | **ONB-B (décision Q1 du PM).** `+` puis 7 à 15 chiffres, premier chiffre de 1 à 9 (8 à 16 caractères au total), **sans espace ni séparateur** : regex `^[+][1-9][0-9]{6,14}$` appliquée par les règles ; la normalisation (pays choisi + saisie nationale -> E.164) est faite par le client, jamais de format national en base. **Le pays n'est pas stocké** (il se déduit de l'indicatif : minimisation). **Aucune vérification** (pas de SMS sur Spark) et **aucune unicité**. Modifiable par son propriétaire (Réglages > Mes informations), **jamais vidé ni supprimé** (une écriture qui supprime la clé, la met à `null` ou à une forme invalide est refusée). **Sensible** (donnée de contact). |
| `createdAt` | timestamp | = heure serveur. **Immuable.** |
| `termsAcceptedAt`, `termsVersion` | timestamp, string 1–20 | **(a)** Preuve d'acceptation des CGU/politique de confidentialité. **Obligatoires** à la création (pas de profil sans eux) ; `termsAcceptedAt` = heure serveur ; **immuables** après la création (décision PM, AUTH-B). `termsVersion` : identifiant de la version publiée des textes (les règles n'en imposent pas le format). |
| `analyticsConsent` | map, **facultatif** | **(b)** Consentement aux statistiques d'usage et rapports de plantage anonymes. Absent = pas de consentement. Voir ci-dessous. |

Lisible et modifiable **uniquement** par son propriétaire. Les autres membres voient la copie dans `members/{uid}` (**c'est la seule voie par laquelle un numéro est visible d'un tiers**). Pas de champ premium ici (volontairement). **Aucune date de naissance, aucun âge, aucun e-mail, aucune position, aucun pays** : toute autre clé est refusée par les règles. Le téléphone est la seule donnée de contact (ONB-B).

**Clés à la création (huit au plus)** : `firstName`, `lastName`, `phone` (obligatoires), `avatar` (facultatif), `createdAt`, `termsAcceptedAt`, `termsVersion` (obligatoires), `analyticsConsent` (facultatif, écrit toujours par le client). **Mise à jour** : seuls `firstName`, `lastName`, `avatar`, `phone` et `analyticsConsent` peuvent changer. **Compte antérieur à ONB-B** (profil sans `phone`) : il reste valide ; un changement de prénom ou de photo reste accepté sans numéro, et l'ajout du numéro (étape « 2b » du routage de l'Onboarding) est une mise à jour du profil **uniquement si le profil porte déjà `termsVersion`**. **Compte antérieur à AUTH-B** (profil sans `termsAcceptedAt` / `termsVersion`) : l'ajout du numéro par `update` est **refusé** (la règle de mise à jour lit `termsVersion`, absent = erreur d'évaluation = refus) ; la seule issue est de **supprimer puis recréer le profil** en une écriture complète (CGU acceptées de nouveau). **Le routage de l'Onboarding (étape 2b) ne doit donc pas supposer un simple `update`** : si l'`update` est refusé, basculer sur la création complète du profil (écran CGU compris). Limite connue, non corrigée ici (aucune règle modifiée). Tant qu'il n'a pas de numéro, ce compte **ne peut ni créer ni rejoindre de Miff** (la fiche de membre exige un numéro identique à celui du profil, §2.6).

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
**Longueur du nom (MIFF-1, constaté à l'émulateur le 2026-10-31, tests ND1 à ND3)** : `name` et `miffName` sont comptés en **unités UTF-16** (un emoji compte pour 2 ; 30 emojis = 60 unités sont refusés ; 40 lettres accentuées sont acceptées : on compte des caractères, pas des octets). Les clients limitent donc à 40 unités UTF-16 (`String.length` en Kotlin, `utf16.count` en Swift), sans couper un emoji en deux.

**Création** : refusée si `miffsDeleted/{miffId}` existe (un identifiant supprimé ne sert plus jamais). **Suppression** (créateur) : refusée si, dans le même lot, la pierre tombale `miffsDeleted/{miffId}` n'est pas créée **ou** si `joinCodes/{joinCode}` existe encore après le lot.

### 2.6 `miffs/{miffId}/members/{uid}` — profil (copie), rôle, consentement
| Champ | Type | Note |
|---|---|---|
| `role` | `"owner"` \| `"member"` | Immuable. |
| `firstName`, `lastName`, `avatar` | comme `users` | **Copie** (dénormalisation : évite N lectures de profils). Le client met à jour toutes ses copies quand le profil change. |
| `phone` | string E.164, **obligatoire à la création de la fiche** | **ONB-B.** Copie du numéro de `users/{uid}` (même forme, §2.1). **Les règles vérifient que la copie est identique au numéro de `users/{uid}` après le lot** (`getAfter` sur le profil, qui peut être créé dans le même lot) à la création **et à chaque mise à jour du numéro de la fiche** : on ne peut donc pas inscrire dans une fiche un autre numéro que celui de son propre profil (**mesuré : aucune lecture de règles de plus**, §12). **Visible de tous les membres de la Miff, partage de position ou non** (décision N1 du PM, option A), et d'eux seuls. Disparaît avec la fiche (départ, retrait, suppression de la Miff, suppression du compte). |
| `joinedAt` | timestamp | Heure serveur, immuable. |
| `joinCode` | string | Présent seulement pour `role = member` : code utilisé pour rejoindre (preuve exigée par les règles). Immuable. |
| `sharing.enabled` | bool | Le membre partage-t-il sa position avec CETTE Miff ? |
| `sharing.consentedAt` | timestamp \| null | Date du consentement explicite (heure serveur). |
| `sharing.consentVersion` | string \| null | Version du texte de consentement affiché (ex. `"2026-10-v1"`). |
| `sharing.revokedAt` | timestamp \| null | Date de révocation, `null` si actif. |

Exemple :
```json
{
  "role": "member", "firstName": "Amandine", "lastName": "Roy", "phone": "+33612345678", "joinedAt": "2026-10-16T09:00:00Z",
  "joinCode": "K7M2QX9PDW4H",
  "sharing": { "enabled": true, "consentedAt": "2026-10-16T09:00:00Z", "consentVersion": "2026-10-v1", "revokedAt": null }
}
```
Lisible par tous les membres de la Miff, tant que la Miff existe (c'est la liste des membres). Un membre ne modifie que son profil (prénom, nom, photo, **téléphone**) et son consentement.

**Cohérence profil / fiches (R5 de l'Onboarding) : ce que les règles garantissent et ce qu'elles ne garantissent pas.**
- Garanti : à chaque écriture du numéro d'une fiche, la valeur est celle de `users/{uid}` **à l'issue du lot**. Une fiche ne peut jamais contenir un numéro que son propriétaire n'a pas lui-même dans son profil (un numéro invalide dans un profil injecté est refusé aussi).
- **Non garanti (limite connue)** : les règles ne peuvent pas obliger à mettre à jour **toutes** les fiches quand le profil change (elles ne connaissent pas la liste des Miffs de la personne). Si le client ne met à jour que le profil, les fiches gardent l'**ancien** numéro jusqu'à la prochaine tentative (le client réessaie automatiquement) ; pendant ce temps les proches voient l'ancien numéro. Un client modifié peut laisser des fiches périmées indéfiniment : c'est son propre numéro qui est périmé, rien de plus.
- **Non vérifié** : le numéro n'est pas prouvé (aucun SMS) ; deux comptes peuvent déclarer le même numéro ; une personne peut déclarer le numéro d'un tiers (risque assumé, §11).
- **Mise à jour en un lot (contrat client, §8)** : profil + une écriture par fiche. Chaque fiche coûte au plus **1 lecture de règles** dans le pire cas sans cache ; la limite est de 20 par lot, donc **au plus 15 fiches par lot** (marge de 5) ; au-delà (compte premium dans plus de 15 Miffs), mettre le profil à jour d'abord (lot seul), puis les fiches par lots de 15 au plus. Mesuré dans l'émulateur : 15 fiches + profil en un lot passent (test PH9b).

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

Suppression : l'auteur, le créateur, ou **tout membre de la Miff une fois la photo expirée** (nettoyage opportuniste : l'app qui constate une photo expirée la supprime, et son auteur supprime la sienne à chaque lancement et mise à jour avec `hasPhoto = false`). **Limite connue** : sur Spark, aucune suppression automatique garantie. La documentation Google (page des quotas Firestore, mise à jour le 2026-10-21) liste les « TTL deletes » parmi les fonctions **sans usage gratuit** (« you must enable billing to use these features »), et indique que les suppressions TTL ont lieu « typiquement dans les 24 heures » après l'expiration (donc jamais exactement à 24 h, même avec Blaze) ; un document expiré reste lisible tant que le TTL ne l'a pas supprimé, ce qui rend de toute façon nécessaire le refus de lecture par les règles. **Solution retenue (gratuite, 3 couches)** : (1) les règles refusent la lecture dès `expiresAt` ; (2) l'auteur supprime sa photo expirée à chaque lancement et mise à jour ; (3) tout membre supprime la photo expirée d'un autre. **Borne de stockage** : au plus une photo de 150 Ko par membre et par Miff (elle écrase la précédente), donc 1 GiB gratuit ≈ 7 000 photos simultanées au pire, loin du premier plafond. La formule exacte est donc « **illisible après 24 h ; suppression physique au mieux** », risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique) (§11).

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
- **Lot d'entrée (MIFF-0, décision QM2 du PM et son extension à la création)** : « Rejoindre et partager » et « Créer et partager » sont le **consentement explicite** de cette Miff. Le client écrit, en **un seul lot atomique** : la fiche de membre avec `sharing = {enabled:true, consentedAt:<heure serveur>, consentVersion:<version du texte affiché avant le bouton>, revokedAt:null}`, l'index `memberships` et le statut initial `status/{moi}` (`presence = "unknown"`, §8). Les règles n'ont **pas besoin de changer** pour cela (la fiche peut naître avec `enabled = true` ; le statut exige le partage actif **après** le lot) : testé (EN1 à EN6), y compris le cas où une écriture est refusée (rien n'est écrit) et mesuré (§12). Les règles **n'imposent pas** que le premier statut soit `unknown` (convention du client, test EN5) ni que la version du texte soit la version courante (seulement 1 à 20 caractères).
  **Versions du texte de consentement (Android, MIFF-1)** : `2026-10-v1` = la phrase du PM + « Ce que les membres voient de toi » (prénom, nom, photo, numéro de téléphone) + le rappel sur la permission du téléphone ; **`2026-10-v2`** (en vigueur) ajoute la phrase « Les lieux de confiance de cette Miff sont choisis par son créateur et visibles de tous ses membres » (l'entrant consent avant de voir les lieux, R1 du ticket Miff). La même version est écrite à la création, à l'entrée et à la reprise du partage ; iOS (MIFF-3) reprend la même constante et les mêmes mots. Le format n'est pas imposé par les règles (1 à 20 caractères).

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
| **1 Miff à la fois (gratuit)** | L'index `users/{uid}/memberships/free` ne peut être créé qu'une fois (un second `create` devient un `update`, interdit). Un document membre n'est accepté que si un index pointant vers la Miff existe dans le même batch. Une clé autre que `free` exige `entitlements` actif. | (a) **Fin du Premium (décision PM du 2026-10-24)** : les Miffs au-delà de la limite gratuite, **les dernières ajoutées**, sont **supprimées** (celles que l'utilisateur a créées : séquence « Supprimer une Miff » du §8 ; celles qu'il a rejointes : lot « Quitter »). L'ancienne règle « les Miffs restent conservées après expiration » est **abandonnée**. Les règles actuelles ne l'appliquent pas (elles tolèrent l'excédent) : préavis, notification bienveillante et choix de l'élément conservé seront définis dans le ticket Premium. (b) Si un membre est retiré, son index reste jusqu'à ce que son appli le nettoie (au lancement) : tant que ce n'est pas fait il ne peut pas rejoindre une autre Miff en gratuit. (c) Un client modifié peut ouvrir un second **compte** gratuit : propre à tout freemium. |
| **1 lieu de confiance (gratuit)** | En gratuit, seul l'identifiant `p1` et le type `trusted` sont acceptés pour créer un lieu. Premium : identifiants libres et zones à risque. | (a) Le compte "lieux" est par Miff ; comme un compte gratuit n'a qu'une Miff, cela revient à 1 lieu. (b) **Fin du Premium (décision PM du 2026-10-24)** : les lieux au-delà de la formule gratuite (1 lieu de confiance), **les derniers ajoutés**, zones à risque comprises, sont **supprimés** ; l'ancienne règle « les lieux premium sont conservés » est **abandonnée**. Non appliqué par les règles actuelles ; préavis, notification bienveillante et choix de l'élément conservé : ticket Premium. (c) Un membre gratuit d'une Miff dont le créateur est premium bénéficie des lieux : voulu par la spec ("les membres héritent"). |
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
| **Inscription / profil** (AUTH-B) | — | **Une seule écriture** `users/{uid}` **set** (création, faite **à la fin de l'étape téléphone** de l'Onboarding, avec tout le profil d'un coup) avec **toutes** ces clés : `firstName`, `lastName`, **`phone` (E.164, obligatoire, ONB-B : le client normalise pays + saisie nationale en `+` et chiffres, sans espace)**, `avatar` (facultatif), `createdAt` = `serverTimestamp()`, `termsAcceptedAt` = `serverTimestamp()`, `termsVersion` (version affichée à l'inscription et conservée sur l'appareil jusqu'ici), et **`analyticsConsent` écrit toujours** : depuis la décision du 2026-10-28 (plus de case à cocher, consentement réputé donné au clic sur « Rejoindre ») la valeur écrite à l'inscription est `{enabled:true, consentedAt:serverTimestamp(), consentVersion:<constante>, revokedAt:null}` ; la forme `enabled:false` reste acceptée pour un retrait ultérieur dans les Réglages (les règles acceptent aussi l'absence du bloc, mais le client l'écrit toujours pour que l'état soit explicite). **Ne jamais** écrire de date du client pour ces champs (refusé), ni âge, date de naissance, e-mail, pays ou toute autre clé (refusés) ; un `phone` absent, nul ou hors E.164 est refusé. Modif. de profil : `users/{uid}` update **limité à** `firstName`, `lastName`, `avatar`, `phone` (+ `analyticsConsent`, voir ci-dessous) + `members/{uid}` update dans **chaque** Miff (liste via `memberships`). **Modifier le téléphone (Réglages > Mes informations) : UN seul lot** `users/{uid}` update `{phone}` + `members/{uid}` update `{phone}` dans chaque Miff (la copie doit être égale au profil après le lot, sinon tout le lot est refusé ; au plus 15 fiches par lot, voir §2.6). **Piège (membre retiré)** : si la personne a été retirée d'une Miff, sa fiche n'existe plus mais son index `memberships` peut subsister (il n'est pas effaçable par le créateur) ; une écriture « update » sur la fiche manquante fait refuser **tout le lot**. Le client fait donc d'abord le **nettoyage au lancement** (§8, « Nettoyage au lancement / Perte d'accès ») et ne met à jour que les fiches des Miffs confirmées, ou, en cas de refus, **replie sur « profil seul, puis fiches une à une »** (chaque fiche refusée est ignorée et son index nettoyé) ; le numéro ne peut pas être vidé (refusé). **Compte antérieur sans numéro** : ajouter `phone` par un update du profil (étape 2b) avant de créer ou rejoindre. **`createdAt`, `termsAcceptedAt`, `termsVersion` ne se modifient jamais** (refusé). |
| **Nouvel utilisateur social (Google / Apple) : panneau de consentement** (AUTH-B, ticket §7.8) | — | **Aucune écriture Firestore avant l'acceptation de (a).** Acceptation : mêmes valeurs que ci-dessus, dans l'écriture de création du profil à l'étape profil. **Refus** (Annuler, retour, fermeture) : **ne rien écrire**, supprimer le compte d'authentification tout juste créé, purger le cache local ; il n'y a aucun document `users/{uid}` à supprimer (si, par erreur, un `delete users/{uid}` est envoyé, il est accepté et sans effet). Aucun événement Analytics ni rapport de plantage, même si (b) était cochée sur le panneau. Suppression du compte impossible (hors connexion) : rester sur le panneau, sans accès au reste de l'app, jamais de profil créé. |
| **Réglages : statistiques et plantages** (AUTH-B ; l'écran est dans le ticket Réglages) | `get users/{uid}` (déjà lu pour le routage du lancement : **aucune lecture de plus**) : lire `analyticsConsent` ; absent = désactivé | **Activer / réactiver** : `users/{uid}` update `{analyticsConsent: {enabled:true, consentedAt:serverTimestamp(), consentVersion:<constante>, revokedAt:null}}` avec une `consentVersion` **égale ou plus récente** que celle déjà enregistrée. **Retirer** : update `{analyticsConsent: {enabled:false, consentedAt:<valeur lue>, consentVersion:<valeur lue>, revokedAt:serverTimestamp()}}` (recopier à l'identique les deux valeurs lues ; ne jamais les effacer). Ne pas tenter d'« activer » un consentement déjà actif ni de « retirer » un consentement jamais donné (refusé). **Le retrait coupe la collecte sur l'appareil immédiatement, avant même la fin de l'écriture** (hors connexion, l'écriture part plus tard ; c'est l'état local qui commande la collecte). **À l'activation, la collecte ne démarre qu'après l'acceptation de l'écriture** (si elle est refusée : collecte restée coupée, message neutre, pas de nouvelle tentative en boucle). **Au retrait, recopier l'objet `Timestamp` `consentedAt` lu tel quel (ne pas le reconvertir en Date ni en millisecondes) : à tester au premier essai sur un projet de développement.** **Limite connue : la preuve de consentement n'est pas infalsifiable par son propriétaire** (il peut supprimer puis recréer son profil, ce qui efface et réécrit la trace). |
| **Connexion / nouvel appareil : appliquer le choix (b)** (AUTH-B, ticket §8) | `get users/{uid}` (la lecture du routage, §5 du ticket) | La collecte reste **coupée** tant que ce document n'a pas été lu. Lu : `analyticsConsent.enabled == true` -> activer la collecte sur cet appareil ; bloc absent, `enabled == false` ou lecture impossible -> collecte coupée, `sign_in_completed` non envoyé. `sign_up_completed` : envoyé après la création du compte, le consentement statistiques étant donné au clic (aucune case depuis la décision PM du 2026-10-28). L'application d'un consentement donné sur un autre appareil est un point RGPD assumé par le PM (§11). |
| **Créer une Miff** (**prérequis : profil `users/{uid}` avec (a), AUTH-B**) | `entitlements/{uid}` (savoir quelle clé utiliser) ; `memberships` (si gratuit et déjà un `free` → afficher le paywall sans tenter l'écriture) | Générer `miffId` (ID auto) et `code` (aléa cryptographique). **Le profil doit exister, ou être créé dans le même lot** (alors lot de 5 : ajouter `users/{uid}` set au début, avec toutes les clés de la ligne « Inscription / profil »). Sans profil : refus (`permission-denied`), à ne jamais présenter comme une erreur de code ou de Miff : revenir à l'étape profil. **Lot d'entrée « Créer et partager » (MIFF-0), UN seul lot atomique de 5** (6 si le profil est créé dans le même lot) : `miffs/{miffId}` create {ownerUid, name, joinCode, createdAt} ; `joinCodes/{code}` create {miffId, miffName, createdAt} ; `miffs/{miffId}/members/{uid}` create {role:"owner", `firstName`, `lastName`, `avatar` si présent, **`phone` identique à celui de `users/{uid}`**, joinedAt, **sharing = {enabled:true, consentedAt:serverTimestamp(), consentVersion:<version du texte affiché avant le bouton « Créer et partager »>, revokedAt:null}**} ; `users/{uid}/memberships/{key}` create {miffId, joinedAt} avec `key="free"` si non premium, sinon `miffId` ; **`miffs/{miffId}/status/{uid}` create : statut initial** `presence="unknown"`, `placeId=null`, `presenceAt` et `lastUpdateAt` = heure serveur, `updateText=null`, `hasPhoto=false`, `batteryPct=null`, `locationAccess` = permission réelle de l'appareil, `escalationDueAt=null`, `updatedAt` = heure serveur. **Si une seule écriture est refusée, aucune n'est écrite** (testé : EN4). Une fiche sans `phone`, ou dont le numéro diffère de celui du profil, est refusée. Collision de code (très improbable) : régénérer et réessayer. |
| **Rejoindre une Miff** (**prérequis : profil `users/{uid}` avec (a), AUTH-B**) | `joinCodes/{CODE}` (get) | Après l'écran de confirmation + consentement. **Le profil doit exister, ou être créé dans le même lot** (lot de 3). Un refus sans profil ne doit pas être confondu avec le message « Ce code ne correspond à aucune Miff ». **Lot d'entrée « Rejoindre et partager » (MIFF-0), UN seul lot atomique de 3** (4 avec le profil) : `members/{uid}` create {role:"member", joinCode, profil copié **dont `phone` identique à celui de `users/{uid}`**, joinedAt, **sharing = {enabled:true, consentedAt:serverTimestamp(), consentVersion:<version du texte affiché avant le bouton « Rejoindre et partager »>, revokedAt:null}**} + `memberships/{key}` create + **`status/{uid}` create : statut initial** (mêmes valeurs que pour la création, ligne ci-dessus). Si une seule écriture est refusée, aucune n'est écrite (testé : EN3) ; sans statut, la fiche est créée mais la lecture des autres statuts reste refusée tant qu'il n'est pas publié (ATK3, test EN3b). La permission de localisation du système reste demandée à part et ne bloque jamais ce lot. **À savoir (risque assumé R7 de l'Onboarding)** : dès l'entrée, la personne lit les numéros de tous les membres ; une fuite du code est donc aussi une fuite des numéros. Erreur "introuvable" ou refus : message unique et bienveillant ("Ce code ne correspond à aucune Miff"), sans distinguer les causes. Limiter les essais côté UI (ex. pause après 5 échecs). |
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
| **Nettoyage au lancement / Perte d'accès** | `get miffs/{id}` pour chaque index (refus = retiré ; introuvable = Miff supprimée) | Message neutre (« Tu ne fais plus partie de la Miff « X » » / « La Miff « X » n'existe plus »), sans reproche. Puis, **sans pouvoir lire ma propre fiche** (sur une Miff supprimée, `get members/{moi}` est refusé par le contrôle d'existence de la Miff : on ne peut donc pas tester « ma fiche existe encore ») : **essayer d'abord le batch « Quitter » complet** (delete fiche + statut + photo + index ; la suppression d'un document absent est sans effet) ; **s'il est refusé** (retiré : la fiche n'existe plus), **en repli supprimer l'index seul** (autorisé car je ne suis plus membre) ; puis purger le cache local (ligne « Purge du cache local » ci-dessous). Supprimer aussi ma propre photo expirée (`hasPhoto = false`) et celle d'un autre membre si je la constate expirée (autorisé à tout membre). |
| **Purge du cache local** (déconnexion, suppression de compte, perte d'accès à une Miff) | — | **Contrat identique iOS et Android, dans cet ordre** (ticket durcissement §5.3, R17) : (1) arrêter les écouteurs Firestore et la surveillance de géofences concernés ; (2) laisser partir les écritures en attente : `waitForPendingWrites()` avec un délai court (5 s), **avant** la déconnexion Auth (sans compte connecté, les règles refusent l'envoi et ces écritures seraient perdues) ; (3) enregistrer localement un drapeau « purge du cache à faire » ; (4) `terminate()` du client Firestore, **puis** `clearPersistence()` (Android : `FirebaseFirestore.terminate()` puis `clearPersistence()` ; iOS : `terminate` puis `clearPersistence` de `Firestore` — mêmes noms, signatures exactes à confirmer par `ios-dev`) ; (5) effacer le drapeau ; (6) seulement ensuite `signOut()` pour une déconnexion ; à la connexion suivante, créer un nouveau client Firestore (la documentation Firebase demande de recréer l'instance après `terminate()`). `clearPersistence()` n'est possible que si le client n'est **pas démarré** (sinon l'appel échoue en `FAILED_PRECONDITION`, documentation Android) : si l'étape 4 échoue (application tuée, client encore actif), le drapeau est relu au **prochain démarrage** et la purge est faite **avant tout autre accès à Firestore**. Perte d'accès à une Miff (retrait, Miff supprimée) : même purge complète, sans `signOut()` (le cache se reconstruit à la prochaine ouverture). Vider **tout** le cache est voulu (simplicité). **Limites** : `clearPersistence()` abandonne les données par un mécanisme efficace mais « n'écrase pas et ne rend pas irrécupérables » les octets (documentation Android) ; Android : `allowBackup` désactivé (déjà exigé) ; iOS : exclusion du cache des sauvegardes **à vérifier par `ios-dev`** ; couvert par le risque juridique assumé par le PM (décision du 2026-10-23, §11). **Preuve attendue (R17, QA sur appareil)** : se déconnecter, couper le réseau, relancer l'app : aucune donnée de Miff visible ; même constat après le retrait d'un membre sur son propre téléphone. |
| **Régénérer le code** (créateur) | — | Batch : `miffs/{id}` update {joinCode} + create `joinCodes/{nouveau}` + delete `joinCodes/{ancien}`. |
| **Renommer la Miff** (créateur) | — | Batch : `miffs/{id}` update {name} + `joinCodes/{code}` update {miffName}. |
| **Supprimer une Miff** (créateur) | — | Par étapes, en petits batches : retirer chaque autre membre avec le batch de retrait ci-dessus (**un membre par batch, le code est renouvelé à chaque fois** ; un retrait dans le batch final qui supprime la Miff est refusé), supprimer les `places` (≤ 5 par batch), puis en dernier un batch : delete `miffs/{id}` + **create `miffsDeleted/{id}` `{deletedAt: serverTimestamp()}`** (pierre tombale, §2.11) + delete `joinCodes/{code}` (le code actuel de la Miff, lu dans `miffs/{id}.joinCode`) + delete mon `status`/`photos` (si existent) + delete mon `members/{uid}` + delete mon `memberships/{key}`. **Le batch est refusé sans la pierre tombale ou sans la suppression du code.** Si interrompu : relancer (idempotent : si le code a déjà été supprimé dans un batch précédent, le batch final reste accepté sans lui). Les sous-collections ne sont pas supprimées automatiquement par Firestore. **Constats MIFF-2 (tests 18-miff2, rapport des tests de règles, section MIFF-2)** : le client lit le serveur à chaque reprise (qui reste, quels lieux, la Miff existe-t-elle) ; la suppression d'un **code** ou d'un **index** absent est **refusée** (celle d'une fiche, d'un statut, d'une photo ou d'un lieu absent est acceptée), donc le client lit leur existence dans la transaction et omet ces deux suppressions s'ils manquent ; le lot final fait 12 appels de règles sur 20 au pire cas (mesure et comptage dans le rapport). |
| **Premium (lecture)** | `get entitlements/{uid}` (peut ne pas exister = gratuit) | Aucune. L'achat store ne suffit pas : c'est le serveur (ou le PM en manuel) qui écrit `entitlements`. |
| **Suppression de compte** | — | §10, séquence décidée le 2026-10-24 : confirmation bienveillante au créateur **avant** (liste de ses Miffs et nombre de membres), puis **pour chaque Miff créée** : retrait des autres membres un par un (batch de retrait ci-dessus), suppression des `places` (≤ 5 par batch), batch final de suppression de la Miff (pierre tombale + code) ; batch « Quitter » pour les Miffs rejointes ; suppression des `devices` et de `users/{uid}` ; **en dernier** suppression de l'utilisateur Auth. Reprenable (idempotent). Les autres membres perdent l'accès et l'apprennent à leur prochain lancement. |
| **Push (Blaze)** | — | À chaque lancement et rafraîchissement de jeton : `users/{uid}/devices/{deviceId}` set {token, platform, updatedAt}. À la déconnexion : delete. |

**App Check** : les deux apps doivent initialiser Firebase App Check (App Attest/DeviceCheck sur iOS, Play Integrity sur Android) avant toute requête Firestore ; jeton de debug pour les builds de développement. Sans cela, le PM ne pourra pas activer l'application stricte (README).

---

## 9. Matrice d'accès

Légende : `S` = soi-même, `M` = membre de la Miff, `P` = membre qui partage lui-même sa position (réciprocité), `O` = créateur de la Miff, `—` = refusé.

| Chemin | get | list | create | update | delete |
|---|---|---|---|---|---|
| `users/{uid}` | S (**le numéro n'est lisible que par S**) | — | S (profil **avec `phone` E.164 obligatoire**, (a) CGU, (b) consentement facultatif ; heure serveur) | S (profil, **`phone` (jamais vidé)**, consentement (b) ; (a) et `createdAt` figés) | S |
| `users/{uid}/memberships/{key}` | S | S | S (slot) | — | S (si plus membre) |
| `users/{uid}/devices/{id}` | S | S | S | S | S |
| `entitlements/{uid}` | S | — | — (serveur/console) | — | — |
| `joinCodes/{code}` | tout compte connecté qui connaît le code | **—** | O (avec sa Miff) | O (nom) | O |
| `miffsDeleted/{id}` | **—** | **—** | O (dans le lot qui supprime la Miff, champ `deletedAt` seul) | **—** | **—** |
| `miffs/{id}` | M | **—** | S (devient O) ; refusé si `miffsDeleted/{id}` existe | O (nom, code) | O (avec pierre tombale et suppression du code dans le lot) |
| `miffs/{id}/members/{uid}` | M (Miff existante) ; **contient le numéro, sans condition de partage (N1)** | M (Miff existante) | S (via code ou création, **et profil `users/{uid}` avec (a) existant ou créé dans le même lot, et `phone` identique à celui du profil**) | S (profil dont `phone` identique à celui du profil, consentement) | S (quitter) / O (retirer, **avec nouveau code dans le même lot**) |
| `miffs/{id}/status/{uid}` | P + statut publié (Miff existante) | P + statut publié (Miff existante) | S (si partage actif) | S (si partage actif) | S / O |
| `miffs/{id}/photos/{uid}` | P (Miff existante, photo non expirée) | **—** | S (si partage actif ; expiresAt ≤ +25 h) | S | S / O / tout membre si expirée |
| `miffs/{id}/places/{pid}` | M (Miff existante) | M (Miff existante) | O (limites freemium) | O | O |
| tout le reste | — | — | — | — | — |

Les Cloud Functions / la console (Admin SDK) contournent les règles : c'est ainsi que `entitlements` est écrit.

---

## 10. Suppression de compte et durées de conservation

**Ordre côté client** (tant que le compte Auth existe, car les règles exigent l'authentification) :
1. Pour chaque Miff dont je suis membre simple : batch "Quitter" (membre + statut + photo + index).
2. Pour chaque Miff dont je suis créateur : **elle est supprimée** (décision du PM du 2026-10-24 ; pas de transfert de propriété), après une confirmation bienveillante **avant toute suppression** qui liste ces Miffs et leur nombre de membres. Pour chacune : retrait des autres membres **un par un** (lot de retrait, le code est renouvelé à chaque fois), suppression des `places` (≤ 5 par batch), puis lot final de suppression avec pierre tombale et suppression du code (§8, « Supprimer une Miff »). Les autres membres perdent l'accès et reçoivent à leur prochain lancement le message neutre d'accès perdu.
3. Supprimer `users/{uid}/devices/*`, puis `users/{uid}`.
4. Supprimer l'utilisateur Auth (ré-authentification récente exigée par Firebase).

**Non supprimable par le client** : `entitlements/{uid}` (ne contient aucune donnée personnelle hormis l'UID et l'état d'abonnement). À purger à la main ou par une fonction (Blaze). **Risque** : si l'app plante en cours de procédure, des données restent orphelines ; une fonction `onDelete` du compte (ou l'extension Firebase "Delete User Data", Blaze) rend la suppression fiable (§13).

| Donnée | Conservation proposée (non relue juridiquement : risque assumé, décision du 2026-10-23) |
|---|---|
| Profil `users`, copies `members` | Jusqu'à suppression du compte ou départ de la Miff. |
| `status` (état courant) | Pas d'historique : écrasé à chaque événement ; supprimé à la révocation du partage, au départ, au retrait. |
| `photos` | **Illisible après 24 h ; suppression physique au mieux** (nettoyage par l'auteur, le créateur ou tout membre ; le TTL de Firestore est payant, donc non utilisé sur Spark). Même durée pour tous les comptes. Supprimée avec le statut au départ / retrait / révocation. |
| `places` (coordonnées, adresses) | Jusqu'à suppression du lieu ou de la Miff. |
| `joinCodes` | Jusqu'à régénération ou suppression de la Miff (la suppression de la Miff exige celle du code, dans le même lot). |
| `miffsDeleted` (pierre tombale) | Indéfinie : identifiant de Miff + date de suppression, sans donnée personnelle. Risque connu : risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique) (§11). |
| Preuve d'acceptation (a) (`termsAcceptedAt`, `termsVersion`) et consentement statistiques (b) (`analyticsConsent`) | Tant que le profil existe ; **supprimées avec `users/{uid}`** à la suppression du compte. Conserver la preuve après suppression du compte exigerait un journal séparé sans donnée personnelle (Q13) : non modélisé. Un utilisateur social qui refuse le panneau de consentement n'a **aucun** enregistrement. |
| Preuve de consentement (`sharing.*`) | Tant que le membre est dans la Miff ; supprimée avec le document membre. Si une conservation de la preuve après départ devenait un jour nécessaire, il faudrait un journal séparé (Q13) ; risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique). |
| `devices` (jetons) | Supprimés à la déconnexion ; purge des jetons inactifs > 60 jours par fonction (Blaze). |
| `entitlements` | Durée de l'abonnement ; purge à la suppression du compte (manuelle ou fonction). |
| Sauvegardes Firestore | Désactivées par défaut. Ne pas activer de sauvegarde/restauration à un instant donné sans décision du PM (elle dupliquerait les données sensibles). |

---

## 11. Données sensibles et points RGPD (risque juridique assumé par le PM : décision du 2026-10-23, pas de relecture juridique)

Ce tableau décrit ce que le modèle implique ; il ne constitue pas une analyse de conformité. Les bases légales sont des **propositions non relues par un juriste** : **risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique)**.

| Donnée | Où | Sensibilité | Base légale proposée (non relue : risque assumé) | Conservation proposée |
|---|---|---|---|---|
| Présence par rapport à un lieu (`presence`, `placeId`, horodatages) = information de localisation dérivée | `status` | **Très élevée** (localisation ; révèle des habitudes et l'absence du domicile) | Consentement explicite et révocable (le modèle le trace) | État courant uniquement, supprimé à la révocation / départ |
| Coordonnées et adresses des lieux | `places` | **Élevée** (peut être un domicile, une école) | Contrat + consentement du créateur ; information des membres | Jusqu'à suppression du lieu / de la Miff |
| Photo de profil, photo d'update | `users`, `members`, `photos` | Élevée (image d'une personne, parfois d'un tiers ou d'un mineur) | Contrat (profil) ; consentement (update) | Profil : durée du compte ; update : 24 h |
| Nom, prénom | `users`, `members` | Moyenne | Contrat | Durée du compte |
| **Numéro de téléphone** (E.164, ONB-B) | `users` (privé), copie dans `members` (**visible de tous les membres de chaque Miff de la personne**) | **Moyenne à élevée** (donnée de contact directe, rattachable à une personne, parfois un mineur) ; **obligatoire** (décision Q1 du PM) donc non strictement nécessaire au geste central | Contrat (permettre aux proches d'appeler ou d'écrire depuis l'app) ; consentement par la saisie volontaire et la mention affichée à l'écran | Jusqu'à suppression du compte (profil) ou départ / retrait / suppression de la Miff (copie) |
| Appartenance à un cercle (lien social, familial) | `members`, `memberships` | Moyenne à élevée (révèle des relations, éventuellement parent-enfant) | Contrat | Jusqu'au départ |
| Niveau de batterie, permission de localisation du téléphone | `status` | Faible à moyenne | Intérêt légitime / contrat | État courant |
| Jetons de notification | `devices` | Faible (identifiant d'appareil) | Contrat | Jusqu'à déconnexion |
| Preuve d'acceptation des CGU (a) et du consentement | `users`, `members` | Faible | Obligation de pouvoir démontrer le consentement | Voir §10 |
| Consentement (b) aux statistiques d'usage et rapports de plantage (état, date, version, retrait) | `users` (`analyticsConsent`) | Faible en soi (un booléen daté) ; **conditionne** la collecte d'une donnée à caractère personnel (identifiant d'installation, zone déduite de l'IP, journaux de plantage) | Consentement (RGPD art. 6.1.a et 7 : libre, spécifique, retirable aussi facilement que donné) | Tant que le profil existe (§10) |
| État d'abonnement | `entitlements` | Faible | Contrat | Durée de l'abonnement |
| Données Analytics et rapports de plantage (collectés seulement si (b) acceptée) | Google Analytics / Crashlytics (hors Firestore) | Faible si jamais de position | Consentement (b) | Régler la rétention GA4 au minimum dans la console |

Risques juridiques connus et assumés par le PM (décision du 2026-10-23, pas de relecture juridique) :
1. **Localisation** : donnée à haut risque ; le choix de ne stocker qu'un état (et non les coordonnées de la personne) réduit l'exposition mais ne la supprime pas.
2. **Mineurs** : si des mineurs peuvent utiliser l'app (cas parent-enfant mentionné au CLAUDE.md), le RGPD prévoit un âge de consentement numérique (15 ans en France d'après la loi nationale, à vérifier) et un consentement parental en dessous. Le modèle **ne contient aucune date de naissance ni âge** (minimisation) : il n'existe donc aucun contrôle d'âge. Q12.
3. **Analyse d'impact (AIPD)** : localisation + éventuels mineurs sont des critères qui peuvent la rendre nécessaire.
4. **Transferts hors UE** : Firestore est en région Paris, mais Firebase Authentication, FCM et Analytics peuvent traiter des données hors UE. À vérifier avec la documentation Google et les clauses contractuelles.
5. **Droit d'accès / portabilité / effacement** : l'effacement est réalisable (§10) ; une procédure d'export (accès/portabilité) n'est pas spécifiée (le client peut lire les documents propres à l'utilisateur ; un export formaté n'existe pas).
6. **Consentement réciproque anti-stalkerware** : le modèle impose la réciprocité, la révocation immédiate avec suppression, et un historique nul. Les stores appliquent des règles propres : à confronter par `release-manager`.
7. **Ex-membre qui connaît le code** : voir §5.
8. **Pierre tombale `miffsDeleted`** (DUR-1) : conservée indéfiniment, ne contient que l'identifiant d'une Miff supprimée et une date (aucune donnée personnelle à ma connaissance : un identifiant aléatoire de groupe). Risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique), y compris la durée de conservation.
9. **Données des survivants d'une Miff supprimée** : la fiche, le statut et la photo d'un membre resté après la suppression d'une Miff restent *physiquement stockés* tant que l'app de ce membre ne les a pas nettoyés (au prochain lancement), mais ils sont **illisibles immédiatement** (contrôle d'existence de la Miff, DUR-2). **Risque accepté par le PM (revue de sécurité de DUR-2)** : les *écritures* de statut, photo et fiche d'un survivant ne vérifient pas que la Miff existe encore ; un survivant (ou un client modifié) peut donc écrire de nouveaux documents dans une Miff morte tant que sa fiche existe. Ces données restent illisibles (stockage seulement, quota Spark) et sont nettoyées au prochain lancement de l'app ; sur Spark aucune fonction ne peut les purger (décision PM : acceptable pour la bêta ; **risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique)**, risque K4 du ticket). La procédure client « retirer chaque membre avant de supprimer la Miff » (§8) limite ce cas ; la suppression de la Miff n'est pas bloquée s'il reste des membres (les règles ne peuvent pas vérifier qu'une sous-collection est vide).
10. **Photos** : la formulation « 24 h maximum » est remplacée par « illisible après 24 h ; suppression physique au mieux » tant qu'une suppression automatique n'est pas disponible sur Spark (les suppressions TTL de Firestore n'ont pas d'usage gratuit selon la documentation Google, et même avec Blaze la suppression n'a lieu que « typiquement dans les 24 heures » après l'expiration) : risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique).
11. **Cache local** : les SDK conservent un cache sur l'appareil (statuts, lieux) ; contrat client de purge (ligne « Purge du cache local » du §8, DUR-3) à la déconnexion, à la suppression de compte et à la perte d'accès à une Miff. `clearPersistence()` n'écrase pas les octets : point couvert par le risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique).
12. **Deux consentements à l'inscription (AUTH-B)** : (a) CGU et politique, obligatoire ; (b) statistiques et plantages, facultatif, décoché par défaut, retirable dans Réglages (retrait enregistré avec sa date). Point RGPD, risque assumé par le PM (décision du 2026-10-23) : le caractère libre et spécifique de (b) (une seule case pour statistiques d'usage **et** rapports de plantage, risque R16 du ticket) et la formulation du texte.
13. **Preuve de consentement** : l'horodatage serveur de (a) et de (b) est celui de la **création du profil**, pas du clic sur le bouton d'inscription ; sans profil créé, aucune preuve serveur n'existe (le choix reste seulement sur l'appareil). La preuve est **supprimée avec le profil** à la suppression du compte. Point RGPD, risque assumé par le PM (ticket §17.3).
14. **Compte d'authentification créé avant le consentement (connexion sociale d'un nouvel utilisateur)** : en cas de refus, le compte est supprimé et **aucun document Firestore** n'est écrit ; mais l'identifiant et l'adresse fournis par Google ou Apple existent dans Firebase Authentication pendant quelques secondes, et plus longtemps si l'app est fermée de force pendant le panneau (ticket R17). Depuis la décision du 2026-10-24, les règles exigent aussi un profil portant (a) pour créer ou rejoindre une Miff (§14, point 7).
15. **Mineurs** : aucun âge minimum, aucune date de naissance (décision PM Q7) ; un mineur peut accepter (a) et (b) seul. Cela relève du point 2 ci-dessus (RGPD art. 8, 15 ans en France à vérifier).
16. **Application du choix (b) à un autre appareil** du même compte : le modèle le permet (le choix est lu sur le compte) ; acceptable ou nouveau consentement par appareil ? Point RGPD, risque assumé par le PM (ticket §17.3).
17. **Téléphone obligatoire (ONB-B, décision Q1 du PM, contre la recommandation de `product-specs`) : minimisation.** Une donnée de contact exigée de tout le monde pour utiliser l'app, alors que le geste central (savoir que ses proches vont bien) ne l'exige pas. Le modèle limite l'exposition : une seule clé `phone` (aucun pays séparé, aucun e-mail), format fixe, jamais dans un journal ni un événement d'analyse (règle de développement du ticket), visible des seuls membres des Miffs de la personne, supprimée avec la fiche. Risque assumé par le PM, sans relecture juridique (décision du 2026-10-23).
18. **Numéro non vérifié** : aucune preuve que la personne possède le numéro (pas de SMS sur Spark), aucune unicité. Conséquences : faute de frappe ; numéro d'un tiers ou inventé ; un tiers dont le numéro est saisi dans une Miff voit son numéro lisible de ses membres sans l'avoir su ni accepté ; usage possible pour embêter quelqu'un. Les règles ne peuvent rien contre cela (forme seulement). Risque assumé.
19. **Fuite du code = fuite des numéros** (R7 de l'Onboarding) : toute personne qui connaît le code d'une Miff la rejoint immédiatement (aucune approbation) et lit alors les numéros de **tous** les membres (testé : PH11, comportement figé). Atténuations existantes : le code est renouvelé à chaque retrait (DUR-2) et le créateur peut le régénérer ; l'approbation du créateur à l'arrivée (§5, Q7) est une atténuation à reconsidérer. Risque assumé.
20. **Mineurs** : aucun âge minimum (décision Q7 de l'Authentification) : le numéro d'un mineur est lisible de ses co-membres, y compris d'un adulte inconnu entré avec le code. Même cadre que les points 2 et 15.
21. **Textes et déclarations** : la politique de confidentialité, les CGU et les déclarations des stores (étiquettes de confidentialité d'Apple, section Sécurité des données de Google) **ne mentionnent pas encore le numéro de téléphone** ; à compléter avant toute publication (critère B5 de l'Onboarding, `release-manager`). Hors périmètre de ce ticket backend.
22. **Copie périmée** : voir §2.6 (les règles garantissent l'égalité à l'écriture, pas la mise à jour de toutes les fiches) ; effet limité à l'affichage d'un ancien numéro de la personne elle-même.

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

### Abus de lectures sur `joinCodes` et signal de bascule vers Blaze (ticket durcissement §5.1, R19)

**Le risque** : n'importe qui peut créer des comptes jetables (e-mail et mot de passe) et boucler sur `get joinCodes/{code}`, chaque essai étant une lecture facturée même si le code n'existe pas. À 10 essais par seconde le quota gratuit de 50 000 lectures est épuisé en environ 1 h 20 (calcul, pas une mesure) ; l'application s'arrête alors **pour tout le monde** jusqu'à la remise à zéro. Ce n'est pas un vol de données (code de 59 bits, introuvable en pratique) mais un **déni de service** et, sur Blaze, une facture. Les règles ne peuvent pas limiter le nombre d'essais par compte (pas de compteur persistant).

**Mitigations gratuites, par ordre d'efficacité** : (1) App Check **appliqué** sur Firestore (et sur Authentication si la console le propose) après mesure ; (2) surveillance quotidienne pendant la bêta externe ; (3) limitation des essais côté application (aide, pas une protection) ; (4) connexion anonyme désactivée, protection contre l'énumération d'e-mails activée. **Décision du PM (2026-10-22) : l'e-mail vérifié n'est PAS exigé** ; il est gardé en réserve comme mesure gratuite (une condition `email_verified`, environ une ligne de règle) avant tout recours à Blaze.

**Risque résiduel assumé (K8)** : un attaquant équipé d'un **vrai appareil** (qui passe App Check) peut créer des comptes jetables sans boîte mail réelle et boucler sur `get joinCodes`. Il peut épuiser le quota du jour (déni de service) sans pouvoir deviner un code. Effort demandé : un appareil réel par flux de comptes, modeste mais pas nul.

**Indicateurs (relevés chaque jour de bêta externe, guide pas à pas : `docs/dev/surveillance-quotas.md`)** :

| Niveau | Seuil | Action |
|---|---|---|
| Normal | ratio lectures ÷ utilisateurs actifs ≤ 100 (estimation de départ : environ 75) | Noter le relevé |
| **À examiner** | ratio **> 100** un jour | Chercher la cause (bug d'app, testeur intensif, début d'abus) ; surveiller le lendemain |
| **SIGNAL de bascule** | ratio **> 150 deux jours de suite**, ou lectures **> 30 000 par jour deux jours de suite** sans explication, ou **création de comptes anormale** (exemple de départ : plus de 5 fois la moyenne des 7 jours précédents sans campagne d'invitation, seuil à calibrer avec le volume réel de la bêta), ou **quota de 50 000 lectures atteint** (même un seul jour) | Procédure de bascule ci-dessous |

**Procédure de bascule (dans cet ordre, du moins cher au plus cher)** : (1) vérifier qu'App Check est bien appliqué (`docs/dev/app-check-strict.md`) ; (2) réexaminer l'exigence d'e-mail vérifié (gratuit, règle testée puis déployée avec `docs/dev/deploiement-regles.md`) ; (3) décision du PM de passer en **Blaze** (carte bancaire saisie par le PM ; l'alerte de budget **prévient mais ne plafonne pas**) pour disposer d'une fonction appelable « rejoindre » avec compteur d'essais par compte. **La décision Blaze est reportée par le PM : à trancher avant la première bêta externe (Q1 du §15)**. Aucun contournement de quota (multi-projets, multi-comptes) n'est acceptable : risque de bannissement du projet.

**À mesurer, non fait** : (a) le débit réel d'une rafale de `get joinCodes` et (b) si une requête refusée par App Check compte dans le quota de lectures. Les deux se mesurent sur le **futur projet de développement** (jamais sur `miff-3d283`), avec l'accord du PM et hors CI ; ce projet n'existe pas encore. Les résultats seront consignés ici.

**Lectures de règles du durcissement (DUR-1 à DUR-3), mesurées dans l'émulateur le 2026-10-24 (méthode corrigée en DUR-3, détail et cause des anciennes mesures non concluantes dans `rules-test-report.md`)** : création de Miff gratuite ≤ 2 et premium ≤ 6 sur 20 (lot de 4) ; rejoindre ≤ 2 sur 20 ; retrait d'un membre avec nouveau code ≤ 2 sur 20 (lot de 6) ; suppression de Miff ≤ 3 sur 20 (lot de 6) ; régénération du code ≤ 1 ; lecture d'un statut (get) ≤ 3 sur 10, **liste des statuts ≤ 3 sur 10 (désormais mesurée)**, photo ≤ 2, lieu ≤ 2, document Miff ≤ 1, recherche d'un code 0 ; mise à jour du statut ≤ 1 ; création d'un lieu premium ≤ 4 (marge d'au moins 6 partout ; au moins 14 pour les lots). **Limites de cette mesure** : elle compte les lectures `get`/`exists` vues par l'émulateur, qui met en cache les lectures d'un même document (la documentation Firebase dit seulement que « some document access calls may be cached ») ; le comportement de l'émulateur pour `getAfter`/`existsAfter` n'est pas établi (une sonde l'a montré différent de `exists`, sans l'élucider). Les chiffres sont donc des **bornes mesurées dans l'émulateur**, à lire avec le comptage manuel du pire cas sans cache fait lors de DUR-2 (retrait d'un membre : 18 sur 20 ; suppression de Miff : 12 sur 20), qui laisse lui aussi une marge d'au moins 2. Impact (estimation, pas une mesure d'usage réel) : quelques lectures de règles de plus par ouverture à froid, sans changer l'ordre de grandeur des estimations ci-dessus. À surveiller avec le ratio lectures ÷ utilisateurs actifs.

**Lectures de règles d'AUTH-B (mesurées dans l'émulateur le 2026-10-24)** : création du profil avec (a) et (b) = 0 lecture de règles ; mise à jour ou retrait du consentement (b) = 0. Aucune règle de `users` ne lit un autre document. **Surcoût du contrôle « profil exigé » (décision du 2026-10-24, un `getAfter` sur le profil lors de la création de la fiche membre)**, mesuré avec la méthode calibrée de DUR-3 : création de Miff gratuite 2 -> 3, premium 6 -> 7 (sur 20, marge au moins 13) ; adhésion par code 2 -> 3 (marge au moins 17) ; « profil + Miff » en un lot de 5 : 2 ; retrait d'un membre (2) et suppression de la Miff (3) **inchangés** (le contrôle n'y est pas évalué). La limite de 20 par lot n'est dépassée dans aucun cas. La lecture du consentement au lancement est la lecture du profil déjà faite pour le routage (1 lecture, §5 du ticket authentification) : aucun surcoût.

**Lectures de règles d'ONB-B (téléphone) et de MIFF-0 (lot d'entrée), mesurées dans l'émulateur le 2026-10-27** (méthode calibrée de DUR-3 ; chiffres = borne haute prouvée sur les exécutions complètes de la suite, variation d'une exécution à l'autre de 1 à 2 lectures, déjà constatée en DUR-3) :

| Opération | Lectures de règles mesurées (sur 20 pour un lot, 10 pour une écriture simple) | Pire cas manuel **sans aucun cache** (appels `get`/`exists`/`getAfter`/`existsAfter` comptés un à un) |
|---|---|---|
| Rejoindre et partager (fiche + index + statut, lot de 3) | <= 3 (marge >= 17) | 8 |
| Profil + rejoindre et partager (lot de 4) | <= 2 (marge >= 18) | 8 |
| Créer et partager, gratuit (Miff + code + fiche + index + statut, lot de 5) | <= 3 (marge >= 17) | 14 |
| Créer et partager, **premium** (lot de 5) | <= 7 (marge >= 13) | **19 (marge 1)** |
| Profil + créer et partager (lot de 6) | <= 2 (marge >= 18) | 14 (19 en premium) |
| Modifier le numéro : profil + fiche (lot de 2) | 0 (marge >= 20) | 1 par fiche : 15 fiches = 15 (marge 5) |
| Modifier le numéro de la fiche seule, profil déjà à jour (écriture simple) | <= 1 (marge >= 9) | 1 |
| Modifier le numéro du profil seul (écriture simple) | 0 (marge 10) | 0 |

- **Le téléphone ne coûte aucune lecture de règles de plus à la création de la fiche** (même document que le contrôle « profil exigé » d'AUTH-B : la création gratuite reste à 3, l'adhésion à 3, la création premium à 5 à 7 selon l'exécution, comme avant ONB-B) ; au pire sans cache il ajoute **1 appel** (§ dernière colonne). Le statut initial de MIFF-0 ajoute **1 appel** (`getAfter` de la fiche) et aucune lecture mesurée.
- **Marge la plus faible : « Créer et partager » en premium, 19 appels sur 20 au pire sans cache (marge de 1, sous le minimum de 2 visé).** Pire cas = compte premium qui garde un ancien emplacement gratuit (`memberships/free`) et crée une 2e Miff : fiche de membre 8 (téléphone 1 + CGU 1 + `!exists` de la Miff 1 + `getAfter` de la Miff 1 + `indexAfterOk` 4), Miff 3, code 3, index premium 4, statut 1 = 19 (une première correction de ce document comptait 18 : elle ignorait les 4 appels de `indexAfterOk` quand l'emplacement gratuit existe déjà). L'émulateur accepte ce lot (mesure : 7 au plus). **Tout appel de règle ajouté à ce lot le fait dépasser 20** (donc refuser la création) : aucun contrôle supplémentaire (profil, abonnement, Miff) ne doit y être ajouté sans refaire ce comptage ; à l'inverse du retrait d'un membre (18 sur 20). En gratuit : 14 sur 20.
- Les mesures sont celles du test `09-rule-reads` (échoue si une marge mesurée tombe sous 2, une mesure est non concluante ou discordante). Limites de la méthode : voir le paragraphe sur DUR-1 à DUR-3 ci-dessus (cache de l'émulateur, comportement de `getAfter` sur un document écrit dans le même lot non établi : c'est pourquoi la colonne de droite compte chaque appel).
- **Impact PM (estimation, pas une mesure d'usage réel)** : aucune lecture facturée de plus à l'ouverture de l'app (les lectures de règles ne concernent que les écritures de création et de modification du numéro, très rares) ; le surcoût d'ONB-B sur les quotas Spark est négligeable.

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
| Purge des jetons FCM périmés | Non nécessaire sans push | Fonction planifiée |
| Suppression physique des photos à l'expiration | **Non garantie** : illisibles après 24 h (règles) + nettoyage par les clients (§2.8) | Stratégie TTL Firestore (suppressions TTL sans usage gratuit ; délai typique jusqu'à 24 h après l'expiration) ou fonction planifiée |

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
5 quinquies. **Commande Firebase visant un mauvais projet** (déploiement oublié sur `ghostrun-app`, test lancé sans `--project`) → DUR-3 : `.firebaserc` sans projet, tests qui refusent toute commande `firebase` du dépôt hors émulateur `demo-*`, aucun déploiement ni jeton dans les workflows, garde-fou qui refuse `GCLOUD_PROJECT=miff-3d283`/`ghostrun-app`. Le déploiement se fait à la main, avec `--project miff-3d283` explicite (`docs/dev/deploiement-regles.md`).
5 sexies. **Données de Miff lisibles dans le cache d'un téléphone après déconnexion ou retrait** → contrat de purge du cache (§8, DUR-3) ; à vérifier sur appareil (R17).
6 bis. **Consentement (b) falsifié ou modifié par un tiers (AUTH-B)** -> corrigé par les règles : bloc lisible et modifiable par son propriétaire seul, horodatage = heure serveur (impossible d'antidater), retrait avec trace conservée, version non rétrogradable, champs (a) figés, aucune clé supplémentaire (âge, e-mail...). Tests AB1 à AB17 (`rules-test-plan.md` §13), chacun prouvé par mutation. Limites : un client modifié peut ne **pas** respecter l'état local (collecter sans consentement) : le consentement n'est pas techniquement contraignant côté appareil, il l'est seulement par le code des applications (revue de code et QA, critères X9 et C13 du ticket) ; la **version courante** du texte n'est connue que du client (les règles ne peuvent pas la comparer à une valeur de référence).
7. **Profil (a) exigé pour créer ou rejoindre une Miff (AUTH-B, décidé le 2026-10-24, corrigé)** : la création de la fiche membre (créateur ou adhésion par code) exige `users/{uid}` avec `termsAcceptedAt` et `termsVersion`, existant ou créé dans le même lot. Tests AB18 à AB22, prouvés par mutation. Limite : un profil dont l'acceptation (a) a été enregistrée reste valable pour toujours (aucune re-acceptation, hors périmètre).
8. **Comparaison de versions par ordre alphabétique (AUTH-B)** : la règle « pas de rétrogradation » compare deux textes. Validée dans l'émulateur Firestore ; la documentation officielle consultée ne précise pas cet opérateur sur les chaînes. En cas de refus par le serveur réel, l'échec est **fermé** (la réactivation après retrait serait refusée, aucun accès en plus) ; à reconfirmer au premier essai sur le projet de développement.
6 ter. **Téléphone (ONB-B)** : usurpation (écrire le numéro d'un autre sur son profil ou sa fiche) -> la fiche doit être égale au profil du **même** uid et le profil n'est écrivable que par son propriétaire (tests PH3, PH6, PH8) ; format -> regex E.164 stricte, 16 caractères au plus (PH2) ; lecture -> profil privé, fiche réservée aux membres de la Miff (PH5, PH10, PH10b, PH10c) ; **limites** : fuite du code = fuite des numéros, numéro non vérifié ni unique, copie périmée possible (§11 points 17 à 22). Tests PH1 à PH11, chacun prouvé par mutation (`mutations-onbb.mjs`).
6 quater. **Lot d'entrée avec statut initial (MIFF-0)** : atomique (tout passe ou rien : EN3, EN4) ; aucune règle modifiée ; limite : les règles n'imposent pas `presence = "unknown"` au premier statut (convention du client, EN5).
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

1. **Blaze** (décision reportée par le PM le 2026-10-22, **à trancher avant la première bêta externe** ; signaux déclencheurs en §12) : accord pour passer en Blaze (carte bancaire + alerte de budget) avant la première bêta externe ? Sinon, accepter qu'il n'y ait ni push, ni escalade app fermée, ni paywall réel.
2. **Photos** : Firestore (≤ 150 Ko, 24 h, sans Blaze) ou Cloud Storage (meilleure qualité, exige Blaze) ? Durée de vie de la photo d'update (24 h proposé) ? La photo est-elle obligatoire dans une update ?
3. **Premium** : par utilisateur ou par Miff ? Qui bénéficie des zones à risque et des notifications intelligentes (le créateur premium, ou tout le cercle) ? Que devient l'excédent (lieux, Miffs) à l'expiration ? **Décidé le 2026-10-24** : l'excédent (les derniers lieux et Miffs ajoutés) est supprimé ; préavis, notification et choix de l'élément conservé restent à définir dans le ticket Premium.
4. **Compte du quota "1 lieu"** : la zone à risque est supposée premium ; le gratuit = 1 zone de confiance. À confirmer.
5. **Qui définit les zones à risque** : le créateur seulement (supposé) ou chaque membre pour lui-même ? Que déclenche l'entrée (alerte immédiate à tout le cercle) ?
6. **Réciprocité** : confirmer qu'un membre qui ne partage pas sa position ne voit pas celle des autres (supposé, pour limiter le risque de surveillance à sens unique). Un parent doit-il pouvoir voir un enfant sans partager la sienne (ce qui lèverait la réciprocité) ?
7. **Code d'invitation** : visible par tous les membres (supposé) ou seulement le créateur ? Régénération (prévue) ? ~~Faut-il bloquer le retour d'un membre retiré ?~~ **Tranché le 2026-10-22 : le retrait d'un membre renouvelle le code (imposé par les règles, DUR-2) ; le départ volontaire ne change rien.** Validation du créateur à l'arrivée ? Taille maximale d'une Miff (coût du feed proportionnel) ?
8. **Créateur qui part** : suppression de la Miff (supposé), transfert de propriété, ou impossible tant que d'autres membres restent ?
9. **Géofences au-delà de la limite** : priorité manuelle (`priority`), proximité ou fréquence de visite ? Prévenir l'utilisateur qu'un lieu n'est plus surveillé ?
10. **Escalade** : orange et/ou rouge déclenchent-ils un push ? Que fait-on d'un membre sans permission de localisation arrière-plan (aujourd'hui : pas d'escalade sans départ connu) ? Si un membre reste rouge, quelle relance ?
11. **Update manuelle** : suffit-il de la poster pour repasser au vert, ou faut-il un geste "je vais bien" explicite ?
12. **Mineurs** : l'app est-elle ouverte aux mineurs ? Âge minimum, consentement parental, collecte d'un âge ou d'une date de naissance (non prévue pour l'instant) ?
13. **Juridique** (**risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique)**) : bases légales (§11), analyse d'impact, durées de conservation, conservation éventuelle de la preuve de consentement après départ, transferts hors UE, procédure d'export des données.
14. **E-mail vérifié** : ~~exigé avant de rejoindre/créer une Miff ?~~ **Tranché le 2026-10-22 : non exigé** (les règles ne testent pas `email_verified`).
15. **Nom/prénom** : conserver la règle "3 caractères minimum" des maquettes (elle exclut des prénoms réels, ex. "Li") ?
16. **Désactivation du partage** : notifier les autres membres ? (ton à définir.)
17. **Rayon** : bornes 50–2 000 m acceptables ? Minimum recommandé côté UI : 100 m.
18. **Suppression de compte d'un créateur** : **décidé le 2026-10-24** : ses Miffs sont supprimées (lieux, statuts, photos compris), les autres membres perdent l'accès, pas de transfert (voir §8 et §10).
19. **Fournisseur d'abonnement** : validation native (App Store / Play Billing) ou service tiers (RevenueCat) — conditionne la fonction de webhook.
20. **Déconnexion et partage** (nouvelle, issue du contrat de purge du cache, hors périmètre DUR-3) : se déconnecter doit-il aussi révoquer le partage de position (suppression du statut) pour que les proches ne voient pas un état figé d'un compte absent, ou le statut est-il conservé ? Aujourd'hui le contrat ne fait que vider le cache local.
21. ~~Profil exigé avant de créer/rejoindre une Miff ?~~ **Tranché le 2026-10-24 : oui.** Les règles refusent la création d'une Miff et l'adhésion par code tant que `users/{uid}` portant `termsAcceptedAt` et `termsVersion` n'existe pas (le profil peut être créé dans le même lot). Coût mesuré : +1 à +2 lectures de règles par création ou adhésion (§12).
