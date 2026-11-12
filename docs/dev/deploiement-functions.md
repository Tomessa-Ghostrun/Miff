# Déployer les Cloud Functions (procédure du PM)

**À qui s'adresse ce document** : au PM, qui tape lui-même les commandes ci-dessous sur son poste. Aucun agent ne déploie, et aucune commande de ce document n'est exécutée par la CI GitHub (voir « Pourquoi jamais depuis la CI »).

**Contact et autorité de décision** : Tomessa MFOUKA, tomessa.mfouka@gmail.com (PM du projet Miff).

**Ce que sont les Cloud Functions** : de petits programmes qui tournent **chez Google** (pas sur les téléphones), par exemple pour envoyer une notification à un proche quand quelqu'un change de statut. Elles n'existent que depuis que le plan **Blaze** est activé (décision du PM du 2026-11-11). Elles ne remplacent pas les règles de sécurité Firestore : celles-ci continuent de décider qui lit quoi (procédure : `deploiement-regles.md`).

**Ce que « déployer » veut dire ici** : envoyer le dossier `backend/functions/` vers le projet Firebase `miff-3d283`. Une fonction envoyée est **en ligne tout de suite** et peut être appelée par n'importe quelle copie de l'application (avec la vérification App Check et la connexion exigées, voir plus bas). On ne déploie donc qu'après un « oui » explicite du PM, CI verte.

