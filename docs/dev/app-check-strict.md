# App Check « appliqué » : checklist du PM (avant les testeurs externes)

**Contact et autorité de décision** : Tomessa MFOUKA, tomessa.mfouka@gmail.com (PM du projet Miff).

**À quoi ça sert, en une phrase** : App Check est un contrôle d'identité de l'application : une fois « appliqué », la base ne répond plus qu'aux vraies applications Miff (installées depuis Google Play ou TestFlight) ; un script ou une copie bricolée est refusé. C'est la **principale protection gratuite** contre l'abus de lectures décrit dans `surveillance-quotas.md` (déni de service : quelqu'un épuise les 50 000 lectures gratuites du jour et l'app s'arrête pour tout le monde).

**Coût** : 0 €. Les fournisseurs d'attestation (Play Integrity, App Attest) ont des quotas propres, voir « Points d'attention ».

**Le risque inverse** : si on l'applique trop tôt, on bloque aussi des testeurs légitimes (application installée hors Play, build de développement sans jeton de debug). D'où l'ordre ci-dessous, **qui ne se raccourcit pas** :

> **1. Enregistrer les apps → 2. Laisser « non appliqué » → 3. Mesurer → 4. Appliquer → 5. Vérifier.**

**Qui fait quoi** : toutes les étapes de console sont à faire **par le PM** ; aucun agent n'a accès à la console. Les deux applications doivent déjà envoyer des jetons (App Check est initialisé dans les builds du socle technique INIT-2 Android et INIT-4 iOS, mode non appliqué ; d'après les tickets, à confirmer sur la version installée sur tes appareils de test).

> Références Firebase consultées le 2026-10-23 : documentation « App Check » (produits compatibles, métriques, application), « Play Integrity provider » et « App Attest provider ». Les libellés exacts de la console peuvent changer : le chemin général (Sécurité > App Check) est celui de la documentation.

---

## Étape 1. Enregistrer les deux apps (console : Sécurité > App Check > onglet « Apps »)

