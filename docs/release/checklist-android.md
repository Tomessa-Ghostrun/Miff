# Checklist de soumission Google Play (Android)

Préparée le 2026-11-11 par `android-dev` (lot « préparation de la release Android »). **Rien n'est soumis, aucun compte n'est créé ni payé, aucun fichier n'est envoyé à Google.** Tout ce qui est à faire dans la Play Console ou sur ton poste est écrit ci-dessous, dans l'ordre, pas à pas. Les agents ne se connectent jamais à tes comptes.

**À savoir avant de commencer**
- Les **noms des menus de la Play Console** sont écrits de mémoire et peuvent avoir changé de quelques mots : aucun agent n'a accès à ta console pour les vérifier. Si un intitulé ne se trouve pas, cherche le mot-clé en gras, ou la barre de recherche de la console. En cas de doute, **ne clique pas sur « Envoyer pour examen »** et demande.
- Pour tout ce qui touche Firebase : **toujours le projet `miff-3d283`**, jamais `ghostrun-app` (même compte Google, deux projets).
- Les comptes développeur existent déjà (Google Play : 25 € payés une fois, créé pour Ghostrun). **Rien à payer.**
- **Un secret ne va jamais dans le dépôt, un message ou une capture** (`docs/dev/secrets.md`). Les mots de passe ci-dessous se tapent à l'écran, jamais dans une commande copiée dans une conversation.

## 0. Ce qui est prêt, ce qui ne l'est pas

| Sujet | État |
|---|---|
| Numéro de version | **Prêt.** Le nom de version (par exemple `0.32.0`) vient du titre le plus récent de `android/CHANGELOG.md`. Le numéro interne (`versionCode`) vient du numéro d'exécution du workflow de release : il ne fait que croître, c'est ce que Google exige. Plus rien n'est figé à `1` / `0.1.0`. |
| Signature | **Prêt côté code, bloqué côté PM** : il faut créer la **clé d'envoi**, l'**environnement GitHub protégé** `android-release`, puis 4 secrets dans cet environnement (étape 2). Sans eux, aucun bundle signé n'est produit (le workflow le dit clairement) ; avec une partie seulement, il s'arrête en rouge en listant ce qui manque. |
| Bundle signé | **Prêt côté code** : workflow manuel `android-release` (jamais déclenché par une PR). **Jamais exécuté de bout en bout** : il ne peut pas l'être avant la fusion dans `main` ni sans tes secrets. Premier essai à surveiller. |
| Pages mémoire de 16 Ko (Android 15+) | **Contrôlé automatiquement** (voir « Annexe technique »). |
| Contrôles « pas de code de debug dans le bundle » | **Prêts**, joués à chaque PR et de nouveau sur le bundle signé. |
| Création de l'app, piste de test, déclarations | **À faire par toi** (étapes 3 à 9). |
| Fiche de l'app (textes, captures, icône) | **Pas écrite** : `aso-marketing` (hors de cette checklist). |

## 1. Avant de commencer : quelques constats à faire (15 min)

