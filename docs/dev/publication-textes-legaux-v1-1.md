# Publier les textes légaux en version 1.1 (site Firebase Hosting) : procédure pour le PM

Préparé le 2026-11-19 par `release-manager`. **C'est toi, et toi seul, qui tapes les commandes de déploiement** : aucun agent ne déploie, ne se connecte à Firebase ni ne soumet quoi que ce soit à un store. Chaque commande Firebase porte `--project miff-3d283` (jamais `ghostrun-app`, même s'il est visible depuis ton compte Google).

## 0. Ce que tu publies, en clair

- Le site `https://miff-3d283.web.app` est déjà en ligne depuis le 13 novembre 2026 avec les textes en **version 1.0**.
- Tu publies la **version 1.1, datée du 19 novembre 2026** : une seule mise à jour qui regroupe trois sujets (description facultative d'une photo, journal du temps qui reste sur le téléphone, alerte « Je suis en danger »). Détail : `docs/legal/README.md` §11 à §14.
- Pages modifiées : conditions (`/fr/cgu`, `/en/terms`), confidentialité (`/fr/confidentialite`, `/en/privacy`), suppression de compte (`/fr/supprimer-mon-compte`, `/en/delete-account`) et quelques phrases de la page d'accueil (`/fr/`, `/en/` : « sur ses serveurs »). La version et la date s'affichent en haut de chaque texte.
- **L'alerte n'est pas encore en service** (fonction `notifierAlerte` non déployée, applications pas encore livrées) : les textes le disent honnêtement (« l'alerte arrive progressivement »). Publier le site maintenant ne la met pas en service et n'oblige à rien d'autre.
- **Ce que cette publication ne fait pas** : elle ne change aucune application, ne déploie aucune fonction ni règle, ne modifie pas `termsVersion` (« 1.0 » reste enregistré, décision du 2026-11-18, aucune acceptation forcée) et ne soumet rien à l'App Store ni à Google Play.
- **Coût : 0 €** (Firebase Hosting, plan gratuit Spark, aucun moyen de paiement).

## 1. Avant de commencer (5 minutes, rien à taper)

| # | À vérifier | Si ce n'est pas le cas |
|---|---|---|
| 1 | La pull request `docs/legal-publication-v1-1` est **fusionnée dans `main`** (Claude la fusionne après ton « prêt à merger » : CI verte, revues faites) | Ne rien déployer : le dossier de déploiement lit `main` |
| 2 | Tu es **d'accord avec les textes** (points à relire dans la description de la PR) et tu assumes les risques écrits dans `docs/legal/README.md` §4 (J44 à J64) : les textes ne sont **pas relus par un juriste**, et ils ne le prétendent nulle part | Dis-le à Claude : on corrige avant |
| 3 | **Nous sommes bien le 19 novembre 2026.** Les pages affichent « 19 novembre 2026 ». Si tu publies un autre jour, **dis-le à Claude avant** : il remet le bon jour dans les quatre textes et dans un test | Ne pas publier avec une date fausse |
| 4 | La **clé web** de Firebase (celle qui commence par `AIza`) est dans ton gestionnaire de mots de passe : Firebase console > Paramètres du projet > Général > Vos applications > application Web « Miff Web » > `apiKey` | Elle n'est pas un secret, mais elle ne s'écrit **jamais** dans un fichier, un message ni une capture |
| 5 | Dans la console Google Analytics, la conservation est restée à **14 mois** avec « Réinitialiser les données utilisateur lors d'une nouvelle activité » **désactivé** (la politique l'affirme ; action 1 de `docs/legal/README.md` §3, faite pour la 1.0) | Le corriger avant de publier |

## 2. Préparer le dossier de déploiement (PowerShell)

Ouvre **PowerShell** (menu Démarrer, tape « PowerShell »). Colle ces lignes une par une.

```powershell
cd C:\dev\miff-deploy
git fetch origin
git switch --detach origin/main
git log -1 --format=%H
```

La dernière ligne affiche l'identifiant du commit. **Note les 8 premiers caractères** et **donne-les à Claude** : il confirme que c'est bien le commit qu'il a validé (celui de la fusion de la PR, ou un commit plus récent qui la contient). Si `git switch` se plaint de fichiers modifiés, **arrête-toi** et copie le message à Claude (ne force rien).

Vérifie que l'outil Firebase est là et que ton compte est connecté :

```powershell
cd C:\dev\miff-deploy\backend
npm ci --ignore-scripts
.\node_modules\.bin\firebase.cmd login:list
```

`login:list` doit afficher ton compte Google. Sinon : `.\node_modules\.bin\firebase.cmd login`.

> Pourquoi pas simplement `npx firebase …` ? Depuis le dossier `web`, `npx` ne trouverait pas l'outil installé dans `backend` et proposerait d'en télécharger une version quelconque. On appelle donc l'outil de `backend` directement : c'est la même commande que celle de `web/README.md` §7. Reste sur la commande ci-dessous (elle est lancée depuis `C:\dev\miff-deploy\web`, là où se trouvent `firebase.json` et le garde-fou).

## 3. Contrôles sans rien envoyer

```powershell
cd C:\dev\miff-deploy
node --test "web/tests/*.test.mjs"
```

Attendu : « fail 0 » (environ 78 tests réussis). Si un test échoue, **ne continue pas** : copie la sortie à Claude.

Prévisualisation locale (facultative, 3 minutes) : `node scripts/preview-web.mjs`, ouvre `http://127.0.0.1:5000/fr/cgu`, vérifie que « Version 1.1 — dernière mise à jour : 19 novembre 2026 » est en haut, parcours `/fr/confidentialite`, `/en/terms`, `/en/privacy`, puis `Ctrl+C` pour arrêter.

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

## 5. Essai à blanc (aucune publication)

```powershell
cd C:\dev\miff-deploy\web
..\backend\node_modules\.bin\firebase.cmd deploy --only hosting --project miff-3d283 --dry-run
```

Cette commande **vérifie la configuration et rejoue le garde-fou, sans publier**. Attendu : un message du type « Dry run complete ». Si elle affiche une erreur que tu ne comprends pas, copie-la à Claude : ne passe pas à l'étape 6.

*Option « adresse temporaire » (facultatif, gratuit, ne touche pas au site actuel)* : tu peux publier la même version sur une adresse de prévisualisation qui expire seule, pour la regarder avant :

```powershell
..\backend\node_modules\.bin\firebase.cmd hosting:channel:deploy essai-textes-1-1 --expires 1d --project miff-3d283
```

Firebase affiche une adresse du type `https://miff-3d283--essai-textes-1-1-….web.app` : ouvre-la sur ton téléphone, vérifie la version et la date, puis passe à l'étape 6. (Cette adresse est publique mais non listée et expire dans 1 jour ; la page de mot de passe n'y fonctionnera pas, c'est sans importance.)

## 6. Publier (seulement après ton « oui, déploie » dans la conversation avec Claude)

Dis d'abord à Claude : **« oui, déploie, commit XXXXXXXX »** (avec les 8 caractères de l'étape 2). Puis :