**État au moment de ce document** : le dossier contient **deux fonctions**.
- **`ping`** (SRV-1, socle) : fonction de démonstration. Elle ne lit ni n'écrit aucune donnée, ne fait aucune notification, et répond seulement « ok » à une application connectée. Aucune application ne l'appelle encore. Elle sert à vérifier que toute la chaîne (code, déploiement, région, sécurité) fonctionne. **Sans risque pour les utilisateurs, coût attendu : 0 €.**
- **`notifierUpdate`** (SRV-4B) : la **première vraie fonction**. Quand un membre publie une update, elle envoie une notification discrète aux autres membres de sa Miff (« {Prénom} a donné des nouvelles »). **Elle lit des données (fiches de membres, jetons d'appareil) et envoie des notifications réelles** : sa mise en ligne est l'**interrupteur de mise en service** de la fonctionnalité (section 11). Elle exige un **compte de service dédié** (section 1.4) à créer **avant** son déploiement.

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

### 1.4 Créer le compte de service dédié de `notifierUpdate` (obligatoire AVANT son déploiement, 10 minutes)

**Pourquoi** : une fonction s'exécute avec une « identité » Google (un compte de service). Par défaut, c'est un compte du projet qui a souvent le rôle **Éditeur** (il peut presque tout faire dans le projet). Une fonction qui lit les fiches des membres et envoie des notifications ne doit pas avoir ce pouvoir : si elle était détournée, les dégâts seraient limités à ce que son compte a le droit de faire. On lui crée donc **son propre compte**, avec **seulement** les droits utiles. **Aucune clé n'est créée ni téléchargée** : l'identité est gérée par Google (`docs/dev/secrets.md`). Si ce compte n'existe pas au moment du déploiement, la commande **échoue** : c'est le bon comportement, rien n'est mis en ligne.

> Libellés recopiés de la documentation Google (en anglais, parfois traduits) : ils peuvent différer légèrement à l'écran. En cas de doute, **arrête-toi et copie ce que tu vois à Claude**. Vérifie à chaque page, en haut, que le projet affiché est bien **Miff / `miff-3d283`** (jamais Ghostrun).

**Étape A : créer le compte**
1. Ouvre https://console.cloud.google.com/iam-admin/serviceaccounts et choisis le projet **`miff-3d283`** (sélecteur de projet en haut de la page).
2. Clique **Créer un compte de service** (« Create service account »).
3. **Nom du compte de service** : `notify-update`. L'identifiant qui s'affiche dessous doit être exactement `notify-update` (l'adresse complète sera `notify-update@miff-3d283.iam.gserviceaccount.com` : c'est celle qui est écrite dans le code, une autre adresse serait refusée par les tests).
4. Description : `Fonction notifierUpdate : lit Firestore et envoie les notifications`.
5. Clique **Créer et continuer**. **Ignore** l'étape « Accorder l'accès à ce compte » (on s'en occupe à l'étape B) : clique **Continuer**, puis **Terminé** (« Done » ; sans rien ajouter à l'étape « Accorder aux utilisateurs l'accès »).
6. **Ne crée aucune clé** (onglet « Clés » du compte : il doit rester vide). Si on te propose de télécharger un fichier `.json`, **refuse**.

**Étape B : lui donner les 4 rôles, et rien d'autre**
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

**Étape C : donner à TON compte le droit d'utiliser ce compte de service**
Pour déployer une fonction « qui tourne sous » un compte de service, le compte Google qui déploie (le tien) doit avoir le rôle **Utilisateur du compte de service** (« Service Account User », `roles/iam.serviceAccountUser`) **sur ce compte**.
1. Retourne sur https://console.cloud.google.com/iam-admin/serviceaccounts, clique l'adresse `notify-update@…`.
2. Onglet **Autorisations** (« Permissions ») > **Accorder l'accès**.
3. Nouveau compte principal : **ton adresse Google** (`tomessa.mfouka@gmail.com`) ; rôle : **Utilisateur du compte de service**. **Enregistrer.**
4. Si tu es **Propriétaire** du projet, tu as déjà ce droit : l'ajouter ne fait pas de mal et rend la règle explicite.

**Étape D : vérifier (30 secondes)**
Sur la page IAM, la ligne `notify-update@miff-3d283.iam.gserviceaccount.com` doit afficher **exactement ces 4 rôles** et aucun autre. Sur la page du compte, l'onglet « Clés » est vide. Note la date dans la conversation avec Claude.

**Étape E : activer la suppression automatique de `notifState` (durée de vie, « TTL »)**
La fonction note dans la collection `notifState` (accessible à la fonction seulement, jamais à une application) la date de la dernière update traitée par auteur, pour ne jamais notifier deux fois et ne pas dépasser une notification par heure. Ces lignes contiennent des identifiants : elles doivent disparaître seules au bout de 2 jours.
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
| 7 | **Pour `notifierUpdate` seulement** : le compte de service dédié existe avec ses 4 rôles et sans clé (section 1.4, étapes A à D) | Google Cloud > IAM | La ligne `notify-update@miff-3d283…` montre exactement 4 rôles |
| 8 | **Pour `notifierUpdate` seulement** : les règles des jetons d'appareil sont déployées, la suppression automatique `notifState` est activée (sections 1.4 E et F) | `deploiement-regles.md`, console Firestore | Règle TTL visible ; règles publiées |
| 9 | **Pour `notifierUpdate` seulement** : les textes légaux (SRV-3) sont publiés et aucune fiche de store ne dit encore « pas de notification » (ticket §15/§16, P6) | Conversation avec Claude | « Oui » du PM écrit |

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

### Les questions que la commande peut te poser

| Question (en anglais) | Ta réponse |
|---|---|
| Activer des services Google (Cloud Functions, Artifact Registry, Cloud Build…) | Elle les active d'elle-même si besoin ; si elle demande confirmation : **oui**. Activer un service ne coûte rien en soi. |
| `No cleanup policy detected for repositories in europe-west9 … This may result in a small monthly bill as container images accumulate over time.` suivi de `How many days do you want to keep container images before they're deleted?` | **Tape `1` puis Entrée** (1 jour : c'est la valeur proposée par défaut, durée de rétention courte). Cette règle supprime automatiquement les anciennes « boîtes » techniques de tes fonctions que Google conserve sinon indéfiniment (petite facture mensuelle qui monte avec le temps). Si la CLI te pose plutôt une question oui/non à ce sujet, réponds **oui**. Si tu la rates, la commande `npx firebase functions:artifacts:setpolicy --project miff-3d283` permet de la définir après coup. |
| Une demande de suppression de fonctions absentes du code (`The following functions are found in your project but do not exist in your local source code`) | Lis la liste. **Si elle contient autre chose que ce que tu attends, réponds NON** et préviens Claude. Au premier déploiement, cette question n'apparaît pas. |
| Tout message qui parle d'**instances minimales** ou d'un **coût minimum** | **Non**, annule (`Ctrl+C`) et préviens Claude : le dépôt n'en prévoit jamais (une instance minimale est facturée même sans trafic). |

La CLI se termine par `Deploy complete!`. En cas d'erreur, **rien de cassé n'est mis en ligne** : l'ancienne version reste (au premier déploiement, il n'y a simplement rien).

### Erreurs possibles au premier déploiement (non vérifiées, de connaissance générale)

- Message disant qu'un **droit manque pour la construction** (Cloud Build / compte de service) : ne donne **aucun rôle large** (Propriétaire, Éditeur) « pour que ça passe ». Copie le message à Claude : il te dira le rôle précis à accorder.
- Message parlant de **facturation** : vérifie que le projet est bien relié au compte de facturation (Firebase > Utilisation et facturation > plan Blaze).

---

## 4. Après le déploiement (vérifications)

| # | Vérification | Où | OK si |
|---|---|---|---|
| 1 | La fonction est en ligne | Console Firebase > projet **Miff** > **Functions** (ou la commande `npx firebase functions:list --project miff-3d283`) | `ping` apparaît, région **europe-west9**, environnement 2e génération, Node.js 24 |
| 2 | Les limites sont bien appliquées | Cliquer sur la fonction > détails (ou Google Cloud > Cloud Run > service de la fonction) | `ping` : instances maximales **3**, mémoire **128 Mio**, délai **30 s**, instances minimales **0**. `notifierUpdate` : instances maximales **5**, mémoire **256 Mio**, délai **30 s**, instances minimales **0**, compte de service **`notify-update@miff-3d283…`** (et non le compte « Compute Engine default ») |
| 3 | Aucune erreur | Onglet **Journaux** de `ping` (section 5) | Aucun message rouge « Error » |
| 4 | Pas de dépense | Google Cloud > Facturation > Rapports (le lendemain) | 0 € pour Cloud Functions |
| 5 | Tu notes le déploiement | Date, commit déployé (8 caractères), version précédente | Dans un fichier à toi ou dans la conversation avec Claude |

> `ping` ne peut pas être appelée depuis un navigateur : elle exige que l'**application** (vérifiée par App Check) soit connectée. Une réponse « refusée » à un essai maladroit est le **bon** comportement. Le premier vrai appel viendra avec les tickets suivants (SRV-2 et suivants). D'ici là, la vérification est : « la fonction est en ligne, avec les bonnes limites, sans erreur ».

---

## 5. Lire les journaux (logs)

Les journaux disent ce que chaque fonction a fait. **Nos journaux APPLICATIFS (ce que le code de Miff écrit) ne contiennent jamais de position, de nom, de jeton ni d'adresse e-mail** (règle codée et testée : un message de journal est un petit code comme `ping.ok`, avec au plus un résultat et un nombre). **Mais** les journaux TECHNIQUES de la plateforme Google (Cloud Run / Cloud Logging, journaux de requêtes) gardent l'**adresse IP et le user-agent** de l'appelant : c'est une donnée personnelle que Miff ne maîtrise pas, à citer dans la politique de confidentialité (point RGPD, risque assumé par le PM, décision du 2026-10-23).

- **Console Firebase** : projet **Miff** > **Functions** > onglet **Journaux** (filtre par fonction et par gravité).
- **Console Google Cloud** : https://console.cloud.google.com/logs (choisir le projet `miff-3d283`, filtrer « Ressource : Cloud Run Revision » ou la fonction).
- **Ligne de commande** : `npx firebase functions:log --project miff-3d283 --only ping -n 30` (les 30 dernières lignes).

Niveaux : `INFO` = normal ; `WARNING` = à regarder si ça se répète (par exemple une demande refusée) ; `ERROR` = quelque chose a échoué : copie le message à Claude.

**Signaux à repérer** : « Memory limit exceeded » (mémoire trop basse), « timeout » (délai de 30 s dépassé), des réponses `429` (limite d'instances atteinte). Chacun correspond à un choix de la section 9.

---

## 6. Désactiver une fonction (si elle fait n'importe quoi ou coûte trop)

Par ordre de simplicité :

1. **La supprimer** (propre, réversible : on redéploie le même code plus tard) :
   ```bash
   cd C:\dev\miff-deploy\backend
   npx firebase functions:delete ping --region europe-west9 --project miff-3d283
   ```
   La CLI demande confirmation : relis le nom de la fonction avant de répondre oui. **Attention** : tant que la fonction est présente dans le code de `main`, le déploiement suivant la remettra en ligne ; demande à Claude de la retirer du code (PR) avant de redéployer.
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

Comme les anciennes « boîtes » techniques sont supprimées après 1 jour (section 3), le retour arrière **reconstruit** le code : compte quelques minutes.

**Méthode B — supprimer la fonction fautive** (section 6, option 1) quand le problème est urgent et que la fonction n'est pas indispensable.

Après un retour arrière : prévenir Claude (agent `backend-data`) pour corriger la cause avant un nouveau déploiement.

---

## 8. Coûts attendus

**Ordre de grandeur (estimations de l'étude `etude-serveur-notifications-premium.md`, §6 ; ce ne sont pas des mesures)** :

| Utilisateurs actifs par jour | 100 | 1 000 | 10 000 |
|---|---|---|---|
| Coût mensuel total (Firestore + fonctions) | ≈ 0 $ | ≈ 0,5 $ | ≈ 12 $ |
| Dont les fonctions elles-mêmes | 0 $ | 0 $ | 0 $ (≈ 75 % du quota gratuit) |

- **Tant qu'on est sur `ping` seule : 0 €.** Les 2 millions d'invocations par mois sont gratuites ; `ping` n'est appelée par personne.
- Le coût dominant n'est **pas** les fonctions, mais les lectures Firestore de l'application elle-même (au-delà de 50 000 par jour). Voir `surveillance-quotas.md`.
- Lignes annexes, non chiffrées ici et normalement de l'ordre de quelques centimes : stockage des « boîtes » techniques (limité par la règle de 1 jour), journaux, construction (120 minutes par jour gratuites). **Non vérifié** : à lire dans Google Cloud > Facturation > Rapports après quelques semaines.
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
| **Pas de nouvelle tentative automatique** (`retry` désactivé) | Évite la boucle « échec → réessai → échec » qui fait exploser une facture | Un échec passager (service FCM indisponible quelques secondes) = **notification perdue** (`notifierUpdate` retente elle-même l'envoi 2 fois au plus, dans la même exécution, mais ne relance jamais l'exécution entière) | Des notifications manquantes avec un `ERROR` transitoire dans les journaux : activer `retry` pour **cette** fonction seulement, la rendre idempotente (sans double envoi) et l'ajouter à la liste `FONCTIONS_AVEC_RETRY_AUTORISEES` avec la raison |
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

- **`ping` ne touche ni Firestore ni FCM** : l'identité par défaut est tolérée pour elle seule.
- **Avant SRV-4 (première fonction qui lit Firestore ou envoie une notification)** : créer un compte de service **dédié** (par exemple nommé `miff-functions`) avec **uniquement** les rôles nécessaires, puis le brancher dans le code avec l'option `serviceAccount` (le **nom** du compte, jamais une clé). Jamais le rôle Éditeur ni Propriétaire.
- Rôles (confirmés dans la documentation Google le 2026-11-12 pour `notifierUpdate`, voir section 1.4) : accès aux données Firestore (rôle « Utilisateur Cloud Datastore » ; **attention**, un compte de service ignore les règles de sécurité Firestore : ce rôle donne donc accès à toute la base, positions comprises, d'où l'importance de ne l'attribuer qu'aux fonctions qui en ont besoin), envoi de notifications (« Firebase Cloud Messaging API Admin »), et écriture des journaux.
- Création et attribution des rôles : **par le PM** dans la console Google Cloud > IAM, **pas à pas en section 1.4** (compte `notify-update`, rôles confirmés pour `notifierUpdate`, droit « Utilisateur du compte de service » pour celui qui déploie).
- Le test `garde-couts.test.mjs` n'accepte `serviceAccount` que sous la forme `serviceAccount: "nom-de-compte-dedie"` (jamais un compte par défaut, jamais une valeur calculée), et refuse tout compte au nom évoquant Éditeur, administrateur ou propriétaire.

### Scripts d'installation des dépendances

Deux dépendances de Firebase ont un script d'installation : `@firebase/util` et `protobufjs`. En local et dans la CI, les commandes utilisent `npm ci --ignore-scripts`, qui les **neutralise**. **Cloud Build (chez Google), lui, ne les neutralise pas** : il exécute l'installation normale au déploiement. Le risque est celui de toute dépendance (code malveillant introduit dans une version publiée) : il est réduit par les versions **épinglées** et le lockfile (pas de mise à jour surprise) et par la relecture des PR Dependabot (une PR par semaine pour `firebase-functions` et `firebase-admin`). À relire à chaque mise à jour : les notes de version et l'apparition éventuelle d'un **nouveau** script d'installation dans `package-lock.json` (`hasInstallScript`).

---

## 10. Pourquoi jamais depuis la CI (détail)

- Il faudrait créer une **clé de compte de service** avec le droit de déployer des fonctions (un droit très large), la stocker comme secret GitHub et la renouveler. `docs/dev/secrets.md` interdit d'en créer.
- Un déploiement automatique sur une fusion supprimerait le « oui » explicite du PM.
- Les fonctions s'exécutent avec une **identité gérée par Google** : aucune clé n'existe, ni côté dépôt, ni côté code. L'identité par défaut est cependant trop large (voir §9 bis) : `ping` (qui ne touche à aucune donnée) la garde, mais **toute fonction métier, dont `notifierUpdate`, utilise un compte de service dédié aux droits minimaux** (section 1.4) ; un test refuse toute fonction métier sans compte dédié explicite.

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
| Miff de **4 membres**, 3 destinataires avec 1 téléphone chacun | **12** (1 contrôle préalable + 4 fiches + 3 listes de téléphones + 3 sessions + 1 vérification finale) | **1** (l'état) |
| Miff de **15 membres** | **45** | 1 |
| Miff de **40 membres** | **72** au plus (73 si l'auteur est hors des 30 premières fiches) : 1 + 30 fiches + 20 téléphones + 20 sessions + 1 | 1 |
| Update plafonnée (2e de l'heure) ou rejouée | **1** (le contrôle préalable arrête tout avant de lire fiches, téléphones et sessions) | **0** |
| Auteur seul dans sa Miff | **2** | 0 |
| Événement de plus de 5 min, suppression de photo | **0** | 0 |
| Jeton invalide purgé | n/a | +1 par jeton |

Ces nombres sont **additionnés par la fonction elle-même** (chaque lecture est comptée dans son bilan) et vérifiés par les tests à l'émulateur ; ce ne sont pas des chiffres de facturation Google. Le bilan de chaque exécution figure dans les journaux (`notif.update.bilan`, champs `lectures` et `ecritures`) : tu peux le comparer au rapport de facturation. Les envois FCM sont gratuits. Estimation du ticket : ≈ 0 $ à 100 utilisateurs actifs par jour, ≈ 0,2 $/mois à 1 000, ≈ 2,4 $/mois à 10 000 (hypothèses, pas des mesures).

### 11.3 La mettre en ligne (c'est l'interrupteur de mise en service)

**Prérequis** : section 1.4 (compte de service, TTL, règles des jetons), checklist de la section 2, textes légaux publiés, applications avec jetons distribuées (SRV-2A / SRV-2I). **Le « oui » du PM est écrit dans la conversation avec Claude.** Tant que la fonction n'est pas déployée, les apps enregistrent des jetons mais **rien n'est envoyé**.

```bash
cd C:\dev\miff-deploy\backend
npx firebase deploy --only functions:notifierUpdate --project miff-3d283
```

(`--only functions:notifierUpdate` ne touche pas à `ping`. Les questions possibles sont celles de la section 3.) **Un échec du type « le compte de service n'existe pas » ou « permission `iam.serviceAccounts.actAs` refusée »** veut dire que la section 1.4 (étape A ou C) n'est pas terminée. **Ne contourne pas** en demandant de retirer l'option de compte dans le code : préviens Claude.

### 11.4 La tester après déploiement avec un compte de test

Matériel : **deux comptes de test** dans **une même Miff de test**, les deux avec le partage activé, chacun sur son téléphone (un appareil par compte), notifications autorisées sur celui qui reçoit, réglage « Quand un proche donne des nouvelles » activé. **Pas de vrais proches dans cette Miff.**
1. Le compte 1 publie une update. Attendu : le compte 2 reçoit « {Prénom} a donné des nouvelles » en quelques secondes (2 minutes au plus), **sans photo ni lieu** ; le compte 1 ne reçoit rien.
2. Journaux (section 5) : `npx firebase functions:log --only notifierUpdate -n 30 --project miff-3d283`. Attendu : une ligne `notif.update.bilan` avec `envoyes: 1`, `lectures: 6` (2 comptes : 1 contrôle préalable + 2 fiches + 1 téléphone + 1 session + 1 vérification finale), `ecritures: 1`, **sans aucun nom, identifiant ni jeton**. Une ligne `notif.update.fcm-erreur` avec `code` 8 = clé APNs absente ou refusée côté Apple (ticket §12, étape du PM) ; `code` 1 = jeton inconnu (purgé) ; `code` 3 ou 4 = panne passagère de Google.
3. Le compte 1 publie une 2e update **dans l'heure** : pas de nouvelle notification (`notif.update.ignore`, `motif: plafond`), la photo est visible dans le Feed.
4. Console Firebase > Firestore : la collection `notifState` contient un document par Miff de test, **sans nom ni jeton** (que des dates) ; la règle TTL est visible.
5. Console Firebase > Functions : instances maximales 5, mémoire 256 Mio, compte `notify-update@…`.

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
- Le **rôle FCM** du compte de service (le rôle `roles/firebasecloudmessaging.admin` suffit-il seul ? attendu : oui).
- Le **déclencheur de production** (Eventarc) avec le compte dédié : droit `eventarc.eventReceiver` ; si les événements n'arrivent pas, copier le message à Claude (un droit `run.invoker` sur le service peut manquer : à confirmer, jamais de rôle large).
- La **suppression automatique (TTL)** de `notifState`.
