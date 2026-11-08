# Certificats Apple et signature de la CI iOS : guide pas à pas (option « signature manuelle »)

**Statut** : **option (a) validée par le PM le 2026-11-08**. Le workflow `ios-testflight` sait maintenant signer manuellement (PR « CI : signature manuelle »). **Il reste au PM à créer le certificat et à poser 3 secrets** (§4). Tant qu'ils sont absents, le workflow garde l'ancien chemin automatique, avec un avertissement clair dans le journal.
**Convention** : [Constaté] = lu dans les journaux de la CI ; [Vérifié] = essayé pour de vrai pendant la rédaction (commandes `openssl` rejouées sous Windows avec le `openssl` fourni avec Git) ; [Connu] = connaissance générale d'Apple/Xcode, **non revérifiée aujourd'hui** (l'outil de recherche web n'était pas disponible : les limites exactes d'Apple sont à constater sur developer.apple.com) ; [À vérifier] = à constater au premier essai.

## 1. Pourquoi (rappel court)

- [Constaté] Run `42429656978` (build 20, signature automatique) : `Choose a certificate to revoke. Your account has reached the maximum number of certificates` puis `No profiles for 'com.tomessa.miff' were found: ... iOS App Development provisioning profiles`. L'archive était signée avec un certificat **de développement** : Xcode en crée un **nouveau à chaque exécution** (« Created via API »), car sa clé privée disparaît avec le runner ; la limite du compte a été atteinte.
- Le mode `unsigned` échoue à l'export (frameworks binaires Firebase / gRPC non signés).
- **Solution** : n'utiliser **aucun** certificat de développement. Un **seul** certificat « Apple Distribution » **persistant**, créé par toi une fois, et un profil « App Store » : la CI les installe à chaque run dans un trousseau temporaire. Coût : 0 €.

## 2. Ce que contiennent tes 18 certificats et ce qu'on en fait

D'après ta liste (le PM a révoqué les 10 certificats « Created via API » de la CI Miff, clé `d1ec3c23…`) :

| Ligne | À faire |
|---|---|
| **1 « Distribution Managed »** (expire 2027/06/27, créé par toi, géré par Apple) | **Ne pas toucher.** C'est le certificat « géré dans le cloud » : Apple garde la clé privée, **aucun `.p12` n'en est exportable**, il ne peut donc pas servir à la signature manuelle. Il sert à l'ancien chemin (export automatique). On l'écarte pour la suite, sans le révoquer. |
| 12 « Created via API » d'**autres clés** | **Ghostrun. Ne JAMAIS les révoquer.** |
| 2 Push (Ghostrun) | Ne pas toucher. |
| 3 Development nominatifs | Ne pas toucher (ce sont les tiens, utiles à Xcode sur un Mac). |
| **Aucun « Apple Distribution » (non géré)** | Bonne nouvelle : **on en crée un seul, nouveau et distinct** (§4). |

**Limite de certificats** [Connu, à vérifier] : Apple limite le nombre de certificats de distribution par équipe (de l'ordre de **3**) et de développement par personne (quelques-uns). Ta liste ne montre **aucun** « Apple Distribution » non géré : en créer **un** reste largement sous une limite de 3, même si le « Distribution Managed » est compté. **Si Apple refuse la création (« maximum number of certificates »)** : **arrête-toi, ne révoque rien**, et dis-moi (copie le message et le nombre de certificats « Distribution » de ta liste) : plan B ci-dessous.

**Plan B si la limite est atteinte** : (1) me dire combien de certificats de distribution existent et leurs dates ; (2) identifier avec toi un certificat **à ton nom, inutilisé et que tu reconnais** (jamais un certificat de Ghostrun) ; (3) à défaut, réutiliser le certificat de distribution de Ghostrun **uniquement sur ta décision explicite** (même `.p12`, mais une fuite toucherait les deux apps).

**Renouvellement** [Connu] : un certificat de distribution et un profil durent **1 an**. Mets dans ton agenda un rappel **1 mois avant** l'expiration. La CI **échoue avec un message clair** si le profil ou le certificat a expiré, et **avertit 30 jours avant** (§5).

## 3. Les règles (à relire avant de commencer)

**Tu ne fais JAMAIS :**
- révoquer un certificat que tu ne reconnais pas, ou de Ghostrun, ou le « Distribution Managed » ;
- coller dans le dépôt, dans une conversation avec l'assistant, dans un e-mail ou un ticket : le fichier `.key`, le `.p12`, le `.p8`, le `.mobileprovision`, **le mot de passe du `.p12`** ou leur version encodée en base64 ;
- mettre ces fichiers dans le dossier du projet (`C:\dev\miff`) : ils vivent dans un dossier à part, hors dépôt ;
- taper le mot de passe dans une commande (il resterait dans l'historique de PowerShell) : les commandes ci-dessous le **demandent** à l'écran, sans l'afficher.

**Tu fais :** ranger le `.p12` **et** son mot de passe dans ton gestionnaire de mots de passe (sans la clé privée, il faudrait recréer un certificat) ; supprimer les fichiers intermédiaires à la fin (étape 9).

## 4. Pas à pas (Windows, PowerShell, aucun Mac)

### Avant de commencer : l'état « avant »
1. Va sur https://developer.apple.com/account > **Certificates, Identifiers & Profiles** > **Certificates**.
2. Note (ou prends une capture) : le **nombre total** de certificats (18) et la liste des types. **Ne change rien.**

### Étape 1. Ouvrir PowerShell dans un dossier hors dépôt
Menu Démarrer > « PowerShell » (Windows PowerShell suffit). Puis, **une ligne à la fois** :
```powershell
mkdir $HOME\miff-signing
cd $HOME\miff-signing
openssl version
```
Attendu : un numéro de version d'OpenSSL (par exemple `OpenSSL 3.x`). [Vérifié] `openssl` est fourni avec Git for Windows et PowerShell le trouve (`C:\Program Files\Git\mingw64\bin\openssl.exe`). **Si PowerShell répond « openssl n'est pas reconnu »**, remplace partout `openssl` par `& "C:\Program Files\Git\mingw64\bin\openssl.exe"`.
> Pourquoi PowerShell et pas Git Bash : Git Bash réécrit certains arguments commençant par `/` (le `-subj "/CN=..."` d'openssl devient un chemin `C:/Program Files/Git/...` et échoue) [Vérifié]. PowerShell ne le fait pas, et le fichier de configuration ci-dessous évite de toute façon `-subj`.

### Étape 2. Créer la demande de certificat (CSR) et la clé privée
```powershell
Set-Content -Path miff-csr.cnf -Value "[req]","distinguished_name = dn","prompt = no","[dn]","CN = Miff Distribution","emailAddress = tomessa.mfouka@gmail.com"
openssl req -new -newkey rsa:2048 -nodes -keyout miff-dist.key -out miff-dist.csr -config miff-csr.cnf
dir
```
Attendu : deux fichiers `miff-dist.key` (**clé privée : ne jamais la partager**) et `miff-dist.csr` (la demande, sans danger). Vérification : `openssl req -in miff-dist.csr -noout -subject -verify` doit afficher `verify OK` et `CN=Miff Distribution`. [Vérifié]

### Étape 3. Créer le certificat « Apple Distribution » sur developer.apple.com
1. **Certificates** > bouton bleu **+** (à côté du titre « Certificates »).
2. Sur la page « Create a New Certificate », section **Software**, choisis **Apple Distribution** (« Sign your app for submission to the App Store »). **Pas** « Apple Development », **pas** « Apple Distribution Managed ». > **Continue**.
3. Page avec « Choose File » : **Choose File** > sélectionne `miff-dist.csr` (dans `miff-signing`) > **Continue**.
4. Page « Download Your Certificate » : bouton **Download** : un fichier `distribution.cer` arrive dans Téléchargements. Copie-le dans `miff-signing` :
```powershell
Copy-Item "$HOME\Downloads\distribution.cer" "$HOME\miff-signing\distribution.cer"
```
> Si Apple répond « maximum number of certificates » : **stop, ne révoque rien**, voir le plan B (§2).
> Le certificat apparaît dans la liste : type **Apple Distribution**, créé par toi, date d'aujourd'hui, expiration dans un an. **Note cette date d'expiration** dans ton agenda. (Les libellés exacts de developer.apple.com peuvent légèrement varier : le principe reste le même.)

### Étape 4. Fabriquer le fichier `.p12` (certificat + clé privée)
```powershell
openssl x509 -inform DER -in distribution.cer -out distribution.pem
openssl pkcs12 -export -inkey miff-dist.key -in distribution.pem -out miff-dist.p12 -name "Miff Distribution"
```
La 2e commande te **demande un mot de passe** (deux fois, sans l'afficher) : choisis un mot de passe long et unique, **range-le tout de suite dans ton gestionnaire de mots de passe**. [Vérifié] avec OpenSSL 3.5 (chaîne `.cer` → `.pem` → `.p12` rejouée).
Contrôle : `openssl pkcs12 -in miff-dist.p12 -nokeys -clcerts | openssl x509 -noout -subject -enddate` (il demande le mot de passe) doit afficher `Apple Distribution: <ton nom> (<ID d'équipe>)` et la date d'expiration.
> Si plus tard la CI répond « Import du .p12 refusé » alors que le mot de passe est bon : refais seulement le `.p12` avec `-legacy` (ancien format que macOS accepte toujours) : `openssl pkcs12 -export -legacy -inkey miff-dist.key -in distribution.pem -out miff-dist.p12 -name "Miff Distribution"`, puis repose le secret de l'étape 8 (garde donc `miff-dist.key` et `distribution.pem` jusqu'au premier run réussi). [Vérifié] la commande fonctionne ; [À vérifier] le besoin réel sur le runner.

### Étape 5. Vérifier l'identifiant d'app
**Identifiers** > ouvre `com.tomessa.miff`. Dans la liste des **Capabilities**, vérifie que **App Attest** et **Sign In with Apple** sont **cochées** (les deux droits de l'app, `ios/Config/Miff.entitlements`). Si l'une ne l'est pas : coche-la > **Save**. (En signature manuelle Xcode ne les active plus tout seul ; sans elles le profil de l'étape 6 ne les contiendrait pas.)

### Étape 6. Créer le profil « App Store »
1. **Profiles** > **+**.
2. Section **Distribution** : choisis **App Store Connect** > **Continue**. **Pas** « Ad Hoc », **pas** « Development ».
3. **App ID** : choisis `com.tomessa.miff` > **Continue**.
4. **Certificates** : coche **le certificat que tu viens de créer** (Apple Distribution, date d'aujourd'hui) > **Continue**.
5. **Provisioning Profile Name** : tape **exactement** `Miff CI App Store` (majuscules, espaces) > **Generate**.
6. **Download** : `Miff_CI_App_Store.mobileprovision` arrive dans Téléchargements. Copie-le :
```powershell
Copy-Item "$HOME\Downloads\Miff_CI_App_Store.mobileprovision" "$HOME\miff-signing\Miff_CI_App_Store.mobileprovision"
```
> Le nom exact est contrôlé par la CI. S'il est faux, elle s'arrête avec « Le profil s'appelle ... au lieu de ... » : recrée le profil avec le bon nom.

### Étape 7. Vérifier que GitHub est prêt
```powershell
gh auth status
gh secret list --repo Tomessa-Ghostrun/Miff
```
Attendu : connecté, et la liste des secrets **existants** (ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_P8_B64, APPLE_TEAM_ID, GOOGLE_SERVICE_INFO_PLIST_B64...) **sans** les 3 nouveaux. (Les valeurs ne sont jamais affichées.)

### Étape 8. Poser les 3 secrets
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\miff-signing\miff-dist.p12")) | gh secret set DIST_CERT_P12_B64 --repo Tomessa-Ghostrun/Miff
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\miff-signing\Miff_CI_App_Store.mobileprovision")) | gh secret set APPSTORE_PROFILE_B64 --repo Tomessa-Ghostrun/Miff
gh secret set DIST_CERT_P12_PASSWORD --repo Tomessa-Ghostrun/Miff
```
La 3e commande **demande le mot de passe** (colle celui du gestionnaire de mots de passe ; il n'apparaît pas à l'écran). Chaque commande répond `✓ Set Actions secret ... for Tomessa-Ghostrun/Miff`. Les valeurs passent directement de ton disque à GitHub : rien dans l'historique ni dans le dépôt. **Poser les 3, ou aucun** : si un seul ou deux existent, la CI s'arrête (« configuration partielle »).

### Étape 9. Nettoyer
Une fois les 3 secrets posés **et** le `.p12` + son mot de passe sauvegardés dans ton gestionnaire de mots de passe :
```powershell
Remove-Item "$HOME\miff-signing\miff-csr.cnf","$HOME\miff-signing\miff-dist.csr","$HOME\miff-signing\distribution.cer"
Remove-Item "$HOME\Downloads\distribution.cer","$HOME\Downloads\Miff_CI_App_Store.mobileprovision"
```
Après le **premier run réussi**, supprime aussi `miff-dist.key`, `distribution.pem`, `miff-dist.p12` et `Miff_CI_App_Store.mobileprovision` du disque (la sauvegarde est dans le gestionnaire de mots de passe).

### Étape 10. Vérifier (avant / après)
1. `gh secret list --repo Tomessa-Ghostrun/Miff` : les **3 nouveaux noms** apparaissent, avec la date du jour.
2. developer.apple.com > Certificates : **19** certificats (18 + 1 « Apple Distribution » à ton nom). **Aucun autre changement.**
3. Dis-moi « secrets posés ». Je ne lance rien moi-même : lance `ios-testflight` **une seule fois** (ou donne-moi ton accord pour que je le fasse). Dans le **Résumé** du run, tu dois lire : « Signature : MANUELLE (certificat Apple Distribution persistant, aucun certificat créé par exécution) », puis « Certificat : Apple Distribution (empreinte …), profil « Miff CI App Store » valide jusqu'au … ».
4. **Après le run** : developer.apple.com > Certificates doit **toujours en compter 19** (aucun nouveau « Created via API »). C'est la preuve que le problème est réglé.

## 5. Ce que fait la CI (pour le développeur)

| Élément | Changement |
|---|---|
| `.github/workflows/ios-testflight.yml` | Contrôle des 3 secrets : **3 présents = signature manuelle** ; **0 présent = ancien chemin automatique** (avec `::warning` et ligne de résumé) ; **1 ou 2 = arrêt**. En manuel : installation du `.p12` dans le trousseau temporaire (`security import`, `set-key-partition-list`) ; contrôle **bloquant** du profil (nom `Miff CI App Store`, équipe = `APPLE_TEAM_ID`, identifiant `<équipe>.com.tomessa.miff`, type App Store sans appareils ni `get-task-allow`, **certificat du `.p12` autorisé par le profil**, **expiration** du profil et du certificat, avertissement à 30 jours) ; installation du profil ; **archive sans `-allowProvisioningUpdates` et sans clé d'API** ; contrôle que l'archive est signée « Apple Distribution » ; **export manuel** (`ExportOptions.manual.plist.template`) ; la clé `.p8` ne sert **qu'à l'envoi** ; nettoyage du trousseau, des fichiers déchiffrés et du profil. |
| `ios/project-signing-manual.yml` (nouveau) | Superposition XcodeGen utilisée **seulement** par le chemin manuel : `CODE_SIGN_STYLE: Manual`, identité `Apple Distribution` et profil `Miff CI App Store` **dans la configuration Internal de la cible Miff, pour les builds sur appareil** (`[sdk=iphoneos*]`). Rien en ligne de commande. **`project.yml` n'est pas modifié** : sinon le repli automatique (qui sert tant que les secrets manquent) et la CI `ios` casseraient. Écart assumé avec la demande initiale (« réglages dans `project.yml` »), pour garder le repli. |
| `ios/ci/ExportOptions.manual.plist.template` (nouveau) | `signingStyle: manual`, `signingCertificate: Apple Distribution`, `provisioningProfiles: { com.tomessa.miff : Miff CI App Store }`, `destination: upload`. L'ancien modèle reste pour le repli. |
| `.github/workflows/ios.yml` | Nouveau contrôle (sans secret) : la superposition se génère et donne les bons réglages (Internal manuel sur appareil ; Debug, simulateur et `project.yml` inchangés). La signature manuelle ne pouvant pas être essayée sans les secrets, c'est ce qui la garde honnête à chaque PR. |
| `docs/dev/ios-spike.md` | §3, §6, §9 et §10 mis à jour. |

Quand le premier run manuel aura réussi, une PR de suivi supprimera le chemin automatique (`archive_mode`, `ExportOptions.plist.template`) : c'est lui qui crée des certificats de développement.

## 6. Entretien

- **Rappel d'agenda** : 1 mois avant l'expiration du certificat (date notée à l'étape 3).
- **Renouvellement** : refaire les étapes 2 à 6 et 8 (nouveau CSR, nouveau certificat, nouveau profil, remplacer les 3 secrets), **puis** seulement, après un build réussi, révoquer l'**ancien certificat Miff** (celui dont tu as noté la date) : jamais un autre.
- **Nouvelle capacité** (notifications APNs, etc.) : la cocher sur l'identifiant `com.tomessa.miff`, **régénérer le profil** (étape 6), remplacer `APPSTORE_PROFILE_B64` (étape 8), et ajouter l'entitlement dans `ios/Config/Miff.entitlements`.
- **Fuite ou doute** (le `.p12` ou son mot de passe a été exposé) : révoquer **ce** certificat (le tien, « Apple Distribution », créé le jour de l'étape 3), supprimer les 3 secrets, recommencer. Aucun autre certificat n'est concerné.

## 7. Risques et parades

| Risque | Parade |
|---|---|
| Limite de certificats de distribution atteinte | Plan B (§2) ; on ne révoque jamais à l'aveugle |
| `.p12` refusé | `-legacy` (étape 4) |
| Profil créé avec un autre certificat ou sans les capacités | Contrôle bloquant « certificat autorisé par le profil » ; étape 5 |
| Expiration silencieuse | Contrôle bloquant avec date explicite + avertissement 30 jours |
| Fuite du `.p12` | Secrets GitHub masqués ; gitleaks en CI ; jamais dans le dépôt ; révocation ciblée (§6) |
| Premier run manuel en échec (non testable sans Mac) | Chemin manuel jamais déclenché par une PR ; le repli automatique reste utilisable tant que les secrets sont absents ; journaux assainis déposés en artefact |
| Collision avec Ghostrun | Certificat et profil **distincts** et nommés « Miff » ; aucune révocation d'un certificat non reconnu ; `APPLE_TEAM_ID` et l'identifiant d'app sont contrôlés |

*Coût : 0 €. Aucun texte légal concerné.*
