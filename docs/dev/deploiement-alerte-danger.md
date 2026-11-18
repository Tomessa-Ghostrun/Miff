# Déployer l'alerte « Je suis en danger » : règles, puis fonction (procédure du PM)

**À qui s'adresse ce document** : au PM, qui tape lui-même les commandes ci-dessous sur son poste. **Aucun agent ne déploie** (même avec un « oui », l'outil de permissions de Claude Code bloque `firebase deploy` : ne pas contourner). Aucune commande de ce document n'est exécutée par la CI.

**Ticket** : `docs/tickets/alerte-danger.md` (AD-B1 règles et modèle, AD-B2 fonction `notifierAlerte`). **Modèle de données** : `backend/data-model.md` §2.18 et §2.19. **Procédures de base** (à lire une fois) : `docs/dev/deploiement-regles.md` et `docs/dev/deploiement-functions.md`.

**Coût** : règles 0 € ; fonction : quelques centimes par mois au plus (`backend/data-model.md` §12, « Coût de l'alerte »), 0 € tant que les alertes restent rares. Aucun moyen de paiement à ajouter (Blaze est déjà actif).

---

## 0. L'ordre, et pourquoi (à lire avant tout)

| # | Quoi | Quand | Pourquoi dans cet ordre |
|---|---|---|---|
| **0** | Poser **les deux règles TTL** : `alerts.purgeAt` et `quota.expireAt` (section 1.2) ; **aucun compte à créer** | **AVANT le déploiement des règles** (étape 1), sans risque (rien n'est en ligne) | **La règle TTL `quota.expireAt` est la seule façon de purger un compteur orphelin** (suppression de compte dans les 24 h qui suivent une alerte : la règle refuse de supprimer le compteur) ; elle doit exister **avant** que le premier compteur puisse être écrit, donc avant les règles |
| **1** | **Déployer les règles Firestore** (section 2) | **Avant** toute application (APK, TestFlight) qui écrit une alerte | Sans les règles, l'écriture d'une alerte est refusée (`permission-denied`). Les règles sont **sans effet** tant qu'aucune application n'écrit d'alerte |
| **2** | Livrer l'APK Android puis le build TestFlight iOS (hors de ce document : `android-dev`, `ios-dev`) | Après l'étape 1 | |
| **3** | **Déployer la fonction `notifierAlerte`** (section 3) | **EN DERNIER**, quand l'APK et TestFlight sont prêts, les textes légaux publiés (AD-L) et ta recette prête | **C'est l'interrupteur de mise en service des notifications** : tant qu'elle n'est pas en ligne, une alerte apparaît dans le Feed des proches **mais aucun push ne part** (comportement prévu, testé) |

Chaque déploiement exige **ton « oui » explicite dans la conversation avec Claude, pour le commit annoncé**, la CI verte et la revue de sécurité faite (checklists des deux procédures de base).

**Rappel du dernier état en ligne** (information du PM, non vérifiable par l'agent) : règles et fonction déployées jusqu'au commit **`b5f0d8b0`** (2026-11-18, zone de danger demandée sans Miff+). **Entre `b5f0d8b0` et `main`, `backend/firestore.rules` et `backend/functions/` ne diffèrent que par cette PR** (vérifié par l'agent le 2026-11-18) : ce déploiement publie donc **uniquement** l'alerte. À re-vérifier avant chaque déploiement (commande en section 2, étape B).

---

## 1. À faire UNE SEULE FOIS avant (sans risque : rien n'est publié)

### 1.1 Compte de service : AUCUN nouveau compte (décision du PM du 2026-11-18)

La fonction `notifierAlerte` **réutilise le compte déjà en service de `notifierUpdate`** : `notify-update@miff-3d283.iam.gserviceaccount.com` (adresse écrite dans le code). **Rien à créer, aucun rôle à ajouter, aucune clé.**

Les **4 rôles de projet** qu'il porte déjà couvrent exactement ce dont la nouvelle fonction a besoin (vérifié par l'agent le 2026-11-18, d'après ce que fait le code) :

| Besoin de `notifierAlerte` | Rôle déjà porté |
|---|---|
| Lire les fiches de membres, les sessions, les jetons et la Miff ; écrire son état `notifState/{miffId}_alertes` ; supprimer un jeton que Google dit inconnu | **Utilisateur Cloud Datastore** (`roles/datastore.user`) |
| Envoyer les notifications | **Administrateur de l'API Firebase Cloud Messaging** (`roles/firebasecloudmessaging.admin`) |
| Être déclenchée par l'écriture d'une alerte | **Récepteur d'événements Eventarc** (`roles/eventarc.eventReceiver`) |
| Écrire ses journaux | **Rédacteur de journaux** (`roles/logging.logWriter`) |

**Aucun rôle ne manque.** Le seul droit à poser est **par service**, après le premier déploiement : `Cloud Run Invoker` du compte sur le service `notifieralerte` (section 3, étape D). Si une erreur « permission denied » apparaissait malgré tout au déploiement ou à l'envoi, **ne rien élargir** et copier le message à Claude.

**Contrôle (lecture seule, 30 secondes)** : Google Cloud > IAM : la ligne `notify-update@miff-3d283.iam.gserviceaccount.com` montre toujours **exactement 4 rôles** (pas 5) ; l'onglet « Clés » de son compte est **vide**.

### 1.2 Règle TTL sur `alerts.purgeAt` (suppression automatique des alertes terminées ou échues)

À poser **avant** la première alerte réelle (sans elle, rien ne casse : les documents restent, quelques centaines d'octets par alerte). Console Google Cloud > Firestore (projet `miff-3d283`) > **Time-to-live** > **Créer une règle** : groupe de collections **`alerts`**, champ **`purgeAt`**. En Cloud Shell (commande de la documentation Google, non vérifiée par l'agent ; en cas de doute, la console fait foi) :

```bash
gcloud firestore fields ttls update purgeAt --collection-group=alerts --enable-ttl --project=miff-3d283
```

**Seconde règle TTL, pour le quota d'alertes** (3 débuts par 24 h et par utilisateur, décision du PM du 2026-11-18) : groupe de collections **`quota`**, champ **`expireAt`** (le petit compteur privé `users/{uid}/quota/alerts` disparaît au plus ~3 jours après le dernier début : minimisation des données). Console : même écran, **Créer une règle** une seconde fois. Cloud Shell :

```bash
gcloud firestore fields ttls update expireAt --collection-group=quota --enable-ttl --project=miff-3d283
```

**Cette seconde règle est obligatoire AVANT le déploiement des règles** : une suppression de compte dans les 24 h qui suivent une alerte laisse un compteur **orphelin** (les règles refusent de le supprimer tant qu'un crédit est en cours : sinon le quota se remettrait à zéro) ; seule la règle TTL le purge (3 jours au plus). Il ne contient que des horodatages et un identifiant de Miff, pas de nom.

La règle existante `notifState.expireAt` couvre **déjà** le nouveau document d'état `notifState/{miffId}_alertes` (même groupe de collections `notifState`, même champ) : **rien à faire**.

### 1.3 Ce qui n'est PAS dans ce document (déjà prévu ailleurs)
Capacité **Time Sensitive** sur `com.tomessa.miff` et profils CI régénérés (ticket §11.4 et `docs/dev/apple-certificats-ci.md`, `ios-dev`) ; textes légaux et déclarations des stores (AD-L, `release-manager`) **publiés avant la mise en service de la fonction** ; validation de la réception push sur iPhone (SRV-2I).

---

## 2. Étape 1 : déployer les règles (`firestore.rules`)

**Précondition (revue du delta, m3)** : les **deux règles TTL de la section 1.2 sont posées et visibles dans la console AVANT cette étape** (en particulier `quota.expireAt`).

**A. Préparer le dossier de déploiement** (PowerShell ou Git Bash) :

```bash
cd C:\dev\miff-deploy
git fetch origin
git switch --detach origin/main
git log -1 --format=%H
```

Note les **8 premiers caractères** du commit et vérifie avec Claude que c'est bien celui qu'il a validé (la PR `backend/alerte-danger` doit être **fusionnée**).

**B. Voir exactement ce qui part** (lecture seule ; remplace `b5f0d8b0` par le dernier commit de règles que tu as déployé si ce n'est plus lui) :

```bash
git diff --stat b5f0d8b0 HEAD -- backend/firestore.rules backend/functions
```

Le résultat ne doit lister que les fichiers de la PR alerte (`firestore.rules`, `functions/src/*`). Si autre chose apparaît, **arrête-toi et copie la sortie à Claude**.

**C. Essai à blanc** (vérifie la syntaxe auprès du projet, **ne publie rien**) :

```bash
cd C:\dev\miff-deploy\backend
npm ci
npx firebase login:list
npx firebase deploy --only firestore:rules --project miff-3d283 --dry-run
```

**D. Déploiement réel** (seulement après ton « oui » écrit à Claude pour ce commit) :

```bash
npx firebase deploy --only firestore:rules --project miff-3d283 --message "AD-B1 alerte danger, commit <8 premiers caracteres>"
```

`--only firestore:rules` : ne publie **que** les règles. `--project miff-3d283` : cible explicite. Jamais `--force`, jamais `firebase use`, jamais `ghostrun-app`. La CLI affiche `Deploy complete!`.

**E. Contrôles en lecture seule après le déploiement**

| # | Vérification | OK si |
|---|---|---|
| 1 | Console Firebase > Firestore Database > **Règles** | La date et l'heure de publication ont changé ; le texte contient `match /alerts/{uid}` et les fonctions `alertShape`, `alertStart`, `alertRearm`, `alertEnd` |
| 2 | Avec une application **déjà installée** (sans alerte) : parcours habituel (publier une update, arrêter puis reprendre le partage, quitter puis rejoindre une Miff de test) | Tout fonctionne comme avant (aucun refus inattendu) : les lots d'arrêt, de départ et de retrait ne sont refusés que **s'il existe une alerte** |
| 3 | Le lendemain : Firestore Database > **Utilisation** | Pas d'écart de lectures : une alerte ne coûte rien tant qu'il n'y en a pas |
| 4 | **Retrait d'un membre EN ALERTE** (compte de test : le membre déclenche une alerte, la créatrice le retire) | Le retrait **réussit** et l'alerte disparaît du Feed. **Ce lot est le plus gros du modèle : 20 lectures de règles sur 20 sans cache, marge nulle** (18 sans alerte). **S'il est refusé** (« permission refusée » alors que tout est en règle) : ne rien forcer, copier le message à Claude : la solution de repli est de rendre la suppression de l'alerte au créateur possible hors de ce lot (règle précédente), à décider avec toi |
| 5 | Tu notes le déploiement | Date, commit déployé, version précédente (`b5f0d8b0`) |

**Rien d'autre n'est visible côté utilisateurs à ce stade** : les règles n'ont aucun effet tant qu'une application n'écrit pas d'alerte.

---

## 3. Étape 3 : déployer la fonction `notifierAlerte` (EN DERNIER)

**Avant (checklist)**

| # | Vérification | OK si |
|---|---|---|
| 1 | Les règles de l'étape 1 sont en ligne ; l'APK et le build TestFlight qui écrivent des alertes sont **prêts** à tester | Oui |
| 2 | Le compte `notify-update@…` (celui de `notifierUpdate`) porte ses **4 rôles** et **aucune clé** (section 1.1) : rien à créer | Oui |
| 3 | Les **deux** règles TTL (`alerts.purgeAt` et `quota.expireAt`) sont posées (section 1.2) | Visibles dans la console Firestore |
| 4 | Les textes légaux (AD-L) sont publiés et aucune fiche de store ne contredit la fonction | « Oui » du PM écrit |
| 4 bis | **PRÉCONDITION (revue de #274, M2) : un build TestFlight (et l'APK) contenant les clés de texte `notif_alert_*` en FR **et** EN, et la capacité Time Sensitive activée, AVANT de déployer la fonction.** Sans cela, une notification iOS d'alerte s'afficherait avec une clé brute à la place du texte (les clés `notif_alert_start_title`, `notif_alert_start_body`, `notif_alert_end_title`, `notif_alert_end_body` ne sont pas encore dans `Localizable.xcstrings` sur `main` au 2026-11-18) | Tu as installé le build sur l'iPhone et l'APK sur l'OPPO ; chaque appli a ces 4 textes en FR et EN |
| 4 ter | **La fonction se déploie APRÈS l'APK et TestFlight, livrés à TOUS les testeurs** (revue de #274, m4) : une application **ancienne** (qui ne supprime pas l'alerte dans ses lots) voit ses lots d'**arrêt du partage, de départ et de retrait REFUSÉS tant qu'une alerte existe dans la Miff** (l'alerte de la personne qui part, ou du membre retiré). Tant que la fonction n'est pas en ligne, personne ne reçoit de notification d'alerte et le risque reste confiné aux testeurs ; une fois la fonction en ligne, les alertes sont réelles : plus aucune ancienne version ne doit rester chez un testeur | | Tous les testeurs ont la dernière version |
| 5 | L'alerte de budget existe (Google Cloud > Facturation > Budgets) | `Miff 5 EUR par mois` |
| 6 | Tu notes l'état actuel : Console Firebase > **Functions** | Capture : seule `notifierUpdate` est listée |

**A. Dossier de déploiement** : comme en section 2, étape A (le commit doit contenir la PR alerte).

**B. Essai à blanc** (construit et vérifie, **ne publie rien**) :

```bash
cd C:\dev\miff-deploy\backend
npx firebase deploy --only functions:notifierAlerte --project miff-3d283 --dry-run
```

**C. Déploiement réel** (seulement après ton « oui » écrit pour ce commit) :

```bash
npx firebase deploy --only functions:notifierAlerte --project miff-3d283
```

- `--only functions:notifierAlerte` : **ne touche pas** `notifierUpdate` (aucune question sur des fonctions « absentes du code »).
- Si la CLI propose de supprimer des fonctions absentes du code : **réponds NON** et préviens Claude. Jamais `--force`.
- Si la CLI parle d'**instances minimales** ou d'un coût minimum : annule (`Ctrl+C`) et préviens Claude.
- **Erreur « Permission denied » liée à l'Eventarc Service Agent** : attends quelques minutes puis relance **exactement** la même commande (déjà rencontrée le 2026-11-13, sans danger).
- La commande se termine par `Deploy complete!`. En cas d'erreur, rien de cassé n'est mis en ligne.

**D. Après le premier déploiement : poser `Cloud Run Invoker` sur le seul service `notifieralerte`** (sans ce droit, Cloud Run **refuse chaque appel** d'Eventarc : « lacks {run.routes.invoke} permission » ; rien ne part). **Jamais** au niveau du projet, **jamais** « Allow public access ». En Cloud Shell :

```bash
gcloud run services add-iam-policy-binding notifieralerte --region=europe-west9 --member="serviceAccount:notify-update@miff-3d283.iam.gserviceaccount.com" --role="roles/run.invoker" --project=miff-3d283
```

(ou console : https://console.cloud.google.com/run, projet `miff-3d283`, cocher `notifieralerte` > Permissions > Add principal > `notify-update@miff-3d283.iam.gserviceaccount.com` > rôle **Cloud Run Invoker**.) Ne donne ce droit à aucun autre compte, sur aucun autre service.

**E. Contrôles en lecture seule après le déploiement**

| # | Vérification | Commande ou endroit | OK si |
|---|---|---|---|
| 1 | La fonction est en ligne | `npx firebase functions:list --project miff-3d283` | `notifierAlerte` **et** `notifierUpdate` listées, région **europe-west9**, 2e génération, Node.js 24 |
| 2 | Les limites sont appliquées | Console Firebase > Functions > `notifierAlerte` > détails (ou Cloud Run > `notifieralerte`) | Instances maximales **5**, mémoire **256 Mio**, délai **30 s**, instances minimales **0**, compte de service **`notify-update@miff-3d283…`** (jamais « Compute Engine default ») |
| 3 | Le droit d'appel est posé, **pas trop large** | Cloud Run > cocher `notifieralerte` > Permissions | `notify-update@…` porte **Cloud Run Invoker** ; **aucun** `allUsers` ni `allAuthenticatedUsers` ; la page IAM du projet montre toujours **4 rôles** pour ce compte (pas 5) |
| 4 | Aucune erreur | `npx firebase functions:log --project miff-3d283 --only notifierAlerte -n 30` | Aucune ligne `ERROR` ; **aucun** « The request was not authenticated… » ni « lacks {run.routes.invoke} permission » |
| 5 | **Essai de bout en bout, deux comptes de test dans la même Miff** (OPPO + iPhone) | Compte A : maintenir 5 s le bouton de l'écran Update ; compte B : attendre | B reçoit **« A a déclaré une alerte »** (FR ou EN selon son téléphone), **en moins de 2 minutes**, **sans position, ni nom de Miff, ni nom de famille** ; A ne reçoit rien ; les journaux montrent `notif.alerte.debut.bilan` (`resultat: ok`, `envoyes` = 1) |
| 6 | Fin manuelle | A : maintenir 5 s la bannière | B reçoit **« A a mis fin à son alerte »** ; journaux : `notif.alerte.fin.bilan` puis `notif.alerte.fin.duree` (durée en minutes) |
| 7 | Garde de la fonction (1 début par minute) | Journaux après deux débuts à moins d'une minute (compte de test, le 2e passe les règles : ré-armement impossible < 60 s, donc supprimer puis recréer) | Une ligne `notif.alerte.debut.ignore` avec `motif: plafond` ; **aucune erreur côté utilisateur** |
| 7 bis | **Quota de 3 débuts par 24 h (règles)** | Compte de test : déclencher 3 alertes (en terminer et supprimer chacune), puis une 4e | La **4e est refusée** par l'application avec un message **calme**, **les numéros d'urgence restent accessibles** ; la 4e n'apparaît pas dans le Feed des proches ; aucune ligne `notif.alerte` pour elle ; **c'est normal et voulu** (pas un incident). Le compte de test retrouve un crédit 24 h après son premier début de la série |
| 8 | Pas d'envoi à l'expiration | **Non vérifiable à la main en 7 jours** : garanti par le code (aucune fonction planifiée) et par les tests (`alerte-regles`, `alerte-declencheur`) ; à vérifier à l'échéance de la première vraie alerte : aucune ligne `notif.alerte.*` ce jour-là | Rien n'est journalisé ni envoyé |
| 9 | Pas de dépense | Google Cloud > Facturation > Rapports (le lendemain) | 0 € à quelques centimes |
| 10 | Tu notes le déploiement | Date, commit (8 caractères), version précédente | Noté dans la conversation avec Claude |

---

## 4. Arrêter ou revenir en arrière (si quelque chose ne va pas)

**Couper les notifications d'alerte tout de suite** (les alertes restent visibles dans le Feed, aucun push ne part) :

```bash
cd C:\dev\miff-deploy\backend
npx firebase functions:delete notifierAlerte --region europe-west9 --project miff-3d283
```

La CLI demande confirmation : **relis que le nom affiché est bien `notifierAlerte`** (jamais `notifierUpdate`). Pour la remettre : section 3, étapes B et C (le droit `run.invoker` est à reposer après un nouveau premier déploiement, étape D). Autre façon, sans supprimer : retirer le droit `run.invoker` du service `notifieralerte` (les appels sont refusés).

**Revenir aux règles d'avant** (seulement si l'application est cassée pour de vrais utilisateurs : l'ancienne version a les mêmes protections, sans l'alerte) :

```bash
cd C:\dev\miff-deploy
git switch --detach b5f0d8b0
cd backend
npx firebase deploy --only firestore:rules --project miff-3d283 --message "Retour arriere vers b5f0d8b0"
cd ..
git switch --detach origin/main
```

Effet : l'écriture d'une alerte est de nouveau refusée (`permission-denied`) ; les alertes **déjà écrites** deviennent illisibles (refus par défaut) jusqu'à leur suppression automatique ; aucune donnée n'est exposée. Détail et méthodes A à C : `deploiement-regles.md` §4 et `deploiement-functions.md` §7.

---

## 5. Coût et signal de bascule (pour que tu voies venir la facture)

- **Estimation** (pas une mesure) : 1 alerte pour 1 000 utilisateurs actifs et par jour = **2 invocations et ≈ 26 lectures par jour** : **0 €** (très en dessous des 2 millions d'invocations et des 50 000 lectures par jour offerts). **Pire cas d'abus** (borné **par les règles** : 3 débuts par utilisateur et par 24 h, plus 1 par minute dans la fonction) : 1 000 comptes qui abusent ≈ 40 000 lectures par jour au plus pour la fonction, **sous les 50 000 gratuites** ; 0 € à quelques centimes par jour. **Ce qui n'est PAS plafonné par la fonction** (revue de #274, m1) : une sortie avant réservation (compte seul dans sa Miff, Miff absente) coûte 3 à 5 lectures ; avant le quota, un compte seul dans sa Miff pouvait créer et supprimer son alerte en boucle (≈ 1 écriture par seconde : ≈ 1 € par jour au plus, borné par le budget d'alerte à 5 €) ; **depuis le quota, ce scénario est impossible (3 débuts par jour et par compte)**. Reste : plusieurs comptes (non empêchable) et des écritures refusées en boucle par un client modifié (≈ 4 à 5 lectures de règles facturées par tentative, bornées par App Check et le budget d'alerte). Détail : `backend/data-model.md` §12.
- **À regarder chaque semaine pendant la bêta** (1 minute) : `docs/dev/surveillance-quotas.md` §5 octies (invocations, notifications envoyées, **garde « 1 par minute » atteinte**, **taux d'alertes de moins de 2 minutes**, erreurs d'envoi, dépense).
- **Signaux de bascule ou de revue** : facture Blaze > **5 $ par mois** ; plus de **100 invocations par jour** de `notifierAlerte` sans alerte réelle ; plus de **5 lignes « plafond »** (1 par minute) par jour ; plus de **5 % d'alertes terminées en moins de 2 minutes** (revoir la durée du maintien, pas le serveur) ; erreurs d'envoi > **5 %**.

---

## 6. Ce que cette PR ne fait PAS (pour éviter les surprises)

- **Aucune** règle de position, de statut, de photo ou de lieu n'est modifiée ; les lots d'arrêt du partage, de départ, de suppression de la Miff gagnent **une lecture de règles** (l'alerte est supprimée dans le même lot) : les applications actuelles écrivent **une Miff par lot**, sans effet visible (`backend/data-model.md` §2.18, point 6, et §12).
- **Aucune** fonction planifiée : l'expiration à 7 jours ne notifie jamais (décision du PM du 2026-11-18, D19).
- **Aucune** donnée d'alerte dans Analytics, Crashlytics ou les journaux (journaux : nombres et mots fixes seulement).
- **Rien n'est déployé par cette PR** : ce document est la procédure que **tu** exécutes, sur ton « oui ».
