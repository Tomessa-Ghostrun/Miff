# Gestion des secrets

**Règle d'or : un secret ne va jamais dans le dépôt, un chat, un ticket, une capture d'écran ni un message de commit.** Ni « temporairement », ni « dans une branche jetable ». Le dépôt est privé, mais un secret commité reste dans l'historique git pour toujours.

Ce document dit : quels secrets existent, comment les créer dans GitHub, comment les utiliser en CI, et quoi faire en cas de fuite. Le PM crée les secrets lui-même (aucun agent n'a accès à la console GitHub, Firebase ou Apple).

## Ce qui est un secret, ce qui ne l'est pas

| Élément | Secret ? | Remarque |
|---|---|---|
| Identifiant du projet (`miff-3d283`), identifiants d'apps Firebase, bundle ID `com.tomessa.miff` | Non | Déjà dans `CLAUDE.md`. |
| `google-services.json`, `GoogleService-Info.plist` | **Traités comme secrets ici** | Google précise que ces clés d'API sont des identifiants, pas des secrets au sens strict ; `CLAUDE.md` impose de ne pas les commiter tant qu'aucune règle contraire n'est actée. La vraie protection : règles Firestore + App Check. |
| Clé `.p8` App Store Connect, certificats `.p12`, profils `.mobileprovision` | **Oui** | Donnent accès à votre compte Apple. |
| Clé de signature Android (`.jks`, `.keystore`) et ses mots de passe | **Oui** | Perdue : problème de publication. Fuitée : usurpation. |
| Jeton de débogage App Check | **Oui** | Contourne la vérification d'authenticité de l'app. |
| Clé de compte de service Google (`...adminsdk...json`) | **Oui** | **Ne jamais en créer pour ce projet** (pas de Cloud Functions, déploiement des règles en local). |
| Mot de passe du compte de test du diagnostic | **Oui** | Gestionnaire de mots de passe uniquement. |

## Secrets GitHub

Emplacement : GitHub > dépôt Miff > Settings > Secrets and variables > Actions > New repository secret. Un secret par ligne. Un secret enregistré ne peut plus être relu, seulement remplacé ou supprimé.

**INIT-1 (ce lot) n'utilise aucun secret** : les workflows `hygiene` et `firestore-rules` fonctionnent sans.

| Nom | Contenu | Utilisé par | Quand |
|---|---|---|---|
| `GOOGLE_SERVICES_JSON_B64` | `google-services.json` encodé en base64 | workflow `android` | INIT-2 |
| `GOOGLE_SERVICE_INFO_PLIST_B64` | `GoogleService-Info.plist` encodé en base64 | workflow `ios` | INIT-3 / INIT-4 |
| `APPCHECK_DEBUG_TOKEN` | Jeton de débogage créé dans Firebase > App Check > Apps > Gérer les jetons de débogage | builds debug / CI | INIT-2 / INIT-4 |
| `ASC_KEY_ID` | Identifiant de la clé API App Store Connect | `ios-testflight` | INIT-3 |
| `ASC_ISSUER_ID` | Identifiant d'émetteur (Issuer ID) | `ios-testflight` | INIT-3 |
| `ASC_KEY_P8_B64` | Fichier `.p8` de la clé API, encodé en base64 | `ios-testflight` | INIT-3 |
| `APPLE_TEAM_ID` | Team ID Apple (identifiant, pas un mot de passe, mais gardé avec les autres) | `ios-testflight` | INIT-3 |
| Secrets de signature Android (clé d'upload, mots de passe) | À définir avec la clé d'upload | publication Play | Avant la première distribution |

Pas de clé de compte de service Google dans la CI : le déploiement des règles Firestore se fait en local, après accord du PM (`firebase deploy --only firestore:rules --project miff-3d283`).

## Encoder un fichier en base64 (Windows, PowerShell)

Le contenu est copié dans le presse-papiers, sans écrire de fichier intermédiaire :

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\chemin\vers\fichier")) | Set-Clipboard
```

Coller ensuite dans le champ « Secret » de GitHub, puis **vider le presse-papiers** (copier un texte quelconque). Ne pas coller le résultat dans une conversation.

Les fichiers de configuration Firebase se régénèrent avec (App IDs dans `CLAUDE.md`) :

```powershell
firebase apps:sdkconfig ANDROID <APP_ID_ANDROID> --project miff-3d283
firebase apps:sdkconfig IOS <APP_ID_IOS> --project miff-3d283
```

Toujours avec `--project miff-3d283`. Ne jamais cibler `ghostrun-app`.

## Utiliser un secret dans un workflow

- Passer le secret par une variable d'environnement de l'étape, jamais directement dans la commande :

  ```yaml
  - name: Décoder google-services.json
    env:
      GOOGLE_SERVICES_JSON_B64: ${{ secrets.GOOGLE_SERVICES_JSON_B64 }}
    run: echo "$GOOGLE_SERVICES_JSON_B64" | base64 -d > android/app/google-services.json
  ```

- Ne jamais afficher (`cat`, `echo`, `set -x`) un secret décodé : GitHub masque la valeur exacte du secret, **pas** sa version décodée.
- Donner le secret uniquement à l'étape qui en a besoin, et uniquement aux workflows qui en ont besoin.
- Jamais de `pull_request_target`. Un workflow lancé par une PR venant d'un fork n'a pas accès aux secrets (sans objet tant que le dépôt a un seul contributeur).
- Les fichiers décodés disparaissent avec la machine de la CI ; ils ne sont jamais envoyés en artefact.

## En local

- Les fichiers de configuration Firebase se placent aux emplacements prévus par `android/README.md` et `ios/README.md` ; `.gitignore` les ignore (`google-services.json`, `GoogleService-Info.plist`).
- Clés, certificats, `.env` : dans un gestionnaire de mots de passe ou un dossier hors du dépôt. Le modèle `.env.example` (sans valeur) est le seul fichier `.env*` autorisé.
- Contrôle avant un commit : `git status` ne doit lister aucun de ces fichiers. En cas de doute, ne pas commiter et demander.

## Ce qui protège (et ses limites)

- `.gitignore` empêche l'ajout par accident ; le job CI `hygiene` échoue si un fichier interdit (liste dans `.github/workflows/hygiene.yml`) ou un motif de secret (outil gitleaks) apparaît dans une PR.
- Ces contrôles réduisent le risque, ils ne le suppriment pas : un secret collé dans un fichier texte ordinaire peut échapper à la détection. La revue de code regarde aussi ce point.
- Le blocage automatique à l'envoi (« push protection ») de GitHub n'est pas supposé disponible sur un dépôt privé gratuit (à vérifier dans les réglages du dépôt).

## En cas de fuite (ou de doute)

Ne pas attendre de « vérifier si quelqu'un l'a vu » : **révoquer d'abord**, nettoyer ensuite.

| Ce qui a fuité | Action immédiate |
|---|---|
| Clé API App Store Connect (`.p8`) | App Store Connect > Utilisateurs et accès > Intégrations > Clés : révoquer la clé, en créer une nouvelle, mettre à jour `ASC_KEY_ID` et `ASC_KEY_P8_B64`. |
| Jeton de débogage App Check | Firebase > App Check > Apps > Gérer les jetons de débogage : supprimer le jeton, en créer un autre, mettre à jour `APPCHECK_DEBUG_TOKEN`. |
| `google-services.json` / `GoogleService-Info.plist` | Moins grave (identifiants publics en soi), mais vérifier que les règles Firestore sont déployées et qu'App Check est actif ; dans Google Cloud > API et services > Identifiants, restreindre la clé d'API aux apps `com.tomessa.miff`. |
| Clé de signature Android | Contacter l'équipe technique : avec « Play App Signing », demander une réinitialisation de la clé d'upload dans la Play Console. |
| Clé de compte de service Google | Google Cloud > IAM > Comptes de service : supprimer la clé. (Aucune ne devrait exister.) |
| Mot de passe du compte de test | Le changer dans Firebase > Authentication. |
| Un secret GitHub | Le remplacer par une nouvelle valeur (la valeur précédente est alors inutilisable côté GitHub, mais la révoquer chez le fournisseur reste nécessaire). |

Puis :

1. Si le secret a été **commité**, le retirer d'un nouveau commit ne suffit pas (il reste dans l'historique). Prévenir l'équipe technique : réécrire l'historique est une opération délicate (force push) à ne faire qu'avec l'accord explicite du PM, **après** révocation.
2. Si le secret a été collé dans un chat ou un ticket : le considérer comme compromis, le révoquer, supprimer le message si possible.
3. Noter l'incident (date, ce qui a fuité, ce qui a été révoqué) dans `docs/qa/` pour garder une trace.
4. Surveiller la facture Firebase et GitHub dans les jours qui suivent (usage anormal).