1. Ouvre la Play Console (https://play.google.com/console) avec le compte développeur existant.
2. Relève dans **Paramètres > Détails du compte** (ou la page « Compte développeur ») : le **type** du compte (personnel ou organisation) et sa **date de création**. Note-les ici : type : ______ ; créé le : ______.
3. **Pourquoi c'est important** : Google impose aux comptes **personnels créés après le 13 novembre 2023** de faire tester l'app par **au moins 12 testeurs inscrits en continu pendant 14 jours** avant d'accéder à la production (source : page Google « Test requirements for new personal developer accounts », lue le 2026-11-07, détail dans `decisions-stores-avant-soumission.md` §6). Si ton compte est dans ce cas, **le calendrier Android est de 14 jours minimum après l'ouverture du test fermé** : planifie-le dès que l'app est assez stable. Si ton compte est ancien ou « organisation », la règle peut ne pas s'appliquer ; **la page ne dit pas** si elle revient pour chaque nouvelle app d'un même compte : **la console te le dira** au moment de demander la production.
4. Vérifie que ton **identité** et ton **adresse e-mail de contact** sont validées dans la console (Google peut demander des justificatifs). Le nom, l'e-mail et l'adresse de l'éditeur sont publics (risque assumé, `CLAUDE.md`).

## 2. La clé d'envoi (une seule fois, sur ton poste)

**Ce que c'est** : deux clés existent. La **clé d'envoi** est la tienne : elle prouve que le fichier envoyé vient bien de nous. La **clé de signature** de l'app est gardée par Google (« Play App Signing ») : Miff ne la détient jamais, c'est plus sûr (si la clé d'envoi fuit ou se perd, Google la remplace ; la clé de signature de l'app, elle, ne change jamais).

**Une clé de debug n'est jamais une clé d'envoi.** Ne réutilise pas `miff-debug.keystore`.

1. Crée un dossier **hors du dépôt** pour les secrets, par exemple `C:\Users\tomes\miff-secrets`. Il est sauvegardé ailleurs (clé USB chiffrée ou gestionnaire de mots de passe), **pas** dans OneDrive non chiffré, **jamais** dans `C:\dev\miff`.
2. Ouvre PowerShell et crée la clé. `keytool` est fourni avec Android Studio (dossier `jbr\bin`) ; adapte le chemin si besoin :

   ```powershell
   New-Item -ItemType Directory -Force "$HOME\miff-secrets" | Out-Null
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore "$HOME\miff-secrets\miff-upload.jks" -alias miff-upload -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Miff Upload, O=Miff, C=FR"
   ```

   `keytool` te demande **deux mots de passe** (celui du fichier, puis celui de la clé : tu peux mettre le même). **Tape-les à l'écran, ils ne s'affichent pas.** Choisis des mots de passe longs, **enregistre-les tout de suite dans ton gestionnaire de mots de passe** avec le nom d'alias (`miff-upload`). Validité 10 000 jours (environ 27 ans) : au-delà de 2033, comme Google le demande.
3. **Sauvegarde** `miff-upload.jks` et les mots de passe en **deux endroits**. Si tu perds la clé d'envoi, Google sait la remplacer sur demande (voir `docs/dev/secrets.md`, « En cas de fuite »), mais c'est une démarche de plusieurs jours : mieux vaut deux copies.
4. **Obligatoire, avant tout secret : crée l'environnement GitHub `android-release`** (gratuit sur un dépôt public, le dépôt l'est depuis le 2026-11-04). GitHub > dépôt Miff > Settings > **Environments** > **New environment** > nom exact `android-release` > **Configure environment**, puis :
   - coche **Required reviewers** et ajoute-toi (chaque lancement te demandera de l'approuver, ce qui prend un clic) ;
   - dans **Deployment branches and tags**, choisis **Selected branches and tags** et ajoute **`main`** seulement.

   *Pourquoi c'est obligatoire* : le workflow refuse déjà de tourner hors de `main`, mais cette vérification est écrite dans le workflow lui-même ; quiconque peut pousser une branche pourrait la modifier. La règle de l'environnement, elle, est appliquée par GitHub **avant** que le moindre secret soit remis au travail. C'est ce qui protège réellement la clé d'envoi. Si ces réglages n'apparaissent pas dans ton interface, **arrête-toi ici et préviens l'équipe technique** : ne crée pas les secrets sans cette protection.
5. Crée maintenant les 4 secrets d'envoi **comme secrets de cet environnement** (et non comme secrets du dépôt). **Les valeurs se tapent à l'invite, elles ne s'affichent pas** :

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\miff-secrets\miff-upload.jks")) | gh secret set ANDROID_UPLOAD_KEYSTORE_B64 --env android-release --repo Tomessa-Ghostrun/Miff
   gh secret set ANDROID_UPLOAD_STORE_PASSWORD --env android-release --repo Tomessa-Ghostrun/Miff
   gh secret set ANDROID_UPLOAD_KEY_ALIAS --env android-release --repo Tomessa-Ghostrun/Miff
   gh secret set ANDROID_UPLOAD_KEY_PASSWORD --env android-release --repo Tomessa-Ghostrun/Miff
   ```

   Aux trois dernières invites : le mot de passe du fichier, `miff-upload`, le mot de passe de la clé. Le secret `GOOGLE_SERVICES_JSON_B64` reste un secret du dépôt : il existe déjà (il sert aussi au workflow `android`). Si tu as créé par erreur un des 4 secrets au niveau du dépôt, supprime-le (Settings > Secrets and variables > Actions) : seuls les secrets de l'environnement sont protégés. Les 5 secrets doivent être **tous présents**, sinon le workflow s'arrête en rouge en les listant (et seulement si aucun n'existe, il affiche calmement « non configurée »).
6. Vide ton presse-papiers (copie un mot quelconque) et ferme PowerShell.

## 3. Créer l'app dans la Play Console

1. Play Console > **Créer une application**.
2. **Nom de l'application** : `Miff`. (Le nom affiché sur Google Play ; décision du nom : point 7 de `decisions-stores-avant-soumission.md`. Vérifie qu'il est libre.)
3. **Langue par défaut** : français (France). L'anglais s'ajoute ensuite comme traduction de la fiche.
4. **Application ou jeu** : *Application*. **Gratuite ou payante** : *Gratuite* (l'app est gratuite, Miff+ est un abonnement dans l'app). **Attention : une app gratuite ne peut plus devenir payante** ; l'abonnement n'est pas concerné.
5. Coche les déclarations (règles du programme pour les développeurs, lois américaines sur l'exportation) après les avoir lues, puis **Créer l'application**.
6. **L'identifiant `com.tomessa.miff` n'est pas saisi ici** : il est lu dans le **premier bundle** que tu envoies (étape 5), et il est **définitif** une fois l'app publiée. Il vient du projet (`applicationId`), tu n'as rien à saisir.

## 4. Construire le bundle signé

Prérequis : l'étape 2 faite, et la PR de cette préparation **fusionnée dans `main`** (un workflow ne peut se lancer à la main qu'une fois présent sur `main`).

1. GitHub > dépôt Miff > onglet **Actions** > workflow **android-release** > **Run workflow** > branche **main** > laisse « 0 » pour le décalage du numéro de version et « true » pour Crashlytics > **Run workflow**. (Tu reçois une demande d'approbation, étape 2.4 : ouvre-la et approuve le lancement.)
2. Attends la fin (quelques minutes). Deux issues possibles :
   - **« Release Android non configurée »** (résumé de l'exécution, exécution verte) : il manque un ou plusieurs secrets (liste donnée, sans valeurs). Reprends l'étape 2.
   - **« Bundle Android signé »** : le résumé donne la version, le numéro interne, les **empreintes de la clé d'envoi** (SHA-1 et SHA-256, publiques) et la liste des contrôles passés.
3. Télécharge l'**artefact `miff-release-aab`** (en bas de la page de l'exécution) : c'est un zip qui contient `app-release.aab`. **Il reste 14 jours**, et comme le dépôt est public, tout compte GitHub connecté pourrait le télécharger : il ne contient aucun secret (jamais la clé privée), mais ne le partage pas inutilement et ne le dépose nulle part ailleurs que dans la Play Console.
4. Si le résumé indique une erreur de signature, de numéro de version refusé par Google, ou d'envoi du fichier de correspondance Crashlytics : relance avec le décalage ou l'option Crashlytics indiqués dans l'aide du formulaire, ou demande à l'équipe technique.

**Numéro de version refusé par Google** (« Le code de version X a déjà été utilisé ») : relance avec un **décalage** plus grand (par exemple 100). Le numéro envoyé est le numéro d'exécution + le décalage.

## 5. Premier envoi : Play App Signing et piste de test interne

1. Play Console > ton app > **Test et publication** > **Tests** > **Test interne** > **Créer une release**.
2. À la première release, la console propose **Play App Signing** : **accepte** (c'est le mode par défaut et obligatoire pour les nouveaux bundles). **Ne dépose jamais ta clé privée** : seul le bundle `.aab` signé avec la clé d'envoi est attendu.
3. **Importe** `app-release.aab`. La console lit l'identifiant `com.tomessa.miff` et la version dans le fichier. Si elle affiche un autre identifiant : **arrête-toi**, ne publie pas.
4. **Notes de version** : une ou deux phrases sobres (FR et EN), ton calme et non alarmiste.
5. **Enregistrer**, **Vérifier la release**, puis **Lancer le déploiement** sur la piste interne.
6. **Testeurs** : onglet « Testeurs » de la piste > créer une liste d'adresses e-mail (comptes Google des testeurs, jusqu'à 100 sur la piste interne) > enregistrer > copier le **lien d'inscription** et l'envoyer aux testeurs (toi, avec l'OPPO Find X5 Lite, d'abord).
7. Installe depuis le **Play Store** (lien d'inscription, accepter, installer) sur l'OPPO. C'est **le seul moyen d'obtenir un jeton Play Integrity** : c'est ce qui débloque App Check pour Android (`docs/dev/app-check.md`, AC-2).
8. **Vérification de la clé** : Test et publication > **Configuration** > **Intégrité de l'application** (ou « Signature de l'application ») : la console affiche le **certificat de la clé d'envoi**. Compare son SHA-256 à celui du résumé de l'exécution GitHub : **ils doivent être identiques**. Si ce n'est pas le cas, ne continue pas.

## 6. Ajouter les empreintes dans Firebase (indispensable pour « Continuer avec Google »)

Quand l'app est installée depuis Google Play, **c'est la clé de signature de Google** qui signe l'application, pas la tienne. Sans son empreinte dans Firebase, **la connexion Google échoue** dans le build du Play Store (elle marche dans les builds de test de la CI, qui ont une autre clé).

1. Play Console > **Intégrité de l'application** > **Signature de l'application** > bloc **Certificat de signature de l'application** : copie le **SHA-1** et le **SHA-256**. (Ce sont des empreintes **publiques** : pas de risque.)
2. Console Firebase, projet **Miff (`miff-3d283`)** > roue dentée > **Paramètres du projet** > **Vos applications** > Android `com.tomessa.miff` > **Ajouter une empreinte** : ajoute le **SHA-1** puis le **SHA-256** de l'étape 1. Ajoute aussi, par prudence, le SHA-1 et le SHA-256 de la **clé d'envoi** (donnés dans le résumé de l'exécution GitHub).
3. **Régénère** le fichier de configuration puis **remplace** le secret (le fichier contient alors les nouveaux clients OAuth) :

   ```powershell
   firebase apps:sdkconfig ANDROID 1:29899146512:android:307ffd8ea71a4a37659195 --project miff-3d283 | Set-Content "$HOME\miff-secrets\google-services.json"
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\miff-secrets\google-services.json")) | gh secret set GOOGLE_SERVICES_JSON_B64 --repo Tomessa-Ghostrun/Miff
   ```

   Vérifie que la première commande n'écrit pas d'avertissement dans le fichier (il doit commencer par `{`). Ne commite jamais ce fichier.
4. Relance l'étape 4 pour obtenir un bundle avec la nouvelle configuration, puis dépose-le dans une **nouvelle release** de la piste interne (le numéro de version augmente tout seul).
5. **App Check** (Play Integrity) : suis `docs/dev/app-check.md`, **étapes P3 et P4** : Play Console > Intégrité de l'application > API Play Integrity > **lier le projet Cloud `miff-3d283`** (il faut en être propriétaire), puis console Firebase > App Check > Apps > enregistrer l'app Android avec **Play Integrity** et le **SHA-256 de la clé de signature de l'application** (celui de Google, pas celui de la clé d'envoi), durée du jeton 1 heure. **Ne pas « appliquer » App Check** à Firestore à ce stade : ordre à respecter dans `app-check.md`.

## 7. Test fermé et règle des 12 testeurs (si ton compte est concerné)

1. Test et publication > **Tests** > **Test fermé** > **Créer une piste** (nom : « Bêta Miff ») > **Créer une release** > importe le dernier bundle (ou promeus la release interne).
2. **Testeurs** : crée une liste d'**au moins 12** personnes (cinq cercles de trois couvrent le seuil, `marketing/launch-plan.md`), avec leurs adresses de compte Google. Ne recours **pas** à un service de testeurs « à louer » (ce n'est pas le test que demande Google ; `CLAUDE.md` classe ce contournement pire qu'un coût maîtrisé).
3. Envoie le **lien d'inscription**. Chaque testeur doit **accepter**, **installer** et **rester inscrit 14 jours de suite** ; un testeur qui se désinscrit avant ne compte pas. Les testeurs de la piste **interne** ne comptent pas pour cette règle.
4. **Avant d'ouvrir** : vérifie qu'aucun testeur extérieur n'a un build avec le consentement `2026-11-v03` d'origine (actions 13 et 19 de `README.md`).
5. Au 15e jour : Play Console > **Tableau de bord** > **Demander l'accès à la production** : un court questionnaire (comment l'app a été testée, retours pris en compte). Réponds honnêtement. Google répond en général sous quelques jours (délai non garanti, source tierce).

## 8. « Contenu de l'application » : déclarations obligatoires

Play Console > **Contenu de l'application** (ou « Politique > Contenu de l'application »). Chaque ligne affiche un état ; tout doit passer au vert avant la production.

| Déclaration | Où trouver les réponses | Remarque |
|---|---|---|
| **Politique de confidentialité** (adresse web) | `web/` (pages `confidentialite`), `decisions-stores-avant-soumission.md` §7 | **Bloqué** : le site n'est pas déployé. Il faut ton « oui, déploie » (hébergement Firebase `miff-3d283.web.app`) **avant** cette étape, et les actions de la console Analytics du `docs/legal/README.md` §3 d'abord. |
| **Sécurité des données** (Data safety) | `google-play-data-safety.md` | Lis l'aide affichée à côté de chaque question ; point ouvert O9 (les autres membres d'une Miff comptent-ils comme un « partage » ? en cas de doute, déclare le partage). L'adresse de **suppression de compte** est demandée : `web/` (page suppression). |
| **Autorisations de localisation** (en arrière-plan) | `google-play-localisation-arriere-plan.md` §3 (libellé), §4 (justification), §8 (**vidéo de 30 s**) | La vidéo se tourne sur l'OPPO, se met sur YouTube en « non répertoriée » et son lien se colle dans le formulaire. C'est la déclaration que Google regarde le plus attentivement pour une app comme Miff : prends ton temps et relis le texte de justification. |
| **Accès à l'application** | `docs/release/README.md`, action 7 | Compte de test (e-mail + mot de passe, Miff déjà créée, **sans** authentification à deux facteurs) pour les relecteurs de Google ; deux comptes dans la même Miff pour voir une photo localisée (action 16). |
| **Classification du contenu** | `decisions-stores-avant-soumission.md` §2 (décision du 2026-11-07 : classification la plus basse possible, pas un ciblage des enfants) | Réponds aux questions du jour, honnêtement. |
| **Public cible** | idem, point O7 | **Ne déclare pas** l'app comme destinée aux enfants (recommandation). |
| **Contenu généré par les utilisateurs** | `update-photo-declarations.md` (UGC, décisions P1 à P4) | Risque Google « UGC » assumé (J34, J35). |
| **Annonces** | — | L'app ne contient aucune publicité à ce jour : à confirmer avec toi. |
| **Autres** (application gouvernementale, fonctions financières, santé...) | — | Réponds « non » aux rubriques qui ne concernent pas Miff, en lisant chaque question. |
| **Statut « trader » (loi européenne sur les services numériques)** | `decisions-stores-avant-soumission.md` §5 | Ton nom, ton adresse et ton e-mail deviennent publics (risque assumé, `CLAUDE.md`). Lis ce qui est déjà déclaré pour Ghostrun. |

Ensuite : **fiche principale** (textes FR et EN, icône, captures, visuel de présentation) : à rédiger avec `aso-marketing`, **hors de cette checklist**. **Catégorie** de l'app : décision du point 3 de `decisions-stores-avant-soumission.md`.

## 9. Du test à la production

1. Quand les étapes 5 à 8 sont vertes et la demande d'accès à la production acceptée : Test et publication > **Production** > **Créer une release** > promeus le bundle testé > **pays et régions** > **Envoyer pour examen**.
2. **Cette dernière étape engage la publication : elle ne se fait qu'avec la porte « prêt à soumettre »** du process (checklist QA de sortie `docs/qa/release-checklist.md`, qui n'existe pas encore ; contrôle de release de la fenêtre d'avis, `decisions-stores-avant-soumission.md` §11 ; décision d'ordre iOS / Android, point 20). Aucun agent ne clique à ta place.
3. Après l'envoi : l'examen peut prendre plusieurs jours ; une app avec localisation en arrière-plan est examinée avec plus d'attention. Un refus n'est pas définitif : le motif est écrit dans la console ; reviens vers l'équipe technique.

## 10. Ce qui reste bloqué côté PM (récapitulatif)

| # | À faire | Bloque quoi |
|---|---|---|
| 1 | Relever type et date du compte Play, décider quand ouvrir le test fermé (14 jours) | Le calendrier de lancement Android |
| 2 | Créer la clé d'envoi, l'environnement `android-release` (relecteur : toi, branche `main` seule), puis les 4 secrets dans cet environnement (étape 2) | Tout bundle signé |
| 3 | Fusionner la PR de préparation dans `main`, lancer `android-release` (étape 4) | Le premier bundle |
| 4 | Créer l'app, accepter Play App Signing, ouvrir la piste interne (étapes 3 et 5) | L'installation depuis Google Play, donc Play Integrity |
| 5 | Ajouter les empreintes dans Firebase, régénérer `GOOGLE_SERVICES_JSON_B64` (étape 6) | « Continuer avec Google » dans le build du Play Store |
| 6 | Lier le projet Cloud et enregistrer Play Integrity dans App Check (étape 6.5) | L'application d'App Check sur Firestore (`app-check.md`) |
| 7 | Dire « oui, déploie » pour le site (politique de confidentialité en ligne) | La déclaration de confidentialité et Data safety |
| 8 | Tourner la vidéo de la localisation en arrière-plan (30 s) | La déclaration de localisation |
| 9 | Préparer le compte de test des relecteurs, les captures, la fiche | « Accès à l'application », la fiche |
| 10 | Recruter 12 testeurs qui restent 14 jours (si le compte est concerné) | La production |
| 11 | Trancher O7 (public cible), O8 (risque « stalkerware » assumé, rien à décider de plus), O9 (partage), ordre iOS / Android | Les formulaires |

## Annexe technique (pour l'équipe, pas pour le PM)

### Versions
- `android/app/build.gradle.kts` : `versionName` = premier titre `## [x.y.z]` de `android/CHANGELOG.md` (ou `-Pmiff.versionName`) ; `versionCode` = `-Pmiff.versionCode=N` si donné, sinon `major * 1 000 000 + minor * 1 000 + patch` (0.32.0 donne 32000). Un build **signé** refuse de démarrer sans `-Pmiff.versionCode` (garde sur `preReleaseBuild`) : le workflow de release donne `run_number + version_code_offset`.
- Les APK de test de la CI (`debug`, `releaseLike`) et le bundle non signé portent le numéro dérivé ; la CI relit le numéro réel de l'APK avec `aapt2` (étape « Check the version carried by the APK »).
- Le compteur `run_number` est propre au workflow : **ne pas renommer ni recréer `android-release.yml`** (le compteur repartirait de 1 ; le décalage d'entrée existe pour ce cas).
- Le numéro dérivé (32000 et plus) et le numéro de release (1, 2, 3...) sont deux séries distinctes : seul le second est envoyé à Google Play. **Ne jamais envoyer à Google Play un APK ou un bundle construit autrement que par le workflow** : un numéro dérivé élevé bloquerait la série.

### Signature
- Variables d'environnement, lues seulement par Gradle : `MIFF_UPLOAD_STORE_FILE`, `MIFF_UPLOAD_STORE_PASSWORD`, `MIFF_UPLOAD_KEY_ALIAS`, `MIFF_UPLOAD_KEY_PASSWORD`. Aucune : release non signée (ce que construit la CI de chaque PR). Une partie seulement : le build échoue. Jamais la clé de debug.
- Le workflow décode le `.jks` depuis le secret dans un fichier temporaire du runner, l'ouvre avec `keytool -storepass:env` (mots de passe jamais sur la ligne de commande), construit avec `--no-configuration-cache --no-build-cache` (les mots de passe ne vont dans aucun cache sur disque, un bundle signé n'est jamais mis en cache), vérifie la signature (`jarsigner -verify`, empreinte égale à celle de la clé d'envoi, pas la clé de debug), joue `android/ci/check-release-bundle.sh`, publie le seul `.aab`, puis supprime le fichier de clé et `google-services.json`.
- La CI de chaque PR vérifie le câblage avec une **clé jetable** générée à la volée (variante `release` signée par la configuration `release`, `debug` et `releaseLike` inchangées, signature à moitié configurée refusée, numéro de version obligatoire).
- Aucun envoi automatique vers Google Play : il demanderait une clé de compte de service Google (interdite, `docs/dev/secrets.md`). Si le volume des envois le justifie un jour, c'est une décision PM séparée.

### Pages mémoire de 16 Ko (Android 15+)
- **Règle** : depuis le 1er novembre 2025, les nouvelles apps et mises à jour qui visent Android 15 ou plus doivent fonctionner sur des appareils à pages de 16 Ko : les bibliothèques natives **64 bits** (`arm64-v8a`, `x86_64`) doivent avoir leurs segments ELF alignés sur au moins 16 384 octets (source : documentation Android « Support 16 KB page sizes », à relire avant la soumission ; non relue ce jour faute d'accès au web dans cette session, la règle est celle connue de l'équipe).
- **Contrôle** : `android/ci/check-elf-16kb.mjs` lit les en-têtes ELF de chaque `.so` 64 bits du bundle (CI de chaque PR sur le bundle non signé, workflow de release sur le bundle signé) ; ses propres tests utilisent des fichiers fabriqués à la main. Résultat du bundle du 2026-11-11 : voir « Résultat » ci-dessous.
- **Empaquetage** : Android Gradle Plugin 9.5.1 aligne les bibliothèques non compressées sur 16 Ko dans l'APK ; il ne corrige pas l'alignement interne d'une bibliothèque tierce. Si un `.so` est signalé : mettre à jour la bibliothèque (version compilée avec NDK r28 ou plus), pas contourner le contrôle.

### Résultat de la mesure (2026-11-11, bundle release de `main` + ce lot)

Les **10 bibliothèques natives 64 bits** du bundle ont leurs segments alignés sur **0x4000 (16 Ko)** : `libmaplibre.so` (**MapLibre 13.7.1**, arm64-v8a et x86_64), `libimage_processing_util_jni.so` et `libsurface_util_jni.so` (CameraX), `libandroidx.graphics.path.so` (AndroidX), `libdatastore_shared_counter.so` (DataStore). Aucune autre bibliothèque native dans le bundle (Firebase, Play services, libphonenumber, Haze, Compose : code Java/Kotlin seulement). Les ABI 32 bits (`armeabi-v7a`, `x86`) sont aussi dans le bundle mais **ne sont pas concernées** par la règle. **Conclusion : MapLibre et les autres bibliothèques sont compatibles aujourd'hui.** Le contrôle automatique garde ce résultat : une mise à jour de bibliothèque (Dependabot) qui réintroduirait un alignement de 4 Ko ferait échouer la CI de la PR. **Non vérifié** : un test sur un appareil ou un émulateur réel à pages de 16 Ko (aucun n'est disponible dans cette session) ; l'alignement des fichiers est la condition vérifiable, pas une preuve de bon fonctionnement à l'exécution.

### Sécurité (ce que ce lot ajoute et ce qu'il ne fait pas)
- Aucun secret, clé ni mot de passe dans le dépôt : `.gitignore` et `scripts/check-forbidden-files.mjs` refusent `.jks`, `.keystore`, `.p12`, `.aab`, `key.properties` ; `ReleaseSigningGuardTest` vérifie en plus qu'aucun de ces fichiers n'existe dans l'arbre et que le workflow n'affiche, n'archive ni ne met en cache aucun secret.
- Le workflow ne se lance que par `workflow_dispatch`, depuis `main`, jamais par une PR (jamais de `pull_request_target`).
- Les empreintes publiées dans le résumé de l'exécution sont celles du **certificat** (publiques par nature), pas de la clé privée.
- Un artefact d'un dépôt public peut être téléchargé par tout compte GitHub connecté : le bundle n'a aucun secret ; durée 14 jours.
