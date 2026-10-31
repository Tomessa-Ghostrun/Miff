# Spike iOS (INIT-3) : de GitHub à l'iPhone, sans Mac

**Statut** (2026-10-22) : le workflow `ios` (build et tests sans signature) **a tourné et est vert** sur la PR #1 (run 39659012614 : génération XcodeGen, SwiftLint 0 violation, build-for-testing, 10 tests, 0 échec, 1 min 40 de job, environ 2 minutes facturées). Le test `MinimumOSVersion` (cible iOS 16.0) y est donc **vert**. Une correction (iPhone seul, `UIDeviceFamily`) et un contrôle supplémentaire ont été ajoutés depuis ce run et passeront par une nouvelle exécution de la CI. Le workflow **`ios-testflight` (signature et envoi TestFlight) n'a, lui, jamais tourné** : tout ce qui touche à la signature, à Apple et à l'iPhone reste non vérifié. Aucun Mac n'est disponible pour le développeur ; les passages marqués « à vérifier » sont des points que seul le premier vrai essai peut confirmer.
**Rédigé le** : 2026-10-21 (agent `ios-dev`). **Ticket** : `docs/tickets/initialisation-technique.md`, sous-ticket INIT-3.

---

## 1. Ce que fait le spike

Un « spike » est un essai volontairement minimal pour lever un risque. Ici, le risque (R1 du ticket) est : **peut-on construire, signer et livrer une app iOS sans jamais toucher un Mac ?** La chaîne à prouver :

1. Un fichier texte (`ios/project.yml`) décrit le projet ; l'outil **XcodeGen** fabrique le projet Xcode à la demande.
2. Un ordinateur Apple loué par GitHub (« runner » `macos-26`) compile l'app et lance ses tests : c'est le workflow **`ios`**, automatique sur les pull requests qui touchent `ios/`.
3. Sur demande, le workflow **`ios-testflight`** fabrique une version signée, l'envoie à **App Store Connect**, et elle apparaît dans **TestFlight**.
4. Vous l'installez sur votre iPhone 11 Pro.

L'app elle-même est un écran « Hello Miff » (nom, version, numéro de build) : pas de Firebase, pas de localisation, aucune permission. L'icône est **provisoire** (carré bleu, M blanc, cœur jaune-vert dessinés par un script) : le vrai logo n'a pas encore été fourni, **ne pas publier**.