| App | Identifiant d'app Firebase | Fournisseur | Ce qu'il faut fournir |
|---|---|---|---|
| Android | `1:29899146512:android:307ffd8ea71a4a37659195` | **Play Integrity** | L'**empreinte SHA-256** du certificat de signature de l'app. Avec « Play App Signing », c'est celle de la **clé de signature d'application** affichée dans la Play Console (pas celle de la clé d'envoi) : à vérifier au moment de l'enregistrement. Il faut aussi **lier le projet Firebase dans la Play Console** (Intégrité de l'app > API Play Integrity), en étant propriétaire du projet. |
| iOS | `1:29899146512:ios:94e30481497dd74c659195` | **App Attest** (DeviceCheck en secours) | Rien dans la console. Côté Apple : la capacité **App Attest** doit être activée sur l'identifiant d'app `com.tomessa.miff`, avec l'environnement `production` pour les builds TestFlight/App Store (App Check **refuse** les jetons de l'environnement « sandbox » d'App Attest). |

- Laisse la **durée de vie du jeton à la valeur par défaut (1 heure)** : plus court = plus de vérifications donc plus de latence et de quota.
- Si la console propose **Authentication** parmi les produits dans l'onglet « API » (la documentation le liste en « Preview »), note-le : il aura sa propre bascule à l'étape 4.
- Pour chaque build de **développement** (émulateur, simulateur, CI) : créer un **jeton de debug** (App Check > Apps > menu de l'app > « Gérer les jetons de débogage »). Ce jeton **ne va jamais dans le dépôt ni dans un message** (`docs/dev/secrets.md`). À terme, les jetons de debug n'existent que sur le projet de développement, pas sur `miff-3d283`.

## Étape 2. Laisser « non appliqué » (c'est le mode par défaut)

Dans l'onglet « API », les produits (Cloud Firestore, Authentication si présent) doivent afficher **« Non appliqué »**. **Ne clique pas sur « Appliquer » maintenant.** À ce stade, les requêtes sont comptées et classées, mais personne n'est refusé.

## Étape 3. Mesurer (au moins 2 jours d'usage réel, sur les appareils de test)

1. Distribue les builds par le **circuit officiel** : test interne Google Play pour l'OPPO Find X5 Lite (Android 13), TestFlight pour l'iPhone 11 Pro. Une application installée autrement (APK envoyé à la main) **échoue à Play Integrity** : ce n'est pas un défaut de l'app, c'est ce qu'App Check est censé repérer.
2. Fais un parcours complet sur chaque appareil : ouvrir l'app, se connecter, créer ou rejoindre une Miff, regarder le fil.
3. Console : Sécurité > App Check > onglet « API » > **Cloud Firestore** : dérouler la vue des métriques. Les requêtes sont classées en quatre catégories (définitions de la documentation Firebase) :

| Catégorie | Signification | Ce qu'on attend |
|---|---|---|
| **Vérifiées** | Requêtes avec un jeton valide. **Seules celles-ci seront acceptées après application.** | ~100 % |
| **Client obsolète** | Pas de jeton : version de l'app d'avant App Check | Proche de 0 % (l'app n'est pas encore publiée) |
| **Origine inconnue** | Pas de jeton et ne ressemble pas à une requête du SDK Firebase (clé d'API volée ou requête fabriquée) | 0 % ; toute valeur durable est un signal d'abus |
| **Invalides** | Jeton invalide (app imitée, émulateur non déclaré) | 0 % (sauf build de dev sans jeton de debug) |

4. Relève le tableau suivant à la fin de chaque journée de mesure :

| Date | Requêtes totales | % vérifiées | % client obsolète | % origine inconnue | % invalides | Cause des non-vérifiées (si connue) |
|---|---|---|---|---|---|---|
| | | | | | | |

5. Si des requêtes ne sont pas vérifiées, cherche la cause **avant** d'appliquer :

| Symptôme | Cause probable | Correction |
|---|---|---|
| « Invalides » depuis un simulateur ou un build debug | Jeton de debug absent ou non enregistré | Créer/enregistrer le jeton de debug de ce build (étape 1) |
| « Client obsolète » après installation d'une vieille version | Build d'avant App Check | Réinstaller le build récent |
| Android : non vérifiée alors que l'app vient de Play | Empreinte SHA-256 incorrecte, ou projet non lié dans la Play Console | Vérifier l'empreinte (clé de signature d'application) et la liaison |
| iOS : non vérifiée sur TestFlight | Capacité App Attest absente ou environnement `sandbox` | Activer la capacité, régler l'environnement sur `production`, reconstruire |
| « Origine inconnue » non expliquée | Quelqu'un appelle l'API sans l'app | Bon signe pour l'application stricte ; surveiller (voir `surveillance-quotas.md`) |

**Critère pour passer à l'étape 4** (à tenir pour acquis seulement si tout est vrai) : sur 24 h complètes, **~100 % des requêtes de tes appareils de test sont vérifiées**, chaque appareil de test a bien fait un parcours complet pendant la période, et les causes éventuelles de non-vérifiées sont comprises et corrigées. La documentation Firebase ne fixe aucun seuil chiffré ; si l'app n'est pas encore publiée, elle indique qu'il n'y a pas de client obsolète à protéger et que l'on peut appliquer sans attendre — le seuil de ~100 % reste notre garde-fou pour ne pas bloquer un testeur par erreur.

## Étape 4. Appliquer (une seule chose à la fois, jamais le jour d'un déploiement de règles)

1. Console : Sécurité > App Check > onglet « API » > **Cloud Firestore** > dérouler la vue des métriques > **Appliquer** > confirmer.
2. Attendre jusqu'à **15 minutes** pour que ce soit effectif (délai indiqué par la documentation).
3. **Authentication** : seulement si la console le propose, et **dans un second temps** (au moins un jour après Firestore, puis même cycle : mesurer puis appliquer). La documentation le classe « Preview » : après application, vérifier la **connexion par e-mail/mot de passe et par Google** sur les deux téléphones. Si une connexion échoue, **désactiver aussitôt** (voir « Retour arrière »).
4. **Jamais le même jour** qu'un déploiement de règles (`deploiement-regles.md`) : en cas de problème, on doit savoir lequel des deux en est la cause.

## Étape 5. Vérifier (critère R18 du ticket de durcissement)

| # | Vérification | OK si |
|---|---|---|
| 1 | Sur chaque appareil de test (build officiel) : se connecter, ouvrir le fil, créer une mise à jour | Tout fonctionne comme avant |
| 2 | Les builds de développement et de test | Fonctionnent (jeton de debug enregistré) |
| 3 | Une requête **sans jeton valide** est refusée | `qa-tester` / `android-dev` fournissent un build de test volontairement sans App Check ; il doit afficher le message neutre « Pas de connexion pour le moment » (jamais un message alarmiste) et la console doit compter des requêtes refusées. Si ce build n'existe pas encore, la démonstration reste à faire : ne coche pas R18 |
| 4 | Le lendemain | Console App Check : toujours ~100 % de requêtes vérifiées ; Firestore > Utilisation : pas de baisse inexpliquée des lectures (les testeurs ne sont pas bloqués) |

## Retour arrière (si un testeur légitime est bloqué)

Console : Sécurité > App Check > onglet « API » > produit concerné > **désactiver l'application** (repasser en « Non appliqué »). Effet en **15 minutes au plus**. Cherche ensuite la cause avec le tableau de l'étape 3, puis recommence à l'étape 4.

---

## Points d'attention

- **Quota Play Integrity** : la documentation Firebase indique un quota quotidien de **10 000 appels** pour le palier standard de Play Integrity. Chaque jeton demandé compte ; avec une durée de vie de 1 heure, un utilisateur actif en demande quelques-uns par jour. À surveiller dès quelques centaines d'utilisateurs (`surveillance-quotas.md`, section 3). Pour App Attest, l'accès dépend des quotas d'Apple.
- **Applications hors Google Play** : par défaut (`PLAY_RECOGNIZED`), une application non publiée sur Google Play ne reçoit pas l'étiquette requise et serait bloquée. Pour la bêta, **utiliser le test interne Play**, pas un APK envoyé à la main.
- **Limite honnête** : App Check n'arrête pas un attaquant équipé d'un vrai appareil ; il augmente son coût. Il ne remplace ni les règles Firestore, ni la surveillance quotidienne.
- **À mesurer** (ticket de durcissement §5.1) : si une requête refusée par App Check compte dans le quota de lectures Firestore. La documentation consultée ne le précise pas. Cette mesure se fait sur le **futur projet de développement**, jamais sur `miff-3d283`, avec ton accord et hors CI.
- **Appareils de test du PM** : iPhone 11 Pro (iOS 26.5.2) : App Attest est disponible depuis iOS 14 ; OPPO Find X5 Lite (Android 13) : l'intégrité d'appareil exigée peut exclure les utilisateurs d'Android 13 et plus qui n'ont pas installé depuis Google Play (documentation Play Integrity).
