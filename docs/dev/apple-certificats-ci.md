# Certificats Apple et signature de la CI iOS : solution durable et gratuite

**Rédigé le** 2026-11-08 par `ios-dev`. **Statut : proposition, rien n'est modifié** (ni `ios-testflight.yml`, ni `project.yml`, ni secrets). Décision du PM attendue.
**Contexte** : le run `42429656978` (build 20, `archive_mode=signed`) échoue à l'archive avec `Choose a certificate to revoke. Your account has reached the maximum number of certificates` puis `No profiles for 'com.tomessa.miff' were found: ... iOS App Development provisioning profiles`. `archive_mode=unsigned` échoue à l'export (`Frameworks/*.framework is not properly signed`, 4 runs le 2026-11-06). Le compte Apple est **partagé avec Ghostrun**.
**Convention** : [Constaté] = lu dans les journaux de la CI ; [Connu] = connaissance générale d'Apple/Xcode, **non revérifiée aujourd'hui** (l'outil de recherche web n'était pas disponible pour cette analyse) ; [À vérifier] = à constater sur developer.apple.com ou au premier run.

## 1. Cause racine

- [Constaté] L'échec est dans **l'archive**, pas dans l'export : Xcode, en signature automatique avec `-allowProvisioningUpdates`, signe l'archive avec un certificat **« Apple Development »** et un profil **de développement** (`iOS App Development provisioning profiles`).
- [Connu] Un certificat de développement exige sa **clé privée** sur la machine qui signe. Le runner GitHub est neuf à chaque run : sa clé disparaît avec lui. Xcode crée donc **un nouveau certificat de développement « Created via API » à chaque run**, qui reste chez Apple, inutilisable (c'est le risque « orphelins » de `docs/dev/ios-spike.md` §10, désormais réalisé). Apple limite le nombre de certificats de développement par équipe/personne : la limite est atteinte, Xcode demande lequel révoquer, et la CI ne peut pas répondre.
- [Connu] Un profil de développement exige aussi au moins un appareil enregistré : un second point de fragilité de ce mode.
- Le certificat de **distribution** n'est pas en cause : avec la clé d'API, Apple le gère « dans le cloud » (sans clé privée sur le runner). C'est pourquoi il ne pose pas de problème tant que l'archive n'a pas besoin de signer elle-même.
- Pourquoi `unsigned` échoue : l'archive non signée contient des frameworks binaires dynamiques (Firebase, gRPC, abseil, openssl_grpc) ; l'export signé par le cloud ne les re-signe pas. [Constaté] « not properly signed » sur les 7 frameworks.

**Conclusion** : il ne faut plus jamais de certificat de **développement** dans la CI. Le TestFlight n'a besoin que d'une archive signée **directement en distribution**.

## 2. Les trois options

### (a) Signature manuelle avec UN certificat « Apple Distribution » persistant, dédié à Miff  *(recommandée)*
Un certificat de distribution créé une fois par le PM, exporté en `.p12`, et un profil « App Store » téléchargé : tous deux stockés comme secrets GitHub. La CI les installe dans un trousseau temporaire, archive **et** exporte en signature manuelle : aucun certificat créé par run, aucun appareil requis, frameworks signés par la même identité.
- **Coût** : 0 €.
- **Avantages** : déterministe, un seul certificat consommé (jamais d'accumulation), pas de dépendance à la clé d'API pour signer (elle ne sert plus qu'à l'envoi), c'est la méthode standard des CI iOS.
- **Risques / entretien** : 3 secrets de plus ; le certificat et le profil expirent (1 an [Connu]) : rappel de renouvellement ; le profil doit être régénéré quand une capacité change (notifications APNs plus tard, etc.) ; le `.p12` est un secret sensible (voir §4) ; la création se fait sans Mac (openssl, §4) : un point de friction mais documenté.
- **Complexité** : moyenne (workflow : un pas d'installation + deux commandes modifiées) ; non testable sans Mac, donc 1 à 2 allers-retours CI attendus.

### (b) `unsigned` + re-signature des frameworks avant export
Signer à la main chaque `.framework` de l'archive (`codesign`) puis exporter.
- `codesign` exige une **identité avec clé privée** : avec un certificat de distribution « géré dans le cloud » (sans clé privée) c'est impossible. Il faut donc de toute façon le certificat et le `.p12` de l'option (a), **plus** un script de re-signature fragile (ordre des signatures, entitlements, bundles imbriqués), à refaire à chaque nouveau framework.
- **Non retenue** : tous les inconvénients de (a) plus de la fragilité.

### (c) fastlane `match` (gratuit, dépôt privé chiffré)
`match` génère le certificat et le profil, les chiffre et les range dans un dépôt Git privé ; la CI les récupère.
- **Coût** : 0 € (dépôt privé GitHub gratuit, fastlane déjà sur le runner), mais **un second dépôt**, une phrase secrète, un jeton d'accès à ce dépôt (3 secrets aussi), et un outil de plus à comprendre.
- **Risque propre** [Connu] : si `match` ne peut pas déchiffrer ou ne trouve pas le certificat, il **en crée un nouveau** (consommation de la limite sur un compte partagé) ; sa commande `nuke` **révoque** : à proscrire ici (Ghostrun).
- **Complexité** : la plus haute pour un seul app et une seule personne ; l'automatisation du renouvellement ne vaut pas ce coût aujourd'hui.
- **Non retenue** (à reconsidérer si l'équipe grandit).

### Comparatif

| | (a) manuelle + .p12 | (b) re-signature | (c) match |
|---|---|---|---|
| Certificat créé à chaque run | non | non | non (si bien configuré) |
| Certificat de développement | aucun | aucun | aucun |
| Appareil enregistré requis | non | non | non |
| Secrets à créer | 3 | 3 | 3 + un dépôt |
| Fragilité | faible | haute | moyenne |
| Entretien | renouvellement annuel | idem + script | renouvellement automatisable |
| Risque pour Ghostrun | nul (certificat distinct) | nul | création possible d'un certificat de trop |

## 3. Recommandation : option (a)

Raisons : la plus simple qui supprime la cause racine, gratuite, sans outil supplémentaire, sans risque pour Ghostrun si le certificat est distinct, et la plus documentée côté Apple/Xcode.

## 4. Ce que le PM doit faire (developer.apple.com, aucun Mac requis)

Les fichiers `.key`, `.p12` et `.mobileprovision` restent **sur ton poste** (hors dépôt, hors conversation avec l'assistant) ; ils partent directement vers les secrets GitHub avec `gh secret set ... < fichier`. Rien n'est jamais collé dans une conversation ni commité.

**Étape 0. Regarder ce qui existe déjà** (developer.apple.com > Certificates, Identifiers & Profiles > Certificates). Noter, pour chaque ligne, le type, le nom, la date de création et le créateur.
- **Ne révoquer AUCUN certificat que tu ne reconnais pas** : « Apple Distribution » ou « iOS Distribution » peuvent appartenir à Ghostrun. Sa révocation casserait la signature de Ghostrun.
- Le certificat **« Apple Distribution » de Ghostrun, s'il existe, ne doit pas être réutilisé** pour Miff sans décision explicite (même impact en cas de fuite, et sa clé privée est chez Ghostrun). On en crée un distinct.
- [À vérifier] Apple limite le nombre de certificats de distribution par équipe (de l'ordre de 3 [Connu], non revérifié) : si la liste est déjà pleine de certificats de distribution reconnus comme utilisés, **s'arrêter et me le dire** : on choisira alors avec toi (réutiliser celui de Ghostrun, ou libérer un certificat **inutilisé** que tu reconnais).

**Étape 1. Débloquer la CI tout de suite (optionnel, indépendant)** : révoquer uniquement les certificats **« Apple Development: Created via API »** (ou nommés ainsi) **datés des jours d'essai de la CI** (2026-11-05 à aujourd'hui), créés par la clé d'API de la CI. Leur clé privée est perdue : ils ne servent à personne. Ne pas toucher aux « Apple Development » à ton nom ni à ceux de Ghostrun. Ce n'est qu'un dépannage : sans l'option (a) la limite sera de nouveau atteinte.

**Étape 2. Créer la demande de certificat (CSR) sur ton poste Windows** (Git Bash, openssl est fourni avec Git) :
```
openssl req -new -newkey rsa:2048 -nodes -keyout miff-dist.key -out miff-dist.csr -subj "/CN=Miff Distribution/emailAddress=tomessa.mfouka@gmail.com"
```

**Étape 3. Créer le certificat** : Certificates > **+** > **Apple Distribution** > importer `miff-dist.csr` > télécharger `distribution.cer`.

**Étape 4. Fabriquer le `.p12`** (mot de passe fort, à ranger dans ton gestionnaire de mots de passe) :
```
openssl x509 -inform DER -in distribution.cer -out distribution.pem
openssl pkcs12 -export -inkey miff-dist.key -in distribution.pem -out miff-dist.p12 -name "Miff Distribution"
```
[À vérifier] Si le runner refuse le `.p12` (« MAC verification failed »), refaire la commande avec `-legacy` (OpenSSL 3 et l'outil `security` de macOS ne s'entendent pas toujours).

**Étape 5. Préparer l'identifiant d'app** (Identifiers > `com.tomessa.miff`) : vérifier que les capacités **« Sign In with Apple »** et **« App Attest »** sont cochées (ce sont les deux droits de `ios/Config/Miff.entitlements`). Avec la signature manuelle, Xcode ne les active plus tout seul.

**Étape 6. Créer le profil** : Profiles > **+** > **App Store Connect** (distribution) > App ID `com.tomessa.miff` > le certificat créé à l'étape 3 > nom exact **`Miff CI App Store`** > télécharger `Miff_CI_App_Store.mobileprovision`.

**Étape 7. Créer les 3 secrets GitHub** (depuis le dossier des fichiers ; PowerShell, sans rien afficher) :
```
gh secret set DIST_CERT_P12_B64 --repo Tomessa-Ghostrun/Miff --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes("miff-dist.p12")))
gh secret set DIST_CERT_P12_PASSWORD --repo Tomessa-Ghostrun/Miff        # il demande le mot de passe en saisie masquée
gh secret set APPSTORE_PROFILE_B64 --repo Tomessa-Ghostrun/Miff --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes("Miff_CI_App_Store.mobileprovision")))
```
Puis **supprimer** `miff-dist.key`, `miff-dist.csr`, `distribution.cer`, `distribution.pem` ; garder `miff-dist.p12` + son mot de passe dans le gestionnaire de mots de passe (sauvegarde : sans la clé, il faudrait recréer un certificat).

**Rappels** : certificat et profil expirent dans l'année [Connu] (date à mettre à l'agenda, un mois avant) ; à chaque nouvelle capacité (notifications APNs, etc.) : cocher la capacité sur l'identifiant, **régénérer le profil**, remplacer `APPSTORE_PROFILE_B64`.

## 5. Modifications à faire ensuite (par moi, dans une PR séparée, après ton accord)

**`.github/workflows/ios-testflight.yml`**
1. Vérifier la présence des 3 nouveaux secrets (comme pour les 4 existants, sans les afficher).
2. Nouveau pas « Installer le certificat et le profil » : trousseau temporaire (existant « Préparer un trousseau temporaire »), `security import` du `.p12` avec `-T /usr/bin/codesign`, `security set-key-partition-list`, copie du profil dans `~/Library/MobileDevice/Provisioning Profiles/<UUID>.mobileprovision` (UUID et nom lus avec `security cms -D`), **contrôle bloquant** : le nom du profil est `Miff CI App Store`, l'identifiant d'app est `<TEAM>.com.tomessa.miff`, le profil n'est pas expiré et contient le certificat importé.
3. Pas d'archive unique en distribution : `xcodebuild archive` **sans** `-allowProvisioningUpdates` ni clé d'API, avec la signature manuelle (voir `project.yml`) ; supprimer les modes `signed` / `unsigned` (entrée `archive_mode` retirée, un seul chemin).
4. Export : `ExportOptions.plist` avec `signingStyle` = `manual`, `signingCertificate` = `Apple Distribution`, `provisioningProfiles` = `{ com.tomessa.miff : Miff CI App Store }`, `destination` = `upload`. La clé d'API App Store Connect ne sert plus qu'à **l'envoi** (`-authenticationKeyPath/ID/IssuerID` sur `-exportArchive`).
5. Nettoyage : supprimer le trousseau et les fichiers déchiffrés (déjà en place pour la clé `.p8`), ajouter le profil copié.
6. Le contrôle « Identité de l'archive » vérifie en plus `SigningIdentity` = Apple Distribution (aujourd'hui informatif) et les entitlements **maintenant lisibles** (App Attest `production`, Sign in with Apple).

**`ios/project.yml`** : `CODE_SIGN_STYLE: Automatic` reste pour Debug et le simulateur. Pour la cible `Miff`, configuration `Internal` seulement, réglages propres à l'appareil : `CODE_SIGN_STYLE: Manual`, `CODE_SIGN_IDENTITY[sdk=iphoneos*]: Apple Distribution`, `PROVISIONING_PROFILE_SPECIFIER[sdk=iphoneos*]: Miff CI App Store`. Ne **pas** passer le profil en ligne de commande : il s'appliquerait aussi aux paquets Swift (Firebase, etc.), qui ne supportent pas les profils (erreur classique). `DEVELOPMENT_TEAM` reste fourni par la CI. La configuration `Release` (futur build public) recevra les mêmes réglages le moment venu.

**`ios/ci/ExportOptions.plist.template`** : passe en signature manuelle (point 4).
**`docs/dev/ios-spike.md`** : §9 (« Décision de signature »), §10 (certificats orphelins : résolu) et §6 (erreurs) mis à jour.

## 6. Risques de la solution et parades

| Risque | Parade |
|---|---|
| Fuite du `.p12` ou de son mot de passe | Secrets GitHub (masqués dans les journaux, jamais dans le dépôt) ; gitleaks en CI ; en cas de doute : révoquer **ce** certificat (le nôtre, nommé « Miff Distribution »), en recréer un |
| Profil sans la bonne capacité (échec à l'archive : droit non autorisé par le profil) | Étape 5 ; message d'erreur clair ; régénérer le profil |
| `.p12` refusé par macOS | `-legacy` (étape 4) |
| Expiration silencieuse | Rappel d'agenda ; le contrôle de la CI lit la date d'expiration du profil et **échoue avec un message explicite** (et un avertissement 30 jours avant) |
| Premier run en échec pour une raison de signature manuelle (non testable sans Mac) | Prévoir 1 à 2 essais ; `ios-testflight` est manuel, ne tourne jamais sur une PR ; le diagnostic reste dans l'artefact de journaux assainis |
| Collision avec Ghostrun | Certificat et profil **distincts**, noms préfixés « Miff » ; aucune révocation d'un certificat non reconnu ; Ghostrun n'est jamais ciblé |

## 7. Questions pour le PM

1. **Étape 0** : peux-tu me dire (ou noter) combien de certificats « Apple Distribution / iOS Distribution » existent, leurs dates et leur créateur ? Cela conditionne l'étape 3 (limite).
2. OK pour l'option (a) et pour que je prépare la PR du workflow une fois les 3 secrets posés ?
3. Veux-tu dépanner maintenant (étape 1) en révoquant les « Created via API » de la CI ?

*Aucun montant à engager : tout est gratuit (compte développeur Apple déjà existant). Aucun texte légal concerné.*