Tout est gratuit (voir l'inventaire en section 9). Aucun moyen de paiement n'est ajouté.

---

## 2. Ce que vous devez faire, dans l'ordre

Les délais d'Apple sont hors de notre contrôle : commencez par les étapes 1 à 5 dès que possible. Rien de cela n'est fait par un agent (comptes, accès nominatifs). Ne collez **jamais** un secret (clé, identifiant de clé) dans une conversation, un ticket ou un fichier du dépôt.

### Étape 1. Compte Apple Developer et accords
1. Allez sur https://appstoreconnect.apple.com et connectez-vous avec le compte Apple Developer (celui de Ghostrun).
2. Si un bandeau signale un accord à accepter, acceptez-le (menu **Accords, fiscalité et opérations bancaires** / « Business »). Tant qu'un accord est en attente, l'envoi d'un build est refusé.
3. Notez si le compte est **individuel** ou une **organisation** (cela change les rôles disponibles).
4. Vérifiez sur https://developer.apple.com/account que l'adhésion est **active** (non expirée).

### Étape 2. Noter le Team ID
https://developer.apple.com/account > **Adhésion** (« Membership details ») > **Team ID** : 10 caractères, lettres majuscules et chiffres (exemple de forme : `ABCDE12345`). C'est le secret `APPLE_TEAM_ID` de l'étape 7.

### Étape 3. Enregistrer l'identifiant d'app `com.tomessa.miff`
1. https://developer.apple.com/account/resources/identifiers/list > bouton **+** > **App IDs** > **App**.
2. Description : `Miff`. Bundle ID : choisir **Explicit** et saisir exactement `com.tomessa.miff`.
3. **Capacités** : le spike n'en a besoin d'aucune, ne cochez rien. La capacité **App Attest** (exigée plus tard par INIT-4 pour App Check) se cochera à ce moment-là sur cette même page ; la signature automatique mettra alors le profil à jour toute seule. L'identifiant `com.tomessa.miff` est définitif une fois l'app publiée.

### Étape 4. Créer la fiche de l'app dans App Store Connect
https://appstoreconnect.apple.com > **Apps** > **+** > **Nouvelle app** :
- Plateformes : iOS. Langue principale : français.
- Nom : celui qui s'affichera sur le store. « Miff » est peut-être déjà pris ; si Apple le refuse, mettez un nom provisoire (par exemple « Miff - test »), il sera modifiable. Notez le résultat.
- Identifiant de bundle : choisir `com.tomessa.miff` dans la liste (il n'apparaît qu'après l'étape 3).
- SKU : libre, par exemple `miff-ios`. Accès : complet.

### Étape 5. Créer la clé d'API App Store Connect
1. App Store Connect > **Utilisateurs et accès** > **Intégrations** > **API App Store Connect** > onglet **Clés d'équipe**. Si la page demande de « demander l'accès » à l'API, faites-le (une seule fois, par le titulaire du compte).
2. **+** > Nom : `Miff CI temporaire`. **Accès : Admin.**
   - **Attention : une clé Admin peut agir sur TOUT le compte Apple, donc aussi sur Ghostrun** (apps, certificats, utilisateurs). Elle est temporaire par conception : voir le plan ci-dessous et la section 8.
   - Pourquoi Admin : pour signer sans Mac, Xcode demande à Apple un certificat de distribution « géré dans le cloud ». La documentation Apple réserve la création de ces certificats au titulaire du compte et aux Admins, et des retours de la communauté indiquent qu'une clé **App Manager** échoue avec « Cloud signing permission error » (**non confirmé par nous**). Une clé Admin est donc très probablement nécessaire pour le premier envoi.
   - **Plan validé par le PM** : (1) créer cette clé Admin « Miff CI temporaire » pour le **premier** envoi ; (2) dès que le certificat de distribution cloud existe (le premier envoi a réussi), **révoquer** cette clé (App Store Connect > Clés d'équipe > Révoquer) ; (3) créer alors une clé **App Manager** et remplacer les secrets pour les envois suivants (**non vérifié** : on ne sait pas encore si App Manager suffit une fois le certificat créé). Cet écart avec le ticket (§7, point 7 : « Developer ou App Manager à confirmer par le spike ») est assumé et à retenir.
3. Cliquez sur **Générer**, puis **Télécharger** le fichier `AuthKey_XXXXXXXXXX.p8`. **Il n'est téléchargeable qu'une seule fois.**
4. Notez : l'**identifiant de clé** (Key ID, 10 caractères, aussi dans le nom du fichier) et l'**Issuer ID** (un long identifiant avec des tirets, affiché en haut de la page des clés).
5. Rangez le `.p8`, le Key ID et l'Issuer ID dans votre gestionnaire de mots de passe. **Ne laissez pas le `.p8` dans le dossier du projet ni dans OneDrive.**

### Étape 6. Encoder la clé pour GitHub
Dans PowerShell, en remplaçant le chemin :

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\chemin\vers\AuthKey_XXXXXXXXXX.p8")) | Set-Clipboard
```

Cela copie un long texte dans le presse-papiers (il n'affiche rien). Collez-le tel quel à l'étape suivante.

### Étape 7. Créer les 4 secrets GitHub
Dépôt GitHub > **Settings** > **Secrets and variables** > **Actions** > **New repository secret** (un secret par ligne, noms exacts) :

| Nom du secret | Valeur |
|---|---|
| `ASC_KEY_ID` | Key ID de l'étape 5 |
| `ASC_ISSUER_ID` | Issuer ID de l'étape 5 |
| `ASC_KEY_P8_B64` | Le texte copié à l'étape 6 |
| `APPLE_TEAM_ID` | Team ID de l'étape 2 |

Un secret GitHub ne peut plus être relu après création, seulement remplacé : c'est normal.

### Étape 8. TestFlight sur l'iPhone
1. Installez l'app **TestFlight** (App Store) sur l'iPhone et connectez-vous avec le même identifiant Apple que celui d'App Store Connect.
2. App Store Connect > votre app > **TestFlight** > **Test interne** > **+** pour créer un groupe (par exemple « Équipe »), et ajoutez-vous comme testeur (vous êtes déjà utilisateur App Store Connect). Un testeur interne n'a pas besoin de revue Apple.
3. **Pour qu'un build apparaisse dans l'app TestFlight de l'iPhone, il faut l'ajouter au groupe de test interne** (App Store Connect > TestFlight > le build > Groupes > ajouter le groupe), **ou activer la « distribution automatique »** dans les réglages du groupe (les nouveaux builds lui sont alors donnés tout seuls). Sans cela, le build existe dans App Store Connect mais n'est pas proposé sur le téléphone.

### Étape 9. Lancer le workflow
Voir la section 3.

### Étape 10. Conformité au chiffrement (plus d'action manuelle)
**Décision du PM du 2026-10-22** : l'app n'utilise que le chiffrement standard (HTTPS fourni par iOS), qui est exempté. La clé `ITSAppUsesNonExemptEncryption = false` est donc **posée dans l'app** (`ios/project.yml`) et vérifiée par un test et par le workflow : App Store Connect ne pose plus la question « Informations de conformité manquantes » à chaque build. Si l'app ajoute un jour un chiffrement qui lui est propre, cette décision devra être revue.

---

## 3. Lancer les workflows

### Le workflow `ios` (build et tests, sans signature)
Il se lance **tout seul** sur une pull request qui modifie `ios/**` ou `.github/workflows/ios.yml`. Vous pouvez aussi le lancer à la main : onglet **Actions** > **ios** > **Run workflow**.

### Le workflow `ios-testflight` (archive signée et envoi)
**Manuel uniquement** : onglet **Actions** > **ios-testflight** > **Run workflow** > choisir la branche > **Run workflow**. Deux options :
- `archive_mode = unsigned` (**défaut**) : archive non signée, la signature se fait à l'export. Choisi par défaut parce qu'il évite de créer un certificat de développement orphelin à chaque essai (section 10). **Son fonctionnement n'est pas confirmé** (nous n'avons pas pu le tester sans Mac) ; s'il échoue, l'échec arrive vite, à l'export.
- `archive_mode = signed` : signature automatique dès l'archive. Plan de repli si `unsigned` échoue ; c'est lui qui peut créer des certificats de développement (section 10) et provoquer l'erreur « no devices » (section 6).

Avant l'archive, le workflow vérifie que l'identifiant d'app est exactement `com.tomessa.miff` (le compte Apple est partagé avec Ghostrun) ; après l'archive, une étape **bloquante** contrôle l'identifiant, la version minimale d'iOS (16.0) et le numéro de build, et annule l'envoi au moindre écart.

**Important** : GitHub n'affiche le bouton « Run workflow » que pour les workflows **présents sur la branche par défaut (`main`)**. Tant que la pull request du spike n'est pas fusionnée, le workflow `ios-testflight` n'apparaît donc pas dans l'onglet Actions. **Le workflow `ios-testflight` ne peut donc pas être lancé avant d'être sur `main`** (déclencheur manuel `workflow_dispatch`, et il ne tourne jamais sur une pull request).

**Décision du PM du 2026-10-22** : le spike iOS est **fusionné AVANT l'essai TestFlight**. C'est une exception assumée à « recette avant merge », car ce workflow est manuel et ne se déclenche jamais tout seul. Le critère I3 (archive signée, envoi, installation sur l'iPhone) est ensuite prouvé **depuis `main`**, avec de petites pull requests de correction si l'essai révèle des erreurs.

Ne relancez pas un job déjà terminé avec « Re-run » : le numéro de build serait identique et TestFlight le refuserait (« redundant binary »). Faites toujours un nouveau « Run workflow ».

### Numéro de build
Le numéro de build est le **numéro d'exécution du workflow** `ios-testflight` (visible dans l'URL et le titre du run, p. ex. `#3`). Il augmente à chaque lancement, ce qui est exigé par Apple. La version affichée reste `0.1.0` (réglage `MARKETING_VERSION`).

---

## 4. Lire les résultats

- **Page du run** (onglet Actions > cliquer le run) : la section **Summary** affiche un tableau avec la **durée de chaque étape**, la version d'Xcode utilisée et la durée totale. C'est ce qu'il faut recopier dans le tableau de la section 7.
- **Coche verte / croix rouge** : la croix indique l'étape en échec ; cliquez dessus pour voir le message. Les messages d'erreur d'Apple sont en anglais ; la section 6 les traduit.
- **En cas d'échec**, un **artefact** (téléchargeable en bas de la page du run, conservé 3 jours) contient les journaux complets (`.log`) et, pour TestFlight, les journaux de distribution d'Apple (`.xcdistributionlogs`). Le workflow **remplace le Team ID, le Key ID et l'Issuer ID** par « [identifiant masqué] » avant dépôt, et ne dépose pas le dossier d'export. Ils **ne devraient pas** contenir le contenu de la clé `.p8`, mais **les `.xcdistributionlogs` n'ont jamais été relus par un humain** : vérifiez-les avant de les partager, et ne les publiez pas.
- **Côté Apple** : App Store Connect > votre app > **TestFlight** > **iOS**. Le build apparaît après quelques minutes de traitement (statut « Traitement », puis disponible).
- **Côté iPhone** : app TestFlight > Miff > Installer.

---

## 5. Checklist de validation du spike (à cocher par le PM)

- [ ] Le workflow `ios` est vert sur la pull request (Xcode 26.2 ou plus, SwiftLint et tests passés).
- [ ] Le workflow `ios-testflight` est vert ; le résumé indique la durée de chaque étape.
- [ ] Le build apparaît dans App Store Connect > TestFlight, avec le bon numéro de build.
- [ ] Le build s'installe sur l'iPhone 11 Pro depuis TestFlight.
- [ ] L'icône (carré bleu, M blanc, cœur jaune-vert) s'affiche sur l'écran d'accueil.
- [ ] Le nom affiché sous l'icône est **« Miff »**.
- [ ] L'app s'ouvre sans plantage et affiche « Miff », « Hello Miff » et « Version 0.1.0 · build N » (N = numéro du run).
- [ ] L'app ne demande aucune permission au premier lancement.
- [ ] GitHub > Settings > Billing : aucune dépense enregistrée.
- [ ] Les durées des deux workflows sont reportées dans la section 7.
- [ ] Le fichier `.p8` n'est ni dans le dépôt, ni dans OneDrive (recherche rapide dans le dossier).
- [ ] La clé Admin « Miff CI temporaire » est **révoquée** après le premier envoi réussi (puis clé App Manager pour la suite, non vérifié).
- [ ] developer.apple.com > Certificates : aucun certificat inconnu ajouté ; ceux « Created via API » du jour ont été relevés (section 10).

Correspondance avec les critères du ticket : I1, I2 (workflow `ios`), I3 (workflow `ios-testflight` + installation), I4, I10 et P1 (icône, nom, installation).

---

## 6. Erreurs les plus probables et remèdes

Classées de la plus à la moins probable pour un premier essai.

| Message (ou symptôme) | Cause probable | Remède |
|---|---|---|
| `Secrets GitHub manquants : ...` ou `APPLE_TEAM_ID doit contenir exactement 10 caractères` | Secret absent, mal nommé ou mal copié | Étape 7 : noms exacts, Team ID sans espace |
| `Le secret ASC_KEY_P8_B64 ne contient pas une clé .p8 valide` | Mauvais fichier encodé, ou texte tronqué au copier-coller | Refaire l'étape 6 avec le fichier `AuthKey_XXXX.p8` d'origine et recoller |
| `Cloud signing permission error` ou `You do not have permission to create ... certificates` (à vérifier : formulation exacte) | Rôle de la clé trop faible pour créer un certificat de distribution géré dans le cloud | Créer une nouvelle clé avec l'accès **Admin** (étape 5 : c'est le plan prévu pour le premier envoi), remplacer `ASC_KEY_ID` et `ASC_KEY_P8_B64`, révoquer l'ancienne |
| `Your team has no devices from which to generate a provisioning profile` | (Mode `signed`) La signature automatique de l'archive crée un profil de développement, qui exige au moins un appareil enregistré chez Apple | Soit relancer avec `archive_mode = unsigned` (défaut ; non garanti), soit enregistrer l'iPhone : developer.apple.com > **Devices** > **+** (identifiant UDID de l'iPhone ; à vérifier : sous Windows, iTunes l'affiche quand on clique sur le numéro de série de l'appareil) |
| `PRODUCT_BUNDLE_IDENTIFIER vaut ... au lieu de ...` ou `Identifiant de l'archive ...` (garde-fous du workflow) | L'identifiant d'app du projet n'est pas `com.tomessa.miff` | Rien n'a été envoyé : transmettre au développeur. Ne jamais contourner ce contrôle (compte partagé avec Ghostrun) |
| `Authentication failed`, `401`, `NOT_AUTHORIZED`, `Provisioning profile ... not found` avec la clé | Key ID ou Issuer ID erroné, clé révoquée, mauvaise équipe, API non activée | Comparer avec la page des clés d'App Store Connect (étape 5), recréer les secrets |
| `No profiles for 'com.tomessa.miff' were found` ou `Failed to register bundle identifier` | L'App ID n'existe pas ou n'est pas dans la bonne équipe | Étape 3 ; vérifier `APPLE_TEAM_ID` |
| `Unable to process application`, `app record not found`, `No suitable application records were found` à l'envoi | La fiche app n'existe pas, ou son identifiant de bundle diffère | Étape 4 ; l'identifiant doit être exactement `com.tomessa.miff` |
| `The bundle version must be higher than the previously uploaded version` / `redundant binary` | Numéro de build déjà envoyé (relance via « Re-run ») | Lancer un **nouveau** « Run workflow » |
| `Required agreement is missing or has expired` / `FORBIDDEN` | Accord Apple en attente | Étape 1 : accepter l'accord dans App Store Connect |
| Message sur la clé `testFlightInternalTestingOnly` (clé inconnue ou refusée) | Clé d'export non supportée par cette version d'Xcode (à vérifier) | Retirer cette clé de `ios/ci/ExportOptions.plist.template` ; le build reste réservé aux testeurs internes tant qu'on ne le soumet pas |
| `Invalid Pre-Release Train` ou version refusée | Version déjà « fermée » dans App Store Connect | Changer `MARKETING_VERSION` dans `ios/project.yml` |
| Avertissements ITMS-9xxxx (manifeste de confidentialité, icône) par e-mail Apple | Contrôle automatique d'Apple après l'envoi | Transmettre le texte au développeur : le manifeste actuel déclare « aucune collecte » |
| `shasum: ... FAILED` pendant l'installation des outils | Fichier téléchargé différent de celui attendu | **Ne pas contourner la vérification** ; signaler au développeur (mise à jour de version ou fichier altéré) |
| Erreur XcodeGen (`Spec validation error`, `Could not find ...`) | Erreur dans `ios/project.yml` (non testé sans Mac) | Transmettre le message au développeur |
| Violations SwiftLint | Règle de style | Transmettre la liste au développeur |
| Échec de `testMinimumOSVersionIsIOS16`, `testAppIsIPhoneOnly` ou `testEncryptionExemptionIsDeclared` | Réglage du projet modifié ou hypothèse sur l'Info.plist du build | Transmettre au développeur |
| Le job n'a pas démarré (« quota », « spending limit ») | Minutes gratuites épuisées | Section 7 ; ne pas ajouter de moyen de paiement sans décision |
| Le workflow `ios-testflight` n'apparaît pas dans Actions | Il n'est pas encore sur `main` | Section 3 |

---

## 7. Minutes macOS gratuites de GitHub : mesurer et surveiller

### Ce qui est confirmé (documentation GitHub, lue le 2026-10-21)
- Compte **GitHub Free**, dépôt **privé** : **2 000 minutes par mois**, 500 Mo d'artefacts, 10 Go de cache par dépôt.
- Sans moyen de paiement enregistré, l'usage est **bloqué** une fois le quota épuisé (pas de facture surprise).
- Tarifs de référence au-delà du quota : macOS 0,062 $ la minute, Linux 2 cœurs 0,006 $ la minute, soit environ **10 fois** plus pour macOS.
- Le runner `macos-26` (processeur Apple) est un runner standard ; les « larger runners » (`macos-26-xlarge`...) sont toujours payants et **ne sont pas utilisés**.

### Ce qui est à vérifier
- La page de facturation actuelle exprime le quota en **dollars de dépense** et ne donne plus explicitement le « multiplicateur macOS » (historiquement **x10**). Hypothèse de travail : **une minute macOS consomme environ 10 minutes du quota**, soit environ **200 minutes macOS par mois**. **À vérifier** en lisant la consommation après la première exécution.
- Durées réelles mesurées (à compléter à chaque nouveau run ; le détail par étape est dans le Summary de chaque run) :

| Run | Workflow | Durée totale | Facturé | Étape la plus longue |
|---|---|---|---|---|
| 39659012614 (PR #1) | `ios` | 1 min 40 | environ 2 min | non relevée (voir le Summary du run) |
| (pas encore lancé) | `ios-testflight` | à mesurer | à mesurer | à mesurer |

Chaque job est facturé à la minute entamée ; une exécution de N minutes consomme donc environ 10 x N minutes de quota si l'hypothèse x10 se confirme. Avec la mesure réelle du workflow `ios` (environ 2 minutes facturées, donc environ 20 minutes de quota si x10), une centaine d'exécutions par mois tiendraient dans le quota ; l'archive et l'envoi TestFlight seront plus longs (à mesurer).

### Comment mesurer
1. Page du run : le **Summary** indique la durée de chaque étape et la durée totale.
2. GitHub > votre photo > **Settings** > **Billing and licensing** > **Usage** (le libellé exact peut varier) : filtrer sur **Actions** et regarder la ligne macOS (`actions_macos`) et le quota restant. Une alerte par e-mail est envoyée à 90 % et 100 % du quota.

### Signal de bascule (règle du projet : documenter le choix économique)
- **Seuil d'alerte** : plus de 70 % du quota mensuel consommé avant le 20 du mois, ou plus de 3 relances d'une même exécution pour cause d'erreur de configuration.
- **Alternatives si le quota devient un frein** (décision PM, question Q4 du ticket) : (B) moyen de paiement avec plafond de dépense, (C) Xcode Cloud (25 heures par mois incluses dans le programme développeur ; sa configuration initiale semble exiger un Mac, à vérifier), (D) GitHub Pro (3 000 minutes, environnements protégés). **Ne pas rendre le dépôt public pour contourner le quota** : cela exposerait les documents produit et les identifiants.
- Mesures d'économie déjà en place : filtres de chemins stricts, annulation des exécutions obsolètes, aucun build iOS sur un changement qui ne touche pas `ios/`, archive et envoi TestFlight uniquement sur demande, outils téléchargés en versions épinglées (pas de mise à jour d'outils par `brew`, qui est plus lent).

---

## 8. Sécurité (ce que vous devez savoir)

- **La clé d'API est puissante, et la clé Admin du premier envoi l'est encore plus.** Une clé d'équipe n'est pas limitée à l'app Miff : elle voit aussi **Ghostrun**, qui est sur le même compte. Une clé **Admin** peut gérer certificats, identifiants, utilisateurs et apps de **tout le compte, Ghostrun compris**. Précautions : ne jamais la sortir de votre gestionnaire de mots de passe sauf pour l'étape 6 ; **la révoquer dès que le premier envoi a réussi** (plan de l'étape 5, puis clé App Manager pour la suite, non vérifié) ; la révoquer immédiatement au moindre doute (App Store Connect > Clés d'équipe > Révoquer) ; ne jamais coller un secret dans une conversation. Le workflow contrôle que l'app est bien `com.tomessa.miff` avant toute signature, pour qu'une erreur de configuration ne touche pas Ghostrun.
- **Décision du PM du 2026-10-24 (option B) : la clé Admin reste en place dans les secrets GitHub, risque assumé.** Constat qui l'a motivée : l'envoi TestFlight du 2026-10-24 avec une clé gestionnaire d'apps a échoué à l'export (`Cloud signing permission error`, `No signing certificate "iOS Distribution" found`) ; la signature gérée dans le cloud exige l'accès Admin. Conséquence à connaître : le secret `ASC_KEY_P8_B64` donne accès à tout le compte Apple, **Ghostrun compris**. Mitigations conservées : workflow manuel uniquement, jamais déclenché par une PR ; aucune action tierce n'a accès au secret ; le script de build Crashlytics ne s'exécute pas avec la clé ; contrôle du bundle ID `com.tomessa.miff` avant toute signature ; un seul contributeur humain. Hygiène recommandée : renouveler la clé tous les 3 mois, la révoquer immédiatement au moindre doute (App Store Connect > Clés d'équipe), ne jamais l'utiliser ailleurs. Alternative sans clé Admin permanente (signature avec un certificat à nous stocké en secret) : disponible sur simple demande.
- **Le fichier `.p8` ne doit jamais entrer dans git.** `ios/.gitignore` bloque `*.p8` (et `.p12`, `.cer`, `.mobileprovision`). Le `.gitignore` de la racine du dépôt ne le fera qu'après la fusion de la PR INIT-1 : d'ici là, n'enregistrez le fichier dans aucun dossier du projet.
- **Exposition de la clé dans le workflow** : le chemin du `.p8` n'est plus exporté vers toutes les étapes (pas de `GITHUB_ENV`) ; chaque étape qui en a besoin le recalcule, et en mode `unsigned` l'archive n'a pas la clé du tout (elle n'est écrite que pour l'export). Reste que le fichier `.p8` est présent sur le disque du runner pendant l'export : à revoir quand INIT-4 ajoutera des paquets Swift tiers (code tiers résolu et compilé pendant le build) pour que la clé n'existe que pendant l'envoi.
- **Les environnements protégés GitHub ne sont pas disponibles** pour un dépôt privé en offre gratuite (documentation GitHub). Un « environnement » est normalement le bon moyen de limiter qui peut lire les secrets (relecteur obligatoire, branches autorisées). Sans lui, **toute personne ayant le droit d'écrire dans le dépôt peut, en modifiant un workflow sur une branche, lire les secrets**. Acceptable pour un dépôt privé à un seul contributeur humain, à reconsidérer dès qu'il y en a plusieurs. Si vous passez en GitHub Pro : créez l'environnement `testflight`, déplacez-y les 4 secrets, limitez-le aux branches voulues avec un relecteur obligatoire (vous), puis décommentez la ligne `environment: testflight` dans `.github/workflows/ios-testflight.yml` (un commentaire l'indique).
- Dans le workflow : la clé est décodée dans un fichier temporaire du runner (droits restreints, jamais affiché, pas de `set -x`), puis supprimée à la fin même en cas d'échec ; le runner est de toute façon détruit après le job. `permissions` est limité à `contents: read` ; aucune action tierce n'a accès aux secrets (les deux actions utilisées, `checkout` et `upload-artifact`, sont celles de GitHub, épinglées par empreinte de commit) ; jamais de `pull_request_target`.
- **Le Team ID, le Key ID et l'Issuer ID ne sont pas des secrets cryptographiques** (seul le `.p8` l'est), mais ils sont traités comme des secrets par prudence.
- Garde-fou : le projet ne déclare aucune permission, aucune clé de localisation, aucune exception ATS (trafic non sécurisé) ; un test XCTest le vérifie.

---

## 9. Inventaire des outils, actions et licences (tout est gratuit)

| Élément | Version | Licence | Rôle | Vérification |
|---|---|---|---|---|
| XcodeGen | 2.46.0 (2026-07-16) | MIT | Génère le projet Xcode depuis `project.yml` | Licence lue sur l'API GitHub ; empreinte SHA-256 du téléchargement recalculée et identique à celle publiée |
| SwiftLint | 0.65.1 (2026-08-21) | MIT | Contrôle de style Swift | Idem |
| Xcode | 26.6 (défaut du runner `macos-26`, image 20261013) ; 26.2 minimum vérifié par le workflow | Compris dans le runner | Compilation, tests, archive | README des images de runners GitHub |
| xcbeautify | 3.2.1 (préinstallé sur le runner) | MIT | Rend les journaux Xcode lisibles | Version : README des images ; licence : API GitHub |
| `actions/checkout` | v7.0.1 (`3d3c42e5aac5ba805825da76410c181273ba90b1`) | MIT | Récupère le code | Empreinte et licence vérifiées via l'API GitHub |
| `actions/upload-artifact` | v7.0.2 (`cf430e030ddbb5b0abf93d22962f4752f3646cd9`) | MIT | Dépose les journaux en cas d'échec | Idem |
| Runner `macos-26` | arm64, standard | Service GitHub (minutes du quota) | Exécute les jobs | README des runners |
| Script d'icône (Node.js) | Node déjà présent ; modules intégrés uniquement | n/a | Génère l'icône provisoire | Exécuté localement, PNG vérifié (1024 x 1024, RGB sans alpha) |
| Dépendances Swift (SwiftPM) | **Aucune pendant le spike.** Depuis INIT-4 : firebase-ios-sdk 13.1.1 et ses 12 dépendances indirectes | voir `ios/README.md` | voir `ios/README.md` | `ios/Package.resolved` est versionné. Durées de la CI avec Firebase : `ios/CHANGELOG.md` (premier run sans cache : 7 min 36 s) |
| CocoaPods | **Non utilisé** | n/a | n/a | n/a |
| fastlane | **Non utilisé** (préinstallé sur le runner, MIT, gardé comme plan B) | MIT | n/a | Voir la décision de signature ci-dessous |

### Décision de signature et compromis
- **Retenu (plan A)** : signature automatique de Xcode pilotée par la clé d'API App Store Connect (`-allowProvisioningUpdates -authenticationKeyPath/ID/IssuerID`), avec export `destination: upload` (l'envoi à TestFlight est fait par `xcodebuild -exportArchive` lui-même). Le workflow essaie d'abord le mode `unsigned` (signature à l'export seulement), voir la section 3 et la section 10 (certificats orphelins). Avantages : aucun certificat ni profil stocké dans GitHub, une seule clé révocable, pas d'outil supplémentaire, aucun dépôt de certificats à entretenir. Apple documente ce certificat « géré dans le cloud » pour Xcode 13 et suivants quand aucun certificat local n'est trouvé.
- **Non vérifié sans Mac** : (a) la création du certificat par la clé d'API sur un runner vierge ; (b) le rôle minimal de la clé ; (c) l'exigence d'un appareil enregistré pour le profil de développement de l'archive. Le spike sert précisément à les trancher.
- **Plans de repli** : (1) `archive_mode = signed` (si le mode `unsigned`, défaut, échoue) ; (2) **fastlane** (gratuit, MIT, déjà installé sur le runner) avec `cert` ou `match`. Compromis de fastlane : `match` demande un second dépôt privé pour stocker des certificats chiffrés, et `cert` crée de **vrais** certificats de distribution, qui sont **en nombre limité par compte** Apple (compte partagé avec Ghostrun : risque de collision, **ne jamais révoquer les certificats de Ghostrun**). C'est pourquoi le certificat géré dans le cloud est préféré.

---

## 10. Risques du ticket (R1 à R3) vus depuis le spike

| # | Risque | Où en est-on |
|---|---|---|
| R1 | iOS sans Mac : boucle lente, signature sans Xcode, `.xcodeproj` non éditable | Le projet est décrit en texte (XcodeGen). Chaque essai coûte un aller-retour avec la CI (quelques minutes à dizaines de minutes) : **grouper les corrections** et lire les journaux complets avant de relancer. Reste à prouver : signature et envoi (ce spike). |
| R2 | Quota de minutes macOS | Mesure intégrée (Summary de chaque run), garde-fous en section 7, multiplicateur **à vérifier**. Mesuré : `ios` = 1 min 40 (environ 2 min facturées) ; l'archive et l'envoi TestFlight restent à mesurer. |
| R3 | Xcode Cloud : configuration initiale via Xcode (donc un Mac) | Non utilisé ni testé ici. Reste une option de secours (section 7), à confirmer avant de s'y engager. |

### Risque : certificats « orphelins » créés à chaque essai (non vérifié, à surveiller)

Avec `-allowProvisioningUpdates`, Xcode peut créer, **à chaque exécution**, un certificat de **développement** (nom du type « Created via API », ou « Apple Development: Created via API ») dans votre compte Apple. Sa clé privée reste sur le runner, qui est détruit à la fin du job : le certificat reste chez Apple, inutilisable par personne, et s'accumule. Apple limite le nombre de certificats par compte, et **ce compte est partagé avec Ghostrun** : une accumulation pourrait gêner Ghostrun. Nous n'avons pas pu confirmer ce comportement sans Mac.

Consignes :
1. **Essayer d'abord `archive_mode = unsigned`** (c'est le défaut du workflow) : l'archive n'est pas signée, donc aucun certificat de développement n'est nécessaire ; seul le certificat de distribution « géré dans le cloud » (sans clé privée à conserver, géré par Apple) intervient à l'export. Support **non confirmé** : si l'export échoue, l'échec est rapide, et le mode `signed` reste disponible.
2. **Regarder developer.apple.com > Certificats, Identifiants et profils > Certificates avant et après chaque essai**, et noter le nombre et les dates.
3. **Ne révoquer que ce qui est daté du jour de l'essai et nommé « Created via API ».**
4. **Ne JAMAIS révoquer un certificat que vous ne reconnaissez pas** : il peut appartenir à Ghostrun (compte partagé), et sa révocation casserait la signature de cette app.
5. **Plan B si les certificats s'accumulent** : créer **un** certificat de distribution dédié à Miff, l'exporter au format `.p12` (avec un mot de passe fort), et le stocker comme secret GitHub, avec le profil correspondant. C'est plus d'entretien (renouvellement annuel) et un secret de plus : à n'engager qu'en cas de besoin.

### Plan B d'envoi (documenté, non automatisé, **non testé**)

Si l'export avec `destination: upload` échoue alors que l'archive et la signature réussissent, on peut séparer les deux gestes : (1) passer `destination` à `export` dans `ExportOptions.plist` pour obtenir un fichier `.ipa` signé dans `build/export` ; (2) l'envoyer avec l'outil d'Apple `xcrun altool --upload-app --file <fichier.ipa> --type ios --apiKey <KEY_ID> --apiIssuer <ISSUER_ID>` (la clé `.p8` est cherchée dans un dossier `private_keys` du répertoire courant, de `~/` ou de `~/.appstoreconnect`, nommée `AuthKey_<KEY_ID>.p8`). La syntaxe exacte et la disponibilité d'`altool` dans Xcode 26 sont **à vérifier** ; rien de cela n'est dans le workflow aujourd'hui.

Autres limites connues du spike : aucun test sur iOS 16 réel (les simulateurs du runner sont en iOS 26.x ; le test `MinimumOSVersion`, vert, vérifie seulement la cible déclarée) ; iPhone uniquement (`TARGETED_DEVICE_FAMILY = 1` posé au niveau de la cible : XcodeGen applique sinon « 1,2 » ; vérifié par le test `UIDeviceFamily` et par le workflow ; l'ouverture à l'iPad reste une décision produit à confirmer) ; orientation portrait uniquement ; couleurs et textes en dur (remplacés par les tokens du design system et le String Catalog dans INIT-4).

---

## 11. Pour le développeur : où est quoi

- `ios/project.yml` : description du projet (configurations `Debug`, `Internal`, `Release`).
- `ios/Miff/` : code (`MiffApp`, `Views/HelloView`, `Models/BuildInfo`) et ressources (icône, manifeste de confidentialité).
- `ios/MiffTests/` : tests XCTest.
- `ios/tools/` : générateurs de données vectorielles et de l'image de l'écran de lancement (l'icône est désormais l'icône officielle de `design/assets/logo/ios`, l'ancien générateur d'icône provisoire a été retiré).
- `ios/ci/` : `timed.sh` (durées dans le résumé) et `ExportOptions.plist.template`.
- `.github/workflows/ios.yml`, `.github/workflows/ios-testflight.yml`.
