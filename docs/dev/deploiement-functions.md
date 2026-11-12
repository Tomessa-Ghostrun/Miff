# Déployer les Cloud Functions (procédure du PM)

**À qui s'adresse ce document** : au PM, qui tape lui-même les commandes ci-dessous sur son poste. Aucun agent ne déploie, et aucune commande de ce document n'est exécutée par la CI GitHub (voir « Pourquoi jamais depuis la CI »).

**Contact et autorité de décision** : Tomessa MFOUKA, tomessa.mfouka@gmail.com (PM du projet Miff).

**Ce que sont les Cloud Functions** : de petits programmes qui tournent **chez Google** (pas sur les téléphones), par exemple pour envoyer une notification à un proche quand quelqu'un change de statut. Elles n'existent que depuis que le plan **Blaze** est activé (décision du PM du 2026-11-11). Elles ne remplacent pas les règles de sécurité Firestore : celles-ci continuent de décider qui lit quoi (procédure : `deploiement-regles.md`).

**Ce que « déployer » veut dire ici** : envoyer le dossier `backend/functions/` vers le projet Firebase `miff-3d283`. Une fonction envoyée est **en ligne tout de suite** et peut être appelée par n'importe quelle copie de l'application (avec la vérification App Check et la connexion exigées, voir plus bas). On ne déploie donc qu'après un « oui » explicite du PM, CI verte.

**État au moment de ce document (SRV-1, socle)** : le dossier ne contient **qu'une seule fonction de démonstration, `ping`**. Elle ne lit ni n'écrit aucune donnée, ne fait aucune notification, et répond seulement « ok » à une application connectée. Aucune application ne l'appelle encore. Elle sert à vérifier que toute la chaîne (code, déploiement, région, sécurité) fonctionne, avant d'y mettre de vraies fonctions (SRV-4 et suivants). **Premier déploiement attendu : sans risque pour les utilisateurs, coût attendu : 0 €.**

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
| 2 | Les limites sont bien appliquées | Cliquer sur `ping` > détails (ou Google Cloud > Cloud Run > service `ping`) | Instances maximales **3**, mémoire **128 Mio**, délai **30 s**, instances minimales **0** |
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
| **3 instances au plus** par fonction (`maxInstances`) | Plafonne la dépense en cas d'emballement ou d'abus : c'est le frein principal | Avec 128 Mio chaque instance traite **une** demande à la fois : au plus ~3 demandes simultanées, les suivantes attendent jusqu'à 30 s et **peuvent être refusées (code 429)** | Réponses `429` dans les journaux, notifications en retard : relever à 5 pour **cette** fonction (plafond du test : 5) |
| **Mémoire 128 Mio** | La moins chère possible | Une fonction qui charge le SDK Admin (Firestore, FCM) peut manquer de mémoire | « Memory limit exceeded » dans les journaux : 256 Mio pour **cette** fonction (plafond du test) |
| **Délai 30 s** | Une fonction bloquée ne facture pas plus de 30 s | Un traitement long est coupé | Journaux « timeout » : relever (plafond du test : 60 s), ou découper le travail |
| **Pas de nouvelle tentative automatique** (`retry` désactivé) | Évite la boucle « échec → réessai → échec » qui fait exploser une facture | Un échec passager (service FCM indisponible quelques secondes) = **notification perdue** | Des notifications manquantes avec un `ERROR` transitoire dans les journaux : activer `retry` pour **cette** fonction seulement, la rendre idempotente (sans double envoi) et l'ajouter à la liste `FONCTIONS_AVEC_RETRY_AUTORISEES` avec la raison |
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
- Rôles probables, **à confirmer dans le ticket SRV-4** (noms de rôles non vérifiés ici) : accès aux données Firestore (rôle « Utilisateur Cloud Datastore » ; **attention**, un compte de service ignore les règles de sécurité Firestore : ce rôle donne donc accès à toute la base, positions comprises, d'où l'importance de ne l'attribuer qu'aux fonctions qui en ont besoin), envoi de notifications (« Firebase Cloud Messaging API Admin »), et écriture des journaux.
- Création et attribution des rôles : **par le PM** dans la console Google Cloud > IAM, pas à pas à fournir dans le ticket SRV-4. Celui qui déploie doit aussi avoir le droit d'« agir en tant que » ce compte.
- Le test `garde-couts.test.mjs` n'accepte `serviceAccount` que sous la forme `serviceAccount: "nom-de-compte-dedie"` (jamais un compte par défaut, jamais une valeur calculée), et refuse tout compte au nom évoquant Éditeur, administrateur ou propriétaire.

### Scripts d'installation des dépendances

Deux dépendances de Firebase ont un script d'installation : `@firebase/util` et `protobufjs`. En local et dans la CI, les commandes utilisent `npm ci --ignore-scripts`, qui les **neutralise**. **Cloud Build (chez Google), lui, ne les neutralise pas** : il exécute l'installation normale au déploiement. Le risque est celui de toute dépendance (code malveillant introduit dans une version publiée) : il est réduit par les versions **épinglées** et le lockfile (pas de mise à jour surprise) et par la relecture des PR Dependabot (une PR par semaine pour `firebase-functions` et `firebase-admin`). À relire à chaque mise à jour : les notes de version et l'apparition éventuelle d'un **nouveau** script d'installation dans `package-lock.json` (`hasInstallScript`).

---

## 10. Pourquoi jamais depuis la CI (détail)

- Il faudrait créer une **clé de compte de service** avec le droit de déployer des fonctions (un droit très large), la stocker comme secret GitHub et la renouveler. `docs/dev/secrets.md` interdit d'en créer.
- Un déploiement automatique sur une fusion supprimerait le « oui » explicite du PM.
- Les fonctions s'exécutent avec une **identité gérée par Google** : aucune clé n'existe, ni côté dépôt, ni côté code. L'identité par défaut est cependant trop large (voir §9 bis) : un compte dédié aux rôles minimaux est à créer avant SRV-4.

Si ce choix change un jour, il se prend par une décision écrite du PM, avec une clé à portée minimale et les tests `project-guard.test.js` / `functions-config.test.js` adaptés en conséquence.
