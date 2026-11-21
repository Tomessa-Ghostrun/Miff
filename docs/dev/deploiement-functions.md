# Déployer les Cloud Functions (procédure du PM)

**À qui s'adresse ce document** : au PM, qui tape lui-même les commandes ci-dessous sur son poste. Aucun agent ne déploie, et aucune commande de ce document n'est exécutée par la CI GitHub (voir « Pourquoi jamais depuis la CI »).

**Contact et autorité de décision** : Tomessa MFOUKA, tomessa.mfouka@gmail.com (PM du projet Miff).

**Ce que sont les Cloud Functions** : de petits programmes qui tournent **chez Google** (pas sur les téléphones), par exemple pour envoyer une notification à un proche quand quelqu'un change de statut. Elles n'existent que depuis que le plan **Blaze** est activé (décision du PM du 2026-11-11). Elles ne remplacent pas les règles de sécurité Firestore : celles-ci continuent de décider qui lit quoi (procédure : `deploiement-regles.md`).

**Ce que « déployer » veut dire ici** : envoyer le dossier `backend/functions/` vers le projet Firebase `miff-3d283`. Une fonction envoyée est **en ligne tout de suite** et peut être appelée par n'importe quelle copie de l'application (avec la vérification App Check et la connexion exigées, voir plus bas). On ne déploie donc qu'après un « oui » explicite du PM, CI verte.

