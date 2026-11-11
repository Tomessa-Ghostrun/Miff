# Étude : petit serveur gratuit externe ou Blaze, pour les notifications aux proches et le premium réel

**Rédigé le 2026-11-11** par `backend-data`. **Documentation seulement** : aucune commande Firebase, aucun accès aux consoles, rien n'a été créé ni modifié sur `miff-3d283`, aucun code écrit.
**Question du PM** : « Si on prend l'option petit serveur gratuit externe (ex. Cloudflare Workers) plutôt que Blaze, est-ce que ça peut nous permettre d'implémenter les notifications qu'on n'a pas pu implémenter jusqu'à maintenant ? »
**Contact et autorité de décision** : Tomessa MFOUKA, tomessa.mfouka@gmail.com (PM). Seul le PM active un plan payant, saisit un moyen de paiement ou ouvre un compte chez un fournisseur.

**Convention** : **Vérifié** = page officielle lue le 2026-11-11 (lien en §10). **Non vérifié** = je n'ai pas pu le confirmer (page illisible, absente ou silencieuse) : à prototyper ou à lire avant de s'y fier. Aucun tarif n'est deviné : quand un chiffre manque, il est écrit « non vérifié ». Les montants sont en dollars américains, tels que publiés (aucune conversion en euros).

---

## 0. La réponse en bref (pour le PM)

1. **Oui pour une partie, non pour le reste.** Un petit serveur gratuit (Cloudflare Workers) peut envoyer des notifications aux proches **quand l'événement part d'un geste fait dans l'app ouverte** (exemple idéal : la demande d'ajout de lieu et sa réponse). Il est **déconseillé** pour tout ce qui dépend du **temps qui passe** (« plus de nouvelles ») et il est **à écarter** pour le **premium réel** : dans ces deux cas il faudrait confier à un tiers gratuit **une clé qui ouvre toute la base** (positions comprises), ce que les règles du projet interdisent de fait (`docs/dev/secrets.md` : « ne jamais créer de clé de compte de service »).
2. **Ça ne fait presque rien économiser.** À 100, 1 000 et 10 000 utilisateurs actifs par jour, Blaze coûte environ **0 $, 0,5 $ et 12 $ par mois** (§6). Le vrai plafond n'est pas le prix de Blaze : ce sont les **50 000 lectures par jour de Spark**, atteintes vers **500 à 650 utilisateurs actifs par jour**, ce qui oblige à passer en Blaze **de toute façon**. Le serveur externe n'achète donc que du temps (une bêta sans carte bancaire), pas une économie durable.
3. **Recommandation** : **Blaze avec garde-fous avant la bêta externe** (alertes de budget, plafond de dépense sur les fonctions, App Check) ; **serveur externe seulement si le PM refuse la carte bancaire**, et alors **limité aux notifications déclenchées depuis l'app ouverte**, premium toujours accordé à la main. Détail et décisions : §7 et §8.
4. **Précision de périmètre** : dans `docs/tickets/notifications.md` (§20), **N5 = « un membre entre dans une zone de danger » vers les autres**, et « plus de nouvelles » est **B1/B2** (phase P2). Je traite les deux. Le ticket « demande d'ajout de lieu » **n'est pas sur `origin/main`** ni sur une branche distante (recherche faite) : j'ai donc travaillé à partir de la description du PM (le créateur est prévenu d'une demande ; le demandeur est prévenu de la réponse) ; le schéma de la demande reste à définir.

---

## 1. Ce qui est reporté faute de serveur, et ce qu'il faut pour le débloquer

