# Déployer le journal de notifications (centre de notifications complet, CNC-B) : règles et règle TTL, puis fonctions (procédure du PM)

**À qui s'adresse ce document** : au PM, qui tape lui-même les commandes ci-dessous sur son poste. **Aucun agent ne déploie** (même avec un « oui », l'outil de permissions de Claude Code bloque `firebase deploy` : ne pas contourner). Aucune commande de ce document n'est exécutée par la CI.

**Ticket** : `docs/tickets/centre-notifications-complet.md` (§8 « journal serveur », §8.9 « ordre de mise en ligne », décisions du PM du 2026-11-19). **Modèle de données** : `backend/data-model.md` §2.21 (et §2.16, §2.19 pour les budgets d'écriture). **Règles** : `backend/firestore.rules`, point 18. **Procédures de base** (à lire une fois) : `docs/dev/deploiement-regles.md`, `docs/dev/deploiement-functions.md`, et `docs/dev/deploiement-alerte-danger.md` (même enchaînement).

**Ce que ça change, en une phrase** : à chaque notification envoyée (« a donné des nouvelles », début ou fin d'alerte), le serveur note une ligne de 30 jours par destinataire (**type, Miff, identifiant de la personne, date : ni prénom, ni photo, ni lieu**) dans `users/{uid}/notifications`, que l'app relit pour que le centre de notifications soit complet.

**Coût** : règles 0 € ; **+1 écriture par destinataire et par notification** : **0 $ jusqu'à environ 1 000 utilisateurs actifs par jour, 3 à 4 $ par mois vers 10 000** (estimation, pas une mesure : `backend/data-model.md` §12 « Coût du journal »). Aucun moyen de paiement à ajouter (Blaze est déjà actif).

**Aucun nouveau compte de service, aucun rôle de plus, aucune clé, aucune nouvelle fonction.** Le compte `notify-update@miff-3d283.iam.gserviceaccount.com` écrit dans Firestore avec le rôle qu'il porte déjà (`roles/datastore.user`).

---

## 0. L'ordre, et pourquoi (à lire avant tout)

| # | Quoi | Quand | Pourquoi dans cet ordre |
|---|---|---|---|
| **1a** | **Poser la règle TTL** `notifications.expireAt` (section 1) | **AVANT** les règles et les fonctions | Sans elle, les lignes ne s'effaceraient jamais : la durée de 30 jours annoncée aux utilisateurs ne serait pas tenue. Sans risque : rien n'est encore écrit |
| **1b** | **Déployer les règles Firestore** (section 2) | Juste après 1a | Les règles n'ont **aucun effet visible** tant qu'aucune ligne n'existe. Elles doivent être en ligne **avant** que l'app (Android, iOS) lise ou supprime le journal |
| **2** | **Publier le texte légal mis à jour** (site, lot CNC-J, **par le PM**) | **AVANT** le redéploiement des fonctions | La politique de confidentialité publiée décrit aujourd'hui un centre de notifications **local** ; dès que les fonctions écrivent, une donnée personnelle de plus est conservée sur nos serveurs. Décision du PM du 2026-11-19 : **le texte précède la mise en service** |
| **3** | **Redéployer `notifierUpdate` et `notifierAlerte`** (section 3) | **EN DERNIER côté serveur**, après la publication du texte | **C'est l'interrupteur de mise en service du journal** : tant que les fonctions ne sont pas redéployées, rien n'est écrit. Les lignes écrites avant la sortie des apps ne sont pas perdues : elles seront relues (30 jours) |
| **4** | App Android, puis app iOS (APK, TestFlight ; hors de ce document : `android-dev`, `ios-dev`) | Après l'étape 3 | Une app **ancienne** ignore la clé `entryId` du message et le journal : **aucune régression** dans les deux sens. **Rétroactivité nulle** : les notifications d'avant l'étape 3 ne sont dans aucun journal |

Chaque étape exige **ton « oui » explicite dans la conversation avec Claude, pour le commit annoncé**, la CI verte et la revue de sécurité faite (checklists des procédures de base). Entre deux étapes, tu peux t'arrêter sans rien casser.

**Dernier état en ligne** (information du PM, non vérifiable par l'agent) : règles déployées jusqu'au commit **`13ec6177`** (alerte, 2026-11-18) au plus tôt ; fonctions `notifierUpdate` et `notifierAlerte` en ligne (alerte déployée le 2026-11-19 d'après le ticket). **Note le commit réellement déployé avant de commencer** : c'est ta référence pour le retour arrière (section 4) et pour la commande de contrôle de l'étape A de chaque section (`<DERNIER_COMMIT_DEPLOYE>`).

---

## 1. À faire UNE SEULE FOIS avant tout déploiement : la règle TTL

Une **règle TTL** (« time-to-live ») dit à Firestore de supprimer tout seul les documents dont une date est dépassée. Ici : le champ `expireAt` de chaque ligne (création + 30 jours exactement).

Console Google Cloud > Firestore (projet `miff-3d283`) > **Time-to-live** > **Créer une règle** : groupe de collections **`notifications`**, champ **`expireAt`**. En Cloud Shell (commande de la documentation Google, non vérifiée par l'agent ; en cas de doute, la console fait foi) :

```bash
gcloud firestore fields ttls update expireAt --collection-group=notifications --enable-ttl --project=miff-3d283
```

- **Active en quelques minutes** (au moins une dizaine, d'après Google) : l'état passe de « Création » à « Actif » dans la console. Attends « Actif » avant l'étape suivante.
- **Un seul champ TTL par groupe de collections** : `notifications` n'est utilisé que par ce journal (aucun conflit avec `notifState.expireAt`, `alerts.purgeAt`, `quota.expireAt`, qui sont d'autres groupes).
- **Suppression « typiquement dans les 24 h » après l'échéance** (documentation Google) : une ligne vit en pratique 30 à 31 jours, et reste lisible entre l'échéance et sa suppression (les apps ignorent une ligne dont `expireAt` est passé). Les suppressions faites par le TTL comptent dans le quota de suppressions (20 000 par jour gratuits).
- **Aucun index à créer** : la lecture de l'app (`createdAt` filtré et trié) n'utilise que l'index simple créé automatiquement. **Ne rien changer à `firestore.indexes.json`, ne pas lancer `deploy --only firestore:indexes`.**
- **Contrôle (lecture seule)** : Firestore > Time-to-live : la règle `notifications` / `expireAt` est listée, état « Actif ».

---

## 2. Étape 1 : déployer les règles (`firestore.rules`)

**Précondition** : la règle TTL de la section 1 est **Active** dans la console.

**A. Préparer le dossier de déploiement** (PowerShell ou Git Bash) :

```bash
cd C:\dev\miff-deploy
git fetch origin
git switch --detach origin/main
git log -1 --format=%H
```

Note les **8 premiers caractères** du commit et vérifie avec Claude que c'est bien celui qu'il a validé (la PR `backend/notif-centre-journal` doit être **fusionnée**).

**B. Voir exactement ce qui part** (lecture seule ; remplace `<DERNIER_COMMIT_DEPLOYE>` par le dernier commit de règles que tu as déployé) :

```bash
git diff --stat <DERNIER_COMMIT_DEPLOYE> HEAD -- backend/firestore.rules backend/functions
```

Le résultat ne doit lister que les fichiers de cette PR (`firestore.rules`, `functions/src/*`, tests, documents). Si autre chose apparaît (une autre PR fusionnée entre-temps), **arrête-toi et copie la sortie à Claude**.

**C. Essai à blanc** (vérifie la syntaxe auprès du projet, **ne publie rien**) :

```bash
cd C:\dev\miff-deploy\backend
npm ci
npx firebase login:list
npx firebase deploy --only firestore:rules --project miff-3d283 --dry-run
```

**D. Déploiement réel** (seulement après ton « oui » écrit à Claude pour ce commit) :

```bash
npx firebase deploy --only firestore:rules --project miff-3d283 --message "CNC-B journal de notifications, commit <8 premiers caracteres>"
```

`--only firestore:rules` : ne publie **que** les règles (ni index, ni fonctions). `--project miff-3d283` : cible explicite. Jamais `--force`, jamais `firebase use`, jamais `ghostrun-app`. La CLI affiche `Deploy complete!`.

**E. Contrôles en lecture seule après le déploiement**

| # | Vérification | OK si |
|---|---|---|
| 1 | Console Firebase > Firestore Database > **Règles** | La date et l'heure de publication ont changé ; le texte contient `match /notifications/{entryId}` avec `allow create, update: if false;` |
| 2 | Avec une application **déjà installée** : parcours habituel (publier une update, arrêter puis reprendre le partage, quitter puis rejoindre une Miff de test, déclarer une alerte de test) | Tout fonctionne comme avant : **aucune règle existante n'est modifiée** |
| 3 | Le lendemain : Firestore Database > **Utilisation** | Pas d'écart de lectures ni d'écritures (aucune écriture de journal tant que les fonctions ne sont pas redéployées) |
| 4 | Tu notes le déploiement | Date, commit déployé, version précédente |

**Rien d'autre n'est visible côté utilisateurs à ce stade.**

---

## 3. Étape 2 (le PM) : publier le texte légal, puis étape 3 : redéployer les deux fonctions

### 3.1 Texte légal (lot CNC-J, `release-manager`)

Politique de confidentialité mise à jour (FR et EN : article 3 « journal serveur », 5, 7 « 30 jours », 8 « effacement », 11) **publiée sur le site par toi**, **sans** prétendre à une relecture juridique (décision du 2026-10-23). **Contrôle** : la page publiée mentionne le journal de 30 jours. **Ne passe à l'étape 3 qu'une fois la page visible en ligne.** Fiches des stores : à contrôler par `release-manager` (Data safety, App Privacy : probablement aucune nouvelle catégorie, à vérifier).

### 3.2 Avant (checklist)

| # | Vérification | OK si |
|---|---|---|
| 1 | Les règles de l'étape 1 sont en ligne et la règle TTL est **Active** | Oui |
| 2 | Le texte légal est **publié** (3.1) | « Oui » du PM écrit |
| 3 | Le compte `notify-update@miff-3d283.iam.gserviceaccount.com` porte toujours **exactement 4 rôles** et **aucune clé** (Google Cloud > IAM ; onglet « Clés » vide). **Rien à ajouter** : `roles/datastore.user` couvre l'écriture dans `users/{uid}/notifications` | Oui |
| 4 | L'alerte de budget existe (Google Cloud > Facturation > Budgets) | `Miff 5 EUR par mois` |
| 5 | Tu notes l'état actuel : `npx firebase functions:list --project miff-3d283` | `notifierUpdate` **et** `notifierAlerte` listées |
| 6 | Des comptes de test sont prêts (deux comptes dans une même Miff, au moins un avec une app récente ou ancienne : peu importe, le journal s'écrit côté serveur) | Oui |

### 3.3 Déployer (les DEUX fonctions, sans toucher à rien d'autre)

**A. Dossier de déploiement** : comme en section 2, étape A (le commit doit contenir cette PR).

**B. Essai à blanc** (construit et vérifie, **ne publie rien**) :

```bash
cd C:\dev\miff-deploy\backend
npx firebase deploy --only functions:notifierUpdate,functions:notifierAlerte --project miff-3d283 --dry-run
```

**C. Déploiement réel** (seulement après ton « oui » écrit pour ce commit, **et** une fois le texte légal publié) :

```bash
npx firebase deploy --only functions:notifierUpdate,functions:notifierAlerte --project miff-3d283
```

- `--only functions:notifierUpdate,functions:notifierAlerte` : **exactement ces deux fonctions**, rien d'autre. Ce sont des **mises à jour** de fonctions existantes (pas de création).
- Si la CLI propose de supprimer des fonctions absentes du code : **réponds NON** et préviens Claude. Jamais `--force`.
- Si la CLI parle d'**instances minimales** ou d'un coût minimum : annule (`Ctrl+C`) et préviens Claude.
- **Erreur « Permission denied » liée à l'Eventarc Service Agent** : attends quelques minutes puis relance **exactement** la même commande (déjà rencontrée, sans danger).
- La commande se termine par `Deploy complete!`. En cas d'erreur, rien de cassé n'est mis en ligne.

**D. Le droit `Cloud Run Invoker` : INCHANGÉ.** Il est posé **par service** (`notifierupdate` et `notifieralerte`) lors du premier déploiement de chacune ; **une mise à jour de la fonction ne le retire normalement pas** (à confirmer par l'étape E, point 3). **Ne rien poser, ne rien retirer.** Vérification en lecture seule (étape E, point 3). **Seulement si** un des deux services a perdu son droit (erreur « lacks {run.routes.invoke} permission » dans les journaux) : le reposer, **jamais** au niveau du projet, **jamais** « Allow public access » :

```bash
gcloud run services add-iam-policy-binding notifierupdate --region=europe-west9 --member="serviceAccount:notify-update@miff-3d283.iam.gserviceaccount.com" --role="roles/run.invoker" --project=miff-3d283
gcloud run services add-iam-policy-binding notifieralerte --region=europe-west9 --member="serviceAccount:notify-update@miff-3d283.iam.gserviceaccount.com" --role="roles/run.invoker" --project=miff-3d283
```

**E. Contrôles en lecture seule après le déploiement**

| # | Vérification | Commande ou endroit | OK si |
|---|---|---|---|
| 1 | Les deux fonctions sont en ligne, mises à jour | `npx firebase functions:list --project miff-3d283` | `notifierUpdate` et `notifierAlerte` listées, région **europe-west9**, 2e génération, Node.js 24 (aucune autre fonction apparue) |
| 2 | Les limites n'ont pas bougé | Console Firebase > Functions > détails (ou Cloud Run) | Instances maximales **5**, mémoire **256 Mio**, délai **30 s**, instances minimales **0**, compte de service **`notify-update@miff-3d283…`** |
| 3 | Le droit d'appel est toujours posé, **pas trop large** | Cloud Run > cocher `notifierupdate`, puis `notifieralerte` > Permissions | `notify-update@…` porte **Cloud Run Invoker** sur chacun ; **aucun** `allUsers` ni `allAuthenticatedUsers` ; la page IAM du projet montre toujours **4 rôles** pour ce compte (pas 5) |
| 4 | Aucune erreur | `npx firebase functions:log --project miff-3d283 --only notifierUpdate -n 30` puis `--only notifierAlerte` | Aucune ligne `ERROR` ; **aucun** « lacks {run.routes.invoke} permission » ni « permission denied » |
| 5 | **Essai de bout en bout, deux comptes de test dans la même Miff** (A publie une update, B reçoit) | Journaux du compte de service : ligne `notif.update.bilan` | `resultat: ok`, `envoyes` = 1, **`journalisees` = 1**, `ecritures` = 2 (l'état + 1 ligne) |
| 6 | **La ligne existe et ne contient rien de personnel** | Console Firebase > Firestore Database > `users` > (uid de B) > `notifications` > le seul document (lecture seule ; l'identifiant commence par `u-`) | **Exactement 6 champs** : `v` = 1, `kind` = `update`, `miffId`, `actorUid` (uid de A), `createdAt`, `expireAt` (= `createdAt` + 30 jours) ; **aucun prénom, aucune photo, aucun lieu** ; **A n'a aucune ligne** |
| 7 | Alerte de test (A déclare puis termine une alerte) | Journaux `notifierAlerte` : `notif.alerte.debut.bilan` puis `notif.alerte.fin.bilan` (`journalisees` = nombre de proches joignables) | B a deux lignes : `as-…` (`alert_start`) et `ae-…` (`alert_end`) |
| 8 | **Plafond conservé** | A publie une 2e update dans l'heure | **Ni push ni nouvelle ligne** (journal `notif.update.ignore`, `motif: plafond`) : conçu ainsi |
| 9 | Pas de dépense | Google Cloud > Facturation > Rapports (le lendemain) | 0 € à quelques centimes |
| 10 | Tu notes le déploiement | Date, commit (8 caractères), version précédente | Noté dans la conversation avec Claude |

---

## 4. Arrêter ou revenir en arrière (si quelque chose ne va pas)

**Arrêter l'écriture du journal tout de suite** (les notifications continuent de partir comme avant, **sans** ligne de journal) : redéployer la version **d'avant** des deux fonctions.

```bash
cd C:\dev\miff-deploy
git switch --detach <DERNIER_COMMIT_DEPLOYE>
cd backend
npx firebase deploy --only functions:notifierUpdate,functions:notifierAlerte --project miff-3d283
cd ..
git switch --detach origin/main
```

Effets : plus aucune ligne n'est écrite ; **les lignes déjà écrites expirent seules au bout de 30 jours** (règle TTL) ; les apps lisent un journal vide ou partiel **sans erreur** (et gardent leurs lignes locales) ; les messages perdent la clé `entryId` (les apps anciennes et nouvelles gèrent son absence : critère CNC-23). **Ne pas revenir aux règles** : elles n'ont aucun effet sans écritures, et les revenir rendrait les lignes déjà écrites illisibles pour leurs propriétaires et non supprimables.

**Revenir aux règles d'avant** (seulement si une règle existante semblait en cause, ce qui n'est pas attendu : aucune règle existante n'est modifiée) : méthodes A à C de `docs/dev/deploiement-regles.md` §4.

**Effacer immédiatement les lignes d'un compte** (demande d'un utilisateur, incident) : l'utilisateur peut effacer ses lignes dans l'app (« Tout effacer ») ; sinon, demande à Claude la commande exacte (suppression ciblée par compte, jamais sur tout le groupe de collections sans décision explicite du PM).

---

## 5. Coût et signal de bascule (pour que tu voies venir la facture)

- **Estimation** (pas une mesure ; hypothèses dites dans `backend/data-model.md` §12) : Miff de 4 membres, 2 publications par utilisateur actif et par jour, 5 ouvertures de l'app par jour. **100 actifs/jour** : 600 lignes/jour, **0 $** ; **1 000** : 6 000 lignes/jour, ≈ 16 000 lectures/jour, **≈ 0 $** (sous les quotas gratuits de 20 000 écritures et 50 000 lectures par jour) ; **10 000** : 60 000 lignes/jour, ≈ 160 000 lectures/jour, **≈ 3 à 4 $ par mois**. Stockage : ≈ 55 Mo à 1 000 actifs, ≈ 0,55 Go à 10 000 (gratuit jusqu'à 1 Go).
- **À regarder chaque semaine pendant la bêta** (1 minute) : `docs/dev/surveillance-quotas.md` §5 nonies (écritures du journal par jour, `journalisees` dans les journaux, lectures du centre par ouverture, dépense).
- **Signaux de bascule ou de revue** : écritures dans `notifications` par jour > **3 × (destinataires × publications) attendus** ; lectures du centre par ouverture > **15** ; écritures totales > **~40 000 par jour** (alors : une ligne **par Miff et par jour** regroupée au lieu d'une par destinataire, `data-model.md` §12) ; facture Blaze > **5 $ par mois**.

---

## 6. Ce que cette PR ne fait PAS (pour éviter les surprises)

- **Aucune** règle existante n'est modifiée (une seule sous-collection de plus sous `users/{uid}`) ; **aucune** fonction nouvelle ; **aucun** droit de plus pour le compte de service ; **aucun** index à créer ; **aucune** fonction planifiée.
- **Aucun rétro-remplissage** : les notifications d'avant l'étape 3 ne sont dans aucun journal et n'apparaîtront jamais dans le centre si le téléphone ne les avait pas déjà inscrites.
- **Aucune** photo ni prénom stocké dans le journal ; **aucun** changement de ce que le téléphone affiche sur l'écran verrouillé (le message ne gagne que la clé technique `entryId`).
- **Aucun** purge croisée à la suppression du compte d'un **auteur** : son identifiant reste jusqu'à 30 jours chez ses anciens proches (risque assumé, `data-model.md` §11 n° 33).
- **Rien n'est déployé par cette PR** : ce document est la procédure que **tu** exécutes, sur ton « oui ».
