# Ticket : UPDATE, l'écran « à la Snapchat » (photo du moment, bouton central coloré, alertes d'incitation)

**Statut** : challenge **validé par le PM le 2026-11-05** (les 5 questions du §17 sont **décidées** : D16 à D20) ; **reste ouvert un seul point, non décisionnel** : l'export PNG des 4 images de l'Update dans Notion (§15, §16). Aucun développement lancé, aucun statut Notion modifié. Ticket Notion concerné : « Update » (statut Notion : « Spec en attente de review IA », page du 2026-09-30) ; liés : « Statut utilisateur (Vert, orange, rouge) », « Feed », « Tabbar » (page vide), « Premium » (rien sur l'Update).
**Rédigé le** : 2026-11-05 (agent `product-specs`, étape « challenge » du process par ticket). Branche `docs/challenge-update`.
**Complète** : `docs/tickets/feed.md` (PR #72, FEED-4 = affichage de la photo) ; **reprend** les décisions de `docs/tickets/lieux-de-confiance.md` (§6.9, N1 à N6, L7, « Décisions du PM » du 2026-11-01) et de `docs/tickets/feed-profil-reglages.md` (§13, Q1, Q15). **Remplace**, une fois validé, `docs/specs/update-et-escalade-statut.md` (écarts listés en §4, C4 et C5). **Ne remplace aucun autre document.**
**Règle de hiérarchie** : décisions du PM > Notion > ce ticket > reste du dépôt. Une réponse déjà présente dans un ticket Notion ou une décision du PM est une **décision**, pas une question (§3 les liste avec leur source).
**Sources lues** :
- **Notion, relu directement le 2026-11-05** : « Update », « Statut utilisateur », « Feed », « Tabbar » (vide), « Premium ». **Limite** : les quatre blocs image de « Update » (deux écrans, message d'alerte orange, message d'alerte rouge) sont des objets externes **illisibles** par l'outil. **Aucune maquette de l'Update n'existe dans le dépôt** (ni `design/exports/`, ni `design/screens/`) : les écrans sont à fournir (export des 4 images) et les **textes d'alerte** du §7, validés par le PM (D17), sont à confronter à cet export (§16).
- Dépôt (`origin/main`, `4171688`) : `CLAUDE.md` ; `backend/firestore.rules` (`validPhoto`, `validStatus`, `match /photos/{uid}`, lots d'arrêt du partage) et `backend/data-model.md` (§2.7, §2.8, §3, §8 « Update manuelle », §11, §12) ; `docs/dev/surveillance-quotas.md` ; `docs/legal/*` (CGU art. 8, politique, `consentement-localisation-v3.md` O4, README J2, J8, J31) ; `docs/release/google-play-data-safety.md`, `app-store-confidentialite-et-revue.md` ; `docs/tickets/suppression-compte.md` (PR #51) ; maquettes `design/exports/feed/` (9 PNG du PM, dont la barre d'onglets) et `design/exports/feed/claude/09-photo-plein-ecran.png` (PR #72).
- Code Android existant (lu, non modifié) : `ui/main/MainTabBar.kt` et `domain/navigation/MainTab.kt` (bouton central **inactif**, anneau jaune-vert, masqué dans les builds de diffusion) ; `data/profile/AndroidAvatarCodec.kt` et `domain/profile/Avatar.kt` (photo de profil : **pas de permission caméra**, photo du système, ré-encodage sans métadonnées, 20 000 octets) ; `AndroidManifest.xml` (**aucune** permission `CAMERA`) ; `ui/permission/PermissionGate*` (localisation uniquement) ; `domain/tracking/StatusLevel.kt` et `TrackingWrites.kt` (fusionnés, LOC-2b) ; `ui/components/RecentsPrivacy.kt`. **Aucune bibliothèque de caméra** (pas de CameraX) dans `libs.versions.toml`. iOS : rien de caméra (la CI vérifie l'absence de `NSLocation*`, pas de `NSCamera*`).
- Web (lu le 2026-11-05) : page des tarifs Firebase (Cloud Storage) et page des quotas Firestore (taille de document).
**Convention** : **[Décidé]** = décision du PM (source citée) ; **[Notion]** = repris du ticket Notion ; **[Proposé]** = ma proposition, applicable sauf objection du PM (§5.9) ; **[À vérifier]** = non confirmé. Les repères Q1 à Q5 sont **conservés** comme références historiques : ce sont maintenant les décisions D16 à D20. Je ne tranche aucune décision produit ou légale. Je n'ai modifié ni le code, ni Notion, ni Figma, ni les règles Firestore, ni `backend/data-model.md`, ni les textes légaux.

---

## 1. Résumé pour le PM

En une phrase : l'Update est **le geste volontaire qui complète la détection automatique** : en deux ou trois touchers, tu prends une photo du moment (selfie par défaut), tu la publies, et ton cercle voit « {Prénom} a donné des nouvelles » ; le bouton central de la barre prend la **couleur de ton statut**, et si tu passes en orange ou en rouge, un **message dans l'app** t'invite doucement à publier. Aucune notification : tout se passe dans l'app ouverte (plan gratuit).

**Bonne nouvelle** : la photo **peut vivre gratuitement dans Firestore** (≤ 150 Ko, une par membre et par Miff, règles déjà déployées : `validPhoto`), la couleur est **déjà calculée** (`StatusColor.level`, LOC-2b fusionné) et l'écriture du « dernier signal » (`lastUpdateAt`) existe dans le contrat. Il n'y a **aucune règle à créer** pour livrer l'Update sans localisation.

**Les huit points qui comptent** (détail §4) :

| # | Constat | Conséquence |
|---|---|---|
| 1 | **Firebase Storage n'est pas gratuit** pour un projet neuf : la page des tarifs Firebase (lue le 2026-11-05) ne donne un usage gratuit sur le plan Spark que pour les anciens espaces `*.appspot.com` ; pour les nouveaux (`*.firebasestorage.app`) la colonne Spark dit « non applicable », et le quota gratuit des nouveaux espaces n'existe que dans trois régions des États-Unis (nos données sont à Paris). | **Décision déjà prise** (`data-model.md` §2.8, décision PM Q2) : **octets de la photo dans Firestore**, ≤ 150 000 octets. Aucune question. |
| 2 | **« Valable pour toutes les Miffs » = une copie de la photo par Miff.** Le schéma stocke `miffs/{id}/photos/{uid}` (une par membre **et par Miff**) : publier = **2 écritures par Miff** (photo + statut), 150 Ko chacune pour la photo. Gratuit : 1 Miff, donc 2 écritures ; Premium : 2 × N, par lots. | §6 ; aucune règle à changer ; une Miff rejointe **après** la publication n'a pas la photo (valeur par défaut V9). |
| 3 | **La caméra intégrée demande la permission `CAMERA`**, que l'app **ne déclare pas aujourd'hui** (la photo de profil passe par l'appareil photo du système, sans permission) ; le manifeste, un test (`PermissionPrivacyTest`), la CI et les déclarations des stores disent « pas de caméra ». | Décision Notion (« il doit donner son autorisation pour qu'on accède à son appareil photo ») : **viseur intégré** (CameraX sur Android, AVFoundation sur iOS), gratuit. Les contrôles « pas de caméra » sont à mettre à jour **dans la PR** ; déclarations des stores pour `release-manager` (§12). |
| 4 | **La localisation de la photo n'est pas livrable avec les règles déployées** (`validPhoto` n'accepte que `jpeg`, `createdAt`, `expiresAt`). Décision du PM : localisation **figée au moment de la photo** (règle R-PHOTO-LOC en préparation par `backend-data`). | L'Update est livré **sans localisation d'abord** ; la localisation s'ajoute (UPDATE-1b) **après** le déploiement de R-PHOTO-LOC (sur ton « oui »). Le texte de consentement v3 est amendé pour citer la photo localisée (ou passe en v4) : **décidé, D20**. |
| 5 | **Photo = donnée sensible** (visage, intérieur d'un domicile, tiers, mineurs, métadonnées EXIF dont la position). | Photo **ré-encodée depuis les pixels** (aucune métadonnée), jamais écrite dans la galerie, jamais sur disque côté viseur intégré, 24 h, écrasée à chaque publication, effacée avec le partage. Risques de modération des stores listés (R4). |
| 6 | **Une contradiction, tranchée par le PM le 2026-11-05** : la décision du 2026-10-30 (`feed-profil-reglages.md` §10 Q1, « OK pour tout ») décrivait « Je vais bien », **photo facultative** ; le ticket Notion Update décrit **une photo** (« prendre une photo et la publier »). | **Décidé : la photo est obligatoire pour publier un update en v1** (D16). Pas de « Je vais bien » sans photo en v1 ; un tel geste pourra s'ajouter plus tard sans changer les règles. |
| 7 | **Le message d'alerte ne peut pas être une notification** (plan Spark, décidé) : il n'existe que **dans l'app ouverte**, précisément quand la personne n'a probablement pas l'app ouverte. | Limite connue, assumée (R2) ; forme et textes **validés par le PM le 2026-11-05** (D17, §7) ; pas de piste cachée : une notification locale (sans serveur) est techniquement possible mais **hors décision actuelle**. |
| 8 | **Aucune maquette de l'Update dans le dépôt.** | **Seul point encore ouvert** : §16, le PM exporte en PNG les 4 images de Notion, puis `design-system` complète. |

**Coût : 0 €.** CameraX est gratuit (licence Apache 2.0) ; aucune clé, aucun service. Budget Spark au §11 : **2 écritures par publication gratuite** (+2 de nettoyage par photo expirée), ~4 lectures par proche et par publication, ~150 Ko transférés par proche qui ouvre la photo. **Taille globale** (relative) : L, en deux PR Android puis deux PR iOS, plus une PR de localisation.

---

## 2. Périmètre et frontières

### Dans le périmètre
- **Bouton central de la barre d'onglets** : devient actif (y compris dans les builds de diffusion), **couleur du statut** (vert, orange, rouge ; neutre si inconnu), nom parlé qui dit l'état.
- **Écran Update** : permission caméra (explication, refus), viseur en plein écran (selfie par défaut, bascule par double toucher et par bouton), obturateur rond, aperçu de la photo, **Recommencer**, **Publier**.
- **Préparation de la photo** : ré-encodage sans métadonnées, taille, dimensions.
- **Publication** : écriture de la photo (écrasante) **et** du dernier signal (`lastUpdateAt`, `hasPhoto`) dans **chaque Miff où je partage** ; retour au Feed ; retour visuel.
- **Nettoyage par l'auteur** de sa photo expirée (§6.4).
- **Messages d'incitation** (alertes orange / rouge / zone de danger) dans l'app, qui disparaissent quand une update est publiée.
- **Localisation figée de la photo** (UPDATE-1b), après R-PHOTO-LOC.

### Hors périmètre (propriétaire indiqué)
| Sujet | Propriétaire | Frontière |
|---|---|---|
| **Affichage** de la photo dans la carte du membre et en plein écran, lecture, copie locale | **FEED-4** (`feed.md`) | L'Update **écrit** ; le Feed **lit** et n'écrit ni ne supprime jamais une photo. |
| Calcul de la couleur, bulle de la Miff, étiquettes | FEED-2 / LOC-2b | L'Update **réutilise** `StatusColor.level` (un seul calcul) et ne le change pas. |
| Détection des lieux, écriture automatique des statuts et des positions | LOC-2a / LOC-2b | L'Update n'écrit **jamais** `positions/{moi}` ni `presence`, `placeId`, `presenceAt`. |
| Notifications (push ou locales), centre de notifications | Ticket Notifications (exige Blaze) | **Aucune notification** [Décidé]. |
| Règle Firestore de la photo localisée | `backend-data` (R-PHOTO-LOC) | Dépend du « oui » du PM. |
| Texte du consentement et textes légaux | `release-manager` / `backend-data` | Ce ticket **note** ce qui est à aligner (§12, §15) ; il ne rédige aucun texte légal. |
| Texte libre accompagnant la photo, filtres, galerie, flash | Non demandés par Notion | **Absents** de la v1 (§5.9, V4). |
| Paywall | Ticket Premium | L'Update est **gratuit pour tous** (règles : « aucune règle de photo ne dépend de l'abonnement »). |

---

## 3. Décisions déjà prises (reprises telles quelles, avec leur source)

| # | Décision | Source |
|---|---|---|
| D1 | L'Update est un **écran « à la Snapchat » accessible depuis la barre d'onglets** : on y prend **une photo** et on la publie. | Notion Update |
| D2 | Le **bouton principal de la barre** prend le **code couleur de mon statut**. | Notion Update |
| D3 | **Première arrivée sur l'écran** : demande d'autorisation d'accès à l'appareil photo ; **caméra en mode selfie par défaut** ; toucher le **rond central** = prendre la photo ; **deux touchers rapides en dehors du rond** = caméra arrière ; un bouton pour **recommencer** ; un bouton **Publier** qui ramène au Feed, où la photo est **postée dans la carte du membre**. | Notion Update |
| D4 | La photo est **valable pour toutes les Miffs** ; chaque nouvelle photo **écrase** la précédente, **y compris en base**. | Notion Update |
| D5 | **Système d'alerte** pour inciter à publier : un message quand on passe en **orange** et un message quand on passe en **rouge**, qui ne disparaissent **que lorsqu'une update est publiée**. | Notion Update |
| D6 | La photo, une fois en plein écran dans le Feed, affiche **l'heure de la prise de vue** et **la localisation figée au moment de la photo**. | Notion Feed ; PM (règle R-PHOTO-LOC en préparation) ; `lieux-de-confiance.md` L7 |
| D7 | **Stockage de la photo dans Firestore** (octets, ≤ 150 000, **sans Cloud Storage**), **illisible après 24 h**, même durée pour tous les comptes, **supprimée avec le statut** (arrêt du partage, départ, retrait, suppression de Miff ou de compte). | `data-model.md` §2.8 et §15 (décision PM Q2) ; `firestore.rules` |
| D8 | **Couleur calculée à la lecture**, jamais écrite ; seuils **1 h / 2 h** ; repère = `max(presenceAt, lastUpdateAt)` ; en zone de danger : rouge, **l'update s'affiche à côté mais ne ramène pas au vert** ; la règle « une update par heure en rouge » est **abandonnée**. | `lieux-de-confiance.md` §6.9, N2, N3 ; `feed.md` D3, D5 |
| D9 | Une « update » = **la publication faite depuis l'écran Update** ; ouvrir l'app ne compte pas. | `lieux-de-confiance.md` N1 (lecture retenue) |
| D10 | **Aucune notification** (plan Spark) : les « alertes » d'incitation sont des **messages dans l'app**. | PM ; CLAUDE.md ; `feed.md` D4 |
| D11 | Ton **bienveillant, non anxiogène** ; wording **« zone de danger »** ; tutoiement ; français et anglais (anglais de repli) ; l'état n'est **jamais porté par la couleur seule**. | CLAUDE.md ; `feed.md` D15 |
| D12 | **Aucun événement Analytics** sur le Feed, les statuts, la localisation ; aucune coordonnée, aucun nom, aucun contenu de photo dans un journal ou un rapport de plantage. | `analytics-events.md` ; `feed.md` D16 |
| D13 | Solutions **gratuites d'abord**, sécurité jamais sacrifiée, anti-abus dès le début (App Check, plafonds). | CLAUDE.md |
| D14 | Le bouton central est **masqué** dans les builds de diffusion et **visible mais désactivé** dans les builds internes **tant que l'Update n'existe pas**. | `feed-profil-reglages.md` §13 Q1 ; `domain/navigation/MainTab.kt` |
| D15 | Les CGU interdisent déjà de publier une photo où figure une personne sans son accord ou un enfant dont on n'a pas l'autorité parentale ; la photo est visible des seuls membres de mes Miffs. | `docs/legal/cgu.*.md` art. 8 |
| D16 *(ex-Q1)* | **La photo est obligatoire pour publier un update en v1** : il n'existe **pas** de bouton « Je vais bien » sans photo ; « Publier » n'est possible qu'avec une photo prise. Un tel geste pourra s'ajouter plus tard **sans changer les règles** (`lastUpdateAt` seul, `hasPhoto = false`). Remplace sur ce point la décision du 2026-10-30 (`feed-profil-reglages.md` §10 Q1). | **PM, 2026-11-05** |
| D17 *(ex-Q2)* | **Les messages d'incitation sont un bandeau en haut du Feed**, au-dessus de la bulle, **non fermable, sans son ni vibration** (ni clignotement ni fenêtre bloquante) ; **titre unique « Un petit signe à tes proches ? »** et **trois phrases** (orange, rouge d'ancienneté, rouge de zone de danger) du §7 ; bouton « Donner des nouvelles ». Les textes restent à relire à voix haute et à confronter aux images de Notion une fois exportées (ajustement de détail, pas de nouvelle décision). | **PM, 2026-11-05** |
| D18 *(ex-Q3)* | **Le message disparaît aussi quand la couleur redevient verte sans publication** (arrivée dans une zone de confiance) ; il ne disparaît ni avec le temps ni par un geste ; en zone de danger, il disparaît à la publication d'une update après mon entrée (C8). | **PM, 2026-11-05** |
| D19 *(ex-Q4)* | **La couleur du bouton central (et le message d'incitation) suivent la Miff affichée dans le Feed** (un seul écouteur, aucune lecture en plus) ; publier met à jour **toutes** mes Miffs. Limite connue (Premium seulement) : le bouton peut être vert alors qu'une autre Miff me voit orange. | **PM, 2026-11-05** |
| D20 *(ex-Q5)* | **Le texte de consentement v3 est amendé pour citer la photo localisée si aucun testeur externe ne l'a accepté ; sinon on passe en v4** (re-consentement, mécanisme déjà préparé). Cela ne bloque que **UPDATE-1b**, après R-PHOTO-LOC. **Vérification « aucun testeur externe n'a accepté v3 » : à faire au moment de UPDATE-1b** (par `release-manager` / `backend-data`, [À vérifier]). | **PM, 2026-11-05** |

---

## 4. Contradictions, ambiguïtés, écarts justifiés

### 4.1 Contradictions entre sources (traitement retenu)
| # | Constat | Traitement |
|---|---|---|
| C1 | **Notion Feed/Update : photo « avec la localisation »** contre le schéma (`validPhoto` : `jpeg`, `createdAt`, `expiresAt` seulement) ; la position `positions/{uid}` est écrasée à chaque mesure et ne peut pas servir de « localisation figée ». | **Décision du PM** : localisation figée au moment de la photo → champ(s) dans le document photo (R-PHOTO-LOC, `feed.md` §6.3). **Livraison en deux temps** (UPDATE-1 sans localisation, UPDATE-1b avec) ; le client n'envoie **jamais** de clé inconnue des règles déployées. |
| C2 | **« Valable pour toutes les Miffs »** contre un schéma **par Miff** (`miffs/{miffId}/photos/{uid}`). | **N copies identiques**, une par Miff où je partage, écrites dans le même lot que les statuts. Miff rejointe après la publication : **pas de copie** (V9). Arrêt du partage dans une Miff : sa copie est supprimée (lot existant), les autres restent. |
| C3 | **« Écrase y compris en base »** : le document est remplacé (`set` sur le même chemin) ; mais `data-model.md` §2.8 dit **« illisible après 24 h, suppression physique au mieux »** (pas de TTL gratuit). | Cohérent : **écraser = `set`** (l'ancienne photo n'est conservée nulle part par Miff). Limite à noter : si des **sauvegardes ou la restauration à un instant donné** de Firestore étaient un jour activées, d'anciennes photos y subsisteraient : **ne pas les activer sans relire ce ticket** [À vérifier par `backend-data`]. |
| C4 | **Notion : photo seulement** ; `update-et-escalade-statut.md` : « photo **et texte court** » ; politique de confidentialité : « petit texte (140 caractères) et photo » ; schéma : `updateText` ≤ 140. | **Pas de texte en v1** (Notion l'emporte) : `updateText` = `null` à chaque publication. La spec est à réécrire ; les textes légaux décrivent un ensemble plus large (inexact par excès, sans risque) : à aligner plus tard par `release-manager`. |
| C5 | **Spec** : orange après 2 h sans update, rouge 2 h après, « update toutes les heures en rouge », « une update ramène à vert ». **Notion Statut** et décisions : orange à 1 h, rouge à 2 h, règle horaire abandonnée, **en zone de danger l'update ne ramène pas au vert**. | Notion et décisions du PM l'emportent (D8). La phrase « une update manuelle ramène à vert » n'est vraie **que hors zone de danger**. |
| C6 | **Décision du 2026-10-30** (« Je vais bien », photo facultative) contre **Notion Update** (photo). | **Tranché par le PM le 2026-11-05 (D16)** : photo obligatoire en v1 ; la décision du 2026-10-30 est remplacée sur ce point. |
| C7 | **Notion : « dispo depuis la tabbar »** ; le code traite le bouton central comme un **onglet sélectionnable** (`MainTab.UPDATE`). | Un viseur n'est pas un onglet : écran **plein écran sans barre**, bouton de fermeture, retour système vers l'onglet précédent (V1) [Proposé]. |
| C8 | **« Le message ne disparaît que lorsqu'un update est publié »** contre la couleur qui peut redevenir verte **sans update** (arrivée dans une zone de confiance, `lastUpdateAt` = heure d'arrivée). | **Lecture retenue** : pas de bouton de fermeture, pas de disparition par le temps ; le message disparaît dès que **je ne suis plus orange ni rouge** (update **ou** arrivée en zone de confiance), et, en zone de danger (rouge qui ne change pas), **dès qu'une update est publiée après mon entrée** (`lastUpdateAt ≥ presenceAt`). Rappeler à quelqu'un déjà revenu à la maison de « donner des nouvelles » serait absurde et anxiogène. **Confirmé par le PM le 2026-11-05 (D18).** |
| C9 | **Notion Feed : « l'heure à laquelle l'utilisateur a pris la photo »** ; le document photo ne porte que `createdAt` = **heure serveur de la publication**. | Heure affichée = publication ; écart borné en limitant la durée entre la prise de vue et Publier à **5 minutes** (V6). |
| C10 | **Un seul bouton central, plusieurs Miffs** : le statut est **par Miff** (les lieux sont par Miff) ; avec deux Miffs, je peux être vert dans l'une et orange dans l'autre. | Couleur et alertes lisent le statut de **la Miff affichée dans le Feed** (`feed.md` §5.1, un seul jeu d'écouteurs) ; **Premium seulement** (gratuit : 1 Miff). **Confirmé par le PM le 2026-11-05 (D19).** Publier **résout tout** (écrit dans toutes les Miffs). |
| C11 | **Durée de vie** : « 24 h » (PM, CGU) contre le contrat client de `data-model.md` §8 (iv) : `expiresAt` **borné à maintenant + 23 h** (horloge du téléphone, les règles tolèrent +25 h). | **23 h** côté client (respecte « plus visible après 24 h » ; la photo disparaît 1 h plus tôt). Valeur par défaut V8, objection possible. |
| C12 | **Feed (PR #72)** : « le Feed ne supprime jamais une photo expirée » ; **`data-model.md` §2.8** : « tout membre supprime la photo expirée d'un autre » ; **politique de confidentialité** : « supprimée par l'app de son auteur **ou d'un membre** ». | Seule la couche **auteur** est réellement faite (§6.4). **À aligner** : texte de la politique et `data-model.md` (`release-manager`, `backend-data`). Conséquence : le compte abandonné laisse une photo **illisible** de ≤ 150 Ko (§11). |
| C13 | **Permission caméra** : Notion l'exige ; le manifeste, `PermissionPrivacyTest`, `android.yml` et la déclaration Play disent **aucune caméra** ; la photo de profil utilise **l'appareil photo du système** sans permission. | Décision Notion (viseur intégré). À mettre à jour **dans la PR** : manifeste (+ `uses-feature` caméra **non requise**, pour ne pas exclure d'appareils), `PermissionPrivacyTest`, CI ; iOS : `NSCameraUsageDescription`, `PrivacyInfo.xcprivacy`, `ios/ci/check-binaries.sh`. |
| C14 | **`StatusColor.level` d'un membre « jamais passé en zone de confiance »** (`unknown`) est **neutre** : publier ne change rien à sa couleur. | Normal (N4) ; le retour visuel dit « Photo partagée », jamais « tu es vert ». |

### 4.2 Écarts avec les maquettes et Notion (à lister dans la PR)
Aucune maquette de l'Update n'est lisible : **tout est à confronter aux 4 images de Notion** une fois exportées. Écarts **déjà prévisibles** : bouton visible de bascule de caméra en plus du double toucher (accessibilité, §5.9 V3) ; astuce « Touche deux fois l'écran… » à la première ouverture ; message d'avertissement sur la visibilité de la photo ; états de permission, de caméra indisponible, de hors connexion, de partage arrêté (jamais dessinés) ; **« zone de danger »** au lieu de « zone à risque » ; textes d'alerte reformulés au tutoiement non anxiogène.

### 4.3 Ambiguïtés restantes (non devinées)
**Aucune ambiguïté décisionnelle ne reste ouverte** : les cinq points qu'aucun ticket ne couvrait (photo obligatoire, forme et texte des alertes, disparition au retour au vert, couleur avec plusieurs Miffs, texte de consentement et photo localisée) ont été **décidés par le PM le 2026-11-05** (D16 à D20). Tout le reste est décidé (§3) ou fixé par défaut (§5.9). Seul reste à fournir : l'export PNG des 4 images de Notion (§15, §16).

---

## 5. Parcours, états d'écran et règles métier

### 5.1 Parcours nominal
1. Dans le Feed, je vois le **bouton central coloré** (§5.4). Je le touche.
2. **Première fois** : écran d'explication (ce que la caméra sert à faire, qui voit la photo, combien de temps, rien dans la galerie) puis boîte du système. Les fois suivantes : directement le viseur.
3. **Viseur** : plein écran, caméra **frontale**, rond central, bouton fermer, bouton de bascule. Deux touchers rapides **hors des boutons** = bascule frontale ↔ arrière.
4. Je touche le rond : **photo prise** (obturateur discret, pas de son imposé par l'app) ; **aperçu** de la photo avec **Recommencer** et **Publier**. **Rien n'a quitté le téléphone.**
5. **Recommencer** : retour au viseur, la photo prise est **oubliée** (mémoire libérée).
6. **Publier** : « Partage en cours… » ; écriture dans **toutes mes Miffs où je partage** (§6) ; à l'acceptation du serveur : retour au **Feed** avec un retour visuel calme (« Photo partagée avec ton cercle »), ma carte en haut montre la photo.
7. La couleur du bouton et les messages d'incitation se mettent à jour d'eux-mêmes.

### 5.2 États de l'écran (à dessiner : §16)
| État | Contenu | Sortie |
|---|---|---|
| **A. Explication de permission** (première fois) | Pourquoi, qui voit, 24 h, rien dans la galerie ; **Continuer** / **Pas maintenant** | Continuer → boîte du système ; Pas maintenant → retour |
| **B. Permission non accordée, redemande possible** | Message calme + **Réessayer** | boîte du système |
| **C. Permission refusée définitivement** (Android : après deux refus ; iOS : dès le refus) | « La caméra est désactivée » + **Ouvrir les réglages** + **Utiliser l'appareil photo du téléphone** + **Fermer** | réglages du système / appareil photo du système (V10) |
| **D. Caméra indisponible** (utilisée par une autre application, désactivée par une politique, erreur) | Message calme + **Réessayer** + appareil photo du système + Fermer | |
| **E. Viseur** (frontal ou arrière) | Aperçu, rond, fermer, bascule ; astuce du double toucher (3 premières ouvertures, V3) | |
| **F. Aperçu** | Photo, **Recommencer**, **Publier**, ligne sur la visibilité (§7) ; si la prise de vue a plus de 5 min : « Cette photo date un peu : reprends-en une » | |
| **G. Publication en cours** | Indicateur, boutons inactifs, **pas d'annulation** une fois l'envoi parti (§6.3) | succès / H / I |
| **H. Échec : hors connexion ou refus** | Message calme, **la photo reste à l'écran**, **Réessayer** ; **rien n'est parti** (critère OF1) | |
| **I. Succès partiel** (Premium, plusieurs lots) | « Photo partagée dans {n} de tes {m} Miffs » + **Réessayer pour les autres** | |
| **J. Partage arrêté dans toutes mes Miffs** | Pas de viseur, pas de permission demandée : « Tu as arrêté de partager. Reprends le partage pour donner de tes nouvelles. » + bouton vers l'écran de la Miff | |
| **K. Trop de publications aujourd'hui** (garde-fou, §5.9 V5) | Message calme, bouton Publier inactif | |

### 5.3 Viseur : règles
- **Selfie par défaut** à chaque ouverture [Notion]. Sans caméra frontale : caméra arrière, **bouton de bascule masqué** ; sans aucune caméra : état D.
- **Double toucher** : **deux touchers rapides en dehors du rond central** (et des boutons fermer / bascule) = bascule ; le même geste **revient** à la frontale [Proposé : Notion dit « met la caméra arrière », sans retour possible un utilisateur serait bloqué]. Deux touchers sur le rond **ne prennent qu'une photo** (la seconde prise de vue est ignorée tant que la première n'est pas terminée).
- **Bouton de bascule visible** [Proposé] : le double toucher est invisible, difficile avec un tremblement ou un lecteur d'écran, et ne se découvre pas.
- **Pas de flash, de zoom, de filtre, de minuterie, de texte, de dessin, de galerie** (non demandés). **Pas de choix d'une photo ancienne** : l'Update signifie « voilà où j'en suis maintenant » ; une photo de galerie donnerait une fausse réassurance (R2).
- L'aperçu **frontal est en miroir** (comme un miroir) ; **la photo publiée n'est pas en miroir** (le texte photographié reste lisible) ; l'**aperçu de validation montre l'image telle qu'elle sera publiée**.
- **La caméra n'est active que si l'écran est visible** : elle s'arrête dès que l'application passe en arrière-plan, que l'écran se verrouille ou qu'on le quitte. **Miff n'ouvre jamais la caméra de lui-même et ne peut pas être déclenché à distance** (point de confiance, critère PR1).
- **`RecentsPrivacy`** appliqué à l'écran (ni le viseur ni la photo n'apparaissent dans les applications récentes).

### 5.4 Bouton central de la barre
| Niveau de **mon** statut (Miff affichée, C10) | Rendu | Nom parlé |
|---|---|---|
| Vert | couleur « sûr » | « Donner des nouvelles, tout va bien » |
| Orange | couleur « attention » | « Donner des nouvelles, tu es en orange » |
| Rouge | couleur « danger » | « Donner des nouvelles, tu es en rouge » |
| Neutre (statut inconnu, partage arrêté) | anneau actuel (jaune-vert de marque) | « Donner des nouvelles » |
- La couleur seule n'est **jamais** l'information (nom parlé ; bords et remplissage distincts) ; contraste conforme au design system ; **le bouton est actif** dès qu'il y a une Miff (D14 levée par ce ticket, y compris en diffusion).
- Calcul : **`StatusColor.level` inchangé**, recalculé chaque minute avec le Feed (aucune lecture serveur en plus, un seul écouteur).

### 5.5 Messages d'incitation (D5, D10)
- **Forme** [Décidé, D17 ; détail de dessin à confronter aux images de Notion une fois exportées] : un **bandeau** en haut du Feed, au-dessus de la bulle, teinte douce de la couleur du niveau, **un titre, une phrase, un bouton « Donner des nouvelles »** qui ouvre l'écran Update. **Ni fenêtre bloquante, ni son, ni vibration, ni clignotement, ni bouton de fermeture** (Notion : ne disparaît que par une update).
- **Quand** : mon niveau (Miff affichée) est **orange** ou **rouge** ; en zone de danger : tant qu'aucune update n'a été publiée depuis mon entrée.
- **Disparition** [Décidé, D18] : voir C8. La page de ma bulle à la 2e personne (`feed.md` §5.2) **reste** (elle décrit l'état de la Miff), **sans bouton** pour ne pas doubler l'appel à l'action.
- **Textes** : §7. Un seul titre pour les trois cas, pour garder un ton constant et sans surenchère ; la phrase donne le fait, jamais un reproche.
- **Limite connue** : le message n'existe que si l'app est ouverte (pas de notification) ; il ne remplace pas la bulle que **les proches** voient.

### 5.6 Photo : fabrication (côté téléphone)
- **Source** : image prise par le viseur **en mémoire** (aucun fichier) ; pour l'appareil photo du système (repli, V10) : un fichier dans le **cache privé**, supprimé dès la lecture (même mécanisme que la photo de profil).
- **Sortie** : JPEG **ré-encodé depuis les pixels** (aucune métadonnée : ni EXIF, ni GPS, ni date, ni modèle, ni miniature) ; orientation **redressée** à partir de la source puis oubliée ; **portrait 3:4**, côté long **≤ 1 024 px** ; taille **≤ 150 000 octets** (plafond dur des règles, **mesuré sur les octets réels**), cible ≤ 100 Ko : le codeur descend la qualité puis la dimension jusqu'à tenir, **comme `AvatarCompression.fit`** ; si rien ne tient : message « On n'a pas pu préparer cette photo. Réessaie. », aucune écriture.
- **Aucune écriture dans la galerie ni dans un dossier partagé ; aucune permission de stockage ou de photothèque.**
- **Aucun traitement du contenu** (pas de détection de visage, pas d'analyse) ; le contenu de la photo ne va **ni** dans Analytics, **ni** dans un rapport de plantage, **ni** dans un journal.
- **Fraîcheur** : une photo en attente de publication depuis **plus de 5 minutes** est **oubliée** (V6).

### 5.7 Publication : règles
- **Une seule opération** pour toutes mes Miffs où **mon partage est actif** (lots de 5 Miffs au plus, §6.3), dans chaque Miff : `photos/{moi}` **set** (remplace) + `status/{moi}` **update** `{ lastUpdateAt = maintenant, hasPhoto = true, updateText = null, batteryPct, updatedAt = heure serveur, deviceId }` ; `presence`, `placeId`, `presenceAt` **inchangés**.
- **Durée** : `expiresAt` = maintenant + 23 h (C11) ; `createdAt` = heure serveur (imposé par les règles).
- **Publier hors connexion** : **interdit en pratique** : le bouton n'annonce « partagé » qu'à l'**accusé du serveur** ; sans réseau, message calme (état H), la photo reste à l'écran, **aucune écriture n'est mise en file pour partir plus tard à l'insu de la personne** (une mise à jour différée de plusieurs heures donnerait une fausse réassurance) ; le mécanisme (transaction ou lot avec attente de l'accusé) est choisi par `android-dev` et **testé en mode avion**.
- **Garde-fou** [Proposé, V5] : **20 publications par jour et par téléphone** (aide à l'utilisateur et au quota ; la vraie protection reste App Check et la surveillance).
- **`deviceId` périmé** (SESS-1) : le lot **entier** est refusé : suivre le contrat existant (« tu es connecté sur un autre appareil »), jamais un message d'erreur technique.
- **Après le succès** : copie en mémoire de ma photo (pour ma carte, sans relecture serveur) et date de publication **enregistrée localement** (horodatage seul, pas la photo), pour le nettoyage §6.4.

### 5.8 Effacement et écrasement
| Événement | Ce qui est effacé | Par qui |
|---|---|---|
| Nouvelle publication | la photo précédente est **remplacée** dans chaque Miff concernée | l'Update (`set`) |
| **24 h** (23 h côté client) | **illisible** pour les membres | règles Firestore (`expiresAt`) |
| Photo expirée | **suppression physique** de ma photo + `hasPhoto = false`, dans chaque Miff, au **lancement** et au **retour au premier plan** si ma date locale a plus de 24 h, et à la publication suivante | l'app de l'auteur (§6.4) |
| Arrêt du partage d'une Miff, départ, retrait, Miff supprimée, suppression de compte | photo + statut + position, dans le même lot | lots existants (aucun changement) |
| Déconnexion, perte d'accès | cache local Firestore purgé (contrat de purge existant) | existant |
| Recommencer, fermeture de l'écran, passage en arrière-plan | la photo en mémoire est oubliée | l'Update |

### 5.9 Valeurs par défaut retenues (applicables sauf objection du PM)
| # | Sujet | Valeur retenue |
|---|---|---|
| V1 | Écran | **Plein écran, sans barre d'onglets**, bouton fermer en haut à gauche, retour système vers l'onglet précédent |
| V2 | Caméra au départ | **Frontale** ; sinon arrière |
| V3 | Bascule | Double toucher (aller et retour) **et** bouton visible ; astuce du double toucher à l'écran pendant les **3 premières** ouvertures |
| V4 | Fonctions absentes | flash, zoom, filtres, minuterie, texte, dessin, galerie |
| V5 | Garde-fou | **20 publications par jour et par téléphone** ; **30 s minimum** entre deux publications |
| V6 | Fraîcheur | Photo en attente > **5 min** : oubliée (« Cette photo date un peu… ») |
| V7 | Format | Portrait 3:4, côté long ≤ 1 024 px, JPEG, ≤ 150 000 octets (cible 100 Ko) |
| V8 | Durée | `expiresAt` = maintenant + **23 h** (contrat `data-model.md` §8 iv) |
| V9 | Miff rejointe après la publication | **Aucune copie** de la photo ; elle apparaît à la prochaine publication |
| V10 | Repli | **Appareil photo du système** (sans permission, photo du moment) quand la permission est refusée ou la caméra intégrée indisponible ; mêmes règles de ré-encodage ; **jamais de galerie** |
| V11 | Retour visuel | Message calme (« Photo partagée avec ton cercle »), sans son ni vibration ; ma carte montre la photo |
| V12 | Message d'alerte | Bandeau fixe au-dessus de la bulle, sans fermeture (**décidé, D17**) |

---

## 6. Contrat avec les règles Firestore déployées et le modèle de données

### 6.1 Ce que les règles permettent (vérifié dans `backend/firestore.rules`)
| Écriture de l'Update | Règle | Remarque |
|---|---|---|
| `set miffs/{id}/photos/{moi}` | `isSelf` + `validPhoto` : clés **exactement** `jpeg`, `createdAt`, `expiresAt` ; `jpeg` octets de 1 à **150 000** ; `createdAt == request.time` ; `expiresAt` > maintenant et ≤ **maintenant + 25 h** ; **mon partage actif après le lot** (`getAfter` sur ma fiche) | create **et** update acceptés : `set` écrase. **Aucune condition de consentement version 3** sur la photo (la version ne sert qu'à la position) |
| `update miffs/{id}/status/{moi}` | `isSelf` + `validStatus` : **toutes les clés de l'ancienne valeur restent** (mise à jour partielle fusionnée) ; `updatedAt == request.time` ; `lastUpdateAt` entre −24 h et +5 min **ou** inchangé (R-LIEU-1) ; `updateText` ≤ 140 ou `null` ; `hasPhoto` booléen ; `deviceId` = celui de la session (SESS-1) | `presenceAt` inchangé = accepté même au-delà de 24 h |
| `delete photos/{moi}` | `isSelf` | utilisé par le nettoyage §6.4 et déjà par les lots d'arrêt / départ |
- **Aucune règle à créer ni à modifier pour UPDATE-1.** Lecture de la photo : **FEED-4** (`get`, jamais `list`).
- La règle **ne vérifie pas** que `hasPhoto` correspond à une photo existante : la **cohérence est l'affaire du client** (même lot).

### 6.2 Stockage de la photo : Firestore ou Cloud Storage ?
- **Firestore** (décidé, D7). **Cloud Storage** : d'après la page des tarifs Firebase lue le 2026-11-05, le plan Spark n'a d'usage gratuit que pour l'ancien espace `*.appspot.com` (5 Go) ; pour `*.firebasestorage.app` et les espaces supplémentaires la colonne Spark indique « non applicable », et le quota gratuit des espaces récents n'existe que dans `us-central1`, `us-west1`, `us-east1` : **avec Blaze et des données à Paris, l'usage serait payant dès le premier octet** [À vérifier dans la console avant toute bascule]. `initialisation-technique.md` §3 le notait déjà (« Cloud Storage non disponible »).
- **Plafond de taille** : un document Firestore pèse au plus **1 MiB** (1 048 576 octets, champ ≤ 1 048 487) d'après la page des quotas (lue le 2026-11-05) ; nos **150 000 octets** sont **sans rapport avec ce plafond** : c'est un choix de **coût de transfert** (§11) inscrit dans les règles, pas une contrainte technique. Une requête d'écriture est limitée à 10 MiB : 5 photos de 150 Ko par lot = 0,75 Mo, très en dessous.
- **Si le volume explose** : la migration vers Cloud Storage (Blaze) remplacerait `jpeg` par un chemin (`data-model.md` §13) sans toucher à l'écran Update.

### 6.3 Coût en lectures de règles et taille des lots (comptage manuel à confirmer par test)
- Par Miff : statut = ma fiche (`getAfter`) + session (`deviceId`) ; photo = ma fiche (`getAfter`) → **≤ 3 accès de règles** au pire cas (comptés sans cache).
- **Limite : 20 accès par lot** → **lots de 5 Miffs au plus** (15 sur 20, marge) ; au-delà, plusieurs lots (Premium seulement ; le compte gratuit n'a qu'une Miff). **`backend-data` confirme par un test** (comme SC-B de `suppression-compte.md`) ; en cas d'écart, réduire la taille des lots, **sans changer les règles**.
- **Un lot refusé n'écrit rien** : pour plusieurs lots, un échec du second laisse la photo publiée dans le premier groupe de Miffs → **état I** (succès partiel), nouvelle tentative sur les Miffs restantes seulement.

### 6.4 Nettoyage par l'auteur (le seul qui existe réellement : C12)
- **Déclencheur** : au lancement (après la lecture de mon statut par le Feed) et au retour au premier plan, si la date de publication **conservée localement** a plus de 24 h et que `hasPhoto` est vrai dans mon statut : `delete photos/{moi}` + `update status/{moi}` `{ hasPhoto = false, updatedAt }` dans chaque Miff où je partage (lots de 5). **0 lecture serveur.** Si le téléphone a été réinstallé (date locale perdue) : se fonder sur `lastUpdateAt` de plus de 25 h.
- **Coût** : au plus **2 écritures par Miff et par photo expirée**, une fois.
- **Limite** : si la personne n'ouvre plus l'app, la photo **reste physiquement** (≤ 150 Ko, **illisible**) jusqu'à l'arrêt du partage, le départ, le retrait ou la suppression de compte.

### 6.5 Contrat avec R-PHOTO-LOC (UPDATE-1b)
- **Rappel** (`feed.md` §6.3, proposition de `backend-data`, non déployée) : `lat` et `lng` **nullables** dans le document photo, 5 décimales, écrits seulement avec un consentement de version 3 ou plus, **supprimés avec la photo** ; lecture : photo localisée visible seulement d'un membre qui a publié **sa propre** position.
- **Côté Update** : au moment de l'obturateur, **une** mesure (permission déjà accordée, en premier plan elle suffit) ; attente **3 s** au plus, mesure de **moins de 10 min** (mêmes constantes que LOC-2b : `POSITION_WAIT_MILLIS`, `FIX_MAX_AGE_MILLIS`) ; sans mesure, sans accord version 3 ou avec la localisation refusée : **photo sans localisation, sans message d'erreur**. La position est **figée à la prise de vue** (« Recommencer » l'oublie ; « Publier » ne la remesure pas). **L'Update n'écrit jamais `positions/{moi}`.**
- **Ordre de livraison** (comme la position) : **règles déployées d'abord**, build qui envoie `lat` / `lng` **ensuite** (un client qui n'envoie pas les clés reste accepté).
- **Aucune coordonnée** dans un journal, un rapport de plantage, un événement d'analyse, l'état sauvegardé de l'écran ; elle n'existe qu'en mémoire jusqu'à l'envoi.
- **Consentement** : le texte de la version 3 ne cite que la « dernière position connue » (`consentement-localisation-v3.md` O4) : la photo localisée doit y figurer : **décidé (D20)** : texte v3 amendé si aucun testeur externe ne l'a accepté, sinon v4 ; la **vérification** « aucun testeur externe n'a accepté v3 » est **à faire au moment de UPDATE-1b**.

### 6.6 Rien d'autre
`status.deviceId` (SESS-1) est obligatoire pour que l'écriture passe quand la session est en temps 2 ; `users/{uid}` n'est pas touché ; aucun champ de statut nouveau n'est demandé.

---

## 7. Textes proposés (FR / EN, tutoiement, **à relire à voix haute par le PM**)

Clés `update_` (nouveau préfixe à ajouter à `docs/contracts/i18n-keys.md` et au script de parité) ; noms parlés du bouton central sous `tabbar_update_*_a11y` (clés existantes `tabbar_update_a11y`, `tabbar_update_soon_a11y` à faire évoluer) ; heures au format du téléphone ; formulations **sans accord de genre** ; aucun texte n'a été relu par un juriste (décision du 2026-10-23).

### Permission et états de la caméra
| Cas | FR | EN |
|---|---|---|
| Explication, titre | « Une photo pour tes proches » | « A photo for your loved ones » |
| Explication, texte | « Pour te prendre en photo, ou photographier ce qui t'entoure, Miff a besoin d'accéder à ta caméra. La photo est visible des membres de tes Miffs pendant 24 h, puis elle n'est plus lisible. Rien n'est enregistré dans ta galerie. » | « To take a photo of you, or of what's around you, Miff needs access to your camera. The photo can be seen by the members of your Miffs for 24 hours, then it can no longer be read. Nothing is saved to your gallery. » |
| Boutons | « Continuer » / « Pas maintenant » | « Continue » / « Not now » |
| Redemande | « Tu pourras donner de tes nouvelles en photo dès que la caméra sera autorisée. » + « Réessayer » | « You'll be able to share news with a photo as soon as the camera is allowed. » + « Try again » |
| Désactivée | « La caméra est désactivée » + « Tu peux l'activer dans les réglages de ton téléphone, ou utiliser l'appareil photo du téléphone. » | « The camera is turned off » + « You can turn it on in your phone's settings, or use your phone's camera app. » |
| Boutons | « Ouvrir les réglages » / « Utiliser l'appareil photo du téléphone » / « Fermer » | « Open settings » / « Use the phone's camera app » / « Close » |
| Indisponible | « La caméra n'est pas disponible pour le moment. Ferme les autres applications qui l'utilisent, puis réessaie. » | « The camera isn't available right now. Close any other app using it, then try again. » |
| Aucune application | « Cette action n'est pas disponible sur cet appareil. » | « This action isn't available on this device. » |

### Viseur et aperçu
| Élément | FR | EN |
|---|---|---|
| Aide sous le viseur | « Un selfie, la vue, ton repas : comme tu veux. » | « A selfie, the view, your lunch: whatever you like. » |
| Astuce (3 premières fois) | « Touche deux fois l'écran pour changer de caméra. » | « Double-tap the screen to switch camera. » |
| Obturateur (parlé) | « Prendre la photo » | « Take the photo » |
| Bascule (parlé) | « Changer de caméra » | « Switch camera » |
| Fermer (parlé) | « Fermer » | « Close » |
| Photo prise (annonce) | « Photo prise. Tu peux la publier ou recommencer. » | « Photo taken. You can publish it or retake it. » |
| Aperçu, boutons | « Recommencer » / « Publier » | « Retake » / « Publish » |
| Visibilité | « Tes proches verront cette photo pendant 24 h. » | « Your loved ones will see this photo for 24 hours. » |
| Visibilité + lieu (si localisée) | « Tes proches verront cette photo pendant 24 h. Ceux qui partagent aussi leur position verront où tu l'as prise. » | « Your loved ones will see this photo for 24 hours. Those who also share their position will see where you took it. » |
| Trop ancienne | « Cette photo date un peu : reprends-en une. » | « This photo is a bit old: take a new one. » |
| Préparation impossible | « On n'a pas pu préparer cette photo. Réessaie. » | « We couldn't prepare this photo. Try again. » |

### Publication
| Cas | FR | EN |
|---|---|---|
| En cours | « Partage en cours… » | « Sharing… » |
| Succès | « Photo partagée avec ton cercle » | « Photo shared with your circle » |
| Hors connexion | « Pas de connexion pour le moment. Ta photo n'est pas partie : réessaie dès que le réseau est revenu. » | « No connection right now. Your photo hasn't been sent: try again once you're back online. » |
| Succès partiel | « Ta photo est partagée dans {n} de tes {m} Miffs. Réessaie pour les autres. » | « Your photo was shared in {n} of your {m} Miffs. Try again for the others. » |
| Partage arrêté | « Tu as arrêté de partager. Reprends le partage pour donner de tes nouvelles. » | « You stopped sharing. Resume sharing to share your news. » |
| Trop de photos | « Tu as déjà partagé beaucoup de photos aujourd'hui. Réessaie un peu plus tard. » | « You've already shared a lot of photos today. Try again a little later. » |
| Autre appareil / accès perdu | textes existants de SESS-1 et de la perte d'accès | idem |

### Messages d'incitation (titre et trois phrases **validés par le PM le 2026-11-05**, D17 ; images de Notion illisibles : à confronter à l'export, relecture à voix haute à la recette)
| Cas | Titre (commun) | Phrase | Bouton |
|---|---|---|---|
| Orange | « Un petit signe à tes proches ? » / « A little sign for your loved ones? » | « Tu as quitté {lieu} à 16 h 40. Une photo suffit pour partager de tes nouvelles. » / « You left {place} at 4:40 PM. One photo is enough to share your news. » | « Donner des nouvelles » / « Share news » |
| Rouge, ancienneté | idem | « Tes proches n'ont pas eu de tes nouvelles depuis 16 h 02. Une photo suffit pour leur en donner. » / « Your loved ones haven't heard from you since 4:02 PM. One photo is enough to let them know. » | idem |
| Rouge, zone de danger | idem | « Tu es dans une zone de danger de ta Miff. Une photo leur donne de tes nouvelles. » / « You're in a danger zone of your Miff. A photo shares your news with them. » | idem |
- **Jamais** : « urgent », « attention », « inquiets », point d'exclamation, majuscules, rouge plein écran. La couleur du bandeau est **une teinte douce**.
- Noms parlés du bouton central : « Donner des nouvelles, tout va bien » / « …, tu es en orange » / « …, tu es en rouge » (EN : « Share news, all good » / « …, you're in orange » / « …, you're in red »).

---

## 8. Cas limites

1. **Aucune caméra frontale** : viseur arrière, bascule masquée. **Aucune caméra** (émulateur, tablette) : état D, repli du système si disponible.
2. **Caméra prise par une autre application** (appel vidéo) : état D, jamais d'erreur technique.
3. **Permission retirée dans les réglages pendant l'usage** : le viseur se ferme, état B ou C au retour ; aucune photo n'est publiée.
4. **Appel entrant, verrouillage, passage en arrière-plan sur l'aperçu** : la caméra est libérée ; la photo en mémoire est conservée **jusqu'à 5 min**, puis oubliée ; si l'app est tuée par le système, la photo est simplement perdue (rien sur disque).
5. **Publier hors connexion** : état H ; **test en mode avion** : rien ne part plus tard.
6. **Réseau coupé en pleine publication** : pas de « partagé » sans accusé ; la personne peut réessayer ; une seconde tentative **écrase** (aucun doublon possible : un document par Miff).
7. **Plusieurs Miffs (Premium)** : lots de 5, succès partiel possible (état I) ; si l'une est refusée (retiré, partage arrêté entre-temps), les autres passent et la Miff refusée est traitée par le contrat de perte d'accès.
8. **Partage arrêté dans certaines Miffs seulement** : la photo n'est écrite que dans celles où il est actif.
9. **Partage arrêté dans toutes** : état J (aucune permission demandée).
10. **Retiré d'une Miff ou Miff supprimée pendant la publication** : refus de ce lot, message neutre, perte d'accès gérée comme ailleurs.
11. **Compte gratuit et Miff Premium d'un autre** : sans effet (aucune règle de photo ne dépend de l'abonnement).
12. **Statut `unknown` (jamais passé en zone de confiance)** : le bouton reste neutre ; publier met à jour `lastUpdateAt` et la photo, sans changer la couleur (C14).
13. **Je suis en zone de danger** : rouge avant et après la publication ; le message d'incitation disparaît (C8) ; la photo s'affiche à côté (D8).
14. **Je suis vert** (à la maison) : l'Update reste possible « à tout moment » ; aucun message d'incitation.
15. **Orange puis arrivée à la maison sans update** : vert, le message disparaît (C8, **décidé D18**).
15 bis. **Personne qui ne veut ou ne peut pas se photographier** (lieu public, gêne) : la photo reste **obligatoire** (D16), mais elle peut montrer **n'importe quoi** (la vue, un repas) : c'est le sens de l'aide sous le viseur ; si la caméra intégrée est inutilisable, le repli appareil photo du système (V10) fournit toujours **une photo**, jamais une publication sans photo. Pas de bouton « Je vais bien » en v1.
15 ter. **Plusieurs Miffs (Premium), couleurs différentes selon la Miff** : le bouton et le bandeau suivent **la Miff affichée dans le Feed** (D19) ; changer de Miff dans le Feed change la couleur du bouton et le bandeau ; publier met à jour toutes les Miffs, donc résout l'alerte dans chacune.
15 quater. **Bandeau d'incitation et page de bulle à la 2e personne** : les deux coexistent, **un seul bouton** (celui du bandeau) ; le bandeau ne peut pas être fermé : il disparaît seulement selon D18.
16. **Horloge du téléphone décalée** : `lastUpdateAt` et `expiresAt` sont bornés par les règles ; si l'horloge est en avance de plus d'1 h, le lot est refusé (message neutre « réessaie », sans accuser l'horloge) ; `expiresAt` à 23 h laisse 2 h de marge.
17. **Photo impossible à faire tenir en 150 000 octets** : message, aucune écriture.
18. **Photo de tiers, d'un enfant, d'une pièce du domicile** : voir R3 ; aucun filtre automatique possible.
19. **Double toucher accidentel** pendant la visée : bascule ; bouton visible pour revenir ; l'obturateur n'est pas déclenché.
20. **Lecteur d'écran, texte maximal, droite-à-gauche** : voir critères A1 à A6.
21. **Changement de langue en cours d'écran** : tous les textes suivent.
22. **Mon statut ou mon partage change pendant que l'écran est ouvert** (retrait depuis un autre appareil) : l'écran se ferme sur le message de perte d'accès existant.
23. **Mise à jour de l'app avec une publication en attente** : rien n'est en attente par construction (§5.7).
24. **Fond sombre / mode économie d'énergie** : aucun effet sur la photo ; la bascule reste disponible.
25. **iPhone 11 Pro** : viseur AVFoundation, autorisation une seule fois (refus = réglages) ; restrictions parentales (« caméra restreinte ») : message calme équivalent à l'état C sans bouton réglages.

---

## 9. Découpage proposé (Android d'abord, puis iOS ; une branche et une PR par sous-ticket)

| # | Ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| **UPDATE-0** | **Décisions, maquettes, règles** (PM, `design-system`, `backend-data`) | Export PNG des 4 images de Notion + maquettes complétées (§16) ; R-PHOTO-LOC préparée (sur « oui ») ; réécriture de `update-et-escalade-statut.md` ; alignement des textes légaux et de `data-model.md` (C12, D20) ; décisions D16 à D20 déjà prises | S à M | Validation de ce document |
| **UPDATE-1** | **Android : écran Update et publication** | Permission et explication, viseur CameraX (selfie, bascule, obturateur), aperçu, Recommencer / Publier, codec photo (ré-encodage, 150 000 octets), écriture multi-Miff (lots de 5), nettoyage auteur, retour au Feed, bouton central **actif** (couleur neutre), repli appareil photo du système, manifeste / test / CI mis à jour | L | MIFF-1, LOC-2b (fusionnés), FEED-1 (carte « moi ») ; D16 (photo obligatoire : **aucun** chemin de publication sans photo) |
| **UPDATE-2** | **Android : couleur du bouton et messages d'incitation** | Couleur du bouton central (`StatusColor.level`), noms parlés, bandeau d'incitation et disparition (C8) | S à M | UPDATE-1, **FEED-2** (écouteur de mon statut) ; D17 (bandeau), D18 (disparition), D19 (Miff affichée) ; maquettes du bandeau (export Notion) |
| **FEED-4** *(`feed.md`)* | **Android : affichage de la photo** | Lecture paresseuse, copie locale, plein écran | M | UPDATE-1, FEED-2 |
| **UPDATE-1b** | **Android : localisation figée de la photo** | Mesure à l'obturateur, `lat` / `lng` dans le document photo, ligne de visibilité | S à M | UPDATE-1, **R-PHOTO-LOC déployée**, D20 (texte de consentement v3 amendé ou v4 ; **vérification « aucun testeur externe n'a accepté v3 » à faire au début de UPDATE-1b**) ; FEED-4 (affichage « Voir où c'était ») |
| **UPDATE-3 à UPDATE-4** | **iOS** : mêmes livrables que UPDATE-1 et UPDATE-2 | AVFoundation, `NSCameraUsageDescription`, manifeste de confidentialité, CI ; spécificités §10 | Même taille | Équivalents Android, INIT-4 |
| **FEED-8** *(`feed.md`)* et **UPDATE-3b** | **iOS** : affichage de la photo (FEED-8) et localisation figée de la photo (UPDATE-3b) | Même taille | UPDATE-3, R-PHOTO-LOC déployée |
- **Ordre conseillé** [Proposé] : UPDATE-0 → UPDATE-1 → (FEED-4 en parallèle de recette) → UPDATE-2 → UPDATE-1b ; la photo ne se **recette** sur téléphone qu'avec **UPDATE-1 + FEED-4** (le PM publie sur l'OPPO, regarde sur l'iPhone ou un second compte).
- Chaque PR **liste ses écarts avec les maquettes** et **mesure ses écritures et lectures** (§11).

---

## 10. Différences Android / iOS

| Sujet | Android | iOS |
|---|---|---|
| Caméra intégrée | CameraX (`ImageCapture` en mémoire, sélecteur de caméra avant / arrière) ; permission `CAMERA` ; `uses-feature` caméra et caméra frontale **non requises** | AVFoundation (`AVCaptureSession`) ; `NSCameraUsageDescription` ; pas de simulateur de caméra : **test sur l'iPhone 11 Pro via TestFlight seulement** |
| Permission | Boîte du système ; après deux refus, plus de boîte : réglages | Boîte **une seule fois** ; ensuite réglages ; restriction parentale possible |
| Double toucher | Détection de geste sur le viseur ; **action d'accessibilité** « Changer de caméra » | Idem ; **action personnalisée VoiceOver** |
| Repli appareil photo du système | Intention « prendre une photo » vers un fichier du **cache privé** (mécanisme de la photo de profil) | `UIImagePickerController` (source caméra) ou équivalent ; **jamais** la photothèque |
| Applications récentes | `RecentsPrivacy()` | Écran de masquage au passage en arrière-plan [À vérifier par `ios-dev`] |
| Ré-encodage | `Bitmap.compress` (aucune métadonnée) | `UIImage.jpegData` depuis les pixels (vérifier l'absence d'EXIF dans les octets) |
| Confidentialité des stores | Déclaration Play : photos (facultatives), caméra | `PrivacyInfo.xcprivacy` : photos ; fiche App Privacy |
| Tests | OPPO Find X5 Lite (Android 13, ColorOS : caméra et arrière-plan à surveiller) | iPhone 11 Pro (iOS 26.5.2), TestFlight |
| Zones tactiles | 48 dp (obturateur ≥ 72 dp) | 44 pt (obturateur ≥ 72 pt) |

---

## 11. Coût Spark (objectif : 0 €, et **voir venir** le plafond)

**Hypothèses** (estimations, pas des mesures) : Miff de 4 membres, une Miff par personne (gratuit) ; **2 publications par personne et par jour** ; les trois proches ouvrent la photo une fois chacun.

| Poste | Par publication (gratuit, 1 Miff) | Par utilisateur actif et par jour (2 publications, 3 proches) | Quota Spark | Remarque |
|---|---|---|---|---|
| **Écritures** | **2** (photo + statut) ; nettoyage : 2 par photo expirée | ~**4 à 8** | 20 000 par jour (partagés avec la détection : ~30 à 70 par téléphone, `surveillance-quotas.md`) | +5 à 10 % sur la détection ; **Premium : 2 × N** par publication |
| **Lectures de règles** (écriture) | ≤ **3** | ~6 | 50 000 par jour | |
| **Lectures des proches** | par proche : 1 (mise à jour du statut reçue) + 1 (photo) + 2 (règles de la photo) = **4** | ~**24** reçues en plus de l'usage du Feed | 50 000 par jour | **Le Feed estimait déjà 3 lectures par photo** (`feed.md` §11) : l'écart d'une lecture est le statut modifié |
| **Transfert sortant** (lecture par les proches) | jusqu'à **150 Ko** par proche qui ouvre la photo, sans copie locale | ~**0,9 Mo** par jour et par utilisateur (**~27 Mo par mois**) | 10 Gio par mois | ~370 utilisateurs actifs avant le plafond **sans** copie locale ; **~7 Mo par mois** avec copie locale (FEED-4) |
| **Stockage** | 150 Ko par membre et par Miff, **écrasés** | borné par le nombre de comptes × Miffs | 1 Gio | ≈ **7 000** photos simultanées par Gio (`data-model.md` §2.8) ; les photos de comptes abandonnés restent (≤ 150 Ko, illisibles) jusqu'à l'arrêt du partage ou la suppression du compte |
| **Envoi depuis le téléphone** | 150 Ko × N (gratuit : 150 Ko) | ~0,3 Mo | pas de quota (entrée gratuite) | surtout un coût de **données mobiles** pour la personne : un seul envoi par publication |
- **Premier plafond atteint** (avec la détection et le Feed) : les **lectures**, puis le **transfert** si la copie locale n'est pas faite.
- **Mitigations gratuites écrites en critères** : taille ≤ 150 000 octets cible 100 Ko ; plafond de **20 publications par jour** ; **30 s** entre deux publications ; aucune relecture de sa propre photo (copie en mémoire) ; nettoyage **sans lecture serveur** ; App Check (déjà prévu) contre les abus.
- **Signal et option payante** : mêmes seuils que `feed.md` §11 (ratio de lectures et lectures du jour) ; **si le transfert dépasse 6 Gio par mois** ou **les écritures 12 000 par jour de façon répétée** : (1) vérifier la copie locale de FEED-4, (2) réduire la dimension et la qualité cibles (sans toucher au plafond des règles), (3) **décision PM sur Blaze** (Cloud Storage, coût indicatif à confirmer, quelques euros par mois à ~2 000 utilisateurs actifs par jour). **Jamais de contournement** des conditions d'utilisation pour rester gratuit.
- **Chaque PR Update mesure** : écritures et accès de règles d'une publication (1 Miff, puis 5 Miffs en émulateur), taille réelle de dix photos types (selfie en pleine lumière, scène chargée, nuit), lectures du rattachement.

---

## 12. Risques

| # | Risque | Effet | Mitigation |
|---|---|---|---|
| R1 | **Ton anxiogène** : bandeau rouge persistant sur **son propre** téléphone, « zone de danger », sans possibilité de le fermer | Perte de confiance, contre-sens avec CLAUDE.md | Titre unique et doux, phrase factuelle, teinte douce, aucun son ni clignotement, action simple ; texte relu à voix haute par le PM (forme et textes validés, D17) |
| R2 | **Fausse réassurance** : une photo n'est pas une preuve que tout va bien (photo mise en scène, de la maison alors qu'on est ailleurs) ; l'alerte n'atteint pas quelqu'un dont l'app est fermée ; une update ramène à vert **sans** avoir vérifié le lieu | Un proche croit que tout va bien | Photo **toujours datée** (« Photo de 15 h 40 »), jamais « en sécurité » ; **aucune photo de galerie** ; en zone de danger l'update **ne** ramène **pas** au vert ; limite « pas de notification » **assumée** (D10) |
| R3 | **Photo = donnée sensible** : visage, intérieur d'un domicile, fond révélant un lieu, **tiers** (CGU art. 8 le déconseille mais ne peut pas l'empêcher), **mineurs** (aucun âge minimum, J2 / J31 assumés) ; une personne qui obtient le code d'une Miff voit **les photos et les positions** de ses membres (R2 / R4 assumés) | Atteinte à la vie privée, plainte, rejet des stores | Visible des seuls membres, **24 h**, écrasée, effacée avec le partage ; le créateur peut **retirer** un membre (code renouvelé) ; texte de visibilité à l'aperçu ; aucune analyse du contenu ; **risque assumé par le PM** (décisions du 2026-10-23 et du 2026-11-01), jamais présenté comme relu par un juriste |
| R4 | **Contenu publié par les utilisateurs (modération)** : les règles des stores sur les contenus générés par les utilisateurs (Apple 1.2, politique Google Play équivalente) demandent en général un moyen de **signaler**, de **bloquer** et un contact ; **aucune analyse automatique des images n'est possible sur Spark** (aucune Cloud Function) | Rejet ou retrait de l'app | **[À vérifier par `release-manager`]** : exigences exactes pour un cercle fermé sur invitation ; pistes gratuites : retrait d'un membre par le créateur (existe), contact de signalement (e-mail du support) ; **jamais** rédigé comme « modéré » |
| R5 | **Captures d'écran par les proches** : « illisible après 24 h » vaut pour l'app, pas pour une capture | Promesse mal comprise | Le texte dit « visible pendant 24 h » (pas « effacée partout ») ; **limite à déclarer** dans les textes légaux (`release-manager`) |
| R6 | **Métadonnées** : une photo du système ou de la caméra peut contenir GPS, date, modèle | Fuite de position à tous les membres | **Ré-encodage depuis les pixels**, test d'octets (critère PR3) ; la localisation n'existe que dans le champ réservé (R-PHOTO-LOC) |
| R7 | **Localisation de la photo hors de la réciprocité** | Contradiction avec le texte de consentement | R-PHOTO-LOC exige que le lecteur ait **publié sa position** (`feed.md` §6.3) ; texte de consentement amendé (v3) ou v4 : **D20** |
| R8 | **Quota Spark** : écritures × N Miffs, transfert des photos | Application coupée pour tous au plafond | §11 ; plafonds ; mesures ; décision PM sur Blaze |
| R9 | **Publication fantôme** (écriture mise en file et envoyée des heures plus tard) | Fausse réassurance tardive | §5.7 : accusé du serveur, test en mode avion |
| R10 | **Permission caméra mal justifiée** devant les stores et les utilisateurs ; manifeste et déclarations qui disent « pas de caméra » | Rejet, perte de confiance | Écran d'explication avant la boîte ; `release-manager` met à jour les déclarations (Play : photos ; App Store : photos, `NSCameraUsageDescription`) **avant** la soumission ; caméra active **uniquement** écran visible, jamais déclenchée à distance |
| R11 | **Photos de comptes abandonnés** (suppression physique non garantie, C12, J8) | Écart entre promesse et réalité | Politique : « supprimée dès que possible » ; **alignement du texte** sur la couche auteur seule (C12) |
| R12 | **Aucun test iOS local** (pas de Mac) ; AVFoundation sans simulateur | Cycle lent, bugs de caméra tardifs | Android d'abord ; **mêmes cas de test** pour le codec (taille, métadonnées) ; TestFlight sur l'iPhone 11 Pro |
| R13 | **Caméras des surcouches constructeur** (ColorOS) : orientation, miroir, plantages de CameraX | Photos de travers, écran noir | Recette sur l'OPPO ; repli appareil photo du système (V10) |
| R14 | **Textes légaux non relus** (décision du 2026-10-23) | Risque assumé | Aucun texte ne prétend l'avoir été |

---

## 13. Critères d'acceptation (testables ; Android puis iOS sauf mention)

**Bouton central et couleur**
- [ ] **B1** Le bouton central est **actif** (builds de diffusion compris) dès qu'il y a une Miff ; il prend la couleur de mon statut (vert / orange / rouge / neutre) selon `StatusColor.level` **inchangée** ; tests unitaires de toutes les branches (dont `unknown` et partage arrêté = neutre) et des limites 0 h 59 / 1 h 00 / 2 h 00 / 2 h 01 (constantes lues dans `StatusThresholds`).
- [ ] **B2** Le nom parlé du bouton dit l'état (« …, tu es en orange ») ; la couleur n'est jamais la seule information ; contraste conforme au design system ; zone tactile ≥ 48 dp / 44 pt.
- [ ] **B3** Le recalcul se fait **chaque minute sans lecture serveur** (horloge simulée) ; **un seul écouteur** de mon statut (celui du Feed).

**Permission et caméra**
- [ ] **P1** La permission n'est demandée **qu'à l'ouverture de l'écran Update**, après l'écran d'explication, **jamais au démarrage** ; la caméra **ne s'active que si l'écran est visible** et s'arrête en arrière-plan, au verrouillage et à la fermeture (test de cycle de vie) ; **aucun déclenchement à distance** possible.
- [ ] **P2** Refus : états B et C du §5.2, ton calme, **aucun blocage** de l'app ; permission retirée en cours d'usage : le viseur se ferme sans plantage.
- [ ] **P3** Caméra indisponible (autre application, politique, aucune caméra) : état D, **aucun plantage**, aucun message technique ; sans caméra frontale : arrière, bascule masquée.
- [ ] **P4** Le manifeste **déclare `CAMERA` et rien d'autre de nouveau** (aucune permission de stockage ou de photothèque), avec caméra **non requise** ; `PermissionPrivacyTest` et la CI sont mis à jour et verts ; iOS : `NSCameraUsageDescription` présent, aucune clé de photothèque, `PrivacyInfo.xcprivacy` à jour.

**Viseur et photo**
- [ ] **V1** Frontale par défaut ; **deux touchers rapides hors des boutons** = bascule (et retour) ; **un bouton visible** équivalent ; l'obturateur ne se déclenche pas sur le double toucher et deux touchers sur le rond ne donnent **qu'une** photo.
- [ ] **V2** Photo prise → aperçu **sans aucun envoi réseau** ; **Recommencer** libère la photo (aucune écriture, aucune trace) ; aperçu = image publiée (pas de miroir).
- [ ] **V3** La photo publiée est un JPEG **≤ 150 000 octets réels**, portrait 3:4, côté long ≤ 1 024 px, produit par ré-encodage ; **test d'octets** : aucune trace de `Exif`, `GPS`, `xap`, modèle, date ni miniature (comme `AndroidAvatarCodecTest`) sur **dix photos types** ; si rien ne tient : message, **aucune écriture**.
- [ ] **V4** Aucun fichier de photo sur le disque avec le viseur intégré ; avec le repli du système : fichier dans le **cache privé**, supprimé dès la lecture ; **aucune écriture dans la galerie**, aucune permission de stockage.
- [ ] **V5** Photo en attente > 5 min : oubliée avec le message du §7 ; `RecentsPrivacy` actif sur l'écran.

**Publication**
- [ ] **U1** Publier écrit, **dans chaque Miff où mon partage est actif**, `photos/{moi}` (set) et `status/{moi}` (update : `lastUpdateAt`, `hasPhoto = true`, `updateText = null`, `batteryPct`, `deviceId`) ; `presence`, `placeId`, `presenceAt` **inchangés** ; `expiresAt` = maintenant + 23 h ; **vérifié à l'émulateur** avec les règles : lots de 5 Miffs au plus.
- [ ] **U2** **Écrasement** : deux publications successives = **un seul** document `photos/{moi}` par Miff, dont les octets sont ceux de la **seconde** (vérifié à l'émulateur).
- [ ] **U3** Hors connexion : **« Photo partagée » n'apparaît jamais sans accusé du serveur** ; en **mode avion**, rien n'est écrit plus tard (test : réactiver le réseau, vérifier l'absence d'écriture) ; la photo reste à l'écran ; message §7.
- [ ] **U4** Succès : retour au Feed, ma carte montre la photo **sans relecture serveur**, message calme sans son ni vibration ; échec d'un lot sur plusieurs (Premium) : état I et nouvelle tentative **sur les Miffs restantes seulement**.
- [ ] **U5** `deviceId` périmé, partage arrêté, retrait, Miff supprimée : messages neutres existants, aucun message d'erreur technique ; partage arrêté partout : état J **sans demande de permission**.
- [ ] **U6** Garde-fou : la 21e publication du jour et une publication moins de 30 s après la précédente sont refusées avec le message calme ; **aucun événement Analytics** ; aucun contenu, aucune coordonnée, aucun nom dans un journal ou un rapport de plantage ; `toString()` masqués.
- [ ] **U7** **Nettoyage** : une photo de plus de 24 h (date locale, ou `lastUpdateAt` > 25 h après réinstallation) est supprimée (`photos/{moi}` + `hasPhoto = false`) dans chaque Miff au lancement et au retour au premier plan, **sans lecture serveur** ; vérifié à l'émulateur.
- [ ] **U9** (D16) **Aucune publication sans photo** : « Publier » n'existe et n'est actif que sur l'aperçu d'une photo prise ; aucun écran, aucune action ni aucun code ne publie un update sans photo (pas de bouton « Je vais bien » en v1) ; chaque publication écrit **toujours** `photos/{moi}` **et** `hasPhoto = true` dans le même lot ; test unitaire : un état sans photo ne produit **aucune écriture**.
- [ ] **U8** L'Update **n'écrit jamais** `positions/{moi}` ni `presence`, `placeId`, `presenceAt`, et ne lit rien d'autre que ce que le Feed lit déjà.

**Messages d'incitation**
- [ ] **A1** (D17) Le message est un **bandeau en haut du Feed, au-dessus de la bulle**, qui apparaît quand **mon** niveau (Miff affichée) est orange ou rouge, avec le **titre unique « Un petit signe à tes proches ? »** et la phrase du cas (§7) ; **aucun** bouton de fermeture, aucun son, aucune vibration, aucun clignotement, aucune fenêtre bloquante.
- [ ] **A2** (D18) Il disparaît **dès que je ne suis plus orange ni rouge** (update **ou** arrivée dans une zone de confiance sans publication), et, en zone de danger, **dès qu'une update est publiée après mon entrée** (`lastUpdateAt ≥ presenceAt`) ; il ne disparaît **pas** avec le temps ni par un geste ; tests unitaires de ces cas, dont « orange puis arrivée à la maison sans publier ».
- [ ] **A5** (D19) La couleur du bouton central et le bandeau suivent **la Miff affichée dans le Feed** : changer de Miff dans le Feed les met à jour ; **aucun écouteur ni lecture de plus** ; test avec deux Miffs aux niveaux différents.
- [ ] **A3** Le bouton du message ouvre l'écran Update ; **aucun bouton** n'est ajouté à la page de bulle à la 2e personne.
- [ ] **A4** **Aucune notification** n'est émise ; le message n'existe que dans l'app ouverte.

**Localisation de la photo (UPDATE-1b, après R-PHOTO-LOC)**
- [ ] **L1** Une seule mesure à l'obturateur (3 s au plus, mesure de moins de 10 min) ; sans mesure, sans accord version 3 ou sans permission : photo **sans localisation, sans message d'erreur** ; « Recommencer » oublie la position ; **aucune coordonnée** dans un journal, un rapport de plantage, un événement d'analyse ou l'état sauvegardé.
- [ ] **L2** Les clés `lat` / `lng` ne sont envoyées **qu'après** le déploiement des règles ; un client sans localisation reste accepté.
- [ ] **L3** (D20) Avant de livrer UPDATE-1b : **vérifier qu'aucun testeur externe n'a accepté le texte v3** (alors le texte v3 est amendé pour citer la photo localisée) ; sinon le texte passe en **v4** avec re-consentement ; la position de la photo n'est jamais écrite sans un consentement qui la cite.

**Accessibilité, textes**
- [ ] **AC1** Obturateur, bascule, fermer, Recommencer, Publier : noms parlés du §7 ; **action d'accessibilité « Changer de caméra »** ; « Photo prise » annoncé ; ordre de focus logique ; texte maximal et droite-à-gauche sans chevauchement ; boutons ≥ 48 dp / 44 pt.
- [ ] **AC2** Une personne qui ne peut pas utiliser le viseur (lecteur d'écran, tremblement) dispose d'une **alternative** : bouton de bascule visible et **repli appareil photo du système**.
- [ ] **AC3** Animations (obturateur, transitions) supprimées si « réduire les animations ».
- [ ] **T1** Textes `update_` en FR et EN, clés **identiques** sur les deux plateformes (parité vérifiée par la CI), tutoiement, sans accord de genre, formats d'heure du téléphone ; préfixe ajouté dans `docs/contracts/i18n-keys.md` et le script de parité ; aucune valeur de design en dur.

**Quota**
- [ ] **QU1** Les **écritures, accès de règles et tailles réelles** (1 Miff et 5 Miffs) sont **mesurés et reportés dans la PR** et comparés au §11 ; aucune relecture périodique ; aucune relecture de ma propre photo.

**iOS (UPDATE-3 et UPDATE-4)**
- [ ] **I1** Mêmes critères, vérifiés par TestFlight sur l'iPhone 11 Pro ; mêmes cas de test du codec et de la couleur que l'Android (contrat) ; VoiceOver et Dynamic Type.

**Recette PM sur téléphone** (OPPO, puis iPhone ; deux comptes dans la même Miff) : ouvrir l'Update, refuser puis accepter la caméra ; selfie, double toucher, bouton de bascule ; recommencer ; publier et voir la photo chez l'autre compte (avec FEED-4) ; publier une seconde fois et vérifier que seule la seconde reste ; couper le réseau et vérifier qu'aucune publication ne part plus tard ; sortir d'un lieu, passer orange puis rouge (ou simuler) et voir le bouton et le message ; publier et voir le message disparaître ; changer la langue.

---

## 14. Dépendances

| Dépendance | État au 2026-11-05 |
|---|---|
| Règles Firestore de la photo (`validPhoto`), du statut, lots d'arrêt / départ | **Déployées** ; rien à changer pour UPDATE-1 |
| LOC-2b (`StatusColor.level`, statuts, positions) | **Fusionné** (PR #68) |
| MIFF-1 / MIFF-2, ONB-3a (barre d'onglets) | **Livrés** |
| **FEED-1** (carte « moi ») et **FEED-2** (écouteur de mon statut) | **Tickets validés ou en cours** (`feed.md`, PR #72) : FEED-2 bloque UPDATE-2 |
| **FEED-4** (affichage de la photo) | À faire après UPDATE-1 ; seul moyen de **voir** la photo |
| **SESS-1** (`deviceId` dans le statut) | Fusionné (PR #76) ; temps 2 des règles à ne passer qu'après livraison des apps |
| **R-PHOTO-LOC** | **En préparation** par `backend-data` ; bloque **seulement** UPDATE-1b |
| Texte de consentement (v3 amendé ou v4) citant la photo localisée | **Décidé (D20)** ; vérification « aucun testeur externe n'a accepté v3 » **à faire au moment de UPDATE-1b** ; bloque seulement UPDATE-1b |
| Déclarations des stores et textes légaux (caméra, photos, contenus, captures d'écran, suppression par l'auteur) | `release-manager` (§15) |
| CameraX (androidx.camera, licence Apache 2.0, gratuit) | À ajouter par `android-dev` (version publiée depuis plus de deux semaines) ; iOS : AVFoundation (système, rien à ajouter) |
| Export PNG des 4 images de Notion + maquettes complétées | **Le PM** (§15) |

---

## 15. Ce que le PM doit fournir ou faire

Rien de ceci n'est fait par un agent.
1. *(Questions Q1 à Q5 : toutes décidées le 2026-11-05, D16 à D20 ; rien à répondre.)*
2. **Exporter en PNG** les **4 images** du ticket Notion « Update » (les deux écrans, le message orange, le message rouge) dans `design/exports/update/` (règle du 2026-10-28) : **seul point encore ouvert**.
3. **Fournir les maquettes manquantes** (§16) ou valider celles de `design-system` dans « Miff - Claude ».
4. **Autoriser** la préparation puis le déploiement de **R-PHOTO-LOC** (« oui » explicite, `docs/dev/deploiement-regles.md`).
5. **Demander à `release-manager`** (notes, sans texte juridique) : déclarations Play et App Store pour la caméra et les photos, règles sur les contenus des utilisateurs (R4), âge et mineurs (J2, J31), alignement de la politique (photo supprimée « par l'app de son auteur », captures d'écran, texte facultatif non collecté)  ; texte de consentement : v3 amendé ou v4 (D20), **à trancher en fait au début de UPDATE-1b** après avoir vérifié qu'aucun testeur externe n'a accepté v3.
6. **Aligner la page Notion « Update »** sur les décisions ci-dessus (ses images d'alerte devront parler de « zone de danger », pas de « zone à risque », et ne plus être des notifications) ; compléter la page vide « Tabbar ».
7. Pour la recette : **deux appareils** (OPPO Find X5 Lite, iPhone 11 Pro via TestFlight) et deux comptes dans la même Miff.

---

## 16. Maquettes manquantes (liste pour `design-system`, dans « Miff - Claude » uniquement)

**Aucune maquette de l'Update n'est lisible aujourd'hui** : le PM exporte d'abord en PNG les 4 images de Notion (`design/exports/update/`) ; ce qui est ensuite à produire (en partant des deux écrans de Notion une fois exportés) :
1. **Barre d'onglets** avec le **bouton central** en vert, orange, rouge et neutre (le dessin du PM montre l'anneau jaune-vert), états normal et pressé.
2. **Écran d'explication de permission** et **états B, C, D** (caméra désactivée, indisponible).
3. **Viseur** : frontal et arrière, rond central (≥ 72 dp), bouton fermer, **bouton de bascule**, aide sous le viseur, astuce du double toucher ; **sans barre d'onglets**.
4. **Aperçu** : photo, **Recommencer**, **Publier**, ligne de visibilité (avec et sans lieu), message « photo qui date ».
5. **Publication** : en cours, succès (retour au Feed), hors connexion, succès partiel, partage arrêté, trop de photos.
6. **Messages d'incitation** : orange, rouge d'ancienneté, rouge de zone de danger (avec le bandeau placé au-dessus de la bulle du Feed), états avec et sans bouton.
7. **Ma carte** après publication (avec la photo) : à fournir avec FEED-4.
8. **Variante iOS** : aucune différence de dessin attendue ; confirmer que le même export sert aux deux plateformes.

---

## 17. Questions pour le PM : **toutes décidées le 2026-11-05**

Le PM a validé les recommandations de l'agent `product-specs` pour les cinq questions. Elles sont reprises au §3 (D16 à D20) ; les repères Q1 à Q5 sont gardés pour s'y retrouver. **Il n'y a plus de question ouverte.**

| Question | Décision | Réf. |
|---|---|---|
| **Q1.** Photo obligatoire, ou un « Je vais bien » sans photo ? | **Photo obligatoire en v1** ; pas de bouton « Je vais bien » ; un tel geste pourra s'ajouter plus tard sans changer les règles | D16 |
| **Q2.** Forme et texte des messages d'incitation | **Bandeau** en haut du Feed, non fermable, sans son ni vibration ; titre « Un petit signe à tes proches ? » et trois phrases (§7) | D17 |
| **Q3.** Disparition au retour au vert sans publication | **Oui**, le message disparaît aussi | D18 |
| **Q4.** Plusieurs Miffs : quelle Miff pour la couleur du bouton ? | **La Miff affichée dans le Feed** | D19 |
| **Q5.** Texte de consentement et photo localisée | **v3 amendé** si aucun testeur externe ne l'a accepté, sinon **v4** ; ne bloque que UPDATE-1b ; **vérification à faire au moment de UPDATE-1b** | D20 |

**Seul reste ouvert, sans décision à prendre** : l'**export PNG des 4 images** de l'Update dans Notion (§15, §16), à fournir par le PM.

---

*Ce document ne modifie ni le code, ni Notion, ni Figma, ni les règles Firestore, ni `backend/data-model.md`. Aucun texte légal n'est rédigé et aucun n'est présenté comme relu par un juriste. Le statut des tickets Notion n'est pas modifié ; le PM en garde la main.*
