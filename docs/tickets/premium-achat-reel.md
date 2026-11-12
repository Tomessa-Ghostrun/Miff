# Ticket : Premium réel (Miff+) : achat, validation côté serveur, état d'abonnement (SRV-7, SRV-8A, SRV-8I, SRV-3)

**Statut** : **proposition de l'agent `product-specs`, avec les 8 questions du §19 TRANCHÉES par le PM le 2026-11-12 (« Ok pour tes recos ») : voir « Décisions du PM » ci-dessous.** Reste le GO du PM sur le ticket lui-même (porte « ticket validé »). Aucun développement lancé, aucun déploiement, aucune écriture Notion, aucune commande Firebase, aucun accès aux consoles des stores.
**Rédigé le** : 2026-11-12.
**Demande du PM** : rédiger le ticket du premium réel maintenant que Blaze est activé (décision du 2026-11-11).
**Décisions amont** : offre Miff+ décidée le 2026-10-31 (`docs/specs/paywall-premium.md`) ; Blaze adopté le 2026-11-11 (le PM souscrit lui-même, avec alertes de budget) ; étude de référence `docs/dev/etude-serveur-notifications-premium.md` (option A, tickets SRV-7 et SRV-8) ; règles des fonctions `docs/dev/deploiement-functions.md`.
**Ticket Notion** : à créer ou relier par le PM (je n'écris rien dans Notion). Statut Notion suggéré : « A cadrer ».

## Décisions du PM, 2026-11-12

Réponse du PM aux 8 questions du §19 : **« Ok pour tes recos »**. Les valeurs par défaut recommandées deviennent des **décisions** :

| # | Décision | Précisions |
|---|---|---|
| Q1 | **Validation directe côté serveur** (Cloud Functions). **Pas de RevenueCat.** | Pas de clé de compte de service Google, pas de transfert hors UE, un seul secret (clé Apple `.p8`) |
| Q2 | **Essai de 7 jours sur l'annuel seulement**, **un essai par compte du store** (le store décide) | **Aucun blocage ajouté par Miff** (pas d'identifiant d'appareil, pas de liste noire, pas de marqueur « essai utilisé »). Abus résiduel accepté |
| Q3 | **Remboursement : accès coupé à la confirmation du store** | Message neutre (« Ton abonnement Miff+ a été remboursé : les avantages ne sont plus actifs »), aucune donnée supprimée sur le moment ; Apple annule le remboursement : accès rétabli |
| Q4 | **Période de grâce activée** (Google et Apple) | Accès conservé pendant la grâce, puis suspension ; pas de mise en pause volontaire Google ; aucune notification push Miff en v1 |
| Q5 | **Excédent après expiration : 30 jours de mise en veille avant suppression** | Ticket de suite **PREM-FIN** (`docs/tickets/prem-fin.md`, cadrage bref), **avant l'ouverture publique** ; rien n'est supprimé automatiquement tant qu'il n'est pas livré |
| Q6 | **Un achat = un compte Miff** | Refus calme sur un autre compte ; transfert manuel par le PM ; Partage en famille / bibliothèque familiale non activés |
| Q7 | Produits : Google **`miff_plus`** (forfaits `annuel` et `mensuel`) ; Apple **`com.tomessa.miff.plus.annual`** et **`.monthly`**. **Prix annuel : on garde 59,88 € par an / 4,99 € par mois** | Si Apple ne propose pas ce palier, on retient **le palier le plus proche**, **à confirmer en App Store Connect** : c'est à ce moment seulement que la question devient bloquante (et le « presque 30 % » est à recalculer). Le même prix sur les deux stores est recommandé |
| Q8 | **L'offre ne liste que ce qui est livré** : « **Notifications intelligentes** » est **RETIRÉ** de l'écran d'achat et des fiches des stores tant qu'elles n'existent pas | À réintroduire quand elles sont livrées |

**Règle nouvelle du PM (2026-11-12) : croix de fermeture du paywall différée de 3 secondes à chaque affichage** (assumée comme « dark pattern », risques écrits comme risque assumé) : **voir `docs/tickets/paywall-v2.md` (PAY-UI)**. **Maquettes reçues** : `design/exports/paywall/Paywall.png` et `Paywall-1.png` (la règle du 2026-10-28 est donc satisfaite pour le paywall). **L'écran d'offre est désormais spécifié et livré par PAY-UI-A / PAY-UI-I, avant l'achat réel** ; SRV-8A / SRV-8I n'ont plus qu'à brancher l'achat du store sur cet écran. Ce qui est écrit au §4.3 (conséquences pour la maquette) et au §12 (textes) est **repris et précisé par `paywall-v2.md`** : en cas de différence, `paywall-v2.md` fait foi.

**Convention** : **[Décidé]** = décision du PM déjà écrite (source citée). **[Proposé]** = ma proposition, à valider. **[Vérifié]** = confirmé sur une page officielle lue le 2026-11-12 (lien en §21). **[Non vérifié]** = je n'ai pas pu le confirmer (page illisible, silencieuse, ou connaissance générale) : à contrôler avant de s'y fier. Les passages techniques (§6 à §12) sont des **propositions pour `backend-data`, `android-dev` et `ios-dev`** : ils décrivent ce qui est nécessaire, pas le code.

**Limites de cette lecture (à connaître)** :
- Je n'ai **pas d'outil shell** dans cette session : je n'ai pu ni créer la branche `docs/premium-achat-reel`, ni le worktree. Le fichier est écrit **hors dépôt** (voir le rapport) ; il est à copier tel quel dans `docs/tickets/premium-achat-reel.md` du worktree.
- J'ai lu les documents dans les worktrees existants les plus proches (`C:\dev\miff-srv4b` pour `deploiement-functions.md`, `data-model.md`, `firestore.rules`, les tickets ; `C:\dev\miff-etude-serveur` pour l'étude) : **ils peuvent être en retard sur `origin/main`**. À réaligner à la relecture.
- Les pages Apple « documentation » sont rendues en JavaScript : j'ai lu leurs données JSON publiques (types de notification, statuts, champs des transactions). Ce que ces lectures n'ont pas confirmé est marqué **[Non vérifié]**.
- Aucun texte de ce ticket n'a été relu par un juriste (décision du PM du 2026-10-23, risque assumé).

---

## 1. Résumé pour le PM

1. **Ce que ça livre** : on peut **vraiment acheter Miff+** dans l'app (essai gratuit de 7 jours sur l'annuel, annuel 59,88 €/an, mensuel 6,99 €), l'achat est **vérifié par un serveur Miff**, et le droit Miff+ s'allume (et s'éteint) tout seul selon l'état réel de l'abonnement. Plus besoin que le PM accorde Miff+ à la main (ça reste possible pour les testeurs).
2. **Recommandation** : **validation directe côté serveur** (Cloud Functions, option A), **pas RevenueCat**. Raison décisive trouvée en lisant la documentation de RevenueCat : il exige de lui **envoyer une clé JSON de compte de service Google** et la clé Apple ; or `docs/dev/secrets.md` interdit de créer une clé de compte de service, et RevenueCat stocke ses données aux États-Unis sans mécanisme de transfert nommé sur sa page de confidentialité (§3).
3. **Un seul secret côté serveur** : la clé Apple « In-App Purchase » (`.p8`), que **le PM dépose lui-même** dans le gestionnaire de secrets de Google. Côté Google Play, **aucune clé** : la fonction utilise l'identité de son compte de service dédié (à confirmer au premier essai).
4. **Les stores imposent des corrections à la maquette du paywall** (§4) : le **prix facturé (59,88 €/an) doit être l'élément le plus visible**, le « 4,99 €/mois » ne peut être qu'un complément plus petit (Apple et Google le disent explicitement) ; « pendant 1 an » disparaît ; on ne peut pas annoncer des **« notifications intelligentes » non livrées**.
5. **Un achat est lié à un compte Miff par un identifiant aléatoire** que le serveur fabrique (jamais l'UID, jamais l'e-mail) : aucune donnée personnelle ne part chez Apple ni chez Google (§5).
6. **Décidé le 2026-11-12** (« Ok pour tes recos », §19) : validation directe, essai sur l'annuel un par compte du store, remboursement = accès coupé, période de grâce, 30 jours de mise en veille avant suppression (PREM-FIN), un achat = un compte, noms de produits, offre limitée à ce qui est livré. **Seul point encore à confirmer** : l'existence du palier 59,88 € chez Apple, en App Store Connect (repli : le palier le plus proche).
7. **Ce que doit faire le PM** (§14, pas à pas) : signer les accords chez Apple et Google, saisir **lui-même** coordonnées bancaires et fiscales, créer les produits, créer la clé Apple, inviter le compte de service dans la Play Console, lancer les déploiements. **Aucun agent ne saisit de coordonnées bancaires ni ne crée de compte.**
8. **Coût** : 0 € d'outil supplémentaire ; commission des stores de l'ordre de 15 % (programmes à rejoindre, §14) ; fonctions et notifications de Pub/Sub dans les quotas gratuits aux volumes visés (§17).
9. **Découpage** : SRV-7 (serveur + tests des règles), SRV-8A (Android), SRV-8I (iOS), SRV-3 (textes légaux et déclarations des stores), plus un ticket de suite **PREM-FIN** (conséquences de la fin d'abonnement sur les Miffs et lieux, §9.3) à livrer avant l'ouverture publique (§18).

---

## 2. Périmètre et frontières

| Dans ce ticket | Hors de ce ticket (propriétaire) |
|---|---|
| Achat de Miff+ (annuel avec essai 7 jours, mensuel) sur Android (Google Play) et iOS (App Store) | Fin d'abonnement : **préavis, mise en veille, suppression** des Miffs et lieux en excédent (**PREM-FIN**, ticket de suite, §9.3) |
| Validation de l'achat par le serveur ; écriture de `entitlements/{uid}` **par le serveur seul** | Changement mensuel/annuel **dans l'app** (v2) : en v1, c'est la page de gestion du store |
| Réception des notifications temps réel des stores (renouvellement, annulation, paiement refusé, remboursement) | Codes promo, offres de reconquête, remises de fidélité, Partage en famille, abonnements prépayés |
| Restauration des achats (nouvel appareil, réinstallation) | Notifications push « ton essai se termine », « paiement refusé » (les stores envoient déjà leurs propres rappels ; pas de push Miff en v1) |
| Écran d'achat (paywall) branché + écran **Abonnement** enrichi (remplace la coquille ABO-1) | Offres régionales différenciées, changement de prix |
| Rappel d'abonnement à la suppression de compte (« Ton abonnement n'est pas résilié », décision de `suppression-compte.md` reportée à ce ticket) | Paiement hors store, site web d'achat |
| Textes légaux et déclarations des stores liés à l'abonnement (SRV-3) | Facturation, comptabilité, déclarations fiscales du PM (hors périmètre technique) |
| Compatibilité avec le Miff+ accordé à la main (`source = manual`) | Création du projet Firebase de développement (décision déjà prise, séparée) |

---

## 3. Options comparées (vérifiées sur sources officielles)

### Option A : validation directe côté serveur (Cloud Functions)

**Google Play**
- L'état d'un abonnement se lit avec `purchases.subscriptionsv2.get` (GET `…/applications/{packageName}/purchases/subscriptionsv2/tokens/{token}`, portée `androidpublisher`) ; Google l'appelle **la source de vérité** de l'état ; le jeton d'achat reste valable **jusqu'à 60 jours après l'expiration** [Vérifié].
- Les **notifications temps réel (RTDN)** passent par **Cloud Pub/Sub** et **disent seulement « quelque chose a changé »** : il faut **rappeler l'API** pour connaître l'état complet [Vérifié]. Types utiles : acheté (4), renouvelé (2), annulé (3), mise en suspens (5), période de grâce (6), récupéré (1), repris (7), expiré (13), révoqué (12) ; un message distinct signale un **achat annulé/remboursé** (« voided purchase ») [Vérifié].
- États renvoyés : en attente, actif, en pause, période de grâce, suspendu (« on hold »), annulé non expiré, expiré, achat en attente annulé [Vérifié, §8].
- Accès de la fonction : un compte de service **invité dans la Play Console** avec les droits « Voir les données financières, commandes et réponses aux enquêtes d'annulation » et « Gérer les commandes et abonnements » ; il n'est **plus nécessaire** de lier la Play Console à un projet Cloud [Vérifié]. **Non vérifié** : la page ne dit rien d'une authentification sans clé (identité du service en cours d'exécution) ; c'est le mécanisme attendu (comme pour l'envoi FCM, étude §2) mais **à confirmer par un premier appel réel** (critère BK9).
- Authentification des notifications : le sujet Pub/Sub n'est publié que par `google-play-developer-notifications@system.gserviceaccount.com` (droit « Pub/Sub Publisher » donné par le PM) [Vérifié]. Une fonction déclenchée par Pub/Sub (« onMessagePublished ») n'a **pas d'adresse publique** ; l'authentification des appels de livraison est assurée par la plateforme (le compte du déclencheur doit pouvoir invoquer la fonction ; la page Google lue le dit pour le déclencheur Eventarc) [Vérifié en partie]. En complément, la fonction **ne croit jamais le contenu du message** : elle ne s'en sert que comme signal et relit l'état à Google (un message falsifié ne peut au pire que déclencher une relecture sans effet).

