# Publier les textes légaux en version 1.2 (site Firebase Hosting) : procédure pour le PM

> **ARCHIVE (2026-11-22) : ne plus suivre ce document.** La version 1.3 (22 novembre 2026) le remplace : `docs/dev/publication-textes-legaux-v1-3.md` (mêmes commandes, contrôles de version, de date et de contenu mis à jour, et le point d'ordre sur le build iPhone). Gardé pour l'historique de la 1.2 ; ses chaînes (« 21 novembre 2026 », « Version 1.2 ») ne correspondent plus aux textes.

Préparé le 2026-11-21 par `release-manager`. **C'est toi, et toi seul, qui tapes les commandes de déploiement** : aucun agent ne déploie, ne se connecte à Firebase ni ne soumet quoi que ce soit à un store. Chaque commande Firebase porte `--project miff-3d283` (jamais `ghostrun-app`, même s'il est visible depuis ton compte Google), jamais `--force`, jamais `firebase use`.

Cette procédure **remplace** celle de la 1.1 (`docs/dev/publication-textes-legaux-v1-1.md`, gardée en archive : elle n'est plus à suivre). Les commandes sont les mêmes ; ce qui change : la version et la date contrôlées, les mentions nouvelles à retrouver sur les pages, et la place de cette étape dans l'ordre de mise en service (étape 2 sur 5, voir §0).

Les textes sont écrits par l'équipe d'agents et publiés sous ta responsabilité. **Aucun juriste ne les a relus et aucune page ne le prétend** (décision du 2026-10-23, risque assumé).

## 0. Ce que tu publies, en clair

- Tu publies la **version 1.2, datée du 21 novembre 2026** des conditions d'utilisation et de la politique de confidentialité (français et anglais). Résumé en français simple et points à valider : `docs/legal/maj-2026-11-21-a-publier.md`. Détail technique : `docs/legal/README.md` §15.
- **Ce qu'elle ajoute** : (1) la date de ta dernière photo (sert au seul classement des cartes du Feed) ; (2) le **registre de 30 jours** des notifications envoyées, côté serveur ; (3) le texte de consentement à la localisation en **version 4** ; (4) la précision que les textes légaux restent **en français et en anglais seulement** (l'app existe en 6 langues).
- **Pages modifiées** : `/fr/cgu`, `/en/terms`, `/fr/confidentialite`, `/en/privacy`, `/fr/supprimer-mon-compte`, `/en/delete-account`. La page d'accueil et `/reset-password` ne changent pas.
- **Où tu en es** : le site est en ligne depuis le 13 novembre 2026 en version 1.0. **Le dépôt ne garde aucune trace de la mise en ligne de la 1.1** (19 novembre). Peu importe : la 1.2 remplace ce qui est en ligne (si la 1.1 n'a jamais été publiée, elle est publiée en même temps, sous le numéro 1.2). À l'étape 1 de la checklist, tu notes ce qui est en ligne maintenant : ça sert au retour arrière.
- **Place dans l'ordre de mise en service** (`maj-2026-11-21-a-publier.md` §2) : 1) règle TTL `notifications.expireAt` dans Firestore, état « Actif » ; **2) cette publication du site** ; 3) règles Firestore ; 4) fonctions `notifierUpdate` et `notifierAlerte` ; 5) apps. **Le texte doit être en ligne avant l'étape 4** (c'est elle qui fait écrire le premier registre) **et avant la distribution des apps**. Publier le site ne met rien en service : le texte dit que le registre est « mis en service progressivement » et que, tant que le serveur ne l'écrit pas, rien n'est enregistré.
- **Ce que cette publication ne fait pas** : elle ne change aucune app, ne déploie ni fonction ni règle, ne modifie pas `termsVersion` (« 1.0 » reste enregistré, décision du 2026-11-18, aucune acceptation forcée) et ne soumet rien à l'App Store ni à Google Play.
- **Coût : 0 €** (Firebase Hosting, plan gratuit Spark, aucun moyen de paiement).

## 1. Avant de commencer (rien à taper, sauf les points 3 et 4)

| # | À vérifier | Si ce n'est pas le cas |
|---|---|---|
| 1 | **Note ce qui est en ligne aujourd'hui** : ouvre `https://miff-3d283.web.app/fr/cgu` en navigation privée et recopie la ligne « Version … — dernière mise à jour : … » (1.0 du 13 novembre, ou 1.1 du 19 novembre). Console Firebase (projet **Miff / `miff-3d283`**) > Hosting > historique des versions : note aussi la date de la dernière version publiée | Sans cette note, le retour arrière (§8) est plus difficile à choisir |
| 2 | La pull request des textes 1.2 (**#307**, `docs/legal-maj-photoat-journal`) est **fusionnée dans `main`**, et la **CI de `main` est verte** (GitHub > Actions > dernier run sur `main` : coche verte, notamment `web` et `hygiene`) | Ne rien déployer : le dossier de déploiement lit `main` |
| 3 | **Nous sommes bien le 21 novembre 2026** (`Get-Date -Format "dd/MM/yyyy"` dans PowerShell). Les quatre textes affichent « 21 novembre 2026 ». Si tu publies un autre jour, **dis-le à Claude avant** : il remet le bon jour dans les quatre textes et dans le test `web/tests/legal-1-1.test.mjs` | Ne pas publier avec une date fausse |
| 4 | Tu es **d'accord avec les textes** et tu as répondu aux 4 points de `maj-2026-11-21-a-publier.md` §3 : base légale du registre (« Contrat » par défaut), risque J68 (date de dernière photo) assumé ou non, date et état de la 1.1, iPhone et le retrait des statistiques (J72) | Dis-le à Claude : on corrige avant |
| 5 | La **règle TTL** `notifications.expireAt` est posée et « Active » (`docs/dev/deploiement-centre-notifications.md` §1). Sans elle, « 30 jours » dans le texte serait faux **dès que le serveur écrira le registre** : ce n'est pas bloquant pour publier le site, mais c'est bloquant pour l'étape 4 | Fais-la avant l'étape 4 (jamais avant d'avoir publié le site) |
| 6 | La **clé web** de Firebase (celle qui commence par `AIza`) est dans ton gestionnaire de mots de passe : console Firebase > Paramètres du projet > Général > Vos applications > application Web « Miff Web » > `apiKey` | Elle n'est pas un secret, mais elle ne s'écrit **jamais** dans un fichier, un message ni une capture |
| 7 | Dans Google Analytics, la conservation est restée à **14 mois** avec « Réinitialiser les données utilisateur lors d'une nouvelle activité » **désactivé** (la politique l'affirme) | Le corriger avant de publier |

## 2. Préparer le dossier de déploiement (PowerShell)

Ouvre **PowerShell** (menu Démarrer, tape « PowerShell »). Colle ces lignes une par une. Le dossier `C:\dev\miff-deploy` est ton clone séparé, réservé aux déploiements : il ne contient jamais de travail en cours.

```powershell
cd C:\dev\miff-deploy
git fetch origin
git switch --detach origin/main
git log -1 --format=%H
```

La dernière ligne affiche l'identifiant du commit. **Note les 8 premiers caractères** et **donne-les à Claude** : il confirme que c'est bien un commit qu'il a validé (la fusion de la PR #307, ou un commit plus récent qui la contient). Si `git switch` se plaint de fichiers modifiés, **arrête-toi** et copie le message à Claude (ne force rien).

**Contrôle que tu as bien les textes 1.2** (4 lignes attendues, une par texte) :

```powershell
Select-String -Path docs\legal\cgu.fr.md, docs\legal\cgu.en.md, docs\legal\politique-confidentialite.fr.md, docs\legal\politique-confidentialite.en.md -Pattern "^\*\*Version 1\.2 "
```

Chaque ligne doit finir par « 21 novembre 2026** » (FR) ou « November 21, 2026** » (EN). Si une ligne dit « 1.1 » ou qu'il manque un fichier, **arrête-toi** : `main` ne contient pas encore la PR #307.

Vérifie que l'outil Firebase est là et que ton compte est connecté :

```powershell
cd C:\dev\miff-deploy\backend
npm ci --ignore-scripts
.\node_modules\.bin\firebase.cmd login:list
```

`login:list` doit afficher ton compte Google. Sinon : `.\node_modules\.bin\firebase.cmd login`.

> Pourquoi pas simplement `npx firebase …` ? Depuis le dossier `web`, `npx` ne trouverait pas l'outil installé dans `backend` et proposerait d'en télécharger une version quelconque. On appelle donc l'outil de `backend` directement (même commande que `web/README.md` §7), lancé depuis `C:\dev\miff-deploy\web` où se trouvent `firebase.json` et le garde-fou.

## 3. Contrôles sans rien envoyer

```powershell
cd C:\dev\miff-deploy
node --test "web/tests/*.test.mjs"
node scripts/check-forbidden-files.mjs
```

Attendu : « fail 0 » (84 tests réussis au 2026-11-21 ; le nombre peut grandir, jamais « fail » supérieur à 0) et « Fichiers interdits : aucun ». Ces tests **vérifient déjà** : la version 1.2 et la date du 21 novembre en tête des quatre pages, le registre de 30 jours (sans prénom, photo, lieu ni position), la date de la dernière photo, le consentement version 4, les textes légaux en FR et EN seulement, et l'absence de toute relecture juridique prétendue. Si un test échoue, **ne continue pas** : copie la sortie à Claude.

Prévisualisation locale (facultative, 3 minutes) : `node scripts/preview-web.mjs`, ouvre `http://127.0.0.1:5000/fr/cgu`, vérifie « Version 1.2 — dernière mise à jour : 21 novembre 2026 » en haut, puis `Ctrl+C` pour arrêter.

## 4. Construire le vrai site avec la clé (variable temporaire)

La clé est tapée **dans cette fenêtre seulement** (elle disparaît quand tu la fermes). Remplace `AIza…` par ta vraie clé :

```powershell
cd C:\dev\miff-deploy
$env:MIFF_WEB_API_KEY = "AIza…"
node scripts/build-web.mjs
```

La commande doit afficher : **« 10 pages, démo=false, clé=présente, emplacements à compléter=0 »**. Si elle affiche « démo=true » ou « clé=absente », la clé n'a pas été prise : recommence l'étape.

Dernier contrôle du garde-fou (aucun envoi) :

```powershell
node scripts/web-predeploy-check.mjs
```

Attendu : « Garde-fou de déploiement : OK ». S'il refuse, **ne le contourne pas** : copie le message à Claude.

**Contrôle du contenu construit** (lecture seule, sur les fichiers du site que tu vas envoyer). Chaque ligne doit afficher `OK` :

```powershell
cd C:\dev\miff-deploy\web\public
$presents = @(
  @("fr\cgu.html", "Version 1.2 — dernière mise à jour : 21 novembre 2026"),
  @("fr\cgu.html", "garde une trace technique de 30 jours"),
  @("fr\cgu.html", "la date de ta dernière photo"),
  @("fr\confidentialite.html", "Version 1.2 — dernière mise à jour : 21 novembre 2026"),
  @("fr\confidentialite.html", "Registre des notifications envoyées"),
  @("fr\confidentialite.html", "registre de 30 jours"),
  @("fr\confidentialite.html", "Date de ta dernière photo"),
  @("fr\confidentialite.html", "2026-11-v04"),
  @("fr\confidentialite.html", "en français et en anglais"),
  @("fr\supprimer-mon-compte.html", "registre de 30 jours"),
  @("fr\supprimer-mon-compte.html", "la date de ta dernière photo"),
  @("en\terms.html", "Version 1.2 — last updated: November 21, 2026"),
  @("en\terms.html", "keeps a 30-day technical trace"),
  @("en\terms.html", "the date of your last photo"),
  @("en\privacy.html", "Version 1.2 — last updated: November 21, 2026"),
  @("en\privacy.html", "Record of notifications sent"),
  @("en\privacy.html", "30-day record"),
  @("en\privacy.html", "Date of your last photo"),
  @("en\privacy.html", "2026-11-v04"),
  @("en\privacy.html", "only exist in French and English"),
  @("en\delete-account.html", "30-day record"),
  @("en\delete-account.html", "the date of your last photo")
)
$absents = @(
  @("fr\cgu.html", "Version 1.1 —"),
  @("fr\confidentialite.html", "Version 1.1 —"),
  @("en\terms.html", "Version 1.1 —"),
  @("en\privacy.html", "Version 1.1 —"),
  @("fr\confidentialite.html", "Nous ne gardons aucun historique des notifications envoyées"),
  @("en\privacy.html", "We keep no history of the notifications sent")
)
foreach ($c in $presents) { $t = Get-Content $c[0] -Raw -Encoding UTF8; "{0,-7} {1}  : {2}" -f $(if ($t.Contains($c[1])) {"OK"} else {"MANQUE"}), $c[0], $c[1] }
foreach ($c in $absents)  { $t = Get-Content $c[0] -Raw -Encoding UTF8; "{0,-7} {1}  : {2}" -f $(if ($t.Contains($c[1])) {"A RETIRER"} else {"OK"}), $c[0], $c[1] }
```

Attendu : 28 lignes `OK`, aucune `MANQUE` ni `A RETIRER`. Si **toutes** les lignes avec des accents sortent `MANQUE`, c'est probablement l'affichage des accents de ta fenêtre PowerShell (pas le site) : copie la sortie à Claude. Ces chaînes sont aussi contrôlées en test (`web/tests/legal-1-1.test.mjs`, « PROCEDURE 1.2 »).

## 5. Essai à blanc (aucune publication)

```powershell
cd C:\dev\miff-deploy\web
..\backend\node_modules\.bin\firebase.cmd deploy --only hosting --project miff-3d283 --dry-run
```

Cette commande **vérifie la configuration et rejoue le garde-fou, sans publier**. Attendu : un message du type « Dry run complete ». Si elle affiche une erreur que tu ne comprends pas, copie-la à Claude : ne passe pas à l'étape 6.

*Option « adresse temporaire » (facultatif, gratuit, ne touche pas au site actuel ; **c'est une vraie publication, mais sur une adresse de prévisualisation non listée qui expire seule**)* :

```powershell
..\backend\node_modules\.bin\firebase.cmd hosting:channel:deploy essai-textes-1-2 --expires 1d --project miff-3d283
```

Firebase affiche une adresse du type `https://miff-3d283--essai-textes-1-2-….web.app` : ouvre-la sur ton téléphone, vérifie la version et la date, puis passe à l'étape 6. (La page de mot de passe n'y fonctionnera pas, c'est sans importance.)

## 6. Publier (seulement après ton « oui, déploie » dans la conversation avec Claude)

Dis d'abord à Claude : **« oui, déploie, commit XXXXXXXX »** (avec les 8 caractères de l'étape 2). Puis :

```powershell
cd C:\dev\miff-deploy\web
..\backend\node_modules\.bin\firebase.cmd deploy --only hosting --project miff-3d283 --message "Textes legaux 1.2 du 21 novembre 2026"
```

Attendu : une liste de fichiers envoyés, puis « Deploy complete! » et l'adresse `https://miff-3d283.web.app`. Si le garde-fou refuse (message « DÉPLOIEMENT REFUSÉ… »), **c'est voulu** : ne le contourne pas, relève le message et transmets-le à Claude. Pas de `--force`, jamais.

Ensuite, **efface la clé de la session** et ferme la fenêtre :

```powershell
Remove-Item Env:MIFF_WEB_API_KEY
```

## 7. Contrôles après publication (lecture seule)

Firebase Hosting vide lui-même son cache réseau (CDN) à chaque nouveau déploiement ; ton navigateur, lui, peut garder une page quelques minutes : utilise la **navigation privée** pour regarder à l'écran.

### 7.1 Les URLs et les phrases à retrouver

Les six pages à ouvrir (en navigation privée) :

| Langue | Conditions | Politique de confidentialité | Suppression de compte |
|---|---|---|---|
| FR | `https://miff-3d283.web.app/fr/cgu` | `https://miff-3d283.web.app/fr/confidentialite` | `https://miff-3d283.web.app/fr/supprimer-mon-compte` |
| EN | `https://miff-3d283.web.app/en/terms` | `https://miff-3d283.web.app/en/privacy` | `https://miff-3d283.web.app/en/delete-account` |

Contrôle automatique (rien n'est modifié ; ce sont de simples lectures). Il rejoue les contrôles du §4 sur le site **en ligne** :

```powershell
$base = "https://miff-3d283.web.app"
$presents = @(
  @("/fr/cgu", "Version 1.2 — dernière mise à jour : 21 novembre 2026"),
  @("/fr/cgu", "garde une trace technique de 30 jours"),
  @("/fr/cgu", "la date de ta dernière photo"),
  @("/fr/confidentialite", "Version 1.2 — dernière mise à jour : 21 novembre 2026"),
  @("/fr/confidentialite", "Registre des notifications envoyées"),
  @("/fr/confidentialite", "Date de ta dernière photo"),
  @("/fr/confidentialite", "2026-11-v04"),
  @("/fr/supprimer-mon-compte", "registre de 30 jours"),
  @("/fr/supprimer-mon-compte", "la date de ta dernière photo"),
  @("/en/terms", "Version 1.2 — last updated: November 21, 2026"),
  @("/en/terms", "keeps a 30-day technical trace"),
  @("/en/privacy", "Version 1.2 — last updated: November 21, 2026"),
  @("/en/privacy", "Record of notifications sent"),
  @("/en/privacy", "Date of your last photo"),
  @("/en/privacy", "only exist in French and English"),
  @("/en/delete-account", "30-day record"),
  @("/en/delete-account", "the date of your last photo")
)
foreach ($c in $presents) {
  $r = Invoke-WebRequest "$base$($c[0])" -UseBasicParsing -Headers @{ "Cache-Control" = "no-cache" }
  "{0,-7} {1} ({2}) : {3}" -f $(if ($r.Content.Contains($c[1])) {"OK"} else {"MANQUE"}), $c[0], $r.StatusCode, $c[1]
}
foreach ($u in "/fr/","/en/","/reset-password") {
  $r = Invoke-WebRequest "$base$u" -UseBasicParsing -Headers @{ "Cache-Control" = "no-cache" }
  "$u -> $($r.StatusCode)"
}
foreach ($u in "/fr/cgu","/en/privacy") {
  $r = (Invoke-WebRequest "$base$u" -UseBasicParsing -Headers @{ "Cache-Control" = "no-cache" }).Content
  "{0,-7} {1} ne contient plus « Version 1.1 — »" -f $(if ($r.Contains("Version 1.1 —")) {"A RETIRER"} else {"OK"}), $u
}
```

Attendu : 17 lignes `OK` avec `(200)`, trois lignes `… -> 200` (accueil FR, accueil EN, mot de passe) et deux lignes `OK` pour l'absence de « Version 1.1 — ». Une ligne `MANQUE` = la page n'est pas à jour : attends 5 minutes, recharge, sinon préviens Claude.

### 7.2 Ce qu'on doit lire à l'écran (navigateur, navigation privée)

| Page | Ce qu'on doit lire |
|---|---|
| `/fr/cgu` | « **Version 1.2 — dernière mise à jour : 21 novembre 2026** » sous le titre ; juste après, l'encadré « Ce qui a changé depuis la version 1.1 (19 novembre 2026) » ; dans l'article 6, la **trace technique de 30 jours** des notifications envoyées à un proche et la **date de ta dernière photo** (effacée avec le statut) ; l'article 17 (« Divers ») : textes légaux en français et en anglais seulement |
| `/en/terms` | « **Version 1.2 — last updated: November 21, 2026** » ; l'encadré « What has changed since version 1.1 (November 19, 2026) » ; article 6 : « 30-day technical trace » et « the date of your last photo » ; article 17 (« Miscellaneous ») : textes légaux en français et en anglais seulement |
| `/fr/confidentialite` | « **Version 1.2 — dernière mise à jour : 21 novembre 2026** » ; article 3 : les lignes **« Date de ta dernière photo »** et **« Registre des notifications envoyées »** (« Jamais de prénom, de nom, de photo, de description, de lieu, de position ni de nom de Miff ») ; la phrase « mis en service progressivement » ; **suppression à 30 jours**, par « Supprimer cette notification » ou « Tout effacer », ou avec le compte ; article 4 : consentement **version 4 (`2026-11-v04`)** et la carte « Une précision sur ce que tu partages » pour les comptes restés en version 3 ; article 13 (« Droit applicable et langues ») : FR et EN seulement. **L'ancienne phrase « nous ne gardons aucun historique des notifications envoyées » ne doit plus apparaître** |
| `/en/privacy` | « **Version 1.2 — last updated: November 21, 2026** » ; « Date of your last photo » ; « Record of notifications sent » (« Never a first name, last name, photo, description, place, location or Miff name ») ; « being put into service gradually » ; « 30-day record » ; version 4 (`2026-11-v04`) ; « only exist in French and English » |
| `/fr/supprimer-mon-compte`, `/en/delete-account` | La phrase sur le **registre de 30 jours** supprimé avec le compte (« registre de 30 jours » / « 30-day record »), avec la réserve « avec une version plus ancienne, seule la suppression automatique à 30 jours s'applique » ; la phrase sur l'arrêt du partage cite **la date de ta dernière photo** (« the date of your last photo ») |
| `/fr/`, `/en/`, `/reset-password` | Ils s'affichent normalement (inchangés) : ni erreur 404, ni image cassée |

**Liens** : sur `/fr/cgu`, « Politique de confidentialité » ouvre `/fr/confidentialite` ; sur `/en/terms`, « Privacy Policy » ouvre `/en/privacy` ; le lien de changement de langue (FR/EN) reste sur la même page.

**Test depuis ton téléphone** : sur l'iPhone **et** sur l'OPPO, ouvre `https://miff-3d283.web.app/fr/cgu` et `https://miff-3d283.web.app/en/privacy` (mobile, navigation privée) : version et date en haut, tableau de l'article 3 de la politique lisible sans zoom horizontal. Dans l'app (Android), « Conditions » et « Confidentialité » ouvrent ces mêmes pages ; pour l'espagnol, l'italien, le portugais et l'allemand, l'app ouvre la **version anglaise** (limite connue, risque J63 assumé et écrit dans les textes).

### 7.3 En-têtes de cache et de réseau (CDN) à vérifier

```powershell
foreach ($u in "/fr/cgu","/en/privacy","/fr/supprimer-mon-compte") {
  $r = Invoke-WebRequest "https://miff-3d283.web.app$u" -UseBasicParsing -Method Head
  "== $u -> $($r.StatusCode)"
  foreach ($h in "Cache-Control","ETag","Age","Last-Modified","X-Cache","Content-Type") { "   {0} : {1}" -f $h, $r.Headers[$h] }
}
```

Ce qu'on vérifie :

- **`Cache-Control`** : aucune règle spéciale n'est posée par le projet pour les pages HTML (`web/firebase.json` ne fixe que les fichiers `/assets/` et `/reset-password`). Tu lis donc la valeur par défaut de Firebase Hosting (observée habituellement : une durée courte, de l'ordre d'une heure : à constater, je ne l'affirme pas). Note-la et donne-la à Claude si elle est très longue (plusieurs jours) : une page périmée resterait alors visible plus longtemps chez les visiteurs.
- **`Age`** et **`X-Cache`** : après un déploiement, Firebase vide le CDN (documentation officielle : « Firebase Hosting automatically clears all your cached content across the CDN until the next request »). Un `Age` très grand (plusieurs heures) juste après la publication serait anormal : préviens Claude.
- **`Last-Modified`** : doit être récent (le jour de la publication). Si la date est celle du 13 ou du 19 novembre alors que le contrôle du §7.1 dit `MANQUE`, tu vois encore l'ancienne version : attends 5 minutes, relance.
- **`Content-Type`** : `text/html; charset=utf-8` (sinon les accents seraient mal lus).
- Les en-têtes de sécurité du site (`Content-Security-Policy`, `Strict-Transport-Security`, etc.) n'ont pas changé : ils viennent de `web/firebase.json`.

## 8. Si quelque chose ne va pas : retour arrière

Console Firebase (projet **Miff / `miff-3d283`**, vérifier le nom en haut) > **Hosting** > historique des versions > choisir la version précédente (celle que tu as notée à l'étape 1 de la checklist : 1.0 du 13 novembre, ou 1.1 du 19 novembre si elle était en ligne) > **Rollback**. Immédiat, gratuit. Aucune commande à taper, donc aucun risque de viser le mauvais projet. Dis-le à Claude pour qu'il note l'état.

**Attention à la conséquence** : un retour arrière remet un texte qui **ne décrit pas le registre de 30 jours**. Si les fonctions du registre (étape 4 de l'ordre de mise en service) ne sont **pas encore** déployées, rien ne s'écrit : aucun problème. Si elles **le sont déjà**, ne reviens pas en arrière sans en parler à Claude d'abord : il faudrait aussi arrêter l'écriture du registre.

## 9. Après la publication

1. Dis à Claude : **« déployé »**, avec le commit (8 caractères) et la date. Il met à jour l'état des déploiements et les pages de suivi (`docs/legal/README.md` §15, `docs/legal/maj-2026-11-21-a-publier.md`, ticket AUTH-W).
2. **Étape suivante de l'ordre de mise en service, sur ton « oui »** : règles Firestore (`docs/dev/deploiement-regles.md`), puis fonctions `notifierUpdate` et `notifierAlerte` (`docs/dev/deploiement-centre-notifications.md` §3), puis distribution des apps. **Ne distribue aucune app et ne déploie pas ces fonctions avant que le §7 soit entièrement `OK`.**
3. Avant toute soumission à un store : contrôler les déclarations Apple (App Privacy) et Google (Data safety) au regard du registre et de la date de dernière photo (risque J71, non vérifié : aucune conformité n'est affirmée).
4. Si tu changes un jour l'éditeur ou le contact (provisoires : Tomessa MFOUKA, `tomessa.mfouka@gmail.com`, 76 rue Marx Dormoy, 75018 Paris, France), la procédure est dans `docs/legal/README.md` §3.

## Ce qui reste ouvert (rien n'est caché)

- Les risques écrits dans `docs/legal/README.md` §4 et §15 (J44 à J72) sont **assumés par toi** : textes jamais relus par un juriste, base légale du registre non tranchée (« Contrat » proposé, J67 f), date de dernière photo (J68), consentement v3 non redemandé (J66), iPhone sans interrupteur des statistiques (J72), textes légaux non traduits en es / it / pt / de (J63), déclarations des stores non vérifiées (J71).
- Les comptes déjà inscrits n'ont **pas** à accepter la 1.2 : la version et la date sont visibles sur les pages, et l'app le signalera « lorsque c'est possible » (J47, bulle bleue J61 encore à développer).
- La page de suppression de compte ne dit pas qui efface le registre quand la demande se fait par e-mail (E12) : le délai de 30 jours s'applique de toute façon.
- L'achat réel de Miff+ n'est pas encore dans le code : la politique devra être complétée à sa livraison (E7).
