# Déployer les règles de sécurité Firestore (procédure du PM)

**À qui s'adresse ce document** : au PM, qui tape lui-même les commandes ci-dessous sur son poste. Aucun agent ne déploie, et aucune commande de ce document n'est exécutée par la CI (voir « Pourquoi jamais depuis la CI »).

**Contact et autorité de décision** : Tomessa MFOUKA, tomessa.mfouka@gmail.com (PM du projet Miff).

**Ce que « déployer » veut dire** : envoyer le fichier `backend/firestore.rules` du dépôt vers le projet Firebase `miff-3d283`. Dès la publication (quelques minutes pour se propager, d'après la documentation Firebase), les nouvelles règles décident de qui peut lire et écrire quoi pour **tous** les utilisateurs. C'est le seul « vigile » de Miff : on ne déploie donc que des règles dont les tests sont verts et qui ont été relues.

**Coût** : 0 €. Un déploiement de règles ne consomme aucun quota facturé.

**État au 2026-11-19** : les règles de l'alerte « Je suis en danger » et du quota d'alertes (3 débuts par 24 h) ont été déployées par le PM le 2026-11-18 au commit `13ec6177` (sortie « released rules » collée ; règles TTL `alerts.purgeAt`, `quota.expireAt` et `notifState.expireAt` constatées actives). La fonction `notifierAlerte` n'est **pas** déployée (guide : `docs/dev/deploiement-alerte-danger.md`).

**À venir (2026-11-20, FEED-ORDRE-PHOTO, PR `docs/feed-ordre-photo`)** : une clé facultative `photoAt` est ajoutée au statut dans `backend/firestore.rules` (ordre du Feed par date de la dernière photo). Règles **rétro-compatibles** : à déployer **AVANT** les applications Android et iOS qui l'écrivent (sinon chaque publication de photo est refusée en entier) ; **pas de retour arrière** des règles une fois ces applications en circulation. Détail : `backend/data-model.md` (« Ce qui change : date de la dernière photo dans le statut »), `docs/tickets/feed-ordre-photo.md` §7.

---

## 0. Les trois interdits

1. **Jamais depuis la CI GitHub.** Déployer depuis GitHub obligerait à y stocker une clé de compte de service Google (un secret très puissant) pour un geste qui se fait en 30 secondes à la main. Les tests du dépôt vérifient qu'aucun workflow ne contient de déploiement ni de jeton (`backend/tests/unit/project-guard.test.js`).
2. **Jamais sans `--project miff-3d283` écrit dans la commande.** Le compte Google du PM contient aussi le projet de Ghostrun (`ghostrun-app`) : une commande sans projet explicite pourrait viser le mauvais. Le dépôt n'a volontairement plus de projet par défaut (`backend/.firebaserc` est vide) : une commande oubliant `--project` s'arrête avec « No currently active project » au lieu de partir vers un projet au hasard.
3. **Jamais `ghostrun-app`, jamais `firebase use`, jamais `--force`.** N'enregistre aucun alias de projet.

---

## 1. Avant (checklist, dans l'ordre)

| # | Vérification | Comment | OK si |
|---|---|---|---|
| 1 | Le code est sur `main`, à jour, et la PR concernée est fusionnée | `git switch main` puis `git pull` dans le dossier `C:\dev\miff` | `git status` ne montre aucune modification locale |
| 2 | La CI de ce commit est verte | GitHub > Actions > dernier run de `firestore-rules` sur `main` | Coche verte |
| 3 | Les tests passent sur ton poste (facultatif si la CI est verte, recommandé avant un premier déploiement) | `cd backend` puis `npm ci` puis `npm test` | « fail 0 » à la fin des deux séries (rapides, puis émulateur) |
| 4 | La revue de sécurité en contexte neuf est faite et tu as dit « oui » au déploiement | Message dans la conversation avec Claude | Un « oui » explicite de ta part, pour **ce** déploiement |
| 5 | Tu notes la version actuellement en ligne | Console Firebase > projet **Miff** (`miff-3d283`) > Firestore Database > onglet **Règles** : relève la date et l'heure de publication affichées, et fais une capture d'écran des règles | Capture enregistrée sur ton poste (hors du dépôt : elle sert de plan B) |
| 6 | Tu sais quel commit tu déploies | `git log -1 --format=%H` dans `C:\dev\miff` | Tu notes l'identifiant (les 8 premiers caractères suffisent) |
| 7 | Tu es connecté avec le bon compte Google | `cd backend` puis `npx firebase login:list` | Ton compte apparaît (sinon `npx firebase login`) |
| 8 | **SESS-1 (un compte, un seul appareil) seulement** : tu sais lequel des deux temps tu déploies (`backend/data-model.md`, « Un compte, un seul appareil ») | **Temps 1** (`deviceIdRequired()` à `false`) : à faire **avant** de livrer les applications SESS. **Temps 2** (`true`) : **seulement** si le PM a confirmé la liste des versions installées (toutes SESS), dans **une PR à part** ; sans mise à jour forcée, une application actuelle ne peut plus écrire statut, position, rejoindre / créer avec partage ni activer le partage (`permission-denied`, qui ressemble à une panne) | Le temps est écrit dans ta demande de « oui » |
| 9 | **SESS-1** : les applications SESS-2 / SESS-3 ont un test qui vérifie que `deviceId` est dans **chaque** écriture de statut et de position, mises à jour partielles comprises (le client Android LOC-2b fait des `update()` partiels) | Les règles ne l'imposent pas : une mise à jour partielle sans la clé passe si la valeur en base est celle de la session | Tests des applications cités dans la PR |

> `npx firebase` utilise la version de la CLI figée dans `backend/package-lock.json` (aujourd'hui 15.34.0) : tout le monde déploie avec le même outil.

---

## 2. La commande de déploiement

Dans un terminal (PowerShell ou Git Bash), **depuis le dossier `backend`** (c'est là que se trouve `firebase.json`, qui désigne `firestore.rules`) :

```bash
cd C:\dev\miff\backend
npx firebase deploy --only firestore:rules --project miff-3d283
```

- `--only firestore:rules` : n'envoie **que** les règles (ni les index, ni autre chose).
- `--project miff-3d283` : cible explicite, jamais implicite.
- Message facultatif pour retrouver le déploiement plus tard : ajouter `--message "DUR-3 commit abcd1234"`.

**Essai à blanc facultatif** (la CLI vérifie la syntaxe des règles auprès du projet, sans rien publier) :

```bash
npx firebase deploy --only firestore:rules --project miff-3d283 --dry-run
```

La CLI affiche « Deploy complete! » en cas de succès. En cas d'erreur de syntaxe, **rien n'est publié** : l'ancienne version reste en ligne.

> La CLI **écrase** les règles de la console par celles du fichier. Ne modifie jamais les règles « à la main » dans la console : elles seraient perdues au déploiement suivant, et le dépôt n'en saurait rien.

---

## 3. Après (vérifications)

| # | Vérification | OK si |
|---|---|---|
| 1 | Console Firebase > Firestore Database > **Règles** | La date et l'heure de publication ont changé (nouvelle version) ; le texte correspond à `backend/firestore.rules` (compare le début du fichier : l'en-tête et les premières fonctions) |
| 2 | Attendre quelques minutes (propagation) puis ouvrir l'application de test, puis le diagnostic (Réglages > Diagnostic, ou appui continu de 10 s, voir `acces-diagnostic.md`) ; le test 1 est refusé si un compte est connecté | L'écran de diagnostic indique « Règles actives : OK » (écran prévu par le ticket d'initialisation technique ; tant qu'il n'existe pas, passer à l'étape suivante) |
| 3 | Parcours rapide avec **deux comptes de test** : créer une Miff, la rejoindre avec le code, activer le partage, lire le statut de l'autre | Tout fonctionne ; aucun message de refus inattendu |
| 4 | Pour une règle sensible (DUR-1, DUR-2) : rejouer à la main les attaques H1c (recréer l'identifiant d'une Miff supprimée) et H4c (rejoindre avec l'ancien code après un retrait) | Elles sont **refusées**. À faire sur le projet de développement dès qu'il existe (décision du PM : avant les premiers testeurs externes), pas sur la production |
| 5 | Le lendemain : Firestore Database > **Utilisation** | Pas d'explosion du nombre de lectures (voir `surveillance-quotas.md`) |
| 6 | Tu notes le déploiement | Date, commit déployé, version précédente : dans un fichier à toi ou dans la conversation avec Claude |

---

## 4. Retour arrière (si quelque chose ne va pas)

À utiliser **seulement** si l'application est cassée pour de vrais utilisateurs (par exemple, des refus partout) : l'ancienne version peut contenir les failles corrigées depuis.

**Attention, `photoAt` (FEED-ORDRE-PHOTO)** : **pas de retour arrière des règles une fois que les applications qui écrivent `photoAt` sont en circulation** : revenir à un fichier sans `photoAt` ferait refuser **chaque publication de photo en entier** (clé inconnue de l'ancien schéma fermé). Dans ce cas, corriger en avant, ne pas revenir en arrière (voir « À venir (2026-11-20, FEED-ORDRE-PHOTO) » en tête de ce document et `backend/data-model.md`).

**Méthode A — historique de la console (la plus rapide).** Console Firebase > Firestore Database > **Règles** : Firebase conserve les versions déjà publiées (d'après l'annonce Firebase de 2018 : une colonne d'historique à gauche, une option pour comparer à la dernière version et republier une version plus ancienne). **L'interface a pu changer depuis : à vérifier dans la console.** Si tu retrouves la version précédente (repère : la date notée à l'étape « Avant » n° 5), republie-la avec le bouton de publication.

**Méthode B — redéployer l'ancien fichier depuis le dépôt (toujours possible, ne dépend pas de l'interface).**

```bash
cd C:\dev\miff
git log --oneline -- backend/firestore.rules        # repère le commit précédent (celui des règles à restaurer)
git switch --detach <identifiant-du-commit>         # l'ancien état du dépôt, sans modifier main
cd backend
npx firebase deploy --only firestore:rules --project miff-3d283 --message "Retour arriere vers <identifiant>"
cd ..
git switch main                                     # retour à la normale
```

**Méthode C — plan B visuel.** La capture d'écran de l'étape « Avant » n° 5 permet de recopier l'ancien texte dans l'éditeur de la console, puis de publier. À éviter sauf urgence (risque d'erreur de copie).

**SESS-1 (I-3)** : une fois qu'une application SESS a écrit une session, **seul le retour au fichier du TEMPS 1** est sûr : un retour aux règles d'avant SESS-1 refuse toute écriture de statut ou de position portant `deviceId`. La capture de l'étape « Avant » n° 5 ne sert de plan B qu'avant la livraison des applications SESS.

Après un retour arrière : refaire les vérifications de la section 3, et prévenir Claude (agent `backend-data`) pour qu'il corrige la cause avant un nouveau déploiement.

---

## 5. Ordre de déploiement quand plusieurs choses changent (ticket de durcissement, §8.2)

1. Tests verts (CI, y compris les mesures de lectures et le test par mutation).
2. Revue de sécurité en contexte neuf.
3. « Oui » explicite du PM.
4. Déploiement local **sur le projet de développement** d'abord (quand il existe), puis sur `miff-3d283`.
5. Vérifications (section 3).
6. **App Check « appliqué » séparément et après** : jamais le même jour que le déploiement des règles, pour pouvoir isoler la cause d'un problème (voir `app-check-strict.md`).

---

## 6. Pourquoi jamais depuis la CI (détail)

- Il faudrait créer une **clé de compte de service** avec le droit de modifier les règles, la stocker comme secret GitHub, et la renouveler. `docs/dev/secrets.md` interdit d'en créer pour ce projet.
- Un déploiement automatique sur une fusion enlèverait le « oui » explicite du PM, pourtant exigé par la procédure.
- Le déploiement manuel prend moins d'une minute et reste une décision humaine.

Si ce choix change un jour, il se prend par une décision écrite du PM, avec une clé à portée minimale et le test `project-guard.test.js` adapté en conséquence.

---

## 7. Déploiement des règles de la description (DESC-R)

**Quoi** : la clé facultative `description` (texte de 1 à 140 unités) du document `miffs/{id}/photos/{uid}` (fonction `validPhoto`). Contexte et tests : `backend/data-model.md` (section « description facultative de la photo d'update »), `backend/rules-test-plan.md` section 27, contrat des clients `docs/dev/description-update-clients.md`. **Coût : 0 €.** Aucune Cloud Function à déployer : `notifierUpdate` n'est pas modifiée.

**Quand** : seulement après la fusion de la PR `backend/description-update` **et** un « oui » explicite du PM pour **ce** déploiement. **Avant** la livraison de tout build Android ou iOS qui écrit une description (sinon la production refuse la photo entière, clé inconnue). Rien n'est déployé par un agent.

### Avant (en plus de la checklist de la section 1)

| # | Vérification | OK si |
|---|---|---|
| 1 | Le dossier `C:\dev\miff-deploy` est sur le commit de `main` qui contient la PR DESC-R (`git -C C:\dev\miff-deploy log -1 --format=%H` ; `git -C C:\dev\miff-deploy status` propre) | Le commit est celui de la fusion, aucune modification locale |
| 2 | **Contenu du fichier : ce déploiement publie TOUT `backend/firestore.rules` de ce commit**, pas seulement la description. Compare avec la version en ligne (`git log --oneline -- backend/firestore.rules` depuis le dernier déploiement noté) et **liste ce qui part en même temps** : l'en-tête du fichier indique ce qui reste NON DÉPLOYÉ (au 2026-11-17 : la demande d'ajout d'un lieu LIEU-REQ-A et les jetons d'appareil SRV-2R, si ce n'est pas déjà fait) | Tu sais exactement ce qui sera publié ; tu as dit « oui » en le sachant |
| 3 | La CI de ce commit est verte (dont `gitleaks` et le test de budgets) | Coche verte |

### La commande

Depuis un terminal (PowerShell ou Git Bash) :

```bash
cd C:\dev\miff-deploy\backend
npx firebase deploy --only firestore:rules --project miff-3d283 --message "DESC-R description facultative, commit <8 premiers caracteres>"
```

Essai à blanc facultatif (vérifie la syntaxe auprès du projet, ne publie rien) : même commande avec `--dry-run`. Jamais d'autre projet, jamais `--force`, jamais `firebase use`.

### Après (vérifications)

| # | Vérification | OK si |
|---|---|---|
| 1 | Console Firebase > Firestore Database > **Règles** : date de publication, puis fonction `validPhoto` | La date a changé ; `validPhoto` contient `'description'` dans la liste de clés autorisées et la ligne `isStr(d.description, 1, 140)` |
| 2 | Attendre quelques minutes (propagation), puis **avec une application déjà installée (sans description)** : publier une update (photo seule) | Elle part comme avant ; ses proches la voient (aucune régression des anciens clients) |
| 3 | Ouvrir une photo ancienne (écrite avant) | Elle s'ouvre comme avant, aucune erreur |
| 4 | **Quand DESC-A (Android) puis DESC-I (iOS) existent** : publier une update **avec** une description de 1 puis de 140 caractères depuis un compte de test, la lire depuis un second compte (qui a publié sa position) | La description s'affiche au-dessus de « Photo de … » ; un compte sans position publiée ne voit ni photo ni description |
| 5 | Le lendemain : Firestore Database > **Utilisation** | Pas d'écart de lectures (la description n'ajoute aucune lecture) |
| 6 | Tu notes le déploiement (date, commit, version précédente) | Noté |

### Retour arrière

Mêmes méthodes qu'à la section 4. **Sans danger pour les données** : redéployer le fichier précédent retire seulement l'autorisation d'écrire `description` ; les photos déjà écrites avec une description restent lisibles (la lecture ne vérifie pas la forme) et expirent en 24 h. Effet visible : une application qui publie une description verrait sa **photo entière** refusée tant que les règles sont revenues en arrière (message calme, la photo et la description restent à l'écran).