**État réel au 2026-11-13** (constaté par le PM) : le dossier contient **une seule fonction**, et elle est **en ligne**.
- **Déploiement** : le **2026-11-13**, sur le commit **`2aacad1d`**, région `europe-west9` (deux fonctions déployées ce jour-là : `ping` et `notifierUpdate`).
- **`ping` : supprimée le 2026-11-13** (et retirée du code). C'était une fonction de démonstration (SRV-1) qui ne lisait ni n'écrivait aucune donnée et que personne n'appelait. Elle tournait avec le compte de service « Compute Engine par défaut » du projet (`29899146512-compute@developer.gserviceaccount.com`), dont les droits sont potentiellement très larges, alors qu'elle ne servait à rien. Il ne reste que `notifierUpdate` en ligne (la procédure de suppression est conservée en section 3, encadré « Retrait de `ping` », à titre d'historique).
- **`notifierUpdate`** (SRV-4B) : la **première vraie fonction**. Quand un membre publie une update, elle envoie une notification discrète aux autres membres de sa Miff (« {Prénom} a donné des nouvelles »). **Elle lit des données (fiches de membres, jetons d'appareil) et envoie des notifications réelles** : sa mise en ligne a été l'**interrupteur de mise en service** de la fonctionnalité (section 11). Elle exige un **compte de service dédié** (section 1.4) : ses **4 rôles de projet** avant le déploiement, son **droit `run.invoker` sur le service** après le premier déploiement.
- **Essai de bout en bout réussi le 2026-11-13** : iPhone (compte A) publie, OPPO (compte B) reçoit la notification ; journaux : `notif.update.bilan` ok, 13 lectures, 1 envoi, 2 destinataires écartés « sans appareil ». **Cet essai n'a PAS vérifié** la réception sur iPhone (APNs) : l'iPhone était du côté de l'auteur.
- **Nettoyage des images techniques** : réglé à **2 jours** (valeur saisie par le PM au premier déploiement ; la procédure, section 3, proposait 1 jour).
- **Reste à faire** : (1) **règle TTL de `notifState`** à poser maintenant que la collection existe (section 1.4, étape E) ; (2) **republier les textes légaux** avant l'ouverture à d'autres testeurs (ils décrivent désormais un service de notifications en ligne : ticket §15/§16, P6) ; (3) vérifier la réception sur iPhone (APNs).

---

> **Deuxième fonction, `notifierAlerte` (AD-B2, alerte « Je suis en danger », NON DÉPLOYÉE)** : sa procédure complète (règle TTL, ordre règles puis fonction, droit `run.invoker` du compte `notify-update` sur le service `notifieralerte`, contrôles, arrêt rapide ; **aucun nouveau compte de service**) est dans [`deploiement-alerte-danger.md`](deploiement-alerte-danger.md). Le reste de ce document (interdits, checklist, journaux, retour arrière) s'applique aux deux fonctions.

> **Cinq fonctions de facturation, `preparerAchat`, `synchroniserAchat`, `recevoirNotificationsGooglePlay`, `recevoirNotificationsApple` et `supprimerDonneesAbonnement` (SRV-7, premium réel Miff+, NON DÉPLOYÉES)** : leur procédure complète (compte de service `billing-sync` **sans clé**, sujet Pub/Sub `play-rtdn`, secret Apple saisi par le PM, trois règles TTL, ordre, droits `run.invoker`, vérifications, arrêt rapide) est au **§12 de ce document**. Ce qui change pour le reste du document : **un compte de plus** (`billing-sync`, 3 rôles de projet) ; **une seule fonction avec nouvelle tentative automatique** (celle de Pub/Sub, idempotente) ; **une fonction HTTP joignable sans connexion ni App Check** (le point d'entrée d'Apple, protégé par la signature) ; un secret **hors de la fonction** (lu à la demande dans le gestionnaire de secrets).

---

## 0. Les interdits

1. **Jamais depuis la CI GitHub.** Déployer depuis GitHub obligerait à y stocker une clé de compte de service Google (un secret très puissant). `docs/dev/secrets.md` interdit d'en créer. Les tests du dépôt vérifient qu'aucun workflow ne contient de déploiement ni de jeton.
2. **Jamais sans `--project miff-3d283` écrit dans la commande.** Le compte Google du PM contient aussi le projet de Ghostrun (`ghostrun-app`). Le dépôt n'a volontairement aucun projet par défaut : une commande qui oublie `--project` s'arrête au lieu de viser un projet au hasard.
3. **Jamais `ghostrun-app`, jamais `firebase use`, jamais `--force`.** `--force` répond « oui » à ta place à des questions de sécurité ou de coût : on répond soi-même, une fois, en lisant.
4. **Jamais sans le « oui » du PM écrit dans la conversation avec Claude**, pour **ce** déploiement et **ce** commit.

---

## 1. À faire UNE SEULE FOIS avant le tout premier déploiement

### 1.1 Alerte de budget (obligatoire, 5 minutes)

Le plan Blaze facture à l'usage. **Une alerte te prévient, elle ne bloque rien** : la dépense continue même après l'alerte. C'est pourquoi on en pose une tôt, et basse.

1. Ouvre https://console.cloud.google.com/billing et choisis le **compte de facturation** relié à Miff (si tu en as plusieurs, c'est celui qui apparaît dans Firebase > Paramètres du projet > Utilisation et facturation).
2. Menu de gauche : **Budgets et alertes** > **Créer un budget**.
3. Nom : `Miff 5 EUR par mois`.
4. Portée : **Projets** > sélectionne **uniquement `miff-3d283`** (ton compte de facturation peut aussi servir à Ghostrun : tu ne veux pas que les dépenses de Ghostrun déclenchent les alertes de Miff, ni l'inverse). Services : tous.
5. Montant : **Montant spécifié** = **5 €** (mensuel).
6. Seuils d'alerte : garde ceux proposés (**50 %, 90 %, 100 %** du budget) ; tu peux ajouter une alerte sur la **dépense prévue**.
7. Notifications : coche l'envoi d'e-mails aux administrateurs de facturation ; vérifie que **ton adresse e-mail** y figure (`tomessa.mfouka@gmail.com`).
8. **Enregistrer.**

> Les chiffres d'un budget Google arrivent avec **plusieurs heures de retard** (documentation Google). Une alerte à 90 % peut donc arriver après un emballement d'une demi-journée. C'est un filet de sécurité, pas un frein. Les vrais freins sont dans le code (limite d'instances, délai court : section 9) et dans App Check.

### 1.2 Plafond de dépense Cloud Functions (recommandé, 2 minutes)

Firebase propose un **plafond mensuel** par service. D'après la documentation Firebase (lue le 2026-11-12) :
- il existe pour **Cloud Functions** mais **pas pour Firestore** ;
- il est en **Preview** (essai : sans engagement de service, peut changer) ;
- à 50 % et 80 %, des e-mails d'avertissement ; **à 100 %, l'usage des fonctions est mis en pause** jusqu'au mois suivant (les appels échouent) ;
- ce n'est **pas un plafond absolu** : l'application peut être retardée de plusieurs minutes, les dépassements pendant ce délai sont facturés.

Pour l'activer : console Firebase > projet **Miff** > roue dentée > **Utilisation et facturation** > onglet **Détails et paramètres** > carte **Plafonds de dépense par service** > choisir **Cloud Functions for Firebase**, saisir un montant mensuel (suggestion : **3 €**, sous le budget de 5 €), **Configurer**. *(Libellés recopiés de la documentation en anglais ; ils peuvent différer à l'écran.)*

**Risque restant, à connaître** : Firestore (les lectures) n'est couvert par aucun plafond. La protection est App Check « appliqué » et la surveillance quotidienne décrite dans `surveillance-quotas.md`.

### 1.3 Préparer le dossier de déploiement

Les déploiements se font depuis le dossier `C:\dev\miff-deploy` (une copie du dépôt dédiée au déploiement, séparée du dossier de travail). Dans un terminal (PowerShell ou Git Bash) :

```bash
cd C:\dev\miff-deploy
git fetch origin
git switch --detach origin/main
git log -1 --format=%H
```

La dernière commande affiche l'identifiant du commit : **note les 8 premiers caractères** et vérifie avec Claude que c'est bien celui qu'il a validé. Puis installe l'outil Firebase (une fois, ou après chaque changement de version) :

```bash
cd C:\dev\miff-deploy\backend
npm ci
npx firebase login:list
```

`login:list` doit afficher ton compte Google (sinon : `npx firebase login`).

### 1.4 Créer le compte de service dédié de `notifierUpdate` (4 rôles AVANT le déploiement, 1 droit sur le service APRÈS le premier déploiement)

**En résumé, le compte `notify-update@miff-3d283.iam.gserviceaccount.com` a deux sortes de droits** (constaté le 2026-11-13) :
- **4 rôles au niveau du PROJET** (étape B), à poser **avant** le premier déploiement ;
- **1 droit PAR SERVICE** : `Cloud Run Invoker` (`roles/run.invoker`) **sur le seul service Cloud Run `notifierupdate`** (étape D bis), à poser **après** le premier déploiement, **parce que ce service n'existe pas avant** : Google le crée au déploiement. Sans ce droit, la fonction est bien déclenchée par Eventarc mais Cloud Run **refuse chaque appel** (voir « Erreurs possibles au premier déploiement »).

**Pourquoi** : une fonction s'exécute avec une « identité » Google (un compte de service). Par défaut, c'est un compte du projet qui a souvent le rôle **Éditeur** (il peut presque tout faire dans le projet). Une fonction qui lit les fiches des membres et envoie des notifications ne doit pas avoir ce pouvoir : si elle était détournée, les dégâts seraient limités à ce que son compte a le droit de faire. On lui crée donc **son propre compte**, avec **seulement** les droits utiles. **Aucune clé n'est créée ni téléchargée** : l'identité est gérée par Google (`docs/dev/secrets.md`). Si ce compte n'existe pas au moment du déploiement, la commande **échoue** : c'est le bon comportement, rien n'est mis en ligne.

> Libellés recopiés de la documentation Google (en anglais, parfois traduits) : ils peuvent différer légèrement à l'écran. En cas de doute, **arrête-toi et copie ce que tu vois à Claude**. Vérifie à chaque page, en haut, que le projet affiché est bien **Miff / `miff-3d283`** (jamais Ghostrun).

**Étape A : créer le compte**
1. Ouvre https://console.cloud.google.com/iam-admin/serviceaccounts et choisis le projet **`miff-3d283`** (sélecteur de projet en haut de la page).
2. Clique **Créer un compte de service** (« Create service account »).
3. **Nom du compte de service** : `notify-update`. L'identifiant qui s'affiche dessous doit être exactement `notify-update` (l'adresse complète sera `notify-update@miff-3d283.iam.gserviceaccount.com` : c'est celle qui est écrite dans le code, une autre adresse serait refusée par les tests).
4. Description : `Fonction notifierUpdate : lit Firestore et envoie les notifications`.
5. Clique **Créer et continuer**. **Ignore** l'étape « Accorder l'accès à ce compte » (on s'en occupe à l'étape B) : clique **Continuer**, puis **Terminé** (« Done » ; sans rien ajouter à l'étape « Accorder aux utilisateurs l'accès »).
6. **Ne crée aucune clé** (onglet « Clés » du compte : il doit rester vide). Si on te propose de télécharger un fichier `.json`, **refuse**.

**Étape B : lui donner les 4 rôles de projet** (le droit sur le service vient à l'étape D bis, après le déploiement)
1. Ouvre https://console.cloud.google.com/iam-admin/iam (projet `miff-3d283`) et clique **Accorder l'accès** (« Grant access »).
2. Dans « Nouveaux comptes principaux », colle `notify-update@miff-3d283.iam.gserviceaccount.com`.
3. Ajoute ces **quatre rôles** (bouton « Ajouter un autre rôle » pour chacun) :

| Rôle (nom affiché, identifiant) | À quoi il sert | Source / réserve |
|---|---|---|
| **Utilisateur Cloud Datastore** (`roles/datastore.user`) | Lire et écrire dans Firestore : fiches de membres, jetons d'appareil, et l'état `notifState` | Rôle documenté de Firestore (accès lecture/écriture aux données, pas à la gestion du projet) |
| **Administrateur de l'API Firebase Cloud Messaging** (`roles/firebasecloudmessaging.admin`) | Envoyer les notifications. C'est le **plus petit rôle prédéfini** documenté qui contient la permission d'envoi `cloudmessaging.messages.create` (le rôle « Firebase Cloud Messaging Service Editor », `roles/cloudmessaging.editor`, a exactement les mêmes permissions) | Documentation IAM de Google, lue le 2026-11-12. Un rôle personnalisé avec cette seule permission serait plus étroit : **non vérifié**, à ne tenter que si Google le confirme ; en cas d'erreur « permission denied » à l'envoi, ne rien élargir sans en parler à Claude |
| **Récepteur d'événements Eventarc** (`roles/eventarc.eventReceiver`) | Recevoir l'événement « un document photo a été écrit » | Documentation Eventarc (rôle du compte du déclencheur) |
| **Rédacteur de journaux** (`roles/logging.logWriter`) | Écrire ses journaux (sans donnée personnelle) dans Cloud Logging | Standard |

4. **Enregistrer.**
5. **N'ajoute jamais** : Éditeur, Propriétaire, Administrateur Firebase, ni un rôle « Admin » de Firestore. Si une erreur au déploiement demande un autre droit pour **ce** compte, copie le message à Claude plutôt que d'ajouter un rôle large.
6. **N'ajoute pas `Cloud Run Invoker` ici** : au niveau du projet, il donnerait le droit d'appeler **tous** les services Cloud Run du projet. Il se pose **sur le seul service `notifierupdate`** (étape D bis).

**Étape C : donner à TON compte le droit d'utiliser ce compte de service**
Pour déployer une fonction « qui tourne sous » un compte de service, le compte Google qui déploie (le tien) doit avoir le rôle **Utilisateur du compte de service** (« Service Account User », `roles/iam.serviceAccountUser`) **sur ce compte**.
1. Retourne sur https://console.cloud.google.com/iam-admin/serviceaccounts, clique l'adresse `notify-update@…`.
2. Onglet **Autorisations** (« Permissions ») > **Accorder l'accès**.
3. Nouveau compte principal : **ton adresse Google** (`tomessa.mfouka@gmail.com`) ; rôle : **Utilisateur du compte de service**. **Enregistrer.**
4. Si tu es **Propriétaire** du projet, tu as déjà ce droit : l'ajouter ne fait pas de mal et rend la règle explicite.

**Étape D : vérifier (30 secondes)**
- **Avant le premier déploiement** : sur la page IAM du projet, la ligne `notify-update@miff-3d283.iam.gserviceaccount.com` doit afficher **exactement ces 4 rôles** et aucun autre (pas de « Cloud Run Invoker » à cet endroit : il n'apparaît **jamais** dans la page IAM du projet, puisqu'il est posé sur le service). Sur la page du compte, l'onglet « Clés » est vide.
- **Après le premier déploiement et l'étape D bis** : la page IAM du projet montre toujours **4 rôles** (pas 5), et, dans les **Permissions du service `notifierupdate`** (Google Cloud > Cloud Run > cocher le service > **Permissions**), la ligne `notify-update@…` porte **Cloud Run Invoker**.
- Note la date dans la conversation avec Claude.

**Étape D bis : après le premier déploiement, poser `Cloud Run Invoker` sur le seul service `notifierupdate`** (confirmé en production le 2026-11-13)

**Pourquoi** : `notifierUpdate` est déclenchée par Eventarc, qui appelle le service Cloud Run de la fonction **en se faisant passer pour le compte `notify-update@…`**. Cloud Run n'accepte l'appel que si ce compte a la permission `run.routes.invoke`, que donne le rôle `Cloud Run Invoker`. Ce droit se pose **sur le service**, qui **n'existe qu'après le premier déploiement** : on ne peut donc pas le poser plus tôt. **Jamais au niveau du projet, jamais « Allow public access »** (ni `allUsers`, ni `allAuthenticatedUsers`) : la fonction n'a aucune raison d'être appelable par n'importe qui.

*Façon 1, dans la console Google Cloud (libellés anglais de la console ; vérifie en haut que le projet est `miff-3d283`) :*
1. Ouvre https://console.cloud.google.com/run (projet `miff-3d283`) : la **liste des services Cloud Run**.
2. **Coche** la case à gauche du service **`notifierupdate`** (région `europe-west9`) : un panneau s'ouvre à droite.
3. Dans ce panneau, ouvre **Permissions**.
4. Clique **Add principal**.
5. **New principals** : colle `notify-update@miff-3d283.iam.gserviceaccount.com`.
6. **Role** : choisis **Cloud Run Invoker** (dans le sélecteur : « Cloud Run » > « Cloud Run Invoker »).
7. **Save.**

*Façon 2, dans Cloud Shell (le terminal intégré à la console Google Cloud, bouton « Activate Cloud Shell » en haut à droite ; la commande vise explicitement le projet et le service) :*
```bash
gcloud run services add-iam-policy-binding notifierupdate --region=europe-west9 --member="serviceAccount:notify-update@miff-3d283.iam.gserviceaccount.com" --role="roles/run.invoker" --project=miff-3d283
```
La commande affiche la politique mise à jour, avec une ligne `role: roles/run.invoker` et le membre `serviceAccount:notify-update@…`.

Ensuite, vérifie l'étape D (second point) et lis les journaux (section 4, vérification 4). **Ne donne ce droit à aucun autre compte, sur aucun autre service.**

**Étape E : activer la suppression automatique de `notifState` (durée de vie, « TTL »), après la première notification**
La fonction note dans la collection `notifState` (accessible à la fonction seulement, jamais à une application) la date de la dernière update traitée par auteur, pour ne jamais notifier deux fois et ne pas dépasser une notification par heure. Ces lignes contiennent des identifiants : elles doivent disparaître seules au bout de 2 jours. **À poser APRÈS la première notification** : la collection n'existe qu'à partir de ce moment (**elle a été créée le 2026-11-13 ; la règle TTL reste à poser**).
1. Ouvre la **console Google Cloud** (https://console.cloud.google.com/firestore), projet **`miff-3d283`** > **Firestore** > menu de gauche **Time-to-live** (« TTL » ; la durée de vie est configurée à cet endroit, pas dans la console Firebase) *(libellé non vérifié ; en cas de doute, copie ce que tu vois à Claude)*.
2. **Créer une règle** (« Create policy ») : **groupe de collections** `notifState`, **champ** `expireAt`. **Enregistrer.**
3. À savoir : la suppression est « typiquement dans les 24 h » après l'échéance (documentation Google) et n'est pas gratuite à l'usage sur Blaze (négligeable : une suppression par Miff active et par 2 jours). Tant que la règle n'est pas créée, rien ne casse : les lignes restent, voilà tout (quelques centaines d'octets par Miff).

**Étape F : les règles des jetons**
La fonction ne trouve des destinataires que si les **règles Firestore des jetons d'appareil** (branche `backend/jetons-appareil`, SRV-2R) sont **déployées** et que des applications ont enregistré des jetons (SRV-2A / SRV-2I). Procédure : `deploiement-regles.md`. Sans cela, la fonction se déclenche, ne trouve aucun appareil et n'envoie rien (sans erreur).

---

## 2. Avant chaque déploiement (checklist)

| # | Vérification | Comment | OK si |
|---|---|---|---|
| 1 | La PR est fusionnée dans `main` | GitHub, ou demande à Claude | Fusionnée |
| 2 | La CI de `main` est verte, y compris le test `functions` | GitHub > Actions > dernier run | Coches vertes |
| 3 | Les garde-fous de coût sont intacts | Ils sont vérifiés par le test `functions` de la CI (fichier `garde-couts.test.mjs`) : une fonction sans limite d'instances le ferait échouer | Coche verte |
| 4 | Revue de sécurité faite, « oui » du PM écrit | Conversation avec Claude | Un « oui » pour ce commit |
| 5 | Alerte de budget en place (section 1.1) | Console Google Cloud > Facturation > Budgets et alertes | Le budget `Miff 5 EUR par mois` existe |
| 6 | Tu notes l'état actuel | Console Firebase > **Functions** : liste des fonctions en ligne | Capture d'écran de la liste (plan B) |
| 7 | **Pour `notifierUpdate` seulement** : le compte de service dédié existe avec ses **4 rôles de projet** et sans clé (section 1.4, étapes A à D). **Le droit `Cloud Run Invoker` sur le service n'est PAS à poser avant** : il se pose après le premier déploiement (étape D bis ; pour un redéploiement, il est déjà en place) | Google Cloud > IAM | La ligne `notify-update@miff-3d283…` montre exactement 4 rôles |
| 8 | **Pour `notifierUpdate` seulement** : les règles des jetons d'appareil sont déployées (section 1.4 F) ; la règle TTL `notifState` est posée **après la première notification** (section 1.4 E) | `deploiement-regles.md`, console Firestore | Règles publiées ; règle TTL visible dès que la collection existe |
| 9 | **Pour `notifierUpdate` seulement** : les textes légaux (SRV-3) sont publiés et aucune fiche de store ne dit encore « pas de notification » (ticket §15/§16, P6) | Conversation avec Claude | « Oui » du PM écrit |
| 10 | **Pour les 5 fonctions de facturation (SRV-7) seulement** : API activées, compte `billing-sync` avec **3 rôles** et sans clé, sujet `play-rtdn` avec **un seul éditeur** (Google Play), **§12.2 à 12.5 faits** ; commande avec `--only` **nommant les 5 fonctions** | Google Cloud > IAM, Pub/Sub | Les 3 rôles et le sujet sont visibles ; aucune clé |

---

## 3. La commande

Dans un terminal, depuis le dossier `backend` (c'est là que se trouve `firebase.json`) :

```bash
cd C:\dev\miff-deploy\backend
npx firebase deploy --only functions --project miff-3d283
```

- `--only functions` : n'envoie **que** les fonctions (ni les règles, ni les index).
- `--project miff-3d283` : cible explicite, jamais implicite.
- La commande installe puis compile le code sur ton poste (étapes automatiques de `firebase.json`), l'envoie, et Google le construit (plusieurs minutes pour le premier déploiement).

**Essai à blanc recommandé avant le premier déploiement** : ajoute `--dry-run` en fin de commande. La CLI installe, construit le code et vérifie, **sans rien publier**. Si l'essai à blanc réussit, relance la même commande sans `--dry-run`.

**Si la CLI refuse `nodejs24`** (message du type « runtime not supported » ou « unsupported Node.js version ») : ne force rien et préviens Claude. Le repli prévu est **Node 22** : dans `backend/firebase.json` (`"runtime": "nodejs22"`), dans `backend/functions/package.json` (`"engines": { "node": "22" }`) et dans `backend/tests/unit/functions-config.test.js` (valeurs attendues). Le code est identique ; `firebase-admin` 14 demande Node 22 ou plus, donc compatible.

### Retrait de `ping` (à faire UNE fois, après la fusion de la PR qui la retire du code)

**Fait le 2026-11-13 : `ping` a été supprimée chez Google** (constaté par le PM). Cette section est conservée à titre d'historique et au cas où elle réapparaîtrait.

`ping` est retirée du code, mais **elle serait restée en ligne chez Google** tant qu'on ne la supprimait pas : retirer le code ne supprime rien tout seul. La commande exacte qui a été utilisée (Git Bash ou PowerShell) :

```bash
cd C:\dev\miff-deploy\backend
npx firebase functions:delete ping --region europe-west9 --project miff-3d283
```

- La CLI demande confirmation : **relis que le nom affiché est bien `ping`**, puis réponds oui. Ne supprime aucune autre fonction (surtout pas `notifierUpdate`).
- Cette commande ne touche **pas** `notifierUpdate` ni aucune donnée (`ping` n'en avait aucune). Elle ne peut pas être « recréée par accident » : le code de `main` ne contient plus `ping`, donc un futur `deploy --only functions` ne la remet pas en ligne (et un test de la CI échouerait si quelqu'un la rajoutait).
- Vérification : `npx firebase functions:list --project miff-3d283` ne doit plus lister que `notifierUpdate` (région `europe-west9`, compte de service `notify-update@…`).
- Si tu lances un déploiement complet **avant** cette suppression, la CLI proposera de supprimer `ping` (« absente du code ») : réponds **oui pour `ping` uniquement**, après avoir lu la liste (voir le tableau ci-dessous).
- Pas de retour de service à craindre : personne n'appelle `ping`. Si, contre toute attente, une application l'appelait, l'appel échouerait simplement (le résultat n'était pas utilisé).

### Les questions que la commande peut te poser

| Question (en anglais) | Ta réponse |
|---|---|
| Activer des services Google (Cloud Functions, Artifact Registry, Cloud Build…) | Elle les active d'elle-même si besoin ; si elle demande confirmation : **oui**. Activer un service ne coûte rien en soi. |
| `No cleanup policy detected for repositories in europe-west9 … This may result in a small monthly bill as container images accumulate over time.` suivi de `How many days do you want to keep container images before they're deleted?` | **Tape `2` puis Entrée** (2 jours : valeur retenue par le PM au premier déploiement du 2026-11-13 ; 1 jour, la valeur proposée par défaut par la CLI, convient aussi : durée de rétention courte dans les deux cas). Cette règle supprime automatiquement les anciennes « boîtes » techniques de tes fonctions que Google conserve sinon indéfiniment (petite facture mensuelle qui monte avec le temps). Si la CLI te pose plutôt une question oui/non à ce sujet, réponds **oui**. Si tu la rates, la commande `npx firebase functions:artifacts:setpolicy --project miff-3d283` permet de la définir après coup. |
| Une demande de suppression de fonctions absentes du code (`The following functions are found in your project but do not exist in your local source code`) | Lis la liste **en entier** avant de répondre. **Oui seulement si la liste ne contient QUE `ping`** (c'est la fonction retirée du code, voir l'encadré ci-dessous). **Si elle contient autre chose (par exemple `notifierUpdate`, ou un nom que tu ne reconnais pas), réponds NON** et préviens Claude : une fonction absente du code peut venir d'une erreur de dossier ou de commit. Ne passe jamais `--force` (il répond « oui » à ta place). Si tu as déjà supprimé `ping` avec la commande ci-dessous, la question n'apparaît plus. |
| Tout message qui parle d'**instances minimales** ou d'un **coût minimum** | **Non**, annule (`Ctrl+C`) et préviens Claude : le dépôt n'en prévoit jamais (une instance minimale est facturée même sans trafic). |

La CLI se termine par `Deploy complete!`. En cas d'erreur, **rien de cassé n'est mis en ligne** : l'ancienne version reste (au premier déploiement, il n'y a simplement rien).

### Erreurs possibles au premier déploiement

Les deux premières ont été **rencontrées et résolues le 2026-11-13** ; les suivantes sont de connaissance générale (non vérifiées).

- **(a) Erreur « Permission denied » liée à l'« Eventarc Service Agent »** (au moment de créer le déclencheur) : c'est un droit que Google vient d'accorder à son propre agent de service et qui met **quelques minutes** à se propager. **Attends quelques minutes, puis relance exactement la même commande.** Ne donne aucun rôle à personne pour « faire passer » l'erreur.
- **(b) Après un déploiement réussi, les journaux affichent « The request was not authenticated… The IAM principal lacks {run.routes.invoke} permission »** : la fonction est bien déclenchée par Eventarc, mais Cloud Run **refuse chaque appel** (rien ne part, aucune notification). Cause : le compte `notify-update@…` n'a pas encore le droit d'appeler le service. **Correction** : poser `Cloud Run Invoker` (`roles/run.invoker`) à ce compte **sur le seul service `notifierupdate`** (section 1.4, **étape D bis**, deux façons). **Jamais** au niveau du projet, **jamais** « Allow public access ». Un droit IAM ne demande en principe pas de redéploiement (connaissance générale, non vérifiée ici : il peut mettre quelques minutes à se propager) ; refais ensuite l'essai de la section 11.4.
- Message disant qu'un **droit manque pour la construction** (Cloud Build / compte de service) : ne donne **aucun rôle large** (Propriétaire, Éditeur) « pour que ça passe ». Copie le message à Claude : il te dira le rôle précis à accorder.
- Message parlant de **facturation** : vérifie que le projet est bien relié au compte de facturation (Firebase > Utilisation et facturation > plan Blaze).

---

## 4. Après le déploiement (vérifications)

| # | Vérification | Où | OK si |
|---|---|---|---|
| 1 | La fonction est en ligne | Console Firebase > projet **Miff** > **Functions** (ou la commande `npx firebase functions:list --project miff-3d283`) | `notifierUpdate` apparaît (et **plus `ping`**, une fois supprimée), région **europe-west9**, environnement 2e génération, Node.js 24 |
| 2 | Les limites sont bien appliquées | Cliquer sur la fonction > détails (ou Google Cloud > Cloud Run > service de la fonction) | `notifierUpdate` : instances maximales **5**, mémoire **256 Mio**, délai **30 s**, instances minimales **0**, compte de service **`notify-update@miff-3d283…`** (et non le compte « Compute Engine default ») |
| 3 | Aucune erreur | Onglet **Journaux** de `notifierUpdate` (section 5) | Aucun message rouge « Error » |
| 4 | **Les appels ne sont pas refusés** (premier déploiement : le droit `Cloud Run Invoker` est-il posé ?) | Journaux (section 5) après une vraie update de test (section 11.4) ; le droit est visible dans Cloud Run > cocher le service `notifierupdate` > **Permissions** | **Aucun message « The request was not authenticated… » ni « lacks {run.routes.invoke} permission »**, et une ligne `notif.update.bilan` apparaît. Sinon : section 1.4, étape D bis |
| 5 | Pas de dépense | Google Cloud > Facturation > Rapports (le lendemain) | 0 € pour Cloud Functions |
| 6 | Tu notes le déploiement | Date, commit déployé (8 caractères), version précédente | Dans un fichier à toi ou dans la conversation avec Claude |

> `notifierUpdate` n'a **aucun point d'entrée appelable depuis un navigateur ni une application** : elle se déclenche seule quand une photo d'update est écrite dans Firestore. Il n'y a donc rien à « essayer » à la main : la vérification est « la fonction est en ligne, avec les bonnes limites et le bon compte de service, sans erreur », puis le test de la section 11.4. **`ping` ne doit plus apparaître** dans la liste (sinon : section 3, « Retrait de `ping` »).

---

## 5. Lire les journaux (logs)

Les journaux disent ce que chaque fonction a fait. **Nos journaux APPLICATIFS (ce que le code de Miff écrit) ne contiennent jamais de position, de nom, de jeton ni d'adresse e-mail** (règle codée et testée : un message de journal est un petit code comme `notif.update.bilan`, avec au plus un résultat et un nombre). **Mais** les journaux TECHNIQUES de la plateforme Google (Cloud Run / Cloud Logging, journaux de requêtes) gardent l'**adresse IP et le user-agent** de l'appelant : c'est une donnée personnelle que Miff ne maîtrise pas, à citer dans la politique de confidentialité (point RGPD, risque assumé par le PM, décision du 2026-10-23).

- **Console Firebase** : projet **Miff** > **Functions** > onglet **Journaux** (filtre par fonction et par gravité).
- **Console Google Cloud** : https://console.cloud.google.com/logs (choisir le projet `miff-3d283`, filtrer « Ressource : Cloud Run Revision » ou la fonction).
- **Ligne de commande** : `npx firebase functions:log --project miff-3d283 --only notifierUpdate -n 30` (les 30 dernières lignes).

Niveaux : `INFO` = normal ; `WARNING` = à regarder si ça se répète (par exemple une demande refusée) ; `ERROR` = quelque chose a échoué : copie le message à Claude.

**Signaux à repérer** : « Memory limit exceeded » (mémoire trop basse), « timeout » (délai de 30 s dépassé), des réponses `429` (limite d'instances atteinte). Chacun correspond à un choix de la section 9.

---

## 6. Désactiver une fonction (si elle fait n'importe quoi ou coûte trop)

Par ordre de simplicité :

1. **La supprimer** (propre, réversible : on redéploie le même code plus tard) :
   ```bash
   cd C:\dev\miff-deploy\backend
   npx firebase functions:delete <nomDeLaFonction> --region europe-west9 --project miff-3d283
   ```
   (Exemple réel, déjà décidé : le retrait de `ping`, section 3, encadré « Retrait de `ping` » : `npx firebase functions:delete ping --region europe-west9 --project miff-3d283`.) La CLI demande confirmation : relis le nom de la fonction avant de répondre oui. **Attention** : tant que la fonction est présente dans le code de `main`, le déploiement suivant la remettra en ligne ; demande à Claude de la retirer du code (PR) avant de redéployer. Inversement, une fonction supprimée **et** absente du code ne revient pas.
2. **Pause rapide sans suppression** (si la console le permet) : Google Cloud > Cloud Run > cliquer le service de la fonction > onglet Sécurité > exiger l'authentification (ce qui bloque les appels publics). *Libellés non vérifiés : à confirmer dans la console ; en cas de doute, utilise la suppression.*
3. **Plafond de dépense** (section 1.2) : à 100 %, Google met les fonctions en pause tout seul jusqu'au mois suivant.
4. **Dernier recours, urgence financière** : prévenir Claude avant toute désactivation de la facturation du projet (conséquences sur Firestore et sur l'application non vérifiées).

---

## 7. Retour arrière (si un déploiement cause un problème)

Cloud Functions n'a pas de bouton « annuler ». Deux méthodes :

**Méthode A — redéployer l'ancien code (la normale).** Trouve le commit précédent (Claude le donne, ou `git log --oneline -- backend/functions`), puis :

```bash
cd C:\dev\miff-deploy
git switch --detach <identifiant-du-commit-precedent>
cd backend
npx firebase deploy --only functions --project miff-3d283 --message "Retour arriere vers <identifiant>"
```

Comme les anciennes « boîtes » techniques sont supprimées après 2 jours (section 3), le retour arrière **reconstruit** le code : compte quelques minutes.

**Méthode B — supprimer la fonction fautive** (section 6, option 1) quand le problème est urgent et que la fonction n'est pas indispensable.

Après un retour arrière : prévenir Claude (agent `backend-data`) pour corriger la cause avant un nouveau déploiement.

---

## 8. Coûts attendus

**Ordre de grandeur (estimations de l'étude `etude-serveur-notifications-premium.md`, §6 ; ce ne sont pas des mesures)** :

| Utilisateurs actifs par jour | 100 | 1 000 | 10 000 |
|---|---|---|---|
| Coût mensuel total (Firestore + fonctions) | ≈ 0 $ | ≈ 0,5 $ | ≈ 12 $ |
| Dont les fonctions elles-mêmes | 0 $ | 0 $ | 0 $ (≈ 75 % du quota gratuit) |

- **Tant qu'aucune notification n'est envoyée : 0 €.** Les 2 millions d'invocations par mois sont gratuites ; `notifierUpdate` ne s'exécute que lorsqu'un membre publie une update (la fonction `ping`, qui ne servait à rien, est retirée).
- Le coût dominant n'est **pas** les fonctions, mais les lectures Firestore de l'application elle-même (au-delà de 50 000 par jour). Voir `surveillance-quotas.md`.
- Lignes annexes, non chiffrées ici et normalement de l'ordre de quelques centimes : stockage des « boîtes » techniques (limité par la règle de 2 jours), journaux, construction (120 minutes par jour gratuites). **Non vérifié** : à lire dans Google Cloud > Facturation > Rapports après quelques semaines.
- **Cloud Scheduler** (utile plus tard pour « plus de nouvelles ») : 3 tâches gratuites **par compte de facturation**, pas par projet. Si le compte de facturation est partagé avec Ghostrun, il faut compter les tâches des deux projets.
- Seuil à surveiller : **toute facture Blaze supérieure à 5 € par mois** = revue de l'usage avant de continuer (suggestion de l'étude, à fixer par le PM).

---

## 9. Les choix « économiques » : leur limite et le signal pour changer

Chaque garde-fou ci-dessous est dans `backend/functions/src/options.ts` et **vérifié par un test** qui échoue si une fonction s'en écarte. Relever une limite se fait par une PR visible, jamais en silence.

| Choix | Pourquoi (impact) | Limite connue | Signal pour le changer |
|---|---|---|---|
| **3 instances au plus** par fonction (`maxInstances`) | Plafonne la dépense en cas d'emballement ou d'abus : c'est le frein principal | Avec 128 Mio chaque instance traite **une** demande à la fois : au plus ~3 demandes simultanées, les suivantes attendent jusqu'à 30 s et **peuvent être refusées (code 429)** | Réponses `429` dans les journaux, notifications en retard : relever à 5 pour **cette** fonction (plafond du test : 5). **`notifierUpdate` est déjà à 5** (le ticket parlait de 10 ; 5 suffit : 1 demande à la fois par instance, ~0,3 à 1 s par événement, soit plus de 100 000 événements par heure pour une estimation de 20 000 **par jour** à 10 000 utilisateurs actifs par jour). Relever au-delà de 5 (jusqu'à 10) = modifier `LIMITES.maxInstancesPlafond` ET le test, en PR, avec le nombre de `429` qui le justifie |
| **Mémoire 128 Mio** | La moins chère possible | Une fonction qui charge le SDK Admin (Firestore, FCM) peut manquer de mémoire | « Memory limit exceeded » dans les journaux : 256 Mio pour **cette** fonction (plafond du test). **`notifierUpdate` est déjà à 256 Mio** (SDK Admin Firestore + messagerie ; les événements portent la photo, jusqu'à 150 Ko, en mémoire) |
| **Délai 30 s** | Une fonction bloquée ne facture pas plus de 30 s | Un traitement long est coupé | Journaux « timeout » : relever (plafond du test : 60 s), ou découper le travail |
| **Pas de nouvelle tentative automatique** (`retry` désactivé) — **sauf `recevoirNotificationsGooglePlay` (SRV-7)**, autorisée dans `FONCTIONS_AVEC_RETRY_AUTORISEES` : traitement idempotent, erreur définitive consommée, erreur passagère abandonnée au bout de 24 h | Évite la boucle « échec → réessai → échec » qui fait exploser une facture | Un échec passager (service FCM indisponible quelques secondes) = **notification perdue** (`notifierUpdate` retente elle-même l'envoi 2 fois au plus, dans la même exécution, mais ne relance jamais l'exécution entière) | Des notifications manquantes avec un `ERROR` transitoire dans les journaux : activer `retry` pour **cette** fonction seulement, la rendre idempotente (sans double envoi) et l'ajouter à la liste `FONCTIONS_AVEC_RETRY_AUTORISEES` avec la raison |
| **Aucune instance permanente** (`minInstances: 0`) | Pas de facture quand personne n'appelle | Le premier appel après une pause est plus lent (démarrage à froid, de l'ordre de la seconde : non mesuré) | Plaintes de lenteur sur les notifications de géofence : 1 instance minimale pour la fonction concernée = coût fixe à chiffrer avec le PM |
| **Région Paris (europe-west9)**, 2e génération | Même région que la base (délais courts, données en UE) | Paris n'accepte que la 2e génération (c'est celle utilisée). Les déclencheurs Firestore doivent être dans la région de la base : c'est cohérent. FCM et les services de notification d'Apple restent des sous-traitants hors UE possibles (risque RGPD assumé, `data-model.md` §11) | Indisponibilité de la région : repli europe-west1 (Belgique), à décider |
| **Node.js 24** (repli prévu : Node 22 si la CLI refuse 24, voir §3) | Supporté par Firebase jusqu'à fin 2028 (dates affichées par la CLI : abandon annoncé 2028-04-30, arrêt 2028-10-31 ; Node 20 est arrêté depuis le 2026-10-30) | Dépendances à mettre à jour (Dependabot ouvre les PR chaque lundi) | E-mail de Google « runtime déprécié » ou PR Dependabot de changement de Node |
| **Journalisation minimale** (liste blanche) | Aucune donnée personnelle dans **nos** journaux applicatifs, et moins de volume (donc moins de coût) | On ne peut pas retrouver « qui » a causé une erreur depuis nos journaux. Les journaux techniques de Google gardent l'IP et le user-agent de l'appelant (hors de notre contrôle) | Besoin d'enquêter sur un incident : décision du PM au cas par cas (jamais de position ni de jeton) |
| **App Check + connexion exigés** sur toute fonction appelable (connexion anonyme refusée aussi) | Évite d'exécuter notre code pour un robot ou un compte anonyme. **Attention : une requête rejetée est quand même une invocation facturée qui occupe une instance** ; le vrai frein de coût reste `maxInstances` | Un téléphone sans App Check valide (appareil modifié, build non enregistré) est refusé. Un jeton App Check volé peut être rejoué : pour les fonctions sensibles (SRV-7), option `consumeAppCheckToken` (jeton à usage unique) | **Beaucoup de réponses 401 ou 429** dans les journaux : soit des robots (vérifier le tableau de bord App Check et la facture), soit des testeurs normaux bloqués (vérifier App Check, `app-check-strict.md`) |
| **Déploiement à la main** (jamais la CI) | Aucune clé de service à stocker | Chaque mise en ligne demande 10 minutes du PM | Déploiements plus d'une fois par semaine : revoir (décision écrite du PM, clé à portée minimale) |
| **Budget d'alerte 5 €/mois** (pas un plafond) | Alerte, sans frais | Données retardées de plusieurs heures ; ne bloque rien | Un e-mail à 50 % : regarder Facturation > Rapports le jour même |

---

## 9 bis. Deux points de sécurité à connaître

### Identité d'exécution : un compte de service dédié AVANT SRV-4

Une fonction s'exécute avec un « compte de service » (l'identité qu'elle présente à Google). Par défaut, c'est le **compte « Compute » par défaut du projet, qui a souvent le rôle Éditeur** : très large (il pourrait lire et modifier presque tout le projet). Aucune clé n'est en jeu, mais si une faille permettait de détourner une fonction, ce rôle large serait celui de l'attaquant.

- **Aucune exception à l'identité par défaut** : la seule fonction qui la gardait, `ping` (qui ne touchait aucune donnée), a été retirée parce que ce compte (« Compute Engine par défaut ») a potentiellement des droits très larges pour un bénéfice nul. La liste d'exceptions du code est **vide**, et un test de la CI le vérifie.
- **Pour toute fonction (lecture Firestore, envoi de notification, ou autre)** : créer un compte de service **dédié** (par exemple nommé `miff-functions`) avec **uniquement** les rôles nécessaires, puis le brancher dans le code avec l'option `serviceAccount` (le **nom** du compte, jamais une clé). Jamais le rôle Éditeur ni Propriétaire.
- Rôles (confirmés dans la documentation Google le 2026-11-12 pour `notifierUpdate`, voir section 1.4) : **4 rôles de projet** : accès aux données Firestore (rôle « Utilisateur Cloud Datastore » ; **attention**, un compte de service ignore les règles de sécurité Firestore : ce rôle donne donc accès à toute la base, positions comprises, d'où l'importance de ne l'attribuer qu'aux fonctions qui en ont besoin), envoi de notifications (« Firebase Cloud Messaging API Admin »), réception des événements (« Récepteur d'événements Eventarc ») et écriture des journaux. **Plus 1 droit par service** (confirmé en production le 2026-11-13) : `Cloud Run Invoker` (`roles/run.invoker`) **sur le seul service Cloud Run de la fonction** (`notifierupdate`), à poser après le premier déploiement. Toute future fonction déclenchée par Eventarc aura le même besoin, **sur son propre service**.
- Création et attribution des rôles : **par le PM** dans la console Google Cloud > IAM, **pas à pas en section 1.4** (compte `notify-update`, 4 rôles de projet, droit `run.invoker` sur le service après le déploiement, droit « Utilisateur du compte de service » pour celui qui déploie).
- Le test `garde-couts.test.mjs` n'accepte `serviceAccount` que sous la forme `serviceAccount: "nom-de-compte-dedie"` (jamais un compte par défaut, jamais une valeur calculée), et refuse tout compte au nom évoquant Éditeur, administrateur ou propriétaire.

### Scripts d'installation des dépendances

Deux dépendances de Firebase ont un script d'installation : `@firebase/util` et `protobufjs`. En local et dans la CI, les commandes utilisent `npm ci --ignore-scripts`, qui les **neutralise**. **Cloud Build (chez Google), lui, ne les neutralise pas** : il exécute l'installation normale au déploiement. Le risque est celui de toute dépendance (code malveillant introduit dans une version publiée) : il est réduit par les versions **épinglées** et le lockfile (pas de mise à jour surprise) et par la relecture des PR Dependabot (une PR par semaine pour `firebase-functions` et `firebase-admin`). À relire à chaque mise à jour : les notes de version et l'apparition éventuelle d'un **nouveau** script d'installation dans `package-lock.json` (`hasInstallScript`).

---

## 10. Pourquoi jamais depuis la CI (détail)

- Il faudrait créer une **clé de compte de service** avec le droit de déployer des fonctions (un droit très large), la stocker comme secret GitHub et la renouveler. `docs/dev/secrets.md` interdit d'en créer.
- Un déploiement automatique sur une fusion supprimerait le « oui » explicite du PM.
- Les fonctions s'exécutent avec une **identité gérée par Google** : aucune clé n'existe, ni côté dépôt, ni côté code. L'identité par défaut est cependant trop large (voir §9 bis) : **toute fonction, dont `notifierUpdate`, utilise un compte de service dédié aux droits minimaux** (section 1.4) ; un test refuse toute fonction sans compte dédié explicite.

Si ce choix change un jour, il se prend par une décision écrite du PM, avec une clé à portée minimale et les tests `project-guard.test.js` / `functions-config.test.js` adaptés en conséquence.


---

## 11. La fonction `notifierUpdate` (SRV-4B) : ce qu'elle fait, la mettre en ligne, la tester, l'arrêter

Ticket : `docs/tickets/notification-update-membres.md`. Recette : `docs/qa/checklists/notif-update-membres-recette-pm.md`.

### 11.1 Ce qu'elle fait (en clair)

Quand un membre **publie une update** (une photo écrite dans `miffs/{miff}/photos/{membre}`), la fonction envoie **une notification discrète aux autres membres de la Miff** : « {Prénom} a donné des nouvelles / Retrouve-les dans ta Miff. » (le texte est composé par l'application ou par le système du téléphone à partir de clés de texte ; le serveur n'envoie que le prénom). Elle part **uniquement** si :
- la personne qui publie **partage sa position** avec la Miff ;
- le destinataire est **encore membre**, **partage lui aussi**, a **au moins un téléphone enregistré** (jeton) **correspondant à sa session active** (un téléphone dont le propriétaire s'est déconnecté, ou dont un autre compte a pris la place, ne reçoit rien) et n'a pas coupé « Quand un proche donne des nouvelles » ;
- il n'y a pas déjà eu une notification **pour cet auteur dans cette Miff dans l'heure** (les updates suivantes restent visibles dans le Feed, sans notification) ;
- la publication a **moins de 5 minutes** au moment du traitement.

**Ce qu'elle n'envoie jamais** : la photo, la position, un nom de lieu, le nom de famille, le nom de la Miff, la légende, la batterie, la couleur du statut. Le message contient le **prénom** (20 caractères au plus), l'**identifiant technique de la Miff** et celui de **l'auteur** (pour ouvrir la bonne carte et remplacer l'ancienne notification du même auteur), et, sur Android, un indicateur « nuit ». Il n'est **jamais envoyé à un « sujet »** mais **appareil par appareil**. Durée de vie : **2 heures** (au-delà, le message est abandonné par Google/Apple). **Heures calmes 22 h – 7 h** (heure du destinataire, d'après le fuseau enregistré avec son téléphone ; fuseau inconnu = calme) : Android reçoit l'indicateur et l'application affiche sans son ni vibration ; iOS reçoit une notification de niveau « passif ». Le jour : son et vibration selon la configuration du téléphone (décision du PM du 2026-11-12).

**Ce qu'elle écrit** : une seule chose, dans `notifState/{miff}` (collection **réservée au serveur**, refusée à toute application par la règle « tout le reste est refusé ») : pour chaque auteur, l'heure de sa dernière publication traitée et de sa dernière notification, plus une date de suppression automatique à **+2 jours**. Elle **supprime** aussi le document d'un téléphone quand Google répond que son jeton n'existe plus (appli désinstallée). Elle **n'écrit jamais** dans `photos/` (pas de boucle sur elle-même).

**Garanties et limites** : **au plus une fois** (la fonction note qu'elle a traité l'événement **avant** d'envoyer : si elle plante entre les deux, **ou si Google/Apple sont en panne juste après**, la notification est perdue **et le plafond de l'heure est consommé** pour cet auteur dans cette Miff ; elle ne sera jamais doublée). Une exception non prévue est journalisée par un code fixe (`notif.update.erreur`), sans détail, et n'est pas relancée. Si Google ou Apple sont en panne passagère : **2 nouvelles tentatives** (0,5 s puis 1,5 s) puis abandon. Si la fonction est indisponible, **rien d'autre ne casse** : la publication réussit et le Feed est à jour.

### 11.2 Coût et lectures (comptés par la fonction, vérifiés à l'émulateur)

| Cas | Lectures Firestore | Écritures |
|---|---|---|
| Miff de **4 membres**, 3 destinataires avec 1 téléphone chacun | **13** (1 contrôle préalable + 1 Miff + 4 fiches + 3 × 2 [session puis téléphone de la session] + 1 vérification finale) | **1** (l'état) |
| Miff de **15 membres** | **46** | 1 |
| Miff de **40 membres** | **73** au plus (74 si l'auteur est hors des 30 premières fiches) : 1 + 1 + 30 fiches + 20 × 2 [session + téléphone] + 1 | 1 |
| Update plafonnée (2e de l'heure) ou rejouée | **1** (le contrôle préalable arrête tout avant de lire fiches, sessions et téléphones) | **0** |
| Miff supprimée (document absent, fiches restées) | **2** (aucun envoi) | 0 |
| Auteur seul dans sa Miff | **3** | 0 |
| Événement de plus de 5 min, suppression de photo | **0** | 0 |
| Jeton invalide purgé | n/a | +1 par jeton |

Ces nombres sont **additionnés par la fonction elle-même** (chaque lecture est comptée dans son bilan) et vérifiés par les tests à l'émulateur ; ce ne sont pas des chiffres de facturation Google. Le bilan de chaque exécution figure dans les journaux (`notif.update.bilan`, champs `lectures` et `ecritures`) : tu peux le comparer au rapport de facturation. Les envois FCM sont gratuits. Estimation du ticket : ≈ 0 $ à 100 utilisateurs actifs par jour, ≈ 0,2 $/mois à 1 000, ≈ 2,4 $/mois à 10 000 (hypothèses, pas des mesures).

### 11.3 La mettre en ligne (c'est l'interrupteur de mise en service)

**Prérequis** : section 1.4 (compte de service et ses 4 rôles, règles des jetons), checklist de la section 2, textes légaux publiés, applications avec jetons distribuées (SRV-2A / SRV-2I). **Le « oui » du PM est écrit dans la conversation avec Claude.** Tant que la fonction n'est pas déployée, les apps enregistrent des jetons mais **rien n'est envoyé**. **Après le tout premier déploiement** : poser `Cloud Run Invoker` sur le service (section 1.4, étape D bis), puis la règle TTL après la première notification (étape E). **État : déployée le 2026-11-13** (commit `2aacad1d`).

```bash
cd C:\dev\miff-deploy\backend
npx firebase deploy --only functions:notifierUpdate --project miff-3d283
```

(`--only functions:notifierUpdate` ne touche à aucune autre fonction. Les questions possibles sont celles de la section 3.) **Un échec du type « le compte de service n'existe pas » ou « permission `iam.serviceAccounts.actAs` refusée »** veut dire que la section 1.4 (étape A ou C) n'est pas terminée. **Ne contourne pas** en demandant de retirer l'option de compte dans le code : préviens Claude.

### 11.4 La tester après déploiement avec un compte de test

Matériel : **deux comptes de test** dans **une même Miff de test**, les deux avec le partage activé, chacun sur son téléphone (un appareil par compte), notifications autorisées sur celui qui reçoit, réglage « Quand un proche donne des nouvelles » activé. **Pas de vrais proches dans cette Miff.**
1. Le compte 1 publie une update. Attendu : le compte 2 reçoit « {Prénom} a donné des nouvelles » en quelques secondes (2 minutes au plus), **sans photo ni lieu** ; le compte 1 ne reçoit rien.
2. Journaux (section 5) : `npx firebase functions:log --only notifierUpdate -n 30 --project miff-3d283`. Attendu : une ligne `notif.update.bilan` avec `envoyes: 1`, `lectures: 7` (2 comptes : 1 contrôle préalable + 1 Miff + 2 fiches + 1 session + 1 téléphone + 1 vérification finale), `ecritures: 1`, **sans aucun nom, identifiant ni jeton**. Une ligne `notif.update.fcm-erreur` avec `code` 8 = clé APNs absente ou refusée côté Apple (ticket §12, étape du PM) ; `code` 1 = jeton inconnu (purgé) ; `code` 3 ou 4 = panne passagère de Google.
3. Le compte 1 publie une 2e update **dans l'heure** : pas de nouvelle notification (`notif.update.ignore`, `motif: plafond`), la photo est visible dans le Feed.
4. Console Firebase > Firestore : la collection `notifState` contient un document par Miff de test, **sans nom ni jeton** (que des dates) ; la règle TTL est visible (**à poser après cette première notification**, section 1.4 E).
5. Console Firebase > Functions : instances maximales 5, mémoire 256 Mio, compte `notify-update@…`.
6. Journaux : **aucun** message « The request was not authenticated… » (sinon, section 1.4 D bis).

**Essai réel du 2026-11-13** : iPhone (compte A) publie, OPPO (compte B) reçoit ; `notif.update.bilan` ok, **13 lectures**, **1 envoi**, **2 écartés « sans appareil »**.

Le reste du scénario est dans la recette PM (`docs/qa/checklists/notif-update-membres-recette-pm.md`).

### 11.5 L'arrêter rapidement

| Besoin | Action | Délai |
|---|---|---|
| **Arrêt net** (la fonction n'existe plus) | `npx firebase functions:delete notifierUpdate --region europe-west9 --project miff-3d283` (relire le nom avant de confirmer). Les publications continuent de fonctionner ; les apps gardent leurs jetons. Tant que la fonction est dans le code de `main`, un déploiement complet la remettrait en ligne : demander à Claude de la retirer du code (PR) avant de redéployer | 1 à 2 minutes |
| **Coupure d'urgence sans toucher à la fonction** *(non vérifié, à confirmer dans la console)* | Google Cloud > IAM > retirer le rôle **Administrateur de l'API Firebase Cloud Messaging** du compte `notify-update@…` : la fonction ne peut plus envoyer (elle journalise une erreur) et ne coûte presque rien ; on remet le rôle ensuite | Immédiat |
| **Limiter le coût** | Plafond de dépense Cloud Functions (section 1.2) ; alerte de budget (section 1.1) | n/a |

### 11.6 Retour arrière

Méthode A ou B de la section 7. Après un retour arrière ou une suppression : les documents `notifState` restants se suppriment seuls sous 2 jours (TTL) ; ils ne contiennent que des identifiants et des dates, et ne servent qu'à la fonction. La fonction ne modifie aucune donnée d'utilisateur (hormis la suppression des jetons que Google déclare inconnus).

### 11.7 Ce qui n'est PAS vérifiable à l'émulateur (à vérifier à la recette)

- La **livraison réelle** par Firebase Cloud Messaging (jeton valide, délai, OPPO/ColorOS, application balayée ou « forcée à l'arrêt »).
- **APNs** : la clé `.p8` dans la console Firebase, l'environnement de production de TestFlight, le rendu des clés de texte (`notif_circle_update_title` / `_body`) par le système, le niveau « passif », le son du téléphone.
- La **suppression automatique (TTL)** de `notifState` (règle à poser après la première notification, étape 1.4 E ; l'effet réel n'est visible qu'après l'échéance de 2 jours).
- La **réception sur iPhone** (APNs) : l'essai du 2026-11-13 a vérifié l'envoi vers Android (OPPO) seulement.

**Confirmé le 2026-11-13, au premier déploiement** (essai de bout en bout, iPhone compte A publie, OPPO compte B reçoit) :
- Le **rôle FCM** du compte de service : `roles/firebasecloudmessaging.admin` **suffit seul** (1 envoi réussi).
- Le **déclencheur de production** (Eventarc) avec le compte dédié : les 4 rôles de projet (dont `eventarc.eventReceiver`) **ne suffisent pas** : il faut en plus `Cloud Run Invoker` (`roles/run.invoker`) **sur le service `notifierupdate`** (section 1.4, étape D bis). Sans lui : « The request was not authenticated… lacks {run.routes.invoke} permission ». Ce point, noté « à confirmer au premier déploiement », est **confirmé le 2026-11-13**.

---

## 12. Le premium réel Miff+ (SRV-7) : cinq fonctions, un compte de service, un sujet, un secret, trois règles TTL

Ticket : `docs/tickets/premium-achat-reel.md` (§10, §14, §16). Modèle de données : `backend/data-model.md` §2.3 et §2.22 à §2.25. **État : code écrit et testé, NON DÉPLOYÉ.** Aucun compte, aucune clé, aucun secret n'a été créé par un agent ; **toutes les étapes ci-dessous sont à faire par le PM**, qui tape lui-même chaque commande (jamais la CI, jamais un agent).

### 12.1 Ce que ça fait (en clair)

Quand quelqu'un achète Miff+ dans l'app (Google Play ou App Store), **un serveur Miff vérifie l'achat auprès du store** et allume (ou éteint) le droit Miff+ de ce compte tout seul : essai, actif, annulé (accès jusqu'à la fin de la période payée), période de grâce (accès conservé), suspendu (accès coupé), remboursé (accès coupé tout de suite), expiré. **Une app ne peut jamais s'accorder Miff+ elle-même** : les règles Firestore l'interdisent et seul le serveur écrit le droit. Le Miff+ accordé « à la main » (`docs/dev/premium-manuel.md`) continue de fonctionner (il n'est écrasé que par un achat qui va plus loin).

| Fonction | Déclencheur | Rôle |
|---|---|---|
| `preparerAchat` | appelable (connexion + App Check) | donne à l'utilisateur son **identifiant d'achat aléatoire** (transmis au store à l'achat, pour retrouver son compte sans jamais envoyer son e-mail ni son UID) |
| `synchroniserAchat` | appelable | vérifie un achat présenté par l'app (jeton Google / transaction signée Apple), le lie au compte, accorde le droit, **acquitte l'achat chez Google** (sinon Google rembourse sous 3 jours) ; **1 appel par minute et par compte** |
| `recevoirNotificationsGooglePlay` | message Pub/Sub `play-rtdn` | reçoit les changements d'état de Google (renouvellement, annulation, paiement refusé, remboursement) et **relit l'état chez Google** |
| `recevoirNotificationsApple` | **adresse HTTPS publique** | reçoit les notifications d'Apple, **vérifie leur signature** et relit l'état chez Apple. **Seul point d'entrée du projet joignable sans connexion ni App Check** |
| `supprimerDonneesAbonnement` | appelable | purge à la suppression du compte (identifiant d'achat, liens ; droit inactif et supprimé 30 jours après) |

**Limites (garde-fous vérifiés par `garde-couts.test.mjs` et `garde-facturation.test.mjs`)** : région `europe-west9`, 2e génération, Node 24, **3 instances au plus, 256 Mio, 30 s, 0 instance permanente**, compte `billing-sync@miff-3d283.iam.gserviceaccount.com` **sans clé**, **nouvelle tentative automatique seulement pour la fonction Pub/Sub** (traitement idempotent ; message abandonné au bout de 24 h), jamais de révocation ni de remboursement par le serveur.

### 12.2 L'ordre complet, avec ce qui est bloquant

| # | Action du PM | Bloquant pour | Délai probable (estimation) |
|---|---|---|---|
| 1 | API Google à activer (§12.3) | le déploiement | 5 min |
| 2 | Compte de service `billing-sync` + 3 rôles (§12.4) | le déploiement | 15 min |
| 3 | Sujet Pub/Sub `play-rtdn` + droit de publication à Google Play (§12.5) | le déploiement (le déclencheur) | 10 min |
| 4 | Déployer les 5 fonctions, **après CI verte, revue de sécurité et « oui » écrit** (§12.8) | tout le reste | 10 à 20 min (1re construction) |
| 5 | Droit `Cloud Run Invoker` sur le service `recevoirnotificationsgoogleplay` (§12.9) | la réception des messages Google | 5 min |
| 6 | Inviter `billing-sync` dans la Play Console (§12.6) | tout appel à l'API Google Play | 10 min ; **prise en compte non précisée par Google** (quelques minutes à quelques heures : non vérifié) |
| 7 | Règle TTL de `entitlements.purgeAt` (§12.10) | rien (hygiène) ; à poser avant l'ouverture publique | 5 min |
| 8 | Play Console : produits, notifications en temps réel, testeurs (ticket §14.2) | l'essai Android de bout en bout | **dépend de SRV-8A** : les fonctions de monétisation de la Play Console n'apparaissent qu'après publication d'une version contenant la Billing Library (piste interne suffisante) |
| 9 | Règles TTL de `billingEvents.purgeAt` et `billingPurchases.purgeAt`, après le premier message (§12.10) | rien (hygiène) | 5 min, après le 1er achat de test |
| 10 | **Apple** : accord « Paid Apps », produits, clé « In-App Purchase » (ticket §14.1) | tout le côté iOS | **jours** : coordonnées bancaires et fiscales saisies par vous seul ; relecture des produits par Apple (24 à 48 h habituellement : non vérifié) |
| 11 | **Apple** : secret `apple-iap-key` + document `billingConfig/apple` (§12.11) | les achats iOS (Android n'est pas touché) | 15 min, une fois la clé Apple créée |
| 12 | **Apple** : adresse des notifications dans App Store Connect (§12.12) | les notifications iOS | 5 min, après l'étape 4 |

**Les étapes 1 à 5 ne dépendent d'aucun compte de store** : on peut déployer le serveur dès maintenant, bien avant que les produits existent. **Android passe en premier** (aucun secret). **Apple peut attendre** : sans l'étape 11, une demande iOS reçoit un refus calme « indisponible » et les notifications d'Apple reçoivent 503 (Apple réessaie 5 fois sur 72 h en production), sans rien casser côté Google.

### 12.3 Étape 1 : activer les API (projet `miff-3d283`)

Console Google Cloud (https://console.cloud.google.com/apis/library, **projet `miff-3d283` en haut de la page**) : rechercher et **Activer** :
1. **Google Play Android Developer API** (`androidpublisher.googleapis.com`) : obligatoire.
2. **Cloud Pub/Sub API** (`pubsub.googleapis.com`) : obligatoire (souvent déjà active : Eventarc l'utilise).
3. **Secret Manager API** (`secretmanager.googleapis.com`) : **seulement pour Apple** (étape 11).
4. **IAM Service Account Credentials API** (`iamcredentials.googleapis.com`) : **seulement si le repli du §12.7 est nécessaire**.

Activer une API ne coûte rien en soi. Libellés recopiés de la documentation (en anglais) : en cas de doute, **copiez ce que vous voyez à Claude**.

### 12.4 Étape 2 : le compte de service `billing-sync` (sans clé) et ses rôles

Même procédure que le §1.4 (étapes A à C), avec ces valeurs :
- **Nom** : `billing-sync` (adresse complète `billing-sync@miff-3d283.iam.gserviceaccount.com`, écrite dans le code : une autre adresse serait refusée par les tests). Description : `Fonctions de facturation Miff+ : lit et écrit Firestore, appelle Google Play et Apple`.
- **Ne créez aucune clé** (onglet « Clés » vide). Si on vous propose de télécharger un fichier `.json`, **refusez**.
- **Rôles de projet : exactement ces 3** (IAM > Accorder l'accès) :

| Rôle | À quoi il sert |
|---|---|
| **Utilisateur Cloud Datastore** (`roles/datastore.user`) | lire et écrire Firestore (accès de l'Admin SDK). **Attention** : un compte de service ignore les règles de sécurité, donc ce rôle donne accès à **toute** la base (positions comprises) : c'est le prix de l'écriture de `entitlements` par le serveur ; réduit par un code court, des entrées bornées et des tests (risque R2 du ticket). La granularité par collection n'est pas confirmée par Google : non vérifié |
| **Récepteur d'événements Eventarc** (`roles/eventarc.eventReceiver`) | recevoir les messages du sujet Pub/Sub |
| **Rédacteur de journaux** (`roles/logging.logWriter`) | écrire ses journaux (sans donnée personnelle) |

**Pas de rôle FCM** (cette fonction n'envoie aucune notification). **Jamais** Éditeur, Propriétaire, Administrateur, ni `Cloud Run Invoker` au niveau du projet.
- **Donnez-vous** le rôle « Utilisateur du compte de service » sur ce compte (étape C du §1.4) : sans lui, le déploiement échoue.
- **Plus tard, par fonction** (étapes 5 et 11) : `Cloud Run Invoker` sur **un seul** service Cloud Run, et « Accesseur de secrets » sur **un seul** secret.

### 12.5 Étape 3 : le sujet Pub/Sub `play-rtdn` et son éditeur

Google Play publie ses notifications dans un sujet Pub/Sub **que vous créez**. Seul l'éditeur nommé ci-dessous doit pouvoir y publier.
1. https://console.cloud.google.com/cloudpubsub/topic/list (projet `miff-3d283`) > **Créer un sujet** (« Create topic ») : **ID du sujet** `play-rtdn` ; **décochez** « Ajouter un abonnement par défaut » (le déploiement crée le sien) ; chiffrement : par défaut. **Créer.**
   - Ou dans Cloud Shell : `gcloud pubsub topics create play-rtdn --project=miff-3d283`
2. Ouvrez le sujet > onglet **Autorisations** (« Permissions ») > **Ajouter un compte principal** : `google-play-developer-notifications@system.gserviceaccount.com`, rôle **Éditeur Pub/Sub** (« Pub/Sub Publisher »). **Enregistrer.** Source : documentation Google Play Billing, section « Grant publish rights on your topic ».
3. **N'ajoutez personne d'autre** : un message falsifié ne peut de toute façon rien accorder (le serveur ne croit jamais le contenu d'un message, il relit l'état chez Google), mais le sujet n'a pas besoin d'autre éditeur.
4. Le **nom complet** à saisir plus tard dans la Play Console est `projects/miff-3d283/topics/play-rtdn`.

### 12.6 Étape 6 : inviter `billing-sync` dans la Play Console (droits sur les abonnements)

Play Console > **Utilisateurs et autorisations** > **Inviter de nouveaux utilisateurs** : adresse e-mail `billing-sync@miff-3d283.iam.gserviceaccount.com` ; droits **« Afficher les données financières, les commandes et les réponses aux enquêtes d'annulation »** et **« Gérer les commandes et abonnements »** (au niveau du compte développeur ou de l'app Miff seulement : pas besoin de plus). **Inviter.** Google ne demande **plus** de lier la Play Console à un projet Cloud (documentation : « You no longer need to link your developer account to a Google Cloud Project »). **Le délai de prise en compte n'est pas précisé par Google.** Source : https://developers.google.com/android-publisher/getting_started

### 12.7 Hypothèse à confirmer au premier essai réel : l'accès à l'API Google Play SANS clé (critère BK9)

**Ce qui est vérifié** : la documentation Google décrit l'invitation d'un compte de service dans la Play Console et la portée `https://www.googleapis.com/auth/androidpublisher`. **Ce qui n'est PAS vérifié** (aucune page de Google ne le dit) : qu'une fonction qui tourne sous l'identité du compte `billing-sync` (sans clé) obtient automatiquement un jeton de **cette** portée. Le code essaie dans cet ordre, **sans aucune clé** :
1. le jeton de l'environnement avec la portée `androidpublisher` ;
2. si Google Play répond 401 ou 403, **le compte se fait émettre lui-même** un jeton de la bonne portée (service IAM Credentials : « usurpation d'identité de soi-même », documentée par Google) et **retient ce repli** pour la vie de l'instance.

**Comment savoir où l'on en est** : après le premier appel réel (message de test ou achat de test), les journaux de `synchroniserAchat` / `recevoirNotificationsGooglePlay` affichent :
- `billing.sync.ok` ou `billing.google.notif` `resultat: ok` : **ça marche** (BK9 confirmé : notez la date dans la conversation) ;
- `billing.sync.refuse` / `billing.google.notif` avec `motif: acces_refuse` : **deux causes possibles, dans cet ordre** : (a) l'invitation de l'étape 6 n'est pas encore prise en compte ou les droits sont incomplets (attendez, vérifiez la page « Utilisateurs et autorisations ») ; (b) le repli est nécessaire : **activez l'API « IAM Service Account Credentials »** (§12.3, point 4) et, sur la page du compte `billing-sync` > **Autorisations** > **Accorder l'accès**, donnez à **`billing-sync@miff-3d283.iam.gserviceaccount.com` lui-même** le rôle **« Créateur de jetons de compte de service »** (`roles/iam.serviceAccountTokenCreator`) **sur ce compte seulement** (jamais au niveau du projet), puis réessayez. **Aucun redéploiement n'est nécessaire.** Si l'erreur persiste : copiez le message à Claude, **n'élargissez aucun rôle**.

### 12.8 Étape 4 : le déploiement

**Prérequis** : étapes 1 à 3 faites, §2 (checklist) vérifiée, **PR fusionnée, CI verte, revue de sécurité faite, « oui » du PM écrit pour ce commit**, alerte de budget en place (§1.1), plafond de dépense des fonctions (§1.2). **Textes légaux** : ils n'ont pas à être publiés pour déployer ces fonctions (aucun achat n'existe tant que SRV-8A / SRV-8I ne sont pas livrés) mais **avant l'ouverture des achats** (SRV-3).

```bash
cd C:\dev\miff-deploy\backend
npm ci
npx firebase deploy --only functions:preparerAchat,functions:synchroniserAchat,functions:supprimerDonneesAbonnement,functions:recevoirNotificationsGooglePlay,functions:recevoirNotificationsApple --project miff-3d283 --dry-run
```
Si l'essai à blanc réussit, relancez **la même commande sans `--dry-run`**. (`--only functions:nom1,functions:nom2…` ne touche aucune autre fonction, ni `notifierUpdate` ni `notifierAlerte`.) Les questions possibles sont celles du §3 ; réponses : voir le tableau. **Une demande de suppression de fonctions absentes du code : répondez NON.** **Rien n'est facturé à l'instance permanente** (aucune), et aucune question ne doit parler d'instances minimales (sinon, annulez).

**Ce que la CLI affiche** : pour `recevoirNotificationsApple`, une ligne `Function URL (recevoirNotificationsApple(europe-west9)): https://…` : **copiez cette adresse** (étape 12). Elle est aussi dans Firebase > Functions.

**Erreurs possibles** : (a) « Permission denied » liée à l'agent de service Eventarc ou Pub/Sub : un droit que Google vient d'accorder met quelques minutes à se propager : **attendez et relancez la même commande**, ne donnez aucun rôle « pour faire passer » ; (b) « Topic not found » : le sujet de l'étape 3 n'existe pas ; (c) « region not supported » pour le déclencheur Pub/Sub : **hypothèse non vérifiée** (Eventarc gère Paris pour Firestore, déjà utilisé par `notifierUpdate`, mais la page de Google ne liste pas explicitement `europe-west9` pour les déclencheurs Pub/Sub) ; **ne forcez rien**, copiez le message à Claude (repli prévu : région `europe-west1` pour cette seule fonction, modification du code et des tests en PR).

### 12.9 Étape 5 : `Cloud Run Invoker` sur le service `recevoirnotificationsgoogleplay` (APRÈS le premier déploiement)

Même mécanisme que `notifierupdate` (§1.4, étape D bis) : le déclencheur Pub/Sub appelle le service Cloud Run de la fonction **en se faisant passer pour `billing-sync`**, qui doit avoir le droit de l'appeler **sur ce seul service**.

```bash
gcloud run services add-iam-policy-binding recevoirnotificationsgoogleplay --region=europe-west9 --member="serviceAccount:billing-sync@miff-3d283.iam.gserviceaccount.com" --role="roles/run.invoker" --project=miff-3d283
```
(ou console : Cloud Run > cocher `recevoirnotificationsgoogleplay` > Permissions > Ajouter un compte principal.) **Jamais** « Allow public access » sur ce service. Sans ce droit, les journaux affichent « The request was not authenticated… lacks {run.routes.invoke} permission » et **aucun message n'est traité** (les messages restent dans Pub/Sub et sont relivrés dès que le droit est posé, pendant 7 jours). **Les trois fonctions appelables et `recevoirNotificationsApple` n'ont pas besoin de ce droit** : leurs services sont publics par conception (la sécurité est dans le code : connexion + App Check pour les appelables, signature pour Apple).

### 12.10 Les règles de durée de vie (TTL) : trois, à poser par le PM

Console Google Cloud > Firestore > **Time-to-live** (libellé non vérifié ; en cas de doute, copiez ce que vous voyez). Pour chacune : **Créer une règle** > groupe de collections + champ :

| Groupe de collections | Champ | Effet | Quand |
|---|---|---|---|
| `entitlements` | `purgeAt` | supprime l'accès 30 jours après une suppression de compte (seuls les comptes supprimés ont ce champ : un accès actif ou manuel n'est **jamais** supprimé) | maintenant (la collection existe) |
| `billingEvents` | `purgeAt` | supprime l'identifiant d'un message traité 7 jours après | **après le premier message traité** (la collection n'existe pas avant) |
| `billingPurchases` | `purgeAt` | supprime un lien d'achat 90 jours après l'échéance | **après le premier achat de test** |

La suppression est « typiquement dans les 24 h » après l'échéance (documentation Google) et n'est pas gratuite à l'usage sur Blaze (négligeable : quelques suppressions par abonné et par an). **Tant que la règle n'existe pas, rien ne casse** : les lignes restent, voilà tout.

### 12.11 Étape 11 : Apple, le secret et la configuration (quand la clé Apple existe)

**Prérequis** : la clé « In-App Purchase » (`.p8`) créée dans App Store Connect (ticket §14.1, point 7) ; vous avez noté son **identifiant de clé** (10 caractères), l'**Issuer ID** (un UUID) et l'**identifiant Apple de l'app** (un nombre, App Store Connect > Informations sur l'app). **La clé `.p8` ne passe ni par le dépôt, ni par une conversation, ni par un e-mail, ni par une capture d'écran.**
1. **Créer le secret** (Secret Manager > **Créer un secret**, projet `miff-3d283`) : **nom** `apple-iap-key` (exactement) ; **valeur** : **Importer un fichier** et choisir votre `.p8` (ne le collez pas) ; **région** : « Gérée par l'utilisateur » > `europe-west9` (les données restent en Europe) ; **Créer.** Ou dans Cloud Shell, avec le fichier téléversé ou en local sur votre poste : `gcloud secrets create apple-iap-key --replication-policy=user-managed --locations=europe-west9 --data-file=CHEMIN\VERS\AuthKey_XXXX.p8 --project=miff-3d283`. **Supprimez ensuite toute copie** du `.p8` hors de votre gestionnaire de mots de passe.
2. **Droit de lecture, sur CE secret seulement** : ouvrir le secret > **Autorisations** > **Accorder l'accès** : `billing-sync@miff-3d283.iam.gserviceaccount.com`, rôle **« Accesseur de secrets Secret Manager »** (`roles/secretmanager.secretAccessor`). **Jamais** ce rôle au niveau du projet.
3. **Configuration non secrète** : Firestore (console Firebase, projet **Miff**) > **Démarrer une collection** `billingConfig` > document **`apple`** avec **trois champs** exactement : `keyId` (texte, 10 caractères majuscules/chiffres), `issuerId` (texte, l'UUID), `appAppleId` (**nombre** entier). Les apps n'y ont **aucun accès** (règles : tout refusé). La configuration est relue au plus toutes les 5 minutes : **aucun redéploiement**.
4. **Rotation** (clé compromise ou renouvelée) : App Store Connect > révoquer la clé et en créer une autre ; Secret Manager > `apple-iap-key` > **Nouvelle version** ; mettre à jour `keyId` dans `billingConfig/apple`. Aucun redéploiement (cache de 5 minutes). **En cas de fuite : révoquer d'abord** (`docs/dev/secrets.md`).

### 12.12 Étape 12 : l'adresse des notifications Apple

App Store Connect > l'app Miff > **Informations sur l'app** > **Notifications du serveur App Store** (version 2) : coller **l'adresse copiée au §12.8** dans **l'URL de production** ET **l'URL du bac à sable** (une seule fonction traite les deux environnements : elle lit l'environnement dans la notification signée). Emplacement exact des champs : non vérifié, copiez ce que vous voyez. Apple n'attend pas de jeton : **l'adresse est publique par conception**, la sécurité est la vérification de la signature (chaîne de certificats jusqu'à la racine d'Apple, fonction testée avec de fausses chaînes).

### 12.13 Vérifications après le déploiement

| # | Vérification | Où | OK si |
|---|---|---|---|
| 1 | Les 5 fonctions sont en ligne | Firebase > Functions, ou `npx firebase functions:list --project miff-3d283` | `preparerAchat`, `synchroniserAchat`, `supprimerDonneesAbonnement`, `recevoirNotificationsGooglePlay`, `recevoirNotificationsApple`, région **europe-west9**, 2e génération, Node.js 24 |
| 2 | Limites et compte | détail de chaque fonction | instances maximales **3**, mémoire **256 Mio**, délai **30 s**, instances minimales **0**, compte **`billing-sync@miff-3d283…`** (jamais « Compute Engine default ») |
| 3 | Exposition | Cloud Run > chaque service > Sécurité | **`recevoirnotificationsapple`** et les trois appelables sont ouverts à l'infrastructure (« Autoriser l'accès non authentifié ») **par conception** : les appelables exigent App Check et la connexion **dans le code**, Apple s'authentifie par la **signature** ; **`recevoirnotificationsgoogleplay` n'est PAS public** (invoker : `billing-sync` seulement) |
| 4 | Aucune clé | page du compte `billing-sync` > Clés | vide |
| 5 | Message de test Google | Play Console > Monétiser > Configuration > Notifications en temps réel > **Envoyer un message de test** (après l'étape 8) | journal `billing.google.ignore` avec `motif: test_notification`, aucune erreur « not authenticated » |
| 6 | Appel de bout en bout (Android, testeur de licence, **carte de test**, jamais un vrai achat) | journaux (§12.14) puis Firestore | `billing.sync.ok` ; `entitlements/{uid}` à jour (`source: google_play`, `test: true`) ; `billingPurchases` présent **sans jeton** ; l'achat est **acquitté** dans la Play Console |
| 7 | Refus d'un autre compte | essai avec un second compte Miff sur le même achat | refus calme (`billing.sync.refuse`, `motif: deja_lie`), aucun détail sur l'autre compte |
| 8 | Journaux sans donnée personnelle | Firebase > Functions > Journaux | seulement des codes `billing.*`, jamais de jeton, d'identifiant, d'UID ni d'e-mail |
| 9 | Pas de dépense | Facturation > Rapports (le lendemain) | 0 € |

### 12.14 Lire les journaux de facturation

| Code | Veut dire | Si ça se répète |
|---|---|---|
| `billing.prepare.ok` | identifiant d'achat remis | rien |
| `billing.sync.ok` | achat vérifié et accordé (champs `lectures`, `ecritures`) | rien |
| `billing.sync.refuse` + `motif` | refus attendu : `deja_lie`, `limite_frequence`, `achat_inconnu`, `produit_inconnu`, `partage_famille`, `jeton_invalide`, `signature_invalide` ; **à regarder** : `acces_refuse` (droits Google ou Apple), `non_configure` (Apple pas encore configuré), `indisponible` (store en panne) | `acces_refuse` : §12.7 ; `non_configure` : §12.11 |
| `billing.google.notif` | message Google traité | rien |
| `billing.google.ignore` + `motif` | message ignoré : `test_notification`, `autre_package`, `deja_vu`, `entree_invalide`, `type_ignore` | `autre_package` répété : vérifier la configuration de la Play Console |
| `billing.apple.notif` / `billing.apple.ignore` | notification Apple traitée / ignorée (`deja_vu`, `type_ignore`) | rien |
| `billing.apple.signature-invalide` | notification refusée (signature) | **à regarder si répété** : quelqu'un envoie n'importe quoi à l'adresse publique (plafond d'instances et alertes de budget en place) ; **s'il apparaît dès la première vraie notification d'Apple en production**, vérifiez `appAppleId` (un nombre, pas un texte) et `keyId` dans `billingConfig/apple` |
| `billing.suppression.ok` | données d'abonnement d'un compte supprimées | rien |
| `billing.*.erreur` | erreur inattendue (Firestore...) | copier à Claude |

### 12.15 L'arrêter rapidement

| Besoin | Action | Délai |
|---|---|---|
| **Plus aucun droit accordé par le serveur** | supprimer `recevoirNotificationsGooglePlay` et `recevoirNotificationsApple` puis les 3 autres : `npx firebase functions:delete <nom> --region europe-west9 --project miff-3d283` (relire le nom avant de confirmer). **Les droits déjà écrits restent** (Firestore) ; le Miff+ accordé à la main fonctionne toujours | 1 à 2 minutes par fonction |
| **Couper l'accès de Google Play seul** | Play Console > Utilisateurs et autorisations > retirer `billing-sync` | immédiat |
| **Couper Apple seul** | Secret Manager > `apple-iap-key` > désactiver la version, ou supprimer `billingConfig/apple` | immédiat (5 min de cache) |
| **Limiter le coût** | plafond de dépense Cloud Functions (§1.2), `maxInstances` déjà à 3 | n/a |
| **Retour arrière du code** | §7 (redéployer le commit précédent) ; les documents `billing*` restants ne contiennent aucune donnée personnelle hors UID et état | quelques minutes |

### 12.16 Ce qui N'EST PAS fait (et pourquoi)

- **Jeton App Check à usage unique** (`consumeAppCheckToken`) : **non activé** en v1. Il est en bêta, exige que les apps demandent des jetons « à usage limité » (iOS : `requireLimitedUseAppCheckTokens`, Android : `limitedUseAppCheckTokens`) et que `billing-sync` ait le rôle « Firebase App Check Token Verifier » ; il ajoute un aller-retour réseau. Un jeton App Check volé ne suffit pas (il faut aussi la connexion du compte) et `synchroniserAchat` est limitée à 1 appel par minute et par compte. **Signal pour l'activer** : appels abusifs constatés dans les journaux. Le code est prêt (option de `appelable()`), **il faudra alors modifier le code, les tests et les deux apps**.
- **Fonction planifiée de réconciliation** : aucune (le filet est la synchronisation à l'ouverture de l'app et les relances des stores). À ajouter seulement si les journaux montrent des états périmés (compteur Scheduler partagé avec Ghostrun : §8).
- **Révocation / remboursement par le serveur** : jamais (voir `docs/tickets/premium-achat-reel.md`, écart motivé avec le §6.5).
- **Partage en famille (Apple) / bibliothèque familiale (Google)** : une transaction partagée est refusée (`partage_famille`), décision Q6.
- **Dépendances ajoutées par SRV-7** (versions exactes, `package-lock.json`) : la bibliothèque **officielle** d'Apple `@apple/app-store-server-library` 3.1.0 (dépôt `apple/app-store-server-library-node`, licence MIT ; elle apporte `jsonwebtoken`, `jsrsasign`, `node-fetch`, `base64url`, soit **18 paquets de plus**) et `google-auth-library` 11.3.4 (déjà présente dans l'arbre de `firebase-admin`, simplement épinglée). **Aucun script d'installation de plus** (seuls `@firebase/util` et `protobufjs` en ont, comme avant : §9 bis) ; `npm audit` : 0 vulnérabilité connue le 2026-11-21. À relire à chaque PR Dependabot de ces paquets (ils vérifient des signatures de paiement).