**Apple**
- **Notifications de serveur App Store V2** : Apple envoie un `signedPayload` (JWS) à une URL publique que le PM déclare dans App Store Connect (une URL de production, une de bac à sable) ; la fonction doit répondre **200 à 206** pour accuser réception ; sinon Apple réessaie **5 fois, à 1, 12, 24, 48 et 72 heures** (production seulement ; **une seule tentative en bac à sable**) [Vérifié]. 24 types existent (SUBSCRIBED, DID_RENEW, DID_FAIL_TO_RENEW avec sous-type GRACE_PERIOD, GRACE_PERIOD_EXPIRED, EXPIRED, DID_CHANGE_RENEWAL_STATUS, REFUND, REFUND_REVERSED, REVOKE…) [Vérifié].
- **Authentification d'un appel Apple** : on ne peut pas exiger un jeton, l'URL est publique ; la protection est la **vérification de la signature JWS** (chaîne de certificats jusqu'aux certificats racines d'Apple). La bibliothèque officielle **`@apple/app-store-server-library`** (Node 22 ou plus) fournit `SignedDataVerifier` ; elle exige les certificats racines Apple, l'identifiant de bundle, l'environnement et, **pour la production, l'identifiant Apple de l'app (`appAppleId`)** [Vérifié].
- **App Store Server API** (`GET /inApps/v1/subscriptions/{anyTransactionId}`, production `api.storekit.apple.com`, bac à sable `api.storekit-sandbox.apple.com`) : état des abonnements du client dans l'app ; filtre `status` ; limite de débit (429) [Vérifié]. Elle exige un **jeton signé avec la clé « In-App Purchase »** (`.p8`, rôle Admin pour la créer, Users and Access > Integrations > In-App Purchase) [Vérifié].
- Statuts : 1 actif, 2 expiré, 3 nouvelle tentative de facturation, 4 période de grâce, 5 révoqué [Vérifié].
- **Secret côté serveur** : cette clé `.p8` est **le seul secret** de l'option A. Elle se range dans le gestionnaire de secrets Google (accès réservé au compte `billing-sync`), jamais dans le dépôt, jamais dans l'app. **Variante sans clé Apple** (repli, non recommandée) : se fier uniquement aux notifications signées et à la transaction signée envoyée par l'app ; elle laisse un risque de **rejeu** d'une ancienne transaction « active » après un remboursement, qu'il faudrait compenser en conservant l'historique des révocations par transaction.

**Avantages** : aucune clé Google ; un seul fournisseur de plus que ceux déjà utilisés (Apple, Google) ; données en Europe (Firestore Paris, fonctions Paris) ; coût proportionnel ; pas de SDK tiers dans l'app. **Inconvénients** : plus de code serveur à écrire et tester (vérification JWS, deux API) ; test iOS uniquement en bac à sable.

### Option B : RevenueCat

