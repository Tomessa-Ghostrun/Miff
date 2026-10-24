# Déployer les règles de sécurité Firestore (procédure du PM)

**À qui s'adresse ce document** : au PM, qui tape lui-même les commandes ci-dessous sur son poste. Aucun agent ne déploie, et aucune commande de ce document n'est exécutée par la CI (voir « Pourquoi jamais depuis la CI »).

**Contact et autorité de décision** : Tomessa MFOUKA, tomessa.mfouka@gmail.com (PM du projet Miff).

**Ce que « déployer » veut dire** : envoyer le fichier `backend/firestore.rules` du dépôt vers le projet Firebase `miff-3d283`. Dès la publication (quelques minutes pour se propager, d'après la documentation Firebase), les nouvelles règles décident de qui peut lire et écrire quoi pour **tous** les utilisateurs. C'est le seul « vigile » de Miff : on ne déploie donc que des règles dont les tests sont verts et qui ont été relues.

**Coût** : 0 €. Un déploiement de règles ne consomme aucun quota facturé.

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
| 2 | Attendre quelques minutes (propagation) puis ouvrir l'application de test | L'écran de diagnostic indique « Règles actives : OK » (écran prévu par le ticket d'initialisation technique ; tant qu'il n'existe pas, passer à l'étape suivante) |
| 3 | Parcours rapide avec **deux comptes de test** : créer une Miff, la rejoindre avec le code, activer le partage, lire le statut de l'autre | Tout fonctionne ; aucun message de refus inattendu |
| 4 | Pour une règle sensible (DUR-1, DUR-2) : rejouer à la main les attaques H1c (recréer l'identifiant d'une Miff supprimée) et H4c (rejoindre avec l'ancien code après un retrait) | Elles sont **refusées**. À faire sur le projet de développement dès qu'il existe (décision du PM : avant les premiers testeurs externes), pas sur la production |
| 5 | Le lendemain : Firestore Database > **Utilisation** | Pas d'explosion du nombre de lectures (voir `surveillance-quotas.md`) |
| 6 | Tu notes le déploiement | Date, commit déployé, version précédente : dans un fichier à toi ou dans la conversation avec Claude |

---

## 4. Retour arrière (si quelque chose ne va pas)

À utiliser **seulement** si l'application est cassée pour de vrais utilisateurs (par exemple, des refus partout) : l'ancienne version peut contenir les failles corrigées depuis.

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