```powershell
cd C:\dev\miff-deploy\web
..\backend\node_modules\.bin\firebase.cmd deploy --only hosting --project miff-3d283 --message "Textes legaux 1.1 du 19 novembre 2026"
```

Attendu : une liste de fichiers envoyés, puis « Deploy complete! » et l'adresse `https://miff-3d283.web.app`. Si le garde-fou refuse (message « DÉPLOIEMENT REFUSÉ… »), **c'est voulu** : ne le contourne pas, relève le message et transmets-le à Claude.

Ensuite, **efface la clé de la session** et ferme la fenêtre :

```powershell
Remove-Item Env:MIFF_WEB_API_KEY
```

## 7. Contrôles après publication (lecture seule)

Dans PowerShell (rien n'est modifié, ce sont de simples lectures) :

```powershell
$base = "https://miff-3d283.web.app"
foreach ($u in "/fr/","/en/","/fr/cgu","/en/terms","/fr/confidentialite","/en/privacy","/fr/supprimer-mon-compte","/en/delete-account","/reset-password") {
  $r = Invoke-WebRequest "$base$u" -UseBasicParsing -Headers @{ "Cache-Control" = "no-cache" }
  "$u -> $($r.StatusCode)"
}
(Invoke-WebRequest "$base/fr/cgu" -UseBasicParsing).Content | Select-String "Version 1.1 — dernière mise à jour : 19 novembre 2026"
(Invoke-WebRequest "$base/fr/confidentialite" -UseBasicParsing).Content | Select-String "Version 1.1 — dernière mise à jour : 19 novembre 2026"
(Invoke-WebRequest "$base/en/terms" -UseBasicParsing).Content | Select-String "Version 1.1 — last updated: November 19, 2026"
(Invoke-WebRequest "$base/en/privacy" -UseBasicParsing).Content | Select-String "Version 1.1 — last updated: November 19, 2026"
```

Attendu : neuf lignes `… -> 200`, et **quatre lignes** qui montrent la version et la date (une par texte). Une ligne manquante = la page n'est pas à jour : attends 5 minutes (cache), recharge, sinon préviens Claude.

**À regarder à l'écran (navigateur, navigation privée) :**

| Page | Ce qu'on doit voir |
|---|---|
| `/fr/cgu` | « Version 1.1 — dernière mise à jour : 19 novembre 2026 » sous le titre ; dans l'encadré du début, la puce « L'alerte « Je suis en danger » prévient tes proches ; elle ne prévient pas les secours » ; un article « 6 bis. L'alerte « Je suis en danger » » qui commence par « Disponibilité. L'alerte arrive progressivement » |
| `/en/terms` | « Version 1.1 — last updated: November 19, 2026 » ; l'article « 6 bis. The "I'm in danger" alert » |
| `/fr/confidentialite` | Version et date ; dans l'article 3, les lignes « Alerte « Je suis en danger » », « Compteur de tes alertes (plafond) » et « Journal de répartition de ton temps » ; dans l'article 4, « Le journal de répartition du temps… en détail » |
| `/en/privacy` | Version et date ; « "I'm in danger" alert », « Counter of your alerts (cap) », « Log for your time breakdown » |
| `/fr/supprimer-mon-compte`, `/en/delete-account` | La phrase sur le créateur d'une Miff cite aussi les « alertes » ; la phrase sur l'arrêt du partage cite le journal du temps |
| `/fr/`, `/en/` | La page d'accueil s'affiche normalement (images, boutons) ; « sur ses serveurs » dans la section « Pas d'historique de trajets » |
| `/reset-password` | La page s'affiche (elle indique qu'un lien est nécessaire) ; elle ne doit pas être une erreur 404 |