| Besoin | Pourquoi le téléphone seul ne suffit pas | Ce qu'il faut |
|---|---|---|
| **N5** : un membre entre dans une zone de danger, les autres sont prévenus (`notifications.md` §20.6, abandonnée « tant que Blaze / un relais n'est pas décidé ») | L'événement naît sur le téléphone d'un membre ; il faut réveiller **d'autres** téléphones | Un expéditeur FCM détenant une identité Google |
| **B1 / B2** : « pas de nouvelles de X depuis 13 h 40 » puis clôture (`notifications.md` §5.2, P2) | Il faut **agir à une heure donnée**, y compris si le téléphone de X est mort ou l'app fermée | Un expéditeur FCM **et** un déclencheur temporel |
| **B3** : arrivée dans un lieu de confiance (P2) | Même chose que N5 | Expéditeur FCM |
| **Demande d'ajout de lieu** → push au créateur ; **réponse** → push au demandeur | Un autre téléphone doit être réveillé ; aujourd'hui seul le créateur crée les lieux (`lieux-de-confiance.md`) | Expéditeur FCM |
| **Premium réel** (Play Billing, StoreKit) | `entitlements/{uid}` est interdit en écriture au client (`backend/firestore.rules`, `docs/dev/premium-manuel.md`) ; il faut **prouver** l'achat auprès du store puis écrire le document | Un serveur qui parle aux API des stores **et** a le droit d'écrire `entitlements` |

Point de vocabulaire (correction à reporter dans `backend/data-model.md` §12, ligne FCM) : « l'envoi de push exige un serveur (donc Blaze) » est inexact. Ce qui exige Blaze, c'est **Cloud Functions** ; un serveur ailleurs peut envoyer des push sans que Firebase passe en Blaze (FCM est « sans coût », [tarifs Firebase](https://firebase.google.com/pricing), **Vérifié**). Ce que ce serveur ailleurs coûterait en sécurité est l'objet de cette étude.

---

## 2. Les trois options

### Option A : Blaze + Cloud Functions (Firebase)
- **Déclencheurs Firestore** : une fonction se lance quand un document est écrit (`onDocumentWritten`, etc.). Livraison **au moins une fois** (une même écriture peut lancer la fonction plusieurs fois) et **ordre non garanti** : les fonctions doivent être **idempotentes** ([doc Firebase](https://firebase.google.com/docs/functions/firestore-events), **Vérifié**).
- **Fonction planifiée** (« toutes les N minutes ») pour le silence : s'appuie sur Cloud Scheduler, **3 tâches gratuites par compte de facturation et par mois**, puis 0,10 $ par tâche et par mois ([tarifs Scheduler](https://cloud.google.com/scheduler/pricing), **Vérifié**). Ce compteur est **au niveau du compte de facturation**, pas du projet (attention si le compte Google de Ghostrun partage le même compte de facturation).
- **Aucune clé à sortir de Google** : la fonction s'exécute avec l'identité de service du projet (documenté pour l'envoi FCM : « Application Default Credentials … Cloud Functions », [guide FCM v1](https://firebase.google.com/docs/cloud-messaging/send/v1-api), **Vérifié**).
- **Région** : `europe-west9` (Paris) est acceptée pour Cloud Functions **2e génération** seulement ([régions](https://firebase.google.com/docs/functions/locations), **Vérifié**).
- **Validation des achats** : fonction qui interroge l'API Google Play (`purchases.subscriptionsv2.get`, portée `androidpublisher`) et l'API / les notifications serveur d'Apple, puis écrit `entitlements/{uid}`. Détail en §3.4.
- **Prix** : 2 M invocations gratuites par mois puis 0,40 $ le million ; Cloud Build 120 min par jour gratuites ; **non disponible sur Spark** ([tarifs Firebase](https://firebase.google.com/pricing), **Vérifié**). Carte bancaire requise (action du PM).

### Option B : petit serveur gratuit externe (Cloudflare Workers + Cron Triggers)
Un Worker (petit programme hébergé par Cloudflare) reçoit des appels, parle à l'API REST de Firestore et à l'API FCM HTTP v1.

**Comment authentifier les appels des clients**
1. Le client envoie son **jeton d'identification Firebase** (`Authorization: Bearer …`) et son **jeton App Check** (`X-Firebase-AppCheck`).
2. Le Worker vérifie le jeton d'identification à la main : algorithme RS256, `kid` présent dans les clés publiques publiées par Google, puis `exp`, `iat`, `aud` = `miff-3d283`, `iss` = `https://securetoken.google.com/miff-3d283`, `sub` non vide, `auth_time` dans le passé ; clés à mettre en cache selon `Cache-Control` ([vérifier un jeton](https://firebase.google.com/docs/auth/admin/verify-id-tokens), **Vérifié**). La vérification par bibliothèque tierce **ne contrôle pas la révocation** (la page ne la mentionne que pour le SDK Admin) : un compte supprimé ou désactivé peut rester accepté jusqu'à l'expiration du jeton. La garde réelle est donc l'étape 3.
3. Le Worker **relit Firestore avec le jeton du client** : « Firebase ID token … Cloud Firestore uses Cloud Firestore Security Rules » ([API REST](https://firebase.google.com/docs/firestore/use-rest-api), **Vérifié**). Les règles existantes (appartenance à la Miff, session de l'appareil, réciprocité) décident donc, et le Worker n'a **aucun droit propre**. Le coût de ces lectures est le même que pour une lecture du client.
4. Le jeton App Check se vérifie à la main avec les clés publiques `firebaseappcheck.googleapis.com/v1/jwks` (signature, RS256, émetteur `…/<numéro de projet>`, expiration, audience `projects/<numéro>`). **La protection contre le rejeu est limitée aux SDK Node.js et Python d'administration** ([doc App Check](https://firebase.google.com/docs/app-check/custom-resource-backend), **Vérifié**) : elle n'existe pas dans un Worker.
5. **Non vérifié** : que Firestore accepte une requête REST **qui porte à la fois** le jeton d'identification et un en-tête App Check une fois l'application stricte activée. Si le Worker ne peut pas transmettre le jeton App Check à Firestore, l'étape 3 casse le jour de l'application stricte. **À prototyper avant de choisir B.**

**Comment déclencher**
- **Appel du client après son écriture** (adapté aux gestes faits **app ouverte**).
- **Cron Trigger** (UTC, 5 déclencheurs maximum en gratuit, 10 ms de CPU par exécution, jusqu'à 15 minutes pour qu'un changement de planification se propage ; la page ne donne **aucune garantie de ponctualité** ni de rattrapage des exécutions manquées) ([limites](https://developers.cloudflare.com/workers/platform/limits/), [Cron Triggers](https://developers.cloudflare.com/workers/configuration/cron-triggers/), **Vérifié**).
- **Webhooks** des stores ou de RevenueCat vers le Worker (§3.4).

**Où sont les jetons d'appareil des destinataires ?** Trois possibilités, par ordre de risque croissant :
- **B-sans-clé-Firestore** : chaque membre publie son jeton dans sa fiche `members/{uid}` (lisible par les membres de la Miff). Le Worker la lit **avec le jeton de l'appelant**. **Aucun droit Firestore pour le Worker.** Contrepartie : le jeton d'appareil devient visible de tous les membres de la Miff (il ne permet pas d'envoyer sans identifiants d'envoi, mais c'est un identifiant technique de plus exposé : arbitrage de minimisation, et changement du schéma `users/{uid}/devices`, `data-model.md` §2.10).
- **B-avec-lecture** : un compte de service avec le rôle de lecture Firestore. **Il lit tout, y compris les positions** (rôle `datastore.viewer` : « read access to all Firestore resources », [IAM Firestore](https://docs.cloud.google.com/firestore/native/docs/security/iam), **Vérifié**).
- **B-avec-écriture** : rôle `datastore.user` (lecture et écriture) ; **les requêtes d'un compte de service ignorent les règles de sécurité** (« allows these requests to ignore your security rules », [API REST](https://firebase.google.com/docs/firestore/use-rest-api), **Vérifié**).

L'envoi FCM exige un jeton OAuth2 d'un compte de service de portée `firebase.messaging` ([guide FCM v1](https://firebase.google.com/docs/cloud-messaging/send/v1-api), **Vérifié**) : **toute variante de B détient au moins une clé de compte de service dans un secret Cloudflare**. Un compte **dédié à l'envoi FCM seul** limite les dégâts d'une fuite à l'envoi de messages vers des jetons déjà connus (rôle « Firebase Cloud Messaging API Admin » cité par la même page, **Vérifié** ; la liste exacte des permissions minimales : non vérifiée).

**Autres hébergeurs gratuits regardés** : **Vercel Hobby : exclu**, « restricted to non-commercial personal use only » ([règles d'usage Vercel](https://vercel.com/docs/limits/fair-use-guidelines), **Vérifié**). **Supabase gratuit** : 500 000 invocations de fonctions, 2 projets actifs, **projet mis en pause après 1 semaine d'inactivité** ([tarifs Supabase](https://supabase.com/pricing), **Vérifié**) : ajoute un second fournisseur de base de données pour rien, non retenu. **GitHub Actions planifiés : écartés** par `notifications.md` §4.3. Deno Deploy, Netlify, Cloud Run, autres : **non étudiés**.

### Option C : rester sans serveur (Spark)
Ce qui marche aujourd'hui : rappels locaux « à soi » (`notifications.md` §20), Feed en direct app ouverte, premium accordé à la main (`docs/dev/premium-manuel.md`). Ce qui n'existera pas : tout ce qui est du §1. **Coût : 0 €, risque de dépense : nul** (Spark s'arrête au lieu de facturer).

---

## 3. Faisabilité fonction par fonction avec l'option B

Légende : **Oui** = faisable avec une sécurité équivalente ; **Sous conditions** ; **Déconseillé** = faisable mais la sécurité ou la fiabilité se dégrade ; **Non**.

| Fonction | Verdict B | Pourquoi en une phrase | Verdict A |
|---|---|---|---|
| **Push demande d'ajout de lieu** (au créateur) | **Oui** | Geste fait app ouverte, un seul destinataire, volume minuscule | Oui (déclencheur sur la création de la demande) |
| **Push réponse** (au demandeur) | **Oui** | Idem, déclenché par le créateur app ouverte | Oui |
| **N5** zone de danger → les autres | **Sous conditions** | Événement de géofence en arrière-plan : l'appel au Worker doit tenir dans la fenêtre de réveil (Android : budget 8,5 s ; iOS : une dizaine de secondes, `notifications.md` §20) | Oui, sans étape client supplémentaire |
| **B3** arrivée dans un lieu | **Sous conditions** | Même contrainte que N5, volume plus élevé | Oui |
| **B1 / B2** « plus de nouvelles » et clôture | **Déconseillé** | Il faut un déclencheur temporel et vérifier l'état à l'échéance (§3.3) | Oui (fonction planifiée) |
| **Premium réel** | **Non** (déconseillé fortement) | Écrire `entitlements` exige une clé à droit d'écriture total, chez un tiers gratuit (§3.4) | Oui |

### 3.1 Demande de lieu et réponse (la meilleure candidate pour B)
1. Le membre écrit sa demande dans Firestore (règles à définir par le ticket de la demande), puis son app appelle le Worker.
2. Le Worker vérifie les jetons, **relit la demande avec le jeton du demandeur** (les règles prouvent qu'il est membre et auteur), **compose lui-même le texte** (jamais de texte fourni par l'appelant) et envoie un push au créateur via le jeton publié dans sa fiche (variante B-sans-clé-Firestore).
3. Réponse : même schéma dans l'autre sens, avec le jeton du créateur.
- **Fiabilité** : l'app est au premier plan, donc l'appel réseau est fiable ; en cas d'échec, la demande reste lisible dans l'app (badge au prochain lancement) : le push n'est qu'un accélérateur. **Il faut le prévoir ainsi.**
- **Latence** : un appel client puis un envoi FCM ; **je n'ai pas de chiffre vérifié** (à mesurer au prototype).

### 3.2 N5 et B3 (événements de géofence)
- Le Worker doit **croire un événement** sans le voir : il vérifie dans Firestore (avec le jeton de l'appelant) que le statut vient bien d'être écrit (`presenceAt` récent, `presence = in_risk`), refuse les événements anciens (pas de push tardif et trompeur) et ne notifie **qu'une fois par version de statut**. Cette **déduplication demande un état** : soit une écriture Firestore (droit d'écriture à confier), soit un stockage Cloudflare. Le **KV gratuit n'offre que 1 000 écritures par jour** ([KV](https://developers.cloudflare.com/kv/platform/pricing/), **Vérifié**) : insuffisant ; les **Durable Objects (SQLite) gratuits** offrent 100 000 écritures de lignes et 100 000 requêtes par jour ([DO](https://developers.cloudflare.com/durable-objects/platform/pricing/), **Vérifié**) : suffisant en bêta.
- Un membre malveillant ne peut dire que des événements **réels** (vérifiés dans ses propres écritures), mais peut rappeler le Worker en boucle : limitation de débit nécessaire. Le **binding de limitation de débit** de Cloudflare est **approximatif et local à chaque site** ; pour une limite stricte la doc renvoie aux Durable Objects ([limitation de débit](https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/), **Vérifié**). Disponibilité en offre gratuite : **non vérifiée**.
- **Fiabilité** : un appel client de plus **dans la fenêtre de réveil de la géofence**, au moment où l'app peut être tuée par la surcouche du constructeur. Perte silencieuse possible, **sans filet** (contrairement à §3.1). Avec A, la notification part de l'écriture elle-même.
- Taille de fan-out : l'API FCM v1 envoie **un message par requête** (**Vérifié** pour la forme d'une requête ; l'absence de lot : connaissance générale, **non vérifiée**). Miff de 15 membres au plus (`docs/tickets/miff.md` §5.8) = 14 envois, **sous la limite de 50 sous-requêtes par exécution gratuite** ([limites](https://developers.cloudflare.com/workers/platform/limits/), **Vérifié**), mais avec peu de marge si le Worker fait aussi 5 à 8 lectures.
- **10 ms de CPU par requête** (l'attente réseau ne compte pas) : signature RSA du jeton OAuth et vérification de deux jetons par appel **à mesurer** ; **non vérifié**.

### 3.3 « Plus de nouvelles » (B1 / B2) : pourquoi je le déconseille avec B
Le serveur doit **trouver les statuts échus**. Trois façons, aucune satisfaisante :
- **Cron + lecture de tous les statuts** : exige un compte de service qui lit **tout Firestore** (positions comprises) et le stocke chez un tiers sans engagement de service. **Contraire au principe du projet** (la sécurité n'est jamais négociable pour économiser, `CLAUDE.md`). Conclusion déjà posée par `notifications.md` §4.3 (b2).
- **Minuteur armé par l'appareil de l'auteur** (appel au Worker au départ du lieu, stocké dans un Durable Object) : l'annulation après une update dépend d'un **appel réseau de plus**, donc **fausses alertes** si cet appel échoue (« Thomas n'a pas donné de nouvelles » alors qu'il l'a fait) ; et il faut re-vérifier l'état à l'échéance, ce qui ramène au droit de lecture. De plus le Worker doit alors tenir **sa propre copie** des destinataires et des jetons : **un second système parallèle à maintenir et à tenir à jour** (membre sorti = doit cesser de recevoir).
- **Limites gratuites** : un cron qui trouve plusieurs alertes dues dépasse vite les **50 sous-requêtes par exécution** (14 envois par alerte).
**Ton et produit** : `notifications.md` §5.4 note qu'avec les seuils 2 h / 4 h une journée de travail peut produire un rouge **tous les jours** ; une fausse alerte ou une alerte quotidienne envoyée à **tout le cercle** est bien plus coûteuse en confiance qu'un rappel « à soi ». À trancher côté produit **avant** de bâtir B1, quel que soit le serveur (Q3 à Q5 de `notifications.md`, suspendues).

### 3.4 Premium réel avec B
- **Google Play** : l'API `purchases.subscriptionsv2.get` (portée `androidpublisher`) donne l'état et l'échéance (`lineItems.expiryTime`) ([référence](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2/get), **Vérifié**) ; elle suppose un compte de service invité dans la Play Console avec les droits « View financial data… » et « Manage orders and subscriptions » ([démarrage](https://developers.google.com/android-publisher/getting_started), **Vérifié**). Les notifications en temps réel passent par **Cloud Pub/Sub** et ne disent **que** « l'état a changé » : il faut rappeler l'API ([RTDN](https://developer.android.com/google/play/billing/rtdn-reference), **Vérifié**). **Non vérifié** : qu'un sujet Pub/Sub puisse être créé sans facturation activée (le quota gratuit de 10 GiB par mois existe, [tarifs Pub/Sub](https://cloud.google.com/pubsub/pricing), mais la page ne tranche pas).
- **Apple** : notifications serveur V2 (JWS signé), API App Store Server, clé `.p8` dédiée : **les pages Apple ne se chargent pas** (rendu JavaScript) ; tout ce qui touche Apple dans cette étude est **non vérifié** et de connaissance générale.
- **RevenueCat** (service tiers qui valide les achats des deux stores et envoie des webhooks) : gratuit jusqu'à **2 500 $ de revenu suivi par mois**, puis 1 % ([tarifs](https://www.revenuecat.com/pricing/), **Vérifié**) ; un webhook vers un Worker reste à écrire. Cela ne supprime pas le point dur : **quelqu'un doit écrire `entitlements/{uid}`**.
- **Le point dur** : le Worker doit écrire un document que les règles interdisent à tout client. Cela exige soit `datastore.user` (écriture sur **toute** la base, règles ignorées), soit des « custom claims » d'authentification (un droit d'administration des comptes, pire : usurpation de n'importe quel utilisateur ; **non vérifié**, raisonnement de l'agent). Une fuite de cette clé = lecture et falsification de toutes les données.
- Pistes de durcissement **non vérifiées** : isoler `entitlements` dans une **base Firestore nommée** et restreindre la clé par une condition IAM (la page IAM dit que les conditions se configurent « for access to one or more databases » ; la granularité par collection n'est pas mentionnée ; le quota gratuit d'une base nommée n'est pas précisé par la page [bases multiples](https://firebase.google.com/docs/firestore/manage-databases)). À prototyper avant toute décision.
- **Conclusion** : garder le premium **accordé à la main** pendant la bêta (déjà fonctionnel, 0 €), et construire le premium réel **sur Blaze** (§7).

---

## 4. Fiabilité des notifications (valable pour A et B, c'est le canal qui limite)

| Sujet | Ce qui est établi | Conséquence pour Miff |
|---|---|---|
| **Android, app force-arrêtée** | Messages **abandonnés** si l'app est « force-stopped » au moment de la livraison, avec échec des nouvelles tentatives ; selon l'article, peu de marge de manœuvre côté développeur ([blog Firebase](https://firebase.blog/posts/2024/07/understand-fcm-delivery-rates/), **Vérifié**) | Une personne qui a « forcé l'arrêt » de Miff ne reçoit rien jusqu'à la réouverture. À écrire dans l'aide, sans culpabiliser |
| **Android, priorité** | Priorité normale : possible retard en Doze ; haute : tente de livrer tout de suite ([priorité Android](https://firebase.google.com/docs/cloud-messaging/android-message-priority), **Vérifié**) | Utiliser la **haute priorité** pour les notifications visibles |
| **Android, hors ligne et file d'attente** | Au plus 100 messages en attente par instance ; seul le dernier message d'une même clé de regroupement est conservé ; l'article recommande les messages regroupables et une durée de vie adaptée ([blog Firebase](https://firebase.blog/posts/2024/07/understand-fcm-delivery-rates/), **Vérifié**) | Regrouper B1/B2 (même identifiant) : c'est déjà le principe « jamais d'alerte sans clôture » |
| **OPPO / ColorOS** | La seule donnée trouvée (ancien modèle F1S) : services d'arrière-plan arrêtés à l'extinction de l'écran, « No known solution on the dev end » ([dontkillmyapp](https://dontkillmyapp.com/oppo), **Vérifié, limité**). Elle ne parle **pas** des push FCM | **Non vérifié** pour l'OPPO Find X5 Lite (Android 13) du PM : **à mesurer en bêta** sur l'appareil, push app tuée par balayage puis après « forcer l'arrêt » |
| **Limites FCM** | 600 000 messages par minute et par projet (messages, pas requêtes) ; par appareil Android : 240 par minute et 5 000 par heure ([quotas FCM](https://firebase.google.com/docs/cloud-messaging/throttling-and-quotas), **Vérifié**) | Très loin de nos volumes |
| **iOS, clé APNs** | Une **clé d'authentification `.p8`** s'importe dans la console Firebase (Paramètres > Cloud Messaging), en développement, en production ou les deux ; la page ne dit pas si une clé sert aux deux environnements, ni son expiration ([APNs](https://firebase.google.com/docs/cloud-messaging/ios/certs), **Vérifié**) | Action du PM sur son compte Apple Developer. **Non vérifié** : le rôle Apple requis ; TestFlight et App Store utilisent l'environnement de **production** (cohérent avec `docs/dev/app-check.md` §3, source non officielle) |
| **iOS, comportement des push** | Comportement des notifications « alerte » quand l'app est tuée ; limitation des notifications silencieuses ; capacité « Push Notifications » à ajouter à l'identifiant et au profil de signature | **Non vérifié** (pages Apple illisibles). Il faut des notifications **« alerte »** et non silencieuses ; la capacité Push impacte la signature en CI (`docs/dev/apple-certificats-ci.md`) : un ticket à part |
| **Aucune garantie** | « Our goal is to always deliver every message » : c'est un objectif, pas un engagement ([quotas FCM](https://firebase.google.com/docs/cloud-messaging/throttling-and-quotas), **Vérifié**) | Le push est un **accélérateur** : l'état exact reste lisible dans le Feed. Ne jamais promettre « tu seras toujours prévenu » dans l'app ni dans les fiches |
| **Topics FCM (abonnement par groupe)** | Un client peut s'abonner à **n'importe quel** sujet existant ; la page ne décrit aucun contrôle d'accès ([sujets](https://firebase.google.com/docs/cloud-messaging/manage-topics), **Vérifié**) | **Écartés** : un ancien membre qui garde son abonnement continuerait de recevoir « X est à l'école ». L'envoi par jeton, avec contrôle de l'appartenance au moment de l'envoi, est le seul modèle acceptable |

Ces limites sont **les mêmes** avec A et B. Ce qui change, c'est **ce qui déclenche l'envoi** : une écriture Firestore côté serveur (A) contre un appel supplémentaire du téléphone (B).

---

## 5. Sécurité, conformité, dépendance

| Critère | A : Blaze + Functions | B : Cloudflare Workers |
|---|---|---|
| **Clé de service** | Aucune clé : identité du service gérée par Google | **Au moins une clé JSON** (envoi FCM) dans un secret Cloudflare ; B-avec-lecture/écriture : clé à portée totale. **Contredit la règle actuelle de `docs/dev/secrets.md`** (à amender par décision du PM si B est retenu) |
| **Stockage de la clé** | — | Jamais dans le dépôt (le dépôt est **public**, `secrets.md`) : secret du Worker (non relisible), compte Cloudflare avec double authentification. Les détails des secrets Workers : **non lus** |
| **Rotation** | Sans objet | Créer une nouvelle clé, remplacer le secret, supprimer l'ancienne ; rappel calendaire (la périodicité est une suggestion de l'agent, pas une règle vérifiée) |
| **Abus et coût** | App Check strict sur Firestore ; fonctions appelables exigeant App Check ; limites d'instances ; alertes de budget | App Check **à reconstruire à la main** (§2, sans protection contre le rejeu) ; limitation de débit approximative ; plafond gratuit de 100 000 requêtes/jour, au-delà « Error 1027 » ([limites](https://developers.cloudflare.com/workers/platform/limits/), **Vérifié**) : un abus provoque **un arrêt du service, pas une facture** |
| **RGPD, hébergeur et région** | Fonctions en **europe-west9** (Paris), **Vérifié**. FCM et les services de notification d'Apple restent des sous-traitants hors UE possibles dans tous les cas | Cloudflare devient un **sous-traitant de plus** : il voit prénoms, libellés de lieux (y compris le **nom saisi** pour Loisir et Autre, décision Q9), jetons, identifiants. Il invoque le cadre « Data Privacy Framework » et les clauses contractuelles types pour les transferts vers les États-Unis (page RGPD de Cloudflare, lue via un résumé de recherche : **à relire**). Où le code s'exécute : **non vérifié** ; les options de limitation régionale seraient réservées aux offres entreprise d'après une source tierce, **non vérifié** |
| **CGU du fournisseur** | Offre prévue du service | Le tiers gratuit peut être modifié ou arrêté « in our sole discretion », sans responsabilité ([conditions Cloudflare](https://www.cloudflare.com/terms/), §2.6 et §8, **Vérifié**) ; la plateforme peut limiter ou suspendre en cas de violation présumée ([conditions Developer Platform](https://www.cloudflare.com/service-specific-terms-developer-platform/), **Vérifié**). **Aucune restriction d'usage commercial** de l'offre gratuite trouvée dans ces deux pages ; l'inscription demande-t-elle une carte : **non vérifié** |
| **Textes légaux** | À mettre à jour : la politique actuelle dit que Miff « ne préviendra pas tes proches par notification » et « n'enregistre aucun jeton » (`docs/legal/politique-confidentialite.fr.md` lignes 68 et 144) ; fiches Data safety et étiquettes Apple à reprendre | Idem **plus** Cloudflare dans le tableau des sous-traitants. Rédaction par les agents concernés, sous la responsabilité du PM, **sans relecture juridique** (risque assumé, décision du 2026-10-23) |
| **Dépendance, « bus factor »** | Un seul fournisseur (Google), déjà au cœur du projet | Un **second fournisseur** et un second langage de déploiement (compte, CLI, secrets, journaux) ; sans engagement de service pour l'offre gratuite |
| **Maintenance par un PM non développeur** | Même console et même CLI que le reste du projet ; budgets et alertes dans la console | Compte et tableau de bord supplémentaires à surveiller ; incident = deux consoles à consulter |
| **Montée en charge** | Progressive : on ne paie que le dépassement des quotas conservés | Gratuit jusqu'à 100 000 requêtes/jour (≈ 14 000 à 20 000 utilisateurs actifs au rythme du §6) ; puis offre payante **5 $ par mois minimum**, 10 M requêtes incluses, 0,30 $ par million en plus ([tarifs Workers](https://developers.cloudflare.com/workers/platform/pricing/), **Vérifié**) |

**Garde-fous de dépense en A (à connaître, pas à surestimer)** : un budget Google Cloud **ne plafonne pas** la dépense, il alerte, avec des données retardées de plusieurs heures ([budgets](https://docs.cloud.google.com/billing/docs/how-to/budgets), **Vérifié**). Il existe des **plafonds de dépense** (« spend caps ») pour **Cloud Functions pour Firebase**, mais **pas pour Firestore** ; ils sont en **Preview**, sans SLA ; l'application peut être retardée de plusieurs minutes (les dépassements sont facturés) et les apps clientes reçoivent des erreurs ([plafonds Firebase](https://firebase.google.com/docs/projects/billing/spend-caps), **Vérifié**). Le poste le plus exposé à un abus reste donc **Firestore** (lectures), protégé par App Check strict et par la surveillance de `docs/dev/surveillance-quotas.md`. **Risque résiduel à assumer par écrit** : une dépense non plafonnée reste possible entre deux alertes.

---

## 6. Coûts chiffrés par scénario

**Hypothèses (aucune n'est une mesure ; reprises de `notifications.md` §4.5 et `data-model.md` §12)** : groupes de 4 membres (3 destinataires) ; **5 événements par utilisateur et par jour** qui déclenchent un envoi (hypothèse haute : départs, arrivées, update) ; chaque événement coûte **8 lectures Firestore** (membres + jetons) ; l'app consomme **60 lectures** et **7 écritures** par utilisateur actif et par jour sans notifications ; une exécution planifiée toutes les 10 minutes (≈ 4 300 par mois) ; la validation des achats est négligeable. **Prix Firestore Standard, Paris (europe-west9)** : lectures **0,036 $** pour 100 000, écritures **0,107 $**, suppressions 0,012 $, quotas gratuits 50 000 lectures et 20 000 écritures par jour, **conservés sous Blaze** ([tarifs Firestore](https://cloud.google.com/firestore/pricing), [tarifs Firebase](https://firebase.google.com/pricing), **Vérifié** ; un second tableau Paris de la même page affiche 0,035 / 0,104 : je n'ai pas pu déterminer lequel s'applique à l'édition Standard, l'écart est négligeable). Cela corrige à la baisse les valeurs prudentes de `notifications.md` §4.5 (0,06 / 0,18).

| Utilisateurs actifs par jour | 100 | 1 000 | 10 000 |
|---|---|---|---|
| Lectures Firestore par jour (60 + 40 liées aux notifications) | 10 000 | 100 000 | 1 000 000 |
| Écritures par jour (≈ 7 par utilisateur) | 700 | 7 000 | 70 000 |
| Invocations de fonctions par mois (événements + planifiée) | ≈ 19 000 | ≈ 154 000 | ≈ 1 504 000 |
| **A : coût des lectures** | 0 $ | (100 000 − 50 000) × 30 = 1,5 M → **≈ 0,54 $** | (1 000 000 − 50 000) × 30 = 28,5 M → **≈ 10,3 $** |
| **A : coût des écritures** | 0 $ | 0 $ (sous 20 000) | (70 000 − 20 000) × 30 = 1,5 M → **≈ 1,6 $** |
| **A : fonctions** (2 M gratuites) | 0 $ | 0 $ | 0 $ (≈ 75 % du gratuit) |
| **A : planificateur** (3 tâches gratuites) | 0 $ | 0 $ | 0 $ |
| **A : total** | **0 $** | **≈ 0,5 $** | **≈ 12 $** (dont ≈ 4 $ attribuables aux notifications) |
| **B : Cloudflare** (requêtes/jour ≈ 5 par utilisateur + cron ; gratuit jusqu'à 100 000) | 0 $ (≈ 700) | 0 $ (≈ 6 500) | 0 $ (≈ 50 000 à 65 000, **50 à 65 % du plafond**) |
| **B : Firestore** | identique à A | identique à A | identique à A |
| **B : le projet peut-il rester sur Spark ?** | **Oui** (10 000 lectures/jour) | **Non** : 100 000 > 50 000, l'app s'arrête pour tout le monde | **Non** |
| **B : total** | **0 $** | **≈ 0,5 $, et Blaze nécessaire de toute façon** | **≈ 12 $ + Blaze nécessaire** |

**Sensibilité** : si les lectures par utilisateur sont **deux fois plus fortes**, A passe à ≈ 1,6 $ (1 000) et ≈ 25 $ (10 000, lectures et écritures doublées). Le coût dominant est la lecture de l'app elle-même, **pas** les notifications.
**Autres lignes de Blaze, non vérifiées** : stockage des images de fonctions, Secret Manager, journaux : « centimes » selon la prudence de `notifications.md`, sans chiffre confirmé ici. Cloud Build : 120 min/jour gratuites (**Vérifié**).
**Seuils de bascule** (le premier atteint) :
1. **~500 utilisateurs actifs par jour** avec notifications (≈ 650 sans) : quota Spark de lectures (`data-model.md` §12 ; signal d'alerte : plus de 30 000 lectures par jour de façon répétée).
2. **Premier besoin de « plus de nouvelles » fiable** (B1/B2) ou de **premium réel** : Blaze, quelle que soit la taille.
3. **Environ 14 000 à 20 000 utilisateurs actifs par jour** : Cloudflare gratuit proche de 100 000 requêtes/jour (offre payante à partir de 5 $ par mois).
4. **Facture Blaze supérieure à 5 $ par mois** (suggestion, à fixer par le PM) : revue de l'usage avant tout autre choix.

---

## 7. Recommandation

### Bêta externe (testeurs invités, de quelques dizaines à ~300 utilisateurs actifs par jour)
**Recommandé : A, Blaze avec garde-fous**, parce que :
- le **coût estimé est de 0 à 0,5 $ par mois** (§6) ;
- c'est la **seule option qui livre « plus de nouvelles »** (la promesse centrale, `data-model.md` §13) sans clé qui ouvre la base ;
- aucun secret à garder, une seule console, et l'envoi part de l'écriture elle-même (pas d'étape de plus dans la fenêtre de réveil d'une géofence).
Garde-fous : alertes de budget (suggestion : 5 $ par mois, alertes à 50 / 90 / 100 %), **plafond de dépense Preview sur Cloud Functions**, App Check strict sur Firestore, revue quotidienne de `surveillance-quotas.md`, limitation du nombre d'instances. **Risque à écrire** : Firestore n'est pas couvert par un plafond.

**Si le PM refuse la carte bancaire : B restreint** (rien d'autre n'est acceptable) :
- Périmètre : demande de lieu et réponse (§3.1) ; N5 et arrivées en **best effort** annoncé comme tel ; **jetons dans la fiche membre** (variante sans clé Firestore, arbitrage de minimisation à valider) ; clé de service **limitée à l'envoi FCM** ; aucune lecture ni écriture Firestore par le Worker.
- Hors périmètre, restent **non livrés** : « plus de nouvelles » (B1/B2), premium réel.
- Prérequis : **prototype** qui lève les points non vérifiés (App Check transmis à Firestore, CPU de 10 ms, limitation de débit) avant tout ticket ; amendement de `secrets.md` ; ajout de Cloudflare aux textes légaux.
- Coût de ce détour : **le Worker est à jeter** le jour du passage à Blaze (la logique se porte, le déclencheur change) ; c'est le prix de 1 à 3 mois sans carte.

### Lancement (stores)
**A est obligatoire** : les lectures de l'app dépasseront Spark (§6), le premium réel exige une écriture protégée de `entitlements`, et « plus de nouvelles » ne se livre pas proprement autrement. B ne se justifie plus.

### Premium réel
Pendant la bêta : **toujours accordé à la main** (`premium-manuel.md`) ; aucun paywall d'achat n'est nécessaire à des testeurs. **Avant la première soumission avec achat intégré** : fonction de validation sur Blaze, choix « natif seul » ou « RevenueCat » à trancher dans le ticket Premium (Question 19 de `data-model.md`).

### Décisions que le PM doit prendre
1. **Carte bancaire pour Blaze : oui ou non, et quand** (avant la bêta externe recommandé).
2. **Alertes de budget** : montant mensuel (suggestion 5 $) et destinataire des e-mails ; accepter le **risque résiduel** Firestore non plafonné.
3. **Si non à Blaze** : accepter un **second fournisseur** (compte Cloudflare gratuit, inscription et éventuelle carte : non vérifié), l'**exception à `secrets.md`** (une clé de service chez un tiers), et que « plus de nouvelles » et le premium réel **restent à venir**.
4. **Clé APNs `.p8`** (compte Apple Developer) et capacité « Push Notifications » : à créer par le PM, quel que soit le serveur.
5. **Jetons d'appareil** : `users/{uid}/devices` privé (modèle actuel, A) ou publié dans la fiche membre (variante B sans clé) ; décision de minimisation.
6. **Produit** : trancher Q3 à Q5 de `notifications.md` (qui est prévenu, B1 en gratuit ou Premium) **avant** de bâtir B1/B2, vu le risque de rouge quotidien en journée de travail.
7. **Mise à jour des textes légaux** quand l'envoi s'active (la politique promet aujourd'hui l'inverse).

---

## 8. Plan de mise en œuvre par tickets (option recommandée : A)

Tailles : **S** = moins d'une journée d'agent, **M** = 1 à 3 jours, **L** = plus. Ordre = dépendances. Aucune étape ne s'applique sans accord du PM ; rien n'est déployé par un agent sans « oui » explicite.

| # | Ticket | Taille | Dépend de | Contenu | Propriétaire |
|---|---|---|---|---|---|
| **SRV-0** | Décisions et activation | S | Décisions 1, 2, 4 | PM : carte, Blaze, alertes de budget, plafond Preview sur Functions, clé APNs ; vérifier le compte de facturation (3 tâches Scheduler partagées) | PM |
| **SRV-1** | Socle Functions | M | SRV-0 | `backend/functions/` (région `europe-west9`, 2e génération), tests sur émulateur, workflow CI (tests, pas de déploiement automatique), App Check exigé sur les fonctions appelables, mise à jour de `data-model.md` §12-13, `secrets.md`, `surveillance-quotas.md` | `backend-data` |
| **SRV-2** | Jetons d'appareil et capacité Push | M | SRV-1 | `users/{uid}/devices` (déjà modélisé §2.10) : règles et tests ; enregistrement et purge à la déconnexion sur Android et iOS ; capacité Push et profils iOS en CI ; clés de texte localisées envoyées avec les messages (les textes restent dans les apps) | `backend-data`, `android-dev`, `ios-dev` |
| **SRV-3** | Textes légaux et déclarations | S | SRV-2 (parallèle) | Politique, tableau des sous-traitants, Data safety, étiquettes Apple, avant activation de l'envoi | `release-manager` |
| **SRV-4** | Push événementiels : N5, B3 | M | SRV-2 | Déclencheur sur l'écriture de `status`, idempotence (livraison « au moins une fois »), plafonds et préférences de `notifications.md` §5.4, regroupement, tests | `backend-data` |
| **SRV-5** | Demande d'ajout de lieu : push créateur et demandeur | S à M | SRV-2 et le ticket « demande d'ajout de lieu » fusionné | Déclencheurs sur la création et la décision ; repli dans l'app si le push est perdu | `backend-data` + apps |
| **SRV-6** | « Plus de nouvelles » B1/B2 | M à L | SRV-4 et décisions produit Q3 à Q5 | `escalationDueAt` (déjà prévu §2.7), fonction planifiée (index de groupe de collections), clôture B2, idempotence, tests de bout en bout | `backend-data` |
| **SRV-7** | Premium réel, backend | L | SRV-1, décision « natif ou RevenueCat » | Fonction de validation (Google Play API, Apple), écriture de `entitlements`, notifications des stores, rattrapage, tests ; secrets dans le gestionnaire de secrets Google | `backend-data` |
| **SRV-8** | Premium réel, clients | L ×2 | SRV-7 | Play Billing (Android), StoreKit (iOS), comptes de test des stores | `android-dev`, `ios-dev` |

**Ordre conseillé** : SRV-0, SRV-1, SRV-2 (+ SRV-3), puis SRV-5 (le plus simple et le plus visible) et SRV-4, puis SRV-6, puis SRV-7 et SRV-8 avant la première soumission avec achat.

**Plan de repli B (seulement si le PM refuse Blaze)** : **WRK-0** prototype (M) : vérification des jetons d'identification et App Check, lecture Firestore avec jeton client + App Check, mesure de CPU et de latence, limitation de débit ; **WRK-1** Worker et envoi FCM (M) ; **WRK-2** demande de lieu et réponse (S) ; **WRK-3** N5 en best effort (M) ; SRV-2 et SRV-3 restent nécessaires. À remplacer par SRV-1, SRV-4 à SRV-6 au passage à Blaze.

---

## 9. Ce qui reste non vérifié ou hypothétique (liste unique)

- Prix Firestore Paris : deux tableaux affichent des valeurs voisines (0,036 / 0,107 et 0,035 / 0,104).
- Frais annexes de Blaze (images de fonctions, Secret Manager, journaux) : non chiffrés.
- Création d'un sujet Pub/Sub sans facturation ; autorisation de créer une clé de compte de service sur ce projet ; retour de Blaze à Spark : non vérifiés.
- Firestore accepte-t-il jeton d'identification **et** jeton App Check en REST une fois l'application stricte activée ; mesure du CPU (10 ms), de la latence et de la limitation de débit gratuite : à prototyper.
- Base Firestore nommée : quota gratuit et condition IAM par base : non vérifiés.
- Apple (APNs, notifications silencieuses, notifications serveur V2, API App Store Server, rôle requis pour la clé `.p8`) : pages illisibles.
- Comportement des push sur l'OPPO Find X5 Lite : non vérifié, à mesurer.
- Inscription Cloudflare (carte demandée ou non), emplacement d'exécution du code, offres de localisation régionale, conditions détaillées de l'offre Workers gratuite : non vérifiés.
- Absence de lot dans l'API FCM v1 : connaissance générale.
- Le ticket « demande d'ajout de lieu » n'a pas pu être lu (absent de `origin/main` et des branches distantes).

---

## 10. Sources (lues le 2026-11-11)

**Cloudflare** : [limites Workers](https://developers.cloudflare.com/workers/platform/limits/) · [tarifs Workers](https://developers.cloudflare.com/workers/platform/pricing/) · [Cron Triggers](https://developers.cloudflare.com/workers/configuration/cron-triggers/) · [KV](https://developers.cloudflare.com/kv/platform/pricing/) · [Durable Objects](https://developers.cloudflare.com/durable-objects/platform/pricing/) · [limitation de débit](https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/) · [conditions générales](https://www.cloudflare.com/terms/) · [conditions Developer Platform](https://www.cloudflare.com/service-specific-terms-developer-platform/) · [RGPD](https://www.cloudflare.com/trust-hub/gdpr/) (résumé de recherche seulement)
**Firebase et Google** : [tarifs Firebase](https://firebase.google.com/pricing) · [tarifs Firestore](https://cloud.google.com/firestore/pricing) · [tarifs Cloud Scheduler](https://cloud.google.com/scheduler/pricing) · [régions Functions](https://firebase.google.com/docs/functions/locations) · [déclencheurs Firestore](https://firebase.google.com/docs/functions/firestore-events) · [FCM HTTP v1](https://firebase.google.com/docs/cloud-messaging/send/v1-api) · [priorité Android](https://firebase.google.com/docs/cloud-messaging/android-message-priority) · [livraison FCM (blog)](https://firebase.blog/posts/2024/07/understand-fcm-delivery-rates/) · [quotas FCM](https://firebase.google.com/docs/cloud-messaging/throttling-and-quotas) · [sujets FCM](https://firebase.google.com/docs/cloud-messaging/manage-topics) · [clé APNs](https://firebase.google.com/docs/cloud-messaging/ios/certs) · [vérifier un jeton d'identification](https://firebase.google.com/docs/auth/admin/verify-id-tokens) · [API REST Firestore](https://firebase.google.com/docs/firestore/use-rest-api) · [App Check, serveur tiers](https://firebase.google.com/docs/app-check/custom-resource-backend) · [IAM Firestore](https://docs.cloud.google.com/firestore/native/docs/security/iam) · [bases multiples](https://firebase.google.com/docs/firestore/manage-databases) · [budgets](https://docs.cloud.google.com/billing/docs/how-to/budgets) · [plafonds de dépense Firebase](https://firebase.google.com/docs/projects/billing/spend-caps) · [tarifs Pub/Sub](https://cloud.google.com/pubsub/pricing)
**Achats** : [API Google Play subscriptionsv2](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2/get) · [démarrage API Google Play](https://developers.google.com/android-publisher/getting_started) · [notifications Play (RTDN)](https://developer.android.com/google/play/billing/rtdn-reference) · [RevenueCat](https://www.revenuecat.com/pricing/)
**Autres** : [Vercel, usage équitable](https://vercel.com/docs/limits/fair-use-guidelines) · [Supabase](https://supabase.com/pricing) · [dontkillmyapp, OPPO](https://dontkillmyapp.com/oppo)
**Dépôt** : `docs/tickets/notifications.md` (§3, §4, §5, §20), `docs/specs/paywall-premium.md`, `docs/tickets/initialisation-technique.md`, `docs/dev/premium-manuel.md`, `docs/dev/secrets.md`, `docs/dev/app-check.md`, `docs/dev/surveillance-quotas.md`, `backend/data-model.md` (§2.3, §2.7, §2.10, §12, §13), `docs/tickets/miff.md` (§5.8), `docs/legal/politique-confidentialite.fr.md`.

*Ce document ne modifie ni le code, ni les règles Firestore, ni Notion, ni les textes légaux, et ne prétend à aucune relecture juridique : les points RGPD (sous-traitants, transferts hors UE, données de localisation, mineurs possibles) restent des risques assumés par le PM (décision du 2026-10-23).*