- **Prix** : gratuit jusqu'à **2 500 $ de revenu suivi par mois**, puis **1 %** de ce qui est suivi [Vérifié]. Ordre de grandeur : environ 500 abonnés à l'équivalent de 4,99 € par mois avant de payer (hypothèse : le revenu annuel compte en équivalent mensuel ; non vérifié).
- **Webhook** : l'en-tête d'autorisation est défini par le développeur et doit être vérifié ; **5 nouvelles tentatives** avec délais exponentiels (environ 2,5 à 40 minutes, soit **80 minutes au total**), puis arrêt ; RevenueCat recommande de **rappeler son API REST** après chaque webhook ; les événements peuvent arriver en double [Vérifié].
- **Identifiants à lui confier** : pour Google Play, un **fichier JSON de clé de compte de service téléversé dans RevenueCat** (plus les rôles Pub/Sub Editor et Monitoring Viewer, jusqu'à 36 heures de validation) ; pour Apple, le **fichier `.p8` « In-App Purchase »** et l'Issuer ID [Vérifié]. **Cela contredit directement `docs/dev/secrets.md`** (aucune clé de compte de service) et confie à un tiers un accès aux ventes de l'app.
- **RGPD** : la page de confidentialité de RevenueCat dit stocker les données **sur AWS aux États-Unis**, ne nomme **aucun mécanisme de transfert** (ni cadre de protection des données UE-États-Unis, ni clauses types) et ne mentionne pas le RGPD dans le texte lu ; il agit comme sous-traitant pour les données des utilisateurs finaux (identifiant utilisateur, jeton d'achat Google, reçu Apple, informations techniques) [Vérifié].
- **Il faudrait de toute façon** une fonction Miff pour recevoir le webhook, relire l'API de RevenueCat (avec une clé secrète de plus) et **écrire `entitlements/{uid}`** : B ne supprime donc pas la fonction serveur, il remplace la vérification directe des stores par une dépendance tierce, et ajoute un SDK dans les deux apps.
- **Avantages réels** : moins de code (pas de vérification JWS, pas de Pub/Sub), un modèle unique pour les deux stores, tableau de bord. **Dépendance** : service tiers sans engagement de disponibilité pour l'offre gratuite (à lire dans ses conditions : non lues).

### Option C : rester manuel

0 €, déjà en place (`docs/dev/premium-manuel.md`), mais **aucune vente possible** : suffisant pour la bêta fermée, pas pour un lancement avec abonnement.

### Comparatif

| Critère | A : directe | B : RevenueCat | C : manuel |
|---|---|---|---|
| Clé de compte de service Google hors de Google | **Non** (identité du service) | **Oui, obligatoire** | Non |
| Secret à garder | Clé Apple `.p8` (secret Google) | Clés Apple et Google + clé API RevenueCat | Aucun |
| Données d'achat hors UE | Non (stores seulement) | Oui (États-Unis) | Non |
| SDK tiers dans les apps | Non | Oui | Non |
| Coût d'outil | 0 € | 0 € puis 1 % au-delà de 2 500 $/mois | 0 € |
| Code serveur | Plus important | Moyen (webhook + lecture API) | Aucun |
| Conforme aux règles du projet (secrets, RGPD) | **Oui** | **Non sans dérogation écrite du PM** | Oui |
| Passage à l'échelle | Progressif (quotas Firebase) | Progressif (1 %) | Impossible |

**Recommandation [Proposé]** : **A**, en commençant par Android (aucun secret, testable en local) puis iOS. **C** reste actif en parallèle pour les testeurs (§9.4). B n'est à reconsidérer que si, après SRV-7, le coût de maintenance du code serveur se révèle disproportionné ; il faudrait alors une décision écrite du PM sur la clé de compte de service et sur les transferts hors UE.

---

## 4. L'offre et ce que les stores exigent d'afficher

### 4.1 L'offre décidée [Décidé, 2026-10-31]
Nom : **Miff+**. **Annuel** : 59,88 € par an (4,99 € par mois en équivalent), **essai gratuit de 7 jours** (annuel seulement), **reconduit au même tarif**. **Mensuel** : 6,99 €, sans engagement, sans essai. « Économisez 30 % » **remplacé par « presque 30 % d'économie »** (décision du PM du 2026-11-07 ; le calcul réel est 28,6 %).

### 4.2 Produits à créer [Proposé]
Les **identifiants de produit sont définitifs** une fois créés (Google : « ne peuvent être ni modifiés ni réutilisés » [Vérifié] ; Apple : à traiter de même). Proposition de noms (à valider, voir Q7) :

| Store | Structure |
|---|---|
| Google Play | **1 abonnement** `miff_plus` avec **2 forfaits de base** : `annuel` (période 1 an, 59,88 €) et `mensuel` (période 1 mois, 6,99 €) ; **1 offre** « essai 7 jours » rattachée au forfait `annuel` (éligibilité « nouveau client »). Un essai gratuit dure de 3 jours à 3 ans [Vérifié] |
| App Store | **1 groupe d'abonnements** « Miff+ » (Apple recommande un seul groupe : un client ne peut avoir qu'un abonnement actif par groupe [Vérifié]) avec **2 abonnements** : `com.tomessa.miff.plus.annual` (durée 1 an) et `com.tomessa.miff.plus.monthly` (durée 1 mois), **même niveau** (même contenu, durées différentes) ; **1 offre d'introduction « essai gratuit 7 jours »** sur l'annuel |

### 4.3 Règles d'affichage imposées par les stores (écart avec la maquette)
- **Apple** : le **montant facturé doit être l'élément de prix le plus visible** ; une équivalence mensuelle ou une économie peut figurer, **en position et en taille subordonnées** [Vérifié]. L'écran d'inscription doit montrer : nom et durée de l'abonnement et ce qu'il contient, **prix de renouvellement complet, lisible et localisé**, un moyen de **restaurer les achats** [Vérifié]. Pour un essai : **durée de l'essai et prix facturé ensuite** [Vérifié]. Les **liens vers les conditions d'utilisation et la politique de confidentialité** doivent figurer dans l'app **et** dans les métadonnées de l'App Store [Vérifié].
- **Google Play** : les conditions, le coût et la fréquence de facturation doivent être indiqués clairement ; **« les forfaits annuels ne doivent pas mettre principalement en avant un prix mensuel équivalent »** ; l'essai doit dire sa durée, expliquer qu'il **bascule automatiquement vers un abonnement payant**, son prix, et **comment annuler avant la bascule** ; il doit être montré que le contenu est **accessible sans essai** quand c'est le cas ; l'app doit donner accès à **un moyen facile d'annuler en ligne** (un lien vers le centre d'abonnements Google Play suffit) [Vérifié].
- **Conséquences pour la maquette (`reglages-0`, paywall)** [Proposé ; **reprises et précisées par `docs/tickets/paywall-v2.md` §4 à partir des maquettes reçues le 2026-11-12, qui fait foi**] :
  1. Afficher **« 59,88 € par an »** (prix lu dans le store, jamais écrit en dur) en grand ; « soit 4,99 €/mois » plus petit.
  2. Remplacer « puis 4,99 €/mois pendant 1 an » par « Essai gratuit 7 jours, puis {prix annuel} par an, reconduit chaque année tant que tu ne l'annules pas ».
  3. La pastille « Annulable à tout moment » devient « Annulable à tout moment : tu gardes l'accès jusqu'à la fin de la période payée » (le remboursement de la période en cours n'est pas la règle par défaut de Google Play [Vérifié]).
  4. Garder **« Continuer avec la version gratuite »** visible (exigence Google ci-dessus).
  5. Ne lister que ce qui est livré (Q8) : « notifications intelligentes » n'existent pas aujourd'hui.
  6. « Économisez 30 % » devient « presque 30 % d'économie » (décision du 2026-11-07) ; à recalculer si les prix du store changent (R8).
  8. **Nouveau (2026-11-12)** : la croix de fermeture n'apparaît que 3 secondes après chaque affichage du paywall (`paywall-v2.md` §6).
  7. Les écrans `reglages-6/8/9/13` (« Prochain paiement », « Changer d'abonnement », « Arrêter l'abonnement », dialogue de perte des Miffs) sont **à reprendre** : l'arrêt et le changement se font **chez le store** en v1 ; le texte de perte des Miffs est remplacé (Q5).
- **Prix : toujours lus dans les stores** (prix localisé et devise de chaque pays), jamais écrits en dur ni stockés dans Firestore. **Point d'attention** : Apple ne propose que des **niveaux de prix prédéfinis** ; **59,88 € n'existe peut-être pas** (le 4,99 × 12 est un calcul) : à vérifier dans App Store Connect avant de créer le produit (repli à discuter : 59,99 €) [Non vérifié] (Q7).

---

## 5. Lier un achat à un compte Miff, sans fuite

**Objectif** : le serveur doit savoir à quel `uid` Firebase appartient un achat, sans jamais envoyer à Apple ni à Google l'UID, l'e-mail, le téléphone ni le nom.

### 5.1 Principe [Proposé]
1. Le serveur fabrique, pour chaque compte, un **identifiant d'achat aléatoire** (UUID aléatoire, jamais dérivé de l'UID ni de l'e-mail), le conserve dans une collection **réservée au serveur** et le remet **uniquement à son propriétaire**, par une fonction appelable (authentification + App Check obligatoires).
2. L'app passe cet identifiant au store au moment de l'achat : **`appAccountToken`** côté Apple (un UUID, retrouvé dans la transaction signée ; omis si l'app n'en fournit pas [Vérifié]) et **`obfuscatedAccountId`** côté Google (retrouvé dans l'état de l'abonnement comme `obfuscatedExternalAccountId` quand il a été fourni à l'achat [Vérifié]). Google recommande un identifiant **obscurci**, qui sert aussi à détecter la fraude [Vérifié].
3. Quand une notification ou une demande de synchronisation arrive, le serveur **relit l'état chez le store**, y retrouve cet identifiant, et retrouve le compte. **Le store est la preuve** : un client qui enverrait l'identifiant d'achat d'un autre ne peut rien gagner, car le serveur vérifie que l'identifiant contenu **dans les données du store** correspond **à l'appelant authentifié**.

### 5.2 Ce que ça garantit
- **Pas de fuite** : le store ne voit qu'un identifiant opaque ; Miff ne reçoit ni numéro de carte ni donnée de paiement (déjà écrit dans la politique, tableau « Abonnement Miff+ »).
- **Pas d'usurpation** : pour l'accorder, le serveur exige (a) l'identifiant du store égal à celui de l'appelant, **ou** (b) l'absence d'identifiant (achat fait hors de l'app, par exemple dans le centre d'abonnements de Google Play : Google signale ces achats comme « hors de l'app » [Vérifié]) **et** un justificatif que seul le propriétaire de l'achat possède : **jeton d'achat Google** (long, non devinable) ou **transaction signée Apple** (obtenue seulement sur un appareil connecté au compte Apple concerné). Un simple numéro de transaction ne suffit jamais.
- **Un achat = un compte Miff** : un achat déjà lié à un compte existant ne peut pas être lié à un autre (Q6). Le lien est conservé dans un document serveur par achat (§9.2).
- **Compte supprimé** : à la suppression du compte, l'identifiant d'achat et le lien sont supprimés ; si la personne se réinscrit avec le même compte Apple ou Google puis « Restaure », l'achat (dont l'identifiant est devenu inconnu) peut être **lié au nouveau compte** : l'abonnement n'est pas perdu (§9.5).
- **Comptes multiples sur un même achat** : refus calme (« Cet abonnement est déjà utilisé avec un autre compte Miff »), sans détail sur l'autre compte, avec un contact ; un transfert est une opération manuelle du PM (suppression du lien dans la console) tant que Q6 n'a pas d'autre réponse.
- **Environnement de test** : les achats de test (Google : champ `testPurchase` [Vérifié] ; Apple : environnement « Sandbox » [Vérifié]) sont acceptés **et marqués** (§9.1) : voir R10.

---

## 6. Flux Android (Google Play Billing)

Bibliothèque : Play Billing Library (la documentation du 2026-11-12 affiche la version **9.1.0** [Vérifié]) ; `minSdk` 26 respecté. Le dev choisit la version à l'installation.

1. **Produits et prix** : au démarrage de l'écran d'achat, l'app interroge `queryProductDetailsAsync` (produit `miff_plus`) et affiche les **prix et libellés fournis par Google** (jamais en dur). L'offre d'essai n'est montrée que si Google la renvoie à cet utilisateur (éligibilité « nouveau client » : n'a jamais eu droit à cet abonnement ni à un autre de l'app [Vérifié]) : **à confirmer en test** que l'offre est bien absente pour un client non éligible ; sinon l'achat se fait sans essai, sans erreur.
2. **Avant l'achat** : appel de la fonction `preparerAchat` (réseau requis) pour obtenir l'identifiant d'achat ; en cas d'échec, message calme (« Pas de connexion pour le moment, réessaie dans un instant ») et aucun achat lancé.
3. **Achat** : `launchBillingFlow` avec l'identifiant d'achat (`setObfuscatedAccountId`). Résultats : annulé par la personne (aucun message d'erreur), erreur, **en attente** (paiement différé : message « Ton paiement est en cours de validation par Google Play », **aucun droit accordé** tant que l'état n'est pas « acheté » [Vérifié]), **acheté**.
4. **Après « acheté »** : l'app envoie le jeton d'achat à `synchroniserAchat`. Le serveur vérifie, accorde, puis **accuse réception de l'achat auprès de Google** (acknowledge). **Délai : 3 jours, sinon Google rembourse et révoque l'achat automatiquement** [Vérifié] ; l'accusé de réception **côté serveur** (méthode `purchases.subscriptions.acknowledge`, toujours en usage [Vérifié]) a l'avantage de fonctionner **même si l'app est fermée juste après le paiement**, via la notification temps réel. Le client **n'accuse pas réception lui-même** (évite un double accusé). Les renouvellements n'ont pas besoin d'accusé [Vérifié].
5. **Rejet** : si la vérification échoue (mauvais identifiant, mauvais package, achat de test non autorisé), le serveur **n'accorde rien, n'accuse pas réception et révoque et rembourse tout de suite** (`purchases.subscriptionsv2.revoke` avec remboursement complet), comme le recommande Google ; il ne laisse **pas** expirer le délai de 3 jours [Vérifié].
6. **Restauration** : à chaque ouverture de l'app connectée, `queryPurchasesAsync` ; s'il existe un achat actif et que `entitlements/{uid}` est absent, expiré ou plus ancien, appel de `synchroniserAchat` (réparation automatique, c'est le filet contre une notification perdue, §10). Bouton **« Actualiser mon abonnement »** sur l'écran Abonnement (et dans le paywall pour la personne qui réinstalle). Attention : un abonnement en **suspension (« on hold »)** n'est **pas** renvoyé par `queryPurchasesAsync` [Vérifié] : l'app s'appuie alors sur l'état du serveur.
7. **Changer de forfait** : **pas dans l'app en v1**. Lien vers la page de gestion du store : `https://play.google.com/store/account/subscriptions?sku=miff_plus&package=com.tomessa.miff` (où la personne peut annuler, reprendre, changer) [Vérifié]. (En v2, passage mensuel vers annuel avec un mode de remplacement : Google en propose cinq, dont proratisation du temps restant ou facturation du complément ; le choix sera fait avec des essais [Vérifié pour la liste].)
8. **Fonctions de Google à régler dans la Play Console** : période de grâce **activée** (par défaut) et suspension du compte (« account hold ») **activée** avec sa durée automatique (**60 jours moins la période de grâce** par défaut) [Vérifié] ; **ne pas activer la mise en pause** par l'utilisateur (moins d'états à gérer).
9. **Utilisateur annule dans le centre d'abonnements** : il garde l'accès jusqu'à la fin de la période payée [Vérifié] ; la fonction reçoit « annulé », l'état devient « annulé non expiré » (§8).

---

## 7. Flux iOS (StoreKit 2)

iOS 16 minimum respecté. **Aucune compilation locale** : tout se vérifie en CI (macOS) et sur l'iPhone 11 Pro du PM via TestFlight, où **les achats sont toujours en bac à sable** (aucun débit réel) [Vérifié].

1. **Produits et prix** : chargement des deux produits ; affichage du **prix localisé fourni par StoreKit** ; l'éligibilité à l'essai se lit via `isEligibleForIntroOffer`, qui vaut pour **l'abonnement ou n'importe quel abonnement du même groupe** [Vérifié] : Apple tient seul le compte des essais déjà utilisés.
2. **Avant l'achat** : `preparerAchat` (comme sur Android) pour l'identifiant d'achat.
3. **Achat** : `product.purchase(options: [.appAccountToken(<identifiant>)])`. Cas : succès vérifié, en attente (« demander à un parent » : aucun droit), annulé (silence), échec.
4. **Après succès vérifié** : l'app envoie la **transaction signée** (JWS) à `synchroniserAchat`. Le serveur vérifie la signature, relit l'état à l'API Apple, accorde. **Ce n'est qu'ensuite** que l'app « termine » la transaction : tant qu'elle n'est pas terminée, StoreKit la **re-présente** au prochain lancement (filet de sécurité). Les transactions non terminées sont livrées une seule fois dès le lancement [Vérifié] : **l'écoute doit donc démarrer au tout début du lancement**.
5. **Écoute permanente** : `Transaction.updates` dès le lancement ; elle reçoit les transactions faites hors de l'app, sur un autre appareil, les approbations « Ask to Buy » et les échanges de code [Vérifié] ; les achats faits dans l'app, sur le même appareil, reviennent par le résultat de l'achat. Chaque transaction vérifiée déclenche `synchroniserAchat` (avec limitation du nombre d'appels).
6. **Restauration** : `AppStore.sync()` **uniquement sur action de la personne** (bouton « Restaurer mes achats », **exigé par Apple** : l'écran d'inscription doit offrir un moyen de restaurer [Vérifié]) ; l'appel affiche une demande d'authentification Apple et ne doit **jamais** être déclenché automatiquement [Vérifié]. Au lancement, la lecture silencieuse des droits en cours (`Transaction.currentEntitlements`) sert à repérer un abonnement à synchroniser.
7. **Changer de forfait, annuler** : **pas dans l'app en v1** : ouverture de la feuille système de gestion des abonnements (Apple recommande de rendre cet accès facile [Vérifié]) ; le dev confirme l'API de présentation de cette feuille [Non vérifié].
8. **Environnements** : le serveur reconnaît l'environnement de chaque transaction (champ « environnement » : bac à sable ou production [Vérifié]) et interroge la bonne API ; il marque les achats de bac à sable (R10).
9. **Partage en famille** : **non activé** (Apple : une fois activé pour un produit, on ne peut plus le désactiver [Vérifié]). Par prudence, le serveur **n'accorde pas** une transaction marquée « partagée en famille » tant que la question n'est pas tranchée (Q6).
10. **Apple exige** : abonnements utilisables sur tous les appareils de la personne, durée d'au moins 7 jours, valeur continue (3.1.2(a)) ; pas de souscription accidentelle à plusieurs variantes (3.1.2(b)) : **l'écran d'achat n'est pas proposé à un compte déjà abonné** [Vérifié].

---

## 8. États d'abonnement : droits et ce que montre l'app

Le droit Miff+ dépend uniquement de `entitlements/{uid}` : `active == true` **et** `expiresAt > heure serveur` (les règles le vérifient, aucune fonction n'est nécessaire pour « éteindre » à l'échéance, `data-model.md` §2.3). Le serveur calcule donc `active` et `expiresAt` à partir de l'état du store, et un champ d'**état** informatif pour l'affichage.

| État (champ `state`) | Google Play | Apple | `active` | `expiresAt` | Ce que montre l'app (écran Abonnement) [Proposé] |
|---|---|---|---|---|---|
| **Essai** (`trial`) | Actif, phase « essai gratuit » | Statut 1, offre d'introduction gratuite | oui | fin de l'essai | « Miff+ : essai gratuit jusqu'au {date}. Ensuite {prix annuel} par an, sauf si tu l'annules avant. » + « Gérer mon abonnement » |
| **Actif** (`active`) | Actif | Statut 1, renouvellement activé | oui | prochaine échéance | « Miff+ : actif. Prochain renouvellement le {date} ({prix}). » + « Gérer mon abonnement » |
| **Annulé, non expiré** (`canceled`) | Annulé | Statut 1, renouvellement désactivé | oui | fin de période payée | « Miff+ se termine le {date}. Tu gardes tout jusqu'à cette date. » + « Gérer mon abonnement » (reprise possible) |
| **Période de grâce** (`grace`) | En période de grâce (échéance prolongée par Google [Vérifié]) | Statut 4, échéance de grâce | oui | fin de la grâce | « Le paiement de ton abonnement n'a pas abouti. Tout fonctionne encore : mets à jour ton moyen de paiement avant le {date}. » + lien vers le store |
| **Suspendu** (`on_hold`) | Suspendu (échéance dans le passé [Vérifié]) | Statut 3 (nouvelle tentative de facturation) | **non** | passée | « Miff+ est en pause : le paiement n'a pas abouti. Tes avantages reviendront dès que ce sera réglé. » + lien vers le store |
| **Expiré** (`expired`) | Expiré | Statut 2 | non | passée | « Ton abonnement Miff+ est terminé. Tu peux le reprendre quand tu veux. » + bouton d'achat |
| **Remboursé / révoqué** (`revoked`) | Révoqué ou achat annulé (« voided ») | Statut 5, remboursement ou révocation | non | immédiatement | « Ton abonnement Miff+ a été remboursé : les avantages ne sont plus actifs. » (ton neutre, aucune explication ni reproche) |
| **En attente** (`pending`) | En attente de paiement | Demande d'approbation | non | n/a | « Ton paiement est en cours de validation. » (écran d'achat uniquement) |
| **Famille** | Bibliothèque familiale : non gérée | Partage en famille : **non activé** | n/a | n/a | Non géré en v1 (Q6) ; si une transaction « partagée en famille » arrive, journal et refus d'accorder |
| **Accordé à la main** (`manual`) | n/a | n/a | selon le document | selon le document | Comportement actuel de ABO-1 : « Actif jusqu'au {date} », « Accordé par l'équipe Miff » |

**Règles d'affichage** :
- **Ton** : jamais de rouge, de point d'exclamation, de « attention », de compte à rebours ni de culpabilisation ; chaque message dit ce qui se passe, ce qui reste vrai, et la prochaine étape.
- **Aucune pastille de notification** pour un problème de paiement ; une ligne sobre sur l'écran Abonnement et, au plus, une carte fermable dans les Réglages.
- **Dates** : format long de la langue de l'app (« 22 août 2027 »), jamais « 02/18/12 » (corrige A3 du challenge des maquettes).
- **Prix** : toujours ceux du store (pas du document Firestore) ; si le store est injoignable, la phrase s'affiche **sans prix**.
- **Changement de règle d'ABO-1** : aujourd'hui, un compte gratuit ou expiré ne voit **aucune trace d'abonnement**. Avec l'achat réel, un compte dont `source` est un store voit l'écran Abonnement **dans tous les états** (y compris expiré) ; un compte gratuit sans document voit l'encart d'achat là où la spec Paywall le prévoit ; un compte `manual` expiré ne voit rien (comportement actuel). **Compatibilité** : l'écran actuel traite toute `source` autre que `manual` comme « Offre en cours » (libellé neutre à valider, ABO-1) ; ce libellé est remplacé par le tableau ci-dessus.
- **Fin de l'accès** : voir §9.3.

---

## 9. Modèle de données [Proposé, à arbitrer par `backend-data`]

### 9.1 `entitlements/{uid}` (lisible par son propriétaire, écrit par le serveur seul)
Les règles ne changent pas : `allow get: if isSelf(uid)`, `list` et `write` refusés (`firestore.rules`). Le serveur (Admin SDK) ignore les règles.

| Champ | Type | Existant ? | Note |
|---|---|---|---|
| `active` | bool | oui | Vu par les règles ; `true` pour essai, actif, annulé non expiré, grâce |
| `expiresAt` | timestamp | oui | Vu par les règles ; fin d'accès (échéance du store, ou fin de grâce) |
| `source` | string | oui | **Valeurs du dépôt conservées : `manual` / `app_store` / `google_play`** (`data-model.md` §2.3). Écart avec la demande (« google, apple, manual ») : je garde les valeurs existantes pour ne pas casser ABO-1 (qui teste `manual`) |
| `updatedAt` | timestamp | oui | Heure serveur de la dernière écriture |
| `state` | string | **nouveau** | `trial` / `active` / `canceled` / `grace` / `on_hold` / `expired` / `revoked` / `manual` ; informatif (l'accès reste décidé par `active` et `expiresAt`) |
| `plan` | string \| null | **nouveau** | `yearly` / `monthly` / `null` (manuel) |
| `trial` | bool | **nouveau** | `true` pendant l'essai seulement ; **n'est pas** un marqueur « essai déjà utilisé » (c'est Apple/Google qui tiennent ce compte) |
| `willRenew` | bool | **nouveau** | Renouvellement activé chez le store |
| `test` | bool | **nouveau** | `true` pour un achat de test (Google `testPurchase`, Apple « Sandbox ») |

**Compatibilité** : un document `manual` existant (4 champs) reste valide ; les clients traitent l'absence des nouveaux champs comme `state` déduit de `active`/`expiresAt`, `trial` faux, `willRenew` inconnu. **À tester** (critère AN7) : la lecture d'un document sans les nouveaux champs et avec des champs inconnus ne plante sur aucune des deux plateformes.

**Précédence manuel / store** : un seul document, une seule source à la fois. Le serveur n'écrase un accès `manual` encore valide que si l'échéance du store est **plus tardive** ; sinon il garde le `manual` et mémorise l'achat (§9.2) pour reprendre la main quand le manuel expire (au prochain événement ou à la prochaine synchronisation de l'app). Règle d'exploitation à ajouter à `premium-manuel.md` : **ne jamais modifier à la main un document dont `source` est un store**.

### 9.2 Collections réservées au serveur (refusées à toute app par « tout le reste est refusé »)
Aucune donnée lisible par un autre utilisateur. À couvrir par des tests de règles (refus de `get`, `list`, création, mise à jour, suppression, même pour le propriétaire).

| Collection | Contenu | Durée de vie |
|---|---|---|
| `billingAccounts/{uid}` | identifiant d'achat aléatoire (UUID), date de création | jusqu'à la suppression du compte |
| `billingPurchases/{clé}` (clé = empreinte SHA-256 de « store + jeton d'achat ou identifiant de transaction d'origine ») | `uid` lié, store, produit, état, échéance, date du dernier événement pris en compte (anti-ordre), jeton d'achat (nécessaire pour rappeler Google), marque `test` | tant que l'achat existe ; lien supprimé à la suppression du compte |
| `billingEvents/{identifiant de message ou de notification}` | seulement l'identifiant et une date d'expiration | **7 jours** (règle de durée de vie Firestore à créer par le PM, comme `notifState`) |

Ni l'e-mail, ni le prénom, ni la position ne sont jamais écrits dans ces documents.

### 9.3 Fin d'accès et Miffs/lieux en excédent (ticket de suite PREM-FIN)
Décision actuelle [Décidé, 2026-10-24] : à la fin de Miff+, les Miffs et lieux au-delà de la formule gratuite (1 Miff, 3 lieux, aucune zone de danger), **les derniers ajoutés, sont supprimés** ; CGU article 9 et politique le disent ; les règles actuelles ne l'appliquent pas (elles tolèrent l'excédent). Ce ticket **ne supprime rien automatiquement** : il fournit le **signal** (`active=false`, `state`, `expiresAt`) et l'information à l'écran. La mise en veille, le préavis et la suppression font l'objet de **PREM-FIN** (Q5 : je propose 30 jours de mise en veille avant suppression). **Dépendance** : PREM-FIN doit être livré avant l'ouverture publique des achats, car la première échéance réelle d'un abonnement mensuel tombe un mois après le premier achat.

### 9.4 Premium accordé à la main (testeurs)
Il continue de fonctionner tel quel. Avant l'ouverture publique, noter la liste des comptes `manual` et leur fin (déjà recommandé par `premium-manuel.md`).

### 9.5 Suppression de compte avec abonnement actif
- **Rappel affiché** (décision de `suppression-compte.md` §7.7 reportée à ce ticket) : « Supprimer ton compte ne résilie pas ton abonnement Miff+. Pour ne plus être facturé, résilie-le dans {Google Play / l'App Store}. » + bouton **« Gérer mon abonnement »** ; pendant un essai, phrase renforcée (l'abonnement annuel démarre seul à la fin de l'essai). Clés de texte déjà rédigées dans le ticket (`settings_delete_sub_*`).
- **Côté serveur** : un appel (fonction appelable, avant la suppression du compte d'authentification) supprime `billingAccounts/{uid}` et les liens `billingPurchases` du compte, et marque `entitlements/{uid}` comme inactif avec une date de purge à **+30 jours** (durée acceptée par le PM le 2026-10-24 ; suppression par règle de durée de vie). **Limite connue** : si l'app plante entre deux étapes, des données restent orphelines (même risque que le reste de la procédure, `data-model.md` §10).
- **Mise à jour des textes** : la ligne `entitlements` du tableau de conservation (« purge à la main ») devient « purge automatique à 30 jours ».

---

## 10. Fonctions Cloud [Proposé]

Dans `backend/functions/` (région `europe-west9`, 2e génération, Node 24 ; la bibliothèque Apple exige Node 22 ou plus [Vérifié]). Noms indicatifs, dans le style de `ping` et `notifierUpdate`.

| Fonction | Déclencheur | Rôle |
|---|---|---|
| `preparerAchat` | appelable (connexion + App Check exigés, connexion anonyme refusée) | crée ou renvoie l'identifiant d'achat du compte |
| `synchroniserAchat` | appelable (idem) | reçoit un jeton d'achat Google ou une transaction signée Apple ; vérifie auprès du store ; applique les règles de §5 ; écrit `billingPurchases` et `entitlements` ; accuse réception chez Google ; limite : 1 appel par minute et par compte |
| `recevoirNotificationsGooglePlay` | message Pub/Sub (sujet `play-rtdn`) | décode, ignore tout autre package, relit l'état à Google, met à jour |
| `recevoirNotificationsApple` | requête HTTPS (URL publique, POST) | vérifie la signature JWS, applique, répond 200 vite |
| `supprimerDonneesAbonnement` | appelable (idem) | purge à la suppression du compte (§9.5) |

**Règles communes** (reprises de `deploiement-functions.md`, vérifiées par le test `garde-couts.test.mjs`) :
- **Compte de service dédié** `billing-sync@miff-3d283.iam.gserviceaccount.com` (nom à valider), **sans clé**, rôles : accès aux données Firestore (`roles/datastore.user`), réception d'événements (`roles/eventarc.eventReceiver`), journaux (`roles/logging.logWriter`), et lecture du **seul** secret Apple (`roles/secretmanager.secretAccessor` accordé **sur ce secret**, pas sur le projet). **Point d'attention** : le rôle Firestore donne accès à **toute** la base (positions comprises) car le compte ignore les règles ; la granularité par collection n'est pas confirmée (étude §3.4, [Non vérifié]) : c'est le prix de l'écriture de `entitlements` par le serveur ; il est réduit par un code court, des entrées bornées et des tests (R2). Jamais Éditeur ni Propriétaire.
- **Limites** : `maxInstances` 3 (plafond du test : 5), 256 Mio (le SDK Admin et la bibliothèque Apple dépassent probablement 128 Mio : à mesurer), délai 30 s, `minInstances` 0, région Paris. **Nouvelle tentative automatique** : refusée par défaut ; **autorisée pour `recevoirNotificationsGooglePlay` seulement** (ajout à `FONCTIONS_AVEC_RETRY_AUTORISEES` avec la raison), parce que son traitement est **idempotent par construction** : chaque exécution relit la vérité chez Google et écrit cet état, un doublon ne change rien.
- **Idempotence** : (a) l'état écrit est toujours celui relu au store ; (b) un événement plus ancien que celui déjà pris en compte pour cet achat est ignoré ; (c) les identifiants de messages/notifications déjà vus sont ignorés (`billingEvents`) pour économiser les appels aux API ; (d) les écritures de l'achat et de l'accès se font dans **une transaction** Firestore (deux événements simultanés).
- **Journalisation** : liste blanche de codes fixes (`billing.sync.ok`, `billing.google.notif`, `billing.apple.signature-invalide`, `billing.achat.deja-lie`…) avec au plus un résultat et un compteur ; **jamais** de jeton d'achat, de transaction signée, d'identifiant d'achat, d'UID, d'e-mail ni de numéro de commande. Les journaux techniques de Google gardent l'adresse IP et l'agent utilisateur de l'appelant (déjà à citer dans la politique, `deploiement-functions.md` §5).
- **Entrées** : taille maximale des champs reçus, listes de valeurs autorisées (store, produit), refus de tout autre `packageName` ou `bundleId`.
- **Réconciliation** : **pas de fonction planifiée en v1**. Le filet est (a) les nouvelles tentatives des stores (Apple : 5 tentatives sur 72 h [Vérifié] ; Pub/Sub : relivraison), (b) la **synchronisation à chaque ouverture de l'app** (§6.6 et §7.6) et le bouton « Actualiser/Restaurer ». Une fonction planifiée (trois tâches gratuites **par compte de facturation**, peut-être partagé avec Ghostrun, étude §2) n'est ajoutée que si les journaux montrent des états périmés.
- **Particularités à connaître** : (1) `recevoirNotificationsApple` est le **seul point d'entrée public** du projet : il doit rejeter sans coût tout ce qui n'est pas un POST de petite taille avant de lire Firestore, et `maxInstances` borne la dépense en cas d'abus ; (2) Apple réessaie sur 40x/50x, jamais sur 2xx : une notification d'un compte inconnu reçoit **200** (rien à faire), une signature invalide reçoit un refus ; (3) la région de livraison du déclencheur Pub/Sub n'est pas confirmée pour Paris (repli : Belgique) [Non vérifié].
- **Déploiement** : selon `deploiement-functions.md` : à la main par le PM, après CI verte, revue de sécurité et « oui » écrit pour ce commit ; jamais depuis la CI ; création préalable du compte de service, du secret, du sujet Pub/Sub et de la règle de durée de vie (§14).

---

## 11. Sécurité

| Sujet | Règle |
|---|---|
| **Clé dans le client** | **Jamais** : ni clé Apple, ni compte de service, ni secret de webhook dans l'app. Les apps n'ont que les identifiants publics du store. Test : recherche de secrets sur tout l'historique (gitleaks existant) ; revue du binaire |
| **Qui accorde** | Seule une fonction serveur, après lecture de l'état chez le store. Une réponse du client (« j'ai payé ») ne vaut jamais preuve |
| **Falsification** | Google : relecture à l'API ; Apple : vérification JWS (chaîne vers les racines Apple) puis relecture à l'API ; liaison par identifiant d'achat aléatoire (§5) |
| **Rejeu / jeton volé** | Fonctions appelables avec App Check ; option de jeton à usage unique recommandée pour ces fonctions sensibles (`deploiement-functions.md` §9) ; limitation de fréquence par compte |
| **Abus de coût** | `maxInstances`, entrées bornées, point d'entrée public minimal, alerte de budget et plafond de dépense des fonctions déjà prévus (`deploiement-functions.md` §1.1 et §1.2). Rappel : Firestore n'a pas de plafond |
| **Secret Apple** | Gestionnaire de secrets Google, lecture réservée au compte `billing-sync` ; le PM le saisit lui-même (jamais en clair dans une conversation) ; rotation = révoquer la clé dans App Store Connect, en créer une, remplacer le secret |
| **Données** | Aucune donnée de paiement chez Miff ; aucune position dans ces documents ; Analytics : jamais d'événement contenant un montant lié à une identité ni une position |
| **Revue** | Revue de code en contexte neuf + revue sécurité (comptes de service, secret, entrées, journaux) avant « prêt à merger » |

---

## 12. Textes obligatoires de l'écran d'achat (modèle ; **repris et précisés par `docs/tickets/paywall-v2.md` §5.4 et §5.5, qui fait foi**)

À composer avec les prix fournis par le store ; une phrase complète par cas et par plateforme (clés `ios_` / `android_` pour les différences) :

> **Miff+ annuel** : Essai gratuit de 7 jours, puis **{prix annuel} par an**, facturés en une fois à la fin de l'essai puis chaque année. Soit {équivalent mensuel} par mois (affichage secondaire). Reconduit automatiquement au même tarif tant que tu ne l'annules pas.
> **Miff+ mensuel** : **{prix mensuel} par mois**, sans engagement. Reconduit chaque mois tant que tu ne l'annules pas.
> **Annulation** : à tout moment dans les réglages de ton compte {App Store / Google Play}, avant la fin de la période en cours ; tu gardes l'accès jusqu'à la fin de la période payée. (iOS : pour un essai, au moins 24 heures avant sa fin, selon les conditions d'Apple [Vérifié].)
> **Liens** : Conditions d'utilisation · Politique de confidentialité · Restaurer mes achats · Continuer avec la version gratuite.

Ces textes ne sont **pas relus par un juriste** (risque assumé). Les prix et la devise ne sont jamais écrits dans les traductions : ils viennent du store.

---

## 13. Conformité des stores

| Exigence | Source | Où dans ce ticket |
|---|---|---|
| Achat numérique par le système de paiement du store ; pas de lien vers un autre moyen de paiement | Apple 3.1.1 [Vérifié] ; Google Payments policy, sections 2 et 4 [Vérifié] | Aucun paiement hors store (§2) |
| Mécanisme de restauration | Apple 3.1.1 et page Subscriptions [Vérifié] | §7.6, §6.6 |
| Valeur continue, au moins 7 jours, sur tous les appareils | Apple 3.1.2(a) [Vérifié] | Droit lié au compte, pas à l'appareil |
| Ne pas inscrire par erreur à plusieurs variantes | Apple 3.1.2(b) [Vérifié] | §7.10 |
| Décrire ce que la personne obtient pour le prix ; prix de renouvellement clair | Apple 3.1.2(c) et page Subscriptions [Vérifié] | §4.3, §12 |
| Prix facturé le plus visible ; équivalent mensuel subordonné | Apple [Vérifié] ; Google (« ne pas mettre principalement en avant un prix mensuel équivalent ») [Vérifié] | §4.3 |
| Essai : durée, prix ensuite, annulation, accès sans essai | Apple et Google [Vérifié] | §4.3, §12 |
| Liens CGU et politique dans l'app **et** dans les métadonnées Apple | Apple [Vérifié] | SRV-3 |
| Moyen d'annulation en ligne facile | Google [Vérifié] ; Apple (recommandé) [Vérifié] | « Gérer mon abonnement » |
| Suppression de compte dans l'app | Apple 5.1.1(v) [Vérifié] | Existant (AUTH-5) + rappel d'abonnement |
| Déclarations de collecte de données | Fiche de confidentialité Apple et « Data safety » Google : ajouter les **achats** (état d'abonnement lié au compte) | SRV-3 (release) [Non vérifié sur les pages exactes] |
| Première souscription Apple soumise **avec une nouvelle version de l'app** | Apple [Vérifié] | §14 |
| Fonctions de facturation Play disponibles seulement après publication d'une version contenant la Billing Library (piste interne suffit) | Google [Vérifié] | §14 |
| Taux de commission | Apple : 15 % avec le **Small Business Program** (jusqu'à 1 M$ de revenu net l'an précédent, comptés sur **tous les comptes associés** : le compte développeur est partagé avec Ghostrun) [Vérifié] ; Google : 15 % pour les abonnements dans les autres marchés, **10 % + 5 % de frais de facturation** dans l'EEE, au Royaume-Uni, aux États-Unis, en Australie et au Japon, et 15 % sur le premier million de dollars par an pour les développeurs inscrits [Vérifié] | §17 |
| TVA / taxes | Google : prix saisi hors taxe, taxes ajoutées automatiquement [Vérifié] ; **qui collecte et reverse la TVA** pour chaque store : [Non vérifié] | §14 (le PM lit la section fiscale de chaque console) |

---

## 14. Ce que doit faire le PM, pas à pas

**Règles** : aucune coordonnée bancaire ni fiscale n'est saisie par un agent ; aucune commande de déploiement n'est lancée par un agent ; les clés (`.p8`) ne passent ni par le dépôt, ni par une conversation, ni par un e-mail. Libellés recopiés de la documentation ; ils peuvent différer à l'écran : **en cas de doute, copier ce qu'on voit à Claude**. Vérifier à chaque page que le projet est **Miff / `miff-3d283`** (jamais Ghostrun) et que l'app est **Miff** (`com.tomessa.miff`).

### 14.1 Apple (compte Apple Developer, rôle Account Holder pour les accords)
1. **App Store Connect > Business (Affaires) > Agreements** : ligne **Paid Apps** > « View and Agree to Terms » > lire > **Agree** (Account Holder uniquement ; **irréversible**) [Vérifié]. Tant que l'accord n'est pas actif, on ne peut pas créer ni soumettre d'achats intégrés [Vérifié].
2. **Coordonnées bancaires et fiscales** : sections banque et fiscalité de Business, **saisies par vous seul** [exigence : Non vérifié sur la page lue, mais attendue par Apple pour les apps payantes].
3. **Small Business Program** : demander l'inscription (Account Holder, dernier accord Paid Apps accepté, liste des comptes développeur associés) ; effet **15 jours après la fin du mois fiscal d'approbation** [Vérifié].
4. **Statut de professionnel (« trader ») dans l'Union européenne** : renseigner ce que la console demande ; ces informations peuvent devenir publiques [Non vérifié].
5. **Produits** : Monetization > Subscriptions > « + » > groupe « Miff+ » > deux abonnements (durée 1 an et 1 mois ; prix ; pays ; **textes localisés FR et EN** ; capture d'écran et notes pour la revue Apple) [Vérifié]. **Ne pas activer le Partage en famille** (irréversible) [Vérifié]. Niveau : même niveau pour les deux [Vérifié]. **Offre d'introduction** : essai gratuit de 7 jours sur l'annuel (étapes non lues : Non vérifié). **Délai de grâce de facturation** : activer (emplacement du réglage : Non vérifié).
6. **Liens légaux** : Conditions d'utilisation et politique de confidentialité dans les métadonnées de l'app [Vérifié].
7. **Clé « In-App Purchase »** : Users and Access > Integrations > In-App Purchase > générer la clé (rôle Admin) ; télécharger le `.p8` **une seule fois** ; le ranger dans votre gestionnaire de mots de passe ; noter l'**identifiant de clé** et l'**Issuer ID** [Vérifié]. C'est **une clé différente** de celle de la CI TestFlight (`ASC_KEY_*`). Elle sera ensuite saisie par vous dans le gestionnaire de secrets Google (commande et étapes précisées dans `deploiement-functions.md` par SRV-7).
8. **Identifiant Apple de l'app** (numéro dans App Store Connect > Informations sur l'app) : à communiquer (non secret) ; **exigé pour la production** [Vérifié].
9. **URL des notifications serveur V2** : après le déploiement de `recevoirNotificationsApple`, saisir l'URL de production et l'URL de bac à sable dans les informations de l'app (version 2) [emplacement exact : Non vérifié].
10. **Comptes de test** : Users and Access > Sandbox > créer des **comptes sandbox** (ils ne fonctionnent qu'avec les apps du même compte développeur) [Vérifié].
11. **Première soumission** : l'abonnement et son groupe partent **avec une nouvelle version de l'app** [Vérifié] ; compter le délai de revue.

### 14.2 Google Play (console Play, compte développeur existant)
1. **Profil de paiement** : créer ou vérifier le profil dans le Centre de paiement Google, le lier au compte développeur ; **saisir vous-même** banque et fiscalité [Vérifié pour le lien ; saisies : vous seul].
2. **Publier une version contenant la Billing Library** sur n'importe quelle piste (la piste interne suffit) pour débloquer les fonctions de monétisation [Vérifié].
3. **Monétiser > Produits > Abonnements** > « Créer un abonnement » : identifiant `miff_plus` (**définitif**), nom ; **forfaits de base** `annuel` et `mensuel` (renouvellement automatique) ; prix saisis **hors taxe** ; **offre** « essai gratuit 7 jours » sur `annuel`, éligibilité « nouveau client » ; période de grâce et suspension du compte (valeurs par défaut) ; **activer** les forfaits [Vérifié].
4. **API Google Play** : activer « Google Play Android Developer API » dans le projet `miff-3d283` ; créer le compte de service `billing-sync` (même procédure que `notify-update`, `deploiement-functions.md` §1.4, **sans clé**) ; Play Console > Utilisateurs et autorisations > **inviter** `billing-sync@miff-3d283.iam.gserviceaccount.com` avec « Afficher les données financières, commandes et réponses aux enquêtes d'annulation » et « Gérer les commandes et abonnements » [Vérifié]. Délai de prise en compte : non précisé par Google.
5. **Notifications temps réel** : dans Pub/Sub (projet `miff-3d283`) créer le sujet `play-rtdn` ; ajouter `google-play-developer-notifications@system.gserviceaccount.com` avec le rôle **Pub/Sub Publisher** ; Play Console > Monétiser > Configuration de la monétisation > Notifications en temps réel : cocher « Activer », saisir `projects/miff-3d283/topics/play-rtdn`, **Envoyer un message de test**, choisir « abonnements et tous les achats annulés » [Vérifié].
6. **Testeurs** : Paramètres > Test de licence : ajouter les comptes Google de test ; piste de test fermée pour les installer [Vérifié]. Rappel : **12 testeurs pendant 14 jours** pour accéder à la production avec un compte personnel (`initialisation-technique.md`).
7. **Déclarations** : fiche « Data safety » et page de suppression de compte (déjà prévue) à compléter avec les achats (SRV-3).

### 14.3 Firebase / Google Cloud (après accord sur ce ticket)
Créer le compte de service et ses rôles, le sujet Pub/Sub, le secret Apple, la règle de durée de vie de `billingEvents` et `entitlements` (champ de purge), vérifier les alertes de budget (déjà posées) ; **chaque déploiement sur « oui » du PM**, commande tapée par lui. Détail à écrire dans `deploiement-functions.md` par la PR SRV-7.

---

## 15. Légal et risques assumés (SRV-3)

**Aucun texte n'est relu par un juriste ; risque assumé par le PM (décision du 2026-10-23).** À mettre à jour par les agents, sous la responsabilité du PM :
- **CGU article 9** (FR et EN) : offre Miff+ réelle (le prix n'est volontairement pas écrit, il est affiché par le store), **reconduction automatique** de l'annuel au même tarif, essai de 7 jours sur l'annuel, annulation chez le store, accès jusqu'à la fin de la période payée, **remboursement et droit de rétractation** : renvoi aux règles du store et réserve des droits du consommateur (déjà écrit), **fin de l'abonnement** (délai de mise en veille de Q5), rappel que **supprimer son compte ne résilie pas l'abonnement**.
- **Droit de rétractation (consommateurs UE)** : les conditions d'Apple prévoient un délai de 14 jours à compter de la réception du reçu et l'impossibilité de se rétracter une fois la livraison commencée à la demande de la personne, avec les règles de remboursement du store [Vérifié, page française des conditions des services multimédias Apple]. Pour Google, les règles régionales sont dans des pages liées que je n'ai pas lues [Non vérifié]. Le droit français et européen (exception pour le contenu numérique dont l'exécution a commencé avec accord exprès, rappels de reconduction tacite, résiliation en ligne) relève de la **connaissance générale** : **non vérifié** ici. Apple décrit son rôle comme celui d'un mandataire du fournisseur de l'app, qui n'est pas partie au contrat entre la personne et le fournisseur (sauf cas d'entités Apple précises) [Vérifié] : **Miff reste donc responsable de la relation de consommation**. Risque assumé.
- **Politique de confidentialité** : ajouter l'identifiant d'achat, le lien achat-compte, les identifiants de transaction (hors positions et paiements), Apple et Google comme sous-traitants de paiement, la durée de conservation (30 jours après suppression du compte), et l'absence de transmission d'informations d'identité aux stores. Le tableau « Abonnement Miff+ » existe (état, date de fin, origine).
- **Mentions légales / éditeur** : l'éditeur actuel est une personne physique (décision provisoire du 2026-10-24). **Vendre des abonnements de façon habituelle peut impliquer un statut professionnel à déclarer et le statut de « trader » exigé par les stores dans l'UE** : sujet à traiter par le PM auprès des organismes compétents ; **non vérifié ici, hors périmètre technique** (R12).
- **Page de suppression de compte** (AUTH-5-W) : ajouter le rappel d'abonnement non résilié.
- **Fiches des stores** : « abonnement avec essai », liens CGU/politique, retrait de toute mention de gratuité totale ; ne rien promettre de non livré (Q8 : **pas de « notifications intelligentes »**) ; « presque 30 % d'économie » (R8) ; notes pour les relecteurs sur la croix différée de 3 s (`paywall-v2.md` §7).

---

## 16. Critères d'acceptation (testables)

**Environnements de test** : Android : comptes **testeurs de licence**, cartes de test (« approuve toujours », « refuse toujours », « lente »), Play Billing Lab, pistes de test ; **renouvellements accélérés** (mensuel : 5 minutes ; annuel : 30 minutes ; essai : 3 minutes ; grâce : 5 minutes ; suspension : 10 minutes ; 6 renouvellements au plus) [Vérifié]. iOS : **comptes sandbox**, build TestFlight (toujours en bac à sable) sur l'iPhone 11 Pro du PM, cadence de renouvellement réglable (valeurs : Non vérifié). Le serveur est testé par des **fixtures** (réponses de l'API Google, notifications Apple signées avec des clés de test) et à l'émulateur.

### Serveur (SRV-7)
- [ ] **BK1** `preparerAchat` refuse un appel sans connexion, anonyme, ou sans App Check valide ; renvoie le **même** identifiant à chaque appel pour un même compte ; l'identifiant n'est pas dérivable de l'UID (deux comptes, deux valeurs aléatoires).
- [ ] **BK2** `synchroniserAchat` accorde un achat Google dont l'identifiant obscurci est celui de l'appelant ; **refuse** (sans rien écrire) quand il correspond à un autre compte ; **refuse** un mauvais `packageName` ; **refuse** un jeton inconnu ; limite d'un appel par minute et par compte.
- [ ] **BK3** Même chose pour Apple : transaction signée vérifiée ; `appAccountToken` égal ; mauvais `bundleId` ou signature invalide refusés.
- [ ] **BK4** Un achat déjà lié à un compte ne peut pas l'être à un autre ; message calme ; aucun détail sur l'autre compte.
- [ ] **BK5** Table de correspondance complète des états de §8 (une fixture par état et par store) : `active`, `expiresAt`, `state`, `trial`, `willRenew` conformes.
- [ ] **BK6** Une notification Google rejouée, ou reçue dans le désordre, laisse le même état final ; un message d'un autre package est ignoré ; un type inconnu est ignoré sans erreur.
- [ ] **BK7** Une notification Apple à signature invalide est refusée **avant** tout accès Firestore ; une notification valide d'un compte inconnu reçoit 200 et ne crée rien ; un POST trop gros ou une autre méthode sont refusés.
- [ ] **BK8** Un remboursement (Google : révocation/achat annulé ; Apple : REFUND, REVOKE) coupe l'accès tout de suite ; une annulation de remboursement Apple (REFUND_REVERSED) le rétablit.
- [ ] **BK9** Premier appel réel à l'API Google Play avec l'identité du compte de service, **sans clé** (à confirmer en conditions réelles, [Non vérifié] aujourd'hui).
- [ ] **BK10** `entitlements/{uid}` : un document `manual` valide n'est pas écrasé par un achat à l'échéance plus courte ; un document store n'est jamais modifié par un script manuel de test sans signalement.
- [ ] **BK11** Les collections `billing*` : `get`, `list`, création, mise à jour, suppression **refusés** à tout client, propriétaire compris (tests de règles ajoutés à `rules-test-plan.md`) ; `entitlements` reste en écriture refusée ; lectures de règles inchangées.
- [ ] **BK12** Journaux : aucun jeton, UID, identifiant d'achat, e-mail ni numéro de commande (test automatisé sur la liste blanche de codes).
- [ ] **BK13** `garde-couts.test.mjs` passe : compte de service dédié, `maxInstances` ≤ 5, mémoire ≤ 256 Mio, délai ≤ 60 s, pas d'instance minimale, nouvelle tentative autorisée seulement pour la fonction Pub/Sub.
- [ ] **BK14** Aucune clé dans le dépôt ni dans l'app ; le secret Apple n'est lisible que par `billing-sync`.
- [ ] **BK15** `supprimerDonneesAbonnement` supprime l'identifiant d'achat et les liens, marque l'accès inactif avec une date de purge à +30 jours.

### Android (SRV-8A)
- [ ] **AN1** Les prix et libellés affichés sont ceux de Google (changer le pays de test avec Play Billing Lab change la devise) ; aucun prix en dur dans le code ni dans les traductions.
- [ ] **AN2** Achat annuel avec essai (testeur de licence, carte « approuve toujours ») : l'accès s'allume en quelques secondes ; l'écran Abonnement dit « essai gratuit jusqu'au {date} » ; l'achat est **acquitté côté serveur** (aucun remboursement automatique après 3 minutes pour un testeur).
- [ ] **AN3** Une seconde tentative d'essai avec le même compte ne propose pas l'essai (hors réglage de test de Play Billing Lab).
- [ ] **AN4** Achat mensuel : pas d'essai proposé.
- [ ] **AN5** Paiement « lent » : message « en cours de validation », aucun droit, puis droit accordé à l'approbation ; refus : aucun droit, message calme.
- [ ] **AN6** Annulation dans Play : l'écran passe à « se termine le {date} » ; l'accès dure jusqu'à l'échéance, puis s'éteint.
- [ ] **AN7** Un document `entitlements` ancien (4 champs, `manual`) s'affiche comme avant ; un document avec champs inconnus ne plante pas.
- [ ] **AN8** Carte « refuse toujours » : passage en grâce (accès conservé, message sobre), puis en suspension (accès coupé, message sobre), puis récupération (accès rétabli) ou fin.
- [ ] **AN9** Réinstallation / nouvel appareil : l'abonnement est retrouvé à l'ouverture et par « Actualiser mon abonnement » ; un compte Miff différent avec le même compte Google reçoit le refus calme de BK4.
- [ ] **AN10** « Gérer mon abonnement » ouvre la page d'abonnement du produit dans Google Play ; **aucun** bouton « Arrêter » dans l'app.
- [ ] **AN11** L'écran d'achat n'est pas proposé à un compte déjà abonné ; le paywall ne s'affiche qu'à l'action bloquée prévue par la spec.
- [ ] **AN12** Mode avion, serveur injoignable ou fonction en erreur : message neutre, rien de bloquant, aucun plantage ; achat déjà payé non perdu (resynchronisation à la prochaine ouverture).
- [ ] **AN13** Textes de §12 présents (FR et EN), prix de renouvellement lisible, liens CGU et politique, « Continuer avec la version gratuite » visible ; prix annuel plus visible que l'équivalent mensuel.
- [ ] **AN14** Suppression de compte avec abonnement : rappel + « Gérer mon abonnement » ; pendant l'essai, phrase renforcée.
- [ ] **AN15** Aucune clé ni secret dans l'APK ; Analytics sans donnée de position ; CHANGELOG et README à jour ; écarts avec les maquettes listés dans la PR.

### iOS (SRV-8I)
- [ ] **IO1** Mêmes critères que AN1 à AN15 vérifiés sur TestFlight (iPhone 11 Pro, compte sandbox), avec les différences de §7.
- [ ] **IO2** Au lancement, l'écoute des transactions est active **avant** l'affichage du premier écran ; une transaction laissée non terminée est reprise au lancement suivant.
- [ ] **IO3** « Restaurer mes achats » (action de la personne) retrouve l'abonnement ; **aucune** restauration automatique ne déclenche la fenêtre d'authentification Apple au lancement.
- [ ] **IO4** Une transaction « partagée en famille » n'accorde rien (Q6).
- [ ] **IO5** « Gérer mon abonnement » ouvre la gestion système.
- [ ] **IO6** Le build `Release` ne contient aucun outil de diagnostic d'achat ; `PrivacyInfo.xcprivacy` à jour.
- [ ] **IO7** Durée des jobs macOS consignée dans la PR (minutes précieuses).

### Textes légaux et déclarations (SRV-3)
- [ ] **LG1** CGU art. 9, politique (FR/EN) et pages web alignées sur §15 ; aucun texte ne prétend avoir été relu par un juriste.
- [ ] **LG2** Fiches Data safety / confidentialité Apple mises à jour (achats) ; fiches des stores sans promesse non livrée.
- [ ] **LG3** Liens CGU et politique présents dans les métadonnées Apple et dans l'app.

### Recette du PM (sur téléphone et dans les consoles)
- [ ] **P1** Sur Android (compte testeur), acheter l'annuel : essai affiché, accès Miff+ immédiat (3e lieu, 2e Miff, zone de danger), « Gérer mon abonnement » ouvre Google Play.
- [ ] **P2** Sur iPhone (TestFlight, sandbox), même parcours ; « Restaurer mes achats » fonctionne après réinstallation.
- [ ] **P3** Provoquer un remboursement ou une annulation de test : l'app reste calme et informe correctement.
- [ ] **P4** Console Firebase > Functions : fonctions en ligne, limites et compte de service conformes ; journaux sans donnée personnelle ; Firestore : `entitlements` à jour, `billing*` invisibles aux apps.
- [ ] **P5** Facture Firebase et budget : 0 € ou quelques centimes ; aucune dépense inattendue.
- [ ] **P6** Aucun achat réel n'a été effectué pendant les tests (relevé des consoles de vente).

---

## 17. Coûts

- **Outils** : aucun abonnement payant (pas de RevenueCat) ; Apple Developer (99 €/an) et Google Play (25 €) déjà existants ; Blaze à l'usage (décision du 2026-11-11).
- **Cloud Functions** : 2 millions d'invocations gratuites par mois, puis 0,40 $ le million [Vérifié]. Volume estimé : par abonné, 1 à 3 appels à l'achat, une notification par renouvellement (12 par an pour un mensuel, 1 pour un annuel) et quelques changements d'état : **moins de 20 000 invocations par mois pour 1 000 abonnés** (hypothèse). Coût : 0 $.
- **Firestore** : quelques lectures/écritures par événement, loin des quotas gratuits du jour.
- **Pub/Sub** : environ 1 Ko par notification Google (document Google [Vérifié]) ; tarifs de Pub/Sub et du gestionnaire de secrets : **[Non vérifié]** (pages illisibles) ; ordre de grandeur attendu : négligeable (quota gratuit mensuel de Pub/Sub, quelques secrets).
- **API Google Play** : « une utilisation inefficace peut conduire à des restrictions de quota » [Vérifié] : d'où la relecture ciblée (une par notification) et pas de scrutation.
- **Commissions** : Apple 15 % après inscription au Small Business Program (sinon plus) ; Google 15 % pour les abonnements, jusqu'à 1 M$ par an. Illustration (hypothèse non vérifiée : commission sur le prix hors taxe, TVA française de 20 %) : 59,88 € TTC ≈ 49,90 € HT, soit environ **42 €** après commission pour un abonné annuel. La TVA dépend du pays de l'abonné.
- **Alertes** : budget Google Cloud de 5 €/mois et plafond de dépense Cloud Functions déjà prévus ; toute facture Blaze supérieure à 5 €/mois déclenche une revue (`deploiement-functions.md` §8). **Signal pour changer** : si la maintenance du code de vérification devient trop lourde, ou au-delà de 2 500 $ de revenu mensuel (seuil gratuit de RevenueCat), réévaluer l'option B avec une dérogation écrite.

---

## 18. Découpage en PR et ordre

| PR | Contenu | Prérequis |
|---|---|---|
| **PREM-0** (docs) | Ce ticket + mises à jour de `docs/specs/paywall-premium.md` (statut), `docs/specs/README.md`, `data-model.md` (§2.3, §10, nouveaux §9.2), `premium-manuel.md` (règle « ne pas modifier un document store ») ; tickets `paywall-v2.md` et `prem-fin.md` | GO du PM |
| **PAY-UI-A** (Android) puis **PAY-UI-I** (iOS) | **Écran d'offre fidèle aux maquettes, états, variante sans achat, croix différée de 3 s** : `docs/tickets/paywall-v2.md`. **Livrés avant SRV-7/8** (aucune dépendance au serveur) | `paywall-v2.md` validé ; maquettes `design/exports/paywall/` |
| **SRV-7** (backend) | Cinq fonctions, tests (fixtures, émulateur), tests de règles `billing*`, `garde-couts`, procédure du compte de service, du sujet, du secret et de la règle de durée de vie dans `deploiement-functions.md`, journaux | Réponses Q1 à Q6 ; actions PM de §14.1 (7, 8) et §14.2 (3, 4, 5) |
| **SRV-8A** (Android) | Billing Library, **achat branché sur l'écran de PAY-UI-A** (pas de nouvel écran d'offre), écran Abonnement, restauration, rappel de suppression de compte ; textes FR/EN ; clés de traduction `settings_subscription_*` | SRV-7 déployé sur « oui » ; **PAY-UI-A livré** ; maquettes du paywall reçues (règle du 2026-10-28) ; maquettes de l'écran Abonnement à demander au PM |
| **SRV-8I** (iOS) | StoreKit 2, mêmes écrans, `Transaction.updates`, restauration par action | SRV-8A (parité), compte sandbox, TestFlight |
| **SRV-3** (textes) | CGU art. 9, politique, pages web, déclarations des stores | Q5, Q8 ; parallèle à SRV-8 |
| **PREM-FIN** (suite) | Mise en veille de **30 jours** (décidée), préavis, choix de ce qu'on garde, suppression des excédents, « Prévenir mes proches » (`miff.md` R9) : cadrage dans `docs/tickets/prem-fin.md` | Q5 tranchée ; **avant l'ouverture publique** |

Process par ticket (CLAUDE.md) : challenge, validation PM, dev Android puis iOS (une branche et une PR par plateforme, jamais de push sur `main`), revue de code en contexte neuf + revue sécurité, QA, recette PM, merge sur « oui ». Statuts Notion changés par Claude selon la décision du PM du 2026-11-06.

---

## 19. Questions pour le PM (8) avec valeur par défaut recommandée

**TOUTES TRANCHÉES le 2026-11-12 : « Ok pour tes recos » : voir « Décisions du PM, 2026-11-12 » en tête du ticket.** Les textes ci-dessous sont conservés comme justification ; chaque valeur par défaut recommandée est maintenant une décision.

**Q1. Validation des achats : directe (Cloud Functions) ou RevenueCat ?** — **TRANCHÉE : directe.**
*Défaut recommandé : directe.* RevenueCat exige une clé JSON de compte de service Google et la clé Apple, stocke les données aux États-Unis, et n'évite pas la fonction serveur (§3). Gain de RevenueCat : moins de code. Si vous préférez RevenueCat, il faut une **dérogation écrite** à `secrets.md` et un accord sur les transferts hors UE.

**Q2. Essai de 7 jours : qui y a droit, et comment limiter les abus ?** — **TRANCHÉE : annuel seulement, un par compte du store, aucun blocage ajouté.**
*Défaut recommandé :* annuel seulement (déjà décidé) ; **un essai par compte Apple (groupe d'abonnement) et par compte Google**, c'est **le store qui décide** ; Miff n'ajoute aucun blocage (pas d'identifiant d'appareil, pas de liste noire), et ne stocke pas « essai déjà utilisé ». Abus résiduel accepté : ouvrir un nouveau compte Apple/Google pour un nouvel essai ; coût pour Miff quasi nul. Se réinscrire sur Miff avec le même compte du store ne redonne pas d'essai.

**Q3. Remboursement : que fait l'app ?** — **TRANCHÉE : accès coupé à la confirmation du store, message neutre.**
*Défaut recommandé :* **accès coupé tout de suite** quand le store confirme (Google : révocation/achat annulé ; Apple : remboursement/révocation), message neutre (« Ton abonnement Miff+ a été remboursé : les avantages ne sont plus actifs »), **aucune donnée supprimée sur le moment** (même traitement que l'expiration, Q5) ; si Apple annule le remboursement, l'accès revient.

**Q4. Paiement refusé : période de grâce et suspension ?** — **TRANCHÉE : période de grâce activée.**
*Défaut recommandé :* **activer la période de grâce** chez les deux stores (Google : valeur par défaut, à lire dans la console, 7 jours attendus [Non vérifié] ; Apple : durée proposée par la console) : **accès conservé** pendant la grâce, message sobre ; puis **suspension** (accès coupé) tant que le paiement n'est pas réglé ; ne pas activer la mise en pause volontaire de Google. Aucune notification push Miff en v1 (les stores relancent eux-mêmes la personne).

**Q5. À la fin de l'abonnement, que devient ce qui dépasse la formule gratuite (Miffs au-delà de 1, lieux au-delà de 3, zones de danger) ?** — **TRANCHÉE : 30 jours de mise en veille avant suppression (ticket PREM-FIN).**
*Défaut recommandé :* la décision du 2026-10-24 (**suppression des derniers ajoutés**) est **conservée**, mais **avec 30 jours de mise en veille** avant suppression : pendant ces 30 jours, rien n'est supprimé ; les lieux en excédent ne déclenchent plus de statut (grisés « Miff+ nécessaire »), la Miff en excédent est en lecture seule, un message calme propose de **choisir ce qu'on garde** et de **prévenir ses proches** ; si la personne se réabonne, tout est rétabli. Suppression ensuite (ticket **PREM-FIN**, avant l'ouverture publique). **Tant que PREM-FIN n'existe pas, rien n'est supprimé automatiquement** (comportement actuel). Impact : CGU art. 9 et politique à aligner (le délai de 30 jours n'y figure pas). Alternative moins radicale : ne jamais supprimer, seulement mettre en lecture seule.

**Q6. Un achat pour plusieurs comptes Miff, ou pour la famille ?** — **TRANCHÉE : un achat = un compte Miff.**
*Défaut recommandé :* **un achat = un compte Miff** ; une seconde tentative avec un autre compte Miff reçoit un refus calme et le contact du support ; transfert fait **à la main par le PM** pour l'instant. **Partage en famille d'Apple et bibliothèque familiale de Google : non activés** (Apple : irréversible une fois activé). À reconsidérer si un usage « un abonnement pour toute la famille » ressort des tests.

**Q7. Prix et identifiants : valider les noms de produits et le prix annuel côté Apple.** — **TRANCHÉE : noms validés ; prix 59,88 € / 4,99 € conservés ; si Apple n'a pas ce palier, palier le plus proche à confirmer en App Store Connect (seul point qui deviendrait bloquant, à ce moment-là).**
*Défaut recommandé :* identifiants `miff_plus` (Google, forfaits `annuel` et `mensuel`) et `com.tomessa.miff.plus.annual` / `.monthly` (Apple), **définitifs** une fois créés ; vérifier si **59,88 €** existe chez Apple avant de créer le produit ; sinon, **choisir entre 59,99 € côté Apple (et 59,88 € côté Google ?) ou 59,99 € partout** : je recommande le **même prix partout** pour ne pas créer deux offres différentes, et de reporter l'écart dans la spec Paywall et dans « Économisez 30 % » (le calcul réel change légèrement).

**Q8. Que liste-t-on dans l'offre au moment de l'achat ?** — **TRANCHÉE : seulement ce qui est livré ; « Notifications intelligentes » retiré.**
*Défaut recommandé :* **seulement ce qui est livré** : Miffs illimitées, lieux de confiance illimités, zones de danger (qui s'affichent en rouge pour les proches mais sans notification aujourd'hui, CGU art. 6). **Retirer « notifications intelligentes »** de l'écran d'achat et des fiches des stores tant qu'elles n'existent pas (les stores exigent de décrire ce que la personne obtient pour le prix ; la promesse d'une fonction absente est un risque de rejet et de défiance). À ajouter quand elles seront livrées.

---

## 20. Risques

| # | Risque | Effet | Parade |
|---|---|---|---|
| R1 | Fuite de la clé Apple `.p8` | Lecture des statuts d'abonnement de l'app (pas de paiement) | Secret Google, accès restreint, rotation décrite, jamais dans une conversation |
| R2 | `billing-sync` ignore les règles Firestore (accès à toute la base) | Détournement de la fonction = accès aux données | Code court, entrées bornées, compte dédié sans clé, tests, revue sécurité ; granularité par collection à rechercher (étude §3.4) |
| R3 | Point d'entrée public (notifications Apple) | Appels abusifs, coût | Rejet précoce, `maxInstances`, alertes |
| R4 | Notification perdue ou en retard | Accès éteint à tort ou maintenu trop longtemps | Nouvelles tentatives des stores, synchronisation à chaque ouverture, bouton Actualiser/Restaurer |
| R5 | Prix Apple 59,88 € inexistant | Offre différente de la décision | Q7 tranchée : palier le plus proche, à confirmer en App Store Connect |
| R6 | Maquette non conforme aux règles de prix des stores | Rejet de la revue | §4.3, maquettes PNG avant dev |
| R7 | Promesse de fonctions non livrées | Rejet, défiance, litige | Q8 |
| R8 | Pourcentage d'économie (28,6 % réel) | Mention publicitaire inexacte | Remplacé par « presque 30 % d'économie » (2026-11-07) ; à recalculer si les prix du store changent (Q7) |
| R21 | Croix du paywall différée de 3 s (décision du PM, 2026-11-12) | Rejet à la revue, avis négatifs, perception de pratique trompeuse | **Risque assumé par le PM** ; parades et détail dans `docs/tickets/paywall-v2.md` §7 |
| R9 | Abus de l'essai (plusieurs comptes de store) | Quelques essais gratuits | Q2 : accepté |
| R10 | Un seul projet Firebase : les achats de test (bac à sable, testeurs) accordent un vrai Miff+ en base | Accès gratuit aux testeurs externes | Marque `test` ; le projet de développement séparé (décision déjà prise) permettra de refuser les achats de test en production |
| R11 | Suppression de Miffs à la fin de l'abonnement | Des proches perdent leur cercle | Q5 : mise en veille, préavis, choix, « Prévenir mes proches » ; PREM-FIN avant l'ouverture publique |
| R12 | Statut du vendeur (personne physique), statut de « trader » UE, fiscalité | Blocage de compte, sanction | À traiter par le PM auprès des organismes compétents ; hors périmètre technique ; non vérifié |
| R13 | Textes non relus (rétractation, reconduction tacite, résiliation) | Réclamation de consommateur | Risque assumé (2026-10-23) ; renvoi aux règles des stores ; médiateur déjà prévu |
| R14 | Blaze : Firestore sans plafond ; compteur Scheduler partagé avec Ghostrun | Dépense imprévue | Alertes, surveillance (`surveillance-quotas.md`), pas de fonction planifiée en v1 |
| R15 | iOS non testable en local | Bugs découverts tard | Fixtures serveur, TestFlight, parité Android d'abord |
| R16 | Première soumission Apple liée à une version de l'app, revue des achats | Délai | Prévoir la soumission tôt (§14.1) |
| R17 | Règle de compatibilité ABO-1 (`source` autre que `manual` = « Offre en cours ») | Affichage incohérent | Remplacé par le tableau de §8 ; critère AN7 |
| R18 | Compte de test Gmail / Apple utilisé pour de vrais achats | Débits réels | Cartes de test uniquement ; Play Billing Lab ; critère P6 |
| R19 | Commission réelle différente de 15 % (inscription oubliée) | Marge réduite | §14.1 point 3 |
| R20 | Horloge : l'accès est décidé sur l'heure du serveur | Écart de quelques minutes à l'échéance | Écart accepté ; la période de grâce du store couvre le cas normal |

---

## 21. Sources (lues le 2026-11-12)

**Google Play** : [subscriptionsv2.get](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2/get) · [SubscriptionPurchaseV2 et états](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2) · [Notifications temps réel (référence)](https://developer.android.com/google/play/billing/rtdn-reference) · [Configuration (compte, API, RTDN)](https://developer.android.com/google/play/billing/getting-ready) · [Démarrage de l'API Developer](https://developers.google.com/android-publisher/getting_started) · [Cycle de vie des abonnements](https://developer.android.com/google/play/billing/lifecycle/subscriptions) · [Abonnements : forfaits, offres, remplacement](https://developer.android.com/google/play/billing/subscriptions) · [Intégration (achat, accusé, rejet)](https://developer.android.com/google/play/billing/integrate) · [Tests](https://developer.android.com/google/play/billing/test) · [Offres et éligibilité](https://support.google.com/googleplay/android-developer/answer/12154973) · [Créer un abonnement](https://support.google.com/googleplay/android-developer/answer/140504) · [Politique des abonnements](https://support.google.com/googleplay/android-developer/answer/9900533) · [Politique de paiements](https://support.google.com/googleplay/android-developer/answer/9858738) · [Commissions](https://support.google.com/googleplay/android-developer/answer/112622) · [Authentifier les abonnements push Pub/Sub](https://docs.cloud.google.com/pubsub/docs/authenticate-push-subscriptions) · [Déclencheurs Pub/Sub des fonctions](https://docs.cloud.google.com/functions/docs/calling/pubsub) · [Fonctions Firebase, événements Pub/Sub](https://firebase.google.com/docs/functions/pubsub-events) · [Tarifs Firebase](https://firebase.google.com/pricing)
**Apple** : [Règles de la revue (3.1.1, 3.1.2, 5.1.1(v))](https://developer.apple.com/app-store/review/guidelines/) · [Abonnements : parcours d'achat et métadonnées](https://developer.apple.com/app-store/subscriptions/) · [Small Business Program](https://developer.apple.com/app-store/small-business-program/) · [Accords](https://developer.apple.com/help/app-store-connect/manage-agreements/sign-and-update-agreements) · [Créer des abonnements](https://developer.apple.com/help/app-store-connect/manage-subscriptions/offer-auto-renewable-subscriptions) · données de documentation : types de notification, réponse aux notifications (200 à 206, 5 tentatives), statuts, champs des transactions et des informations de renouvellement, `appAccountToken`, `Transaction.updates`, `AppStore.sync()`, `isEligibleForIntroOffer`, Partage en famille, tests en bac à sable · [Bibliothèque serveur officielle](https://github.com/apple/app-store-server-library-node) · [Conditions des services multimédias Apple (FR)](https://www.apple.com/legal/internet-services/itunes/fr/terms.html)
**RevenueCat** : [Tarifs](https://www.revenuecat.com/pricing/) · [Webhooks](https://www.revenuecat.com/docs/integrations/webhooks) · [Identifiants Google Play](https://www.revenuecat.com/docs/service-credentials/creating-play-service-credentials) · [Clé Apple](https://www.revenuecat.com/docs/service-credentials/itunesconnect-app-specific-shared-secret/in-app-purchase-key-configuration) · [Confidentialité](https://www.revenuecat.com/privacy/)
**Dépôt** : `CLAUDE.md` ; `docs/specs/paywall-premium.md` ; `docs/tickets/initialisation-technique.md` (§3, §6, §7) ; `docs/dev/etude-serveur-notifications-premium.md` (§3.4, §8) ; `docs/dev/deploiement-functions.md` ; `docs/dev/premium-manuel.md` ; `docs/dev/secrets.md` ; `backend/data-model.md` (§2.3, §6, §10, §13) ; `backend/firestore.rules` (`entitlements`) ; `docs/tickets/feed-profil-reglages.md` (ABO-1, Q7, Q9, B5, B15) ; `docs/tickets/suppression-compte.md` (§7.7) ; `docs/tickets/miff.md` (R9) ; `docs/legal/README.md`, `cgu.fr.md` (art. 9), `politique-confidentialite.fr.md`.

**Non lu ou non vérifié (liste unique)** : tarifs Pub/Sub et gestionnaire de secrets ; authentification sans clé de l'API Google Play ; région du déclencheur Pub/Sub à Paris ; existence du prix de 59,88 € chez Apple ; emplacement de plusieurs réglages Apple (délai de grâce, offre d'introduction, URL de notification) ; qui collecte la TVA pour chaque store ; règles de rétractation de Google Play ; droit français et européen (rétractation, reconduction tacite, statut de vendeur, « trader ») ; cadence de renouvellement en bac à sable Apple ; valeur par défaut de la période de grâce ; éligibilité à l'essai d'Apple quand un abonnement mensuel payant a précédé ; granularité par collection du rôle Firestore.

*Ce document ne modifie ni le code, ni les règles Firestore, ni Notion, ni les textes légaux, et ne prétend à aucune relecture juridique : les points RGPD (sous-traitants Apple et Google, données d'achat), consommateurs (rétractation, reconduction tacite), fiscaux et de statut du vendeur restent des risques assumés par le PM (décision du 2026-10-23).*