**Liens** : sur `/fr/cgu`, clique « Politique de confidentialité » (doit ouvrir `/fr/confidentialite`) ; sur `/en/terms`, « Privacy Policy » (doit ouvrir `/en/privacy`) ; en bas de page, le lien de changement de langue (FR/EN) doit rester sur la même page.

**Test depuis ton téléphone** : sur l'iPhone **et** sur l'OPPO, ouvre `https://miff-3d283.web.app/fr/cgu` et `https://miff-3d283.web.app/en/privacy` (mobile, **en navigation privée** pour éviter le cache), vérifie la version et la date en haut, fais défiler jusqu'à l'article 6 bis (CGU) et au tableau de l'article 3 (politique) : le texte doit rester lisible sans zoom horizontal. Dans l'app (Android), le lien « Conditions » / « Confidentialité » doit ouvrir ces mêmes pages ; pour une langue espagnol / italien / portugais / allemand, l'app ouvre la **version anglaise** (limite connue, risque J63 assumé).

## 8. Si quelque chose ne va pas : retour arrière

Console Firebase (projet **Miff / `miff-3d283`**, vérifier le nom en haut) > **Hosting** > « Historique des versions » > choisir la version précédente (celle du 13 novembre) > **Rollback**. Immédiat, gratuit. Dis-le à Claude pour qu'il note l'état.

## 9. Après la publication

1. Dis à Claude : **« déployé »**, avec le commit (8 caractères) et la date. Il met à jour l'état des déploiements et les pages de suivi (`docs/legal/README.md` §14, ticket AUTH-W).
2. **Tu n'as rien d'autre à faire pour l'instant** côté site. La suite, plus tard et sur ton « oui » à chaque étape : applications (bulle bleue « nouveauté » et fiche « En savoir plus » pour les comptes existants, décidée le 2026-11-18), puis déploiement de la fonction `notifierAlerte` (`docs/dev/deploiement-alerte-danger.md`), **après** que ce site soit en ligne.
3. Si tu changes un jour l'éditeur ou le contact (provisoires : Tomessa MFOUKA, `tomessa.mfouka@gmail.com`, 76 rue Marx Dormoy, 75018 Paris, France), la procédure est dans `docs/legal/README.md` §3 : un seul fichier de configuration (`web/site.config.json`) et une recherche dans les quatre textes.

## Ce qui reste ouvert (rien n'est caché)

- Les risques J44 à J64 de `docs/legal/README.md` §4 sont **assumés par toi** (décision du 2026-10-23, pas de relecture juridique) : en particulier l'absence de signalement et de blocage dans l'app (J44), la preuve d'acceptation qui reste « 1.0 » (J46, J61), l'alerte non reçue ou non lue (J51), la version anglaise ouverte pour les langues sans traduction légale (J63).
- **J47 n'est pas tranché** : l'article 15 des CGU promet de prévenir dans l'app avant qu'un changement important s'applique ; la bulle prévue ne parlera que de l'alerte tant que tu n'as pas décidé qu'elle parle aussi de la description et du journal.
- Les comptes déjà inscrits n'ont **pas** à accepter la 1.1 : ils sont informés (bulle, à développer) et la version et la date sont visibles sur les pages.
